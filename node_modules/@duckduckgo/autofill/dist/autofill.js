(function(){function r(e,n,t){function o(i,f){if(!n[i]){if(!e[i]){var c="function"==typeof require&&require;if(!f&&c)return c(i,!0);if(u)return u(i,!0);var a=new Error("Cannot find module '"+i+"'");throw a.code="MODULE_NOT_FOUND",a}var p=n[i]={exports:{}};e[i][0].call(p.exports,function(r){var n=e[i][1][r];return o(n||r)},p,p.exports,r,e,n,t)}return n[i].exports}for(var u="function"==typeof require&&require,i=0;i<t.length;i++)o(t[i]);return o}return r})()({1:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.processConfig = processConfig;

function getTopLevelURL() {
  try {
    // FROM: https://stackoverflow.com/a/7739035/73479
    // FIX: Better capturing of top level URL so that trackers in embedded documents are not considered first party
    if (window.location !== window.parent.location) {
      return new URL(window.location.href !== 'about:blank' ? document.referrer : window.parent.location.href);
    } else {
      return new URL(window.location.href);
    }
  } catch (error) {
    return new URL(location.href);
  }
}

function isUnprotectedDomain(topLevelUrl, featureList) {
  let unprotectedDomain = false;
  const domainParts = topLevelUrl && topLevelUrl.host ? topLevelUrl.host.split('.') : []; // walk up the domain to see if it's unprotected

  while (domainParts.length > 1 && !unprotectedDomain) {
    const partialDomain = domainParts.join('.');
    unprotectedDomain = featureList.filter(domain => domain.domain === partialDomain).length > 0;
    domainParts.shift();
  }

  return unprotectedDomain;
}

function processConfig(data, userList, preferences) {
  const topLevelUrl = getTopLevelURL();
  const allowlisted = userList.filter(domain => domain === topLevelUrl.host).length > 0;
  const enabledFeatures = Object.keys(data.features).filter(featureName => {
    const feature = data.features[featureName];
    return feature.state === 'enabled' && !isUnprotectedDomain(topLevelUrl, feature.exceptions);
  });
  const isBroken = isUnprotectedDomain(topLevelUrl, data.unprotectedTemporary);
  preferences.site = {
    domain: topLevelUrl.hostname,
    isBroken,
    allowlisted,
    enabledFeatures
  }; // TODO

  preferences.cookie = {};
  return preferences;
}

},{}],2:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
Object.defineProperty(exports, "DeviceApi", {
  enumerable: true,
  get: function () {
    return _deviceApi.DeviceApi;
  }
});
Object.defineProperty(exports, "DeviceApiCall", {
  enumerable: true,
  get: function () {
    return _deviceApiCall.DeviceApiCall;
  }
});
Object.defineProperty(exports, "DeviceApiTransport", {
  enumerable: true,
  get: function () {
    return _deviceApi.DeviceApiTransport;
  }
});
Object.defineProperty(exports, "createNotification", {
  enumerable: true,
  get: function () {
    return _deviceApiCall.createNotification;
  }
});
Object.defineProperty(exports, "createRequest", {
  enumerable: true,
  get: function () {
    return _deviceApiCall.createRequest;
  }
});
Object.defineProperty(exports, "validate", {
  enumerable: true,
  get: function () {
    return _deviceApiCall.validate;
  }
});

var _deviceApiCall = require("./lib/device-api-call");

var _deviceApi = require("./lib/device-api");

},{"./lib/device-api":4,"./lib/device-api-call":3}],3:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.SchemaValidationError = exports.DeviceApiCallError = exports.DeviceApiCall = void 0;
exports.createDeviceApiCall = createDeviceApiCall;
exports.createNotification = void 0;
exports.createRequest = createRequest;
exports.validate = validate;

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * This roughly follows https://www.jsonrpc.org/specification
 * @template {import("zod").ZodType} Params=import("zod").ZodType
 * @template {import("zod").ZodType} Result=import("zod").ZodType
 */
class DeviceApiCall {
  /** @type {string} */

  /**
   * An optional 'id' - used to indicate if a request requires a response.
   * @type {string|null}
   */

  /** @type {Params | null | undefined} */

  /** @type {Result | null | undefined} */

  /** @type {import("zod").infer<Params>} */

  /**
   * This is a carve-out for legacy messages that are not typed yet.
   * If you set this to 'true', then the response will not be checked to conform
   * to any shape
   * @deprecated this is here to aid migration, should be removed ASAP
   * @type {boolean}
   */

  /**
   * New messages should be in a particular format, eg: { success: T },
   * but you can set this to false if you want to access the result as-is,
   * without any unwrapping logic
   * @deprecated this is here to aid migration, should be removed ASAP
   * @type {boolean}
   */

  /**
   * @param {import("zod").infer<Params>} data
   */
  constructor(data) {
    _defineProperty(this, "method", 'unknown');

    _defineProperty(this, "id", null);

    _defineProperty(this, "paramsValidator", null);

    _defineProperty(this, "resultValidator", null);

    _defineProperty(this, "params", void 0);

    _defineProperty(this, "throwOnResultKeysMissing", true);

    _defineProperty(this, "unwrapResult", true);

    this.params = data;
  }
  /**
   * @returns {import("zod").infer<Params>|undefined}
   */


  validateParams() {
    if (this.params === undefined) {
      return undefined;
    }

    this._validate(this.params, this.paramsValidator);

    return this.params;
  }
  /**
   * @param {any|null} incoming
   * @returns {import("zod").infer<Result>}
   */


  validateResult(incoming) {
    this._validate(incoming, this.resultValidator);

    if (!incoming) {
      return incoming;
    }

    if (!this.unwrapResult) {
      return incoming;
    }

    if ('data' in incoming) {
      console.warn('response had `data` property. Please migrate to `success`');
      return incoming.data;
    }

    if ('success' in incoming) {
      return incoming.success;
    }

    if ('error' in incoming) {
      if (typeof incoming.error.message === 'string') {
        throw new DeviceApiCallError("".concat(this.method, ": ").concat(incoming.error.message));
      }
    }

    if (this.throwOnResultKeysMissing) {
      throw new Error('unreachable. Response did not contain `success` or `data`');
    }

    return incoming;
  }
  /**
   * @param {any} data
   * @param {import("zod").ZodType|undefined|null} [validator]
   * @private
   */


  _validate(data, validator) {
    if (!validator) return data;

    if (validator) {
      const result = validator === null || validator === void 0 ? void 0 : validator.safeParse(data);

      if (!result) {
        throw new Error('unreachable');
      }

      if (!result.success) {
        if ('error' in result) {
          this.throwError(result.error.issues);
        } else {
          console.error('unknown error from validate');
        }
      }
    }
  }
  /**
   * @param {import('zod').ZodIssue[]} errors
   */


  throwError(errors) {
    const error = SchemaValidationError.fromZodErrors(errors, this.constructor.name);
    throw error;
  }
  /**
   * Use this helper for creating stand-in response messages that are typed correctly.
   *
   * @examples
   *
   * ```js
   * const msg = new Message();
   * const response = msg.response({}) // <-- This argument will be typed correctly
   * ```
   *
   * @param {import("zod").infer<Result>} response
   * @returns {import("zod").infer<Result>}
   */


  result(response) {
    return response;
  }
  /**
   * @returns {import("zod").infer<Result>}
   */


  preResultValidation(response) {
    return response;
  }

}

exports.DeviceApiCall = DeviceApiCall;

class DeviceApiCallError extends Error {}
/**
 * Check for this error if you'd like to
 */


exports.DeviceApiCallError = DeviceApiCallError;

class SchemaValidationError extends Error {
  constructor() {
    super(...arguments);

    _defineProperty(this, "validationErrors", []);
  }

  /**
   * @param {import("zod").ZodIssue[]} errors
   * @param {string} name
   * @returns {SchemaValidationError}
   */
  static fromZodErrors(errors, name) {
    const heading = "".concat(errors.length, " SchemaValidationError(s) errors for ") + name;

    function log(issue) {
      switch (issue.code) {
        case 'invalid_literal':
        case 'invalid_type':
          {
            console.log("".concat(name, ". Path: '").concat(issue.path.join('.'), "', Error: '").concat(issue.message, "'"));
            break;
          }

        case 'invalid_union':
          {
            for (let unionError of issue.unionErrors) {
              for (let issue1 of unionError.issues) {
                log(issue1);
              }
            }

            break;
          }

        default:
          {
            console.log(name, 'other issue:', issue);
          }
      }
    }

    for (let error of errors) {
      log(error);
    }

    const message = [heading, 'please see the details above'].join('\n    ');
    const error = new SchemaValidationError(message);
    error.validationErrors = errors;
    return error;
  }

}
/**
 * Creates an instance of `DeviceApiCall` from only a name and 'params'
 * and optional validators. Use this to help migrate existing messages.
 *
 * @template {import("zod").ZodType} Params
 * @template {import("zod").ZodType} Result
 * @param {string} method
 * @param {import("zod").infer<Params>} [params]
 * @param {Params|null} [paramsValidator]
 * @param {Result|null} [resultValidator]
 * @returns {DeviceApiCall<Params, Result>}
 */


exports.SchemaValidationError = SchemaValidationError;

function createDeviceApiCall(method, params) {
  let paramsValidator = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : null;
  let resultValidator = arguments.length > 3 && arguments[3] !== undefined ? arguments[3] : null;

  /** @type {DeviceApiCall<Params, Result>} */
  const deviceApiCall = new DeviceApiCall(params);
  deviceApiCall.paramsValidator = paramsValidator;
  deviceApiCall.resultValidator = resultValidator;
  deviceApiCall.method = method;
  deviceApiCall.throwOnResultKeysMissing = false;
  deviceApiCall.unwrapResult = false;
  return deviceApiCall;
}
/**
 * Creates an instance of `DeviceApiCall` from only a name and 'params'
 * and optional validators. Use this to help migrate existing messages.
 *
 * Note: This creates a regular DeviceApiCall, but adds the 'id' as a string
 * so that transports know that it expects a response.
 *
 * @template {import("zod").ZodType} Params
 * @template {import("zod").ZodType} Result
 * @param {string} method
 * @param {import("zod").infer<Params>} [params]
 * @param {string} [id]
 * @param {Params|null} [paramsValidator]
 * @param {Result|null} [resultValidator]
 * @returns {DeviceApiCall<Params, Result>}
 */


function createRequest(method, params) {
  let id = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : 'n/a';
  let paramsValidator = arguments.length > 3 && arguments[3] !== undefined ? arguments[3] : null;
  let resultValidator = arguments.length > 4 && arguments[4] !== undefined ? arguments[4] : null;
  const call = createDeviceApiCall(method, params, paramsValidator, resultValidator);
  call.id = id;
  return call;
}

const createNotification = createDeviceApiCall;
/**
 * Validate any arbitrary data with any Zod validator
 *
 * @template {import("zod").ZodType} Validator
 * @param {any} data
 * @param {Validator | null} [validator]
 * @returns {import("zod").infer<Validator>}
 */

exports.createNotification = createNotification;

function validate(data) {
  let validator = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : null;

  if (validator) {
    return validator.parse(data);
  }

  return data;
}

},{}],4:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.DeviceApiTransport = exports.DeviceApi = void 0;

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * Platforms should only need to implement this `send` method
 */
class DeviceApiTransport {
  /**
   * @param {import("./device-api-call.js").DeviceApiCall} _deviceApiCall
   * @returns {Promise<any>}
   */
  async send(_deviceApiCall) {
    return undefined;
  }

}
/**
 * This is the base Sender class that platforms can will implement.
 *
 * Note: The 'handle' method must be implemented, unless you also implement 'send'
 */


exports.DeviceApiTransport = DeviceApiTransport;

class DeviceApi {
  /** @type {DeviceApiTransport} */

  /** @param {DeviceApiTransport} transport */
  constructor(transport) {
    _defineProperty(this, "transport", void 0);

    this.transport = transport;
  }
  /**
   * @template {import("./device-api-call").DeviceApiCall} D
   * @param {D} deviceApiCall
   * @returns {Promise<ReturnType<D['validateResult']>['success']>}
   */


  async request(deviceApiCall) {
    deviceApiCall.validateParams();
    let result = await this.transport.send(deviceApiCall);
    let processed = deviceApiCall.preResultValidation(result);
    return deviceApiCall.validateResult(processed);
  }
  /**
   * @template {import("./device-api-call").DeviceApiCall} P
   * @param {P} deviceApiCall
   * @returns {Promise<void>}
   */


  async notify(deviceApiCall) {
    deviceApiCall.validateParams();
    await this.transport.send(deviceApiCall);
  }

}

exports.DeviceApi = DeviceApi;

},{}],5:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.HostnameInputError = void 0;
Object.defineProperty(exports, "ParserError", {
  enumerable: true,
  get: function () {
    return _rulesParser.ParserError;
  }
});
exports._selectPasswordRules = _selectPasswordRules;
Object.defineProperty(exports, "constants", {
  enumerable: true,
  get: function () {
    return _constants.constants;
  }
});
exports.generate = generate;

var _applePassword = require("./lib/apple.password.js");

var _rulesParser = require("./lib/rules-parser.js");

var _constants = require("./lib/constants.js");

/**
 * @typedef {{
 *   domain?: string | null | undefined;
 *   input?: string | null | undefined;
 *   rules?: RulesFormat | null | undefined;
 *   onError?: ((error: unknown) => void) | null | undefined;
 * }} GenerateOptions
 */

/**
 * Generate a random password based on the following attempts
 *
 * 1) using `options.input` if provided -> falling back to default ruleset
 * 2) using `options.domain` if provided -> falling back to default ruleset
 * 3) using default ruleset
 *
 * Note: This API is designed to never throw - if you want to observe errors
 * during development, you can provide an `onError` callback
 *
 * @param {GenerateOptions} [options]
 */
function generate() {
  let options = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {};

  try {
    if (typeof (options === null || options === void 0 ? void 0 : options.input) === 'string') {
      return _applePassword.Password.generateOrThrow(options.input);
    }

    if (typeof (options === null || options === void 0 ? void 0 : options.domain) === 'string') {
      if (options !== null && options !== void 0 && options.rules) {
        const rules = _selectPasswordRules(options.domain, options.rules);

        if (rules) {
          return _applePassword.Password.generateOrThrow(rules);
        }
      }
    }
  } catch (e) {
    // if an 'onError' callback was provided, forward all errors
    if (options !== null && options !== void 0 && options.onError && typeof (options === null || options === void 0 ? void 0 : options.onError) === 'function') {
      options.onError(e);
    } else {
      // otherwise, only console.error unknown errors (which could be implementation bugs)
      const isKnownError = e instanceof _rulesParser.ParserError || e instanceof HostnameInputError;

      if (!isKnownError) {
        console.error(e);
      }
    }
  } // At this point, we have to trust the generation will not throw
  // as it is NOT using any user/page-provided data


  return _applePassword.Password.generateDefault();
} // An extension type to differentiate between known errors


class HostnameInputError extends Error {}
/**
 * @typedef {Record<string, {"password-rules": string}>} RulesFormat
 */

/**
 * @private
 * @param {string} inputHostname
 * @param {RulesFormat} rules
 * @returns {string | undefined}
 * @throws {HostnameInputError}
 */


exports.HostnameInputError = HostnameInputError;

function _selectPasswordRules(inputHostname, rules) {
  const hostname = _safeHostname(inputHostname); // direct match


  if (rules[hostname]) {
    return rules[hostname]['password-rules'];
  } // otherwise, start chopping off subdomains and re-joining to compare


  const pieces = hostname.split('.');

  while (pieces.length > 1) {
    pieces.shift();
    const joined = pieces.join('.');

    if (rules[joined]) {
      return rules[joined]['password-rules'];
    }
  }

  return undefined;
}
/**
 * @private
 * @param {string} inputHostname;
 * @throws {HostnameInputError}
 * @returns {string}
 */


function _safeHostname(inputHostname) {
  if (inputHostname.startsWith('http:') || inputHostname.startsWith('https:')) {
    throw new HostnameInputError('invalid input, you can only provide a hostname but you gave a scheme');
  }

  if (inputHostname.includes(':')) {
    throw new HostnameInputError('invalid input, you can only provide a hostname but you gave a :port');
  }

  try {
    const asUrl = new URL('https://' + inputHostname);
    return asUrl.hostname;
  } catch (e) {
    throw new HostnameInputError("could not instantiate a URL from that hostname ".concat(inputHostname));
  }
}

},{"./lib/apple.password.js":6,"./lib/constants.js":7,"./lib/rules-parser.js":8}],6:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.Password = void 0;

var parser = _interopRequireWildcard(require("./rules-parser.js"));

var _constants = require("./constants.js");

function _getRequireWildcardCache(nodeInterop) { if (typeof WeakMap !== "function") return null; var cacheBabelInterop = new WeakMap(); var cacheNodeInterop = new WeakMap(); return (_getRequireWildcardCache = function (nodeInterop) { return nodeInterop ? cacheNodeInterop : cacheBabelInterop; })(nodeInterop); }

function _interopRequireWildcard(obj, nodeInterop) { if (!nodeInterop && obj && obj.__esModule) { return obj; } if (obj === null || typeof obj !== "object" && typeof obj !== "function") { return { default: obj }; } var cache = _getRequireWildcardCache(nodeInterop); if (cache && cache.has(obj)) { return cache.get(obj); } var newObj = {}; var hasPropertyDescriptor = Object.defineProperty && Object.getOwnPropertyDescriptor; for (var key in obj) { if (key !== "default" && Object.prototype.hasOwnProperty.call(obj, key)) { var desc = hasPropertyDescriptor ? Object.getOwnPropertyDescriptor(obj, key) : null; if (desc && (desc.get || desc.set)) { Object.defineProperty(newObj, key, desc); } else { newObj[key] = obj[key]; } } } newObj.default = obj; if (cache) { cache.set(obj, newObj); } return newObj; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * @typedef {{
 *     PasswordAllowedCharacters?: string,
 *     PasswordRequiredCharacters?: string[],
 *     PasswordRepeatedCharacterLimit?: number,
 *     PasswordConsecutiveCharacterLimit?: number,
 *     PasswordMinLength?: number,
 *     PasswordMaxLength?: number,
 * }} Requirements
 */

/**
 * @typedef {{
 *     NumberOfRequiredRandomCharacters: number,
 *     PasswordAllowedCharacters: string,
 *     RequiredCharacterSets: string[]
 * }} PasswordParameters
 */
const defaults = Object.freeze({
  SCAN_SET_ORDER: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-~!@#$%^&*_+=`|(){}[:;\\\"'<>,.?/ ]",
  defaultUnambiguousCharacters: 'abcdefghijkmnopqrstuvwxyzABCDEFGHIJKLMNPQRSTUVWXYZ0123456789',
  defaultPasswordLength: _constants.constants.DEFAULT_MIN_LENGTH,
  defaultPasswordRules: _constants.constants.DEFAULT_PASSWORD_RULES,
  defaultRequiredCharacterSets: ['abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', '0123456789'],

  /**
   * @type {typeof window.crypto.getRandomValues | null}
   */
  getRandomValues: null
});
/**
 * This is added here to ensure:
 *
 * 1) `getRandomValues` is called with the correct prototype chain
 * 2) `window` is not accessed when in a node environment
 * 3) `bind` is not called in a hot code path
 *
 * @type {{ getRandomValues: typeof window.crypto.getRandomValues }}
 */

const safeGlobals = {};

if (typeof window !== 'undefined') {
  safeGlobals.getRandomValues = window.crypto.getRandomValues.bind(window.crypto);
}

class Password {
  /**
   * @type {typeof defaults}
   */

  /**
   * @param {Partial<typeof defaults>} [options]
   */
  constructor() {
    let options = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {};

    _defineProperty(this, "options", void 0);

    this.options = { ...defaults,
      ...options
    };
    return this;
  }
  /**
   * This is here to provide external access to un-modified defaults
   * in case they are needed for tests/verifications
   * @type {typeof defaults}
   */


  /**
   * Generates a password from the given input.
   *
   * Note: This method will throw an error if parsing fails - use with caution
   *
   * @example
   *
   * ```javascript
   * const password = Password.generateOrThrow("minlength: 20")
   * ```
   * @public
   * @param {string} inputString
   * @param {Partial<typeof defaults>} [options]
   * @throws {ParserError|Error}
   * @returns {string}
   */
  static generateOrThrow(inputString) {
    let options = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
    return new Password(options).parse(inputString).generate();
  }
  /**
   * Generates a password using the default ruleset.
   *
   * @example
   *
   * ```javascript
   * const password = Password.generateDefault()
   * ```
   *
   * @public
   * @param {Partial<typeof defaults>} [options]
   * @returns {string}
   */


  static generateDefault() {
    let options = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {};
    return new Password(options).parse(Password.defaults.defaultPasswordRules).generate();
  }
  /**
   * Convert a ruleset into it's internally-used component pieces.
   *
   * @param {string} inputString
   * @throws {parser.ParserError|Error}
   * @returns {{
   *    requirements: Requirements;
   *    parameters: PasswordParameters;
   *    rules: parser.Rule[],
   *    get entropy(): number;
   *    generate: () => string;
   * }}
   */


  parse(inputString) {
    const rules = parser.parsePasswordRules(inputString);

    const requirements = this._requirementsFromRules(rules);

    if (!requirements) throw new Error('could not generate requirements for ' + JSON.stringify(inputString));

    const parameters = this._passwordGenerationParametersDictionary(requirements);

    return {
      requirements,
      parameters,
      rules,

      get entropy() {
        return Math.log2(parameters.PasswordAllowedCharacters.length ** parameters.NumberOfRequiredRandomCharacters);
      },

      generate: () => {
        const password = this._generatedPasswordMatchingRequirements(requirements, parameters);
        /**
         * The following is unreachable because if user input was incorrect then
         * the parsing phase would throw. The following lines is to satisfy Typescript
         */


        if (password === '') throw new Error('unreachable');
        return password;
      }
    };
  }
  /**
   * Given an array of `Rule's`, convert into `Requirements`
   *
   * @param {parser.Rule[]} passwordRules
   * @returns {Requirements | null}
   */


  _requirementsFromRules(passwordRules) {
    /** @type {Requirements} */
    const requirements = {};

    for (let rule of passwordRules) {
      if (rule.name === parser.RuleName.ALLOWED) {
        console.assert(!('PasswordAllowedCharacters' in requirements));

        const chars = this._charactersFromCharactersClasses(rule.value);

        const scanSet = this._canonicalizedScanSetFromCharacters(chars);

        if (scanSet) {
          requirements.PasswordAllowedCharacters = scanSet;
        }
      } else if (rule.name === parser.RuleName.MAX_CONSECUTIVE) {
        console.assert(!('PasswordRepeatedCharacterLimit' in requirements));
        requirements.PasswordRepeatedCharacterLimit = rule.value;
      } else if (rule.name === parser.RuleName.REQUIRED) {
        let requiredCharacters = requirements.PasswordRequiredCharacters;

        if (!requiredCharacters) {
          requiredCharacters = requirements.PasswordRequiredCharacters = [];
        }

        requiredCharacters.push(this._canonicalizedScanSetFromCharacters(this._charactersFromCharactersClasses(rule.value)));
      } else if (rule.name === parser.RuleName.MIN_LENGTH) {
        requirements.PasswordMinLength = rule.value;
      } else if (rule.name === parser.RuleName.MAX_LENGTH) {
        requirements.PasswordMaxLength = rule.value;
      }
    } // Only include an allowed rule matching SCAN_SET_ORDER (all characters) when a required rule is also present.


    if (requirements.PasswordAllowedCharacters === this.options.SCAN_SET_ORDER && !requirements.PasswordRequiredCharacters) {
      delete requirements.PasswordAllowedCharacters;
    } // Fix up PasswordRequiredCharacters, if needed.


    if (requirements.PasswordRequiredCharacters && requirements.PasswordRequiredCharacters.length === 1 && requirements.PasswordRequiredCharacters[0] === this.options.SCAN_SET_ORDER) {
      delete requirements.PasswordRequiredCharacters;
    }

    return Object.keys(requirements).length ? requirements : null;
  }
  /**
   * @param {number} range
   * @returns {number}
   */


  _randomNumberWithUniformDistribution(range) {
    const getRandomValues = this.options.getRandomValues || safeGlobals.getRandomValues; // Based on the algorithm described in https://pthree.org/2018/06/13/why-the-multiply-and-floor-rng-method-is-biased/

    const max = Math.floor(2 ** 32 / range) * range;
    let x;

    do {
      x = getRandomValues(new Uint32Array(1))[0];
    } while (x >= max);

    return x % range;
  }
  /**
   * @param {number} numberOfRequiredRandomCharacters
   * @param {string} allowedCharacters
   */


  _classicPassword(numberOfRequiredRandomCharacters, allowedCharacters) {
    const length = allowedCharacters.length;
    const randomCharArray = Array(numberOfRequiredRandomCharacters);

    for (let i = 0; i < numberOfRequiredRandomCharacters; i++) {
      const index = this._randomNumberWithUniformDistribution(length);

      randomCharArray[i] = allowedCharacters[index];
    }

    return randomCharArray.join('');
  }
  /**
   * @param {string} password
   * @param {number} consecutiveCharLimit
   * @returns {boolean}
   */


  _passwordHasNotExceededConsecutiveCharLimit(password, consecutiveCharLimit) {
    let longestConsecutiveCharLength = 1;
    let firstConsecutiveCharIndex = 0; // Both "123" or "abc" and "321" or "cba" are considered consecutive.

    let isSequenceAscending;

    for (let i = 1; i < password.length; i++) {
      const currCharCode = password.charCodeAt(i);
      const prevCharCode = password.charCodeAt(i - 1);

      if (isSequenceAscending) {
        // If `isSequenceAscending` is defined, then we know that we are in the middle of an existing
        // pattern. Check if the pattern continues based on whether the previous pattern was
        // ascending or descending.
        if (isSequenceAscending.valueOf() && currCharCode === prevCharCode + 1 || !isSequenceAscending.valueOf() && currCharCode === prevCharCode - 1) {
          continue;
        } // Take into account the case when the sequence transitions from descending
        // to ascending.


        if (currCharCode === prevCharCode + 1) {
          firstConsecutiveCharIndex = i - 1;
          isSequenceAscending = Boolean(true);
          continue;
        } // Take into account the case when the sequence transitions from ascending
        // to descending.


        if (currCharCode === prevCharCode - 1) {
          firstConsecutiveCharIndex = i - 1;
          isSequenceAscending = Boolean(false);
          continue;
        }

        isSequenceAscending = null;
      } else if (currCharCode === prevCharCode + 1) {
        isSequenceAscending = Boolean(true);
        continue;
      } else if (currCharCode === prevCharCode - 1) {
        isSequenceAscending = Boolean(false);
        continue;
      }

      const currConsecutiveCharLength = i - firstConsecutiveCharIndex;

      if (currConsecutiveCharLength > longestConsecutiveCharLength) {
        longestConsecutiveCharLength = currConsecutiveCharLength;
      }

      firstConsecutiveCharIndex = i;
    }

    if (isSequenceAscending) {
      const currConsecutiveCharLength = password.length - firstConsecutiveCharIndex;

      if (currConsecutiveCharLength > longestConsecutiveCharLength) {
        longestConsecutiveCharLength = currConsecutiveCharLength;
      }
    }

    return longestConsecutiveCharLength <= consecutiveCharLimit;
  }
  /**
   * @param {string} password
   * @param {number} repeatedCharLimit
   * @returns {boolean}
   */


  _passwordHasNotExceededRepeatedCharLimit(password, repeatedCharLimit) {
    let longestRepeatedCharLength = 1;
    let lastRepeatedChar = password.charAt(0);
    let lastRepeatedCharIndex = 0;

    for (let i = 1; i < password.length; i++) {
      const currChar = password.charAt(i);

      if (currChar === lastRepeatedChar) {
        continue;
      }

      const currRepeatedCharLength = i - lastRepeatedCharIndex;

      if (currRepeatedCharLength > longestRepeatedCharLength) {
        longestRepeatedCharLength = currRepeatedCharLength;
      }

      lastRepeatedChar = currChar;
      lastRepeatedCharIndex = i;
    }

    return longestRepeatedCharLength <= repeatedCharLimit;
  }
  /**
   * @param {string} password
   * @param {string[]} requiredCharacterSets
   * @returns {boolean}
   */


  _passwordContainsRequiredCharacters(password, requiredCharacterSets) {
    const requiredCharacterSetsLength = requiredCharacterSets.length;
    const passwordLength = password.length;

    for (let i = 0; i < requiredCharacterSetsLength; i++) {
      const requiredCharacterSet = requiredCharacterSets[i];
      let hasRequiredChar = false;

      for (let j = 0; j < passwordLength; j++) {
        const char = password.charAt(j);

        if (requiredCharacterSet.indexOf(char) !== -1) {
          hasRequiredChar = true;
          break;
        }
      }

      if (!hasRequiredChar) {
        return false;
      }
    }

    return true;
  }
  /**
   * @param {string} string1
   * @param {string} string2
   * @returns {boolean}
   */


  _stringsHaveAtLeastOneCommonCharacter(string1, string2) {
    const string2Length = string2.length;

    for (let i = 0; i < string2Length; i++) {
      const char = string2.charAt(i);

      if (string1.indexOf(char) !== -1) {
        return true;
      }
    }

    return false;
  }
  /**
   * @param {Requirements} requirements
   * @returns {PasswordParameters}
   */


  _passwordGenerationParametersDictionary(requirements) {
    let minPasswordLength = requirements.PasswordMinLength;
    const maxPasswordLength = requirements.PasswordMaxLength; // @ts-ignore

    if (minPasswordLength > maxPasswordLength) {
      // Resetting invalid value of min length to zero means "ignore min length parameter in password generation".
      minPasswordLength = 0;
    }

    const requiredCharacterArray = requirements.PasswordRequiredCharacters;
    let allowedCharacters = requirements.PasswordAllowedCharacters;
    let requiredCharacterSets = this.options.defaultRequiredCharacterSets;

    if (requiredCharacterArray) {
      const mutatedRequiredCharacterSets = [];
      const requiredCharacterArrayLength = requiredCharacterArray.length;

      for (let i = 0; i < requiredCharacterArrayLength; i++) {
        const requiredCharacters = requiredCharacterArray[i];

        if (allowedCharacters && this._stringsHaveAtLeastOneCommonCharacter(requiredCharacters, allowedCharacters)) {
          mutatedRequiredCharacterSets.push(requiredCharacters);
        }
      }

      requiredCharacterSets = mutatedRequiredCharacterSets;
    } // If requirements allow, we will generateOrThrow the password in default format: "xxx-xxx-xxx-xxx".


    let numberOfRequiredRandomCharacters = this.options.defaultPasswordLength;

    if (minPasswordLength && minPasswordLength > numberOfRequiredRandomCharacters) {
      numberOfRequiredRandomCharacters = minPasswordLength;
    }

    if (maxPasswordLength && maxPasswordLength < numberOfRequiredRandomCharacters) {
      numberOfRequiredRandomCharacters = maxPasswordLength;
    }

    if (!allowedCharacters) {
      allowedCharacters = this.options.defaultUnambiguousCharacters;
    } // In default password format, we use dashes only as separators, not as symbols you can encounter at a random position.


    if (!requiredCharacterSets) {
      requiredCharacterSets = this.options.defaultRequiredCharacterSets;
    } // If we have more requirements of the type "need a character from set" than the length of the password we want to generateOrThrow, then
    // we will never be able to meet these requirements, and we'll end up in an infinite loop generating passwords. To avoid this,
    // reset required character sets if the requirements are impossible to meet.


    if (requiredCharacterSets.length > numberOfRequiredRandomCharacters) {
      requiredCharacterSets = [];
    } // Do not require any character sets that do not contain allowed characters.


    const requiredCharacterSetsLength = requiredCharacterSets.length;
    const mutatedRequiredCharacterSets = [];
    const allowedCharactersLength = allowedCharacters.length;

    for (let i = 0; i < requiredCharacterSetsLength; i++) {
      const requiredCharacterSet = requiredCharacterSets[i];
      let requiredCharacterSetContainsAllowedCharacters = false;

      for (let j = 0; j < allowedCharactersLength; j++) {
        const character = allowedCharacters.charAt(j);

        if (requiredCharacterSet.indexOf(character) !== -1) {
          requiredCharacterSetContainsAllowedCharacters = true;
          break;
        }
      }

      if (requiredCharacterSetContainsAllowedCharacters) {
        mutatedRequiredCharacterSets.push(requiredCharacterSet);
      }
    }

    requiredCharacterSets = mutatedRequiredCharacterSets;
    return {
      NumberOfRequiredRandomCharacters: numberOfRequiredRandomCharacters,
      PasswordAllowedCharacters: allowedCharacters,
      RequiredCharacterSets: requiredCharacterSets
    };
  }
  /**
   * @param {Requirements | null} requirements
   * @param {PasswordParameters} [parameters]
   * @returns {string}
   */


  _generatedPasswordMatchingRequirements(requirements, parameters) {
    requirements = requirements || {};
    parameters = parameters || this._passwordGenerationParametersDictionary(requirements);
    const numberOfRequiredRandomCharacters = parameters.NumberOfRequiredRandomCharacters;
    const repeatedCharLimit = requirements.PasswordRepeatedCharacterLimit;
    const allowedCharacters = parameters.PasswordAllowedCharacters;
    const shouldCheckRepeatedCharRequirement = !!repeatedCharLimit;

    while (true) {
      const password = this._classicPassword(numberOfRequiredRandomCharacters, allowedCharacters);

      if (!this._passwordContainsRequiredCharacters(password, parameters.RequiredCharacterSets)) {
        continue;
      }

      if (shouldCheckRepeatedCharRequirement) {
        if (repeatedCharLimit !== undefined && repeatedCharLimit >= 1 && !this._passwordHasNotExceededRepeatedCharLimit(password, repeatedCharLimit)) {
          continue;
        }
      }

      const consecutiveCharLimit = requirements.PasswordConsecutiveCharacterLimit;

      if (consecutiveCharLimit && consecutiveCharLimit >= 1) {
        if (!this._passwordHasNotExceededConsecutiveCharLimit(password, consecutiveCharLimit)) {
          continue;
        }
      }

      return password || '';
    }
  }
  /**
   * @param {parser.CustomCharacterClass | parser.NamedCharacterClass} characterClass
   * @returns {string[]}
   */


  _scanSetFromCharacterClass(characterClass) {
    if (characterClass instanceof parser.CustomCharacterClass) {
      return characterClass.characters;
    }

    console.assert(characterClass instanceof parser.NamedCharacterClass);

    switch (characterClass.name) {
      case parser.Identifier.ASCII_PRINTABLE:
      case parser.Identifier.UNICODE:
        return this.options.SCAN_SET_ORDER.split('');

      case parser.Identifier.DIGIT:
        return this.options.SCAN_SET_ORDER.substring(this.options.SCAN_SET_ORDER.indexOf('0'), this.options.SCAN_SET_ORDER.indexOf('9') + 1).split('');

      case parser.Identifier.LOWER:
        return this.options.SCAN_SET_ORDER.substring(this.options.SCAN_SET_ORDER.indexOf('a'), this.options.SCAN_SET_ORDER.indexOf('z') + 1).split('');

      case parser.Identifier.SPECIAL:
        return this.options.SCAN_SET_ORDER.substring(this.options.SCAN_SET_ORDER.indexOf('-'), this.options.SCAN_SET_ORDER.indexOf(']') + 1).split('');

      case parser.Identifier.UPPER:
        return this.options.SCAN_SET_ORDER.substring(this.options.SCAN_SET_ORDER.indexOf('A'), this.options.SCAN_SET_ORDER.indexOf('Z') + 1).split('');
    }

    console.assert(false, parser.SHOULD_NOT_BE_REACHED);
    return [];
  }
  /**
   * @param {(parser.CustomCharacterClass | parser.NamedCharacterClass)[]} characterClasses
   */


  _charactersFromCharactersClasses(characterClasses) {
    const output = [];

    for (let characterClass of characterClasses) {
      output.push(...this._scanSetFromCharacterClass(characterClass));
    }

    return output;
  }
  /**
   * @param {string[]} characters
   * @returns {string}
   */


  _canonicalizedScanSetFromCharacters(characters) {
    if (!characters.length) {
      return '';
    }

    let shadowCharacters = Array.prototype.slice.call(characters);
    shadowCharacters.sort((a, b) => this.options.SCAN_SET_ORDER.indexOf(a) - this.options.SCAN_SET_ORDER.indexOf(b));
    let uniqueCharacters = [shadowCharacters[0]];

    for (let i = 1, length = shadowCharacters.length; i < length; ++i) {
      if (shadowCharacters[i] === shadowCharacters[i - 1]) {
        continue;
      }

      uniqueCharacters.push(shadowCharacters[i]);
    }

    return uniqueCharacters.join('');
  }

}

exports.Password = Password;

_defineProperty(Password, "defaults", defaults);

},{"./constants.js":7,"./rules-parser.js":8}],7:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.constants = void 0;
const DEFAULT_MIN_LENGTH = 20;
const DEFAULT_MAX_LENGTH = 30;
const DEFAULT_REQUIRED_CHARS = '-!?$&#%';
const DEFAULT_UNAMBIGUOUS_CHARS = 'abcdefghijkmnopqrstuvwxyzABCDEFGHIJKLMNPQRSTUVWXYZ0123456789';
const DEFAULT_PASSWORD_RULES = ["minlength: ".concat(DEFAULT_MIN_LENGTH), "maxlength: ".concat(DEFAULT_MAX_LENGTH), "required: [".concat(DEFAULT_REQUIRED_CHARS, "]"), "allowed: [".concat(DEFAULT_UNAMBIGUOUS_CHARS, "]")].join('; ');
const constants = {
  DEFAULT_MIN_LENGTH,
  DEFAULT_MAX_LENGTH,
  DEFAULT_PASSWORD_RULES,
  DEFAULT_REQUIRED_CHARS,
  DEFAULT_UNAMBIGUOUS_CHARS
};
exports.constants = constants;

},{}],8:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.SHOULD_NOT_BE_REACHED = exports.RuleName = exports.Rule = exports.ParserError = exports.NamedCharacterClass = exports.Identifier = exports.CustomCharacterClass = void 0;
exports.parsePasswordRules = parsePasswordRules;
// Copyright (c) 2019 - 2020 Apple Inc. Licensed under MIT License.

/*
 *
 * NOTE:
 *
 * This file was taken as intended from https://github.com/apple/password-manager-resources.
 *
 * The only additions from DuckDuckGo employees are
 *
 * 1) exporting some identifiers
 * 2) adding some JSDoc comments
 * 3) making this parser throw when it cannot produce any rules
 *    ^ the default implementation still returns a base-line ruleset, which we didn't want.
 *
 */
const Identifier = {
  ASCII_PRINTABLE: 'ascii-printable',
  DIGIT: 'digit',
  LOWER: 'lower',
  SPECIAL: 'special',
  UNICODE: 'unicode',
  UPPER: 'upper'
};
exports.Identifier = Identifier;
const RuleName = {
  ALLOWED: 'allowed',
  MAX_CONSECUTIVE: 'max-consecutive',
  REQUIRED: 'required',
  MIN_LENGTH: 'minlength',
  MAX_LENGTH: 'maxlength'
};
exports.RuleName = RuleName;
const CHARACTER_CLASS_START_SENTINEL = '[';
const CHARACTER_CLASS_END_SENTINEL = ']';
const PROPERTY_VALUE_SEPARATOR = ',';
const PROPERTY_SEPARATOR = ';';
const PROPERTY_VALUE_START_SENTINEL = ':';
const SPACE_CODE_POINT = ' '.codePointAt(0);
const SHOULD_NOT_BE_REACHED = 'Should not be reached';
exports.SHOULD_NOT_BE_REACHED = SHOULD_NOT_BE_REACHED;

class Rule {
  constructor(name, value) {
    this._name = name;
    this.value = value;
  }

  get name() {
    return this._name;
  }

  toString() {
    return JSON.stringify(this);
  }

}

exports.Rule = Rule;
;

class NamedCharacterClass {
  constructor(name) {
    console.assert(_isValidRequiredOrAllowedPropertyValueIdentifier(name));
    this._name = name;
  }

  get name() {
    return this._name.toLowerCase();
  }

  toString() {
    return this._name;
  }

  toHTMLString() {
    return this._name;
  }

}

exports.NamedCharacterClass = NamedCharacterClass;
;

class ParserError extends Error {}

exports.ParserError = ParserError;
;

class CustomCharacterClass {
  constructor(characters) {
    console.assert(characters instanceof Array);
    this._characters = characters;
  }

  get characters() {
    return this._characters;
  }

  toString() {
    return "[".concat(this._characters.join(''), "]");
  }

  toHTMLString() {
    return "[".concat(this._characters.join('').replace('"', '&quot;'), "]");
  }

}

exports.CustomCharacterClass = CustomCharacterClass;
; // MARK: Lexer functions

function _isIdentifierCharacter(c) {
  console.assert(c.length === 1); // eslint-disable-next-line no-mixed-operators

  return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c === '-';
}

function _isASCIIDigit(c) {
  console.assert(c.length === 1);
  return c >= '0' && c <= '9';
}

function _isASCIIPrintableCharacter(c) {
  console.assert(c.length === 1);
  return c >= ' ' && c <= '~';
}

function _isASCIIWhitespace(c) {
  console.assert(c.length === 1);
  return c === ' ' || c === '\f' || c === '\n' || c === '\r' || c === '\t';
} // MARK: ASCII printable character bit set and canonicalization functions


function _bitSetIndexForCharacter(c) {
  console.assert(c.length === 1); // @ts-ignore

  return c.codePointAt(0) - SPACE_CODE_POINT;
}

function _characterAtBitSetIndex(index) {
  return String.fromCodePoint(index + SPACE_CODE_POINT);
}

function _markBitsForNamedCharacterClass(bitSet, namedCharacterClass) {
  console.assert(bitSet instanceof Array);
  console.assert(namedCharacterClass.name !== Identifier.UNICODE);
  console.assert(namedCharacterClass.name !== Identifier.ASCII_PRINTABLE);

  if (namedCharacterClass.name === Identifier.UPPER) {
    bitSet.fill(true, _bitSetIndexForCharacter('A'), _bitSetIndexForCharacter('Z') + 1);
  } else if (namedCharacterClass.name === Identifier.LOWER) {
    bitSet.fill(true, _bitSetIndexForCharacter('a'), _bitSetIndexForCharacter('z') + 1);
  } else if (namedCharacterClass.name === Identifier.DIGIT) {
    bitSet.fill(true, _bitSetIndexForCharacter('0'), _bitSetIndexForCharacter('9') + 1);
  } else if (namedCharacterClass.name === Identifier.SPECIAL) {
    bitSet.fill(true, _bitSetIndexForCharacter(' '), _bitSetIndexForCharacter('/') + 1);
    bitSet.fill(true, _bitSetIndexForCharacter(':'), _bitSetIndexForCharacter('@') + 1);
    bitSet.fill(true, _bitSetIndexForCharacter('['), _bitSetIndexForCharacter('`') + 1);
    bitSet.fill(true, _bitSetIndexForCharacter('{'), _bitSetIndexForCharacter('~') + 1);
  } else {
    console.assert(false, SHOULD_NOT_BE_REACHED, namedCharacterClass);
  }
}

function _markBitsForCustomCharacterClass(bitSet, customCharacterClass) {
  for (let character of customCharacterClass.characters) {
    bitSet[_bitSetIndexForCharacter(character)] = true;
  }
}

function _canonicalizedPropertyValues(propertyValues, keepCustomCharacterClassFormatCompliant) {
  // @ts-ignore
  let asciiPrintableBitSet = new Array('~'.codePointAt(0) - ' '.codePointAt(0) + 1);

  for (let propertyValue of propertyValues) {
    if (propertyValue instanceof NamedCharacterClass) {
      if (propertyValue.name === Identifier.UNICODE) {
        return [new NamedCharacterClass(Identifier.UNICODE)];
      }

      if (propertyValue.name === Identifier.ASCII_PRINTABLE) {
        return [new NamedCharacterClass(Identifier.ASCII_PRINTABLE)];
      }

      _markBitsForNamedCharacterClass(asciiPrintableBitSet, propertyValue);
    } else if (propertyValue instanceof CustomCharacterClass) {
      _markBitsForCustomCharacterClass(asciiPrintableBitSet, propertyValue);
    }
  }

  let charactersSeen = [];

  function checkRange(start, end) {
    let temp = [];

    for (let i = _bitSetIndexForCharacter(start); i <= _bitSetIndexForCharacter(end); ++i) {
      if (asciiPrintableBitSet[i]) {
        temp.push(_characterAtBitSetIndex(i));
      }
    }

    let result = temp.length === _bitSetIndexForCharacter(end) - _bitSetIndexForCharacter(start) + 1;

    if (!result) {
      charactersSeen = charactersSeen.concat(temp);
    }

    return result;
  }

  let hasAllUpper = checkRange('A', 'Z');
  let hasAllLower = checkRange('a', 'z');
  let hasAllDigits = checkRange('0', '9'); // Check for special characters, accounting for characters that are given special treatment (i.e. '-' and ']')

  let hasAllSpecial = false;
  let hasDash = false;
  let hasRightSquareBracket = false;
  let temp = [];

  for (let i = _bitSetIndexForCharacter(' '); i <= _bitSetIndexForCharacter('/'); ++i) {
    if (!asciiPrintableBitSet[i]) {
      continue;
    }

    let character = _characterAtBitSetIndex(i);

    if (keepCustomCharacterClassFormatCompliant && character === '-') {
      hasDash = true;
    } else {
      temp.push(character);
    }
  }

  for (let i = _bitSetIndexForCharacter(':'); i <= _bitSetIndexForCharacter('@'); ++i) {
    if (asciiPrintableBitSet[i]) {
      temp.push(_characterAtBitSetIndex(i));
    }
  }

  for (let i = _bitSetIndexForCharacter('['); i <= _bitSetIndexForCharacter('`'); ++i) {
    if (!asciiPrintableBitSet[i]) {
      continue;
    }

    let character = _characterAtBitSetIndex(i);

    if (keepCustomCharacterClassFormatCompliant && character === ']') {
      hasRightSquareBracket = true;
    } else {
      temp.push(character);
    }
  }

  for (let i = _bitSetIndexForCharacter('{'); i <= _bitSetIndexForCharacter('~'); ++i) {
    if (asciiPrintableBitSet[i]) {
      temp.push(_characterAtBitSetIndex(i));
    }
  }

  if (hasDash) {
    temp.unshift('-');
  }

  if (hasRightSquareBracket) {
    temp.push(']');
  }

  let numberOfSpecialCharacters = _bitSetIndexForCharacter('/') - _bitSetIndexForCharacter(' ') + 1 + (_bitSetIndexForCharacter('@') - _bitSetIndexForCharacter(':') + 1) + (_bitSetIndexForCharacter('`') - _bitSetIndexForCharacter('[') + 1) + (_bitSetIndexForCharacter('~') - _bitSetIndexForCharacter('{') + 1);
  hasAllSpecial = temp.length === numberOfSpecialCharacters;

  if (!hasAllSpecial) {
    charactersSeen = charactersSeen.concat(temp);
  }

  let result = [];

  if (hasAllUpper && hasAllLower && hasAllDigits && hasAllSpecial) {
    return [new NamedCharacterClass(Identifier.ASCII_PRINTABLE)];
  }

  if (hasAllUpper) {
    result.push(new NamedCharacterClass(Identifier.UPPER));
  }

  if (hasAllLower) {
    result.push(new NamedCharacterClass(Identifier.LOWER));
  }

  if (hasAllDigits) {
    result.push(new NamedCharacterClass(Identifier.DIGIT));
  }

  if (hasAllSpecial) {
    result.push(new NamedCharacterClass(Identifier.SPECIAL));
  }

  if (charactersSeen.length) {
    result.push(new CustomCharacterClass(charactersSeen));
  }

  return result;
} // MARK: Parser functions


function _indexOfNonWhitespaceCharacter(input) {
  let position = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 0;
  console.assert(position >= 0);
  console.assert(position <= input.length);
  let length = input.length;

  while (position < length && _isASCIIWhitespace(input[position])) {
    ++position;
  }

  return position;
}

function _parseIdentifier(input, position) {
  console.assert(position >= 0);
  console.assert(position < input.length);
  console.assert(_isIdentifierCharacter(input[position]));
  let length = input.length;
  let seenIdentifiers = [];

  do {
    let c = input[position];

    if (!_isIdentifierCharacter(c)) {
      break;
    }

    seenIdentifiers.push(c);
    ++position;
  } while (position < length);

  return [seenIdentifiers.join(''), position];
}

function _isValidRequiredOrAllowedPropertyValueIdentifier(identifier) {
  return identifier && Object.values(Identifier).includes(identifier.toLowerCase());
}

function _parseCustomCharacterClass(input, position) {
  console.assert(position >= 0);
  console.assert(position < input.length);
  console.assert(input[position] === CHARACTER_CLASS_START_SENTINEL);
  let length = input.length;
  ++position;

  if (position >= length) {
    // console.error('Found end-of-line instead of character class character')
    return [null, position];
  }

  let initialPosition = position;
  let result = [];

  do {
    let c = input[position];

    if (!_isASCIIPrintableCharacter(c)) {
      ++position;
      continue;
    }

    if (c === '-' && position - initialPosition > 0) {
      // FIXME: Should this be an error?
      console.warn("Ignoring '-'; a '-' may only appear as the first character in a character class");
      ++position;
      continue;
    }

    result.push(c);
    ++position;

    if (c === CHARACTER_CLASS_END_SENTINEL) {
      break;
    }
  } while (position < length);

  if (position < length && input[position] !== CHARACTER_CLASS_END_SENTINEL) {
    // Fix up result; we over consumed.
    result.pop();
    return [result, position];
  } else if (position === length && input[position - 1] === CHARACTER_CLASS_END_SENTINEL) {
    // Fix up result; we over consumed.
    result.pop();
    return [result, position];
  }

  if (position < length && input[position] === CHARACTER_CLASS_END_SENTINEL) {
    return [result, position + 1];
  } // console.error('Found end-of-line instead of end of character class')


  return [null, position];
}

function _parsePasswordRequiredOrAllowedPropertyValue(input, position) {
  console.assert(position >= 0);
  console.assert(position < input.length);
  let length = input.length;
  let propertyValues = [];

  while (true) {
    if (_isIdentifierCharacter(input[position])) {
      let identifierStartPosition = position; // eslint-disable-next-line no-redeclare

      var [propertyValue, position] = _parseIdentifier(input, position);

      if (!_isValidRequiredOrAllowedPropertyValueIdentifier(propertyValue)) {
        // console.error('Unrecognized property value identifier: ' + propertyValue)
        return [null, identifierStartPosition];
      }

      propertyValues.push(new NamedCharacterClass(propertyValue));
    } else if (input[position] === CHARACTER_CLASS_START_SENTINEL) {
      // eslint-disable-next-line no-redeclare
      var [propertyValue, position] = _parseCustomCharacterClass(input, position);

      if (propertyValue && propertyValue.length) {
        propertyValues.push(new CustomCharacterClass(propertyValue));
      }
    } else {
      // console.error('Failed to find start of property value: ' + input.substr(position))
      return [null, position];
    }

    position = _indexOfNonWhitespaceCharacter(input, position);

    if (position >= length || input[position] === PROPERTY_SEPARATOR) {
      break;
    }

    if (input[position] === PROPERTY_VALUE_SEPARATOR) {
      position = _indexOfNonWhitespaceCharacter(input, position + 1);

      if (position >= length) {
        // console.error('Found end-of-line instead of start of next property value')
        return [null, position];
      }

      continue;
    } // console.error('Failed to find start of next property or property value: ' + input.substr(position))


    return [null, position];
  }

  return [propertyValues, position];
}
/**
 * @param input
 * @param position
 * @returns {[Rule|null, number, string|undefined]}
 * @private
 */


function _parsePasswordRule(input, position) {
  console.assert(position >= 0);
  console.assert(position < input.length);
  console.assert(_isIdentifierCharacter(input[position]));
  let length = input.length;
  var mayBeIdentifierStartPosition = position; // eslint-disable-next-line no-redeclare

  var [identifier, position] = _parseIdentifier(input, position);

  if (!Object.values(RuleName).includes(identifier)) {
    // console.error('Unrecognized property name: ' + identifier)
    return [null, mayBeIdentifierStartPosition, undefined];
  }

  if (position >= length) {
    // console.error('Found end-of-line instead of start of property value')
    return [null, position, undefined];
  }

  if (input[position] !== PROPERTY_VALUE_START_SENTINEL) {
    // console.error('Failed to find start of property value: ' + input.substr(position))
    return [null, position, undefined];
  }

  let property = {
    name: identifier,
    value: null
  };
  position = _indexOfNonWhitespaceCharacter(input, position + 1); // Empty value

  if (position >= length || input[position] === PROPERTY_SEPARATOR) {
    return [new Rule(property.name, property.value), position, undefined];
  }

  switch (identifier) {
    case RuleName.ALLOWED:
    case RuleName.REQUIRED:
      {
        // eslint-disable-next-line no-redeclare
        var [propertyValue, position] = _parsePasswordRequiredOrAllowedPropertyValue(input, position);

        if (propertyValue) {
          property.value = propertyValue;
        }

        return [new Rule(property.name, property.value), position, undefined];
      }

    case RuleName.MAX_CONSECUTIVE:
      {
        // eslint-disable-next-line no-redeclare
        var [propertyValue, position] = _parseMaxConsecutivePropertyValue(input, position);

        if (propertyValue) {
          property.value = propertyValue;
        }

        return [new Rule(property.name, property.value), position, undefined];
      }

    case RuleName.MIN_LENGTH:
    case RuleName.MAX_LENGTH:
      {
        // eslint-disable-next-line no-redeclare
        var [propertyValue, position] = _parseMinLengthMaxLengthPropertyValue(input, position);

        if (propertyValue) {
          property.value = propertyValue;
        }

        return [new Rule(property.name, property.value), position, undefined];
      }
  }

  console.assert(false, SHOULD_NOT_BE_REACHED);
  return [null, -1, undefined];
}

function _parseMinLengthMaxLengthPropertyValue(input, position) {
  return _parseInteger(input, position);
}

function _parseMaxConsecutivePropertyValue(input, position) {
  return _parseInteger(input, position);
}

function _parseInteger(input, position) {
  console.assert(position >= 0);
  console.assert(position < input.length);

  if (!_isASCIIDigit(input[position])) {
    // console.error('Failed to parse value of type integer; not a number: ' + input.substr(position))
    return [null, position];
  }

  let length = input.length; // let initialPosition = position

  let result = 0;

  do {
    result = 10 * result + parseInt(input[position], 10);
    ++position;
  } while (position < length && input[position] !== PROPERTY_SEPARATOR && _isASCIIDigit(input[position]));

  if (position >= length || input[position] === PROPERTY_SEPARATOR) {
    return [result, position];
  } // console.error('Failed to parse value of type integer; not a number: ' + input.substr(initialPosition))


  return [null, position];
}
/**
 * @param input
 * @returns {[Rule[]|null, string|undefined]}
 * @private
 */


function _parsePasswordRulesInternal(input) {
  let parsedProperties = [];
  let length = input.length;

  var position = _indexOfNonWhitespaceCharacter(input);

  while (position < length) {
    if (!_isIdentifierCharacter(input[position])) {
      // console.warn('Failed to find start of property: ' + input.substr(position))
      return [parsedProperties, undefined];
    } // eslint-disable-next-line no-redeclare


    var [parsedProperty, position, message] = _parsePasswordRule(input, position);

    if (parsedProperty && parsedProperty.value) {
      parsedProperties.push(parsedProperty);
    }

    position = _indexOfNonWhitespaceCharacter(input, position);

    if (position >= length) {
      break;
    }

    if (input[position] === PROPERTY_SEPARATOR) {
      position = _indexOfNonWhitespaceCharacter(input, position + 1);

      if (position >= length) {
        return [parsedProperties, undefined];
      }

      continue;
    } // console.error('Failed to find start of next property: ' + input.substr(position))


    return [null, message || 'Failed to find start of next property: ' + input.substr(position)];
  }

  return [parsedProperties, undefined];
}
/**
 * @param {string} input
 * @param {boolean} [formatRulesForMinifiedVersion]
 * @returns {Rule[]}
 */


function parsePasswordRules(input, formatRulesForMinifiedVersion) {
  let [passwordRules, maybeMessage] = _parsePasswordRulesInternal(input);

  if (!passwordRules) {
    throw new ParserError(maybeMessage);
  }

  if (passwordRules.length === 0) {
    throw new ParserError('No valid rules were provided');
  } // When formatting rules for minified version, we should keep the formatted rules
  // as similar to the input as possible. Avoid copying required rules to allowed rules.


  let suppressCopyingRequiredToAllowed = formatRulesForMinifiedVersion;
  let requiredRules = [];
  let newAllowedValues = [];
  let minimumMaximumConsecutiveCharacters = null;
  let maximumMinLength = 0;
  let minimumMaxLength = null;

  for (let rule of passwordRules) {
    switch (rule.name) {
      case RuleName.MAX_CONSECUTIVE:
        minimumMaximumConsecutiveCharacters = minimumMaximumConsecutiveCharacters ? Math.min(rule.value, minimumMaximumConsecutiveCharacters) : rule.value;
        break;

      case RuleName.MIN_LENGTH:
        maximumMinLength = Math.max(rule.value, maximumMinLength);
        break;

      case RuleName.MAX_LENGTH:
        minimumMaxLength = minimumMaxLength ? Math.min(rule.value, minimumMaxLength) : rule.value;
        break;

      case RuleName.REQUIRED:
        rule.value = _canonicalizedPropertyValues(rule.value, formatRulesForMinifiedVersion);
        requiredRules.push(rule);

        if (!suppressCopyingRequiredToAllowed) {
          newAllowedValues = newAllowedValues.concat(rule.value);
        }

        break;

      case RuleName.ALLOWED:
        newAllowedValues = newAllowedValues.concat(rule.value);
        break;
    }
  }

  let newPasswordRules = [];

  if (maximumMinLength > 0) {
    newPasswordRules.push(new Rule(RuleName.MIN_LENGTH, maximumMinLength));
  }

  if (minimumMaxLength !== null) {
    newPasswordRules.push(new Rule(RuleName.MAX_LENGTH, minimumMaxLength));
  }

  if (minimumMaximumConsecutiveCharacters !== null) {
    newPasswordRules.push(new Rule(RuleName.MAX_CONSECUTIVE, minimumMaximumConsecutiveCharacters));
  }

  let sortedRequiredRules = requiredRules.sort(function (a, b) {
    const namedCharacterClassOrder = [Identifier.LOWER, Identifier.UPPER, Identifier.DIGIT, Identifier.SPECIAL, Identifier.ASCII_PRINTABLE, Identifier.UNICODE];
    let aIsJustOneNamedCharacterClass = a.value.length === 1 && a.value[0] instanceof NamedCharacterClass;
    let bIsJustOneNamedCharacterClass = b.value.length === 1 && b.value[0] instanceof NamedCharacterClass;

    if (aIsJustOneNamedCharacterClass && !bIsJustOneNamedCharacterClass) {
      return -1;
    }

    if (!aIsJustOneNamedCharacterClass && bIsJustOneNamedCharacterClass) {
      return 1;
    }

    if (aIsJustOneNamedCharacterClass && bIsJustOneNamedCharacterClass) {
      let aIndex = namedCharacterClassOrder.indexOf(a.value[0].name);
      let bIndex = namedCharacterClassOrder.indexOf(b.value[0].name);
      return aIndex - bIndex;
    }

    return 0;
  });
  newPasswordRules = newPasswordRules.concat(sortedRequiredRules);
  newAllowedValues = _canonicalizedPropertyValues(newAllowedValues, suppressCopyingRequiredToAllowed);

  if (!suppressCopyingRequiredToAllowed && !newAllowedValues.length) {
    newAllowedValues = [new NamedCharacterClass(Identifier.ASCII_PRINTABLE)];
  }

  if (newAllowedValues.length) {
    newPasswordRules.push(new Rule(RuleName.ALLOWED, newAllowedValues));
  }

  return newPasswordRules;
}

},{}],9:[function(require,module,exports){
module.exports={
  "163.com": {
    "password-rules": "minlength: 6; maxlength: 16;"
  },
  "1800flowers.com": {
    "password-rules": "minlength: 6; required: lower, upper; required: digit;"
  },
  "access.service.gov.uk": {
    "password-rules": "minlength: 10; required: lower; required: upper; required: digit; required: special;"
  },
  "admiral.com": {
    "password-rules": "minlength: 8; required: digit; required: [- !\"#$&'()*+,.:;<=>?@[^_`{|}~]]; allowed: lower, upper;"
  },
  "ae.com": {
    "password-rules": "minlength: 8; maxlength: 25; required: lower; required: upper; required: digit;"
  },
  "aetna.com": {
    "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 2; required: upper; required: digit; allowed: lower, [-_&#@];"
  },
  "airasia.com": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit;"
  },
  "ajisushionline.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; allowed: [ !#$%&*?@];"
  },
  "aliexpress.com": {
    "password-rules": "minlength: 6; maxlength: 20; allowed: lower, upper, digit;"
  },
  "alliantcreditunion.com": {
    "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 3; required: lower, upper; required: digit; allowed: [!#$*];"
  },
  "allianz.com.br": {
    "password-rules": "minlength: 4; maxlength: 4;"
  },
  "americanexpress.com": {
    "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 4; required: lower, upper; required: digit; allowed: [%&_?#=];"
  },
  "anatel.gov.br": {
    "password-rules": "minlength: 6; maxlength: 15; allowed: lower, upper, digit;"
  },
  "ancestry.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [-!\"#$%&'()*+,./:;<=>?@[^_`{|}~]];"
  },
  "angieslist.com": {
    "password-rules": "minlength: 6; maxlength: 15;"
  },
  "anthem.com": {
    "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 3; required: lower, upper; required: digit; allowed: [!$*?@|];"
  },
  "app.digio.in": {
    "password-rules": "minlength: 8; maxlength: 15;"
  },
  "app.parkmobile.io": {
    "password-rules": "minlength: 8; maxlength: 25; required: lower; required: upper; required: digit; required: [!@#$%^&];"
  },
  "apple.com": {
    "password-rules": "minlength: 8; maxlength: 63; required: lower; required: upper; required: digit; allowed: ascii-printable;"
  },
  "areariservata.bancaetica.it": {
    "password-rules": "minlength: 8; maxlength: 10; required: lower; required: upper; required: digit; required: [!#&*+/=@_];"
  },
  "artscyclery.com": {
    "password-rules": "minlength: 6; maxlength: 19;"
  },
  "astonmartinf1.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: special;"
  },
  "autify.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!\"#$%&'()*+,./:;<=>?@[^_`{|}~]];"
  },
  "axa.de": {
    "password-rules": "minlength: 8; maxlength: 65; required: lower; required: upper; required: digit; allowed: [-!\"§$%&/()=?;:_+*'#];"
  },
  "baidu.com": {
    "password-rules": "minlength: 6; maxlength: 14;"
  },
  "bancochile.cl": {
    "password-rules": "minlength: 8; maxlength: 8; required: lower; required: upper; required: digit;"
  },
  "bankofamerica.com": {
    "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 3; required: lower; required: upper; required: digit; allowed: [-@#*()+={}/?~;,._];"
  },
  "battle.net": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower, upper; allowed: digit, special;"
  },
  "bcassessment.ca": {
    "password-rules": "minlength: 8; maxlength: 14;"
  },
  "belkin.com": {
    "password-rules": "minlength: 8; required: lower, upper; required: digit; required: [$!@~_,%&];"
  },
  "benefitslogin.discoverybenefits.com": {
    "password-rules": "minlength: 10; required: upper; required: digit; required: [!#$%&*?@]; allowed: lower;"
  },
  "benjerry.com": {
    "password-rules": "required: upper; required: upper; required: digit; required: digit; required: special; required: special; allowed: lower;"
  },
  "bestbuy.com": {
    "password-rules": "minlength: 20; required: lower; required: upper; required: digit; required: special;"
  },
  "bhphotovideo.com": {
    "password-rules": "maxlength: 15;"
  },
  "bilibili.com": {
    "password-rules": "maxlength: 16;"
  },
  "billerweb.com": {
    "password-rules": "minlength: 8; max-consecutive: 2; required: digit; required: upper,lower;"
  },
  "biovea.com": {
    "password-rules": "maxlength: 19;"
  },
  "bitly.com": {
    "password-rules": "minlength: 6; required: lower; required: upper; required: digit; required: [`!@#$%^&*()+~{}'\";:<>?]];"
  },
  "bloomingdales.com": {
    "password-rules": "minlength: 7; maxlength: 16; required: lower, upper; required: digit; required: [`!@#$%^&*()+~{}'\";:<>?]];"
  },
  "bluesguitarunleashed.com": {
    "password-rules": "allowed: lower, upper, digit, [!$#@];"
  },
  "bochk.com": {
    "password-rules": "minlength: 8; maxlength: 12; max-consecutive: 3; required: lower; required: upper; required: digit; allowed: [#$%&()*+,.:;<=>?@_];"
  },
  "box.com": {
    "password-rules": "minlength: 6; maxlength: 20; required: lower; required: upper; required: digit; required: digit;"
  },
  "brighthorizons.com": {
    "password-rules": "minlength: 8; maxlength: 16;"
  },
  "callofduty.com": {
    "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 2; required: lower, upper; required: digit;"
  },
  "capitalone.com": {
    "password-rules": "minlength: 8; maxlength: 32; required: lower, upper; required: digit; allowed: [-_./\\@$*&!#];"
  },
  "cardbenefitservices.com": {
    "password-rules": "minlength: 7; maxlength: 100; required: lower, upper; required: digit;"
  },
  "cb2.com": {
    "password-rules": "minlength: 7; maxlength: 18; required: lower, upper; required: digit;"
  },
  "ccs-grp.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: digit; required: upper,lower; allowed: [-!#$%&'+./=?\\^_`{|}~];"
  },
  "cecredentialtrust.com": {
    "password-rules": "minlength: 12; required: lower; required: upper; required: digit; required: [!#$%&*@^];"
  },
  "chase.com": {
    "password-rules": "minlength: 8; maxlength: 32; max-consecutive: 2; required: lower, upper; required: digit; required: [!#$%+/=@~];"
  },
  "cigna.co.uk": {
    "password-rules": "minlength: 8; maxlength: 12; required: lower; required: upper; required: digit;"
  },
  "citi.com": {
    "password-rules": "minlength: 6; maxlength: 50; max-consecutive: 2; required: lower, upper; required: digit; allowed: [_!@$]"
  },
  "claimlookup.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [@#$%^&+=!];"
  },
  "claro.com.br": {
    "password-rules": "minlength: 8; required: lower; allowed: upper, digit, [-!@#$%&*_+=<>];"
  },
  "clien.net": {
    "password-rules": "minlength: 5; required: lower, upper; required: digit;"
  },
  "collectivehealth.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit;"
  },
  "comcastpaymentcenter.com": {
    "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 2;required: lower, upper; required: digit;"
  },
  "comed.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: [-~!@#$%^&*_+=`|(){}[:;\"'<>,.?/\\]];"
  },
  "commerzbank.de": {
    "password-rules": "minlength: 5; maxlength: 8; required: lower, upper; required: digit;"
  },
  "consorsbank.de": {
    "password-rules": "minlength: 5; maxlength: 5; required: lower, upper, digit;"
  },
  "consorsfinanz.de": {
    "password-rules": "minlength: 6; maxlength: 15; allowed: lower, upper, digit, [-.];"
  },
  "costco.com": {
    "password-rules": "minlength: 8; maxlength: 20; required: lower, upper; allowed: digit, [-!#$%&'()*+/:;=?@[^_`{|}~]];"
  },
  "coursera.com": {
    "password-rules": "minlength: 8; maxlength: 72;"
  },
  "cox.com": {
    "password-rules": "minlength: 8; maxlength: 24; required: digit; required: upper,lower; allowed: [!#$%()*@^];"
  },
  "crateandbarrel.com": {
    "password-rules": "minlength: 9; maxlength: 64; required: lower; required: upper; required: digit; required: [!\"#$%&()*,.:<>?@^_{|}];"
  },
  "cvs.com": {
    "password-rules": "minlength: 8; maxlength: 25; required: lower, upper; required: digit; allowed: [!@#$%^&*()];"
  },
  "dailymail.co.uk": {
    "password-rules": "minlength: 5; maxlength: 15;"
  },
  "dan.org": {
    "password-rules": "minlength: 8; maxlength: 25; required: lower; required: upper; required: digit; required: [!@$%^&*];"
  },
  "danawa.com": {
    "password-rules": "minlength: 8; maxlength: 21; required: lower, upper; required: digit; required: [!@$%^&*];"
  },
  "darty.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit;"
  },
  "dbs.com.hk": {
    "password-rules": "minlength: 8; maxlength: 30; required: lower; required: upper; required: digit;"
  },
  "decluttr.com": {
    "password-rules": "minlength: 8; maxlength: 45; required: lower; required: upper; required: digit;"
  },
  "delta.com": {
    "password-rules": "minlength: 8; maxlength: 20; required: lower; required: upper; required: digit;"
  },
  "deutsche-bank.de": {
    "password-rules": "minlength: 5; maxlength: 5; required: lower, upper, digit;"
  },
  "devstore.cn": {
    "password-rules": "minlength: 6; maxlength: 12;"
  },
  "dickssportinggoods.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&*?@^];"
  },
  "dkb.de": {
    "password-rules": "minlength: 8; maxlength: 38; required: lower, upper; required: digit; allowed: [-äüöÄÜÖß!$%&/()=?+#,.:];"
  },
  "dmm.com": {
    "password-rules": "minlength: 4; maxlength: 16; required: lower; required: upper; required: digit;"
  },
  "dowjones.com": {
    "password-rules": "maxlength: 15;"
  },
  "ea.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: special;"
  },
  "easycoop.com": {
    "password-rules": "minlength: 8; required: upper; required: special; allowed: lower, digit;"
  },
  "easyjet.com": {
    "password-rules": "minlength: 6; maxlength: 20; required: lower; required: upper; required: digit; required: [-];"
  },
  "ebrap.org": {
    "password-rules": "minlength: 15; required: lower; required: lower; required: upper; required: upper; required: digit; required: digit; required: [-!@#$%^&*()_+|~=`{}[:\";'?,./.]]; required: [-!@#$%^&*()_+|~=`{}[:\";'?,./.]];"
  },
  "ecompanystore.com": {
    "password-rules": "minlength: 8; maxlength: 16; max-consecutive: 2; required: lower; required: upper; required: digit; required: [#$%*+.=@^_];"
  },
  "eddservices.edd.ca.gov": {
    "password-rules": "minlength: 8; maxlength: 12; required: lower; required: upper; required: digit; required: [!@#$%^&*()];"
  },
  "empower-retirement.com": {
    "password-rules": "minlength: 8; maxlength: 16;"
  },
  "epicgames.com": {
    "password-rules": "minlength: 7; required: lower; required: upper; required: digit; required: [-!\"#$%&'()*+,./:;<=>?@[^_`{|}~]];"
  },
  "epicmix.com": {
    "password-rules": "minlength: 8; maxlength: 16;"
  },
  "equifax.com": {
    "password-rules": "minlength: 8; maxlength: 20; required: lower; required: upper; required: digit; required: [!$*+@];"
  },
  "essportal.excelityglobal.com": {
    "password-rules": "minlength: 6; maxlength: 8; allowed: lower, upper, digit;"
  },
  "ettoday.net": {
    "password-rules": "minlength: 6; maxlength: 12;"
  },
  "examservice.com.tw": {
    "password-rules": "minlength: 6; maxlength: 8;"
  },
  "expertflyer.com": {
    "password-rules": "minlength: 5; maxlength: 16; required: lower, upper; required: digit;"
  },
  "extraspace.com": {
    "password-rules": "minlength: 8; maxlength: 20; allowed: lower; required: upper, digit, [!#$%&*?@];"
  },
  "ezpassva.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: special;"
  },
  "fc2.com": {
    "password-rules": "minlength: 8; maxlength: 16;"
  },
  "fedex.com": {
    "password-rules": "minlength: 8; max-consecutive: 3; required: lower; required: upper; required: digit; allowed: [-!@#$%^&*_+=`|(){}[:;,.?]];"
  },
  "fidelity.com": {
    "password-rules": "minlength: 6; maxlength: 20; required: lower; allowed: upper,digit,[!$%'()+,./:;=?@^_|~];"
  },
  "flysas.com": {
    "password-rules": "minlength: 8; maxlength: 14; required: lower; required: upper; required: digit; required: [-~!@#$%^&_+=`|(){}[:\"'<>,.?]];"
  },
  "fnac.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit;"
  },
  "fuelrewards.com": {
    "password-rules": "minlength: 8; maxlength: 16; allowed: upper,lower,digit,[!#$%@];"
  },
  "gamestop.com": {
    "password-rules": "minlength: 8; maxlength: 225; required: lower; required: upper; required: digit; required: [!@#$%];"
  },
  "getflywheel.com": {
    "password-rules": "minlength: 7; maxlength: 72;"
  },
  "girlscouts.org": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: [$#!];"
  },
  "gmx.net": {
    "password-rules": "minlength: 8; maxlength: 40; allowed: lower, upper, digit, [-<=>~!|()@#{}$%,.?^'&*_+`:;\"[]];"
  },
  "google.com": {
    "password-rules": "minlength: 8; allowed: lower, upper, digit, [-!\"#$%&'()*+,./:;<=>?@[^_{|}~]];"
  },
  "guardiananytime.com": {
    "password-rules": "minlength: 8; maxlength: 50; max-consecutive: 2; required: lower; required: upper; required: digit, [-~!@#$%^&*_+=`|(){}[:;,.?]];"
  },
  "gwl.greatwestlife.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [-!#$%_=+<>];"
  },
  "hangseng.com": {
    "password-rules": "minlength: 8; maxlength: 30; required: lower; required: upper; required: digit;"
  },
  "hawaiianairlines.com": {
    "password-rules": "maxlength: 16;"
  },
  "hertz.com": {
    "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
  },
  "hetzner.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit, special;"
  },
  "hilton.com": {
    "password-rules": "minlength: 8; maxlength: 32; required: lower; required: upper; required: digit;"
  },
  "hkbea.com": {
    "password-rules": "minlength: 8; maxlength: 12; required: lower; required: upper; required: digit;"
  },
  "hkexpress.com": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; required: special;"
  },
  "hotels.com": {
    "password-rules": "minlength: 6; maxlength: 20; required: digit; allowed: lower, upper, [@$!#()&^*%];"
  },
  "hotwire.com": {
    "password-rules": "minlength: 6; maxlength: 30; allowed: lower, upper, digit, [-~!@#$%^&*_+=`|(){}[:;\"'<>,.?]];"
  },
  "hrblock.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [$#%!];"
  },
  "hsbc.com.hk": {
    "password-rules": "minlength: 6; maxlength: 30; required: lower; required: upper; required: digit; allowed: ['.@_];"
  },
  "hsbc.com.my": {
    "password-rules": "minlength: 8; maxlength: 30; required: lower, upper; required: digit; allowed: [-!$*.=?@_'];"
  },
  "hypovereinsbank.de": {
    "password-rules": "minlength: 6; maxlength: 10; required: lower, upper, digit; allowed: [!\"#$%&()*+:;<=>?@[{}~]];"
  },
  "hyresbostader.se": {
    "password-rules": "minlength: 6; maxlength: 20; required: lower, upper; required: digit;"
  },
  "id.sonyentertainmentnetwork.com": {
    "password-rules": "minlength: 8; maxlength: 30; required: lower, upper; required: digit; allowed: [-!@#^&*=+;:];"
  },
  "identitytheft.gov": {
    "password-rules": "allowed: lower, upper, digit, [!#%&*@^];"
  },
  "idestination.info": {
    "password-rules": "maxlength: 15;"
  },
  "impots.gouv.fr": {
    "password-rules": "minlength: 12; maxlength: 128; required: lower; required: digit; allowed: [-!#$%&*+/=?^_'.{|}];"
  },
  "indochino.com": {
    "password-rules": "minlength: 6; maxlength: 15; required: upper; required: digit; allowed: lower, special;"
  },
  "internationalsos.com": {
    "password-rules": "required: lower; required: upper; required: digit; required: [@#$%^&+=_];"
  },
  "irctc.co.in": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; required: [!@#$%^&*()+];"
  },
  "irs.gov": {
    "password-rules": "minlength: 8; maxlength: 32; required: lower; required: upper; required: digit; required: [!#$%&*@];"
  },
  "jal.co.jp": {
    "password-rules": "minlength: 8; maxlength: 16;"
  },
  "japanpost.jp": {
    "password-rules": "minlength: 8; maxlength: 16; required: digit; required: upper,lower;"
  },
  "jordancu-onlinebanking.org": {
    "password-rules": "minlength: 6; maxlength: 32; allowed: upper, lower, digit,[-!\"#$%&'()*+,.:;<=>?@[^_`{|}~]];"
  },
  "keldoc.com": {
    "password-rules": "minlength: 12; required: lower; required: upper; required: digit; required: [!@#$%^&*];"
  },
  "key.harvard.edu": {
    "password-rules": "minlength: 10; maxlength: 100; required: lower; required: upper; required: digit; allowed: [-@_#!&$`%*+()./,;~:{}|?>=<^[']];"
  },
  "kfc.ca": {
    "password-rules": "minlength: 6; maxlength: 15; required: lower; required: upper; required: digit; required: [!@#$%&?*];"
  },
  "klm.com": {
    "password-rules": "minlength: 8; maxlength: 12;"
  },
  "la-z-boy.com": {
    "password-rules": "minlength: 6; maxlength: 15; required: lower, upper; required: digit;"
  },
  "ladwp.com": {
    "password-rules": "minlength: 8; maxlength: 20; required: digit; allowed: lower, upper;"
  },
  "launtel.net.au": {
    "password-rules": "minlength: 8; required: digit; required: digit; allowed: lower, upper;"
  },
  "leetchi.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&()*+,./:;<>?@\"_];"
  },
  "lg.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: [-!#$%&'()*+,.:;=?@[^_{|}~]];"
  },
  "live.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; allowed: [-@_#!&$`%*+()./,;~:{}|?>=<^'[]];"
  },
  "lloydsbank.co.uk": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: digit; allowed: upper;"
  },
  "lowes.com": {
    "password-rules": "minlength: 8; maxlength: 12; required: lower, upper; required: digit;"
  },
  "loyalty.accor.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&=@];"
  },
  "lsacsso.b2clogin.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit, [-!#$%&*?@^_];"
  },
  "lufthansa.com": {
    "password-rules": "minlength: 8; maxlength: 32; required: lower; required: upper; required: digit; required: [!#$%&()*+,./:;<>?@\"_];"
  },
  "macys.com": {
    "password-rules": "minlength: 7; maxlength: 16; allowed: lower, upper, digit, [~!@#$%^&*+`(){}[:;\"'<>?]];"
  },
  "mailbox.org": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; allowed: [-!$\"%&/()=*+#.,;:@?{}[]];"
  },
  "makemytrip.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [@$!%*#?&];"
  },
  "marriott.com": {
    "password-rules": "minlength: 8; maxlength: 20; required: lower; required: upper; required: digit; allowed: [$!#&@?%=];"
  },
  "maybank2u.com.my": {
    "password-rules": "minlength: 8; maxlength: 12; max-consecutive: 2; required: lower; required: upper; required: digit; required: [-~!@#$%^&*_+=`|(){}[:;\"'<>,.?];"
  },
  "medicare.gov": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [@!$%^*()];"
  },
  "metlife.com": {
    "password-rules": "minlength: 6; maxlength: 20;"
  },
  "microsoft.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: special;"
  },
  "minecraft.com": {
    "password-rules": "minlength: 8; required: lower, upper; required: digit; allowed: ascii-printable;"
  },
  "mintmobile.com": {
    "password-rules": "minlength: 8; maxlength: 20; required: lower; required: upper; required: digit; required: special; allowed: [!#$%&()*+:;=@[^_`{}~]];"
  },
  "mlb.com": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; allowed: [!\"#$%&'()*+,./:;<=>?[\\^_`{|}~]];"
  },
  "mpv.tickets.com": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit;"
  },
  "museumofflight.org": {
    "password-rules": "minlength: 8; maxlength: 15;"
  },
  "my.konami.net": {
    "password-rules": "minlength: 8; maxlength: 32; required: lower; required: upper; required: digit;"
  },
  "myaccess.dmdc.osd.mil": {
    "password-rules": "minlength: 9; maxlength: 20; required: lower; required: upper; required: digit; allowed: [-@_#!&$`%*+()./,;~:{}|?>=<^'[]];"
  },
  "mygoodtogo.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower, upper, digit;"
  },
  "myhealthrecord.com": {
    "password-rules": "minlength: 8; maxlength: 20; allowed: lower, upper, digit, [_.!$*=];"
  },
  "mysubaru.com": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; allowed: [!#$%()*+,./:;=?@\\^`~];"
  },
  "naver.com": {
    "password-rules": "minlength: 6; maxlength: 16;"
  },
  "nelnet.net": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit, [!@#$&*];"
  },
  "netflix.com": {
    "password-rules": "minlength: 4; maxlength: 60; required: lower, upper, digit; allowed: special;"
  },
  "netgear.com": {
    "password-rules": "minlength: 6; maxlength: 128; allowed: lower, upper, digit, [!@#$%^&*()];"
  },
  "nowinstock.net": {
    "password-rules": "minlength: 6; maxlength: 20; allowed: lower, upper, digit;"
  },
  "order.wendys.com": {
    "password-rules": "minlength: 6; maxlength: 20; required: lower; required: upper; required: digit; allowed: [!#$%&()*+/=?^_{}];"
  },
  "ototoy.jp": {
    "password-rules": "minlength: 8; allowed: upper,lower,digit,[- .=_];"
  },
  "packageconciergeadmin.com": {
    "password-rules": "minlength: 4; maxlength: 4; allowed: digit;"
  },
  "paypal.com": {
    "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 3; required: lower, upper; required: digit, [!@#$%^&*()];"
  },
  "payvgm.youraccountadvantage.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: special;"
  },
  "pilotflyingj.com": {
    "password-rules": "minlength: 7; required: digit; allowed: lower, upper;"
  },
  "pixnet.cc": {
    "password-rules": "minlength: 4; maxlength: 16; allowed: lower, upper;"
  },
  "planetary.org": {
    "password-rules": "minlength: 5; maxlength: 20; required: lower; required: upper; required: digit; allowed: ascii-printable;"
  },
  "plazapremiumlounge.com": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; allowed: [!#$%&*,@^];"
  },
  "portal.edd.ca.gov": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&()*@^];"
  },
  "portals.emblemhealth.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&'()*+,./:;<>?@\\^_`{|}~[]];"
  },
  "portlandgeneral.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: [!#$%&*?@];"
  },
  "poste.it": {
    "password-rules": "minlength: 8; maxlength: 16; max-consecutive: 2; required: lower; required: upper; required: digit; required: special;"
  },
  "posteo.de": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit, [-~!#$%&_+=|(){}[:;\"’<>,.? ]];"
  },
  "powells.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [\"!@#$%^&*(){}[]];"
  },
  "preferredhotels.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&()*+@^_];"
  },
  "premier.ticketek.com.au": {
    "password-rules": "minlength: 6; maxlength: 16;"
  },
  "premierinn.com": {
    "password-rules": "minlength: 8; required: upper; required: digit; allowed: lower;"
  },
  "prepaid.bankofamerica.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [!@#$%^&*()+~{}'\";:<>?];"
  },
  "prestocard.ca": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit,[!\"#$%&'()*+,<>?@];"
  },
  "propelfuels.com": {
    "password-rules": "minlength: 6; maxlength: 16;"
  },
  "qdosstatusreview.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&@^];"
  },
  "questdiagnostics.com": {
    "password-rules": "minlength: 8; maxlength: 30; required: upper, lower; required: digit, [!#$%&()*+<>?@^_~];"
  },
  "rejsekort.dk": {
    "password-rules": "minlength: 7; maxlength: 15; required: lower; required: upper; required: digit;"
  },
  "renaud-bray.com": {
    "password-rules": "minlength: 8; maxlength: 38; allowed: upper,lower,digit;"
  },
  "ring.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!@#$%^&*<>?];"
  },
  "riteaid.com": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit;"
  },
  "robinhood.com": {
    "password-rules": "minlength: 10;"
  },
  "rogers.com": {
    "password-rules": "minlength: 8; required: lower, upper; required: digit; required: [!@#$];"
  },
  "ruc.dk": {
    "password-rules": "minlength: 6; maxlength: 8; required: lower, upper; required: [-!#%&(){}*+;%/<=>?_];"
  },
  "runescape.com": {
    "password-rules": "minlength: 5; maxlength: 20; required: lower; required: upper; required: digit;"
  },
  "ruten.com.tw": {
    "password-rules": "minlength: 6; maxlength: 15; required: lower, upper;"
  },
  "salslimo.com": {
    "password-rules": "minlength: 8; maxlength: 50; required: upper; required: lower; required: digit; required: [!@#$&*];"
  },
  "santahelenasaude.com.br": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; required: [-!@#$%&*_+=<>];"
  },
  "santander.de": {
    "password-rules": "minlength: 8; maxlength: 12; required: lower, upper; required: digit; allowed: [-!#$%&'()*,.:;=?^{}];"
  },
  "sbisec.co.jp": {
    "password-rules": "minlength: 10; maxlength: 20; allowed: upper,lower,digit;"
  },
  "secure-arborfcu.org": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; required: [!#$%&'()+,.:?@[_`~]];"
  },
  "secure.orclinic.com": {
    "password-rules": "minlength: 6; maxlength: 15; required: lower; required: digit; allowed: ascii-printable;"
  },
  "secure.snnow.ca": {
    "password-rules": "minlength: 7; maxlength: 16; required: digit; allowed: lower, upper;"
  },
  "secure.wa.aaa.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: ascii-printable;"
  },
  "sephora.com": {
    "password-rules": "minlength: 6; maxlength: 12;"
  },
  "serviziconsolari.esteri.it": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: special;"
  },
  "servizioelettriconazionale.it": {
    "password-rules": "minlength: 8; maxlength: 20; required: lower; required: upper; required: digit; required: [!#$%&*?@^_~];"
  },
  "sfwater.org": {
    "password-rules": "minlength: 10; maxlength: 30; required: digit; allowed: lower, upper, [!@#$%*()_+^}{:;?.];"
  },
  "signin.ea.com": {
    "password-rules": "minlength: 8; maxlength: 64; required: lower, upper; required: digit; allowed: [-!@#^&*=+;:];"
  },
  "southwest.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: upper; required: digit; allowed: lower, [!@#$%^*(),.;:/\\];"
  },
  "speedway.com": {
    "password-rules": "minlength: 4; maxlength: 8; required: digit;"
  },
  "spirit.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [!@#$%^&*()];"
  },
  "splunk.com": {
    "password-rules": "minlength: 8; maxlength: 64; required: lower; required: upper; required: digit; required: [-!@#$%&*_+=<>];"
  },
  "ssa.gov": {
    "password-rules": "required: lower; required: upper; required: digit; required: [~!@#$%^&*];"
  },
  "store.nvidia.com": {
    "password-rules": "minlength: 8; maxlength: 32; required: lower; required: upper; required: digit; required: [-!@#$%^*~:;&><[{}|_+=?]];"
  },
  "store.steampowered.com": {
    "password-rules": "minlength: 6; required: lower; required: upper; required: digit; allowed: [~!@#$%^&*];"
  },
  "successfactors.eu": {
    "password-rules": "minlength: 8; maxlength: 18; required: lower; required: upper; required: digit,[-!\"#$%&'()*+,.:;<=>?@[^_`{|}~]];"
  },
  "sulamericaseguros.com.br": {
    "password-rules": "minlength: 6; maxlength: 6;"
  },
  "sunlife.com": {
    "password-rules": "minlength: 8; maxlength: 10; required: digit; required: lower, upper;"
  },
  "t-mobile.net": {
    "password-rules": "minlength: 8; maxlength: 16;"
  },
  "target.com": {
    "password-rules": "minlength: 8; maxlength: 20; required: lower, upper; required: digit, [-!\"#$%&'()*+,./:;=?@[\\^_`{|}~];"
  },
  "telekom-dienste.de": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [#$%&()*+,./<=>?@_{|}~];"
  },
  "thameswater.co.uk": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: special;"
  },
  "tix.soundrink.com": {
    "password-rules": "minlength: 6; maxlength: 16;"
  },
  "training.confluent.io": {
    "password-rules": "minlength: 6; maxlength: 16; required: lower; required: upper; required: digit; allowed: [!#$%*@^_~];"
  },
  "twitter.com": {
    "password-rules": "minlength: 8;"
  },
  "ubisoft.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [-]; required: [!@#$%^&*()+];"
  },
  "udel.edu": {
    "password-rules": "minlength: 12; maxlength: 30; required: lower; required: upper; required: digit; required: [!@#$%^&*()+];"
  },
  "user.ornl.gov": {
    "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower, upper; required: digit; allowed: [!#$%./_];"
  },
  "usps.com": {
    "password-rules": "minlength: 8; maxlength: 50; max-consecutive: 2; required: lower; required: upper; required: digit; allowed: [-!\"#&'()+,./?@];"
  },
  "vanguard.com": {
    "password-rules": "minlength: 6; maxlength: 20; required: lower; required: upper; required: digit; required: digit;"
  },
  "vanguardinvestor.co.uk": {
    "password-rules": "minlength: 8; maxlength: 50; required: lower; required: upper; required: digit; required: digit;"
  },
  "ventrachicago.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit, [!@#$%^];"
  },
  "verizonwireless.com": {
    "password-rules": "minlength: 8; maxlength: 20; required: lower, upper; required: digit; allowed: unicode;"
  },
  "vetsfirstchoice.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; allowed: [?!@$%^+=&];"
  },
  "virginmobile.ca": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$@];"
  },
  "visa.com": {
    "password-rules": "minlength: 6; maxlength: 32;"
  },
  "visabenefits-auth.axa-assistance.us": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!\"#$%&()*,.:<>?@^{|}];"
  },
  "vivo.com.br": {
    "password-rules": "maxlength: 6; max-consecutive: 3; allowed: digit;"
  },
  "walkhighlands.co.uk": {
    "password-rules": "minlength: 9; maxlength: 15; required: lower; required: upper; required: digit; allowed: special;"
  },
  "walmart.com": {
    "password-rules": "allowed: lower, upper, digit, [-(~!@#$%^&*_+=`|(){}[:;\"'<>,.?]];"
  },
  "waze.com": {
    "password-rules": "minlength: 8; maxlength: 64; required: lower, upper, digit;"
  },
  "wccls.org": {
    "password-rules": "minlength: 4; maxlength: 16; allowed: lower, upper, digit;"
  },
  "web.de": {
    "password-rules": "minlength: 8; maxlength: 40; allowed: lower, upper, digit, [-<=>~!|()@#{}$%,.?^'&*_+`:;\"[]];"
  },
  "wegmans.com": {
    "password-rules": "minlength: 8; required: digit; required: upper,lower; required: [!#$%&*+=?@^];"
  },
  "weibo.com": {
    "password-rules": "minlength: 6; maxlength: 16;"
  },
  "wsj.com": {
    "password-rules": "minlength: 5; maxlength: 15; required: digit; allowed: lower, upper, [-~!@#$^*_=`|(){}[:;\"'<>,.?]];"
  },
  "xfinity.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower, upper; required: digit;"
  },
  "xvoucher.com": {
    "password-rules": "minlength: 11; required: upper; required: digit; required: [!@#$%&_];"
  },
  "yatra.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&'()+,.:?@[_`~]];"
  },
  "zara.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit;"
  },
  "zdf.de": {
    "password-rules": "minlength: 8; required: upper; required: digit; allowed: lower, special;"
  },
  "zoom.us": {
    "password-rules": "minlength: 8; maxlength: 32; max-consecutive: 6; required: lower; required: upper; required: digit;"
  }
}
},{}],10:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.createDevice = createDevice;

var _config = require("./config");

var _AndroidInterface = require("./DeviceInterface/AndroidInterface");

var _ExtensionInterface = require("./DeviceInterface/ExtensionInterface");

var _AppleDeviceInterface = require("./DeviceInterface/AppleDeviceInterface");

var _AppleOverlayDeviceInterface = require("./DeviceInterface/AppleOverlayDeviceInterface");

var _transports = require("./deviceApiCalls/transports/transports");

var _deviceApi = require("../packages/device-api");

var _Settings = require("./Settings");

function createDevice() {
  const globalConfig = (0, _config.createGlobalConfig)();
  const transport = (0, _transports.createTransport)(globalConfig);
  /**
   * A wrapper around transports to assist in debugging/integrations
   * @type {import("../packages/device-api").DeviceApiTransport}
   */

  const loggingTransport = {
    async send(deviceApiCall) {
      console.log('[outgoing]', deviceApiCall.method, 'id:', deviceApiCall.id, JSON.stringify(deviceApiCall.params || null));
      const result = await transport.send(deviceApiCall);
      console.log('[incoming]', deviceApiCall.method, 'id:', deviceApiCall.id, JSON.stringify(result || null));
      return result;
    }

  }; // Create the DeviceAPI + Setting

  let deviceApi = new _deviceApi.DeviceApi(globalConfig.isDDGTestMode ? loggingTransport : transport);
  const settings = new _Settings.Settings(globalConfig, deviceApi);

  if (globalConfig.isDDGApp) {
    if (globalConfig.isAndroid) {
      return new _AndroidInterface.AndroidInterface(globalConfig, deviceApi, settings);
    }

    if (globalConfig.isTopFrame) {
      return new _AppleOverlayDeviceInterface.AppleOverlayDeviceInterface(globalConfig, deviceApi, settings);
    }

    return new _AppleDeviceInterface.AppleDeviceInterface(globalConfig, deviceApi, settings);
  }

  return new _ExtensionInterface.ExtensionInterface(globalConfig, deviceApi, settings);
}

},{"../packages/device-api":2,"./DeviceInterface/AndroidInterface":11,"./DeviceInterface/AppleDeviceInterface":12,"./DeviceInterface/AppleOverlayDeviceInterface":13,"./DeviceInterface/ExtensionInterface":14,"./Settings":34,"./config":48,"./deviceApiCalls/transports/transports":56}],11:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.AndroidInterface = void 0;

var _InterfacePrototype = _interopRequireDefault(require("./InterfacePrototype.js"));

var _autofillUtils = require("../autofill-utils");

var _NativeUIController = require("../UI/controllers/NativeUIController.js");

var _appleUtils = require("@duckduckgo/content-scope-scripts/src/apple-utils");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

class AndroidInterface extends _InterfacePrototype.default {
  async isEnabled() {
    return (0, _autofillUtils.autofillEnabled)(this.globalConfig, _appleUtils.processConfig);
  }

  async getAlias() {
    const {
      alias
    } = await (0, _autofillUtils.sendAndWaitForAnswer)(() => {
      return window.EmailInterface.showTooltip();
    }, 'getAliasResponse');
    return alias;
  }
  /**
   * @override
   */


  createUIController() {
    return new _NativeUIController.NativeUIController({
      onPointerDown: event => this._onPointerDown(event)
    });
  }
  /**
   * @deprecated use `this.settings.availableInputTypes.email` in the future
   * @returns {boolean}
   */


  isDeviceSignedIn() {
    var _this$globalConfig$av;

    // on DDG domains, always check via `window.EmailInterface.isSignedIn()`
    if (this.globalConfig.isDDGDomain) {
      return window.EmailInterface.isSignedIn() === 'true';
    } // on non-DDG domains, where `availableInputTypes.email` is present, use it


    if (typeof ((_this$globalConfig$av = this.globalConfig.availableInputTypes) === null || _this$globalConfig$av === void 0 ? void 0 : _this$globalConfig$av.email) === 'boolean') {
      return this.globalConfig.availableInputTypes.email;
    } // ...on other domains we assume true because the script wouldn't exist otherwise


    return true;
  }

  async setupAutofill() {}

  postInit() {
    const cleanup = this.scanner.init();
    this.addLogoutListener(cleanup);
  }
  /**
   * Used by the email web app
   * Settings page displays data of the logged in user data
   */


  getUserData() {
    let userData = null;

    try {
      userData = JSON.parse(window.EmailInterface.getUserData());
    } catch (e) {
      if (this.globalConfig.isDDGTestMode) {
        console.error(e);
      }
    }

    return Promise.resolve(userData);
  }
  /**
   * Used by the email web app
   * Device capabilities determine which functionality is available to the user
   */


  getEmailProtectionCapabilities() {
    let deviceCapabilities = null;

    try {
      deviceCapabilities = JSON.parse(window.EmailInterface.getDeviceCapabilities());
    } catch (e) {
      if (this.globalConfig.isDDGTestMode) {
        console.error(e);
      }
    }

    return Promise.resolve(deviceCapabilities);
  }

  storeUserData(_ref) {
    let {
      addUserData: {
        token,
        userName,
        cohort
      }
    } = _ref;
    return window.EmailInterface.storeCredentials(token, userName, cohort);
  }
  /**
    * Used by the email web app
    * Provides functionality to log the user out
    */


  removeUserData() {
    try {
      return window.EmailInterface.removeCredentials();
    } catch (e) {
      if (this.globalConfig.isDDGTestMode) {
        console.error(e);
      }
    }
  }

  addLogoutListener(handler) {
    // Only deal with logging out if we're in the email web app
    if (!this.globalConfig.isDDGDomain) return;
    window.addEventListener('message', e => {
      if (this.globalConfig.isDDGDomain && e.data.emailProtectionSignedOut) {
        handler();
      }
    });
  }

}

exports.AndroidInterface = AndroidInterface;

},{"../UI/controllers/NativeUIController.js":39,"../autofill-utils":46,"./InterfacePrototype.js":15,"@duckduckgo/content-scope-scripts/src/apple-utils":1}],12:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.AppleDeviceInterface = void 0;

var _InterfacePrototype = _interopRequireDefault(require("./InterfacePrototype.js"));

var _autofillUtils = require("../autofill-utils");

var _appleUtils = require("@duckduckgo/content-scope-scripts/src/apple-utils");

var _HTMLTooltip = require("../UI/HTMLTooltip");

var _HTMLTooltipUIController = require("../UI/controllers/HTMLTooltipUIController");

var _OverlayUIController = require("../UI/controllers/OverlayUIController");

var _deviceApi = require("../../packages/device-api");

var _additionalDeviceApiCalls = require("../deviceApiCalls/additionalDeviceApiCalls");

var _NativeUIController = require("../UI/controllers/NativeUIController");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

class AppleDeviceInterface extends _InterfacePrototype.default {
  constructor() {
    super(...arguments);

    _defineProperty(this, "initialSetupDelayMs", 300);

    _defineProperty(this, "pollingTimeout", null);
  }

  async isEnabled() {
    return (0, _autofillUtils.autofillEnabled)(this.globalConfig, _appleUtils.processConfig);
  }
  /**
   * The default functionality of this class is to operate as an 'overlay controller' -
   * which means it's purpose is to message the native layer about when to open/close the overlay.
   *
   * There is an additional use-case though, when running on older macOS versions, we just display the
   * HTMLTooltip in-page (like the extension does). This is why the `!this.globalConfig.supportsTopFrame`
   * check exists below - if we know we don't support the overlay, we fall back to in-page.
   *
   * @override
   * @returns {import("../UI/controllers/UIController.js").UIController}
   */


  createUIController() {
    var _this$globalConfig$us, _this$globalConfig$us2;

    if (((_this$globalConfig$us = this.globalConfig.userPreferences) === null || _this$globalConfig$us === void 0 ? void 0 : (_this$globalConfig$us2 = _this$globalConfig$us.platform) === null || _this$globalConfig$us2 === void 0 ? void 0 : _this$globalConfig$us2.name) === 'ios') {
      return new _NativeUIController.NativeUIController({
        onPointerDown: event => this._onPointerDown(event)
      });
    }

    if (!this.globalConfig.supportsTopFrame) {
      const options = { ..._HTMLTooltip.defaultOptions,
        testMode: this.isTestMode()
      };
      return new _HTMLTooltipUIController.HTMLTooltipUIController({
        device: this,
        tooltipKind: 'modern',
        onPointerDown: e => this._onPointerDown(e)
      }, options);
    }
    /**
     * If we get here, we're just a controller for an overlay
     */


    return new _OverlayUIController.OverlayUIController({
      remove: async () => this._closeAutofillParent(),
      show: async details => this._show(details),
      onPointerDown: event => this._onPointerDown(event)
    });
  }
  /**
   * For now, this could be running
   *  1) on iOS
   *  2) on macOS + Overlay
   *  3) on macOS + in-page HTMLTooltip
   *
   * @override
   * @returns {Promise<void>}
   */


  async setupAutofill() {
    if (this.globalConfig.isApp) {
      await this._getAutofillInitData();
    }

    const signedIn = await this._checkDeviceSignedIn();

    if (signedIn) {
      if (this.globalConfig.isApp) {
        await this.getAddresses();
      }
    }
  }

  async postInit() {
    if (this.isDeviceSignedIn()) {
      this.scanner.forms.forEach(form => form.redecorateAllInputs());
    }

    const cleanup = this.scanner.init();
    this.addLogoutListener(cleanup);
  }
  /**
   * Used by the email web app
   * Settings page displays data of the logged in user data
   */


  getUserData() {
    return this.deviceApi.request((0, _deviceApi.createRequest)('emailHandlerGetUserData'));
  }
  /**
   * Used by the email web app
   * Device capabilities determine which functionality is available to the user
   */


  getEmailProtectionCapabilities() {
    return this.deviceApi.request((0, _deviceApi.createRequest)('emailHandlerGetCapabilities'));
  }
  /**
   */


  async getSelectedCredentials() {
    return this.deviceApi.request((0, _deviceApi.createRequest)('getSelectedCredentials'));
  }
  /**
   * @param {import('../UI/controllers/OverlayUIController.js').ShowAutofillParentRequest} parentArgs
   */


  async _showAutofillParent(parentArgs) {
    return this.deviceApi.notify((0, _deviceApi.createNotification)('showAutofillParent', parentArgs));
  }
  /**
   * @returns {Promise<any>}
   */


  async _closeAutofillParent() {
    return this.deviceApi.notify((0, _deviceApi.createNotification)('closeAutofillParent', {}));
  }
  /**
   * @param {import('../UI/controllers/OverlayUIController.js').ShowAutofillParentRequest} details
   */


  async _show(details) {
    await this._showAutofillParent(details);

    this._listenForSelectedCredential().then(response => {
      if (!response) {
        return;
      }

      this.activeFormSelectedDetail(response.data, response.configType);
    }).catch(e => {
      console.error('unknown error', e);
    });
  }

  async getAddresses() {
    if (!this.globalConfig.isApp) return this.getAlias();
    const {
      addresses
    } = await this.deviceApi.request((0, _deviceApi.createRequest)('emailHandlerGetAddresses'));
    this.storeLocalAddresses(addresses);
    return addresses;
  }

  async refreshAlias() {
    await this.deviceApi.notify((0, _deviceApi.createNotification)('emailHandlerRefreshAlias')); // On macOS we also update the addresses stored locally

    if (this.globalConfig.isApp) this.getAddresses();
  }

  async _checkDeviceSignedIn() {
    const {
      isAppSignedIn
    } = await this.deviceApi.request((0, _deviceApi.createRequest)('emailHandlerCheckAppSignedInStatus'));

    this.isDeviceSignedIn = () => !!isAppSignedIn;

    return !!isAppSignedIn;
  }

  storeUserData(_ref) {
    let {
      addUserData: {
        token,
        userName,
        cohort
      }
    } = _ref;
    return this.deviceApi.notify((0, _deviceApi.createNotification)('emailHandlerStoreToken', {
      token,
      username: userName,
      cohort
    }));
  }
  /**
   * Used by the email web app
   * Provides functionality to log the user out
   */


  removeUserData() {
    this.deviceApi.notify((0, _deviceApi.createNotification)('emailHandlerRemoveToken'));
  }
  /**
   * PM endpoints
   */

  /**
   * Sends credentials to the native layer
   * @param {{username: string, password: string}} credentials
   */


  storeCredentials(credentials) {
    return this.deviceApi.notify((0, _deviceApi.createNotification)('pmHandlerStoreCredentials', credentials));
  }
  /**
   * Sends form data to the native layer
   * @deprecated should use the base implementation once available on Apple devices (instead of this override)
   * @param {DataStorageObject} data
   */


  storeFormData(data) {
    this.deviceApi.notify((0, _deviceApi.createNotification)('pmHandlerStoreData', data));
  }
  /**
   * Gets the init data from the device
   * @returns {APIResponse<PMData>}
   */


  async _getAutofillInitData() {
    const response = await this.deviceApi.request((0, _deviceApi.createRequest)('pmHandlerGetAutofillInitData'));
    this.storeLocalData(response.success);
    return response;
  }
  /**
   * Gets credentials ready for autofill
   * @param {Number} id - the credential id
   * @returns {APIResponseSingle<CredentialsObject>}
   */


  getAutofillCredentials(id) {
    return this.deviceApi.request((0, _deviceApi.createRequest)('pmHandlerGetAutofillCredentials', {
      id
    }));
  }
  /**
   * Opens the native UI for managing passwords
   */


  openManagePasswords() {
    return this.deviceApi.notify((0, _deviceApi.createNotification)('pmHandlerOpenManagePasswords'));
  }
  /**
   * Opens the native UI for managing identities
   */


  openManageIdentities() {
    return this.deviceApi.notify((0, _deviceApi.createNotification)('pmHandlerOpenManageIdentities'));
  }
  /**
   * Opens the native UI for managing credit cards
   */


  openManageCreditCards() {
    return this.deviceApi.notify((0, _deviceApi.createNotification)('pmHandlerOpenManageCreditCards'));
  }
  /**
   * Gets a single identity obj once the user requests it
   * @param {Number} id
   * @returns {Promise<{success: IdentityObject|undefined}>}
   */


  getAutofillIdentity(id) {
    const identity = this.getLocalIdentities().find(_ref2 => {
      let {
        id: identityId
      } = _ref2;
      return "".concat(identityId) === "".concat(id);
    });
    return Promise.resolve({
      success: identity
    });
  }
  /**
   * Gets a single complete credit card obj once the user requests it
   * @param {Number} id
   * @returns {APIResponse<CreditCardObject>}
   */


  getAutofillCreditCard(id) {
    return this.deviceApi.request((0, _deviceApi.createRequest)('pmHandlerGetCreditCard', {
      id
    }));
  }

  async getCurrentInputType() {
    const {
      inputType
    } = this.getTopContextData() || {};
    return inputType || 'unknown';
  }
  /**
   * @returns {Promise<string>}
   */


  async getAlias() {
    const {
      alias
    } = await this.deviceApi.request(new _additionalDeviceApiCalls.GetAlias({
      requiresUserPermission: !this.globalConfig.isApp,
      shouldConsumeAliasIfProvided: !this.globalConfig.isApp
    }));
    return (0, _autofillUtils.formatDuckAddress)(alias);
  }

  addLogoutListener(handler) {
    // Only deal with logging out if we're in the email web app
    if (!this.globalConfig.isDDGDomain) return;
    window.addEventListener('message', e => {
      if (this.globalConfig.isDDGDomain && e.data.emailProtectionSignedOut) {
        handler();
      }
    });
  }
  /** @type {any} */


  /**
   * Poll the native listener until the user has selected a credential.
   * Message return types are:
   * - 'stop' is returned whenever the message sent doesn't match the native last opened tooltip.
   *     - This also is triggered when the close event is called and prevents any edge case continued polling.
   * - 'ok' is when the user has selected a credential and the value can be injected into the page.
   * - 'none' is when the tooltip is open in the native window however hasn't been entered.
   * @returns {Promise<{data:IdentityObject|CreditCardObject|CredentialsObject, configType: string} | null>}
   */
  async _listenForSelectedCredential() {
    return new Promise(resolve => {
      // Prevent two timeouts from happening
      // @ts-ignore
      const poll = async () => {
        clearTimeout(this.pollingTimeout);
        const response = await this.getSelectedCredentials();

        switch (response.type) {
          case 'none':
            // Parent hasn't got a selected credential yet
            // @ts-ignore
            this.pollingTimeout = setTimeout(() => {
              poll();
            }, 100);
            return;

          case 'ok':
            {
              return resolve({
                data: response.data,
                configType: response.configType
              });
            }

          case 'stop':
            // Parent wants us to stop polling
            resolve(null);
            break;
        }
      };

      poll();
    });
  }

}

exports.AppleDeviceInterface = AppleDeviceInterface;

},{"../../packages/device-api":2,"../UI/HTMLTooltip":37,"../UI/controllers/HTMLTooltipUIController":38,"../UI/controllers/NativeUIController":39,"../UI/controllers/OverlayUIController":40,"../autofill-utils":46,"../deviceApiCalls/additionalDeviceApiCalls":52,"./InterfacePrototype.js":15,"@duckduckgo/content-scope-scripts/src/apple-utils":1}],13:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.AppleOverlayDeviceInterface = void 0;

var _AppleDeviceInterface = require("./AppleDeviceInterface");

var _styles = require("../UI/styles/styles");

var _HTMLTooltipUIController = require("../UI/controllers/HTMLTooltipUIController");

var _deviceApi = require("../../packages/device-api");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * This subclass is designed to separate code that *only* runs inside the
 * Overlay into a single place.
 *
 * It will only run inside the macOS overlay, therefor all code here
 * can be viewed as *not* executing within a regular page context.
 */
class AppleOverlayDeviceInterface extends _AppleDeviceInterface.AppleDeviceInterface {
  constructor() {
    super(...arguments);

    _defineProperty(this, "stripCredentials", false);
  }

  /**
   * Because we're running inside the Overlay, we always create the HTML
   * Tooltip controller.
   *
   * @override
   * @returns {import("../UI/controllers/UIController.js").UIController}
   */
  createUIController() {
    /** @type {import('../UI/controllers/HTMLTooltipUIController').HTMLTooltipControllerOptions} */
    const controllerOptions = {
      tooltipKind:
      /** @type {const} */
      'modern',
      device: this
    };
    /** @type {import('../UI/HTMLTooltip').HTMLTooltipOptions} */

    const tooltipOptions = {
      wrapperClass: 'top-autofill',
      tooltipPositionClass: () => '.wrapper { transform: none; }',
      css: "<style>".concat(_styles.CSS_STYLES, "</style>"),
      setSize: details => this._setSize(details),
      testMode: this.isTestMode(),
      remove: () => {
        /** noop - the overlay does not close itself */
      }
    };
    return new _HTMLTooltipUIController.HTMLTooltipUIController(controllerOptions, tooltipOptions);
  }
  /**
   * Since we're running inside the Overlay we can limit what happens here to
   * be only things that are needed to power the HTML Tooltip
   *
   * @override
   * @returns {Promise<void>}
   */


  async setupAutofill() {
    await this._getAutofillInitData();
    const signedIn = await this._checkDeviceSignedIn();

    if (signedIn) {
      await this.getAddresses();
    }

    this._setupTopFrame();

    this._listenForCustomMouseEvent();
  }

  _setupTopFrame() {
    var _this$uiController$cr, _this$uiController;

    const topContextData = this.getTopContextData();
    if (!topContextData) throw new Error('unreachable, topContextData should be available'); // Provide dummy values, they're not used

    const getPosition = () => {
      return {
        x: 0,
        y: 0,
        height: 50,
        width: 50
      };
    }; // Create the tooltip, and set it as active


    const tooltip = (_this$uiController$cr = (_this$uiController = this.uiController).createTooltip) === null || _this$uiController$cr === void 0 ? void 0 : _this$uiController$cr.call(_this$uiController, getPosition, topContextData);

    if (tooltip) {
      var _this$uiController$se, _this$uiController2;

      (_this$uiController$se = (_this$uiController2 = this.uiController).setActiveTooltip) === null || _this$uiController$se === void 0 ? void 0 : _this$uiController$se.call(_this$uiController2, tooltip);
    }
  }
  /**
   * The native side will send a custom event 'mouseMove' to indicate
   * that the HTMLTooltip should fake an element being focussed.
   *
   * Note: There's no cleanup required here since the Overlay has a fresh
   * page load every time it's opened.
   */


  _listenForCustomMouseEvent() {
    window.addEventListener('mouseMove', event => {
      var _this$uiController$ge, _this$uiController3;

      const activeTooltip = (_this$uiController$ge = (_this$uiController3 = this.uiController).getActiveTooltip) === null || _this$uiController$ge === void 0 ? void 0 : _this$uiController$ge.call(_this$uiController3);
      activeTooltip === null || activeTooltip === void 0 ? void 0 : activeTooltip.focus(event.detail.x, event.detail.y);
    });
  }
  /**
   * This is overridden in the Overlay, so that instead of trying to fill a form
   * with the selected credentials, we instead send a message to the native
   * side. Once received, the native side will store that selection so that a
   * subsequence call from main webpage can retrieve it via polling.
   *
   * @override
   * @param detailIn
   * @param configType
   * @returns {Promise<void>}
   */


  async selectedDetail(detailIn, configType) {
    let detailsEntries = Object.entries(detailIn).map(_ref => {
      let [key, value] = _ref;
      return [key, String(value)];
    });
    const data = Object.fromEntries(detailsEntries);
    await this.deviceApi.notify((0, _deviceApi.createNotification)('selectedDetail', {
      data,
      configType
    }));
  }
  /**
   * When the HTMLTooltip calls 'setSize', we forward that message to the native layer
   * so that the window that contains the Autofill UI can be set correctly.
   *
   * This is an overlay-only scenario - normally 'setSize' isn't needed (like in the extension)
   * because the HTML element will grow as needed.
   *
   * @param {{height: number, width: number}} details
   */


  async _setSize(details) {
    await this.deviceApi.notify((0, _deviceApi.createNotification)('setSize', details));
  }

}

exports.AppleOverlayDeviceInterface = AppleOverlayDeviceInterface;

},{"../../packages/device-api":2,"../UI/controllers/HTMLTooltipUIController":38,"../UI/styles/styles":43,"./AppleDeviceInterface":12}],14:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.ExtensionInterface = void 0;

var _InterfacePrototype = _interopRequireDefault(require("./InterfacePrototype.js"));

var _autofillUtils = require("../autofill-utils");

var _HTMLTooltipUIController = require("../UI/controllers/HTMLTooltipUIController");

var _HTMLTooltip = require("../UI/HTMLTooltip");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

class ExtensionInterface extends _InterfacePrototype.default {
  /**
   * @override
   */
  createUIController() {
    /** @type {import('../UI/HTMLTooltip.js').HTMLTooltipOptions} */
    const htmlTooltipOptions = { ..._HTMLTooltip.defaultOptions,
      css: "<link rel=\"stylesheet\" href=\"".concat(chrome.runtime.getURL('public/css/autofill.css'), "\" crossOrigin=\"anonymous\">"),
      testMode: this.isTestMode()
    };
    return new _HTMLTooltipUIController.HTMLTooltipUIController({
      tooltipKind: 'legacy',
      device: this
    }, htmlTooltipOptions);
  }

  async isEnabled() {
    return new Promise(resolve => {
      chrome.runtime.sendMessage({
        registeredTempAutofillContentScript: true,
        documentUrl: window.location.href
      }, response => {
        if (response && 'site' in response) {
          resolve((0, _autofillUtils.isAutofillEnabledFromProcessedConfig)(response));
        }
      });
    });
  }

  isDeviceSignedIn() {
    return this.hasLocalAddresses;
  }

  setupAutofill() {
    return this.getAddresses();
  }

  postInit() {
    if (this.hasLocalAddresses) {
      const cleanup = this.scanner.init();
      this.addLogoutListener(cleanup);
    }
  }

  getAddresses() {
    return new Promise(resolve => chrome.runtime.sendMessage({
      getAddresses: true
    }, data => {
      this.storeLocalAddresses(data);
      return resolve(data);
    }));
  }
  /**
   * Used by the email web app
   * Settings page displays data of the logged in user data
   */


  getUserData() {
    return new Promise(resolve => chrome.runtime.sendMessage({
      getUserData: true
    }, data => resolve(data)));
  }
  /**
   * Used by the email web app
   * Device capabilities determine which functionality is available to the user
   */


  getEmailProtectionCapabilities() {
    return new Promise(resolve => chrome.runtime.sendMessage({
      getEmailProtectionCapabilities: true
    }, data => resolve(data)));
  }

  refreshAlias() {
    return chrome.runtime.sendMessage({
      refreshAlias: true
    }, addresses => this.storeLocalAddresses(addresses));
  }

  async trySigningIn() {
    if (this.globalConfig.isDDGDomain) {
      const data = await (0, _autofillUtils.sendAndWaitForAnswer)(_autofillUtils.SIGN_IN_MSG, 'addUserData');
      this.storeUserData(data);
    }
  }
  /**
   * @param {object} message
   * @param {object} message.addUserData
   * @param {string} message.addUserData.token
   * @param {string} message.addUserData.userName
   * @param {string} message.addUserData.cohort
   */


  storeUserData(message) {
    return chrome.runtime.sendMessage(message);
  }
  /**
   * Used by the email web app
   * Provides functionality to log the user out
   */


  removeUserData() {
    return chrome.runtime.sendMessage({
      removeUserData: true
    });
  }

  addDeviceListeners() {
    // Add contextual menu listeners
    let activeEl = null;
    document.addEventListener('contextmenu', e => {
      activeEl = e.target;
    });
    chrome.runtime.onMessage.addListener((message, sender) => {
      if (sender.id !== chrome.runtime.id) return;

      switch (message.type) {
        case 'ddgUserReady':
          this.setupAutofill().then(() => {
            this.refreshSettings().then(() => {
              this.setupSettingsPage({
                shouldLog: true
              }).then(() => {
                return this.postInit();
              });
            });
          });
          break;

        case 'contextualAutofill':
          (0, _autofillUtils.setValue)(activeEl, (0, _autofillUtils.formatDuckAddress)(message.alias), this.globalConfig);
          activeEl.classList.add('ddg-autofilled');
          this.refreshAlias(); // If the user changes the alias, remove the decoration

          activeEl.addEventListener('input', e => e.target.classList.remove('ddg-autofilled'), {
            once: true
          });
          break;

        default:
          break;
      }
    });
  }

  addLogoutListener(handler) {
    // Cleanup on logout events
    chrome.runtime.onMessage.addListener((message, sender) => {
      if (sender.id === chrome.runtime.id && message.type === 'logout') {
        handler();
      }
    });
  }

}

exports.ExtensionInterface = ExtensionInterface;

},{"../UI/HTMLTooltip":37,"../UI/controllers/HTMLTooltipUIController":38,"../autofill-utils":46,"./InterfacePrototype.js":15}],15:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;

var _autofillUtils = require("../autofill-utils");

var _matching = require("../Form/matching");

var _formatters = require("../Form/formatters");

var _listenForFormSubmission = _interopRequireDefault(require("../Form/listenForFormSubmission"));

var _Credentials = require("../InputTypes/Credentials");

var _PasswordGenerator = require("../PasswordGenerator");

var _Scanner = require("../Scanner");

var _config = require("../config");

var _NativeUIController = require("../UI/controllers/NativeUIController");

var _transports = require("../deviceApiCalls/transports/transports");

var _Settings = require("../Settings");

var _deviceApi = require("../../packages/device-api");

var _deviceApiCalls = require("../deviceApiCalls/__generated__/deviceApiCalls");

var _selectorsCss = require("../Form/selectors-css");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

var _addresses = /*#__PURE__*/new WeakMap();

var _data2 = /*#__PURE__*/new WeakMap();

/**
 * @typedef {import('../deviceApiCalls/__generated__/validators-ts').StoreFormData} StoreFormData
 */

/**
 * @implements {GlobalConfigImpl}
 * @implements {FormExtensionPoints}
 * @implements {DeviceExtensionPoints}
 */
class InterfacePrototype {
  /** @type {import("../Form/Form").Form | null} */

  /** @type {import("../UI/HTMLTooltip.js").default | null} */

  /** @type {number} */

  /** @type {PasswordGenerator} */

  /** @type {{privateAddress: string, personalAddress: string}} */

  /** @type {GlobalConfig} */

  /** @type {import('../Scanner').Scanner} */

  /** @type {import("../UI/controllers/UIController.js").UIController} */

  /** @type {import("../../packages/device-api").DeviceApi} */

  /**
   * @param {GlobalConfig} config
   * @param {import("../../packages/device-api").DeviceApi} deviceApi
   * @param {Settings} settings
   */
  constructor(config, deviceApi, settings) {
    _defineProperty(this, "attempts", 0);

    _defineProperty(this, "currentAttached", null);

    _defineProperty(this, "currentTooltip", null);

    _defineProperty(this, "stripCredentials", true);

    _defineProperty(this, "initialSetupDelayMs", 0);

    _defineProperty(this, "passwordGenerator", new _PasswordGenerator.PasswordGenerator());

    _classPrivateFieldInitSpec(this, _addresses, {
      writable: true,
      value: {
        privateAddress: '',
        personalAddress: ''
      }
    });

    _defineProperty(this, "globalConfig", void 0);

    _defineProperty(this, "scanner", void 0);

    _defineProperty(this, "uiController", void 0);

    _defineProperty(this, "deviceApi", void 0);

    _classPrivateFieldInitSpec(this, _data2, {
      writable: true,
      value: {
        credentials: [],
        creditCards: [],
        identities: [],
        topContextData: undefined
      }
    });

    this.globalConfig = config;
    this.deviceApi = deviceApi;
    this.uiController = this.createUIController();
    this.settings = settings;
    this.scanner = (0, _Scanner.createScanner)(this, {
      initialDelay: this.initialSetupDelayMs
    });
  }
  /**
   * Implementors should override this with a UI controller that suits
   * their platform.
   *
   * @returns {import("../UI/controllers/UIController.js").UIController}
   */


  createUIController() {
    return new _NativeUIController.NativeUIController();
  }

  get hasLocalAddresses() {
    var _classPrivateFieldGet2, _classPrivateFieldGet3;

    return !!((_classPrivateFieldGet2 = _classPrivateFieldGet(this, _addresses)) !== null && _classPrivateFieldGet2 !== void 0 && _classPrivateFieldGet2.privateAddress && (_classPrivateFieldGet3 = _classPrivateFieldGet(this, _addresses)) !== null && _classPrivateFieldGet3 !== void 0 && _classPrivateFieldGet3.personalAddress);
  }

  getLocalAddresses() {
    return _classPrivateFieldGet(this, _addresses);
  }

  storeLocalAddresses(addresses) {
    _classPrivateFieldSet(this, _addresses, addresses); // When we get new duck addresses, add them to the identities list


    const identities = this.getLocalIdentities();
    const privateAddressIdentity = identities.find(_ref => {
      let {
        id
      } = _ref;
      return id === 'privateAddress';
    }); // If we had previously stored them, just update the private address

    if (privateAddressIdentity) {
      privateAddressIdentity.emailAddress = (0, _autofillUtils.formatDuckAddress)(addresses.privateAddress);
    } else {
      // Otherwise, add both addresses
      _classPrivateFieldGet(this, _data2).identities = this.addDuckAddressesToIdentities(identities);
    }
  }
  /** @type { PMData } */


  /**
   * @returns {Promise<import('../Form/matching').SupportedTypes>}
   */
  async getCurrentInputType() {
    throw new Error('Not implemented');
  }

  addDuckAddressesToIdentities(identities) {
    if (!this.hasLocalAddresses) return identities;
    const newIdentities = [];
    let {
      privateAddress,
      personalAddress
    } = this.getLocalAddresses();
    privateAddress = (0, _autofillUtils.formatDuckAddress)(privateAddress);
    personalAddress = (0, _autofillUtils.formatDuckAddress)(personalAddress); // Get the duck addresses in identities

    const duckEmailsInIdentities = identities.reduce((duckEmails, _ref2) => {
      let {
        emailAddress: email
      } = _ref2;
      return email !== null && email !== void 0 && email.includes(_autofillUtils.ADDRESS_DOMAIN) ? duckEmails.concat(email) : duckEmails;
    }, []); // Only add the personal duck address to identities if the user hasn't
    // already manually added it

    if (!duckEmailsInIdentities.includes(personalAddress)) {
      newIdentities.push({
        id: 'personalAddress',
        emailAddress: personalAddress,
        title: 'Blocks email trackers'
      });
    }

    newIdentities.push({
      id: 'privateAddress',
      emailAddress: privateAddress,
      title: 'Blocks email trackers and hides your address'
    });
    return [...identities, ...newIdentities];
  }
  /**
   * Stores init data coming from the tooltipHandler
   * @param { InboundPMData } data
   */


  storeLocalData(data) {
    if (this.stripCredentials) {
      data.credentials.forEach(cred => delete cred.password);
      data.creditCards.forEach(cc => delete cc.cardNumber && delete cc.cardSecurityCode);
    } // Store the full name as a separate field to simplify autocomplete


    const updatedIdentities = data.identities.map(identity => ({ ...identity,
      fullName: (0, _formatters.formatFullName)(identity)
    })); // Add addresses

    _classPrivateFieldGet(this, _data2).identities = this.addDuckAddressesToIdentities(updatedIdentities);
    _classPrivateFieldGet(this, _data2).creditCards = data.creditCards;
    _classPrivateFieldGet(this, _data2).credentials = data.credentials; // Top autofill only

    if (data.serializedInputContext) {
      try {
        _classPrivateFieldGet(this, _data2).topContextData = JSON.parse(data.serializedInputContext);
      } catch (e) {
        console.error(e);
        this.removeTooltip();
      }
    }
  }

  getTopContextData() {
    return _classPrivateFieldGet(this, _data2).topContextData;
  }
  /**
   * @deprecated use `availableInputTypes.credentials` directly instead
   * @returns {boolean}
   */


  get hasLocalCredentials() {
    return _classPrivateFieldGet(this, _data2).credentials.length > 0;
  }

  getLocalCredentials() {
    return _classPrivateFieldGet(this, _data2).credentials.map(cred => {
      const {
        password,
        ...rest
      } = cred;
      return rest;
    });
  }
  /**
   * @deprecated use `availableInputTypes.identities` directly instead
   * @returns {boolean}
   */


  get hasLocalIdentities() {
    return _classPrivateFieldGet(this, _data2).identities.length > 0;
  }

  getLocalIdentities() {
    return _classPrivateFieldGet(this, _data2).identities;
  }
  /**
   * @deprecated use `availableInputTypes.creditCards` directly instead
   * @returns {boolean}
   */


  get hasLocalCreditCards() {
    return _classPrivateFieldGet(this, _data2).creditCards.length > 0;
  }
  /** @return {CreditCardObject[]} */


  getLocalCreditCards() {
    return _classPrivateFieldGet(this, _data2).creditCards;
  }

  async startInit() {
    this.addDeviceListeners();
    await this.setupAutofill();
    await this.refreshSettings();
    await this.setupSettingsPage();
    await this.postInit();

    if (this.settings.featureToggles.credentials_saving) {
      (0, _listenForFormSubmission.default)(this.scanner.forms);
    }
  }
  /**
   * This is a fall-back situation for macOS since it was the only
   * platform to support anything none-email based in the past.
   *
   * Once macOS fully supports 'getAvailableInputTypes' this can be removed
   *
   * @returns {Promise<void>}
   */


  async refreshSettings() {
    var _this$globalConfig$us, _this$globalConfig$us2;

    const defaults = ((_this$globalConfig$us = this.globalConfig.userPreferences) === null || _this$globalConfig$us === void 0 ? void 0 : (_this$globalConfig$us2 = _this$globalConfig$us.platform) === null || _this$globalConfig$us2 === void 0 ? void 0 : _this$globalConfig$us2.name) === 'macos' ? {
      identities: this.hasLocalIdentities,
      credentials: this.hasLocalCredentials,
      creditCards: this.hasLocalCreditCards,
      email: this.isDeviceSignedIn()
    } : undefined;
    await this.settings.refresh(defaults);
  }

  postInit() {}

  async isEnabled() {
    return (0, _autofillUtils.autofillEnabled)(this.globalConfig);
  }

  async init() {
    const isEnabled = await this.isEnabled();
    if (!isEnabled) return;

    if (document.readyState === 'complete') {
      this.startInit();
    } else {
      window.addEventListener('load', () => {
        this.startInit();
      });
    }
  }
  /**
   * @deprecated This was a port from the macOS implementation so the API may not be suitable for all
   * @returns {Promise<any>}
   */


  async getSelectedCredentials() {
    throw new Error('`getSelectedCredentials` not implemented');
  }

  isTestMode() {
    return this.globalConfig.isDDGTestMode;
  }
  /**
   * @param {IdentityObject|CreditCardObject|CredentialsObject|{email:string, id: string}} data
   * @param {string} type
   */


  async selectedDetail(data, type) {
    this.activeFormSelectedDetail(data, type);
  }
  /**
   * @param {IdentityObject|CreditCardObject|CredentialsObject|{email:string, id: string}} data
   * @param {string} type
   */


  activeFormSelectedDetail(data, type) {
    const form = this.currentAttached;

    if (!form) {
      return;
    }

    if (data.id === 'privateAddress') {
      this.refreshAlias();
    }

    if (type === 'email' && 'email' in data) {
      form.autofillEmail(data.email);
    } else {
      form.autofillData(data, type);
    }

    this.removeTooltip();
  }
  /**
   * Before the DataWebTooltip opens, we collect the data based on the config.type
   * @param {InputTypeConfigs} config
   * @param {import('../Form/matching').SupportedTypes} inputType
   * @param {TopContextData} [data]
   * @returns {(CredentialsObject|CreditCardObject|IdentityObject)[]}
   */


  dataForAutofill(config, inputType, data) {
    const subtype = (0, _matching.getSubtypeFromType)(inputType);

    if (config.type === 'identities') {
      return this.getLocalIdentities().filter(identity => !!identity[subtype]);
    }

    if (config.type === 'creditCards') {
      return this.getLocalCreditCards();
    }

    if (config.type === 'credentials') {
      if (data) {
        if (Array.isArray(data.credentials) && data.credentials.length > 0) {
          return data.credentials;
        } else {
          return this.getLocalCredentials();
        }
      }
    }

    return [];
  }
  /**
   * @param {import("../Form/Form").Form} form
   * @param {HTMLInputElement} input
   * @param {{ x: number; y: number; } | null} click
   */


  attachTooltip(form, input, click) {
    // Avoid flashing tooltip from background tabs on macOS
    if (document.visibilityState !== 'visible') return;
    form.activeInput = input;
    this.currentAttached = form;
    const inputType = (0, _matching.getInputType)(input);
    /** @type {PosFn} */

    const getPosition = () => {
      // In extensions, the tooltip is centered on the Dax icon
      return this.globalConfig.isApp ? input.getBoundingClientRect() : (0, _autofillUtils.getDaxBoundingBox)(input);
    }; // todo: this will be migrated to use NativeUIController soon


    if (this.globalConfig.isMobileApp && inputType === 'identities.emailAddress') {
      this.getAlias().then(alias => {
        var _form$activeInput;

        if (alias) form.autofillEmail(alias);else (_form$activeInput = form.activeInput) === null || _form$activeInput === void 0 ? void 0 : _form$activeInput.focus();
      });
      return;
    }
    /** @type {TopContextData} */


    const topContextData = {
      inputType
    }; // Allow features to append/change top context data
    // for example, generated passwords may get appended here

    const processedTopContext = this.preAttachTooltip(topContextData, input, form);
    this.uiController.attach({
      input,
      form,
      click,
      getPosition,
      topContextData: processedTopContext,
      device: this
    });
  }
  /**
   * When an item was selected, we then call back to the device
   * to fetch the full suite of data needed to complete the autofill
   *
   * @param {InputTypeConfigs} config
   * @param {(CreditCardObject|IdentityObject|CredentialsObject)[]} items
   * @param {string|number} id
   */


  onSelect(config, items, id) {
    id = String(id);
    const matchingData = items.find(item => String(item.id) === id);
    if (!matchingData) throw new Error('unreachable (fatal)');

    const dataPromise = (() => {
      switch (config.type) {
        case 'creditCards':
          return this.getAutofillCreditCard(id);

        case 'identities':
          return this.getAutofillIdentity(id);

        case 'credentials':
          {
            if (_Credentials.AUTOGENERATED_KEY in matchingData) {
              return Promise.resolve({
                success: matchingData
              });
            }

            return this.getAutofillCredentials(id);
          }

        default:
          throw new Error('unreachable!');
      }
    })(); // wait for the data back from the device


    dataPromise.then(response => {
      if (response.success) {
        return this.selectedDetail(response.success, config.type);
      } else {
        return Promise.reject(new Error('none-success response'));
      }
    }).catch(e => {
      console.error(e);
      return this.removeTooltip();
    });
  }

  isTooltipActive() {
    var _this$uiController$is, _this$uiController$is2, _this$uiController;

    return (_this$uiController$is = (_this$uiController$is2 = (_this$uiController = this.uiController).isActive) === null || _this$uiController$is2 === void 0 ? void 0 : _this$uiController$is2.call(_this$uiController)) !== null && _this$uiController$is !== void 0 ? _this$uiController$is : false;
  }

  removeTooltip() {
    var _this$uiController$re, _this$uiController2;

    return (_this$uiController$re = (_this$uiController2 = this.uiController).removeTooltip) === null || _this$uiController$re === void 0 ? void 0 : _this$uiController$re.call(_this$uiController2, 'interface');
  }

  async setupSettingsPage() {
    let {
      shouldLog
    } = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {
      shouldLog: false
    };

    if (!this.globalConfig.isDDGDomain) {
      return;
    }

    (0, _autofillUtils.notifyWebApp)({
      isApp: this.globalConfig.isApp
    });

    if (this.isDeviceSignedIn()) {
      let userData;

      try {
        userData = await this.getUserData();
      } catch (e) {}

      let capabilities;

      try {
        capabilities = await this.getEmailProtectionCapabilities();
      } catch (e) {} // Set up listener for web app actions


      window.addEventListener('message', e => {
        if (this.globalConfig.isDDGDomain && e.data.removeUserData) {
          this.removeUserData();
        }
      });
      const hasUserData = userData && !userData.error && Object.entries(userData).length > 0;
      (0, _autofillUtils.notifyWebApp)({
        deviceSignedIn: {
          value: true,
          shouldLog,
          userData: hasUserData ? userData : undefined,
          capabilities
        }
      });
    } else {
      this.trySigningIn();
    }
  }

  async setupAutofill() {}
  /** @returns {Promise<EmailAddresses>} */


  async getAddresses() {
    throw new Error('unimplemented');
  }
  /** @returns {Promise<null|Record<any,any>>} */


  getUserData() {
    return Promise.resolve(null);
  }
  /** @returns {void} */


  removeUserData() {}
  /** @returns {Promise<null|Record<string,boolean>>} */


  getEmailProtectionCapabilities() {
    throw new Error('unimplemented');
  }

  refreshAlias() {}

  async trySigningIn() {
    if (this.globalConfig.isDDGDomain) {
      if (this.attempts < 10) {
        this.attempts++;
        const data = await (0, _autofillUtils.sendAndWaitForAnswer)(_autofillUtils.SIGN_IN_MSG, 'addUserData'); // This call doesn't send a response, so we can't know if it succeeded

        this.storeUserData(data);
        await this.setupAutofill();
        await this.refreshSettings();
        await this.setupSettingsPage({
          shouldLog: true
        });
        await this.postInit();
      } else {
        console.warn('max attempts reached, bailing');
      }
    }
  }

  storeUserData(_data) {}

  addDeviceListeners() {}
  /** @param {() => void} _fn */


  addLogoutListener(_fn) {}

  isDeviceSignedIn() {
    return false;
  }
  /**
   * @returns {Promise<null|string>}
   */


  async getAlias() {
    return null;
  } // PM endpoints


  storeCredentials(_opts) {}

  getAccounts() {}
  /**
   * Gets credentials ready for autofill
   * @param {number|string} _id - the credential id
   * @returns {APIResponseSingle<CredentialsObject>}
   */


  getAutofillCredentials(_id) {
    throw new Error('unimplemented');
  }
  /** @returns {APIResponse<CreditCardObject>} */


  async getAutofillCreditCard(_id) {
    throw new Error('unimplemented');
  }
  /** @returns {Promise<{success: IdentityObject|undefined}>} */


  async getAutofillIdentity(_id) {
    throw new Error('unimplemented');
  }

  openManagePasswords() {}
  /** @param {StoreFormData} values */


  storeFormData(values) {
    this.deviceApi.notify(new _deviceApiCalls.StoreFormDataCall(values));
  }
  /**
   * `preAttachTooltip` happens just before a tooltip is show - features may want to append some data
   * at this point.
   *
   * For example, if password generation is enabled, this will generate
   * a password and send it to the tooltip as though it were a stored credential.
   *
   * @param {TopContextData} topContextData
   * @param {HTMLInputElement} input
   * @param {{isSignup: boolean|null}} form
   */


  preAttachTooltip(topContextData, input, form) {
    // A list of checks to determine if we need to generate a password
    const checks = [topContextData.inputType === 'credentials.password', this.settings.featureToggles.password_generation, form.isSignup]; // if all checks pass, generate and save a password

    if (checks.every(Boolean)) {
      const password = this.passwordGenerator.generate({
        input: input.getAttribute('passwordrules'),
        domain: window.location.hostname
      }); // append the new credential to the topContextData so that the top autofill can display it

      topContextData.credentials = [(0, _Credentials.fromPassword)(password)];
    }

    return topContextData;
  }
  /**
   * `postAutofill` gives features an opportunity to perform an action directly
   * following an autofill.
   *
   * For example, if a generated password was used, we want to fire a save event.
   *
   * @param {IdentityObject|CreditCardObject|CredentialsObject} data
   * @param {DataStorageObject} formValues
   */


  postAutofill(data, formValues) {
    if (_Credentials.AUTOGENERATED_KEY in data && 'password' in data) {
      var _formValues$credentia;

      if (((_formValues$credentia = formValues.credentials) === null || _formValues$credentia === void 0 ? void 0 : _formValues$credentia.password) === data.password) {
        const withAutoGeneratedFlag = (0, _Credentials.appendGeneratedId)(formValues, data.password);
        this.storeFormData(withAutoGeneratedFlag);
      }
    }
  }
  /**
   * `postSubmit` gives features a one-time-only opportunity to perform an
   * action directly after a form submission was observed.
   *
   * Mostly this is about storing data from the form submission, but it can
   * also be used like in the case of Password generation, to append additional
   * data before it's sent to be saved.
   *
   * @param {DataStorageObject} values
   * @param {import("../Form/Form").Form} form
   */


  postSubmit(values, form) {
    if (!form.form) return;
    if (!form.hasValues(values)) return;
    const checks = [form.shouldPromptToStoreData, this.passwordGenerator.generated];

    if (checks.some(Boolean)) {
      const withAutoGeneratedFlag = (0, _Credentials.appendGeneratedId)(values, this.passwordGenerator.password);
      this.storeFormData(withAutoGeneratedFlag);
    }
  }
  /**
   * on macOS we try to detect if a click occurred within a form
   * @param {PointerEvent} event
   */


  _onPointerDown(event) {
    if (this.settings.featureToggles.credentials_saving) {
      this._detectFormSubmission(event);
    }
  }
  /**
   * @param {PointerEvent} event
   */


  _detectFormSubmission(event) {
    const matchingForm = [...this.scanner.forms.values()].find(form => {
      const btns = [...form.submitButtons]; // @ts-ignore

      if (btns.includes(event.target)) return true; // @ts-ignore

      if (btns.find(btn => btn.contains(event.target))) return true;
    });
    matchingForm === null || matchingForm === void 0 ? void 0 : matchingForm.submitHandler();

    if (!matchingForm) {
      var _event$target;

      // check if the click happened on a button
      const button =
      /** @type HTMLElement */
      (_event$target = event.target) === null || _event$target === void 0 ? void 0 : _event$target.closest(_selectorsCss.SUBMIT_BUTTON_SELECTOR);
      if (!button) return;
      const text = (0, _matching.removeExcessWhitespace)(button === null || button === void 0 ? void 0 : button.textContent);
      const hasRelevantText = /(log|sign).?(in|up)|continue|next|submit/i.test(text);

      if (hasRelevantText && text.length < 25) {
        // check if there's a form with values
        const filledForm = [...this.scanner.forms.values()].find(form => form.hasValues());

        if (filledForm && (0, _autofillUtils.buttonMatchesFormType)(
        /** @type HTMLElement */
        button, filledForm)) {
          filledForm === null || filledForm === void 0 ? void 0 : filledForm.submitHandler();
        }
      }
    }
  }
  /**
   * This serves as a single place to create a default instance
   * of InterfacePrototype that can be useful in testing scenarios
   * @returns {InterfacePrototype}
   */


  static default() {
    const globalConfig = (0, _config.createGlobalConfig)();
    const transport = (0, _transports.createTransport)(globalConfig);
    const deviceApi = new _deviceApi.DeviceApi(transport);

    const settings = _Settings.Settings.default(globalConfig, deviceApi);

    return new InterfacePrototype(globalConfig, deviceApi, settings);
  }

}

var _default = InterfacePrototype;
exports.default = _default;

},{"../../packages/device-api":2,"../Form/formatters":19,"../Form/listenForFormSubmission":23,"../Form/matching":26,"../Form/selectors-css":27,"../InputTypes/Credentials":29,"../PasswordGenerator":32,"../Scanner":33,"../Settings":34,"../UI/controllers/NativeUIController":39,"../autofill-utils":46,"../config":48,"../deviceApiCalls/__generated__/deviceApiCalls":50,"../deviceApiCalls/transports/transports":56}],16:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.Form = void 0;

var _FormAnalyzer = _interopRequireDefault(require("./FormAnalyzer"));

var _autofillUtils = require("../autofill-utils");

var _matching = require("./matching");

var _inputStyles = require("./inputStyles");

var _inputTypeConfig = require("./inputTypeConfig.js");

var _formatters = require("./formatters");

var _constants = require("../constants");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

const {
  ATTR_AUTOFILL
} = _constants.constants;

class Form {
  /** @type {import("../Form/matching").Matching} */

  /** @type {HTMLElement} */

  /** @type {HTMLInputElement | null} */

  /** @type {boolean | null} */

  /**
   * @param {HTMLElement} form
   * @param {HTMLInputElement|HTMLSelectElement} input
   * @param {import("../DeviceInterface/InterfacePrototype").default} deviceInterface
   * @param {import("../Form/matching").Matching} [matching]
   */
  constructor(form, input, deviceInterface, matching) {
    _defineProperty(this, "matching", void 0);

    _defineProperty(this, "form", void 0);

    _defineProperty(this, "activeInput", void 0);

    _defineProperty(this, "isSignup", void 0);

    this.form = form;
    this.matching = matching || (0, _matching.createMatching)();
    this.formAnalyzer = new _FormAnalyzer.default(form, input, matching);
    this.isLogin = this.formAnalyzer.isLogin;
    this.isSignup = this.formAnalyzer.isSignup;
    this.device = deviceInterface;
    /** @type Record<'all' | SupportedMainTypes, Set> */

    this.inputs = {
      all: new Set(),
      credentials: new Set(),
      creditCards: new Set(),
      identities: new Set(),
      unknown: new Set()
    };
    this.touched = new Set();
    this.listeners = new Set();
    this.activeInput = null; // We set this to true to skip event listeners while we're autofilling

    this.isAutofilling = false;
    this.handlerExecuted = false;
    this.shouldPromptToStoreData = true;
    /**
     * @type {IntersectionObserver | null}
     */

    this.intObs = new IntersectionObserver(entries => {
      for (const entry of entries) {
        if (!entry.isIntersecting) this.removeTooltip();
      }
    }); // This ensures we fire the handler again if the form is changed

    this.addListener(form, 'input', () => {
      if (!this.isAutofilling) {
        this.handlerExecuted = false;
        this.shouldPromptToStoreData = true;
      }
    });
    this.categorizeInputs();
  }
  /**
   * Checks if the form element contains the activeElement or the event target
   * @return {boolean}
   * @param {KeyboardEvent | null} [e]
   */


  hasFocus(e) {
    return this.form.contains(document.activeElement) || this.form.contains(
    /** @type HTMLElement */
    e === null || e === void 0 ? void 0 : e.target);
  }
  /**
   * Checks that the form element doesn't contain an invalid field
   * @return {boolean}
   */


  isValid() {
    if (this.form instanceof HTMLFormElement) {
      return this.form.checkValidity();
    } // If the container is not a valid form, we must check fields individually


    let validity = true;
    this.execOnInputs(input => {
      if (input.validity && !input.validity.valid) validity = false;
    }, 'all', false);
    return validity;
  }

  submitHandler() {
    var _this$device$postSubm, _this$device;

    if (this.handlerExecuted) return;
    if (!this.isValid()) return;
    const values = this.getValues();
    (_this$device$postSubm = (_this$device = this.device).postSubmit) === null || _this$device$postSubm === void 0 ? void 0 : _this$device$postSubm.call(_this$device, values, this); // mark this form as being handled

    this.handlerExecuted = true;
  }
  /** @return {DataStorageObject} */


  getValues() {
    const formValues = [...this.inputs.credentials, ...this.inputs.identities, ...this.inputs.creditCards].reduce((output, inputEl) => {
      var _output$mainType;

      const mainType = (0, _matching.getInputMainType)(inputEl);
      const subtype = (0, _matching.getInputSubtype)(inputEl);
      let value = inputEl.value || ((_output$mainType = output[mainType]) === null || _output$mainType === void 0 ? void 0 : _output$mainType[subtype]);

      if (subtype === 'addressCountryCode') {
        value = (0, _formatters.inferCountryCodeFromElement)(inputEl);
      }

      if (value) {
        output[mainType][subtype] = value;
      }

      return output;
    }, {
      credentials: {},
      creditCards: {},
      identities: {}
    });
    return (0, _formatters.prepareFormValuesForStorage)(formValues);
  }
  /**
   * Determine if the form has values we want to store in the device
   * @param {DataStorageObject} [values]
   * @return {boolean}
   */


  hasValues(values) {
    const {
      credentials,
      creditCards,
      identities
    } = values || this.getValues();
    return Boolean(credentials || creditCards || identities);
  }

  removeTooltip() {
    var _this$intObs;

    const tooltip = this.device.isTooltipActive();

    if (this.isAutofilling || !tooltip) {
      return;
    }

    this.device.removeTooltip();
    (_this$intObs = this.intObs) === null || _this$intObs === void 0 ? void 0 : _this$intObs.disconnect();
  }

  showingTooltip(input) {
    var _this$intObs2;

    (_this$intObs2 = this.intObs) === null || _this$intObs2 === void 0 ? void 0 : _this$intObs2.observe(input);
  }

  removeInputHighlight(input) {
    (0, _autofillUtils.removeInlineStyles)(input, (0, _inputStyles.getIconStylesAutofilled)(input, this));
    input.classList.remove('ddg-autofilled');
    this.addAutofillStyles(input);
  }

  removeAllHighlights(e, dataType) {
    // This ensures we are not removing the highlight ourselves when autofilling more than once
    if (e && !e.isTrusted) return; // If the user has changed the value, we prompt to update the stored creds

    this.shouldPromptToStoreCredentials = true;
    this.execOnInputs(input => this.removeInputHighlight(input), dataType);
  }

  removeInputDecoration(input) {
    (0, _autofillUtils.removeInlineStyles)(input, (0, _inputStyles.getIconStylesBase)(input, this));
    input.removeAttribute(ATTR_AUTOFILL);
  }

  removeAllDecorations() {
    this.execOnInputs(input => this.removeInputDecoration(input));
    this.listeners.forEach(_ref => {
      let {
        el,
        type,
        fn
      } = _ref;
      return el.removeEventListener(type, fn);
    });
  }

  redecorateAllInputs() {
    this.removeAllDecorations();
    this.execOnInputs(input => this.decorateInput(input));
  }

  resetAllInputs() {
    this.execOnInputs(input => {
      (0, _autofillUtils.setValue)(input, '', this.device.globalConfig);
      this.removeInputHighlight(input);
    });
    if (this.activeInput) this.activeInput.focus();
    this.matching.clear();
  }

  dismissTooltip() {
    this.removeTooltip();
  } // This removes all listeners to avoid memory leaks and weird behaviours


  destroy() {
    this.removeAllDecorations();
    this.removeTooltip();
    this.matching.clear();
    this.intObs = null;
  }

  categorizeInputs() {
    const selector = this.matching.cssSelector('FORM_INPUTS_SELECTOR');
    this.form.querySelectorAll(selector).forEach(input => this.addInput(input));
  }

  get submitButtons() {
    const selector = this.matching.cssSelector('SUBMIT_BUTTON_SELECTOR');
    const allButtons =
    /** @type {HTMLElement[]} */
    [...this.form.querySelectorAll(selector)];
    return allButtons.filter(btn => (0, _autofillUtils.isLikelyASubmitButton)(btn) && (0, _autofillUtils.buttonMatchesFormType)(btn, this));
  }
  /**
   * Executes a function on input elements. Can be limited to certain element types
   * @param {(input: HTMLInputElement|HTMLSelectElement) => void} fn
   * @param {'all' | SupportedMainTypes} inputType
   * @param {boolean} shouldCheckForDecorate
   */


  execOnInputs(fn) {
    let inputType = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 'all';
    let shouldCheckForDecorate = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : true;
    const inputs = this.inputs[inputType];

    for (const input of inputs) {
      let canExecute = true; // sometimes we want to execute even if we didn't decorate

      if (shouldCheckForDecorate) {
        const {
          shouldDecorate
        } = (0, _inputTypeConfig.getInputConfig)(input);
        canExecute = shouldDecorate(input, this);
      }

      if (canExecute) fn(input);
    }
  }

  addInput(input) {
    if (this.inputs.all.has(input)) return this;
    this.inputs.all.add(input);
    this.matching.setInputType(input, this.form, {
      isLogin: this.isLogin
    });
    const mainInputType = (0, _matching.getInputMainType)(input);
    this.inputs[mainInputType].add(input);
    this.decorateInput(input);
    return this;
  }

  areAllInputsEmpty(inputType) {
    let allEmpty = true;
    this.execOnInputs(input => {
      if (input.value) allEmpty = false;
    }, inputType);
    return allEmpty;
  }

  addListener(el, type, fn) {
    el.addEventListener(type, fn);
    this.listeners.add({
      el,
      type,
      fn
    });
  }

  addAutofillStyles(input) {
    const styles = (0, _inputStyles.getIconStylesBase)(input, this);
    (0, _autofillUtils.addInlineStyles)(input, styles);
  }

  decorateInput(input) {
    const config = (0, _inputTypeConfig.getInputConfig)(input);
    if (!config.shouldDecorate(input, this)) return this;
    input.setAttribute(ATTR_AUTOFILL, 'true');
    const hasIcon = !!config.getIconBase(input, this);

    if (hasIcon) {
      this.addAutofillStyles(input);
      this.addListener(input, 'mousemove', e => {
        if ((0, _autofillUtils.isEventWithinDax)(e, e.target)) {
          e.target.style.setProperty('cursor', 'pointer', 'important');
        } else {
          e.target.style.removeProperty('cursor');
        }
      });
    }

    function getMainClickCoords(e) {
      if (!e.isTrusted) return;
      const isMainMouseButton = e.button === 0;
      if (!isMainMouseButton) return;
      return {
        x: e.clientX,
        y: e.clientY
      };
    } // Store the click to a label so we can use the click when the field is focused


    let storedClick = new WeakMap();
    let timeout = null;

    const handlerLabel = e => {
      // Look for e.target OR it's closest parent to be a HTMLLabelElement
      const control = e.target.closest('label').control;
      if (!control) return;
      storedClick.set(control, getMainClickCoords(e));
      clearTimeout(timeout); // Remove the stored click if the timer expires

      timeout = setTimeout(() => {
        storedClick = new WeakMap();
      }, 1000);
    };

    const handler = e => {
      if (this.isAutofilling) {
        return;
      }

      const input = e.target;
      let click = null; // Checks for mousedown event

      if (e.type === 'pointerdown') {
        click = getMainClickCoords(e);
        if (!click) return;
      } else if (storedClick) {
        // Reuse a previous click if one exists for this element
        click = storedClick.get(input);
        storedClick.delete(input);
      }

      if (this.shouldOpenTooltip(e, input)) {
        if (this.device.globalConfig.isMobileApp && // Avoid the icon capturing clicks on small fields making it impossible to focus
        input.offsetWidth > 50 && (0, _autofillUtils.isEventWithinDax)(e, input)) {
          e.preventDefault();
          e.stopImmediatePropagation();
        }

        this.touched.add(input);
        this.device.attachTooltip(this, input, click);
      }
    };

    if (input.nodeName !== 'SELECT') {
      const events = ['pointerdown'];
      if (!this.device.globalConfig.isMobileApp) events.push('focus');
      input.labels.forEach(label => {
        this.addListener(label, 'pointerdown', handlerLabel);
      });
      events.forEach(ev => this.addListener(input, ev, handler));
    }

    return this;
  }

  shouldOpenTooltip(e, input) {
    if (this.device.globalConfig.isApp) return true;
    const inputType = (0, _matching.getInputMainType)(input);
    return !this.touched.has(input) && this.areAllInputsEmpty(inputType) || (0, _autofillUtils.isEventWithinDax)(e, input);
  }

  autofillInput(input, string, dataType) {
    // Do not autofill if it's invisible (select elements can be hidden because of custom implementations)
    if (input instanceof HTMLInputElement && !(0, _autofillUtils.isVisible)(input)) return; // @ts-ignore

    const activeInputSubtype = (0, _matching.getInputSubtype)(this.activeInput);
    const inputSubtype = (0, _matching.getInputSubtype)(input);
    const isEmailAutofill = activeInputSubtype === 'emailAddress' && inputSubtype === 'emailAddress'; // Don't override values for identities, unless it's the current input or we're autofilling email

    if (dataType === 'identities' && // only for identities
    input.nodeName !== 'SELECT' && input.value !== '' && // if the input is not empty
    this.activeInput !== input && // and this is not the active input
    !isEmailAutofill // and we're not auto-filling email
    ) return; // do not overwrite the value

    const successful = (0, _autofillUtils.setValue)(input, string, this.device.globalConfig);
    if (!successful) return;
    input.classList.add('ddg-autofilled');
    (0, _autofillUtils.addInlineStyles)(input, (0, _inputStyles.getIconStylesAutofilled)(input, this)); // If the user changes the value, remove the decoration

    input.addEventListener('input', e => this.removeAllHighlights(e, dataType), {
      once: true
    });
  }
  /**
   * Autofill method for email protection only
   * @param {string} alias
   * @param {'all' | SupportedMainTypes} dataType
   */


  autofillEmail(alias) {
    let dataType = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 'identities';
    this.isAutofilling = true;
    this.execOnInputs(input => this.autofillInput(input, alias, dataType), dataType);
    this.isAutofilling = false;
    this.removeTooltip();
  }

  autofillData(data, dataType) {
    var _this$device$postAuto, _this$device2;

    this.shouldPromptToStoreData = false;
    this.isAutofilling = true;
    this.execOnInputs(input => {
      const inputSubtype = (0, _matching.getInputSubtype)(input);
      let autofillData = data[inputSubtype];

      if (inputSubtype === 'expiration' && input instanceof HTMLInputElement) {
        autofillData = (0, _formatters.getUnifiedExpiryDate)(input, data.expirationMonth, data.expirationYear, this);
      }

      if (inputSubtype === 'expirationYear' && input instanceof HTMLInputElement) {
        autofillData = (0, _formatters.formatCCYear)(input, autofillData, this);
      }

      if (inputSubtype === 'addressCountryCode') {
        autofillData = (0, _formatters.getCountryName)(input, data);
      }

      if (autofillData) this.autofillInput(input, autofillData, dataType);
    }, dataType);
    this.isAutofilling = false;
    (_this$device$postAuto = (_this$device2 = this.device).postAutofill) === null || _this$device$postAuto === void 0 ? void 0 : _this$device$postAuto.call(_this$device2, data, this.getValues());
    this.removeTooltip();
  }

}

exports.Form = Form;

},{"../autofill-utils":46,"../constants":49,"./FormAnalyzer":17,"./formatters":19,"./inputStyles":20,"./inputTypeConfig.js":21,"./matching":26}],17:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;

var _matching = require("./matching");

var _constants = require("../constants");

var _matchingConfiguration = require("./matching-configuration");

var _autofillUtils = require("../autofill-utils");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

class FormAnalyzer {
  /** @type HTMLElement */

  /** @type Matching */

  /**
   * @param {HTMLElement} form
   * @param {HTMLInputElement|HTMLSelectElement} input
   * @param {Matching} [matching]
   */
  constructor(form, input, matching) {
    _defineProperty(this, "form", void 0);

    _defineProperty(this, "matching", void 0);

    this.form = form;
    this.matching = matching || new _matching.Matching(_matchingConfiguration.matchingConfiguration);
    this.autofillSignal = 0;
    this.signals = []; // Avoid autofill on our signup page

    if (window.location.href.match(/^https:\/\/(.+\.)?duckduckgo\.com\/email\/choose-address/i)) {
      return this;
    }

    this.evaluateElAttributes(input, 3, true);
    form ? this.evaluateForm() : this.evaluatePage();
    return this;
  }

  get isLogin() {
    return this.autofillSignal < 0;
  }

  get isSignup() {
    return this.autofillSignal >= 0;
  }

  increaseSignalBy(strength, signal) {
    this.autofillSignal += strength;
    this.signals.push("".concat(signal, ": +").concat(strength));
    return this;
  }

  decreaseSignalBy(strength, signal) {
    this.autofillSignal -= strength;
    this.signals.push("".concat(signal, ": -").concat(strength));
    return this;
  }

  updateSignal(_ref) {
    let {
      string,
      // The string to check
      strength,
      // Strength of the signal
      signalType = 'generic',
      // For debugging purposes, we give a name to the signal
      shouldFlip = false,
      // Flips the signals, i.e. when a link points outside. See below
      shouldCheckUnifiedForm = false,
      // Should check for login/signup forms
      shouldBeConservative = false // Should use the conservative signup regex

    } = _ref;
    const negativeRegex = new RegExp(/sign(ing)?.?in(?!g)|log.?in|unsubscri/i);
    const positiveRegex = new RegExp(/sign(ing)?.?up|join|\bregist(er|ration)|newsletter|\bsubscri(be|ption)|contact|create|start|settings|preferences|profile|update|checkout|guest|purchase|buy|order|schedule|estimate|request/i);
    const conservativePositiveRegex = new RegExp(/sign.?up|join|register|newsletter|subscri(be|ption)|settings|preferences|profile|update/i);
    const strictPositiveRegex = new RegExp(/sign.?up|join|register|settings|preferences|profile|update/i);
    const matchesNegative = string === 'current-password' || string.match(negativeRegex); // Check explicitly for unified login/signup forms. They should always be negative, so we increase signal

    if (shouldCheckUnifiedForm && matchesNegative && string.match(strictPositiveRegex)) {
      this.decreaseSignalBy(strength + 2, "Unified detected ".concat(signalType));
      return this;
    }

    const matchesPositive = string === 'new-password' || string.match(shouldBeConservative ? conservativePositiveRegex : positiveRegex); // In some cases a login match means the login is somewhere else, i.e. when a link points outside

    if (shouldFlip) {
      if (matchesNegative) this.increaseSignalBy(strength, signalType);
      if (matchesPositive) this.decreaseSignalBy(strength, signalType);
    } else {
      if (matchesNegative) this.decreaseSignalBy(strength, signalType);
      if (matchesPositive) this.increaseSignalBy(strength, signalType);
    }

    return this;
  }

  evaluateElAttributes(el) {
    let signalStrength = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 3;
    let isInput = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : false;
    Array.from(el.attributes).forEach(attr => {
      if (attr.name === 'style') return;
      const attributeString = "".concat(attr.name, "=").concat(attr.value);
      this.updateSignal({
        string: attributeString,
        strength: signalStrength,
        signalType: "".concat(el.name, " attr: ").concat(attributeString),
        shouldCheckUnifiedForm: isInput
      });
    });
  }

  evaluatePageTitle() {
    const pageTitle = document.title;
    this.updateSignal({
      string: pageTitle,
      strength: 2,
      signalType: "page title: ".concat(pageTitle)
    });
  }

  evaluatePageHeadings() {
    const headings = document.querySelectorAll('h1, h2, h3, [class*="title"], [id*="title"]');

    if (headings) {
      headings.forEach(_ref2 => {
        let {
          textContent
        } = _ref2;
        textContent = (0, _matching.removeExcessWhitespace)(textContent || '');
        this.updateSignal({
          string: textContent,
          strength: 0.5,
          signalType: "heading: ".concat(textContent),
          shouldCheckUnifiedForm: true,
          shouldBeConservative: true
        });
      });
    }
  }

  evaluatePage() {
    this.evaluatePageTitle();
    this.evaluatePageHeadings(); // Check for submit buttons

    const buttons = document.querySelectorAll("\n                button[type=submit],\n                button:not([type]),\n                [role=button]\n            ");
    buttons.forEach(button => {
      // if the button has a form, it's not related to our input, because our input has no form here
      if (button instanceof HTMLButtonElement) {
        if (!button.form && !button.closest('form')) {
          this.evaluateElement(button);
          this.evaluateElAttributes(button, 0.5);
        }
      }
    });
  }

  elementIs(el, type) {
    return el.nodeName.toLowerCase() === type.toLowerCase();
  }

  getText(el) {
    // for buttons, we don't care about descendants, just get the whole text as is
    // this is important in order to give proper attribution of the text to the button
    if (this.elementIs(el, 'BUTTON')) return (0, _matching.removeExcessWhitespace)(el.textContent);
    if (this.elementIs(el, 'INPUT') && ['submit', 'button'].includes(el.type)) return el.value;
    return (0, _matching.removeExcessWhitespace)(Array.from(el.childNodes).reduce((text, child) => this.elementIs(child, '#text') ? text + ' ' + child.textContent : text, ''));
  }

  evaluateElement(el) {
    const string = this.getText(el);

    if (el.matches(this.matching.cssSelector('password'))) {
      // These are explicit signals by the web author, so we weigh them heavily
      this.updateSignal({
        string: el.getAttribute('autocomplete') || '',
        strength: 20,
        signalType: "explicit: ".concat(el.getAttribute('autocomplete'))
      });
    } // check button contents


    if (el.matches(this.matching.cssSelector('SUBMIT_BUTTON_SELECTOR'))) {
      // If we're sure this is a submit button, it's a stronger signal
      const strength = (0, _autofillUtils.isLikelyASubmitButton)(el) ? 20 : 2;
      this.updateSignal({
        string,
        strength,
        signalType: "submit: ".concat(string)
      });
    } // if a link points to relevant urls or contain contents outside the page…


    if (this.elementIs(el, 'A') && el.href && el.href !== '#' || (el.getAttribute('role') || '').toUpperCase() === 'LINK' || el.matches('button[class*=secondary]')) {
      // …and matches one of the regexes, we assume the match is not pertinent to the current form
      this.updateSignal({
        string,
        strength: 1,
        signalType: "external link: ".concat(string),
        shouldFlip: true
      });
    } else {
      var _removeExcessWhitespa;

      // any other case
      // only consider the el if it's a small text to avoid noisy disclaimers
      if (((_removeExcessWhitespa = (0, _matching.removeExcessWhitespace)(el.textContent)) === null || _removeExcessWhitespa === void 0 ? void 0 : _removeExcessWhitespa.length) < _constants.constants.TEXT_LENGTH_CUTOFF) {
        this.updateSignal({
          string,
          strength: 1,
          signalType: "generic: ".concat(string),
          shouldCheckUnifiedForm: true
        });
      }
    }
  }

  evaluateForm() {
    // Check page title
    this.evaluatePageTitle(); // Check form attributes

    this.evaluateElAttributes(this.form); // Check form contents (skip select and option because they contain too much noise)

    this.form.querySelectorAll('*:not(select):not(option)').forEach(el => {
      // Check if element is not hidden. Note that we can't use offsetHeight
      // nor intersectionObserver, because the element could be outside the
      // viewport or its parent hidden
      const displayValue = window.getComputedStyle(el, null).getPropertyValue('display');
      if (displayValue !== 'none') this.evaluateElement(el);
    }); // If we can't decide at this point, try reading page headings

    if (this.autofillSignal === 0) {
      this.evaluatePageHeadings();
    }

    return this;
  }

}

var _default = FormAnalyzer;
exports.default = _default;

},{"../autofill-utils":46,"../constants":49,"./matching":26,"./matching-configuration":25}],18:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.COUNTRY_NAMES_TO_CODES = exports.COUNTRY_CODES_TO_NAMES = void 0;

/**
 * Country names object using 2-letter country codes to reference country name
 * Derived from the Intl.DisplayNames implementation
 * @source https://stackoverflow.com/a/70517921/1948947
 */
const COUNTRY_CODES_TO_NAMES = {
  AC: 'Ascension Island',
  AD: 'Andorra',
  AE: 'United Arab Emirates',
  AF: 'Afghanistan',
  AG: 'Antigua & Barbuda',
  AI: 'Anguilla',
  AL: 'Albania',
  AM: 'Armenia',
  AN: 'Curaçao',
  AO: 'Angola',
  AQ: 'Antarctica',
  AR: 'Argentina',
  AS: 'American Samoa',
  AT: 'Austria',
  AU: 'Australia',
  AW: 'Aruba',
  AX: 'Åland Islands',
  AZ: 'Azerbaijan',
  BA: 'Bosnia & Herzegovina',
  BB: 'Barbados',
  BD: 'Bangladesh',
  BE: 'Belgium',
  BF: 'Burkina Faso',
  BG: 'Bulgaria',
  BH: 'Bahrain',
  BI: 'Burundi',
  BJ: 'Benin',
  BL: 'St. Barthélemy',
  BM: 'Bermuda',
  BN: 'Brunei',
  BO: 'Bolivia',
  BQ: 'Caribbean Netherlands',
  BR: 'Brazil',
  BS: 'Bahamas',
  BT: 'Bhutan',
  BU: 'Myanmar (Burma)',
  BV: 'Bouvet Island',
  BW: 'Botswana',
  BY: 'Belarus',
  BZ: 'Belize',
  CA: 'Canada',
  CC: 'Cocos (Keeling) Islands',
  CD: 'Congo - Kinshasa',
  CF: 'Central African Republic',
  CG: 'Congo - Brazzaville',
  CH: 'Switzerland',
  CI: 'Côte d’Ivoire',
  CK: 'Cook Islands',
  CL: 'Chile',
  CM: 'Cameroon',
  CN: 'China mainland',
  CO: 'Colombia',
  CP: 'Clipperton Island',
  CR: 'Costa Rica',
  CS: 'Serbia',
  CU: 'Cuba',
  CV: 'Cape Verde',
  CW: 'Curaçao',
  CX: 'Christmas Island',
  CY: 'Cyprus',
  CZ: 'Czechia',
  DD: 'Germany',
  DE: 'Germany',
  DG: 'Diego Garcia',
  DJ: 'Djibouti',
  DK: 'Denmark',
  DM: 'Dominica',
  DO: 'Dominican Republic',
  DY: 'Benin',
  DZ: 'Algeria',
  EA: 'Ceuta & Melilla',
  EC: 'Ecuador',
  EE: 'Estonia',
  EG: 'Egypt',
  EH: 'Western Sahara',
  ER: 'Eritrea',
  ES: 'Spain',
  ET: 'Ethiopia',
  EU: 'European Union',
  EZ: 'Eurozone',
  FI: 'Finland',
  FJ: 'Fiji',
  FK: 'Falkland Islands',
  FM: 'Micronesia',
  FO: 'Faroe Islands',
  FR: 'France',
  FX: 'France',
  GA: 'Gabon',
  GB: 'United Kingdom',
  GD: 'Grenada',
  GE: 'Georgia',
  GF: 'French Guiana',
  GG: 'Guernsey',
  GH: 'Ghana',
  GI: 'Gibraltar',
  GL: 'Greenland',
  GM: 'Gambia',
  GN: 'Guinea',
  GP: 'Guadeloupe',
  GQ: 'Equatorial Guinea',
  GR: 'Greece',
  GS: 'So. Georgia & So. Sandwich Isl.',
  GT: 'Guatemala',
  GU: 'Guam',
  GW: 'Guinea-Bissau',
  GY: 'Guyana',
  HK: 'Hong Kong',
  HM: 'Heard & McDonald Islands',
  HN: 'Honduras',
  HR: 'Croatia',
  HT: 'Haiti',
  HU: 'Hungary',
  HV: 'Burkina Faso',
  IC: 'Canary Islands',
  ID: 'Indonesia',
  IE: 'Ireland',
  IL: 'Israel',
  IM: 'Isle of Man',
  IN: 'India',
  IO: 'Chagos Archipelago',
  IQ: 'Iraq',
  IR: 'Iran',
  IS: 'Iceland',
  IT: 'Italy',
  JE: 'Jersey',
  JM: 'Jamaica',
  JO: 'Jordan',
  JP: 'Japan',
  KE: 'Kenya',
  KG: 'Kyrgyzstan',
  KH: 'Cambodia',
  KI: 'Kiribati',
  KM: 'Comoros',
  KN: 'St. Kitts & Nevis',
  KP: 'North Korea',
  KR: 'South Korea',
  KW: 'Kuwait',
  KY: 'Cayman Islands',
  KZ: 'Kazakhstan',
  LA: 'Laos',
  LB: 'Lebanon',
  LC: 'St. Lucia',
  LI: 'Liechtenstein',
  LK: 'Sri Lanka',
  LR: 'Liberia',
  LS: 'Lesotho',
  LT: 'Lithuania',
  LU: 'Luxembourg',
  LV: 'Latvia',
  LY: 'Libya',
  MA: 'Morocco',
  MC: 'Monaco',
  MD: 'Moldova',
  ME: 'Montenegro',
  MF: 'St. Martin',
  MG: 'Madagascar',
  MH: 'Marshall Islands',
  MK: 'North Macedonia',
  ML: 'Mali',
  MM: 'Myanmar (Burma)',
  MN: 'Mongolia',
  MO: 'Macao',
  MP: 'Northern Mariana Islands',
  MQ: 'Martinique',
  MR: 'Mauritania',
  MS: 'Montserrat',
  MT: 'Malta',
  MU: 'Mauritius',
  MV: 'Maldives',
  MW: 'Malawi',
  MX: 'Mexico',
  MY: 'Malaysia',
  MZ: 'Mozambique',
  NA: 'Namibia',
  NC: 'New Caledonia',
  NE: 'Niger',
  NF: 'Norfolk Island',
  NG: 'Nigeria',
  NH: 'Vanuatu',
  NI: 'Nicaragua',
  NL: 'Netherlands',
  NO: 'Norway',
  NP: 'Nepal',
  NR: 'Nauru',
  NU: 'Niue',
  NZ: 'New Zealand',
  OM: 'Oman',
  PA: 'Panama',
  PE: 'Peru',
  PF: 'French Polynesia',
  PG: 'Papua New Guinea',
  PH: 'Philippines',
  PK: 'Pakistan',
  PL: 'Poland',
  PM: 'St. Pierre & Miquelon',
  PN: 'Pitcairn Islands',
  PR: 'Puerto Rico',
  PS: 'Palestinian Territories',
  PT: 'Portugal',
  PW: 'Palau',
  PY: 'Paraguay',
  QA: 'Qatar',
  QO: 'Outlying Oceania',
  RE: 'Réunion',
  RH: 'Zimbabwe',
  RO: 'Romania',
  RS: 'Serbia',
  RU: 'Russia',
  RW: 'Rwanda',
  SA: 'Saudi Arabia',
  SB: 'Solomon Islands',
  SC: 'Seychelles',
  SD: 'Sudan',
  SE: 'Sweden',
  SG: 'Singapore',
  SH: 'St. Helena',
  SI: 'Slovenia',
  SJ: 'Svalbard & Jan Mayen',
  SK: 'Slovakia',
  SL: 'Sierra Leone',
  SM: 'San Marino',
  SN: 'Senegal',
  SO: 'Somalia',
  SR: 'Suriname',
  SS: 'South Sudan',
  ST: 'São Tomé & Príncipe',
  SU: 'Russia',
  SV: 'El Salvador',
  SX: 'Sint Maarten',
  SY: 'Syria',
  SZ: 'Eswatini',
  TA: 'Tristan da Cunha',
  TC: 'Turks & Caicos Islands',
  TD: 'Chad',
  TF: 'French Southern Territories',
  TG: 'Togo',
  TH: 'Thailand',
  TJ: 'Tajikistan',
  TK: 'Tokelau',
  TL: 'Timor-Leste',
  TM: 'Turkmenistan',
  TN: 'Tunisia',
  TO: 'Tonga',
  TP: 'Timor-Leste',
  TR: 'Turkey',
  TT: 'Trinidad & Tobago',
  TV: 'Tuvalu',
  TW: 'Taiwan',
  TZ: 'Tanzania',
  UA: 'Ukraine',
  UG: 'Uganda',
  UK: 'United Kingdom',
  UM: 'U.S. Outlying Islands',
  UN: 'United Nations',
  US: 'United States',
  UY: 'Uruguay',
  UZ: 'Uzbekistan',
  VA: 'Vatican City',
  VC: 'St. Vincent & Grenadines',
  VD: 'Vietnam',
  VE: 'Venezuela',
  VG: 'British Virgin Islands',
  VI: 'U.S. Virgin Islands',
  VN: 'Vietnam',
  VU: 'Vanuatu',
  WF: 'Wallis & Futuna',
  WS: 'Samoa',
  XA: 'Pseudo-Accents',
  XB: 'Pseudo-Bidi',
  XK: 'Kosovo',
  YD: 'Yemen',
  YE: 'Yemen',
  YT: 'Mayotte',
  YU: 'Serbia',
  ZA: 'South Africa',
  ZM: 'Zambia',
  ZR: 'Congo - Kinshasa',
  ZW: 'Zimbabwe',
  ZZ: 'Unknown Region'
};
/**
 * Country names object using country name to reference 2-letter country codes
 * Derived from the solution above with
 * Object.fromEntries(Object.entries(COUNTRY_CODES_TO_NAMES).map(entry => [entry[1], entry[0]]))
 */

exports.COUNTRY_CODES_TO_NAMES = COUNTRY_CODES_TO_NAMES;
const COUNTRY_NAMES_TO_CODES = {
  'Ascension Island': 'AC',
  Andorra: 'AD',
  'United Arab Emirates': 'AE',
  Afghanistan: 'AF',
  'Antigua & Barbuda': 'AG',
  Anguilla: 'AI',
  Albania: 'AL',
  Armenia: 'AM',
  'Curaçao': 'CW',
  Angola: 'AO',
  Antarctica: 'AQ',
  Argentina: 'AR',
  'American Samoa': 'AS',
  Austria: 'AT',
  Australia: 'AU',
  Aruba: 'AW',
  'Åland Islands': 'AX',
  Azerbaijan: 'AZ',
  'Bosnia & Herzegovina': 'BA',
  Barbados: 'BB',
  Bangladesh: 'BD',
  Belgium: 'BE',
  'Burkina Faso': 'HV',
  Bulgaria: 'BG',
  Bahrain: 'BH',
  Burundi: 'BI',
  Benin: 'DY',
  'St. Barthélemy': 'BL',
  Bermuda: 'BM',
  Brunei: 'BN',
  Bolivia: 'BO',
  'Caribbean Netherlands': 'BQ',
  Brazil: 'BR',
  Bahamas: 'BS',
  Bhutan: 'BT',
  'Myanmar (Burma)': 'MM',
  'Bouvet Island': 'BV',
  Botswana: 'BW',
  Belarus: 'BY',
  Belize: 'BZ',
  Canada: 'CA',
  'Cocos (Keeling) Islands': 'CC',
  'Congo - Kinshasa': 'ZR',
  'Central African Republic': 'CF',
  'Congo - Brazzaville': 'CG',
  Switzerland: 'CH',
  'Côte d’Ivoire': 'CI',
  'Cook Islands': 'CK',
  Chile: 'CL',
  Cameroon: 'CM',
  'China mainland': 'CN',
  Colombia: 'CO',
  'Clipperton Island': 'CP',
  'Costa Rica': 'CR',
  Serbia: 'YU',
  Cuba: 'CU',
  'Cape Verde': 'CV',
  'Christmas Island': 'CX',
  Cyprus: 'CY',
  Czechia: 'CZ',
  Germany: 'DE',
  'Diego Garcia': 'DG',
  Djibouti: 'DJ',
  Denmark: 'DK',
  Dominica: 'DM',
  'Dominican Republic': 'DO',
  Algeria: 'DZ',
  'Ceuta & Melilla': 'EA',
  Ecuador: 'EC',
  Estonia: 'EE',
  Egypt: 'EG',
  'Western Sahara': 'EH',
  Eritrea: 'ER',
  Spain: 'ES',
  Ethiopia: 'ET',
  'European Union': 'EU',
  Eurozone: 'EZ',
  Finland: 'FI',
  Fiji: 'FJ',
  'Falkland Islands': 'FK',
  Micronesia: 'FM',
  'Faroe Islands': 'FO',
  France: 'FX',
  Gabon: 'GA',
  'United Kingdom': 'UK',
  Grenada: 'GD',
  Georgia: 'GE',
  'French Guiana': 'GF',
  Guernsey: 'GG',
  Ghana: 'GH',
  Gibraltar: 'GI',
  Greenland: 'GL',
  Gambia: 'GM',
  Guinea: 'GN',
  Guadeloupe: 'GP',
  'Equatorial Guinea': 'GQ',
  Greece: 'GR',
  'So. Georgia & So. Sandwich Isl.': 'GS',
  Guatemala: 'GT',
  Guam: 'GU',
  'Guinea-Bissau': 'GW',
  Guyana: 'GY',
  'Hong Kong': 'HK',
  'Heard & McDonald Islands': 'HM',
  Honduras: 'HN',
  Croatia: 'HR',
  Haiti: 'HT',
  Hungary: 'HU',
  'Canary Islands': 'IC',
  Indonesia: 'ID',
  Ireland: 'IE',
  Israel: 'IL',
  'Isle of Man': 'IM',
  India: 'IN',
  'Chagos Archipelago': 'IO',
  Iraq: 'IQ',
  Iran: 'IR',
  Iceland: 'IS',
  Italy: 'IT',
  Jersey: 'JE',
  Jamaica: 'JM',
  Jordan: 'JO',
  Japan: 'JP',
  Kenya: 'KE',
  Kyrgyzstan: 'KG',
  Cambodia: 'KH',
  Kiribati: 'KI',
  Comoros: 'KM',
  'St. Kitts & Nevis': 'KN',
  'North Korea': 'KP',
  'South Korea': 'KR',
  Kuwait: 'KW',
  'Cayman Islands': 'KY',
  Kazakhstan: 'KZ',
  Laos: 'LA',
  Lebanon: 'LB',
  'St. Lucia': 'LC',
  Liechtenstein: 'LI',
  'Sri Lanka': 'LK',
  Liberia: 'LR',
  Lesotho: 'LS',
  Lithuania: 'LT',
  Luxembourg: 'LU',
  Latvia: 'LV',
  Libya: 'LY',
  Morocco: 'MA',
  Monaco: 'MC',
  Moldova: 'MD',
  Montenegro: 'ME',
  'St. Martin': 'MF',
  Madagascar: 'MG',
  'Marshall Islands': 'MH',
  'North Macedonia': 'MK',
  Mali: 'ML',
  Mongolia: 'MN',
  Macao: 'MO',
  'Northern Mariana Islands': 'MP',
  Martinique: 'MQ',
  Mauritania: 'MR',
  Montserrat: 'MS',
  Malta: 'MT',
  Mauritius: 'MU',
  Maldives: 'MV',
  Malawi: 'MW',
  Mexico: 'MX',
  Malaysia: 'MY',
  Mozambique: 'MZ',
  Namibia: 'NA',
  'New Caledonia': 'NC',
  Niger: 'NE',
  'Norfolk Island': 'NF',
  Nigeria: 'NG',
  Vanuatu: 'VU',
  Nicaragua: 'NI',
  Netherlands: 'NL',
  Norway: 'NO',
  Nepal: 'NP',
  Nauru: 'NR',
  Niue: 'NU',
  'New Zealand': 'NZ',
  Oman: 'OM',
  Panama: 'PA',
  Peru: 'PE',
  'French Polynesia': 'PF',
  'Papua New Guinea': 'PG',
  Philippines: 'PH',
  Pakistan: 'PK',
  Poland: 'PL',
  'St. Pierre & Miquelon': 'PM',
  'Pitcairn Islands': 'PN',
  'Puerto Rico': 'PR',
  'Palestinian Territories': 'PS',
  Portugal: 'PT',
  Palau: 'PW',
  Paraguay: 'PY',
  Qatar: 'QA',
  'Outlying Oceania': 'QO',
  'Réunion': 'RE',
  Zimbabwe: 'ZW',
  Romania: 'RO',
  Russia: 'SU',
  Rwanda: 'RW',
  'Saudi Arabia': 'SA',
  'Solomon Islands': 'SB',
  Seychelles: 'SC',
  Sudan: 'SD',
  Sweden: 'SE',
  Singapore: 'SG',
  'St. Helena': 'SH',
  Slovenia: 'SI',
  'Svalbard & Jan Mayen': 'SJ',
  Slovakia: 'SK',
  'Sierra Leone': 'SL',
  'San Marino': 'SM',
  Senegal: 'SN',
  Somalia: 'SO',
  Suriname: 'SR',
  'South Sudan': 'SS',
  'São Tomé & Príncipe': 'ST',
  'El Salvador': 'SV',
  'Sint Maarten': 'SX',
  Syria: 'SY',
  Eswatini: 'SZ',
  'Tristan da Cunha': 'TA',
  'Turks & Caicos Islands': 'TC',
  Chad: 'TD',
  'French Southern Territories': 'TF',
  Togo: 'TG',
  Thailand: 'TH',
  Tajikistan: 'TJ',
  Tokelau: 'TK',
  'Timor-Leste': 'TP',
  Turkmenistan: 'TM',
  Tunisia: 'TN',
  Tonga: 'TO',
  Turkey: 'TR',
  'Trinidad & Tobago': 'TT',
  Tuvalu: 'TV',
  Taiwan: 'TW',
  Tanzania: 'TZ',
  Ukraine: 'UA',
  Uganda: 'UG',
  'U.S. Outlying Islands': 'UM',
  'United Nations': 'UN',
  'United States': 'US',
  Uruguay: 'UY',
  Uzbekistan: 'UZ',
  'Vatican City': 'VA',
  'St. Vincent & Grenadines': 'VC',
  Vietnam: 'VN',
  Venezuela: 'VE',
  'British Virgin Islands': 'VG',
  'U.S. Virgin Islands': 'VI',
  'Wallis & Futuna': 'WF',
  Samoa: 'WS',
  'Pseudo-Accents': 'XA',
  'Pseudo-Bidi': 'XB',
  Kosovo: 'XK',
  Yemen: 'YE',
  Mayotte: 'YT',
  'South Africa': 'ZA',
  Zambia: 'ZM',
  'Unknown Region': 'ZZ'
};
exports.COUNTRY_NAMES_TO_CODES = COUNTRY_NAMES_TO_CODES;

},{}],19:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.prepareFormValuesForStorage = exports.inferCountryCodeFromElement = exports.getUnifiedExpiryDate = exports.getMMAndYYYYFromString = exports.getCountryName = exports.getCountryDisplayName = exports.formatFullName = exports.formatCCYear = void 0;

var _matching = require("./matching");

var _countryNames = require("./countryNames");

var _templateObject, _templateObject2;

function _taggedTemplateLiteral(strings, raw) { if (!raw) { raw = strings.slice(0); } return Object.freeze(Object.defineProperties(strings, { raw: { value: Object.freeze(raw) } })); }

// Matches strings like mm/yy, mm-yyyy, mm-aa
const DATE_SEPARATOR_REGEX = /\w\w\s?(?<separator>[/\s.\-_—–])\s?\w\w/i; // Matches 4 non-digit repeated characters (YYYY or AAAA) or 4 digits (2022)

const FOUR_DIGIT_YEAR_REGEX = /(\D)\1{3}|\d{4}/i;
/**
 * Format the cc year to best adapt to the input requirements (YY vs YYYY)
 * @param {HTMLInputElement} input
 * @param {string} year
 * @param {import("./Form").Form} form
 * @returns {string}
 */

const formatCCYear = (input, year, form) => {
  const selector = form.matching.cssSelector('FORM_INPUTS_SELECTOR');
  if (input.maxLength === 4 || (0, _matching.checkPlaceholderAndLabels)(input, FOUR_DIGIT_YEAR_REGEX, form.form, selector)) return year;
  return "".concat(Number(year) - 2000);
};
/**
 * Get a unified expiry date with separator
 * @param {HTMLInputElement} input
 * @param {string} month
 * @param {string} year
 * @param {import("./Form").Form} form
 * @returns {string}
 */


exports.formatCCYear = formatCCYear;

const getUnifiedExpiryDate = (input, month, year, form) => {
  var _matchInPlaceholderAn, _matchInPlaceholderAn2;

  const formattedYear = formatCCYear(input, year, form);
  const paddedMonth = "".concat(month).padStart(2, '0');
  const cssSelector = form.matching.cssSelector('FORM_INPUTS_SELECTOR');
  const separator = ((_matchInPlaceholderAn = (0, _matching.matchInPlaceholderAndLabels)(input, DATE_SEPARATOR_REGEX, form.form, cssSelector)) === null || _matchInPlaceholderAn === void 0 ? void 0 : (_matchInPlaceholderAn2 = _matchInPlaceholderAn.groups) === null || _matchInPlaceholderAn2 === void 0 ? void 0 : _matchInPlaceholderAn2.separator) || '/';
  return "".concat(paddedMonth).concat(separator).concat(formattedYear);
};

exports.getUnifiedExpiryDate = getUnifiedExpiryDate;

const formatFullName = _ref => {
  let {
    firstName = '',
    middleName = '',
    lastName = ''
  } = _ref;
  return "".concat(firstName, " ").concat(middleName ? middleName + ' ' : '').concat(lastName).trim();
};
/**
 * Tries to look up a human-readable country name from the country code
 * @param {string} locale
 * @param {string} addressCountryCode
 * @return {string} - Returns the country code if we can't find a name
 */


exports.formatFullName = formatFullName;

const getCountryDisplayName = (locale, addressCountryCode) => {
  try {
    const regionNames = new Intl.DisplayNames([locale], {
      type: 'region'
    }); // Adding this ts-ignore to prevent having to change this implementation.
    // @ts-ignore

    return regionNames.of(addressCountryCode);
  } catch (e) {
    return _countryNames.COUNTRY_CODES_TO_NAMES[addressCountryCode] || addressCountryCode;
  }
};
/**
 * Tries to infer the element locale or returns 'en'
 * @param {HTMLInputElement | HTMLSelectElement} el
 * @return {string | 'en'}
 */


exports.getCountryDisplayName = getCountryDisplayName;

const inferElementLocale = el => {
  var _el$form;

  return el.lang || ((_el$form = el.form) === null || _el$form === void 0 ? void 0 : _el$form.lang) || document.body.lang || document.documentElement.lang || 'en';
};
/**
 * Tries to format the country code into a localised country name
 * @param {HTMLInputElement | HTMLSelectElement} el
 * @param {{addressCountryCode?: string}} options
 */


const getCountryName = function (el) {
  let options = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  const {
    addressCountryCode
  } = options;
  if (!addressCountryCode) return ''; // Try to infer the field language or fallback to en

  const elLocale = inferElementLocale(el);
  const localisedCountryName = getCountryDisplayName(elLocale, addressCountryCode); // If it's a select el we try to find a suitable match to autofill

  if (el.nodeName === 'SELECT') {
    const englishCountryName = getCountryDisplayName('en', addressCountryCode); // This regex matches both the localised and English country names

    const countryNameRegex = new RegExp(String.raw(_templateObject || (_templateObject = _taggedTemplateLiteral(["", "|", ""])), localisedCountryName.replace(/ /g, '.?'), englishCountryName.replace(/ /g, '.?')), 'i');
    const countryCodeRegex = new RegExp(String.raw(_templateObject2 || (_templateObject2 = _taggedTemplateLiteral(["\b", "\b"], ["\\b", "\\b"])), addressCountryCode), 'i'); // We check the country code first because it's more accurate

    if (el instanceof HTMLSelectElement) {
      for (const option of el.options) {
        if (countryCodeRegex.test(option.value)) {
          return option.value;
        }
      }

      for (const option of el.options) {
        if (countryNameRegex.test(option.value) || countryNameRegex.test(option.innerText)) return option.value;
      }
    }
  }

  return localisedCountryName;
};
/**
 * Try to get a map of localised country names to code, or falls back to the English map
 * @param {HTMLInputElement | HTMLSelectElement} el
 */


exports.getCountryName = getCountryName;

const getLocalisedCountryNamesToCodes = el => {
  if (typeof Intl.DisplayNames !== 'function') return _countryNames.COUNTRY_NAMES_TO_CODES; // Try to infer the field language or fallback to en

  const elLocale = inferElementLocale(el);
  return Object.fromEntries(Object.entries(_countryNames.COUNTRY_CODES_TO_NAMES).map(_ref2 => {
    let [code] = _ref2;
    return [getCountryDisplayName(elLocale, code), code];
  }));
};
/**
 * Try to infer a country code from an element we identified as identities.addressCountryCode
 * @param {HTMLInputElement | HTMLSelectElement} el
 * @return {string}
 */


const inferCountryCodeFromElement = el => {
  if (_countryNames.COUNTRY_CODES_TO_NAMES[el.value]) return el.value;
  if (_countryNames.COUNTRY_NAMES_TO_CODES[el.value]) return _countryNames.COUNTRY_NAMES_TO_CODES[el.value];
  const localisedCountryNamesToCodes = getLocalisedCountryNamesToCodes(el);
  if (localisedCountryNamesToCodes[el.value]) return localisedCountryNamesToCodes[el.value];

  if (el instanceof HTMLSelectElement) {
    var _el$selectedOptions$;

    const selectedText = (_el$selectedOptions$ = el.selectedOptions[0]) === null || _el$selectedOptions$ === void 0 ? void 0 : _el$selectedOptions$.text;
    if (_countryNames.COUNTRY_CODES_TO_NAMES[selectedText]) return selectedText;
    if (_countryNames.COUNTRY_NAMES_TO_CODES[selectedText]) return localisedCountryNamesToCodes[selectedText];
    if (localisedCountryNamesToCodes[selectedText]) return localisedCountryNamesToCodes[selectedText];
  }

  return '';
};
/**
 * Gets separate expiration month and year from a single string
 * @param {string} expiration
 * @return {{expirationYear: string, expirationMonth: string}}
 */


exports.inferCountryCodeFromElement = inferCountryCodeFromElement;

const getMMAndYYYYFromString = expiration => {
  const values = expiration.match(/(\d+)/g) || [];
  return values === null || values === void 0 ? void 0 : values.reduce((output, current) => {
    if (Number(current) > 12) {
      output.expirationYear = current.padStart(4, '20');
    } else {
      output.expirationMonth = current.padStart(2, '0');
    }

    return output;
  }, {
    expirationYear: '',
    expirationMonth: ''
  });
};
/**
 * @param {InternalDataStorageObject} credentials
 * @return {boolean}
 */


exports.getMMAndYYYYFromString = getMMAndYYYYFromString;

const shouldStoreCredentials = _ref3 => {
  let {
    credentials
  } = _ref3;
  return Boolean(credentials.password);
};
/**
 * @param {InternalDataStorageObject} credentials
 * @return {boolean}
 */


const shouldStoreIdentities = _ref4 => {
  let {
    identities
  } = _ref4;
  return Boolean((identities.firstName || identities.fullName) && identities.addressStreet && identities.addressCity);
};
/**
 * @param {InternalDataStorageObject} credentials
 * @return {boolean}
 */


const shouldStoreCreditCards = _ref5 => {
  let {
    creditCards
  } = _ref5;
  if (!creditCards.cardNumber) return false;
  if (creditCards.cardSecurityCode) return true; // Some forms (Amazon) don't have the cvv, so we still save if there's the expiration

  if (creditCards.expiration) return true; // Expiration can also be two separate values

  return Boolean(creditCards.expirationYear && creditCards.expirationMonth);
};
/**
 * Formats form data into an object to send to the device for storage
 * If values are insufficient for a complete entry, they are discarded
 * @param {InternalDataStorageObject} formValues
 * @return {DataStorageObject}
 */


const prepareFormValuesForStorage = formValues => {
  var _identities, _identities2;

  /** @type {Partial<InternalDataStorageObject>} */
  let {
    credentials,
    identities,
    creditCards
  } = formValues; // If we have an identity name but not a card name, copy it over there

  if (!creditCards.cardName && ((_identities = identities) !== null && _identities !== void 0 && _identities.fullName || (_identities2 = identities) !== null && _identities2 !== void 0 && _identities2.firstName)) {
    var _identities3;

    creditCards.cardName = ((_identities3 = identities) === null || _identities3 === void 0 ? void 0 : _identities3.fullName) || formatFullName(identities);
  }
  /** Fixes for credentials **/
  // Don't store if there isn't enough data


  if (shouldStoreCredentials(formValues)) {
    // If we don't have a username to match a password, let's see if the email is available
    if (credentials.password && !credentials.username && identities.emailAddress) {
      credentials.username = identities.emailAddress;
    }
  } else {
    credentials = undefined;
  }
  /** Fixes for identities **/
  // Don't store if there isn't enough data


  if (shouldStoreIdentities(formValues)) {
    if (identities.fullName) {
      // when forms have both first/last and fullName we keep the individual values and drop the fullName
      if (!(identities.firstName && identities.lastName)) {
        // If the fullname can be easily split into two, we'll store it as first and last
        const nameParts = identities.fullName.trim().split(/\s+/);

        if (nameParts.length === 2) {
          identities.firstName = nameParts[0];
          identities.lastName = nameParts[1];
        } else {
          // If we can't split it, just store it as first name
          identities.firstName = identities.fullName;
        }
      }

      delete identities.fullName;
    }
  } else {
    identities = undefined;
  }
  /** Fixes for credit cards **/
  // Don't store if there isn't enough data


  if (shouldStoreCreditCards(formValues)) {
    var _creditCards$expirati;

    if (creditCards.expiration) {
      const {
        expirationMonth,
        expirationYear
      } = getMMAndYYYYFromString(creditCards.expiration);
      creditCards.expirationMonth = expirationMonth;
      creditCards.expirationYear = expirationYear;
      delete creditCards.expiration;
    }

    creditCards.expirationYear = (_creditCards$expirati = creditCards.expirationYear) === null || _creditCards$expirati === void 0 ? void 0 : _creditCards$expirati.padStart(4, '20');

    if (creditCards.cardNumber) {
      creditCards.cardNumber = creditCards.cardNumber.replace(/\D/g, '');
    }
  } else {
    creditCards = undefined;
  }

  return {
    credentials,
    identities,
    creditCards
  };
};

exports.prepareFormValuesForStorage = prepareFormValuesForStorage;

},{"./countryNames":18,"./matching":26}],20:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.getIconStylesBase = exports.getIconStylesAutofilled = void 0;

var _inputTypeConfig = require("./inputTypeConfig.js");

/**
 * Returns the css-ready base64 encoding of the icon for the given input
 * @param {HTMLInputElement} input
 * @param {import("./Form").Form} form
 * @param {'base' | 'filled'} type
 * @return {string}
 */
const getIcon = function (input, form) {
  let type = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : 'base';
  const config = (0, _inputTypeConfig.getInputConfig)(input);

  if (type === 'base') {
    return config.getIconBase(input, form);
  }

  if (type === 'filled') {
    return config.getIconFilled(input, form);
  }

  return '';
};
/**
 * Returns an object with styles to be applied inline
 * @param {HTMLInputElement} input
 * @param {String} icon
 * @return {Object<string, string>}
 */


const getBasicStyles = (input, icon) => ({
  // Height must be > 0 to account for fields initially hidden
  'background-size': "auto ".concat(input.offsetHeight <= 30 && input.offsetHeight > 0 ? '100%' : '26px'),
  'background-position': 'center right',
  'background-repeat': 'no-repeat',
  'background-origin': 'content-box',
  'background-image': "url(".concat(icon, ")"),
  'transition': 'background 0s'
});
/**
 * Get inline styles for the injected icon, base state
 * @param {HTMLInputElement} input
 * @param {import("./Form").Form} form
 * @return {Object<string, string>}
 */


const getIconStylesBase = (input, form) => {
  const icon = getIcon(input, form);
  if (!icon) return {};
  return getBasicStyles(input, icon);
};
/**
 * Get inline styles for the injected icon, autofilled state
 * @param {HTMLInputElement} input
 * @param {import("./Form").Form} form
 * @return {Object<string, string>}
 */


exports.getIconStylesBase = getIconStylesBase;

const getIconStylesAutofilled = (input, form) => {
  const icon = getIcon(input, form, 'filled');
  const iconStyle = icon ? getBasicStyles(input, icon) : {};
  return { ...iconStyle,
    'background-color': '#F8F498',
    'color': '#333333'
  };
};

exports.getIconStylesAutofilled = getIconStylesAutofilled;

},{"./inputTypeConfig.js":21}],21:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.getInputConfigFromType = exports.getInputConfig = void 0;

var _logoSvg = require("./logo-svg");

var ddgPasswordIcons = _interopRequireWildcard(require("../UI/img/ddgPasswordIcon"));

var _matching = require("./matching");

var _Credentials = require("../InputTypes/Credentials");

var _CreditCard = require("../InputTypes/CreditCard");

var _Identity = require("../InputTypes/Identity");

function _getRequireWildcardCache(nodeInterop) { if (typeof WeakMap !== "function") return null; var cacheBabelInterop = new WeakMap(); var cacheNodeInterop = new WeakMap(); return (_getRequireWildcardCache = function (nodeInterop) { return nodeInterop ? cacheNodeInterop : cacheBabelInterop; })(nodeInterop); }

function _interopRequireWildcard(obj, nodeInterop) { if (!nodeInterop && obj && obj.__esModule) { return obj; } if (obj === null || typeof obj !== "object" && typeof obj !== "function") { return { default: obj }; } var cache = _getRequireWildcardCache(nodeInterop); if (cache && cache.has(obj)) { return cache.get(obj); } var newObj = {}; var hasPropertyDescriptor = Object.defineProperty && Object.getOwnPropertyDescriptor; for (var key in obj) { if (key !== "default" && Object.prototype.hasOwnProperty.call(obj, key)) { var desc = hasPropertyDescriptor ? Object.getOwnPropertyDescriptor(obj, key) : null; if (desc && (desc.get || desc.set)) { Object.defineProperty(newObj, key, desc); } else { newObj[key] = obj[key]; } } } newObj.default = obj; if (cache) { cache.set(obj, newObj); } return newObj; }

/**
 * Get the icon for the identities (currently only Dax for emails)
 * @param {HTMLInputElement} input
 * @param {import("./Form").Form} form
 * @return {string}
 */
const getIdentitiesIcon = (input, _ref) => {
  let {
    device
  } = _ref;
  // In Firefox web_accessible_resources could leak a unique user identifier, so we avoid it here
  const {
    isDDGApp,
    isFirefox
  } = device.globalConfig;
  const getDaxImg = isDDGApp || isFirefox ? _logoSvg.daxBase64 : chrome.runtime.getURL('img/logo-small.svg');
  const subtype = (0, _matching.getInputSubtype)(input);
  if (subtype === 'emailAddress' && device.isDeviceSignedIn()) return getDaxImg;
  return '';
};
/**
 * Inputs with readOnly or disabled should never be decorated
 * @param {HTMLInputElement} input
 * @return {boolean}
 */


const canBeDecorated = input => !input.readOnly && !input.disabled;
/**
 * A map of config objects. These help by centralising here some complexity
 * @type {InputTypeConfig}
 */


const inputTypeConfig = {
  /** @type {CredentialsInputTypeConfig} */
  credentials: {
    type: 'credentials',
    getIconBase: (_input, _ref2) => {
      let {
        device
      } = _ref2;

      if (device.settings.featureToggles.inlineIcon_credentials) {
        return ddgPasswordIcons.ddgPasswordIconBase;
      }

      return '';
    },
    getIconFilled: (_input, _ref3) => {
      let {
        device
      } = _ref3;

      if (device.settings.featureToggles.inlineIcon_credentials) {
        return ddgPasswordIcons.ddgPasswordIconFilled;
      }

      return '';
    },
    shouldDecorate: (input, _ref4) => {
      let {
        isLogin,
        device
      } = _ref4;

      // if we are on a 'login' page, continue to use old logic, eg: just checking if there's a
      // saved password
      if (isLogin) {
        return Boolean(device.settings.availableInputTypes.credentials);
      } // at this point, it's not a 'login' attempt, so we could offer to provide a password?


      if (device.settings.featureToggles.password_generation) {
        const subtype = (0, _matching.getInputSubtype)(input);

        if (subtype === 'password') {
          return true;
        }
      }

      return false;
    },
    dataType: 'Credentials',
    tooltipItem: data => (0, _Credentials.createCredentialsTooltipItem)(data)
  },

  /** @type {CreditCardsInputTypeConfig} */
  creditCards: {
    type: 'creditCards',
    getIconBase: () => '',
    getIconFilled: () => '',
    shouldDecorate: (_input, _ref5) => {
      let {
        device
      } = _ref5;
      return canBeDecorated(_input) && Boolean(device.settings.availableInputTypes.creditCards);
    },
    dataType: 'CreditCards',
    tooltipItem: data => new _CreditCard.CreditCardTooltipItem(data)
  },

  /** @type {IdentitiesInputTypeConfig} */
  identities: {
    type: 'identities',
    getIconBase: getIdentitiesIcon,
    getIconFilled: getIdentitiesIcon,
    shouldDecorate: (input, _ref6) => {
      let {
        device
      } = _ref6;
      if (!canBeDecorated(input)) return false;
      const subtype = (0, _matching.getInputSubtype)(input);

      if (device.settings.availableInputTypes.identities) {
        var _device$getLocalIdent;

        return Boolean((_device$getLocalIdent = device.getLocalIdentities()) === null || _device$getLocalIdent === void 0 ? void 0 : _device$getLocalIdent.some(identity => !!identity[subtype]));
      }

      if (subtype === 'emailAddress') {
        return Boolean(device.isDeviceSignedIn());
      }

      return false;
    },
    dataType: 'Identities',
    tooltipItem: data => new _Identity.IdentityTooltipItem(data)
  },

  /** @type {UnknownInputTypeConfig} */
  unknown: {
    type: 'unknown',
    getIconBase: () => '',
    getIconFilled: () => '',
    shouldDecorate: () => false,
    dataType: '',
    tooltipItem: _data => {
      throw new Error('unreachable');
    }
  }
};
/**
 * Retrieves configs from an input el
 * @param {HTMLInputElement} input
 * @returns {InputTypeConfigs}
 */

const getInputConfig = input => {
  const inputType = (0, _matching.getInputType)(input);
  return getInputConfigFromType(inputType);
};
/**
 * Retrieves configs from an input type
 * @param {import('./matching').SupportedTypes | string} inputType
 * @returns {InputTypeConfigs}
 */


exports.getInputConfig = getInputConfig;

const getInputConfigFromType = inputType => {
  const inputMainType = (0, _matching.getMainTypeFromType)(inputType);
  return inputTypeConfig[inputMainType];
};

exports.getInputConfigFromType = getInputConfigFromType;

},{"../InputTypes/Credentials":29,"../InputTypes/CreditCard":30,"../InputTypes/Identity":31,"../UI/img/ddgPasswordIcon":42,"./logo-svg":24,"./matching":26}],22:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.extractElementStrings = void 0;

var _matching = require("./matching");

const EXCLUDED_TAGS = ['SCRIPT', 'NOSCRIPT', 'OPTION', 'STYLE'];
/**
 * Extract all strings of an element's children to an array.
 * "element.textContent" is a string which is merged of all children nodes,
 * which can cause issues with things like script tags etc.
 *
 * @param  {HTMLElement} element
 *         A DOM element to be extracted.
 * @returns {string[]}
 *          All strings in an element.
 */

const extractElementStrings = element => {
  const strings = [];

  const _extractElementStrings = el => {
    if (EXCLUDED_TAGS.includes(el.tagName)) {
      return;
    } // only take the string when it's an explicit text node


    if (el.nodeType === el.TEXT_NODE || !el.childNodes.length) {
      let trimmedText = (0, _matching.removeExcessWhitespace)(el.textContent);

      if (trimmedText) {
        strings.push(trimmedText);
      }

      return;
    }

    for (let node of el.childNodes) {
      let nodeType = node.nodeType;

      if (nodeType !== node.ELEMENT_NODE && nodeType !== node.TEXT_NODE) {
        continue;
      }

      _extractElementStrings(node);
    }
  };

  _extractElementStrings(element);

  return strings;
};

exports.extractElementStrings = extractElementStrings;

},{"./matching":26}],23:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;

/**
 * @param {Map<HTMLElement, import("./Form").Form>} forms
 */
const listenForGlobalFormSubmission = forms => {
  try {
    window.addEventListener('submit', e => {
      var _forms$get;

      return (// @ts-ignore
        (_forms$get = forms.get(e.target)) === null || _forms$get === void 0 ? void 0 : _forms$get.submitHandler()
      );
    }, true);
    window.addEventListener('keydown', e => {
      if (e.key === 'Enter') {
        const focusedForm = [...forms.values()].find(form => form.hasFocus(e));
        focusedForm === null || focusedForm === void 0 ? void 0 : focusedForm.submitHandler();
      }
    });
    const observer = new PerformanceObserver(list => {
      const entries = list.getEntries().filter(entry => // @ts-ignore why does TS not know about `entry.initiatorType`?
      ['fetch', 'xmlhttprequest'].includes(entry.initiatorType) && /login|sign-in|signin/.test(entry.name));
      if (!entries.length) return;
      const filledForm = [...forms.values()].find(form => form.hasValues());
      filledForm === null || filledForm === void 0 ? void 0 : filledForm.submitHandler();
    });
    observer.observe({
      entryTypes: ['resource']
    });
  } catch (error) {// Unable to detect form submissions using AJAX calls
  }
};

var _default = listenForGlobalFormSubmission;
exports.default = _default;

},{}],24:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.daxBase64 = void 0;
const daxBase64 = 'data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjI0IiB2aWV3Qm94PSIwIDAgNDQgNDQiIHdpZHRoPSIyNCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+PGxpbmVhckdyYWRpZW50IGlkPSJhIj48c3RvcCBvZmZzZXQ9Ii4wMSIgc3RvcC1jb2xvcj0iIzYxNzZiOSIvPjxzdG9wIG9mZnNldD0iLjY5IiBzdG9wLWNvbG9yPSIjMzk0YTlmIi8+PC9saW5lYXJHcmFkaWVudD48bGluZWFyR3JhZGllbnQgaWQ9ImIiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMTMuOTI5NyIgeDI9IjE3LjA3MiIgeGxpbms6aHJlZj0iI2EiIHkxPSIxNi4zOTgiIHkyPSIxNi4zOTgiLz48bGluZWFyR3JhZGllbnQgaWQ9ImMiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMjMuODExNSIgeDI9IjI2LjY3NTIiIHhsaW5rOmhyZWY9IiNhIiB5MT0iMTQuOTY3OSIgeTI9IjE0Ljk2NzkiLz48bWFzayBpZD0iZCIgaGVpZ2h0PSI0MCIgbWFza1VuaXRzPSJ1c2VyU3BhY2VPblVzZSIgd2lkdGg9IjQwIiB4PSIyIiB5PSIyIj48cGF0aCBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Im0yMi4wMDAzIDQxLjA2NjljMTAuNTMwMiAwIDE5LjA2NjYtOC41MzY0IDE5LjA2NjYtMTkuMDY2NiAwLTEwLjUzMDMtOC41MzY0LTE5LjA2NjcxLTE5LjA2NjYtMTkuMDY2NzEtMTAuNTMwMyAwLTE5LjA2NjcxIDguNTM2NDEtMTkuMDY2NzEgMTkuMDY2NzEgMCAxMC41MzAyIDguNTM2NDEgMTkuMDY2NiAxOS4wNjY3MSAxOS4wNjY2eiIgZmlsbD0iI2ZmZiIgZmlsbC1ydWxlPSJldmVub2RkIi8+PC9tYXNrPjxwYXRoIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0ibTIyIDQ0YzEyLjE1MDMgMCAyMi05Ljg0OTcgMjItMjIgMC0xMi4xNTAyNi05Ljg0OTctMjItMjItMjItMTIuMTUwMjYgMC0yMiA5Ljg0OTc0LTIyIDIyIDAgMTIuMTUwMyA5Ljg0OTc0IDIyIDIyIDIyeiIgZmlsbD0iI2RlNTgzMyIgZmlsbC1ydWxlPSJldmVub2RkIi8+PGcgbWFzaz0idXJsKCNkKSI+PHBhdGggY2xpcC1ydWxlPSJldmVub2RkIiBkPSJtMjYuMDgxMyA0MS42Mzg2Yy0uOTIwMy0xLjc4OTMtMS44MDAzLTMuNDM1Ni0yLjM0NjYtNC41MjQ2LTEuNDUyLTIuOTA3Ny0yLjkxMTQtNy4wMDctMi4yNDc3LTkuNjUwNy4xMjEtLjQ4MDMtMS4zNjc3LTE3Ljc4Njk5LTIuNDItMTguMzQ0MzItMS4xNjk3LS42MjMzMy0zLjcxMDctMS40NDQ2Ny01LjAyNy0xLjY2NDY3LS45MTY3LS4xNDY2Ni0xLjEyNTcuMTEtMS41MTA3LjE2ODY3LjM2My4wMzY2NyAyLjA5Ljg4NzMzIDIuNDIzNy45MzUtLjMzMzcuMjI3MzMtMS4zMi0uMDA3MzMtMS45NTA3LjI3MTMzLS4zMTkuMTQ2NjctLjU1NzMuNjg5MzQtLjU1Ljk0NiAxLjc5NjctLjE4MzMzIDQuNjA1NC0uMDAzNjYgNi4yNy43MzMyOS0xLjMyMzYuMTUwNC0zLjMzMy4zMTktNC4xOTgzLjc3MzctMi41MDggMS4zMi0zLjYxNTMgNC40MTEtMi45NTUzIDguMTE0My42NTYzIDMuNjk2IDMuNTY0IDE3LjE3ODQgNC40OTE2IDIxLjY4MS45MjQgNC40OTkgMTEuNTUzNyAzLjU1NjcgMTAuMDE3NC41NjF6IiBmaWxsPSIjZDVkN2Q4IiBmaWxsLXJ1bGU9ImV2ZW5vZGQiLz48cGF0aCBkPSJtMjIuMjg2NSAyNi44NDM5Yy0uNjYgMi42NDM2Ljc5MiA2LjczOTMgMi4yNDc2IDkuNjUwNi40ODkxLjk3MjcgMS4yNDM4IDIuMzkyMSAyLjA1NTggMy45NjM3LTEuODk0LjQ2OTMtNi40ODk1IDEuMTI2NC05LjcxOTEgMC0uOTI0LTQuNDkxNy0zLjgzMTctMTcuOTc3Ny00LjQ5NTMtMjEuNjgxLS42Ni0zLjcwMzMgMC02LjM0NyAyLjUxNTMtNy42NjcuODYxNy0uNDU0NyAyLjA5MzctLjc4NDcgMy40MTM3LS45MzEzLTEuNjY0Ny0uNzQwNy0zLjYzNzQtMS4wMjY3LTUuNDQxNC0uODQzMzYtLjAwNzMtLjc2MjY3IDEuMzM4NC0uNzE4NjcgMS44NDQ0LTEuMDYzMzQtLjMzMzctLjA0NzY2LTEuMTYyNC0uNzk1NjYtMS41MjktLjgzMjMzIDIuMjg4My0uMzkyNDQgNC42NDIzLS4wMjEzOCA2LjY5OSAxLjA1NiAxLjA0ODYuNTYxIDEuNzg5MyAxLjE2MjMzIDIuMjQ3NiAxLjc5MzAzIDEuMTk1NC4yMjczIDIuMjUxNC42NiAyLjk0MDcgMS4zNDkzIDIuMTE5MyAyLjExNTcgNC4wMTEzIDYuOTUyIDMuMjE5MyA5LjczMTMtLjIyMzYuNzctLjczMzMgMS4zMzEtMS4zNzEzIDEuNzk2Ny0xLjIzOTMuOTAyLTEuMDE5My0xLjA0NS00LjEwMy45NzE3LS4zOTk3LjI2MDMtLjM5OTcgMi4yMjU2LS41MjQzIDIuNzA2eiIgZmlsbD0iI2ZmZiIvPjwvZz48ZyBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGZpbGwtcnVsZT0iZXZlbm9kZCI+PHBhdGggZD0ibTE2LjY3MjQgMjAuMzU0Yy43Njc1IDAgMS4zODk2LS42MjIxIDEuMzg5Ni0xLjM4OTZzLS42MjIxLTEuMzg5Ny0xLjM4OTYtMS4zODk3LTEuMzg5Ny42MjIyLTEuMzg5NyAxLjM4OTcuNjIyMiAxLjM4OTYgMS4zODk3IDEuMzg5NnoiIGZpbGw9IiMyZDRmOGUiLz48cGF0aCBkPSJtMTcuMjkyNCAxOC44NjE3Yy4xOTg1IDAgLjM1OTQtLjE2MDguMzU5NC0uMzU5M3MtLjE2MDktLjM1OTMtLjM1OTQtLjM1OTNjLS4xOTg0IDAtLjM1OTMuMTYwOC0uMzU5My4zNTkzcy4xNjA5LjM1OTMuMzU5My4zNTkzeiIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Im0yNS45NTY4IDE5LjMzMTFjLjY1ODEgMCAxLjE5MTctLjUzMzUgMS4xOTE3LTEuMTkxNyAwLS42NTgxLS41MzM2LTEuMTkxNi0xLjE5MTctMS4xOTE2cy0xLjE5MTcuNTMzNS0xLjE5MTcgMS4xOTE2YzAgLjY1ODIuNTMzNiAxLjE5MTcgMS4xOTE3IDEuMTkxN3oiIGZpbGw9IiMyZDRmOGUiLz48cGF0aCBkPSJtMjYuNDg4MiAxOC4wNTExYy4xNzAxIDAgLjMwOC0uMTM3OS4zMDgtLjMwOHMtLjEzNzktLjMwOC0uMzA4LS4zMDgtLjMwOC4xMzc5LS4zMDguMzA4LjEzNzkuMzA4LjMwOC4zMDh6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTE3LjA3MiAxNC45NDJzLTEuMDQ4Ni0uNDc2Ni0yLjA2NDMuMTY1Yy0xLjAxNTcuNjM4LS45NzkgMS4yOTA3LS45NzkgMS4yOTA3cy0uNTM5LTEuMjAyNy44OTgzLTEuNzkzYzEuNDQxLS41ODY3IDIuMTQ1LjMzNzMgMi4xNDUuMzM3M3oiIGZpbGw9InVybCgjYikiLz48cGF0aCBkPSJtMjYuNjc1MiAxNC44NDY3cy0uNzUxNy0uNDI5LTEuMzM4My0uNDIxN2MtMS4xOTkuMDE0Ny0xLjUyNTQuNTQyNy0xLjUyNTQuNTQyN3MuMjAxNy0xLjI2MTQgMS43MzQ0LTEuMDA4NGMuNDk5Ny4wOTE0LjkyMjMuNDIzNCAxLjEyOTMuODg3NHoiIGZpbGw9InVybCgjYykiLz48cGF0aCBkPSJtMjAuOTI1OCAyNC4zMjFjLjEzOTMtLjg0MzMgMi4zMS0yLjQzMSAzLjg1LTIuNTMgMS41NC0uMDk1MyAyLjAxNjctLjA3MzMgMy4zLS4zODEzIDEuMjg3LS4zMDQzIDQuNTk4LTEuMTI5MyA1LjUxMS0xLjU1NDcuOTE2Ny0uNDIxNiA0LjgwMzMuMjA5IDIuMDY0MyAxLjczOC0xLjE4NDMuNjYzNy00LjM3OCAxLjg4MS02LjY2MjMgMi41NjMtMi4yODA3LjY4Mi0zLjY2My0uNjUyNi00LjQyMi40Njk0LS42MDEzLjg5MS0uMTIxIDIuMTEyIDIuNjAzMyAyLjM2NSAzLjY4MTQuMzQxIDcuMjA4Ny0xLjY1NzQgNy41OTc0LS41OTQuMzg4NiAxLjA2MzMtMy4xNjA3IDIuMzgzMy01LjMyNCAyLjQyNzMtMi4xNjM0LjA0MDMtNi41MTk0LTEuNDMtNy4xNzItMS44ODQ3LS42NTY0LS40NTEtMS41MjU0LTEuNTE0My0xLjM0NTctMi42MTh6IiBmaWxsPSIjZmRkMjBhIi8+PHBhdGggZD0ibTI4Ljg4MjUgMzEuODM4NmMtLjc3NzMtLjE3MjQtNC4zMTIgMi41MDA2LTQuMzEyIDIuNTAwNmguMDAzN2wtLjE2NSAyLjA1MzRzNC4wNDA2IDEuNjUzNiA0LjczIDEuMzk3Yy42ODkzLS4yNjQuNTE3LTUuNzc1LS4yNTY3LTUuOTUxem0tMTEuNTQ2MyAxLjAzNGMuMDg0My0xLjExODQgNS4yNTQzIDEuNjQyNiA1LjI1NDMgMS42NDI2bC4wMDM3LS4wMDM2LjI1NjYgMi4xNTZzLTQuMzA4MyAyLjU4MTMtNC45MTMzIDIuMjM2NmMtLjYwMTMtLjM0NDYtLjY4OTMtNC45MDk2LS42MDEzLTYuMDMxNnoiIGZpbGw9IiM2NWJjNDYiLz48cGF0aCBkPSJtMjEuMzQgMzQuODA0OWMwIDEuODA3Ny0uMjYwNCAyLjU4NS41MTMzIDIuNzU3NC43NzczLjE3MjMgMi4yNDAzIDAgMi43NjEtLjM0NDcuNTEzMy0uMzQ0Ny4wODQzLTIuNjY5My0uMDg4LTMuMTAycy0zLjE5LS4wODgtMy4xOS42ODkzeiIgZmlsbD0iIzQzYTI0NCIvPjxwYXRoIGQ9Im0yMS42NzAxIDM0LjQwNTFjMCAxLjgwNzYtLjI2MDQgMi41ODEzLjUxMzMgMi43NTM2Ljc3MzcuMTc2IDIuMjM2NyAwIDIuNzU3My0uMzQ0Ni41MTctLjM0NDcuMDg4LTIuNjY5NC0uMDg0My0zLjEwMi0uMTcyMy0uNDMyNy0zLjE5LS4wODQ0LTMuMTkuNjg5M3oiIGZpbGw9IiM2NWJjNDYiLz48cGF0aCBkPSJtMjIuMDAwMiA0MC40NDgxYzEwLjE4ODUgMCAxOC40NDc5LTguMjU5NCAxOC40NDc5LTE4LjQ0NzlzLTguMjU5NC0xOC40NDc5NS0xOC40NDc5LTE4LjQ0Nzk1LTE4LjQ0Nzk1IDguMjU5NDUtMTguNDQ3OTUgMTguNDQ3OTUgOC4yNTk0NSAxOC40NDc5IDE4LjQ0Nzk1IDE4LjQ0Nzl6bTAgMS43MTg3YzExLjEzNzcgMCAyMC4xNjY2LTkuMDI4OSAyMC4xNjY2LTIwLjE2NjYgMC0xMS4xMzc4LTkuMDI4OS0yMC4xNjY3LTIwLjE2NjYtMjAuMTY2Ny0xMS4xMzc4IDAtMjAuMTY2NyA5LjAyODktMjAuMTY2NyAyMC4xNjY3IDAgMTEuMTM3NyA5LjAyODkgMjAuMTY2NiAyMC4xNjY3IDIwLjE2NjZ6IiBmaWxsPSIjZmZmIi8+PC9nPjwvc3ZnPg==';
exports.daxBase64 = daxBase64;

},{}],25:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.matchingConfiguration = void 0;

var _selectorsCss = _interopRequireDefault(require("./selectors-css"));

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

/**
 * This is here to mimic what Remote Configuration might look like
 * later on.
 *
 * @type {MatchingConfiguration}
 */
const matchingConfiguration = {
  /** @type {MatcherConfiguration} */
  matchers: {
    fields: {
      email: {
        type: 'email',
        strategies: {
          cssSelector: 'email',
          ddgMatcher: 'email',
          vendorRegex: 'email'
        }
      },
      password: {
        type: 'password',
        strategies: {
          cssSelector: 'password',
          ddgMatcher: 'password'
        }
      },
      username: {
        type: 'username',
        strategies: {
          cssSelector: 'username',
          ddgMatcher: 'username'
        }
      },
      firstName: {
        type: 'firstName',
        strategies: {
          cssSelector: 'firstName',
          ddgMatcher: 'firstName',
          vendorRegex: 'given-name'
        }
      },
      middleName: {
        type: 'middleName',
        strategies: {
          cssSelector: 'middleName',
          ddgMatcher: 'middleName',
          vendorRegex: 'additional-name'
        }
      },
      lastName: {
        type: 'lastName',
        strategies: {
          cssSelector: 'lastName',
          ddgMatcher: 'lastName',
          vendorRegex: 'family-name'
        }
      },
      fullName: {
        type: 'fullName',
        strategies: {
          cssSelector: 'fullName',
          ddgMatcher: 'fullName',
          vendorRegex: 'name'
        }
      },
      phone: {
        type: 'phone',
        strategies: {
          cssSelector: 'phone',
          ddgMatcher: 'phone',
          vendorRegex: 'tel'
        }
      },
      addressStreet: {
        type: 'addressStreet',
        strategies: {
          cssSelector: 'addressStreet',
          ddgMatcher: 'addressStreet',
          vendorRegex: 'address-line1'
        }
      },
      addressStreet2: {
        type: 'addressStreet2',
        strategies: {
          cssSelector: 'addressStreet2',
          ddgMatcher: 'addressStreet2',
          vendorRegex: 'address-line2'
        }
      },
      addressCity: {
        type: 'addressCity',
        strategies: {
          cssSelector: 'addressCity',
          ddgMatcher: 'addressCity',
          vendorRegex: 'address-level2'
        }
      },
      addressProvince: {
        type: 'addressProvince',
        strategies: {
          cssSelector: 'addressProvince',
          ddgMatcher: 'addressProvince',
          vendorRegex: 'address-level1'
        }
      },
      addressPostalCode: {
        type: 'addressPostalCode',
        strategies: {
          cssSelector: 'addressPostalCode',
          ddgMatcher: 'addressPostalCode',
          vendorRegex: 'postal-code'
        }
      },
      addressCountryCode: {
        type: 'addressCountryCode',
        strategies: {
          cssSelector: 'addressCountryCode',
          ddgMatcher: 'addressCountryCode',
          vendorRegex: 'country'
        }
      },
      birthdayDay: {
        type: 'birthdayDay',
        strategies: {
          cssSelector: 'birthdayDay',
          ddgMatcher: 'birthdayDay'
        }
      },
      birthdayMonth: {
        type: 'birthdayMonth',
        strategies: {
          cssSelector: 'birthdayMonth',
          ddgMatcher: 'birthdayMonth'
        }
      },
      birthdayYear: {
        type: 'birthdayYear',
        strategies: {
          cssSelector: 'birthdayYear',
          ddgMatcher: 'birthdayYear'
        }
      },
      cardName: {
        type: 'cardName',
        strategies: {
          cssSelector: 'cardName',
          ddgMatcher: 'cardName',
          vendorRegex: 'cc-name'
        }
      },
      cardNumber: {
        type: 'cardNumber',
        strategies: {
          cssSelector: 'cardNumber',
          ddgMatcher: 'cardNumber',
          vendorRegex: 'cc-number'
        }
      },
      cardSecurityCode: {
        type: 'cardSecurityCode',
        strategies: {
          cssSelector: 'cardSecurityCode',
          ddgMatcher: 'cardSecurityCode'
        }
      },
      expirationMonth: {
        type: 'expirationMonth',
        strategies: {
          cssSelector: 'expirationMonth',
          ddgMatcher: 'expirationMonth',
          vendorRegex: 'cc-exp-month'
        }
      },
      expirationYear: {
        type: 'expirationYear',
        strategies: {
          cssSelector: 'expirationYear',
          ddgMatcher: 'expirationYear',
          vendorRegex: 'cc-exp-year'
        }
      },
      expiration: {
        type: 'expiration',
        strategies: {
          cssSelector: 'expiration',
          ddgMatcher: 'expiration',
          vendorRegex: 'cc-exp'
        }
      }
    },
    lists: {
      email: ['email'],
      password: ['password'],
      username: ['username'],
      cc: ['cardName', 'cardNumber', 'cardSecurityCode', 'expirationMonth', 'expirationYear', 'expiration'],
      id: ['firstName', 'middleName', 'lastName', 'fullName', 'phone', 'addressStreet', 'addressStreet2', 'addressCity', 'addressProvince', 'addressPostalCode', 'addressCountryCode', 'birthdayDay', 'birthdayMonth', 'birthdayYear']
    }
  },
  strategies: {
    /** @type {CssSelectorConfiguration} */
    cssSelector: {
      selectors: {
        // Generic
        FORM_INPUTS_SELECTOR: _selectorsCss.default.__secret_do_not_use.FORM_INPUTS_SELECTOR,
        SUBMIT_BUTTON_SELECTOR: _selectorsCss.default.__secret_do_not_use.SUBMIT_BUTTON_SELECTOR,
        GENERIC_TEXT_FIELD: _selectorsCss.default.__secret_do_not_use.GENERIC_TEXT_FIELD,
        // user
        email: _selectorsCss.default.__secret_do_not_use.email,
        password: _selectorsCss.default.__secret_do_not_use.password,
        username: _selectorsCss.default.__secret_do_not_use.username,
        // CC
        cardName: _selectorsCss.default.__secret_do_not_use.cardName,
        cardNumber: _selectorsCss.default.__secret_do_not_use.cardNumber,
        cardSecurityCode: _selectorsCss.default.__secret_do_not_use.cardSecurityCode,
        expirationMonth: _selectorsCss.default.__secret_do_not_use.expirationMonth,
        expirationYear: _selectorsCss.default.__secret_do_not_use.expirationYear,
        expiration: _selectorsCss.default.__secret_do_not_use.expiration,
        // Identities
        firstName: _selectorsCss.default.__secret_do_not_use.firstName,
        middleName: _selectorsCss.default.__secret_do_not_use.middleName,
        lastName: _selectorsCss.default.__secret_do_not_use.lastName,
        fullName: _selectorsCss.default.__secret_do_not_use.fullName,
        phone: _selectorsCss.default.__secret_do_not_use.phone,
        addressStreet: _selectorsCss.default.__secret_do_not_use.addressStreet1,
        addressStreet2: _selectorsCss.default.__secret_do_not_use.addressStreet2,
        addressCity: _selectorsCss.default.__secret_do_not_use.addressCity,
        addressProvince: _selectorsCss.default.__secret_do_not_use.addressProvince,
        addressPostalCode: _selectorsCss.default.__secret_do_not_use.addressPostalCode,
        addressCountryCode: _selectorsCss.default.__secret_do_not_use.addressCountryCode,
        birthdayDay: _selectorsCss.default.__secret_do_not_use.birthdayDay,
        birthdayMonth: _selectorsCss.default.__secret_do_not_use.birthdayMonth,
        birthdayYear: _selectorsCss.default.__secret_do_not_use.birthdayYear
      }
    },

    /** @type {DDGMatcherConfiguration} */
    ddgMatcher: {
      matchers: {
        email: {
          match: '.mail\\b',
          skip: 'phone|name|reservation number',
          forceUnknown: 'search|filter|subject|title|\btab\b'
        },
        password: {
          match: 'password',
          forceUnknown: 'captcha|mfa|2fa|two factor'
        },
        username: {
          match: '(user|account|apple|login)((.)?(name|id|login).?)?(.or.+)?$|benutzername',
          forceUnknown: 'search'
        },
        // CC
        cardName: {
          match: '(card.*name|name.*card)|(card.*holder|holder.*card)|(card.*owner|owner.*card)'
        },
        cardNumber: {
          match: 'card.*number|number.*card',
          forceUnknown: 'plus'
        },
        cardSecurityCode: {
          match: 'security.?code|card.?verif|cvv|csc|cvc'
        },
        expirationMonth: {
          match: '(card|\\bcc\\b)?.?(exp(iry|iration)?)?.?(month|\\bmm\\b(?![.\\s/-]yy))',
          skip: 'mm[/\\s.\\-_—–]'
        },
        expirationYear: {
          match: '(card|\\bcc\\b)?.?(exp(iry|iration)?)?.?(year|yy)',
          skip: 'mm[/\\s.\\-_—–]'
        },
        expiration: {
          match: '(\\bmm\\b|\\b\\d\\d\\b)[/\\s.\\-_—–](\\byy|\\bjj|\\baa|\\b\\d\\d)|\\bexp|\\bvalid(idity| through| until)',
          skip: 'invalid'
        },
        // Identities
        firstName: {
          match: '(first|given|fore).?name',
          skip: 'last'
        },
        middleName: {
          match: '(middle|additional).?name'
        },
        lastName: {
          match: '(last|family|sur)[^i]?name',
          skip: 'first'
        },
        fullName: {
          match: '^(full.?|whole\\s|first.*last\\s|real\\s|contact.?)?name\\b',
          forceUnknown: 'company|org|item'
        },
        phone: {
          match: 'phone',
          skip: 'code|pass|country',
          forceUnknown: 'ext|type'
        },
        addressStreet: {
          match: 'address',
          forceUnknown: '\\bip\\b|duck|web|url',
          skip: 'address.*(2|two|3|three)|email|log.?in|sign.?in'
        },
        addressStreet2: {
          match: 'address.*(2|two)|apartment|\\bapt\\b|\\bflat\\b|\\bline.*(2|two)',
          forceUnknown: '\\bip\\b|duck',
          skip: 'email|log.?in|sign.?in'
        },
        addressCity: {
          match: 'city|town',
          forceUnknown: 'vatican'
        },
        addressProvince: {
          match: 'state|province|region|county',
          forceUnknown: 'united',
          skip: 'country'
        },
        addressPostalCode: {
          match: '\\bzip\\b|postal\b|post.?code'
        },
        addressCountryCode: {
          match: 'country'
        },
        birthdayDay: {
          match: '(birth.*day|day.*birth)',
          skip: 'month|year'
        },
        birthdayMonth: {
          match: '(birth.*month|month.*birth)',
          skip: 'year'
        },
        birthdayYear: {
          match: '(birth.*year|year.*birth)'
        }
      }
    },

    /**
     * @type {VendorRegexConfiguration}
     */
    vendorRegex: {
      rules: {
        email: null,
        tel: null,
        organization: null,
        'street-address': null,
        'address-line1': null,
        'address-line2': null,
        'address-line3': null,
        'address-level2': null,
        'address-level1': null,
        'postal-code': null,
        country: null,
        'cc-name': null,
        name: null,
        'given-name': null,
        'additional-name': null,
        'family-name': null,
        'cc-number': null,
        'cc-exp-month': null,
        'cc-exp-year': null,
        'cc-exp': null,
        'cc-type': null
      },
      ruleSets: [//= ========================================================================
      // Firefox-specific rules
      {
        'address-line1': 'addrline1|address_1',
        'address-line2': 'addrline2|address_2',
        'address-line3': 'addrline3|address_3',
        'address-level1': 'land',
        // de-DE
        'additional-name': 'apellido.?materno|lastlastname',
        'cc-name': 'accountholdername' + '|titulaire',
        // fr-FR
        'cc-number': '(cc|kk)nr',
        // de-DE
        'cc-exp-month': '(cc|kk)month',
        // de-DE
        'cc-exp-year': '(cc|kk)year',
        // de-DE
        'cc-type': 'type' + '|kartenmarke' // de-DE

      }, //= ========================================================================
      // These are the rules used by Bitwarden [0], converted into RegExp form.
      // [0] https://github.com/bitwarden/browser/blob/c2b8802201fac5e292d55d5caf3f1f78088d823c/src/services/autofill.service.ts#L436
      {
        email: '(^e-?mail$)|(^email-?address$)',
        tel: '(^phone$)' + '|(^mobile$)' + '|(^mobile-?phone$)' + '|(^tel$)' + '|(^telephone$)' + '|(^phone-?number$)',
        organization: '(^company$)' + '|(^company-?name$)' + '|(^organization$)' + '|(^organization-?name$)',
        'street-address': '(^address$)' + '|(^street-?address$)' + '|(^addr$)' + '|(^street$)' + '|(^mailing-?addr(ess)?$)' + // Modified to not grab lines, below
        '|(^billing-?addr(ess)?$)' + // Modified to not grab lines, below
        '|(^mail-?addr(ess)?$)' + // Modified to not grab lines, below
        '|(^bill-?addr(ess)?$)',
        // Modified to not grab lines, below
        'address-line1': '(^address-?1$)' + '|(^address-?line-?1$)' + '|(^addr-?1$)' + '|(^street-?1$)',
        'address-line2': '(^address-?2$)' + '|(^address-?line-?2$)' + '|(^addr-?2$)' + '|(^street-?2$)',
        'address-line3': '(^address-?3$)' + '|(^address-?line-?3$)' + '|(^addr-?3$)' + '|(^street-?3$)',
        'address-level2': '(^city$)' + '|(^town$)' + '|(^address-?level-?2$)' + '|(^address-?city$)' + '|(^address-?town$)',
        'address-level1': '(^state$)' + '|(^province$)' + '|(^provence$)' + '|(^address-?level-?1$)' + '|(^address-?state$)' + '|(^address-?province$)',
        'postal-code': '(^postal$)' + '|(^zip$)' + '|(^zip2$)' + '|(^zip-?code$)' + '|(^postal-?code$)' + '|(^post-?code$)' + '|(^address-?zip$)' + '|(^address-?postal$)' + '|(^address-?code$)' + '|(^address-?postal-?code$)' + '|(^address-?zip-?code$)',
        country: '(^country$)' + '|(^country-?code$)' + '|(^country-?name$)' + '|(^address-?country$)' + '|(^address-?country-?name$)' + '|(^address-?country-?code$)',
        name: '(^name$)|full-?name|your-?name',
        'given-name': '(^f-?name$)' + '|(^first-?name$)' + '|(^given-?name$)' + '|(^first-?n$)',
        'additional-name': '(^m-?name$)' + '|(^middle-?name$)' + '|(^additional-?name$)' + '|(^middle-?initial$)' + '|(^middle-?n$)' + '|(^middle-?i$)',
        'family-name': '(^l-?name$)' + '|(^last-?name$)' + '|(^s-?name$)' + '|(^surname$)' + '|(^family-?name$)' + '|(^family-?n$)' + '|(^last-?n$)',
        'cc-name': 'cc-?name' + '|card-?name' + '|cardholder-?name' + '|cardholder' + // "|(^name$)" + // Removed to avoid overwriting "name", above.
        '|(^nom$)',
        'cc-number': 'cc-?number' + '|cc-?num' + '|card-?number' + '|card-?num' + '|(^number$)' + '|(^cc$)' + '|cc-?no' + '|card-?no' + '|(^credit-?card$)' + '|numero-?carte' + '|(^carte$)' + '|(^carte-?credit$)' + '|num-?carte' + '|cb-?num',
        'cc-exp': '(^cc-?exp$)' + '|(^card-?exp$)' + '|(^cc-?expiration$)' + '|(^card-?expiration$)' + '|(^cc-?ex$)' + '|(^card-?ex$)' + '|(^card-?expire$)' + '|(^card-?expiry$)' + '|(^validite$)' + '|(^expiration$)' + '|(^expiry$)' + '|mm-?yy' + '|mm-?yyyy' + '|yy-?mm' + '|yyyy-?mm' + '|expiration-?date' + '|payment-?card-?expiration' + '|(^payment-?cc-?date$)',
        'cc-exp-month': '(^exp-?month$)' + '|(^cc-?exp-?month$)' + '|(^cc-?month$)' + '|(^card-?month$)' + '|(^cc-?mo$)' + '|(^card-?mo$)' + '|(^exp-?mo$)' + '|(^card-?exp-?mo$)' + '|(^cc-?exp-?mo$)' + '|(^card-?expiration-?month$)' + '|(^expiration-?month$)' + '|(^cc-?mm$)' + '|(^cc-?m$)' + '|(^card-?mm$)' + '|(^card-?m$)' + '|(^card-?exp-?mm$)' + '|(^cc-?exp-?mm$)' + '|(^exp-?mm$)' + '|(^exp-?m$)' + '|(^expire-?month$)' + '|(^expire-?mo$)' + '|(^expiry-?month$)' + '|(^expiry-?mo$)' + '|(^card-?expire-?month$)' + '|(^card-?expire-?mo$)' + '|(^card-?expiry-?month$)' + '|(^card-?expiry-?mo$)' + '|(^mois-?validite$)' + '|(^mois-?expiration$)' + '|(^m-?validite$)' + '|(^m-?expiration$)' + '|(^expiry-?date-?field-?month$)' + '|(^expiration-?date-?month$)' + '|(^expiration-?date-?mm$)' + '|(^exp-?mon$)' + '|(^validity-?mo$)' + '|(^exp-?date-?mo$)' + '|(^cb-?date-?mois$)' + '|(^date-?m$)',
        'cc-exp-year': '(^exp-?year$)' + '|(^cc-?exp-?year$)' + '|(^cc-?year$)' + '|(^card-?year$)' + '|(^cc-?yr$)' + '|(^card-?yr$)' + '|(^exp-?yr$)' + '|(^card-?exp-?yr$)' + '|(^cc-?exp-?yr$)' + '|(^card-?expiration-?year$)' + '|(^expiration-?year$)' + '|(^cc-?yy$)' + '|(^cc-?y$)' + '|(^card-?yy$)' + '|(^card-?y$)' + '|(^card-?exp-?yy$)' + '|(^cc-?exp-?yy$)' + '|(^exp-?yy$)' + '|(^exp-?y$)' + '|(^cc-?yyyy$)' + '|(^card-?yyyy$)' + '|(^card-?exp-?yyyy$)' + '|(^cc-?exp-?yyyy$)' + '|(^expire-?year$)' + '|(^expire-?yr$)' + '|(^expiry-?year$)' + '|(^expiry-?yr$)' + '|(^card-?expire-?year$)' + '|(^card-?expire-?yr$)' + '|(^card-?expiry-?year$)' + '|(^card-?expiry-?yr$)' + '|(^an-?validite$)' + '|(^an-?expiration$)' + '|(^annee-?validite$)' + '|(^annee-?expiration$)' + '|(^expiry-?date-?field-?year$)' + '|(^expiration-?date-?year$)' + '|(^cb-?date-?ann$)' + '|(^expiration-?date-?yy$)' + '|(^expiration-?date-?yyyy$)' + '|(^validity-?year$)' + '|(^exp-?date-?year$)' + '|(^date-?y$)',
        'cc-type': '(^cc-?type$)' + '|(^card-?type$)' + '|(^card-?brand$)' + '|(^cc-?brand$)' + '|(^cb-?type$)'
      }, //= ========================================================================
      // These rules are from Chromium source codes [1]. Most of them
      // converted to JS format have the same meaning with the original ones
      // except the first line of "address-level1".
      // [1] https://source.chromium.org/chromium/chromium/src/+/master:components/autofill/core/common/autofill_regex_constants.cc
      {
        // ==== Email ====
        email: 'e.?mail' + '|courriel' + // fr
        '|correo.*electr(o|ó)nico' + // es-ES
        '|メールアドレス' + // ja-JP
        '|Электронной.?Почты' + // ru
        '|邮件|邮箱' + // zh-CN
        '|電郵地址' + // zh-TW
        '|ഇ-മെയില്‍|ഇലക്ട്രോണിക്.?' + 'മെയിൽ' + // ml
        '|ایمیل|پست.*الکترونیک' + // fa
        '|ईमेल|इलॅक्ट्रॉनिक.?मेल' + // hi
        '|(\\b|_)eposta(\\b|_)' + // tr
        '|(?:이메일|전자.?우편|[Ee]-?mail)(.?주소)?',
        // ko-KR
        // ==== Telephone ====
        tel: 'phone|mobile|contact.?number' + '|telefonnummer' + // de-DE
        '|telefono|teléfono' + // es
        '|telfixe' + // fr-FR
        '|電話' + // ja-JP
        '|telefone|telemovel' + // pt-BR, pt-PT
        '|телефон' + // ru
        '|मोबाइल' + // hi for mobile
        '|(\\b|_|\\*)telefon(\\b|_|\\*)' + // tr
        '|电话' + // zh-CN
        '|മൊബൈല്‍' + // ml for mobile
        '|(?:전화|핸드폰|휴대폰|휴대전화)(?:.?번호)?',
        // ko-KR
        // ==== Address Fields ====
        organization: 'company|business|organization|organisation' + // '|(?<!con)firma' + // de-DE // // todo: not supported in safari
        '|empresa' + // es
        '|societe|société' + // fr-FR
        '|ragione.?sociale' + // it-IT
        '|会社' + // ja-JP
        '|название.?компании' + // ru
        '|单位|公司' + // zh-CN
        '|شرکت' + // fa
        '|회사|직장',
        // ko-KR
        'street-address': 'streetaddress|street-address',
        'address-line1': '^address$|address[_-]?line[_-]?(1|one)|address1|addr1|street' + '|(?:shipping|billing)address$' + '|strasse|straße|hausnummer|housenumber' + // de-DE
        '|house.?name' + // en-GB
        '|direccion|dirección' + // es
        '|adresse' + // fr-FR
        '|indirizzo' + // it-IT
        '|^住所$|住所1' + // ja-JP
        // '|morada|((?<!identificação do )endereço)' + // pt-BR, pt-PT // todo: not supported in safari
        '|Адрес' + // ru
        '|地址' + // zh-CN
        '|(\\b|_)adres(?! (başlığı(nız)?|tarifi))(\\b|_)' + // tr
        '|^주소.?$|주소.?1',
        // ko-KR
        'address-line2': 'address[_-]?line(2|two)|address2|addr2|street|suite|unit(?!e)' + // Firefox adds `(?!e)` to unit to skip `United State`
        '|adresszusatz|ergänzende.?angaben' + // de-DE
        '|direccion2|colonia|adicional' + // es
        '|addresssuppl|complementnom|appartement' + // fr-FR
        '|indirizzo2' + // it-IT
        '|住所2' + // ja-JP
        '|complemento|addrcomplement' + // pt-BR, pt-PT
        '|Улица' + // ru
        '|地址2' + // zh-CN
        '|주소.?2',
        // ko-KR
        'address-line3': 'address[_-]?line(3|three)|address3|addr3|street|suite|unit(?!e)' + // Firefox adds `(?!e)` to unit to skip `United State`
        '|adresszusatz|ergänzende.?angaben' + // de-DE
        '|direccion3|colonia|adicional' + // es
        '|addresssuppl|complementnom|appartement' + // fr-FR
        '|indirizzo3' + // it-IT
        '|住所3' + // ja-JP
        '|complemento|addrcomplement' + // pt-BR, pt-PT
        '|Улица' + // ru
        '|地址3' + // zh-CN
        '|주소.?3',
        // ko-KR
        'address-level2': 'city|town' + '|\\bort\\b|stadt' + // de-DE
        '|suburb' + // en-AU
        '|ciudad|provincia|localidad|poblacion' + // es
        '|ville|commune' + // fr-FR
        '|localit(a|à)|citt(a|à)' + // it-IT
        '|市区町村' + // ja-JP
        '|cidade' + // pt-BR, pt-PT
        '|Город' + // ru
        '|市' + // zh-CN
        '|分區' + // zh-TW
        '|شهر' + // fa
        '|शहर' + // hi for city
        '|ग्राम|गाँव' + // hi for village
        '|നഗരം|ഗ്രാമം' + // ml for town|village
        '|((\\b|_|\\*)([İii̇]l[cç]e(miz|niz)?)(\\b|_|\\*))' + // tr
        '|^시[^도·・]|시[·・]?군[·・]?구',
        // ko-KR
        'address-level1': // '(?<!(united|hist|history).?)state|county|region|province' + // todo: not supported in safari
        'county|region|province' + '|county|principality' + // en-UK
        '|都道府県' + // ja-JP
        '|estado|provincia' + // pt-BR, pt-PT
        '|область' + // ru
        '|省' + // zh-CN
        '|地區' + // zh-TW
        '|സംസ്ഥാനം' + // ml
        '|استان' + // fa
        '|राज्य' + // hi
        '|((\\b|_|\\*)(eyalet|[şs]ehir|[İii̇]l(imiz)?|kent)(\\b|_|\\*))' + // tr
        '|^시[·・]?도',
        // ko-KR
        'postal-code': 'zip|postal|post.*code|pcode' + '|pin.?code' + // en-IN
        '|postleitzahl' + // de-DE
        '|\\bcp\\b' + // es
        '|\\bcdp\\b' + // fr-FR
        '|\\bcap\\b' + // it-IT
        '|郵便番号' + // ja-JP
        '|codigo|codpos|\\bcep\\b' + // pt-BR, pt-PT
        '|Почтовый.?Индекс' + // ru
        '|पिन.?कोड' + // hi
        '|പിന്‍കോഡ്' + // ml
        '|邮政编码|邮编' + // zh-CN
        '|郵遞區號' + // zh-TW
        '|(\\b|_)posta kodu(\\b|_)' + // tr
        '|우편.?번호',
        // ko-KR
        country: 'country|countries' + '|país|pais' + // es
        '|(\\b|_)land(\\b|_)(?!.*(mark.*))' + // de-DE landmark is a type in india.
        // '|(?<!(入|出))国' + // ja-JP // todo: not supported in safari
        '|国家' + // zh-CN
        '|국가|나라' + // ko-KR
        '|(\\b|_)(ülke|ulce|ulke)(\\b|_)' + // tr
        '|کشور',
        // fa
        // ==== Name Fields ====
        'cc-name': 'card.?(?:holder|owner)|name.*(\\b)?on(\\b)?.*card' + '|(?:card|cc).?name|cc.?full.?name' + '|karteninhaber' + // de-DE
        '|nombre.*tarjeta' + // es
        '|nom.*carte' + // fr-FR
        '|nome.*cart' + // it-IT
        '|名前' + // ja-JP
        '|Имя.*карты' + // ru
        '|信用卡开户名|开户名|持卡人姓名' + // zh-CN
        '|持卡人姓名',
        // zh-TW
        name: '^name|full.?name|your.?name|customer.?name|bill.?name|ship.?name' + '|name.*first.*last|firstandlastname' + '|nombre.*y.*apellidos' + // es
        '|^nom(?!bre)' + // fr-FR
        '|お名前|氏名' + // ja-JP
        '|^nome' + // pt-BR, pt-PT
        '|نام.*نام.*خانوادگی' + // fa
        '|姓名' + // zh-CN
        '|(\\b|_|\\*)ad[ı]? soyad[ı]?(\\b|_|\\*)' + // tr
        '|성명',
        // ko-KR
        'given-name': 'first.*name|initials|fname|first$|given.*name' + '|vorname' + // de-DE
        '|nombre' + // es
        '|forename|prénom|prenom' + // fr-FR
        '|名' + // ja-JP
        '|nome' + // pt-BR, pt-PT
        '|Имя' + // ru
        '|نام' + // fa
        '|이름' + // ko-KR
        '|പേര്' + // ml
        '|(\\b|_|\\*)(isim|ad|ad(i|ı|iniz|ınız)?)(\\b|_|\\*)' + // tr
        '|नाम',
        // hi
        'additional-name': 'middle.*name|mname|middle$|middle.*initial|m\\.i\\.|mi$|\\bmi\\b',
        'family-name': 'last.*name|lname|surname|last$|secondname|family.*name' + '|nachname' + // de-DE
        '|apellidos?' + // es
        '|famille|^nom(?!bre)' + // fr-FR
        '|cognome' + // it-IT
        '|姓' + // ja-JP
        '|apelidos|surename|sobrenome' + // pt-BR, pt-PT
        '|Фамилия' + // ru
        '|نام.*خانوادگی' + // fa
        '|उपनाम' + // hi
        '|മറുപേര്' + // ml
        '|(\\b|_|\\*)(soyisim|soyad(i|ı|iniz|ınız)?)(\\b|_|\\*)' + // tr
        '|\\b성(?:[^명]|\\b)',
        // ko-KR
        // ==== Credit Card Fields ====
        // Note: `cc-name` expression has been moved up, above `name`, in
        // order to handle specialization through ordering.
        'cc-number': '(add)?(?:card|cc|acct).?(?:number|#|no|num|field)' + // '|(?<!telefon|haus|person|fødsels)nummer' + // de-DE, sv-SE, no // todo: not supported in safari
        '|カード番号' + // ja-JP
        '|Номер.*карты' + // ru
        '|信用卡号|信用卡号码' + // zh-CN
        '|信用卡卡號' + // zh-TW
        '|카드' + // ko-KR
        // es/pt/fr
        '|(numero|número|numéro)(?!.*(document|fono|phone|réservation))',
        'cc-exp-month': // 'expir|exp.*mo|exp.*date|ccmonth|cardmonth|addmonth' + // todo: Decide if we need any of this
        'gueltig|gültig|monat' + // de-DE
        '|fecha' + // es
        '|date.*exp' + // fr-FR
        '|scadenza' + // it-IT
        '|有効期限' + // ja-JP
        '|validade' + // pt-BR, pt-PT
        '|Срок действия карты' + // ru
        '|月',
        // zh-CN
        'cc-exp-year': // 'exp|^/|(add)?year' + // todo: Decide if we need any of this
        'ablaufdatum|gueltig|gültig|jahr' + // de-DE
        '|fecha' + // es
        '|scadenza' + // it-IT
        '|有効期限' + // ja-JP
        '|validade' + // pt-BR, pt-PT
        '|Срок действия карты' + // ru
        '|年|有效期',
        // zh-CN
        'cc-exp': 'expir|exp.*date|^expfield$' + '|gueltig|gültig' + // de-DE
        '|fecha' + // es
        '|date.*exp' + // fr-FR
        '|scadenza' + // it-IT
        '|有効期限' + // ja-JP
        '|validade' + // pt-BR, pt-PT
        '|Срок действия карты' // ru

      }]
    }
  }
};
exports.matchingConfiguration = matchingConfiguration;

},{"./selectors-css":27}],26:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.checkPlaceholderAndLabels = exports.Matching = void 0;
exports.createMatching = createMatching;
exports.getInputMainType = exports.getExplicitLabelsText = void 0;
exports.getInputSubtype = getInputSubtype;
exports.getInputType = getInputType;
exports.getMainTypeFromType = getMainTypeFromType;
exports.getRelatedText = void 0;
exports.getSubtypeFromType = getSubtypeFromType;
exports.safeRegex = exports.removeExcessWhitespace = exports.matchInPlaceholderAndLabels = void 0;

var _vendorRegex = require("./vendor-regex");

var _constants = require("../constants");

var _labelUtil = require("./label-util");

var _selectorsCss = require("./selectors-css");

var _matchingConfiguration = require("./matching-configuration");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

const {
  TEXT_LENGTH_CUTOFF,
  ATTR_INPUT_TYPE
} = _constants.constants;
/**
 * An abstraction around the concept of classifying input fields.
 *
 * The only state this class keeps is derived from the passed-in MatchingConfiguration.
 */

var _config = /*#__PURE__*/new WeakMap();

var _cssSelectors = /*#__PURE__*/new WeakMap();

var _ddgMatchers = /*#__PURE__*/new WeakMap();

var _vendorRegExpCache = /*#__PURE__*/new WeakMap();

var _matcherLists = /*#__PURE__*/new WeakMap();

var _defaultStrategyOrder = /*#__PURE__*/new WeakMap();

class Matching {
  /** @type {MatchingConfiguration} */

  /** @type {CssSelectorConfiguration['selectors']} */

  /** @type {Record<string, DDGMatcher>} */

  /**
   * This acts as an internal cache for the larger vendorRegexes
   * @type {{RULES: Record<keyof VendorRegexRules, RegExp|undefined>}}
   */

  /** @type {MatcherLists} */

  /** @type {Array<StrategyNames>} */

  /** @type {Record<MatchableStrings, string>} */

  /**
   * @param {MatchingConfiguration} config
   */
  constructor(config) {
    _classPrivateFieldInitSpec(this, _config, {
      writable: true,
      value: void 0
    });

    _classPrivateFieldInitSpec(this, _cssSelectors, {
      writable: true,
      value: void 0
    });

    _classPrivateFieldInitSpec(this, _ddgMatchers, {
      writable: true,
      value: void 0
    });

    _classPrivateFieldInitSpec(this, _vendorRegExpCache, {
      writable: true,
      value: void 0
    });

    _classPrivateFieldInitSpec(this, _matcherLists, {
      writable: true,
      value: void 0
    });

    _classPrivateFieldInitSpec(this, _defaultStrategyOrder, {
      writable: true,
      value: ['cssSelector', 'ddgMatcher', 'vendorRegex']
    });

    _defineProperty(this, "activeElementStrings", {
      nameAttr: '',
      labelText: '',
      placeholderAttr: '',
      relatedText: '',
      id: ''
    });

    _defineProperty(this, "_elementStringCache", new WeakMap());

    _classPrivateFieldSet(this, _config, config);

    const {
      rules,
      ruleSets
    } = _classPrivateFieldGet(this, _config).strategies.vendorRegex;

    _classPrivateFieldSet(this, _vendorRegExpCache, (0, _vendorRegex.createCacheableVendorRegexes)(rules, ruleSets));

    _classPrivateFieldSet(this, _cssSelectors, _classPrivateFieldGet(this, _config).strategies.cssSelector.selectors);

    _classPrivateFieldSet(this, _ddgMatchers, _classPrivateFieldGet(this, _config).strategies.ddgMatcher.matchers);

    _classPrivateFieldSet(this, _matcherLists, {
      cc: [],
      id: [],
      password: [],
      username: [],
      email: []
    });
    /**
     * Convert the raw config data into actual references.
     *
     * For example this takes `email: ["email"]` and creates
     *
     * `email: [{type: "email", strategies: {cssSelector: "email", ... etc}]`
     */


    for (let [listName, matcherNames] of Object.entries(_classPrivateFieldGet(this, _config).matchers.lists)) {
      for (let fieldName of matcherNames) {
        if (!_classPrivateFieldGet(this, _matcherLists)[listName]) {
          _classPrivateFieldGet(this, _matcherLists)[listName] = [];
        }

        _classPrivateFieldGet(this, _matcherLists)[listName].push(_classPrivateFieldGet(this, _config).matchers.fields[fieldName]);
      }
    }
  }
  /**
   * @param {HTMLInputElement|HTMLSelectElement} input
   * @param {HTMLElement} formEl
   */


  setActiveElementStrings(input, formEl) {
    this.activeElementStrings = this.getElementStrings(input, formEl);
  }
  /**
   * Try to access a 'vendor regex' by name
   * @param {string} regexName
   * @returns {RegExp | undefined}
   */


  vendorRegex(regexName) {
    const match = _classPrivateFieldGet(this, _vendorRegExpCache).RULES[regexName];

    if (!match) {
      console.warn('Vendor Regex not found for', regexName);
      return undefined;
    }

    return match;
  }
  /**
   * Try to access a 'css selector' by name from configuration
   * @param {keyof RequiredCssSelectors | string} selectorName
   * @returns {string};
   */


  cssSelector(selectorName) {
    const match = _classPrivateFieldGet(this, _cssSelectors)[selectorName];

    if (!match) {
      console.warn('CSS selector not found for %s, using a default value', selectorName);
      return '';
    }

    if (Array.isArray(match)) {
      return match.join(',');
    }

    return match;
  }
  /**
   * Try to access a 'ddg matcher' by name from configuration
   * @param {keyof RequiredCssSelectors | string} matcherName
   * @returns {DDGMatcher | undefined}
   */


  ddgMatcher(matcherName) {
    const match = _classPrivateFieldGet(this, _ddgMatchers)[matcherName];

    if (!match) {
      console.warn('DDG matcher not found for', matcherName);
      return undefined;
    }

    return match;
  }
  /**
   * Try to access a list of matchers by name - these are the ones collected in the constructor
   * @param {keyof MatcherLists} listName
   * @return {Matcher[]}
   */


  matcherList(listName) {
    const matcherList = _classPrivateFieldGet(this, _matcherLists)[listName];

    if (!matcherList) {
      console.warn('MatcherList not found for ', listName);
      return [];
    }

    return matcherList;
  }
  /**
   * Convert a list of matchers into a single CSS selector.
   *
   * This will consider all matchers in the list and if it
   * contains a CSS Selector it will be added to the final output
   *
   * @param {keyof MatcherLists} listName
   * @returns {string | undefined}
   */


  joinCssSelectors(listName) {
    const matcherList = this.matcherList(listName);

    if (!matcherList) {
      console.warn('Matcher list not found for', listName);
      return undefined;
    }
    /**
     * @type {string[]}
     */


    const selectors = [];

    for (let matcher of matcherList) {
      if (matcher.strategies.cssSelector) {
        const css = this.cssSelector(matcher.strategies.cssSelector);

        if (css) {
          selectors.push(css);
        }
      }
    }

    return selectors.join(', ');
  }
  /**
   * Tries to infer the input type for an input
   *
   * @param {HTMLInputElement|HTMLSelectElement} input
   * @param {HTMLElement} formEl
   * @param {{isLogin?: boolean}} [opts]
   * @returns {SupportedTypes}
   */


  inferInputType(input, formEl) {
    let opts = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : {};
    const presetType = getInputType(input);

    if (presetType !== 'unknown') {
      return presetType;
    }

    this.setActiveElementStrings(input, formEl); // // For CC forms we run aggressive matches, so we want to make sure we only
    // // run them on actual CC forms to avoid false positives and expensive loops

    if (this.isCCForm(formEl)) {
      const subtype = this.subtypeFromMatchers('cc', input);

      if (subtype && isValidCreditCardSubtype(subtype)) {
        return "creditCards.".concat(subtype);
      }
    }

    if (input instanceof HTMLInputElement) {
      if (this.subtypeFromMatchers('password', input)) {
        return 'credentials.password';
      }

      if (this.subtypeFromMatchers('email', input)) {
        return opts.isLogin ? 'credentials.username' : 'identities.emailAddress';
      }

      if (this.subtypeFromMatchers('username', input)) {
        return 'credentials.username';
      }
    }

    const idSubtype = this.subtypeFromMatchers('id', input);

    if (idSubtype && isValidIdentitiesSubtype(idSubtype)) {
      return "identities.".concat(idSubtype);
    }

    return 'unknown';
  }
  /**
   * Sets the input type as a data attribute to the element and returns it
   * @param {HTMLInputElement} input
   * @param {HTMLElement} formEl
   * @param {{isLogin?: boolean}} [opts]
   * @returns {SupportedSubTypes | string}
   */


  setInputType(input, formEl) {
    let opts = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : {};
    const type = this.inferInputType(input, formEl, opts);
    input.setAttribute(ATTR_INPUT_TYPE, type);
    return type;
  }
  /**
   * Tries to infer input subtype, with checks in decreasing order of reliability
   * @param {keyof MatcherLists} listName
   * @param {HTMLInputElement|HTMLSelectElement} el
   * @return {MatcherTypeNames|undefined}
   */


  subtypeFromMatchers(listName, el) {
    const matchers = this.matcherList(listName);
    /**
     * Loop through each strategy in order
     */

    for (let strategyName of _classPrivateFieldGet(this, _defaultStrategyOrder)) {
      var _result4;

      let result;
      /**
       * Now loop through each matcher in the list.
       */

      for (let matcher of matchers) {
        var _result, _result2, _result3;

        /**
         * for each `strategyName` (such as cssSelector), check
         * if the current matcher implements it.
         */
        const lookup = matcher.strategies[strategyName];
        /**
         * Sometimes a matcher may not implement the current strategy,
         * so we skip it
         */

        if (!lookup) continue;
        /**
         * Now perform the matching
         */

        if (strategyName === 'cssSelector') {
          result = this.execCssSelector(lookup, el);
        }

        if (strategyName === 'ddgMatcher') {
          result = this.execDDGMatcher(lookup);
        }

        if (strategyName === 'vendorRegex') {
          result = this.execVendorRegex(lookup);
        }
        /**
         * If there's a match, return the matcher type.
         *
         * So, for example if 'username' had a `cssSelector` implemented, and
         * it matched the current element, then we'd return 'username'
         */


        if ((_result = result) !== null && _result !== void 0 && _result.matched) {
          return matcher.type;
        }
        /**
         * If a matcher wants to prevent all future matching on this element,
         * it would return { matched: false, proceed: false }
         */


        if (!((_result2 = result) !== null && _result2 !== void 0 && _result2.matched) && ((_result3 = result) === null || _result3 === void 0 ? void 0 : _result3.proceed) === false) {
          // If we get here, do not allow subsequent strategies to continue
          return undefined;
        }
      }

      if ((_result4 = result) !== null && _result4 !== void 0 && _result4.skip) break;
    }

    return undefined;
  }
  /**
   * CSS selector matching just leverages the `.matches` method on elements
   *
   * @param {string} lookup
   * @param {HTMLInputElement|HTMLSelectElement} el
   * @returns {MatchingResult}
   */


  execCssSelector(lookup, el) {
    const selector = this.cssSelector(lookup);
    return {
      matched: el.matches(selector)
    };
  }
  /**
   * A DDG Matcher can have a `match` regex along with a `not` regex. This is done
   * to allow it to be driven by configuration as it avoids needing to invoke custom functions.
   *
   * todo: maxDigits was added as an edge-case when converting this over to be declarative, but I'm
   * unsure if it's actually needed. It's not urgent, but we should consider removing it if that's the case
   *
   * @param {string} lookup
   * @returns {MatchingResult}
   */


  execDDGMatcher(lookup) {
    const ddgMatcher = this.ddgMatcher(lookup);

    if (!ddgMatcher || !ddgMatcher.match) {
      return {
        matched: false
      };
    }

    let matchRexExp = safeRegex(ddgMatcher.match || '');

    if (!matchRexExp) {
      return {
        matched: false
      };
    }

    let requiredScore = ['match', 'forceUnknown', 'maxDigits'].filter(ddgMatcherProp => ddgMatcherProp in ddgMatcher).length;
    /** @type {MatchableStrings[]} */

    const matchableStrings = ddgMatcher.matchableStrings || ['labelText', 'placeholderAttr', 'relatedText'];

    for (let stringName of matchableStrings) {
      let elementString = this.activeElementStrings[stringName];
      if (!elementString) continue;
      elementString = elementString.toLowerCase(); // Scoring to ensure all DDG tests are valid

      let score = 0; // If a negated regex was provided, ensure it does not match
      // If it DOES match - then we need to prevent any future strategies from continuing

      if (ddgMatcher.forceUnknown) {
        let notRegex = safeRegex(ddgMatcher.forceUnknown);

        if (!notRegex) {
          return {
            matched: false
          };
        }

        if (notRegex.test(elementString)) {
          return {
            matched: false,
            proceed: false
          };
        } else {
          // All good here, increment the score
          score++;
        }
      }

      if (ddgMatcher.skip) {
        let skipRegex = safeRegex(ddgMatcher.skip);

        if (!skipRegex) {
          return {
            matched: false
          };
        }

        if (skipRegex.test(elementString)) {
          return {
            matched: false,
            skip: true
          };
        }
      } // if the `match` regex fails, moves onto the next string


      if (!matchRexExp.test(elementString)) {
        continue;
      } // Otherwise, increment the score


      score++; // If a 'maxDigits' rule was provided, validate it

      if (ddgMatcher.maxDigits) {
        const digitLength = elementString.replace(/[^0-9]/g, '').length;

        if (digitLength > ddgMatcher.maxDigits) {
          return {
            matched: false
          };
        } else {
          score++;
        }
      }

      if (score === requiredScore) {
        return {
          matched: true
        };
      }
    }

    return {
      matched: false
    };
  }
  /**
   * If we get here, a firefox/vendor regex was given and we can execute it on the element
   * strings
   * @param {string} lookup
   * @return {MatchingResult}
   */


  execVendorRegex(lookup) {
    const regex = this.vendorRegex(lookup);

    if (!regex) {
      return {
        matched: false
      };
    }
    /** @type {MatchableStrings[]} */


    const stringsToMatch = ['placeholderAttr', 'nameAttr', 'labelText', 'id', 'relatedText'];

    for (let stringName of stringsToMatch) {
      let elementString = this.activeElementStrings[stringName];
      if (!elementString) continue;
      elementString = elementString.toLowerCase();

      if (regex.test(elementString)) {
        return {
          matched: true
        };
      }
    }

    return {
      matched: false
    };
  }
  /**
   * Yield strings in the order in which they should be checked against.
   *
   * Note: some strategies may not want to accept all strings, which is
   * where `matchableStrings` helps. It defaults to when you see below but can
   * be overridden.
   *
   * For example, `nameAttr` is first, since this has the highest chance of matching
   * and then the rest are in decreasing order of value vs cost
   *
   * A generator function is used here to prevent any potentially expensive
   * lookups occurring if they are rare. For example if 90% of all matching never needs
   * to look at the output from `relatedText`, then the cost of computing it will be avoided.
   *
   * @param {HTMLInputElement|HTMLSelectElement} el
   * @param {HTMLElement} form
   * @returns {Record<MatchableStrings, string>}
   */


  getElementStrings(el, form) {
    if (this._elementStringCache.has(el)) {
      return this._elementStringCache.get(el);
    }

    const explicitLabelsText = getExplicitLabelsText(el);
    /** @type {Record<MatchableStrings, string>} */

    const next = {
      nameAttr: el.name,
      labelText: explicitLabelsText,
      placeholderAttr: el.placeholder || '',
      id: el.id,
      relatedText: explicitLabelsText ? '' : getRelatedText(el, form, this.cssSelector('FORM_INPUTS_SELECTOR'))
    };

    this._elementStringCache.set(el, next);

    return next;
  }

  clear() {
    this._elementStringCache = new WeakMap();
  }
  /**
   * @param {HTMLInputElement|HTMLSelectElement} input
   * @param {HTMLElement} form
   * @returns {Matching}
   */


  forInput(input, form) {
    this.setActiveElementStrings(input, form);
    return this;
  }
  /**
   * Tries to infer if it's a credit card form
   * @param {HTMLElement} formEl
   * @returns {boolean}
   */


  isCCForm(formEl) {
    var _formEl$textContent;

    const ccFieldSelector = this.joinCssSelectors('cc');

    if (!ccFieldSelector) {
      return false;
    }

    const hasCCSelectorChild = formEl.querySelector(ccFieldSelector); // If the form contains one of the specific selectors, we have high confidence

    if (hasCCSelectorChild) return true; // Read form attributes to find a signal

    const hasCCAttribute = [...formEl.attributes].some(_ref => {
      let {
        name,
        value
      } = _ref;
      return /(credit|payment).?card/i.test("".concat(name, "=").concat(value));
    });
    if (hasCCAttribute) return true; // Match form textContent against common cc fields (includes hidden labels)

    const textMatches = (_formEl$textContent = formEl.textContent) === null || _formEl$textContent === void 0 ? void 0 : _formEl$textContent.match(/(credit)?card(.?number)?|ccv|security.?code|cvv|cvc|csc/ig); // We check for more than one to minimise false positives

    return Boolean(textMatches && textMatches.length > 1);
  }
  /**
   * @type {MatchingConfiguration}
   */


}
/**
 *  @returns {SupportedTypes}
 */


exports.Matching = Matching;

_defineProperty(Matching, "emptyConfig", {
  matchers: {
    lists: {},
    fields: {}
  },
  strategies: {
    'vendorRegex': {
      rules: {},
      ruleSets: []
    },
    'ddgMatcher': {
      matchers: {}
    },
    'cssSelector': {
      selectors: {
        FORM_INPUTS_SELECTOR: _selectorsCss.FORM_INPUTS_SELECTOR
      }
    }
  }
});

function getInputType(input) {
  const attr = input.getAttribute(ATTR_INPUT_TYPE);

  if (isValidSupportedType(attr)) {
    return attr;
  }

  return 'unknown';
}
/**
 * Retrieves the main type
 * @param {SupportedTypes | string} type
 * @returns {SupportedMainTypes}
 */


function getMainTypeFromType(type) {
  const mainType = type.split('.')[0];

  switch (mainType) {
    case 'credentials':
    case 'creditCards':
    case 'identities':
      return mainType;
  }

  return 'unknown';
}
/**
 * Retrieves the input main type
 * @param {HTMLInputElement} input
 * @returns {SupportedMainTypes}
 */


const getInputMainType = input => getMainTypeFromType(getInputType(input));
/** @typedef {supportedIdentitiesSubtypes[number]} SupportedIdentitiesSubTypes */


exports.getInputMainType = getInputMainType;
const supportedIdentitiesSubtypes =
/** @type {const} */
['emailAddress', 'firstName', 'middleName', 'lastName', 'fullName', 'phone', 'addressStreet', 'addressStreet2', 'addressCity', 'addressProvince', 'addressPostalCode', 'addressCountryCode', 'birthdayDay', 'birthdayMonth', 'birthdayYear'];
/**
 * @param {SupportedTypes | any} supportedType
 * @returns {supportedType is SupportedIdentitiesSubTypes}
 */

function isValidIdentitiesSubtype(supportedType) {
  return supportedIdentitiesSubtypes.includes(supportedType);
}
/** @typedef {supportedCreditCardSubtypes[number]} SupportedCreditCardSubTypes */


const supportedCreditCardSubtypes =
/** @type {const} */
['cardName', 'cardNumber', 'cardSecurityCode', 'expirationMonth', 'expirationYear', 'expiration'];
/**
 * @param {SupportedTypes | any} supportedType
 * @returns {supportedType is SupportedCreditCardSubTypes}
 */

function isValidCreditCardSubtype(supportedType) {
  return supportedCreditCardSubtypes.includes(supportedType);
}
/** @typedef {supportedCredentialsSubtypes[number]} SupportedCredentialsSubTypes */


const supportedCredentialsSubtypes =
/** @type {const} */
['password', 'username'];
/**
 * @param {SupportedTypes | any} supportedType
 * @returns {supportedType is SupportedCredentialsSubTypes}
 */

function isValidCredentialsSubtype(supportedType) {
  return supportedCredentialsSubtypes.includes(supportedType);
}
/** @typedef {SupportedIdentitiesSubTypes | SupportedCreditCardSubTypes | SupportedCredentialsSubTypes} SupportedSubTypes */

/** @typedef {`identities.${SupportedIdentitiesSubTypes}` | `creditCards.${SupportedCreditCardSubTypes}` | `credentials.${SupportedCredentialsSubTypes}` | 'unknown'} SupportedTypes */


const supportedTypes = [...supportedIdentitiesSubtypes.map(type => "identities.".concat(type)), ...supportedCreditCardSubtypes.map(type => "creditCards.".concat(type)), ...supportedCredentialsSubtypes.map(type => "credentials.".concat(type))];
/**
 * Retrieves the subtype
 * @param {SupportedTypes | string} type
 * @returns {SupportedSubTypes | 'unknown'}
 */

function getSubtypeFromType(type) {
  const subType = type === null || type === void 0 ? void 0 : type.split('.')[1];
  const validType = isValidSubtype(subType);
  return validType ? subType : 'unknown';
}
/**
 * @param {SupportedSubTypes | any} supportedSubType
 * @returns {supportedSubType is SupportedSubTypes}
 */


function isValidSubtype(supportedSubType) {
  return isValidIdentitiesSubtype(supportedSubType) || isValidCreditCardSubtype(supportedSubType) || isValidCredentialsSubtype(supportedSubType);
}
/**
 * @param {SupportedTypes | any} supportedType
 * @returns {supportedType is SupportedTypes}
 */


function isValidSupportedType(supportedType) {
  return supportedTypes.includes(supportedType);
}
/**
 * Retrieves the input subtype
 * @param {HTMLInputElement|Element} input
 * @returns {SupportedSubTypes | 'unknown'}
 */


function getInputSubtype(input) {
  const type = getInputType(input);
  return getSubtypeFromType(type);
}
/**
 * Remove whitespace of more than 2 in a row and trim the string
 * @param {string | null} string
 * @return {string}
 */


const removeExcessWhitespace = function () {
  let string = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : '';
  return (string || '').replace(/\n/g, ' ').replace(/\s{2,}/, ' ').trim();
};
/**
 * Get text from all explicit labels
 * @param {HTMLInputElement|HTMLSelectElement} el
 * @return {string}
 */


exports.removeExcessWhitespace = removeExcessWhitespace;

const getExplicitLabelsText = el => {
  const labelTextCandidates = [];

  for (let label of el.labels || []) {
    labelTextCandidates.push(...(0, _labelUtil.extractElementStrings)(label));
  }

  if (el.hasAttribute('aria-label')) {
    labelTextCandidates.push(removeExcessWhitespace(el.getAttribute('aria-label')));
  } // Try to access another element if it was marked as the label for this input/select


  const ariaLabelAttr = removeExcessWhitespace(el.getAttribute('aria-labelled') || el.getAttribute('aria-labelledby'));

  if (ariaLabelAttr) {
    const labelledByElement = document.getElementById(ariaLabelAttr);

    if (labelledByElement) {
      labelTextCandidates.push(...(0, _labelUtil.extractElementStrings)(labelledByElement));
    }
  } // Labels with long text are likely to be noisy and lead to false positives


  const filteredLabels = labelTextCandidates.filter(string => string.length < 65);

  if (filteredLabels.length > 0) {
    return filteredLabels.join(' ');
  }

  return '';
};
/**
 * Get all text close to the input (useful when no labels are defined)
 * @param {HTMLInputElement|HTMLSelectElement} el
 * @param {HTMLElement} form
 * @param {string} cssSelector
 * @return {string}
 */


exports.getExplicitLabelsText = getExplicitLabelsText;

const getRelatedText = (el, form, cssSelector) => {
  let scope = getLargestMeaningfulContainer(el, form, cssSelector); // If we didn't find a container, try looking for an adjacent label

  if (scope === el) {
    if (el.previousElementSibling instanceof HTMLLabelElement) {
      scope = el.previousElementSibling;
    }
  } // If there is still no meaningful container return empty string


  if (scope === el || scope.nodeName === 'SELECT') return ''; // If the container has a select element, remove its contents to avoid noise

  const text = removeExcessWhitespace((0, _labelUtil.extractElementStrings)(scope).join(' ')); // If the text is longer than n chars it's too noisy and likely to yield false positives, so return ''

  if (text.length < TEXT_LENGTH_CUTOFF) return text;
  return '';
};
/**
 * Find a container for the input field that won't contain other inputs (useful to get elements related to the field)
 * @param {HTMLElement} el
 * @param {HTMLElement} form
 * @param {string} cssSelector
 * @return {HTMLElement}
 */


exports.getRelatedText = getRelatedText;

const getLargestMeaningfulContainer = (el, form, cssSelector) => {
  /* TODO: there could be more than one select el for the same label, in that case we should
      change how we compute the container */
  const parentElement = el.parentElement;
  if (!parentElement || el === form) return el;
  const inputsInParentsScope = parentElement.querySelectorAll(cssSelector); // To avoid noise, ensure that our input is the only in scope

  if (inputsInParentsScope.length === 1) {
    return getLargestMeaningfulContainer(parentElement, form, cssSelector);
  }

  return el;
};
/**
 * Find a regex match for a given input
 * @param {HTMLInputElement} input
 * @param {RegExp} regex
 * @param {HTMLElement} form
 * @param {string} cssSelector
 * @returns {RegExpMatchArray|null}
 */


const matchInPlaceholderAndLabels = (input, regex, form, cssSelector) => {
  var _input$placeholder;

  return ((_input$placeholder = input.placeholder) === null || _input$placeholder === void 0 ? void 0 : _input$placeholder.match(regex)) || getExplicitLabelsText(input).match(regex) || getRelatedText(input, form, cssSelector).match(regex);
};
/**
 * Check if a given input matches a regex
 * @param {HTMLInputElement} input
 * @param {RegExp} regex
 * @param {HTMLElement} form
 * @param {string} cssSelector
 * @returns {boolean}
 */


exports.matchInPlaceholderAndLabels = matchInPlaceholderAndLabels;

const checkPlaceholderAndLabels = (input, regex, form, cssSelector) => {
  return !!matchInPlaceholderAndLabels(input, regex, form, cssSelector);
};
/**
 * Creating Regex instances can throw, so we add this to be
 * @param {string} string
 * @returns {RegExp | undefined} string
 */


exports.checkPlaceholderAndLabels = checkPlaceholderAndLabels;

const safeRegex = string => {
  try {
    // This is lower-cased here because giving a `i` on a regex flag is a performance problem in some cases
    const input = String(string).toLowerCase().normalize('NFKC');
    return new RegExp(input, 'u');
  } catch (e) {
    console.warn('Could not generate regex from string input', string);
    return undefined;
  }
};
/**
 * Factory for instances of Matching
 *
 * @return {Matching}
 */


exports.safeRegex = safeRegex;

function createMatching() {
  return new Matching(_matchingConfiguration.matchingConfiguration);
}

},{"../constants":49,"./label-util":22,"./matching-configuration":25,"./selectors-css":27,"./vendor-regex":28}],27:[function(require,module,exports){
"use strict";

const FORM_INPUTS_SELECTOR = "\ninput:not([type=submit]):not([type=button]):not([type=checkbox]):not([type=radio]):not([type=hidden]):not([type=file]):not([name^=fake i]):not([data-description^=dummy i]),\nselect";
const SUBMIT_BUTTON_SELECTOR = "\ninput[type=submit],\ninput[type=button],\nbutton:not([role=switch]):not([role=link]),\n[role=button]";
const email = "\ninput:not([type])[name*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput[type=\"\"][name*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput[type=text][name*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]):not([name*=title i]):not([name*=tab i]),\ninput:not([type])[placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput[type=text][placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput[type=\"\"][placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput:not([type])[placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput[type=email],\ninput[type=text][aria-label*=mail i]:not([aria-label*=search i]),\ninput:not([type])[aria-label*=mail i]:not([aria-label*=search i]),\ninput[type=text][placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput[name=username][type=email],\ninput[autocomplete=email]"; // We've seen non-standard types like 'user'. This selector should get them, too

const GENERIC_TEXT_FIELD = "\ninput:not([type=button]):not([type=checkbox]):not([type=color]):not([type=date]):not([type=datetime-local]):not([type=datetime]):not([type=file]):not([type=hidden]):not([type=month]):not([type=number]):not([type=radio]):not([type=range]):not([type=reset]):not([type=search]):not([type=submit]):not([type=time]):not([type=url]):not([type=week])";
const password = "input[type=password]:not([autocomplete*=cc]):not([autocomplete=one-time-code]):not([name*=answer i]):not([name*=mfa i]):not([name*=tin i])";
const cardName = "\ninput[autocomplete=\"cc-name\"],\ninput[autocomplete=\"ccname\"],\ninput[name=\"ccname\"],\ninput[name=\"cc-name\"],\ninput[name=\"ppw-accountHolderName\"],\ninput[id*=cardname i],\ninput[id*=card-name i],\ninput[id*=card_name i]";
const cardNumber = "\ninput[autocomplete=\"cc-number\"],\ninput[autocomplete=\"ccnumber\"],\ninput[autocomplete=\"cardnumber\"],\ninput[autocomplete=\"card-number\"],\ninput[name=\"ccnumber\"],\ninput[name=\"cc-number\"],\ninput[name*=card i][name*=number i],\ninput[id*=cardnumber i],\ninput[id*=card-number i],\ninput[id*=card_number i]";
const cardSecurityCode = "\ninput[autocomplete=\"cc-csc\"],\ninput[autocomplete=\"csc\"],\ninput[autocomplete=\"cc-cvc\"],\ninput[autocomplete=\"cvc\"],\ninput[name=\"cvc\"],\ninput[name=\"cc-cvc\"],\ninput[name=\"cc-csc\"],\ninput[name=\"csc\"],\ninput[name*=security i][name*=code i]";
const expirationMonth = "\n[autocomplete=\"cc-exp-month\"],\n[name=\"ccmonth\"],\n[name=\"ppw-expirationDate_month\"],\n[name=cardExpiryMonth],\n[name*=ExpDate_Month i],\n[name*=expiration i][name*=month i],\n[id*=expiration i][id*=month i]";
const expirationYear = "\n[autocomplete=\"cc-exp-year\"],\n[name=\"ccyear\"],\n[name=\"ppw-expirationDate_year\"],\n[name=cardExpiryYear],\n[name*=ExpDate_Year i],\n[name*=expiration i][name*=year i],\n[id*=expiration i][id*=year i]";
const expiration = "\n[autocomplete=\"cc-exp\"],\n[name=\"cc-exp\"],\n[name=\"exp-date\"],\n[name=\"expirationDate\"],\ninput[id*=expiration i]";
const firstName = "\n[name*=fname i], [autocomplete*=given-name i],\n[name*=firstname i], [autocomplete*=firstname i],\n[name*=first-name i], [autocomplete*=first-name i],\n[name*=first_name i], [autocomplete*=first_name i],\n[name*=givenname i], [autocomplete*=givenname i],\n[name*=given-name i],\n[name*=given_name i], [autocomplete*=given_name i],\n[name*=forename i], [autocomplete*=forename i]";
const middleName = "\n[name*=mname i], [autocomplete*=additional-name i],\n[name*=middlename i], [autocomplete*=middlename i],\n[name*=middle-name i], [autocomplete*=middle-name i],\n[name*=middle_name i], [autocomplete*=middle_name i],\n[name*=additionalname i], [autocomplete*=additionalname i],\n[name*=additional-name i],\n[name*=additional_name i], [autocomplete*=additional_name i]";
const lastName = "\n[name=lname], [autocomplete*=family-name i],\n[name*=lastname i], [autocomplete*=lastname i],\n[name*=last-name i], [autocomplete*=last-name i],\n[name*=last_name i], [autocomplete*=last_name i],\n[name*=familyname i], [autocomplete*=familyname i],\n[name*=family-name i],\n[name*=family_name i], [autocomplete*=family_name i],\n[name*=surname i], [autocomplete*=surname i]";
const fullName = "\n[name=name], [autocomplete=name],\n[name*=fullname i], [autocomplete*=fullname i],\n[name*=full-name i], [autocomplete*=full-name i],\n[name*=full_name i], [autocomplete*=full_name i],\n[name*=your-name i], [autocomplete*=your-name i]";
const phone = "\n[name*=phone i]:not([name*=extension i]):not([name*=type i]):not([name*=country i]), [name*=mobile i]:not([name*=type i]), [autocomplete=tel], [placeholder*=\"phone number\" i]";
const addressStreet1 = "\n[name=address], [autocomplete=street-address], [autocomplete=address-line1],\n[name=street],\n[name=ppw-line1], [name*=addressLine1 i]";
const addressStreet2 = "\n[name=address], [autocomplete=address-line2],\n[name=ppw-line2], [name*=addressLine2 i]";
const addressCity = "\n[name=city], [autocomplete=address-level2],\n[name=ppw-city], [name*=addressCity i]";
const addressProvince = "\n[name=province], [name=state], [autocomplete=address-level1]";
const addressPostalCode = "\n[name=zip], [name=zip2], [name=postal], [autocomplete=postal-code], [autocomplete=zip-code],\n[name*=postalCode i], [name*=zipcode i]";
const addressCountryCode = ["[name=country], [autocomplete=country],\n     [name*=countryCode i], [name*=country-code i],\n     [name*=countryName i], [name*=country-name i]", "select.idms-address-country" // Fix for Apple signup
];
const birthdayDay = "\n[name=bday-day],\n[name=birthday_day], [name=birthday-day],\n[name=date_of_birth_day], [name=date-of-birth-day],\n[name^=birthdate_d], [name^=birthdate-d],\n[aria-label=\"birthday\" i][placeholder=\"day\" i]";
const birthdayMonth = "\n[name=bday-month],\n[name=birthday_month], [name=birthday-month],\n[name=date_of_birth_month], [name=date-of-birth-month],\n[name^=birthdate_m], [name^=birthdate-m],\nselect[name=\"mm\"]";
const birthdayYear = "\n[name=bday-year],\n[name=birthday_year], [name=birthday-year],\n[name=date_of_birth_year], [name=date-of-birth-year],\n[name^=birthdate_y], [name^=birthdate-y],\n[aria-label=\"birthday\" i][placeholder=\"year\" i]";
const username = ["".concat(GENERIC_TEXT_FIELD, "[autocomplete^=user]"), "input[name=username i]", // fix for `aa.com`
"input[name=\"loginId\" i]", // fix for https://online.mbank.pl/pl/Login
"input[name=\"userID\" i]", "input[id=\"login-id\" i]", "input[name=accountname i]", "input[autocomplete=username]"]; // todo: these are still used directly right now, mostly in scanForInputs
// todo: ensure these can be set via configuration

module.exports.FORM_INPUTS_SELECTOR = FORM_INPUTS_SELECTOR;
module.exports.SUBMIT_BUTTON_SELECTOR = SUBMIT_BUTTON_SELECTOR; // Exported here for now, to be moved to configuration later

module.exports.__secret_do_not_use = {
  GENERIC_TEXT_FIELD,
  SUBMIT_BUTTON_SELECTOR,
  FORM_INPUTS_SELECTOR,
  email: email,
  password,
  username,
  cardName,
  cardNumber,
  cardSecurityCode,
  expirationMonth,
  expirationYear,
  expiration,
  firstName,
  middleName,
  lastName,
  fullName,
  phone,
  addressStreet1,
  addressStreet2,
  addressCity,
  addressProvince,
  addressPostalCode,
  addressCountryCode,
  birthdayDay,
  birthdayMonth,
  birthdayYear
};

},{}],28:[function(require,module,exports){
"use strict";

/**
 * Given some ruleSets, create an efficient
 * lookup system for accessing cached regexes by name.
 *
 * @param {VendorRegexConfiguration["rules"]} rules
 * @param {VendorRegexConfiguration["ruleSets"]} ruleSets
 * @return {{RULES: Record<keyof VendorRegexRules, RegExp | undefined>}}
 */
function createCacheableVendorRegexes(rules, ruleSets) {
  const vendorRegExp = {
    RULES: rules,
    RULE_SETS: ruleSets,

    _getRule(name) {
      let rules = [];
      this.RULE_SETS.forEach(set => {
        if (set[name]) {
          var _set$name;

          // Add the rule.
          // We make the regex lower case so that we can match it against the
          // lower-cased field name and get a rough equivalent of a case-insensitive
          // match. This avoids a performance cliff with the "iu" flag on regular
          // expressions.
          rules.push("(".concat((_set$name = set[name]) === null || _set$name === void 0 ? void 0 : _set$name.toLowerCase(), ")").normalize('NFKC'));
        }
      });
      const value = new RegExp(rules.join('|'), 'u');
      Object.defineProperty(this.RULES, name, {
        get: undefined
      });
      Object.defineProperty(this.RULES, name, {
        value
      });
      return value;
    },

    init() {
      Object.keys(this.RULES).forEach(field => Object.defineProperty(this.RULES, field, {
        get() {
          return vendorRegExp._getRule(field);
        }

      }));
    }

  };
  vendorRegExp.init(); // @ts-ignore

  return vendorRegExp;
}

module.exports.createCacheableVendorRegexes = createCacheableVendorRegexes;

},{}],29:[function(require,module,exports){
"use strict";

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

const AUTOGENERATED_KEY = 'autogenerated';
/**
 * @implements {TooltipItemRenderer}
 */

var _data = /*#__PURE__*/new WeakMap();

class CredentialsTooltipItem {
  /** @type {CredentialsObject} */

  /** @param {CredentialsObject} data */
  constructor(data) {
    _classPrivateFieldInitSpec(this, _data, {
      writable: true,
      value: void 0
    });

    _defineProperty(this, "id", () => String(_classPrivateFieldGet(this, _data).id));

    _defineProperty(this, "labelMedium", _subtype => _classPrivateFieldGet(this, _data).username);

    _defineProperty(this, "labelSmall", _subtype => '•••••••••••••••');

    _classPrivateFieldSet(this, _data, data);
  }

}
/**
 * @implements {TooltipItemRenderer}
 */


var _data2 = /*#__PURE__*/new WeakMap();

class AutoGeneratedCredential {
  /** @type {CredentialsObject} */

  /** @param {CredentialsObject} data */
  constructor(data) {
    _classPrivateFieldInitSpec(this, _data2, {
      writable: true,
      value: void 0
    });

    _defineProperty(this, "id", () => String(_classPrivateFieldGet(this, _data2).id));

    _defineProperty(this, "label", _subtype => _classPrivateFieldGet(this, _data2).password);

    _defineProperty(this, "labelMedium", _subtype => 'Generated password');

    _defineProperty(this, "labelSmall", _subtype => 'Login information will be saved for this website');

    _classPrivateFieldSet(this, _data2, data);
  }

}
/**
 * Generate a stand-in 'CredentialsObject' from a
 * given (generated) password.
 *
 * @param {string} password
 * @returns {CredentialsObject}
 */


function fromPassword(password) {
  return {
    [AUTOGENERATED_KEY]: true,
    password: password,
    username: ''
  };
}
/**
 * If the locally generated/stored password ends up being the same
 * as submitted in a subsequent form submission - then we mark the
 * credentials as 'autogenerated' so that the native layer can decide
 * how to process it
 *
 * @type {PreRequest<DataStorageObject, string|null>}
 */


function appendGeneratedId(data, generatedPassword) {
  var _data$credentials;

  if (generatedPassword && ((_data$credentials = data.credentials) === null || _data$credentials === void 0 ? void 0 : _data$credentials.password) === generatedPassword) {
    return { ...data,
      credentials: { ...data.credentials,
        [AUTOGENERATED_KEY]: true
      }
    };
  }

  return data;
}
/**
 * Factory for creating a TooltipItemRenderer
 *
 * @param {CredentialsObject} data
 * @returns {TooltipItemRenderer}
 */


function createCredentialsTooltipItem(data) {
  if (AUTOGENERATED_KEY in data && data.password) {
    return new AutoGeneratedCredential(data);
  }

  return new CredentialsTooltipItem(data);
}

module.exports.createCredentialsTooltipItem = createCredentialsTooltipItem;
module.exports.fromPassword = fromPassword;
module.exports.appendGeneratedId = appendGeneratedId;
module.exports.AUTOGENERATED_KEY = AUTOGENERATED_KEY;

},{}],30:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.CreditCardTooltipItem = void 0;

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

var _data = /*#__PURE__*/new WeakMap();

/**
 * @implements {TooltipItemRenderer}
 */
class CreditCardTooltipItem {
  /** @type {CreditCardObject} */

  /** @param {CreditCardObject} data */
  constructor(data) {
    _classPrivateFieldInitSpec(this, _data, {
      writable: true,
      value: void 0
    });

    _defineProperty(this, "id", () => String(_classPrivateFieldGet(this, _data).id));

    _defineProperty(this, "labelMedium", _ => _classPrivateFieldGet(this, _data).title);

    _defineProperty(this, "labelSmall", _ => _classPrivateFieldGet(this, _data).displayNumber);

    _classPrivateFieldSet(this, _data, data);
  }

}

exports.CreditCardTooltipItem = CreditCardTooltipItem;

},{}],31:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.IdentityTooltipItem = void 0;

var _formatters = require("../Form/formatters");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

var _data = /*#__PURE__*/new WeakMap();

/**
 * @implements {TooltipItemRenderer}
 */
class IdentityTooltipItem {
  /** @type {IdentityObject} */

  /** @param {IdentityObject} data */
  constructor(data) {
    _classPrivateFieldInitSpec(this, _data, {
      writable: true,
      value: void 0
    });

    _defineProperty(this, "id", () => String(_classPrivateFieldGet(this, _data).id));

    _defineProperty(this, "labelMedium", subtype => {
      if (subtype === 'addressCountryCode') {
        return (0, _formatters.getCountryDisplayName)('en', _classPrivateFieldGet(this, _data).addressCountryCode || '');
      }

      if (_classPrivateFieldGet(this, _data).id === 'privateAddress') {
        return 'Generated Private Duck Address';
      }

      return _classPrivateFieldGet(this, _data)[subtype];
    });

    _defineProperty(this, "labelSmall", _ => {
      return _classPrivateFieldGet(this, _data).title;
    });

    _classPrivateFieldSet(this, _data, data);
  }

  label(subtype) {
    if (_classPrivateFieldGet(this, _data).id === 'privateAddress') {
      return _classPrivateFieldGet(this, _data)[subtype];
    }

    return null;
  }

}

exports.IdentityTooltipItem = IdentityTooltipItem;

},{"../Form/formatters":19}],32:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.PasswordGenerator = void 0;

var _password = require("../packages/password");

var _rules = _interopRequireDefault(require("../packages/password/rules.json"));

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

var _previous = /*#__PURE__*/new WeakMap();

/**
 * Create a password once and reuse it.
 */
class PasswordGenerator {
  constructor() {
    _classPrivateFieldInitSpec(this, _previous, {
      writable: true,
      value: null
    });
  }

  /** @returns {boolean} */
  get generated() {
    return _classPrivateFieldGet(this, _previous) !== null;
  }
  /** @returns {string|null} */


  get password() {
    return _classPrivateFieldGet(this, _previous);
  }
  /** @param {import('../packages/password').GenerateOptions} [params] */


  generate() {
    let params = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {};

    if (_classPrivateFieldGet(this, _previous)) {
      return _classPrivateFieldGet(this, _previous);
    }

    _classPrivateFieldSet(this, _previous, (0, _password.generate)({ ...params,
      rules: _rules.default
    }));

    return _classPrivateFieldGet(this, _previous);
  }

}

exports.PasswordGenerator = PasswordGenerator;

},{"../packages/password":5,"../packages/password/rules.json":9}],33:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.createScanner = createScanner;

var _Form = require("./Form/Form");

var _autofillUtils = require("./autofill-utils");

var _selectorsCss = require("./Form/selectors-css");

var _matching = require("./Form/matching");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * @typedef {{
 *     forms: Map<HTMLElement, import("./Form/Form").Form>;
 *     init(): ()=> void;
 *     enqueue(elements: (HTMLElement|Document)[]): void;
 *     findEligibleInputs(context): Scanner;
 * }} Scanner
 *
 * @typedef {{
 *     initialDelay: number,
 *     bufferSize: number,
 *     debounceTimePeriod: number,
 * }} ScannerOptions
 */

/** @type {ScannerOptions} */
const defaultScannerOptions = {
  // This buffer size is very large because it's an unexpected edge-case that
  // a DOM will be continually modified over and over without ever stopping. If we do see 1000 unique
  // new elements in the buffer however then this will prevent the algorithm from never ending.
  bufferSize: 50,
  // wait for a 500ms window of event silence before performing the scan
  debounceTimePeriod: 500,
  // how long to wait when performing the initial scan
  initialDelay: 0
};
/**
 * This allows:
 *   1) synchronous DOM scanning + mutations - via `createScanner(device).findEligibleInputs(document)`
 *   2) or, as above + a debounced mutation observer to re-run the scan after the given time
 */

class DefaultScanner {
  /** @type Map<HTMLElement, Form> */

  /** @type {any|undefined} the timer to reset */

  /** @type {Set<HTMLElement|Document>} stored changed elements until they can be processed */

  /** @type {ScannerOptions} */

  /** @type {HTMLInputElement | null} */

  /** @type {boolean} A flag to indicate the whole page will be re-scanned */

  /**
   * @param {import("./DeviceInterface/InterfacePrototype").default} device
   * @param {ScannerOptions} options
   */
  constructor(device, options) {
    _defineProperty(this, "forms", new Map());

    _defineProperty(this, "debounceTimer", void 0);

    _defineProperty(this, "changedElements", new Set());

    _defineProperty(this, "options", void 0);

    _defineProperty(this, "activeInput", null);

    _defineProperty(this, "rescanAll", false);

    _defineProperty(this, "mutObs", new MutationObserver(mutationList => {
      /** @type {HTMLElement[]} */
      if (this.rescanAll) {
        // quick version if buffer full
        this.enqueue([]);
        return;
      }

      const outgoing = [];

      for (const mutationRecord of mutationList) {
        if (mutationRecord.type === 'childList') {
          for (let addedNode of mutationRecord.addedNodes) {
            if (!(addedNode instanceof HTMLElement)) continue;
            if (addedNode.nodeName === 'DDG-AUTOFILL') continue;
            outgoing.push(addedNode);
          }
        }
      }

      this.enqueue(outgoing);
    }));

    this.device = device;
    this.matching = (0, _matching.createMatching)();
    this.options = options;
  }
  /**
   * Call this to scan once and then watch for changes.
   *
   * Call the returned function to remove listeners.
   * @returns {() => void}
   */


  init() {
    const delay = this.options.initialDelay; // if the delay is zero, (chrome/firefox etc) then use `requestIdleCallback`

    if (delay === 0) {
      window.requestIdleCallback(() => this.scanAndObserve());
    } else {
      // otherwise, use the delay time to defer the initial scan
      setTimeout(() => this.scanAndObserve(), delay);
    }

    return () => {
      // remove Dax, listeners, timers, and observers
      clearTimeout(this.debounceTimer);
      this.mutObs.disconnect();
      this.forms.forEach(form => {
        form.resetAllInputs();
        form.removeAllDecorations();
      });
      this.forms.clear();

      if (this.device.globalConfig.isDDGDomain) {
        (0, _autofillUtils.notifyWebApp)({
          deviceSignedIn: {
            value: false
          }
        });
      }
    };
  }
  /**
   * Scan the page and begin observing changes
   */


  scanAndObserve() {
    var _window$performance, _window$performance$m, _window$performance2, _window$performance2$;

    (_window$performance = window.performance) === null || _window$performance === void 0 ? void 0 : (_window$performance$m = _window$performance.mark) === null || _window$performance$m === void 0 ? void 0 : _window$performance$m.call(_window$performance, 'scanner:init:start');
    this.findEligibleInputs(document);
    (_window$performance2 = window.performance) === null || _window$performance2 === void 0 ? void 0 : (_window$performance2$ = _window$performance2.mark) === null || _window$performance2$ === void 0 ? void 0 : _window$performance2$.call(_window$performance2, 'scanner:init:end');
    this.mutObs.observe(document.body, {
      childList: true,
      subtree: true
    });
  }
  /**
   * @param context
   */


  findEligibleInputs(context) {
    var _context$matches;

    if ('matches' in context && (_context$matches = context.matches) !== null && _context$matches !== void 0 && _context$matches.call(context, _selectorsCss.FORM_INPUTS_SELECTOR)) {
      this.addInput(context);
    } else {
      context.querySelectorAll(_selectorsCss.FORM_INPUTS_SELECTOR).forEach(input => this.addInput(input));
    }

    return this;
  }
  /**
   * @param {HTMLElement|HTMLInputElement|HTMLSelectElement} input
   * @returns {HTMLFormElement|HTMLElement}
   */


  getParentForm(input) {
    if (input instanceof HTMLInputElement || input instanceof HTMLSelectElement) {
      if (input.form) return input.form;
    }

    let element = input; // traverse the DOM to search for related inputs

    while (element.parentElement && element.parentElement !== document.body) {
      element = element.parentElement; // todo: These selectors should be configurable

      const inputs = element.querySelectorAll(_selectorsCss.FORM_INPUTS_SELECTOR);
      const buttons = element.querySelectorAll(_selectorsCss.SUBMIT_BUTTON_SELECTOR); // If we find a button or another input, we assume that's our form

      if (inputs.length > 1 || buttons.length) {
        // found related input, return common ancestor
        return element;
      }
    }

    return input;
  }
  /**
   * @param {HTMLInputElement|HTMLSelectElement} input
   */


  addInput(input) {
    const parentForm = this.getParentForm(input); // Note that el.contains returns true for el itself

    const previouslyFoundParent = [...this.forms.keys()].find(form => form.contains(parentForm));

    if (previouslyFoundParent) {
      var _this$forms$get;

      // If we've already met the form or a descendant, add the input
      (_this$forms$get = this.forms.get(previouslyFoundParent)) === null || _this$forms$get === void 0 ? void 0 : _this$forms$get.addInput(input);
    } else {
      // if this form is an ancestor of an existing form, remove that before adding this
      const childForm = [...this.forms.keys()].find(form => parentForm.contains(form));

      if (childForm) {
        var _this$forms$get2;

        (_this$forms$get2 = this.forms.get(childForm)) === null || _this$forms$get2 === void 0 ? void 0 : _this$forms$get2.destroy();
        this.forms.delete(childForm);
      }

      this.forms.set(parentForm, new _Form.Form(parentForm, input, this.device, this.matching));
    }
  }
  /**
   * enqueue elements to be re-scanned after the given
   * amount of time has elapsed.
   *
   * @param {(HTMLElement|Document)[]} htmlElements
   */


  enqueue(htmlElements) {
    // if the buffer limit is reached, stop trying to track elements and process body instead.
    if (this.changedElements.size >= this.options.bufferSize) {
      this.rescanAll = true;
      this.changedElements.clear();
    } else if (!this.rescanAll) {
      // otherwise keep adding each element to the queue
      for (let element of htmlElements) {
        this.changedElements.add(element);
      }
    }

    clearTimeout(this.debounceTimer);
    this.debounceTimer = setTimeout(() => {
      this.processChangedElements();
      this.changedElements.clear();
      this.rescanAll = false;
    }, this.options.debounceTimePeriod);
  }
  /**
   * re-scan the changed elements, but only if they
   * are still present in the DOM
   */


  processChangedElements() {
    if (this.rescanAll) {
      this.findEligibleInputs(document);
      return;
    }

    for (let element of this.changedElements) {
      if (element.isConnected) {
        this.findEligibleInputs(element);
      }
    }
  }
  /**
   * Watch for changes in the DOM, and enqueue elements to be scanned
   * @type {MutationObserver}
   */


}
/**
 * @param {import("./DeviceInterface/InterfacePrototype").default} device
 * @param {Partial<ScannerOptions>} [scannerOptions]
 * @returns {Scanner}
 */


function createScanner(device, scannerOptions) {
  return new DefaultScanner(device, { ...defaultScannerOptions,
    ...scannerOptions
  });
}

},{"./Form/Form":16,"./Form/matching":26,"./Form/selectors-css":27,"./autofill-utils":46}],34:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.Settings = void 0;

var _deviceApi = require("../packages/device-api");

var _deviceApiCalls = require("./deviceApiCalls/__generated__/deviceApiCalls");

var _validators = require("./deviceApiCalls/__generated__/validators.zod");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * Some Type helpers to prevent duplication
 * @typedef {import("./deviceApiCalls/__generated__/validators-ts").AutofillFeatureToggles} AutofillFeatureToggles
 * @typedef {import("./deviceApiCalls/__generated__/validators-ts").AvailableInputTypes} AvailableInputTypes
 * @typedef {import("../packages/device-api").DeviceApi} DeviceApi
 */

/**
 * The Settings class encapsulates the concept of 1) feature toggles + 2) available input types.
 *
 * 1) Feature toggles are boolean flags that can represent a device's capabilities. That may be user-toggled
 * or not, we don't make that distinction.
 *
 * 2) Available Input Types are indicators to whether the given platform can provide data for a given type.
 * For example, a user might have credentials saved for https://example.com, so when the page loads, but **before**
 * we can decorate any fields, we determine this first.
 */
class Settings {
  /** @type {GlobalConfig} */

  /** @type {DeviceApi} */

  /** @type {AutofillFeatureToggles | null} */

  /** @type {AvailableInputTypes | null} */

  /**
   * @param {GlobalConfig} config
   * @param {DeviceApi} deviceApi
   */
  constructor(config, deviceApi) {
    _defineProperty(this, "globalConfig", void 0);

    _defineProperty(this, "deviceApi", void 0);

    _defineProperty(this, "_featureToggles", null);

    _defineProperty(this, "_availableInputTypes", null);

    this.deviceApi = deviceApi;
    this.globalConfig = config;
  }
  /**
   * Feature toggles are delivered as part of the Runtime Configuration - a flexible design that
   * allows data per user + remote config to be accessed together.
   *
   * Once we access the Runtime Configuration, we then extract the autofill-specific settings via
   * `runtimeConfig.userPreferences.features.autofill.settings` and validate that separately.
   *
   * The 2-step validation occurs because RuntimeConfiguration will be coming from a shared library
   * and does not know about the shape of Autofill specific settings.
   *
   * @returns {Promise<AutofillFeatureToggles>}
   */


  async getFeatureToggles() {
    try {
      var _runtimeConfig$userPr, _runtimeConfig$userPr2, _runtimeConfig$userPr3;

      const runtimeConfig = await this.deviceApi.request(new _deviceApiCalls.GetRuntimeConfigurationCall(null));
      const autofillSettings = (0, _deviceApi.validate)(runtimeConfig === null || runtimeConfig === void 0 ? void 0 : (_runtimeConfig$userPr = runtimeConfig.userPreferences) === null || _runtimeConfig$userPr === void 0 ? void 0 : (_runtimeConfig$userPr2 = _runtimeConfig$userPr.features) === null || _runtimeConfig$userPr2 === void 0 ? void 0 : (_runtimeConfig$userPr3 = _runtimeConfig$userPr2.autofill) === null || _runtimeConfig$userPr3 === void 0 ? void 0 : _runtimeConfig$userPr3.settings, _validators.autofillSettingsSchema);
      return autofillSettings.featureToggles;
    } catch (e) {
      // these are the fallbacks for when a platform hasn't implemented the calls above. (like on android)
      if (this.globalConfig.isDDGTestMode) {
        console.log('isDDGTestMode: getFeatureToggles: ❌', e);
      }

      return Settings.defaults.featureToggles;
    }
  }
  /**
   * Available Input Types are boolean indicators to represent which input types the
   * current **user** has data available for.
   *
   * @returns {Promise<AvailableInputTypes>}
   */


  async getAvailableInputTypes() {
    try {
      return await this.deviceApi.request(new _deviceApiCalls.GetAvailableInputTypesCall(null));
    } catch (e) {
      if (this.globalConfig.isDDGTestMode) {
        console.log('isDDGTestMode: getAvailableInputTypes: ❌', e);
      }

      return Settings.defaults.availableInputTypes;
    }
  }
  /**
   * To 'refresh' settings means to re-call APIs to determine new state. This may
   * only occur once per page, but it must be done before any page scanning/decorating can happen
   *
   * @returns {Promise<{
   *      availableInputTypes: AvailableInputTypes,
   *      featureToggles: AutofillFeatureToggles
   * }>}
   * @param {AvailableInputTypes} [availableInputTypesOverrides] a migration aid so that macOS can provide data in its old way initially
   */


  async refresh(availableInputTypesOverrides) {
    this.setFeatureToggles(await this.getFeatureToggles());
    const availableInputTypesFromRemote = await this.getAvailableInputTypes();
    /** @type {AvailableInputTypes} */

    const availableInputTypes = {
      email: false,
      // not supported yet
      ...availableInputTypesFromRemote,
      ...availableInputTypesOverrides
    }; // Update the availableInputTypes to take into account the feature toggles.

    if (!this.featureToggles.inputType_credentials) {
      availableInputTypes.credentials = false;
    }

    if (!this.featureToggles.inputType_identities) {
      availableInputTypes.identities = false;
    }

    if (!this.featureToggles.inputType_creditCards) {
      availableInputTypes.creditCards = false;
    } // at this point we've fetched from remote + merged local overrides, so we're ready to set.


    this.setAvailableInputTypes(availableInputTypes);
    return {
      featureToggles: this.featureToggles,
      availableInputTypes: this.availableInputTypes
    };
  }
  /** @returns {AutofillFeatureToggles} */


  get featureToggles() {
    if (this._featureToggles === null) throw new Error('feature toggles accessed before being set');
    return this._featureToggles;
  }
  /** @param {AutofillFeatureToggles} input */


  setFeatureToggles(input) {
    this._featureToggles = input;
  }
  /** @returns {AvailableInputTypes} */


  get availableInputTypes() {
    if (this._availableInputTypes === null) throw new Error('available input types accessed before being set');
    return this._availableInputTypes;
  }
  /** @param {AvailableInputTypes} value */


  setAvailableInputTypes(value) {
    this._availableInputTypes = value;
  }

  static default(globalConfig, deviceApi) {
    const settings = new Settings(globalConfig, deviceApi);
    settings.setFeatureToggles(Settings.defaults.featureToggles);
    settings.setAvailableInputTypes(Settings.defaults.availableInputTypes);
    return settings;
  }

}

exports.Settings = Settings;

_defineProperty(Settings, "defaults", {
  /** @type {AutofillFeatureToggles} */
  featureToggles: {
    credentials_saving: false,
    password_generation: false,
    emailProtection: false,
    inputType_identities: false,
    inputType_credentials: false,
    inputType_creditCards: false,
    inlineIcon_credentials: false
  },

  /** @type {AvailableInputTypes} */
  availableInputTypes: {
    credentials: false,
    identities: false,
    creditCards: false,
    email: false
  }
});

},{"../packages/device-api":2,"./deviceApiCalls/__generated__/deviceApiCalls":50,"./deviceApiCalls/__generated__/validators.zod":51}],35:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;

var _autofillUtils = require("../autofill-utils");

var _HTMLTooltip = _interopRequireDefault(require("./HTMLTooltip"));

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

class DataHTMLTooltip extends _HTMLTooltip.default {
  /**
   * @param {InputTypeConfigs} config
   * @param {TooltipItemRenderer[]} items
   * @param {{onSelect(id:string): void}} callbacks
   */
  render(config, items, callbacks) {
    let hasAddedSeparator = false; // Only show an hr above the first duck address button, but it can be either personal or private

    const shouldShowSeparator = dataId => {
      const shouldShow = ['personalAddress', 'privateAddress'].includes(dataId) && !hasAddedSeparator;
      if (shouldShow) hasAddedSeparator = true;
      return shouldShow;
    };

    const topClass = this.options.wrapperClass || '';
    this.shadow.innerHTML = "\n".concat(this.options.css, "\n<div class=\"wrapper wrapper--data ").concat(topClass, "\">\n    <div class=\"tooltip tooltip--data\" hidden>\n        ").concat(items.map(item => {
      var _item$labelSmall, _item$label;

      // these 2 are optional
      const labelSmall = (_item$labelSmall = item.labelSmall) === null || _item$labelSmall === void 0 ? void 0 : _item$labelSmall.call(item, this.subtype);
      const label = (_item$label = item.label) === null || _item$label === void 0 ? void 0 : _item$label.call(item, this.subtype);
      return "\n            ".concat(shouldShowSeparator(item.id()) ? '<hr />' : '', "\n            <button id=\"").concat(item.id(), "\" class=\"tooltip__button tooltip__button--data tooltip__button--data--").concat(config.type, " js-autofill-button\" >\n                <span class=\"tooltip__button__text-container\">\n                    <span class=\"label label--medium\">").concat((0, _autofillUtils.escapeXML)(item.labelMedium(this.subtype)), "</span>\n                    ").concat(label ? "<span class=\"label\">".concat((0, _autofillUtils.escapeXML)(label), "</span>") : '', "\n                    ").concat(labelSmall ? "<span class=\"label label--small\">".concat((0, _autofillUtils.escapeXML)(labelSmall), "</span>") : '', "\n                </span>\n            </button>\n        ");
    }).join(''), "\n    </div>\n</div>");
    this.wrapper = this.shadow.querySelector('.wrapper');
    this.tooltip = this.shadow.querySelector('.tooltip');
    this.autofillButtons = this.shadow.querySelectorAll('.js-autofill-button');
    this.autofillButtons.forEach(btn => {
      this.registerClickableButton(btn, () => {
        callbacks.onSelect(btn.id);
      });
    });
    this.init();
    return this;
  }

}

var _default = DataHTMLTooltip;
exports.default = _default;

},{"../autofill-utils":46,"./HTMLTooltip":37}],36:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;

var _autofillUtils = require("../autofill-utils");

var _HTMLTooltip = _interopRequireDefault(require("./HTMLTooltip"));

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

class EmailHTMLTooltip extends _HTMLTooltip.default {
  /**
   * @param {import("../DeviceInterface/InterfacePrototype").default} device
   */
  render(device) {
    this.device = device;
    this.addresses = device.getLocalAddresses();
    this.shadow.innerHTML = "\n".concat(this.options.css, "\n<div class=\"wrapper wrapper--email\">\n    <div class=\"tooltip tooltip--email\" hidden>\n        <button class=\"tooltip__button tooltip__button--email js-use-personal\">\n            <span class=\"tooltip__button--email__primary-text\">\n                Use <span class=\"js-address\">").concat((0, _autofillUtils.formatDuckAddress)((0, _autofillUtils.escapeXML)(this.addresses.personalAddress)), "</span>\n            </span>\n            <span class=\"tooltip__button--email__secondary-text\">Blocks email trackers</span>\n        </button>\n        <button class=\"tooltip__button tooltip__button--email js-use-private\">\n            <span class=\"tooltip__button--email__primary-text\">Use a Private Address</span>\n            <span class=\"tooltip__button--email__secondary-text\">Blocks email trackers and hides your address</span>\n        </button>\n    </div>\n</div>");
    this.wrapper = this.shadow.querySelector('.wrapper');
    this.tooltip = this.shadow.querySelector('.tooltip');
    this.usePersonalButton = this.shadow.querySelector('.js-use-personal');
    this.usePrivateButton = this.shadow.querySelector('.js-use-private');
    this.addressEl = this.shadow.querySelector('.js-address');

    this.updateAddresses = addresses => {
      if (addresses && this.addressEl) {
        this.addresses = addresses;
        this.addressEl.textContent = (0, _autofillUtils.formatDuckAddress)(addresses.personalAddress);
      }
    };

    this.registerClickableButton(this.usePersonalButton, () => {
      this.fillForm('personalAddress');
    });
    this.registerClickableButton(this.usePrivateButton, () => {
      this.fillForm('privateAddress');
    }); // Get the alias from the extension

    this.device.getAddresses().then(this.updateAddresses);
    this.init();
    return this;
  }
  /**
   * @param {'personalAddress' | 'privateAddress'} id
   */


  async fillForm(id) {
    var _this$device;

    const address = this.addresses[id];
    const formattedAddress = (0, _autofillUtils.formatDuckAddress)(address);
    (_this$device = this.device) === null || _this$device === void 0 ? void 0 : _this$device.selectedDetail({
      email: formattedAddress,
      id
    }, 'email');
  }

}

var _default = EmailHTMLTooltip;
exports.default = _default;

},{"../autofill-utils":46,"./HTMLTooltip":37}],37:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.defaultOptions = exports.default = exports.HTMLTooltip = void 0;

var _autofillUtils = require("../autofill-utils");

var _matching = require("../Form/matching");

var _styles = require("./styles/styles");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * @typedef {object} HTMLTooltipOptions
 * @property {boolean} testMode
 * @property {string | null} [wrapperClass]
 * @property {(top: number, left: number) => string} [tooltipPositionClass]
 * @property {(details: {height: number, width: number}) => void} [setSize] - if this is set, it will be called initially once + every times the size changes
 * @property {() => void} remove
 * @property {string} css
 */

/** @type {import('./HTMLTooltip.js').HTMLTooltipOptions} */
const defaultOptions = {
  wrapperClass: '',
  tooltipPositionClass: (top, left) => ".wrapper {transform: translate(".concat(left, "px, ").concat(top, "px);}"),
  css: "<style>".concat(_styles.CSS_STYLES, "</style>"),
  setSize: undefined,
  remove: () => {
    /** noop */
  },
  testMode: false
};
exports.defaultOptions = defaultOptions;

class HTMLTooltip {
  /** @type {HTMLTooltipOptions} */

  /**
   * @param config
   * @param inputType
   * @param getPosition
   * @param {HTMLTooltipOptions} options
   */
  constructor(config, inputType, getPosition, options) {
    _defineProperty(this, "options", void 0);

    _defineProperty(this, "resObs", new ResizeObserver(entries => entries.forEach(() => this.checkPosition())));

    _defineProperty(this, "mutObs", new MutationObserver(mutationList => {
      for (const mutationRecord of mutationList) {
        if (mutationRecord.type === 'childList') {
          // Only check added nodes
          mutationRecord.addedNodes.forEach(el => {
            if (el.nodeName === 'DDG-AUTOFILL') return;
            this.ensureIsLastInDOM();
          });
        }
      }

      this.checkPosition();
    }));

    _defineProperty(this, "clickableButtons", new Map());

    this.options = options;
    this.shadow = document.createElement('ddg-autofill').attachShadow({
      mode: options.testMode ? 'open' : 'closed'
    });
    this.host = this.shadow.host;
    this.config = config;
    this.subtype = (0, _matching.getSubtypeFromType)(inputType);
    this.tooltip = null;
    this.getPosition = getPosition;
    const forcedVisibilityStyles = {
      'display': 'block',
      'visibility': 'visible',
      'opacity': '1'
    }; // @ts-ignore how to narrow this.host to HTMLElement?

    (0, _autofillUtils.addInlineStyles)(this.host, forcedVisibilityStyles);
    this.count = 0;
  }

  append() {
    document.body.appendChild(this.host);
  }

  remove() {
    window.removeEventListener('scroll', this, {
      capture: true
    });
    this.resObs.disconnect();
    this.mutObs.disconnect();
    this.lift();
  }

  lift() {
    this.left = null;
    this.top = null;
    document.body.removeChild(this.host);
  }

  handleEvent(event) {
    switch (event.type) {
      case 'scroll':
        this.checkPosition();
        break;
    }
  }

  focus(x, y) {
    var _this$shadow$elementF, _this$shadow$elementF2;

    const focusableElements = 'button';
    const currentFocusClassName = 'currentFocus';
    const currentFocused = this.shadow.querySelectorAll(".".concat(currentFocusClassName));
    [...currentFocused].forEach(el => {
      el.classList.remove(currentFocusClassName);
    });
    (_this$shadow$elementF = this.shadow.elementFromPoint(x, y)) === null || _this$shadow$elementF === void 0 ? void 0 : (_this$shadow$elementF2 = _this$shadow$elementF.closest(focusableElements)) === null || _this$shadow$elementF2 === void 0 ? void 0 : _this$shadow$elementF2.classList.add(currentFocusClassName);
  }

  checkPosition() {
    if (this.animationFrame) {
      window.cancelAnimationFrame(this.animationFrame);
    }

    this.animationFrame = window.requestAnimationFrame(() => {
      const {
        left,
        bottom
      } = this.getPosition();

      if (left !== this.left || bottom !== this.top) {
        this.updatePosition({
          left,
          top: bottom
        });
      }

      this.animationFrame = null;
    });
  }

  updatePosition(_ref) {
    var _this$options$tooltip, _this$options;

    let {
      left,
      top
    } = _ref;
    const shadow = this.shadow; // If the stylesheet is not loaded wait for load (Chrome bug)

    if (!shadow.styleSheets.length) {
      var _this$stylesheet;

      (_this$stylesheet = this.stylesheet) === null || _this$stylesheet === void 0 ? void 0 : _this$stylesheet.addEventListener('load', () => this.checkPosition());
      return;
    }

    this.left = left;
    this.top = top;

    if (this.transformRuleIndex && shadow.styleSheets[0].rules[this.transformRuleIndex]) {
      // If we have already set the rule, remove it…
      shadow.styleSheets[0].deleteRule(this.transformRuleIndex);
    } else {
      // …otherwise, set the index as the very last rule
      this.transformRuleIndex = shadow.styleSheets[0].rules.length;
    }

    let cssRule = (_this$options$tooltip = (_this$options = this.options).tooltipPositionClass) === null || _this$options$tooltip === void 0 ? void 0 : _this$options$tooltip.call(_this$options, top, left);

    if (typeof cssRule === 'string') {
      shadow.styleSheets[0].insertRule(cssRule, this.transformRuleIndex);
    }
  }

  ensureIsLastInDOM() {
    this.count = this.count || 0; // If DDG el is not the last in the doc, move it there

    if (document.body.lastElementChild !== this.host) {
      // Try up to 15 times to avoid infinite loop in case someone is doing the same
      if (this.count < 15) {
        this.lift();
        this.append();
        this.checkPosition();
        this.count++;
      } else {
        // Remove the tooltip from the form to cleanup listeners and observers
        this.options.remove();
        console.info("DDG autofill bailing out");
      }
    }
  }

  setActiveButton(e) {
    this.activeButton = e.target;
  }

  unsetActiveButton() {
    this.activeButton = null;
  }

  registerClickableButton(btn, handler) {
    this.clickableButtons.set(btn, handler); // Needed because clicks within the shadow dom don't provide this info to the outside

    btn.addEventListener('mouseenter', e => this.setActiveButton(e));
    btn.addEventListener('mouseleave', () => this.unsetActiveButton());
  }

  dispatchClick() {
    const handler = this.clickableButtons.get(this.activeButton);

    if (handler) {
      (0, _autofillUtils.safeExecute)(this.activeButton, handler);
    }
  }

  setupSizeListener() {
    // Listen to layout and paint changes to register the size
    const observer = new PerformanceObserver(() => {
      this.setSize();
    });
    observer.observe({
      entryTypes: ['layout-shift', 'paint']
    });
  }

  setSize() {
    var _this$options$setSize, _this$options2;

    const innerNode = this.shadow.querySelector('.wrapper--data'); // Shouldn't be possible

    if (!innerNode) return;
    const details = {
      height: innerNode.clientHeight,
      width: innerNode.clientWidth
    };
    (_this$options$setSize = (_this$options2 = this.options).setSize) === null || _this$options$setSize === void 0 ? void 0 : _this$options$setSize.call(_this$options2, details);
  }

  init() {
    var _this$stylesheet2;

    this.animationFrame = null;
    this.top = 0;
    this.left = 0;
    this.transformRuleIndex = null;
    this.stylesheet = this.shadow.querySelector('link, style'); // Un-hide once the style is loaded, to avoid flashing unstyled content

    (_this$stylesheet2 = this.stylesheet) === null || _this$stylesheet2 === void 0 ? void 0 : _this$stylesheet2.addEventListener('load', () => this.tooltip.removeAttribute('hidden'));
    this.append();
    this.resObs.observe(document.body);
    this.mutObs.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true
    });
    window.addEventListener('scroll', this, {
      capture: true
    });
    this.setSize();

    if (typeof this.options.setSize === 'function') {
      this.setupSizeListener();
    }
  }

}

exports.HTMLTooltip = HTMLTooltip;
var _default = HTMLTooltip;
exports.default = _default;

},{"../Form/matching":26,"../autofill-utils":46,"./styles/styles":43}],38:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.HTMLTooltipUIController = void 0;

var _inputTypeConfig = require("../../Form/inputTypeConfig");

var _DataHTMLTooltip = _interopRequireDefault(require("../DataHTMLTooltip"));

var _EmailHTMLTooltip = _interopRequireDefault(require("../EmailHTMLTooltip"));

var _HTMLTooltip = require("../HTMLTooltip");

var _UIController = require("./UIController");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * @typedef HTMLTooltipControllerOptions
 * @property {"modern" | "legacy"} tooltipKind - A choice between the newer Autofill UI vs the older one used in the extension
 * @property {import("../../DeviceInterface/InterfacePrototype").default} device - The device interface that's currently running
 * @property {(e: PointerEvent) => void} [onPointerDown] - An optional callback that will be executed for every pointerdown event
 * regardless of whether this Controller has an open tooltip, or not
 */

/**
 * This encapsulates all the logic relating to showing/hiding the HTML Tooltip
 *
 * Note: This could be displayed in the current webpage (for example, in the extension)
 * or within a webview overlay (like on macOS & upcoming in windows)
 */
class HTMLTooltipUIController extends _UIController.UIController {
  /** @type {import("../HTMLTooltip.js").HTMLTooltip | null} */

  /** @type {HTMLTooltipControllerOptions} */

  /** @type {import('../HTMLTooltip.js').HTMLTooltipOptions} */

  /** @type {import("../../DeviceInterface/InterfacePrototype").default | null} */

  /**
   * Store any cleanups that may have been registered
   * @type {CleanupFn[]}
   */

  /**
   * @param {HTMLTooltipControllerOptions} options
   * @param {import('../HTMLTooltip.js').HTMLTooltipOptions} htmlTooltipOptions
   */
  constructor(options) {
    let htmlTooltipOptions = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : _HTMLTooltip.defaultOptions;
    super();

    _defineProperty(this, "_activeTooltip", null);

    _defineProperty(this, "_options", void 0);

    _defineProperty(this, "_htmlTooltipOptions", void 0);

    _defineProperty(this, "_device", null);

    _defineProperty(this, "_listenerCleanups", []);

    this._options = options;
    this._htmlTooltipOptions = htmlTooltipOptions;
    window.addEventListener('pointerdown', this, true);
  }
  /**
   * @param {import('./UIController').AttachArgs} args
   */


  attach(args) {
    if (this.getActiveTooltip()) {
      return;
    }

    const {
      topContextData,
      getPosition,
      input,
      form
    } = args;
    const tooltip = this.createTooltip(getPosition, topContextData);
    this.setActiveTooltip(tooltip);
    form.showingTooltip(input);
  }
  /**
   * Actually create the HTML Tooltip
   * @param {PosFn} getPosition
   * @param {TopContextData} topContextData
   * @return {import("../HTMLTooltip").HTMLTooltip}
   */


  createTooltip(getPosition, topContextData) {
    this._attachListeners();

    const config = (0, _inputTypeConfig.getInputConfigFromType)(topContextData.inputType);
    /**
     * @type {import('../HTMLTooltip').HTMLTooltipOptions}
     */

    const tooltipOptions = { ...this._htmlTooltipOptions,
      remove: () => this.removeTooltip()
    };

    if (this._options.tooltipKind === 'legacy') {
      return new _EmailHTMLTooltip.default(config, topContextData.inputType, getPosition, tooltipOptions).render(this._options.device);
    } // collect the data for each item to display


    const data = this._dataForAutofill(config, topContextData.inputType, topContextData); // convert the data into tool tip item renderers


    const asRenderers = data.map(d => config.tooltipItem(d)); // construct the autofill

    return new _DataHTMLTooltip.default(config, topContextData.inputType, getPosition, tooltipOptions).render(config, asRenderers, {
      onSelect: id => {
        this._onSelect(config, data, id);
      }
    });
  }

  _attachListeners() {
    window.addEventListener('input', this);
    window.addEventListener('keydown', this);
  }

  _removeListeners() {
    window.removeEventListener('input', this);
    window.removeEventListener('keydown', this);
  }

  handleEvent(event) {
    switch (event.type) {
      case 'keydown':
        if (['Escape', 'Tab', 'Enter'].includes(event.code)) {
          this.removeTooltip();
        }

        break;

      case 'input':
        this.removeTooltip();
        break;

      case 'pointerdown':
        {
          this._pointerDownListener(event);

          break;
        }
    }
  } // Global listener for event delegation


  _pointerDownListener(e) {
    var _this$_options$onPoin, _this$_options;

    if (!e.isTrusted) return; // @ts-ignore

    if (e.target.nodeName === 'DDG-AUTOFILL') {
      e.preventDefault();
      e.stopImmediatePropagation();
      const activeTooltip = this.getActiveTooltip();

      if (!activeTooltip) {
        console.warn('Could not get activeTooltip');
      } else {
        activeTooltip.dispatchClick();
      }
    } else {
      this.removeTooltip().catch(e => {
        console.error('error removing tooltip', e);
      });
    }

    (_this$_options$onPoin = (_this$_options = this._options).onPointerDown) === null || _this$_options$onPoin === void 0 ? void 0 : _this$_options$onPoin.call(_this$_options, e);
  }

  async removeTooltip(_via) {
    this._htmlTooltipOptions.remove();

    if (this._activeTooltip) {
      this._removeListeners();

      this._activeTooltip.remove();

      this._activeTooltip = null;
    }
  }
  /**
   * @returns {import("../HTMLTooltip.js").HTMLTooltip|null}
   */


  getActiveTooltip() {
    return this._activeTooltip;
  }
  /**
   * @param {import("../HTMLTooltip.js").HTMLTooltip} value
   */


  setActiveTooltip(value) {
    this._activeTooltip = value;
  }
  /**
   * Collect the data that's needed to populate the Autofill UI.
   *
   * Note: ideally we'd pass this data instead, so that we didn't have a circular dependency
   *
   * @param {InputTypeConfigs} config - This is the selected `InputTypeConfig` based on the type of field
   * @param {import('../../Form/matching').SupportedTypes} inputType - The input type for the current field
   * @param {TopContextData} topContextData
   */


  _dataForAutofill(config, inputType, topContextData) {
    return this._options.device.dataForAutofill(config, inputType, topContextData);
  }
  /**
   * When a field is selected, call the `onSelect` method from the device.
   *
   * Note: ideally we'd pass this data instead, so that we didn't have a circular dependency
   *
   * @param {InputTypeConfigs} config
   * @param {(CreditCardObject | IdentityObject | CredentialsObject)[]} data
   * @param {string | number} id
   */


  _onSelect(config, data, id) {
    return this._options.device.onSelect(config, data, id);
  }

  isActive() {
    return Boolean(this.getActiveTooltip());
  }

}

exports.HTMLTooltipUIController = HTMLTooltipUIController;

},{"../../Form/inputTypeConfig":21,"../DataHTMLTooltip":35,"../EmailHTMLTooltip":36,"../HTMLTooltip":37,"./UIController":41}],39:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.NativeUIController = void 0;

var _UIController = require("./UIController");

var _matching = require("../../Form/matching");

var _deviceApiCalls = require("../../deviceApiCalls/__generated__/deviceApiCalls");

/**
 * `NativeController` should be used in situations where you DO NOT
 * want any Autofill-controlled user interface.
 *
 * Examples are with iOS/Android, where 'attaching' only means
 * messaging a native layer to show a native tooltip.
 *
 * @example
 *
 * ```javascript
 * const controller = new NativeController();
 * controller.attach(...);
 * ```
 */
class NativeUIController extends _UIController.UIController {
  /**
   * @param {import('./UIController').AttachArgs} args
   */
  attach(args) {
    const {
      form,
      input,
      device
    } = args;
    const inputType = (0, _matching.getInputType)(input);
    const mainType = (0, _matching.getMainTypeFromType)(inputType);
    const subType = (0, _matching.getSubtypeFromType)(inputType);

    if (mainType === 'unknown') {
      throw new Error('unreachable, should not be here if (mainType === "unknown")');
    } // /** @type {GetAutofillDataRequest} */


    const payload = {
      inputType,
      mainType,
      subType
    };
    device.deviceApi.request(new _deviceApiCalls.GetAutofillDataCall(payload)).then(resp => {
      if (!resp) throw new Error('unreachable');

      switch (resp.action) {
        case 'fill':
          {
            if (mainType in resp) {
              form.autofillData(resp[mainType], mainType);
            } else {
              throw new Error("action: \"fill\" cannot occur because \"".concat(mainType, "\" was missing"));
            }

            break;
          }

        case 'focus':
          {
            var _form$activeInput;

            (_form$activeInput = form.activeInput) === null || _form$activeInput === void 0 ? void 0 : _form$activeInput.focus();
            break;
          }

        default:
          {
            if (args.device.isTestMode()) {
              console.warn('response not handled', resp);
            }
          }
      }
    }).catch(e => {
      console.error('NativeTooltip::device.getAutofillData(payload)');
      console.error(e);
    });
  }

}

exports.NativeUIController = NativeUIController;

},{"../../Form/matching":26,"../../deviceApiCalls/__generated__/deviceApiCalls":50,"./UIController":41}],40:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.OverlayUIController = void 0;

var _UIController = require("./UIController");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

var _state = /*#__PURE__*/new WeakMap();

/**
 * @typedef OverlayControllerOptions
 * @property {() => Promise<void>} remove - A callback that will be fired when the tooltip should be removed
 * @property {(details: ShowAutofillParentRequest) => Promise<void>} show - A callback that will be fired when the tooltip should be shown
 * @property {(e: PointerEvent) => void} [onPointerDown] - An optional callback for reacting to all `pointerdown` events.
 */

/**
 * @typedef ShowAutofillParentRequest - The argument that's sent to the native side
 * @property {boolean} wasFromClick - Whether the request originated from a click
 * @property {number} inputTop
 * @property {number} inputLeft
 * @property {number} inputHeight
 * @property {number} inputWidth
 * @property {string} serializedInputContext - Serialized JSON that will be picked up once the
 * 'overlay' requests its initial data
 */

/**
 * Use this `OverlayController` when you want to control an overlay, but don't have
 * your own UI to display.
 *
 * For example, on macOS this `OverlayController` would run in the main webpage
 * and would then signal to its native side when the overlay should show/close
 *
 * @example `show` and `remove` can be implemented to match your native side's messaging needs
 *
 * ```javascript
 * const controller = new OverlayController({
 *     remove: async () => this.closeAutofillParent(),
 *     show: async (details) => this.show(details),
 *     onPointerDown: (e) => this.onPointerDown(e)
 * })
 *
 * controller.attach(...)
 * ```
 */
class OverlayUIController extends _UIController.UIController {
  /** @type {"idle" | "parentShown"} */

  /** @type {import('../HTMLTooltip.js').HTMLTooltip | null} */

  /**
   * @param {OverlayControllerOptions} options
   */
  constructor(options) {
    super(options); // We always register this 'pointerdown' event, regardless of
    // whether we have a tooltip currently open or not. This is to ensure
    // we can clear out any existing state before opening a new one.

    _classPrivateFieldInitSpec(this, _state, {
      writable: true,
      value: 'idle'
    });

    _defineProperty(this, "_activeTooltip", null);

    window.addEventListener('pointerdown', this, true);
  }
  /**
   * @param {import('./UIController').AttachArgs} args
   */


  attach(args) {
    const {
      getPosition,
      topContextData,
      click,
      input
    } = args;
    let delay = 0;

    if (!click && !this.elementIsInViewport(getPosition())) {
      input.scrollIntoView(true);
      delay = 500;
    }

    setTimeout(() => {
      this.showTopTooltip(click, getPosition(), topContextData).catch(e => {
        console.error('error from showTopTooltip', e);
      });
    }, delay);
  }
  /**
   * @param {{ x: number; y: number; height: number; width: number; }} inputDimensions
   * @returns {boolean}
   */


  elementIsInViewport(inputDimensions) {
    if (inputDimensions.x < 0 || inputDimensions.y < 0 || inputDimensions.x + inputDimensions.width > document.documentElement.clientWidth || inputDimensions.y + inputDimensions.height > document.documentElement.clientHeight) {
      return false;
    }

    const viewport = document.documentElement;

    if (inputDimensions.x + inputDimensions.width > viewport.clientWidth || inputDimensions.y + inputDimensions.height > viewport.clientHeight) {
      return false;
    }

    return true;
  }
  /**
   * @param {{ x: number; y: number; } | null} click
   * @param {{ x: number; y: number; height: number; width: number; }} inputDimensions
   * @param {TopContextData} [data]
   */


  async showTopTooltip(click, inputDimensions, data) {
    let diffX = inputDimensions.x;
    let diffY = inputDimensions.y;

    if (click) {
      diffX -= click.x;
      diffY -= click.y;
    } else if (!this.elementIsInViewport(inputDimensions)) {
      // If the focus event is outside the viewport ignore, we've already tried to scroll to it
      return;
    }
    /** @type {ShowAutofillParentRequest} */


    const details = {
      wasFromClick: Boolean(click),
      inputTop: Math.floor(diffY),
      inputLeft: Math.floor(diffX),
      inputHeight: Math.floor(inputDimensions.height),
      inputWidth: Math.floor(inputDimensions.width),
      serializedInputContext: JSON.stringify(data)
    };

    try {
      await this._options.show(details);

      _classPrivateFieldSet(this, _state, 'parentShown');

      this._attachListeners();
    } catch (e) {
      console.error('could not show parent', e);
    }
  }

  _attachListeners() {
    window.addEventListener('scroll', this);
    window.addEventListener('keydown', this);
    window.addEventListener('input', this);
  }

  _removeListeners() {
    window.removeEventListener('scroll', this);
    window.removeEventListener('keydown', this);
    window.removeEventListener('input', this);
  }

  handleEvent(event) {
    switch (event.type) {
      case 'scroll':
        {
          this.removeTooltip(event.type);
          break;
        }

      case 'keydown':
        {
          if (['Escape', 'Tab', 'Enter'].includes(event.code)) {
            this.removeTooltip(event.type);
          }

          break;
        }

      case 'input':
        {
          this.removeTooltip(event.type);
          break;
        }

      case 'pointerdown':
        {
          var _this$_options$onPoin, _this$_options;

          this.removeTooltip(event.type);
          (_this$_options$onPoin = (_this$_options = this._options).onPointerDown) === null || _this$_options$onPoin === void 0 ? void 0 : _this$_options$onPoin.call(_this$_options, event);
          break;
        }
    }
  }
  /**
   * @param {string} trigger
   * @returns {Promise<void>}
   */


  async removeTooltip(trigger) {
    // for none pointer events, check to see if the tooltip is open before trying to close it
    if (trigger !== 'pointerdown') {
      if (_classPrivateFieldGet(this, _state) !== 'parentShown') {
        return;
      }
    }

    this._options.remove().catch(e => console.error('Could not close parent', e));

    _classPrivateFieldSet(this, _state, 'idle');

    this._removeListeners();
  }

}

exports.OverlayUIController = OverlayUIController;

},{"./UIController":41}],41:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.UIController = void 0;

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * @typedef AttachArgs The argument required to 'attach' a tooltip
 * @property {import("../../Form/Form").Form} form the Form that triggered this 'attach' call
 * @property {HTMLInputElement} input the input field that triggered this 'attach' call
 * @property {() => { x: number; y: number; height: number; width: number; }} getPosition A function that provides positioning information
 * @property {{x: number, y: number}|null} click The click positioning
 * @property {TopContextData} topContextData
 * @property {import("../../DeviceInterface/InterfacePrototype").default} device
 */

/**
 * This is the base interface that `UIControllers` should extend/implement
 */
class UIController {
  /**
   * @type {any}
   */

  /**
   * @param {any} [options]
   */
  constructor(options) {
    _defineProperty(this, "_options", void 0);

    this._options = options; // We always register this 'pointerdown' event, regardless of
    // whether we have a tooltip currently open or not. This is to ensure
    // we can clear out any existing state before opening a new one.

    window.addEventListener('pointerdown', this, true);
  }

  handleEvent(event) {
    switch (event.type) {
      case 'pointerdown':
        {
          var _this$_options$onPoin, _this$_options;

          (_this$_options$onPoin = (_this$_options = this._options).onPointerDown) === null || _this$_options$onPoin === void 0 ? void 0 : _this$_options$onPoin.call(_this$_options, event);
          break;
        }
    }
  }
  /**
   * Implement this method to control what happen when Autofill
   * has enough information to 'attach' a tooltip.
   *
   * @param {AttachArgs} _args
   * @returns {void}
   */


  attach(_args) {
    throw new Error('must implement attach');
  }
  /**
   * Implement this if your tooltip can be created from positioning
   * + topContextData.
   *
   * For example, in an 'overlay' on macOS/Windows this is needed since
   * there's no page information to call 'attach' above.
   *
   * @param {PosFn} _pos
   * @param {TopContextData} _topContextData
   * @returns {any | null}
   */


  createTooltip(_pos, _topContextData) {}
  /**
   * @param {string} _via
   */


  removeTooltip(_via) {}
  /**
   * Set the currently open HTMLTooltip instance
   *
   * @param {import("../HTMLTooltip.js").HTMLTooltip} _tooltip
   */


  setActiveTooltip(_tooltip) {}
  /**
   * Get the currently open HTMLTooltip instance, if one exists
   *
   * @returns {import("../HTMLTooltip.js").HTMLTooltip | null}
   */


  getActiveTooltip() {
    return null;
  }
  /**
   * Indicate whether the controller deems itself 'active'
   *
   * @returns {boolean}
   */


  isActive() {
    return false;
  }

}

exports.UIController = UIController;

},{}],42:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.ddgPasswordIconFocused = exports.ddgPasswordIconFilled = exports.ddgPasswordIconBaseWhite = exports.ddgPasswordIconBase = exports.ddgIdentityIconBase = exports.ddgCcIconFilled = exports.ddgCcIconBase = void 0;
const ddgPasswordIconBase = 'data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB3aWR0aD0iMjRweCIgaGVpZ2h0PSIyNHB4IiB2aWV3Qm94PSIwIDAgMjQgMjQiIHZlcnNpb249IjEuMSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+CiAgICA8dGl0bGU+ZGRnLXBhc3N3b3JkLWljb24tYmFzZTwvdGl0bGU+CiAgICA8ZyBpZD0iZGRnLXBhc3N3b3JkLWljb24tYmFzZSIgc3Ryb2tlPSJub25lIiBzdHJva2Utd2lkdGg9IjEiIGZpbGw9Im5vbmUiIGZpbGwtcnVsZT0iZXZlbm9kZCI+CiAgICAgICAgPGcgaWQ9IlVuaW9uIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSg0LjAwMDAwMCwgNC4wMDAwMDApIiBmaWxsPSIjMDAwMDAwIj4KICAgICAgICAgICAgPHBhdGggZD0iTTExLjMzMzMsMi42NjY2NyBDMTAuMjI4OCwyLjY2NjY3IDkuMzMzMzMsMy41NjIxIDkuMzMzMzMsNC42NjY2NyBDOS4zMzMzMyw1Ljc3MTI0IDEwLjIyODgsNi42NjY2NyAxMS4zMzMzLDYuNjY2NjcgQzEyLjQzNzksNi42NjY2NyAxMy4zMzMzLDUuNzcxMjQgMTMuMzMzMyw0LjY2NjY3IEMxMy4zMzMzLDMuNTYyMSAxMi40Mzc5LDIuNjY2NjcgMTEuMzMzMywyLjY2NjY3IFogTTEwLjY2NjcsNC42NjY2NyBDMTAuNjY2Nyw0LjI5ODQ4IDEwLjk2NTEsNCAxMS4zMzMzLDQgQzExLjcwMTUsNCAxMiw0LjI5ODQ4IDEyLDQuNjY2NjcgQzEyLDUuMDM0ODYgMTEuNzAxNSw1LjMzMzMzIDExLjMzMzMsNS4zMzMzMyBDMTAuOTY1MSw1LjMzMzMzIDEwLjY2NjcsNS4wMzQ4NiAxMC42NjY3LDQuNjY2NjcgWiIgaWQ9IlNoYXBlIj48L3BhdGg+CiAgICAgICAgICAgIDxwYXRoIGQ9Ik0xMC42NjY3LDAgQzcuNzIxMTUsMCA1LjMzMzMzLDIuMzg3ODEgNS4zMzMzMyw1LjMzMzMzIEM1LjMzMzMzLDUuNzYxMTkgNS4zODM4NSw2LjE3Nzk4IDUuNDc5NDUsNi41Nzc3NSBMMC4xOTUyNjIsMTEuODYxOSBDMC4wNzAyMzc5LDExLjk4NyAwLDEyLjE1NjUgMCwxMi4zMzMzIEwwLDE1LjMzMzMgQzAsMTUuNzAxNSAwLjI5ODQ3NywxNiAwLjY2NjY2NywxNiBMMy4zMzMzMywxNiBDNC4wNjk3MSwxNiA0LjY2NjY3LDE1LjQwMyA0LjY2NjY3LDE0LjY2NjcgTDQuNjY2NjcsMTQgTDUuMzMzMzMsMTQgQzYuMDY5NzEsMTQgNi42NjY2NywxMy40MDMgNi42NjY2NywxMi42NjY3IEw2LjY2NjY3LDExLjMzMzMgTDgsMTEuMzMzMyBDOC4xNzY4MSwxMS4zMzMzIDguMzQ2MzgsMTEuMjYzMSA4LjQ3MTQxLDExLjEzODEgTDkuMTU5MDYsMTAuNDUwNCBDOS42Mzc3MiwxMC41OTEyIDEwLjE0MzksMTAuNjY2NyAxMC42NjY3LDEwLjY2NjcgQzEzLjYxMjIsMTAuNjY2NyAxNiw4LjI3ODg1IDE2LDUuMzMzMzMgQzE2LDIuMzg3ODEgMTMuNjEyMiwwIDEwLjY2NjcsMCBaIE02LjY2NjY3LDUuMzMzMzMgQzYuNjY2NjcsMy4xMjQxOSA4LjQ1NzUzLDEuMzMzMzMgMTAuNjY2NywxLjMzMzMzIEMxMi44NzU4LDEuMzMzMzMgMTQuNjY2NywzLjEyNDE5IDE0LjY2NjcsNS4zMzMzMyBDMTQuNjY2Nyw3LjU0MjQ3IDEyLjg3NTgsOS4zMzMzMyAxMC42NjY3LDkuMzMzMzMgQzEwLjE1NTgsOS4zMzMzMyA5LjY2ODg2LDkuMjM3OSA5LjIyMTUyLDkuMDY0NSBDOC45NzUyOCw4Ljk2OTA1IDguNjk1OTEsOS4wMjc5NSA4LjUwOTE2LDkuMjE0NjkgTDcuNzIzODYsMTAgTDYsMTAgQzUuNjMxODEsMTAgNS4zMzMzMywxMC4yOTg1IDUuMzMzMzMsMTAuNjY2NyBMNS4zMzMzMywxMi42NjY3IEw0LDEyLjY2NjcgQzMuNjMxODEsMTIuNjY2NyAzLjMzMzMzLDEyLjk2NTEgMy4zMzMzMywxMy4zMzMzIEwzLjMzMzMzLDE0LjY2NjcgTDEuMzMzMzMsMTQuNjY2NyBMMS4zMzMzMywxMi42MDk1IEw2LjY5Nzg3LDcuMjQ0OTQgQzYuODc1MDIsNy4wNjc3OSA2LjkzNzksNi44MDYyOSA2Ljg2MDY1LDYuNTY3OTggQzYuNzM0ODksNi4xNzk5NyA2LjY2NjY3LDUuNzY1MjcgNi42NjY2Nyw1LjMzMzMzIFoiIGlkPSJTaGFwZSI+PC9wYXRoPgogICAgICAgIDwvZz4KICAgIDwvZz4KPC9zdmc+';
exports.ddgPasswordIconBase = ddgPasswordIconBase;
const ddgPasswordIconBaseWhite = 'data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB3aWR0aD0iMjRweCIgaGVpZ2h0PSIyNHB4IiB2aWV3Qm94PSIwIDAgMjQgMjQiIHZlcnNpb249IjEuMSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+CiAgICA8dGl0bGU+ZGRnLXBhc3N3b3JkLWljb24tYmFzZS13aGl0ZTwvdGl0bGU+CiAgICA8ZyBpZD0iZGRnLXBhc3N3b3JkLWljb24tYmFzZS13aGl0ZSIgc3Ryb2tlPSJub25lIiBzdHJva2Utd2lkdGg9IjEiIGZpbGw9Im5vbmUiIGZpbGwtcnVsZT0iZXZlbm9kZCI+CiAgICAgICAgPGcgaWQ9IlVuaW9uIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSg0LjAwMDAwMCwgNC4wMDAwMDApIiBmaWxsPSIjRkZGRkZGIj4KICAgICAgICAgICAgPHBhdGggZD0iTTExLjMzMzMsMi42NjY2NyBDMTAuMjI4OCwyLjY2NjY3IDkuMzMzMzMsMy41NjIxIDkuMzMzMzMsNC42NjY2NyBDOS4zMzMzMyw1Ljc3MTI0IDEwLjIyODgsNi42NjY2NyAxMS4zMzMzLDYuNjY2NjcgQzEyLjQzNzksNi42NjY2NyAxMy4zMzMzLDUuNzcxMjQgMTMuMzMzMyw0LjY2NjY3IEMxMy4zMzMzLDMuNTYyMSAxMi40Mzc5LDIuNjY2NjcgMTEuMzMzMywyLjY2NjY3IFogTTEwLjY2NjcsNC42NjY2NyBDMTAuNjY2Nyw0LjI5ODQ4IDEwLjk2NTEsNCAxMS4zMzMzLDQgQzExLjcwMTUsNCAxMiw0LjI5ODQ4IDEyLDQuNjY2NjcgQzEyLDUuMDM0ODYgMTEuNzAxNSw1LjMzMzMzIDExLjMzMzMsNS4zMzMzMyBDMTAuOTY1MSw1LjMzMzMzIDEwLjY2NjcsNS4wMzQ4NiAxMC42NjY3LDQuNjY2NjcgWiIgaWQ9IlNoYXBlIj48L3BhdGg+CiAgICAgICAgICAgIDxwYXRoIGQ9Ik0xMC42NjY3LDAgQzcuNzIxMTUsMCA1LjMzMzMzLDIuMzg3ODEgNS4zMzMzMyw1LjMzMzMzIEM1LjMzMzMzLDUuNzYxMTkgNS4zODM4NSw2LjE3Nzk4IDUuNDc5NDUsNi41Nzc3NSBMMC4xOTUyNjIsMTEuODYxOSBDMC4wNzAyMzc5LDExLjk4NyAwLDEyLjE1NjUgMCwxMi4zMzMzIEwwLDE1LjMzMzMgQzAsMTUuNzAxNSAwLjI5ODQ3NywxNiAwLjY2NjY2NywxNiBMMy4zMzMzMywxNiBDNC4wNjk3MSwxNiA0LjY2NjY3LDE1LjQwMyA0LjY2NjY3LDE0LjY2NjcgTDQuNjY2NjcsMTQgTDUuMzMzMzMsMTQgQzYuMDY5NzEsMTQgNi42NjY2NywxMy40MDMgNi42NjY2NywxMi42NjY3IEw2LjY2NjY3LDExLjMzMzMgTDgsMTEuMzMzMyBDOC4xNzY4MSwxMS4zMzMzIDguMzQ2MzgsMTEuMjYzMSA4LjQ3MTQxLDExLjEzODEgTDkuMTU5MDYsMTAuNDUwNCBDOS42Mzc3MiwxMC41OTEyIDEwLjE0MzksMTAuNjY2NyAxMC42NjY3LDEwLjY2NjcgQzEzLjYxMjIsMTAuNjY2NyAxNiw4LjI3ODg1IDE2LDUuMzMzMzMgQzE2LDIuMzg3ODEgMTMuNjEyMiwwIDEwLjY2NjcsMCBaIE02LjY2NjY3LDUuMzMzMzMgQzYuNjY2NjcsMy4xMjQxOSA4LjQ1NzUzLDEuMzMzMzMgMTAuNjY2NywxLjMzMzMzIEMxMi44NzU4LDEuMzMzMzMgMTQuNjY2NywzLjEyNDE5IDE0LjY2NjcsNS4zMzMzMyBDMTQuNjY2Nyw3LjU0MjQ3IDEyLjg3NTgsOS4zMzMzMyAxMC42NjY3LDkuMzMzMzMgQzEwLjE1NTgsOS4zMzMzMyA5LjY2ODg2LDkuMjM3OSA5LjIyMTUyLDkuMDY0NSBDOC45NzUyOCw4Ljk2OTA1IDguNjk1OTEsOS4wMjc5NSA4LjUwOTE2LDkuMjE0NjkgTDcuNzIzODYsMTAgTDYsMTAgQzUuNjMxODEsMTAgNS4zMzMzMywxMC4yOTg1IDUuMzMzMzMsMTAuNjY2NyBMNS4zMzMzMywxMi42NjY3IEw0LDEyLjY2NjcgQzMuNjMxODEsMTIuNjY2NyAzLjMzMzMzLDEyLjk2NTEgMy4zMzMzMywxMy4zMzMzIEwzLjMzMzMzLDE0LjY2NjcgTDEuMzMzMzMsMTQuNjY2NyBMMS4zMzMzMywxMi42MDk1IEw2LjY5Nzg3LDcuMjQ0OTQgQzYuODc1MDIsNy4wNjc3OSA2LjkzNzksNi44MDYyOSA2Ljg2MDY1LDYuNTY3OTggQzYuNzM0ODksNi4xNzk5NyA2LjY2NjY3LDUuNzY1MjcgNi42NjY2Nyw1LjMzMzMzIFoiIGlkPSJTaGFwZSI+PC9wYXRoPgogICAgICAgIDwvZz4KICAgIDwvZz4KPC9zdmc+';
exports.ddgPasswordIconBaseWhite = ddgPasswordIconBaseWhite;
const ddgPasswordIconFilled = 'data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB3aWR0aD0iMjRweCIgaGVpZ2h0PSIyNHB4IiB2aWV3Qm94PSIwIDAgMjQgMjQiIHZlcnNpb249IjEuMSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+CiAgICA8dGl0bGU+ZGRnLXBhc3N3b3JkLWljb24tZmlsbGVkPC90aXRsZT4KICAgIDxnIGlkPSJkZGctcGFzc3dvcmQtaWNvbi1maWxsZWQiIHN0cm9rZT0ibm9uZSIgc3Ryb2tlLXdpZHRoPSIxIiBmaWxsPSJub25lIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiPgogICAgICAgIDxnIGlkPSJTaGFwZSIgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoNC4wMDAwMDAsIDQuMDAwMDAwKSIgZmlsbD0iIzc2NDMxMCI+CiAgICAgICAgICAgIDxwYXRoIGQ9Ik0xMS4yNSwyLjc1IEMxMC4xNDU0LDIuNzUgOS4yNSwzLjY0NTQzIDkuMjUsNC43NSBDOS4yNSw1Ljg1NDU3IDEwLjE0NTQsNi43NSAxMS4yNSw2Ljc1IEMxMi4zNTQ2LDYuNzUgMTMuMjUsNS44NTQ1NyAxMy4yNSw0Ljc1IEMxMy4yNSwzLjY0NTQzIDEyLjM1NDYsMi43NSAxMS4yNSwyLjc1IFogTTEwLjc1LDQuNzUgQzEwLjc1LDQuNDczODYgMTAuOTczOSw0LjI1IDExLjI1LDQuMjUgQzExLjUyNjEsNC4yNSAxMS43NSw0LjQ3Mzg2IDExLjc1LDQuNzUgQzExLjc1LDUuMDI2MTQgMTEuNTI2MSw1LjI1IDExLjI1LDUuMjUgQzEwLjk3MzksNS4yNSAxMC43NSw1LjAyNjE0IDEwLjc1LDQuNzUgWiI+PC9wYXRoPgogICAgICAgICAgICA8cGF0aCBkPSJNMTAuNjI1LDAgQzcuNjU2NDcsMCA1LjI1LDIuNDA2NDcgNS4yNSw1LjM3NSBDNS4yNSw1Ljc4MDk4IDUuMjk1MTQsNi4xNzcxNCA1LjM4MDg4LDYuNTU4NDYgTDAuMjE5NjcsMTEuNzE5NyBDMC4wNzkwMTc2LDExLjg2MDMgMCwxMi4wNTExIDAsMTIuMjUgTDAsMTUuMjUgQzAsMTUuNjY0MiAwLjMzNTc4NiwxNiAwLjc1LDE2IEwzLjc0NjYxLDE2IEM0LjMwMDc2LDE2IDQuNzUsMTUuNTUwOCA0Ljc1LDE0Ljk5NjYgTDQuNzUsMTQgTDUuNzQ2NjEsMTQgQzYuMzAwNzYsMTQgNi43NSwxMy41NTA4IDYuNzUsMTIuOTk2NiBMNi43NSwxMS41IEw4LDExLjUgQzguMTk4OTEsMTEuNSA4LjM4OTY4LDExLjQyMSA4LjUzMDMzLDExLjI4MDMgTDkuMjQwNzgsMTAuNTY5OSBDOS42ODMwNCwxMC42ODc1IDEwLjE0NzIsMTAuNzUgMTAuNjI1LDEwLjc1IEMxMy41OTM1LDEwLjc1IDE2LDguMzQzNTMgMTYsNS4zNzUgQzE2LDIuNDA2NDcgMTMuNTkzNSwwIDEwLjYyNSwwIFogTTYuNzUsNS4zNzUgQzYuNzUsMy4yMzQ5IDguNDg0OSwxLjUgMTAuNjI1LDEuNSBDMTIuNzY1MSwxLjUgMTQuNSwzLjIzNDkgMTQuNSw1LjM3NSBDMTQuNSw3LjUxNTEgMTIuNzY1MSw5LjI1IDEwLjYyNSw5LjI1IEMxMC4xNTQ1LDkuMjUgOS43MDUyOCw5LjE2NjUgOS4yOTAxMSw5LjAxNDE2IEM5LjAxNTgxLDguOTEzNSA4LjcwODAzLDguOTgxMzEgOC41MDE0Miw5LjE4NzkyIEw3LjY4OTM0LDEwIEw2LDEwIEM1LjU4NTc5LDEwIDUuMjUsMTAuMzM1OCA1LjI1LDEwLjc1IEw1LjI1LDEyLjUgTDQsMTIuNSBDMy41ODU3OSwxMi41IDMuMjUsMTIuODM1OCAzLjI1LDEzLjI1IEwzLjI1LDE0LjUgTDEuNSwxNC41IEwxLjUsMTIuNTYwNyBMNi43NDgyNiw3LjMxMjQgQzYuOTQ2NjYsNy4xMTQgNy4wMTc3Myw2LjgyMTQ1IDYuOTMyNDUsNi41NTQxMyBDNi44MTQxNSw2LjE4MzI3IDYuNzUsNS43ODczNSA2Ljc1LDUuMzc1IFoiPjwvcGF0aD4KICAgICAgICA8L2c+CiAgICA8L2c+Cjwvc3ZnPg==';
exports.ddgPasswordIconFilled = ddgPasswordIconFilled;
const ddgPasswordIconFocused = 'data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB3aWR0aD0iMjRweCIgaGVpZ2h0PSIyNHB4IiB2aWV3Qm94PSIwIDAgMjQgMjQiIHZlcnNpb249IjEuMSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+CiAgICA8dGl0bGU+ZGRnLXBhc3N3b3JkLWljb24tZm9jdXNlZDwvdGl0bGU+CiAgICA8ZyBpZD0iZGRnLXBhc3N3b3JkLWljb24tZm9jdXNlZCIgc3Ryb2tlPSJub25lIiBzdHJva2Utd2lkdGg9IjEiIGZpbGw9Im5vbmUiIGZpbGwtcnVsZT0iZXZlbm9kZCI+CiAgICAgICAgPGcgaWQ9Ikljb24tQ29udGFpbmVyIiBmaWxsPSIjMDAwMDAwIj4KICAgICAgICAgICAgPHJlY3QgaWQ9IlJlY3RhbmdsZSIgZmlsbC1vcGFjaXR5PSIwLjEiIGZpbGwtcnVsZT0ibm9uemVybyIgeD0iMCIgeT0iMCIgd2lkdGg9IjI0IiBoZWlnaHQ9IjI0IiByeD0iMTIiPjwvcmVjdD4KICAgICAgICAgICAgPGcgaWQ9Ikdyb3VwIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSg0LjAwMDAwMCwgNC4wMDAwMDApIiBmaWxsLW9wYWNpdHk9IjAuOSI+CiAgICAgICAgICAgICAgICA8cGF0aCBkPSJNMTEuMjUsMi43NSBDMTAuMTQ1NCwyLjc1IDkuMjUsMy42NDU0MyA5LjI1LDQuNzUgQzkuMjUsNS44NTQ1NyAxMC4xNDU0LDYuNzUgMTEuMjUsNi43NSBDMTIuMzU0Niw2Ljc1IDEzLjI1LDUuODU0NTcgMTMuMjUsNC43NSBDMTMuMjUsMy42NDU0MyAxMi4zNTQ2LDIuNzUgMTEuMjUsMi43NSBaIE0xMC43NSw0Ljc1IEMxMC43NSw0LjQ3Mzg2IDEwLjk3MzksNC4yNSAxMS4yNSw0LjI1IEMxMS41MjYxLDQuMjUgMTEuNzUsNC40NzM4NiAxMS43NSw0Ljc1IEMxMS43NSw1LjAyNjE0IDExLjUyNjEsNS4yNSAxMS4yNSw1LjI1IEMxMC45NzM5LDUuMjUgMTAuNzUsNS4wMjYxNCAxMC43NSw0Ljc1IFoiIGlkPSJTaGFwZSI+PC9wYXRoPgogICAgICAgICAgICAgICAgPHBhdGggZD0iTTEwLjYyNSwwIEM3LjY1NjUsMCA1LjI1LDIuNDA2NDcgNS4yNSw1LjM3NSBDNS4yNSw1Ljc4MDk4IDUuMjk1MTQsNi4xNzcxIDUuMzgwODgsNi41NTg1IEwwLjIxOTY3LDExLjcxOTcgQzAuMDc5MDIsMTEuODYwMyAwLDEyLjA1MTEgMCwxMi4yNSBMMCwxNS4yNSBDMCwxNS42NjQyIDAuMzM1NzksMTYgMC43NSwxNiBMMy43NDY2MSwxNiBDNC4zMDA3NiwxNiA0Ljc1LDE1LjU1MDggNC43NSwxNC45OTY2IEw0Ljc1LDE0IEw1Ljc0NjYxLDE0IEM2LjMwMDgsMTQgNi43NSwxMy41NTA4IDYuNzUsMTIuOTk2NiBMNi43NSwxMS41IEw4LDExLjUgQzguMTk4OSwxMS41IDguMzg5NywxMS40MjEgOC41MzAzLDExLjI4MDMgTDkuMjQwOCwxMC41Njk5IEM5LjY4MywxMC42ODc1IDEwLjE0NzIsMTAuNzUgMTAuNjI1LDEwLjc1IEMxMy41OTM1LDEwLjc1IDE2LDguMzQzNSAxNiw1LjM3NSBDMTYsMi40MDY0NyAxMy41OTM1LDAgMTAuNjI1LDAgWiBNNi43NSw1LjM3NSBDNi43NSwzLjIzNDkgOC40ODQ5LDEuNSAxMC42MjUsMS41IEMxMi43NjUxLDEuNSAxNC41LDMuMjM0OSAxNC41LDUuMzc1IEMxNC41LDcuNTE1MSAxMi43NjUxLDkuMjUgMTAuNjI1LDkuMjUgQzEwLjE1NDUsOS4yNSA5LjcwNTMsOS4xNjY1IDkuMjkwMSw5LjAxNDIgQzkuMDE1OCw4LjkxMzUgOC43MDgsOC45ODEzIDguNTAxNCw5LjE4NzkgTDcuNjg5MywxMCBMNiwxMCBDNS41ODU3OSwxMCA1LjI1LDEwLjMzNTggNS4yNSwxMC43NSBMNS4yNSwxMi41IEw0LDEyLjUgQzMuNTg1NzksMTIuNSAzLjI1LDEyLjgzNTggMy4yNSwxMy4yNSBMMy4yNSwxNC41IEwxLjUsMTQuNSBMMS41LDEyLjU2MDcgTDYuNzQ4Myw3LjMxMjQgQzYuOTQ2Nyw3LjExNCA3LjAxNzcsNi44MjE0IDYuOTMyNSw2LjU1NDEgQzYuODE0MSw2LjE4MzMgNi43NSw1Ljc4NzM1IDYuNzUsNS4zNzUgWiIgaWQ9IlNoYXBlIj48L3BhdGg+CiAgICAgICAgICAgIDwvZz4KICAgICAgICA8L2c+CiAgICA8L2c+Cjwvc3ZnPg==';
exports.ddgPasswordIconFocused = ddgPasswordIconFocused;
const ddgCcIconBase = 'data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgZmlsbD0ibm9uZSI+CiAgICA8cGF0aCBkPSJNNSA5Yy0uNTUyIDAtMSAuNDQ4LTEgMXYyYzAgLjU1Mi40NDggMSAxIDFoM2MuNTUyIDAgMS0uNDQ4IDEtMXYtMmMwLS41NTItLjQ0OC0xLTEtMUg1eiIgZmlsbD0iIzAwMCIvPgogICAgPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xIDZjMC0yLjIxIDEuNzktNCA0LTRoMTRjMi4yMSAwIDQgMS43OSA0IDR2MTJjMCAyLjIxLTEuNzkgNC00IDRINWMtMi4yMSAwLTQtMS43OS00LTRWNnptNC0yYy0xLjEwNSAwLTIgLjg5NS0yIDJ2OWgxOFY2YzAtMS4xMDUtLjg5NS0yLTItMkg1em0wIDE2Yy0xLjEwNSAwLTItLjg5NS0yLTJoMThjMCAxLjEwNS0uODk1IDItMiAySDV6IiBmaWxsPSIjMDAwIi8+Cjwvc3ZnPgo=';
exports.ddgCcIconBase = ddgCcIconBase;
const ddgCcIconFilled = 'data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgZmlsbD0ibm9uZSI+CiAgICA8cGF0aCBkPSJNNSA5Yy0uNTUyIDAtMSAuNDQ4LTEgMXYyYzAgLjU1Mi40NDggMSAxIDFoM2MuNTUyIDAgMS0uNDQ4IDEtMXYtMmMwLS41NTItLjQ0OC0xLTEtMUg1eiIgZmlsbD0iIzc2NDMxMCIvPgogICAgPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xIDZjMC0yLjIxIDEuNzktNCA0LTRoMTRjMi4yMSAwIDQgMS43OSA0IDR2MTJjMCAyLjIxLTEuNzkgNC00IDRINWMtMi4yMSAwLTQtMS43OS00LTRWNnptNC0yYy0xLjEwNSAwLTIgLjg5NS0yIDJ2OWgxOFY2YzAtMS4xMDUtLjg5NS0yLTItMkg1em0wIDE2Yy0xLjEwNSAwLTItLjg5NS0yLTJoMThjMCAxLjEwNS0uODk1IDItMiAySDV6IiBmaWxsPSIjNzY0MzEwIi8+Cjwvc3ZnPgo=';
exports.ddgCcIconFilled = ddgCcIconFilled;
const ddgIdentityIconBase = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgZmlsbD0ibm9uZSI+CiAgICA8cGF0aCBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0iTTEyIDIxYzIuMTQzIDAgNC4xMTEtLjc1IDUuNjU3LTItLjYyNi0uNTA2LTEuMzE4LS45MjctMi4wNi0xLjI1LTEuMS0uNDgtMi4yODUtLjczNS0zLjQ4Ni0uNzUtMS4yLS4wMTQtMi4zOTIuMjExLTMuNTA0LjY2NC0uODE3LjMzMy0xLjU4Ljc4My0yLjI2NCAxLjMzNiAxLjU0NiAxLjI1IDMuNTE0IDIgNS42NTcgMnptNC4zOTctNS4wODNjLjk2Ny40MjIgMS44NjYuOTggMi42NzIgMS42NTVDMjAuMjc5IDE2LjAzOSAyMSAxNC4xMDQgMjEgMTJjMC00Ljk3LTQuMDMtOS05LTlzLTkgNC4wMy05IDljMCAyLjEwNC43MjIgNC4wNCAxLjkzMiA1LjU3Mi44NzQtLjczNCAxLjg2LTEuMzI4IDIuOTIxLTEuNzYgMS4zNi0uNTU0IDIuODE2LS44MyA0LjI4My0uODExIDEuNDY3LjAxOCAyLjkxNi4zMyA0LjI2LjkxNnpNMTIgMjNjNi4wNzUgMCAxMS00LjkyNSAxMS0xMVMxOC4wNzUgMSAxMiAxIDEgNS45MjUgMSAxMnM0LjkyNSAxMSAxMSAxMXptMy0xM2MwIDEuNjU3LTEuMzQzIDMtMyAzcy0zLTEuMzQzLTMtMyAxLjM0My0zIDMtMyAzIDEuMzQzIDMgM3ptMiAwYzAgMi43NjEtMi4yMzkgNS01IDVzLTUtMi4yMzktNS01IDIuMjM5LTUgNS01IDUgMi4yMzkgNSA1eiIgZmlsbD0iIzAwMCIvPgo8L3N2Zz4KPHBhdGggeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0iTTEyIDIxYzIuMTQzIDAgNC4xMTEtLjc1IDUuNjU3LTItLjYyNi0uNTA2LTEuMzE4LS45MjctMi4wNi0xLjI1LTEuMS0uNDgtMi4yODUtLjczNS0zLjQ4Ni0uNzUtMS4yLS4wMTQtMi4zOTIuMjExLTMuNTA0LjY2NC0uODE3LjMzMy0xLjU4Ljc4My0yLjI2NCAxLjMzNiAxLjU0NiAxLjI1IDMuNTE0IDIgNS42NTcgMnptNC4zOTctNS4wODNjLjk2Ny40MjIgMS44NjYuOTggMi42NzIgMS42NTVDMjAuMjc5IDE2LjAzOSAyMSAxNC4xMDQgMjEgMTJjMC00Ljk3LTQuMDMtOS05LTlzLTkgNC4wMy05IDljMCAyLjEwNC43MjIgNC4wNCAxLjkzMiA1LjU3Mi44NzQtLjczNCAxLjg2LTEuMzI4IDIuOTIxLTEuNzYgMS4zNi0uNTU0IDIuODE2LS44MyA0LjI4My0uODExIDEuNDY3LjAxOCAyLjkxNi4zMyA0LjI2LjkxNnpNMTIgMjNjNi4wNzUgMCAxMS00LjkyNSAxMS0xMVMxOC4wNzUgMSAxMiAxIDEgNS45MjUgMSAxMnM0LjkyNSAxMSAxMSAxMXptMy0xM2MwIDEuNjU3LTEuMzQzIDMtMyAzcy0zLTEuMzQzLTMtMyAxLjM0My0zIDMtMyAzIDEuMzQzIDMgM3ptMiAwYzAgMi43NjEtMi4yMzkgNS01IDVzLTUtMi4yMzktNS01IDIuMjM5LTUgNS01IDUgMi4yMzkgNSA1eiIgZmlsbD0iIzAwMCIvPgo8c3ZnIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgd2lkdGg9IjI0IiBoZWlnaHQ9IjI0IiBmaWxsPSJub25lIj4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xMiAyMWMyLjE0MyAwIDQuMTExLS43NSA1LjY1Ny0yLS42MjYtLjUwNi0xLjMxOC0uOTI3LTIuMDYtMS4yNS0xLjEtLjQ4LTIuMjg1LS43MzUtMy40ODYtLjc1LTEuMi0uMDE0LTIuMzkyLjIxMS0zLjUwNC42NjQtLjgxNy4zMzMtMS41OC43ODMtMi4yNjQgMS4zMzYgMS41NDYgMS4yNSAzLjUxNCAyIDUuNjU3IDJ6bTQuMzk3LTUuMDgzYy45NjcuNDIyIDEuODY2Ljk4IDIuNjcyIDEuNjU1QzIwLjI3OSAxNi4wMzkgMjEgMTQuMTA0IDIxIDEyYzAtNC45Ny00LjAzLTktOS05cy05IDQuMDMtOSA5YzAgMi4xMDQuNzIyIDQuMDQgMS45MzIgNS41NzIuODc0LS43MzQgMS44Ni0xLjMyOCAyLjkyMS0xLjc2IDEuMzYtLjU1NCAyLjgxNi0uODMgNC4yODMtLjgxMSAxLjQ2Ny4wMTggMi45MTYuMzMgNC4yNi45MTZ6TTEyIDIzYzYuMDc1IDAgMTEtNC45MjUgMTEtMTFTMTguMDc1IDEgMTIgMSAxIDUuOTI1IDEgMTJzNC45MjUgMTEgMTEgMTF6bTMtMTNjMCAxLjY1Ny0xLjM0MyAzLTMgM3MtMy0xLjM0My0zLTMgMS4zNDMtMyAzLTMgMyAxLjM0MyAzIDN6bTIgMGMwIDIuNzYxLTIuMjM5IDUtNSA1cy01LTIuMjM5LTUtNSAyLjIzOS01IDUtNSA1IDIuMjM5IDUgNXoiIGZpbGw9IiMwMDAiLz4KPC9zdmc+Cg==";
exports.ddgIdentityIconBase = ddgIdentityIconBase;

},{}],43:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.CSS_STYLES = void 0;
const CSS_STYLES = ".wrapper *, .wrapper *::before, .wrapper *::after {\n    box-sizing: border-box;\n}\n.wrapper {\n    position: fixed;\n    top: 0;\n    left: 0;\n    padding: 0;\n    font-family: 'DDG_ProximaNova', 'Proxima Nova', -apple-system,\n    BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu',\n    'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif;\n    -webkit-font-smoothing: antialiased;\n    /* move it offscreen to avoid flashing */\n    transform: translate(-1000px);\n    z-index: 2147483647;\n}\n:not(.top-autofill).wrapper--data {\n    font-family: 'SF Pro Text', -apple-system,\n    BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu',\n    'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif;\n}\n:not(.top-autofill) .tooltip {\n    position: absolute;\n    width: 300px;\n    max-width: calc(100vw - 25px);\n    z-index: 2147483647;\n}\n.tooltip--data, #topAutofill {\n    background-color: rgba(242, 240, 240, 0.9);\n    -webkit-backdrop-filter: blur(40px);\n    backdrop-filter: blur(40px);\n}\n.tooltip--data {\n    padding: 6px;\n    font-size: 13px;\n    line-height: 14px;\n    width: 315px;\n}\n:not(.top-autofill) .tooltip--data {\n    top: 100%;\n    left: 100%;\n    border: 0.5px solid rgba(0, 0, 0, 0.2);\n    border-radius: 6px;\n    box-shadow: 0 10px 20px rgba(0, 0, 0, 0.32);\n}\n:not(.top-autofill) .tooltip--email {\n    top: calc(100% + 6px);\n    right: calc(100% - 46px);\n    padding: 8px;\n    border: 1px solid #D0D0D0;\n    border-radius: 10px;\n    background-color: #FFFFFF;\n    font-size: 14px;\n    line-height: 1.3;\n    color: #333333;\n    box-shadow: 0 10px 20px rgba(0, 0, 0, 0.15);\n}\n.tooltip--email::before,\n.tooltip--email::after {\n    content: \"\";\n    width: 0;\n    height: 0;\n    border-left: 10px solid transparent;\n    border-right: 10px solid transparent;\n    display: block;\n    border-bottom: 8px solid #D0D0D0;\n    position: absolute;\n    right: 20px;\n}\n.tooltip--email::before {\n    border-bottom-color: #D0D0D0;\n    top: -9px;\n}\n.tooltip--email::after {\n    border-bottom-color: #FFFFFF;\n    top: -8px;\n}\n\n/* Buttons */\n.tooltip__button {\n    display: flex;\n    width: 100%;\n    padding: 8px 0px;\n    font-family: inherit;\n    color: inherit;\n    background: transparent;\n    border: none;\n    border-radius: 6px;\n}\n.tooltip__button.currentFocus,\n.tooltip__button:hover {\n    background-color: rgba(0, 121, 242, 0.8);\n    color: #FFFFFF;\n}\n\n/* Data autofill tooltip specific */\n.tooltip__button--data {\n    min-height: 48px;\n    flex-direction: row;\n    justify-content: flex-start;\n    font-size: inherit;\n    font-weight: 500;\n    line-height: 16px;\n    text-align: left;\n}\n.tooltip__button--data > * {\n    opacity: 0.9;\n}\n.tooltip__button--data:first-child {\n    margin-top: 0;\n}\n.tooltip__button--data:last-child {\n    margin-bottom: 0;\n}\n.tooltip__button--data::before {\n    content: '';\n    flex-shrink: 0;\n    display: block;\n    width: 32px;\n    height: 32px;\n    margin: 0 8px;\n    background-size: 24px 24px;\n    background-repeat: no-repeat;\n    background-position: center 1px;\n}\n.tooltip__button--data.currentFocus::before,\n.tooltip__button--data:hover::before {\n    filter: invert(100%);\n}\n.tooltip__button__text-container {\n    margin: auto 0;\n}\n.label {\n    display: block;\n    font-weight: 400;\n    letter-spacing: -0.25px;\n    color: rgba(0,0,0,.8);\n    line-height: 13px;\n}\n.label + .label {\n    margin-top: 5px;\n}\n.label.label--medium {\n    letter-spacing: -0.08px;\n    color: rgba(0,0,0,.9)\n}\n.label.label--small {\n    font-size: 11px;\n    font-weight: 400;\n    letter-spacing: 0.06px;\n    color: rgba(0,0,0,0.6);\n}\n.tooltip__button.currentFocus .label,\n.tooltip__button:hover .label,\n.tooltip__button.currentFocus .label,\n.tooltip__button:hover .label {\n    color: #FFFFFF;\n}\n\n/* Icons */\n.tooltip__button--data--credentials::before {\n    /* TODO: use dynamically from src/UI/img/ddgPasswordIcon.js */\n    background-image: url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjQiIGhlaWdodD0iMjQiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik05LjYzNiA4LjY4MkM5LjYzNiA1LjU0NCAxMi4xOCAzIDE1LjMxOCAzIDE4LjQ1NiAzIDIxIDUuNTQ0IDIxIDguNjgyYzAgMy4xMzgtMi41NDQgNS42ODItNS42ODIgNS42ODItLjY5MiAwLTEuMzUzLS4xMjQtMS45NjQtLjM0OS0uMzcyLS4xMzctLjc5LS4wNDEtMS4wNjYuMjQ1bC0uNzEzLjc0SDEwYy0uNTUyIDAtMSAuNDQ4LTEgMXYySDdjLS41NTIgMC0xIC40NDgtMSAxdjJIM3YtMi44ODFsNi42NjgtNi42NjhjLjI2NS0uMjY2LjM2LS42NTguMjQ0LTEuMDE1LS4xNzktLjU1MS0uMjc2LTEuMTQtLjI3Ni0xLjc1NHpNMTUuMzE4IDFjLTQuMjQyIDAtNy42ODIgMy40NC03LjY4MiA3LjY4MiAwIC42MDcuMDcxIDEuMi4yMDUgMS43NjdsLTYuNTQ4IDYuNTQ4Yy0uMTg4LjE4OC0uMjkzLjQ0Mi0uMjkzLjcwOFYyMmMwIC4yNjUuMTA1LjUyLjI5My43MDcuMTg3LjE4OC40NDIuMjkzLjcwNy4yOTNoNGMxLjEwNSAwIDItLjg5NSAyLTJ2LTFoMWMxLjEwNSAwIDItLjg5NSAyLTJ2LTFoMWMuMjcyIDAgLjUzMi0uMTEuNzItLjMwNmwuNTc3LS42Yy42NDUuMTc2IDEuMzIzLjI3IDIuMDIxLjI3IDQuMjQzIDAgNy42ODItMy40NCA3LjY4Mi03LjY4MkMyMyA0LjQzOSAxOS41NiAxIDE1LjMxOCAxek0xNSA4YzAtLjU1Mi40NDgtMSAxLTFzMSAuNDQ4IDEgMS0uNDQ4IDEtMSAxLTEtLjQ0OC0xLTF6bTEtM2MtMS42NTcgMC0zIDEuMzQzLTMgM3MxLjM0MyAzIDMgMyAzLTEuMzQzIDMtMy0xLjM0My0zLTMtM3oiIGZpbGw9IiMwMDAiIGZpbGwtb3BhY2l0eT0iLjkiLz4KPC9zdmc+');\n}\n.tooltip__button--data--creditCards::before {\n    background-image: url('data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgZmlsbD0ibm9uZSI+CiAgICA8cGF0aCBkPSJNNSA5Yy0uNTUyIDAtMSAuNDQ4LTEgMXYyYzAgLjU1Mi40NDggMSAxIDFoM2MuNTUyIDAgMS0uNDQ4IDEtMXYtMmMwLS41NTItLjQ0OC0xLTEtMUg1eiIgZmlsbD0iIzAwMCIvPgogICAgPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xIDZjMC0yLjIxIDEuNzktNCA0LTRoMTRjMi4yMSAwIDQgMS43OSA0IDR2MTJjMCAyLjIxLTEuNzkgNC00IDRINWMtMi4yMSAwLTQtMS43OS00LTRWNnptNC0yYy0xLjEwNSAwLTIgLjg5NS0yIDJ2OWgxOFY2YzAtMS4xMDUtLjg5NS0yLTItMkg1em0wIDE2Yy0xLjEwNSAwLTItLjg5NS0yLTJoMThjMCAxLjEwNS0uODk1IDItMiAySDV6IiBmaWxsPSIjMDAwIi8+Cjwvc3ZnPgo=');\n}\n.tooltip__button--data--identities::before {\n    background-image: url('data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgZmlsbD0ibm9uZSI+CiAgICA8cGF0aCBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0iTTEyIDIxYzIuMTQzIDAgNC4xMTEtLjc1IDUuNjU3LTItLjYyNi0uNTA2LTEuMzE4LS45MjctMi4wNi0xLjI1LTEuMS0uNDgtMi4yODUtLjczNS0zLjQ4Ni0uNzUtMS4yLS4wMTQtMi4zOTIuMjExLTMuNTA0LjY2NC0uODE3LjMzMy0xLjU4Ljc4My0yLjI2NCAxLjMzNiAxLjU0NiAxLjI1IDMuNTE0IDIgNS42NTcgMnptNC4zOTctNS4wODNjLjk2Ny40MjIgMS44NjYuOTggMi42NzIgMS42NTVDMjAuMjc5IDE2LjAzOSAyMSAxNC4xMDQgMjEgMTJjMC00Ljk3LTQuMDMtOS05LTlzLTkgNC4wMy05IDljMCAyLjEwNC43MjIgNC4wNCAxLjkzMiA1LjU3Mi44NzQtLjczNCAxLjg2LTEuMzI4IDIuOTIxLTEuNzYgMS4zNi0uNTU0IDIuODE2LS44MyA0LjI4My0uODExIDEuNDY3LjAxOCAyLjkxNi4zMyA0LjI2LjkxNnpNMTIgMjNjNi4wNzUgMCAxMS00LjkyNSAxMS0xMVMxOC4wNzUgMSAxMiAxIDEgNS45MjUgMSAxMnM0LjkyNSAxMSAxMSAxMXptMy0xM2MwIDEuNjU3LTEuMzQzIDMtMyAzcy0zLTEuMzQzLTMtMyAxLjM0My0zIDMtMyAzIDEuMzQzIDMgM3ptMiAwYzAgMi43NjEtMi4yMzkgNS01IDVzLTUtMi4yMzktNS01IDIuMjM5LTUgNS01IDUgMi4yMzkgNSA1eiIgZmlsbD0iIzAwMCIvPgo8L3N2Zz4=');\n}\n\nhr {\n    display: block;\n    margin: 5px 10px;\n    border: none; /* reset the border */\n    border-top: 1px solid rgba(0,0,0,.1);\n}\n\nhr:first-child {\n    display: none;\n}\n\n#privateAddress {\n    align-items: flex-start;\n}\n#personalAddress::before,\n#privateAddress::before,\n#personalAddress.currentFocus::before,\n#personalAddress:hover::before,\n#privateAddress.currentFocus::before,\n#privateAddress:hover::before {\n    filter: none;\n    background-image: url('data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjI0IiB2aWV3Qm94PSIwIDAgNDQgNDQiIHdpZHRoPSIyNCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+PGxpbmVhckdyYWRpZW50IGlkPSJhIj48c3RvcCBvZmZzZXQ9Ii4wMSIgc3RvcC1jb2xvcj0iIzYxNzZiOSIvPjxzdG9wIG9mZnNldD0iLjY5IiBzdG9wLWNvbG9yPSIjMzk0YTlmIi8+PC9saW5lYXJHcmFkaWVudD48bGluZWFyR3JhZGllbnQgaWQ9ImIiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMTMuOTI5NyIgeDI9IjE3LjA3MiIgeGxpbms6aHJlZj0iI2EiIHkxPSIxNi4zOTgiIHkyPSIxNi4zOTgiLz48bGluZWFyR3JhZGllbnQgaWQ9ImMiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMjMuODExNSIgeDI9IjI2LjY3NTIiIHhsaW5rOmhyZWY9IiNhIiB5MT0iMTQuOTY3OSIgeTI9IjE0Ljk2NzkiLz48bWFzayBpZD0iZCIgaGVpZ2h0PSI0MCIgbWFza1VuaXRzPSJ1c2VyU3BhY2VPblVzZSIgd2lkdGg9IjQwIiB4PSIyIiB5PSIyIj48cGF0aCBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Im0yMi4wMDAzIDQxLjA2NjljMTAuNTMwMiAwIDE5LjA2NjYtOC41MzY0IDE5LjA2NjYtMTkuMDY2NiAwLTEwLjUzMDMtOC41MzY0LTE5LjA2NjcxLTE5LjA2NjYtMTkuMDY2NzEtMTAuNTMwMyAwLTE5LjA2NjcxIDguNTM2NDEtMTkuMDY2NzEgMTkuMDY2NzEgMCAxMC41MzAyIDguNTM2NDEgMTkuMDY2NiAxOS4wNjY3MSAxOS4wNjY2eiIgZmlsbD0iI2ZmZiIgZmlsbC1ydWxlPSJldmVub2RkIi8+PC9tYXNrPjxwYXRoIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0ibTIyIDQ0YzEyLjE1MDMgMCAyMi05Ljg0OTcgMjItMjIgMC0xMi4xNTAyNi05Ljg0OTctMjItMjItMjItMTIuMTUwMjYgMC0yMiA5Ljg0OTc0LTIyIDIyIDAgMTIuMTUwMyA5Ljg0OTc0IDIyIDIyIDIyeiIgZmlsbD0iI2RlNTgzMyIgZmlsbC1ydWxlPSJldmVub2RkIi8+PGcgbWFzaz0idXJsKCNkKSI+PHBhdGggY2xpcC1ydWxlPSJldmVub2RkIiBkPSJtMjYuMDgxMyA0MS42Mzg2Yy0uOTIwMy0xLjc4OTMtMS44MDAzLTMuNDM1Ni0yLjM0NjYtNC41MjQ2LTEuNDUyLTIuOTA3Ny0yLjkxMTQtNy4wMDctMi4yNDc3LTkuNjUwNy4xMjEtLjQ4MDMtMS4zNjc3LTE3Ljc4Njk5LTIuNDItMTguMzQ0MzItMS4xNjk3LS42MjMzMy0zLjcxMDctMS40NDQ2Ny01LjAyNy0xLjY2NDY3LS45MTY3LS4xNDY2Ni0xLjEyNTcuMTEtMS41MTA3LjE2ODY3LjM2My4wMzY2NyAyLjA5Ljg4NzMzIDIuNDIzNy45MzUtLjMzMzcuMjI3MzMtMS4zMi0uMDA3MzMtMS45NTA3LjI3MTMzLS4zMTkuMTQ2NjctLjU1NzMuNjg5MzQtLjU1Ljk0NiAxLjc5NjctLjE4MzMzIDQuNjA1NC0uMDAzNjYgNi4yNy43MzMyOS0xLjMyMzYuMTUwNC0zLjMzMy4zMTktNC4xOTgzLjc3MzctMi41MDggMS4zMi0zLjYxNTMgNC40MTEtMi45NTUzIDguMTE0My42NTYzIDMuNjk2IDMuNTY0IDE3LjE3ODQgNC40OTE2IDIxLjY4MS45MjQgNC40OTkgMTEuNTUzNyAzLjU1NjcgMTAuMDE3NC41NjF6IiBmaWxsPSIjZDVkN2Q4IiBmaWxsLXJ1bGU9ImV2ZW5vZGQiLz48cGF0aCBkPSJtMjIuMjg2NSAyNi44NDM5Yy0uNjYgMi42NDM2Ljc5MiA2LjczOTMgMi4yNDc2IDkuNjUwNi40ODkxLjk3MjcgMS4yNDM4IDIuMzkyMSAyLjA1NTggMy45NjM3LTEuODk0LjQ2OTMtNi40ODk1IDEuMTI2NC05LjcxOTEgMC0uOTI0LTQuNDkxNy0zLjgzMTctMTcuOTc3Ny00LjQ5NTMtMjEuNjgxLS42Ni0zLjcwMzMgMC02LjM0NyAyLjUxNTMtNy42NjcuODYxNy0uNDU0NyAyLjA5MzctLjc4NDcgMy40MTM3LS45MzEzLTEuNjY0Ny0uNzQwNy0zLjYzNzQtMS4wMjY3LTUuNDQxNC0uODQzMzYtLjAwNzMtLjc2MjY3IDEuMzM4NC0uNzE4NjcgMS44NDQ0LTEuMDYzMzQtLjMzMzctLjA0NzY2LTEuMTYyNC0uNzk1NjYtMS41MjktLjgzMjMzIDIuMjg4My0uMzkyNDQgNC42NDIzLS4wMjEzOCA2LjY5OSAxLjA1NiAxLjA0ODYuNTYxIDEuNzg5MyAxLjE2MjMzIDIuMjQ3NiAxLjc5MzAzIDEuMTk1NC4yMjczIDIuMjUxNC42NiAyLjk0MDcgMS4zNDkzIDIuMTE5MyAyLjExNTcgNC4wMTEzIDYuOTUyIDMuMjE5MyA5LjczMTMtLjIyMzYuNzctLjczMzMgMS4zMzEtMS4zNzEzIDEuNzk2Ny0xLjIzOTMuOTAyLTEuMDE5My0xLjA0NS00LjEwMy45NzE3LS4zOTk3LjI2MDMtLjM5OTcgMi4yMjU2LS41MjQzIDIuNzA2eiIgZmlsbD0iI2ZmZiIvPjwvZz48ZyBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGZpbGwtcnVsZT0iZXZlbm9kZCI+PHBhdGggZD0ibTE2LjY3MjQgMjAuMzU0Yy43Njc1IDAgMS4zODk2LS42MjIxIDEuMzg5Ni0xLjM4OTZzLS42MjIxLTEuMzg5Ny0xLjM4OTYtMS4zODk3LTEuMzg5Ny42MjIyLTEuMzg5NyAxLjM4OTcuNjIyMiAxLjM4OTYgMS4zODk3IDEuMzg5NnoiIGZpbGw9IiMyZDRmOGUiLz48cGF0aCBkPSJtMTcuMjkyNCAxOC44NjE3Yy4xOTg1IDAgLjM1OTQtLjE2MDguMzU5NC0uMzU5M3MtLjE2MDktLjM1OTMtLjM1OTQtLjM1OTNjLS4xOTg0IDAtLjM1OTMuMTYwOC0uMzU5My4zNTkzcy4xNjA5LjM1OTMuMzU5My4zNTkzeiIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Im0yNS45NTY4IDE5LjMzMTFjLjY1ODEgMCAxLjE5MTctLjUzMzUgMS4xOTE3LTEuMTkxNyAwLS42NTgxLS41MzM2LTEuMTkxNi0xLjE5MTctMS4xOTE2cy0xLjE5MTcuNTMzNS0xLjE5MTcgMS4xOTE2YzAgLjY1ODIuNTMzNiAxLjE5MTcgMS4xOTE3IDEuMTkxN3oiIGZpbGw9IiMyZDRmOGUiLz48cGF0aCBkPSJtMjYuNDg4MiAxOC4wNTExYy4xNzAxIDAgLjMwOC0uMTM3OS4zMDgtLjMwOHMtLjEzNzktLjMwOC0uMzA4LS4zMDgtLjMwOC4xMzc5LS4zMDguMzA4LjEzNzkuMzA4LjMwOC4zMDh6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTE3LjA3MiAxNC45NDJzLTEuMDQ4Ni0uNDc2Ni0yLjA2NDMuMTY1Yy0xLjAxNTcuNjM4LS45NzkgMS4yOTA3LS45NzkgMS4yOTA3cy0uNTM5LTEuMjAyNy44OTgzLTEuNzkzYzEuNDQxLS41ODY3IDIuMTQ1LjMzNzMgMi4xNDUuMzM3M3oiIGZpbGw9InVybCgjYikiLz48cGF0aCBkPSJtMjYuNjc1MiAxNC44NDY3cy0uNzUxNy0uNDI5LTEuMzM4My0uNDIxN2MtMS4xOTkuMDE0Ny0xLjUyNTQuNTQyNy0xLjUyNTQuNTQyN3MuMjAxNy0xLjI2MTQgMS43MzQ0LTEuMDA4NGMuNDk5Ny4wOTE0LjkyMjMuNDIzNCAxLjEyOTMuODg3NHoiIGZpbGw9InVybCgjYykiLz48cGF0aCBkPSJtMjAuOTI1OCAyNC4zMjFjLjEzOTMtLjg0MzMgMi4zMS0yLjQzMSAzLjg1LTIuNTMgMS41NC0uMDk1MyAyLjAxNjctLjA3MzMgMy4zLS4zODEzIDEuMjg3LS4zMDQzIDQuNTk4LTEuMTI5MyA1LjUxMS0xLjU1NDcuOTE2Ny0uNDIxNiA0LjgwMzMuMjA5IDIuMDY0MyAxLjczOC0xLjE4NDMuNjYzNy00LjM3OCAxLjg4MS02LjY2MjMgMi41NjMtMi4yODA3LjY4Mi0zLjY2My0uNjUyNi00LjQyMi40Njk0LS42MDEzLjg5MS0uMTIxIDIuMTEyIDIuNjAzMyAyLjM2NSAzLjY4MTQuMzQxIDcuMjA4Ny0xLjY1NzQgNy41OTc0LS41OTQuMzg4NiAxLjA2MzMtMy4xNjA3IDIuMzgzMy01LjMyNCAyLjQyNzMtMi4xNjM0LjA0MDMtNi41MTk0LTEuNDMtNy4xNzItMS44ODQ3LS42NTY0LS40NTEtMS41MjU0LTEuNTE0My0xLjM0NTctMi42MTh6IiBmaWxsPSIjZmRkMjBhIi8+PHBhdGggZD0ibTI4Ljg4MjUgMzEuODM4NmMtLjc3NzMtLjE3MjQtNC4zMTIgMi41MDA2LTQuMzEyIDIuNTAwNmguMDAzN2wtLjE2NSAyLjA1MzRzNC4wNDA2IDEuNjUzNiA0LjczIDEuMzk3Yy42ODkzLS4yNjQuNTE3LTUuNzc1LS4yNTY3LTUuOTUxem0tMTEuNTQ2MyAxLjAzNGMuMDg0My0xLjExODQgNS4yNTQzIDEuNjQyNiA1LjI1NDMgMS42NDI2bC4wMDM3LS4wMDM2LjI1NjYgMi4xNTZzLTQuMzA4MyAyLjU4MTMtNC45MTMzIDIuMjM2NmMtLjYwMTMtLjM0NDYtLjY4OTMtNC45MDk2LS42MDEzLTYuMDMxNnoiIGZpbGw9IiM2NWJjNDYiLz48cGF0aCBkPSJtMjEuMzQgMzQuODA0OWMwIDEuODA3Ny0uMjYwNCAyLjU4NS41MTMzIDIuNzU3NC43NzczLjE3MjMgMi4yNDAzIDAgMi43NjEtLjM0NDcuNTEzMy0uMzQ0Ny4wODQzLTIuNjY5My0uMDg4LTMuMTAycy0zLjE5LS4wODgtMy4xOS42ODkzeiIgZmlsbD0iIzQzYTI0NCIvPjxwYXRoIGQ9Im0yMS42NzAxIDM0LjQwNTFjMCAxLjgwNzYtLjI2MDQgMi41ODEzLjUxMzMgMi43NTM2Ljc3MzcuMTc2IDIuMjM2NyAwIDIuNzU3My0uMzQ0Ni41MTctLjM0NDcuMDg4LTIuNjY5NC0uMDg0My0zLjEwMi0uMTcyMy0uNDMyNy0zLjE5LS4wODQ0LTMuMTkuNjg5M3oiIGZpbGw9IiM2NWJjNDYiLz48cGF0aCBkPSJtMjIuMDAwMiA0MC40NDgxYzEwLjE4ODUgMCAxOC40NDc5LTguMjU5NCAxOC40NDc5LTE4LjQ0NzlzLTguMjU5NC0xOC40NDc5NS0xOC40NDc5LTE4LjQ0Nzk1LTE4LjQ0Nzk1IDguMjU5NDUtMTguNDQ3OTUgMTguNDQ3OTUgOC4yNTk0NSAxOC40NDc5IDE4LjQ0Nzk1IDE4LjQ0Nzl6bTAgMS43MTg3YzExLjEzNzcgMCAyMC4xNjY2LTkuMDI4OSAyMC4xNjY2LTIwLjE2NjYgMC0xMS4xMzc4LTkuMDI4OS0yMC4xNjY3LTIwLjE2NjYtMjAuMTY2Ny0xMS4xMzc4IDAtMjAuMTY2NyA5LjAyODktMjAuMTY2NyAyMC4xNjY3IDAgMTEuMTM3NyA5LjAyODkgMjAuMTY2NiAyMC4xNjY3IDIwLjE2NjZ6IiBmaWxsPSIjZmZmIi8+PC9nPjwvc3ZnPg==');\n}\n\n/* Email tooltip specific */\n.tooltip__button--email {\n    flex-direction: column;\n    justify-content: center;\n    align-items: flex-start;\n    font-size: 14px;\n    padding: 4px 8px;\n}\n.tooltip__button--email__primary-text {\n    font-weight: bold;\n}\n.tooltip__button--email__secondary-text {\n    font-size: 12px;\n}\n";
exports.CSS_STYLES = CSS_STYLES;

},{}],44:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.wkSendAndWait = exports.wkSend = exports.MissingWebkitHandler = void 0;

var _captureDdgGlobals = _interopRequireDefault(require("./captureDdgGlobals"));

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * Sends message to the webkit layer (fire and forget)
 * @param {String} handler
 * @param {*} data
 * @param {{hasModernWebkitAPI?: boolean, secret?: string}} opts
 */
const wkSend = function (handler) {
  let data = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let opts = arguments.length > 2 ? arguments[2] : undefined;

  if (!(handler in window.webkit.messageHandlers)) {
    throw new MissingWebkitHandler("Missing webkit handler: '".concat(handler, "'"));
  }

  return window.webkit.messageHandlers[handler].postMessage({ ...data,
    messageHandling: { ...data.messageHandling,
      secret: opts.secret
    }
  });
};
/**
 * Generate a random method name and adds it to the global scope
 * The native layer will use this method to send the response
 * @param {String} randomMethodName
 * @param {Function} callback
 */


exports.wkSend = wkSend;

const generateRandomMethod = (randomMethodName, callback) => {
  _captureDdgGlobals.default.ObjectDefineProperty(_captureDdgGlobals.default.window, randomMethodName, {
    enumerable: false,
    // configurable, To allow for deletion later
    configurable: true,
    writable: false,
    value: function () {
      callback(...arguments);
      delete _captureDdgGlobals.default.window[randomMethodName];
    }
  });
};
/**
 * Sends message to the webkit layer and waits for the specified response
 * @param {String} handler
 * @param {*} data
 * @param {{hasModernWebkitAPI?: boolean, secret?: string}} opts
 * @returns {Promise<*>}
 */


const wkSendAndWait = async function (handler) {
  let data = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let opts = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : {};

  if (opts.hasModernWebkitAPI) {
    const response = await wkSend(handler, data, opts);
    return _captureDdgGlobals.default.JSONparse(response || '{}');
  }

  try {
    const randMethodName = createRandMethodName();
    const key = await createRandKey();
    const iv = createRandIv();
    const {
      ciphertext,
      tag
    } = await new _captureDdgGlobals.default.Promise(resolve => {
      generateRandomMethod(randMethodName, resolve);
      data.messageHandling = {
        methodName: randMethodName,
        secret: opts.secret,
        key: _captureDdgGlobals.default.Arrayfrom(key),
        iv: _captureDdgGlobals.default.Arrayfrom(iv)
      };
      wkSend(handler, data, opts);
    });
    const cipher = new _captureDdgGlobals.default.Uint8Array([...ciphertext, ...tag]);
    const decrypted = await decrypt(cipher, key, iv);
    return _captureDdgGlobals.default.JSONparse(decrypted || '{}');
  } catch (e) {
    // re-throw when the error is a 'MissingWebkitHandler'
    if (e instanceof MissingWebkitHandler) {
      throw e;
    } else {
      console.error('decryption failed', e);
      console.error(e);
      return {
        error: e
      };
    }
  }
};

exports.wkSendAndWait = wkSendAndWait;

const randomString = () => '' + _captureDdgGlobals.default.getRandomValues(new _captureDdgGlobals.default.Uint32Array(1))[0];

const createRandMethodName = () => '_' + randomString();

const algoObj = {
  name: 'AES-GCM',
  length: 256
};

const createRandKey = async () => {
  const key = await _captureDdgGlobals.default.generateKey(algoObj, true, ['encrypt', 'decrypt']);
  const exportedKey = await _captureDdgGlobals.default.exportKey('raw', key);
  return new _captureDdgGlobals.default.Uint8Array(exportedKey);
};

const createRandIv = () => _captureDdgGlobals.default.getRandomValues(new _captureDdgGlobals.default.Uint8Array(12));

const decrypt = async (ciphertext, key, iv) => {
  const cryptoKey = await _captureDdgGlobals.default.importKey('raw', key, 'AES-GCM', false, ['decrypt']);
  const algo = {
    name: 'AES-GCM',
    iv
  };
  let decrypted = await _captureDdgGlobals.default.decrypt(algo, cryptoKey, ciphertext);
  let dec = new _captureDdgGlobals.default.TextDecoder();
  return dec.decode(decrypted);
};

class MissingWebkitHandler extends Error {
  constructor(handlerName) {
    super();

    _defineProperty(this, "handlerName", void 0);

    this.handlerName = handlerName;
  }

}

exports.MissingWebkitHandler = MissingWebkitHandler;

},{"./captureDdgGlobals":45}],45:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;
// Capture the globals we need on page start
const secretGlobals = {
  window,
  // Methods must be bound to their interface, otherwise they throw Illegal invocation
  encrypt: window.crypto.subtle.encrypt.bind(window.crypto.subtle),
  decrypt: window.crypto.subtle.decrypt.bind(window.crypto.subtle),
  generateKey: window.crypto.subtle.generateKey.bind(window.crypto.subtle),
  exportKey: window.crypto.subtle.exportKey.bind(window.crypto.subtle),
  importKey: window.crypto.subtle.importKey.bind(window.crypto.subtle),
  getRandomValues: window.crypto.getRandomValues.bind(window.crypto),
  TextEncoder,
  TextDecoder,
  Uint8Array,
  Uint16Array,
  Uint32Array,
  JSONstringify: window.JSON.stringify,
  JSONparse: window.JSON.parse,
  Arrayfrom: window.Array.from,
  Promise: window.Promise,
  ObjectDefineProperty: window.Object.defineProperty
};
var _default = secretGlobals;
exports.default = _default;

},{}],46:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.buttonMatchesFormType = exports.autofillEnabled = exports.addInlineStyles = exports.SIGN_IN_MSG = exports.ADDRESS_DOMAIN = void 0;
exports.escapeXML = escapeXML;
exports.setValue = exports.sendAndWaitForAnswer = exports.safeExecute = exports.removeInlineStyles = exports.notifyWebApp = exports.isVisible = exports.isLikelyASubmitButton = exports.isEventWithinDax = exports.isAutofillEnabledFromProcessedConfig = exports.getDaxBoundingBox = exports.formatDuckAddress = void 0;

var _matching = require("./Form/matching");

const SIGN_IN_MSG = {
  signMeIn: true
}; // Send a message to the web app (only on DDG domains)

exports.SIGN_IN_MSG = SIGN_IN_MSG;

const notifyWebApp = message => {
  window.postMessage(message, window.origin);
};
/**
 * Sends a message and returns a Promise that resolves with the response
 * @param {{} | Function} msgOrFn - a fn to call or an object to send via postMessage
 * @param {String} expectedResponse - the name of the response
 * @returns {Promise<*>}
 */


exports.notifyWebApp = notifyWebApp;

const sendAndWaitForAnswer = (msgOrFn, expectedResponse) => {
  if (typeof msgOrFn === 'function') {
    msgOrFn();
  } else {
    window.postMessage(msgOrFn, window.origin);
  }

  return new Promise(resolve => {
    const handler = e => {
      if (e.origin !== window.origin) return;
      if (!e.data || e.data && !(e.data[expectedResponse] || e.data.type === expectedResponse)) return;
      resolve(e.data);
      window.removeEventListener('message', handler);
    };

    window.addEventListener('message', handler);
  });
};
/**
 * @param {GlobalConfig} globalConfig
 * @param [processConfig]
 * @return {boolean}
 */


exports.sendAndWaitForAnswer = sendAndWaitForAnswer;

const autofillEnabled = (globalConfig, processConfig) => {
  if (!globalConfig.contentScope) {
    // Return enabled for platforms that haven't implemented the config yet
    return true;
  }

  const {
    contentScope,
    userUnprotectedDomains,
    userPreferences
  } = globalConfig; // Check config on Apple platforms

  const processedConfig = processConfig(contentScope, userUnprotectedDomains, userPreferences);
  return isAutofillEnabledFromProcessedConfig(processedConfig);
};

exports.autofillEnabled = autofillEnabled;

const isAutofillEnabledFromProcessedConfig = processedConfig => {
  const site = processedConfig.site;

  if (site.isBroken || !site.enabledFeatures.includes('autofill')) {
    return false;
  }

  return true;
}; // Access the original setter (needed to bypass React's implementation on mobile)
// @ts-ignore


exports.isAutofillEnabledFromProcessedConfig = isAutofillEnabledFromProcessedConfig;
const originalSet = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
/**
 * Ensures the value is set properly and dispatches events to simulate real user action
 * @param {HTMLInputElement} el
 * @param {string} val
 * @param {GlobalConfig} [config]
 * @return {boolean}
 */

const setValueForInput = (el, val, config) => {
  // Avoid keyboard flashing on Android
  if (!(config !== null && config !== void 0 && config.isAndroid)) {
    el.focus();
  } // todo(Shane): Not sending a 'key' property on these events can cause exceptions on 3rd party listeners that expect it


  el.dispatchEvent(new Event('keydown', {
    bubbles: true
  }));
  originalSet === null || originalSet === void 0 ? void 0 : originalSet.call(el, val);
  const events = [new Event('input', {
    bubbles: true
  }), // todo(Shane): Not sending a 'key' property on these events can cause exceptions on 3rd party listeners that expect it
  new Event('keyup', {
    bubbles: true
  }), new Event('change', {
    bubbles: true
  })];
  events.forEach(ev => el.dispatchEvent(ev)); // We call this again to make sure all forms are happy

  originalSet === null || originalSet === void 0 ? void 0 : originalSet.call(el, val);
  events.forEach(ev => el.dispatchEvent(ev));
  el.blur();
  return true;
};
/**
 * Fires events on a select element to simulate user interaction
 * @param {HTMLSelectElement} el
 */


const fireEventsOnSelect = el => {
  /** @type {Event[]} */
  const events = [new Event('mousedown', {
    bubbles: true
  }), new Event('mouseup', {
    bubbles: true
  }), new Event('click', {
    bubbles: true
  }), new Event('change', {
    bubbles: true
  })]; // Events fire on the select el, not option

  events.forEach(ev => el.dispatchEvent(ev));
  events.forEach(ev => el.dispatchEvent(ev));
  el.blur();
};
/**
 * Selects an option of a select element
 * We assume Select is only used for dates, i.e. in the credit card
 * @param {HTMLSelectElement} el
 * @param {string} val
 * @return {boolean}
 */


const setValueForSelect = (el, val) => {
  const subtype = (0, _matching.getInputSubtype)(el);
  const isMonth = subtype.includes('Month');
  const isZeroBasedNumber = isMonth && el.options[0].value === '0' && el.options.length === 12; // Loop first through all values because they tend to be more precise

  for (const option of el.options) {
    // If values for months are zero-based (Jan === 0), add one to match our data type
    let value = option.value;

    if (isZeroBasedNumber) {
      value = "".concat(Number(value) + 1);
    } // TODO: try to match localised month names


    if (value === String(val)) {
      if (option.selected) return false;
      option.selected = true;
      fireEventsOnSelect(el);
      return true;
    }
  }

  for (const option of el.options) {
    if (option.innerText === String(val)) {
      if (option.selected) return false;
      option.selected = true;
      fireEventsOnSelect(el);
      return true;
    }
  } // If we didn't find a matching option return false


  return false;
};
/**
 * Sets or selects a value to a form element
 * @param {HTMLInputElement | HTMLSelectElement} el
 * @param {string} val
 * @param {GlobalConfig} [config]
 * @return {boolean}
 */


const setValue = (el, val, config) => {
  if (el instanceof HTMLInputElement) return setValueForInput(el, val, config);
  if (el instanceof HTMLSelectElement) return setValueForSelect(el, val);
  return false;
};
/**
 * Use IntersectionObserver v2 to make sure the element is visible when clicked
 * https://developers.google.com/web/updates/2019/02/intersectionobserver-v2
 */


exports.setValue = setValue;

const safeExecute = (el, fn) => {
  const intObs = new IntersectionObserver(changes => {
    for (const change of changes) {
      // Feature detection
      if (typeof change.isVisible === 'undefined') {
        // The browser doesn't support Intersection Observer v2, falling back to v1 behavior.
        change.isVisible = true;
      }

      if (change.isIntersecting && change.isVisible) {
        fn();
      }
    }

    intObs.disconnect();
  }, {
    trackVisibility: true,
    delay: 100
  });
  intObs.observe(el);
};
/**
 * Checks that an element is potentially viewable (even if off-screen)
 * @param {HTMLElement} el
 * @return {boolean}
 */


exports.safeExecute = safeExecute;

const isVisible = el => el.clientWidth !== 0 && el.clientHeight !== 0 && (el.style.opacity !== '' ? parseFloat(el.style.opacity) > 0 : true);
/**
 * Gets the bounding box of the icon
 * @param {HTMLInputElement} input
 * @returns {{top: number, left: number, bottom: number, width: number, x: number, y: number, right: number, height: number}}
 */


exports.isVisible = isVisible;

const getDaxBoundingBox = input => {
  const {
    right: inputRight,
    top: inputTop,
    height: inputHeight
  } = input.getBoundingClientRect();
  const inputRightPadding = parseInt(getComputedStyle(input).paddingRight);
  const width = 30;
  const height = 30;
  const top = inputTop + (inputHeight - height) / 2;
  const right = inputRight - inputRightPadding;
  const left = right - width;
  const bottom = top + height;
  return {
    bottom,
    height,
    left,
    right,
    top,
    width,
    x: left,
    y: top
  };
};
/**
 * Check if a mouse event is within the icon
 * @param {MouseEvent} e
 * @param {HTMLInputElement} input
 * @returns {boolean}
 */


exports.getDaxBoundingBox = getDaxBoundingBox;

const isEventWithinDax = (e, input) => {
  const {
    left,
    right,
    top,
    bottom
  } = getDaxBoundingBox(input);
  const withinX = e.clientX >= left && e.clientX <= right;
  const withinY = e.clientY >= top && e.clientY <= bottom;
  return withinX && withinY;
};
/**
 * Adds inline styles from a prop:value object
 * @param {HTMLElement} el
 * @param {Object<string, string>} styles
 */


exports.isEventWithinDax = isEventWithinDax;

const addInlineStyles = (el, styles) => Object.entries(styles).forEach(_ref => {
  let [property, val] = _ref;
  return el.style.setProperty(property, val, 'important');
});
/**
 * Removes inline styles from a prop:value object
 * @param {HTMLElement} el
 * @param {Object<string, string>} styles
 */


exports.addInlineStyles = addInlineStyles;

const removeInlineStyles = (el, styles) => Object.keys(styles).forEach(property => el.style.removeProperty(property));

exports.removeInlineStyles = removeInlineStyles;
const ADDRESS_DOMAIN = '@duck.com';
/**
 * Given a username, returns the full email address
 * @param {string} address
 * @returns {string}
 */

exports.ADDRESS_DOMAIN = ADDRESS_DOMAIN;

const formatDuckAddress = address => address + ADDRESS_DOMAIN;
/**
 * Escapes any occurrences of &, ", <, > or / with XML entities.
 * @param {string} str The string to escape.
 * @return {string} The escaped string.
 */


exports.formatDuckAddress = formatDuckAddress;

function escapeXML(str) {
  const replacements = {
    '&': '&amp;',
    '"': '&quot;',
    "'": '&apos;',
    '<': '&lt;',
    '>': '&gt;',
    '/': '&#x2F;'
  };
  return String(str).replace(/[&"'<>/]/g, m => replacements[m]);
}

const SUBMIT_BUTTON_REGEX = /submit|send|confirm|save|continue|sign|log.?([io])n|buy|purchase|check.?out|subscribe|donate/i;
const SUBMIT_BUTTON_UNLIKELY_REGEX = /facebook|twitter|google|apple|cancel|password|show|toggle|reveal|hide/i;
/**
 * Determines if an element is likely to be a submit button
 * @param {HTMLElement} el A button, input, anchor or other element with role=button
 * @return {boolean}
 */

const isLikelyASubmitButton = el => {
  const text = el.textContent || '';
  const ariaLabel = el.getAttribute('aria-label') || '';
  const title = el.title || '';
  const value = el instanceof HTMLInputElement ? el.value || '' : '';
  const contentExcludingLabel = text + ' ' + title + ' ' + value;
  return (el.getAttribute('type') === 'submit' || // is explicitly set as "submit"
  /primary|submit/i.test(el.className) || // has high-signal submit classes
  SUBMIT_BUTTON_REGEX.test(contentExcludingLabel) || // has high-signal text
  el.offsetHeight * el.offsetWidth >= 10000) && // it's a large element, at least 250x40px
  !SUBMIT_BUTTON_UNLIKELY_REGEX.test(contentExcludingLabel + ' ' + ariaLabel);
};
/**
 * Check that a button matches the form type - login buttons on a login form, signup buttons on a signup form
 * @param {HTMLElement} el
 * @param {import('./Form/Form').Form} formObj
 */


exports.isLikelyASubmitButton = isLikelyASubmitButton;

const buttonMatchesFormType = (el, formObj) => {
  if (formObj.isLogin) {
    return !/sign.?up/i.test(el.textContent || '');
  } else if (formObj.isSignup) {
    return !/(log|sign).?([io])n/i.test(el.textContent || '');
  } else {
    return true;
  }
};

exports.buttonMatchesFormType = buttonMatchesFormType;

},{"./Form/matching":26}],47:[function(require,module,exports){
"use strict";

require("./requestIdleCallback");

var _DeviceInterface = require("./DeviceInterface");

// Polyfills/shims
(() => {
  if (!window.isSecureContext) return false;

  try {
    const startupAutofill = () => {
      if (document.visibilityState === 'visible') {
        const deviceInterface = (0, _DeviceInterface.createDevice)();
        deviceInterface.init();
      } else {
        document.addEventListener('visibilitychange', startupAutofill, {
          once: true
        });
      }
    };

    startupAutofill();
  } catch (e) {
    console.error(e); // Noop, we errored
  }
})();

},{"./DeviceInterface":10,"./requestIdleCallback":57}],48:[function(require,module,exports){
"use strict";

const DDG_DOMAIN_REGEX = new RegExp(/^https:\/\/(([a-z0-9-_]+?)\.)?duckduckgo\.com\/email/);
/**
 * This is a centralised place to contain all string/variable replacements
 *
 * @returns {GlobalConfig}
 */

function createGlobalConfig() {
  let isApp = false;
  let isTopFrame = false;
  let supportsTopFrame = false; // Do not remove -- Apple devices change this when they support modern webkit messaging

  let hasModernWebkitAPI = false; // INJECT isApp HERE
  // INJECT isTopFrame HERE
  // INJECT supportsTopFrame HERE
  // INJECT hasModernWebkitAPI HERE

  let isDDGTestMode = false; // INJECT isDDGTestMode HERE

  let contentScope = null;
  let userUnprotectedDomains = null;
  let userPreferences = null; // INJECT contentScope HERE
  // INJECT userUnprotectedDomains HERE
  // INJECT userPreferences HERE

  /** @type {Record<string, any> | null} */

  let availableInputTypes = null; // INJECT availableInputTypes HERE
  // The native layer will inject a randomised secret here and use it to verify the origin

  let secret = 'PLACEHOLDER_SECRET';
  /**
   * The user agent check will not be needed here once `android` supports `userPreferences?.platform.name`
   */
  // @ts-ignore

  const isAndroid = (userPreferences === null || userPreferences === void 0 ? void 0 : userPreferences.platform.name) === 'android' || /Android.*DuckDuckGo\/\d/i.test(window.navigator.userAgent); // @ts-ignore

  const isDDGApp = ['ios', 'android', 'macos', 'windows'].includes(userPreferences === null || userPreferences === void 0 ? void 0 : userPreferences.platform.name) || isAndroid; // @ts-ignore

  const isMobileApp = ['ios', 'android'].includes(userPreferences === null || userPreferences === void 0 ? void 0 : userPreferences.platform.name) || isAndroid;
  const isFirefox = navigator.userAgent.includes('Firefox');
  const isDDGDomain = Boolean(window.location.href.match(DDG_DOMAIN_REGEX));
  return {
    isApp,
    isDDGApp,
    isAndroid,
    isFirefox,
    isMobileApp,
    isTopFrame,
    secret,
    supportsTopFrame,
    hasModernWebkitAPI,
    contentScope,
    userUnprotectedDomains,
    userPreferences,
    isDDGTestMode,
    isDDGDomain,
    availableInputTypes
  };
}

module.exports.createGlobalConfig = createGlobalConfig;
module.exports.DDG_DOMAIN_REGEX = DDG_DOMAIN_REGEX;

},{}],49:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.constants = void 0;
const constants = {
  ATTR_INPUT_TYPE: 'data-ddg-inputType',
  ATTR_AUTOFILL: 'data-ddg-autofill',
  TEXT_LENGTH_CUTOFF: 50
};
exports.constants = constants;

},{}],50:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.StoreFormDataCall = exports.GetRuntimeConfigurationCall = exports.GetAvailableInputTypesCall = exports.GetAutofillDataCall = void 0;

var _validatorsZod = require("./validators.zod.js");

var _deviceApi = require("../../../packages/device-api");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * @extends {DeviceApiCall<getAutofillDataRequestSchema, getAutofillDataResponseSchema>} 
 */
class GetAutofillDataCall extends _deviceApi.DeviceApiCall {
  constructor() {
    super(...arguments);

    _defineProperty(this, "method", "getAutofillData");

    _defineProperty(this, "id", "getAutofillDataResponse");

    _defineProperty(this, "paramsValidator", _validatorsZod.getAutofillDataRequestSchema);

    _defineProperty(this, "resultValidator", _validatorsZod.getAutofillDataResponseSchema);
  }

}
/**
 * @extends {DeviceApiCall<any, getRuntimeConfigurationResponseSchema>} 
 */


exports.GetAutofillDataCall = GetAutofillDataCall;

class GetRuntimeConfigurationCall extends _deviceApi.DeviceApiCall {
  constructor() {
    super(...arguments);

    _defineProperty(this, "method", "getRuntimeConfiguration");

    _defineProperty(this, "id", "getRuntimeConfigurationResponse");

    _defineProperty(this, "resultValidator", _validatorsZod.getRuntimeConfigurationResponseSchema);
  }

}
/**
 * @extends {DeviceApiCall<storeFormDataSchema, any>} 
 */


exports.GetRuntimeConfigurationCall = GetRuntimeConfigurationCall;

class StoreFormDataCall extends _deviceApi.DeviceApiCall {
  constructor() {
    super(...arguments);

    _defineProperty(this, "method", "storeFormData");

    _defineProperty(this, "paramsValidator", _validatorsZod.storeFormDataSchema);
  }

}
/**
 * @extends {DeviceApiCall<any, getAvailableInputTypesResultSchema>} 
 */


exports.StoreFormDataCall = StoreFormDataCall;

class GetAvailableInputTypesCall extends _deviceApi.DeviceApiCall {
  constructor() {
    super(...arguments);

    _defineProperty(this, "method", "getAvailableInputTypes");

    _defineProperty(this, "id", "getAvailableInputTypesResponse");

    _defineProperty(this, "resultValidator", _validatorsZod.getAvailableInputTypesResultSchema);
  }

}

exports.GetAvailableInputTypesCall = GetAvailableInputTypesCall;

},{"../../../packages/device-api":2,"./validators.zod.js":51}],51:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.userPreferencesSchema = exports.storeFormDataSchema = exports.runtimeConfigurationSchema = exports.outgoingCredentialsSchema = exports.getRuntimeConfigurationResponseSchema = exports.getAvailableInputTypesResultSchema = exports.getAutofillDataResponseSchema = exports.getAutofillDataRequestSchema = exports.getAliasResultSchema = exports.getAliasParamsSchema = exports.genericErrorSchema = exports.credentialsSchema = exports.contentScopeSchema = exports.contentScopeFeaturesSchema = exports.contentScopeFeaturesItemSettingsSchema = exports.availableInputTypesSchema = exports.autofillSettingsSchema = exports.autofillFeatureTogglesSchema = void 0;
const autofillFeatureTogglesSchema = null;
exports.autofillFeatureTogglesSchema = autofillFeatureTogglesSchema;
const credentialsSchema = null;
exports.credentialsSchema = credentialsSchema;
const genericErrorSchema = null;
exports.genericErrorSchema = genericErrorSchema;
const getAliasParamsSchema = null;
exports.getAliasParamsSchema = getAliasParamsSchema;
const getAliasResultSchema = null;
exports.getAliasResultSchema = getAliasResultSchema;
const getAutofillDataRequestSchema = null;
exports.getAutofillDataRequestSchema = getAutofillDataRequestSchema;
const getAutofillDataResponseSchema = null;
exports.getAutofillDataResponseSchema = getAutofillDataResponseSchema;
const availableInputTypesSchema = null;
exports.availableInputTypesSchema = availableInputTypesSchema;
const contentScopeFeaturesItemSettingsSchema = null;
exports.contentScopeFeaturesItemSettingsSchema = contentScopeFeaturesItemSettingsSchema;
const userPreferencesSchema = null;
exports.userPreferencesSchema = userPreferencesSchema;
const contentScopeFeaturesSchema = null;
exports.contentScopeFeaturesSchema = contentScopeFeaturesSchema;
const outgoingCredentialsSchema = null;
exports.outgoingCredentialsSchema = outgoingCredentialsSchema;
const autofillSettingsSchema = null;
exports.autofillSettingsSchema = autofillSettingsSchema;
const getAvailableInputTypesResultSchema = null;
exports.getAvailableInputTypesResultSchema = getAvailableInputTypesResultSchema;
const contentScopeSchema = null;
exports.contentScopeSchema = contentScopeSchema;
const runtimeConfigurationSchema = null;
exports.runtimeConfigurationSchema = runtimeConfigurationSchema;
const storeFormDataSchema = null;
exports.storeFormDataSchema = storeFormDataSchema;
const getRuntimeConfigurationResponseSchema = null;
exports.getRuntimeConfigurationResponseSchema = getRuntimeConfigurationResponseSchema;

},{}],52:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.GetAlias = void 0;

var _deviceApi = require("../../packages/device-api");

var _validators = require("./__generated__/validators.zod");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * @extends {DeviceApiCall<getAliasParamsSchema, getAliasResultSchema>}
 */
class GetAlias extends _deviceApi.DeviceApiCall {
  constructor() {
    super(...arguments);

    _defineProperty(this, "method", 'emailHandlerGetAlias');

    _defineProperty(this, "id", 'n/a');

    _defineProperty(this, "paramsValidator", _validators.getAliasParamsSchema);

    _defineProperty(this, "resultValidator", _validators.getAliasResultSchema);
  }

  preResultValidation(response) {
    // convert to the correct format, because this is a legacy API
    return {
      success: response
    };
  }

}

exports.GetAlias = GetAlias;

},{"../../packages/device-api":2,"./__generated__/validators.zod":51}],53:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.AndroidTransport = void 0;

var _deviceApi = require("../../../packages/device-api");

var _deviceApiCalls = require("../__generated__/deviceApiCalls");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

class AndroidTransport extends _deviceApi.DeviceApiTransport {
  /** @type {GlobalConfig} */

  /** @param {GlobalConfig} globalConfig */
  constructor(globalConfig) {
    super();

    _defineProperty(this, "config", void 0);

    this.config = globalConfig;

    if (this.config.isDDGTestMode) {
      var _window$BrowserAutofi, _window$BrowserAutofi2;

      if (typeof ((_window$BrowserAutofi = window.BrowserAutofill) === null || _window$BrowserAutofi === void 0 ? void 0 : _window$BrowserAutofi.getAutofillData) !== 'function') {
        console.warn('window.BrowserAutofill.getAutofillData missing');
      }

      if (typeof ((_window$BrowserAutofi2 = window.BrowserAutofill) === null || _window$BrowserAutofi2 === void 0 ? void 0 : _window$BrowserAutofi2.storeFormData) !== 'function') {
        console.warn('window.BrowserAutofill.storeFormData missing');
      }
    }
  }
  /**
   * @param {import("../../../packages/device-api").DeviceApiCall} deviceApiCall
   * @returns {Promise<any>}
   */


  async send(deviceApiCall) {
    if (deviceApiCall instanceof _deviceApiCalls.GetRuntimeConfigurationCall) {
      return androidSpecificRuntimeConfiguration(this.config);
    }

    if (deviceApiCall instanceof _deviceApiCalls.GetAvailableInputTypesCall) {
      return androidSpecificAvailableInputTypes(this.config);
    }

    if (deviceApiCall instanceof _deviceApiCalls.GetAutofillDataCall) {
      window.BrowserAutofill.getAutofillData(JSON.stringify(deviceApiCall.params));
      return waitForResponse(deviceApiCall.id, this.config);
    }

    if (deviceApiCall instanceof _deviceApiCalls.StoreFormDataCall) {
      return window.BrowserAutofill.storeFormData(JSON.stringify(deviceApiCall.params));
    }

    throw new Error('android: not implemented: ' + deviceApiCall.method);
  }

}
/**
 * @param {string} expectedResponse - the name/id of the response
 * @param {GlobalConfig} config
 * @returns {Promise<*>}
 */


exports.AndroidTransport = AndroidTransport;

function waitForResponse(expectedResponse, config) {
  return new Promise(resolve => {
    const handler = e => {
      if (!config.isDDGTestMode) {
        if (e.origin !== '') {
          return;
        }
      }

      if (!e.data) {
        return;
      }

      if (typeof e.data !== 'string') {
        if (config.isDDGTestMode) {
          console.log('❌ event.data was not a string. Expected a string so that it can be JSON parsed');
        }

        return;
      }

      try {
        let data = JSON.parse(e.data);

        if (data.type === expectedResponse) {
          window.removeEventListener('message', handler);
          return resolve(data);
        }

        if (config.isDDGTestMode) {
          console.log("\u274C event.data.type was '".concat(data.type, "', which didnt match '").concat(expectedResponse, "'"), JSON.stringify(data));
        }
      } catch (e) {
        window.removeEventListener('message', handler);

        if (config.isDDGTestMode) {
          console.log('❌ Could not JSON.parse the response');
        }
      }
    };

    window.addEventListener('message', handler);
  });
}
/**
 * @param {GlobalConfig} globalConfig
 * @returns {{success: import('../__generated__/validators-ts').RuntimeConfiguration}}
 */


function androidSpecificRuntimeConfiguration(globalConfig) {
  if (!globalConfig.userPreferences) {
    throw new Error('globalConfig.userPreferences not supported yet on Android');
  }

  return {
    success: {
      // @ts-ignore
      contentScope: globalConfig.contentScope,
      // @ts-ignore
      userPreferences: globalConfig.userPreferences,
      // @ts-ignore
      userUnprotectedDomains: globalConfig.userUnprotectedDomains
    }
  };
}
/**
 * @param {GlobalConfig} globalConfig
 * @returns {{success: import('../__generated__/validators-ts').AvailableInputTypes}}
 */


function androidSpecificAvailableInputTypes(globalConfig) {
  if (!globalConfig.availableInputTypes) {
    throw new Error('globalConfig.availableInputTypes not supported yet on Android');
  }

  return {
    success: globalConfig.availableInputTypes
  };
}

},{"../../../packages/device-api":2,"../__generated__/deviceApiCalls":50}],54:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.AppleTransport = void 0;

var _appleDeviceUtils = require("../../appleDeviceUtils/appleDeviceUtils");

var _deviceApi = require("../../../packages/device-api");

var _deviceApiCalls = require("../__generated__/deviceApiCalls");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

class AppleTransport extends _deviceApi.DeviceApiTransport {
  /** @type {{hasModernWebkitAPI?: boolean, secret?: string}} */

  /** @param {GlobalConfig} globalConfig */
  constructor(globalConfig) {
    super();

    _defineProperty(this, "sendOptions", void 0);

    this.config = globalConfig;
    this.sendOptions = {
      secret: this.config.secret,
      hasModernWebkitAPI: this.config.hasModernWebkitAPI
    };
  }

  async send(deviceApiCall) {
    try {
      // if the call has an `id`, it means that it expects a response
      if (deviceApiCall.id) {
        return await (0, _appleDeviceUtils.wkSendAndWait)(deviceApiCall.method, deviceApiCall.params || undefined, this.sendOptions);
      } else {
        return await (0, _appleDeviceUtils.wkSend)(deviceApiCall.method, deviceApiCall.params || undefined, this.sendOptions);
      }
    } catch (e) {
      if (e instanceof _appleDeviceUtils.MissingWebkitHandler) {
        if (this.config.isDDGTestMode) {
          console.log('MissingWebkitHandler error for:', deviceApiCall.method);
        }

        if (deviceApiCall instanceof _deviceApiCalls.GetRuntimeConfigurationCall) {
          return deviceApiCall.result(appleSpecificRuntimeConfiguration(this.config));
        }

        throw new Error('unimplemented handler: ' + deviceApiCall.method);
      } else {
        throw e;
      }
    }
  }

}
/**
 * @param {GlobalConfig} globalConfig
 * @returns {ReturnType<GetRuntimeConfigurationCall['result']>}
 */


exports.AppleTransport = AppleTransport;

function appleSpecificRuntimeConfiguration(globalConfig) {
  return {
    success: {
      // @ts-ignore
      contentScope: globalConfig.contentScope,
      // @ts-ignore
      userPreferences: globalConfig.userPreferences,
      // @ts-ignore
      userUnprotectedDomains: globalConfig.userUnprotectedDomains
    }
  };
}

},{"../../../packages/device-api":2,"../../appleDeviceUtils/appleDeviceUtils":44,"../__generated__/deviceApiCalls":50}],55:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.ExtensionTransport = void 0;

var _deviceApi = require("../../../packages/device-api");

class ExtensionTransport extends _deviceApi.DeviceApiTransport {
  async send(deviceApiCall) {
    throw new Error('not implemented yet for ' + deviceApiCall.method);
  }

}

exports.ExtensionTransport = ExtensionTransport;

},{"../../../packages/device-api":2}],56:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.createTransport = createTransport;

var _apple = require("./apple.transport");

var _android = require("./android.transport");

var _extension = require("./extension.transport");

/**
 * @param {GlobalConfig} globalConfig
 * @returns {import("../../../packages/device-api").DeviceApiTransport}
 */
function createTransport(globalConfig) {
  var _globalConfig$userPre, _globalConfig$userPre2, _globalConfig$userPre3, _globalConfig$userPre4;

  if (typeof ((_globalConfig$userPre = globalConfig.userPreferences) === null || _globalConfig$userPre === void 0 ? void 0 : (_globalConfig$userPre2 = _globalConfig$userPre.platform) === null || _globalConfig$userPre2 === void 0 ? void 0 : _globalConfig$userPre2.name) === 'string') {
    switch ((_globalConfig$userPre3 = globalConfig.userPreferences) === null || _globalConfig$userPre3 === void 0 ? void 0 : (_globalConfig$userPre4 = _globalConfig$userPre3.platform) === null || _globalConfig$userPre4 === void 0 ? void 0 : _globalConfig$userPre4.name) {
      case 'ios':
      case 'macos':
        return new _apple.AppleTransport(globalConfig);

      case 'android':
        return new _android.AndroidTransport(globalConfig);

      default:
        throw new Error('selectSender unimplemented!');
    }
  } // fallback for when `globalConfig.userPreferences.platform.name` is absent


  if (globalConfig.isDDGApp) {
    if (globalConfig.isAndroid) {
      return new _android.AndroidTransport(globalConfig);
    }

    throw new Error('unreachable, createTransport');
  }

  return new _extension.ExtensionTransport();
}

},{"./android.transport":53,"./apple.transport":54,"./extension.transport":55}],57:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;

/*!
 * Copyright 2015 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

/*
 * @see https://developers.google.com/web/updates/2015/08/using-requestidlecallback
 */
// @ts-ignore
window.requestIdleCallback = window.requestIdleCallback || function (cb) {
  return setTimeout(function () {
    const start = Date.now(); // eslint-disable-next-line standard/no-callback-literal

    cb({
      didTimeout: false,
      timeRemaining: function () {
        return Math.max(0, 50 - (Date.now() - start));
      }
    });
  }, 1);
};

window.cancelIdleCallback = window.cancelIdleCallback || function (id) {
  clearTimeout(id);
};

var _default = {};
exports.default = _default;

},{}]},{},[47]);
