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
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

@Composable
fun CalculatorAccessGate(
    onUnlock: () -> Unit,
    onSecretCode: (String) -> Unit
) {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var hasError by remember { mutableStateOf(false) }
    var inverse by remember { mutableStateOf(false) }
    var degrees by remember { mutableStateOf(true) }
    var memory by remember { mutableStateOf(0.0) }
    var answer by remember { mutableStateOf(0.0) }

    val portraitKeys = listOf(
        listOf("AC", "DEL", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("00", "0", ".", "=")
    )
    val scientificKeys = listOf(
        listOf("2nd", "DEG", "sin", "cos", "tan", "ln", "log", "√", "∛"),
        listOf("x²", "xʸ", "1/x", "(", ")", "π", "e", "!", "DEL"),
        listOf("MC", "MR", "M+", "M-", "MS", "%", "±", "ANS", "AC"),
        listOf("7", "8", "9", "÷", "4", "5", "6", "×", "00"),
        listOf("1", "2", "3", "−", "0", ".", "+", "=", "10ˣ")
    )

    fun currentValue(): Double = CalculatorMath.evaluate(expression, degrees, answer)

    fun press(key: String) {
        fun append(value: String) {
            if (expression.length + value.length <= 96) {
                expression += value
                result = null
                hasError = false
            }
        }
        fun clear() {
            expression = ""
            result = null
            hasError = false
        }
        when (key) {
            "AC", "MC" -> {
                if (key == "MC") memory = 0.0 else clear()
            }
            "DEL" -> { expression = expression.dropLast(1); result = null; hasError = false }
            "2nd" -> inverse = !inverse
            "DEG" -> degrees = !degrees
            "π" -> append("pi")
            "e" -> append("e")
            "ANS" -> append(CalculatorMath.format(answer))
            "x²" -> append("^2")
            "xʸ" -> append("^")
            "1/x" -> append("1/(")
            "10ˣ" -> append("10^(")
            "√" -> append("sqrt(")
            "∛" -> append("cbrt(")
            "sin", "cos", "tan" -> {
                val function = if (inverse) "a$key" else key
                append("$function(")
                inverse = false
            }
            "ln", "log" -> append("$key(")
            "!" -> append("!")
            "%" -> append("%")
            "±" -> {
                if (expression.isNotBlank()) {
                    expression = if (expression.startsWith("-(") && expression.endsWith(")")) {
                        expression.substring(2, expression.length - 1)
                    } else {
                        "-($expression)"
                    }
                    result = null
                    hasError = false
                } else {
                    append("-")
                }
            }
            "M+" -> runCatching { memory += currentValue() }.onSuccess { result = "M = ${CalculatorMath.format(memory)}" }
            "M-" -> runCatching { memory -= currentValue() }.onSuccess { result = "M = ${CalculatorMath.format(memory)}" }
            "MS" -> runCatching { memory = currentValue() }.onSuccess { result = "M = ${CalculatorMath.format(memory)}" }
            "MR" -> append(CalculatorMath.format(memory))
            "=" -> {
                val value = expression.trim()
                when {
                    isValidAccessPin(value) -> { clear(); onUnlock() }
                    value in SecretCodeRegistry.codes -> { clear(); onSecretCode(value) }
                    value.isBlank() -> Unit
                    else -> try {
                        val calculated = currentValue()
                        answer = calculated
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
                append(token)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101318)) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CalculatorDisplay(
                        expression, result, hasError,
                        Modifier.weight(0.78f).fillMaxHeight(),
                        title = "Calculator",
                        mode = if (degrees) "DEG" else "RAD",
                        memoryActive = memory != 0.0
                    )
                    CalculatorKeypad(
                        scientificKeys, ::press, Modifier.weight(2.22f),
                        compact = true,
                        inverse = inverse
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CalculatorDisplay(expression, result, hasError, Modifier.weight(0.9f).fillMaxWidth())
                    CalculatorKeypad(portraitKeys, ::press, Modifier.weight(1.5f))
                }
            }
        }
    }
}

@Composable
private fun CalculatorDisplay(
    expression: String,
    result: String?,
    error: Boolean,
    modifier: Modifier = Modifier,
    title: String = "NSiO CALCULATOR",
    mode: String? = null,
    memoryActive: Boolean = false
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color(0xFF8993A2), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
            if (mode != null) {
                Text(
                    text = mode + if (memoryActive) "  •  M" else "",
                    color = Color(0xFF9DA8B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = expression.ifBlank { "0" }.replace("*", "×").replace("/", "÷").replace("-", "−"),
            color = Color(0xFFF3F5F8),
            fontSize = if (mode == null) 36.sp else 30.sp,
            fontWeight = FontWeight.Light,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
        if (result != null) {
            Text(
                result,
                color = if (error) Color(0xFFFF7777) else Color(0xFF9DA8B8),
                fontSize = 16.sp,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // PIN instructions are intentionally omitted: the keypad is the only access UI.
    }
}

@Composable
private fun CalculatorKeypad(
    keys: List<List<String>>,
    onPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    inverse: Boolean = false
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
            ) {
                row.forEach { key ->
                    val special = key in setOf(
                        "AC", "DEL", "%", "÷", "×", "−", "+", "2nd", "DEG",
                        "MC", "MR", "M+", "M-", "MS", "±", "ANS"
                    )
                    val equals = key == "="
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight().clickable { onPress(key) }.focusable(),
                        shape = RoundedCornerShape(if (compact) 10.dp else 18.dp),
                        color = when {
                            equals -> Color(0xFFB8C7DA)
                            key == "2nd" && inverse -> Color(0xFF526B8C)
                            special -> Color(0xFF242B35)
                            compact && key in setOf("sin", "cos", "tan", "ln", "log", "√", "∛", "π", "e", "!", "x²", "xʸ") -> Color(0xFF26394A)
                            else -> Color(0xFF1A2029)
                        },
                        tonalElevation = 0.dp,
                        shadowElevation = 1.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                key,
                                color = if (equals) Color(0xFF111820) else Color(0xFFE8EDF4),
                                fontSize = when {
                                    compact -> if (key.length > 3) 10.sp else 13.sp
                                    key.length > 2 -> 16.sp
                                    else -> 22.sp
                                },
                                fontWeight = if (equals) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
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
        Entry("101", "Developer diagnostics", "Menampilkan panel diagnostik aplikasi dan status komponen nyata"),
        Entry("103", "Panduan gestur", "Menampilkan panduan gestur dan navigasi"),
        Entry("104", "Mode tampilan", "Mengganti mode tampilan NSiO"),
        Entry("111", "Riwayat crash", "Menampilkan log crash terakhir"),
        Entry("123", "Bersihkan cache", "Menghapus file sementara dengan konfirmasi"),
        Entry("200", "Info aplikasi", "Membuka detail aplikasi di Pengaturan Android"),
        Entry("202", "Pengaturan notifikasi", "Membuka pengaturan notifikasi NSiO"),
        Entry("333", "Informasi sistem", "Menampilkan informasi perangkat dan Android"),
        Entry("444", "Diagnostik media player", "Menampilkan status engine, playback, buffering, durasi, posisi, dan error"),
        Entry("456", "Refresh sumber", "Memulai penyegaran sumber playlist"),
        Entry("555", "Informasi jaringan", "Menampilkan status konektivitas"),
        Entry("666", "Informasi izin", "Memeriksa status izin yang dideklarasikan aplikasi"),
        Entry("777", "Penyimpanan", "Menghitung ukuran direktori data NSiO dan kapasitas partisi secara terpisah"),
        Entry("888", "Statistik aplikasi", "Menampilkan jumlah media dan playlist")
    )
    val codes: Set<String> = entries.map { it.code }.toSet()
    val allCodes: String = entries.joinToString("\n") { "${it.code} =  ${it.title}\n${it.description}" }
}

private fun isValidAccessPin(value: String): Boolean {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return digest == "03122084549941efe9eb5c75f4dcdeb82654e086fb54da8fa4e92d06680f4e57"
}

internal object CalculatorMath {
    fun evaluate(expression: String, degrees: Boolean = true, answer: Double = 0.0): Double {
        val parser = Parser(expression, degrees, answer)
        val value = parser.expression()
        parser.skipSpaces()
        require(parser.atEnd()) { "Unexpected token" }
        require(value.isFinite()) { "Invalid result" }
        return value
    }

    fun format(value: Double): String {
        require(value.isFinite())
        return if (value % 1.0 == 0.0 && value >= Long.MIN_VALUE.toDouble() && value <= Long.MAX_VALUE.toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    private class Parser(
        private val source: String,
        private val degrees: Boolean,
        private val answer: Double
    ) {
        private var index = 0
        fun atEnd() = index >= source.length
        fun skipSpaces() { while (!atEnd() && source[index].isWhitespace()) index++ }

        fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                when {
                    consume('+') -> value += term()
                    consume('-') -> value -= term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = unary()
            while (true) {
                skipSpaces()
                when {
                    consume('*') -> value *= unary()
                    consume('/') -> { val divisor = unary(); require(divisor != 0.0); value /= divisor }
                    consume('%') -> { value /= 100.0 }
                    else -> return value
                }
            }
        }

        // Exponentiation binds more tightly than a leading sign and remains right-associative:
        // -2^2 == -(2^2), while 2^-2 and 2^3^2 are also parsed correctly.
        private fun power(): Double {
            val base = postfix()
            skipSpaces()
            return if (consume('^')) base.pow(unary()) else base
        }

        private fun unary(): Double {
            skipSpaces()
            if (consume('+')) return unary()
            if (consume('-')) return -unary()
            return power()
        }

        private fun postfix(): Double {
            var value = primary()
            while (true) {
                skipSpaces()
                if (consume('!')) value = factorial(value) else return value
            }
        }

        private fun primary(): Double {
            skipSpaces()
            if (consume('(')) {
                val value = expression()
                skipSpaces()
                require(consume(')'))
                return value
            }
            if (!atEnd() && (source[index].isDigit() || source[index] == '.')) return number()
            val identifier = identifier()
            return when (identifier) {
                "pi" -> PI
                "e" -> kotlin.math.E
                "ans" -> answer
                "sqrt" -> sqrt(argument())
                "cbrt" -> Math.cbrt(argument())
                "abs" -> abs(argument())
                "exp" -> exp(argument())
                "ln" -> ln(positiveArgument())
                "log" -> log10(positiveArgument())
                "log2" -> kotlin.math.log2(positiveArgument())
                "sin" -> sin(toRadians(argument()))
                "cos" -> cos(toRadians(argument()))
                "tan" -> tan(toRadians(argument()))
                "asin" -> fromRadians(asin(argument().also { require(it in -1.0..1.0) }))
                "acos" -> fromRadians(acos(argument().also { require(it in -1.0..1.0) }))
                "atan" -> fromRadians(atan(argument()))
                else -> throw IllegalArgumentException("Unknown function")
            }
        }

        private fun argument(): Double {
            skipSpaces()
            require(consume('('))
            val value = expression()
            skipSpaces()
            require(consume(')'))
            require(value.isFinite())
            return value
        }

        private fun positiveArgument(): Double = argument().also { require(it > 0.0) }
        private fun toRadians(value: Double) = if (degrees) Math.toRadians(value) else value
        private fun fromRadians(value: Double) = if (degrees) Math.toDegrees(value) else value

        private fun number(): Double {
            val start = index
            var decimal = false
            while (!atEnd()) {
                val char = source[index]
                if (char.isDigit()) index++
                else if (char == '.' && !decimal) { decimal = true; index++ }
                else break
            }
            if (!atEnd() && (source[index] == 'E' || source[index] == 'e') && index > start) {
                val exponentStart = index++
                if (!atEnd() && (source[index] == '+' || source[index] == '-')) index++
                val digitsStart = index
                while (!atEnd() && source[index].isDigit()) index++
                if (digitsStart == index) index = exponentStart
            }
            return source.substring(start, index).toDouble()
        }

        private fun identifier(): String {
            val start = index
            while (!atEnd() && source[index].isLetter()) index++
            require(index > start) { "Expected number or function" }
            return source.substring(start, index).lowercase()
        }

        private fun factorial(value: Double): Double {
            require(value >= 0.0 && value <= 170.0 && value % 1.0 == 0.0)
            var result = 1.0
            for (number in 2..value.toInt()) result *= number
            return result
        }

        private fun consume(char: Char): Boolean {
            if (!atEnd() && source[index] == char) { index++; return true }
            return false
        }
    }
}
