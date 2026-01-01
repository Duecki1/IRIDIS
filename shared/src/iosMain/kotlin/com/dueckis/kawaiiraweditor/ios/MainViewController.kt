package com.dueckis.kawaiiraweditor.ios

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

fun MainViewController() = ComposeUIViewController {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("iOS App Built Successfully!")
    }
}