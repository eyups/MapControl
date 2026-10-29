package com.mapcontrol.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mapcontrol.ui.welcome.WelcomeAudioFiles
import java.io.File
import java.util.function.Consumer

/** Hoş geldin sesi: seçili motorla dosya üretir ve welcome-audio içinde tutar. */
object WelcomeTts {
    private val main = Handler(Looper.getMainLooper())

    @JvmStatic
    fun generate(context: Context, text: String,
                 onSuccess: Consumer<File>, onError: Consumer<String>) {
        val cloudText = TurkishSpeechText.normalize(text.trim())
        val localText = TurkishSpeechText.normalize(SpeechText.plain(text))
        if (cloudText.isEmpty()) {
            onError.accept("empty")
            return
        }
        val app = context.applicationContext
        val selected = MapControlSpeech.isElevenLabsSelected(app)
        val iflytek = MapControlSpeech.isIflytekSelected(app)
        val hasKey = MapControlSpeech.hasApiKey(app)
        val online = MapControlSpeech.isOnline(app)
        Log.i("WelcomeTts", "motor=" + MapControlSpeech.engine(app)
                + " selected=" + selected + " iflytek=" + iflytek
                + " key=" + hasKey + " online=" + online)
        val eleven = selected && hasKey && online
        if (eleven) {
            try {
                val out = WelcomeAudioFiles.newTtsFile(app, ".mp3")
                ElevenLabsTts.synthesizeToFile(app, cloudText,
                        MapControlSpeech.apiKey(app), MapControlSpeech.voiceId(app), out,
                        object : ElevenLabsTts.FileCallback {
                            override fun onSuccess(file: File) {
                                keep(app, file)
                                postSuccess(onSuccess, file)
                            }

                            override fun onFailure(reason: String) {
                                out.delete()
                                sherpa(app, localText, onSuccess, onError, reason)
                            }
                        })
            } catch (t: Throwable) {
                sherpa(app, localText, onSuccess, onError, t.javaClass.simpleName)
            }
        } else {
            // iFlytek can speak live, but its service has no synthesize-to-file API.
            // Welcome audio is stored as a file, so use the embedded Sherpa engine.
            sherpa(app, localText, onSuccess, onError,
                    if (iflytek) "iflytek-no-file-api" else "offline")
        }
    }

    private fun sherpa(app: Context, text: String,
                       onSuccess: Consumer<File>, onError: Consumer<String>, cloudReason: String) {
        try {
            val out = WelcomeAudioFiles.newTtsFile(app, ".wav")
            MapControlTts.synthesizeToWav(app, text, out,
                    Consumer { file ->
                        keep(app, file)
                        postSuccess(onSuccess, file)
                    },
                    Consumer { reason ->
                        postError(onError, "Sherpa: $reason")
                    })
        } catch (t: Throwable) {
            postError(onError, "Sherpa: ${t.javaClass.simpleName} ($cloudReason)")
        }
    }

    private fun keep(app: Context, file: File) {
        try {
            WelcomeAudioFiles.keepOnly(app, file)
        } catch (_: Throwable) {
        }
    }

    private fun postSuccess(callback: Consumer<File>, file: File) {
        main.post { callback.accept(file) }
    }

    private fun postError(callback: Consumer<String>, reason: String) {
        main.post { callback.accept(reason) }
    }
}
