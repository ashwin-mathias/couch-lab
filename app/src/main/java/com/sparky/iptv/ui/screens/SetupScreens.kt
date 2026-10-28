package com.sparky.iptv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.sparky.iptv.R
import com.sparky.iptv.setup.Qr
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

@Composable
fun SetupScreen(vm: AppViewModel) {
    DisposableEffect(Unit) {
        vm.startSetup()
        onDispose { /* server is stopped after a successful login or when leaving */ }
    }
    val hasPlaylist = vm.prefs.playlist != null
    BackHandler(enabled = hasPlaylist) {
        vm.stopSetup()
        vm.pop()
    }
    val url = vm.setupUrl
    val qr = remember(url) { url?.let { Qr.bitmap(it) } }

    Column(
        Modifier.fillMaxSize().background(C.Bg).padding(horizontal = 112.u, vertical = 64.u),
        verticalArrangement = Arrangement.spacedBy(40.u),
    ) {
        Brand()
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.u)) {
                T("Set up with your phone", size = 44, weight = FontWeight.Bold)
                T(
                    "No typing with the remote. Scan the code, enter your login on your phone, and the TV fills in the rest.",
                    size = 18, color = C.Muted, maxLines = 3, lineHeight = 1.6f,
                    modifier = Modifier.widthIn(max = 520.u),
                )
                Spacer(Modifier.height(4.u))
                Step("1", "Open your phone camera and scan the code")
                Step("2", "Enter the server URL, username and password")
                Step("3", "Tap Send to TV — channels load automatically")
            }
            Spacer(Modifier.width(80.u))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.u)) {
                Box(
                    Modifier.size(300.u).clip(RoundedCornerShape(18.u)).background(C.Text).padding(24.u),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) {
                        Image(bitmap = qr, contentDescription = "QR code", modifier = Modifier.fillMaxSize())
                    } else {
                        T("No Wi-Fi", size = 18, color = C.OnFocus, weight = FontWeight.Bold)
                    }
                }
                if (url != null) {
                    T("Or open on any device on this Wi-Fi", size = 13, color = C.Dim)
                    T(url.removePrefix("http://"), size = 18, weight = FontWeight.SemiBold)
                } else {
                    T("Connect this TV to Wi-Fi to continue", size = 15, color = C.Muted)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.u).background(C.Line))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.u).clip(CircleShape).background(if (vm.setupBusy) C.Accent else C.Ok))
            Spacer(Modifier.width(10.u))
            T(if (vm.setupBusy) "Checking your login…" else "Waiting for your phone…", size = 14, color = C.Dim)
            Spacer(Modifier.weight(1f))
            T(
                if (hasPlaylist) "Press Back to keep your current login" else "Your login stays on your home network",
                size = 14, color = C.Dim,
            )
        }
    }
}

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = null,
            modifier = Modifier.size(44.u).clip(RoundedCornerShape(11.u)),
        )
        Spacer(Modifier.width(14.u))
        T("Sparky IPTV", size = 18, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        T(n, size = 14, weight = FontWeight.Bold, color = C.Dim, modifier = Modifier.width(30.u))
        T(text, size = 17)
    }
}

@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    icon: Int? = null,
    onClick: () -> Unit,
) {
    FocusItem(
        onClick = onClick,
        modifier = modifier.height(50.u),
        container = if (primary) C.Raised else C.Panel,
        shape = RoundedCornerShape(12.u),
    ) { focused ->
        Row(
            Modifier.padding(horizontal = 24.u).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                com.sparky.iptv.ui.components.Icon(icon, tint = if (focused) C.OnFocus else C.Text, size = 18.u)
                Spacer(Modifier.width(10.u))
            }
            T(text, size = 15, weight = FontWeight.SemiBold, color = if (focused) C.OnFocus else C.Text)
        }
    }
}
