package com.folio.app

import android.content.Context
import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

data class AiCfg(val provider: String = "Gemini", val key: String = "", val model: String = "", val base: String = "", val web: Boolean = true)
data class NoteSrc(val id: String, val path: String, val text: String)
data class WebSrc(val title: String, val url: String, val text: String)
data class AiMsg(val role: String, val text: String, val notes: List<NoteSrc> = emptyList(), val web: List<WebSrc> = emptyList(),
                 val mode: String = "", val question: String = "")

/** The API key lives in app-private settings. It is not part of the notebook file, backups or exports. */
object AiPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("folio_ai", Context.MODE_PRIVATE)
    fun load(ctx: Context): AiCfg {
        val p = sp(ctx)
        return AiCfg(p.getString("provider", "Gemini") ?: "Gemini", p.getString("key", "") ?: "", p.getString("model", "") ?: "",
            p.getString("base", "") ?: "", p.getBoolean("web", true))
    }
    fun save(ctx: Context, c: AiCfg) {
        sp(ctx).edit().putString("provider", c.provider).putString("key", c.key).putString("model", c.model)
            .putString("base", c.base).putBoolean("web", c.web).apply()
    }
}

object Http {
    private fun java.io.InputStream.readCapped(max: Int): String = bufferedReader().use { r ->
        val sb = StringBuilder(); val buf = CharArray(4096)
        while (sb.length < max) { val n = r.read(buf); if (n <= 0) break; sb.append(buf, 0, n) }
        sb.toString()
    }
    /** Returns (status code, body). Never call on the main thread. */
    fun request(url: String, method: String = "GET", headers: Map<String, String> = emptyMap(), body: String? = null,
                timeoutMs: Int = 30_000, maxChars: Int = 600_000): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 15_000; c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) FolioApp/0.9")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return code to (stream?.readCapped(maxChars) ?: "")
        } finally { c.disconnect() }
    }
}

object Ai {
    fun base(c: AiCfg): String = when (c.provider) {
        "Gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai"
        "Groq" -> "https://api.groq.com/openai/v1"
        "OpenRouter" -> "https://openrouter.ai/api/v1"
        else -> c.base
    }.trim().trimEnd('/')

    private fun headers(c: AiCfg) = mapOf("Authorization" to "Bearer ${c.key.trim()}", "Content-Type" to "application/json")

    private fun errorText(code: Int, body: String): String {
        val detail = runCatching {
            val e = JSONObject(body).opt("error")
            if (e is JSONObject) e.optString("message") else (e as? String).orEmpty()
        }.getOrDefault("").ifBlank { body.take(160) }
        val head = when (code) {
            401, 403 -> "The provider rejected your API key (HTTP $code)."
            404 -> "Model or address not found. Tap Fetch models and pick one."
            429 -> "Rate limit reached. Wait a minute, or switch model or provider."
            else -> "The provider returned an error (HTTP $code)."
        }
        return if (detail.isBlank()) head else "$head $detail".take(300)
    }

    /** OpenAI-style chat completion, which Gemini, Groq and OpenRouter all accept. */
    fun chat(c: AiCfg, messages: JSONArray): String {
        if (base(c).isBlank()) throw IllegalStateException("Enter the provider's base URL in Setup.")
        val body = JSONObject().put("model", c.model.trim()).put("messages", messages).toString()
        val (code, text) = Http.request(base(c) + "/chat/completions", "POST", headers(c), body, timeoutMs = 90_000)
        if (code !in 200..299) throw IllegalStateException(errorText(code, text))
        val msg = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val out = msg.optString("content", "").trim()
        if (out.isEmpty()) throw IllegalStateException("The model sent an empty answer. Try again or choose another model.")
        return out
    }

    fun models(c: AiCfg): List<String> {
        if (base(c).isBlank()) throw IllegalStateException("Enter the provider's base URL first.")
        val (code, text) = Http.request(base(c) + "/models", "GET", headers(c))
        if (code !in 200..299) throw IllegalStateException(errorText(code, text))
        val j = JSONObject(text)
        val out = mutableListOf<String>()
        j.optJSONArray("data")?.let { a -> for (i in 0 until a.length()) out.add(a.getJSONObject(i).optString("id")) }
        j.optJSONArray("models")?.let { a -> for (i in 0 until a.length()) out.add(a.getJSONObject(i).optString("name")) }
        return out.map { it.removePrefix("models/") }.filter { it.isNotBlank() && !it.contains("embed", true) }.distinct().sorted()
    }
}

// ---------------- internet sources (all fetched inside the app) ----------------
private fun plain(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()

object Web {
    /** Intro text of the best matching Wikipedia articles. Reliable and free. */
    fun wikipedia(query: String): List<WebSrc> = runCatching {
        val (code, body) = Http.request("https://en.wikipedia.org/w/api.php?action=query&list=search&srlimit=3&format=json&srsearch=" + URLEncoder.encode(query, "UTF-8"))
        if (code != 200) return@runCatching emptyList<WebSrc>()
        val hits = JSONObject(body).getJSONObject("query").getJSONArray("search")
        (0 until hits.length()).mapNotNull { i ->
            val title = hits.getJSONObject(i).getString("title")
            val (c2, b2) = Http.request("https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1&explaintext=1&redirects=1&format=json&titles=" + URLEncoder.encode(title, "UTF-8"))
            if (c2 != 200) null else {
                val pages = JSONObject(b2).getJSONObject("query").getJSONObject("pages")
                val text = pages.getJSONObject(pages.keys().next()).optString("extract").trim()
                if (text.isEmpty()) null else WebSrc("$title (Wikipedia)", "https://en.wikipedia.org/wiki/" + title.replace(' ', '_'), text.take(1500))
            }
        }
    }.getOrDefault(emptyList())

    /** General web results from DuckDuckGo's plain page. Best effort: it may be blocked or change its layout. */
    fun search(query: String): List<WebSrc> = runCatching {
        val (code, html) = Http.request("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8"), maxChars = 300_000)
        if (code != 200) return@runCatching emptyList<WebSrc>()
        val rx = Regex("""class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>.*?class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        rx.findAll(html).take(4).map { m ->
            var url = m.groupValues[1]
            Regex("uddg=([^&]+)").find(url)?.groupValues?.get(1)?.let { url = URLDecoder.decode(it, "UTF-8") }
            if (url.startsWith("//")) url = "https:$url"
            WebSrc(plain(m.groupValues[2]).trim(), url, plain(m.groupValues[3]).trim())
        }.filter { it.url.startsWith("http") && it.text.isNotBlank() }.toList()
    }.getOrDefault(emptyList())

    /** Reads a web page the student pasted a link to and returns its text. */
    fun fetch(url: String): WebSrc? = runCatching {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return@runCatching null
        val (code, html) = Http.request(url, maxChars = 400_000)
        if (code !in 200..299) return@runCatching null
        val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.let { plain(it).trim() }?.take(120) ?: url
        val body = plain(html.replace(Regex("(?is)<(script|style|noscript|svg|nav|footer|header)[^>]*>.*?</\\1>"), " "))
        WebSrc(title, url, body.replace(Regex("\\s+"), " ").trim().take(5000))
    }.getOrNull()
}

// ---------------- notes retrieval ----------------
private val STOP = setOf("the", "and", "for", "with", "what", "how", "why", "when", "where", "which", "who", "does", "did", "can", "could", "would",
    "should", "explain", "tell", "about", "give", "show", "make", "please", "this", "that", "these", "those", "from", "into", "than", "then", "are",
    "was", "were", "its", "you", "your", "using", "use", "notes", "note", "page", "summarise", "summarize", "quiz")

fun keywords(q: String): List<String> =
    q.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in STOP }.distinct()

/** Finds the notes most relevant to a question. A pinned page is always included. */
fun retrieveNotes(s: Store, query: String, pinned: String?, k: Int = 4): List<NoteSrc> {
    val kw = keywords(query)
    val nodes = s.data.nodes.filter { n -> !n.deleted && n.blocks.any { it.text.isNotBlank() || it.type == "file" } }
    fun pdfText(n: Node) = n.blocks.filter { it.type == "file" }.mapNotNull { s.data.texts[it.text] }.joinToString("\n")
    val scored = nodes.map { n ->
        val title = n.title.lowercase(); val tags = n.tags.joinToString(" ").lowercase()
        val body = (n.blocks.joinToString("\n") { it.text + " " + it.extra } + " " + pdfText(n)).lowercase()
        var sc = 0
        kw.forEach { w ->
            if (title.contains(w)) sc += 5
            if (tags.contains(w)) sc += 3
            sc += minOf(8, Regex(Regex.escape(w)).findAll(body).count())
        }
        if (n.id == pinned) sc += 1000
        n to sc
    }.filter { it.second > 0 }.sortedByDescending { it.second }.take(k)
    return scored.map { (n, _) ->
        val limit = if (n.id == pinned) 4500 else 2200
        val pdf = pdfText(n).take(1500)
        NoteSrc(n.id, s.path(n.id), pageToMarkdown(n).take(limit) + if (pdf.isNotBlank()) "\n\n[Text from an attached PDF]\n$pdf" else "")
    }
}

// ---------------- Folio AI ----------------
object Scribe {
    val MODES = linkedMapOf(
        "Explain" to "Explain clearly and simply, building up from the basics, with one short worked example where it helps.",
        "Summarise" to "Summarise the relevant material as concise bullet points a student can revise from.",
        "Quiz me" to "Write 5 multiple-choice questions on the topic in EXACTLY this format: a question line, then 4 option lines (put * at the start of the correct option), then a line starting with 'Why:' explaining the answer. Separate questions with a blank line. No numbering and no other text.",
        "Flashcards" to "Write 8 flashcards, one per line, in EXACTLY this format: question | answer. No numbering and no other text.",
        "Exam answer" to "Write a well structured exam-style answer with short headings, key formulas, and a brief conclusion."
    )

    private fun gatherWeb(q: String): List<WebSrc> {
        val out = mutableListOf<WebSrc>()
        Regex("https?://[^\\s]+").findAll(q).take(2).forEach { m -> Web.fetch(m.value.trimEnd('.', ',', ')', ';'))?.let { out.add(it) } }
        val kw = keywords(q).take(8).joinToString(" ")
        if (kw.isNotBlank()) { out.addAll(Web.wikipedia(kw).take(2)); out.addAll(Web.search(kw).take(3)) }
        return out.distinctBy { it.url }.take(6)
    }

    /** Runs the whole pipeline on a background thread: find notes, read the web, ask the model. */
    fun answer(s: Store, cfg: AiCfg, question: String, mode: String, history: List<Pair<String, String>>, pinned: String?): AiMsg {
        val notes = retrieveNotes(s, question, pinned)
        val web = if (cfg.web) gatherWeb(question) else emptyList()
        val system = "You are Folio AI, the study assistant built into the student's notes app, Folio. The student is an engineering student. " +
            "Answer using the NOTES first, then the WEB sources, then your own knowledge. Cite sources inline like [N1] or [W2] when you use them, and never invent sources. " +
            "If the notes do not cover something, say so briefly and then answer from general knowledge. Be accurate and say when you are unsure. " +
            "Format as simple Markdown: short paragraphs, '- ' bullets, **bold** key terms. Write equations in plain text such as V = I*R, not LaTeX. " +
            (MODES[mode] ?: "")
        val ctx = buildString {
            if (notes.isNotEmpty()) { append("NOTES FROM THE STUDENT'S NOTEBOOK:\n"); notes.forEachIndexed { i, n -> append("[N${i + 1}] ${n.path}\n${n.text}\n\n") } }
            if (web.isNotEmpty()) { append("WEB SOURCES:\n"); web.forEachIndexed { i, w -> append("[W${i + 1}] ${w.title} (${w.url})\n${w.text}\n\n") } }
        }
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        history.forEach { (r, t) -> msgs.put(JSONObject().put("role", r).put("content", t.take(3000))) }
        msgs.put(JSONObject().put("role", "user").put("content", (if (ctx.isNotBlank()) ctx else "") + "QUESTION: " + question))
        return AiMsg("assistant", Ai.chat(cfg, msgs), notes, web, mode, question)
    }
}

/** Turns an AI answer written in simple Markdown into editor blocks. */
fun aiToBlocks(t: String): List<Block> = t.lines().map { it.trimEnd() }.filter { it.isNotBlank() }.map { l ->
    val s = l.trimStart()
    when {
        s.startsWith("#") -> Block(type = "h2", text = s.trimStart('#').trim())
        s.startsWith("- ") || s.startsWith("* ") -> Block(type = "bullet", text = s.drop(2).trim())
        Regex("^\\d+[.)] ").containsMatchIn(s) -> Block(type = "number", text = s.replace(Regex("^\\d+[.)]\\s+"), ""))
        else -> Block(text = s)
    }
}
