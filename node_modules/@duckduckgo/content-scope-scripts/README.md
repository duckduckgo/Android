# Content scope scripts

Content Scope Scripts handles injecting in DOM modifications in a browser context; it's a cross platform solution that requires some minimal platform hooks.

## Content Scope Features API

Each platform calls into the API exposed by content-scope-features.js where the relevant JavaScript file is included from features/. This file loads the relevant platform enabled features. The platform itself should adhere to the features lifecycle when implementing.

The exposed API is a global called contentScopeFeatures and has three methods:
- load
    - Calls the load method on all of the features
- init
    - Calls the init method on all of the features
    - This should be passed the arguments object which has the following keys:
        - 'debug' true if debuging should be enabled
        - 'globalPrivacyControlValue' false if the user has disabled GPC.
        - 'sessionKey' a unique session based key.
        - 'cookie' TODO
        - 'site' which is an object with:
            - 'isBroken' true if remote config has an exception.
            - 'allowlisted' true if the user has disabled protections.
            - 'domain' the hostname of the site in the URL bar
            - 'enabledFeatures' this is an array of features/ to enable
- update
    - Calls the update method on all of the features

## Features

These files stored in the features directory must include an init function and optionally update and load explained in the features lifecycle.

## Features Lifecycle

There are three stages that the content scope code is hooked into the platform:
- load
    - This should be reserved for work that should happen that could cause a delay in loading the feature.
    - Given the current limitations of how we inject our code we don't have the Privacy Remote Configuration exceptions so authors should be wary of actually loading anything that would modify the page (and potentially breaking it).
    - This limitation may be re-addressed in manifest v3
    - One exception here is the first party cookie protections that are triggered on init to prevent race conditions.
- init
    - This is the main place that features are actually loaded into the extension.
- update
    - This allows the feature to be sent updates from the browser.
    - If this is triggered before init, these updates will be queued and triggered straight after.

### Platform specific integration details

The [inject/](https://github.com/duckduckgo/content-scope-scripts/tree/main/inject) directory handles platform specific differences and is glue code into calling the contentScopeFeatures API.

- In Firefox the code is loaded as a standard extension content script.
- For Apple the code is a UserScript that has some string replacements for properties and loads in as the page scope.
    - Note: currently we don't implement the update calls as it's only required by cookie protections which we don't implement.
- All other browsers the code is stringified, base64 encoded and injected in as a self deleting <script> tag.

In the built output you will see these dramatic differences in the bundled code which is created into: /build

### Features scope injection utilities

To handle the difference in scope injection we expose multiple utilities which behave differently per browser in src/utils.js. for Firefox the code exposed handles [xrays correctly](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Sharing_objects_with_page_scripts) without needing the features to be authored differently.

- defineProperty
    - defineProperty(object, propertyName, descriptor) behaves the same as Object.defineProperty(object, propertyName, descriptor) 
    - The difference is for Firefox we export the relevant functions so it can go across the xray
- DDGProxy
    - Behaves a lot like new window.Proxy with a few differences:
        - has an overload function to actually apply the function to the native property.
        - Stores the native original property in _native such that it can be called elsewhere if needed without going through the proxy.
- DDGReflect
    - Calls into wrappedJSObject.Reflect for Firefox but otherwise exactly the same as [window.Reflect](Sources/BrowserServicesKit/UserScript/ContentScopeUserScript.swift) 
