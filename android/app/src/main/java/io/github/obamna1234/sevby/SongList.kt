package io.github.obamna1234.sevby

/** Song-list helpers, ported from sevby_app.py so both versions read lists the same way. */
object SongList {

    /** One song per line. Skips blanks, "===" headers and links; trims Chosic-style "  →  " suffixes. */
    fun parse(text: String): List<String> =
        text.removePrefix("﻿").lines().mapNotNull { raw ->
            var line = raw.trim()
            if (line.isEmpty() || line.startsWith("===") || line.startsWith("http")) return@mapNotNull null
            if ("  →  " in line) line = line.substringBefore("  →  ").trim()
            line.ifEmpty { null }
        }

    /** (artist, title). Artist is "" when the line has no " - " separator. */
    fun split(song: String): Pair<String, String> {
        for (sep in listOf(" - ", " – ", " — ")) {
            val i = song.indexOf(sep)
            if (i >= 0) return song.substring(0, i).trim() to song.substring(i + sep.length).trim()
        }
        return "" to song.trim()
    }

    /** Same rules as the desktop app, so files get identical names on every platform. */
    fun safeFileName(name: String): String =
        name.replace(Regex("""[<>:"/\\|?*]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(180)
}
