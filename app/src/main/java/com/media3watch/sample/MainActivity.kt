package com.media3watch.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.media3watch.sample.ui.PlayerScreen
import com.media3watch.sample.ui.theme.Media3WatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Media3WatchTheme {
                PlayerScreen()
            }
        }
    }
}
