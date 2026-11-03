package com.sparky.iptv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.sparky.iptv.platform.AndroidAppPlatform
import com.sparky.iptv.platform.AndroidPlatform
import com.sparky.iptv.player.ExoEngine
import com.sparky.iptv.player.PlayerHolder
import com.sparky.iptv.ui.App
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.Screen
import com.sparky.iptv.ui.platform.Platform
import com.sparky.iptv.ui.platform.tv
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidPlatform.init(applicationContext)
        // 3.1: the shared screens reach the device through Platform.app and the player through PlayerHolder.
        if (!Platform.isSet()) Platform.app = AndroidAppPlatform(application)
        (Platform.app as? AndroidAppPlatform)?.activity = WeakReference(this)
        PlayerHolder.factory = { ExoEngine(applicationContext) }
        super.onCreate(savedInstanceState)
        setContent { App(vm) }
    }

    // The sleep timer sees every key first (any key in its last minute keeps watching).
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (vm.onAnyKey(event.tv())) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        if (vm.current is Screen.Player || vm.current == Screen.Home) PlayerHolder.resume()
        if (vm.current == Screen.Home) vm.maybeDailyRefresh()
    }

    override fun onStop() {
        PlayerHolder.pause()
        super.onStop()
    }
}
