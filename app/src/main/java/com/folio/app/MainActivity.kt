package com.folio.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {
    private val store: Store by viewModels()
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onStart() { super.onStart(); store.mergePending() }
    override fun onStop() { super.onStop(); store.flush() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Graphite.toArgb()
        window.navigationBarColor = Graphite.toArgb()
        Notifier.channels(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && !Prefs.p(this).getBoolean("perm_asked", false)) {
            Prefs.p(this).edit().putBoolean("perm_asked", true).apply()
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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
