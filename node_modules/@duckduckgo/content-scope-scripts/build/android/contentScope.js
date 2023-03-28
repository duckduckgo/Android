(function () {
    'use strict';

    /* global cloneInto, exportFunction, false */

    // Only use globalThis for testing this breaks window.wrappedJSObject code in Firefox
    // eslint-disable-next-line no-global-assign
    let globalObj = typeof window === 'undefined' ? globalThis : window;
    let Error$1 = globalObj.Error;
    let messageSecret;

    // save a reference to original CustomEvent amd dispatchEvent so they can't be overriden to forge messages
    const OriginalCustomEvent = typeof CustomEvent === 'undefined' ? null : CustomEvent;
    const originalWindowDispatchEvent = typeof window === 'undefined' ? null : window.dispatchEvent.bind(window);
    function registerMessageSecret (secret) {
        messageSecret = secret;
    }

    /**
     * @returns {HTMLElement} the element to inject the script into
     */
    function getInjectionElement () {
        return document.head || document.documentElement
    }

    /**
     * Creates a script element with the given code to avoid Firefox CSP restrictions.
     * @param {string} css
     * @returns {HTMLLinkElement}
     */
    function createStyleElement (css) {
        const style = document.createElement('link');
        style.href = 'data:text/css,' + encodeURIComponent(css);
        style.setAttribute('rel', 'stylesheet');
        style.setAttribute('type', 'text/css');
        return style
    }

    /**
     * Injects a script into the page, avoiding CSP restrictions if possible.
     */
    function injectGlobalStyles (css) {
        const style = createStyleElement(css);
        getInjectionElement().appendChild(style);
    }

    // linear feedback shift register to find a random approximation
    function nextRandom (v) {
        return Math.abs((v >> 1) | (((v << 62) ^ (v << 61)) & (~(~0 << 63) << 62)))
    }

    const exemptionLists = {};
    function shouldExemptUrl (type, url) {
        for (const regex of exemptionLists[type]) {
            if (regex.test(url)) {
                return true
            }
        }
        return false
    }

    let debug = false;

    function initStringExemptionLists (args) {
        const { stringExemptionLists } = args;
        debug = args.debug;
        for (const type in stringExemptionLists) {
            exemptionLists[type] = [];
            for (const stringExemption of stringExemptionLists[type]) {
                exemptionLists[type].push(new RegExp(stringExemption));
            }
        }
    }

    /**
     * Best guess effort if the document is being framed
     * @returns {boolean} if we infer the document is framed
     */
    function isBeingFramed () {
        if ('ancestorOrigins' in globalThis.location) {
            return globalThis.location.ancestorOrigins.length > 0
        }
        return globalThis.top !== globalThis.window
    }

    /**
     * Best guess effort if the document is third party
     * @returns {boolean} if we infer the document is third party
     */
    function isThirdParty () {
        if (!isBeingFramed()) {
            return false
        }
        return !matchHostname(globalThis.location.hostname, getTabHostname())
    }

    /**
     * Best guess effort of the tabs hostname; where possible always prefer the args.site.domain
     * @returns {string|null} inferred tab hostname
     */
    function getTabHostname () {
        let framingOrigin = null;
        try {
            framingOrigin = globalThis.top.location.href;
        } catch {
            framingOrigin = globalThis.document.referrer;
        }

        // Not supported in Firefox
        if ('ancestorOrigins' in globalThis.location && globalThis.location.ancestorOrigins.length) {
            // ancestorOrigins is reverse order, with the last item being the top frame
            framingOrigin = globalThis.location.ancestorOrigins.item(globalThis.location.ancestorOrigins.length - 1);
        }

        try {
            framingOrigin = new URL(framingOrigin).hostname;
        } catch {
            framingOrigin = null;
        }
        return framingOrigin
    }

    /**
     * Returns true if hostname is a subset of exceptionDomain or an exact match.
     * @param {string} hostname
     * @param {string} exceptionDomain
     * @returns {boolean}
     */
    function matchHostname (hostname, exceptionDomain) {
        return hostname === exceptionDomain || hostname.endsWith(`.${exceptionDomain}`)
    }

    const lineTest = /(\()?(https?:[^)]+):[0-9]+:[0-9]+(\))?/;
    function getStackTraceUrls (stack) {
        const urls = new Set();
        try {
            const errorLines = stack.split('\n');
            // Should cater for Chrome and Firefox stacks, we only care about https? resources.
            for (const line of errorLines) {
                const res = line.match(lineTest);
                if (res) {
                    urls.add(new URL(res[2], location.href));
                }
            }
        } catch (e) {
            // Fall through
        }
        return urls
    }

    function getStackTraceOrigins (stack) {
        const urls = getStackTraceUrls(stack);
        const origins = new Set();
        for (const url of urls) {
            origins.add(url.hostname);
        }
        return origins
    }

    // Checks the stack trace if there are known libraries that are broken.
    function shouldExemptMethod (type) {
        // Short circuit stack tracing if we don't have checks
        if (!(type in exemptionLists) || exemptionLists[type].length === 0) {
            return false
        }
        const stack = getStack();
        const errorFiles = getStackTraceUrls(stack);
        for (const path of errorFiles) {
            if (shouldExemptUrl(type, path.href)) {
                return true
            }
        }
        return false
    }

    // Iterate through the key, passing an item index and a byte to be modified
    function iterateDataKey (key, callback) {
        let item = key.charCodeAt(0);
        for (const i in key) {
            let byte = key.charCodeAt(i);
            for (let j = 8; j >= 0; j--) {
                const res = callback(item, byte);
                // Exit early if callback returns null
                if (res === null) {
                    return
                }

                // find next item to perturb
                item = nextRandom(item);

                // Right shift as we use the least significant bit of it
                byte = byte >> 1;
            }
        }
    }

    function isFeatureBroken (args, feature) {
        return isWindowsSpecificFeature(feature)
            ? !args.site.enabledFeatures.includes(feature)
            : args.site.isBroken || args.site.allowlisted || !args.site.enabledFeatures.includes(feature)
    }

    /**
     * For each property defined on the object, update it with the target value.
     */
    function overrideProperty (name, prop) {
        // Don't update if existing value is undefined or null
        if (!(prop.origValue === undefined)) {
            /**
             * When re-defining properties, we bind the overwritten functions to null. This prevents
             * sites from using toString to see if the function has been overwritten
             * without this bind call, a site could run something like
             * `Object.getOwnPropertyDescriptor(Screen.prototype, "availTop").get.toString()` and see
             * the contents of the function. Appending .bind(null) to the function definition will
             * have the same toString call return the default [native code]
             */
            try {
                defineProperty(prop.object, name, {
                    // eslint-disable-next-line no-extra-bind
                    get: (() => prop.targetValue).bind(null)
                });
            } catch (e) {
            }
        }
        return prop.origValue
    }

    function defineProperty (object, propertyName, descriptor) {
        {
            Object.defineProperty(object, propertyName, descriptor);
        }
    }

    function camelcase (dashCaseText) {
        return dashCaseText.replace(/-(.)/g, (match, letter) => {
            return letter.toUpperCase()
        })
    }

    // We use this method to detect M1 macs and set appropriate API values to prevent sites from detecting fingerprinting protections
    function isAppleSilicon () {
        const canvas = document.createElement('canvas');
        const gl = canvas.getContext('webgl');

        // Best guess if the device is an Apple Silicon
        // https://stackoverflow.com/a/65412357
        return gl.getSupportedExtensions().indexOf('WEBGL_compressed_texture_etc') !== -1
    }

    /**
     * Take configSeting which should be an array of possible values.
     * If a value contains a criteria that is a match for this environment then return that value.
     * Otherwise return the first value that doesn't have a criteria.
     *
     * @param {*[]} configSetting - Config setting which should contain a list of possible values
     * @returns {*|undefined} - The value from the list that best matches the criteria in the config
     */
    function processAttrByCriteria (configSetting) {
        let bestOption;
        for (const item of configSetting) {
            if (item.criteria) {
                if (item.criteria.arch === 'AppleSilicon' && isAppleSilicon()) {
                    bestOption = item;
                    break
                }
            } else {
                bestOption = item;
            }
        }

        return bestOption
    }

    /**
     * Get the value of a config setting.
     * If the value is not set, return the default value.
     * If the value is not an object, return the value.
     * If the value is an object, check its type property.
     *
     * @param {string} featureName
     * @param {object} args
     * @param {string} prop
     * @param {any} defaultValue - The default value to use if the config setting is not set
     * @returns The value of the config setting or the default value
     */
    function getFeatureAttr (featureName, args, prop, defaultValue) {
        const configSetting = getFeatureSetting(featureName, args, prop);
        return processAttr(configSetting, defaultValue)
    }

    /**
     * Handles the processing of a config setting.
     * @param {*} configSetting
     * @param {*} defaultValue
     * @returns
     */
    function processAttr (configSetting, defaultValue) {
        if (configSetting === undefined) {
            return defaultValue
        }

        const configSettingType = typeof configSetting;
        switch (configSettingType) {
        case 'object':
            if (Array.isArray(configSetting)) {
                configSetting = processAttrByCriteria(configSetting);
                if (configSetting === undefined) {
                    return defaultValue
                }
            }

            if (!configSetting.type) {
                return defaultValue
            }

            if (configSetting.type === 'undefined') {
                return undefined
            }

            return configSetting.value
        default:
            return defaultValue
        }
    }

    /**
     * @param {string} featureName
     * @param {object} args
     * @param {string} prop
     * @returns {any}
     */
    function getFeatureSetting (featureName, args, prop) {
        const camelFeatureName = camelcase(featureName);
        return args.featureSettings?.[camelFeatureName]?.[prop]
    }

    /**
     * @param {string} featureName
     * @param {object} args
     * @param {string} prop
     * @returns {boolean}
     */
    function getFeatureSettingEnabled (featureName, args, prop) {
        const result = getFeatureSetting(featureName, args, prop);
        return result === 'enabled'
    }

    function getStack () {
        return new Error$1().stack
    }

    /**
     * @template {object} P
     * @typedef {object} ProxyObject<P>
     * @property {(target?: object, thisArg?: P, args?: object) => void} apply
     */

    /**
     * @template [P=object]
     */
    class DDGProxy {
        /**
         * @param {string} featureName
         * @param {P} objectScope
         * @param {string} property
         * @param {ProxyObject<P>} proxyObject
         */
        constructor (featureName, objectScope, property, proxyObject) {
            this.objectScope = objectScope;
            this.property = property;
            this.featureName = featureName;
            this.camelFeatureName = camelcase(this.featureName);
            const outputHandler = (...args) => {
                const isExempt = shouldExemptMethod(this.camelFeatureName);
                if (debug) {
                    postDebugMessage(this.camelFeatureName, {
                        action: isExempt ? 'ignore' : 'restrict',
                        kind: this.property,
                        documentUrl: document.location.href,
                        stack: getStack(),
                        args: JSON.stringify(args[2])
                    });
                }
                // The normal return value
                if (isExempt) {
                    return DDGReflect.apply(...args)
                }
                return proxyObject.apply(...args)
            };
            const getMethod = (target, prop, receiver) => {
                if (prop === 'toString') {
                    const method = Reflect.get(target, prop, receiver).bind(target);
                    Object.defineProperty(method, 'toString', {
                        value: String.toString.bind(String.toString),
                        enumerable: false
                    });
                    return method
                }
                return DDGReflect.get(target, prop, receiver)
            };
            {
                this._native = objectScope[property];
                const handler = {};
                handler.apply = outputHandler;
                handler.get = getMethod;
                this.internal = new globalObj.Proxy(objectScope[property], handler);
            }
        }

        // Actually apply the proxy to the native property
        overload () {
            {
                this.objectScope[this.property] = this.internal;
            }
        }
    }

    function postDebugMessage (feature, message) {
        if (message.stack) {
            const scriptOrigins = [...getStackTraceOrigins(message.stack)];
            message.scriptOrigins = scriptOrigins;
        }
        globalObj.postMessage({
            action: feature,
            message
        });
    }

    let DDGReflect;
    let DDGPromise;

    // Exports for usage where we have to cross the xray boundary: https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Sharing_objects_with_page_scripts
    {
        DDGPromise = globalObj.Promise;
        DDGReflect = globalObj.Reflect;
    }

    function isUnprotectedDomain (topLevelHostname, featureList) {
        let unprotectedDomain = false;
        const domainParts = topLevelHostname.split('.');

        // walk up the domain to see if it's unprotected
        while (domainParts.length > 1 && !unprotectedDomain) {
            const partialDomain = domainParts.join('.');

            unprotectedDomain = featureList.filter(domain => domain.domain === partialDomain).length > 0;

            domainParts.shift();
        }

        return unprotectedDomain
    }

    /**
     * @param {{ features: Record<string, { state: string; settings: any; exceptions: string[] }>; unprotectedTemporary: string; }} data
     * @param {string[]} userList
     * @param {Record<string, unknown>} preferences
     * @param {string[]} platformSpecificFeatures
     */
    function processConfig (data, userList, preferences, platformSpecificFeatures = []) {
        const topLevelHostname = getTabHostname();
        const allowlisted = userList.filter(domain => domain === topLevelHostname).length > 0;
        const remoteFeatureNames = Object.keys(data.features);
        const platformSpecificFeaturesNotInRemoteConfig = platformSpecificFeatures.filter((featureName) => !remoteFeatureNames.includes(featureName));
        const enabledFeatures = remoteFeatureNames.filter((featureName) => {
            const feature = data.features[featureName];
            return feature.state === 'enabled' && !isUnprotectedDomain(topLevelHostname, feature.exceptions)
        }).concat(platformSpecificFeaturesNotInRemoteConfig); // only disable platform specific features if it's explicitly disabled in remote config
        const isBroken = isUnprotectedDomain(topLevelHostname, data.unprotectedTemporary);
        preferences.site = {
            domain: topLevelHostname,
            isBroken,
            allowlisted,
            enabledFeatures
        };
        // TODO
        preferences.cookie = {};

        // Copy feature settings from remote config to preferences object
        preferences.featureSettings = {};
        remoteFeatureNames.forEach((featureName) => {
            if (!enabledFeatures.includes(featureName)) {
                return
            }

            preferences.featureSettings[featureName] = data.features[featureName].settings;
        });

        return preferences
    }

    function isGloballyDisabled (args) {
        return args.site.allowlisted || args.site.isBroken
    }

    const windowsSpecificFeatures = ['windowsPermissionUsage'];

    function isWindowsSpecificFeature (featureName) {
        return windowsSpecificFeatures.includes(featureName)
    }

    function createCustomEvent (eventName, eventDetail) {

        return new OriginalCustomEvent(eventName, eventDetail)
    }

    function sendMessage (messageType, options) {
        // FF & Chrome
        return originalWindowDispatchEvent(createCustomEvent('sendMessageProxy' + messageSecret, { detail: { messageType, options } }))
        // TBD other platforms
    }

    const featureNames = [
        'runtimeChecks',
        'windowsPermissionUsage',
        'webCompat',
        'fingerprintingAudio',
        'fingerprintingBattery',
        'fingerprintingCanvas',
        'cookie',
        'googleRejected',
        'gpc',
        'fingerprintingHardware',
        'referrer',
        'fingerprintingScreenSize',
        'fingerprintingTemporaryStorage',
        'navigatorInterface',
        'clickToLoad',
        'elementHiding'
    ];

    function __variableDynamicImportRuntime0__(path) {
       switch (path) {
         case './features/click-to-load.js': return Promise.resolve().then(function () { return clickToLoad; });
         case './features/click-to-play.js': return Promise.resolve().then(function () { return clickToPlay; });
         case './features/cookie.js': return Promise.resolve().then(function () { return cookie; });
         case './features/element-hiding.js': return Promise.resolve().then(function () { return elementHiding; });
         case './features/fingerprinting-audio.js': return Promise.resolve().then(function () { return fingerprintingAudio; });
         case './features/fingerprinting-battery.js': return Promise.resolve().then(function () { return fingerprintingBattery; });
         case './features/fingerprinting-canvas.js': return Promise.resolve().then(function () { return fingerprintingCanvas; });
         case './features/fingerprinting-hardware.js': return Promise.resolve().then(function () { return fingerprintingHardware; });
         case './features/fingerprinting-screen-size.js': return Promise.resolve().then(function () { return fingerprintingScreenSize; });
         case './features/fingerprinting-temporary-storage.js': return Promise.resolve().then(function () { return fingerprintingTemporaryStorage; });
         case './features/google-rejected.js': return Promise.resolve().then(function () { return googleRejected; });
         case './features/gpc.js': return Promise.resolve().then(function () { return gpc; });
         case './features/navigator-interface.js': return Promise.resolve().then(function () { return navigatorInterface; });
         case './features/referrer.js': return Promise.resolve().then(function () { return referrer; });
         case './features/runtime-checks.js': return Promise.resolve().then(function () { return runtimeChecks; });
         case './features/web-compat.js': return Promise.resolve().then(function () { return webCompat; });
         case './features/windows-permission-usage.js': return Promise.resolve().then(function () { return windowsPermissionUsage; });
         default: return Promise.reject(new Error("Unknown variable dynamic import: " + path));
       }
     }

    function shouldRun () {
        // don't inject into non-HTML documents (such as XML documents)
        // but do inject into XHTML documents
        if (document instanceof Document === false && (
            document instanceof XMLDocument === false ||
            document.createElement('div') instanceof HTMLDivElement === false
        )) {
            return false
        }
        return true
    }

    let initArgs = null;
    const updates = [];
    const features = [];
    const alwaysInitFeatures = new Set(['cookie']);

    async function load$2 (args) {
        if (!shouldRun()) {
            return
        }

        for (const featureName of featureNames) {
            const filename = featureName.replace(/([a-zA-Z])(?=[A-Z0-9])/g, '$1-').toLowerCase();
            const feature = __variableDynamicImportRuntime0__(`./features/${filename}.js`).then(({ init, load, update }) => {
                if (load) {
                    load(args);
                }
                return { featureName, init, update }
            });
            features.push(feature);
        }
    }

    async function init$h (args) {
        initArgs = args;
        if (!shouldRun()) {
            return
        }
        registerMessageSecret(args.messageSecret);
        initStringExemptionLists(args);
        const resolvedFeatures = await Promise.all(features);
        resolvedFeatures.forEach(({ init, featureName }) => {
            if (!isFeatureBroken(args, featureName) || alwaysInitExtensionFeatures(args, featureName)) {
                init(args);
            }
        });
        // Fire off updates that came in faster than the init
        while (updates.length) {
            const update = updates.pop();
            await updateFeaturesInner(update);
        }
    }

    function alwaysInitExtensionFeatures (args, featureName) {
        return args.platform.name === 'extension' && alwaysInitFeatures.has(featureName)
    }

    async function updateFeaturesInner (args) {
        const resolvedFeatures = await Promise.all(features);
        resolvedFeatures.forEach(({ update, featureName }) => {
            if (!isFeatureBroken(initArgs, featureName) && update) {
                update(args);
            }
        });
    }

    /**
     * @module Android integration
     * @category Content Scope Scripts Integrations
     */

    function initCode () {
        // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
        const processedConfig = processConfig($CONTENT_SCOPE$, $USER_UNPROTECTED_DOMAINS$, $USER_PREFERENCES$);
        if (isGloballyDisabled(processedConfig)) {
            return
        }

        load$2({
            platform: processedConfig.platform
        });

        init$h(processedConfig);

        // Not supported:
        // update(message)
    }

    initCode();

    const logoImg$1 = 'data:application/octet-stream;base64,iVBORw0KGgoAAAANSUhEUgAAAIQAAACECAMAAABmmnOVAAA0aXpUWHRSYXcgcHJvZmlsZSB0eXBlIGV4aWYAAHjarZxpkhw5kqX/4xR1BOzLcQAFIDI36OP392BB5lY9Uy0yyWQyMsLpbgaovkVVYe781/+57l//+lcIPXeXS+t11Or5J4884uSL7r9/xvtv8Pn99/3T88/Pwl+/737/IPKtxJ/p+982f14/+X750xv9ep/11++7/vOT2H/eKPx+4/dP0ifr6/3ni+T78ft++LkQN873RR29/flS188b2a8r7n/8zr8v6/tD/+/+8o3GKu3CB6UYTwrJv//m7wqSfoc0+TPx35Aar/Np8HVJ0b1vxZ83Y0H+cnu//vT+zwv0l0X+9ZX7++r//upvix/nz/fT39ay/qwRX/zbH4Tyt++n3x8T//zB6fcVxb/+IIS4/3E7P7/v3f3e893dzJUVrT8R9RY7/HobXrhY8vT+WuVX43fh6/Z+DX51P72x5dubX/yyMEJkV64LOewwww3n/WnBuMQcT2RPYozGRul7nT0a0dgldlG/wo2NHdups5cWj2Mrc4q/ryW8zx3v8yx0PnkHXhoDb8bu/s+/3P/th/+bX+5ee0vs+++14rqiIovL0M7pv7yKDQn3Z9/KW+Bfv3623/8pfhSqmZdpmTs3OP363mKV8EdspbfPidcV/vxSKLi2f96AJeKzCxcTEjvga0gl1OBbjC0E1rGzQZMrjynHxQ6EUuLmImNOqUbXYo/6bP5OC++1scQa9W2wiY0oqabG3pBTbFbOhfhpuRNDs6SSSym1tNJdGWXWVHMttdZWBXKzpZZbabW11ttosycgsPTaW+999DniSGBgGXW00ccYc0Y3+aDJe01eP/nOiiutvMqqq62+xppG+Fi2YtWadRs2d9xpAxO77rb7Hnue4A5IcfIpp552+hlnXmLtpptvufW22++48/eu/ezqP379L3Yt/OxafDul17Xfu8Z3XWu/3iIITor2jB2LObDjTTtAQEftme8h56id0575EUmKErnIor1xO2jH2MJ8Qiw3/N67P3buP9o3V/p/tG/x/7VzTlv3/2PnHFv3z337N7u2xXP2duzLQq2pT2QfPz99utinSG3++z9H9vER0ogNiCz11FFYzM3HZH6SY2UHQOvr7LCJx9/pYxrz3JXnZbdYl+pjGens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';
    const loadingImages$1 = {
        darkMode: 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2220%22%20height%3D%2220%22%20viewBox%3D%220%200%2020%2020%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%20%20%20%20%20%20%3Cstyle%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%40keyframes%20rotate%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20from%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20transform%3A%20rotate%280deg%29%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20to%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20transform%3A%20rotate%28359deg%29%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%3C%2Fstyle%3E%0A%20%20%20%20%20%20%20%20%3Cg%20style%3D%22transform-origin%3A%2050%25%2050%25%3B%20animation%3A%20rotate%201s%20infinite%20reverse%20linear%3B%22%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2218.0968%22%20y%3D%2216.0861%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%28136.161%2018.0968%2016.0861%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.1%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.49878%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.4%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2219.9976%22%20y%3D%228.37451%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2890%2019.9976%208.37451%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.2%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2216.1727%22%20y%3D%221.9917%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2846.1607%2016.1727%201.9917%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.3%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.91309%22%20y%3D%226.88501%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%28136.161%208.91309%206.88501%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.6%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%226.79602%22%20y%3D%2210.996%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2846.1607%206.79602%2010.996%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.7%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%227%22%20y%3D%228.62549%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2890%207%208.62549%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.8%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.49878%22%20y%3D%2213%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.9%22%2F%3E%0A%20%20%20%20%20%20%20%20%3C%2Fg%3E%0A%20%20%20%20%3C%2Fsvg%3E',
        lightMode: 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2220%22%20height%3D%2220%22%20viewBox%3D%220%200%2020%2020%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%20%20%20%20%20%20%3Cstyle%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%40keyframes%20rotate%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20from%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20transform%3A%20rotate%280deg%29%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20to%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20transform%3A%20rotate%28359deg%29%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%3C%2Fstyle%3E%0A%20%20%20%20%20%20%20%20%3Cg%20style%3D%22transform-origin%3A%2050%25%2050%25%3B%20animation%3A%20rotate%201s%20infinite%20reverse%20linear%3B%22%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2218.0968%22%20y%3D%2216.0861%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%28136.161%2018.0968%2016.0861%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.1%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.49878%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.4%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2219.9976%22%20y%3D%228.37451%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2890%2019.9976%208.37451%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.2%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2216.1727%22%20y%3D%221.9917%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2846.1607%2016.1727%201.9917%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.3%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.91309%22%20y%3D%226.88501%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%28136.161%208.91309%206.88501%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.6%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%226.79602%22%20y%3D%2210.996%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2846.1607%206.79602%2010.996%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.7%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%227%22%20y%3D%228.62549%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2890%207%208.62549%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.8%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.49878%22%20y%3D%2213%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.9%22%2F%3E%0A%20%20%20%20%20%20%20%20%3C%2Fg%3E%0A%20%20%20%20%3C%2Fsvg%3E' // 'data:application/octet-stream;base64,PHN2ZyB3aWR0aD0iMjAiIGhlaWdodD0iMjAiIHZpZXdCb3g9IjAgMCAyMCAyMCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KCTxzdHlsZT4KCQlAa2V5ZnJhbWVzIHJvdGF0ZSB7CgkJCWZyb20gewoJCQkJdHJhbnNmb3JtOiByb3RhdGUoMGRlZyk7CgkJCX0KCQkJdG8gewoJCQkJdHJhbnNmb3JtOiByb3RhdGUoMzU5ZGVnKTsKCQkJfQoJCX0KCTwvc3R5bGU+Cgk8ZyBzdHlsZT0idHJhbnNmb3JtLW9yaWdpbjogNTAlIDUwJTsgYW5pbWF0aW9uOiByb3RhdGUgMXMgaW5maW5pdGUgcmV2ZXJzZSBsaW5lYXI7Ij4KCQk8cmVjdCB4PSIxOC4wOTY4IiB5PSIxNi4wODYxIiB3aWR0aD0iMyIgaGVpZ2h0PSI3IiByeD0iMS41IiB0cmFuc2Zvcm09InJvdGF0ZSgxMzYuMTYxIDE4LjA5NjggMTYuMDg2MSkiIGZpbGw9IiNmZmZmZmYiIGZpbGwtb3BhY2l0eT0iMC4xIi8+CQoJCTxyZWN0IHg9IjguNDk4NzgiIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIGZpbGw9IiNmZmZmZmYiIGZpbGwtb3BhY2l0eT0iMC40Ii8+CgkJPHJlY3QgeD0iMTkuOTk3NiIgeT0iOC4zNzQ1MSIgd2lkdGg9IjMiIGhlaWdodD0iNyIgcng9IjEuNSIgdHJhbnNmb3JtPSJyb3RhdGUoOTAgMTkuOTk3NiA4LjM3NDUxKSIgZmlsbD0iI2ZmZmZmZiIgZmlsbC1vcGFjaXR5PSIwLjIiLz4KCQk8cmVjdCB4PSIxNi4xNzI3IiB5PSIxLjk5MTciIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIHRyYW5zZm9ybT0icm90YXRlKDQ2LjE2MDcgMTYuMTcyNyAxLjk5MTcpIiBmaWxsPSIjZmZmZmZmIiBmaWxsLW9wYWNpdHk9IjAuMyIvPgoJCTxyZWN0IHg9IjguOTEzMDkiIHk9IjYuODg1MDEiIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIHRyYW5zZm9ybT0icm90YXRlKDEzNi4xNjEgOC45MTMwOSA2Ljg4NTAxKSIgZmlsbD0iI2ZmZmZmZiIgZmlsbC1vcGFjaXR5PSIwLjYiLz4KCQk8cmVjdCB4PSI2Ljc5NjAyIiB5PSIxMC45OTYiIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIHRyYW5zZm9ybT0icm90YXRlKDQ2LjE2MDcgNi43OTYwMiAxMC45OTYpIiBmaWxsPSIjZmZmZmZmIiBmaWxsLW9wYWNpdHk9IjAuNyIvPgoJCTxyZWN0IHg9IjciIHk9IjguNjI1NDkiIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIHRyYW5zZm9ybT0icm90YXRlKDkwIDcgOC42MjU0OSkiIGZpbGw9IiNmZmZmZmYiIGZpbGwtb3BhY2l0eT0iMC44Ii8+CQkKCQk8cmVjdCB4PSI4LjQ5ODc4IiB5PSIxMyIgd2lkdGg9IjMiIGhlaWdodD0iNyIgcng9IjEuNSIgZmlsbD0iI2ZmZmZmZiIgZmlsbC1vcGFjaXR5PSIwLjkiLz4KCTwvZz4KPC9zdmc+Cg=='
    };
    const closeIcon$1 = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2212%22%20height%3D%2212%22%20viewBox%3D%220%200%2012%2012%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%3Cpath%20fill-rule%3D%22evenodd%22%20clip-rule%3D%22evenodd%22%20d%3D%22M5.99998%204.58578L10.2426%200.34314C10.6331%20-0.0473839%2011.2663%20-0.0473839%2011.6568%200.34314C12.0474%200.733665%2012.0474%201.36683%2011.6568%201.75735L7.41419%205.99999L11.6568%2010.2426C12.0474%2010.6332%2012.0474%2011.2663%2011.6568%2011.6568C11.2663%2012.0474%2010.6331%2012.0474%2010.2426%2011.6568L5.99998%207.41421L1.75734%2011.6568C1.36681%2012.0474%200.733649%2012.0474%200.343125%2011.6568C-0.0473991%2011.2663%20-0.0473991%2010.6332%200.343125%2010.2426L4.58577%205.99999L0.343125%201.75735C-0.0473991%201.36683%20-0.0473991%200.733665%200.343125%200.34314C0.733649%20-0.0473839%201.36681%20-0.0473839%201.75734%200.34314L5.99998%204.58578Z%22%20fill%3D%22%23222222%22%2F%3E%0A%3C%2Fsvg%3E';

    const blockedFBLogo$1 = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2280%22%20height%3D%2280%22%20viewBox%3D%220%200%2080%2080%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%3Ccircle%20cx%3D%2240%22%20cy%3D%2240%22%20r%3D%2240%22%20fill%3D%22white%22%2F%3E%0A%3Cg%20clip-path%3D%22url%28%23clip0%29%22%3E%0A%3Cpath%20d%3D%22M73.8457%2039.974C73.8457%2021.284%2058.7158%206.15405%2040.0258%206.15405C21.3358%206.15405%206.15344%2021.284%206.15344%2039.974C6.15344%2056.884%2018.5611%2070.8622%2034.7381%2073.4275V49.764H26.0999V39.974H34.7381V32.5399C34.7381%2024.0587%2039.764%2019.347%2047.5122%2019.347C51.2293%2019.347%2055.0511%2020.0799%2055.0511%2020.0799V28.3517H50.8105C46.6222%2028.3517%2045.2611%2030.9693%2045.2611%2033.6393V39.974H54.6846L53.1664%2049.764H45.2611V73.4275C61.4381%2070.9146%2073.8457%2056.884%2073.8457%2039.974Z%22%20fill%3D%22%231877F2%22%2F%3E%0A%3C%2Fg%3E%0A%3Crect%20x%3D%223.01295%22%20y%3D%2211.7158%22%20width%3D%2212.3077%22%20height%3D%2292.3077%22%20rx%3D%226.15385%22%20transform%3D%22rotate%28-45%203.01295%2011.7158%29%22%20fill%3D%22%23666666%22%20stroke%3D%22white%22%20stroke-width%3D%226.15385%22%2F%3E%0A%3Cdefs%3E%0A%3CclipPath%20id%3D%22clip0%22%3E%0A%3Crect%20width%3D%2267.6923%22%20height%3D%2267.6923%22%20fill%3D%22white%22%20transform%3D%22translate%286.15344%206.15405%29%22%2F%3E%0A%3C%2FclipPath%3E%0A%3C%2Fdefs%3E%0A%3C%2Fsvg%3E';

    const blockedYTVideo$1 = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2275%22%20height%3D%2275%22%20viewBox%3D%220%200%2075%2075%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%3Crect%20x%3D%226.75%22%20y%3D%2215.75%22%20width%3D%2256.25%22%20height%3D%2239%22%20rx%3D%2213.5%22%20fill%3D%22%23DE5833%22%2F%3E%0A%20%20%3Cmask%20id%3D%22path-2-outside-1_885_11045%22%20maskUnits%3D%22userSpaceOnUse%22%20x%3D%2223.75%22%20y%3D%2222.5%22%20width%3D%2224%22%20height%3D%2226%22%20fill%3D%22black%22%3E%0A%20%20%3Crect%20fill%3D%22white%22%20x%3D%2223.75%22%20y%3D%2222.5%22%20width%3D%2224%22%20height%3D%2226%22%2F%3E%0A%20%20%3Cpath%20d%3D%22M41.9425%2037.5279C43.6677%2036.492%2043.6677%2033.9914%2041.9425%2032.9555L31.0394%2026.4088C29.262%2025.3416%2027%2026.6218%2027%2028.695L27%2041.7884C27%2043.8615%2029.262%2045.1418%2031.0394%2044.0746L41.9425%2037.5279Z%22%2F%3E%0A%20%20%3C%2Fmask%3E%0A%20%20%3Cpath%20d%3D%22M41.9425%2037.5279C43.6677%2036.492%2043.6677%2033.9914%2041.9425%2032.9555L31.0394%2026.4088C29.262%2025.3416%2027%2026.6218%2027%2028.695L27%2041.7884C27%2043.8615%2029.262%2045.1418%2031.0394%2044.0746L41.9425%2037.5279Z%22%20fill%3D%22white%22%2F%3E%0A%20%20%3Cpath%20d%3D%22M30.0296%2044.6809L31.5739%2047.2529L30.0296%2044.6809ZM30.0296%2025.8024L31.5739%2023.2304L30.0296%2025.8024ZM42.8944%2036.9563L44.4387%2039.5283L42.8944%2036.9563ZM41.35%2036.099L28.4852%2028.3744L31.5739%2023.2304L44.4387%2030.955L41.35%2036.099ZM30%2027.5171L30%2042.9663L24%2042.9663L24%2027.5171L30%2027.5171ZM28.4852%2042.1089L41.35%2034.3843L44.4387%2039.5283L31.5739%2047.2529L28.4852%2042.1089ZM30%2042.9663C30%2042.1888%2029.1517%2041.7087%2028.4852%2042.1089L31.5739%2047.2529C28.2413%2049.2539%2024%2046.8535%2024%2042.9663L30%2042.9663ZM28.4852%2028.3744C29.1517%2028.7746%2030%2028.2945%2030%2027.5171L24%2027.5171C24%2023.6299%2028.2413%2021.2294%2031.5739%2023.2304L28.4852%2028.3744ZM44.4387%2030.955C47.6735%2032.8974%2047.6735%2037.586%2044.4387%2039.5283L41.35%2034.3843C40.7031%2034.7728%2040.7031%2035.7105%2041.35%2036.099L44.4387%2030.955Z%22%20fill%3D%22%23BC4726%22%20mask%3D%22url(%23path-2-outside-1_885_11045)%22%2F%3E%0A%20%20%3Ccircle%20cx%3D%2257.75%22%20cy%3D%2252.5%22%20r%3D%2213.5%22%20fill%3D%22%23E0E0E0%22%2F%3E%0A%20%20%3Crect%20x%3D%2248.75%22%20y%3D%2250.25%22%20width%3D%2218%22%20height%3D%224.5%22%20rx%3D%221.5%22%20fill%3D%22%23666666%22%2F%3E%0A%20%20%3Cpath%20fill-rule%3D%22evenodd%22%20clip-rule%3D%22evenodd%22%20d%3D%22M57.9853%2015.8781C58.2046%2016.1015%2058.5052%2016.2262%2058.8181%2016.2238C59.1311%2016.2262%2059.4316%2016.1015%2059.6509%2015.8781L62.9821%2012.5469C63.2974%2012.2532%2063.4272%2011.8107%2063.3206%2011.3931C63.2139%2010.9756%2062.8879%2010.6495%2062.4703%2010.5429C62.0528%2010.4363%2061.6103%2010.5661%2061.3165%2010.8813L57.9853%2014.2125C57.7627%2014.4325%2057.6374%2014.7324%2057.6374%2015.0453C57.6374%2015.3583%2057.7627%2015.6582%2057.9853%2015.8781ZM61.3598%2018.8363C61.388%2019.4872%2061.9385%2019.9919%2062.5893%2019.9637L62.6915%2019.9559L66.7769%2019.6023C67.4278%2019.5459%2067.9097%2018.9726%2067.8533%2018.3217C67.7968%2017.6708%2067.2235%2017.1889%2066.5726%2017.2453L62.4872%2017.6067C61.8363%2017.6349%2061.3316%2018.1854%2061.3598%2018.8363Z%22%20fill%3D%22%23AAAAAA%22%20fill-opacity%3D%220.6%22%2F%3E%0A%20%20%3Cpath%20fill-rule%3D%22evenodd%22%20clip-rule%3D%22evenodd%22%20d%3D%22M10.6535%2015.8781C10.4342%2016.1015%2010.1336%2016.2262%209.82067%2016.2238C9.5077%2016.2262%209.20717%2016.1015%208.98787%2015.8781L5.65667%2012.5469C5.34138%2012.2532%205.2116%2011.8107%205.31823%2011.3931C5.42487%2010.9756%205.75092%2010.6495%206.16847%2010.5429C6.58602%2010.4363%207.02848%2010.5661%207.32227%2010.8813L10.6535%2014.2125C10.8761%2014.4325%2011.0014%2014.7324%2011.0014%2015.0453C11.0014%2015.3583%2010.8761%2015.6582%2010.6535%2015.8781ZM7.2791%2018.8362C7.25089%2019.4871%206.7004%2019.9919%206.04954%2019.9637L5.9474%2019.9558L1.86197%2019.6023C1.44093%2019.5658%201.07135%2019.3074%200.892432%2018.9246C0.713515%2018.5417%200.752449%2018.0924%200.994567%2017.7461C1.23669%2017.3997%201.6452%2017.2088%202.06624%2017.2453L6.15167%2017.6067C6.80254%2017.6349%207.3073%2018.1854%207.2791%2018.8362Z%22%20fill%3D%22%23AAAAAA%22%20fill-opacity%3D%220.6%22%2F%3E%0A%3C%2Fsvg%3E%0A';
    const videoPlayDark$1 = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2222%22%20height%3D%2226%22%20viewBox%3D%220%200%2022%2026%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%3Cpath%20d%3D%22M21%2011.2679C22.3333%2012.0377%2022.3333%2013.9622%2021%2014.732L3%2025.1244C1.66667%2025.8942%202.59376e-06%2024.9319%202.66105e-06%2023.3923L3.56958e-06%202.60769C3.63688e-06%201.06809%201.66667%200.105844%203%200.875644L21%2011.2679Z%22%20fill%3D%22%23222222%22%2F%3E%0A%3C%2Fsvg%3E%0A';
    const videoPlayLight$1 = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2222%22%20height%3D%2226%22%20viewBox%3D%220%200%2022%2026%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%3Cpath%20d%3D%22M21%2011.2679C22.3333%2012.0377%2022.3333%2013.9622%2021%2014.732L3%2025.1244C1.66667%2025.8942%202.59376e-06%2024.9319%202.66105e-06%2023.3923L3.56958e-06%202.60769C3.63688e-06%201.06809%201.66667%200.105844%203%200.875644L21%2011.2679Z%22%20fill%3D%22%23FFFFFF%22%2F%3E%0A%3C%2Fsvg%3E';

    const ddgFont$1 = 'data:application/octet-stream;base64,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';
    const ddgFontBold$1 = 'data:application/octet-stream;base64,d09GMgABAAAAAFUQABMAAAAA0SgAAFSmAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGYEIP0ZGVE0cGjgbxQYciWIGYACDYghMCYRlEQgKgqYMgoUtC4NaAAE2AiQDhy4EIAWKQAeFbwyCXxuhvSfQ2/aCcNsA0K8f1o4VT8YtvLtlg6JSkjMbEcPGAWM7Pi37//+vSccRrXJbBfz7BK0opJCFbhSKXQXNWtXErsXuxqmDg5UEiZ63MFmoi7SKLI8DhGUFit7zJ11+kIfx6fE7J0G788eUy0Eclr02OMHwTXmTg7nxQ4NmTwhl6UxBkeSmueAo+eIXOKiW+ZeRJW0ZRhcHn8MrTwQ9glYP+UyaBZo2s2W3sKPPfb0kRhRgsGxCsUVg4zJGsnLy/jxt8/1/dxwgIFEi4llFWmijw0JnrVqZzQbLCtaV8FC/Bvp2/94FWLgIFRkPwI4AXIQCVAQyEwlU0IC2lVV0A+A2qdDe0a4nL/48gAaKnC8qCILg4/gO4Pn48T2kFPPcPPO8NM/Dctaa69pW7Dm7dq3NXBlEeOLd/ycNFubbyFfwQQ2yYc3mMM7/5rQkQzLVl5pFBslK6mndnYALSxBHl8CtcBtEMAQWWn73Pe0O/4/cs9tftTjyxANqAWDkLbL4nf+dVqrZ6pa+isfjm9nW/G61J6X5BXVAA2EADoBhnS9BkwqrAUyCfwHY536ZXFtHlE4KL/PsT88WEJIijQfyN3FqW5bAlki4CturS/Xmdq98Ga/X5pafPOAhCYcCEv5GT+lG6dQG2/ZPpTBqhNSI1MlQn/A6/uF++/877Xslq7Srarlp7Gd7+tvZnS2t/ALgPyE8MBSG/SAQHBr4/09XWRqC9pEaSDtEGpSGykSZgyh/99fvpSRytEFexphqD/D/1+lXK0UhD2uWKP/v0RKf/nO11WxRbrdFJd335CfpWTElGcsv4HEGDAP+ziewLNlj6ys45GSBKfbnyTAsIVYMRU1VsV3HUNTVwkO/zNn7edDVSiQygzrh0jRF4RCK8eTf5tpjQ6mbVUzcHSoXak/rilaEQbeuEQaUhufTk5sDeikVrEnpO88GdoDzgchAbf6XqWX6fvcM+Btc3hKU5a6siVQKEtDJ5YpysBvgDKYBcNAzdDNrsCDvxAXOgOYcd6+KXGCvgLMukrGZwquLZJzJYhcryBRlCnUtV1WjTXQYdiCGWPL47Z3fD53fI3FsnLiiRXoBwr8gpmOs1Oc3t8//33Jf6bc3ERGRsAQZwhByyu9b9rP60vrd7Cw6oKiAoUQSlfe+7hjbv5Gufvdm4ROMBK7Q1UsOEAA2AJIA3rG50uF01DcAOXO2rRxUm3o760C/zVlZC6/z+sq7AVpjAQBAMDYe9P9DCEhC0Bm99lejnTXx0Kf7ebhe6hukV54HIJweRkKOQ044iXDKC8hLb44GAC7HgsNH55pkkS9CqJBm5OODl37NQQhPX2VjQLii1PIhMpv6NmKxp61vdFTsjNJyIi7IbCkuamwTF9uyxeWNCw9fmVkeKC0geuujXVucDcAmmMdYhCHIY4FWzF6JnMiNujEvJ7WL+yleK66edW8C64MEYNEchQIeRRif6kF22hXdnocEVg+BEWRcJKhFRHPbBIwiEgkSkXBHJNwXCQ8ZAeno6k6GTXHPgmWwJw0eKJbwDmUFX6e1iPCyyC23Z3bXPT+jypeDxS4hc5JcJ4g6ITjFWmIqkrpiQpA56bn+PIZ4ofGISLDeQZmq368yREWTEYT9zbFlg61VQ6kvPWkWO37AaP1d0d+NFBFKa66aCWvE7hYRT39v4ykbmqzqMNlECoTYGoGcAfTQXb/r62sChmfkx+oPbttGpG8R/iN3F2Y3sRZ4rrsBhSLZen7qk8W1vip7Vxbq0LsP4XGXIApZSIlRnH7X5NZ8smqlWm4nQ0xX6qQI1WSc/R9h1i0XRhAnP1HDXb8KqOPAXCQqd5XvU2n1gsNd+xRfZOOapNZLUiom8kxmKiygUiJrlRTC0mjk6eEZovejiLt7ZtSXu+/LlIegZMPzi8sLFmqFCkmxcB37CrNuFsG+IwSVvPFeGrGZqDRh3PoT3nI+vpe+mwegisEDKjv3pSzCGgEryxeYtRXcTek8+xoqSXc5aZILRjAigj4p8AncoEJ8NVVuqbK6vPXDVgiPVcOdQsySw9LhYA52gyKbwDtbLt6SPvtSH0mAg16sJbL46PJgwFRCeqIBjwOpfN5sjVM8Ooznob3+6X67U1Bf76p0JfnQZ+U4GaPw0PhkdByqI3Uosk0JS0TpJ2pdi2uaQmtwiEPmgmx5kj7F6blEUsU6x0WD3cJKtsJX/kJ6MlRF4QYWeEAXtR1Y6ZKFJRl6n6YjLeNUIgYqEGpq6NR0rzsklZFkht5wLchcaiZG262o9/WTJh2ilihr8XcsL+xZX0DSKTmp9ExfhbBvUjsQf6Qi6NPXsRupI3BghBhpuCo4JVuNgHfu3ywPajCVcA6JgEUhRCPpcOlRDFhiYXFo8bAEbGYcSbiSeUnBk4ovjUA6b2Mh0xKbYQ5sXmLzLcK1mMAS2PFdAMtHLz44GF5i8PjwJiKkIBJNzEQimhJDheGXDH+MRMGUQoezgK5jpyCp7kR95Uiilpmhe3LgE453HB3BCmmQK59RATlbGpUqY1bOqAmzpoyaMWsuTgsqLam14qc1jTZiteWvHTM7Iycfvan1fZsMM0aAtJeohQoV58vbMmsYrLWR0TbCbAejPRT2UzvE3xH+jkKOMTrO2OnthptMzKgPYxKND9FE6+MrGnIpT+YAISJEBQJDqpsr0/EMM5qkcX2UgSr4FkswdVthcNeNO8vIyzWOhKZkhnI0X2DWjMSZKCmavogb9ItQqxBsql/fkWpfKgVTePltocllXmchxmCcAoXkpSNtEmiGhMiDVpWpYQULgQhm+irQNYh7vcwbv8zs6fLZXCqdrLWDEfq12ZLrsgNm/kCKrYzrHJAfUUJ4jYzpGgvXKYA2Yy1qBye8DFjw3IeOlEhGCsjk9G+f55mHzJWBFS7sJlQn4EOXLaLOTGm+rxFNOedpGxoXtgulCUTldFCjdKqqjvQVWYW8dAqr6ztQJcvB/fU9QzLNHlkzUejLxNQ+yybd3Gu54QHzvN95c/1wzk4XxZHU0ZLMZsRE1tkQA6OD3ZD6bnuXaDRQOBJNL2DfDHzGgbTwDAAe8Smgm5HMQM4H/9X6leFg7vM3oYMDCaDOEJhCtcX2UqGpoTpVfpvVxhamH6VXNefd2NIja17exXUUjDD5s4iKr/3GAGQrqBajEVErk0wgOAJQmqnJRqnJNLbyaYTTCqS0PFpbmZSLRhqNEMG0BaVvK5mcRqpulKWM4ND+CkfI9+NI4Zr4+7Q6PROz/6RDHfypQrSX3g4+PCD2p8qKh7YOe4QkkvyaBZ59jIeg2AZgRmOo1emauNsR6h7Bxip1T4PSdlMtSU1katsyNl6pI0jN7ZzANuOaRXh6RZiJK+FOY4JMv+5ep3ePJUojFcfU2K/znj592IwnrtjBmTsVy8+bci/b/enJLwRdIgsPaZSEHRV07IxkyDh8PXDs2bFp1DkdEp0KFOv+Dx5e0lz7Onlh9s3LqXQRElUsOELuia2RoyG5sHyEAiQbSmOU4uGxLOWaQKS9FQUNGw5cvODBRwBXzNghm2sNQVib2DobEbbyth2yF3KIwGHIEQLHTgIWjh5eRHhoginhPUQExOQkBIKxhFIx3whzdtkKQyDMSRZQ4jqUCaDMaSjrWlm/KvrKeTrqRMKeyDgTWl/lEEQl6Q2UuQJljStDoaRtIIdGAI0AKYJAt8NY5bQUCtqFGit2QnFL8qaakd+30FIrrbXRVjt2vf3J311rnfU22Gir7fY65LAjjsEqkABipcA4wJOomCl2sNtb3qcJpJmbnPLNZj2RJSCElHTSPUt3GBsei3jBJahShh80Bi+aQHhj5BDS6b61c9gbno3i0KXOpxKSuSIErZe7+1siU8xzJWbpYUlejIUqkf9cFzf/jhbbahGu01bpAONZf7OHFKRFh65Ya0mMmMwZd2erXdcejNwFjMCM5waW+ph8mZB2ROdgd6fMkTACM0zUIMk1qxMsyGYEXBF85NQHkmD/SLB/Jty/EunfsuGGoxFoBFVDOE9QErMshQKhUBA9HQqtCU4EQDhAnMgghxgDw3QT1SV/+SsCzWER0zCFQsFp4MbgAGsbTNtQsmVxAIEmwMgjJOSX03j4vAkLMYmUl2dor6+h9oh26560xgOZ4EZeCeh17MUStHktqQv9avXmWKl7G1Sxy33V1/YGE2qmWt2/9P81Ubgt3Ko+bwtIuiqgW/72pVQEHYSOTbae2ff9yMUXtkRSc/S9A3JPtqXqB3464TtvA3a3EDjwtH5DfEanYr574g9g/Yy19xEIpjRPxj7+NiSxKS9bYYxJI3iq0dWte+WOVvK9ezvOmaMycoyzcqCy+iw0k7cBY2MDE7cxIx0Zi8HgMD2NdCz0QSJNGiHajoJiYaBloGWw2LybgVAzDEPqeNbLOBwYjMZ0bcbCNhisrFu1aDQmpQAkNdIaDtb2H2Qbluo46gh0f1sFO3fOtvXe5E1HsnXuZb2nfYO1u0jmcbRmuLr/M7qetHRtv8DcSax9Lh4vx9cftuZ6+6j1WO9YoPSHQOreI1TrstbPLW+/2BSBXilsnSjI0jijUKJGM1wiNRp1hkWhZApTaUuFWrWqwBqOJK5XUT1YlW5QGM0yUbFjT58GDviJ4giUPWPRKreJJn9j61AltDziOJTaB3dIve/HpNy23M3/Q+UfgWp3decOwOxzVjRA6KHuXVwQ3X68ggAXCBUTdarZlGO9zIq7teJppPJ/P812D+MASrEuZsTeeJi7MRP52SWn5fa8mjstOHNbLyQA1HpAN/kShCzJTxBXLpFDFZXG8J2E/nro2U5bTkGZ3FsZE4P17SAyGvBh73Ix/CKcg9tYCGi2ElSP9/LLh9RCfamN7tS2QJhIZSGF3TzBGJG1WgBauCirAdZFJQ0vNoNltc3tmXJXPEFVdBsRpubVHMFjb41fXAu7O1FOPSQEVSGvQ+EYaKJ2Q1coW1KxQnl2nL886MpbUcXKKldEKTRbWSWB6kC90opX5NmGx7yM+ugjcLZH2k54XUt4NuMSn5XpmycgWInXIlvkFexZL2FhISgWMS5rMOBK4KJZlSMqLPaikJ2pzgGYI24Lf7U1i9Oonojz5GTwNUIqfznW73a5gMH/p1il/5cC7HtN9QB0GwCgP7IOAIMEQDRMtR69GxJIiqSDNBrz0uYB4BvAs2tF9Mrj+XQyYW6MitEwgUwoo2OSmHbMuqdByN9/eTwgJcaiKL1jxExklIy6y8z748w2/FjrFsVwH/r3p1MPjz489PDgw30mDOkiBfvn5vYb6jn0KDzXmwQEUC6L5NAw1Tcm3pQdfHt28IMaf0MfHh2fnJ6dX1xeNdc3t3f3D233+PT88ho//8IsJ4QzEoXGYHF4ApFEdqG4urlTaR50hieT5cXmePv4+vlzeXxBAGBUYXGpXNHU3tbRebWrp/eHvv7BgaHh0bFr4xPXZ6Zn5wBx9pCIP1JaY6KeJUXmMBhzA7qD6hMAANBwFta8GVopAgBwnHtYMezr2Udv3Lh5996t23s68g6e/f7k5Svo+8N9GHF9+MRxk6dMnTRzFsy4tXA+nPjgAoB+AACInzpWAKzjdsoF19zyxEu/g0QJkvArSMRDGoMMM8pEc+3wyB1vQp9xMsw23z83ZK/WlCxABMjpQne/Q+eGh+A6BVZ0NX79RbTv6QUyK1MWiOgLqdECU9ega2FvLAhaU4SMnk0MMrwFSemaGDY77lff8RpS7l/fNjmKgEreBK0bXn5deWVBUdrkuAWLDgJlot+BEq0nNcBb0HQQvNpV35pN2VlUEbfgEO1nsO8bfccTCNPBBNJ2QrTmwlKYo+nYcm2M1yEhDKLMQlxiiUyd6ct01IvoKS8XjUcUBx0nHVEbt86tk6PF+fSAMfhOFR6hUOKCxjYegrIy2HAU9TLq9cn3G0sVpZ8KnDSteVP6RtNb6Bp04wkdBBihRc1JEt7EFTXjDJPrGprmAVq4gLId3CwrLU8acwzVgCuzIdwFfLYh+uf57GWJjTDRmNfNegGVlCSioI2R26ywc5ED3ZGwqvVFdkFpdAT1sr6xUXadZ4q0GMMJY00cNCl1PE8AbtScQ5oHLSNudihIvL3QeCgLSdYFLaBwqciNJYuQsRjwlbYCwyVCzAEP/InKm4qb0Z0/0WtuqXP0oK2P4qJCo3+fpyZZG4IPI5EkIURHOkQdsaJild0egYo0dwAN0ukoR1d69fBlZp8ruz4RdrWX0np1uSQJF+CdVoXgdeYuQklu8QqiJPbnN3hVidPUwkiPQgBQrU/GWIAEiUpaJ1EuhkyzE0vwi5MyaUGpOMqPIElUatIK9AzUA7c6aK+yD/KpTMKC0pJKQelqA/TBtqadIWvqNNBtjtpZb93TLgsLUa90PRvaiTcjdJS+d6BT7EfLOacWnKIGKHS3dLSmCK04F9QQQefLZ8hBS0okoVIQeSISiJYEIJDnQHVWAGjKdHGZTzunl0B10HTmBLdoQBf1Uib63SUSaEW9+m0tu1q6CrsudgWrVwtwBfWgpaY1tBWmBWOvZyPzErmYiULHhdwQtKZaN0czpRGR4ahInU21KvYMRoEWC+WdqYU2RnN+lZaalmW01eY8U3N9gEp4k1gTlaWoN0DT7wkVyxPlUahkOCf1tCBx1pZgE3MgJA/kJEBsfHxOBkIPuYYfpCQGdB3DQ0s7EjAwsBoieEgVO3p834siuUIySpGeUGblEyQyyKWUgryLRn9s9MUolplmx/LLkMZxmoh//p35IHjzgEhsInweCDrVMtOWllsij4VexCnmVYN2r6yo0mg/b9VyTfAwFTUqdUsGtXKVK2nlbmgBl7K8FJYgkjuY6aAxRDWiy/StrVPR3YsPXKGNGYoCJLMNehDI/1OJXqH+3CqgVBNho+hBvqBihLXWvnLBo3GuomHoRa020p7Qrgqqfm89GZeyMvoyfA9eclLErYj1DjZNjyjXBnB/QzZWLAYa0ag+iZm3SgSL4ldq1i51Fl1k+pxSY/iSFPyiBqNoXhtthrZBRxZ+s+A4aKmd6FfLU2WXIjmaRLDP5z1D1bGZ8aC6n9uc8p3igdKUZ4/Zmx9Hu0SqA/6gJS9YwcC7kqO+txcaJWhE8//XMqbmJ4dNP23qYIV+/AnZ+EoFs7XEv/yMPaSiyi33n9/9wvbO3avRV7nua667tOvlXbOHrGL+pzpplxrm7x6s4XGK33XdSxyXZGzwpoTkbHTPgZwyzxjtGfCraNYGZVS+2dM3VAdZTK7V/NMkinnUauY2awOVsNMgmcRMOkDx2+A/lcbIQ4RAXaNshGIpqrRnKLxce7BWrg4Qayw0EoIjaeKRfIaM0awbrD/mc0xxwpCh1XkTZiRM5lGz2ThNzzNYnMXGDrbdVTf79TfaxbXkhIQmAH3IxfOkEPvCx2XEmiJ9AG650C4G/ToN9PmDRLYbppp5RSrH0DeGTH+HGPSGVmk9dJJ/p5PmyDkcEL8vXLs3DN8LLa3NHIeGob+HC6E5Z8eO4Mi7R44EJ4IzF2DdivYp/eqkE2iTM6zgQaQ8koyNFQYTy9NeUXjXCmpe0t3Fsi7r6KS5TK27s7vYoCZg1ezvTRJaSjFEPYaaLz4RTFHMPOp0QydpSfKrXaUgS+xgML8MMzUwHqwoPg/q+OMYFhwYmmiqhmaVpS2kr9liVE7iT9jRERd8WMg6hhgXbSzX1sjqzKPYN+tREtMPHlEbnKOjCGmn0OvizGVzYwhCMIEBLxDoxs6jsQAiBjXVHSswi+EStcCgTEi+MC6+rm8EfflLMBxqhjF7w7gcipCo187HWSwpl9gY/iI/w06bxlyW6aQ5juCTgzhDhy6mi0iV8cibwmKvGL96Re3gwQW9ivYYU50AwDVgSKN81KCZe/CKPRJVDMZjXjed3vB7Tb+FOFraLpUoBB5WcPLke1u3hvqYnXs3hvYyDzoiI7ZsCXaQ/j0bpqeFHQw60wARrXgAek9dLxLF8aZtMqOVo00Wo2kNQ6qAxlmrA29lP6u2iRS7OZayWSZLW5K/yW053vOe3IIfFwxwN8VX6mDIxr0R1ksRANIhEsVR+aLBVz1Mjg2FAk+AAJV/JAjBFB9QVDnnE6KiOyW12z3BmluDB1cY9/oI3+Ub7g3X3jZTsXbmUhFOZkSZQnSM2KVG3qZGRk0jgkPPiwsTUupEUBqL+wrWPDql9lqGyBTU/2TbTIUHQyPxrS9FBiBn7CmZs1HoK1VLCr6oaaHR4k7eR1bJdTBE0eNiiilmi4QJRqH3WaclZu5cifpzc2DnSX6hSgrI5JYWS8QWhKAHUBgdbdynimt2nDjy7CmcZfk+9winpxUVUY0gVGP+AMZTmfD7hxvZmw1URSMHfnEQR6XxpQSqfsK96H0FERqCYHLDHsYro/d4FzJZ8lyk8JVFdBaBu+JEhvpTehezglgEXbKXcOkieWQmqpc/oTApltAWcrk2Z6gZD0fdG1HyQPX6a1iGWjHYkS2bXMikFDOKPm4YU2LifnF4NSw4fUpvaZcrETCPKOKReI99amjpJXUOl1aK9R6wJPBVxi5ULsSIyW9BNb0cGXp4w9FY6eYgm4FntQKwQZbGbuQ9SUSwBfpGH38Ma5U43p2SFxKl6FC06+Vdk/vW4c9LMgWdePqF7VP70zPSnnhLMO8/6QJkSZ610YBjW1zIfH3SyoYUsfhWNG4bnFjx4BT5iilLGDuEoSsjOx+aeidfZ9yPKqs6a16q1m6etFffxqjNC0CJ9JyX4av5Q+EVmHKXmeiGgtS2O6n+JfcB47MxERiu/bsy2ffxgO23ziGHDKegDgYkqLXgT5twyLshFHAATKI02GxmTxUOHl9CXnwUbDy68qG9IOOWJTeG0oMQHJfU52HsdiHzS8jR3u2tC506d7rdydl6u7XdnMy6IpPlucyojoq3gScxYwBji4kM3aL44yjUGhY7sNgS9Nzph3vlQgb3z+nWWTtCtKgCyCBDDOnsvPFIiCPg2Z3f7S/fjc5YseTIkme3jOI4Zi1qXLdaufLk1ue23gxPnj2Y+te4tO/lVT3rgv+0oU7RkJNtNK2KpA76UKF61YerdzseBYsqKyKxY9eeuKWavXaTXkCB7Qg4Ama9dpoasvgcHZYnYry39wfi6kyGd8RTET7yUefB4NTwRC5cIVmGLOxCQHVdZZJs5qhpFazvxjYgzTqY0gPD+2rbh0uyj+l6i9Bqt+TS8DoZsKbYqLZY7x8bnyodqfN4CtApT62RrQi9dsu8qWMk+zYCb3e1K/TlSuoRoxKR2u70P3KlH7/Vx70ZJz+sRgH+Cy4dcM8elDrjyNzh2vEnuHbex9eOty6v7dQ/pKiVH21WU76qi+lztFFpDM9+SvIsvUKH0BC77pd6eKmFnLpHfZj7HEf5wGOrR9y2HlcCZ4LgLjQINRliXE3wm4rZI3+1oXjsk5DiuH/HNIJYfv3HofzmsMN40yrGm1bqnf6LkC1cEX8s/YN6hHk9prEyPGuYW7qdYIjGiRaiqttNR29MIBqs/ex3zFKYIaIRPatDyobteZAkLRqOX9kv6M1/W8ZLcjcC2m+TD49T6pcZrCD2x5xk30GmuMPM25gvNtiaOzUX+GU4Hj+P9eAwPF7eiwbQiOO0W6Z45qHWpnL08HCEedLhx83eakZrRsUyRmNAjnSPOFpKXeVE+0ww4EDal6MYP9q1roj95mOIrAqVCX5LNq13Khus2LXfj+EsxJPfTHGcuVjOl/ZZIWNuxWmu/ml+0bQBmnw8wlCCdveAZtCa3j37UWN2QfnuVH9D84MA/1rj827upwUggjkJaUnSDoD3r7/87dTkwnWTM/tbf7ekw1011FzV6WSNNTDjAJNuD2U6Mc5U7PE8IZtdVFyZW5RVzk1fqZuZHq+gevMIRG8elcnhk4gcwa7FYHs5q86+nZV5sS8KmWc/t8/PQWbnxiroHG6FD5fO8Amo4AhaLsRQrNK8/LhYlB6nK7X4yWRXzUpdLvgMy44QzIKgDUAErpLnxvFINy/4hes5GVYaImxodt3wYren/3Rz547mQ35KPDvYj5icryR7I9nxzMlTbvAwvmWoAA/FICDONI/48L5oMYwv7/ueV3e2pITPCSIWBJHhUy3p1R0aBmcipgIjFwIiIudaUy519j9/P/BKnsrsAd+4GaQLkaLppP1/ZvH0ZBJatHJddPSknHsVmcL5LeLyoXJhi7BxqJHJrcD689CuESKeSHyThxf6wn2jh09qOtIYVk6Zqg2VAZeSxxcUEGj8X5eZmS6zc1o3qPMUXv8Y9rN0WZV47mmidBn3uSDSbNl7NCtviiouVSVJb/3956JOPSItDEvyT0m2W8gV6FxxSg3DEElhGEQqaVdsR2//DoCxjP/465kx0KiYG31aeKnqdXDxj7mdhzF6t2AA4Pq1vF+vXq9nR1wIBDOzfP1W5VdIpguXp3HEO4yglHwejZHjVnbJdierMj7PlUIt9RTdlJeXD889dnmnebMnpb8j5aRn5AWuu2Mqedj4908jp5LFdufW9TdZlY4DALeWgZbouziyA4YbFytkTxPy49jcbHFsZ4LolsOh7u1SVmWjMyf+RnDqT9LHZjtf6HH4q79UO+mP3HzbVpL9W4cE6lUz/c9VzdO2pNnASP/2i5kRlK7wW4YHnl9+Fl2oxH0x1cDp+zkzw23xwuxUamhleCSlBC/eo8kC4NvnF4uRutUsAyI/qfV6pmWqaVVjkq9PWS191CxAMGpWS/cp802qbkg1y7Rsv56UcqX/I63aq1G7GBHsBU8DRblUcKIiKr3J5lAC3Zx21hBMO0s3hxJOGhu/pRiTTkFOcfXpFFtihmdQSKZnwp77NbeMDzxPXiTlZ/gMm60krMic9EdubDaUZj6uzgB7hdcL3QbCtCHkuaAIvw5YdgSl2zQ+pCIswq0M8xqiz3VmRoAJ0VkS4VkgoW54djf5kbf16ec2nHyFI2PnGP0zbnMGhxEE5JHL5AynTsD0FfOP7B/ZzQPgdY1VjTD9jNaq1nPwsYRmJ/X5yutvAoCM2mj+r4Kli+tDFbbnWBY7Q2SXL28vaEFRv6Ow5HCz06SfO/eZlohYOmAqiiYLTd+rUZ8nEER/VcUSN5vyzFWPzbPrZ8x6rmh3p1KWkhKLnfFegV6NjvaUOi+6rHu2Rprz8kFeAieXon0q4nnIR8YRqzIDPRQITjHKYXyGWAHKDyYoF+P3lzbKgPvkbYtDoh+WMv8eM+u6avz0V5pvoFumQGr3lsxwRiS2DL84oK/GitJbZyGYrwOYr1uf3fgrxjIfQLFW/Gsu+vPtUvFp0quBmlHHLs9mNP+yMrXwXyLdz8sF5+vNSNyKIrE8sSiWJykqAH8UItw/e21vSLVoxr6YHyAE4b6kWLIGoqOH3+xkv/meogejLby+JM1Tc6v3hsxeg9i/4FoPyJ3XHC5dsydfffDf98/i888j03+8/WkLvuFDXdpuM5NvyxFtX4nLL3kYCt917wae1P3jzTOtzLwPObaULm7IxIdvuR92Jtj1Tgc7oraLD67v4sr3By2OxByM0csZnpWeNb6wjiKkCOSKYtbQPxiZ/nvwgX3aeWJgpaG8IW31fkDFM+tNCwn+5ofoZmIfhNQbhKW32awzFgawVTap0Rz21FR6KmxlV89orGaY2CpBmrMwkcXHmz2PZ02HL10i4I/bhO+dHN8XVnMHPzyGvfSWbeROzVUOz2K2ceHvnbj1nQXl+te4v337esuIHMCLEp0afLyd6sWJ/bhI35drULYohOkpDvGCYgx9aLf1B+cD4pbbIzOr8YP2dj3hhjlTN3qrVTGKF1YbFtjn4d7W5wUvu0oWLxhRb7fX/eCLh/y3vC0nNqQ7NphjyzSda3zq7qEKlopTvZG6K03uuvgTkVPmDKM73ufGEXr9516/Jv003fyASv6ZEsoLdUWe3izxdDnlpHWglKe1RDeB9ZzHnGTZLp76eDD8u6UOYQEsMIFlxILn6UFNLGQtfXU1TX0yCxPwPjuwlTUU7ABoZ7mebtM6Xa3lqrfOitIHGLMIRzafHMM++eeY4ZMj95+QzjBZ0KN7uo+sdUMr1d9OrtR6X7wHuee/cvnttC9z02sTlD7FMHI3Ei/lVhNrQ2sb1oYpI1wKwC9NGVEygJabC2B9nOwHQCcHbIAD1DKmdIM6p08ZPFqLbpyo/A9lAgvqcu6ZhffMPf3y5MtxNY/yvvLTADXF++Oga8boCwyEJJgQa4AMbHU6UEhJ8Ba0SuL4vYN1OxyZcRoDLw/JAAZbtGH4iASzIDdEphoJG+5GRYssXqa8FFmgqeG0lCwAxQJuwcj1bpz5z2feffh8cn4sWS5B7GQ4I2MK63cXNsQgM1Dv/dDv1kAG66XzgQ7uXfWksILE6vrMrOrGxDyXsK4rF93ng0rWQUYI9Jt3n87Mc+rdYOQtdUBjqUuS5WPznzQPfsgobozGpCGOc4+nITDCogY/9LvyzNevi2Sq41absD8OCS5lJxNk0KfiqyTMI0LiurnuQfzzk9okewzXFAH3NXFAnycjTn1Za4U2jo/LY1xMoONGtjCUC972x7Uft/due6O/vfl4YH4s+XKGem0U6mpIUDm69DQ19zaW4x47GZ28BOac9oDaBBKCUutG5z/uPRj1tbBShJIgj6LeS5zRorit8LO3TR3liJuaW6tngN4Cfw9vQhyGpiCqB97K9PVBonx8Mr290UigkVAFVGEpeccexOs7EVxpW8CtvsGlPsTeZz4YijO3kG3CGDJvVqZNUEtLSN+xW4cL9Us0SwXMwo8FhEQU+WkxrUgT4RHFl+Xy2+piMpHhiozEfruyV1wvc47CHVGE4i43FDXMFjW2dMgngCyBP5NNjMfYKIjod2UpL96XNTR+cPOUhZOmssuW6EkN0WEprw6fWV7Hq4KOlOSCtld5bbjMpGxfupIE+YRyU+OfzVPKiYTLW+NHMlDOosLGrMzCehEyA7Xjh/72zweNBZ8GN3uiZPdpWy1nPjF/Zl9WUaMQmY46wj0qQaLa/1JkqYD59rgeNmen3QpTifpWamueKRqVLtLYDDrE+uzoxC/78+2pGmUYbbcN/jEMKOfXkckX7XnBQ780or2IDBsLcx9LrkZwgeuJObROxtucY3jTkrXBH7NeeXwP/OzLWttR9aPLa8uz1l/NtW277RcJyKrC8qJcIC9TdwbIFvgx2IR4JLcBqAJKYKgBT3FjUysRcER1alf/rv6zZ+6rzuqrjozXPK55/rhmvObIifuqk780rzSfvgHw+0P+0/Anm1s2hpQ/+f8b/un2txso1ZuujmYGOHsstGg3sR70gmoVok80YliwlNP2M2vnpxYRd9YuqHXJIPZuTv6yyZEsSb/EnsTAplb4mfiZVCbhSEx7SZ8ka3K4iudk7yrMMwWodfYBqE4dg0dNi12YuKTKnopUrAuD7z9sROafcoPIIJ0JJ17Vk7ebLIlwD4OnTtPMtixA+gTBMRfIuksmZN1lHQbQPjNIK7JfQ52ukWVZ6OwTDMdakXU7TMmHlq/Umn4oOyrQDvFV3+OO4/GxuNsDLzKeADAsd7es5sXu4Rp39Lvl16dntS9PyW/oBJZ31UOJLhr6ZA17fNeVqxwmJqemC3LxT+LBW1xmCVn7KFmLWWhxAdRRbIWxjsb5s5pLwgcdfb0z/32Wdw4lpBJ8U8JZpmfcZy9BnkGMO15oaRHIyS29TnBPA6IuOAFGjnkmCL5l5a1RPsg3hPCLXQU3Aq2Tcyng1EGb/2RVHulNLYw0+aXay4q6eHxzQwq2prauVl7NSGtpZmTIK+ou18iT8E0NacTqyz3k2OIrSalFdXExxXUpScV1cZepl6s1V9jysO1h48MNw437jffb7rdExOWZ5vflH1jT/7wrG65FtnYPsPzoPARM4w/yAFrjTCGbTdLMeoqoo303chJyaNpLlLHMv64Ofy8rCq4B18Corl4wiL271v9czZclbK1UtDXxLNWGycqnm3a5DMrfdM+oqecc0q6zfH7Y69ZFAjSqC8TWxJMTgP98hdGocBiVCoNTaTBHU9APg3lQY1gPDO5Bi3mRWHO65qbx+KnZCjXh5JRQraJcqDY5rSasmMVPT+PJM93dhobA9WhdsEFm4KvOzleBmaHWBe9Pjzuv//tpeCqm4E18hjOpaF5Zc2lhuZCciUx4k1h0ffjf/4r2bPIC8iO8i/FudLCbI8o2wCKEmJIQ1IcIPMN0sAkmyabQZI9873zhRKAV1s7WhHgeToITdExtcLbpTh+IYe+JYUTRe4SIMNsFgAxrtVYZdGSAlc9O/j4C5oyPfvs6krgUE7OUOPL12+jIt2+o4ZpHq4qa1dUaxeqjGsWjR7dkH2/j/BhYkg/FMPcmK3ORdUNNakQzNkUbS1t3PciQ9cl0dld7xjU1x8a0tDxrboiNa2woi7lIcYVdpLo5wKjUizAKlebYVNUEM7jSKK4eqhY2CluGWgKBeaedsP0Y36qxvGRe+/T1nOCGnIrDUz80YR3cYM7YFKd9LkDDu9zMuXQ7BIkKtrzoZ8HyGLxynMz0MAVbelhhJRyOT1xS9s+KnuRgUlgg5RARmBrCjJyQ1sb8gI+OPQ56TwHpIOOLmz0+jotHcxuAI+Yrxhrg408bkA+gA5bulXxWYJeCsSc20OjnO1XNAJZhFoBqazRFWQFVFO5msFHsgv7tk58n4BvQ7MJdERzLf/TPvNIx+skfiriaBUlXWsKp7s4eOf48QpIRDvAs49TxbvDSxaJS3T33aecWFhua55Tac8rmBjqLEVDtGdjN4F2i81ydMomi8QlcIrS9GwXAX7i63+gORSX7Ne7Yy1n19u2s3/ul6G8nXkYpogYWBn6uXcFeFrnAHmNO2AGMb9W1vGRe29RETkhDbvmhyd4WW26EwKQ57S9By9l1M8BOvGVnnXQBm+bt3ZUkhF5ocS+B7W0vZTXYj7L+6Jeivmm8jFREDii78dpaB1Fsz7HOotHpaaZ+0wgth+jfnfGkCFsdOgKjXpxaRdSx9UDRqkPS2ibo/i8C8xaeDx1rM8XPh5J4jMCmHpIlQItdz96/OwEa2yf7eyngtDyI8KNLc72jgDZ/ttSClqdAL+dATfLRilE1w5r73NN+N0dcFmG08SRFxQwcAalMmXNhjjA6n8Yo7UNhx6fACuxlRrU4S02/HLlr89W4jGK2m0VHAp4m0Ky9n2xbjnP7D0bHg/tGp4V0d5MHJ9jpmWxO6mNEwjl/hJcksEEKkEmyiWmIqUlBygnwZ5bjdrOZbFuGaP56c2Oj4PXRZ0HxLHyXYl6zlxGYr4YBTWkFd4dhvRZQPzJ+UMu0vIOvRD0jgv+T5jdJh0fvKacH9Ye0YfvyiejxIF5+5Gyky5OgV7OgpurRylHxRwV30GEeifIIaLSBZNq+a2495tqe9uVZUK/JYihx1Hg5OeadC8hkV7wLsfQBQIjQnObAPMlDh/49s2U2EEQ3/gXlHIWoOnFaD1w6xxwujdmTrj7QgRpB/l3efVRS/WcWxAVbOFuVrXjJdCoBx7zjY/BrGPv0jcP65cj6mVXyYPwzM87WBJ+fYU2Mx1wsDvdJWRPjIjwlwgfwVdrh1FED+th+6AGUgZRpXPdPRJD8yNPb6FcvRVRw3uC8XqxYV19PP0asp6dnECPUO697HoBfXXO48/lmxsbdhLowXtyRptutnLeVTp4D8W52z/KyObiJmrC/x908ekJ97CxT/BziJ9Z3Fkb4SXHLu+TBzw/Rww9mG9Mz6rtwddmgAemDnx9HgVnHjB0GHIDT7AD0Ei8Q2e77ihYpBO6/yqvyrbifEnwjJTlk4X5iue8BdWs/cbAnMybU15qSdaf/65vH0TP4pAKOtZZ7YxUFuqV75oh80MVKqv7ylbeWJdbO8SwLUvLX8ET+GJVBiR6+2uTKdmU6W55wH44t4nRpM7Qs34FOU60vaLmWtcAevmg7buMHRXFi+Z530uaWSSBkwqPn3CdzmWNOdsY8le3TUPf3ic1Vww7myHAo1Ij25Tu2avMG9w/XBcU9s5EM08KMa/RWVu3dxn1x2qQSoDPn3s1ambM1NMqxc7KVph4GPOnOQoJ3ezoXxnv9gnwZbR4WUN2ADwgZQiRJyVne4T2FcfxeL9c19xQgs8uJ2snJurNjtQmbOMwrShdj8sA/xPXg0XeJAd8ljqQ9CcgUXcPGy9XGMqNxTG5ZKOLQRckFAgdTunDyLMkOwzaFBcVI0woO69kSugIS92aypbLzD/TBjsZHj9/NM1vnpCsNSbW2ahSYuQZUbqh2iSJe/H16qrBew6G1qOQx0fx259p//9/EMmN0vILpjgWphqUewBeONtodF6sCqkyyvvqOeBLgBzHZ/Sk2SGK7gFvnNFMHLmqsI5oLydl7PL/626uMg751/8vRqTvJ08S2rQHzXLvk33gZHHBHyb1iupj1i69dQhXaDx6/ecZs89X5I4eRj/IqXhVUPCr4+Etc27g/92mmhpjfsvJHUh0HG+b05ux1kAlyL5jEa/YTG/eVp8hOsByPsFZWlDnXQeW5nh1hunPRdPsiVStaO9eB2DrSMz34bs3p5CpnzVsE57c0tbidB5NIoe84GPKiaXgcsYcIhpLIA+QLTe2hVUJZaI3KLqh83Nzl6Mz02NcUV5Bbcfgh2wDLucx3ic9ri4u55BaPDyMbRBKt0SR3y486xCf9V3bq/gswur1s2L/2wuI+OVXuD/UH4lm5/7KyD9vtmdWJ55NFZhKAtQL8t3DmXtJ9Pzx6oBuE/LzBBHhVTT/aYXAx8S8/tFfL0naLWfV2NaLl61ITmv03N2Hx4nDqEeXzbwsSZZqNqMMmZj4xQ/nsy2UXvYjJ6Sb+JCfUq+Kn71RHrTZhqsM8eW4yvhxyMK6dhHlMSHDPBvkTnNFPfnVC+xEbTTLc588sahHHFjVnZRQ1xIrNrm14hs9P6pDsMTxTBCyBeEUTlPvjMMSQ0dSKUOQ0mqaCnHqVGbwAzyJOdToRAAD5ogCuvRfHLPKxQo65v+iGVM+R6oHGcLkY1wiRrTpwCevPRStT+L9ji33M/USjMzPM/+NMSX85h09qOlE9rNQM34tFi3ysiGPOlbevMFEzVyy8wcOIOXB/8cgQgLs/bf25WEqoCN8Qa0Ri68qkWCy6yceJfFR2/3f2VDLgOkQaUTd4XoeI800LF+OPazJf2rugstJbpaVBgaVWVoMXuGWFvW4BluM9Cu8QGsf9fhiXdxBWH4LbavCFndpTawivYn3zn+vcHWwQ7rURmKfJ6l8iJ+wL+AEiEPb/FHPmD8KQNGUiJPRjrckpa0sNk7pP4LAbqZKQXqEZ6/+keWpyw3nah/k9dzdUH+oAzSiDle0qVbKzibqQrYTF18Yd46n4JMAjUW9Spr+HqYs2F9EkLDROXlkjV1AxJqqU7rdNuD/wS8Hcb2Lxn22vtf3IiQ1I3U0xZAx0eW15W70tzmcK2nvt/gGvpnqhxAjQglc7LVPfB6HyfYFR+dIEu/mzhXd/HgFC5FQbaWMZE/3t/1zAV8gkQg1k2EwTtBRyRegkXWFTHr7Ny9QEHRIL1mfZAviMxwtgoUqEC3IzjgFNoRKs9w/BCEGLfHMzIM4WtbXQUIRO8JiT7pFY3PiwwxaZ4nq2EfdYvJCqYht22zuuE9ooBZtoDZXISmcd5wNpTIY1OAwUlGmaCQJXFqlb5wjP67uLVVB3XQdiG/WubZ2x4w4n7pxjKePS8mRb6APcC59c+jBYaSQ9Bi8OifPMihs9mz+++euPX6BptiS8qlfJDze2qHwihMcm8bEcySiqw4Yxs8zyoqL9GN+/5eddH+bvJYfKmt2bGKMMJA2DvZPxcZsQkvVVH+elbd9qbRC76V614JHWnEmzaAf9jwNP/iB94jhmHT0EL9jrtvV7pLu3F5/KgNEg9pc5qM2D+vWbnHnq2kdFY7z63/WlrKhDjAFyo34GWtXAO6zi3tnlfUcnDDVOT5oK9cYZNDrILcs2EE8RgmbAr+cny7tI12kv4bNCDxxeM97NAk/CjfNDcBq9I450kavjoQ/8vqaskqLT/GQcudyV1WI42z8+mcGMr48d1kMEbtwlOOrc1MJ3OOmjvOpyobd+x6fgxD2E+ZHrnzzPsTfv8A6Hh+/LyvbdvTvo+dzT9SC0c7e/OIrGUXcusH3oK1ggbo/3jyLw89pZBQt21MstnA8bJ1ceAIAhtPcHf4L4SrchmO+R7u9PotVd4Fyq1FX+FqEi0cBsE6uGsFrSB6TlYhh9MKZGcajvooKiQUi7ikopyYtaskJBwy2PDkWBGqmPmPSjbvUmiINKfG29C5bbwS8vRAsIL06msMkWmCxfBG95ITn8GtpsomxgqfeE1d3ydeG5p4oRtVJ56OeclUEVo442R+yMqax6EubGr71OlxJGUqsgmhjGO4h9eoIkrMsbTFgqHIKRbYi0IKJDQYbILHMzqWSjjQWUtvtuVQeDS1Mg7ZIg1FB0VkFUFDQ99+phFiUds6Vu7200hS6p56dwSFkHr+niU0maO6kwWcdd6Yo+AF3XAL4V8NHS1L7SqubEhGL760gNjKqf70hLYhfRBqKbeE3vUIAbZMoi1aS7WfS5KQECIIdvTRkEvFuBrMpo0wsiwnIw81nOd5ErM6Z9OBFjyUUjhcF9d0UPxwt9zty4nx9NWWHwGsUhEZnmAZ2SN/0KVTyPtZliZbSxPVFoLyjp+fQ0WxEJ94B+ks1Zc5X1nHBDt7S5QnTKT2/XLMy0jXJEH8h0g+5QAkPWjRH03WIVBf/dNAYLbS4NDYc0hMBjRB/eGWGiN12Q72mJ+Ljxg5ws3gPr7dFtvcImetl5WQi2zYd0uvTZjF++9FK5bra1vHYE1YLfXB9coN6PmqdM4ImF+vDFZN9c72Kt2pjmtXHTJrqTOlZUytxvA6iL99ECe8nGZYM36hy6ow2IWLuqUtO3XkBoDVJlsPAiZJL1KBG8PC8xRThrqouCEQjJpIvVJ+ha0+0X4HY9dWw7bDO2QjbVjbiH2+qAD+9oToe3y8GC6+HTSW3l0oHQ52aX5fwSPA+X4/3hBUlvji+vLSqWHfgyfE+7Bg6PZWVwHLj+W3bA0odqsuDM+1cluKh7Db+6TW+Lv2AhrDRqilzjS9w5Vd1XkuWF/khUjSfpuGK36fEj/ugrRgdu4mpjyp8k4uK7b/7f8xsuzz19ezVZ538GbV/1zf84f5kJl74/k+l//fKzdn0Pqqh5DWm++vmdRvSLxXxx+FkjFR3M3U/4je9cl+u71y++ci43H3V49XGU+IL0yF/hZyRvrh8nYCKUizQ/P+lcIGwXuB/tmgLAnYTUAlEUGNvcy15YFLsDHsfkSo12amCdbgAPcY09bpxRtBsYYAR0qurqVdvI0/Jbg28LljqLJoTtiBuVW+zi/RcYKT2O9W8pdBaVSFHdDYUONZIDd+QDl5S3oQt6U1BEp5kQTAKUyY0SQV9SVSfJ4IrQCPWX0qC1M9+YhPYwYzeVQrBoovZWzuzG0BGqD/UxWz0p4JjbLOGJeigrM2wGMHoneWpTYUbvGpiIbJtSX8eACWHXOOKeOCLWnKjzLUL7TKZXVRmf9PZFKyIY0ttKp6Qg+jHIzsISsUIAHpyUDRGvEw4QxONgXZ6DtvY3sah7FSJ1r8qWuMUPEyPmZdo9E9HNL4bLplhaAge+Sgw/jIdEYlLiilXb+hIjQhtrne9+ZlRDBT4hw0Q6VdPaFYm1y56YBM8WvSwIbYXviyYtGGnDjc+A7oT72EtYSAT2s75G3bR1wsqe3KavgNFhh+HeQOfKEBOh0Im1sWNPchQ3GGkvbkrOr+ZSgJqq83PGDBsrsKnARDCBZtPOlznyCPCUPZaR3UCtAi4N0Ogsu2w9Zk2ijUb/xFPU0Zztq6PHD3LD8DapXrhtDlMFf7bQHez6Ovsq7ObnZ39r0x4C2PmOpOFtv/MGRhSQRdnxTDXONnW4Pr9gf2neBjXRrGl8MtpncoGYcG70+3sDCkDpVVYfujVWw/74em0t7NSNcT5QS7d3T6DYZBA8nSPdOiIg0bFeguGB6/eWUbQBhWP+Ao7T+402sD3BCRuOR8TKoAPLjPVRwR3NjHcf3KNgchWeOTW+TVomJhmEFEOmWil61a+hsQiJgwQESaFeVgJ2lW5dhY4R1h9B8DGegIkrMVVdt/v2r78tJxIJKxjQ6v8mwy/8nf/+9ROlnjFKfKfbbM6foK/L4YeqUwZ9vbcVZZ1U+nmmzRxvIw1bD+CEjCqNiQGE5QrtQtV6OimGZBUj17FNeprzSwH0FuBLS1DThFDUN6n9EgLNszcpXRpVR4dp10Isx3CGt4spbOkFmWSpkz9eDK9zoWqDrtuM30SqjouTSHV+fpF7x5lw+VKTOkNKVZ/AeZqHWSLHyDJk7NSmV+Jh64K/4cBDG0OrBGLkfccRnwTE5mypy5xITF55dY2VIh2X921L5ygbryjHuWzWT8xAzWLa+2nFgAsQGKSFyQ1x3V/XEiw04cw4RlnSjTH1dNxGxc0xtawCTaItcqNqBIf+LrWKk33RsNeKYKW07DcgJFflc5QQ8k5DHBZ+kke6UwUrhCCiyCkJAf9Jd/jYtgBHN0r7aWJJwaCMI8lRvc/ABQ3Hreut7C+MVNt118K2YwqiCIl8wpBIZMGPmH4zjIt2B0BqU0S0US4SDFC7LIfFOqkIxRxFijrVymAo8IZMRS08QNwOyBWysBij7XDlhRYsNCh2S7Jxd6YyXxEDhotkSLXKAyJGm/hiWCa6NQ6SLEEIu06Pu/Ayo9Z8BnXdTKwdNWxiW1wo7saIq9hgvQAxpQnpmO9WTT/ZHouddYyn1q2FOqtQxqdK4Do8DGUD2jQOvgLulCMM+2kjBz15s99l+ULe5UClehXHYCC+IBqwojI3RYezMnJlTiAX9lSoic2zL2hMaltNVXLrrVipYIg1HBQR2uwwKZf2SjSEB2IENEccIc864VOMyzDZy2QN62ioLzccQyOGL0kxEhQtc0NyA5BxpN4dsdoNwdBdESpqIosHNMCOZ2kq2yJTHFUqKB5APFtYO0rrwtNpSHRJNoTD4DV9SPtZTFPuXLL7A4DRuilxTei09ZQy0klbLc5sYWkq8YhpLwY193iKlrceWxJb952madI73uv9EgqcDYffvf4O65s0FjVVnk0cUKjw1W63jAhqFhQ3W+v6QvrslcBkFM8TUkyqi+9sv4Jx1vCpbRcYmvfY+CY45x2nWi2c2i9JxfWisY5rD8Dw7X8v3LLXwenwzQG9+gyM3Y5z+/0W96c3B6/SPDfWdxjkhtJlQCdJj22KWlgVDB01xs3mZirLRmTb47EXCAVQchYbkLzGxLwsHey2VFW1kGFLlaLiFi4gZSvAbZNcHwO3HfNX8JvxcVmYChf//up97QDygtdeNVZFL7z59Y/T7dXvCGVMqEr1G86W/udhy9l9KS99fPutrH8b2onvuBd7XZXwkmzOfauO64KTCc5vQeSWjWsm0CiurA6ji5JAasUS49sioNIrhyMl8AqUkGRQ0J0QKo4Z+qAIhNVOpXlkDHTWLOYjp30s5RWnxEfs2KJM8zh+jHYZ9RFd6hDEMsjMnSZ+ligzL2fWw4n0XbdpIW3fpL541w1QKG9EXRwZH3aZ0cMZs4tcru6rkZFQPVLsgA4CblrF3BKZCx/hwLIFvAPjU7Xnrq6ePKU6sT2ydtwyOtG9hSX2KuyBBg4AnEeJJGo9fL59QQ1krEcNy0zP1WR7AofBJ1DIGwrKmpKU9K6A0jRkXnx3U5epQ0f1/cjctp7KLtTnBrP++Cm6n+41zpY4emjQ2R/fPG3tHvS4iHCu0rTbGWuyfn1jwBuw/WeVenfLMouIuv30Mq5ujPHsEsV55fPDz+UtbnjZlZVV738RY/Tzjng5Hp108xs+tw6XD6LEHtsQNc3NT9FZXBEUESyApeh51jgore2ZL4pHFyWjr8QwHkbjbqRw2Xs7Lub5w7UFehWjEkfAujgcZerm6QPvi4MwmuGgIoX+CNYLMzizO+7cogEP0At276lGYBRx7jk/AaOciNoTwiGxTZBFO5CNpZeis2pISahOwKYdXKtCNs4MEGiTEIdrt4HzQktCV0uN0Gq3yuCwP8f/r5hwb2Z1RPcjX6yPgFZ3IhDsSe79soKO4hRweMGZB2e3Xx0eXS1DxWs89G7DuvYxdHz8+U9fYTYLpL3Chk3Oug25ygt5DY0ECJGmf9iAraC3xD2geMc45hRpxb73Qnortvp9uh5lnRe67kywCGFR0cDbwGMhpoybjBBjvx/vreyfXTy/8kh+0LeySrPjixCenisw6wKcLZJY9e6xkGFEfXayqw4VuiZFnm0OlLqr8Xq/iq3SdPLeJPuhnQYZ5cJW7Z9Y3t2ABmNcB/nXiEkzXUKw3LAWoV4sOxy1EIJ3+3ziFsuUbF8KdvNAc4J7+eyNvl3VJMnVNBEC7f1QHjwdeiNgpSMcvlrkuJE8AUJCNolVprpKTn+cYo6Wjw+5qXpJ1OtQVy5ioCQYHWpXphhnVYdDPaTmxgwSQiWECpC6kCr5KVD9jZpn9MXGCwiGq3EpmdQ50lNOkeOZc7nx3CAFkRUyO6huU1I/fbyQEALXBBUCbV/m/ifbgjVqwoSAXP9E0RQ2zR1LR8ka9G0bWMPkiBl1HN11wVmlAeKsEXAVcrYLx/CK7Zho1x1B5BY3QhYtp6yljtXEqCx2yHLXcQFwWeWe9YI6CsJYgy50my7yatX+SuTZVZDbRuS5ICKJVIpTKcLboR5yl9DzfitAGFQrJCSJSTWbJZ2LJ5bWhvK//YayidMNWoM0Vra8tpwXDNjQ3xhftOqPTqoUfFN718LO7FkAwkIIAQ6wSlMrFloKqJij1ay78pqdAbHA3ZIO13WzoVcPgGBhg/aUik0xGijl5Jw3FAd0HPsOnjdETetjbccdc9VbL6xwqMShpx4g5qnlxd5uXiiwZCBKVtvC5IQwkPYIAAVt23ADdG9PyjEHJEm2417qqY4d6JT1K4shlUgcw4J85ZtkjGMT9zgoD4hSB0dwixHgaPxsilRnI+C5cXlfORB+mCTey4Hl+WGijR8IOuUFNUSXnGbRwLb6WYEr7S6X25rmvVpGmIuXHV/uMq+5t9rUXUVDMS3KjCWjdqTVi/EK9xbip+e9A7HTOUE+dZmdT3d8vkm56XIm8AAFxmNnK/XD3YjDl12u/dIav0aoNR66IbEponYA4N0mRRjjB0bFvT9okhT0pxsSlO669cD2ehPVtBGNsCmpKJv1EmowwNzYg8XGq4/Ob/KjtihKtbxDQE0DO8u4Kx97fBgBWmbgZSDzeO5zSwkSS+kyhyRrcktFv2iwLPGm2rrpkmspUc2IeASJK+3YK4Ka+tC1648ndRXTvBxzTPThwYfNYS/UFsoBPVy5HBwkm6zZhWH5mEr9WHTTSMreUAutqee4wExSh9HMOW92AirjOEWvAW04+rOMZJ0qpSfcajFRHpX1fAQLnDjDDgYd7aDDlHosKu3TTvvy/QlMJ07KWI5BRWQsM6g2pjm4TKiBKf3kpBESloC93VGqasqgXo9CGaISoO9P1deW/F0wcnvb4ufBF1d/bDyHrOSIS+qz4vSCL/B9v1WGLk5n9dHpQ9tYxRmH/hVsvhOlXR8D5Z077MA10vuP3CaDvaKAeAmF8Lv/gbhuN44V1U9VVkiLMTCzt7bgl6Mnq8dYpyRISCC4j0kKBC71PEXaRYoEVj4HIWKSSUmCFInLEPIRgQoYyCeKoBnKhZASBoI0AWt+b6pw3wV/EpgAK+1keF2c6kTFkKjdxJ3pD2WIeQhkSJvIHytAm0SGSG6kKX0ymziuynXUr1WaUd+QGsRoAcFil9AVnVHDXISBjG73lFiOdoKXaLASuUCKeC9gbzyMOk+UQBO6RyvVcM/XW73ZriVFRjnbdY+AXl8lpUhY9sK9EHvUgWhodqz7dwkBehIjEeuf5d8vUScDgdLhTBidJcLFdkHiB5SmDg3W3RQA18Bqv5pWJiA3P5ziXzLxU6Sk1IkExyNuL5mXo0/BGz0sNRr9l87NrR9PrLEjTH35iqXee94wNqRM19wvb/ydZvXHkzert6l3Oe9LpsYRYz/cDyPkxxUYe0GcqyUe2dlcrDFFVL9qsHP6mKqDe+JbbqcRTcQafLbhkJJYdxFWRixLTDV7xAZCAcW+B5+qc7rHPuXkwCKHFxLor84PJlPoRUV2yEXtyDscg6RT4fk7kFhURqDXl/QIKpayVhDkvGEPhk9l+GWW2cozZfUNQfqEnJ0lAz+E6RF7+M9p2sRVsxg9UYb51ZLIksUic8/wrcRWE6VC2+TEvifBFLYuI9Y9QqRnZMoQQtnnvHqcwL9P6XKjwTOWYQgHb3J3WhCTOwvvJtnQmBiGB/sLpTEpHotCDiEILnoD2PANir7wRWtimk2Tn1WSINF4PTUzP5iuXyP4cRMkrv72rTnc7T66bLG+tKPee0NvwKT0LEbyfGFbhiWhyq/8gWaqYMkaD8Xb/Oj4JPLbZeHb5+6XRxabxcLezjwBJQ5Y/NvOfuFQRuSJWr/193SebIvvvUtPz1VoqxT+sGKy8KXwvcpS3/egpdv9k1/iDxQ/Gt1HUw9LMRr5170c+8Nt81Fh7/2mNMHJRuNkKpv4V4YZ/gOAp4/7J7dPP34b0X9ZxvcOtyeHnMv9azKRRrXwULjSk+/+40yp7/0bUuilCNhlKd+1aWqaO5W9uSdo6t6S4tXGGt7bhFfFjQqJduocbay0LVevkNVDIGng/uVuwLe4eVqgdw+4fWwU/uHpumqYd+sK+63vgGd/mnhrrNinvlaAv/qNPNbMs/eA/VgVC9+jVZ+85f9B9QAWWtmULw/ylMoT9XybHvfVQL3uP3kSXUK+Vq7wWJUOPRBfo6tN4ZWPLpo1gLZ9LgCw44XEjSiuW2zU1CeogRauJyq/fWKXWS/OsMf1AWBjZxvIV/QsrFG6Keh8Xx5Ys6BR40U0FRvt4Yr4ZjTZO7z+z1pnlwPEVk0J8oEOWIQaYiqXvVGPYzkl5k+OIlmhzAtFiGqJWoajh7uM+JPL8KjwYhGjXaJbRkyth4C0XPc9x5lATzDx9+C5rcXGKHZMbHGB10VGsW30kmavXJRKj1hmlP+H4i3TlShwQsrpZRrl9DKNcrpg6CUyzLAzX5ZFf3oM4XOiEug5iyblKE0qFYHWKGPXEiWou5MkgxXzn3zHisyFrUn/oXXDhbQdRolyqS4LxHe8nouSDR8YLSzlbrXY8368dMgvfCiPkR0eQvOebbqzveHg4JVMJYlSr9oInSLV2pwXjO4iELPscGgdHIb5F1FWMXiEfOIc8YbLlFy4FJJEGblOwL054rq1MugnnMa+0GUkscySqZ/B8029Z5O8JlAsmxF6K0pIZBKRD9+PqWj6as3GYLHbH45XYixZcj5nFhWOdmEvU0/KvbRLTn1HXp+YrKzJ1GEC9QCAd8Akzqv1KD8aQ0SYyg+fpDsrp93ja9xYjnnI2VU5dTiN5Jk5ylUhQ6Ubd28/4+j0YV5oygviLIF4IiSIWKDVRGmUjrixOw1mhB0IXT8Rl1Vywv1TTbgGhCZVKNCFrLy8C8eQ+/2doa8ytWqkCuOaMEDARjrmmX16kqsKPqmrvRrI91RAvGoStQFRV9XEifumfd0AnUWHdHgPKbDZ/gEMcbUmTlkHGF8waH8WipC5HFWgspyehibxm3oWUsyrNs6Eqjz6iUXWvXlnTMPxk2+3l7OZHNUjTyazWD/foE7LN99zanQ22VYIo5ZBlKft5yohrZ7A6/BqssRgjfBgDXrBJNUw8RwUSeaIq7LD9iEOD3ne3QeKDBW2oFmyPSuBk+TTjYgNU0F3GW9PM07rsf7RNLONgL5Z7KPHrHGbaBvb8LyLmoOJuhueqJKEjRH08J5mB7cbhVMwTR6Yy4hCy66IbbvyoGeq+Qv0il3YQdlU64wdWIoVYDJDtPKe0YWuXbYE4jC+vIq8WUVBRW9mXPPVFl8FGsYstay/SiiD8XDAn/jZ9u6x9aRbJdg7h8yHilL1bDubETMC2sNPwNDDbbQ08e65pUTpmqzpjQ2w0JwZ4KvecZw8WiFq1kRh7EMgm7TEAd8JObGzCdWxOFbluPTOSKMLnOkaSXpJpEVSgGD99EJIwTRc37C9d2m0A5+IJfX5GHNZoJXDeQZJKaoxddEKqWkcYBjjD/aEwkvBHxzICiqvIu0CUz6P6q9KOYvsgZSuH64uaBy+mDlYlvjorX0OVGg3TWVaW2Soktb+OBXgpXwztQ4qdUEhHn4xdIwxVO6Sn1qJe7zQE7mWuJ1+eQdqaiicyZA2tb7wgG7r11eTGzFSu28nA5K1qwpAAzk2PartBAvXlEJ5fexS6+Omtn2qH1WrPsiMDOKaBcKbqbUuaihw2pgUr+jVEOv0+BVQtC5b3KG9MC5bDFs9bdMNWH+pLvon8dB2Q5KaboIr6JVxzEFwuLiivkaEwP37OfJtm6jfzHx6eAWZN88qF3FMmdY5cqLWyZJ74rkc4I9KoAG3bGeEhTiJmJggsCyrDDoc7YiW0vNoM8Mfp+2ydhW0CJXR1tqC+3+gjE9Z7/cahmHs+iL8Fft0Yv8VV9rmRR5Tol4Z5aJyfSxDeDfEbOGiewft+gTJUVq6NhX8KTW18prveckb14F68ABHXYbwlIRKkYfAeB0tT4FgbYISDNLcozt3JaOHUX2c5iSiRx8fTQY8fTkZR7sGvPPKfEpWVdDH8luoQ1nsetWdF/tO9aNGN4flxRhczBQtbQ8fuYNZOwyB8HpQmmrXtbcJ5eWcc8RFi47uJKVHBbCQkusbCvHUJtJIu1sV1Gwa2XN03d7/02GLRuBnMxXDH3XuC/TsDlAVofwHpmMBQDTxdc5t/mjKn/kPGKKcvf+1Q+tVjfIRVggbBgCAAPvev2MB+/HQWuCtHAL8o97khmxvww8leGzjr+vY0mlZnwTVK6m9AOK6KHRxVstsr9z8/KA8z1RsSE4etE+7VPHXLAbk36wtj6LOstRmEXWcqe6pgIdbqzVD3ZJ7VnqBNV7rrw3/iracuM+S2/Ua/m2FRnYDWLv9RtZqU5/s02Hc4jQcaE/wOnkOunZ/I1+/6j6gwVr/uAv+nnVWRK1k6i4bae87bM3hrvmw3cLPY7VX00ylu0oHg9pIYTU0/3MCA5BrWcptcPsHFYO6Azmfojacg0sNThbYoevZes6BCnB/UdXi+swaNmgM5yPRzdpFUbmVUN5KtT71UIAcCbBomlc1zUKs2G2kOyGopMSFnbZ3WWynqz8MNEF9gdsDutZRUW+mZZOSXgmklobkaKfti9x8wS/7iQihFUOuoRY/it8a3GZKsNkyjgI8j6Clkqq7YN+HoH9EMMKMpekIC7nTML3tH94XAyoCwgAiKx6Q4F6us8YvHwhZWZYuNnRlUAx2BqD/oyyYwSF3Gnb331DkUQc3TJandTcMDnguGYtCT4qgLBuRk6wigIukrzaITcY61hFOFjqfQtgBvgmI+4CeDTjGu9voe+NNbcNLoMPorIthjXHt5371fHKhyLYv0p11SkG0P5H39O+Daa/XxS+lgrb5M6nHotpiQe0RIO9TAI/ITCguuobc8q0ilInLJHgCIGgDjKgi7QI+JBS4kBCeQgkjVUgEX4slksgjiZIS9RKLMM5KtJg0S2zKnC1x8PO85MW3SXleR1WhQES7iZCB4gwxACREySRhbC0lQrxqiRRon0Tp5YvEwsQaidYy7kls+uwscahys+QlPu9yvIObvrlAaRumTCdVHJrV6SisWkxmy256q1MJfqU2/Bpz9HuzXwIAxZwceqlWrwNGEYceOoDig45e1fUdDukXA7DYS1aAvWDbH4CJlp6BUbL6VXdpj2Q7pFoTk7LINEVvMCb/7hoz2TTENMaeOxJhRtdOJ8Qwq6nKJ7pLiA4NNlLZYmUYr14vga3DuumE1XtIwWBpJWqa7JbVqlTr9szqdap1ZAQ+LhsvVoIeigO/ox/7c3xxGaPXz1NVjm6dcokejIGntYmJyb+TW44+H+3auuqORm2cNj42ISEOtlO1QqMQ5sLUrG6cwCpUqqlz11sOdggXJmmZNnhOXWMvbUjtB0wO/d0f+CtfRmEH7Ree5nqfWi3Ywac01l1FUVgMm56dBVLjwzJ2v9psWWbKpcgCVOlGF3WRRCf0DGvz6OnKEexchZhQ1nut5wsdormgB782Ojnq7Vk61Z5uF6JOHeukUq1XGleu4L84a+rKfp53AWD83v4p2GrPbwFKRCASUUCUvh6Vzkzk8mrkE5TNTZeCKKfQfcTLz5eaHw1/jACBggQLESpMuAiRokSLoaU7n+mmSY8TeoK4+GZJkqVIlSZdhkwWWbI1KtlgOK9Gi2r+S2p/x7bXIViw1DDD7TPDSyNMMNZ8qy0LGsa4baipwUYcGG+mUY64HwdggTW++eq7JdY75YQNOupkkgpnVDrptAvOOue8V+yuuOiSjTr7aLLrrrqmyhvvjFajWq16dRos4tBVF04u3XXTQ0+v9dJHb331189Oiw00wCCDvfXebm6b7HHTrTiIeIiPBMgbCRUpVqJUmXIVKtlsi+12OGqrbY4ZaW34sN+BUOnLuFDrp0Z/md55d+8uVYZJu7HARHffW61/hbLFwZl6NdlvMYIi3yTMdWNzDXyDb/RNfqwf58f7CX6ib/YyZwz61MAtPfxeRQdXVeec0ZrFWcl9XjT1uThrVmy10FrkG33TXx27U8/wDxqv4LkoXrsJownrBr9Uj3oHuHdOgTvR+d8xW+i4e9TZHNyNOdw06uU2zLGZrsuoJdIzJprUjA4mOaPUEPfbjTjBhwb4pc2iByh3P8Ol1M30WmIzHS+y+m8lIouGc9b3MnJPIEaenevaL61HZQQpSt7nhtoawtXNUFuneD5YUDofMyifT4Y0ns+FtDs/atF0x06j28KdLAA=';

    var localesJSON = `{"bg":{"facebook.json":{"informationalModalMessageTitle":"При влизане разрешавате на Facebook да Ви проследява","informationalModalMessageBody":"След като влезете, DuckDuckGo не може да блокира проследяването от Facebook в съдържанието на този сайт.","informationalModalConfirmButtonText":"Вход","informationalModalRejectButtonText":"Назад","loginButtonText":"Вход във Facebook","loginBodyText":"Facebook проследява Вашата активност в съответния сайт, когато го използвате за вход.","buttonTextUnblockContent":"Разблокиране на съдържанието","buttonTextUnblockComment":"Разблокиране на коментара","buttonTextUnblockComments":"Разблокиране на коментарите","buttonTextUnblockPost":"Разблокиране на публикацията","buttonTextUnblockVideo":"Разблокиране на видеото","infoTitleUnblockContent":"DuckDuckGo блокира това съдържание, за да предотврати проследяване от Facebook","infoTitleUnblockComment":"DuckDuckGo блокира този коментар, за да предотврати проследяване от Facebook","infoTitleUnblockComments":"DuckDuckGo блокира тези коментари, за да предотврати проследяване от Facebook","infoTitleUnblockPost":"DuckDuckGo блокира тази публикация, за да предотврати проследяване от Facebook","infoTitleUnblockVideo":"DuckDuckGo блокира това видео, за да предотврати проследяване от Facebook","infoTextUnblockContent":"Блокирахме проследяването от Facebook при зареждане на страницата. Ако разблокирате това съдържание, Facebook ще следи Вашата активност."},"shared.json":{"learnMore":"Научете повече","readAbout":"Прочетете за тази защита на поверителността"},"youtube.json":{"informationalModalMessageTitle":"Активиране на всички прегледи в YouTube?","informationalModalMessageBody":"Показването на преглед позволява на Google (собственик на YouTube) да види част от информацията за Вашето устройство, но все пак осигурява повече поверителност отколкото при възпроизвеждане на видеоклипа.","informationalModalConfirmButtonText":"Активиране на всички прегледи","informationalModalRejectButtonText":"Не, благодаря","buttonTextUnblockVideo":"Разблокиране на видеото","infoTitleUnblockVideo":"DuckDuckGo блокира този видеоклип в YouTube, за да предотврати проследяване от Google","infoTextUnblockVideo":"Блокирахме проследяването от Google (собственик на YouTube) при зареждане на страницата. Ако разблокирате този видеоклип, Google ще следи Вашата активност.","infoPreviewToggleText":"Прегледите са деактивирани за осигуряване на допълнителна поверителност","infoPreviewToggleEnabledText":"Прегледите са активирани","infoPreviewInfoText":"Изключете прегледите, за да получите допълнителна поверителност от DuckDuckGo."}},"cs":{"facebook.json":{"informationalModalMessageTitle":"Když se přihlásíš přes Facebook, bude tě moct sledovat","informationalModalMessageBody":"Po přihlášení už DuckDuckGo nemůže bránit Facebooku, aby tě na téhle stránce sledoval.","informationalModalConfirmButtonText":"Přihlásit se","informationalModalRejectButtonText":"Zpět","loginButtonText":"Přihlásit se pomocí Facebooku","loginBodyText":"Facebook sleduje tvou aktivitu na webu, když se přihlásíš jeho prostřednictvím.","buttonTextUnblockContent":"Odblokovat obsah","buttonTextUnblockComment":"Odblokovat komentář","buttonTextUnblockComments":"Odblokovat komentáře","buttonTextUnblockPost":"Odblokovat příspěvek","buttonTextUnblockVideo":"Odblokovat video","infoTitleUnblockContent":"DuckDuckGo zablokoval tenhle obsah, aby Facebooku zabránil tě sledovat","infoTitleUnblockComment":"Služba DuckDuckGo zablokovala tento komentář, aby Facebooku zabránila ve tvém sledování","infoTitleUnblockComments":"Služba DuckDuckGo zablokovala tyto komentáře, aby Facebooku zabránila ve tvém sledování","infoTitleUnblockPost":"DuckDuckGo zablokoval tenhle příspěvek, aby Facebooku zabránil tě sledovat","infoTitleUnblockVideo":"DuckDuckGo zablokoval tohle video, aby Facebooku zabránil tě sledovat","infoTextUnblockContent":"Při načítání stránky jsme Facebooku zabránili, aby tě sledoval. Když tenhle obsah odblokuješ, Facebook bude mít přístup ke tvé aktivitě."},"shared.json":{"learnMore":"Více informací","readAbout":"Přečti si o téhle ochraně soukromí"},"youtube.json":{"informationalModalMessageTitle":"Zapnout všechny náhledy YouTube?","informationalModalMessageBody":"Zobrazování náhledů umožní společnosti Google (která vlastní YouTube) zobrazit některé informace o tvém zařízení, ale pořád jde o diskrétnější volbu, než je přehrávání videa.","informationalModalConfirmButtonText":"Zapnout všechny náhledy","informationalModalRejectButtonText":"Ne, děkuji","buttonTextUnblockVideo":"Odblokovat video","infoTitleUnblockVideo":"DuckDuckGo zablokoval tohle video z YouTube, aby Googlu zabránil tě sledovat","infoTextUnblockVideo":"Zabránili jsme společnosti Google (která vlastní YouTube), aby tě při načítání stránky sledovala. Pokud toto video odblokuješ, Google získá přístup ke tvé aktivitě.","infoPreviewToggleText":"Náhledy jsou pro větší soukromí vypnuté","infoPreviewToggleEnabledText":"Náhledy jsou zapnuté","infoPreviewInfoText":"Vypnutím náhledů ti DuckDuckGo zajistí víc soukromí."}},"da":{"facebook.json":{"informationalModalMessageTitle":"Når du logger ind med Facebook, kan de spore dig","informationalModalMessageBody":"Når du er logget ind, kan DuckDuckGo ikke blokere for, at indhold fra Facebook sporer dig på dette websted.","informationalModalConfirmButtonText":"Log på","informationalModalRejectButtonText":"Gå tilbage","loginButtonText":"Log ind med Facebook","loginBodyText":"Facebook sporer din aktivitet på et websted, når du bruger dem til at logge ind.","buttonTextUnblockContent":"Fjern blokering af indhold","buttonTextUnblockComment":"Fjern blokering af kommentar","buttonTextUnblockComments":"Fjern blokering af kommentarer","buttonTextUnblockPost":"Fjern blokering af indlæg","buttonTextUnblockVideo":"Fjern blokering af video","infoTitleUnblockContent":"DuckDuckGo har blokeret dette indhold for at forhindre Facebook i at spore dig","infoTitleUnblockComment":"DuckDuckGo har blokeret denne kommentar for at forhindre Facebook i at spore dig","infoTitleUnblockComments":"DuckDuckGo har blokeret disse kommentarer for at forhindre Facebook i at spore dig","infoTitleUnblockPost":"DuckDuckGo blokerede dette indlæg for at forhindre Facebook i at spore dig","infoTitleUnblockVideo":"DuckDuckGo har blokeret denne video for at forhindre Facebook i at spore dig","infoTextUnblockContent":"Vi blokerede for, at Facebook sporede dig, da siden blev indlæst. Hvis du ophæver blokeringen af dette indhold, vil Facebook kende din aktivitet."},"shared.json":{"learnMore":"Mere info","readAbout":"Læs om denne beskyttelse af privatlivet"},"youtube.json":{"informationalModalMessageTitle":"Vil du aktivere alle YouTube-forhåndsvisninger?","informationalModalMessageBody":"Med forhåndsvisninger kan Google (som ejer YouTube) se nogle af enhedens oplysninger, men det er stadig mere privat end at afspille videoen.","informationalModalConfirmButtonText":"Aktivér alle forhåndsvisninger","informationalModalRejectButtonText":"Nej tak.","buttonTextUnblockVideo":"Fjern blokering af video","infoTitleUnblockVideo":"DuckDuckGo har blokeret denne YouTube-video for at forhindre Google i at spore dig","infoTextUnblockVideo":"Vi blokerede Google (som ejer YouTube) fra at spore dig, da siden blev indlæst. Hvis du fjerner blokeringen af denne video, vil Google få kendskab til din aktivitet.","infoPreviewToggleText":"Forhåndsvisninger er deaktiveret for at give yderligere privatliv","infoPreviewToggleEnabledText":"Forhåndsvisninger er deaktiveret","infoPreviewInfoText":"Slå forhåndsvisninger fra for at få yderligere privatliv fra DuckDuckGo."}},"de":{"facebook.json":{"informationalModalMessageTitle":"Wenn du dich bei Facebook anmeldest, kann Facebook dich tracken","informationalModalMessageBody":"Sobald du angemeldet bist, kann DuckDuckGo nicht mehr verhindern, dass Facebook-Inhalte dich auf dieser Website tracken.","informationalModalConfirmButtonText":"Anmelden","informationalModalRejectButtonText":"Zurück","loginButtonText":"Mit Facebook anmelden","loginBodyText":"Facebook trackt deine Aktivität auf einer Website, wenn du dich über Facebook dort anmeldest.","buttonTextUnblockContent":"Blockierung aufheben","buttonTextUnblockComment":"Blockierung aufheben","buttonTextUnblockComments":"Blockierung aufheben","buttonTextUnblockPost":"Blockierung aufheben","buttonTextUnblockVideo":"Blockierung aufheben","infoTitleUnblockContent":"DuckDuckGo hat diesen Inhalt blockiert, um zu verhindern, dass Facebook dich trackt","infoTitleUnblockComment":"DuckDuckGo hat diesen Kommentar blockiert, um zu verhindern, dass Facebook dich trackt","infoTitleUnblockComments":"DuckDuckGo hat diese Kommentare blockiert, um zu verhindern, dass Facebook dich trackt","infoTitleUnblockPost":"DuckDuckGo hat diesen Beitrag blockiert, um zu verhindern, dass Facebook dich trackt","infoTitleUnblockVideo":"DuckDuckGo hat dieses Video blockiert, um zu verhindern, dass Facebook dich trackt","infoTextUnblockContent":"Wir haben Facebook daran gehindert, dich zu tracken, als die Seite geladen wurde. Wenn du die Blockierung für diesen Inhalt aufhebst, kennt Facebook deine Aktivitäten."},"shared.json":{"learnMore":"Mehr erfahren","readAbout":"Weitere Informationen über diesen Datenschutz"},"youtube.json":{"informationalModalMessageTitle":"Alle YouTube-Vorschauen aktivieren?","informationalModalMessageBody":"Durch das Anzeigen von Vorschauen kann Google (dem YouTube gehört) einige Informationen zu deinem Gerät sehen. Dies ist aber immer noch privater als das Abspielen des Videos.","informationalModalConfirmButtonText":"Alle Vorschauen aktivieren","informationalModalRejectButtonText":"Nein, danke","buttonTextUnblockVideo":"Blockierung aufheben","infoTitleUnblockVideo":"DuckDuckGo hat dieses YouTube-Video blockiert, um zu verhindern, dass Google dich trackt.","infoTextUnblockVideo":"Wir haben Google (dem YouTube gehört) daran gehindert, dich beim Laden der Seite zu tracken. Wenn du die Blockierung für dieses Video aufhebst, kennt Google deine Aktivitäten.","infoPreviewToggleText":"Vorschau für mehr Privatsphäre deaktiviert","infoPreviewToggleEnabledText":"Vorschau aktiviert","infoPreviewInfoText":"Deaktiviere Vorschauen für zusätzlichen Datenschutz von DuckDuckGo."}},"el":{"facebook.json":{"informationalModalMessageTitle":"Η σύνδεση μέσω Facebook τους επιτρέπει να σας παρακολουθούν","informationalModalMessageBody":"Μόλις συνδεθείτε, το DuckDuckGo δεν μπορεί να εμποδίσει το περιεχόμενο του Facebook από το να σας παρακολουθεί σε αυτόν τον ιστότοπο.","informationalModalConfirmButtonText":"Σύνδεση","informationalModalRejectButtonText":"Επιστροφή","loginButtonText":"Σύνδεση μέσω Facebook","loginBodyText":"Το Facebook παρακολουθεί τη δραστηριότητά σας σε έναν ιστότοπο όταν τον χρησιμοποιείτε για να συνδεθείτε.","buttonTextUnblockContent":"Άρση αποκλεισμού περιεχομένου","buttonTextUnblockComment":"Άρση αποκλεισμού σχολίου","buttonTextUnblockComments":"Άρση αποκλεισμού σχολίων","buttonTextUnblockPost":"Άρση αποκλεισμού ανάρτησης","buttonTextUnblockVideo":"Άρση αποκλεισμού βίντεο","infoTitleUnblockContent":"Το DuckDuckGo απέκλεισε το περιεχόμενο αυτό για να εμποδίσει το Facebook από το να σας παρακολουθεί","infoTitleUnblockComment":"Το DuckDuckGo απέκλεισε το σχόλιο αυτό για να εμποδίσει το Facebook από το να σας παρακολουθεί","infoTitleUnblockComments":"Το DuckDuckGo απέκλεισε τα σχόλια αυτά για να εμποδίσει το Facebook από το να σας παρακολουθεί","infoTitleUnblockPost":"Το DuckDuckGo απέκλεισε την ανάρτηση αυτή για να εμποδίσει το Facebook από το να σας παρακολουθεί","infoTitleUnblockVideo":"Το DuckDuckGo απέκλεισε το βίντεο αυτό για να εμποδίσει το Facebook από το να σας παρακολουθεί","infoTextUnblockContent":"Αποκλείσαμε το Facebook από το να σας παρακολουθεί όταν φορτώθηκε η σελίδα. Εάν κάνετε άρση αποκλεισμού γι' αυτό το περιεχόμενο, το Facebook θα γνωρίζει τη δραστηριότητά σας."},"shared.json":{"learnMore":"Μάθετε περισσότερα","readAbout":"Διαβάστε σχετικά με την παρούσα προστασίας προσωπικών δεδομένων"},"youtube.json":{"informationalModalMessageTitle":"Ενεργοποίηση όλων των προεπισκοπήσεων του YouTube;","informationalModalMessageBody":"Η προβολή των προεπισκοπήσεων θα επιτρέψει στην Google (στην οποία ανήκει το YouTube) να βλέπει ορισμένες από τις πληροφορίες της συσκευής σας, ωστόσο εξακολουθεί να είναι πιο ιδιωτική από την αναπαραγωγή του βίντεο.","informationalModalConfirmButtonText":"Ενεργοποίηση όλων των προεπισκοπήσεων","informationalModalRejectButtonText":"Όχι, ευχαριστώ","buttonTextUnblockVideo":"Άρση αποκλεισμού βίντεο","infoTitleUnblockVideo":"Το DuckDuckGo απέκλεισε το βίντεο αυτό στο YouTube για να εμποδίσει την Google από το να σας παρακολουθεί","infoTextUnblockVideo":"Αποκλείσαμε την Google (στην οποία ανήκει το YouTube) από το να σας παρακολουθεί όταν φορτώθηκε η σελίδα. Εάν κάνετε άρση αποκλεισμού γι' αυτό το βίντεο, η Google θα γνωρίζει τη δραστηριότητά σας.","infoPreviewToggleText":"Οι προεπισκοπήσεις απενεργοποιήθηκαν για πρόσθετη προστασία των προσωπικών δεδομένων","infoPreviewToggleEnabledText":"Οι προεπισκοπήσεις ενεργοποιήθηκαν","infoPreviewInfoText":"Απενεργοποιήστε τις προεπισκοπήσεις για πρόσθετη προστασία των προσωπικών δεδομένων από το DuckDuckGo."}},"en":{"facebook.json":{"informationalModalMessageTitle":"Logging in with Facebook lets them track you","informationalModalMessageBody":"Once you're logged in, DuckDuckGo can't block Facebook content from tracking you on this site.","informationalModalConfirmButtonText":"Log In","informationalModalRejectButtonText":"Go back","loginButtonText":"Log in with Facebook","loginBodyText":"Facebook tracks your activity on a site when you use them to login.","buttonTextUnblockContent":"Unblock Content","buttonTextUnblockComment":"Unblock Comment","buttonTextUnblockComments":"Unblock Comments","buttonTextUnblockPost":"Unblock Post","buttonTextUnblockVideo":"Unblock Video","infoTitleUnblockContent":"DuckDuckGo blocked this content to prevent Facebook from tracking you","infoTitleUnblockComment":"DuckDuckGo blocked this comment to prevent Facebook from tracking you","infoTitleUnblockComments":"DuckDuckGo blocked these comments to prevent Facebook from tracking you","infoTitleUnblockPost":"DuckDuckGo blocked this post to prevent Facebook from tracking you","infoTitleUnblockVideo":"DuckDuckGo blocked this video to prevent Facebook from tracking you","infoTextUnblockContent":"We blocked Facebook from tracking you when the page loaded. If you unblock this content, Facebook will know your activity."},"shared.json":{"learnMore":"Learn More","readAbout":"Read about this privacy protection"},"youtube.json":{"informationalModalMessageTitle":"Enable all YouTube previews?","informationalModalMessageBody":"Showing previews will allow Google (which owns YouTube) to see some of your device’s information, but is still more private than playing the video.","informationalModalConfirmButtonText":"Enable All Previews","informationalModalRejectButtonText":"No Thanks","buttonTextUnblockVideo":"Unblock Video","infoTitleUnblockVideo":"DuckDuckGo blocked this YouTube video to prevent Google from tracking you","infoTextUnblockVideo":"We blocked Google (which owns YouTube) from tracking you when the page loaded. If you unblock this video, Google will know your activity.","infoPreviewToggleText":"Previews disabled for additional privacy","infoPreviewToggleEnabledText":"Previews enabled","infoPreviewInfoText":"Turn previews off for additional privacy from DuckDuckGo."}},"es":{"facebook.json":{"informationalModalMessageTitle":"Al iniciar sesión en Facebook, les permites que te rastreen","informationalModalMessageBody":"Una vez que hayas iniciado sesión, DuckDuckGo no puede bloquear el contenido de Facebook para que no te rastree en este sitio.","informationalModalConfirmButtonText":"Iniciar sesión","informationalModalRejectButtonText":"Volver atrás","loginButtonText":"Iniciar sesión con Facebook","loginBodyText":"Facebook rastrea tu actividad en un sitio web cuando lo usas para iniciar sesión.","buttonTextUnblockContent":"Desbloquear contenido","buttonTextUnblockComment":"Desbloquear comentario","buttonTextUnblockComments":"Desbloquear comentarios","buttonTextUnblockPost":"Desbloquear publicación","buttonTextUnblockVideo":"Desbloquear vídeo","infoTitleUnblockContent":"DuckDuckGo ha bloqueado este contenido para evitar que Facebook te rastree","infoTitleUnblockComment":"DuckDuckGo ha bloqueado este comentario para evitar que Facebook te rastree","infoTitleUnblockComments":"DuckDuckGo ha bloqueado estos comentarios para evitar que Facebook te rastree","infoTitleUnblockPost":"DuckDuckGo ha bloqueado esta publicación para evitar que Facebook te rastree","infoTitleUnblockVideo":"DuckDuckGo ha bloqueado este vídeo para evitar que Facebook te rastree","infoTextUnblockContent":"Hemos bloqueado el rastreo de Facebook cuando se ha cargado la página. Si desbloqueas este contenido, Facebook tendrá conocimiento de tu actividad."},"shared.json":{"learnMore":"Más información","readAbout":"Lee acerca de esta protección de privacidad"},"youtube.json":{"informationalModalMessageTitle":"¿Habilitar todas las vistas previas de YouTube?","informationalModalMessageBody":"Mostrar vistas previas permitirá a Google (que es el propietario de YouTube) ver parte de la información de tu dispositivo, pero sigue siendo más privado que reproducir el vídeo.","informationalModalConfirmButtonText":"Habilitar todas las vistas previas","informationalModalRejectButtonText":"No, gracias","buttonTextUnblockVideo":"Desbloquear vídeo","infoTitleUnblockVideo":"DuckDuckGo ha bloqueado este vídeo de YouTube para evitar que Google te rastree","infoTextUnblockVideo":"Hemos bloqueado el rastreo de Google (que es el propietario de YouTube) al cargarse la página. Si desbloqueas este vídeo, Goggle tendrá conocimiento de tu actividad.","infoPreviewToggleText":"Vistas previas desactivadas para mayor privacidad","infoPreviewToggleEnabledText":"Vistas previas activadas","infoPreviewInfoText":"Desactiva las vistas previas para obtener más privacidad de DuckDuckGo."}},"et":{"facebook.json":{"informationalModalMessageTitle":"Kui logid Facebookiga sisse, saab Facebook sind jälgida","informationalModalMessageBody":"Kui oled sisse logitud, ei saa DuckDuckGo blokeerida Facebooki sisu sind jälgimast.","informationalModalConfirmButtonText":"Logi sisse","informationalModalRejectButtonText":"Mine tagasi","loginButtonText":"Logi sisse Facebookiga","loginBodyText":"Kui logid sisse Facebookiga, saab Facebook sinu tegevust saidil jälgida.","buttonTextUnblockContent":"Deblokeeri sisu","buttonTextUnblockComment":"Deblokeeri kommentaar","buttonTextUnblockComments":"Deblokeeri kommentaarid","buttonTextUnblockPost":"Deblokeeri postitus","buttonTextUnblockVideo":"Deblokeeri video","infoTitleUnblockContent":"DuckDuckGo blokeeris selle sisu, et Facebook ei saaks sind jälgida","infoTitleUnblockComment":"DuckDuckGo blokeeris selle kommentaari, et Facebook ei saaks sind jälgida","infoTitleUnblockComments":"DuckDuckGo blokeeris need kommentaarid, et Facebook ei saaks sind jälgida","infoTitleUnblockPost":"DuckDuckGo blokeeris selle postituse, et Facebook ei saaks sind jälgida","infoTitleUnblockVideo":"DuckDuckGo blokeeris selle video, et Facebook ei saaks sind jälgida","infoTextUnblockContent":"Blokeerisime lehe laadimise ajal Facebooki jaoks sinu jälgimise. Kui sa selle sisu deblokeerid, saab Facebook sinu tegevust jälgida."},"shared.json":{"learnMore":"Loe edasi","readAbout":"Loe selle privaatsuskaitse kohta"},"youtube.json":{"informationalModalMessageTitle":"Kas lubada kõik YouTube’i eelvaated?","informationalModalMessageBody":"Eelvaate näitamine võimaldab Google’il (kellele YouTube kuulub) näha osa sinu seadme teabest, kuid see on siiski privaatsem kui video esitamine.","informationalModalConfirmButtonText":"Luba kõik eelvaated","informationalModalRejectButtonText":"Ei aitäh","buttonTextUnblockVideo":"Deblokeeri video","infoTitleUnblockVideo":"DuckDuckGo blokeeris selle YouTube’i video, et takistada Google’it sind jälgimast","infoTextUnblockVideo":"Me blokeerisime lehe laadimise ajal Google’i (kellele YouTube kuulub) jälgimise. Kui sa selle video deblokeerid, saab Google sinu tegevusest teada.","infoPreviewToggleText":"Eelvaated on täiendava privaatsuse tagamiseks keelatud","infoPreviewToggleEnabledText":"Eelvaated on lubatud","infoPreviewInfoText":"Lülita DuckDuckGo täiendava privaatsuse tagamiseks eelvaated välja."}},"fi":{"facebook.json":{"informationalModalMessageTitle":"Kun kirjaudut sisään Facebook-tunnuksilla, Facebook voi seurata sinua","informationalModalMessageBody":"Kun olet kirjautunut sisään, DuckDuckGo ei voi estää Facebook-sisältöä seuraamasta sinua tällä sivustolla.","informationalModalConfirmButtonText":"Kirjaudu sisään","informationalModalRejectButtonText":"Edellinen","loginButtonText":"Kirjaudu sisään Facebook-tunnuksilla","loginBodyText":"Facebook seuraa toimintaasi sivustolla, kun kirjaudut sisään sen kautta.","buttonTextUnblockContent":"Poista sisällön esto","buttonTextUnblockComment":"Poista kommentin esto","buttonTextUnblockComments":"Poista kommenttien esto","buttonTextUnblockPost":"Poista julkaisun esto","buttonTextUnblockVideo":"Poista videon esto","infoTitleUnblockContent":"DuckDuckGo esti tämän sisällön estääkseen Facebookia seuraamasta sinua","infoTitleUnblockComment":"DuckDuckGo esti tämän kommentin estääkseen Facebookia seuraamasta sinua","infoTitleUnblockComments":"DuckDuckGo esti nämä kommentit estääkseen Facebookia seuraamasta sinua","infoTitleUnblockPost":"DuckDuckGo esti tämän julkaisun estääkseen Facebookia seuraamasta sinua","infoTitleUnblockVideo":"DuckDuckGo esti tämän videon estääkseen Facebookia seuraamasta sinua","infoTextUnblockContent":"Estimme Facebookia seuraamasta sinua, kun sivua ladattiin. Jos poistat tämän sisällön eston, Facebook saa tietää toimintasi."},"shared.json":{"learnMore":"Lue lisää","readAbout":"Lue tästä yksityisyydensuojasta"},"youtube.json":{"informationalModalMessageTitle":"Otetaanko käyttöön kaikki YouTube-esikatselut?","informationalModalMessageBody":"Kun sallit esikatselun, Google (joka omistaa YouTuben) voi nähdä joitakin laitteesi tietoja, mutta se on silti yksityisempää kuin videon toistaminen.","informationalModalConfirmButtonText":"Ota käyttöön kaikki esikatselut","informationalModalRejectButtonText":"Ei kiitos","buttonTextUnblockVideo":"Poista videon esto","infoTitleUnblockVideo":"DuckDuckGo esti tämän YouTube-videon, jotta Google ei voi seurata sinua","infoTextUnblockVideo":"Estimme Googlea (joka omistaa YouTuben) seuraamasta sinua, kun sivua ladattiin. Jos poistat tämän videon eston, Google tietää toimintasi.","infoPreviewToggleText":"Esikatselut on poistettu käytöstä yksityisyyden lisäämiseksi","infoPreviewToggleEnabledText":"Esikatselut käytössä","infoPreviewInfoText":"Poista esikatselut käytöstä saadaksesi paremman yksityisyyden DuckDuckGolta."}},"fr":{"facebook.json":{"informationalModalMessageTitle":"L'identification via Facebook leur permet de vous pister","informationalModalMessageBody":"Une fois que vous êtes connecté(e), DuckDuckGo ne peut pas empêcher le contenu Facebook de vous pister sur ce site.","informationalModalConfirmButtonText":"Connexion","informationalModalRejectButtonText":"Revenir en arrière","loginButtonText":"S'identifier avec Facebook","loginBodyText":"Facebook piste votre activité sur un site lorsque vous l'utilisez pour vous identifier.","buttonTextUnblockContent":"Débloquer le contenu","buttonTextUnblockComment":"Débloquer le commentaire","buttonTextUnblockComments":"Débloquer les commentaires","buttonTextUnblockPost":"Débloquer la publication","buttonTextUnblockVideo":"Débloquer la vidéo","infoTitleUnblockContent":"DuckDuckGo a bloqué ce contenu pour empêcher Facebook de vous suivre","infoTitleUnblockComment":"DuckDuckGo a bloqué ce commentaire pour empêcher Facebook de vous suivre","infoTitleUnblockComments":"DuckDuckGo a bloqué ces commentaires pour empêcher Facebook de vous suivre","infoTitleUnblockPost":"DuckDuckGo a bloqué cette publication pour empêcher Facebook de vous pister","infoTitleUnblockVideo":"DuckDuckGo a bloqué cette vidéo pour empêcher Facebook de vous pister","infoTextUnblockContent":"Nous avons empêché Facebook de vous pister lors du chargement de la page. Si vous débloquez ce contenu, Facebook connaîtra votre activité."},"shared.json":{"learnMore":"En savoir plus","readAbout":"En savoir plus sur cette protection de la confidentialité"},"youtube.json":{"informationalModalMessageTitle":"Activer tous les aperçus YouTube ?","informationalModalMessageBody":"L'affichage des aperçus permettra à Google (propriétaire de YouTube) de voir certaines informations de votre appareil, mais cela reste davantage confidentiel qu'en lisant la vidéo.","informationalModalConfirmButtonText":"Activer tous les aperçus","informationalModalRejectButtonText":"Non merci","buttonTextUnblockVideo":"Débloquer la vidéo","infoTitleUnblockVideo":"DuckDuckGo a bloqué cette vidéo YouTube pour empêcher Google de vous pister","infoTextUnblockVideo":"Nous avons empêché Google (propriétaire de YouTube) de vous pister lors du chargement de la page. Si vous débloquez cette vidéo, Google connaîtra votre activité.","infoPreviewToggleText":"Aperçus désactivés pour plus de confidentialité","infoPreviewToggleEnabledText":"Aperçus activés","infoPreviewInfoText":"Désactivez les aperçus pour plus de confidentialité grâce à DuckDuckGo."}},"hr":{"facebook.json":{"informationalModalMessageTitle":"Prijava putem Facebooka omogućuje im da te prate","informationalModalMessageBody":"Nakon što se prijaviš, DuckDuckGo ne može blokirati Facebookov sadržaj da te prati na Facebooku.","informationalModalConfirmButtonText":"Prijavljivanje","informationalModalRejectButtonText":"Vrati se","loginButtonText":"Prijavi se putem Facebooka","loginBodyText":"Facebook prati tvoju aktivnost na toj web lokaciji kad je koristiš za prijavu.","buttonTextUnblockContent":"Deblokiranje sadržaja","buttonTextUnblockComment":"Deblokiranje komentara","buttonTextUnblockComments":"Deblokiranje komentara","buttonTextUnblockPost":"Deblokiranje objave","buttonTextUnblockVideo":"Deblokiranje videozapisa","infoTitleUnblockContent":"DuckDuckGo je blokirao ovaj sadržaj kako bi spriječio Facebook da te prati","infoTitleUnblockComment":"DuckDuckGo je blokirao ovaj komentar kako bi spriječio Facebook da te prati","infoTitleUnblockComments":"DuckDuckGo je blokirao ove komentare kako bi spriječio Facebook da te prati","infoTitleUnblockPost":"DuckDuckGo je blokirao ovu objavu kako bi spriječio Facebook da te prati","infoTitleUnblockVideo":"DuckDuckGo je blokirao ovaj video kako bi spriječio Facebook da te prati","infoTextUnblockContent":"Blokirali smo Facebook da te prati kad se stranica učita. Ako deblokiraš ovaj sadržaj, Facebook će znati tvoju aktivnost."},"shared.json":{"learnMore":"Saznajte više","readAbout":"Pročitaj više o ovoj zaštiti privatnosti"},"youtube.json":{"informationalModalMessageTitle":"Omogućiti sve YouTube pretpreglede?","informationalModalMessageBody":"Prikazivanje pretpregleda omogućit će Googleu (u čijem je vlasništvu YouTube) da vidi neke podatke o tvom uređaju, ali je i dalje privatnija opcija od reprodukcije videozapisa.","informationalModalConfirmButtonText":"Omogući sve pretpreglede","informationalModalRejectButtonText":"Ne, hvala","buttonTextUnblockVideo":"Deblokiranje videozapisa","infoTitleUnblockVideo":"DuckDuckGo je blokirao ovaj YouTube videozapis kako bi spriječio Google da te prati","infoTextUnblockVideo":"Blokirali smo Google (u čijem je vlasništvu YouTube) da te prati kad se stranica učita. Ako deblokiraš ovaj videozapis, Google će znati tvoju aktivnost.","infoPreviewToggleText":"Pretpregledi su onemogućeni radi dodatne privatnosti","infoPreviewToggleEnabledText":"Pretpregledi su omogućeni","infoPreviewInfoText":"Isključivanjem pretpregleda DuckDuckGo ti omogućava dodatnu privatnost."}},"hu":{"facebook.json":{"informationalModalMessageTitle":"A Facebookkal való bejelentkezéskor a Facebook nyomon követhet","informationalModalMessageBody":"Miután bejelentkezel, a DuckDuckGo nem fogja tudni blokkolni a Facebook-tartalmat, amely nyomon követ ezen az oldalon.","informationalModalConfirmButtonText":"Bejelentkezés","informationalModalRejectButtonText":"Visszalépés","loginButtonText":"Bejelentkezés Facebookkal","loginBodyText":"Ha a Facebookkal jelentkezel be, nyomon követik a webhelyen végzett tevékenységedet.","buttonTextUnblockContent":"Tartalom feloldása","buttonTextUnblockComment":"Hozzászólás feloldása","buttonTextUnblockComments":"Hozzászólások feloldása","buttonTextUnblockPost":"Bejegyzés feloldása","buttonTextUnblockVideo":"Videó feloldása","infoTitleUnblockContent":"A DuckDuckGo blokkolta ezt a tartalmat, hogy megakadályozza a Facebookot a nyomon követésedben","infoTitleUnblockComment":"A DuckDuckGo blokkolta ezt a hozzászólást, hogy megakadályozza a Facebookot a nyomon követésedben","infoTitleUnblockComments":"A DuckDuckGo blokkolta ezeket a hozzászólásokat, hogy megakadályozza a Facebookot a nyomon követésedben","infoTitleUnblockPost":"A DuckDuckGo blokkolta ezt a bejegyzést, hogy megakadályozza a Facebookot a nyomon követésedben","infoTitleUnblockVideo":"A DuckDuckGo blokkolta ezt a videót, hogy megakadályozza a Facebookot a nyomon követésedben","infoTextUnblockContent":"Az oldal betöltésekor blokkoltuk a Facebookot a nyomon követésedben. Ha feloldod ezt a tartalmat, a Facebook tudni fogja, hogy milyen tevékenységet végzel."},"shared.json":{"learnMore":"További részletek","readAbout":"Tudj meg többet erről az adatvédelemről"},"youtube.json":{"informationalModalMessageTitle":"Engedélyezed minden YouTube-videó előnézetét?","informationalModalMessageBody":"Az előnézetek megjelenítésével a Google (a YouTube tulajdonosa) láthatja a készülék néhány adatát, de ez adatvédelmi szempontból még mindig előnyösebb, mint a videó lejátszása.","informationalModalConfirmButtonText":"Minden előnézet engedélyezése","informationalModalRejectButtonText":"Nem, köszönöm","buttonTextUnblockVideo":"Videó feloldása","infoTitleUnblockVideo":"A DuckDuckGo blokkolta a YouTube-videót, hogy a Google ne követhessen nyomon","infoTextUnblockVideo":"Blokkoltuk, hogy a Google (a YouTube tulajdonosa) nyomon követhessen az oldal betöltésekor. Ha feloldod a videó blokkolását, a Google tudni fogja, hogy milyen tevékenységet végzel.","infoPreviewToggleText":"Az előnézetek a fokozott adatvédelem érdekében letiltva","infoPreviewToggleEnabledText":"Az előnézetek engedélyezve","infoPreviewInfoText":"A DuckDuckGo fokozott adatvédelme érdekében kapcsold ki az előnézeteket."}},"it":{"facebook.json":{"informationalModalMessageTitle":"L'accesso con Facebook consente di tracciarti","informationalModalMessageBody":"Dopo aver effettuato l'accesso, DuckDuckGo non può bloccare il tracciamento dei contenuti di Facebook su questo sito.","informationalModalConfirmButtonText":"Accedi","informationalModalRejectButtonText":"Torna indietro","loginButtonText":"Accedi con Facebook","loginBodyText":"Facebook tiene traccia della tua attività su un sito quando lo usi per accedere.","buttonTextUnblockContent":"Sblocca contenuti","buttonTextUnblockComment":"Sblocca commento","buttonTextUnblockComments":"Sblocca commenti","buttonTextUnblockPost":"Sblocca post","buttonTextUnblockVideo":"Sblocca video","infoTitleUnblockContent":"DuckDuckGo ha bloccato questo contenuto per impedire a Facebook di tracciarti","infoTitleUnblockComment":"DuckDuckGo ha bloccato questo commento per impedire a Facebook di tracciarti","infoTitleUnblockComments":"DuckDuckGo ha bloccato questi commenti per impedire a Facebook di tracciarti","infoTitleUnblockPost":"DuckDuckGo ha bloccato questo post per impedire a Facebook di tracciarti","infoTitleUnblockVideo":"DuckDuckGo ha bloccato questo video per impedire a Facebook di tracciarti","infoTextUnblockContent":"Abbiamo impedito a Facebook di tracciarti al caricamento della pagina. Se sblocchi questo contenuto, Facebook conoscerà la tua attività."},"shared.json":{"learnMore":"Ulteriori informazioni","readAbout":"Leggi di più su questa protezione della privacy"},"youtube.json":{"informationalModalMessageTitle":"Abilitare tutte le anteprime di YouTube?","informationalModalMessageBody":"La visualizzazione delle anteprime consentirà a Google (che possiede YouTube) di vedere alcune delle informazioni del tuo dispositivo, ma è comunque più privato rispetto alla riproduzione del video.","informationalModalConfirmButtonText":"Abilita tutte le anteprime","informationalModalRejectButtonText":"No, grazie","buttonTextUnblockVideo":"Sblocca video","infoTitleUnblockVideo":"DuckDuckGo ha bloccato questo video di YouTube per impedire a Google di tracciarti","infoTextUnblockVideo":"Abbiamo impedito a Google (che possiede YouTube) di tracciarti quando la pagina è stata caricata. Se sblocchi questo video, Google conoscerà la tua attività.","infoPreviewToggleText":"Anteprime disabilitate per una maggiore privacy","infoPreviewToggleEnabledText":"Anteprime abilitate","infoPreviewInfoText":"Disabilita le anteprime per una maggiore privacy da DuckDuckGo."}},"lt":{"facebook.json":{"informationalModalMessageTitle":"Prisijungę prie „Facebook“ galite būti sekami","informationalModalMessageBody":"Kai esate prisijungę, „DuckDuckGo“ negali užblokuoti „Facebook“ turinio, todėl esate sekami šioje svetainėje.","informationalModalConfirmButtonText":"Prisijungti","informationalModalRejectButtonText":"Grįžti atgal","loginButtonText":"Prisijunkite su „Facebook“","loginBodyText":"„Facebook“ seka jūsų veiklą svetainėje, kai prisijungiate su šia svetaine.","buttonTextUnblockContent":"Atblokuoti turinį","buttonTextUnblockComment":"Atblokuoti komentarą","buttonTextUnblockComments":"Atblokuoti komentarus","buttonTextUnblockPost":"Atblokuoti įrašą","buttonTextUnblockVideo":"Atblokuoti vaizdo įrašą","infoTitleUnblockContent":"„DuckDuckGo“ užblokavo šį turinį, kad „Facebook“ negalėtų jūsų sekti","infoTitleUnblockComment":"„DuckDuckGo“ užblokavo šį komentarą, kad „Facebook“ negalėtų jūsų sekti","infoTitleUnblockComments":"„DuckDuckGo“ užblokavo šiuos komentarus, kad „Facebook“ negalėtų jūsų sekti","infoTitleUnblockPost":"„DuckDuckGo“ užblokavo šį įrašą, kad „Facebook“ negalėtų jūsų sekti","infoTitleUnblockVideo":"„DuckDuckGo“ užblokavo šį vaizdo įrašą, kad „Facebook“ negalėtų jūsų sekti","infoTextUnblockContent":"Užblokavome „Facebook“, kad negalėtų jūsų sekti, kai puslapis buvo įkeltas. Jei atblokuosite šį turinį, „Facebook“ žinos apie jūsų veiklą."},"shared.json":{"learnMore":"Sužinoti daugiau","readAbout":"Skaitykite apie šią privatumo apsaugą"},"youtube.json":{"informationalModalMessageTitle":"Įjungti visas „YouTube“ peržiūras?","informationalModalMessageBody":"Peržiūrų rodymas leis „Google“ (kuriai priklauso „YouTube“) matyti tam tikrą jūsų įrenginio informaciją, tačiau ji vis tiek bus privatesnė nei leidžiant vaizdo įrašą.","informationalModalConfirmButtonText":"Įjungti visas peržiūras","informationalModalRejectButtonText":"Ne, dėkoju","buttonTextUnblockVideo":"Atblokuoti vaizdo įrašą","infoTitleUnblockVideo":"„DuckDuckGo“ užblokavo šį „YouTube“ vaizdo įrašą, kad „Google“ negalėtų jūsų sekti","infoTextUnblockVideo":"Užblokavome „Google“ (kuriai priklauso „YouTube“) galimybę sekti jus, kai puslapis buvo įkeltas. Jei atblokuosite šį vaizdo įrašą, „Google“ sužinos apie jūsų veiklą.","infoPreviewToggleText":"Peržiūros išjungtos dėl papildomo privatumo","infoPreviewToggleEnabledText":"Peržiūros įjungtos","infoPreviewInfoText":"Išjunkite peržiūras, kad gautumėte papildomo privatumo iš „DuckDuckGo“."}},"lv":{"facebook.json":{"informationalModalMessageTitle":"Ja pieteiksies ar Facebook, viņi varēs tevi izsekot","informationalModalMessageBody":"Kad tu piesakies, DuckDuckGo nevar novērst, ka Facebook saturs tevi izseko šajā vietnē.","informationalModalConfirmButtonText":"Pieteikties","informationalModalRejectButtonText":"Atgriezties","loginButtonText":"Pieteikties ar Facebook","loginBodyText":"Facebook izseko tavas aktivitātes vietnē, kad esi pieteicies ar Facebook.","buttonTextUnblockContent":"Atbloķēt saturu","buttonTextUnblockComment":"Atbloķēt komentāru","buttonTextUnblockComments":"Atbloķēt komentārus","buttonTextUnblockPost":"Atbloķēt ziņu","buttonTextUnblockVideo":"Atbloķēt video","infoTitleUnblockContent":"DuckDuckGo bloķēja šo saturu, lai neļautu Facebook tevi izsekot","infoTitleUnblockComment":"DuckDuckGo bloķēja šo komentāru, lai neļautu Facebook tevi izsekot","infoTitleUnblockComments":"DuckDuckGo bloķēja šos komentārus, lai neļautu Facebook tevi izsekot","infoTitleUnblockPost":"DuckDuckGo bloķēja šo ziņu, lai neļautu Facebook tevi izsekot","infoTitleUnblockVideo":"DuckDuckGo bloķēja šo videoklipu, lai neļautu Facebook tevi izsekot","infoTextUnblockContent":"Mēs bloķējām Facebook iespēju tevi izsekot, ielādējot lapu. Ja atbloķēsi šo saturu, Facebook redzēs, ko tu dari."},"shared.json":{"learnMore":"Uzzināt vairāk","readAbout":"Lasi par šo privātuma aizsardzību"},"youtube.json":{"informationalModalMessageTitle":"Vai iespējot visus YouTube priekšskatījumus?","informationalModalMessageBody":"Priekšskatījumu rādīšana ļaus Google (kam pieder YouTube) redzēt daļu tavas ierīces informācijas, taču tas tāpat ir privātāk par videoklipa atskaņošanu.","informationalModalConfirmButtonText":"Iespējot visus priekšskatījumus","informationalModalRejectButtonText":"Nē, paldies","buttonTextUnblockVideo":"Atbloķēt video","infoTitleUnblockVideo":"DuckDuckGo bloķēja šo YouTube videoklipu, lai neļautu Google tevi izsekot","infoTextUnblockVideo":"Mēs neļāvām Google (kam pieder YouTube) tevi izsekot, kad lapa tika ielādēta. Ja atbloķēsi šo videoklipu, Google zinās, ko tu dari.","infoPreviewToggleText":"Priekšskatījumi ir atspējoti, lai nodrošinātu papildu konfidencialitāti","infoPreviewToggleEnabledText":"Priekšskatījumi ir iespējoti","infoPreviewInfoText":"Izslēdz priekšskatījumus, lai iegūtu papildu konfidencialitāti no DuckDuckGo."}},"nb":{"facebook.json":{"informationalModalMessageTitle":"Når du logger på med Facebook, kan de spore deg","informationalModalMessageBody":"Når du er logget på, kan ikke DuckDuckGo hindre Facebook-innhold i å spore deg på dette nettstedet.","informationalModalConfirmButtonText":"Logg inn","informationalModalRejectButtonText":"Gå tilbake","loginButtonText":"Logg på med Facebook","loginBodyText":"Når du logger på med Facebook, sporer de aktiviteten din på nettstedet.","buttonTextUnblockContent":"Opphev blokkering av innhold","buttonTextUnblockComment":"Opphev blokkering av kommentar","buttonTextUnblockComments":"Opphev blokkering av kommentarer","buttonTextUnblockPost":"Opphev blokkering av innlegg","buttonTextUnblockVideo":"Opphev blokkering av video","infoTitleUnblockContent":"DuckDuckGo blokkerte dette innholdet for å hindre Facebook i å spore deg","infoTitleUnblockComment":"DuckDuckGo blokkerte denne kommentaren for å hindre Facebook i å spore deg","infoTitleUnblockComments":"DuckDuckGo blokkerte disse kommentarene for å hindre Facebook i å spore deg","infoTitleUnblockPost":"DuckDuckGo blokkerte dette innlegget for å hindre Facebook i å spore deg","infoTitleUnblockVideo":"DuckDuckGo blokkerte denne videoen for å hindre Facebook i å spore deg","infoTextUnblockContent":"Vi hindret Facebook i å spore deg da siden ble lastet. Hvis du opphever blokkeringen av dette innholdet, får Facebook vite om aktiviteten din."},"shared.json":{"learnMore":"Finn ut mer","readAbout":"Les om denne personvernfunksjonen"},"youtube.json":{"informationalModalMessageTitle":"Vil du aktivere alle YouTube-forhåndsvisninger?","informationalModalMessageBody":"Forhåndsvisninger gjør det mulig for Google (som eier YouTube) å se enkelte opplysninger om enheten din, men det er likevel mer privat enn å spille av videoen.","informationalModalConfirmButtonText":"Aktiver alle forhåndsvisninger","informationalModalRejectButtonText":"Nei takk","buttonTextUnblockVideo":"Opphev blokkering av video","infoTitleUnblockVideo":"DuckDuckGo blokkerte denne YouTube-videoen for å hindre Google i å spore deg","infoTextUnblockVideo":"Vi blokkerte Google (som eier YouTube) mot å spore deg da siden ble lastet. Hvis du opphever blokkeringen av denne videoen, får Google vite om aktiviteten din.","infoPreviewToggleText":"Forhåndsvisninger er deaktivert for å gi deg ekstra personvern","infoPreviewToggleEnabledText":"Forhåndsvisninger er aktivert","infoPreviewInfoText":"Slå av forhåndsvisninger for sterkere personvern med DuckDuckGo."}},"nl":{"facebook.json":{"informationalModalMessageTitle":"Als je inlogt met Facebook, kunnen zij je volgen","informationalModalMessageBody":"Als je eenmaal bent ingelogd, kan DuckDuckGo niet voorkomen dat Facebook je op deze site volgt.","informationalModalConfirmButtonText":"Inloggen","informationalModalRejectButtonText":"Terug","loginButtonText":"Inloggen met Facebook","loginBodyText":"Facebook volgt je activiteit op een site als je Facebook gebruikt om in te loggen.","buttonTextUnblockContent":"Inhoud deblokkeren","buttonTextUnblockComment":"Opmerking deblokkeren","buttonTextUnblockComments":"Opmerkingen deblokkeren","buttonTextUnblockPost":"Bericht deblokkeren","buttonTextUnblockVideo":"Video deblokkeren","infoTitleUnblockContent":"DuckDuckGo heeft deze inhoud geblokkeerd om te voorkomen dat Facebook je kan volgen","infoTitleUnblockComment":"DuckDuckGo heeft deze opmerking geblokkeerd om te voorkomen dat Facebook je kan volgen","infoTitleUnblockComments":"DuckDuckGo heeft deze opmerkingen geblokkeerd om te voorkomen dat Facebook je kan volgen","infoTitleUnblockPost":"DuckDuckGo heeft dit bericht geblokkeerd om te voorkomen dat Facebook je kan volgen","infoTitleUnblockVideo":"DuckDuckGo heeft deze video geblokkeerd om te voorkomen dat Facebook je kan volgen","infoTextUnblockContent":"We hebben voorkomen dat Facebook je volgde toen de pagina werd geladen. Als je deze inhoud deblokkeert, kan Facebook je activiteit zien."},"shared.json":{"learnMore":"Meer informatie","readAbout":"Lees meer over deze privacybescherming"},"youtube.json":{"informationalModalMessageTitle":"Alle YouTube-voorbeelden inschakelen?","informationalModalMessageBody":"Bij het tonen van voorbeelden kan Google (eigenaar van YouTube) een deel van de informatie over je apparaat zien, maar blijft je privacy beter beschermd dan als je de video zou afspelen.","informationalModalConfirmButtonText":"Alle voorbeelden inschakelen","informationalModalRejectButtonText":"Nee, bedankt","buttonTextUnblockVideo":"Video deblokkeren","infoTitleUnblockVideo":"DuckDuckGo heeft deze YouTube-video geblokkeerd om te voorkomen dat Google je kan volgen","infoTextUnblockVideo":"We hebben voorkomen dat Google (eigenaar van YouTube) je volgde toen de pagina werd geladen. Als je deze video deblokkeert, kan Google je activiteit zien.","infoPreviewToggleText":"Voorbeelden uitgeschakeld voor extra privacy","infoPreviewToggleEnabledText":"Voorbeelden ingeschakeld","infoPreviewInfoText":"Schakel voorbeelden uit voor extra privacy van DuckDuckGo."}},"pl":{"facebook.json":{"informationalModalMessageTitle":"Jeśli zalogujesz się za pośrednictwem Facebooka, będzie on mógł śledzić Twoją aktywność","informationalModalMessageBody":"Po zalogowaniu się DuckDuckGo nie może zablokować możliwości śledzenia Cię przez Facebooka na tej stronie.","informationalModalConfirmButtonText":"Zaloguj się","informationalModalRejectButtonText":"Wróć","loginButtonText":"Zaloguj się za pośrednictwem Facebooka","loginBodyText":"Facebook śledzi Twoją aktywność na stronie, gdy logujesz się za jego pośrednictwem.","buttonTextUnblockContent":"Odblokuj treść","buttonTextUnblockComment":"Odblokuj komentarz","buttonTextUnblockComments":"Odblokuj komentarze","buttonTextUnblockPost":"Odblokuj post","buttonTextUnblockVideo":"Odblokuj wideo","infoTitleUnblockContent":"DuckDuckGo zablokował tę treść, aby Facebook nie mógł Cię śledzić","infoTitleUnblockComment":"DuckDuckGo zablokował ten komentarz, aby Facebook nie mógł Cię śledzić","infoTitleUnblockComments":"DuckDuckGo zablokował te komentarze, aby Facebook nie mógł Cię śledzić","infoTitleUnblockPost":"DuckDuckGo zablokował ten post, aby Facebook nie mógł Cię śledzić","infoTitleUnblockVideo":"DuckDuckGo zablokował tę treść wideo, aby Facebook nie mógł Cię śledzić.","infoTextUnblockContent":"Zablokowaliśmy Facebookowi możliwość śledzenia Cię podczas ładowania strony. Jeśli odblokujesz tę treść, Facebook uzyska informacje o Twojej aktywności."},"shared.json":{"learnMore":"Dowiedz się więcej","readAbout":"Dowiedz się więcej o tej ochronie prywatności"},"youtube.json":{"informationalModalMessageTitle":"Włączyć wszystkie podglądy w YouTube?","informationalModalMessageBody":"Wyświetlanie podglądu pozwala Google (który jest właścicielem YouTube) zobaczyć niektóre informacje o Twoim urządzeniu, ale nadal jest to bardziej prywatne niż odtwarzanie filmu.","informationalModalConfirmButtonText":"Włącz wszystkie podglądy","informationalModalRejectButtonText":"Nie, dziękuję","buttonTextUnblockVideo":"Odblokuj wideo","infoTitleUnblockVideo":"DuckDuckGo zablokował ten film w YouTube, aby uniemożliwić Google śledzenie Twojej aktywności","infoTextUnblockVideo":"Zablokowaliśmy możliwość śledzenia Cię przez Google (właściciela YouTube) podczas ładowania strony. Jeśli odblokujesz ten film, Google zobaczy Twoją aktywność.","infoPreviewToggleText":"Podglądy zostały wyłączone, aby zapewnić większą ptywatność","infoPreviewToggleEnabledText":"Podglądy włączone","infoPreviewInfoText":"Wyłącz podglądy, aby uzyskać większą prywatność dzięki DuckDuckGo."}},"pt":{"facebook.json":{"informationalModalMessageTitle":"Iniciar sessão no Facebook permite que este te rastreie","informationalModalMessageBody":"Depois de iniciares sessão, o DuckDuckGo não poderá bloquear o rastreio por parte do conteúdo do Facebook neste site.","informationalModalConfirmButtonText":"Iniciar sessão","informationalModalRejectButtonText":"Retroceder","loginButtonText":"Iniciar sessão com o Facebook","loginBodyText":"O Facebook rastreia a tua atividade num site quando o usas para iniciares sessão.","buttonTextUnblockContent":"Desbloquear Conteúdo","buttonTextUnblockComment":"Desbloquear Comentário","buttonTextUnblockComments":"Desbloquear Comentários","buttonTextUnblockPost":"Desbloquear Publicação","buttonTextUnblockVideo":"Desbloquear Vídeo","infoTitleUnblockContent":"O DuckDuckGo bloqueou este conteúdo para evitar que o Facebook te rastreie","infoTitleUnblockComment":"O DuckDuckGo bloqueou este comentário para evitar que o Facebook te rastreie","infoTitleUnblockComments":"O DuckDuckGo bloqueou estes comentários para evitar que o Facebook te rastreie","infoTitleUnblockPost":"O DuckDuckGo bloqueou esta publicação para evitar que o Facebook te rastreie","infoTitleUnblockVideo":"O DuckDuckGo bloqueou este vídeo para evitar que o Facebook te rastreie","infoTextUnblockContent":"Bloqueámos o rastreio por parte do Facebook quando a página foi carregada. Se desbloqueares este conteúdo, o Facebook fica a saber a tua atividade."},"shared.json":{"learnMore":"Saiba mais","readAbout":"Ler mais sobre esta proteção de privacidade"},"youtube.json":{"informationalModalMessageTitle":"Ativar todas as pré-visualizações do YouTube?","informationalModalMessageBody":"Mostrar visualizações permite à Google (que detém o YouTube) ver algumas das informações do teu dispositivo, mas ainda é mais privado do que reproduzir o vídeo.","informationalModalConfirmButtonText":"Ativar todas as pré-visualizações","informationalModalRejectButtonText":"Não, obrigado","buttonTextUnblockVideo":"Desbloquear Vídeo","infoTitleUnblockVideo":"O DuckDuckGo bloqueou este vídeo do YouTube para impedir que a Google te rastreie","infoTextUnblockVideo":"Bloqueámos o rastreio por parte da Google (que detém o YouTube) quando a página foi carregada. Se desbloqueares este vídeo, a Google fica a saber a tua atividade.","infoPreviewToggleText":"Pré-visualizações desativadas para privacidade adicional","infoPreviewToggleEnabledText":"Pré-visualizações ativadas","infoPreviewInfoText":"Desativa as pré-visualizações para obteres privacidade adicional com o DuckDuckGo."}},"ro":{"facebook.json":{"informationalModalMessageTitle":"Conectarea cu Facebook îi permite să te urmărească","informationalModalMessageBody":"Odată ce te-ai conectat, DuckDuckGo nu poate împiedica conținutul Facebook să te urmărească pe acest site.","informationalModalConfirmButtonText":"Autentificare","informationalModalRejectButtonText":"Înapoi","loginButtonText":"Conectează-te cu Facebook","loginBodyText":"Facebook urmărește activitatea ta pe un site atunci când îl utilizezi pentru a te conecta.","buttonTextUnblockContent":"Deblochează conținutul","buttonTextUnblockComment":"Deblochează comentariul","buttonTextUnblockComments":"Deblochează comentariile","buttonTextUnblockPost":"Deblochează postarea","buttonTextUnblockVideo":"Deblochează videoclipul","infoTitleUnblockContent":"DuckDuckGo a blocat acest conținut pentru a împiedica Facebook să te urmărească","infoTitleUnblockComment":"DuckDuckGo a blocat acest comentariu pentru a împiedica Facebook să te urmărească","infoTitleUnblockComments":"DuckDuckGo a blocat aceste comentarii pentru a împiedica Facebook să te urmărească","infoTitleUnblockPost":"DuckDuckGo a blocat această postare pentru a împiedica Facebook să te urmărească","infoTitleUnblockVideo":"DuckDuckGo a blocat acest videoclip pentru a împiedica Facebook să te urmărească","infoTextUnblockContent":"Am împiedicat Facebook să te urmărească atunci când pagina a fost încărcată. Dacă deblochezi acest conținut, Facebook îți va cunoaște activitatea."},"shared.json":{"learnMore":"Află mai multe","readAbout":"Citește despre această protecție a confidențialității"},"youtube.json":{"informationalModalMessageTitle":"Activezi toate previzualizările YouTube?","informationalModalMessageBody":"Afișarea previzualizărilor va permite ca Google (care deține YouTube) să vadă unele dintre informațiile despre dispozitivul tău, dar este totuși mai privată decât redarea videoclipului.","informationalModalConfirmButtonText":"Activează toate previzualizările","informationalModalRejectButtonText":"Nu, mulțumesc","buttonTextUnblockVideo":"Deblochează videoclipul","infoTitleUnblockVideo":"DuckDuckGo a blocat acest videoclip de pe YouTube pentru a împiedica Google să te urmărească","infoTextUnblockVideo":"Am împiedicat Google (care deține YouTube) să te urmărească atunci când s-a încărcat pagina. Dacă deblochezi acest videoclip, Google va cunoaște activitatea ta.","infoPreviewToggleText":"Previzualizările au fost dezactivate pentru o confidențialitate suplimentară","infoPreviewToggleEnabledText":"Previzualizări activate","infoPreviewInfoText":"Dezactivează previzualizările pentru o confidențialitate suplimentară de la DuckDuckGo."}},"ru":{"facebook.json":{"informationalModalMessageTitle":"Вход через Facebook позволяет этой социальной сети отслеживать вас","informationalModalMessageBody":"После входа DuckDuckGo не сможет блокировать отслеживание ваших действий с контентом на Facebook.","informationalModalConfirmButtonText":"Войти","informationalModalRejectButtonText":"Вернуться","loginButtonText":"Войти через Facebook","loginBodyText":"При использовании учетной записи Facebook для входа на сайты эта социальная сеть сможет отслеживать на них ваши действия.","buttonTextUnblockContent":"Разблокировать","buttonTextUnblockComment":"Разблокировать","buttonTextUnblockComments":"Разблокировать","buttonTextUnblockPost":"Разблокировать","buttonTextUnblockVideo":"Разблокировать","infoTitleUnblockContent":"DuckDuckGo заблокировал этот контент, чтобы вас не отслеживал Facebook","infoTitleUnblockComment":"DuckDuckGo заблокировал этот комментарий, чтобы вас не отслеживал Facebook","infoTitleUnblockComments":"DuckDuckGo заблокировал эти комментарии, чтобы вас не отслеживал Facebook","infoTitleUnblockPost":"DuckDuckGo заблокировал эту публикацию, чтобы вас не отслеживал Facebook","infoTitleUnblockVideo":"DuckDuckGo заблокировал это видео, чтобы вас не отслеживал Facebook","infoTextUnblockContent":"Во время загрузки страницы мы помешали Facebook отследить ваши действия. Если разблокировать этот контент, Facebook сможет фиксировать вашу активность."},"shared.json":{"learnMore":"Узнать больше","readAbout":"Подробнее об этом виде защиты конфиденциальности"},"youtube.json":{"informationalModalMessageTitle":"Включить предпросмотр видео из YouTube?","informationalModalMessageBody":"Активация предварительного просмотра позволит Google (владельцу YouTube) получить некоторые сведения о вашем устройстве, однако это более безопасный вариант, чем воспроизведение видео целиком.","informationalModalConfirmButtonText":"Включить предпросмотр","informationalModalRejectButtonText":"Нет, спасибо","buttonTextUnblockVideo":"Разблокировать","infoTitleUnblockVideo":"DuckDuckGo заблокировал это видео из YouTube, чтобы вас не отслеживал Google","infoTextUnblockVideo":"Во время загрузки страницы мы помешали Google (владельцу YouTube) отследить ваши действия. Если разблокировать видео, Google сможет фиксировать вашу активность.","infoPreviewToggleText":"Предварительный просмотр отключен для дополнительной защиты конфиденциальности","infoPreviewToggleEnabledText":"Предварительный просмотр включен","infoPreviewInfoText":"DuckDuckGo рекомендует отключить предпросмотр для дополнительной защиты конфиденциальности."}},"sk":{"facebook.json":{"informationalModalMessageTitle":"Prihlásenie cez Facebook mu umožní sledovať vás","informationalModalMessageBody":"DuckDuckGo po prihlásení nemôže na tejto lokalite zablokovať sledovanie vašej osoby obsahom Facebooku.","informationalModalConfirmButtonText":"Prihlásiť sa","informationalModalRejectButtonText":"Prejsť späť","loginButtonText":"Prihláste sa pomocou služby Facebook","loginBodyText":"Keď použijete prihlasovanie cez Facebook, Facebook bude na lokalite sledovať vašu aktivitu.","buttonTextUnblockContent":"Odblokovať obsah","buttonTextUnblockComment":"Odblokovať komentár","buttonTextUnblockComments":"Odblokovať komentáre","buttonTextUnblockPost":"Odblokovať príspevok","buttonTextUnblockVideo":"Odblokovať video","infoTitleUnblockContent":"DuckDuckGo zablokoval tento obsah, aby vás Facebook nesledoval","infoTitleUnblockComment":"DuckDuckGo zablokoval tento komentár, aby zabránil sledovaniu zo strany Facebooku","infoTitleUnblockComments":"DuckDuckGo zablokoval tieto komentáre, aby vás Facebook nesledoval","infoTitleUnblockPost":"DuckDuckGo zablokoval tento príspevok, aby vás Facebook nesledoval","infoTitleUnblockVideo":"DuckDuckGo zablokoval toto video, aby vás Facebook nesledoval","infoTextUnblockContent":"Pri načítaní stránky sme zablokovali Facebook, aby vás nesledoval. Ak tento obsah odblokujete, Facebook bude vedieť o vašej aktivite."},"shared.json":{"learnMore":"Zistite viac","readAbout":"Prečítajte si o tejto ochrane súkromia"},"youtube.json":{"informationalModalMessageTitle":"Chcete povoliť všetky ukážky zo služby YouTube?","informationalModalMessageBody":"Zobrazenie ukážok umožní spoločnosti Google (ktorá vlastní YouTube) vidieť niektoré informácie o vašom zariadení, ale stále je to súkromnejšie ako prehrávanie videa.","informationalModalConfirmButtonText":"Povoliť všetky ukážky","informationalModalRejectButtonText":"Nie, ďakujem","buttonTextUnblockVideo":"Odblokovať video","infoTitleUnblockVideo":"DuckDuckGo toto video v službe YouTube zablokoval s cieľom predísť tomu, aby vás spoločnosť Google mohla sledovať","infoTextUnblockVideo":"Zablokovali sme pre spoločnosť Google (ktorá vlastní YouTube), aby vás nemohla sledovať, keď sa stránka načíta. Ak toto video odblokujete, Google bude poznať vašu aktivitu.","infoPreviewToggleText":"Ukážky sú zakázané s cieľom zvýšiť ochranu súkromia","infoPreviewToggleEnabledText":"Ukážky sú povolené","infoPreviewInfoText":"Pre zvýšenú ochranu súkromia od DuckDuckGo vypnite ukážky."}},"sl":{"facebook.json":{"informationalModalMessageTitle":"Če se prijavite s Facebookom, vam Facebook lahko sledi","informationalModalMessageBody":"Ko ste enkrat prijavljeni, DuckDuckGo ne more blokirati Facebookove vsebine, da bi vam sledila na tem spletnem mestu.","informationalModalConfirmButtonText":"Prijava","informationalModalRejectButtonText":"Pojdi nazaj","loginButtonText":"Prijavite se s Facebookom","loginBodyText":"Če se prijavite s Facebookom, bo nato spremljal vaša dejanja na spletnem mestu.","buttonTextUnblockContent":"Odblokiraj vsebino","buttonTextUnblockComment":"Odblokiraj komentar","buttonTextUnblockComments":"Odblokiraj komentarje","buttonTextUnblockPost":"Odblokiraj objavo","buttonTextUnblockVideo":"Odblokiraj videoposnetek","infoTitleUnblockContent":"DuckDuckGo je blokiral to vsebino, da bi Facebooku preprečil sledenje","infoTitleUnblockComment":"DuckDuckGo je blokiral ta komentar, da bi Facebooku preprečil sledenje","infoTitleUnblockComments":"DuckDuckGo je blokiral te komentarje, da bi Facebooku preprečil sledenje","infoTitleUnblockPost":"DuckDuckGo je blokiral to objavo, da bi Facebooku preprečil sledenje","infoTitleUnblockVideo":"DuckDuckGo je blokiral ta videoposnetek, da bi Facebooku preprečil sledenje","infoTextUnblockContent":"Ko se je stran naložila, smo Facebooku preprečili, da bi vam sledil. Če to vsebino odblokirate, bo Facebook izvedel za vaša dejanja."},"shared.json":{"learnMore":"Več","readAbout":"Preberite več o tej zaščiti zasebnosti"},"youtube.json":{"informationalModalMessageTitle":"Želite omogočiti vse YouTubove predoglede?","informationalModalMessageBody":"Prikaz predogledov omogoča Googlu (ki je lastnik YouTuba) vpogled v nekatere podatke o napravi, vendar je še vedno bolj zasebno kot predvajanje videoposnetka.","informationalModalConfirmButtonText":"Omogoči vse predoglede","informationalModalRejectButtonText":"Ne, hvala","buttonTextUnblockVideo":"Odblokiraj videoposnetek","infoTitleUnblockVideo":"DuckDuckGo je blokiral ta videoposnetek v YouTubu, da bi Googlu preprečil sledenje","infoTextUnblockVideo":"Googlu (ki je lastnik YouTuba) smo preprečili, da bi vam sledil, ko se je stran naložila. Če odblokirate ta videoposnetek, bo Google izvedel za vašo dejavnost.","infoPreviewToggleText":"Predogledi so zaradi dodatne zasebnosti onemogočeni","infoPreviewToggleEnabledText":"Predogledi so omogočeni","infoPreviewInfoText":"Izklopite predoglede za dodatno zasebnost v DuckDuckGo."}},"sv":{"facebook.json":{"informationalModalMessageTitle":"Om du loggar in med Facebook kan de spåra dig","informationalModalMessageBody":"När du väl är inloggad kan DuckDuckGo inte hindra Facebooks innehåll från att spåra dig på den här webbplatsen.","informationalModalConfirmButtonText":"Logga in","informationalModalRejectButtonText":"Gå tillbaka","loginButtonText":"Logga in med Facebook","loginBodyText":"Facebook spårar din aktivitet på en webbplats om du använder det för att logga in.","buttonTextUnblockContent":"Avblockera innehåll","buttonTextUnblockComment":"Avblockera kommentar","buttonTextUnblockComments":"Avblockera kommentarer","buttonTextUnblockPost":"Avblockera inlägg","buttonTextUnblockVideo":"Avblockera video","infoTitleUnblockContent":"DuckDuckGo blockerade det här innehållet för att förhindra att Facebook spårar dig","infoTitleUnblockComment":"DuckDuckGo blockerade den här kommentaren för att förhindra att Facebook spårar dig","infoTitleUnblockComments":"DuckDuckGo blockerade de här kommentarerna för att förhindra att Facebook spårar dig","infoTitleUnblockPost":"DuckDuckGo blockerade det här inlägget för att förhindra att Facebook spårar dig","infoTitleUnblockVideo":"DuckDuckGo blockerade den här videon för att förhindra att Facebook spårar dig","infoTextUnblockContent":"Vi hindrade Facebook från att spåra dig när sidan lästes in. Om du avblockerar det här innehållet kommer Facebook att känna till din aktivitet."},"shared.json":{"learnMore":"Läs mer","readAbout":"Läs mer om detta integritetsskydd"},"youtube.json":{"informationalModalMessageTitle":"Aktivera alla förhandsvisningar för YouTube?","informationalModalMessageBody":"Genom att visa förhandsvisningar kan Google (som äger YouTube) se en del av enhetens information, men det är ändå mer privat än att spela upp videon.","informationalModalConfirmButtonText":"Aktivera alla förhandsvisningar","informationalModalRejectButtonText":"Nej tack","buttonTextUnblockVideo":"Avblockera video","infoTitleUnblockVideo":"DuckDuckGo blockerade den här YouTube-videon för att förhindra att Google spårar dig","infoTextUnblockVideo":"Vi hindrade Google (som äger YouTube) från att spåra dig när sidan laddades. Om du tar bort blockeringen av videon kommer Google att känna till din aktivitet.","infoPreviewToggleText":"Förhandsvisningar har inaktiverats för ytterligare integritet","infoPreviewToggleEnabledText":"Förhandsvisningar aktiverade","infoPreviewInfoText":"Inaktivera förhandsvisningar för ytterligare integritet med DuckDuckGo."}},"tr":{"facebook.json":{"informationalModalMessageTitle":"Facebook ile giriş yapmak, sizi takip etmelerini sağlar","informationalModalMessageBody":"Giriş yaptıktan sonra, DuckDuckGo Facebook içeriğinin sizi bu sitede izlemesini engelleyemez.","informationalModalConfirmButtonText":"Oturum Aç","informationalModalRejectButtonText":"Geri dön","loginButtonText":"Facebook ile giriş yapın","loginBodyText":"Facebook, giriş yapmak için kullandığınızda bir sitedeki etkinliğinizi izler.","buttonTextUnblockContent":"İçeriğin Engelini Kaldır","buttonTextUnblockComment":"Yorumun Engelini Kaldır","buttonTextUnblockComments":"Yorumların Engelini Kaldır","buttonTextUnblockPost":"Gönderinin Engelini Kaldır","buttonTextUnblockVideo":"Videonun Engelini Kaldır","infoTitleUnblockContent":"DuckDuckGo, Facebook'un sizi izlemesini önlemek için bu içeriği engelledi","infoTitleUnblockComment":"DuckDuckGo, Facebook'un sizi izlemesini önlemek için bu yorumu engelledi","infoTitleUnblockComments":"DuckDuckGo, Facebook'un sizi izlemesini önlemek için bu yorumları engelledi","infoTitleUnblockPost":"DuckDuckGo, Facebook'un sizi izlemesini önlemek için bu gönderiyi engelledi","infoTitleUnblockVideo":"DuckDuckGo, Facebook'un sizi izlemesini önlemek için bu videoyu engelledi","infoTextUnblockContent":"Sayfa yüklendiğinde Facebook'un sizi izlemesini engelledik. Bu içeriğin engelini kaldırırsanız Facebook etkinliğinizi öğrenecektir."},"shared.json":{"learnMore":"Daha Fazla Bilgi","readAbout":"Bu gizlilik koruması hakkında bilgi edinin"},"youtube.json":{"informationalModalMessageTitle":"Tüm YouTube önizlemeleri etkinleştirilsin mi?","informationalModalMessageBody":"Önizlemelerin gösterilmesi Google'ın (YouTube'un sahibi) cihazınızın bazı bilgilerini görmesine izin verir, ancak yine de videoyu oynatmaktan daha özeldir.","informationalModalConfirmButtonText":"Tüm Önizlemeleri Etkinleştir","informationalModalRejectButtonText":"Hayır Teşekkürler","buttonTextUnblockVideo":"Videonun Engelini Kaldır","infoTitleUnblockVideo":"DuckDuckGo, Google'ın sizi izlemesini önlemek için bu YouTube videosunu engelledi","infoTextUnblockVideo":"Sayfa yüklendiğinde Google'ın (YouTube'un sahibi) sizi izlemesini engelledik. Bu videonun engelini kaldırırsanız, Google etkinliğinizi öğrenecektir.","infoPreviewToggleText":"Ek gizlilik için önizlemeler devre dışı bırakıldı","infoPreviewToggleEnabledText":"Önizlemeler etkinleştirildi","infoPreviewInfoText":"DuckDuckGo'dan daha fazla gizlilik için önizlemeleri kapatın."}}}`;

    /*********************************************************
     *  Style Definitions
     *********************************************************/
    const styles$1 = {
        fontStyle: `
        @font-face{
            font-family: DuckDuckGoPrivacyEssentials;
            src: url(${ddgFont$1});
        }
        @font-face{
            font-family: DuckDuckGoPrivacyEssentialsBold;
            font-weight: bold;
            src: url(${ddgFontBold$1});
        }
    `,
        darkMode: {
            background: `
            background: #111111;
        `,
            textFont: `
            color: rgba(255, 255, 255, 0.9);
        `,
            buttonFont: `
            color: #111111;
        `,
            linkFont: `
            color: #7295F6;
        `,
            buttonBackground: `
            background: #5784FF;
        `,
            buttonBackgroundHover: `
            background: #557FF3;
        `,
            buttonBackgroundPress: `
            background: #3969EF;
        `,
            toggleButtonText: `
            color: #EEEEEE;
        `
        },
        lightMode: {
            background: `
            background: #FFFFFF;
        `,
            textFont: `
            color: #222222;
        `,
            buttonFont: `
            color: #FFFFFF;
        `,
            linkFont: `
            color: #3969EF;
        `,
            buttonBackground: `
            background: #3969EF;
        `,
            buttonBackgroundHover: `
            background: #2B55CA;
        `,
            buttonBackgroundPress: `
            background: #1E42A4;
        `,
            toggleButtonText: `
            color: #666666;
        `
        },
        loginMode: {
            buttonBackground: `
            background: #666666;
        `,
            buttonFont: `
            color: #FFFFFF;
        `
        },
        cancelMode: {
            buttonBackground: `
            background: rgba(34, 34, 34, 0.1);
        `,
            buttonFont: `
            color: #222222;
        `,
            buttonBackgroundHover: `
            background: rgba(0, 0, 0, 0.12);
        `,
            buttonBackgroundPress: `
            background: rgba(0, 0, 0, 0.18);
        `
        },
        button: `
        border-radius: 8px;

        padding: 11px 22px;
        font-weight: bold;
        margin: 0px auto;
        border-color: #3969EF;
        border: none;

        font-family: DuckDuckGoPrivacyEssentialsBold;
        font-size: 14px;

        position: relative;
        cursor: pointer;
        box-shadow: none;
        z-index: 2147483646;
    `,
        circle: `
        border-radius: 50%;
        width: 18px;
        height: 18px;
        background: #E0E0E0;
        border: 1px solid #E0E0E0;
        position: absolute;
        top: -8px;
        right: -8px;
    `,
        loginIcon: `
        position: absolute;
        top: -13px;
        right: -10px;
        height: 28px;
        width: 28px;
    `,
        rectangle: `
        width: 12px;
        height: 3px;
        background: #666666;
        position: relative;
        top: 42.5%;
        margin: auto;
    `,
        textBubble: `
        background: #FFFFFF;
        border: 1px solid rgba(0, 0, 0, 0.1);
        border-radius: 16px;
        box-shadow: 0px 2px 6px rgba(0, 0, 0, 0.12), 0px 8px 16px rgba(0, 0, 0, 0.08);
        width: 360px;
        margin-top: 10px;
        z-index: 2147483647;
        position: absolute;
        line-height: normal;
    `,
        textBubbleWidth: 360, // Should match the width rule in textBubble
        textBubbleLeftShift: 100, // Should match the CSS left: rule in textBubble
        textArrow: `
        display: inline-block;
        background: #FFFFFF;
        border: solid rgba(0, 0, 0, 0.1);
        border-width: 0 1px 1px 0;
        padding: 5px;
        transform: rotate(-135deg);
        -webkit-transform: rotate(-135deg);
        position: relative;
        top: -9px;
    `,
        arrowDefaultLocationPercent: 50,
        hoverTextTitle: `
        padding: 0px 12px 12px;
        margin-top: -5px;
    `,
        hoverTextBody: `
        font-family: DuckDuckGoPrivacyEssentials;
        font-size: 14px;
        line-height: 21px;
        margin: auto;
        padding: 17px;
        text-align: left;
    `,
        hoverContainer: `
        padding-bottom: 10px;
    `,
        buttonTextContainer: `
        display: flex;
        flex-direction: row;
        align-items: center;
        border: none;
        padding: 0;
        margin: 0;
    `,
        headerRow: `

    `,
        block: `
        box-sizing: border-box;
        border: 1px solid rgba(0,0,0,0.1);
        border-radius: 12px;
        max-width: 600px;
        min-height: 300px;
        margin: auto;
        display: flex;
        flex-direction: column;

        font-family: DuckDuckGoPrivacyEssentials;
        line-height: 1;
    `,
        youTubeDialogBlock: `
        height: calc(100% - 30px);
        max-width: initial;
        min-height: initial;
    `,
        imgRow: `
        display: flex;
        flex-direction: column;
        margin: 20px 0px;
    `,
        content: `
        display: flex;
        flex-direction: column;
        padding: 16px 0;
        flex: 1 1 1px;
    `,
        feedbackLink: `
        font-family: DuckDuckGoPrivacyEssentials;
        font-style: normal;
        font-weight: 400;
        font-size: 12px;
        line-height: 12px;
        color: #ABABAB;
        text-decoration: none;
    `,
        feedbackRow: `
        height: 30px;
        display: flex;
        justify-content: flex-end;
        align-items: center;
    `,
        titleBox: `
        display: flex;
        padding: 12px;
        max-height: 44px;
        border-bottom: 1px solid;
        border-color: rgba(196, 196, 196, 0.3);
        margin: 0;
        margin-bottom: 4px;
    `,
        title: `
        font-family: DuckDuckGoPrivacyEssentials;
        line-height: 1.4;
        font-size: 14px;
        margin: auto 10px;
        flex-basis: 100%;
        height: 1.4em;
        flex-wrap: wrap;
        overflow: hidden;
        text-align: left;
        border: none;
        padding: 0;
    `,
        buttonRow: `
        display: flex;
        height: 100%
        flex-direction: row;
        margin: 20px auto 0px;
        height: 100%;
        align-items: flex-start;
    `,
        modalContentTitle: `
        font-family: DuckDuckGoPrivacyEssentialsBold;
        font-size: 17px;
        font-weight: bold;
        line-height: 21px;
        margin: 10px auto;
        text-align: center;
        border: none;
        padding: 0px 32px;
    `,
        modalContentText: `
        font-family: DuckDuckGoPrivacyEssentials;
        font-size: 14px;
        line-height: 21px;
        margin: 0px auto 14px;
        text-align: center;
        border: none;
        padding: 0;
    `,
        modalButtonRow: `
        border: none;
        padding: 0;
        margin: auto;
        width: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
    `,
        modalButton: `
        width: 100%;
        display: flex;
        justify-content: center;
        align-items: center;
    `,
        modalIcon: `
        display: block;
    `,
        contentTitle: `
        font-family: DuckDuckGoPrivacyEssentialsBold;
        font-size: 17px;
        font-weight: bold;
        margin: 20px auto 10px;
        padding: 0px 30px;
        text-align: center;
        margin-top: auto;
    `,
        contentText: `
        font-family: DuckDuckGoPrivacyEssentials;
        font-size: 14px;
        line-height: 21px;
        padding: 0px 40px;
        text-align: center;
        margin: 0 auto auto;
    `,
        icon: `
        height: 80px;
        width: 80px;
        margin: auto;
    `,
        closeIcon: `
        height: 12px;
        width: 12px;
        margin: auto;
    `,
        closeButton: `
        display: flex;
        justify-content: center;
        align-items: center;
        min-width: 20px;
        height: 21px;
        border: 0;
        background: transparent;
        cursor: pointer;
    `,
        logo: `
        flex-basis: 0%;
        min-width: 20px;
        height: 21px;
        border: none;
        padding: 0;
        margin: 0;
    `,
        logoImg: `
        height: 21px;
        width: 21px;
    `,
        loadingImg: `
        display: block;
        margin: 0px 8px 0px 0px;
        height: 14px;
        width: 14px;
    `,
        modal: `
        width: 340px;
        padding: 0;
        margin: auto;
        background-color: #FFFFFF;
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        display: block;
        box-shadow: 0px 1px 3px rgba(0, 0, 0, 0.08), 0px 2px 4px rgba(0, 0, 0, 0.1);
        border-radius: 12px;
        border: none;
    `,
        modalContent: `
        padding: 24px;
        display: flex;
        flex-direction: column;
        border: none;
        margin: 0;
    `,
        overlay: `
        height: 100%;
        width: 100%;
        background-color: #666666;
        opacity: .5;
        display: block;
        position: fixed;
        top: 0;
        right: 0;
        border: none;
        padding: 0;
        margin: 0;
    `,
        modalContainer: `
        height: 100vh;
        width: 100vw;
        box-sizing: border-box;
        z-index: 2147483647;
        display: block;
        position: fixed;
        border: 0;
        margin: 0;
        padding: 0;
    `,
        headerLinkContainer: `
        flex-basis: 100%;
        display: grid;
        justify-content: flex-end;
    `,
        headerLink: `
        line-height: 1.4;
        font-size: 14px;
        font-weight: bold;
        font-family: DuckDuckGoPrivacyEssentialsBold;
        text-decoration: none;
        cursor: pointer;
        min-width: 100px;
        text-align: end;
        float: right;
        display: none;
    `,
        generalLink: `
        line-height: 1.4;
        font-size: 14px;
        font-weight: bold;
        font-family: DuckDuckGoPrivacyEssentialsBold;
        cursor: pointer;
        text-decoration: none;
    `,
        wrapperDiv: `
        display: inline-block;
        border: 0;
        padding: 0;
        margin: 0;
        max-width: 600px;
        min-height: 300px;
    `,
        toggleButtonWrapper: `
        display: flex;
        align-items: center;
        cursor: pointer;
    `,
        toggleButton: `
        cursor: pointer;
        position: relative;
        width: 30px;
        height: 16px;
        margin-top: -3px;
        margin: 0;
        padding: 0;
        border: none;
        background-color: transparent;
        text-align: left;
    `,
        toggleButtonBg: `
        right: 0;
        width: 30px;
        height: 16px;
        overflow: visible;
        border-radius: 10px;
    `,
        toggleButtonText: `
        display: inline-block;
        margin: 0 0 0 7px;
        padding: 0;
    `,
        toggleButtonBgState: {
            active: `
            background: #3969EF;
        `,
            inactive: `
            background-color: #666666;
        `
        },
        toggleButtonKnob: `
        position: absolute;
        display: inline-block;
        width: 14px;
        height: 14px;
        border-radius: 10px;
        background-color: #ffffff;
        margin-top: 1px;
        top: calc(50% - 14px/2 - 1px);
        box-shadow: 0px 0px 1px rgba(0, 0, 0, 0.05), 0px 1px 1px rgba(0, 0, 0, 0.1);
    `,
        toggleButtonKnobState: {
            active: `
            right: 1px;
        `,
            inactive: `
            left: 1px;
        `
        },
        placeholderWrapperDiv: `
        position: relative;
        overflow: hidden;
        border-radius: 12px;
        box-sizing: border-box;
        max-width: initial;
        min-width: 380px;
        min-height: 300px;
        margin: auto;
    `,
        youTubeWrapperDiv: `
        position: relative;
        overflow: hidden;
        max-width: initial;
        min-width: 380px;
        min-height: 300px;
        height: 100%;
    `,
        youTubeDialogDiv: `
        position: relative;
        overflow: hidden;
        border-radius: 12px;
        max-width: initial;
        min-height: initial;
        height: calc(100% - 30px);
    `,
        youTubeDialogBottomRow: `
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: flex-end;
        margin-top: auto;
    `,
        youTubePlaceholder: `
        display: flex;
        flex-direction: column;
        justify-content: flex-start;
        position: relative;
        width: 100%;
        height: 100%;
        background: rgba(45, 45, 45, 0.8);
    `,
        youTubePreviewWrapperImg: `
        position: absolute;
        display: flex;
        justify-content: center;
        align-items: center;
        width: 100%;
        height: 100%;
    `,
        youTubePreviewImg: `
        min-width: 100%;
        min-height: 100%;
        height: auto;
    `,
        youTubeTopSection: `
        font-family: DuckDuckGoPrivacyEssentialsBold;
        flex: 1;
        display: flex;
        justify-content: space-between;
        position: relative;
        padding: 18px 12px 0;
    `,
        youTubeTitle: `
        font-size: 14px;
        font-weight: bold;
        line-height: 14px;
        color: #FFFFFF;
        margin: 0;
        width: 100%;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        box-sizing: border-box;
    `,
        youTubePlayButtonRow: `
        flex: 2;
        display: flex;
        align-items: center;
        justify-content: center;
    `,
        youTubePlayButton: `
        display: flex;
        justify-content: center;
        align-items: center;
        height: 48px;
        width: 80px;
        padding: 0px 24px;
        border-radius: 8px;
    `,
        youTubePreviewToggleRow: `
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: flex-end;
        align-items: center;
        padding: 0 12px 18px;
    `,
        youTubePreviewToggleText: `
        color: #EEEEEE;
        font-weight: 400;
    `,
        youTubePreviewInfoText: `
        color: #ABABAB;
    `
    };

    function getConfig$1 (locale) {
        const locales = JSON.parse(localesJSON);
        const fbStrings = locales[locale]['facebook.json'];
        const ytStrings = locales[locale]['youtube.json'];

        const sharedStrings = locales[locale]['shared.json'];
        const config = {
            'Facebook, Inc.': {
                informationalModal: {
                    icon: blockedFBLogo$1,
                    messageTitle: fbStrings.informationalModalMessageTitle,
                    messageBody: fbStrings.informationalModalMessageBody,
                    confirmButtonText: fbStrings.informationalModalConfirmButtonText,
                    rejectButtonText: fbStrings.informationalModalRejectButtonText
                },
                elementData: {
                    'FB Like Button': {
                        selectors: [
                            '.fb-like'
                        ],
                        replaceSettings: {
                            type: 'blank'
                        }
                    },
                    'FB Button iFrames': {
                        selectors: [
                            "iframe[src*='://www.facebook.com/plugins/like.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/like.php']",
                            "iframe[src*='://www.facebook.com/plugins/share_button.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/share_button.php']"
                        ],
                        replaceSettings: {
                            type: 'blank'
                        }
                    },
                    'FB Save Button': {
                        selectors: [
                            '.fb-save'
                        ],
                        replaceSettings: {
                            type: 'blank'
                        }
                    },
                    'FB Share Button': {
                        selectors: [
                            '.fb-share-button'
                        ],
                        replaceSettings: {
                            type: 'blank'
                        }
                    },
                    'FB Page iFrames': {
                        selectors: [
                            "iframe[src*='://www.facebook.com/plugins/page.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/page.php']"
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockContent,
                            infoTitle: fbStrings.infoTitleUnblockContent,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'originalElement'
                        }
                    },
                    'FB Page Div': {
                        selectors: [
                            '.fb-page'
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockContent,
                            infoTitle: fbStrings.infoTitleUnblockContent,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'iFrame',
                            targetURL: 'https://www.facebook.com/plugins/page.php?href=data-href&tabs=data-tabs&width=data-width&height=data-height',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-tabs': {
                                    default: 'timeline'
                                },
                                'data-height': {
                                    default: '500'
                                },
                                'data-width': {
                                    default: '500'
                                }
                            },
                            styleDataAttributes: {
                                width: {
                                    name: 'data-width',
                                    unit: 'px'
                                },
                                height: {
                                    name: 'data-height',
                                    unit: 'px'
                                }
                            }
                        }
                    },
                    'FB Comment iFrames': {
                        selectors: [
                            "iframe[src*='://www.facebook.com/plugins/comment_embed.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/comment_embed.php']"
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockComment,
                            infoTitle: fbStrings.infoTitleUnblockComment,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'originalElement'
                        }
                    },
                    'FB Comments': {
                        selectors: [
                            '.fb-comments',
                            'fb\\:comments'
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockComments,
                            infoTitle: fbStrings.infoTitleUnblockComments,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'allowFull',
                            targetURL: 'https://www.facebook.com/v9.0/plugins/comments.php?href=data-href&numposts=data-numposts&sdk=joey&version=v9.0&width=data-width',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-numposts': {
                                    default: 10
                                },
                                'data-width': {
                                    default: '500'
                                }
                            }
                        }
                    },
                    'FB Embedded Comment Div': {
                        selectors: [
                            '.fb-comment-embed'
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockComment,
                            infoTitle: fbStrings.infoTitleUnblockComment,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'iFrame',
                            targetURL: 'https://www.facebook.com/v9.0/plugins/comment_embed.php?href=data-href&sdk=joey&width=data-width&include_parent=data-include-parent',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-width': {
                                    default: '500'
                                },
                                'data-include-parent': {
                                    default: 'false'
                                }
                            },
                            styleDataAttributes: {
                                width: {
                                    name: 'data-width',
                                    unit: 'px'
                                }
                            }
                        }
                    },
                    'FB Post iFrames': {
                        selectors: [
                            "iframe[src*='://www.facebook.com/plugins/post.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/post.php']"
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockPost,
                            infoTitle: fbStrings.infoTitleUnblockPost,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'originalElement'
                        }
                    },
                    'FB Posts Div': {
                        selectors: [
                            '.fb-post'
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockPost,
                            infoTitle: fbStrings.infoTitleUnblockPost,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'allowFull',
                            targetURL: 'https://www.facebook.com/v9.0/plugins/post.php?href=data-href&sdk=joey&show_text=true&width=data-width',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-width': {
                                    default: '500'
                                }
                            },
                            styleDataAttributes: {
                                width: {
                                    name: 'data-width',
                                    unit: 'px'
                                },
                                height: {
                                    name: 'data-height',
                                    unit: 'px',
                                    fallbackAttribute: 'data-width'
                                }
                            }
                        }
                    },
                    'FB Video iFrames': {
                        selectors: [
                            "iframe[src*='://www.facebook.com/plugins/video.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/video.php']"
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockVideo,
                            infoTitle: fbStrings.infoTitleUnblockVideo,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'originalElement'
                        }
                    },
                    'FB Video': {
                        selectors: [
                            '.fb-video'
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockVideo,
                            infoTitle: fbStrings.infoTitleUnblockVideo,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'iFrame',
                            targetURL: 'https://www.facebook.com/plugins/video.php?href=data-href&show_text=true&width=data-width',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-width': {
                                    default: '500'
                                }
                            },
                            styleDataAttributes: {
                                width: {
                                    name: 'data-width',
                                    unit: 'px'
                                },
                                height: {
                                    name: 'data-height',
                                    unit: 'px',
                                    fallbackAttribute: 'data-width'
                                }
                            }
                        }
                    },
                    'FB Group iFrames': {
                        selectors: [
                            "iframe[src*='://www.facebook.com/plugins/group.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/group.php']"
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockContent,
                            infoTitle: fbStrings.infoTitleUnblockContent,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'originalElement'
                        }
                    },
                    'FB Group': {
                        selectors: [
                            '.fb-group'
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockContent,
                            infoTitle: fbStrings.infoTitleUnblockContent,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'iFrame',
                            targetURL: 'https://www.facebook.com/plugins/group.php?href=data-href&width=data-width',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-width': {
                                    default: '500'
                                }
                            },
                            styleDataAttributes: {
                                width: {
                                    name: 'data-width',
                                    unit: 'px'
                                }
                            }
                        }
                    },
                    'FB Login Button': {
                        selectors: [
                            '.fb-login-button'
                        ],
                        replaceSettings: {
                            type: 'loginButton',
                            icon: blockedFBLogo$1,
                            buttonText: fbStrings.loginButtonText,
                            popupBodyText: fbStrings.loginBodyText
                        },
                        clickAction: {
                            type: 'allowFull',
                            targetURL: 'https://www.facebook.com/v9.0/plugins/login_button.php?app_id=app_id_replace&auto_logout_link=false&button_type=continue_with&sdk=joey&size=large&use_continue_as=false&width=',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-width': {
                                    default: '500'
                                },
                                app_id_replace: {
                                    default: 'null'
                                }
                            }
                        }
                    }
                }
            },
            Youtube: {
                informationalModal: {
                    icon: blockedYTVideo$1,
                    messageTitle: ytStrings.informationalModalMessageTitle,
                    messageBody: ytStrings.informationalModalMessageBody,
                    confirmButtonText: ytStrings.informationalModalConfirmButtonText,
                    rejectButtonText: ytStrings.informationalModalRejectButtonText
                },
                elementData: {
                    'YouTube embedded video': {
                        selectors: [
                            "iframe[src*='://youtube.com/embed']",
                            "iframe[src*='://youtube-nocookie.com/embed']",
                            "iframe[src*='://www.youtube.com/embed']",
                            "iframe[src*='://www.youtube-nocookie.com/embed']",
                            "iframe[data-src*='://youtube.com/embed']",
                            "iframe[data-src*='://youtube-nocookie.com/embed']",
                            "iframe[data-src*='://www.youtube.com/embed']",
                            "iframe[data-src*='://www.youtube-nocookie.com/embed']"
                        ],
                        replaceSettings: {
                            type: 'youtube-video',
                            buttonText: ytStrings.buttonTextUnblockVideo,
                            infoTitle: ytStrings.infoTitleUnblockVideo,
                            infoText: ytStrings.infoTextUnblockVideo,
                            previewToggleText: ytStrings.infoPreviewToggleText,
                            placeholder: {
                                previewToggleEnabledText: ytStrings.infoPreviewToggleEnabledText,
                                previewInfoText: ytStrings.infoPreviewInfoText,
                                videoPlayIcon: {
                                    lightMode: videoPlayLight$1,
                                    darkMode: videoPlayDark$1
                                }
                            }
                        },
                        clickAction: {
                            type: 'youtube-video'
                        }
                    },
                    'YouTube embedded subscription button': {
                        selectors: [
                            "iframe[src*='://youtube.com/subscribe_embed']",
                            "iframe[src*='://youtube-nocookie.com/subscribe_embed']",
                            "iframe[src*='://www.youtube.com/subscribe_embed']",
                            "iframe[src*='://www.youtube-nocookie.com/subscribe_embed']",
                            "iframe[data-src*='://youtube.com/subscribe_embed']",
                            "iframe[data-src*='://youtube-nocookie.com/subscribe_embed']",
                            "iframe[data-src*='://www.youtube.com/subscribe_embed']",
                            "iframe[data-src*='://www.youtube-nocookie.com/subscribe_embed']"
                        ],
                        replaceSettings: {
                            type: 'blank'
                        }
                    }
                }
            }
        };

        return { config, sharedStrings }
    }

    // @ts-nocheck

    let devMode$1 = false;
    let isYoutubePreviewsEnabled$1 = false;
    let appID$1;

    const titleID$1 = 'DuckDuckGoPrivacyEssentialsCTLElementTitle';

    // Configuration for how the placeholder elements should look and behave.
    // @see {getConfig}
    let config$1 = null;
    let sharedStrings$1 = null;

    // TODO: Remove these redundant data structures and refactor the related code.
    //       There should be no need to have the entity configuration stored in two
    //       places.
    const entities$1 = [];
    const entityData$1 = {};

    let readyResolver$1;
    const ready$1 = new Promise(resolve => { readyResolver$1 = resolve; });

    /*********************************************************
     *  Widget Replacement logic
     *********************************************************/
    let DuckWidget$1 = class DuckWidget {
        constructor (widgetData, originalElement, entity) {
            this.clickAction = { ...widgetData.clickAction }; // shallow copy
            this.replaceSettings = widgetData.replaceSettings;
            this.originalElement = originalElement;
            this.dataElements = {};
            this.gatherDataElements();
            this.entity = entity;
            this.widgetID = Math.random();
            // Boolean if widget is unblocked and content should not be blocked
            this.isUnblocked = false;
        }

        dispatchEvent (eventTarget, eventName) {
            eventTarget.dispatchEvent(
                createCustomEvent(
                    eventName, {
                        detail: {
                            entity: this.entity,
                            replaceSettings: this.replaceSettings,
                            widgetID: this.widgetID
                        }
                    }
                )
            );
        }

        // Collect and store data elements from original widget. Store default values
        // from config if not present.
        gatherDataElements () {
            if (!this.clickAction.urlDataAttributesToPreserve) {
                return
            }
            for (const [attrName, attrSettings] of Object.entries(this.clickAction.urlDataAttributesToPreserve)) {
                let value = this.originalElement.getAttribute(attrName);
                if (!value) {
                    if (attrSettings.required) {
                        // missing a required attribute means we won't be able to replace it
                        // with a light version, replace with full version.
                        this.clickAction.type = 'allowFull';
                    }
                    value = attrSettings.default;
                }
                this.dataElements[attrName] = value;
            }
        }

        // Return the facebook content URL to use when a user has clicked.
        getTargetURL () {
            // Copying over data fields should be done lazily, since some required data may not be
            // captured until after page scripts run.
            this.copySocialDataFields();
            return this.clickAction.targetURL
        }

        // Determine if element should render in dark mode
        getMode () {
            // Login buttons are always the login style types
            if (this.replaceSettings.type === 'loginButton') {
                return 'loginMode'
            }
            const mode = this.originalElement.getAttribute('data-colorscheme');
            if (mode === 'dark') {
                return 'darkMode'
            }
            return 'lightMode'
        }

        // The config file offers the ability to style the replaced facebook widget. This
        // collects the style from the original element & any specified in config for the element
        // type and returns a CSS string.
        getStyle () {
            let styleString = 'border: none;';

            if (this.clickAction.styleDataAttributes) {
                // Copy elements from the original div into style attributes as directed by config
                for (const [attr, valAttr] of Object.entries(this.clickAction.styleDataAttributes)) {
                    let valueFound = this.dataElements[valAttr.name];
                    if (!valueFound) {
                        valueFound = this.dataElements[valAttr.fallbackAttribute];
                    }
                    let partialStyleString = '';
                    if (valueFound) {
                        partialStyleString += `${attr}: ${valueFound}`;
                    }
                    if (!partialStyleString.includes(valAttr.unit)) {
                        partialStyleString += valAttr.unit;
                    }
                    partialStyleString += ';';
                    styleString += partialStyleString;
                }
            }

            return styleString
        }

        // Some data fields are 'kept' from the original element. These are used both in
        // replacement styling (darkmode, width, height), and when returning to a FB element.
        copySocialDataFields () {
            if (!this.clickAction.urlDataAttributesToPreserve) {
                return
            }

            // App ID may be set by client scripts, and is required for some elements.
            if (this.dataElements.app_id_replace && appID$1 != null) {
                this.clickAction.targetURL = this.clickAction.targetURL.replace('app_id_replace', appID$1);
            }

            for (const key of Object.keys(this.dataElements)) {
                let attrValue = this.dataElements[key];

                if (!attrValue) {
                    continue
                }

                // The URL for Facebook videos are specified as the data-href
                // attribute on a div, that is then used to create the iframe.
                // Some websites omit the protocol part of the URL when doing
                // that, which then prevents the iframe from loading correctly.
                if (key === 'data-href' && attrValue.startsWith('//')) {
                    attrValue = window.location.protocol + attrValue;
                }

                this.clickAction.targetURL =
                    this.clickAction.targetURL.replace(
                        key, encodeURIComponent(attrValue)
                    );
            }
        }

        /*
            * Creates an iFrame for this facebook content.
            *
            * @returns {Element}
            */
        createFBIFrame () {
            const frame = document.createElement('iframe');

            frame.setAttribute('src', this.getTargetURL());
            frame.setAttribute('style', this.getStyle());

            return frame
        }

        /**
        * Tweaks an embedded YouTube video element ready for when it's
        * reloaded.
        *
        * @param {Element} videoElement
        * @returns {Function?} onError
        *   Function to be called if the video fails to load.
        */
        adjustYouTubeVideoElement (videoElement) {
            let onError = null;

            if (!videoElement.src) {
                return onError
            }
            const url = new URL(videoElement.src);
            const { hostname: originalHostname } = url;

            // Upgrade video to YouTube's "privacy enhanced" mode, but fall back
            // to standard mode if the video fails to load.
            // Note:
            //  1. Changing the iframe's host like this won't cause a CSP
            //     violation on Chrome, see https://crbug.com/1271196.
            //  2. The onError event doesn't fire for blocked iframes on Chrome.
            if (originalHostname !== 'www.youtube-nocookie.com') {
                url.hostname = 'www.youtube-nocookie.com';
                onError = (event) => {
                    url.hostname = originalHostname;
                    videoElement.src = url.href;
                    event.stopImmediatePropagation();
                };
            }

            // Configure auto-play correctly depending on if the video's preview
            // loaded, otherwise it doesn't allow autoplay.
            let allowString = videoElement.getAttribute('allow') || '';
            const allowed = new Set(allowString.split(';').map(s => s.trim()));
            if (this.autoplay) {
                allowed.add('autoplay');
                url.searchParams.set('autoplay', '1');
            } else {
                allowed.delete('autoplay');
                url.searchParams.delete('autoplay');
            }
            allowString = Array.from(allowed).join('; ');
            videoElement.setAttribute('allow', allowString);

            videoElement.src = url.href;
            return onError
        }

        /*
            * Fades out the given element. Returns a promise that resolves when the fade is complete.
            * @param {Element} element - the element to fade in or out
            * @param {int} interval - frequency of opacity updates (ms)
            * @param {bool} fadeIn - true if the element should fade in instead of out
            */
        fadeElement (element, interval, fadeIn) {
            return new Promise((resolve, reject) => {
                let opacity = fadeIn ? 0 : 1;
                const originStyle = element.style.cssText;
                const fadeOut = setInterval(function () {
                    opacity += fadeIn ? 0.03 : -0.03;
                    element.style.cssText = originStyle + `opacity: ${opacity};`;
                    if (opacity <= 0 || opacity >= 1) {
                        clearInterval(fadeOut);
                        resolve();
                    }
                }, interval);
            })
        }

        fadeOutElement (element) {
            return this.fadeElement(element, 10, false)
        }

        fadeInElement (element) {
            return this.fadeElement(element, 10, true)
        }

        clickFunction (originalElement, replacementElement) {
            let clicked = false;
            const handleClick = async function handleClick (e) {
                // Ensure that the click is created by a user event & prevent double clicks from adding more animations
                if (e.isTrusted && !clicked) {
                    this.isUnblocked = true;
                    clicked = true;
                    let isLogin = false;
                    const clickElement = e.srcElement; // Object.assign({}, e)
                    if (this.replaceSettings.type === 'loginButton') {
                        isLogin = true;
                    }
                    window.addEventListener('ddg-ctp-unblockClickToLoadContent-complete', () => {
                        const parent = replacementElement.parentNode;

                        // If we allow everything when this element is clicked,
                        // notify surrogate to enable SDK and replace original element.
                        if (this.clickAction.type === 'allowFull') {
                            parent.replaceChild(originalElement, replacementElement);
                            this.dispatchEvent(window, 'ddg-ctp-load-sdk');
                            return
                        }
                        // Create a container for the new FB element
                        const fbContainer = document.createElement('div');
                        fbContainer.style.cssText = styles$1.wrapperDiv;
                        const fadeIn = document.createElement('div');
                        fadeIn.style.cssText = 'display: none; opacity: 0;';

                        // Loading animation (FB can take some time to load)
                        const loadingImg = document.createElement('img');
                        loadingImg.setAttribute('src', loadingImages$1[this.getMode()]);
                        loadingImg.setAttribute('height', '14px');
                        loadingImg.style.cssText = styles$1.loadingImg;

                        // Always add the animation to the button, regardless of click source
                        if (clickElement.nodeName === 'BUTTON') {
                            clickElement.firstElementChild.insertBefore(loadingImg, clickElement.firstElementChild.firstChild);
                        } else {
                            // try to find the button
                            let el = clickElement;
                            let button = null;
                            while (button === null && el !== null) {
                                button = el.querySelector('button');
                                el = el.parentElement;
                            }
                            if (button) {
                                button.firstElementChild.insertBefore(loadingImg, button.firstElementChild.firstChild);
                            }
                        }

                        fbContainer.appendChild(fadeIn);

                        let fbElement;
                        let onError = null;
                        switch (this.clickAction.type) {
                        case 'iFrame':
                            fbElement = this.createFBIFrame();
                            break
                        case 'youtube-video':
                            onError = this.adjustYouTubeVideoElement(originalElement);
                            fbElement = originalElement;
                            break
                        default:
                            fbElement = originalElement;
                            break
                        }

                        // If hidden, restore the tracking element's styles to make
                        // it visible again.
                        if (this.originalElementStyle) {
                            for (const key of ['display', 'visibility']) {
                                const { value, priority } = this.originalElementStyle[key];
                                if (value) {
                                    fbElement.style.setProperty(key, value, priority);
                                } else {
                                    fbElement.style.removeProperty(key);
                                }
                            }
                        }

                        /*
                        * Modify the overlay to include a Facebook iFrame, which
                        * starts invisible. Once loaded, fade out and remove the overlay
                        * then fade in the Facebook content
                        */
                        parent.replaceChild(fbContainer, replacementElement);
                        fbContainer.appendChild(replacementElement);
                        fadeIn.appendChild(fbElement);
                        fbElement.addEventListener('load', () => {
                            this.fadeOutElement(replacementElement)
                                .then(v => {
                                    fbContainer.replaceWith(fbElement);
                                    this.dispatchEvent(fbElement, 'ddg-ctp-placeholder-clicked');
                                    this.fadeInElement(fadeIn).then(() => {
                                        fbElement.focus(); // focus on new element for screen readers
                                    });
                                });
                        }, { once: true });
                        // Note: This event only fires on Firefox, on Chrome the frame's
                        //       load event will always fire.
                        if (onError) {
                            fbElement.addEventListener('error', onError, { once: true });
                        }
                    }, { once: true });
                    const action = this.entity === 'Youtube' ? 'block-ctl-yt' : 'block-ctl-fb';
                    unblockClickToLoadContent$1({ entity: this.entity, action, isLogin });
                }
            }.bind(this);
            // If this is a login button, show modal if needed
            if (this.replaceSettings.type === 'loginButton' && entityData$1[this.entity].shouldShowLoginModal) {
                return function handleLoginClick (e) {
                    makeModal$1(this.entity, handleClick, e);
                }.bind(this)
            }
            return handleClick
        }
    };

    function replaceTrackingElement$1 (widget, trackingElement, placeholderElement, hideTrackingElement = false, currentPlaceholder = null) {
        widget.dispatchEvent(trackingElement, 'ddg-ctp-tracking-element');

        // Usually the tracking element can simply be replaced with the
        // placeholder, but in some situations that isn't possible and the
        // tracking element must be hidden instead.
        if (hideTrackingElement) {
            // Don't save original element styles if we've already done it
            if (!widget.originalElementStyle) {
                // Take care to note existing styles so that they can be restored.
                widget.originalElementStyle = getOriginalElementStyle$1(trackingElement, widget);
            }
            // Hide the tracking element and add the placeholder next to it in
            // the DOM.
            trackingElement.style.setProperty('display', 'none', 'important');
            trackingElement.style.setProperty('visibility', 'hidden', 'important');
            trackingElement.parentElement.insertBefore(placeholderElement, trackingElement);
            if (currentPlaceholder) {
                currentPlaceholder.remove();
            }
        } else {
            if (currentPlaceholder) {
                currentPlaceholder.replaceWith(placeholderElement);
            } else {
                trackingElement.replaceWith(placeholderElement);
            }
        }

        widget.dispatchEvent(placeholderElement, 'ddg-ctp-placeholder-element');
    }

    /**
     * Creates a placeholder element for the given tracking element and replaces
     * it on the page.
     * @param {DuckWidget} widget
     *   The CTP 'widget' associated with the tracking element.
     * @param {Element} trackingElement
     *   The tracking element on the page that should be replaced with a placeholder.
     */
    async function createPlaceholderElementAndReplace$1 (widget, trackingElement) {
        if (widget.replaceSettings.type === 'blank') {
            replaceTrackingElement$1(widget, trackingElement, document.createElement('div'));
        }

        if (widget.replaceSettings.type === 'loginButton') {
            const icon = widget.replaceSettings.icon;
            // Create a button to replace old element
            const { button, container } = makeLoginButton$1(
                widget.replaceSettings.buttonText, widget.getMode(),
                widget.replaceSettings.popupBodyText, icon, trackingElement
            );
            button.addEventListener('click', widget.clickFunction(trackingElement, container));
            replaceTrackingElement$1(widget, trackingElement, container);
        }

        /** Facebook CTL */
        if (widget.replaceSettings.type === 'dialog') {
            const icon = widget.replaceSettings.icon;
            const button = makeButton$1(widget.replaceSettings.buttonText, widget.getMode());
            const textButton = makeTextButton$1(widget.replaceSettings.buttonText, widget.getMode());
            const { contentBlock, shadowRoot } = await createContentBlock$1(
                widget, button, textButton, icon
            );
            button.addEventListener('click', widget.clickFunction(trackingElement, contentBlock));
            textButton.addEventListener('click', widget.clickFunction(trackingElement, contentBlock));

            replaceTrackingElement$1(
                widget, trackingElement, contentBlock
            );

            // Show the extra unblock link in the header if the placeholder or
            // its parent is too short for the normal unblock button to be visible.
            // Note: This does not take into account the placeholder's vertical
            //       position in the parent element.
            const { height: placeholderHeight } = window.getComputedStyle(contentBlock);
            const { height: parentHeight } = window.getComputedStyle(contentBlock.parentElement);
            if (parseInt(placeholderHeight, 10) <= 200 || parseInt(parentHeight, 10) <= 200) {
                const titleRowTextButton = shadowRoot.querySelector(`#${titleID$1 + 'TextButton'}`);
                titleRowTextButton.style.display = 'block';

                // Avoid the placeholder being taller than the containing element
                // and overflowing.
                const innerDiv = shadowRoot.querySelector('.DuckDuckGoSocialContainer');
                innerDiv.style.minHeight = '';
                innerDiv.style.maxHeight = parentHeight;
                innerDiv.style.overflow = 'hidden';
            }
        }

        /** YouTube CTL */
        if (widget.replaceSettings.type === 'youtube-video') {
            sendMessage('updateYouTubeCTLAddedFlag', true);
            await replaceYouTubeCTL$1(trackingElement, widget);

            // Subscribe to changes to youtubePreviewsEnabled setting
            // and update the CTL state
            window.addEventListener('ddg-settings-youtubePreviewsEnabled', ({ detail: value }) => {
                isYoutubePreviewsEnabled$1 = value;
                replaceYouTubeCTL$1(trackingElement, widget, true);
            });
        }
    }

    /**
     * @param {Element} trackingElement
     *   The original tracking element (YouTube video iframe)
     * @param {DuckWidget} widget
     *   The CTP 'widget' associated with the tracking element.
     * @param {boolean} togglePlaceholder
     *   Boolean indicating if this function should toggle between placeholders,
     *   because tracking element has already been replaced
     */
    async function replaceYouTubeCTL$1 (trackingElement, widget, togglePlaceholder = false) {
        // Skip replacing tracking element if it has already been unblocked
        if (widget.isUnblocked) {
            return
        }

        // Show YouTube Preview for embedded video
        // TODO: Fix the hideTrackingElement option and reenable, or remove it. It's
        //       disabled for YouTube videos so far since it caused multiple
        //       placeholders to be displayed on the page.
        if (isYoutubePreviewsEnabled$1 === true) {
            const { youTubePreview, shadowRoot } = await createYouTubePreview$1(trackingElement, widget);
            const currentPlaceholder = togglePlaceholder ? document.getElementById(`yt-ctl-dialog-${widget.widgetID}`) : null;
            replaceTrackingElement$1(
                widget, trackingElement, youTubePreview, /* hideTrackingElement= */ false, currentPlaceholder
            );
            showExtraUnblockIfShortPlaceholder$1(shadowRoot, youTubePreview);

            // Block YouTube embedded video and display blocking dialog
        } else {
            widget.autoplay = false;
            const { blockingDialog, shadowRoot } = await createYouTubeBlockingDialog$1(trackingElement, widget);
            const currentPlaceholder = togglePlaceholder ? document.getElementById(`yt-ctl-preview-${widget.widgetID}`) : null;
            replaceTrackingElement$1(
                widget, trackingElement, blockingDialog, /* hideTrackingElement= */ false, currentPlaceholder
            );
            showExtraUnblockIfShortPlaceholder$1(shadowRoot, blockingDialog);
        }
    }

    /**
     /* Show the extra unblock link in the header if the placeholder or
    /* its parent is too short for the normal unblock button to be visible.
    /* Note: This does not take into account the placeholder's vertical
    /*       position in the parent element.
    * @param {Element} shadowRoot
    * @param {Element} placeholder Placeholder for tracking element
    */
    function showExtraUnblockIfShortPlaceholder$1 (shadowRoot, placeholder) {
        const { height: placeholderHeight } = window.getComputedStyle(placeholder);
        const { height: parentHeight } = window.getComputedStyle(placeholder.parentElement);
        if (parseInt(placeholderHeight, 10) <= 200 || parseInt(parentHeight, 10) <= 200) {
            const titleRowTextButton = shadowRoot.querySelector(`#${titleID$1 + 'TextButton'}`);
            titleRowTextButton.style.display = 'block';
        }
    }

    /**
     * Replace the blocked CTP elements on the page with placeholders.
     * @param {Element} [targetElement]
     *   If specified, only this element will be replaced (assuming it matches
     *   one of the expected CSS selectors). If omitted, all matching elements
     *   in the document will be replaced instead.
     */
    async function replaceClickToLoadElements$1 (targetElement) {
        await ready$1;

        for (const entity of Object.keys(config$1)) {
            for (const widgetData of Object.values(config$1[entity].elementData)) {
                const selector = widgetData.selectors.join();

                let trackingElements = [];
                if (targetElement) {
                    if (targetElement.matches(selector)) {
                        trackingElements.push(targetElement);
                    }
                } else {
                    trackingElements = Array.from(document.querySelectorAll(selector));
                }

                await Promise.all(trackingElements.map(trackingElement => {
                    const widget = new DuckWidget$1(widgetData, trackingElement, entity);
                    return createPlaceholderElementAndReplace$1(widget, trackingElement)
                }));
            }
        }
    }

    /*********************************************************
     *  Messaging to surrogates & extension
     *********************************************************/

    /**
     * @typedef unblockClickToLoadContentRequest
     * @property {string} entity
     *   The entity to unblock requests for (e.g. "Facebook, Inc.").
     * @property {bool} [isLogin=false]
     *   True if we should "allow social login", defaults to false.
     * @property {string} action
     *   The Click to Load blocklist rule action (e.g. "block-ctl-fb") that should
     *   be allowed. Important since in the future there might be multiple types of
     *   embedded content from the same entity that the user can allow
     *   independently.
     */

    /**
     * Send a message to the background to unblock requests for the given entity for
     * the page.
     * @param {unblockClickToLoadContentRequest} message
     * @see {@event ddg-ctp-unblockClickToLoadContent-complete} for the response handler.
     */
    function unblockClickToLoadContent$1 (message) {
        sendMessage('unblockClickToLoadContent', message);
    }

    function runLogin$1 (entity) {
        const action = entity === 'Youtube' ? 'block-ctl-yt' : 'block-ctl-fb';
        unblockClickToLoadContent$1({ entity, action, isLogin: true });
        originalWindowDispatchEvent(
            createCustomEvent('ddg-ctp-run-login', {
                detail: {
                    entity
                }
            })
        );
    }

    function cancelModal$1 (entity) {
        originalWindowDispatchEvent(
            createCustomEvent('ddg-ctp-cancel-modal', {
                detail: {
                    entity
                }
            })
        );
    }

    function openShareFeedbackPage$1 () {
        sendMessage('openShareFeedbackPage', '');
    }

    function getYouTubeVideoDetails$1 (videoURL) {
        sendMessage('getYouTubeVideoDetails', videoURL);
    }

    /*********************************************************
     *  Widget building blocks
     *********************************************************/
    function getLearnMoreLink$1 (mode) {
        if (!mode) {
            mode = 'lightMode';
        }

        const linkElement = document.createElement('a');
        linkElement.style.cssText = styles$1.generalLink + styles$1[mode].linkFont;
        linkElement.ariaLabel = sharedStrings$1.readAbout;
        linkElement.href = 'https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/';
        linkElement.target = '_blank';
        linkElement.textContent = sharedStrings$1.learnMore;
        return linkElement
    }

    /**
     * Reads and stores a set of styles from the original tracking element, and then returns it.
     * @param {Element} originalElement Original tracking element (ie iframe)
     * @param {DuckWidget} widget The widget Object.
     * @returns {{[key: string]: string[]}} Object with styles read from original element.
     */
    function getOriginalElementStyle$1 (originalElement, widget) {
        if (widget.originalElementStyle) {
            return widget.originalElementStyle
        }

        const stylesToCopy = ['display', 'visibility', 'position', 'top', 'bottom', 'left', 'right',
            'transform', 'margin'];
        widget.originalElementStyle = {};
        const allOriginalElementStyles = getComputedStyle(originalElement);
        for (const key of stylesToCopy) {
            widget.originalElementStyle[key] = {
                value: allOriginalElementStyles[key],
                priority: originalElement.style.getPropertyPriority(key)
            };
        }

        // Copy current size of the element
        const { height: heightViewValue, width: widthViewValue } = originalElement.getBoundingClientRect();
        widget.originalElementStyle.height = { value: `${heightViewValue}px`, priority: '' };
        widget.originalElementStyle.width = { value: `${widthViewValue}px`, priority: '' };

        return widget.originalElementStyle
    }

    /**
     * Copy list of styles to provided element
     * @param {{[key: string]: string[]}} originalStyles Object with styles read from original element.
     * @param {Element} element Node element to have the styles copied to
     */
    function copyStylesTo$1 (originalStyles, element) {
        const { display, visibility, ...filteredStyles } = originalStyles;
        const cssText = Object.keys(filteredStyles).reduce((cssAcc, key) => (cssAcc + `${key}: ${filteredStyles[key].value};`), '');
        element.style.cssText += cssText;
    }

    /**
     * Create a `<style/>` element with DDG font-face styles/CSS
     * to be attached to DDG wrapper elements
     * @returns HTMLStyleElement
     */
    function makeFontFaceStyleElement$1 () {
        // Put our custom font-faces inside the wrapper element, since
        // @font-face does not work inside a shadowRoot.
        // See https://github.com/mdn/interactive-examples/issues/887.
        const fontFaceStyleElement = document.createElement('style');
        fontFaceStyleElement.textContent = styles$1.fontStyle;
        return fontFaceStyleElement
    }

    /**
     * Create a `<style/>` element with base styles for DDG social container and
     * button to be attached to DDG wrapper elements/shadowRoot, also returns a wrapper
     * class name for Social Container link styles
     * @param {"lightMode" | "darkMode"} mode Light or Dark mode value
     * @returns {{wrapperClass: string, styleElement: HTMLStyleElement; }}
     */
    function makeBaseStyleElement$1 (mode = 'lightMode') {
        // Style element includes our font & overwrites page styles
        const styleElement = document.createElement('style');
        const wrapperClass = 'DuckDuckGoSocialContainer';
        styleElement.textContent = `
        .${wrapperClass} a {
            ${styles$1[mode].linkFont}
            font-weight: bold;
        }
        .${wrapperClass} a:hover {
            ${styles$1[mode].linkFont}
            font-weight: bold;
        }
        .DuckDuckGoButton {
            ${styles$1.button}
        }
        .DuckDuckGoButton > div {
            ${styles$1.buttonTextContainer}
        }
        .DuckDuckGoButton.primary {
           ${styles$1[mode].buttonBackground}
        }
        .DuckDuckGoButton.primary > div {
           ${styles$1[mode].buttonFont}
        }
        .DuckDuckGoButton.primary:hover {
           ${styles$1[mode].buttonBackgroundHover}
        }
        .DuckDuckGoButton.primary:active {
           ${styles$1[mode].buttonBackgroundPress}
        }
        .DuckDuckGoButton.secondary {
           ${styles$1.cancelMode.buttonBackground}
        }
        .DuckDuckGoButton.secondary > div {
            ${styles$1.cancelMode.buttonFont}
         }
        .DuckDuckGoButton.secondary:hover {
           ${styles$1.cancelMode.buttonBackgroundHover}
        }
        .DuckDuckGoButton.secondary:active {
           ${styles$1.cancelMode.buttonBackgroundPress}
        }
    `;
        return { wrapperClass, styleElement }
    }

    function makeTextButton$1 (linkText, mode) {
        const linkElement = document.createElement('a');
        linkElement.style.cssText = styles$1.headerLink + styles$1[mode].linkFont;
        linkElement.textContent = linkText;
        return linkElement
    }

    /**
     * Create a button element.
     * @param {string} buttonText Text to be displayed inside the button
     * @param {'lightMode' | 'darkMode' | 'cancelMode'} mode Key for theme value to determine the styling of the button. Key matches `styles[mode]` keys.
     * - `'lightMode'`: Primary colors styling for light theme
     * - `'darkMode'`: Primary colors styling for dark theme
     * - `'cancelMode'`: Secondary colors styling for all themes
     * @returns {HTMLButtonElement} Button element
     */
    function makeButton$1 (buttonText, mode = 'lightMode') {
        const button = document.createElement('button');
        button.classList.add('DuckDuckGoButton');
        button.classList.add(mode === 'cancelMode' ? 'secondary' : 'primary');
        if (buttonText) {
            const textContainer = document.createElement('div');
            textContainer.textContent = buttonText;
            button.appendChild(textContainer);
        }
        return button
    }

    function makeToggleButton$1 (isActive = false, classNames = '', dataKey = '') {
        const toggleButton = document.createElement('button');
        toggleButton.className = classNames;
        toggleButton.style.cssText = styles$1.toggleButton;
        toggleButton.type = 'button';
        toggleButton.setAttribute('aria-pressed', isActive ? 'true' : 'false');
        toggleButton.setAttribute('data-key', dataKey);

        const toggleBg = document.createElement('div');
        toggleBg.style.cssText = styles$1.toggleButtonBg + (isActive ? styles$1.toggleButtonBgState.active : styles$1.toggleButtonBgState.inactive);

        const toggleKnob = document.createElement('div');
        toggleKnob.style.cssText = styles$1.toggleButtonKnob + (isActive ? styles$1.toggleButtonKnobState.active : styles$1.toggleButtonKnobState.inactive);

        toggleButton.appendChild(toggleBg);
        toggleButton.appendChild(toggleKnob);

        return toggleButton
    }

    function makeToggleButtonWithText$1 (text, mode, isActive = false, toggleCssStyles = '', textCssStyles = '', dataKey = '') {
        const wrapper = document.createElement('div');
        wrapper.style.cssText = styles$1.toggleButtonWrapper;

        const toggleButton = makeToggleButton$1(isActive, toggleCssStyles, dataKey);

        const textDiv = document.createElement('div');
        textDiv.style.cssText = styles$1.contentText + styles$1.toggleButtonText + styles$1[mode].toggleButtonText + textCssStyles;
        textDiv.textContent = text;

        wrapper.appendChild(toggleButton);
        wrapper.appendChild(textDiv);
        return wrapper
    }

    /* If there isn't an image available, just make a default block symbol */
    function makeDefaultBlockIcon$1 () {
        const blockedIcon = document.createElement('div');
        const dash = document.createElement('div');
        blockedIcon.appendChild(dash);
        blockedIcon.style.cssText = styles$1.circle;
        dash.style.cssText = styles$1.rectangle;
        return blockedIcon
    }

    function makeShareFeedbackLink$1 () {
        const feedbackLink = document.createElement('a');
        feedbackLink.style.cssText = styles$1.feedbackLink;
        feedbackLink.target = '_blank';
        feedbackLink.href = '#';
        feedbackLink.text = 'Share Feedback';
        // Open Feedback Form page through background event to avoid browser blocking extension link
        feedbackLink.addEventListener('click', function (e) {
            e.preventDefault();
            openShareFeedbackPage$1();
        });

        return feedbackLink
    }

    function makeShareFeedbackRow$1 () {
        const feedbackRow = document.createElement('div');
        feedbackRow.style.cssText = styles$1.feedbackRow;

        const feedbackLink = makeShareFeedbackLink$1();
        feedbackRow.appendChild(feedbackLink);

        return feedbackRow
    }

    /* FB login replacement button, with hover text */
    function makeLoginButton$1 (buttonText, mode, hoverTextBody, icon, originalElement) {
        const container = document.createElement('div');
        container.style.cssText = 'position: relative;';
        container.appendChild(makeFontFaceStyleElement$1());

        const shadowRoot = container.attachShadow({ mode: devMode$1 ? 'open' : 'closed' });
        // inherit any class styles on the button
        container.className = 'fb-login-button FacebookLogin__button';
        const { styleElement } = makeBaseStyleElement$1(mode);
        styleElement.textContent += `
        #DuckDuckGoPrivacyEssentialsHoverableText {
            display: none;
        }
        #DuckDuckGoPrivacyEssentialsHoverable:hover #DuckDuckGoPrivacyEssentialsHoverableText {
            display: block;
        }
    `;
        shadowRoot.appendChild(styleElement);

        const hoverContainer = document.createElement('div');
        hoverContainer.id = 'DuckDuckGoPrivacyEssentialsHoverable';
        hoverContainer.style.cssText = styles$1.hoverContainer;
        shadowRoot.appendChild(hoverContainer);

        // Make the button
        const button = makeButton$1(buttonText, mode);
        // Add blocked icon
        if (!icon) {
            button.appendChild(makeDefaultBlockIcon$1());
        } else {
            const imgElement = document.createElement('img');
            imgElement.style.cssText = styles$1.loginIcon;
            imgElement.setAttribute('src', icon);
            imgElement.setAttribute('height', '28px');
            button.appendChild(imgElement);
        }
        hoverContainer.appendChild(button);

        // hover action
        const hoverBox = document.createElement('div');
        hoverBox.id = 'DuckDuckGoPrivacyEssentialsHoverableText';
        hoverBox.style.cssText = styles$1.textBubble;
        const arrow = document.createElement('div');
        arrow.style.cssText = styles$1.textArrow;
        hoverBox.appendChild(arrow);
        const branding = createTitleRow$1('DuckDuckGo');
        branding.style.cssText += styles$1.hoverTextTitle;
        hoverBox.appendChild(branding);
        const hoverText = document.createElement('div');
        hoverText.style.cssText = styles$1.hoverTextBody;
        hoverText.textContent = hoverTextBody + ' ';
        hoverText.appendChild(getLearnMoreLink$1(mode));
        hoverBox.appendChild(hoverText);

        hoverContainer.appendChild(hoverBox);
        const rect = originalElement.getBoundingClientRect();
        /*
        * The left side of the hover popup may go offscreen if the
        * login button is all the way on the left side of the page. This
        * If that is the case, dynamically shift the box right so it shows
        * properly.
        */
        if (rect.left < styles$1.textBubbleLeftShift) {
            const leftShift = -rect.left + 10; // 10px away from edge of the screen
            hoverBox.style.cssText += `left: ${leftShift}px;`;
            const change = (1 - (rect.left / styles$1.textBubbleLeftShift)) * (100 - styles$1.arrowDefaultLocationPercent);
            arrow.style.cssText += `left: ${Math.max(10, styles$1.arrowDefaultLocationPercent - change)}%;`;
        } else if (rect.left + styles$1.textBubbleWidth - styles$1.textBubbleLeftShift > window.innerWidth) {
            const rightShift = rect.left + styles$1.textBubbleWidth - styles$1.textBubbleLeftShift;
            const diff = Math.min(rightShift - window.innerWidth, styles$1.textBubbleLeftShift);
            const rightMargin = 20; // Add some margin to the page, so scrollbar doesn't overlap.
            hoverBox.style.cssText += `left: -${styles$1.textBubbleLeftShift + diff + rightMargin}px;`;
            const change = ((diff / styles$1.textBubbleLeftShift)) * (100 - styles$1.arrowDefaultLocationPercent);
            arrow.style.cssText += `left: ${Math.max(10, styles$1.arrowDefaultLocationPercent + change)}%;`;
        } else {
            hoverBox.style.cssText += `left: -${styles$1.textBubbleLeftShift}px;`;
            arrow.style.cssText += `left: ${styles$1.arrowDefaultLocationPercent}%;`;
        }

        return {
            button,
            container
        }
    }

    async function makeModal$1 (entity, acceptFunction, ...acceptFunctionParams) {
        const icon = entityData$1[entity].modalIcon;

        const modalContainer = document.createElement('div');
        modalContainer.setAttribute('data-key', 'modal');
        modalContainer.style.cssText = styles$1.modalContainer;

        modalContainer.appendChild(makeFontFaceStyleElement$1());

        const closeModal = () => {
            document.body.removeChild(modalContainer);
            cancelModal$1(entity);
        };

        // Protect the contents of our modal inside a shadowRoot, to avoid
        // it being styled by the website's stylesheets.
        const shadowRoot = modalContainer.attachShadow({ mode: devMode$1 ? 'open' : 'closed' });
        const { styleElement } = makeBaseStyleElement$1('lightMode');
        shadowRoot.appendChild(styleElement);

        const pageOverlay = document.createElement('div');
        pageOverlay.style.cssText = styles$1.overlay;

        const modal = document.createElement('div');
        modal.style.cssText = styles$1.modal;

        // Title
        const modalTitle = createTitleRow$1('DuckDuckGo', null, closeModal);
        modal.appendChild(modalTitle);

        const iconElement = document.createElement('img');
        iconElement.style.cssText = styles$1.icon + styles$1.modalIcon;
        iconElement.setAttribute('src', icon);
        iconElement.setAttribute('height', '70px');

        const title = document.createElement('div');
        title.style.cssText = styles$1.modalContentTitle;
        title.textContent = entityData$1[entity].modalTitle;

        // Content
        const modalContent = document.createElement('div');
        modalContent.style.cssText = styles$1.modalContent;

        const message = document.createElement('div');
        message.style.cssText = styles$1.modalContentText;
        message.textContent = entityData$1[entity].modalText + ' ';
        message.appendChild(getLearnMoreLink$1());

        modalContent.appendChild(iconElement);
        modalContent.appendChild(title);
        modalContent.appendChild(message);

        // Buttons
        const buttonRow = document.createElement('div');
        buttonRow.style.cssText = styles$1.modalButtonRow;
        const allowButton = makeButton$1(entityData$1[entity].modalAcceptText, 'lightMode');
        allowButton.style.cssText += styles$1.modalButton + 'margin-bottom: 8px;';
        allowButton.setAttribute('data-key', 'allow');
        allowButton.addEventListener('click', function doLogin () {
            acceptFunction(...acceptFunctionParams);
            document.body.removeChild(modalContainer);
        });
        const rejectButton = makeButton$1(entityData$1[entity].modalRejectText, 'cancelMode');
        rejectButton.setAttribute('data-key', 'reject');
        rejectButton.style.cssText += styles$1.modalButton;
        rejectButton.addEventListener('click', closeModal);

        buttonRow.appendChild(allowButton);
        buttonRow.appendChild(rejectButton);
        modalContent.appendChild(buttonRow);

        modal.appendChild(modalContent);

        shadowRoot.appendChild(pageOverlay);
        shadowRoot.appendChild(modal);

        document.body.insertBefore(modalContainer, document.body.childNodes[0]);
    }

    function createTitleRow$1 (message, textButton, closeBtnFn) {
        // Create row container
        const row = document.createElement('div');
        row.style.cssText = styles$1.titleBox;

        // Logo
        const logoContainer = document.createElement('div');
        logoContainer.style.cssText = styles$1.logo;
        const logoElement = document.createElement('img');
        logoElement.setAttribute('src', logoImg$1);
        logoElement.setAttribute('height', '21px');
        logoElement.style.cssText = styles$1.logoImg;
        logoContainer.appendChild(logoElement);
        row.appendChild(logoContainer);

        // Content box title
        const msgElement = document.createElement('div');
        msgElement.id = titleID$1; // Ensure we can find this to potentially hide it later.
        msgElement.textContent = message;
        msgElement.style.cssText = styles$1.title;
        row.appendChild(msgElement);

        // Close Button
        if (typeof closeBtnFn === 'function') {
            const closeButton = document.createElement('button');
            closeButton.style.cssText = styles$1.closeButton;
            const closeIconImg = document.createElement('img');
            closeIconImg.setAttribute('src', closeIcon$1);
            closeIconImg.setAttribute('height', '12px');
            closeIconImg.style.cssText = styles$1.closeIcon;
            closeButton.appendChild(closeIconImg);
            closeButton.addEventListener('click', closeBtnFn);
            row.appendChild(closeButton);
        }

        // Text button for very small boxes
        if (textButton) {
            textButton.id = titleID$1 + 'TextButton';
            row.appendChild(textButton);
        }

        return row
    }

    // Create the content block to replace other divs/iframes with
    async function createContentBlock$1 (widget, button, textButton, img, bottomRow) {
        const contentBlock = document.createElement('div');
        contentBlock.style.cssText = styles$1.wrapperDiv;

        contentBlock.appendChild(makeFontFaceStyleElement$1());

        // Put everything else inside the shadowRoot of the wrapper element to
        // reduce the chances of the website's stylesheets messing up the
        // placeholder's appearance.
        const shadowRootMode = devMode$1 ? 'open' : 'closed';
        const shadowRoot = contentBlock.attachShadow({ mode: shadowRootMode });

        // Style element includes our font & overwrites page styles
        const { wrapperClass, styleElement } = makeBaseStyleElement$1(widget.getMode());
        shadowRoot.appendChild(styleElement);

        // Create overall grid structure
        const element = document.createElement('div');
        element.style.cssText = styles$1.block + styles$1[widget.getMode()].background + styles$1[widget.getMode()].textFont;
        if (widget.replaceSettings.type === 'youtube-video') {
            element.style.cssText += styles$1.youTubeDialogBlock;
        }
        element.className = wrapperClass;
        shadowRoot.appendChild(element);

        // grid of three rows
        const titleRow = document.createElement('div');
        titleRow.style.cssText = styles$1.headerRow;
        element.appendChild(titleRow);
        titleRow.appendChild(createTitleRow$1('DuckDuckGo', textButton));

        const contentRow = document.createElement('div');
        contentRow.style.cssText = styles$1.content;

        if (img) {
            const imageRow = document.createElement('div');
            imageRow.style.cssText = styles$1.imgRow;
            const imgElement = document.createElement('img');
            imgElement.style.cssText = styles$1.icon;
            imgElement.setAttribute('src', img);
            imgElement.setAttribute('height', '70px');
            imageRow.appendChild(imgElement);
            element.appendChild(imageRow);
        }

        const contentTitle = document.createElement('div');
        contentTitle.style.cssText = styles$1.contentTitle;
        contentTitle.textContent = widget.replaceSettings.infoTitle;
        contentRow.appendChild(contentTitle);
        const contentText = document.createElement('div');
        contentText.style.cssText = styles$1.contentText;
        contentText.textContent = widget.replaceSettings.infoText + ' ';
        contentText.appendChild(getLearnMoreLink$1());
        contentRow.appendChild(contentText);
        element.appendChild(contentRow);

        const buttonRow = document.createElement('div');
        buttonRow.style.cssText = styles$1.buttonRow;
        buttonRow.appendChild(button);
        contentText.appendChild(buttonRow);

        if (bottomRow) {
            contentRow.appendChild(bottomRow);
        }

        /** Share Feedback Link */
        if (widget.replaceSettings.type === 'youtube-video') {
            const feedbackRow = makeShareFeedbackRow$1();
            shadowRoot.appendChild(feedbackRow);
        }

        return { contentBlock, shadowRoot }
    }

    // Create the content block to replace embedded youtube videos/iframes with
    async function createYouTubeBlockingDialog$1 (trackingElement, widget) {
        const button = makeButton$1(widget.replaceSettings.buttonText, widget.getMode());
        const textButton = makeTextButton$1(widget.replaceSettings.buttonText, widget.getMode());

        const bottomRow = document.createElement('div');
        bottomRow.style.cssText = styles$1.youTubeDialogBottomRow;
        const previewToggle = makeToggleButtonWithText$1(
            widget.replaceSettings.previewToggleText,
            widget.getMode(),
            false,
            '',
            '',
            'yt-preview-toggle'
        );
        previewToggle.addEventListener(
            'click',
            () => makeModal$1(widget.entity, () => sendMessage('setYoutubePreviewsEnabled', true), widget.entity)
        );
        bottomRow.appendChild(previewToggle);

        const { contentBlock, shadowRoot } = await createContentBlock$1(
            widget, button, textButton, null, bottomRow
        );
        contentBlock.id = `yt-ctl-dialog-${widget.widgetID}`;
        contentBlock.style.cssText += styles$1.wrapperDiv + styles$1.youTubeWrapperDiv;

        button.addEventListener('click', widget.clickFunction(trackingElement, contentBlock));
        textButton.addEventListener('click', widget.clickFunction(trackingElement, contentBlock));

        // Size the placeholder element to match the original video element styles.
        // If no styles are in place, it will get its current size
        const originalStyles = getOriginalElementStyle$1(trackingElement, widget);
        copyStylesTo$1(originalStyles, contentBlock);

        return {
            blockingDialog: contentBlock,
            shadowRoot
        }
    }

    /**
     * Creates the placeholder element to replace a YouTube video iframe element
     * with a preview image. Mutates widget Object to set the autoplay property
     * as the preview details load.
     * @param {Element} originalElement
     *   The YouTube video iframe element.
     * @param {DuckWidget} widget
     *   The widget Object. We mutate this to set the autoplay property.
     * @returns {{ youTubePreview: Element, shadowRoot: Element }}
     *   Object containing the YouTube Preview element and its shadowRoot.
     */
    async function createYouTubePreview$1 (originalElement, widget) {
        const youTubePreview = document.createElement('div');
        youTubePreview.id = `yt-ctl-preview-${widget.widgetID}`;
        youTubePreview.style.cssText = styles$1.wrapperDiv + styles$1.placeholderWrapperDiv;

        youTubePreview.appendChild(makeFontFaceStyleElement$1());

        // Size the placeholder element to match the original video element styles.
        // If no styles are in place, it will get its current size
        const originalStyles = getOriginalElementStyle$1(originalElement, widget);
        copyStylesTo$1(originalStyles, youTubePreview);

        // Protect the contents of our placeholder inside a shadowRoot, to avoid
        // it being styled by the website's stylesheets.
        const shadowRoot = youTubePreview.attachShadow({ mode: devMode$1 ? 'open' : 'closed' });
        const { wrapperClass, styleElement } = makeBaseStyleElement$1(widget.getMode());
        shadowRoot.appendChild(styleElement);

        const youTubePreviewDiv = document.createElement('div');
        youTubePreviewDiv.style.cssText = styles$1.youTubeDialogDiv;
        youTubePreviewDiv.classList.add(wrapperClass);
        shadowRoot.appendChild(youTubePreviewDiv);

        /** Preview Image */
        const previewImageWrapper = document.createElement('div');
        previewImageWrapper.style.cssText = styles$1.youTubePreviewWrapperImg;
        youTubePreviewDiv.appendChild(previewImageWrapper);
        // We use an image element for the preview image so that we can ensure
        // the referrer isn't passed.
        const previewImageElement = document.createElement('img');
        previewImageElement.setAttribute('referrerPolicy', 'no-referrer');
        previewImageElement.style.cssText = styles$1.youTubePreviewImg;
        previewImageWrapper.appendChild(previewImageElement);

        const innerDiv = document.createElement('div');
        innerDiv.style.cssText = styles$1.youTubePlaceholder;

        /** Top section */
        const topSection = document.createElement('div');
        topSection.style.cssText = styles$1.youTubeTopSection;
        innerDiv.appendChild(topSection);

        /** Video Title */
        const titleElement = document.createElement('p');
        titleElement.style.cssText = styles$1.youTubeTitle;
        topSection.appendChild(titleElement);

        /** Text Button on top section */
        // Use darkMode styles because the preview background is dark and causes poor contrast
        // with lightMode button, making it hard to read.
        const textButton = makeTextButton$1(widget.replaceSettings.buttonText, 'darkMode');
        textButton.id = titleID$1 + 'TextButton';

        textButton.addEventListener(
            'click',
            widget.clickFunction(originalElement, youTubePreview)
        );
        topSection.appendChild(textButton);

        /** Play Button */
        const playButtonRow = document.createElement('div');
        playButtonRow.style.cssText = styles$1.youTubePlayButtonRow;

        const playButton = makeButton$1('', widget.getMode());
        playButton.style.cssText += styles$1.youTubePlayButton;

        const videoPlayImg = document.createElement('img');
        const videoPlayIcon = widget.replaceSettings.placeholder.videoPlayIcon[widget.getMode()];
        videoPlayImg.setAttribute('src', videoPlayIcon);
        playButton.appendChild(videoPlayImg);

        playButton.addEventListener(
            'click',
            widget.clickFunction(originalElement, youTubePreview)
        );
        playButtonRow.appendChild(playButton);
        innerDiv.appendChild(playButtonRow);

        /** Preview Toggle */
        const previewToggleRow = document.createElement('div');
        previewToggleRow.style.cssText = styles$1.youTubePreviewToggleRow;

        const previewToggle = makeToggleButtonWithText$1(
            widget.replaceSettings.placeholder.previewToggleEnabledText,
            widget.getMode(),
            true,
            '',
            styles$1.youTubePreviewToggleText,
            'yt-preview-toggle'
        );
        previewToggle.addEventListener(
            'click',
            () => sendMessage('setYoutubePreviewsEnabled', false)
        );

        /** Preview Info Text */
        const previewText = document.createElement('div');
        previewText.style.cssText = styles$1.contentText + styles$1.toggleButtonText + styles$1.youTubePreviewInfoText;
        previewText.innerText = widget.replaceSettings.placeholder.previewInfoText + ' ';
        // Use darkMode styles because of preview background
        previewText.appendChild(getLearnMoreLink$1('darkMode'));

        previewToggleRow.appendChild(previewToggle);
        previewToggleRow.appendChild(previewText);
        innerDiv.appendChild(previewToggleRow);

        youTubePreviewDiv.appendChild(innerDiv);

        widget.autoplay = false;
        // We use .then() instead of await here to show the placeholder right away
        // while the YouTube endpoint takes it time to respond.
        const videoURL = originalElement.src || originalElement.getAttribute('data-src');
        getYouTubeVideoDetails$1(videoURL);
        window.addEventListener('ddg-ctp-youTubeVideoDetails',
            ({ detail: { videoURL: videoURLResp, status, title, previewImage } }) => {
                if (videoURLResp !== videoURL) { return }
                if (status === 'success') {
                    titleElement.innerText = title;
                    titleElement.title = title;
                    if (previewImage) {
                        previewImageElement.setAttribute('src', previewImage);
                    }
                    widget.autoplay = true;
                }
            }
        );

        /** Share Feedback Link */
        const feedbackRow = makeShareFeedbackRow$1();
        shadowRoot.appendChild(feedbackRow);

        return { youTubePreview, shadowRoot }
    }

    // Convention is that each function should be named the same as the sendMessage
    // method we are calling into eg. calling `sendMessage('getClickToLoadState')`
    // will result in a response routed to `updateHandlers.getClickToLoadState()`.
    const messageResponseHandlers$1 = {
        getClickToLoadState (response) {
            devMode$1 = response.devMode;
            isYoutubePreviewsEnabled$1 = response.youtubePreviewsEnabled;

            // TODO: Move the below init logic to the exported init() function,
            //       somehow waiting for this response handler to have been called
            //       first.

            // Start Click to Load
            window.addEventListener('ddg-ctp-replace-element', ({ target }) => {
                replaceClickToLoadElements$1(target);
            }, { capture: true });

            // Inform surrogate scripts that CTP is ready
            originalWindowDispatchEvent(createCustomEvent('ddg-ctp-ready'));

            // Mark the feature as ready, to allow placeholder replacements.
            readyResolver$1();
        },
        setYoutubePreviewsEnabled: function (resp) {
            if (resp?.messageType && typeof resp?.value === 'boolean') {
                originalWindowDispatchEvent(new OriginalCustomEvent(resp.messageType, { detail: resp.value }));
            }
        },
        getYouTubeVideoDetails: function (resp) {
            if (resp?.status && typeof resp.videoURL === 'string') {
                originalWindowDispatchEvent(new OriginalCustomEvent('ddg-ctp-youTubeVideoDetails', { detail: resp }));
            }
        },
        unblockClickToLoadContent () {
            originalWindowDispatchEvent(new OriginalCustomEvent('ddg-ctp-unblockClickToLoadContent-complete'));
        }
    };

    const knownMessageResponseType$1 = Object.prototype.hasOwnProperty.bind(messageResponseHandlers$1);

    function init$g (args) {
        const websiteOwner = args?.site?.parentEntity;
        const settings = args?.featureSettings?.clickToLoad || {};
        const locale = args?.locale || 'en';
        const localizedConfig = getConfig$1(locale);
        config$1 = localizedConfig.config;
        sharedStrings$1 = localizedConfig.sharedStrings;

        for (const entity of Object.keys(config$1)) {
            // Strip config entities that are first-party, or aren't enabled in the
            // extension's clickToLoad settings.
            if ((websiteOwner && entity === websiteOwner) ||
                !settings[entity] ||
                settings[entity].state !== 'enabled') {
                delete config$1[entity];
                continue
            }

            // Populate the entities and entityData data structures.
            // TODO: Remove them and this logic, they seem unnecessary.

            entities$1.push(entity);

            const shouldShowLoginModal = !!config$1[entity].informationalModal;
            const currentEntityData = { shouldShowLoginModal };

            if (shouldShowLoginModal) {
                const { informationalModal } = config$1[entity];
                currentEntityData.modalIcon = informationalModal.icon;
                currentEntityData.modalTitle = informationalModal.messageTitle;
                currentEntityData.modalText = informationalModal.messageBody;
                currentEntityData.modalAcceptText = informationalModal.confirmButtonText;
                currentEntityData.modalRejectText = informationalModal.rejectButtonText;
            }

            entityData$1[entity] = currentEntityData;
        }

        // Listen for events from "surrogate" scripts.
        addEventListener('ddg-ctp', (event) => {
            if (!event.detail) return
            const entity = event.detail.entity;
            if (!entities$1.includes(entity)) {
                // Unknown entity, reject
                return
            }
            if (event.detail.appID) {
                appID$1 = JSON.stringify(event.detail.appID).replace(/"/g, '');
            }
            // Handle login call
            if (event.detail.action === 'login') {
                if (entityData$1[entity].shouldShowLoginModal) {
                    makeModal$1(entity, runLogin$1, entity);
                } else {
                    runLogin$1(entity);
                }
            }
        });

        // Request the current state of Click to Load from the platform.
        // Note: When the response is received, the response handler finishes
        //       starting up the feature.
        sendMessage('getClickToLoadState');
    }

    function update$2 (message) {
        // TODO: Once all Click to Load messages include the feature property, drop
        //       messages that don't include the feature property too.
        if (message?.feature && message?.feature !== 'clickToLoad') return

        const messageType = message?.messageType;
        if (!messageType) return

        // Message responses.
        if (messageType === 'response') {
            const messageResponseType = message?.responseMessageType;
            if (messageResponseType && knownMessageResponseType$1(messageResponseType)) {
                return messageResponseHandlers$1[messageResponseType](message.response)
            }
        }

        // Other known update messages.
        if (messageType === 'displayClickToLoadPlaceholders') {
            // TODO: Pass `message.options.ruleAction` through, that way only
            //       content corresponding to the entity for that ruleAction need to
            //       be replaced with a placeholder.
            return replaceClickToLoadElements$1()
        }
    }

    var clickToLoad = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$g,
        update: update$2
    });

    const logoImg = 'data:application/octet-stream;base64,iVBORw0KGgoAAAANSUhEUgAAAIQAAACECAMAAABmmnOVAAA0aXpUWHRSYXcgcHJvZmlsZSB0eXBlIGV4aWYAAHjarZxpkhw5kqX/4xR1BOzLcQAFIDI36OP392BB5lY9Uy0yyWQyMsLpbgaovkVVYe781/+57l//+lcIPXeXS+t11Or5J4884uSL7r9/xvtv8Pn99/3T88/Pwl+/737/IPKtxJ/p+982f14/+X750xv9ep/11++7/vOT2H/eKPx+4/dP0ifr6/3ni+T78ft++LkQN873RR29/flS188b2a8r7n/8zr8v6/tD/+/+8o3GKu3CB6UYTwrJv//m7wqSfoc0+TPx35Aar/Np8HVJ0b1vxZ83Y0H+cnu//vT+zwv0l0X+9ZX7++r//upvix/nz/fT39ay/qwRX/zbH4Tyt++n3x8T//zB6fcVxb/+IIS4/3E7P7/v3f3e893dzJUVrT8R9RY7/HobXrhY8vT+WuVX43fh6/Z+DX51P72x5dubX/yyMEJkV64LOewwww3n/WnBuMQcT2RPYozGRul7nT0a0dgldlG/wo2NHdups5cWj2Mrc4q/ryW8zx3v8yx0PnkHXhoDb8bu/s+/3P/th/+bX+5ee0vs+++14rqiIovL0M7pv7yKDQn3Z9/KW+Bfv3623/8pfhSqmZdpmTs3OP363mKV8EdspbfPidcV/vxSKLi2f96AJeKzCxcTEjvga0gl1OBbjC0E1rGzQZMrjynHxQ6EUuLmImNOqUbXYo/6bP5OC++1scQa9W2wiY0oqabG3pBTbFbOhfhpuRNDs6SSSym1tNJdGWXWVHMttdZWBXKzpZZbabW11ttosycgsPTaW+999DniSGBgGXW00ccYc0Y3+aDJe01eP/nOiiutvMqqq62+xppG+Fi2YtWadRs2d9xpAxO77rb7Hnue4A5IcfIpp552+hlnXmLtpptvufW22++48/eu/ezqP379L3Yt/OxafDul17Xfu8Z3XWu/3iIITor2jB2LObDjTTtAQEftme8h56id0575EUmKErnIor1xO2jH2MJ8Qiw3/N67P3buP9o3V/p/tG/x/7VzTlv3/2PnHFv3z337N7u2xXP2duzLQq2pT2QfPz99utinSG3++z9H9vER0ogNiCz11FFYzM3HZH6SY2UHQOvr7LCJx9/pYxrz3JXnZbdYl+pjGens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';
    const loadingImages = {
        darkMode: 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2220%22%20height%3D%2220%22%20viewBox%3D%220%200%2020%2020%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%20%20%20%20%20%20%3Cstyle%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%40keyframes%20rotate%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20from%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20transform%3A%20rotate%280deg%29%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20to%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20transform%3A%20rotate%28359deg%29%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%3C%2Fstyle%3E%0A%20%20%20%20%20%20%20%20%3Cg%20style%3D%22transform-origin%3A%2050%25%2050%25%3B%20animation%3A%20rotate%201s%20infinite%20reverse%20linear%3B%22%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2218.0968%22%20y%3D%2216.0861%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%28136.161%2018.0968%2016.0861%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.1%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.49878%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.4%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2219.9976%22%20y%3D%228.37451%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2890%2019.9976%208.37451%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.2%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2216.1727%22%20y%3D%221.9917%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2846.1607%2016.1727%201.9917%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.3%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.91309%22%20y%3D%226.88501%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%28136.161%208.91309%206.88501%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.6%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%226.79602%22%20y%3D%2210.996%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2846.1607%206.79602%2010.996%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.7%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%227%22%20y%3D%228.62549%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2890%207%208.62549%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.8%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.49878%22%20y%3D%2213%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.9%22%2F%3E%0A%20%20%20%20%20%20%20%20%3C%2Fg%3E%0A%20%20%20%20%3C%2Fsvg%3E',
        lightMode: 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2220%22%20height%3D%2220%22%20viewBox%3D%220%200%2020%2020%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%20%20%20%20%20%20%3Cstyle%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%40keyframes%20rotate%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20from%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20transform%3A%20rotate%280deg%29%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20to%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20transform%3A%20rotate%28359deg%29%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%3C%2Fstyle%3E%0A%20%20%20%20%20%20%20%20%3Cg%20style%3D%22transform-origin%3A%2050%25%2050%25%3B%20animation%3A%20rotate%201s%20infinite%20reverse%20linear%3B%22%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2218.0968%22%20y%3D%2216.0861%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%28136.161%2018.0968%2016.0861%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.1%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.49878%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.4%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2219.9976%22%20y%3D%228.37451%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2890%2019.9976%208.37451%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.2%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2216.1727%22%20y%3D%221.9917%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2846.1607%2016.1727%201.9917%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.3%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.91309%22%20y%3D%226.88501%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%28136.161%208.91309%206.88501%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.6%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%226.79602%22%20y%3D%2210.996%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2846.1607%206.79602%2010.996%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.7%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%227%22%20y%3D%228.62549%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2890%207%208.62549%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.8%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.49878%22%20y%3D%2213%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.9%22%2F%3E%0A%20%20%20%20%20%20%20%20%3C%2Fg%3E%0A%20%20%20%20%3C%2Fsvg%3E' // 'data:application/octet-stream;base64,PHN2ZyB3aWR0aD0iMjAiIGhlaWdodD0iMjAiIHZpZXdCb3g9IjAgMCAyMCAyMCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KCTxzdHlsZT4KCQlAa2V5ZnJhbWVzIHJvdGF0ZSB7CgkJCWZyb20gewoJCQkJdHJhbnNmb3JtOiByb3RhdGUoMGRlZyk7CgkJCX0KCQkJdG8gewoJCQkJdHJhbnNmb3JtOiByb3RhdGUoMzU5ZGVnKTsKCQkJfQoJCX0KCTwvc3R5bGU+Cgk8ZyBzdHlsZT0idHJhbnNmb3JtLW9yaWdpbjogNTAlIDUwJTsgYW5pbWF0aW9uOiByb3RhdGUgMXMgaW5maW5pdGUgcmV2ZXJzZSBsaW5lYXI7Ij4KCQk8cmVjdCB4PSIxOC4wOTY4IiB5PSIxNi4wODYxIiB3aWR0aD0iMyIgaGVpZ2h0PSI3IiByeD0iMS41IiB0cmFuc2Zvcm09InJvdGF0ZSgxMzYuMTYxIDE4LjA5NjggMTYuMDg2MSkiIGZpbGw9IiNmZmZmZmYiIGZpbGwtb3BhY2l0eT0iMC4xIi8+CQoJCTxyZWN0IHg9IjguNDk4NzgiIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIGZpbGw9IiNmZmZmZmYiIGZpbGwtb3BhY2l0eT0iMC40Ii8+CgkJPHJlY3QgeD0iMTkuOTk3NiIgeT0iOC4zNzQ1MSIgd2lkdGg9IjMiIGhlaWdodD0iNyIgcng9IjEuNSIgdHJhbnNmb3JtPSJyb3RhdGUoOTAgMTkuOTk3NiA4LjM3NDUxKSIgZmlsbD0iI2ZmZmZmZiIgZmlsbC1vcGFjaXR5PSIwLjIiLz4KCQk8cmVjdCB4PSIxNi4xNzI3IiB5PSIxLjk5MTciIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIHRyYW5zZm9ybT0icm90YXRlKDQ2LjE2MDcgMTYuMTcyNyAxLjk5MTcpIiBmaWxsPSIjZmZmZmZmIiBmaWxsLW9wYWNpdHk9IjAuMyIvPgoJCTxyZWN0IHg9IjguOTEzMDkiIHk9IjYuODg1MDEiIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIHRyYW5zZm9ybT0icm90YXRlKDEzNi4xNjEgOC45MTMwOSA2Ljg4NTAxKSIgZmlsbD0iI2ZmZmZmZiIgZmlsbC1vcGFjaXR5PSIwLjYiLz4KCQk8cmVjdCB4PSI2Ljc5NjAyIiB5PSIxMC45OTYiIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIHRyYW5zZm9ybT0icm90YXRlKDQ2LjE2MDcgNi43OTYwMiAxMC45OTYpIiBmaWxsPSIjZmZmZmZmIiBmaWxsLW9wYWNpdHk9IjAuNyIvPgoJCTxyZWN0IHg9IjciIHk9IjguNjI1NDkiIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIHRyYW5zZm9ybT0icm90YXRlKDkwIDcgOC42MjU0OSkiIGZpbGw9IiNmZmZmZmYiIGZpbGwtb3BhY2l0eT0iMC44Ii8+CQkKCQk8cmVjdCB4PSI4LjQ5ODc4IiB5PSIxMyIgd2lkdGg9IjMiIGhlaWdodD0iNyIgcng9IjEuNSIgZmlsbD0iI2ZmZmZmZiIgZmlsbC1vcGFjaXR5PSIwLjkiLz4KCTwvZz4KPC9zdmc+Cg=='
    };
    const closeIcon = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2212%22%20height%3D%2212%22%20viewBox%3D%220%200%2012%2012%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%3Cpath%20fill-rule%3D%22evenodd%22%20clip-rule%3D%22evenodd%22%20d%3D%22M5.99998%204.58578L10.2426%200.34314C10.6331%20-0.0473839%2011.2663%20-0.0473839%2011.6568%200.34314C12.0474%200.733665%2012.0474%201.36683%2011.6568%201.75735L7.41419%205.99999L11.6568%2010.2426C12.0474%2010.6332%2012.0474%2011.2663%2011.6568%2011.6568C11.2663%2012.0474%2010.6331%2012.0474%2010.2426%2011.6568L5.99998%207.41421L1.75734%2011.6568C1.36681%2012.0474%200.733649%2012.0474%200.343125%2011.6568C-0.0473991%2011.2663%20-0.0473991%2010.6332%200.343125%2010.2426L4.58577%205.99999L0.343125%201.75735C-0.0473991%201.36683%20-0.0473991%200.733665%200.343125%200.34314C0.733649%20-0.0473839%201.36681%20-0.0473839%201.75734%200.34314L5.99998%204.58578Z%22%20fill%3D%22%23222222%22%2F%3E%0A%3C%2Fsvg%3E';

    const blockedFBLogo = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2280%22%20height%3D%2280%22%20viewBox%3D%220%200%2080%2080%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%3Ccircle%20cx%3D%2240%22%20cy%3D%2240%22%20r%3D%2240%22%20fill%3D%22white%22%2F%3E%0A%3Cg%20clip-path%3D%22url%28%23clip0%29%22%3E%0A%3Cpath%20d%3D%22M73.8457%2039.974C73.8457%2021.284%2058.7158%206.15405%2040.0258%206.15405C21.3358%206.15405%206.15344%2021.284%206.15344%2039.974C6.15344%2056.884%2018.5611%2070.8622%2034.7381%2073.4275V49.764H26.0999V39.974H34.7381V32.5399C34.7381%2024.0587%2039.764%2019.347%2047.5122%2019.347C51.2293%2019.347%2055.0511%2020.0799%2055.0511%2020.0799V28.3517H50.8105C46.6222%2028.3517%2045.2611%2030.9693%2045.2611%2033.6393V39.974H54.6846L53.1664%2049.764H45.2611V73.4275C61.4381%2070.9146%2073.8457%2056.884%2073.8457%2039.974Z%22%20fill%3D%22%231877F2%22%2F%3E%0A%3C%2Fg%3E%0A%3Crect%20x%3D%223.01295%22%20y%3D%2211.7158%22%20width%3D%2212.3077%22%20height%3D%2292.3077%22%20rx%3D%226.15385%22%20transform%3D%22rotate%28-45%203.01295%2011.7158%29%22%20fill%3D%22%23666666%22%20stroke%3D%22white%22%20stroke-width%3D%226.15385%22%2F%3E%0A%3Cdefs%3E%0A%3CclipPath%20id%3D%22clip0%22%3E%0A%3Crect%20width%3D%2267.6923%22%20height%3D%2267.6923%22%20fill%3D%22white%22%20transform%3D%22translate%286.15344%206.15405%29%22%2F%3E%0A%3C%2FclipPath%3E%0A%3C%2Fdefs%3E%0A%3C%2Fsvg%3E';

    const blockedYTVideo = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2275%22%20height%3D%2275%22%20viewBox%3D%220%200%2075%2075%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%3Crect%20x%3D%226.75%22%20y%3D%2215.75%22%20width%3D%2256.25%22%20height%3D%2239%22%20rx%3D%2213.5%22%20fill%3D%22%23DE5833%22%2F%3E%0A%20%20%3Cmask%20id%3D%22path-2-outside-1_885_11045%22%20maskUnits%3D%22userSpaceOnUse%22%20x%3D%2223.75%22%20y%3D%2222.5%22%20width%3D%2224%22%20height%3D%2226%22%20fill%3D%22black%22%3E%0A%20%20%3Crect%20fill%3D%22white%22%20x%3D%2223.75%22%20y%3D%2222.5%22%20width%3D%2224%22%20height%3D%2226%22%2F%3E%0A%20%20%3Cpath%20d%3D%22M41.9425%2037.5279C43.6677%2036.492%2043.6677%2033.9914%2041.9425%2032.9555L31.0394%2026.4088C29.262%2025.3416%2027%2026.6218%2027%2028.695L27%2041.7884C27%2043.8615%2029.262%2045.1418%2031.0394%2044.0746L41.9425%2037.5279Z%22%2F%3E%0A%20%20%3C%2Fmask%3E%0A%20%20%3Cpath%20d%3D%22M41.9425%2037.5279C43.6677%2036.492%2043.6677%2033.9914%2041.9425%2032.9555L31.0394%2026.4088C29.262%2025.3416%2027%2026.6218%2027%2028.695L27%2041.7884C27%2043.8615%2029.262%2045.1418%2031.0394%2044.0746L41.9425%2037.5279Z%22%20fill%3D%22white%22%2F%3E%0A%20%20%3Cpath%20d%3D%22M30.0296%2044.6809L31.5739%2047.2529L30.0296%2044.6809ZM30.0296%2025.8024L31.5739%2023.2304L30.0296%2025.8024ZM42.8944%2036.9563L44.4387%2039.5283L42.8944%2036.9563ZM41.35%2036.099L28.4852%2028.3744L31.5739%2023.2304L44.4387%2030.955L41.35%2036.099ZM30%2027.5171L30%2042.9663L24%2042.9663L24%2027.5171L30%2027.5171ZM28.4852%2042.1089L41.35%2034.3843L44.4387%2039.5283L31.5739%2047.2529L28.4852%2042.1089ZM30%2042.9663C30%2042.1888%2029.1517%2041.7087%2028.4852%2042.1089L31.5739%2047.2529C28.2413%2049.2539%2024%2046.8535%2024%2042.9663L30%2042.9663ZM28.4852%2028.3744C29.1517%2028.7746%2030%2028.2945%2030%2027.5171L24%2027.5171C24%2023.6299%2028.2413%2021.2294%2031.5739%2023.2304L28.4852%2028.3744ZM44.4387%2030.955C47.6735%2032.8974%2047.6735%2037.586%2044.4387%2039.5283L41.35%2034.3843C40.7031%2034.7728%2040.7031%2035.7105%2041.35%2036.099L44.4387%2030.955Z%22%20fill%3D%22%23BC4726%22%20mask%3D%22url(%23path-2-outside-1_885_11045)%22%2F%3E%0A%20%20%3Ccircle%20cx%3D%2257.75%22%20cy%3D%2252.5%22%20r%3D%2213.5%22%20fill%3D%22%23E0E0E0%22%2F%3E%0A%20%20%3Crect%20x%3D%2248.75%22%20y%3D%2250.25%22%20width%3D%2218%22%20height%3D%224.5%22%20rx%3D%221.5%22%20fill%3D%22%23666666%22%2F%3E%0A%20%20%3Cpath%20fill-rule%3D%22evenodd%22%20clip-rule%3D%22evenodd%22%20d%3D%22M57.9853%2015.8781C58.2046%2016.1015%2058.5052%2016.2262%2058.8181%2016.2238C59.1311%2016.2262%2059.4316%2016.1015%2059.6509%2015.8781L62.9821%2012.5469C63.2974%2012.2532%2063.4272%2011.8107%2063.3206%2011.3931C63.2139%2010.9756%2062.8879%2010.6495%2062.4703%2010.5429C62.0528%2010.4363%2061.6103%2010.5661%2061.3165%2010.8813L57.9853%2014.2125C57.7627%2014.4325%2057.6374%2014.7324%2057.6374%2015.0453C57.6374%2015.3583%2057.7627%2015.6582%2057.9853%2015.8781ZM61.3598%2018.8363C61.388%2019.4872%2061.9385%2019.9919%2062.5893%2019.9637L62.6915%2019.9559L66.7769%2019.6023C67.4278%2019.5459%2067.9097%2018.9726%2067.8533%2018.3217C67.7968%2017.6708%2067.2235%2017.1889%2066.5726%2017.2453L62.4872%2017.6067C61.8363%2017.6349%2061.3316%2018.1854%2061.3598%2018.8363Z%22%20fill%3D%22%23AAAAAA%22%20fill-opacity%3D%220.6%22%2F%3E%0A%20%20%3Cpath%20fill-rule%3D%22evenodd%22%20clip-rule%3D%22evenodd%22%20d%3D%22M10.6535%2015.8781C10.4342%2016.1015%2010.1336%2016.2262%209.82067%2016.2238C9.5077%2016.2262%209.20717%2016.1015%208.98787%2015.8781L5.65667%2012.5469C5.34138%2012.2532%205.2116%2011.8107%205.31823%2011.3931C5.42487%2010.9756%205.75092%2010.6495%206.16847%2010.5429C6.58602%2010.4363%207.02848%2010.5661%207.32227%2010.8813L10.6535%2014.2125C10.8761%2014.4325%2011.0014%2014.7324%2011.0014%2015.0453C11.0014%2015.3583%2010.8761%2015.6582%2010.6535%2015.8781ZM7.2791%2018.8362C7.25089%2019.4871%206.7004%2019.9919%206.04954%2019.9637L5.9474%2019.9558L1.86197%2019.6023C1.44093%2019.5658%201.07135%2019.3074%200.892432%2018.9246C0.713515%2018.5417%200.752449%2018.0924%200.994567%2017.7461C1.23669%2017.3997%201.6452%2017.2088%202.06624%2017.2453L6.15167%2017.6067C6.80254%2017.6349%207.3073%2018.1854%207.2791%2018.8362Z%22%20fill%3D%22%23AAAAAA%22%20fill-opacity%3D%220.6%22%2F%3E%0A%3C%2Fsvg%3E%0A';
    const videoPlayDark = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2222%22%20height%3D%2226%22%20viewBox%3D%220%200%2022%2026%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%3Cpath%20d%3D%22M21%2011.2679C22.3333%2012.0377%2022.3333%2013.9622%2021%2014.732L3%2025.1244C1.66667%2025.8942%202.59376e-06%2024.9319%202.66105e-06%2023.3923L3.56958e-06%202.60769C3.63688e-06%201.06809%201.66667%200.105844%203%200.875644L21%2011.2679Z%22%20fill%3D%22%23222222%22%2F%3E%0A%3C%2Fsvg%3E%0A';
    const videoPlayLight = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2222%22%20height%3D%2226%22%20viewBox%3D%220%200%2022%2026%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%3Cpath%20d%3D%22M21%2011.2679C22.3333%2012.0377%2022.3333%2013.9622%2021%2014.732L3%2025.1244C1.66667%2025.8942%202.59376e-06%2024.9319%202.66105e-06%2023.3923L3.56958e-06%202.60769C3.63688e-06%201.06809%201.66667%200.105844%203%200.875644L21%2011.2679Z%22%20fill%3D%22%23FFFFFF%22%2F%3E%0A%3C%2Fsvg%3E';

    const ddgFont = 'data:application/octet-stream;base64,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';
    const ddgFontBold = 'data:application/octet-stream;base64,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';

    /*********************************************************
     *  Style Definitions
     *********************************************************/
    const styles = {
        fontStyle: `
        @font-face{
            font-family: DuckDuckGoPrivacyEssentials;
            src: url(${ddgFont});
        }
        @font-face{
            font-family: DuckDuckGoPrivacyEssentialsBold;
            font-weight: bold;
            src: url(${ddgFontBold});
        }
    `,
        darkMode: {
            background: `
            background: #111111;
        `,
            textFont: `
            color: rgba(255, 255, 255, 0.9);
        `,
            buttonFont: `
            color: #111111;
        `,
            linkFont: `
            color: #7295F6;
        `,
            buttonBackground: `
            background: #5784FF;
        `,
            buttonBackgroundHover: `
            background: #557FF3;
        `,
            buttonBackgroundPress: `
            background: #3969EF;
        `,
            toggleButtonText: `
            color: #EEEEEE;
        `
        },
        lightMode: {
            background: `
            background: #FFFFFF;
        `,
            textFont: `
            color: #222222;
        `,
            buttonFont: `
            color: #FFFFFF;
        `,
            linkFont: `
            color: #3969EF;
        `,
            buttonBackground: `
            background: #3969EF;
        `,
            buttonBackgroundHover: `
            background: #2B55CA;
        `,
            buttonBackgroundPress: `
            background: #1E42A4;
        `,
            toggleButtonText: `
            color: #666666;
        `
        },
        loginMode: {
            buttonBackground: `
            background: #666666;
        `,
            buttonFont: `
            color: #FFFFFF;
        `
        },
        cancelMode: {
            buttonBackground: `
            background: rgba(34, 34, 34, 0.1);
        `,
            buttonFont: `
            color: #222222;
        `,
            buttonBackgroundHover: `
            background: rgba(0, 0, 0, 0.12);
        `,
            buttonBackgroundPress: `
            background: rgba(0, 0, 0, 0.18);
        `
        },
        button: `
        border-radius: 8px;

        padding: 11px 22px;
        font-weight: bold;
        margin: 0px auto;
        border-color: #3969EF;
        border: none;

        font-family: DuckDuckGoPrivacyEssentialsBold;
        font-size: 14px;

        position: relative;
        cursor: pointer;
        box-shadow: none;
        z-index: 2147483646;
    `,
        circle: `
        border-radius: 50%;
        width: 18px;
        height: 18px;
        background: #E0E0E0;
        border: 1px solid #E0E0E0;
        position: absolute;
        top: -8px;
        right: -8px;
    `,
        loginIcon: `
        position: absolute;
        top: -13px;
        right: -10px;
        height: 28px;
        width: 28px;
    `,
        rectangle: `
        width: 12px;
        height: 3px;
        background: #666666;
        position: relative;
        top: 42.5%;
        margin: auto;
    `,
        textBubble: `
        background: #FFFFFF;
        border: 1px solid rgba(0, 0, 0, 0.1);
        border-radius: 16px;
        box-shadow: 0px 2px 6px rgba(0, 0, 0, 0.12), 0px 8px 16px rgba(0, 0, 0, 0.08);
        width: 360px;
        margin-top: 10px;
        z-index: 2147483647;
        position: absolute;
        line-height: normal;
    `,
        textBubbleWidth: 360, // Should match the width rule in textBubble
        textBubbleLeftShift: 100, // Should match the CSS left: rule in textBubble
        textArrow: `
        display: inline-block;
        background: #FFFFFF;
        border: solid rgba(0, 0, 0, 0.1);
        border-width: 0 1px 1px 0;
        padding: 5px;
        transform: rotate(-135deg);
        -webkit-transform: rotate(-135deg);
        position: relative;
        top: -9px;
    `,
        arrowDefaultLocationPercent: 50,
        hoverTextTitle: `
        padding: 0px 12px 12px;
        margin-top: -5px;
    `,
        hoverTextBody: `
        font-family: DuckDuckGoPrivacyEssentials;
        font-size: 14px;
        line-height: 21px;
        margin: auto;
        padding: 17px;
        text-align: left;
    `,
        hoverContainer: `
        padding-bottom: 10px;
    `,
        buttonTextContainer: `
        display: flex;
        flex-direction: row;
        align-items: center;
        border: none;
        padding: 0;
        margin: 0;
    `,
        headerRow: `

    `,
        block: `
        box-sizing: border-box;
        border: 1px solid rgba(0,0,0,0.1);
        border-radius: 12px;
        max-width: 600px;
        min-height: 300px;
        margin: auto;
        display: flex;
        flex-direction: column;

        font-family: DuckDuckGoPrivacyEssentials;
        line-height: 1;
    `,
        youTubeDialogBlock: `
        height: calc(100% - 30px);
        max-width: initial;
        min-height: initial;
    `,
        imgRow: `
        display: flex;
        flex-direction: column;
        margin: 20px 0px;
    `,
        content: `
        display: flex;
        flex-direction: column;
        padding: 16px 0;
        flex: 1 1 1px;
    `,
        feedbackLink: `
        font-family: DuckDuckGoPrivacyEssentials;
        font-style: normal;
        font-weight: 400;
        font-size: 12px;
        line-height: 12px;
        color: #ABABAB;
        text-decoration: none;
    `,
        feedbackRow: `
        height: 30px;
        display: flex;
        justify-content: flex-end;
        align-items: center;
    `,
        titleBox: `
        display: flex;
        padding: 12px;
        max-height: 44px;
        border-bottom: 1px solid;
        border-color: rgba(196, 196, 196, 0.3);
        margin: 0;
        margin-bottom: 4px;
    `,
        title: `
        font-family: DuckDuckGoPrivacyEssentials;
        line-height: 1.4;
        font-size: 14px;
        margin: auto 10px;
        flex-basis: 100%;
        height: 1.4em;
        flex-wrap: wrap;
        overflow: hidden;
        text-align: left;
        border: none;
        padding: 0;
    `,
        buttonRow: `
        display: flex;
        height: 100%
        flex-direction: row;
        margin: 20px auto 0px;
        height: 100%;
        align-items: flex-start;
    `,
        modalContentTitle: `
        font-family: DuckDuckGoPrivacyEssentialsBold;
        font-size: 17px;
        font-weight: bold;
        line-height: 21px;
        margin: 10px auto;
        text-align: center;
        border: none;
        padding: 0px 32px;
    `,
        modalContentText: `
        font-family: DuckDuckGoPrivacyEssentials;
        font-size: 14px;
        line-height: 21px;
        margin: 0px auto 14px;
        text-align: center;
        border: none;
        padding: 0;
    `,
        modalButtonRow: `
        border: none;
        padding: 0;
        margin: auto;
        width: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
    `,
        modalButton: `
        width: 100%;
        display: flex;
        justify-content: center;
        align-items: center;
    `,
        modalIcon: `
        display: block;
    `,
        contentTitle: `
        font-family: DuckDuckGoPrivacyEssentialsBold;
        font-size: 17px;
        font-weight: bold;
        margin: 20px auto 10px;
        padding: 0px 30px;
        text-align: center;
        margin-top: auto;
    `,
        contentText: `
        font-family: DuckDuckGoPrivacyEssentials;
        font-size: 14px;
        line-height: 21px;
        padding: 0px 40px;
        text-align: center;
        margin: 0 auto auto;
    `,
        icon: `
        height: 80px;
        width: 80px;
        margin: auto;
    `,
        closeIcon: `
        height: 12px;
        width: 12px;
        margin: auto;
    `,
        closeButton: `
        display: flex;
        justify-content: center;
        align-items: center;
        min-width: 20px;
        height: 21px;
        border: 0;
        background: transparent;
        cursor: pointer;
    `,
        logo: `
        flex-basis: 0%;
        min-width: 20px;
        height: 21px;
        border: none;
        padding: 0;
        margin: 0;
    `,
        logoImg: `
        height: 21px;
        width: 21px;
    `,
        loadingImg: `
        display: block;
        margin: 0px 8px 0px 0px;
        height: 14px;
        width: 14px;
    `,
        modal: `
        width: 340px;
        padding: 0;
        margin: auto;
        background-color: #FFFFFF;
        position: absolute;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        display: block;
        box-shadow: 0px 1px 3px rgba(0, 0, 0, 0.08), 0px 2px 4px rgba(0, 0, 0, 0.1);
        border-radius: 12px;
        border: none;
    `,
        modalContent: `
        padding: 24px;
        display: flex;
        flex-direction: column;
        border: none;
        margin: 0;
    `,
        overlay: `
        height: 100%;
        width: 100%;
        background-color: #666666;
        opacity: .5;
        display: block;
        position: fixed;
        top: 0;
        right: 0;
        border: none;
        padding: 0;
        margin: 0;
    `,
        modalContainer: `
        height: 100vh;
        width: 100vw;
        box-sizing: border-box;
        z-index: 2147483647;
        display: block;
        position: fixed;
        border: 0;
        margin: 0;
        padding: 0;
    `,
        headerLinkContainer: `
        flex-basis: 100%;
        display: grid;
        justify-content: flex-end;
    `,
        headerLink: `
        line-height: 1.4;
        font-size: 14px;
        font-weight: bold;
        font-family: DuckDuckGoPrivacyEssentialsBold;
        text-decoration: none;
        cursor: pointer;
        min-width: 100px;
        text-align: end;
        float: right;
        display: none;
    `,
        generalLink: `
        line-height: 1.4;
        font-size: 14px;
        font-weight: bold;
        font-family: DuckDuckGoPrivacyEssentialsBold;
        cursor: pointer;
        text-decoration: none;
    `,
        wrapperDiv: `
        display: inline-block;
        border: 0;
        padding: 0;
        margin: 0;
        max-width: 600px;
        min-height: 300px;
    `,
        toggleButtonWrapper: `
        display: flex;
        align-items: center;
        cursor: pointer;
    `,
        toggleButton: `
        cursor: pointer;
        position: relative;
        width: 30px;
        height: 16px;
        margin-top: -3px;
        margin: 0;
        padding: 0;
        border: none;
        background-color: transparent;
        text-align: left;
    `,
        toggleButtonBg: `
        right: 0;
        width: 30px;
        height: 16px;
        overflow: visible;
        border-radius: 10px;
    `,
        toggleButtonText: `
        display: inline-block;
        margin: 0 0 0 7px;
        padding: 0;
    `,
        toggleButtonBgState: {
            active: `
            background: #3969EF;
        `,
            inactive: `
            background-color: #666666;
        `
        },
        toggleButtonKnob: `
        position: absolute;
        display: inline-block;
        width: 14px;
        height: 14px;
        border-radius: 10px;
        background-color: #ffffff;
        margin-top: 1px;
        top: calc(50% - 14px/2 - 1px);
        box-shadow: 0px 0px 1px rgba(0, 0, 0, 0.05), 0px 1px 1px rgba(0, 0, 0, 0.1);
    `,
        toggleButtonKnobState: {
            active: `
            right: 1px;
        `,
            inactive: `
            left: 1px;
        `
        },
        placeholderWrapperDiv: `
        position: relative;
        overflow: hidden;
        border-radius: 12px;
        box-sizing: border-box;
        max-width: initial;
        min-width: 380px;
        min-height: 300px;
        margin: auto;
    `,
        youTubeWrapperDiv: `
        position: relative;
        overflow: hidden;
        max-width: initial;
        min-width: 380px;
        min-height: 300px;
        height: 100%;
    `,
        youTubeDialogDiv: `
        position: relative;
        overflow: hidden;
        border-radius: 12px;
        max-width: initial;
        min-height: initial;
        height: calc(100% - 30px);
    `,
        youTubeDialogBottomRow: `
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: flex-end;
        margin-top: auto;
    `,
        youTubePlaceholder: `
        display: flex;
        flex-direction: column;
        justify-content: flex-start;
        position: relative;
        width: 100%;
        height: 100%;
        background: rgba(45, 45, 45, 0.8);
    `,
        youTubePreviewWrapperImg: `
        position: absolute;
        display: flex;
        justify-content: center;
        align-items: center;
        width: 100%;
        height: 100%;
    `,
        youTubePreviewImg: `
        min-width: 100%;
        min-height: 100%;
        height: auto;
    `,
        youTubeTopSection: `
        font-family: DuckDuckGoPrivacyEssentialsBold;
        flex: 1;
        display: flex;
        justify-content: space-between;
        position: relative;
        padding: 18px 12px 0;
    `,
        youTubeTitle: `
        font-size: 14px;
        font-weight: bold;
        line-height: 14px;
        color: #FFFFFF;
        margin: 0;
        width: 100%;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        box-sizing: border-box;
    `,
        youTubePlayButtonRow: `
        flex: 2;
        display: flex;
        align-items: center;
        justify-content: center;
    `,
        youTubePlayButton: `
        display: flex;
        justify-content: center;
        align-items: center;
        height: 48px;
        width: 80px;
        padding: 0px 24px;
        border-radius: 8px;
    `,
        youTubePreviewToggleRow: `
        flex: 1;
        display: flex;
        flex-direction: column;
        justify-content: flex-end;
        align-items: center;
        padding: 0 12px 18px;
    `,
        youTubePreviewToggleText: `
        color: #EEEEEE;
        font-weight: 400;
    `,
        youTubePreviewInfoText: `
        color: #ABABAB;
    `
    };

    function getConfig (locale) {
        const locales = JSON.parse(localesJSON);
        const fbStrings = locales[locale]['facebook.json'];
        const ytStrings = locales[locale]['youtube.json'];

        const sharedStrings = locales[locale]['shared.json'];
        const config = {
            Facebook: {
                informationalModal: {
                    icon: blockedFBLogo,
                    messageTitle: fbStrings.informationalModalMessageTitle,
                    messageBody: fbStrings.informationalModalMessageBody,
                    confirmButtonText: fbStrings.informationalModalConfirmButtonText,
                    rejectButtonText: fbStrings.informationalModalRejectButtonText
                },
                elementData: {
                    'FB Like Button': {
                        selectors: [
                            '.fb-like'
                        ],
                        replaceSettings: {
                            type: 'blank'
                        }
                    },
                    'FB Button iFrames': {
                        selectors: [
                            "iframe[src*='://www.facebook.com/plugins/like.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/like.php']",
                            "iframe[src*='://www.facebook.com/plugins/share_button.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/share_button.php']"
                        ],
                        replaceSettings: {
                            type: 'blank'
                        }
                    },
                    'FB Save Button': {
                        selectors: [
                            '.fb-save'
                        ],
                        replaceSettings: {
                            type: 'blank'
                        }
                    },
                    'FB Share Button': {
                        selectors: [
                            '.fb-share-button'
                        ],
                        replaceSettings: {
                            type: 'blank'
                        }
                    },
                    'FB Page iFrames': {
                        selectors: [
                            "iframe[src*='://www.facebook.com/plugins/page.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/page.php']"
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockContent,
                            infoTitle: fbStrings.infoTitleUnblockContent,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'originalElement'
                        }
                    },
                    'FB Page Div': {
                        selectors: [
                            '.fb-page'
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockContent,
                            infoTitle: fbStrings.infoTitleUnblockContent,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'iFrame',
                            targetURL: 'https://www.facebook.com/plugins/page.php?href=data-href&tabs=data-tabs&width=data-width&height=data-height',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-tabs': {
                                    default: 'timeline'
                                },
                                'data-height': {
                                    default: '500'
                                },
                                'data-width': {
                                    default: '500'
                                }
                            },
                            styleDataAttributes: {
                                width: {
                                    name: 'data-width',
                                    unit: 'px'
                                },
                                height: {
                                    name: 'data-height',
                                    unit: 'px'
                                }
                            }
                        }
                    },
                    'FB Comment iFrames': {
                        selectors: [
                            "iframe[src*='://www.facebook.com/plugins/comment_embed.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/comment_embed.php']"
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockComment,
                            infoTitle: fbStrings.infoTitleUnblockComment,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'originalElement'
                        }
                    },
                    'FB Comments': {
                        selectors: [
                            '.fb-comments',
                            'fb\\:comments'
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockComments,
                            infoTitle: fbStrings.infoTitleUnblockComments,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'allowFull',
                            targetURL: 'https://www.facebook.com/v9.0/plugins/comments.php?href=data-href&numposts=data-numposts&sdk=joey&version=v9.0&width=data-width',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-numposts': {
                                    default: 10
                                },
                                'data-width': {
                                    default: '500'
                                }
                            }
                        }
                    },
                    'FB Embedded Comment Div': {
                        selectors: [
                            '.fb-comment-embed'
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockComment,
                            infoTitle: fbStrings.infoTitleUnblockComment,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'iFrame',
                            targetURL: 'https://www.facebook.com/v9.0/plugins/comment_embed.php?href=data-href&sdk=joey&width=data-width&include_parent=data-include-parent',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-width': {
                                    default: '500'
                                },
                                'data-include-parent': {
                                    default: 'false'
                                }
                            },
                            styleDataAttributes: {
                                width: {
                                    name: 'data-width',
                                    unit: 'px'
                                }
                            }
                        }
                    },
                    'FB Post iFrames': {
                        selectors: [
                            "iframe[src*='://www.facebook.com/plugins/post.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/post.php']"
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockPost,
                            infoTitle: fbStrings.infoTitleUnblockPost,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'originalElement'
                        }
                    },
                    'FB Posts Div': {
                        selectors: [
                            '.fb-post'
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockPost,
                            infoTitle: fbStrings.infoTitleUnblockPost,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'allowFull',
                            targetURL: 'https://www.facebook.com/v9.0/plugins/post.php?href=data-href&sdk=joey&show_text=true&width=data-width',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-width': {
                                    default: '500'
                                }
                            },
                            styleDataAttributes: {
                                width: {
                                    name: 'data-width',
                                    unit: 'px'
                                },
                                height: {
                                    name: 'data-height',
                                    unit: 'px',
                                    fallbackAttribute: 'data-width'
                                }
                            }
                        }
                    },
                    'FB Video iFrames': {
                        selectors: [
                            "iframe[src*='://www.facebook.com/plugins/video.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/video.php']"
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockVideo,
                            infoTitle: fbStrings.infoTitleUnblockVideo,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'originalElement'
                        }
                    },
                    'FB Video': {
                        selectors: [
                            '.fb-video'
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockVideo,
                            infoTitle: fbStrings.infoTitleUnblockVideo,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'iFrame',
                            targetURL: 'https://www.facebook.com/plugins/video.php?href=data-href&show_text=true&width=data-width',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-width': {
                                    default: '500'
                                }
                            },
                            styleDataAttributes: {
                                width: {
                                    name: 'data-width',
                                    unit: 'px'
                                },
                                height: {
                                    name: 'data-height',
                                    unit: 'px',
                                    fallbackAttribute: 'data-width'
                                }
                            }
                        }
                    },
                    'FB Group iFrames': {
                        selectors: [
                            "iframe[src*='://www.facebook.com/plugins/group.php']",
                            "iframe[src*='://www.facebook.com/v2.0/plugins/group.php']"
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockContent,
                            infoTitle: fbStrings.infoTitleUnblockContent,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'originalElement'
                        }
                    },
                    'FB Group': {
                        selectors: [
                            '.fb-group'
                        ],
                        replaceSettings: {
                            type: 'dialog',
                            buttonText: fbStrings.buttonTextUnblockContent,
                            infoTitle: fbStrings.infoTitleUnblockContent,
                            infoText: fbStrings.infoTextUnblockContent
                        },
                        clickAction: {
                            type: 'iFrame',
                            targetURL: 'https://www.facebook.com/plugins/group.php?href=data-href&width=data-width',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-width': {
                                    default: '500'
                                }
                            },
                            styleDataAttributes: {
                                width: {
                                    name: 'data-width',
                                    unit: 'px'
                                }
                            }
                        }
                    },
                    'FB Login Button': {
                        selectors: [
                            '.fb-login-button'
                        ],
                        replaceSettings: {
                            type: 'loginButton',
                            icon: blockedFBLogo,
                            buttonText: fbStrings.loginButtonText,
                            popupBodyText: fbStrings.loginBodyText
                        },
                        clickAction: {
                            type: 'allowFull',
                            targetURL: 'https://www.facebook.com/v9.0/plugins/login_button.php?app_id=app_id_replace&auto_logout_link=false&button_type=continue_with&sdk=joey&size=large&use_continue_as=false&width=',
                            urlDataAttributesToPreserve: {
                                'data-href': {
                                    default: '',
                                    required: true
                                },
                                'data-width': {
                                    default: '500'
                                },
                                app_id_replace: {
                                    default: 'null'
                                }
                            }
                        }
                    }
                }
            },
            Youtube: {
                informationalModal: {
                    icon: blockedYTVideo,
                    messageTitle: ytStrings.informationalModalMessageTitle,
                    messageBody: ytStrings.informationalModalMessageBody,
                    confirmButtonText: ytStrings.informationalModalConfirmButtonText,
                    rejectButtonText: ytStrings.informationalModalRejectButtonText
                },
                elementData: {
                    'YouTube embedded video': {
                        selectors: [
                            "iframe[src*='://youtube.com/embed']",
                            "iframe[src*='://youtube-nocookie.com/embed']",
                            "iframe[src*='://www.youtube.com/embed']",
                            "iframe[src*='://www.youtube-nocookie.com/embed']",
                            "iframe[data-src*='://youtube.com/embed']",
                            "iframe[data-src*='://youtube-nocookie.com/embed']",
                            "iframe[data-src*='://www.youtube.com/embed']",
                            "iframe[data-src*='://www.youtube-nocookie.com/embed']"
                        ],
                        replaceSettings: {
                            type: 'youtube-video',
                            buttonText: ytStrings.buttonTextUnblockVideo,
                            infoTitle: ytStrings.infoTitleUnblockVideo,
                            infoText: ytStrings.infoTextUnblockVideo,
                            previewToggleText: ytStrings.infoPreviewToggleText,
                            placeholder: {
                                previewToggleEnabledText: ytStrings.infoPreviewToggleEnabledText,
                                previewInfoText: ytStrings.infoPreviewInfoText,
                                videoPlayIcon: {
                                    lightMode: videoPlayLight,
                                    darkMode: videoPlayDark
                                }
                            }
                        },
                        clickAction: {
                            type: 'youtube-video'
                        }
                    },
                    'YouTube embedded subscription button': {
                        selectors: [
                            "iframe[src*='://youtube.com/subscribe_embed']",
                            "iframe[src*='://youtube-nocookie.com/subscribe_embed']",
                            "iframe[src*='://www.youtube.com/subscribe_embed']",
                            "iframe[src*='://www.youtube-nocookie.com/subscribe_embed']",
                            "iframe[data-src*='://youtube.com/subscribe_embed']",
                            "iframe[data-src*='://youtube-nocookie.com/subscribe_embed']",
                            "iframe[data-src*='://www.youtube.com/subscribe_embed']",
                            "iframe[data-src*='://www.youtube-nocookie.com/subscribe_embed']"
                        ],
                        replaceSettings: {
                            type: 'blank'
                        }
                    }
                }
            }
        };

        return { config, sharedStrings }
    }

    // @ts-nocheck

    let devMode = false;
    let isYoutubePreviewsEnabled = false;
    let appID;

    const titleID = 'DuckDuckGoPrivacyEssentialsCTLElementTitle';

    // Configuration for how the placeholder elements should look and behave.
    // @see {getConfig}
    let config = null;
    let sharedStrings = null;

    // TODO: Remove these redundant data structures and refactor the related code.
    //       There should be no need to have the entity configuration stored in two
    //       places.
    const entities = [];
    const entityData = {};

    let readyResolver;
    const ready = new Promise(resolve => { readyResolver = resolve; });

    /*********************************************************
     *  Widget Replacement logic
     *********************************************************/
    class DuckWidget {
        constructor (widgetData, originalElement, entity) {
            this.clickAction = { ...widgetData.clickAction }; // shallow copy
            this.replaceSettings = widgetData.replaceSettings;
            this.originalElement = originalElement;
            this.dataElements = {};
            this.gatherDataElements();
            this.entity = entity;
            this.widgetID = Math.random();
            // Boolean if widget is unblocked and content should not be blocked
            this.isUnblocked = false;
        }

        dispatchEvent (eventTarget, eventName) {
            eventTarget.dispatchEvent(
                createCustomEvent(
                    eventName, {
                        detail: {
                            entity: this.entity,
                            replaceSettings: this.replaceSettings,
                            widgetID: this.widgetID
                        }
                    }
                )
            );
        }

        // Collect and store data elements from original widget. Store default values
        // from config if not present.
        gatherDataElements () {
            if (!this.clickAction.urlDataAttributesToPreserve) {
                return
            }
            for (const [attrName, attrSettings] of Object.entries(this.clickAction.urlDataAttributesToPreserve)) {
                let value = this.originalElement.getAttribute(attrName);
                if (!value) {
                    if (attrSettings.required) {
                        // missing a required attribute means we won't be able to replace it
                        // with a light version, replace with full version.
                        this.clickAction.type = 'allowFull';
                    }
                    value = attrSettings.default;
                }
                this.dataElements[attrName] = value;
            }
        }

        // Return the facebook content URL to use when a user has clicked.
        getTargetURL () {
            // Copying over data fields should be done lazily, since some required data may not be
            // captured until after page scripts run.
            this.copySocialDataFields();
            return this.clickAction.targetURL
        }

        // Determine if element should render in dark mode
        getMode () {
            // Login buttons are always the login style types
            if (this.replaceSettings.type === 'loginButton') {
                return 'loginMode'
            }
            const mode = this.originalElement.getAttribute('data-colorscheme');
            if (mode === 'dark') {
                return 'darkMode'
            }
            return 'lightMode'
        }

        // The config file offers the ability to style the replaced facebook widget. This
        // collects the style from the original element & any specified in config for the element
        // type and returns a CSS string.
        getStyle () {
            let styleString = 'border: none;';

            if (this.clickAction.styleDataAttributes) {
                // Copy elements from the original div into style attributes as directed by config
                for (const [attr, valAttr] of Object.entries(this.clickAction.styleDataAttributes)) {
                    let valueFound = this.dataElements[valAttr.name];
                    if (!valueFound) {
                        valueFound = this.dataElements[valAttr.fallbackAttribute];
                    }
                    let partialStyleString = '';
                    if (valueFound) {
                        partialStyleString += `${attr}: ${valueFound}`;
                    }
                    if (!partialStyleString.includes(valAttr.unit)) {
                        partialStyleString += valAttr.unit;
                    }
                    partialStyleString += ';';
                    styleString += partialStyleString;
                }
            }

            return styleString
        }

        // Some data fields are 'kept' from the original element. These are used both in
        // replacement styling (darkmode, width, height), and when returning to a FB element.
        copySocialDataFields () {
            if (!this.clickAction.urlDataAttributesToPreserve) {
                return
            }

            // App ID may be set by client scripts, and is required for some elements.
            if (this.dataElements.app_id_replace && appID != null) {
                this.clickAction.targetURL = this.clickAction.targetURL.replace('app_id_replace', appID);
            }

            for (const key of Object.keys(this.dataElements)) {
                let attrValue = this.dataElements[key];

                if (!attrValue) {
                    continue
                }

                // The URL for Facebook videos are specified as the data-href
                // attribute on a div, that is then used to create the iframe.
                // Some websites omit the protocol part of the URL when doing
                // that, which then prevents the iframe from loading correctly.
                if (key === 'data-href' && attrValue.startsWith('//')) {
                    attrValue = window.location.protocol + attrValue;
                }

                this.clickAction.targetURL =
                    this.clickAction.targetURL.replace(
                        key, encodeURIComponent(attrValue)
                    );
            }
        }

        /*
            * Creates an iFrame for this facebook content.
            *
            * @returns {Element}
            */
        createFBIFrame () {
            const frame = document.createElement('iframe');

            frame.setAttribute('src', this.getTargetURL());
            frame.setAttribute('style', this.getStyle());

            return frame
        }

        /**
        * Tweaks an embedded YouTube video element ready for when it's
        * reloaded.
        *
        * @param {Element} videoElement
        * @returns {Function?} onError
        *   Function to be called if the video fails to load.
        */
        adjustYouTubeVideoElement (videoElement) {
            let onError = null;

            if (!videoElement.src) {
                return onError
            }
            const url = new URL(videoElement.src);
            const { hostname: originalHostname } = url;

            // Upgrade video to YouTube's "privacy enhanced" mode, but fall back
            // to standard mode if the video fails to load.
            // Note:
            //  1. Changing the iframe's host like this won't cause a CSP
            //     violation on Chrome, see https://crbug.com/1271196.
            //  2. The onError event doesn't fire for blocked iframes on Chrome.
            if (originalHostname !== 'www.youtube-nocookie.com') {
                url.hostname = 'www.youtube-nocookie.com';
                onError = (event) => {
                    url.hostname = originalHostname;
                    videoElement.src = url.href;
                    event.stopImmediatePropagation();
                };
            }

            // Configure auto-play correctly depending on if the video's preview
            // loaded, otherwise it doesn't allow autoplay.
            let allowString = videoElement.getAttribute('allow') || '';
            const allowed = new Set(allowString.split(';').map(s => s.trim()));
            if (this.autoplay) {
                allowed.add('autoplay');
                url.searchParams.set('autoplay', '1');
            } else {
                allowed.delete('autoplay');
                url.searchParams.delete('autoplay');
            }
            allowString = Array.from(allowed).join('; ');
            videoElement.setAttribute('allow', allowString);

            videoElement.src = url.href;
            return onError
        }

        /*
            * Fades out the given element. Returns a promise that resolves when the fade is complete.
            * @param {Element} element - the element to fade in or out
            * @param {int} interval - frequency of opacity updates (ms)
            * @param {bool} fadeIn - true if the element should fade in instead of out
            */
        fadeElement (element, interval, fadeIn) {
            return new Promise((resolve, reject) => {
                let opacity = fadeIn ? 0 : 1;
                const originStyle = element.style.cssText;
                const fadeOut = setInterval(function () {
                    opacity += fadeIn ? 0.03 : -0.03;
                    element.style.cssText = originStyle + `opacity: ${opacity};`;
                    if (opacity <= 0 || opacity >= 1) {
                        clearInterval(fadeOut);
                        resolve();
                    }
                }, interval);
            })
        }

        fadeOutElement (element) {
            return this.fadeElement(element, 10, false)
        }

        fadeInElement (element) {
            return this.fadeElement(element, 10, true)
        }

        clickFunction (originalElement, replacementElement) {
            let clicked = false;
            const handleClick = async function handleClick (e) {
                // Ensure that the click is created by a user event & prevent double clicks from adding more animations
                if (e.isTrusted && !clicked) {
                    this.isUnblocked = true;
                    clicked = true;
                    let isLogin = false;
                    const clickElement = e.srcElement; // Object.assign({}, e)
                    if (this.replaceSettings.type === 'loginButton') {
                        isLogin = true;
                    }
                    window.addEventListener('ddg-ctp-unblockClickToLoadContent-complete', () => {
                        const parent = replacementElement.parentNode;

                        // If we allow everything when this element is clicked,
                        // notify surrogate to enable SDK and replace original element.
                        if (this.clickAction.type === 'allowFull') {
                            parent.replaceChild(originalElement, replacementElement);
                            this.dispatchEvent(window, 'ddg-ctp-load-sdk');
                            return
                        }
                        // Create a container for the new FB element
                        const fbContainer = document.createElement('div');
                        fbContainer.style.cssText = styles.wrapperDiv;
                        const fadeIn = document.createElement('div');
                        fadeIn.style.cssText = 'display: none; opacity: 0;';

                        // Loading animation (FB can take some time to load)
                        const loadingImg = document.createElement('img');
                        loadingImg.setAttribute('src', loadingImages[this.getMode()]);
                        loadingImg.setAttribute('height', '14px');
                        loadingImg.style.cssText = styles.loadingImg;

                        // Always add the animation to the button, regardless of click source
                        if (clickElement.nodeName === 'BUTTON') {
                            clickElement.firstElementChild.insertBefore(loadingImg, clickElement.firstElementChild.firstChild);
                        } else {
                            // try to find the button
                            let el = clickElement;
                            let button = null;
                            while (button === null && el !== null) {
                                button = el.querySelector('button');
                                el = el.parentElement;
                            }
                            if (button) {
                                button.firstElementChild.insertBefore(loadingImg, button.firstElementChild.firstChild);
                            }
                        }

                        fbContainer.appendChild(fadeIn);

                        let fbElement;
                        let onError = null;
                        switch (this.clickAction.type) {
                        case 'iFrame':
                            fbElement = this.createFBIFrame();
                            break
                        case 'youtube-video':
                            onError = this.adjustYouTubeVideoElement(originalElement);
                            fbElement = originalElement;
                            break
                        default:
                            fbElement = originalElement;
                            break
                        }

                        // If hidden, restore the tracking element's styles to make
                        // it visible again.
                        if (this.originalElementStyle) {
                            for (const key of ['display', 'visibility']) {
                                const { value, priority } = this.originalElementStyle[key];
                                if (value) {
                                    fbElement.style.setProperty(key, value, priority);
                                } else {
                                    fbElement.style.removeProperty(key);
                                }
                            }
                        }

                        /*
                        * Modify the overlay to include a Facebook iFrame, which
                        * starts invisible. Once loaded, fade out and remove the overlay
                        * then fade in the Facebook content
                        */
                        parent.replaceChild(fbContainer, replacementElement);
                        fbContainer.appendChild(replacementElement);
                        fadeIn.appendChild(fbElement);
                        fbElement.addEventListener('load', () => {
                            this.fadeOutElement(replacementElement)
                                .then(v => {
                                    fbContainer.replaceWith(fbElement);
                                    this.dispatchEvent(fbElement, 'ddg-ctp-placeholder-clicked');
                                    this.fadeInElement(fadeIn).then(() => {
                                        fbElement.focus(); // focus on new element for screen readers
                                    });
                                });
                        }, { once: true });
                        // Note: This event only fires on Firefox, on Chrome the frame's
                        //       load event will always fire.
                        if (onError) {
                            fbElement.addEventListener('error', onError, { once: true });
                        }
                    }, { once: true });
                    const action = this.entity === 'Youtube' ? 'block-ctl-yt' : 'block-ctl-fb';
                    unblockClickToLoadContent({ entity: this.entity, action, isLogin });
                }
            }.bind(this);
            // If this is a login button, show modal if needed
            if (this.replaceSettings.type === 'loginButton' && entityData[this.entity].shouldShowLoginModal) {
                return function handleLoginClick (e) {
                    makeModal(this.entity, handleClick, e);
                }.bind(this)
            }
            return handleClick
        }
    }

    function replaceTrackingElement (widget, trackingElement, placeholderElement, hideTrackingElement = false, currentPlaceholder = null) {
        widget.dispatchEvent(trackingElement, 'ddg-ctp-tracking-element');

        // Usually the tracking element can simply be replaced with the
        // placeholder, but in some situations that isn't possible and the
        // tracking element must be hidden instead.
        if (hideTrackingElement) {
            // Don't save original element styles if we've already done it
            if (!widget.originalElementStyle) {
                // Take care to note existing styles so that they can be restored.
                widget.originalElementStyle = getOriginalElementStyle(trackingElement, widget);
            }
            // Hide the tracking element and add the placeholder next to it in
            // the DOM.
            trackingElement.style.setProperty('display', 'none', 'important');
            trackingElement.style.setProperty('visibility', 'hidden', 'important');
            trackingElement.parentElement.insertBefore(placeholderElement, trackingElement);
            if (currentPlaceholder) {
                currentPlaceholder.remove();
            }
        } else {
            if (currentPlaceholder) {
                currentPlaceholder.replaceWith(placeholderElement);
            } else {
                trackingElement.replaceWith(placeholderElement);
            }
        }

        widget.dispatchEvent(placeholderElement, 'ddg-ctp-placeholder-element');
    }

    /**
     * Creates a placeholder element for the given tracking element and replaces
     * it on the page.
     * @param {DuckWidget} widget
     *   The CTP 'widget' associated with the tracking element.
     * @param {Element} trackingElement
     *   The tracking element on the page that should be replaced with a placeholder.
     */
    async function createPlaceholderElementAndReplace (widget, trackingElement) {
        if (widget.replaceSettings.type === 'blank') {
            replaceTrackingElement(widget, trackingElement, document.createElement('div'));
        }

        if (widget.replaceSettings.type === 'loginButton') {
            const icon = widget.replaceSettings.icon;
            // Create a button to replace old element
            const { button, container } = makeLoginButton(
                widget.replaceSettings.buttonText, widget.getMode(),
                widget.replaceSettings.popupBodyText, icon, trackingElement
            );
            button.addEventListener('click', widget.clickFunction(trackingElement, container));
            replaceTrackingElement(widget, trackingElement, container);
        }

        /** Facebook CTL */
        if (widget.replaceSettings.type === 'dialog') {
            const icon = widget.replaceSettings.icon;
            const button = makeButton(widget.replaceSettings.buttonText, widget.getMode());
            const textButton = makeTextButton(widget.replaceSettings.buttonText, widget.getMode());
            const { contentBlock, shadowRoot } = await createContentBlock(
                widget, button, textButton, icon
            );
            button.addEventListener('click', widget.clickFunction(trackingElement, contentBlock));
            textButton.addEventListener('click', widget.clickFunction(trackingElement, contentBlock));

            replaceTrackingElement(
                widget, trackingElement, contentBlock
            );

            // Show the extra unblock link in the header if the placeholder or
            // its parent is too short for the normal unblock button to be visible.
            // Note: This does not take into account the placeholder's vertical
            //       position in the parent element.
            const { height: placeholderHeight } = window.getComputedStyle(contentBlock);
            const { height: parentHeight } = window.getComputedStyle(contentBlock.parentElement);
            if (parseInt(placeholderHeight, 10) <= 200 || parseInt(parentHeight, 10) <= 200) {
                const titleRowTextButton = shadowRoot.querySelector(`#${titleID + 'TextButton'}`);
                titleRowTextButton.style.display = 'block';
            }
        }

        /** YouTube CTL */
        if (widget.replaceSettings.type === 'youtube-video') {
            sendMessage('updateYouTubeCTLAddedFlag', true);
            await replaceYouTubeCTL(trackingElement, widget);

            // Subscribe to changes to youtubePreviewsEnabled setting
            // and update the CTL state
            window.addEventListener('ddg-settings-youtubePreviewsEnabled', ({ detail: value }) => {
                isYoutubePreviewsEnabled = value;
                replaceYouTubeCTL(trackingElement, widget, true);
            });
        }
    }

    /**
     * @param {Element} trackingElement
     *   The original tracking element (YouTube video iframe)
     * @param {DuckWidget} widget
     *   The CTP 'widget' associated with the tracking element.
     * @param {boolean} togglePlaceholder
     *   Boolean indicating if this function should toggle between placeholders,
     *   because tracking element has already been replaced
     */
    async function replaceYouTubeCTL (trackingElement, widget, togglePlaceholder = false) {
        // Skip replacing tracking element if it has already been unblocked
        if (widget.isUnblocked) {
            return
        }

        // Show YouTube Preview for embedded video
        // TODO: Fix the hideTrackingElement option and reenable, or remove it. It's
        //       disabled for YouTube videos so far since it caused multiple
        //       placeholders to be displayed on the page.
        if (isYoutubePreviewsEnabled === true) {
            const { youTubePreview, shadowRoot } = await createYouTubePreview(trackingElement, widget);
            const currentPlaceholder = togglePlaceholder ? document.getElementById(`yt-ctl-dialog-${widget.widgetID}`) : null;
            replaceTrackingElement(
                widget, trackingElement, youTubePreview, /* hideTrackingElement= */ false, currentPlaceholder
            );
            showExtraUnblockIfShortPlaceholder(shadowRoot, youTubePreview);

            // Block YouTube embedded video and display blocking dialog
        } else {
            widget.autoplay = false;
            const { blockingDialog, shadowRoot } = await createYouTubeBlockingDialog(trackingElement, widget);
            const currentPlaceholder = togglePlaceholder ? document.getElementById(`yt-ctl-preview-${widget.widgetID}`) : null;
            replaceTrackingElement(
                widget, trackingElement, blockingDialog, /* hideTrackingElement= */ false, currentPlaceholder
            );
            showExtraUnblockIfShortPlaceholder(shadowRoot, blockingDialog);
        }
    }

    /**
     /* Show the extra unblock link in the header if the placeholder or
    /* its parent is too short for the normal unblock button to be visible.
    /* Note: This does not take into account the placeholder's vertical
    /*       position in the parent element.
    * @param {Element} shadowRoot
    * @param {Element} placeholder Placeholder for tracking element
    */
    function showExtraUnblockIfShortPlaceholder (shadowRoot, placeholder) {
        const { height: placeholderHeight } = window.getComputedStyle(placeholder);
        const { height: parentHeight } = window.getComputedStyle(placeholder.parentElement);
        if (parseInt(placeholderHeight, 10) <= 200 || parseInt(parentHeight, 10) <= 200) {
            const titleRowTextButton = shadowRoot.querySelector(`#${titleID + 'TextButton'}`);
            titleRowTextButton.style.display = 'block';
        }
    }

    /**
     * Replace the blocked CTP elements on the page with placeholders.
     * @param {Element} [targetElement]
     *   If specified, only this element will be replaced (assuming it matches
     *   one of the expected CSS selectors). If omitted, all matching elements
     *   in the document will be replaced instead.
     */
    async function replaceClickToLoadElements (targetElement) {
        await ready;

        for (const entity of Object.keys(config)) {
            for (const widgetData of Object.values(config[entity].elementData)) {
                const selector = widgetData.selectors.join();

                let trackingElements = [];
                if (targetElement) {
                    if (targetElement.matches(selector)) {
                        trackingElements.push(targetElement);
                    }
                } else {
                    trackingElements = Array.from(document.querySelectorAll(selector));
                }

                await Promise.all(trackingElements.map(trackingElement => {
                    const widget = new DuckWidget(widgetData, trackingElement, entity);
                    return createPlaceholderElementAndReplace(widget, trackingElement)
                }));
            }
        }
    }

    /*********************************************************
     *  Messaging to surrogates & extension
     *********************************************************/

    /**
     * @typedef unblockClickToLoadContentRequest
     * @property {string} entity
     *   The entity to unblock requests for (e.g. "Facebook").
     * @property {bool} [isLogin=false]
     *   True if we should "allow social login", defaults to false.
     * @property {string} action
     *   The Click to Load blocklist rule action (e.g. "block-ctl-fb") that should
     *   be allowed. Important since in the future there might be multiple types of
     *   embedded content from the same entity that the user can allow
     *   independently.
     */

    /**
     * Send a message to the background to unblock requests for the given entity for
     * the page.
     * @param {unblockClickToLoadContentRequest} message
     * @see {@event ddg-ctp-unblockClickToLoadContent-complete} for the response handler.
     */
    function unblockClickToLoadContent (message) {
        sendMessage('unblockClickToLoadContent', message);
    }

    function runLogin (entity) {
        const action = entity === 'Youtube' ? 'block-ctl-yt' : 'block-ctl-fb';
        unblockClickToLoadContent({ entity, action, isLogin: true });
        originalWindowDispatchEvent(
            createCustomEvent('ddg-ctp-run-login', {
                detail: {
                    entity
                }
            })
        );
    }

    function cancelModal (entity) {
        originalWindowDispatchEvent(
            createCustomEvent('ddg-ctp-cancel-modal', {
                detail: {
                    entity
                }
            })
        );
    }

    function openShareFeedbackPage () {
        sendMessage('openShareFeedbackPage', '');
    }

    function getYouTubeVideoDetails (videoURL) {
        sendMessage('getYouTubeVideoDetails', videoURL);
    }

    /*********************************************************
     *  Widget building blocks
     *********************************************************/
    function getLearnMoreLink (mode) {
        if (!mode) {
            mode = 'lightMode';
        }

        const linkElement = document.createElement('a');
        linkElement.style.cssText = styles.generalLink + styles[mode].linkFont;
        linkElement.ariaLabel = sharedStrings.readAbout;
        linkElement.href = 'https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/';
        linkElement.target = '_blank';
        linkElement.textContent = sharedStrings.learnMore;
        return linkElement
    }

    /**
     * Reads and stores a set of styles from the original tracking element, and then returns it.
     * @param {Element} originalElement Original tracking element (ie iframe)
     * @param {DuckWidget} widget The widget Object.
     * @returns {{[key: string]: string[]}} Object with styles read from original element.
     */
    function getOriginalElementStyle (originalElement, widget) {
        if (widget.originalElementStyle) {
            return widget.originalElementStyle
        }

        const stylesToCopy = ['display', 'visibility', 'position', 'top', 'bottom', 'left', 'right',
            'transform', 'margin'];
        widget.originalElementStyle = {};
        const allOriginalElementStyles = getComputedStyle(originalElement);
        for (const key of stylesToCopy) {
            widget.originalElementStyle[key] = {
                value: allOriginalElementStyles[key],
                priority: originalElement.style.getPropertyPriority(key)
            };
        }

        // Copy current size of the element
        const { height: heightViewValue, width: widthViewValue } = originalElement.getBoundingClientRect();
        widget.originalElementStyle.height = { value: `${heightViewValue}px`, priority: '' };
        widget.originalElementStyle.width = { value: `${widthViewValue}px`, priority: '' };

        return widget.originalElementStyle
    }

    /**
     * Copy list of styles to provided element
     * @param {{[key: string]: string[]}} originalStyles Object with styles read from original element.
     * @param {Element} element Node element to have the styles copied to
     */
    function copyStylesTo (originalStyles, element) {
        const { display, visibility, ...filteredStyles } = originalStyles;
        const cssText = Object.keys(filteredStyles).reduce((cssAcc, key) => (cssAcc + `${key}: ${filteredStyles[key].value};`), '');
        element.style.cssText += cssText;
    }

    /**
     * Create a `<style/>` element with DDG font-face styles/CSS
     * to be attached to DDG wrapper elements
     * @returns HTMLStyleElement
     */
    function makeFontFaceStyleElement () {
        // Put our custom font-faces inside the wrapper element, since
        // @font-face does not work inside a shadowRoot.
        // See https://github.com/mdn/interactive-examples/issues/887.
        const fontFaceStyleElement = document.createElement('style');
        fontFaceStyleElement.textContent = styles.fontStyle;
        return fontFaceStyleElement
    }

    /**
     * Create a `<style/>` element with base styles for DDG social container and
     * button to be attached to DDG wrapper elements/shadowRoot, also returns a wrapper
     * class name for Social Container link styles
     * @param {"lightMode" | "darkMode"} mode Light or Dark mode value
     * @returns {{wrapperClass: string, styleElement: HTMLStyleElement; }}
     */
    function makeBaseStyleElement (mode = 'lightMode') {
        // Style element includes our font & overwrites page styles
        const styleElement = document.createElement('style');
        const wrapperClass = 'DuckDuckGoSocialContainer';
        styleElement.textContent = `
        .${wrapperClass} a {
            ${styles[mode].linkFont}
            font-weight: bold;
        }
        .${wrapperClass} a:hover {
            ${styles[mode].linkFont}
            font-weight: bold;
        }
        .DuckDuckGoButton {
            ${styles.button}
        }
        .DuckDuckGoButton > div {
            ${styles.buttonTextContainer}
        }
        .DuckDuckGoButton.primary {
           ${styles[mode].buttonBackground}
        }
        .DuckDuckGoButton.primary > div {
           ${styles[mode].buttonFont}
        }
        .DuckDuckGoButton.primary:hover {
           ${styles[mode].buttonBackgroundHover}
        }
        .DuckDuckGoButton.primary:active {
           ${styles[mode].buttonBackgroundPress}
        }
        .DuckDuckGoButton.secondary {
           ${styles.cancelMode.buttonBackground}
        }
        .DuckDuckGoButton.secondary > div {
            ${styles.cancelMode.buttonFont}
         }
        .DuckDuckGoButton.secondary:hover {
           ${styles.cancelMode.buttonBackgroundHover}
        }
        .DuckDuckGoButton.secondary:active {
           ${styles.cancelMode.buttonBackgroundPress}
        }
    `;
        return { wrapperClass, styleElement }
    }

    function makeTextButton (linkText, mode) {
        const linkElement = document.createElement('a');
        linkElement.style.cssText = styles.headerLink + styles[mode].linkFont;
        linkElement.textContent = linkText;
        return linkElement
    }

    /**
     * Create a button element.
     * @param {string} buttonText Text to be displayed inside the button
     * @param {'lightMode' | 'darkMode' | 'cancelMode'} mode Key for theme value to determine the styling of the button. Key matches `styles[mode]` keys.
     * - `'lightMode'`: Primary colors styling for light theme
     * - `'darkMode'`: Primary colors styling for dark theme
     * - `'cancelMode'`: Secondary colors styling for all themes
     * @returns {HTMLButtonElement} Button element
     */
    function makeButton (buttonText, mode = 'lightMode') {
        const button = document.createElement('button');
        button.classList.add('DuckDuckGoButton');
        button.classList.add(mode === 'cancelMode' ? 'secondary' : 'primary');
        if (buttonText) {
            const textContainer = document.createElement('div');
            textContainer.textContent = buttonText;
            button.appendChild(textContainer);
        }
        return button
    }

    function makeToggleButton (isActive = false, classNames = '', dataKey = '') {
        const toggleButton = document.createElement('button');
        toggleButton.className = classNames;
        toggleButton.style.cssText = styles.toggleButton;
        toggleButton.type = 'button';
        toggleButton.setAttribute('aria-pressed', isActive ? 'true' : 'false');
        toggleButton.setAttribute('data-key', dataKey);

        const toggleBg = document.createElement('div');
        toggleBg.style.cssText = styles.toggleButtonBg + (isActive ? styles.toggleButtonBgState.active : styles.toggleButtonBgState.inactive);

        const toggleKnob = document.createElement('div');
        toggleKnob.style.cssText = styles.toggleButtonKnob + (isActive ? styles.toggleButtonKnobState.active : styles.toggleButtonKnobState.inactive);

        toggleButton.appendChild(toggleBg);
        toggleButton.appendChild(toggleKnob);

        return toggleButton
    }

    function makeToggleButtonWithText (text, mode, isActive = false, toggleCssStyles = '', textCssStyles = '', dataKey = '') {
        const wrapper = document.createElement('div');
        wrapper.style.cssText = styles.toggleButtonWrapper;

        const toggleButton = makeToggleButton(isActive, toggleCssStyles, dataKey);

        const textDiv = document.createElement('div');
        textDiv.style.cssText = styles.contentText + styles.toggleButtonText + styles[mode].toggleButtonText + textCssStyles;
        textDiv.textContent = text;

        wrapper.appendChild(toggleButton);
        wrapper.appendChild(textDiv);
        return wrapper
    }

    /* If there isn't an image available, just make a default block symbol */
    function makeDefaultBlockIcon () {
        const blockedIcon = document.createElement('div');
        const dash = document.createElement('div');
        blockedIcon.appendChild(dash);
        blockedIcon.style.cssText = styles.circle;
        dash.style.cssText = styles.rectangle;
        return blockedIcon
    }

    function makeShareFeedbackLink () {
        const feedbackLink = document.createElement('a');
        feedbackLink.style.cssText = styles.feedbackLink;
        feedbackLink.target = '_blank';
        feedbackLink.href = '#';
        feedbackLink.text = 'Share Feedback';
        // Open Feedback Form page through background event to avoid browser blocking extension link
        feedbackLink.addEventListener('click', function (e) {
            e.preventDefault();
            openShareFeedbackPage();
        });

        return feedbackLink
    }

    function makeShareFeedbackRow () {
        const feedbackRow = document.createElement('div');
        feedbackRow.style.cssText = styles.feedbackRow;

        const feedbackLink = makeShareFeedbackLink();
        feedbackRow.appendChild(feedbackLink);

        return feedbackRow
    }

    /* FB login replacement button, with hover text */
    function makeLoginButton (buttonText, mode, hoverTextBody, icon, originalElement) {
        const container = document.createElement('div');
        container.style.cssText = 'position: relative;';
        container.appendChild(makeFontFaceStyleElement());

        const shadowRoot = container.attachShadow({ mode: devMode ? 'open' : 'closed' });
        // inherit any class styles on the button
        container.className = 'fb-login-button FacebookLogin__button';
        const { styleElement } = makeBaseStyleElement(mode);
        styleElement.textContent += `
        #DuckDuckGoPrivacyEssentialsHoverableText {
            display: none;
        }
        #DuckDuckGoPrivacyEssentialsHoverable:hover #DuckDuckGoPrivacyEssentialsHoverableText {
            display: block;
        }
    `;
        shadowRoot.appendChild(styleElement);

        const hoverContainer = document.createElement('div');
        hoverContainer.id = 'DuckDuckGoPrivacyEssentialsHoverable';
        hoverContainer.style.cssText = styles.hoverContainer;
        shadowRoot.appendChild(hoverContainer);

        // Make the button
        const button = makeButton(buttonText, mode);
        // Add blocked icon
        if (!icon) {
            button.appendChild(makeDefaultBlockIcon());
        } else {
            const imgElement = document.createElement('img');
            imgElement.style.cssText = styles.loginIcon;
            imgElement.setAttribute('src', icon);
            imgElement.setAttribute('height', '28px');
            button.appendChild(imgElement);
        }
        hoverContainer.appendChild(button);

        // hover action
        const hoverBox = document.createElement('div');
        hoverBox.id = 'DuckDuckGoPrivacyEssentialsHoverableText';
        hoverBox.style.cssText = styles.textBubble;
        const arrow = document.createElement('div');
        arrow.style.cssText = styles.textArrow;
        hoverBox.appendChild(arrow);
        const branding = createTitleRow('DuckDuckGo');
        branding.style.cssText += styles.hoverTextTitle;
        hoverBox.appendChild(branding);
        const hoverText = document.createElement('div');
        hoverText.style.cssText = styles.hoverTextBody;
        hoverText.textContent = hoverTextBody + ' ';
        hoverText.appendChild(getLearnMoreLink(mode));
        hoverBox.appendChild(hoverText);

        hoverContainer.appendChild(hoverBox);
        const rect = originalElement.getBoundingClientRect();
        /*
        * The left side of the hover popup may go offscreen if the
        * login button is all the way on the left side of the page. This
        * If that is the case, dynamically shift the box right so it shows
        * properly.
        */
        if (rect.left < styles.textBubbleLeftShift) {
            const leftShift = -rect.left + 10; // 10px away from edge of the screen
            hoverBox.style.cssText += `left: ${leftShift}px;`;
            const change = (1 - (rect.left / styles.textBubbleLeftShift)) * (100 - styles.arrowDefaultLocationPercent);
            arrow.style.cssText += `left: ${Math.max(10, styles.arrowDefaultLocationPercent - change)}%;`;
        } else if (rect.left + styles.textBubbleWidth - styles.textBubbleLeftShift > window.innerWidth) {
            const rightShift = rect.left + styles.textBubbleWidth - styles.textBubbleLeftShift;
            const diff = Math.min(rightShift - window.innerWidth, styles.textBubbleLeftShift);
            const rightMargin = 20; // Add some margin to the page, so scrollbar doesn't overlap.
            hoverBox.style.cssText += `left: -${styles.textBubbleLeftShift + diff + rightMargin}px;`;
            const change = ((diff / styles.textBubbleLeftShift)) * (100 - styles.arrowDefaultLocationPercent);
            arrow.style.cssText += `left: ${Math.max(10, styles.arrowDefaultLocationPercent + change)}%;`;
        } else {
            hoverBox.style.cssText += `left: -${styles.textBubbleLeftShift}px;`;
            arrow.style.cssText += `left: ${styles.arrowDefaultLocationPercent}%;`;
        }

        return {
            button,
            container
        }
    }

    async function makeModal (entity, acceptFunction, ...acceptFunctionParams) {
        const icon = entityData[entity].modalIcon;

        const modalContainer = document.createElement('div');
        modalContainer.setAttribute('data-key', 'modal');
        modalContainer.style.cssText = styles.modalContainer;

        modalContainer.appendChild(makeFontFaceStyleElement());

        const closeModal = () => {
            document.body.removeChild(modalContainer);
            cancelModal(entity);
        };

        // Protect the contents of our modal inside a shadowRoot, to avoid
        // it being styled by the website's stylesheets.
        const shadowRoot = modalContainer.attachShadow({ mode: devMode ? 'open' : 'closed' });
        const { styleElement } = makeBaseStyleElement('lightMode');
        shadowRoot.appendChild(styleElement);

        const pageOverlay = document.createElement('div');
        pageOverlay.style.cssText = styles.overlay;

        const modal = document.createElement('div');
        modal.style.cssText = styles.modal;

        // Title
        const modalTitle = createTitleRow('DuckDuckGo', null, closeModal);
        modal.appendChild(modalTitle);

        const iconElement = document.createElement('img');
        iconElement.style.cssText = styles.icon + styles.modalIcon;
        iconElement.setAttribute('src', icon);
        iconElement.setAttribute('height', '70px');

        const title = document.createElement('div');
        title.style.cssText = styles.modalContentTitle;
        title.textContent = entityData[entity].modalTitle;

        // Content
        const modalContent = document.createElement('div');
        modalContent.style.cssText = styles.modalContent;

        const message = document.createElement('div');
        message.style.cssText = styles.modalContentText;
        message.textContent = entityData[entity].modalText + ' ';
        message.appendChild(getLearnMoreLink());

        modalContent.appendChild(iconElement);
        modalContent.appendChild(title);
        modalContent.appendChild(message);

        // Buttons
        const buttonRow = document.createElement('div');
        buttonRow.style.cssText = styles.modalButtonRow;
        const allowButton = makeButton(entityData[entity].modalAcceptText, 'lightMode');
        allowButton.style.cssText += styles.modalButton + 'margin-bottom: 8px;';
        allowButton.setAttribute('data-key', 'allow');
        allowButton.addEventListener('click', function doLogin () {
            acceptFunction(...acceptFunctionParams);
            document.body.removeChild(modalContainer);
        });
        const rejectButton = makeButton(entityData[entity].modalRejectText, 'cancelMode');
        rejectButton.setAttribute('data-key', 'reject');
        rejectButton.style.cssText += styles.modalButton;
        rejectButton.addEventListener('click', closeModal);

        buttonRow.appendChild(allowButton);
        buttonRow.appendChild(rejectButton);
        modalContent.appendChild(buttonRow);

        modal.appendChild(modalContent);

        shadowRoot.appendChild(pageOverlay);
        shadowRoot.appendChild(modal);

        document.body.insertBefore(modalContainer, document.body.childNodes[0]);
    }

    function createTitleRow (message, textButton, closeBtnFn) {
        // Create row container
        const row = document.createElement('div');
        row.style.cssText = styles.titleBox;

        // Logo
        const logoContainer = document.createElement('div');
        logoContainer.style.cssText = styles.logo;
        const logoElement = document.createElement('img');
        logoElement.setAttribute('src', logoImg);
        logoElement.setAttribute('height', '21px');
        logoElement.style.cssText = styles.logoImg;
        logoContainer.appendChild(logoElement);
        row.appendChild(logoContainer);

        // Content box title
        const msgElement = document.createElement('div');
        msgElement.id = titleID; // Ensure we can find this to potentially hide it later.
        msgElement.textContent = message;
        msgElement.style.cssText = styles.title;
        row.appendChild(msgElement);

        // Close Button
        if (typeof closeBtnFn === 'function') {
            const closeButton = document.createElement('button');
            closeButton.style.cssText = styles.closeButton;
            const closeIconImg = document.createElement('img');
            closeIconImg.setAttribute('src', closeIcon);
            closeIconImg.setAttribute('height', '12px');
            closeIconImg.style.cssText = styles.closeIcon;
            closeButton.appendChild(closeIconImg);
            closeButton.addEventListener('click', closeBtnFn);
            row.appendChild(closeButton);
        }

        // Text button for very small boxes
        if (textButton) {
            textButton.id = titleID + 'TextButton';
            row.appendChild(textButton);
        }

        return row
    }

    // Create the content block to replace other divs/iframes with
    async function createContentBlock (widget, button, textButton, img, bottomRow) {
        const contentBlock = document.createElement('div');
        contentBlock.style.cssText = styles.wrapperDiv;

        contentBlock.appendChild(makeFontFaceStyleElement());

        // Put everything else inside the shadowRoot of the wrapper element to
        // reduce the chances of the website's stylesheets messing up the
        // placeholder's appearance.
        const shadowRootMode = devMode ? 'open' : 'closed';
        const shadowRoot = contentBlock.attachShadow({ mode: shadowRootMode });

        // Style element includes our font & overwrites page styles
        const { wrapperClass, styleElement } = makeBaseStyleElement(widget.getMode());
        shadowRoot.appendChild(styleElement);

        // Create overall grid structure
        const element = document.createElement('div');
        element.style.cssText = styles.block + styles[widget.getMode()].background + styles[widget.getMode()].textFont;
        if (widget.replaceSettings.type === 'youtube-video') {
            element.style.cssText += styles.youTubeDialogBlock;
        }
        element.className = wrapperClass;
        shadowRoot.appendChild(element);

        // grid of three rows
        const titleRow = document.createElement('div');
        titleRow.style.cssText = styles.headerRow;
        element.appendChild(titleRow);
        titleRow.appendChild(createTitleRow('DuckDuckGo', textButton));

        const contentRow = document.createElement('div');
        contentRow.style.cssText = styles.content;

        if (img) {
            const imageRow = document.createElement('div');
            imageRow.style.cssText = styles.imgRow;
            const imgElement = document.createElement('img');
            imgElement.style.cssText = styles.icon;
            imgElement.setAttribute('src', img);
            imgElement.setAttribute('height', '70px');
            imageRow.appendChild(imgElement);
            element.appendChild(imageRow);
        }

        const contentTitle = document.createElement('div');
        contentTitle.style.cssText = styles.contentTitle;
        contentTitle.textContent = widget.replaceSettings.infoTitle;
        contentRow.appendChild(contentTitle);
        const contentText = document.createElement('div');
        contentText.style.cssText = styles.contentText;
        contentText.textContent = widget.replaceSettings.infoText + ' ';
        contentText.appendChild(getLearnMoreLink());
        contentRow.appendChild(contentText);
        element.appendChild(contentRow);

        const buttonRow = document.createElement('div');
        buttonRow.style.cssText = styles.buttonRow;
        buttonRow.appendChild(button);
        contentText.appendChild(buttonRow);

        if (bottomRow) {
            contentRow.appendChild(bottomRow);
        }

        /** Share Feedback Link */
        if (widget.replaceSettings.type === 'youtube-video') {
            const feedbackRow = makeShareFeedbackRow();
            shadowRoot.appendChild(feedbackRow);
        }

        return { contentBlock, shadowRoot }
    }

    // Create the content block to replace embedded youtube videos/iframes with
    async function createYouTubeBlockingDialog (trackingElement, widget) {
        const button = makeButton(widget.replaceSettings.buttonText, widget.getMode());
        const textButton = makeTextButton(widget.replaceSettings.buttonText, widget.getMode());

        const bottomRow = document.createElement('div');
        bottomRow.style.cssText = styles.youTubeDialogBottomRow;
        const previewToggle = makeToggleButtonWithText(
            widget.replaceSettings.previewToggleText,
            widget.getMode(),
            false,
            '',
            '',
            'yt-preview-toggle'
        );
        previewToggle.addEventListener(
            'click',
            () => makeModal(widget.entity, () => sendMessage('setYoutubePreviewsEnabled', true), widget.entity)
        );
        bottomRow.appendChild(previewToggle);

        const { contentBlock, shadowRoot } = await createContentBlock(
            widget, button, textButton, null, bottomRow
        );
        contentBlock.id = `yt-ctl-dialog-${widget.widgetID}`;
        contentBlock.style.cssText += styles.wrapperDiv + styles.youTubeWrapperDiv;

        button.addEventListener('click', widget.clickFunction(trackingElement, contentBlock));
        textButton.addEventListener('click', widget.clickFunction(trackingElement, contentBlock));

        // Size the placeholder element to match the original video element styles.
        // If no styles are in place, it will get its current size
        const originalStyles = getOriginalElementStyle(trackingElement, widget);
        copyStylesTo(originalStyles, contentBlock);

        return {
            blockingDialog: contentBlock,
            shadowRoot
        }
    }

    /**
     * Creates the placeholder element to replace a YouTube video iframe element
     * with a preview image. Mutates widget Object to set the autoplay property
     * as the preview details load.
     * @param {Element} originalElement
     *   The YouTube video iframe element.
     * @param {DuckWidget} widget
     *   The widget Object. We mutate this to set the autoplay property.
     * @returns {{ youTubePreview: Element, shadowRoot: Element }}
     *   Object containing the YouTube Preview element and its shadowRoot.
     */
    async function createYouTubePreview (originalElement, widget) {
        const youTubePreview = document.createElement('div');
        youTubePreview.id = `yt-ctl-preview-${widget.widgetID}`;
        youTubePreview.style.cssText = styles.wrapperDiv + styles.placeholderWrapperDiv;

        youTubePreview.appendChild(makeFontFaceStyleElement());

        // Size the placeholder element to match the original video element styles.
        // If no styles are in place, it will get its current size
        const originalStyles = getOriginalElementStyle(originalElement, widget);
        copyStylesTo(originalStyles, youTubePreview);

        // Protect the contents of our placeholder inside a shadowRoot, to avoid
        // it being styled by the website's stylesheets.
        const shadowRoot = youTubePreview.attachShadow({ mode: devMode ? 'open' : 'closed' });
        const { wrapperClass, styleElement } = makeBaseStyleElement(widget.getMode());
        shadowRoot.appendChild(styleElement);

        const youTubePreviewDiv = document.createElement('div');
        youTubePreviewDiv.style.cssText = styles.youTubeDialogDiv;
        youTubePreviewDiv.classList.add(wrapperClass);
        shadowRoot.appendChild(youTubePreviewDiv);

        /** Preview Image */
        const previewImageWrapper = document.createElement('div');
        previewImageWrapper.style.cssText = styles.youTubePreviewWrapperImg;
        youTubePreviewDiv.appendChild(previewImageWrapper);
        // We use an image element for the preview image so that we can ensure
        // the referrer isn't passed.
        const previewImageElement = document.createElement('img');
        previewImageElement.setAttribute('referrerPolicy', 'no-referrer');
        previewImageElement.style.cssText = styles.youTubePreviewImg;
        previewImageWrapper.appendChild(previewImageElement);

        const innerDiv = document.createElement('div');
        innerDiv.style.cssText = styles.youTubePlaceholder;

        /** Top section */
        const topSection = document.createElement('div');
        topSection.style.cssText = styles.youTubeTopSection;
        innerDiv.appendChild(topSection);

        /** Video Title */
        const titleElement = document.createElement('p');
        titleElement.style.cssText = styles.youTubeTitle;
        topSection.appendChild(titleElement);

        /** Text Button on top section */
        // Use darkMode styles because the preview background is dark and causes poor contrast
        // with lightMode button, making it hard to read.
        const textButton = makeTextButton(widget.replaceSettings.buttonText, 'darkMode');
        textButton.id = titleID + 'TextButton';

        textButton.addEventListener(
            'click',
            widget.clickFunction(originalElement, youTubePreview)
        );
        topSection.appendChild(textButton);

        /** Play Button */
        const playButtonRow = document.createElement('div');
        playButtonRow.style.cssText = styles.youTubePlayButtonRow;

        const playButton = makeButton('', widget.getMode());
        playButton.style.cssText += styles.youTubePlayButton;

        const videoPlayImg = document.createElement('img');
        const videoPlayIcon = widget.replaceSettings.placeholder.videoPlayIcon[widget.getMode()];
        videoPlayImg.setAttribute('src', videoPlayIcon);
        playButton.appendChild(videoPlayImg);

        playButton.addEventListener(
            'click',
            widget.clickFunction(originalElement, youTubePreview)
        );
        playButtonRow.appendChild(playButton);
        innerDiv.appendChild(playButtonRow);

        /** Preview Toggle */
        const previewToggleRow = document.createElement('div');
        previewToggleRow.style.cssText = styles.youTubePreviewToggleRow;

        const previewToggle = makeToggleButtonWithText(
            widget.replaceSettings.placeholder.previewToggleEnabledText,
            widget.getMode(),
            true,
            '',
            styles.youTubePreviewToggleText,
            'yt-preview-toggle'
        );
        previewToggle.addEventListener(
            'click',
            () => sendMessage('setYoutubePreviewsEnabled', false)
        );

        /** Preview Info Text */
        const previewText = document.createElement('div');
        previewText.style.cssText = styles.contentText + styles.toggleButtonText + styles.youTubePreviewInfoText;
        previewText.innerText = widget.replaceSettings.placeholder.previewInfoText + ' ';
        // Use darkMode styles because of preview background
        previewText.appendChild(getLearnMoreLink('darkMode'));

        previewToggleRow.appendChild(previewToggle);
        previewToggleRow.appendChild(previewText);
        innerDiv.appendChild(previewToggleRow);

        youTubePreviewDiv.appendChild(innerDiv);

        widget.autoplay = false;
        // We use .then() instead of await here to show the placeholder right away
        // while the YouTube endpoint takes it time to respond.
        const videoURL = originalElement.src || originalElement.getAttribute('data-src');
        getYouTubeVideoDetails(videoURL);
        window.addEventListener('ddg-ctp-youTubeVideoDetails',
            ({ detail: { videoURL: videoURLResp, status, title, previewImage } }) => {
                if (videoURLResp !== videoURL) { return }
                if (status === 'success') {
                    titleElement.innerText = title;
                    titleElement.title = title;
                    if (previewImage) {
                        previewImageElement.setAttribute('src', previewImage);
                    }
                    widget.autoplay = true;
                }
            }
        );

        /** Share Feedback Link */
        const feedbackRow = makeShareFeedbackRow();
        shadowRoot.appendChild(feedbackRow);

        return { youTubePreview, shadowRoot }
    }

    // Convention is that each function should be named the same as the sendMessage
    // method we are calling into eg. calling `sendMessage('getClickToLoadState')`
    // will result in a response routed to `updateHandlers.getClickToLoadState()`.
    const messageResponseHandlers = {
        getClickToLoadState (response) {
            devMode = response.devMode;
            isYoutubePreviewsEnabled = response.youtubePreviewsEnabled;

            // TODO: Move the below init logic to the exported init() function,
            //       somehow waiting for this response handler to have been called
            //       first.

            // Start Click to Load
            window.addEventListener('ddg-ctp-replace-element', ({ target }) => {
                replaceClickToLoadElements(target);
            }, { capture: true });

            // Inform surrogate scripts that CTP is ready
            originalWindowDispatchEvent(createCustomEvent('ddg-ctp-ready'));

            // Mark the feature as ready, to allow placeholder replacements.
            readyResolver();
        },
        setYoutubePreviewsEnabled: function (resp) {
            if (resp?.messageType && typeof resp?.value === 'boolean') {
                originalWindowDispatchEvent(new OriginalCustomEvent(resp.messageType, { detail: resp.value }));
            }
        },
        getYouTubeVideoDetails: function (resp) {
            if (resp?.status && typeof resp.videoURL === 'string') {
                originalWindowDispatchEvent(new OriginalCustomEvent('ddg-ctp-youTubeVideoDetails', { detail: resp }));
            }
        },
        unblockClickToLoadContent () {
            originalWindowDispatchEvent(new OriginalCustomEvent('ddg-ctp-unblockClickToLoadContent-complete'));
        }
    };

    const knownMessageResponseType = Object.prototype.hasOwnProperty.bind(messageResponseHandlers);

    function init$f (args) {
        const websiteOwner = args?.site?.parentEntity;
        const settings = args?.featureSettings?.clickToPlay || {};
        const locale = args?.locale || 'en';
        const localizedConfig = getConfig(locale);
        config = localizedConfig.config;
        sharedStrings = localizedConfig.sharedStrings;

        for (const entity of Object.keys(config)) {
            // TODO: Remove this workaround once the privacy-configuration has been
            //       updated, and 'Facebook, Inc.' is used consistently in
            //       content-scope-scripts too.
            const normalizedEntity = entity === 'Facebook' ? 'Facebook, Inc.' : entity;

            // Strip config entities that are first-party, or aren't enabled in the
            // extension's clickToPlay settings.
            if ((websiteOwner && normalizedEntity === websiteOwner) ||
                !settings[entity] ||
                settings[entity].state !== 'enabled') {
                delete config[entity];
                continue
            }

            // Populate the entities and entityData data structures.
            // TODO: Remove them and this logic, they seem unnecessary.

            entities.push(entity);

            const shouldShowLoginModal = !!config[entity].informationalModal;
            const currentEntityData = { shouldShowLoginModal };

            if (shouldShowLoginModal) {
                const { informationalModal } = config[entity];
                currentEntityData.modalIcon = informationalModal.icon;
                currentEntityData.modalTitle = informationalModal.messageTitle;
                currentEntityData.modalText = informationalModal.messageBody;
                currentEntityData.modalAcceptText = informationalModal.confirmButtonText;
                currentEntityData.modalRejectText = informationalModal.rejectButtonText;
            }

            entityData[entity] = currentEntityData;
        }

        // Listen for events from "surrogate" scripts.
        addEventListener('ddg-ctp', (event) => {
            if (!event.detail) return
            const entity = event.detail.entity;
            if (!entities.includes(entity)) {
                // Unknown entity, reject
                return
            }
            if (event.detail.appID) {
                appID = JSON.stringify(event.detail.appID).replace(/"/g, '');
            }
            // Handle login call
            if (event.detail.action === 'login') {
                if (entityData[entity].shouldShowLoginModal) {
                    makeModal(entity, runLogin, entity);
                } else {
                    runLogin(entity);
                }
            }
        });

        // Request the current state of Click to Load from the platform.
        // Note: When the response is received, the response handler finishes
        //       starting up the feature.
        sendMessage('getClickToLoadState');
    }

    function update$1 (message) {
        // TODO: Once all Click to Load messages include the feature property, drop
        //       messages that don't include the feature property too.
        if (message?.feature && message?.feature !== 'clickToLoad') return

        const messageType = message?.messageType;
        if (!messageType) return

        // Message responses.
        if (messageType === 'response') {
            const messageResponseType = message?.responseMessageType;
            if (messageResponseType && knownMessageResponseType(messageResponseType)) {
                return messageResponseHandlers[messageResponseType](message.response)
            }
        }

        // Other known update messages.
        if (messageType === 'displayClickToLoadPlaceholders') {
            // TODO: Pass `message.options.ruleAction` through, that way only
            //       content corresponding to the entity for that ruleAction need to
            //       be replaced with a placeholder.
            return replaceClickToLoadElements()
        }
    }

    var clickToPlay = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$f,
        update: update$1
    });

    class Cookie {
        constructor (cookieString) {
            this.parts = cookieString.split(';');
            this.parse();
        }

        parse () {
            const EXTRACT_ATTRIBUTES = new Set(['max-age', 'expires', 'domain']);
            this.attrIdx = {};
            this.parts.forEach((part, index) => {
                const kv = part.split('=', 1);
                const attribute = kv[0].trim();
                const value = part.slice(kv[0].length + 1);
                if (index === 0) {
                    this.name = attribute;
                    this.value = value;
                } else if (EXTRACT_ATTRIBUTES.has(attribute.toLowerCase())) {
                    this[attribute.toLowerCase()] = value;
                    this.attrIdx[attribute.toLowerCase()] = index;
                }
            });
        }

        getExpiry () {
            // @ts-ignore
            if (!this.maxAge && !this.expires) {
                return NaN
            }
            const expiry = this.maxAge
                ? parseInt(this.maxAge)
                // @ts-ignore
                : (new Date(this.expires) - new Date()) / 1000;
            return expiry
        }

        get maxAge () {
            return this['max-age']
        }

        set maxAge (value) {
            if (this.attrIdx['max-age'] > 0) {
                this.parts.splice(this.attrIdx['max-age'], 1, `max-age=${value}`);
            } else {
                this.parts.push(`max-age=${value}`);
            }
            this.parse();
        }

        toString () {
            return this.parts.join(';')
        }
    }

    // Initial cookie policy pre init
    let cookiePolicy = {
        debug: false,
        isFrame: isBeingFramed(),
        isTracker: false,
        shouldBlock: true,
        shouldBlockTrackerCookie: true,
        shouldBlockNonTrackerCookie: false,
        isThirdParty: isThirdParty(),
        policy: {
            threshold: 604800, // 7 days
            maxAge: 604800 // 7 days
        }
    };

    let loadedPolicyResolve;
    // Listen for a message from the content script which will configure the policy for this context
    const trackerHosts = new Set();

    /**
     * @param {'ignore' | 'block' | 'restrict'} action
     * @param {string} reason
     * @param {any} ctx
     */
    function debugHelper (action, reason, ctx) {
        cookiePolicy.debug && postDebugMessage('jscookie', {
            action,
            reason,
            stack: ctx.stack,
            documentUrl: globalThis.document.location.href,
            scriptOrigins: [...ctx.scriptOrigins],
            value: ctx.value
        });
    }

    function shouldBlockTrackingCookie () {
        return cookiePolicy.shouldBlock && cookiePolicy.shouldBlockTrackerCookie && isTrackingCookie()
    }

    function shouldBlockNonTrackingCookie () {
        return cookiePolicy.shouldBlock && cookiePolicy.shouldBlockNonTrackerCookie && isNonTrackingCookie()
    }

    function isTrackingCookie () {
        return cookiePolicy.isFrame && cookiePolicy.isTracker && cookiePolicy.isThirdParty
    }

    function isNonTrackingCookie () {
        return cookiePolicy.isFrame && !cookiePolicy.isTracker && cookiePolicy.isThirdParty
    }

    function load$1 (args) {
        // Feature is only relevant to the extension and windows, we should skip for other platforms for now as the config testing is broken.
        if (args.platform.name !== 'extension' && args.platform.name !== 'windows') {
            return
        }
        if (args.documentOriginIsTracker) {
            cookiePolicy.isTracker = true;
        }
        if (args.bundledConfig) {
            // use the bundled config to get a best-effort at the policy, before the background sends the real one
            const { exceptions, settings } = args.bundledConfig.features.cookie;
            const tabHostname = getTabHostname();
            let tabExempted = true;

            if (tabHostname != null) {
                tabExempted = exceptions.some((exception) => {
                    return matchHostname(tabHostname, exception.domain)
                });
            }
            const frameExempted = settings.excludedCookieDomains.some((exception) => {
                return matchHostname(globalThis.location.hostname, exception.domain)
            });
            cookiePolicy.shouldBlock = !frameExempted && !tabExempted;
            cookiePolicy.policy = settings.firstPartyCookiePolicy;
        }
        trackerHosts.clear();

        // The cookie policy is injected into every frame immediately so that no cookie will
        // be missed.
        const document = globalThis.document;
        const cookieSetter = Object.getOwnPropertyDescriptor(globalThis.Document.prototype, 'cookie').set;
        const cookieGetter = Object.getOwnPropertyDescriptor(globalThis.Document.prototype, 'cookie').get;

        const loadPolicy = new Promise((resolve) => {
            loadedPolicyResolve = resolve;
        });
        // Create the then callback now - this ensures that Promise.prototype.then changes won't break
        // this call.
        const loadPolicyThen = loadPolicy.then.bind(loadPolicy);

        function getCookiePolicy () {
            const stack = getStack();
            const scriptOrigins = getStackTraceOrigins(stack);
            const getCookieContext = {
                stack,
                scriptOrigins,
                value: 'getter'
            };

            if (shouldBlockTrackingCookie() || shouldBlockNonTrackingCookie()) {
                debugHelper('block', '3p frame', getCookieContext);
                return ''
            } else if (isTrackingCookie() || isNonTrackingCookie()) {
                debugHelper('ignore', '3p frame', getCookieContext);
            }
            return cookieGetter.call(document)
        }

        function setCookiePolicy (value) {
            const stack = getStack();
            const scriptOrigins = getStackTraceOrigins(stack);
            const setCookieContext = {
                stack,
                scriptOrigins,
                value
            };

            if (shouldBlockTrackingCookie() || shouldBlockNonTrackingCookie()) {
                debugHelper('block', '3p frame', setCookieContext);
                return
            } else if (isTrackingCookie() || isNonTrackingCookie()) {
                debugHelper('ignore', '3p frame', setCookieContext);
            }
            // call the native document.cookie implementation. This will set the cookie immediately
            // if the value is valid. We will override this set later if the policy dictates that
            // the expiry should be changed.
            cookieSetter.call(document, value);

            try {
                // wait for config before doing same-site tests
                loadPolicyThen(() => {
                    const { shouldBlock, policy } = cookiePolicy;

                    if (!shouldBlock) {
                        debugHelper('ignore', 'disabled', setCookieContext);
                        return
                    }

                    // extract cookie expiry from cookie string
                    const cookie = new Cookie(value);
                    // apply cookie policy
                    if (cookie.getExpiry() > policy.threshold) {
                        // check if the cookie still exists
                        if (document.cookie.split(';').findIndex(kv => kv.trim().startsWith(cookie.parts[0].trim())) !== -1) {
                            cookie.maxAge = policy.maxAge;

                            debugHelper('restrict', 'expiry', setCookieContext);

                            cookieSetter.apply(document, [cookie.toString()]);
                        } else {
                            debugHelper('ignore', 'dissappeared', setCookieContext);
                        }
                    } else {
                        debugHelper('ignore', 'expiry', setCookieContext);
                    }
                });
            } catch (e) {
                debugHelper('ignore', 'error', setCookieContext);
                // suppress error in cookie override to avoid breakage
                console.warn('Error in cookie override', e);
            }
        }

        defineProperty(document, 'cookie', {
            configurable: true,
            set: setCookiePolicy,
            get: getCookiePolicy
        });
    }

    function init$e (args) {
        if (args.cookie) {
            cookiePolicy = args.cookie;
            args.cookie.debug = args.debug;

            const featureName = 'cookie';
            cookiePolicy.shouldBlockTrackerCookie = getFeatureSettingEnabled(featureName, args, 'trackerCookie');
            cookiePolicy.shouldBlockNonTrackerCookie = getFeatureSettingEnabled(featureName, args, 'nonTrackerCookie');
            const policy = getFeatureSetting(featureName, args, 'firstPartyCookiePolicy');
            if (policy) {
                cookiePolicy.policy = policy;
            }
        } else {
            // no cookie information - disable protections
            cookiePolicy.shouldBlock = false;
        }

        loadedPolicyResolve();
    }

    function update (args) {
        if (args.trackerDefinition) {
            trackerHosts.add(args.hostname);
        }
    }

    var cookie = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$e,
        load: load$1,
        update: update
    });

    let adLabelStrings = [];
    const parser = new DOMParser();
    let hiddenElements = new WeakMap();
    let appliedRules = new Set();
    let shouldInjectStyleTag = false;
    let mediaAndFormSelectors = 'video,canvas,embed,object,audio,map,form,input,textarea,select,option,button';

    /**
     * Hide DOM element if rule conditions met
     * @param {HTMLElement} element
     * @param {Object} rule
     * @param {HTMLElement} [previousElement]
     */
    function collapseDomNode (element, rule, previousElement) {
        if (!element) {
            return
        }
        const type = rule.type;
        const alreadyHidden = hiddenElements.has(element);

        if (alreadyHidden) {
            return
        }

        switch (type) {
        case 'hide':
            hideNode(element);
            break
        case 'hide-empty':
            if (isDomNodeEmpty(element)) {
                hideNode(element);
                appliedRules.add(rule);
            }
            break
        case 'closest-empty':
            // hide the outermost empty node so that we may unhide if ad loads
            if (isDomNodeEmpty(element)) {
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                collapseDomNode(element.parentNode, rule, element);
            } else if (previousElement) {
                hideNode(previousElement);
                appliedRules.add(rule);
            }
            break
        }
    }

    /**
     * Unhide previously hidden DOM element if content loaded into it
     * @param {HTMLElement} element
     * @param {Object} rule
     * @param {HTMLElement} [previousElement]
     */
    function expandNonEmptyDomNode (element, rule, previousElement) {
        if (!element) {
            return
        }
        const type = rule.type;

        const alreadyHidden = hiddenElements.has(element);

        switch (type) {
        case 'hide':
            // only care about rule types that specifically apply to empty elements
            break
        case 'hide-empty':
        case 'closest-empty':
            if (alreadyHidden && !isDomNodeEmpty(element)) {
                unhideNode(element);
            } else if (type === 'closest-empty') {
                // iterate upwards from matching DOM elements until we arrive at previously
                // hidden element. Unhide element if it contains visible content.
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                expandNonEmptyDomNode(element.parentNode, rule);
            }
            break
        }
    }

    /**
     * Hide DOM element
     * @param {HTMLElement} element
     */
    function hideNode (element) {
        // maintain a reference to each hidden element along with the properties
        // that are being overwritten
        const cachedDisplayProperties = {
            display: element.style.display,
            'min-height': element.style.minHeight,
            height: element.style.height
        };
        hiddenElements.set(element, cachedDisplayProperties);

        // apply styles to hide element
        element.style.setProperty('display', 'none', 'important');
        element.style.setProperty('min-height', '0px', 'important');
        element.style.setProperty('height', '0px', 'important');
        element.hidden = true;
    }

    /**
     * Show previously hidden DOM element
     * @param {HTMLElement} element
     */
    function unhideNode (element) {
        const cachedDisplayProperties = hiddenElements.get(element);
        if (!cachedDisplayProperties) {
            return
        }

        for (const prop in cachedDisplayProperties) {
            element.style.setProperty(prop, cachedDisplayProperties[prop]);
        }
        hiddenElements.delete(element);
        element.hidden = false;
    }

    /**
     * Check if DOM element contains visible content
     * @param {HTMLElement} node
     */
    function isDomNodeEmpty (node) {
        // no sense wasting cycles checking if the page's body element is empty
        if (node.tagName === 'BODY') {
            return false
        }
        // use a DOMParser to remove all metadata elements before checking if
        // the node is empty.
        const parsedNode = parser.parseFromString(node.outerHTML, 'text/html').documentElement;
        parsedNode.querySelectorAll('base,link,meta,script,style,template,title,desc').forEach((el) => {
            el.remove();
        });

        const visibleText = parsedNode.innerText.trim().toLocaleLowerCase().replace(/:$/, '');
        const mediaAndFormContent = parsedNode.querySelector(mediaAndFormSelectors);
        const frameElements = [...parsedNode.querySelectorAll('iframe')];
        // query original node instead of parsedNode for img elements since heuristic relies
        // on size of image elements
        const imageElements = [...node.querySelectorAll('img,svg')];
        // about:blank iframes don't count as content, return true if:
        // - node doesn't contain any iframes
        // - node contains iframes, all of which are hidden or have src='about:blank'
        const noFramesWithContent = frameElements.every((frame) => {
            return (frame.hidden || frame.src === 'about:blank')
        });
        // ad containers often contain tracking pixels and other small images (eg adchoices logo).
        // these should be treated as empty and hidden, but real images should not.
        const visibleImages = imageElements.some((image) => {
            return (image.getBoundingClientRect().width > 20 || image.getBoundingClientRect().height > 20)
        });

        if ((visibleText === '' || adLabelStrings.includes(visibleText)) &&
            mediaAndFormContent === null && noFramesWithContent && !visibleImages) {
            return true
        }
        return false
    }

    /**
     * Apply relevant hiding rules to page at set intervals
     * @param {Object[]} rules
     * @param {string} rules[].selector
     * @param {string} rules[].type
     */
    function applyRules (rules) {
        const hideTimeouts = [0, 100, 200, 300, 400, 500, 1000, 1500, 2000, 2500, 3000];
        const unhideTimeouts = [750, 1500, 2250, 3000];
        const timeoutRules = extractTimeoutRules(rules);

        // several passes are made to hide & unhide elements. this is necessary because we're not using
        // a mutation observer but we want to hide/unhide elements as soon as possible, and ads
        // frequently take from several hundred milliseconds to several seconds to load
        // check at 0ms, 100ms, 200ms, 300ms, 400ms, 500ms, 1000ms, 1500ms, 2000ms, 2500ms, 3000ms
        hideTimeouts.forEach((timeout) => {
            setTimeout(() => {
                hideAdNodes(timeoutRules);
            }, timeout);
        });

        // check previously hidden ad elements for contents, unhide if content has loaded after hiding.
        // we do this in order to display non-tracking ads that aren't blocked at the request level
        // check at 750ms, 1500ms, 2250ms, 3000ms
        unhideTimeouts.forEach((timeout) => {
            setTimeout(() => {
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                unhideLoadedAds();
            }, timeout);
        });

        // clear appliedRules and hiddenElements caches once all checks have run
        setTimeout(() => {
            appliedRules = new Set();
            hiddenElements = new WeakMap();
        }, 3100);
    }

    /**
     * Separate strict hide rules to inject as style tag if enabled
     * @param {Object[]} rules
     * @param {string} rules[].selector
     * @param {string} rules[].type
     */
    function extractTimeoutRules (rules) {
        if (!shouldInjectStyleTag) {
            return rules
        }

        const strictHideRules = [];
        const timeoutRules = [];

        rules.forEach((rule, i) => {
            if (rule.type === 'hide') {
                strictHideRules.push(rule);
            } else {
                timeoutRules.push(rule);
            }
        });

        injectStyleTag(strictHideRules);
        return timeoutRules
    }

    /**
     * Create styletag for strict hide rules and append it to the document
     * @param {Object[]} rules
     * @param {string} rules[].selector
     * @param {string} rules[].type
     */
    function injectStyleTag (rules) {
        let styleTagContents = '';

        rules.forEach((rule, i) => {
            if (i !== rules.length - 1) {
                styleTagContents = styleTagContents.concat(rule.selector, ',');
            } else {
                styleTagContents = styleTagContents.concat(rule.selector);
            }
        });

        styleTagContents = styleTagContents.concat('{display:none!important;min-height:0!important;height:0!important;}');
        injectGlobalStyles(styleTagContents);
    }

    /**
     * Apply list of active element hiding rules to page
     * @param {Object[]} rules
     * @param {string} rules[].selector
     * @param {string} rules[].type
     */
    function hideAdNodes (rules) {
        const document = globalThis.document;

        rules.forEach((rule) => {
            const matchingElementArray = [...document.querySelectorAll(rule.selector)];
            matchingElementArray.forEach((element) => {
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                collapseDomNode(element, rule);
            });
        });
    }

    /**
     * Iterate over previously hidden elements, unhiding if content has loaded into them
     */
    function unhideLoadedAds () {
        const document = globalThis.document;

        appliedRules.forEach((rule) => {
            const matchingElementArray = [...document.querySelectorAll(rule.selector)];
            matchingElementArray.forEach((element) => {
                expandNonEmptyDomNode(element, rule);
            });
        });
    }

    function init$d (args) {
        if (isBeingFramed()) {
            return
        }

        const featureName = 'elementHiding';
        const domain = args.site.domain;
        const domainRules = getFeatureSetting(featureName, args, 'domains');
        const globalRules = getFeatureSetting(featureName, args, 'rules');
        const styleTagExceptions = getFeatureSetting(featureName, args, 'styleTagExceptions');
        adLabelStrings = getFeatureSetting(featureName, args, 'adLabelStrings');
        shouldInjectStyleTag = getFeatureSetting(featureName, args, 'useStrictHideStyleTag');
        mediaAndFormSelectors = getFeatureSetting(featureName, args, 'mediaAndFormSelectors') || mediaAndFormSelectors;

        // determine whether strict hide rules should be injected as a style tag
        if (shouldInjectStyleTag) {
            shouldInjectStyleTag = !styleTagExceptions.some((exception) => {
                return matchHostname(domain, exception.domain)
            });
        }

        // collect all matching rules for domain
        const activeDomainRules = domainRules.filter((rule) => {
            return matchHostname(domain, rule.domain)
        }).flatMap((item) => item.rules);

        const overrideRules = activeDomainRules.filter((rule) => {
            return rule.type === 'override'
        });

        let activeRules = activeDomainRules.concat(globalRules);

        // remove overrides and rules that match overrides from array of rules to be applied to page
        overrideRules.forEach((override) => {
            activeRules = activeRules.filter((rule) => {
                return rule.selector !== override.selector
            });
        });

        // now have the final list of rules to apply, so we apply them when document is loaded
        if (document.readyState === 'loading') {
            window.addEventListener('DOMContentLoaded', (event) => {
                applyRules(activeRules);
            });
        } else {
            applyRules(activeRules);
        }
        // single page applications don't have a DOMContentLoaded event on navigations, so
        // we use proxy/reflect on history.pushState to call applyRules on page navigations
        const historyMethodProxy = new DDGProxy(featureName, History.prototype, 'pushState', {
            apply (target, thisArg, args) {
                applyRules(activeRules);
                return DDGReflect.apply(target, thisArg, args)
            }
        });
        historyMethodProxy.overload();
        // listen for popstate events in order to run on back/forward navigations
        window.addEventListener('popstate', (event) => {
            applyRules(activeRules);
        });
    }

    var elementHiding = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$d
    });

    // @ts-nocheck
        const sjcl = (() => {
    /*jslint indent: 2, bitwise: false, nomen: false, plusplus: false, white: false, regexp: false */
    /*global document, window, escape, unescape, module, require, Uint32Array */

    /**
     * The Stanford Javascript Crypto Library, top-level namespace.
     * @namespace
     */
    var sjcl = {
      /**
       * Symmetric ciphers.
       * @namespace
       */
      cipher: {},

      /**
       * Hash functions.  Right now only SHA256 is implemented.
       * @namespace
       */
      hash: {},

      /**
       * Key exchange functions.  Right now only SRP is implemented.
       * @namespace
       */
      keyexchange: {},
      
      /**
       * Cipher modes of operation.
       * @namespace
       */
      mode: {},

      /**
       * Miscellaneous.  HMAC and PBKDF2.
       * @namespace
       */
      misc: {},
      
      /**
       * Bit array encoders and decoders.
       * @namespace
       *
       * @description
       * The members of this namespace are functions which translate between
       * SJCL's bitArrays and other objects (usually strings).  Because it
       * isn't always clear which direction is encoding and which is decoding,
       * the method names are "fromBits" and "toBits".
       */
      codec: {},
      
      /**
       * Exceptions.
       * @namespace
       */
      exception: {
        /**
         * Ciphertext is corrupt.
         * @constructor
         */
        corrupt: function(message) {
          this.toString = function() { return "CORRUPT: "+this.message; };
          this.message = message;
        },
        
        /**
         * Invalid parameter.
         * @constructor
         */
        invalid: function(message) {
          this.toString = function() { return "INVALID: "+this.message; };
          this.message = message;
        },
        
        /**
         * Bug or missing feature in SJCL.
         * @constructor
         */
        bug: function(message) {
          this.toString = function() { return "BUG: "+this.message; };
          this.message = message;
        },

        /**
         * Something isn't ready.
         * @constructor
         */
        notReady: function(message) {
          this.toString = function() { return "NOT READY: "+this.message; };
          this.message = message;
        }
      }
    };
    /** @fileOverview Arrays of bits, encoded as arrays of Numbers.
     *
     * @author Emily Stark
     * @author Mike Hamburg
     * @author Dan Boneh
     */

    /**
     * Arrays of bits, encoded as arrays of Numbers.
     * @namespace
     * @description
     * <p>
     * These objects are the currency accepted by SJCL's crypto functions.
     * </p>
     *
     * <p>
     * Most of our crypto primitives operate on arrays of 4-byte words internally,
     * but many of them can take arguments that are not a multiple of 4 bytes.
     * This library encodes arrays of bits (whose size need not be a multiple of 8
     * bits) as arrays of 32-bit words.  The bits are packed, big-endian, into an
     * array of words, 32 bits at a time.  Since the words are double-precision
     * floating point numbers, they fit some extra data.  We use this (in a private,
     * possibly-changing manner) to encode the number of bits actually  present
     * in the last word of the array.
     * </p>
     *
     * <p>
     * Because bitwise ops clear this out-of-band data, these arrays can be passed
     * to ciphers like AES which want arrays of words.
     * </p>
     */
    sjcl.bitArray = {
      /**
       * Array slices in units of bits.
       * @param {bitArray} a The array to slice.
       * @param {Number} bstart The offset to the start of the slice, in bits.
       * @param {Number} bend The offset to the end of the slice, in bits.  If this is undefined,
       * slice until the end of the array.
       * @return {bitArray} The requested slice.
       */
      bitSlice: function (a, bstart, bend) {
        a = sjcl.bitArray._shiftRight(a.slice(bstart/32), 32 - (bstart & 31)).slice(1);
        return (bend === undefined) ? a : sjcl.bitArray.clamp(a, bend-bstart);
      },

      /**
       * Extract a number packed into a bit array.
       * @param {bitArray} a The array to slice.
       * @param {Number} bstart The offset to the start of the slice, in bits.
       * @param {Number} blength The length of the number to extract.
       * @return {Number} The requested slice.
       */
      extract: function(a, bstart, blength) {
        // FIXME: this Math.floor is not necessary at all, but for some reason
        // seems to suppress a bug in the Chromium JIT.
        var x, sh = Math.floor((-bstart-blength) & 31);
        if ((bstart + blength - 1 ^ bstart) & -32) {
          // it crosses a boundary
          x = (a[bstart/32|0] << (32 - sh)) ^ (a[bstart/32+1|0] >>> sh);
        } else {
          // within a single word
          x = a[bstart/32|0] >>> sh;
        }
        return x & ((1<<blength) - 1);
      },

      /**
       * Concatenate two bit arrays.
       * @param {bitArray} a1 The first array.
       * @param {bitArray} a2 The second array.
       * @return {bitArray} The concatenation of a1 and a2.
       */
      concat: function (a1, a2) {
        if (a1.length === 0 || a2.length === 0) {
          return a1.concat(a2);
        }
        
        var last = a1[a1.length-1], shift = sjcl.bitArray.getPartial(last);
        if (shift === 32) {
          return a1.concat(a2);
        } else {
          return sjcl.bitArray._shiftRight(a2, shift, last|0, a1.slice(0,a1.length-1));
        }
      },

      /**
       * Find the length of an array of bits.
       * @param {bitArray} a The array.
       * @return {Number} The length of a, in bits.
       */
      bitLength: function (a) {
        var l = a.length, x;
        if (l === 0) { return 0; }
        x = a[l - 1];
        return (l-1) * 32 + sjcl.bitArray.getPartial(x);
      },

      /**
       * Truncate an array.
       * @param {bitArray} a The array.
       * @param {Number} len The length to truncate to, in bits.
       * @return {bitArray} A new array, truncated to len bits.
       */
      clamp: function (a, len) {
        if (a.length * 32 < len) { return a; }
        a = a.slice(0, Math.ceil(len / 32));
        var l = a.length;
        len = len & 31;
        if (l > 0 && len) {
          a[l-1] = sjcl.bitArray.partial(len, a[l-1] & 0x80000000 >> (len-1), 1);
        }
        return a;
      },

      /**
       * Make a partial word for a bit array.
       * @param {Number} len The number of bits in the word.
       * @param {Number} x The bits.
       * @param {Number} [_end=0] Pass 1 if x has already been shifted to the high side.
       * @return {Number} The partial word.
       */
      partial: function (len, x, _end) {
        if (len === 32) { return x; }
        return (_end ? x|0 : x << (32-len)) + len * 0x10000000000;
      },

      /**
       * Get the number of bits used by a partial word.
       * @param {Number} x The partial word.
       * @return {Number} The number of bits used by the partial word.
       */
      getPartial: function (x) {
        return Math.round(x/0x10000000000) || 32;
      },

      /**
       * Compare two arrays for equality in a predictable amount of time.
       * @param {bitArray} a The first array.
       * @param {bitArray} b The second array.
       * @return {boolean} true if a == b; false otherwise.
       */
      equal: function (a, b) {
        if (sjcl.bitArray.bitLength(a) !== sjcl.bitArray.bitLength(b)) {
          return false;
        }
        var x = 0, i;
        for (i=0; i<a.length; i++) {
          x |= a[i]^b[i];
        }
        return (x === 0);
      },

      /** Shift an array right.
       * @param {bitArray} a The array to shift.
       * @param {Number} shift The number of bits to shift.
       * @param {Number} [carry=0] A byte to carry in
       * @param {bitArray} [out=[]] An array to prepend to the output.
       * @private
       */
      _shiftRight: function (a, shift, carry, out) {
        var i, last2=0, shift2;
        if (out === undefined) { out = []; }
        
        for (; shift >= 32; shift -= 32) {
          out.push(carry);
          carry = 0;
        }
        if (shift === 0) {
          return out.concat(a);
        }
        
        for (i=0; i<a.length; i++) {
          out.push(carry | a[i]>>>shift);
          carry = a[i] << (32-shift);
        }
        last2 = a.length ? a[a.length-1] : 0;
        shift2 = sjcl.bitArray.getPartial(last2);
        out.push(sjcl.bitArray.partial(shift+shift2 & 31, (shift + shift2 > 32) ? carry : out.pop(),1));
        return out;
      },
      
      /** xor a block of 4 words together.
       * @private
       */
      _xor4: function(x,y) {
        return [x[0]^y[0],x[1]^y[1],x[2]^y[2],x[3]^y[3]];
      },

      /** byteswap a word array inplace.
       * (does not handle partial words)
       * @param {sjcl.bitArray} a word array
       * @return {sjcl.bitArray} byteswapped array
       */
      byteswapM: function(a) {
        var i, v, m = 0xff00;
        for (i = 0; i < a.length; ++i) {
          v = a[i];
          a[i] = (v >>> 24) | ((v >>> 8) & m) | ((v & m) << 8) | (v << 24);
        }
        return a;
      }
    };
    /** @fileOverview Bit array codec implementations.
     *
     * @author Emily Stark
     * @author Mike Hamburg
     * @author Dan Boneh
     */

    /**
     * UTF-8 strings
     * @namespace
     */
    sjcl.codec.utf8String = {
      /** Convert from a bitArray to a UTF-8 string. */
      fromBits: function (arr) {
        var out = "", bl = sjcl.bitArray.bitLength(arr), i, tmp;
        for (i=0; i<bl/8; i++) {
          if ((i&3) === 0) {
            tmp = arr[i/4];
          }
          out += String.fromCharCode(tmp >>> 8 >>> 8 >>> 8);
          tmp <<= 8;
        }
        return decodeURIComponent(escape(out));
      },

      /** Convert from a UTF-8 string to a bitArray. */
      toBits: function (str) {
        str = unescape(encodeURIComponent(str));
        var out = [], i, tmp=0;
        for (i=0; i<str.length; i++) {
          tmp = tmp << 8 | str.charCodeAt(i);
          if ((i&3) === 3) {
            out.push(tmp);
            tmp = 0;
          }
        }
        if (i&3) {
          out.push(sjcl.bitArray.partial(8*(i&3), tmp));
        }
        return out;
      }
    };
    /** @fileOverview Bit array codec implementations.
     *
     * @author Emily Stark
     * @author Mike Hamburg
     * @author Dan Boneh
     */

    /**
     * Hexadecimal
     * @namespace
     */
    sjcl.codec.hex = {
      /** Convert from a bitArray to a hex string. */
      fromBits: function (arr) {
        var out = "", i;
        for (i=0; i<arr.length; i++) {
          out += ((arr[i]|0)+0xF00000000000).toString(16).substr(4);
        }
        return out.substr(0, sjcl.bitArray.bitLength(arr)/4);//.replace(/(.{8})/g, "$1 ");
      },
      /** Convert from a hex string to a bitArray. */
      toBits: function (str) {
        var i, out=[], len;
        str = str.replace(/\s|0x/g, "");
        len = str.length;
        str = str + "00000000";
        for (i=0; i<str.length; i+=8) {
          out.push(parseInt(str.substr(i,8),16)^0);
        }
        return sjcl.bitArray.clamp(out, len*4);
      }
    };

    /** @fileOverview Javascript SHA-256 implementation.
     *
     * An older version of this implementation is available in the public
     * domain, but this one is (c) Emily Stark, Mike Hamburg, Dan Boneh,
     * Stanford University 2008-2010 and BSD-licensed for liability
     * reasons.
     *
     * Special thanks to Aldo Cortesi for pointing out several bugs in
     * this code.
     *
     * @author Emily Stark
     * @author Mike Hamburg
     * @author Dan Boneh
     */

    /**
     * Context for a SHA-256 operation in progress.
     * @constructor
     */
    sjcl.hash.sha256 = function (hash) {
      if (!this._key[0]) { this._precompute(); }
      if (hash) {
        this._h = hash._h.slice(0);
        this._buffer = hash._buffer.slice(0);
        this._length = hash._length;
      } else {
        this.reset();
      }
    };

    /**
     * Hash a string or an array of words.
     * @static
     * @param {bitArray|String} data the data to hash.
     * @return {bitArray} The hash value, an array of 16 big-endian words.
     */
    sjcl.hash.sha256.hash = function (data) {
      return (new sjcl.hash.sha256()).update(data).finalize();
    };

    sjcl.hash.sha256.prototype = {
      /**
       * The hash's block size, in bits.
       * @constant
       */
      blockSize: 512,
       
      /**
       * Reset the hash state.
       * @return this
       */
      reset:function () {
        this._h = this._init.slice(0);
        this._buffer = [];
        this._length = 0;
        return this;
      },
      
      /**
       * Input several words to the hash.
       * @param {bitArray|String} data the data to hash.
       * @return this
       */
      update: function (data) {
        if (typeof data === "string") {
          data = sjcl.codec.utf8String.toBits(data);
        }
        var i, b = this._buffer = sjcl.bitArray.concat(this._buffer, data),
            ol = this._length,
            nl = this._length = ol + sjcl.bitArray.bitLength(data);
        if (nl > 9007199254740991){
          throw new sjcl.exception.invalid("Cannot hash more than 2^53 - 1 bits");
        }

        if (typeof Uint32Array !== 'undefined') {
    	var c = new Uint32Array(b);
        	var j = 0;
        	for (i = 512+ol - ((512+ol) & 511); i <= nl; i+= 512) {
          	    this._block(c.subarray(16 * j, 16 * (j+1)));
          	    j += 1;
        	}
        	b.splice(0, 16 * j);
        } else {
    	for (i = 512+ol - ((512+ol) & 511); i <= nl; i+= 512) {
          	    this._block(b.splice(0,16));
          	}
        }
        return this;
      },
      
      /**
       * Complete hashing and output the hash value.
       * @return {bitArray} The hash value, an array of 8 big-endian words.
       */
      finalize:function () {
        var i, b = this._buffer, h = this._h;

        // Round out and push the buffer
        b = sjcl.bitArray.concat(b, [sjcl.bitArray.partial(1,1)]);
        
        // Round out the buffer to a multiple of 16 words, less the 2 length words.
        for (i = b.length + 2; i & 15; i++) {
          b.push(0);
        }
        
        // append the length
        b.push(Math.floor(this._length / 0x100000000));
        b.push(this._length | 0);

        while (b.length) {
          this._block(b.splice(0,16));
        }

        this.reset();
        return h;
      },

      /**
       * The SHA-256 initialization vector, to be precomputed.
       * @private
       */
      _init:[],
      /*
      _init:[0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19],
      */
      
      /**
       * The SHA-256 hash key, to be precomputed.
       * @private
       */
      _key:[],
      /*
      _key:
        [0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
         0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
         0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
         0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
         0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
         0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
         0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
         0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2],
      */


      /**
       * Function to precompute _init and _key.
       * @private
       */
      _precompute: function () {
        var i = 0, prime = 2, factor, isPrime;

        function frac(x) { return (x-Math.floor(x)) * 0x100000000 | 0; }

        for (; i<64; prime++) {
          isPrime = true;
          for (factor=2; factor*factor <= prime; factor++) {
            if (prime % factor === 0) {
              isPrime = false;
              break;
            }
          }
          if (isPrime) {
            if (i<8) {
              this._init[i] = frac(Math.pow(prime, 1/2));
            }
            this._key[i] = frac(Math.pow(prime, 1/3));
            i++;
          }
        }
      },
      
      /**
       * Perform one cycle of SHA-256.
       * @param {Uint32Array|bitArray} w one block of words.
       * @private
       */
      _block:function (w) {  
        var i, tmp, a, b,
          h = this._h,
          k = this._key,
          h0 = h[0], h1 = h[1], h2 = h[2], h3 = h[3],
          h4 = h[4], h5 = h[5], h6 = h[6], h7 = h[7];

        /* Rationale for placement of |0 :
         * If a value can overflow is original 32 bits by a factor of more than a few
         * million (2^23 ish), there is a possibility that it might overflow the
         * 53-bit mantissa and lose precision.
         *
         * To avoid this, we clamp back to 32 bits by |'ing with 0 on any value that
         * propagates around the loop, and on the hash state h[].  I don't believe
         * that the clamps on h4 and on h0 are strictly necessary, but it's close
         * (for h4 anyway), and better safe than sorry.
         *
         * The clamps on h[] are necessary for the output to be correct even in the
         * common case and for short inputs.
         */
        for (i=0; i<64; i++) {
          // load up the input word for this round
          if (i<16) {
            tmp = w[i];
          } else {
            a   = w[(i+1 ) & 15];
            b   = w[(i+14) & 15];
            tmp = w[i&15] = ((a>>>7  ^ a>>>18 ^ a>>>3  ^ a<<25 ^ a<<14) + 
                             (b>>>17 ^ b>>>19 ^ b>>>10 ^ b<<15 ^ b<<13) +
                             w[i&15] + w[(i+9) & 15]) | 0;
          }
          
          tmp = (tmp + h7 + (h4>>>6 ^ h4>>>11 ^ h4>>>25 ^ h4<<26 ^ h4<<21 ^ h4<<7) +  (h6 ^ h4&(h5^h6)) + k[i]); // | 0;
          
          // shift register
          h7 = h6; h6 = h5; h5 = h4;
          h4 = h3 + tmp | 0;
          h3 = h2; h2 = h1; h1 = h0;

          h0 = (tmp +  ((h1&h2) ^ (h3&(h1^h2))) + (h1>>>2 ^ h1>>>13 ^ h1>>>22 ^ h1<<30 ^ h1<<19 ^ h1<<10)) | 0;
        }

        h[0] = h[0]+h0 | 0;
        h[1] = h[1]+h1 | 0;
        h[2] = h[2]+h2 | 0;
        h[3] = h[3]+h3 | 0;
        h[4] = h[4]+h4 | 0;
        h[5] = h[5]+h5 | 0;
        h[6] = h[6]+h6 | 0;
        h[7] = h[7]+h7 | 0;
      }
    };


    /** @fileOverview HMAC implementation.
     *
     * @author Emily Stark
     * @author Mike Hamburg
     * @author Dan Boneh
     */

    /** HMAC with the specified hash function.
     * @constructor
     * @param {bitArray} key the key for HMAC.
     * @param {Object} [Hash=sjcl.hash.sha256] The hash function to use.
     */
    sjcl.misc.hmac = function (key, Hash) {
      this._hash = Hash = Hash || sjcl.hash.sha256;
      var exKey = [[],[]], i,
          bs = Hash.prototype.blockSize / 32;
      this._baseHash = [new Hash(), new Hash()];

      if (key.length > bs) {
        key = Hash.hash(key);
      }
      
      for (i=0; i<bs; i++) {
        exKey[0][i] = key[i]^0x36363636;
        exKey[1][i] = key[i]^0x5C5C5C5C;
      }
      
      this._baseHash[0].update(exKey[0]);
      this._baseHash[1].update(exKey[1]);
      this._resultHash = new Hash(this._baseHash[0]);
    };

    /** HMAC with the specified hash function.  Also called encrypt since it's a prf.
     * @param {bitArray|String} data The data to mac.
     */
    sjcl.misc.hmac.prototype.encrypt = sjcl.misc.hmac.prototype.mac = function (data) {
      if (!this._updated) {
        this.update(data);
        return this.digest(data);
      } else {
        throw new sjcl.exception.invalid("encrypt on already updated hmac called!");
      }
    };

    sjcl.misc.hmac.prototype.reset = function () {
      this._resultHash = new this._hash(this._baseHash[0]);
      this._updated = false;
    };

    sjcl.misc.hmac.prototype.update = function (data) {
      this._updated = true;
      this._resultHash.update(data);
    };

    sjcl.misc.hmac.prototype.digest = function () {
      var w = this._resultHash.finalize(), result = new (this._hash)(this._baseHash[1]).update(w).finalize();

      this.reset();

      return result;
    };

        return sjcl;
      })();

    function getDataKeySync (sessionKey, domainKey, inputData) {
        // eslint-disable-next-line new-cap
        const hmac = new sjcl.misc.hmac(sjcl.codec.utf8String.toBits(sessionKey + domainKey), sjcl.hash.sha256);
        return sjcl.codec.hex.fromBits(hmac.encrypt(inputData))
    }

    function init$c (args) {
        const { sessionKey, site } = args;
        const domainKey = site.domain;
        const featureName = 'fingerprinting-audio';

        // In place modify array data to remove fingerprinting
        function transformArrayData (channelData, domainKey, sessionKey, thisArg) {
            let { audioKey } = getCachedResponse(thisArg, args);
            if (!audioKey) {
                let cdSum = 0;
                for (const k in channelData) {
                    cdSum += channelData[k];
                }
                // If the buffer is blank, skip adding data
                if (cdSum === 0) {
                    return
                }
                audioKey = getDataKeySync(sessionKey, domainKey, cdSum);
                setCache(thisArg, args, audioKey);
            }
            iterateDataKey(audioKey, (item, byte) => {
                const itemAudioIndex = item % channelData.length;

                let factor = byte * 0.0000001;
                if (byte ^ 0x1) {
                    factor = 0 - factor;
                }
                channelData[itemAudioIndex] = channelData[itemAudioIndex] + factor;
            });
        }

        const copyFromChannelProxy = new DDGProxy(featureName, AudioBuffer.prototype, 'copyFromChannel', {
            apply (target, thisArg, args) {
                const [source, channelNumber, startInChannel] = args;
                // This is implemented in a different way to canvas purely because calling the function copied the original value, which is not ideal
                if (// If channelNumber is longer than arrayBuffer number of channels then call the default method to throw
                    channelNumber > thisArg.numberOfChannels ||
                    // If startInChannel is longer than the arrayBuffer length then call the default method to throw
                    startInChannel > thisArg.length) {
                    // The normal return value
                    return DDGReflect.apply(target, thisArg, args)
                }
                try {
                    // Call the protected getChannelData we implement, slice from the startInChannel value and assign to the source array
                    thisArg.getChannelData(channelNumber).slice(startInChannel).forEach((val, index) => {
                        source[index] = val;
                    });
                } catch {
                    return DDGReflect.apply(target, thisArg, args)
                }
            }
        });
        copyFromChannelProxy.overload();

        const cacheExpiry = 60;
        const cacheData = new WeakMap();
        function getCachedResponse (thisArg, args) {
            const data = cacheData.get(thisArg);
            const timeNow = Date.now();
            if (data &&
                data.args === JSON.stringify(args) &&
                data.expires > timeNow) {
                data.expires = timeNow + cacheExpiry;
                cacheData.set(thisArg, data);
                return data
            }
            return { audioKey: null }
        }

        function setCache (thisArg, args, audioKey) {
            cacheData.set(thisArg, { args: JSON.stringify(args), expires: Date.now() + cacheExpiry, audioKey });
        }

        const getChannelDataProxy = new DDGProxy(featureName, AudioBuffer.prototype, 'getChannelData', {
            apply (target, thisArg, args) {
                // The normal return value
                const channelData = DDGReflect.apply(target, thisArg, args);
                // Anything we do here should be caught and ignored silently
                try {
                    // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                    transformArrayData(channelData, domainKey, sessionKey, thisArg, args);
                } catch {
                }
                return channelData
            }
        });
        getChannelDataProxy.overload();

        const audioMethods = ['getByteTimeDomainData', 'getFloatTimeDomainData', 'getByteFrequencyData', 'getFloatFrequencyData'];
        for (const methodName of audioMethods) {
            const proxy = new DDGProxy(featureName, AnalyserNode.prototype, methodName, {
                apply (target, thisArg, args) {
                    DDGReflect.apply(target, thisArg, args);
                    // Anything we do here should be caught and ignored silently
                    try {
                        // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                        transformArrayData(args[0], domainKey, sessionKey, thisArg, args);
                    } catch {
                    }
                }
            });
            proxy.overload();
        }
    }

    var fingerprintingAudio = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$c
    });

    /**
     * Overwrites the Battery API if present in the browser.
     * It will return the values defined in the getBattery function to the client,
     * as well as prevent any script from listening to events.
     */
    function init$b (args) {
        // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
        if (globalThis.navigator.getBattery) {
            const BatteryManager = globalThis.BatteryManager;

            const spoofedValues = {
                charging: true,
                chargingTime: 0,
                dischargingTime: Infinity,
                level: 1
            };
            const eventProperties = ['onchargingchange', 'onchargingtimechange', 'ondischargingtimechange', 'onlevelchange'];

            for (const [prop, val] of Object.entries(spoofedValues)) {
                try {
                    defineProperty(BatteryManager.prototype, prop, { get: () => val });
                } catch (e) { }
            }
            for (const eventProp of eventProperties) {
                try {
                    defineProperty(BatteryManager.prototype, eventProp, { get: () => null });
                } catch (e) { }
            }
        }
    }

    var fingerprintingBattery = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$b
    });

    var commonjsGlobal = typeof globalThis !== 'undefined' ? globalThis : typeof window !== 'undefined' ? window : typeof global !== 'undefined' ? global : typeof self !== 'undefined' ? self : {};

    var aleaExports = {};
    var alea$1 = {
      get exports(){ return aleaExports; },
      set exports(v){ aleaExports = v; },
    };

    (function (module) {
    	// A port of an algorithm by Johannes Baagøe <baagoe@baagoe.com>, 2010
    	// http://baagoe.com/en/RandomMusings/javascript/
    	// https://github.com/nquinlan/better-random-numbers-for-javascript-mirror
    	// Original work is under MIT license -

    	// Copyright (C) 2010 by Johannes Baagøe <baagoe@baagoe.org>
    	//
    	// Permission is hereby granted, free of charge, to any person obtaining a copy
    	// of this software and associated documentation files (the "Software"), to deal
    	// in the Software without restriction, including without limitation the rights
    	// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    	// copies of the Software, and to permit persons to whom the Software is
    	// furnished to do so, subject to the following conditions:
    	//
    	// The above copyright notice and this permission notice shall be included in
    	// all copies or substantial portions of the Software.
    	//
    	// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    	// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    	// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    	// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    	// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    	// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
    	// THE SOFTWARE.



    	(function(global, module, define) {

    	function Alea(seed) {
    	  var me = this, mash = Mash();

    	  me.next = function() {
    	    var t = 2091639 * me.s0 + me.c * 2.3283064365386963e-10; // 2^-32
    	    me.s0 = me.s1;
    	    me.s1 = me.s2;
    	    return me.s2 = t - (me.c = t | 0);
    	  };

    	  // Apply the seeding algorithm from Baagoe.
    	  me.c = 1;
    	  me.s0 = mash(' ');
    	  me.s1 = mash(' ');
    	  me.s2 = mash(' ');
    	  me.s0 -= mash(seed);
    	  if (me.s0 < 0) { me.s0 += 1; }
    	  me.s1 -= mash(seed);
    	  if (me.s1 < 0) { me.s1 += 1; }
    	  me.s2 -= mash(seed);
    	  if (me.s2 < 0) { me.s2 += 1; }
    	  mash = null;
    	}

    	function copy(f, t) {
    	  t.c = f.c;
    	  t.s0 = f.s0;
    	  t.s1 = f.s1;
    	  t.s2 = f.s2;
    	  return t;
    	}

    	function impl(seed, opts) {
    	  var xg = new Alea(seed),
    	      state = opts && opts.state,
    	      prng = xg.next;
    	  prng.int32 = function() { return (xg.next() * 0x100000000) | 0; };
    	  prng.double = function() {
    	    return prng() + (prng() * 0x200000 | 0) * 1.1102230246251565e-16; // 2^-53
    	  };
    	  prng.quick = prng;
    	  if (state) {
    	    if (typeof(state) == 'object') copy(state, xg);
    	    prng.state = function() { return copy(xg, {}); };
    	  }
    	  return prng;
    	}

    	function Mash() {
    	  var n = 0xefc8249d;

    	  var mash = function(data) {
    	    data = String(data);
    	    for (var i = 0; i < data.length; i++) {
    	      n += data.charCodeAt(i);
    	      var h = 0.02519603282416938 * n;
    	      n = h >>> 0;
    	      h -= n;
    	      h *= n;
    	      n = h >>> 0;
    	      h -= n;
    	      n += h * 0x100000000; // 2^32
    	    }
    	    return (n >>> 0) * 2.3283064365386963e-10; // 2^-32
    	  };

    	  return mash;
    	}


    	if (module && module.exports) {
    	  module.exports = impl;
    	} else if (define && define.amd) {
    	  define(function() { return impl; });
    	} else {
    	  this.alea = impl;
    	}

    	})(
    	  commonjsGlobal,
    	  module,    // present in node.js
    	  (typeof undefined) == 'function'    // present with an AMD loader
    	);
    } (alea$1));

    var xor128Exports = {};
    var xor128$1 = {
      get exports(){ return xor128Exports; },
      set exports(v){ xor128Exports = v; },
    };

    (function (module) {
    	// A Javascript implementaion of the "xor128" prng algorithm by
    	// George Marsaglia.  See http://www.jstatsoft.org/v08/i14/paper

    	(function(global, module, define) {

    	function XorGen(seed) {
    	  var me = this, strseed = '';

    	  me.x = 0;
    	  me.y = 0;
    	  me.z = 0;
    	  me.w = 0;

    	  // Set up generator function.
    	  me.next = function() {
    	    var t = me.x ^ (me.x << 11);
    	    me.x = me.y;
    	    me.y = me.z;
    	    me.z = me.w;
    	    return me.w ^= (me.w >>> 19) ^ t ^ (t >>> 8);
    	  };

    	  if (seed === (seed | 0)) {
    	    // Integer seed.
    	    me.x = seed;
    	  } else {
    	    // String seed.
    	    strseed += seed;
    	  }

    	  // Mix in string seed, then discard an initial batch of 64 values.
    	  for (var k = 0; k < strseed.length + 64; k++) {
    	    me.x ^= strseed.charCodeAt(k) | 0;
    	    me.next();
    	  }
    	}

    	function copy(f, t) {
    	  t.x = f.x;
    	  t.y = f.y;
    	  t.z = f.z;
    	  t.w = f.w;
    	  return t;
    	}

    	function impl(seed, opts) {
    	  var xg = new XorGen(seed),
    	      state = opts && opts.state,
    	      prng = function() { return (xg.next() >>> 0) / 0x100000000; };
    	  prng.double = function() {
    	    do {
    	      var top = xg.next() >>> 11,
    	          bot = (xg.next() >>> 0) / 0x100000000,
    	          result = (top + bot) / (1 << 21);
    	    } while (result === 0);
    	    return result;
    	  };
    	  prng.int32 = xg.next;
    	  prng.quick = prng;
    	  if (state) {
    	    if (typeof(state) == 'object') copy(state, xg);
    	    prng.state = function() { return copy(xg, {}); };
    	  }
    	  return prng;
    	}

    	if (module && module.exports) {
    	  module.exports = impl;
    	} else if (define && define.amd) {
    	  define(function() { return impl; });
    	} else {
    	  this.xor128 = impl;
    	}

    	})(
    	  commonjsGlobal,
    	  module,    // present in node.js
    	  (typeof undefined) == 'function'    // present with an AMD loader
    	);
    } (xor128$1));

    var xorwowExports = {};
    var xorwow$1 = {
      get exports(){ return xorwowExports; },
      set exports(v){ xorwowExports = v; },
    };

    (function (module) {
    	// A Javascript implementaion of the "xorwow" prng algorithm by
    	// George Marsaglia.  See http://www.jstatsoft.org/v08/i14/paper

    	(function(global, module, define) {

    	function XorGen(seed) {
    	  var me = this, strseed = '';

    	  // Set up generator function.
    	  me.next = function() {
    	    var t = (me.x ^ (me.x >>> 2));
    	    me.x = me.y; me.y = me.z; me.z = me.w; me.w = me.v;
    	    return (me.d = (me.d + 362437 | 0)) +
    	       (me.v = (me.v ^ (me.v << 4)) ^ (t ^ (t << 1))) | 0;
    	  };

    	  me.x = 0;
    	  me.y = 0;
    	  me.z = 0;
    	  me.w = 0;
    	  me.v = 0;

    	  if (seed === (seed | 0)) {
    	    // Integer seed.
    	    me.x = seed;
    	  } else {
    	    // String seed.
    	    strseed += seed;
    	  }

    	  // Mix in string seed, then discard an initial batch of 64 values.
    	  for (var k = 0; k < strseed.length + 64; k++) {
    	    me.x ^= strseed.charCodeAt(k) | 0;
    	    if (k == strseed.length) {
    	      me.d = me.x << 10 ^ me.x >>> 4;
    	    }
    	    me.next();
    	  }
    	}

    	function copy(f, t) {
    	  t.x = f.x;
    	  t.y = f.y;
    	  t.z = f.z;
    	  t.w = f.w;
    	  t.v = f.v;
    	  t.d = f.d;
    	  return t;
    	}

    	function impl(seed, opts) {
    	  var xg = new XorGen(seed),
    	      state = opts && opts.state,
    	      prng = function() { return (xg.next() >>> 0) / 0x100000000; };
    	  prng.double = function() {
    	    do {
    	      var top = xg.next() >>> 11,
    	          bot = (xg.next() >>> 0) / 0x100000000,
    	          result = (top + bot) / (1 << 21);
    	    } while (result === 0);
    	    return result;
    	  };
    	  prng.int32 = xg.next;
    	  prng.quick = prng;
    	  if (state) {
    	    if (typeof(state) == 'object') copy(state, xg);
    	    prng.state = function() { return copy(xg, {}); };
    	  }
    	  return prng;
    	}

    	if (module && module.exports) {
    	  module.exports = impl;
    	} else if (define && define.amd) {
    	  define(function() { return impl; });
    	} else {
    	  this.xorwow = impl;
    	}

    	})(
    	  commonjsGlobal,
    	  module,    // present in node.js
    	  (typeof undefined) == 'function'    // present with an AMD loader
    	);
    } (xorwow$1));

    var xorshift7Exports = {};
    var xorshift7$1 = {
      get exports(){ return xorshift7Exports; },
      set exports(v){ xorshift7Exports = v; },
    };

    (function (module) {
    	// A Javascript implementaion of the "xorshift7" algorithm by
    	// François Panneton and Pierre L'ecuyer:
    	// "On the Xorgshift Random Number Generators"
    	// http://saluc.engr.uconn.edu/refs/crypto/rng/panneton05onthexorshift.pdf

    	(function(global, module, define) {

    	function XorGen(seed) {
    	  var me = this;

    	  // Set up generator function.
    	  me.next = function() {
    	    // Update xor generator.
    	    var X = me.x, i = me.i, t, v;
    	    t = X[i]; t ^= (t >>> 7); v = t ^ (t << 24);
    	    t = X[(i + 1) & 7]; v ^= t ^ (t >>> 10);
    	    t = X[(i + 3) & 7]; v ^= t ^ (t >>> 3);
    	    t = X[(i + 4) & 7]; v ^= t ^ (t << 7);
    	    t = X[(i + 7) & 7]; t = t ^ (t << 13); v ^= t ^ (t << 9);
    	    X[i] = v;
    	    me.i = (i + 1) & 7;
    	    return v;
    	  };

    	  function init(me, seed) {
    	    var j, X = [];

    	    if (seed === (seed | 0)) {
    	      // Seed state array using a 32-bit integer.
    	      X[0] = seed;
    	    } else {
    	      // Seed state using a string.
    	      seed = '' + seed;
    	      for (j = 0; j < seed.length; ++j) {
    	        X[j & 7] = (X[j & 7] << 15) ^
    	            (seed.charCodeAt(j) + X[(j + 1) & 7] << 13);
    	      }
    	    }
    	    // Enforce an array length of 8, not all zeroes.
    	    while (X.length < 8) X.push(0);
    	    for (j = 0; j < 8 && X[j] === 0; ++j);
    	    if (j == 8) X[7] = -1; else X[j];

    	    me.x = X;
    	    me.i = 0;

    	    // Discard an initial 256 values.
    	    for (j = 256; j > 0; --j) {
    	      me.next();
    	    }
    	  }

    	  init(me, seed);
    	}

    	function copy(f, t) {
    	  t.x = f.x.slice();
    	  t.i = f.i;
    	  return t;
    	}

    	function impl(seed, opts) {
    	  if (seed == null) seed = +(new Date);
    	  var xg = new XorGen(seed),
    	      state = opts && opts.state,
    	      prng = function() { return (xg.next() >>> 0) / 0x100000000; };
    	  prng.double = function() {
    	    do {
    	      var top = xg.next() >>> 11,
    	          bot = (xg.next() >>> 0) / 0x100000000,
    	          result = (top + bot) / (1 << 21);
    	    } while (result === 0);
    	    return result;
    	  };
    	  prng.int32 = xg.next;
    	  prng.quick = prng;
    	  if (state) {
    	    if (state.x) copy(state, xg);
    	    prng.state = function() { return copy(xg, {}); };
    	  }
    	  return prng;
    	}

    	if (module && module.exports) {
    	  module.exports = impl;
    	} else if (define && define.amd) {
    	  define(function() { return impl; });
    	} else {
    	  this.xorshift7 = impl;
    	}

    	})(
    	  commonjsGlobal,
    	  module,    // present in node.js
    	  (typeof undefined) == 'function'    // present with an AMD loader
    	);
    } (xorshift7$1));

    var xor4096Exports = {};
    var xor4096$1 = {
      get exports(){ return xor4096Exports; },
      set exports(v){ xor4096Exports = v; },
    };

    (function (module) {
    	// A Javascript implementaion of Richard Brent's Xorgens xor4096 algorithm.
    	//
    	// This fast non-cryptographic random number generator is designed for
    	// use in Monte-Carlo algorithms. It combines a long-period xorshift
    	// generator with a Weyl generator, and it passes all common batteries
    	// of stasticial tests for randomness while consuming only a few nanoseconds
    	// for each prng generated.  For background on the generator, see Brent's
    	// paper: "Some long-period random number generators using shifts and xors."
    	// http://arxiv.org/pdf/1004.3115v1.pdf
    	//
    	// Usage:
    	//
    	// var xor4096 = require('xor4096');
    	// random = xor4096(1);                        // Seed with int32 or string.
    	// assert.equal(random(), 0.1520436450538547); // (0, 1) range, 53 bits.
    	// assert.equal(random.int32(), 1806534897);   // signed int32, 32 bits.
    	//
    	// For nonzero numeric keys, this impelementation provides a sequence
    	// identical to that by Brent's xorgens 3 implementaion in C.  This
    	// implementation also provides for initalizing the generator with
    	// string seeds, or for saving and restoring the state of the generator.
    	//
    	// On Chrome, this prng benchmarks about 2.1 times slower than
    	// Javascript's built-in Math.random().

    	(function(global, module, define) {

    	function XorGen(seed) {
    	  var me = this;

    	  // Set up generator function.
    	  me.next = function() {
    	    var w = me.w,
    	        X = me.X, i = me.i, t, v;
    	    // Update Weyl generator.
    	    me.w = w = (w + 0x61c88647) | 0;
    	    // Update xor generator.
    	    v = X[(i + 34) & 127];
    	    t = X[i = ((i + 1) & 127)];
    	    v ^= v << 13;
    	    t ^= t << 17;
    	    v ^= v >>> 15;
    	    t ^= t >>> 12;
    	    // Update Xor generator array state.
    	    v = X[i] = v ^ t;
    	    me.i = i;
    	    // Result is the combination.
    	    return (v + (w ^ (w >>> 16))) | 0;
    	  };

    	  function init(me, seed) {
    	    var t, v, i, j, w, X = [], limit = 128;
    	    if (seed === (seed | 0)) {
    	      // Numeric seeds initialize v, which is used to generates X.
    	      v = seed;
    	      seed = null;
    	    } else {
    	      // String seeds are mixed into v and X one character at a time.
    	      seed = seed + '\0';
    	      v = 0;
    	      limit = Math.max(limit, seed.length);
    	    }
    	    // Initialize circular array and weyl value.
    	    for (i = 0, j = -32; j < limit; ++j) {
    	      // Put the unicode characters into the array, and shuffle them.
    	      if (seed) v ^= seed.charCodeAt((j + 32) % seed.length);
    	      // After 32 shuffles, take v as the starting w value.
    	      if (j === 0) w = v;
    	      v ^= v << 10;
    	      v ^= v >>> 15;
    	      v ^= v << 4;
    	      v ^= v >>> 13;
    	      if (j >= 0) {
    	        w = (w + 0x61c88647) | 0;     // Weyl.
    	        t = (X[j & 127] ^= (v + w));  // Combine xor and weyl to init array.
    	        i = (0 == t) ? i + 1 : 0;     // Count zeroes.
    	      }
    	    }
    	    // We have detected all zeroes; make the key nonzero.
    	    if (i >= 128) {
    	      X[(seed && seed.length || 0) & 127] = -1;
    	    }
    	    // Run the generator 512 times to further mix the state before using it.
    	    // Factoring this as a function slows the main generator, so it is just
    	    // unrolled here.  The weyl generator is not advanced while warming up.
    	    i = 127;
    	    for (j = 4 * 128; j > 0; --j) {
    	      v = X[(i + 34) & 127];
    	      t = X[i = ((i + 1) & 127)];
    	      v ^= v << 13;
    	      t ^= t << 17;
    	      v ^= v >>> 15;
    	      t ^= t >>> 12;
    	      X[i] = v ^ t;
    	    }
    	    // Storing state as object members is faster than using closure variables.
    	    me.w = w;
    	    me.X = X;
    	    me.i = i;
    	  }

    	  init(me, seed);
    	}

    	function copy(f, t) {
    	  t.i = f.i;
    	  t.w = f.w;
    	  t.X = f.X.slice();
    	  return t;
    	}
    	function impl(seed, opts) {
    	  if (seed == null) seed = +(new Date);
    	  var xg = new XorGen(seed),
    	      state = opts && opts.state,
    	      prng = function() { return (xg.next() >>> 0) / 0x100000000; };
    	  prng.double = function() {
    	    do {
    	      var top = xg.next() >>> 11,
    	          bot = (xg.next() >>> 0) / 0x100000000,
    	          result = (top + bot) / (1 << 21);
    	    } while (result === 0);
    	    return result;
    	  };
    	  prng.int32 = xg.next;
    	  prng.quick = prng;
    	  if (state) {
    	    if (state.X) copy(state, xg);
    	    prng.state = function() { return copy(xg, {}); };
    	  }
    	  return prng;
    	}

    	if (module && module.exports) {
    	  module.exports = impl;
    	} else if (define && define.amd) {
    	  define(function() { return impl; });
    	} else {
    	  this.xor4096 = impl;
    	}

    	})(
    	  commonjsGlobal,                                     // window object or global
    	  module,    // present in node.js
    	  (typeof undefined) == 'function'    // present with an AMD loader
    	);
    } (xor4096$1));

    var tycheiExports = {};
    var tychei$1 = {
      get exports(){ return tycheiExports; },
      set exports(v){ tycheiExports = v; },
    };

    (function (module) {
    	// A Javascript implementaion of the "Tyche-i" prng algorithm by
    	// Samuel Neves and Filipe Araujo.
    	// See https://eden.dei.uc.pt/~sneves/pubs/2011-snfa2.pdf

    	(function(global, module, define) {

    	function XorGen(seed) {
    	  var me = this, strseed = '';

    	  // Set up generator function.
    	  me.next = function() {
    	    var b = me.b, c = me.c, d = me.d, a = me.a;
    	    b = (b << 25) ^ (b >>> 7) ^ c;
    	    c = (c - d) | 0;
    	    d = (d << 24) ^ (d >>> 8) ^ a;
    	    a = (a - b) | 0;
    	    me.b = b = (b << 20) ^ (b >>> 12) ^ c;
    	    me.c = c = (c - d) | 0;
    	    me.d = (d << 16) ^ (c >>> 16) ^ a;
    	    return me.a = (a - b) | 0;
    	  };

    	  /* The following is non-inverted tyche, which has better internal
    	   * bit diffusion, but which is about 25% slower than tyche-i in JS.
    	  me.next = function() {
    	    var a = me.a, b = me.b, c = me.c, d = me.d;
    	    a = (me.a + me.b | 0) >>> 0;
    	    d = me.d ^ a; d = d << 16 ^ d >>> 16;
    	    c = me.c + d | 0;
    	    b = me.b ^ c; b = b << 12 ^ d >>> 20;
    	    me.a = a = a + b | 0;
    	    d = d ^ a; me.d = d = d << 8 ^ d >>> 24;
    	    me.c = c = c + d | 0;
    	    b = b ^ c;
    	    return me.b = (b << 7 ^ b >>> 25);
    	  }
    	  */

    	  me.a = 0;
    	  me.b = 0;
    	  me.c = 2654435769 | 0;
    	  me.d = 1367130551;

    	  if (seed === Math.floor(seed)) {
    	    // Integer seed.
    	    me.a = (seed / 0x100000000) | 0;
    	    me.b = seed | 0;
    	  } else {
    	    // String seed.
    	    strseed += seed;
    	  }

    	  // Mix in string seed, then discard an initial batch of 64 values.
    	  for (var k = 0; k < strseed.length + 20; k++) {
    	    me.b ^= strseed.charCodeAt(k) | 0;
    	    me.next();
    	  }
    	}

    	function copy(f, t) {
    	  t.a = f.a;
    	  t.b = f.b;
    	  t.c = f.c;
    	  t.d = f.d;
    	  return t;
    	}
    	function impl(seed, opts) {
    	  var xg = new XorGen(seed),
    	      state = opts && opts.state,
    	      prng = function() { return (xg.next() >>> 0) / 0x100000000; };
    	  prng.double = function() {
    	    do {
    	      var top = xg.next() >>> 11,
    	          bot = (xg.next() >>> 0) / 0x100000000,
    	          result = (top + bot) / (1 << 21);
    	    } while (result === 0);
    	    return result;
    	  };
    	  prng.int32 = xg.next;
    	  prng.quick = prng;
    	  if (state) {
    	    if (typeof(state) == 'object') copy(state, xg);
    	    prng.state = function() { return copy(xg, {}); };
    	  }
    	  return prng;
    	}

    	if (module && module.exports) {
    	  module.exports = impl;
    	} else if (define && define.amd) {
    	  define(function() { return impl; });
    	} else {
    	  this.tychei = impl;
    	}

    	})(
    	  commonjsGlobal,
    	  module,    // present in node.js
    	  (typeof undefined) == 'function'    // present with an AMD loader
    	);
    } (tychei$1));

    var seedrandomExports = {};
    var seedrandom$1 = {
      get exports(){ return seedrandomExports; },
      set exports(v){ seedrandomExports = v; },
    };

    /*
    Copyright 2019 David Bau.

    Permission is hereby granted, free of charge, to any person obtaining
    a copy of this software and associated documentation files (the
    "Software"), to deal in the Software without restriction, including
    without limitation the rights to use, copy, modify, merge, publish,
    distribute, sublicense, and/or sell copies of the Software, and to
    permit persons to whom the Software is furnished to do so, subject to
    the following conditions:

    The above copyright notice and this permission notice shall be
    included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
    EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
    MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
    IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
    CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
    TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
    SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

    */

    (function (module) {
    	(function (global, pool, math) {
    	//
    	// The following constants are related to IEEE 754 limits.
    	//

    	var width = 256,        // each RC4 output is 0 <= x < 256
    	    chunks = 6,         // at least six RC4 outputs for each double
    	    digits = 52,        // there are 52 significant digits in a double
    	    rngname = 'random', // rngname: name for Math.random and Math.seedrandom
    	    startdenom = math.pow(width, chunks),
    	    significance = math.pow(2, digits),
    	    overflow = significance * 2,
    	    mask = width - 1,
    	    nodecrypto;         // node.js crypto module, initialized at the bottom.

    	//
    	// seedrandom()
    	// This is the seedrandom function described above.
    	//
    	function seedrandom(seed, options, callback) {
    	  var key = [];
    	  options = (options == true) ? { entropy: true } : (options || {});

    	  // Flatten the seed string or build one from local entropy if needed.
    	  var shortseed = mixkey(flatten(
    	    options.entropy ? [seed, tostring(pool)] :
    	    (seed == null) ? autoseed() : seed, 3), key);

    	  // Use the seed to initialize an ARC4 generator.
    	  var arc4 = new ARC4(key);

    	  // This function returns a random double in [0, 1) that contains
    	  // randomness in every bit of the mantissa of the IEEE 754 value.
    	  var prng = function() {
    	    var n = arc4.g(chunks),             // Start with a numerator n < 2 ^ 48
    	        d = startdenom,                 //   and denominator d = 2 ^ 48.
    	        x = 0;                          //   and no 'extra last byte'.
    	    while (n < significance) {          // Fill up all significant digits by
    	      n = (n + x) * width;              //   shifting numerator and
    	      d *= width;                       //   denominator and generating a
    	      x = arc4.g(1);                    //   new least-significant-byte.
    	    }
    	    while (n >= overflow) {             // To avoid rounding up, before adding
    	      n /= 2;                           //   last byte, shift everything
    	      d /= 2;                           //   right using integer math until
    	      x >>>= 1;                         //   we have exactly the desired bits.
    	    }
    	    return (n + x) / d;                 // Form the number within [0, 1).
    	  };

    	  prng.int32 = function() { return arc4.g(4) | 0; };
    	  prng.quick = function() { return arc4.g(4) / 0x100000000; };
    	  prng.double = prng;

    	  // Mix the randomness into accumulated entropy.
    	  mixkey(tostring(arc4.S), pool);

    	  // Calling convention: what to return as a function of prng, seed, is_math.
    	  return (options.pass || callback ||
    	      function(prng, seed, is_math_call, state) {
    	        if (state) {
    	          // Load the arc4 state from the given state if it has an S array.
    	          if (state.S) { copy(state, arc4); }
    	          // Only provide the .state method if requested via options.state.
    	          prng.state = function() { return copy(arc4, {}); };
    	        }

    	        // If called as a method of Math (Math.seedrandom()), mutate
    	        // Math.random because that is how seedrandom.js has worked since v1.0.
    	        if (is_math_call) { math[rngname] = prng; return seed; }

    	        // Otherwise, it is a newer calling convention, so return the
    	        // prng directly.
    	        else return prng;
    	      })(
    	  prng,
    	  shortseed,
    	  'global' in options ? options.global : (this == math),
    	  options.state);
    	}

    	//
    	// ARC4
    	//
    	// An ARC4 implementation.  The constructor takes a key in the form of
    	// an array of at most (width) integers that should be 0 <= x < (width).
    	//
    	// The g(count) method returns a pseudorandom integer that concatenates
    	// the next (count) outputs from ARC4.  Its return value is a number x
    	// that is in the range 0 <= x < (width ^ count).
    	//
    	function ARC4(key) {
    	  var t, keylen = key.length,
    	      me = this, i = 0, j = me.i = me.j = 0, s = me.S = [];

    	  // The empty key [] is treated as [0].
    	  if (!keylen) { key = [keylen++]; }

    	  // Set up S using the standard key scheduling algorithm.
    	  while (i < width) {
    	    s[i] = i++;
    	  }
    	  for (i = 0; i < width; i++) {
    	    s[i] = s[j = mask & (j + key[i % keylen] + (t = s[i]))];
    	    s[j] = t;
    	  }

    	  // The "g" method returns the next (count) outputs as one number.
    	  (me.g = function(count) {
    	    // Using instance members instead of closure state nearly doubles speed.
    	    var t, r = 0,
    	        i = me.i, j = me.j, s = me.S;
    	    while (count--) {
    	      t = s[i = mask & (i + 1)];
    	      r = r * width + s[mask & ((s[i] = s[j = mask & (j + t)]) + (s[j] = t))];
    	    }
    	    me.i = i; me.j = j;
    	    return r;
    	    // For robust unpredictability, the function call below automatically
    	    // discards an initial batch of values.  This is called RC4-drop[256].
    	    // See http://google.com/search?q=rsa+fluhrer+response&btnI
    	  })(width);
    	}

    	//
    	// copy()
    	// Copies internal state of ARC4 to or from a plain object.
    	//
    	function copy(f, t) {
    	  t.i = f.i;
    	  t.j = f.j;
    	  t.S = f.S.slice();
    	  return t;
    	}
    	//
    	// flatten()
    	// Converts an object tree to nested arrays of strings.
    	//
    	function flatten(obj, depth) {
    	  var result = [], typ = (typeof obj), prop;
    	  if (depth && typ == 'object') {
    	    for (prop in obj) {
    	      try { result.push(flatten(obj[prop], depth - 1)); } catch (e) {}
    	    }
    	  }
    	  return (result.length ? result : typ == 'string' ? obj : obj + '\0');
    	}

    	//
    	// mixkey()
    	// Mixes a string seed into a key that is an array of integers, and
    	// returns a shortened string seed that is equivalent to the result key.
    	//
    	function mixkey(seed, key) {
    	  var stringseed = seed + '', smear, j = 0;
    	  while (j < stringseed.length) {
    	    key[mask & j] =
    	      mask & ((smear ^= key[mask & j] * 19) + stringseed.charCodeAt(j++));
    	  }
    	  return tostring(key);
    	}

    	//
    	// autoseed()
    	// Returns an object for autoseeding, using window.crypto and Node crypto
    	// module if available.
    	//
    	function autoseed() {
    	  try {
    	    var out;
    	    if (nodecrypto && (out = nodecrypto.randomBytes)) {
    	      // The use of 'out' to remember randomBytes makes tight minified code.
    	      out = out(width);
    	    } else {
    	      out = new Uint8Array(width);
    	      (global.crypto || global.msCrypto).getRandomValues(out);
    	    }
    	    return tostring(out);
    	  } catch (e) {
    	    var browser = global.navigator,
    	        plugins = browser && browser.plugins;
    	    return [+new Date, global, plugins, global.screen, tostring(pool)];
    	  }
    	}

    	//
    	// tostring()
    	// Converts an array of charcodes to a string
    	//
    	function tostring(a) {
    	  return String.fromCharCode.apply(0, a);
    	}

    	//
    	// When seedrandom.js is loaded, we immediately mix a few bits
    	// from the built-in RNG into the entropy pool.  Because we do
    	// not want to interfere with deterministic PRNG state later,
    	// seedrandom will not call math.random on its own again after
    	// initialization.
    	//
    	mixkey(math.random(), pool);

    	//
    	// Nodejs and AMD support: export the implementation as a module using
    	// either convention.
    	//
    	if (module.exports) {
    	  module.exports = seedrandom;
    	  // When in node.js, try using crypto package for autoseeding.
    	  try {
    	    nodecrypto = require('crypto');
    	  } catch (ex) {}
    	} else {
    	  // When included as a plain script, set up Math.seedrandom global.
    	  math['seed' + rngname] = seedrandom;
    	}


    	// End anonymous scope, and pass initial values.
    	})(
    	  // global: `self` in browsers (including strict mode and web workers),
    	  // otherwise `this` in Node and other environments
    	  (typeof self !== 'undefined') ? self : commonjsGlobal,
    	  [],     // pool: entropy pool starts empty
    	  Math    // math: package containing random, pow, and seedrandom
    	);
    } (seedrandom$1));

    // A library of seedable RNGs implemented in Javascript.
    //
    // Usage:
    //
    // var seedrandom = require('seedrandom');
    // var random = seedrandom(1); // or any seed.
    // var x = random();       // 0 <= x < 1.  Every bit is random.
    // var x = random.quick(); // 0 <= x < 1.  32 bits of randomness.

    // alea, a 53-bit multiply-with-carry generator by Johannes Baagøe.
    // Period: ~2^116
    // Reported to pass all BigCrush tests.
    var alea = aleaExports;

    // xor128, a pure xor-shift generator by George Marsaglia.
    // Period: 2^128-1.
    // Reported to fail: MatrixRank and LinearComp.
    var xor128 = xor128Exports;

    // xorwow, George Marsaglia's 160-bit xor-shift combined plus weyl.
    // Period: 2^192-2^32
    // Reported to fail: CollisionOver, SimpPoker, and LinearComp.
    var xorwow = xorwowExports;

    // xorshift7, by François Panneton and Pierre L'ecuyer, takes
    // a different approach: it adds robustness by allowing more shifts
    // than Marsaglia's original three.  It is a 7-shift generator
    // with 256 bits, that passes BigCrush with no systmatic failures.
    // Period 2^256-1.
    // No systematic BigCrush failures reported.
    var xorshift7 = xorshift7Exports;

    // xor4096, by Richard Brent, is a 4096-bit xor-shift with a
    // very long period that also adds a Weyl generator. It also passes
    // BigCrush with no systematic failures.  Its long period may
    // be useful if you have many generators and need to avoid
    // collisions.
    // Period: 2^4128-2^32.
    // No systematic BigCrush failures reported.
    var xor4096 = xor4096Exports;

    // Tyche-i, by Samuel Neves and Filipe Araujo, is a bit-shifting random
    // number generator derived from ChaCha, a modern stream cipher.
    // https://eden.dei.uc.pt/~sneves/pubs/2011-snfa2.pdf
    // Period: ~2^127
    // No systematic BigCrush failures reported.
    var tychei = tycheiExports;

    // The original ARC4-based prng included in this library.
    // Period: ~2^1600
    var sr = seedrandomExports;

    sr.alea = alea;
    sr.xor128 = xor128;
    sr.xorwow = xorwow;
    sr.xorshift7 = xorshift7;
    sr.xor4096 = xor4096;
    sr.tychei = tychei;

    var seedrandom = sr;

    /**
     * @param {HTMLCanvasElement} canvas
     * @param {string} domainKey
     * @param {string} sessionKey
     * @param {any} getImageDataProxy
     * @param {CanvasRenderingContext2D | WebGL2RenderingContext | WebGLRenderingContext} ctx?
     */
    function computeOffScreenCanvas (canvas, domainKey, sessionKey, getImageDataProxy, ctx) {
        if (!ctx) {
            ctx = canvas.getContext('2d');
        }

        // Make a off-screen canvas and put the data there
        const offScreenCanvas = document.createElement('canvas');
        offScreenCanvas.width = canvas.width;
        offScreenCanvas.height = canvas.height;
        const offScreenCtx = offScreenCanvas.getContext('2d');

        let rasterizedCtx = ctx;
        // If we're not a 2d canvas we need to rasterise first into 2d
        const rasterizeToCanvas = !(ctx instanceof CanvasRenderingContext2D);
        if (rasterizeToCanvas) {
            rasterizedCtx = offScreenCtx;
            offScreenCtx.drawImage(canvas, 0, 0);
        }

        // We *always* compute the random pixels on the complete pixel set, then pass back the subset later
        let imageData = getImageDataProxy._native.apply(rasterizedCtx, [0, 0, canvas.width, canvas.height]);
        imageData = modifyPixelData(imageData, sessionKey, domainKey, canvas.width);

        if (rasterizeToCanvas) {
            clearCanvas(offScreenCtx);
        }

        offScreenCtx.putImageData(imageData, 0, 0);

        return { offScreenCanvas, offScreenCtx }
    }

    /**
     * Clears the pixels from the canvas context
     *
     * @param {CanvasRenderingContext2D} canvasContext
     */
    function clearCanvas (canvasContext) {
        // Save state and clean the pixels from the canvas
        canvasContext.save();
        canvasContext.globalCompositeOperation = 'destination-out';
        canvasContext.fillStyle = 'rgb(255,255,255)';
        canvasContext.fillRect(0, 0, canvasContext.canvas.width, canvasContext.canvas.height);
        canvasContext.restore();
    }

    /**
     * @param {ImageData} imageData
     * @param {string} sessionKey
     * @param {string} domainKey
     * @param {number} width
     */
    function modifyPixelData (imageData, domainKey, sessionKey, width) {
        const d = imageData.data;
        const length = d.length / 4;
        let checkSum = 0;
        const mappingArray = [];
        for (let i = 0; i < length; i += 4) {
            if (!shouldIgnorePixel(d, i) && !adjacentSame(d, i, width)) {
                mappingArray.push(i);
                checkSum += d[i] + d[i + 1] + d[i + 2] + d[i + 3];
            }
        }

        const windowHash = getDataKeySync(sessionKey, domainKey, checkSum);
        const rng = new seedrandom(windowHash);
        for (let i = 0; i < mappingArray.length; i++) {
            const rand = rng();
            const byte = Math.floor(rand * 10);
            const channel = byte % 3;
            const pixelCanvasIndex = mappingArray[i] + channel;

            d[pixelCanvasIndex] = d[pixelCanvasIndex] ^ (byte & 0x1);
        }

        return imageData
    }

    /**
     * Ignore pixels that have neighbours that are the same
     *
     * @param {Uint8ClampedArray} imageData
     * @param {number} index
     * @param {number} width
     */
    function adjacentSame (imageData, index, width) {
        const widthPixel = width * 4;
        const x = index % widthPixel;
        const maxLength = imageData.length;

        // Pixels not on the right border of the canvas
        if (x < widthPixel) {
            const right = index + 4;
            if (!pixelsSame(imageData, index, right)) {
                return false
            }
            const diagonalRightUp = right - widthPixel;
            if (diagonalRightUp > 0 && !pixelsSame(imageData, index, diagonalRightUp)) {
                return false
            }
            const diagonalRightDown = right + widthPixel;
            if (diagonalRightDown < maxLength && !pixelsSame(imageData, index, diagonalRightDown)) {
                return false
            }
        }

        // Pixels not on the left border of the canvas
        if (x > 0) {
            const left = index - 4;
            if (!pixelsSame(imageData, index, left)) {
                return false
            }
            const diagonalLeftUp = left - widthPixel;
            if (diagonalLeftUp > 0 && !pixelsSame(imageData, index, diagonalLeftUp)) {
                return false
            }
            const diagonalLeftDown = left + widthPixel;
            if (diagonalLeftDown < maxLength && !pixelsSame(imageData, index, diagonalLeftDown)) {
                return false
            }
        }

        const up = index - widthPixel;
        if (up > 0 && !pixelsSame(imageData, index, up)) {
            return false
        }

        const down = index + widthPixel;
        if (down < maxLength && !pixelsSame(imageData, index, down)) {
            return false
        }

        return true
    }

    /**
     * Check that a pixel at index and index2 match all channels
     * @param {Uint8ClampedArray} imageData
     * @param {number} index
     * @param {number} index2
     */
    function pixelsSame (imageData, index, index2) {
        return imageData[index] === imageData[index2] &&
               imageData[index + 1] === imageData[index2 + 1] &&
               imageData[index + 2] === imageData[index2 + 2] &&
               imageData[index + 3] === imageData[index2 + 3]
    }

    /**
     * Returns true if pixel should be ignored
     * @param {Uint8ClampedArray} imageData
     * @param {number} index
     * @returns {boolean}
     */
    function shouldIgnorePixel (imageData, index) {
        // Transparent pixels
        if (imageData[index + 3] === 0) {
            return true
        }
        return false
    }

    function init$a (args) {
        const { sessionKey, site } = args;
        const domainKey = site.domain;
        const featureName = 'fingerprinting-canvas';
        const supportsWebGl = getFeatureSettingEnabled(featureName, args, 'webGl');

        const unsafeCanvases = new WeakSet();
        const canvasContexts = new WeakMap();
        const canvasCache = new WeakMap();

        /**
         * Clear cache as canvas has changed
         * @param {OffscreenCanvas | HTMLCanvasElement} canvas
         */
        function clearCache (canvas) {
            canvasCache.delete(canvas);
        }

        /**
         * @param {OffscreenCanvas | HTMLCanvasElement} canvas
         */
        function treatAsUnsafe (canvas) {
            unsafeCanvases.add(canvas);
            clearCache(canvas);
        }

        const proxy = new DDGProxy(featureName, HTMLCanvasElement.prototype, 'getContext', {
            apply (target, thisArg, args) {
                const context = DDGReflect.apply(target, thisArg, args);
                try {
                    canvasContexts.set(thisArg, context);
                } catch {
                }
                return context
            }
        });
        proxy.overload();

        // Known data methods
        const safeMethods = ['putImageData', 'drawImage'];
        for (const methodName of safeMethods) {
            const safeMethodProxy = new DDGProxy(featureName, CanvasRenderingContext2D.prototype, methodName, {
                apply (target, thisArg, args) {
                    // Don't apply escape hatch for canvases
                    if (methodName === 'drawImage' && args[0] && args[0] instanceof HTMLCanvasElement) {
                        treatAsUnsafe(args[0]);
                    } else {
                        clearCache(thisArg.canvas);
                    }
                    return DDGReflect.apply(target, thisArg, args)
                }
            });
            safeMethodProxy.overload();
        }

        const unsafeMethods = [
            'strokeRect',
            'bezierCurveTo',
            'quadraticCurveTo',
            'arcTo',
            'ellipse',
            'rect',
            'fill',
            'stroke',
            'lineTo',
            'beginPath',
            'closePath',
            'arc',
            'fillText',
            'fillRect',
            'strokeText',
            'createConicGradient',
            'createLinearGradient',
            'createRadialGradient',
            'createPattern'
        ];
        for (const methodName of unsafeMethods) {
            // Some methods are browser specific
            if (methodName in CanvasRenderingContext2D.prototype) {
                const unsafeProxy = new DDGProxy(featureName, CanvasRenderingContext2D.prototype, methodName, {
                    apply (target, thisArg, args) {
                        treatAsUnsafe(thisArg.canvas);
                        return DDGReflect.apply(target, thisArg, args)
                    }
                });
                unsafeProxy.overload();
            }
        }

        if (supportsWebGl) {
            const unsafeGlMethods = [
                'commit',
                'compileShader',
                'shaderSource',
                'attachShader',
                'createProgram',
                'linkProgram',
                'drawElements',
                'drawArrays'
            ];
            const glContexts = [
                WebGLRenderingContext
            ];
            if ('WebGL2RenderingContext' in globalThis) {
                glContexts.push(WebGL2RenderingContext);
            }
            for (const context of glContexts) {
                for (const methodName of unsafeGlMethods) {
                    // Some methods are browser specific
                    if (methodName in context.prototype) {
                        const unsafeProxy = new DDGProxy(featureName, context.prototype, methodName, {
                            apply (target, thisArg, args) {
                                treatAsUnsafe(thisArg.canvas);
                                return DDGReflect.apply(target, thisArg, args)
                            }
                        });
                        unsafeProxy.overload();
                    }
                }
            }
        }

        // Using proxies here to swallow calls to toString etc
        const getImageDataProxy = new DDGProxy(featureName, CanvasRenderingContext2D.prototype, 'getImageData', {
            apply (target, thisArg, args) {
                if (!unsafeCanvases.has(thisArg.canvas)) {
                    return DDGReflect.apply(target, thisArg, args)
                }
                // Anything we do here should be caught and ignored silently
                try {
                    const { offScreenCtx } = getCachedOffScreenCanvasOrCompute(thisArg.canvas, domainKey, sessionKey);
                    // Call the original method on the modified off-screen canvas
                    return DDGReflect.apply(target, offScreenCtx, args)
                } catch {
                }

                return DDGReflect.apply(target, thisArg, args)
            }
        });
        getImageDataProxy.overload();

        /**
         * Get cached offscreen if one exists, otherwise compute one
         *
         * @param {HTMLCanvasElement} canvas
         * @param {string} domainKey
         * @param {string} sessionKey
         */
        function getCachedOffScreenCanvasOrCompute (canvas, domainKey, sessionKey) {
            let result;
            if (canvasCache.has(canvas)) {
                result = canvasCache.get(canvas);
            } else {
                const ctx = canvasContexts.get(canvas);
                result = computeOffScreenCanvas(canvas, domainKey, sessionKey, getImageDataProxy, ctx);
                canvasCache.set(canvas, result);
            }
            return result
        }

        const canvasMethods = ['toDataURL', 'toBlob'];
        for (const methodName of canvasMethods) {
            const proxy = new DDGProxy(featureName, HTMLCanvasElement.prototype, methodName, {
                apply (target, thisArg, args) {
                    // Short circuit for low risk canvas calls
                    if (!unsafeCanvases.has(thisArg)) {
                        return DDGReflect.apply(target, thisArg, args)
                    }
                    try {
                        const { offScreenCanvas } = getCachedOffScreenCanvasOrCompute(thisArg, domainKey, sessionKey);
                        // Call the original method on the modified off-screen canvas
                        return DDGReflect.apply(target, offScreenCanvas, args)
                    } catch {
                        // Something we did caused an exception, fall back to the native
                        return DDGReflect.apply(target, thisArg, args)
                    }
                }
            });
            proxy.overload();
        }
    }

    var fingerprintingCanvas = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$a
    });

    const featureName$2 = 'fingerprinting-hardware';

    function init$9 (args) {
        const Navigator = globalThis.Navigator;
        const navigator = globalThis.navigator;

        overrideProperty('keyboard', {
            object: Navigator.prototype,
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            origValue: navigator.keyboard,
            targetValue: getFeatureAttr(featureName$2, args, 'keyboard')
        });
        overrideProperty('hardwareConcurrency', {
            object: Navigator.prototype,
            origValue: navigator.hardwareConcurrency,
            targetValue: getFeatureAttr(featureName$2, args, 'hardwareConcurrency', 2)
        });
        overrideProperty('deviceMemory', {
            object: Navigator.prototype,
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            origValue: navigator.deviceMemory,
            targetValue: getFeatureAttr(featureName$2, args, 'deviceMemory', 8)
        });
    }

    var fingerprintingHardware = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$9
    });

    const featureName$1 = 'fingerprinting-screen-size';

    /**
     * normalize window dimensions, if more than one monitor is in play.
     *  X/Y values are set in the browser based on distance to the main monitor top or left, which
     * can mean second or more monitors have very large or negative values. This function maps a given
     * given coordinate value to the proper place on the main screen.
     */
    function normalizeWindowDimension (value, targetDimension) {
        if (value > targetDimension) {
            return value % targetDimension
        }
        if (value < 0) {
            return targetDimension + value
        }
        return value
    }

    function setWindowPropertyValue (property, value) {
        // Here we don't update the prototype getter because the values are updated dynamically
        try {
            defineProperty(globalThis, property, {
                get: () => value,
                set: () => {},
                configurable: true
            });
        } catch (e) {}
    }

    const origPropertyValues = {};

    /**
     * Fix window dimensions. The extension runs in a different JS context than the
     * page, so we can inject the correct screen values as the window is resized,
     * ensuring that no information is leaked as the dimensions change, but also that the
     * values change correctly for valid use cases.
     */
    function setWindowDimensions () {
        try {
            const window = globalThis;
            const top = globalThis.top;

            const normalizedY = normalizeWindowDimension(window.screenY, window.screen.height);
            const normalizedX = normalizeWindowDimension(window.screenX, window.screen.width);
            if (normalizedY <= origPropertyValues.availTop) {
                setWindowPropertyValue('screenY', 0);
                setWindowPropertyValue('screenTop', 0);
            } else {
                setWindowPropertyValue('screenY', normalizedY);
                setWindowPropertyValue('screenTop', normalizedY);
            }

            if (top.window.outerHeight >= origPropertyValues.availHeight - 1) {
                setWindowPropertyValue('outerHeight', top.window.screen.height);
            } else {
                try {
                    setWindowPropertyValue('outerHeight', top.window.outerHeight);
                } catch (e) {
                    // top not accessible to certain iFrames, so ignore.
                }
            }

            if (normalizedX <= origPropertyValues.availLeft) {
                setWindowPropertyValue('screenX', 0);
                setWindowPropertyValue('screenLeft', 0);
            } else {
                setWindowPropertyValue('screenX', normalizedX);
                setWindowPropertyValue('screenLeft', normalizedX);
            }

            if (top.window.outerWidth >= origPropertyValues.availWidth - 1) {
                setWindowPropertyValue('outerWidth', top.window.screen.width);
            } else {
                try {
                    setWindowPropertyValue('outerWidth', top.window.outerWidth);
                } catch (e) {
                    // top not accessible to certain iFrames, so ignore.
                }
            }
        } catch (e) {
            // in a cross domain iFrame, top.window is not accessible.
        }
    }

    function init$8 (args) {
        const Screen = globalThis.Screen;
        const screen = globalThis.screen;

        origPropertyValues.availTop = overrideProperty('availTop', {
            object: Screen.prototype,
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            origValue: screen.availTop,
            targetValue: getFeatureAttr(featureName$1, args, 'availTop', 0)
        });
        origPropertyValues.availLeft = overrideProperty('availLeft', {
            object: Screen.prototype,
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            origValue: screen.availLeft,
            targetValue: getFeatureAttr(featureName$1, args, 'availLeft', 0)
        });
        origPropertyValues.availWidth = overrideProperty('availWidth', {
            object: Screen.prototype,
            origValue: screen.availWidth,
            targetValue: screen.width
        });
        origPropertyValues.availHeight = overrideProperty('availHeight', {
            object: Screen.prototype,
            origValue: screen.availHeight,
            targetValue: screen.height
        });
        overrideProperty('colorDepth', {
            object: Screen.prototype,
            origValue: screen.colorDepth,
            targetValue: getFeatureAttr(featureName$1, args, 'colorDepth', 24)
        });
        overrideProperty('pixelDepth', {
            object: Screen.prototype,
            origValue: screen.pixelDepth,
            targetValue: getFeatureAttr(featureName$1, args, 'pixelDepth', 24)
        });

        window.addEventListener('resize', function () {
            setWindowDimensions();
        });
        setWindowDimensions();
    }

    var fingerprintingScreenSize = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$8
    });

    function init$7 () {
        const navigator = globalThis.navigator;
        const Navigator = globalThis.Navigator;

        /**
         * Temporary storage can be used to determine hard disk usage and size.
         * This will limit the max storage to 4GB without completely disabling the
         * feature.
         */
        // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
        if (navigator.webkitTemporaryStorage) {
            try {
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                const org = navigator.webkitTemporaryStorage.queryUsageAndQuota;
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                const tStorage = navigator.webkitTemporaryStorage;
                tStorage.queryUsageAndQuota = function queryUsageAndQuota (callback, err) {
                    const modifiedCallback = function (usedBytes, grantedBytes) {
                        const maxBytesGranted = 4 * 1024 * 1024 * 1024;
                        const spoofedGrantedBytes = Math.min(grantedBytes, maxBytesGranted);
                        callback(usedBytes, spoofedGrantedBytes);
                    };
                    // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                    org.call(navigator.webkitTemporaryStorage, modifiedCallback, err);
                };
                defineProperty(Navigator.prototype, 'webkitTemporaryStorage', { get: () => tStorage });
            } catch (e) {}
        }
    }

    var fingerprintingTemporaryStorage = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$7
    });

    function init$6 () {
        try {
            if ('browsingTopics' in Document.prototype) {
                delete Document.prototype.browsingTopics;
            }
            if ('joinAdInterestGroup' in Navigator.prototype) {
                delete Navigator.prototype.joinAdInterestGroup;
            }
            if ('leaveAdInterestGroup' in Navigator.prototype) {
                delete Navigator.prototype.leaveAdInterestGroup;
            }
            if ('updateAdInterestGroups' in Navigator.prototype) {
                delete Navigator.prototype.updateAdInterestGroups;
            }
            if ('runAdAuction' in Navigator.prototype) {
                delete Navigator.prototype.runAdAuction;
            }
            if ('adAuctionComponents' in Navigator.prototype) {
                delete Navigator.prototype.adAuctionComponents;
            }
        } catch {
            // Throw away this exception, it's likely a confict with another extension
        }
    }

    var googleRejected = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$6
    });

    // Set Global Privacy Control property on DOM
    function init$5 (args) {
        try {
            // If GPC on, set DOM property prototype to true if not already true
            if (args.globalPrivacyControlValue) {
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                if (navigator.globalPrivacyControl) return
                defineProperty(Navigator.prototype, 'globalPrivacyControl', {
                    get: () => true,
                    configurable: true,
                    enumerable: true
                });
            } else {
                // If GPC off & unsupported by browser, set DOM property prototype to false
                // this may be overwritten by the user agent or other extensions
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                if (typeof navigator.globalPrivacyControl !== 'undefined') return
                defineProperty(Navigator.prototype, 'globalPrivacyControl', {
                    get: () => false,
                    configurable: true,
                    enumerable: true
                });
            }
        } catch {
            // Ignore exceptions that could be caused by conflicting with other extensions
        }
    }

    var gpc = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$5
    });

    function init$4 (args) {
        try {
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            if (navigator.duckduckgo) {
                return
            }
            if (!args.platform || !args.platform.name) {
                return
            }
            defineProperty(Navigator.prototype, 'duckduckgo', {
                value: {
                    platform: args.platform.name,
                    isDuckDuckGo () {
                        return DDGPromise.resolve(true)
                    }
                },
                enumerable: true,
                configurable: false,
                writable: false
            });
        } catch {
            // todo: Just ignore this exception?
        }
    }

    var navigatorInterface = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$4
    });

    function init$3 (args) {
        // Unfortunately, we only have limited information about the referrer and current frame. A single
        // page may load many requests and sub frames, all with different referrers. Since we
        if (args.referrer && // make sure the referrer was set correctly
            args.referrer.referrer !== undefined && // referrer value will be undefined when it should be unchanged.
            document.referrer && // don't change the value if it isn't set
            document.referrer !== '' && // don't add referrer information
            new URL(document.URL).hostname !== new URL(document.referrer).hostname) { // don't replace the referrer for the current host.
            let trimmedReferer = document.referrer;
            if (new URL(document.referrer).hostname === args.referrer.referrerHost) {
                // make sure the real referrer & replacement referrer match if we're going to replace it
                trimmedReferer = args.referrer.referrer;
            } else {
                // if we don't have a matching referrer, just trim it to origin.
                trimmedReferer = new URL(document.referrer).origin + '/';
            }
            overrideProperty('referrer', {
                object: Document.prototype,
                origValue: document.referrer,
                targetValue: trimmedReferer
            });
        }
    }

    var referrer = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$3
    });

    let stackDomains = [];
    let matchAllStackDomains = false;
    let taintCheck = false;
    let initialCreateElement;
    let tagModifiers = {};
    let shadowDomEnabled = false;

    /**
     * @param {string} tagName
     * @param {'property' | 'attribute' | 'handler' | 'listener'} filterName
     * @param {string} key
     * @returns {boolean}
     */
    function shouldFilterKey (tagName, filterName, key) {
        if (filterName === 'attribute') {
            key = key.toLowerCase();
        }
        return tagModifiers?.[tagName]?.filters?.[filterName]?.includes(key)
    }

    let elementRemovalTimeout;
    const featureName = 'runtimeChecks';
    const taintSymbol = Symbol(featureName);
    const supportedSinks = ['src'];

    class DDGRuntimeChecks extends HTMLElement {
        #tagName
        #el
        #listeners
        #connected
        #sinks

        constructor () {
            super();
            this.#tagName = null;
            this.#el = null;
            this.#listeners = [];
            this.#connected = false;
            this.#sinks = {};
            if (shadowDomEnabled) {
                const shadow = this.attachShadow({ mode: 'open' });
                const style = createStyleElement(`
                :host {
                    display: none;
                }
            `);
                shadow.appendChild(style);
            }
        }

        /**
         * This method is called once and externally so has to remain public.
         **/
        setTagName (tagName) {
            this.#tagName = tagName;

            // Clear the method so it can't be called again
            delete this.setTagName;
        }

        connectedCallback () {
            // Solves re-entrancy issues from React
            if (this.#connected) return
            this.#connected = true;
            if (!this._transplantElement) {
                // Restore the 'this' object with the DDGRuntimeChecks prototype as sometimes pages will overwrite it.
                Object.setPrototypeOf(this, DDGRuntimeChecks.prototype);
            }
            this._transplantElement();
        }

        _monitorProperties (el) {
            // Mutation oberver and observedAttributes don't work on property accessors
            // So instead we need to monitor all properties on the prototypes and forward them to the real element
            let propertyNames = [];
            let proto = Object.getPrototypeOf(el);
            while (proto && proto !== Object.prototype) {
                propertyNames.push(...Object.getOwnPropertyNames(proto));
                proto = Object.getPrototypeOf(proto);
            }
            const classMethods = Object.getOwnPropertyNames(Object.getPrototypeOf(this));
            // Filter away the methods we don't want to monitor from our own class
            propertyNames = propertyNames.filter(prop => !classMethods.includes(prop));
            propertyNames.forEach(prop => {
                if (prop === 'constructor') return
                // May throw, but this is best effort monitoring.
                try {
                    Object.defineProperty(this, prop, {
                        get () {
                            return el[prop]
                        },
                        set (value) {
                            if (shouldFilterKey(this.#tagName, 'property', prop)) return
                            el[prop] = value;
                        }
                    });
                } catch { }
            });
        }

        /**
         * The element has been moved to the DOM, so we can now reflect all changes to a real element.
         * This is to allow us to interrogate the real element before it is moved to the DOM.
         */
        _transplantElement () {
            // Creeate the real element
            const el = initialCreateElement.call(document, this.#tagName);

            if (taintCheck) {
                // Add a symbol to the element so we can identify it as a runtime checked element
                Object.defineProperty(el, taintSymbol, { value: true, configurable: false, enumerable: false, writable: false });
            }

            // Reflect all attrs to the new element
            for (const attribute of this.getAttributeNames()) {
                if (shouldFilterKey(this.#tagName, 'attribute', attribute)) continue
                el.setAttribute(attribute, this.getAttribute(attribute));
            }

            // Reflect all props to the new element
            const props = Object.keys(this);

            // Nonce isn't enumerable so we need to add it manually
            props.push('nonce');

            for (const prop of props) {
                if (shouldFilterKey(this.#tagName, 'property', prop)) continue
                el[prop] = this[prop];
            }

            for (const sink of supportedSinks) {
                if (this.#sinks[sink]) {
                    el[sink] = this.#sinks[sink];
                }
            }

            // Reflect all listeners to the new element
            for (const [...args] of this.#listeners) {
                if (shouldFilterKey(this.#tagName, 'listener', args[0])) continue
                el.addEventListener(...args);
            }
            this.#listeners = [];

            // Reflect all 'on' event handlers to the new element
            for (const propName in this) {
                if (propName.startsWith('on')) {
                    if (shouldFilterKey(this.#tagName, 'handler', propName)) continue
                    const prop = this[propName];
                    if (typeof prop === 'function') {
                        el[propName] = prop;
                    }
                }
            }

            // Move all children to the new element
            while (this.firstChild) {
                el.appendChild(this.firstChild);
            }

            // Move the new element to the DOM
            try {
                this.insertAdjacentElement('afterend', el);
            } catch (e) { console.warn(e); }

            this._monitorProperties(el);
            // TODO pollyfill WeakRef
            this.#el = new WeakRef(el);

            // Delay removal of the custom element so if the script calls removeChild it will still be in the DOM and not throw.
            setTimeout(() => {
                this.remove();
            }, elementRemovalTimeout);
        }

        _getElement () {
            return this.#el?.deref()
        }

        /* Native DOM element methods we're capturing to supplant values into the constructed node or store data for. */

        set src (value) {
            const el = this._getElement();
            if (el) {
                el.src = value;
                return
            }
            this.#sinks.src = value;
        }

        get src () {
            const el = this._getElement();
            if (el) {
                return el.src
            }
            // @ts-expect-error TrustedScriptURL is not defined in the TS lib
            // eslint-disable-next-line no-undef
            if ('TrustedScriptURL' in window && this.#sinks.src instanceof TrustedScriptURL) {
                return this.#sinks.src.toString()
            }
            return this.#sinks.src
        }

        getAttribute (name, value) {
            if (shouldFilterKey(this.#tagName, 'attribute', name)) return
            if (supportedSinks.includes(name)) {
                return this[name]
            }
            const el = this._getElement();
            if (el) {
                return el.getAttribute(name)
            }
            return super.getAttribute(name)
        }

        setAttribute (name, value) {
            if (shouldFilterKey(this.#tagName, 'attribute', name)) return
            if (supportedSinks.includes(name)) {
                this[name] = value;
                return
            }
            const el = this._getElement();
            if (el) {
                return el.setAttribute(name, value)
            }
            return super.setAttribute(name, value)
        }

        removeAttribute (name) {
            if (shouldFilterKey(this.#tagName, 'attribute', name)) return
            if (supportedSinks.includes(name)) {
                delete this[name];
                return
            }
            const el = this._getElement();
            if (el) {
                return el.removeAttribute(name)
            }
            return super.removeAttribute(name)
        }

        addEventListener (...args) {
            if (shouldFilterKey(this.#tagName, 'listener', args[0])) return
            const el = this._getElement();
            if (el) {
                return el.addEventListener(...args)
            }
            this.#listeners.push([...args]);
        }

        removeEventListener (...args) {
            if (shouldFilterKey(this.#tagName, 'listener', args[0])) return
            const el = this._getElement();
            if (el) {
                return el.removeEventListener(...args)
            }
            this.#listeners = this.#listeners.filter((listener) => {
                return listener[0] !== args[0] || listener[1] !== args[1]
            });
        }

        toString () {
            const interfaceName = this.#tagName.charAt(0).toUpperCase() + this.#tagName.slice(1);
            return `[object HTML${interfaceName}Element]`
        }

        get tagName () {
            return this.#tagName.toUpperCase()
        }

        get nodeName () {
            return this.tagName
        }

        remove () {
            const el = this._getElement();
            if (el) {
                return el.remove()
            }
            return super.remove()
        }

        removeChild (child) {
            const el = this._getElement();
            if (el) {
                return el.removeChild(child)
            }
            return super.removeChild(child)
        }
    }

    /**
     * Overrides the instanceof checks to make the custom element interface pass an instanceof check
     * @param {Object} elementInterface
     */
    function overloadInstanceOfChecks (elementInterface) {
        const proxy = new Proxy(elementInterface[Symbol.hasInstance], {
            apply (fn, scope, args) {
                if (args[0] instanceof DDGRuntimeChecks) {
                    return true
                }
                return Reflect.apply(fn, scope, args)
            }
        });
        // May throw, but we can ignore it
        try {
            Object.defineProperty(elementInterface, Symbol.hasInstance, {
                value: proxy
            });
        } catch {}
    }

    /**
     * Returns true if the tag should be intercepted
     * @param {string} tagName
     * @returns {boolean}
     */
    function shouldInterrogate (tagName) {
        const interestingTags = ['script'];
        if (!interestingTags.includes(tagName)) {
            return false
        }
        if (matchAllStackDomains) {
            return true
        }
        if (taintCheck && document.currentScript?.[taintSymbol]) {
            return true
        }
        const stack = getStack();
        const scriptOrigins = [...getStackTraceOrigins(stack)];
        const isInterestingHost = scriptOrigins.some(origin => {
            return stackDomains.some(rule => matchHostname(origin, rule.domain))
        });
        return isInterestingHost
    }

    function overrideCreateElement () {
        const proxy = new DDGProxy(featureName, Document.prototype, 'createElement', {
            apply (fn, scope, args) {
                if (args.length >= 1) {
                    const initialTagName = args[0].toLowerCase();
                    if (shouldInterrogate(initialTagName)) {
                        args[0] = 'ddg-runtime-checks';
                        const el = Reflect.apply(fn, scope, args);
                        el.setTagName(initialTagName);
                        return el
                    }
                }
                return Reflect.apply(fn, scope, args)
            }
        });
        proxy.overload();
        initialCreateElement = proxy._native;
    }

    function load () {
        // This shouldn't happen, but if it does we don't want to break the page
        try {
            customElements.define('ddg-runtime-checks', DDGRuntimeChecks);
        } catch {}
    }

    function init$2 (args) {
        const domain = args.site.domain;
        const domains = getFeatureSetting(featureName, args, 'domains') || [];
        let enabled = getFeatureSettingEnabled(featureName, args, 'matchAllDomains');
        if (!enabled) {
            enabled = domains.find((rule) => {
                return matchHostname(domain, rule.domain)
            });
        }
        if (!enabled) return

        taintCheck = getFeatureSettingEnabled(featureName, args, 'taintCheck');
        matchAllStackDomains = getFeatureSettingEnabled(featureName, args, 'matchAllStackDomains');
        stackDomains = getFeatureSetting(featureName, args, 'stackDomains') || [];
        elementRemovalTimeout = getFeatureSetting(featureName, args, 'elementRemovalTimeout') || 1000;
        tagModifiers = getFeatureSetting(featureName, args, 'tagModifiers') || {};
        shadowDomEnabled = getFeatureSettingEnabled(featureName, args, 'shadowDom') || false;

        overrideCreateElement();

        if (getFeatureSettingEnabled(featureName, args, 'overloadInstanceOf')) {
            overloadInstanceOfChecks(HTMLScriptElement);
        }

        if (getFeatureSettingEnabled(featureName, args, 'injectGlobalStyles')) {
            injectGlobalStyles(`
            ddg-runtime-checks {
                display: none;
            }
        `);
        }
    }

    var runtimeChecks = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$2,
        load: load
    });

    /**
     * Fixes incorrect sizing value for outerHeight and outerWidth
     */
    function windowSizingFix () {
        if (window.outerHeight !== 0 && window.outerWidth !== 0) {
            return
        }
        window.outerHeight = window.innerHeight;
        window.outerWidth = window.innerWidth;
    }

    /**
     * Add missing navigator.credentials API
     */
    function navigatorCredentialsFix () {
        try {
            if ('credentials' in navigator && 'get' in navigator.credentials) {
                return
            }
            const value = {
                get () {
                    return Promise.reject(new Error())
                }
            };
            defineProperty(Navigator.prototype, 'credentials', {
                value,
                configurable: true,
                enumerable: true
            });
        } catch {
            // Ignore exceptions that could be caused by conflicting with other extensions
        }
    }

    function safariObjectFix () {
        try {
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            if (window.safari) {
                return
            }
            defineProperty(window, 'safari', {
                value: {
                },
                configurable: true,
                enumerable: true
            });
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            defineProperty(window.safari, 'pushNotification', {
                value: {
                },
                configurable: true,
                enumerable: true
            });
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            defineProperty(window.safari.pushNotification, 'toString', {
                value: () => { return '[object SafariRemoteNotification]' },
                configurable: true,
                enumerable: true
            });
            class SafariRemoteNotificationPermission {
                constructor () {
                    this.deviceToken = null;
                    this.permission = 'denied';
                }
            }
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            defineProperty(window.safari.pushNotification, 'permission', {
                value: (name) => {
                    return new SafariRemoteNotificationPermission()
                },
                configurable: true,
                enumerable: true
            });
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            defineProperty(window.safari.pushNotification, 'requestPermission', {
                value: (name, domain, options, callback) => {
                    if (typeof callback === 'function') {
                        callback(new SafariRemoteNotificationPermission());
                        return
                    }
                    const reason = "Invalid 'callback' value passed to safari.pushNotification.requestPermission(). Expected a function.";
                    throw new Error(reason)
                },
                configurable: true,
                enumerable: true
            });
        } catch {
            // Ignore exceptions that could be caused by conflicting with other extensions
        }
    }

    function init$1 (args) {
        const featureName = 'web-compat';
        if (getFeatureSettingEnabled(featureName, args, 'windowSizing')) {
            windowSizingFix();
        }
        if (getFeatureSettingEnabled(featureName, args, 'navigatorCredentials')) {
            navigatorCredentialsFix();
        }
        if (getFeatureSettingEnabled(featureName, args, 'safariObject')) {
            safariObjectFix();
        }
    }

    var webCompat = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init$1
    });

    /* global Bluetooth, Geolocation, HID, Serial, USB */

    function init () {
        const featureName = 'windows-permission-usage';

        const Permission = {
            Geolocation: 'geolocation',
            Camera: 'camera',
            Microphone: 'microphone'
        };

        const Status = {
            Inactive: 'inactive',
            Accessed: 'accessed',
            Active: 'active',
            Paused: 'paused'
        };

        const isFrameInsideFrame = window.self !== window.top && window.parent !== window.top;

        function windowsPostMessage (name, data) {
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            window.chrome.webview.postMessage({
                Feature: 'Permissions',
                Name: name,
                Data: data
            });
        }

        function signalPermissionStatus (permission, status) {
            windowsPostMessage('PermissionStatusMessage', { permission, status });
            console.debug(`Permission '${permission}' is ${status}`);
        }

        let pauseWatchedPositions = false;
        const watchedPositions = new Set();
        // proxy for navigator.geolocation.watchPosition -> show red geolocation indicator
        const watchPositionProxy = new DDGProxy(featureName, Geolocation.prototype, 'watchPosition', {
            apply (target, thisArg, args) {
                if (isFrameInsideFrame) {
                    // we can't communicate with iframes inside iframes -> deny permission instead of putting users at risk
                    throw new DOMException('Permission denied')
                }

                const successHandler = args[0];
                args[0] = function (position) {
                    if (pauseWatchedPositions) {
                        signalPermissionStatus(Permission.Geolocation, Status.Paused);
                    } else {
                        signalPermissionStatus(Permission.Geolocation, Status.Active);
                        successHandler?.(position);
                    }
                };
                const id = DDGReflect.apply(target, thisArg, args);
                watchedPositions.add(id);
                return id
            }
        });
        watchPositionProxy.overload();

        // proxy for navigator.geolocation.clearWatch -> clear red geolocation indicator
        const clearWatchProxy = new DDGProxy(featureName, Geolocation.prototype, 'clearWatch', {
            apply (target, thisArg, args) {
                DDGReflect.apply(target, thisArg, args);
                if (args[0] && watchedPositions.delete(args[0]) && watchedPositions.size === 0) {
                    signalPermissionStatus(Permission.Geolocation, Status.Inactive);
                }
            }
        });
        clearWatchProxy.overload();

        // proxy for navigator.geolocation.getCurrentPosition -> normal geolocation indicator
        const getCurrentPositionProxy = new DDGProxy(featureName, Geolocation.prototype, 'getCurrentPosition', {
            apply (target, thisArg, args) {
                const successHandler = args[0];
                args[0] = function (position) {
                    signalPermissionStatus(Permission.Geolocation, Status.Accessed);
                    successHandler?.(position);
                };
                return DDGReflect.apply(target, thisArg, args)
            }
        });
        getCurrentPositionProxy.overload();

        const userMediaStreams = new Set();
        const videoTracks = new Set();
        const audioTracks = new Set();

        // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
        function getTracks (permission) {
            switch (permission) {
            case Permission.Camera:
                return videoTracks
            case Permission.Microphone:
                return audioTracks
            }
        }

        function stopTracks (streamTracks) {
            streamTracks?.forEach(track => track.stop());
        }

        function clearAllGeolocationWatch () {
            watchedPositions.forEach(id => navigator.geolocation.clearWatch(id));
        }

        function pause (permission) {
            switch (permission) {
            case Permission.Camera:
            case Permission.Microphone: {
                const streamTracks = getTracks(permission);
                streamTracks?.forEach(track => {
                    track.enabled = false;
                });
                break
            }
            case Permission.Geolocation:
                pauseWatchedPositions = true;
                signalPermissionStatus(Permission.Geolocation, Status.Paused);
                break
            }
        }

        function resume (permission) {
            switch (permission) {
            case Permission.Camera:
            case Permission.Microphone: {
                const streamTracks = getTracks(permission);
                streamTracks?.forEach(track => {
                    track.enabled = true;
                });
                break
            }
            case Permission.Geolocation:
                pauseWatchedPositions = false;
                signalPermissionStatus(Permission.Geolocation, Status.Active);
                break
            }
        }

        function stop (permission) {
            switch (permission) {
            case Permission.Camera:
                stopTracks(videoTracks);
                break
            case Permission.Microphone:
                stopTracks(audioTracks);
                break
            case Permission.Geolocation:
                pauseWatchedPositions = false;
                clearAllGeolocationWatch();
                break
            }
        }

        function monitorTrack (track) {
            if (track.readyState === 'ended') return

            if (track.kind === 'video' && !videoTracks.has(track)) {
                console.debug(`New video stream track ${track.id}`);
                track.addEventListener('ended', videoTrackEnded);
                track.addEventListener('mute', signalVideoTracksState);
                track.addEventListener('unmute', signalVideoTracksState);
                videoTracks.add(track);
            } else if (track.kind === 'audio' && !audioTracks.has(track)) {
                console.debug(`New audio stream track ${track.id}`);
                track.addEventListener('ended', audioTrackEnded);
                track.addEventListener('mute', signalAudioTracksState);
                track.addEventListener('unmute', signalAudioTracksState);
                audioTracks.add(track);
            }
        }

        function handleTrackEnded (track) {
            if (track.kind === 'video' && videoTracks.has(track)) {
                console.debug(`Video stream track ${track.id} ended`);
                track.removeEventListener('ended', videoTrackEnded);
                track.removeEventListener('mute', signalVideoTracksState);
                track.removeEventListener('unmute', signalVideoTracksState);
                videoTracks.delete(track);
                signalVideoTracksState();
            } else if (track.kind === 'audio' && audioTracks.has(track)) {
                console.debug(`Audio stream track ${track.id} ended`);
                track.removeEventListener('ended', audioTrackEnded);
                track.removeEventListener('mute', signalAudioTracksState);
                track.removeEventListener('unmute', signalAudioTracksState);
                audioTracks.delete(track);
                signalAudioTracksState();
            }
        }

        function videoTrackEnded (e) {
            handleTrackEnded(e.target);
        }

        function audioTrackEnded (e) {
            handleTrackEnded(e.target);
        }

        function signalTracksState (permission) {
            const tracks = getTracks(permission);
            if (!tracks) return

            const allTrackCount = tracks.size;
            if (allTrackCount === 0) {
                signalPermissionStatus(permission, Status.Inactive);
                return
            }

            let mutedTrackCount = 0;
            tracks.forEach(track => {
                mutedTrackCount += ((!track.enabled || track.muted) ? 1 : 0);
            });
            if (mutedTrackCount === allTrackCount) {
                signalPermissionStatus(permission, Status.Paused);
            } else {
                if (mutedTrackCount > 0) {
                    console.debug(`Some ${permission} tracks are still active: ${allTrackCount - mutedTrackCount}/${allTrackCount}`);
                }
                signalPermissionStatus(permission, Status.Active);
            }
        }

        let signalVideoTracksStateTimer;
        function signalVideoTracksState () {
            clearTimeout(signalVideoTracksStateTimer);
            signalVideoTracksStateTimer = setTimeout(() => signalTracksState(Permission.Camera), 100);
        }

        let signalAudioTracksStateTimer;
        function signalAudioTracksState () {
            clearTimeout(signalAudioTracksStateTimer);
            signalAudioTracksStateTimer = setTimeout(() => signalTracksState(Permission.Microphone), 100);
        }

        // proxy for track.stop -> clear camera/mic indicator manually here because no ended event raised this way
        const stopTrackProxy = new DDGProxy(featureName, MediaStreamTrack.prototype, 'stop', {
            apply (target, thisArg, args) {
                handleTrackEnded(thisArg);
                return DDGReflect.apply(target, thisArg, args)
            }
        });
        stopTrackProxy.overload();

        // proxy for track.clone -> monitor the cloned track
        const cloneTrackProxy = new DDGProxy(featureName, MediaStreamTrack.prototype, 'clone', {
            apply (target, thisArg, args) {
                const clonedTrack = DDGReflect.apply(target, thisArg, args);
                if (clonedTrack && (videoTracks.has(thisArg) || audioTracks.has(thisArg))) {
                    console.debug(`Media stream track ${thisArg.id} has been cloned to track ${clonedTrack.id}`);
                    monitorTrack(clonedTrack);
                }
                return clonedTrack
            }
        });
        cloneTrackProxy.overload();

        // override MediaStreamTrack.enabled -> update active/paused status when enabled is set
        const trackEnabledPropertyDescriptor = Object.getOwnPropertyDescriptor(MediaStreamTrack.prototype, 'enabled');
        defineProperty(MediaStreamTrack.prototype, 'enabled', {
            configurable: trackEnabledPropertyDescriptor.configurable,
            enumerable: trackEnabledPropertyDescriptor.enumerable,
            get: function () {
                return trackEnabledPropertyDescriptor.get.bind(this)()
            },
            set: function (value) {
                const result = trackEnabledPropertyDescriptor.set.bind(this)(...arguments);
                if (videoTracks.has(this)) {
                    signalVideoTracksState();
                } else if (audioTracks.has(this)) {
                    signalAudioTracksState();
                }
                return result
            }
        });

        // proxy for get*Tracks methods -> needed to monitor tracks returned by saved media stream coming for MediaDevices.getUserMedia
        const getTracksMethodNames = ['getTracks', 'getAudioTracks', 'getVideoTracks'];
        for (const methodName of getTracksMethodNames) {
            const getTracksProxy = new DDGProxy(featureName, MediaStream.prototype, methodName, {
                apply (target, thisArg, args) {
                    const tracks = DDGReflect.apply(target, thisArg, args);
                    if (userMediaStreams.has(thisArg)) {
                        tracks.forEach(monitorTrack);
                    }
                    return tracks
                }
            });
            getTracksProxy.overload();
        }

        // proxy for MediaStream.clone -> needed to monitor cloned MediaDevices.getUserMedia streams
        const cloneMediaStreamProxy = new DDGProxy(featureName, MediaStream.prototype, 'clone', {
            apply (target, thisArg, args) {
                const clonedStream = DDGReflect.apply(target, thisArg, args);
                if (userMediaStreams.has(thisArg)) {
                    console.debug(`User stream ${thisArg.id} has been cloned to stream ${clonedStream.id}`);
                    userMediaStreams.add(clonedStream);
                }
                return clonedStream
            }
        });
        cloneMediaStreamProxy.overload();

        // proxy for navigator.mediaDevices.getUserMedia -> show red camera/mic indicators
        if (MediaDevices) {
            const getUserMediaProxy = new DDGProxy(featureName, MediaDevices.prototype, 'getUserMedia', {
                apply (target, thisArg, args) {
                    if (isFrameInsideFrame) {
                        // we can't communicate with iframes inside iframes -> deny permission instead of putting users at risk
                        return Promise.reject(new DOMException('Permission denied'))
                    }

                    const videoRequested = args[0]?.video;
                    const audioRequested = args[0]?.audio;

                    if (videoRequested && (videoRequested.pan || videoRequested.tilt || videoRequested.zoom)) {
                        // WebView2 doesn't support acquiring pan-tilt-zoom from its API at the moment
                        return Promise.reject(new DOMException('Pan-tilt-zoom is not supported'))
                    }

                    return DDGReflect.apply(target, thisArg, args).then(function (stream) {
                        console.debug(`User stream ${stream.id} has been acquired`);
                        userMediaStreams.add(stream);
                        if (videoRequested) {
                            const newVideoTracks = stream.getVideoTracks();
                            if (newVideoTracks?.length > 0) {
                                signalPermissionStatus(Permission.Camera, Status.Active);
                            }
                            newVideoTracks.forEach(monitorTrack);
                        }

                        if (audioRequested) {
                            const newAudioTracks = stream.getAudioTracks();
                            if (newAudioTracks?.length > 0) {
                                signalPermissionStatus(Permission.Microphone, Status.Active);
                            }
                            newAudioTracks.forEach(monitorTrack);
                        }
                        return stream
                    })
                }
            });
            getUserMediaProxy.overload();
        }

        function performAction (action, permission) {
            if (action && permission) {
                switch (action) {
                case 'pause':
                    pause(permission);
                    break
                case 'resume':
                    resume(permission);
                    break
                case 'stop':
                    stop(permission);
                    break
                }
            }
        }

        // handle actions from browser
        // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
        window.chrome.webview.addEventListener('message', function ({ data }) {
            if (data?.action && data?.permission) {
                performAction(data?.action, data?.permission);
            }
        });

        // these permissions cannot be disabled using WebView2 or DevTools protocol
        const permissionsToDisable = [
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            { name: 'Bluetooth', prototype: Bluetooth.prototype, method: 'requestDevice', isPromise: true },
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            { name: 'USB', prototype: USB.prototype, method: 'requestDevice', isPromise: true },
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            { name: 'Serial', prototype: Serial.prototype, method: 'requestPort', isPromise: true },
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            { name: 'HID', prototype: HID.prototype, method: 'requestDevice', isPromise: true },
            { name: 'Protocol handler', prototype: Navigator.prototype, method: 'registerProtocolHandler', isPromise: false },
            { name: 'MIDI', prototype: Navigator.prototype, method: 'requestMIDIAccess', isPromise: true }
        ];
        for (const { name, prototype, method, isPromise } of permissionsToDisable) {
            try {
                const proxy = new DDGProxy(featureName, prototype, method, {
                    apply () {
                        if (isPromise) {
                            return Promise.reject(new DOMException('Permission denied'))
                        } else {
                            throw new DOMException('Permission denied')
                        }
                    }
                });
                proxy.overload();
            } catch (error) {
                console.info(`Could not disable access to ${name} because of error`, error);
            }
        }

        // these permissions can be disabled using DevTools protocol but it's not reliable and can throw exception sometimes
        const permissionsToDelete = [
            { name: 'Idle detection', permission: 'IdleDetector' },
            { name: 'NFC', permission: 'NDEFReader' },
            { name: 'Orientation', permission: 'ondeviceorientation' },
            { name: 'Motion', permission: 'ondevicemotion' }
        ];
        for (const { permission } of permissionsToDelete) {
            if (permission in window) {
                delete window[permission];
            }
        }
    }

    var windowsPermissionUsage = /*#__PURE__*/Object.freeze({
        __proto__: null,
        init: init
    });

})();

