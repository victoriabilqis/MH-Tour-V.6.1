package com.mhtour.audio

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.token.TokenRequestOptions
import io.livekit.android.token.TokenSource
import io.livekit.android.token.cached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult

class MainActivity : ComponentActivity() {
    // LiveKit tetap dipertahankan seperti versi sebelumnya.
    private val liveKitTokenSource by lazy {
        TokenSource.fromDevelopmentTokenServer("mhtour-19kg8e").cached()
    }

    private var room: Room? = null
    private var currentCode: String? = null
    private var sessionActive = false
    private var micEnabled = false
    private var isHomePage = true
    private var lastBackPressedAt = 0L
    private val roomUiHandler = Handler(Looper.getMainLooper())
    private var roomUiRefresh: Runnable? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true
        val cameraGranted = grants[Manifest.permission.CAMERA] == true
        if (cameraGranted) startQrScanner()
        if (!micGranted && grants.containsKey(Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this, "Izin mikrofon diperlukan untuk Guide berbicara.", Toast.LENGTH_LONG).show()
        }
    }

    private val storageLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) saveDonationQr()
        else Toast.makeText(this, "Izin penyimpanan diperlukan untuk mengunduh QR Code.", Toast.LENGTH_LONG).show()
    }

    private val green = Color.rgb(7, 88, 67)
    private val emerald = Color.rgb(0, 126, 115)
    private val deep = Color.rgb(3, 58, 44)
    private val gold = Color.rgb(198, 145, 36)
    private val cream = Color.rgb(249, 251, 249)
    private val ink = Color.rgb(30, 41, 59)
    private val muted = Color.rgb(100, 116, 139)
    private val white = Color.WHITE
    private val danaUrl = "https://link.dana.id/minta?full_url=https://qr.dana.id/v1/281012012023022707162488"

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LiveKit.init(applicationContext)
        setupBackNavigation()
        home()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isHomePage) {
                    // Back dari halaman mana pun kembali ke beranda.
                    // Khusus Guide, sesi/room TIDAK dihentikan.
                    home()
                    return
                }

                val now = System.currentTimeMillis()
                if (now - lastBackPressedAt < 2000L) {
                    finishAndRemoveTask()
                } else {
                    lastBackPressedAt = now
                    Toast.makeText(this@MainActivity, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun hasInternet(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun requireInternet(): Boolean {
        if (hasInternet()) return true
        Toast.makeText(this, "Tidak ada koneksi internet. Aktifkan Wi-Fi atau data seluler.", Toast.LENGTH_LONG).show()
        return false
    }

    private fun requestMicIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun requestCameraIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        } else startQrScanner()
    }

    private fun bg(color: Int, radius: Float = 18f, stroke: Int? = null, strokeWidth: Int = 1) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius.toInt()).toFloat()
            stroke?.let { setStroke(dp(strokeWidth), it) }
        }

    private fun gradientBg(top: Int, bottom: Int, radius: Float = 24f) =
        android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(top, bottom)
        ).apply { cornerRadius = dp(radius.toInt()).toFloat() }

    private fun tv(text: String, size: Float, color: Int = ink, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        includeFontPadding = true
    }

    private fun primaryButton(text: String, color: Int = emerald): Button = Button(this).apply {
        this.text = text
        setTextColor(white)
        textSize = 15f
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        minHeight = dp(52)
        background = bg(color, 18f)
        stateListAnimator = null
        elevation = dp(2).toFloat()
    }

    private fun outlineButton(text: String): Button = Button(this).apply {
        this.text = text
        setTextColor(green)
        textSize = 15f
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        minHeight = dp(50)
        background = bg(white, 18f, Color.rgb(23, 118, 99), 2)
        stateListAnimator = null
    }

    private fun edit(hint: String, value: String): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
        setSingleLine(true)
        textSize = 15f
        setTextColor(ink)
        setHintTextColor(muted)
        background = bg(white, 14f, Color.rgb(226, 232, 240), 1)
        setPadding(dp(16), 0, dp(16), 0)
    }

    private fun logoView(size: Int): ImageView = ImageView(this).apply {
        setImageResource(R.drawable.mh_tour_logo)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "Logo MH Tour"
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun page(title: String, subtitle: String): LinearLayout {
        isHomePage = false
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cream)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(10), dp(18), dp(14))
            background = gradientBg(deep, green, 0f)
        }
        header.addView(logoView(88), LinearLayout.LayoutParams(dp(88), dp(88)))
        header.addView(tv(title, 23f, white, true).apply { gravity = Gravity.CENTER })
        header.addView(tv(subtitle, 12.5f, Color.rgb(226, 240, 235)).apply {
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(3), dp(4), 0)
        })
        root.addView(header)
        return root
    }

    private fun scroll(content: View): ScrollView = ScrollView(this).apply {
        isFillViewport = true
        addView(content)
    }

    private fun contentColumn(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(28))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(17))
        background = bg(white, 20f, Color.rgb(222, 233, 228), 1)
        elevation = dp(1).toFloat()
    }

    private fun addGap(parent: LinearLayout, h: Int = 12) =
        parent.addView(Space(this), LinearLayout.LayoutParams(1, dp(h)))

    private fun home() {
        isHomePage = true

        /*
         * Beranda sengaja menggunakan artwork referensi yang diberikan pengguna.
         * Tidak ada fungsi yang dipindahkan/dihapus: tiga area transparan di atas
         * artwork hanya menjadi tombol sentuh untuk fungsi yang sama seperti versi
         * sebelumnya.
         */
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            isClickable = true
        }

        val artwork = ImageView(this).apply {
            setImageResource(R.drawable.home_reference)
            scaleType = ImageView.ScaleType.FIT_XY
            contentDescription = "Beranda MH Tour"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(artwork, FrameLayout.LayoutParams(-1, -1))

        fun touchZone(description: String): TextView = TextView(this).apply {
            text = ""
            isClickable = true
            isFocusable = true
            contentDescription = description
            background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            // Jangan tampilkan teks tambahan di atas artwork referensi.
            setTextColor(Color.TRANSPARENT)
        }

        val jamaahBtn = touchZone("Dengarkan Sebagai Jemaah")
        val guideBtn = touchZone("Lanjut Sebagai Pemandu")
        val donationBtn = touchZone("Donasi Seikhlasnya")
        root.addView(jamaahBtn)
        root.addView(guideBtn)
        root.addView(donationBtn)

        // Posisi mengikuti tiga tombol pada gambar referensi dan ikut
        // menyesuaikan ukuran layar/rasio perangkat.
        root.post {
            val w = root.width
            val h = root.height
            fun place(view: View, leftPct: Float, topPct: Float, widthPct: Float, heightPct: Float) {
                val lp = FrameLayout.LayoutParams(
                    (w * widthPct).toInt(),
                    (h * heightPct).toInt()
                )
                lp.leftMargin = (w * leftPct).toInt()
                lp.topMargin = (h * topPct).toInt()
                view.layoutParams = lp
            }

            // Area sentuh sengaja sedikit lebih luas daripada gambar tombol
            // supaya mudah ditekan tanpa mengubah tampilan visual.
            // Pemandu di atas, Jemaah di bawah sesuai alur utama aplikasi.
            place(guideBtn, 0.075f, 0.582f, 0.85f, 0.085f)
            place(jamaahBtn, 0.075f, 0.682f, 0.85f, 0.085f)
            place(donationBtn, 0.145f, 0.789f, 0.71f, 0.075f)
        }

        setContentView(root)

        jamaahBtn.setOnClickListener {
            if (requireInternet()) jamaahScreen()
        }
        guideBtn.setOnClickListener {
            if (requireInternet()) guideScreen()
        }
        donationBtn.setOnClickListener {
            donationScreen()
        }
    }

    private fun donationScreen() {
        val root = page("Donasi Seikhlasnya", "Dukungan Anda membantu pengembangan dan pemeliharaan MH Tour.")
        val body = contentColumn()

        val intro = card()
        intro.gravity = Gravity.CENTER_HORIZONTAL
        intro.addView(tv("DUKUNG MH TOUR", 12f, gold, true).apply { gravity = Gravity.CENTER })
        addGap(intro, 6)
        intro.addView(tv("Jika berkenan, silakan berdonasi seikhlasnya melalui DANA.", 15f, ink, true).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.1f)
        })
        addGap(intro, 14)

        val qr = ImageView(this).apply {
            setImageResource(R.drawable.donation_qr)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
            contentDescription = "QR Code Donasi DANA"
        }
        intro.addView(qr, LinearLayout.LayoutParams(-1, dp(300)))
        addGap(intro, 8)
        intro.addView(tv("Scan QR Code untuk berdonasi", 13f, muted).apply { gravity = Gravity.CENTER })
        addGap(intro, 14)

        val download = primaryButton("⬇  Unduh QR Code", green)
        intro.addView(download, LinearLayout.LayoutParams(-1, dp(54)))
        addGap(intro, 10)

        val dana = primaryButton("DANA • Donasi Langsung", emerald)
        intro.addView(dana, LinearLayout.LayoutParams(-1, dp(54)))
        body.addView(intro)
        addGap(body, 14)

        val back = outlineButton("Kembali ke Beranda")
        body.addView(back)
        root.addView(scroll(body), LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        download.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveDonationQr()
            else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) saveDonationQr()
            else storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        dana.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(danaUrl)))
            } catch (e: Exception) {
                Toast.makeText(this, "Aplikasi DANA/browser tidak tersedia.", Toast.LENGTH_LONG).show()
            }
        }
        back.setOnClickListener { home() }
    }

    private fun saveDonationQr() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeResource(resources, R.drawable.donation_qr)
                    ?: error("QR Code tidak dapat dibaca")
                val fileName = "MH-Tour-QR-Donasi-DANA.png"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "image/png")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("Tidak dapat membuat file unduhan")
                    try {
                        contentResolver.openOutputStream(uri)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        } ?: error("Tidak dapat menulis file")
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)
                    } catch (e: Exception) {
                        contentResolver.delete(uri, null, null)
                        throw e
                    }
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "QR Code berhasil diunduh ke folder Download.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Gagal mengunduh QR Code: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareText(text: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
            }
            startActivity(Intent.createChooser(intent, "Bagikan Rombongan MH Tour"))
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak ada aplikasi yang dapat digunakan untuk berbagi.", Toast.LENGTH_LONG).show()
        }
    }

    private fun guideScreen() {
        val root = page("Ruang Audio Guide", "Buat ruang rombongan, bagikan QR, lalu bicara secara real-time.")
        val body = contentColumn()

        val identity = card()
        identity.addView(tv("PROFIL GUIDE", 12f, gold, true)); addGap(identity, 5)
        val name = edit("Nama Guide / Muthawwif", "Guide")
        identity.addView(name, LinearLayout.LayoutParams(-1, dp(52)))
        body.addView(identity); addGap(body)

        val session = card()
        session.addView(tv("SESI ROMBONGAN", 12f, gold, true)); addGap(session, 4)
        val code = tv(currentCode ?: "—", 34f, green, true).apply { gravity = Gravity.CENTER }
        session.addView(code)
        val hint = tv(
            if (sessionActive) "Sesi sebelumnya masih aktif. Akhiri sesi sebelum membuat sesi baru."
            else "Kode akan muncul setelah sesi dibuat",
            12f, muted
        ).apply { gravity = Gravity.CENTER }
        session.addView(hint)
        addGap(session, 10)

        val qrTitle = tv("QR CODE ROMBONGAN", 11f, gold, true).apply { gravity = Gravity.CENTER }
        session.addView(qrTitle); addGap(session, 6)
        val qr = ImageView(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = if (sessionActive) View.VISIBLE else View.GONE
            if (sessionActive && currentCode != null) setImageBitmap(makeQrBitmap("MHTOUR|JOIN|$currentCode"))
        }
        session.addView(qr, LinearLayout.LayoutParams(-1, dp(250)))
        val qrHint = tv("Jemaah dapat memindai QR ini untuk bergabung ke rombongan.", 12f, muted).apply {
            gravity = Gravity.CENTER
            visibility = if (sessionActive) View.VISIBLE else View.GONE
        }
        session.addView(qrHint)
        addGap(session, 10)

        val create = primaryButton(if (sessionActive) "Sesi Aktif — Akhiri Sesi Dulu" else "Buat Sesi Rombongan", gold)
        create.isEnabled = !sessionActive
        session.addView(create); addGap(session, 10)
        val shareQr = outlineButton("Bagikan Kode & QR Rombongan")
        shareQr.isEnabled = sessionActive
        session.addView(shareQr); addGap(session, 10)
        val roomBtn = outlineButton("👥  Lihat Room & Daftar Jemaah")
        roomBtn.isEnabled = sessionActive && room != null
        session.addView(roomBtn); addGap(session, 10)
        val end = outlineButton("⏹  Akhiri Sesi")
        end.isEnabled = sessionActive
        session.addView(end)
        body.addView(session); addGap(body)

        val live = card()
        live.addView(tv("KONTROL AUDIO", 12f, gold, true)); addGap(live, 5)
        val micPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        val micCircle = TextView(this).apply {
            text = "🎙"
            textSize = 42f
            gravity = Gravity.CENTER
            setTextColor(white)
            background = bg(emerald, 60f)
        }
        micPanel.addView(micCircle, LinearLayout.LayoutParams(dp(104), dp(104)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val micStatus = tv(
            when {
                !sessionActive -> "MIKROFON SIAP"
                micEnabled -> "●  ANDA SEDANG BERBICARA"
                room != null -> "MIKROFON TERHUBUNG"
                else -> "MIKROFON SIAP"
            }, 14f, muted, true
        ).apply { gravity = Gravity.CENTER }
        micPanel.addView(micStatus, LinearLayout.LayoutParams(-1, dp(36)))
        live.addView(micPanel)
        addGap(live, 5)

        val stateText = when {
            !sessionActive -> "Belum ada sesi aktif"
            room != null && micEnabled -> "TERHUBUNG • Suara Anda LIVE ke jemaah"
            room != null -> "TERHUBUNG • Mikrofon dimute"
            else -> "Sesi siap — tekan Mulai Bicara"
        }
        val state = tv(stateText, 16f, muted, true).apply { gravity = Gravity.CENTER }
        live.addView(state); addGap(live, 10)

        val talk = primaryButton(if (micEnabled) "Mute" else "🎙  Mulai Bicara", emerald)
        talk.isEnabled = sessionActive
        live.addView(talk); addGap(live, 8)
        val mute = outlineButton(if (micEnabled) "Mute Mikrofon" else "Unmute Mikrofon")
        mute.isEnabled = room != null
        live.addView(mute); addGap(live, 8)
        val back = outlineButton("Kembali ke Beranda")
        live.addView(back)
        body.addView(live)

        root.addView(scroll(body), LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        create.setOnClickListener {
            if (sessionActive) {
                Toast.makeText(this, "Akhiri sesi sebelumnya terlebih dahulu.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!requireInternet()) return@setOnClickListener
            create.isEnabled = false
            lifecycleScope.launch {
                try {
                    currentCode = generateSessionCode()
                    sessionActive = true
                    code.text = currentCode
                    qr.setImageBitmap(makeQrBitmap("MHTOUR|JOIN|$currentCode"))
                    qr.visibility = View.VISIBLE
                    qrHint.visibility = View.VISIBLE
                    shareQr.isEnabled = true
                    end.isEnabled = true
                    talk.isEnabled = true
                    hint.text = "Sesi aktif. Sesi baru tidak dapat dibuat sebelum sesi ini diakhiri."
                    state.text = "Sesi siap — bagikan kode atau QR ke jemaah."
                } catch (e: Exception) {
                    sessionActive = false
                    currentCode = null
                    state.text = "Gagal membuat sesi: ${e.message}"
                } finally {
                    create.isEnabled = !sessionActive
                    create.text = if (sessionActive) "Sesi Aktif — Akhiri Sesi Dulu" else "Buat Sesi Rombongan"
                }
            }
        }

        shareQr.setOnClickListener {
            currentCode?.let { c -> shareText("MH Tour — Rombongan: $c\n\nScan QR untuk bergabung. Kode rombongan: $c") }
        }

        roomBtn.setOnClickListener {
            if (!sessionActive || room == null) {
                Toast.makeText(this, "Hubungkan sesi terlebih dahulu untuk melihat daftar jemaah.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            roomScreen()
        }

        end.setOnClickListener {
            room?.disconnect()
            room = null
            currentCode = null
            sessionActive = false
            micEnabled = false
            Toast.makeText(this, "Sesi rombongan telah diakhiri.", Toast.LENGTH_LONG).show()
            guideScreen()
        }

        talk.setOnClickListener {
            if (!sessionActive || currentCode.isNullOrBlank()) {
                Toast.makeText(this, "Buat sesi rombongan terlebih dahulu.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!requireInternet()) return@setOnClickListener
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestMicIfNeeded()
                state.text = "Izinkan mikrofon, lalu tekan Mulai Bicara lagi."
                return@setOnClickListener
            }
            talk.isEnabled = false
            state.text = "Menghubungkan ke server LiveKit..."
            lifecycleScope.launch {
                try {
                    val token = requestLiveKitToken(currentCode!!, name.text.toString().trim().ifBlank { "Guide" })
                    room?.disconnect()
                    room = connectLiveKit(token)
                    val ok = room!!.localParticipant.setMicrophoneEnabled(true)
                    if (!ok) error("Mikrofon gagal dipublish")
                    micEnabled = true
                    mute.isEnabled = true
                    talk.text = "Mute"
                    roomBtn.isEnabled = sessionActive && room != null
                    micStatus.text = "●  ANDA SEDANG BERBICARA"
                    state.text = "TERHUBUNG • Suara Anda LIVE ke jemaah"
                    addPulse(micCircle)
                } catch (e: Exception) {
                    state.text = "Gagal terhubung: ${e.message}"
                    micStatus.text = "MIKROFON GAGAL TERHUBUNG"
                    room?.disconnect(); room = null; micEnabled = false
                } finally {
                    talk.isEnabled = sessionActive
                }
            }
        }

        mute.setOnClickListener {
            lifecycleScope.launch {
                val targetEnabled = !micEnabled
                mute.isEnabled = false
                try {
                    val activeRoom = room ?: error("Belum terhubung ke sesi audio")
                    val applied = activeRoom.localParticipant.setMicrophoneEnabled(targetEnabled)
                    if (!applied) error("Server audio tidak menerima perubahan mikrofon")
                    micEnabled = targetEnabled
                    mute.text = if (micEnabled) "Mute Mikrofon" else "Unmute Mikrofon"
                    talk.text = if (micEnabled) "Mute" else "🎙  Mulai Bicara"
                    micStatus.text = if (micEnabled) "●  ANDA SEDANG BERBICARA" else "MIKROFON DIMUTE"
                    state.text = if (micEnabled) "Mikrofon LIVE • Jemaah dapat mendengar" else "Mikrofon dimute • Sesi tetap tersambung"
                    if (micEnabled) addPulse(micCircle) else stopPulse(micCircle)
                } catch (e: Exception) {
                    state.text = "Gagal mengubah mikrofon: ${e.message}"
                } finally {
                    mute.isEnabled = room != null
                }
            }
        }
        back.setOnClickListener { home() }
    }

    private fun roomScreen() {
        val root = page("Room Rombongan", "Daftar jemaah yang sedang terhubung ke streaming MH Tour.")
        val body = contentColumn()

        val summary = card()
        summary.addView(tv("STATUS ROOM", 12f, gold, true))
        addGap(summary, 8)
        val codeView = tv("Kode: ${currentCode ?: "—"}", 18f, green, true).apply { gravity = Gravity.CENTER }
        summary.addView(codeView)
        addGap(summary, 6)
        val countView = tv("Memuat peserta...", 14f, muted, true).apply { gravity = Gravity.CENTER }
        summary.addView(countView)
        body.addView(summary)
        addGap(body, 14)

        val listCard = card()
        listCard.addView(tv("DAFTAR JEMAAH", 12f, gold, true))
        addGap(listCard, 8)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listCard.addView(list, LinearLayout.LayoutParams(-1, -2))
        body.addView(listCard)
        addGap(body, 14)

        val refresh = primaryButton("↻  Perbarui Daftar")
        body.addView(refresh)
        addGap(body, 10)
        val back = outlineButton("Kembali ke Ruang Guide")
        body.addView(back)

        root.addView(scroll(body), LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        fun refreshParticipants() {
            val activeRoom = room
            if (activeRoom == null) {
                countView.text = "Room tidak terhubung"
                list.removeAllViews()
                return
            }
            val participants = activeRoom.remoteParticipants.values.toList()
            val total = participants.size
            countView.text = "$total jemaah terhubung"
            list.removeAllViews()
            if (participants.isEmpty()) {
                list.addView(tv("Belum ada jemaah yang bergabung.", 14f, muted).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(18), 0, dp(18))
                })
            } else {
                participants.sortedWith(Comparator { first, second ->
                    val firstName = first.name.toString().takeUnless { it == "null" }.orEmpty()
                        .ifBlank { first.identity.toString().takeUnless { it == "null" }.orEmpty() }
                    val secondName = second.name.toString().takeUnless { it == "null" }.orEmpty()
                        .ifBlank { second.identity.toString().takeUnless { it == "null" }.orEmpty() }
                    firstName.compareTo(secondName, ignoreCase = true)
                }).forEach { participant ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(4), dp(10), dp(4), dp(10))
                    }
                    val onlineDot = TextView(this).apply {
                        text = "●"
                        textSize = 20f
                        setTextColor(emerald)
                        gravity = Gravity.CENTER
                    }
                    row.addView(onlineDot, LinearLayout.LayoutParams(dp(30), dp(42)))
                    val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    val participantName = participant.name.toString().takeUnless { it == "null" }.orEmpty()
                        .ifBlank { participant.identity.toString().takeUnless { it == "null" }.orEmpty() }
                        .ifBlank { "Jemaah" }
                    info.addView(tv(participantName, 15f, ink, true))
                    info.addView(tv("ONLINE • Terhubung ke room", 12f, emerald, false))
                    row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
                    list.addView(row)
                    list.addView(View(this).apply { setBackgroundColor(Color.rgb(226, 232, 240)) }, LinearLayout.LayoutParams(-1, dp(1)))
                }
            }
        }

        refresh.setOnClickListener { refreshParticipants() }
        back.setOnClickListener { guideScreen() }
        refreshParticipants()

        roomUiRefresh?.let { roomUiHandler.removeCallbacks(it) }
        roomUiRefresh = object : Runnable {
            override fun run() {
                if (!isFinishing && !isHomePage) {
                    refreshParticipants()
                    roomUiHandler.postDelayed(this, 1500L)
                }
            }
        }
        roomUiHandler.postDelayed(roomUiRefresh!!, 1500L)
    }

    private fun addPulse(view: View) {
        view.animate().cancel()
        view.scaleX = 1f; view.scaleY = 1f; view.alpha = 1f
        view.animate().scaleX(1.10f).scaleY(1.10f).alpha(0.78f).setDuration(650).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(650).withEndAction {
                if (!isFinishing && micEnabled) addPulse(view)
            }.start()
        }.start()
    }

    private fun stopPulse(view: View) {
        view.animate().cancel(); view.scaleX = 1f; view.scaleY = 1f; view.alpha = 1f
    }

    private fun jamaahScreen(prefilledCode: String = "") {
        val root = page("Masuk ke Rombongan", "Masukkan kode yang diberikan Guide untuk mulai menerima audio.")
        val body = contentColumn()
        val form = card()
        form.addView(tv("DATA JEMAAH", 12f, gold, true)); addGap(form, 5)
        val name = edit("Nama Jemaah", "Jemaah")
        form.addView(name, LinearLayout.LayoutParams(-1, dp(52))); addGap(form, 10)
        val code = edit("Kode sesi  •  contoh UM123456", prefilledCode)
        code.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        form.addView(code, LinearLayout.LayoutParams(-1, dp(52))); addGap(form, 10)
        val scan = outlineButton("▣  Scan QR Rombongan")
        form.addView(scan); addGap(form, 12)
        val join = primaryButton("Gabung & Dengarkan", green)
        form.addView(join)
        body.addView(form); addGap(body)

        val listening = card()
        listening.addView(tv("STATUS AUDIO", 12f, gold, true)); addGap(listening, 4)
        val state = tv("Belum terhubung", 17f, muted, true).apply { gravity = Gravity.CENTER }
        listening.addView(state); addGap(listening, 10)
        val indicator = tv("◉  MENUNGGU SUARA GUIDE", 13f, green, true).apply { gravity = Gravity.CENTER }
        listening.addView(indicator); addGap(listening, 12)
        listening.addView(tv("Pastikan volume media HP aktif. Audio Guide akan terdengar otomatis setelah Guide mulai berbicara.", 12f, muted))
        addGap(listening, 12)
        listening.addView(outlineButton("Kembali ke Beranda").apply { setOnClickListener { home() } })
        body.addView(listening)

        root.addView(scroll(body), LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        scan.setOnClickListener { requestCameraIfNeeded() }
        join.setOnClickListener {
            if (!requireInternet()) return@setOnClickListener
            val c = code.text.toString().trim().uppercase()
            if (c.isBlank()) { code.error = "Masukkan kode sesi"; return@setOnClickListener }
            join.isEnabled = false
            state.text = "Menghubungkan..."
            indicator.text = "◉  MENGHUBUNGKAN KE SERVER LIVEKIT"
            lifecycleScope.launch {
                try {
                    val token = requestLiveKitToken(c, name.text.toString().trim().ifBlank { "Jemaah" })
                    room?.disconnect()
                    room = connectLiveKit(token)
                    currentCode = c
                    state.text = "TERHUBUNG • Menunggu Guide"
                    indicator.text = "●  SIAP MENDENGARKAN"
                } catch (e: Exception) {
                    state.text = "Gagal bergabung: ${e.message}"
                    indicator.text = "◉  KONEKSI GAGAL"
                    room?.disconnect(); room = null
                } finally { join.isEnabled = true }
            }
        }
    }

    private fun startQrScanner() {
        IntentIntegrator(this).apply {
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            setPrompt("Arahkan kamera ke QR Code rombongan MH Tour")
            setBeepEnabled(true)
            setOrientationLocked(false)
            initiateScan()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result: IntentResult? = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                val payload = result.contents.trim()
                val parts = payload.split("|")
                val code = when {
                    parts.size >= 4 && parts[0] == "MHTOUR" && parts[1] == "JOIN" -> parts[3].trim().uppercase()
                    parts.size >= 3 && parts[0] == "MHTOUR" && parts[1] == "JOIN" -> parts[2].trim().uppercase()
                    else -> payload.removePrefix("MHTOUR|JOIN|").trim().uppercase()
                }
                if (code.matches(Regex("UM[0-9]{6}"))) jamaahScreenWithCode(code)
                else Toast.makeText(this, "QR bukan QR rombongan MH Tour yang valid", Toast.LENGTH_LONG).show()
            } else Toast.makeText(this, "Pemindaian dibatalkan", Toast.LENGTH_SHORT).show()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun jamaahScreenWithCode(codeValue: String) = jamaahScreen(codeValue)

    private fun generateSessionCode(): String = "UM${(100000..999999).random()}"

    private fun makeQrBitmap(payload: String, size: Int = 720): Bitmap {
        val matrix: BitMatrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        return bitmap
    }

    private suspend fun requestLiveKitToken(code: String, name: String): JSONObject = withContext(Dispatchers.IO) {
        val roomName = "mh-tour-$code"
        val identity = "${name.replace("\\s+".toRegex(), "-")}-${java.util.UUID.randomUUID()}"
        val result = liveKitTokenSource.fetch(
            TokenRequestOptions(roomName = roomName, participantName = name, participantIdentity = identity)
        )
        val credentials = result.getOrThrow()
        JSONObject().put("serverUrl", credentials.serverUrl).put("participantToken", credentials.participantToken)
    }

    private suspend fun connectLiveKit(response: JSONObject): Room {
        val serverUrl = response.optString("serverUrl").trim()
        val participantToken = response.optString("participantToken").trim()
        require(serverUrl.startsWith("ws://") || serverUrl.startsWith("wss://")) { "Alamat LiveKit tidak valid" }
        require(participantToken.isNotBlank()) { "Token LiveKit kosong" }
        val newRoom = LiveKit.create(applicationContext)
        newRoom.connect(
            url = serverUrl,
            token = participantToken,
            options = ConnectOptions(autoSubscribe = true, audio = false, video = false)
        )
        return newRoom
    }

    override fun onDestroy() {
        roomUiRefresh?.let { roomUiHandler.removeCallbacks(it) }
        roomUiRefresh = null
        // Saat Activity benar-benar ditutup, koneksi LiveKit dihentikan.
        room?.disconnect(); room = null
        super.onDestroy()
    }
}
