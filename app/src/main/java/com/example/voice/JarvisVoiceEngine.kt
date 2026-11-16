package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class VoicePhase {
    IDLE,
    WAKE_LISTENING,
    COMMAND_LISTENING,
    SPEAKING,
}

class JarvisVoiceEngine(
    private val context: Context,
    private val onCommandRecognized: (String) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var wakePhrase: String = "hey jarvis"
    private var continuousWakeEnabled = false

    private val _phase = MutableStateFlow(VoicePhase.IDLE)
    val phase: StateFlow<VoicePhase> = _phase.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _voiceStatusMessage = MutableStateFlow<String?>(null)
    val voiceStatusMessage: StateFlow<String?> = _voiceStatusMessage.asStateFlow()

    val isSpeechRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = textToSpeech?.setLanguage(Locale.US)
                ttsReady = res != TextToSpeech.LANG_MISSING_DATA &&
                    res != TextToSpeech.LANG_NOT_SUPPORTED
                textToSpeech?.setSpeechRate(1.02f)
                textToSpeech?.setPitch(0.95f)
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post { _phase.value = VoicePhase.SPEAKING }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            _phase.value = VoicePhase.IDLE
                            _rmsLevel.value = 0f
                            if (continuousWakeEnabled) {
                                scheduleWakeListen(350L)
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            _phase.value = VoicePhase.IDLE
                            _rmsLevel.value = 0f
                        }
                    }
                })
            } else {
                Log.w(TAG, "TextToSpeech init status=$status")
            }
        }
    }

    fun updateWakeWord(newWakeWord: String) {
        wakePhrase = newWakeWord.trim().lowercase().ifBlank { "hey jarvis" }
    }

    fun setWakeWordMonitoring(enabled: Boolean) {
        continuousWakeEnabled = enabled
        if (enabled) {
            if (!isSpeechRecognitionAvailable) {
                continuousWakeEnabled = false
                _voiceStatusMessage.value = "Speech recognition service is unavailable on this device."
                return
            }
            if (_phase.value == VoicePhase.IDLE) {
                startListeningInternal(wakeMode = true)
            }
        } else {
            if (_phase.value == VoicePhase.WAKE_LISTENING) {
                stopListening()
            }
        }
    }

    fun startSingleCommandListen() {
        if (!isSpeechRecognitionAvailable) {
            _voiceStatusMessage.value = "Speech recognition service is unavailable on this device."
            return
        }
        stopSpeaking()
        startListeningInternal(wakeMode = false)
    }

    fun stopListening() {
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            runCatching { speechRecognizer?.cancel() }
            _rmsLevel.value = 0f
            _partialTranscript.value = ""
            if (_phase.value != VoicePhase.SPEAKING) {
                _phase.value = VoicePhase.IDLE
            }
        }
    }

    fun speak(text: String) {
        val clean = sanitizeForSpeech(text)
        if (clean.isBlank() || !ttsReady) return
        mainHandler.post {
            if (_phase.value == VoicePhase.WAKE_LISTENING || _phase.value == VoicePhase.COMMAND_LISTENING) {
                runCatching { speechRecognizer?.cancel() }
            }
            val utteranceId = UUID.randomUUID().toString()
            textToSpeech?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            runCatching { textToSpeech?.stop() }
            if (_phase.value == VoicePhase.SPEAKING) {
                _phase.value = VoicePhase.IDLE
            }
        }
    }

    fun clearVoiceStatus() {
        _voiceStatusMessage.value = null
    }

    private fun scheduleWakeListen(delayMs: Long) {
        mainHandler.postDelayed({
            if (continuousWakeEnabled && _phase.value == VoicePhase.IDLE) {
                startListeningInternal(wakeMode = true)
            }
        }, delayMs)
    }

    private fun startListeningInternal(wakeMode: Boolean) {
        mainHandler.post {
            _voiceStatusMessage.value = null
            _partialTranscript.value = ""
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            }
            val recognizer = speechRecognizer ?: return@post
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _phase.value = if (wakeMode) VoicePhase.WAKE_LISTENING else VoicePhase.COMMAND_LISTENING
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                    _rmsLevel.value = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _rmsLevel.value = 0f
                }

                override fun onError(error: Int) {
                    _rmsLevel.value = 0f
                    _partialTranscript.value = ""
                    if (wakeMode && continuousWakeEnabled) {
                        _phase.value = VoicePhase.IDLE
                        val retryDelay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 900L else 500L
                        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            continuousWakeEnabled = false
                            _voiceStatusMessage.value = "Microphone permission required for wake word."
                        } else {
                            scheduleWakeListen(retryDelay)
                        }
                    } else {
                        _phase.value = VoicePhase.IDLE
                        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_CLIENT) {
                            _voiceStatusMessage.value = mapSpeechError(error)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    _rmsLevel.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull().orEmpty().trim()
                    _partialTranscript.value = ""
                    _phase.value = VoicePhase.IDLE

                    if (wakeMode) {
                        handleWakeTranscript(best)
                    } else if (best.isNotBlank()) {
                        onCommandRecognized(best)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _partialTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            runCatching {
                recognizer.startListening(intent)
            }.onFailure { err ->
                Log.e(TAG, "Failed to start speech recognizer", err)
                _phase.value = VoicePhase.IDLE
                _voiceStatusMessage.value = err.localizedMessage ?: "Unable to start microphone listener."
            }
        }
    }

    private fun handleWakeTranscript(transcript: String) {
        if (transcript.isBlank()) {
            if (continuousWakeEnabled) scheduleWakeListen(400L)
            return
        }
        val lower = transcript.lowercase()
        val idx = lower.indexOf(wakePhrase)
        if (idx >= 0) {
            val afterWake = transcript.substring(idx + wakePhrase.length)
                .trim(' ', ',', '.', '?', '!')
            if (afterWake.isNotBlank()) {
                onCommandRecognized(afterWake)
            } else {
                startListeningInternal(wakeMode = false)
            }
        } else if (continuousWakeEnabled) {
            scheduleWakeListen(350L)
        }
    }

    private fun sanitizeForSpeech(raw: String): String {
        return raw
            .replace(Regex("""```[\s\S]*?```"""), " Code block omitted. ")
            .replace(Regex("""[*_`#>\[\]]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(1200)
    }

    private fun mapSpeechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy; please try again."
        else -> "Voice input did not recognize speech (code $code)."
    }

    fun release() {
        continuousWakeEnabled = false
        mainHandler.post {
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            runCatching {
                textToSpeech?.stop()
                textToSpeech?.shutdown()
            }
            textToSpeech = null
        }
    }

    private companion object {
        const val TAG = "JarvisVoiceEngine"
    }
}
