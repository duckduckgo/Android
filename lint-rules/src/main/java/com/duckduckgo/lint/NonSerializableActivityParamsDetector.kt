/*
 * Copyright (c) 2026 DuckDuckGo
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

import com.android.tools.lint.client.api.JavaEvaluator
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
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiWildcardType
import org.jetbrains.uast.UClass
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class NonSerializableActivityParamsDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = Handler(context)

    internal class Handler(private val context: JavaContext) : UElementHandler() {
        override fun visitClass(node: UClass) {
            if (node.isInterface) return
            if (!context.evaluator.implementsInterface(node, ACTIVITY_PARAMS, false)) return

            node.fields
                .filterNot { it.hasModifierProperty(PsiModifier.STATIC) || it.hasModifierProperty(PsiModifier.TRANSIENT) }
                .filterNot { context.evaluator.isSerializable(it.type) }
                .forEach { field ->
                    context.report(
                        NON_SERIALIZABLE_ACTIVITY_PARAMS,
                        field,
                        context.getLocation(field),
                        "`${field.name}` of type `${field.type.presentableText}` is not `Serializable`. " +
                            "`ActivityParams` are passed through an `Intent` and every property must be serializable.",
                    )
                }
        }
    }

    companion object {
        private const val ACTIVITY_PARAMS = "com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams"
        private const val SERIALIZABLE = "java.io.Serializable"

        // Kotlin's read-only collection interfaces aren't Serializable, but the stdlib implementations behind
        // them are, so we accept them as long as their elements are.
        private val SERIALIZABLE_CONTAINERS = setOf(
            "java.util.Collection",
            "java.util.List",
            "java.util.Set",
            "java.util.Map",
        )

        private fun JavaEvaluator.isSerializable(type: PsiType): Boolean = when (type) {
            is PsiPrimitiveType -> true
            is PsiArrayType -> isSerializable(type.componentType)
            is PsiWildcardType -> type.bound?.let { isSerializable(it) } ?: false
            is PsiClassType -> isSerializable(type)
            else -> false
        }

        private fun JavaEvaluator.isSerializable(type: PsiClassType): Boolean {
            val psiClass = type.resolve() ?: return false
            if (psiClass is PsiTypeParameter) {
                return psiClass.extendsListTypes.any { isSerializable(it) }
            }
            val isSerializableClass = psiClass.qualifiedName in SERIALIZABLE_CONTAINERS ||
                implementsInterface(psiClass, SERIALIZABLE, false)
            return isSerializableClass && type.parameters.all { isSerializable(it) }
        }

        val NON_SERIALIZABLE_ACTIVITY_PARAMS = Issue.create(
            "NonSerializableActivityParams",
            "ActivityParams implementation has a non-serializable property",
            """
                `ActivityParams` is `Serializable` and is put into the `Intent` extras by `GlobalActivityStarter`.
                Any property whose type is not `Serializable` (or a primitive) makes the `Intent` fail at runtime
                with a `NotSerializableException`.

                Use primitives, `String`, enums, or classes that implement `java.io.Serializable`. Collections
                (`List`, `Set`, `Map`) are accepted when their elements are serializable. Mark properties that
                should not be serialized with `@Transient`.
            """.trimIndent(),
            Category.CORRECTNESS,
            10,
            ERROR,
            Implementation(NonSerializableActivityParamsDetector::class.java, EnumSet.of(JAVA_FILE, TEST_SOURCES)),
        )
    }
}
