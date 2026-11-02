package com.sparky.iptv.player

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

/** How subtitles look. Each field is an index into the option lists below. */
data class SubStyle(
    val size: Int = 1,        // Medium
    val color: Int = 0,       // White
    val background: Int = 1,  // Semi-transparent box
    val edge: Int = 0,        // Drop shadow
    val position: Int = 0,    // Normal
) {
    fun encode() = "$size,$color,$background,$edge,$position"

    val summary: String
        get() = listOf(SIZES[size], COLORS[color], EDGES[edge], BACKGROUNDS[background]).joinToString(" · ") +
            if (position == 1) " · Raised" else ""

    companion object {
        val SIZES = listOf("Small", "Medium", "Large", "Extra large")
        private val SIZE_SCALE = floatArrayOf(0.8f, 1f, 1.25f, 1.5f)
        val COLORS = listOf("White", "Yellow", "Cyan")
        private val COLOR_VALUES = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFE14D.toInt(), 0xFF7FE3F0.toInt())
        val BACKGROUNDS = listOf("None", "Semi-transparent box", "Solid black box")
        private val BACKGROUND_VALUES = intArrayOf(Color.TRANSPARENT, 0x80000000.toInt(), 0xFF000000.toInt())
        val EDGES = listOf("Drop shadow", "Outline", "None")
        private val EDGE_VALUES = intArrayOf(
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, CaptionStyleCompat.EDGE_TYPE_OUTLINE, CaptionStyleCompat.EDGE_TYPE_NONE,
        )
        val POSITIONS = listOf("Normal", "Raised")

        /** Bottom gap as a share of the video height. Raised lifts captions clear of the bottom edge. */
        private const val RAISED_BOTTOM = 0.2f

        fun decode(raw: String?): SubStyle {
            val p = raw?.split(",")?.map { it.trim().toIntOrNull() } ?: return SubStyle()
            fun at(i: Int, max: Int, def: Int) = p.getOrNull(i)?.takeIf { it in 0..max } ?: def
            val d = SubStyle()
            return SubStyle(
                size = at(0, SIZES.lastIndex, d.size),
                color = at(1, COLORS.lastIndex, d.color),
                background = at(2, BACKGROUNDS.lastIndex, d.background),
                edge = at(3, EDGES.lastIndex, d.edge),
                position = at(4, POSITIONS.lastIndex, d.position),
            )
        }

        /** Applies the style to a subtitle view. Our style wins over styling embedded in the stream. */
        @OptIn(UnstableApi::class)
        fun apply(v: SubtitleView?, s: SubStyle) {
            v ?: return
            v.setApplyEmbeddedStyles(false)
            v.setApplyEmbeddedFontSizes(false)
            v.setStyle(
                CaptionStyleCompat(
                    COLOR_VALUES[s.color], BACKGROUND_VALUES[s.background], Color.TRANSPARENT,
                    EDGE_VALUES[s.edge], Color.BLACK, null,
                )
            )
            v.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * SIZE_SCALE[s.size])
            v.setBottomPaddingFraction(if (s.position == 1) RAISED_BOTTOM else SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
        }
    }
}

/** The current style, shared by every video surface so a change shows immediately. */
object SubtitleStyleState {
    var style by mutableStateOf(SubStyle())
}
