package com.media3watch.sample.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.ui.compose.material3.R as Media3Material3R
import com.media3watch.sample.PlayerUiState
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isBuffering && !visible) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }
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
                    iconRes = Media3Material3R.drawable.media3_icon_skip_back_10,
                    contentDescription = "Seek back 10 seconds",
                    onClick = {
                        onUserInteraction()
                        onSeekBack()
                    }
                )

                CenterActionButton(
                    iconRes = if (uiState.isPlaying) Media3Material3R.drawable.media3_icon_pause else Media3Material3R.drawable.media3_icon_play,
                    contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                    onClick = {
                        onUserInteraction()
                        onTogglePlayPause()
                    }
                )

                CenterActionButton(
                    iconRes = Media3Material3R.drawable.media3_icon_skip_forward_10,
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
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .clip(CircleShape)
                                .shadow(elevation = 4.dp, CircleShape)
                                .background(Color.White)
                        )
                    },
                    track = { sliderState ->
                        val playedFraction = if (durationMs > 0L) {
                            (sliderState.value / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(bufferedFraction)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(playedFraction)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
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
