package com.sparky.iptv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sparky.iptv.data.Favs
import com.sparky.iptv.setup.Qr
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.FavMenuRequest
import com.sparky.iptv.ui.LIST_FOLDERS
import com.sparky.iptv.ui.components.C
import com.sparky.iptv.ui.components.FocusItem
import com.sparky.iptv.ui.components.Inter
import com.sparky.iptv.ui.components.Label
import com.sparky.iptv.ui.components.T
import com.sparky.iptv.ui.components.su
import com.sparky.iptv.ui.components.u
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Moving a favorite
// ---------------------------------------------------------------------------------------------

/** Outline on the favorite that is being moved. */
fun Modifier.movingOutline(on: Boolean): Modifier =
    if (on) this.border(3.u, C.Accent, RoundedCornerShape(14.u)) else this

/** Scrolls so item [index] is fully on screen. */
suspend fun LazyListState.keepVisible(index: Int) {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) { scrollToItem(index); return }
    val start = info.viewportStartOffset
    val end = info.viewportEndOffset - info.afterContentPadding
    if (item.offset < start) scrollBy((item.offset - start).toFloat())
    else if (item.offset + item.size > end) scrollBy((item.offset + item.size - end).toFloat())
}

/** Same for a vertical grid. */
suspend fun LazyGridState.keepVisible(index: Int) {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) { scrollToItem(index); return }
    val start = info.viewportStartOffset
    val end = info.viewportEndOffset - info.afterContentPadding
    val top = item.offset.y
    val bottom = top + item.size.height
    if (top < start) scrollBy((top - start).toFloat())
    else if (bottom > end) scrollBy((bottom - end).toFloat())
}

/** While a favorite of [listId] moves, keeps it on screen in a lazy list. [indexOf] maps its key to the lazy index. */
@Composable
fun FollowMove(vm: AppViewModel, listId: String?, state: LazyListState, indexOf: (String) -> Int) {
    val m = vm.moving
    LaunchedEffect(m?.working) {
        if (m == null || m.listId != listId) return@LaunchedEffect
        val i = indexOf(m.key)
        if (i >= 0) state.keepVisible(i)
    }
}

@Composable
fun FollowMoveGrid(vm: AppViewModel, listId: String?, state: LazyGridState, indexOf: (String) -> Int) {
    val m = vm.moving
    LaunchedEffect(m?.working) {
        if (m == null || m.listId != listId) return@LaunchedEffect
        val i = indexOf(m.key)
        if (i >= 0) state.keepVisible(i)
    }
}

/** Hint at the top of the screen while something moves. */
@Composable
fun MoveHint(vm: AppViewModel, modifier: Modifier = Modifier) {
    val m = vm.moving ?: return
    val arrows = when {
        m.upDown > 0 && m.leftRight > 0 -> "◀ ▲ ▼ ▶"
        m.leftRight > 0 -> "◀ ▶"
        else -> "▲ ▼"
    }
    Box(modifier.clip(RoundedCornerShape(999.u)).background(C.Accent)) {
        T("Moving ${vm.nameOf(m.key) ?: ""} · $arrows to move · OK to drop · Back to cancel",
            size = 15, weight = FontWeight.SemiBold, color = C.Bg,
            modifier = Modifier.padding(horizontal = 22.u, vertical = 10.u))
    }
}

// ---------------------------------------------------------------------------------------------
// Menu pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun Panel(width: Int = 460, content: @Composable () -> Unit) {
    Column(
        Modifier.width(width.u).clip(RoundedCornerShape(18.u)).background(C.Panel).border(1.u, C.Line, RoundedCornerShape(18.u))
            .padding(horizontal = 22.u, vertical = 22.u),
        verticalArrangement = Arrangement.spacedBy(6.u),
    ) { content() }
}

/** One row in a menu: title on the left, a short value on the right. */
@Composable
fun MenuRow(title: String, value: String = "", modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusItem(onClick = onClick, container = C.Raised, shape = RoundedCornerShape(10.u), modifier = modifier.fillMaxWidth().height(48.u)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 16.u), verticalAlignment = Alignment.CenterVertically) {
            T(title, size = 16, weight = FontWeight.SemiBold, color = if (f) C.OnFocus else C.Text, modifier = Modifier.weight(1f))
            if (value.isNotEmpty()) T(value, size = 14, color = if (f) C.OnFocusMuted else C.Dim, textAlign = TextAlign.End)
        }
    }
}

/** A text field for a folder name. Press OK on it to type with the TV keyboard. */
@Composable
fun NameField(value: String, onChange: (String) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier, placeholder: String = "Folder name") {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier.fillMaxWidth().height(50.u)
            .background(C.Bg, RoundedCornerShape(10.u))
            .border(if (focused) 2.u else 1.u, if (focused) C.Text else C.Line, RoundedCornerShape(10.u))
            .padding(horizontal = 16.u),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) T(placeholder, size = 16, color = C.Dim)
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(Favs.MAX_NAME)) },
            singleLine = true,
            textStyle = TextStyle(fontFamily = Inter, fontSize = 16.su, color = C.Text),
            cursorBrush = SolidColor(C.Text),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Hold-OK menu: Move · Folders… · Remove
// ---------------------------------------------------------------------------------------------

@Composable
fun FavMenuDialog(vm: AppViewModel, r: FavMenuRequest) {
    Dialog(onDismissRequest = { vm.favMenu = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
        Panel {
            Label("Favorite")
            T(r.label, size = 20, weight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(10.u))
            var firstUsed = false
            fun mod(): Modifier = if (!firstUsed) { firstUsed = true; Modifier.focusRequester(first) } else Modifier
            if (r.moveList != null) {
                MenuRow("Move", "Change its place", mod()) {
                    vm.favMenu = null
                    vm.startMove(r.moveList, r.key, r.upDown, r.leftRight)
                }
            }
            if (r.key.startsWith("live:")) {
                val n = vm.folders.count { r.key in it.keys }
                MenuRow("Folders…", if (n == 0) "Not in a folder" else if (n == 1) "In 1 folder" else "In $n folders", mod()) {
                    vm.favMenu = null
                    vm.folderPicker = r.key
                }
            }
            MenuRow("Remove from favorites", "", mod()) {
                vm.favMenu = null
                vm.removeFavoriteFromMenu(r.key, r.label)
            }
            Spacer(Modifier.height(4.u))
            T("Back to close", size = 12, color = C.Dim)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Folder checklist for one channel
// ---------------------------------------------------------------------------------------------

@Composable
fun FolderPickerDialog(vm: AppViewModel, key: String) {
    val name = vm.nameOf(key) ?: "This channel"
    fun close() {
        vm.folderPicker = null
        val n = vm.folders.count { key in it.keys }
        vm.toast = when (n) { 0 -> "$name isn't in a folder"; 1 -> "$name is in 1 folder"; else -> "$name is in $n folders" }
    }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var adding by remember { mutableStateOf(vm.folders.isEmpty()) }
        var text by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        val first = remember { FocusRequester() }
        LaunchedEffect(adding) { delay(80); runCatching { first.requestFocus() } }
        Panel(width = 480) {
            Label("Folders")
            T(name, size = 20, weight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(10.u))
            val fs = vm.folders
            LazyColumn(Modifier.heightIn(max = 380.u), verticalArrangement = Arrangement.spacedBy(6.u)) {
                itemsIndexed(fs, key = { _, f -> f.id }) { i, f ->
                    val on = key in f.keys
                    MenuRow(f.name, if (on) "✓  In folder" else "", if (i == 0 && !adding) Modifier.focusRequester(first) else Modifier) {
                        vm.setInFolder(f.id, key, !on)
                    }
                }
            }
            if (adding) {
                NameField(text, { text = it; error = null }, onDone = {
                    error = vm.createFolder(text, key)
                    if (error == null) { text = ""; adding = false }
                }, Modifier.focusRequester(first))
                MenuRow("Create folder") {
                    error = vm.createFolder(text, key)
                    if (error == null) { text = ""; adding = false }
                }
            } else {
                MenuRow("New folder…", "", if (fs.isEmpty()) Modifier.focusRequester(first) else Modifier) { adding = true }
            }
            error?.let { T(it, size = 14, color = C.Accent) }
            Spacer(Modifier.height(4.u))
            T("OK ticks or unticks · Back to close", size = 12, color = C.Dim)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings → Library → Favorite folders
// ---------------------------------------------------------------------------------------------

@Composable
fun FavFoldersScreen(vm: AppViewModel) {
    BackHandler { vm.pop() }
    DisposableEffect(Unit) {
        vm.startPhonePage(AppViewModel.PhonePage.FAVORITES)
        onDispose { vm.stopPhonePage() }
    }
    var adding by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var actionsFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(adding) { delay(150); runCatching { first.requestFocus() } }
    val ids = vm.keysFor(LIST_FOLDERS)
    val state = rememberLazyListState()
    FollowMove(vm, LIST_FOLDERS, state) { k -> ids.indexOf(k) + 1 }

    Row(Modifier.fillMaxSize().background(C.Bg).padding(horizontal = 96.u, vertical = 56.u)) {
        Column(Modifier.width(620.u).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.u)) {
            T("Favorite folders", size = 34, weight = FontWeight.Bold)
            T("Folders show under Favorites in Live TV and the TV Guide, and as rows on Home. Hold OK on a favorite channel to put it in a folder.",
                size = 15, color = C.Muted, maxLines = 3, lineHeight = 1.5f)
            Spacer(Modifier.height(6.u))
            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(6.u), contentPadding = PaddingValues(bottom = 24.u)) {
                item(key = "new") {
                    if (adding) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.u)) {
                            NameField(text, { text = it; error = null }, onDone = {
                                error = vm.createFolder(text)
                                if (error == null) { text = ""; adding = false }
                            }, Modifier.focusRequester(first))
                            MenuRow("Create folder") {
                                error = vm.createFolder(text)
                                if (error == null) { text = ""; adding = false }
                            }
                            error?.let { T(it, size = 14, color = C.Accent) }
                        }
                    } else {
                        MenuRow("New folder", "+", Modifier.focusRequester(first)) { adding = true }
                    }
                }
                itemsIndexed(ids, key = { _, id -> id }) { _, id ->
                    val f = vm.folder(id) ?: return@itemsIndexed
                    val count = vm.folderChannels(id).size
                    MenuRow(f.name, if (count == 1) "1 channel" else "$count channels",
                        Modifier.movingOutline(vm.moving?.key == id)) { actionsFor = id }
                }
                if (ids.isEmpty()) item(key = "empty") {
                    T("No folders yet. Make one here or on your phone, for example US News.", size = 15, color = C.Dim, maxLines = 2,
                        modifier = Modifier.padding(top = 8.u))
                }
            }
        }
        Spacer(Modifier.width(80.u))
        PhoneQr(vm.phoneUrl, "Edit on your phone",
            "Name folders, add channels and put them in order on your phone. Tap Save and the TV updates.")
    }

    actionsFor?.let { id ->
        val f = vm.folder(id)
        if (f == null) { actionsFor = null; return@let }
        FolderActions(vm, f.id, f.name,
            onClose = { actionsFor = null },
            onRename = { actionsFor = null; renaming = id })
    }
    renaming?.let { id ->
        val f = vm.folder(id)
        if (f == null) { renaming = null; return@let }
        RenameDialog(vm, id, f.name) { renaming = null }
    }
}

@Composable
private fun FolderActions(vm: AppViewModel, id: String, name: String, onClose: () -> Unit, onRename: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var confirmDelete by remember { mutableStateOf(false) }
        LaunchedEffect(confirmDelete) { if (confirmDelete) { delay(3000); confirmDelete = false } }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
        Panel {
            Label("Folder")
            T(name, size = 20, weight = FontWeight.Bold)
            Spacer(Modifier.height(10.u))
            MenuRow("Rename", "", Modifier.focusRequester(first)) { onRename() }
            MenuRow("Move", "Change its place") { onClose(); vm.startMove(LIST_FOLDERS, id, 1, 0) }
            MenuRow(if (confirmDelete) "Press OK again to delete" else "Delete", "Channels stay in Favorites") {
                if (confirmDelete) { onClose(); vm.deleteFolder(id) } else confirmDelete = true
            }
            Spacer(Modifier.height(4.u))
            T("Back to close", size = 12, color = C.Dim)
        }
    }
}

@Composable
private fun RenameDialog(vm: AppViewModel, id: String, current: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var text by remember { mutableStateOf(current) }
        var error by remember { mutableStateOf<String?>(null) }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
        fun save() {
            error = if (text.trim() == current) null else vm.renameFolder(id, text)
            if (error == null) onClose()
        }
        Panel {
            Label("Rename folder")
            Spacer(Modifier.height(6.u))
            NameField(text, { text = it; error = null }, onDone = { save() }, Modifier.focusRequester(first))
            MenuRow("Save") { save() }
            error?.let { T(it, size = 14, color = C.Accent) }
        }
    }
}

/** The QR column used by the phone pages. */
@Composable
private fun PhoneQr(url: String?, title: String, text: String) {
    val qr = remember(url) { url?.let { Qr.bitmap(it) } }
    Column(Modifier.widthIn(max = 420.u), verticalArrangement = Arrangement.spacedBy(16.u)) {
        Label(title)
        Box(
            Modifier.size(260.u).clip(RoundedCornerShape(18.u)).background(C.Text).padding(20.u),
            contentAlignment = Alignment.Center,
        ) {
            if (qr != null) Image(bitmap = qr, contentDescription = "QR code", modifier = Modifier.fillMaxSize())
            else T("No Wi-Fi", size = 18, color = C.OnFocus, weight = FontWeight.Bold)
        }
        T(if (url != null) text else "Connect this TV to Wi-Fi to use your phone.", size = 15, color = C.Muted, maxLines = 4, lineHeight = 1.5f)
        if (url != null) T("Scan with your phone's camera while on this Wi-Fi. The code works for 10 minutes.", size = 13, color = C.Dim, maxLines = 2)
    }
}

// ---------------------------------------------------------------------------------------------
// Settings → App → Back up settings / Restore settings
// ---------------------------------------------------------------------------------------------

@Composable
fun BackupScreen(vm: AppViewModel, restore: Boolean) {
    BackHandler { vm.pop() }
    DisposableEffect(restore) {
        vm.startPhonePage(if (restore) AppViewModel.PhonePage.RESTORE else AppViewModel.PhonePage.BACKUP)
        onDispose { vm.stopPhonePage() }
    }
    Row(
        Modifier.fillMaxSize().background(C.Bg).padding(horizontal = 112.u, vertical = 64.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(22.u)) {
            T(if (restore) "Restore settings" else "Back up settings", size = 40, weight = FontWeight.Bold)
            T(
                if (restore) "Bring back your login, favorites and settings from a backup file on your phone."
                else "Saves your login, favorites and settings to your phone, as one file you keep in Files or iCloud.",
                size = 18, color = C.Muted, maxLines = 3, lineHeight = 1.6f, modifier = Modifier.widthIn(max = 560.u),
            )
            Spacer(Modifier.height(4.u))
            val steps = if (restore) listOf(
                "Scan the code with your phone",
                "Pick your backup file and enter its passphrase",
                "Choose Add or Replace, then tap Restore",
            ) else listOf(
                "Scan the code with your phone",
                "Choose what to include and set a passphrase",
                "Tap Download backup and keep the file",
            )
            steps.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("${i + 1}", size = 14, weight = FontWeight.Bold, color = C.Dim, modifier = Modifier.width(30.u))
                    T(s, size = 17)
                }
            }
            Spacer(Modifier.height(8.u))
            T("Press Back when you're done. The page stops working when you leave this screen.", size = 14, color = C.Dim, maxLines = 2)
        }
        Spacer(Modifier.width(80.u))
        PhoneQr(vm.phoneUrl, "Scan with your phone",
            if (restore) "On a fresh install, use Restore from a backup file on the phone's login page instead."
            else "Your login is encrypted with your passphrase. Nothing leaves your home network.")
    }
}
