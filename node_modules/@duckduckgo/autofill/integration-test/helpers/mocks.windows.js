/**
 * Use this to mock windows message handlers
 *
 * For example, the following would mock interactions with window.postMessage
 * to ensure that it returns { alias: "x" }
 *
 * ```js
 * await createWindowsMocks()
 *     .withPrivateEmail("x")
 *     .withPersonalEmail("y")
 *     .applyTo(page)
 * ```
 * @public
 * @returns {MockBuilder}
 */
export function createWindowsMocks () {
    const mocks = {
        getRuntimeConfiguration: {
            'contentScope': {
                'features': {
                    'autofill': {
                        'state': 'enabled',
                        'exceptions': []
                    }
                },
                'unprotectedTemporary': []
            },
            'userUnprotectedDomains': [],
            'userPreferences': {
                'debug': false,
                'platform': {
                    'name': 'windows'
                },
                'features': {
                    'autofill': {
                        'settings': {
                            'featureToggles': {
                                'inputType_credentials': true,
                                'inputType_identities': false,
                                'inputType_creditCards': false,
                                'emailProtection': false,
                                'password_generation': false,
                                'credentials_saving': true
                            }
                        }
                    }
                }
            }
        },
        /** @type {AvailableInputTypes} */
        getAvailableInputTypes: {
            credentials: true
        },
        /** @type {InboundPMData} */
        getAutofillInitData: {
            credentials: [],
            creditCards: [],
            identities: [],
            serializedInputContext: '{}'
        },
        /** @type {CredentialsObject | null} */
        getAutofillCredentials: null
    }
    /** @type {MockBuilder} */
    const builder = {
        withPrivateEmail (_email) {
            return this
        },
        withPersonalEmail (_email) {
            return this
        },
        withAvailableInputTypes (inputTypes) {
            mocks.getAvailableInputTypes = inputTypes
            return this
        },
        /**
          * @param {FeatureToggles} featureToggles
         */
        withFeatureToggles (featureToggles) {
            Object.assign(mocks.getRuntimeConfiguration.userPreferences.features.autofill.settings.featureToggles, featureToggles)
            return this
        },
        tap () {
            return this
        },
        async applyTo (page) {
            return page.evaluate(mocks => {
                window.__playwright = { mocks: { calls: [] } }
                const listeners = []

                function recordCall (name, request, response) {
                    const call = [name, request, response]
                    window.__playwright.mocks.calls.push(JSON.parse(JSON.stringify(call)))
                }
                /**
                 * @param {Names} name
                 * @param {any} request
                 * @param {any} response
                 */
                function respond (name, request, response) {
                    const call = [name, request, response]
                    window.__playwright.mocks.calls.push(JSON.parse(JSON.stringify(call)))
                    setTimeout(() => {
                        for (let listener of listeners) {
                            listener({
                                origin: window.origin,
                                data: {
                                    type: name + 'Response',
                                    success: response
                                }
                            })
                        }
                    }, 0)
                }
                // @ts-ignore
                /** @type {MocksObjectAndroid} */
                const mocksObject = {
                    getRuntimeConfiguration () {
                        recordCall('getRuntimeConfiguration', null, mocks.getRuntimeConfiguration)
                        return respond('getRuntimeConfiguration', null, mocks.getRuntimeConfiguration)
                    },
                    getAvailableInputTypes () {
                        recordCall('getAvailableInputTypes', null, mocks.getAvailableInputTypes)
                        return respond('getAvailableInputTypes', null, mocks.getAvailableInputTypes)
                    },
                    getAutofillData (_request) {
                        recordCall('getRuntimeConfiguration', null, null)
                        throw new Error('unimplemented windows.getAutofillData')
                    },
                    storeFormData (request) {
                        recordCall('storeFormData', request, null)
                    },
                    getAutofillInitData () {
                        recordCall('getAutofillInitData', null, mocks.getAutofillInitData)
                        return respond('getAutofillInitData', null, mocks.getAutofillInitData)
                    },
                    getAutofillCredentials () {
                        recordCall('getAutofillCredentials', null, mocks.getAutofillCredentials)
                        return respond('getAutofillCredentials', null, mocks.getAutofillCredentials)
                    }
                }

                // @ts-ignore
                window.chrome = {
                    webview: {
                        postMessage (input) {
                            if (mocksObject[input.type]) {
                                return mocksObject[input.type](input)
                            } else {
                                throw new Error('windows mock missing for ' + input.type)
                            }
                        },
                        removeEventListener (_name, _listener) {

                        },
                        addEventListener (_name, listener) {
                            listeners.push(listener)
                        }
                    }
                }
            }, mocks)
        },
        withIdentity: function () {
            throw new Error('Function not implemented.')
        },
        withCredentials: function (credentials) {
            mocks.getAutofillInitData.credentials.push(credentials)
            mocks.getAutofillCredentials = credentials
            return this
        }
    }
    return builder
}
