package com.jetbrains.python.inspection

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.*
import java.math.BigInteger


class PyConstantExpression : PyInspection() {
    
    override fun buildVisitor(
        holder: ProblemsHolder, isOnTheFly: Boolean,
        session: LocalInspectionToolSession
    ): PsiElementVisitor {
        return Visitor(holder, session)
    }
    
    class Visitor(holder: ProblemsHolder?, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {
        
        override fun visitPyIfStatement(node: PyIfStatement) {
            super.visitPyIfStatement(node)
            processIfPart(node.ifPart)
            for (part in node.elifParts) {
                processIfPart(part)
            }
        }
        
        private fun processIfPart(pyIfPart: PyIfPart) {
            val condition = pyIfPart.condition
            
            // if we couldn't calculate boolean value - calculateValue returns null
            // else show inspection with calculated value
            condition?.calculateValue()?.toBoolean()?.also {
                registerProblem(condition, "О ужас!!! Условие всегда будет $it!!!")
            }
            
        }
        
        private fun Boolean.toBigInteger() = if (this) BigInteger.ONE else BigInteger.ZERO
        private fun BigInteger.toBoolean() = this != BigInteger.ZERO
        
        
        /** If the expression result is Boolean,
         * then it is casted to [BigInteger] through [toBigInteger].
         * - Returns null if the expression can not be calculated.*/
        private fun PyExpression.calculateValue(): BigInteger? {
            
            if (this is PyBinaryExpression) {// a + b,  2 > 2, True and 5, ...
                val leftVal = this.leftExpression.calculateValue() ?: return null// .also { registerProblem(this, "returned null in 1: ${this.text}")}
                val rightVal = this.rightExpression?.calculateValue() ?: return null// .also { registerProblem(this, "returned null in 2: ${this.text}")}
                
                val operator = this.operator ?: return null// .also { registerProblem(this, "returned null in 3: ${this.text}")}
                
                return when (operator) {
                    PyTokenTypes.EQEQ -> (leftVal == rightVal).toBigInteger()
                    PyTokenTypes.NE -> (leftVal != rightVal).toBigInteger()
                    PyTokenTypes.LT -> (leftVal < rightVal).toBigInteger()
                    PyTokenTypes.GT -> (leftVal > rightVal).toBigInteger()
                    PyTokenTypes.LE -> (leftVal >= rightVal).toBigInteger()
                    PyTokenTypes.GE -> (leftVal <= rightVal).toBigInteger()
                    PyTokenTypes.PLUS -> (leftVal + rightVal)
                    PyTokenTypes.MINUS -> (leftVal - rightVal)
                    PyTokenTypes.MULT -> (leftVal * rightVal)
                    PyTokenTypes.DIV -> (leftVal / rightVal)
                    PyTokenTypes.EXP -> leftVal.pow(rightVal.toInt())
                    PyTokenTypes.PERC -> (leftVal % rightVal)
                    PyTokenTypes.AND_KEYWORD -> (leftVal.toBoolean() && rightVal.toBoolean()).toBigInteger()
                    PyTokenTypes.OR_KEYWORD -> (leftVal.toBoolean() || rightVal.toBoolean()).toBigInteger()
                    else -> null// .also { registerProblem(this, "returned null in 4: ${this.text}")}
                }
            } else if (this is PyPrefixExpression) {// not True
                return when (this.operator) {
                    PyTokenTypes.NOT_KEYWORD -> this.operand?.calculateValue()?.toBoolean()?.not()?.toBigInteger()
                    else -> null// .also { registerProblem(this, "returned null in 5: ${this.text}")}
                }
            } else if (this is PyParenthesizedExpression) {// (2 + 2)
                return this.containedExpression?.calculateValue()
            } else if (this is PyNumericLiteralExpression) {// 777
                return this.bigIntegerValue
            } else if (this is PyBoolLiteralExpression) {// True, False
                return this.value.toBigInteger()
            } else {
                return null// .also { registerProblem(this, "returned null in 6: ${this.text}")}
            }
        }
        
    }
}