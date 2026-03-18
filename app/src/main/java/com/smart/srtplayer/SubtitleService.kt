package com.smart.srtplayer

import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.KeyboardType
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
    
    private var baseTimeMs = mutableLongStateOf(0L)
    private var timeOffsetMs = mutableLongStateOf(0L)

    private val handler = Handler(Looper.getMainLooper())
    private var isBgVisible = mutableStateOf(true)
    private var isControlsFolded = mutableStateOf(false)
    private var showJumpDialog = mutableStateOf(false)

    private val ticker = object : Runnable {
        override fun run() {
            if (isPlaying.value) {
                baseTimeMs.longValue += 100
                updateIndexByTime(baseTimeMs.longValue + timeOffsetMs.longValue)
                saveProgress()
                handler.postDelayed(this, 100)
            }
        }
    }

    private fun updateIndexByTime(totalTime: Long) {
        val list = MainActivity.fullSubtitleList
        val foundIndex = list.indexOfFirst { totalTime >= it.start && totalTime <= it.end }
        if (foundIndex != -1) {
            currentIndex.intValue = foundIndex
        } else {
            val nextIdx = list.indexOfFirst { it.start > totalTime }
            if (nextIdx != -1) currentIndex.intValue = nextIdx
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        currentIndex.intValue = prefs.getInt("last_index", 0)
        baseTimeMs.longValue = prefs.getLong("last_time_ms", 0L)
        timeOffsetMs.longValue = prefs.getLong("last_offset_ms", 0L)
        
        startMyForeground()
        showFloatingUI()
    }

    private fun saveProgress() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("last_index", currentIndex.intValue)
            .putLong("last_time_ms", baseTimeMs.longValue)
            .putLong("last_offset_ms", timeOffsetMs.longValue)
            .apply()
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = Math.max(0, ms / 1000)
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return "%02d:%02d".format(mins, secs)
    }

    private fun startMyForeground() {
        val chan = NotificationChannel("sub_chan", "Subtitles", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
        startForeground(1, NotificationCompat.Builder(this, "sub_chan")
            .setContentTitle("Smart SRT Player Active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build())
    }

    private fun showFloatingUI() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val fontPath = prefs.getString("last_font_path", null)
        val tSize = prefs.getFloat("text_size", 1.0f)
        val clockSize = prefs.getFloat("timer_size", 0.8f)
        val textCol = Color(prefs.getInt("text_color", Color.White.toArgb()))
        val bgCol = Color(prefs.getInt("bg_color", Color.Black.toArgb()))
        val opac = prefs.getFloat("opacity", 0.8f)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 400 
        }

        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SubtitleService)
            setViewTreeSavedStateRegistryOwner(this@SubtitleService)
            setContent {
                val font = if (fontPath != null) FontFamily(Font(File(fontPath))) else FontFamily.Default
                val idx by currentIndex
                val active by isPlaying
                val offset by timeOffsetMs
                val baseTime by baseTimeMs
                val controlsFolded by isControlsFolded
                
                val displayTime = formatTime(baseTime + offset)
                
                val currentText = if (MainActivity.fullSubtitleList.isNotEmpty() && idx < MainActivity.fullSubtitleList.size) 
                    MainActivity.fullSubtitleList[idx].text else "..."

                val finalBgColor = if (isBgVisible.value) bgCol.copy(alpha = opac) else Color.Transparent

                Box(modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            params.x += dragAmount.x.toInt()
                            params.y += dragAmount.y.toInt()
                            windowManager.updateViewLayout(floatingView, params)
                        }
                    }
                    .background(finalBgColor, RoundedCornerShape(12.dp))
                    .padding(8.dp).widthIn(min = 250.dp, max = 500.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        
                        // ٹائمر صرف تب نظر آئے گا جب کنٹرولز کھلے (Unfolded) ہوں
                        if (!controlsFolded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = displayTime, 
                                    color = textCol.copy(alpha = 0.8f), 
                                    fontSize = (18 * clockSize).sp, 
                                    fontFamily = font,
                                    modifier = Modifier.clickable { showJumpDialog.value = true }
                                )
                                if (offset != 0L) {
                                    Text(
                                        text = " (${if(offset>0) "+" else ""}${offset/1000}s)",
                                        color = Color.Yellow.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        }

                        // سب ٹائٹل ٹیکسٹ (یہ ہمیشہ نظر آئے گا)
                        Text(
                            text = currentText, 
                            color = textCol, fontSize = (22 * tSize).sp, fontFamily = font,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clickable { isControlsFolded.value = !isControlsFolded.value }
                                .padding(vertical = 4.dp).fillMaxWidth()
                        )
                        
                        // اضافی کنٹرولز (صرف Unfolded موڈ میں)
                        if (!controlsFolded) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { timeOffsetMs.longValue -= 1000; updateIndexByTime(baseTimeMs.longValue + timeOffsetMs.longValue) }) {
                                    Text("-1s", color = textCol)
                                }
                                
                                IconButton(onClick = { if(currentIndex.intValue > 0) { 
                                    currentIndex.intValue-- 
                                    baseTimeMs.longValue = MainActivity.fullSubtitleList[currentIndex.intValue].start - timeOffsetMs.longValue
                                }}) { Icon(Icons.Default.SkipPrevious, "", tint = textCol) }

                                FloatingActionButton(
                                    onClick = { 
                                        isPlaying.value = !isPlaying.value
                                        if(isPlaying.value) handler.post(ticker) else handler.removeCallbacks(ticker)
                                    }, 
                                    containerColor = Color(0xFFFFD700), shape = CircleShape, modifier = Modifier.size(40.dp)
                                ) { Icon(if(active) Icons.Default.Pause else Icons.Default.PlayArrow, "", tint = Color.Black) }

                                IconButton(onClick = { if(currentIndex.intValue < MainActivity.fullSubtitleList.size - 1) {
                                    currentIndex.intValue++
                                    baseTimeMs.longValue = MainActivity.fullSubtitleList[currentIndex.intValue].start - timeOffsetMs.longValue
                                }}) { Icon(Icons.Default.SkipNext, "", tint = textCol) }

                                TextButton(onClick = { timeOffsetMs.longValue += 1000; updateIndexByTime(baseTimeMs.longValue + timeOffsetMs.longValue) }) {
                                    Text("+1s", color = textCol)
                                }
                            }

                            Row {
                                if (offset != 0L) {
                                    IconButton(onClick = { timeOffsetMs.longValue = 0; updateIndexByTime(baseTimeMs.longValue) }) {
                                        Icon(Icons.Default.Sync, "Reset Sync", tint = Color.Cyan, modifier = Modifier.size(20.dp))
                                    }
                                }
                                IconButton(onClick = { isBgVisible.value = !isBgVisible.value }) { 
                                    Icon(if(isBgVisible.value) Icons.Default.Visibility else Icons.Default.VisibilityOff, "", tint = textCol, modifier = Modifier.size(20.dp)) 
                                }
                                IconButton(onClick = { stopSelf() }) { Icon(Icons.Default.Close, "", tint = Color.Red, modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }

                    if (showJumpDialog.value) {
                        JumpToTimeDialog(
                            onDismiss = { showJumpDialog.value = false },
                            onConfirm = { inputSec ->
                                baseTimeMs.longValue = (inputSec * 1000) - timeOffsetMs.longValue
                                updateIndexByTime(inputSec * 1000)
                                showJumpDialog.value = false
                            }
                        )
                    }
                }
            }
        }
        windowManager.addView(floatingView, params)
    }

    @Composable
    fun JumpToTimeDialog(onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Jump to (Seconds)") },
            text = {
                TextField(
                    value = input, onValueChange = { input = it },
                    placeholder = { Text("مثال: 120") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = { 
                    val sec = input.toLongOrNull() ?: 0L
                    onConfirm(sec)
                }) { Text("OK") }
            }
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        saveProgress()
        floatingView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        super.onDestroy()
    }
    override fun onBind(i: Intent?) = null
}
