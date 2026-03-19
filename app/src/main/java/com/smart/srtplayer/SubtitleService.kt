package com.smart.srtplayer

import android.annotation.SuppressLint
import android.app.Service
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat

class SubtitleService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val seekHandler = Handler(Looper.getMainLooper())

    private var isPlaying = false // پوائنٹ 10: شروع میں پاؤز سٹیٹ
    private var isControlsVisible = true
    private var isBgHidden = false
    private var currentTimeMs: Long = 0
    private var timeOffset: Long = 0
    private var isSeeking = false

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupFloatingWindow()
        startForegroundServiceNotification()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingWindow() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0; y = 150
        }

        val subtitleContainer = floatingView!!.findViewById<View>(R.id.subtitle_container)
        val controlsLayout = floatingView!!.findViewById<View>(R.id.controls_layout)
        val playPauseBtn = floatingView!!.findViewById<ImageButton>(R.id.btn_play_pause)

        // --- پوائنٹ 8 & 9: ڈریگ اور ٹیپ ٹو ہائڈ ---
        subtitleContainer.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            private var isMoved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        isMoved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(floatingView, params)
                            isMoved = true
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMoved) { // پوائنٹ 9: ٹیپ پر بٹنز غائب
                            isControlsVisible = !isControlsVisible
                            controlsLayout.visibility = if (isControlsVisible) View.VISIBLE else View.GONE
                        }
                        return true
                    }
                }
                return false
            }
        })

        // --- پوائنٹ 1: پلے پاؤز ---
        playPauseBtn.setOnClickListener {
            isPlaying = !isPlaying
            playPauseBtn.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }

        // --- پوائنٹ 2: سمارٹ فارورڈ/بیک ورڈ (Variable Speed) ---
        setupSmartSeek(floatingView!!.findViewById(R.id.btn_forward), 1)
        setupSmartSeek(floatingView!!.findViewById(R.id.btn_backward), -1)

        // --- پوائنٹ 3: ٹائم آف سیٹ ---
        floatingView!!.findViewById<ImageButton>(R.id.btn_offset_plus).setOnClickListener { timeOffset += 500 }
        floatingView!!.findViewById<ImageButton>(R.id.btn_offset_minus).setOnClickListener { timeOffset -= 500 }

        // --- پوائنٹ 7: بیک گراؤنڈ ہائڈ ---
        floatingView!!.findViewById<ImageButton>(R.id.btn_hide_bg).setOnClickListener {
            isBgHidden = !isBgHidden
            subtitleContainer.setBackgroundColor(if (isBgHidden) Color.TRANSPARENT else Color.parseColor("#CC000000"))
        }

        windowManager.addView(floatingView, params)
        startTimerLoop()
    }

    private fun setupSmartSeek(btn: ImageButton, direction: Int) {
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSeeking = true
                    startSeekingTask(direction)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isSeeking = false
                    seekHandler.removeCallbacksAndMessages(null)
                    true
                }
                else -> false
            }
        }
    }

    private fun startSeekingTask(direction: Int) {
        val startTimePress = System.currentTimeMillis()
        val runnable = object : Runnable {
            override fun run() {
                if (!isSeeking) return
                val pressDuration = System.currentTimeMillis() - startTimePress
                val multiplier = when {
                    pressDuration > 4000 -> 5000L // 4 سیکنڈ بعد بہت تیز
                    pressDuration > 2000 -> 1000L // 2 سیکنڈ بعد درمیانی
                    else -> 300L // شروع میں آہستہ
                }
                currentTimeMs += (multiplier * direction)
                if (currentTimeMs < 0) currentTimeMs = 0
                updateTimerDisplay()
                seekHandler.postDelayed(this, 100)
            }
        }
        seekHandler.post(runnable)
    }

    private fun startTimerLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (isPlaying && !isSeeking) {
                    currentTimeMs += 100
                    updateTimerDisplay()
                }
                handler.postDelayed(this, 100)
            }
        })
    }

    private fun updateTimerDisplay() {
        val totalMs = currentTimeMs + timeOffset
        val seconds = (totalMs / 1000) % 60
        val minutes = (totalMs / (1000 * 60)) % 60
        val hours = (totalMs / (1000 * 60 * 60)) % 24
        floatingView?.findViewById<TextView>(R.id.timer_display)?.text = 
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun startForegroundServiceNotification() {
        val channelId = "srt_player_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "SRT Player", android.app.NotificationManager.IMPORTANCE_LOW)
            getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Smart SRT Player")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        floatingView?.let { windowManager.removeView(it) }
    }
}
