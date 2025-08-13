package com.duckduckgo.lint

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

@Suppress("UnstableApiUsage")
class WebViewCompatApisUsageDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("addWebMessageListener", "removeWebMessageListener")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            "addWebMessageListener" -> {
                if (context.evaluator.isMemberInClass(method, "androidx.webkit.WebViewCompat")) {
                    context.report(
                        ISSUE_ADD_WEB_MESSAGE_LISTENER_USAGE,
                        node,
                        context.getLocation(node),
                        ISSUE_ADD_WEB_MESSAGE_LISTENER_USAGE.getExplanation(TextFormat.RAW)
                    )
                }
            }
            "removeWebMessageListener" -> {
                if (context.evaluator.isMemberInClass(method, "androidx.webkit.WebViewCompat")) {
                    context.report(
                        ISSUE_REMOVE_WEB_MESSAGE_LISTENER_USAGE,
                        node,
                        context.getLocation(node),
                        ISSUE_REMOVE_WEB_MESSAGE_LISTENER_USAGE.getExplanation(TextFormat.RAW)
                    )
                }
            }
            "addDocumentStartJavaScript" -> {
                if (context.evaluator.isMemberInClass(method, "androidx.webkit.WebViewCompat")) {
                    context.report(
                        ISSUE_ADD_DOCUMENT_START_JAVASCRIPT_USAGE,
                        node,
                        context.getLocation(node),
                        ISSUE_ADD_DOCUMENT_START_JAVASCRIPT_USAGE.getExplanation(TextFormat.RAW)
                    )
                }
            }
        }
    }

    companion object {
        val ISSUE_ADD_WEB_MESSAGE_LISTENER_USAGE: Issue = Issue.create(
            id = "AddWebMessageListenerUsage",
            briefDescription = "Use safe WebMessageListener methods",
            explanation = "Use `safeAddWebMessageListener` instead of `WebViewCompat.addWebMessageListener`",
            category = Category.CORRECTNESS,
            severity = Severity.ERROR,
            implementation = Implementation(
                WebViewCompatApisUsageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        val ISSUE_REMOVE_WEB_MESSAGE_LISTENER_USAGE: Issue = Issue.create(
            id = "RemoveWebMessageListenerUsage",
            briefDescription = "Use safe WebMessageListener methods",
            explanation = "Use `safeRemoveWebMessageListener` instead of `WebViewCompat.removeWebMessageListener`",
            category = Category.CORRECTNESS,
            severity = Severity.ERROR,
            implementation = Implementation(
                WebViewCompatApisUsageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        val ISSUE_ADD_DOCUMENT_START_JAVASCRIPT_USAGE: Issue = Issue.create(
            id = "AddDocumentStartJavaScriptUsage",
            briefDescription = "Use safe WebViewCompat methods",
            explanation = "Use `safeAddDocumentStartJavaScript` instead of `WebViewCompat.addDocumentStartJavaScript`",
            category = Category.CORRECTNESS,
            severity = Severity.ERROR,
            implementation = Implementation(
                WebViewCompatApisUsageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
