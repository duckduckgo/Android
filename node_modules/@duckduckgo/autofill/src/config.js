const DDG_DOMAIN_REGEX = new RegExp(/^https:\/\/(([a-z0-9-_]+?)\.)?duckduckgo\.com\/email/)

/**
 * This is a centralised place to contain all string/variable replacements
 *
 * @returns {GlobalConfig}
 */
function createGlobalConfig () {
    let isApp = false
    let isTopFrame = false
    let supportsTopFrame = false
    // Do not remove -- Apple devices change this when they support modern webkit messaging
    let hasModernWebkitAPI = false
    // INJECT isApp HERE
    // INJECT isTopFrame HERE
    // INJECT supportsTopFrame HERE
    // INJECT hasModernWebkitAPI HERE

    let isDDGTestMode = false
    // INJECT isDDGTestMode HERE

    let contentScope = null
    let userUnprotectedDomains = null
    let userPreferences = null
    // INJECT contentScope HERE
    // INJECT userUnprotectedDomains HERE
    // INJECT userPreferences HERE

    /** @type {Record<string, any> | null} */
    let availableInputTypes = null
    // INJECT availableInputTypes HERE

    // The native layer will inject a randomised secret here and use it to verify the origin
    let secret = 'PLACEHOLDER_SECRET'

    let isDDGApp = /(iPhone|iPad|Android|Mac).*DuckDuckGo\/[0-9]/i.test(window.navigator.userAgent) ||
        isApp ||
        isTopFrame
    const isAndroid = isDDGApp && /Android/i.test(window.navigator.userAgent)
    const isMobileApp = isDDGApp && !isApp
    const isFirefox = navigator.userAgent.includes('Firefox')
    const isDDGDomain = Boolean(window.location.href.match(DDG_DOMAIN_REGEX))

    return {
        isApp,
        isDDGApp,
        isAndroid,
        isFirefox,
        isMobileApp,
        isTopFrame,
        secret,
        supportsTopFrame,
        hasModernWebkitAPI,
        contentScope,
        userUnprotectedDomains,
        userPreferences,
        isDDGTestMode,
        isDDGDomain,
        availableInputTypes
    }
}

module.exports.createGlobalConfig = createGlobalConfig
module.exports.DDG_DOMAIN_REGEX = DDG_DOMAIN_REGEX
