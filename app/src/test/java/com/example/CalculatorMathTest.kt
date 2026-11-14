package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorMathTest {
    @Test
    fun evaluatesBasicArithmeticAndParentheses() {
        assertEquals(14.0, CalculatorMath.evaluate("2+3*4"), 0.0000001)
        assertEquals(20.0, CalculatorMath.evaluate("(2+3)*4"), 0.0000001)
        assertEquals(-5.0, CalculatorMath.evaluate("-2-3"), 0.0000001)
    }

    @Test
    fun evaluatesPowersRootsAndFactorials() {
        assertEquals(512.0, CalculatorMath.evaluate("2^3^2"), 0.0000001)
        assertEquals(-4.0, CalculatorMath.evaluate("-2^2"), 0.0000001)
        assertEquals(0.25, CalculatorMath.evaluate("2^-2"), 0.0000001)
        assertEquals(9.0, CalculatorMath.evaluate("sqrt(81)"), 0.0000001)
        assertEquals(3.0, CalculatorMath.evaluate("cbrt(27)"), 0.0000001)
        assertEquals(120.0, CalculatorMath.evaluate("5!"), 0.0000001)
    }

    @Test
    fun evaluatesTrigonometryInDegreesAndRadians() {
        assertEquals(0.5, CalculatorMath.evaluate("sin(30)", degrees = true), 0.0000001)
        assertEquals(1.0, CalculatorMath.evaluate("cos(0)", degrees = false), 0.0000001)
        assertEquals(30.0, CalculatorMath.evaluate("asin(0.5)", degrees = true), 0.0000001)
    }

    @Test
    fun evaluatesLogarithmsConstantsPercentAndAnswer() {
        assertEquals(2.0, CalculatorMath.evaluate("log(100)"), 0.0000001)
        assertEquals(1.0, CalculatorMath.evaluate("ln(e)"), 0.0000001)
        assertEquals(Math.PI, CalculatorMath.evaluate("pi"), 0.0000001)
        assertEquals(0.25, CalculatorMath.evaluate("25%"), 0.0000001)
        assertEquals(42.0, CalculatorMath.evaluate("ans*2", answer = 21.0), 0.0000001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInverseTrigonometryOutsideDomain() {
        CalculatorMath.evaluate("asin(2)")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDivisionByZero() {
        CalculatorMath.evaluate("10/0")
    }
}
