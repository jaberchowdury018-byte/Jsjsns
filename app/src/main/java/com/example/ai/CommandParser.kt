package com.example.ai

import com.example.model.AppCommand
import java.util.Locale

object CommandParser {
    fun parse(text: String): AppCommand? {
        val cleanText = text.lowercase(Locale.getDefault()).trim()
        
        // Wifi
        if (cleanText.contains("wifi on") || cleanText.contains("wifi chalu")) {
            return AppCommand("WIFI_ON")
        }
        if (cleanText.contains("wifi off") || cleanText.contains("wifi band")) {
            return AppCommand("WIFI_OFF")
        }
        
        // Bluetooth
        if (cleanText.contains("bluetooth on") || cleanText.contains("bluetooth chalu")) {
            return AppCommand("BLUETOOTH_ON")
        }
        if (cleanText.contains("bluetooth off") || cleanText.contains("bluetooth band")) {
            return AppCommand("BLUETOOTH_OFF")
        }
        
        // Flashlight / Torch
        if (cleanText.contains("flashlight on") || cleanText.contains("torch on") || cleanText.contains("torch chalu") || cleanText.contains("flashlight chalu")) {
            return AppCommand("FLASHLIGHT_ON")
        }
        if (cleanText.contains("flashlight off") || cleanText.contains("torch off") || cleanText.contains("torch band") || cleanText.contains("flashlight band")) {
            return AppCommand("FLASHLIGHT_OFF")
        }
        
        // Volume
        if (cleanText.contains("volume badhao") || cleanText.contains("volume up") || cleanText.contains("sound up")) {
            return AppCommand("VOLUME_UP")
        }
        if (cleanText.contains("volume kam") || cleanText.contains("volume down") || cleanText.contains("sound down")) {
            return AppCommand("VOLUME_DOWN")
        }
        
        // Prime Calls/Messages
        if (cleanText.contains("close friend ko call") || cleanText.contains("call my close friend") || cleanText.contains("close friend call")) {
            return AppCommand("PRIME_CALL", mapOf("index" to "0"))
        }
        if (cleanText.contains("second contact ko call") || cleanText.contains("call my second contact") || cleanText.contains("second contact call")) {
            return AppCommand("PRIME_CALL", mapOf("index" to "1"))
        }
        if (cleanText.contains("message my love") || cleanText.contains("meri jaan ko message") || cleanText.contains("meri jaan ko msg")) {
            return AppCommand("PRIME_MSG", mapOf("index" to "0"))
        }
        
        // WhatsApp Message
        if (cleanText.contains("whatsapp karo") || cleanText.contains("whatsapp msg") || cleanText.contains("whatsapp message")) {
            val regex = Regex("(?:whatsapp karo|whatsapp msg|whatsapp message to|whatsapp message)\\s+([a-zA-Z0-9\\s]+?)(?:ko|to)?(?:\\s+msg\\s+|\\s+message\\s+|\\s+text\\s+|\\s+karo\\s+|\\s+bhejo\\s+|\\s+saying\\s+|\\s+)?([a-zA-Z0-9\\s]+)?$")
            val match = regex.find(cleanText)
            if (match != null) {
                val name = match.groupValues[1].trim()
                val msg = match.groupValues.getOrNull(2)?.trim() ?: "hello"
                return AppCommand("WHATSAPP_MSG", mapOf("name" to name, "message" to msg))
            }
        }
        
        // SMS
        if (cleanText.contains("sms") || cleanText.contains("message karo") || cleanText.contains("msg karo")) {
            val regex = Regex("(?:sms to|message to|msg to|sms|message|msg)\\s+([a-zA-Z0-9\\s]+?)(?:ko|to)?(?:\\s+bhejo\\s+|\\s+send\\s+|\\s+text\\s+|\\s+saying\\s+|\\s+)?([a-zA-Z0-9\\s]+)?$")
            val match = regex.find(cleanText)
            if (match != null) {
                val name = match.groupValues[1].trim()
                val msg = match.groupValues.getOrNull(2)?.trim() ?: "hello"
                return AppCommand("SMS", mapOf("name" to name, "message" to msg))
            }
        }
        
        // Call [name]
        if (cleanText.contains("call") || cleanText.contains("karo call") || cleanText.contains("ko call")) {
            val regex = Regex("(?:call to|call|dial)\\s+([a-zA-Z0-9\\s]+)|([a-zA-Z0-9\\s]+)\\s+(?:ko call|ko dial)")
            val match = regex.find(cleanText)
            if (match != null) {
                var name = (match.groupValues[1].ifEmpty { match.groupValues[2] }).trim()
                if (name.isNotEmpty() && name != "my close friend" && name != "my second contact") {
                    return AppCommand("CALL", mapOf("name" to name))
                }
            }
        }
        
        // Open App
        if (cleanText.contains("open") || cleanText.contains("kholo") || cleanText.contains("chalu karo")) {
            val regex = Regex("(?:open|launch)\\s+([a-zA-Z0-9\\s]+)|([a-zA-Z0-9\\s]+)\\s+(?:kholo|chalu karo)")
            val match = regex.find(cleanText)
            if (match != null) {
                val app = (match.groupValues[1].ifEmpty { match.groupValues[2] }).trim()
                if (app.isNotEmpty()) {
                    return AppCommand("OPEN_APP", mapOf("app_name" to app))
                }
            }
        }
        
        // Close App
        if (cleanText.contains("close") || cleanText.contains("band karo") || cleanText.contains("exit")) {
            val regex = Regex("(?:close|exit)\\s+([a-zA-Z0-9\\s]+)|([a-zA-Z0-9\\s]+)\\s+(?:band karo|band)")
            val match = regex.find(cleanText)
            if (match != null) {
                val app = (match.groupValues[1].ifEmpty { match.groupValues[2] }).trim()
                if (app.isNotEmpty()) {
                    return AppCommand("CLOSE_APP", mapOf("app_name" to app))
                }
            }
        }
        
        return null
    }
}
