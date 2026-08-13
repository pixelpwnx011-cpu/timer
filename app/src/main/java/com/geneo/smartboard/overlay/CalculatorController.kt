package com.geneo.smartboard.overlay

import android.view.View
import android.widget.Button
import android.widget.TextView
import java.text.DecimalFormat

/**
 * Wires up a basic 4-function calculator (with %, decimal point, backspace, clear)
 * to the buttons in overlay_calculator.xml. Keeps its own small piece of state
 * (current operand, pending operator, running total) rather than parsing a full
 * expression string, which keeps behaviour predictable for quick on-board sums.
 */
class CalculatorController(root: View) {

    private val tvExpression: TextView = root.findViewById(R.id.tvCalcExpression)
    private val tvResult: TextView = root.findViewById(R.id.tvCalcResult)

    private val fmt = DecimalFormat("#,##0.##########")

    private var pendingOperator: Char? = null
    private var storedValue: Double = 0.0
    private var currentInput: String = "0"
    private var expressionText: String = ""
    private var justEvaluated = false

    init {
        digit(root, R.id.btn0, "0")
        digit(root, R.id.btn1, "1")
        digit(root, R.id.btn2, "2")
        digit(root, R.id.btn3, "3")
        digit(root, R.id.btn4, "4")
        digit(root, R.id.btn5, "5")
        digit(root, R.id.btn6, "6")
        digit(root, R.id.btn7, "7")
        digit(root, R.id.btn8, "8")
        digit(root, R.id.btn9, "9")

        root.findViewById<Button>(R.id.btnDot).setOnClickListener { onDot() }
        root.findViewById<Button>(R.id.btnClear).setOnClickListener { onClear() }
        root.findViewById<Button>(R.id.btnBackspace).setOnClickListener { onBackspace() }
        root.findViewById<Button>(R.id.btnPercent).setOnClickListener { onPercent() }
        root.findViewById<Button>(R.id.btnPlus).setOnClickListener { onOperator('+') }
        root.findViewById<Button>(R.id.btnMinus).setOnClickListener { onOperator('-') }
        root.findViewById<Button>(R.id.btnMultiply).setOnClickListener { onOperator('×') }
        root.findViewById<Button>(R.id.btnDivide).setOnClickListener { onOperator('÷') }
        root.findViewById<Button>(R.id.btnEquals).setOnClickListener { onEquals() }

        render()
    }

    private fun digit(root: View, id: Int, value: String) {
        root.findViewById<Button>(id).setOnClickListener { onDigit(value) }
    }

    private fun onDigit(value: String) {
        if (justEvaluated) {
            currentInput = "0"
            expressionText = ""
            justEvaluated = false
        }
        currentInput = if (currentInput == "0") value else currentInput + value
        render()
    }

    private fun onDot() {
        if (justEvaluated) {
            currentInput = "0"
            expressionText = ""
            justEvaluated = false
        }
        if (!currentInput.contains(".")) {
            currentInput += "."
            render()
        }
    }

    private fun onBackspace() {
        if (justEvaluated) {
            onClear()
            return
        }
        currentInput = if (currentInput.length > 1) currentInput.dropLast(1) else "0"
        render()
    }

    private fun onClear() {
        pendingOperator = null
        storedValue = 0.0
        currentInput = "0"
        expressionText = ""
        justEvaluated = false
        render()
    }

    private fun onPercent() {
        val value = currentInput.toDoubleOrNull() ?: return
        currentInput = fmt.format(value / 100.0)
        render()
    }

    private fun onOperator(op: Char) {
        val value = currentInput.toDoubleOrNull() ?: return
        if (pendingOperator != null && !justEvaluated) {
            storedValue = compute(storedValue, value, pendingOperator!!)
        } else {
            storedValue = value
        }
        pendingOperator = op
        expressionText = "${fmt.format(storedValue)} $op"
        currentInput = "0"
        justEvaluated = false
        renderExpressionOnly(storedValue)
    }

    private fun onEquals() {
        val value = currentInput.toDoubleOrNull() ?: return
        val op = pendingOperator
        if (op != null) {
            val result = compute(storedValue, value, op)
            expressionText = "${fmt.format(storedValue)} $op ${fmt.format(value)} ="
            storedValue = result
            currentInput = fmt.format(result)
        }
        pendingOperator = null
        justEvaluated = true
        render()
    }

    private fun compute(a: Double, b: Double, op: Char): Double {
        return when (op) {
            '+' -> a + b
            '-' -> a - b
            '×' -> a * b
            '÷' -> if (b == 0.0) Double.NaN else a / b
            else -> b
        }
    }

    private fun render() {
        tvExpression.text = expressionText
        val value = currentInput.toDoubleOrNull()
        tvResult.text = when {
            currentInput.endsWith(".") -> currentInput
            value != null && value.isNaN() -> "Error"
            else -> currentInput
        }
    }

    private fun renderExpressionOnly(value: Double) {
        tvExpression.text = expressionText
        tvResult.text = fmt.format(value)
    }
}
