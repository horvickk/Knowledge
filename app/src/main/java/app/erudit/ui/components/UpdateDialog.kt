package app.erudit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.erudit.ui.markdown.Markdown
import app.erudit.ui.theme.AppTheme
import app.erudit.update.DownloadState
import app.erudit.update.Release
import app.erudit.update.UpdateInfo
import app.erudit.update.UpdateManager
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Окно обновления поверх приложения: появляется при запуске или по кнопке в настройках. */
@Composable
fun UpdateDialogHost(updates: UpdateManager) {
    val show by updates.dialog.collectAsState()
    val info by updates.info.collectAsState()
    val download by updates.download.collectAsState()
    val context = LocalContext.current
    val current = info
    if (!show || current == null) return
    Dialog(
        onDismissRequest = updates::later,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        UpdateCard(
            info = current,
            download = download,
            onDownload = updates::startDownload,
            onCancel = updates::cancelDownload,
            onInstall = { updates.install(context) },
            onLater = updates::later,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
        )
    }
}

/** Содержимое окна: что нового во всех сборках новее установленной и загрузка последней. */
@Composable
fun UpdateCard(
    info: UpdateInfo,
    download: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = AppTheme.extra
    val latest = info.latest
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth().heightIn(max = 640.dp),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text("ОБНОВЛЕНИЕ", style = MaterialTheme.typography.labelSmall, color = extra.muted)
            Spacer(Modifier.height(8.dp))
            Text("Доступна новая версия", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Сборка ${latest.build} · у вас ${info.current}" + (if (latest.apkSize > 0) " · ${megabytes(latest.apkSize)}" else ""),
                style = MaterialTheme.typography.bodyMedium,
                color = extra.muted,
            )
            Spacer(Modifier.height(16.dp))
            Text("Что нового", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                info.releases.forEach { ReleaseNotes(it, showHeader = info.releases.size > 1) }
            }
            Spacer(Modifier.height(20.dp))
            when (download) {
                DownloadState.None -> PrimaryButton("Скачать и установить", onClick = onDownload)
                is DownloadState.Downloading -> {
                    ThinProgress(download.progress)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Загрузка… ${(download.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = extra.muted,
                        )
                        TextButton(onClick = onCancel) { Text("Отменить") }
                    }
                }
                is DownloadState.Ready -> PrimaryButton("Установить", onClick = onInstall)
                is DownloadState.NeedsPermission -> {
                    Text(
                        "Разрешите установку приложений из «Эрудита» в открывшихся настройках, вернитесь и нажмите «Установить».",
                        style = MaterialTheme.typography.bodySmall,
                        color = extra.muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Установить", onClick = onInstall)
                }
                is DownloadState.Failed -> {
                    Text(download.message, style = MaterialTheme.typography.bodySmall, color = extra.error)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Повторить", onClick = onDownload)
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                Text(if (download is DownloadState.Downloading) "Скрыть" else "Позже", color = extra.muted)
            }
        }
    }
}

@Composable
private fun ReleaseNotes(release: Release, showHeader: Boolean) {
    val extra = AppTheme.extra
    Column(Modifier.padding(top = 10.dp)) {
        if (showHeader) {
            Text(
                "Сборка ${release.build}" + (release.date?.let { " · ${it.format(DATE)}" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = extra.muted,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (release.notes.isEmpty()) {
            Text("Исправления и улучшения", style = MaterialTheme.typography.bodyMedium)
        }
        release.notes.forEach { note ->
            Text(Markdown.inline(note.title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            note.details.forEach { detail ->
                Row(Modifier.padding(top = 3.dp)) {
                    Text("—", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(20.dp))
                    Text(Markdown.inline(detail), style = MaterialTheme.typography.bodyMedium, color = extra.muted)
                }
            }
        }
    }
}

private val DATE = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

private fun megabytes(bytes: Long): String = "%.1f МБ".format(Locale.forLanguageTag("ru"), bytes / 1_048_576.0)
