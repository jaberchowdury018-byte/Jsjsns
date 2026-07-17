package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val serviceIntent = Intent(context, CallMonitorService::class.java)
            try {
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                context.startService(serviceIntent)
            }
        }
    }
}
