/**
 * Use this to mock android message handlers.
 *
 * For example, the following would mock interactions with window.postMessage
 * to ensure that it returns { alias: "x" }
 *
 * ```js
 * await createWebkitMocks()
 *     .withPrivateEmail("x")
 *     .withPersonalEmail("y")
 *     .applyTo(page)
 * ```
 * @public
 * @returns {MockBuilder}
 */
export function createAndroidMocks () {
    const mocks = {
        getRuntimeConfigurationResponse: {
            contentScope: {
                features: {
                    autofill: {
                        state: 'enabled',
                        exceptions: []
                    }
                },
                unprotectedTemporary: []
            },
            userUnprotectedDomains: [],
            userPreferences: {
                debug: false,
                platform: {
                    name: 'android'
                },
                features: {
                    autofill: {
                        settings: {
                            /** @type {FeatureToggles} */
                            featureToggles: {
                                inputType_credentials: true,
                                inputType_identities: false,
                                inputType_creditCards: false,
                                emailProtection: true,
                                password_generation: false,
                                credentials_saving: true
                            }
                        }
                    }
                }
            }
        },
        getAvailableInputTypesResponse: {
            credentials: true,
            email: true
        },
        /** @type {IdentityObject|CredentialsObject|CreditCardObject|null} */
        getAutofillData: null,
        /** @type {string|null} */
        address: null
    }
    /** @type {MockBuilder} */
    const builder = {
        withPrivateEmail (email) {
            mocks.address = email
            return this
        },
        withPersonalEmail (email) {
            mocks.address = email
            return this
        },
        withAvailableInputTypes (inputTypes) {
            mocks.inputTypes = inputTypes
            return this
        },
        withIdentity: function () {
            throw new Error('Function not implemented.')
        },
        withFeatureToggles (featureToggles) {
            const defaults = mocks.getRuntimeConfigurationResponse.userPreferences.features.autofill.settings.featureToggles
            Object.assign(defaults, featureToggles)
            return this
        },
        /**
         * @param credentials
         * @returns {MockBuilder}
         */
        withCredentials: function (credentials) {
            mocks.getAutofillData = credentials
            return this
        },
        tap () {
            return this
        },
        async applyTo (page) {
            return page.evaluate(mocks => {
                window.__playwright = { mocks: { calls: [] } }
                window.EmailInterface = {
                    showTooltip () {
                        window.postMessage({
                            type: 'getAliasResponse',
                            alias: mocks.address
                        }, window.origin)
                    },
                    getUserData () {
                        return ''
                    },
                    storeCredentials () {
                        return ''
                    },
                    isSignedIn () {
                        return ''
                    }
                }
                /**
                 * @param {Names} name
                 * @param {any} request
                 * @param {any} response
                 */
                function respond (name, request, response) {
                    const call = [name, request, response]
                    window.__playwright.mocks.calls.push(JSON.parse(JSON.stringify(call)))
                    window.postMessage(JSON.stringify({
                        type: name + 'Response',
                        success: response
                    }), window.origin)
                }
                // todo(Shane): This is the proposed android API.
                /** @type {MocksObjectAndroid} */
                const mocksObject = {
                    getRuntimeConfiguration () {
                        return respond('getRuntimeConfiguration', null, mocks.getRuntimeConfigurationResponse)
                    },
                    getAvailableInputTypes () {
                        return respond('getAvailableInputTypes', null, mocks.getAvailableInputTypesResponse)
                    },
                    getAutofillData (request) {
                        return respond('getAutofillData', request, mocks.getAutofillData)
                    },
                    storeFormData (request) {
                        /** @type {MockCall} */
                        const call = ['storeFormData', request, mocks.getAutofillData]
                        window.__playwright.mocks.calls.push(JSON.parse(JSON.stringify(call)))
                    }
                }
                // @ts-ignore
                window.BrowserAutofill = mocksObject
            }, mocks)
        }
    }
    return builder
}
