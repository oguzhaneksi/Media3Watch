package com.media3watch.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.media3watch.sample.ui.PlayerScreen
import com.media3watch.sample.ui.StreamSelectionScreen
import com.media3watch.sample.ui.theme.Media3WatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Media3WatchTheme {
                val viewModel: PlayerViewModel = viewModel()
                val selectedStream by viewModel.selectedStream.collectAsStateWithLifecycle()
                val current = selectedStream
                if (current == null) {
                    StreamSelectionScreen(onStreamSelected = { viewModel.selectStream(it) })
                } else {
                    PlayerScreen(viewModel = viewModel)
                }
            }
        }
    }
}
