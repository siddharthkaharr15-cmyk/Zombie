package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.GameScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.GameViewModel
import com.example.viewmodel.GameViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels {
        GameViewModelFactory((application as MyApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.audio.GameAudioSynth.context = this.applicationContext
        AdManager.loadRewardedAd(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GameScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.audio.GameAudioSynth.onResume()
    }

    override fun onPause() {
        super.onPause()
        com.example.audio.GameAudioSynth.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        com.example.audio.GameAudioSynth.stopEngine()
    }
}

