package com.smart.srtplayer

import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MainScreen() } }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var subFontSize by remember { mutableStateOf(24f) }
    var timerSize by remember { mutableStateOf(16f) }
    var bgOpacity by remember { mutableStateOf(0.7f) }
    var textColor by remember { mutableStateOf(Color.White) }
    var bgColor by remember { mutableStateOf(Color.Black) }
    var srtUri by remember { mutableStateOf<Uri?>(null) }
    var fontUri by remember { mutableStateOf<Uri?>(null) }

    var showTextColorPicker by remember { mutableStateOf(false) }
    var showBgColorPicker by remember { mutableStateOf(false) }

    val srtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { srtUri = it }
    val fontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { fontUri = it }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Smart SRT Player Settings", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)

        // Live Preview
        Text("Preview:", fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier.fillMaxWidth().height(100.dp).background(Color.Gray.copy(0.1f), RoundedCornerShape(8.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.wrapContentSize().background(bgColor.copy(alpha = bgOpacity), RoundedCornerShape(4.dp)).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("00:01:24", color = textColor, fontSize = timerSize.sp, fontWeight = FontWeight.Bold)
                Text("اردو سبٹائٹل پری ویو", color = textColor, fontSize = subFontSize.sp, textAlign = TextAlign.Center)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { srtLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                Text(if (srtUri == null) "Add SRT" else "SRT Added ✅")
            }
            Button(onClick = { fontLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                Text(if (fontUri == null) "Add Font" else "Font Added ✅")
            }
        }

        Text("Font Size: ${subFontSize.toInt()}")
        Slider(value = subFontSize, valueRange = 12f..60f, onValueChange = { subFontSize = it })

        Text("Timer Size: ${timerSize.toInt()}")
        Slider(value = timerSize, valueRange = 10f..30f, onValueChange = { timerSize = it })

        Text("Background Opacity: ${(bgOpacity * 100).toInt()}%")
        Slider(value = bgOpacity, valueRange = 0f..1f, onValueChange = { bgOpacity = it })

        ColorSelectionRow("Text Color", textColor) { showTextColorPicker = true }
        ColorSelectionRow("BG Color", bgColor) { showBgColorPicker = true }

        Spacer(modifier = Modifier.height(20.dp))

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
                        putExtra("timerSize", timerSize)
                        putExtra("bgColor", bgColor.toArgb())
                        putExtra("textColor", textColor.toArgb())
                        putExtra("opacity", bgOpacity)
                        putExtra("srtUri", srtUri.toString())
                        fontUri?.let { putExtra("fontUri", it.toString()) }
                    }
                    context.startForegroundService(intent)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
        ) {
            Text("START SUBTITLE PLAYER", fontWeight = FontWeight.Bold)
        }
    }

    if (showTextColorPicker) ColorPickerDialog({ textColor = it; showTextColorPicker = false }, "Select Text Color")
    if (showBgColorPicker) ColorPickerDialog({ bgColor = it; showBgColorPicker = false }, "Select Background Color")
}

@Composable
fun ColorSelectionRow(label: String, color: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Text(label, modifier = Modifier.weight(1f))
        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(color).border(1.dp, Color.Gray, CircleShape))
    }
}

@Composable
fun ColorPickerDialog(onColorSelected: (Color) -> Unit, title: String) {
    val colors = listOf(Color.White, Color.Black, Color.Yellow, Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Magenta)
    AlertDialog(
        onDismissRequest = { },
        title = { Text(title) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    colors.take(4).forEach { color ->
                        Box(Modifier.size(40.dp).clip(CircleShape).background(color).border(1.dp, Color.LightGray, CircleShape).clickable { onColorSelected(color) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    colors.drop(4).forEach { color ->
                        Box(Modifier.size(40.dp).clip(CircleShape).background(color).border(1.dp, Color.LightGray, CircleShape).clickable { onColorSelected(color) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onColorSelected(Color.Transparent) }) { Text("Cancel") } }
    )
}
