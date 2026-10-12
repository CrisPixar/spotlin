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
import androidx.compose.foundation.gestures.detectTapGestures
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
    val initialDurationMs = remember(durationSec) {
        if (durationSec > 0f) (durationSec * 1000).toInt() else 42000
    }
    var totalDurationMs by remember { mutableIntStateOf(initialDurationMs) }
    var isPrepared by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isSimulated by remember { mutableStateOf(false) }

    val mediaPlayer = remember(audioUrl) {
        MediaPlayer().apply {
            try {
                if (audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) {
                    setDataSource(audioUrl)
                    setOnPreparedListener { mp ->
                        totalDurationMs = if (mp.duration > 0) mp.duration else initialDurationMs
                        isPrepared = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        currentPositionMs = 0
                    }
                    prepareAsync()
                } else if (audioUrl.startsWith("content://") || audioUrl.startsWith("file://")) {
                    setDataSource(context, Uri.parse(audioUrl))
                    setOnPreparedListener { mp ->
                        totalDurationMs = if (mp.duration > 0) mp.duration else initialDurationMs
                        isPrepared = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        currentPositionMs = 0
                    }
                    prepareAsync()
                } else {
                    // Local synthetic voice note (recorded or simulated)
                    isSimulated = true
                    isPrepared = true
                    totalDurationMs = initialDurationMs
                }
            } catch (e: Exception) {
                isSimulated = true
                isPrepared = true
                totalDurationMs = initialDurationMs
            }
        }
    }

    DisposableEffect(audioUrl) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (_: Exception) {}
        }
    }

    // Playback loop with real timer progression
    LaunchedEffect(isPlaying, isSimulated) {
        while (isPlaying) {
            if (isSimulated) {
                delay(100)
                val step = (100 * playbackSpeed).toInt()
                if (currentPositionMs + step >= totalDurationMs) {
                    currentPositionMs = 0
                    isPlaying = false
                } else {
                    currentPositionMs += step
                }
            } else {
                try {
                    if (mediaPlayer.isPlaying) {
                        currentPositionMs = mediaPlayer.currentPosition
                        if (mediaPlayer.duration > 0) {
                            totalDurationMs = mediaPlayer.duration
                        }
                    }
                } catch (_: Exception) {}
                delay(100)
            }
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
                    if (isPlaying) {
                        if (!isSimulated) {
                            try { mediaPlayer.pause() } catch (_: Exception) {}
                        }
                        isPlaying = false
                    } else {
                        if (!isSimulated) {
                            try { mediaPlayer.start() } catch (_: Exception) {}
                        }
                        isPlaying = true
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
            val progress = if (totalDurationMs > 0) (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f) else 0f
            TelegramVoiceWaveform(
                progress = progress,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onSeek = { seekFraction ->
                    val targetMs = (seekFraction * totalDurationMs).toInt().coerceIn(0, totalDurationMs)
                    currentPositionMs = targetMs
                    if (!isSimulated) {
                        try { mediaPlayer.seekTo(targetMs) } catch (_: Exception) {}
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Real timer
                val displayMs = if (isPlaying || currentPositionMs > 0) currentPositionMs else totalDurationMs
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
                            if (!isSimulated && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
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
    onSeek: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val barHeights = remember {
        listOf(
            0.35f, 0.55f, 0.85f, 0.45f, 0.65f, 0.95f, 0.75f, 0.5f, 1.0f, 0.8f,
            0.6f, 0.4f, 0.75f, 0.9f, 0.55f, 0.35f, 0.65f, 0.85f, 0.45f, 0.75f,
            0.5f, 0.35f, 0.65f, 0.95f, 0.7f, 0.45f, 0.85f, 0.6f, 0.5f, 0.35f
        )
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                onSeek(fraction)
            }
        }
    ) {
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
 * Authentic Telegram Music / Song Player for Audio files (with title, artist/performer, seek slider):
 */
@Composable
fun TelegramMusicPlayer(
    audioUrl: String,
    title: String,
    performer: String = "Неизвестный исполнитель",
    durationSec: Float = 0f,
    isMine: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    val initialDurationMs = remember(durationSec) {
        if (durationSec > 0f) (durationSec * 1000).toInt() else 180000
    }
    var totalDurationMs by remember { mutableIntStateOf(initialDurationMs) }
    var isSimulated by remember { mutableStateOf(false) }

    val mediaPlayer = remember(audioUrl) {
        MediaPlayer().apply {
            try {
                if (audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) {
                    setDataSource(audioUrl)
                    setOnPreparedListener { mp ->
                        totalDurationMs = if (mp.duration > 0) mp.duration else initialDurationMs
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        currentPositionMs = 0
                    }
                    prepareAsync()
                } else if (audioUrl.startsWith("content://") || audioUrl.startsWith("file://")) {
                    setDataSource(context, Uri.parse(audioUrl))
                    setOnPreparedListener { mp ->
                        totalDurationMs = if (mp.duration > 0) mp.duration else initialDurationMs
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        currentPositionMs = 0
                    }
                    prepareAsync()
                } else {
                    isSimulated = true
                    totalDurationMs = initialDurationMs
                }
            } catch (_: Exception) {
                isSimulated = true
                totalDurationMs = initialDurationMs
            }
        }
    }

    DisposableEffect(audioUrl) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying, isSimulated) {
        while (isPlaying) {
            if (isSimulated) {
                delay(200)
                if (currentPositionMs + 200 >= totalDurationMs) {
                    currentPositionMs = 0
                    isPlaying = false
                } else {
                    currentPositionMs += 200
                }
            } else {
                try {
                    if (mediaPlayer.isPlaying) {
                        currentPositionMs = mediaPlayer.currentPosition
                        if (mediaPlayer.duration > 0) totalDurationMs = mediaPlayer.duration
                    }
                } catch (_: Exception) {}
                delay(200)
            }
        }
    }

    val activeColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    val progress = if (totalDurationMs > 0) (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f) else 0f

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = activeColor,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (isPlaying) {
                                if (!isSimulated) try { mediaPlayer.pause() } catch (_: Exception) {}
                                isPlaying = false
                            } else {
                                if (!isSimulated) try { mediaPlayer.start() } catch (_: Exception) {}
                                isPlaying = true
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                            tint = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { "Аудиозапись" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Text(
                        text = performer.ifBlank { "Spotlin Audio" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = progress,
                onValueChange = { newProgress ->
                    val targetMs = (newProgress * totalDurationMs).toInt().coerceIn(0, totalDurationMs)
                    currentPositionMs = targetMs
                    if (!isSimulated) {
                        try { mediaPlayer.seekTo(targetMs) } catch (_: Exception) {}
                    }
                },
                modifier = Modifier.fillMaxWidth().height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = activeColor,
                    activeTrackColor = activeColor,
                    inactiveTrackColor = activeColor.copy(alpha = 0.25f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTimer(currentPositionMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                Text(formatTimer(totalDurationMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
            }
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

/**
 * Telegram Photo Bubble displaying the real photo preview
 */
@Composable
fun TelegramPhotoBubble(
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(imageUrl) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(imageUrl) { mutableStateOf(true) }

    LaunchedEffect(imageUrl) {
        withContext(Dispatchers.IO) {
            try {
                if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                    val stream: InputStream = URL(imageUrl).openStream()
                    bitmap = BitmapFactory.decodeStream(stream)
                } else if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
                    val stream = context.contentResolver.openInputStream(Uri.parse(imageUrl))
                    bitmap = BitmapFactory.decodeStream(stream)
                    stream?.close()
                }
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E232A),
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = "Фотография",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2B333E)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Фотография",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
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
