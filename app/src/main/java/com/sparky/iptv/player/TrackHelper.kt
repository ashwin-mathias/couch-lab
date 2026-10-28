package com.sparky.iptv.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

/** One choosable audio or subtitle track in the current stream. */
data class TrackOption(
    val label: String,
    val language: String?,
    val group: Tracks.Group,
    val index: Int,
    val selected: Boolean,
)

/** Lists and switches audio / subtitle tracks on the shared player. */
@OptIn(UnstableApi::class)
object TrackHelper {

    fun options(player: Player, type: Int): List<TrackOption> {
        val out = ArrayList<TrackOption>()
        for (g in player.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                val f = g.getTrackFormat(i)
                out.add(TrackOption(label(f, type, out.size), f.language, g, i, g.isTrackSelected(i)))
            }
        }
        return out
    }

    /** True when subtitles are switched on and showing one of the tracks. */
    fun subtitlesOn(player: Player): Boolean =
        !player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) &&
            options(player, C.TRACK_TYPE_TEXT).any { it.selected }

    fun select(player: Player, type: Int, option: TrackOption) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
            .build()
    }

    fun subtitlesOff(player: Player) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    /** Clears choices from the previous stream; subtitles start off. */
    fun reset(player: Player) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    /**
     * Re-applies a remembered choice (by language) once the stream's tracks are known.
     * Returns true if anything was applied.
     */
    fun applyRemembered(player: Player, audioLang: String?, textLang: String?): Boolean {
        var applied = false
        if (audioLang != null) {
            options(player, C.TRACK_TYPE_AUDIO).firstOrNull { it.language == audioLang }?.let {
                if (!it.selected) select(player, C.TRACK_TYPE_AUDIO, it)
                applied = true
            }
        }
        if (textLang != null && textLang != "off") {
            options(player, C.TRACK_TYPE_TEXT).firstOrNull { it.language == textLang }?.let {
                if (!it.selected) select(player, C.TRACK_TYPE_TEXT, it)
                applied = true
            }
        }
        return applied
    }

    private fun label(f: Format, type: Int, n: Int): String {
        val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let { code ->
            Locale.forLanguageTag(code).displayLanguage.takeIf { it.isNotBlank() && it != code } ?: code.uppercase()
        }
        val parts = mutableListOf<String>()
        if (lang != null) parts.add(lang)
        f.label?.takeIf { it.isNotBlank() && !it.equals(lang, ignoreCase = true) }?.let { parts.add(it) }
        if (type == C.TRACK_TYPE_AUDIO) {
            when {
                f.channelCount >= 6 -> parts.add("5.1")
                f.channelCount == 2 -> parts.add("Stereo")
                f.channelCount == 1 -> parts.add("Mono")
            }
        }
        if (type == C.TRACK_TYPE_TEXT && (f.roleFlags and C.ROLE_FLAG_CAPTION) != 0) parts.add("CC")
        return parts.joinToString(" · ").ifBlank { (if (type == C.TRACK_TYPE_AUDIO) "Audio " else "Subtitles ") + (n + 1) }
    }
}
