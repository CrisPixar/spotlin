package com.spotlin.messenger.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import java.util.Locale

/**
 * Authentic Telegram Voice Message Player:
 * - Circular play/pause button with smooth animation
 * - Audio wave visualization bars
 * - Real playback with MediaPlayer and position/duration timer
 * - Playback speed multiplier (1x / 2x)
 */
@Composable
fun TelegramVoiceMessagePlayer(
    audioUrl: String,
    durationSec: Float = 0f,
    isMine: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var totalDurationMs by remember { mutableIntStateOf((durationSec * 1000).toInt()) }
    var isPrepared by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    val mediaPlayer = remember {
        MediaPlayer().apply {
            try {
                if (audioUrl.startsWith("http")) {
                    setDataSource(audioUrl)
                } else {
                    setDataSource(context, Uri.parse(audioUrl))
                }
                setOnPreparedListener { mp ->
                    totalDurationMs = mp.duration
                    isPrepared = true
                }
                setOnCompletionListener {
                    isPlaying = false
                    currentPositionMs = 0
                }
                prepareAsync()
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer.stop()
                mediaPlayer.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                if (mediaPlayer.isPlaying) {
                    currentPositionMs = mediaPlayer.currentPosition
                }
            } catch (_: Exception) {}
            delay(100)
        }
    }

    val activeColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    val inactiveColor = activeColor.copy(alpha = 0.35f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Telegram circular Play/Pause button
        Surface(
            shape = CircleShape,
            color = activeColor,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable {
                    if (isPrepared) {
                        if (isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            mediaPlayer.start()
                            isPlaying = true
                        }
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                    tint = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Waveform bars and counter
        Column(modifier = Modifier.weight(1f)) {
            // Simulated Telegram voice waveform bars
            val progress = if (totalDurationMs > 0) currentPositionMs.toFloat() / totalDurationMs else 0f
            TelegramVoiceWaveform(
                progress = progress,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clickable {
                        // Click anywhere on waveform to seek
                    }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Real timer
                val displayMs = if (isPlaying) currentPositionMs else totalDurationMs
                Text(
                    text = formatTimer(displayMs),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = activeColor
                )

                // 2X Speed Toggle Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (playbackSpeed > 1f) activeColor else activeColor.copy(alpha = 0.15f),
                    modifier = Modifier.clickable {
                        playbackSpeed = if (playbackSpeed == 1.0f) 2.0f else 1.0f
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(playbackSpeed)
                            }
                        } catch (_: Exception) {}
                    }
                ) {
                    Text(
                        text = if (playbackSpeed > 1f) "2X" else "1X",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (playbackSpeed > 1f) MaterialTheme.colorScheme.surface else activeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TelegramVoiceWaveform(
    progress: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    val barHeights = remember {
        listOf(
            0.3f, 0.5f, 0.8f, 0.4f, 0.6f, 0.9f, 0.7f, 0.5f, 1.0f, 0.8f,
            0.6f, 0.4f, 0.7f, 0.9f, 0.5f, 0.3f, 0.6f, 0.8f, 0.4f, 0.7f,
            0.5f, 0.3f, 0.6f, 0.9f, 0.7f, 0.4f, 0.8f, 0.6f, 0.5f, 0.3f
        )
    }

    Canvas(modifier = modifier) {
        val totalBars = barHeights.size
        val barWidth = 3.dp.toPx()
        val spacing = (size.width - (totalBars * barWidth)) / (totalBars - 1).coerceAtLeast(1)

        barHeights.forEachIndexed { index, heightRatio ->
            val x = index * (barWidth + spacing) + barWidth / 2
            val barH = size.height * heightRatio.coerceIn(0.2f, 1f)
            val topY = (size.height - barH) / 2
            val bottomY = topY + barH

            val barProgress = index.toFloat() / totalBars
            val color = if (barProgress <= progress) activeColor else inactiveColor

            drawLine(
                color = color,
                start = Offset(x, topY),
                end = Offset(x, bottomY),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Circular progress overlay for Telegram-style file uploads and downloads
 */
@Composable
fun TelegramCircularProgress(
    progress: Float, // 0.0f to 1.0f
    isDownloading: Boolean = false,
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "CircularProgress"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(38.dp)) {
            // Inactive track
            drawCircle(
                color = Color.White.copy(alpha = 0.3f),
                style = Stroke(width = 3.dp.toPx())
            )
            // Progress arc
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Icon(
            if (isDownloading) Icons.Default.Close else Icons.Default.ArrowDownward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Native Document / File Bubble with download progress
 */
@Composable
fun TelegramFileBubble(
    fileName: String,
    fileSize: String,
    fileUrl: String,
    onOpenFile: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenFile)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = fileSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onOpenFile) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Скачать",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SpotlinAudioPlayer(
    audioUrl: String,
    title: String = "Аудиозапись",
    modifier: Modifier = Modifier
) {
    TelegramVoiceMessagePlayer(audioUrl = audioUrl, modifier = modifier)
}

@Composable
fun SpotlinVideoPlayerDialog(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoURI(Uri.parse(videoUrl))
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
            }
        }
    }
}

@Composable
fun SpotlinImageViewerDialog(
    imageUrl: String,
    title: String = "Фотография",
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUrl) {
        withContext(Dispatchers.IO) {
            try {
                if (imageUrl.startsWith("http")) {
                    val stream: InputStream = URL(imageUrl).openStream()
                    bitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.8f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentAlignment = Alignment.Center
            ) {
                val currentBitmap = bitmap
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(title, color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimer(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
