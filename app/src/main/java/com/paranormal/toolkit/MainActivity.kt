package com.paranormal.toolkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.paranormal.toolkit.navigation.AppScaffold
import com.paranormal.toolkit.ui.theme.ParanormalToolkitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParanormalToolkitTheme {
                AppScaffold()
            }
        }
    }
}
