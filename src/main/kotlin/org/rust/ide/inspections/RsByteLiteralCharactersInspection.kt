/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsVisitor

class RsByteLiteralCharactersInspection(): RsLocalInspectionTool() {
    override fun getDisplayName() = "Byte literal characters"

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitLitExpr(expr: RsLitExpr) {
                expr.byteLiteral?.let {
                    inspectByteLiteral(it, expr)
                } ?: expr.byteStringLiteral?.let {
                    inspectByteStringLiteral(it, expr)
                }
            }

            private fun inspectByteLiteral(element: PsiElement, expr: RsLitExpr) {
                if (!element.isTextAscii()) {
                    holder.registerProblem(expr, "Non-ASCII character in byte literal")
                }
            }

            private fun inspectByteStringLiteral(element: PsiElement, expr: RsLitExpr) {
                if (!element.isTextAscii()) {
                    holder.registerProblem(
                        expr,
                        "Non-ASCII character in byte string literal",
                        EscapeNonASCIISymbolsFix()
                    )
                }
            }
        }

    private class EscapeNonASCIISymbolsFix: LocalQuickFix {
        override fun getName(): String = "Replace non-ASCII symbols"

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val textWithEscapedBytes = buildString {
                element.text.encodeToByteArray().forEach {
                    if (it in 0x0 .. 0x7E) this.append(it.toInt().toChar())
                    else this.append("\\x${it.toUByte().toString(16)}")
                }
            }
            element.replace(RsPsiFactory(project).createLiteral(textWithEscapedBytes))
        }
    }
}

private fun PsiElement.isTextAscii(): Boolean = text.all { it.isAscii }

private val Char.isAscii: Boolean
    get() = this in '\u0000'..'\u007E'
