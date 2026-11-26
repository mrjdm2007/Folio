package com.folio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {
    private val store: Store by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Graphite.toArgb()
        window.navigationBarColor = Graphite.toArgb()
        setContent { FolioTheme { App(store) } }
    }
}
