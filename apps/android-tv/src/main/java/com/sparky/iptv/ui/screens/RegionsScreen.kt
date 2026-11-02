package com.sparky.iptv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import com.sparky.iptv.data.Repository
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

/** Pick the regions to show. Saved once; change or reset anytime from Settings. */
@Composable
fun RegionsScreen(vm: AppViewModel, firstRun: Boolean) {
    BackHandler { vm.pop() }
    val regions = vm.index.regions
    val chosen = vm.myRegions
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }
    val shown = if (chosen.isEmpty()) regions.sumOf { it.channels } else regions.filter { it.code in chosen }.sumOf { it.channels }

    Column(Modifier.fillMaxSize().padding(horizontal = 64.u, vertical = 40.u), verticalArrangement = Arrangement.spacedBy(18.u)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.u)) {
                T(if (firstRun) "Which regions do you watch?" else "My regions", size = 30, weight = FontWeight.Bold)
                T(
                    "Only these regions appear in Live TV, the guide, search and movies. Favorites always show. " +
                        "You can change this anytime in Settings.",
                    size = 15, color = C.Muted, maxLines = 2, lineHeight = 1.5f,
                )
            }
            Spacer(Modifier.width(24.u))
            Column(horizontalAlignment = Alignment.End) {
                T(if (chosen.isEmpty()) "Showing all regions" else "${chosen.size} selected", size = 14, weight = FontWeight.SemiBold)
                T("${Repository.fmt(shown)} channels", size = 13, color = C.Dim)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.u)) {
            PillButton("Done", Modifier.focusRequester(first), primary = true) { vm.pop() }
            PillButton("Show all regions") { vm.resetRegions() }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(12.u),
            verticalArrangement = Arrangement.spacedBy(12.u),
            contentPadding = PaddingValues(bottom = 32.u, top = 6.u),
        ) {
            itemsIndexed(regions, key = { idx, r -> "$idx:" + (r.code) }) { _, r ->
                val on = r.code in chosen
                FocusItem(
                    onClick = { vm.toggleRegion(r.code) },
                    container = if (on) C.Raised else C.Panel,
                    shape = RoundedCornerShape(12.u),
                    modifier = Modifier.fillMaxWidth().height(64.u),
                ) { f ->
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.u), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(22.u).clip(RoundedCornerShape(6.u))
                                .background(if (on) (if (f) C.OnFocus else C.Text) else androidx.compose.ui.graphics.Color.Transparent)
                                .border(1.5f.u, if (f) C.OnFocus else if (on) C.Text else C.Dim, RoundedCornerShape(6.u)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) T("✓", size = 13, weight = FontWeight.Bold, color = if (f) C.Text else C.Bg)
                        }
                        Spacer(Modifier.width(12.u))
                        Column(Modifier.weight(1f)) {
                            T(r.label, size = 15, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text)
                            T(
                                (if (r.label != r.code) "${r.code} · " else "") + "${Repository.fmt(r.channels)} channels",
                                size = 12, color = if (f) C.OnFocusMuted else C.Dim,
                            )
                        }
                    }
                }
            }
        }
    }
}
