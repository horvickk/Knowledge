package app.erudit.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Castle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.ui.graphics.vector.ImageVector

/** Иконка области по имени из `domain.txt`. */
fun domainIcon(name: String): ImageVector = when (name) {
    "functions" -> Icons.Outlined.Functions
    "bolt" -> Icons.Outlined.Bolt
    "science" -> Icons.Outlined.Science
    "biotech" -> Icons.Outlined.Biotech
    "public" -> Icons.Outlined.Public
    "rocket" -> Icons.Outlined.RocketLaunch
    "terrain" -> Icons.Outlined.Terrain
    "history" -> Icons.Outlined.AccountBalance
    "castle" -> Icons.Outlined.Castle
    "book" -> Icons.AutoMirrored.Outlined.MenuBook
    "palette" -> Icons.Outlined.Palette
    "music" -> Icons.Outlined.MusicNote
    "lightbulb" -> Icons.Outlined.Lightbulb
    "psychology" -> Icons.Outlined.Psychology
    "economy" -> Icons.AutoMirrored.Outlined.TrendingUp
    "gavel" -> Icons.Outlined.Gavel
    "computer" -> Icons.Outlined.Computer
    "health" -> Icons.Outlined.HealthAndSafety
    "translate" -> Icons.Outlined.Translate
    "extension" -> Icons.Outlined.Extension
    "stories" -> Icons.Outlined.AutoStories
    "groups" -> Icons.Outlined.Groups
    "language" -> Icons.Outlined.Language
    else -> Icons.AutoMirrored.Outlined.MenuBook
}
