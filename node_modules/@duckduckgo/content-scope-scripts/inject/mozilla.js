/* global contentScopeFeatures */

function init () {
    contentScopeFeatures.load()

    chrome.runtime.sendMessage({
        messageType: 'registeredContentScript',
        options: {
            documentUrl: window.location.href
        }
    },
    (message) => {
        // Background has disabled features
        if (!message) {
            return
        }
        if (message.debug) {
            window.addEventListener('message', (m) => {
                if (m.data.action && m.data.message) {
                    chrome.runtime.sendMessage({
                        debuggerMessage: m.data
                    })
                }
            })
        }
        contentScopeFeatures.init(message)
    }
    )

    chrome.runtime.onMessage.addListener((message) => {
        // forward update messages to the embedded script
        if (message && message.type === 'update') {
            contentScopeFeatures.update(message)
        }
    })
}

init()
