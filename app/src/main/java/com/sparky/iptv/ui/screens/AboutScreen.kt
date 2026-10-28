package com.sparky.iptv.ui.screens

import android.content.Context
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.sparky.iptv.R
import com.sparky.iptv.setup.Net
import com.sparky.iptv.setup.Qr
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.Label
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

data class Release(val version: String, val date: String, val title: String, val notes: List<String>)
data class AboutInfo(val name: String, val role: String, val linkedin: String)

private fun readAsset(ctx: Context, name: String): String? =
    runCatching { ctx.assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()

fun loadAbout(ctx: Context): AboutInfo {
    val o = readAsset(ctx, "about.json")?.let { runCatching { JSONObject(it) }.getOrNull() }
    return AboutInfo(
        name = o?.optString("name").orEmpty().ifBlank { "Ashwin Mathias" },
        role = o?.optString("role").orEmpty(),
        linkedin = o?.optString("linkedin").orEmpty().trim(),
    )
}

fun loadReleases(ctx: Context): List<Release> {
    val arr = readAsset(ctx, "changelog.json")?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val notes = o.optJSONArray("notes")
        Release(
            version = o.optString("version"),
            date = o.optString("date"),
            title = o.optString("title"),
            notes = if (notes == null) emptyList() else (0 until notes.length()).map { notes.optString(it) },
        )
    }
}

private fun prettyDate(iso: String): String = runCatching {
    val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(d!!)
}.getOrDefault(iso)

private val credits = listOf(
    "AndroidX Media3 / ExoPlayer — Apache 2.0",
    "Jetpack Compose for TV — Apache 2.0",
    "OkHttp — Apache 2.0",
    "Coil — Apache 2.0",
    "NanoHTTPD — BSD 3-Clause",
    "ZXing — Apache 2.0",
    "Inter typeface — SIL Open Font License",
)

@Composable
fun AboutScreen(vm: AppViewModel) {
    BackHandler { vm.pop() }
    val ctx = LocalContext.current
    val about = remember { loadAbout(ctx) }
    val releases = remember { loadReleases(ctx) }
    val qr = remember(about.linkedin) {
        about.linkedin.takeIf { it.isNotBlank() }?.let {
            Qr.bitmap(if (it.startsWith("http")) it else "https://$it", 360)
        }
    }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }
    val device = remember { "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}" }
    val ip = remember { Net.localIp(ctx) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }

    Row(Modifier.fillMaxSize().padding(horizontal = 64.u, vertical = 44.u), horizontalArrangement = Arrangement.spacedBy(56.u)) {
        // Left: the person behind the app
        Column(Modifier.width(380.u).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.u)) {
            Label("About")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.avatar),
                    contentDescription = null,
                    modifier = Modifier.size(96.u).clip(CircleShape),
                )
                Spacer(Modifier.width(20.u))
                Column(verticalArrangement = Arrangement.spacedBy(4.u)) {
                    T(about.name, size = 26, weight = FontWeight.Bold)
                    if (about.role.isNotBlank()) T(about.role, size = 14, color = C.Muted)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.u)).background(C.Panel).padding(18.u),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(132.u).clip(RoundedCornerShape(12.u)).background(if (qr != null) C.Text else C.Raised).padding(10.u),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) Image(bitmap = qr, contentDescription = "LinkedIn QR code", modifier = Modifier.fillMaxSize())
                    else T("in", size = 34, weight = FontWeight.Bold, color = C.Dim)
                }
                Spacer(Modifier.width(18.u))
                Column(verticalArrangement = Arrangement.spacedBy(6.u)) {
                    T("Connect on LinkedIn", size = 16, weight = FontWeight.SemiBold)
                    T(
                        if (qr != null) "Scan with your phone camera" else "Link coming soon",
                        size = 13, color = C.Dim,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Info2("Version", "Sparky IPTV $version")
            Info2("Device", device)
            if (ip != null) Info2("Network", ip)
            T(
                "Your login is stored only on this TV and is only sent to your provider.",
                size = 13, color = C.Dim, maxLines = 2, lineHeight = 1.5f,
            )
        }

        // Right: release history (focusable cards so the remote can scroll it)
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.u)) {
            Label("Release history")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.u), contentPadding = PaddingValues(bottom = 24.u)) {
                itemsIndexed(releases, key = { idx, r -> "$idx:" + (r.version + r.date) }) { i, r ->
                    FocusItem(
                        onClick = {},
                        container = C.Panel,
                        focusedContainer = C.Raised,
                        shape = RoundedCornerShape(14.u),
                        modifier = Modifier.fillMaxWidth().let { if (i == 0) it.focusRequester(first) else it },
                    ) { _ ->
                        Column(Modifier.padding(horizontal = 22.u, vertical = 18.u), verticalArrangement = Arrangement.spacedBy(8.u)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                T("Version ${r.version}", size = 13, weight = FontWeight.Bold, color = C.Text2)
                                Spacer(Modifier.width(12.u))
                                T(prettyDate(r.date), size = 13, color = C.Dim)
                                if (i == 0) {
                                    Spacer(Modifier.width(12.u))
                                    T("LATEST", size = 11, weight = FontWeight.Bold, color = C.Accent, letterSpacing = 0.08f)
                                }
                            }
                            T(r.title, size = 18, weight = FontWeight.Bold, color = C.Text)
                            r.notes.forEach { n ->
                                Row {
                                    T("•", size = 14, color = C.Dim, modifier = Modifier.width(18.u))
                                    T(n, size = 14, color = C.Muted, maxLines = 2, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                item {
                    FocusItem(
                        onClick = {},
                        container = C.Bg,
                        focusedContainer = C.Panel,
                        shape = RoundedCornerShape(14.u),
                        modifier = Modifier.fillMaxWidth(),
                    ) { _ ->
                        Column(Modifier.padding(horizontal = 22.u, vertical = 18.u), verticalArrangement = Arrangement.spacedBy(6.u)) {
                            Label("Open-source credits")
                            Spacer(Modifier.height(2.u))
                            credits.forEach { T(it, size = 13, color = C.Dim) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Info2(label: String, value: String) {
    Row {
        T(label, size = 13, color = C.Dim, modifier = Modifier.width(100.u))
        T(value, size = 13, color = C.Text2, modifier = Modifier.weight(1f))
    }
}
