package com.paranormal.toolkit.data

import java.io.File

data class Recording(
    val file: File,
    val name: String,
    val timestampMillis: Long,
    val durationMillis: Long,
    val sampleRateHz: Int,
)
