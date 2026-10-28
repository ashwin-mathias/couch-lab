package com.sparky.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.sparky.iptv.data.AppLog
import com.sparky.iptv.player.PlayerHolder
import com.sparky.iptv.ui.App
import com.sparky.iptv.ui.AppViewModel
import com.sparky.iptv.ui.Screen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLog.init(applicationContext)
        super.onCreate(savedInstanceState)
        setContent { App(vm) }
    }

    override fun onStart() {
        super.onStart()
        if (vm.current is Screen.Player || vm.current == Screen.Home) PlayerHolder.resume()
    }

    override fun onStop() {
        PlayerHolder.pause()
        super.onStop()
    }
}
