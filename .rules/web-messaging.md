---
title: "WebMessaging"
description: "How we use WebMessaging for JavaScript communication"
keywords: ["webmessaging", "webMessaging", "WebMessaging", "web messaging", "javascript", "js", "WebView", "webview", "js-messaging", "js messaging", "message handlers", "subscription events", "WebViewCompat", "messaging strategy", "messaging", "js messaging", "javascript messaging", "JavaScript messaging", "javaScript messaging", "new message handler", "new messaging interface", "WebMessagingDelegate", ]
alwaysApply: false
---

# Using WebMessaging for JavaScript Communication

There are 2 ways of implementing WebMessaging functionality:
1. (Recommended) Using `WebMessagingDelegate` to implement `WebMessaging` via delegation pattern. This approach already takes care of managing WebView lifecycle, as well as supporting `WebViewCompatMessageHandler` and `GlobalJsMessageHandler` for a standardized way to handle messaging across different features .
2. (Manual approach, only recommended if more flexibility than the one provided by the delegate is needed) Manually implementing `WebMessaging`

## Using `WebMessagingDelegate` to implement `WebMessaging` via delegation pattern

```kotlin
class <YourFeature>WebMessaging @Inject constructor(
    webMessagingDelegate: WebMessagingDelegate,
) : WebMessaging by webMessagingDelegate.createPlugin(
    object : WebMessagingStrategy {
        override val context: String
            get() = TODO("Return a string representing the context of this messaging implementation, e.g., \"YourFeature\"")

        override val allowedDomains: Set<String>
            get() = TODO("Return the set of allowed domains for messaging. For example:" +
                "- if messaging should work on all domains, return setOf(\"*\")" +
                "- if messaging should work only on specific domains, return setOf(\"https://example.com\", \"https://another.com\")" +
                "- if messaging should work on all subdomains of a domain, return setOf(\"https://*.example.com\")")

        override val objectName: String
            get() = TODO("Return the JavaScript object name that will be available in the WebView, e.g., \"YourFeatureMessaging\"")

        override suspend fun canHandleMessaging(): Boolean {
            TODO("Implement logic to determine if messaging can be handled (i.e. checking feature flags or user settings)" +
                "or return true if always applicable")
        }

        override fun getMessageHandlers(): List<WebViewCompatMessageHandler> {
            TODO("Return the list of message handlers that will process incoming JavaScript messages")
        }

        override fun getGlobalMessageHandler(): List<GlobalJsMessageHandler> {
            TODO("Return the list of global message handlers that should always be processed" +
                "regardless of whether a specific feature handler matches the message. For example DebugFlagGlobalHandler")
        }
    },
)
```

## Manually implementing `WebMessaging`

Since the `WebMessagingDelegate` already solves most of the issues and dangers of working with JavaScript messaging, manual implementation isn't recommended. If absolutely necessary, having a look at `RealWebMessagingDelegate` is recommended, in order to replicate some best practices:
* Always check WebView lifecycle before registering/unregistering handlers
* Ensure thread safety when working with WebView operations
* Use `WebViewCompatWrapper` instead of calling `WebViewCompat` directly, as it includes several checks on the `WebView` lifecycle and ensures proper threading is used

# Adding WebMessaging to the browser (DuckDuckGoWebView/BrowserTabFragment)

If you need your messaging functionality to be available on the main browser WebView, you need to create a browser plugin that wraps your `WebMessaging` implementation.

## Step 1: Create the core implementation

Follow the patterns described in the [delegation pattern section](#using-webmessagingdelegate-to-implement-webmessaging-via-delegation-pattern) above.

## Step 2: Create the browser plugin wrapper

```kotlin
@ContributesMultibinding(FragmentScope::class)
class <YourFeature>WebMessagingBrowserPlugin @Inject constructor(
    private val <yourFeature>WebMessaging: <YourFeature>WebMessaging,
) : WebMessagingBrowserPlugin {
    override fun webMessaging(): WebMessaging = 
        <yourFeature>WebMessaging
}
```

## How it works

The `WebMessagingDelegate` handles lifecycle management, WebView safety checks, and proper JavaScript interface management.

## Message Handler Implementation

When implementing message handlers, you need to implement the appropriate plugin interfaces and follow these patterns:

### WebViewCompatMessageHandler
```kotlin
import com.duckduckgo.contentscopescripts.api.WebViewCompatContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.ProcessResult
import com.duckduckgo.js.messaging.api.WebViewCompatMessageHandler
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(ActivityScope::class)
class YourFeatureMessageHandler @Inject constructor() : WebViewCompatContentScopeJsMessageHandlersPlugin {

    override fun getJsMessageHandler(): WebViewCompatMessageHandler = object : WebViewCompatMessageHandler {

        override fun process(jsMessage: JsMessage): ProcessResult? {
            
            TODO("Process the message and return appropriate result" + 
                " - Return SendToConsumer to pass message to consumer callback (normally UI layer)" + 
                " - Return SendResponse(response) to send direct response without going through the UI layer" + 
                " - Return null if no further action required. For example, if you need to store something"+ 
                "from the handler and don't need to send a response or notify the UI layer"
            )
        }

        override val featureName: String = TODO("Return feature name that should match this handler")
        override val methods: List<String> = TODO("Return list of methods that should match this handler")
    }
}
```

### GlobalJsMessageHandler
```kotlin
import com.duckduckgo.contentscopescripts.impl.messaging.GlobalContentScopeJsMessageHandlersPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.js.messaging.api.GlobalJsMessageHandler
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.ProcessResult
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class YourFeatureGlobalHandler @Inject constructor() : GlobalContentScopeJsMessageHandlersPlugin {

    override fun getGlobalJsMessageHandler(): GlobalJsMessageHandler = object : GlobalJsMessageHandler {

        override fun process(jsMessage: JsMessage): ProcessResult? {
            TODO("Process the message and return appropriate result" + 
                " - Return SendToConsumer to pass message to consumer callback (normally UI layer)" + 
                " - Return SendResponse(response) to send direct response without going through the UI layer" + 
                " - Return null if no further action required. For example, if you need to store something" + 
                "from the handler and don't need to send a response or notify the UI layer"
            )
        }

        override val method: String = TODO("Return the name of the method that should match this handler")
    }
}
```

## Posting Messages to JavaScript

There are 2 ways of sending messages to JavaScript
1. If you don't require backwards compatibility with the old way of handling messages, you can use `WebMessaging` directly
2. Otherwise, you can use `PostMessageWrapperPlugin`


**Important**: To send messages using the new `WebMessaging` interface using the [delegation pattern](#using-webmessagingdelegate-to-implement-webmessaging-via-delegation-pattern), you must first receive a message from JavaScript. This is because the delegate needs to establish a `replyProxy` to ensure proper context communication. The system automatically sets up the reply proxy when it receives an `initialPing` message from JavaScript, which allows subsequent `postMessage` calls to work correctly. If you're not using the [delegation pattern](#using-webmessagingdelegate-to-implement-webmessaging-via-delegation-pattern), establishing a `replyProxy` for message posting is still recommended to ensure messages are only received by the appropriate script.


### Using `WebMessaging` directly

To send messages from native code to JavaScript using the new WebMessaging interface:

```kotlin
// Create subscription event data
val subscriptionEventData = SubscriptionEventData(
    featureName = "yourFeature",
    subscriptionName = "yourEventType",
    params = JSONObject().put("key", "value")
)

// Post message to WebView
webMessaging.postMessage(webView, subscriptionEventData)
```

### Using `PostMessageWrapperPlugin`

Use `PostMessageWrapperPlugin` when:
- You need to support both new and legacy messaging interfaces
- You have feature flags that determine which messaging system to use
- You want to gradually migrate from legacy to new messaging

The plugin handles the complexity of choosing the right messaging interface, allowing consumers to simply call `postMessage()` without worrying about the underlying implementation details.

When you need to post messages using either the new WebMessaging interface or the legacy JsMessageHelper depending on feature flags or other conditions, you can create an implementation of `PostMessageWrapperPlugin`:

```kotlin
@ContributesMultibinding(FragmentScope::class)
class YourFeaturePostMessageWrapperPlugin @Inject constructor(
    @Named("yourFeature") private val webMessaging: WebMessaging,
    private val jsMessageHelper: JsMessageHelper,
    private val yourFeatureFlags: YourFeatureFlags,
) : PostMessageWrapperPlugin {
    
    override suspend fun postMessage(message: SubscriptionEventData, webView: WebView) {
        if (yourFeatureFlags.isNewMessagingEnabled()) {
            // Use new WebMessaging interface
            webMessaging.postMessage(webView, message)
        } else {
            // Use legacy JsMessageHelper
            jsMessageHelper.sendSubscriptionEvent(
                subscriptionEvent = SubscriptionEvent(
                    context = webMessaging.context,
                    featureName = message.featureName,
                    subscriptionName = message.subscriptionName,
                    params = message.params,
                ),
                callbackName = "yourCallbackName",
                secret = "yourSecret",
                webView = webView,
            )
        }
    }
    
    override val context: String
        get() = webMessaging.context
}
```

In order to make sure you're only sending the message to the appropriate consumer, inject your `WebMessaging` implementation, not the entire list of available implementations.


## Important Notes

- Use appropriate scoping and consider using `@SingleInstanceIn(<Scope>)` with appropriate scoping to make sure only one instance of `WebMessaging` exists per `WebView`
- The `context` string should be unique and descriptive

