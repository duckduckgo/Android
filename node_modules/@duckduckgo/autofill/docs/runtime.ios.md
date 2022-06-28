## ~`getRuntimeConfiguration()`~

on Apple devices, this data is retrieved from the following string-replacements

- [BrowserServices Kit String replacements](https://github.com/duckduckgo/BrowserServicesKit/blob/main/Sources/BrowserServicesKit/Autofill/AutofillUserScript+SourceProvider.swift#L54-L56)

Internally, we force it into the following shape in order to conform to the following schema definition:
- [Runtime Configuration Schema](https://github.com/duckduckgo/content-scope-scripts/blob/shane/unify-config/src/schema/runtime-configuration.schema.json)

**strings to replace**
```
// INJECT contentScope HERE
// INJECT userUnprotectedDomains HERE
// INJECT userPreferences HERE
```

Directly replace the lines above in the following way:

`str.replace('// INJECT contentScope HERE', 'contentScope = {JSON_HERE}') + ';'`

For example, the 3 variables should look like this (don't forget the semicolon at the end of each!)

```javascript
// INJECT contentScope HERE
contentScope = {
  "features": {
    "autofill": {
      "state": "enabled",
      "exceptions": []
    }
  },
  "unprotectedTemporary": []
};
```

```javascript
// INJECT userUnprotectedDomains HERE
userUnprotectedDomains = [];
```

```javascript
// INJECT userPreferences HERE
userPreferences = {
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
};
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

**`response`** examples

1) autofill a field:

```json
{
  "success": {
    "action": "fill",
    "credentials": {
      "username": "dax@example.com",
      "password": "123456"
    }
  }
}
```

2) re-focus a field (to present the keyboard)

```json
{
  "success": {
    "action": "focus"
  }
}
```

3) Do nothing:

```json
{
  "success": {
    "action": "none"
  }
}
```


