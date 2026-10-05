package io.github.obamna1234.sevby

import android.content.Context
import androidx.core.content.edit

enum class Source { BOTH, BANDCAMP, YOUTUBE }

/** Small remembered settings: save folder, source, and the last song list. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("sevby", Context.MODE_PRIVATE)

    var folderUri: String?
        get() = sp.getString("folder_uri", null)
        set(v) = sp.edit { putString("folder_uri", v) }

    var source: Source
        get() = runCatching { Source.valueOf(sp.getString("source", null)!!) }.getOrDefault(Source.BOTH)
        set(v) = sp.edit { putString("source", v.name) }

    var draft: String
        get() = sp.getString("draft", "") ?: ""
        set(v) = sp.edit { putString("draft", v) }

    /** When SEVBY last asked GitHub for the newest yt-dlp (ms), and what it said. */
    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0L)
        set(v) = sp.edit { putLong("last_update_check", v) }

    var latestYtDlp: String
        get() = sp.getString("latest_ytdlp", "") ?: ""
        set(v) = sp.edit { putString("latest_ytdlp", v) }

    var askedNotifications: Boolean
        get() = sp.getBoolean("asked_notifications", false)
        set(v) = sp.edit { putBoolean("asked_notifications", v) }

    /** Name of the loaded .txt (without extension); used later as the queue item's sub-folder. */
    var listName: String
        get() = sp.getString("list_name", "") ?: ""
        set(v) = sp.edit { putString("list_name", v) }
}
