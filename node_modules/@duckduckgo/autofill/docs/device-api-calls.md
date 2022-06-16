# Device API Calls

## Adding new auto-generated API Calls

**Note 1:** Everything here is **100%** optional. Whilst we encourage you to design any new messaging in a 'schema-first' workflow, you are not forced to. 

**Note 2:** The code generation described below has been designed in a way that prevents lock-in to any library or technique.
If at some point in the future we encounter difficulties or problems, then we simply remove the build step and the classes/validation will continue to work. We can continue with all the strongly-typed messaging patterns and validations, 
it just means that we write them ourselves, rather than being schema-generated.

**Note 3** See `scripts/tests/fixtures` for the example that is unit-tested as part of this feature. 

<details>
<summary>📝A full <b>'tl;dr'</b> example</summary>

**Create an entry in `src/deviceApiCalls/deviceApiCalls.json`**
```json
{
  "example": {
    "paramsValidator": "schemas/example.params.json"
  }
}
```

**Create a schema in `src/deviceApiCalls/schemas/example.params.json` (relative reference above)**
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ExampleParams",
  "type": "object",
  "properties": {
    "secret": {
      "type": "string"
    }
  },
  "required": [
    "secret"
  ]
}
```

**✅ the following is generated: DeviceApiCall in `src/deviceApiCalls/__generated__/deviceApiCalls.js`**

```javascript
import { exampleParamsSchema } from "./validators.zod.js"
import { DeviceApiCall } from "../../../packages/device-api";

/**
 * @extends {DeviceApiCall<exampleParamsSchema, any>} 
 */
export class ExampleCall extends DeviceApiCall {
  method = "example"
  paramsValidator = exampleParamsSchema
}
```

</details>

### Step 1: Add an entry to `src/deviceApiCalls/deviceApiCalls.json`

This is a single place where all auto-generated API calls are listed. 

- Object key - a unique identifier for this API Call
- `id` - an optional ID to be used when listening for responses
- `description` - an optional short description of the purpose of this API call
- `paramsValidator` - an optional filepath to the schema file used to validate outgoing params (see step 2)
- `resultValidator` - an optional filepath to the schema file used to validate the incoming result (see step 3)

**Minimal Example**

Technically, this is all that's needed - you won't get any validation, but it's a good way to
start developing a new API call because you can then import the class it creates.

```json
{
  "getAutofillData": {}
}

```

<details>
<summary>📝 Full example with schemas</summary>

**With all fields**

Notice how `paramsValidator` and `resultValidator` are using relative file paths
to JSON files that live within `schema`

```json
{
  "getAutofillData": {
    "id": "getAutofillDataResponse",
    "description": "Request autofill information from the device",
    "paramsValidator": "./schemas/getAutofillData.params.json",
    "resultValidator": "./schemas/getAutofillData.result.json"
  }
}
```
</details>

### Step 2: (optional) Add a params validator as JSON Schema
 
- Add a `<name>.params.json` file inside `src/deviceApiCalls/schemas`
- reference this file in your `deviceApiCalls.json` entry via `paramsValidator`
    
<details>
<summary>📝<code>schemas/getAlias.<b>params</b>.json</code> example</summary>

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "GetAliasParams",
  "type": "object",
  "properties": {
    "requiresUserPermission": { 
      "type": "boolean"
    },
    "shouldConsumeAliasIfProvided": {
      "type": "boolean"
    }
  },
  "required": [
    "requiresUserPermission",
    "shouldConsumeAliasIfProvided"
  ]
}
```
</details>

### Step 3: (optional) Add a results validator as JSON Schema

- Add a `<name>.result.json` file inside `src/deviceApiCalls/schemas`
- reference this file in your `deviceApiCalls.json` entry via `resultsValidator`
- For these API results (return values) ensure you conform to the [Result Properties](#Result-properties)

<details>
<summary>📝<code>schemas/getAlias.<b>result</b>.json</code> example</summary>

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "GetAliasResult",
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "success": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "alias": {
          "type": "string"
        }
      },
      "required": [
        "alias"
      ]
    }
  },
  "required": [
    "success"
  ]
}
```

That schema defines the following response:

```json
{
  "success": {
    "alias": "dax"
  }
}
```

</details>

### Step 4: Run the build

Either with `npm run build`, or it will also run on every file change when the watcher is running.

#### Step 4.1: Verify the output.

When you are authoring schemas, it's useful to keep an eye on the output files that
are generated, as this can help you prevent typos and other human errors. 

- `src/deviceApiCalls/__generated__/deviceApiCalls.js`
  - This contains the class definitions that you'll use directly when making API calls
- `src/deviceApiCalls/__generated__/validators-ts.d.ts`
  - This contains the auto-generated Typescript definitions from the schema files
- `src/deviceApiCalls/__generated__/validators.zod.js`
  - This contains the auto-generated validators that are referenced in `deviceApiCalls.js`

### Step 5: reference the generated classes 

Now just import things from the `src/deviceApiCalls/__generated__` folder as you would any other module in the codebase

```javascript
import { GetAutofillDataCall } from "../deviceApiCalls/__generated__/deviceApiCalls"

class AppleDeviceInterface {
    getAutofillData() {
        return this.deviceApi.request(new GetAutofillDataCall())
    }
}
```

--- 

## Adding new manually-created API calls

If you need to create a `DeviceApiCall`, but you're not ready for it to be auto-generated (because 
you're not ready for writing a schema etc), then you can just add your DeviceApiCall to `src/deviceApiCalls/additionalDeviceApiCalls.js`. Note: this is *not* one of the auto-generated files, you are free to edit this as you see fit.

--- 

## Result Properties

An important aspect of this schema-driven API workflow is the standardisation of return values. We're attempting to roughly follow [https://www.jsonrpc.org/specification#response_object](https://www.jsonrpc.org/specification#response_object), except for using `success` instead of `result`. 

- `success` - if the call is successful, then any valid JSON value can be here  
- `id` - if present, this should match the `id` sent in the request, *or* a value that's been documented elsewhere
- `error` - if the request was not successful, then `error` can contain a `message` (see examples below) 

**✅ good**

```json
{
  "success": {
    "alias": "dax@example.com" 
  }
}
```

**✅ also good**

```json
{
  "success": "dax@example.com"
}
```

**❌ bad**
```json
{ "alias": "dax@example.com" }
```


**❌ also bad**
```json
"dax@example.com"
```

**Example with an error**

```json
{
  "error": {
    "message": "Something went wrong!"
  }
}
```
