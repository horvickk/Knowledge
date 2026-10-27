package app.erudit

import android.app.Application
import android.content.res.AssetManager
import app.erudit.data.content.Content
import app.erudit.data.content.ContentLoader
import app.erudit.data.content.ContentSource
import app.erudit.data.db.AppDatabase
import app.erudit.data.prefs.SettingsRepository
import app.erudit.domain.StudyRepository
import app.erudit.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EruditApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(app)
    val database = AppDatabase.create(app)

    private val _content = MutableStateFlow<Content?>(null)

    /** Весь учебный контент; null, пока идёт загрузка из assets. */
    val content: StateFlow<Content?> = _content

    suspend fun awaitContent(): Content = content.filterNotNull().first()

    val study = StudyRepository(database.dao(), settings) { awaitContent() }

    val updates = UpdateManager(app, scope, settings)

    suspend fun resetProgress() = withContext(Dispatchers.IO) { database.clearAllTables() }

    init {
        scope.launch(Dispatchers.IO) {
            _content.value = ContentLoader.load(AssetSource(app.assets))
        }
        updates.checkOnStart()
    }
}

private class AssetSource(private val assets: AssetManager) : ContentSource {
    override fun list(path: String): List<String> = assets.list(path)?.toList().orEmpty()
    override fun read(path: String): String = assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
