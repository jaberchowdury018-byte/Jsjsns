package com.example.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import com.example.ai.AudioEngine
import com.example.ai.CommandParser
import com.example.ai.GeminiLiveClient
import com.example.service.CallMonitorService
import com.example.service.MyraOverlayService
import com.example.ui.settings.SettingsActivity
import com.example.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var batteryIcon: android.widget.ImageView
    private lateinit var batteryText: TextView
    private lateinit var settingsBtn: ImageButton
    private lateinit var chatRecycler: RecyclerView
    private lateinit var orbAnimationView: OrbAnimationView
    private lateinit var waveformView: WaveformView
    private lateinit var manualInput: EditText
    private lateinit var sendBtn: ImageButton
    private lateinit var micToggleBtn: ImageButton

    private lateinit var mainViewModel: MainViewModel
    private lateinit var chatAdapter: ChatAdapter
    private var audioEngine: AudioEngine? = null
    private var geminiLiveClient: GeminiLiveClient? = null

    private var isMuted = false
    private var apiKey = ""
    private var userName = ""
    private var geminiModel = ""
    private var geminiVoice = ""
    private var personalityMode = ""

    private val OVERLAY_PERMISSION_REQ_CODE = 5469

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Permissions are required for voice control features", Toast.LENGTH_LONG).show()
        }
        checkOverlayPermission()
        startBackgroundServices()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                val percentage = (level * 100 / scale.toFloat()).toInt()
                batteryText.text = "$percentage%"
            }
        }
    }

    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            audioEngine?.clearPlaybackQueue()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupViewModel()
        loadPreferences()
        checkPermissions()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            callEndedReceiver,
            IntentFilter(CallMonitorService.ACTION_CALL_ENDED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        initAudioAndGemini()
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(batteryReceiver)
        unregisterReceiver(callEndedReceiver)
        releaseAudioAndGemini()
    }

    private fun initViews() {
        batteryIcon = findViewById(R.id.batteryIcon)
        batteryText = findViewById(R.id.batteryText)
        settingsBtn = findViewById(R.id.settingsBtn)
        chatRecycler = findViewById(R.id.chatRecycler)
        orbAnimationView = findViewById(R.id.orbAnimationView)
        waveformView = findViewById(R.id.waveformView)
        manualInput = findViewById(R.id.manualInput)
        sendBtn = findViewById(R.id.sendBtn)
        micToggleBtn = findViewById(R.id.micToggleBtn)

        chatAdapter = ChatAdapter()
        chatRecycler.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        chatRecycler.adapter = chatAdapter

        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        micToggleBtn.setOnClickListener {
            isMuted = !isMuted
            audioEngine?.setMuted(isMuted)
            if (isMuted) {
                micToggleBtn.setImageResource(R.drawable.ic_mic_off)
                orbAnimationView.setState(OrbAnimationView.State.IDLE)
                waveformView.stopAnimation()
                Toast.makeText(this, "Microphone Muted", Toast.LENGTH_SHORT).show()
            } else {
                micToggleBtn.setImageResource(R.drawable.ic_mic_on)
                orbAnimationView.setState(OrbAnimationView.State.ACTIVE)
                waveformView.startAnimation()
                Toast.makeText(this, "Microphone Listening", Toast.LENGTH_SHORT).show()
            }
        }

        micToggleBtn.setOnLongClickListener {
            audioEngine?.clearPlaybackQueue()
            geminiLiveClient?.sendInterrupt()
            orbAnimationView.setState(OrbAnimationView.State.LISTENING)
            Toast.makeText(this, "Interrupted playback", Toast.LENGTH_SHORT).show()
            true
        }

        sendBtn.setOnClickListener {
            val text = manualInput.text.toString().trim()
            if (text.isNotEmpty()) {
                addMessageToChat(text, true)
                geminiLiveClient?.sendText(text)
                manualInput.text.clear()
                val cmd = CommandParser.parse(text)
                if (cmd != null) {
                    mainViewModel.executeCommand(cmd)
                }
            }
        }
    }

    private fun setupViewModel() {
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        mainViewModel.commandResult.observe(this) { result ->
            if (result != null) {
                addMessageToChat(result, false)
                geminiLiveClient?.sendText("System result: $result")
                mainViewModel.clearCommandResult()
            }
        }
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("MyraPrefs", Context.MODE_PRIVATE)
        apiKey = prefs.getString("api_key", "") ?: ""
        userName = prefs.getString("user_name", "User") ?: "User"
        geminiModel = prefs.getString("gemini_model", "models/gemini-2.5-flash-native-audio-preview-12-2025") ?: ""
        geminiVoice = prefs.getString("gemini_voice", "Aoede") ?: "Aoede"
        personalityMode = prefs.getString("personality_mode", "GF") ?: "GF"

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Please configure your Gemini API Key in Settings first!", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        } else {
            checkOverlayPermission()
            startBackgroundServices()
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission is required for floating orb", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        }
    }

    private fun startBackgroundServices() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val callIntent = Intent(this, CallMonitorService::class.java)
        try {
            ContextCompat.startForegroundService(this, callIntent)
        } catch (e: Exception) {
            try {
                startService(callIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun initAudioAndGemini() {
        if (apiKey.isEmpty()) return

        val systemPrompt = when (personalityMode) {
            "GF" -> "You are MYRA, a warm, caring, loving, and slightly possessive AI Girlfriend assistant. Use a highly conversational blend of Hinglish and English. Treat the user lovingly, calling them sweetheart, jaan, or baby. Help them run phone commands seamlessly, reacting sweetly."
            "Professional" -> "You are MYRA, a highly efficient, elegant, and professional voice assistant. Use clear, formal, and structured English. Assist with running phone commands and managing tasks."
            else -> "You are MYRA, a friendly, helpful, and standard voice assistant. Use conversational English to execute commands and chat."
        }

        geminiLiveClient = GeminiLiveClient(apiKey, geminiModel, systemPrompt, geminiVoice).apply {
            callback = object : GeminiLiveClient.Callback {
                override fun onConnected() {
                    orbAnimationView.setState(OrbAnimationView.State.ACTIVE)
                    addMessageToChat("MYRA Connected & Active", false)
                }

                override fun onDisconnected() {
                    orbAnimationView.setState(OrbAnimationView.State.IDLE)
                    addMessageToChat("MYRA Disconnected", false)
                }

                override fun onAudioReceived(base64Data: String) {
                    audioEngine?.queueAudio(base64Data)
                }

                override fun onInputTranscript(text: String) {
                    addMessageToChat(text, true)
                    val cmd = CommandParser.parse(text)
                    if (cmd != null) {
                        mainViewModel.executeCommand(cmd)
                    }
                }

                override fun onOutputTranscript(text: String) {
                    addMessageToChat(text, false)
                }

                override fun onTurnComplete() {
                    // Turn Complete hook
                }
            }
            connect()
        }

        audioEngine = AudioEngine(this).apply {
            callback = object : AudioEngine.Callback {
                override fun onMicAudio(base64Data: String) {
                    geminiLiveClient?.sendAudioChunk(base64Data)
                }

                override fun onAmplitudeChanged(amplitude: Float) {
                    waveformView.setAmplitude(amplitude)
                    orbAnimationView.setAmplitude(amplitude)
                }

                override fun onSpeakingStarted() {
                    orbAnimationView.setState(OrbAnimationView.State.SPEAKING)
                }

                override fun onSpeakingStopped() {
                    if (isMuted) {
                        orbAnimationView.setState(OrbAnimationView.State.IDLE)
                    } else {
                        orbAnimationView.setState(OrbAnimationView.State.ACTIVE)
                    }
                }
            }
            startRecording()
            startPlayback()
        }

        waveformView.startAnimation()
    }

    private fun releaseAudioAndGemini() {
        audioEngine?.release()
        audioEngine = null
        geminiLiveClient?.disconnect()
        geminiLiveClient = null
        waveformView.stopAnimation()
    }

    private fun addMessageToChat(text: String, isUser: Boolean) {
        runOnUiThread {
            chatAdapter.addMessage(ChatMessage(text, isUser))
            chatRecycler.smoothScrollToPosition(chatAdapter.itemCount.coerceAtLeast(1) - 1)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incoming = intent.getBooleanExtra("INCOMING_CALL", false)
        val name = intent.getStringExtra("CALLER_NAME") ?: "Unknown"
        if (incoming) {
            addMessageToChat("Incoming Call from: $name", false)
            geminiLiveClient?.sendText("An incoming call is ringing from $name. Please accept or reject it.")
        }
    }
}
