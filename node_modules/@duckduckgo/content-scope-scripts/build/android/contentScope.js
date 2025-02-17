/*! © DuckDuckGo ContentScopeScripts protections https://github.com/duckduckgo/content-scope-scripts/ */
(function () {
    'use strict';

    /* eslint-disable no-redeclare */
    const Set$1 = globalThis.Set;
    const Reflect$1 = globalThis.Reflect;
    const customElementsGet = globalThis.customElements?.get.bind(globalThis.customElements);
    const customElementsDefine = globalThis.customElements?.define.bind(globalThis.customElements);
    const getOwnPropertyDescriptor = Object.getOwnPropertyDescriptor;
    const getOwnPropertyDescriptors = Object.getOwnPropertyDescriptors;
    const toString = Object.prototype.toString;
    const objectKeys = Object.keys;
    const objectEntries = Object.entries;
    const objectDefineProperty = Object.defineProperty;
    const URL$1 = globalThis.URL;
    const Proxy$1 = globalThis.Proxy;
    const functionToString = Function.prototype.toString;
    const TypeError$1 = globalThis.TypeError;
    const Symbol = globalThis.Symbol;
    const hasOwnProperty = Object.prototype.hasOwnProperty;
    const dispatchEvent = globalThis.dispatchEvent?.bind(globalThis);
    const addEventListener = globalThis.addEventListener?.bind(globalThis);
    const removeEventListener = globalThis.removeEventListener?.bind(globalThis);
    const CustomEvent$1 = globalThis.CustomEvent;
    const Promise$1 = globalThis.Promise;
    const String$1 = globalThis.String;
    const Map$1 = globalThis.Map;
    const Error$2 = globalThis.Error;
    const randomUUID = globalThis.crypto?.randomUUID?.bind(globalThis.crypto);

    var capturedGlobals = /*#__PURE__*/Object.freeze({
        __proto__: null,
        CustomEvent: CustomEvent$1,
        Error: Error$2,
        Map: Map$1,
        Promise: Promise$1,
        Proxy: Proxy$1,
        Reflect: Reflect$1,
        Set: Set$1,
        String: String$1,
        Symbol: Symbol,
        TypeError: TypeError$1,
        URL: URL$1,
        addEventListener: addEventListener,
        customElementsDefine: customElementsDefine,
        customElementsGet: customElementsGet,
        dispatchEvent: dispatchEvent,
        functionToString: functionToString,
        getOwnPropertyDescriptor: getOwnPropertyDescriptor,
        getOwnPropertyDescriptors: getOwnPropertyDescriptors,
        hasOwnProperty: hasOwnProperty,
        objectDefineProperty: objectDefineProperty,
        objectEntries: objectEntries,
        objectKeys: objectKeys,
        randomUUID: randomUUID,
        removeEventListener: removeEventListener,
        toString: toString
    });

    /* eslint-disable no-redeclare, no-global-assign */

    // Only use globalThis for testing this breaks window.wrappedJSObject code in Firefox

    let globalObj = typeof window === 'undefined' ? globalThis : window;
    let Error$1 = globalObj.Error;
    let messageSecret;

    // save a reference to original CustomEvent amd dispatchEvent so they can't be overriden to forge messages
    const OriginalCustomEvent = typeof CustomEvent === 'undefined' ? null : CustomEvent;
    const originalWindowDispatchEvent = typeof window === 'undefined' ? null : window.dispatchEvent.bind(window);
    function registerMessageSecret(secret) {
        messageSecret = secret;
    }

    /**
     * @returns {HTMLElement} the element to inject the script into
     */
    function getInjectionElement() {
        return document.head || document.documentElement;
    }

    /**
     * Creates a script element with the given code to avoid Firefox CSP restrictions.
     * @param {string} css
     * @returns {HTMLLinkElement | HTMLStyleElement}
     */
    function createStyleElement(css) {
        const style = document.createElement('style');
        style.innerText = css;
        return style;
    }

    /**
     * Injects a script into the page, avoiding CSP restrictions if possible.
     */
    function injectGlobalStyles(css) {
        const style = createStyleElement(css);
        getInjectionElement().appendChild(style);
    }

    // linear feedback shift register to find a random approximation
    function nextRandom(v) {
        return Math.abs((v >> 1) | (((v << 62) ^ (v << 61)) & (2147483647 << 62)));
    }

    const exemptionLists = {};
    function shouldExemptUrl(type, url) {
        for (const regex of exemptionLists[type]) {
            if (regex.test(url)) {
                return true;
            }
        }
        return false;
    }

    let debug = false;

    function initStringExemptionLists(args) {
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
    function isBeingFramed() {
        if (globalThis.location && 'ancestorOrigins' in globalThis.location) {
            return globalThis.location.ancestorOrigins.length > 0;
        }
        return globalThis.top !== globalThis.window;
    }

    /**
     * Best guess effort of the tabs hostname; where possible always prefer the args.site.domain
     * @returns {string|null} inferred tab hostname
     */
    function getTabHostname() {
        let framingOrigin = null;
        try {
            // @ts-expect-error - globalThis.top is possibly 'null' here
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
            // @ts-expect-error - framingOrigin is possibly 'null' here
            framingOrigin = new URL(framingOrigin).hostname;
        } catch {
            framingOrigin = null;
        }
        return framingOrigin;
    }

    /**
     * Returns true if hostname is a subset of exceptionDomain or an exact match.
     * @param {string} hostname
     * @param {string} exceptionDomain
     * @returns {boolean}
     */
    function matchHostname(hostname, exceptionDomain) {
        return hostname === exceptionDomain || hostname.endsWith(`.${exceptionDomain}`);
    }

    const lineTest = /(\()?(https?:[^)]+):[0-9]+:[0-9]+(\))?/;
    function getStackTraceUrls(stack) {
        const urls = new Set$1();
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
        return urls;
    }

    function getStackTraceOrigins(stack) {
        const urls = getStackTraceUrls(stack);
        const origins = new Set$1();
        for (const url of urls) {
            origins.add(url.hostname);
        }
        return origins;
    }

    // Checks the stack trace if there are known libraries that are broken.
    function shouldExemptMethod(type) {
        // Short circuit stack tracing if we don't have checks
        if (!(type in exemptionLists) || exemptionLists[type].length === 0) {
            return false;
        }
        const stack = getStack();
        const errorFiles = getStackTraceUrls(stack);
        for (const path of errorFiles) {
            if (shouldExemptUrl(type, path.href)) {
                return true;
            }
        }
        return false;
    }

    // Iterate through the key, passing an item index and a byte to be modified
    function iterateDataKey(key, callback) {
        let item = key.charCodeAt(0);
        for (const i in key) {
            let byte = key.charCodeAt(i);
            for (let j = 8; j >= 0; j--) {
                const res = callback(item, byte);
                // Exit early if callback returns null
                if (res === null) {
                    return;
                }

                // find next item to perturb
                item = nextRandom(item);

                // Right shift as we use the least significant bit of it
                byte = byte >> 1;
            }
        }
    }

    function isFeatureBroken(args, feature) {
        return isPlatformSpecificFeature(feature)
            ? !args.site.enabledFeatures.includes(feature)
            : args.site.isBroken || args.site.allowlisted || !args.site.enabledFeatures.includes(feature);
    }

    function camelcase(dashCaseText) {
        return dashCaseText.replace(/-(.)/g, (match, letter) => {
            return letter.toUpperCase();
        });
    }

    // We use this method to detect M1 macs and set appropriate API values to prevent sites from detecting fingerprinting protections
    function isAppleSilicon() {
        const canvas = document.createElement('canvas');
        const gl = canvas.getContext('webgl');

        // Best guess if the device is an Apple Silicon
        // https://stackoverflow.com/a/65412357
        // @ts-expect-error - Object is possibly 'null'
        return gl.getSupportedExtensions().indexOf('WEBGL_compressed_texture_etc') !== -1;
    }

    /**
     * Take configSeting which should be an array of possible values.
     * If a value contains a criteria that is a match for this environment then return that value.
     * Otherwise return the first value that doesn't have a criteria.
     *
     * @param {ConfigSetting[]} configSetting - Config setting which should contain a list of possible values
     * @returns {*|undefined} - The value from the list that best matches the criteria in the config
     */
    function processAttrByCriteria(configSetting) {
        let bestOption;
        for (const item of configSetting) {
            if (item.criteria) {
                if (item.criteria.arch === 'AppleSilicon' && isAppleSilicon()) {
                    bestOption = item;
                    break;
                }
            } else {
                bestOption = item;
            }
        }

        return bestOption;
    }

    const functionMap = {
        /** Useful for debugging APIs in the wild, shouldn't be used */
        debug: (...args) => {
            console.log('debugger', ...args);
            // eslint-disable-next-line no-debugger
            debugger;
        },

        noop: () => {},
    };

    /**
     * @typedef {object} ConfigSetting
     * @property {'undefined' | 'number' | 'string' | 'function' | 'boolean' | 'null' | 'array' | 'object'} type
     * @property {string} [functionName]
     * @property {boolean | string | number} value
     * @property {object} [criteria]
     * @property {string} criteria.arch
     */

    /**
     * Processes a structured config setting and returns the value according to its type
     * @param {ConfigSetting} configSetting
     * @param {*} [defaultValue]
     * @returns
     */
    function processAttr(configSetting, defaultValue) {
        if (configSetting === undefined) {
            return defaultValue;
        }

        const configSettingType = typeof configSetting;
        switch (configSettingType) {
            case 'object':
                if (Array.isArray(configSetting)) {
                    configSetting = processAttrByCriteria(configSetting);
                    if (configSetting === undefined) {
                        return defaultValue;
                    }
                }

                if (!configSetting.type) {
                    return defaultValue;
                }

                if (configSetting.type === 'function') {
                    if (configSetting.functionName && functionMap[configSetting.functionName]) {
                        return functionMap[configSetting.functionName];
                    }
                }

                if (configSetting.type === 'undefined') {
                    return undefined;
                }

                // All JSON expressable types are handled here
                return configSetting.value;
            default:
                return defaultValue;
        }
    }

    function getStack() {
        return new Error$1().stack;
    }

    /**
     * @param {*[]} argsArray
     * @returns {string}
     */
    function debugSerialize(argsArray) {
        const maxSerializedSize = 1000;
        const serializedArgs = argsArray.map((arg) => {
            try {
                const serializableOut = JSON.stringify(arg);
                if (serializableOut.length > maxSerializedSize) {
                    return `<truncated, length: ${serializableOut.length}, value: ${serializableOut.substring(0, maxSerializedSize)}...>`;
                }
                return serializableOut;
            } catch (e) {
                // Sometimes this happens when we can't serialize an object to string but we still wish to log it and make other args readable
                return '<unserializable>';
            }
        });
        return JSON.stringify(serializedArgs);
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
         * @param {import('./content-feature').default} feature
         * @param {P} objectScope
         * @param {string} property
         * @param {ProxyObject<P>} proxyObject
         */
        constructor(feature, objectScope, property, proxyObject) {
            this.objectScope = objectScope;
            this.property = property;
            this.feature = feature;
            this.featureName = feature.name;
            this.camelFeatureName = camelcase(this.featureName);
            const outputHandler = (...args) => {
                this.feature.addDebugFlag();
                const isExempt = shouldExemptMethod(this.camelFeatureName);
                // Keep this here as getStack() is expensive
                if (debug) {
                    postDebugMessage(this.camelFeatureName, {
                        isProxy: true,
                        action: isExempt ? 'ignore' : 'restrict',
                        kind: this.property,
                        documentUrl: document.location.href,
                        stack: getStack(),
                        args: debugSerialize(args[2]),
                    });
                }
                // The normal return value
                if (isExempt) {
                    return DDGReflect.apply(args[0], args[1], args[2]);
                }
                return proxyObject.apply(...args);
            };
            const getMethod = (target, prop, receiver) => {
                this.feature.addDebugFlag();
                if (prop === 'toString') {
                    const method = Reflect.get(target, prop, receiver).bind(target);
                    Object.defineProperty(method, 'toString', {
                        value: String.toString.bind(String.toString),
                        enumerable: false,
                    });
                    return method;
                }
                return DDGReflect.get(target, prop, receiver);
            };
            this._native = objectScope[property];
            const handler = {};
            handler.apply = outputHandler;
            handler.get = getMethod;
            this.internal = new globalObj.Proxy(objectScope[property], handler);
        }

        // Actually apply the proxy to the native property
        overload() {
            this.objectScope[this.property] = this.internal;
        }

        overloadDescriptor() {
            // TODO: this is not always correct! Use wrap* or shim* methods instead
            this.feature.defineProperty(this.objectScope, this.property, {
                value: this.internal,
                writable: true,
                enumerable: true,
                configurable: true,
            });
        }
    }

    const maxCounter = new Map();
    function numberOfTimesDebugged(feature) {
        if (!maxCounter.has(feature)) {
            maxCounter.set(feature, 1);
        } else {
            maxCounter.set(feature, maxCounter.get(feature) + 1);
        }
        return maxCounter.get(feature);
    }

    const DEBUG_MAX_TIMES = 5000;

    function postDebugMessage(feature, message, allowNonDebug = false) {
        if (!debug && !allowNonDebug) {
            return;
        }
        if (numberOfTimesDebugged(feature) > DEBUG_MAX_TIMES) {
            return;
        }
        if (message.stack) {
            const scriptOrigins = [...getStackTraceOrigins(message.stack)];
            message.scriptOrigins = scriptOrigins;
        }
        globalObj.postMessage({
            action: feature,
            message,
        });
    }

    const DDGPromise = globalObj.Promise;
    const DDGReflect = globalObj.Reflect;

    /**
     * @param {string | null} topLevelHostname
     * @param {object[]} featureList
     * @returns {boolean}
     */
    function isUnprotectedDomain(topLevelHostname, featureList) {
        let unprotectedDomain = false;
        if (!topLevelHostname) {
            return false;
        }
        const domainParts = topLevelHostname.split('.');

        // walk up the domain to see if it's unprotected
        while (domainParts.length > 1 && !unprotectedDomain) {
            const partialDomain = domainParts.join('.');

            unprotectedDomain = featureList.filter((domain) => domain.domain === partialDomain).length > 0;

            domainParts.shift();
        }

        return unprotectedDomain;
    }

    /**
     * @typedef {object} Platform
     * @property {'ios' | 'macos' | 'extension' | 'android' | 'windows'} name
     * @property {string | number } [version]
     */

    /**
     * @typedef {object} UserPreferences
     * @property {Platform} platform
     * @property {boolean} [debug]
     * @property {boolean} [globalPrivacyControl]
     * @property {number} [versionNumber] - Android version number only
     * @property {string} [versionString] - Non Android version string
     * @property {string} sessionKey
     */

    /**
     * Used to inialize extension code in the load phase
     */
    function computeLimitedSiteObject() {
        const topLevelHostname = getTabHostname();
        return {
            domain: topLevelHostname,
        };
    }

    /**
     * Expansion point to add platform specific versioning logic
     * @param {UserPreferences} preferences
     * @returns {string | number | undefined}
     */
    function getPlatformVersion(preferences) {
        if (preferences.versionNumber) {
            return preferences.versionNumber;
        }
        if (preferences.versionString) {
            return preferences.versionString;
        }
        return undefined;
    }

    function parseVersionString(versionString) {
        return versionString.split('.').map(Number);
    }

    /**
     * @param {string} minVersionString
     * @param {string} applicationVersionString
     * @returns {boolean}
     */
    function satisfiesMinVersion(minVersionString, applicationVersionString) {
        const minVersions = parseVersionString(minVersionString);
        const currentVersions = parseVersionString(applicationVersionString);
        const maxLength = Math.max(minVersions.length, currentVersions.length);
        for (let i = 0; i < maxLength; i++) {
            const minNumberPart = minVersions[i] || 0;
            const currentVersionPart = currentVersions[i] || 0;
            if (currentVersionPart > minNumberPart) {
                return true;
            }
            if (currentVersionPart < minNumberPart) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param {string | number | undefined} minSupportedVersion
     * @param {string | number | undefined} currentVersion
     * @returns {boolean}
     */
    function isSupportedVersion(minSupportedVersion, currentVersion) {
        if (typeof currentVersion === 'string' && typeof minSupportedVersion === 'string') {
            if (satisfiesMinVersion(minSupportedVersion, currentVersion)) {
                return true;
            }
        } else if (typeof currentVersion === 'number' && typeof minSupportedVersion === 'number') {
            if (minSupportedVersion <= currentVersion) {
                return true;
            }
        }
        return false;
    }

    /**
     * @typedef RemoteConfig
     * @property {Record<string, { state: string; settings: any; exceptions: { domain: string }[], minSupportedVersion?: string|number }>} features
     * @property {string[]} unprotectedTemporary
     */

    /**
     * @param {RemoteConfig} data
     * @param {string[]} userList
     * @param {UserPreferences} preferences
     * @param {string[]} platformSpecificFeatures
     */
    function processConfig(data, userList, preferences, platformSpecificFeatures = []) {
        const topLevelHostname = getTabHostname();
        const site = computeLimitedSiteObject();
        const allowlisted = userList.filter((domain) => domain === topLevelHostname).length > 0;
        /** @type {Record<string, any>} */
        const output = { ...preferences };
        if (output.platform) {
            const version = getPlatformVersion(preferences);
            if (version) {
                output.platform.version = version;
            }
        }
        const enabledFeatures = computeEnabledFeatures(data, topLevelHostname, preferences.platform?.version, platformSpecificFeatures);
        const isBroken = isUnprotectedDomain(topLevelHostname, data.unprotectedTemporary);
        output.site = Object.assign(site, {
            isBroken,
            allowlisted,
            enabledFeatures,
        });

        // Copy feature settings from remote config to preferences object
        output.featureSettings = parseFeatureSettings(data, enabledFeatures);
        output.trackerLookup = {"org":{"cdn77":{"rsc":{"1558334541":1}},"adsrvr":1,"ampproject":1,"browser-update":1,"flowplayer":1,"privacy-center":1,"webvisor":1,"framasoft":1,"do-not-tracker":1,"trackersimulator":1},"io":{"1dmp":1,"1rx":1,"4dex":1,"adnami":1,"aidata":1,"arcspire":1,"bidr":1,"branch":1,"center":1,"cloudimg":1,"concert":1,"connectad":1,"cordial":1,"dcmn":1,"extole":1,"getblue":1,"hbrd":1,"instana":1,"karte":1,"leadsmonitor":1,"litix":1,"lytics":1,"marchex":1,"mediago":1,"mrf":1,"narrative":1,"ntv":1,"optad360":1,"oracleinfinity":1,"oribi":1,"p-n":1,"personalizer":1,"pghub":1,"piano":1,"powr":1,"pzz":1,"searchspring":1,"segment":1,"siteimproveanalytics":1,"sspinc":1,"t13":1,"webgains":1,"wovn":1,"yellowblue":1,"zprk":1,"axept":1,"akstat":1,"clarium":1,"hotjar":1},"com":{"2020mustang":1,"33across":1,"360yield":1,"3lift":1,"4dsply":1,"4strokemedia":1,"8353e36c2a":1,"a-mx":1,"a2z":1,"aamsitecertifier":1,"absorbingband":1,"abstractedauthority":1,"abtasty":1,"acexedge":1,"acidpigs":1,"acsbapp":1,"acuityplatform":1,"ad-score":1,"ad-stir":1,"adalyser":1,"adapf":1,"adara":1,"adblade":1,"addthis":1,"addtoany":1,"adelixir":1,"adentifi":1,"adextrem":1,"adgrx":1,"adhese":1,"adition":1,"adkernel":1,"adlightning":1,"adlooxtracking":1,"admanmedia":1,"admedo":1,"adnium":1,"adnxs-simple":1,"adnxs":1,"adobedtm":1,"adotmob":1,"adpone":1,"adpushup":1,"adroll":1,"adrta":1,"ads-twitter":1,"ads3-adnow":1,"adsafeprotected":1,"adstanding":1,"adswizz":1,"adtdp":1,"adtechus":1,"adtelligent":1,"adthrive":1,"adtlgc":1,"adtng":1,"adultfriendfinder":1,"advangelists":1,"adventive":1,"adventori":1,"advertising":1,"aegpresents":1,"affinity":1,"affirm":1,"agilone":1,"agkn":1,"aimbase":1,"albacross":1,"alcmpn":1,"alexametrics":1,"alicdn":1,"alikeaddition":1,"aliveachiever":1,"aliyuncs":1,"alluringbucket":1,"aloofvest":1,"amazon-adsystem":1,"amazon":1,"ambiguousafternoon":1,"amplitude":1,"analytics-egain":1,"aniview":1,"annoyedairport":1,"annoyingclover":1,"anyclip":1,"anymind360":1,"app-us1":1,"appboycdn":1,"appdynamics":1,"appsflyer":1,"aralego":1,"aspiringattempt":1,"aswpsdkus":1,"atemda":1,"att":1,"attentivemobile":1,"attractionbanana":1,"audioeye":1,"audrte":1,"automaticside":1,"avanser":1,"avmws":1,"aweber":1,"aweprt":1,"azure":1,"b0e8":1,"badgevolcano":1,"bagbeam":1,"ballsbanana":1,"bandborder":1,"batch":1,"bawdybalance":1,"bc0a":1,"bdstatic":1,"bedsberry":1,"beginnerpancake":1,"benchmarkemail":1,"betweendigital":1,"bfmio":1,"bidtheatre":1,"billowybelief":1,"bimbolive":1,"bing":1,"bizographics":1,"bizrate":1,"bkrtx":1,"blismedia":1,"blogherads":1,"bluecava":1,"bluekai":1,"blushingbread":1,"boatwizard":1,"boilingcredit":1,"boldchat":1,"booking":1,"borderfree":1,"bounceexchange":1,"brainlyads":1,"brand-display":1,"brandmetrics":1,"brealtime":1,"brightfunnel":1,"brightspotcdn":1,"btloader":1,"btstatic":1,"bttrack":1,"btttag":1,"bumlam":1,"butterbulb":1,"buttonladybug":1,"buzzfeed":1,"buzzoola":1,"byside":1,"c3tag":1,"cabnnr":1,"calculatorstatement":1,"callrail":1,"calltracks":1,"capablecup":1,"captcha-delivery":1,"carpentercomparison":1,"cartstack":1,"carvecakes":1,"casalemedia":1,"cattlecommittee":1,"cdninstagram":1,"cdnwidget":1,"channeladvisor":1,"chargecracker":1,"chartbeat":1,"chatango":1,"chaturbate":1,"cheqzone":1,"cherriescare":1,"chickensstation":1,"childlikecrowd":1,"childlikeform":1,"chocolateplatform":1,"cintnetworks":1,"circlelevel":1,"ck-ie":1,"clcktrax":1,"cleanhaircut":1,"clearbit":1,"clearbitjs":1,"clickagy":1,"clickcease":1,"clickcertain":1,"clicktripz":1,"clientgear":1,"cloudflare":1,"cloudflareinsights":1,"cloudflarestream":1,"cobaltgroup":1,"cobrowser":1,"cognitivlabs":1,"colossusssp":1,"combativecar":1,"comm100":1,"googleapis":{"commondatastorage":1,"imasdk":1,"storage":1,"fonts":1,"maps":1,"www":1},"company-target":1,"condenastdigital":1,"confusedcart":1,"connatix":1,"contextweb":1,"conversionruler":1,"convertkit":1,"convertlanguage":1,"cootlogix":1,"coveo":1,"cpmstar":1,"cquotient":1,"crabbychin":1,"cratecamera":1,"crazyegg":1,"creative-serving":1,"creativecdn":1,"criteo":1,"crowdedmass":1,"crowdriff":1,"crownpeak":1,"crsspxl":1,"ctnsnet":1,"cudasvc":1,"cuddlethehyena":1,"cumbersomecarpenter":1,"curalate":1,"curvedhoney":1,"cushiondrum":1,"cutechin":1,"cxense":1,"d28dc30335":1,"dailymotion":1,"damdoor":1,"dampdock":1,"dapperfloor":1,"datadoghq-browser-agent":1,"decisivebase":1,"deepintent":1,"defybrick":1,"delivra":1,"demandbase":1,"detectdiscovery":1,"devilishdinner":1,"dimelochat":1,"disagreeabledrop":1,"discreetfield":1,"disqus":1,"dmpxs":1,"dockdigestion":1,"dotomi":1,"doubleverify":1,"drainpaste":1,"dramaticdirection":1,"driftt":1,"dtscdn":1,"dtscout":1,"dwin1":1,"dynamics":1,"dynamicyield":1,"dynatrace":1,"ebaystatic":1,"ecal":1,"eccmp":1,"elfsight":1,"elitrack":1,"eloqua":1,"en25":1,"encouragingthread":1,"enormousearth":1,"ensighten":1,"enviousshape":1,"eqads":1,"ero-advertising":1,"esputnik":1,"evergage":1,"evgnet":1,"exdynsrv":1,"exelator":1,"exoclick":1,"exosrv":1,"expansioneggnog":1,"expedia":1,"expertrec":1,"exponea":1,"exponential":1,"extole":1,"ezodn":1,"ezoic":1,"ezoiccdn":1,"facebook":1,"facil-iti":1,"fadewaves":1,"fallaciousfifth":1,"farmergoldfish":1,"fastly-insights":1,"fearlessfaucet":1,"fiftyt":1,"financefear":1,"fitanalytics":1,"five9":1,"fixedfold":1,"fksnk":1,"flashtalking":1,"flipp":1,"flowerstreatment":1,"floweryflavor":1,"flutteringfireman":1,"flux-cdn":1,"foresee":1,"fortunatemark":1,"fouanalytics":1,"fox":1,"fqtag":1,"frailfruit":1,"freezingbuilding":1,"fronttoad":1,"fullstory":1,"functionalfeather":1,"fuzzybasketball":1,"gammamaximum":1,"gbqofs":1,"geetest":1,"geistm":1,"geniusmonkey":1,"geoip-js":1,"getbread":1,"getcandid":1,"getclicky":1,"getdrip":1,"getelevar":1,"getrockerbox":1,"getshogun":1,"getsitecontrol":1,"giraffepiano":1,"glassdoor":1,"gloriousbeef":1,"godpvqnszo":1,"google-analytics":1,"google":1,"googleadservices":1,"googlehosted":1,"googleoptimize":1,"googlesyndication":1,"googletagmanager":1,"googletagservices":1,"gorgeousedge":1,"govx":1,"grainmass":1,"greasysquare":1,"greylabeldelivery":1,"groovehq":1,"growsumo":1,"gstatic":1,"guarantee-cdn":1,"guiltlessbasketball":1,"gumgum":1,"haltingbadge":1,"hammerhearing":1,"handsomelyhealth":1,"harborcaption":1,"hawksearch":1,"amazonaws":{"us-east-2":{"s3":{"hb-obv2":1}}},"heapanalytics":1,"hellobar":1,"hhbypdoecp":1,"hiconversion":1,"highwebmedia":1,"histats":1,"hlserve":1,"hocgeese":1,"hollowafterthought":1,"honorableland":1,"hotjar":1,"hp":1,"hs-banner":1,"htlbid":1,"htplayground":1,"hubspot":1,"ib-ibi":1,"id5-sync":1,"igodigital":1,"iheart":1,"iljmp":1,"illiweb":1,"impactcdn":1,"impactradius-event":1,"impressionmonster":1,"improvedcontactform":1,"improvedigital":1,"imrworldwide":1,"indexww":1,"infolinks":1,"infusionsoft":1,"inmobi":1,"inq":1,"inside-graph":1,"instagram":1,"intentiq":1,"intergient":1,"investingchannel":1,"invocacdn":1,"iperceptions":1,"iplsc":1,"ipredictive":1,"iteratehq":1,"ivitrack":1,"j93557g":1,"jaavnacsdw":1,"jimstatic":1,"journity":1,"js7k":1,"jscache":1,"juiceadv":1,"juicyads":1,"justanswer":1,"justpremium":1,"jwpcdn":1,"kakao":1,"kampyle":1,"kargo":1,"kissmetrics":1,"klarnaservices":1,"klaviyo":1,"knottyswing":1,"krushmedia":1,"ktkjmp":1,"kxcdn":1,"laboredlocket":1,"ladesk":1,"ladsp":1,"laughablelizards":1,"leadsrx":1,"lendingtree":1,"levexis":1,"liadm":1,"licdn":1,"lightboxcdn":1,"lijit":1,"linkedin":1,"linksynergy":1,"list-manage":1,"listrakbi":1,"livechatinc":1,"livejasmin":1,"localytics":1,"loggly":1,"loop11":1,"looseloaf":1,"lovelydrum":1,"lunchroomlock":1,"lwonclbench":1,"macromill":1,"maddeningpowder":1,"mailchimp":1,"mailchimpapp":1,"mailerlite":1,"maillist-manage":1,"marinsm":1,"marketiq":1,"marketo":1,"marphezis":1,"marriedbelief":1,"materialparcel":1,"matheranalytics":1,"mathtag":1,"maxmind":1,"mczbf":1,"measlymiddle":1,"medallia":1,"meddleplant":1,"media6degrees":1,"mediacategory":1,"mediavine":1,"mediawallahscript":1,"medtargetsystem":1,"megpxs":1,"memberful":1,"memorizematch":1,"mentorsticks":1,"metaffiliation":1,"metricode":1,"metricswpsh":1,"mfadsrvr":1,"mgid":1,"micpn":1,"microadinc":1,"minutemedia-prebid":1,"minutemediaservices":1,"mixpo":1,"mkt932":1,"mktoresp":1,"mktoweb":1,"ml314":1,"moatads":1,"mobtrakk":1,"monsido":1,"mookie1":1,"motionflowers":1,"mountain":1,"mouseflow":1,"mpeasylink":1,"mql5":1,"mrtnsvr":1,"murdoog":1,"mxpnl":1,"mybestpro":1,"myregistry":1,"nappyattack":1,"navistechnologies":1,"neodatagroup":1,"nervoussummer":1,"netmng":1,"newrelic":1,"newscgp":1,"nextdoor":1,"ninthdecimal":1,"nitropay":1,"noibu":1,"nondescriptnote":1,"nosto":1,"npttech":1,"ntvpwpush":1,"nuance":1,"nutritiousbean":1,"nxsttv":1,"omappapi":1,"omnisnippet1":1,"omnisrc":1,"omnitagjs":1,"ondemand":1,"oneall":1,"onesignal":1,"onetag-sys":1,"oo-syringe":1,"ooyala":1,"opecloud":1,"opentext":1,"opera":1,"opmnstr":1,"opti-digital":1,"optimicdn":1,"optimizely":1,"optinmonster":1,"optmnstr":1,"optmstr":1,"optnmnstr":1,"optnmstr":1,"osano":1,"otm-r":1,"outbrain":1,"overconfidentfood":1,"ownlocal":1,"pailpatch":1,"panickypancake":1,"panoramicplane":1,"parastorage":1,"pardot":1,"parsely":1,"partplanes":1,"patreon":1,"paypal":1,"pbstck":1,"pcmag":1,"peerius":1,"perfdrive":1,"perfectmarket":1,"permutive":1,"picreel":1,"pinterest":1,"pippio":1,"piwikpro":1,"pixlee":1,"placidperson":1,"pleasantpump":1,"plotrabbit":1,"pluckypocket":1,"pocketfaucet":1,"possibleboats":1,"postaffiliatepro":1,"postrelease":1,"potatoinvention":1,"powerfulcopper":1,"predictplate":1,"prepareplanes":1,"pricespider":1,"priceypies":1,"pricklydebt":1,"profusesupport":1,"proofpoint":1,"protoawe":1,"providesupport":1,"pswec":1,"psychedelicarithmetic":1,"psyma":1,"ptengine":1,"publir":1,"pubmatic":1,"pubmine":1,"pubnation":1,"qualaroo":1,"qualtrics":1,"quantcast":1,"quantserve":1,"quantummetric":1,"quietknowledge":1,"quizzicalpartner":1,"quizzicalzephyr":1,"quora":1,"r42tag":1,"radiateprose":1,"railwayreason":1,"rakuten":1,"rambunctiousflock":1,"rangeplayground":1,"rating-widget":1,"realsrv":1,"rebelswing":1,"reconditerake":1,"reconditerespect":1,"recruitics":1,"reddit":1,"redditstatic":1,"rehabilitatereason":1,"repeatsweater":1,"reson8":1,"resonantrock":1,"resonate":1,"responsiveads":1,"restrainstorm":1,"restructureinvention":1,"retargetly":1,"revcontent":1,"rezync":1,"rfihub":1,"rhetoricalloss":1,"richaudience":1,"righteouscrayon":1,"rightfulfall":1,"riotgames":1,"riskified":1,"rkdms":1,"rlcdn":1,"rmtag":1,"rogersmedia":1,"rokt":1,"route":1,"rtbsystem":1,"rubiconproject":1,"ruralrobin":1,"s-onetag":1,"saambaa":1,"sablesong":1,"sail-horizon":1,"salesforceliveagent":1,"samestretch":1,"sascdn":1,"satisfycork":1,"savoryorange":1,"scarabresearch":1,"scaredsnakes":1,"scaredsong":1,"scaredstomach":1,"scarfsmash":1,"scene7":1,"scholarlyiq":1,"scintillatingsilver":1,"scorecardresearch":1,"screechingstove":1,"screenpopper":1,"scribblestring":1,"sddan":1,"seatsmoke":1,"securedvisit":1,"seedtag":1,"sefsdvc":1,"segment":1,"sekindo":1,"selectivesummer":1,"selfishsnake":1,"servebom":1,"servedbyadbutler":1,"servenobid":1,"serverbid":1,"serving-sys":1,"shakegoldfish":1,"shamerain":1,"shapecomb":1,"shappify":1,"shareaholic":1,"sharethis":1,"sharethrough":1,"shopifyapps":1,"shopperapproved":1,"shrillspoon":1,"sibautomation":1,"sicksmash":1,"signifyd":1,"singroot":1,"site":1,"siteimprove":1,"siteimproveanalytics":1,"sitescout":1,"sixauthority":1,"skillfuldrop":1,"skimresources":1,"skisofa":1,"sli-spark":1,"slickstream":1,"slopesoap":1,"smadex":1,"smartadserver":1,"smashquartz":1,"smashsurprise":1,"smg":1,"smilewanted":1,"smoggysnakes":1,"snapchat":1,"snapkit":1,"snigelweb":1,"socdm":1,"sojern":1,"songsterritory":1,"sonobi":1,"soundstocking":1,"spectacularstamp":1,"speedcurve":1,"sphereup":1,"spiceworks":1,"spookyexchange":1,"spookyskate":1,"spookysleet":1,"sportradarserving":1,"sportslocalmedia":1,"spotxchange":1,"springserve":1,"srvmath":1,"ssl-images-amazon":1,"stackadapt":1,"stakingsmile":1,"statcounter":1,"steadfastseat":1,"steadfastsound":1,"steadfastsystem":1,"steelhousemedia":1,"steepsquirrel":1,"stereotypedsugar":1,"stickyadstv":1,"stiffgame":1,"stingycrush":1,"straightnest":1,"stripchat":1,"strivesquirrel":1,"strokesystem":1,"stupendoussleet":1,"stupendoussnow":1,"stupidscene":1,"sulkycook":1,"sumo":1,"sumologic":1,"sundaysky":1,"superficialeyes":1,"superficialsquare":1,"surveymonkey":1,"survicate":1,"svonm":1,"swankysquare":1,"symantec":1,"taboola":1,"tailtarget":1,"talkable":1,"tamgrt":1,"tangycover":1,"taobao":1,"tapad":1,"tapioni":1,"taptapnetworks":1,"taskanalytics":1,"tealiumiq":1,"techlab-cdn":1,"technoratimedia":1,"techtarget":1,"tediousticket":1,"teenytinyshirt":1,"tendertest":1,"the-ozone-project":1,"theadex":1,"themoneytizer":1,"theplatform":1,"thestar":1,"thinkitten":1,"threetruck":1,"thrtle":1,"tidaltv":1,"tidiochat":1,"tiktok":1,"tinypass":1,"tiqcdn":1,"tiresomethunder":1,"trackjs":1,"traffichaus":1,"trafficjunky":1,"trafmag":1,"travelaudience":1,"treasuredata":1,"tremorhub":1,"trendemon":1,"tribalfusion":1,"trovit":1,"trueleadid":1,"truoptik":1,"truste":1,"trustpilot":1,"trvdp":1,"tsyndicate":1,"tubemogul":1,"turn":1,"tvpixel":1,"tvsquared":1,"tweakwise":1,"twitter":1,"tynt":1,"typicalteeth":1,"u5e":1,"ubembed":1,"uidapi":1,"ultraoranges":1,"unbecominglamp":1,"unbxdapi":1,"undertone":1,"uninterestedquarter":1,"unpkg":1,"unrulymedia":1,"unwieldyhealth":1,"unwieldyplastic":1,"upsellit":1,"urbanairship":1,"usabilla":1,"usbrowserspeed":1,"usemessages":1,"userreport":1,"uservoice":1,"valuecommerce":1,"vengefulgrass":1,"vidazoo":1,"videoplayerhub":1,"vidoomy":1,"viglink":1,"visualwebsiteoptimizer":1,"vivaclix":1,"vk":1,"vlitag":1,"voicefive":1,"volatilevessel":1,"voraciousgrip":1,"voxmedia":1,"vrtcal":1,"w3counter":1,"walkme":1,"warmafterthought":1,"warmquiver":1,"webcontentassessor":1,"webengage":1,"webeyez":1,"webtraxs":1,"webtrends-optimize":1,"webtrends":1,"wgplayer":1,"woosmap":1,"worldoftulo":1,"wpadmngr":1,"wpshsdk":1,"wpushsdk":1,"wsod":1,"wt-safetag":1,"wysistat":1,"xg4ken":1,"xiti":1,"xlirdr":1,"xlivrdr":1,"xnxx-cdn":1,"y-track":1,"yahoo":1,"yandex":1,"yieldmo":1,"yieldoptimizer":1,"yimg":1,"yotpo":1,"yottaa":1,"youtube-nocookie":1,"youtube":1,"zemanta":1,"zendesk":1,"zeotap":1,"zestycrime":1,"zonos":1,"zoominfo":1,"zopim":1,"createsend1":1,"veoxa":1,"parchedsofa":1,"sooqr":1,"adtraction":1,"addthisedge":1,"adsymptotic":1,"bootstrapcdn":1,"bugsnag":1,"dmxleo":1,"dtssrv":1,"fontawesome":1,"hs-scripts":1,"jwpltx":1,"nereserv":1,"onaudience":1,"outbrainimg":1,"quantcount":1,"rtactivate":1,"shopifysvc":1,"stripe":1,"twimg":1,"vimeo":1,"vimeocdn":1,"wp":1,"2znp09oa":1,"4jnzhl0d0":1,"6ldu6qa":1,"82o9v830":1,"abilityscale":1,"aboardamusement":1,"aboardlevel":1,"abovechat":1,"abruptroad":1,"absentairport":1,"absorbingcorn":1,"absorbingprison":1,"abstractedamount":1,"absurdapple":1,"abundantcoin":1,"acceptableauthority":1,"accurateanimal":1,"accuratecoal":1,"achieverknee":1,"acidicstraw":1,"acridangle":1,"acridtwist":1,"actoramusement":1,"actuallysheep":1,"actuallysnake":1,"actuallything":1,"adamantsnail":1,"addictedattention":1,"adorableanger":1,"adorableattention":1,"adventurousamount":1,"afraidlanguage":1,"aftermathbrother":1,"agilebreeze":1,"agreeablearch":1,"agreeabletouch":1,"aheadday":1,"aheadgrow":1,"aheadmachine":1,"ak0gsh40":1,"alertarithmetic":1,"aliasanvil":1,"alleythecat":1,"aloofmetal":1,"alpineactor":1,"ambientdusk":1,"ambientlagoon":1,"ambiguousanger":1,"ambiguousdinosaurs":1,"ambiguousincome":1,"ambrosialsummit":1,"amethystzenith":1,"amuckafternoon":1,"amusedbucket":1,"analogwonder":1,"analyzecorona":1,"ancientact":1,"annoyingacoustics":1,"anxiousapples":1,"aquaticowl":1,"ar1nvz5":1,"archswimming":1,"aromamirror":1,"arrivegrowth":1,"artthevoid":1,"aspiringapples":1,"aspiringtoy":1,"astonishingfood":1,"astralhustle":1,"astrallullaby":1,"attendchase":1,"attractivecap":1,"audioarctic":1,"automaticturkey":1,"availablerest":1,"avalonalbum":1,"averageactivity":1,"awarealley":1,"awesomeagreement":1,"awzbijw":1,"axiomaticalley":1,"axiomaticanger":1,"azuremystique":1,"backupcat":1,"badgeboat":1,"badgerabbit":1,"baitbaseball":1,"balloonbelieve":1,"bananabarrel":1,"barbarousbase":1,"basilfish":1,"basketballbelieve":1,"baskettexture":1,"bawdybeast":1,"beamvolcano":1,"beancontrol":1,"bearmoonlodge":1,"beetleend":1,"begintrain":1,"berserkhydrant":1,"bespokesandals":1,"bestboundary":1,"bewilderedbattle":1,"bewilderedblade":1,"bhcumsc":1,"bikepaws":1,"bikesboard":1,"billowybead":1,"binspiredtees":1,"birthdaybelief":1,"blackbrake":1,"bleachbubble":1,"bleachscarecrow":1,"bleedlight":1,"blesspizzas":1,"blissfulcrescendo":1,"blissfullagoon":1,"blueeyedblow":1,"blushingbeast":1,"boatsvest":1,"boilingbeetle":1,"boostbehavior":1,"boredcrown":1,"bouncyproperty":1,"boundarybusiness":1,"boundlessargument":1,"boundlessbrake":1,"boundlessveil":1,"brainybasin":1,"brainynut":1,"branchborder":1,"brandsfive":1,"brandybison":1,"bravebone":1,"bravecalculator":1,"breadbalance":1,"breakableinsurance":1,"breakfastboat":1,"breezygrove":1,"brianwould":1,"brighttoe":1,"briskstorm":1,"broadborder":1,"broadboundary":1,"broadcastbed":1,"broaddoor":1,"brotherslocket":1,"bruisebaseball":1,"brunchforher":1,"buildingknife":1,"bulbbait":1,"burgersalt":1,"burlywhistle":1,"burnbubble":1,"bushesbag":1,"bustlingbath":1,"bustlingbook":1,"butterburst":1,"cakesdrum":1,"calculatingcircle":1,"calculatingtoothbrush":1,"callousbrake":1,"calmcactus":1,"calypsocapsule":1,"cannonchange":1,"capablecows":1,"capriciouscorn":1,"captivatingcanyon":1,"captivatingillusion":1,"captivatingpanorama":1,"captivatingperformance":1,"carefuldolls":1,"caringcast":1,"caringzinc":1,"carloforward":1,"carscannon":1,"cartkitten":1,"catalogcake":1,"catschickens":1,"causecherry":1,"cautiouscamera":1,"cautiouscherries":1,"cautiouscrate":1,"cautiouscredit":1,"cavecurtain":1,"ceciliavenus":1,"celestialeuphony":1,"celestialquasar":1,"celestialspectra":1,"chaireggnog":1,"chairscrack":1,"chairsdonkey":1,"chalkoil":1,"changeablecats":1,"channelcamp":1,"charmingplate":1,"charscroll":1,"cheerycraze":1,"chessbranch":1,"chesscolor":1,"chesscrowd":1,"childlikeexample":1,"chilledliquid":1,"chingovernment":1,"chinsnakes":1,"chipperisle":1,"chivalrouscord":1,"chubbycreature":1,"chunkycactus":1,"cicdserver":1,"cinemabonus":1,"clammychicken":1,"cloisteredcord":1,"cloisteredcurve":1,"closedcows":1,"closefriction":1,"cloudhustles":1,"cloudjumbo":1,"clovercabbage":1,"clumsycar":1,"coatfood":1,"cobaltoverture":1,"coffeesidehustle":1,"coldbalance":1,"coldcreatives":1,"colorfulafterthought":1,"colossalclouds":1,"colossalcoat":1,"colossalcry":1,"combativedetail":1,"combbit":1,"combcattle":1,"combcompetition":1,"cometquote":1,"comfortablecheese":1,"comfygoodness":1,"companyparcel":1,"comparereaction":1,"compiledoctor":1,"concernedchange":1,"concernedchickens":1,"condemnedcomb":1,"conditionchange":1,"conditioncrush":1,"confesschairs":1,"configchain":1,"connectashelf":1,"consciouschairs":1,"consciouscheese":1,"consciousdirt":1,"consumerzero":1,"controlcola":1,"controlhall":1,"convertbatch":1,"cooingcoal":1,"coordinatedbedroom":1,"coordinatedcoat":1,"copycarpenter":1,"copyrightaccesscontrols":1,"coralreverie":1,"corgibeachday":1,"cosmicsculptor":1,"cosmosjackson":1,"courageousbaby":1,"coverapparatus":1,"coverlayer":1,"cozydusk":1,"cozyhillside":1,"cozytryst":1,"crackedsafe":1,"crafthenry":1,"crashchance":1,"craterbox":1,"creatorcherry":1,"creatorpassenger":1,"creaturecabbage":1,"crimsonmeadow":1,"critictruck":1,"crookedcreature":1,"cruisetourist":1,"cryptvalue":1,"crystalboulevard":1,"crystalstatus":1,"cubchannel":1,"cubepins":1,"cuddlycake":1,"cuddlylunchroom":1,"culturedcamera":1,"culturedfeather":1,"cumbersomecar":1,"cumbersomecloud":1,"curiouschalk":1,"curioussuccess":1,"curlycannon":1,"currentcollar":1,"curtaincows":1,"curvycord":1,"curvycry":1,"cushionpig":1,"cutcurrent":1,"cyclopsdial":1,"dailydivision":1,"damagedadvice":1,"damageddistance":1,"dancemistake":1,"dandydune":1,"dandyglow":1,"dapperdiscussion":1,"datastoried":1,"daughterstone":1,"daymodern":1,"dazzlingbook":1,"deafeningdock":1,"deafeningdowntown":1,"debonairdust":1,"debonairtree":1,"debugentity":1,"decidedrum":1,"decisivedrawer":1,"decisiveducks":1,"decoycreation":1,"deerbeginner":1,"defeatedbadge":1,"defensevest":1,"degreechariot":1,"delegatediscussion":1,"delicatecascade":1,"deliciousducks":1,"deltafault":1,"deluxecrate":1,"dependenttrip":1,"desirebucket":1,"desiredirt":1,"detailedgovernment":1,"detailedkitten":1,"detectdinner":1,"detourgame":1,"deviceseal":1,"deviceworkshop":1,"dewdroplagoon":1,"difficultfog":1,"digestiondrawer":1,"dinnerquartz":1,"diplomahawaii":1,"direfuldesk":1,"discreetquarter":1,"distributionneck":1,"distributionpocket":1,"distributiontomatoes":1,"disturbedquiet":1,"divehope":1,"dk4ywix":1,"dogsonclouds":1,"dollardelta":1,"doubledefend":1,"doubtdrawer":1,"dq95d35":1,"dreamycanyon":1,"driftpizza":1,"drollwharf":1,"drydrum":1,"dustydime":1,"dustyhammer":1,"eagereden":1,"eagerflame":1,"eagerknight":1,"earthyfarm":1,"eatablesquare":1,"echochief":1,"echoinghaven":1,"effervescentcoral":1,"effervescentvista":1,"effulgentnook":1,"effulgenttempest":1,"ejyymghi":1,"elasticchange":1,"elderlybean":1,"elderlytown":1,"elephantqueue":1,"elusivebreeze":1,"elusivecascade":1,"elysiantraverse":1,"embellishedmeadow":1,"embermosaic":1,"emberwhisper":1,"eminentbubble":1,"eminentend":1,"emptyescort":1,"enchantedskyline":1,"enchantingdiscovery":1,"enchantingenchantment":1,"enchantingmystique":1,"enchantingtundra":1,"enchantingvalley":1,"encourageshock":1,"endlesstrust":1,"endurablebulb":1,"energeticexample":1,"energeticladybug":1,"engineergrape":1,"engineertrick":1,"enigmaticblossom":1,"enigmaticcanyon":1,"enigmaticvoyage":1,"enormousfoot":1,"enterdrama":1,"entertainskin":1,"enthusiastictemper":1,"enviousthread":1,"equablekettle":1,"etherealbamboo":1,"ethereallagoon":1,"etherealpinnacle":1,"etherealquasar":1,"etherealripple":1,"evanescentedge":1,"evasivejar":1,"eventexistence":1,"exampleshake":1,"excitingtub":1,"exclusivebrass":1,"executeknowledge":1,"exhibitsneeze":1,"exquisiteartisanship":1,"extractobservation":1,"extralocker":1,"extramonies":1,"exuberantedge":1,"facilitatebreakfast":1,"fadechildren":1,"fadedsnow":1,"fairfeeling":1,"fairiesbranch":1,"fairytaleflame":1,"falseframe":1,"familiarrod":1,"fancyactivity":1,"fancydune":1,"fancygrove":1,"fangfeeling":1,"fantastictone":1,"farethief":1,"farshake":1,"farsnails":1,"fastenfather":1,"fasterfineart":1,"fasterjson":1,"fatcoil":1,"faucetfoot":1,"faultycanvas":1,"fearfulfish":1,"fearfulmint":1,"fearlesstramp":1,"featherstage":1,"feeblestamp":1,"feignedfaucet":1,"fernwaycloud":1,"fertilefeeling":1,"fewjuice":1,"fewkittens":1,"finalizeforce":1,"finestpiece":1,"finitecube":1,"firecatfilms":1,"fireworkcamp":1,"firstendpoint":1,"firstfrogs":1,"firsttexture":1,"fitmessage":1,"fivesidedsquare":1,"flakyfeast":1,"flameuncle":1,"flimsycircle":1,"flimsythought":1,"flippedfunnel":1,"floodprincipal":1,"flourishingcollaboration":1,"flourishingendeavor":1,"flourishinginnovation":1,"flourishingpartnership":1,"flowersornament":1,"flowerycreature":1,"floweryfact":1,"floweryoperation":1,"foambench":1,"followborder":1,"forecasttiger":1,"foretellfifth":1,"forevergears":1,"forgetfulflowers":1,"forgetfulsnail":1,"fractalcoast":1,"framebanana":1,"franticroof":1,"frantictrail":1,"frazzleart":1,"freakyglass":1,"frequentflesh":1,"friendlycrayon":1,"friendlyfold":1,"friendwool":1,"frightenedpotato":1,"frogator":1,"frogtray":1,"frugalfiestas":1,"fumblingform":1,"functionalcrown":1,"funoverbored":1,"funoverflow":1,"furnstudio":1,"furryfork":1,"furryhorses":1,"futuristicapparatus":1,"futuristicfairies":1,"futuristicfifth":1,"futuristicframe":1,"fuzzyaudio":1,"fuzzyerror":1,"gardenovens":1,"gaudyairplane":1,"geekactive":1,"generalprose":1,"generateoffice":1,"giantsvessel":1,"giddycoat":1,"gitcrumbs":1,"givevacation":1,"gladglen":1,"gladysway":1,"glamhawk":1,"gleamingcow":1,"gleaminghaven":1,"glisteningguide":1,"glisteningsign":1,"glitteringbrook":1,"glowingmeadow":1,"gluedpixel":1,"goldfishgrowth":1,"gondolagnome":1,"goodbark":1,"gracefulmilk":1,"grandfatherguitar":1,"gravitygive":1,"gravitykick":1,"grayoranges":1,"grayreceipt":1,"greyinstrument":1,"gripcorn":1,"groovyornament":1,"grouchybrothers":1,"grouchypush":1,"grumpydime":1,"grumpydrawer":1,"guardeddirection":1,"guardedschool":1,"guessdetail":1,"guidecent":1,"guildalpha":1,"gulliblegrip":1,"gustocooking":1,"gustygrandmother":1,"habitualhumor":1,"halcyoncanyon":1,"halcyonsculpture":1,"hallowedinvention":1,"haltingdivision":1,"haltinggold":1,"handleteeth":1,"handnorth":1,"handsomehose":1,"handsomeindustry":1,"handsomelythumb":1,"handsomeyam":1,"handyfield":1,"handyfireman":1,"handyincrease":1,"haplesshydrant":1,"haplessland":1,"happysponge":1,"harborcub":1,"harmonicbamboo":1,"harmonywing":1,"hatefulrequest":1,"headydegree":1,"headyhook":1,"healflowers":1,"hearinglizards":1,"heartbreakingmind":1,"hearthorn":1,"heavydetail":1,"heavyplayground":1,"helpcollar":1,"helpflame":1,"hfc195b":1,"highfalutinbox":1,"highfalutinhoney":1,"hilariouszinc":1,"historicalbeam":1,"homelycrown":1,"honeybulb":1,"honeywhipped":1,"honorablehydrant":1,"horsenectar":1,"hospitablehall":1,"hospitablehat":1,"howdyinbox":1,"humdrumhobbies":1,"humdrumtouch":1,"hurtgrape":1,"hypnoticwound":1,"hystericalcloth":1,"hystericalfinger":1,"idolscene":1,"idyllicjazz":1,"illinvention":1,"illustriousoatmeal":1,"immensehoney":1,"imminentshake":1,"importantmeat":1,"importedincrease":1,"importedinsect":1,"importlocate":1,"impossibleexpansion":1,"impossiblemove":1,"impulsejewel":1,"impulselumber":1,"incomehippo":1,"incompetentjoke":1,"inconclusiveaction":1,"infamousstream":1,"innocentlamp":1,"innocentwax":1,"inputicicle":1,"inquisitiveice":1,"inquisitiveinvention":1,"intelligentscissors":1,"intentlens":1,"interestdust":1,"internalcondition":1,"internalsink":1,"iotapool":1,"irritatingfog":1,"itemslice":1,"ivykiosk":1,"jadeitite":1,"jaderooster":1,"jailbulb":1,"joblessdrum":1,"jollylens":1,"joyfulkeen":1,"joyoussurprise":1,"jubilantaura":1,"jubilantcanyon":1,"jubilantcascade":1,"jubilantglimmer":1,"jubilanttempest":1,"jubilantwhisper":1,"justicejudo":1,"kaputquill":1,"keenquill":1,"kindhush":1,"kitesquirrel":1,"knitstamp":1,"laboredlight":1,"lameletters":1,"lamplow":1,"largebrass":1,"lasttaco":1,"leaplunchroom":1,"leftliquid":1,"lemonpackage":1,"lemonsandjoy":1,"liftedknowledge":1,"lightenafterthought":1,"lighttalon":1,"livelumber":1,"livelylaugh":1,"livelyreward":1,"livingsleet":1,"lizardslaugh":1,"loadsurprise":1,"lonelyflavor":1,"longingtrees":1,"lorenzourban":1,"losslace":1,"loudlunch":1,"loveseashore":1,"lp3tdqle":1,"ludicrousarch":1,"lumberamount":1,"luminousboulevard":1,"luminouscatalyst":1,"luminoussculptor":1,"lumpygnome":1,"lumpylumber":1,"lustroushaven":1,"lyricshook":1,"madebyintent":1,"magicaljoin":1,"magnetairport":1,"majesticmountainrange":1,"majesticwaterscape":1,"majesticwilderness":1,"maliciousmusic":1,"managedpush":1,"mantrafox":1,"marblediscussion":1,"markahouse":1,"markedmeasure":1,"marketspiders":1,"marriedmailbox":1,"marriedvalue":1,"massivemark":1,"materialisticmoon":1,"materialmilk":1,"materialplayground":1,"meadowlullaby":1,"meatydime":1,"mediatescarf":1,"mediumshort":1,"mellowhush":1,"mellowmailbox":1,"melodiouschorus":1,"melodiouscomposition":1,"meltmilk":1,"memopilot":1,"memorizeneck":1,"meremark":1,"merequartz":1,"merryopal":1,"merryvault":1,"messagenovice":1,"messyoranges":1,"mightyspiders":1,"mimosamajor":1,"mindfulgem":1,"minorcattle":1,"minusmental":1,"minuteburst":1,"miscreantmoon":1,"mistyhorizon":1,"mittencattle":1,"mixedreading":1,"modularmental":1,"monacobeatles":1,"moorshoes":1,"motionlessbag":1,"motionlessbelief":1,"motionlessmeeting":1,"movemeal":1,"muddledaftermath":1,"muddledmemory":1,"mundanenail":1,"mundanepollution":1,"mushywaste":1,"muteknife":1,"mutemailbox":1,"mysticalagoon":1,"naivestatement":1,"nappyneck":1,"neatshade":1,"nebulacrescent":1,"nebulajubilee":1,"nebulousamusement":1,"nebulousgarden":1,"nebulousquasar":1,"nebulousripple":1,"needlessnorth":1,"needyneedle":1,"neighborlywatch":1,"niftygraphs":1,"niftyhospital":1,"niftyjelly":1,"nightwound":1,"nimbleplot":1,"nocturnalloom":1,"nocturnalmystique":1,"noiselessplough":1,"nonchalantnerve":1,"nondescriptcrowd":1,"nondescriptstocking":1,"nostalgicknot":1,"nostalgicneed":1,"notifyglass":1,"nudgeduck":1,"nullnorth":1,"numberlessring":1,"numerousnest":1,"nuttyorganization":1,"oafishchance":1,"oafishobservation":1,"obscenesidewalk":1,"observantice":1,"oldfashionedoffer":1,"omgthink":1,"omniscientfeeling":1,"onlywoofs":1,"opalquill":1,"operationchicken":1,"operationnail":1,"oppositeoperation":1,"optimallimit":1,"opulentsylvan":1,"orientedargument":1,"orionember":1,"ourblogthing":1,"outgoinggiraffe":1,"outsidevibe":1,"outstandingincome":1,"outstandingsnails":1,"overkick":1,"overratedchalk":1,"oxygenfuse":1,"pailcrime":1,"painstakingpickle":1,"paintpear":1,"paleleaf":1,"pamelarandom":1,"panickycurtain":1,"parallelbulb":1,"pardonpopular":1,"parentpicture":1,"parsimoniouspolice":1,"passivepolo":1,"pastoralroad":1,"pawsnug":1,"peacefullimit":1,"pedromister":1,"pedropanther":1,"perceivequarter":1,"perkyjade":1,"petiteumbrella":1,"philippinch":1,"photographpan":1,"piespower":1,"piquantgrove":1,"piquantmeadow":1,"piquantpigs":1,"piquantprice":1,"piquantvortex":1,"pixeledhub":1,"pizzasnut":1,"placeframe":1,"placidactivity":1,"planebasin":1,"plantdigestion":1,"playfulriver":1,"plotparent":1,"pluckyzone":1,"poeticpackage":1,"pointdigestion":1,"pointlesshour":1,"pointlesspocket":1,"pointlessprofit":1,"pointlessrifle":1,"polarismagnet":1,"polishedcrescent":1,"polishedfolly":1,"politeplanes":1,"politicalflip":1,"politicalporter":1,"popplantation":1,"possiblepencil":1,"powderjourney":1,"powerfulblends":1,"preciousplanes":1,"prefixpatriot":1,"presetrabbits":1,"previousplayground":1,"previouspotato":1,"pricklypollution":1,"pristinegale":1,"probablepartner":1,"processplantation":1,"producepickle":1,"productsurfer":1,"profitrumour":1,"promiseair":1,"proofconvert":1,"propertypotato":1,"protestcopy":1,"psychedelicchess":1,"publicsofa":1,"puffyloss":1,"puffypaste":1,"puffypull":1,"puffypurpose":1,"pulsatingmeadow":1,"pumpedpancake":1,"pumpedpurpose":1,"punyplant":1,"puppytooth":1,"purposepipe":1,"quacksquirrel":1,"quaintcan":1,"quaintlake":1,"quantumlagoon":1,"quantumshine":1,"queenskart":1,"quillkick":1,"quirkybliss":1,"quirkysugar":1,"quixoticnebula":1,"rabbitbreath":1,"rabbitrifle":1,"radiantcanopy":1,"radiantlullaby":1,"railwaygiraffe":1,"raintwig":1,"rainyhand":1,"rainyrule":1,"rangecake":1,"raresummer":1,"reactjspdf":1,"readingguilt":1,"readymoon":1,"readysnails":1,"realizedoor":1,"realizerecess":1,"rebelclover":1,"rebelhen":1,"rebelsubway":1,"receiptcent":1,"receptiveink":1,"receptivereaction":1,"recessrain":1,"reconditeprison":1,"reflectivestatement":1,"refundradar":1,"regularplants":1,"regulatesleet":1,"relationrest":1,"reloadphoto":1,"rememberdiscussion":1,"rentinfinity":1,"replaceroute":1,"resonantbrush":1,"respectrain":1,"resplendentecho":1,"retrievemint":1,"rhetoricalactivity":1,"rhetoricalveil":1,"rhymezebra":1,"rhythmrule":1,"richstring":1,"rigidrobin":1,"rigidveil":1,"rigorlab":1,"ringplant":1,"ringsrecord":1,"ritzykey":1,"ritzyrepresentative":1,"ritzyveil":1,"rockpebbles":1,"rollconnection":1,"roofrelation":1,"roseincome":1,"rottenray":1,"rusticprice":1,"ruthlessdegree":1,"ruthlessmilk":1,"sableloss":1,"sablesmile":1,"sadloaf":1,"saffronrefuge":1,"sagargift":1,"saltsacademy":1,"samesticks":1,"samplesamba":1,"scarcecard":1,"scarceshock":1,"scarcesign":1,"scarcestructure":1,"scarcesurprise":1,"scaredcomfort":1,"scaredsidewalk":1,"scaredslip":1,"scaredsnake":1,"scaredswing":1,"scarefowl":1,"scatteredheat":1,"scatteredquiver":1,"scatteredstream":1,"scenicapparel":1,"scientificshirt":1,"scintillatingscissors":1,"scissorsstatement":1,"scrapesleep":1,"scratchsofa":1,"screechingfurniture":1,"screechingstocking":1,"scribbleson":1,"scrollservice":1,"scrubswim":1,"seashoresociety":1,"secondhandfall":1,"secretivesheep":1,"secretspiders":1,"secretturtle":1,"seedscissors":1,"seemlysuggestion":1,"selfishsea":1,"sendingspire":1,"sensorsmile":1,"separatesort":1,"seraphichorizon":1,"seraphicjubilee":1,"serendipityecho":1,"serenecascade":1,"serenepebble":1,"serenesurf":1,"serioussuit":1,"serpentshampoo":1,"settleshoes":1,"shadeship":1,"shaggytank":1,"shakyseat":1,"shakysurprise":1,"shakytaste":1,"shallowblade":1,"sharkskids":1,"sheargovernor":1,"shesubscriptions":1,"shinypond":1,"shirtsidewalk":1,"shiveringspot":1,"shiverscissors":1,"shockinggrass":1,"shockingship":1,"shredquiz":1,"shydinosaurs":1,"sierrakermit":1,"signaturepod":1,"siliconslow":1,"sillyscrew":1,"simplesidewalk":1,"simulateswing":1,"sincerebuffalo":1,"sincerepelican":1,"sinceresubstance":1,"sinkbooks":1,"sixscissors":1,"sizzlingsmoke":1,"slaysweater":1,"slimyscarf":1,"slinksuggestion":1,"smallershops":1,"smashshoe":1,"smilewound":1,"smilingcattle":1,"smilingswim":1,"smilingwaves":1,"smoggysongs":1,"smoggystation":1,"snacktoken":1,"snakemineral":1,"snakeslang":1,"sneakwind":1,"sneakystew":1,"snoresmile":1,"snowmentor":1,"soggysponge":1,"soggyzoo":1,"solarislabyrinth":1,"somberscarecrow":1,"sombersea":1,"sombersquirrel":1,"sombersticks":1,"sombersurprise":1,"soothingglade":1,"sophisticatedstove":1,"sordidsmile":1,"soresidewalk":1,"soresneeze":1,"sorethunder":1,"soretrain":1,"sortsail":1,"sortsummer":1,"sowlettuce":1,"spadelocket":1,"sparkgoal":1,"sparklingshelf":1,"specialscissors":1,"spellmist":1,"spellsalsa":1,"spiffymachine":1,"spirebaboon":1,"spookystitch":1,"spoonsilk":1,"spotlessstamp":1,"spottednoise":1,"springolive":1,"springsister":1,"springsnails":1,"sproutingbag":1,"sprydelta":1,"sprysummit":1,"spuriousair":1,"spuriousbase":1,"spurioussquirrel":1,"spuriousstranger":1,"spysubstance":1,"squalidscrew":1,"squeakzinc":1,"squealingturn":1,"stakingbasket":1,"stakingshock":1,"staleshow":1,"stalesummer":1,"starkscale":1,"startingcars":1,"statshunt":1,"statuesqueship":1,"stayaction":1,"steadycopper":1,"stealsteel":1,"steepscale":1,"steepsister":1,"stepcattle":1,"stepplane":1,"stepwisevideo":1,"stereoproxy":1,"stewspiders":1,"stiffstem":1,"stimulatingsneeze":1,"stingsquirrel":1,"stingyshoe":1,"stingyspoon":1,"stockingsleet":1,"stockingsneeze":1,"stomachscience":1,"stonechin":1,"stopstomach":1,"stormyachiever":1,"stormyfold":1,"strangeclocks":1,"strangersponge":1,"strangesink":1,"streetsort":1,"stretchsister":1,"stretchsneeze":1,"stretchsquirrel":1,"stripedbat":1,"strivesidewalk":1,"sturdysnail":1,"subletyoke":1,"sublimequartz":1,"subsequentswim":1,"substantialcarpenter":1,"substantialgrade":1,"succeedscene":1,"successfulscent":1,"suddensoda":1,"sugarfriction":1,"suggestionbridge":1,"summerobject":1,"sunshinegates":1,"superchichair":1,"superficialspring":1,"superviseshoes":1,"supportwaves":1,"suspectmark":1,"swellstocking":1,"swelteringsleep":1,"swingslip":1,"swordgoose":1,"syllablesight":1,"synonymousrule":1,"synonymoussticks":1,"synthesizescarecrow":1,"tackytrains":1,"tacojournal":1,"talltouch":1,"tangibleteam":1,"tangyamount":1,"tastelesstrees":1,"tastelesstrucks":1,"tastesnake":1,"tawdryson":1,"tearfulglass":1,"techconverter":1,"tediousbear":1,"tedioustooth":1,"teenytinycellar":1,"teenytinytongue":1,"telephoneapparatus":1,"tempertrick":1,"tempttalk":1,"temptteam":1,"terriblethumb":1,"terrifictooth":1,"testadmiral":1,"texturetrick":1,"therapeuticcars":1,"thickticket":1,"thicktrucks":1,"thingsafterthought":1,"thingstaste":1,"thinkitwice":1,"thirdrespect":1,"thirstytwig":1,"thomastorch":1,"thoughtlessknot":1,"thrivingmarketplace":1,"ticketaunt":1,"ticklesign":1,"tidymitten":1,"tightpowder":1,"tinyswans":1,"tinytendency":1,"tiredthroat":1,"toolcapital":1,"toomanyalts":1,"torpidtongue":1,"trackcaddie":1,"tradetooth":1,"trafficviews":1,"tranquilamulet":1,"tranquilarchipelago":1,"tranquilcan":1,"tranquilcanyon":1,"tranquilplume":1,"tranquilside":1,"tranquilveil":1,"tranquilveranda":1,"trappush":1,"treadbun":1,"tremendousearthquake":1,"tremendousplastic":1,"tremendoustime":1,"tritebadge":1,"tritethunder":1,"tritetongue":1,"troubledtail":1,"troubleshade":1,"truckstomatoes":1,"truculentrate":1,"tumbleicicle":1,"tuneupcoffee":1,"twistloss":1,"twistsweater":1,"typicalairplane":1,"ubiquitoussea":1,"ubiquitousyard":1,"ultravalid":1,"unablehope":1,"unaccountablecreator":1,"unaccountablepie":1,"unarmedindustry":1,"unbecominghall":1,"uncoveredexpert":1,"understoodocean":1,"unequalbrake":1,"unequaltrail":1,"unknowncontrol":1,"unknowncrate":1,"unknowntray":1,"untidyquestion":1,"untidyrice":1,"unusedstone":1,"unusualtitle":1,"unwieldyimpulse":1,"uppitytime":1,"uselesslumber":1,"validmemo":1,"vanfireworks":1,"vanishmemory":1,"velvetnova":1,"velvetquasar":1,"venomousvessel":1,"venusgloria":1,"verdantanswer":1,"verdantlabyrinth":1,"verdantloom":1,"verdantsculpture":1,"verseballs":1,"vibrantcelebration":1,"vibrantgale":1,"vibranthaven":1,"vibrantpact":1,"vibrantsundown":1,"vibranttalisman":1,"vibrantvale":1,"victoriousrequest":1,"virtualvincent":1,"vividcanopy":1,"vividfrost":1,"vividmeadow":1,"vividplume":1,"voicelessvein":1,"voidgoo":1,"volatileprofit":1,"waitingnumber":1,"wantingwindow":1,"warnwing":1,"washbanana":1,"wateryvan":1,"waterywave":1,"waterywrist":1,"wearbasin":1,"websitesdude":1,"wellgroomedapparel":1,"wellgroomedhydrant":1,"wellmadefrog":1,"westpalmweb":1,"whimsicalcanyon":1,"whimsicalgrove":1,"whineattempt":1,"whirlwealth":1,"whiskyqueue":1,"whisperingcascade":1,"whisperingcrib":1,"whisperingquasar":1,"whisperingsummit":1,"whispermeeting":1,"wildcommittee":1,"wirecomic":1,"wiredforcoffee":1,"wirypaste":1,"wistfulwaste":1,"wittypopcorn":1,"wittyshack":1,"workoperation":1,"worldlever":1,"worriednumber":1,"worriedwine":1,"wretchedfloor":1,"wrongpotato":1,"wrongwound":1,"wtaccesscontrol":1,"xovq5nemr":1,"yieldingwoman":1,"zbwp6ghm":1,"zephyrcatalyst":1,"zephyrlabyrinth":1,"zestyhorizon":1,"zestyrover":1,"zestywire":1,"zipperxray":1,"zonewedgeshaft":1},"net":{"2mdn":1,"2o7":1,"3gl":1,"a-mo":1,"acint":1,"adform":1,"adhigh":1,"admixer":1,"adobedc":1,"adspeed":1,"adverticum":1,"apicit":1,"appier":1,"akamaized":{"assets-momentum":1},"aticdn":1,"edgekey":{"au":1,"ca":1,"ch":1,"cn":1,"com-v1":1,"es":1,"ihg":1,"in":1,"io":1,"it":1,"jp":1,"net":1,"org":1,"com":{"scene7":1},"uk-v1":1,"uk":1},"azure":1,"azurefd":1,"bannerflow":1,"bf-tools":1,"bidswitch":1,"bitsngo":1,"blueconic":1,"boldapps":1,"buysellads":1,"cachefly":1,"cedexis":1,"certona":1,"confiant-integrations":1,"contentsquare":1,"criteo":1,"crwdcntrl":1,"cloudfront":{"d1af033869koo7":1,"d1cr9zxt7u0sgu":1,"d1s87id6169zda":1,"d1vg5xiq7qffdj":1,"d1y068gyog18cq":1,"d214hhm15p4t1d":1,"d21gpk1vhmjuf5":1,"d2zah9y47r7bi2":1,"d38b8me95wjkbc":1,"d38xvr37kwwhcm":1,"d3fv2pqyjay52z":1,"d3i4yxtzktqr9n":1,"d3odp2r1osuwn0":1,"d5yoctgpv4cpx":1,"d6tizftlrpuof":1,"dbukjj6eu5tsf":1,"dn0qt3r0xannq":1,"dsh7ky7308k4b":1,"d2g3ekl4mwm40k":1},"demdex":1,"dotmetrics":1,"doubleclick":1,"durationmedia":1,"e-planning":1,"edgecastcdn":1,"emsecure":1,"episerver":1,"esm1":1,"eulerian":1,"everestjs":1,"everesttech":1,"eyeota":1,"ezoic":1,"fastly":{"global":{"shared":{"f2":1},"sni":{"j":1}},"map":{"prisa-us-eu":1,"scribd":1},"ssl":{"global":{"qognvtzku-x":1}}},"facebook":1,"fastclick":1,"fonts":1,"azureedge":{"fp-cdn":1,"sdtagging":1},"fuseplatform":1,"fwmrm":1,"go-mpulse":1,"hadronid":1,"hs-analytics":1,"hsleadflows":1,"im-apps":1,"impervadns":1,"iocnt":1,"iprom":1,"jsdelivr":1,"kanade-ad":1,"krxd":1,"line-scdn":1,"listhub":1,"livecom":1,"livedoor":1,"liveperson":1,"lkqd":1,"llnwd":1,"lpsnmedia":1,"magnetmail":1,"marketo":1,"maxymiser":1,"media":1,"microad":1,"mobon":1,"monetate":1,"mxptint":1,"myfonts":1,"myvisualiq":1,"naver":1,"nr-data":1,"ojrq":1,"omtrdc":1,"onecount":1,"openx":1,"openxcdn":1,"opta":1,"owneriq":1,"pages02":1,"pages03":1,"pages04":1,"pages05":1,"pages06":1,"pages08":1,"pingdom":1,"pmdstatic":1,"popads":1,"popcash":1,"primecaster":1,"pro-market":1,"akamaihd":{"pxlclnmdecom-a":1},"rfihub":1,"sancdn":1,"sc-static":1,"semasio":1,"sensic":1,"sexad":1,"smaato":1,"spreadshirts":1,"storygize":1,"tfaforms":1,"trackcmp":1,"trackedlink":1,"tradetracker":1,"truste-svc":1,"uuidksinc":1,"viafoura":1,"visilabs":1,"visx":1,"w55c":1,"wdsvc":1,"witglobal":1,"yandex":1,"yastatic":1,"yieldlab":1,"zencdn":1,"zucks":1,"opencmp":1,"azurewebsites":{"app-fnsp-matomo-analytics-prod":1},"ad-delivery":1,"chartbeat":1,"msecnd":1,"cloudfunctions":{"us-central1-adaptive-growth":1},"eviltracker":1},"co":{"6sc":1,"ayads":1,"getlasso":1,"idio":1,"increasingly":1,"jads":1,"nanorep":1,"nc0":1,"pcdn":1,"prmutv":1,"resetdigital":1,"t":1,"tctm":1,"zip":1},"gt":{"ad":1},"ru":{"adfox":1,"adriver":1,"digitaltarget":1,"mail":1,"mindbox":1,"rambler":1,"rutarget":1,"sape":1,"smi2":1,"tns-counter":1,"top100":1,"ulogin":1,"yandex":1,"yadro":1},"jp":{"adingo":1,"admatrix":1,"auone":1,"co":{"dmm":1,"i-mobile":1,"rakuten":1,"yahoo":1},"fout":1,"genieesspv":1,"gmossp-sp":1,"gsspat":1,"gssprt":1,"ne":{"hatena":1},"i2i":1,"impact-ad":1,"microad":1,"nakanohito":1,"r10s":1,"reemo-ad":1,"rtoaster":1,"shinobi":1,"team-rec":1,"uncn":1,"yimg":1,"yjtag":1},"pl":{"adocean":1,"gemius":1,"nsaudience":1,"onet":1,"salesmanago":1,"wp":1},"pro":{"adpartner":1,"piwik":1,"usocial":1},"de":{"adscale":1,"auswaertiges-amt":1,"fiduciagad":1,"ioam":1,"itzbund":1,"vgwort":1,"werk21system":1},"re":{"adsco":1},"info":{"adxbid":1,"bitrix":1,"navistechnologies":1,"usergram":1,"webantenna":1},"tv":{"affec":1,"attn":1,"iris":1,"ispot":1,"samba":1,"teads":1,"twitch":1,"videohub":1},"dev":{"amazon":1},"us":{"amung":1,"samplicio":1,"slgnt":1,"trkn":1,"owlsr":1},"media":{"andbeyond":1,"nextday":1,"townsquare":1,"underdog":1},"link":{"app":1},"cloud":{"avct":1,"egain":1,"matomo":1},"delivery":{"ay":1,"monu":1},"ly":{"bit":1},"br":{"com":{"btg360":1,"clearsale":1,"jsuol":1,"shopconvert":1,"shoptarget":1,"soclminer":1},"org":{"ivcbrasil":1}},"ch":{"ch":1,"da-services":1,"google":1},"me":{"channel":1,"contentexchange":1,"grow":1,"line":1,"loopme":1,"t":1},"ms":{"clarity":1},"my":{"cnt":1},"se":{"codigo":1},"to":{"cpx":1,"tawk":1},"chat":{"crisp":1,"gorgias":1},"fr":{"d-bi":1,"open-system":1,"weborama":1},"uk":{"co":{"dailymail":1,"hsbc":1}},"gov":{"dhs":1},"ai":{"e-volution":1,"hybrid":1,"m2":1,"nrich":1,"wknd":1},"be":{"geoedge":1},"au":{"com":{"google":1,"news":1,"nine":1,"zipmoney":1,"telstra":1}},"stream":{"ibclick":1},"cz":{"imedia":1,"seznam":1,"trackad":1},"app":{"infusionsoft":1,"permutive":1,"shop":1},"tech":{"ingage":1,"primis":1},"eu":{"kameleoon":1,"medallia":1,"media01":1,"ocdn":1,"rqtrk":1,"slgnt":1},"fi":{"kesko":1,"simpli":1},"live":{"lura":1},"services":{"marketingautomation":1},"sg":{"mediacorp":1},"bi":{"newsroom":1},"fm":{"pdst":1},"ad":{"pixel":1},"xyz":{"playground":1},"it":{"plug":1,"repstatic":1},"cc":{"popin":1},"network":{"pub":1},"nl":{"rijksoverheid":1},"fyi":{"sda":1},"es":{"socy":1},"im":{"spot":1},"market":{"spotim":1},"am":{"tru":1},"no":{"uio":1,"medietall":1},"at":{"waust":1},"pe":{"shop":1},"ca":{"bc":{"gov":1}},"gg":{"clean":1},"example":{"ad-company":1},"site":{"ad-company":1,"third-party":{"bad":1,"broken":1}},"pw":{"5mcwl":1,"fvl1f":1,"h78xb":1,"i9w8p":1,"k54nw":1,"tdzvm":1,"tzwaw":1,"vq1qi":1,"zlp6s":1},"pub":{"admiral":1}};
        output.bundledConfig = data;

        return output;
    }

    /**
     * Retutns a list of enabled features
     * @param {RemoteConfig} data
     * @param {string | null} topLevelHostname
     * @param {Platform['version']} platformVersion
     * @param {string[]} platformSpecificFeatures
     * @returns {string[]}
     */
    function computeEnabledFeatures(data, topLevelHostname, platformVersion, platformSpecificFeatures = []) {
        const remoteFeatureNames = Object.keys(data.features);
        const platformSpecificFeaturesNotInRemoteConfig = platformSpecificFeatures.filter(
            (featureName) => !remoteFeatureNames.includes(featureName),
        );
        const enabledFeatures = remoteFeatureNames
            .filter((featureName) => {
                const feature = data.features[featureName];
                // Check that the platform supports minSupportedVersion checks and that the feature has a minSupportedVersion
                if (feature.minSupportedVersion && platformVersion) {
                    if (!isSupportedVersion(feature.minSupportedVersion, platformVersion)) {
                        return false;
                    }
                }
                return feature.state === 'enabled' && !isUnprotectedDomain(topLevelHostname, feature.exceptions);
            })
            .concat(platformSpecificFeaturesNotInRemoteConfig); // only disable platform specific features if it's explicitly disabled in remote config
        return enabledFeatures;
    }

    /**
     * Returns the relevant feature settings for the enabled features
     * @param {RemoteConfig} data
     * @param {string[]} enabledFeatures
     * @returns {Record<string, unknown>}
     */
    function parseFeatureSettings(data, enabledFeatures) {
        /** @type {Record<string, unknown>} */
        const featureSettings = {};
        const remoteFeatureNames = Object.keys(data.features);
        remoteFeatureNames.forEach((featureName) => {
            if (!enabledFeatures.includes(featureName)) {
                return;
            }

            featureSettings[featureName] = data.features[featureName].settings;
        });
        return featureSettings;
    }

    function isGloballyDisabled(args) {
        return args.site.allowlisted || args.site.isBroken;
    }

    /**
     * @import {FeatureName} from "./features";
     * @type {FeatureName[]}
     */
    const platformSpecificFeatures = ['windowsPermissionUsage', 'messageBridge'];

    function isPlatformSpecificFeature(featureName) {
        return platformSpecificFeatures.includes(featureName);
    }

    function createCustomEvent(eventName, eventDetail) {
        // @ts-expect-error - possibly null
        return new OriginalCustomEvent(eventName, eventDetail);
    }

    /** @deprecated */
    function legacySendMessage(messageType, options) {
        // FF & Chrome
        return (
            originalWindowDispatchEvent &&
            originalWindowDispatchEvent(
                createCustomEvent('sendMessageProxy' + messageSecret, { detail: JSON.stringify({ messageType, options }) }),
            )
        );
        // TBD other platforms
    }

    const baseFeatures = /** @type {const} */ ([
        'fingerprintingAudio',
        'fingerprintingBattery',
        'fingerprintingCanvas',
        'googleRejected',
        'gpc',
        'fingerprintingHardware',
        'referrer',
        'fingerprintingScreenSize',
        'fingerprintingTemporaryStorage',
        'navigatorInterface',
        'elementHiding',
        'exceptionHandler',
        'apiManipulation',
    ]);

    const otherFeatures = /** @type {const} */ ([
        'clickToLoad',
        'cookie',
        'messageBridge',
        'duckPlayer',
        'harmfulApis',
        'webCompat',
        'windowsPermissionUsage',
        'brokerProtection',
        'performanceMetrics',
        'breakageReporting',
        'autofillPasswordImport',
    ]);

    /** @typedef {baseFeatures[number]|otherFeatures[number]} FeatureName */
    /** @type {Record<string, FeatureName[]>} */
    const platformSupport = {
        apple: ['webCompat', ...baseFeatures],
        'apple-isolated': ['duckPlayer', 'brokerProtection', 'performanceMetrics', 'clickToLoad', 'messageBridge'],
        android: [...baseFeatures, 'webCompat', 'breakageReporting', 'duckPlayer', 'messageBridge'],
        'android-broker-protection': ['brokerProtection'],
        'android-autofill-password-import': ['autofillPasswordImport'],
        windows: ['cookie', ...baseFeatures, 'windowsPermissionUsage', 'duckPlayer', 'brokerProtection', 'breakageReporting'],
        firefox: ['cookie', ...baseFeatures, 'clickToLoad'],
        chrome: ['cookie', ...baseFeatures, 'clickToLoad'],
        'chrome-mv3': ['cookie', ...baseFeatures, 'clickToLoad'],
        integration: [...baseFeatures, ...otherFeatures],
    };

    /**
     * Performance monitor, holds reference to PerformanceMark instances.
     */
    class PerformanceMonitor {
        constructor() {
            this.marks = [];
        }

        /**
         * Create performance marker
         * @param {string} name
         * @returns {PerformanceMark}
         */
        mark(name) {
            const mark = new PerformanceMark(name);
            this.marks.push(mark);
            return mark;
        }

        /**
         * Measure all performance markers
         */
        measureAll() {
            this.marks.forEach((mark) => {
                mark.measure();
            });
        }
    }

    /**
     * Tiny wrapper around performance.mark and performance.measure
     */
    // eslint-disable-next-line no-redeclare
    class PerformanceMark {
        /**
         * @param {string} name
         */
        constructor(name) {
            this.name = name;
            performance.mark(this.name + 'Start');
        }

        end() {
            performance.mark(this.name + 'End');
        }

        measure() {
            performance.measure(this.name, this.name + 'Start', this.name + 'End');
        }
    }

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
                corrupt: function (message) {
                    this.toString = function () {
                        return 'CORRUPT: ' + this.message;
                    };
                    this.message = message;
                },

                /**
                 * Invalid parameter.
                 * @constructor
                 */
                invalid: function (message) {
                    this.toString = function () {
                        return 'INVALID: ' + this.message;
                    };
                    this.message = message;
                },

                /**
                 * Bug or missing feature in SJCL.
                 * @constructor
                 */
                bug: function (message) {
                    this.toString = function () {
                        return 'BUG: ' + this.message;
                    };
                    this.message = message;
                },

                /**
                 * Something isn't ready.
                 * @constructor
                 */
                notReady: function (message) {
                    this.toString = function () {
                        return 'NOT READY: ' + this.message;
                    };
                    this.message = message;
                },
            },
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
                a = sjcl.bitArray._shiftRight(a.slice(bstart / 32), 32 - (bstart & 31)).slice(1);
                return bend === undefined ? a : sjcl.bitArray.clamp(a, bend - bstart);
            },

            /**
             * Extract a number packed into a bit array.
             * @param {bitArray} a The array to slice.
             * @param {Number} bstart The offset to the start of the slice, in bits.
             * @param {Number} blength The length of the number to extract.
             * @return {Number} The requested slice.
             */
            extract: function (a, bstart, blength) {
                // FIXME: this Math.floor is not necessary at all, but for some reason
                // seems to suppress a bug in the Chromium JIT.
                var x,
                    sh = Math.floor((-bstart - blength) & 31);
                if (((bstart + blength - 1) ^ bstart) & -32) {
                    // it crosses a boundary
                    x = (a[(bstart / 32) | 0] << (32 - sh)) ^ (a[(bstart / 32 + 1) | 0] >>> sh);
                } else {
                    // within a single word
                    x = a[(bstart / 32) | 0] >>> sh;
                }
                return x & ((1 << blength) - 1);
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

                var last = a1[a1.length - 1],
                    shift = sjcl.bitArray.getPartial(last);
                if (shift === 32) {
                    return a1.concat(a2);
                } else {
                    return sjcl.bitArray._shiftRight(a2, shift, last | 0, a1.slice(0, a1.length - 1));
                }
            },

            /**
             * Find the length of an array of bits.
             * @param {bitArray} a The array.
             * @return {Number} The length of a, in bits.
             */
            bitLength: function (a) {
                var l = a.length,
                    x;
                if (l === 0) {
                    return 0;
                }
                x = a[l - 1];
                return (l - 1) * 32 + sjcl.bitArray.getPartial(x);
            },

            /**
             * Truncate an array.
             * @param {bitArray} a The array.
             * @param {Number} len The length to truncate to, in bits.
             * @return {bitArray} A new array, truncated to len bits.
             */
            clamp: function (a, len) {
                if (a.length * 32 < len) {
                    return a;
                }
                a = a.slice(0, Math.ceil(len / 32));
                var l = a.length;
                len = len & 31;
                if (l > 0 && len) {
                    a[l - 1] = sjcl.bitArray.partial(len, a[l - 1] & (0x80000000 >> (len - 1)), 1);
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
                if (len === 32) {
                    return x;
                }
                return (_end ? x | 0 : x << (32 - len)) + len * 0x10000000000;
            },

            /**
             * Get the number of bits used by a partial word.
             * @param {Number} x The partial word.
             * @return {Number} The number of bits used by the partial word.
             */
            getPartial: function (x) {
                return Math.round(x / 0x10000000000) || 32;
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
                var x = 0,
                    i;
                for (i = 0; i < a.length; i++) {
                    x |= a[i] ^ b[i];
                }
                return x === 0;
            },

            /** Shift an array right.
             * @param {bitArray} a The array to shift.
             * @param {Number} shift The number of bits to shift.
             * @param {Number} [carry=0] A byte to carry in
             * @param {bitArray} [out=[]] An array to prepend to the output.
             * @private
             */
            _shiftRight: function (a, shift, carry, out) {
                var i,
                    last2 = 0,
                    shift2;
                if (out === undefined) {
                    out = [];
                }

                for (; shift >= 32; shift -= 32) {
                    out.push(carry);
                    carry = 0;
                }
                if (shift === 0) {
                    return out.concat(a);
                }

                for (i = 0; i < a.length; i++) {
                    out.push(carry | (a[i] >>> shift));
                    carry = a[i] << (32 - shift);
                }
                last2 = a.length ? a[a.length - 1] : 0;
                shift2 = sjcl.bitArray.getPartial(last2);
                out.push(sjcl.bitArray.partial((shift + shift2) & 31, shift + shift2 > 32 ? carry : out.pop(), 1));
                return out;
            },

            /** xor a block of 4 words together.
             * @private
             */
            _xor4: function (x, y) {
                return [x[0] ^ y[0], x[1] ^ y[1], x[2] ^ y[2], x[3] ^ y[3]];
            },

            /** byteswap a word array inplace.
             * (does not handle partial words)
             * @param {sjcl.bitArray} a word array
             * @return {sjcl.bitArray} byteswapped array
             */
            byteswapM: function (a) {
                var i,
                    v,
                    m = 0xff00;
                for (i = 0; i < a.length; ++i) {
                    v = a[i];
                    a[i] = (v >>> 24) | ((v >>> 8) & m) | ((v & m) << 8) | (v << 24);
                }
                return a;
            },
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
                var out = '',
                    bl = sjcl.bitArray.bitLength(arr),
                    i,
                    tmp;
                for (i = 0; i < bl / 8; i++) {
                    if ((i & 3) === 0) {
                        tmp = arr[i / 4];
                    }
                    out += String.fromCharCode(((tmp >>> 8) >>> 8) >>> 8);
                    tmp <<= 8;
                }
                return decodeURIComponent(escape(out));
            },

            /** Convert from a UTF-8 string to a bitArray. */
            toBits: function (str) {
                str = unescape(encodeURIComponent(str));
                var out = [],
                    i,
                    tmp = 0;
                for (i = 0; i < str.length; i++) {
                    tmp = (tmp << 8) | str.charCodeAt(i);
                    if ((i & 3) === 3) {
                        out.push(tmp);
                        tmp = 0;
                    }
                }
                if (i & 3) {
                    out.push(sjcl.bitArray.partial(8 * (i & 3), tmp));
                }
                return out;
            },
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
                var out = '',
                    i;
                for (i = 0; i < arr.length; i++) {
                    out += ((arr[i] | 0) + 0xf00000000000).toString(16).substr(4);
                }
                return out.substr(0, sjcl.bitArray.bitLength(arr) / 4); //.replace(/(.{8})/g, "$1 ");
            },
            /** Convert from a hex string to a bitArray. */
            toBits: function (str) {
                var i,
                    out = [],
                    len;
                str = str.replace(/\s|0x/g, '');
                len = str.length;
                str = str + '00000000';
                for (i = 0; i < str.length; i += 8) {
                    out.push(parseInt(str.substr(i, 8), 16) ^ 0);
                }
                return sjcl.bitArray.clamp(out, len * 4);
            },
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
            if (!this._key[0]) {
                this._precompute();
            }
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
            return new sjcl.hash.sha256().update(data).finalize();
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
            reset: function () {
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
                if (typeof data === 'string') {
                    data = sjcl.codec.utf8String.toBits(data);
                }
                var i,
                    b = (this._buffer = sjcl.bitArray.concat(this._buffer, data)),
                    ol = this._length,
                    nl = (this._length = ol + sjcl.bitArray.bitLength(data));
                if (nl > 9007199254740991) {
                    throw new sjcl.exception.invalid('Cannot hash more than 2^53 - 1 bits');
                }

                if (typeof Uint32Array !== 'undefined') {
                    var c = new Uint32Array(b);
                    var j = 0;
                    for (i = 512 + ol - ((512 + ol) & 511); i <= nl; i += 512) {
                        this._block(c.subarray(16 * j, 16 * (j + 1)));
                        j += 1;
                    }
                    b.splice(0, 16 * j);
                } else {
                    for (i = 512 + ol - ((512 + ol) & 511); i <= nl; i += 512) {
                        this._block(b.splice(0, 16));
                    }
                }
                return this;
            },

            /**
             * Complete hashing and output the hash value.
             * @return {bitArray} The hash value, an array of 8 big-endian words.
             */
            finalize: function () {
                var i,
                    b = this._buffer,
                    h = this._h;

                // Round out and push the buffer
                b = sjcl.bitArray.concat(b, [sjcl.bitArray.partial(1, 1)]);

                // Round out the buffer to a multiple of 16 words, less the 2 length words.
                for (i = b.length + 2; i & 15; i++) {
                    b.push(0);
                }

                // append the length
                b.push(Math.floor(this._length / 0x100000000));
                b.push(this._length | 0);

                while (b.length) {
                    this._block(b.splice(0, 16));
                }

                this.reset();
                return h;
            },

            /**
             * The SHA-256 initialization vector, to be precomputed.
             * @private
             */
            _init: [],
            /*
      _init:[0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19],
      */

            /**
             * The SHA-256 hash key, to be precomputed.
             * @private
             */
            _key: [],
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
                var i = 0,
                    prime = 2,
                    factor,
                    isPrime;

                function frac(x) {
                    return ((x - Math.floor(x)) * 0x100000000) | 0;
                }

                for (; i < 64; prime++) {
                    isPrime = true;
                    for (factor = 2; factor * factor <= prime; factor++) {
                        if (prime % factor === 0) {
                            isPrime = false;
                            break;
                        }
                    }
                    if (isPrime) {
                        if (i < 8) {
                            this._init[i] = frac(Math.pow(prime, 1 / 2));
                        }
                        this._key[i] = frac(Math.pow(prime, 1 / 3));
                        i++;
                    }
                }
            },

            /**
             * Perform one cycle of SHA-256.
             * @param {Uint32Array|bitArray} w one block of words.
             * @private
             */
            _block: function (w) {
                var i,
                    tmp,
                    a,
                    b,
                    h = this._h,
                    k = this._key,
                    h0 = h[0],
                    h1 = h[1],
                    h2 = h[2],
                    h3 = h[3],
                    h4 = h[4],
                    h5 = h[5],
                    h6 = h[6],
                    h7 = h[7];

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
                for (i = 0; i < 64; i++) {
                    // load up the input word for this round
                    if (i < 16) {
                        tmp = w[i];
                    } else {
                        a = w[(i + 1) & 15];
                        b = w[(i + 14) & 15];
                        tmp = w[i & 15] =
                            (((a >>> 7) ^ (a >>> 18) ^ (a >>> 3) ^ (a << 25) ^ (a << 14)) +
                                ((b >>> 17) ^ (b >>> 19) ^ (b >>> 10) ^ (b << 15) ^ (b << 13)) +
                                w[i & 15] +
                                w[(i + 9) & 15]) |
                            0;
                    }

                    tmp =
                        tmp +
                        h7 +
                        ((h4 >>> 6) ^ (h4 >>> 11) ^ (h4 >>> 25) ^ (h4 << 26) ^ (h4 << 21) ^ (h4 << 7)) +
                        (h6 ^ (h4 & (h5 ^ h6))) +
                        k[i]; // | 0;

                    // shift register
                    h7 = h6;
                    h6 = h5;
                    h5 = h4;
                    h4 = (h3 + tmp) | 0;
                    h3 = h2;
                    h2 = h1;
                    h1 = h0;

                    h0 =
                        (tmp +
                            ((h1 & h2) ^ (h3 & (h1 ^ h2))) +
                            ((h1 >>> 2) ^ (h1 >>> 13) ^ (h1 >>> 22) ^ (h1 << 30) ^ (h1 << 19) ^ (h1 << 10))) |
                        0;
                }

                h[0] = (h[0] + h0) | 0;
                h[1] = (h[1] + h1) | 0;
                h[2] = (h[2] + h2) | 0;
                h[3] = (h[3] + h3) | 0;
                h[4] = (h[4] + h4) | 0;
                h[5] = (h[5] + h5) | 0;
                h[6] = (h[6] + h6) | 0;
                h[7] = (h[7] + h7) | 0;
            },
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
            var exKey = [[], []],
                i,
                bs = Hash.prototype.blockSize / 32;
            this._baseHash = [new Hash(), new Hash()];

            if (key.length > bs) {
                key = Hash.hash(key);
            }

            for (i = 0; i < bs; i++) {
                exKey[0][i] = key[i] ^ 0x36363636;
                exKey[1][i] = key[i] ^ 0x5c5c5c5c;
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
                throw new sjcl.exception.invalid('encrypt on already updated hmac called!');
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
            var w = this._resultHash.finalize(),
                result = new this._hash(this._baseHash[1]).update(w).finalize();

            this.reset();

            return result;
        };

        return sjcl;
    })();

    function getDataKeySync(sessionKey, domainKey, inputData) {
        // eslint-disable-next-line new-cap
        const hmac = new sjcl.misc.hmac(sjcl.codec.utf8String.toBits(sessionKey + domainKey), sjcl.hash.sha256);
        return sjcl.codec.hex.fromBits(hmac.encrypt(inputData));
    }

    function isJSONArray(value) {
      return Array.isArray(value);
    }
    function isJSONObject(value) {
      return value !== null && typeof value === 'object' && (value.constructor === undefined ||
      // for example Object.create(null)
      value.constructor.name === 'Object') // do not match on classes or Array
      ;
    }

    /**
     * Test deep equality of two JSON values, objects, or arrays
     */
    // TODO: write unit tests
    function isEqual(a, b) {
      // FIXME: this function will return false for two objects with the same keys
      //  but different order of keys
      return JSON.stringify(a) === JSON.stringify(b);
    }

    /**
     * Get all but the last items from an array
     */
    // TODO: write unit tests
    function initial(array) {
      return array.slice(0, array.length - 1);
    }

    /**
     * Get the last item from an array
     */
    // TODO: write unit tests
    function last(array) {
      return array[array.length - 1];
    }

    /**
     * Test whether a value is an Object or an Array (and not a primitive JSON value)
     */
    // TODO: write unit tests
    function isObjectOrArray(value) {
      return typeof value === 'object' && value !== null;
    }

    /**
     * Immutability helpers
     *
     * inspiration:
     *
     * https://www.npmjs.com/package/seamless-immutable
     * https://www.npmjs.com/package/ih
     * https://www.npmjs.com/package/mutatis
     * https://github.com/mariocasciaro/object-path-immutable
     */

    /**
     * Shallow clone of an Object, Array, or value
     * Symbols are cloned too.
     */
    function shallowClone(value) {
      if (isJSONArray(value)) {
        // copy array items
        const copy = value.slice();

        // copy all symbols
        Object.getOwnPropertySymbols(value).forEach(symbol => {
          // eslint-disable-next-line @typescript-eslint/ban-ts-comment
          // @ts-ignore
          copy[symbol] = value[symbol];
        });
        return copy;
      } else if (isJSONObject(value)) {
        // copy object properties
        const copy = {
          ...value
        };

        // copy all symbols
        Object.getOwnPropertySymbols(value).forEach(symbol => {
          // eslint-disable-next-line @typescript-eslint/ban-ts-comment
          // @ts-ignore
          copy[symbol] = value[symbol];
        });
        return copy;
      } else {
        return value;
      }
    }

    /**
     * Update a value in an object in an immutable way.
     * If the value is unchanged, the original object will be returned
     */
    function applyProp(object, key, value) {
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      if (object[key] === value) {
        // return original object unchanged when the new value is identical to the old one
        return object;
      } else {
        const updatedObject = shallowClone(object);
        // eslint-disable-next-line @typescript-eslint/ban-ts-comment
        // @ts-ignore
        updatedObject[key] = value;
        return updatedObject;
      }
    }

    /**
     * helper function to get a nested property in an object or array
     *
     * @return Returns the field when found, or undefined when the path doesn't exist
     */
    function getIn(object, path) {
      let value = object;
      let i = 0;
      while (i < path.length) {
        if (isJSONObject(value)) {
          value = value[path[i]];
        } else if (isJSONArray(value)) {
          value = value[parseInt(path[i])];
        } else {
          value = undefined;
        }
        i++;
      }
      return value;
    }

    /**
     * helper function to replace a nested property in an object with a new value
     * without mutating the object itself.
     *
     * @param object
     * @param path
     * @param value
     * @param [createPath=false]
     *                    If true, `path` will be created when (partly) missing in
     *                    the object. For correctly creating nested Arrays or
     *                    Objects, the function relies on `path` containing number
     *                    in case of array indexes.
     *                    If false (default), an error will be thrown when the
     *                    path doesn't exist.
     * @return Returns a new, updated object or array
     */
    function setIn(object, path, value) {
      let createPath = arguments.length > 3 && arguments[3] !== undefined ? arguments[3] : false;
      if (path.length === 0) {
        return value;
      }
      const key = path[0];
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      const updatedValue = setIn(object ? object[key] : undefined, path.slice(1), value, createPath);
      if (isJSONObject(object) || isJSONArray(object)) {
        return applyProp(object, key, updatedValue);
      } else {
        if (createPath) {
          const newObject = IS_INTEGER_REGEX.test(key) ? [] : {};
          // eslint-disable-next-line @typescript-eslint/ban-ts-comment
          // @ts-ignore
          newObject[key] = updatedValue;
          return newObject;
        } else {
          throw new Error('Path does not exist');
        }
      }
    }
    const IS_INTEGER_REGEX = /^\d+$/;

    /**
     * helper function to replace a nested property in an object with a new value
     * without mutating the object itself.
     *
     * @return  Returns a new, updated object or array
     */
    function updateIn(object, path, transform) {
      if (path.length === 0) {
        return transform(object);
      }
      if (!isObjectOrArray(object)) {
        throw new Error('Path doesn\'t exist');
      }
      const key = path[0];
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      const updatedValue = updateIn(object[key], path.slice(1), transform);
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      return applyProp(object, key, updatedValue);
    }

    /**
     * helper function to delete a nested property in an object
     * without mutating the object itself.
     *
     * @return Returns a new, updated object or array
     */
    function deleteIn(object, path) {
      if (path.length === 0) {
        return object;
      }
      if (!isObjectOrArray(object)) {
        throw new Error('Path does not exist');
      }
      if (path.length === 1) {
        const key = path[0];
        if (!(key in object)) {
          // key doesn't exist. return object unchanged
          return object;
        } else {
          const updatedObject = shallowClone(object);
          if (isJSONArray(updatedObject)) {
            updatedObject.splice(parseInt(key), 1);
          }
          if (isJSONObject(updatedObject)) {
            delete updatedObject[key];
          }
          return updatedObject;
        }
      }
      const key = path[0];
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      const updatedValue = deleteIn(object[key], path.slice(1));
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      return applyProp(object, key, updatedValue);
    }

    /**
     * Insert a new item in an array at a specific index.
     * Example usage:
     *
     *     insertAt({arr: [1,2,3]}, ['arr', '2'], 'inserted')  // [1,2,'inserted',3]
     */
    function insertAt(document, path, value) {
      const parentPath = path.slice(0, path.length - 1);
      const index = path[path.length - 1];
      return updateIn(document, parentPath, items => {
        if (!Array.isArray(items)) {
          throw new TypeError('Array expected at path ' + JSON.stringify(parentPath));
        }
        const updatedItems = shallowClone(items);
        updatedItems.splice(parseInt(index), 0, value);
        return updatedItems;
      });
    }

    /**
     * Test whether a path exists in a JSON object
     * @return Returns true if the path exists, else returns false
     */
    function existsIn(document, path) {
      if (document === undefined) {
        return false;
      }
      if (path.length === 0) {
        return true;
      }
      if (document === null) {
        return false;
      }

      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      return existsIn(document[path[0]], path.slice(1));
    }

    /**
     * Parse a JSON Pointer
     */
    function parseJSONPointer(pointer) {
      const path = pointer.split('/');
      path.shift(); // remove the first empty entry

      return path.map(p => p.replace(/~1/g, '/').replace(/~0/g, '~'));
    }

    /**
     * Compile a JSON Pointer
     */
    function compileJSONPointer(path) {
      return path.map(compileJSONPointerProp).join('');
    }

    /**
     * Compile a single path property from a JSONPath
     */
    function compileJSONPointerProp(pathProp) {
      return '/' + String(pathProp).replace(/~/g, '~0').replace(/\//g, '~1');
    }

    /**
     * Apply a patch to a JSON object
     * The original JSON object will not be changed,
     * instead, the patch is applied in an immutable way
     */
    function immutableJSONPatch(document, operations, options) {
      let updatedDocument = document;
      for (let i = 0; i < operations.length; i++) {
        validateJSONPatchOperation(operations[i]);
        let operation = operations[i];
        const path = parsePath(updatedDocument, operation.path);
        if (operation.op === 'add') {
          updatedDocument = add(updatedDocument, path, operation.value);
        } else if (operation.op === 'remove') {
          updatedDocument = remove(updatedDocument, path);
        } else if (operation.op === 'replace') {
          updatedDocument = replace(updatedDocument, path, operation.value);
        } else if (operation.op === 'copy') {
          updatedDocument = copy(updatedDocument, path, parseFrom(operation.from));
        } else if (operation.op === 'move') {
          updatedDocument = move(updatedDocument, path, parseFrom(operation.from));
        } else if (operation.op === 'test') {
          test(updatedDocument, path, operation.value);
        } else {
          throw new Error('Unknown JSONPatch operation ' + JSON.stringify(operation));
        }
      }
      return updatedDocument;
    }

    /**
     * Replace an existing item
     */
    function replace(document, path, value) {
      return setIn(document, path, value);
    }

    /**
     * Remove an item or property
     */
    function remove(document, path) {
      return deleteIn(document, path);
    }

    /**
     * Add an item or property
     */
    function add(document, path, value) {
      if (isArrayItem(document, path)) {
        return insertAt(document, path, value);
      } else {
        return setIn(document, path, value);
      }
    }

    /**
     * Copy a value
     */
    function copy(document, path, from) {
      const value = getIn(document, from);
      if (isArrayItem(document, path)) {
        return insertAt(document, path, value);
      } else {
        const value = getIn(document, from);
        return setIn(document, path, value);
      }
    }

    /**
     * Move a value
     */
    function move(document, path, from) {
      const value = getIn(document, from);
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      const removedJson = deleteIn(document, from);
      return isArrayItem(removedJson, path) ? insertAt(removedJson, path, value) : setIn(removedJson, path, value);
    }

    /**
     * Test whether the data contains the provided value at the specified path.
     * Throws an error when the test fails
     */
    function test(document, path, value) {
      if (value === undefined) {
        throw new Error(`Test failed: no value provided (path: "${compileJSONPointer(path)}")`);
      }
      if (!existsIn(document, path)) {
        throw new Error(`Test failed: path not found (path: "${compileJSONPointer(path)}")`);
      }
      const actualValue = getIn(document, path);
      if (!isEqual(actualValue, value)) {
        throw new Error(`Test failed, value differs (path: "${compileJSONPointer(path)}")`);
      }
    }
    function isArrayItem(document, path) {
      if (path.length === 0) {
        return false;
      }
      const parent = getIn(document, initial(path));
      return Array.isArray(parent);
    }

    /**
     * Resolve the path index of an array, resolves indexes '-'
     * @returns Returns the resolved path
     */
    function resolvePathIndex(document, path) {
      if (last(path) !== '-') {
        return path;
      }
      const parentPath = initial(path);
      const parent = getIn(document, parentPath);

      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      return parentPath.concat(parent.length);
    }

    /**
     * Validate a JSONPatch operation.
     * Throws an error when there is an issue
     */
    function validateJSONPatchOperation(operation) {
      // TODO: write unit tests
      const ops = ['add', 'remove', 'replace', 'copy', 'move', 'test'];
      if (!ops.includes(operation.op)) {
        throw new Error('Unknown JSONPatch op ' + JSON.stringify(operation.op));
      }
      if (typeof operation.path !== 'string') {
        throw new Error('Required property "path" missing or not a string in operation ' + JSON.stringify(operation));
      }
      if (operation.op === 'copy' || operation.op === 'move') {
        if (typeof operation.from !== 'string') {
          throw new Error('Required property "from" missing or not a string in operation ' + JSON.stringify(operation));
        }
      }
    }
    function parsePath(document, pointer) {
      return resolvePathIndex(document, parseJSONPointer(pointer));
    }
    function parseFrom(fromPointer) {
      return parseJSONPointer(fromPointer);
    }

    /**
     * FIXME: this function is not needed anymore after FF xray removal
     * Like Object.defineProperty, but with support for Firefox's mozProxies.
     * @param {any} object - object whose property we are wrapping (most commonly a prototype, e.g. globalThis.BatteryManager.prototype)
     * @param {string} propertyName
     * @param {import('./wrapper-utils').StrictPropertyDescriptor} descriptor - requires all descriptor options to be defined because we can't validate correctness based on TS types
     */
    function defineProperty(object, propertyName, descriptor) {
        objectDefineProperty(object, propertyName, descriptor);
    }

    /**
     * return a proxy to `newFn` that fakes .toString() and .toString.toString() to resemble the `origFn`.
     * WARNING: do NOT proxy toString multiple times, as it will not work as expected.
     *
     * @param {*} newFn
     * @param {*} origFn
     * @param {string} [mockValue] - when provided, .toString() will return this value
     */
    function wrapToString(newFn, origFn, mockValue) {
        if (typeof newFn !== 'function' || typeof origFn !== 'function') {
            return newFn;
        }

        return new Proxy(newFn, { get: toStringGetTrap(origFn, mockValue) });
    }

    /**
     * generate a proxy handler trap that fakes .toString() and .toString.toString() to resemble the `targetFn`.
     * Note that it should be used as the get() trap.
     * @param {*} targetFn
     * @param {string} [mockValue] - when provided, .toString() will return this value
     * @returns { (target: any, prop: string, receiver: any) => any }
     */
    function toStringGetTrap(targetFn, mockValue) {
        // We wrap two levels deep to handle toString.toString() calls
        return function get(target, prop, receiver) {
            if (prop === 'toString') {
                const origToString = Reflect.get(targetFn, 'toString', targetFn);
                const toStringProxy = new Proxy(origToString, {
                    apply(target, thisArg, argumentsList) {
                        // only mock toString() when called on the proxy itself. If the method is applied to some other object, it should behave as a normal toString()
                        if (thisArg === receiver) {
                            if (mockValue) {
                                return mockValue;
                            }
                            return Reflect.apply(target, targetFn, argumentsList);
                        } else {
                            return Reflect.apply(target, thisArg, argumentsList);
                        }
                    },
                    get(target, prop, receiver) {
                        // handle toString.toString() result
                        if (prop === 'toString') {
                            const origToStringToString = Reflect.get(origToString, 'toString', origToString);
                            const toStringToStringProxy = new Proxy(origToStringToString, {
                                apply(target, thisArg, argumentsList) {
                                    if (thisArg === toStringProxy) {
                                        return Reflect.apply(target, origToString, argumentsList);
                                    } else {
                                        return Reflect.apply(target, thisArg, argumentsList);
                                    }
                                },
                            });
                            return toStringToStringProxy;
                        }
                        return Reflect.get(target, prop, receiver);
                    },
                });
                return toStringProxy;
            }
            return Reflect.get(target, prop, receiver);
        };
    }

    /**
     * Wrap a `get`/`set` or `value` property descriptor. Only for data properties. For methods, use wrapMethod(). For constructors, use wrapConstructor().
     * @param {any} object - object whose property we are wrapping (most commonly a prototype, e.g. globalThis.Screen.prototype)
     * @param {string} propertyName
     * @param {Partial<PropertyDescriptor>} descriptor
     * @param {typeof Object.defineProperty} definePropertyFn - function to use for defining the property
     * @returns {PropertyDescriptor|undefined} original property descriptor, or undefined if it's not found
     */
    function wrapProperty(object, propertyName, descriptor, definePropertyFn) {
        if (!object) {
            return;
        }

        /** @type {StrictPropertyDescriptor} */
        // @ts-expect-error - we check for undefined below
        const origDescriptor = getOwnPropertyDescriptor(object, propertyName);
        if (!origDescriptor) {
            // this happens if the property is not implemented in the browser
            return;
        }

        if (
            ('value' in origDescriptor && 'value' in descriptor) ||
            ('get' in origDescriptor && 'get' in descriptor) ||
            ('set' in origDescriptor && 'set' in descriptor)
        ) {
            definePropertyFn(object, propertyName, {
                ...origDescriptor,
                ...descriptor,
            });
            return origDescriptor;
        } else {
            // if the property is defined with get/set it must be wrapped with a get/set. If it's defined with a `value`, it must be wrapped with a `value`
            throw new Error(`Property descriptor for ${propertyName} may only include the following keys: ${objectKeys(origDescriptor)}`);
        }
    }

    /**
     * Wrap a method descriptor. Only for function properties. For data properties, use wrapProperty(). For constructors, use wrapConstructor().
     * @param {any} object - object whose property we are wrapping (most commonly a prototype, e.g. globalThis.Bluetooth.prototype)
     * @param {string} propertyName
     * @param {(originalFn, ...args) => any } wrapperFn - wrapper function receives the original function as the first argument
     * @param {DefinePropertyFn} definePropertyFn - function to use for defining the property
     * @returns {PropertyDescriptor|undefined} original property descriptor, or undefined if it's not found
     */
    function wrapMethod(object, propertyName, wrapperFn, definePropertyFn) {
        if (!object) {
            return;
        }

        /** @type {StrictPropertyDescriptor} */
        // @ts-expect-error - we check for undefined below
        const origDescriptor = getOwnPropertyDescriptor(object, propertyName);
        if (!origDescriptor) {
            // this happens if the property is not implemented in the browser
            return;
        }

        // @ts-expect-error - we check for undefined below
        const origFn = origDescriptor.value;
        if (!origFn || typeof origFn !== 'function') {
            // method properties are expected to be defined with a `value`
            throw new Error(`Property ${propertyName} does not look like a method`);
        }

        const newFn = wrapToString(function () {
            return wrapperFn.call(this, origFn, ...arguments);
        }, origFn);

        definePropertyFn(object, propertyName, {
            ...origDescriptor,
            value: newFn,
        });
        return origDescriptor;
    }

    /**
     * @template {keyof typeof globalThis} StandardInterfaceName
     * @param {StandardInterfaceName} interfaceName - the name of the interface to shim (must be some known standard API, e.g. 'MediaSession')
     * @param {typeof globalThis[StandardInterfaceName]} ImplClass - the class to use as the shim implementation
     * @param {DefineInterfaceOptions} options - options for defining the interface
     * @param {DefinePropertyFn} definePropertyFn - function to use for defining the property
     */
    function shimInterface(interfaceName, ImplClass, options, definePropertyFn) {

        /** @type {DefineInterfaceOptions} */
        const defaultOptions = {
            allowConstructorCall: false,
            disallowConstructor: false,
            constructorErrorMessage: 'Illegal constructor',
            wrapToString: true,
        };

        const fullOptions = {
            interfaceDescriptorOptions: { writable: true, enumerable: false, configurable: true, value: ImplClass },
            ...defaultOptions,
            ...options,
        };

        // In some cases we can get away without a full proxy, but in many cases below we need it.
        // For example, we can't redefine `prototype` property on ES6 classes.
        // Se we just always wrap the class to make the code more maintaibnable

        /** @type {ProxyHandler<Function>} */
        const proxyHandler = {};

        // handle the case where the constructor is called without new
        if (fullOptions.allowConstructorCall) {
            // make the constructor function callable without new
            proxyHandler.apply = function (target, thisArg, argumentsList) {
                return Reflect.construct(target, argumentsList, target);
            };
        }

        // make the constructor function throw when called without new
        if (fullOptions.disallowConstructor) {
            proxyHandler.construct = function () {
                throw new TypeError(fullOptions.constructorErrorMessage);
            };
        }

        if (fullOptions.wrapToString) {
            // mask toString() on class methods. `ImplClass.prototype` is non-configurable: we can't override or proxy it, so we have to wrap each method individually
            for (const [prop, descriptor] of objectEntries(getOwnPropertyDescriptors(ImplClass.prototype))) {
                if (prop !== 'constructor' && descriptor.writable && typeof descriptor.value === 'function') {
                    ImplClass.prototype[prop] = new Proxy(descriptor.value, {
                        get: toStringGetTrap(descriptor.value, `function ${prop}() { [native code] }`),
                    });
                }
            }

            // wrap toString on the constructor function itself
            Object.assign(proxyHandler, {
                get: toStringGetTrap(ImplClass, `function ${interfaceName}() { [native code] }`),
            });
        }

        // Note that instanceof should still work, since the `.prototype` object is proxied too:
        // Interface() instanceof Interface === true
        // ImplClass() instanceof Interface === true
        const Interface = new Proxy(ImplClass, proxyHandler);

        // Make sure that Interface().constructor === Interface (not ImplClass)
        if (ImplClass.prototype?.constructor === ImplClass) {
            /** @type {StrictDataDescriptor} */
            // @ts-expect-error - As long as ImplClass is a normal class, it should have the prototype property
            const descriptor = getOwnPropertyDescriptor(ImplClass.prototype, 'constructor');
            if (descriptor.writable) {
                ImplClass.prototype.constructor = Interface;
            }
        }

        // mock the name property
        definePropertyFn(ImplClass, 'name', {
            value: interfaceName,
            configurable: true,
            enumerable: false,
            writable: false,
        });

        // interfaces are exposed directly on the global object, not on its prototype
        definePropertyFn(globalThis, interfaceName, { ...fullOptions.interfaceDescriptorOptions, value: Interface });
    }

    /**
     * Define a missing standard property on a global (prototype) object. Only for data properties.
     * For constructors, use shimInterface().
     * Most of the time, you'd want to call shimInterface() first to shim the class itself (MediaSession), and then shimProperty() for the global singleton instance (Navigator.prototype.mediaSession).
     * @template Base
     * @template {keyof Base & string} K
     * @param {Base} baseObject - object whose property we are shimming (most commonly a prototype object, e.g. Navigator.prototype)
     * @param {K} propertyName - name of the property to shim (e.g. 'mediaSession')
     * @param {Base[K]} implInstance - instance to use as the shim (e.g. new MyMediaSession())
     * @param {boolean} readOnly - whether the property should be read-only
     * @param {DefinePropertyFn} definePropertyFn - function to use for defining the property
     */
    function shimProperty(baseObject, propertyName, implInstance, readOnly, definePropertyFn) {
        // @ts-expect-error - implInstance is a class instance
        const ImplClass = implInstance.constructor;

        // mask toString() and toString.toString() on the instance
        const proxiedInstance = new Proxy(implInstance, {
            get: toStringGetTrap(implInstance, `[object ${ImplClass.name}]`),
        });

        /** @type {StrictPropertyDescriptor} */
        let descriptor;

        // Note that we only cover most common cases: a getter for "readonly" properties, and a value descriptor for writable properties.
        // But there could be other cases, e.g. a property with both a getter and a setter. These could be defined with a raw defineProperty() call.
        // Important: make sure to cover each new shim with a test that verifies that all descriptors match the standard API.
        if (readOnly) {
            const getter = function get() {
                return proxiedInstance;
            };
            const proxiedGetter = new Proxy(getter, {
                get: toStringGetTrap(getter, `function get ${propertyName}() { [native code] }`),
            });
            descriptor = {
                configurable: true,
                enumerable: true,
                get: proxiedGetter,
            };
        } else {
            descriptor = {
                configurable: true,
                enumerable: true,
                writable: true,
                value: proxiedInstance,
            };
        }

        definePropertyFn(baseObject, propertyName, descriptor);
    }

    /**
     * @callback DefinePropertyFn
     * @param {object} baseObj
     * @param {PropertyKey} propertyName
     * @param {StrictPropertyDescriptor} descriptor
     * @returns {object}
     */

    /**
     * @typedef {Object} BaseStrictPropertyDescriptor
     * @property {boolean} configurable
     * @property {boolean} enumerable
     */

    /**
     * @typedef {BaseStrictPropertyDescriptor & { value: any; writable: boolean }} StrictDataDescriptor
     * @typedef {BaseStrictPropertyDescriptor & { get: () => any; set: (v: any) => void }} StrictAccessorDescriptor
     * @typedef {BaseStrictPropertyDescriptor & { get: () => any }} StrictGetDescriptor
     * @typedef {BaseStrictPropertyDescriptor & { set: (v: any) => void }} StrictSetDescriptor
     * @typedef {StrictDataDescriptor | StrictAccessorDescriptor | StrictGetDescriptor | StrictSetDescriptor} StrictPropertyDescriptor
     */

    /**
     * @typedef {Object} BaseDefineInterfaceOptions
     * @property {string} [constructorErrorMessage]
     * @property {boolean} wrapToString
     */

    /**
     * @typedef {{ allowConstructorCall: true; disallowConstructor: false }} DefineInterfaceOptionsWithAllowConstructorCallMixin
     */

    /**
     * @typedef {{ allowConstructorCall: false; disallowConstructor: true }} DefineInterfaceOptionsWithDisallowConstructorMixin
     */

    /**
     * @typedef {{ allowConstructorCall: false; disallowConstructor: false }} DefineInterfaceOptionsDefaultMixin
     */

    /**
     * @typedef {BaseDefineInterfaceOptions & (DefineInterfaceOptionsWithAllowConstructorCallMixin | DefineInterfaceOptionsWithDisallowConstructorMixin | DefineInterfaceOptionsDefaultMixin)} DefineInterfaceOptions
     */

    /**
     * A wrapper for messaging on Windows.
     *
     * This requires 3 methods to be available, see {@link WindowsMessagingConfig} for details
     *
     * @document messaging/lib/examples/windows.example.js
     *
     */
    // eslint-disable-next-line @typescript-eslint/no-unused-vars

    /**
     * An implementation of {@link MessagingTransport} for Windows
     *
     * All messages go through `window.chrome.webview` APIs
     *
     * @implements {MessagingTransport}
     */
    class WindowsMessagingTransport {
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
                String: window.String,
            };
            for (const [methodName, fn] of Object.entries(this.config.methods)) {
                if (typeof fn !== 'function') {
                    throw new Error('cannot create WindowsMessagingTransport, missing the method: ' + methodName);
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
            // convert the message to window-specific naming
            const data = this.globals.JSONparse(this.globals.JSONstringify(msg.params || {}));
            const outgoing = WindowsRequestMessage.fromRequest(msg, data);

            // send the message
            this.config.methods.postMessage(outgoing);

            // compare incoming messages against the `msg.id`
            const comparator = (eventData) => {
                return eventData.featureName === msg.featureName && eventData.context === msg.context && eventData.id === msg.id;
            };

            /**
             * @param data
             * @return {data is import('../index.js').MessageResponse}
             */
            function isMessageResponse(data) {
                if ('result' in data) return true;
                if ('error' in data) return true;
                return false;
            }

            // now wait for a matching message
            return new this.globals.Promise((resolve, reject) => {
                try {
                    this._subscribe(comparator, opts, (value, unsubscribe) => {
                        unsubscribe();

                        if (!isMessageResponse(value)) {
                            console.warn('unknown response type', value);
                            return reject(new this.globals.Error('unknown response'));
                        }

                        if (value.result) {
                            return resolve(value.result);
                        }

                        const message = this.globals.String(value.error?.message || 'unknown error');
                        reject(new this.globals.Error(message));
                    });
                } catch (e) {
                    reject(e);
                }
            });
        }

        /**
         * @param {import('../index.js').Subscription} msg
         * @param {(value: unknown | undefined) => void} callback
         */
        subscribe(msg, callback) {
            // compare incoming messages against the `msg.subscriptionName`
            const comparator = (eventData) => {
                return (
                    eventData.featureName === msg.featureName &&
                    eventData.context === msg.context &&
                    eventData.subscriptionName === msg.subscriptionName
                );
            };

            // only forward the 'params' from a SubscriptionEvent
            const cb = (eventData) => {
                return callback(eventData.params);
            };

            // now listen for matching incoming messages.
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
            // if already aborted, reject immediately
            if (options?.signal?.aborted) {
                throw new DOMException('Aborted', 'AbortError');
            }
            /** @type {(()=>void) | undefined} */
            // eslint-disable-next-line prefer-const
            let teardown;

            /**
             * @param {MessageEvent} event
             */
            const idHandler = (event) => {
                if (this.messagingContext.env === 'production') {
                    if (event.origin !== null && event.origin !== undefined) {
                        console.warn('ignoring because evt.origin is not `null` or `undefined`');
                        return;
                    }
                }
                if (!event.data) {
                    console.warn('data absent from message');
                    return;
                }
                if (comparator(event.data)) {
                    if (!teardown) throw new Error('unreachable');
                    callback(event.data, teardown);
                }
            };

            // what to do if this promise is aborted
            const abortHandler = () => {
                teardown?.();
                throw new DOMException('Aborted', 'AbortError');
            };

            // console.log('DEBUG: handler setup', { config, comparator })

            this.config.methods.addEventListener('message', idHandler);
            options?.signal?.addEventListener('abort', abortHandler);

            teardown = () => {
                // console.log('DEBUG: handler teardown', { config, comparator })

                this.config.methods.removeEventListener('message', idHandler);
                options?.signal?.removeEventListener('abort', abortHandler);
            };

            return () => {
                teardown?.();
            };
        }
    }

    /**
     * To construct this configuration object, you need access to 3 methods
     *
     * - `postMessage`
     * - `addEventListener`
     * - `removeEventListener`
     *
     * These would normally be available on Windows via the following:
     *
     * - `window.chrome.webview.postMessage`
     * - `window.chrome.webview.addEventListener`
     * - `window.chrome.webview.removeEventListener`
     *
     * Depending on where the script is running, we may want to restrict access to those globals. On the native
     * side those handlers `window.chrome.webview` handlers might be deleted and replaces with in-scope variables, such as:
     *
     * [Example](./examples/windows.example.js)
     *
     */
    class WindowsMessagingConfig {
        /**
         * @param {object} params
         * @param {WindowsInteropMethods} params.methods
         * @internal
         */
        constructor(params) {
            /**
             * The methods required for communication
             */
            this.methods = params.methods;
            /**
             * @type {'windows'}
             */
            this.platform = 'windows';
        }
    }

    /**
     * This data type represents a message sent to the Windows
     * platform via `window.chrome.webview.postMessage`.
     *
     * **NOTE**: This is sent when a response is *not* expected
     */
    class WindowsNotification {
        /**
         * @param {object} params
         * @param {string} params.Feature
         * @param {string} params.SubFeatureName
         * @param {string} params.Name
         * @param {Record<string, any>} [params.Data]
         * @internal
         */
        constructor(params) {
            /**
             * Alias for: {@link NotificationMessage.context}
             */
            this.Feature = params.Feature;
            /**
             * Alias for: {@link NotificationMessage.featureName}
             */
            this.SubFeatureName = params.SubFeatureName;
            /**
             * Alias for: {@link NotificationMessage.method}
             */
            this.Name = params.Name;
            /**
             * Alias for: {@link NotificationMessage.params}
             */
            this.Data = params.Data;
        }

        /**
         * Helper to convert a {@link NotificationMessage} to a format that Windows can support
         * @param {NotificationMessage} notification
         * @returns {WindowsNotification}
         */
        static fromNotification(notification, data) {
            /** @type {WindowsNotification} */
            const output = {
                Data: data,
                Feature: notification.context,
                SubFeatureName: notification.featureName,
                Name: notification.method,
            };
            return output;
        }
    }

    /**
     * This data type represents a message sent to the Windows
     * platform via `window.chrome.webview.postMessage` when it
     * expects a response
     */
    class WindowsRequestMessage {
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
            /** @type {WindowsRequestMessage} */
            const output = {
                Data: data,
                Feature: msg.context,
                SubFeatureName: msg.featureName,
                Name: msg.method,
                Id: msg.id,
            };
            return output;
        }
    }

    /**
     * These are all the shared data types used throughout. Transports receive these types and
     * can choose how to deliver the message to their respective native platforms.
     *
     * - Notifications via {@link NotificationMessage}
     * - Request -> Response via {@link RequestMessage} and {@link MessageResponse}
     * - Subscriptions via {@link Subscription}
     *
     * Note: For backwards compatibility, some platforms may alter the data shape within the transport.
     *
     * @module Messaging Schema
     *
     */

    /**
     * This is the format of an outgoing message.
     *
     * - See {@link MessageResponse} for what's expected in a response
     *
     * **NOTE**:
     * - Windows will alter this before it's sent, see: {@link Messaging.WindowsRequestMessage}
     */
    class RequestMessage {
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
            /**
             * The global context for this message. For example, something like `contentScopeScripts` or `specialPages`
             * @type {string}
             */
            this.context = params.context;
            /**
             * The name of the sub-feature, such as `duckPlayer` or `clickToLoad`
             * @type {string}
             */
            this.featureName = params.featureName;
            /**
             * The name of the handler to be executed on the native side
             */
            this.method = params.method;
            /**
             * The `id` that native sides can use when sending back a response
             */
            this.id = params.id;
            /**
             * Optional data payload - must be a plain key/value object
             */
            this.params = params.params;
        }
    }

    /**
     * **NOTE**:
     * - Windows will alter this before it's sent, see: {@link Messaging.WindowsNotification}
     */
    class NotificationMessage {
        /**
         * @param {object} params
         * @param {string} params.context
         * @param {string} params.featureName
         * @param {string} params.method
         * @param {Record<string, any>} [params.params]
         * @internal
         */
        constructor(params) {
            /**
             * The global context for this message. For example, something like `contentScopeScripts` or `specialPages`
             */
            this.context = params.context;
            /**
             * The name of the sub-feature, such as `duckPlayer` or `clickToLoad`
             */
            this.featureName = params.featureName;
            /**
             * The name of the handler to be executed on the native side
             */
            this.method = params.method;
            /**
             * An optional payload
             */
            this.params = params.params;
        }
    }

    class Subscription {
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
    }

    /**
     * @param {RequestMessage} request
     * @param {Record<string, any>} data
     * @return {data is MessageResponse}
     */
    function isResponseFor(request, data) {
        if ('result' in data) {
            return data.featureName === request.featureName && data.context === request.context && data.id === request.id;
        }
        if ('error' in data) {
            if ('message' in data.error) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param {Subscription} sub
     * @param {Record<string, any>} data
     * @return {data is SubscriptionEvent}
     */
    function isSubscriptionEventFor(sub, data) {
        if ('subscriptionName' in data) {
            return data.featureName === sub.featureName && data.context === sub.context && data.subscriptionName === sub.subscriptionName;
        }

        return false;
    }

    /**
     *
     * A wrapper for messaging on WebKit platforms. It supports modern WebKit messageHandlers
     * along with encryption for older versions (like macOS Catalina)
     *
     * Note: If you wish to support Catalina then you'll need to implement the native
     * part of the message handling, see {@link WebkitMessagingTransport} for details.
     */
    // eslint-disable-next-line @typescript-eslint/no-unused-vars

    /**
     * @example
     * On macOS 11+, this will just call through to `window.webkit.messageHandlers.x.postMessage`
     *
     * Eg: for a `foo` message defined in Swift that accepted the payload `{"bar": "baz"}`, the following
     * would occur:
     *
     * ```js
     * const json = await window.webkit.messageHandlers.foo.postMessage({ bar: "baz" });
     * const response = JSON.parse(json)
     * ```
     *
     * @example
     * On macOS 10 however, the process is a little more involved. A method will be appended to `window`
     * that allows the response to be delivered there instead. It's not exactly this, but you can visualize the flow
     * as being something along the lines of:
     *
     * ```js
     * // add the window method
     * window["_0123456"] = (response) => {
     *    // decrypt `response` and deliver the result to the caller here
     *    // then remove the temporary method
     *    delete window['_0123456']
     * };
     *
     * // send the data + `messageHanding` values
     * window.webkit.messageHandlers.foo.postMessage({
     *   bar: "baz",
     *   messagingHandling: {
     *     methodName: "_0123456",
     *     secret: "super-secret",
     *     key: [1, 2, 45, 2],
     *     iv: [34, 4, 43],
     *   }
     * });
     *
     * // later in swift, the following JavaScript snippet will be executed
     * (() => {
     *   window['_0123456']({
     *     ciphertext: [12, 13, 4],
     *     tag: [3, 5, 67, 56]
     *   })
     * })()
     * ```
     * @implements {MessagingTransport}
     */
    class WebkitMessagingTransport {
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
                        secret: this.config.secret,
                    },
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
                return this.globals.JSONparse(response || '{}');
            }

            try {
                const randMethodName = this.createRandMethodName();
                const key = await this.createRandKey();
                const iv = this.createRandIv();

                const { ciphertext, tag } = await new this.globals.Promise((/** @type {any} */ resolve) => {
                    this.generateRandomMethod(randMethodName, resolve);

                    // @ts-expect-error - this is a carve-out for catalina that will be removed soon
                    data.messageHandling = new SecureMessagingParams({
                        methodName: randMethodName,
                        secret: this.config.secret,
                        key: this.globals.Arrayfrom(key),
                        iv: this.globals.Arrayfrom(iv),
                    });
                    this.wkSend(handler, data);
                });

                const cipher = new this.globals.Uint8Array([...ciphertext, ...tag]);
                const decrypted = await this.decrypt(cipher, key, iv);
                return this.globals.JSONparse(decrypted || '{}');
            } catch (e) {
                // re-throw when the error is just a 'MissingHandler'
                if (e instanceof MissingHandler) {
                    throw e;
                } else {
                    console.error('decryption failed', e);
                    console.error(e);
                    return { error: e };
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
                // forward the error if one was given explicity
                if (data.error) {
                    throw new Error(data.error.message);
                }
            }

            throw new Error('an unknown error occurred');
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
                },
            });
        }

        /**
         * @internal
         * @return {string}
         */
        randomString() {
            return '' + this.globals.getRandomValues(new this.globals.Uint32Array(1))[0];
        }

        /**
         * @internal
         * @return {string}
         */
        createRandMethodName() {
            return '_' + this.randomString();
        }

        /**
         * @type {{name: string, length: number}}
         * @internal
         */
        algoObj = {
            name: 'AES-GCM',
            length: 256,
        };

        /**
         * @returns {Promise<Uint8Array>}
         * @internal
         */
        async createRandKey() {
            const key = await this.globals.generateKey(this.algoObj, true, ['encrypt', 'decrypt']);
            const exportedKey = await this.globals.exportKey('raw', key);
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
            const cryptoKey = await this.globals.importKey('raw', key, 'AES-GCM', false, ['decrypt']);
            const algo = {
                name: 'AES-GCM',
                iv,
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
            if (!handlers) throw new MissingHandler('window.webkit.messageHandlers was absent', 'all');
            for (const webkitMessageHandlerName of handlerNames) {
                if (typeof handlers[webkitMessageHandlerName]?.postMessage === 'function') {
                    /**
                     * `bind` is used here to ensure future calls to the captured
                     * `postMessage` have the correct `this` context
                     */
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
            // for now, bail if there's already a handler setup for this subscription
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
                        console.warn('Received a message that did not match the subscription', data);
                    }
                },
            });
            return () => {
                this.globals.ReflectDeleteProperty(this.globals.window, msg.subscriptionName);
            };
        }
    }

    /**
     * Use this configuration to create an instance of {@link Messaging} for WebKit platforms
     *
     * We support modern WebKit environments *and* macOS Catalina.
     *
     * Please see {@link WebkitMessagingTransport} for details on how messages are sent/received
     *
     * [Example](./examples/webkit.example.js)
     */
    class WebkitMessagingConfig {
        /**
         * @param {object} params
         * @param {boolean} params.hasModernWebkitAPI
         * @param {string[]} params.webkitMessageHandlerNames
         * @param {string} params.secret
         * @internal
         */
        constructor(params) {
            /**
             * Whether or not the current WebKit Platform supports secure messaging
             * by default (eg: macOS 11+)
             */
            this.hasModernWebkitAPI = params.hasModernWebkitAPI;
            /**
             * A list of WebKit message handler names that a user script can send.
             *
             * For example, if the native platform can receive messages through this:
             *
             * ```js
             * window.webkit.messageHandlers.foo.postMessage('...')
             * ```
             *
             * then, this property would be:
             *
             * ```js
             * webkitMessageHandlerNames: ['foo']
             * ```
             */
            this.webkitMessageHandlerNames = params.webkitMessageHandlerNames;
            /**
             * A string provided by native platforms to be sent with future outgoing
             * messages.
             */
            this.secret = params.secret;
        }
    }

    /**
     * This is the additional payload that gets appended to outgoing messages.
     * It's used in the Swift side to encrypt the response that comes back
     */
    class SecureMessagingParams {
        /**
         * @param {object} params
         * @param {string} params.methodName
         * @param {string} params.secret
         * @param {number[]} params.key
         * @param {number[]} params.iv
         */
        constructor(params) {
            /**
             * The method that's been appended to `window` to be called later
             */
            this.methodName = params.methodName;
            /**
             * The secret used to ensure message sender validity
             */
            this.secret = params.secret;
            /**
             * The CipherKey as number[]
             */
            this.key = params.key;
            /**
             * The Initial Vector as number[]
             */
            this.iv = params.iv;
        }
    }

    /**
     * Capture some globals used for messaging handling to prevent page
     * scripts from tampering with this
     */
    function captureGlobals() {
        // Create base with null prototype
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
            capturedWebkitHandlers: {},
        };
        if (isSecureContext) {
            // skip for HTTP content since window.crypto.subtle is unavailable
            globals.generateKey = window.crypto.subtle.generateKey.bind(window.crypto.subtle);
            globals.exportKey = window.crypto.subtle.exportKey.bind(window.crypto.subtle);
            globals.importKey = window.crypto.subtle.importKey.bind(window.crypto.subtle);
            globals.encrypt = window.crypto.subtle.encrypt.bind(window.crypto.subtle);
            globals.decrypt = window.crypto.subtle.decrypt.bind(window.crypto.subtle);
        }
        return globals;
    }

    /**
     *
     * A wrapper for messaging on Android.
     *
     * You must share a {@link AndroidMessagingConfig} instance between features
     *
     */
    // eslint-disable-next-line @typescript-eslint/no-unused-vars

    /**
     * @typedef {import('../index.js').Subscription} Subscription
     * @typedef {import('../index.js').MessagingContext} MessagingContext
     * @typedef {import('../index.js').RequestMessage} RequestMessage
     * @typedef {import('../index.js').NotificationMessage} NotificationMessage
     */

    /**
     * An implementation of {@link MessagingTransport} for Android
     *
     * All messages go through `window.chrome.webview` APIs
     *
     * @implements {MessagingTransport}
     */
    class AndroidMessagingTransport {
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
            } catch (e) {
                console.error('.notify failed', e);
            }
        }

        /**
         * @param {RequestMessage} msg
         * @return {Promise<any>}
         */
        request(msg) {
            return new Promise((resolve, reject) => {
                // subscribe early
                const unsub = this.config.subscribe(msg.id, handler);

                try {
                    this.config.sendMessageThrows?.(JSON.stringify(msg));
                } catch (e) {
                    unsub();
                    reject(new Error('request failed to send: ' + e.message || 'unknown error'));
                }

                function handler(data) {
                    if (isResponseFor(msg, data)) {
                        // success case, forward .result only
                        if (data.result) {
                            resolve(data.result || {});
                            return unsub();
                        }

                        // error case, forward the error as a regular promise rejection
                        if (data.error) {
                            reject(new Error(data.error.message));
                            return unsub();
                        }

                        // getting here is undefined behavior
                        unsub();
                        throw new Error('unreachable: must have `result` or `error` key by this point');
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
    }

    /**
     * Android shared messaging configuration. This class should be constructed once and then shared
     * between features (because of the way it modifies globals).
     *
     * For example, if Android is injecting a JavaScript module like C-S-S which contains multiple 'sub-features', then
     * this class would be instantiated once and then shared between all sub-features.
     *
     * The following example shows all the fields that are required to be passed in:
     *
     * ```js
     * const config = new AndroidMessagingConfig({
     *     // a value that native has injected into the script
     *     messageSecret: 'abc',
     *
     *     // the name of the window method that android will deliver responses through
     *     messageCallback: 'callback_123',
     *
     *     // the `@JavascriptInterface` name from native that will be used to receive messages
     *     javascriptInterface: "ARandomValue",
     *
     *     // the global object where methods will be registered
     *     target: globalThis
     * });
     * ```
     * Once an instance of {@link AndroidMessagingConfig} is created, you can then use it to construct
     * many instances of {@link Messaging} (one per feature). See `examples/android.example.js` for an example.
     *
     *
     * ## Native integration
     *
     * Assuming you have the following:
     *  - a `@JavascriptInterface` named `"ContentScopeScripts"`
     *  - a sub-feature called `"featureA"`
     *  - and a method on `"featureA"` called `"helloWorld"`
     *
     * Then delivering a {@link NotificationMessage} to it, would be roughly this in JavaScript (remember `params` is optional though)
     *
     * ```
     * const secret = "abc";
     * const json = JSON.stringify({
     *     context: "ContentScopeScripts",
     *     featureName: "featureA",
     *     method: "helloWorld",
     *     params: { "foo": "bar" }
     * });
     * window.ContentScopeScripts.process(json, secret)
     * ```
     * When you receive the JSON payload (note that it will be a string), you'll need to deserialize/verify it according to {@link "Messaging Implementation Guide"}
     *
     *
     * ## Responding to a {@link RequestMessage}, or pushing a {@link SubscriptionEvent}
     *
     * If you receive a {@link RequestMessage}, you'll need to deliver a {@link MessageResponse}.
     * Similarly, if you want to push new data, you need to deliver a {@link SubscriptionEvent}. In both
     * cases you'll do this through a global `window` method. Given the snippet below, this is how it would relate
     * to the {@link AndroidMessagingConfig}:
     *
     * - `$messageCallback` matches {@link AndroidMessagingConfig.messageCallback}
     * - `$messageSecret` matches {@link AndroidMessagingConfig.messageSecret}
     * - `$message` is JSON string that represents one of {@link MessageResponse} or {@link SubscriptionEvent}
     *
     * ```
     * object ReplyHandler {
     *     fun constructReply(message: String, messageCallback: String, messageSecret: String): String {
     *         return """
     *             (function() {
     *                 window['$messageCallback']('$messageSecret', $message);
     *             })();
     *         """.trimIndent()
     *     }
     * }
     * ```
     */
    class AndroidMessagingConfig {
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

            /**
             * @type {Map<string, (msg: MessageResponse | SubscriptionEvent) => void>}
             * @internal
             */
            this.listeners = new globalThis.Map();

            /**
             * Capture the global handler and remove it from the global object.
             */
            this._captureGlobalHandler();

            /**
             * Assign the incoming handler method to the global object.
             */
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
            // do nothing if the response is empty
            // this prevents the next `in` checks from throwing in test/debug scenarios
            if (!payload) return this._log('no response');

            // if the payload has an 'id' field, then it's a message response
            if ('id' in payload) {
                if (this.listeners.has(payload.id)) {
                    this._tryCatch(() => this.listeners.get(payload.id)?.(payload));
                } else {
                    this._log('no listeners for ', payload);
                }
            }

            // if the payload has an 'subscriptionName' field, then it's a push event
            if ('subscriptionName' in payload) {
                if (this.listeners.has(payload.subscriptionName)) {
                    this._tryCatch(() => this.listeners.get(payload.subscriptionName)?.(payload));
                } else {
                    this._log('no subscription listeners for ', payload);
                }
            }
        }

        /**
         *
         * @param {(...args: any[]) => any} fn
         * @param {string} [context]
         */
        _tryCatch(fn, context = 'none') {
            try {
                return fn();
            } catch (e) {
                if (this.debug) {
                    console.error('AndroidMessagingConfig error:', context);
                    console.error(e);
                }
            }
        }

        /**
         * @param {...any} args
         */
        _log(...args) {
            if (this.debug) {
                console.log('AndroidMessagingConfig', ...args);
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
                    this._log('Android messaging interface not available', javascriptInterface);
                };
            }
        }

        /**
         * Assign the incoming handler method to the global object.
         * This is the method that Android will call to deliver messages.
         */
        _assignHandlerMethod() {
            /**
             * @type {(secret: string, response: MessageResponse | SubscriptionEvent) => void}
             */
            const responseHandler = (providedSecret, response) => {
                if (providedSecret === this.messageSecret) {
                    this._dispatch(response);
                }
            };

            Object.defineProperty(this.target, this.messageCallback, {
                value: responseHandler,
            });
        }
    }

    /**
     *
     * An abstraction for communications between JavaScript and host platforms.
     *
     * 1) First you construct your platform-specific configuration (eg: {@link WebkitMessagingConfig})
     * 2) Then use that to get an instance of the Messaging utility which allows
     * you to send and receive data in a unified way
     * 3) Each platform implements {@link MessagingTransport} along with its own Configuration
     *     - For example, to learn what configuration is required for Webkit, see: {@link WebkitMessagingConfig}
     *     - Or, to learn about how messages are sent and received in Webkit, see {@link WebkitMessagingTransport}
     *
     * ## Links
     * Please see the following links for examples
     *
     * - Windows: {@link WindowsMessagingConfig}
     * - Webkit: {@link WebkitMessagingConfig}
     * - Android: {@link AndroidMessagingConfig}
     * - Schema: {@link "Messaging Schema"}
     * - Implementation Guide: {@link "Messaging Implementation Guide"}
     *
     * @module Messaging
     */

    /**
     * Common options/config that are *not* transport specific.
     */
    class MessagingContext {
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
    }

    /**
     * @typedef {WebkitMessagingConfig | WindowsMessagingConfig | AndroidMessagingConfig | TestTransportConfig} MessagingConfig
     */

    /**
     *
     */
    class Messaging {
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
         * @throws {MissingHandler}
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
                params: data,
            });
            this.transport.notify(message);
        }

        /**
         * Send a request, and wait for a response
         * @throws {MissingHandler}
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
            const id = globalThis?.crypto?.randomUUID?.() || name + '.response';
            const message = new RequestMessage({
                context: this.messagingContext.context,
                featureName: this.messagingContext.featureName,
                method: name,
                params: data,
                id,
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
                subscriptionName: name,
            });
            return this.transport.subscribe(msg, callback);
        }
    }

    /**
     * Use this to create testing transport on the fly.
     * It's useful for debugging, and for enabling scripts to run in
     * other environments - for example, testing in a browser without the need
     * for a full integration
     */
    class TestTransportConfig {
        /**
         * @param {MessagingTransport} impl
         */
        constructor(impl) {
            this.impl = impl;
        }
    }

    /**
     * @implements {MessagingTransport}
     */
    class TestTransport {
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
    }

    /**
     * @param {WebkitMessagingConfig | WindowsMessagingConfig | AndroidMessagingConfig | TestTransportConfig} config
     * @param {MessagingContext} messagingContext
     * @returns {MessagingTransport}
     */
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
        if (config instanceof TestTransportConfig) {
            return new TestTransport(config, messagingContext);
        }
        throw new Error('unreachable');
    }

    /**
     * Thrown when a handler cannot be found
     */
    class MissingHandler extends Error {
        /**
         * @param {string} message
         * @param {string} handlerName
         */
        constructor(message, handlerName) {
            super(message);
            this.handlerName = handlerName;
        }
    }

    /**
     * Workaround defining MessagingTransport locally because "import()" is not working in `@implements`
     * @typedef {import('@duckduckgo/messaging').MessagingTransport} MessagingTransport
     */

    /**
     * @deprecated - A temporary constructor for the extension to make the messaging config
     */
    function extensionConstructMessagingConfig() {
        const messagingTransport = new SendMessageMessagingTransport();
        return new TestTransportConfig(messagingTransport);
    }

    /**
     * A temporary implementation of {@link MessagingTransport} to communicate with Extensions.
     * It wraps the current messaging system that calls `sendMessage`
     *
     * @implements {MessagingTransport}
     * @deprecated - Use this only to communicate with Android and the Extension while support to {@link Messaging}
     * is not ready and we need to use `sendMessage()`.
     */
    class SendMessageMessagingTransport {
        /**
         * Queue of callbacks to be called with messages sent from the Platform.
         * This is used to connect requests with responses and to trigger subscriptions callbacks.
         */
        _queue = new Set();

        constructor() {
            this.globals = {
                window: globalThis,
                globalThis,
                JSONparse: globalThis.JSON.parse,
                JSONstringify: globalThis.JSON.stringify,
                Promise: globalThis.Promise,
                Error: globalThis.Error,
                String: globalThis.String,
            };
        }

        /**
         * Callback for update() handler. This connects messages sent from the Platform
         * with callback functions in the _queue.
         * @param {any} response
         */
        onResponse(response) {
            this._queue.forEach((subscription) => subscription(response));
        }

        /**
         * @param {import('@duckduckgo/messaging').NotificationMessage} msg
         */
        notify(msg) {
            let params = msg.params;

            // Unwrap 'setYoutubePreviewsEnabled' params to match expected payload
            // for sendMessage()
            if (msg.method === 'setYoutubePreviewsEnabled') {
                params = msg.params?.youtubePreviewsEnabled;
            }
            // Unwrap 'updateYouTubeCTLAddedFlag' params to match expected payload
            // for sendMessage()
            if (msg.method === 'updateYouTubeCTLAddedFlag') {
                params = msg.params?.youTubeCTLAddedFlag;
            }

            legacySendMessage(msg.method, params);
        }

        /**
         * @param {import('@duckduckgo/messaging').RequestMessage} req
         * @return {Promise<any>}
         */
        request(req) {
            let comparator = (eventData) => {
                return eventData.responseMessageType === req.method;
            };
            let params = req.params;

            // Adapts request for 'getYouTubeVideoDetails' by identifying the correct
            // response for each request and updating params to expect current
            // implementation specifications.
            if (req.method === 'getYouTubeVideoDetails') {
                comparator = (eventData) => {
                    return (
                        eventData.responseMessageType === req.method &&
                        eventData.response &&
                        eventData.response.videoURL === req.params?.videoURL
                    );
                };
                params = req.params?.videoURL;
            }

            legacySendMessage(req.method, params);

            return new this.globals.Promise((resolve) => {
                this._subscribe(comparator, (msgRes, unsubscribe) => {
                    unsubscribe();

                    return resolve(msgRes.response);
                });
            });
        }

        /**
         * @param {import('@duckduckgo/messaging').Subscription} msg
         * @param {(value: unknown | undefined) => void} callback
         */
        subscribe(msg, callback) {
            const comparator = (eventData) => {
                return eventData.messageType === msg.subscriptionName || eventData.responseMessageType === msg.subscriptionName;
            };

            // only forward the 'params' ('response' in current format), to match expected
            // callback from a SubscriptionEvent
            const cb = (eventData) => {
                return callback(eventData.response);
            };
            return this._subscribe(comparator, cb);
        }

        /**
         * @param {(eventData: any) => boolean} comparator
         * @param {(value: any, unsubscribe: (()=>void)) => void} callback
         * @internal
         */
        _subscribe(comparator, callback) {
            /** @type {(()=>void) | undefined} */
            // eslint-disable-next-line prefer-const
            let teardown;

            /**
             * @param {MessageEvent} event
             */
            const idHandler = (event) => {
                if (!event) {
                    console.warn('no message available');
                    return;
                }
                if (comparator(event)) {
                    if (!teardown) throw new this.globals.Error('unreachable');
                    callback(event, teardown);
                }
            };
            this._queue.add(idHandler);

            teardown = () => {
                this._queue.delete(idHandler);
            };

            return () => {
                teardown?.();
            };
        }
    }

    /**
     * @typedef {object} AssetConfig
     * @property {string} regularFontUrl
     * @property {string} boldFontUrl
     */

    /**
     * @typedef {object} Site
     * @property {string | null} domain
     * @property {boolean} [isBroken]
     * @property {boolean} [allowlisted]
     * @property {string[]} [enabledFeatures]
     */

    class ContentFeature {
        /** @type {import('./utils.js').RemoteConfig | undefined} */
        #bundledConfig;
        /** @type {object | undefined} */
        #trackerLookup;
        /** @type {boolean | undefined} */
        #documentOriginIsTracker;
        /** @type {Record<string, unknown> | undefined} */
        // eslint-disable-next-line no-unused-private-class-members
        #bundledfeatureSettings;
        /** @type {import('../../messaging').Messaging} */
        // eslint-disable-next-line no-unused-private-class-members
        #messaging;
        /** @type {boolean} */
        #isDebugFlagSet = false;

        /** @type {{ debug?: boolean, desktopModeEnabled?: boolean, forcedZoomEnabled?: boolean, featureSettings?: Record<string, unknown>, assets?: AssetConfig | undefined, site: Site, messagingConfig?: import('@duckduckgo/messaging').MessagingConfig } | null} */
        #args;

        constructor(featureName) {
            this.name = featureName;
            this.#args = null;
            this.monitor = new PerformanceMonitor();
        }

        get isDebug() {
            return this.#args?.debug || false;
        }

        get desktopModeEnabled() {
            return this.#args?.desktopModeEnabled || false;
        }

        get forcedZoomEnabled() {
            return this.#args?.forcedZoomEnabled || false;
        }

        /**
         * @param {import('./utils').Platform} platform
         */
        set platform(platform) {
            this._platform = platform;
        }

        get platform() {
            // @ts-expect-error - Type 'Platform | undefined' is not assignable to type 'Platform'
            return this._platform;
        }

        /**
         * @type {AssetConfig | undefined}
         */
        get assetConfig() {
            return this.#args?.assets;
        }

        /**
         * @returns {boolean}
         */
        get documentOriginIsTracker() {
            return !!this.#documentOriginIsTracker;
        }

        /**
         * @returns {object}
         **/
        get trackerLookup() {
            return this.#trackerLookup || {};
        }

        /**
         * @returns {import('./utils.js').RemoteConfig | undefined}
         **/
        get bundledConfig() {
            return this.#bundledConfig;
        }

        /**
         * @deprecated as we should make this internal to the class and not used externally
         * @return {MessagingContext}
         */
        _createMessagingContext() {
            const contextName = 'contentScopeScripts';

            return new MessagingContext({
                context: contextName,
                env: this.isDebug ? 'development' : 'production',
                featureName: this.name,
            });
        }

        /**
         * Lazily create a messaging instance for the given Platform + feature combo
         *
         * @return {import('@duckduckgo/messaging').Messaging}
         */
        get messaging() {
            if (this._messaging) return this._messaging;
            const messagingContext = this._createMessagingContext();
            let messagingConfig = this.#args?.messagingConfig;
            if (!messagingConfig) {
                if (this.platform?.name !== 'extension') throw new Error('Only extension messaging supported, all others should be passed in');
                messagingConfig = extensionConstructMessagingConfig();
            }
            this._messaging = new Messaging(messagingContext, messagingConfig);
            return this._messaging;
        }

        /**
         * Get the value of a config setting.
         * If the value is not set, return the default value.
         * If the value is not an object, return the value.
         * If the value is an object, check its type property.
         * @param {string} attrName
         * @param {any} defaultValue - The default value to use if the config setting is not set
         * @returns The value of the config setting or the default value
         */
        getFeatureAttr(attrName, defaultValue) {
            const configSetting = this.getFeatureSetting(attrName);
            return processAttr(configSetting, defaultValue);
        }

        /**
         * Return a specific setting from the feature settings
         * If the "settings" key within the config has a "domains" key, it will be used to override the settings.
         * This uses JSONPatch to apply the patches to settings before getting the setting value.
         * For example.com getFeatureSettings('val') will return 1:
         * ```json
         *  {
         *      "settings": {
         *         "domains": [
         *             {
         *                "domain": "example.com",
         *                "patchSettings": [
         *                    { "op": "replace", "path": "/val", "value": 1 }
         *                ]
         *             }
         *         ]
         *      }
         *  }
         * ```
         * "domain" can either be a string or an array of strings.

         * For boolean states you should consider using getFeatureSettingEnabled.
         * @param {string} featureKeyName
         * @param {string} [featureName]
         * @returns {any}
         */
        getFeatureSetting(featureKeyName, featureName) {
            let result = this._getFeatureSettings(featureName);
            if (featureKeyName === 'domains') {
                throw new Error('domains is a reserved feature setting key name');
            }
            const domainMatch = [...this.matchDomainFeatureSetting('domains')].sort((a, b) => {
                return a.domain.length - b.domain.length;
            });
            for (const match of domainMatch) {
                if (match.patchSettings === undefined) {
                    continue;
                }
                try {
                    result = immutableJSONPatch(result, match.patchSettings);
                } catch (e) {
                    console.error('Error applying patch settings', e);
                }
            }
            return result?.[featureKeyName];
        }

        /**
         * Return the settings object for a feature
         * @param {string} [featureName] - The name of the feature to get the settings for; defaults to the name of the feature
         * @returns {any}
         */
        _getFeatureSettings(featureName) {
            const camelFeatureName = featureName || camelcase(this.name);
            return this.#args?.featureSettings?.[camelFeatureName];
        }

        /**
         * For simple boolean settings, return true if the setting is 'enabled'
         * For objects, verify the 'state' field is 'enabled'.
         * This allows for future forwards compatibility with more complex settings if required.
         * For example:
         * ```json
         * {
         *    "toggle": "enabled"
         * }
         * ```
         * Could become later (without breaking changes):
         * ```json
         * {
         *   "toggle": {
         *       "state": "enabled",
         *       "someOtherKey": 1
         *   }
         * }
         * ```
         * This also supports domain overrides as per `getFeatureSetting`.
         * @param {string} featureKeyName
         * @param {string} [featureName]
         * @returns {boolean}
         */
        getFeatureSettingEnabled(featureKeyName, featureName) {
            const result = this.getFeatureSetting(featureKeyName, featureName);
            if (typeof result === 'object') {
                return result.state === 'enabled';
            }
            return result === 'enabled';
        }

        /**
         * Given a config key, interpret the value as a list of domain overrides, and return the elements that match the current page
         * Consider using patchSettings instead as per `getFeatureSetting`.
         * @param {string} featureKeyName
         * @return {any[]}
         * @private
         */
        matchDomainFeatureSetting(featureKeyName) {
            const domain = this.#args?.site.domain;
            if (!domain) return [];
            const domains = this._getFeatureSettings()?.[featureKeyName] || [];
            return domains.filter((rule) => {
                if (Array.isArray(rule.domain)) {
                    return rule.domain.some((domainRule) => {
                        return matchHostname(domain, domainRule);
                    });
                }
                return matchHostname(domain, rule.domain);
            });
        }

        init(args) {}

        callInit(args) {
            const mark = this.monitor.mark(this.name + 'CallInit');
            this.#args = args;
            this.platform = args.platform;
            this.init(args);
            mark.end();
            this.measure();
        }

        load(args) {}

        /**
         * This is a wrapper around `this.messaging.notify` that applies the
         * auto-generated types from the `src/types` folder. It's used
         * to provide per-feature type information based on the schemas
         * in `src/messages`
         *
         * @type {import("@duckduckgo/messaging").Messaging['notify']}
         */
        notify(...args) {
            const [name, params] = args;
            this.messaging.notify(name, params);
        }

        /**
         * This is a wrapper around `this.messaging.request` that applies the
         * auto-generated types from the `src/types` folder. It's used
         * to provide per-feature type information based on the schemas
         * in `src/messages`
         *
         * @type {import("@duckduckgo/messaging").Messaging['request']}
         */
        request(...args) {
            const [name, params] = args;
            return this.messaging.request(name, params);
        }

        /**
         * This is a wrapper around `this.messaging.subscribe` that applies the
         * auto-generated types from the `src/types` folder. It's used
         * to provide per-feature type information based on the schemas
         * in `src/messages`
         *
         * @type {import("@duckduckgo/messaging").Messaging['subscribe']}
         */
        subscribe(...args) {
            const [name, cb] = args;
            return this.messaging.subscribe(name, cb);
        }

        /**
         * @param {import('./content-scope-features.js').LoadArgs} args
         */
        callLoad(args) {
            const mark = this.monitor.mark(this.name + 'CallLoad');
            this.#args = args;
            this.platform = args.platform;
            this.#bundledConfig = args.bundledConfig;
            // If we have a bundled config, treat it as a regular config
            // This will be overriden by the remote config if it is available
            if (this.#bundledConfig && this.#args) {
                const enabledFeatures = computeEnabledFeatures(args.bundledConfig, args.site.domain, this.platform.version);
                this.#args.featureSettings = parseFeatureSettings(args.bundledConfig, enabledFeatures);
            }
            this.#trackerLookup = args.trackerLookup;
            this.#documentOriginIsTracker = args.documentOriginIsTracker;
            this.load(args);
            mark.end();
        }

        measure() {
            if (this.#args?.debug) {
                this.monitor.measureAll();
            }
        }

        update() {}

        /**
         * Register a flag that will be added to page breakage reports
         */
        addDebugFlag() {
            if (this.#isDebugFlagSet) return;
            this.#isDebugFlagSet = true;
            this.messaging?.notify('addDebugFlag', {
                flag: this.name,
            });
        }

        /**
         * Define a property descriptor with debug flags.
         * Mainly used for defining new properties. For overriding existing properties, consider using wrapProperty(), wrapMethod() and wrapConstructor().
         * @param {any} object - object whose property we are wrapping (most commonly a prototype, e.g. globalThis.BatteryManager.prototype)
         * @param {string} propertyName
         * @param {import('./wrapper-utils').StrictPropertyDescriptor} descriptor - requires all descriptor options to be defined because we can't validate correctness based on TS types
         */
        defineProperty(object, propertyName, descriptor) {
            // make sure to send a debug flag when the property is used
            // NOTE: properties passing data in `value` would not be caught by this
            ['value', 'get', 'set'].forEach((k) => {
                const descriptorProp = descriptor[k];
                if (typeof descriptorProp === 'function') {
                    const addDebugFlag = this.addDebugFlag.bind(this);
                    const wrapper = new Proxy$1(descriptorProp, {
                        apply(target, thisArg, argumentsList) {
                            addDebugFlag();
                            return Reflect$1.apply(descriptorProp, thisArg, argumentsList);
                        },
                    });
                    descriptor[k] = wrapToString(wrapper, descriptorProp);
                }
            });

            return defineProperty(object, propertyName, descriptor);
        }

        /**
         * Wrap a `get`/`set` or `value` property descriptor. Only for data properties. For methods, use wrapMethod(). For constructors, use wrapConstructor().
         * @param {any} object - object whose property we are wrapping (most commonly a prototype, e.g. globalThis.Screen.prototype)
         * @param {string} propertyName
         * @param {Partial<PropertyDescriptor>} descriptor
         * @returns {PropertyDescriptor|undefined} original property descriptor, or undefined if it's not found
         */
        wrapProperty(object, propertyName, descriptor) {
            return wrapProperty(object, propertyName, descriptor, this.defineProperty.bind(this));
        }

        /**
         * Wrap a method descriptor. Only for function properties. For data properties, use wrapProperty(). For constructors, use wrapConstructor().
         * @param {any} object - object whose property we are wrapping (most commonly a prototype, e.g. globalThis.Bluetooth.prototype)
         * @param {string} propertyName
         * @param {(originalFn, ...args) => any } wrapperFn - wrapper function receives the original function as the first argument
         * @returns {PropertyDescriptor|undefined} original property descriptor, or undefined if it's not found
         */
        wrapMethod(object, propertyName, wrapperFn) {
            return wrapMethod(object, propertyName, wrapperFn, this.defineProperty.bind(this));
        }

        /**
         * @template {keyof typeof globalThis} StandardInterfaceName
         * @param {StandardInterfaceName} interfaceName - the name of the interface to shim (must be some known standard API, e.g. 'MediaSession')
         * @param {typeof globalThis[StandardInterfaceName]} ImplClass - the class to use as the shim implementation
         * @param {import('./wrapper-utils').DefineInterfaceOptions} options
         */
        shimInterface(interfaceName, ImplClass, options) {
            return shimInterface(interfaceName, ImplClass, options, this.defineProperty.bind(this));
        }

        /**
         * Define a missing standard property on a global (prototype) object. Only for data properties.
         * For constructors, use shimInterface().
         * Most of the time, you'd want to call shimInterface() first to shim the class itself (MediaSession), and then shimProperty() for the global singleton instance (Navigator.prototype.mediaSession).
         * @template Base
         * @template {keyof Base & string} K
         * @param {Base} instanceHost - object whose property we are shimming (most commonly a prototype object, e.g. Navigator.prototype)
         * @param {K} instanceProp - name of the property to shim (e.g. 'mediaSession')
         * @param {Base[K]} implInstance - instance to use as the shim (e.g. new MyMediaSession())
         * @param {boolean} [readOnly] - whether the property should be read-only (default: false)
         */
        shimProperty(instanceHost, instanceProp, implInstance, readOnly = false) {
            return shimProperty(instanceHost, instanceProp, implInstance, readOnly, this.defineProperty.bind(this));
        }
    }

    class FingerprintingAudio extends ContentFeature {
        init(args) {
            const { sessionKey, site } = args;
            const domainKey = site.domain;

            // In place modify array data to remove fingerprinting
            function transformArrayData(channelData, domainKey, sessionKey, thisArg) {
                let { audioKey } = getCachedResponse(thisArg, args);
                if (!audioKey) {
                    let cdSum = 0;
                    for (const k in channelData) {
                        cdSum += channelData[k];
                    }
                    // If the buffer is blank, skip adding data
                    if (cdSum === 0) {
                        return;
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

            const copyFromChannelProxy = new DDGProxy(this, AudioBuffer.prototype, 'copyFromChannel', {
                apply(target, thisArg, args) {
                    const [source, channelNumber, startInChannel] = args;
                    // This is implemented in a different way to canvas purely because calling the function copied the original value, which is not ideal
                    if (
                        // If channelNumber is longer than arrayBuffer number of channels then call the default method to throw
                        // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                        channelNumber > thisArg.numberOfChannels ||
                        // If startInChannel is longer than the arrayBuffer length then call the default method to throw
                        // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                        startInChannel > thisArg.length
                    ) {
                        // The normal return value
                        return DDGReflect.apply(target, thisArg, args);
                    }
                    try {
                        // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                        // Call the protected getChannelData we implement, slice from the startInChannel value and assign to the source array
                        thisArg
                            .getChannelData(channelNumber)
                            .slice(startInChannel)
                            .forEach((val, index) => {
                                source[index] = val;
                            });
                    } catch {
                        return DDGReflect.apply(target, thisArg, args);
                    }
                },
            });
            copyFromChannelProxy.overload();

            const cacheExpiry = 60;
            const cacheData = new WeakMap();
            function getCachedResponse(thisArg, args) {
                const data = cacheData.get(thisArg);
                const timeNow = Date.now();
                if (data && data.args === JSON.stringify(args) && data.expires > timeNow) {
                    data.expires = timeNow + cacheExpiry;
                    cacheData.set(thisArg, data);
                    return data;
                }
                return { audioKey: null };
            }

            function setCache(thisArg, args, audioKey) {
                cacheData.set(thisArg, { args: JSON.stringify(args), expires: Date.now() + cacheExpiry, audioKey });
            }

            const getChannelDataProxy = new DDGProxy(this, AudioBuffer.prototype, 'getChannelData', {
                apply(target, thisArg, args) {
                    // The normal return value
                    const channelData = DDGReflect.apply(target, thisArg, args);
                    // Anything we do here should be caught and ignored silently
                    try {
                        // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                        transformArrayData(channelData, domainKey, sessionKey, thisArg, args);
                    } catch {}
                    return channelData;
                },
            });
            getChannelDataProxy.overload();

            const audioMethods = ['getByteTimeDomainData', 'getFloatTimeDomainData', 'getByteFrequencyData', 'getFloatFrequencyData'];
            for (const methodName of audioMethods) {
                const proxy = new DDGProxy(this, AnalyserNode.prototype, methodName, {
                    apply(target, thisArg, args) {
                        DDGReflect.apply(target, thisArg, args);
                        // Anything we do here should be caught and ignored silently
                        try {
                            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                            transformArrayData(args[0], domainKey, sessionKey, thisArg, args);
                        } catch {}
                    },
                });
                proxy.overload();
            }
        }
    }

    /**
     * Overwrites the Battery API if present in the browser.
     * It will return the values defined in the getBattery function to the client,
     * as well as prevent any script from listening to events.
     */
    class FingerprintingBattery extends ContentFeature {
        init() {
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            if (globalThis.navigator.getBattery) {
                const BatteryManager = globalThis.BatteryManager;

                const spoofedValues = {
                    charging: true,
                    chargingTime: 0,
                    dischargingTime: Infinity,
                    level: 1,
                };
                const eventProperties = ['onchargingchange', 'onchargingtimechange', 'ondischargingtimechange', 'onlevelchange'];

                for (const [prop, val] of Object.entries(spoofedValues)) {
                    try {
                        this.defineProperty(BatteryManager.prototype, prop, {
                            enumerable: true,
                            configurable: true,
                            get: () => {
                                return val;
                            },
                        });
                    } catch (e) {}
                }
                for (const eventProp of eventProperties) {
                    try {
                        this.defineProperty(BatteryManager.prototype, eventProp, {
                            enumerable: true,
                            configurable: true,
                            set: (x) => x, // noop
                            get: () => {
                                return null;
                            },
                        });
                    } catch (e) {}
                }
            }
        }
    }

    function getDefaultExportFromCjs (x) {
    	return x && x.__esModule && Object.prototype.hasOwnProperty.call(x, 'default') ? x['default'] : x;
    }

    var alea$1 = {exports: {}};

    var alea = alea$1.exports;

    var hasRequiredAlea;

    function requireAlea () {
    	if (hasRequiredAlea) return alea$1.exports;
    	hasRequiredAlea = 1;
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


    		if (module.exports) {
    		  module.exports = impl;
    		} else {
    		  this.alea = impl;
    		}

    		})(
    		  alea,
    		  module); 
    	} (alea$1));
    	return alea$1.exports;
    }

    var xor128$1 = {exports: {}};

    var xor128 = xor128$1.exports;

    var hasRequiredXor128;

    function requireXor128 () {
    	if (hasRequiredXor128) return xor128$1.exports;
    	hasRequiredXor128 = 1;
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

    		if (module.exports) {
    		  module.exports = impl;
    		} else {
    		  this.xor128 = impl;
    		}

    		})(
    		  xor128,
    		  module); 
    	} (xor128$1));
    	return xor128$1.exports;
    }

    var xorwow$1 = {exports: {}};

    var xorwow = xorwow$1.exports;

    var hasRequiredXorwow;

    function requireXorwow () {
    	if (hasRequiredXorwow) return xorwow$1.exports;
    	hasRequiredXorwow = 1;
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

    		if (module.exports) {
    		  module.exports = impl;
    		} else {
    		  this.xorwow = impl;
    		}

    		})(
    		  xorwow,
    		  module); 
    	} (xorwow$1));
    	return xorwow$1.exports;
    }

    var xorshift7$1 = {exports: {}};

    var xorshift7 = xorshift7$1.exports;

    var hasRequiredXorshift7;

    function requireXorshift7 () {
    	if (hasRequiredXorshift7) return xorshift7$1.exports;
    	hasRequiredXorshift7 = 1;
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

    		if (module.exports) {
    		  module.exports = impl;
    		} else {
    		  this.xorshift7 = impl;
    		}

    		})(
    		  xorshift7,
    		  module); 
    	} (xorshift7$1));
    	return xorshift7$1.exports;
    }

    var xor4096$1 = {exports: {}};

    var xor4096 = xor4096$1.exports;

    var hasRequiredXor4096;

    function requireXor4096 () {
    	if (hasRequiredXor4096) return xor4096$1.exports;
    	hasRequiredXor4096 = 1;
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

    		if (module.exports) {
    		  module.exports = impl;
    		} else {
    		  this.xor4096 = impl;
    		}

    		})(
    		  xor4096,                                     // window object or global
    		  module); 
    	} (xor4096$1));
    	return xor4096$1.exports;
    }

    var tychei$1 = {exports: {}};

    var tychei = tychei$1.exports;

    var hasRequiredTychei;

    function requireTychei () {
    	if (hasRequiredTychei) return tychei$1.exports;
    	hasRequiredTychei = 1;
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

    		if (module.exports) {
    		  module.exports = impl;
    		} else {
    		  this.tychei = impl;
    		}

    		})(
    		  tychei,
    		  module); 
    	} (tychei$1));
    	return tychei$1.exports;
    }

    var seedrandom$2 = {exports: {}};

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
    var seedrandom$1 = seedrandom$2.exports;

    var hasRequiredSeedrandom$1;

    function requireSeedrandom$1 () {
    	if (hasRequiredSeedrandom$1) return seedrandom$2.exports;
    	hasRequiredSeedrandom$1 = 1;
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
    		  (typeof self !== 'undefined') ? self : seedrandom$1,
    		  [],     // pool: entropy pool starts empty
    		  Math    // math: package containing random, pow, and seedrandom
    		); 
    	} (seedrandom$2));
    	return seedrandom$2.exports;
    }

    var seedrandom;
    var hasRequiredSeedrandom;

    function requireSeedrandom () {
    	if (hasRequiredSeedrandom) return seedrandom;
    	hasRequiredSeedrandom = 1;
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
    	var alea = requireAlea();

    	// xor128, a pure xor-shift generator by George Marsaglia.
    	// Period: 2^128-1.
    	// Reported to fail: MatrixRank and LinearComp.
    	var xor128 = requireXor128();

    	// xorwow, George Marsaglia's 160-bit xor-shift combined plus weyl.
    	// Period: 2^192-2^32
    	// Reported to fail: CollisionOver, SimpPoker, and LinearComp.
    	var xorwow = requireXorwow();

    	// xorshift7, by François Panneton and Pierre L'ecuyer, takes
    	// a different approach: it adds robustness by allowing more shifts
    	// than Marsaglia's original three.  It is a 7-shift generator
    	// with 256 bits, that passes BigCrush with no systmatic failures.
    	// Period 2^256-1.
    	// No systematic BigCrush failures reported.
    	var xorshift7 = requireXorshift7();

    	// xor4096, by Richard Brent, is a 4096-bit xor-shift with a
    	// very long period that also adds a Weyl generator. It also passes
    	// BigCrush with no systematic failures.  Its long period may
    	// be useful if you have many generators and need to avoid
    	// collisions.
    	// Period: 2^4128-2^32.
    	// No systematic BigCrush failures reported.
    	var xor4096 = requireXor4096();

    	// Tyche-i, by Samuel Neves and Filipe Araujo, is a bit-shifting random
    	// number generator derived from ChaCha, a modern stream cipher.
    	// https://eden.dei.uc.pt/~sneves/pubs/2011-snfa2.pdf
    	// Period: ~2^127
    	// No systematic BigCrush failures reported.
    	var tychei = requireTychei();

    	// The original ARC4-based prng included in this library.
    	// Period: ~2^1600
    	var sr = requireSeedrandom$1();

    	sr.alea = alea;
    	sr.xor128 = xor128;
    	sr.xorwow = xorwow;
    	sr.xorshift7 = xorshift7;
    	sr.xor4096 = xor4096;
    	sr.tychei = tychei;

    	seedrandom = sr;
    	return seedrandom;
    }

    var seedrandomExports = requireSeedrandom();
    var Seedrandom = /*@__PURE__*/getDefaultExportFromCjs(seedrandomExports);

    /**
     * @param {HTMLCanvasElement} canvas
     * @param {string} domainKey
     * @param {string} sessionKey
     * @param {any} getImageDataProxy
     * @param {CanvasRenderingContext2D | WebGL2RenderingContext | WebGLRenderingContext} ctx?
     */
    function computeOffScreenCanvas(canvas, domainKey, sessionKey, getImageDataProxy, ctx) {
        if (!ctx) {
            // @ts-expect-error - Type 'null' is not assignable to type 'CanvasRenderingContext2D | WebGL2RenderingContext | WebGLRenderingContext'.
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
            // @ts-expect-error - Type 'CanvasRenderingContext2D | null' is not assignable to type 'CanvasRenderingContext2D | WebGL2RenderingContext | WebGLRenderingContext'.
            rasterizedCtx = offScreenCtx;
            // @ts-expect-error - 'offScreenCtx' is possibly 'null'.
            offScreenCtx.drawImage(canvas, 0, 0);
        }

        // We *always* compute the random pixels on the complete pixel set, then pass back the subset later
        let imageData = getImageDataProxy._native.apply(rasterizedCtx, [0, 0, canvas.width, canvas.height]);
        imageData = modifyPixelData(imageData, sessionKey, domainKey, canvas.width);

        if (rasterizeToCanvas) {
            // @ts-expect-error - Type 'null' is not assignable to type 'CanvasRenderingContext2D'.
            clearCanvas(offScreenCtx);
        }

        // @ts-expect-error - 'offScreenCtx' is possibly 'null'.
        offScreenCtx.putImageData(imageData, 0, 0);

        return { offScreenCanvas, offScreenCtx };
    }

    /**
     * Clears the pixels from the canvas context
     *
     * @param {CanvasRenderingContext2D} canvasContext
     */
    function clearCanvas(canvasContext) {
        // Save state and clean the pixels from the canvas
        canvasContext.save();
        canvasContext.globalCompositeOperation = 'destination-out';
        canvasContext.fillStyle = 'rgb(255,255,255)';
        canvasContext.fillRect(0, 0, canvasContext.canvas.width, canvasContext.canvas.height);
        canvasContext.restore();
    }

    /**
     * @param {import("@canvas/image-data")} imageData
     * @param {string} sessionKey
     * @param {string} domainKey
     * @param {number} width
     */
    function modifyPixelData(imageData, domainKey, sessionKey, width) {
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
        const rng = new Seedrandom(windowHash);
        for (let i = 0; i < mappingArray.length; i++) {
            const rand = rng();
            const byte = Math.floor(rand * 10);
            const channel = byte % 3;
            const pixelCanvasIndex = mappingArray[i] + channel;

            d[pixelCanvasIndex] = d[pixelCanvasIndex] ^ (byte & 0x1);
        }

        return imageData;
    }

    /**
     * Ignore pixels that have neighbours that are the same
     *
     * @param {Uint8ClampedArray} imageData
     * @param {number} index
     * @param {number} width
     */
    function adjacentSame(imageData, index, width) {
        const widthPixel = width * 4;
        const x = index % widthPixel;
        const maxLength = imageData.length;

        // Pixels not on the right border of the canvas
        if (x < widthPixel) {
            const right = index + 4;
            if (!pixelsSame(imageData, index, right)) {
                return false;
            }
            const diagonalRightUp = right - widthPixel;
            if (diagonalRightUp > 0 && !pixelsSame(imageData, index, diagonalRightUp)) {
                return false;
            }
            const diagonalRightDown = right + widthPixel;
            if (diagonalRightDown < maxLength && !pixelsSame(imageData, index, diagonalRightDown)) {
                return false;
            }
        }

        // Pixels not on the left border of the canvas
        if (x > 0) {
            const left = index - 4;
            if (!pixelsSame(imageData, index, left)) {
                return false;
            }
            const diagonalLeftUp = left - widthPixel;
            if (diagonalLeftUp > 0 && !pixelsSame(imageData, index, diagonalLeftUp)) {
                return false;
            }
            const diagonalLeftDown = left + widthPixel;
            if (diagonalLeftDown < maxLength && !pixelsSame(imageData, index, diagonalLeftDown)) {
                return false;
            }
        }

        const up = index - widthPixel;
        if (up > 0 && !pixelsSame(imageData, index, up)) {
            return false;
        }

        const down = index + widthPixel;
        if (down < maxLength && !pixelsSame(imageData, index, down)) {
            return false;
        }

        return true;
    }

    /**
     * Check that a pixel at index and index2 match all channels
     * @param {Uint8ClampedArray} imageData
     * @param {number} index
     * @param {number} index2
     */
    function pixelsSame(imageData, index, index2) {
        return (
            imageData[index] === imageData[index2] &&
            imageData[index + 1] === imageData[index2 + 1] &&
            imageData[index + 2] === imageData[index2 + 2] &&
            imageData[index + 3] === imageData[index2 + 3]
        );
    }

    /**
     * Returns true if pixel should be ignored
     * @param {Uint8ClampedArray} imageData
     * @param {number} index
     * @returns {boolean}
     */
    function shouldIgnorePixel(imageData, index) {
        // Transparent pixels
        if (imageData[index + 3] === 0) {
            return true;
        }
        return false;
    }

    class FingerprintingCanvas extends ContentFeature {
        init(args) {
            const { sessionKey, site } = args;
            const domainKey = site.domain;
            const supportsWebGl = this.getFeatureSettingEnabled('webGl');

            const unsafeCanvases = new WeakSet();
            const canvasContexts = new WeakMap();
            const canvasCache = new WeakMap();

            /**
             * Clear cache as canvas has changed
             * @param {OffscreenCanvas | HTMLCanvasElement} canvas
             */
            function clearCache(canvas) {
                canvasCache.delete(canvas);
            }

            /**
             * @param {OffscreenCanvas | HTMLCanvasElement} canvas
             */
            function treatAsUnsafe(canvas) {
                unsafeCanvases.add(canvas);
                clearCache(canvas);
            }

            const proxy = new DDGProxy(this, HTMLCanvasElement.prototype, 'getContext', {
                apply(target, thisArg, args) {
                    const context = DDGReflect.apply(target, thisArg, args);
                    try {
                        // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'.
                        canvasContexts.set(thisArg, context);
                    } catch {}
                    return context;
                },
            });
            proxy.overload();

            // Known data methods
            const safeMethods = ['putImageData', 'drawImage'];
            for (const methodName of safeMethods) {
                const safeMethodProxy = new DDGProxy(this, CanvasRenderingContext2D.prototype, methodName, {
                    apply(target, thisArg, args) {
                        // Don't apply escape hatch for canvases
                        if (methodName === 'drawImage' && args[0] && args[0] instanceof HTMLCanvasElement) {
                            treatAsUnsafe(args[0]);
                        } else {
                            // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                            clearCache(thisArg.canvas);
                        }
                        return DDGReflect.apply(target, thisArg, args);
                    },
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
                'createPattern',
            ];
            for (const methodName of unsafeMethods) {
                // Some methods are browser specific
                if (methodName in CanvasRenderingContext2D.prototype) {
                    const unsafeProxy = new DDGProxy(this, CanvasRenderingContext2D.prototype, methodName, {
                        apply(target, thisArg, args) {
                            // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                            treatAsUnsafe(thisArg.canvas);
                            return DDGReflect.apply(target, thisArg, args);
                        },
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
                    'drawArrays',
                ];
                const glContexts = [WebGLRenderingContext];
                if ('WebGL2RenderingContext' in globalThis) {
                    glContexts.push(WebGL2RenderingContext);
                }
                for (const context of glContexts) {
                    for (const methodName of unsafeGlMethods) {
                        // Some methods are browser specific
                        if (methodName in context.prototype) {
                            const unsafeProxy = new DDGProxy(this, context.prototype, methodName, {
                                apply(target, thisArg, args) {
                                    // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                                    treatAsUnsafe(thisArg.canvas);
                                    return DDGReflect.apply(target, thisArg, args);
                                },
                            });
                            unsafeProxy.overload();
                        }
                    }
                }
            }

            // Using proxies here to swallow calls to toString etc
            const getImageDataProxy = new DDGProxy(this, CanvasRenderingContext2D.prototype, 'getImageData', {
                apply(target, thisArg, args) {
                    // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                    if (!unsafeCanvases.has(thisArg.canvas)) {
                        return DDGReflect.apply(target, thisArg, args);
                    }
                    // Anything we do here should be caught and ignored silently
                    try {
                        // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                        const { offScreenCtx } = getCachedOffScreenCanvasOrCompute(thisArg.canvas, domainKey, sessionKey);
                        // Call the original method on the modified off-screen canvas
                        return DDGReflect.apply(target, offScreenCtx, args);
                    } catch {}

                    return DDGReflect.apply(target, thisArg, args);
                },
            });
            getImageDataProxy.overload();

            /**
             * Get cached offscreen if one exists, otherwise compute one
             *
             * @param {HTMLCanvasElement} canvas
             * @param {string} domainKey
             * @param {string} sessionKey
             */
            function getCachedOffScreenCanvasOrCompute(canvas, domainKey, sessionKey) {
                let result;
                if (canvasCache.has(canvas)) {
                    result = canvasCache.get(canvas);
                } else {
                    const ctx = canvasContexts.get(canvas);
                    result = computeOffScreenCanvas(canvas, domainKey, sessionKey, getImageDataProxy, ctx);
                    canvasCache.set(canvas, result);
                }
                return result;
            }

            const canvasMethods = ['toDataURL', 'toBlob'];
            for (const methodName of canvasMethods) {
                const proxy = new DDGProxy(this, HTMLCanvasElement.prototype, methodName, {
                    apply(target, thisArg, args) {
                        // Short circuit for low risk canvas calls
                        // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                        if (!unsafeCanvases.has(thisArg)) {
                            return DDGReflect.apply(target, thisArg, args);
                        }
                        try {
                            // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                            const { offScreenCanvas } = getCachedOffScreenCanvasOrCompute(thisArg, domainKey, sessionKey);
                            // Call the original method on the modified off-screen canvas
                            return DDGReflect.apply(target, offScreenCanvas, args);
                        } catch {
                            // Something we did caused an exception, fall back to the native
                            return DDGReflect.apply(target, thisArg, args);
                        }
                    },
                });
                proxy.overload();
            }
        }
    }

    class GoogleRejected extends ContentFeature {
        init() {
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
    }

    // Set Global Privacy Control property on DOM
    class GlobalPrivacyControl extends ContentFeature {
        init(args) {
            try {
                // If GPC on, set DOM property prototype to true if not already true
                if (args.globalPrivacyControlValue) {
                    // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                    if (navigator.globalPrivacyControl) return;
                    this.defineProperty(Navigator.prototype, 'globalPrivacyControl', {
                        get: () => true,
                        configurable: true,
                        enumerable: true,
                    });
                } else {
                    // If GPC off & unsupported by browser, set DOM property prototype to false
                    // this may be overwritten by the user agent or other extensions
                    // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                    if (typeof navigator.globalPrivacyControl !== 'undefined') return;
                    this.defineProperty(Navigator.prototype, 'globalPrivacyControl', {
                        get: () => false,
                        configurable: true,
                        enumerable: true,
                    });
                }
            } catch {
                // Ignore exceptions that could be caused by conflicting with other extensions
            }
        }
    }

    class FingerprintingHardware extends ContentFeature {
        init() {
            this.wrapProperty(globalThis.Navigator.prototype, 'keyboard', {
                get: () => {
                    // @ts-expect-error - error TS2554: Expected 2 arguments, but got 1.
                    return this.getFeatureAttr('keyboard');
                },
            });

            this.wrapProperty(globalThis.Navigator.prototype, 'hardwareConcurrency', {
                get: () => {
                    return this.getFeatureAttr('hardwareConcurrency', 2);
                },
            });

            this.wrapProperty(globalThis.Navigator.prototype, 'deviceMemory', {
                get: () => {
                    return this.getFeatureAttr('deviceMemory', 8);
                },
            });
        }
    }

    class Referrer extends ContentFeature {
        init() {
            // If the referer is a different host to the current one, trim it.
            if (document.referrer && new URL(document.URL).hostname !== new URL(document.referrer).hostname) {
                // trim referrer to origin.
                const trimmedReferer = new URL(document.referrer).origin + '/';
                this.wrapProperty(Document.prototype, 'referrer', {
                    get: () => trimmedReferer,
                });
            }
        }
    }

    class FingerprintingScreenSize extends ContentFeature {
        origPropertyValues = {};

        init() {
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            this.origPropertyValues.availTop = globalThis.screen.availTop;
            this.wrapProperty(globalThis.Screen.prototype, 'availTop', {
                get: () => this.getFeatureAttr('availTop', 0),
            });

            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            this.origPropertyValues.availLeft = globalThis.screen.availLeft;
            this.wrapProperty(globalThis.Screen.prototype, 'availLeft', {
                get: () => this.getFeatureAttr('availLeft', 0),
            });

            this.origPropertyValues.availWidth = globalThis.screen.availWidth;
            const forcedAvailWidthValue = globalThis.screen.width;
            this.wrapProperty(globalThis.Screen.prototype, 'availWidth', {
                get: () => forcedAvailWidthValue,
            });

            this.origPropertyValues.availHeight = globalThis.screen.availHeight;
            const forcedAvailHeightValue = globalThis.screen.height;
            this.wrapProperty(globalThis.Screen.prototype, 'availHeight', {
                get: () => forcedAvailHeightValue,
            });

            this.origPropertyValues.colorDepth = globalThis.screen.colorDepth;
            this.wrapProperty(globalThis.Screen.prototype, 'colorDepth', {
                get: () => this.getFeatureAttr('colorDepth', 24),
            });

            this.origPropertyValues.pixelDepth = globalThis.screen.pixelDepth;
            this.wrapProperty(globalThis.Screen.prototype, 'pixelDepth', {
                get: () => this.getFeatureAttr('pixelDepth', 24),
            });

            globalThis.window.addEventListener('resize', () => {
                this.setWindowDimensions();
            });
            this.setWindowDimensions();
        }

        /**
         * normalize window dimensions, if more than one monitor is in play.
         *  X/Y values are set in the browser based on distance to the main monitor top or left, which
         * can mean second or more monitors have very large or negative values. This function maps a given
         * given coordinate value to the proper place on the main screen.
         */
        normalizeWindowDimension(value, targetDimension) {
            if (value > targetDimension) {
                return value % targetDimension;
            }
            if (value < 0) {
                return targetDimension + value;
            }
            return value;
        }

        setWindowPropertyValue(property, value) {
            // Here we don't update the prototype getter because the values are updated dynamically
            try {
                this.defineProperty(globalThis, property, {
                    get: () => value,

                    set: () => {},
                    configurable: true,
                    enumerable: true,
                });
            } catch (e) {}
        }

        /**
         * Fix window dimensions. The extension runs in a different JS context than the
         * page, so we can inject the correct screen values as the window is resized,
         * ensuring that no information is leaked as the dimensions change, but also that the
         * values change correctly for valid use cases.
         */
        setWindowDimensions() {
            try {
                const window = globalThis;
                const top = globalThis.top;

                const normalizedY = this.normalizeWindowDimension(window.screenY, window.screen.height);
                const normalizedX = this.normalizeWindowDimension(window.screenX, window.screen.width);
                if (normalizedY <= this.origPropertyValues.availTop) {
                    this.setWindowPropertyValue('screenY', 0);
                    this.setWindowPropertyValue('screenTop', 0);
                } else {
                    this.setWindowPropertyValue('screenY', normalizedY);
                    this.setWindowPropertyValue('screenTop', normalizedY);
                }

                // @ts-expect-error -  error TS18047: 'top' is possibly 'null'.
                if (top.window.outerHeight >= this.origPropertyValues.availHeight - 1) {
                    // @ts-expect-error -  error TS18047: 'top' is possibly 'null'.
                    this.setWindowPropertyValue('outerHeight', top.window.screen.height);
                } else {
                    try {
                        // @ts-expect-error -  error TS18047: 'top' is possibly 'null'.
                        this.setWindowPropertyValue('outerHeight', top.window.outerHeight);
                    } catch (e) {
                        // top not accessible to certain iFrames, so ignore.
                    }
                }

                if (normalizedX <= this.origPropertyValues.availLeft) {
                    this.setWindowPropertyValue('screenX', 0);
                    this.setWindowPropertyValue('screenLeft', 0);
                } else {
                    this.setWindowPropertyValue('screenX', normalizedX);
                    this.setWindowPropertyValue('screenLeft', normalizedX);
                }

                // @ts-expect-error -  error TS18047: 'top' is possibly 'null'.
                if (top.window.outerWidth >= this.origPropertyValues.availWidth - 1) {
                    // @ts-expect-error -  error TS18047: 'top' is possibly 'null'.
                    this.setWindowPropertyValue('outerWidth', top.window.screen.width);
                } else {
                    try {
                        // @ts-expect-error -  error TS18047: 'top' is possibly 'null'.
                        this.setWindowPropertyValue('outerWidth', top.window.outerWidth);
                    } catch (e) {
                        // top not accessible to certain iFrames, so ignore.
                    }
                }
            } catch (e) {
                // in a cross domain iFrame, top.window is not accessible.
            }
        }
    }

    class FingerprintingTemporaryStorage extends ContentFeature {
        init() {
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
                    tStorage.queryUsageAndQuota = function queryUsageAndQuota(callback, err) {
                        const modifiedCallback = function (usedBytes, grantedBytes) {
                            const maxBytesGranted = 4 * 1024 * 1024 * 1024;
                            const spoofedGrantedBytes = Math.min(grantedBytes, maxBytesGranted);
                            callback(usedBytes, spoofedGrantedBytes);
                        };
                        // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                        org.call(navigator.webkitTemporaryStorage, modifiedCallback, err);
                    };
                    this.defineProperty(Navigator.prototype, 'webkitTemporaryStorage', {
                        get: () => tStorage,
                        enumerable: true,
                        configurable: true,
                    });
                } catch (e) {}
            }
        }
    }

    /**
     * @param {unknown} input
     * @return {input is Object}
     */
    function isObject(input) {
        return toString.call(input) === '[object Object]';
    }

    /**
     * @param {unknown} input
     * @return {input is string}
     */
    function isString(input) {
        return typeof input === 'string';
    }

    /**
     * @import { Messaging } from "@duckduckgo/messaging";
     * @typedef {Pick<Messaging, 'notify' | 'request' | 'subscribe'>} MessagingInterface
     */

    /**
     * Sending this event
     */
    class InstallProxy {
        static NAME = 'INSTALL_BRIDGE';
        get name() {
            return InstallProxy.NAME;
        }

        /**
         * @param {object} params
         * @param {string} params.featureName
         * @param {string} params.id
         */
        constructor(params) {
            this.featureName = params.featureName;
            this.id = params.id;
        }

        /**
         * @param {unknown} params
         */
        static create(params) {
            if (!isObject(params)) return null;
            if (!isString(params.featureName)) return null;
            if (!isString(params.id)) return null;
            return new InstallProxy({ featureName: params.featureName, id: params.id });
        }
    }

    class DidInstall {
        static NAME = 'DID_INSTALL';
        get name() {
            return DidInstall.NAME;
        }
        /**
         * @param {object} params
         * @param {string} params.id
         */
        constructor(params) {
            this.id = params.id;
        }

        /**
         * @param {unknown} params
         */
        static create(params) {
            if (!isObject(params)) return null;
            if (!isString(params.id)) return null;
            return new DidInstall({ id: params.id });
        }
    }

    class ProxyRequest {
        static NAME = 'PROXY_REQUEST';
        get name() {
            return ProxyRequest.NAME;
        }
        /**
         * @param {object} params
         * @param {string} params.featureName
         * @param {string} params.method
         * @param {string} params.id
         * @param {Record<string, any>} [params.params]
         */
        constructor(params) {
            this.featureName = params.featureName;
            this.method = params.method;
            this.params = params.params;
            this.id = params.id;
        }
        /**
         * @param {unknown} params
         */
        static create(params) {
            if (!isObject(params)) return null;
            if (!isString(params.featureName)) return null;
            if (!isString(params.method)) return null;
            if (!isString(params.id)) return null;
            if (params.params && !isObject(params.params)) return null;
            return new ProxyRequest({
                featureName: params.featureName,
                method: params.method,
                params: params.params,
                id: params.id,
            });
        }
    }

    class ProxyResponse {
        static NAME = 'PROXY_RESPONSE';
        get name() {
            return ProxyResponse.NAME;
        }
        /**
         * @param {object} params
         * @param {string} params.featureName
         * @param {string} params.method
         * @param {string} params.id
         * @param {Record<string, any>} [params.result]
         * @param {import("@duckduckgo/messaging").MessageError} [params.error]
         */
        constructor(params) {
            this.featureName = params.featureName;
            this.method = params.method;
            this.result = params.result;
            this.error = params.error;
            this.id = params.id;
        }
        /**
         * @param {unknown} params
         */
        static create(params) {
            if (!isObject(params)) return null;
            if (!isString(params.featureName)) return null;
            if (!isString(params.method)) return null;
            if (!isString(params.id)) return null;
            if (params.result && !isObject(params.result)) return null;
            if (params.error && !isObject(params.error)) return null;
            return new ProxyResponse({
                featureName: params.featureName,
                method: params.method,
                result: params.result,
                error: params.error,
                id: params.id,
            });
        }
    }

    /**
     */
    class ProxyNotification {
        static NAME = 'PROXY_NOTIFICATION';
        get name() {
            return ProxyNotification.NAME;
        }
        /**
         * @param {object} params
         * @param {string} params.featureName
         * @param {string} params.method
         * @param {Record<string, any>} [params.params]
         */
        constructor(params) {
            this.featureName = params.featureName;
            this.method = params.method;
            this.params = params.params;
        }

        /**
         * @param {unknown} params
         */
        static create(params) {
            if (!isObject(params)) return null;
            if (!isString(params.featureName)) return null;
            if (!isString(params.method)) return null;
            if (params.params && !isObject(params.params)) return null;
            return new ProxyNotification({
                featureName: params.featureName,
                method: params.method,
                params: params.params,
            });
        }
    }

    class SubscriptionRequest {
        static NAME = 'SUBSCRIPTION_REQUEST';
        get name() {
            return SubscriptionRequest.NAME;
        }
        /**
         * @param {object} params
         * @param {string} params.featureName
         * @param {string} params.subscriptionName
         * @param {string} params.id
         */
        constructor(params) {
            this.featureName = params.featureName;
            this.subscriptionName = params.subscriptionName;
            this.id = params.id;
        }
        /**
         * @param {unknown} params
         */
        static create(params) {
            if (!isObject(params)) return null;
            if (!isString(params.featureName)) return null;
            if (!isString(params.subscriptionName)) return null;
            if (!isString(params.id)) return null;
            return new SubscriptionRequest({
                featureName: params.featureName,
                subscriptionName: params.subscriptionName,
                id: params.id,
            });
        }
    }

    class SubscriptionResponse {
        static NAME = 'SUBSCRIPTION_RESPONSE';
        get name() {
            return SubscriptionResponse.NAME;
        }
        /**
         * @param {object} params
         * @param {string} params.featureName
         * @param {string} params.subscriptionName
         * @param {string} params.id
         * @param {Record<string, any>} [params.params]
         */
        constructor(params) {
            this.featureName = params.featureName;
            this.subscriptionName = params.subscriptionName;
            this.id = params.id;
            this.params = params.params;
        }
        /**
         * @param {unknown} params
         */
        static create(params) {
            if (!isObject(params)) return null;
            if (!isString(params.featureName)) return null;
            if (!isString(params.subscriptionName)) return null;
            if (!isString(params.id)) return null;
            if (params.params && !isObject(params.params)) return null;
            return new SubscriptionResponse({
                featureName: params.featureName,
                subscriptionName: params.subscriptionName,
                params: params.params,
                id: params.id,
            });
        }
    }

    class SubscriptionUnsubscribe {
        static NAME = 'SUBSCRIPTION_UNSUBSCRIBE';
        get name() {
            return SubscriptionUnsubscribe.NAME;
        }
        /**
         * @param {object} params
         * @param {string} params.id
         */
        constructor(params) {
            this.id = params.id;
        }
        /**
         * @param {unknown} params
         */
        static create(params) {
            if (!isObject(params)) return null;
            if (!isString(params.id)) return null;
            return new SubscriptionUnsubscribe({
                id: params.id,
            });
        }
    }

    /**
     * @import { MessagingInterface } from "./schema.js"
     * @typedef {Pick<import("../../captured-globals.js"),
     *    "dispatchEvent" | "addEventListener" | "removeEventListener" | "CustomEvent" | "String" | "Error" | "randomUUID">
     * } Captured
     */
    /** @type {Captured} */
    const captured = capturedGlobals;

    const ERROR_MSG = 'Did not install Message Bridge';

    /**
     * Try to create a message bridge.
     *
     * Note: This will throw an exception if the bridge cannot be established.
     *
     * @param {string} featureName
     * @param {string} [token]
     * @return {MessagingInterface}
     * @throws {Error}
     */
    function createPageWorldBridge(featureName, token) {
        /**
         * This feature never operates without a featureName or token
         */
        if (typeof featureName !== 'string' || !token) {
            throw new captured.Error(ERROR_MSG);
        }
        /**
         * This feature never operates in a frame or insecure context
         */
        if (isBeingFramed() || !isSecureContext) {
            throw new captured.Error(ERROR_MSG);
        }

        /**
         * @param {string} eventName
         * @return {`${string}-${string}`}
         */
        const appendToken = (eventName) => {
            return `${eventName}-${token}`;
        };

        /**
         * Create the sender to centralize the sending logic
         * @param {{name: string} & Record<string, any>} incoming
         */
        const send = (incoming) => {
            // when the token is absent, just silently fail
            if (!token) return;
            const event = new captured.CustomEvent(appendToken(incoming.name), { detail: incoming });
            captured.dispatchEvent(event);
        };

        /**
         * Events are synchronous (even across contexts), so we can figure out
         * the result of installing the proxy before we return and give a
         * better experience for consumers
         */
        let installed = false;
        const id = random();
        const evt = new InstallProxy({ featureName, id });
        const evtName = appendToken(DidInstall.NAME + '-' + id);
        const didInstall = (/** @type {CustomEvent<unknown>} */ e) => {
            const result = DidInstall.create(e.detail);
            if (result && result.id === id) {
                installed = true;
            }
            captured.removeEventListener(evtName, didInstall);
        };

        captured.addEventListener(evtName, didInstall);
        send(evt);

        if (!installed) {
            // leaving this as a generic message for now
            throw new captured.Error(ERROR_MSG);
        }

        return createMessagingInterface(featureName, send, appendToken);
    }

    /**
     * We are executing exclusively in secure contexts, so this should never fail
     */
    function random() {
        if (typeof captured.randomUUID !== 'function') throw new Error('unreachable');
        return captured.randomUUID();
    }

    /**
     * @param {string} featureName
     * @param {(evt: {name: string} & Record<string, any>) => void} send
     * @param {(s: string) => string} appendToken
     * @returns {MessagingInterface}
     */
    function createMessagingInterface(featureName, send, appendToken) {
        return {
            /**
             * @param {string} method
             * @param {Record<string, any>} params
             */
            notify(method, params) {
                send(
                    new ProxyNotification({
                        method,
                        params,
                        featureName,
                    }),
                );
            },

            /**
             * @param {string} method
             * @param {Record<string, any>} params
             * @returns {Promise<any>}
             */
            request(method, params) {
                const id = random();

                send(
                    new ProxyRequest({
                        method,
                        params,
                        featureName,
                        id,
                    }),
                );

                return new Promise((resolve, reject) => {
                    const responseName = appendToken(ProxyResponse.NAME + '-' + id);
                    const handler = (/** @type {CustomEvent<unknown>} */ e) => {
                        const response = ProxyResponse.create(e.detail);
                        if (response && response.id === id) {
                            if ('error' in response && response.error) {
                                reject(new Error(response.error.message));
                            } else if ('result' in response) {
                                resolve(response.result);
                            }
                            captured.removeEventListener(responseName, handler);
                        }
                    };
                    captured.addEventListener(responseName, handler);
                });
            },

            /**
             * @param {string} name
             * @param {(d: any) => void} callback
             * @returns {() => void}
             */
            subscribe(name, callback) {
                const id = random();

                send(
                    new SubscriptionRequest({
                        subscriptionName: name,
                        featureName,
                        id,
                    }),
                );

                const handler = (/** @type {CustomEvent<unknown>} */ e) => {
                    const subscriptionEvent = SubscriptionResponse.create(e.detail);
                    if (subscriptionEvent) {
                        const { id: eventId, params } = subscriptionEvent;
                        if (eventId === id) {
                            callback(params);
                        }
                    }
                };

                const type = appendToken(SubscriptionResponse.NAME + '-' + id);
                captured.addEventListener(type, handler);

                return () => {
                    captured.removeEventListener(type, handler);
                    const evt = new SubscriptionUnsubscribe({ id });
                    send(evt);
                };
            },
        };
    }

    class NavigatorInterface extends ContentFeature {
        load(args) {
            // @ts-expect-error: Accessing private method
            if (this.matchDomainFeatureSetting('privilegedDomains').length) {
                this.injectNavigatorInterface(args);
            }
        }

        init(args) {
            this.injectNavigatorInterface(args);
        }

        injectNavigatorInterface(args) {
            try {
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                if (navigator.duckduckgo) {
                    return;
                }
                if (!args.platform || !args.platform.name) {
                    return;
                }
                this.defineProperty(Navigator.prototype, 'duckduckgo', {
                    value: {
                        platform: args.platform.name,
                        isDuckDuckGo() {
                            return DDGPromise.resolve(true);
                        },
                        /**
                         * @import { MessagingInterface } from "./message-bridge/schema.js"
                         * @param {string} featureName
                         * @return {MessagingInterface}
                         * @throws {Error}
                         */
                        createMessageBridge(featureName) {
                            return createPageWorldBridge(featureName, args.messageSecret);
                        },
                    },
                    enumerable: true,
                    configurable: false,
                    writable: false,
                });
            } catch {
                // todo: Just ignore this exception?
            }
        }
    }

    let adLabelStrings = [];
    const parser = new DOMParser();
    let hiddenElements = new WeakMap();
    let modifiedElements = new WeakMap();
    let appliedRules = new Set();
    let shouldInjectStyleTag = false;
    let mediaAndFormSelectors = 'video,canvas,embed,object,audio,map,form,input,textarea,select,option,button';
    let hideTimeouts = [0, 100, 300, 500, 1000, 2000, 3000];
    let unhideTimeouts = [1250, 2250, 3000];

    /** @type {ElementHiding} */
    let featureInstance;

    /**
     * Hide DOM element if rule conditions met
     * @param {HTMLElement} element
     * @param {Object} rule
     * @param {HTMLElement} [previousElement]
     */
    function collapseDomNode(element, rule, previousElement) {
        if (!element) {
            return;
        }
        const type = rule.type;
        const alreadyHidden = hiddenElements.has(element);
        const alreadyModified = modifiedElements.has(element) && modifiedElements.get(element) === rule.type;
        // return if the element has already been hidden, or modified by the same rule type
        if (alreadyHidden || alreadyModified) {
            return;
        }

        switch (type) {
            case 'hide':
                hideNode(element);
                break;
            case 'hide-empty':
                if (isDomNodeEmpty(element)) {
                    hideNode(element);
                    appliedRules.add(rule);
                }
                break;
            case 'closest-empty':
                // hide the outermost empty node so that we may unhide if ad loads
                if (isDomNodeEmpty(element)) {
                    // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                    collapseDomNode(element.parentNode, rule, element);
                } else if (previousElement) {
                    hideNode(previousElement);
                    appliedRules.add(rule);
                }
                break;
            case 'modify-attr':
                modifyAttribute(element, rule.values);
                break;
            case 'modify-style':
                modifyStyle(element, rule.values);
                break;
        }
    }

    /**
     * Unhide previously hidden DOM element if content loaded into it
     * @param {HTMLElement} element
     * @param {Object} rule
     */
    function expandNonEmptyDomNode(element, rule) {
        if (!element) {
            return;
        }
        const type = rule.type;

        const alreadyHidden = hiddenElements.has(element);

        switch (type) {
            case 'hide':
                // only care about rule types that specifically apply to empty elements
                break;
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
                break;
        }
    }

    /**
     * Hide DOM element
     * @param {HTMLElement} element
     */
    function hideNode(element) {
        // maintain a reference to each hidden element along with the properties
        // that are being overwritten
        const cachedDisplayProperties = {
            display: element.style.display,
            'min-height': element.style.minHeight,
            height: element.style.height,
        };
        hiddenElements.set(element, cachedDisplayProperties);

        // apply styles to hide element
        element.style.setProperty('display', 'none', 'important');
        element.style.setProperty('min-height', '0px', 'important');
        element.style.setProperty('height', '0px', 'important');
        element.hidden = true;
        // add debug flag to site breakage reports
        featureInstance.addDebugFlag();
    }

    /**
     * Show previously hidden DOM element
     * @param {HTMLElement} element
     */
    function unhideNode(element) {
        const cachedDisplayProperties = hiddenElements.get(element);
        if (!cachedDisplayProperties) {
            return;
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
    function isDomNodeEmpty(node) {
        // no sense wasting cycles checking if the page's body element is empty
        if (node.tagName === 'BODY') {
            return false;
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
            return frame.hidden || frame.src === 'about:blank';
        });
        // ad containers often contain tracking pixels and other small images (eg adchoices logo).
        // these should be treated as empty and hidden, but real images should not.
        const visibleImages = imageElements.some((image) => {
            return image.getBoundingClientRect().width > 20 || image.getBoundingClientRect().height > 20;
        });

        if (
            (visibleText === '' || adLabelStrings.includes(visibleText)) &&
            mediaAndFormContent === null &&
            noFramesWithContent &&
            !visibleImages
        ) {
            return true;
        }
        return false;
    }

    /**
     * Modify specified attribute(s) on element
     * @param {HTMLElement} element
     * @param {Object[]} values
     * @param {string} values[].property
     * @param {string} values[].value
     */
    function modifyAttribute(element, values) {
        values.forEach((item) => {
            element.setAttribute(item.property, item.value);
        });
        modifiedElements.set(element, 'modify-attr');
    }

    /**
     * Modify specified style(s) on element
     * @param {HTMLElement} element
     * @param {Object[]} values
     * @param {string} values[].property
     * @param {string} values[].value
     */
    function modifyStyle(element, values) {
        values.forEach((item) => {
            element.style.setProperty(item.property, item.value, 'important');
        });
        modifiedElements.set(element, 'modify-style');
    }

    /**
     * Separate strict hide rules to inject as style tag if enabled
     * @param {Object[]} rules
     * @param {string} rules[].selector
     * @param {string} rules[].type
     */
    function extractTimeoutRules(rules) {
        if (!shouldInjectStyleTag) {
            return rules;
        }

        const strictHideRules = [];
        const timeoutRules = [];

        rules.forEach((rule) => {
            if (rule.type === 'hide') {
                strictHideRules.push(rule);
            } else {
                timeoutRules.push(rule);
            }
        });

        injectStyleTag(strictHideRules);
        return timeoutRules;
    }

    /**
     * Create styletag for strict hide rules and append it to the document
     * @param {Object[]} rules
     * @param {string} rules[].selector
     * @param {string} rules[].type
     */
    function injectStyleTag(rules) {
        // wrap selector list in :is(...) to make it a forgiving selector list. this enables
        // us to use selectors not supported in all browsers, eg :has in Firefox
        let selector = '';

        rules.forEach((rule, i) => {
            if (i !== rules.length - 1) {
                selector = selector.concat(rule.selector, ',');
            } else {
                selector = selector.concat(rule.selector);
            }
        });
        const styleTagProperties = 'display:none!important;min-height:0!important;height:0!important;';
        const styleTagContents = `${forgivingSelector(selector)} {${styleTagProperties}}`;

        injectGlobalStyles(styleTagContents);
    }

    /**
     * Apply list of active element hiding rules to page
     * @param {Object[]} rules
     * @param {string} rules[].selector
     * @param {string} rules[].type
     */
    function hideAdNodes(rules) {
        const document = globalThis.document;

        rules.forEach((rule) => {
            const selector = forgivingSelector(rule.selector);
            const matchingElementArray = [...document.querySelectorAll(selector)];
            matchingElementArray.forEach((element) => {
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                collapseDomNode(element, rule);
            });
        });
    }

    /**
     * Iterate over previously hidden elements, unhiding if content has loaded into them
     */
    function unhideLoadedAds() {
        const document = globalThis.document;

        appliedRules.forEach((rule) => {
            const selector = forgivingSelector(rule.selector);
            const matchingElementArray = [...document.querySelectorAll(selector)];
            matchingElementArray.forEach((element) => {
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                expandNonEmptyDomNode(element, rule);
            });
        });
    }

    /**
     * Wrap selector(s) in :is(..) to make them forgiving
     */
    function forgivingSelector(selector) {
        return `:is(${selector})`;
    }

    class ElementHiding extends ContentFeature {
        init() {
            // eslint-disable-next-line @typescript-eslint/no-this-alias
            featureInstance = this;

            if (isBeingFramed()) {
                return;
            }

            let activeRules;
            const globalRules = this.getFeatureSetting('rules');
            adLabelStrings = this.getFeatureSetting('adLabelStrings');
            shouldInjectStyleTag = this.getFeatureSetting('useStrictHideStyleTag');
            hideTimeouts = this.getFeatureSetting('hideTimeouts') || hideTimeouts;
            unhideTimeouts = this.getFeatureSetting('unhideTimeouts') || unhideTimeouts;
            mediaAndFormSelectors = this.getFeatureSetting('mediaAndFormSelectors') || mediaAndFormSelectors;

            // determine whether strict hide rules should be injected as a style tag
            if (shouldInjectStyleTag) {
                // @ts-expect-error: Accessing private method
                shouldInjectStyleTag = this.matchDomainFeatureSetting('styleTagExceptions').length === 0;
            }

            // collect all matching rules for domain
            // @ts-expect-error: Accessing private method
            const activeDomainRules = this.matchDomainFeatureSetting('domains').flatMap((item) => item.rules);

            const overrideRules = activeDomainRules.filter((rule) => {
                return rule.type === 'override';
            });

            const disableDefault = activeDomainRules.some((rule) => {
                return rule.type === 'disable-default';
            });

            // if rule with type 'disable-default' is present, ignore all global rules
            if (disableDefault) {
                activeRules = activeDomainRules.filter((rule) => {
                    return rule.type !== 'disable-default';
                });
            } else {
                activeRules = activeDomainRules.concat(globalRules);
            }

            // remove overrides and rules that match overrides from array of rules to be applied to page
            overrideRules.forEach((override) => {
                activeRules = activeRules.filter((rule) => {
                    return rule.selector !== override.selector;
                });
            });

            const applyRules = this.applyRules.bind(this);

            // now have the final list of rules to apply, so we apply them when document is loaded
            if (document.readyState === 'loading') {
                window.addEventListener('DOMContentLoaded', () => {
                    applyRules(activeRules);
                });
            } else {
                applyRules(activeRules);
            }
            // single page applications don't have a DOMContentLoaded event on navigations, so
            // we use proxy/reflect on history.pushState to call applyRules on page navigations
            const historyMethodProxy = new DDGProxy(this, History.prototype, 'pushState', {
                apply(target, thisArg, args) {
                    applyRules(activeRules);
                    return DDGReflect.apply(target, thisArg, args);
                },
            });
            historyMethodProxy.overload();
            // listen for popstate events in order to run on back/forward navigations
            window.addEventListener('popstate', () => {
                applyRules(activeRules);
            });
        }

        /**
         * Apply relevant hiding rules to page at set intervals
         * @param {Object[]} rules
         * @param {string} rules[].selector
         * @param {string} rules[].type
         */
        applyRules(rules) {
            const timeoutRules = extractTimeoutRules(rules);
            const clearCacheTimer = unhideTimeouts.concat(hideTimeouts).reduce((a, b) => Math.max(a, b), 0) + 100;

            // several passes are made to hide & unhide elements. this is necessary because we're not using
            // a mutation observer but we want to hide/unhide elements as soon as possible, and ads
            // frequently take from several hundred milliseconds to several seconds to load
            hideTimeouts.forEach((timeout) => {
                setTimeout(() => {
                    hideAdNodes(timeoutRules);
                }, timeout);
            });

            // check previously hidden ad elements for contents, unhide if content has loaded after hiding.
            // we do this in order to display non-tracking ads that aren't blocked at the request level
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
                modifiedElements = new WeakMap();
            }, clearCacheTimer);
        }
    }

    class ExceptionHandler extends ContentFeature {
        init() {
            // Report to the debugger panel if an uncaught exception occurs
            const handleUncaughtException = (e) => {
                postDebugMessage(
                    'jsException',
                    {
                        documentUrl: document.location.href,
                        message: e.message,
                        filename: e.filename,
                        lineno: e.lineno,
                        colno: e.colno,
                        stack: e.error?.stack,
                    },
                    true,
                );
                this.addDebugFlag();
            };
            globalThis.addEventListener('error', handleUncaughtException);
        }
    }

    /**
     * This feature allows remote configuration of APIs that exist within the DOM.
     * We support removal of APIs and returning different values from getters.
     *
     * @module API manipulation
     */

    /**
     * @internal
     */
    class ApiManipulation extends ContentFeature {
        init() {
            const apiChanges = this.getFeatureSetting('apiChanges');
            if (apiChanges) {
                for (const scope in apiChanges) {
                    const change = apiChanges[scope];
                    if (!this.checkIsValidAPIChange(change)) {
                        continue;
                    }
                    this.applyApiChange(scope, change);
                }
            }
        }

        /**
         * Checks if the config API change is valid.
         * @param {any} change
         * @returns {change is APIChange}
         */
        checkIsValidAPIChange(change) {
            if (typeof change !== 'object') {
                return false;
            }
            if (change.type === 'remove') {
                return true;
            }
            if (change.type === 'descriptor') {
                if (change.enumerable && typeof change.enumerable !== 'boolean') {
                    return false;
                }
                if (change.configurable && typeof change.configurable !== 'boolean') {
                    return false;
                }
                return typeof change.getterValue !== 'undefined';
            }
            return false;
        }

        // TODO move this to schema definition imported from the privacy-config
        // Additionally remove checkIsValidAPIChange when this change happens.
        // See: https://app.asana.com/0/1201614831475344/1208715421518231/f
        /**
         * @typedef {Object} APIChange
         * @property {"remove"|"descriptor"} type
         * @property {import('../utils.js').ConfigSetting} [getterValue] - The value returned from a getter.
         * @property {boolean} [enumerable] - Whether the property is enumerable.
         * @property {boolean} [configurable] - Whether the property is configurable.
         */

        /**
         * Applies a change to DOM APIs.
         * @param {string} scope
         * @param {APIChange} change
         * @returns {void}
         */
        applyApiChange(scope, change) {
            const response = this.getGlobalObject(scope);
            if (!response) {
                return;
            }
            const [obj, key] = response;
            if (change.type === 'remove') {
                this.removeApiMethod(obj, key);
            } else if (change.type === 'descriptor') {
                this.wrapApiDescriptor(obj, key, change);
            }
        }

        /**
         * Removes a method from an API.
         * @param {object} api
         * @param {string} key
         */
        removeApiMethod(api, key) {
            try {
                if (hasOwnProperty.call(api, key)) {
                    delete api[key];
                }
            } catch (e) {}
        }

        /**
         * Wraps a property with descriptor.
         * @param {object} api
         * @param {string} key
         * @param {APIChange} change
         */
        wrapApiDescriptor(api, key, change) {
            const getterValue = change.getterValue;
            if (getterValue) {
                const descriptor = {
                    get: () => processAttr(getterValue, undefined),
                };
                if ('enumerable' in change) {
                    descriptor.enumerable = change.enumerable;
                }
                if ('configurable' in change) {
                    descriptor.configurable = change.configurable;
                }
                this.wrapProperty(api, key, descriptor);
            }
        }

        /**
         * Looks up a global object from a scope, e.g. 'Navigator.prototype'.
         * @param {string} scope the scope of the object to get to.
         * @returns {[object, string]|null} the object at the scope.
         */
        getGlobalObject(scope) {
            const parts = scope.split('.');
            // get the last part of the scope
            const lastPart = parts.pop();
            if (!lastPart) {
                return null;
            }
            let obj = window;
            for (const part of parts) {
                obj = obj[part];
                if (!obj) {
                    return null;
                }
            }
            return [obj, lastPart];
        }
    }

    /**
     * Fixes incorrect sizing value for outerHeight and outerWidth
     */
    function windowSizingFix() {
        if (window.outerHeight !== 0 && window.outerWidth !== 0) {
            return;
        }
        window.outerHeight = window.innerHeight;
        window.outerWidth = window.innerWidth;
    }

    const MSG_WEB_SHARE = 'webShare';
    const MSG_PERMISSIONS_QUERY = 'permissionsQuery';
    const MSG_SCREEN_LOCK = 'screenLock';
    const MSG_SCREEN_UNLOCK = 'screenUnlock';

    function canShare(data) {
        if (typeof data !== 'object') return false;
        if (!('url' in data) && !('title' in data) && !('text' in data)) return false; // At least one of these is required
        if ('files' in data) return false; // File sharing is not supported at the moment
        if ('title' in data && typeof data.title !== 'string') return false;
        if ('text' in data && typeof data.text !== 'string') return false;
        if ('url' in data) {
            if (typeof data.url !== 'string') return false;
            try {
                const url = new URL$1(data.url, location.href);
                if (url.protocol !== 'http:' && url.protocol !== 'https:') return false;
            } catch (err) {
                return false;
            }
        }
        if (window !== window.top) return false; // Not supported in iframes
        return true;
    }

    /**
     * Clean data before sending to the Android side
     * @returns {ShareRequestData}
     */
    function cleanShareData(data) {
        /** @type {ShareRequestData} */
        const dataToSend = {};

        // only send the keys we care about
        for (const key of ['title', 'text', 'url']) {
            if (key in data) dataToSend[key] = data[key];
        }

        // clean url and handle relative links (e.g. if url is an empty string)
        if ('url' in data) {
            dataToSend.url = new URL$1(data.url, location.href).href;
        }

        // combine url and text into text if both are present
        if ('url' in dataToSend && 'text' in dataToSend) {
            dataToSend.text = `${dataToSend.text} ${dataToSend.url}`;
            delete dataToSend.url;
        }

        // if there's only title, create a dummy empty text
        if (!('url' in dataToSend) && !('text' in dataToSend)) {
            dataToSend.text = '';
        }
        return dataToSend;
    }

    class WebCompat extends ContentFeature {
        /** @type {Promise<any> | null} */
        #activeShareRequest = null;

        /** @type {Promise<any> | null} */
        #activeScreenLockRequest = null;

        init() {
            if (this.getFeatureSettingEnabled('windowSizing')) {
                windowSizingFix();
            }
            if (this.getFeatureSettingEnabled('navigatorCredentials')) {
                this.navigatorCredentialsFix();
            }
            if (this.getFeatureSettingEnabled('safariObject')) {
                this.safariObjectFix();
            }
            if (this.getFeatureSettingEnabled('messageHandlers')) {
                this.messageHandlersFix();
            }
            if (this.getFeatureSettingEnabled('notification')) {
                this.notificationFix();
            }
            if (this.getFeatureSettingEnabled('permissions')) {
                const settings = this.getFeatureSetting('permissions');
                this.permissionsFix(settings);
            }
            if (this.getFeatureSettingEnabled('cleanIframeValue')) {
                this.cleanIframeValue();
            }

            if (this.getFeatureSettingEnabled('mediaSession')) {
                this.mediaSessionFix();
            }

            if (this.getFeatureSettingEnabled('presentation')) {
                this.presentationFix();
            }

            if (this.getFeatureSettingEnabled('webShare')) {
                this.shimWebShare();
            }

            if (this.getFeatureSettingEnabled('viewportWidth')) {
                this.viewportWidthFix();
            }

            if (this.getFeatureSettingEnabled('screenLock')) {
                this.screenLockFix();
            }

            if (this.getFeatureSettingEnabled('modifyLocalStorage')) {
                this.modifyLocalStorage();
            }

            if (this.getFeatureSettingEnabled('modifyCookies')) {
                this.modifyCookies();
            }
        }

        /** Shim Web Share API in Android WebView */
        shimWebShare() {
            if (typeof navigator.canShare === 'function' || typeof navigator.share === 'function') return;

            this.defineProperty(Navigator.prototype, 'canShare', {
                configurable: true,
                enumerable: true,
                writable: true,
                value: canShare,
            });

            this.defineProperty(Navigator.prototype, 'share', {
                configurable: true,
                enumerable: true,
                writable: true,
                value: async (data) => {
                    if (!canShare(data)) return Promise.reject(new TypeError('Invalid share data'));
                    if (this.#activeShareRequest) {
                        return Promise.reject(new DOMException('Share already in progress', 'InvalidStateError'));
                    }
                    if (!navigator.userActivation.isActive) {
                        return Promise.reject(new DOMException('Share must be initiated by a user gesture', 'InvalidStateError'));
                    }

                    const dataToSend = cleanShareData(data);
                    this.#activeShareRequest = this.request(MSG_WEB_SHARE, dataToSend);
                    let resp;
                    try {
                        resp = await this.#activeShareRequest;
                    } catch (err) {
                        throw new DOMException(err.message, 'DataError');
                    } finally {
                        this.#activeShareRequest = null;
                    }

                    if (resp.failure) {
                        switch (resp.failure.name) {
                            case 'AbortError':
                            case 'NotAllowedError':
                            case 'DataError':
                                throw new DOMException(resp.failure.message, resp.failure.name);
                            default:
                                throw new DOMException(resp.failure.message, 'DataError');
                        }
                    }
                },
            });
        }

        /**
         * Notification fix for adding missing API for Android WebView.
         */
        notificationFix() {
            if (window.Notification) {
                return;
            }
            // Expose the API
            this.defineProperty(window, 'Notification', {
                value: () => {
                    // noop
                },
                writable: true,
                configurable: true,
                enumerable: false,
            });

            this.defineProperty(window.Notification, 'requestPermission', {
                value: () => {
                    return Promise.resolve('denied');
                },
                writable: true,
                configurable: true,
                enumerable: true,
            });

            this.defineProperty(window.Notification, 'permission', {
                get: () => 'denied',
                configurable: true,
                enumerable: false,
            });

            this.defineProperty(window.Notification, 'maxActions', {
                get: () => 2,
                configurable: true,
                enumerable: true,
            });
        }

        cleanIframeValue() {
            function cleanValueData(val) {
                const clone = Object.assign({}, val);
                const deleteKeys = ['iframeProto', 'iframeData', 'remap'];
                for (const key of deleteKeys) {
                    if (key in clone) {
                        delete clone[key];
                    }
                }
                val.iframeData = clone;
                return val;
            }

            window.XMLHttpRequest.prototype.send = new Proxy(window.XMLHttpRequest.prototype.send, {
                apply(target, thisArg, args) {
                    const body = args[0];
                    const cleanKey = 'bi_wvdp';
                    if (body && typeof body === 'string' && body.includes(cleanKey)) {
                        const parts = body.split('&').map((part) => {
                            return part.split('=');
                        });
                        if (parts.length > 0) {
                            parts.forEach((part) => {
                                if (part[0] === cleanKey) {
                                    const val = JSON.parse(decodeURIComponent(part[1]));
                                    part[1] = encodeURIComponent(JSON.stringify(cleanValueData(val)));
                                }
                            });
                            args[0] = parts
                                .map((part) => {
                                    return part.join('=');
                                })
                                .join('&');
                        }
                    }
                    return Reflect.apply(target, thisArg, args);
                },
            });
        }

        /**
         * Adds missing permissions API for Android WebView.
         */
        permissionsFix(settings) {
            if (window.navigator.permissions) {
                return;
            }
            const permissions = {};
            class PermissionStatus extends EventTarget {
                constructor(name, state) {
                    super();
                    this.name = name;
                    this.state = state;
                    this.onchange = null; // noop
                }
            }
            permissions.query = new Proxy(
                async (query) => {
                    this.addDebugFlag();
                    if (!query) {
                        throw new TypeError("Failed to execute 'query' on 'Permissions': 1 argument required, but only 0 present.");
                    }
                    if (!query.name) {
                        throw new TypeError(
                            "Failed to execute 'query' on 'Permissions': Failed to read the 'name' property from 'PermissionDescriptor': Required member is undefined.",
                        );
                    }
                    if (!settings.supportedPermissions || !(query.name in settings.supportedPermissions)) {
                        throw new TypeError(
                            `Failed to execute 'query' on 'Permissions': Failed to read the 'name' property from 'PermissionDescriptor': The provided value '${query.name}' is not a valid enum value of type PermissionName.`,
                        );
                    }
                    const permSetting = settings.supportedPermissions[query.name];
                    const returnName = permSetting.name || query.name;
                    let returnStatus = settings.permissionResponse || 'prompt';
                    if (permSetting.native) {
                        try {
                            const response = await this.messaging.request(MSG_PERMISSIONS_QUERY, query);
                            returnStatus = response.state || 'prompt';
                        } catch (err) {
                            // do nothing - keep returnStatus as-is
                        }
                    }
                    return Promise.resolve(new PermissionStatus(returnName, returnStatus));
                },
                {
                    get(target, name) {
                        return Reflect.get(target, name);
                    },
                },
            );
            // Expose the API
            // @ts-expect-error window.navigator isn't assignable
            window.navigator.permissions = permissions;
        }

        /**
         * Fixes screen lock/unlock APIs for Android WebView.
         */
        screenLockFix() {
            const validOrientations = [
                'any',
                'natural',
                'landscape',
                'portrait',
                'portrait-primary',
                'portrait-secondary',
                'landscape-primary',
                'landscape-secondary',
                'unsupported',
            ];

            this.wrapProperty(globalThis.ScreenOrientation.prototype, 'lock', {
                value: async (requestedOrientation) => {
                    if (!requestedOrientation) {
                        return Promise.reject(
                            new TypeError("Failed to execute 'lock' on 'ScreenOrientation': 1 argument required, but only 0 present."),
                        );
                    }
                    if (!validOrientations.includes(requestedOrientation)) {
                        return Promise.reject(
                            new TypeError(
                                `Failed to execute 'lock' on 'ScreenOrientation': The provided value '${requestedOrientation}' is not a valid enum value of type OrientationLockType.`,
                            ),
                        );
                    }
                    if (this.#activeScreenLockRequest) {
                        return Promise.reject(new DOMException('Screen lock already in progress', 'AbortError'));
                    }

                    this.#activeScreenLockRequest = this.messaging.request(MSG_SCREEN_LOCK, { orientation: requestedOrientation });
                    let resp;
                    try {
                        resp = await this.#activeScreenLockRequest;
                    } catch (err) {
                        throw new DOMException(err.message, 'DataError');
                    } finally {
                        this.#activeScreenLockRequest = null;
                    }

                    if (resp.failure) {
                        switch (resp.failure.name) {
                            case 'TypeError':
                                return Promise.reject(new TypeError(resp.failure.message));
                            case 'InvalidStateError':
                                return Promise.reject(new DOMException(resp.failure.message, resp.failure.name));
                            default:
                                return Promise.reject(new DOMException(resp.failure.message, 'DataError'));
                        }
                    }

                    return Promise.resolve();
                },
            });

            this.wrapProperty(globalThis.ScreenOrientation.prototype, 'unlock', {
                value: () => {
                    this.messaging.request(MSG_SCREEN_UNLOCK, {});
                },
            });
        }

        /**
         * Add missing navigator.credentials API
         */
        navigatorCredentialsFix() {
            try {
                if ('credentials' in navigator && 'get' in navigator.credentials) {
                    return;
                }
                const value = {
                    get() {
                        return Promise.reject(new Error());
                    },
                };
                // TODO: original property is an accessor descriptor
                this.defineProperty(Navigator.prototype, 'credentials', {
                    value,
                    configurable: true,
                    enumerable: true,
                    writable: true,
                });
            } catch {
                // Ignore exceptions that could be caused by conflicting with other extensions
            }
        }

        safariObjectFix() {
            try {
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                if (window.safari) {
                    return;
                }
                this.defineProperty(window, 'safari', {
                    value: {},
                    writable: true,
                    configurable: true,
                    enumerable: true,
                });
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                this.defineProperty(window.safari, 'pushNotification', {
                    value: {},
                    configurable: true,
                    enumerable: true,
                });
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                this.defineProperty(window.safari.pushNotification, 'toString', {
                    value: () => {
                        return '[object SafariRemoteNotification]';
                    },
                    configurable: true,
                    enumerable: true,
                });
                class SafariRemoteNotificationPermission {
                    constructor() {
                        this.deviceToken = null;
                        this.permission = 'denied';
                    }
                }
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                this.defineProperty(window.safari.pushNotification, 'permission', {
                    value: () => {
                        return new SafariRemoteNotificationPermission();
                    },
                    configurable: true,
                    enumerable: true,
                });
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                this.defineProperty(window.safari.pushNotification, 'requestPermission', {
                    value: (name, domain, options, callback) => {
                        if (typeof callback === 'function') {
                            callback(new SafariRemoteNotificationPermission());
                            return;
                        }
                        const reason = "Invalid 'callback' value passed to safari.pushNotification.requestPermission(). Expected a function.";
                        throw new Error(reason);
                    },
                    configurable: true,
                    enumerable: true,
                });
            } catch {
                // Ignore exceptions that could be caused by conflicting with other extensions
            }
        }

        mediaSessionFix() {
            try {
                if (window.navigator.mediaSession && "android" !== 'integration') {
                    return;
                }

                class MyMediaSession {
                    metadata = null;
                    /** @type {MediaSession['playbackState']} */
                    playbackState = 'none';

                    setActionHandler() {}
                    setCameraActive() {}
                    setMicrophoneActive() {}
                    setPositionState() {}
                }

                this.shimInterface('MediaSession', MyMediaSession, {
                    disallowConstructor: true,
                    allowConstructorCall: false,
                    wrapToString: true,
                });
                this.shimProperty(Navigator.prototype, 'mediaSession', new MyMediaSession(), true);

                this.shimInterface(
                    'MediaMetadata',
                    class {
                        constructor(metadata = {}) {
                            this.title = metadata.title;
                            this.artist = metadata.artist;
                            this.album = metadata.album;
                            this.artwork = metadata.artwork;
                        }
                    },
                    {
                        disallowConstructor: false,
                        allowConstructorCall: false,
                        wrapToString: true,
                    },
                );
            } catch {
                // Ignore exceptions that could be caused by conflicting with other extensions
            }
        }

        presentationFix() {
            try {
                // @ts-expect-error due to: Property 'presentation' does not exist on type 'Navigator'
                if (window.navigator.presentation && "android" !== 'integration') {
                    return;
                }

                const MyPresentation = class {
                    get defaultRequest() {
                        return null;
                    }

                    get receiver() {
                        return null;
                    }
                };

                // @ts-expect-error Presentation API is still experimental, TS types are missing
                this.shimInterface('Presentation', MyPresentation, {
                    disallowConstructor: true,
                    allowConstructorCall: false,
                    wrapToString: true,
                });

                this.shimInterface(
                    // @ts-expect-error Presentation API is still experimental, TS types are missing
                    'PresentationAvailability',
                    class {
                        // class definition is empty because there's no way to get an instance of it anyways
                    },
                    {
                        disallowConstructor: true,
                        allowConstructorCall: false,
                        wrapToString: true,
                    },
                );

                this.shimInterface(
                    // @ts-expect-error Presentation API is still experimental, TS types are missing
                    'PresentationRequest',
                    class {
                        // class definition is empty because there's no way to get an instance of it anyways
                    },
                    {
                        disallowConstructor: true,
                        allowConstructorCall: false,
                        wrapToString: true,
                    },
                );

                /** TODO: add shims for other classes in the Presentation API:
                 * PresentationConnection,
                 * PresentationReceiver,
                 * PresentationConnectionList,
                 * PresentationConnectionAvailableEvent,
                 * PresentationConnectionCloseEvent
                 */

                // @ts-expect-error Presentation API is still experimental, TS types are missing
                this.shimProperty(Navigator.prototype, 'presentation', new MyPresentation(), true);
            } catch {
                // Ignore exceptions that could be caused by conflicting with other extensions
            }
        }

        /**
         * Support for modifying localStorage entries
         */
        modifyLocalStorage() {
            /** @type {import('@duckduckgo/privacy-configuration/schema/features/webcompat').WebCompatSettings['modifyLocalStorage']} */
            const settings = this.getFeatureSetting('modifyLocalStorage');

            if (!settings || !settings.changes) return;

            settings.changes.forEach((change) => {
                if (change.action === 'delete') {
                    localStorage.removeItem(change.key);
                }
            });
        }

        /**
         * Support for modifying cookies
         */
        modifyCookies() {
            /** @type {import('@duckduckgo/privacy-configuration/schema/features/webcompat').WebCompatSettings['modifyCookies']} */
            const settings = this.getFeatureSetting('modifyCookies');

            if (!settings || !settings.changes) return;

            settings.changes.forEach((change) => {
                if (change.action === 'delete') {
                    const pathValue = change.path ? `; path=${change.path}` : '';
                    const domainValue = change.domain ? `; domain=${change.domain}` : '';
                    document.cookie = `${change.key}=; expires=Thu, 01 Jan 1970 00:00:00 GMT${pathValue}${domainValue}`;
                }
            });
        }

        /**
         * Support for proxying `window.webkit.messageHandlers`
         */
        messageHandlersFix() {
            /** @type {import('@duckduckgo/privacy-configuration/schema/features/webcompat').WebCompatSettings['messageHandlers']} */
            const settings = this.getFeatureSetting('messageHandlers');

            // Do nothing if `messageHandlers` is absent
            if (!globalThis.webkit?.messageHandlers) return;
            // This should never occur, but keeps typescript happy
            if (!settings) return;

            const proxy = new Proxy(globalThis.webkit.messageHandlers, {
                get(target, messageName, receiver) {
                    const handlerName = String(messageName);

                    // handle known message names, such as DDG webkit messaging
                    if (settings.handlerStrategies.reflect.includes(handlerName)) {
                        return Reflect.get(target, messageName, receiver);
                    }

                    if (settings.handlerStrategies.undefined.includes(handlerName)) {
                        return undefined;
                    }

                    if (settings.handlerStrategies.polyfill.includes('*') || settings.handlerStrategies.polyfill.includes(handlerName)) {
                        return {
                            postMessage() {
                                return Promise.resolve({});
                            },
                        };
                    }
                    // if we get here, we couldn't handle the message handler name, so we opt for doing nothing.
                    // It's unlikely we'll ever reach here, since `["*"]' should be present
                },
            });

            globalThis.webkit = {
                ...globalThis.webkit,
                messageHandlers: proxy,
            };
        }

        viewportWidthFix() {
            if (document.readyState === 'loading') {
                // if the document is not ready, we may miss the original viewport tag
                document.addEventListener('DOMContentLoaded', () => this.viewportWidthFixInner());
            } else {
                this.viewportWidthFixInner();
            }
        }

        /**
         * create or update a viewport tag with the given content
         * @param {HTMLMetaElement|null} viewportTag
         * @param {string} forcedValue
         */
        forceViewportTag(viewportTag, forcedValue) {
            const viewportTagExists = Boolean(viewportTag);
            if (!viewportTag) {
                viewportTag = document.createElement('meta');
                viewportTag.setAttribute('name', 'viewport');
            }
            viewportTag.setAttribute('content', forcedValue);
            if (!viewportTagExists) {
                document.head.appendChild(viewportTag);
            }
        }

        viewportWidthFixInner() {
            /** @type {NodeListOf<HTMLMetaElement>} **/
            const viewportTags = document.querySelectorAll('meta[name=viewport i]');
            // Chrome respects only the last viewport tag
            const viewportTag = viewportTags.length === 0 ? null : viewportTags[viewportTags.length - 1];
            const viewportContent = viewportTag?.getAttribute('content') || '';
            /** @type {readonly string[]} **/
            const viewportContentParts = viewportContent ? viewportContent.split(/,|;/) : [];
            /** @type {readonly string[][]} **/
            const parsedViewportContent = viewportContentParts.map((part) => {
                const [key, value] = part.split('=').map((p) => p.trim().toLowerCase());
                return [key, value];
            });

            // first, check if there are any forced values
            const { forcedDesktopValue, forcedMobileValue } = this.getFeatureSetting('viewportWidth');
            if (typeof forcedDesktopValue === 'string' && this.desktopModeEnabled) {
                this.forceViewportTag(viewportTag, forcedDesktopValue);
                return;
            } else if (typeof forcedMobileValue === 'string' && !this.desktopModeEnabled) {
                this.forceViewportTag(viewportTag, forcedMobileValue);
                return;
            }

            // otherwise, check for special cases
            const forcedValues = {};

            if (this.forcedZoomEnabled) {
                forcedValues['initial-scale'] = 1;
                forcedValues['user-scalable'] = 'yes';
                forcedValues['maximum-scale'] = 10;
            }

            if (this.getFeatureSettingEnabled('plainTextViewPort') && document.contentType === 'text/plain') {
                // text should span the full screen width
                forcedValues.width = 'device-width';
                // keep default scale to prevent text from appearing too small
                forcedValues['initial-scale'] = 1;
            } else if (!viewportTag || this.desktopModeEnabled) {
                // force wide viewport width
                forcedValues.width = screen.width >= 1280 ? 1280 : 980;
                forcedValues['initial-scale'] = (screen.width / forcedValues.width).toFixed(3);
                // Race condition: depending on the loading state of the page, initial scale may or may not be respected, so the page may look zoomed-in after applying this hack.
                // Usually this is just an annoyance, but it may be a bigger issue if user-scalable=no is set, so we remove it too.
                forcedValues['user-scalable'] = 'yes';
            } else {
                // mobile mode with a viewport tag
                // fix an edge case where WebView forces the wide viewport
                const widthPart = parsedViewportContent.find(([key]) => key === 'width');
                const initialScalePart = parsedViewportContent.find(([key]) => key === 'initial-scale');
                if (!widthPart && initialScalePart) {
                    // Chromium accepts float values for initial-scale
                    const parsedInitialScale = parseFloat(initialScalePart[1]);
                    if (parsedInitialScale !== 1) {
                        forcedValues.width = 'device-width';
                    }
                }
            }

            const newContent = [];
            Object.keys(forcedValues).forEach((key) => {
                newContent.push(`${key}=${forcedValues[key]}`);
            });

            if (newContent.length > 0) {
                // need to override at least one viewport component
                parsedViewportContent.forEach(([key], idx) => {
                    if (!(key in forcedValues)) {
                        newContent.push(viewportContentParts[idx].trim()); // reuse the original values, not the parsed ones
                    }
                });
                this.forceViewportTag(viewportTag, newContent.join(', '));
            }
        }
    }

    /**
     * @returns array of performance metrics
     */
    function getJsPerformanceMetrics() {
        const paintResources = performance.getEntriesByType('paint');
        const firstPaint = paintResources.find((entry) => entry.name === 'first-contentful-paint');
        return firstPaint ? [firstPaint.startTime] : [];
    }

    class BreakageReporting extends ContentFeature {
        init() {
            this.messaging.subscribe('getBreakageReportValues', () => {
                const jsPerformance = getJsPerformanceMetrics();
                const referrer = document.referrer;

                this.messaging.notify('breakageReportResult', {
                    jsPerformance,
                    referrer,
                });
            });
        }
    }

    const MSG_NAME_INITIAL_SETUP = 'initialSetup';
    const MSG_NAME_SET_VALUES = 'setUserValues';
    const MSG_NAME_READ_VALUES = 'getUserValues';
    const MSG_NAME_READ_VALUES_SERP = 'readUserValues';
    const MSG_NAME_OPEN_PLAYER = 'openDuckPlayer';
    const MSG_NAME_OPEN_INFO = 'openInfo';
    const MSG_NAME_PUSH_DATA = 'onUserValuesChanged';
    const MSG_NAME_PIXEL = 'sendDuckPlayerPixel';
    const MSG_NAME_PROXY_INCOMING = 'ddg-serp-yt';
    const MSG_NAME_PROXY_RESPONSE = 'ddg-serp-yt-response';

    /* eslint-disable promise/prefer-await-to-then */

    /**
     * @typedef {import("@duckduckgo/messaging").Messaging} Messaging
     *
     * A wrapper for all communications.
     *
     * Please see https://duckduckgo.github.io/content-scope-utils/modules/Webkit_Messaging for the underlying
     * messaging primitives.
     */
    class DuckPlayerOverlayMessages {
        /**
         * @param {Messaging} messaging
         * @param {import('./overlays.js').Environment} environment
         * @internal
         */
        constructor(messaging, environment) {
            /**
             * @internal
             */
            this.messaging = messaging;
            this.environment = environment;
        }

        /**
         * @returns {Promise<import("../duck-player.js").OverlaysInitialSettings>}
         */
        initialSetup() {
            if (this.environment.isIntegrationMode()) {
                return Promise.resolve({
                    userValues: {
                        overlayInteracted: false,
                        privatePlayerMode: { alwaysAsk: {} },
                    },
                    ui: {},
                });
            }
            return this.messaging.request(MSG_NAME_INITIAL_SETUP);
        }

        /**
         * Inform the native layer that an interaction occurred
         * @param {import("../duck-player.js").UserValues} userValues
         * @returns {Promise<import("../duck-player.js").UserValues>}
         */
        setUserValues(userValues) {
            return this.messaging.request(MSG_NAME_SET_VALUES, userValues);
        }

        /**
         * @returns {Promise<import("../duck-player.js").UserValues>}
         */
        getUserValues() {
            return this.messaging.request(MSG_NAME_READ_VALUES, {});
        }

        /**
         * @param {Pixel} pixel
         */
        sendPixel(pixel) {
            this.messaging.notify(MSG_NAME_PIXEL, {
                pixelName: pixel.name(),
                params: pixel.params(),
            });
        }

        /**
         * This is sent when the user wants to open Duck Player.
         * See {@link OpenInDuckPlayerMsg} for params
         * @param {OpenInDuckPlayerMsg} params
         */
        openDuckPlayer(params) {
            return this.messaging.notify(MSG_NAME_OPEN_PLAYER, params);
        }

        /**
         * This is sent when the user wants to open Duck Player.
         */
        openInfo() {
            return this.messaging.notify(MSG_NAME_OPEN_INFO);
        }

        /**
         * Get notification when preferences/state changed
         * @param {(userValues: import("../duck-player.js").UserValues) => void} cb
         */
        onUserValuesChanged(cb) {
            return this.messaging.subscribe('onUserValuesChanged', cb);
        }

        /**
         * Get notification when ui settings changed
         * @param {(userValues: import("../duck-player.js").UISettings) => void} cb
         */
        onUIValuesChanged(cb) {
            return this.messaging.subscribe('onUIValuesChanged', cb);
        }

        /**
         * This allows our SERP to interact with Duck Player settings.
         */
        serpProxy() {
            function respond(kind, data) {
                window.dispatchEvent(
                    new CustomEvent(MSG_NAME_PROXY_RESPONSE, {
                        detail: { kind, data },
                        composed: true,
                        bubbles: true,
                    }),
                );
            }

            // listen for setting and forward to the SERP window
            this.onUserValuesChanged((values) => {
                respond(MSG_NAME_PUSH_DATA, values);
            });

            // accept messages from the SERP and forward them to native
            window.addEventListener(MSG_NAME_PROXY_INCOMING, (evt) => {
                try {
                    assertCustomEvent(evt);
                    if (evt.detail.kind === MSG_NAME_SET_VALUES) {
                        return this.setUserValues(evt.detail.data)
                            .then((updated) => respond(MSG_NAME_PUSH_DATA, updated))
                            .catch(console.error);
                    }
                    if (evt.detail.kind === MSG_NAME_READ_VALUES_SERP) {
                        return this.getUserValues()
                            .then((updated) => respond(MSG_NAME_PUSH_DATA, updated))
                            .catch(console.error);
                    }
                    if (evt.detail.kind === MSG_NAME_OPEN_INFO) {
                        return this.openInfo();
                    }
                    console.warn('unhandled event', evt);
                } catch (e) {
                    console.warn('cannot handle this message', e);
                }
            });
        }
    }

    /**
     * @param {any} event
     * @returns {asserts event is CustomEvent<{kind: string, data: any}>}
     */
    function assertCustomEvent(event) {
        if (!('detail' in event)) throw new Error('none-custom event');
        if (typeof event.detail.kind !== 'string') throw new Error('custom event requires detail.kind to be a string');
    }

    class Pixel {
        /**
         * A list of known pixels
         * @param {{name: "overlay"}
         *   | {name: "play.use", remember: "0" | "1"}
         *   | {name: "play.use.thumbnail"}
         *   | {name: "play.do_not_use", remember: "0" | "1"}} input
         */
        constructor(input) {
            this.input = input;
        }

        name() {
            return this.input.name;
        }

        params() {
            switch (this.input.name) {
                case 'overlay':
                    return {};
                case 'play.use.thumbnail':
                    return {};
                case 'play.use':
                case 'play.do_not_use': {
                    return { remember: this.input.remember };
                }
                default:
                    throw new Error('unreachable');
            }
        }
    }

    class OpenInDuckPlayerMsg {
        /**
         * @param {object} params
         * @param {string} params.href
         */
        constructor(params) {
            this.href = params.href;
        }
    }

    /* eslint-disable promise/prefer-await-to-then */
    /**
     * Add an event listener to an element that is only executed if it actually comes from a user action
     * @param {Element} element - to attach event to
     * @param {string} event
     * @param {function} callback
     */

    /**
     * Try to load an image first. If the status code is 2xx, then continue
     * to load
     * @param {HTMLElement} parent
     * @param {string} targetSelector
     * @param {string} imageUrl
     */
    function appendImageAsBackground(parent, targetSelector, imageUrl) {

        /**
         * Make a HEAD request to see what the status of this image is, without
         * having to fully download it.
         *
         * This is needed because YouTube returns a 404 + valid image file when there's no
         * thumbnail and you can't tell the difference through the 'onload' event alone
         */
        fetch(imageUrl, { method: 'HEAD' })
            .then((x) => {
                const status = String(x.status);
                if (status.startsWith('2')) {
                    {
                        append();
                    }
                } else {
                    markError();
                }
            })
            .catch(() => {
                console.error('e from fetch');
            });

        /**
         * If loading fails, mark the parent with data-attributes
         */
        function markError() {
            parent.dataset.thumbLoaded = String(false);
            parent.dataset.error = String(true);
        }

        /**
         * If loading succeeds, try to append the image
         */
        function append() {
            const targetElement = parent.querySelector(targetSelector);
            if (!(targetElement instanceof HTMLElement)) {
                return console.warn('could not find child with selector', targetSelector, 'from', parent);
            }
            parent.dataset.thumbLoaded = String(true);
            parent.dataset.thumbSrc = imageUrl;
            const img = new Image();
            img.src = imageUrl;
            img.onload = function () {
                targetElement.style.backgroundImage = `url(${imageUrl})`;
                targetElement.style.backgroundSize = 'cover';
            };
            img.onerror = function () {
                markError();
                const targetElement = parent.querySelector(targetSelector);
                if (!(targetElement instanceof HTMLElement)) return;
                targetElement.style.backgroundImage = '';
            };
        }
    }

    class SideEffects {
        /**
         * @param {object} params
         * @param {boolean} [params.debug]
         */
        constructor({ debug = false } = {}) {
            this.debug = debug;
        }

        /** @type {{fn: () => void, name: string}[]} */
        _cleanups = [];
        /**
         * Wrap a side-effecting operation for easier debugging
         * and teardown/release of resources
         * @param {string} name
         * @param {() => () => void} fn
         */
        add(name, fn) {
            try {
                if (this.debug) {
                    console.log('☢️', name);
                }
                const cleanup = fn();
                if (typeof cleanup === 'function') {
                    this._cleanups.push({ name, fn: cleanup });
                }
            } catch (e) {
                console.error('%s threw an error', name, e);
            }
        }

        /**
         * Remove elements, event listeners etc
         */
        destroy() {
            for (const cleanup of this._cleanups) {
                if (typeof cleanup.fn === 'function') {
                    try {
                        if (this.debug) {
                            console.log('🗑️', cleanup.name);
                        }
                        cleanup.fn();
                    } catch (e) {
                        console.error(`cleanup ${cleanup.name} threw`, e);
                    }
                } else {
                    throw new Error('invalid cleanup');
                }
            }
            this._cleanups = [];
        }
    }

    /**
     * A container for valid/parsed video params.
     *
     * If you have an instance of `VideoParams`, then you can trust that it's valid, and you can always
     * produce a PrivatePlayer link from it
     *
     * The purpose is to co-locate all processing of search params/pathnames for easier security auditing/testing
     *
     * @example
     *
     * ```
     * const privateUrl = VideoParams.fromHref("https://example.com/foo/bar?v=123&t=21")?.toPrivatePlayerUrl()
     *       ^^^^ <- this is now null, or a string if it was valid
     * ```
     */
    class VideoParams {
        /**
         * @param {string} id - the YouTube video ID
         * @param {string|null|undefined} time - an optional time
         */
        constructor(id, time) {
            this.id = id;
            this.time = time;
        }

        static validVideoId = /^[a-zA-Z0-9-_]+$/;
        static validTimestamp = /^[0-9hms]+$/;

        /**
         * @returns {string}
         */
        toPrivatePlayerUrl() {
            // no try/catch because we already validated the ID
            // in Microsoft WebView2 v118+ changing from special protocol (https) to non-special one (duck) is forbidden
            // so we need to construct duck player this way
            const duckUrl = new URL(`duck://player/${this.id}`);

            if (this.time) {
                duckUrl.searchParams.set('t', this.time);
            }
            return duckUrl.href;
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
            } catch (e) {
                return null;
            }
            if (!url.pathname.startsWith('/watch')) {
                return null;
            }
            return VideoParams.fromHref(url.href);
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
            } catch (e) {
                return null;
            }
            return VideoParams.fromHref(url.href);
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
            } catch (e) {
                return null;
            }

            let id = null;

            // known params
            const vParam = url.searchParams.get('v');
            const tParam = url.searchParams.get('t');

            // don't continue if 'list' is present, but 'index' is not.
            //   valid: '/watch?v=321&list=123&index=1234'
            // invalid: '/watch?v=321&list=123' <- index absent
            if (url.searchParams.has('list') && !url.searchParams.has('index')) {
                return null;
            }

            let time = null;

            // ensure youtube video id is good
            if (vParam && VideoParams.validVideoId.test(vParam)) {
                id = vParam;
            } else {
                // if the video ID is invalid, we cannot produce an instance of VideoParams
                return null;
            }

            // ensure timestamp is good, if set
            if (tParam && VideoParams.validTimestamp.test(tParam)) {
                time = tParam;
            }

            return new VideoParams(id, time);
        }
    }

    /**
     * A helper to run a callback when the DOM is loaded.
     * Construct this early, so that the event listener is added as soon as possible.
     * Then you can add callbacks to it, and they will be called when the DOM is loaded, or immediately
     * if the DOM is already loaded.
     */
    class DomState {
        loaded = false;
        loadedCallbacks = [];
        constructor() {
            window.addEventListener('DOMContentLoaded', () => {
                this.loaded = true;
                this.loadedCallbacks.forEach((cb) => cb());
            });
        }

        onLoaded(loadedCallback) {
            if (this.loaded) return loadedCallback();
            this.loadedCallbacks.push(loadedCallback);
        }
    }

    var css$1 = "/* -- THUMBNAIL OVERLAY -- */\n.ddg-overlay {\n    font-family: system, -apple-system, system-ui, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif, \"Apple Color Emoji\", \"Segoe UI Emoji\", \"Segoe UI Symbol\";\n    position: absolute;\n    margin-top: 5px;\n    margin-left: 5px;\n    z-index: 1000;\n    height: 32px;\n\n    background: rgba(0, 0, 0, 0.6);\n    box-shadow: 0px 1px 2px rgba(0, 0, 0, 0.25), 0px 4px 8px rgba(0, 0, 0, 0.1), inset 0px 0px 0px 1px rgba(0, 0, 0, 0.18);\n    backdrop-filter: blur(2px);\n    -webkit-backdrop-filter: blur(2px);\n    border-radius: 6px;\n\n    transition: 0.15s linear background;\n}\n\n.ddg-overlay a.ddg-play-privately {\n    color: white;\n    text-decoration: none;\n    font-style: normal;\n    font-weight: 600;\n    font-size: 12px;\n}\n\n.ddg-overlay .ddg-dax,\n.ddg-overlay .ddg-play-icon {\n    display: inline-block;\n\n}\n\n.ddg-overlay .ddg-dax {\n    float: left;\n    padding: 4px 4px;\n    width: 24px;\n    height: 24px;\n}\n\n.ddg-overlay .ddg-play-text-container {\n    width: 0px;\n    overflow: hidden;\n    float: left;\n    opacity: 0;\n    transition: all 0.15s linear;\n}\n\n.ddg-overlay .ddg-play-text {\n    line-height: 14px;\n    margin-top: 10px;\n    width: 200px;\n}\n\n.ddg-overlay .ddg-play-icon {\n    float: right;\n    width: 24px;\n    height: 20px;\n    padding: 6px 4px;\n}\n\n.ddg-overlay:not([data-size=\"fixed small\"]):hover .ddg-play-text-container {\n    width: 80px;\n    opacity: 1;\n}\n\n.ddg-overlay[data-size^=\"video-player\"].hidden {\n    display: none;\n}\n\n.ddg-overlay[data-size=\"video-player\"] {\n    bottom: 145px;\n    right: 20px;\n    opacity: 1;\n    transition: opacity .2s;\n}\n\n.html5-video-player.playing-mode.ytp-autohide .ddg-overlay[data-size=\"video-player\"] {\n    opacity: 0;\n}\n\n.html5-video-player.ad-showing .ddg-overlay[data-size=\"video-player\"] {\n    display: none;\n}\n\n.html5-video-player.ytp-hide-controls .ddg-overlay[data-size=\"video-player\"] {\n    display: none;\n}\n\n.ddg-overlay[data-size=\"video-player-with-title\"] {\n    top: 40px;\n    left: 10px;\n}\n\n.ddg-overlay[data-size=\"video-player-with-paid-content\"] {\n    top: 65px;\n    left: 11px;\n}\n\n.ddg-overlay[data-size=\"title\"] {\n    position: relative;\n    margin: 0;\n    float: right;\n}\n\n.ddg-overlay[data-size=\"title\"] .ddg-play-text-container {\n    width: 90px;\n}\n\n.ddg-overlay[data-size^=\"fixed\"] {\n    position: absolute;\n    top: 0;\n    left: 0;\n    display: none;\n    z-index: 10;\n}\n\n#preview .ddg-overlay {\n    transition: transform 160ms ease-out 200ms;\n    /*TODO: scale needs to equal 1/--ytd-video-preview-initial-scale*/\n    transform: scale(1.15) translate(5px, 4px);\n}\n\n#preview ytd-video-preview[active] .ddg-overlay {\n    transform:scale(1) translate(0px, 0px);\n}\n";

    var dax = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 32 32\" fill=\"none\"><path fill=\"#DE5833\" fill-rule=\"evenodd\" d=\"M16 32c8.837 0 16-7.163 16-16S24.837 0 16 0 0 7.163 0 16s7.163 16 16 16Z\" clip-rule=\"evenodd\"/><path fill=\"#DDD\" fill-rule=\"evenodd\" d=\"M18.25 27.938c0-.125.03-.154-.367-.946-1.056-2.115-2.117-5.096-1.634-7.019.088-.349-.995-12.936-1.76-13.341-.85-.453-1.898-1.172-2.855-1.332-.486-.078-1.123-.041-1.62.026-.089.012-.093.17-.008.2.327.11.724.302.958.593.044.055-.016.142-.086.144-.22.008-.62.1-1.148.549-.061.052-.01.148.068.133 1.134-.225 2.292-.114 2.975.506.044.04.021.113-.037.128-5.923 1.61-4.75 6.763-3.174 13.086 1.405 5.633 1.934 7.448 2.1 8.001a.18.18 0 0 0 .106.117c2.039.812 6.482.848 6.482-.533v-.313Z\" clip-rule=\"evenodd\"/><path fill=\"#fff\" fill-rule=\"evenodd\" d=\"M30.688 16c0 8.112-6.576 14.688-14.688 14.688-8.112 0-14.688-6.576-14.688-14.688C1.313 7.888 7.888 1.312 16 1.312c8.112 0 14.688 6.576 14.688 14.688ZM12.572 28.996a140.697 140.697 0 0 1-2.66-9.48L9.8 19.06v-.003C8.442 13.516 7.334 8.993 13.405 7.57c.055-.013.083-.08.046-.123-.697-.826-2.001-1.097-3.651-.528-.068.024-.127-.045-.085-.102.324-.446.956-.79 1.268-.94.065-.03.06-.125-.008-.146a6.968 6.968 0 0 0-.942-.225c-.093-.015-.101-.174-.008-.186 2.339-.315 4.781.387 6.007 1.931a.081.081 0 0 0 .046.029c4.488.964 4.81 8.058 4.293 8.382-.102.063-.429.027-.86-.021-1.746-.196-5.204-.583-2.35 4.736.028.053-.01.122-.068.132-1.604.25.438 5.258 1.953 8.58C25 27.71 29.437 22.374 29.437 16c0-7.421-6.016-13.438-13.437-13.438C8.579 2.563 2.562 8.58 2.562 16c0 6.237 4.25 11.481 10.01 12.996Z\" clip-rule=\"evenodd\"/><path fill=\"#3CA82B\" d=\"M21.07 22.675c-.341-.159-1.655.783-2.527 1.507-.183-.258-.526-.446-1.301-.31-.678.117-1.053.28-1.22.563-1.07-.406-2.872-1.033-3.307-.428-.476.662.119 3.79.75 4.197.33.212 1.908-.802 2.732-1.502.133.188.347.295.787.284.665-.015 1.744-.17 1.912-.48a.341.341 0 0 0 .026-.066c.847.316 2.338.651 2.67.601.869-.13-.12-4.18-.522-4.366Z\"/><path fill=\"#4CBA3C\" d=\"M18.622 24.274a1.3 1.3 0 0 1 .09.2c.12.338.317 1.412.169 1.678-.15.265-1.115.393-1.711.403-.596.01-.73-.207-.851-.545-.097-.27-.144-.905-.143-1.269-.024-.539.173-.729 1.083-.876.674-.11 1.03.018 1.237.235.957-.714 2.553-1.722 2.709-1.538.777.918.875 3.105.707 3.985-.055.287-2.627-.285-2.627-.595 0-1.288-.334-1.642-.663-1.678ZM12.99 23.872c.21-.333 1.918.081 2.856.498 0 0-.193.873.114 1.901.09.301-2.157 1.64-2.45 1.41-.339-.267-.963-3.108-.52-3.809Z\"/><path fill=\"#FC3\" fill-rule=\"evenodd\" d=\"M13.817 17.101c.138-.6.782-1.733 3.08-1.705 1.163-.005 2.606 0 3.563-.11a12.813 12.813 0 0 0 3.181-.773c.995-.38 1.348-.295 1.472-.068.136.25-.024.68-.372 1.077-.664.758-1.857 1.345-3.966 1.52-2.108.174-3.505-.392-4.106.529-.26.397-.06 1.333 1.98 1.628 2.755.397 5.018-.48 5.297.05.28.53-1.33 1.607-4.09 1.63-2.76.022-4.484-.967-5.095-1.458-.775-.624-1.123-1.533-.944-2.32Z\" clip-rule=\"evenodd\"/><g fill=\"#14307E\" opacity=\".8\"><path d=\"M17.332 10.532c.154-.252.495-.447 1.054-.447.558 0 .821.222 1.003.47.037.05-.02.11-.076.085a29.677 29.677 0 0 1-.043-.018c-.204-.09-.455-.199-.884-.205-.46-.006-.75.109-.932.208-.062.033-.159-.034-.122-.093ZM11.043 10.854c.542-.226.968-.197 1.27-.126.063.015.107-.053.057-.094-.234-.189-.758-.423-1.44-.168-.61.227-.897.699-.899 1.009 0 .073.15.08.19.017.104-.167.28-.411.822-.638Z\"/><path fill-rule=\"evenodd\" d=\"M18.86 13.98a.867.867 0 0 1-.868-.865.867.867 0 0 1 1.737 0 .867.867 0 0 1-.869.865Zm.612-1.152a.225.225 0 0 0-.45 0 .225.225 0 0 0 .45 0ZM13.106 13.713a1.01 1.01 0 0 1-1.012 1.01 1.011 1.011 0 0 1-1.013-1.01c0-.557.454-1.009 1.013-1.009.558 0 1.012.452 1.012 1.01Zm-.299-.334a.262.262 0 0 0-.524 0 .262.262 0 0 0 .524 0Z\" clip-rule=\"evenodd\"/></g></svg>";

    /**
     * The following code is originally from https://github.com/mozilla-extensions/secure-proxy/blob/db4d1b0e2bfe0abae416bf04241916f9e4768fd2/src/commons/template.js
     */
    class Template {
        constructor(strings, values) {
            this.values = values;
            this.strings = strings;
        }

        /**
         * Escapes any occurrences of &, ", <, > or / with XML entities.
         *
         * @param {string} str
         *        The string to escape.
         * @return {string} The escaped string.
         */
        escapeXML(str) {
            const replacements = {
                '&': '&amp;',
                '"': '&quot;',
                "'": '&apos;',
                '<': '&lt;',
                '>': '&gt;',
                '/': '&#x2F;',
            };
            return String(str).replace(/[&"'<>/]/g, (m) => replacements[m]);
        }

        potentiallyEscape(value) {
            if (typeof value === 'object') {
                if (value instanceof Array) {
                    return value.map((val) => this.potentiallyEscape(val)).join('');
                }

                // If we are an escaped template let join call toString on it
                if (value instanceof Template) {
                    return value;
                }

                throw new Error('Unknown object to escape');
            }
            return this.escapeXML(value);
        }

        toString() {
            const result = [];

            for (const [i, string] of this.strings.entries()) {
                result.push(string);
                if (i < this.values.length) {
                    result.push(this.potentiallyEscape(this.values[i]));
                }
            }
            return result.join('');
        }
    }

    function html(strings, ...values) {
        return new Template(strings, values);
    }

    /**
     * @param {string} string
     * @return {Template}
     */
    function trustedUnsafe(string) {
        return html([string]);
    }

    /**
     * Use a policy if trustedTypes is available
     * @return {{createHTML: (s: string) => any}}
     */
    function createPolicy() {
        if (globalThis.trustedTypes) {
            return globalThis.trustedTypes?.createPolicy?.('ddg-default', { createHTML: (s) => s });
        }
        return {
            createHTML: (s) => s,
        };
    }

    /**
     * If this get's localised in the future, this would likely be in a json file
     */
    const text = {
        playText: {
            title: 'Duck Player',
        },
        videoOverlayTitle: {
            title: 'Tired of targeted YouTube ads and recommendations?',
        },
        videoOverlayTitle2: {
            title: 'Turn on Duck Player to watch without targeted ads',
        },
        videoOverlayTitle3: {
            title: 'Drowning in ads on YouTube? {newline} Turn on Duck Player.',
        },
        videoOverlaySubtitle: {
            title: 'provides a clean viewing experience without personalized ads and prevents viewing activity from influencing your YouTube recommendations.',
        },
        videoOverlaySubtitle2: {
            title: 'What you watch in DuckDuckGo won’t influence your recommendations on YouTube.',
        },
        videoButtonOpen: {
            title: 'Watch in Duck Player',
        },
        videoButtonOpen2: {
            title: 'Turn On Duck Player',
        },
        videoButtonOptOut: {
            title: 'Watch Here',
        },
        videoButtonOptOut2: {
            title: 'No Thanks',
        },
        rememberLabel: {
            title: 'Remember my choice',
        },
    };

    const i18n = {
        /**
         * @param {keyof text} name
         */
        t(name) {
            // eslint-disable-next-line no-prototype-builtins
            if (!text.hasOwnProperty(name)) {
                console.error(`missing key ${name}`);
                return 'missing';
            }
            const match = text[name];
            if (!match.title) {
                return 'missing';
            }
            return match.title;
        },
    };

    /**
     * @typedef {ReturnType<html>} Template
     */

    /**
     * @typedef {Object} OverlayCopyTranslation
     * @property {string | Template} title
     * @property {string | Template} subtitle
     * @property {string | Template} buttonOptOut
     * @property {string | Template} buttonOpen
     * @property {string | Template} rememberLabel
     */

    /**
     *  @type {Record<'default', OverlayCopyTranslation>}
     */
    const overlayCopyVariants = {
        default: {
            title: i18n.t('videoOverlayTitle2'),
            subtitle: i18n.t('videoOverlaySubtitle2'),
            buttonOptOut: i18n.t('videoButtonOptOut2'),
            buttonOpen: i18n.t('videoButtonOpen2'),
            rememberLabel: i18n.t('rememberLabel'),
        },
    };

    /**
     * @param {Record<string, string>} lookup
     * @returns {OverlayCopyTranslation}
     */
    const mobileStrings = (lookup) => {
        return {
            title: lookup.videoOverlayTitle2,
            subtitle: lookup.videoOverlaySubtitle2,
            buttonOptOut: lookup.videoButtonOptOut2,
            buttonOpen: lookup.videoButtonOpen2,
            rememberLabel: lookup.rememberLabel,
        };
    };

    class IconOverlay {
        sideEffects = new SideEffects();
        policy = createPolicy();

        /** @type {HTMLElement | null} */
        element = null;
        /**
         * Special class used for the overlay hover. For hovering, we use a
         * single element and move it around to the hovered video element.
         */
        HOVER_CLASS = 'ddg-overlay-hover';
        OVERLAY_CLASS = 'ddg-overlay';

        CSS_OVERLAY_MARGIN_TOP = 5;
        CSS_OVERLAY_HEIGHT = 32;

        /** @type {HTMLElement | null} */
        currentVideoElement = null;
        hoverOverlayVisible = false;

        /**
         * Creates an Icon Overlay.
         * @param {string} size - currently kind-of unused
         * @param {string} href - what, if any, href to set the link to by default.
         * @param {string} [extraClass] - whether to add any extra classes, such as hover
         * @returns {HTMLElement}
         */
        create(size, href, extraClass) {
            const overlayElement = document.createElement('div');

            overlayElement.setAttribute('class', 'ddg-overlay' + (extraClass ? ' ' + extraClass : ''));
            overlayElement.setAttribute('data-size', size);
            const svgIcon = trustedUnsafe(dax);
            const safeString = html` <a class="ddg-play-privately" href="#">
            <div class="ddg-dax">${svgIcon}</div>
            <div class="ddg-play-text-container">
                <div class="ddg-play-text">${i18n.t('playText')}</div>
            </div>
        </a>`.toString();

            overlayElement.innerHTML = this.policy.createHTML(safeString);

            overlayElement.querySelector('a.ddg-play-privately')?.setAttribute('href', href);
            return overlayElement;
        }

        /**
         * Util to return the hover overlay
         * @returns {HTMLElement | null}
         */
        getHoverOverlay() {
            return document.querySelector('.' + this.HOVER_CLASS);
        }

        /**
         * Moves the hover overlay to a specified videoElement
         * @param {HTMLElement} videoElement - which element to move it to
         */
        moveHoverOverlayToVideoElement(videoElement) {
            const overlay = this.getHoverOverlay();

            if (overlay === null || this.videoScrolledOutOfViewInPlaylist(videoElement)) {
                return;
            }

            const videoElementOffset = this.getElementOffset(videoElement);

            overlay.setAttribute(
                'style',
                '' + 'top: ' + videoElementOffset.top + 'px;' + 'left: ' + videoElementOffset.left + 'px;' + 'display:block;',
            );

            overlay.setAttribute('data-size', 'fixed ' + this.getThumbnailSize(videoElement));

            const href = videoElement.getAttribute('href');

            if (href) {
                const privateUrl = VideoParams.fromPathname(href)?.toPrivatePlayerUrl();
                if (overlay && privateUrl) {
                    overlay.querySelector('a')?.setAttribute('href', privateUrl);
                }
            }

            this.hoverOverlayVisible = true;
            this.currentVideoElement = videoElement;
        }

        /**
         * Returns true if the videoElement is scrolled out of view in a playlist. (In these cases
         * we don't want to show the overlay.)
         * @param {HTMLElement} videoElement
         * @returns {boolean}
         */
        videoScrolledOutOfViewInPlaylist(videoElement) {
            const inPlaylist = videoElement.closest('#items.playlist-items');

            if (inPlaylist) {
                const video = videoElement.getBoundingClientRect();
                const playlist = inPlaylist.getBoundingClientRect();

                const videoOutsideTop = video.top + this.CSS_OVERLAY_MARGIN_TOP < playlist.top;
                const videoOutsideBottom = video.top + this.CSS_OVERLAY_HEIGHT + this.CSS_OVERLAY_MARGIN_TOP > playlist.bottom;

                if (videoOutsideTop || videoOutsideBottom) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Return the offset of an HTML Element
         * @param {HTMLElement} el
         * @returns {Object}
         */
        getElementOffset(el) {
            const box = el.getBoundingClientRect();
            const docElem = document.documentElement;
            return {
                top: box.top + window.pageYOffset - docElem.clientTop,
                left: box.left + window.pageXOffset - docElem.clientLeft,
            };
        }

        /**
         * Hides the hover overlay element, but only if mouse pointer is outside of the hover overlay element
         */
        hideHoverOverlay(event, force) {
            const overlay = this.getHoverOverlay();

            const toElement = event.toElement;

            if (overlay) {
                // Prevent hiding overlay if mouseleave is triggered by user is actually hovering it and that
                // triggered the mouseleave event
                if (toElement === overlay || overlay.contains(toElement) || force) {
                    return;
                }

                this.hideOverlay(overlay);
                this.hoverOverlayVisible = false;
            }
        }

        /**
         * Util for hiding an overlay
         * @param {HTMLElement} overlay
         */
        hideOverlay(overlay) {
            overlay.setAttribute('style', 'display:none;');
        }

        /**
         * Appends the Hover Overlay to the page. This is the one that is shown on hover of any video thumbnail.
         * More performant / clean than adding an overlay to each and every video thumbnail. Also it prevents triggering
         * the video hover preview on the homepage if the user hovers the overlay, because user is no longer hovering
         * inside a video thumbnail when hovering the overlay. Nice.
         * @param {(href: string) => void} onClick
         */
        appendHoverOverlay(onClick) {
            this.sideEffects.add('Adding the re-usable overlay to the page ', () => {
                // add the CSS to the head
                const cleanUpCSS = this.loadCSS();

                // create and append the element
                const element = this.create('fixed', '', this.HOVER_CLASS);
                document.body.appendChild(element);

                this.addClickHandler(element, onClick);

                return () => {
                    element.remove();
                    cleanUpCSS();
                };
            });
        }

        loadCSS() {
            // add the CSS to the head
            const id = '__ddg__icon';
            const style = document.head.querySelector(`#${id}`);
            if (!style) {
                const style = document.createElement('style');
                style.id = id;
                style.textContent = css$1;
                document.head.appendChild(style);
            }
            return () => {
                const style = document.head.querySelector(`#${id}`);
                if (style) {
                    document.head.removeChild(style);
                }
            };
        }

        /**
         * @param {HTMLElement} container
         * @param {string} href
         * @param {(href: string) => void} onClick
         */
        appendSmallVideoOverlay(container, href, onClick) {
            this.sideEffects.add('Adding a small overlay for the video player', () => {
                // add the CSS to the head
                const cleanUpCSS = this.loadCSS();

                const element = this.create('video-player', href, 'hidden');

                this.addClickHandler(element, onClick);

                container.appendChild(element);
                element.classList.remove('hidden');

                return () => {
                    element?.remove();
                    cleanUpCSS();
                };
            });
        }

        getThumbnailSize(videoElement) {
            const imagesByArea = {};

            Array.from(videoElement.querySelectorAll('img')).forEach((image) => {
                imagesByArea[image.offsetWidth * image.offsetHeight] = image;
            });

            const largestImage = Math.max.apply(this, Object.keys(imagesByArea).map(Number));

            const getSizeType = (width, height) => {
                if (width < 123 + 10) {
                    // match CSS: width of expanded overlay + twice the left margin.
                    return 'small';
                } else if (width < 300 && height < 175) {
                    return 'medium';
                } else {
                    return 'large';
                }
            };

            return getSizeType(imagesByArea[largestImage].offsetWidth, imagesByArea[largestImage].offsetHeight);
        }

        /**
         * Handle when dax is clicked - prevent propagation
         * so no further listeners see this
         *
         * @param {HTMLElement} element - the wrapping div
         * @param {(href: string) => void} callback - the function to execute following a click
         */
        addClickHandler(element, callback) {
            element.addEventListener('click', (event) => {
                event.preventDefault();
                event.stopImmediatePropagation();
                const link = /** @type {HTMLElement} */ (event.target).closest('a');
                const href = link?.getAttribute('href');
                if (href) {
                    callback(href);
                }
            });
        }

        destroy() {
            this.sideEffects.destroy();
        }
    }

    /**
     *
     * ## Decision flow for `mouseover` (appending Dax)
     *
     * We'll try to append Dax icons onto thumbnails, if the following conditions are met:
     *
     * 1. User has Duck Player configured to 'always ask' (the default)
     * 2. `thumbnailOverlays` is enabled in the remote config
     *
     * If those are met, the following steps occur:
     *
     * - let `stack` be the entire element stack below the cursor
     * - let `eventTarget` be the event target that received the mouseover event `(e.target)`
     * - **exit** if any element in `stack` matches a css selector in `[config] hoverExcluded`
     * - let `match` be the first element that satisfies both conditions:
     *   1. matches the `[config] thumbLink` CSS selector
     *   2. can be converted into a valid DuckPlayer URL
     * - **exit** if `match` was not found, or a valid link could not be created
     * - **exit** if `match` is contained within any parent element defined in `[config] excludedRegions`
     * - **exit** if `match` contains any sub-links (nested `<a>` tags)
     * - **exit** if `match` does NOT contain an `img` tag
     * - if we get this far, mark `match` as a valid link element, then:
     *   - append Dax overlay to `match` ONLY if:
     *     - `eventTarget` is equal to `match`, or
     *     - `eventTarget` *contains* `match`, or
     *     - `eventTarget` matches a CSS selector in `[config] allowedEventTargets`
     *
     * ## Decision flow for `click interceptions` (opening Duck Player)
     *
     * We'll try to intercept clicks on thumbnails, if the following conditions are met:
     *
     * 1. User has Duck Player configured to 'enabled'
     * 2. `clickInterception` is enabled in the remote config
     *
     * If those are met, the following steps occur:
     *
     * - let `stack` be the entire element stack below the cursor when clicked
     * - let `eventTarget` be the event target that received click event `(e.target)`
     * - **exit** if any element in `stack` matches a css selector in `[config] clickExcluded`
     * - let `match` be the first element that satisfies both conditions:
     *     1. matches the `[config] thumbLink` CSS selector
     *     2. can be converted into a valid DuckPlayer URL
     * - **exit** if `match` was not found, or a valid link could not be created
     * - **exit** if `match` is contained within any parent element defined in `[config] excludedRegions`
     * - if we get this far, mark `match` as a valid link element, then:
     *     - prevent default + propagation on the event ONLY if:
     *         - `eventTarget` is equal to `match`, or
     *         - `eventTarget` *contains* `match`, or
     *         - `eventTarget` matches a CSS selector in `[config] allowedEventTargets`
     *     - otherwise, do nothing
     *
     * @module Duck Player Thumbnails
     */


    /**
     * @typedef ThumbnailParams
     * @property {import("../duck-player.js").OverlaysFeatureSettings} settings
     * @property {import("./overlays.js").Environment} environment
     * @property {import("../duck-player.js").DuckPlayerOverlayMessages} messages
     */

    /**
     * This features covers the implementation
     */
    class Thumbnails {
        sideEffects = new SideEffects();
        /**
         * @param {ThumbnailParams} params
         */
        constructor(params) {
            this.settings = params.settings;
            this.messages = params.messages;
            this.environment = params.environment;
        }

        /**
         * Perform side effects
         */
        init() {
            this.sideEffects.add('showing overlays on hover', () => {
                const { selectors } = this.settings;
                const parentNode = document.documentElement || document.body;

                // create the icon & append it to the page
                const icon = new IconOverlay();
                icon.appendHoverOverlay((href) => {
                    if (this.environment.opensVideoOverlayLinksViaMessage) {
                        this.messages.sendPixel(new Pixel({ name: 'play.use.thumbnail' }));
                    }

                    this.messages.openDuckPlayer(new OpenInDuckPlayerMsg({ href }));
                });

                // remember when a none-dax click occurs - so that we can avoid re-adding the
                // icon whilst the page is navigating
                let clicked = false;

                // detect all click, if it's anywhere on the page
                // but in the icon overlay itself, then just hide the overlay
                const clickHandler = (e) => {
                    const overlay = icon.getHoverOverlay();
                    if (overlay?.contains(e.target)) ; else if (overlay) {
                        clicked = true;
                        icon.hideOverlay(overlay);
                        icon.hoverOverlayVisible = false;
                        setTimeout(() => {
                            clicked = false;
                        }, 0);
                    }
                };

                parentNode.addEventListener('click', clickHandler, true);

                const removeOverlay = () => {
                    const overlay = icon.getHoverOverlay();
                    if (overlay) {
                        icon.hideOverlay(overlay);
                        icon.hoverOverlayVisible = false;
                    }
                };

                const appendOverlay = (element) => {
                    if (element && element.isConnected) {
                        icon.moveHoverOverlayToVideoElement(element);
                    }
                };

                // detect hovers and decide to show hover icon, or not
                const mouseOverHandler = (e) => {
                    if (clicked) return;
                    const hoverElement = findElementFromEvent(selectors.thumbLink, selectors.hoverExcluded, e);
                    const validLink = isValidLink(hoverElement, selectors.excludedRegions);

                    // if it's not an element we care about, bail early and remove the overlay
                    if (!hoverElement || !validLink) {
                        return removeOverlay();
                    }

                    // ensure it doesn't contain sub-links
                    if (hoverElement.querySelector('a[href]')) {
                        return removeOverlay();
                    }

                    // only add Dax when this link also contained an img
                    if (!hoverElement.querySelector('img')) {
                        return removeOverlay();
                    }

                    // if the hover target is the match, or contains the match, all good
                    if (e.target === hoverElement || hoverElement?.contains(e.target)) {
                        return appendOverlay(hoverElement);
                    }

                    // finally, check the 'allowedEventTargets' to see if the hover occurred in an element
                    // that we know to be a thumbnail overlay, like a preview
                    const matched = selectors.allowedEventTargets.find((css) => e.target.matches(css));
                    if (matched) {
                        appendOverlay(hoverElement);
                    }
                };

                parentNode.addEventListener('mouseover', mouseOverHandler, true);

                return () => {
                    parentNode.removeEventListener('mouseover', mouseOverHandler, true);
                    parentNode.removeEventListener('click', clickHandler, true);
                    icon.destroy();
                };
            });
        }

        destroy() {
            this.sideEffects.destroy();
        }
    }

    class ClickInterception {
        sideEffects = new SideEffects();
        /**
         * @param {ThumbnailParams} params
         */
        constructor(params) {
            this.settings = params.settings;
            this.messages = params.messages;
            this.environment = params.environment;
        }

        /**
         * Perform side effects
         */
        init() {
            this.sideEffects.add('intercepting clicks', () => {
                const { selectors } = this.settings;
                const parentNode = document.documentElement || document.body;

                const clickHandler = (e) => {
                    const elementInStack = findElementFromEvent(selectors.thumbLink, selectors.clickExcluded, e);
                    const validLink = isValidLink(elementInStack, selectors.excludedRegions);

                    const block = (href) => {
                        e.preventDefault();
                        e.stopImmediatePropagation();
                        this.messages.openDuckPlayer({ href });
                    };

                    // if there's no match, return early
                    if (!validLink) {
                        return;
                    }

                    // if the hover target is the match, or contains the match, all good
                    if (e.target === elementInStack || elementInStack?.contains(e.target)) {
                        return block(validLink);
                    }

                    // finally, check the 'allowedEventTargets' to see if the hover occurred in an element
                    // that we know to be a thumbnail overlay, like a preview
                    const matched = selectors.allowedEventTargets.find((css) => e.target.matches(css));
                    if (matched) {
                        block(validLink);
                    }
                };

                parentNode.addEventListener('click', clickHandler, true);

                return () => {
                    parentNode.removeEventListener('click', clickHandler, true);
                };
            });
        }

        destroy() {
            this.sideEffects.destroy();
        }
    }

    /**
     * @param {string} selector
     * @param {string[]} excludedSelectors
     * @param {MouseEvent} e
     * @return {HTMLElement|null}
     */
    function findElementFromEvent(selector, excludedSelectors, e) {
        /** @type {HTMLElement | null} */
        let matched = null;

        const fastPath = excludedSelectors.length === 0;

        for (const element of document.elementsFromPoint(e.clientX, e.clientY)) {
            // bail early if this item was excluded anywhere in the element stack
            if (excludedSelectors.some((ex) => element.matches(ex))) {
                return null;
            }

            // we cannot return this immediately, because another element in the stack
            // might have been excluded
            if (element.matches(selector)) {
                // in lots of cases we can just return the element as soon as it's found, to prevent
                // checking the entire stack
                matched = /** @type {HTMLElement} */ (element);
                if (fastPath) return matched;
            }
        }
        return matched;
    }

    /**
     * @param {HTMLElement|null} element
     * @param {string[]} excludedRegions
     * @return {string | null | undefined}
     */
    function isValidLink(element, excludedRegions) {
        if (!element) return null;

        /**
         * Does this element exist inside an excluded region?
         */
        const existsInExcludedParent = excludedRegions.some((selector) => {
            for (const parent of document.querySelectorAll(selector)) {
                if (parent.contains(element)) return true;
            }
            return false;
        });

        /**
         * Does this element exist inside an excluded region?
         * If so, bail
         */
        if (existsInExcludedParent) return null;

        /**
         * We shouldn't be able to get here, but this keeps Typescript happy
         * and is a good check regardless
         */
        if (!('href' in element)) return null;

        /**
         * If we get here, we're trying to convert the `element.href`
         * into a valid Duck Player URL
         */
        return VideoParams.fromHref(element.href)?.toPrivatePlayerUrl();
    }

    var css = "/* -- VIDEO PLAYER OVERLAY */\n:host {\n    position: absolute;\n    top: 0;\n    left: 0;\n    right: 0;\n    bottom: 0;\n    color: white;\n    z-index: 10000;\n}\n:host * {\n    font-family: system, -apple-system, system-ui, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif, \"Apple Color Emoji\", \"Segoe UI Emoji\", \"Segoe UI Symbol\";\n}\n.ddg-video-player-overlay {\n    font-size: 13px;\n    font-weight: 400;\n    line-height: 16px;\n    text-align: center;\n\n    position: absolute;\n    top: 0;\n    left: 0;\n    right: 0;\n    bottom: 0;\n    color: white;\n    z-index: 10000;\n}\n\n.ddg-eyeball svg {\n    width: 60px;\n    height: 60px;\n}\n\n.ddg-vpo-bg {\n    position: absolute;\n    top: 0;\n    left: 0;\n    right: 0;\n    bottom: 0;\n    color: white;\n    text-align: center;\n    background: black;\n}\n\n.ddg-vpo-bg:after {\n    content: \" \";\n    position: absolute;\n    display: block;\n    width: 100%;\n    height: 100%;\n    top: 0;\n    left: 0;\n    right: 0;\n    bottom: 0;\n    background: rgba(0,0,0,1); /* this gets overriden if the background image can be found */\n    color: white;\n    text-align: center;\n}\n\n.ddg-video-player-overlay[data-thumb-loaded=\"true\"] .ddg-vpo-bg:after {\n    background: rgba(0,0,0,0.75);\n}\n\n.ddg-vpo-content {\n    position: relative;\n    top: 50%;\n    transform: translate(-50%, -50%);\n    left: 50%;\n    max-width: 90%;\n}\n\n.ddg-vpo-eyeball {\n    margin-bottom: 18px;\n}\n\n.ddg-vpo-title {\n    font-size: 22px;\n    font-weight: 400;\n    line-height: 26px;\n    margin-top: 25px;\n}\n\n.ddg-vpo-text {\n    margin-top: 16px;\n    width: 496px;\n    margin-left: auto;\n    margin-right: auto;\n}\n\n.ddg-vpo-text b {\n    font-weight: 600;\n}\n\n.ddg-vpo-buttons {\n    margin-top: 25px;\n}\n.ddg-vpo-buttons > * {\n    display: inline-block;\n    margin: 0;\n    padding: 0;\n}\n\n.ddg-vpo-button {\n    color: white;\n    padding: 9px 16px;\n    font-size: 13px;\n    border-radius: 8px;\n    font-weight: 600;\n    display: inline-block;\n    text-decoration: none;\n}\n\n.ddg-vpo-button + .ddg-vpo-button {\n    margin-left: 10px;\n}\n\n.ddg-vpo-cancel {\n    background: #585b58;\n    border: 0.5px solid rgba(40, 145, 255, 0.05);\n    box-shadow: 0px 0px 0px 0.5px rgba(0, 0, 0, 0.1), 0px 0px 1px rgba(0, 0, 0, 0.05), 0px 1px 1px rgba(0, 0, 0, 0.2), inset 0px 0.5px 0px rgba(255, 255, 255, 0.2), inset 0px 1px 0px rgba(255, 255, 255, 0.05);\n}\n\n.ddg-vpo-open {\n    background: #3969EF;\n    border: 0.5px solid rgba(40, 145, 255, 0.05);\n    box-shadow: 0px 0px 0px 0.5px rgba(0, 0, 0, 0.1), 0px 0px 1px rgba(0, 0, 0, 0.05), 0px 1px 1px rgba(0, 0, 0, 0.2), inset 0px 0.5px 0px rgba(255, 255, 255, 0.2), inset 0px 1px 0px rgba(255, 255, 255, 0.05);\n}\n\n.ddg-vpo-open:hover {\n    background: #1d51e2;\n}\n.ddg-vpo-cancel:hover {\n    cursor: pointer;\n    background: #2f2f2f;\n}\n\n.ddg-vpo-remember {\n}\n.ddg-vpo-remember label {\n    display: flex;\n    align-items: center;\n    justify-content: center;\n    margin-top: 25px;\n    cursor: pointer;\n}\n.ddg-vpo-remember input {\n    margin-right: 6px;\n}\n";

    /**
     * The custom element that we use to present our UI elements
     * over the YouTube player
     */
    class DDGVideoOverlay extends HTMLElement {
        policy = createPolicy();

        static CUSTOM_TAG_NAME = 'ddg-video-overlay';
        /**
         * @param {object} options
         * @param {import("../overlays.js").Environment} options.environment
         * @param {import("../util").VideoParams} options.params
         * @param {import("../../duck-player.js").UISettings} options.ui
         * @param {VideoOverlay} options.manager
         */
        constructor({ environment, params, ui, manager }) {
            super();
            if (!(manager instanceof VideoOverlay)) throw new Error('invalid arguments');
            this.environment = environment;
            this.ui = ui;
            this.params = params;
            this.manager = manager;

            /**
             * Create the shadow root, closed to prevent any outside observers
             * @type {ShadowRoot}
             */
            const shadow = this.attachShadow({ mode: this.environment.isTestMode() ? 'open' : 'closed' });

            /**
             * Add our styles
             * @type {HTMLStyleElement}
             */
            const style = document.createElement('style');
            style.innerText = css;

            /**
             * Create the overlay
             * @type {HTMLDivElement}
             */
            const overlay = this.createOverlay();

            /**
             * Append both to the shadow root
             */
            shadow.appendChild(overlay);
            shadow.appendChild(style);
        }

        /**
         * @returns {HTMLDivElement}
         */
        createOverlay() {
            const overlayCopy = overlayCopyVariants.default;
            const overlayElement = document.createElement('div');
            overlayElement.classList.add('ddg-video-player-overlay');
            const svgIcon = trustedUnsafe(dax);
            const safeString = html`
            <div class="ddg-vpo-bg"></div>
            <div class="ddg-vpo-content">
                <div class="ddg-eyeball">${svgIcon}</div>
                <div class="ddg-vpo-title">${overlayCopy.title}</div>
                <div class="ddg-vpo-text">${overlayCopy.subtitle}</div>
                <div class="ddg-vpo-buttons">
                    <button class="ddg-vpo-button ddg-vpo-cancel" type="button">${overlayCopy.buttonOptOut}</button>
                    <a class="ddg-vpo-button ddg-vpo-open" href="#">${overlayCopy.buttonOpen}</a>
                </div>
                <div class="ddg-vpo-remember">
                    <label for="remember"> <input id="remember" type="checkbox" name="ddg-remember" /> ${overlayCopy.rememberLabel} </label>
                </div>
            </div>
        `.toString();

            overlayElement.innerHTML = this.policy.createHTML(safeString);

            /**
             * Set the link
             * @type {string}
             */
            const href = this.params.toPrivatePlayerUrl();
            overlayElement.querySelector('.ddg-vpo-open')?.setAttribute('href', href);

            /**
             * Add thumbnail
             */
            this.appendThumbnail(overlayElement, this.params.id);

            /**
             * Setup the click handlers
             */
            this.setupButtonsInsideOverlay(overlayElement, this.params);

            return overlayElement;
        }

        /**
         * @param {HTMLElement} overlayElement
         * @param {string} videoId
         */
        appendThumbnail(overlayElement, videoId) {
            const imageUrl = this.environment.getLargeThumbnailSrc(videoId);
            appendImageAsBackground(overlayElement, '.ddg-vpo-bg', imageUrl);
        }

        /**
         * @param {HTMLElement} containerElement
         * @param {import("../util").VideoParams} params
         */
        setupButtonsInsideOverlay(containerElement, params) {
            const cancelElement = containerElement.querySelector('.ddg-vpo-cancel');
            const watchInPlayer = containerElement.querySelector('.ddg-vpo-open');
            if (!cancelElement) return console.warn('Could not access .ddg-vpo-cancel');
            if (!watchInPlayer) return console.warn('Could not access .ddg-vpo-open');
            const optOutHandler = (e) => {
                if (e.isTrusted) {
                    const remember = containerElement.querySelector('input[name="ddg-remember"]');
                    if (!(remember instanceof HTMLInputElement)) throw new Error('cannot find our input');
                    this.manager.userOptOut(remember.checked, params);
                }
            };
            const watchInPlayerHandler = (e) => {
                if (e.isTrusted) {
                    e.preventDefault();
                    const remember = containerElement.querySelector('input[name="ddg-remember"]');
                    if (!(remember instanceof HTMLInputElement)) throw new Error('cannot find our input');
                    this.manager.userOptIn(remember.checked, params);
                }
            };
            cancelElement.addEventListener('click', optOutHandler);
            watchInPlayer.addEventListener('click', watchInPlayerHandler);
        }
    }

    var mobilecss = "/* -- VIDEO PLAYER OVERLAY */\n:host {\n    position: absolute;\n    top: 0;\n    left: 0;\n    right: 0;\n    bottom: 0;\n    color: white;\n    z-index: 10000;\n    --title-size: 16px;\n    --title-line-height: 20px;\n    --title-gap: 16px;\n    --button-gap: 6px;\n    --logo-size: 32px;\n    --logo-gap: 8px;\n    --gutter: 16px;\n\n}\n/* iphone 15 */\n@media screen and (min-width: 390px) {\n    :host {\n        --title-size: 20px;\n        --title-line-height: 25px;\n        --button-gap: 16px;\n        --logo-size: 40px;\n        --logo-gap: 12px;\n        --title-gap: 16px;\n    }\n}\n/* iphone 15 Pro Max */\n@media screen and (min-width: 430px) {\n    :host {\n        --title-size: 22px;\n        --title-gap: 24px;\n        --button-gap: 20px;\n        --logo-gap: 16px;\n    }\n}\n/* small landscape */\n@media screen and (min-width: 568px) {\n}\n/* large landscape */\n@media screen and (min-width: 844px) {\n    :host {\n        --title-gap: 30px;\n        --button-gap: 24px;\n        --logo-size: 48px;\n    }\n}\n\n\n:host * {\n    font-family: system, -apple-system, system-ui, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif, \"Apple Color Emoji\", \"Segoe UI Emoji\", \"Segoe UI Symbol\";\n}\n\n:root *, :root *:after, :root *:before {\n    box-sizing: border-box;\n}\n\n.ddg-video-player-overlay {\n    position: absolute;\n    top: 0;\n    left: 0;\n    right: 0;\n    bottom: 0;\n    color: white;\n    z-index: 10000;\n    padding-left: var(--gutter);\n    padding-right: var(--gutter);\n\n    @media screen and (min-width: 568px) {\n        padding: 0;\n    }\n}\n\n.bg {\n    position: absolute;\n    top: 0;\n    left: 0;\n    right: 0;\n    bottom: 0;\n    color: white;\n    background: rgba(0, 0, 0, 0.6);\n    text-align: center;\n}\n\n.bg:before {\n    content: \" \";\n    position: absolute;\n    display: block;\n    width: 100%;\n    height: 100%;\n    top: 0;\n    left: 0;\n    right: 0;\n    bottom: 0;\n    background:\n            linear-gradient(180deg, rgba(0, 0, 0, 1) 0%, rgba(0, 0, 0, 0.5) 40%, rgba(0, 0, 0, 0) 60%),\n            radial-gradient(circle at bottom, rgba(131, 58, 180, 0.8), rgba(253, 29, 29, 0.6), rgba(252, 176, 69, 0.4));\n}\n\n.bg:after {\n    content: \" \";\n    position: absolute;\n    display: block;\n    width: 100%;\n    height: 100%;\n    top: 0;\n    left: 0;\n    right: 0;\n    bottom: 0;\n    background: rgba(0,0,0,0.7);\n    text-align: center;\n}\n\n.content {\n    height: 100%;\n    width: 100%;\n    margin: 0 auto;\n    overflow: hidden;\n    display: grid;\n    color: rgba(255, 255, 255, 0.96);\n    position: relative;\n    grid-column-gap: var(--logo-gap);\n    grid-template-columns: var(--logo-size) auto calc(12px + 16px);\n    grid-template-rows:\n            auto\n            var(--title-gap)\n            auto\n            var(--button-gap)\n            auto;\n    align-content: center;\n    justify-content: center;\n\n    @media screen and (min-width: 568px) {\n        grid-template-columns: var(--logo-size) auto auto;\n    }\n}\n\n.logo {\n    align-self: start;\n    grid-column: 1/2;\n    grid-row: 1/2;\n}\n\n.logo svg {\n    width: 100%;\n    height: 100%;\n}\n\n.arrow {\n    position: absolute;\n    top: 48px;\n    left: -18px;\n    color: white;\n    z-index: 0;\n}\n\n.title {\n    font-size: var(--title-size);\n    line-height: var(--title-line-height);\n    font-weight: 600;\n    grid-column: 2/3;\n    grid-row: 1/2;\n\n    @media screen and (min-width: 568px) {\n        grid-column: 2/4;\n        max-width: 428px;\n    }\n}\n\n.text {\n    display: none;\n}\n\n.info {\n    grid-column: 3/4;\n    grid-row: 1/2;\n    align-self: start;\n    padding-top: 3px;\n    justify-self: end;\n\n    @media screen and (min-width: 568px) {\n        grid-column: unset;\n        grid-row: unset;\n        position: absolute;\n        top: 12px;\n        right: 12px;\n    }\n    @media screen and (min-width: 844px) {\n        top: 24px;\n        right: 24px;\n    }\n}\n\n.buttons {\n    gap: 8px;\n    display: flex;\n    grid-column: 1/4;\n    grid-row: 3/4;\n\n    @media screen and (min-width: 568px) {\n        grid-column: 2/3;\n    }\n}\n\n.remember {\n    height: 40px;\n    border-radius: 8px;\n    display: flex;\n    gap: 16px;\n    align-items: center;\n    justify-content: space-between;\n    padding-left: 8px;\n    padding-right: 8px;\n    grid-column: 1/4;\n    grid-row: 5/6;\n\n    @media screen and (min-width: 568px) {\n        grid-column: 2/3;\n    }\n}\n\n.button {\n    margin: 0;\n    -webkit-appearance: none;\n    background: none;\n    box-shadow: none;\n    border: none;\n    display: flex;\n    align-items: center;\n    justify-content: center;\n    color: rgba(255, 255, 255, 1);\n    text-decoration: none;\n    line-height: 16px;\n    padding: 0 12px;\n    font-size: 15px;\n    font-weight: 600;\n    border-radius: 8px;\n}\n\n.button--info {\n    display: block;\n    padding: 0;\n    margin: 0;\n    width: 16px;\n    height: 16px;\n    @media screen and (min-width: 568px) {\n        width: 24px;\n        height: 24px;\n    }\n    @media screen and (min-width: 844px) {\n        width: 24px;\n        height: 24px;\n    }\n}\n.button--info svg {\n    display: block;\n    width: 100%;\n    height: 100%;\n}\n\n.button--info svg path {\n    fill: rgba(255, 255, 255, 0.84);\n}\n\n.cancel {\n    background: rgba(255, 255, 255, 0.3);\n    min-height: 40px;\n}\n\n.open {\n    background: #3969EF;\n    flex: 1;\n    text-align: center;\n    min-height: 40px;\n\n    @media screen and (min-width: 568px) {\n        flex: inherit;\n        padding-left: 24px;\n        padding-right: 24px;\n    }\n}\n\n.open:hover {\n}\n.cancel:hover {\n}\n\n.remember-label {\n    display: flex;\n    align-items: center;\n    flex: 1;\n}\n\n.remember-text {\n    display: block;\n    font-size: 13px;\n    font-weight: 400;\n}\n.remember-checkbox {\n    margin-left: auto;\n    display: flex;\n}\n\n.switch {\n    margin: 0;\n    padding: 0;\n    width: 52px;\n    height: 32px;\n    border: 0;\n    box-shadow: none;\n    background: rgba(136, 136, 136, 0.5);\n    border-radius: 32px;\n    position: relative;\n    transition: all .3s;\n}\n\n.switch:active .thumb {\n    scale: 1.15;\n}\n\n.thumb {\n    width: 20px;\n    height: 20px;\n    border-radius: 100%;\n    background: white;\n    position: absolute;\n    top: 4px;\n    left: 4px;\n    pointer-events: none;\n    transition: .2s left ease-in-out;\n}\n\n.switch[aria-checked=\"true\"] {\n    background: rgba(57, 105, 239, 1)\n}\n\n.ios-switch {\n    width: 42px;\n    height: 24px;\n}\n\n.ios-switch .thumb {\n    top: 2px;\n    left: 2px;\n    width: 20px;\n    height: 20px;\n    box-shadow: 0 1px 4px 0 rgba(0, 0, 0, 0.25)\n}\n\n.ios-switch:active .thumb {\n    scale: 1;\n}\n\n.ios-switch[aria-checked=\"true\"] .thumb {\n    left: calc(100% - 22px)\n}\n\n.android {}\n";

    var info = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"25\" viewBox=\"0 0 24 25\" fill=\"none\">\n    <path d=\"M12.7248 5.96753C11.6093 5.96753 10.9312 6.86431 10.9312 7.69548C10.9312 8.70163 11.6968 9.02972 12.3748 9.02972C13.6216 9.02972 14.1465 8.08919 14.1465 7.32364C14.1465 6.36124 13.381 5.96753 12.7248 5.96753Z\" fill=\"white\" fill-opacity=\"0.84\"/>\n    <path d=\"M13.3696 10.3183L10.6297 10.7613C10.5458 11.4244 10.4252 12.0951 10.3026 12.7763C10.0661 14.0912 9.82251 15.4455 9.82251 16.8607C9.82251 18.2659 10.6629 19.0328 11.9918 19.0328C13.5096 19.0328 13.7693 18.0801 13.8282 17.2171C12.57 17.3996 12.2936 16.8317 12.4992 15.495C12.7049 14.1584 13.3696 10.3183 13.3696 10.3183Z\" fill=\"white\" fill-opacity=\"0.84\"/>\n    <path fill-rule=\"evenodd\" clip-rule=\"evenodd\" d=\"M12 0.5C5.37258 0.5 0 5.87258 0 12.5C0 19.1274 5.37258 24.5 12 24.5C18.6274 24.5 24 19.1274 24 12.5C24 5.87258 18.6274 0.5 12 0.5ZM2.25 12.5C2.25 7.11522 6.61522 2.75 12 2.75C17.3848 2.75 21.75 7.11522 21.75 12.5C21.75 17.8848 17.3848 22.25 12 22.25C6.61522 22.25 2.25 17.8848 2.25 12.5Z\" fill=\"white\" fill-opacity=\"0.84\"/>\n</svg>\n";

    /**
     * @typedef {ReturnType<import("../text").overlayCopyVariants>} TextVariants
     * @typedef {TextVariants[keyof TextVariants]} Text
     */

    /**
     * The custom element that we use to present our UI elements
     * over the YouTube player
     */
    class DDGVideoOverlayMobile extends HTMLElement {
        static CUSTOM_TAG_NAME = 'ddg-video-overlay-mobile';
        static OPEN_INFO = 'open-info';
        static OPT_IN = 'opt-in';
        static OPT_OUT = 'opt-out';

        policy = createPolicy();
        /** @type {boolean} */
        testMode = false;
        /** @type {Text | null} */
        text = null;

        connectedCallback() {
            this.createMarkupAndStyles();
        }

        createMarkupAndStyles() {
            const shadow = this.attachShadow({ mode: this.testMode ? 'open' : 'closed' });
            const style = document.createElement('style');
            style.innerText = mobilecss;
            const overlayElement = document.createElement('div');
            const content = this.mobileHtml();
            overlayElement.innerHTML = this.policy.createHTML(content);
            shadow.append(style, overlayElement);
            this.setupEventHandlers(overlayElement);
        }

        /**
         * @returns {string}
         */
        mobileHtml() {
            if (!this.text) {
                console.warn('missing `text`. Please assign before rendering');
                return '';
            }
            const svgIcon = trustedUnsafe(dax);
            const infoIcon = trustedUnsafe(info);
            return html`
            <div class="ddg-video-player-overlay">
                <div class="bg ddg-vpo-bg"></div>
                <div class="content ios">
                    <div class="logo">${svgIcon}</div>
                    <div class="title">${this.text.title}</div>
                    <div class="info">
                        <button class="button button--info" type="button" aria-label="Open Information Modal">${infoIcon}</button>
                    </div>
                    <div class="text">${this.text.subtitle}</div>
                    <div class="buttons">
                        <button class="button cancel ddg-vpo-cancel" type="button">${this.text.buttonOptOut}</button>
                        <a class="button open ddg-vpo-open" href="#">${this.text.buttonOpen}</a>
                    </div>
                    <div class="remember">
                        <div class="remember-label">
                            <span class="remember-text"> ${this.text.rememberLabel} </span>
                            <span class="remember-checkbox">
                                <input id="remember" type="checkbox" name="ddg-remember" hidden />
                                <button role="switch" aria-checked="false" class="switch ios-switch">
                                    <span class="thumb"></span>
                                </button>
                            </span>
                        </div>
                    </div>
                </div>
            </div>
        `.toString();
        }

        /**
         * @param {HTMLElement} containerElement
         */
        setupEventHandlers(containerElement) {
            const switchElem = containerElement.querySelector('[role=switch]');
            const infoButton = containerElement.querySelector('.button--info');
            const remember = containerElement.querySelector('input[name="ddg-remember"]');
            const cancelElement = containerElement.querySelector('.ddg-vpo-cancel');
            const watchInPlayer = containerElement.querySelector('.ddg-vpo-open');

            if (!infoButton || !cancelElement || !watchInPlayer || !switchElem || !(remember instanceof HTMLInputElement)) {
                return console.warn('missing elements');
            }

            infoButton.addEventListener('click', () => {
                this.dispatchEvent(new Event(DDGVideoOverlayMobile.OPEN_INFO));
            });

            switchElem.addEventListener('pointerdown', () => {
                const current = switchElem.getAttribute('aria-checked');
                if (current === 'false') {
                    switchElem.setAttribute('aria-checked', 'true');
                    remember.checked = true;
                } else {
                    switchElem.setAttribute('aria-checked', 'false');
                    remember.checked = false;
                }
            });

            cancelElement.addEventListener('click', (e) => {
                if (!e.isTrusted) return;
                e.preventDefault();
                e.stopImmediatePropagation();
                this.dispatchEvent(new CustomEvent(DDGVideoOverlayMobile.OPT_OUT, { detail: { remember: remember.checked } }));
            });

            watchInPlayer.addEventListener('click', (e) => {
                if (!e.isTrusted) return;
                e.preventDefault();
                e.stopImmediatePropagation();
                this.dispatchEvent(new CustomEvent(DDGVideoOverlayMobile.OPT_IN, { detail: { remember: remember.checked } }));
            });
        }
    }

    /* eslint-disable promise/prefer-await-to-then */
    /**
     * @module Duck Player Video Overlay
     *
     * ## Decision flow for appending the Video Overlays
     *
     * We'll try to append the full video overlay (or small Dax icon) onto the main video player
     * if the following conditions are met:
     *
     * 1. User has Duck Player configured to 'always ask' (the default)
     * 2. `videoOverlays` is enabled in the remote config
     *
     * If those are both met, the following steps occur on *first page load*:
     *
     * - let `href` be the current `window.location.href` value
     * - *exit to polling step* if `href` is not a valid watchPage
     * - when `href` is a valid watch page, then:
     *   - append CSS to the HEAD to avoid the main player showing
     *   - in a loop (every 100ms), continuously check if the video element has appeared
     * - when the video is showing:
     *   - if the user has duck player set to 'enabled', then:
     *     - show the small dax overlay
     * - if the user has duck player set to 'always ask', then:
     *   - if there's a one-time override (eg: from the serp), then exit to polling
     *   - if the user previously clicked 'watch here + remember', just add the small dax
     *   - otherwise, stop the video playing + append our overlay
     */

    /**
     * Handle the switch between small & large overlays
     * + conduct any communications
     */
    class VideoOverlay {
        sideEffects = new SideEffects();

        /** @type {string | null} */
        lastVideoId = null;

        /** @type {boolean} */
        didAllowFirstVideo = false;

        /**
         * @param {object} options
         * @param {import("../duck-player.js").UserValues} options.userValues
         * @param {import("../duck-player.js").OverlaysFeatureSettings} options.settings
         * @param {import("./overlays.js").Environment} options.environment
         * @param {import("./overlay-messages.js").DuckPlayerOverlayMessages} options.messages
         * @param {import("../duck-player.js").UISettings} options.ui
         */
        constructor({ userValues, settings, environment, messages, ui }) {
            this.userValues = userValues;
            this.settings = settings;
            this.environment = environment;
            this.messages = messages;
            this.ui = ui;
        }

        /**
         * @param {'page-load' | 'preferences-changed' | 'href-changed'} trigger
         */
        init(trigger) {
            if (trigger === 'page-load') {
                this.handleFirstPageLoad();
            } else if (trigger === 'preferences-changed') {
                this.watchForVideoBeingAdded({ via: 'user notification', ignoreCache: true });
            } else if (trigger === 'href-changed') {
                this.watchForVideoBeingAdded({ via: 'href changed' });
            }
        }

        /**
         * Special handling of a first-page, an attempt to load our overlay as quickly as possible
         */
        handleFirstPageLoad() {
            // don't continue unless we're in 'alwaysAsk' mode
            if ('disabled' in this.userValues.privatePlayerMode) return;

            // don't continue if we can't derive valid video params
            const validParams = VideoParams.forWatchPage(this.environment.getPlayerPageHref());
            if (!validParams) return;

            /**
             * If we get here, we know the following:
             *
             * 1) we're going to show the overlay because of user settings/state
             * 2) we're on a valid `/watch` page
             * 3) we have at _least_ a valid video id
             *
             * So, in that case we append some css quickly to the head to ensure player items are not showing
             * Later, when our overlay loads that CSS will be removed in the cleanup.
             */
            this.sideEffects.add('add css to head', () => {
                const style = document.createElement('style');
                style.innerText = this.settings.selectors.videoElementContainer + ' { opacity: 0!important }';
                if (document.head) {
                    document.head.appendChild(style);
                }
                return () => {
                    if (style.isConnected) {
                        document.head.removeChild(style);
                    }
                };
            });

            /**
             * Keep trying to find the video element every 100 ms
             */
            this.sideEffects.add('wait for first video element', () => {
                const int = setInterval(() => {
                    this.watchForVideoBeingAdded({ via: 'first page load' });
                }, 100);
                return () => {
                    clearInterval(int);
                };
            });
        }

        /**
         * @param {import("./util").VideoParams} params
         */
        addSmallDaxOverlay(params) {
            const containerElement = document.querySelector(this.settings.selectors.videoElementContainer);
            if (!containerElement || !(containerElement instanceof HTMLElement)) {
                console.error('no container element');
                return;
            }
            this.sideEffects.add('adding small dax 🐥 icon overlay', () => {
                const href = params.toPrivatePlayerUrl();

                const icon = new IconOverlay();

                icon.appendSmallVideoOverlay(containerElement, href, (href) => {
                    this.messages.openDuckPlayer(new OpenInDuckPlayerMsg({ href }));
                });

                return () => {
                    icon.destroy();
                };
            });
        }

        /**
         * @param {{ignoreCache?: boolean, via?: string}} [opts]
         */
        watchForVideoBeingAdded(opts = {}) {
            const params = VideoParams.forWatchPage(this.environment.getPlayerPageHref());

            if (!params) {
                /**
                 * If we've shown a video before, but now we don't have a valid ID,
                 * it's likely a 'back' navigation by the user, so we should always try to remove all overlays
                 */
                if (this.lastVideoId) {
                    this.destroy();
                    this.lastVideoId = null;
                }
                return;
            }

            const conditions = [
                // cache overridden
                opts.ignoreCache,
                // first visit
                !this.lastVideoId,
                // new video id
                this.lastVideoId && this.lastVideoId !== params.id, // different
            ];

            if (conditions.some(Boolean)) {
                /**
                 * Don't continue until we've been able to find the HTML elements that we inject into
                 */
                const videoElement = document.querySelector(this.settings.selectors.videoElement);
                const playerContainer = document.querySelector(this.settings.selectors.videoElementContainer);
                if (!videoElement || !playerContainer) {
                    return null;
                }

                /**
                 * If we get here, it's a valid situation
                 */
                const userValues = this.userValues;
                this.lastVideoId = params.id;

                /**
                 * always remove everything first, to prevent any lingering state
                 */
                this.destroy();

                /**
                 * When enabled, just show the small dax icon
                 */
                if ('enabled' in userValues.privatePlayerMode) {
                    return this.addSmallDaxOverlay(params);
                }

                if ('alwaysAsk' in userValues.privatePlayerMode) {
                    // if there's a one-time-override (eg: a link from the serp), then do nothing
                    if (this.environment.hasOneTimeOverride()) return;

                    // should the first video be allowed to play?
                    if (this.ui.allowFirstVideo === true && !this.didAllowFirstVideo) {
                        this.didAllowFirstVideo = true;
                        return console.count('Allowing the first video');
                    }

                    // if the user previously clicked 'watch here + remember', just add the small dax
                    if (this.userValues.overlayInteracted) {
                        return this.addSmallDaxOverlay(params);
                    }

                    // if we get here, we're trying to prevent the video playing
                    this.stopVideoFromPlaying();
                    this.appendOverlayToPage(playerContainer, params);
                }
            }
        }

        /**
         * @param {Element} targetElement
         * @param {import("./util").VideoParams} params
         */
        appendOverlayToPage(targetElement, params) {
            this.sideEffects.add(`appending ${DDGVideoOverlay.CUSTOM_TAG_NAME} or ${DDGVideoOverlayMobile.CUSTOM_TAG_NAME} to the page`, () => {
                this.messages.sendPixel(new Pixel({ name: 'overlay' }));
                const controller = new AbortController();
                const { environment } = this;

                if (this.environment.layout === 'mobile') {
                    const elem = /** @type {DDGVideoOverlayMobile} */ (document.createElement(DDGVideoOverlayMobile.CUSTOM_TAG_NAME));
                    elem.testMode = this.environment.isTestMode();
                    elem.text = mobileStrings(this.environment.strings);
                    elem.addEventListener(DDGVideoOverlayMobile.OPEN_INFO, () => this.messages.openInfo());
                    elem.addEventListener(DDGVideoOverlayMobile.OPT_OUT, (/** @type {CustomEvent<{remember: boolean}>} */ e) => {
                        return this.mobileOptOut(e.detail.remember).catch(console.error);
                    });
                    elem.addEventListener(DDGVideoOverlayMobile.OPT_IN, (/** @type {CustomEvent<{remember: boolean}>} */ e) => {
                        return this.mobileOptIn(e.detail.remember, params).catch(console.error);
                    });
                    targetElement.appendChild(elem);
                } else {
                    const elem = new DDGVideoOverlay({
                        environment,
                        params,
                        ui: this.ui,
                        manager: this,
                    });
                    targetElement.appendChild(elem);
                }

                /**
                 * To cleanup just find and remove the element
                 */
                return () => {
                    document.querySelector(DDGVideoOverlay.CUSTOM_TAG_NAME)?.remove();
                    document.querySelector(DDGVideoOverlayMobile.CUSTOM_TAG_NAME)?.remove();
                    controller.abort();
                };
            });
        }

        /**
         * Just brute-force calling video.pause() for as long as the user is seeing the overlay.
         */
        stopVideoFromPlaying() {
            this.sideEffects.add(`pausing the <video> element with selector '${this.settings.selectors.videoElement}'`, () => {
                /**
                 * Set up the interval - keep calling .pause() to prevent
                 * the video from playing
                 */
                const int = setInterval(() => {
                    const video = /** @type {HTMLVideoElement} */ (document.querySelector(this.settings.selectors.videoElement));
                    if (video?.isConnected) {
                        video.pause();
                    }
                }, 10);

                /**
                 * To clean up, we need to stop the interval
                 * and then call .play() on the original element, if it's still connected
                 */
                return () => {
                    clearInterval(int);

                    const video = /** @type {HTMLVideoElement} */ (document.querySelector(this.settings.selectors.videoElement));
                    if (video?.isConnected) {
                        video.play();
                    }
                };
            });
        }

        /**
         * If the checkbox was checked, this action means that we want to 'always'
         * use the private player
         *
         * But, if the checkbox was not checked, then we want to keep the state
         * as 'alwaysAsk'
         *
         * @param {boolean} remember
         * @param {VideoParams} params
         */
        userOptIn(remember, params) {
            /** @type {import("../duck-player.js").UserValues['privatePlayerMode']} */
            let privatePlayerMode = { alwaysAsk: {} };
            if (remember) {
                this.messages.sendPixel(new Pixel({ name: 'play.use', remember: '1' }));
                privatePlayerMode = { enabled: {} };
            } else {
                this.messages.sendPixel(new Pixel({ name: 'play.use', remember: '0' }));
                // do nothing. The checkbox was off meaning we don't want to save any choice
            }
            const outgoing = {
                overlayInteracted: false,
                privatePlayerMode,
            };
            this.messages
                .setUserValues(outgoing)
                .then(() => {
                    if (this.environment.opensVideoOverlayLinksViaMessage) {
                        return this.messages.openDuckPlayer(new OpenInDuckPlayerMsg({ href: params.toPrivatePlayerUrl() }));
                    }
                    return this.environment.setHref(params.toPrivatePlayerUrl());
                })
                .catch((e) => console.error('error setting user choice', e));
        }

        /**
         * @param {boolean} remember
         * @param {import("./util").VideoParams} params
         */
        userOptOut(remember, params) {
            /**
             * If the checkbox was checked we send the 'interacted' flag to the backend
             * so that the next video can just see the Dax icon instead of the full overlay
             *
             * But, if the checkbox was **not** checked, then we don't update any backend state
             * and instead we just swap the main overlay for Dax
             */
            if (remember) {
                this.messages.sendPixel(new Pixel({ name: 'play.do_not_use', remember: '1' }));
                /** @type {import("../duck-player.js").UserValues['privatePlayerMode']} */
                const privatePlayerMode = { alwaysAsk: {} };
                this.messages
                    .setUserValues({
                        privatePlayerMode,
                        overlayInteracted: true,
                    })
                    .then((values) => {
                        this.userValues = values;
                    })
                    .then(() => this.watchForVideoBeingAdded({ ignoreCache: true, via: 'userOptOut' }))
                    .catch((e) => console.error('could not set userChoice for opt-out', e));
            } else {
                this.messages.sendPixel(new Pixel({ name: 'play.do_not_use', remember: '0' }));
                this.destroy();
                this.addSmallDaxOverlay(params);
            }
        }

        /**
         * @param {boolean} remember
         * @param {import("./util").VideoParams} params
         */
        async mobileOptIn(remember, params) {
            const pixel = remember ? new Pixel({ name: 'play.use', remember: '1' }) : new Pixel({ name: 'play.use', remember: '0' });

            this.messages.sendPixel(pixel);

            /** @type {import("../duck-player.js").UserValues} */
            const outgoing = {
                overlayInteracted: false,
                privatePlayerMode: remember ? { enabled: {} } : { alwaysAsk: {} },
            };

            const result = await this.messages.setUserValues(outgoing);

            if (this.environment.debug) {
                console.log('did receive new values', result);
            }

            return this.messages.openDuckPlayer(new OpenInDuckPlayerMsg({ href: params.toPrivatePlayerUrl() }));
        }

        /**
         * @param {boolean} remember
         */
        async mobileOptOut(remember) {
            const pixel = remember
                ? new Pixel({ name: 'play.do_not_use', remember: '1' })
                : new Pixel({ name: 'play.do_not_use', remember: '0' });

            this.messages.sendPixel(pixel);

            if (!remember) {
                return this.destroy();
            }

            /** @type {import("../duck-player.js").UserValues} */
            const next = {
                privatePlayerMode: { disabled: {} },
                overlayInteracted: false,
            };

            if (this.environment.debug) {
                console.log('sending user values:', next);
            }

            const updatedValues = await this.messages.setUserValues(next);

            // this is needed to ensure any future page navigations respect the new settings
            this.userValues = updatedValues;

            if (this.environment.debug) {
                console.log('user values response:', updatedValues);
            }

            this.destroy();
        }

        /**
         * Remove elements, event listeners etc
         */
        destroy() {
            this.sideEffects.destroy();
        }
    }

    /**
     * Register custom elements in this wrapper function to be called only when we need to
     * and also to allow remote-config later if needed.
     *
     */
    function registerCustomElements() {
        if (!customElementsGet(DDGVideoOverlay.CUSTOM_TAG_NAME)) {
            customElementsDefine(DDGVideoOverlay.CUSTOM_TAG_NAME, DDGVideoOverlay);
        }
        if (!customElementsGet(DDGVideoOverlayMobile.CUSTOM_TAG_NAME)) {
            customElementsDefine(DDGVideoOverlayMobile.CUSTOM_TAG_NAME, DDGVideoOverlayMobile);
        }
    }

    var strings = `{"bg":{"overlays.json":{"videoOverlayTitle2":"Включете Duck Player, за да гледате без насочени реклами","videoButtonOpen2":"Включване на Duck Player","videoButtonOptOut2":"Не, благодаря","rememberLabel":"Запомни моя избор"}},"cs":{"overlays.json":{"videoOverlayTitle2":"Zapněte si Duck Player a sledujte videa bez cílených reklam","videoButtonOpen2":"Zapni si Duck Player","videoButtonOptOut2":"Ne, děkuji","rememberLabel":"Zapamatovat mou volbu"}},"da":{"overlays.json":{"videoOverlayTitle2":"Slå Duck Player til for at se indhold uden målrettede reklamer","videoButtonOpen2":"Slå Duck Player til","videoButtonOptOut2":"Nej tak.","rememberLabel":"Husk mit valg"}},"de":{"overlays.json":{"videoOverlayTitle2":"Aktiviere den Duck Player, um ohne gezielte Werbung zu schauen","videoButtonOpen2":"Duck Player aktivieren","videoButtonOptOut2":"Nein, danke","rememberLabel":"Meine Auswahl merken"}},"el":{"overlays.json":{"videoOverlayTitle2":"Ενεργοποιήστε το Duck Player για παρακολούθηση χωρίς στοχευμένες διαφημίσεις","videoButtonOpen2":"Ενεργοποίηση του Duck Player","videoButtonOptOut2":"Όχι, ευχαριστώ","rememberLabel":"Θυμηθείτε την επιλογή μου"}},"en":{"overlays.json":{"videoOverlayTitle2":"Turn on Duck Player to watch without targeted ads","videoButtonOpen2":"Turn On Duck Player","videoButtonOptOut2":"No Thanks","rememberLabel":"Remember my choice"}},"es":{"overlays.json":{"videoOverlayTitle2":"Activa Duck Player para ver sin anuncios personalizados","videoButtonOpen2":"Activar Duck Player","videoButtonOptOut2":"No, gracias","rememberLabel":"Recordar mi elección"}},"et":{"overlays.json":{"videoOverlayTitle2":"Sihitud reklaamideta vaatamiseks lülita sisse Duck Player","videoButtonOpen2":"Lülita Duck Player sisse","videoButtonOptOut2":"Ei aitäh","rememberLabel":"Jäta mu valik meelde"}},"fi":{"overlays.json":{"videoOverlayTitle2":"Jos haluat katsoa ilman kohdennettuja mainoksia, ota Duck Player käyttöön","videoButtonOpen2":"Ota Duck Player käyttöön","videoButtonOptOut2":"Ei kiitos","rememberLabel":"Muista valintani"}},"fr":{"overlays.json":{"videoOverlayTitle2":"Activez Duck Player pour une vidéo sans publicités ciblées","videoButtonOpen2":"Activez Duck Player","videoButtonOptOut2":"Non merci","rememberLabel":"Mémoriser mon choix"}},"hr":{"overlays.json":{"videoOverlayTitle2":"Uključi Duck Player za gledanje bez ciljanih oglasa","videoButtonOpen2":"Uključi Duck Player","videoButtonOptOut2":"Ne, hvala","rememberLabel":"Zapamti moj izbor"}},"hu":{"overlays.json":{"videoOverlayTitle2":"Kapcsold be a Duck Playert, hogy célzott hirdetések nélkül videózhass","videoButtonOpen2":"Duck Player bekapcsolása","videoButtonOptOut2":"Nem, köszönöm","rememberLabel":"Választott beállítás megjegyzése"}},"it":{"overlays.json":{"videoOverlayTitle2":"Attiva Duck Player per guardare senza annunci personalizzati","videoButtonOpen2":"Attiva Duck Player","videoButtonOptOut2":"No, grazie","rememberLabel":"Ricorda la mia scelta"}},"lt":{"overlays.json":{"videoOverlayTitle2":"Įjunkite „Duck Player“, kad galėtumėte žiūrėti be tikslinių reklamų","videoButtonOpen2":"Įjunkite „Duck Player“","videoButtonOptOut2":"Ne, dėkoju","rememberLabel":"Įsiminti mano pasirinkimą"}},"lv":{"overlays.json":{"videoOverlayTitle2":"Ieslēdz Duck Player, lai skatītos bez mērķētām reklāmām","videoButtonOpen2":"Ieslēgt Duck Player","videoButtonOptOut2":"Nē, paldies","rememberLabel":"Atcerēties manu izvēli"}},"nb":{"overlays.json":{"videoOverlayTitle2":"Slå på Duck Player for å se på uten målrettede annonser","videoButtonOpen2":"Slå på Duck Player","videoButtonOptOut2":"Nei takk","rememberLabel":"Husk valget mitt"}},"nl":{"overlays.json":{"videoOverlayTitle2":"Zet Duck Player aan om te kijken zonder gerichte advertenties","videoButtonOpen2":"Duck Player aanzetten","videoButtonOptOut2":"Nee, bedankt","rememberLabel":"Mijn keuze onthouden"}},"pl":{"overlays.json":{"videoOverlayTitle2":"Włącz Duck Player, aby oglądać bez reklam ukierunkowanych","videoButtonOpen2":"Włącz Duck Player","videoButtonOptOut2":"Nie, dziękuję","rememberLabel":"Zapamiętaj mój wybór"}},"pt":{"overlays.json":{"videoOverlayTitle2":"Ativa o Duck Player para ver sem anúncios personalizados","videoButtonOpen2":"Ligar o Duck Player","videoButtonOptOut2":"Não, obrigado","rememberLabel":"Memorizar a minha opção"}},"ro":{"overlays.json":{"videoOverlayTitle2":"Activează Duck Player pentru a viziona fără reclame direcționate","videoButtonOpen2":"Activează Duck Player","videoButtonOptOut2":"Nu, mulțumesc","rememberLabel":"Reține alegerea mea"}},"ru":{"overlays.json":{"videoOverlayTitle2":"Duck Player — просмотр без целевой рекламы","videoButtonOpen2":"Включить Duck Player","videoButtonOptOut2":"Нет, спасибо","rememberLabel":"Запомнить выбор"}},"sk":{"overlays.json":{"videoOverlayTitle2":"Zapnite Duck Player a pozerajte bez cielených reklám","videoButtonOpen2":"Zapnúť prehrávač Duck Player","videoButtonOptOut2":"Nie, ďakujem","rememberLabel":"Zapamätať si moju voľbu"}},"sl":{"overlays.json":{"videoOverlayTitle2":"Vklopite predvajalnik Duck Player za gledanje brez ciljanih oglasov","videoButtonOpen2":"Vklopi predvajalnik Duck Player","videoButtonOptOut2":"Ne, hvala","rememberLabel":"Zapomni si mojo izbiro"}},"sv":{"overlays.json":{"videoOverlayTitle2":"Aktivera Duck Player för att titta utan riktade annonser","videoButtonOpen2":"Aktivera Duck Player","videoButtonOptOut2":"Nej tack","rememberLabel":"Kom ihåg mitt val"}},"tr":{"overlays.json":{"videoOverlayTitle2":"Hedeflenmiş reklamlar olmadan izlemek için Duck Player'ı açın","videoButtonOpen2":"Duck Player'ı Aç","videoButtonOptOut2":"Hayır Teşekkürler","rememberLabel":"Seçimimi hatırla"}}}`;

    /**
     * @typedef {object} OverlayOptions
     * @property {import("../duck-player.js").UserValues} userValues
     * @property {import("../duck-player.js").OverlaysFeatureSettings} settings
     * @property {import("../duck-player.js").DuckPlayerOverlayMessages} messages
     * @property {import("../duck-player.js").UISettings} ui
     * @property {Environment} environment
     */

    /**
     * @param {import("../duck-player.js").OverlaysFeatureSettings} settings - methods to read environment-sensitive things like the current URL etc
     * @param {import("./overlays.js").Environment} environment - methods to read environment-sensitive things like the current URL etc
     * @param {import("./overlay-messages.js").DuckPlayerOverlayMessages} messages - methods to communicate with a native backend
     */
    async function initOverlays(settings, environment, messages) {
        // bind early to attach all listeners
        const domState = new DomState();

        /** @type {import("../duck-player.js").OverlaysInitialSettings} */
        let initialSetup;
        try {
            initialSetup = await messages.initialSetup();
        } catch (e) {
            console.error(e);
            return;
        }

        if (!initialSetup) {
            console.error('cannot continue without user settings');
            return;
        }

        let { userValues, ui } = initialSetup;

        /**
         * Create the instance - this might fail if settings or user preferences prevent it
         * @type {Thumbnails|ClickInterception|null}
         */
        let thumbnails = thumbnailsFeatureFromOptions({ userValues, settings, messages, environment, ui });
        let videoOverlays = videoOverlaysFeatureFromSettings({ userValues, settings, messages, environment, ui });

        if (thumbnails || videoOverlays) {
            if (videoOverlays) {
                registerCustomElements();
                videoOverlays?.init('page-load');
            }
            domState.onLoaded(() => {
                // start initially
                thumbnails?.init();

                // now add video overlay specific stuff
                if (videoOverlays) {
                    // there was an issue capturing history.pushState, so just falling back to
                    let prev = globalThis.location.href;
                    setInterval(() => {
                        if (globalThis.location.href !== prev) {
                            videoOverlays?.init('href-changed');
                        }
                        prev = globalThis.location.href;
                    }, 500);
                }
            });
        }

        function update() {
            thumbnails?.destroy();
            videoOverlays?.destroy();

            // re-create thumbs
            thumbnails = thumbnailsFeatureFromOptions({ userValues, settings, messages, environment, ui });
            thumbnails?.init();

            // re-create video overlay
            videoOverlays = videoOverlaysFeatureFromSettings({ userValues, settings, messages, environment, ui });
            videoOverlays?.init('preferences-changed');
        }

        /**
         * Continue to listen for updated preferences and try to re-initiate
         */
        messages.onUserValuesChanged((_userValues) => {
            userValues = _userValues;
            update();
        });

        /**
         * Continue to listen for updated UI settings and try to re-initiate
         */
        messages.onUIValuesChanged((_ui) => {
            ui = _ui;
            update();
        });
    }

    /**
     * @param {OverlayOptions} options
     * @returns {Thumbnails | ClickInterception | null}
     */
    function thumbnailsFeatureFromOptions(options) {
        return thumbnailOverlays(options) || clickInterceptions(options);
    }

    /**
     * @param {OverlayOptions} options
     * @return {Thumbnails | null}
     */
    function thumbnailOverlays({ userValues, settings, messages, environment, ui }) {
        // bail if not enabled remotely
        if (settings.thumbnailOverlays.state !== 'enabled') return null;

        const conditions = [
            // must be in 'always ask' mode
            'alwaysAsk' in userValues.privatePlayerMode,
            // must not be set to play in DuckPlayer
            ui?.playInDuckPlayer !== true,
            // must be a desktop layout
            environment.layout === 'desktop',
        ];

        // Only show thumbnails if ALL conditions above are met
        if (!conditions.every(Boolean)) return null;

        return new Thumbnails({
            environment,
            settings,
            messages,
        });
    }

    /**
     * @param {OverlayOptions} options
     * @return {ClickInterception | null}
     */
    function clickInterceptions({ userValues, settings, messages, environment, ui }) {
        // bail if not enabled remotely
        if (settings.clickInterception.state !== 'enabled') return null;

        const conditions = [
            // either enabled via prefs
            'enabled' in userValues.privatePlayerMode,
            // or has a one-time override
            ui?.playInDuckPlayer === true,
        ];

        // Intercept clicks if ANY of the conditions above are met
        if (!conditions.some(Boolean)) return null;

        return new ClickInterception({
            environment,
            settings,
            messages,
        });
    }

    /**
     * @param {OverlayOptions} options
     * @returns {VideoOverlay | undefined}
     */
    function videoOverlaysFeatureFromSettings({ userValues, settings, messages, environment, ui }) {
        if (settings.videoOverlays.state !== 'enabled') return undefined;

        return new VideoOverlay({ userValues, settings, environment, messages, ui });
    }

    class Environment {
        allowedProxyOrigins = ['duckduckgo.com'];
        _strings = JSON.parse(strings);

        /**
         * @param {object} params
         * @param {{name: string}} params.platform
         * @param {boolean|null|undefined} [params.debug]
         * @param {ImportMeta['injectName']} params.injectName
         * @param {string} params.locale
         */
        constructor(params) {
            this.debug = Boolean(params.debug);
            this.injectName = params.injectName;
            this.platform = params.platform;
            this.locale = params.locale;
        }

        get strings() {
            const matched = this._strings[this.locale];
            if (matched) return matched['overlays.json'];
            return this._strings.en['overlays.json'];
        }

        /**
         * This is the URL of the page that the user is currently on
         * It's abstracted so that we can mock it in tests
         * @return {string}
         */
        getPlayerPageHref() {
            if (this.debug) {
                const url = new URL(window.location.href);
                if (url.hostname === 'www.youtube.com') return window.location.href;

                // reflect certain query params, this is useful for testing
                if (url.searchParams.has('v')) {
                    const base = new URL('/watch', 'https://youtube.com');
                    base.searchParams.set('v', url.searchParams.get('v') || '');
                    return base.toString();
                }

                return 'https://youtube.com/watch?v=123';
            }
            return window.location.href;
        }

        getLargeThumbnailSrc(videoId) {
            const url = new URL(`/vi/${videoId}/maxresdefault.jpg`, 'https://i.ytimg.com');
            return url.href;
        }

        setHref(href) {
            window.location.href = href;
        }

        hasOneTimeOverride() {
            try {
                // #ddg-play is a hard requirement, regardless of referrer
                if (window.location.hash !== '#ddg-play') return false;

                // double-check that we have something that might be a parseable URL
                if (typeof document.referrer !== 'string') return false;
                if (document.referrer.length === 0) return false; // can be empty!

                const { hostname } = new URL(document.referrer);
                const isAllowed = this.allowedProxyOrigins.includes(hostname);
                return isAllowed;
            } catch (e) {
                console.error(e);
            }
            return false;
        }

        isIntegrationMode() {
            return this.debug === true && this.injectName === 'integration';
        }

        isTestMode() {
            return this.debug === true;
        }

        get opensVideoOverlayLinksViaMessage() {
            return this.platform.name !== 'windows';
        }

        /**
         * @return {boolean}
         */
        get isMobile() {
            return this.platform.name === 'ios' || this.platform.name === 'android';
        }

        /**
         * @return {boolean}
         */
        get isDesktop() {
            return !this.isMobile;
        }

        /**
         * @return {'desktop' | 'mobile'}
         */
        get layout() {
            if (this.platform.name === 'ios' || this.platform.name === 'android') {
                return 'mobile';
            }
            return 'desktop';
        }
    }

    /**
     *
     * Duck Player Overlays are either the small Dax icons that appear on top of video thumbnails
     * when browsing YouTube. These icons allow users to open the video in Duck Player.
     *
     * On the YouTube player page, the main Duck Player Overlay also allows users to open the video
     * in Duck Player, or dismiss the overlay.
     *
     * #### Messages:
     *
     * On Page Load
     *   - {@link DuckPlayerOverlayMessages.initialSetup} is initially called to get the current settings
     *   - {@link DuckPlayerOverlayMessages.onUserValuesChanged} subscription begins immediately - it will continue to listen for updates
     *   - {@link DuckPlayerOverlayMessages.onUIValuesChanged} subscription begins immediately - it will continue to listen for updates
     *
     * Then the following message can be sent at any time
     *   - {@link DuckPlayerOverlayMessages.setUserValues}
     *   - {@link DuckPlayerOverlayMessages.openDuckPlayer}
     *
     * Please see {@link DuckPlayerOverlayMessages} for the up-to-date list
     *
     * ## Remote Config
     *
     *   - Please see {@link OverlaysFeatureSettings} for docs on the individual fields
     *
     * All features are **off** by default. Remote config is then used to selectively enable features.
     *
     * For example, to enable the Duck Player Overlay on YouTube, the following config is used:
     *
     * [📝 JSON example](../../integration-test/test-pages/duckplayer/config/overlays-live.json)
     *
     * @module Duck Player Overlays
     */

    /**
     * @typedef UserValues - A way to communicate user settings
     * @property {{enabled: {}} | {alwaysAsk:{}} | {disabled:{}}} privatePlayerMode - one of 3 values
     * @property {boolean} overlayInteracted - always a boolean
     */

    /**
     * @typedef UISettings - UI-specific settings
     * @property {boolean} [allowFirstVideo] - should the first video be allowed to load/play?
     * @property {boolean} [playInDuckPlayer] - Forces next video to be played in Duck Player regardless of user setting
     */

    /**
     * @typedef OverlaysInitialSettings - The initial payload used to communicate render-blocking information
     * @property {UserValues} userValues
     * @property {UISettings} ui
     */

    /**
     * @internal
     */
    class DuckPlayerFeature extends ContentFeature {
        init(args) {
            /**
             * This feature never operates in a frame
             */
            if (isBeingFramed()) return;

            /**
             * Just the 'overlays' part of the settings object.
             * @type {import("@duckduckgo/privacy-configuration/schema/features/duckplayer").DuckPlayerSettings['overlays']}
             */
            const overlaySettings = this.getFeatureSetting('overlays');
            const overlaysEnabled = overlaySettings?.youtube?.state === 'enabled';

            /**
             * Serp proxy
             */
            const serpProxyEnabled = overlaySettings?.serpProxy?.state === 'enabled';

            /**
             * Bail if no features are enabled
             */
            if (!overlaysEnabled && !serpProxyEnabled) {
                return;
            }

            /**
             * Bail if no messaging backend - this is a debugging feature to ensure we don't
             * accidentally enabled this
             */
            if (!this.messaging) {
                throw new Error('cannot operate duck player without a messaging backend');
            }

            const locale = args?.locale || args?.language || 'en';
            const env = new Environment({
                debug: args.debug,
                injectName: "android",
                platform: this.platform,
                locale,
            });
            const comms = new DuckPlayerOverlayMessages(this.messaging, env);

            if (overlaysEnabled) {
                initOverlays(overlaySettings.youtube, env, comms);
            } else if (serpProxyEnabled) {
                comms.serpProxy();
            }
        }

        load(args) {
            super.load(args);
        }
    }

    /**
     * @typedef {Pick<import("../captured-globals.js"),
     *    "dispatchEvent" | "addEventListener" | "CustomEvent">
     * } Captured
     */

    /**
     * This part has access to messaging handlers
     */
    class MessageBridge extends ContentFeature {
        /** @type {Captured} */
        captured = capturedGlobals;
        /**
         * A mapping of feature names to instances of `Messaging`.
         * This allows the bridge to handle more than 1 feature at a time.
         * @type {Map<string, Messaging>}
         */
        proxies = new Map$1();

        /**
         * If any subscriptions are created, we store the cleanup functions
         * for later use.
         * @type {Map<string, () => void>}
         */
        subscriptions = new Map$1();

        /**
         * This side of the bridge can only be instantiated once,
         * so we use this flag to ensure we can handle multiple invocations
         */
        installed = false;

        init(args) {
            /**
             * This feature never operates in a frame or insecure context
             */
            if (isBeingFramed() || !isSecureContext) return;
            /**
             * This feature never operates without messageSecret
             */
            if (!args.messageSecret) return;

            const { captured } = this;

            /**
             * @param {string} eventName
             * @return {`${string}-${string}`}
             */
            function appendToken(eventName) {
                return `${eventName}-${args.messageSecret}`;
            }

            /**
             * @param {{name: string; id: string} & Record<string, any>} incoming
             */
            const reply = (incoming) => {
                if (!args.messageSecret) return this.log('ignoring because args.messageSecret was absent');
                const eventName = appendToken(incoming.name + '-' + incoming.id);
                const event = new captured.CustomEvent(eventName, { detail: incoming });
                captured.dispatchEvent(event);
            };

            /**
             * @template T
             * @param {{ create: (params: any) => T | null, NAME: string }} ClassType - A class with a `create` static method.
             * @param {(instance: T) => void} callback - A callback that receives an instance of the class.
             */
            const accept = (ClassType, callback) => {
                captured.addEventListener(appendToken(ClassType.NAME), (/** @type {CustomEvent<unknown>} */ e) => {
                    this.log(`${ClassType.NAME}`, JSON.stringify(e.detail));
                    const instance = ClassType.create(e.detail);
                    if (instance) {
                        callback(instance);
                    } else {
                        this.log('Failed to create an instance');
                    }
                });
            };

            /**
             * These are all the messages we accept from the page-world.
             */
            this.log(`bridge is installing...`);
            accept(InstallProxy, (install) => {
                this.installProxyFor(install, args.messagingConfig, reply);
            });
            accept(ProxyNotification, (notification) => this.proxyNotification(notification));
            accept(ProxyRequest, (request) => this.proxyRequest(request, reply));
            accept(SubscriptionRequest, (subscription) => this.proxySubscription(subscription, reply));
            accept(SubscriptionUnsubscribe, (unsubscribe) => this.removeSubscription(unsubscribe.id));
        }

        /**
         * Installing a feature proxy is the act of creating a fresh instance of 'Messaging', but
         * using the same underlying transport
         *
         * @param {InstallProxy} install
         * @param {import('@duckduckgo/messaging').MessagingConfig} config
         * @param {(payload: {name: string; id: string} & Record<string, any>) => void} reply
         */
        installProxyFor(install, config, reply) {
            const { id, featureName } = install;
            if (this.proxies.has(featureName)) return this.log('ignoring `installProxyFor` because it exists', featureName);
            const allowed = this.getFeatureSettingEnabled(featureName);
            if (!allowed) {
                return this.log('not installing proxy, because', featureName, 'was not enabled');
            }

            const ctx = { ...this.messaging.messagingContext, featureName };
            const messaging = new Messaging(ctx, config);
            this.proxies.set(featureName, messaging);

            this.log('did install proxy for ', featureName);
            reply(new DidInstall({ id }));
        }

        /**
         * @param {ProxyRequest} request
         * @param {(payload: {name: string; id: string} & Record<string, any>) => void} reply
         */
        async proxyRequest(request, reply) {
            const { id, featureName, method, params } = request;

            const proxy = this.proxies.get(featureName);
            if (!proxy) return this.log('proxy was not installed for ', featureName);

            this.log('will proxy', request);

            try {
                const result = await proxy.request(method, params);
                const responseEvent = new ProxyResponse({
                    method,
                    featureName,
                    result,
                    id,
                });
                reply(responseEvent);
            } catch (e) {
                const errorResponseEvent = new ProxyResponse({
                    method,
                    featureName,
                    error: { message: e.message },
                    id,
                });
                reply(errorResponseEvent);
            }
        }

        /**
         * @param {SubscriptionRequest} subscription
         * @param {(payload: {name: string; id: string} & Record<string, any>) => void} reply
         */
        proxySubscription(subscription, reply) {
            const { id, featureName, subscriptionName } = subscription;
            const proxy = this.proxies.get(subscription.featureName);
            if (!proxy) return this.log('proxy was not installed for', featureName);

            this.log('will setup subscription', subscription);

            // cleanup existing subscriptions first
            const prev = this.subscriptions.get(id);
            if (prev) {
                this.removeSubscription(id);
            }

            const unsubscribe = proxy.subscribe(subscriptionName, (/** @type {Record<string, any>} */ data) => {
                const responseEvent = new SubscriptionResponse({
                    subscriptionName,
                    featureName,
                    params: data,
                    id,
                });
                reply(responseEvent);
            });

            this.subscriptions.set(id, unsubscribe);
        }

        /**
         * @param {string} id
         */
        removeSubscription(id) {
            const unsubscribe = this.subscriptions.get(id);
            this.log(`will remove subscription`, id);
            unsubscribe?.();
            this.subscriptions.delete(id);
        }

        /**
         * @param {ProxyNotification} notification
         */
        proxyNotification(notification) {
            const proxy = this.proxies.get(notification.featureName);
            if (!proxy) return this.log('proxy was not installed for', notification.featureName);

            this.log('will proxy notification', notification);
            proxy.notify(notification.method, notification.params);
        }

        /**
         * @param {Parameters<console['log']>} args
         */
        log(...args) {
            if (this.isDebug) {
                console.log('[isolated]', ...args);
            }
        }

        load(args) {}
    }

    var platformFeatures = {
        ddg_feature_fingerprintingAudio: FingerprintingAudio,
        ddg_feature_fingerprintingBattery: FingerprintingBattery,
        ddg_feature_fingerprintingCanvas: FingerprintingCanvas,
        ddg_feature_googleRejected: GoogleRejected,
        ddg_feature_gpc: GlobalPrivacyControl,
        ddg_feature_fingerprintingHardware: FingerprintingHardware,
        ddg_feature_referrer: Referrer,
        ddg_feature_fingerprintingScreenSize: FingerprintingScreenSize,
        ddg_feature_fingerprintingTemporaryStorage: FingerprintingTemporaryStorage,
        ddg_feature_navigatorInterface: NavigatorInterface,
        ddg_feature_elementHiding: ElementHiding,
        ddg_feature_exceptionHandler: ExceptionHandler,
        ddg_feature_apiManipulation: ApiManipulation,
        ddg_feature_webCompat: WebCompat,
        ddg_feature_breakageReporting: BreakageReporting,
        ddg_feature_duckPlayer: DuckPlayerFeature,
        ddg_feature_messageBridge: MessageBridge
    };

    let initArgs = null;
    const updates = [];
    const features = [];
    const alwaysInitFeatures = new Set(['cookie']);
    const performanceMonitor = new PerformanceMonitor();

    // It's important to avoid enabling the features for non-HTML documents (such as
    // XML documents that aren't XHTML). Note that it's necessary to check the
    // document type in advance, to minimise the risk of a website breaking the
    // checks by altering document.__proto__. In the future, it might be worth
    // running the checks even earlier (and in the "isolated world" for the Chrome
    // extension), to further reduce that risk.
    const isHTMLDocument =
        document instanceof HTMLDocument || (document instanceof XMLDocument && document.createElement('div') instanceof HTMLDivElement);

    /**
     * @typedef {object} LoadArgs
     * @property {import('./content-feature').Site} site
     * @property {import('./utils.js').Platform} platform
     * @property {boolean} documentOriginIsTracker
     * @property {import('./utils.js').RemoteConfig} bundledConfig
     * @property {string} [injectName]
     * @property {object} trackerLookup - provided currently only by the extension
     * @property {import('@duckduckgo/messaging').MessagingConfig} [messagingConfig]
     * @property {string} [messageSecret] - optional, used in the messageBridge creation
     */

    /**
     * @param {LoadArgs} args
     */
    function load(args) {
        const mark = performanceMonitor.mark('load');
        if (!isHTMLDocument) {
            return;
        }

        const featureNames = platformSupport["android"] ;

        for (const featureName of featureNames) {
            const ContentFeature = platformFeatures['ddg_feature_' + featureName];
            const featureInstance = new ContentFeature(featureName);
            featureInstance.callLoad(args);
            features.push({ featureName, featureInstance });
        }
        mark.end();
    }

    async function init(args) {
        const mark = performanceMonitor.mark('init');
        initArgs = args;
        if (!isHTMLDocument) {
            return;
        }
        registerMessageSecret(args.messageSecret);
        initStringExemptionLists(args);
        const resolvedFeatures = await Promise.all(features);
        resolvedFeatures.forEach(({ featureInstance, featureName }) => {
            if (!isFeatureBroken(args, featureName) || alwaysInitExtensionFeatures(args, featureName)) {
                featureInstance.callInit(args);
            }
        });
        // Fire off updates that came in faster than the init
        while (updates.length) {
            const update = updates.pop();
            await updateFeaturesInner(update);
        }
        mark.end();
        if (args.debug) {
            performanceMonitor.measureAll();
        }
    }

    function alwaysInitExtensionFeatures(args, featureName) {
        return args.platform.name === 'extension' && alwaysInitFeatures.has(featureName);
    }

    async function updateFeaturesInner(args) {
        const resolvedFeatures = await Promise.all(features);
        resolvedFeatures.forEach(({ featureInstance, featureName }) => {
            if (!isFeatureBroken(initArgs, featureName) && featureInstance.update) {
                featureInstance.update(args);
            }
        });
    }

    /**
     * Check if the current document origin is on the tracker list, using the provided lookup trie.
     * @param {object} trackerLookup Trie lookup of tracker domains
     * @returns {boolean} True iff the origin is a tracker.
     */
    function isTrackerOrigin(trackerLookup, originHostname = document.location.hostname) {
        const parts = originHostname.split('.').reverse();
        let node = trackerLookup;
        for (const sub of parts) {
            if (node[sub] === 1) {
                return true;
            } else if (node[sub]) {
                node = node[sub];
            } else {
                return false;
            }
        }
        return false;
    }

    /**
     * @module Android integration
     */

    function initCode() {
        // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
        const config = $CONTENT_SCOPE$;
        // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
        const userUnprotectedDomains = $USER_UNPROTECTED_DOMAINS$;
        // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
        const userPreferences = $USER_PREFERENCES$;

        const processedConfig = processConfig(config, userUnprotectedDomains, userPreferences);
        if (isGloballyDisabled(processedConfig)) {
            return;
        }

        const configConstruct = processedConfig;
        const messageCallback = configConstruct.messageCallback;
        const messageSecret = configConstruct.messageSecret;
        const javascriptInterface = configConstruct.javascriptInterface;
        processedConfig.messagingConfig = new AndroidMessagingConfig({
            messageSecret,
            messageCallback,
            javascriptInterface,
            target: globalThis,
            debug: processedConfig.debug,
        });

        load({
            platform: processedConfig.platform,
            trackerLookup: processedConfig.trackerLookup,
            documentOriginIsTracker: isTrackerOrigin(processedConfig.trackerLookup),
            site: processedConfig.site,
            bundledConfig: processedConfig.bundledConfig,
            messagingConfig: processedConfig.messagingConfig,
            messageSecret: processedConfig.messageSecret,
        });

        init(processedConfig);
    }

    initCode();

})();
