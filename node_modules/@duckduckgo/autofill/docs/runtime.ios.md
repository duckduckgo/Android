## ~`getRuntimeConfiguration()`~

on Apple devices, this data is retrieved from the following string-replacements

- [BrowserServices Kit String replacements](https://github.com/duckduckgo/BrowserServicesKit/blob/main/Sources/BrowserServicesKit/Autofill/AutofillUserScript+SourceProvider.swift#L54-L56)

Internally, we force it into the following shape in order to conform to the following schema definition:
- [Runtime Configuration Schema](https://github.com/duckduckgo/content-scope-scripts/blob/shane/unify-config/src/schema/runtime-configuration.schema.json)

```json
{
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
        "name": "ios"
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

## `getAvailableInputTypes()`

see:

- [../src/schema/response.getAvailableInputTypes.schema.json](../src/schema/response.getAvailableInputTypes.schema.json)

This represents which input types we can autofill for the current user.

```json
{
  "success": {
    "email": true,
    "credentials": true
  }
}
```

---

## `storeFormData(data)`

see:

- TODO: Schema for the 'data' argument above

```json
{
  "credentials": {
    "username": "dax@duck.com",
    "password": "123456"
  }
}
```

---

## `getAutofillData(request)`

see: 
 
- [../src/schema/request.getAutofillData.schema.json](../src/schema/request.getAutofillData.schema.json)
- [../src/schema/response.getAutofillData.schema.json](../src/schema/response.getAutofillData.schema.json)

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
  "success": {
    "username": "dax@example.com",
    "password": "123456"
  }
}
```
