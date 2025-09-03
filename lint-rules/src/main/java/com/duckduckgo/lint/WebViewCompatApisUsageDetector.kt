package com.duckduckgo.lint

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.utils.mapToSetOrEmpty
import org.jetbrains.uast.UCallExpression

data class WebViewCompatApiUsage(
    val methodName: String,
    val containingClass: String,
    val issue: Issue
)

@Suppress("UnstableApiUsage")
class WebViewCompatApisUsageDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return webViewCompatApiUsages.map { it.methodName }
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {

        webViewCompatApiUsages
            .firstOrNull { it.methodName == method.name }
            ?.takeIf { context.evaluator.isMemberInClass(method, it.containingClass) }
            ?.let { context.report(
                it.issue,
                node,
                context.getLocation(node),
                it.issue.getExplanation(TextFormat.RAW)
            ) }
    }

    companion object {
        val ISSUE_ADD_WEB_MESSAGE_LISTENER_USAGE: Issue = Issue.create(
            id = "AddWebMessageListenerUsage",
            briefDescription = "Use safe WebViewCompatWrapper methods",
            explanation = "Use `WebViewCompatWrapper#addWebMessageListener` instead",
            category = Category.CORRECTNESS,
            severity = Severity.ERROR,
            implementation = Implementation(
                WebViewCompatApisUsageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        val ISSUE_REMOVE_WEB_MESSAGE_LISTENER_USAGE: Issue = Issue.create(
            id = "RemoveWebMessageListenerUsage",
            briefDescription = "Use safe WebViewCompatWrapper methods",
            explanation = "Use `WebViewCompatWrapper#removeWebMessageListener` instead",
            category = Category.CORRECTNESS,
            severity = Severity.ERROR,
            implementation = Implementation(
                WebViewCompatApisUsageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        val ISSUE_ADD_DOCUMENT_START_JAVASCRIPT_USAGE: Issue = Issue.create(
            id = "AddDocumentStartJavaScriptUsage",
            briefDescription = "Use safe WebViewCompatWrapper methods",
            explanation = "Use `WebViewCompatWrapper#addDocumentStartJavaScript` instead",
            category = Category.CORRECTNESS,
            severity = Severity.ERROR,
            implementation = Implementation(
                WebViewCompatApisUsageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val webViewCompatApiUsages = listOf(
            WebViewCompatApiUsage(
                methodName = "addWebMessageListener",
                containingClass = "androidx.webkit.WebViewCompat",
                issue = ISSUE_ADD_WEB_MESSAGE_LISTENER_USAGE
            ),
            WebViewCompatApiUsage(
                methodName = "safeAddWebMessageListener",
                containingClass = "com.duckduckgo.app.browser.DuckDuckGoWebView",
                issue = ISSUE_ADD_WEB_MESSAGE_LISTENER_USAGE
            ),
            WebViewCompatApiUsage(
                methodName = "removeWebMessageListener",
                containingClass = "androidx.webkit.WebViewCompat",
                issue = ISSUE_REMOVE_WEB_MESSAGE_LISTENER_USAGE
            ),
            WebViewCompatApiUsage(
                methodName = "safeRemoveWebMessageListener",
                containingClass = "com.duckduckgo.app.browser.DuckDuckGoWebView",
                issue = ISSUE_REMOVE_WEB_MESSAGE_LISTENER_USAGE
            ),
            WebViewCompatApiUsage(
                methodName = "addDocumentStartJavaScript",
                containingClass = "androidx.webkit.WebViewCompat",
                issue = ISSUE_ADD_DOCUMENT_START_JAVASCRIPT_USAGE
            ),
            WebViewCompatApiUsage(
                methodName = "safeAddDocumentStartJavaScript",
                containingClass = "com.duckduckgo.app.browser.DuckDuckGoWebView",
                issue = ISSUE_ADD_DOCUMENT_START_JAVASCRIPT_USAGE
            ),
        )

        val issues = webViewCompatApiUsages.mapToSetOrEmpty { it.issue }
    }
}
