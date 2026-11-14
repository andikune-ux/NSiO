package com.example

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CalculatorAccessGate(
    onUnlock: () -> Unit,
    onSecretCode: (String) -> Unit
) {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var hasError by remember { mutableStateOf(false) }
    val keys = listOf(
        listOf("AC", "DEL", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("00", "0", ".", "=")
    )
    fun press(key: String) {
        when (key) {
            "AC" -> { expression = ""; result = null; hasError = false }
            "DEL" -> { expression = expression.dropLast(1); result = null; hasError = false }
            "=" -> {
                val value = expression.trim()
                when {
                    value == "140399" -> { expression = ""; result = null; onUnlock() }
                    value in SecretCodeRegistry.codes -> { expression = ""; result = null; onSecretCode(value) }
                    value.isBlank() -> Unit
                    else -> try {
                        val calculated = CalculatorMath.evaluate(value)
                        result = CalculatorMath.format(calculated)
                        expression = result.orEmpty()
                        hasError = false
                    } catch (_: Exception) {
                        result = "Error"
                        hasError = true
                    }
                }
            }
            else -> {
                val token = when (key) { "×" -> "*"; "÷" -> "/"; "−" -> "-"; else -> key }
                if (expression.length < 48) { expression += token; result = null; hasError = false }
            }
        }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101318)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CalculatorDisplay(expression, result, hasError, Modifier.weight(0.9f).fillMaxHeight())
                    CalculatorKeypad(keys, ::press, Modifier.weight(1.1f))
                }
            } else {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CalculatorDisplay(expression, result, hasError, Modifier.weight(0.9f).fillMaxWidth())
                    CalculatorKeypad(keys, ::press, Modifier.weight(1.5f))
                }
            }
        }
    }
}

@Composable
private fun CalculatorDisplay(expression: String, result: String?, error: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End
    ) {
        Text("NSiO CALCULATOR", color = Color(0xFF8993A2), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = expression.ifBlank { "0" }.replace("*", "×").replace("/", "÷").replace("-", "−"),
            color = Color(0xFFF3F5F8), fontSize = 36.sp, fontWeight = FontWeight.Light,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End
        )
        if (result != null) Text(result, color = if (error) Color(0xFFFF7777) else Color(0xFF9DA8B8), fontSize = 17.sp, textAlign = TextAlign.End)
        Spacer(Modifier.height(10.dp))
        Text("Masukkan PIN lalu tekan = untuk membuka", color = Color(0xFF727D8D), fontSize = 11.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun CalculatorKeypad(keys: List<List<String>>, onPress: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    val special = key in setOf("AC", "DEL", "%", "÷", "×", "−", "+")
                    val equals = key == "="
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight().clickable { onPress(key) }.focusable(),
                        shape = RoundedCornerShape(18.dp),
                        color = when { equals -> Color(0xFFB8C7DA); special -> Color(0xFF242B35); else -> Color(0xFF1A2029) },
                        tonalElevation = 0.dp,
                        shadowElevation = 1.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(key, color = if (equals) Color(0xFF111820) else Color(0xFFE8EDF4), fontSize = if (key.length > 2) 16.sp else 22.sp, fontWeight = if (equals) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

object SecretCodeRegistry {
    data class Entry(val code: String, val title: String, val description: String)
    val entries = listOf(
        Entry("000", "Daftar kode rahasia", "Menampilkan daftar kode yang tersedia"),
        Entry("101", "Developer Mode", "Mengaktifkan atau menonaktifkan penanda Developer Mode"),
        Entry("103", "Panduan gestur", "Menampilkan panduan gestur dan navigasi"),
        Entry("104", "Mode tampilan", "Mengganti mode tampilan NSiO"),
        Entry("111", "Riwayat crash", "Menampilkan log crash terakhir"),
        Entry("123", "Bersihkan cache", "Menghapus file sementara dengan konfirmasi"),
        Entry("200", "Info aplikasi", "Membuka detail aplikasi di Pengaturan Android"),
        Entry("202", "Pengaturan notifikasi", "Membuka pengaturan notifikasi NSiO"),
        Entry("333", "Informasi sistem", "Menampilkan informasi perangkat dan Android"),
        Entry("444", "Diagnostik media player", "Menampilkan status pemutar media"),
        Entry("456", "Refresh sumber", "Memulai penyegaran sumber playlist"),
        Entry("555", "Informasi jaringan", "Menampilkan status konektivitas"),
        Entry("666", "Informasi izin", "Menampilkan panduan pemeriksaan izin"),
        Entry("777", "Penyimpanan", "Menampilkan ringkasan penyimpanan aplikasi"),
        Entry("888", "Statistik aplikasi", "Menampilkan jumlah media dan playlist")
    )
    val codes: Set<String> = entries.map { it.code }.toSet()
    val allCodes: String = entries.joinToString("\n") { "${it.code} =  ${it.title}\n${it.description}" }
}

private object CalculatorMath {
    fun evaluate(expression: String): Double {
        val parser = Parser(expression)
        val value = parser.expression()
        parser.skipSpaces()
        require(parser.atEnd()) { "Unexpected token" }
        require(value.isFinite()) { "Invalid result" }
        return value
    }
    fun format(value: Double): String {
        require(value.isFinite())
        return if (value % 1.0 == 0.0 && value >= Long.MIN_VALUE.toDouble() && value <= Long.MAX_VALUE.toDouble()) value.toLong().toString() else value.toString()
    }
    private class Parser(private val source: String) {
        private var index = 0
        fun atEnd() = index >= source.length
        fun skipSpaces() { while (!atEnd() && source[index].isWhitespace()) index++ }
        fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                when { consume('+') -> value += term(); consume('-') -> value -= term(); else -> return value }
            }
        }
        private fun term(): Double {
            var value = factor()
            while (true) {
                skipSpaces()
                when {
                    consume('*') -> value *= factor()
                    consume('/') -> { val d = factor(); require(d != 0.0); value /= d }
                    consume('%') -> { val d = factor(); require(d != 0.0); value %= d }
                    else -> return value
                }
            }
        }
        private fun factor(): Double {
            skipSpaces()
            if (consume('+')) return factor()
            if (consume('-')) return -factor()
            if (consume('(')) {
                val value = expression()
                skipSpaces()
                require(consume(')'))
                return value
            }
            val start = index
            var decimal = false
            while (!atEnd()) {
                val c = source[index]
                if (c.isDigit()) index++
                else if (c == '.' && !decimal) { decimal = true; index++ }
                else break
            }
            require(index > start)
            return source.substring(start, index).toDouble()
        }
        private fun consume(c: Char): Boolean {
            if (!atEnd() && source[index] == c) { index++; return true }
            return false
        }
    }
}
