package com.spotlin.messenger.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app Telegram-style Media Editor Dialog before sending:
 * Allows adding caption, crop preview, filters and sending
 */
@Composable
fun TelegramMediaEditorDialog(
    mediaUri: Uri,
    mediaType: String, // "photo" or "video"
    initialCaption: String = "",
    onDismiss: () -> Unit,
    onSend: (caption: String) -> Unit
) {
    val context = LocalContext.current
    var caption by remember { mutableStateOf(initialCaption) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isMutedVideo by remember { mutableStateOf(false) }

    LaunchedEffect(mediaUri) {
        if (mediaType == "photo") {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(mediaUri)?.use { stream ->
                        bitmap = BitmapFactory.decodeStream(stream)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Отмена", tint = Color.White)
                    }

                    Row {
                        if (mediaType == "video") {
                            IconButton(onClick = { isMutedVideo = !isMutedVideo }) {
                                Icon(
                                    if (isMutedVideo) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Звук",
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(onClick = { /* crop action */ }) {
                            Icon(Icons.Default.Crop, contentDescription = "Кадрирование", tint = Color.White)
                        }
                        IconButton(onClick = { /* draw action */ }) {
                            Icon(Icons.Default.Brush, contentDescription = "Рисование", tint = Color.White)
                        }
                    }
                }

                // Media Preview Area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp, bottom = 120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = "Редактируемое фото",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (mediaType == "video") Icons.Default.PlayCircle else Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (mediaType == "video") "Видеозапись готова к отправке" else "Изображение",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                // Bottom Caption & Send Bar (Telegram style)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                    color = Color.Black.copy(alpha = 0.85f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = caption,
                            onValueChange = { caption = it },
                            placeholder = { Text("Добавить подпись...", color = Color.White.copy(alpha = 0.6f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        FloatingActionButton(
                            onClick = { onSend(caption) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Отправить")
                        }
                    }
                }
            }
        }
    }
}
