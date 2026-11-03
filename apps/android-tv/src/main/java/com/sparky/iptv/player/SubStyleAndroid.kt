package com.sparky.iptv.player

import android.graphics.Color
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView

/** Edge types for SubStyle.EDGES (Drop shadow, Outline, None). */
private val EDGE_VALUES = intArrayOf(
    CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, CaptionStyleCompat.EDGE_TYPE_OUTLINE, CaptionStyleCompat.EDGE_TYPE_NONE,
)

/** Applies the style to ExoPlayer's subtitle view. Our style wins over styling embedded in the stream. */
@OptIn(UnstableApi::class)
fun SubStyle.applyTo(v: SubtitleView?) {
    v ?: return
    v.setApplyEmbeddedStyles(false)
    v.setApplyEmbeddedFontSizes(false)
    v.setStyle(
        CaptionStyleCompat(
            SubStyle.COLOR_VALUES[color], SubStyle.BACKGROUND_VALUES[background], Color.TRANSPARENT,
            EDGE_VALUES[edge], Color.BLACK, null,
        )
    )
    v.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * SubStyle.SIZE_SCALE[size])
    v.setBottomPaddingFraction(if (position == 1) SubStyle.RAISED_BOTTOM else SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
}
