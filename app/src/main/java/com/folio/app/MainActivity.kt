package com.folio.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {
    private val store: Store by viewModels()
    override fun onStop() { super.onStop(); store.flush() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Graphite.toArgb()
        window.navigationBarColor = Graphite.toArgb()
        if (savedInstanceState == null) handle(intent)
        setContent { FolioTheme { App(store) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }
    /** "Open with Folio" (ACTION_VIEW) and the share sheet (ACTION_SEND) both land here. */
    private fun handle(i: Intent?) {
        val uri: Uri? = when (i?.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> streamOf(i)
            else -> null
        }
        uri?.let { store.openIncoming(it) }
    }
    @Suppress("DEPRECATION")
    private fun streamOf(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)
}
