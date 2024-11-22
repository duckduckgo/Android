/*! © DuckDuckGo ContentScopeScripts protections https://github.com/duckduckgo/content-scope-scripts/ */
(function () {
    'use strict';

    /* eslint-disable no-redeclare */
    const Set$1 = globalThis.Set;
    const Reflect$1 = globalThis.Reflect;
    globalThis.customElements?.get.bind(globalThis.customElements);
    globalThis.customElements?.define.bind(globalThis.customElements);
    const getOwnPropertyDescriptor = Object.getOwnPropertyDescriptor;
    const getOwnPropertyDescriptors = Object.getOwnPropertyDescriptors;
    const objectKeys = Object.keys;
    const objectEntries = Object.entries;
    const objectDefineProperty = Object.defineProperty;
    const Proxy$1 = globalThis.Proxy;
    globalThis.dispatchEvent?.bind(globalThis);
    globalThis.addEventListener?.bind(globalThis);
    globalThis.removeEventListener?.bind(globalThis);
    globalThis.crypto?.randomUUID?.bind(globalThis.crypto);

    /* eslint-disable no-redeclare, no-global-assign */
    /* global cloneInto, exportFunction, false */

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
                    return DDGReflect.apply(...args);
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
            {
                this._native = objectScope[property];
                const handler = {};
                handler.apply = outputHandler;
                handler.get = getMethod;
                this.internal = new globalObj.Proxy(objectScope[property], handler);
            }
        }

        // Actually apply the proxy to the native property
        overload() {
            {
                this.objectScope[this.property] = this.internal;
            }
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

    let DDGReflect;

    // Exports for usage where we have to cross the xray boundary: https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Sharing_objects_with_page_scripts
    {
        DDGReflect = globalObj.Reflect;
    }

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
            originalWindowDispatchEvent(createCustomEvent('sendMessageProxy' + messageSecret, { detail: { messageType, options } }))
        );
        // TBD other platforms
    }

    /**
     * Takes a function that returns an element and tries to find it with exponential backoff.
     * @param {number} delay
     * @param {number} [maxAttempts=4] - The maximum number of attempts to find the element.
     * @param {number} [delay=500] - The initial delay to be used to create the exponential backoff.
     * @returns {Promise<Element|HTMLElement|null>}
     */
    function withExponentialBackoff(fn, maxAttempts = 4, delay = 500) {
        return new Promise((resolve, reject) => {
            let attempts = 0;
            const tryFn = () => {
                attempts += 1;
                const error = new Error$1('Element not found');
                try {
                    const element = fn();
                    if (element) {
                        resolve(element);
                    } else if (attempts < maxAttempts) {
                        setTimeout(tryFn, delay * Math.pow(2, attempts));
                    } else {
                        reject(error);
                    }
                } catch {
                    if (attempts < maxAttempts) {
                        setTimeout(tryFn, delay * Math.pow(2, attempts));
                    } else {
                        reject(error);
                    }
                }
            };
            tryFn();
        });
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
        android: [...baseFeatures, 'webCompat', 'clickToLoad', 'breakageReporting', 'duckPlayer'],
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

    /* global false, cloneInto, exportFunction */


    /**
     * Like Object.defineProperty, but with support for Firefox's mozProxies.
     * @param {any} object - object whose property we are wrapping (most commonly a prototype, e.g. globalThis.BatteryManager.prototype)
     * @param {string} propertyName
     * @param {import('./wrapper-utils').StrictPropertyDescriptor} descriptor - requires all descriptor options to be defined because we can't validate correctness based on TS types
     */
    function defineProperty(object, propertyName, descriptor) {
        {
            objectDefineProperty(object, propertyName, descriptor);
        }
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

    const ANIMATION_DURATION_MS = 1000;
    const ANIMATION_ITERATIONS = Infinity;
    const BACKGROUND_COLOR_START = 'rgba(85, 127, 243, 0.10)';
    const BACKGROUND_COLOR_END = 'rgba(85, 127, 243, 0.25)';
    const OVERLAY_ID = 'ddg-password-import-overlay';

    /**
     * @typedef ButtonAnimationStyle
     * @property {Record<string, string>} transform
     * @property {string} zIndex
     * @property {string} borderRadius
     * @property {number} offsetLeftEm
     * @property {number} offsetTopEm
     */

    /**
     * @typedef ElementConfig
     * @property {HTMLElement|Element|SVGElement} element
     * @property {ButtonAnimationStyle} animationStyle
     * @property {boolean} shouldTap
     * @property {boolean} shouldWatchForRemoval
     */

    /**
     * This feature is responsible for animating some buttons passwords.google.com,
     * during a password import flow. The overall approach is:
     * 1. Check if the path is supported,
     * 2. Find the element to animate based on the path - using structural selectors first and then fallback to label texts),
     * 3. Animate the element, or tap it if it should be autotapped.
     */
    class AutofillPasswordImport extends ContentFeature {
        #exportButtonSettings;

        #settingsButtonSettings;

        #signInButtonSettings;

        /** @type {HTMLElement|Element|SVGElement|null} */
        #elementToCenterOn;

        /** @type {HTMLElement|null} */
        #currentOverlay;

        /** @type {ElementConfig|null} */
        #currentElementConfig;

        #domLoaded;

        /**
         * @returns {ButtonAnimationStyle}
         */
        get settingsButtonAnimationStyle() {
            return {
                transform: {
                    start: 'scale(0.90)',
                    mid: 'scale(0.96)',
                },
                zIndex: '984',
                borderRadius: '100%',
                offsetLeftEm: 0.01,
                offsetTopEm: 0,
            };
        }

        /**
         * @returns {ButtonAnimationStyle}
         */
        get exportButtonAnimationStyle() {
            return {
                transform: {
                    start: 'scale(1)',
                    mid: 'scale(1.01)',
                },
                zIndex: '984',
                borderRadius: '100%',
                offsetLeftEm: 0,
                offsetTopEm: 0,
            };
        }

        /**
         * @returns {ButtonAnimationStyle}
         */
        get signInButtonAnimationStyle() {
            return {
                transform: {
                    start: 'scale(1)',
                    mid: 'scale(1.3, 1.5)',
                },
                zIndex: '999',
                borderRadius: '2px',
                offsetLeftEm: 0,
                offsetTopEm: -0.05,
            };
        }

        /**
         * @param {HTMLElement|null} overlay
         */
        set currentOverlay(overlay) {
            this.#currentOverlay = overlay;
        }

        /**
         * @returns {HTMLElement|null}
         */
        get currentOverlay() {
            return this.#currentOverlay ?? null;
        }

        /**
         * @returns {ElementConfig|null}
         */
        get currentElementConfig() {
            return this.#currentElementConfig;
        }

        /**
         * @returns {Promise<void>}
         */
        get domLoaded() {
            return this.#domLoaded;
        }

        /**
         * Takes a path and returns the element and style to animate.
         * @param {string} path
         * @returns {Promise<ElementConfig | null>}
         */
        async getElementAndStyleFromPath(path) {
            if (path === '/') {
                const element = await this.findSettingsElement();
                return element != null
                    ? {
                          animationStyle: this.settingsButtonAnimationStyle,
                          element,
                          shouldTap: this.#settingsButtonSettings?.shouldAutotap ?? false,
                          shouldWatchForRemoval: false,
                      }
                    : null;
            } else if (path === '/options') {
                const element = await this.findExportElement();
                return element != null
                    ? {
                          animationStyle: this.exportButtonAnimationStyle,
                          element,
                          shouldTap: this.#exportButtonSettings?.shouldAutotap ?? false,
                          shouldWatchForRemoval: true,
                      }
                    : null;
            } else if (path === '/intro') {
                const element = await this.findSignInButton();
                return element != null
                    ? {
                          animationStyle: this.signInButtonAnimationStyle,
                          element,
                          shouldTap: this.#signInButtonSettings?.shouldAutotap ?? false,
                          shouldWatchForRemoval: false,
                      }
                    : null;
            } else {
                return null;
            }
        }

        /**
         * Removes the overlay if it exists.
         */
        removeOverlayIfNeeded() {
            if (this.currentOverlay != null) {
                this.currentOverlay.style.display = 'none';
                this.currentOverlay.remove();
                this.currentOverlay = null;
                document.removeEventListener('scroll', this);
            }
        }

        /**
         * Updates the position of the overlay based on the element to center on.
         */
        updateOverlayPosition() {
            if (this.currentOverlay != null && this.currentElementConfig?.animationStyle != null && this.elementToCenterOn != null) {
                const animations = this.currentOverlay.getAnimations();
                animations.forEach((animation) => animation.pause());
                const { top, left, width, height } = this.elementToCenterOn.getBoundingClientRect();
                this.currentOverlay.style.position = 'absolute';

                const { animationStyle } = this.currentElementConfig;
                const isRound = animationStyle.borderRadius === '100%';

                const widthOffset = isRound ? width / 2 : 0;
                const heightOffset = isRound ? height / 2 : 0;

                this.currentOverlay.style.top = `calc(${top}px + ${window.scrollY}px - ${widthOffset}px - 1px - ${animationStyle.offsetTopEm}em)`;
                this.currentOverlay.style.left = `calc(${left}px + ${window.scrollX}px - ${heightOffset}px - 1px - ${animationStyle.offsetLeftEm}em)`;

                // Ensure overlay is non-interactive
                this.currentOverlay.style.pointerEvents = 'none';
                animations.forEach((animation) => animation.play());
            }
        }

        /**
         * Creates an overlay element to animate, by adding a div to the body
         * and styling it based on the found element.
         * @param {HTMLElement|Element} mainElement
         * @param {any} style
         */
        createOverlayElement(mainElement, style) {
            this.removeOverlayIfNeeded();

            const overlay = document.createElement('div');
            overlay.setAttribute('id', OVERLAY_ID);

            if (this.elementToCenterOn != null) {
                this.currentOverlay = overlay;
                this.updateOverlayPosition();
                const mainElementRect = mainElement.getBoundingClientRect();
                overlay.style.width = `${mainElementRect.width}px`;
                overlay.style.height = `${mainElementRect.height}px`;
                overlay.style.zIndex = style.zIndex;

                // Ensure overlay is non-interactive
                overlay.style.pointerEvents = 'none';

                // insert in document.body
                document.body.appendChild(overlay);

                document.addEventListener('scroll', this, { passive: true });
            } else {
                this.currentOverlay = null;
            }
        }

        /**
         * Observes the removal of an element from the DOM.
         * @param {HTMLElement|Element} element
         * @param {any} onRemoveCallback
         */
        observeElementRemoval(element, onRemoveCallback) {
            // Set up the mutation observer
            const observer = new MutationObserver((mutations) => {
                mutations.forEach((mutation) => {
                    // Check if the element has been removed from its parent
                    if (mutation.type === 'childList' && !document.contains(element)) {
                        // Element has been removed
                        onRemoveCallback();
                        observer.disconnect(); // Stop observing
                    }
                });
            });

            // Start observing the parent node for child list changes
            observer.observe(document.body, { childList: true, subtree: true });
        }

        /**
         *
         * @param {HTMLElement|Element|SVGElement} element
         * @param {ButtonAnimationStyle} style
         */
        setElementToCenterOn(element, style) {
            const svgElement = element.parentNode?.querySelector('svg') ?? element.querySelector('svg');
            this.#elementToCenterOn = style.borderRadius === '100%' && svgElement != null ? svgElement : element;
        }

        /**
         * @returns {HTMLElement|Element|SVGElement|null}
         */
        get elementToCenterOn() {
            return this.#elementToCenterOn;
        }

        /**
         * Moves the element into view and animates it.
         * @param {HTMLElement|Element} element
         * @param {ButtonAnimationStyle} style
         */
        animateElement(element, style) {
            this.createOverlayElement(element, style);
            if (this.currentOverlay != null) {
                this.currentOverlay.scrollIntoView({
                    behavior: 'smooth',
                    block: 'center',
                    inline: 'center',
                }); // Scroll into view
                const keyframes = [
                    {
                        backgroundColor: BACKGROUND_COLOR_START,
                        offset: 0,
                        borderRadius: style.borderRadius,
                        border: `1px solid ${BACKGROUND_COLOR_START}`,
                        transform: style.transform.start,
                    }, // Start: 10% blue
                    {
                        backgroundColor: BACKGROUND_COLOR_END,
                        offset: 0.5,
                        borderRadius: style.borderRadius,
                        border: `1px solid ${BACKGROUND_COLOR_END}`,
                        transform: style.transform.mid,
                        transformOrigin: 'center',
                    }, // Middle: 25% blue
                    {
                        backgroundColor: BACKGROUND_COLOR_START,
                        offset: 1,
                        borderRadius: style.borderRadius,
                        border: `1px solid ${BACKGROUND_COLOR_START}`,
                        transform: style.transform.start,
                    }, // End: 10% blue
                ];

                // Define the animation options
                const options = {
                    duration: ANIMATION_DURATION_MS,
                    iterations: ANIMATION_ITERATIONS,
                };

                // Apply the animation to the element
                this.currentOverlay.animate(keyframes, options);
            }
        }

        autotapElement(element) {
            element.click();
        }

        /**
         * On passwords.google.com the export button is in a container that is quite ambiguious.
         * To solve for that we first try to find the container and then the button inside it.
         * If that fails, we look for the button based on it's label.
         * @returns {Promise<HTMLElement|Element|null>}
         */
        async findExportElement() {
            const findInContainer = () => {
                const exportButtonContainer = document.querySelector(this.exportButtonContainerSelector);
                return exportButtonContainer && exportButtonContainer.querySelectorAll('button')[1];
            };

            const findWithLabel = () => {
                return document.querySelector(this.exportButtonLabelTextSelector);
            };

            return await withExponentialBackoff(() => findInContainer() ?? findWithLabel());
        }

        /**
         * @returns {Promise<HTMLElement|Element|null>}
         */
        async findSettingsElement() {
            const fn = () => {
                const settingsButton = document.querySelector(this.settingsButtonSelector);
                return settingsButton;
            };
            return await withExponentialBackoff(fn);
        }

        /**
         * @returns {Promise<HTMLElement|Element|null>}
         */
        async findSignInButton() {
            return await withExponentialBackoff(() => document.querySelector(this.signinButtonSelector));
        }

        /**
         * @param {Event} event
         */
        handleEvent(event) {
            if (event.type === 'scroll') {
                requestAnimationFrame(() => this.updateOverlayPosition());
            }
        }

        /**
         * @param {ElementConfig|null} config
         */
        setCurrentElementConfig(config) {
            if (config != null) {
                this.#currentElementConfig = config;
                this.setElementToCenterOn(config.element, config.animationStyle);
            }
        }

        /**
         * Checks if the path is supported for animation.
         * @param {string} path
         * @returns {boolean}
         */
        isSupportedPath(path) {
            return [this.#exportButtonSettings?.path, this.#settingsButtonSettings?.path, this.#signInButtonSettings?.path].includes(path);
        }

        async handlePath(path) {
            this.removeOverlayIfNeeded();
            if (this.isSupportedPath(path)) {
                try {
                    this.setCurrentElementConfig(await this.getElementAndStyleFromPath(path));
                    await this.animateOrTapElement();
                } catch {
                    console.error('password-import: failed for path:', path);
                }
            }
        }

        /**
         * Based on the current element config, animates the element or taps it.
         * If the element should be watched for removal, it sets up a mutation observer.
         */
        async animateOrTapElement() {
            const { element, animationStyle, shouldTap, shouldWatchForRemoval } = this.currentElementConfig ?? {};
            if (element != null && animationStyle != null) {
                if (shouldTap) {
                    this.autotapElement(element);
                } else {
                    await this.domLoaded;
                    this.animateElement(element, animationStyle);
                }
                if (shouldWatchForRemoval) {
                    // Sometimes navigation events are not triggered, then we need to watch for removal
                    this.observeElementRemoval(element, () => {
                        this.removeOverlayIfNeeded();
                    });
                }
            }
        }

        /**
         * @returns {string}
         */
        get exportButtonContainerSelector() {
            return this.#exportButtonSettings?.selectors?.join(',');
        }

        /**
         * @returns {string}
         */
        get exportButtonLabelTextSelector() {
            return this.#exportButtonSettings?.labelTexts.map((text) => `button[aria-label="${text}"]`).join(',');
        }

        /**
         * @returns {string}
         */
        get signinLabelTextSelector() {
            return this.#signInButtonSettings?.labelTexts.map((text) => `a[aria-label="${text}"]:not([target="_top"])`).join(',');
        }

        /**
         * @returns {string}
         */
        get signinButtonSelector() {
            return `${this.#signInButtonSettings?.selectors?.join(',')}, ${this.signinLabelTextSelector}`;
        }

        /**
         * @returns {string}
         */
        get settingsLabelTextSelector() {
            return this.#settingsButtonSettings?.labelTexts.map((text) => `a[aria-label="${text}"]`).join(',');
        }

        /**
         * @returns {string}
         */
        get settingsButtonSelector() {
            return `${this.#settingsButtonSettings?.selectors?.join(',')}, ${this.settingsLabelTextSelector}`;
        }

        setButtonSettings() {
            this.#exportButtonSettings = this.getFeatureSetting('exportButton');
            this.#signInButtonSettings = this.getFeatureSetting('signInButton');
            this.#settingsButtonSettings = this.getFeatureSetting('settingsButton');
        }

        init() {
            this.setButtonSettings();

            const handlePath = this.handlePath.bind(this);
            const historyMethodProxy = new DDGProxy(this, History.prototype, 'pushState', {
                async apply(target, thisArg, args) {
                    const path = args[1] === '' ? args[2].split('?')[0] : args[1];
                    await handlePath(path);
                    return DDGReflect.apply(target, thisArg, args);
                },
            });
            historyMethodProxy.overload();
            // listen for popstate events in order to run on back/forward navigations
            window.addEventListener('popstate', async () => {
                const path = window.location.pathname;
                await handlePath(path);
            });

            this.#domLoaded = new Promise((resolve) => {
                if (document.readyState !== 'loading') {
                    // @ts-expect-error - caller doesn't expect a value here
                    resolve();
                    return;
                }

                document.addEventListener(
                    'DOMContentLoaded',
                    async () => {
                        // @ts-expect-error - caller doesn't expect a value here
                        resolve();
                        const path = window.location.pathname;
                        await handlePath(path);
                    },
                    { once: true },
                );
            });
        }
    }

    var platformFeatures = {
        ddg_feature_autofillPasswordImport: AutofillPasswordImport
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
     */

    /**
     * @param {LoadArgs} args
     */
    function load(args) {
        const mark = performanceMonitor.mark('load');
        if (!isHTMLDocument) {
            return;
        }

        const featureNames = platformSupport["android-autofill-password-import"] ;

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
        });

        init(processedConfig);
    }

    initCode();

})();
