package com.example.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.model.AppCommand
import com.example.service.AccessibilityHelperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _commandResult = MutableLiveData<String?>()
    val commandResult: LiveData<String?> = _commandResult

    private val appPackageMap = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "telegram" to "org.telegram.messenger",
        "snapchat" to "com.snapchat.android",
        "settings" to "com.android.settings",
        "calculator" to "com.android.calculator2",
        "calendar" to "com.google.android.calendar",
        "clock" to "com.google.android.deskclock",
        "phone" to "com.android.dialer",
        "contacts" to "com.android.contacts",
        "play store" to "com.android.vending",
        "amazon" to "com.amazon.mShop.android.shopping",
        "flipkart" to "com.flipkart.android",
        "paytm" to "net.one97.paytm",
        "phonepe" to "com.phonepe.app",
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "zoom" to "us.zoom.videomeetings",
        "meet" to "com.google.android.apps.meetings",
        "teams" to "com.microsoft.teams",
        "tiktok" to "com.zhiliaoapp.musically",
        "discord" to "com.discord",
        "linkedin" to "com.linkedin.android"
    )

    fun executeCommand(command: AppCommand) {
        viewModelScope.launch(Dispatchers.IO) {
            when (command.type) {
                "WIFI_ON" -> toggleWifi(true)
                "WIFI_OFF" -> toggleWifi(false)
                "BLUETOOTH_ON" -> toggleBluetooth(true)
                "BLUETOOTH_OFF" -> toggleBluetooth(false)
                "FLASHLIGHT_ON" -> toggleFlashlight(true)
                "FLASHLIGHT_OFF" -> toggleFlashlight(false)
                "VOLUME_UP" -> adjustVolume(true)
                "VOLUME_DOWN" -> adjustVolume(false)
                "OPEN_APP" -> openApp(command.params["app_name"] ?: "")
                "CLOSE_APP" -> closeApp()
                "CALL" -> makeCall(command.params["name"] ?: "")
                "SMS" -> sendSms(command.params["name"] ?: "", command.params["message"] ?: "")
                "WHATSAPP_MSG" -> sendWhatsAppMessage(command.params["name"] ?: "", command.params["message"] ?: "")
                "PRIME_CALL" -> makePrimeCall(command.params["index"]?.toIntOrNull() ?: 0)
                "PRIME_MSG" -> sendPrimeMsg(command.params["index"]?.toIntOrNull() ?: 0)
            }
        }
    }

    private fun toggleWifi(enable: Boolean) {
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = enable
            postResult("WiFi has been turned ${if (enable) "on" else "off"}.")
        } catch (e: Exception) {
            // Newer android might block direct state, so offer settings opening
            openSettingsPanel("android.settings.WIFI_SETTINGS")
            postResult("Opening WiFi settings to let you turn it ${if (enable) "on" else "off"}.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun toggleBluetooth(enable: Boolean) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null) {
                if (enable) {
                    adapter.enable()
                } else {
                    adapter.disable()
                }
                postResult("Bluetooth has been turned ${if (enable) "on" else "off"}.")
            } else {
                postResult("Bluetooth is not supported on this device.")
            }
        } catch (e: Exception) {
            openSettingsPanel(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            postResult("Opening Bluetooth settings...")
        }
    }

    private fun toggleFlashlight(enable: Boolean) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enable)
                postResult("Flashlight has been turned ${if (enable) "on" else "off"}.")
            } else {
                postResult("No camera flashlight detected.")
            }
        } catch (e: Exception) {
            postResult("Failed to toggle flashlight: ${e.message}")
        }
    }

    private fun adjustVolume(up: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            postResult("Volume adjusted ${if (up) "up" else "down"}.")
        } catch (e: Exception) {
            postResult("Failed to adjust volume.")
        }
    }

    private fun openApp(appName: String) {
        if (appName.isEmpty()) return
        val cleanAppName = appName.lowercase().trim()

        // 1. Try hardcoded package map
        val packageName = appPackageMap[cleanAppName]
        if (packageName != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                postResult("Opening $appName.")
                return
            }
        }

        // 2. Scan all installed packages by label
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(cleanAppName) || cleanAppName.contains(label)) {
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    postResult("Opening ${pm.getApplicationLabel(app)}.")
                    return
                }
            }
        }
        postResult("Sorry, I couldn't find the app $appName installed on your device.")
    }

    private fun closeApp() {
        val helper = AccessibilityHelperService.instance
        if (helper != null) {
            helper.closeCurrentApp()
            postResult("Closing current app.")
        } else {
            postResult("Accessibility service is disabled. Please enable it in Settings to allow closing apps.")
            openSettingsPanel(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
    }

    private fun makeCall(nameOrNumber: String) {
        val number = resolveContactNumber(nameOrNumber)
        if (number.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                postResult("Calling $nameOrNumber.")
            } catch (e: SecurityException) {
                postResult("Call permission denied. Please grant Phone permission.")
            }
        } else {
            postResult("Sorry, I couldn't find any contact named $nameOrNumber.")
        }
    }

    private fun sendSms(nameOrNumber: String, message: String) {
        val number = resolveContactNumber(nameOrNumber)
        if (number.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$number")).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                postResult("Opening SMS composer for $nameOrNumber.")
            } catch (e: Exception) {
                postResult("Failed to open SMS composer.")
            }
        } else {
            postResult("Sorry, I couldn't find any contact named $nameOrNumber.")
        }
    }

    private fun sendWhatsAppMessage(nameOrNumber: String, message: String) {
        val number = resolveContactNumber(nameOrNumber).replace("+", "").replace(" ", "")
        if (number.isNotEmpty()) {
            val url = "https://wa.me/$number?text=${Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                postResult("Opening WhatsApp conversation.")
            } catch (e: Exception) {
                postResult("WhatsApp is not installed on this device.")
            }
        } else {
            postResult("Sorry, I couldn't find any contact named $nameOrNumber.")
        }
    }

    private fun makePrimeCall(index: Int) {
        val contacts = getPrimeContacts()
        if (index < contacts.size) {
            val contact = contacts[index]
            makeCall(contact.second)
        } else {
            postResult("No prime contact set at index $index.")
        }
    }

    private fun sendPrimeMsg(index: Int) {
        val contacts = getPrimeContacts()
        if (index < contacts.size) {
            val contact = contacts[index]
            sendSms(contact.second, "Hey! Just wanted to check in.")
        } else {
            postResult("No prime contact set at index $index.")
        }
    }

    @SuppressLint("Range")
    private fun resolveContactNumber(nameOrNumber: String): String {
        // If it's already a digit/phone format, return it
        if (nameOrNumber.matches(Regex("^[+0-9\\s-]+$"))) {
            return nameOrNumber
        }

        var number = ""
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$nameOrNumber%"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                number = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
            }
        }
        return number
    }

    @SuppressLint("MissingPermission")
    fun acceptCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecomManager.acceptRingingCall()
                postResult("Accepting the call.")
            } catch (e: Exception) {
                postResult("Failed to accept call automatically.")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun rejectCall() {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                telecomManager.endCall()
                postResult("Call has been rejected.")
            }
        } catch (e: Exception) {
            postResult("Failed to reject call automatically.")
        }
    }

    private fun getPrimeContacts(): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences("MyraPrefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("prime_contacts_json", null)
        val list = mutableListOf<Pair<String, String>>()
        if (jsonStr != null) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(Pair(obj.getString("name"), obj.getString("number")))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Migrating legacy prime contacts
            val legacyName = prefs.getString("prime_name", null)
            val legacyNumber = prefs.getString("prime_number", null)
            if (legacyName != null && legacyNumber != null) {
                list.add(Pair(legacyName, legacyNumber))
            }
        }
        return list
    }

    private fun openSettingsPanel(action: String) {
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun postResult(msg: String) {
        _commandResult.postValue(msg)
    }

    fun clearCommandResult() {
        _commandResult.value = null
    }
}
