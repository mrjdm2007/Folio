package com.folio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

/** "4.7k", "100n", "2.2u", "10M", "1e3", "20mA" -> a number in base units. */
fun parseEng(raw: String): Double? {
    var s = raw.trim().replace("Ω", "").replace("ohm", "", true).replace("Hz", "").replace(",", ".")
    s = s.trimEnd('V', 'A', 'F', 'H', 'W', 's', ' ')
    val mult = when (s.lastOrNull()) { 'p' -> 1e-12; 'n' -> 1e-9; 'u', 'µ' -> 1e-6; 'm' -> 1e-3; 'k', 'K' -> 1e3; 'M' -> 1e6; 'G' -> 1e9; else -> null }
    val num = if (mult != null) s.dropLast(1) else s
    return num.trim().toDoubleOrNull()?.let { it * (mult ?: 1.0) }
}

/** 0.0047 + "F" -> "4.7 mF" */
fun fmtEng(x: Double, unit: String): String {
    if (x == 0.0 || x.isNaN() || x.isInfinite()) return "$x $unit"
    val a = abs(x)
    val (div, p) = when {
        a >= 1e9 -> 1e9 to "G"; a >= 1e6 -> 1e6 to "M"; a >= 1e3 -> 1e3 to "k"; a >= 1 -> 1.0 to ""
        a >= 1e-3 -> 1e-3 to "m"; a >= 1e-6 -> 1e-6 to "µ"; a >= 1e-9 -> 1e-9 to "n"; else -> 1e-12 to "p"
    }
    return fmtNum(x / div) + " " + p + unit
}

@Composable fun NumField(label: String, v: String, onChange: (String) -> Unit) =
    OutlinedTextField(v, onChange, label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())

@Composable fun Electronics() {
    var sub by remember { mutableIntStateOf(0) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("Ohm's law", "Resistor code", "Series / parallel", "RC / RL", "dB").forEachIndexed { i, t -> Chip(t, sub == i) { sub = i } }
        }
        Text("Type values with suffixes p n µ(u) m k M G, for example 4.7k, 100n, 20m.", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
        when (sub) { 0 -> OhmLaw(); 1 -> ResistorCode(); 2 -> SeriesParallel(); 3 -> RcRl(); else -> Decibels() }
    }
}

// ---------- Ohm's law ----------
private fun ohm(v: Double?, i: Double?, r: Double?, p: Double?): List<Double>? = when {   // [V, I, R, P]
    v != null && i != null -> listOf(v, i, v / i, v * i)
    v != null && r != null -> listOf(v, v / r, r, v * v / r)
    v != null && p != null -> listOf(v, p / v, v * v / p, p)
    i != null && r != null -> listOf(i * r, i, r, i * i * r)
    i != null && p != null -> listOf(p / i, i, p / (i * i), p)
    r != null && p != null -> listOf(sqrt(p * r), sqrt(p / r), r, p)
    else -> null
}

@Composable fun OhmLaw() {
    var vS by remember { mutableStateOf("") }; var iS by remember { mutableStateOf("") }
    var rS by remember { mutableStateOf("") }; var pS by remember { mutableStateOf("") }
    val res = ohm(parseEng(vS), parseEng(iS), parseEng(rS), parseEng(pS))
    Text("Fill in any two values and the rest are worked out.", fontSize = 13.sp)
    NumField("Voltage V", vS) { vS = it }
    NumField("Current I", iS) { iS = it }
    NumField("Resistance R", rS) { rS = it }
    NumField("Power P", pS) { pS = it }
    if (res == null) Text("Waiting for two values…", color = Ink.copy(alpha = 0.6f))
    else GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("V = ${fmtEng(res[0], "V")}", color = Gold, fontSize = 18.sp)
            Text("I = ${fmtEng(res[1], "A")}", color = Gold, fontSize = 18.sp)
            Text("R = ${fmtEng(res[2], "Ω")}", color = Gold, fontSize = 18.sp)
            Text("P = ${fmtEng(res[3], "W")}", color = Gold, fontSize = 18.sp)
        }
    }
    Chip("Clear") { vS = ""; iS = ""; rS = ""; pS = "" }
}

// ---------- resistor colour code ----------
private val DIGITS = listOf("Black" to 0xFF000000L, "Brown" to 0xFF8B4513L, "Red" to 0xFFE53935L, "Orange" to 0xFFFB8C00L, "Yellow" to 0xFFFDD835L,
    "Green" to 0xFF43A047L, "Blue" to 0xFF1E88E5L, "Violet" to 0xFF8E24AAL, "Grey" to 0xFF9E9E9EL, "White" to 0xFFFAFAFAL)
private val MULT = DIGITS + listOf("Gold" to 0xFFD4AF37L, "Silver" to 0xFFC0C0C0L)
private val TOL = listOf(Triple("Brown", 0xFF8B4513L, 1.0), Triple("Red", 0xFFE53935L, 2.0), Triple("Green", 0xFF43A047L, 0.5), Triple("Blue", 0xFF1E88E5L, 0.25),
    Triple("Violet", 0xFF8E24AAL, 0.1), Triple("Grey", 0xFF9E9E9EL, 0.05), Triple("Gold", 0xFFD4AF37L, 5.0), Triple("Silver", 0xFFC0C0C0L, 10.0))
private fun multOf(i: Int) = when { i < 10 -> 10.0.pow(i); i == 10 -> 0.1; else -> 0.01 }

@Composable private fun BandRow(label: String, options: List<Pair<String, Long>>, sel: Int, onSel: (Int) -> Unit) {
    Text("$label: ${options[sel].first}", fontSize = 12.sp, color = Ink.copy(alpha = 0.7f))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        options.forEachIndexed { i, (_, c) ->
            Box(Modifier.padding(4.dp).size(30.dp).clip(CircleShape).background(Color(c))
                .border(if (i == sel) 2.dp else 1.dp, if (i == sel) Gold else Ink.copy(alpha = 0.3f), CircleShape).clickable { onSel(i) })
        }
    }
}

@Composable fun ResistorCode() {
    var five by remember { mutableStateOf(false) }
    var d1 by remember { mutableIntStateOf(4) }; var d2 by remember { mutableIntStateOf(7) }; var d3 by remember { mutableIntStateOf(0) }
    var mi by remember { mutableIntStateOf(2) }; var ti by remember { mutableIntStateOf(6) }
    var valueIn by remember { mutableStateOf("4.7k") }
    val ohms = (if (five) d1 * 100 + d2 * 10 + d3 else d1 * 10 + d2) * multOf(mi)
    Row { Chip("4 bands", !five) { five = false }; Chip("5 bands", five) { five = true } }
    Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFFD7B98E))) {
        Row(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val cols = buildList { add(DIGITS[d1].second); add(DIGITS[d2].second); if (five) add(DIGITS[d3].second); add(MULT[mi].second); add(TOL[ti].second) }
            cols.forEach { c -> Box(Modifier.width(12.dp).fillMaxHeight().background(Color(c))) }
        }
    }
    BandRow("Band 1 (digit)", DIGITS, d1) { d1 = it }
    BandRow("Band 2 (digit)", DIGITS, d2) { d2 = it }
    if (five) BandRow("Band 3 (digit)", DIGITS, d3) { d3 = it }
    BandRow("Multiplier", MULT, mi) { mi = it }
    BandRow("Tolerance", TOL.map { it.first to it.second }, ti) { ti = it }
    Text("${fmtEng(ohms, "Ω")}  ±${TOL[ti].third}%", fontSize = 26.sp, color = Gold)
    SectionTitle("Value to colour bands")
    NumField("Resistance, e.g. 4.7k", valueIn) { valueIn = it }
    val v = parseEng(valueIn)
    val bands: List<String>? = v?.takeIf { it >= 0.1 && it < 1e11 }?.let { x ->
        var e = floor(log10(x)).toInt() - 1
        var mant = (x / 10.0.pow(e)).roundToInt()
        if (mant >= 100) { mant /= 10; e += 1 }
        val m = if (e >= 0) e else if (e == -1) 10 else if (e == -2) 11 else -1
        if (m < 0 || m > 11) null else listOf(DIGITS[mant / 10].first, DIGITS[mant % 10].first, MULT[m].first)
    }
    if (bands == null) Text("Enter a value between 0.1 Ω and 99 GΩ.", color = Ink.copy(alpha = 0.6f))
    else Text("4-band: " + bands.joinToString(" · ") + " · Gold (±5%)", color = Gold)
}

// ---------- series / parallel ----------
@Composable fun SeriesParallel() {
    var cap by remember { mutableStateOf(false) }
    var inp by remember { mutableStateOf("100, 220, 330") }
    val vals = inp.split(",", " ", ";").filter { it.isNotBlank() }.map { parseEng(it) }
    val ok = vals.isNotEmpty() && vals.all { it != null && it > 0 }
    Row { Chip("Resistors", !cap) { cap = false }; Chip("Capacitors", cap) { cap = true } }
    NumField("Values separated by commas", inp) { inp = it }
    if (!ok) Text("Enter positive values, for example 100, 4.7k, 330.", color = Ink.copy(alpha = 0.6f))
    else {
        val v = vals.map { it!! }
        val sum = v.sum(); val rec = 1.0 / v.sumOf { 1.0 / it }
        val unit = if (cap) "F" else "Ω"
        GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("In series: ${fmtEng(if (cap) rec else sum, unit)}", color = Gold, fontSize = 18.sp)
                Text("In parallel: ${fmtEng(if (cap) sum else rec, unit)}", color = Gold, fontSize = 18.sp)
            }
        }
    }
}

// ---------- RC / RL ----------
@Composable fun RcRl() {
    var rl by remember { mutableStateOf(false) }
    var rS by remember { mutableStateOf("") }; var xS by remember { mutableStateOf("") }; var fS by remember { mutableStateOf("") }
    val r = parseEng(rS); val x = parseEng(xS); val f = parseEng(fS)
    Row { Chip("RC filter", !rl) { rl = false }; Chip("RL filter", rl) { rl = true } }
    Text("Fill in any two values.", fontSize = 13.sp)
    NumField("Resistance R (Ω)", rS) { rS = it }
    NumField(if (rl) "Inductance L (H)" else "Capacitance C (F)", xS) { xS = it }
    NumField("Cut-off frequency (Hz)", fS) { fS = it }
    val lines = mutableListOf<String>()
    if (!rl) when {
        r != null && x != null -> { lines.add("Cut-off: ${fmtEng(1 / (2 * PI * r * x), "Hz")}"); lines.add("Time constant τ = ${fmtEng(r * x, "s")}") }
        r != null && f != null -> lines.add("C = ${fmtEng(1 / (2 * PI * r * f), "F")}")
        x != null && f != null -> lines.add("R = ${fmtEng(1 / (2 * PI * x * f), "Ω")}")
    } else when {
        r != null && x != null -> { lines.add("Cut-off: ${fmtEng(r / (2 * PI * x), "Hz")}"); lines.add("Time constant τ = ${fmtEng(x / r, "s")}") }
        r != null && f != null -> lines.add("L = ${fmtEng(r / (2 * PI * f), "H")}")
        x != null && f != null -> lines.add("R = ${fmtEng(2 * PI * f * x, "Ω")}")
    }
    if (lines.isEmpty()) Text("Waiting for two values…", color = Ink.copy(alpha = 0.6f))
    else GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { lines.forEach { Text(it, color = Gold, fontSize = 18.sp) } }
    }
}

// ---------- decibels ----------
@Composable fun Decibels() {
    var power by remember { mutableStateOf(false) }
    var ratio by remember { mutableStateOf("") }; var db by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }; var dbm by remember { mutableStateOf("") }
    val k = if (power) 10.0 else 20.0
    Row { Chip("Voltage ratio (20 log)", !power) { power = false }; Chip("Power ratio (10 log)", power) { power = true } }
    NumField("Ratio (output / input)", ratio) { ratio = it; db = "" }
    NumField("Decibels (dB)", db) { db = it; ratio = "" }
    val rr = ratio.toDoubleOrNull(); val dd = db.toDoubleOrNull()
    if (rr != null && rr > 0) Text("= ${fmtNum(k * log10(rr))} dB", color = Gold, fontSize = 18.sp)
    if (dd != null) Text("= ratio of ${fmtNum(10.0.pow(dd / k))}", color = Gold, fontSize = 18.sp)
    SectionTitle("dBm (relative to 1 mW)")
    NumField("Power, e.g. 5m for 5 mW", pw) { pw = it; dbm = "" }
    NumField("Level (dBm)", dbm) { dbm = it; pw = "" }
    val p = parseEng(pw); val dm = dbm.toDoubleOrNull()
    if (p != null && p > 0) Text("= ${fmtNum(10 * log10(p / 1e-3))} dBm", color = Gold, fontSize = 18.sp)
    if (dm != null) Text("= ${fmtEng(1e-3 * 10.0.pow(dm / 10), "W")}", color = Gold, fontSize = 18.sp)
}

// ---------- scientific calculator ----------
@Composable fun Calculator() {
    var expr by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var ans by remember { mutableDoubleStateOf(0.0) }
    var err by remember { mutableStateOf("") }
    fun run() {
        val src = expr.trim(); if (src.isEmpty()) return
        try {
            val v = evalExpr(src.replace("ans", "(" + java.math.BigDecimal(ans).toPlainString() + ")"), 0.0)
            if (v.isNaN() || v.isInfinite()) { err = "Undefined result (division by zero?)"; return }
            ans = v; err = ""; history = (listOf(src to fmtNum(v)) + history).take(30); expr = ""
        } catch (e: Exception) { err = "Can't read that expression" }
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(expr, { expr = it }, label = { Text("Expression, e.g. sin(30*deg) + 2^8") }, singleLine = true, shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { run() }), modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("sin(", "cos(", "tan(", "asin(", "acos(", "atan(", "sqrt(", "ln(", "log(", "exp(", "abs(", "^", "pi", "e", "deg", "ans", "(", ")").forEach { t -> Chip(t) { expr += t } }
        }
        Row { Button({ run() }) { Text("=") }; Chip("Clear") { expr = ""; err = "" }; Chip("Clear history") { history = emptyList() } }
        if (err.isNotBlank()) Text(err, color = Color(0xFFE57373))
        Text("Angles are in radians. Multiply by deg to use degrees, for example sin(30*deg). ans is the last result.", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
        history.forEach { (q, a) ->
            Column(Modifier.fillMaxWidth().clickable { expr += a }.padding(vertical = 4.dp)) {
                Text(q, fontSize = 13.sp, color = Ink.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
                Text("= $a", fontSize = 20.sp, color = Gold)
            }
        }
    }
}
