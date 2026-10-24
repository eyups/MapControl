package com.mapcontrol.ttsprobe

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val TAG = "TtsProbe"

data class Voice(
    val id: String,
    val label: String,
    val assetDir: String,
    val modelFile: String,
)

private val VOICES = listOf(
    Voice("dfki", "DFKI", "vits-piper-tr_TR-dfki-medium-int8", "tr_TR-dfki-medium.onnx"),
    Voice("fahrettin", "Fahrettin", "vits-piper-tr_TR-fahrettin-medium-int8", "tr_TR-fahrettin-medium.onnx"),
    Voice("fettah", "Fettah", "vits-piper-tr_TR-fettah-medium-int8", "tr_TR-fettah-medium.onnx"),
)

data class AudioOutput(
    val id: String,
    val label: String,
    val usage: Int,
)

private val OUTPUTS = listOf(
    AudioOutput("media", "Media", AudioAttributes.USAGE_MEDIA),
    AudioOutput("nav", "Navigation", AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE),
    AudioOutput("assistant", "Assistant", AudioAttributes.USAGE_ASSISTANT),
    AudioOutput("notification", "Notification", AudioAttributes.USAGE_NOTIFICATION),
    AudioOutput("alarm", "Alarm", AudioAttributes.USAGE_ALARM),
)

data class Sample(val id: String, val label: String, val text: String)

private val SAMPLES = listOf(
    Sample("", "Rapor örneği seç...", ""),
    Sample(
        "efsane", "Efsane Şoför",
        "12.4 km yol yaptın, yüz kilometrede ortalama 7.2 litre yaktın. " +
            "Frenlere gereksiz yere dokunmadın; balatalar tatilde. " +
            "Kalkışların pamuk gibiydi; turbo mutlu. Ortalama 54 km/h ile aktın. " +
            "Puanın 96, notun A. Unvanın: Efsane Şoför."
    ),
    Sample(
        "sakin", "Sakin Kaptan",
        "5.5 km yol yaptın, yüz kilometrede ortalama 10.4 litre yaktın. " +
            "1 kez sert fren yaptın; balatalar senden şikayetçi. " +
            "1 kez sert kalkış yaptın; turbo sana küstü. Ortalama 19 km/h ile aktın. " +
            "Puanın 84, notun B. Unvanın: Sakin Kaptan."
    ),
    Sample(
        "trafik", "Trafik Canavarı",
        "3.2 km yol yaptın, yüz kilometrede ortalama 11.8 litre yaktın. " +
            "2 kez sert fren yaptın; balatalar senden şikayetçi. " +
            "2 kez sert kalkış yaptın; turbo sana küstü. Ortalama 14 km/h ile aktın. " +
            "Puanın 72, notun C. Unvanın: Trafik Canavarı."
    ),
    Sample(
        "sokak", "Sokak Yarışçısı",
        "8.0 km yol yaptın, yüz kilometrede ortalama 9.5 litre yaktın. " +
            "3 kez sert fren yaptın; balatalar senden şikayetçi. " +
            "4 kez sert kalkış yaptın; turbo sana küstü. " +
            "En yüksek hızın 128 km/h; radar sana selam söylüyor. " +
            "Ortalama 48 km/h ile aktın. Puanın 64, notun D. Unvanın: Sokak Yarışçısı."
    ),
    Sample(
        "ehliyet", "Ehliyet Askıda",
        "4.1 km yol yaptın, yüz kilometrede ortalama 13.5 litre yaktın. " +
            "5 kez sert fren yaptın; balatalar senden şikayetçi. " +
            "6 kez sert kalkış yaptın; turbo sana küstü. " +
            "En yüksek hızın 152 km/h; radar sana selam söylüyor. " +
            "Ortalama 37 km/h ile aktın. Puanın 45, notun F. Unvanın: Ehliyet Askıda."
    ),
)

private const val DEFAULT_TEXT =
    "Merhaba, MapControl Türkçe ses testi. Hız doksan kilometre saat, menzil dört yüz yirmi kilometre."

class MainActivity : Activity() {

    private lateinit var input: EditText
    private lateinit var speedInput: EditText
    private lateinit var noiseInput: EditText
    private lateinit var noiseWInput: EditText
    private lateinit var sampleSpinner: Spinner
    private lateinit var voiceSpinner: Spinner
    private lateinit var outputSpinner: Spinner
    private lateinit var volumeInput: EditText
    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var speakButton: Button
    private lateinit var stopButton: Button
    private lateinit var systemButton: Button

    private var engine: OfflineTts? = null
    private var track: AudioTrack? = null
    private var systemTts: TextToSpeech? = null

    @Volatile private var engineNoise = 0.667f
    @Volatile private var engineNoiseW = 0.8f
    @Volatile private var engineVoiceId = ""
    @Volatile private var selectedVoiceIndex = 0
    @Volatile private var selectedOutputIndex = 0
    @Volatile private var trackUsage = -1
    @Volatile private var trackSampleRate = 0

    @Volatile private var stopped = false
    @Volatile private var busy = false
    @Volatile private var initStarted = false
    @Volatile private var autoSpeak = false

    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        handleIntent(intent)
        initSherpa()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        handleIntent(intent)
        if (engine != null && autoSpeak) {
            autoSpeak = false
            speakSherpa()
        }
    }

    override fun onDestroy() {
        stopped = true
        try { track?.pause(); track?.flush(); track?.release() } catch (_: Throwable) {}
        track = null
        try { engine?.free() } catch (_: Throwable) {}
        engine = null
        try { systemTts?.shutdown() } catch (_: Throwable) {}
        systemTts = null
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent) {
        val text = intent.getStringExtra("text")
        if (!text.isNullOrBlank()) {
            input.setText(text)
        }
        val sampleId = intent.getStringExtra("sample")
        if (!sampleId.isNullOrBlank()) {
            val idx = SAMPLES.indexOfFirst { it.id == sampleId }
            if (idx >= 0) {
                input.setText(SAMPLES[idx].text)
                if (::sampleSpinner.isInitialized) sampleSpinner.setSelection(idx)
            } else {
                log("Bilinmeyen sample: $sampleId")
            }
        }
        val speed = intent.getFloatExtra("speed", 1.0f)
        if (speed > 0f) {
            speedInput.setText(speed.toString())
        }
        val noise = intent.getFloatExtra("noise", -1f)
        if (noise > 0f) {
            noiseInput.setText(noise.toString())
        }
        val noiseW = intent.getFloatExtra("noiseW", -1f)
        if (noiseW > 0f) {
            noiseWInput.setText(noiseW.toString())
        }
        val voiceId = intent.getStringExtra("voice")
        if (!voiceId.isNullOrBlank()) {
            val idx = VOICES.indexOfFirst { it.id == voiceId }
            if (idx >= 0) {
                selectedVoiceIndex = idx
                if (::voiceSpinner.isInitialized) voiceSpinner.setSelection(idx)
            } else {
                log("Bilinmeyen voice: $voiceId")
            }
        }
        val usageId = intent.getStringExtra("usage")
        if (!usageId.isNullOrBlank()) {
            val idx = OUTPUTS.indexOfFirst { it.id == usageId }
            if (idx >= 0) {
                selectedOutputIndex = idx
                if (::outputSpinner.isInitialized) outputSpinner.setSelection(idx)
            } else {
                log("Bilinmeyen usage: $usageId")
            }
        }
        val volume = intent.getIntExtra("volume", -1)
        if (volume in 0..100) {
            volumeInput.setText(volume.toString())
        }
        autoSpeak = intent.getBooleanExtra("autoplay", false)
        log(
            "Intent: text=${text?.take(80)} autoplay=$autoSpeak " +
                "voice=${VOICES[selectedVoiceIndex].id} usage=${OUTPUTS[selectedOutputIndex].id} " +
                "volume=${volumeInput.text} extras=${intent.extras?.keySet()}"
        )
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#101418"))
            setPadding(dp(28), dp(20), dp(28), dp(20))
        }

        root.addView(TextView(this).apply {
            text = "TTS Probe"
            setTextColor(Color.WHITE)
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
        })

        statusView = TextView(this).apply {
            text = "Sherpa-ONNX motoru yükleniyor..."
            setTextColor(Color.parseColor("#FFB000"))
            textSize = 16f
            setPadding(0, dp(6), 0, dp(10))
        }
        root.addView(statusView)

        val voiceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        voiceRow.addView(TextView(this).apply {
            text = "Voice"
            setTextColor(Color.parseColor("#C7D0D9"))
            textSize = 16f
        })
        voiceSpinner = Spinner(this).apply {
            adapter = object : ArrayAdapter<String>(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                VOICES.map { it.label }
            ) {
                override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                    val v = super.getView(position, convertView, parent)
                    (v as TextView).setTextColor(Color.WHITE)
                    return v
                }

                override fun getDropDownView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                    val v = super.getDropDownView(position, convertView, parent)
                    (v as TextView).setTextColor(Color.WHITE)
                    v.setBackgroundColor(Color.parseColor("#1B2229"))
                    return v
                }
            }
            setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#1B2229")))
            setSelection(selectedVoiceIndex)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    selectedVoiceIndex = position
                    log("Voice seçildi: ${VOICES[position].label} (${VOICES[position].id})")
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        voiceRow.addView(voiceSpinner, LinearLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(10)
        })
        root.addView(voiceRow)

        val sampleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        sampleRow.addView(TextView(this).apply {
            text = "Örnek"
            setTextColor(Color.parseColor("#C7D0D9"))
            textSize = 16f
        })
        sampleSpinner = Spinner(this).apply {
            adapter = object : ArrayAdapter<String>(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                SAMPLES.map { it.label }
            ) {
                override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                    val v = super.getView(position, convertView, parent)
                    (v as TextView).setTextColor(Color.WHITE)
                    return v
                }

                override fun getDropDownView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                    val v = super.getDropDownView(position, convertView, parent)
                    (v as TextView).setTextColor(Color.WHITE)
                    v.setBackgroundColor(Color.parseColor("#1B2229"))
                    return v
                }
            }
            setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#1B2229")))
            setSelection(0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    if (position > 0) {
                        input.setText(SAMPLES[position].text)
                        log("Örnek seçildi: ${SAMPLES[position].label}")
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        sampleRow.addView(sampleSpinner, LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(10)
        })
        root.addView(sampleRow)

        val audioRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        audioRow.addView(TextView(this).apply {
            text = "Çıkış"
            setTextColor(Color.parseColor("#C7D0D9"))
            textSize = 16f
        })
        outputSpinner = Spinner(this).apply {
            adapter = object : ArrayAdapter<String>(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                OUTPUTS.map { it.label }
            ) {
                override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                    val v = super.getView(position, convertView, parent)
                    (v as TextView).setTextColor(Color.WHITE)
                    return v
                }

                override fun getDropDownView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                    val v = super.getDropDownView(position, convertView, parent)
                    (v as TextView).setTextColor(Color.WHITE)
                    v.setBackgroundColor(Color.parseColor("#1B2229"))
                    return v
                }
            }
            setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#1B2229")))
            setSelection(selectedOutputIndex)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    selectedOutputIndex = position
                    log("Çıkış seçildi: ${OUTPUTS[position].label} (${OUTPUTS[position].id})")
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        audioRow.addView(outputSpinner, LinearLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(10)
        })
        audioRow.addView(TextView(this).apply {
            text = "Ses %"
            setTextColor(Color.parseColor("#C7D0D9"))
            textSize = 16f
            setPadding(dp(20), 0, 0, 0)
        })
        volumeInput = EditText(this).apply {
            setText("100")
            setTextColor(Color.WHITE)
            textSize = 18f
            inputType = InputType.TYPE_CLASS_NUMBER
            setBackgroundColor(Color.parseColor("#1B2229"))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        audioRow.addView(volumeInput, LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(10)
        })
        root.addView(audioRow)

        input = EditText(this).apply {
            setText(DEFAULT_TEXT)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8899A6"))
            hint = "Okunacak Türkçe metin"
            textSize = 20f
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setBackgroundColor(Color.parseColor("#1B2229"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        root.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val speedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        speedRow.addView(TextView(this).apply {
            text = "Hız"
            setTextColor(Color.parseColor("#C7D0D9"))
            textSize = 16f
        })
        speedInput = EditText(this).apply {
            setText("1.0")
            setTextColor(Color.WHITE)
            textSize = 18f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setBackgroundColor(Color.parseColor("#1B2229"))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        speedRow.addView(speedInput, LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(10)
        })

        speedRow.addView(TextView(this).apply {
            text = "noise"
            setTextColor(Color.parseColor("#C7D0D9"))
            textSize = 16f
            setPadding(dp(20), 0, 0, 0)
        })
        noiseInput = EditText(this).apply {
            setText("0.667")
            setTextColor(Color.WHITE)
            textSize = 18f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setBackgroundColor(Color.parseColor("#1B2229"))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        speedRow.addView(noiseInput, LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(10)
        })

        speedRow.addView(TextView(this).apply {
            text = "noiseW"
            setTextColor(Color.parseColor("#C7D0D9"))
            textSize = 16f
            setPadding(dp(20), 0, 0, 0)
        })
        noiseWInput = EditText(this).apply {
            setText("0.8")
            setTextColor(Color.WHITE)
            textSize = 18f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setBackgroundColor(Color.parseColor("#1B2229"))
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        speedRow.addView(noiseWInput, LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(10)
        })
        root.addView(speedRow)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }
        speakButton = makeButton("Sherpa ile konuş") { speakSherpa() }
        stopButton = makeButton("Durdur") { stopPlayback() }
        systemButton = makeButton("Sistem TTS") { speakSystem() }
        buttons.addView(speakButton)
        buttons.addView(stopButton)
        buttons.addView(systemButton)
        buttons.addView(makeButton("Temizle") { logView.text = "" })
        root.addView(buttons)

        logView = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#B8C4CE"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(12), 0, dp(8))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            addView(logView, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.BLACK)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFB000"))
            setOnClickListener { action() }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(10)
            layoutParams = lp
        }
    }

    private fun initSherpa() {
        if (initStarted) return
        initStarted = true
        thread(name = "sherpa-init") {
            try {
                val voice = VOICES[selectedVoiceIndex]
                log("Model: ${voice.label} (${voice.assetDir})")
                val t0 = SystemClock.elapsedRealtime()
                val modelDir = prepareVoiceDir(voice)
                val dataDir = prepareEspeakData()
                val created = buildEngine(modelDir, voice.modelFile, dataDir, engineNoise, engineNoiseW)
                val loadMs = SystemClock.elapsedRealtime() - t0
                val sampleRate = created.sampleRate()
                val speakers = created.numSpeakers()
                createAudioTrack(sampleRate)
                engine = created
                engineVoiceId = voice.id
                setStatus("Sherpa hazır • ${voice.label} • ${sampleRate} Hz • ${speakers} konuşmacı • yükleme ${loadMs} ms")
                log("Motor hazır: voice=${voice.id} sampleRate=$sampleRate speakers=$speakers load=${loadMs}ms noise=$engineNoise noiseW=$engineNoiseW")
                if (autoSpeak) {
                    autoSpeak = false
                    ui.post { speakSherpa() }
                }
            } catch (t: Throwable) {
                initStarted = false
                setStatus("Sherpa yüklenemedi: ${t.javaClass.simpleName}: ${t.message}")
                log("HATA: ${Log.getStackTraceString(t)}")
            }
        }
    }

    private fun buildEngine(
        modelDir: File,
        modelFile: String,
        dataDir: File,
        noise: Float,
        noiseW: Float,
    ): OfflineTts {
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(modelDir, modelFile).absolutePath,
                    tokens = File(modelDir, "tokens.txt").absolutePath,
                    dataDir = dataDir.absolutePath,
                    noiseScale = noise,
                    noiseScaleW = noiseW,
                    lengthScale = 1.0f
                ),
                numThreads = 2,
                debug = true
            )
        )
        return OfflineTts(config = config)
    }

    @Synchronized
    private fun ensureEngine(voice: Voice, noise: Float, noiseW: Float): OfflineTts {
        val current = engine
        if (current != null &&
            engineVoiceId == voice.id &&
            kotlin.math.abs(engineNoise - noise) < 0.001f &&
            kotlin.math.abs(engineNoiseW - noiseW) < 0.001f
        ) {
            return current
        }
        val t0 = SystemClock.elapsedRealtime()
        try { current?.free() } catch (_: Throwable) {}
        engine = null
        val modelDir = prepareVoiceDir(voice)
        val dataDir = prepareEspeakData()
        val created = buildEngine(modelDir, voice.modelFile, dataDir, noise, noiseW)
        val sampleRate = created.sampleRate()
        ensureTrack(sampleRate)
        engine = created
        engineVoiceId = voice.id
        engineNoise = noise
        engineNoiseW = noiseW
        val loadMs = SystemClock.elapsedRealtime() - t0
        log("Motor yeniden yüklendi: voice=${voice.id} noise=$noise noiseW=$noiseW load=${loadMs}ms")
        setStatus("Sherpa hazır • ${voice.label} • noise=$noise noiseW=$noiseW • yükleme ${loadMs} ms")
        return created
    }

    private fun prepareVoiceDir(voice: Voice): File {
        val dest = File(filesDir, voice.assetDir)
        val marker = File(dest, ".extracted")
        if (marker.exists() && File(dest, voice.modelFile).exists()) {
            log("Model zaten çıkarılmış: ${dest.absolutePath}")
            return dest
        }
        if (dest.exists()) dest.deleteRecursively()
        val t0 = SystemClock.elapsedRealtime()
        log("Model dosyaları assets -> ${dest.absolutePath} çıkarılıyor...")
        extractAssetDir(voice.assetDir, dest, setOf("espeak-ng-data"))
        marker.writeText("1")
        log("Çıkarma tamam: ${SystemClock.elapsedRealtime() - t0} ms")
        return dest
    }

    private fun prepareEspeakData(): File {
        val dest = File(filesDir, "espeak-ng-data")
        val marker = File(dest, ".extracted")
        if (marker.exists() && File(dest, "phontab").exists()) {
            return dest
        }
        if (dest.exists()) dest.deleteRecursively()
        val t0 = SystemClock.elapsedRealtime()
        log("espeak-ng-data çıkarılıyor: ${dest.absolutePath}")
        extractAssetDir("${VOICES[0].assetDir}/espeak-ng-data", dest)
        marker.writeText("1")
        log("espeak-ng-data tamam: ${SystemClock.elapsedRealtime() - t0} ms")
        return dest
    }

    private fun extractAssetDir(assetPath: String, dest: File, skip: Set<String> = emptySet()) {
        val children = assets.list(assetPath)
        if (children.isNullOrEmpty()) {
            dest.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            return
        }
        dest.mkdirs()
        for (child in children) {
            if (child in skip) continue
            extractAssetDir("$assetPath/$child", File(dest, child), skip)
        }
    }

    private fun createAudioTrack(sampleRate: Int) {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufferSize = max(minBuffer, sampleRate / 5 * 4)
        val output = OUTPUTS[selectedOutputIndex]
        val attrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(output.usage)
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
        if (created.state != AudioTrack.STATE_INITIALIZED) {
            throw IllegalStateException("AudioTrack başlatılamadı, state=${created.state}")
        }
        created.play()
        track = created
        trackSampleRate = sampleRate
        trackUsage = output.usage
        applyVolume()
        log("AudioTrack: ${sampleRate}Hz mono float, usage=${output.label}(${output.id}), buffer=$bufferSize, state=${created.state}")
    }

    private fun ensureTrack(sampleRate: Int) {
        val usage = OUTPUTS[selectedOutputIndex].usage
        if (track == null || trackSampleRate != sampleRate || trackUsage != usage) {
            try { track?.pause(); track?.flush(); track?.release() } catch (_: Throwable) {}
            track = null
            createAudioTrack(sampleRate)
        } else {
            applyVolume()
        }
    }

    private fun applyVolume() {
        val percent = volumeInput.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: 100
        try { track?.setVolume(percent / 100f) } catch (_: Throwable) {}
        log("Ses seviyesi: $percent%")
    }

    private fun speakSherpa() {
        if (busy) {
            toast("Üretim sürüyor")
            return
        }
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            toast("Metin boş")
            return
        }
        val speed = speedInput.text.toString().toFloatOrNull()?.takeIf { it > 0f } ?: 1.0f
        val noise = noiseInput.text.toString().toFloatOrNull()?.coerceIn(0.05f, 1.5f) ?: 0.667f
        val noiseW = noiseWInput.text.toString().toFloatOrNull()?.coerceIn(0.05f, 1.5f) ?: 0.8f
        val voice = VOICES[selectedVoiceIndex]
        val usage = OUTPUTS[selectedOutputIndex]
        busy = true
        stopped = false
        val t0 = SystemClock.elapsedRealtime()
        log(
            "Üretim başladı: ${text.length} karakter, voice=${voice.id} " +
                "usage=${usage.id} volume=${volumeInput.text} speed=$speed noise=$noise noiseW=$noiseW"
        )
        thread(name = "sherpa-generate") {
            try {
                val tts = ensureEngine(voice, noise, noiseW)
                ensureTrack(tts.sampleRate())
                val audio = tts.generate(text, speed = speed)
                val raw = stats(audio.samples)
                val gain = normalize(audio.samples, 0.6f)
                val generateMs = SystemClock.elapsedRealtime() - t0
                val durationSec = audio.samples.size.toDouble() / audio.sampleRate
                val rtf = if (durationSec > 0) generateMs / 1000.0 / durationSec else 0.0
                val stats = stats(audio.samples)
                val wav = saveWav(audio)
                log(
                    "Üretim: ${generateMs}ms • ses=${"%.2f".format(durationSec)}s • " +
                        "RTF=${"%.2f".format(rtf)} • peak=${"%.2f".format(stats.first)} " +
                        "rms=${"%.3f".format(stats.second)} • ${audio.sampleRate}Hz • " +
                        "normalize=${"%.2f".format(gain)}x (rawPeak=${"%.2f".format(raw.first)})"
                )
                log("WAV: ${wav.absolutePath} (${wav.length()} bayt)")
                if (!stopped) {
                    playAudio(audio)
                } else {
                    log("Durduruldu; oynatma atlandı")
                }
            } catch (t: Throwable) {
                log("ÜRETİM HATASI: ${Log.getStackTraceString(t)}")
                setStatus("Üretim hatası: ${t.javaClass.simpleName}")
            } finally {
                busy = false
            }
        }
    }

    private fun playAudio(audio: GeneratedAudio) {
        val t = track ?: return
        try {
            t.pause()
            t.flush()
            t.play()
        } catch (e: Throwable) {
            log("AudioTrack başlatma hatası: $e")
            return
        }
        val chunk = 2048
        var offset = 0
        while (offset < audio.samples.size && !stopped) {
            val count = min(chunk, audio.samples.size - offset)
            val written = t.write(audio.samples, offset, count, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) {
                log("AudioTrack write durdu: $written")
                break
            }
            offset += written
        }
        log("Oynatma bitti: ${offset}/${audio.samples.size} örnek")
    }

    private fun stopPlayback() {
        stopped = true
        try {
            track?.pause()
            track?.flush()
        } catch (_: Throwable) {}
        try { systemTts?.stop() } catch (_: Throwable) {}
        log("Durduruldu")
    }

    private fun speakSystem() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            toast("Metin boş")
            return
        }
        val existing = systemTts
        if (existing != null) {
            existing.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ttsprobe")
            return
        }
        setStatus("Sistem TTS başlatılıyor...")
        val ref = java.util.concurrent.atomic.AtomicReference<TextToSpeech?>()
        val created = TextToSpeech(this) { status ->
            val t = ref.get()
            if (status == TextToSpeech.SUCCESS && t != null) {
                val result = t.setLanguage(Locale("tr", "TR"))
                val voices = t.voices?.map { it.name }?.take(8)
                log("Sistem TTS hazır: dilSonucu=$result voices=$voices")
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ttsprobe")
                setStatus("Sistem TTS hazır")
            } else {
                log("Sistem TTS motoru yok (status=$status)")
                setStatus("Sistem TTS motoru yok")
            }
        }
        ref.set(created)
        systemTts = created
    }

    private fun normalize(samples: FloatArray, target: Float): Float {
        var peak = 0f
        for (s in samples) {
            val a = kotlin.math.abs(s)
            if (a > peak) peak = a
        }
        if (peak <= 0f) return 1f
        val gain = min(3f, target / peak)
        if (kotlin.math.abs(gain - 1f) > 0.02f) {
            for (i in samples.indices) samples[i] *= gain
        }
        return gain
    }

    private fun stats(samples: FloatArray): Pair<Float, Float> {
        if (samples.isEmpty()) return 0f to 0f
        var peak = 0f
        var sum = 0.0
        for (s in samples) {
            val a = kotlin.math.abs(s)
            if (a > peak) peak = a
            sum += s.toDouble() * s
        }
        return peak to sqrt(sum / samples.size).toFloat()
    }

    private fun saveWav(audio: GeneratedAudio): File {
        val dir = filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "tts_probe_last.wav")
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
        return file
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        ui.post {
            logView.append(message + "\n")
        }
    }

    private fun setStatus(message: String) {
        Log.i(TAG, "STATUS: $message")
        ui.post { statusView.text = message }
    }

    private fun toast(message: String) {
        ui.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
