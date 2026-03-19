package com.smart.srtplayer

import android.annotation.SuppressLint
import android.app.Service
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat

class SubtitleService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    
    private var isPlaying = false // پوائنٹ 10: شروع میں پاؤز سٹیٹ
    private var isControlsVisible = true
    private var isBgHidden = false
    private var currentTimeMs: Long = 0
    private var timeOffset: Long = 0
    
    private val handler = Handler(Looper.getMainLooper())
    private val seekHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupFloatingWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFloatingWindow() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0; y = 100
        }

        // --- پوائنٹ 8: ڈریگ ایبل لاجک ---
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // پوائنٹ 9: ٹیکسٹ پر ٹیپ کرنے سے بٹنز غائب/ظاہر
                        if (Math.abs(event.rawX - initialTouchX) < 10) {
                            isControlsVisible = !isControlsVisible
                            floatingView?.findViewById<View>(R.id.controls_layout)?.visibility = 
                                if (isControlsVisible) View.VISIBLE else View.GONE
                        }
                        return true
                    }
                }
                return false
            }
        })

        // --- پوائنٹ 2: سمارٹ فارورڈ/بیک ورڈ (Long Press) ---
        setupSmartSeek(floatingView!!.findViewById(R.id.btn_forward), 1)
        setupSmartSeek(floatingView!!.findViewById(R.id.btn_backward), -1)

        // --- پوائنٹ 7: بیک گراؤنڈ ہائڈ بٹن ---
        floatingView?.findViewById<ImageButton>(R.id.btn_hide_bg)?.setOnClickListener {
            isBgHidden = !isBgHidden
            floatingView?.findViewById<View>(R.id.subtitle_container)?.setBackgroundColor(
                if (isBgHidden) 0x00000000 else 0x80000000
            )
        }

        // --- پوائنٹ 1: پلے پاؤز بٹن ---
        floatingView?.findViewById<ImageButton>(R.id.btn_play_pause)?.setOnClickListener {
            isPlaying = !isPlaying
            (it as ImageButton).setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
        }

        windowManager.addView(floatingView, params)
    }

    private fun setupSmartSeek(btn: ImageButton, direction: Int) {
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startSeeking(direction)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    stopSeeking()
                    true
                }
                else -> false
            }
        }
    }

    private fun startSeeking(direction: Int) {
        var speedMultiplier = 1
        val seekRunnable = object : Runnable {
            override fun run() {
                currentTimeMs += (500 * direction * speedMultiplier) // ہر سٹیپ پر وقت بدلے گا
                if (speedMultiplier < 10) speedMultiplier++ // جتنا لمبا پریس، اتنی سپیڈ (پوائنٹ 2)
                updateUI()
                seekHandler.postDelayed(this, 100)
            }
        }
        seekHandler.post(seekRunnable)
    }

    private fun stopSeeking() {
        seekHandler.removeCallbacksAndMessages(null)
    }

    private fun updateUI() {
        // ٹائمر اور سبٹائٹل اپ ڈیٹ کرنے کی لاجک یہاں آئے گی
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager.removeView(it) }
    }
}
