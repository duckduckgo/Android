const supportedMessages = ["ContextMenuOpened", "PageStarted"];

const delay = $DELAY$;
const postInitialPing = $POST_INITIAL_PING$;
const replyToNativeMessages = $REPLY_TO_NATIVE_MESSAGES$;

const webViewCompatPingMessage = 'Ping:' + window.location.href + ' ' + delay + 'ms'


if (postInitialPing) {
    setTimeout(() => {
        webViewCompatTestObj.postMessage(webViewCompatPingMessage)
    }, delay)
}


webViewCompatTestObj.onmessage = function(event) {
    console.log("webViewCompatTestObj received", event.data)
    if (replyToNativeMessages && supportedMessages.includes(event.data)) {
        webViewCompatTestObj.postMessage(event.data + " from webViewCompatTestObj")
    }
}

window.onmessage = function(event) {
    console.log("window received", event.data)
    if (replyToNativeMessages && supportedMessages.includes(event.data)) {
        webViewCompatTestObj.postMessage(event.data + " from window")
    }
}
