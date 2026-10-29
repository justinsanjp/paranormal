package com.paranormal.toolkit.data

enum class Language { DE, EN }

data class SpokenWord(
    val text: String,
    val language: Language,
    val timestampMillis: Long,
    val triggerEnergy: Float,
)
