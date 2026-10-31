package com.sparky.iptv.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.sparky.iptv.data.AppLog
import com.sparky.iptv.data.Updater
import com.sparky.iptv.setup.LogServer
import com.sparky.iptv.setup.Net
import com.sparky.iptv.setup.Qr
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.Label
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

@Composable
fun DiagnosticsScreen(vm: AppViewModel) {
    BackHandler { vm.pop() }
    val ctx = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    val lines = remember(refreshTick) { AppLog.text().lines().takeLast(200).reversed() }
    val crash = remember(refreshTick) { AppLog.lastCrash() }
    val ip = remember { Net.localIp(ctx) }
    var url by remember { mutableStateOf<String?>(null) }
    val qr = remember(url) { url?.let { Qr.bitmap(it, 360) } }
    val header = remember {
        {
            val v = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
            buildString {
                // The version name already includes "(build N)".
                appendLine("Sparky IPTV $v")
                appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                val rt = Runtime.getRuntime()
                appendLine("Memory: ${rt.totalMemory() / 1048576} MB used of ${rt.maxMemory() / 1048576} MB")
                val lib = vm.library
                appendLine("Library: ${lib.channels.size} channels · ${lib.movies.size} movies · ${lib.series.size} series")
                val archive = lib.channels.filter { it.archiveDays > 0 }
                appendLine(
                    when {
                        !vm.prefs.catchupScanned -> "Catch-up: checking at the next playlist update"
                        archive.isEmpty() -> "Catch-up: not offered by your provider"
                        else -> "Catch-up: ${archive.size} channels · up to ${archive.maxOf { it.archiveDays }} days"
                    }
                )
                val sw = vm.switchTimes.toList()
                if (sw.isNotEmpty()) appendLine("Channel switch: average %.1f s (last %d)".format(sw.average() / 1000.0, sw.size))
                vm.catchupTest?.let { appendLine(it) }
                appendLine("Playlist type: ${vm.prefs.playlist?.type ?: "none"} · live format ${vm.prefs.liveFormat}")
                appendLine("Stream: ${com.sparky.iptv.player.PlayerHolder.streamState()}")
                appendLine(
                    if (vm.sleepMinutes == 0) "Sleep timer: off"
                    else "Sleep timer: ${vm.sleepMinutes} min · ${(vm.sleepLeftMs() + 59_999L) / 60_000L} min left"
                )
            }
        }
    }
    DisposableEffect(Unit) {
        val s = LogServer.startOnFreePort(header)
        url = if (s != null && ip != null) "http://$ip:${s.listeningPort}/?k=${s.token}" else null
        AppLog.i("Diagnostics opened")
        onDispose { s?.stop() }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }

    Row(Modifier.fillMaxSize().padding(horizontal = 64.u, vertical = 44.u), horizontalArrangement = Arrangement.spacedBy(48.u)) {
        Column(Modifier.width(340.u).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.u)) {
            T("Diagnostics", size = 30, weight = FontWeight.Bold)
            T("Scan to open the full log on your phone, then copy it and send it to Claude.", size = 14, color = C.Muted, maxLines = 3, lineHeight = 1.5f)
            Box(
                Modifier.size(220.u).clip(RoundedCornerShape(16.u)).background(if (qr != null) C.Text else C.Raised).padding(14.u),
                contentAlignment = Alignment.Center,
            ) {
                if (qr != null) Image(bitmap = qr, contentDescription = "Log QR code", modifier = Modifier.fillMaxSize())
                else T("No Wi-Fi", size = 16, color = C.Dim)
            }
            if (url != null) T("Scan with your phone's camera", size = 13, color = C.Dim)
            T(remember(refreshTick, vm.catchupTest, vm.switchTimes.size) { header().trim() }, size = 12, color = C.Dim, maxLines = 10, lineHeight = 1.5f)
            Spacer(Modifier.weight(1f))
            PillButton(if (vm.catchupTest == "Testing…") "Testing…" else "Test catch-up") { vm.testCatchup() }
            Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
                PillButton("Refresh", Modifier.focusRequester(first)) { refreshTick++ }
                if (crash != null) PillButton("Clear crash") { AppLog.clearCrash(); refreshTick++ }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.u)) {
            if (crash != null) {
                Label("Last crash", color = C.Accent)
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.u)).background(Color(0xFF2A1A17)).padding(14.u)) {
                    T(crash.lines().take(6).joinToString("\n"), size = 12, color = Color(0xFFF2C4B8), maxLines = 6)
                }
            }
            Label("Recent activity (newest first)")
            val state = rememberLazyListState()
            LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.u)) {
                items(lines) { line ->
                    FocusItem(onClick = {}, container = Color.Transparent, focusedContainer = C.Panel, shape = RoundedCornerShape(6.u), modifier = Modifier.fillMaxWidth()) { _ ->
                        T(
                            line, size = 12, maxLines = 3,
                            color = when {
                                line.contains("ERROR") || line.contains("CRASH") -> C.Accent
                                line.contains("WARN") -> Color(0xFFE6C17A)
                                else -> C.Text2
                            },
                            modifier = Modifier.padding(horizontal = 8.u, vertical = 4.u),
                        )
                    }
                }
            }
        }
    }
}
