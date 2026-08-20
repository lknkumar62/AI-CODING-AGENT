package com.vasu.codeagent.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vasu.codeagent.VasuApp

@Composable
fun HomeScreen(app: VasuApp, onStartAgent: () -> Unit, onOpenSettings: () -> Unit) {
    val config by app.settingsStore.providerConfig.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("VASU CODE AGENT", style = MaterialTheme.typography.titleLarge)
        Text(
            "Mobile-first AI software development agent — no laptop required.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Connected Model", style = MaterialTheme.typography.labelSmall)
                Text(
                    if (config.isUsable()) "${config.model}\n${config.baseUrl}" else "Not configured",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Connected GitHub", style = MaterialTheme.typography.labelSmall)
                Text("Not connected (Phase 4)", style = MaterialTheme.typography.bodyMedium)
                Text("Current Project", style = MaterialTheme.typography.labelSmall)
                Text("No project open (Phase 2)", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Button(onClick = onStartAgent, modifier = Modifier.fillMaxWidth()) {
            Text("Start AI Agent")
        }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Configure Provider")
        }

        Text("Recent Projects", style = MaterialTheme.typography.titleMedium)
        Text("None yet — repository browsing arrives in Phase 4.", style = MaterialTheme.typography.bodyMedium)

        Text("Recent Tasks", style = MaterialTheme.typography.titleMedium)
        Text("None yet.", style = MaterialTheme.typography.bodyMedium)
    }
}
