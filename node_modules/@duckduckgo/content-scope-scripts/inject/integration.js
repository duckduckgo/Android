/* global contentScopeFeatures */

function getTopLevelURL () {
    try {
        // FROM: https://stackoverflow.com/a/7739035/73479
        // FIX: Better capturing of top level URL so that trackers in embedded documents are not considered first party
        if (window.location !== window.parent.location) {
            return new URL(window.location.href !== 'about:blank' ? document.referrer : window.parent.location.href)
        } else {
            return new URL(window.location.href)
        }
    } catch (error) {
        return new URL(location.href)
    }
}

function generateConfig (data, userList) {
    const topLevelUrl = getTopLevelURL()
    return {
        debug: false,
        sessionKey: 'randomVal',
        site: {
            domain: topLevelUrl.hostname,
            isBroken: false,
            allowlisted: false,
            enabledFeatures: [
                'fingerprintingCanvas',
                'fingerprintingScreenSize',
                'navigatorInterface'
            ]
        }
    }
}

async function init () {
    const topLevelUrl = getTopLevelURL()
    const processedConfig = generateConfig()
    await contentScopeFeatures.load()

    // mark this phase as loaded
    setStatus('loaded')

    if (!topLevelUrl.searchParams.has('wait-for-init-args')) {
        await contentScopeFeatures.init(processedConfig)
        setStatus('initialized')
        return
    }

    // Wait for a message containing additional config
    document.addEventListener('content-scope-init-args', async (evt) => {
        const merged = {
            ...processedConfig,
            ...evt.detail
        }

        // init features
        await contentScopeFeatures.init(merged)

        // set status to initialized so that tests can resume
        setStatus('initialized')
    }, { once: true })
}

/**
 * @param {"loaded" | "initialized"} status
 */
function setStatus (status) {
    window.__content_scope_status = status
}

init()
