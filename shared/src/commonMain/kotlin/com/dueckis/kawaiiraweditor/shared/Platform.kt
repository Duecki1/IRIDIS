package com.dueckis.kawaiiraweditor.shared

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
