package com.jetbrains.python.inspection

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.impl.PyBinaryExpressionImpl
import groovy.util.MapEntry
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
            val condition = pyIfPart.condition ?: return
            
            
            // if we couldn't calculate boolean value - calculateValue returns null
            // else show inspection with calculated value
            val calculatedValue = condition.calculateValue()?.toBoolean()
            
            if (calculatedValue != null) {
                registerProblem(
                    condition,
                    "Oh no!!! The condition is always ${calculatedValue.toStringCapFirst()}!!! Do something!!!"
                )
                return
            }
            
            val divisions = mutableListOf<Division>()
            
            condition.collectDivisions(divisions)
            
            if (divisions.isEmpty()) return

//            registerProblem(condition, divisions.joinToString("\n"))// check division search
            
            
            val boolMaps = generateBoolListForDivisions(divisions)
            
            if (boolMaps.isEmpty()) throw Exception("Empty BoolMap after divisions: $divisions")
            
            // check boolMap generation
//            registerProblem(condition, boolMaps.joinToString("\n---\n") {
//                it.entries.joinToString("\n") { "'${it.key.text}' to ${it.value}" }
//            })
            
            // trying to calculate again with some predefined Expressions
            val calc2 = condition.calculateValue(boolMaps.first())?.toBoolean()
            
            // predefining some comparisons didn't help
            if (calc2 == null) {
//                registerProblem(condition, "returned null after boolMaps.first (((")
                return
            }
            
            // all combinations should give the same result
            val allMatch = boolMaps.drop(1).all {
                    bools -> condition.calculateValue(bools)?.toBoolean() == calc2
            }
            
            if (allMatch) {
                registerProblem(condition, "The condition is always ${calc2.toStringCapFirst()}!" +
                        " (It was really hard to discover!)"
                )
            }
        }
        
        private fun Boolean.toBigInteger() = if (this) BigInteger.ONE else BigInteger.ZERO
        
        private fun BigInteger.toBoolean() = this != BigInteger.ZERO
        
        /** Converts bool to str with first capital letter: True or False*/
        private fun Boolean.toStringCapFirst() = toString().let { it[0].toUpperCase() + it.drop(1) }
    
    
    
        /** Tries to calculate constant expression with booleans and integers.
         * 
         * If the expression result is Boolean,
         * then it is casted to [BigInteger] through [toBigInteger].
         * 
         * @param predefinedExpr map with some maybe inner expressions in the tree (or this expression),
         * which are considered to be concrete boolean values in this context.
         * 
         * @return null if the expression can not be calculated. (and calculated value otherwise)*/
        private fun PyExpression.calculateValue(predefinedExpr: Map<out PyExpression, Boolean> = emptyMap()): BigInteger? {
            
            if (this in predefinedExpr) {
                return predefinedExpr[this]!!.toBigInteger()
            }
            
            if (this is PyBinaryExpression) {// a + b,  2 > 2, True and 5, ...
                val leftVal = this.leftExpression.calculateValue(predefinedExpr)
                        ?: return null// .also { registerProblem(this, "returned null in 1: ${this.text}")}
                val rightVal = this.rightExpression?.calculateValue(predefinedExpr)
                        ?: return null// .also { registerProblem(this, "returned null in 2: ${this.text}")}
                
                val operator =
                    this.operator ?: return null// .also { registerProblem(this, "returned null in 3: ${this.text}")}
                
                return when (operator) {
                    PyTokenTypes.EQEQ -> (leftVal == rightVal).toBigInteger()
                    PyTokenTypes.NE -> (leftVal != rightVal).toBigInteger()
                    PyTokenTypes.LT -> (leftVal < rightVal).toBigInteger()
                    PyTokenTypes.GT -> (leftVal > rightVal).toBigInteger()
                    PyTokenTypes.LE -> (leftVal <= rightVal).toBigInteger()
                    PyTokenTypes.GE -> (leftVal >= rightVal).toBigInteger()
                    PyTokenTypes.PLUS -> (leftVal + rightVal)
                    PyTokenTypes.MINUS -> (leftVal - rightVal)
                    PyTokenTypes.MULT -> (leftVal * rightVal)
                    PyTokenTypes.FLOORDIV -> if (rightVal == BigInteger.ZERO)
                            null// can't calculate irregular expressions
                        else
                            (leftVal / rightVal)// FLOORDIV - integer division: 5 // 2 = 2
//                    PyTokenTypes.DIV -> (leftVal / rightVal)// conversion int to double is not supported yet
                    PyTokenTypes.EXP -> leftVal.pow(rightVal.toInt())
                    PyTokenTypes.PERC -> (leftVal % rightVal)
                    PyTokenTypes.AND_KEYWORD -> (leftVal.toBoolean() && rightVal.toBoolean()).toBigInteger()
                    PyTokenTypes.OR_KEYWORD -> (leftVal.toBoolean() || rightVal.toBoolean()).toBigInteger()
                    else -> null// .also { registerProblem(this, "returned null in 4: ${this.text}")}
                }
            } else if (this is PyPrefixExpression) {// not True
                return when (this.operator) {
                    PyTokenTypes.NOT_KEYWORD -> this.operand?.calculateValue(predefinedExpr)?.toBoolean()?.not()?.toBigInteger()
                    else -> null// .also { registerProblem(this, "returned null in 5: ${this.text}")}
                }
            } else if (this is PyParenthesizedExpression) {// (2 + 2)
                return this.containedExpression?.calculateValue(predefinedExpr)
            } else if (this is PyNumericLiteralExpression) {// 777
                return this.bigIntegerValue
            } else if (this is PyBoolLiteralExpression) {// True, False
                return this.value.toBigInteger()
            } else {
                return null// .also { registerProblem(this, "returned null in 6: ${this.text}")}
            }
        }
        
        
        /** Collects all nodes like a > 5, x == 2 + 2 into list [divisions]
         * @param divisions list with [Division] object
         * (eg Division(pyExpr, "a", 5, false, false, true)*/
        private fun PyExpression.collectDivisions(divisions: MutableList<Division>) {
            if (this is PyBinaryExpression) {// a + b,  2 > 2, True and 5, ...
                val leftExpr = this.leftExpression
                val rightExpr = this.rightExpression ?: return
                
                val operator = this.operator ?: return
                
                /* if false - division looks 5 > x. if true - like x < 5**/
                val leftIsId: Boolean
                /** name of identifier*/
                val name: String
                /** calculated value of expression*/
                val number: BigInteger
                
                
                // x < 5
                if (leftExpr is PyReferenceExpression) {
                    leftIsId = true
                    name = leftExpr.text
                    number = rightExpr.calculateValue() ?: return// try to compute right expr for x < 2 + 3
                }
                // 5 > x
                else if (rightExpr is PyReferenceExpression) {
                    leftIsId = false
                    name = rightExpr.text
                    number = leftExpr.calculateValue() ?: return
                } else {// something compound like: not (a > 5 and b > 5)
                    // go in recursion
                    leftExpr.collectDivisions(divisions)
                    rightExpr.collectDivisions(divisions)
                    return
                }
                
                
                
                if (leftIsId) {
                    divisions += when (operator) {
                        PyTokenTypes.EQEQ -> Division.eq(this, name, number)
                        PyTokenTypes.NE -> Division.notEq(this, name, number)
                        PyTokenTypes.LT -> Division.less(this, name, number)
                        PyTokenTypes.GT -> Division.greater(this, name, number)
                        PyTokenTypes.LE -> Division.lessEq(this, name, number)
                        PyTokenTypes.GE -> Division.greaterEq(this, name, number)
                        else -> return
                    }
                } else {// inverted divisions. eg for 5 > x Division.less(x, 5)
                    divisions += when (operator) {
                        PyTokenTypes.EQEQ -> Division.eq(this, name, number)
                        PyTokenTypes.NE -> Division.notEq(this, name, number)
                        PyTokenTypes.LT -> Division.greaterEq(this, name, number)
                        PyTokenTypes.GT -> Division.less(this, name, number)
                        PyTokenTypes.LE -> Division.greaterEq(this, name, number)
                        PyTokenTypes.GE -> Division.lessEq(this, name, number)
                        else -> return
                    }
                }
                
            } else if (this is PyPrefixExpression) {// not (x > 5)
                if (this.operator == PyTokenTypes.NOT_KEYWORD) {
                    this.operand?.collectDivisions(divisions)
                }
            } else if (this is PyParenthesizedExpression) {// (a > 2 or a > 3)
                this.containedExpression?.collectDivisions(divisions)
            }
        }
        
        /**
         * @param divisions list of found binary expressions like 'x < 5'
         *
         * @return list of maps (PyBinExpr -> Boolean). Each map contains one of possible situations for all
         * binary expressions to be True or False
         * */
        private fun generateBoolListForDivisions(divisions: List<Division>): List<Map<PyBinaryExpression, Boolean>> {
            /** Divisions, grouped by names.*/
            val grouped = divisions.groupBy { it.idName }
            
            
            /** map: id name -> set of its sign breaking points([Division.point]), sorted.
             * (Точки перехода знака)*/
            val breakPointsMap = grouped.map { (name, divs) ->
                // here we have divisions with the same name
                
                // set of sign breaking points of all Divisions with the same name
                val points = divs.map { it.point }.sorted().toSet()
                
                Pair(name, points)
            }.toMap()
            
            
            // creating list of maps: PyExpr -> its sign
            
            // at first do it for each name
            
            // name -> matrix of Pairs: (BinExpr, Boolean)
            // in each row in matrix we have booleans for each division at current point
            val mapOfMatrices = grouped.mapValues {
                // divisions with the same name
                val (name, divs) = it
                
                // breakPoints for current name
                val breakPoints = breakPointsMap[name]!!
                
                val boolMatrix = mutableListOf<BooleanArray>()
                
                /** checks if the last row in matrix equals [arr]*/
                fun MutableList<BooleanArray>.equalsLast(arr: BooleanArray) = lastOrNull()?.contentEquals(arr) ?: false
                
                var arr: BooleanArray
                
                breakPoints.forEach { point ->
                    arr = BooleanArray(divs.size) { i -> divs[i].boolBeforePoint(point) }
                    // don't add array with the same values
                    if (!boolMatrix.equalsLast(arr)) boolMatrix += arr
                    
                    arr = BooleanArray(divs.size) { i -> divs[i].boolAtPoint(point) }
                    if (!boolMatrix.equalsLast(arr)) boolMatrix += arr
                }
                
                arr = BooleanArray(divs.size) { i -> divs[i].boolAtPlusInfinity() }
                if (!boolMatrix.equalsLast(arr)) boolMatrix += arr
                
                
                // add its own binExpr to each bool
                boolMatrix.map {
                    it.mapIndexed { i, bool ->
                        Pair(divs[i].binExpr, bool)
                    }
                }
            }
            
            // then just multiply rows with booleans for each name
            
            val product = cartesianProduct(mapOfMatrices.values.toList())
            // короче, мы получили список списков списков пар (BinaryExpr, Boolean)
            // судя по моему листочку, это выглядит так:
            // у нас есть список [наборов массивов True/False для каждого имени переменной]
            // то есть нам нужно объеденить внутренние списки для переменных в один map BinExpr -> Bool
            // и вернуть список map-ов
            
            return product.map { it.flatten().toMap() }
        }
        
        /** Represents boolean expression like  x < 5 when substitute different x
         * For this example [idName] = "x", [point] = 5,
         * [left] = true, [atPoint] = false, [right] = false*/
        private class Division(
            val binExpr: PyBinaryExpression,
            val idName: String,
            val point: BigInteger,
            val left: Boolean,
            val atPoint: Boolean,
            val right: Boolean
        ) {
            companion object {
                fun less(binExpr: PyBinaryExpression, name: String, point: BigInteger) =
                    Division(binExpr, name, point, true, false, false)
                
                fun lessEq(binExpr: PyBinaryExpression, name: String, point: BigInteger) =
                    Division(binExpr, name, point, true, true, false)
                
                fun greater(binExpr: PyBinaryExpression, name: String, point: BigInteger) =
                    Division(binExpr, name, point, false, false, true)
                
                fun greaterEq(binExpr: PyBinaryExpression, name: String, point: BigInteger) =
                    Division(binExpr, name, point, false, true, true)
                
                fun eq(binExpr: PyBinaryExpression, name: String, point: BigInteger) =
                    Division(binExpr, name, point, false, true, false)
                
                fun notEq(binExpr: PyBinaryExpression, name: String, point: BigInteger) =
                    Division(binExpr, name, point, true, false, true)
            }
            
            
            /**
             * Returns expression when we substitute real value a bit smaller than [x].
             *
             * Example: for range [5, inf) when
             * - [x] == 4 -> return false
             * - [x] == 5 -> return false
             * - [x] == 6 -> return true
             */
            fun boolBeforePoint(x: BigInteger) = if (x <= point) left else right
            
            /**
             * Returns expression when we substitute real value, exactly equal [x].
             *
             * Example: for range [5, inf) when
             * - [x] == 4 -> return false
             * - [x] == 5 -> return true
             * - [x] == 6 -> return true
             */
            fun boolAtPoint(x: BigInteger) = when {
                x < point -> left
                x > point -> right
                else -> atPoint
            }
            
            /** Returns expression when we substitute very big real value.*/
            fun boolAtPlusInfinity() = right
            
            override fun toString(): String {
                return "div('${binExpr.text}': $idName at $point as $left-$atPoint-$right)"
            }
//            var state: Boolean? = null
        }
    }
}


/** Set product. (Декартово произведение, если по-русски.)
 *  (In current implementation sets are replaced with lists.)
 * For [setList]:
 * a  b  c - first set,
 * 1  2  3 - second set,
 * x  y  z - third set
 * @return (a,1,x), (a,1,y), (a,1,z), (a, 2, x), ...
 */
private fun <T> cartesianProduct(setList: List<List<T>>): List<List<T>> {
    
    if (setList.size == 1) {
        return setList.first().map { listOf(it) }// ((a b c)) -> ((a) (b) (c))
    } else {
        
        val factor1 = setList.first()
        
        val smaller = cartesianProduct(setList.drop(1))
        
        return factor1.map { val1 ->
            smaller.map { smallerList -> listOf(val1) + smallerList }
        }.flatten()
    }
}



fun main(args: Array<String>) {
    // test cartesianProduct
    
    val setList = listOf(
        listOf("a", "b", "c"),
        listOf("1", "2", "3"),
        listOf("x", "y", "z")
    )
    
    println(cartesianProduct(setList).joinToString("\n"))
    
    
    
}