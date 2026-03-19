package com.smart.srtplayer

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

// پری ویو کے لیے فونٹ لوڈ کرنے کا فنکشن
fun loadPreviewTypeface(context: Context, uri: Uri?): Typeface {
    if (uri == null) return Typeface.DEFAULT
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = File(context.cacheDir, "preview_font.ttf")
        val outputStream = FileOutputStream(tempFile)
        inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }
        Typeface.createFromFile(tempFile)
    } catch (e: Exception) {
        Typeface.DEFAULT
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("srt_prefs", Context.MODE_PRIVATE)

    // سٹیٹس (States)
    var subFontSize by remember { mutableStateOf(24f) }
    var bgOpacity by remember { mutableStateOf(0.7f) }
    var textColor by remember { mutableStateOf(Color.White) }
    var bgColor by remember { mutableStateOf(Color.Black) }
    
    var srtUri by remember { mutableStateOf<Uri?>(null) }
    var fontUri by remember { mutableStateOf<Uri?>(null) }

    var showTextColorPicker by remember { mutableStateOf(false) }
    var showBgColorPicker by remember { mutableStateOf(false) }

    // ایپ کھلتے ہی پرانی سیٹنگز لوڈ کریں
    LaunchedEffect(Unit) {
        prefs.getString("srt_uri", null)?.let { srtUri = Uri.parse(it) }
        prefs.getString("font_uri", null)?.let { fontUri = Uri.parse(it) }
    }

    // لائیو فونٹ پری ویو کے لیے
    val customTypeface = remember(fontUri) { loadPreviewTypeface(context, fontUri) }

    // سروس کو ٹائمر ری سیٹ کرنے کی کمانڈ بھیجنا
    fun sendResetCommand() {
        val intent = Intent(context, SubtitleService::class.java).apply {
            action = "ACTION_RESET_TIMER"
        }
        context.startService(intent)
    }

    // SRT فائل سلیکٹر
    val srtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            srtUri = it
            prefs.edit().putString("srt_uri", it.toString()).putLong("last_time", 0L).apply()
            sendResetCommand() // نئی فائل پر ٹائمر زیرو کریں
            Toast.makeText(context, "New SRT Loaded & Timer Reset", Toast.LENGTH_SHORT).show()
        }
    }

    // فونٹ فائل سلیکٹر
    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            fontUri = it
            prefs.edit().putString("font_uri", it.toString()).apply()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Smart SRT Player",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF2E7D32),
            fontWeight = FontWeight.Bold
        )

        // --- لائیو پری ویو باکس ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Color.Gray.copy(0.1f), RoundedCornerShape(12.dp))
                .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .wrapContentSize()
                    .background(bgColor.copy(alpha = bgOpacity), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "00:00:45", 
                    color = textColor, 
                    fontSize = 14.sp, 
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "اردو سبٹائٹل کا نمونہ",
                    color = textColor,
                    fontSize = subFontSize.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily(customTypeface)
                )
            }
        }

        // --- فائل سلیکشن بٹنز ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { srtLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                Text(if (srtUri == null) "Select SRT" else "Change SRT")
            }
            Button(onClick = { fontLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                Text(if (fontUri == null) "Select Font" else "Change Font")
            }
        }

        // --- ٹائمر ری سیٹ بٹن ---
        OutlinedButton(
            onClick = { 
                prefs.edit().putLong("last_time", 0L).apply()
                sendResetCommand()
                Toast.makeText(context, "Timer Reset to Zero", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Timer to 00:00:00")
        }

        // --- سیٹنگز کنٹرولز ---
        Text("Font Size: ${subFontSize.toInt()}sp", fontWeight = FontWeight.Medium)
        Slider(value = subFontSize, valueRange = 16f..60f, onValueChange = { subFontSize = it })

        Text("Background Opacity: ${(bgOpacity * 100).toInt()}%", fontWeight = FontWeight.Medium)
        Slider(value = bgOpacity, valueRange = 0f..1f, onValueChange = { bgOpacity = it })

        // کلر روز (Color Rows)
        ColorRow("Text Color", textColor) { showTextColorPicker = true }
        ColorRow("Background Color", bgColor) { showBgColorPicker = true }

        Spacer(Modifier.height(10.dp))

        // --- اسٹارٹ پلیئر بٹن ---
        Button(
            onClick = {
                if (!Settings.canDrawOverlays(context)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                } else if (srtUri == null) {
                    Toast.makeText(context, "Please select an SRT file first", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(context, SubtitleService::class.java).apply {
                        putExtra("fontSize", subFontSize)
                        putExtra("bgColor", bgColor.toArgb())
                        putExtra("textColor", textColor.toArgb())
                        putExtra("opacity", bgOpacity)
                        putExtra("srtUri", srtUri.toString())
                        fontUri?.let { putExtra("fontUri", it.toString()) }
                    }
                    context.startForegroundService(intent)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
            Text("LAUNCH FLOATING PLAYER", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }

    // کلر پیکر ڈائیلاگز
    if (showTextColorPicker) {
        SimpleColorPicker({ textColor = it; showTextColorPicker = false }, "Text Color")
    }
    if (showBgColorPicker) {
        SimpleColorPicker({ bgColor = it; showBgColorPicker = false }, "Background Color")
    }
}

@Composable
fun ColorRow(label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .border(2.dp, Color.LightGray, CircleShape)
        )
    }
}

@Composable
fun SimpleColorPicker(onColorSelected: (Color) -> Unit, title: String) {
    val colors = listOf(
        Color.White, Color.Black, Color.Yellow, Color.Red, 
        Color.Green, Color.Blue, Color.Cyan, Color.Magenta,
        Color(0xFFFF9800), Color(0xFF795548), Color(0xFF9C27B0), Color.Gray
    )
    AlertDialog(
        onDismissRequest = { },
        title = { Text(title) },
        text = {
            Column {
                repeat(3) { rowIndex ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(4) { colIndex ->
                            val color = colors[rowIndex * 4 + colIndex]
                            Box(
                                Modifier
                                    .size(45.dp)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(1.dp, Color.Black, CircleShape)
                                    .clickable { onColorSelected(color) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(Color.Transparent) }) { Text("Close") }
        }
    )
}
