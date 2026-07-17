package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

class PowerButtonReceiver : BroadcastReceiver() {
    companion object {
        private var lastPressTime: Long = 0
        private const val DOUBLE_PRESS_INTERVAL = 600 // ms
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_SCREEN_OFF == action || Intent.ACTION_SCREEN_ON == action) {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastPressTime < DOUBLE_PRESS_INTERVAL) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                    return
                }
                val serviceIntent = Intent(context, MyraOverlayService::class.java).apply {
                    putExtra("action", "SHOW_OVERLAY")
                }
                try {
                    context.startForegroundService(serviceIntent)
                } catch (e: Exception) {
                    try {
                        context.startService(serviceIntent)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
                lastPressTime = 0
            } else {
                lastPressTime = currentTime
            }
        }
    }
}
