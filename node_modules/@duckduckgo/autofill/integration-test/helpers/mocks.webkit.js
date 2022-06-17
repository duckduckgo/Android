/**
 * @param {object} [overrides]
 * @param {Partial<FeatureToggles>} [overrides.featureToggles]
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
            platform: {name: 'ios'},
            features: {
                autofill: {
                    settings: {
                        featureToggles: {
                            'inputType_credentials': true,
                            'inputType_identities': false,
                            'inputType_creditCards': false,
                            'emailProtection': true,
                            'password_generation': false,
                            'credentials_saving': true,
                            ...overrides.featureToggles
                        }
                    }
                }
            }
        }
    }
}
/**
 * @param {object} [overrides]
 * @param {Partial<FeatureToggles>} [overrides.featureToggles]
 */
export const macosContentScopeReplacements = (overrides = {}) => {
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
            platform: {name: 'macos'},
            features: {
                autofill: {
                    settings: {
                        featureToggles: {
                            'inputType_credentials': true,
                            'inputType_identities': true,
                            'inputType_creditCards': true,
                            'emailProtection': true,
                            'password_generation': true,
                            'credentials_saving': true,
                            ...overrides.featureToggles
                        }
                    }
                }
            }
        }
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
 * @returns {MockBuilder}
 */
export function createWebkitMocks (platform = 'macos') {
    /**
     * Note: this will be mutated
     */
    const webkitBase = {
        pmHandlerGetAutofillInitData: {
            /** @type {PMData} */
            success: {
                identities: [],
                credentials: [],
                creditCards: []
            }
        },
        emailHandlerCheckAppSignedInStatus: {
            isAppSignedIn: true
        },
        emailHandlerGetAddresses: {
            /** @type {EmailAddresses} */
            addresses: {
                personalAddress: '',
                privateAddress: ''
            }
        },
        emailHandlerRefreshAlias: '',
        emailHandlerGetAlias: {
            /** @type {string|null} */
            alias: null
        },
        closeAutofillParent: {},
        getSelectedCredentials: {type: 'none'},
        pmHandlerGetAutofillCredentials: {
            /** @type {CredentialsObject|null} */
            success: null
        }
    }

    /** @type {MocksObjectWebkit} */
    const mocksObject = {
        getAutofillData: null,
        getAvailableInputTypes: null,
        storeFormData: null
    }

    /** @type {MockBuilder} */
    const builder = {
        withPrivateEmail (email) {
            if (platform === 'ios') {
                webkitBase.emailHandlerGetAlias.alias = email
            } else {
                webkitBase.emailHandlerGetAddresses.addresses.privateAddress = email
            }
            return this
        },
        withPersonalEmail (email) {
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
            webkitBase.pmHandlerGetAutofillCredentials.success = credentials
            mocksObject.getAutofillData = { success: credentials }
            return this
        },
        withAvailableInputTypes: function (inputTypes) {
            mocksObject.getAvailableInputTypes = { success: inputTypes }
            return this
        },
        withFeatureToggles: function (_featureToggles) {
            return this
        },
        tap (fn) {
            fn(webkitBase)
            return this
        },
        async applyTo (page) {
            if (mocksObject.getAvailableInputTypes === null) {
                mocksObject.getAvailableInputTypes = {success: {}}
            }
            return withMockedWebkit(page, { ...webkitBase, ...mocksObject })
        }
    }

    return builder
}

/**
 * This will mock webkit handlers based on the key-values you provide
 *
 * @private
 * @param {import('playwright').Page} page
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
                    return JSON.stringify(response)
                }
            }
        }
    }, mocks)
}
