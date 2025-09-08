"use strict";
(() => {
  var __create = Object.create;
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __getProtoOf = Object.getPrototypeOf;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
  var __commonJS = (cb, mod) => function __require() {
    return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
    // If the importer is in node compatibility mode or this is not an ESM
    // file that has been converted to a CommonJS file using a Babel-
    // compatible transform (i.e. "__esModule" has not been set), then set
    // "default" to the CommonJS "module.exports" for node compatibility.
    isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
    mod
  ));
  var __publicField = (obj, key, value) => __defNormalProp(obj, typeof key !== "symbol" ? key + "" : key, value);

  // ../node_modules/classnames/index.js
  var require_classnames = __commonJS({
    "../node_modules/classnames/index.js"(exports, module) {
      (function() {
        "use strict";
        var hasOwn = {}.hasOwnProperty;
        function classNames() {
          var classes = "";
          for (var i3 = 0; i3 < arguments.length; i3++) {
            var arg = arguments[i3];
            if (arg) {
              classes = appendClass(classes, parseValue(arg));
            }
          }
          return classes;
        }
        function parseValue(arg) {
          if (typeof arg === "string" || typeof arg === "number") {
            return arg;
          }
          if (typeof arg !== "object") {
            return "";
          }
          if (Array.isArray(arg)) {
            return classNames.apply(null, arg);
          }
          if (arg.toString !== Object.prototype.toString && !arg.toString.toString().includes("[native code]")) {
            return arg.toString();
          }
          var classes = "";
          for (var key in arg) {
            if (hasOwn.call(arg, key) && arg[key]) {
              classes = appendClass(classes, key);
            }
          }
          return classes;
        }
        function appendClass(value, newClass) {
          if (!newClass) {
            return value;
          }
          if (value) {
            return value + " " + newClass;
          }
          return value + newClass;
        }
        if (typeof module !== "undefined" && module.exports) {
          classNames.default = classNames;
          module.exports = classNames;
        } else if (typeof define === "function" && typeof define.amd === "object" && define.amd) {
          define("classnames", [], function() {
            return classNames;
          });
        } else {
          window.classNames = classNames;
        }
      })();
    }
  });

  // ../messaging/lib/windows.js
  var WindowsMessagingTransport = class {
    /**
     * @param {WindowsMessagingConfig} config
     * @param {import('../index.js').MessagingContext} messagingContext
     * @internal
     */
    constructor(config, messagingContext) {
      this.messagingContext = messagingContext;
      this.config = config;
      this.globals = {
        window,
        JSONparse: window.JSON.parse,
        JSONstringify: window.JSON.stringify,
        Promise: window.Promise,
        Error: window.Error,
        String: window.String
      };
      for (const [methodName, fn] of Object.entries(this.config.methods)) {
        if (typeof fn !== "function") {
          throw new Error("cannot create WindowsMessagingTransport, missing the method: " + methodName);
        }
      }
    }
    /**
     * @param {import('../index.js').NotificationMessage} msg
     */
    notify(msg) {
      const data = this.globals.JSONparse(this.globals.JSONstringify(msg.params || {}));
      const notification = WindowsNotification.fromNotification(msg, data);
      this.config.methods.postMessage(notification);
    }
    /**
     * @param {import('../index.js').RequestMessage} msg
     * @param {{signal?: AbortSignal}} opts
     * @return {Promise<any>}
     */
    request(msg, opts = {}) {
      const data = this.globals.JSONparse(this.globals.JSONstringify(msg.params || {}));
      const outgoing = WindowsRequestMessage.fromRequest(msg, data);
      this.config.methods.postMessage(outgoing);
      const comparator = (eventData) => {
        return eventData.featureName === msg.featureName && eventData.context === msg.context && eventData.id === msg.id;
      };
      function isMessageResponse(data2) {
        if ("result" in data2) return true;
        if ("error" in data2) return true;
        return false;
      }
      return new this.globals.Promise((resolve, reject) => {
        try {
          this._subscribe(comparator, opts, (value, unsubscribe) => {
            unsubscribe();
            if (!isMessageResponse(value)) {
              console.warn("unknown response type", value);
              return reject(new this.globals.Error("unknown response"));
            }
            if (value.result) {
              return resolve(value.result);
            }
            const message = this.globals.String(value.error?.message || "unknown error");
            reject(new this.globals.Error(message));
          });
        } catch (e3) {
          reject(e3);
        }
      });
    }
    /**
     * @param {import('../index.js').Subscription} msg
     * @param {(value: unknown | undefined) => void} callback
     */
    subscribe(msg, callback) {
      const comparator = (eventData) => {
        return eventData.featureName === msg.featureName && eventData.context === msg.context && eventData.subscriptionName === msg.subscriptionName;
      };
      const cb = (eventData) => {
        return callback(eventData.params);
      };
      return this._subscribe(comparator, {}, cb);
    }
    /**
     * @typedef {import('../index.js').MessageResponse | import('../index.js').SubscriptionEvent} Incoming
     */
    /**
     * @param {(eventData: any) => boolean} comparator
     * @param {{signal?: AbortSignal}} options
     * @param {(value: Incoming, unsubscribe: (()=>void)) => void} callback
     * @internal
     */
    _subscribe(comparator, options, callback) {
      if (options?.signal?.aborted) {
        throw new DOMException("Aborted", "AbortError");
      }
      let teardown;
      const idHandler = (event) => {
        if (this.messagingContext.env === "production") {
          if (event.origin !== null && event.origin !== void 0) {
            console.warn("ignoring because evt.origin is not `null` or `undefined`");
            return;
          }
        }
        if (!event.data) {
          console.warn("data absent from message");
          return;
        }
        if (comparator(event.data)) {
          if (!teardown) throw new Error("unreachable");
          callback(event.data, teardown);
        }
      };
      const abortHandler = () => {
        teardown?.();
        throw new DOMException("Aborted", "AbortError");
      };
      this.config.methods.addEventListener("message", idHandler);
      options?.signal?.addEventListener("abort", abortHandler);
      teardown = () => {
        this.config.methods.removeEventListener("message", idHandler);
        options?.signal?.removeEventListener("abort", abortHandler);
      };
      return () => {
        teardown?.();
      };
    }
  };
  var WindowsMessagingConfig = class {
    /**
     * @param {object} params
     * @param {WindowsInteropMethods} params.methods
     * @internal
     */
    constructor(params) {
      this.methods = params.methods;
      this.platform = "windows";
    }
  };
  var WindowsNotification = class {
    /**
     * @param {object} params
     * @param {string} params.Feature
     * @param {string} params.SubFeatureName
     * @param {string} params.Name
     * @param {Record<string, any>} [params.Data]
     * @internal
     */
    constructor(params) {
      this.Feature = params.Feature;
      this.SubFeatureName = params.SubFeatureName;
      this.Name = params.Name;
      this.Data = params.Data;
    }
    /**
     * Helper to convert a {@link NotificationMessage} to a format that Windows can support
     * @param {NotificationMessage} notification
     * @returns {WindowsNotification}
     */
    static fromNotification(notification, data) {
      const output = {
        Data: data,
        Feature: notification.context,
        SubFeatureName: notification.featureName,
        Name: notification.method
      };
      return output;
    }
  };
  var WindowsRequestMessage = class {
    /**
     * @param {object} params
     * @param {string} params.Feature
     * @param {string} params.SubFeatureName
     * @param {string} params.Name
     * @param {Record<string, any>} [params.Data]
     * @param {string} [params.Id]
     * @internal
     */
    constructor(params) {
      this.Feature = params.Feature;
      this.SubFeatureName = params.SubFeatureName;
      this.Name = params.Name;
      this.Data = params.Data;
      this.Id = params.Id;
    }
    /**
     * Helper to convert a {@link RequestMessage} to a format that Windows can support
     * @param {RequestMessage} msg
     * @param {Record<string, any>} data
     * @returns {WindowsRequestMessage}
     */
    static fromRequest(msg, data) {
      const output = {
        Data: data,
        Feature: msg.context,
        SubFeatureName: msg.featureName,
        Name: msg.method,
        Id: msg.id
      };
      return output;
    }
  };

  // ../messaging/schema.js
  var RequestMessage = class {
    /**
     * @param {object} params
     * @param {string} params.context
     * @param {string} params.featureName
     * @param {string} params.method
     * @param {string} params.id
     * @param {Record<string, any>} [params.params]
     * @internal
     */
    constructor(params) {
      this.context = params.context;
      this.featureName = params.featureName;
      this.method = params.method;
      this.id = params.id;
      this.params = params.params;
    }
  };
  var NotificationMessage = class {
    /**
     * @param {object} params
     * @param {string} params.context
     * @param {string} params.featureName
     * @param {string} params.method
     * @param {Record<string, any>} [params.params]
     * @internal
     */
    constructor(params) {
      this.context = params.context;
      this.featureName = params.featureName;
      this.method = params.method;
      this.params = params.params;
    }
  };
  var Subscription = class {
    /**
     * @param {object} params
     * @param {string} params.context
     * @param {string} params.featureName
     * @param {string} params.subscriptionName
     * @internal
     */
    constructor(params) {
      this.context = params.context;
      this.featureName = params.featureName;
      this.subscriptionName = params.subscriptionName;
    }
  };
  function isResponseFor(request, data) {
    if ("result" in data) {
      return data.featureName === request.featureName && data.context === request.context && data.id === request.id;
    }
    if ("error" in data) {
      if ("message" in data.error) {
        return true;
      }
    }
    return false;
  }
  function isSubscriptionEventFor(sub, data) {
    if ("subscriptionName" in data) {
      return data.featureName === sub.featureName && data.context === sub.context && data.subscriptionName === sub.subscriptionName;
    }
    return false;
  }

  // ../messaging/lib/webkit.js
  var WebkitMessagingTransport = class {
    /**
     * @param {WebkitMessagingConfig} config
     * @param {import('../index.js').MessagingContext} messagingContext
     */
    constructor(config, messagingContext) {
      this.messagingContext = messagingContext;
      this.config = config;
      this.globals = captureGlobals();
      if (!this.config.hasModernWebkitAPI) {
        this.captureWebkitHandlers(this.config.webkitMessageHandlerNames);
      }
    }
    /**
     * Sends message to the webkit layer (fire and forget)
     * @param {String} handler
     * @param {*} data
     * @internal
     */
    wkSend(handler, data = {}) {
      if (!(handler in this.globals.window.webkit.messageHandlers)) {
        throw new MissingHandler(`Missing webkit handler: '${handler}'`, handler);
      }
      if (!this.config.hasModernWebkitAPI) {
        const outgoing = {
          ...data,
          messageHandling: {
            ...data.messageHandling,
            secret: this.config.secret
          }
        };
        if (!(handler in this.globals.capturedWebkitHandlers)) {
          throw new MissingHandler(`cannot continue, method ${handler} not captured on macos < 11`, handler);
        } else {
          return this.globals.capturedWebkitHandlers[handler](outgoing);
        }
      }
      return this.globals.window.webkit.messageHandlers[handler].postMessage?.(data);
    }
    /**
     * Sends message to the webkit layer and waits for the specified response
     * @param {String} handler
     * @param {import('../index.js').RequestMessage} data
     * @returns {Promise<*>}
     * @internal
     */
    async wkSendAndWait(handler, data) {
      if (this.config.hasModernWebkitAPI) {
        const response = await this.wkSend(handler, data);
        return this.globals.JSONparse(response || "{}");
      }
      try {
        const randMethodName = this.createRandMethodName();
        const key = await this.createRandKey();
        const iv = this.createRandIv();
        const { ciphertext, tag } = await new this.globals.Promise((resolve) => {
          this.generateRandomMethod(randMethodName, resolve);
          data.messageHandling = new SecureMessagingParams({
            methodName: randMethodName,
            secret: this.config.secret,
            key: this.globals.Arrayfrom(key),
            iv: this.globals.Arrayfrom(iv)
          });
          this.wkSend(handler, data);
        });
        const cipher = new this.globals.Uint8Array([...ciphertext, ...tag]);
        const decrypted = await this.decrypt(cipher, key, iv);
        return this.globals.JSONparse(decrypted || "{}");
      } catch (e3) {
        if (e3 instanceof MissingHandler) {
          throw e3;
        } else {
          console.error("decryption failed", e3);
          console.error(e3);
          return { error: e3 };
        }
      }
    }
    /**
     * @param {import('../index.js').NotificationMessage} msg
     */
    notify(msg) {
      this.wkSend(msg.context, msg);
    }
    /**
     * @param {import('../index.js').RequestMessage} msg
     */
    async request(msg) {
      const data = await this.wkSendAndWait(msg.context, msg);
      if (isResponseFor(msg, data)) {
        if (data.result) {
          return data.result || {};
        }
        if (data.error) {
          throw new Error(data.error.message);
        }
      }
      throw new Error("an unknown error occurred");
    }
    /**
     * Generate a random method name and adds it to the global scope
     * The native layer will use this method to send the response
     * @param {string | number} randomMethodName
     * @param {Function} callback
     * @internal
     */
    generateRandomMethod(randomMethodName, callback) {
      this.globals.ObjectDefineProperty(this.globals.window, randomMethodName, {
        enumerable: false,
        // configurable, To allow for deletion later
        configurable: true,
        writable: false,
        /**
         * @param {any[]} args
         */
        value: (...args) => {
          callback(...args);
          delete this.globals.window[randomMethodName];
        }
      });
    }
    /**
     * @internal
     * @return {string}
     */
    randomString() {
      return "" + this.globals.getRandomValues(new this.globals.Uint32Array(1))[0];
    }
    /**
     * @internal
     * @return {string}
     */
    createRandMethodName() {
      return "_" + this.randomString();
    }
    /**
     * @type {{name: string, length: number}}
     * @internal
     */
    algoObj = {
      name: "AES-GCM",
      length: 256
    };
    /**
     * @returns {Promise<Uint8Array>}
     * @internal
     */
    async createRandKey() {
      const key = await this.globals.generateKey(this.algoObj, true, ["encrypt", "decrypt"]);
      const exportedKey = await this.globals.exportKey("raw", key);
      return new this.globals.Uint8Array(exportedKey);
    }
    /**
     * @returns {Uint8Array}
     * @internal
     */
    createRandIv() {
      return this.globals.getRandomValues(new this.globals.Uint8Array(12));
    }
    /**
     * @param {BufferSource} ciphertext
     * @param {BufferSource} key
     * @param {Uint8Array} iv
     * @returns {Promise<string>}
     * @internal
     */
    async decrypt(ciphertext, key, iv) {
      const cryptoKey = await this.globals.importKey("raw", key, "AES-GCM", false, ["decrypt"]);
      const algo = {
        name: "AES-GCM",
        iv
      };
      const decrypted = await this.globals.decrypt(algo, cryptoKey, ciphertext);
      const dec = new this.globals.TextDecoder();
      return dec.decode(decrypted);
    }
    /**
     * When required (such as on macos 10.x), capture the `postMessage` method on
     * each webkit messageHandler
     *
     * @param {string[]} handlerNames
     */
    captureWebkitHandlers(handlerNames) {
      const handlers = window.webkit.messageHandlers;
      if (!handlers) throw new MissingHandler("window.webkit.messageHandlers was absent", "all");
      for (const webkitMessageHandlerName of handlerNames) {
        if (typeof handlers[webkitMessageHandlerName]?.postMessage === "function") {
          const original = handlers[webkitMessageHandlerName];
          const bound = handlers[webkitMessageHandlerName].postMessage?.bind(original);
          this.globals.capturedWebkitHandlers[webkitMessageHandlerName] = bound;
          delete handlers[webkitMessageHandlerName].postMessage;
        }
      }
    }
    /**
     * @param {import('../index.js').Subscription} msg
     * @param {(value: unknown) => void} callback
     */
    subscribe(msg, callback) {
      if (msg.subscriptionName in this.globals.window) {
        throw new this.globals.Error(`A subscription with the name ${msg.subscriptionName} already exists`);
      }
      this.globals.ObjectDefineProperty(this.globals.window, msg.subscriptionName, {
        enumerable: false,
        configurable: true,
        writable: false,
        value: (data) => {
          if (data && isSubscriptionEventFor(msg, data)) {
            callback(data.params);
          } else {
            console.warn("Received a message that did not match the subscription", data);
          }
        }
      });
      return () => {
        this.globals.ReflectDeleteProperty(this.globals.window, msg.subscriptionName);
      };
    }
  };
  var WebkitMessagingConfig = class {
    /**
     * @param {object} params
     * @param {boolean} params.hasModernWebkitAPI
     * @param {string[]} params.webkitMessageHandlerNames
     * @param {string} params.secret
     * @internal
     */
    constructor(params) {
      this.hasModernWebkitAPI = params.hasModernWebkitAPI;
      this.webkitMessageHandlerNames = params.webkitMessageHandlerNames;
      this.secret = params.secret;
    }
  };
  var SecureMessagingParams = class {
    /**
     * @param {object} params
     * @param {string} params.methodName
     * @param {string} params.secret
     * @param {number[]} params.key
     * @param {number[]} params.iv
     */
    constructor(params) {
      this.methodName = params.methodName;
      this.secret = params.secret;
      this.key = params.key;
      this.iv = params.iv;
    }
  };
  function captureGlobals() {
    const globals = {
      window,
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
      Error: window.Error,
      ReflectDeleteProperty: window.Reflect.deleteProperty.bind(window.Reflect),
      ObjectDefineProperty: window.Object.defineProperty,
      addEventListener: window.addEventListener.bind(window),
      /** @type {Record<string, any>} */
      capturedWebkitHandlers: {}
    };
    if (isSecureContext) {
      globals.generateKey = window.crypto.subtle.generateKey.bind(window.crypto.subtle);
      globals.exportKey = window.crypto.subtle.exportKey.bind(window.crypto.subtle);
      globals.importKey = window.crypto.subtle.importKey.bind(window.crypto.subtle);
      globals.encrypt = window.crypto.subtle.encrypt.bind(window.crypto.subtle);
      globals.decrypt = window.crypto.subtle.decrypt.bind(window.crypto.subtle);
    }
    return globals;
  }

  // ../messaging/lib/android.js
  var AndroidMessagingTransport = class {
    /**
     * @param {AndroidMessagingConfig} config
     * @param {MessagingContext} messagingContext
     * @internal
     */
    constructor(config, messagingContext) {
      this.messagingContext = messagingContext;
      this.config = config;
    }
    /**
     * @param {NotificationMessage} msg
     */
    notify(msg) {
      try {
        this.config.sendMessageThrows?.(JSON.stringify(msg));
      } catch (e3) {
        console.error(".notify failed", e3);
      }
    }
    /**
     * @param {RequestMessage} msg
     * @return {Promise<any>}
     */
    request(msg) {
      return new Promise((resolve, reject) => {
        const unsub = this.config.subscribe(msg.id, handler);
        try {
          this.config.sendMessageThrows?.(JSON.stringify(msg));
        } catch (e3) {
          unsub();
          reject(new Error("request failed to send: " + e3.message || "unknown error"));
        }
        function handler(data) {
          if (isResponseFor(msg, data)) {
            if (data.result) {
              resolve(data.result || {});
              return unsub();
            }
            if (data.error) {
              reject(new Error(data.error.message));
              return unsub();
            }
            unsub();
            throw new Error("unreachable: must have `result` or `error` key by this point");
          }
        }
      });
    }
    /**
     * @param {Subscription} msg
     * @param {(value: unknown | undefined) => void} callback
     */
    subscribe(msg, callback) {
      const unsub = this.config.subscribe(msg.subscriptionName, (data) => {
        if (isSubscriptionEventFor(msg, data)) {
          callback(data.params || {});
        }
      });
      return () => {
        unsub();
      };
    }
  };
  var AndroidMessagingConfig = class {
    /** @type {(json: string, secret: string) => void} */
    _capturedHandler;
    /**
     * @param {object} params
     * @param {Record<string, any>} params.target
     * @param {boolean} params.debug
     * @param {string} params.messageSecret - a secret to ensure that messages are only
     * processed by the correct handler
     * @param {string} params.javascriptInterface - the name of the javascript interface
     * registered on the native side
     * @param {string} params.messageCallback - the name of the callback that the native
     * side will use to send messages back to the javascript side
     */
    constructor(params) {
      this.target = params.target;
      this.debug = params.debug;
      this.javascriptInterface = params.javascriptInterface;
      this.messageSecret = params.messageSecret;
      this.messageCallback = params.messageCallback;
      this.listeners = new globalThis.Map();
      this._captureGlobalHandler();
      this._assignHandlerMethod();
    }
    /**
     * The transport can call this to transmit a JSON payload along with a secret
     * to the native Android handler.
     *
     * Note: This can throw - it's up to the transport to handle the error.
     *
     * @type {(json: string) => void}
     * @throws
     * @internal
     */
    sendMessageThrows(json) {
      this._capturedHandler(json, this.messageSecret);
    }
    /**
     * A subscription on Android is just a named listener. All messages from
     * android -> are delivered through a single function, and this mapping is used
     * to route the messages to the correct listener.
     *
     * Note: Use this to implement request->response by unsubscribing after the first
     * response.
     *
     * @param {string} id
     * @param {(msg: MessageResponse | SubscriptionEvent) => void} callback
     * @returns {() => void}
     * @internal
     */
    subscribe(id, callback) {
      this.listeners.set(id, callback);
      return () => {
        this.listeners.delete(id);
      };
    }
    /**
     * Accept incoming messages and try to deliver it to a registered listener.
     *
     * This code is defensive to prevent any single handler from affecting another if
     * it throws (producer interference).
     *
     * @param {MessageResponse | SubscriptionEvent} payload
     * @internal
     */
    _dispatch(payload) {
      if (!payload) return this._log("no response");
      if ("id" in payload) {
        if (this.listeners.has(payload.id)) {
          this._tryCatch(() => this.listeners.get(payload.id)?.(payload));
        } else {
          this._log("no listeners for ", payload);
        }
      }
      if ("subscriptionName" in payload) {
        if (this.listeners.has(payload.subscriptionName)) {
          this._tryCatch(() => this.listeners.get(payload.subscriptionName)?.(payload));
        } else {
          this._log("no subscription listeners for ", payload);
        }
      }
    }
    /**
     *
     * @param {(...args: any[]) => any} fn
     * @param {string} [context]
     */
    _tryCatch(fn, context = "none") {
      try {
        return fn();
      } catch (e3) {
        if (this.debug) {
          console.error("AndroidMessagingConfig error:", context);
          console.error(e3);
        }
      }
    }
    /**
     * @param {...any} args
     */
    _log(...args) {
      if (this.debug) {
        console.log("AndroidMessagingConfig", ...args);
      }
    }
    /**
     * Capture the global handler and remove it from the global object.
     */
    _captureGlobalHandler() {
      const { target, javascriptInterface } = this;
      if (Object.prototype.hasOwnProperty.call(target, javascriptInterface)) {
        this._capturedHandler = target[javascriptInterface].process.bind(target[javascriptInterface]);
        delete target[javascriptInterface];
      } else {
        this._capturedHandler = () => {
          this._log("Android messaging interface not available", javascriptInterface);
        };
      }
    }
    /**
     * Assign the incoming handler method to the global object.
     * This is the method that Android will call to deliver messages.
     */
    _assignHandlerMethod() {
      const responseHandler = (providedSecret, response) => {
        if (providedSecret === this.messageSecret) {
          this._dispatch(response);
        }
      };
      Object.defineProperty(this.target, this.messageCallback, {
        value: responseHandler
      });
    }
  };

  // ../injected/src/captured-globals.js
  var Set2 = globalThis.Set;
  var Reflect2 = globalThis.Reflect;
  var customElementsGet = globalThis.customElements?.get.bind(globalThis.customElements);
  var customElementsDefine = globalThis.customElements?.define.bind(globalThis.customElements);
  var URL2 = globalThis.URL;
  var Proxy2 = globalThis.Proxy;
  var functionToString = Function.prototype.toString;
  var TypeError2 = globalThis.TypeError;
  var Symbol2 = globalThis.Symbol;
  var dispatchEvent = globalThis.dispatchEvent?.bind(globalThis);
  var addEventListener = globalThis.addEventListener?.bind(globalThis);
  var removeEventListener = globalThis.removeEventListener?.bind(globalThis);
  var CustomEvent2 = globalThis.CustomEvent;
  var Promise2 = globalThis.Promise;
  var String2 = globalThis.String;
  var Map2 = globalThis.Map;
  var Error2 = globalThis.Error;
  var randomUUID = globalThis.crypto?.randomUUID?.bind(globalThis.crypto);

  // ../injected/src/utils.js
  var globalObj = typeof window === "undefined" ? globalThis : window;
  var Error3 = globalObj.Error;
  var originalWindowDispatchEvent = typeof window === "undefined" ? null : window.dispatchEvent.bind(window);
  function isBeingFramed() {
    if (globalThis.location && "ancestorOrigins" in globalThis.location) {
      return globalThis.location.ancestorOrigins.length > 0;
    }
    return globalThis.top !== globalThis.window;
  }
  var DDGPromise = globalObj.Promise;
  var DDGReflect = globalObj.Reflect;

  // ../messaging/lib/android-adsjs.js
  var AndroidAdsjsMessagingTransport = class {
    /**
     * @param {AndroidAdsjsMessagingConfig} config
     * @param {MessagingContext} messagingContext
     * @internal
     */
    constructor(config, messagingContext) {
      this.messagingContext = messagingContext;
      this.config = config;
    }
    /**
     * @param {NotificationMessage} msg
     */
    notify(msg) {
      try {
        this.config.sendMessageThrows?.(msg);
      } catch (e3) {
        console.error(".notify failed", e3);
      }
    }
    /**
     * @param {RequestMessage} msg
     * @return {Promise<any>}
     */
    request(msg) {
      return new Promise((resolve, reject) => {
        const unsub = this.config.subscribe(msg.id, handler);
        try {
          this.config.sendMessageThrows?.(msg);
        } catch (e3) {
          unsub();
          reject(new Error("request failed to send: " + e3.message || "unknown error"));
        }
        function handler(data) {
          if (isResponseFor(msg, data)) {
            if (data.result) {
              resolve(data.result || {});
              return unsub();
            }
            if (data.error) {
              reject(new Error(data.error.message));
              return unsub();
            }
            unsub();
            throw new Error("unreachable: must have `result` or `error` key by this point");
          }
        }
      });
    }
    /**
     * @param {Subscription} msg
     * @param {(value: unknown | undefined) => void} callback
     */
    subscribe(msg, callback) {
      const unsub = this.config.subscribe(msg.subscriptionName, (data) => {
        if (isSubscriptionEventFor(msg, data)) {
          callback(data.params || {});
        }
      });
      return () => {
        unsub();
      };
    }
  };
  var AndroidAdsjsMessagingConfig = class {
    /** @type {{
     * postMessage: (message: string) => void,
     * addEventListener: (type: string, listener: (event: MessageEvent) => void) => void,
     * } | null} */
    _capturedHandler;
    /**
     * @param {object} params
     * @param {Record<string, any>} params.target
     * @param {boolean} params.debug
     * @param {string} params.objectName - the object name for addWebMessageListener
     */
    constructor(params) {
      this.target = params.target;
      this.debug = params.debug;
      this.objectName = params.objectName;
      this.listeners = new globalThis.Map();
      this._captureGlobalHandler();
      this._setupEventListener();
    }
    /**
     * The transport can call this to transmit a JSON payload along with a secret
     * to the native Android handler via postMessage.
     *
     * Note: This can throw - it's up to the transport to handle the error.
     *
     * @type {(json: object) => void}
     * @throws
     * @internal
     */
    sendMessageThrows(message) {
      if (!this.objectName) {
        throw new Error("Object name not set for WebMessageListener");
      }
      if (this._capturedHandler && this._capturedHandler.postMessage) {
        this._capturedHandler.postMessage(JSON.stringify(message));
      } else {
        throw new Error("postMessage not available");
      }
    }
    /**
     * A subscription on Android is just a named listener. All messages from
     * android -> are delivered through a single function, and this mapping is used
     * to route the messages to the correct listener.
     *
     * Note: Use this to implement request->response by unsubscribing after the first
     * response.
     *
     * @param {string} id
     * @param {(msg: MessageResponse | SubscriptionEvent) => void} callback
     * @returns {() => void}
     * @internal
     */
    subscribe(id, callback) {
      this.listeners.set(id, callback);
      return () => {
        this.listeners.delete(id);
      };
    }
    /**
     * Accept incoming messages and try to deliver it to a registered listener.
     *
     * This code is defensive to prevent any single handler from affecting another if
     * it throws (producer interference).
     *
     * @param {MessageResponse | SubscriptionEvent} payload
     * @internal
     */
    _dispatch(payload) {
      if (!payload) return this._log("no response");
      if ("id" in payload) {
        if (this.listeners.has(payload.id)) {
          this._tryCatch(() => this.listeners.get(payload.id)?.(payload));
        } else {
          this._log("no listeners for ", payload);
        }
      }
      if ("subscriptionName" in payload) {
        if (this.listeners.has(payload.subscriptionName)) {
          this._tryCatch(() => this.listeners.get(payload.subscriptionName)?.(payload));
        } else {
          this._log("no subscription listeners for ", payload);
        }
      }
    }
    /**
     *
     * @param {(...args: any[]) => any} fn
     * @param {string} [context]
     */
    _tryCatch(fn, context = "none") {
      try {
        return fn();
      } catch (e3) {
        if (this.debug) {
          console.error("AndroidAdsjsMessagingConfig error:", context);
          console.error(e3);
        }
      }
    }
    /**
     * @param {...any} args
     */
    _log(...args) {
      if (this.debug) {
        console.log("AndroidAdsjsMessagingConfig", ...args);
      }
    }
    /**
     * Capture the global handler and remove it from the global object.
     */
    _captureGlobalHandler() {
      const { target, objectName } = this;
      if (Object.prototype.hasOwnProperty.call(target, objectName)) {
        this._capturedHandler = target[objectName];
        delete target[objectName];
      } else {
        this._capturedHandler = null;
        this._log("Android adsjs messaging interface not available", objectName);
      }
    }
    /**
     * Set up event listener for incoming messages from the captured handler.
     */
    _setupEventListener() {
      if (!this._capturedHandler || !this._capturedHandler.addEventListener) {
        this._log("No event listener support available");
        return;
      }
      this._capturedHandler.addEventListener("message", (event) => {
        try {
          const data = (
            /** @type {MessageEvent} */
            event.data
          );
          if (typeof data === "string") {
            const parsedData = JSON.parse(data);
            this._dispatch(parsedData);
          }
        } catch (e3) {
          this._log("Error processing incoming message:", e3);
        }
      });
    }
    /**
     * Send an initial ping message to the platform to establish communication.
     * This is a fire-and-forget notification that signals the JavaScript side is ready.
     * Only sends in top context (not in frames) and if the messaging interface is available.
     *
     * @param {MessagingContext} messagingContext
     * @returns {boolean} true if ping was sent, false if in frame or interface not ready
     */
    sendInitialPing(messagingContext) {
      if (isBeingFramed()) {
        this._log("Skipping initial ping - running in frame context");
        return false;
      }
      try {
        const message = new RequestMessage({
          id: "initialPing",
          context: messagingContext.context,
          featureName: "messaging",
          method: "initialPing"
        });
        this.sendMessageThrows(message);
        this._log("Initial ping sent successfully");
        return true;
      } catch (e3) {
        this._log("Failed to send initial ping:", e3);
        return false;
      }
    }
  };

  // ../messaging/lib/typed-messages.js
  function createTypedMessages(_base, _messaging) {
    const asAny = (
      /** @type {any} */
      _messaging
    );
    return (
      /** @type {BaseClass} */
      asAny
    );
  }

  // ../messaging/index.js
  var MessagingContext = class {
    /**
     * @param {object} params
     * @param {string} params.context
     * @param {string} params.featureName
     * @param {"production" | "development"} params.env
     * @internal
     */
    constructor(params) {
      this.context = params.context;
      this.featureName = params.featureName;
      this.env = params.env;
    }
  };
  var Messaging = class {
    /**
     * @param {MessagingContext} messagingContext
     * @param {MessagingConfig} config
     */
    constructor(messagingContext, config) {
      this.messagingContext = messagingContext;
      this.transport = getTransport(config, this.messagingContext);
    }
    /**
     * Send a 'fire-and-forget' message.
     *
     * @example
     *
     * ```ts
     * const messaging = new Messaging(config)
     * messaging.notify("foo", {bar: "baz"})
     * ```
     * @param {string} name
     * @param {Record<string, any>} [data]
     */
    notify(name, data = {}) {
      const message = new NotificationMessage({
        context: this.messagingContext.context,
        featureName: this.messagingContext.featureName,
        method: name,
        params: data
      });
      try {
        this.transport.notify(message);
      } catch (e3) {
        if (this.messagingContext.env === "development") {
          console.error("[Messaging] Failed to send notification:", e3);
          console.error("[Messaging] Message details:", { name, data });
        }
      }
    }
    /**
     * Send a request and wait for a response
     * @throws {Error}
     *
     * @example
     * ```
     * const messaging = new Messaging(config)
     * const response = await messaging.request("foo", {bar: "baz"})
     * ```
     *
     * @param {string} name
     * @param {Record<string, any>} [data]
     * @return {Promise<any>}
     */
    request(name, data = {}) {
      const id = globalThis?.crypto?.randomUUID?.() || name + ".response";
      const message = new RequestMessage({
        context: this.messagingContext.context,
        featureName: this.messagingContext.featureName,
        method: name,
        params: data,
        id
      });
      return this.transport.request(message);
    }
    /**
     * @param {string} name
     * @param {(value: unknown) => void} callback
     * @return {() => void}
     */
    subscribe(name, callback) {
      const msg = new Subscription({
        context: this.messagingContext.context,
        featureName: this.messagingContext.featureName,
        subscriptionName: name
      });
      return this.transport.subscribe(msg, callback);
    }
  };
  var TestTransportConfig = class {
    /**
     * @param {MessagingTransport} impl
     */
    constructor(impl) {
      this.impl = impl;
    }
  };
  var TestTransport = class {
    /**
     * @param {TestTransportConfig} config
     * @param {MessagingContext} messagingContext
     */
    constructor(config, messagingContext) {
      this.config = config;
      this.messagingContext = messagingContext;
    }
    notify(msg) {
      return this.config.impl.notify(msg);
    }
    request(msg) {
      return this.config.impl.request(msg);
    }
    subscribe(msg, callback) {
      return this.config.impl.subscribe(msg, callback);
    }
  };
  function getTransport(config, messagingContext) {
    if (config instanceof WebkitMessagingConfig) {
      return new WebkitMessagingTransport(config, messagingContext);
    }
    if (config instanceof WindowsMessagingConfig) {
      return new WindowsMessagingTransport(config, messagingContext);
    }
    if (config instanceof AndroidMessagingConfig) {
      return new AndroidMessagingTransport(config, messagingContext);
    }
    if (config instanceof AndroidAdsjsMessagingConfig) {
      return new AndroidAdsjsMessagingTransport(config, messagingContext);
    }
    if (config instanceof TestTransportConfig) {
      return new TestTransport(config, messagingContext);
    }
    throw new Error("unreachable");
  }
  var MissingHandler = class extends Error {
    /**
     * @param {string} message
     * @param {string} handlerName
     */
    constructor(message, handlerName) {
      super(message);
      this.handlerName = handlerName;
    }
  };

  // shared/environment.js
  var Environment = class _Environment {
    /**
     * @param {object} params
     * @param {'app' | 'components'} [params.display] - whether to show the application or component list
     * @param {'production' | 'development'} [params.env] - application environment
     * @param {URLSearchParams} [params.urlParams] - URL params passed into the page
     * @param {ImportMeta['injectName']} [params.injectName] - application platform
     * @param {boolean} [params.willThrow] - whether the application will simulate an error
     * @param {boolean} [params.debugState] - whether to show debugging UI
     * @param {keyof typeof import('./utils').translationsLocales} [params.locale] - for applications strings and numbers formatting
     * @param {number} [params.textLength] - what ratio of text should be used. Set a number higher than 1 to have longer strings for testing
     */
    constructor({
      env = "production",
      urlParams = new URLSearchParams(location.search),
      injectName = "windows",
      willThrow = urlParams.get("willThrow") === "true",
      debugState = urlParams.has("debugState"),
      display = "app",
      locale = "en",
      textLength = 1
    } = {}) {
      this.display = display;
      this.urlParams = urlParams;
      this.injectName = injectName;
      this.willThrow = willThrow;
      this.debugState = debugState;
      this.env = env;
      this.locale = locale;
      this.textLength = textLength;
    }
    /**
     * @param {string|null|undefined} injectName
     * @returns {Environment}
     */
    withInjectName(injectName) {
      if (!injectName) return this;
      if (!isInjectName(injectName)) return this;
      return new _Environment({
        ...this,
        injectName
      });
    }
    /**
     * @param {string|null|undefined} env
     * @returns {Environment}
     */
    withEnv(env) {
      if (!env) return this;
      if (env !== "production" && env !== "development") return this;
      return new _Environment({
        ...this,
        env
      });
    }
    /**
     * @param {string|null|undefined} display
     * @returns {Environment}
     */
    withDisplay(display) {
      if (!display) return this;
      if (display !== "app" && display !== "components") return this;
      return new _Environment({
        ...this,
        display
      });
    }
    /**
     * @param {string|null|undefined} locale
     * @returns {Environment}
     */
    withLocale(locale) {
      if (!locale) return this;
      if (typeof locale !== "string") return this;
      if (locale.length !== 2) return this;
      return new _Environment({
        ...this,
        locale
      });
    }
    /**
     * @param {string|number|null|undefined} length
     * @returns {Environment}
     */
    withTextLength(length) {
      if (!length) return this;
      const num = Number(length);
      if (num >= 1 && num <= 2) {
        return new _Environment({
          ...this,
          textLength: num
        });
      }
      return this;
    }
  };
  function isInjectName(input) {
    const allowed = ["windows", "apple", "integration", "android"];
    return allowed.includes(input);
  }

  // shared/create-special-page-messaging.js
  function createSpecialPageMessaging(opts) {
    const messageContext = new MessagingContext({
      context: "specialPages",
      featureName: opts.pageName,
      env: opts.env
    });
    try {
      if (opts.injectName === "windows") {
        const opts2 = new WindowsMessagingConfig({
          methods: {
            // @ts-expect-error - not in @types/chrome
            postMessage: globalThis.windowsInteropPostMessage,
            // @ts-expect-error - not in @types/chrome
            addEventListener: globalThis.windowsInteropAddEventListener,
            // @ts-expect-error - not in @types/chrome
            removeEventListener: globalThis.windowsInteropRemoveEventListener
          }
        });
        return new Messaging(messageContext, opts2);
      } else if (opts.injectName === "apple") {
        const opts2 = new WebkitMessagingConfig({
          hasModernWebkitAPI: true,
          secret: "",
          webkitMessageHandlerNames: ["specialPages"]
        });
        return new Messaging(messageContext, opts2);
      } else if (opts.injectName === "android") {
        const opts2 = new AndroidMessagingConfig({
          messageSecret: "duckduckgo-android-messaging-secret",
          messageCallback: "messageCallback",
          javascriptInterface: messageContext.context,
          target: globalThis,
          debug: true
        });
        return new Messaging(messageContext, opts2);
      }
    } catch (e3) {
      console.error("could not access handlers for %s, falling back to mock interface", opts.injectName);
    }
    const fallback = opts.mockTransport?.() || new TestTransportConfig({
      /**
       * @param {import('@duckduckgo/messaging').NotificationMessage} msg
       */
      notify(msg) {
        console.log(msg);
      },
      /**
       * @param {import('@duckduckgo/messaging').RequestMessage} msg
       */
      request: (msg) => {
        console.log(msg);
        if (msg.method === "initialSetup") {
          return Promise.resolve({
            locale: "en",
            env: opts.env
          });
        }
        return Promise.resolve(null);
      },
      /**
       * @param {import('@duckduckgo/messaging').SubscriptionEvent} msg
       */
      subscribe(msg) {
        console.log(msg);
        return () => {
          console.log("teardown");
        };
      }
    });
    return new Messaging(messageContext, fallback);
  }

  // shared/call-with-retry.js
  async function callWithRetry(fn, params = {}) {
    const { maxAttempts = 10, intervalMs = 300 } = params;
    let attempt = 1;
    while (attempt <= maxAttempts) {
      try {
        return { value: await fn(), attempt };
      } catch (error) {
        if (attempt === maxAttempts) {
          return { error: `Max attempts reached: ${error}` };
        }
        await new Promise((resolve) => setTimeout(resolve, intervalMs));
        attempt++;
      }
    }
    return { error: "Unreachable: value not retrieved" };
  }

  // ../node_modules/preact/dist/preact.module.js
  var n;
  var l;
  var u;
  var t;
  var i;
  var r;
  var o;
  var e;
  var f;
  var c;
  var s;
  var a;
  var h;
  var p = {};
  var v = [];
  var y = /acit|ex(?:s|g|n|p|$)|rph|grid|ows|mnc|ntw|ine[ch]|zoo|^ord|itera/i;
  var w = Array.isArray;
  function d(n2, l3) {
    for (var u3 in l3) n2[u3] = l3[u3];
    return n2;
  }
  function g(n2) {
    n2 && n2.parentNode && n2.parentNode.removeChild(n2);
  }
  function _(l3, u3, t3) {
    var i3, r3, o3, e3 = {};
    for (o3 in u3) "key" == o3 ? i3 = u3[o3] : "ref" == o3 ? r3 = u3[o3] : e3[o3] = u3[o3];
    if (arguments.length > 2 && (e3.children = arguments.length > 3 ? n.call(arguments, 2) : t3), "function" == typeof l3 && null != l3.defaultProps) for (o3 in l3.defaultProps) void 0 === e3[o3] && (e3[o3] = l3.defaultProps[o3]);
    return m(l3, e3, i3, r3, null);
  }
  function m(n2, t3, i3, r3, o3) {
    var e3 = { type: n2, props: t3, key: i3, ref: r3, __k: null, __: null, __b: 0, __e: null, __c: null, constructor: void 0, __v: null == o3 ? ++u : o3, __i: -1, __u: 0 };
    return null == o3 && null != l.vnode && l.vnode(e3), e3;
  }
  function k(n2) {
    return n2.children;
  }
  function x(n2, l3) {
    this.props = n2, this.context = l3;
  }
  function S(n2, l3) {
    if (null == l3) return n2.__ ? S(n2.__, n2.__i + 1) : null;
    for (var u3; l3 < n2.__k.length; l3++) if (null != (u3 = n2.__k[l3]) && null != u3.__e) return u3.__e;
    return "function" == typeof n2.type ? S(n2) : null;
  }
  function C(n2) {
    var l3, u3;
    if (null != (n2 = n2.__) && null != n2.__c) {
      for (n2.__e = n2.__c.base = null, l3 = 0; l3 < n2.__k.length; l3++) if (null != (u3 = n2.__k[l3]) && null != u3.__e) {
        n2.__e = n2.__c.base = u3.__e;
        break;
      }
      return C(n2);
    }
  }
  function M(n2) {
    (!n2.__d && (n2.__d = true) && i.push(n2) && !$.__r++ || r != l.debounceRendering) && ((r = l.debounceRendering) || o)($);
  }
  function $() {
    for (var n2, u3, t3, r3, o3, f3, c3, s3 = 1; i.length; ) i.length > s3 && i.sort(e), n2 = i.shift(), s3 = i.length, n2.__d && (t3 = void 0, o3 = (r3 = (u3 = n2).__v).__e, f3 = [], c3 = [], u3.__P && ((t3 = d({}, r3)).__v = r3.__v + 1, l.vnode && l.vnode(t3), O(u3.__P, t3, r3, u3.__n, u3.__P.namespaceURI, 32 & r3.__u ? [o3] : null, f3, null == o3 ? S(r3) : o3, !!(32 & r3.__u), c3), t3.__v = r3.__v, t3.__.__k[t3.__i] = t3, z(f3, t3, c3), t3.__e != o3 && C(t3)));
    $.__r = 0;
  }
  function I(n2, l3, u3, t3, i3, r3, o3, e3, f3, c3, s3) {
    var a3, h3, y3, w3, d3, g3, _3 = t3 && t3.__k || v, m3 = l3.length;
    for (f3 = P(u3, l3, _3, f3, m3), a3 = 0; a3 < m3; a3++) null != (y3 = u3.__k[a3]) && (h3 = -1 == y3.__i ? p : _3[y3.__i] || p, y3.__i = a3, g3 = O(n2, y3, h3, i3, r3, o3, e3, f3, c3, s3), w3 = y3.__e, y3.ref && h3.ref != y3.ref && (h3.ref && q(h3.ref, null, y3), s3.push(y3.ref, y3.__c || w3, y3)), null == d3 && null != w3 && (d3 = w3), 4 & y3.__u || h3.__k === y3.__k ? f3 = A(y3, f3, n2) : "function" == typeof y3.type && void 0 !== g3 ? f3 = g3 : w3 && (f3 = w3.nextSibling), y3.__u &= -7);
    return u3.__e = d3, f3;
  }
  function P(n2, l3, u3, t3, i3) {
    var r3, o3, e3, f3, c3, s3 = u3.length, a3 = s3, h3 = 0;
    for (n2.__k = new Array(i3), r3 = 0; r3 < i3; r3++) null != (o3 = l3[r3]) && "boolean" != typeof o3 && "function" != typeof o3 ? (f3 = r3 + h3, (o3 = n2.__k[r3] = "string" == typeof o3 || "number" == typeof o3 || "bigint" == typeof o3 || o3.constructor == String ? m(null, o3, null, null, null) : w(o3) ? m(k, { children: o3 }, null, null, null) : null == o3.constructor && o3.__b > 0 ? m(o3.type, o3.props, o3.key, o3.ref ? o3.ref : null, o3.__v) : o3).__ = n2, o3.__b = n2.__b + 1, e3 = null, -1 != (c3 = o3.__i = L(o3, u3, f3, a3)) && (a3--, (e3 = u3[c3]) && (e3.__u |= 2)), null == e3 || null == e3.__v ? (-1 == c3 && (i3 > s3 ? h3-- : i3 < s3 && h3++), "function" != typeof o3.type && (o3.__u |= 4)) : c3 != f3 && (c3 == f3 - 1 ? h3-- : c3 == f3 + 1 ? h3++ : (c3 > f3 ? h3-- : h3++, o3.__u |= 4))) : n2.__k[r3] = null;
    if (a3) for (r3 = 0; r3 < s3; r3++) null != (e3 = u3[r3]) && 0 == (2 & e3.__u) && (e3.__e == t3 && (t3 = S(e3)), B(e3, e3));
    return t3;
  }
  function A(n2, l3, u3) {
    var t3, i3;
    if ("function" == typeof n2.type) {
      for (t3 = n2.__k, i3 = 0; t3 && i3 < t3.length; i3++) t3[i3] && (t3[i3].__ = n2, l3 = A(t3[i3], l3, u3));
      return l3;
    }
    n2.__e != l3 && (l3 && n2.type && !u3.contains(l3) && (l3 = S(n2)), u3.insertBefore(n2.__e, l3 || null), l3 = n2.__e);
    do {
      l3 = l3 && l3.nextSibling;
    } while (null != l3 && 8 == l3.nodeType);
    return l3;
  }
  function L(n2, l3, u3, t3) {
    var i3, r3, o3 = n2.key, e3 = n2.type, f3 = l3[u3];
    if (null === f3 && null == n2.key || f3 && o3 == f3.key && e3 == f3.type && 0 == (2 & f3.__u)) return u3;
    if (t3 > (null != f3 && 0 == (2 & f3.__u) ? 1 : 0)) for (i3 = u3 - 1, r3 = u3 + 1; i3 >= 0 || r3 < l3.length; ) {
      if (i3 >= 0) {
        if ((f3 = l3[i3]) && 0 == (2 & f3.__u) && o3 == f3.key && e3 == f3.type) return i3;
        i3--;
      }
      if (r3 < l3.length) {
        if ((f3 = l3[r3]) && 0 == (2 & f3.__u) && o3 == f3.key && e3 == f3.type) return r3;
        r3++;
      }
    }
    return -1;
  }
  function T(n2, l3, u3) {
    "-" == l3[0] ? n2.setProperty(l3, null == u3 ? "" : u3) : n2[l3] = null == u3 ? "" : "number" != typeof u3 || y.test(l3) ? u3 : u3 + "px";
  }
  function j(n2, l3, u3, t3, i3) {
    var r3, o3;
    n: if ("style" == l3) if ("string" == typeof u3) n2.style.cssText = u3;
    else {
      if ("string" == typeof t3 && (n2.style.cssText = t3 = ""), t3) for (l3 in t3) u3 && l3 in u3 || T(n2.style, l3, "");
      if (u3) for (l3 in u3) t3 && u3[l3] == t3[l3] || T(n2.style, l3, u3[l3]);
    }
    else if ("o" == l3[0] && "n" == l3[1]) r3 = l3 != (l3 = l3.replace(f, "$1")), o3 = l3.toLowerCase(), l3 = o3 in n2 || "onFocusOut" == l3 || "onFocusIn" == l3 ? o3.slice(2) : l3.slice(2), n2.l || (n2.l = {}), n2.l[l3 + r3] = u3, u3 ? t3 ? u3.u = t3.u : (u3.u = c, n2.addEventListener(l3, r3 ? a : s, r3)) : n2.removeEventListener(l3, r3 ? a : s, r3);
    else {
      if ("http://www.w3.org/2000/svg" == i3) l3 = l3.replace(/xlink(H|:h)/, "h").replace(/sName$/, "s");
      else if ("width" != l3 && "height" != l3 && "href" != l3 && "list" != l3 && "form" != l3 && "tabIndex" != l3 && "download" != l3 && "rowSpan" != l3 && "colSpan" != l3 && "role" != l3 && "popover" != l3 && l3 in n2) try {
        n2[l3] = null == u3 ? "" : u3;
        break n;
      } catch (n3) {
      }
      "function" == typeof u3 || (null == u3 || false === u3 && "-" != l3[4] ? n2.removeAttribute(l3) : n2.setAttribute(l3, "popover" == l3 && 1 == u3 ? "" : u3));
    }
  }
  function F(n2) {
    return function(u3) {
      if (this.l) {
        var t3 = this.l[u3.type + n2];
        if (null == u3.t) u3.t = c++;
        else if (u3.t < t3.u) return;
        return t3(l.event ? l.event(u3) : u3);
      }
    };
  }
  function O(n2, u3, t3, i3, r3, o3, e3, f3, c3, s3) {
    var a3, h3, p3, v3, y3, _3, m3, b, S2, C3, M2, $2, P2, A3, H, L2, T3, j3 = u3.type;
    if (null != u3.constructor) return null;
    128 & t3.__u && (c3 = !!(32 & t3.__u), o3 = [f3 = u3.__e = t3.__e]), (a3 = l.__b) && a3(u3);
    n: if ("function" == typeof j3) try {
      if (b = u3.props, S2 = "prototype" in j3 && j3.prototype.render, C3 = (a3 = j3.contextType) && i3[a3.__c], M2 = a3 ? C3 ? C3.props.value : a3.__ : i3, t3.__c ? m3 = (h3 = u3.__c = t3.__c).__ = h3.__E : (S2 ? u3.__c = h3 = new j3(b, M2) : (u3.__c = h3 = new x(b, M2), h3.constructor = j3, h3.render = D), C3 && C3.sub(h3), h3.props = b, h3.state || (h3.state = {}), h3.context = M2, h3.__n = i3, p3 = h3.__d = true, h3.__h = [], h3._sb = []), S2 && null == h3.__s && (h3.__s = h3.state), S2 && null != j3.getDerivedStateFromProps && (h3.__s == h3.state && (h3.__s = d({}, h3.__s)), d(h3.__s, j3.getDerivedStateFromProps(b, h3.__s))), v3 = h3.props, y3 = h3.state, h3.__v = u3, p3) S2 && null == j3.getDerivedStateFromProps && null != h3.componentWillMount && h3.componentWillMount(), S2 && null != h3.componentDidMount && h3.__h.push(h3.componentDidMount);
      else {
        if (S2 && null == j3.getDerivedStateFromProps && b !== v3 && null != h3.componentWillReceiveProps && h3.componentWillReceiveProps(b, M2), !h3.__e && null != h3.shouldComponentUpdate && false === h3.shouldComponentUpdate(b, h3.__s, M2) || u3.__v == t3.__v) {
          for (u3.__v != t3.__v && (h3.props = b, h3.state = h3.__s, h3.__d = false), u3.__e = t3.__e, u3.__k = t3.__k, u3.__k.some(function(n3) {
            n3 && (n3.__ = u3);
          }), $2 = 0; $2 < h3._sb.length; $2++) h3.__h.push(h3._sb[$2]);
          h3._sb = [], h3.__h.length && e3.push(h3);
          break n;
        }
        null != h3.componentWillUpdate && h3.componentWillUpdate(b, h3.__s, M2), S2 && null != h3.componentDidUpdate && h3.__h.push(function() {
          h3.componentDidUpdate(v3, y3, _3);
        });
      }
      if (h3.context = M2, h3.props = b, h3.__P = n2, h3.__e = false, P2 = l.__r, A3 = 0, S2) {
        for (h3.state = h3.__s, h3.__d = false, P2 && P2(u3), a3 = h3.render(h3.props, h3.state, h3.context), H = 0; H < h3._sb.length; H++) h3.__h.push(h3._sb[H]);
        h3._sb = [];
      } else do {
        h3.__d = false, P2 && P2(u3), a3 = h3.render(h3.props, h3.state, h3.context), h3.state = h3.__s;
      } while (h3.__d && ++A3 < 25);
      h3.state = h3.__s, null != h3.getChildContext && (i3 = d(d({}, i3), h3.getChildContext())), S2 && !p3 && null != h3.getSnapshotBeforeUpdate && (_3 = h3.getSnapshotBeforeUpdate(v3, y3)), L2 = a3, null != a3 && a3.type === k && null == a3.key && (L2 = N(a3.props.children)), f3 = I(n2, w(L2) ? L2 : [L2], u3, t3, i3, r3, o3, e3, f3, c3, s3), h3.base = u3.__e, u3.__u &= -161, h3.__h.length && e3.push(h3), m3 && (h3.__E = h3.__ = null);
    } catch (n3) {
      if (u3.__v = null, c3 || null != o3) if (n3.then) {
        for (u3.__u |= c3 ? 160 : 128; f3 && 8 == f3.nodeType && f3.nextSibling; ) f3 = f3.nextSibling;
        o3[o3.indexOf(f3)] = null, u3.__e = f3;
      } else for (T3 = o3.length; T3--; ) g(o3[T3]);
      else u3.__e = t3.__e, u3.__k = t3.__k;
      l.__e(n3, u3, t3);
    }
    else null == o3 && u3.__v == t3.__v ? (u3.__k = t3.__k, u3.__e = t3.__e) : f3 = u3.__e = V(t3.__e, u3, t3, i3, r3, o3, e3, c3, s3);
    return (a3 = l.diffed) && a3(u3), 128 & u3.__u ? void 0 : f3;
  }
  function z(n2, u3, t3) {
    for (var i3 = 0; i3 < t3.length; i3++) q(t3[i3], t3[++i3], t3[++i3]);
    l.__c && l.__c(u3, n2), n2.some(function(u4) {
      try {
        n2 = u4.__h, u4.__h = [], n2.some(function(n3) {
          n3.call(u4);
        });
      } catch (n3) {
        l.__e(n3, u4.__v);
      }
    });
  }
  function N(n2) {
    return "object" != typeof n2 || null == n2 || n2.__b && n2.__b > 0 ? n2 : w(n2) ? n2.map(N) : d({}, n2);
  }
  function V(u3, t3, i3, r3, o3, e3, f3, c3, s3) {
    var a3, h3, v3, y3, d3, _3, m3, b = i3.props, k3 = t3.props, x3 = t3.type;
    if ("svg" == x3 ? o3 = "http://www.w3.org/2000/svg" : "math" == x3 ? o3 = "http://www.w3.org/1998/Math/MathML" : o3 || (o3 = "http://www.w3.org/1999/xhtml"), null != e3) {
      for (a3 = 0; a3 < e3.length; a3++) if ((d3 = e3[a3]) && "setAttribute" in d3 == !!x3 && (x3 ? d3.localName == x3 : 3 == d3.nodeType)) {
        u3 = d3, e3[a3] = null;
        break;
      }
    }
    if (null == u3) {
      if (null == x3) return document.createTextNode(k3);
      u3 = document.createElementNS(o3, x3, k3.is && k3), c3 && (l.__m && l.__m(t3, e3), c3 = false), e3 = null;
    }
    if (null == x3) b === k3 || c3 && u3.data == k3 || (u3.data = k3);
    else {
      if (e3 = e3 && n.call(u3.childNodes), b = i3.props || p, !c3 && null != e3) for (b = {}, a3 = 0; a3 < u3.attributes.length; a3++) b[(d3 = u3.attributes[a3]).name] = d3.value;
      for (a3 in b) if (d3 = b[a3], "children" == a3) ;
      else if ("dangerouslySetInnerHTML" == a3) v3 = d3;
      else if (!(a3 in k3)) {
        if ("value" == a3 && "defaultValue" in k3 || "checked" == a3 && "defaultChecked" in k3) continue;
        j(u3, a3, null, d3, o3);
      }
      for (a3 in k3) d3 = k3[a3], "children" == a3 ? y3 = d3 : "dangerouslySetInnerHTML" == a3 ? h3 = d3 : "value" == a3 ? _3 = d3 : "checked" == a3 ? m3 = d3 : c3 && "function" != typeof d3 || b[a3] === d3 || j(u3, a3, d3, b[a3], o3);
      if (h3) c3 || v3 && (h3.__html == v3.__html || h3.__html == u3.innerHTML) || (u3.innerHTML = h3.__html), t3.__k = [];
      else if (v3 && (u3.innerHTML = ""), I("template" == t3.type ? u3.content : u3, w(y3) ? y3 : [y3], t3, i3, r3, "foreignObject" == x3 ? "http://www.w3.org/1999/xhtml" : o3, e3, f3, e3 ? e3[0] : i3.__k && S(i3, 0), c3, s3), null != e3) for (a3 = e3.length; a3--; ) g(e3[a3]);
      c3 || (a3 = "value", "progress" == x3 && null == _3 ? u3.removeAttribute("value") : null != _3 && (_3 !== u3[a3] || "progress" == x3 && !_3 || "option" == x3 && _3 != b[a3]) && j(u3, a3, _3, b[a3], o3), a3 = "checked", null != m3 && m3 != u3[a3] && j(u3, a3, m3, b[a3], o3));
    }
    return u3;
  }
  function q(n2, u3, t3) {
    try {
      if ("function" == typeof n2) {
        var i3 = "function" == typeof n2.__u;
        i3 && n2.__u(), i3 && null == u3 || (n2.__u = n2(u3));
      } else n2.current = u3;
    } catch (n3) {
      l.__e(n3, t3);
    }
  }
  function B(n2, u3, t3) {
    var i3, r3;
    if (l.unmount && l.unmount(n2), (i3 = n2.ref) && (i3.current && i3.current != n2.__e || q(i3, null, u3)), null != (i3 = n2.__c)) {
      if (i3.componentWillUnmount) try {
        i3.componentWillUnmount();
      } catch (n3) {
        l.__e(n3, u3);
      }
      i3.base = i3.__P = null;
    }
    if (i3 = n2.__k) for (r3 = 0; r3 < i3.length; r3++) i3[r3] && B(i3[r3], u3, t3 || "function" != typeof n2.type);
    t3 || g(n2.__e), n2.__c = n2.__ = n2.__e = void 0;
  }
  function D(n2, l3, u3) {
    return this.constructor(n2, u3);
  }
  function E(u3, t3, i3) {
    var r3, o3, e3, f3;
    t3 == document && (t3 = document.documentElement), l.__ && l.__(u3, t3), o3 = (r3 = "function" == typeof i3) ? null : i3 && i3.__k || t3.__k, e3 = [], f3 = [], O(t3, u3 = (!r3 && i3 || t3).__k = _(k, null, [u3]), o3 || p, p, t3.namespaceURI, !r3 && i3 ? [i3] : o3 ? null : t3.firstChild ? n.call(t3.childNodes) : null, e3, !r3 && i3 ? i3 : o3 ? o3.__e : t3.firstChild, r3, f3), z(e3, u3, f3);
  }
  function K(n2) {
    function l3(n3) {
      var u3, t3;
      return this.getChildContext || (u3 = /* @__PURE__ */ new Set(), (t3 = {})[l3.__c] = this, this.getChildContext = function() {
        return t3;
      }, this.componentWillUnmount = function() {
        u3 = null;
      }, this.shouldComponentUpdate = function(n4) {
        this.props.value != n4.value && u3.forEach(function(n5) {
          n5.__e = true, M(n5);
        });
      }, this.sub = function(n4) {
        u3.add(n4);
        var l4 = n4.componentWillUnmount;
        n4.componentWillUnmount = function() {
          u3 && u3.delete(n4), l4 && l4.call(n4);
        };
      }), n3.children;
    }
    return l3.__c = "__cC" + h++, l3.__ = n2, l3.Provider = l3.__l = (l3.Consumer = function(n3, l4) {
      return n3.children(l4);
    }).contextType = l3, l3;
  }
  n = v.slice, l = { __e: function(n2, l3, u3, t3) {
    for (var i3, r3, o3; l3 = l3.__; ) if ((i3 = l3.__c) && !i3.__) try {
      if ((r3 = i3.constructor) && null != r3.getDerivedStateFromError && (i3.setState(r3.getDerivedStateFromError(n2)), o3 = i3.__d), null != i3.componentDidCatch && (i3.componentDidCatch(n2, t3 || {}), o3 = i3.__d), o3) return i3.__E = i3;
    } catch (l4) {
      n2 = l4;
    }
    throw n2;
  } }, u = 0, t = function(n2) {
    return null != n2 && null == n2.constructor;
  }, x.prototype.setState = function(n2, l3) {
    var u3;
    u3 = null != this.__s && this.__s != this.state ? this.__s : this.__s = d({}, this.state), "function" == typeof n2 && (n2 = n2(d({}, u3), this.props)), n2 && d(u3, n2), null != n2 && this.__v && (l3 && this._sb.push(l3), M(this));
  }, x.prototype.forceUpdate = function(n2) {
    this.__v && (this.__e = true, n2 && this.__h.push(n2), M(this));
  }, x.prototype.render = k, i = [], o = "function" == typeof Promise ? Promise.prototype.then.bind(Promise.resolve()) : setTimeout, e = function(n2, l3) {
    return n2.__v.__b - l3.__v.__b;
  }, $.__r = 0, f = /(PointerCapture)$|Capture$/i, c = 0, s = F(false), a = F(true), h = 0;

  // ../node_modules/preact/hooks/dist/hooks.module.js
  var t2;
  var r2;
  var u2;
  var i2;
  var o2 = 0;
  var f2 = [];
  var c2 = l;
  var e2 = c2.__b;
  var a2 = c2.__r;
  var v2 = c2.diffed;
  var l2 = c2.__c;
  var m2 = c2.unmount;
  var s2 = c2.__;
  function p2(n2, t3) {
    c2.__h && c2.__h(r2, n2, o2 || t3), o2 = 0;
    var u3 = r2.__H || (r2.__H = { __: [], __h: [] });
    return n2 >= u3.__.length && u3.__.push({}), u3.__[n2];
  }
  function d2(n2) {
    return o2 = 1, h2(D2, n2);
  }
  function h2(n2, u3, i3) {
    var o3 = p2(t2++, 2);
    if (o3.t = n2, !o3.__c && (o3.__ = [i3 ? i3(u3) : D2(void 0, u3), function(n3) {
      var t3 = o3.__N ? o3.__N[0] : o3.__[0], r3 = o3.t(t3, n3);
      t3 !== r3 && (o3.__N = [r3, o3.__[1]], o3.__c.setState({}));
    }], o3.__c = r2, !r2.__f)) {
      var f3 = function(n3, t3, r3) {
        if (!o3.__c.__H) return true;
        var u4 = o3.__c.__H.__.filter(function(n4) {
          return !!n4.__c;
        });
        if (u4.every(function(n4) {
          return !n4.__N;
        })) return !c3 || c3.call(this, n3, t3, r3);
        var i4 = o3.__c.props !== n3;
        return u4.forEach(function(n4) {
          if (n4.__N) {
            var t4 = n4.__[0];
            n4.__ = n4.__N, n4.__N = void 0, t4 !== n4.__[0] && (i4 = true);
          }
        }), c3 && c3.call(this, n3, t3, r3) || i4;
      };
      r2.__f = true;
      var c3 = r2.shouldComponentUpdate, e3 = r2.componentWillUpdate;
      r2.componentWillUpdate = function(n3, t3, r3) {
        if (this.__e) {
          var u4 = c3;
          c3 = void 0, f3(n3, t3, r3), c3 = u4;
        }
        e3 && e3.call(this, n3, t3, r3);
      }, r2.shouldComponentUpdate = f3;
    }
    return o3.__N || o3.__;
  }
  function y2(n2, u3) {
    var i3 = p2(t2++, 3);
    !c2.__s && C2(i3.__H, u3) && (i3.__ = n2, i3.u = u3, r2.__H.__h.push(i3));
  }
  function _2(n2, u3) {
    var i3 = p2(t2++, 4);
    !c2.__s && C2(i3.__H, u3) && (i3.__ = n2, i3.u = u3, r2.__h.push(i3));
  }
  function A2(n2) {
    return o2 = 5, T2(function() {
      return { current: n2 };
    }, []);
  }
  function T2(n2, r3) {
    var u3 = p2(t2++, 7);
    return C2(u3.__H, r3) && (u3.__ = n2(), u3.__H = r3, u3.__h = n2), u3.__;
  }
  function q2(n2, t3) {
    return o2 = 8, T2(function() {
      return n2;
    }, t3);
  }
  function x2(n2) {
    var u3 = r2.context[n2.__c], i3 = p2(t2++, 9);
    return i3.c = n2, u3 ? (null == i3.__ && (i3.__ = true, u3.sub(r2)), u3.props.value) : n2.__;
  }
  function g2() {
    var n2 = p2(t2++, 11);
    if (!n2.__) {
      for (var u3 = r2.__v; null !== u3 && !u3.__m && null !== u3.__; ) u3 = u3.__;
      var i3 = u3.__m || (u3.__m = [0, 0]);
      n2.__ = "P" + i3[0] + "-" + i3[1]++;
    }
    return n2.__;
  }
  function j2() {
    for (var n2; n2 = f2.shift(); ) if (n2.__P && n2.__H) try {
      n2.__H.__h.forEach(z2), n2.__H.__h.forEach(B2), n2.__H.__h = [];
    } catch (t3) {
      n2.__H.__h = [], c2.__e(t3, n2.__v);
    }
  }
  c2.__b = function(n2) {
    r2 = null, e2 && e2(n2);
  }, c2.__ = function(n2, t3) {
    n2 && t3.__k && t3.__k.__m && (n2.__m = t3.__k.__m), s2 && s2(n2, t3);
  }, c2.__r = function(n2) {
    a2 && a2(n2), t2 = 0;
    var i3 = (r2 = n2.__c).__H;
    i3 && (u2 === r2 ? (i3.__h = [], r2.__h = [], i3.__.forEach(function(n3) {
      n3.__N && (n3.__ = n3.__N), n3.u = n3.__N = void 0;
    })) : (i3.__h.forEach(z2), i3.__h.forEach(B2), i3.__h = [], t2 = 0)), u2 = r2;
  }, c2.diffed = function(n2) {
    v2 && v2(n2);
    var t3 = n2.__c;
    t3 && t3.__H && (t3.__H.__h.length && (1 !== f2.push(t3) && i2 === c2.requestAnimationFrame || ((i2 = c2.requestAnimationFrame) || w2)(j2)), t3.__H.__.forEach(function(n3) {
      n3.u && (n3.__H = n3.u), n3.u = void 0;
    })), u2 = r2 = null;
  }, c2.__c = function(n2, t3) {
    t3.some(function(n3) {
      try {
        n3.__h.forEach(z2), n3.__h = n3.__h.filter(function(n4) {
          return !n4.__ || B2(n4);
        });
      } catch (r3) {
        t3.some(function(n4) {
          n4.__h && (n4.__h = []);
        }), t3 = [], c2.__e(r3, n3.__v);
      }
    }), l2 && l2(n2, t3);
  }, c2.unmount = function(n2) {
    m2 && m2(n2);
    var t3, r3 = n2.__c;
    r3 && r3.__H && (r3.__H.__.forEach(function(n3) {
      try {
        z2(n3);
      } catch (n4) {
        t3 = n4;
      }
    }), r3.__H = void 0, t3 && c2.__e(t3, r3.__v));
  };
  var k2 = "function" == typeof requestAnimationFrame;
  function w2(n2) {
    var t3, r3 = function() {
      clearTimeout(u3), k2 && cancelAnimationFrame(t3), setTimeout(n2);
    }, u3 = setTimeout(r3, 35);
    k2 && (t3 = requestAnimationFrame(r3));
  }
  function z2(n2) {
    var t3 = r2, u3 = n2.__c;
    "function" == typeof u3 && (n2.__c = void 0, u3()), r2 = t3;
  }
  function B2(n2) {
    var t3 = r2;
    n2.__c = n2.__(), r2 = t3;
  }
  function C2(n2, t3) {
    return !n2 || n2.length !== t3.length || t3.some(function(t4, r3) {
      return t4 !== n2[r3];
    });
  }
  function D2(n2, t3) {
    return "function" == typeof t3 ? t3(n2) : t3;
  }

  // shared/components/EnvironmentProvider.js
  var EnvironmentContext = K({
    isReducedMotion: false,
    isDarkMode: false,
    debugState: false,
    injectName: (
      /** @type {import('../environment').Environment['injectName']} */
      "windows"
    ),
    willThrow: false,
    /** @type {keyof typeof import('../utils').translationsLocales} */
    locale: "en",
    /** @type {import('../environment').Environment['env']} */
    env: "production"
  });
  var THEME_QUERY = "(prefers-color-scheme: dark)";
  var REDUCED_MOTION_QUERY = "(prefers-reduced-motion: reduce)";
  function EnvironmentProvider({
    children,
    debugState,
    env = "production",
    willThrow = false,
    injectName = "windows",
    locale = "en"
  }) {
    const [theme, setTheme] = d2(window.matchMedia(THEME_QUERY).matches ? "dark" : "light");
    const [isReducedMotion, setReducedMotion] = d2(window.matchMedia(REDUCED_MOTION_QUERY).matches);
    y2(() => {
      const mediaQueryList = window.matchMedia(THEME_QUERY);
      const listener = (e3) => setTheme(e3.matches ? "dark" : "light");
      mediaQueryList.addEventListener("change", listener);
      return () => mediaQueryList.removeEventListener("change", listener);
    }, []);
    y2(() => {
      const mediaQueryList = window.matchMedia(REDUCED_MOTION_QUERY);
      const listener = (e3) => setter(e3.matches);
      mediaQueryList.addEventListener("change", listener);
      setter(mediaQueryList.matches);
      function setter(value) {
        document.documentElement.dataset.reducedMotion = String(value);
        setReducedMotion(value);
      }
      window.addEventListener("toggle-reduced-motion", () => {
        setter(true);
      });
      return () => mediaQueryList.removeEventListener("change", listener);
    }, []);
    return /* @__PURE__ */ _(
      EnvironmentContext.Provider,
      {
        value: {
          isReducedMotion,
          debugState,
          isDarkMode: theme === "dark",
          injectName,
          willThrow,
          env,
          locale
        }
      },
      children
    );
  }
  function UpdateEnvironment({ search }) {
    y2(() => {
      const params = new URLSearchParams(search);
      if (params.has("reduced-motion")) {
        setTimeout(() => {
          window.dispatchEvent(new CustomEvent("toggle-reduced-motion"));
        }, 0);
      }
    }, [search]);
    return null;
  }
  function useEnv() {
    return x2(EnvironmentContext);
  }
  function WillThrow() {
    const env = useEnv();
    if (env.willThrow) {
      throw new Error("Simulated Exception");
    }
    return null;
  }

  // shared/translations.js
  function apply(subject, replacements, textLength = 1) {
    if (typeof subject !== "string" || subject.length === 0) return "";
    let out = subject;
    if (replacements) {
      for (let [name, value] of Object.entries(replacements)) {
        if (typeof value !== "string") value = "";
        out = out.replaceAll(`{${name}}`, value);
      }
    }
    if (textLength !== 1 && textLength > 0 && textLength <= 2) {
      const targetLen = Math.ceil(out.length * textLength);
      const target = Math.ceil(textLength);
      const combined = out.repeat(target);
      return combined.slice(0, targetLen);
    }
    return out;
  }

  // shared/components/TranslationsProvider.js
  var TranslationContext = K({
    /** @type {LocalTranslationFn} */
    t: () => {
      throw new Error("must implement");
    }
  });
  function TranslationProvider({ children, translationObject, fallback, textLength = 1 }) {
    function t3(inputKey, replacements) {
      const subject = translationObject?.[inputKey]?.title || fallback?.[inputKey]?.title;
      return apply(subject, replacements, textLength);
    }
    return /* @__PURE__ */ _(TranslationContext.Provider, { value: { t: t3 } }, children);
  }

  // shared/components/ErrorBoundary.js
  var ErrorBoundary = class extends x {
    constructor(props) {
      super(props);
      this.state = { hasError: false };
    }
    static getDerivedStateFromError() {
      return { hasError: true };
    }
    componentDidCatch(error, info) {
      console.error(error);
      console.log(info);
      let message = error.message;
      if (typeof message !== "string") message = "unknown";
      const composed = this.props.context ? [this.props.context, message].join(" ") : message;
      this.props.didCatch({ error, message: composed, info });
    }
    render() {
      if (this.state.hasError) {
        return this.props.fallback;
      }
      return this.props.children;
    }
  };

  // pages/duckplayer/app/embed-settings.js
  var EmbedSettings = class _EmbedSettings {
    /**
     * @param {object} params
     * @param {VideoId} params.videoId - videoID is required
     * @param {Timestamp|null|undefined} params.timestamp - optional timestamp
     * @param {boolean} [params.autoplay] - optional timestamp
     * @param {boolean} [params.muted] - optionally start muted
     */
    constructor({ videoId, timestamp, autoplay = true, muted = false }) {
      this.videoId = videoId;
      this.timestamp = timestamp;
      this.autoplay = autoplay;
      this.muted = muted;
    }
    /**
     * @param {boolean|null|undefined} autoplay
     * @return {EmbedSettings}
     */
    withAutoplay(autoplay) {
      if (typeof autoplay !== "boolean") return this;
      return new _EmbedSettings({
        ...this,
        autoplay
      });
    }
    /**
     * @param {boolean|null|undefined} muted
     * @return {EmbedSettings}
     */
    withMuted(muted) {
      if (typeof muted !== "boolean") return this;
      return new _EmbedSettings({
        ...this,
        muted
      });
    }
    /**
     * @param {string|null|undefined} href
     * @returns {EmbedSettings|null}
     */
    static fromHref(href) {
      try {
        return new _EmbedSettings({
          videoId: VideoId.fromHref(href),
          timestamp: Timestamp.fromHref(href)
        });
      } catch (e3) {
        console.error(e3);
        return null;
      }
    }
    /**
     * @return {string}
     */
    toEmbedUrl() {
      const url = new URL(`/embed/${this.videoId.id}`, "https://www.youtube-nocookie.com");
      url.searchParams.set("iv_load_policy", "1");
      if (this.autoplay) {
        url.searchParams.set("autoplay", "1");
        if (this.muted) {
          url.searchParams.set("muted", "1");
        }
      }
      url.searchParams.set("rel", "0");
      url.searchParams.set("modestbranding", "1");
      url.searchParams.set("color", "white");
      if (this.timestamp && this.timestamp.seconds > 0) {
        url.searchParams.set("start", String(this.timestamp.seconds));
      }
      return url.href;
    }
    /**
     * @param {URL} base
     * @return {string}
     */
    intoYoutubeUrl(base) {
      const url = new URL(base);
      url.searchParams.set("v", this.videoId.id);
      if (this.timestamp && this.timestamp.seconds > 0) {
        url.searchParams.set("t", `${this.timestamp.seconds}s`);
      }
      return url.toString();
    }
  };
  var VideoId = class _VideoId {
    /**
     * @param {string|null|undefined} input
     * @throws {Error}
     */
    constructor(input) {
      if (typeof input !== "string") throw new Error("string required, got: " + input);
      const sanitized = sanitizeYoutubeId(input);
      if (sanitized === null) throw new Error("invalid ID from: " + input);
      this.id = sanitized;
    }
    /**
     * @param {string|null|undefined} href
     */
    static fromHref(href) {
      return new _VideoId(idFromHref(href));
    }
  };
  var Timestamp = class _Timestamp {
    /**
     * @param {string|null|undefined} input
     * @throws {Error}
     */
    constructor(input) {
      if (typeof input !== "string") throw new Error("string required for timestamp");
      const seconds = timestampInSeconds(input);
      if (seconds === null) throw new Error("invalid input for timestamp: " + input);
      this.seconds = seconds;
    }
    /**
     * @param {string|null|undefined} href
     * @return {Timestamp|null}
     */
    static fromHref(href) {
      if (typeof href !== "string") return null;
      const param = timestampFromHref(href);
      if (param) {
        try {
          return new _Timestamp(param);
        } catch (e3) {
          return null;
        }
      }
      return null;
    }
  };
  function idFromHref(href) {
    if (typeof href !== "string") return null;
    let url;
    try {
      url = new URL(href);
    } catch (e3) {
      return null;
    }
    const fromParam = url.searchParams.get("videoID");
    if (fromParam) return fromParam;
    if (url.protocol === "duck:") {
      return url.pathname.slice(1);
    }
    if (url.pathname.includes("/embed/")) {
      return url.pathname.replace("/embed/", "");
    }
    return null;
  }
  function timestampFromHref(href) {
    if (typeof href !== "string") return null;
    let url;
    try {
      url = new URL(href);
    } catch (e3) {
      console.error(e3);
      return null;
    }
    const timeParameter = url.searchParams.get("t");
    if (timeParameter) {
      return timeParameter;
    }
    return null;
  }
  function timestampInSeconds(timestamp) {
    const units = {
      h: 3600,
      m: 60,
      s: 1
    };
    const parts = timestamp.split(/(\d+[hms]?)/);
    const totalSeconds = parts.reduce((total, part) => {
      if (!part) return total;
      for (const unit in units) {
        if (part.includes(unit)) {
          return total + parseInt(part) * units[unit];
        }
      }
      return total;
    }, 0);
    if (totalSeconds > 0) {
      return totalSeconds;
    }
    return null;
  }
  function sanitizeYoutubeId(input) {
    const subject = input.slice(0, 11);
    if (/^[a-zA-Z0-9-_]+$/.test(subject)) {
      return subject;
    }
    return null;
  }

  // pages/duckplayer/public/locales/en/duckplayer.json
  var duckplayer_default = {
    smartling: {
      string_format: "icu",
      translate_paths: [
        {
          path: "*/title",
          key: "{*}/title",
          instruction: "*/note"
        }
      ]
    },
    alwaysWatchHere: {
      title: "Always open YouTube videos here",
      note: "label text for a checkbox that enables this feature for all videos, not just the current one"
    },
    keepEnabled: {
      title: "Keep Duck Player turned on",
      note: "label text for a checkbox that enables this feature for all videos, not just the current one"
    },
    openInfoButton: {
      title: "Open Info",
      note: "aria label text on a button, to indicate there's more information to be shown if clicked"
    },
    openSettingsButton: {
      title: "Open Settings",
      note: "aria label text on a button, opens a screen where the user can change settings"
    },
    watchOnYoutube: {
      title: "Watch on YouTube",
      note: "text on a link that takes the user from the current page back onto YouTube.com"
    },
    invalidIdError: {
      title: "<b>ERROR:</b> Invalid video id",
      note: "Shown when the page URL doesn't match a known video ID. Note for translators: The <b> tag makes the word 'ERROR:' bold. Depending on the grammar of the target language, you might need to move it so that the correct word is emphasized."
    },
    unknownErrorHeading2: {
      title: "Duck Player can\u2019t load this video",
      note: "Message shown when YouTube has blocked playback of a video"
    },
    unknownErrorMessage2a: {
      title: "This video can\u2019t be viewed outside of YouTube.",
      note: "Explanation on why the error is happening."
    },
    unknownErrorMessage2b: {
      title: "You can still watch this video on YouTube, but without the added privacy of Duck Player.",
      note: "A message explaining that the blocked video can be watched directly on YouTube."
    },
    ageRestrictedErrorHeading2: {
      title: "Sorry, this video is age-restricted",
      note: "Message shown when YouTube has blocked playback of a video"
    },
    ageRestrictedErrorMessage2a: {
      title: "To watch age-restricted videos, you need to sign in to YouTube to verify your age.",
      note: "Explanation on why the error is happening."
    },
    ageRestrictedErrorMessage2b: {
      title: "You can still watch this video, but you\u2019ll have to sign in and watch it on YouTube without the added privacy of Duck Player.",
      note: "A message explaining that the blocked video can be watched directly on YouTube."
    },
    noEmbedErrorHeading2: {
      title: "Sorry, this video can only be played on YouTube",
      note: "Message shown when YouTube has blocked playback of a video"
    },
    noEmbedErrorMessage2a: {
      title: "The creator of this video has chosen not to allow it to be viewed on other sites.",
      note: "Explanation on why the error is happening."
    },
    noEmbedErrorMessage2b: {
      title: "You can still watch it on YouTube, but without the added privacy of Duck Player.",
      note: "A message explaining that the blocked video can be watched directly on YouTube."
    },
    blockedVideoErrorHeading: {
      title: "YouTube won\u2019t let Duck Player load this video",
      note: "Message shown when YouTube has blocked playback of a video"
    },
    blockedVideoErrorMessage1: {
      title: "YouTube doesn\u2019t allow this video to be viewed outside of YouTube.",
      note: "Explanation on why the error is happening."
    },
    blockedVideoErrorMessage2: {
      title: "You can still watch this video on YouTube, but without the added privacy of Duck Player.",
      note: "A message explaining that the blocked video can be watched directly on YouTube."
    },
    signInRequiredErrorHeading2: {
      title: "Sorry, YouTube thinks you\u2019re a bot",
      note: "Message shown when YouTube has blocked playback of a video"
    },
    signInRequiredErrorMessage1: {
      title: "YouTube is blocking this video from loading. If you\u2019re using a VPN, try turning it off and reloading this page.",
      note: "Explanation on why the error is happening and a suggestions on how to solve it."
    },
    signInRequiredErrorMessage2: {
      title: "If this doesn\u2019t work, you can still watch this video on YouTube, but without the added privacy of Duck Player.",
      note: "More troubleshooting tips for this specific error"
    },
    signInRequiredErrorMessage2a: {
      title: "This can happen if you\u2019re using a VPN. Try turning the VPN off or switching server locations and reloading this page.",
      note: "Explanation on why the error is happening and a suggestions on how to solve it."
    },
    signInRequiredErrorMessage2b: {
      title: "If that doesn\u2019t work, you\u2019ll have to sign in and watch this video on YouTube without the added privacy of Duck Player.",
      note: "More troubleshooting tips for this specific error"
    },
    tooltipInfo: {
      title: "Duck Player provides a clean viewing experience without personalized ads and prevents viewing activity from influencing your YouTube recommendations."
    }
  };

  // pages/duckplayer/app/settings.js
  var DEFAULT_SIGN_IN_REQURED_HREF = '[href*="//support.google.com/youtube/answer/3037019"]';
  var Settings = class _Settings {
    /**
     * @param {object} params
     * @param {{name: ImportMeta['platform']}} [params.platform]
     * @param {{state: 'enabled' | 'disabled'}} [params.pip]
     * @param {{state: 'enabled' | 'disabled'}} [params.autoplay]
     * @param {{state: 'enabled' | 'disabled'}} [params.focusMode]
     * @param {import("../types/duckplayer.js").DuckPlayerPageSettings['customError']} [params.customError]
     */
    constructor({
      platform = { name: "macos" },
      pip = { state: "disabled" },
      autoplay = { state: "enabled" },
      focusMode = { state: "enabled" },
      customError = { state: "disabled", settings: {}, signInRequiredSelector: "" }
    }) {
      this.platform = platform;
      this.pip = pip;
      this.autoplay = autoplay;
      this.focusMode = focusMode;
      this.customError = this.parseLegacyCustomError(customError);
    }
    /**
     * Parses custom error settings so that both old and new schemas are accepted.
     *
     * Old schema:
     * {
     *   state: "enabled",
     *   signInRequiredSelector: "div"
     * }
     *
     * New schema:
     * {
     *   state: "disabled",
     *   settings: {
     *     signInRequiredSelector: "div"
     *   }
     * }
     *
     * @param {import("../types/duckplayer.js").DuckPlayerPageSettings['customError']} initialSettings
     * @return {import("../types/duckplayer.js").CustomErrorSettings}
     */
    parseLegacyCustomError(initialSettings) {
      if (initialSettings?.state !== "enabled") {
        return { state: "disabled" };
      }
      const { settings, signInRequiredSelector } = initialSettings;
      return {
        state: "enabled",
        settings: {
          ...settings,
          ...signInRequiredSelector && { signInRequiredSelector }
        }
      };
    }
    /**
     * @param {keyof import("../types/duckplayer.js").DuckPlayerPageSettings} named
     * @param {{state: 'enabled' | 'disabled'} | null | undefined} settings
     * @return {Settings}
     */
    withFeatureState(named, settings) {
      if (!settings) return this;
      const valid = ["pip", "autoplay", "focusMode", "customError"];
      if (!valid.includes(named)) {
        console.warn(`Excluding invalid feature key ${named}`);
        return this;
      }
      if (settings.state === "enabled" || settings.state === "disabled") {
        return new _Settings({
          ...this,
          [named]: settings
        });
      }
      return this;
    }
    withPlatformName(name) {
      const valid = ["windows", "macos", "ios", "android"];
      if (valid.includes(
        /** @type {any} */
        name
      )) {
        return new _Settings({
          ...this,
          platform: { name }
        });
      }
      return this;
    }
    /**
     * @param {string|null|undefined} newState
     * @return {Settings}
     */
    withDisabledFocusMode(newState) {
      if (newState === "disabled" || newState === "enabled") {
        return new _Settings({
          ...this,
          focusMode: { state: newState }
        });
      }
      return this;
    }
    /**
     * @param {string|null|undefined} newState
     * @return {Settings}
     */
    withCustomError(newState) {
      if (newState === "disabled") {
        return new _Settings({
          ...this,
          customError: { state: "disabled" }
        });
      }
      if (newState === "enabled") {
        return new _Settings({
          ...this,
          customError: {
            state: "enabled",
            signOnRequiredSelector: DEFAULT_SIGN_IN_REQURED_HREF
          }
        });
      }
      return this;
    }
    /**
     * @return {string}
     */
    get youtubeBase() {
      switch (this.platform.name) {
        case "windows":
        case "ios":
        case "android": {
          return "duck://player/openInYoutube";
        }
        case "macos": {
          return "https://www.youtube.com/watch";
        }
        default:
          throw new Error("unreachable");
      }
    }
    /**
     * @return {'desktop' | 'mobile'}
     */
    get layout() {
      switch (this.platform.name) {
        case "windows":
        case "macos": {
          return "desktop";
        }
        case "ios":
        case "android": {
          return "mobile";
        }
        default:
          return "desktop";
      }
    }
  };

  // pages/duckplayer/app/types.js
  function useTypedTranslation() {
    return {
      t: x2(TranslationContext).t
    };
  }
  var MessagingContext2 = K(
    /** @type {import("../src/index.js").DuckplayerPage} */
    {}
  );
  var useMessaging = () => x2(MessagingContext2);
  var TelemetryContext = K(
    /** @type {import("../src/index.js").Telemetry} */
    {}
  );
  var useTelemetry = () => x2(TelemetryContext);

  // pages/duckplayer/app/providers/SettingsProvider.jsx
  var SettingsContext = K(
    /** @type {{settings: Settings}} */
    {}
  );
  function SettingsProvider({ settings, children }) {
    return /* @__PURE__ */ _(SettingsContext.Provider, { value: { settings } }, children);
  }
  function usePlatformName() {
    return x2(SettingsContext).settings.platform.name;
  }
  function useOpenSettingsHandler() {
    const settings = x2(SettingsContext).settings;
    const messaging2 = useMessaging();
    return () => {
      switch (settings.platform.name) {
        case "ios":
        case "android": {
          messaging2.openSettings();
          break;
        }
        default: {
          console.warn("unreachable!");
        }
      }
    };
  }
  function useSettingsUrl() {
    return "duck://settings/duckplayer";
  }
  function useSettings() {
    return x2(SettingsContext).settings;
  }
  function useOpenInfoHandler() {
    const settings = x2(SettingsContext).settings;
    const messaging2 = useMessaging();
    return () => {
      switch (settings.platform.name) {
        case "android":
        case "ios": {
          messaging2.openInfo();
          break;
        }
        default: {
          console.warn("unreachable!");
        }
      }
    };
  }
  function useOpenOnYoutubeHandler() {
    const settings = x2(SettingsContext).settings;
    return (embed) => {
      if (!embed) return console.warn("unreachable, settings.embed must be present");
      try {
        const base = new URL(settings.youtubeBase);
        window.location.href = embed.intoYoutubeUrl(base);
      } catch (e3) {
        console.error("could not form a URL to open in Youtube", e3);
      }
    };
  }

  // pages/duckplayer/app/providers/UserValuesProvider.jsx
  var UserValuesContext = K({
    /** @type {UserValues} */
    value: {
      privatePlayerMode: { alwaysAsk: {} },
      overlayInteracted: false
    },
    /**
     * @type {() => void}
     */
    setEnabled: () => {
    }
  });
  function UserValuesProvider({ initial, children }) {
    const [value, setValue] = d2(initial);
    const messaging2 = useMessaging();
    y2(() => {
      window.addEventListener("toggle-user-values-enabled", () => {
        setValue({ privatePlayerMode: { enabled: {} }, overlayInteracted: false });
      });
      window.addEventListener("toggle-user-values-ask", () => {
        setValue({ privatePlayerMode: { alwaysAsk: {} }, overlayInteracted: false });
      });
      const unsubscribe = messaging2.onUserValuesChanged((userValues) => {
        setValue(userValues);
      });
      return () => unsubscribe();
    }, [messaging2]);
    function setEnabled() {
      const values = {
        privatePlayerMode: { enabled: {} },
        overlayInteracted: false
      };
      messaging2.setUserValues(values).then((next) => {
        console.log("response after setUserValues...", next);
        console.log("will set", values);
        setValue(values);
      }).catch((err) => {
        console.error("could not set the enabled flag", err);
        messaging2.reportPageException({ message: "could not set the enabled flag: " + err.toString() });
      });
    }
    return /* @__PURE__ */ _(UserValuesContext.Provider, { value: { value, setEnabled } }, children);
  }
  function useUserValues() {
    return x2(UserValuesContext).value;
  }
  function useSetEnabled() {
    return x2(UserValuesContext).setEnabled;
  }

  // shared/components/Fallback/Fallback.module.css
  var Fallback_default = {
    fallback: "Fallback_fallback"
  };

  // shared/components/Fallback/Fallback.jsx
  function Fallback({ showDetails, children }) {
    return /* @__PURE__ */ _("div", { class: Fallback_default.fallback }, /* @__PURE__ */ _("div", null, /* @__PURE__ */ _("p", null, "Something went wrong!"), children, showDetails && /* @__PURE__ */ _("p", null, "Please check logs for a message called ", /* @__PURE__ */ _("code", null, "reportPageException"))));
  }

  // pages/duckplayer/app/components/Components.module.css
  var Components_default = {
    main: "Components_main",
    tube: "Components_tube"
  };

  // pages/duckplayer/app/components/PlayerContainer.jsx
  var import_classnames = __toESM(require_classnames(), 1);

  // pages/duckplayer/app/components/PlayerContainer.module.css
  var PlayerContainer_default = {
    container: "PlayerContainer_container",
    inset: "PlayerContainer_inset",
    internals: "PlayerContainer_internals",
    insetInternals: "PlayerContainer_insetInternals"
  };

  // pages/duckplayer/app/components/PlayerContainer.jsx
  function PlayerContainer({ children, inset }) {
    return /* @__PURE__ */ _(
      "div",
      {
        class: (0, import_classnames.default)(PlayerContainer_default.container, {
          [PlayerContainer_default.inset]: inset
        })
      },
      children
    );
  }
  function PlayerInternal({ children, inset }) {
    return /* @__PURE__ */ _("div", { class: (0, import_classnames.default)(PlayerContainer_default.internals, { [PlayerContainer_default.insetInternals]: inset }) }, children);
  }

  // pages/duckplayer/app/img/info.data.svg
  var info_data_default = 'data:image/svg+xml,<svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">%0A    <path d="M10.604 4.55627C9.67439 4.55627 9.10934 5.3036 9.10934 5.99623C9.10934 6.83469 9.7473 7.1081 10.3123 7.1081C11.3513 7.1081 11.7888 6.32432 11.7888 5.68637C11.7888 4.88437 11.1508 4.55627 10.604 4.55627Z" fill="white"/>%0A    <path d="M11.1413 8.18192L8.85806 8.55106C8.78817 9.10365 8.68764 9.66256 8.58552 10.2303C8.38843 11.326 8.18542 12.4546 8.18542 13.6339C8.18542 14.8049 8.88572 15.444 9.99318 15.444C11.258 15.444 11.4745 14.6501 11.5235 13.9309C10.475 14.083 10.2447 13.6097 10.416 12.4959C10.5874 11.382 11.1413 8.18192 11.1413 8.18192Z" fill="white"/>%0A    <path fill-rule="evenodd" clip-rule="evenodd" d="M10 0C4.47715 0 0 4.47715 0 10C0 15.5228 4.47715 20 10 20C15.5228 20 20 15.5228 20 10C20 4.47715 15.5228 0 10 0ZM1.875 10C1.875 5.51269 5.51269 1.875 10 1.875C14.4873 1.875 18.125 5.51269 18.125 10C18.125 14.4873 14.4873 18.125 10 18.125C5.51269 18.125 1.875 14.4873 1.875 10Z" fill="white"/>%0A</svg>%0A';

  // pages/duckplayer/app/img/cog.data.svg
  var cog_data_default = 'data:image/svg+xml,<svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">%0A    <path fill-rule="evenodd" clip-rule="evenodd" d="M10 5.625C12.4162 5.625 14.375 7.58375 14.375 10C14.375 12.4162 12.4162 14.375 10 14.375C7.58375 14.375 5.625 12.4162 5.625 10C5.625 7.58375 7.58375 5.625 10 5.625ZM12.5 10C12.5 8.61929 11.3807 7.5 10 7.5C8.61929 7.5 7.5 8.61929 7.5 10C7.5 11.3807 8.61929 12.5 10 12.5C11.3807 12.5 12.5 11.3807 12.5 10Z" fill="white"/>%0A    <path fill-rule="evenodd" clip-rule="evenodd" d="M10.625 0C11.5695 0 12.3509 0.698404 12.481 1.60697C13.0763 1.7827 13.6451 2.01996 14.1799 2.31125C14.9143 1.76035 15.9611 1.81892 16.6291 2.48696L17.513 3.37084C18.181 4.03887 18.2396 5.08556 17.6887 5.82C17.98 6.35484 18.2173 6.92365 18.393 7.51901C19.3016 7.64906 20 8.43047 20 9.375V10.625C20 11.5695 19.3016 12.3509 18.393 12.481C18.2173 13.0763 17.98 13.6451 17.6887 14.1799C18.2396 14.9143 18.1811 15.9611 17.513 16.6291L16.6292 17.513C15.9611 18.181 14.9144 18.2396 14.18 17.6887C13.6451 17.98 13.0763 18.2173 12.481 18.393C12.3509 19.3016 11.5695 20 10.625 20H9.375C8.43047 20 7.64906 19.3016 7.51901 18.393C6.92365 18.2173 6.35484 17.98 5.82 17.6887C5.08557 18.2396 4.03887 18.181 3.37084 17.513L2.48695 16.6291C1.81892 15.9611 1.76035 14.9143 2.31125 14.1799C2.01996 13.6451 1.7827 13.0763 1.60697 12.481C0.698404 12.3509 0 11.5695 0 10.625V9.375C0 8.43047 0.698404 7.64906 1.60697 7.51901C1.78271 6.92365 2.01998 6.35484 2.3113 5.82C1.76042 5.08557 1.81899 4.03887 2.48702 3.37084L3.37091 2.48695C4.03894 1.81892 5.08566 1.76035 5.82009 2.31125C6.35491 2.01996 6.92368 1.7827 7.51901 1.60697C7.64906 0.698403 8.43047 0 9.375 0H10.625ZM10.625 1.875H9.375C9.375 2.60562 8.86376 3.1857 8.2228 3.35667C7.63403 3.51372 7.07618 3.7471 6.5604 4.04579C5.98574 4.37857 5.21357 4.32962 4.69673 3.81278L3.81285 4.69666C4.32968 5.21349 4.37863 5.98566 4.04584 6.56031C3.74713 7.07612 3.51373 7.634 3.35667 8.2228C3.1857 8.86377 2.60562 9.375 1.875 9.375V10.625C2.60562 10.625 3.1857 11.1362 3.35667 11.7772C3.51372 12.366 3.7471 12.9238 4.04579 13.4396C4.37857 14.0143 4.32962 14.7864 3.81278 15.3033L4.69666 16.1872C5.21349 15.6703 5.98566 15.6214 6.56031 15.9542C7.07612 16.2529 7.634 16.4863 8.2228 16.6433C8.86376 16.8143 9.375 17.3944 9.375 18.125H10.625V17.6562C10.625 17.2103 10.939 16.8262 11.376 16.7375C12.1135 16.5878 12.8082 16.3199 13.4397 15.9542C14.0143 15.6214 14.7865 15.6703 15.3033 16.1871L16.1872 15.3033C15.6704 14.7864 15.6214 14.0143 15.9542 13.4396C16.2529 12.9238 16.4863 12.366 16.6433 11.7772C16.8143 11.1362 17.3944 10.625 18.125 10.625V9.375C17.3944 9.375 16.8143 8.86376 16.6433 8.2228C16.4863 7.63399 16.2529 7.07611 15.9542 6.5603C15.6214 5.98566 15.6703 5.2135 16.1871 4.69667L15.3033 3.81278C14.7864 4.32962 14.0143 4.37857 13.4396 4.04579C12.9238 3.7471 12.366 3.51372 11.7772 3.35667C11.1362 3.1857 10.625 2.60562 10.625 1.875Z" fill="white"/>%0A</svg>%0A';

  // pages/duckplayer/app/components/Button.jsx
  var import_classnames2 = __toESM(require_classnames(), 1);

  // pages/duckplayer/app/components/Button.module.css
  var Button_default = {
    button: "Button_button",
    fill: "Button_fill",
    desktop: "Button_desktop",
    icon: "Button_icon",
    iconOnly: "Button_iconOnly",
    highlight: "Button_highlight",
    accent: "Button_accent",
    svgIcon: "Button_svgIcon"
  };

  // pages/duckplayer/app/components/Button.jsx
  function Button({
    children,
    formfactor = "mobile",
    variant = "standard",
    icon = false,
    fill = false,
    highlight = false,
    buttonProps = {}
  }) {
    const classes = (0, import_classnames2.default)({
      [Button_default.button]: true,
      [Button_default.desktop]: formfactor === "desktop",
      [Button_default.highlight]: highlight === true,
      [Button_default.accent]: variant === "accent",
      [Button_default.fill]: fill === true,
      [Button_default.iconOnly]: icon === true
    });
    return /* @__PURE__ */ _("button", { class: classes, type: "button", ...buttonProps }, children);
  }
  function ButtonLink({ children, formfactor = "mobile", icon = false, fill = false, highlight = false, anchorProps = {} }) {
    const classes = (0, import_classnames2.default)({
      [Button_default.button]: true,
      [Button_default.desktop]: formfactor === "desktop",
      [Button_default.highlight]: highlight === true,
      [Button_default.fill]: fill === true,
      [Button_default.iconOnly]: icon === true
    });
    return /* @__PURE__ */ _("a", { class: classes, type: "button", ...anchorProps }, children);
  }
  function Icon({ src }) {
    return /* @__PURE__ */ _("span", { class: Button_default.icon }, /* @__PURE__ */ _("img", { src, alt: "" }));
  }
  function OpenInIcon() {
    return /* @__PURE__ */ _("svg", { fill: "none", viewBox: "0 0 16 16", xmlns: "http://www.w3.org/2000/svg", className: Button_default.svgIcon }, /* @__PURE__ */ _(
      "path",
      {
        fill: "currentColor",
        d: "M7.361 1.013a.626.626 0 0 1 0 1.224l-.126.013H5A2.75 2.75 0 0 0 2.25 5v6A2.75 2.75 0 0 0 5 13.75h6A2.75 2.75 0 0 0 13.75 11V8.765a.625.625 0 0 1 1.25 0V11a4 4 0 0 1-4 4H5a4 4 0 0 1-4-4V5a4 4 0 0 1 4-4h2.235l.126.013Z"
      }
    ), /* @__PURE__ */ _(
      "path",
      {
        fill: "currentColor",
        d: "M12.875 1C14.049 1 15 1.951 15 3.125v2.25a.625.625 0 1 1-1.25 0v-2.24L9.067 7.817a.626.626 0 0 1-.884-.884l4.682-4.683h-2.24a.625.625 0 1 1 0-1.25h2.25Z"
      }
    ));
  }

  // pages/duckplayer/app/components/FloatingBar.module.css
  var FloatingBar_default = {
    floatingBar: "FloatingBar_floatingBar",
    inset: "FloatingBar_inset",
    topBar: "FloatingBar_topBar"
  };

  // pages/duckplayer/app/components/FloatingBar.jsx
  var import_classnames3 = __toESM(require_classnames(), 1);
  function FloatingBar({ children, inset = false }) {
    return /* @__PURE__ */ _("div", { class: (0, import_classnames3.default)(FloatingBar_default.floatingBar, { [FloatingBar_default.inset]: inset }) }, children);
  }

  // pages/duckplayer/app/components/SwitchBarMobile.jsx
  var import_classnames5 = __toESM(require_classnames(), 1);

  // pages/duckplayer/app/components/SwitchBarMobile.module.css
  var SwitchBarMobile_default = {
    switchBar: "SwitchBarMobile_switchBar",
    stateExiting: "SwitchBarMobile_stateExiting",
    stateHidden: "SwitchBarMobile_stateHidden",
    labelRow: "SwitchBarMobile_labelRow",
    label: "SwitchBarMobile_label",
    checkbox: "SwitchBarMobile_checkbox",
    text: "SwitchBarMobile_text",
    placeholder: "SwitchBarMobile_placeholder"
  };

  // pages/duckplayer/app/providers/SwitchProvider.jsx
  var SwitchContext = K({
    /** @type {SwitchState} */
    state: "showing",
    /** @type {() => void} */
    onChange: () => {
      throw new Error("must implement");
    },
    /** @type {() => void} */
    onDone: () => {
      throw new Error("must implement");
    }
  });
  function SwitchProvider({ children }) {
    const userValues = useUserValues();
    const setEnabled = useSetEnabled();
    const initialState = "enabled" in userValues.privatePlayerMode ? "completed" : "showing";
    const [state, dispatch] = h2((state2, event) => {
      console.log("\u{1F4E9}", { state: state2, event });
      switch (state2) {
        case "showing": {
          if (event === "change") {
            return "exiting";
          }
          if (event === "enabled") {
            return "completed";
          }
          if (event === "done") {
            return "completed";
          }
          break;
        }
        case "exiting": {
          if (event === "done") {
            return "completed";
          }
          break;
        }
        case "completed": {
          if (event === "ask") {
            return "showing";
          }
        }
      }
      return state2;
    }, initialState);
    function onChange() {
      dispatch("change");
      setEnabled();
    }
    y2(() => {
      const evt = "enabled" in userValues.privatePlayerMode ? "enabled" : "ask";
      dispatch(evt);
    }, [initialState]);
    function onDone() {
      dispatch("done");
    }
    return /* @__PURE__ */ _(SwitchContext.Provider, { value: { state, onChange, onDone } }, children);
  }

  // pages/duckplayer/app/components/Switch.module.css
  var Switch_default = {
    switch: "Switch_switch",
    thumb: "Switch_thumb",
    ios: "Switch_ios"
  };

  // pages/duckplayer/app/components/Switch.jsx
  var import_classnames4 = __toESM(require_classnames(), 1);
  function Switch({ checked, onChange, id, platformName = "ios" }) {
    return /* @__PURE__ */ _(
      "button",
      {
        role: "switch",
        "aria-checked": checked,
        onClick: onChange,
        id,
        className: (0, import_classnames4.default)(Switch_default.switch, {
          [Switch_default.ios]: platformName === "ios",
          [Switch_default.android]: platformName === "android"
        })
      },
      /* @__PURE__ */ _("span", { className: Switch_default.thumb })
    );
  }

  // pages/duckplayer/app/components/SwitchBarMobile.jsx
  function SwitchBarMobile({ platformName }) {
    const { onChange, onDone, state } = x2(SwitchContext);
    const { t: t3 } = useTypedTranslation();
    const inputId = g2();
    function blockClick(e3) {
      if (state === "exiting") {
        return e3.preventDefault();
      }
    }
    function onTransitionEnd(e3) {
      if (e3.target?.dataset?.state === "exiting") {
        onDone();
      }
    }
    const classes = (0, import_classnames5.default)({
      [SwitchBarMobile_default.switchBar]: true,
      [SwitchBarMobile_default.stateExiting]: state === "exiting",
      [SwitchBarMobile_default.stateHidden]: state === "completed"
    });
    return /* @__PURE__ */ _("div", { class: classes, "data-state": state, "data-allow-animation": "true", onTransitionEnd }, /* @__PURE__ */ _("div", { class: SwitchBarMobile_default.labelRow, onClick: blockClick }, /* @__PURE__ */ _("label", { for: inputId, class: SwitchBarMobile_default.label }, /* @__PURE__ */ _("span", { className: SwitchBarMobile_default.text }, t3("keepEnabled"))), /* @__PURE__ */ _(Switch, { checked: state !== "showing", onChange, platformName, id: inputId })));
  }

  // pages/duckplayer/app/components/InfoBar.module.css
  var InfoBar_default = {
    infoBar: "InfoBar_infoBar",
    container: "InfoBar_container",
    dax: "InfoBar_dax",
    img: "InfoBar_img",
    text: "InfoBar_text",
    info: "InfoBar_info",
    lhs: "InfoBar_lhs",
    rhs: "InfoBar_rhs",
    switch: "InfoBar_switch",
    controls: "InfoBar_controls"
  };

  // pages/duckplayer/app/img/dax.data.svg
  var dax_data_default = 'data:image/svg+xml,<svg fill="none" viewBox="0 0 128 128" xmlns="http://www.w3.org/2000/svg">%0A    <path fill="%23DE5833" fill-rule="evenodd" d="M64 128c35.346 0 64-28.654 64-64 0-35.346-28.654-64-64-64C28.654 0 0 28.654 0 64c0 35.346 28.654 64 64 64Z" clip-rule="evenodd"/>%0A    <path fill="%23DDD" fill-rule="evenodd" d="M73 111.75c0-.5.123-.614-1.466-3.782-4.224-8.459-8.47-20.384-6.54-28.075.353-1.397-3.978-51.744-7.04-53.365-3.402-1.813-7.588-4.69-11.418-5.33-1.943-.31-4.49-.163-6.482.105-.353.047-.368.683-.03.798 1.308.443 2.895 1.212 3.83 2.375.178.22-.06.566-.342.577-.882.032-2.482.402-4.593 2.195-.244.207-.041.592.273.53 4.536-.897 9.17-.455 11.9 2.027.177.16.084.45-.147.512-23.694 6.44-19.003 27.05-12.696 52.344 5.619 22.53 7.733 29.792 8.4 32.004a.718.718 0 0 0 .423.467C55.228 118.38 73 118.524 73 113v-1.25Z" clip-rule="evenodd"/>%0A    <path fill="%23fff" fill-rule="evenodd" d="M122.75 64c0 32.447-26.303 58.75-58.75 58.75S5.25 96.447 5.25 64 31.553 5.25 64 5.25 122.75 31.553 122.75 64Zm-72.46 51.986c-1.624-5.016-6.161-19.551-10.643-37.92l-.447-1.828-.003-.016c-5.425-22.155-9.855-40.252 14.427-45.937.222-.052.33-.317.183-.492-2.786-3.305-8.005-4.388-14.604-2.111-.27.093-.506-.18-.338-.412 1.294-1.784 3.823-3.155 5.072-3.756.258-.124.242-.502-.031-.588a27.848 27.848 0 0 0-3.771-.9c-.37-.059-.404-.693-.032-.743 9.356-1.259 19.125 1.55 24.028 7.726a.325.325 0 0 0 .185.114c17.953 3.855 19.239 32.235 17.17 33.528-.407.255-1.714.108-3.438-.085-6.985-.781-20.818-2.329-9.401 18.948.113.21-.037.488-.272.525-6.416.997 1.755 21.034 7.812 34.323 23.815-5.52 41.563-26.868 41.563-52.362 0-29.685-24.065-53.75-53.75-53.75S10.25 34.315 10.25 64c0 24.947 16.995 45.924 40.04 51.986Z" clip-rule="evenodd"/>%0A    <path fill="%233CA82B" d="M84.28 90.698c-1.366-.633-6.621 3.135-10.109 6.028-.729-1.031-2.103-1.78-5.204-1.242-2.713.472-4.21 1.126-4.88 2.254-4.282-1.623-11.487-4.13-13.228-1.71-1.903 2.646.475 15.161 3.003 16.786 1.319.849 7.63-3.208 10.926-6.005.532.749 1.388 1.178 3.148 1.137 2.661-.062 6.978-.681 7.648-1.921.04-.075.076-.164.106-.266 3.387 1.266 9.349 2.606 10.681 2.406 3.47-.521-.483-16.723-2.09-17.467Z"/>%0A    <path fill="%234CBA3C" d="M74.49 97.097c.144.256.26.526.358.8.483 1.352 1.27 5.648.674 6.709-.595 1.062-4.459 1.574-6.843 1.615-2.384.041-2.92-.831-3.403-2.181-.387-1.081-.577-3.621-.572-5.075-.098-2.158.69-2.916 4.334-3.506 2.696-.436 4.121.071 4.944.94 3.828-2.857 10.215-6.889 10.838-6.152 3.106 3.674 3.499 12.42 2.826 15.939-.22 1.151-10.505-1.139-10.505-2.38 0-5.152-1.337-6.565-2.65-6.71Zm-22.53-1.609c.843-1.333 7.674.325 11.424 1.993 0 0-.77 3.491.456 7.604.359 1.203-8.627 6.558-9.8 5.637-1.355-1.065-3.85-12.432-2.08-15.234Z"/>%0A    <path fill="%23FC3" fill-rule="evenodd" d="M55.269 68.407c.553-2.404 3.127-6.932 12.321-6.823 4.648-.019 10.422-.002 14.25-.436a51.312 51.312 0 0 0 12.726-3.095c3.98-1.519 5.392-1.18 5.887-.272.544.999-.097 2.722-1.488 4.31-2.656 3.03-7.431 5.378-15.865 6.075-8.433.698-14.02-1.565-16.425 2.118-1.038 1.589-.236 5.333 7.92 6.512 11.02 1.59 20.072-1.917 21.19.201 1.119 2.119-5.323 6.428-16.362 6.518-11.039.09-17.934-3.865-20.379-5.83-3.102-2.495-4.49-6.133-3.775-9.278Z" clip-rule="evenodd"/>%0A    <g fill="%2314307E" opacity=".8">%0A        <path d="M69.327 42.127c.616-1.008 1.981-1.786 4.216-1.786 2.234 0 3.285.889 4.013 1.88.148.202-.076.44-.306.34a59.869 59.869 0 0 1-.168-.073c-.817-.357-1.82-.795-3.54-.82-1.838-.026-2.997.435-3.727.831-.246.134-.634-.133-.488-.372Zm-25.157 1.29c2.17-.907 3.876-.79 5.081-.504.254.06.43-.213.227-.377-.935-.755-3.03-1.692-5.76-.674-2.437.909-3.585 2.796-3.592 4.038-.002.292.6.317.756.07.42-.67 1.12-1.646 3.289-2.553Z"/>%0A        <path fill-rule="evenodd" d="M75.44 55.92a3.47 3.47 0 0 1-3.474-3.462 3.47 3.47 0 0 1 3.475-3.46 3.47 3.47 0 0 1 3.474 3.46 3.47 3.47 0 0 1-3.475 3.462Zm2.447-4.608a.899.899 0 0 0-1.799 0c0 .494.405.895.9.895.499 0 .9-.4.9-.895Zm-25.464 3.542a4.042 4.042 0 0 1-4.049 4.037 4.045 4.045 0 0 1-4.05-4.037 4.045 4.045 0 0 1 4.05-4.037 4.045 4.045 0 0 1 4.05 4.037Zm-1.193-1.338a1.05 1.05 0 0 0-2.097 0 1.048 1.048 0 0 0 2.097 0Z" clip-rule="evenodd"/>%0A    </g>%0A</svg>%0A';

  // pages/duckplayer/app/components/SwitchBarDesktop.module.css
  var SwitchBarDesktop_default = {
    switchBarDesktop: "SwitchBarDesktop_switchBarDesktop",
    stateCompleted: "SwitchBarDesktop_stateCompleted",
    stateExiting: "SwitchBarDesktop_stateExiting",
    label: "SwitchBarDesktop_label",
    "slide-out": "SwitchBarDesktop_slide-out",
    checkbox: "SwitchBarDesktop_checkbox",
    input: "SwitchBarDesktop_input",
    text: "SwitchBarDesktop_text"
  };

  // pages/duckplayer/app/components/SwitchBarDesktop.jsx
  var import_classnames6 = __toESM(require_classnames(), 1);
  function SwitchBarDesktop() {
    const { onChange, onDone, state } = x2(SwitchContext);
    const { t: t3 } = useTypedTranslation();
    function blockClick(e3) {
      if (state === "exiting") {
        return e3.preventDefault();
      }
    }
    const classes = (0, import_classnames6.default)({
      [SwitchBarDesktop_default.switchBarDesktop]: true,
      [SwitchBarDesktop_default.stateExiting]: state === "exiting",
      [SwitchBarDesktop_default.stateCompleted]: state === "completed"
    });
    return /* @__PURE__ */ _("div", { class: classes, "data-state": state, "data-allow-animation": true, onTransitionEnd: onDone }, /* @__PURE__ */ _("label", { class: SwitchBarDesktop_default.label, onClick: blockClick }, /* @__PURE__ */ _("span", { class: SwitchBarDesktop_default.checkbox }, /* @__PURE__ */ _("input", { class: SwitchBarDesktop_default.input, onChange, name: "enabled", type: "checkbox", checked: state !== "showing" })), /* @__PURE__ */ _("span", { class: SwitchBarDesktop_default.text }, t3("alwaysWatchHere"))));
  }

  // pages/duckplayer/app/components/Tooltip.jsx
  var import_classnames7 = __toESM(require_classnames(), 1);

  // pages/duckplayer/app/components/Tooltip.module.css
  var Tooltip_default = {
    tooltip: "Tooltip_tooltip",
    top: "Tooltip_top",
    bottom: "Tooltip_bottom",
    visible: "Tooltip_visible"
  };

  // pages/duckplayer/app/components/Tooltip.jsx
  function Tooltip({ id, isVisible, position }) {
    const { t: t3 } = useTypedTranslation();
    return /* @__PURE__ */ _(
      "div",
      {
        class: (0, import_classnames7.default)(Tooltip_default.tooltip, {
          [Tooltip_default.top]: position === "top",
          [Tooltip_default.bottom]: position === "bottom"
        }),
        role: "tooltip",
        "aria-hidden": !isVisible,
        id
      },
      t3("tooltipInfo")
    );
  }

  // pages/duckplayer/app/components/FocusMode.jsx
  var import_classnames8 = __toESM(require_classnames(), 1);

  // pages/duckplayer/app/components/FocusMode.module.css
  var FocusMode_default = {
    fade: "FocusMode_fade",
    slide: "FocusMode_slide",
    hideInFocus: "FocusMode_hideInFocus"
  };

  // pages/duckplayer/app/components/FocusMode.jsx
  var EVENT_ON = "ddg-duckplayer-focusmode-on";
  var EVENT_OFF = "ddg-duckplayer-focusmode-off";
  function FocusMode() {
    y2(() => {
      let enabled = true;
      let timerId;
      const on = () => {
        if (document.documentElement.dataset.focusModeState === "paused") {
          wait();
        } else {
          if (!enabled) {
            return console.warn("ignoring focusMode because it was disabled");
          }
          document.documentElement.dataset.focusMode = "on";
        }
      };
      const off = () => document.documentElement.dataset.focusMode = "off";
      const cancel = () => {
        clearTimeout(timerId);
        off();
        wait();
      };
      const wait = () => {
        clearTimeout(timerId);
        timerId = setTimeout(on, 2e3);
      };
      wait();
      document.addEventListener("mousemove", cancel);
      document.addEventListener("pointerdown", cancel);
      window.addEventListener("frame-mousemove", cancel);
      window.addEventListener(EVENT_OFF, () => {
        enabled = false;
        off();
      });
      window.addEventListener(EVENT_ON, () => {
        if (enabled === true) return;
        enabled = true;
        on();
      });
      return () => {
        clearTimeout(timerId);
      };
    }, []);
    return null;
  }
  FocusMode.disable = () => setTimeout(() => window.dispatchEvent(new Event(EVENT_OFF)), 0);
  FocusMode.enable = () => setTimeout(() => window.dispatchEvent(new Event(EVENT_ON)), 0);
  function HideInFocusMode({ children, style = "fade" }) {
    const classes = (0, import_classnames8.default)({
      [FocusMode_default.hideInFocus]: true,
      [FocusMode_default.fade]: style === "fade",
      [FocusMode_default.slide]: style === "slide"
    });
    return /* @__PURE__ */ _("div", { class: classes, "data-style": style }, children);
  }
  function useSetFocusMode() {
    return q2((action) => {
      document.documentElement.dataset.focusModeState = action;
    }, []);
  }

  // ../injected/src/features/duckplayer-native/youtube-errors.js
  var YOUTUBE_ERROR_EVENT = "ddg-duckplayer-youtube-error";
  var YOUTUBE_ERRORS = {
    ageRestricted: "age-restricted",
    signInRequired: "sign-in-required",
    noEmbed: "no-embed",
    unknown: "unknown"
  };
  var YOUTUBE_ERROR_IDS = Object.values(YOUTUBE_ERRORS);
  function checkForError(errorSelector, node) {
    if (node?.nodeType === Node.ELEMENT_NODE) {
      const element = (
        /** @type {HTMLElement} */
        node
      );
      const isError = element.matches(errorSelector) || !!element.querySelector(errorSelector);
      return isError;
    }
    return false;
  }
  function getErrorType(windowObject, signInRequiredSelector, logger) {
    const currentWindow = (
      /** @type {Window & typeof globalThis & { ytcfg: object }} */
      windowObject
    );
    const currentDocument = currentWindow.document;
    if (!currentWindow || !currentDocument) {
      logger?.warn("Window or document missing!");
      return YOUTUBE_ERRORS.unknown;
    }
    let playerResponse;
    if (!currentWindow.ytcfg) {
      logger?.warn("ytcfg missing!");
    } else {
      logger?.log("Got ytcfg", currentWindow.ytcfg);
    }
    try {
      const playerResponseJSON = currentWindow.ytcfg?.get("PLAYER_VARS")?.embedded_player_response;
      logger?.log("Player response", playerResponseJSON);
      playerResponse = JSON.parse(playerResponseJSON);
    } catch (e3) {
      logger?.log("Could not parse player response", e3);
    }
    if (typeof playerResponse === "object") {
      const {
        previewPlayabilityStatus: { desktopLegacyAgeGateReason, status }
      } = playerResponse;
      if (status === "UNPLAYABLE") {
        if (desktopLegacyAgeGateReason === 1) {
          logger?.log("AGE RESTRICTED ERROR");
          return YOUTUBE_ERRORS.ageRestricted;
        }
        logger?.log("NO EMBED ERROR");
        return YOUTUBE_ERRORS.noEmbed;
      }
    }
    try {
      if (signInRequiredSelector && !!currentDocument.querySelector(signInRequiredSelector)) {
        logger?.log("SIGN-IN ERROR");
        return YOUTUBE_ERRORS.signInRequired;
      }
    } catch (e3) {
      logger?.log("Sign-in required query failed", e3);
    }
    logger?.log("UNKNOWN ERROR");
    return YOUTUBE_ERRORS.unknown;
  }

  // pages/duckplayer/app/providers/YouTubeErrorProvider.jsx
  var YouTubeErrorContext = K({
    /** @type {YouTubeError|null} */
    error: null,
    /** @type {string} - Enables showing different error messages based on locale */
    locale: "en"
  });
  function YouTubeErrorProvider({ initial = null, locale, children }) {
    let initialError = null;
    if (initial && YOUTUBE_ERROR_IDS.includes(initial)) {
      initialError = initial;
    }
    const [error, setError] = d2(initialError);
    const messaging2 = useMessaging();
    const setFocusMode = useSetFocusMode();
    y2(() => {
      const errorEventHandler = (event) => {
        const eventError = event.detail?.error;
        if (YOUTUBE_ERROR_IDS.includes(eventError) || eventError === null) {
          if (eventError && eventError !== error) {
            setFocusMode("paused");
            messaging2.reportYouTubeError({ error: eventError });
          } else {
            setFocusMode("enabled");
          }
          setError(eventError);
        }
      };
      window.addEventListener(YOUTUBE_ERROR_EVENT, errorEventHandler);
      return () => window.removeEventListener(YOUTUBE_ERROR_EVENT, errorEventHandler);
    }, []);
    return /* @__PURE__ */ _(YouTubeErrorContext.Provider, { value: { error, locale } }, children);
  }
  function useYouTubeError() {
    return x2(YouTubeErrorContext).error;
  }
  function useShowCustomError() {
    const settings = useSettings();
    const youtubeError = x2(YouTubeErrorContext).error;
    return youtubeError !== null && settings.customError?.state === "enabled";
  }

  // pages/duckplayer/app/components/InfoBar.jsx
  function InfoBar({ embed }) {
    const showCustomError = useShowCustomError();
    return /* @__PURE__ */ _("div", { class: InfoBar_default.infoBar }, /* @__PURE__ */ _("div", { class: InfoBar_default.lhs }, /* @__PURE__ */ _("div", { class: InfoBar_default.dax }, /* @__PURE__ */ _("img", { src: dax_data_default, class: InfoBar_default.img })), /* @__PURE__ */ _("div", { class: InfoBar_default.text }, "Duck Player"), /* @__PURE__ */ _(InfoIcon, null)), /* @__PURE__ */ _("div", { class: InfoBar_default.rhs }, /* @__PURE__ */ _(SwitchProvider, null, !showCustomError && /* @__PURE__ */ _("div", { class: InfoBar_default.switch }, /* @__PURE__ */ _(SwitchBarDesktop, null)), /* @__PURE__ */ _(ControlBarDesktop, { embed }))));
  }
  function InfoIcon({ debugStyles = false }) {
    const setFocusMode = useSetFocusMode();
    const [isVisible, setIsVisible] = d2(debugStyles);
    const [isBottom, setIsBottom] = d2(false);
    const tooltipRef = A2(null);
    function show() {
      setIsVisible(true);
      setFocusMode("paused");
    }
    function hide() {
      setIsVisible(false);
      setFocusMode("enabled");
    }
    _2(() => {
      if (!tooltipRef.current) return;
      const icon = tooltipRef.current;
      const rect = icon.getBoundingClientRect();
      const iconTop = rect.top + window.scrollY;
      const spaceBelowIcon = window.innerHeight - iconTop;
      if (spaceBelowIcon < 125) {
        return setIsBottom(false);
      }
      return setIsBottom(true);
    }, [isVisible]);
    return /* @__PURE__ */ _(
      "button",
      {
        className: InfoBar_default.info,
        "aria-describedby": "tooltip1",
        "aria-expanded": isVisible,
        "aria-label": "Info",
        onMouseEnter: show,
        onMouseLeave: hide,
        onFocus: show,
        onBlur: hide,
        ref: tooltipRef
      },
      /* @__PURE__ */ _(Icon, { src: info_data_default }),
      /* @__PURE__ */ _(Tooltip, { id: "tooltip1", isVisible, position: isBottom ? "bottom" : "top" })
    );
  }
  function ControlBarDesktop({ embed }) {
    const settingsUrl = useSettingsUrl();
    const openOnYoutube = useOpenOnYoutubeHandler();
    const showCustomError = useShowCustomError();
    const { t: t3 } = useTypedTranslation();
    const { state } = x2(SwitchContext);
    return /* @__PURE__ */ _("div", { className: InfoBar_default.controls }, /* @__PURE__ */ _(
      ButtonLink,
      {
        formfactor: "desktop",
        icon: true,
        highlight: state === "exiting",
        anchorProps: {
          href: settingsUrl,
          target: "_blank",
          "aria-label": t3("openSettingsButton")
        }
      },
      /* @__PURE__ */ _(Icon, { src: cog_data_default })
    ), !showCustomError && /* @__PURE__ */ _(
      Button,
      {
        formfactor: "desktop",
        buttonProps: {
          onClick: () => {
            if (embed) openOnYoutube(embed);
          }
        }
      },
      t3("watchOnYoutube")
    ));
  }
  function InfoBarContainer({ children }) {
    return /* @__PURE__ */ _("div", { class: InfoBar_default.container }, children);
  }

  // pages/duckplayer/app/components/Wordmark.module.css
  var Wordmark_default = {
    wordmark: "Wordmark_wordmark",
    logo: "Wordmark_logo",
    img: "Wordmark_img",
    text: "Wordmark_text"
  };

  // pages/duckplayer/app/components/Wordmark-mobile.module.css
  var Wordmark_mobile_default = {
    logo: "Wordmark_mobile_logo",
    logoSvg: "Wordmark_mobile_logoSvg",
    text: "Wordmark_mobile_text"
  };

  // pages/duckplayer/app/components/Wordmark.jsx
  function Wordmark() {
    return /* @__PURE__ */ _("div", { class: Wordmark_default.wordmark }, /* @__PURE__ */ _("div", { className: Wordmark_default.logo }, /* @__PURE__ */ _("img", { src: dax_data_default, className: Wordmark_default.img, alt: "DuckDuckGo logo" })), /* @__PURE__ */ _("div", { className: Wordmark_default.text }, "Duck Player"));
  }
  function MobileWordmark() {
    return /* @__PURE__ */ _("div", { class: Wordmark_mobile_default.logo }, /* @__PURE__ */ _("span", { class: Wordmark_mobile_default.logoSvg }, /* @__PURE__ */ _("img", { src: dax_data_default, className: Wordmark_mobile_default.img, alt: "DuckDuckGo logo" })), /* @__PURE__ */ _("span", { class: Wordmark_mobile_default.text }, "Duck Player"));
  }

  // pages/duckplayer/app/components/Player.jsx
  var import_classnames9 = __toESM(require_classnames(), 1);

  // pages/duckplayer/app/components/Player.module.css
  var Player_default = {
    root: "Player_root",
    desktop: "Player_desktop",
    mobile: "Player_mobile",
    player: "Player_player",
    iframe: "Player_iframe",
    error: "Player_error"
  };

  // pages/duckplayer/app/features/pip.js
  var PIP = class {
    /**
     * @param {HTMLIFrameElement} iframe
     */
    iframeDidLoad(iframe) {
      try {
        const iframeDocument = iframe.contentDocument;
        const iframeWindow = iframe.contentWindow;
        if (iframeDocument && iframeWindow) {
          const CSSStyleSheet = (
            /** @type {any} */
            iframeWindow.CSSStyleSheet
          );
          const styleSheet = new CSSStyleSheet();
          styleSheet.replaceSync("button.ytp-pip-button { display: inline-block !important; }");
          iframeDocument.adoptedStyleSheets = [...iframeDocument.adoptedStyleSheets, styleSheet];
        }
      } catch (e3) {
        console.warn(e3);
      }
      return null;
    }
  };

  // pages/duckplayer/app/features/autofocus.js
  var AutoFocus = class {
    /**
     * @param {HTMLIFrameElement} iframe
     */
    iframeDidLoad(iframe) {
      const maxAttempts = 1e3;
      let attempt = 0;
      let id;
      function check() {
        if (!iframe.contentDocument) return;
        if (attempt > maxAttempts) return;
        attempt += 1;
        const selector = "#player video";
        const video = (
          /** @type {HTMLIFrameElement | null} */
          iframe.contentDocument?.body.querySelector(selector)
        );
        if (!video) {
          id = requestAnimationFrame(check);
          return;
        }
        video.focus();
        document.body.dataset.videoState = "loaded+focussed";
      }
      id = requestAnimationFrame(check);
      return () => {
        cancelAnimationFrame(id);
      };
    }
  };

  // ../injected/src/features/duckplayer/util.js
  var _VideoParams = class _VideoParams {
    /**
     * @param {string} id - the YouTube video ID
     * @param {string|null|undefined} time - an optional time
     */
    constructor(id, time) {
      this.id = id;
      this.time = time;
    }
    /**
     * @returns {string}
     */
    toPrivatePlayerUrl() {
      const duckUrl = new URL(`duck://player/${this.id}`);
      if (this.time) {
        duckUrl.searchParams.set("t", this.time);
      }
      return duckUrl.href;
    }
    /**
     * Get the large thumbnail URL for the current video id
     *
     * @returns {string}
     */
    toLargeThumbnailUrl() {
      const url = new URL(`/vi/${this.id}/maxresdefault.jpg`, "https://i.ytimg.com");
      return url.href;
    }
    /**
     * Create a VideoParams instance from a href, only if it's on the watch page
     *
     * @param {string} href
     * @returns {VideoParams|null}
     */
    static forWatchPage(href) {
      let url;
      try {
        url = new URL(href);
      } catch (e3) {
        return null;
      }
      if (!url.pathname.startsWith("/watch")) {
        return null;
      }
      return _VideoParams.fromHref(url.href);
    }
    /**
     * Convert a relative pathname into VideoParams
     *
     * @param pathname
     * @returns {VideoParams|null}
     */
    static fromPathname(pathname) {
      let url;
      try {
        url = new URL(pathname, window.location.origin);
      } catch (e3) {
        return null;
      }
      return _VideoParams.fromHref(url.href);
    }
    /**
     * Convert a href into valid video params. Those can then be converted into a private player
     * link when needed
     *
     * @param href
     * @returns {VideoParams|null}
     */
    static fromHref(href) {
      let url;
      try {
        url = new URL(href);
      } catch (e3) {
        return null;
      }
      let id = null;
      const vParam = url.searchParams.get("v");
      const tParam = url.searchParams.get("t");
      let time = null;
      if (vParam && _VideoParams.validVideoId.test(vParam)) {
        id = vParam;
      } else {
        return null;
      }
      if (tParam && _VideoParams.validTimestamp.test(tParam)) {
        time = tParam;
      }
      return new _VideoParams(id, time);
    }
  };
  __publicField(_VideoParams, "validVideoId", /^[a-zA-Z0-9-_]+$/);
  __publicField(_VideoParams, "validTimestamp", /^[0-9hms]+$/);
  var VideoParams = _VideoParams;

  // pages/duckplayer/src/utils.js
  function createYoutubeURLForError(href, urlBase) {
    const valid = VideoParams.forWatchPage(href);
    if (!valid) return null;
    const original = new URL(href);
    if (original.searchParams.get("feature") !== "emb_err_woyt") return null;
    const url = new URL(urlBase);
    url.searchParams.set("v", valid.id);
    if (typeof valid.time === "string") {
      url.searchParams.set("t", valid.time);
    }
    return url.toString();
  }
  function getValidVideoTitle(iframeTitle) {
    if (typeof iframeTitle !== "string") return null;
    if (iframeTitle === "YouTube") return null;
    return iframeTitle.replace(/ - YouTube$/g, "");
  }

  // pages/duckplayer/app/features/click-capture.js
  var ClickCapture = class {
    /**
     * @param {object} params
     * @param {string} params.baseUrl
     */
    constructor({ baseUrl }) {
      this.baseUrl = baseUrl;
    }
    /**
     * @param {HTMLIFrameElement} iframe
     */
    iframeDidLoad(iframe) {
      const handler = (e3) => {
        if (!e3.target) return;
        const target = (
          /** @type {Element} */
          e3.target
        );
        if (!("href" in target) || typeof target.href !== "string") return;
        const next = createYoutubeURLForError(target.href, this.baseUrl);
        if (!next) return;
        e3.preventDefault();
        e3.stopImmediatePropagation();
        window.location.href = next;
      };
      iframe.contentDocument?.addEventListener("click", handler);
      return () => {
        iframe.contentDocument?.removeEventListener("click", handler);
      };
    }
  };

  // pages/duckplayer/app/features/title-capture.js
  var TitleCapture = class {
    /**
     * @param {HTMLIFrameElement} iframe
     */
    iframeDidLoad(iframe) {
      const setter = (title) => {
        const validTitle = getValidVideoTitle(title);
        if (validTitle) {
          document.title = "Duck Player - " + validTitle;
        }
      };
      const doc = iframe.contentDocument;
      const win = iframe.contentWindow;
      if (!doc) {
        console.log("could not access contentDocument");
        return () => {
        };
      }
      if (doc.title) {
        setter(doc.title);
      }
      if (win && doc) {
        const titleElem = doc.querySelector("title");
        if (titleElem) {
          const observer = new win.MutationObserver(function(mutations) {
            mutations.forEach(function(mutation) {
              setter(mutation.target.textContent);
            });
          });
          observer.observe(titleElem, { childList: true });
        } else {
          console.warn("could not access title in iframe");
        }
      } else {
        console.warn("could not access iframe?.contentWindow && iframe?.contentDocument");
      }
      return null;
    }
  };

  // pages/duckplayer/app/features/mouse-capture.js
  var MouseCapture = class {
    /**
     * @param {HTMLIFrameElement} iframe
     */
    iframeDidLoad(iframe) {
      iframe.contentDocument?.addEventListener("mousemove", () => {
        window.dispatchEvent(new Event("iframe-mousemove"));
      });
      return null;
    }
  };

  // pages/duckplayer/app/features/error-detection.js
  var ErrorDetection = class {
    /** @type {HTMLIFrameElement} */
    iframe;
    /** @type {CustomErrorSettings} */
    options;
    /**
     * @param {CustomErrorSettings} options
     */
    constructor(options) {
      this.options = options;
      this.errorSelector = options?.settings?.youtubeErrorSelector || ".ytp-error";
      console.log("options", options);
    }
    /**
     * @param {HTMLIFrameElement} iframe
     */
    iframeDidLoad(iframe) {
      this.iframe = iframe;
      if (this.options?.state !== "enabled") {
        console.log("Error detection disabled");
        return null;
      }
      const contentWindow = iframe.contentWindow;
      const documentBody = contentWindow?.document?.body;
      if (contentWindow && documentBody) {
        if (checkForError(this.errorSelector, documentBody)) {
          const error = getErrorType(contentWindow, this.options.settings?.signInRequiredSelector);
          window.dispatchEvent(new CustomEvent(YOUTUBE_ERROR_EVENT, { detail: { error } }));
          return null;
        }
        const observer = new MutationObserver(this.handleMutation.bind(this));
        observer.observe(documentBody, {
          childList: true,
          subtree: true
          // Observe all descendants of the body
        });
        return () => {
          observer.disconnect();
        };
      }
      return null;
    }
    /**
     * Mutation handler that checks new nodes for error states
     *
     * @type {MutationCallback}
     */
    handleMutation(mutationsList) {
      for (const mutation of mutationsList) {
        if (mutation.type === "childList") {
          mutation.addedNodes.forEach((node) => {
            if (checkForError(this.errorSelector, node)) {
              console.log("A node with an error has been added to the document:", node);
              const error = getErrorType(this.iframe.contentWindow, this.options.settings?.signInRequiredSelector);
              window.dispatchEvent(new CustomEvent(YOUTUBE_ERROR_EVENT, { detail: { error } }));
            }
          });
        }
      }
    }
  };

  // pages/duckplayer/app/features/replace-watch-links.js
  var ReplaceWatchLinks = class {
    /**
     * @param {string} videoId
     * @param {() => void} handler - what to invoke when a watch-link was clicked
     */
    constructor(videoId, handler) {
      this.videoId = videoId;
      this.handler = handler;
    }
    /**
     * @param {HTMLIFrameElement} iframe
     */
    iframeDidLoad(iframe) {
      const doc = iframe.contentDocument;
      const win = iframe.contentWindow;
      if (!doc) {
        console.log("could not access contentDocument");
        return () => {
        };
      }
      if (win && doc) {
        doc.addEventListener(
          "click",
          (e3) => {
            if (!(e3.target instanceof /** @type {any} */
            win.Element)) return;
            const closestLink = (
              /** @type {Element} */
              e3.target.closest("a[href]")
            );
            if (closestLink && this.isWatchLink(closestLink.href)) {
              e3.preventDefault();
              e3.stopPropagation();
              this.handler();
            }
          },
          {
            capture: true
          }
        );
      } else {
        console.warn("could not access iframe?.contentWindow && iframe?.contentDocument");
      }
      return null;
    }
    /**
     * @param {string} href
     * @return {boolean}
     */
    isWatchLink(href) {
      const videoParams = VideoParams.forWatchPage(href);
      return videoParams?.id === this.videoId;
    }
  };

  // pages/duckplayer/app/features/iframe.js
  var IframeFeature = class {
    /**
     * @param {HTMLIFrameElement} _iframe
     * @returns {(() => void) | null}
     */
    iframeDidLoad(_iframe) {
      return () => {
        console.log("teardown");
      };
    }
    static noop() {
      return {
        iframeDidLoad: () => {
          return () => {
          };
        }
      };
    }
  };
  function createIframeFeatures(settings, embed) {
    return {
      /**
       * @return {IframeFeature}
       */
      pip: () => {
        if (settings.pip.state === "enabled") {
          return new PIP();
        }
        return IframeFeature.noop();
      },
      /**
       * @return {IframeFeature}
       */
      autofocus: () => {
        return new AutoFocus();
      },
      /**
       * @return {IframeFeature}
       */
      clickCapture: () => {
        return new ClickCapture({
          baseUrl: settings.youtubeBase
        });
      },
      /**
       * @return {IframeFeature}
       */
      titleCapture: () => {
        return new TitleCapture();
      },
      /**
       * @return {IframeFeature}
       */
      mouseCapture: () => {
        return new MouseCapture();
      },
      /**
       * @return {IframeFeature}
       */
      errorDetection: () => {
        return new ErrorDetection(settings.customError);
      },
      /**
       * @param {() => void} handler - what to invoke when a watch-link was clicked
       * @return {IframeFeature}
       */
      replaceWatchLinks: (handler) => {
        return new ReplaceWatchLinks(embed.videoId.id, handler);
      }
    };
  }

  // pages/duckplayer/app/components/Player.jsx
  function Player({ src, layout, embed }) {
    const { ref, didLoad } = useIframeEffects(src, embed);
    const wrapperClasses = (0, import_classnames9.default)({
      [Player_default.root]: true,
      [Player_default.player]: true,
      [Player_default.desktop]: layout === "desktop",
      [Player_default.mobile]: layout === "mobile"
    });
    const iframeClasses = (0, import_classnames9.default)({
      [Player_default.iframe]: true,
      [Player_default.desktop]: layout === "desktop",
      [Player_default.mobile]: layout === "mobile"
    });
    return /* @__PURE__ */ _("div", { class: wrapperClasses }, /* @__PURE__ */ _(
      "iframe",
      {
        class: iframeClasses,
        frameBorder: "0",
        id: "player",
        allow: "autoplay; encrypted-media; fullscreen",
        sandbox: "allow-popups allow-scripts allow-same-origin allow-popups-to-escape-sandbox",
        src,
        ref,
        onLoad: didLoad
      }
    ));
  }
  function PlayerError({ kind, layout }) {
    const { t: t3 } = useTypedTranslation();
    const errors = {
      ["invalid-id"]: /* @__PURE__ */ _("span", { dangerouslySetInnerHTML: { __html: t3("invalidIdError") } })
    };
    const text = errors[kind] || errors["invalid-id"];
    return /* @__PURE__ */ _(
      "div",
      {
        class: (0, import_classnames9.default)(Player_default.root, {
          [Player_default.desktop]: layout === "desktop",
          [Player_default.mobile]: layout === "mobile"
        })
      },
      /* @__PURE__ */ _("div", { className: Player_default.error }, /* @__PURE__ */ _("p", null, text))
    );
  }
  function useIframeEffects(src, embed) {
    const ref = A2(
      /** @type {HTMLIFrameElement|null} */
      null
    );
    const didLoad = A2(
      /** @type {boolean} */
      false
    );
    const settings = useSettings();
    const openOnYoutube = useOpenOnYoutubeHandler();
    y2(() => {
      if (!ref.current) return;
      const iframe = ref.current;
      const features = createIframeFeatures(settings, embed);
      const iframeFeatures = [
        features.autofocus(),
        features.pip(),
        features.clickCapture(),
        features.titleCapture(),
        features.mouseCapture(),
        features.errorDetection(),
        features.replaceWatchLinks(() => openOnYoutube(embed))
      ];
      const cleanups = [];
      const loadHandler = () => {
        for (let feature of iframeFeatures) {
          try {
            cleanups.push(feature.iframeDidLoad(iframe));
          } catch (e3) {
            console.error(e3);
          }
        }
      };
      if (didLoad.current === true) {
        loadHandler();
      } else {
        iframe.addEventListener("load", loadHandler);
      }
      return () => {
        for (let cleanup of cleanups) {
          cleanup?.();
        }
        iframe.removeEventListener("load", loadHandler);
      };
    }, [src, settings, embed]);
    return { ref, didLoad: () => didLoad.current = true };
  }

  // pages/duckplayer/app/components/YouTubeError.jsx
  var import_classnames10 = __toESM(require_classnames(), 1);

  // pages/duckplayer/app/components/YouTubeError.module.css
  var YouTubeError_default = {
    error: "YouTubeError_error",
    desktop: "YouTubeError_desktop",
    mobile: "YouTubeError_mobile",
    container: "YouTubeError_container",
    content: "YouTubeError_content",
    icon: "YouTubeError_icon",
    heading: "YouTubeError_heading",
    messages: "YouTubeError_messages",
    buttons: "YouTubeError_buttons",
    spacer: "YouTubeError_spacer"
  };

  // pages/duckplayer/app/components/YouTubeError.jsx
  function useErrorStrings(youtubeError) {
    const { t: t3 } = useTypedTranslation();
    const version = "v2";
    const versions = {
      v1: {
        "sign-in-required": {
          heading: t3("blockedVideoErrorHeading"),
          messages: [t3("signInRequiredErrorMessage1"), t3("signInRequiredErrorMessage2")],
          variant: "paragraphs"
        },
        unknown: {
          heading: t3("blockedVideoErrorHeading"),
          messages: [t3("blockedVideoErrorMessage1"), t3("blockedVideoErrorMessage2")],
          variant: "paragraphs"
        }
      },
      v2: {
        "sign-in-required": {
          heading: t3("signInRequiredErrorHeading2"),
          messages: [t3("signInRequiredErrorMessage2a"), t3("signInRequiredErrorMessage2b")],
          variant: "paragraphs"
        },
        "age-restricted": {
          heading: t3("ageRestrictedErrorHeading2"),
          messages: [t3("ageRestrictedErrorMessage2a"), t3("ageRestrictedErrorMessage2b")],
          variant: "paragraphs"
        },
        "no-embed": {
          heading: t3("noEmbedErrorHeading2"),
          messages: [t3("noEmbedErrorMessage2a"), t3("noEmbedErrorMessage2b")],
          variant: "paragraphs"
        },
        unknown: {
          heading: t3("unknownErrorHeading2"),
          messages: [t3("unknownErrorMessage2a"), t3("unknownErrorMessage2b")],
          variant: "paragraphs"
        }
      }
    };
    return versions[version]?.[youtubeError] || versions[version]?.["unknown"] || versions["v1"]["unknown"];
  }
  function YouTubeError({ layout, embed }) {
    const youtubeError = useYouTubeError();
    if (!youtubeError) {
      return null;
    }
    const { t: t3 } = useTypedTranslation();
    const openOnYoutube = useOpenOnYoutubeHandler();
    const { heading, messages, variant } = useErrorStrings(youtubeError);
    const classes = (0, import_classnames10.default)(YouTubeError_default.error, {
      [YouTubeError_default.desktop]: layout === "desktop",
      [YouTubeError_default.mobile]: layout === "mobile"
    });
    return /* @__PURE__ */ _("div", { className: classes }, /* @__PURE__ */ _("div", { className: YouTubeError_default.container }, /* @__PURE__ */ _("span", { className: YouTubeError_default.icon }), /* @__PURE__ */ _("div", { className: YouTubeError_default.content, "data-testid": "YouTubeErrorContent" }, /* @__PURE__ */ _("h1", { className: YouTubeError_default.heading }, heading), messages && variant === "inline" && /* @__PURE__ */ _("p", { className: YouTubeError_default.messages }, messages.map((item) => /* @__PURE__ */ _("span", { key: item }, item))), messages && variant === "paragraphs" && /* @__PURE__ */ _("div", { className: YouTubeError_default.messages }, messages.map((item) => /* @__PURE__ */ _("p", { key: item }, item))), messages && variant === "list" && /* @__PURE__ */ _("ul", { className: YouTubeError_default.messages }, messages.map((item) => /* @__PURE__ */ _("li", { key: item }, item))), embed && layout === "desktop" && /* @__PURE__ */ _("div", { className: YouTubeError_default.buttons }, /* @__PURE__ */ _("span", { className: YouTubeError_default.spacer }), /* @__PURE__ */ _(
      Button,
      {
        formfactor: "desktop",
        variant: "accent",
        buttonProps: {
          onClick: () => {
            openOnYoutube(embed);
          }
        }
      },
      /* @__PURE__ */ _(OpenInIcon, null),
      t3("watchOnYoutube")
    )))));
  }

  // pages/duckplayer/app/components/Components.jsx
  function Components() {
    const settings = new Settings({
      platform: { name: "macos" },
      customError: { state: "enabled" }
    });
    let embed = (
      /** @type {EmbedSettings} */
      EmbedSettings.fromHref("https://localhost?videoID=123")
    );
    let url = embed?.toEmbedUrl();
    if (!url) throw new Error("unreachable");
    return /* @__PURE__ */ _(k, null, /* @__PURE__ */ _("main", { class: Components_default.main }, /* @__PURE__ */ _("div", { class: Components_default.tube }, /* @__PURE__ */ _(Wordmark, null), /* @__PURE__ */ _("h2", null, "Floating Bar"), /* @__PURE__ */ _("div", { style: "position: relative; padding-left: 10em; min-height: 150px;" }, /* @__PURE__ */ _(InfoIcon, { debugStyles: true })), /* @__PURE__ */ _("h2", null, "Info Tooltip"), /* @__PURE__ */ _(FloatingBar, null, /* @__PURE__ */ _(Button, { icon: true }, /* @__PURE__ */ _(Icon, { src: info_data_default })), /* @__PURE__ */ _(Button, { icon: true }, /* @__PURE__ */ _(Icon, { src: cog_data_default })), /* @__PURE__ */ _(Button, { fill: true }, "Open in YouTube")), /* @__PURE__ */ _("h2", null, "Info Bar"), /* @__PURE__ */ _(SettingsProvider, { settings }, /* @__PURE__ */ _(SwitchProvider, null, /* @__PURE__ */ _(InfoBar, { embed }))), /* @__PURE__ */ _("br", null), /* @__PURE__ */ _("h2", null, "Mobile Switch Bar (ios)"), /* @__PURE__ */ _(SwitchProvider, null, /* @__PURE__ */ _(SwitchBarMobile, { platformName: "ios" })), /* @__PURE__ */ _("h2", null, "Mobile Switch Bar (android)"), /* @__PURE__ */ _(SwitchProvider, null, /* @__PURE__ */ _(SwitchBarMobile, { platformName: "android" })), /* @__PURE__ */ _("h2", null, "Desktop Switch bar"), /* @__PURE__ */ _("h3", null, "idle"), /* @__PURE__ */ _(SwitchProvider, null, /* @__PURE__ */ _(SwitchBarDesktop, null))), /* @__PURE__ */ _("h2", null, /* @__PURE__ */ _("code", null, "inset=false (desktop)")), /* @__PURE__ */ _(SettingsProvider, { settings }, /* @__PURE__ */ _(PlayerContainer, null, /* @__PURE__ */ _(Player, { src: url, layout: "desktop", embed }), /* @__PURE__ */ _(InfoBarContainer, null, /* @__PURE__ */ _(InfoBar, { embed })))), /* @__PURE__ */ _("br", null), /* @__PURE__ */ _(SettingsProvider, { settings }, /* @__PURE__ */ _(YouTubeErrorProvider, { initial: "sign-in-required", locale: "en" }, /* @__PURE__ */ _(PlayerContainer, null, /* @__PURE__ */ _(YouTubeError, { layout: "desktop" }), /* @__PURE__ */ _(InfoBarContainer, null, /* @__PURE__ */ _(InfoBar, { embed }))))), /* @__PURE__ */ _("br", null), /* @__PURE__ */ _(SettingsProvider, { settings }, /* @__PURE__ */ _(YouTubeErrorProvider, { initial: "age-restricted", locale: "en" }, /* @__PURE__ */ _(PlayerContainer, null, /* @__PURE__ */ _(YouTubeError, { layout: "desktop" }), /* @__PURE__ */ _(InfoBarContainer, null, /* @__PURE__ */ _(InfoBar, { embed }))))), /* @__PURE__ */ _("br", null), /* @__PURE__ */ _(SettingsProvider, { settings }, /* @__PURE__ */ _(YouTubeErrorProvider, { initial: "no-embed", locale: "en" }, /* @__PURE__ */ _(PlayerContainer, null, /* @__PURE__ */ _(YouTubeError, { layout: "desktop" }), /* @__PURE__ */ _(InfoBarContainer, null, /* @__PURE__ */ _(InfoBar, { embed }))))), /* @__PURE__ */ _("br", null), /* @__PURE__ */ _(SettingsProvider, { settings }, /* @__PURE__ */ _(YouTubeErrorProvider, { initial: "unknown", locale: "en" }, /* @__PURE__ */ _(PlayerContainer, null, /* @__PURE__ */ _(YouTubeError, { layout: "desktop" }), /* @__PURE__ */ _(InfoBarContainer, null, /* @__PURE__ */ _(InfoBar, { embed }))))), /* @__PURE__ */ _("br", null), /* @__PURE__ */ _(SettingsProvider, { settings }, /* @__PURE__ */ _(YouTubeErrorProvider, { initial: "unknown", locale: "es" }, /* @__PURE__ */ _(PlayerContainer, null, /* @__PURE__ */ _(YouTubeError, { layout: "desktop" }), /* @__PURE__ */ _(InfoBarContainer, null, /* @__PURE__ */ _(InfoBar, { embed }))))), /* @__PURE__ */ _("br", null), /* @__PURE__ */ _("h2", null, /* @__PURE__ */ _("code", null, "inset=true (mobile)")), /* @__PURE__ */ _(PlayerContainer, { inset: true }, /* @__PURE__ */ _(PlayerInternal, { inset: true }, /* @__PURE__ */ _(PlayerError, { layout: "mobile", kind: "invalid-id" }), /* @__PURE__ */ _(SwitchBarMobile, { platformName: "ios" }))), /* @__PURE__ */ _("br", null), /* @__PURE__ */ _(YouTubeErrorProvider, { initial: "sign-in-required", locale: "en" }, /* @__PURE__ */ _(PlayerContainer, { inset: true }, /* @__PURE__ */ _(PlayerInternal, { inset: true }, /* @__PURE__ */ _(YouTubeError, { layout: "mobile" }), /* @__PURE__ */ _(SwitchBarMobile, { platformName: "ios" })))), /* @__PURE__ */ _("br", null), /* @__PURE__ */ _(YouTubeErrorProvider, { initial: "age-restricted", locale: "en" }, /* @__PURE__ */ _(PlayerContainer, { inset: true }, /* @__PURE__ */ _(PlayerInternal, { inset: true }, /* @__PURE__ */ _(YouTubeError, { layout: "mobile" }), /* @__PURE__ */ _(SwitchBarMobile, { platformName: "ios" })))), /* @__PURE__ */ _("br", null), /* @__PURE__ */ _(YouTubeErrorProvider, { initial: "no-embed", locale: "en" }, /* @__PURE__ */ _(PlayerContainer, { inset: true }, /* @__PURE__ */ _(PlayerInternal, { inset: true }, /* @__PURE__ */ _(YouTubeError, { layout: "mobile" }), /* @__PURE__ */ _(SwitchBarMobile, { platformName: "ios" })))), /* @__PURE__ */ _("br", null), /* @__PURE__ */ _(YouTubeErrorProvider, { initial: "unknown", locale: "en" }, /* @__PURE__ */ _(PlayerContainer, { inset: true }, /* @__PURE__ */ _(PlayerInternal, { inset: true }, /* @__PURE__ */ _(YouTubeError, { layout: "mobile" }), /* @__PURE__ */ _(SwitchBarMobile, { platformName: "ios" })))), /* @__PURE__ */ _("br", null), /* @__PURE__ */ _(YouTubeErrorProvider, { initial: "unknown", locale: "es" }, /* @__PURE__ */ _(PlayerContainer, { inset: true }, /* @__PURE__ */ _(PlayerInternal, { inset: true }, /* @__PURE__ */ _(YouTubeError, { layout: "mobile" }), /* @__PURE__ */ _(SwitchBarMobile, { platformName: "ios" })))), /* @__PURE__ */ _("br", null)));
  }

  // pages/duckplayer/app/components/MobileApp.jsx
  var import_classnames11 = __toESM(require_classnames(), 1);

  // pages/duckplayer/app/components/MobileApp.module.css
  var MobileApp_default = {
    main: "MobileApp_main",
    hideInFocus: "MobileApp_hideInFocus",
    fadeout: "MobileApp_fadeout",
    filler: "MobileApp_filler",
    switch: "MobileApp_switch",
    embed: "MobileApp_embed",
    logo: "MobileApp_logo",
    buttons: "MobileApp_buttons",
    detachedControls: "MobileApp_detachedControls"
  };

  // pages/duckplayer/app/features/app.js
  function createAppFeaturesFrom(settings) {
    return {
      focusMode: () => {
        if (settings.focusMode.state === "enabled") {
          return /* @__PURE__ */ _(FocusMode, null);
        } else {
          return null;
        }
      }
    };
  }

  // pages/duckplayer/app/components/MobileButtons.module.css
  var MobileButtons_default = {
    buttons: "MobileButtons_buttons"
  };

  // pages/duckplayer/app/components/MobileButtons.jsx
  function MobileButtons({ embed, accentedWatchButton = false }) {
    const openSettings = useOpenSettingsHandler();
    const openInfo = useOpenInfoHandler();
    const openOnYoutube = useOpenOnYoutubeHandler();
    const { t: t3 } = useTypedTranslation();
    return /* @__PURE__ */ _("div", { class: MobileButtons_default.buttons }, /* @__PURE__ */ _(
      Button,
      {
        icon: true,
        buttonProps: {
          "aria-label": t3("openInfoButton"),
          onClick: openInfo
        }
      },
      /* @__PURE__ */ _(Icon, { src: info_data_default })
    ), /* @__PURE__ */ _(
      Button,
      {
        icon: true,
        buttonProps: {
          "aria-label": t3("openSettingsButton"),
          onClick: openSettings
        }
      },
      /* @__PURE__ */ _(Icon, { src: cog_data_default })
    ), /* @__PURE__ */ _(
      Button,
      {
        fill: true,
        variant: accentedWatchButton ? "accent" : "standard",
        buttonProps: {
          onClick: () => {
            if (embed) openOnYoutube(embed);
          }
        }
      },
      t3("watchOnYoutube")
    ));
  }

  // pages/duckplayer/app/providers/OrientationProvider.jsx
  function OrientationProvider({ onChange }) {
    y2(() => {
      if (!screen.orientation?.type) {
        onChange(getOrientationFromWidth());
        return;
      }
      onChange(getOrientationFromScreen());
      const handleOrientationChange = () => {
        onChange(getOrientationFromScreen());
      };
      screen.orientation.addEventListener("change", handleOrientationChange);
      return () => screen.orientation.removeEventListener("change", handleOrientationChange);
    }, []);
    y2(() => {
      let timer;
      const listener = () => {
        clearTimeout(timer);
        timer = setTimeout(() => onChange(getOrientationFromWidth()), 300);
      };
      window.addEventListener("resize", listener);
      return () => window.removeEventListener("resize", listener);
    }, []);
    return null;
  }
  function getOrientationFromWidth() {
    return window.innerWidth > window.innerHeight ? "landscape" : "portrait";
  }
  function getOrientationFromScreen() {
    return screen.orientation.type.includes("landscape") ? "landscape" : "portrait";
  }

  // pages/duckplayer/app/components/MobileApp.jsx
  var DISABLED_HEIGHT = 450;
  function MobileApp({ embed }) {
    const settings = useSettings();
    const telemetry2 = useTelemetry();
    const showCustomError = useShowCustomError();
    const features = createAppFeaturesFrom(settings);
    return /* @__PURE__ */ _(k, null, !showCustomError && features.focusMode(), /* @__PURE__ */ _(
      OrientationProvider,
      {
        onChange: (orientation) => {
          if (showCustomError) return;
          if (orientation === "portrait") {
            return FocusMode.enable();
          }
          if (window.innerHeight < DISABLED_HEIGHT) {
            FocusMode.disable();
            telemetry2.landscapeImpression();
            return;
          }
          return FocusMode.enable();
        }
      }
    ), /* @__PURE__ */ _(MobileLayout, { embed }));
  }
  function MobileLayout({ embed }) {
    const platformName = usePlatformName();
    const showCustomError = useShowCustomError();
    return /* @__PURE__ */ _("main", { class: MobileApp_default.main, "data-youtube-error": showCustomError }, /* @__PURE__ */ _("div", { class: (0, import_classnames11.default)(MobileApp_default.filler, MobileApp_default.hideInFocus) }), /* @__PURE__ */ _("div", { class: MobileApp_default.embed }, embed === null && /* @__PURE__ */ _(PlayerError, { layout: "mobile", kind: "invalid-id" }), embed !== null && showCustomError && /* @__PURE__ */ _(YouTubeError, { layout: "mobile", embed }), embed !== null && !showCustomError && /* @__PURE__ */ _(Player, { src: embed.toEmbedUrl(), layout: "mobile", embed })), /* @__PURE__ */ _("div", { class: (0, import_classnames11.default)(MobileApp_default.logo, MobileApp_default.hideInFocus) }, /* @__PURE__ */ _(MobileWordmark, null)), /* @__PURE__ */ _("div", { class: (0, import_classnames11.default)(MobileApp_default.switch, MobileApp_default.hideInFocus) }, !showCustomError && /* @__PURE__ */ _(SwitchProvider, null, /* @__PURE__ */ _(SwitchBarMobile, { platformName }))), /* @__PURE__ */ _("div", { class: (0, import_classnames11.default)(MobileApp_default.buttons, MobileApp_default.hideInFocus) }, /* @__PURE__ */ _(MobileButtons, { embed, accentedWatchButton: embed !== null && showCustomError })));
  }

  // pages/duckplayer/app/components/DesktopApp.module.css
  var DesktopApp_default = {
    app: "DesktopApp_app",
    portrait: "DesktopApp_portrait",
    landscape: "DesktopApp_landscape",
    wrapper: "DesktopApp_wrapper",
    desktop: "DesktopApp_desktop",
    rhs: "DesktopApp_rhs",
    header: "DesktopApp_header",
    main: "DesktopApp_main",
    controls: "DesktopApp_controls",
    switch: "DesktopApp_switch"
  };

  // pages/duckplayer/app/components/DesktopApp.jsx
  function DesktopApp({ embed }) {
    const settings = useSettings();
    const features = createAppFeaturesFrom(settings);
    const showCustomError = useShowCustomError();
    return /* @__PURE__ */ _(k, null, features.focusMode(), /* @__PURE__ */ _("main", { class: DesktopApp_default.app, "data-youtube-error": showCustomError }, /* @__PURE__ */ _(DesktopLayout, { embed })));
  }
  function DesktopLayout({ embed }) {
    const showCustomError = useShowCustomError();
    return /* @__PURE__ */ _("div", { class: DesktopApp_default.desktop }, /* @__PURE__ */ _(PlayerContainer, null, embed === null && /* @__PURE__ */ _(PlayerError, { layout: "desktop", kind: "invalid-id" }), embed !== null && showCustomError && /* @__PURE__ */ _(YouTubeError, { layout: "desktop", embed }), embed !== null && !showCustomError && /* @__PURE__ */ _(Player, { src: embed.toEmbedUrl(), layout: "desktop", embed }), /* @__PURE__ */ _(HideInFocusMode, { style: "slide" }, /* @__PURE__ */ _(InfoBarContainer, null, /* @__PURE__ */ _(InfoBar, { embed })))));
  }

  // pages/duckplayer/app/index.js
  async function init(messaging2, telemetry2, baseEnvironment2) {
    const result = await callWithRetry(() => messaging2.initialSetup());
    if ("error" in result) {
      throw new Error(result.error);
    }
    const init2 = result.value;
    console.log("INITIAL DATA", init2);
    const environment = baseEnvironment2.withEnv(init2.env).withLocale(init2.locale).withLocale(baseEnvironment2.urlParams.get("locale")).withTextLength(baseEnvironment2.urlParams.get("textLength")).withDisplay(baseEnvironment2.urlParams.get("display"));
    console.log("environment:", environment);
    console.log("locale:", environment.locale);
    document.body.dataset.display = environment.display;
    const strings = environment.locale === "en" ? duckplayer_default : await getTranslationsFromStringOrLoadDynamically(init2.localeStrings, environment.locale) || duckplayer_default;
    const settings = new Settings({}).withPlatformName(baseEnvironment2.injectName).withPlatformName(init2.platform?.name).withPlatformName(baseEnvironment2.urlParams.get("platform")).withFeatureState("pip", init2.settings.pip).withFeatureState("autoplay", init2.settings.autoplay).withFeatureState("focusMode", init2.settings.focusMode).withFeatureState("customError", init2.settings.customError).withDisabledFocusMode(baseEnvironment2.urlParams.get("focusMode")).withCustomError(baseEnvironment2.urlParams.get("customError"));
    const initialYouTubeError = (
      /** @type {YouTubeError} */
      baseEnvironment2.urlParams.get("youtubeError")
    );
    console.log(settings);
    const embed = createEmbedSettings(window.location.href, settings);
    const didCatch = (error) => {
      const message = error?.message || "unknown";
      messaging2.reportPageException({ message });
    };
    document.body.dataset.layout = settings.layout;
    const root = document.querySelector("body");
    if (!root) throw new Error("could not render, root element missing");
    if (environment.display === "app") {
      E(
        /* @__PURE__ */ _(EnvironmentProvider, { debugState: environment.debugState, injectName: environment.injectName, willThrow: environment.willThrow }, /* @__PURE__ */ _(ErrorBoundary, { didCatch, fallback: /* @__PURE__ */ _(Fallback, { showDetails: environment.env === "development" }) }, /* @__PURE__ */ _(UpdateEnvironment, { search: window.location.search }), /* @__PURE__ */ _(TelemetryContext.Provider, { value: telemetry2 }, /* @__PURE__ */ _(MessagingContext2.Provider, { value: messaging2 }, /* @__PURE__ */ _(SettingsProvider, { settings }, /* @__PURE__ */ _(YouTubeErrorProvider, { initial: initialYouTubeError, locale: environment.locale }, /* @__PURE__ */ _(UserValuesProvider, { initial: init2.userValues }, settings.layout === "desktop" && /* @__PURE__ */ _(
          TranslationProvider,
          {
            translationObject: duckplayer_default,
            fallback: duckplayer_default,
            textLength: environment.textLength
          },
          /* @__PURE__ */ _(DesktopApp, { embed })
        ), settings.layout === "mobile" && /* @__PURE__ */ _(
          TranslationProvider,
          {
            translationObject: strings,
            fallback: duckplayer_default,
            textLength: environment.textLength
          },
          /* @__PURE__ */ _(MobileApp, { embed })
        ), /* @__PURE__ */ _(WillThrow, null)))))))),
        root
      );
    } else if (environment.display === "components") {
      E(
        /* @__PURE__ */ _(EnvironmentProvider, { debugState: false, injectName: environment.injectName }, /* @__PURE__ */ _(MessagingContext2.Provider, { value: messaging2 }, /* @__PURE__ */ _(TranslationProvider, { translationObject: duckplayer_default, fallback: duckplayer_default, textLength: environment.textLength }, /* @__PURE__ */ _(Components, null)))),
        root
      );
    }
  }
  function createEmbedSettings(href, settings) {
    const embed = EmbedSettings.fromHref(href);
    if (!embed) return null;
    return embed.withAutoplay(settings.autoplay.state === "enabled").withMuted(settings.platform.name === "ios");
  }
  async function getTranslationsFromStringOrLoadDynamically(stringInput, locale) {
    if (stringInput) {
      try {
        return JSON.parse(stringInput);
      } catch (e3) {
        console.warn("String could not be parsed. Falling back to fetch...");
      }
    }
    try {
      const response = await fetch(`./locales/${locale}/duckplayer.json`);
      if (!response.ok) {
        console.error("Network response was not ok");
        return null;
      }
      return await response.json();
    } catch (e3) {
      console.error("Failed to fetch or parse JSON from the network:", e3);
      return null;
    }
  }

  // pages/duckplayer/src/storage.js
  function deleteStorage(subject) {
    Object.keys(subject).forEach((key) => {
      if (key.indexOf("yt-player") === 0) {
        return;
      }
      subject.removeItem(key);
    });
  }
  function deleteAllCookies() {
    const cookies = document.cookie.split(";");
    for (let i3 = 0; i3 < cookies.length; i3++) {
      const cookie = cookies[i3];
      const eqPos = cookie.indexOf("=");
      const name = eqPos > -1 ? cookie.substr(0, eqPos) : cookie;
      document.cookie = name + "=;expires=Thu, 01 Jan 1970 00:00:00 GMT;domain=youtube-nocookie.com;path=/;";
    }
  }
  function initStorage() {
    window.addEventListener("unload", () => {
      deleteStorage(localStorage);
      deleteStorage(sessionStorage);
      deleteAllCookies();
    });
    window.addEventListener("load", () => {
      deleteStorage(localStorage);
      deleteStorage(sessionStorage);
      deleteAllCookies();
    });
  }

  // pages/duckplayer/src/index.js
  var DuckplayerPage = class {
    /**
     * @param {import("@duckduckgo/messaging").Messaging} messaging
     */
    constructor(messaging2, injectName) {
      this.messaging = createTypedMessages(this, messaging2);
      this.injectName = injectName;
    }
    /**
     * This will be sent if the application has loaded, but a client-side error
     * has occurred that cannot be recovered from
     * @returns {Promise<import("../types/duckplayer.ts").InitialSetupResponse>}
     */
    initialSetup() {
      if (this.injectName === "integration") {
        return Promise.resolve({
          platform: { name: "ios" },
          env: "development",
          userValues: { privatePlayerMode: { alwaysAsk: {} }, overlayInteracted: false },
          settings: {
            pip: {
              state: "enabled"
            },
            autoplay: {
              state: "enabled"
            }
          },
          locale: "en"
        });
      }
      return this.messaging.request("initialSetup");
    }
    /**
     * This is sent when the user wants to set Duck Player as the default.
     *
     * @param {import("../types/duckplayer.ts").UserValues} userValues
     */
    setUserValues(userValues) {
      return this.messaging.request("setUserValues", userValues);
    }
    /**
     * For platforms that require a message to open settings
     */
    openSettings() {
      return this.messaging.notify("openSettings");
    }
    /**
     * For platforms that require a message to open info modal
     */
    openInfo() {
      return this.messaging.notify("openInfo");
    }
    /**
     * This is a subscription that we set up when the page loads.
     * We use this value to show/hide the checkboxes.
     *
     * **Integration NOTE**: Native platforms should always send this at least once on initial page load.
     *
     * - See {@link Messaging.SubscriptionEvent} for details on each value of this message
     *
     * ```json
     * // the payload that we receive should look like this
     * {
     *   "context": "specialPages",
     *   "featureName": "duckPlayerPage",
     *   "subscriptionName": "onUserValuesChanged",
     *   "params": {
     *     "overlayInteracted": false,
     *     "privatePlayerMode": {
     *       "enabled": {}
     *     }
     *   }
     * }
     * ```
     *
     * @param {(value: import("../types/duckplayer.ts").UserValues) => void} cb
     */
    onUserValuesChanged(cb) {
      return this.messaging.subscribe("onUserValuesChanged", cb);
    }
    /**
     * This will be sent if the application fails to load.
     * @param {{error: import('../types/duckplayer.ts').YouTubeError}} params
     */
    reportYouTubeError(params) {
      this.messaging.notify("reportYouTubeError", params);
    }
    /**
     * This will be sent if the application has loaded, but a client-side error
     * has occurred that cannot be recovered from
     * @param {{message: string}} params
     */
    reportPageException(params) {
      this.messaging.notify("reportPageException", params);
    }
    /**
     * This will be sent if the application fails to load.
     * @param {{message: string}} params
     */
    reportInitException(params) {
      this.messaging.notify("reportInitException", params);
    }
  };
  var Telemetry = class {
    /**
     * @internal
     */
    oneTimeEvents = /* @__PURE__ */ new Set();
    /**
     * @param {import("@duckduckgo/messaging").Messaging} messaging
     * @internal
     */
    constructor(messaging2) {
      this.messaging = messaging2;
    }
    /**
     * @param {import('../types/duckplayer.ts').TelemetryEvent} event
     * @internal
     */
    _event(event) {
      this.messaging.notify("telemetryEvent", event);
    }
    /**
     * A landscape impression should only be sent once
     *
     * - Sends {@link "Duckplayer Messages".TelemetryEvent}
     * - With attributes: {@link "Duckplayer Messages".Impression}
     *
     * ```json
     * {
     *   "attributes": {
     *     "name": "impression",
     *     "value": "landscape-layout"
     *   }
     * }
     * ```
     */
    landscapeImpression() {
      if (this.oneTimeEvents.has("landscapeImpression")) return;
      this.oneTimeEvents.add("landscapeImpression");
      this._event({ attributes: { name: "impression", value: "landscape-layout" } });
    }
  };
  var baseEnvironment = new Environment().withInjectName(document.documentElement.dataset.platform).withEnv("production");
  var messaging = createSpecialPageMessaging({
    injectName: baseEnvironment.injectName,
    env: baseEnvironment.env,
    pageName: "duckPlayerPage"
  });
  var duckplayerPage = new DuckplayerPage(messaging, "android");
  var telemetry = new Telemetry(messaging);
  init(duckplayerPage, telemetry, baseEnvironment).catch((e3) => {
    console.error(e3);
    const msg = typeof e3?.message === "string" ? e3.message : "unknown init error";
    duckplayerPage.reportInitException({ message: msg });
  });
  initStorage();
})();
/*! Bundled license information:

classnames/index.js:
  (*!
  	Copyright (c) 2018 Jed Watson.
  	Licensed under the MIT License (MIT), see
  	http://jedwatson.github.io/classnames
  *)
*/
