package com.example.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.util.Base64
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue

class AudioEngine(private val context: Context) {

    interface Callback {
        fun onMicAudio(base64Data: String)
        fun onAmplitudeChanged(amplitude: Float)
        fun onSpeakingStarted()
        fun onSpeakingStopped()
    }

    var callback: Callback? = null

    private val sampleRateMic = 16000
    private val sampleRateSpeaker = 24000
    private val channelMic = AudioFormat.CHANNEL_IN_MONO
    private val channelSpeaker = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSizeMic = AudioRecord.getMinBufferSize(sampleRateMic, channelMic, audioFormat)
    private val bufferSizeSpeaker = AudioTrack.getMinBufferSize(sampleRateSpeaker, channelSpeaker, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var isRecording = false
    private var isPlaying = false
    private var isMuted = false

    private val playbackQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    var isSpeaking = false
        private set

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return
        isRecording = true
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRateMic,
                channelMic,
                audioFormat,
                bufferSizeMic.coerceAtLeast(2048)
            )
            audioRecord?.startRecording()
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            return
        }

        recordJob = scope.launch(Dispatchers.IO) {
            val shortBuffer = ShortArray(1024)
            val byteBuffer = ByteArray(2048)
            while (isRecording) {
                val record = audioRecord ?: break
                val readShorts = record.read(shortBuffer, 0, shortBuffer.size)
                if (readShorts > 0) {
                    val rms = calculateRMS(shortBuffer, readShorts)
                    withContext(Dispatchers.Main) {
                        callback?.onAmplitudeChanged(rms)
                    }

                    for (i in 0 until readShorts) {
                        val value = shortBuffer[i]
                        byteBuffer[i * 2] = (value.toInt() and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
                    }

                    if (!isMuted && !isSpeaking) {
                        val subArray = byteBuffer.copyOf(readShorts * 2)
                        val base64 = Base64.encodeToString(subArray, Base64.NO_WRAP)
                        callback?.onMicAudio(base64)
                    }
                }
                delay(20)
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }

    fun startPlayback() {
        if (isPlaying) return
        isPlaying = true

        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(sampleRateSpeaker)
            .setChannelMask(channelSpeaker)
            .setEncoding(audioFormat)
            .build()

        audioTrack = AudioTrack(
            attr,
            format,
            bufferSizeSpeaker.coerceAtLeast(4096),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack?.play()

        playbackJob = scope.launch(Dispatchers.IO) {
            while (isPlaying) {
                val chunk = playbackQueue.poll()
                if (chunk != null) {
                    if (!isSpeaking) {
                        isSpeaking = true
                        withContext(Dispatchers.Main) {
                            callback?.onSpeakingStarted()
                        }
                    }
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    if (isSpeaking) {
                        isSpeaking = false
                        withContext(Dispatchers.Main) {
                            callback?.onSpeakingStopped()
                        }
                    }
                    delay(50)
                }
            }
        }
    }

    fun stopPlayback() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null
        clearPlaybackQueue()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioTrack = null
        if (isSpeaking) {
            isSpeaking = false
            callback?.onSpeakingStopped()
        }
    }

    fun queueAudio(base64Data: String) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            playbackQueue.offer(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearPlaybackQueue() {
        playbackQueue.clear()
        if (isSpeaking) {
            isSpeaking = false
            callback?.onSpeakingStopped()
        }
    }

    fun setMuted(muted: Boolean) {
        this.isMuted = muted
    }

    fun release() {
        stopRecording()
        stopPlayback()
        scope.cancel()
    }

    private fun calculateRMS(buffer: ShortArray, readSize: Int): Float {
        var sum = 0.0
        for (i in 0 until readSize) {
            sum += buffer[i] * buffer[i]
        }
        val mean = sum / readSize
        return Math.sqrt(mean).toFloat() / Short.MAX_VALUE.toFloat()
    }
}
