package app.erudit.update

import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Пункт списка изменений: заголовок (сообщение коммита) и подробности. */
data class ChangeNote(val title: String, val details: List<String>)

/** Сборка, опубликованная в GitHub Releases. */
data class Release(
    val build: Int,
    val date: LocalDate?,
    val notes: List<ChangeNote>,
    val apkUrl: String,
    val apkSize: Long,
)

/** Разбор ответа GitHub API со списком релизов и раздела «Что нового» из их описаний. */
object Releases {
    private const val TAG_PREFIX = "build-"
    private const val NOTES_HEADING = "## Что нового"

    /** Релизы с APK, от новых к старым. Черновики, пре-релизы и чужие теги пропускаются. */
    fun parse(json: String, zone: ZoneId = ZoneId.systemDefault()): List<Release> {
        val array = JSONArray(json)
        val result = mutableListOf<Release>()
        for (i in 0 until array.length()) {
            val r = array.getJSONObject(i)
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
            val build = r.optString("tag_name").removePrefix(TAG_PREFIX).toIntOrNull() ?: continue
            val assets = r.optJSONArray("assets") ?: continue
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: continue
            val date = runCatching { Instant.parse(r.optString("published_at")).atZone(zone).toLocalDate() }.getOrNull()
            result += Release(
                build = build,
                date = date,
                notes = notes(r.optString("body")),
                apkUrl = apk.optString("browser_download_url"),
                apkSize = apk.optLong("size"),
            )
        }
        return result.sortedByDescending { it.build }
    }

    /**
     * Раздел «Что нового» из описания релиза (его пишет CI по сообщениям коммитов):
     * `### заголовок`, под ним пункты `- ` с продолжением строк через отступ.
     */
    fun notes(body: String): List<ChangeNote> {
        val lines = body.replace("\r", "").lines()
        val start = lines.indexOfFirst { it.trim() == NOTES_HEADING }
        if (start < 0) return emptyList()
        val notes = mutableListOf<ChangeNote>()
        var title: String? = null
        val details = mutableListOf<String>()
        fun flush() {
            title?.let { notes += ChangeNote(it, details.toList()) }
            title = null
            details.clear()
        }
        for (raw in lines.drop(start + 1)) {
            val line = raw.trimEnd()
            when {
                line.startsWith("## ") -> break
                line.startsWith("### ") -> {
                    flush()
                    title = line.removePrefix("### ").trim()
                }
                title == null || line.isBlank() -> Unit
                line.startsWith("- ") -> details += line.removePrefix("- ").trim()
                line.startsWith(" ") && details.isNotEmpty() -> details[details.lastIndex] += " " + line.trim()
                else -> details += line.trim()
            }
        }
        flush()
        return notes
    }
}
