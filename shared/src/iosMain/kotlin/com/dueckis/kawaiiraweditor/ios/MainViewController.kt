package com.dueckis.kawaiiraweditor.ios

// FIX: This import is required because the function returns a UIViewController
import platform.UIKit.UIViewController

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

// Optional: Explicitly state the return type to help the IDE
fun MainViewController(): UIViewController = ComposeUIViewController {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("iOS App Built Successfully!")
    }
}