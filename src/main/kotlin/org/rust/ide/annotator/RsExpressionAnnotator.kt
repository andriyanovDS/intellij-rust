/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.SmartList
import org.rust.ide.fixes.AddStructFieldsFix
import org.rust.ide.fixes.CreateStructFieldFromConstructorFix
import org.rust.ide.fixes.RemoveRedundantParenthesesFix
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ref.deepResolve

class RsExpressionAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        val rsHolder = RsAnnotationHolder(holder)
        element.accept(RedundantParenthesisVisitor(rsHolder))
        if (element is RsStructLiteral) {
            val decl = element.path.reference?.deepResolve() as? RsFieldsOwner
            if (decl != null) {
                checkStructLiteral(rsHolder, decl, element)
            }
        }
    }

    private fun checkStructLiteral(
        holder: RsAnnotationHolder,
        decl: RsFieldsOwner,
        literal: RsStructLiteral
    ) {
        val body = literal.structLiteralBody
        body.structLiteralFieldList
            .filter { field ->
                field.reference.multiResolve().none { it is RsFieldDecl }
            }
            .forEach { field ->
                val annotationBuilder = holder.newErrorAnnotation(field.referenceNameElement, "No such field") ?: return@forEach

                CreateStructFieldFromConstructorFix.tryCreate(field)?.also { annotationBuilder.withFix(it) }

                annotationBuilder.highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL).create()
            }

        for (field in body.structLiteralFieldList.findDuplicateReferences()) {
            holder.createErrorAnnotation(field.referenceNameElement, "Duplicate field")
        }

        if (body.dotdot != null) return  // functional update, no need to declare all the fields.

        if (decl is RsStructItem && decl.kind == RsStructKind.UNION) return

        if (calculateMissingFields(body, decl).isNotEmpty()) {
            if (!literal.existsAfterExpansion) return

            val structNameRange = literal.descendantOfTypeStrict<RsPath>()?.textRange
            if (structNameRange != null) {
                holder.holder.newAnnotation(HighlightSeverity.ERROR, "Some fields are missing")
                    .range(structNameRange)
                    .newFix(AddStructFieldsFix(literal)).range(body.parent.textRange).registerFix()
                    .newFix(AddStructFieldsFix(literal, recursive = true)).range(body.parent.textRange).registerFix()
                    .create()
            }
        }


    }
}


private class RedundantParenthesisVisitor(private val holder: RsAnnotationHolder) : RsVisitor() {
    override fun visitCondition(o: RsCondition) =
        o.expr.warnIfParens("Predicate expression has unnecessary parentheses")

    override fun visitRetExpr(o: RsRetExpr) =
        o.expr.warnIfParens("Return expression has unnecessary parentheses")

    override fun visitMatchExpr(o: RsMatchExpr) =
        o.expr.warnIfParens("Match expression has unnecessary parentheses")

    override fun visitForExpr(o: RsForExpr) =
        o.expr.warnIfParens("For loop expression has unnecessary parentheses")

    override fun visitParenExpr(o: RsParenExpr) {
        if (o.parent !is RsParenExpr) o.expr.warnIfParens("Redundant parentheses in expression")
    }

    private fun RsExpr?.warnIfParens(@InspectionMessage message: String) {
        if (this !is RsParenExpr || !canWarn(this)) return
        holder.createWeakWarningAnnotation(this, message, RemoveRedundantParenthesesFix(this))
    }

    private fun canWarn(expr: RsParenExpr): Boolean {
        if (PsiTreeUtil.getContextOfType(
                expr,
                false,
                RsCondition::class.java,
                RsMatchExpr::class.java,
                RsForExpr::class.java
            ) == null) return true

        return when (val child = expr.children.singleOrNull()) {
            is RsStructLiteral -> false
            is RsBinaryExpr -> child.exprList.all { it !is RsStructLiteral }
            else -> true
        }
    }
}

private fun <T : RsMandatoryReferenceElement> Collection<T>.findDuplicateReferences(): Collection<T> {
    val names = HashSet<String>(size)
    val result = SmartList<T>()
    for (item in this) {
        val name = item.referenceName
        if (name in names) {
            result += item
        }
        names += name
    }
    return result
}

fun calculateMissingFields(expr: RsStructLiteralBody, decl: RsFieldsOwner): List<RsFieldDecl> {
    val declaredFields = expr.structLiteralFieldList.map { it.referenceName }.toSet()
    return decl.fields.filter { it.name !in declaredFields }
}
