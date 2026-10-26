package app.erudit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import android.graphics.Color as AndroidColor
import app.erudit.data.prefs.Settings
import app.erudit.data.prefs.ThemeMode
import app.erudit.ui.nav.AppNav
import app.erudit.ui.nav.LocalContainer
import app.erudit.ui.theme.EruditTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as EruditApp).container
        setContent {
            val settings: Settings? by container.settings.settings.collectAsState(initial = null)
            val s = settings
            val dark = when (s?.theme ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SystemBars(dark)
            CompositionLocalProvider(LocalContainer provides container) {
                EruditTheme(mode = s?.theme ?: ThemeMode.SYSTEM, readingScale = s?.textScale ?: 1f) {
                    if (s == null) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    } else {
                        AppNav(onboarded = s.onboarded)
                    }
                }
            }
        }
    }

    @Composable
    private fun SystemBars(dark: Boolean) {
        DisposableEffect(dark) {
            val style = if (dark) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose {}
        }
    }
}
