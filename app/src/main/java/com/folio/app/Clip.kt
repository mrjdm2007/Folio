package com.folio.app

import android.content.ClipboardManager as AndroidClipboard
import android.content.Context
import android.net.Uri
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString

/** Inserted into a text field when the user pastes an image/file; the editor swaps it for an inline attachment. */
const val PASTE_MARK = "\uE000FOLIO-PASTE\uE001"

private fun cm(ctx: Context) = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as AndroidClipboard

fun isFileClip(ctx: Context): Boolean {
    val d = cm(ctx).primaryClipDescription ?: return false
    return d.hasMimeType("image/*") || d.hasMimeType("application/*")
}

fun clipFileUri(ctx: Context): Uri? {
    val clip = cm(ctx).primaryClip ?: return null
    return if (clip.itemCount > 0) clip.getItemAt(0).uri else null
}

/** Makes the normal long-press "Paste" menu work for images and PDFs inside note text. */
class FolioClipboard(private val base: ClipboardManager, private val ctx: Context) : ClipboardManager by base {
    override fun hasText(): Boolean = isFileClip(ctx) || base.hasText()
    override fun getText(): AnnotatedString? = if (isFileClip(ctx)) AnnotatedString(PASTE_MARK) else base.getText()
}
