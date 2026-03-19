package com.smart.srtplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    // سیٹنگز کی سٹیٹس
    var subFontSize by remember { mutableStateOf(24f) }
    var timerSize by remember { mutableStateOf(16f) }
    var bgOpacity by remember { mutableStateOf(0.7f) }
    var textColor by remember { mutableStateOf(Color.White) }
    var bgColor by remember { mutableStateOf(Color.Black) }

    // ڈائیلاگ کنٹرول
    var showTextColorPicker by remember { mutableStateOf(false) }
    var showBgColorPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Smart SRT Player Settings", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)

        // --- لائیو پری ویو سیکشن ---
        Text("Preview:", fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            // یہ وہ باکس ہے جو فلوٹنگ ونڈو کی نقل ہے
            Column(
                modifier = Modifier
                    .wrapContentSize()
                    .background(bgColor.copy(alpha = bgOpacity), RoundedCornerShape(4.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "00:01:24", 
                    color = textColor, 
                    fontSize = timerSize.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "اردو سبٹائٹل یہاں نظر آئے گا",
                    color = textColor,
                    fontSize = subFontSize.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 1 & 2: فائل بٹنز
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { }, modifier = Modifier.weight(1f)) { Text("Add SRT") }
            Button(onClick = { }, modifier = Modifier.weight(1f)) { Text("Add TTF") }
        }

        // 3: فونٹ سائز سلائیڈر
        Text("Subtitle Font Size: ${subFontSize.toInt()}")
        Slider(value = subFontSize, valueRange = 12f..60f, onValueChange = { subFontSize = it })

        // 4: ٹائمر سائز سلائیڈر
        Text("Timer Size: ${timerSize.toInt()}")
        Slider(value = timerSize, valueRange = 10f..30f, onValueChange = { timerSize = it })

        // 5: اوپیسٹی سلائیڈر
        Text("Background Opacity: ${(bgOpacity * 100).toInt()}%")
        Slider(value = bgOpacity, valueRange = 0f..1f, onValueChange = { bgOpacity = it })

        // 6 & 7: کلر پکرز
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Subtitle Color: ", modifier = Modifier.weight(1f))
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(textColor).border(1.dp, Color.Gray, CircleShape).clickable { showTextColorPicker = true })
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Background Color: ", modifier = Modifier.weight(1f))
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(bgColor).border(1.dp, Color.Gray, CircleShape).clickable { showBgColorPicker = true })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 8: سٹارٹ بٹن
        Button(
            onClick = { /* یہاں سروس سٹارٹ کرنے کا کوڈ آئے گا */ },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
        ) { Text("START SUBTITLE PLAYER WINDOW", fontWeight = FontWeight.Bold) }
    }

    // کلر ڈائیلاگز
    if (showTextColorPicker) ColorPickerDialog({ textColor = it; showTextColorPicker = false }, "Select Text Color")
    if (showBgColorPicker) ColorPickerDialog({ bgColor = it; showBgColorPicker = false }, "Select Background Color")
}

@Composable
fun ColorPickerDialog(onColorSelected: (Color) -> Unit, title: String) {
    val colors = listOf(Color.White, Color.Black, Color.Yellow, Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Gray)
    AlertDialog(
        onDismissRequest = { },
        title = { Text(title) },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    colors.take(4).forEach { color ->
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(color).border(1.dp, Color.LightGray, CircleShape).clickable { onColorSelected(color) })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    colors.drop(4).forEach { color ->
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(color).border(1.dp, Color.LightGray, CircleShape).clickable { onColorSelected(color) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { /* Dismiss */ }) { Text("Cancel") } }
    )
}
