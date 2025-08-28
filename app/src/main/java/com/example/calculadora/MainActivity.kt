package com.example.calculadora

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.ArrayDeque
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var tvExpression: TextView
    private lateinit var tvResult: TextView
    private var expression: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvExpression = findViewById(R.id.tvExpression)
        tvResult = findViewById(R.id.tvResult)

        val buttons = listOf(
            R.id.btn0, R.id.btn00, R.id.btn1, R.id.btn2, R.id.btn3,
            R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9,
            R.id.btnPlus, R.id.btnMinus, R.id.btnMultiply, R.id.btnDivide,
            R.id.btnPercent, R.id.btnDot
        )

        // Clique genérico pros botões
        buttons.forEach { id ->
            findViewById<MaterialButton>(id).setOnClickListener { btn ->
                val t = (btn as MaterialButton).text.toString()
                expression += t
                tvExpression.text = expression
            }
        }

        // Clear (C)
        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            expression = ""
            tvExpression.text = ""
            tvResult.text = "0"
        }

        // Delete (DEL)
        findViewById<MaterialButton>(R.id.btnDel).setOnClickListener {
            if (expression.isNotEmpty()) {
                expression = expression.dropLast(1)
                tvExpression.text = expression
            }
        }

        // Igual (=)
        findViewById<MaterialButton>(R.id.btnEquals).setOnClickListener {
            val out = evalSafe(expression)
            tvResult.text = out
        }
    }

    // -------- Avaliador sem libs: tokeniza -> Shunting-yard -> avalia RPN --------

    private fun evalSafe(expr: String): String = try {
        val res = evaluate(expr)
        if (res.isNaN() || res.isInfinite()) "Erro"
        else formatResult(res)
    } catch (e: Exception) {
        "Erro"
    }

    private fun formatResult(value: Double): String {
        val rounded = String.format("%.10f", value).trimEnd('0').trimEnd('.')
        // mostra inteiro se “parecer” inteiro
        return if (abs(rounded.toDoubleOrNull() ?: value - value.roundToIntSafe()) < 1e-10 &&
            abs(value - value.roundToIntSafe()) < 1e-10
        ) value.roundToIntSafe().toString() else rounded
    }

    private fun Double.roundToIntSafe(): Int = if (this >= 0) (this + 0.0000000001).toInt() else (this - 0.0000000001).toInt()

    private enum class TokType { NUM, OP, PAREN }
    private data class Tok(val type: TokType, val s: String)

    private fun evaluate(raw: String): Double {
        val clean = raw
            .replace("×", "*")
            .replace("÷", "/")
            .replace(",", ".") // caso o usuário digite vírgula

        val tokens = tokenize(clean)
        val rpn = toRPN(tokens)
        return evalRPN(rpn)
    }

    // Converte string em tokens (números/operadores/parênteses)
    private fun tokenize(s: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        var lastWasOpOrStart = true // para detectar unário (-3, +2)

        while (i < s.length) {
            val c = s[i]

            when {
                c.isWhitespace() -> i++

                // número (com suporte a sinal unário e ponto)
                c.isDigit() || (lastWasOpOrStart && (c == '+' || c == '-')) || c == '.' -> {
                    val start = i
                    var hasDot = (c == '.')
                    var j = i + 1

                    // Se começou com sinal unário, continue
                    if (lastWasOpOrStart && (c == '+' || c == '-')) {
                        // depois do sinal, consome dígitos e ponto
                        while (j < s.length && (s[j].isDigit() || (s[j] == '.' && !hasDot))) {
                            if (s[j] == '.') hasDot = true
                            j++
                        }
                    } else {
                        // sem sinal inicial
                        while (j < s.length && (s[j].isDigit() || (s[j] == '.' && !hasDot))) {
                            if (s[j] == '.') hasDot = true
                            j++
                        }
                    }

                    val numStr = s.substring(start, j)
                    // valida número (evita tokenizar apenas "+" ou "-")
                    if (numStr == "+" || numStr == "-") {
                        // era operador, não número
                        out.add(Tok(TokType.OP, numStr))
                        lastWasOpOrStart = true
                        i = j
                        continue
                    }

                    out.add(Tok(TokType.NUM, numStr))
                    lastWasOpOrStart = false
                    i = j
                }

                // operadores
                c == '+' || c == '-' || c == '*' || c == '/' || c == '%' -> {
                    out.add(Tok(TokType.OP, c.toString()))
                    lastWasOpOrStart = true
                    i++
                }

                // parênteses (se quiser habilitar futuramente)
                c == '(' || c == ')' -> {
                    out.add(Tok(TokType.PAREN, c.toString()))
                    lastWasOpOrStart = (c == '(')
                    i++
                }

                else -> {
                    // caractere desconhecido -> erro
                    throw IllegalArgumentException("Caractere inválido: '$c'")
                }
            }
        }
        return out
    }

    private fun precedence(op: String): Int = when (op) {
        "%" -> 3      // unário pós-fixo
        "*", "/" -> 2
        "+", "-" -> 1
        else -> -1
    }

    private fun isRightAssociative(op: String): Boolean = false // todos à esquerda; % é pós-fixo

    private fun toRPN(tokens: List<Tok>): List<Tok> {
        val output = mutableListOf<Tok>()
        val stack = ArrayDeque<Tok>()

        for (t in tokens) {
            when (t.type) {
                TokType.NUM -> output.add(t)
                TokType.OP -> {
                    // operador pós-fixo (%) tem precedência máxima e não desempilha
                    if (t.s == "%") {
                        output.add(t) // empilha direto na saída
                        continue
                    }

                    while (stack.isNotEmpty() && stack.peek().type == TokType.OP) {
                        val top = stack.peek().s
                        val p1 = precedence(t.s)
                        val p2 = precedence(top)
                        if ((p1 < p2) || (p1 == p2 && !isRightAssociative(t.s))) {
                            output.add(stack.pop())
                        } else break
                    }
                    stack.push(t)
                }
                TokType.PAREN -> {
                    if (t.s == "(") {
                        stack.push(t)
                    } else {
                        while (stack.isNotEmpty() && stack.peek().s != "(") {
                            output.add(stack.pop())
                        }
                        if (stack.isEmpty() || stack.peek().s != "(") {
                            throw IllegalArgumentException("Parênteses desbalanceados")
                        }
                        stack.pop() // remove "("
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
                        val a = st.popOrErr()
                        st.push(a / 100.0) // 50% -> 0.5
                    } else {
                        val b = st.popOrErr()
                        val a = st.popOrErr()
                        val r = when (t.s) {
                            "+" -> a + b
                            "-" -> a - b
                            "*" -> a * b
                            "/" -> a / b
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
