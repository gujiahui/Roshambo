package com.roshambo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roshambo.app.game.DuelViewModel
import com.roshambo.app.ui.RoshamboApp
import com.roshambo.app.ui.theme.RoshamboTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RoshamboTheme {
                Surface(Modifier.fillMaxSize()) {
                    val viewModel: DuelViewModel = viewModel()
                    RoshamboApp(viewModel)
                }
            }
        }
    }
}
