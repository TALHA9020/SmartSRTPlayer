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
        setContent { MaterialTheme { MainScreen() } }
    }
}

fun loadPreviewTypeface(context: Context, uri: Uri?): Typeface {
    if (uri == null) return Typeface.DEFAULT
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = File(context.cacheDir, "preview_font.ttf")
        val outputStream = FileOutputStream(tempFile)
        inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }
        Typeface.createFromFile(tempFile)
    } catch (e: Exception) { Typeface.DEFAULT }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("srt_prefs", Context.MODE_PRIVATE) }

    // سیٹنگز کو یاد رکھنے کے لیے Prefs سے ویلیوز اٹھائیں
    var subFontSize by remember { mutableStateOf(prefs.getFloat("font_size", 24f)) }
    var bgOpacity by remember { mutableStateOf(prefs.getFloat("bg_opacity", 0.7f)) }
    var textColor by remember { mutableStateOf(Color(prefs.getInt("text_color", Color.White.toArgb()))) }
    var bgColor by remember { mutableStateOf(Color(prefs.getInt("bg_color", Color.Black.toArgb()))) }
    
    var srtUri by remember { mutableStateOf<Uri?>(null) }
    var fontUri by remember { mutableStateOf<Uri?>(null) }

    var showTextColorPicker by remember { mutableStateOf(false) }
    var showBgColorPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        prefs.getString("srt_uri", null)?.let { srtUri = Uri.parse(it) }
        prefs.getString("font_uri", null)?.let { fontUri = Uri.parse(it) }
    }

    val customTypeface = remember(fontUri) { loadPreviewTypeface(context, fontUri) }

    val srtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            srtUri = it
            prefs.edit().putString("srt_uri", it.toString()).putLong("last_time", 0L).apply()
            context.startService(Intent(context, SubtitleService::class.java).apply { action = "ACTION_RESET_TIMER" })
        }
    }

    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            fontUri = it
            prefs.edit().putString("font_uri", it.toString()).apply()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Smart SRT Player", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)

        // پری ویو باکس (گول کنارے)
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp).background(Color.Gray.copy(0.1f), RoundedCornerShape(12.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.wrapContentSize().background(bgColor.copy(alpha = bgOpacity), RoundedCornerShape(15.dp)).padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("00:00:45", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("اردو فونٹ پری ویو", color = textColor, fontSize = subFontSize.sp, textAlign = TextAlign.Center, fontFamily = FontFamily(customTypeface))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { srtLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Set SRT") }
            Button(onClick = { fontLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) { Text("Set Font") }
        }

        Text("Font Size: ${subFontSize.toInt()}sp")
        Slider(value = subFontSize, valueRange = 16f..60f, onValueChange = { 
            subFontSize = it
            prefs.edit().putFloat("font_size", it).apply()
        })

        Text("Opacity: ${(bgOpacity * 100).toInt()}%")
        Slider(value = bgOpacity, valueRange = 0f..1f, onValueChange = { 
            bgOpacity = it
            prefs.edit().putFloat("bg_opacity", it).apply()
        })

        ColorRow("Text Color", textColor) { showTextColorPicker = true }
        ColorRow("BG Color", bgColor) { showBgColorPicker = true }

        Button(
            onClick = {
                if (!Settings.canDrawOverlays(context)) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                } else {
                    val intent = Intent(context, SubtitleService::class.java).apply {
                        putExtra("fontSize", subFontSize); putExtra("bgColor", bgColor.toArgb())
                        putExtra("textColor", textColor.toArgb()); putExtra("opacity", bgOpacity)
                        putExtra("srtUri", srtUri.toString()); fontUri?.let { putExtra("fontUri", it.toString()) }
                    }
                    context.startForegroundService(intent)
                }
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) { Text("LAUNCH PLAYER", fontWeight = FontWeight.Bold) }
    }

    if (showTextColorPicker) SimpleColorPicker({ textColor = it; prefs.edit().putInt("text_color", it.toArgb()).apply(); showTextColorPicker = false }, "Text Color")
    if (showBgColorPicker) SimpleColorPicker({ bgColor = it; prefs.edit().putInt("bg_color", it.toArgb()).apply(); showBgColorPicker = false }, "BG Color")
}

@Composable
fun ColorRow(label: String, color: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(color).border(2.dp, Color.LightGray, CircleShape))
    }
}

@Composable
fun SimpleColorPicker(onColorSelected: (Color) -> Unit, title: String) {
    val colors = listOf(Color.White, Color.Black, Color.Yellow, Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Magenta, Color.Gray)
    AlertDialog(
        onDismissRequest = { },
        title = { Text(title) },
        text = {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                colors.forEach { color ->
                    Box(Modifier.size(45.dp).padding(4.dp).clip(CircleShape).background(color).border(1.dp, Color.Black, CircleShape).clickable { onColorSelected(color) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onColorSelected(Color.Transparent) }) { Text("Close") } }
    )
}
