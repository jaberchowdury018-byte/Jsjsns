package com.example.ai

import android.os.Handler
import android.os.Looper
import android.util.Base64
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    private var apiKey: String,
    private var model: String,
    private var systemPrompt: String,
    private var voiceName: String
) {

    interface Callback {
        fun onConnected()
        fun onDisconnected()
        fun onAudioReceived(base64Data: String)
        fun onInputTranscript(text: String)
        fun onOutputTranscript(text: String)
        fun onTurnComplete()
    }

    var callback: Callback? = null

    private var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private val handler = Handler(Looper.getMainLooper())

    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            sendKeepAlive()
            handler.postDelayed(this, 8000)
        }
    }

    private val sessionRenewalRunnable = object : Runnable {
        override fun run() {
            renewSession()
        }
    }

    private val reconnectRunnable = Runnable {
        connect()
    }

    fun updateConfig(apiKey: String, model: String, systemPrompt: String, voiceName: String) {
        this.apiKey = apiKey
        this.model = model
        this.systemPrompt = systemPrompt
        this.voiceName = voiceName
    }

    fun connect() {
        if (isConnected) return
        handler.removeCallbacks(reconnectRunnable)

        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                sendSetupMessage()
                handler.post {
                    callback?.onConnected()
                }
                handler.postDelayed(keepAliveRunnable, 8000)
                handler.postDelayed(sessionRenewalRunnable, 540000)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseServerMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
                handleDisconnect()
            }
        })
    }

    fun disconnect() {
        handler.removeCallbacks(keepAliveRunnable)
        handler.removeCallbacks(sessionRenewalRunnable)
        handler.removeCallbacks(reconnectRunnable)
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
    }

    private fun handleDisconnect() {
        isConnected = false
        handler.removeCallbacks(keepAliveRunnable)
        handler.removeCallbacks(sessionRenewalRunnable)
        handler.post {
            callback?.onDisconnected()
        }
        handler.postDelayed(reconnectRunnable, 3000)
    }

    private fun renewSession() {
        disconnect()
        connect()
    }

    private fun sendSetupMessage() {
        val setupJson = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", model)
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                })
                put("generation_config", JSONObject().apply {
                    put("response_modalities", JSONArray().apply {
                        put("AUDIO")
                    })
                    put("speech_config", JSONObject().apply {
                        put("voice_config", JSONObject().apply {
                            put("prebuilt_voice_config", JSONObject().apply {
                                put("voice_name", voiceName)
                            })
                        })
                    })
                    put("temperature", 0.9)
                })
                put("output_audio_transcription", JSONObject())
                put("input_audio_transcription", JSONObject())
            })
        }
        webSocket?.send(setupJson.toString())
    }

    fun sendAudioChunk(base64Data: String) {
        if (!isConnected) return
        val audioJson = JSONObject().apply {
            put("realtime_input", JSONObject().apply {
                put("media_chunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mime_type", "audio/pcm;rate=16000")
                        put("data", base64Data)
                    })
                })
            })
        }
        webSocket?.send(audioJson.toString())
    }

    fun sendText(message: String) {
        if (!isConnected) return
        val textJson = JSONObject().apply {
            put("client_content", JSONObject().apply {
                put("turns", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", message)
                            })
                        })
                    })
                })
                put("turn_complete", true)
            })
        }
        webSocket?.send(textJson.toString())
    }

    fun sendInterrupt() {
        if (!isConnected) return
        val interruptJson = JSONObject().apply {
            put("client_content", JSONObject().apply {
                put("turns", JSONArray())
                put("turn_complete", true)
            })
        }
        webSocket?.send(interruptJson.toString())
    }

    private fun sendKeepAlive() {
        val silentPcm = ByteArray(1024)
        val base64 = Base64.encodeToString(silentPcm, Base64.NO_WRAP)
        sendAudioChunk(base64)
    }

    private fun parseServerMessage(text: String) {
        try {
            val root = JSONObject(text)
            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                val modelTurn = serverContent.optJSONObject("modelTurn")
                if (modelTurn != null) {
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i)
                            val inlineData = part?.optJSONObject("inlineData")
                            val data = inlineData?.optString("data") ?: ""
                            if (data.isNotEmpty()) {
                                handler.post {
                                    callback?.onAudioReceived(data)
                                }
                            }
                        }
                    }
                }

                val outputTranscription = serverContent.optJSONObject("outputTranscription")
                if (outputTranscription != null) {
                    val transcriptText = outputTranscription.optString("text") ?: ""
                    if (transcriptText.isNotEmpty()) {
                        handler.post {
                            callback?.onOutputTranscript(transcriptText)
                        }
                    }
                }

                val inputTranscription = serverContent.optJSONObject("inputTranscription")
                if (inputTranscription != null) {
                    val transcriptText = inputTranscription.optString("text") ?: ""
                    if (transcriptText.isNotEmpty()) {
                        handler.post {
                            callback?.onInputTranscript(transcriptText)
                        }
                    }
                }

                val turnComplete = serverContent.optBoolean("turnComplete", false)
                if (turnComplete) {
                    handler.post {
                        callback?.onTurnComplete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
