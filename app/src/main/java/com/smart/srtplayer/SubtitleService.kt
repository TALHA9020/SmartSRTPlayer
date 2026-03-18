package com.smart.srtplayer // نیا پیکج نام

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import java.io.File

class SubtitleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    private lateinit var params: WindowManager.LayoutParams
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var currentIndex = mutableIntStateOf(0)
    private var isPlaying = mutableStateOf(false)
    private var currentTimeMs = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            if (isPlaying.value && MainActivity.fullSubtitleList.isNotEmpty()) {
                currentTimeMs += 100
                val list = MainActivity.fullSubtitleList
                
                val foundIndex = list.indexOfFirst { currentTimeMs >= it.start && currentTimeMs <= it.end }
                if (foundIndex != -1 && foundIndex != currentIndex.intValue) {
                    currentIndex.intValue = foundIndex
                }
                handler.postDelayed(this, 100)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startMyForeground()
    }

    private fun startMyForeground() {
        val chan = NotificationChannel("sub_chan", "Subtitles", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
        startForeground(1, NotificationCompat.Builder(this, "sub_chan")
            .setContentTitle("Smart SRT Player Active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fontPath = intent?.getStringExtra("font_path")
        showFloatingUI(fontPath)
        return START_STICKY
    }

    private fun showFloatingUI(fontPath: String?) {
        if (floatingView != null) return
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM; y = 150 }

        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SubtitleService)
            setViewTreeSavedStateRegistryOwner(this@SubtitleService)
            setContent {
                val font = if (fontPath != null) FontFamily(Font(File(fontPath))) else FontFamily.Default
                val idx by currentIndex
                val active by isPlaying
                val currentText = if (MainActivity.fullSubtitleList.isNotEmpty()) MainActivity.fullSubtitleList[idx].text else "انتظار کریں..."

                Column(modifier = Modifier.fillMaxWidth().padding(10.dp).background(Color.Black.copy(0.85f), RoundedCornerShape(20.dp)).padding(15.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    
                    Text(text = currentText, color = Color.White, fontSize = 22.sp, fontFamily = font, textAlign = TextAlign.Center)
                    
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { if(currentIndex.intValue > 0) { 
                            currentIndex.intValue-- 
                            currentTimeMs = MainActivity.fullSubtitleList[currentIndex.intValue].start
                        }}) { Icon(Icons.Default.SkipPrevious, "", tint = Color.White) }

                        FloatingActionButton(onClick = { 
                            isPlaying.value = !isPlaying.value
                            if(isPlaying.value) handler.post(ticker) else handler.removeCallbacks(ticker)
                        }, containerColor = Color(0xFFFFD700), shape = CircleShape, modifier = Modifier.size(50.dp)) {
                            Icon(if(active) Icons.Default.Pause else Icons.Default.PlayArrow, "", tint = Color.Black)
                        }

                        IconButton(onClick = { if(currentIndex.intValue < MainActivity.fullSubtitleList.size - 1) {
                            currentIndex.intValue++
                            currentTimeMs = MainActivity.fullSubtitleList[currentIndex.intValue].start
                        }}) { Icon(Icons.Default.SkipNext, "", tint = Color.White) }
                        
                        Spacer(modifier = Modifier.width(10.dp))
                        
                        IconButton(onClick = { stopSelf() }) { Icon(Icons.Default.Close, "", tint = Color.Red) }
                    }
                }
            }
        }

        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialY = 0; private var initialTouchY = 0f
            override fun onTouch(v: View?, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { initialY = params.y; initialTouchY = e.rawY; return true }
                    MotionEvent.ACTION_MOVE -> { 
                        params.y = initialY - (e.rawY - initialTouchY).toInt()
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
        handler.removeCallbacks(ticker)
        floatingView?.let { 
            if (it.isAttachedToWindow) windowManager.removeView(it) 
        }
        super.onDestroy()
    }

    override fun onBind(i: Intent?) = null
}