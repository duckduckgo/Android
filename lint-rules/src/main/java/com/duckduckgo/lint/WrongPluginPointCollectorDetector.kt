/*
 * Copyright (c) 2022 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope.JAVA_FILE
import com.android.tools.lint.detector.api.Scope.TEST_SOURCES
import com.android.tools.lint.detector.api.Severity.ERROR
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat.TEXT
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.*

@Suppress("UnstableApiUsage")
class WrongPluginPointCollectorDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = NoInternalImportHandler(context)

    internal class NoInternalImportHandler(private val context: JavaContext) : UElementHandler() {

        override fun visitClass(node: UClass) {
            // travers class to find constructors
            node.accept(
                object : AbstractUastVisitor() {
                    override fun visitMethod(node: UMethod): Boolean {
                        if (node.isConstructor) {
                            handleConstructor(node)
                        }
                        return super.visitMethod(node)
                    }

                    override fun visitField(node: UField): Boolean {
                        // handle field
                        handleField(node)
                        return super.visitField(node)
                    }
                },
            )
        }
        private fun handleConstructor(node: UMethod) {
            node.parameterList.parameters.map { it.type }.forEach { psiType ->
                if (psiType is PsiClassType) {
                    val resolvedClass = psiType.resolve()
                    if (resolvedClass?.qualifiedName == "com.duckduckgo.common.utils.plugins.PluginPoint") {
                        psiType.parameters.forEach { typeArgument ->
                            val typeArgumentClass = (typeArgument as? PsiClassType)?.resolve()
                            if (typeArgumentClass?.isActivePlugin() == true) {
                                context.reportError(node, WRONG_PLUGIN_POINT_ISSUE)
                            }
                        }
                    }
                }
            }
        }

        private fun PsiClass.isActivePlugin(): Boolean {
            return this.isSubtypeOf("com.duckduckgo.common.utils.plugins.ActivePlugin")
        }
        private fun handleField(node: UField) {
            node.type.let { psiType ->
                if (psiType is PsiClassType) {
                    val resolvedClass = psiType.resolve()
                    if (resolvedClass?.qualifiedName == "com.duckduckgo.common.utils.plugins.PluginPoint") {
                        val typeArguments = psiType.parameters
                        for (typeArgument in typeArguments) {
                            val typeArgumentClass = (typeArgument as? PsiClassType)?.resolve()
                            if (typeArgumentClass?.isSubtypeOf(
                                    "com.duckduckgo.common.utils.plugins.ActivePlugin"
                                ) == true) {
                                context.reportError(node, WRONG_PLUGIN_POINT_ISSUE)
                            }
                        }
                    }
                }
            }
        }
        private fun PsiClass.isSubtypeOf(superClassQualifiedName: String): Boolean {
            if (this.qualifiedName == superClassQualifiedName) {
                return true
            }
            for (superType in this.supers) {
                if (superType.isSubtypeOf(superClassQualifiedName)) {
                    return true
                }
            }
            return false
        }

        private fun JavaContext.reportError(node: UElement, issue: Issue) {
            report(
                issue,
                node,
                context.getNameLocation(node),
                issue.getBriefDescription(TEXT),
            )

        }
    }

    companion object {
        val WRONG_PLUGIN_POINT_ISSUE = Issue.create("WrongPluginPointCollectorDetector",
            "PluginPoint cannot be collector of ActivePlugin(s)",
            """
                PluginPoint cannot be collector of ActivePlugin(s). Use ActivePluginPoint instead
            """.trimIndent(),
            Category.CORRECTNESS, 10, ERROR,
            Implementation(WrongPluginPointCollectorDetector::class.java, EnumSet.of(JAVA_FILE, TEST_SOURCES))
        )
    }
}
