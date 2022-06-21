/**
 * @typedef {import('../../src/deviceApiCalls/__generated__/validators-ts').GetAutofillDataResponse} GetAutofillDataResponse
 * @typedef {import('../../src/deviceApiCalls/__generated__/validators-ts').AutofillFeatureToggles} AutofillFeatureToggles
 */

/**
 * @param {object} [overrides]
 * @param {Partial<AutofillFeatureToggles>} [overrides.featureToggles]
 */
export const iosContentScopeReplacements = (overrides = {}) => {
    return {
        contentScope: {
            features: {
                'autofill': {
                    exceptions: [],
                    state: 'enabled'
                }
            },
            unprotectedTemporary: []
        },
        userUnprotectedDomains: [],
        userPreferences: {
            debug: true,
            platform: { name: 'ios' },
            features: {
                autofill: {
                    settings: {
                        featureToggles: {
                            inlineIcon_credentials: false,
                            ...overrides.featureToggles
                        }
                    }
                }
            }
        }
    }
}

/**
 * @param {{overlay?: boolean, featureToggles?: AutofillFeatureToggles}} opts
 * @returns {Partial<Replacements>}
 */
export const macosContentScopeReplacements = (opts = {}) => {
    const { overlay = false, featureToggles } = opts
    return {
        isApp: true,
        hasModernWebkitAPI: true,
        contentScope: {
            features: {
                'autofill': {
                    exceptions: [],
                    state: 'enabled'
                }
            },
            unprotectedTemporary: []
        },
        userUnprotectedDomains: [],
        userPreferences: {
            debug: true,
            platform: { name: 'macos' },
            features: {
                autofill: {
                    settings: {
                        /** @type {AutofillFeatureToggles} */
                        featureToggles: {
                            inputType_credentials: true,
                            inputType_identities: true,
                            inputType_creditCards: true,
                            emailProtection: true,
                            password_generation: true,
                            credentials_saving: true,
                            inlineIcon_credentials: true,
                            ...featureToggles
                        }
                    }
                }
            }
        },
        ...overlay ? macosWithOverlay() : undefined
    }
}

/**
 * @returns {Partial<Replacements>}
 */
export const macosWithOverlay = () => {
    return {
        isTopFrame: false,
        supportsTopFrame: true
    }
}
/**
 * Use this to mock webkit message handlers.
 *
 * For example, the following would mock the message 'emailHandlerGetAddresses'
 * and ensure that it returns  { addresses: { privateAddress: "x", personalAddress: "y" } }
 *
 * ```js
 * await createWebkitMocks()
 *     .withPrivateEmail("x")
 *     .withPersonalEmail("y")
 *     .applyTo(page)
 * ```
 * @public
 * @param {"macos" | "ios"} platform
 */
export function createWebkitMocks (platform = 'macos') {
    /**
     * Note: this will be mutated
     */
    const webkitBase = {
        pmHandlerGetAutofillInitData: {
            /** @type {InboundPMData} */
            success: {
                identities: [],
                credentials: [],
                creditCards: [],
                serializedInputContext: '{}'
            }
        },
        emailHandlerCheckAppSignedInStatus: {
            isAppSignedIn: false
        },
        emailHandlerGetAddresses: {
            /** @type {EmailAddresses} */
            addresses: {
                personalAddress: '',
                privateAddress: ''
            }
        },
        emailHandlerRefreshAlias: null,
        emailHandlerGetAlias: {
            /** @type {string|null} */
            alias: null
        },
        closeAutofillParent: {},
        getSelectedCredentials: [{type: 'stop'}],
        pmHandlerStoreData: {},
        pmHandlerGetAutofillCredentials: {
            /** @type {CredentialsObject|null} */
            success: null
        },
        showAutofillParent: {},
        setSize: {},
        // newer ones
        /** @type {null | GetAutofillDataResponse} */
        getAutofillData: null,
        /** @type {null | Record<string, any>} */
        getAvailableInputTypes: null,
        storeFormData: null
    }

    /** @type {MockBuilder<any, webkitBase>} */
    const builder = {
        withPrivateEmail (email) {
            webkitBase.emailHandlerCheckAppSignedInStatus.isAppSignedIn = true
            if (platform === 'ios') {
                webkitBase.emailHandlerGetAlias.alias = email
            } else {
                webkitBase.emailHandlerGetAddresses.addresses.privateAddress = email
            }
            return this
        },
        withPersonalEmail (email) {
            webkitBase.emailHandlerCheckAppSignedInStatus.isAppSignedIn = true
            if (platform === 'ios') {
                webkitBase.emailHandlerGetAlias.alias = email
            } else {
                webkitBase.emailHandlerGetAddresses.addresses.personalAddress = email
            }
            return this
        },
        withIdentity (identity) {
            webkitBase.pmHandlerGetAutofillInitData.success.identities.push(identity)
            return this
        },
        withCredentials: function (credentials) {
            webkitBase.pmHandlerGetAutofillInitData.success.credentials.push(credentials)
            /** @type {TopContextData} */
            const topContextData = {inputType: 'credentials.username'}
            webkitBase.pmHandlerGetAutofillInitData.success.serializedInputContext = JSON.stringify(topContextData)
            webkitBase.pmHandlerGetAutofillCredentials.success = credentials
            webkitBase.getAutofillData = { success: { credentials, action: 'fill' } }
            webkitBase.getSelectedCredentials = [
                {type: 'none'},
                {type: 'none'},
                {type: 'none'},
                {type: 'none'},
                {type: 'none'},
                {type: 'ok', data: credentials, configType: 'credentials'},
                {type: 'stop'}
            ]
            return this
        },
        withAvailableInputTypes: function (inputTypes) {
            webkitBase.getAvailableInputTypes = {success: inputTypes}
            return this
        },
        withFeatureToggles: function (_featureToggles) {
            throw new Error('unreachable - webkit cannot mock feature toggles this way. Use script replacements')
        },
        tap (fn) {
            fn(webkitBase)
            return this
        },
        async applyTo (page) {
            if (webkitBase.getAvailableInputTypes === null) {
                webkitBase.getAvailableInputTypes = {success: {}}
            }
            return withMockedWebkit(page, {...webkitBase})
        },
        /**
         * @param {(keyof webkitBase)[]} handlers
         * @returns {builder}
         */
        removeHandlers: function (handlers) {
            const keys = Object.keys(webkitBase)
            for (let handler of handlers) {
                if (!keys.includes(handler)) {
                    throw new Error('webkit mock did not exist for ' + handler)
                }
                delete webkitBase[handler]
            }
            return this
        }
    }

    return builder
}

/**
 * This will mock webkit handlers based on the key-values you provide
 *
 * @private
 * @param {import("playwright").Page} page
 * @param {Record<string, any>} mocks
 */
async function withMockedWebkit (page, mocks) {
    await page.addInitScript((mocks) => {
        window.__playwright = { mocks: { calls: [] } }
        window.webkit = {
            messageHandlers: {}
        }

        for (let [msgName, response] of Object.entries(mocks)) {
            window.webkit.messageHandlers[msgName] = {
                postMessage: async (data) => {
                    /** @type {MockCall} */
                    const call = [msgName, data, response]
                    window.__playwright.mocks.calls.push(JSON.parse(JSON.stringify(call)))

                    // This allows mocks to have multiple return values.
                    // It has to be inline here since it's serialized into the page.
                    const isMulti = Array.isArray(response)
                    if (isMulti) {
                        const prevCount = window.__playwright.mocks.calls.filter(([name]) => name === msgName).length
                        const next = response[prevCount]
                        if (next) {
                            return JSON.stringify(next)
                        }
                    }

                    return JSON.stringify(response)
                }
            }
        }
    }, mocks)
}
