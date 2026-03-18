package com.smart.whiteboard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.io.File

class SubtitleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startMyForeground()
    }

    private fun startMyForeground() {
        val channelId = "subtitle_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Subtitle Player", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Subtitle Player Active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(2, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra("subtitle_text") ?: "سب ٹائٹل یہاں نظر آئیں گے"
        val fontPath = intent?.getStringExtra("font_path")
        showFloatingSubtitle(text, fontPath)
        return START_STICKY
    }

    private fun showFloatingSubtitle(textToDisplay: String, fontPath: String?) {
        if (floatingView != null) return // اگر پہلے سے موجود ہے تو دوبارہ نہ بنائیں

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 100 // سکرین کے نیچے سے فاصلہ
        }

        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SubtitleService)
            setViewTreeSavedStateRegistryOwner(this@SubtitleService)

            setContent {
                val customFont = if (fontPath != null) FontFamily(Font(File(fontPath))) else FontFamily.Default
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(15.dp))
                        .padding(15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = textToDisplay,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = customFont,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ڈریگنگ (Dragging) لاجک
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialY: Int = 0
            private var initialTouchY: Float = 0f
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = params.y
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.y = initialY - (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(floatingView, params)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        floatingView?.let { windowManager.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
