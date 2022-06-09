// const domain = 'duck.co'
let random = 0
const userData = {
    userName: 'shane-123',
    nextAlias: random
}

function getAddresses () {
    return {
        personalAddress: `${userData.userName}`,
        privateAddress: `${userData.nextAlias}`
    }
}

async function addUserData (userData, sender) {
    const { userName, token } = userData
    // Check the origin. Shouldn't be necessary, but better safe than sorry
    if (!sender.url.match(/^https:\/\/(([a-z0-9-_]+?)\.)?duckduckgo\.com\/email/)) return

    const sendDdgUserReady = async () => {
        const tabs = await browser.tabs.query({})
        tabs.forEach((tab) =>
            // eslint-disable-next-line no-undef
            utils.sendTabMessage(tab.id, { type: 'ddgUserReady' })
        )
    }

    // eslint-disable-next-line no-undef
    await settings.ready()
    // eslint-disable-next-line no-undef
    const { existingToken } = settings.getSetting('userData') || {}

    // If the user is already registered, just notify tabs that we're ready
    if (existingToken === token) {
        await sendDdgUserReady()
        return { success: true }
    }

    // Check general data validity
    // eslint-disable-next-line no-undef
    if (isValidUsername(userName) && isValidToken(token)) {
        // eslint-disable-next-line no-undef
        settings.updateSetting('userData', userData)
        // Once user is set, fetch the alias and notify all tabs
        // eslint-disable-next-line no-undef
        const response = await fetchAlias()
        if (response && response.error) {
            return { error: response.error.message }
        }

        sendDdgUserReady()
        // eslint-disable-next-line no-undef
        showContextMenuAction()
        return { success: true }
    } else {
        return { error: 'Something seems wrong with the user data' }
    }
}

function registeredTempAutofillContentScript () {
    return {
        debug: false,
        site: {
            isBroken: false,
            allowlisted: false,
            enabledFeatures: ['autofill']
        }
    }
}

function init () {
    chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
        if (message.registeredTempAutofillContentScript) {
            return sendResponse(registeredTempAutofillContentScript())
        } else if (message.getAddresses) {
            return sendResponse(getAddresses())
        } else if (message.refreshAlias) {
            userData.nextAlias = random + 1
            return sendResponse(getAddresses())
        } else if (message.addUserData) {
            return sendResponse(addUserData(message.addUserData, sender))
        }
    })

    // TODO handle logout, contextualAutofill and ddgUserReady messages
}

init()
