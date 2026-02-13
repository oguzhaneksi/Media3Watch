package com.media3watch.sample.ui

import android.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.media3watch.sample.PlayerUiState
import java.util.concurrent.TimeUnit

@Composable
fun PlayerController(
    uiState: PlayerUiState,
    visible: Boolean,
    onSeekBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onScrubPositionChanged: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    onUserInteraction: () -> Unit,
) {
    val durationMs = uiState.durationMs.coerceAtLeast(0L)
    val sliderValue = uiState.sliderValueMs.coerceIn(0L, durationMs)
    val bufferedFraction = if (durationMs > 0L) {
        (uiState.bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CenterActionButton(
                    iconRes = R.drawable.ic_media_rew,
                    contentDescription = "Seek back 10 seconds",
                    onClick = {
                        onUserInteraction()
                        onSeekBack()
                    }
                )

                CenterActionButton(
                    iconRes = if (uiState.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                    contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                    onClick = {
                        onUserInteraction()
                        onTogglePlayPause()
                    }
                )

                CenterActionButton(
                    iconRes = R.drawable.ic_media_ff,
                    contentDescription = "Seek forward 10 seconds",
                    onClick = {
                        onUserInteraction()
                        onSeekForward()
                    }
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.70f)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                LinearProgressIndicator(
                    progress = { bufferedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                    color = Color.White.copy(alpha = 0.35f),
                    trackColor = Color.White.copy(alpha = 0.15f)
                )

                Slider(
                    value = sliderValue.toFloat(),
                    onValueChange = { value ->
                        onUserInteraction()
                        onScrubPositionChanged(value.toLong())
                    },
                    onValueChangeFinished = {
                        onUserInteraction()
                        onScrubFinished()
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(sliderValue),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    Text(
                        text = "-${formatTime((durationMs - sliderValue).coerceAtLeast(0L))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterActionButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .size(58.dp)
            .clip(CircleShape),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.50f),
            contentColor = Color.White
        )
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(30.dp)
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
