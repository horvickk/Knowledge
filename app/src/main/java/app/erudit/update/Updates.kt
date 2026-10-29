package app.erudit.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.erudit.BuildConfig
import app.erudit.data.prefs.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** Новые сборки относительно установленной [current], от новой к старой. */
data class UpdateInfo(val current: Int, val releases: List<Release>) {
    val latest: Release get() = releases.first()
}

enum class CheckState { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, FAILED }

sealed interface DownloadState {
    data object None : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data class Ready(val file: File) : DownloadState
    /** Установка из приложения не разрешена: пользователь ушёл в системные настройки. */
    data class NeedsPermission(val file: File) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Обновления из GitHub Releases: при запуске приложение сверяет номер своей сборки с последним
 * релизом и, если вышла новая, показывает окно со списком изменений. APK скачивается в кэш
 * и передаётся системному установщику. Подпись у сборок одна, поэтому новая версия ставится
 * поверх, прогресс сохраняется.
 */
class UpdateManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsRepository,
    val current: Int = BuildConfig.BUILD_NUMBER,
    private val repo: String = BuildConfig.UPDATE_REPO,
) {
    /** Обновления проверяют только сборки CI: у локальных нет номера и репозитория. */
    val enabled: Boolean = current > 0 && repo.isNotEmpty() && !BuildConfig.DEBUG

    private val _info = MutableStateFlow<UpdateInfo?>(null)
    val info: StateFlow<UpdateInfo?> = _info

    private val _check = MutableStateFlow(CheckState.IDLE)
    val check: StateFlow<CheckState> = _check

    private val _download = MutableStateFlow<DownloadState>(DownloadState.None)
    val download: StateFlow<DownloadState> = _download

    private val _dialog = MutableStateFlow(false)

    /** Показывать ли окно обновления. */
    val dialog: StateFlow<Boolean> = _dialog

    private var downloadJob: Job? = null

    /** Проверка при запуске: окно появляется, если есть новая сборка и её не отложили сегодня. */
    fun checkOnStart() {
        if (!enabled) return
        scope.launch {
            val s = settings.settings.first()
            if (!s.checkUpdates) return@launch
            val info = fetch() ?: return@launch
            val snoozed = s.updateSnoozeBuild >= info.latest.build && s.updateSnoozeDay == LocalDate.now().toEpochDay()
            if (!snoozed) _dialog.value = true
        }
    }

    /** Проверка по кнопке в настройках: окно открывается сразу, если обновление есть. */
    fun checkNow() {
        if (!enabled || _check.value == CheckState.CHECKING) return
        scope.launch { if (fetch() != null) _dialog.value = true }
    }

    fun showDialog() {
        if (_info.value != null) _dialog.value = true
    }

    /** «Позже»: окно не появится до завтра или до следующей сборки. Загрузка, если идёт, продолжается. */
    fun later() {
        _dialog.value = false
        val build = _info.value?.latest?.build ?: return
        scope.launch { settings.snoozeUpdate(build, LocalDate.now().toEpochDay()) }
    }

    private suspend fun fetch(): UpdateInfo? {
        _check.value = CheckState.CHECKING
        return try {
            val json = withContext(Dispatchers.IO) { get("https://api.github.com/repos/$repo/releases?per_page=20") }
            val newer = Releases.parse(json).filter { it.build > current }
            val info = newer.takeIf { it.isNotEmpty() }?.let { UpdateInfo(current, it) }
            if (info?.latest?.build != _info.value?.latest?.build) _download.value = DownloadState.None
            _info.value = info
            _check.value = if (info != null) CheckState.AVAILABLE else CheckState.UP_TO_DATE
            info
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _check.value = CheckState.FAILED
            null
        }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Erudit-Android")
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Скачивает APK последней сборки в кэш; прогресс — в [download]. */
    fun startDownload() {
        val release = _info.value?.latest ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "Erudit-${release.build}.apk")
            // Старые загрузки больше не нужны.
            dir.listFiles()?.filter { it.name != target.name }?.forEach { it.delete() }
            if (target.exists() && target.length() == release.apkSize) {
                _download.value = DownloadState.Ready(target)
                return@launch
            }
            _download.value = DownloadState.Downloading(0f)
            val part = File(dir, "${target.name}.part")
            try {
                val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "Erudit-Android")
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) error("HTTP ${conn.responseCode}")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                    conn.inputStream.use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            var shown = 0
                            while (isActive) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                done += n
                                val percent = if (total > 0) (done * 100 / total).toInt() else 0
                                if (percent != shown) {
                                    shown = percent
                                    _download.value = DownloadState.Downloading(percent / 100f)
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                if (release.apkSize > 0 && part.length() != release.apkSize) error("Файл скачан не полностью")
                target.delete()
                if (!part.renameTo(target)) error("Не удалось сохранить файл")
                // Полоса успевает дойти до конца, прежде чем её сменит кнопка «Установить».
                _download.value = DownloadState.Downloading(1f)
                delay(400)
                _download.value = DownloadState.Ready(target)
            } catch (e: CancellationException) {
                part.delete()
                _download.value = DownloadState.None
                throw e
            } catch (e: Exception) {
                part.delete()
                _download.value = DownloadState.Failed("Не удалось скачать обновление. Проверьте соединение и попробуйте ещё раз.")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /**
     * Открывает системный установщик. Если установка из приложения ещё не разрешена, сначала
     * открывает экран разрешения — вернувшись, пользователь нажимает «Установить» ещё раз.
     */
    fun install(activity: Context) {
        val file = when (val d = _download.value) {
            is DownloadState.Ready -> d.file
            is DownloadState.NeedsPermission -> d.file
            else -> return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            _download.value = DownloadState.NeedsPermission(file)
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            runCatching { activity.startActivity(settingsIntent) }
            return
        }
        _download.value = DownloadState.Ready(file)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            _download.value = DownloadState.Failed("Не удалось открыть установщик. Скачайте APK со страницы релиза.")
        }
    }
}
