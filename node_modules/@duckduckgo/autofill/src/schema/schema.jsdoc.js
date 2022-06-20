// Do not edit, this was created by `scripts/schema.js`
/**
 * @link {import("./credentials.schema.json")}
 * @typedef Credentials
 * @property {string} [id] Credentials.id If present, must be a string
 * @property {string} username Credentials.username This field is always present, but sometimes it could be an empty string
 * @property {string} [password] Credentials.password This field may be empty or absent altogether, which is why it's not marked as 'required'
 */
/**
 * @link {import("./creditCard.schema.json")}
 * @typedef CreditCard
 * @property {string} [id]
 * @property {string} [title]
 * @property {string} [displayNumber]
 * @property {string} [cardName]
 * @property {string} [cardSecurityCode]
 * @property {string} [expirationMonth]
 * @property {string} [expirationYear]
 * @property {string} [cardNumber]
 */
/**
 * @link {import("./error.schema.json")}
 * @typedef GenericError
 * @property {string} message
 */
/**
 * @link {import("./identity.schema.json")}
 * @typedef Identity A user's Identity
 * @property {string} [id]
 * @property {string} title This is the only required field
 * @property {string} [firstName]
 * @property {string} [middleName]
 * @property {string} [lastName]
 * @property {string} [birthdayDay]
 * @property {string} [birthdayMonth]
 * @property {string} [birthdayYear]
 * @property {string} [addressStreet]
 * @property {string} [addressStreet2]
 * @property {string} [addressCity]
 * @property {string} [addressProvince]
 * @property {string} [addressPostalCode]
 * @property {string} [addressCountryCode]
 * @property {string} [phone]
 * @property {string} [emailAddress]
 */
/**
 * @link {import("./request.getAutofillCredentials.schema.json")}
 * @typedef GetAutofillCredentials GetAutofillCredentials Request Object This describes the argument given to `getAutofillCredentials`
 * @property {string} id
 */
/**
 * @link {import("./request.getAutofillData.schema.json")}
 * @typedef GetAutofillDataRequest GetAutofillDataRequest Request Object This describes the argument given to `getAutofillData(data)`
 * @property {string} inputType The input type that triggered the call This is the combined input type, such as `credentials.username`
 * @property {"credentials" | "identities" | "creditCards"} mainType The main input type
 * @property {string} subType Just the subtype, such as `password` or `username`
 */
/**
 * @link {import("./request.getAvailableInputTypes.schema.json")}
 * @typedef {unknown} GetAvailableInputTypesRequest This method does not currently send any data
 */
/**
 * @link {import("./request.getRuntimeConfiguration.schema.json")}
 * @typedef {unknown} GetRuntimeConfigurationRequest This method does not currently send any data
 */
/**
 * @link {import("./request.showAutofillParent.schema.json")}
 * @typedef ShowAutofillParentRequest ShowAutofillParentRequest Request Object This describes the argument given to showAutofillParent(data)
 * @property {boolean} wasFromClick
 * @property {number} inputTop
 * @property {number} inputLeft
 * @property {number} inputHeight
 * @property {number} inputWidth
 * @property {string} serializedInputContext JSON string that will be available from `getAutofillInitData()`
 */
/**
 * @link {import("./request.storeFormData.schema.json")}
 * @typedef StoreFormDataRequest StoreFormData Request
 * Autofill could send this data at any point. 
 * It will **not** listen for a response, it's expected that the native side will handle
 * @property {CredentialsOutgoing} [credentials]
 * @property {Identity} [identities]
 * @property {CreditCard} [creditCards]
 */
/**
 * @link {import("./request.storeFormData.schema.json")}
 * @typedef CredentialsOutgoing
 * @property {string} [username] Optional username
 * @property {string} [password] Optional password
 */
/**
 * @link {import("./response.getAutofillData.schema.json")}
 * @typedef GetAutofillDataResponse
 * @property {"getAutofillDataResponse"} [type] This is the 'type' field on message that may be sent back to the window Required on Android + Windows devices, optional on iOS
 * @property {Credentials} success The data returned, containing only fields that will be auto-filled
 * @property {GenericError} [error]
 */
/**
 * @link {import("./response.getAutofillInitData.schema.json")}
 * @typedef GetAutofillInitDataResponse
 * @property {"getAutofillInitDataResponse"} [type] This is the 'type' field on message that may be sent back to the window Required on Android + Windows devices, optional on iOS
 * @property {AutofillInitData} success
 * @property {GenericError} [error]
 */
/**
 * @link {import("./response.getAutofillInitData.schema.json")}
 * @typedef AutofillInitData GetAutofillInitDataResponse Success Response
 * @property {Credentials[]} credentials
 * @property {any[]} identities
 * @property {any[]} creditCards
 * @property {string} serializedInputContext A clone of the `serializedInputContext` that was sent in the request
 */
/**
 * @link {import("./response.getAvailableInputTypes.schema.json")}
 * @typedef GetAvailableInputTypesResponse GetAvailableInputTypesResponse Success Response
 * @property {"getAvailableInputTypesResponse"} [type] This is the 'type' field on message that may be sent back to the window Required on Android + Windows devices, optional on iOS
 * @property {AvailableInputTypes} success
 * @property {GenericError} [error]
 */
/**
 * @link {import("./response.getAvailableInputTypes.schema.json")}
 * @typedef AvailableInputTypes
 * @property {boolean} [credentials]
 * @property {boolean} [identities]
 * @property {boolean} [creditCards]
 * @property {boolean} [email]
 */
/**
 * @link {import("./response.getRuntimeConfiguration.schema.json")}
 * @typedef GetRuntimeConfigurationResponse GetRuntimeConfigurationResponse Success Response Data that can be understood by \@duckduckgo/content-scope-scripts
 * @property {"getRuntimeConfigurationResponse"} [type] This is the 'type' field on message that may be sent back to the window Required on Android + Windows devices, optional on iOS
 * @property {RuntimeConfiguration} success
 * @property {GenericError} [error]
 */
/**
 * @link {import("./runtime-configuration.schema.json")}
 * @typedef RuntimeConfiguration Runtime Configuration Schema Required Properties to enable an instance of RuntimeConfiguration
 * @property {RuntimeConfigurationContentScope} contentScope
 * @property {any[]} userUnprotectedDomains
 * @property {RuntimeConfigurationUserPreferences} userPreferences
 */
/**
 * @link {import("./runtime-configuration.schema.json")}
 * @typedef RuntimeConfigurationPlatform Platform
 * @property {"ios" | "macos" | "windows" | "extension" | "android" | "unknown"} name
 */
/**
 * @link {import("./runtime-configuration.schema.json")}
 * @typedef {Record<string, unknown>} RuntimeConfigurationSettings Settings
 */
/**
 * @link {import("./runtime-configuration.schema.json")}
 * @typedef RuntimeConfigurationUserPreferencesFeatureItem UserPreferencesFeatureItem
 * @property {RuntimeConfigurationSettings} settings
 */
/**
 * @link {import("./runtime-configuration.schema.json")}
 * @typedef {Record<string, RuntimeConfigurationUserPreferencesFeatureItem>} RuntimeConfigurationUserPreferencesFeatures UserPreferencesFeatures
 */
/**
 * @link {import("./runtime-configuration.schema.json")}
 * @typedef RuntimeConfigurationUserPreferences UserPreferences
 * @property {boolean} debug
 * @property {RuntimeConfigurationPlatform} platform
 * @property {RuntimeConfigurationUserPreferencesFeatures} features
 */
/**
 * @link {import("./runtime-configuration.schema.json")}
 * @typedef RuntimeConfigurationContentScopeFeatureItem ContentScopeFeatureItem
 * @property {any[]} exceptions
 * @property {string} state
 */
/**
 * @link {import("./runtime-configuration.schema.json")}
 * @typedef {Record<string, RuntimeConfigurationContentScopeFeatureItem>} RuntimeConfigurationContentScopeFeatures ContentScopeFeatures
 */
/**
 * @link {import("./runtime-configuration.schema.json")}
 * @typedef RuntimeConfigurationContentScope ContentScope
 * @property {RuntimeConfigurationContentScopeFeatures} features
 * @property {any[]} unprotectedTemporary
 */
/**
 * @link {import("./settings.schema.json")}
 * @typedef AutofillSettings
 * @property {FeatureToggles} featureToggles
 */
/**
 * @link {import("./settings.schema.json")}
 * @typedef FeatureToggles These are toggles used throughout the application to enable/disable features fully
 * @property {boolean} inputType_credentials
 * @property {boolean} inputType_identities
 * @property {boolean} inputType_creditCards
 * @property {boolean} emailProtection
 * @property {boolean} password_generation
 * @property {boolean} credentials_saving
 */
