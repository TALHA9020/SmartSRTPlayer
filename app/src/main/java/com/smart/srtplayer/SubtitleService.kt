package com.smart.srtplayer

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

class SubtitleService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val seekHandler = Handler(Looper.getMainLooper())

    private var isPlaying = false
    private var isControlsVisible = true
    private var isBgHidden = false
    private var currentTimeMs: Long = 0
    private var timeOffset: Long = 0
    private var subtitleList = mutableListOf<SubtitleItem>()

    private var userBgColor: Int = Color.BLACK
    private var userOpacity: Float = 0.7f

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(1, NotificationCompat.Builder(this, "srt_service")
            .setContentTitle("SRT Player is Running")
            .setSmallIcon(android.R.drawable.ic_media_play).build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("srt_prefs", MODE_PRIVATE)
        val srtUriStr = intent?.getStringExtra("srtUri") ?: prefs.getString("srt_uri", null)
        val fontUriStr = intent?.getStringExtra("fontUri") ?: prefs.getString("font_uri", null)
        
        currentTimeMs = prefs.getLong("last_time", 0L)

        srtUriStr?.let { parseSrt(Uri.parse(it)) }
        setupFloatingWindow(intent, fontUriStr)
        startAutoSaveTask()
        return START_NOT_STICKY
    }

    // TTF فونٹ لوڈ کرنے کا فنکشن
    private fun loadCustomFont(uriStr: String?): Typeface? {
        if (uriStr == null) return null
        return try {
            val uri = Uri.parse(uriStr)
            val inputStream = contentResolver.openInputStream(uri)
            val tempFile = File(cacheDir, "temp_font.ttf")
            val outputStream = FileOutputStream(tempFile)
            inputStream?.use { input ->
                outputStream.use { output -> input.copyTo(output) }
            }
            Typeface.createFromFile(tempFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseSrt(uri: Uri) {
        subtitleList.clear()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.contains(" --> ")) {
                        val parts = line!!.split(" --> ")
                        val start = srtTimeToMs(parts[0])
                        val end = srtTimeToMs(parts[1])
                        val text = StringBuilder()
                        while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                            text.append(line).append("\n")
                        }
                        subtitleList.add(SubtitleItem(start, end, text.toString().trim()))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun srtTimeToMs(time: String): Long {
        val parts = time.replace(",", ".").split(":")
        val hrs = parts[0].trim().toLong() * 3600000
        val mins = parts[1].trim().toLong() * 60000
        val secs = (parts[2].trim().toDouble() * 1000).toLong()
        return hrs + mins + secs
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingWindow(intent: Intent?, fontUriStr: String?) {
        if (floatingView != null) windowManager.removeView(floatingView)
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        val subText = floatingView!!.findViewById<TextView>(R.id.subtitle_text)
        val container = floatingView!!.findViewById<LinearLayout>(R.id.subtitle_container)
        val controls = floatingView!!.findViewById<LinearLayout>(R.id.controls_layout)
        
        val playBtn = floatingView!!.findViewById<ImageButton>(R.id.btn_play_pause)
        val forwardBtn = floatingView!!.findViewById<ImageButton>(R.id.btn_forward)
        val backwardBtn = floatingView!!.findViewById<ImageButton>(R.id.btn_backward)
        val closeBtn = floatingView!!.findViewById<ImageButton>(R.id.btn_close_service)
        val offsetPlus = floatingView!!.findViewById<ImageButton>(R.id.btn_offset_plus)
        val offsetMinus = floatingView!!.findViewById<ImageButton>(R.id.btn_offset_minus)
        val hideBgBtn = floatingView!!.findViewById<ImageButton>(R.id.btn_hide_bg)

        // فونٹ اپلائی کریں
        loadCustomFont(fontUriStr)?.let { subText.typeface = it }

        intent?.let {
            subText.textSize = it.getFloatExtra("fontSize", 24f)
            subText.setTextColor(it.getIntExtra("textColor", Color.WHITE))
            userBgColor = it.getIntExtra("bgColor", Color.BLACK)
            userOpacity = it.getFloatExtra("opacity", 0.7f)
            updateBackground(container)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP; y = 300 }

        // ڈریگ اور کنٹرولز ٹوگل
        container.setOnTouchListener(object : View.OnTouchListener {
            private var x = 0; private var y = 0; private var px = 0f; private var py = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { x = params.x; y = params.y; px = e.rawX; py = e.rawY; return true }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = x + (e.rawX - px).toInt(); params.y = y + (e.rawY - py).toInt()
                        windowManager.updateViewLayout(floatingView, params); return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (Math.abs(e.rawX - px) < 10) {
                            isControlsVisible = !isControlsVisible
                            controls.visibility = if (isControlsVisible) View.VISIBLE else View.GONE
                        }
                        return true
                    }
                }
                return false
            }
        })

        // لونگ پریس لاجک (تیز رفتاری سے وقت بدلنے کے لیے)
        fun createSeekRunnable(isForward: Boolean): Runnable = object : Runnable {
            override fun run() {
                if (isForward) currentTimeMs += 1000 else currentTimeMs = maxOf(0, currentTimeMs - 1000)
                updateUI()
                seekHandler.postDelayed(this, 100) // ہر 100 ملی سیکنڈ بعد سپیڈ بڑھے گی
            }
        }

        val forwardRunnable = createSeekRunnable(true)
        val backwardRunnable = createSeekRunnable(false)

        forwardBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> seekHandler.post(forwardRunnable)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> seekHandler.removeCallbacks(forwardRunnable)
            }
            true
        }

        backwardBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> seekHandler.post(backwardRunnable)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> seekHandler.removeCallbacks(backwardRunnable)
            }
            true
        }

        playBtn.setOnClickListener {
            isPlaying = !isPlaying
            playBtn.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }

        offsetPlus.setOnClickListener { timeOffset += 500; updateUI() }
        offsetMinus.setOnClickListener { timeOffset -= 500; updateUI() }
        hideBgBtn.setOnClickListener { isBgHidden = !isBgHidden; updateBackground(container) }
        closeBtn.setOnClickListener { stopSelf() }

        windowManager.addView(floatingView, params)
        startMainLoop()
    }

    private fun startMainLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (isPlaying) {
                    currentTimeMs += 100
                    updateUI()
                }
                handler.postDelayed(this, 100)
            }
        })
    }

    private fun updateUI() {
        val displayTime = currentTimeMs
        val subSyncTime = currentTimeMs + timeOffset

        val s = (displayTime / 1000) % 60
        val m = (displayTime / 60000) % 60
        val h = (displayTime / 3600000) % 24
        floatingView?.findViewById<TextView>(R.id.timer_display)?.text = String.format("%02d:%02d:%02d", h, m, s)
        
        val currentSub = subtitleList.find { subSyncTime in it.startTime..it.endTime }
        floatingView?.findViewById<TextView>(R.id.subtitle_text)?.text = currentSub?.text ?: ""
    }

    private fun updateBackground(view: View) {
        if (isBgHidden) view.setBackgroundColor(Color.TRANSPARENT)
        else {
            val alpha = (userOpacity * 255).toInt()
            view.setBackgroundColor(Color.argb(alpha, Color.red(userBgColor), Color.green(userBgColor), Color.blue(userBgColor)))
        }
    }

    private fun startAutoSaveTask() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                getSharedPreferences("srt_prefs", MODE_PRIVATE).edit()
                    .putLong("last_time", currentTimeMs).apply()
                handler.postDelayed(this, 5000)
            }
        }, 5000)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("srt_service", "SRT Player", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        getSharedPreferences("srt_prefs", MODE_PRIVATE).edit().putLong("last_time", currentTimeMs).apply()
        handler.removeCallbacksAndMessages(null)
        seekHandler.removeCallbacksAndMessages(null)
        floatingView?.let { windowManager.removeView(it) }
        super.onDestroy()
    }
}
