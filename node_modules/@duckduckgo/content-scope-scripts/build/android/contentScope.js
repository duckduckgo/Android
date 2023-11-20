/*! © DuckDuckGo ContentScopeScripts protections https://github.com/duckduckgo/content-scope-scripts/ */
(function () {
    'use strict';

    const Set$1 = globalThis.Set;
    const Reflect$1 = globalThis.Reflect;
    globalThis.customElements?.get.bind(globalThis.customElements);
    globalThis.customElements?.define.bind(globalThis.customElements);
    const getOwnPropertyDescriptor = Object.getOwnPropertyDescriptor;
    const objectKeys = Object.keys;
    const URL$1 = globalThis.URL;

    /* global cloneInto, exportFunction, false */

    // Only use globalThis for testing this breaks window.wrappedJSObject code in Firefox
    // eslint-disable-next-line no-global-assign
    let globalObj = typeof window === 'undefined' ? globalThis : window;
    let Error$1 = globalObj.Error;
    let messageSecret;

    const taintSymbol = Symbol('taint');

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
     * @returns {HTMLLinkElement | HTMLStyleElement}
     */
    function createStyleElement (css) {
        let style;
        {
            style = document.createElement('style');
            style.innerText = css;
        }
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
        if (globalThis.location && 'ancestorOrigins' in globalThis.location) {
            return globalThis.location.ancestorOrigins.length > 0
        }
        return globalThis.top !== globalThis.window
    }

    /**
     * Best guess effort of the tabs hostname; where possible always prefer the args.site.domain
     * @returns {string|null} inferred tab hostname
     */
    function getTabHostname () {
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
        return urls
    }

    function getStackTraceOrigins (stack) {
        const urls = getStackTraceUrls(stack);
        const origins = new Set$1();
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
        // @ts-expect-error - Object is possibly 'null'
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

    const functionMap = {
        /** Useful for debugging APIs in the wild, shouldn't be used */
        debug: (...args) => {
            console.log('debugger', ...args);
            // eslint-disable-next-line no-debugger
            debugger
        },
        // eslint-disable-next-line @typescript-eslint/no-empty-function
        noop: () => { }
    };

    /**
     * Processes a structured config setting and returns the value according to its type
     * @param {*} configSetting
     * @param {*} [defaultValue]
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

            if (configSetting.type === 'function') {
                if (configSetting.functionName && functionMap[configSetting.functionName]) {
                    return functionMap[configSetting.functionName]
                }
            }

            if (configSetting.type === 'undefined') {
                return undefined
            }

            return configSetting.value
        default:
            return defaultValue
        }
    }

    function getStack () {
        return new Error$1().stack
    }

    function getContextId (scope) {
        if (document?.currentScript && 'contextID' in document.currentScript) {
            return document.currentScript.contextID
        }
        if (scope.contextID) {
            return scope.contextID
        }
        // @ts-expect-error - contextID is a global variable
        if (typeof contextID !== 'undefined') {
            // @ts-expect-error - contextID is a global variable
            // eslint-disable-next-line no-undef
            return contextID
        }
    }

    /**
     * Returns a set of origins that are tainted
     * @returns {Set<string> | null}
     */
    function taintedOrigins () {
        return getGlobalObject('taintedOrigins')
    }

    /**
     * @param {string} name
     * @returns {any | null}
     */
    function getGlobalObject (name) {
        if ('duckduckgo' in navigator &&
            typeof navigator.duckduckgo === 'object' &&
            navigator.duckduckgo &&
            name in navigator.duckduckgo &&
            navigator.duckduckgo[name]) {
            return navigator.duckduckgo[name]
        }
        return null
    }

    function hasTaintedMethod (scope, shouldStackCheck = false) {
        if (document?.currentScript?.[taintSymbol]) return true
        if ('__ddg_taint__' in window) return true
        if (getContextId(scope)) return true
        if (!shouldStackCheck || !taintedOrigins()) {
            return false
        }
        const currentTaintedOrigins = taintedOrigins();
        if (!currentTaintedOrigins || currentTaintedOrigins.size === 0) {
            return false
        }
        const stackOrigins = getStackTraceOrigins(getStack());
        for (const stackOrigin of stackOrigins) {
            if (currentTaintedOrigins.has(stackOrigin)) {
                return true
            }
        }
        return false
    }

    /**
     * @param {*[]} argsArray
     * @returns {string}
     */
    function debugSerialize (argsArray) {
        const maxSerializedSize = 1000;
        const serializedArgs = argsArray.map((arg) => {
            try {
                const serializableOut = JSON.stringify(arg);
                if (serializableOut.length > maxSerializedSize) {
                    return `<truncated, length: ${serializableOut.length}, value: ${serializableOut.substring(0, maxSerializedSize)}...>`
                }
                return serializableOut
            } catch (e) {
                // Sometimes this happens when we can't serialize an object to string but we still wish to log it and make other args readable
                return '<unserializable>'
            }
        });
        return JSON.stringify(serializedArgs)
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
        constructor (feature, objectScope, property, proxyObject, taintCheck = false) {
            this.objectScope = objectScope;
            this.property = property;
            this.feature = feature;
            this.featureName = feature.name;
            this.camelFeatureName = camelcase(this.featureName);
            const outputHandler = (...args) => {
                this.feature.addDebugFlag();
                let isExempt = shouldExemptMethod(this.camelFeatureName);
                // If taint checking is enabled for this proxy then we should verify that the method is not tainted and exempt if it isn't
                if (!isExempt && taintCheck) {
                    // eslint-disable-next-line @typescript-eslint/no-this-alias
                    let scope = this;
                    try {
                        // @ts-expect-error - Caller doesn't match this
                        // eslint-disable-next-line no-caller
                        scope = arguments.callee.caller;
                    } catch {}
                    const isTainted = hasTaintedMethod(scope);
                    isExempt = !isTainted;
                }
                // Keep this here as getStack() is expensive
                if (debug) {
                    postDebugMessage(this.camelFeatureName, {
                        isProxy: true,
                        action: isExempt ? 'ignore' : 'restrict',
                        kind: this.property,
                        documentUrl: document.location.href,
                        stack: getStack(),
                        args: debugSerialize(args[2])
                    });
                }
                // The normal return value
                if (isExempt) {
                    return DDGReflect.apply(...args)
                }
                return proxyObject.apply(...args)
            };
            const getMethod = (target, prop, receiver) => {
                this.feature.addDebugFlag();
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

        overloadDescriptor () {
            this.feature.defineProperty(this.objectScope, this.property, {
                value: this.internal
            });
        }
    }

    const maxCounter = new Map();
    function numberOfTimesDebugged (feature) {
        if (!maxCounter.has(feature)) {
            maxCounter.set(feature, 1);
        } else {
            maxCounter.set(feature, maxCounter.get(feature) + 1);
        }
        return maxCounter.get(feature)
    }

    const DEBUG_MAX_TIMES = 5000;

    function postDebugMessage (feature, message, allowNonDebug = false) {
        if (!debug && !allowNonDebug) {
            return
        }
        if (numberOfTimesDebugged(feature) > DEBUG_MAX_TIMES) {
            return
        }
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

    /**
     * @param {string | null} topLevelHostname
     * @param {object[]} featureList
     * @returns {boolean}
     */
    function isUnprotectedDomain (topLevelHostname, featureList) {
        let unprotectedDomain = false;
        if (!topLevelHostname) {
            return false
        }
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
    function computeLimitedSiteObject () {
        const topLevelHostname = getTabHostname();
        return {
            domain: topLevelHostname
        }
    }

    /**
     * Expansion point to add platform specific versioning logic
     * @param {UserPreferences} preferences
     * @returns {string | number | undefined}
     */
    function getPlatformVersion (preferences) {
        if (preferences.versionNumber) {
            return preferences.versionNumber
        }
        if (preferences.versionString) {
            return preferences.versionString
        }
        return undefined
    }

    function parseVersionString (versionString) {
        return versionString.split('.').map(Number)
    }

    /**
     * @param {string} minVersionString
     * @param {string} applicationVersionString
     * @returns {boolean}
     */
    function satisfiesMinVersion (minVersionString, applicationVersionString) {
        const minVersions = parseVersionString(minVersionString);
        const currentVersions = parseVersionString(applicationVersionString);
        const maxLength = Math.max(minVersions.length, currentVersions.length);
        for (let i = 0; i < maxLength; i++) {
            const minNumberPart = minVersions[i] || 0;
            const currentVersionPart = currentVersions[i] || 0;
            if (currentVersionPart > minNumberPart) {
                return true
            }
            if (currentVersionPart < minNumberPart) {
                return false
            }
        }
        return true
    }

    /**
     * @param {string | number | undefined} minSupportedVersion
     * @param {string | number | undefined} currentVersion
     * @returns {boolean}
     */
    function isSupportedVersion (minSupportedVersion, currentVersion) {
        if (typeof currentVersion === 'string' && typeof minSupportedVersion === 'string') {
            if (satisfiesMinVersion(minSupportedVersion, currentVersion)) {
                return true
            }
        } else if (typeof currentVersion === 'number' && typeof minSupportedVersion === 'number') {
            if (minSupportedVersion <= currentVersion) {
                return true
            }
        }
        return false
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
    function processConfig (data, userList, preferences, platformSpecificFeatures = []) {
        const topLevelHostname = getTabHostname();
        const site = computeLimitedSiteObject();
        const allowlisted = userList.filter(domain => domain === topLevelHostname).length > 0;
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
            enabledFeatures
        });

        // Copy feature settings from remote config to preferences object
        output.featureSettings = parseFeatureSettings(data, enabledFeatures);
        output.trackerLookup = {"org":{"cdn77":{"rsc":{"1558334541":1}},"adsrvr":1,"ampproject":1,"browser-update":1,"flowplayer":1,"privacy-center":1,"webvisor":1,"framasoft":1,"do-not-tracker":1,"trackersimulator":1},"io":{"1dmp":1,"1rx":1,"4dex":1,"adnami":1,"aidata":1,"arcspire":1,"bidr":1,"branch":1,"center":1,"cloudimg":1,"concert":1,"connectad":1,"cordial":1,"dcmn":1,"extole":1,"getblue":1,"hbrd":1,"instana":1,"karte":1,"leadsmonitor":1,"litix":1,"lytics":1,"marchex":1,"mediago":1,"mrf":1,"narrative":1,"ntv":1,"optad360":1,"oracleinfinity":1,"oribi":1,"p-n":1,"personalizer":1,"pghub":1,"piano":1,"powr":1,"pzz":1,"searchspring":1,"segment":1,"siteimproveanalytics":1,"sspinc":1,"t13":1,"webgains":1,"wovn":1,"yellowblue":1,"zprk":1,"axept":1,"akstat":1,"clarium":1,"hotjar":1},"com":{"2020mustang":1,"33across":1,"360yield":1,"3lift":1,"4dsply":1,"4strokemedia":1,"8353e36c2a":1,"a-mx":1,"a2z":1,"aamsitecertifier":1,"absorbingband":1,"abstractedauthority":1,"abtasty":1,"acexedge":1,"acidpigs":1,"acsbapp":1,"acuityplatform":1,"ad-score":1,"ad-stir":1,"adalyser":1,"adapf":1,"adara":1,"adblade":1,"addthis":1,"addtoany":1,"adelixir":1,"adentifi":1,"adextrem":1,"adgrx":1,"adhese":1,"adition":1,"adkernel":1,"adlightning":1,"adlooxtracking":1,"admanmedia":1,"admedo":1,"adnium":1,"adnxs-simple":1,"adnxs":1,"adobedtm":1,"adotmob":1,"adpone":1,"adpushup":1,"adroll":1,"adrta":1,"ads-twitter":1,"ads3-adnow":1,"adsafeprotected":1,"adstanding":1,"adswizz":1,"adtdp":1,"adtechus":1,"adtelligent":1,"adthrive":1,"adtlgc":1,"adtng":1,"adultfriendfinder":1,"advangelists":1,"adventive":1,"adventori":1,"advertising":1,"aegpresents":1,"affinity":1,"affirm":1,"agilone":1,"agkn":1,"aimbase":1,"albacross":1,"alcmpn":1,"alexametrics":1,"alicdn":1,"alikeaddition":1,"aliveachiever":1,"aliyuncs":1,"alluringbucket":1,"aloofvest":1,"amazon-adsystem":1,"amazon":1,"ambiguousafternoon":1,"amplitude":1,"analytics-egain":1,"aniview":1,"annoyedairport":1,"annoyingclover":1,"anyclip":1,"anymind360":1,"app-us1":1,"appboycdn":1,"appdynamics":1,"appsflyer":1,"aralego":1,"aspiringattempt":1,"aswpsdkus":1,"atemda":1,"att":1,"attentivemobile":1,"attractionbanana":1,"audioeye":1,"audrte":1,"automaticside":1,"avanser":1,"avmws":1,"aweber":1,"aweprt":1,"azure":1,"b0e8":1,"badgevolcano":1,"bagbeam":1,"ballsbanana":1,"bandborder":1,"batch":1,"bawdybalance":1,"bc0a":1,"bdstatic":1,"bedsberry":1,"beginnerpancake":1,"benchmarkemail":1,"betweendigital":1,"bfmio":1,"bidtheatre":1,"billowybelief":1,"bimbolive":1,"bing":1,"bizographics":1,"bizrate":1,"bkrtx":1,"blismedia":1,"blogherads":1,"bluecava":1,"bluekai":1,"blushingbread":1,"boatwizard":1,"boilingcredit":1,"boldchat":1,"booking":1,"borderfree":1,"bounceexchange":1,"brainlyads":1,"brand-display":1,"brandmetrics":1,"brealtime":1,"brightfunnel":1,"brightspotcdn":1,"btloader":1,"btstatic":1,"bttrack":1,"btttag":1,"bumlam":1,"butterbulb":1,"buttonladybug":1,"buzzfeed":1,"buzzoola":1,"byside":1,"c3tag":1,"cabnnr":1,"calculatorstatement":1,"callrail":1,"calltracks":1,"capablecup":1,"captcha-delivery":1,"carpentercomparison":1,"cartstack":1,"carvecakes":1,"casalemedia":1,"cattlecommittee":1,"cdninstagram":1,"cdnwidget":1,"channeladvisor":1,"chargecracker":1,"chartbeat":1,"chatango":1,"chaturbate":1,"cheqzone":1,"cherriescare":1,"chickensstation":1,"childlikecrowd":1,"childlikeform":1,"chocolateplatform":1,"cintnetworks":1,"circlelevel":1,"ck-ie":1,"clcktrax":1,"cleanhaircut":1,"clearbit":1,"clearbitjs":1,"clickagy":1,"clickcease":1,"clickcertain":1,"clicktripz":1,"clientgear":1,"cloudflare":1,"cloudflareinsights":1,"cloudflarestream":1,"cobaltgroup":1,"cobrowser":1,"cognitivlabs":1,"colossusssp":1,"combativecar":1,"comm100":1,"googleapis":{"commondatastorage":1,"imasdk":1,"storage":1,"fonts":1,"maps":1,"www":1},"company-target":1,"condenastdigital":1,"confusedcart":1,"connatix":1,"contextweb":1,"conversionruler":1,"convertkit":1,"convertlanguage":1,"cootlogix":1,"coveo":1,"cpmstar":1,"cquotient":1,"crabbychin":1,"cratecamera":1,"crazyegg":1,"creative-serving":1,"creativecdn":1,"criteo":1,"crowdedmass":1,"crowdriff":1,"crownpeak":1,"crsspxl":1,"ctnsnet":1,"cudasvc":1,"cuddlethehyena":1,"cumbersomecarpenter":1,"curalate":1,"curvedhoney":1,"cushiondrum":1,"cutechin":1,"cxense":1,"d28dc30335":1,"dailymotion":1,"damdoor":1,"dampdock":1,"dapperfloor":1,"datadoghq-browser-agent":1,"decisivebase":1,"deepintent":1,"defybrick":1,"delivra":1,"demandbase":1,"detectdiscovery":1,"devilishdinner":1,"dimelochat":1,"disagreeabledrop":1,"discreetfield":1,"disqus":1,"dmpxs":1,"dockdigestion":1,"dotomi":1,"doubleverify":1,"drainpaste":1,"dramaticdirection":1,"driftt":1,"dtscdn":1,"dtscout":1,"dwin1":1,"dynamics":1,"dynamicyield":1,"dynatrace":1,"ebaystatic":1,"ecal":1,"eccmp":1,"elfsight":1,"elitrack":1,"eloqua":1,"en25":1,"encouragingthread":1,"enormousearth":1,"ensighten":1,"enviousshape":1,"eqads":1,"ero-advertising":1,"esputnik":1,"evergage":1,"evgnet":1,"exdynsrv":1,"exelator":1,"exoclick":1,"exosrv":1,"expansioneggnog":1,"expedia":1,"expertrec":1,"exponea":1,"exponential":1,"extole":1,"ezodn":1,"ezoic":1,"ezoiccdn":1,"facebook":1,"facil-iti":1,"fadewaves":1,"fallaciousfifth":1,"farmergoldfish":1,"fastly-insights":1,"fearlessfaucet":1,"fiftyt":1,"financefear":1,"fitanalytics":1,"five9":1,"fixedfold":1,"fksnk":1,"flashtalking":1,"flipp":1,"flowerstreatment":1,"floweryflavor":1,"flutteringfireman":1,"flux-cdn":1,"foresee":1,"fortunatemark":1,"fouanalytics":1,"fox":1,"fqtag":1,"frailfruit":1,"freezingbuilding":1,"fronttoad":1,"fullstory":1,"functionalfeather":1,"fuzzybasketball":1,"gammamaximum":1,"gbqofs":1,"geetest":1,"geistm":1,"geniusmonkey":1,"geoip-js":1,"getbread":1,"getcandid":1,"getclicky":1,"getdrip":1,"getelevar":1,"getrockerbox":1,"getshogun":1,"getsitecontrol":1,"giraffepiano":1,"glassdoor":1,"gloriousbeef":1,"godpvqnszo":1,"google-analytics":1,"google":1,"googleadservices":1,"googlehosted":1,"googleoptimize":1,"googlesyndication":1,"googletagmanager":1,"googletagservices":1,"gorgeousedge":1,"govx":1,"grainmass":1,"greasysquare":1,"greylabeldelivery":1,"groovehq":1,"growsumo":1,"gstatic":1,"guarantee-cdn":1,"guiltlessbasketball":1,"gumgum":1,"haltingbadge":1,"hammerhearing":1,"handsomelyhealth":1,"harborcaption":1,"hawksearch":1,"amazonaws":{"us-east-2":{"s3":{"hb-obv2":1}}},"heapanalytics":1,"hellobar":1,"hhbypdoecp":1,"hiconversion":1,"highwebmedia":1,"histats":1,"hlserve":1,"hocgeese":1,"hollowafterthought":1,"honorableland":1,"hotjar":1,"hp":1,"hs-banner":1,"htlbid":1,"htplayground":1,"hubspot":1,"ib-ibi":1,"id5-sync":1,"igodigital":1,"iheart":1,"iljmp":1,"illiweb":1,"impactcdn":1,"impactradius-event":1,"impressionmonster":1,"improvedcontactform":1,"improvedigital":1,"imrworldwide":1,"indexww":1,"infolinks":1,"infusionsoft":1,"inmobi":1,"inq":1,"inside-graph":1,"instagram":1,"intentiq":1,"intergient":1,"investingchannel":1,"invocacdn":1,"iperceptions":1,"iplsc":1,"ipredictive":1,"iteratehq":1,"ivitrack":1,"j93557g":1,"jaavnacsdw":1,"jimstatic":1,"journity":1,"js7k":1,"jscache":1,"juiceadv":1,"juicyads":1,"justanswer":1,"justpremium":1,"jwpcdn":1,"kakao":1,"kampyle":1,"kargo":1,"kissmetrics":1,"klarnaservices":1,"klaviyo":1,"knottyswing":1,"krushmedia":1,"ktkjmp":1,"kxcdn":1,"laboredlocket":1,"ladesk":1,"ladsp":1,"laughablelizards":1,"leadsrx":1,"lendingtree":1,"levexis":1,"liadm":1,"licdn":1,"lightboxcdn":1,"lijit":1,"linkedin":1,"linksynergy":1,"list-manage":1,"listrakbi":1,"livechatinc":1,"livejasmin":1,"localytics":1,"loggly":1,"loop11":1,"looseloaf":1,"lovelydrum":1,"lunchroomlock":1,"lwonclbench":1,"macromill":1,"maddeningpowder":1,"mailchimp":1,"mailchimpapp":1,"mailerlite":1,"maillist-manage":1,"marinsm":1,"marketiq":1,"marketo":1,"marphezis":1,"marriedbelief":1,"materialparcel":1,"matheranalytics":1,"mathtag":1,"maxmind":1,"mczbf":1,"measlymiddle":1,"medallia":1,"meddleplant":1,"media6degrees":1,"mediacategory":1,"mediavine":1,"mediawallahscript":1,"medtargetsystem":1,"megpxs":1,"memberful":1,"memorizematch":1,"mentorsticks":1,"metaffiliation":1,"metricode":1,"metricswpsh":1,"mfadsrvr":1,"mgid":1,"micpn":1,"microadinc":1,"minutemedia-prebid":1,"minutemediaservices":1,"mixpo":1,"mkt932":1,"mktoresp":1,"mktoweb":1,"ml314":1,"moatads":1,"mobtrakk":1,"monsido":1,"mookie1":1,"motionflowers":1,"mountain":1,"mouseflow":1,"mpeasylink":1,"mql5":1,"mrtnsvr":1,"murdoog":1,"mxpnl":1,"mybestpro":1,"myregistry":1,"nappyattack":1,"navistechnologies":1,"neodatagroup":1,"nervoussummer":1,"netmng":1,"newrelic":1,"newscgp":1,"nextdoor":1,"ninthdecimal":1,"nitropay":1,"noibu":1,"nondescriptnote":1,"nosto":1,"npttech":1,"ntvpwpush":1,"nuance":1,"nutritiousbean":1,"nxsttv":1,"omappapi":1,"omnisnippet1":1,"omnisrc":1,"omnitagjs":1,"ondemand":1,"oneall":1,"onesignal":1,"onetag-sys":1,"oo-syringe":1,"ooyala":1,"opecloud":1,"opentext":1,"opera":1,"opmnstr":1,"opti-digital":1,"optimicdn":1,"optimizely":1,"optinmonster":1,"optmnstr":1,"optmstr":1,"optnmnstr":1,"optnmstr":1,"osano":1,"otm-r":1,"outbrain":1,"overconfidentfood":1,"ownlocal":1,"pailpatch":1,"panickypancake":1,"panoramicplane":1,"parastorage":1,"pardot":1,"parsely":1,"partplanes":1,"patreon":1,"paypal":1,"pbstck":1,"pcmag":1,"peerius":1,"perfdrive":1,"perfectmarket":1,"permutive":1,"picreel":1,"pinterest":1,"pippio":1,"piwikpro":1,"pixlee":1,"placidperson":1,"pleasantpump":1,"plotrabbit":1,"pluckypocket":1,"pocketfaucet":1,"possibleboats":1,"postaffiliatepro":1,"postrelease":1,"potatoinvention":1,"powerfulcopper":1,"predictplate":1,"prepareplanes":1,"pricespider":1,"priceypies":1,"pricklydebt":1,"profusesupport":1,"proofpoint":1,"protoawe":1,"providesupport":1,"pswec":1,"psychedelicarithmetic":1,"psyma":1,"ptengine":1,"publir":1,"pubmatic":1,"pubmine":1,"pubnation":1,"qualaroo":1,"qualtrics":1,"quantcast":1,"quantserve":1,"quantummetric":1,"quietknowledge":1,"quizzicalpartner":1,"quizzicalzephyr":1,"quora":1,"r42tag":1,"radiateprose":1,"railwayreason":1,"rakuten":1,"rambunctiousflock":1,"rangeplayground":1,"rating-widget":1,"realsrv":1,"rebelswing":1,"reconditerake":1,"reconditerespect":1,"recruitics":1,"reddit":1,"redditstatic":1,"rehabilitatereason":1,"repeatsweater":1,"reson8":1,"resonantrock":1,"resonate":1,"responsiveads":1,"restrainstorm":1,"restructureinvention":1,"retargetly":1,"revcontent":1,"rezync":1,"rfihub":1,"rhetoricalloss":1,"richaudience":1,"righteouscrayon":1,"rightfulfall":1,"riotgames":1,"riskified":1,"rkdms":1,"rlcdn":1,"rmtag":1,"rogersmedia":1,"rokt":1,"route":1,"rtbsystem":1,"rubiconproject":1,"ruralrobin":1,"s-onetag":1,"saambaa":1,"sablesong":1,"sail-horizon":1,"salesforceliveagent":1,"samestretch":1,"sascdn":1,"satisfycork":1,"savoryorange":1,"scarabresearch":1,"scaredsnakes":1,"scaredsong":1,"scaredstomach":1,"scarfsmash":1,"scene7":1,"scholarlyiq":1,"scintillatingsilver":1,"scorecardresearch":1,"screechingstove":1,"screenpopper":1,"scribblestring":1,"sddan":1,"seatsmoke":1,"securedvisit":1,"seedtag":1,"sefsdvc":1,"segment":1,"sekindo":1,"selectivesummer":1,"selfishsnake":1,"servebom":1,"servedbyadbutler":1,"servenobid":1,"serverbid":1,"serving-sys":1,"shakegoldfish":1,"shamerain":1,"shapecomb":1,"shappify":1,"shareaholic":1,"sharethis":1,"sharethrough":1,"shopifyapps":1,"shopperapproved":1,"shrillspoon":1,"sibautomation":1,"sicksmash":1,"signifyd":1,"singroot":1,"site":1,"siteimprove":1,"siteimproveanalytics":1,"sitescout":1,"sixauthority":1,"skillfuldrop":1,"skimresources":1,"skisofa":1,"sli-spark":1,"slickstream":1,"slopesoap":1,"smadex":1,"smartadserver":1,"smashquartz":1,"smashsurprise":1,"smg":1,"smilewanted":1,"smoggysnakes":1,"snapchat":1,"snapkit":1,"snigelweb":1,"socdm":1,"sojern":1,"songsterritory":1,"sonobi":1,"soundstocking":1,"spectacularstamp":1,"speedcurve":1,"sphereup":1,"spiceworks":1,"spookyexchange":1,"spookyskate":1,"spookysleet":1,"sportradarserving":1,"sportslocalmedia":1,"spotxchange":1,"springserve":1,"srvmath":1,"ssl-images-amazon":1,"stackadapt":1,"stakingsmile":1,"statcounter":1,"steadfastseat":1,"steadfastsound":1,"steadfastsystem":1,"steelhousemedia":1,"steepsquirrel":1,"stereotypedsugar":1,"stickyadstv":1,"stiffgame":1,"stingycrush":1,"straightnest":1,"stripchat":1,"strivesquirrel":1,"strokesystem":1,"stupendoussleet":1,"stupendoussnow":1,"stupidscene":1,"sulkycook":1,"sumo":1,"sumologic":1,"sundaysky":1,"superficialeyes":1,"superficialsquare":1,"surveymonkey":1,"survicate":1,"svonm":1,"swankysquare":1,"symantec":1,"taboola":1,"tailtarget":1,"talkable":1,"tamgrt":1,"tangycover":1,"taobao":1,"tapad":1,"tapioni":1,"taptapnetworks":1,"taskanalytics":1,"tealiumiq":1,"techlab-cdn":1,"technoratimedia":1,"techtarget":1,"tediousticket":1,"teenytinyshirt":1,"tendertest":1,"the-ozone-project":1,"theadex":1,"themoneytizer":1,"theplatform":1,"thestar":1,"thinkitten":1,"threetruck":1,"thrtle":1,"tidaltv":1,"tidiochat":1,"tiktok":1,"tinypass":1,"tiqcdn":1,"tiresomethunder":1,"trackjs":1,"traffichaus":1,"trafficjunky":1,"trafmag":1,"travelaudience":1,"treasuredata":1,"tremorhub":1,"trendemon":1,"tribalfusion":1,"trovit":1,"trueleadid":1,"truoptik":1,"truste":1,"trustpilot":1,"trvdp":1,"tsyndicate":1,"tubemogul":1,"turn":1,"tvpixel":1,"tvsquared":1,"tweakwise":1,"twitter":1,"tynt":1,"typicalteeth":1,"u5e":1,"ubembed":1,"uidapi":1,"ultraoranges":1,"unbecominglamp":1,"unbxdapi":1,"undertone":1,"uninterestedquarter":1,"unpkg":1,"unrulymedia":1,"unwieldyhealth":1,"unwieldyplastic":1,"upsellit":1,"urbanairship":1,"usabilla":1,"usbrowserspeed":1,"usemessages":1,"userreport":1,"uservoice":1,"valuecommerce":1,"vengefulgrass":1,"vidazoo":1,"videoplayerhub":1,"vidoomy":1,"viglink":1,"visualwebsiteoptimizer":1,"vivaclix":1,"vk":1,"vlitag":1,"voicefive":1,"volatilevessel":1,"voraciousgrip":1,"voxmedia":1,"vrtcal":1,"w3counter":1,"walkme":1,"warmafterthought":1,"warmquiver":1,"webcontentassessor":1,"webengage":1,"webeyez":1,"webtraxs":1,"webtrends-optimize":1,"webtrends":1,"wgplayer":1,"woosmap":1,"worldoftulo":1,"wpadmngr":1,"wpshsdk":1,"wpushsdk":1,"wsod":1,"wt-safetag":1,"wysistat":1,"xg4ken":1,"xiti":1,"xlirdr":1,"xlivrdr":1,"xnxx-cdn":1,"y-track":1,"yahoo":1,"yandex":1,"yieldmo":1,"yieldoptimizer":1,"yimg":1,"yotpo":1,"yottaa":1,"youtube-nocookie":1,"youtube":1,"zemanta":1,"zendesk":1,"zeotap":1,"zestycrime":1,"zonos":1,"zoominfo":1,"createsend1":1,"veoxa":1,"parchedsofa":1,"sooqr":1,"adtraction":1,"addthisedge":1,"adsymptotic":1,"bootstrapcdn":1,"bugsnag":1,"dmxleo":1,"dtssrv":1,"fontawesome":1,"hs-scripts":1,"jwpltx":1,"nereserv":1,"onaudience":1,"outbrainimg":1,"quantcount":1,"rtactivate":1,"shopifysvc":1,"stripe":1,"twimg":1,"vimeo":1,"vimeocdn":1,"wp":1,"4jnzhl0d0":1,"aboardamusement":1,"absorbingcorn":1,"abstractedamount":1,"acceptableauthority":1,"actoramusement":1,"actuallysnake":1,"actuallything":1,"adamantsnail":1,"adorableanger":1,"adventurousamount":1,"agreeablearch":1,"agreeabletouch":1,"aheadday":1,"aliasanvil":1,"ambiguousdinosaurs":1,"amusedbucket":1,"ancientact":1,"annoyingacoustics":1,"aquaticowl":1,"aspiringapples":1,"astonishingfood":1,"audioarctic":1,"automaticturkey":1,"awarealley":1,"awesomeagreement":1,"awzbijw":1,"baitbaseball":1,"balloonbelieve":1,"barbarousbase":1,"basketballbelieve":1,"beamvolcano":1,"begintrain":1,"bestboundary":1,"bikesboard":1,"blackbrake":1,"bleachbubble":1,"blushingbeast":1,"boredcrown":1,"boundarybusiness":1,"boundlessveil":1,"brainynut":1,"bravecalculator":1,"breadbalance":1,"breakfastboat":1,"broadborder":1,"brotherslocket":1,"buildingknife":1,"bulbbait":1,"burnbubble":1,"bushesbag":1,"bustlingbath":1,"bustlingbook":1,"calculatingcircle":1,"callousbrake":1,"calmcactus":1,"capriciouscorn":1,"carefuldolls":1,"caringcast":1,"cartkitten":1,"catschickens":1,"causecherry":1,"cautiouscamera":1,"cautiouscherries":1,"cautiouscredit":1,"ceciliavenus":1,"chalkoil":1,"charmingplate":1,"childlikeexample":1,"chinsnakes":1,"chunkycactus":1,"cloisteredcord":1,"closedcows":1,"coldbalance":1,"colossalclouds":1,"colossalcoat":1,"combcattle":1,"combcompetition":1,"comfortablecheese":1,"concernedchickens":1,"condemnedcomb":1,"conditioncrush":1,"confesschairs":1,"consciouscheese":1,"consciousdirt":1,"courageousbaby":1,"coverapparatus":1,"cozyhillside":1,"crimsonmeadow":1,"critictruck":1,"crookedcreature":1,"crystalboulevard":1,"cubchannel":1,"currentcollar":1,"curvycry":1,"cushionpig":1,"damagedadvice":1,"damageddistance":1,"daughterstone":1,"dazzlingbook":1,"debonairdust":1,"decisivedrawer":1,"decisiveducks":1,"deerbeginner":1,"delicatecascade":1,"detailedkitten":1,"digestiondrawer":1,"diplomahawaii":1,"discreetquarter":1,"dk4ywix":1,"dollardelta":1,"dq95d35":1,"dustydime":1,"elasticchange":1,"elderlybean":1,"endurablebulb":1,"energeticladybug":1,"entertainskin":1,"equablekettle":1,"ethereallagoon":1,"evanescentedge":1,"eventexistence":1,"exampleshake":1,"excitingtub":1,"exhibitsneeze":1,"exuberantedge":1,"fadedsnow":1,"fancyactivity":1,"farshake":1,"farsnails":1,"fastenfather":1,"fatcoil":1,"faucetfoot":1,"faultycanvas":1,"fearfulmint":1,"fewjuice":1,"fewkittens":1,"firstfrogs":1,"flimsycircle":1,"flimsythought":1,"flowerycreature":1,"floweryfact":1,"followborder":1,"forgetfulsnail":1,"franticroof":1,"friendwool":1,"fumblingform":1,"furryfork":1,"futuristicfifth":1,"futuristicframe":1,"fuzzyerror":1,"gaudyairplane":1,"giddycoat":1,"givevacation":1,"gleamingcow":1,"glisteningguide":1,"gondolagnome":1,"grandfatherguitar":1,"grayoranges":1,"grayreceipt":1,"grouchypush":1,"grumpydime":1,"guardeddirection":1,"guidecent":1,"gulliblegrip":1,"gustygrandmother":1,"hallowedinvention":1,"haltinggold":1,"handsomehose":1,"handyfield":1,"handyfireman":1,"haplessland":1,"hatefulrequest":1,"headydegree":1,"heartbreakingmind":1,"hearthorn":1,"heavyplayground":1,"historicalbeam":1,"honeybulb":1,"horsenectar":1,"hospitablehall":1,"hospitablehat":1,"hystericalcloth":1,"illinvention":1,"impossibleexpansion":1,"impulsejewel":1,"incompetentjoke":1,"inputicicle":1,"inquisitiveice":1,"internalsink":1,"jubilantcanyon":1,"kaputquill":1,"knitstamp":1,"lameletters":1,"largebrass":1,"leftliquid":1,"lightenafterthought":1,"livelumber":1,"livelylaugh":1,"livelyreward":1,"livingsleet":1,"lizardslaugh":1,"lonelyflavor":1,"longingtrees":1,"lorenzourban":1,"losslace":1,"ludicrousarch":1,"lumpylumber":1,"maliciousmusic":1,"marketspiders":1,"materialisticmoon":1,"materialplayground":1,"meatydime":1,"meltmilk":1,"memorizeneck":1,"merequartz":1,"mightyspiders":1,"mixedreading":1,"modularmental":1,"moorshoes":1,"motionlessbag":1,"motionlessmeeting":1,"movemeal":1,"mundanenail":1,"muteknife":1,"neatshade":1,"needlessnorth":1,"nightwound":1,"nondescriptcrowd":1,"nostalgicneed":1,"nuttyorganization":1,"oafishchance":1,"obscenesidewalk":1,"operationchicken":1,"optimallimit":1,"outstandingincome":1,"outstandingsnails":1,"painstakingpickle":1,"pamelarandom":1,"panickycurtain":1,"parentpicture":1,"passivepolo":1,"peacefullimit":1,"petiteumbrella":1,"planebasin":1,"plantdigestion":1,"poeticpackage":1,"pointlesspocket":1,"politeplanes":1,"politicalporter":1,"powderjourney":1,"protestcopy":1,"puffypurpose":1,"pumpedpancake":1,"punyplant":1,"quillkick":1,"quirkysugar":1,"rabbitbreath":1,"rabbitrifle":1,"raintwig":1,"rainyhand":1,"rainyrule":1,"rangecake":1,"raresummer":1,"readymoon":1,"rebelhen":1,"rebelsubway":1,"receptivereaction":1,"recessrain":1,"regularplants":1,"regulatesleet":1,"replaceroute":1,"resonantbrush":1,"respectrain":1,"retrievemint":1,"rhetoricalveil":1,"richstring":1,"rigidrobin":1,"roofrelation":1,"roseincome":1,"rusticprice":1,"sadloaf":1,"samesticks":1,"samplesamba":1,"scaredcomfort":1,"scaredsnake":1,"scarefowl":1,"scatteredstream":1,"scientificshirt":1,"scintillatingscissors":1,"scissorsstatement":1,"scrapesleep":1,"screechingfurniture":1,"seashoresociety":1,"secondhandfall":1,"secretturtle":1,"separatesort":1,"serioussuit":1,"serpentshampoo":1,"settleshoes":1,"shakyseat":1,"shakysurprise":1,"shallowblade":1,"shesubscriptions":1,"shirtsidewalk":1,"shiveringspot":1,"shiverscissors":1,"shockingship":1,"sillyscrew":1,"simulateswing":1,"sincerebuffalo":1,"sinceresubstance":1,"sinkbooks":1,"sixscissors":1,"slinksuggestion":1,"smilingswim":1,"smoggysongs":1,"soggysponge":1,"somberscarecrow":1,"sombersticks":1,"sordidsmile":1,"soretrain":1,"sortsail":1,"sortsummer":1,"spellsalsa":1,"spotlessstamp":1,"spottednoise":1,"stakingbasket":1,"stakingshock":1,"stalesummer":1,"steadycopper":1,"stealsteel":1,"stepplane":1,"stereoproxy":1,"stimulatingsneeze":1,"stingyshoe":1,"stingyspoon":1,"stomachscience":1,"strangeclocks":1,"strangersponge":1,"strangesink":1,"stretchsister":1,"stretchsneeze":1,"stretchsquirrel":1,"strivesidewalk":1,"succeedscene":1,"sugarfriction":1,"suggestionbridge":1,"superficialspring":1,"supportwaves":1,"suspectmark":1,"swellstocking":1,"swelteringsleep":1,"swingslip":1,"synonymousrule":1,"synonymoussticks":1,"tangyamount":1,"tastelesstrees":1,"tastelesstrucks":1,"teenytinycellar":1,"teenytinytongue":1,"tempertrick":1,"temptteam":1,"terriblethumb":1,"terrifictooth":1,"thirdrespect":1,"thomastorch":1,"thoughtlessknot":1,"ticketaunt":1,"tidymitten":1,"tiredthroat":1,"tranquilcanyon":1,"tremendousearthquake":1,"tremendousplastic":1,"tritebadge":1,"troubledtail":1,"truculentrate":1,"tumbleicicle":1,"typicalairplane":1,"ubiquitousyard":1,"unablehope":1,"unaccountablepie":1,"unbecominghall":1,"uncoveredexpert":1,"unequalbrake":1,"unequaltrail":1,"unknowncrate":1,"untidyrice":1,"unusedstone":1,"uselesslumber":1,"venusgloria":1,"verdantanswer":1,"verseballs":1,"virtualvincent":1,"wantingwindow":1,"wearbasin":1,"wellgroomedhydrant":1,"whispermeeting":1,"wildcommittee":1,"workoperation":1,"zipperxray":1},"net":{"2mdn":1,"2o7":1,"3gl":1,"a-mo":1,"acint":1,"adform":1,"adhigh":1,"admixer":1,"adobedc":1,"adspeed":1,"adverticum":1,"apicit":1,"appier":1,"akamaized":{"assets-momentum":1},"aticdn":1,"edgekey":{"au":1,"ca":1,"ch":1,"cn":1,"com-v1":1,"es":1,"ihg":1,"in":1,"io":1,"it":1,"jp":1,"net":1,"org":1,"com":{"scene7":1},"uk-v1":1,"uk":1},"azure":1,"azurefd":1,"bannerflow":1,"bf-tools":1,"bidswitch":1,"bitsngo":1,"blueconic":1,"boldapps":1,"buysellads":1,"cachefly":1,"cedexis":1,"certona":1,"confiant-integrations":1,"contentsquare":1,"criteo":1,"crwdcntrl":1,"cloudfront":{"d1af033869koo7":1,"d1cr9zxt7u0sgu":1,"d1s87id6169zda":1,"d1vg5xiq7qffdj":1,"d1y068gyog18cq":1,"d214hhm15p4t1d":1,"d21gpk1vhmjuf5":1,"d2zah9y47r7bi2":1,"d38b8me95wjkbc":1,"d38xvr37kwwhcm":1,"d3fv2pqyjay52z":1,"d3i4yxtzktqr9n":1,"d3odp2r1osuwn0":1,"d5yoctgpv4cpx":1,"d6tizftlrpuof":1,"dbukjj6eu5tsf":1,"dn0qt3r0xannq":1,"dsh7ky7308k4b":1,"d2g3ekl4mwm40k":1},"demdex":1,"dotmetrics":1,"doubleclick":1,"durationmedia":1,"e-planning":1,"edgecastcdn":1,"emsecure":1,"episerver":1,"esm1":1,"eulerian":1,"everestjs":1,"everesttech":1,"eyeota":1,"ezoic":1,"fastly":{"global":{"shared":{"f2":1},"sni":{"j":1}},"map":{"prisa-us-eu":1,"scribd":1},"ssl":{"global":{"qognvtzku-x":1}}},"facebook":1,"fastclick":1,"fonts":1,"azureedge":{"fp-cdn":1,"sdtagging":1},"fuseplatform":1,"fwmrm":1,"go-mpulse":1,"hadronid":1,"hs-analytics":1,"hsleadflows":1,"im-apps":1,"impervadns":1,"iocnt":1,"iprom":1,"jsdelivr":1,"kanade-ad":1,"krxd":1,"line-scdn":1,"listhub":1,"livecom":1,"livedoor":1,"liveperson":1,"lkqd":1,"llnwd":1,"lpsnmedia":1,"magnetmail":1,"marketo":1,"maxymiser":1,"media":1,"microad":1,"mobon":1,"monetate":1,"mxptint":1,"myfonts":1,"myvisualiq":1,"naver":1,"nr-data":1,"ojrq":1,"omtrdc":1,"onecount":1,"online-metrix":1,"openx":1,"openxcdn":1,"opta":1,"owneriq":1,"pages02":1,"pages03":1,"pages04":1,"pages05":1,"pages06":1,"pages08":1,"pingdom":1,"pmdstatic":1,"popads":1,"popcash":1,"primecaster":1,"pro-market":1,"akamaihd":{"pxlclnmdecom-a":1},"rfihub":1,"sancdn":1,"sc-static":1,"semasio":1,"sensic":1,"sexad":1,"smaato":1,"spreadshirts":1,"storygize":1,"tfaforms":1,"trackcmp":1,"trackedlink":1,"tradetracker":1,"truste-svc":1,"uuidksinc":1,"viafoura":1,"visilabs":1,"visx":1,"w55c":1,"wdsvc":1,"witglobal":1,"yandex":1,"yastatic":1,"yieldlab":1,"zencdn":1,"zucks":1,"opencmp":1,"azurewebsites":{"app-fnsp-matomo-analytics-prod":1},"ad-delivery":1,"chartbeat":1,"msecnd":1,"cloudfunctions":{"us-central1-adaptive-growth":1},"eviltracker":1},"co":{"6sc":1,"ayads":1,"getlasso":1,"idio":1,"increasingly":1,"jads":1,"nanorep":1,"nc0":1,"pcdn":1,"prmutv":1,"resetdigital":1,"t":1,"tctm":1,"zip":1},"gt":{"ad":1},"ru":{"adfox":1,"adriver":1,"digitaltarget":1,"mail":1,"mindbox":1,"rambler":1,"rutarget":1,"sape":1,"smi2":1,"tns-counter":1,"top100":1,"ulogin":1,"yandex":1,"yadro":1},"jp":{"adingo":1,"admatrix":1,"auone":1,"co":{"dmm":1,"i-mobile":1,"rakuten":1,"yahoo":1},"fout":1,"genieesspv":1,"gmossp-sp":1,"gsspat":1,"gssprt":1,"ne":{"hatena":1},"i2i":1,"impact-ad":1,"microad":1,"nakanohito":1,"r10s":1,"reemo-ad":1,"rtoaster":1,"shinobi":1,"team-rec":1,"uncn":1,"yimg":1,"yjtag":1},"pl":{"adocean":1,"gemius":1,"nsaudience":1,"onet":1,"salesmanago":1,"wp":1},"pro":{"adpartner":1,"piwik":1,"usocial":1},"de":{"adscale":1,"auswaertiges-amt":1,"fiduciagad":1,"ioam":1,"itzbund":1,"vgwort":1,"werk21system":1},"re":{"adsco":1},"info":{"adxbid":1,"bitrix":1,"navistechnologies":1,"usergram":1,"webantenna":1},"tv":{"affec":1,"attn":1,"iris":1,"ispot":1,"samba":1,"teads":1,"twitch":1,"videohub":1},"dev":{"amazon":1},"us":{"amung":1,"samplicio":1,"slgnt":1,"trkn":1},"media":{"andbeyond":1,"nextday":1,"townsquare":1,"underdog":1},"link":{"app":1},"cloud":{"avct":1,"egain":1,"matomo":1},"delivery":{"ay":1,"monu":1},"ly":{"bit":1},"br":{"com":{"btg360":1,"clearsale":1,"jsuol":1,"shopconvert":1,"shoptarget":1,"soclminer":1},"org":{"ivcbrasil":1}},"ch":{"ch":1,"da-services":1,"google":1},"me":{"channel":1,"contentexchange":1,"grow":1,"line":1,"loopme":1,"t":1},"ms":{"clarity":1},"my":{"cnt":1},"se":{"codigo":1},"to":{"cpx":1,"tawk":1},"chat":{"crisp":1,"gorgias":1},"fr":{"d-bi":1,"open-system":1,"weborama":1},"uk":{"co":{"dailymail":1,"hsbc":1}},"gov":{"dhs":1},"ai":{"e-volution":1,"hybrid":1,"m2":1,"nrich":1,"wknd":1},"be":{"geoedge":1},"au":{"com":{"google":1,"news":1,"nine":1,"zipmoney":1,"telstra":1}},"stream":{"ibclick":1},"cz":{"imedia":1,"seznam":1,"trackad":1},"app":{"infusionsoft":1,"permutive":1,"shop":1},"tech":{"ingage":1,"primis":1},"eu":{"kameleoon":1,"medallia":1,"media01":1,"ocdn":1,"rqtrk":1,"slgnt":1},"fi":{"kesko":1,"simpli":1},"live":{"lura":1},"services":{"marketingautomation":1},"sg":{"mediacorp":1},"bi":{"newsroom":1},"fm":{"pdst":1},"ad":{"pixel":1},"xyz":{"playground":1},"it":{"plug":1,"repstatic":1},"cc":{"popin":1},"network":{"pub":1},"nl":{"rijksoverheid":1},"fyi":{"sda":1},"es":{"socy":1},"im":{"spot":1},"market":{"spotim":1},"am":{"tru":1},"no":{"uio":1,"medietall":1},"at":{"waust":1},"pe":{"shop":1},"ca":{"bc":{"gov":1}},"gg":{"clean":1},"example":{"ad-company":1},"site":{"ad-company":1,"third-party":{"bad":1,"broken":1}},"pw":{"zlp6s":1}};
        output.bundledConfig = data;

        return output
    }

    /**
     * Retutns a list of enabled features
     * @param {RemoteConfig} data
     * @param {string | null} topLevelHostname
     * @param {Platform['version']} platformVersion
     * @param {string[]} platformSpecificFeatures
     * @returns {string[]}
     */
    function computeEnabledFeatures (data, topLevelHostname, platformVersion, platformSpecificFeatures = []) {
        const remoteFeatureNames = Object.keys(data.features);
        const platformSpecificFeaturesNotInRemoteConfig = platformSpecificFeatures.filter((featureName) => !remoteFeatureNames.includes(featureName));
        const enabledFeatures = remoteFeatureNames.filter((featureName) => {
            const feature = data.features[featureName];
            // Check that the platform supports minSupportedVersion checks and that the feature has a minSupportedVersion
            if (feature.minSupportedVersion && platformVersion) {
                if (!isSupportedVersion(feature.minSupportedVersion, platformVersion)) {
                    return false
                }
            }
            return feature.state === 'enabled' && !isUnprotectedDomain(topLevelHostname, feature.exceptions)
        }).concat(platformSpecificFeaturesNotInRemoteConfig); // only disable platform specific features if it's explicitly disabled in remote config
        return enabledFeatures
    }

    /**
     * Returns the relevant feature settings for the enabled features
     * @param {RemoteConfig} data
     * @param {string[]} enabledFeatures
     * @returns {Record<string, unknown>}
     */
    function parseFeatureSettings (data, enabledFeatures) {
        /** @type {Record<string, unknown>} */
        const featureSettings = {};
        const remoteFeatureNames = Object.keys(data.features);
        remoteFeatureNames.forEach((featureName) => {
            if (!enabledFeatures.includes(featureName)) {
                return
            }

            featureSettings[featureName] = data.features[featureName].settings;
        });
        return featureSettings
    }

    function isGloballyDisabled (args) {
        return args.site.allowlisted || args.site.isBroken
    }

    const windowsSpecificFeatures = ['windowsPermissionUsage'];

    function isWindowsSpecificFeature (featureName) {
        return windowsSpecificFeatures.includes(featureName)
    }

    function createCustomEvent (eventName, eventDetail) {

        // @ts-expect-error - possibly null
        return new OriginalCustomEvent(eventName, eventDetail)
    }

    /** @deprecated */
    function legacySendMessage (messageType, options) {
        // FF & Chrome
        return originalWindowDispatchEvent && originalWindowDispatchEvent(createCustomEvent('sendMessageProxy' + messageSecret, { detail: { messageType, options } }))
        // TBD other platforms
    }

    const baseFeatures = /** @type {const} */([
        'runtimeChecks',
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
        'exceptionHandler'
    ]);

    const otherFeatures = /** @type {const} */([
        'clickToLoad',
        'cookie',
        'duckPlayer',
        'harmfulApis',
        'webCompat',
        'windowsPermissionUsage'
    ]);

    /** @typedef {baseFeatures[number]|otherFeatures[number]} FeatureName */
    /** @type {Record<string, FeatureName[]>} */
    const platformSupport = {
        apple: [
            'webCompat',
            ...baseFeatures
        ],
        'apple-isolated': [
            'duckPlayer'
        ],
        android: [
            ...baseFeatures,
            'webCompat',
            'clickToLoad'
        ],
        windows: [
            'cookie',
            ...baseFeatures,
            'windowsPermissionUsage',
            'duckPlayer'
        ],
        firefox: [
            'cookie',
            ...baseFeatures,
            'clickToLoad'
        ],
        chrome: [
            'cookie',
            ...baseFeatures,
            'clickToLoad'
        ],
        'chrome-mv3': [
            'cookie',
            ...baseFeatures,
            'clickToLoad'
        ],
        integration: [
            ...baseFeatures,
            ...otherFeatures
        ]
    };

    /**
     * Performance monitor, holds reference to PerformanceMark instances.
     */
    class PerformanceMonitor {
        constructor () {
            this.marks = [];
        }

        /**
         * Create performance marker
         * @param {string} name
         * @returns {PerformanceMark}
         */
        mark (name) {
            const mark = new PerformanceMark(name);
            this.marks.push(mark);
            return mark
        }

        /**
         * Measure all performance markers
         */
        measureAll () {
            this.marks.forEach((mark) => {
                mark.measure();
            });
        }
    }

    /**
     * Tiny wrapper around performance.mark and performance.measure
     */
    class PerformanceMark {
        /**
         * @param {string} name
         */
        constructor (name) {
            this.name = name;
            performance.mark(this.name + 'Start');
        }

        end () {
            performance.mark(this.name + 'End');
        }

        measure () {
            performance.measure(this.name, this.name + 'Start', this.name + 'End');
        }
    }

    function _typeof$2(obj) { "@babel/helpers - typeof"; return _typeof$2 = "function" == typeof Symbol && "symbol" == typeof Symbol.iterator ? function (obj) { return typeof obj; } : function (obj) { return obj && "function" == typeof Symbol && obj.constructor === Symbol && obj !== Symbol.prototype ? "symbol" : typeof obj; }, _typeof$2(obj); }
    function isJSONArray(value) {
      return Array.isArray(value);
    }
    function isJSONObject(value) {
      return value !== null && _typeof$2(value) === 'object' && value.constructor === Object // do not match on classes or Array
      ;
    }

    function _typeof$1(obj) { "@babel/helpers - typeof"; return _typeof$1 = "function" == typeof Symbol && "symbol" == typeof Symbol.iterator ? function (obj) { return typeof obj; } : function (obj) { return obj && "function" == typeof Symbol && obj.constructor === Symbol && obj !== Symbol.prototype ? "symbol" : typeof obj; }, _typeof$1(obj); }
    /**
     * Test deep equality of two JSON values, objects, or arrays
     */ // TODO: write unit tests
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
      return _typeof$1(value) === 'object' && value !== null;
    }

    function _typeof(obj) { "@babel/helpers - typeof"; return _typeof = "function" == typeof Symbol && "symbol" == typeof Symbol.iterator ? function (obj) { return typeof obj; } : function (obj) { return obj && "function" == typeof Symbol && obj.constructor === Symbol && obj !== Symbol.prototype ? "symbol" : typeof obj; }, _typeof(obj); }
    function ownKeys(object, enumerableOnly) { var keys = Object.keys(object); if (Object.getOwnPropertySymbols) { var symbols = Object.getOwnPropertySymbols(object); enumerableOnly && (symbols = symbols.filter(function (sym) { return Object.getOwnPropertyDescriptor(object, sym).enumerable; })), keys.push.apply(keys, symbols); } return keys; }
    function _objectSpread(target) { for (var i = 1; i < arguments.length; i++) { var source = null != arguments[i] ? arguments[i] : {}; i % 2 ? ownKeys(Object(source), !0).forEach(function (key) { _defineProperty(target, key, source[key]); }) : Object.getOwnPropertyDescriptors ? Object.defineProperties(target, Object.getOwnPropertyDescriptors(source)) : ownKeys(Object(source)).forEach(function (key) { Object.defineProperty(target, key, Object.getOwnPropertyDescriptor(source, key)); }); } return target; }
    function _defineProperty(obj, key, value) { key = _toPropertyKey(key); if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }
    function _toPropertyKey(arg) { var key = _toPrimitive(arg, "string"); return _typeof(key) === "symbol" ? key : String(key); }
    function _toPrimitive(input, hint) { if (_typeof(input) !== "object" || input === null) return input; var prim = input[Symbol.toPrimitive]; if (prim !== undefined) { var res = prim.call(input, hint || "default"); if (_typeof(res) !== "object") return res; throw new TypeError("@@toPrimitive must return a primitive value."); } return (hint === "string" ? String : Number)(input); }

    /**
     * Shallow clone of an Object, Array, or value
     * Symbols are cloned too.
     */
    function shallowClone(value) {
      if (isJSONArray(value)) {
        // copy array items
        var copy = value.slice();

        // copy all symbols
        Object.getOwnPropertySymbols(value).forEach(function (symbol) {
          // eslint-disable-next-line @typescript-eslint/ban-ts-comment
          // @ts-ignore
          copy[symbol] = value[symbol];
        });
        return copy;
      } else if (isJSONObject(value)) {
        // copy object properties
        var _copy = _objectSpread({}, value);

        // copy all symbols
        Object.getOwnPropertySymbols(value).forEach(function (symbol) {
          // eslint-disable-next-line @typescript-eslint/ban-ts-comment
          // @ts-ignore
          _copy[symbol] = value[symbol];
        });
        return _copy;
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
        var updatedObject = shallowClone(object);
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
      var value = object;
      var i = 0;
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
      var createPath = arguments.length > 3 && arguments[3] !== undefined ? arguments[3] : false;
      if (path.length === 0) {
        return value;
      }
      var key = path[0];
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      var updatedValue = setIn(object ? object[key] : undefined, path.slice(1), value, createPath);
      if (isJSONObject(object) || isJSONArray(object)) {
        return applyProp(object, key, updatedValue);
      } else {
        if (createPath) {
          var newObject = IS_INTEGER_REGEX.test(key) ? [] : {};
          // eslint-disable-next-line @typescript-eslint/ban-ts-comment
          // @ts-ignore
          newObject[key] = updatedValue;
          return newObject;
        } else {
          throw new Error('Path does not exist');
        }
      }
    }
    var IS_INTEGER_REGEX = /^\d+$/;

    /**
     * helper function to replace a nested property in an object with a new value
     * without mutating the object itself.
     *
     * @return  Returns a new, updated object or array
     */
    function updateIn(object, path, callback) {
      if (path.length === 0) {
        return callback(object);
      }
      if (!isObjectOrArray(object)) {
        throw new Error('Path doesn\'t exist');
      }
      var key = path[0];
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      var updatedValue = updateIn(object[key], path.slice(1), callback);
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
        var _key = path[0];
        if (!(_key in object)) {
          // key doesn't exist. return object unchanged
          return object;
        } else {
          var updatedObject = shallowClone(object);
          if (isJSONArray(updatedObject)) {
            updatedObject.splice(parseInt(_key), 1);
          }
          if (isJSONObject(updatedObject)) {
            delete updatedObject[_key];
          }
          return updatedObject;
        }
      }
      var key = path[0];
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      var updatedValue = deleteIn(object[key], path.slice(1));
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
      var parentPath = path.slice(0, path.length - 1);
      var index = path[path.length - 1];
      return updateIn(document, parentPath, function (items) {
        if (!Array.isArray(items)) {
          throw new TypeError('Array expected at path ' + JSON.stringify(parentPath));
        }
        var updatedItems = shallowClone(items);
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
      var path = pointer.split('/');
      path.shift(); // remove the first empty entry

      return path.map(function (p) {
        return p.replace(/~1/g, '/').replace(/~0/g, '~');
      });
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
      var updatedDocument = document;
      for (var i = 0; i < operations.length; i++) {
        validateJSONPatchOperation(operations[i]);
        var operation = operations[i];

        // TODO: test before
        if (options && options.before) {
          var result = options.before(updatedDocument, operation);
          if (result !== undefined) {
            if (result.document !== undefined) {
              updatedDocument = result.document;
            }
            // eslint-disable-next-line @typescript-eslint/ban-ts-comment
            // @ts-ignore
            if (result.json !== undefined) {
              // TODO: deprecated since v5.0.0. Cleanup this warning some day
              throw new Error('Deprecation warning: returned object property ".json" has been renamed to ".document"');
            }
            if (result.operation !== undefined) {
              operation = result.operation;
            }
          }
        }
        var previousDocument = updatedDocument;
        var path = parsePath(updatedDocument, operation.path);
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

        // TODO: test after
        if (options && options.after) {
          var _result = options.after(updatedDocument, operation, previousDocument);
          if (_result !== undefined) {
            updatedDocument = _result;
          }
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
      var value = getIn(document, from);
      if (isArrayItem(document, path)) {
        return insertAt(document, path, value);
      } else {
        var _value = getIn(document, from);
        return setIn(document, path, _value);
      }
    }

    /**
     * Move a value
     */
    function move(document, path, from) {
      var value = getIn(document, from);
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-ignore
      var removedJson = deleteIn(document, from);
      return isArrayItem(removedJson, path) ? insertAt(removedJson, path, value) : setIn(removedJson, path, value);
    }

    /**
     * Test whether the data contains the provided value at the specified path.
     * Throws an error when the test fails
     */
    function test(document, path, value) {
      if (value === undefined) {
        throw new Error("Test failed: no value provided (path: \"".concat(compileJSONPointer(path), "\")"));
      }
      if (!existsIn(document, path)) {
        throw new Error("Test failed: path not found (path: \"".concat(compileJSONPointer(path), "\")"));
      }
      var actualValue = getIn(document, path);
      if (!isEqual(actualValue, value)) {
        throw new Error("Test failed, value differs (path: \"".concat(compileJSONPointer(path), "\")"));
      }
    }
    function isArrayItem(document, path) {
      if (path.length === 0) {
        return false;
      }
      var parent = getIn(document, initial(path));
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
      var parentPath = initial(path);
      var parent = getIn(document, parentPath);

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
      var ops = ['add', 'remove', 'replace', 'copy', 'move', 'test'];
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

    /* global false */
    // Tests don't define this variable so fallback to behave like chrome
    const functionToString = Function.prototype.toString;

    /**
     * add a fake toString() method to a wrapper function to resemble the original function
     * @param {*} newFn
     * @param {*} origFn
     */
    function wrapToString (newFn, origFn) {
        if (typeof newFn !== 'function' || typeof origFn !== 'function') {
            return
        }
        newFn.toString = function () {
            if (this === newFn) {
                return functionToString.call(origFn)
            } else {
                return functionToString.call(this)
            }
        };
    }

    /**
     * Wrap functions to fix toString but also behave as closely to their real function as possible like .name and .length etc.
     * TODO: validate with firefox non runtimeChecks context and also consolidate with wrapToString
     * @param {*} functionValue
     * @param {*} realTarget
     * @returns {Proxy} a proxy for the function
     */
    function wrapFunction (functionValue, realTarget) {
        return new Proxy(realTarget, {
            get (target, prop, receiver) {
                if (prop === 'toString') {
                    const method = Reflect.get(target, prop, receiver).bind(target);
                    Object.defineProperty(method, 'toString', {
                        value: functionToString.bind(functionToString),
                        enumerable: false
                    });
                    return method
                }
                return Reflect.get(target, prop, receiver)
            },
            apply (target, thisArg, argumentsList) {
                // This is where we call our real function
                return Reflect.apply(functionValue, thisArg, argumentsList)
            }
        })
    }

    /**
     * @description
     *
     * A wrapper for messaging on Windows.
     *
     * This requires 3 methods to be available, see {@link WindowsMessagingConfig} for details
     *
     * @example
     *
     * ```javascript
     * [[include:packages/messaging/lib/examples/windows.example.js]]```
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
        constructor (config, messagingContext) {
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
                if (typeof fn !== 'function') {
                    throw new Error('cannot create WindowsMessagingTransport, missing the method: ' + methodName)
                }
            }
        }

        /**
         * @param {import('../index.js').NotificationMessage} msg
         */
        notify (msg) {
            const data = this.globals.JSONparse(this.globals.JSONstringify(msg.params || {}));
            const notification = WindowsNotification.fromNotification(msg, data);
            this.config.methods.postMessage(notification);
        }

        /**
         * @param {import('../index.js').RequestMessage} msg
         * @param {{signal?: AbortSignal}} opts
         * @return {Promise<any>}
         */
        request (msg, opts = {}) {
            // convert the message to window-specific naming
            const data = this.globals.JSONparse(this.globals.JSONstringify(msg.params || {}));
            const outgoing = WindowsRequestMessage.fromRequest(msg, data);

            // send the message
            this.config.methods.postMessage(outgoing);

            // compare incoming messages against the `msg.id`
            const comparator = (eventData) => {
                return eventData.featureName === msg.featureName &&
                    eventData.context === msg.context &&
                    eventData.id === msg.id
            };

            /**
             * @param data
             * @return {data is import('../index.js').MessageResponse}
             */
            function isMessageResponse (data) {
                if ('result' in data) return true
                if ('error' in data) return true
                return false
            }

            // now wait for a matching message
            return new this.globals.Promise((resolve, reject) => {
                try {
                    this._subscribe(comparator, opts, (value, unsubscribe) => {
                        unsubscribe();

                        if (!isMessageResponse(value)) {
                            console.warn('unknown response type', value);
                            return reject(new this.globals.Error('unknown response'))
                        }

                        if (value.result) {
                            return resolve(value.result)
                        }

                        const message = this.globals.String(value.error?.message || 'unknown error');
                        reject(new this.globals.Error(message));
                    });
                } catch (e) {
                    reject(e);
                }
            })
        }

        /**
         * @param {import('../index.js').Subscription} msg
         * @param {(value: unknown | undefined) => void} callback
         */
        subscribe (msg, callback) {
            // compare incoming messages against the `msg.subscriptionName`
            const comparator = (eventData) => {
                return eventData.featureName === msg.featureName &&
                    eventData.context === msg.context &&
                    eventData.subscriptionName === msg.subscriptionName
            };

            // only forward the 'params' from a SubscriptionEvent
            const cb = (eventData) => {
                return callback(eventData.params)
            };

            // now listen for matching incoming messages.
            return this._subscribe(comparator, {}, cb)
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
        _subscribe (comparator, options, callback) {
            // if already aborted, reject immediately
            if (options?.signal?.aborted) {
                throw new DOMException('Aborted', 'AbortError')
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
                        return
                    }
                }
                if (!event.data) {
                    console.warn('data absent from message');
                    return
                }
                if (comparator(event.data)) {
                    if (!teardown) throw new Error('unreachable')
                    callback(event.data, teardown);
                }
            };

            // what to do if this promise is aborted
            const abortHandler = () => {
                teardown?.();
                throw new DOMException('Aborted', 'AbortError')
            };

            // console.log('DEBUG: handler setup', { config, comparator })
            // eslint-disable-next-line no-undef
            this.config.methods.addEventListener('message', idHandler);
            options?.signal?.addEventListener('abort', abortHandler);

            teardown = () => {
                // console.log('DEBUG: handler teardown', { config, comparator })
                // eslint-disable-next-line no-undef
                this.config.methods.removeEventListener('message', idHandler);
                options?.signal?.removeEventListener('abort', abortHandler);
            };

            return () => {
                teardown?.();
            }
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
     * ```ts
     * [[include:packages/messaging/lib/examples/windows.example.js]]```
     *
     */
    class WindowsMessagingConfig {
        /**
         * @param {object} params
         * @param {WindowsInteropMethods} params.methods
         * @internal
         */
        constructor (params) {
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
        constructor (params) {
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
        static fromNotification (notification, data) {
            /** @type {WindowsNotification} */
            const output = {
                Data: data,
                Feature: notification.context,
                SubFeatureName: notification.featureName,
                Name: notification.method
            };
            return output
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
        constructor (params) {
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
        static fromRequest (msg, data) {
            /** @type {WindowsRequestMessage} */
            const output = {
                Data: data,
                Feature: msg.context,
                SubFeatureName: msg.featureName,
                Name: msg.method,
                Id: msg.id
            };
            return output
        }
    }

    /**
     * @module Messaging Schema
     *
     * @description
     * These are all the shared data types used throughout. Transports receive these types and
     * can choose how to deliver the message to their respective native platforms.
     *
     * - Notifications via {@link NotificationMessage}
     * - Request -> Response via {@link RequestMessage} and {@link MessageResponse}
     * - Subscriptions via {@link Subscription}
     *
     * Note: For backwards compatibility, some platforms may alter the data shape within the transport.
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
        constructor (params) {
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
        constructor (params) {
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
        constructor (params) {
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
    function isResponseFor (request, data) {
        if ('result' in data) {
            return data.featureName === request.featureName &&
                data.context === request.context &&
                data.id === request.id
        }
        if ('error' in data) {
            if ('message' in data.error) {
                return true
            }
        }
        return false
    }

    /**
     * @param {Subscription} sub
     * @param {Record<string, any>} data
     * @return {data is SubscriptionEvent}
     */
    function isSubscriptionEventFor (sub, data) {
        if ('subscriptionName' in data) {
            return data.featureName === sub.featureName &&
                data.context === sub.context &&
                data.subscriptionName === sub.subscriptionName
        }

        return false
    }

    /**
     *
     * @description
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
        constructor (config, messagingContext) {
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
        wkSend (handler, data = {}) {
            if (!(handler in this.globals.window.webkit.messageHandlers)) {
                throw new MissingHandler(`Missing webkit handler: '${handler}'`, handler)
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
                    throw new MissingHandler(`cannot continue, method ${handler} not captured on macos < 11`, handler)
                } else {
                    return this.globals.capturedWebkitHandlers[handler](outgoing)
                }
            }
            return this.globals.window.webkit.messageHandlers[handler].postMessage?.(data)
        }

        /**
         * Sends message to the webkit layer and waits for the specified response
         * @param {String} handler
         * @param {import('../index.js').RequestMessage} data
         * @returns {Promise<*>}
         * @internal
         */
        async wkSendAndWait (handler, data) {
            if (this.config.hasModernWebkitAPI) {
                const response = await this.wkSend(handler, data);
                return this.globals.JSONparse(response || '{}')
            }

            try {
                const randMethodName = this.createRandMethodName();
                const key = await this.createRandKey();
                const iv = this.createRandIv();

                const {
                    ciphertext,
                    tag
                } = await new this.globals.Promise((/** @type {any} */ resolve) => {
                    this.generateRandomMethod(randMethodName, resolve);

                    // @ts-expect-error - this is a carve-out for catalina that will be removed soon
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
                return this.globals.JSONparse(decrypted || '{}')
            } catch (e) {
                // re-throw when the error is just a 'MissingHandler'
                if (e instanceof MissingHandler) {
                    throw e
                } else {
                    console.error('decryption failed', e);
                    console.error(e);
                    return { error: e }
                }
            }
        }

        /**
         * @param {import('../index.js').NotificationMessage} msg
         */
        notify (msg) {
            this.wkSend(msg.context, msg);
        }

        /**
         * @param {import('../index.js').RequestMessage} msg
         */
        async request (msg) {
            const data = await this.wkSendAndWait(msg.context, msg);

            if (isResponseFor(msg, data)) {
                if (data.result) {
                    return data.result || {}
                }
                // forward the error if one was given explicity
                if (data.error) {
                    throw new Error(data.error.message)
                }
            }

            throw new Error('an unknown error occurred')
        }

        /**
         * Generate a random method name and adds it to the global scope
         * The native layer will use this method to send the response
         * @param {string | number} randomMethodName
         * @param {Function} callback
         * @internal
         */
        generateRandomMethod (randomMethodName, callback) {
            this.globals.ObjectDefineProperty(this.globals.window, randomMethodName, {
                enumerable: false,
                // configurable, To allow for deletion later
                configurable: true,
                writable: false,
                /**
                 * @param {any[]} args
                 */
                value: (...args) => {
                    // eslint-disable-next-line n/no-callback-literal
                    callback(...args);
                    delete this.globals.window[randomMethodName];
                }
            });
        }

        /**
         * @internal
         * @return {string}
         */
        randomString () {
            return '' + this.globals.getRandomValues(new this.globals.Uint32Array(1))[0]
        }

        /**
         * @internal
         * @return {string}
         */
        createRandMethodName () {
            return '_' + this.randomString()
        }

        /**
         * @type {{name: string, length: number}}
         * @internal
         */
        algoObj = {
            name: 'AES-GCM',
            length: 256
        }

        /**
         * @returns {Promise<Uint8Array>}
         * @internal
         */
        async createRandKey () {
            const key = await this.globals.generateKey(this.algoObj, true, ['encrypt', 'decrypt']);
            const exportedKey = await this.globals.exportKey('raw', key);
            return new this.globals.Uint8Array(exportedKey)
        }

        /**
         * @returns {Uint8Array}
         * @internal
         */
        createRandIv () {
            return this.globals.getRandomValues(new this.globals.Uint8Array(12))
        }

        /**
         * @param {BufferSource} ciphertext
         * @param {BufferSource} key
         * @param {Uint8Array} iv
         * @returns {Promise<string>}
         * @internal
         */
        async decrypt (ciphertext, key, iv) {
            const cryptoKey = await this.globals.importKey('raw', key, 'AES-GCM', false, ['decrypt']);
            const algo = {
                name: 'AES-GCM',
                iv
            };

            const decrypted = await this.globals.decrypt(algo, cryptoKey, ciphertext);

            const dec = new this.globals.TextDecoder();
            return dec.decode(decrypted)
        }

        /**
         * When required (such as on macos 10.x), capture the `postMessage` method on
         * each webkit messageHandler
         *
         * @param {string[]} handlerNames
         */
        captureWebkitHandlers (handlerNames) {
            const handlers = window.webkit.messageHandlers;
            if (!handlers) throw new MissingHandler('window.webkit.messageHandlers was absent', 'all')
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
        subscribe (msg, callback) {
            // for now, bail if there's already a handler setup for this subscription
            if (msg.subscriptionName in this.globals.window) {
                throw new this.globals.Error(`A subscription with the name ${msg.subscriptionName} already exists`)
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
                }
            });
            return () => {
                this.globals.ReflectDeleteProperty(this.globals.window, msg.subscriptionName);
            }
        }
    }

    /**
     * Use this configuration to create an instance of {@link Messaging} for WebKit platforms
     *
     * We support modern WebKit environments *and* macOS Catalina.
     *
     * Please see {@link WebkitMessagingTransport} for details on how messages are sent/received
     *
     * @example Webkit Messaging
     *
     * ```javascript
     * [[include:packages/messaging/lib/examples/webkit.example.js]]```
     */
    class WebkitMessagingConfig {
        /**
         * @param {object} params
         * @param {boolean} params.hasModernWebkitAPI
         * @param {string[]} params.webkitMessageHandlerNames
         * @param {string} params.secret
         * @internal
         */
        constructor (params) {
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
        constructor (params) {
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
    function captureGlobals () {
        // Create base with null prototype
        return {
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
            Error: window.Error,
            ReflectDeleteProperty: window.Reflect.deleteProperty.bind(window.Reflect),
            ObjectDefineProperty: window.Object.defineProperty,
            addEventListener: window.addEventListener.bind(window),
            /** @type {Record<string, any>} */
            capturedWebkitHandlers: {}
        }
    }

    /**
     * @description
     *
     * A wrapper for messaging on Android.
     *
     * You must share a {@link AndroidMessagingConfig} instance between features
     *
     * @example
     *
     * ```javascript
     * [[include:packages/messaging/lib/examples/windows.example.js]]```
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
        constructor (config, messagingContext) {
            this.messagingContext = messagingContext;
            this.config = config;
        }

        /**
         * @param {NotificationMessage} msg
         */
        notify (msg) {
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
        request (msg) {
            return new Promise((resolve, reject) => {
                // subscribe early
                const unsub = this.config.subscribe(msg.id, handler);

                try {
                    this.config.sendMessageThrows?.(JSON.stringify(msg));
                } catch (e) {
                    unsub();
                    reject(new Error('request failed to send: ' + e.message || 'unknown error'));
                }

                function handler (data) {
                    if (isResponseFor(msg, data)) {
                        // success case, forward .result only
                        if (data.result) {
                            resolve(data.result || {});
                            return unsub()
                        }

                        // error case, forward the error as a regular promise rejection
                        if (data.error) {
                            reject(new Error(data.error.message));
                            return unsub()
                        }

                        // getting here is undefined behavior
                        unsub();
                        throw new Error('unreachable: must have `result` or `error` key by this point')
                    }
                }
            })
        }

        /**
         * @param {Subscription} msg
         * @param {(value: unknown | undefined) => void} callback
         */
        subscribe (msg, callback) {
            const unsub = this.config.subscribe(msg.subscriptionName, (data) => {
                if (isSubscriptionEventFor(msg, data)) {
                    callback(data.params || {});
                }
            });
            return () => {
                unsub();
            }
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
     * ```kotlin
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
        _capturedHandler
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
        constructor (params) {
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
        sendMessageThrows (json) {
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
        subscribe (id, callback) {
            this.listeners.set(id, callback);
            return () => {
                this.listeners.delete(id);
            }
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
        _dispatch (payload) {
            // do nothing if the response is empty
            // this prevents the next `in` checks from throwing in test/debug scenarios
            if (!payload) return this._log('no response')

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
        _tryCatch (fn, context = 'none') {
            try {
                return fn()
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
        _log (...args) {
            if (this.debug) {
                console.log('AndroidMessagingConfig', ...args);
            }
        }

        /**
         * Capture the global handler and remove it from the global object.
         */
        _captureGlobalHandler () {
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
        _assignHandlerMethod () {
            /**
             * @type {(secret: string, response: MessageResponse | SubscriptionEvent) => void}
             */
            const responseHandler = (providedSecret, response) => {
                if (providedSecret === this.messageSecret) {
                    this._dispatch(response);
                }
            };

            Object.defineProperty(this.target, this.messageCallback, {
                value: responseHandler
            });
        }
    }

    /**
     * @module Messaging
     * @category Libraries
     * @description
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
        constructor (params) {
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
        constructor (messagingContext, config) {
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
        notify (name, data = {}) {
            const message = new NotificationMessage({
                context: this.messagingContext.context,
                featureName: this.messagingContext.featureName,
                method: name,
                params: data
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
        request (name, data = {}) {
            const id = globalThis?.crypto?.randomUUID?.() || name + '.response';
            const message = new RequestMessage({
                context: this.messagingContext.context,
                featureName: this.messagingContext.featureName,
                method: name,
                params: data,
                id
            });
            return this.transport.request(message)
        }

        /**
         * @param {string} name
         * @param {(value: unknown) => void} callback
         * @return {() => void}
         */
        subscribe (name, callback) {
            const msg = new Subscription({
                context: this.messagingContext.context,
                featureName: this.messagingContext.featureName,
                subscriptionName: name
            });
            return this.transport.subscribe(msg, callback)
        }
    }

    /**
     * Use this to create testing transport on the fly.
     * It's useful for debugging, and for enabling scripts to run in
     * other environments - for example, testing in a browser without the need
     * for a full integration
     *
     * ```js
     * [[include:packages/messaging/lib/examples/test.example.js]]```
     */
    class TestTransportConfig {
        /**
         * @param {MessagingTransport} impl
         */
        constructor (impl) {
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
        constructor (config, messagingContext) {
            this.config = config;
            this.messagingContext = messagingContext;
        }

        notify (msg) {
            return this.config.impl.notify(msg)
        }

        request (msg) {
            return this.config.impl.request(msg)
        }

        subscribe (msg, callback) {
            return this.config.impl.subscribe(msg, callback)
        }
    }

    /**
     * @param {WebkitMessagingConfig | WindowsMessagingConfig | AndroidMessagingConfig | TestTransportConfig} config
     * @param {MessagingContext} messagingContext
     * @returns {MessagingTransport}
     */
    function getTransport (config, messagingContext) {
        if (config instanceof WebkitMessagingConfig) {
            return new WebkitMessagingTransport(config, messagingContext)
        }
        if (config instanceof WindowsMessagingConfig) {
            return new WindowsMessagingTransport(config, messagingContext)
        }
        if (config instanceof AndroidMessagingConfig) {
            return new AndroidMessagingTransport(config, messagingContext)
        }
        if (config instanceof TestTransportConfig) {
            return new TestTransport(config, messagingContext)
        }
        throw new Error('unreachable')
    }

    /**
     * Thrown when a handler cannot be found
     */
    class MissingHandler extends Error {
        /**
         * @param {string} message
         * @param {string} handlerName
         */
        constructor (message, handlerName) {
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
    function extensionConstructMessagingConfig () {
        const messagingTransport = new SendMessageMessagingTransport();
        return new TestTransportConfig(messagingTransport)
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
        _queue = new Set()

        constructor () {
            this.globals = {
                window: globalThis,
                globalThis,
                JSONparse: globalThis.JSON.parse,
                JSONstringify: globalThis.JSON.stringify,
                Promise: globalThis.Promise,
                Error: globalThis.Error,
                String: globalThis.String
            };
        }

        /**
         * Callback for update() handler. This connects messages sent from the Platform
         * with callback functions in the _queue.
         * @param {any} response
         */
        onResponse (response) {
            this._queue.forEach((subscription) => subscription(response));
        }

        /**
         * @param {import('@duckduckgo/messaging').NotificationMessage} msg
         */
        notify (msg) {
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
        request (req) {
            let comparator = (eventData) => {
                return eventData.responseMessageType === req.method
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
                    )
                };
                params = req.params?.videoURL;
            }

            legacySendMessage(req.method, params);

            return new this.globals.Promise((resolve) => {
                this._subscribe(comparator, (msgRes, unsubscribe) => {
                    unsubscribe();

                    return resolve(msgRes.response)
                });
            })
        }

        /**
         * @param {import('@duckduckgo/messaging').Subscription} msg
         * @param {(value: unknown | undefined) => void} callback
         */
        subscribe (msg, callback) {
            const comparator = (eventData) => {
                return (
                    eventData.messageType === msg.subscriptionName ||
                    eventData.responseMessageType === msg.subscriptionName
                )
            };

            // only forward the 'params' ('response' in current format), to match expected
            // callback from a SubscriptionEvent
            const cb = (eventData) => {
                return callback(eventData.response)
            };
            return this._subscribe(comparator, cb)
        }

        /**
         * @param {(eventData: any) => boolean} comparator
         * @param {(value: any, unsubscribe: (()=>void)) => void} callback
         * @internal
         */
        _subscribe (comparator, callback) {
            /** @type {(()=>void) | undefined} */
            // eslint-disable-next-line prefer-const
            let teardown;

            /**
             * @param {MessageEvent} event
             */
            const idHandler = (event) => {
                if (!event) {
                    console.warn('no message available');
                    return
                }
                if (comparator(event)) {
                    if (!teardown) throw new this.globals.Error('unreachable')
                    callback(event, teardown);
                }
            };
            this._queue.add(idHandler);

            teardown = () => {
                this._queue.delete(idHandler);
            };

            return () => {
                teardown?.();
            }
        }
    }

    /* global cloneInto, exportFunction */


    class ContentFeature {
        /** @type {import('./utils.js').RemoteConfig | undefined} */
        #bundledConfig
        /** @type {object | undefined} */
        #trackerLookup
        /** @type {boolean | undefined} */
        #documentOriginIsTracker
        /** @type {Record<string, unknown> | undefined} */
        #bundledfeatureSettings
        /** @type {import('../packages/messaging').Messaging} */
        #messaging
        /** @type {boolean} */
        #isDebugFlagSet = false

        /** @type {{ debug?: boolean, featureSettings?: Record<string, unknown>, assets?: AssetConfig | undefined, site: Site, messagingConfig?: import('@duckduckgo/messaging').MessagingConfig } | null} */
        #args

        constructor (featureName) {
            this.name = featureName;
            this.#args = null;
            this.monitor = new PerformanceMonitor();
        }

        get isDebug () {
            return this.#args?.debug || false
        }

        /**
         * @param {import('./utils').Platform} platform
         */
        set platform (platform) {
            this._platform = platform;
        }

        get platform () {
            // @ts-expect-error - Type 'Platform | undefined' is not assignable to type 'Platform'
            return this._platform
        }

        /**
         * @type {AssetConfig | undefined}
         */
        get assetConfig () {
            return this.#args?.assets
        }

        /**
         * @returns {boolean}
         */
        get documentOriginIsTracker () {
            return !!this.#documentOriginIsTracker
        }

        /**
         * @returns {object}
         **/
        get trackerLookup () {
            return this.#trackerLookup || {}
        }

        /**
         * @returns {import('./utils.js').RemoteConfig | undefined}
         **/
        get bundledConfig () {
            return this.#bundledConfig
        }

        /**
         * @deprecated as we should make this internal to the class and not used externally
         * @return {MessagingContext}
         */
        _createMessagingContext () {
            const contextName = 'contentScopeScripts';

            return new MessagingContext({
                context: contextName,
                env: this.isDebug ? 'development' : 'production',
                featureName: this.name
            })
        }

        /**
         * Lazily create a messaging instance for the given Platform + feature combo
         *
         * @return {import('@duckduckgo/messaging').Messaging}
         */
        get messaging () {
            if (this._messaging) return this._messaging
            const messagingContext = this._createMessagingContext();
            let messagingConfig = this.#args?.messagingConfig;
            if (!messagingConfig) {
                if (this.platform?.name !== 'extension') throw new Error('Only extension messaging supported, all others should be passed in')
                messagingConfig = extensionConstructMessagingConfig();
            }
            this._messaging = new Messaging(messagingContext, messagingConfig);
            return this._messaging
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
        getFeatureAttr (attrName, defaultValue) {
            const configSetting = this.getFeatureSetting(attrName);
            return processAttr(configSetting, defaultValue)
        }

        /**
         * Return a specific setting from the feature settings
         * @param {string} featureKeyName
         * @param {string} [featureName]
         * @returns {any}
         */
        getFeatureSetting (featureKeyName, featureName) {
            let result = this._getFeatureSettings(featureName);
            if (featureKeyName === 'domains') {
                throw new Error('domains is a reserved feature setting key name')
            }
            const domainMatch = [...this.matchDomainFeatureSetting('domains')].sort((a, b) => {
                return a.domain.length - b.domain.length
            });
            for (const match of domainMatch) {
                if (match.patchSettings === undefined) {
                    continue
                }
                try {
                    result = immutableJSONPatch(result, match.patchSettings);
                } catch (e) {
                    console.error('Error applying patch settings', e);
                }
            }
            return result?.[featureKeyName]
        }

        /**
         * Return the settings object for a feature
         * @param {string} [featureName] - The name of the feature to get the settings for; defaults to the name of the feature
         * @returns {any}
         */
        _getFeatureSettings (featureName) {
            const camelFeatureName = featureName || camelcase(this.name);
            return this.#args?.featureSettings?.[camelFeatureName]
        }

        /**
         * For simple boolean settings, return true if the setting is 'enabled'
         * For objects, verify the 'state' field is 'enabled'.
         * @param {string} featureKeyName
         * @param {string} [featureName]
         * @returns {boolean}
         */
        getFeatureSettingEnabled (featureKeyName, featureName) {
            const result = this.getFeatureSetting(featureKeyName, featureName);
            if (typeof result === 'object') {
                return result.state === 'enabled'
            }
            return result === 'enabled'
        }

        /**
         * Given a config key, interpret the value as a list of domain overrides, and return the elements that match the current page
         * @param {string} featureKeyName
         * @return {any[]}
         */
        matchDomainFeatureSetting (featureKeyName) {
            const domain = this.#args?.site.domain;
            if (!domain) return []
            const domains = this._getFeatureSettings()?.[featureKeyName] || [];
            return domains.filter((rule) => {
                if (Array.isArray(rule.domain)) {
                    return rule.domain.some((domainRule) => {
                        return matchHostname(domain, domainRule)
                    })
                }
                return matchHostname(domain, rule.domain)
            })
        }

        // eslint-disable-next-line @typescript-eslint/no-unused-vars, @typescript-eslint/no-empty-function
        init (args) {
        }

        callInit (args) {
            const mark = this.monitor.mark(this.name + 'CallInit');
            this.#args = args;
            this.platform = args.platform;
            this.init(args);
            mark.end();
            this.measure();
        }

        // eslint-disable-next-line @typescript-eslint/no-unused-vars, @typescript-eslint/no-empty-function
        load (args) {
        }

        /**
         * @param {import('./content-scope-features.js').LoadArgs} args
         */
        callLoad (args) {
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

        measure () {
            if (this.#args?.debug) {
                this.monitor.measureAll();
            }
        }

        // eslint-disable-next-line @typescript-eslint/no-empty-function
        update () {
        }

        /**
         * Register a flag that will be added to page breakage reports
         */
        addDebugFlag () {
            if (this.#isDebugFlagSet) return
            this.#isDebugFlagSet = true;
            this.messaging?.notify('addDebugFlag', {
                flag: this.name
            });
        }

        /**
         * Define a property descriptor. Mainly used for defining new properties. For overriding existing properties, consider using wrapProperty(), wrapMethod() and wrapConstructor().
         * @param {any} object - object whose property we are wrapping (most commonly a prototype)
         * @param {string} propertyName
         * @param {PropertyDescriptor} descriptor
         */
        defineProperty (object, propertyName, descriptor) {
            // make sure to send a debug flag when the property is used
            // NOTE: properties passing data in `value` would not be caught by this
            ['value', 'get', 'set'].forEach((k) => {
                const descriptorProp = descriptor[k];
                if (typeof descriptorProp === 'function') {
                    const addDebugFlag = this.addDebugFlag.bind(this);
                    descriptor[k] = function () {
                        addDebugFlag();
                        return Reflect.apply(descriptorProp, this, arguments)
                    };
                }
            });

            {
                Object.defineProperty(object, propertyName, descriptor);
            }
        }

        /**
         * Wrap a `get`/`set` or `value` property descriptor. Only for data properties. For methods, use wrapMethod(). For constructors, use wrapConstructor().
         * @param {any} object - object whose property we are wrapping (most commonly a prototype)
         * @param {string} propertyName
         * @param {Partial<PropertyDescriptor>} descriptor
         * @returns {PropertyDescriptor|undefined} original property descriptor, or undefined if it's not found
         */
        wrapProperty (object, propertyName, descriptor) {
            if (!object) {
                return
            }

            const origDescriptor = getOwnPropertyDescriptor(object, propertyName);
            if (!origDescriptor) {
                // this happens if the property is not implemented in the browser
                return
            }

            if (('value' in origDescriptor && 'value' in descriptor) ||
                ('get' in origDescriptor && 'get' in descriptor) ||
                ('set' in origDescriptor && 'set' in descriptor)
            ) {
                wrapToString(descriptor.value, origDescriptor.value);
                wrapToString(descriptor.get, origDescriptor.get);
                wrapToString(descriptor.set, origDescriptor.set);

                this.defineProperty(object, propertyName, {
                    ...origDescriptor,
                    ...descriptor
                });
                return origDescriptor
            } else {
                // if the property is defined with get/set it must be wrapped with a get/set. If it's defined with a `value`, it must be wrapped with a `value`
                throw new Error(`Property descriptor for ${propertyName} may only include the following keys: ${objectKeys(origDescriptor)}`)
            }
        }

        /**
         * Wrap a method descriptor. Only for function properties. For data properties, use wrapProperty(). For constructors, use wrapConstructor().
         * @param {any} object - object whose property we are wrapping (most commonly a prototype)
         * @param {string} propertyName
         * @param {(originalFn, ...args) => any } wrapperFn - wrapper function receives the original function as the first argument
         * @returns {PropertyDescriptor|undefined} original property descriptor, or undefined if it's not found
         */
        wrapMethod (object, propertyName, wrapperFn) {
            if (!object) {
                return
            }
            const origDescriptor = getOwnPropertyDescriptor(object, propertyName);
            if (!origDescriptor) {
                // this happens if the property is not implemented in the browser
                return
            }

            const origFn = origDescriptor.value;
            if (!origFn || typeof origFn !== 'function') {
                // method properties are expected to be defined with a `value`
                throw new Error(`Property ${propertyName} does not look like a method`)
            }

            const newFn = function () {
                return wrapperFn.call(this, origFn, ...arguments)
            };
            wrapToString(newFn, origFn);

            this.defineProperty(object, propertyName, {
                ...origDescriptor,
                value: newFn
            });
            return origDescriptor
        }
    }

    function generateUniqueID () {
        return Symbol(undefined)
    }

    function addTaint () {
        const contextID = generateUniqueID();
        if ('duckduckgo' in navigator &&
            navigator.duckduckgo &&
            typeof navigator.duckduckgo === 'object' &&
            'taints' in navigator.duckduckgo &&
            navigator.duckduckgo.taints instanceof Set) {
            if (document.currentScript) {
                // @ts-expect-error - contextID is undefined on currentScript
                document.currentScript.contextID = contextID;
            }
            navigator?.duckduckgo?.taints.add(contextID);
        }
        return contextID
    }

    function createContextAwareFunction (fn) {
        return function (...args) {
            // eslint-disable-next-line @typescript-eslint/no-this-alias
            let scope = this;
            // Save the previous contextID and set the new one
            const prevContextID = this?.contextID;
            // @ts-expect-error - contextID is undefined on window
            // eslint-disable-next-line no-undef
            const changeToContextID = getContextId(this) || contextID;
            if (typeof args[0] === 'function') {
                args[0].contextID = changeToContextID;
            }
            // @ts-expect-error - scope doesn't match window
            if (scope && scope !== globalThis) {
                scope.contextID = changeToContextID;
            } else if (!scope) {
                scope = new Proxy(scope, {
                    get (target, prop) {
                        if (prop === 'contextID') {
                            return changeToContextID
                        }
                        return Reflect.get(target, prop)
                    }
                });
            }
            // Run the original function with the new contextID
            const result = Reflect.apply(fn, scope, args);

            // Restore the previous contextID
            scope.contextID = prevContextID;

            return result
        }
    }

    /**
     * Indent a code block using braces
     * @param {string} string
     * @returns {string}
     */
    function removeIndent (string) {
        const lines = string.split('\n');
        const indentSize = 2;
        let currentIndent = 0;
        const indentedLines = lines.map((line) => {
            if (line.trim().startsWith('}')) {
                currentIndent -= indentSize;
            }
            const indentedLine = ' '.repeat(currentIndent) + line.trim();
            if (line.trim().endsWith('{')) {
                currentIndent += indentSize;
            }

            return indentedLine
        });
        return indentedLines.filter(a => a.trim()).join('\n')
    }

    const lookup = {};
    function getOrGenerateIdentifier (path) {
        if (!(path in lookup)) {
            lookup[path] = generateAlphaIdentifier(Object.keys(lookup).length + 1);
        }
        return lookup[path]
    }

    function generateAlphaIdentifier (num) {
        if (num < 1) {
            throw new Error('Input must be a positive integer')
        }
        const charCodeOffset = 97;
        let identifier = '';
        while (num > 0) {
            num--;
            const remainder = num % 26;
            const charCode = remainder + charCodeOffset;
            identifier = String.fromCharCode(charCode) + identifier;
            num = Math.floor(num / 26);
        }
        return '_ddg_' + identifier
    }

    /**
     * @param {*} scope
     * @param {Record<string, any>} outputs
     * @returns {Proxy}
     */
    function constructProxy (scope, outputs) {
        const taintString = '__ddg_taint__';
        // @ts-expect-error - Expected 2 arguments, but got 1
        if (Object.is(scope)) {
            // Should not happen, but just in case fail safely
            console.error('Runtime checks: Scope must be an object', scope, outputs);
            return scope
        }
        return new Proxy(scope, {
            get (target, property) {
                const targetObj = target[property];
                let targetOut = target;
                if (typeof property === 'string' && property in outputs) {
                    targetOut = outputs;
                }
                // Reflects functions with the correct 'this' scope
                if (typeof targetObj === 'function') {
                    return (...args) => {
                        return Reflect.apply(targetOut[property], target, args)
                    }
                } else {
                    return Reflect.get(targetOut, property, scope)
                }
            },
            getOwnPropertyDescriptor (target, property) {
                if (typeof property === 'string' && property === taintString) {
                    return { configurable: true, enumerable: false, value: true }
                }
                return Reflect.getOwnPropertyDescriptor(target, property)
            }
        })
    }

    function valToString (val) {
        if (typeof val === 'function') {
            return val.toString()
        }
        return JSON.stringify(val)
    }

    /**
     * Output scope variable definitions to arbitrary depth
     */
    function stringifyScope (scope, scopePath) {
        let output = '';
        for (const [key, value] of scope) {
            const varOutName = getOrGenerateIdentifier([...scopePath, key]);
            if (value instanceof Map) {
                const proxyName = getOrGenerateIdentifier(['_proxyFor_', varOutName]);
                output += `
            let ${proxyName} = ${scopePath.join('?.')}?.${key} ? ${scopePath.join('.')}.${key} : Object.bind(null);
            `;
                const keys = Array.from(value.keys());
                output += stringifyScope(value, [...scopePath, key]);
                const proxyOut = keys.map((keyName) => `${keyName}: ${getOrGenerateIdentifier([...scopePath, key, keyName])}`);
                output += `
            let ${varOutName} = constructProxy(${proxyName}, {
                ${proxyOut.join(',\n')}
            });
            `;
                // If we're at the top level, we need to add the window and globalThis variables (Eg: let navigator = parentScope_navigator)
                if (scopePath.length === 1) {
                    output += `
                let ${key} = ${varOutName};
                `;
                }
            } else {
                output += `
            let ${varOutName} = ${valToString(value)};
            `;
            }
        }
        return output
    }

    /**
     * Code generates wrapping variables for code that is injected into the page
     * @param {*} code
     * @param {*} config
     * @returns {string}
     */
    function wrapScriptCodeOverload (code, config) {
        const processedConfig = {};
        for (const [key, value] of Object.entries(config)) {
            processedConfig[key] = processAttr(value);
        }
        // Don't do anything if the config is empty
        if (Object.keys(processedConfig).length === 0) return code

        let prepend = '';
        const aggregatedLookup = new Map();
        let currentScope = null;
        /* Convert the config into a map of scopePath -> { key: value } */
        for (const [key, value] of Object.entries(processedConfig)) {
            const path = key.split('.');

            currentScope = aggregatedLookup;
            const pathOut = path[path.length - 1];
            // Traverse the path and create the nested objects
            path.slice(0, -1).forEach((pathPart) => {
                if (!currentScope.has(pathPart)) {
                    currentScope.set(pathPart, new Map());
                }
                currentScope = currentScope.get(pathPart);
            });
            currentScope.set(pathOut, value);
        }

        prepend += stringifyScope(aggregatedLookup, ['parentScope']);
        // Stringify top level keys
        const keysOut = [...aggregatedLookup.keys()].map((keyName) => `${keyName}: ${getOrGenerateIdentifier(['parentScope', keyName])}`).join(',\n');
        prepend += `
    const window = constructProxy(parentScope, {
        ${keysOut}
    });
    // Ensure globalThis === window
    const globalThis = window
    `;
        return removeIndent(`(function (parentScope) {
        /**
         * DuckDuckGo Runtime Checks injected code.
         * If you're reading this, you're probably trying to debug a site that is breaking due to our runtime checks.
         * Please raise an issues on our GitHub repo: https://github.com/duckduckgo/content-scope-scripts/
         */
        ${constructProxy.toString()}
        ${prepend}

        ${getContextId.toString()}
        ${generateUniqueID.toString()}
        ${createContextAwareFunction.toString()}
        ${addTaint.toString()}
        const contextID = addTaint()
        
        const originalSetTimeout = setTimeout
        setTimeout = createContextAwareFunction(originalSetTimeout)
        
        const originalSetInterval = setInterval
        setInterval = createContextAwareFunction(originalSetInterval)
        
        const originalPromiseThen = Promise.prototype.then
        Promise.prototype.then = createContextAwareFunction(originalPromiseThen)
        
        const originalPromiseCatch = Promise.prototype.catch
        Promise.prototype.catch = createContextAwareFunction(originalPromiseCatch)
        
        const originalPromiseFinally = Promise.prototype.finally
        Promise.prototype.finally = createContextAwareFunction(originalPromiseFinally)

        ${code}
    })(globalThis)
    `)
    }

    /**
     * @typedef {object} Sizing
     * @property {number} height
     * @property {number} width
     */

    /**
     * @param {Sizing[]} breakpoints
     * @param {Sizing} screenSize
     * @returns { Sizing | null}
     */
    function findClosestBreakpoint (breakpoints, screenSize) {
        let closestBreakpoint = null;
        let closestDistance = Infinity;

        for (let i = 0; i < breakpoints.length; i++) {
            const breakpoint = breakpoints[i];
            const distance = Math.sqrt(Math.pow(breakpoint.height - screenSize.height, 2) + Math.pow(breakpoint.width - screenSize.width, 2));

            if (distance < closestDistance) {
                closestBreakpoint = breakpoint;
                closestDistance = distance;
            }
        }

        return closestBreakpoint
    }

    /* global TrustedScriptURL, TrustedScript */


    let stackDomains = [];
    let matchAllStackDomains = false;
    let taintCheck = false;
    let initialCreateElement;
    let tagModifiers = {};
    let shadowDomEnabled = false;
    let scriptOverload = {};
    let replaceElement = false;
    let monitorProperties = true;
    // Ignore monitoring properties that are only relevant once and already handled
    const defaultIgnoreMonitorList = ['onerror', 'onload'];
    let ignoreMonitorList = defaultIgnoreMonitorList;

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

    // use a module-scoped variable to extract some methods from the class https://github.com/duckduckgo/content-scope-scripts/pull/654#discussion_r1277375832
    /** @type {RuntimeChecks} */
    let featureInstance$1;

    let elementRemovalTimeout;
    const supportedSinks = ['src'];
    // Store the original methods so we can call them without any side effects
    const defaultElementMethods = {
        setAttribute: HTMLElement.prototype.setAttribute,
        setAttributeNS: HTMLElement.prototype.setAttributeNS,
        getAttribute: HTMLElement.prototype.getAttribute,
        getAttributeNS: HTMLElement.prototype.getAttributeNS,
        removeAttribute: HTMLElement.prototype.removeAttribute,
        remove: HTMLElement.prototype.remove,
        removeChild: HTMLElement.prototype.removeChild
    };
    const supportedTrustedTypes = 'TrustedScriptURL' in window;

    const jsMimeTypes = [
        'text/javascript',
        'text/ecmascript',
        'application/javascript',
        'application/ecmascript',
        'application/x-javascript',
        'application/x-ecmascript',
        'text/javascript1.0',
        'text/javascript1.1',
        'text/javascript1.2',
        'text/javascript1.3',
        'text/javascript1.4',
        'text/javascript1.5',
        'text/jscript',
        'text/livescript',
        'text/x-ecmascript',
        'text/x-javascript'
    ];

    function getTaintFromScope (scope, args, shouldStackCheck = false) {
        try {
            scope = args.callee.caller;
        } catch {}
        return hasTaintedMethod(scope, shouldStackCheck)
    }

    class DDGRuntimeChecks extends HTMLElement {
        #tagName
        #el
        #listeners
        #connected
        #sinks
        #debug

        constructor () {
            super();
            this.#tagName = null;
            this.#el = null;
            this.#listeners = [];
            this.#connected = false;
            this.#sinks = {};
            this.#debug = false;
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
        setTagName (tagName, debug = false) {
            this.#tagName = tagName;
            this.#debug = debug;

            // Clear the method so it can't be called again
            // @ts-expect-error - error TS2790: The operand of a 'delete' operator must be optional.
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
                            if (ignoreMonitorList.includes(prop)) return
                            el[prop] = value;
                        }
                    });
                } catch { }
            });
        }

        computeScriptOverload (el) {
            // Short circuit if we don't have any script text
            if (el.textContent === '') return
            // Short circuit if we're in a trusted script environment
            // @ts-expect-error TrustedScript is not defined in the TS lib
            if (supportedTrustedTypes && el.textContent instanceof TrustedScript) return

            // Short circuit if not a script type
            const scriptType = el.type.toLowerCase();
            if (!jsMimeTypes.includes(scriptType) &&
                scriptType !== 'module' &&
                scriptType !== '') return

            el.textContent = wrapScriptCodeOverload(el.textContent, scriptOverload);
        }

        /**
         * The element has been moved to the DOM, so we can now reflect all changes to a real element.
         * This is to allow us to interrogate the real element before it is moved to the DOM.
         */
        _transplantElement () {
            // Create the real element
            const el = initialCreateElement.call(document, this.#tagName);
            if (taintCheck) {
                // Add a symbol to the element so we can identify it as a runtime checked element
                Object.defineProperty(el, taintSymbol, { value: true, configurable: false, enumerable: false, writable: false });
                // Only show this attribute whilst debugging
                if (this.#debug) {
                    el.setAttribute('data-ddg-runtime-checks', 'true');
                }
                try {
                    const origin = this.src && new URL(this.src, window.location.href).hostname;
                    if (origin && taintedOrigins() && getTabHostname() !== origin) {
                        taintedOrigins()?.add(origin);
                    }
                } catch {}
            }

            // Reflect all attrs to the new element
            for (const attribute of this.getAttributeNames()) {
                if (shouldFilterKey(this.#tagName, 'attribute', attribute)) continue
                defaultElementMethods.setAttribute.call(el, attribute, this.getAttribute(attribute));
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

            if (this.#tagName === 'script') {
                this.computeScriptOverload(el);
            }

            if (replaceElement) {
                this.replaceElement(el);
            } else {
                this.insertAfterAndRemove(el);
            }

            // TODO pollyfill WeakRef
            this.#el = new WeakRef(el);
        }

        replaceElement (el) {
            // This should be called before this.#el is set
            // @ts-expect-error - this is wrong node type
            super.parentElement?.replaceChild(el, this);

            if (monitorProperties) {
                this._monitorProperties(el);
            }
        }

        insertAfterAndRemove (el) {
            // Move the new element to the DOM
            try {
                this.insertAdjacentElement('afterend', el);
            } catch (e) { console.warn(e); }

            if (monitorProperties) {
                this._monitorProperties(el);
            }

            // Delay removal of the custom element so if the script calls removeChild it will still be in the DOM and not throw.
            setTimeout(() => {
                try {
                    super.remove();
                } catch {}
            }, elementRemovalTimeout);
        }

        _getElement () {
            return this.#el?.deref()
        }

        /**
         * Calls a method on the real element if it exists, otherwise calls the method on the DDGRuntimeChecks element.
         * @template {keyof defaultElementMethods} E
         * @param {E} method
         * @param  {...Parameters<defaultElementMethods[E]>} args
         * @return {ReturnType<defaultElementMethods[E]>}
         */
        _callMethod (method, ...args) {
            const el = this._getElement();
            if (el) {
                return defaultElementMethods[method].call(el, ...args)
            }
            // @ts-expect-error TS doesn't like the spread operator
            return super[method](...args)
        }

        _callSetter (prop, value) {
            const el = this._getElement();
            if (el) {
                el[prop] = value;
                return
            }
            super[prop] = value;
        }

        _callGetter (prop) {
            const el = this._getElement();
            if (el) {
                return el[prop]
            }
            return super[prop]
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
            if (supportedTrustedTypes && this.#sinks.src instanceof TrustedScriptURL) {
                return this.#sinks.src.toString()
            }
            return this.#sinks.src
        }

        getAttribute (name, value) {
            if (shouldFilterKey(this.#tagName, 'attribute', name)) return
            if (supportedSinks.includes(name)) {
                // Use Reflect to avoid infinite recursion
                return Reflect$1.get(DDGRuntimeChecks.prototype, name, this)
            }
            return this._callMethod('getAttribute', name, value)
        }

        getAttributeNS (namespace, name, value) {
            if (namespace) {
                return this._callMethod('getAttributeNS', namespace, name, value)
            }
            return Reflect$1.apply(DDGRuntimeChecks.prototype.getAttribute, this, [name, value])
        }

        setAttribute (name, value) {
            if (shouldFilterKey(this.#tagName, 'attribute', name)) return
            if (supportedSinks.includes(name)) {
                // Use Reflect to avoid infinite recursion
                return Reflect$1.set(DDGRuntimeChecks.prototype, name, value, this)
            }
            return this._callMethod('setAttribute', name, value)
        }

        setAttributeNS (namespace, name, value) {
            if (namespace) {
                return this._callMethod('setAttributeNS', namespace, name, value)
            }
            return Reflect$1.apply(DDGRuntimeChecks.prototype.setAttribute, this, [name, value])
        }

        removeAttribute (name) {
            if (shouldFilterKey(this.#tagName, 'attribute', name)) return
            if (supportedSinks.includes(name)) {
                delete this[name];
                return
            }
            return this._callMethod('removeAttribute', name)
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
            let returnVal;
            try {
                returnVal = this._callMethod('remove');
                super.remove();
            } catch {}
            return returnVal
        }

        // @ts-expect-error TS node return here
        removeChild (child) {
            return this._callMethod('removeChild', child)
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
                return Reflect$1.apply(fn, scope, args)
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
            isInterrogatingDebugMessage('matchedAllStackDomain');
            return true
        }
        if (taintCheck && document.currentScript?.[taintSymbol]) {
            isInterrogatingDebugMessage('taintCheck');
            return true
        }
        const stack = getStack();
        const scriptOrigins = [...getStackTraceOrigins(stack)];
        const interestingHost = scriptOrigins.find(origin => {
            return stackDomains.some(rule => matchHostname(origin, rule.domain))
        });
        const isInterestingHost = !!interestingHost;
        if (isInterestingHost) {
            isInterrogatingDebugMessage('matchedStackDomain', interestingHost, stack, scriptOrigins);
        }
        return isInterestingHost
    }

    function isInterrogatingDebugMessage (matchType, matchedStackDomain, stack, scriptOrigins) {
        postDebugMessage('runtimeChecks', {
            documentUrl: document.location.href,
            matchedStackDomain,
            matchType,
            scriptOrigins,
            stack
        });
    }

    function isRuntimeElement (element) {
        try {
            return element instanceof DDGRuntimeChecks
        } catch {}
        return false
    }

    function overloadGetOwnPropertyDescriptor () {
        const capturedDescriptors = {
            HTMLScriptElement: Object.getOwnPropertyDescriptors(HTMLScriptElement),
            HTMLScriptElementPrototype: Object.getOwnPropertyDescriptors(HTMLScriptElement.prototype)
        };
        /**
         * @param {any} value
         * @returns {string | undefined}
         */
        function getInterfaceName (value) {
            let interfaceName;
            if (value === HTMLScriptElement) {
                interfaceName = 'HTMLScriptElement';
            }
            if (value === HTMLScriptElement.prototype) {
                interfaceName = 'HTMLScriptElementPrototype';
            }
            return interfaceName
        }
        // TODO: Consoldiate with wrapProperty code
        function getInterfaceDescriptor (interfaceValue, interfaceName, propertyName) {
            const capturedInterface = capturedDescriptors[interfaceName] && capturedDescriptors[interfaceName][propertyName];
            const capturedInterfaceOut = { ...capturedInterface };
            if (capturedInterface.get) {
                capturedInterfaceOut.get = wrapFunction(function () {
                    let method = capturedInterface.get;
                    if (isRuntimeElement(this)) {
                        method = () => this._callGetter(propertyName);
                    }
                    return method.call(this)
                }, capturedInterface.get);
            }
            if (capturedInterface.set) {
                capturedInterfaceOut.set = wrapFunction(function (value) {
                    let method = capturedInterface;
                    if (isRuntimeElement(this)) {
                        method = (value) => this._callSetter(propertyName, value);
                    }
                    return method.call(this, [value])
                }, capturedInterface.set);
            }
            return capturedInterfaceOut
        }
        const proxy = new DDGProxy(featureInstance$1, Object, 'getOwnPropertyDescriptor', {
            apply (fn, scope, args) {
                const interfaceValue = args[0];
                const interfaceName = getInterfaceName(interfaceValue);
                const propertyName = args[1];
                const capturedInterface = capturedDescriptors[interfaceName] && capturedDescriptors[interfaceName][propertyName];
                if (interfaceName && capturedInterface) {
                    return getInterfaceDescriptor(interfaceValue, interfaceName, propertyName)
                }
                return Reflect$1.apply(fn, scope, args)
            }
        });
        proxy.overload();
        const proxy2 = new DDGProxy(featureInstance$1, Object, 'getOwnPropertyDescriptors', {
            apply (fn, scope, args) {
                const interfaceValue = args[0];
                const interfaceName = getInterfaceName(interfaceValue);
                const capturedInterface = capturedDescriptors[interfaceName];
                if (interfaceName && capturedInterface) {
                    const out = {};
                    for (const propertyName of Object.getOwnPropertyNames(capturedInterface)) {
                        out[propertyName] = getInterfaceDescriptor(interfaceValue, interfaceName, propertyName);
                    }
                    return out
                }
                return Reflect$1.apply(fn, scope, args)
            }
        });
        proxy2.overload();
    }

    function overrideCreateElement (debug) {
        const proxy = new DDGProxy(featureInstance$1, Document.prototype, 'createElement', {
            apply (fn, scope, args) {
                if (args.length >= 1) {
                    // String() is used to coerce the value to a string (For: ProseMirror/prosemirror-model/src/to_dom.ts)
                    const initialTagName = String(args[0]).toLowerCase();
                    if (shouldInterrogate(initialTagName)) {
                        args[0] = 'ddg-runtime-checks';
                        const el = Reflect$1.apply(fn, scope, args);
                        el.setTagName(initialTagName, debug);
                        return el
                    }
                }
                return Reflect$1.apply(fn, scope, args)
            }
        });
        proxy.overload();
        initialCreateElement = proxy._native;
    }

    function overloadRemoveChild () {
        const proxy = new DDGProxy(featureInstance$1, Node.prototype, 'removeChild', {
            apply (fn, scope, args) {
                const child = args[0];
                if (child instanceof DDGRuntimeChecks) {
                    // Should call the real removeChild method if it's already replaced
                    const realNode = child._getElement();
                    if (realNode) {
                        args[0] = realNode;
                    }
                }
                return Reflect$1.apply(fn, scope, args)
            }
        });
        proxy.overloadDescriptor();
    }

    function overloadReplaceChild () {
        const proxy = new DDGProxy(featureInstance$1, Node.prototype, 'replaceChild', {
            apply (fn, scope, args) {
                const newChild = args[1];
                if (newChild instanceof DDGRuntimeChecks) {
                    const realNode = newChild._getElement();
                    if (realNode) {
                        args[1] = realNode;
                    }
                }
                return Reflect$1.apply(fn, scope, args)
            }
        });
        proxy.overloadDescriptor();
    }

    class RuntimeChecks extends ContentFeature {
        load () {
            // This shouldn't happen, but if it does we don't want to break the page
            try {
                // @ts-expect-error TS node return here
                globalThis.customElements.define('ddg-runtime-checks', DDGRuntimeChecks);
            } catch {}
            // eslint-disable-next-line @typescript-eslint/no-this-alias
            featureInstance$1 = this;
        }

        init () {
            let enabled = this.getFeatureSettingEnabled('matchAllDomains');
            if (!enabled) {
                enabled = this.matchDomainFeatureSetting('domains').length > 0;
            }
            if (!enabled) return

            taintCheck = this.getFeatureSettingEnabled('taintCheck');
            matchAllStackDomains = this.getFeatureSettingEnabled('matchAllStackDomains');
            stackDomains = this.getFeatureSetting('stackDomains') || [];
            elementRemovalTimeout = this.getFeatureSetting('elementRemovalTimeout') || 1000;
            tagModifiers = this.getFeatureSetting('tagModifiers') || {};
            shadowDomEnabled = this.getFeatureSettingEnabled('shadowDom') || false;
            scriptOverload = this.getFeatureSetting('scriptOverload') || {};
            ignoreMonitorList = this.getFeatureSetting('ignoreMonitorList') || defaultIgnoreMonitorList;
            replaceElement = this.getFeatureSettingEnabled('replaceElement') || false;
            monitorProperties = this.getFeatureSettingEnabled('monitorProperties') || true;

            overrideCreateElement(this.isDebug);

            if (this.getFeatureSettingEnabled('overloadInstanceOf')) {
                overloadInstanceOfChecks(HTMLScriptElement);
            }

            if (this.getFeatureSettingEnabled('injectGlobalStyles')) {
                injectGlobalStyles(`
                ddg-runtime-checks {
                    display: none;
                }
            `);
            }

            if (this.getFeatureSetting('injectGenericOverloads')) {
                this.injectGenericOverloads();
            }
            if (this.getFeatureSettingEnabled('overloadRemoveChild')) {
                overloadRemoveChild();
            }
            if (this.getFeatureSettingEnabled('overloadReplaceChild')) {
                overloadReplaceChild();
            }
            if (this.getFeatureSettingEnabled('overloadGetOwnPropertyDescriptor')) {
                overloadGetOwnPropertyDescriptor();
            }
        }

        injectGenericOverloads () {
            const genericOverloads = this.getFeatureSetting('injectGenericOverloads');
            if ('Date' in genericOverloads) {
                this.overloadDate(genericOverloads.Date);
            }
            if ('Date.prototype.getTimezoneOffset' in genericOverloads) {
                this.overloadDateGetTimezoneOffset(genericOverloads['Date.prototype.getTimezoneOffset']);
            }
            if ('NavigatorUAData.prototype.getHighEntropyValues' in genericOverloads) {
                this.overloadHighEntropyValues(genericOverloads['NavigatorUAData.prototype.getHighEntropyValues']);
            }
            ['localStorage', 'sessionStorage'].forEach(storageType => {
                if (storageType in genericOverloads) {
                    const storageConfig = genericOverloads[storageType];
                    if (storageConfig.scheme === 'memory') {
                        this.overloadStorageWithMemory(storageConfig, storageType);
                    } else if (storageConfig.scheme === 'session') {
                        this.overloadStorageWithSession(storageConfig, storageType);
                    }
                }
            });
            const breakpoints = this.getFeatureSetting('breakpoints');
            const screenSize = { height: screen.height, width: screen.width };
            ['innerHeight', 'innerWidth', 'outerHeight', 'outerWidth', 'Screen.prototype.height', 'Screen.prototype.width'].forEach(sizing => {
                if (sizing in genericOverloads) {
                    const sizingConfig = genericOverloads[sizing];
                    if (isBeingFramed() && !sizingConfig.applyToFrames) return
                    this.overloadScreenSizes(sizingConfig, breakpoints, screenSize, sizing, sizingConfig.offset || 0);
                }
            });
        }

        overloadDate (config) {
            const offset = (new Date()).getTimezoneOffset();
            globalThis.Date = new Proxy(globalThis.Date, {
                construct (target, args) {
                    const constructed = Reflect$1.construct(target, args);
                    if (getTaintFromScope(this, arguments, config.stackCheck)) {
                        // Falible in that the page could brute force the offset to match. We should fix this.
                        if (constructed.getTimezoneOffset() === offset) {
                            return constructed.getUTCDate()
                        }
                    }
                    return constructed
                }
            });
        }

        overloadDateGetTimezoneOffset (config) {
            const offset = (new Date()).getTimezoneOffset();
            this.defineProperty(globalThis.Date.prototype, 'getTimezoneOffset', {
                configurable: true,
                enumerable: true,
                writable: true,
                value () {
                    if (getTaintFromScope(this, arguments, config.stackCheck)) {
                        return 0
                    }
                    return offset
                }
            });
        }

        overloadHighEntropyValues (config) {
            if (!('NavigatorUAData' in globalThis)) {
                return
            }

            const originalGetHighEntropyValues = globalThis.NavigatorUAData.prototype.getHighEntropyValues;
            this.defineProperty(globalThis.NavigatorUAData.prototype, 'getHighEntropyValues', {
                configurable: true,
                enumerable: true,
                writable: true,
                value (hints) {
                    let hintsOut = hints;
                    if (getTaintFromScope(this, arguments, config.stackCheck)) {
                        // If tainted override with default values (using empty array)
                        hintsOut = [];
                    }
                    return Reflect$1.apply(originalGetHighEntropyValues, this, [hintsOut])
                }
            });
        }

        overloadStorageWithMemory (config, key) {
            /**
             * @implements {Storage}
             */
            class MemoryStorage {
                #data = {}

                /**
                 * @param {Parameters<Storage['setItem']>[0]} id
                 * @param {Parameters<Storage['setItem']>[1]} val
                 * @returns {ReturnType<Storage['setItem']>}
                 */
                setItem (id, val) {
                    if (arguments.length < 2) throw new TypeError(`Failed to execute 'setItem' on 'Storage': 2 arguments required, but only ${arguments.length} present.`)
                    this.#data[id] = String(val);
                }

                /**
                 * @param {Parameters<Storage['getItem']>[0]} id
                 * @returns {ReturnType<Storage['getItem']>}
                 */
                getItem (id) {
                    return Object.prototype.hasOwnProperty.call(this.#data, id) ? this.#data[id] : null
                }

                /**
                 * @param {Parameters<Storage['removeItem']>[0]} id
                 * @returns {ReturnType<Storage['removeItem']>}
                 */
                removeItem (id) {
                    delete this.#data[id];
                }

                /**
                 * @returns {ReturnType<Storage['clear']>}
                 */
                clear () {
                    this.#data = {};
                }

                /**
                 * @param {Parameters<Storage['key']>[0]} n
                 * @returns {ReturnType<Storage['key']>}
                 */
                key (n) {
                    const keys = Object.keys(this.#data);
                    return keys[n]
                }

                get length () {
                    return Object.keys(this.#data).length
                }
            }
            /** @satisfies {Storage} */
            const instance = new MemoryStorage();
            const storage = new Proxy(instance, {
                set (target, prop, value) {
                    Reflect$1.apply(target.setItem, target, [prop, value]);
                    return true
                },
                get (target, prop) {
                    if (typeof target[prop] === 'function') {
                        return target[prop].bind(instance)
                    }
                    return Reflect$1.get(target, prop, instance)
                }
            });
            this.overrideStorage(config, key, storage);
        }

        overloadStorageWithSession (config, key) {
            const storage = globalThis.sessionStorage;
            this.overrideStorage(config, key, storage);
        }

        overrideStorage (config, key, storage) {
            const originalStorage = globalThis[key];
            this.defineProperty(globalThis, key, {
                get () {
                    if (getTaintFromScope(this, arguments, config.stackCheck)) {
                        return storage
                    }
                    return originalStorage
                }
            });
        }

        /**
         * @typedef {import('./runtime-checks/helpers.js').Sizing} Sizing
         */

        /**
         * Overloads the provided key with the closest breakpoint size
         * @param {Sizing[]} breakpoints
         * @param {Sizing} screenSize
         * @param {string} key
         * @param {number} [offset]
         */
        overloadScreenSizes (config, breakpoints, screenSize, key, offset = 0) {
            const closest = findClosestBreakpoint(breakpoints, screenSize);
            if (!closest) {
                return
            }
            let returnVal = null;
            /** @type {object} */
            let scope = globalThis;
            let overrideKey = key;
            let receiver;
            switch (key) {
            case 'innerHeight':
            case 'outerHeight':
                returnVal = closest.height - offset;
                break
            case 'innerWidth':
            case 'outerWidth':
                returnVal = closest.width - offset;
                break
            case 'Screen.prototype.height':
                scope = Screen.prototype;
                overrideKey = 'height';
                returnVal = closest.height - offset;
                receiver = globalThis.screen;
                break
            case 'Screen.prototype.width':
                scope = Screen.prototype;
                overrideKey = 'width';
                returnVal = closest.width - offset;
                receiver = globalThis.screen;
                break
            }
            const defaultGetter = Object.getOwnPropertyDescriptor(scope, overrideKey)?.get;
            // Should never happen
            if (!defaultGetter) {
                return
            }
            this.defineProperty(scope, overrideKey, {
                get () {
                    const defaultVal = Reflect$1.apply(defaultGetter, receiver, []);
                    if (getTaintFromScope(this, arguments, config.stackCheck)) {
                        return returnVal
                    }
                    return defaultVal
                }
            });
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

    class FingerprintingAudio extends ContentFeature {
        init (args) {
            const { sessionKey, site } = args;
            const domainKey = site.domain;

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

            const copyFromChannelProxy = new DDGProxy(this, AudioBuffer.prototype, 'copyFromChannel', {
                apply (target, thisArg, args) {
                    const [source, channelNumber, startInChannel] = args;
                    // This is implemented in a different way to canvas purely because calling the function copied the original value, which is not ideal
                    if (// If channelNumber is longer than arrayBuffer number of channels then call the default method to throw
                        // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                        channelNumber > thisArg.numberOfChannels ||
                        // If startInChannel is longer than the arrayBuffer length then call the default method to throw
                        // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                        startInChannel > thisArg.length) {
                        // The normal return value
                        return DDGReflect.apply(target, thisArg, args)
                    }
                    try {
                        // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
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

            const getChannelDataProxy = new DDGProxy(this, AudioBuffer.prototype, 'getChannelData', {
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
                const proxy = new DDGProxy(this, AnalyserNode.prototype, methodName, {
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
    }

    /**
     * Overwrites the Battery API if present in the browser.
     * It will return the values defined in the getBattery function to the client,
     * as well as prevent any script from listening to events.
     */
    class FingerprintingBattery extends ContentFeature {
        init () {
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
                        this.defineProperty(BatteryManager.prototype, prop, {
                            get: () => {
                                return val
                            }
                        });
                    } catch (e) { }
                }
                for (const eventProp of eventProperties) {
                    try {
                        this.defineProperty(BatteryManager.prototype, eventProp, {
                            get: () => {
                                return null
                            }
                        });
                    } catch (e) { }
                }
            }
        }
    }

    var commonjsGlobal = typeof globalThis !== 'undefined' ? globalThis : typeof window !== 'undefined' ? window : typeof global !== 'undefined' ? global : typeof self !== 'undefined' ? self : {};

    function getDefaultExportFromCjs (x) {
    	return x && x.__esModule && Object.prototype.hasOwnProperty.call(x, 'default') ? x['default'] : x;
    }

    var alea$1 = {exports: {}};

    alea$1.exports;

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

    var aleaExports = alea$1.exports;

    var xor128$1 = {exports: {}};

    xor128$1.exports;

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

    var xor128Exports = xor128$1.exports;

    var xorwow$1 = {exports: {}};

    xorwow$1.exports;

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

    var xorwowExports = xorwow$1.exports;

    var xorshift7$1 = {exports: {}};

    xorshift7$1.exports;

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

    var xorshift7Exports = xorshift7$1.exports;

    var xor4096$1 = {exports: {}};

    xor4096$1.exports;

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

    var xor4096Exports = xor4096$1.exports;

    var tychei$1 = {exports: {}};

    tychei$1.exports;

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

    var tycheiExports = tychei$1.exports;

    var seedrandom$1 = {exports: {}};

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

    var seedrandomExports = seedrandom$1.exports;

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

    var Seedrandom = /*@__PURE__*/getDefaultExportFromCjs(seedrandom);

    /**
     * @param {HTMLCanvasElement} canvas
     * @param {string} domainKey
     * @param {string} sessionKey
     * @param {any} getImageDataProxy
     * @param {CanvasRenderingContext2D | WebGL2RenderingContext | WebGLRenderingContext} ctx?
     */
    function computeOffScreenCanvas (canvas, domainKey, sessionKey, getImageDataProxy, ctx) {
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
        const rng = new Seedrandom(windowHash);
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

    class FingerprintingCanvas extends ContentFeature {
        init (args) {
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

            const proxy = new DDGProxy(this, HTMLCanvasElement.prototype, 'getContext', {
                apply (target, thisArg, args) {
                    const context = DDGReflect.apply(target, thisArg, args);
                    try {
                        // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'.
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
                const safeMethodProxy = new DDGProxy(this, CanvasRenderingContext2D.prototype, methodName, {
                    apply (target, thisArg, args) {
                        // Don't apply escape hatch for canvases
                        if (methodName === 'drawImage' && args[0] && args[0] instanceof HTMLCanvasElement) {
                            treatAsUnsafe(args[0]);
                        } else {
                            // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
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
                    const unsafeProxy = new DDGProxy(this, CanvasRenderingContext2D.prototype, methodName, {
                        apply (target, thisArg, args) {
                            // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
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
                            const unsafeProxy = new DDGProxy(this, context.prototype, methodName, {
                                apply (target, thisArg, args) {
                                    // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
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
            const getImageDataProxy = new DDGProxy(this, CanvasRenderingContext2D.prototype, 'getImageData', {
                apply (target, thisArg, args) {
                    // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                    if (!unsafeCanvases.has(thisArg.canvas)) {
                        return DDGReflect.apply(target, thisArg, args)
                    }
                    // Anything we do here should be caught and ignored silently
                    try {
                        // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
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
                const proxy = new DDGProxy(this, HTMLCanvasElement.prototype, methodName, {
                    apply (target, thisArg, args) {
                        // Short circuit for low risk canvas calls
                        // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
                        if (!unsafeCanvases.has(thisArg)) {
                            return DDGReflect.apply(target, thisArg, args)
                        }
                        try {
                            // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
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
    }

    class GoogleRejected extends ContentFeature {
        init () {
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
        init (args) {
            try {
                // If GPC on, set DOM property prototype to true if not already true
                if (args.globalPrivacyControlValue) {
                    // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                    if (navigator.globalPrivacyControl) return
                    this.defineProperty(Navigator.prototype, 'globalPrivacyControl', {
                        get: () => true,
                        configurable: true,
                        enumerable: true
                    });
                } else {
                    // If GPC off & unsupported by browser, set DOM property prototype to false
                    // this may be overwritten by the user agent or other extensions
                    // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                    if (typeof navigator.globalPrivacyControl !== 'undefined') return
                    this.defineProperty(Navigator.prototype, 'globalPrivacyControl', {
                        get: () => false,
                        configurable: true,
                        enumerable: true
                    });
                }
            } catch {
                // Ignore exceptions that could be caused by conflicting with other extensions
            }
        }
    }

    class FingerprintingHardware extends ContentFeature {
        init () {
            this.wrapProperty(globalThis.Navigator.prototype, 'keyboard', {
                get: () => {
                    // @ts-expect-error - error TS2554: Expected 2 arguments, but got 1.
                    return this.getFeatureAttr('keyboard')
                }
            });

            this.wrapProperty(globalThis.Navigator.prototype, 'hardwareConcurrency', {
                get: () => {
                    return this.getFeatureAttr('hardwareConcurrency', 2)
                }
            });

            this.wrapProperty(globalThis.Navigator.prototype, 'deviceMemory', {
                get: () => {
                    return this.getFeatureAttr('deviceMemory', 8)
                }
            });
        }
    }

    class Referrer extends ContentFeature {
        init () {
            // If the referer is a different host to the current one, trim it.
            if (document.referrer && new URL(document.URL).hostname !== new URL(document.referrer).hostname) {
                // trim referrer to origin.
                const trimmedReferer = new URL(document.referrer).origin + '/';
                this.wrapProperty(Document.prototype, 'referrer', {
                    get: () => trimmedReferer
                });
            }
        }
    }

    class FingerprintingScreenSize extends ContentFeature {
        origPropertyValues = {}

        init () {
            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            this.origPropertyValues.availTop = globalThis.screen.availTop;
            this.wrapProperty(globalThis.Screen.prototype, 'availTop', {
                get: () => this.getFeatureAttr('availTop', 0)
            });

            // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
            this.origPropertyValues.availLeft = globalThis.screen.availLeft;
            this.wrapProperty(globalThis.Screen.prototype, 'availLeft', {
                get: () => this.getFeatureAttr('availLeft', 0)
            });

            this.origPropertyValues.availWidth = globalThis.screen.availWidth;
            const forcedAvailWidthValue = globalThis.screen.width;
            this.wrapProperty(globalThis.Screen.prototype, 'availWidth', {
                get: () => forcedAvailWidthValue
            });

            this.origPropertyValues.availHeight = globalThis.screen.availHeight;
            const forcedAvailHeightValue = globalThis.screen.height;
            this.wrapProperty(globalThis.Screen.prototype, 'availHeight', {
                get: () => forcedAvailHeightValue
            });

            this.origPropertyValues.colorDepth = globalThis.screen.colorDepth;
            this.wrapProperty(globalThis.Screen.prototype, 'colorDepth', {
                get: () => this.getFeatureAttr('colorDepth', 24)
            });

            this.origPropertyValues.pixelDepth = globalThis.screen.pixelDepth;
            this.wrapProperty(globalThis.Screen.prototype, 'pixelDepth', {
                get: () => this.getFeatureAttr('pixelDepth', 24)
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
        normalizeWindowDimension (value, targetDimension) {
            if (value > targetDimension) {
                return value % targetDimension
            }
            if (value < 0) {
                return targetDimension + value
            }
            return value
        }

        setWindowPropertyValue (property, value) {
            // Here we don't update the prototype getter because the values are updated dynamically
            try {
                this.defineProperty(globalThis, property, {
                    get: () => value,
                    // eslint-disable-next-line @typescript-eslint/no-empty-function
                    set: () => {},
                    configurable: true
                });
            } catch (e) {}
        }

        /**
         * Fix window dimensions. The extension runs in a different JS context than the
         * page, so we can inject the correct screen values as the window is resized,
         * ensuring that no information is leaked as the dimensions change, but also that the
         * values change correctly for valid use cases.
         */
        setWindowDimensions () {
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
        init () {
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
                    this.defineProperty(Navigator.prototype, 'webkitTemporaryStorage', { get: () => tStorage });
                } catch (e) {}
            }
        }
    }

    class NavigatorInterface extends ContentFeature {
        load (args) {
            if (this.matchDomainFeatureSetting('privilegedDomains').length) {
                this.injectNavigatorInterface(args);
            }
        }

        init (args) {
            this.injectNavigatorInterface(args);
        }

        injectNavigatorInterface (args) {
            try {
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                if (navigator.duckduckgo) {
                    return
                }
                if (!args.platform || !args.platform.name) {
                    return
                }
                this.defineProperty(Navigator.prototype, 'duckduckgo', {
                    value: {
                        platform: args.platform.name,
                        isDuckDuckGo () {
                            return DDGPromise.resolve(true)
                        },
                        taints: new Set(),
                        taintedOrigins: new Set()
                    },
                    enumerable: true,
                    configurable: false,
                    writable: false
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
    function collapseDomNode (element, rule, previousElement) {
        if (!element) {
            return
        }
        const type = rule.type;
        const alreadyHidden = hiddenElements.has(element);
        const alreadyModified = modifiedElements.has(element) && modifiedElements.get(element) === rule.type;
        // return if the element has already been hidden, or modified by the same rule type
        if (alreadyHidden || alreadyModified) {
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
        case 'modify-attr':
            modifyAttribute(element, rule.values);
            break
        case 'modify-style':
            modifyStyle(element, rule.values);
            break
        }
    }

    /**
     * Unhide previously hidden DOM element if content loaded into it
     * @param {HTMLElement} element
     * @param {Object} rule
     */
    function expandNonEmptyDomNode (element, rule) {
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
        // add debug flag to site breakage reports
        featureInstance.addDebugFlag();
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
     * Modify specified attribute(s) on element
     * @param {HTMLElement} element
     * @param {Object[]} values
     * @param {string} values[].property
     * @param {string} values[].value
     */
    function modifyAttribute (element, values) {
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
    function modifyStyle (element, values) {
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
    function extractTimeoutRules (rules) {
        if (!shouldInjectStyleTag) {
            return rules
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
        return timeoutRules
    }

    /**
     * Create styletag for strict hide rules and append it to the document
     * @param {Object[]} rules
     * @param {string} rules[].selector
     * @param {string} rules[].type
     */
    function injectStyleTag (rules) {
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
    function hideAdNodes (rules) {
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
    function unhideLoadedAds () {
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
    function forgivingSelector (selector) {
        return `:is(${selector})`
    }

    class ElementHiding extends ContentFeature {
        init () {
            // eslint-disable-next-line @typescript-eslint/no-this-alias
            featureInstance = this;

            if (isBeingFramed()) {
                return
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
                shouldInjectStyleTag = this.matchDomainFeatureSetting('styleTagExceptions').length === 0;
            }

            // collect all matching rules for domain
            const activeDomainRules = this.matchDomainFeatureSetting('domains').flatMap((item) => item.rules);

            const overrideRules = activeDomainRules.filter((rule) => {
                return rule.type === 'override'
            });

            const disableDefault = activeDomainRules.some((rule) => {
                return rule.type === 'disable-default'
            });

            // if rule with type 'disable-default' is present, ignore all global rules
            if (disableDefault) {
                activeRules = activeDomainRules.filter((rule) => {
                    return rule.type !== 'disable-default'
                });
            } else {
                activeRules = activeDomainRules.concat(globalRules);
            }

            // remove overrides and rules that match overrides from array of rules to be applied to page
            overrideRules.forEach((override) => {
                activeRules = activeRules.filter((rule) => {
                    return rule.selector !== override.selector
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
                apply (target, thisArg, args) {
                    applyRules(activeRules);
                    return DDGReflect.apply(target, thisArg, args)
                }
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
        applyRules (rules) {
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
        init () {
            // Report to the debugger panel if an uncaught exception occurs
            const handleUncaughtException = (e) => {
                postDebugMessage('jsException', {
                    documentUrl: document.location.href,
                    message: e.message,
                    filename: e.filename,
                    lineno: e.lineno,
                    colno: e.colno,
                    stack: e.error?.stack
                }, true);
                this.addDebugFlag();
            };
            globalThis.addEventListener('error', handleUncaughtException);
        }
    }

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

    const MSG_WEB_SHARE = 'webShare';

    function canShare (data) {
        if (typeof data !== 'object') return false
        if (!('url' in data) && !('title' in data) && !('text' in data)) return false // At least one of these is required
        if ('files' in data) return false // File sharing is not supported at the moment
        if ('title' in data && typeof data.title !== 'string') return false
        if ('text' in data && typeof data.text !== 'string') return false
        if ('url' in data) {
            if (typeof data.url !== 'string') return false
            try {
                const url = new URL$1(data.url, location.href);
                if (url.protocol !== 'http:' && url.protocol !== 'https:') return false
            } catch (err) {
                return false
            }
        }
        if (window !== window.top) return false // Not supported in iframes
        return true
    }

    /**
     * Clean data before sending to the Android side
     * @returns {ShareRequestData}
     */
    function cleanShareData (data) {
        /** @type {ShareRequestData} */
        const dataToSend = {};

        // only send the keys we care about
        for (const key of ['title', 'text', 'url']) {
            if (key in data) dataToSend[key] = data[key];
        }

        // clean url and handle relative links (e.g. if url is an empty string)
        if ('url' in data) {
            dataToSend.url = (new URL$1(data.url, location.href)).href;
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
        return dataToSend
    }

    class WebCompat extends ContentFeature {
        /** @type {Promise<any> | null} */
        #activeShareRequest = null

        init () {
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
        }

        /** Shim Web Share API in Android WebView */
        shimWebShare () {
            if (typeof navigator.canShare === 'function' || typeof navigator.share === 'function') return

            this.defineProperty(Navigator.prototype, 'canShare', {
                configurable: true,
                enumerable: true,
                writable: true,
                value: canShare
            });

            this.defineProperty(Navigator.prototype, 'share', {
                configurable: true,
                enumerable: true,
                writable: true,
                value: async (data) => {
                    if (!canShare(data)) return Promise.reject(new TypeError('Invalid share data'))
                    if (this.#activeShareRequest) {
                        return Promise.reject(new DOMException('Share already in progress', 'InvalidStateError'))
                    }
                    if (!navigator.userActivation.isActive) {
                        return Promise.reject(new DOMException('Share must be initiated by a user gesture', 'InvalidStateError'))
                    }

                    const dataToSend = cleanShareData(data);
                    this.#activeShareRequest = this.messaging.request(MSG_WEB_SHARE, dataToSend);
                    let resp;
                    try {
                        resp = await this.#activeShareRequest;
                    } catch (err) {
                        throw new DOMException(err.message, 'DataError')
                    } finally {
                        this.#activeShareRequest = null;
                    }

                    if (resp.failure) {
                        switch (resp.failure.name) {
                        case 'AbortError':
                        case 'NotAllowedError':
                        case 'DataError':
                            throw new DOMException(resp.failure.message, resp.failure.name)
                        default:
                            throw new DOMException(resp.failure.message, 'DataError')
                        }
                    }
                }
            });
        }

        /**
         * Notification fix for adding missing API for Android WebView.
         */
        notificationFix () {
            if (window.Notification) {
                return
            }
            // Expose the API
            this.defineProperty(window, 'Notification', {
                value: () => {
                    // noop
                },
                writable: true,
                configurable: true,
                enumerable: false
            });

            this.defineProperty(window.Notification, 'requestPermission', {
                value: () => {
                    return Promise.resolve('denied')
                },
                writable: true,
                configurable: true,
                enumerable: false
            });

            this.defineProperty(window.Notification, 'permission', {
                value: 'denied',
                writable: true,
                configurable: true,
                enumerable: false
            });

            this.defineProperty(window.Notification, 'maxActions', {
                value: 2,
                writable: true,
                configurable: true,
                enumerable: false
            });
        }

        cleanIframeValue () {
            function cleanValueData (val) {
                const clone = Object.assign({}, val);
                const deleteKeys = ['iframeProto', 'iframeData', 'remap'];
                for (const key of deleteKeys) {
                    if (key in clone) {
                        delete clone[key];
                    }
                }
                val.iframeData = clone;
                return val
            }

            window.XMLHttpRequest.prototype.send = new Proxy(window.XMLHttpRequest.prototype.send, {
                apply (target, thisArg, args) {
                    const body = args[0];
                    const cleanKey = 'bi_wvdp';
                    if (body && typeof body === 'string' && body.includes(cleanKey)) {
                        const parts = body.split('&').map((part) => { return part.split('=') });
                        if (parts.length > 0) {
                            parts.forEach((part) => {
                                if (part[0] === cleanKey) {
                                    const val = JSON.parse(decodeURIComponent(part[1]));
                                    part[1] = encodeURIComponent(JSON.stringify(cleanValueData(val)));
                                }
                            });
                            args[0] = parts.map((part) => { return part.join('=') }).join('&');
                        }
                    }
                    return Reflect.apply(target, thisArg, args)
                }
            });
        }

        /**
         * Adds missing permissions API for Android WebView.
         */
        permissionsFix (settings) {
            if (window.navigator.permissions) {
                return
            }
            const permissions = {};
            class PermissionStatus extends EventTarget {
                constructor (name, state) {
                    super();
                    this.name = name;
                    this.state = state;
                    this.onchange = null; // noop
                }
            }
            // Default subset based upon Firefox (the full list is pretty large right now and these are the common ones)
            const defaultValidPermissionNames = [
                'geolocation',
                'notifications',
                'push',
                'persistent-storage',
                'midi',
                'accelerometer',
                'ambient-light-sensor',
                'background-sync',
                'bluetooth',
                'camera',
                'clipboard',
                'device-info',
                'gyroscope',
                'magnetometer',
                'microphone',
                'speaker'
            ];
            const validPermissionNames = settings.validPermissionNames || defaultValidPermissionNames;
            const returnStatus = settings.permissionResponse || 'prompt';
            permissions.query = new Proxy((query) => {
                this.addDebugFlag();
                if (!query) {
                    throw new TypeError("Failed to execute 'query' on 'Permissions': 1 argument required, but only 0 present.")
                }
                if (!query.name) {
                    throw new TypeError("Failed to execute 'query' on 'Permissions': Failed to read the 'name' property from 'PermissionDescriptor': Required member is undefined.")
                }
                if (!validPermissionNames.includes(query.name)) {
                    throw new TypeError(`Failed to execute 'query' on 'Permissions': Failed to read the 'name' property from 'PermissionDescriptor': The provided value '${query.name}' is not a valid enum value of type PermissionName.`)
                }
                return Promise.resolve(new PermissionStatus(query.name, returnStatus))
            }, {
                get (target, name) {
                    return Reflect.get(target, name)
                }
            });
            // Expose the API
            // @ts-expect-error window.navigator isn't assignable
            window.navigator.permissions = permissions;
        }

        /**
         * Add missing navigator.credentials API
         */
        navigatorCredentialsFix () {
            try {
                if ('credentials' in navigator && 'get' in navigator.credentials) {
                    return
                }
                // TODO: change the property descriptor shape to match the original
                const value = {
                    get () {
                        return Promise.reject(new Error())
                    }
                };
                this.defineProperty(Navigator.prototype, 'credentials', {
                    value,
                    configurable: true,
                    enumerable: true
                });
            } catch {
                // Ignore exceptions that could be caused by conflicting with other extensions
            }
        }

        safariObjectFix () {
            try {
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                if (window.safari) {
                    return
                }
                this.defineProperty(window, 'safari', {
                    value: {
                    },
                    writable: true,
                    configurable: true,
                    enumerable: true
                });
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                this.defineProperty(window.safari, 'pushNotification', {
                    value: {
                    },
                    configurable: true,
                    enumerable: true
                });
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                this.defineProperty(window.safari.pushNotification, 'toString', {
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
                this.defineProperty(window.safari.pushNotification, 'permission', {
                    value: () => {
                        return new SafariRemoteNotificationPermission()
                    },
                    configurable: true,
                    enumerable: true
                });
                // @ts-expect-error https://app.asana.com/0/1201614831475344/1203979574128023/f
                this.defineProperty(window.safari.pushNotification, 'requestPermission', {
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

        mediaSessionFix () {
            try {
                if (window.navigator.mediaSession) {
                    return
                }

                this.defineProperty(window.navigator, 'mediaSession', {
                    value: {
                    },
                    writable: true,
                    configurable: true,
                    enumerable: true
                });
                this.defineProperty(window.navigator.mediaSession, 'metadata', {
                    value: null,
                    writable: true,
                    configurable: false,
                    enumerable: false
                });
                this.defineProperty(window.navigator.mediaSession, 'playbackState', {
                    value: 'none',
                    writable: true,
                    configurable: false,
                    enumerable: false
                });
                this.defineProperty(window.navigator.mediaSession, 'setActionHandler', {
                    value: () => {},
                    configurable: true,
                    enumerable: true
                });
                this.defineProperty(window.navigator.mediaSession, 'setCameraActive', {
                    value: () => {},
                    configurable: true,
                    enumerable: true
                });
                this.defineProperty(window.navigator.mediaSession, 'setMicrophoneActive', {
                    value: () => {},
                    configurable: true,
                    enumerable: true
                });
                this.defineProperty(window.navigator.mediaSession, 'setPositionState', {
                    value: () => {},
                    configurable: true,
                    enumerable: true
                });

                class MediaMetadata {
                    constructor (metadata = {}) {
                        this.title = metadata.title;
                        this.artist = metadata.artist;
                        this.album = metadata.album;
                        this.artwork = metadata.artwork;
                    }
                }

                window.MediaMetadata = new Proxy(MediaMetadata, {});
            } catch {
                // Ignore exceptions that could be caused by conflicting with other extensions
            }
        }

        presentationFix () {
            try {
                // @ts-expect-error due to: Property 'presentation' does not exist on type 'Navigator'
                if (window.navigator.presentation) {
                    return
                }

                this.defineProperty(window.navigator, 'presentation', {
                    value: {
                    },
                    writable: true,
                    configurable: true,
                    enumerable: true
                });
                // @ts-expect-error due to: Property 'presentation' does not exist on type 'Navigator'
                this.defineProperty(window.navigator.presentation, 'defaultRequest', {
                    value: null,
                    configurable: true,
                    enumerable: true
                });
                // @ts-expect-error due to: Property 'presentation' does not exist on type 'Navigator'
                this.defineProperty(window.navigator.presentation, 'receiver', {
                    value: null,
                    configurable: true,
                    enumerable: true
                });
            } catch {
                // Ignore exceptions that could be caused by conflicting with other extensions
            }
        }

        /**
         * Support for proxying `window.webkit.messageHandlers`
         */
        messageHandlersFix () {
            /** @type {import('../types//webcompat-settings').WebCompatSettings['messageHandlers']} */
            const settings = this.getFeatureSetting('messageHandlers');

            // Do nothing if `messageHandlers` is absent
            if (!globalThis.webkit?.messageHandlers) return
            // This should never occur, but keeps typescript happy
            if (!settings) return

            const proxy = new Proxy(globalThis.webkit.messageHandlers, {
                get (target, messageName, receiver) {
                    const handlerName = String(messageName);

                    // handle known message names, such as DDG webkit messaging
                    if (settings.handlerStrategies.reflect.includes(handlerName)) {
                        return Reflect.get(target, messageName, receiver)
                    }

                    if (settings.handlerStrategies.undefined.includes(handlerName)) {
                        return undefined
                    }

                    if (settings.handlerStrategies.polyfill.includes('*') ||
                        settings.handlerStrategies.polyfill.includes(handlerName)
                    ) {
                        return {
                            postMessage () {
                                return Promise.resolve({})
                            }
                        }
                    }
                    // if we get here, we couldn't handle the message handler name, so we opt for doing nothing.
                    // It's unlikely we'll ever reach here, since `["*"]' should be present
                }
            });

            globalThis.webkit = {
                ...globalThis.webkit,
                messageHandlers: proxy
            };
        }

        viewportWidthFix () {
            const viewportTag = document.querySelector('meta[name=viewport]');
            if (!viewportTag) return
            const viewportContent = viewportTag.getAttribute('content');
            if (!viewportContent) return
            const viewportContentParts = viewportContent.split(',');
            const widthPart = viewportContentParts.find((part) => part.includes('width'));
            // If we already have a width, don't add one
            if (widthPart) return
            viewportTag.setAttribute('content', `${viewportContent},width=device-width`);
        }
    }

    /** @typedef {{title?: string, url?: string, text?: string}} ShareRequestData */

    const logoImg = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAFQAAABUCAYAAAAcaxDBAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAABNTSURBVHgBzV0LcFPXmf6PJFt+gkEY8wrYMSEbgst7m02ywZnOZiEJCQlJC+QB25lNs7OzlEJ2ptmZLGayfUy3EEhmW5rM7gCZBtjJgzxmSTvTRSST9IF5pCE0TUosmmBjHIKNZFmWLN2e78hHPvfqXuleSdfONyNLV7q6uve7//uc85vRlwAda25oTFK8lZGn0UPaLI2okUhrTH/KGnU7M+olTevlL0KaeM3e01LaKa/PE2p64dgpGmMwGgN0rGqtS1Ve2cB/fhk/gVbSqI5KAU4wvxlBTdNe9VJ5sOnAb0I0yhg1QiWJTGN3E0gcHQRTpO0dTXJdJ7RjzZJWflHrGaNVdiTRN2kalTfOIU9VLfnqp5ruM9TTxR+dlIqGKX7uI7IDLrl7PFS2zW1iXSMURGqkbaUc0uiprqWqxa1UOXcxVcxdxAmcRoUApMZDH9HAmeMU+8NxQbYV3Ca25ITCwaRY4immcYk0AUgcv3wtJ3CxeLgBEBw++jpF249akusWsSUltGPNoq0aY5vMVLviusU04b5HbJMoVLo/ItRaBUyBp7rGtjTHuNSGj75BkbdeN/2ckdbWdODENioRSkIopFLThl4hpi0wflZzy0pO5D9aEiDsIFfXQagtf4CAXCqronzWHHFc3CQ/f53rZuGYl198zorYEKOyW0shrUUT2rFu8bc1jdqMUplLIkFi9NhRCvOLA4mp/jCVAjAn+N2qJa1UvXSZkGYjQOylfTu4OQjqPxAhl7atef+JnVQEiiK0Y+2ipzSNq7gCXFT9o1vFRRkB6evnFxJ5642SkWgF4fD4OUxYba4dEW4GLr/0bJY2FGsCCiIUMaVWEX6FDB4cF1D/T1uzJANE4uTxPBaoWbbSlNgcZiDIYsl7mg6d6iWHcEyolb0MPLyFxq1Yq9sXqg31ihx9nb4MsCK298VnxQ3XQaNTjJXd49SuOiJUkEmJIyRy7TSgWg2bf5xlK/sO76defpJuq7ZTgMy61Y9Q7bI7de/Dlndvf8xoAhw7K9uECjX3R46okomTm/rEbt0dh1TixIzqDeI9lSPZD/ZDWDT0uT2PXmqYSSvI7HryUT2pkNTB5K121d82oZ+sWQzJbJXbZmRa3GWBces2UuXX7qOKigryeDy6z0A+wqbosaDIdEYLZtdgSiq3qVcfOH6rnWPaIlQE7MTacp1ImHvuL/Ztz63iE+qpZtN2qp8z13IX6Siix4OjYi7gQCdy+6+aADNSecKys3l/+3fyHc+bb4d0nMl+KLfNyIS9vPTfPyAtEbc8jvjevz5F45r/inIBpqF6aSvV/M1twiTYLX4UCpwzYlIRw17TMnIOS5aJ8E5eE5e8Gza2TO17+nTXb3IdLyehaSeUOsBfVsj3pv77z6hsWmNmH5AJycwFQeb3nqfBqvHU399P4XBYPMfjcWK8DOXz+bK+I4mFCo2GGRh479dZpFbMbhGkSvBzvWHTvFkHd53+zNKe5lR5bjc7SPHoE7h3rOPZjwTU/POftlE+4ORS5ZVEly+OvDm1UTw0bldRsmtoaCC/32/6/SvQgDw3rVSY9GibTv2zfps7qasPHl9o9X1LCYXd5HxnKkbIyQPrt2Q+h325uOOxnGqeOQfsE+vXvxnhN7krROzd/6PUlJkU9nOJrK4mrzf7lPxcaiCt0IxE57msgkkpAQdZNf9G8tYFMr8Ns5PoDKV3YDRl47zp7OnTnUGz75tK6HC82SG3jXbTwhM6Q0U1sZvvFERVz77e1PtbwSptLBVwndN/+PNMxocb+OnGu0acJM/7mVa20Cw+Nb2CFCW2qtsIhFUndPml5wq/mAmTiT2yjep2HKKZ/7CF6r+ylKqqqmyTCdRwlcQNRmXfDeDaEP5JgFjUJzLghSDUfM2+m3UVkE4uthvkNvJz1aZAOgpNJbWv3U/jnnyeZi5bQRMmTHBEohFprfmZa6RC9eFwJcCDmg2igI5RCeP3sq7IKJ2BhzdnXosY0Zjz2gHUm0vltAe/TYFAoCgiVUByQGqhQyf5gBxftddwyiqGh3j056RuGKUTjqhoVR8mc8bf/r2wk6VGmtTdIpIoNWRxRwISCk4UtBqlVEeoUTpRaZcAkYWoOtQ8MG+xaaxZKuCmj1u+ltwArlmtS6icABjRVbczhNqRTqfQFvGM57avU21t6aXnvTOd9PKb79O+l9rpnfYOGn/7WlekFFDNnBxykcDweMeqBZnRigyhmAqjHsSY2xbkiLh0Tpw4MbMZiQ5yAo7T1h2/oG89/iL9aHeQLvQ4jynfaQ8JEqsry6lhUi2dPXeJdr/4vmtSCgnVSalqS+HxK30b5GZGD73E1mvyTcNdKEg6m3hsOeWqjKqDuMf+43VOQA09vHoJNTcGqKbKL0h2ipuWNIqHEaloC115c78rRRUM3UhO8Cyyv+HfYZqG2TBiLEpIaDqQHynNVfHCwMhJhrMHtOzguqUi85GAet52y7W0/Ym7aP7caYJMQD6XAnBQmDjhBhAuqh7foA2tUu0FoVnqrngyjE4WdMeb5upy83uXt3DJdGdigwpjJb5UAJn9nAuJSsMIhVR7QejwBC4BqLsaLPcXIp0Az7vLy8szm1Pq3XEYRoh5US45J3UwT6q9BFf7VjynCfWMqDvGtVUUVDrjhWRx8BIF8FaQTk46OGxD7TEBwg1gQoaq9jrzwkjYSU/H/UsXqJMUVGcEz1aIumt1k/OSibDnP3cfoZ/se7cgTw/8ZN+vRdjUzb+/ekUL/fJouhjtFqFylouETu05h/BFnqQv1ah+ya+czKBL1XKQsIV7/F+89VFGygrx9t09V8RzJBrnEnpEhFOAf9a15BZUTjBjUEWSkq0ebj914+uq/SxmYkIqlbL87J3joczrmqp0Ovpue4icAtGCBGJRue1WwQRQJdRYQ2CkNfpI0+bLqqhRVYod4gWpZqof6R8pSr/85u/F880mcWU+IJ6Fs4NkNs8KZKIIT1UNuQWjTwGpsr6B9QE+D6M6GdAbp9Cod8MJWO9FzL+0JHT1innC/kmAlBsLIBRAbIuHCjte3sMVo2o2FyLuP+N8ZCbyAdmCsTgEIZTv8ZHhRp8mVlukRdQ4Pl0wBqLiCYNwZkWRe5d/RQT0cEwNnMx7V7RQKWE26068P0xi7fXc/l2l/8wuoQC4kVzpfwsqz1gdDYuoOqc9FY1QwcD4USxKiUTCchczySoVZGjjG8clqIGTN4M7qsnZJErEPiVHwPA2pSPDrHUAPquFBEXnw5zUoaEhKhpJfh69PEMZ5BoT78q/L394+H6z/oVLj42sNsWDi543yRFyDBI2ulek5KOEA5OnU8EY4Pb7Uz58Gy4s0rBLZtdBrsJ9VDK4R+jlnsIl9NIbRKE2chNQc0hmKckE3CP0Qkh4eTgmNafPi3ina2RCIsOnecHnT87tpl1wQrVQ1npKoqILDKzjA+HrBgYGnBHamb/2CmLiF7Pf940f/jyW3gfSl+DJ1BB/xP6cfi4FrKIIjNfrJBQr1Ea+VGRwzFUenn5w0OFxon/M+XHPYWchjhvAsh4JlTMuQb08rmchua16r5IMzXZ1UCwWc/adpHW4BiLHmkxAF6/rskkW8nC1PCc3jVMHiya185xwTI6cU611ETrp8N64AWN6rg+htD5O6IiEGrMjY23UMTrOiCfYUdsIWFfcx/PTKZ9MYwqjkKnpOefyFCc0FVJ3UEkttmoDxyR+NJ5/hl4GkNDASsuPpz/Mk5QVY0esWi82ajQv3Z3yeSkV1JRZjQNnTvBxmfRd8BdbqEUKygP8ft9sMQXHNq7azE+EO6eoeXGm5vr0A148zn3f4MW0V0+ZlFSRfiLILxufjgJkwA+v7zRDAlROsopHzBPyNR04Ffpk7eJemYKiBioHuuT4TFFpKFf7IT6+ZFV5MoWXhyXXvcBvxrPcsVnPpfINk4SCh2MUsOQN4ZIqoQNqKY+HTGjRIa5QS1FQvq8OGZdkfIYH+ACmgDvGtEeIWl7LaQIKQR/n4dIRcgzjWixdAV4jMSSaFhkPy4yPwmupO9beUtzFsDPHxLMjO6qinJufxq1pYhvbKOUp7AbDHIBI5O5fHEkH/06hrl+F/VT9Da/WH8KzCOw9/qE9WsybmUCKzgjyblRhVe/zRag97GhvD7ejPmd21AhO7BAfVTn/X9sxeCMKw3BM/vqRDEkFCEOWBBuLrMoss3ICaCtWOEuEs6YmpYL4Kwht2nOqt2PN4qCcPYKJ+hOGFyfgQDW33CneKxgfHKOhm253ZkdNgAmw8sYiF3crHzcDpFNNOdEtYgQsCF+EV5mrSzH2aua1Qe2rTZZqO0IxdlSBKOyOEdRpjMYmCYxSe+XrDKFQe9FkahjqFL5i+4MUbUfHGMapnWFl7VIaaXUHMoRC7bmnykip8S4Yp0M7grSjRUqom8PDuZBr4jGPvvZIdQd0Bo0XSvao2+o0RpPp0M4AO+o0rzfAqo+TEVE/o8MLy+hHd1fQQHlxXUDyTzxO6ro/6AhtOtAe5D8flNvG6dCB9ZsLr5MO5/XFSGmlDbMTvN5H2+73c0J99FmAie1CASKdSCdg4nKZjnHVlsLLFar6Mq93XM5TYMxUVFyqZfTMCj+9/NUynVT+9pq864MtYVyfpS5gSCOZ1Zsk69d2ne4MbWqZhuk5YtkwCqh+brvkglks1Ut378ozAmnEUEJMwk1yUurq9AOtF/o76YVP/ofe7v5/ev/ySUqk+LCJ10/Vvuzi9Nnuk/Re8iy9P8tLA34PNfSlhBTubS2n7rps+QC5X/04RZVxjZwg3R5pRHgw4bbvtT2Z7bR0ntxr/J7F0sQFjRrznpT5PSTjqmde0y3VO//dBxxPhtBu30DE49GpU6dSZWVl5v21h2+niC87cbi69hq6a+b91DJxIb392a/of//8PEWTepMBovq9Gnm81vHtA28nOKn2bbedpZiMkk1GdQdMzwI7ahrbJbdBYM9PR6QbxDZs+bFzezpsR41qf2HA/MZ8Ev6Ydn7wfXrglytp95mdWWQCkMBYbIA0zVoCv6ix75hwTcZ+AMb1Wbzuuc2MTPF9skDzgfY2fhsyDU5RNFGX6qFoEnhoMzmBtKNqwRnqXiwY81Aibj1LxQmhgYe2GMh81rgCJiS4sUDOPJBpyXvUYB+NBlSvj0YoaC9kG4hHOamQUDndcUr1NF7tym/ftBzTI7EkPJkjHBuwOeiKa6lR5uijAILliRlgFTIlc/YeyUmoUP2UpvNkxiYt6NXkiNTO9BCWGj5VeXOPjKLrg1bE53ZiUWPfKeOKZCCXqkvkrVQ0HzyxU2Oks6dGA40TwfJnOzaV/SGdhqpqP6V6ak4bCAlM8LTVah9I+1AiwR/mUjoxYn3sdGu5tiwys5q4cDKb97fn7Ytnq/TTvP/4JjXgN/tBqP/0H/w8/0hpV0iM10ej0cxbC+qXWpIhfo+rM8iMRvqFrcQjPhinAX6MSDhMc88O0sLzTLy+0ttHUS79g7FBcUyQXTFobi7kEvGaPB1xUE3KZTdV2I56Ny1peJWSnuX85RRspxeEHRXdY6Rkym4yObvZIB6dM5+0unqxOrmsrIy+iH1O73QeobLyMt2uIDHGJXmiN0Dfv/lp6rzyKSUScQqU1dOc2rnU0j+RVh3ppjs/9tEN5710z4c+uraH0cRwWmL7tDhFEjF6sJ1R3aBe7TGii4Y0+RthsVNscGjFrg8v2MpIHLZq4/EpeXWt2nBCaNVmLFzkamOh3XgH0R3rafz48aLoHEmE6Y5DN9G4upFKMSQQZK6evY6+Oe+fqaYs25zgpp3/7jpyAtx0ZHvGPn1wtt07HjMW0kNwQvnspgpHedmu0xd6N83jkso8raRIavhXL4lbo+baINhKWhk88l//HSWTSUEqsqKTF39H3dEu7q2TQpUDvkn0vZt20arZ3xCfm558XcBR1obsZ8rjT5v26et55t/0DWkgmSy5wgmZ4tqoAHRsWFBHMe8rmqHdpZO2ktoTe7jeVdGMGTPEZLKPL39IG498U5zQfXMepK9f+5CpVBoByep68ls597FqDisTluy1rCzIYkOj0+5Sxdk1S9qYoU2EVfdDQG3Dlly2WqSh6D2CBwDVt0OiEecfX5c1Rg7VxtBNtaFXiARI7Nm9LWusjJvtXc0Hj2+iAlF0y+Cz31i0iXnYVuPUcozBoF+JmdcXDu2zEEXG1YsYEk2wioHsbgYSy2fO4TdzZXpw0WTaoWVzWNEy2F5olAslamqd7awkrMxAKSGXDMp/KGCGdAOa58wbKQh7yVXcob00Q0kIlTAzARIgtparoFu9662Qs10xpJIXgezGmHZQUkKBYWlt4y/Xm30OSUWDA0ygcLPnEqbJXDls3d2BW5pDpCW/Uwqp1B2XXEI+YgHZigNeGJOwCiUY6hw7c0KQCGeTe1IGwzDPNgz3kAtwjVAJO8SqQFkQzgVk+yZZ/HOVz7sEacbpMJYQveq4RBLb6xaRIz81SgCxSfK0esmzXqN09wP3waWRpV6lgdSeQmLKgn6RxgAZcpnnbkFuCf9BFR8KD3K/f3Q0SdSfwpcAHevQVSLVmNLYAg+j+SBYLOrlNQ0TskP4k15swUIp0s5hFvZY/YcvI/4CeAZjCToTSnsAAAAASUVORK5CYII=';
    const loadingImages = {
        darkMode: 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2220%22%20height%3D%2220%22%20viewBox%3D%220%200%2020%2020%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%20%20%20%20%20%20%3Cstyle%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%40keyframes%20rotate%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20from%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20transform%3A%20rotate%280deg%29%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20to%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20transform%3A%20rotate%28359deg%29%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%3C%2Fstyle%3E%0A%20%20%20%20%20%20%20%20%3Cg%20style%3D%22transform-origin%3A%2050%25%2050%25%3B%20animation%3A%20rotate%201s%20infinite%20reverse%20linear%3B%22%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2218.0968%22%20y%3D%2216.0861%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%28136.161%2018.0968%2016.0861%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.1%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.49878%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.4%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2219.9976%22%20y%3D%228.37451%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2890%2019.9976%208.37451%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.2%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2216.1727%22%20y%3D%221.9917%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2846.1607%2016.1727%201.9917%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.3%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.91309%22%20y%3D%226.88501%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%28136.161%208.91309%206.88501%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.6%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%226.79602%22%20y%3D%2210.996%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2846.1607%206.79602%2010.996%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.7%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%227%22%20y%3D%228.62549%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2890%207%208.62549%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.8%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.49878%22%20y%3D%2213%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.9%22%2F%3E%0A%20%20%20%20%20%20%20%20%3C%2Fg%3E%0A%20%20%20%20%3C%2Fsvg%3E',
        lightMode: 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2220%22%20height%3D%2220%22%20viewBox%3D%220%200%2020%2020%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%20%20%20%20%20%20%3Cstyle%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%40keyframes%20rotate%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20from%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20transform%3A%20rotate%280deg%29%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20to%20%7B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20transform%3A%20rotate%28359deg%29%3B%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%20%20%20%20%7D%0A%20%20%20%20%20%20%20%20%3C%2Fstyle%3E%0A%20%20%20%20%20%20%20%20%3Cg%20style%3D%22transform-origin%3A%2050%25%2050%25%3B%20animation%3A%20rotate%201s%20infinite%20reverse%20linear%3B%22%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2218.0968%22%20y%3D%2216.0861%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%28136.161%2018.0968%2016.0861%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.1%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.49878%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.4%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2219.9976%22%20y%3D%228.37451%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2890%2019.9976%208.37451%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.2%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%2216.1727%22%20y%3D%221.9917%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2846.1607%2016.1727%201.9917%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.3%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.91309%22%20y%3D%226.88501%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%28136.161%208.91309%206.88501%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.6%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%226.79602%22%20y%3D%2210.996%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2846.1607%206.79602%2010.996%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.7%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%227%22%20y%3D%228.62549%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20transform%3D%22rotate%2890%207%208.62549%29%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.8%22%2F%3E%0A%20%20%20%20%20%20%20%20%20%20%20%20%3Crect%20x%3D%228.49878%22%20y%3D%2213%22%20width%3D%223%22%20height%3D%227%22%20rx%3D%221.5%22%20fill%3D%22%23111111%22%20fill-opacity%3D%220.9%22%2F%3E%0A%20%20%20%20%20%20%20%20%3C%2Fg%3E%0A%20%20%20%20%3C%2Fsvg%3E' // 'data:application/octet-stream;base64,PHN2ZyB3aWR0aD0iMjAiIGhlaWdodD0iMjAiIHZpZXdCb3g9IjAgMCAyMCAyMCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KCTxzdHlsZT4KCQlAa2V5ZnJhbWVzIHJvdGF0ZSB7CgkJCWZyb20gewoJCQkJdHJhbnNmb3JtOiByb3RhdGUoMGRlZyk7CgkJCX0KCQkJdG8gewoJCQkJdHJhbnNmb3JtOiByb3RhdGUoMzU5ZGVnKTsKCQkJfQoJCX0KCTwvc3R5bGU+Cgk8ZyBzdHlsZT0idHJhbnNmb3JtLW9yaWdpbjogNTAlIDUwJTsgYW5pbWF0aW9uOiByb3RhdGUgMXMgaW5maW5pdGUgcmV2ZXJzZSBsaW5lYXI7Ij4KCQk8cmVjdCB4PSIxOC4wOTY4IiB5PSIxNi4wODYxIiB3aWR0aD0iMyIgaGVpZ2h0PSI3IiByeD0iMS41IiB0cmFuc2Zvcm09InJvdGF0ZSgxMzYuMTYxIDE4LjA5NjggMTYuMDg2MSkiIGZpbGw9IiNmZmZmZmYiIGZpbGwtb3BhY2l0eT0iMC4xIi8+CQoJCTxyZWN0IHg9IjguNDk4NzgiIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIGZpbGw9IiNmZmZmZmYiIGZpbGwtb3BhY2l0eT0iMC40Ii8+CgkJPHJlY3QgeD0iMTkuOTk3NiIgeT0iOC4zNzQ1MSIgd2lkdGg9IjMiIGhlaWdodD0iNyIgcng9IjEuNSIgdHJhbnNmb3JtPSJyb3RhdGUoOTAgMTkuOTk3NiA4LjM3NDUxKSIgZmlsbD0iI2ZmZmZmZiIgZmlsbC1vcGFjaXR5PSIwLjIiLz4KCQk8cmVjdCB4PSIxNi4xNzI3IiB5PSIxLjk5MTciIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIHRyYW5zZm9ybT0icm90YXRlKDQ2LjE2MDcgMTYuMTcyNyAxLjk5MTcpIiBmaWxsPSIjZmZmZmZmIiBmaWxsLW9wYWNpdHk9IjAuMyIvPgoJCTxyZWN0IHg9IjguOTEzMDkiIHk9IjYuODg1MDEiIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIHRyYW5zZm9ybT0icm90YXRlKDEzNi4xNjEgOC45MTMwOSA2Ljg4NTAxKSIgZmlsbD0iI2ZmZmZmZiIgZmlsbC1vcGFjaXR5PSIwLjYiLz4KCQk8cmVjdCB4PSI2Ljc5NjAyIiB5PSIxMC45OTYiIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIHRyYW5zZm9ybT0icm90YXRlKDQ2LjE2MDcgNi43OTYwMiAxMC45OTYpIiBmaWxsPSIjZmZmZmZmIiBmaWxsLW9wYWNpdHk9IjAuNyIvPgoJCTxyZWN0IHg9IjciIHk9IjguNjI1NDkiIHdpZHRoPSIzIiBoZWlnaHQ9IjciIHJ4PSIxLjUiIHRyYW5zZm9ybT0icm90YXRlKDkwIDcgOC42MjU0OSkiIGZpbGw9IiNmZmZmZmYiIGZpbGwtb3BhY2l0eT0iMC44Ii8+CQkKCQk8cmVjdCB4PSI4LjQ5ODc4IiB5PSIxMyIgd2lkdGg9IjMiIGhlaWdodD0iNyIgcng9IjEuNSIgZmlsbD0iI2ZmZmZmZiIgZmlsbC1vcGFjaXR5PSIwLjkiLz4KCTwvZz4KPC9zdmc+Cg=='
    };
    const closeIcon = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2212%22%20height%3D%2212%22%20viewBox%3D%220%200%2012%2012%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%3Cpath%20fill-rule%3D%22evenodd%22%20clip-rule%3D%22evenodd%22%20d%3D%22M5.99998%204.58578L10.2426%200.34314C10.6331%20-0.0473839%2011.2663%20-0.0473839%2011.6568%200.34314C12.0474%200.733665%2012.0474%201.36683%2011.6568%201.75735L7.41419%205.99999L11.6568%2010.2426C12.0474%2010.6332%2012.0474%2011.2663%2011.6568%2011.6568C11.2663%2012.0474%2010.6331%2012.0474%2010.2426%2011.6568L5.99998%207.41421L1.75734%2011.6568C1.36681%2012.0474%200.733649%2012.0474%200.343125%2011.6568C-0.0473991%2011.2663%20-0.0473991%2010.6332%200.343125%2010.2426L4.58577%205.99999L0.343125%201.75735C-0.0473991%201.36683%20-0.0473991%200.733665%200.343125%200.34314C0.733649%20-0.0473839%201.36681%20-0.0473839%201.75734%200.34314L5.99998%204.58578Z%22%20fill%3D%22%23222222%22%2F%3E%0A%3C%2Fsvg%3E';

    const blockedFBLogo = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2280%22%20height%3D%2280%22%20viewBox%3D%220%200%2080%2080%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%3Ccircle%20cx%3D%2240%22%20cy%3D%2240%22%20r%3D%2240%22%20fill%3D%22white%22%2F%3E%0A%3Cg%20clip-path%3D%22url%28%23clip0%29%22%3E%0A%3Cpath%20d%3D%22M73.8457%2039.974C73.8457%2021.284%2058.7158%206.15405%2040.0258%206.15405C21.3358%206.15405%206.15344%2021.284%206.15344%2039.974C6.15344%2056.884%2018.5611%2070.8622%2034.7381%2073.4275V49.764H26.0999V39.974H34.7381V32.5399C34.7381%2024.0587%2039.764%2019.347%2047.5122%2019.347C51.2293%2019.347%2055.0511%2020.0799%2055.0511%2020.0799V28.3517H50.8105C46.6222%2028.3517%2045.2611%2030.9693%2045.2611%2033.6393V39.974H54.6846L53.1664%2049.764H45.2611V73.4275C61.4381%2070.9146%2073.8457%2056.884%2073.8457%2039.974Z%22%20fill%3D%22%231877F2%22%2F%3E%0A%3C%2Fg%3E%0A%3Crect%20x%3D%223.01295%22%20y%3D%2211.7158%22%20width%3D%2212.3077%22%20height%3D%2292.3077%22%20rx%3D%226.15385%22%20transform%3D%22rotate%28-45%203.01295%2011.7158%29%22%20fill%3D%22%23666666%22%20stroke%3D%22white%22%20stroke-width%3D%226.15385%22%2F%3E%0A%3Cdefs%3E%0A%3CclipPath%20id%3D%22clip0%22%3E%0A%3Crect%20width%3D%2267.6923%22%20height%3D%2267.6923%22%20fill%3D%22white%22%20transform%3D%22translate%286.15344%206.15405%29%22%2F%3E%0A%3C%2FclipPath%3E%0A%3C%2Fdefs%3E%0A%3C%2Fsvg%3E';
    const facebookLogo = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjEiIGhlaWdodD0iMjAiIHZpZXdCb3g9IjAgMCAyMSAyMCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTguODUgMTkuOUM0LjEgMTkuMDUgMC41IDE0Ljk1IDAuNSAxMEMwLjUgNC41IDUgMCAxMC41IDBDMTYgMCAyMC41IDQuNSAyMC41IDEwQzIwLjUgMTQuOTUgMTYuOSAxOS4wNSAxMi4xNSAxOS45TDExLjYgMTkuNDVIOS40TDguODUgMTkuOVoiIGZpbGw9IiMxODc3RjIiLz4KPHBhdGggZD0iTTE0LjQgMTIuOEwxNC44NSAxMEgxMi4yVjguMDVDMTIuMiA3LjI1IDEyLjUgNi42NSAxMy43IDYuNjVIMTVWNC4xQzE0LjMgNCAxMy41IDMuOSAxMi44IDMuOUMxMC41IDMuOSA4LjkgNS4zIDguOSA3LjhWMTBINi40VjEyLjhIOC45VjE5Ljg1QzkuNDUgMTkuOTUgMTAgMjAgMTAuNTUgMjBDMTEuMSAyMCAxMS42NSAxOS45NSAxMi4yIDE5Ljg1VjEyLjhIMTQuNFoiIGZpbGw9IndoaXRlIi8+Cjwvc3ZnPgo=';

    const blockedYTVideo = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2275%22%20height%3D%2275%22%20viewBox%3D%220%200%2075%2075%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%3Crect%20x%3D%226.75%22%20y%3D%2215.75%22%20width%3D%2256.25%22%20height%3D%2239%22%20rx%3D%2213.5%22%20fill%3D%22%23DE5833%22%2F%3E%0A%20%20%3Cmask%20id%3D%22path-2-outside-1_885_11045%22%20maskUnits%3D%22userSpaceOnUse%22%20x%3D%2223.75%22%20y%3D%2222.5%22%20width%3D%2224%22%20height%3D%2226%22%20fill%3D%22black%22%3E%0A%20%20%3Crect%20fill%3D%22white%22%20x%3D%2223.75%22%20y%3D%2222.5%22%20width%3D%2224%22%20height%3D%2226%22%2F%3E%0A%20%20%3Cpath%20d%3D%22M41.9425%2037.5279C43.6677%2036.492%2043.6677%2033.9914%2041.9425%2032.9555L31.0394%2026.4088C29.262%2025.3416%2027%2026.6218%2027%2028.695L27%2041.7884C27%2043.8615%2029.262%2045.1418%2031.0394%2044.0746L41.9425%2037.5279Z%22%2F%3E%0A%20%20%3C%2Fmask%3E%0A%20%20%3Cpath%20d%3D%22M41.9425%2037.5279C43.6677%2036.492%2043.6677%2033.9914%2041.9425%2032.9555L31.0394%2026.4088C29.262%2025.3416%2027%2026.6218%2027%2028.695L27%2041.7884C27%2043.8615%2029.262%2045.1418%2031.0394%2044.0746L41.9425%2037.5279Z%22%20fill%3D%22white%22%2F%3E%0A%20%20%3Cpath%20d%3D%22M30.0296%2044.6809L31.5739%2047.2529L30.0296%2044.6809ZM30.0296%2025.8024L31.5739%2023.2304L30.0296%2025.8024ZM42.8944%2036.9563L44.4387%2039.5283L42.8944%2036.9563ZM41.35%2036.099L28.4852%2028.3744L31.5739%2023.2304L44.4387%2030.955L41.35%2036.099ZM30%2027.5171L30%2042.9663L24%2042.9663L24%2027.5171L30%2027.5171ZM28.4852%2042.1089L41.35%2034.3843L44.4387%2039.5283L31.5739%2047.2529L28.4852%2042.1089ZM30%2042.9663C30%2042.1888%2029.1517%2041.7087%2028.4852%2042.1089L31.5739%2047.2529C28.2413%2049.2539%2024%2046.8535%2024%2042.9663L30%2042.9663ZM28.4852%2028.3744C29.1517%2028.7746%2030%2028.2945%2030%2027.5171L24%2027.5171C24%2023.6299%2028.2413%2021.2294%2031.5739%2023.2304L28.4852%2028.3744ZM44.4387%2030.955C47.6735%2032.8974%2047.6735%2037.586%2044.4387%2039.5283L41.35%2034.3843C40.7031%2034.7728%2040.7031%2035.7105%2041.35%2036.099L44.4387%2030.955Z%22%20fill%3D%22%23BC4726%22%20mask%3D%22url(%23path-2-outside-1_885_11045)%22%2F%3E%0A%20%20%3Ccircle%20cx%3D%2257.75%22%20cy%3D%2252.5%22%20r%3D%2213.5%22%20fill%3D%22%23E0E0E0%22%2F%3E%0A%20%20%3Crect%20x%3D%2248.75%22%20y%3D%2250.25%22%20width%3D%2218%22%20height%3D%224.5%22%20rx%3D%221.5%22%20fill%3D%22%23666666%22%2F%3E%0A%20%20%3Cpath%20fill-rule%3D%22evenodd%22%20clip-rule%3D%22evenodd%22%20d%3D%22M57.9853%2015.8781C58.2046%2016.1015%2058.5052%2016.2262%2058.8181%2016.2238C59.1311%2016.2262%2059.4316%2016.1015%2059.6509%2015.8781L62.9821%2012.5469C63.2974%2012.2532%2063.4272%2011.8107%2063.3206%2011.3931C63.2139%2010.9756%2062.8879%2010.6495%2062.4703%2010.5429C62.0528%2010.4363%2061.6103%2010.5661%2061.3165%2010.8813L57.9853%2014.2125C57.7627%2014.4325%2057.6374%2014.7324%2057.6374%2015.0453C57.6374%2015.3583%2057.7627%2015.6582%2057.9853%2015.8781ZM61.3598%2018.8363C61.388%2019.4872%2061.9385%2019.9919%2062.5893%2019.9637L62.6915%2019.9559L66.7769%2019.6023C67.4278%2019.5459%2067.9097%2018.9726%2067.8533%2018.3217C67.7968%2017.6708%2067.2235%2017.1889%2066.5726%2017.2453L62.4872%2017.6067C61.8363%2017.6349%2061.3316%2018.1854%2061.3598%2018.8363Z%22%20fill%3D%22%23AAAAAA%22%20fill-opacity%3D%220.6%22%2F%3E%0A%20%20%3Cpath%20fill-rule%3D%22evenodd%22%20clip-rule%3D%22evenodd%22%20d%3D%22M10.6535%2015.8781C10.4342%2016.1015%2010.1336%2016.2262%209.82067%2016.2238C9.5077%2016.2262%209.20717%2016.1015%208.98787%2015.8781L5.65667%2012.5469C5.34138%2012.2532%205.2116%2011.8107%205.31823%2011.3931C5.42487%2010.9756%205.75092%2010.6495%206.16847%2010.5429C6.58602%2010.4363%207.02848%2010.5661%207.32227%2010.8813L10.6535%2014.2125C10.8761%2014.4325%2011.0014%2014.7324%2011.0014%2015.0453C11.0014%2015.3583%2010.8761%2015.6582%2010.6535%2015.8781ZM7.2791%2018.8362C7.25089%2019.4871%206.7004%2019.9919%206.04954%2019.9637L5.9474%2019.9558L1.86197%2019.6023C1.44093%2019.5658%201.07135%2019.3074%200.892432%2018.9246C0.713515%2018.5417%200.752449%2018.0924%200.994567%2017.7461C1.23669%2017.3997%201.6452%2017.2088%202.06624%2017.2453L6.15167%2017.6067C6.80254%2017.6349%207.3073%2018.1854%207.2791%2018.8362Z%22%20fill%3D%22%23AAAAAA%22%20fill-opacity%3D%220.6%22%2F%3E%0A%3C%2Fsvg%3E%0A';
    const videoPlayDark = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2222%22%20height%3D%2226%22%20viewBox%3D%220%200%2022%2026%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%3Cpath%20d%3D%22M21%2011.2679C22.3333%2012.0377%2022.3333%2013.9622%2021%2014.732L3%2025.1244C1.66667%2025.8942%202.59376e-06%2024.9319%202.66105e-06%2023.3923L3.56958e-06%202.60769C3.63688e-06%201.06809%201.66667%200.105844%203%200.875644L21%2011.2679Z%22%20fill%3D%22%23222222%22%2F%3E%0A%3C%2Fsvg%3E%0A';
    const videoPlayLight = 'data:image/svg+xml;utf8,%3Csvg%20width%3D%2222%22%20height%3D%2226%22%20viewBox%3D%220%200%2022%2026%22%20fill%3D%22none%22%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%3E%0A%20%20%3Cpath%20d%3D%22M21%2011.2679C22.3333%2012.0377%2022.3333%2013.9622%2021%2014.732L3%2025.1244C1.66667%2025.8942%202.59376e-06%2024.9319%202.66105e-06%2023.3923L3.56958e-06%202.60769C3.63688e-06%201.06809%201.66667%200.105844%203%200.875644L21%2011.2679Z%22%20fill%3D%22%23FFFFFF%22%2F%3E%0A%3C%2Fsvg%3E';

    var localesJSON = `{"bg":{"facebook.json":{"informationalModalMessageTitle":"При влизане разрешавате на Facebook да Ви проследява","informationalModalMessageBody":"След като влезете, DuckDuckGo не може да блокира проследяването от Facebook в съдържанието на този сайт.","informationalModalConfirmButtonText":"Вход","informationalModalRejectButtonText":"Назад","loginButtonText":"Вход във Facebook","loginBodyText":"Facebook проследява Вашата активност в съответния сайт, когато го използвате за вход.","buttonTextUnblockContent":"Разблокиране на съдържание от Facebook","buttonTextUnblockComment":"Разблокиране на коментар във Facebook","buttonTextUnblockComments":"Разблокиране на коментари във Facebook","buttonTextUnblockPost":"Разблокиране на публикация от Facebook","buttonTextUnblockVideo":"Разблокиране на видео от Facebook","buttonTextUnblockLogin":"Разблокиране на вход с Facebook","infoTitleUnblockContent":"DuckDuckGo блокира това съдържание, за да предотврати проследяване от Facebook","infoTitleUnblockComment":"DuckDuckGo блокира този коментар, за да предотврати проследяване от Facebook","infoTitleUnblockComments":"DuckDuckGo блокира тези коментари, за да предотврати проследяване от Facebook","infoTitleUnblockPost":"DuckDuckGo блокира тази публикация, за да предотврати проследяване от Facebook","infoTitleUnblockVideo":"DuckDuckGo блокира това видео, за да предотврати проследяване от Facebook","infoTextUnblockContent":"Блокирахме проследяването от Facebook при зареждане на страницата. Ако разблокирате това съдържание, Facebook ще следи Вашата активност."},"shared.json":{"learnMore":"Научете повече","readAbout":"Прочетете за тази защита на поверителността","shareFeedback":"Споделяне на отзив"},"youtube.json":{"informationalModalMessageTitle":"Активиране на всички прегледи в YouTube?","informationalModalMessageBody":"Показването на преглед позволява на Google (собственик на YouTube) да види част от информацията за Вашето устройство, но все пак осигурява повече поверителност отколкото при възпроизвеждане на видеоклипа.","informationalModalConfirmButtonText":"Активиране на всички прегледи","informationalModalRejectButtonText":"Не, благодаря","buttonTextUnblockVideo":"Разблокиране на видео от YouTube","infoTitleUnblockVideo":"DuckDuckGo блокира този видеоклип в YouTube, за да предотврати проследяване от Google","infoTextUnblockVideo":"Блокирахме проследяването от Google (собственик на YouTube) при зареждане на страницата. Ако разблокирате този видеоклип, Google ще следи Вашата активност.","infoPreviewToggleText":"Прегледите са деактивирани за осигуряване на допълнителна поверителност","infoPreviewToggleEnabledText":"Прегледите са активирани","infoPreviewToggleEnabledDuckDuckGoText":"Визуализациите от YouTube са активирани в DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Научете повече</a> за вградената защита от социални медии на DuckDuckGo"}},"cs":{"facebook.json":{"informationalModalMessageTitle":"Když se přihlásíš přes Facebook, bude tě moct sledovat","informationalModalMessageBody":"Po přihlášení už DuckDuckGo nemůže bránit Facebooku, aby tě na téhle stránce sledoval.","informationalModalConfirmButtonText":"Přihlásit se","informationalModalRejectButtonText":"Zpět","loginButtonText":"Přihlásit se pomocí Facebooku","loginBodyText":"Facebook sleduje tvou aktivitu na webu, když se přihlásíš jeho prostřednictvím.","buttonTextUnblockContent":"Odblokovat obsah na Facebooku","buttonTextUnblockComment":"Odblokovat komentář na Facebooku","buttonTextUnblockComments":"Odblokovat komentáře na Facebooku","buttonTextUnblockPost":"Odblokovat příspěvek na Facebooku","buttonTextUnblockVideo":"Odblokovat video na Facebooku","buttonTextUnblockLogin":"Odblokovat přihlášení k Facebooku","infoTitleUnblockContent":"DuckDuckGo zablokoval tenhle obsah, aby Facebooku zabránil tě sledovat","infoTitleUnblockComment":"Služba DuckDuckGo zablokovala tento komentář, aby Facebooku zabránila ve tvém sledování","infoTitleUnblockComments":"Služba DuckDuckGo zablokovala tyto komentáře, aby Facebooku zabránila ve tvém sledování","infoTitleUnblockPost":"DuckDuckGo zablokoval tenhle příspěvek, aby Facebooku zabránil tě sledovat","infoTitleUnblockVideo":"DuckDuckGo zablokoval tohle video, aby Facebooku zabránil tě sledovat","infoTextUnblockContent":"Při načítání stránky jsme Facebooku zabránili, aby tě sledoval. Když tenhle obsah odblokuješ, Facebook bude mít přístup ke tvé aktivitě."},"shared.json":{"learnMore":"Více informací","readAbout":"Přečti si o téhle ochraně soukromí","shareFeedback":"Podělte se o zpětnou vazbu"},"youtube.json":{"informationalModalMessageTitle":"Zapnout všechny náhledy YouTube?","informationalModalMessageBody":"Zobrazování náhledů umožní společnosti Google (která vlastní YouTube) zobrazit některé informace o tvém zařízení, ale pořád jde o diskrétnější volbu, než je přehrávání videa.","informationalModalConfirmButtonText":"Zapnout všechny náhledy","informationalModalRejectButtonText":"Ne, děkuji","buttonTextUnblockVideo":"Odblokovat video na YouTube","infoTitleUnblockVideo":"DuckDuckGo zablokoval tohle video z YouTube, aby Googlu zabránil tě sledovat","infoTextUnblockVideo":"Zabránili jsme společnosti Google (která vlastní YouTube), aby tě při načítání stránky sledovala. Pokud toto video odblokuješ, Google získá přístup ke tvé aktivitě.","infoPreviewToggleText":"Náhledy jsou pro větší soukromí vypnuté","infoPreviewToggleEnabledText":"Náhledy jsou zapnuté","infoPreviewToggleEnabledDuckDuckGoText":"Náhledy YouTube jsou v DuckDuckGo povolené.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Další informace</a> o ochraně DuckDuckGo před sledováním prostřednictvím vloženého obsahu ze sociálních médií"}},"da":{"facebook.json":{"informationalModalMessageTitle":"Når du logger ind med Facebook, kan de spore dig","informationalModalMessageBody":"Når du er logget ind, kan DuckDuckGo ikke blokere for, at indhold fra Facebook sporer dig på dette websted.","informationalModalConfirmButtonText":"Log på","informationalModalRejectButtonText":"Gå tilbage","loginButtonText":"Log ind med Facebook","loginBodyText":"Facebook sporer din aktivitet på et websted, når du bruger dem til at logge ind.","buttonTextUnblockContent":"Bloker ikke Facebook-indhold","buttonTextUnblockComment":"Bloker ikke Facebook-kommentar","buttonTextUnblockComments":"Bloker ikke Facebook-kommentarer","buttonTextUnblockPost":"Bloker ikke Facebook-opslag","buttonTextUnblockVideo":"Bloker ikke Facebook-video","buttonTextUnblockLogin":"Bloker ikke Facebook-login","infoTitleUnblockContent":"DuckDuckGo har blokeret dette indhold for at forhindre Facebook i at spore dig","infoTitleUnblockComment":"DuckDuckGo har blokeret denne kommentar for at forhindre Facebook i at spore dig","infoTitleUnblockComments":"DuckDuckGo har blokeret disse kommentarer for at forhindre Facebook i at spore dig","infoTitleUnblockPost":"DuckDuckGo blokerede dette indlæg for at forhindre Facebook i at spore dig","infoTitleUnblockVideo":"DuckDuckGo har blokeret denne video for at forhindre Facebook i at spore dig","infoTextUnblockContent":"Vi blokerede for, at Facebook sporede dig, da siden blev indlæst. Hvis du ophæver blokeringen af dette indhold, vil Facebook kende din aktivitet."},"shared.json":{"learnMore":"Mere info","readAbout":"Læs om denne beskyttelse af privatlivet","shareFeedback":"Del feedback"},"youtube.json":{"informationalModalMessageTitle":"Vil du aktivere alle YouTube-forhåndsvisninger?","informationalModalMessageBody":"Med forhåndsvisninger kan Google (som ejer YouTube) se nogle af enhedens oplysninger, men det er stadig mere privat end at afspille videoen.","informationalModalConfirmButtonText":"Aktivér alle forhåndsvisninger","informationalModalRejectButtonText":"Nej tak.","buttonTextUnblockVideo":"Bloker ikke YouTube-video","infoTitleUnblockVideo":"DuckDuckGo har blokeret denne YouTube-video for at forhindre Google i at spore dig","infoTextUnblockVideo":"Vi blokerede Google (som ejer YouTube) fra at spore dig, da siden blev indlæst. Hvis du fjerner blokeringen af denne video, vil Google få kendskab til din aktivitet.","infoPreviewToggleText":"Forhåndsvisninger er deaktiveret for at give yderligere privatliv","infoPreviewToggleEnabledText":"Forhåndsvisninger er deaktiveret","infoPreviewToggleEnabledDuckDuckGoText":"YouTube-forhåndsvisninger er aktiveret i DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Få mere at vide på</a> om DuckDuckGos indbyggede beskyttelse på sociale medier"}},"de":{"facebook.json":{"informationalModalMessageTitle":"Wenn du dich bei Facebook anmeldest, kann Facebook dich tracken","informationalModalMessageBody":"Sobald du angemeldet bist, kann DuckDuckGo nicht mehr verhindern, dass Facebook-Inhalte dich auf dieser Website tracken.","informationalModalConfirmButtonText":"Anmelden","informationalModalRejectButtonText":"Zurück","loginButtonText":"Mit Facebook anmelden","loginBodyText":"Facebook trackt deine Aktivität auf einer Website, wenn du dich über Facebook dort anmeldest.","buttonTextUnblockContent":"Facebook-Inhalt entsperren","buttonTextUnblockComment":"Facebook-Kommentar entsperren","buttonTextUnblockComments":"Facebook-Kommentare entsperren","buttonTextUnblockPost":"Facebook-Beitrag entsperren","buttonTextUnblockVideo":"Facebook-Video entsperren","buttonTextUnblockLogin":"Facebook-Anmeldung entsperren","infoTitleUnblockContent":"DuckDuckGo hat diesen Inhalt blockiert, um zu verhindern, dass Facebook dich trackt","infoTitleUnblockComment":"DuckDuckGo hat diesen Kommentar blockiert, um zu verhindern, dass Facebook dich trackt","infoTitleUnblockComments":"DuckDuckGo hat diese Kommentare blockiert, um zu verhindern, dass Facebook dich trackt","infoTitleUnblockPost":"DuckDuckGo hat diesen Beitrag blockiert, um zu verhindern, dass Facebook dich trackt","infoTitleUnblockVideo":"DuckDuckGo hat dieses Video blockiert, um zu verhindern, dass Facebook dich trackt","infoTextUnblockContent":"Wir haben Facebook daran gehindert, dich zu tracken, als die Seite geladen wurde. Wenn du die Blockierung für diesen Inhalt aufhebst, kennt Facebook deine Aktivitäten."},"shared.json":{"learnMore":"Mehr erfahren","readAbout":"Weitere Informationen über diesen Datenschutz","shareFeedback":"Feedback teilen"},"youtube.json":{"informationalModalMessageTitle":"Alle YouTube-Vorschauen aktivieren?","informationalModalMessageBody":"Durch das Anzeigen von Vorschauen kann Google (dem YouTube gehört) einige Informationen zu deinem Gerät sehen. Dies ist aber immer noch privater als das Abspielen des Videos.","informationalModalConfirmButtonText":"Alle Vorschauen aktivieren","informationalModalRejectButtonText":"Nein, danke","buttonTextUnblockVideo":"YouTube-Video entsperren","infoTitleUnblockVideo":"DuckDuckGo hat dieses YouTube-Video blockiert, um zu verhindern, dass Google dich trackt.","infoTextUnblockVideo":"Wir haben Google (dem YouTube gehört) daran gehindert, dich beim Laden der Seite zu tracken. Wenn du die Blockierung für dieses Video aufhebst, kennt Google deine Aktivitäten.","infoPreviewToggleText":"Vorschau für mehr Privatsphäre deaktiviert","infoPreviewToggleEnabledText":"Vorschau aktiviert","infoPreviewToggleEnabledDuckDuckGoText":"YouTube-Vorschauen sind in DuckDuckGo aktiviert.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Erfahre mehr</a> über den DuckDuckGo-Schutz vor eingebetteten Social Media-Inhalten"}},"el":{"facebook.json":{"informationalModalMessageTitle":"Η σύνδεση μέσω Facebook τους επιτρέπει να σας παρακολουθούν","informationalModalMessageBody":"Μόλις συνδεθείτε, το DuckDuckGo δεν μπορεί να εμποδίσει το περιεχόμενο του Facebook από το να σας παρακολουθεί σε αυτόν τον ιστότοπο.","informationalModalConfirmButtonText":"Σύνδεση","informationalModalRejectButtonText":"Επιστροφή","loginButtonText":"Σύνδεση μέσω Facebook","loginBodyText":"Το Facebook παρακολουθεί τη δραστηριότητά σας σε έναν ιστότοπο όταν τον χρησιμοποιείτε για να συνδεθείτε.","buttonTextUnblockContent":"Άρση αποκλεισμού περιεχομένου στο Facebook","buttonTextUnblockComment":"Άρση αποκλεισμού σχόλιου στο Facebook","buttonTextUnblockComments":"Άρση αποκλεισμού σχολίων στο Facebook","buttonTextUnblockPost":"Άρση αποκλεισμού ανάρτησης στο Facebook","buttonTextUnblockVideo":"Άρση αποκλεισμού βίντεο στο Facebook","buttonTextUnblockLogin":"Άρση αποκλεισμού σύνδεσης στο Facebook","infoTitleUnblockContent":"Το DuckDuckGo απέκλεισε το περιεχόμενο αυτό για να εμποδίσει το Facebook από το να σας παρακολουθεί","infoTitleUnblockComment":"Το DuckDuckGo απέκλεισε το σχόλιο αυτό για να εμποδίσει το Facebook από το να σας παρακολουθεί","infoTitleUnblockComments":"Το DuckDuckGo απέκλεισε τα σχόλια αυτά για να εμποδίσει το Facebook από το να σας παρακολουθεί","infoTitleUnblockPost":"Το DuckDuckGo απέκλεισε την ανάρτηση αυτή για να εμποδίσει το Facebook από το να σας παρακολουθεί","infoTitleUnblockVideo":"Το DuckDuckGo απέκλεισε το βίντεο αυτό για να εμποδίσει το Facebook από το να σας παρακολουθεί","infoTextUnblockContent":"Αποκλείσαμε το Facebook από το να σας παρακολουθεί όταν φορτώθηκε η σελίδα. Εάν κάνετε άρση αποκλεισμού γι' αυτό το περιεχόμενο, το Facebook θα γνωρίζει τη δραστηριότητά σας."},"shared.json":{"learnMore":"Μάθετε περισσότερα","readAbout":"Διαβάστε σχετικά με την παρούσα προστασίας προσωπικών δεδομένων","shareFeedback":"Κοινοποίηση σχολίου"},"youtube.json":{"informationalModalMessageTitle":"Ενεργοποίηση όλων των προεπισκοπήσεων του YouTube;","informationalModalMessageBody":"Η προβολή των προεπισκοπήσεων θα επιτρέψει στην Google (στην οποία ανήκει το YouTube) να βλέπει ορισμένες από τις πληροφορίες της συσκευής σας, ωστόσο εξακολουθεί να είναι πιο ιδιωτική από την αναπαραγωγή του βίντεο.","informationalModalConfirmButtonText":"Ενεργοποίηση όλων των προεπισκοπήσεων","informationalModalRejectButtonText":"Όχι, ευχαριστώ","buttonTextUnblockVideo":"Άρση αποκλεισμού βίντεο YouTube","infoTitleUnblockVideo":"Το DuckDuckGo απέκλεισε το βίντεο αυτό στο YouTube για να εμποδίσει την Google από το να σας παρακολουθεί","infoTextUnblockVideo":"Αποκλείσαμε την Google (στην οποία ανήκει το YouTube) από το να σας παρακολουθεί όταν φορτώθηκε η σελίδα. Εάν κάνετε άρση αποκλεισμού γι' αυτό το βίντεο, η Google θα γνωρίζει τη δραστηριότητά σας.","infoPreviewToggleText":"Οι προεπισκοπήσεις απενεργοποιήθηκαν για πρόσθετη προστασία των προσωπικών δεδομένων","infoPreviewToggleEnabledText":"Οι προεπισκοπήσεις ενεργοποιήθηκαν","infoPreviewToggleEnabledDuckDuckGoText":"Οι προεπισκοπήσεις YouTube ενεργοποιήθηκαν στο DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Μάθετε περισσότερα</a> για την ενσωματωμένη προστασία κοινωνικών μέσων DuckDuckGo"}},"en":{"facebook.json":{"informationalModalMessageTitle":"Logging in with Facebook lets them track you","informationalModalMessageBody":"Once you're logged in, DuckDuckGo can't block Facebook content from tracking you on this site.","informationalModalConfirmButtonText":"Log In","informationalModalRejectButtonText":"Go back","loginButtonText":"Log in with Facebook","loginBodyText":"Facebook tracks your activity on a site when you use them to login.","buttonTextUnblockContent":"Unblock Facebook Content","buttonTextUnblockComment":"Unblock Facebook Comment","buttonTextUnblockComments":"Unblock Facebook Comments","buttonTextUnblockPost":"Unblock Facebook Post","buttonTextUnblockVideo":"Unblock Facebook Video","buttonTextUnblockLogin":"Unblock Facebook Login","infoTitleUnblockContent":"DuckDuckGo blocked this content to prevent Facebook from tracking you","infoTitleUnblockComment":"DuckDuckGo blocked this comment to prevent Facebook from tracking you","infoTitleUnblockComments":"DuckDuckGo blocked these comments to prevent Facebook from tracking you","infoTitleUnblockPost":"DuckDuckGo blocked this post to prevent Facebook from tracking you","infoTitleUnblockVideo":"DuckDuckGo blocked this video to prevent Facebook from tracking you","infoTextUnblockContent":"We blocked Facebook from tracking you when the page loaded. If you unblock this content, Facebook will know your activity."},"shared.json":{"learnMore":"Learn More","readAbout":"Read about this privacy protection","shareFeedback":"Share Feedback"},"youtube.json":{"informationalModalMessageTitle":"Enable all YouTube previews?","informationalModalMessageBody":"Showing previews will allow Google (which owns YouTube) to see some of your device’s information, but is still more private than playing the video.","informationalModalConfirmButtonText":"Enable All Previews","informationalModalRejectButtonText":"No Thanks","buttonTextUnblockVideo":"Unblock YouTube Video","infoTitleUnblockVideo":"DuckDuckGo blocked this YouTube video to prevent Google from tracking you","infoTextUnblockVideo":"We blocked Google (which owns YouTube) from tracking you when the page loaded. If you unblock this video, Google will know your activity.","infoPreviewToggleText":"Previews disabled for additional privacy","infoPreviewToggleEnabledText":"Previews enabled","infoPreviewToggleEnabledDuckDuckGoText":"YouTube previews enabled in DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Learn more</a> about DuckDuckGo Embedded Social Media Protection"}},"es":{"facebook.json":{"informationalModalMessageTitle":"Al iniciar sesión en Facebook, les permites que te rastreen","informationalModalMessageBody":"Una vez que hayas iniciado sesión, DuckDuckGo no puede bloquear el contenido de Facebook para que no te rastree en este sitio.","informationalModalConfirmButtonText":"Iniciar sesión","informationalModalRejectButtonText":"Volver atrás","loginButtonText":"Iniciar sesión con Facebook","loginBodyText":"Facebook rastrea tu actividad en un sitio web cuando lo usas para iniciar sesión.","buttonTextUnblockContent":"Desbloquear contenido de Facebook","buttonTextUnblockComment":"Desbloquear comentario de Facebook","buttonTextUnblockComments":"Desbloquear comentarios de Facebook","buttonTextUnblockPost":"Desbloquear publicación de Facebook","buttonTextUnblockVideo":"Desbloquear vídeo de Facebook","buttonTextUnblockLogin":"Desbloquear inicio de sesión de Facebook","infoTitleUnblockContent":"DuckDuckGo ha bloqueado este contenido para evitar que Facebook te rastree","infoTitleUnblockComment":"DuckDuckGo ha bloqueado este comentario para evitar que Facebook te rastree","infoTitleUnblockComments":"DuckDuckGo ha bloqueado estos comentarios para evitar que Facebook te rastree","infoTitleUnblockPost":"DuckDuckGo ha bloqueado esta publicación para evitar que Facebook te rastree","infoTitleUnblockVideo":"DuckDuckGo ha bloqueado este vídeo para evitar que Facebook te rastree","infoTextUnblockContent":"Hemos bloqueado el rastreo de Facebook cuando se ha cargado la página. Si desbloqueas este contenido, Facebook tendrá conocimiento de tu actividad."},"shared.json":{"learnMore":"Más información","readAbout":"Lee acerca de esta protección de privacidad","shareFeedback":"Compartir opiniones"},"youtube.json":{"informationalModalMessageTitle":"¿Habilitar todas las vistas previas de YouTube?","informationalModalMessageBody":"Mostrar vistas previas permitirá a Google (que es el propietario de YouTube) ver parte de la información de tu dispositivo, pero sigue siendo más privado que reproducir el vídeo.","informationalModalConfirmButtonText":"Habilitar todas las vistas previas","informationalModalRejectButtonText":"No, gracias","buttonTextUnblockVideo":"Desbloquear vídeo de YouTube","infoTitleUnblockVideo":"DuckDuckGo ha bloqueado este vídeo de YouTube para evitar que Google te rastree","infoTextUnblockVideo":"Hemos bloqueado el rastreo de Google (que es el propietario de YouTube) al cargarse la página. Si desbloqueas este vídeo, Goggle tendrá conocimiento de tu actividad.","infoPreviewToggleText":"Vistas previas desactivadas para mayor privacidad","infoPreviewToggleEnabledText":"Vistas previas activadas","infoPreviewToggleEnabledDuckDuckGoText":"Vistas previas de YouTube habilitadas en DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Más información</a> sobre la protección integrada de redes sociales DuckDuckGo"}},"et":{"facebook.json":{"informationalModalMessageTitle":"Kui logid Facebookiga sisse, saab Facebook sind jälgida","informationalModalMessageBody":"Kui oled sisse logitud, ei saa DuckDuckGo blokeerida Facebooki sisu sind jälgimast.","informationalModalConfirmButtonText":"Logi sisse","informationalModalRejectButtonText":"Mine tagasi","loginButtonText":"Logi sisse Facebookiga","loginBodyText":"Kui logid sisse Facebookiga, saab Facebook sinu tegevust saidil jälgida.","buttonTextUnblockContent":"Deblokeeri Facebooki sisu","buttonTextUnblockComment":"Deblokeeri Facebooki kommentaar","buttonTextUnblockComments":"Deblokeeri Facebooki kommentaarid","buttonTextUnblockPost":"Deblokeeri Facebooki postitus","buttonTextUnblockVideo":"Deblokeeri Facebooki video","buttonTextUnblockLogin":"Deblokeeri Facebooki sisselogimine","infoTitleUnblockContent":"DuckDuckGo blokeeris selle sisu, et Facebook ei saaks sind jälgida","infoTitleUnblockComment":"DuckDuckGo blokeeris selle kommentaari, et Facebook ei saaks sind jälgida","infoTitleUnblockComments":"DuckDuckGo blokeeris need kommentaarid, et Facebook ei saaks sind jälgida","infoTitleUnblockPost":"DuckDuckGo blokeeris selle postituse, et Facebook ei saaks sind jälgida","infoTitleUnblockVideo":"DuckDuckGo blokeeris selle video, et Facebook ei saaks sind jälgida","infoTextUnblockContent":"Blokeerisime lehe laadimise ajal Facebooki jaoks sinu jälgimise. Kui sa selle sisu deblokeerid, saab Facebook sinu tegevust jälgida."},"shared.json":{"learnMore":"Loe edasi","readAbout":"Loe selle privaatsuskaitse kohta","shareFeedback":"Jaga tagasisidet"},"youtube.json":{"informationalModalMessageTitle":"Kas lubada kõik YouTube’i eelvaated?","informationalModalMessageBody":"Eelvaate näitamine võimaldab Google’il (kellele YouTube kuulub) näha osa sinu seadme teabest, kuid see on siiski privaatsem kui video esitamine.","informationalModalConfirmButtonText":"Luba kõik eelvaated","informationalModalRejectButtonText":"Ei aitäh","buttonTextUnblockVideo":"Deblokeeri YouTube’i video","infoTitleUnblockVideo":"DuckDuckGo blokeeris selle YouTube’i video, et takistada Google’it sind jälgimast","infoTextUnblockVideo":"Me blokeerisime lehe laadimise ajal Google’i (kellele YouTube kuulub) jälgimise. Kui sa selle video deblokeerid, saab Google sinu tegevusest teada.","infoPreviewToggleText":"Eelvaated on täiendava privaatsuse tagamiseks keelatud","infoPreviewToggleEnabledText":"Eelvaated on lubatud","infoPreviewToggleEnabledDuckDuckGoText":"YouTube’i eelvaated on DuckDuckGos lubatud.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Lisateave</a> DuckDuckGo sisseehitatud sotsiaalmeediakaitse kohta"}},"fi":{"facebook.json":{"informationalModalMessageTitle":"Kun kirjaudut sisään Facebook-tunnuksilla, Facebook voi seurata sinua","informationalModalMessageBody":"Kun olet kirjautunut sisään, DuckDuckGo ei voi estää Facebook-sisältöä seuraamasta sinua tällä sivustolla.","informationalModalConfirmButtonText":"Kirjaudu sisään","informationalModalRejectButtonText":"Edellinen","loginButtonText":"Kirjaudu sisään Facebook-tunnuksilla","loginBodyText":"Facebook seuraa toimintaasi sivustolla, kun kirjaudut sisään sen kautta.","buttonTextUnblockContent":"Poista Facebook-sisällön esto","buttonTextUnblockComment":"Poista Facebook-kommentin esto","buttonTextUnblockComments":"Poista Facebook-kommenttien esto","buttonTextUnblockPost":"Poista Facebook-julkaisun esto","buttonTextUnblockVideo":"Poista Facebook-videon esto","buttonTextUnblockLogin":"Poista Facebook-kirjautumisen esto","infoTitleUnblockContent":"DuckDuckGo esti tämän sisällön estääkseen Facebookia seuraamasta sinua","infoTitleUnblockComment":"DuckDuckGo esti tämän kommentin estääkseen Facebookia seuraamasta sinua","infoTitleUnblockComments":"DuckDuckGo esti nämä kommentit estääkseen Facebookia seuraamasta sinua","infoTitleUnblockPost":"DuckDuckGo esti tämän julkaisun estääkseen Facebookia seuraamasta sinua","infoTitleUnblockVideo":"DuckDuckGo esti tämän videon estääkseen Facebookia seuraamasta sinua","infoTextUnblockContent":"Estimme Facebookia seuraamasta sinua, kun sivua ladattiin. Jos poistat tämän sisällön eston, Facebook saa tietää toimintasi."},"shared.json":{"learnMore":"Lue lisää","readAbout":"Lue tästä yksityisyydensuojasta","shareFeedback":"Jaa palaute"},"youtube.json":{"informationalModalMessageTitle":"Otetaanko käyttöön kaikki YouTube-esikatselut?","informationalModalMessageBody":"Kun sallit esikatselun, Google (joka omistaa YouTuben) voi nähdä joitakin laitteesi tietoja, mutta se on silti yksityisempää kuin videon toistaminen.","informationalModalConfirmButtonText":"Ota käyttöön kaikki esikatselut","informationalModalRejectButtonText":"Ei kiitos","buttonTextUnblockVideo":"Poista YouTube-videon esto","infoTitleUnblockVideo":"DuckDuckGo esti tämän YouTube-videon, jotta Google ei voi seurata sinua","infoTextUnblockVideo":"Estimme Googlea (joka omistaa YouTuben) seuraamasta sinua, kun sivua ladattiin. Jos poistat tämän videon eston, Google tietää toimintasi.","infoPreviewToggleText":"Esikatselut on poistettu käytöstä yksityisyyden lisäämiseksi","infoPreviewToggleEnabledText":"Esikatselut käytössä","infoPreviewToggleEnabledDuckDuckGoText":"YouTube-esikatselut käytössä DuckDuckGossa.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Lue lisää</a> DuckDuckGon upotetusta sosiaalisen median suojauksesta"}},"fr":{"facebook.json":{"informationalModalMessageTitle":"L'identification via Facebook leur permet de vous pister","informationalModalMessageBody":"Une fois que vous êtes connecté(e), DuckDuckGo ne peut pas empêcher le contenu Facebook de vous pister sur ce site.","informationalModalConfirmButtonText":"Connexion","informationalModalRejectButtonText":"Revenir en arrière","loginButtonText":"S'identifier avec Facebook","loginBodyText":"Facebook piste votre activité sur un site lorsque vous l'utilisez pour vous identifier.","buttonTextUnblockContent":"Débloquer le contenu Facebook","buttonTextUnblockComment":"Débloquer le commentaire Facebook","buttonTextUnblockComments":"Débloquer les commentaires Facebook","buttonTextUnblockPost":"Débloquer la publication Facebook","buttonTextUnblockVideo":"Débloquer la vidéo Facebook","buttonTextUnblockLogin":"Débloquer la connexion Facebook","infoTitleUnblockContent":"DuckDuckGo a bloqué ce contenu pour empêcher Facebook de vous suivre","infoTitleUnblockComment":"DuckDuckGo a bloqué ce commentaire pour empêcher Facebook de vous suivre","infoTitleUnblockComments":"DuckDuckGo a bloqué ces commentaires pour empêcher Facebook de vous suivre","infoTitleUnblockPost":"DuckDuckGo a bloqué cette publication pour empêcher Facebook de vous pister","infoTitleUnblockVideo":"DuckDuckGo a bloqué cette vidéo pour empêcher Facebook de vous pister","infoTextUnblockContent":"Nous avons empêché Facebook de vous pister lors du chargement de la page. Si vous débloquez ce contenu, Facebook connaîtra votre activité."},"shared.json":{"learnMore":"En savoir plus","readAbout":"En savoir plus sur cette protection de la confidentialité","shareFeedback":"Partagez vos commentaires"},"youtube.json":{"informationalModalMessageTitle":"Activer tous les aperçus YouTube ?","informationalModalMessageBody":"L'affichage des aperçus permettra à Google (propriétaire de YouTube) de voir certaines informations de votre appareil, mais cela reste davantage confidentiel qu'en lisant la vidéo.","informationalModalConfirmButtonText":"Activer tous les aperçus","informationalModalRejectButtonText":"Non merci","buttonTextUnblockVideo":"Débloquer la vidéo YouTube","infoTitleUnblockVideo":"DuckDuckGo a bloqué cette vidéo YouTube pour empêcher Google de vous pister","infoTextUnblockVideo":"Nous avons empêché Google (propriétaire de YouTube) de vous pister lors du chargement de la page. Si vous débloquez cette vidéo, Google connaîtra votre activité.","infoPreviewToggleText":"Aperçus désactivés pour plus de confidentialité","infoPreviewToggleEnabledText":"Aperçus activés","infoPreviewToggleEnabledDuckDuckGoText":"Les aperçus YouTube sont activés dans DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">En savoir plus</a> sur la protection intégrée DuckDuckGo des réseaux sociaux"}},"hr":{"facebook.json":{"informationalModalMessageTitle":"Prijava putem Facebooka omogućuje im da te prate","informationalModalMessageBody":"Nakon što se prijaviš, DuckDuckGo ne može blokirati Facebookov sadržaj da te prati na Facebooku.","informationalModalConfirmButtonText":"Prijavljivanje","informationalModalRejectButtonText":"Vrati se","loginButtonText":"Prijavi se putem Facebooka","loginBodyText":"Facebook prati tvoju aktivnost na toj web lokaciji kad je koristiš za prijavu.","buttonTextUnblockContent":"Deblokiraj sadržaj na Facebooku","buttonTextUnblockComment":"Deblokiraj komentar na Facebooku","buttonTextUnblockComments":"Deblokiraj komentare na Facebooku","buttonTextUnblockPost":"Deblokiraj objavu na Facebooku","buttonTextUnblockVideo":"Deblokiraj videozapis na Facebooku","buttonTextUnblockLogin":"Deblokiraj prijavu na Facebook","infoTitleUnblockContent":"DuckDuckGo je blokirao ovaj sadržaj kako bi spriječio Facebook da te prati","infoTitleUnblockComment":"DuckDuckGo je blokirao ovaj komentar kako bi spriječio Facebook da te prati","infoTitleUnblockComments":"DuckDuckGo je blokirao ove komentare kako bi spriječio Facebook da te prati","infoTitleUnblockPost":"DuckDuckGo je blokirao ovu objavu kako bi spriječio Facebook da te prati","infoTitleUnblockVideo":"DuckDuckGo je blokirao ovaj video kako bi spriječio Facebook da te prati","infoTextUnblockContent":"Blokirali smo Facebook da te prati kad se stranica učita. Ako deblokiraš ovaj sadržaj, Facebook će znati tvoju aktivnost."},"shared.json":{"learnMore":"Saznajte više","readAbout":"Pročitaj više o ovoj zaštiti privatnosti","shareFeedback":"Podijeli povratne informacije"},"youtube.json":{"informationalModalMessageTitle":"Omogućiti sve YouTube pretpreglede?","informationalModalMessageBody":"Prikazivanje pretpregleda omogućit će Googleu (u čijem je vlasništvu YouTube) da vidi neke podatke o tvom uređaju, ali je i dalje privatnija opcija od reprodukcije videozapisa.","informationalModalConfirmButtonText":"Omogući sve pretpreglede","informationalModalRejectButtonText":"Ne, hvala","buttonTextUnblockVideo":"Deblokiraj YouTube videozapis","infoTitleUnblockVideo":"DuckDuckGo je blokirao ovaj YouTube videozapis kako bi spriječio Google da te prati","infoTextUnblockVideo":"Blokirali smo Google (u čijem je vlasništvu YouTube) da te prati kad se stranica učita. Ako deblokiraš ovaj videozapis, Google će znati tvoju aktivnost.","infoPreviewToggleText":"Pretpregledi su onemogućeni radi dodatne privatnosti","infoPreviewToggleEnabledText":"Pretpregledi su omogućeni","infoPreviewToggleEnabledDuckDuckGoText":"YouTube pretpregledi omogućeni su u DuckDuckGou.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Saznaj više</a> o uključenoj DuckDuckGo zaštiti od društvenih medija"}},"hu":{"facebook.json":{"informationalModalMessageTitle":"A Facebookkal való bejelentkezéskor a Facebook nyomon követhet","informationalModalMessageBody":"Miután bejelentkezel, a DuckDuckGo nem fogja tudni blokkolni a Facebook-tartalmat, amely nyomon követ ezen az oldalon.","informationalModalConfirmButtonText":"Bejelentkezés","informationalModalRejectButtonText":"Visszalépés","loginButtonText":"Bejelentkezés Facebookkal","loginBodyText":"Ha a Facebookkal jelentkezel be, nyomon követik a webhelyen végzett tevékenységedet.","buttonTextUnblockContent":"Facebook-tartalom feloldása","buttonTextUnblockComment":"Facebook-hozzászólás feloldása","buttonTextUnblockComments":"Facebook-hozzászólások feloldása","buttonTextUnblockPost":"Facebook-bejegyzés feloldása","buttonTextUnblockVideo":"Facebook-videó feloldása","buttonTextUnblockLogin":"Facebook-bejelentkezés feloldása","infoTitleUnblockContent":"A DuckDuckGo blokkolta ezt a tartalmat, hogy megakadályozza a Facebookot a nyomon követésedben","infoTitleUnblockComment":"A DuckDuckGo blokkolta ezt a hozzászólást, hogy megakadályozza a Facebookot a nyomon követésedben","infoTitleUnblockComments":"A DuckDuckGo blokkolta ezeket a hozzászólásokat, hogy megakadályozza a Facebookot a nyomon követésedben","infoTitleUnblockPost":"A DuckDuckGo blokkolta ezt a bejegyzést, hogy megakadályozza a Facebookot a nyomon követésedben","infoTitleUnblockVideo":"A DuckDuckGo blokkolta ezt a videót, hogy megakadályozza a Facebookot a nyomon követésedben","infoTextUnblockContent":"Az oldal betöltésekor blokkoltuk a Facebookot a nyomon követésedben. Ha feloldod ezt a tartalmat, a Facebook tudni fogja, hogy milyen tevékenységet végzel."},"shared.json":{"learnMore":"További részletek","readAbout":"Tudj meg többet erről az adatvédelemről","shareFeedback":"Visszajelzés megosztása"},"youtube.json":{"informationalModalMessageTitle":"Engedélyezed minden YouTube-videó előnézetét?","informationalModalMessageBody":"Az előnézetek megjelenítésével a Google (a YouTube tulajdonosa) láthatja a készülék néhány adatát, de ez adatvédelmi szempontból még mindig előnyösebb, mint a videó lejátszása.","informationalModalConfirmButtonText":"Minden előnézet engedélyezése","informationalModalRejectButtonText":"Nem, köszönöm","buttonTextUnblockVideo":"YouTube-videó feloldása","infoTitleUnblockVideo":"A DuckDuckGo blokkolta a YouTube-videót, hogy a Google ne követhessen nyomon","infoTextUnblockVideo":"Blokkoltuk, hogy a Google (a YouTube tulajdonosa) nyomon követhessen az oldal betöltésekor. Ha feloldod a videó blokkolását, a Google tudni fogja, hogy milyen tevékenységet végzel.","infoPreviewToggleText":"Az előnézetek a fokozott adatvédelem érdekében letiltva","infoPreviewToggleEnabledText":"Az előnézetek engedélyezve","infoPreviewToggleEnabledDuckDuckGoText":"YouTube-előnézetek engedélyezve a DuckDuckGo-ban.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">További tudnivalók</a> a DuckDuckGo beágyazott közösségi média elleni védelméről"}},"it":{"facebook.json":{"informationalModalMessageTitle":"L'accesso con Facebook consente di tracciarti","informationalModalMessageBody":"Dopo aver effettuato l'accesso, DuckDuckGo non può bloccare il tracciamento dei contenuti di Facebook su questo sito.","informationalModalConfirmButtonText":"Accedi","informationalModalRejectButtonText":"Torna indietro","loginButtonText":"Accedi con Facebook","loginBodyText":"Facebook tiene traccia della tua attività su un sito quando lo usi per accedere.","buttonTextUnblockContent":"Sblocca i contenuti di Facebook","buttonTextUnblockComment":"Sblocca il commento di Facebook","buttonTextUnblockComments":"Sblocca i commenti di Facebook","buttonTextUnblockPost":"Sblocca post di Facebook","buttonTextUnblockVideo":"Sblocca video di Facebook","buttonTextUnblockLogin":"Sblocca l'accesso a Facebook","infoTitleUnblockContent":"DuckDuckGo ha bloccato questo contenuto per impedire a Facebook di tracciarti","infoTitleUnblockComment":"DuckDuckGo ha bloccato questo commento per impedire a Facebook di tracciarti","infoTitleUnblockComments":"DuckDuckGo ha bloccato questi commenti per impedire a Facebook di tracciarti","infoTitleUnblockPost":"DuckDuckGo ha bloccato questo post per impedire a Facebook di tracciarti","infoTitleUnblockVideo":"DuckDuckGo ha bloccato questo video per impedire a Facebook di tracciarti","infoTextUnblockContent":"Abbiamo impedito a Facebook di tracciarti al caricamento della pagina. Se sblocchi questo contenuto, Facebook conoscerà la tua attività."},"shared.json":{"learnMore":"Ulteriori informazioni","readAbout":"Leggi di più su questa protezione della privacy","shareFeedback":"Condividi feedback"},"youtube.json":{"informationalModalMessageTitle":"Abilitare tutte le anteprime di YouTube?","informationalModalMessageBody":"La visualizzazione delle anteprime consentirà a Google (che possiede YouTube) di vedere alcune delle informazioni del tuo dispositivo, ma è comunque più privato rispetto alla riproduzione del video.","informationalModalConfirmButtonText":"Abilita tutte le anteprime","informationalModalRejectButtonText":"No, grazie","buttonTextUnblockVideo":"Sblocca video YouTube","infoTitleUnblockVideo":"DuckDuckGo ha bloccato questo video di YouTube per impedire a Google di tracciarti","infoTextUnblockVideo":"Abbiamo impedito a Google (che possiede YouTube) di tracciarti quando la pagina è stata caricata. Se sblocchi questo video, Google conoscerà la tua attività.","infoPreviewToggleText":"Anteprime disabilitate per una maggiore privacy","infoPreviewToggleEnabledText":"Anteprime abilitate","infoPreviewToggleEnabledDuckDuckGoText":"Anteprime YouTube abilitate in DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Scopri di più</a> sulla protezione dai social media integrata di DuckDuckGo"}},"lt":{"facebook.json":{"informationalModalMessageTitle":"Prisijungę prie „Facebook“ galite būti sekami","informationalModalMessageBody":"Kai esate prisijungę, „DuckDuckGo“ negali užblokuoti „Facebook“ turinio, todėl esate sekami šioje svetainėje.","informationalModalConfirmButtonText":"Prisijungti","informationalModalRejectButtonText":"Grįžti atgal","loginButtonText":"Prisijunkite su „Facebook“","loginBodyText":"„Facebook“ seka jūsų veiklą svetainėje, kai prisijungiate su šia svetaine.","buttonTextUnblockContent":"Atblokuoti „Facebook“ turinį","buttonTextUnblockComment":"Atblokuoti „Facebook“ komentarą","buttonTextUnblockComments":"Atblokuoti „Facebook“ komentarus","buttonTextUnblockPost":"Atblokuoti „Facebook“ įrašą","buttonTextUnblockVideo":"Atblokuoti „Facebook“ vaizdo įrašą","buttonTextUnblockLogin":"Atblokuoti „Facebook“ prisijungimą","infoTitleUnblockContent":"„DuckDuckGo“ užblokavo šį turinį, kad „Facebook“ negalėtų jūsų sekti","infoTitleUnblockComment":"„DuckDuckGo“ užblokavo šį komentarą, kad „Facebook“ negalėtų jūsų sekti","infoTitleUnblockComments":"„DuckDuckGo“ užblokavo šiuos komentarus, kad „Facebook“ negalėtų jūsų sekti","infoTitleUnblockPost":"„DuckDuckGo“ užblokavo šį įrašą, kad „Facebook“ negalėtų jūsų sekti","infoTitleUnblockVideo":"„DuckDuckGo“ užblokavo šį vaizdo įrašą, kad „Facebook“ negalėtų jūsų sekti","infoTextUnblockContent":"Užblokavome „Facebook“, kad negalėtų jūsų sekti, kai puslapis buvo įkeltas. Jei atblokuosite šį turinį, „Facebook“ žinos apie jūsų veiklą."},"shared.json":{"learnMore":"Sužinoti daugiau","readAbout":"Skaitykite apie šią privatumo apsaugą","shareFeedback":"Bendrinti atsiliepimą"},"youtube.json":{"informationalModalMessageTitle":"Įjungti visas „YouTube“ peržiūras?","informationalModalMessageBody":"Peržiūrų rodymas leis „Google“ (kuriai priklauso „YouTube“) matyti tam tikrą jūsų įrenginio informaciją, tačiau ji vis tiek bus privatesnė nei leidžiant vaizdo įrašą.","informationalModalConfirmButtonText":"Įjungti visas peržiūras","informationalModalRejectButtonText":"Ne, dėkoju","buttonTextUnblockVideo":"Atblokuoti „YouTube“ vaizdo įrašą","infoTitleUnblockVideo":"„DuckDuckGo“ užblokavo šį „YouTube“ vaizdo įrašą, kad „Google“ negalėtų jūsų sekti","infoTextUnblockVideo":"Užblokavome „Google“ (kuriai priklauso „YouTube“) galimybę sekti jus, kai puslapis buvo įkeltas. Jei atblokuosite šį vaizdo įrašą, „Google“ sužinos apie jūsų veiklą.","infoPreviewToggleText":"Peržiūros išjungtos dėl papildomo privatumo","infoPreviewToggleEnabledText":"Peržiūros įjungtos","infoPreviewToggleEnabledDuckDuckGoText":"„YouTube“ peržiūros įjungtos „DuckDuckGo“.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Sužinokite daugiau</a> apie „DuckDuckGo“ įdėtąją socialinės žiniasklaidos apsaugą"}},"lv":{"facebook.json":{"informationalModalMessageTitle":"Ja pieteiksies ar Facebook, viņi varēs tevi izsekot","informationalModalMessageBody":"Kad tu piesakies, DuckDuckGo nevar novērst, ka Facebook saturs tevi izseko šajā vietnē.","informationalModalConfirmButtonText":"Pieteikties","informationalModalRejectButtonText":"Atgriezties","loginButtonText":"Pieteikties ar Facebook","loginBodyText":"Facebook izseko tavas aktivitātes vietnē, kad esi pieteicies ar Facebook.","buttonTextUnblockContent":"Atbloķēt Facebook saturu","buttonTextUnblockComment":"Atbloķēt Facebook komentāru","buttonTextUnblockComments":"Atbloķēt Facebook komentārus","buttonTextUnblockPost":"Atbloķēt Facebook ziņu","buttonTextUnblockVideo":"Atbloķēt Facebook video","buttonTextUnblockLogin":"Atbloķēt Facebook pieteikšanos","infoTitleUnblockContent":"DuckDuckGo bloķēja šo saturu, lai neļautu Facebook tevi izsekot","infoTitleUnblockComment":"DuckDuckGo bloķēja šo komentāru, lai neļautu Facebook tevi izsekot","infoTitleUnblockComments":"DuckDuckGo bloķēja šos komentārus, lai neļautu Facebook tevi izsekot","infoTitleUnblockPost":"DuckDuckGo bloķēja šo ziņu, lai neļautu Facebook tevi izsekot","infoTitleUnblockVideo":"DuckDuckGo bloķēja šo videoklipu, lai neļautu Facebook tevi izsekot","infoTextUnblockContent":"Mēs bloķējām Facebook iespēju tevi izsekot, ielādējot lapu. Ja atbloķēsi šo saturu, Facebook redzēs, ko tu dari."},"shared.json":{"learnMore":"Uzzināt vairāk","readAbout":"Lasi par šo privātuma aizsardzību","shareFeedback":"Kopīgot atsauksmi"},"youtube.json":{"informationalModalMessageTitle":"Vai iespējot visus YouTube priekšskatījumus?","informationalModalMessageBody":"Priekšskatījumu rādīšana ļaus Google (kam pieder YouTube) redzēt daļu tavas ierīces informācijas, taču tas tāpat ir privātāk par videoklipa atskaņošanu.","informationalModalConfirmButtonText":"Iespējot visus priekšskatījumus","informationalModalRejectButtonText":"Nē, paldies","buttonTextUnblockVideo":"Atbloķēt YouTube videoklipu","infoTitleUnblockVideo":"DuckDuckGo bloķēja šo YouTube videoklipu, lai neļautu Google tevi izsekot","infoTextUnblockVideo":"Mēs neļāvām Google (kam pieder YouTube) tevi izsekot, kad lapa tika ielādēta. Ja atbloķēsi šo videoklipu, Google zinās, ko tu dari.","infoPreviewToggleText":"Priekšskatījumi ir atspējoti, lai nodrošinātu papildu konfidencialitāti","infoPreviewToggleEnabledText":"Priekšskatījumi ir iespējoti","infoPreviewToggleEnabledDuckDuckGoText":"DuckDuckGo iespējoti YouTube priekšskatījumi.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Uzzini vairāk</a> par DuckDuckGo iegulto sociālo mediju aizsardzību"}},"nb":{"facebook.json":{"informationalModalMessageTitle":"Når du logger på med Facebook, kan de spore deg","informationalModalMessageBody":"Når du er logget på, kan ikke DuckDuckGo hindre Facebook-innhold i å spore deg på dette nettstedet.","informationalModalConfirmButtonText":"Logg inn","informationalModalRejectButtonText":"Gå tilbake","loginButtonText":"Logg på med Facebook","loginBodyText":"Når du logger på med Facebook, sporer de aktiviteten din på nettstedet.","buttonTextUnblockContent":"Fjern blokkering av Facebook-innhold","buttonTextUnblockComment":"Fjern blokkering av Facebook-kommentar","buttonTextUnblockComments":"Fjern blokkering av Facebook-kommentarer","buttonTextUnblockPost":"Fjern blokkering av Facebook-innlegg","buttonTextUnblockVideo":"Fjern blokkering av Facebook-video","buttonTextUnblockLogin":"Fjern blokkering av Facebook-pålogging","infoTitleUnblockContent":"DuckDuckGo blokkerte dette innholdet for å hindre Facebook i å spore deg","infoTitleUnblockComment":"DuckDuckGo blokkerte denne kommentaren for å hindre Facebook i å spore deg","infoTitleUnblockComments":"DuckDuckGo blokkerte disse kommentarene for å hindre Facebook i å spore deg","infoTitleUnblockPost":"DuckDuckGo blokkerte dette innlegget for å hindre Facebook i å spore deg","infoTitleUnblockVideo":"DuckDuckGo blokkerte denne videoen for å hindre Facebook i å spore deg","infoTextUnblockContent":"Vi hindret Facebook i å spore deg da siden ble lastet. Hvis du opphever blokkeringen av dette innholdet, får Facebook vite om aktiviteten din."},"shared.json":{"learnMore":"Finn ut mer","readAbout":"Les om denne personvernfunksjonen","shareFeedback":"Del tilbakemelding"},"youtube.json":{"informationalModalMessageTitle":"Vil du aktivere alle YouTube-forhåndsvisninger?","informationalModalMessageBody":"Forhåndsvisninger gjør det mulig for Google (som eier YouTube) å se enkelte opplysninger om enheten din, men det er likevel mer privat enn å spille av videoen.","informationalModalConfirmButtonText":"Aktiver alle forhåndsvisninger","informationalModalRejectButtonText":"Nei takk","buttonTextUnblockVideo":"Fjern blokkering av YouTube-video","infoTitleUnblockVideo":"DuckDuckGo blokkerte denne YouTube-videoen for å hindre Google i å spore deg","infoTextUnblockVideo":"Vi blokkerte Google (som eier YouTube) mot å spore deg da siden ble lastet. Hvis du opphever blokkeringen av denne videoen, får Google vite om aktiviteten din.","infoPreviewToggleText":"Forhåndsvisninger er deaktivert for å gi deg ekstra personvern","infoPreviewToggleEnabledText":"Forhåndsvisninger er aktivert","infoPreviewToggleEnabledDuckDuckGoText":"YouTube-forhåndsvisninger er aktivert i DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Finn ut mer</a> om DuckDuckGos innebygde beskyttelse for sosiale medier"}},"nl":{"facebook.json":{"informationalModalMessageTitle":"Als je inlogt met Facebook, kunnen zij je volgen","informationalModalMessageBody":"Als je eenmaal bent ingelogd, kan DuckDuckGo niet voorkomen dat Facebook je op deze site volgt.","informationalModalConfirmButtonText":"Inloggen","informationalModalRejectButtonText":"Terug","loginButtonText":"Inloggen met Facebook","loginBodyText":"Facebook volgt je activiteit op een site als je Facebook gebruikt om in te loggen.","buttonTextUnblockContent":"Facebook-inhoud deblokkeren","buttonTextUnblockComment":"Facebook-opmerkingen deblokkeren","buttonTextUnblockComments":"Facebook-opmerkingen deblokkeren","buttonTextUnblockPost":"Facebook-bericht deblokkeren","buttonTextUnblockVideo":"Facebook-video deblokkeren","buttonTextUnblockLogin":"Facebook-aanmelding deblokkeren","infoTitleUnblockContent":"DuckDuckGo heeft deze inhoud geblokkeerd om te voorkomen dat Facebook je kan volgen","infoTitleUnblockComment":"DuckDuckGo heeft deze opmerking geblokkeerd om te voorkomen dat Facebook je kan volgen","infoTitleUnblockComments":"DuckDuckGo heeft deze opmerkingen geblokkeerd om te voorkomen dat Facebook je kan volgen","infoTitleUnblockPost":"DuckDuckGo heeft dit bericht geblokkeerd om te voorkomen dat Facebook je kan volgen","infoTitleUnblockVideo":"DuckDuckGo heeft deze video geblokkeerd om te voorkomen dat Facebook je kan volgen","infoTextUnblockContent":"We hebben voorkomen dat Facebook je volgde toen de pagina werd geladen. Als je deze inhoud deblokkeert, kan Facebook je activiteit zien."},"shared.json":{"learnMore":"Meer informatie","readAbout":"Lees meer over deze privacybescherming","shareFeedback":"Feedback delen"},"youtube.json":{"informationalModalMessageTitle":"Alle YouTube-voorbeelden inschakelen?","informationalModalMessageBody":"Bij het tonen van voorbeelden kan Google (eigenaar van YouTube) een deel van de informatie over je apparaat zien, maar blijft je privacy beter beschermd dan als je de video zou afspelen.","informationalModalConfirmButtonText":"Alle voorbeelden inschakelen","informationalModalRejectButtonText":"Nee, bedankt","buttonTextUnblockVideo":"YouTube-video deblokkeren","infoTitleUnblockVideo":"DuckDuckGo heeft deze YouTube-video geblokkeerd om te voorkomen dat Google je kan volgen","infoTextUnblockVideo":"We hebben voorkomen dat Google (eigenaar van YouTube) je volgde toen de pagina werd geladen. Als je deze video deblokkeert, kan Google je activiteit zien.","infoPreviewToggleText":"Voorbeelden uitgeschakeld voor extra privacy","infoPreviewToggleEnabledText":"Voorbeelden ingeschakeld","infoPreviewToggleEnabledDuckDuckGoText":"YouTube-voorbeelden ingeschakeld in DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Meer informatie</a> over DuckDuckGo's bescherming tegen ingesloten social media"}},"pl":{"facebook.json":{"informationalModalMessageTitle":"Jeśli zalogujesz się za pośrednictwem Facebooka, będzie on mógł śledzić Twoją aktywność","informationalModalMessageBody":"Po zalogowaniu się DuckDuckGo nie może zablokować możliwości śledzenia Cię przez Facebooka na tej stronie.","informationalModalConfirmButtonText":"Zaloguj się","informationalModalRejectButtonText":"Wróć","loginButtonText":"Zaloguj się za pośrednictwem Facebooka","loginBodyText":"Facebook śledzi Twoją aktywność na stronie, gdy logujesz się za jego pośrednictwem.","buttonTextUnblockContent":"Odblokuj treść na Facebooku","buttonTextUnblockComment":"Odblokuj komentarz na Facebooku","buttonTextUnblockComments":"Odblokuj komentarze na Facebooku","buttonTextUnblockPost":"Odblokuj post na Facebooku","buttonTextUnblockVideo":"Odblokuj wideo na Facebooku","buttonTextUnblockLogin":"Odblokuj logowanie na Facebooku","infoTitleUnblockContent":"DuckDuckGo zablokował tę treść, aby Facebook nie mógł Cię śledzić","infoTitleUnblockComment":"DuckDuckGo zablokował ten komentarz, aby Facebook nie mógł Cię śledzić","infoTitleUnblockComments":"DuckDuckGo zablokował te komentarze, aby Facebook nie mógł Cię śledzić","infoTitleUnblockPost":"DuckDuckGo zablokował ten post, aby Facebook nie mógł Cię śledzić","infoTitleUnblockVideo":"DuckDuckGo zablokował tę treść wideo, aby Facebook nie mógł Cię śledzić.","infoTextUnblockContent":"Zablokowaliśmy Facebookowi możliwość śledzenia Cię podczas ładowania strony. Jeśli odblokujesz tę treść, Facebook uzyska informacje o Twojej aktywności."},"shared.json":{"learnMore":"Dowiedz się więcej","readAbout":"Dowiedz się więcej o tej ochronie prywatności","shareFeedback":"Podziel się opinią"},"youtube.json":{"informationalModalMessageTitle":"Włączyć wszystkie podglądy w YouTube?","informationalModalMessageBody":"Wyświetlanie podglądu pozwala Google (który jest właścicielem YouTube) zobaczyć niektóre informacje o Twoim urządzeniu, ale nadal jest to bardziej prywatne niż odtwarzanie filmu.","informationalModalConfirmButtonText":"Włącz wszystkie podglądy","informationalModalRejectButtonText":"Nie, dziękuję","buttonTextUnblockVideo":"Odblokuj wideo w YouTube","infoTitleUnblockVideo":"DuckDuckGo zablokował ten film w YouTube, aby uniemożliwić Google śledzenie Twojej aktywności","infoTextUnblockVideo":"Zablokowaliśmy możliwość śledzenia Cię przez Google (właściciela YouTube) podczas ładowania strony. Jeśli odblokujesz ten film, Google zobaczy Twoją aktywność.","infoPreviewToggleText":"Podglądy zostały wyłączone, aby zapewnić większą ptywatność","infoPreviewToggleEnabledText":"Podglądy włączone","infoPreviewToggleEnabledDuckDuckGoText":"Podglądy YouTube włączone w DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Dowiedz się więcej</a> o zabezpieczeniu osadzonych treści społecznościowych DuckDuckGo"}},"pt":{"facebook.json":{"informationalModalMessageTitle":"Iniciar sessão no Facebook permite que este te rastreie","informationalModalMessageBody":"Depois de iniciares sessão, o DuckDuckGo não poderá bloquear o rastreio por parte do conteúdo do Facebook neste site.","informationalModalConfirmButtonText":"Iniciar sessão","informationalModalRejectButtonText":"Retroceder","loginButtonText":"Iniciar sessão com o Facebook","loginBodyText":"O Facebook rastreia a tua atividade num site quando o usas para iniciares sessão.","buttonTextUnblockContent":"Desbloquear Conteúdo do Facebook","buttonTextUnblockComment":"Desbloquear Comentário do Facebook","buttonTextUnblockComments":"Desbloquear Comentários do Facebook","buttonTextUnblockPost":"Desbloquear Publicação no Facebook","buttonTextUnblockVideo":"Desbloquear Vídeo do Facebook","buttonTextUnblockLogin":"Desbloquear Início de Sessão no Facebook","infoTitleUnblockContent":"O DuckDuckGo bloqueou este conteúdo para evitar que o Facebook te rastreie","infoTitleUnblockComment":"O DuckDuckGo bloqueou este comentário para evitar que o Facebook te rastreie","infoTitleUnblockComments":"O DuckDuckGo bloqueou estes comentários para evitar que o Facebook te rastreie","infoTitleUnblockPost":"O DuckDuckGo bloqueou esta publicação para evitar que o Facebook te rastreie","infoTitleUnblockVideo":"O DuckDuckGo bloqueou este vídeo para evitar que o Facebook te rastreie","infoTextUnblockContent":"Bloqueámos o rastreio por parte do Facebook quando a página foi carregada. Se desbloqueares este conteúdo, o Facebook fica a saber a tua atividade."},"shared.json":{"learnMore":"Saiba mais","readAbout":"Ler mais sobre esta proteção de privacidade","shareFeedback":"Partilhar comentários"},"youtube.json":{"informationalModalMessageTitle":"Ativar todas as pré-visualizações do YouTube?","informationalModalMessageBody":"Mostrar visualizações permite à Google (que detém o YouTube) ver algumas das informações do teu dispositivo, mas ainda é mais privado do que reproduzir o vídeo.","informationalModalConfirmButtonText":"Ativar todas as pré-visualizações","informationalModalRejectButtonText":"Não, obrigado","buttonTextUnblockVideo":"Desbloquear Vídeo do YouTube","infoTitleUnblockVideo":"O DuckDuckGo bloqueou este vídeo do YouTube para impedir que a Google te rastreie","infoTextUnblockVideo":"Bloqueámos o rastreio por parte da Google (que detém o YouTube) quando a página foi carregada. Se desbloqueares este vídeo, a Google fica a saber a tua atividade.","infoPreviewToggleText":"Pré-visualizações desativadas para privacidade adicional","infoPreviewToggleEnabledText":"Pré-visualizações ativadas","infoPreviewToggleEnabledDuckDuckGoText":"Pré-visualizações do YouTube ativadas no DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Saiba mais</a> sobre a Proteção contra conteúdos de redes sociais incorporados do DuckDuckGo"}},"ro":{"facebook.json":{"informationalModalMessageTitle":"Conectarea cu Facebook îi permite să te urmărească","informationalModalMessageBody":"Odată ce te-ai conectat, DuckDuckGo nu poate împiedica conținutul Facebook să te urmărească pe acest site.","informationalModalConfirmButtonText":"Autentificare","informationalModalRejectButtonText":"Înapoi","loginButtonText":"Conectează-te cu Facebook","loginBodyText":"Facebook urmărește activitatea ta pe un site atunci când îl utilizezi pentru a te conecta.","buttonTextUnblockContent":"Deblochează conținutul Facebook","buttonTextUnblockComment":"Deblochează comentariul de pe Facebook","buttonTextUnblockComments":"Deblochează comentariile de pe Facebook","buttonTextUnblockPost":"Deblochează postarea de pe Facebook","buttonTextUnblockVideo":"Deblochează videoclipul de pe Facebook","buttonTextUnblockLogin":"Deblochează conectarea cu Facebook","infoTitleUnblockContent":"DuckDuckGo a blocat acest conținut pentru a împiedica Facebook să te urmărească","infoTitleUnblockComment":"DuckDuckGo a blocat acest comentariu pentru a împiedica Facebook să te urmărească","infoTitleUnblockComments":"DuckDuckGo a blocat aceste comentarii pentru a împiedica Facebook să te urmărească","infoTitleUnblockPost":"DuckDuckGo a blocat această postare pentru a împiedica Facebook să te urmărească","infoTitleUnblockVideo":"DuckDuckGo a blocat acest videoclip pentru a împiedica Facebook să te urmărească","infoTextUnblockContent":"Am împiedicat Facebook să te urmărească atunci când pagina a fost încărcată. Dacă deblochezi acest conținut, Facebook îți va cunoaște activitatea."},"shared.json":{"learnMore":"Află mai multe","readAbout":"Citește despre această protecție a confidențialității","shareFeedback":"Partajează feedback"},"youtube.json":{"informationalModalMessageTitle":"Activezi toate previzualizările YouTube?","informationalModalMessageBody":"Afișarea previzualizărilor va permite ca Google (care deține YouTube) să vadă unele dintre informațiile despre dispozitivul tău, dar este totuși mai privată decât redarea videoclipului.","informationalModalConfirmButtonText":"Activează toate previzualizările","informationalModalRejectButtonText":"Nu, mulțumesc","buttonTextUnblockVideo":"Deblochează videoclipul de pe YouTube","infoTitleUnblockVideo":"DuckDuckGo a blocat acest videoclip de pe YouTube pentru a împiedica Google să te urmărească","infoTextUnblockVideo":"Am împiedicat Google (care deține YouTube) să te urmărească atunci când s-a încărcat pagina. Dacă deblochezi acest videoclip, Google va cunoaște activitatea ta.","infoPreviewToggleText":"Previzualizările au fost dezactivate pentru o confidențialitate suplimentară","infoPreviewToggleEnabledText":"Previzualizări activate","infoPreviewToggleEnabledDuckDuckGoText":"Previzualizările YouTube sunt activate în DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Află mai multe</a> despre Protecția integrată DuckDuckGo pentru rețelele sociale"}},"ru":{"facebook.json":{"informationalModalMessageTitle":"Вход через Facebook позволяет этой социальной сети отслеживать вас","informationalModalMessageBody":"После входа DuckDuckGo не сможет блокировать отслеживание ваших действий с контентом на Facebook.","informationalModalConfirmButtonText":"Войти","informationalModalRejectButtonText":"Вернуться","loginButtonText":"Войти через Facebook","loginBodyText":"При использовании учётной записи Facebook для входа на сайты эта социальная сеть сможет отслеживать на них ваши действия.","buttonTextUnblockContent":"Разблокировать контент из Facebook","buttonTextUnblockComment":"Разблокировать комментарий из Facebook","buttonTextUnblockComments":"Разблокировать комментарии из Facebook","buttonTextUnblockPost":"Разблокировать публикацию из Facebook","buttonTextUnblockVideo":"Разблокировать видео из Facebook","buttonTextUnblockLogin":"Разблокировать окно входа в Facebook","infoTitleUnblockContent":"DuckDuckGo заблокировал этот контент, чтобы вас не отслеживал Facebook","infoTitleUnblockComment":"DuckDuckGo заблокировал этот комментарий, чтобы вас не отслеживал Facebook","infoTitleUnblockComments":"DuckDuckGo заблокировал эти комментарии, чтобы вас не отслеживал Facebook","infoTitleUnblockPost":"DuckDuckGo заблокировал эту публикацию, чтобы вас не отслеживал Facebook","infoTitleUnblockVideo":"DuckDuckGo заблокировал это видео, чтобы вас не отслеживал Facebook","infoTextUnblockContent":"Во время загрузки страницы мы помешали Facebook отследить ваши действия. Если разблокировать этот контент, Facebook сможет фиксировать вашу активность."},"shared.json":{"learnMore":"Узнать больше","readAbout":"Подробнее об этом виде защиты конфиденциальности","shareFeedback":"Оставьте нам отзыв"},"youtube.json":{"informationalModalMessageTitle":"Включить предпросмотр видео из YouTube?","informationalModalMessageBody":"Включение предварительного просмотра позволит Google (владельцу YouTube) получить некоторые сведения о вашем устройстве, однако это более безопасный вариант, чем воспроизведение видео целиком.","informationalModalConfirmButtonText":"Включить предпросмотр","informationalModalRejectButtonText":"Нет, спасибо","buttonTextUnblockVideo":"Разблокировать видео из YouTube","infoTitleUnblockVideo":"DuckDuckGo заблокировал это видео из YouTube, чтобы вас не отслеживал Google","infoTextUnblockVideo":"Во время загрузки страницы мы помешали Google (владельцу YouTube) отследить ваши действия. Если разблокировать видео, Google сможет фиксировать вашу активность.","infoPreviewToggleText":"Предварительный просмотр отключён для дополнительной защиты конфиденциальности","infoPreviewToggleEnabledText":"Предварительный просмотр включён","infoPreviewToggleEnabledDuckDuckGoText":"В DuckDuckGo включён предпросмотр видео из YouTube.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Подробнее</a> о защите DuckDuckGo от внедрённого контента соцсетей"}},"sk":{"facebook.json":{"informationalModalMessageTitle":"Prihlásenie cez Facebook mu umožní sledovať vás","informationalModalMessageBody":"DuckDuckGo po prihlásení nemôže na tejto lokalite zablokovať sledovanie vašej osoby obsahom Facebooku.","informationalModalConfirmButtonText":"Prihlásiť sa","informationalModalRejectButtonText":"Prejsť späť","loginButtonText":"Prihláste sa pomocou služby Facebook","loginBodyText":"Keď použijete prihlasovanie cez Facebook, Facebook bude na lokalite sledovať vašu aktivitu.","buttonTextUnblockContent":"Odblokovať obsah Facebooku","buttonTextUnblockComment":"Odblokovať komentár na Facebooku","buttonTextUnblockComments":"Odblokovať komentáre na Facebooku","buttonTextUnblockPost":"Odblokovať príspevok na Facebooku","buttonTextUnblockVideo":"Odblokovanie videa na Facebooku","buttonTextUnblockLogin":"Odblokovať prihlásenie na Facebook","infoTitleUnblockContent":"DuckDuckGo zablokoval tento obsah, aby vás Facebook nesledoval","infoTitleUnblockComment":"DuckDuckGo zablokoval tento komentár, aby zabránil sledovaniu zo strany Facebooku","infoTitleUnblockComments":"DuckDuckGo zablokoval tieto komentáre, aby vás Facebook nesledoval","infoTitleUnblockPost":"DuckDuckGo zablokoval tento príspevok, aby vás Facebook nesledoval","infoTitleUnblockVideo":"DuckDuckGo zablokoval toto video, aby vás Facebook nesledoval","infoTextUnblockContent":"Pri načítaní stránky sme zablokovali Facebook, aby vás nesledoval. Ak tento obsah odblokujete, Facebook bude vedieť o vašej aktivite."},"shared.json":{"learnMore":"Zistite viac","readAbout":"Prečítajte si o tejto ochrane súkromia","shareFeedback":"Zdieľať spätnú väzbu"},"youtube.json":{"informationalModalMessageTitle":"Chcete povoliť všetky ukážky zo služby YouTube?","informationalModalMessageBody":"Zobrazenie ukážok umožní spoločnosti Google (ktorá vlastní YouTube) vidieť niektoré informácie o vašom zariadení, ale stále je to súkromnejšie ako prehrávanie videa.","informationalModalConfirmButtonText":"Povoliť všetky ukážky","informationalModalRejectButtonText":"Nie, ďakujem","buttonTextUnblockVideo":"Odblokovať YouTube video","infoTitleUnblockVideo":"DuckDuckGo toto video v službe YouTube zablokoval s cieľom predísť tomu, aby vás spoločnosť Google mohla sledovať","infoTextUnblockVideo":"Zablokovali sme pre spoločnosť Google (ktorá vlastní YouTube), aby vás nemohla sledovať, keď sa stránka načíta. Ak toto video odblokujete, Google bude poznať vašu aktivitu.","infoPreviewToggleText":"Ukážky sú zakázané s cieľom zvýšiť ochranu súkromia","infoPreviewToggleEnabledText":"Ukážky sú povolené","infoPreviewToggleEnabledDuckDuckGoText":"Ukážky YouTube sú v DuckDuckGo povolené.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Získajte viac informácií</a> o DuckDuckGo, vloženej ochrane sociálnych médií"}},"sl":{"facebook.json":{"informationalModalMessageTitle":"Če se prijavite s Facebookom, vam Facebook lahko sledi","informationalModalMessageBody":"Ko ste enkrat prijavljeni, DuckDuckGo ne more blokirati Facebookove vsebine, da bi vam sledila na tem spletnem mestu.","informationalModalConfirmButtonText":"Prijava","informationalModalRejectButtonText":"Pojdi nazaj","loginButtonText":"Prijavite se s Facebookom","loginBodyText":"Če se prijavite s Facebookom, bo nato spremljal vaša dejanja na spletnem mestu.","buttonTextUnblockContent":"Odblokiraj vsebino na Facebooku","buttonTextUnblockComment":"Odblokiraj komentar na Facebooku","buttonTextUnblockComments":"Odblokiraj komentarje na Facebooku","buttonTextUnblockPost":"Odblokiraj objavo na Facebooku","buttonTextUnblockVideo":"Odblokiraj videoposnetek na Facebooku","buttonTextUnblockLogin":"Odblokiraj prijavo na Facebooku","infoTitleUnblockContent":"DuckDuckGo je blokiral to vsebino, da bi Facebooku preprečil sledenje","infoTitleUnblockComment":"DuckDuckGo je blokiral ta komentar, da bi Facebooku preprečil sledenje","infoTitleUnblockComments":"DuckDuckGo je blokiral te komentarje, da bi Facebooku preprečil sledenje","infoTitleUnblockPost":"DuckDuckGo je blokiral to objavo, da bi Facebooku preprečil sledenje","infoTitleUnblockVideo":"DuckDuckGo je blokiral ta videoposnetek, da bi Facebooku preprečil sledenje","infoTextUnblockContent":"Ko se je stran naložila, smo Facebooku preprečili, da bi vam sledil. Če to vsebino odblokirate, bo Facebook izvedel za vaša dejanja."},"shared.json":{"learnMore":"Več","readAbout":"Preberite več o tej zaščiti zasebnosti","shareFeedback":"Deli povratne informacije"},"youtube.json":{"informationalModalMessageTitle":"Želite omogočiti vse YouTubove predoglede?","informationalModalMessageBody":"Prikaz predogledov omogoča Googlu (ki je lastnik YouTuba) vpogled v nekatere podatke o napravi, vendar je še vedno bolj zasebno kot predvajanje videoposnetka.","informationalModalConfirmButtonText":"Omogoči vse predoglede","informationalModalRejectButtonText":"Ne, hvala","buttonTextUnblockVideo":"Odblokiraj videoposnetek na YouTubu","infoTitleUnblockVideo":"DuckDuckGo je blokiral ta videoposnetek v YouTubu, da bi Googlu preprečil sledenje","infoTextUnblockVideo":"Googlu (ki je lastnik YouTuba) smo preprečili, da bi vam sledil, ko se je stran naložila. Če odblokirate ta videoposnetek, bo Google izvedel za vašo dejavnost.","infoPreviewToggleText":"Predogledi so zaradi dodatne zasebnosti onemogočeni","infoPreviewToggleEnabledText":"Predogledi so omogočeni","infoPreviewToggleEnabledDuckDuckGoText":"YouTubovi predogledi so omogočeni v DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Več</a> o vgrajeni zaščiti družbenih medijev DuckDuckGo"}},"sv":{"facebook.json":{"informationalModalMessageTitle":"Om du loggar in med Facebook kan de spåra dig","informationalModalMessageBody":"När du väl är inloggad kan DuckDuckGo inte hindra Facebooks innehåll från att spåra dig på den här webbplatsen.","informationalModalConfirmButtonText":"Logga in","informationalModalRejectButtonText":"Gå tillbaka","loginButtonText":"Logga in med Facebook","loginBodyText":"Facebook spårar din aktivitet på en webbplats om du använder det för att logga in.","buttonTextUnblockContent":"Avblockera Facebook-innehåll","buttonTextUnblockComment":"Avblockera Facebook-kommentar","buttonTextUnblockComments":"Avblockera Facebook-kommentarer","buttonTextUnblockPost":"Avblockera Facebook-inlägg","buttonTextUnblockVideo":"Avblockera Facebook-video","buttonTextUnblockLogin":"Avblockera Facebook-inloggning","infoTitleUnblockContent":"DuckDuckGo blockerade det här innehållet för att förhindra att Facebook spårar dig","infoTitleUnblockComment":"DuckDuckGo blockerade den här kommentaren för att förhindra att Facebook spårar dig","infoTitleUnblockComments":"DuckDuckGo blockerade de här kommentarerna för att förhindra att Facebook spårar dig","infoTitleUnblockPost":"DuckDuckGo blockerade det här inlägget för att förhindra att Facebook spårar dig","infoTitleUnblockVideo":"DuckDuckGo blockerade den här videon för att förhindra att Facebook spårar dig","infoTextUnblockContent":"Vi hindrade Facebook från att spåra dig när sidan lästes in. Om du avblockerar det här innehållet kommer Facebook att känna till din aktivitet."},"shared.json":{"learnMore":"Läs mer","readAbout":"Läs mer om detta integritetsskydd","shareFeedback":"Berätta vad du tycker"},"youtube.json":{"informationalModalMessageTitle":"Aktivera alla förhandsvisningar för YouTube?","informationalModalMessageBody":"Genom att visa förhandsvisningar kan Google (som äger YouTube) se en del av enhetens information, men det är ändå mer privat än att spela upp videon.","informationalModalConfirmButtonText":"Aktivera alla förhandsvisningar","informationalModalRejectButtonText":"Nej tack","buttonTextUnblockVideo":"Avblockera YouTube-video","infoTitleUnblockVideo":"DuckDuckGo blockerade den här YouTube-videon för att förhindra att Google spårar dig","infoTextUnblockVideo":"Vi hindrade Google (som äger YouTube) från att spåra dig när sidan laddades. Om du tar bort blockeringen av videon kommer Google att känna till din aktivitet.","infoPreviewToggleText":"Förhandsvisningar har inaktiverats för ytterligare integritet","infoPreviewToggleEnabledText":"Förhandsvisningar aktiverade","infoPreviewToggleEnabledDuckDuckGoText":"YouTube-förhandsvisningar aktiverade i DuckDuckGo.","infoPreviewInfoText":"<a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">Läs mer</a> om DuckDuckGos skydd mot inbäddade sociala medier"}},"tr":{"facebook.json":{"informationalModalMessageTitle":"Facebook ile giriş yapmak, sizi takip etmelerini sağlar","informationalModalMessageBody":"Giriş yaptıktan sonra, DuckDuckGo Facebook içeriğinin sizi bu sitede izlemesini engelleyemez.","informationalModalConfirmButtonText":"Oturum Aç","informationalModalRejectButtonText":"Geri dön","loginButtonText":"Facebook ile giriş yapın","loginBodyText":"Facebook, giriş yapmak için kullandığınızda bir sitedeki etkinliğinizi izler.","buttonTextUnblockContent":"Facebook İçeriğinin Engelini Kaldır","buttonTextUnblockComment":"Facebook Yorumunun Engelini Kaldır","buttonTextUnblockComments":"Facebook Yorumlarının Engelini Kaldır","buttonTextUnblockPost":"Facebook Gönderisinin Engelini Kaldır","buttonTextUnblockVideo":"Facebook Videosunun Engelini Kaldır","buttonTextUnblockLogin":"Facebook Girişinin Engelini Kaldır","infoTitleUnblockContent":"DuckDuckGo, Facebook'un sizi izlemesini önlemek için bu içeriği engelledi","infoTitleUnblockComment":"DuckDuckGo, Facebook'un sizi izlemesini önlemek için bu yorumu engelledi","infoTitleUnblockComments":"DuckDuckGo, Facebook'un sizi izlemesini önlemek için bu yorumları engelledi","infoTitleUnblockPost":"DuckDuckGo, Facebook'un sizi izlemesini önlemek için bu gönderiyi engelledi","infoTitleUnblockVideo":"DuckDuckGo, Facebook'un sizi izlemesini önlemek için bu videoyu engelledi","infoTextUnblockContent":"Sayfa yüklendiğinde Facebook'un sizi izlemesini engelledik. Bu içeriğin engelini kaldırırsanız Facebook etkinliğinizi öğrenecektir."},"shared.json":{"learnMore":"Daha Fazla Bilgi","readAbout":"Bu gizlilik koruması hakkında bilgi edinin","shareFeedback":"Geri Bildirim Paylaş"},"youtube.json":{"informationalModalMessageTitle":"Tüm YouTube önizlemeleri etkinleştirilsin mi?","informationalModalMessageBody":"Önizlemelerin gösterilmesi Google'ın (YouTube'un sahibi) cihazınızın bazı bilgilerini görmesine izin verir, ancak yine de videoyu oynatmaktan daha özeldir.","informationalModalConfirmButtonText":"Tüm Önizlemeleri Etkinleştir","informationalModalRejectButtonText":"Hayır Teşekkürler","buttonTextUnblockVideo":"YouTube Videosunun Engelini Kaldır","infoTitleUnblockVideo":"DuckDuckGo, Google'ın sizi izlemesini önlemek için bu YouTube videosunu engelledi","infoTextUnblockVideo":"Sayfa yüklendiğinde Google'ın (YouTube'un sahibi) sizi izlemesini engelledik. Bu videonun engelini kaldırırsanız, Google etkinliğinizi öğrenecektir.","infoPreviewToggleText":"Ek gizlilik için önizlemeler devre dışı bırakıldı","infoPreviewToggleEnabledText":"Önizlemeler etkinleştirildi","infoPreviewToggleEnabledDuckDuckGoText":"DuckDuckGo'da YouTube önizlemeleri etkinleştirildi.","infoPreviewInfoText":"DuckDuckGo Yerleşik Sosyal Medya Koruması hakkında <a href=\\\"https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/\\\">daha fazla bilgi edinin</a>"}}}`;

    /*********************************************************
     *  Style Definitions
     *********************************************************/
    /**
     * Get CSS style defintions for CTL, using the provided AssetConfig for any non-embedded assets
     * (e.g. fonts.)
     * @param {import('../../content-feature.js').AssetConfig} [assets]
     */
    function getStyles (assets) {
        let fontStyle = '';
        let regularFontFamily = "system, -apple-system, system-ui, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol'";
        let boldFontFamily = regularFontFamily;
        if (assets?.regularFontUrl && assets?.boldFontUrl) {
            fontStyle = `
        @font-face{
            font-family: DuckDuckGoPrivacyEssentials;
            src: url(${assets.regularFontUrl});
        }
        @font-face{
            font-family: DuckDuckGoPrivacyEssentialsBold;
            font-weight: bold;
            src: url(${assets.boldFontUrl});
        }
    `;
            regularFontFamily = 'DuckDuckGoPrivacyEssentials';
            boldFontFamily = 'DuckDuckGoPrivacyEssentialsBold';
        }
        return {
            fontStyle,
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
        `,
                toggleButtonBgState: {
                    active: `
                background: #5784FF;
            `,
                    inactive: `
                background-color: #666666;
            `
                }
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
        `,
                toggleButtonBgState: {
                    active: `
                background: #3969EF;
            `,
                    inactive: `
                background-color: #666666;
            `
                }
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

        font-family: ${boldFontFamily};
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
        font-family: ${regularFontFamily};
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

        font-family: ${regularFontFamily};
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
        font-family: ${regularFontFamily};
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
        font-family: ${regularFontFamily};
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
        font-family: ${boldFontFamily};
        font-size: 17px;
        font-weight: bold;
        line-height: 21px;
        margin: 10px auto;
        text-align: center;
        border: none;
        padding: 0px 32px;
    `,
            modalContentText: `
        font-family: ${regularFontFamily};
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
        font-family: ${boldFontFamily};
        font-size: 17px;
        font-weight: bold;
        margin: 20px auto 10px;
        padding: 0px 30px;
        text-align: center;
        margin-top: auto;
    `,
            contentText: `
        font-family: ${regularFontFamily};
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
        font-family: ${boldFontFamily};
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
        font-family: ${boldFontFamily};
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
        font-family: ${boldFontFamily};
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
        }
    }

    /**
     * @param {string} locale UI locale
     */
    function getConfig (locale) {
        const locales = JSON.parse(localesJSON);
        const fbStrings = locales[locale]['facebook.json'];
        const ytStrings = locales[locale]['youtube.json'];

        const sharedStrings = locales[locale]['shared.json'];
        const config = {
            'Facebook, Inc.': {
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
                            "iframe[src*='//www.facebook.com/plugins/like.php']",
                            "iframe[src*='//www.facebook.com/v2.0/plugins/like.php']",
                            "iframe[src*='//www.facebook.com/plugins/share_button.php']",
                            "iframe[src*='//www.facebook.com/v2.0/plugins/share_button.php']"
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
                            "iframe[src*='//www.facebook.com/plugins/page.php']",
                            "iframe[src*='//www.facebook.com/v2.0/plugins/page.php']"
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
                            "iframe[src*='//www.facebook.com/plugins/comment_embed.php']",
                            "iframe[src*='//www.facebook.com/v2.0/plugins/comment_embed.php']"
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
                            "iframe[src*='//www.facebook.com/plugins/post.php']",
                            "iframe[src*='//www.facebook.com/v2.0/plugins/post.php']"
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
                            "iframe[src*='//www.facebook.com/plugins/video.php']",
                            "iframe[src*='//www.facebook.com/v2.0/plugins/video.php']"
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
                            "iframe[src*='//www.facebook.com/plugins/group.php']",
                            "iframe[src*='//www.facebook.com/v2.0/plugins/group.php']"
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
                            buttonTextUnblockLogin: fbStrings.buttonTextUnblockLogin,
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
                            "iframe[src*='//youtube.com/embed']",
                            "iframe[src*='//youtube-nocookie.com/embed']",
                            "iframe[src*='//www.youtube.com/embed']",
                            "iframe[src*='//www.youtube-nocookie.com/embed']",
                            "iframe[data-src*='//youtube.com/embed']",
                            "iframe[data-src*='//youtube-nocookie.com/embed']",
                            "iframe[data-src*='//www.youtube.com/embed']",
                            "iframe[data-src*='//www.youtube-nocookie.com/embed']"
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
                                previewToggleEnabledDuckDuckGoText: ytStrings.infoPreviewToggleEnabledText,
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
                            "iframe[src*='//youtube.com/subscribe_embed']",
                            "iframe[src*='//youtube-nocookie.com/subscribe_embed']",
                            "iframe[src*='//www.youtube.com/subscribe_embed']",
                            "iframe[src*='//www.youtube-nocookie.com/subscribe_embed']",
                            "iframe[data-src*='//youtube.com/subscribe_embed']",
                            "iframe[data-src*='//youtube-nocookie.com/subscribe_embed']",
                            "iframe[data-src*='//www.youtube.com/subscribe_embed']",
                            "iframe[data-src*='//www.youtube-nocookie.com/subscribe_embed']"
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

    /**
     * The following code is originally from https://github.com/mozilla-extensions/secure-proxy/blob/db4d1b0e2bfe0abae416bf04241916f9e4768fd2/src/commons/template.js
     */
    class Template {
        constructor (strings, values) {
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
        escapeXML (str) {
            const replacements = {
                '&': '&amp;',
                '"': '&quot;',
                "'": '&apos;',
                '<': '&lt;',
                '>': '&gt;',
                '/': '&#x2F;'
            };
            return String(str).replace(/[&"'<>/]/g, m => replacements[m])
        }

        potentiallyEscape (value) {
            if (typeof value === 'object') {
                if (value instanceof Array) {
                    return value.map(val => this.potentiallyEscape(val)).join('')
                }

                // If we are an escaped template let join call toString on it
                if (value instanceof Template) {
                    return value
                }

                throw new Error('Unknown object to escape')
            }
            return this.escapeXML(value)
        }

        toString () {
            const result = [];

            for (const [i, string] of this.strings.entries()) {
                result.push(string);
                if (i < this.values.length) {
                    result.push(this.potentiallyEscape(this.values[i]));
                }
            }
            return result.join('')
        }
    }

    function html (strings, ...values) {
        return new Template(strings, values)
    }

    var cssVars = ":host {\n    /* Color palette */\n    --ddg-shade-06: rgba(0, 0, 0, 0.06);\n    --ddg-shade-12: rgba(0, 0, 0, 0.12);\n    --ddg-shade-18: rgba(0, 0, 0, 0.18);\n    --ddg-shade-36: rgba(0, 0, 0, 0.36);\n    --ddg-shade-84: rgba(0, 0, 0, 0.84);\n    --ddg-tint-12: rgba(255, 255, 255, 0.12);\n    --ddg-tint-18: rgba(255, 255, 255, 0.18);\n    --ddg-tint-24: rgba(255, 255, 255, 0.24);\n    --ddg-tint-84: rgba(255, 255, 255, 0.84);\n    /* Tokens */\n    --ddg-color-primary: #3969ef;\n    --ddg-color-bg-01: #ffffff;\n    --ddg-color-bg-02: #ababab;\n    --ddg-color-border: var(--ddg-shade-12);\n    --ddg-color-txt: var(--ddg-shade-84);\n    --ddg-color-txt-link-02: #ababab;\n}\n@media (prefers-color-scheme: dark) {\n    :host {\n        --ddg-color-primary: #7295f6;\n        --ddg-color-bg-01: #222222;\n        --ddg-color-bg-02: #444444;\n        --ddg-color-border: var(--ddg-tint-12);\n        --ddg-color-txt: var(--ddg-tint-84);\n    }\n}\n\n/* SHARED STYLES */\n/* Text Link */\n.ddg-text-link {\n    line-height: 1.4;\n    font-size: 14px;\n    font-weight: 700;\n    cursor: pointer;\n    text-decoration: none;\n    color: var(--ddg-color-primary);\n}\n\n/* Button */\n.DuckDuckGoButton {\n    border-radius: 8px;\n    padding: 8px 16px;\n    border-color: var(--ddg-color-primary);\n    border: none;\n    min-height: 36px;\n\n    position: relative;\n    cursor: pointer;\n    box-shadow: none;\n    z-index: 2147483646;\n}\n.DuckDuckGoButton > div {\n    display: flex;\n    flex-direction: row;\n    align-items: center;\n    border: none;\n    padding: 0;\n    margin: 0;\n}\n.DuckDuckGoButton,\n.DuckDuckGoButton > div {\n    font-size: 14px;\n    font-family: DuckDuckGoPrivacyEssentialsBold;\n    font-weight: 600;\n}\n.DuckDuckGoButton.tertiary {\n    color: var(--ddg-color-txt);\n    background-color: transparent;\n    display: flex;\n    justify-content: center;\n    align-items: center;\n    border: 1px solid var(--ddg-color-border);\n    border-radius: 8px;\n}\n.DuckDuckGoButton.tertiary:hover {\n    background: var(--ddg-shade-06);\n    border-color: var(--ddg-shade-18);\n}\n@media (prefers-color-scheme: dark) {\n    .DuckDuckGoButton.tertiary:hover {\n        background: var(--ddg-tint-18);\n        border-color: var(--ddg-tint-24);\n    }\n}\n.DuckDuckGoButton.tertiary:active {\n    background: var(--ddg-shade-12);\n    border-color: var(--ddg-shade-36);\n}\n@media (prefers-color-scheme: dark) {\n    .DuckDuckGoButton.tertiary:active {\n        background: var(--ddg-tint-24);\n        border-color: var(--ddg-tint-24);\n    }\n}\n";

    var css$1 = ":host,\n* {\n    font-family: DuckDuckGoPrivacyEssentials, system, -apple-system, system-ui, BlinkMacSystemFont, 'Segoe UI', Roboto,\n        Helvetica, Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol';\n    box-sizing: border-box;\n    font-weight: normal;\n    font-style: normal;\n    margin: 0;\n    padding: 0;\n    text-align: left;\n}\n\n:host,\n.DuckDuckGoSocialContainer {\n    display: inline-block;\n    border: 0;\n    padding: 0;\n    margin: auto;\n    inset: initial;\n    max-width: 600px;\n    min-height: 180px;\n}\n\n/* SHARED STYLES */\n/* Toggle Button */\n.ddg-toggle-button-container {\n    display: flex;\n    align-items: center;\n    cursor: pointer;\n}\n.ddg-toggle-button {\n    cursor: pointer;\n    position: relative;\n    margin-top: -3px;\n    margin: 0;\n    padding: 0;\n    border: none;\n    background-color: transparent;\n    text-align: left;\n}\n.ddg-toggle-button,\n.ddg-toggle-button.md,\n.ddg-toggle-button-bg,\n.ddg-toggle-button.md .ddg-toggle-button-bg {\n    width: 32px;\n    height: 16px;\n    border-radius: 20px;\n}\n.ddg-toggle-button.lg,\n.ddg-toggle-button.lg .ddg-toggle-button-bg {\n    width: 56px;\n    height: 34px;\n    border-radius: 50px;\n}\n.ddg-toggle-button-bg {\n    right: 0;\n    overflow: visible;\n}\n.ddg-toggle-button.active .ddg-toggle-button-bg {\n    background: var(--ddg-color-primary);\n}\n.ddg-toggle-button.inactive .ddg-toggle-button-bg {\n    background: var(--ddg-color-bg-02);\n}\n.ddg-toggle-button-knob {\n    --ddg-toggle-knob-margin: 2px;\n    position: absolute;\n    display: inline-block;\n    border-radius: 50%;\n    background-color: #ffffff;\n    margin-top: var(--ddg-toggle-knob-margin);\n}\n.ddg-toggle-button-knob,\n.ddg-toggle-button.md .ddg-toggle-button-knob {\n    width: 12px;\n    height: 12px;\n    top: calc(50% - 16px / 2);\n}\n.ddg-toggle-button.lg .ddg-toggle-button-knob {\n    --ddg-toggle-knob-margin: 4px;\n    width: 26px;\n    height: 26px;\n    top: calc(50% - 34px / 2);\n}\n.ddg-toggle-button.active .ddg-toggle-button-knob {\n    right: var(--ddg-toggle-knob-margin);\n}\n.ddg-toggle-button.inactive .ddg-toggle-button-knob {\n    left: var(--ddg-toggle-knob-margin);\n}\n.ddg-toggle-button-label {\n    font-size: 14px;\n    line-height: 20px;\n    color: var(--ddg-color-txt);\n    margin-left: 12px;\n}\n\n/* Styles for DDGCtlPlaceholderBlocked */\n.DuckDuckGoButton.ddg-ctl-unblock-btn {\n    width: 100%;\n    margin: 0 auto;\n}\n.DuckDuckGoSocialContainer:is(.size-md, .size-lg) .DuckDuckGoButton.ddg-ctl-unblock-btn {\n    width: auto;\n}\n\n.ddg-ctl-placeholder-card {\n    height: 100%;\n    overflow: auto;\n    padding: 16px;\n    color: var(--ddg-color-txt);\n    background: var(--ddg-color-bg-01);\n    border: 1px solid var(--ddg-color-border);\n    border-radius: 12px;\n    margin: auto;\n    display: grid;\n    justify-content: center;\n    align-items: center;\n    line-height: 1;\n}\n.ddg-ctl-placeholder-card.slim-card {\n    padding: 12px;\n}\n.DuckDuckGoSocialContainer.size-xs .ddg-ctl-placeholder-card-body {\n    margin: auto;\n}\n.DuckDuckGoSocialContainer:is(.size-md, .size-lg) .ddg-ctl-placeholder-card.with-feedback-link {\n    height: calc(100% - 30px);\n    max-width: initial;\n    min-height: initial;\n}\n\n.ddg-ctl-placeholder-card-header {\n    width: 100%;\n    display: flex;\n    align-items: center;\n    margin: auto;\n    margin-bottom: 8px;\n    text-align: left;\n}\n.DuckDuckGoSocialContainer:is(.size-md, .size-lg) .ddg-ctl-placeholder-card-header {\n    flex-direction: column;\n    align-items: center;\n    justify-content: center;\n    margin-bottom: 12px;\n    width: 80%;\n    text-align: center;\n}\n\n.DuckDuckGoSocialContainer:is(.size-md, .size-lg) .ddg-ctl-placeholder-card-header .ddg-ctl-placeholder-card-title,\n.DuckDuckGoSocialContainer:is(.size-md, .size-lg) .ddg-ctl-placeholder-card-header .ddg-text-link {\n    text-align: center;\n}\n\n/* Show Learn More link in the header on mobile and\n * tablet size screens and hide it on desktop size */\n.DuckDuckGoSocialContainer.size-lg .ddg-ctl-placeholder-card-header .ddg-learn-more {\n    display: none;\n}\n\n.ddg-ctl-placeholder-card-title,\n.ddg-ctl-placeholder-card-title .ddg-text-link {\n    font-family: DuckDuckGoPrivacyEssentialsBold;\n    font-weight: 700;\n    font-size: 16px;\n    line-height: 24px;\n}\n\n.ddg-ctl-placeholder-card-header-dax {\n    align-self: flex-start;\n    width: 48px;\n    height: 48px;\n    margin: 0 8px 0 0;\n}\n.DuckDuckGoSocialContainer:is(.size-md, .size-lg) .ddg-ctl-placeholder-card-header-dax {\n    align-self: inherit;\n    margin: 0 0 12px 0;\n}\n\n.DuckDuckGoSocialContainer.size-lg .ddg-ctl-placeholder-card-header-dax {\n    width: 56px;\n    height: 56px;\n}\n\n.ddg-ctl-placeholder-card-body-text {\n    font-size: 16px;\n    line-height: 24px;\n    text-align: center;\n    margin: 0 auto 12px;\n\n    display: none;\n}\n.DuckDuckGoSocialContainer.size-lg .ddg-ctl-placeholder-card-body-text {\n    width: 80%;\n    display: block;\n}\n\n.ddg-ctl-placeholder-card-footer {\n    width: 100%;\n    margin-top: 12px;\n    display: flex;\n    align-items: center;\n    justify-content: flex-start;\n    align-self: end;\n}\n\n/* Only display the unblock button on really small placeholders */\n.DuckDuckGoSocialContainer.size-xs .ddg-ctl-placeholder-card-header,\n.DuckDuckGoSocialContainer.size-xs .ddg-ctl-placeholder-card-body-text,\n.DuckDuckGoSocialContainer.size-xs .ddg-ctl-placeholder-card-footer {\n    display: none;\n}\n\n.ddg-ctl-feedback-row {\n    display: none;\n}\n.DuckDuckGoSocialContainer:is(.size-md, .size-lg) .ddg-ctl-feedback-row {\n    height: 30px;\n    justify-content: flex-end;\n    align-items: center;\n    display: flex;\n}\n\n.ddg-ctl-feedback-link {\n    font-style: normal;\n    font-weight: 400;\n    font-size: 12px;\n    line-height: 12px;\n    color: var(--ddg-color-txt-link-02);\n    text-decoration: none;\n    display: inline;\n    background-color: transparent;\n    border: 0;\n    padding: 0;\n    cursor: pointer;\n}\n";

    /**
     * Size keys for a placeholder
     * @typedef { 'size-xs' | 'size-sm' | 'size-md' | 'size-lg'| null } placeholderSize
     */

    /**
     * @typedef WithToggleParams - Toggle params
     * @property {boolean} isActive - Toggle state
     * @property {string} dataKey - data-key attribute for toggle button
     * @property {string} label - Text to be presented with toggle
     * @property {'md' | 'lg'} [size=md] - Toggle size variant, 'md' by default
     * @property {() => void} onClick - Toggle on click callback
     */
    /**
     * @typedef WithFeedbackParams - Feedback link params
     * @property {string=} label - "Share Feedback" link text
     * @property {() => void} onClick - Feedback element on click callback
     */
    /**
     * @typedef LearnMoreParams - "Learn More" link params
     * @property {string} readAbout - "Learn More" aria-label text
     * @property {string} learnMore - "Learn More" link text
     */

    /**
     * The custom HTML element (Web Component) template with the placeholder for blocked
     * embedded content. The constructor gets a list of parameters with the
     * content and event handlers for this template.
     * This is currently only used in our Mobile Apps, but can be expanded in the future.
     */
    class DDGCtlPlaceholderBlockedElement extends HTMLElement {
        static CUSTOM_TAG_NAME = 'ddg-ctl-placeholder-blocked'
        /**
         * Min height that the placeholder needs to have in order to
         * have enough room to display content.
         */
        static MIN_CONTENT_HEIGHT = 110
        static MAX_CONTENT_WIDTH_SMALL = 480
        static MAX_CONTENT_WIDTH_MEDIUM = 650
        /**
         * Set observed attributes that will trigger attributeChangedCallback()
         */
        static get observedAttributes () {
            return ['style']
        }

        /**
         * Placeholder element for blocked content
         * @type {HTMLDivElement}
         */
        placeholderBlocked

        /**
         * Size variant of the latest calculated size of the placeholder.
         * This is used to add the appropriate CSS class to the placeholder container
         * and adapt the layout for each size.
         * @type {placeholderSize}
         */
        size = null

        /**
         * @param {object} params - Params for building a custom element
         *                          with a placeholder for blocked content
         * @param {boolean} params.devMode - Used to create the Shadow DOM on 'open'(true) or 'closed'(false) mode
         * @param {string} params.title - Card title text
         * @param {string} params.body - Card body text
         * @param {string} params.unblockBtnText - Unblock button text
         * @param {boolean=} params.useSlimCard - Flag for using less padding on card (ie YT CTL on mobile)
         * @param {HTMLElement} params.originalElement - The original element this placeholder is replacing.
         * @param {LearnMoreParams} params.learnMore - Localized strings for "Learn More" link.
         * @param {WithToggleParams=} params.withToggle - Toggle config to be displayed in the bottom of the placeholder
         * @param {WithFeedbackParams=} params.withFeedback - Shows feedback link on tablet and desktop sizes,
         * @param {(originalElement: HTMLIFrameElement | HTMLElement, replacementElement: HTMLElement) => (e: any) => void} params.onButtonClick
         */
        constructor (params) {
            super();
            this.params = params;
            /**
             * Create the shadow root, closed to prevent any outside observers
             * @type {ShadowRoot}
             */
            const shadow = this.attachShadow({
                mode: this.params.devMode ? 'open' : 'closed'
            });

            /**
             * Add our styles
             * @type {HTMLStyleElement}
             */
            const style = document.createElement('style');
            style.innerText = cssVars + css$1;

            /**
             * Creates the placeholder for blocked content
             * @type {HTMLDivElement}
             */
            this.placeholderBlocked = this.createPlaceholder();
            /**
             * Creates the Share Feedback element
             * @type {HTMLDivElement | null}
             */
            const feedbackLink = this.params.withFeedback ? this.createShareFeedbackLink() : null;
            /**
             * Setup the click handlers
             */
            this.setupEventListeners(this.placeholderBlocked, feedbackLink);

            /**
             * Append both to the shadow root
             */
            feedbackLink && this.placeholderBlocked.appendChild(feedbackLink);
            shadow.appendChild(this.placeholderBlocked);
            shadow.appendChild(style);
        }

        /**
         * Creates a placeholder for content blocked by Click to Load.
         * Note: We're using arrow functions () => {} in this class due to a bug
         * found in Firefox where it is getting the wrong "this" context on calls in the constructor.
         * This is a temporary workaround.
         * @returns {HTMLDivElement}
         */
        createPlaceholder = () => {
            const { title, body, unblockBtnText, useSlimCard, withToggle, withFeedback } = this.params;

            const container = document.createElement('div');
            container.classList.add('DuckDuckGoSocialContainer');
            const cardClassNames = [
                ['slim-card', !!useSlimCard],
                ['with-feedback-link', !!withFeedback]
            ]
                .map(([className, active]) => (active ? className : ''))
                .join(' ');

            // Only add a card footer if we have the toggle button to display
            const cardFooterSection = withToggle
                ? html`<div class="ddg-ctl-placeholder-card-footer">${this.createToggleButton()}</div> `
                : '';
            const learnMoreLink = this.createLearnMoreLink();

            container.innerHTML = html`
            <div class="ddg-ctl-placeholder-card ${cardClassNames}">
                <div class="ddg-ctl-placeholder-card-header">
                    <img class="ddg-ctl-placeholder-card-header-dax" src=${logoImg} alt="DuckDuckGo Dax" />
                    <div class="ddg-ctl-placeholder-card-title">${title}. ${learnMoreLink}</div>
                </div>
                <div class="ddg-ctl-placeholder-card-body">
                    <div class="ddg-ctl-placeholder-card-body-text">${body} ${learnMoreLink}</div>
                    <button class="DuckDuckGoButton tertiary ddg-ctl-unblock-btn" type="button">
                        <div>${unblockBtnText}</div>
                    </button>
                </div>
                ${cardFooterSection}
            </div>
        `.toString();

            return container
        }

        /**
         * Creates a template string for Learn More link.
         */
        createLearnMoreLink = () => {
            const { learnMore } = this.params;

            return html`<a
            class="ddg-text-link ddg-learn-more"
            aria-label="${learnMore.readAbout}"
            href="https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/"
            target="_blank"
            >${learnMore.learnMore}</a
        >`
        }

        /**
         * Creates a Feedback Link container row
         * @returns {HTMLDivElement}
         */
        createShareFeedbackLink = () => {
            const { withFeedback } = this.params;

            const container = document.createElement('div');
            container.classList.add('ddg-ctl-feedback-row');

            container.innerHTML = html`
            <button class="ddg-ctl-feedback-link" type="button">${withFeedback?.label || 'Share Feedback'}</button>
        `.toString();

            return container
        }

        /**
         * Creates a template string for a toggle button with text.
         */
        createToggleButton = () => {
            const { withToggle } = this.params;
            if (!withToggle) return

            const { isActive, dataKey, label, size: toggleSize = 'md' } = withToggle;

            const toggleButton = html`
            <div class="ddg-toggle-button-container">
                <button
                    class="ddg-toggle-button ${isActive ? 'active' : 'inactive'} ${toggleSize}"
                    type="button"
                    aria-pressed=${!!isActive}
                    data-key=${dataKey}
                >
                    <div class="ddg-toggle-button-bg"></div>
                    <div class="ddg-toggle-button-knob"></div>
                </button>
                <div class="ddg-toggle-button-label">${label}</div>
            </div>
        `;
            return toggleButton
        }

        /**
         *
         * @param {HTMLElement} containerElement
         * @param {HTMLElement?} feedbackLink
         */
        setupEventListeners = (containerElement, feedbackLink) => {
            const { withToggle, withFeedback, originalElement, onButtonClick } = this.params;

            containerElement
                .querySelector('button.ddg-ctl-unblock-btn')
                ?.addEventListener('click', onButtonClick(originalElement, this));

            if (withToggle) {
                containerElement
                    .querySelector('.ddg-toggle-button-container')
                    ?.addEventListener('click', withToggle.onClick);
            }
            if (withFeedback && feedbackLink) {
                feedbackLink.querySelector('.ddg-ctl-feedback-link')?.addEventListener('click', withFeedback.onClick);
            }
        }

        /**
         * Use JS to calculate the width and height of the root element placeholder. We could use a CSS Container Query, but full
         * support to it was only added recently, so we're not using it for now.
         * https://caniuse.com/css-container-queries
         */
        updatePlaceholderSize = () => {
            /** @type {placeholderSize} */
            let newSize = null;

            const { height, width } = this.getBoundingClientRect();
            if (height && height < DDGCtlPlaceholderBlockedElement.MIN_CONTENT_HEIGHT) {
                newSize = 'size-xs';
            } else if (width) {
                if (width < DDGCtlPlaceholderBlockedElement.MAX_CONTENT_WIDTH_SMALL) {
                    newSize = 'size-sm';
                } else if (width < DDGCtlPlaceholderBlockedElement.MAX_CONTENT_WIDTH_MEDIUM) {
                    newSize = 'size-md';
                } else {
                    newSize = 'size-lg';
                }
            }

            if (newSize && newSize !== this.size) {
                if (this.size) {
                    this.placeholderBlocked.classList.remove(this.size);
                }
                this.placeholderBlocked.classList.add(newSize);
                this.size = newSize;
            }
        }

        /**
         * Web Component lifecycle function.
         * When element is first added to the DOM, trigger this callback and
         * update the element CSS size class.
         */
        connectedCallback () {
            this.updatePlaceholderSize();
        }

        /**
         * Web Component lifecycle function.
         * When the root element gets the 'style' attribute updated, reflect that in the container
         * element inside the shadow root. This way, we can copy the size and other styles from the root
         * element and have the inner context be able to use the same sizes to adapt the template layout.
         * @param {string} attr Observed attribute key
         * @param {*} _ Attribute old value, ignored
         * @param {*} newValue Attribute new value
         */
        attributeChangedCallback (attr, _, newValue) {
            if (attr === 'style') {
                this.placeholderBlocked[attr].cssText = newValue;
                this.updatePlaceholderSize();
            }
        }
    }

    var css = ":host,\n* {\n    font-family: DuckDuckGoPrivacyEssentials, system, -apple-system, system-ui, BlinkMacSystemFont, 'Segoe UI', Roboto,\n        Helvetica, Arial, sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji', 'Segoe UI Symbol';\n    box-sizing: border-box;\n    font-weight: normal;\n    font-style: normal;\n    margin: 0;\n    padding: 0;\n    text-align: left;\n}\n\n/* SHARED STYLES */\n/* Popover */\n.ddg-popover {\n    background: #ffffff;\n    border: 1px solid rgba(0, 0, 0, 0.1);\n    border-radius: 16px;\n    box-shadow: 0px 2px 6px rgba(0, 0, 0, 0.12), 0px 8px 16px rgba(0, 0, 0, 0.08);\n    width: 360px;\n    margin-top: 10px;\n    z-index: 2147483647;\n    position: absolute;\n    line-height: normal;\n}\n.ddg-popover-arrow {\n    display: inline-block;\n    background: #ffffff;\n    border: solid rgba(0, 0, 0, 0.1);\n    border-width: 0 1px 1px 0;\n    padding: 5px;\n    transform: rotate(-135deg);\n    -webkit-transform: rotate(-135deg);\n    position: relative;\n    top: -9px;\n}\n.ddg-popover .ddg-title-header {\n    padding: 0px 12px 12px;\n    margin-top: -5px;\n}\n.ddg-popover-body {\n    font-size: 14px;\n    line-height: 21px;\n    margin: auto;\n    padding: 17px;\n    text-align: left;\n}\n\n/* DDG common header */\n.ddg-title-header {\n    display: flex;\n    padding: 12px;\n    max-height: 44px;\n    border-bottom: 1px solid;\n    border-color: rgba(196, 196, 196, 0.3);\n    margin: 0;\n    margin-bottom: 4px;\n}\n.ddg-title-header .ddg-title-text {\n    line-height: 1.4;\n    font-size: 14px;\n    margin: auto 10px;\n    flex-basis: 100%;\n    height: 1.4em;\n    flex-wrap: wrap;\n    overflow: hidden;\n    text-align: left;\n    border: none;\n    padding: 0;\n}\n.ddg-title-header .ddg-logo {\n    flex-basis: 0%;\n    min-width: 20px;\n    height: 21px;\n    border: none;\n    padding: 0;\n    margin: 0;\n}\n.ddg-title-header .ddg-logo .ddg-logo-img {\n    height: 21px;\n    width: 21px;\n}\n\n/* CTL Login Button styles */\n#DuckDuckGoPrivacyEssentialsHoverable {\n    padding-bottom: 10px;\n}\n\n#DuckDuckGoPrivacyEssentialsHoverableText {\n    display: none;\n}\n#DuckDuckGoPrivacyEssentialsHoverable:hover #DuckDuckGoPrivacyEssentialsHoverableText {\n    display: block;\n}\n\n.DuckDuckGoButton.tertiary.ddg-ctl-fb-login-btn {\n    background-color: var(--ddg-color-bg-01);\n}\n@media (prefers-color-scheme: dark) {\n    .DuckDuckGoButton.tertiary.ddg-ctl-fb-login-btn {\n        background: #111111;\n    }\n}\n.DuckDuckGoButton.tertiary:hover {\n    background: rgb(238, 238, 238);\n    border-color: var(--ddg-shade-18);\n}\n@media (prefers-color-scheme: dark) {\n    .DuckDuckGoButton.tertiary:hover {\n        background: rgb(39, 39, 39);\n        border-color: var(--ddg-tint-24);\n    }\n}\n.DuckDuckGoButton.tertiary:active {\n    background: rgb(220, 220, 220);\n    border-color: var(--ddg-shade-36);\n}\n@media (prefers-color-scheme: dark) {\n    .DuckDuckGoButton.tertiary:active {\n        background: rgb(65, 65, 65);\n        border-color: var(--ddg-tint-24);\n    }\n}\n\n.ddg-ctl-button-login-icon {\n    margin-right: 8px;\n    height: 20px;\n    width: 20px;\n}\n\n.ddg-fb-login-container {\n    position: relative;\n    margin: auto;\n    width: auto;\n}\n";

    /**
     * @typedef LearnMoreParams - "Learn More" link params
     * @property {string} readAbout - "Learn More" aria-label text
     * @property {string} learnMore - "Learn More" link text
     */

    /**
     * Template for creating a <div/> element placeholder for blocked login embedded buttons.
     * The constructor gets a list of parameters with the
     * content and event handlers for this template.
     * This is currently only used in our Mobile Apps, but can be expanded in the future.
     */
    class DDGCtlLoginButton {
        /**
         * Placeholder container element for blocked login button
         * @type {HTMLDivElement}
         */
        #element

        /**
         * @param {object} params - Params for building a custom element with
         *                          a placeholder for a blocked login button
         * @param {boolean} params.devMode - Used to create the Shadow DOM on 'open'(true) or 'closed'(false) mode
         * @param {string} params.label - Button text
         * @param {string} params.logoIcon - Logo image to be displayed in the Login Button to the left of the label text
         * @param {string} params.hoverText - Text for popover on button hover
         * @param {boolean=} params.useSlimCard - Flag for using less padding on card (ie YT CTL on mobile)
         * @param {HTMLElement} params.originalElement - The original element this placeholder is replacing.
         * @param {LearnMoreParams} params.learnMore - Localized strings for "Learn More" link.
         * @param {(originalElement: HTMLIFrameElement | HTMLElement, replacementElement: HTMLElement) => (e: any) => void} params.onClick
         */
        constructor (params) {
            this.params = params;

            /**
             * Create the placeholder element to be inject in the page
             * @type {HTMLDivElement}
             */
            this.element = document.createElement('div');

            /**
             * Create the shadow root, closed to prevent any outside observers
             * @type {ShadowRoot}
             */
            const shadow = this.element.attachShadow({
                mode: this.params.devMode ? 'open' : 'closed'
            });

            /**
             * Add our styles
             * @type {HTMLStyleElement}
             */
            const style = document.createElement('style');
            style.innerText = cssVars + css;

            /**
             * Create the Facebook login button
             * @type {HTMLDivElement}
             */
            const loginButton = this._createLoginButton();

            /**
             * Setup the click handlers
             */
            this._setupEventListeners(loginButton);

            /**
             * Append both to the shadow root
             */
            shadow.appendChild(loginButton);
            shadow.appendChild(style);
        }

        /**
         * @returns {HTMLDivElement}
         */
        get element () {
            return this.#element
        }

        /**
         * @param {HTMLDivElement} el - New placeholder element
         */
        set element (el) {
            this.#element = el;
        }

        /**
         * Creates a placeholder Facebook login button. When clicked, a warning dialog
         * is displayed to the user. The login flow only continues if the user clicks to
         * proceed.
         * @returns {HTMLDivElement}
         */
        _createLoginButton () {
            const { label, hoverText, logoIcon, learnMore } = this.params;

            const { popoverStyle, arrowStyle } = this._calculatePopoverPosition();

            const container = document.createElement('div');
            // Add our own styles and inherit any local class styles on the button
            container.classList.add('ddg-fb-login-container');

            container.innerHTML = html`
            <div id="DuckDuckGoPrivacyEssentialsHoverable">
                <!-- Login Button -->
                <button class="DuckDuckGoButton tertiary ddg-ctl-fb-login-btn">
                    <img class="ddg-ctl-button-login-icon" height="20px" width="20px" src="${logoIcon}" />
                    <div>${label}</div>
                </button>

                <!-- Popover - hover box -->
                <div id="DuckDuckGoPrivacyEssentialsHoverableText" class="ddg-popover" style="${popoverStyle}">
                    <div class="ddg-popover-arrow" style="${arrowStyle}"></div>

                    <div class="ddg-title-header">
                        <div class="ddg-logo">
                            <img class="ddg-logo-img" src="${logoImg}" height="21px" />
                        </div>
                        <div id="DuckDuckGoPrivacyEssentialsCTLElementTitle" class="ddg-title-text">DuckDuckGo</div>
                    </div>

                    <div class="ddg-popover-body">
                        ${hoverText}
                        <a
                            class="ddg-text-link"
                            aria-label="${learnMore.readAbout}"
                            href="https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/"
                            target="_blank"
                            id="learnMoreLink"
                        >
                            ${learnMore.learnMore}
                        </a>
                    </div>
                </div>
            </div>
        `.toString();

            return container
        }

        /**
         * The left side of the popover may go offscreen if the
         * login button is all the way on the left side of the page. This
         * If that is the case, dynamically shift the box right so it shows
         * properly.
         * @returns {{
         *  popoverStyle: string, // CSS styles to be applied in the Popover container
         *  arrowStyle: string,   // CSS styles to be applied in the Popover arrow
         * }}
         */
        _calculatePopoverPosition () {
            const { originalElement } = this.params;
            const rect = originalElement.getBoundingClientRect();
            const textBubbleWidth = 360; // Should match the width rule in .ddg-popover
            const textBubbleLeftShift = 100; // Should match the CSS left: rule in .ddg-popover
            const arrowDefaultLocationPercent = 50;

            let popoverStyle;
            let arrowStyle;

            if (rect.left < textBubbleLeftShift) {
                const leftShift = -rect.left + 10; // 10px away from edge of the screen
                popoverStyle = `left: ${leftShift}px;`;
                const change = (1 - rect.left / textBubbleLeftShift) * (100 - arrowDefaultLocationPercent);
                arrowStyle = `left: ${Math.max(10, arrowDefaultLocationPercent - change)}%;`;
            } else if (rect.left + textBubbleWidth - textBubbleLeftShift > window.innerWidth) {
                const rightShift = rect.left + textBubbleWidth - textBubbleLeftShift;
                const diff = Math.min(rightShift - window.innerWidth, textBubbleLeftShift);
                const rightMargin = 20; // Add some margin to the page, so scrollbar doesn't overlap.
                popoverStyle = `left: -${textBubbleLeftShift + diff + rightMargin}px;`;
                const change = (diff / textBubbleLeftShift) * (100 - arrowDefaultLocationPercent);
                arrowStyle = `left: ${Math.max(10, arrowDefaultLocationPercent + change)}%;`;
            } else {
                popoverStyle = `left: -${textBubbleLeftShift}px;`;
                arrowStyle = `left: ${arrowDefaultLocationPercent}%;`;
            }

            return { popoverStyle, arrowStyle }
        }

        /**
         *
         * @param {HTMLElement} loginButton
         */
        _setupEventListeners (loginButton) {
            const { originalElement, onClick } = this.params;

            loginButton
                .querySelector('.ddg-ctl-fb-login-btn')
                ?.addEventListener('click', onClick(originalElement, this.element));
        }
    }

    /**
     * Register custom elements in this wrapper function to be called only when we need to
     * and also to allow remote-config later if needed.
     */
    function registerCustomElements () {
        if (!customElements.get(DDGCtlPlaceholderBlockedElement.CUSTOM_TAG_NAME)) {
            customElements.define(DDGCtlPlaceholderBlockedElement.CUSTOM_TAG_NAME, DDGCtlPlaceholderBlockedElement);
        }
    }

    /**
     * @typedef {'darkMode' | 'lightMode' | 'loginMode' | 'cancelMode'} displayMode
     *   Key for theme value to determine the styling of buttons/placeholders.
     *   Matches `styles[mode]` keys:
     *     - `'lightMode'`: Primary colors styling for light theme
     *     - `'darkMode'`: Primary colors styling for dark theme
     *     - `'cancelMode'`: Secondary colors styling for all themes
     */

    let devMode = false;
    let isYoutubePreviewsEnabled = false;
    let appID;

    const titleID = 'DuckDuckGoPrivacyEssentialsCTLElementTitle';

    // Configuration for how the placeholder elements should look and behave.
    // @see {getConfig}
    let config = null;
    let sharedStrings = null;
    let styles = null;

    /**
     * List of platforms where we can skip showing a Web Modal from C-S-S.
     * It is generally expected that the platform will show a native modal instead.
     * @type {import('../utils').Platform["name"][]} */
    const platformsWithNativeModalSupport = ['android', 'ios'];
    /**
     * Platforms supporting the new layout using Web Components.
     * @type {import('../utils').Platform["name"][]} */
    const platformsWithWebComponentsEnabled = ['android', 'ios'];
    /**
     * Based on the current Platform where the Widget is running, it will
     * return if it is one of our mobile apps or not. This should be used to
     * define which layout to use between Mobile and Desktop Platforms variations.
     * @type {import('../utils').Platform["name"][]} */
    const mobilePlatforms = ['android', 'ios'];

    // TODO: Remove these redundant data structures and refactor the related code.
    //       There should be no need to have the entity configuration stored in two
    //       places.
    const entities = [];
    const entityData = {};

    // Used to avoid displaying placeholders for the same tracking element twice.
    const knownTrackingElements = new WeakSet();

    // Promise that is resolved when the Click to Load feature init() function has
    // finished its work, enough that it's now safe to replace elements with
    // placeholders.
    let readyToDisplayPlaceholdersResolver;
    const readyToDisplayPlaceholders = new Promise(resolve => {
        readyToDisplayPlaceholdersResolver = resolve;
    });

    // Promise that is resolved when the page has finished loading (and
    // readyToDisplayPlaceholders has resolved). Wait for this before sending
    // essential messages to surrogate scripts.
    let afterPageLoadResolver;
    const afterPageLoad = new Promise(resolve => { afterPageLoadResolver = resolve; });

    // Messaging layer for Click to Load. The messaging instance is initialized in
    // ClickToLoad.init() and updated here to be used outside ClickToLoad class
    // we need a module scoped reference.
    /** @type {import("@duckduckgo/messaging").Messaging} */
    let _messagingModuleScope;
    /** @type function */
    let _addDebugFlag;
    const ctl = {
        /**
         * @return {import("@duckduckgo/messaging").Messaging}
         */
        get messaging () {
            if (!_messagingModuleScope) throw new Error('Messaging not initialized')
            return _messagingModuleScope
        },

        addDebugFlag () {
            if (!_addDebugFlag) throw new Error('addDebugFlag not initialized')
            return _addDebugFlag()
        }
    };

    /*********************************************************
     *  Widget Replacement logic
     *********************************************************/
    class DuckWidget {
        /**
         * @param {Object} widgetData
         *   The configuration for this "widget" as determined in ctl-config.js.
         * @param {HTMLElement} originalElement
         *   The original tracking element to replace with a placeholder.
         * @param {string} entity
         *   The entity behind the tracking element (e.g. "Facebook, Inc.").
         * @param {import('../utils').Platform} platform
         *   The platform where Click to Load and the Duck Widget is running on (ie Extension, Android App, etc)
         */
        constructor (widgetData, originalElement, entity, platform) {
            this.clickAction = { ...widgetData.clickAction }; // shallow copy
            this.replaceSettings = widgetData.replaceSettings;
            this.originalElement = originalElement;
            this.placeholderElement = null;
            this.dataElements = {};
            this.gatherDataElements();
            this.entity = entity;
            this.widgetID = Math.random();
            this.autoplay = false;
            // Boolean if widget is unblocked and content should not be blocked
            this.isUnblocked = false;
            this.platform = platform;
        }

        /**
         * Dispatch an event on the target element, including the widget's ID and
         * other details.
         * @param {EventTarget} eventTarget
         * @param {string} eventName
         */
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

        /**
         * Take note of some of the tracking element's attributes (as determined by
         * clickAction.urlDataAttributesToPreserve) and store those in
         * this.dataElement.
         */
        gatherDataElements () {
            if (!this.clickAction.urlDataAttributesToPreserve) {
                return
            }
            for (const [attrName, attrSettings] of Object.entries(this.clickAction.urlDataAttributesToPreserve)) {
                let value = this.originalElement.getAttribute(attrName);
                if (!value) {
                    if (attrSettings.required) {
                        // Missing a required attribute means we won't be able to replace it
                        // with a light version, replace with full version.
                        this.clickAction.type = 'allowFull';
                    }

                    // If the attribute is "width", try first to measure the parent's width and use that as a default value.
                    if (attrName === 'data-width') {
                        const windowWidth = window.innerWidth;
                        const { parentElement } = this.originalElement;
                        const parentStyles = parentElement
                            ? window.getComputedStyle(parentElement)
                            : null;
                        let parentInnerWidth = null;

                        // We want to calculate the inner width of the parent element as the iframe, when added back,
                        // should not be bigger than the space available in the parent element. There is no straightforward way of
                        // doing this. We need to get the parent's .clientWidth and remove the paddings size from it.
                        if (parentElement && parentStyles && parentStyles.display !== 'inline') {
                            parentInnerWidth = parentElement.clientWidth - parseFloat(parentStyles.paddingLeft) - parseFloat(parentStyles.paddingRight);
                        }

                        if (parentInnerWidth && parentInnerWidth < windowWidth) {
                            value = parentInnerWidth.toString();
                        } else {
                            // Our default value for width is often greater than the window size of smaller
                            // screens (ie mobile). Then use whatever is the smallest value.
                            value = Math.min(attrSettings.default, windowWidth).toString();
                        }
                    } else {
                        value = attrSettings.default;
                    }
                }
                this.dataElements[attrName] = value;
            }
        }

        /**
         * Return the URL of the Facebook content, for use when a Facebook Click to
         * Load placeholder has been clicked by the user.
         * @returns {string}
         */
        getTargetURL () {
            // Copying over data fields should be done lazily, since some required data may not be
            // captured until after page scripts run.
            this.copySocialDataFields();
            return this.clickAction.targetURL
        }

        /**
         * Determines which display mode the placeholder element should render in.
         * @returns {displayMode}
         */
        getMode () {
            // Login buttons are always the login style types
            if (this.replaceSettings.type === 'loginButton') {
                return 'loginMode'
            }
            if (window?.matchMedia('(prefers-color-scheme: dark)')?.matches) {
                return 'darkMode'
            }
            return 'lightMode'
        }

        /**
         * Take note of some of the tracking element's style attributes (as
         * determined by clickAction.styleDataAttributes) as a CSS string.
         *
         * @returns {string}
         */
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

        /**
         * Store some attributes from the original tracking element, used for both
         * placeholder element styling, and when restoring the original tracking
         * element.
         */
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

        /**
         * Creates an iFrame for this facebook content.
         *
         * @returns {HTMLIFrameElement}
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
         * @param {HTMLIFrameElement} videoElement
         * @returns {EventListener?} onError
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

        /**
         * Fades the given element in/out.
         * @param {HTMLElement} element
         *   The element to fade in or out.
         * @param {number} interval
         *   Frequency of opacity updates (ms).
         * @param {boolean} fadeIn
         *   True if the element should fade in instead of out.
         * @returns {Promise<void>}
         *    Promise that resolves when the fade in/out is complete.
         */
        fadeElement (element, interval, fadeIn) {
            return new Promise(resolve => {
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

        /**
         * Fades the given element out.
         * @param {HTMLElement} element
         *   The element to fade out.
         * @returns {Promise<void>}
         *    Promise that resolves when the fade out is complete.
         */
        fadeOutElement (element) {
            return this.fadeElement(element, 10, false)
        }

        /**
         * Fades the given element in.
         * @param {HTMLElement} element
         *   The element to fade in.
         * @returns {Promise<void>}
         *    Promise that resolves when the fade in is complete.
         */
        fadeInElement (element) {
            return this.fadeElement(element, 10, true)
        }

        /**
         * The function that's called when the user clicks to load some content.
         * Unblocks the content, puts it back in the page, and removes the
         * placeholder.
         * @param {HTMLIFrameElement} originalElement
         *   The original tracking element.
         * @param {HTMLElement} replacementElement
         *   The placeholder element.
         */
        clickFunction (originalElement, replacementElement) {
            let clicked = false;
            const handleClick = e => {
                // Ensure that the click is created by a user event & prevent double clicks from adding more animations
                if (e.isTrusted && !clicked) {
                    e.stopPropagation();
                    this.isUnblocked = true;
                    clicked = true;
                    let isLogin = false;
                    // Logins triggered by user click means they were not triggered by the surrogate
                    const isSurrogateLogin = false;
                    const clickElement = e.srcElement; // Object.assign({}, e)
                    if (this.replaceSettings.type === 'loginButton') {
                        isLogin = true;
                    }
                    const action = this.entity === 'Youtube' ? 'block-ctl-yt' : 'block-ctl-fb';
                    // eslint-disable-next-line promise/prefer-await-to-then
                    unblockClickToLoadContent({ entity: this.entity, action, isLogin, isSurrogateLogin }).then((response) => {
                        // If user rejected confirmation modal and content was not unblocked, inform surrogate and stop.
                        if (response && response.type === 'ddg-ctp-user-cancel') {
                            return abortSurrogateConfirmation(this.entity)
                        }

                        const parent = replacementElement.parentNode;

                        // The placeholder was removed from the DOM while we loaded
                        // the original content, give up.
                        if (!parent) return

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

                        // Modify the overlay to include a Facebook iFrame, which
                        // starts invisible. Once loaded, fade out and remove the
                        // overlay then fade in the Facebook content.
                        parent.replaceChild(fbContainer, replacementElement);
                        fbContainer.appendChild(replacementElement);
                        fadeIn.appendChild(fbElement);
                        fbElement.addEventListener('load', async () => {
                            await this.fadeOutElement(replacementElement);
                            fbContainer.replaceWith(fbElement);
                            this.dispatchEvent(fbElement, 'ddg-ctp-placeholder-clicked');
                            await this.fadeInElement(fadeIn);
                            // Focus on new element for screen readers.
                            fbElement.focus();
                        }, { once: true });
                        // Note: This event only fires on Firefox, on Chrome the frame's
                        //       load event will always fire.
                        if (onError) {
                            fbElement.addEventListener('error', onError, { once: true });
                        }
                    });
                }
            };
            // If this is a login button, show modal if needed
            if (this.replaceSettings.type === 'loginButton' && entityData[this.entity].shouldShowLoginModal) {
                return e => {
                    // Even if the user cancels the login attempt, consider Facebook Click to
                    // Load to have been active on the page if the user reports the page as broken.
                    if (this.entity === 'Facebook, Inc.') {
                        notifyFacebookLogin();
                    }

                    handleUnblockConfirmation(
                        this.platform.name, this.entity, handleClick, e
                    );
                }
            }
            return handleClick
        }

        /**
         * Based on the current Platform where the Widget is running, it will
         * return if the new layout using Web Components is supported or not.
         * @returns {boolean}
         */
        shouldUseCustomElement () {
            return platformsWithWebComponentsEnabled.includes(this.platform.name)
        }

        /**
         * Based on the current Platform where the Widget is running, it will
         * return if it is one of our mobile apps or not. This should be used to
         * define which layout to use between Mobile and Desktop Platforms variations.
         * @returns {boolean}
         */
        isMobilePlatform () {
            return mobilePlatforms.includes(this.platform.name)
        }
    }

    /**
     * Replace the given tracking element with the given placeholder.
     * Notes:
     *  1. This function also dispatches events targeting the original and
     *     placeholder elements. That way, the surrogate scripts can use the event
     *     targets to keep track of which placeholder corresponds to which tracking
     *     element.
     *  2. To achieve that, the original and placeholder elements must be in the DOM
     *     at the time the events are dispatched. Otherwise, the events will not
     *     bubble up and the surrogate script will miss them.
     *  3. Placeholder must be shown immediately (to avoid a flicker for the user),
     *     but the events must only be sent once the document (and therefore
     *     surrogate scripts) have loaded.
     *  4. Therefore, we hide the element until the page has loaded, then dispatch
     *     the events after page load, and then remove the element from the DOM.
     *  5. The "ddg-ctp-ready" event needs to be dispatched _after_ the element
     *     replacement events have fired. That is why a setTimeout is required
     *     before dispatching "ddg-ctp-ready".
     *
     *  Also note, this all assumes that the surrogate script that needs these
     *  events will not be loaded asynchronously after the page has finished
     *  loading.
     *
     * @param {DuckWidget} widget
     *   The DuckWidget associated with the tracking element.
     * @param {HTMLElement} trackingElement
     *   The tracking element on the page to replace.
     * @param {HTMLElement} placeholderElement
     *   The placeholder element that should be shown instead.
     */
    function replaceTrackingElement (widget, trackingElement, placeholderElement) {
        // In some situations (e.g. YouTube Click to Load previews are
        // enabled/disabled), a second placeholder will be shown for a tracking
        // element.
        const elementToReplace = widget.placeholderElement || trackingElement;

        // Note the placeholder element, so that it can also be replaced later if
        // necessary.
        widget.placeholderElement = placeholderElement;

        // First hide the element, since we need to keep it in the DOM until the
        // events have been dispatched.
        const originalDisplay = [
            elementToReplace.style.getPropertyValue('display'),
            elementToReplace.style.getPropertyPriority('display')
        ];
        elementToReplace.style.setProperty('display', 'none', 'important');

        // When iframes are blocked by the declarativeNetRequest API, they are
        // collapsed (hidden) automatically. Unfortunately however, there's a bug
        // that stops them from being uncollapsed (shown again) if they are removed
        // from the DOM after they are collapsed. As a workaround, have the iframe
        // load a benign data URI, so that it's uncollapsed, before removing it from
        // the DOM. See https://crbug.com/1428971
        const originalSrc = elementToReplace.src || elementToReplace.getAttribute('data-src');
        elementToReplace.src =
            'data:text/plain;charset=utf-8;base64,' + btoa('https://crbug.com/1428971');

        // Add the placeholder element to the page.
        elementToReplace.parentElement.insertBefore(
            placeholderElement, elementToReplace
        );

        // While the placeholder is shown (and original element hidden)
        // synchronously, the events are dispatched (and original element removed
        // from the DOM) asynchronously after the page has finished loading.
        // eslint-disable-next-line promise/prefer-await-to-then
        afterPageLoad.then(() => {
            // With page load complete, and both elements in the DOM, the events can
            // be dispatched.
            widget.dispatchEvent(trackingElement, 'ddg-ctp-tracking-element');
            widget.dispatchEvent(placeholderElement, 'ddg-ctp-placeholder-element');

            // Once the events are sent, the tracking element (or previous
            // placeholder) can finally be removed from the DOM.
            elementToReplace.remove();
            elementToReplace.style.setProperty('display', ...originalDisplay);
            elementToReplace.src = originalSrc;
        });
    }

    /**
     * Creates a placeholder element for the given tracking element and replaces
     * it on the page.
     * @param {DuckWidget} widget
     *   The CTL 'widget' associated with the tracking element.
     * @param {HTMLIFrameElement} trackingElement
     *   The tracking element on the page that should be replaced with a placeholder.
     */
    function createPlaceholderElementAndReplace (widget, trackingElement) {
        if (widget.replaceSettings.type === 'blank') {
            replaceTrackingElement(widget, trackingElement, document.createElement('div'));
        }

        if (widget.replaceSettings.type === 'loginButton') {
            const icon = widget.replaceSettings.icon;
            // Create a button to replace old element
            if (widget.shouldUseCustomElement()) {
                const facebookLoginButton = new DDGCtlLoginButton({
                    devMode,
                    label: widget.replaceSettings.buttonTextUnblockLogin,
                    hoverText: widget.replaceSettings.popupBodyText,
                    logoIcon: facebookLogo,
                    originalElement: trackingElement,
                    learnMore: { // Localized strings for "Learn More" link.
                        readAbout: sharedStrings.readAbout,
                        learnMore: sharedStrings.learnMore
                    },
                    onClick: widget.clickFunction.bind(widget)
                }).element;
                facebookLoginButton.classList.add('fb-login-button', 'FacebookLogin__button');
                facebookLoginButton.appendChild(makeFontFaceStyleElement());
                replaceTrackingElement(widget, trackingElement, facebookLoginButton);
            } else {
                const { button, container } = makeLoginButton(
                    widget.replaceSettings.buttonText, widget.getMode(),
                    widget.replaceSettings.popupBodyText, icon, trackingElement
                );
                button.addEventListener('click', widget.clickFunction(trackingElement, container));
                replaceTrackingElement(widget, trackingElement, container);
            }
        }

        // Facebook
        if (widget.replaceSettings.type === 'dialog') {
            ctl.addDebugFlag();
            ctl.messaging.notify('updateFacebookCTLBreakageFlags', { ctlFacebookPlaceholderShown: true });
            if (widget.shouldUseCustomElement()) {
                /**
                 * Creates a custom HTML element with the placeholder element for blocked
                 * embedded content. The constructor gets a list of parameters with the
                 * content and event handlers for this HTML element.
                 */
                const mobileBlockedPlaceholder = new DDGCtlPlaceholderBlockedElement({
                    devMode,
                    title: widget.replaceSettings.infoTitle, // Card title text
                    body: widget.replaceSettings.infoText, // Card body text
                    unblockBtnText: widget.replaceSettings.buttonText, // Unblock button text
                    useSlimCard: false, // Flag for using less padding on card (ie YT CTL on mobile)
                    originalElement: trackingElement, // The original element this placeholder is replacing.
                    learnMore: { // Localized strings for "Learn More" link.
                        readAbout: sharedStrings.readAbout,
                        learnMore: sharedStrings.learnMore
                    },
                    onButtonClick: widget.clickFunction.bind(widget)
                });
                mobileBlockedPlaceholder.appendChild(makeFontFaceStyleElement());

                replaceTrackingElement(widget, trackingElement, mobileBlockedPlaceholder);
                showExtraUnblockIfShortPlaceholder(null, mobileBlockedPlaceholder);
            } else {
                const icon = widget.replaceSettings.icon;
                const button = makeButton(widget.replaceSettings.buttonText, widget.getMode());
                const textButton = makeTextButton(widget.replaceSettings.buttonText, widget.getMode());
                const { contentBlock, shadowRoot } = createContentBlock(
                    widget, button, textButton, icon
                );
                button.addEventListener('click', widget.clickFunction(trackingElement, contentBlock));
                textButton.addEventListener('click', widget.clickFunction(trackingElement, contentBlock));

                replaceTrackingElement(widget, trackingElement, contentBlock);
                showExtraUnblockIfShortPlaceholder(shadowRoot, contentBlock);
            }
        }

        // YouTube
        if (widget.replaceSettings.type === 'youtube-video') {
            ctl.addDebugFlag();
            ctl.messaging.notify('updateYouTubeCTLAddedFlag', { youTubeCTLAddedFlag: true });
            replaceYouTubeCTL(trackingElement, widget);

            // Subscribe to changes to youtubePreviewsEnabled setting
            // and update the CTL state
            ctl.messaging.subscribe(
                'setYoutubePreviewsEnabled',
                ({ value }) => {
                    isYoutubePreviewsEnabled = value;
                    replaceYouTubeCTL(trackingElement, widget);
                }
            );
        }
    }

    /**
     * @param {HTMLIFrameElement} trackingElement
     *   The original tracking element (YouTube video iframe)
     * @param {DuckWidget} widget
     *   The CTL 'widget' associated with the tracking element.
     */
    function replaceYouTubeCTL (trackingElement, widget) {
        // Skip replacing tracking element if it has already been unblocked
        if (widget.isUnblocked) {
            return
        }

        if (isYoutubePreviewsEnabled === true) {
            // Show YouTube Preview for embedded video
            const oldPlaceholder = widget.placeholderElement;
            const { youTubePreview, shadowRoot } = createYouTubePreview(trackingElement, widget);
            resizeElementToMatch(oldPlaceholder || trackingElement, youTubePreview);
            replaceTrackingElement(widget, trackingElement, youTubePreview);
            showExtraUnblockIfShortPlaceholder(shadowRoot, youTubePreview);
        } else {
            // Block YouTube embedded video and display blocking dialog
            widget.autoplay = false;
            const oldPlaceholder = widget.placeholderElement;

            if (widget.shouldUseCustomElement()) {
                /**
                 * Creates a custom HTML element with the placeholder element for blocked
                 * embedded content. The constructor gets a list of parameters with the
                 * content and event handlers for this HTML element.
                 */
                const mobileBlockedPlaceholderElement = new DDGCtlPlaceholderBlockedElement({
                    devMode,
                    title: widget.replaceSettings.infoTitle, // Card title text
                    body: widget.replaceSettings.infoText, // Card body text
                    unblockBtnText: widget.replaceSettings.buttonText, // Unblock button text
                    useSlimCard: true, // Flag for using less padding on card (ie YT CTL on mobile)
                    originalElement: trackingElement, // The original element this placeholder is replacing.
                    learnMore: { // Localized strings for "Learn More" link.
                        readAbout: sharedStrings.readAbout,
                        learnMore: sharedStrings.learnMore
                    },
                    withToggle: { // Toggle config to be displayed in the bottom of the placeholder
                        isActive: false, // Toggle state
                        dataKey: 'yt-preview-toggle', // data-key attribute for button
                        label: widget.replaceSettings.previewToggleText, // Text to be presented with toggle
                        size: widget.isMobilePlatform() ? 'lg' : 'md',
                        onClick: () => ctl.messaging.notify('setYoutubePreviewsEnabled', { youtubePreviewsEnabled: true }) // Toggle click callback
                    },
                    withFeedback: {
                        label: sharedStrings.shareFeedback,
                        onClick: () => openShareFeedbackPage()
                    },
                    onButtonClick: widget.clickFunction.bind(widget)
                });
                mobileBlockedPlaceholderElement.appendChild(makeFontFaceStyleElement());
                mobileBlockedPlaceholderElement.id = trackingElement.id;
                resizeElementToMatch(oldPlaceholder || trackingElement, mobileBlockedPlaceholderElement);
                replaceTrackingElement(widget, trackingElement, mobileBlockedPlaceholderElement);
                showExtraUnblockIfShortPlaceholder(null, mobileBlockedPlaceholderElement);
            } else {
                const { blockingDialog, shadowRoot } = createYouTubeBlockingDialog(trackingElement, widget);
                resizeElementToMatch(oldPlaceholder || trackingElement, blockingDialog);
                replaceTrackingElement(widget, trackingElement, blockingDialog);
                showExtraUnblockIfShortPlaceholder(shadowRoot, blockingDialog);
                hideInfoTextIfNarrowPlaceholder(shadowRoot, blockingDialog, 460);
            }
        }
    }

    /**
     * Show the extra unblock link in the header if the placeholder or
     * its parent is too short for the normal unblock button to be visible.
     * Note: This does not take into account the placeholder's vertical
     *       position in the parent element.
     * @param {ShadowRoot?} shadowRoot
     * @param {HTMLElement} placeholder Placeholder for tracking element
     */
    function showExtraUnblockIfShortPlaceholder (shadowRoot, placeholder) {
        if (!placeholder.parentElement) {
            return
        }
        const parentStyles = window.getComputedStyle(placeholder.parentElement);
        // Inline elements, like span or p, don't have a height value that we can use because they're
        // not a "block" like element with defined sizes. Because we skip this check on "inline"
        // parents, it might be necessary to traverse up the DOM tree until we find the nearest non
        // "inline" parent to get a reliable height for this check.
        if (parentStyles.display === 'inline') {
            return
        }
        const { height: placeholderHeight } = placeholder.getBoundingClientRect();
        const { height: parentHeight } = placeholder.parentElement.getBoundingClientRect();

        if (
            (placeholderHeight > 0 && placeholderHeight <= 200) ||
            (parentHeight > 0 && parentHeight <= 230)
        ) {
            if (shadowRoot) {
                /** @type {HTMLElement?} */
                const titleRowTextButton = shadowRoot.querySelector(`#${titleID + 'TextButton'}`);
                if (titleRowTextButton) {
                    titleRowTextButton.style.display = 'block';
                }
            }
            // Avoid the placeholder being taller than the containing element
            // and overflowing.
            /** @type {HTMLElement?} */
            const blockedDiv = shadowRoot?.querySelector('.DuckDuckGoSocialContainer') || placeholder;
            if (blockedDiv) {
                blockedDiv.style.minHeight = 'initial';
                blockedDiv.style.maxHeight = parentHeight + 'px';
                blockedDiv.style.overflow = 'hidden';
            }
        }
    }

    /**
     * Hide the info text (and move the "Learn More" link) if the placeholder is too
     * narrow.
     * @param {ShadowRoot} shadowRoot
     * @param {HTMLElement} placeholder Placeholder for tracking element
     * @param {number} narrowWidth
     *    Maximum placeholder width (in pixels) for the placeholder to be considered
     *    narrow.
     */
    function hideInfoTextIfNarrowPlaceholder (shadowRoot, placeholder, narrowWidth) {
        const { width: placeholderWidth } = placeholder.getBoundingClientRect();
        if (placeholderWidth > 0 && placeholderWidth <= narrowWidth) {
            const buttonContainer =
                  shadowRoot.querySelector('.DuckDuckGoButton.primary')?.parentElement;
            const contentTitle = shadowRoot.getElementById('contentTitle');
            const infoText = shadowRoot.getElementById('infoText');
            /** @type {HTMLElement?} */
            const learnMoreLink = shadowRoot.getElementById('learnMoreLink');

            // These elements will exist, but this check keeps TypeScript happy.
            if (!buttonContainer || !contentTitle || !infoText || !learnMoreLink) {
                return
            }

            // Remove the information text.
            infoText.remove();
            learnMoreLink.remove();

            // Append the "Learn More" link to the title.
            contentTitle.innerText += '. ';
            learnMoreLink.style.removeProperty('font-size');
            contentTitle.appendChild(learnMoreLink);

            // Improve margin/padding, to ensure as much is displayed as possible.
            buttonContainer.style.removeProperty('margin');
        }
    }

    /*********************************************************
     *  Messaging to surrogates & extension
     *********************************************************/

    /**
     * @typedef unblockClickToLoadContentRequest
     * @property {string} entity
     *   The entity to unblock requests for (e.g. "Facebook, Inc.").
     * @property {boolean} [isLogin=false]
     *   True if we should "allow social login", defaults to false.
     * @property {boolean} [isSurrogateLogin=false]
     *   True if logins triggered by the surrogate (custom login), False if login trigger
     *   by user clicking in our Login button placeholder.
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
     * @see {@link ddg-ctp-unblockClickToLoadContent-complete} for the response handler.
     * @returns {Promise<any>}
     */
    function unblockClickToLoadContent (message) {
        return ctl.messaging.request('unblockClickToLoadContent', message)
    }

    /**
     * Handle showing a web modal to request the user for a confirmation or, in some platforms,
     * proceed with the "acceptFunction" call and let the platform handle with each request
     * accordingly.
     * @param {import('../utils').Platform["name"]} platformName
     *   The current platform name where Click to Load is running
     * @param {string} entity
     *   The entity to unblock requests for (e.g. "Facebook, Inc.") if the user
     *   clicks to proceed.
     * @param {function} acceptFunction
     *   The function to call if the user has clicked to proceed.
     * @param {...any} acceptFunctionParams
     *   The parameters passed to acceptFunction when it is called.
     */
    function handleUnblockConfirmation (platformName, entity, acceptFunction, ...acceptFunctionParams) {
        // In our mobile platforms, we want to show a native UI to request user unblock
        // confirmation. In these cases we send directly the unblock request to the platform
        // and the platform chooses how to best handle it.
        if (platformsWithNativeModalSupport.includes(platformName)) {
            acceptFunction(...acceptFunctionParams);
        // By default, for other platforms (ie Extension), we show a web modal with a
        // confirmation request to the user before we proceed to unblock the content.
        } else {
            makeModal(entity, acceptFunction, ...acceptFunctionParams);
        }
    }

    /**
     * Set the ctlFacebookLogin breakage flag for the page, to indicate that the
     * Facebook Click to Load login flow had started if the user should then report
     * the website as broken.
     */
    function notifyFacebookLogin () {
        ctl.addDebugFlag();
        ctl.messaging.notify('updateFacebookCTLBreakageFlags', { ctlFacebookLogin: true });
    }

    /**
     * Unblock the entity, close the login dialog and continue the Facebook login
     * flow. Called after the user clicks to proceed after the warning dialog is
     * shown.
     * @param {string} entity
     */
    async function runLogin (entity) {
        if (entity === 'Facebook, Inc.') {
            notifyFacebookLogin();
        }

        const action = entity === 'Youtube' ? 'block-ctl-yt' : 'block-ctl-fb';
        const response = await unblockClickToLoadContent({ entity, action, isLogin: true, isSurrogateLogin: true });
        // If user rejected confirmation modal and content was not unblocked, inform surrogate and stop.
        if (response && response.type === 'ddg-ctp-user-cancel') {
            return abortSurrogateConfirmation(this.entity)
        }
        // Communicate with surrogate to run login
        originalWindowDispatchEvent(
            createCustomEvent('ddg-ctp-run-login', {
                detail: {
                    entity
                }
            })
        );
    }

    /**
     * Communicate with the surrogate to abort (ie Abort login when user rejects confirmation dialog)
     * Called after the user cancel from a warning dialog.
     * @param {string} entity
     */
    function abortSurrogateConfirmation (entity) {
        originalWindowDispatchEvent(
            createCustomEvent('ddg-ctp-cancel-modal', {
                detail: {
                    entity
                }
            })
        );
    }

    function openShareFeedbackPage () {
        ctl.messaging.notify('openShareFeedbackPage');
    }

    /*********************************************************
     *  Widget building blocks
     *********************************************************/

    /**
     * Creates a "Learn more" link element.
     * @param {displayMode} [mode='lightMode']
     * @returns {HTMLAnchorElement}
     */
    function getLearnMoreLink (mode = 'lightMode') {
        const linkElement = document.createElement('a');
        linkElement.style.cssText = styles.generalLink + styles[mode].linkFont;
        linkElement.ariaLabel = sharedStrings.readAbout;
        linkElement.href = 'https://help.duckduckgo.com/duckduckgo-help-pages/privacy/embedded-content-protection/';
        linkElement.target = '_blank';
        linkElement.textContent = sharedStrings.learnMore;
        linkElement.id = 'learnMoreLink';
        return linkElement
    }

    /**
     * Resizes and positions the target element to match the source element.
     * @param {HTMLElement} sourceElement
     * @param {HTMLElement} targetElement
     */
    function resizeElementToMatch (sourceElement, targetElement) {
        const computedStyle = window.getComputedStyle(sourceElement);
        const stylesToCopy = ['position', 'top', 'bottom', 'left', 'right',
            'transform', 'margin'];

        // It's apparently preferable to use the source element's size relative to
        // the current viewport, when resizing the target element. However, the
        // declarativeNetRequest API "collapses" (hides) blocked elements. When
        // that happens, getBoundingClientRect will return all zeros.
        // TODO: Remove this entirely, and always use the computed height/width of
        //       the source element instead?
        const { height, width } = sourceElement.getBoundingClientRect();
        if (height > 0 && width > 0) {
            targetElement.style.height = height + 'px';
            targetElement.style.width = width + 'px';
        } else {
            stylesToCopy.push('height', 'width');
        }

        for (const key of stylesToCopy) {
            targetElement.style[key] = computedStyle[key];
        }

        // If the parent element is very small (and its dimensions can be trusted) set a max height/width
        // to avoid the placeholder overflowing.
        if (computedStyle.display !== 'inline') {
            if (targetElement.style.maxHeight < computedStyle.height) {
                targetElement.style.maxHeight = 'initial';
            }
            if (targetElement.style.maxWidth < computedStyle.width) {
                targetElement.style.maxWidth = 'initial';
            }
        }
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
     * @param {displayMode} [mode='lightMode']
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

    /**
     * Creates an anchor element with no destination. It is expected that a click
     * handler is added to the element later.
     * @param {string} linkText
     * @param {displayMode} mode
     * @returns {HTMLAnchorElement}
     */
    function makeTextButton (linkText, mode = 'lightMode') {
        const linkElement = document.createElement('a');
        linkElement.style.cssText = styles.headerLink + styles[mode].linkFont;
        linkElement.textContent = linkText;
        return linkElement
    }

    /**
     * Create a button element.
     * @param {string} buttonText
     *   Text to be displayed inside the button.
     * @param {displayMode} [mode='lightMode']
     *   The button is usually styled as the primary call to action, but if
     *   'cancelMode' is specified the button is styled as a secondary call to
     *   action.
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

    /**
     * Create a toggle button.
     * @param {displayMode} mode
     * @param {boolean} [isActive=false]
     *   True if the button should be toggled by default.
     * @param {string} [classNames='']
     *   Class names to assign to the button (space delimited).
     * @param {string} [dataKey='']
     *   Value to assign to the button's 'data-key' attribute.
     * @returns {HTMLButtonElement}
     */
    function makeToggleButton (mode, isActive = false, classNames = '', dataKey = '') {
        const toggleButton = document.createElement('button');
        toggleButton.className = classNames;
        toggleButton.style.cssText = styles.toggleButton;
        toggleButton.type = 'button';
        toggleButton.setAttribute('aria-pressed', isActive ? 'true' : 'false');
        toggleButton.setAttribute('data-key', dataKey);

        const activeKey = isActive ? 'active' : 'inactive';

        const toggleBg = document.createElement('div');
        toggleBg.style.cssText =
            styles.toggleButtonBg + styles[mode].toggleButtonBgState[activeKey];

        const toggleKnob = document.createElement('div');
        toggleKnob.style.cssText =
            styles.toggleButtonKnob + styles.toggleButtonKnobState[activeKey];

        toggleButton.appendChild(toggleBg);
        toggleButton.appendChild(toggleKnob);

        return toggleButton
    }

    /**
     * Create a toggle button that's wrapped in a div with some text.
     * @param {string} text
     *   Text to display by the button.
     * @param {displayMode} mode
     * @param {boolean} [isActive=false]
     *   True if the button should be toggled by default.
     * @param {string} [toggleClassNames='']
     *   Class names to assign to the toggle button.
     * @param {string} [textCssStyles='']
     *   Styles to apply to the wrapping div (on top of ones determined by the
     *   display mode.)
     * @param {string} [dataKey='']
     *   Value to assign to the button's 'data-key' attribute.
     * @returns {HTMLDivElement}
     */
    function makeToggleButtonWithText (text, mode, isActive = false, toggleClassNames = '', textCssStyles = '', dataKey = '') {
        const wrapper = document.createElement('div');
        wrapper.style.cssText = styles.toggleButtonWrapper;

        const toggleButton = makeToggleButton(mode, isActive, toggleClassNames, dataKey);

        const textDiv = document.createElement('div');
        textDiv.style.cssText = styles.contentText + styles.toggleButtonText + styles[mode].toggleButtonText + textCssStyles;
        textDiv.textContent = text;

        wrapper.appendChild(toggleButton);
        wrapper.appendChild(textDiv);
        return wrapper
    }

    /**
     * Create the default block symbol, for when the image isn't available.
     * @returns {HTMLDivElement}
     */
    function makeDefaultBlockIcon () {
        const blockedIcon = document.createElement('div');
        const dash = document.createElement('div');
        blockedIcon.appendChild(dash);
        blockedIcon.style.cssText = styles.circle;
        dash.style.cssText = styles.rectangle;
        return blockedIcon
    }

    /**
     * Creates a share feedback link element.
     * @returns {HTMLAnchorElement}
     */
    function makeShareFeedbackLink () {
        const feedbackLink = document.createElement('a');
        feedbackLink.style.cssText = styles.feedbackLink;
        feedbackLink.target = '_blank';
        feedbackLink.href = '#';
        feedbackLink.text = sharedStrings.shareFeedback;
        // Open Feedback Form page through background event to avoid browser blocking extension link
        feedbackLink.addEventListener('click', function (e) {
            e.preventDefault();
            openShareFeedbackPage();
        });

        return feedbackLink
    }

    /**
     * Creates a share feedback link element, wrapped in a styled div.
     * @returns {HTMLDivElement}
     */
    function makeShareFeedbackRow () {
        const feedbackRow = document.createElement('div');
        feedbackRow.style.cssText = styles.feedbackRow;

        const feedbackLink = makeShareFeedbackLink();
        feedbackRow.appendChild(feedbackLink);

        return feedbackRow
    }

    /**
     * Creates a placeholder Facebook login button. When clicked, a warning dialog
     * is displayed to the user. The login flow only continues if the user clicks to
     * proceed.
     * @param {string} buttonText
     * @param {displayMode} mode
     * @param {string} hoverTextBody
     *   The hover text to display for the button.
     * @param {string?} icon
     *   The source of the icon to display in the button, if null the default block
     *   icon is used instead.
     * @param {HTMLElement} originalElement
     *   The original Facebook login button that this placeholder is replacing.
     *   Note: This function does not actually replace the button, the caller is
     *         expected to do that.
     * @returns {{ container: HTMLDivElement, button: HTMLButtonElement }}
     */
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

        // The left side of the hover popup may go offscreen if the
        // login button is all the way on the left side of the page. This
        // If that is the case, dynamically shift the box right so it shows
        // properly.
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

    /**
     * Creates a privacy warning dialog for the user, so that the user can choose to
     * proceed/abort.
     * @param {string} entity
     *   The entity to unblock requests for (e.g. "Facebook, Inc.") if the user
     *   clicks to proceed.
     * @param {function} acceptFunction
     *   The function to call if the user has clicked to proceed.
     * @param {...any} acceptFunctionParams
     *   The parameters passed to acceptFunction when it is called.
     *   TODO: Have the caller bind these arguments to the function instead.
     */
    function makeModal (entity, acceptFunction, ...acceptFunctionParams) {
        const icon = entityData[entity].modalIcon;

        const modalContainer = document.createElement('div');
        modalContainer.setAttribute('data-key', 'modal');
        modalContainer.style.cssText = styles.modalContainer;

        modalContainer.appendChild(makeFontFaceStyleElement());

        const closeModal = () => {
            document.body.removeChild(modalContainer);
            abortSurrogateConfirmation(entity);
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

    /**
     * Create the "title row" div that contains a placeholder's heading.
     * @param {string} message
     *   The title text to display.
     * @param {HTMLAnchorElement?} [textButton]
     *   The link to display with the title, if any.
     * @param {EventListener} [closeBtnFn]
     *   If provided, a close button is added that calls this function when clicked.
     * @returns {HTMLDivElement}
     */
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

    /**
     * Create a placeholder element (wrapped in a div and shadowRoot), to replace a
     * tracking element with.
     * @param {DuckWidget} widget
     *   Widget corresponding to the tracking element.
     * @param {HTMLButtonElement} button
     *   Primary button that loads the original tracking element (and removed this
     *   placeholder) when clicked.
     * @param {HTMLAnchorElement?} textButton
     *   Link to display next to the title, if any.
     * @param {string?} img
     *   Source of image to display in the placeholder (if any).
     * @param {HTMLDivElement} [bottomRow]
     *   Bottom row to append to the placeholder, if any.
     * @returns {{ contentBlock: HTMLDivElement, shadowRoot: ShadowRoot }}
     */
    function createContentBlock (widget, button, textButton, img, bottomRow) {
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
        contentTitle.id = 'contentTitle';
        contentRow.appendChild(contentTitle);
        const contentText = document.createElement('div');
        contentText.style.cssText = styles.contentText;
        const contentTextSpan = document.createElement('span');
        contentTextSpan.id = 'infoText';
        contentTextSpan.textContent = widget.replaceSettings.infoText + ' ';
        contentText.appendChild(contentTextSpan);
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

    /**
     * Create the content block to replace embedded YouTube videos/iframes with.
     * @param {HTMLIFrameElement} trackingElement
     * @param {DuckWidget} widget
     * @returns {{ blockingDialog: HTMLElement, shadowRoot: ShadowRoot }}
     */
    function createYouTubeBlockingDialog (trackingElement, widget) {
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
            () => makeModal(widget.entity, () => ctl.messaging.notify('setYoutubePreviewsEnabled', { youtubePreviewsEnabled: true }), widget.entity)
        );
        bottomRow.appendChild(previewToggle);

        const { contentBlock, shadowRoot } = createContentBlock(
            widget, button, textButton, null, bottomRow
        );
        contentBlock.id = trackingElement.id;
        contentBlock.style.cssText += styles.wrapperDiv + styles.youTubeWrapperDiv;

        button.addEventListener('click', widget.clickFunction(trackingElement, contentBlock));
        textButton.addEventListener('click', widget.clickFunction(trackingElement, contentBlock));

        return {
            blockingDialog: contentBlock,
            shadowRoot
        }
    }

    /**
     * Creates the placeholder element to replace a YouTube video iframe element
     * with a preview image. Mutates widget Object to set the autoplay property
     * as the preview details load.
     * @param {HTMLIFrameElement} originalElement
     *   The YouTube video iframe element.
     * @param {DuckWidget} widget
     *   The widget Object. We mutate this to set the autoplay property.
     * @returns {{ youTubePreview: HTMLElement, shadowRoot: ShadowRoot }}
     *   Object containing the YouTube Preview element and its shadowRoot.
     */
    function createYouTubePreview (originalElement, widget) {
        const youTubePreview = document.createElement('div');
        youTubePreview.id = originalElement.id;
        youTubePreview.style.cssText = styles.wrapperDiv + styles.placeholderWrapperDiv;

        youTubePreview.appendChild(makeFontFaceStyleElement());

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

        // TODO: Use `widget.replaceSettings.placeholder.previewToggleEnabledDuckDuckGoText` for toggle
        // copy when implementing mobile YT CTL Preview
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
            () => ctl.messaging.notify('setYoutubePreviewsEnabled', { youtubePreviewsEnabled: false })
        );

        /** Preview Info Text */
        const previewText = document.createElement('div');
        previewText.style.cssText = styles.contentText + styles.toggleButtonText + styles.youTubePreviewInfoText;
        // Since this string contains an anchor element, setting innerText won't
        // work.
        // Warning: This is not ideal! The translated (and original) strings must be
        //          checked very carefully! Any HTML they contain will be inserted.
        //          Ideally, the translation system would allow only certain element
        //          types to be included, and would avoid the URLs for links being
        //          included in the translations.
        previewText.insertAdjacentHTML(
            'beforeend', widget.replaceSettings.placeholder.previewInfoText
        );
        const previewTextLink = previewText.querySelector('a');
        if (previewTextLink) {
            const newPreviewTextLink = getLearnMoreLink(widget.getMode());
            newPreviewTextLink.innerText = previewTextLink.innerText;
            previewTextLink.replaceWith(newPreviewTextLink);
        }

        previewToggleRow.appendChild(previewToggle);
        previewToggleRow.appendChild(previewText);
        innerDiv.appendChild(previewToggleRow);

        youTubePreviewDiv.appendChild(innerDiv);

        // We use .then() instead of await here to show the placeholder right away
        // while the YouTube endpoint takes it time to respond.
        const videoURL = originalElement.src || originalElement.getAttribute('data-src');
        ctl.messaging.request('getYouTubeVideoDetails', { videoURL })
            // eslint-disable-next-line promise/prefer-await-to-then
            .then(({ videoURL: videoURLResp, status, title, previewImage }) => {
                if (!status || videoURLResp !== videoURL) { return }
                if (status === 'success') {
                    titleElement.innerText = title;
                    titleElement.title = title;
                    if (previewImage) {
                        previewImageElement.setAttribute('src', previewImage);
                    }
                    widget.autoplay = true;
                }
            });

        /** Share Feedback Link */
        const feedbackRow = makeShareFeedbackRow();
        shadowRoot.appendChild(feedbackRow);

        return { youTubePreview, shadowRoot }
    }

    /**
     * @typedef {import('@duckduckgo/messaging').MessagingContext} MessagingContext
     */

    class ClickToLoad extends ContentFeature {
        /** @type {MessagingContext} */
        #messagingContext

        async init (args) {
            /**
             * Bail if no messaging backend - this is a debugging feature to ensure we don't
             * accidentally enabled this
             */
            if (!this.messaging) {
                throw new Error('Cannot operate click to load without a messaging backend')
            }
            _messagingModuleScope = this.messaging;
            _addDebugFlag = this.addDebugFlag.bind(this);

            const websiteOwner = args?.site?.parentEntity;
            const settings = args?.featureSettings?.clickToLoad || {};
            const locale = args?.locale || 'en';
            const localizedConfig = getConfig(locale);
            config = localizedConfig.config;
            sharedStrings = localizedConfig.sharedStrings;
            // update styles if asset config was sent
            styles = getStyles(this.assetConfig);

            /**
             * Register Custom Elements only when Click to Load is initialized, to ensure it is only
             * called when config is ready and any previous context have been appropriately invalidated
             * prior when applicable (ie Firefox when hot reloading the Extension)
             */
            registerCustomElements();

            for (const entity of Object.keys(config)) {
                // Strip config entities that are first-party, or aren't enabled in the
                // extension's clickToLoad settings.
                if ((websiteOwner && entity === websiteOwner) ||
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

            // Listen for window events from "surrogate" scripts.
            window.addEventListener('ddg-ctp', (/** @type {CustomEvent} */ event) => {
                if (!('detail' in event)) return

                const entity = event.detail?.entity;
                if (!entities.includes(entity)) {
                    // Unknown entity, reject
                    return
                }
                if (event.detail?.appID) {
                    appID = JSON.stringify(event.detail.appID).replace(/"/g, '');
                }
                // Handle login call
                if (event.detail?.action === 'login') {
                    // Even if the user cancels the login attempt, consider Facebook Click to
                    // Load to have been active on the page if the user reports the page as broken.
                    if (entity === 'Facebook, Inc.') {
                        notifyFacebookLogin();
                    }

                    if (entityData[entity].shouldShowLoginModal) {
                        handleUnblockConfirmation(this.platform.name, entity, runLogin, entity);
                    } else {
                        runLogin(entity);
                    }
                }
            });
            // Listen to message from Platform letting CTL know that we're ready to
            // replace elements in the page
            // eslint-disable-next-line promise/prefer-await-to-then
            this.messaging.subscribe(
                'displayClickToLoadPlaceholders',
                // TODO: Pass `message.options.ruleAction` through, that way only
                //       content corresponding to the entity for that ruleAction need to
                //       be replaced with a placeholder.
                () => this.replaceClickToLoadElements()
            );

            // Request the current state of Click to Load from the platform.
            // Note: When the response is received, the response handler resolves
            //       the readyToDisplayPlaceholders Promise.
            const clickToLoadState = await this.messaging.request('getClickToLoadState');
            this.onClickToLoadState(clickToLoadState);

            // Then wait for the page to finish loading, and resolve the
            // afterPageLoad Promise.
            if (document.readyState === 'complete') {
                afterPageLoadResolver();
            } else {
                window.addEventListener('load', afterPageLoadResolver, { once: true });
            }
            await afterPageLoad;

            // On some websites, the "ddg-ctp-ready" event is occasionally
            // dispatched too early, before the listener is ready to receive it.
            // To counter that, catch "ddg-ctp-surrogate-load" events dispatched
            // _after_ page, so the "ddg-ctp-ready" event can be dispatched again.
            window.addEventListener(
                'ddg-ctp-surrogate-load', () => {
                    originalWindowDispatchEvent(createCustomEvent('ddg-ctp-ready'));
                }
            );

            // Then wait for any in-progress element replacements, before letting
            // the surrogate scripts know to start.
            window.setTimeout(() => {
                originalWindowDispatchEvent(createCustomEvent('ddg-ctp-ready'));
            }, 0);
        }

        /**
         * This is only called by the current integration between Android and Extension and is now
         * used to connect only these Platforms responses with the temporary implementation of
         * SendMessageMessagingTransport that wraps this communication.
         * This can be removed once they have their own Messaging integration.
         */
        update (message) {
            // TODO: Once all Click to Load messages include the feature property, drop
            //       messages that don't include the feature property too.
            if (message?.feature && message?.feature !== 'clickToLoad') return

            const messageType = message?.messageType;
            if (!messageType) return

            if (!this._clickToLoadMessagingTransport) {
                throw new Error('_clickToLoadMessagingTransport not ready. Cannot operate click to load without a messaging backend')
            }

            // Send to Messaging layer the response or subscription message received
            // from the Platform.
            return this._clickToLoadMessagingTransport.onResponse(message)
        }

        /**
         * Update Click to Load internal state
         * @param {Object} state Click to Load state response from the Platform
         * @param {boolean} state.devMode Developer or Production environment
         * @param {boolean} state.youtubePreviewsEnabled YouTube Click to Load - YT Previews enabled flag
         */
        onClickToLoadState (state) {
            devMode = state.devMode;
            isYoutubePreviewsEnabled = state.youtubePreviewsEnabled;

            // Mark the feature as ready, to allow placeholder
            // replacements to start.
            readyToDisplayPlaceholdersResolver();
        }

        /**
         * Replace the blocked CTL elements on the page with placeholders.
         * @param {HTMLElement} [targetElement]
         *   If specified, only this element will be replaced (assuming it matches
         *   one of the expected CSS selectors). If omitted, all matching elements
         *   in the document will be replaced instead.
         */
        async replaceClickToLoadElements (targetElement) {
            await readyToDisplayPlaceholders;

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
                        if (knownTrackingElements.has(trackingElement)) {
                            return Promise.resolve()
                        }

                        knownTrackingElements.add(trackingElement);

                        const widget = new DuckWidget(widgetData, trackingElement, entity, this.platform);
                        return createPlaceholderElementAndReplace(widget, trackingElement)
                    }));
                }
            }
        }

        /**
         * @returns {MessagingContext}
         */
        get messagingContext () {
            if (this.#messagingContext) return this.#messagingContext
            this.#messagingContext = this._createMessagingContext();
            return this.#messagingContext
        }

        // Messaging layer between Click to Load and the Platform
        get messaging () {
            if (this._messaging) return this._messaging

            if (this.platform.name === 'android' || this.platform.name === 'extension' || this.platform.name === 'macos') {
                this._clickToLoadMessagingTransport = new SendMessageMessagingTransport();
                const config = new TestTransportConfig(this._clickToLoadMessagingTransport);
                this._messaging = new Messaging(this.messagingContext, config);
                return this._messaging
            } else if (this.platform.name === 'ios') {
                const config = new WebkitMessagingConfig({
                    secret: '',
                    hasModernWebkitAPI: true,
                    webkitMessageHandlerNames: ['contentScopeScripts']
                });
                this._messaging = new Messaging(this.messagingContext, config);
                return this._messaging
            } else {
                throw new Error('Messaging not supported yet on platform: ' + this.name)
            }
        }
    }

    var platformFeatures = {
        ddg_feature_runtimeChecks: RuntimeChecks,
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
        ddg_feature_webCompat: WebCompat,
        ddg_feature_clickToLoad: ClickToLoad
    };

    /* global false */

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
    const isHTMLDocument = (
        document instanceof HTMLDocument || (
            document instanceof XMLDocument &&
                document.createElement('div') instanceof HTMLDivElement
        )
    );

    /**
     * @typedef {object} LoadArgs
     * @property {import('./content-feature').Site} site
     * @property {import('./utils.js').Platform} platform
     * @property {boolean} documentOriginIsTracker
     * @property {import('./utils.js').RemoteConfig} bundledConfig
     * @property {string} [injectName]
     * @property {object} trackerLookup - provided currently only by the extension
     * @property {import('@duckduckgo/messaging').MessagingConfig} [messagingConfig]
     */

    /**
     * @param {LoadArgs} args
     */
    function load (args) {
        const mark = performanceMonitor.mark('load');
        if (!isHTMLDocument) {
            return
        }

        const featureNames = platformSupport["android"]
            ;

        for (const featureName of featureNames) {
            const ContentFeature = platformFeatures['ddg_feature_' + featureName];
            const featureInstance = new ContentFeature(featureName);
            featureInstance.callLoad(args);
            features.push({ featureName, featureInstance });
        }
        mark.end();
    }

    async function init (args) {
        const mark = performanceMonitor.mark('init');
        initArgs = args;
        if (!isHTMLDocument) {
            return
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

    function alwaysInitExtensionFeatures (args, featureName) {
        return args.platform.name === 'extension' && alwaysInitFeatures.has(featureName)
    }

    async function updateFeaturesInner (args) {
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
    function isTrackerOrigin (trackerLookup, originHostname = document.location.hostname) {
        const parts = originHostname.split('.').reverse();
        let node = trackerLookup;
        for (const sub of parts) {
            if (node[sub] === 1) {
                return true
            } else if (node[sub]) {
                node = node[sub];
            } else {
                return false
            }
        }
        return false
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

        const configConstruct = processedConfig;
        const messageCallback = configConstruct.messageCallback;
        const messageSecret = configConstruct.messageSecret;
        const javascriptInterface = configConstruct.javascriptInterface;
        processedConfig.messagingConfig = new AndroidMessagingConfig({
            messageSecret,
            messageCallback,
            javascriptInterface,
            target: globalThis,
            debug: processedConfig.debug
        });

        load({
            platform: processedConfig.platform,
            trackerLookup: processedConfig.trackerLookup,
            documentOriginIsTracker: isTrackerOrigin(processedConfig.trackerLookup),
            site: processedConfig.site,
            bundledConfig: processedConfig.bundledConfig,
            messagingConfig: processedConfig.messagingConfig
        });

        init(processedConfig);
    }

    initCode();

})();
