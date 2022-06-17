## `getAutofillData`

**request**


<details>
<summary><code>request.getAutofillData.schema.json</code></summary>
<br/>

[./request.getAutofillData.schema.json](./request.getAutofillData.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAutofillDataRequest",
  "title": "GetAutofillDataRequest Request Object",
  "type": "object",
  "description": "This describes the argument given to `getAutofillData(data)`",
  "properties": {
    "inputType": {
      "title": "The input type that triggered the call",
      "description": "This is the combined input type, such as `credentials.username`",
      "type": "string"
    },
    "mainType": {
      "title": "The main input type",
      "type": "string",
      "enum": [
        "credentials",
        "identities",
        "creditCards"
      ]
    },
    "subType": {
      "title": "Just the subtype, such as `password` or `username`",
      "type": "string"
    }
  },
  "required": [
    "inputType",
    "mainType",
    "subType"
  ]
}
```

</details>
  

### GetAutofillDataRequest



```ts

/**
 * GetAutofillDataRequest Request Object
 * 
 * This describes the argument given to `getAutofillData(data)`
 * 
 * @link {./request.getAutofillData.schema.json}
 */
interface GetAutofillDataRequest {
    
    /**
     * The input type that triggered the call
     * This is the combined input type, such as `credentials.username`
     */
    inputType: string
    
    /**
     * The main input type
     */
    mainType: "credentials" | "identities" | "creditCards"
    
    /**
     * Just the subtype, such as `password` or `username`
     */
    subType: string
}
```


**response**


<details>
<summary><code>response.getAutofillData.schema.json</code></summary>
<br/>

[./response.getAutofillData.schema.json](./response.getAutofillData.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAutofillDataResponse",
  "title": "GetAutofillDataResponse",
  "type": "object",
  "properties": {
    "type": {
      "title": "This is the 'type' field on message that may be sent back to the window",
      "description": "Required on Android + Windows devices, optional on iOS",
      "type": "string",
      "const": "getAutofillDataResponse"
    },
    "success": {
      "$id": "#/definitions/AutofillData",
      "title": "GetAutofillDataResponse Success Response",
      "description": "The data returned, containing only fields that will be auto-filled",
      "type": "object",
      "oneOf": [
        {
          "$ref": "#/definitions/Credentials"
        }
      ]
    },
    "error": {
      "$ref": "#/definitions/GenericError"
    }
  },
  "required": [
    "success"
  ]
}
```

</details>
  

### GetAutofillDataResponse



```ts

/**
 * @link {./response.getAutofillData.schema.json}
 */
interface GetAutofillDataResponse {
    
    /**
     * This is the 'type' field on message that may be sent back to the window
     * Required on Android + Windows devices, optional on iOS
     */
    type?: "getAutofillDataResponse"
    
    /**
     * The data returned, containing only fields that will be auto-filled
     */
    success: Credentials
    error?: GenericError
}
```


---
## `getAvailableInputTypes`

**request**


<details>
<summary><code>request.getAvailableInputTypes.schema.json</code></summary>
<br/>

[./request.getAvailableInputTypes.schema.json](./request.getAvailableInputTypes.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAvailableInputTypesRequest",
  "type": "object",
  "title": "GetAvailableInputTypesRequest",
  "description": "This method does not currently send any data"
}
```

</details>
  

### GetAvailableInputTypesRequest



```ts

/**
 * This method does not currently send any data
 * 
 * @link {./request.getAvailableInputTypes.schema.json}
 */
interface GetAvailableInputTypesRequest {
}
```


**response**


<details>
<summary><code>response.getAvailableInputTypes.schema.json</code></summary>
<br/>

[./response.getAvailableInputTypes.schema.json](./response.getAvailableInputTypes.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAvailableInputTypesResponse",
  "type": "object",
  "title": "GetAvailableInputTypesResponse Success Response",
  "properties": {
    "type": {
      "title": "This is the 'type' field on message that may be sent back to the window",
      "description": "Required on Android + Windows devices, optional on iOS",
      "type": "string",
      "const": "getAvailableInputTypesResponse"
    },
    "success": {
      "type": "object",
      "$id": "#/definitions/AvailableInputTypes",
      "properties": {
        "credentials": {
          "description": "true if *any* credentials are available",
          "type": "boolean"
        },
        "identities": {
          "description": "true if *any* identities are available",
          "type": "boolean"
        },
        "creditCards": {
          "description": "true if *any* credit cards are available",
          "type": "boolean"
        },
        "email": {
          "description": "true if signed in for Email Protection",
          "type": "boolean"
        }
      }
    },
    "error": {
      "$ref": "#/definitions/GenericError"
    }
  },
  "required": [
    "success"
  ]
}
```

</details>
  

### GetAvailableInputTypesResponse



```ts

/**
 * GetAvailableInputTypesResponse Success Response
 * 
 * @link {./response.getAvailableInputTypes.schema.json}
 */
interface GetAvailableInputTypesResponse {
    
    /**
     * This is the 'type' field on message that may be sent back to the window
     * Required on Android + Windows devices, optional on iOS
     */
    type?: "getAvailableInputTypesResponse"
    success: AvailableInputTypes
    error?: GenericError
}
```


### AvailableInputTypes



```ts

/**
 * @link {./response.getAvailableInputTypes.schema.json}
 */
interface AvailableInputTypes {
    credentials?: boolean
    identities?: boolean
    creditCards?: boolean
    email?: boolean
}
```


---
## `getRuntimeConfiguration`

**request**


<details>
<summary><code>request.getRuntimeConfiguration.schema.json</code></summary>
<br/>

[./request.getRuntimeConfiguration.schema.json](./request.getRuntimeConfiguration.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetRuntimeConfigurationRequest",
  "title": "GetRuntimeConfigurationRequest",
  "description": "This method does not currently send any data"
}
```

</details>
  

### GetRuntimeConfigurationRequest



```ts

/**
 * This method does not currently send any data
 * 
 * @link {./request.getRuntimeConfiguration.schema.json}
 */
interface GetRuntimeConfigurationRequest {
}
```


**response**


<details>
<summary><code>response.getRuntimeConfiguration.schema.json</code></summary>
<br/>

[./response.getRuntimeConfiguration.schema.json](./response.getRuntimeConfiguration.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetRuntimeConfigurationResponse",
  "type": "object",
  "title": "GetRuntimeConfigurationResponse Success Response",
  "description": "Data that can be understood by @duckduckgo/content-scope-scripts",
  "properties": {
    "type": {
      "title": "This is the 'type' field on message that may be sent back to the window",
      "description": "Required on Android + Windows devices, optional on iOS",
      "type": "string",
      "const": "getRuntimeConfigurationResponse"
    },
    "success": {
      "description": "This is loaded dynamically from @duckduckgo/content-scope-scripts/src/schema/runtime-configuration.schema.json",
      "$ref": "#/definitions/RuntimeConfiguration"
    },
    "error": {
      "$ref": "#/definitions/GenericError"
    }
  },
  "required": [
    "success"
  ]
}
```

</details>
  

### GetRuntimeConfigurationResponse



```ts

/**
 * GetRuntimeConfigurationResponse Success Response
 * 
 * Data that can be understood by @duckduckgo/content-scope-scripts
 * 
 * @link {./response.getRuntimeConfiguration.schema.json}
 */
interface GetRuntimeConfigurationResponse {
    
    /**
     * This is the 'type' field on message that may be sent back to the window
     * Required on Android + Windows devices, optional on iOS
     */
    type?: "getRuntimeConfigurationResponse"
    success: RuntimeConfiguration
    error?: GenericError
}
```


---
## `showAutofillParent`

**request**


<details>
<summary><code>request.showAutofillParent.schema.json</code></summary>
<br/>

[./request.showAutofillParent.schema.json](./request.showAutofillParent.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/ShowAutofillParentRequest",
  "title": "ShowAutofillParentRequest Request Object",
  "type": "object",
  "description": "This describes the argument given to showAutofillParent(data)",
  "properties": {
    "wasFromClick": {
      "type": "boolean"
    },
    "inputTop": {
      "type": "number"
    },
    "inputLeft": {
      "type": "number"
    },
    "inputHeight": {
      "type": "number"
    },
    "inputWidth": {
      "type": "number"
    },
    "serializedInputContext": {
      "description": "JSON string that will be available from `getAutofillInitData()`",
      "type": "string"
    }
  },
  "required": [
    "wasFromClick",
    "inputTop",
    "inputLeft",
    "inputHeight",
    "inputWidth",
    "serializedInputContext"
  ]
}
```

</details>
  

### ShowAutofillParentRequest



```ts

/**
 * ShowAutofillParentRequest Request Object
 * 
 * This describes the argument given to showAutofillParent(data)
 * 
 * @link {./request.showAutofillParent.schema.json}
 */
interface ShowAutofillParentRequest {
    wasFromClick: boolean
    inputTop: number
    inputLeft: number
    inputHeight: number
    inputWidth: number
    
    /**
     * JSON string that will be available from `getAutofillInitData()`
     */
    serializedInputContext: string
}
```


---
## `storeFormData`

**request**


<details>
<summary><code>request.storeFormData.schema.json</code></summary>
<br/>

[./request.storeFormData.schema.json](./request.storeFormData.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/StoreFormDataRequest",
  "title": "StoreFormData Request",
  "type": "object",
  "description": "Autofill could send this data at any point. \n\nIt will **not** listen for a response, it's expected that the native side will handle",
  "properties": {
    "credentials": {
      "type": "object",
      "$id": "#/definitions/CredentialsOutgoing",
      "properties": {
        "username": {
          "description": "Optional username",
          "type": "string"
        },
        "password": {
          "description": "Optional password",
          "type": "string"
        }
      }
    },
    "identities": {
      "description": "todo(Shane): Rename to identity",
      "$ref": "#/definitions/Identity"
    },
    "creditCards": {
      "description": "todo(Shane): Rename to creditCard",
      "$ref": "#/definitions/CreditCard"
    }
  }
}
```

</details>
  

### StoreFormDataRequest



```ts

/**
 * StoreFormData Request
 * 
 * Autofill could send this data at any point. 
 * 
 * It will **not** listen for a response, it's expected that the native side will handle
 * 
 * @link {./request.storeFormData.schema.json}
 */
interface StoreFormDataRequest {
    credentials?: CredentialsOutgoing
    identities?: Identity
    creditCards?: CreditCard
}
```


### CredentialsOutgoing



```ts

/**
 * @link {./request.storeFormData.schema.json}
 */
interface CredentialsOutgoing {
    
    /**
     * Optional username
     */
    username?: string
    
    /**
     * Optional password
     */
    password?: string
}
```


---
## `getAutofillInitData`

**response**


<details>
<summary><code>response.getAutofillInitData.schema.json</code></summary>
<br/>

[./response.getAutofillInitData.schema.json](./response.getAutofillInitData.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAutofillInitDataResponse",
  "title": "GetAutofillInitDataResponse",
  "type": "object",
  "properties": {
    "type": {
      "title": "This is the 'type' field on message that may be sent back to the window",
      "description": "Required on Android + Windows devices, optional on iOS",
      "type": "string",
      "const": "getAutofillInitDataResponse"
    },
    "success": {
      "title": "GetAutofillInitDataResponse Success Response",
      "$id": "#/definitions/AutofillInitData",
      "type": "object",
      "properties": {
        "credentials": {
          "type": "array",
          "items": {
            "$ref": "#/definitions/Credentials"
          }
        },
        "identities": {
          "type": "array",
          "items": {
            "type": "object"
          }
        },
        "creditCards": {
          "type": "array",
          "items": {
            "type": "object"
          }
        },
        "serializedInputContext": {
          "description": "A clone of the `serializedInputContext` that was sent in the request",
          "type": "string"
        }
      },
      "required": [
        "serializedInputContext",
        "credentials",
        "creditCards",
        "identities"
      ]
    },
    "error": {
      "$ref": "#/definitions/GenericError"
    }
  },
  "required": [
    "success"
  ]
}
```

</details>
  

### GetAutofillInitDataResponse



```ts

/**
 * @link {./response.getAutofillInitData.schema.json}
 */
interface GetAutofillInitDataResponse {
    
    /**
     * This is the 'type' field on message that may be sent back to the window
     * Required on Android + Windows devices, optional on iOS
     */
    type?: "getAutofillInitDataResponse"
    success: AutofillInitData
    error?: GenericError
}
```


### AutofillInitData



```ts

/**
 * GetAutofillInitDataResponse Success Response
 * 
 * @link {./response.getAutofillInitData.schema.json}
 */
interface AutofillInitData {
    credentials: Credentials[]
    identities: any[]
    creditCards: any[]
    
    /**
     * A clone of the `serializedInputContext` that was sent in the request
     */
    serializedInputContext: string
}
```


---
## `credentials`


<details>
<summary><code>credentials.schema.json</code></summary>
<br/>

[./credentials.schema.json](./credentials.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/Credentials",
  "title": "Credentials",
  "type": "object",
  "properties": {
    "id": {
      "title": "Credentials.id",
      "description": "If present, must be a string",
      "type": "string"
    },
    "username": {
      "title": "Credentials.username",
      "description": "This field is always present, but sometimes it could be an empty string",
      "type": "string"
    },
    "password": {
      "title": "Credentials.password",
      "description": "This field may be empty or absent altogether, which is why it's not marked as 'required'",
      "type": "string"
    }
  },
  "required": [
    "username"
  ]
}
```

</details>
  

### Credentials



```ts

/**
 * @link {./credentials.schema.json}
 */
interface Credentials {
    
    /**
     * Credentials.id
     * If present, must be a string
     */
    id?: string
    
    /**
     * Credentials.username
     * This field is always present, but sometimes it could be an empty string
     */
    username: string
    
    /**
     * Credentials.password
     * This field may be empty or absent altogether, which is why it's not marked as 'required'
     */
    password?: string
}
```


---
## `creditCard`


<details>
<summary><code>creditCard.schema.json</code></summary>
<br/>

[./creditCard.schema.json](./creditCard.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/CreditCard",
  "title": "CreditCard",
  "type": "object",
  "properties": {
    "id": {
      "type": "string"
    },
    "title": {
      "type": "string"
    },
    "displayNumber": {
      "type": "string"
    },
    "cardName": {
      "type": "string"
    },
    "cardSecurityCode": {
      "type": "string"
    },
    "expirationMonth": {
      "type": "string"
    },
    "expirationYear": {
      "type": "string"
    },
    "cardNumber": {
      "type": "string"
    }
  },
  "required": [
    "username"
  ]
}
```

</details>
  

### CreditCard



```ts

/**
 * @link {./creditCard.schema.json}
 */
interface CreditCard {
    id?: string
    title?: string
    displayNumber?: string
    cardName?: string
    cardSecurityCode?: string
    expirationMonth?: string
    expirationYear?: string
    cardNumber?: string
}
```


---
## `error`


<details>
<summary><code>error.schema.json</code></summary>
<br/>

[./error.schema.json](./error.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GenericError",
  "title": "GenericError",
  "type": "object",
  "properties": {
    "message": {
      "type": "string"
    }
  },
  "required": [
    "message"
  ]
}
```

</details>
  

### GenericError



```ts

/**
 * @link {./error.schema.json}
 */
interface GenericError {
    message: string
}
```


---
## `identity`


<details>
<summary><code>identity.schema.json</code></summary>
<br/>

[./identity.schema.json](./identity.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/Identity",
  "title": "Identity",
  "description": "A user's Identity",
  "type": "object",
  "properties": {
    "id": {
      "type": "string"
    },
    "title": {
      "description": "This is the only required field",
      "type": "string"
    },
    "firstName": {
      "type": "string"
    },
    "middleName": {
      "type": "string"
    },
    "lastName": {
      "type": "string"
    },
    "birthdayDay": {
      "type": "string"
    },
    "birthdayMonth": {
      "type": "string"
    },
    "birthdayYear": {
      "type": "string"
    },
    "addressStreet": {
      "type": "string"
    },
    "addressStreet2": {
      "type": "string"
    },
    "addressCity": {
      "type": "string"
    },
    "addressProvince": {
      "type": "string"
    },
    "addressPostalCode": {
      "type": "string"
    },
    "addressCountryCode": {
      "type": "string"
    },
    "phone": {
      "type": "string"
    },
    "emailAddress": {
      "type": "string"
    }
  },
  "required": [
    "title"
  ]
}
```

</details>
  

### Identity



```ts

/**
 * A user's Identity
 * 
 * @link {./identity.schema.json}
 */
interface Identity {
    id?: string
    
    /**
     * This is the only required field
     */
    title: string
    firstName?: string
    middleName?: string
    lastName?: string
    birthdayDay?: string
    birthdayMonth?: string
    birthdayYear?: string
    addressStreet?: string
    addressStreet2?: string
    addressCity?: string
    addressProvince?: string
    addressPostalCode?: string
    addressCountryCode?: string
    phone?: string
    emailAddress?: string
}
```


---
## `runtime-configuration`


<details>
<summary><code>runtime-configuration.schema.json</code></summary>
<br/>

[./runtime-configuration.schema.json](./runtime-configuration.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/RuntimeConfiguration",
  "type": "object",
  "additionalProperties": false,
  "title": "Runtime Configuration Schema",
  "description": "Required Properties to enable an instance of RuntimeConfiguration",
  "properties": {
    "contentScope": {
      "$ref": "#/definitions/ContentScope"
    },
    "userUnprotectedDomains": {
      "type": "array",
      "items": {}
    },
    "userPreferences": {
      "$ref": "#/definitions/UserPreferences"
    }
  },
  "required": [
    "contentScope",
    "userPreferences",
    "userUnprotectedDomains"
  ],
  "definitions": {
    "ContentScope": {
      "type": "object",
      "additionalProperties": true,
      "properties": {
        "features": {
          "$ref": "#/definitions/ContentScopeFeatures"
        },
        "unprotectedTemporary": {
          "type": "array",
          "items": {}
        }
      },
      "required": [
        "features",
        "unprotectedTemporary"
      ],
      "title": "ContentScope"
    },
    "ContentScopeFeatures": {
      "type": "object",
      "additionalProperties": {
        "$ref": "#/definitions/ContentScopeFeatureItem"
      },
      "title": "ContentScopeFeatures"
    },
    "ContentScopeFeatureItem": {
      "type": "object",
      "properties": {
        "exceptions": {
          "type": "array",
          "items": {}
        },
        "state": {
          "type": "string"
        },
        "settings": {
          "type": "object"
        }
      },
      "required": [
        "exceptions",
        "state"
      ],
      "title": "ContentScopeFeatureItem"
    },
    "UserPreferences": {
      "type": "object",
      "properties": {
        "debug": {
          "type": "boolean"
        },
        "platform": {
          "$ref": "#/definitions/Platform"
        },
        "features": {
          "$ref": "#/definitions/UserPreferencesFeatures"
        }
      },
      "required": [
        "debug",
        "features",
        "platform"
      ],
      "title": "UserPreferences"
    },
    "UserPreferencesFeatures": {
      "type": "object",
      "additionalProperties": {
        "$ref": "#/definitions/UserPreferencesFeatureItem"
      },
      "title": "UserPreferencesFeatures"
    },
    "UserPreferencesFeatureItem": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "settings": {
          "$ref": "#/definitions/Settings"
        }
      },
      "required": [
        "settings"
      ],
      "title": "UserPreferencesFeatureItem"
    },
    "Settings": {
      "type": "object",
      "additionalProperties": true,
      "title": "Settings"
    },
    "Platform": {
      "type": "object",
      "properties": {
        "name": {
          "type": "string",
          "enum": [
            "ios",
            "macos",
            "windows",
            "extension",
            "android",
            "unknown"
          ]
        }
      },
      "required": [
        "name"
      ],
      "title": "Platform"
    }
  }
}
```

</details>
  

### RuntimeConfiguration



```ts

/**
 * Runtime Configuration Schema
 * 
 * Required Properties to enable an instance of RuntimeConfiguration
 * 
 * @link {./runtime-configuration.schema.json}
 */
interface RuntimeConfiguration {
    contentScope: RuntimeConfigurationContentScope
    userUnprotectedDomains: any[]
    userPreferences: RuntimeConfigurationUserPreferences
}
```


### RuntimeConfigurationPlatform



```ts

/**
 * Platform
 * 
 * @link {./runtime-configuration.schema.json}
 */
interface RuntimeConfigurationPlatform {
    name: "ios" | "macos" | "windows" | "extension" | "android" | "unknown"
}
```


### RuntimeConfigurationSettings



```ts

/**
 * Settings
 * 
 * @link {./runtime-configuration.schema.json}
 */
interface RuntimeConfigurationSettings {
    [index: string]: unknown
}
```


### RuntimeConfigurationUserPreferencesFeatureItem



```ts

/**
 * UserPreferencesFeatureItem
 * 
 * @link {./runtime-configuration.schema.json}
 */
interface RuntimeConfigurationUserPreferencesFeatureItem {
    settings: RuntimeConfigurationSettings
}
```


### RuntimeConfigurationUserPreferencesFeatures



```ts

/**
 * UserPreferencesFeatures
 * 
 * @link {./runtime-configuration.schema.json}
 */
interface RuntimeConfigurationUserPreferencesFeatures {
    [index: string]: RuntimeConfigurationUserPreferencesFeatureItem
}
```


### RuntimeConfigurationUserPreferences



```ts

/**
 * UserPreferences
 * 
 * @link {./runtime-configuration.schema.json}
 */
interface RuntimeConfigurationUserPreferences {
    debug: boolean
    platform: RuntimeConfigurationPlatform
    features: RuntimeConfigurationUserPreferencesFeatures
}
```


### RuntimeConfigurationContentScopeFeatureItem



```ts

/**
 * ContentScopeFeatureItem
 * 
 * @link {./runtime-configuration.schema.json}
 */
interface RuntimeConfigurationContentScopeFeatureItem {
    exceptions: any[]
    state: string
}
```


### RuntimeConfigurationContentScopeFeatures



```ts

/**
 * ContentScopeFeatures
 * 
 * @link {./runtime-configuration.schema.json}
 */
interface RuntimeConfigurationContentScopeFeatures {
    [index: string]: RuntimeConfigurationContentScopeFeatureItem
}
```


### RuntimeConfigurationContentScope



```ts

/**
 * ContentScope
 * 
 * @link {./runtime-configuration.schema.json}
 */
interface RuntimeConfigurationContentScope {
    features: RuntimeConfigurationContentScopeFeatures
    unprotectedTemporary: any[]
}
```


---
## `settings`


<details>
<summary><code>settings.schema.json</code></summary>
<br/>

[./settings.schema.json](./settings.schema.json)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/AutofillSettings",
  "title": "AutofillSettings",
  "type": "object",
  "properties": {
    "featureToggles": {
      "title": "FeatureToggles",
      "$id": "#/definitions/FeatureToggles",
      "description": "These are toggles used throughout the application to enable/disable features fully",
      "type": "object",
      "properties": {
        "inputType_credentials": {
          "type": "boolean"
        },
        "inputType_identities": {
          "type": "boolean"
        },
        "inputType_creditCards": {
          "type": "boolean"
        },
        "emailProtection": {
          "type": "boolean"
        },
        "password_generation": {
          "type": "boolean"
        },
        "credentials_saving": {
          "type": "boolean"
        }
      },
      "required": [
        "inputType_credentials",
        "inputType_identities",
        "inputType_creditCards",
        "emailProtection",
        "password_generation",
        "credentials_saving"
      ]
    }
  },
  "required": [
    "featureToggles"
  ]
}
```

</details>
  

### AutofillSettings



```ts

/**
 * @link {./settings.schema.json}
 */
interface AutofillSettings {
    featureToggles: FeatureToggles
}
```


### FeatureToggles



```ts

/**
 * These are toggles used throughout the application to enable/disable features fully
 * 
 * @link {./settings.schema.json}
 */
interface FeatureToggles {
    inputType_credentials: boolean
    inputType_identities: boolean
    inputType_creditCards: boolean
    emailProtection: boolean
    password_generation: boolean
    credentials_saving: boolean
}
```
