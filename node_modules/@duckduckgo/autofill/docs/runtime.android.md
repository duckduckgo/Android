## ~`getRuntimeConfiguration()`~

on android devices, this data is retrieved from the following string-replacements:

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

This represents which input types we can autofill for the current user.


**strings to replace**
```
// INJECT availableInputTypes HERE
```

Directly replace the line above in the following way:

`str.replace('// INJECT availableInputTypes HERE', 'contentScope = {JSON_HERE}') + ';'`

```javascript
// INJECT availableInputTypes HERE
availableInputTypes = {
  "email": true,
  "credentials": true
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

2) re-focus a field (to present the keyboard where possible)

```json
{
  "success": {
    "action": "focus"
  }
}
```

3) do nothing

```json
{
  "success": {
    "action": "none"
  }
}
```
