# Content scope scripts

Content Scope Scripts handles injecting in DOM modifications in a browser context; it's a cross platform solution that requires some minimal platform hooks.

## Content Scope Features API

Each platform calls into the API exposed by content-scope-features.js where the relevant JavaScript file is included from features/. This file loads the relevant platform enabled features. The platform itself should adhere to the features lifecycle when implementing.

The exposed API is a global called contentScopeFeatures and has three methods:
- load
    - Calls the load method on all the features
- init
    - Calls the init method on all the features
    - This should be passed the arguments object which has the following keys:
        - 'platform' which is an object with:
            - 'name' which is a string of 'android', 'ios', 'macos' or 'extension'
        - 'debug' true if debugging should be enabled
        - 'globalPrivacyControlValue' false if the user has disabled GPC.
        - 'sessionKey' a unique session based key.
        - 'cookie' TODO
        - 'site' which is an object with:
            - 'isBroken' true if remote config has an exception.
            - 'allowlisted' true if the user has disabled protections.
            - 'domain' the hostname of the site in the URL bar
            - 'enabledFeatures' this is an array of features/ to enable
- update
    - Calls the update method on all the features

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
- For Apple, Windows and Android the code is a UserScript that has some string replacements for properties and loads in as the page scope.
    - Note: currently we don't implement the update calls as it's only required by cookie protections which we don't implement.
- All other browsers the code is stringified, base64 encoded and injected in as a self deleting `<script>` tag.

In the built output you will see these dramatic differences in the bundled code which is created into: /build

#### App specific integration replacements

- `$CONTENT_SCOPE$` - raw remote config object
- `$USER_UNPROTECTED_DOMAINS$` - an array of user allowlisted domains
- `$USER_PREFERENCES$` - an object containing:
    - platform: `{ name: '<ios | macos | extension | android>' }`
    - debug: boolean
    - globalPrivacyControlValue: boolean
    - sessionKey: `<CSRNG UUID 4 string>` (used for fingerprinting) - this should regenerate on browser close or every 24 hours.

### Features scope injection utilities

To handle the difference in scope injection we expose multiple utilities which behave differently per browser in src/utils.js and `ContentFeature` base class. for Firefox the code exposed handles [xrays correctly](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Sharing_objects_with_page_scripts) without needing the features to be authored differently.

- `ContentFeature.defineProperty()`
    - `defineProperty(object, propertyName, descriptor)` behaves the same as `Object.defineProperty(object, propertyName, descriptor)`
    - The difference is for Firefox we export the relevant functions so it can go across the xray
    - Use this method if `Object.getOwnPropertyDescriptors(object).propertyName` should to exist in the supporting browser.
- `ContentFeature.wrapProperty(object, propertyName, descriptor)`
    - A simple wrapper around `defineProperty()` that ignores non-existing properties and retains unspecified descriptor keys.
    - Example usage: `this.wrapProperty('Navigator.prototype.userAgent', { get: () => 'fakeUA' })`
- `ContentFeature.wrapMethod(object, propertyName, wrapperFn)`
    - Overrides a native method. wrapperFn() will be called in place of the original method. The original method will be passed as the first argument.
    - Example usage:
    ```JavaScript
    this.wrapMethod(Permissions.prototype, 'query', async function (originalFn, queryObject) {
        if (queryObject.name === 'blocked-permission') {
            return {
                name: queryObject.name,
                state: 'denied',
                status: 'denied'
            }
        }
        return await nativeImpl.call(this, queryObject)
    })
    ```
- `ContentFeature.shimInterface(interfaceName, ImplClass, options)`
    - API for shimming standard constructors. See the WebCompat feature and JSDoc for more details.
    - Example usage:
    ```javascript
    this.shimInterface('MediaSession', MyMediaSessionClass, {
        disallowConstructor: true,
        allowConstructorCall: false,
        wrapToString: true
    })
    ```
- `ContentFeature.shimProperty(instanceHost, instanceProp, implInstance, readOnly = false)`
    - API for shimming standard global objects. Usually you want to call `shimInterface()` first, and pass an object instance as `implInstance`. See the WebCompat feature and JSDoc for more details.
    - Example usage:
    ```javascript
    this.shimProperty(Navigator.prototype, 'mediaSession', myMediaSessionInstance, true)
    ```

- `DDGProxy`
    - Behaves a lot like `new window.Proxy` with a few differences:
        - has an `overload` method to actually apply the function to the native property.
        - Stores the native original property in _native such that it can be called elsewhere if needed without going through the proxy.
        - Triggers `addDebugFlag` if get/apply is called.
        - Sends debugging messaging if debug is enabled.
        - Allows for remotely disabling the override based on script URL via `shouldExemptMethod`.
        - Fixes `value.toString()` to appear like it was defined natively.
    - Example usage:
    ```JavaScript
    const historyMethodProxy = new DDGProxy(this, History.prototype, 'pushState', {
        apply (target, thisArg, args) {
            applyRules(activeRules)
            return DDGReflect.apply(target, thisArg, args)
        }
    })
    historyMethodProxy.overload()
    ```
- `DDGReflect`
    - Calls into wrappedJSObject.Reflect for Firefox but otherwise exactly the same as [window.Reflect](Sources/BrowserServicesKit/UserScript/ContentScopeUserScript.swift) 

### Testing Locally

Depending on what you are changing, you may need to run the build processes locally, or individual tests.
The following all run within GitHub Actions when you create a pull request, but you can run them locally as well.

- eslint
- Typescript 
- Unit tests (jasmine)
- Feature Integration Tests (puppeteer)
- Feature Integration Tests (playwright)
- Special Pages Integration Tests (playwright)
- Feature Build process + Special Pages Build process

If you want to get a good feeling for whether a PR or CI run will pass/fail, you can run the `test` command 
which chains most of the following together

```shell
# run this if you want some confidence that your PR will pass
npm test
```

#### eslint

```shell
# run eslint to check for errors
npm run lint

# run eslint and attempt to fix errors
npm run lint-fix
```

#### Typescript

```shell
# run Typescript to check for errors
npm run tsc

# run Typescript in watch mode
npm run tsc-watch
```

#### Unit Tests (jasmine)

Everything for unit-testing is located in the `unit-test` folder. Jasmine configuration is in `unit-test/jasmine.json`.

```shell
npm run test-unit
```

#### Feature Integration Tests (puppeteer)

Everything within `integration-test` (minus the playwright folder) is controlled by Jasmine + Puppeteer.
The configuration is within `integration-test/config.js`

Note: when you run this command, it will also be executed all workspaces too. For example, within `packages/special-pages` 

```shell
npm run test-int
```

#### Feature Integration Tests (playwright)
Everything within `integration-test/playwright` is integration tests controlled by Playwright. These should be defaulted
to for any new tests that include UI elements (such as click to load)

```shell
npm run playwright
```

#### Special Pages Integration Tests (playwright)
There are tests within `packages/special-pages/tests` that are dedicated to testing the special pages.
These tests will be ran automatically when you execute `npm run test-int` from the root. But during development
you might find it useful to run them individually.

```shell
cd packages/special-pages
npm run test-int
```

#### Feature Build process

To produce all artefacts that are used by platforms, just run the `npm run build` command.
This will create platform specific code within the `build` folder (that is not checked in)

```shell
npm run build
```

Note: This will also execute the build process witin `packages/special-pages`
