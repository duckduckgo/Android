/**
 * Deny-listed APIs that we don't want people to use.
 *
 * From https://gist.github.com/JakeWharton/1f102d98cd10133b03a5f374540c327a but using our own rules
 */
@file:Suppress("UnstableApiUsage")

package com.duckduckgo.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LocationType.NAME
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity.ERROR
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.duckduckgo.lint.DenyListedEntry.Companion.MATCH_ALL
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.util.isConstructorCall
import org.w3c.dom.Element
import java.util.EnumSet

/**
 * Steps for adding a new deny-listed API:
 * 1. Add it to [config].
 * 2. For allowing existing usages,
 *   - Regenerate new baselines: `./gradlew updateLintBaseline`.
 */
internal class DenyListedApiDetector : Detector(), SourceCodeScanner, XmlScanner {
    private val config = DenyListConfig(
        DenyListedEntry(
            className = "android.content.pm.PackageManager",
            functionName = "getInstalledApplications",
            errorMessage = "Use PackageManager.safeGetInstalledApplications() instead"
        ),
        DenyListedEntry(
            className = "kotlinx.coroutines.flow.FlowKt__ReduceKt",
            functionName = "first",
            errorMessage = "first() will throw if flow is empty, firstOrNull() it's a safer option."
        ),
        DenyListedEntry(
            className = "kotlinx.coroutines.flow.FlowKt__ReduceKt",
            functionName = "last",
            errorMessage = "last() will throw if there's not at least one item, lastOrNull() it's a safer option."
        ),
        DenyListedEntry(
            className = "com.duckduckgo.feature.toggles.api.Toggle",
            functionName = "setRawStoredState",
            errorMessage = "If you find yourself using this API in production, you're doing something wrong!!"
        ),
        DenyListedEntry(
            className = "com.duckduckgo.feature.toggles.api.Toggle",
            functionName = "getRawStoredState",
            errorMessage = "If you find yourself using this API in production, you're doing something wrong!!"
        ),
        DenyListedEntry(
            className = "com.duckduckgo.feature.toggles.api.FeatureTogglesPlugin",
            functionName = MATCH_ALL,
            errorMessage = "Use ContributesRemoteFeature instead fo create features"
        ),
        DenyListedEntry(
            className = "android.content.Context",
            functionName = "getSharedPreferences",
            errorMessage = "Use com.duckduckgo.data.store.api.SharedPreferencesProvider instead"
        ),
        DenyListedEntry(
            className = "kotlinx.coroutines.test.TestBuildersKt",
            functionName = "runBlockingTest",
            errorMessage = "Use runBlocking or runBlockingWithTestDispatcher instead. runBlockingTest " +
                "is often overkill. The only difference between it and runBlocking is that it gives you " +
                "a TestDispatcher. Treat it like RxJava's TestScheduler: if you don't need it, leave it " +
                "out. But even if you do need it, you are better off using TestDispatcher directly: if a " +
                "coroutine is blocked on something other than TestDispatcher (which isn't uncommon - " +
                "most things that block in Cash App aren't coroutines right now), at the end of your " +
                "runBlockingTest block it will fail the test. This is not what you want."
        ),
        DenyListedEntry(
            className = "java.util.LinkedList",
            functionName = "<init>",
            errorMessage = "For a stack/queue/double-ended queue use ArrayDeque, for a list use ArrayList. Both are more efficient internally."
        ),
        DenyListedEntry(
            className = "java.util.Stack",
            functionName = "<init>",
            errorMessage = "For a stack use ArrayDeque which is more efficient internally."
        ),
        DenyListedEntry(
            className = "java.util.Vector",
            functionName = "<init>",
            errorMessage = "For a vector use ArrayList or ArrayDeque which are more efficient internally."
        ),
        DenyListedEntry(
            className = "io.reactivex.schedulers.Schedulers",
            functionName = "newThread",
            errorMessage = "Use a scheduler which wraps a cached set of threads. There should be no reason to be arbitrarily creating threads on Android."
        ),
        DenyListedEntry(
            className = "android.os.Build.VERSION_CODES",
            fieldName = MATCH_ALL,
            errorMessage = "No one remembers what these constants map to. Use the API level integer value directly since it's self-defining."
        ),
    )

    override fun getApplicableUastTypes() = config.applicableTypes()
    override fun createUastHandler(context: JavaContext) = config.visitor(context)

    override fun getApplicableElements() = config.applicableLayoutInflaterElements.keys
    override fun visitElement(context: XmlContext, element: Element) = config.visitor(context, element)

    private class DenyListConfig(vararg entries: DenyListedEntry) {
        private class TypeConfig(entries: List<DenyListedEntry>) {
            @Suppress("UNCHECKED_CAST") // Safe because of filter call.
            val functionEntries = entries.groupBy { it.functionName }
                .filterKeys { it != null } as Map<String, List<DenyListedEntry>>

            @Suppress("UNCHECKED_CAST") // Safe because of filter call.
            val referenceEntries = entries.groupBy { it.fieldName }
                .filterKeys { it != null } as Map<String, List<DenyListedEntry>>
        }

        private val typeConfigs = entries.groupBy { it.className }
            .mapValues { (_, entries) -> TypeConfig(entries) }

        val applicableLayoutInflaterElements = entries
            .filter { it.functionName == "<init>" }
            .filter {
                it.arguments == null ||
                    it.arguments == listOf("android.content.Context", "android.util.AttributeSet")
            }
            .groupBy { it.className }
            .mapValues { (cls, entries) ->
                entries.singleOrNull() ?: error("Multiple two-arg init rules for $cls")
            }

        fun applicableTypes() = listOf<Class<out UElement>>(
            UCallExpression::class.java,
            UImportStatement::class.java,
            UQualifiedReferenceExpression::class.java,
        )

        fun visitor(context: JavaContext) = object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val function = node.resolve() ?: return

                val className = function.containingClass?.qualifiedName
                // uncomment below for debugging only
                // println("Resolved call: ${className}.${function.name}")
                val typeConfig = typeConfigs[className] ?: return

                val functionName = if (node.isConstructorCall()) {
                    "<init>"
                } else {
                    // Kotlin compiler mangles function names that use inline value types as parameters by suffixing them
                    // with a hyphen. https://github.com/Kotlin/KEEP/blob/master/proposals/inline-classes.md#mangling-rules
                    function.name.substringBefore("-")
                }

                val deniedFunctions = typeConfig.functionEntries.getOrDefault(functionName, emptyList()) +
                    typeConfig.functionEntries.getOrDefault(MATCH_ALL, emptyList())

                deniedFunctions.forEach { denyListEntry ->
                    if (denyListEntry.parametersMatchWith(function) && denyListEntry.argumentsMatchWith(node)) {
                        context.report(
                            issue = ISSUE,
                            location = context.getLocation(node),
                            message = denyListEntry.errorMessage
                        )
                    }
                }
            }

            override fun visitImportStatement(node: UImportStatement) {
                val reference = node.resolve() as? PsiField ?: return
                visitField(reference, node)
            }

            override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
                val reference = node.resolve() as? PsiField ?: return
                visitField(reference, node)
            }

            private fun visitField(reference: PsiField, node: UElement) {
                val className = reference.containingClass?.qualifiedName
                val typeConfig = typeConfigs[className] ?: return

                val referenceName = reference.name
                val deniedFunctions = typeConfig.referenceEntries.getOrDefault(referenceName, emptyList()) +
                    typeConfig.referenceEntries.getOrDefault(MATCH_ALL, emptyList())

                deniedFunctions.forEach { denyListEntry ->
                    context.report(
                        issue = ISSUE,
                        location = context.getLocation(node),
                        message = denyListEntry.errorMessage
                    )
                }
            }
        }

        fun visitor(context: XmlContext, element: Element) {
            val denyListEntry = applicableLayoutInflaterElements.getValue(element.tagName)
            context.report(
                issue = ISSUE,
                location = context.getLocation(element, type = NAME),
                message = denyListEntry.errorMessage,
            )
        }

        private fun DenyListedEntry.parametersMatchWith(function: PsiMethod): Boolean {
            val expected = parameters
            val actual = function.parameterList.parameters.map { it.type.canonicalText }

            return when {
                expected == null -> true
                expected.isEmpty() && actual.isEmpty() -> true
                expected.size != actual.size -> false
                else -> expected == actual
            }
        }

        private fun DenyListedEntry.argumentsMatchWith(node: UCallExpression): Boolean {
            // "arguments" being null means we don't care about this check and it should just return true.
            val expected = arguments ?: return true
            val actual = node.valueArguments

            return when {
                expected.size != actual.size -> false
                else -> expected.zip(actual).all { (expectedValue, actualValue) ->
                    argumentMatches(expectedValue, actualValue)
                }
            }
        }

        private fun argumentMatches(expectedValue: String, actualValue: UExpression): Boolean {
            if (expectedValue == "*") return true
            val renderString =
                (actualValue as? ULiteralExpression)?.asRenderString()
                    ?: (actualValue as? UQualifiedReferenceExpression)?.asRenderString() // Helps to match against static method params 'Class.staticMethod()'.
            if (expectedValue == renderString) return true

            return false
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "DenyListedApi",
            briefDescription = "Deny-listed API",
            explanation = "This lint check flags usages of APIs in external libraries that we prefer not to use in Cash App",
            category = CORRECTNESS,
            priority = 5,
            severity = ERROR,
            implementation = Implementation(
                DenyListedApiDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE, Scope.TEST_SOURCES),
                EnumSet.of(Scope.JAVA_FILE),
                EnumSet.of(Scope.RESOURCE_FILE),
                EnumSet.of(Scope.TEST_SOURCES),
            )
        )
    }
}

data class DenyListedEntry(
    val className: String,
    /** The function name to match, [MATCH_ALL] to match all functions, or null if matching a field. */
    val functionName: String? = null,
    /** The field name to match, [MATCH_ALL] to match all fields, or null if matching a function. */
    val fieldName: String? = null,
    /** Fully-qualified types of function parameters to match, or null to match all overloads. */
    val parameters: List<String>? = null,
    /** Argument expressions to match at the call site, or null to match all invocations. */
    val arguments: List<String>? = null,
    val errorMessage: String,
) {
    init {
        require((functionName == null) xor (fieldName == null)) {
            "One of functionName or fieldName must be set"
        }
    }

    companion object {
        internal const val MATCH_ALL = "*"
    }
}
