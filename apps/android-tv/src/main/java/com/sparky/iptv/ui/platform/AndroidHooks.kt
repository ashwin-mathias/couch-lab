package com.sparky.iptv.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.res.painterResource
import com.sparky.iptv.ui.components.TvKey

/*
 * Android's side of the shared screens' hooks (3.1). The desktop app has the same functions.
 */

/** The remote key behind a Compose key event. */
fun KeyEvent.tv(): TvKey = nativeKeyEvent.tv()

fun android.view.KeyEvent.tv(): TvKey = TvKey(keyCode, action, repeatCount, eventTime, downTime)

/** Back: Android's own back dispatcher. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) =
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)

/** An image from the app's resources (R.drawable.*). */
@Composable
fun painterRes(id: Int): Painter = painterResource(id)
