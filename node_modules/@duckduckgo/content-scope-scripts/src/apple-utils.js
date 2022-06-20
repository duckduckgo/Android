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

/**
 * @param {URL} topLevelUrl
 * @param {*} featureList
 */
function isUnprotectedDomain (topLevelUrl, featureList) {
    let unprotectedDomain = false
    const domainParts = topLevelUrl && topLevelUrl.host ? topLevelUrl.host.split('.') : []

    // walk up the domain to see if it's unprotected
    while (domainParts.length > 1 && !unprotectedDomain) {
        const partialDomain = domainParts.join('.')

        unprotectedDomain = featureList.filter(domain => domain.domain === partialDomain).length > 0

        domainParts.shift()
    }

    return unprotectedDomain
}

/**
 * @param {*} data
 * @param {string[]} userList
 * @param {*} preferences
 * @param {string|URL} [maybeTopLevelUrl]
 */
export function processConfig (data, userList, preferences, maybeTopLevelUrl) {
    const topLevelUrl = maybeTopLevelUrl || getTopLevelURL()
    const allowlisted = userList.filter(domain => domain === topLevelUrl.host).length > 0
    const enabledFeatures = Object.keys(data.features).filter((featureName) => {
        const feature = data.features[featureName]
        return feature.state === 'enabled' && !isUnprotectedDomain(topLevelUrl, feature.exceptions)
    })
    const isBroken = isUnprotectedDomain(topLevelUrl, data.unprotectedTemporary)
    const prefs = {
        ...preferences,
        site: {
            domain: topLevelUrl.hostname,
            isBroken,
            allowlisted,
            enabledFeatures
        },
        cookie: {}
    }
    return prefs
}
