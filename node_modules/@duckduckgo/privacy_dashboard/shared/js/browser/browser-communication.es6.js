export function fetch (message) {
    return new Promise((resolve, reject) => {
        chrome.runtime.sendMessage(message, (result) => resolve(result))
    })
}

export function backgroundMessage (thisModel) {
    // listen for messages from background and
    // // notify subscribers
    chrome.runtime.onMessage.addListener((req, sender) => {
        if (sender.id !== chrome.runtime.id) return
        if (req.allowlistChanged) thisModel.send('allowlistChanged')
        if (req.updateTabData) thisModel.send('updateTabData')
        if (req.didResetTrackersData) thisModel.send('didResetTrackersData', req.didResetTrackersData)
        if (req.closePopup) window.close()
    })
}

export async function getBackgroundTabData () {
    const tab = await fetch({ getCurrentTab: true })
    if (tab) {
        const backgroundTabObj = await fetch({ getTab: tab.id })
        return backgroundTabObj
    }
}
