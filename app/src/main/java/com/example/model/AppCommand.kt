package com.example.model

data class AppCommand(
    val type: String,
    val params: Map<String, String> = emptyMap()
)
