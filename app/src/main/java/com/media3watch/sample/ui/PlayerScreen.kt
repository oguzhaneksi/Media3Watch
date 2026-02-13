package com.media3watch.sample.ui

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.compose.PlayerSurface
import com.media3watch.sample.PlayerViewModel
import kotlinx.coroutines.delay

private const val CONTROLLER_AUTO_HIDE_MS = 3_000L

@Composable
fun PlayerScreen(viewModel: PlayerViewModel = viewModel()) {
    val activity = LocalActivity.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val player = viewModel.player

    var controlsVisible by rememberSaveable { mutableStateOf(true) }

    if (Build.VERSION.SDK_INT > 23) {
        LifecycleStartEffect(viewModel) {
            viewModel.initializePlayer()
            onStopOrDispose {
                if (activity?.isChangingConfigurations == false) {
                    viewModel.releasePlayer()
                }
            }
        }
    } else {
        LifecycleResumeEffect(viewModel) {
            viewModel.initializePlayer()
            onPauseOrDispose {
                if (activity?.isChangingConfigurations == false) {
                    viewModel.releasePlayer()
                }
            }
        }
    }

    LaunchedEffect(controlsVisible, uiState.isPlaying) {
        if (controlsVisible && uiState.isPlaying) {
            delay(CONTROLLER_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { controlsVisible = !controlsVisible }
            )
    ) {
        PlayerSurface(
            player = player,
            modifier = Modifier.fillMaxSize()
        )

        PlayerController(
            uiState = uiState,
            visible = controlsVisible,
            onSeekBack = viewModel::seekBack10s,
            onTogglePlayPause = viewModel::togglePlayPause,
            onSeekForward = viewModel::seekForward10s,
            onScrubPositionChanged = viewModel::onScrubPositionChanged,
            onScrubFinished = viewModel::onScrubFinished,
            onUserInteraction = { controlsVisible = true }
        )
    }
}
