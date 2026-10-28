package com.sparky.iptv.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.sparky.iptv.R
import com.sparky.iptv.data.AppLog
import com.sparky.iptv.data.LoadStep
import com.sparky.iptv.data.StepStatus
import com.sparky.iptv.setup.Net
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.Screen
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.rememberNow
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

@Composable
fun LoadingScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val err = vm.loadError
    val steps = vm.loadSteps
    val now = rememberNow(1_000L)
    val ip = remember(err) { Net.localIp(ctx) }
    val online = remember(err, now / 5_000) { vm.repo.isOnline() }
    val crash = remember { AppLog.takeUnseenCrashSummary() }
    val retry = remember { FocusRequester() }

    // Overall progress: finished steps count fully, the active step counts half.
    val done = steps.count { it.status == StepStatus.DONE || it.status == StepStatus.FAILED }
    val active = steps.any { it.status == StepStatus.ACTIVE }
    val target = vm.loadFraction ?: if (steps.isEmpty()) 0f else (done + if (active) 0.5f else 0f) / steps.size
    val fraction by animateFloatAsState(target, tween(600), label = "progress")
    val elapsed = if (vm.loadStartedAt > 0) ((now - vm.loadStartedAt) / 1000).coerceAtLeast(0) else 0

    Row(
        Modifier.fillMaxSize().background(C.Bg).padding(horizontal = 112.u, vertical = 72.u),
        horizontalArrangement = Arrangement.spacedBy(80.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(360.u), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(20.u)) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier.size(140.u).clip(RoundedCornerShape(32.u)),
            )
            T(if (err == null) "Loading your channels" else "Couldn't load your playlist", size = 28, weight = FontWeight.Bold, maxLines = 2)
            T(
                if (err == null) "The first load can take a minute for large playlists. Next time the app opens instantly."
                else err,
                size = 15, color = C.Muted, maxLines = 4, lineHeight = 1.5f,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.u).clip(CircleShape).background(if (online) C.Ok else C.Accent))
                Spacer(Modifier.width(10.u))
                T(
                    if (online) "Connected to the internet${ip?.let { " · $it" } ?: ""}" else "No internet connection",
                    size = 13, color = C.Dim,
                )
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.u)) {
            Row(verticalAlignment = Alignment.Bottom) {
                T(
                    if (err == null) "${(fraction * 100).toInt()}%" else "Stopped",
                    size = 40, weight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                T("%d:%02d elapsed".format(elapsed / 60, elapsed % 60), size = 14, color = C.Dim)
            }
            ProgressTrack(fraction, running = err == null)
            Spacer(Modifier.height(8.u))
            steps.forEach { StepRow(it) }

            if (err != null) {
                Spacer(Modifier.height(10.u))
                Row(horizontalArrangement = Arrangement.spacedBy(14.u)) {
                    PillButton("Try again", Modifier.focusRequester(retry), primary = true) { vm.load() }
                    PillButton("Change login") { vm.changeLogin() }
                    PillButton("Diagnostics") { vm.push(Screen.Diagnostics) }
                }
                LaunchedEffect(err) { delay(100); runCatching { retry.requestFocus() } }
            }
            if (crash != null) {
                Spacer(Modifier.height(6.u))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.u)).background(Color(0xFF2A1A17))
                        .border(1.u, Color(0xFF6B3A30), RoundedCornerShape(12.u)).padding(16.u),
                    verticalArrangement = Arrangement.spacedBy(4.u),
                ) {
                    T("The app closed unexpectedly last time", size = 13, weight = FontWeight.SemiBold, color = Color(0xFFF2C4B8))
                    T(crash, size = 12, color = Color(0xFFD9A99C), maxLines = 3)
                }
            }
        }
    }
}

@Composable
private fun StepRow(s: LoadStep) {
    Row(
        Modifier.fillMaxWidth().height(44.u).clip(RoundedCornerShape(10.u))
            .background(if (s.status == StepStatus.ACTIVE) C.Panel else Color.Transparent)
            .padding(horizontal = 14.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusMark(s.status)
        Spacer(Modifier.width(14.u))
        T(
            s.label, size = 16,
            weight = if (s.status == StepStatus.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
            color = if (s.status == StepStatus.PENDING) C.Dim else C.Text,
            modifier = Modifier.weight(1f),
        )
        T(
            s.detail, size = 14,
            color = when (s.status) {
                StepStatus.FAILED -> C.Accent
                StepStatus.DONE -> C.Muted
                else -> C.Text2
            },
        )
    }
}

@Composable
private fun StatusMark(status: StepStatus) {
    val size = 18.u
    when (status) {
        StepStatus.DONE -> Box(Modifier.size(size).clip(CircleShape).background(C.Ok), contentAlignment = Alignment.Center) {
            T("✓", size = 13, weight = FontWeight.Bold, color = C.Bg)
        }
        StepStatus.FAILED -> Box(Modifier.size(size).clip(CircleShape).background(C.Accent), contentAlignment = Alignment.Center) {
            T("!", size = 13, weight = FontWeight.Bold, color = C.Bg)
        }
        StepStatus.ACTIVE -> {
            val t = rememberInfiniteTransition(label = "pulse")
            val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
            Box(Modifier.size(size).clip(CircleShape).background(C.Text.copy(alpha = a)))
        }
        StepStatus.PENDING -> Box(Modifier.size(size).clip(CircleShape).border(1.5f.u, C.Line2, CircleShape))
    }
}

/** A progress bar with a moving sheen so it's obvious the app is still working. */
@Composable
private fun ProgressTrack(fraction: Float, running: Boolean) {
    val t = rememberInfiniteTransition(label = "sheen")
    val x by t.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "x")
    BoxWithConstraints(Modifier.fillMaxWidth().height(8.u).clip(RoundedCornerShape(4.u)).background(C.Line)) {
        val w = maxWidth
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(if (running) C.Text else C.Accent))
        if (running) {
            Box(
                Modifier.fillMaxHeight().width(w * 0.18f).offset(x = w * x)
                    .background(Color.White.copy(alpha = 0.18f))
            )
        }
    }
}
