(function() {
    const ddgObj = window.$OBJECT_NAME$;

    const supportedMessages = ["ContextMenuOpened", "PageStarted"];

    const delay = $DELAY$;
    const postInitialPing = $POST_INITIAL_PING$;
    const replyToNativeMessages = $REPLY_TO_NATIVE_MESSAGES$;
    const messagePrefix = 'webViewCompat '

    const webViewCompatPingMessage = messagePrefix + 'Ping:' + window.location.href + ' ' + delay + 'ms'


    if (postInitialPing) {
        if (delay > 0) {
            setTimeout(() => {
                ddgObj.postMessage(webViewCompatPingMessage)
            }, delay)
        } else {
            ddgObj.postMessage(webViewCompatPingMessage)
        }
    }


    ddgObj.addEventListener('message', function(event) {
        console.log("$OBJECT_NAME$ received", event.data)
        if (replyToNativeMessages && supportedMessages.includes(event.data)) {
            ddgObj.postMessage(messagePrefix + event.data + " from $OBJECT_NAME$")
        }
    });

    window.addEventListener('message', function(event) {
        console.log("window received", event.data)
        if (replyToNativeMessages && supportedMessages.includes(event.data)) {
            ddgObj.postMessage(messagePrefix + event.data + " from window")
        }
    });
})();
