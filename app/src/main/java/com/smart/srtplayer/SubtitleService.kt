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
    private var isSeeking = false
    private var subtitleList = mutableListOf<SubtitleItem>()

    // Settings
    private var userBgColor: Int = Color.BLACK
    private var userOpacity: Float = 0.7f

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val srtUri = intent?.getStringExtra("srtUri")
        val fontUri = intent?.getStringExtra("fontUri")
        
        if (srtUri != null) {
            parseSrt(Uri.parse(srtUri))
        }

        setupFloatingWindow(intent)
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
    }

    private fun parseSrt(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            var startTime: Long = 0
            var endTime: Long = 0
            var text = ""

            while (reader.readLine().also { line = it } != null) {
                if (line!!.contains(" --> ")) {
                    val parts = line!!.split(" --> ")
                    startTime = timeToMs(parts[0])
                    endTime = timeToMs(parts[1])
                    text = ""
                    while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                        text += line + "\n"
                    }
                    subtitleList.add(SubtitleItem(startTime, endTime, text.trim()))
                }
            }
            reader.close()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun timeToMs(timeStr: String): Long {
        val parts = timeStr.replace(",", ".").split(":")
        val hours = parts[0].trim().toLong()
        val minutes = parts[1].trim().toLong()
        val secondsParts = parts[2].trim().split(".")
        val seconds = secondsParts[0].toLong()
        val ms = secondsParts[1].toLong()
        return (hours * 3600000) + (minutes * 60000) + (seconds * 1000) + ms
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingWindow(intent: Intent?) {
        if (floatingView != null) windowManager.removeView(floatingView)
        
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        
        val subText = floatingView!!.findViewById<TextView>(R.id.subtitle_text)
        val timerDisplay = floatingView!!.findViewById<TextView>(R.id.timer_display)
        val container = floatingView!!.findViewById<LinearLayout>(R.id.subtitle_container)
        val controls = floatingView!!.findViewById<LinearLayout>(R.id.controls_layout)

        // Apply User Settings
        intent?.let {
            subText.textSize = it.getFloatExtra("fontSize", 24f)
            timerDisplay.textSize = it.getFloatExtra("timerSize", 16f)
            subText.setTextColor(it.getIntExtra("textColor", Color.WHITE))
            timerDisplay.setTextColor(it.getIntExtra("textColor", Color.WHITE))
            userBgColor = it.getIntExtra("bgColor", Color.BLACK)
            userOpacity = it.getFloatExtra("opacity", 0.7f)
            
            val alpha = (userOpacity * 255).toInt()
            val finalColor = Color.argb(alpha, Color.red(userBgColor), Color.green(userBgColor), Color.blue(userBgColor))
            container.setBackgroundColor(finalColor)

            it.getStringExtra("fontUri")?.let { uriStr ->
                try {
                    val pfd = contentResolver.openFileDescriptor(Uri.parse(uriStr), "r")
                    subText.typeface = Typeface.createFromFile("/proc/self/fd/${pfd?.fd}")
                } catch (e: Exception) {}
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP; y = 200 }

        // Point 8 & 9: Drag and Toggle
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

        // Point 1: Play/Pause
        val playBtn = floatingView!!.findViewById<ImageButton>(R.id.btn_play_pause)
        playBtn.setOnClickListener {
            isPlaying = !isPlaying
            playBtn.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }

        // Point 2: Smart Seek
        setupSmartSeek(floatingView!!.findViewById(R.id.btn_forward), 1)
        setupSmartSeek(floatingView!!.findViewById(R.id.btn_backward), -1)

        // Point 3: Offset
        floatingView!!.findViewById<View>(R.id.btn_offset_plus).setOnClickListener { timeOffset += 500 }
        floatingView!!.findViewById<View>(R.id.btn_offset_minus).setOnClickListener { timeOffset -= 500 }

        // Point 7: Background Hide
        floatingView!!.findViewById<View>(R.id.btn_hide_bg).setOnClickListener {
            isBgHidden = !isBgHidden
            container.setBackgroundColor(if (isBgHidden) Color.TRANSPARENT else Color.argb((userOpacity * 255).toInt(), Color.red(userBgColor), Color.green(userBgColor), Color.blue(userBgColor)))
        }

        windowManager.addView(floatingView, params)
        startMainLoop()
    }

    private fun setupSmartSeek(btn: ImageButton, dir: Int) {
        btn.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) { isSeeking = true; startSeeking(dir) }
            if (e.action == MotionEvent.ACTION_UP) isSeeking = false
            true
        }
    }

    private fun startSeeking(dir: Int) {
        val start = System.currentTimeMillis()
        seekHandler.post(object : Runnable {
            override fun run() {
                if (!isSeeking) return
                val diff = System.currentTimeMillis() - start
                val step = if (diff > 2000) 2000L else 300L
                currentTimeMs += (step * dir)
                if (currentTimeMs < 0) currentTimeMs = 0
                updateDisplay()
                seekHandler.postDelayed(this, 100)
            }
        })
    }

    private fun startMainLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (isPlaying && !isSeeking) {
                    currentTimeMs += 100
                    updateDisplay()
                }
                handler.postDelayed(this, 100)
            }
        })
    }

    private fun updateDisplay() {
        val totalTime = currentTimeMs + timeOffset
        val hours = (totalTime / 3600000) % 24
        val mins = (totalTime / 60000) % 60
        val secs = (totalTime / 1000) % 60
        floatingView?.findViewById<TextView>(R.id.timer_display)?.text = String.format("%02d:%02d:%02d", hours, mins, secs)

        val currentSub = subtitleList.firstOrNull { totalTime in it.startTime..it.endTime }
        floatingView?.findViewById<TextView>(R.id.subtitle_text)?.text = currentSub?.text ?: ""
    }

    private fun startForegroundServiceNotification() {
        val chan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel("srt_player", "SRT Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
            "srt_player"
        } else ""
        val notif = NotificationCompat.Builder(this, chan).setContentTitle("Subtitle Active").setSmallIcon(android.R.drawable.ic_media_play).build()
        startForeground(1, notif)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        floatingView?.let { windowManager.removeView(it) }
    }
}
