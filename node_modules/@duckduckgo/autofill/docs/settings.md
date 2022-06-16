## `src/Settings.js`

Autofill needs access to certain pieces of state before it can perform any page scanning, 
therefor a 'Settings' object has been introduced to allow this state to be queried and updated before
any page scanning occurs.

## Properties

`settings.featureToggles` 
 
The platform in question will deliver feature toggles (boolean flags) based on device support, user preferences, or a combination of both. Inside Autofill we don't make any distinction about 'how' the toggles are set, we just ask the platform for them and then use them verbatim. This prevents additional logic from bleeding into the Autofill codebase.

- `inputType_credentials` - whether the device can autofill credentials
- `inputType_identities` - whether the device can autofill identities
- `inputType_creditCards` - whether the device can autofill creditCards
- `emailProtection` - whether the device supports email protection
- `password_generation` - if the device can offer generated passwords
- `credentials_saving` - if the device should offer to capture submitted form data to save

---

`settings.availableInputTypes` 

Another set of boolean flags, this time indicating which data types the 
current user can autofill. This is domain specific.

- `credentials` - true if the user has credentials stored for the current domain
- `identities` - true if the user has identities stored (not domain specific)
- `creditCards` - true if the user has creditCards stored (not domain specific)
- `email` - true if the user has logged into email protection (not domain specific)
  - note: `availableInputTypes.email` is only current used on android. In the future all platforms will migrate to this.

## Methods

`settings.refresh(availableInputTypesOverrides)`

Initially, this is called as an additional step that follows data access when the page loads, but
the long-term goal is to remove all initial data access and do everything through this.

**Note:** This method currently accepts 'availableInputTypesOverrides' as a way to support the current version of `macOS`. macOS currently determines which data types it supports by fetching user data on page load, so we use that data to derive `availableInputTypes`. In the future, once macOS supports `getAvailableInputTypes` then we can remove this override.

```javascript
// if we're executing on macOS, use old methods as overrides for `availableInputTypes`
const macOSoverrides = isMac ? {
    credentials: this.hasLocalCredentials(),
    identities: this.hasLocalIdentities(),
    ...etc
} : {}
await settings.refresh(overrides)
```
