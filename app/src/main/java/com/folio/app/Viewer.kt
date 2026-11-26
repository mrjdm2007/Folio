package com.folio.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.min

data class ViewTarget(val stored: String, val name: String, val cache: Boolean = false)

fun loadBitmap(f: File, maxW: Int): Bitmap? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var sample = 1
    while (o.outWidth / sample > maxW * 2) sample *= 2
    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

@Composable fun AttachmentImage(s: Store, stored: String) {
    val bmp = remember(stored) { loadBitmap(s.attachmentFile(stored), 1200) }
    if (bmp != null) Image(bmp.asImageBitmap(), null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    else SectionTitle("⚠ Image file is missing")
}

// ---- LaTeX rendering with KaTeX inside a WebView (needs internet the first time; falls back to showing the source) ----
@Composable fun MathView(latex: String, color: Color) {
    var h by remember { mutableIntStateOf(56) }
    val hex = "#%06X".format(color.toArgb() and 0xFFFFFF)
    val html = remember(latex, hex) {
        "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<link rel='stylesheet' href='https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css'>" +
            "<script src='https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js'></script></head>" +
            "<body style='margin:0;color:$hex;background:transparent'><div id='m' style='font-size:1.25em;padding:6px 0;overflow-x:auto'></div>" +
            "<script>var s=${JSONObject.quote(latex)};var m=document.getElementById('m');" +
            "try{katex.render(s,m,{displayMode:true,throwOnError:false})}catch(e){m.style.fontFamily='monospace';m.textContent=s}</script></body></html>"
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(h.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(0)
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.evaluateJavascript("document.getElementById('m').scrollHeight") { r -> r?.toIntOrNull()?.let { h = it + 12 } }
                    }
                }
            }
        },
        update = { v ->
            if (v.tag != html) { v.tag = html; v.loadDataWithBaseURL("https://folio.local/", html, "text/html", "utf-8", null) }
        })
}

// ---- viewer screen ----
@Composable fun Viewer(s: Store, t: ViewTarget, onClose: () -> Unit) {
    val f = s.attachmentFile(t.stored, t.cache)
    val ext = t.name.substringAfterLast('.', "").lowercase()
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) { Chip("‹ Back", onClick = onClose); Text(t.name, Modifier.weight(1f), maxLines = 1) }
        when {
            !f.exists() -> Text("This file is missing from app storage.", Modifier.padding(16.dp))
            ext == "pdf" -> PdfViewer(s, t, f)
            ext in listOf("docx", "pptx", "xlsx", "txt", "md", "csv") -> DocViewer(f, ext)
            else -> Text("Can't display .$ext files. Supported: PDF, Word (.docx), PowerPoint (.pptx), Excel (.xlsx) and plain text. Older .doc / .ppt / .xls files are not supported.", Modifier.padding(16.dp))
        }
    }
}

fun renderPage(r: PdfRenderer, i: Int, targetW: Int): Bitmap {
    val p = r.openPage(i)
    try {
        val scale = targetW / p.width.toFloat()
        val b = Bitmap.createBitmap(targetW, (p.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        b.eraseColor(android.graphics.Color.WHITE)
        p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return b
    } finally { p.close() }
}

private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
suspend fun ocr(b: Bitmap): String = suspendCoroutine { c ->
    recognizer.process(InputImage.fromBitmap(b, 0)).addOnSuccessListener { c.resume(it.text) }.addOnFailureListener { c.resume("") }
}

@Composable fun PdfViewer(s: Store, t: ViewTarget, f: File) {
    val renderer = remember(f) { runCatching { PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull() }
    DisposableEffect(renderer) { onDispose { runCatching { renderer?.close() } } }
    if (renderer == null) Text("This PDF could not be opened (it may be password-protected or damaged).", Modifier.padding(16.dp))
    else PdfBody(s, t, renderer)
}

@Composable fun PdfBody(s: Store, t: ViewTarget, r: PdfRenderer) {
    val count = r.pageCount
    var page by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var showText by remember { mutableStateOf(false) }
    var goto by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val bmp = remember(page) { renderPage(r, page, 1400) }
    val text = s.data.texts[t.stored]
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Chip("‹") { if (page > 0) page-- }
            Chip("Page ${page + 1} / $count") { goto = true }
            Chip("›") { if (page < count - 1) page++ }
            Chip("−") { zoom = max(1f, zoom - 0.5f) }
            Chip("+") { zoom = min(4f, zoom + 0.5f) }
            Chip(if (text == null) "Extract text (OCR)" else "Re-extract") {
                scope.launch {
                    runCatching {
                        val sb = StringBuilder()
                        for (i in 0 until count) { status = "Reading text… ${i + 1}/$count"; sb.append(ocr(renderPage(r, i, 1500))).append("\n\n") }
                        s.update { d -> d.copy(texts = d.texts + (t.stored to sb.toString())) }
                        status = "Text extracted. It is now searchable."
                    }.onFailure { status = "Text extraction failed" }
                }
            }
            if (text != null) Chip(if (showText) "Show page" else "Show text", showText) { showText = !showText }
        }
        if (status.isNotBlank()) Text(status, color = Gold, modifier = Modifier.padding(horizontal = 12.dp))
        if (showText && text != null) Text(text, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp))
        else BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            Image(bmp.asImageBitmap(), null, modifier = Modifier.width(maxWidth * zoom), contentScale = ContentScale.FillWidth)
        }
    }
    if (goto) FormDialog("Go to page", listOf("Page number"), listOf((page + 1).toString()),
        { v -> v[0].toIntOrNull()?.let { page = (it - 1).coerceIn(0, count - 1) } }) { goto = false }
}

@Composable fun DocViewer(f: File, ext: String) {
    val res by produceState<Result<List<DocPart>>?>(null, f) { value = withContext(Dispatchers.IO) { runCatching { readDoc(f, ext) } } }
    val r = res
    when {
        r == null -> Text("Loading…", Modifier.padding(16.dp))
        r.isFailure -> Text("Could not read this file: ${r.exceptionOrNull()?.message ?: "unknown error"}", Modifier.padding(16.dp))
        else -> {
            val parts = r.getOrNull().orEmpty()
            if (parts.all { it.lines.isEmpty() }) Text("No readable text found in this file.", Modifier.padding(16.dp))
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                parts.forEach { part ->
                    if (part.title.isNotBlank()) item { Text(part.title, color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    items(part.lines) { line ->
                        if (line.startsWith("#")) Text(line.drop(1), fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                        else Text(line, fontSize = 17.sp, lineHeight = 26.sp, fontFamily = FontFamily.Serif)
                    }
                }
            }
        }
    }
}

@Composable fun OpenDoc(s: Store, view: (ViewTarget) -> Unit) {
    var err by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { val r = s.attach(it, cache = true); if (r != null) view(ViewTarget(r.first, r.second, true)) else err = "Could not read that file" }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Open a PDF, Word, PowerPoint or Excel file just to read it. Nothing is added to your notes.")
        Button({ pick.launch(arrayOf("*/*")) }) { Text("Choose a document") }
        if (err.isNotBlank()) Text(err, color = Gold)
    }
}
