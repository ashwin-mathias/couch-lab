package com.sparky.iptv.player

/** Lists and switches audio / subtitle tracks on the shared player (3.1: through the engine). */
object TrackHelper {
    fun options(player: PlayerEngine, type: Int): List<TrackOption> = player.tracks(type)

    fun subtitlesOn(player: PlayerEngine): Boolean = player.subtitlesShowing()

    fun select(player: PlayerEngine, type: Int, option: TrackOption) = player.select(type, option)

    fun subtitlesOff(player: PlayerEngine) = player.subtitlesOff()

    fun reset(player: PlayerEngine) = player.resetTracks()

    /**
     * Re-applies a remembered choice (by language) once the stream's tracks are known.
     * Returns true if anything was applied.
     */
    fun applyRemembered(player: PlayerEngine, audioLang: String?, textLang: String?): Boolean {
        var applied = false
        if (audioLang != null) {
            options(player, TrackType.AUDIO).firstOrNull { it.language == audioLang }?.let {
                if (!it.selected) select(player, TrackType.AUDIO, it)
                applied = true
            }
        }
        if (textLang != null && textLang != "off") {
            options(player, TrackType.TEXT).firstOrNull { it.language == textLang }?.let {
                if (!it.selected) select(player, TrackType.TEXT, it)
                applied = true
            }
        }
        return applied
    }

    /** "English · 5.1", "Spanish · CC", or "Audio 2" when nothing is known (shared by every engine). */
    fun label(language: String?, extra: String?, channels: Int, caption: Boolean, type: Int, n: Int): String {
        val lang = language?.takeIf { it.isNotBlank() && it != "und" }?.let { code ->
            java.util.Locale.forLanguageTag(code).displayLanguage.takeIf { it.isNotBlank() && it != code } ?: code.uppercase()
        }
        val parts = mutableListOf<String>()
        if (lang != null) parts.add(lang)
        extra?.takeIf { it.isNotBlank() && !it.equals(lang, ignoreCase = true) }?.let { parts.add(it) }
        if (type == TrackType.AUDIO) {
            when {
                channels >= 6 -> parts.add("5.1")
                channels == 2 -> parts.add("Stereo")
                channels == 1 -> parts.add("Mono")
            }
        }
        if (type == TrackType.TEXT && caption) parts.add("CC")
        return parts.joinToString(" · ").ifBlank { (if (type == TrackType.AUDIO) "Audio " else "Subtitles ") + (n + 1) }
    }
}
