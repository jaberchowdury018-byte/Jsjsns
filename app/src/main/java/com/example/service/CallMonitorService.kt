package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat

class CallMonitorService : Service() {

    private lateinit var telephonyManager: TelephonyManager
    private var phoneStateListener: PhoneStateListener? = null

    companion object {
        const val CHANNEL_ID = "MyraCallMonitorChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CALL_ENDED = "com.myra.CALL_ENDED"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification("Monitoring incoming calls...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        setupPhoneStateListener()
    }

    private fun setupPhoneStateListener() {
        phoneStateListener = object : PhoneStateListener() {
            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        val number = phoneNumber ?: ""
                        val name = resolveCallerName(number)
                        val intent = Intent(this@CallMonitorService, com.example.ui.main.MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("INCOMING_CALL", true)
                            putExtra("CALLER_NAME", name)
                        }
                        startActivity(intent)
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        val intent = Intent(ACTION_CALL_ENDED)
                        sendBroadcast(intent)
                    }
                }
            }
        }
        try {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun resolveCallerName(phoneNumber: String): String {
        if (phoneNumber.isEmpty()) return "Unknown"
        var contactName = phoneNumber
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                contactName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return contactName
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val appName = getString(com.example.R.string.app_name)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$appName Call Monitor")
            .setContentText(content)
            .setSmallIcon(com.example.R.drawable.ic_myra_notif)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        phoneStateListener?.let {
            telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
        }
    }
}
