# device-api

This package helps you validate messages to/from the enclosing devices with optional validation of inputs and outputs. 

This is especially useful during integration because it shortens feedback loops and enables teams to integrate more autonomously thanks to detailed error messages. We currently use the [zod](https://github.com/colinhacks/zod) library to power the validations.

Validation happens only in the debug version which is compiled separately to `autofill-debug.js`. During integration, please instruct the native team to use that version if they want to receive rich error messages. The runtime version doesn't include validation to avoid unnecessary checks at runtime.

Going forward we will utilise [JSONSchema](https://json-schema.org/specification.html) files to 
describe the shapes of input parameters and expected responses. We'll generate Zod definitions (like those seen below) directly from the JSONSchema files - this will ensure a single 'source of truth'. 

We also use this package in tests to provide clear interface testing using json-schemas.

Here's how you can use this package to implement your integration:

## Device API calls

### 1 Create a new Device Api Call that **requires** a response

In any file, create a class with the following structure, note that the `validator` here would
be located in a sibling file with the `.zod.js` extension.

```js
import { DeviceApiCall } from "../packages/device-api";

// These need to be in a separate file with the *.zod.js extension
const paramsValidator = z.object({name: z.string()});
const resultValidator = z.object({name: z.string()});

/** @extends {DeviceApiCall<paramsValidator, resultValidator>} */
class Login extends DeviceApiCall {
    method = "loginUser"
    id = "loginUserResponse"
    paramsValidator = paramsValidator
    resultValidator = resultValidator
}

const result = await deviceApi.request(new Login({name: "dax"}))
```

Explanation:
* `/** @extends {DeviceApiCall<paramsValidator, resultValidator>} */` is how we can forward generic parameters when using JSDoc with Typescript 
* `class Login extends DeviceApiCall` creates a class `Login` that inherits all the functionality of a DeviceApiCall call (such as validation etc)
* `method = "loginUser"` depending on the platform in question this may be used in various ways
*   - For example, on macOS/iOS the `method` is used to look up a specific named handler that the native side injects
*   - But on Windows, the `method` is sent via a generic handler such as `postMessage({type: 'Autofill::' + deviceApiCall.method})`
* `paramsValidator` this is the Zod Validator used to validate the `params` (outgoing) data
* `resultValidator` this is the Zod Validator used to validate the `result` (incoming) data
 
Please see **Send a request** section for details on how to actually send this  

### 2 Create a new DeviceApiCall that does NOT require a response

This is the same process as above, except you do not need to specify an `id` or `resultValidator`.

```javascript
const paramsValidator = z.object({name: z.string()});

/** @extends {DeviceApiCall<paramsValidator>} */
class Login extends DeviceApiCall {
    method = "loginUser"
    paramsValidator = paramsValidator
}

deviceApi.notify(new Login({name: "dax"}))
```

### 3 Create an DeviceApiCall from a name + data only 

In situations where you just want to quickly create an DeviceApiCall, but you don't care/need to have a 
class definition (eg: because you're accepting that you won't be able to use things like `instanceof` checks later), then you can do the following:

**note**: This pattern is here to aid migrations, it shouldn't be seen as a viable alternative to the class-based approach described above. 

```javascript
// create an `DeviceApiCall` inline for simple, untyped messaging
const result = await deviceApi.request(createDeviceApiCall("isSignedIn"));

// or a notification, with optional data
deviceApi.notify(createDeviceApiCall("notifyWebApp", {signedIn: true}));

// you can also add validation here
const paramsValidator = z.object({name: z.string()});
const apiCall = createDeviceApiCall("signIn", {name: "dax"}, paramsValidator)
const result = await deviceApi.request(apiCall);

// with params + result validation
const paramsValidator = z.object({name: z.string()});
const resultValidator = z.object({success: z.string()});
const apiCall = createDeviceApiCall("signIn", {name: "dax"}, paramsValidator, resultValidator);
const result = await deviceApi.request(apiCall);
```

### 4 Send a request/notification

To send a request, you need to have 3 things:

- `DeviceApiTransport` - to handle differences in messaging for each platform
- `DeviceApi` - to handle the lifecycle of messaging + validation
- A `DeviceApiCall`

All device interfaces that inherit from `InterfaceProtype` have direct access to `this.deviceApi`, which is 
an instance of DeviceApi. So if you're working within an existing class, like `AppleDeviceInterface` (for example),
then you can send any `DeviceApiCall`'s you like just using `this.deviceApi.notify(...)` or `this.deviceApi.request(...)`.

```javascript
// a concrete example of how easy it is to use the new DeviceApi system 
class AppleDeviceInterface extends InterfaceProtype {
    // an example of sending a request and waiting for the response
    async getAlias() {
        const {alias} = await this.deviceApi.request(createDeviceApiCall("getAlias"));
    }

    // an example of sending a fire+forget message
    storeFormData() {
        this.deviceApi.notify(createDeviceApiCall("storeFormData", {credentials: {username: ""}}));
    }
}
```

### 5 Use Zod validations without creating an `DeviceApiCall`

If you want to leverage the type safety of Zod, outside of `DeviceApiCall`s, you can use the `validate` method directly, just be sure to keep the validator code in a `*.zod.js` file so that it can be stripped for production.

**Note:** this will throw in development, it's specifically designed to be a type-safety tool that aids development.

```javascript
import { myValidator } from "./validators.zod.js";

const valid = validate({name: 2}, myValidator);
```

### 6 Creating validators in separate files

In the examples above, it's mentioned that you should place Zod definitions in a separate file with the `*.zod.js` extension. This is done to allow us to strip validation for the production builds.

Technically these `*.zod.js` files can live anywhere, but a recommended approach is to use the following structure: 

```
 deviceApiCalls/
    deviceApiCalls.js
    validators.zod.js
```

You can use the top level folder `deviceApiCalls` that already lives in the `src` folder, or you can nest it 
within any other folder if it makes sense. 

**`validators.zod.js` example**

```javascript
import z from "zod";

export const getAliasResultValidator = z.object({
    success: z.string()
})
```

**`deviceApiCalls.js` example**
```javascript
import { getAliasResultValidator } from "./validators.zod.js"

/** @extends {DeviceApiCall<getAliasResultValidator>} */
export class GetAlias extends DeviceApiCall {
    method = "getAlias"
    resultValidator = getAliasResultValidator
}
```

The most important part of this setup, is that `import z from "zod"` only occurs from within `.zod.js` files.

## Working with DeviceApiTransport's

### 1) Implement a new transport

A `DeviceApiTransport` handles the very last part of browser<->native messaging. The following example is
a cut-down version to show the concept. 

Implementors just override `send(deviceApiCall)` and can do whatever is needed on their platform to respond.

```javascript
// an example transport 
class AppleTransport extends DeviceApiTransport {
    async send (deviceApiCall) {
        return wkSendAndWait(deviceApiCall.method, deviceApiCall.params, {
            secret: this.config.secret,
            hasModernWebkitAPI: this.config.hasModernWebkitAPI
        })
    }
}
```

If this DeviceApiTransport is chosen for the current platform, then every call to `this.deviceApi.request` and `this.deviceApi.notify`
will come through here.

### 2) Debug an existing transport 

Search for anything that `extends DeviceApiTransport` -> there's only a single method implemented, so it should be
straightforward to incorporate logging to see what's going on.

### 3) Make a change to an existing transport 

Once you know of a required change, you may need to update supporting mocks that live within `integration-test/helpers`

---

## Internals

This library contains:

- `DeviceApiCall` - a class that you can extend to describe your device api calls, with validation via `Zod`
- `DeviceApiTransport` - each platform can implement to customise how requests & notifications work
    - Example implementation: [Apple](../../src/appleDeviceUtils/appleDeviceUtils.js#L97-L115)  
- `DeviceApi` - the API that your device interfaces will use to make requests & notifications

Note: This is designed to roughly follow naming patterns seen here: https://www.jsonrpc.org/specification 

## `DeviceApiCall`

**Class format**

You should use this format for all *new* device api calls

Benefits:
  1) By using a `class` for each new message, we enable powerful type checking
     1) For example, if a given transport wants to intercept or change an outgoing api call, it can use `instanceof` to get full type safety on parameters and result types. 
  2) The `@extends` comment allows the type-safety to flow through the handling of the requests/notifications

```javascript
// This would be in another file *.zod.js file
const validator = z.object({name: z.string()});

/** @extends {DeviceApiCall<validator>} */
class Login extends DeviceApiCall {
    method = "loginUser"
    paramsValidator = validator
}

// ❌ TS2322: Type 'number' is not assignable to type 'string'.
const data = new Login({name: 2}).validateParams(); 
```

By adding `@extends {DeviceApiCall<validator>}`, Typescript is abel to verify the parameters that are provided in the constructor.

--- 

## `DeviceApi`

`DeviceApi` encapsulates the functionality of sending, receiving and validating `DeviceApiCall`s

**request**

```javascript
const handler = new DeviceApi({
    // an example of a `DeviceApiTransport` implementation
    send: async (apiCall) => {
        console.log(apiCall.method, apiCall.params);
        return {success: {foo: "bar"}}
    }
})

// Create an DeviceApiCall from a method name only
const apiCall = createDeviceApiCall("storeFormData");

// send and wait for response
const result = handler.request(apiCall);

// this will print `{ foo: "bar" }`
console.log(result);
```

**notify**

The key difference with a `notification` is that a response will not be waited for. This is a 'fire and forget'
operation.

```javascript
const handler = new DeviceApi({
    send: async (apiCall) => {
        console.log(apiCall.method, apiCall.params);
    }
})

// Create an `DeviceApiCall` from a method name only
const apiCall = createDeviceApiCall("storeFormData")

// Fire and forget notification 
handler.notify(apiCall)
```
