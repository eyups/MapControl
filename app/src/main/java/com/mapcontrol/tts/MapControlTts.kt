package com.mapcontrol.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.function.Consumer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * MapControl içi Sherpa-ONNX TTS. İlk konuşmada modeli assets'ten filesDir'e
 * çıkarır; sonraki konuşmalarda motoru yeniden kullanır.
 */
object MapControlTts {
    private const val TAG = "MapControlTts"
    private const val ASSET_DIR = "tts/vits-piper-tr_TR-dfki-medium-int8"
    private const val MODEL_FILE = "tr_TR-dfki-medium.onnx"
    private const val TARGET_PEAK = 0.85f

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mapcontrol-tts").apply { isDaemon = true }
    }
    private val generation = AtomicInteger(0)

    @Volatile private var engine: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var trackRate = 0

    @JvmStatic
    fun speak(context: Context, text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        val app = context.applicationContext
        val gen = generation.incrementAndGet()
        try {
            track?.pause()
            track?.flush()
        } catch (_: Throwable) {
        }
        executor.execute {
            var focus = false
            try {
                val tts = ensureEngine(app)
                if (gen != generation.get()) return@execute
                val audio = tts.generate(value, speed = speed(app))
                if (gen != generation.get()) return@execute
                normalize(audio.samples, TARGET_PEAK)
                focus = TtsAudioFocus.acquire(app) { stop() }
                if (gen != generation.get()) return@execute
                Thread.sleep(TtsAudioFocus.DUCK_SETTLE_MS)
                if (gen != generation.get()) return@execute
                ensureTrack(audio.sampleRate)
                play(audio.samples, gen)
            } catch (t: Throwable) {
                Log.e(TAG, "TTS hatasi", t)
            } finally {
                if (focus) {
                    TtsAudioFocus.release()
                }
            }
        }
    }

    @JvmStatic
    fun synthesizeToWav(context: Context, text: String, out: File,
                        onSuccess: Consumer<File>, onError: Consumer<String>) {
        val value = text.trim()
        if (value.isEmpty()) {
            onError.accept("empty")
            return
        }
        val app = context.applicationContext
        executor.execute {
            try {
                val tts = ensureEngine(app)
                val audio = tts.generate(value, speed = speed(app))
                normalize(audio.samples, TARGET_PEAK)
                writeWav(audio, out)
                onSuccess.accept(out)
            } catch (t: Throwable) {
                out.delete()
                onError.accept(t.javaClass.simpleName)
            }
        }
    }

    private fun writeWav(audio: GeneratedAudio, file: File) {
        file.parentFile?.mkdirs()
        val count = audio.samples.size
        val dataSize = count * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(audio.sampleRate)
        header.putInt(audio.sampleRate * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataSize)
        FileOutputStream(file).use { out ->
            out.write(header.array())
            val pcm = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) {
                val v = (audio.samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                pcm.putShort(v)
            }
            out.write(pcm.array())
        }
    }

    @JvmStatic
    fun stop() {
        generation.incrementAndGet()
        TtsAudioFocus.release()
        try {
            track?.pause()
            track?.flush()
        } catch (_: Throwable) {
        }
    }

    private fun speed(context: Context): Float {
        val percent = context.getSharedPreferences(MapControlSpeech.PREFS, Context.MODE_PRIVATE)
            .getInt(MapControlSpeech.KEY_SHERPA_SPEED, 100)
        return (percent / 100f).coerceIn(0.7f, 1.3f)
    }

    @Synchronized
    private fun ensureEngine(context: Context): OfflineTts {
        engine?.let { return it }
        val modelDir = prepareModelDir(context)
        val dataDir = File(modelDir, "espeak-ng-data")
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(modelDir, MODEL_FILE).absolutePath,
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    dataDir = dataDir.absolutePath,
                    noiseScale = 0.9f,
                    noiseScaleW = 1.0f,
                    lengthScale = 1.0f
                ),
                numThreads = 2,
                debug = false
            )
        )
        val started = System.currentTimeMillis()
        val created = OfflineTts(config = config)
        engine = created
        Log.i(TAG, "TTS hazır: ${created.sampleRate()} Hz, ${System.currentTimeMillis() - started} ms")
        return created
    }

    private fun prepareModelDir(context: Context): File {
        val dest = File(context.filesDir, "mapcontrol_tts")
        val marker = File(dest, ".extracted")
        if (marker.exists() && File(dest, MODEL_FILE).exists()) {
            return dest
        }
        if (dest.exists()) dest.deleteRecursively()
        val started = System.currentTimeMillis()
        extractAssetDir(context, ASSET_DIR, dest)
        marker.writeText("1")
        Log.i(TAG, "TTS modeli çıkarıldı: ${System.currentTimeMillis() - started} ms")
        return dest
    }

    private fun extractAssetDir(context: Context, assetPath: String, dest: File) {
        val children = context.assets.list(assetPath)
        if (children.isNullOrEmpty()) {
            dest.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            return
        }
        dest.mkdirs()
        for (child in children) {
            extractAssetDir(context, "$assetPath/$child", File(dest, child))
        }
    }

    private fun ensureTrack(sampleRate: Int) {
        if (track != null && trackRate == sampleRate) {
            try {
                track?.setVolume(1.0f)
            } catch (_: Throwable) {
            }
            return
        }
        try {
            track?.pause()
            track?.flush()
            track?.release()
        } catch (_: Throwable) {
        }
        track = null
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferSize = max(minBuffer, sampleRate / 5 * 4)
        val attrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()
        val created = AudioTrack(
            attrs,
            format,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        check(created.state == AudioTrack.STATE_INITIALIZED) { "AudioTrack başlatılamadı" }
        created.setVolume(1.0f)
        created.play()
        track = created
        trackRate = sampleRate
        Log.i(TAG, "AudioTrack: ${sampleRate}Hz mono, usage=navigation")
    }

    private fun play(samples: FloatArray, gen: Int) {
        val t = track ?: return
        t.pause()
        t.flush()
        t.play()
        val chunk = 2048
        var offset = 0
        while (offset < samples.size && gen == generation.get()) {
            val count = min(chunk, samples.size - offset)
            val written = t.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) break
            offset += written
        }
        Log.i(TAG, "Oynatma: $offset/${samples.size}")
    }

    private fun normalize(samples: FloatArray, target: Float) {
        var peak = 0f
        for (s in samples) {
            val a = abs(s)
            if (a > peak) peak = a
        }
        if (peak <= 0f) return
        val gain = min(3f, target / peak)
        if (abs(gain - 1f) > 0.02f) {
            for (i in samples.indices) samples[i] *= gain
        }
    }
}
