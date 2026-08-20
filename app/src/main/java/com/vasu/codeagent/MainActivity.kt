package com.vasu.codeagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vasu.codeagent.ui.navigation.VasuNavGraph
import com.vasu.codeagent.ui.theme.VasuCodeAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VasuApp
        setContent {
            VasuCodeAgentTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VasuNavGraph(app = app)
                }
            }
        }
    }
}
