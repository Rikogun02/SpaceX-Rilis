package com.spacexrilis.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SpaceXRilisApp() }
    }
}

@Composable
private fun SpaceXRilisApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("SpaceX Rilis", style = MaterialTheme.typography.headlineMedium)
                Text("Solana token scanner", style = MaterialTheme.typography.titleMedium)
                AssistChip(onClick = {}, label = { Text("AUTO BUY SIMULATION: AKTIF") })
                Text("Scanner hybrid · polling 30 detik")
                Text("Tahap 3–4: fondasi repository dan integrasi API sedang disiapkan.")
                Text("Tidak ada transaksi nyata atau wallet private key di aplikasi.")
            }
        }
    }
}
