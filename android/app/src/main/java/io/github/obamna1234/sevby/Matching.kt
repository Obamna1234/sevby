package io.github.obamna1234.sevby

/** One YouTube search result. */
data class YtEntry(
    val id: String,
    val url: String,
    val title: String,
    val channel: String,
    val duration: Double?,
) {
    val watchUrl: String get() = if (url.startsWith("http")) url else "https://www.youtube.com/watch?v=$id"
}

/**
 * Picks the most likely right recording from YouTube results.
 * Same rules as score_youtube_result() in sevby_app.py: right length, title/artist words,
 * official "Topic" channels, and penalties for live / remix / sped-up versions.
 */
object Matching {

    private val UNWANTED = listOf(
        "live", "remix", "cover", "karaoke", "instrumental", "sped up", "slowed", "reverb",
        "nightcore", "8d", "bass boosted", "mashup", "reaction", "acoustic", "tribute",
        "extended", "1 hour", "10 hours", "full album", "loop",
    )

    private val WORD = Regex("""[\p{L}\p{N}_]+""")

    /** "&" counts as the word "and" ("In Shadows & Dust" = "In Shadows and Dust"). */
    private fun ampersand(s: String): String = s.replace("&", " and ")

    fun words(s: String): List<String> = WORD.findAll(ampersand(s).lowercase()).map { it.value }.toList()

    private fun hasWord(text: String, w: String): Boolean =
        Regex("""(?<![\p{L}\p{N}_])""" + Regex.escape(w) + """(?![\p{L}\p{N}_])""").containsMatchIn(text)

    /** [targetSeconds] is the real track length when known (0 = unknown). */
    fun score(e: YtEntry, song: String, index: Int, targetSeconds: Int = 0): Double {
        val (artist, title) = SongList.split(song)
        val tLow = e.title.lowercase()
        val wanted = "$song $title $artist".lowercase()
        var score = -0.5 * index

        val dur = e.duration
        if (dur == null || dur <= 0) {
            score -= 20                                   // live streams / unknown length
        } else if (targetSeconds > 0) {
            val diff = kotlin.math.abs(dur - targetSeconds)
            score += when {
                diff <= 3 -> 40.0; diff <= 8 -> 28.0; diff <= 15 -> 12.0; diff <= 40 -> -15.0; else -> -45.0
            }
        } else {
            score += if (dur > 600) -25.0 else if (dur < 60) -10.0 else 0.0
        }

        val tw = words(title).toSet()
        if (tw.isNotEmpty()) score += 25.0 * (tw intersect words(e.title).toSet()).size / tw.size
        val aw = words(artist).toSet()
        if (aw.isNotEmpty()) score += 15.0 * (aw intersect words(e.title + " " + e.channel).toSet()).size / aw.size

        if (e.channel.lowercase().endsWith("- topic")) score += 20
        if ("official audio" in tLow) score += 6 else if ("official" in tLow) score += 2

        var penalty = 0
        for (w in UNWANTED) if (hasWord(tLow, w) && !hasWord(wanted, w)) penalty += 30
        score -= minOf(penalty, 60)
        return score
    }

    fun rank(entries: List<YtEntry>, song: String, targetSeconds: Int = 0): List<Pair<Double, YtEntry>> =
        entries.mapIndexed { i, e -> score(e, song, i, targetSeconds) to e }.sortedByDescending { it.first }

    private val NOISE = Regex(
        """\s*[(\[][^)\]]*\b(feat\.?|ft\.?|featuring|remaster(ed)?|original mix|original version|explicit|clean)\b[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val REMASTER_SUFFIX = Regex("""\s+-\s+(\d{4}\s+)?(digital(ly)?\s+)?remaster(ed)?.*$""", RegexOption.IGNORE_CASE)

    /** Title without "(feat. X)", "(Remastered)", "(Original Mix)" and "- 2011 Remaster" parts. */
    fun cleanTitle(t: String): String = t.replace(NOISE, "").replace(REMASTER_SUFFIX, "").trim()

    /** Lowercase letters and digits only, for comparing names ("M.A.D.E.S" = "mades", "&" = "and"). */
    fun norm(s: String): String = ampersand(s).lowercase().filter { it.isLetterOrDigit() }

    fun fmtLen(sec: Double?): String {
        if (sec == null || sec <= 0) return "?:??"
        val s = sec.toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }
}
