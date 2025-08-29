package com.example.calculadora

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.ArrayDeque
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var tvDisplay: TextView
    private var expression: String = ""
    private var justEvaluated: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvDisplay = findViewById(R.id.tvResult)

        val buttons = listOf(
            R.id.btn0, R.id.btn00, R.id.btn1, R.id.btn2, R.id.btn3,
            R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9,
            R.id.btnPlus, R.id.btnMinus, R.id.btnMultiply, R.id.btnDivide,
            R.id.btnPercent, R.id.btnDot
        )

        // Digitação
        buttons.forEach { id ->
            findViewById<MaterialButton>(id).setOnClickListener { btn ->
                val t = (btn as MaterialButton).text.toString()
                val isNumberChunk = (t == "00" || t == "." || t.all { it.isDigit() })

                // Após "=", se o usuário digitar número, começamos nova expressão
                expression = if (justEvaluated && isNumberChunk) t else expression + t
                justEvaluated = false
                tvDisplay.text = expression.ifBlank { "0" }
            }
        }

        // Clear
        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            expression = ""
            justEvaluated = false
            tvDisplay.text = "0"
        }

        // Delete
        findViewById<MaterialButton>(R.id.btnDel).setOnClickListener {
            if (expression.isNotEmpty()) {
                expression = expression.dropLast(1)
                tvDisplay.text = if (expression.isEmpty()) "0" else expression
            }
        }

        // Igual
        findViewById<MaterialButton>(R.id.btnEquals).setOnClickListener {
            val out = evalSafe(expression)
            tvDisplay.text = out

            // Se for erro, não “promove” o resultado para a expressão
            if (out != getString(R.string.error_div_zero) &&
                out != getString(R.string.error_invalid_expr)) {
                expression = out
                justEvaluated = true
            } else {
                justEvaluated = false
            }
        }
    }

    // ---------- Avaliador com mensagens claras ----------
    private fun evalSafe(expr: String): String {
        if (expr.isBlank()) return "0"
        return try {
            val res = evaluate(expr)
            when {
                res.isNaN() -> getString(R.string.error_invalid_expr)
                res.isInfinite() -> getString(R.string.error_div_zero) // fallback
                else -> formatResult(res)
            }
        } catch (e: ArithmeticException) {
            if (e.message == "DIV_ZERO") getString(R.string.error_div_zero)
            else getString(R.string.error_invalid_expr)
        } catch (_: Exception) {
            getString(R.string.error_invalid_expr)
        }
    }

    private fun formatResult(value: Double): String {
        val rounded = String.format("%.10f", value).trimEnd('0').trimEnd('.')
        val asDouble = rounded.toDoubleOrNull()
        return if (asDouble != null && abs(asDouble - asDouble.toInt().toDouble()) < 1e-10)
            asDouble.toInt().toString()
        else
            rounded
    }

    private fun Double.roundToIntSafe(): Int =
        if (this >= 0) (this + 0.0000000001).toInt() else (this - 0.0000000001).toInt()

    private enum class TokType { NUM, OP, PAREN }
    private data class Tok(val type: TokType, val s: String)

    private fun evaluate(raw: String): Double {
        val clean = raw
            .replace("×", "*")
            .replace("÷", "/")
            .replace(",", ".")
        val tokens = tokenize(clean)
        val rpn = toRPN(tokens)
        return evalRPN(rpn)
    }

    private fun tokenize(s: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        var lastWasOpOrStart = true

        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++

                c.isDigit() || (lastWasOpOrStart && (c == '+' || c == '-')) || c == '.' -> {
                    val start = i
                    var hasDot = (c == '.')
                    var j = i + 1
                    if (lastWasOpOrStart && (c == '+' || c == '-')) {
                        while (j < s.length && (s[j].isDigit() || (s[j] == '.' && !hasDot))) {
                            if (s[j] == '.') hasDot = true; j++
                        }
                    } else {
                        while (j < s.length && (s[j].isDigit() || (s[j] == '.' && !hasDot))) {
                            if (s[j] == '.') hasDot = true; j++
                        }
                    }
                    val numStr = s.substring(start, j)
                    if (numStr == "+" || numStr == "-") {
                        out.add(Tok(TokType.OP, numStr)); lastWasOpOrStart = true; i = j; continue
                    }
                    out.add(Tok(TokType.NUM, numStr))
                    lastWasOpOrStart = false; i = j
                }

                c == '+' || c == '-' || c == '*' || c == '/' || c == '%' -> {
                    out.add(Tok(TokType.OP, c.toString()))
                    lastWasOpOrStart = true; i++
                }

                c == '(' || c == ')' -> {
                    out.add(Tok(TokType.PAREN, c.toString()))
                    lastWasOpOrStart = (c == '('); i++
                }

                else -> throw IllegalArgumentException("Caractere inválido: '$c'")
            }
        }
        return out
    }

    private fun precedence(op: String): Int = when (op) {
        "%" -> 3
        "*", "/" -> 2
        "+", "-" -> 1
        else -> -1
    }
    private fun isRightAssociative(op: String): Boolean = false

    private fun toRPN(tokens: List<Tok>): List<Tok> {
        val output = mutableListOf<Tok>()
        val stack = ArrayDeque<Tok>()
        for (t in tokens) {
            when (t.type) {
                TokType.NUM -> output.add(t)
                TokType.OP -> {
                    if (t.s == "%") { output.add(t); continue }
                    while (stack.isNotEmpty() && stack.peek().type == TokType.OP) {
                        val top = stack.peek().s
                        val p1 = precedence(t.s); val p2 = precedence(top)
                        if ((p1 < p2) || (p1 == p2 && !isRightAssociative(t.s))) {
                            output.add(stack.pop())
                        } else break
                    }
                    stack.push(t)
                }
                TokType.PAREN -> {
                    if (t.s == "(") stack.push(t) else {
                        while (stack.isNotEmpty() && stack.peek().s != "(") output.add(stack.pop())
                        if (stack.isEmpty() || stack.peek().s != "(") throw IllegalArgumentException("Parênteses desbalanceados")
                        stack.pop()
                    }
                }
            }
        }
        while (stack.isNotEmpty()) {
            val top = stack.pop()
            if (top.type == TokType.PAREN) throw IllegalArgumentException("Parênteses desbalanceados")
            output.add(top)
        }
        return output
    }

    private fun evalRPN(rpn: List<Tok>): Double {
        val st = ArrayDeque<Double>()
        for (t in rpn) {
            when (t.type) {
                TokType.NUM -> st.push(t.s.toDouble())
                TokType.OP -> {
                    if (t.s == "%") {
                        val a = st.popOrErr(); st.push(a / 100.0)
                    } else {
                        val b = st.popOrErr()
                        val a = st.popOrErr()
                        val r = when (t.s) {
                            "+" -> a + b
                            "-" -> a - b
                            "*" -> a * b
                            "/" -> {
                                if (abs(b) < 1e-12) throw ArithmeticException("DIV_ZERO")
                                a / b
                            }
                            else -> error("Operador inválido")
                        }
                        st.push(r)
                    }
                }
                else -> error("Token inesperado em RPN")
            }
        }
        return st.popOrErr().also {
            if (st.isNotEmpty()) throw IllegalStateException("Expressão inválida")
        }
    }

    private fun ArrayDeque<Double>.popOrErr(): Double =
        if (isEmpty()) throw IllegalArgumentException("Expressão inválida")
        else removeFirst()
}
