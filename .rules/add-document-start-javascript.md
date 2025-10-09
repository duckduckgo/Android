---
title: "AddDocumentStartJavaScript"
description: "How we use AddDocumentStartJavaScript"
keywords: ["adddocumentstartjavascript", "addDocumentStartJavaScript", "AddDocumentStartJavaScript", "add document start javascript", "css", "c-s-s", "CSS", "C-S-S", "WebView", "webview", "contentscopescripts", "content-scope-scripts", "ContentScopeScripts", "contentScopeScripts"]
alwaysApply: false
---

# Adding a new script using `addDocumentStartJavaScript`

There are 2 ways of adding a new script using 
1. (Recommended) Using `AddDocumentStartScriptDelegate` to implement `AddDocumentStartJavaScript` via delegation pattern. This approach already takes care of
preventing adding the same script more than once, and removing the previous script and adding a new one if needed. It also adds checks on the WebView lifecycle to minimize native crashes. 
2. (Manual approach, only recommended if more flexibility than the one provided by the delegate is needed) Manually implementing `AddDocumentStartJavaScript`

## Using `AddDocumentStartScriptDelegate` to implement `AddDocumentStartJavaScript` via delegation pattern
```
class <YourFeature>AddDocumentStartJavaScript @Inject constructor(
    scriptInjectorDelegate: AddDocumentStartScriptDelegate,
) : AddDocumentStartJavaScript by scriptInjectorDelegate.createPlugin(
    object : AddDocumentStartJavaScriptScriptStrategy {
        override suspend fun canInject(): Boolean {
            TODO("Implement logic to determine if the script can be added (i.e. checking RC flags or user settings)" +
                "or return true if always applicable")
        }

        override suspend fun getScriptString(): String {
            TODO("Return the script to be injected")
        }

        override val allowedOriginRules: Set<String>
            get() = TODO("Return the set of allowed origin rules. For example:" +
                "- if the script should be injected on all origins, return setOf(\"*\")" +
                "- if the script should be injected only on specific origins, return setOf(\"https://example.com\", \"https://another.com\")" +
                "- if the script should be injected on all subdomains of a domain, return setOf(\"https://*.example.com\")")

        override val context: String
            get() = TODO("Return a string representing the context of this script, e.g., \"YourFeature\"")
    },
)
```

## Manually implementing `AddDocumentStartJavaScript`
Since the `AddDocumentStartScriptDelegate` already solves most of the issues and dangers of working with the `addDocumentStartJavaScript` API, manual implementation isn't recommended. If absolutely necessary, having a look at `RealAddDocumentStartScriptDelegate` is recommended, in order to replicate some best practices:
* If a script has already been added, don't add it again unless the content has changed
* If the content has changed, remove the previous `ScriptHandler` before adding the new script. Call `remove` on the main thread
* Use `WebViewCompatWrapper` instead of calling `WebViewCompat` directly, as it includes several checks on the `WebView` lifecycle and ensures proper threading is used

# Adding a new script to the browser (DuckDuckGoWebView/BrowserTabFragment)

If you need your script to be executed on the main browser WebView, you need to create a browser plugin that wraps your `AddDocumentStartJavaScript` implementation.

## Step 1: Create the core implementation

Follow the patterns described in the [delegation pattern section](#using-adddocumentstartscriptdelegate-to-implement-adddocumentstartjavascript-via-delegation-pattern) above.

## Step 2: Create the browser plugin wrapper

```kotlin
@ContributesMultibinding(FragmentScope::class)
class <YourFeature>AddDocumentStartJavaScriptBrowserPlugin @Inject constructor(
    private val <yourFeature>AddDocumentStartJavaScript: <YourFeature>AddDocumentStartJavaScript,
) : AddDocumentStartJavaScriptBrowserPlugin {
    override fun addDocumentStartJavaScript(): AddDocumentStartJavaScript = 
        <yourFeature>AddDocumentStartJavaScript
}
```

## How it works

The browser automatically calls your script's `addDocumentStartJavaScript(webView)` method when:
- A new page loads
- If you also need it to be called when Privacy protections are updated, you need to add your implementation context to `BrowserTabViewModel#privacyProtectionsUpdated`
- If you need your script to be re-added in any other circumstances, follow the same pattern as `BrowserTabViewModel#privacyProtectionsUpdated`, so we only update the necessary scripts, not all of them. However, this is strongly discouraged by the Chromium team, and should only be done if messaging is not viable (for example, out of performance concerns)

The `AddDocumentStartScriptDelegate` handles lifecycle management, script deduplication, and WebView safety checks.

## Important Notes

- Use appropriate scoping and consider using `@SingleInstanceIn(<Scope>)` with appropriate scoping to make sure only one instance of `WebMessaging` exists per `WebView`
