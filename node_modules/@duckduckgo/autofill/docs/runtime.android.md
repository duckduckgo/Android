## `window.BrowserAutofill.getRuntimeConfiguration()`

- Response Message via: `window.postMessage(response)`
  - See [Response Schema](../src/schema/response.getRuntimeConfiguration.schema.json)
- Autofill will `JSON.parse(string)` this response.
- [Runtime Configuration Schema (linked from above, but in a separate repo)](https://github.com/duckduckgo/content-scope-scripts/blob/shane/unify-config/src/schema/runtime-configuration.schema.json)

**`response`** example

```json
{
  "type": "getRuntimeConfigurationResponse",
  "success": {
    "contentScope": {
      "features": {
        "autofill": {
          "state": "enabled",
          "exceptions": []
        }
      },
      "unprotectedTemporary": []
    },
    "userUnprotectedDomains": [],
    "userPreferences": {
      "debug": false,
      "platform": {
        "name": "android"
      },
      "features": {
        "autofill": {
          "settings": {
            "featureToggles": {
              "inputType_credentials": true,
              "inputType_identities": false,
              "inputType_creditCards": false,
              "emailProtection": true,
              "password_generation": false,
              "credentials_saving": true
            }
          }
        }
      }
    }
  }
}
```

--- 

## `window.BrowserAutofill.getAvailableInputTypes()`

This represents which input types we can autofill for the current user.

- Response Message via: `window.postMessage(response)`
  - See [Response Schema](../src/schema/response.getAvailableInputTypes.schema.json)
- Autofill will `JSON.parse(string)` the response.

**`response`** example

```json
{
  "type": "getAvailableInputTypesResponse",
  "success": {
    "email": true,
    "credentials": true
  }
}
```

---

## `window.BrowserAutofill.storeFormData(data)`

**data** type: `string`

Autofill will send a *string* of JSON data, conforming to the following schema: (TODO: Add schema)

- Currently, autofill doesn't care/listen for any response.
- TODO: Schema for the 'data' argument above

**request example**

```js
const data = {
  "credentials": {
    "username": "dax@duck.com",
    "password": "123456"
  }
}
window.BrowserAutofill.storeFormData(data)
```


```json
{
  "credentials": {
    "username": "dax@duck.com",
    "password": "123456"
  }
}
```

---

## `window.BrowserAutofill.getAutofillData(request)`

- Autofill will send `request` as a string of JSON 
- See: [../src/schema/request.getAutofillData.schema.json](../src/schema/request.getAutofillData.schema.json)
- Response Message via: `window.postMessage(response)`
  - See: [../src/schema/response.getAutofillData.schema.json](../src/schema/response.getAutofillData.schema.json)

**`request`** example

```json
{
  "type": "credentials.username",
  "mainType": "credentials",
  "subType": "username"
}
```

**`response`** example

```json
{
  "type": "getAutofillDataResponse",
  "success": {
    "username": "dax@example.com",
    "password": "123456"
  }
}
```
