/*! © DuckDuckGo ContentScopeScripts protections https://github.com/duckduckgo/content-scope-scripts/ */
(function () {
    'use strict';

    /* eslint-disable no-redeclare */
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
    let messageSecret;

    // save a reference to original CustomEvent amd dispatchEvent so they can't be overriden to forge messages
    const OriginalCustomEvent = typeof CustomEvent === 'undefined' ? null : CustomEvent;
    const originalWindowDispatchEvent = typeof window === 'undefined' ? null : window.dispatchEvent.bind(window);
    function registerMessageSecret(secret) {
        messageSecret = secret;
    }

    const exemptionLists = {};

    function initStringExemptionLists(args) {
        const { stringExemptionLists } = args;
        args.debug;
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

    /** @typedef {baseFeatures[number]|otherFeatures[number]} FeatureName */
    /** @type {Record<string, FeatureName[]>} */
    const platformSupport = {
        'android-broker-protection': ['brokerProtection']};

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

    // From https://github.com/carltonnorthern/nicknames
    const names = {
        /** @type {null | Record<string, string[]>} */
        _memo: null,
        /**
         * This is wrapped in a way to prevent initialization in the top-level context
         * when hoisted by bundlers
         * @return {Record<string, string[]>}
         */
        get nicknames() {
            if (this._memo !== null) return this._memo;
            this._memo = {
                aaron: ['erin', 'ronnie', 'ron'],
                abbigail: ['nabby', 'abby', 'gail', 'abbe', 'abbi', 'abbey', 'abbie'],
                abbigale: ['nabby', 'abby', 'gail', 'abbe', 'abbi', 'abbey', 'abbie'],
                abednego: ['bedney'],
                abel: ['ebbie', 'ab', 'abe', 'eb'],
                abiel: ['ab'],
                abigail: ['nabby', 'abby', 'gail', 'abbe', 'abbi', 'abbey', 'abbie'],
                abigale: ['nabby', 'abby', 'gail', 'abbe', 'abbi', 'abbey', 'abbie'],
                abijah: ['ab', 'bige'],
                abner: ['ab'],
                abraham: ['ab', 'abe'],
                abram: ['ab', 'abe'],
                absalom: ['app', 'ab', 'abbie'],
                ada: ['addy', 'adie'],
                adaline: ['delia', 'lena', 'dell', 'addy', 'ada', 'adie'],
                addison: ['addie', 'addy'],
                adela: ['della', 'adie'],
                adelaide: ['heidi', 'adele', 'dell', 'addy', 'della', 'adie'],
                adelbert: ['del', 'albert', 'delbert', 'bert'],
                adele: ['addy', 'dell'],
                adeline: ['delia', 'lena', 'dell', 'addy', 'ada'],
                adelphia: ['philly', 'delphia', 'adele', 'dell', 'addy'],
                adena: ['dena', 'dina', 'deena', 'adina'],
                adolphus: ['dolph', 'ado', 'adolph'],
                adrian: ['rian'],
                adriane: ['riane'],
                adrienne: ['addie', 'rienne', 'enne'],
                agatha: ['aggy', 'aga'],
                agnes: ['inez', 'aggy', 'nessa'],
                aileen: ['lena', 'allie'],
                alan: ['al'],
                alanson: ['al', 'lanson'],
                alastair: ['al'],
                alazama: ['ali'],
                albert: ['bert', 'al'],
                alberta: ['bert', 'allie', 'bertie'],
                aldo: ['al'],
                aldrich: ['riche', 'rich', 'richie'],
                aleksandr: ['alex', 'alek'],
                aleva: ['levy', 'leve'],
                alex: ['al'],
                alexander: ['alex', 'al', 'sandy', 'alec'],
                alexandra: ['alex', 'sandy', 'alla', 'sandra'],
                alexandria: ['drina', 'alexander', 'alla', 'sandra', 'alex'],
                alexis: ['lexi', 'alex'],
                alfonse: ['al'],
                alfred: ['freddy', 'al', 'fred'],
                alfreda: ['freddy', 'alfy', 'freda', 'frieda'],
                algernon: ['algy'],
                alice: ['lisa', 'elsie', 'allie'],
                alicia: ['lisa', 'elsie', 'allie'],
                aline: ['adeline'],
                alison: ['ali', 'allie'],
                alixandra: ['alix'],
                allan: ['al', 'alan', 'allen'],
                allen: ['al', 'allan', 'alan'],
                allisandra: ['ali', 'ally', 'allie'],
                allison: ['ali', 'ally', 'allie'],
                allyson: ['ali', 'ally', 'allie'],
                allyssa: ['ali', 'ally', 'allie'],
                almena: ['mena', 'ali', 'ally', 'allie'],
                almina: ['minnie'],
                almira: ['myra'],
                alonzo: ['lon', 'al', 'lonzo'],
                alphinias: ['alphus'],
                althea: ['ally'],
                alverta: ['virdie', 'vert'],
                alyssa: ['lissia', 'al', 'ally'],
                alzada: ['zada'],
                amanda: ['mandy', 'manda'],
                ambrose: ['brose'],
                amelia: ['amy', 'mel', 'millie', 'emily'],
                amos: ['moses'],
                anastasia: ['ana', 'stacy'],
                anderson: ['andy'],
                andre: ['drea'],
                andrea: ['drea', 'rea', 'andrew', 'andi', 'andy'],
                andrew: ['andy', 'drew'],
                andriane: ['ada', 'adri', 'rienne'],
                angela: ['angel', 'angie'],
                angelica: ['angie', 'angel', 'angelika', 'angelique'],
                angelina: ['angel', 'angie', 'lina'],
                ann: ['annie', 'nan'],
                anna: ['anne', 'ann', 'annie', 'nan'],
                anne: ['annie', 'ann', 'nan'],
                annette: ['anna', 'nettie'],
                annie: ['ann', 'anna'],
                anselm: ['ansel', 'selma', 'anse', 'ance'],
                anthony: ['ant', 'tony'],
                antoinette: ['tony', 'netta', 'ann'],
                antonia: ['tony', 'netta', 'ann'],
                antonio: ['ant', 'tony'],
                appoline: ['appy', 'appie'],
                aquilla: ['quil', 'quillie'],
                ara: ['belle', 'arry'],
                arabella: ['ara', 'bella', 'arry', 'belle'],
                arabelle: ['ara', 'bella', 'arry', 'belle'],
                araminta: ['armida', 'middie', 'ruminta', 'minty'],
                archibald: ['archie'],
                archilles: ['kill', 'killis'],
                ariadne: ['arie', 'ari'],
                arielle: ['arie'],
                aristotle: ['telly'],
                arizona: ['onie', 'ona'],
                arlene: ['arly', 'lena'],
                armanda: ['mandy'],
                armena: ['mena', 'arry'],
                armilda: ['milly'],
                arminda: ['mindie'],
                arminta: ['minite', 'minnie'],
                arnold: ['arnie'],
                aron: ['erin', 'ronnie', 'ron'],
                artelepsa: ['epsey'],
                artemus: ['art'],
                arthur: ['art'],
                arthusa: ['thursa'],
                arzada: ['zaddi'],
                asahel: ['asa'],
                asaph: ['asa'],
                asenath: ['sene', 'assene', 'natty'],
                ashley: ['ash', 'leah', 'lee', 'ashly'],
                aubrey: ['bree'],
                audrey: ['dee', 'audree'],
                august: ['gus'],
                augusta: ['tina', 'aggy', 'gatsy', 'gussie'],
                augustina: ['tina', 'aggy', 'gatsy', 'gussie'],
                augustine: ['gus', 'austin', 'august'],
                augustus: ['gus', 'austin', 'august'],
                aurelia: ['ree', 'rilly', 'orilla', 'aurilla', 'ora'],
                avarilla: ['rilla'],
                azariah: ['riah', 'aze'],
                bab: ['barby'],
                babs: ['barby', 'barbara', 'bab'],
                barbara: ['barby', 'babs', 'bab', 'bobbie', 'barbie'],
                barbery: ['barbara'],
                barbie: ['barbara'],
                barnabas: ['barney'],
                barney: ['barnabas'],
                bart: ['bartholomew'],
                bartholomew: ['bartel', 'bat', 'meus', 'bart', 'mees'],
                barticus: ['bart'],
                bazaleel: ['basil'],
                bea: ['beatrice'],
                beatrice: ['bea', 'trisha', 'trixie', 'trix'],
                becca: ['beck'],
                beck: ['becky'],
                bedelia: ['delia', 'bridgit'],
                belinda: ['belle', 'linda'],
                bella: ['belle', 'arabella', 'isabella'],
                benedict: ['bennie', 'ben'],
                benjamin: ['benjy', 'jamie', 'bennie', 'ben', 'benny'],
                benjy: ['benjamin'],
                bernard: ['barney', 'bernie', 'berney', 'berny'],
                berney: ['bernie'],
                bert: ['bertie', 'bob', 'bobby'],
                bertha: ['bert', 'birdie', 'bertie'],
                bertram: ['bert'],
                bess: ['bessie'],
                beth: ['betsy', 'betty', 'elizabeth'],
                bethena: ['beth', 'thaney'],
                beverly: ['bev'],
                bezaleel: ['zeely'],
                biddie: ['biddy'],
                bill: ['william', 'billy', 'robert', 'willie', 'fred'],
                billy: ['william', 'robert', 'fred'],
                blanche: ['bea'],
                bob: ['rob', 'robert'],
                bobby: ['rob', 'bob'],
                boetius: ['bo'],
                brad: ['bradford', 'ford'],
                bradford: ['ford', 'brad'],
                bradley: ['brad'],
                brady: ['brody'],
                breanna: ['bree', 'bri'],
                breeanna: ['bree'],
                brenda: ['brandy'],
                brian: ['bryan', 'bryant'],
                brianna: ['bri'],
                bridget: ['bridie', 'biddy', 'bridgie', 'biddie'],
                brittany: ['britt', 'brittnie'],
                brittney: ['britt', 'brittnie'],
                broderick: ['ricky', 'brody', 'brady', 'rick', 'rod'],
                bryanna: ['brianna', 'bri', 'briana', 'ana', 'anna'],
                caitlin: ['cait', 'caity'],
                caitlyn: ['cait', 'caity'],
                caldonia: ['calliedona'],
                caleb: ['cal'],
                california: ['callie'],
                calista: ['kissy'],
                calpurnia: ['cally'],
                calvin: ['vin', 'vinny', 'cal'],
                cameron: ['ron', 'cam', 'ronny'],
                camile: ['cammie'],
                camille: ['millie', 'cammie'],
                campbell: ['cam'],
                candace: ['candy', 'dacey'],
                carla: ['karla', 'carly'],
                carlotta: ['lottie'],
                carlton: ['carl'],
                carmellia: ['mellia'],
                carmelo: ['melo'],
                carmon: ['charm', 'cammie', 'carm'],
                carol: ['lynn', 'carrie', 'carolann', 'cassie', 'caroline', 'carole', 'carri', 'kari', 'kara'],
                carolann: ['carol', 'carole'],
                caroline: ['lynn', 'carol', 'carrie', 'cassie', 'carole'],
                carolyn: ['lynn', 'carrie', 'cassie'],
                carrie: ['cassie'],
                carthaette: ['etta', 'etty'],
                casey: ['k.c.'],
                casper: ['jasper'],
                cassandra: ['sandy', 'cassie', 'sandra'],
                cassidy: ['cassie', 'cass'],
                caswell: ['cass'],
                catherine: ['kathy', 'katy', 'lena', 'kittie', 'kit', 'trina', 'cathy', 'kay', 'cassie', 'casey'],
                cathleen: ['kathy', 'katy', 'lena', 'kittie', 'kit', 'trina', 'cathy', 'kay', 'cassie', 'casey'],
                cathy: ['kathy', 'cathleen', 'catherine'],
                cecilia: ['cissy', 'celia'],
                cedric: ['ced', 'rick', 'ricky'],
                celeste: ['lessie', 'celia'],
                celinda: ['linda', 'lynn', 'lindy'],
                charity: ['chat'],
                charles: ['charlie', 'chuck', 'carl', 'chick'],
                charlie: ['charles', 'chuck'],
                charlotte: ['char', 'sherry', 'lottie', 'lotta'],
                chauncey: ['chan'],
                chelsey: ['chelsie'],
                cheryl: ['cher'],
                chesley: ['chet'],
                chester: ['chet'],
                chet: ['chester'],
                chick: ['charlotte', 'caroline', 'chuck'],
                chloe: ['clo'],
                chris: ['kris'],
                christa: ['chris'],
                christian: ['chris', 'kit'],
                christiana: ['kris', 'kristy', 'ann', 'tina', 'christy', 'chris', 'crissy'],
                christiano: ['chris'],
                christina: ['kris', 'kristy', 'tina', 'christy', 'chris', 'crissy', 'chrissy'],
                christine: ['kris', 'kristy', 'chrissy', 'tina', 'chris', 'crissy', 'christy'],
                christoph: ['chris'],
                christopher: ['chris', 'kit'],
                christy: ['crissy'],
                cicely: ['cilla'],
                cinderella: ['arilla', 'rella', 'cindy', 'rilla'],
                cindy: ['cinderella'],
                claire: ['clair', 'clare', 'clara'],
                clara: ['clarissa'],
                clare: ['clara'],
                clarence: ['clare', 'clair'],
                clarinda: ['clara'],
                clarissa: ['cissy', 'clara'],
                claudia: ['claud'],
                cleatus: ['cleat'],
                clement: ['clem'],
                clementine: ['clement', 'clem'],
                cliff: ['clifford'],
                clifford: ['ford', 'cliff'],
                clifton: ['tony', 'cliff'],
                cole: ['colie'],
                columbus: ['clum'],
                con: ['conny'],
                conrad: ['conny', 'con'],
                constance: ['connie'],
                cordelia: ['cordy', 'delia'],
                corey: ['coco', 'cordy', 'ree'],
                corinne: ['cora', 'ora'],
                cornelia: ['nelly', 'cornie', 'nelia', 'corny', 'nelle'],
                cornelius: ['conny', 'niel', 'corny', 'con', 'neil'],
                cory: ['coco', 'cordy', 'ree'],
                courtney: ['curt', 'court'],
                crystal: ['chris', 'tal', 'stal', 'crys'],
                curtis: ['curt'],
                cynthia: ['cintha', 'cindy'],
                cyrenius: ['swene', 'cy', 'serene', 'renius', 'cene'],
                cyrus: ['cy'],
                dahl: ['dal'],
                dalton: ['dahl', 'dal'],
                daniel: ['dan', 'danny', 'dann'],
                danielle: ['ellie', 'dani'],
                danny: ['daniel'],
                daphne: ['daph', 'daphie'],
                darlene: ['lena', 'darry'],
                david: ['dave', 'day', 'davey'],
                daycia: ['daisha', 'dacia'],
                deanne: ['ann', 'dee'],
                debbie: ['deb', 'debra', 'deborah', 'debby'],
                debby: ['deb'],
                debora: ['deb', 'debbie', 'debby'],
                deborah: ['deb', 'debbie', 'debby'],
                debra: ['deb', 'debbie'],
                deidre: ['deedee'],
                delbert: ['bert', 'del'],
                delia: ['fidelia', 'cordelia', 'delius'],
                delilah: ['lil', 'lila', 'dell', 'della'],
                deliverance: ['delly', 'dilly', 'della'],
                della: ['adela', 'delilah', 'adelaide', 'dell'],
                delores: ['lolly', 'lola', 'della', 'dee', 'dell'],
                delpha: ['philadelphia'],
                delphine: ['delphi', 'del', 'delf'],
                demaris: ['dea', 'maris', 'mary'],
                demerias: ['dea', 'maris', 'mary'],
                democrates: ['mock'],
                dennis: ['denny', 'dennie'],
                dennison: ['denny', 'dennis'],
                derek: ['derrek', 'rick', 'ricky'],
                derick: ['rick', 'ricky'],
                derrick: ['ricky', 'eric', 'rick'],
                deuteronomy: ['duty'],
                diana: ['dicey', 'didi', 'di'],
                diane: ['dicey', 'didi', 'di', 'dianne', 'dian'],
                dicey: ['dicie'],
                dick: ['rick', 'richard'],
                dickson: ['dick'],
                domenic: ['dom', 'nic'],
                dominic: ['dom', 'nic'],
                dominick: ['dom', 'nick', 'nicky'],
                dominico: ['dom'],
                donald: ['dony', 'donnie', 'don', 'donny'],
                donato: ['don'],
                donna: ['dona'],
                donovan: ['dony', 'donnie', 'don', 'donny'],
                dorcus: ['darkey'],
                dorinda: ['dorothea', 'dora'],
                doris: ['dora'],
                dorothea: ['doda', 'dora'],
                dorothy: ['dortha', 'dolly', 'dot', 'dotty', 'dora', 'dottie'],
                dotha: ['dotty'],
                dotty: ['dot'],
                douglas: ['doug'],
                drusilla: ['silla'],
                duncan: ['dunk'],
                earnest: ['ernestine', 'ernie'],
                ebbie: ['eb'],
                ebenezer: ['ebbie', 'eben', 'eb'],
                eddie: ['ed'],
                eddy: ['ed'],
                edgar: ['ed', 'eddie', 'eddy'],
                edith: ['edie', 'edye'],
                edmond: ['ed', 'eddie', 'eddy'],
                edmund: ['ed', 'eddie', 'ted', 'eddy', 'ned'],
                edna: ['edny'],
                eduardo: ['ed', 'eddie', 'eddy'],
                edward: ['teddy', 'ed', 'ned', 'ted', 'eddy', 'eddie'],
                edwin: ['ed', 'eddie', 'win', 'eddy', 'ned'],
                edwina: ['edwin'],
                edyth: ['edie', 'edye'],
                edythe: ['edie', 'edye'],
                egbert: ['bert', 'burt'],
                eighta: ['athy'],
                eileen: ['helen'],
                elaine: ['lainie', 'helen'],
                elbert: ['albert', 'bert'],
                elbertson: ['elbert', 'bert'],
                eldora: ['dora'],
                eleanor: ['lanna', 'nora', 'nelly', 'ellie', 'elaine', 'ellen', 'lenora'],
                eleazer: ['lazar'],
                elena: ['helen'],
                elias: ['eli', 'lee', 'lias'],
                elijah: ['lige', 'eli'],
                eliphalel: ['life'],
                eliphalet: ['left'],
                elisa: ['lisa'],
                elisha: ['lish', 'eli'],
                eliza: ['elizabeth'],
                elizabeth: ['libby', 'lisa', 'lib', 'lizzy', 'lizzie', 'eliza', 'betsy', 'liza', 'betty', 'bessie', 'bess', 'beth', 'liz'],
                ella: ['ellen', 'el'],
                ellen: ['nellie', 'nell', 'helen'],
                ellender: ['nellie', 'ellen', 'helen'],
                ellie: ['elly'],
                ellswood: ['elsey'],
                elminie: ['minnie'],
                elmira: ['ellie', 'elly', 'mira'],
                elnora: ['nora'],
                eloise: ['heloise', 'louise'],
                elouise: ['louise'],
                elsie: ['elsey'],
                elswood: ['elsey'],
                elvira: ['elvie'],
                elwood: ['woody'],
                elysia: ['lisa', 'lissa'],
                elze: ['elsey'],
                emanuel: ['manuel', 'manny'],
                emeline: ['em', 'emmy', 'emma', 'milly', 'emily'],
                emil: ['emily', 'em'],
                emily: ['emmy', 'millie', 'emma', 'mel', 'em'],
                emma: ['emmy', 'em'],
                epaphroditius: ['dite', 'ditus', 'eppa', 'dyche', 'dyce'],
                ephraim: ['eph'],
                erasmus: ['raze', 'rasmus'],
                eric: ['rick', 'ricky'],
                ernest: ['ernie'],
                ernestine: ['teeny', 'ernest', 'tina', 'erna'],
                erwin: ['irwin'],
                eseneth: ['senie'],
                essy: ['es'],
                estella: ['essy', 'stella'],
                estelle: ['essy', 'stella'],
                esther: ['hester', 'essie'],
                eudicy: ['dicey'],
                eudora: ['dora'],
                eudoris: ['dossie', 'dosie'],
                eugene: ['gene'],
                eunice: ['nicie'],
                euphemia: ['effie', 'effy'],
                eurydice: ['dicey'],
                eustacia: ['stacia', 'stacy'],
                eva: ['eve'],
                evaline: ['eva', 'lena', 'eve'],
                evangeline: ['ev', 'evan', 'vangie'],
                evelyn: ['evelina', 'ev', 'eve'],
                experience: ['exie'],
                ezekiel: ['zeke', 'ez'],
                ezideen: ['ez'],
                ezra: ['ez'],
                faith: ['fay'],
                fallon: ['falon', 'fal', 'fall', 'fallie', 'fally', 'falcon', 'lon', 'lonnie'],
                felicia: ['fel', 'felix', 'feli'],
                felicity: ['flick', 'tick'],
                feltie: ['felty'],
                ferdinand: ['freddie', 'freddy', 'ferdie', 'fred'],
                ferdinando: ['nando', 'ferdie', 'fred'],
                fidelia: ['delia'],
                fionna: ['fiona'],
                flora: ['florence'],
                florence: ['flossy', 'flora', 'flo'],
                floyd: ['lloyd'],
                fran: ['frannie'],
                frances: ['sis', 'cissy', 'frankie', 'franniey', 'fran', 'francie', 'frannie', 'fanny', 'franny'],
                francie: ['francine'],
                francine: ['franniey', 'fran', 'frannie', 'francie', 'franny'],
                francis: ['fran', 'frankie', 'frank'],
                frankie: ['frank', 'francis'],
                franklin: ['fran', 'frank'],
                franklind: ['fran', 'frank'],
                freda: ['frieda'],
                frederica: ['frederick', 'freddy', 'erika', 'erica', 'rickey'],
                frederick: ['freddie', 'freddy', 'fritz', 'fred', 'erick', 'ricky', 'derick', 'rick'],
                fredericka: ['freddy', 'ricka', 'freda', 'frieda', 'ericka', 'rickey'],
                frieda: ['freddie', 'freddy', 'fred'],
                gabriel: ['gabe', 'gabby'],
                gabriella: ['ella', 'gabby'],
                gabrielle: ['ella', 'gabby'],
                gareth: ['gary', 'gare'],
                garrett: ['gare', 'gary', 'garry', 'rhett', 'garratt', 'garret', 'barrett', 'jerry'],
                garrick: ['garri'],
                genevieve: ['jean', 'eve', 'jenny'],
                geoffrey: ['geoff', 'jeff'],
                george: ['georgie'],
                georgiana: ['georgia'],
                georgine: ['george'],
                gerald: ['gerry', 'jerry'],
                geraldine: ['gerry', 'gerrie', 'jerry', 'dina', 'gerri'],
                gerhardt: ['gay'],
                gertie: ['gertrude', 'gert'],
                gertrude: ['gertie', 'gert', 'trudy'],
                gilbert: ['bert', 'gil', 'wilber'],
                giovanni: ['gio'],
                glenn: ['glen'],
                gloria: ['glory'],
                governor: ['govie'],
                greenberry: ['green', 'berry'],
                greggory: ['gregg'],
                gregory: ['greg'],
                gretchen: ['margaret'],
                griselda: ['grissel'],
                gum: ['monty'],
                gus: ['gussie'],
                gustavus: ['gus', 'gussie'],
                gwen: ['wendy'],
                gwendolyn: ['gwen', 'wendy'],
                hailey: ['hayley', 'haylee'],
                hamilton: ['ham'],
                hannah: ['nan', 'nanny', 'anna'],
                harold: ['hal', 'harry', 'hap', 'haps'],
                harriet: ['hattie'],
                harrison: ['harry', 'hap', 'haps'],
                harry: ['harold', 'henry', 'hap', 'haps'],
                haseltine: ['hassie'],
                haylee: ['hayley', 'hailey'],
                hayley: ['hailey', 'haylee'],
                heather: ['hetty'],
                helen: ['lena', 'ella', 'ellen', 'ellie'],
                helena: ['eileen', 'lena', 'nell', 'nellie', 'eleanor', 'elaine', 'ellen', 'aileen'],
                helene: ['lena', 'ella', 'ellen', 'ellie'],
                heloise: ['lois', 'eloise', 'elouise'],
                henrietta: ['hank', 'etta', 'etty', 'retta', 'nettie', 'henny'],
                henry: ['hank', 'hal', 'harry', 'hap', 'haps'],
                hephsibah: ['hipsie'],
                hepsibah: ['hipsie'],
                herbert: ['bert', 'herb'],
                herman: ['harman', 'dutch'],
                hermione: ['hermie'],
                hester: ['hessy', 'esther', 'hetty'],
                hezekiah: ['hy', 'hez', 'kiah'],
                hillary: ['hilary'],
                hipsbibah: ['hipsie'],
                hiram: ['hy'],
                honora: ['honey', 'nora', 'norry', 'norah'],
                hopkins: ['hopp', 'hop'],
                horace: ['horry'],
                hortense: ['harty', 'tensey'],
                hosea: ['hosey', 'hosie'],
                howard: ['hal', 'howie'],
                hubert: ['bert', 'hugh', 'hub'],
                ian: ['john'],
                ignatius: ['natius', 'iggy', 'nate', 'nace'],
                ignatzio: ['naz', 'iggy', 'nace'],
                immanuel: ['manuel', 'emmanuel'],
                india: ['indie', 'indy'],
                inez: ['agnes'],
                iona: ['onnie'],
                irene: ['rena'],
                irvin: ['irving'],
                irving: ['irv'],
                irwin: ['erwin'],
                isaac: ['ike', 'zeke'],
                isabel: ['tibbie', 'bell', 'nib', 'belle', 'bella', 'nibby', 'ib', 'issy'],
                isabella: ['tibbie', 'nib', 'belle', 'bella', 'nibby', 'ib', 'issy'],
                isabelle: ['tibbie', 'nib', 'belle', 'bella', 'nibby', 'ib', 'issy'],
                isadora: ['issy', 'dora'],
                isadore: ['izzy'],
                isaiah: ['zadie', 'zay'],
                isidore: ['izzy'],
                iva: ['ivy'],
                ivan: ['john'],
                jackson: ['jack'],
                jacob: ['jaap', 'jake', 'jay'],
                jacobus: ['jacob'],
                jacqueline: ['jackie', 'jack', 'jacqui'],
                jahoda: ['hody', 'hodie', 'hoda'],
                jakob: ['jake'],
                jalen: ['jay', 'jaye', 'len', 'lenny', 'lennie', 'jaylin', 'alen', 'al', 'haylen', 'jaelin', 'jaelyn', 'jailyn', 'jaylyn'],
                james: ['jimmy', 'jim', 'jamie', 'jimmie', 'jem'],
                jamey: ['james', 'jamie'],
                jamie: ['james'],
                jane: ['janie', 'jessie', 'jean', 'jennie'],
                janet: ['jan', 'jessie'],
                janice: ['jan'],
                jannett: ['nettie'],
                jasper: ['jap', 'casper'],
                jayme: ['jay'],
                jean: ['jane', 'jeannie'],
                jeanette: ['jessie', 'jean', 'janet', 'nettie'],
                jeanne: ['jane', 'jeannie'],
                jebadiah: ['jeb'],
                jedediah: ['dyer', 'jed', 'diah'],
                jedidiah: ['dyer', 'jed', 'diah'],
                jefferey: ['jeff'],
                jefferson: ['sonny', 'jeff'],
                jeffery: ['jeff'],
                jeffrey: ['geoff', 'jeff'],
                jehiel: ['hiel'],
                jehu: ['hugh', 'gee'],
                jemima: ['mima'],
                jennet: ['jessie', 'jenny', 'jenn'],
                jennifer: ['jennie', 'jenn', 'jen', 'jenny', 'jenni'],
                jeremiah: ['jereme', 'jerry'],
                jeremy: ['jezza', 'jez'],
                jerita: ['rita'],
                jerry: ['jereme', 'geraldine', 'gerry', 'geri'],
                jessica: ['jessie', 'jess'],
                jessie: ['jane', 'jess', 'janet'],
                jillian: ['jill'],
                jim: ['jimmie'],
                jincy: ['jane'],
                jinsy: ['jane'],
                joan: ['jo', 'nonie'],
                joann: ['jo'],
                joanna: ['hannah', 'jody', 'jo', 'joan', 'jodi'],
                joanne: ['jo'],
                jody: ['jo'],
                joe: ['joey'],
                johann: ['john'],
                johanna: ['jo'],
                johannah: ['hannah', 'jody', 'joan', 'nonie', 'jo'],
                johannes: ['jonathan', 'john', 'johnny'],
                john: ['jon', 'johnny', 'jonny', 'jonnie', 'jack', 'jock', 'ian'],
                johnathan: ['johnathon', 'jonathan', 'jonathon', 'jon', 'jonny', 'john', 'johny', 'jonnie', 'nathan'],
                johnathon: ['johnathan', 'jonathon', 'jonathan', 'jon', 'jonny', 'john', 'johny', 'jonnie'],
                jon: ['john', 'johnny', 'jonny', 'jonnie'],
                jonathan: ['johnathan', 'johnathon', 'jonathon', 'jon', 'jonny', 'john', 'johny', 'jonnie', 'nathan'],
                jonathon: ['johnathan', 'johnathon', 'jonathan', 'jon', 'jonny', 'john', 'johny', 'jonnie'],
                joseph: ['jody', 'jos', 'joe', 'joey'],
                josephine: ['fina', 'jody', 'jo', 'josey', 'joey', 'josie'],
                josetta: ['jettie'],
                josey: ['josophine'],
                joshua: ['jos', 'josh', 'joe'],
                josiah: ['jos'],
                josophine: ['jo', 'joey', 'josey'],
                joyce: ['joy'],
                juanita: ['nita', 'nettie'],
                judah: ['juder', 'jude'],
                judith: ['judie', 'juda', 'judy', 'judi', 'jude'],
                judson: ['sonny', 'jud'],
                judy: ['judith'],
                julia: ['julie', 'jill'],
                julian: ['jule'],
                julias: ['jule'],
                julie: ['julia', 'jule'],
                june: ['junius'],
                junior: ['junie', 'june', 'jr'],
                justin: ['justus', 'justina', 'juston'],
                kaitlin: ['kait', 'kaitie'],
                kaitlyn: ['kait', 'kaitie'],
                kaitlynn: ['kait', 'kaitie'],
                kalli: ['kali', 'cali'],
                kameron: ['kam'],
                karla: ['carla', 'carly'],
                kasey: ['k.c.'],
                katarina: ['catherine', 'tina'],
                kate: ['kay'],
                katelin: ['kay', 'kate', 'kaye'],
                katelyn: ['kay', 'kate', 'kaye'],
                katherine: ['kathy', 'katy', 'lena', 'kittie', 'kaye', 'kit', 'trina', 'cathy', 'kay', 'kate', 'cassie'],
                kathleen: ['kathy', 'katy', 'lena', 'kittie', 'kit', 'trina', 'cathy', 'kay', 'cassie'],
                kathryn: ['kathy', 'katie', 'kate'],
                katia: ['kate', 'katie'],
                katy: ['kathy', 'katie', 'kate'],
                kayla: ['kay'],
                kelley: ['kellie', 'kelli', 'kelly'],
                kendall: ['ken', 'kenny'],
                kendra: ['kenj', 'kenji', 'kay', 'kenny'],
                kendrick: ['ken', 'kenny'],
                kendrik: ['ken', 'kenny'],
                kenneth: ['ken', 'kenny', 'kendrick'],
                kenny: ['ken', 'kenneth'],
                kent: ['ken', 'kenny', 'kendrick'],
                kerry: ['kerri'],
                kevin: ['kev'],
                keziah: ['kizza', 'kizzie'],
                kimberley: ['kim', 'kimberly', 'kimberli'],
                kimberly: ['kim', 'kimberli', 'kimberley'],
                kingsley: ['king'],
                kingston: ['king'],
                kit: ['kittie'],
                kris: ['chris'],
                kristel: ['kris'],
                kristen: ['chris'],
                kristin: ['chris'],
                kristine: ['kris', 'kristy', 'tina', 'christy', 'chris', 'crissy'],
                kristopher: ['chris', 'kris'],
                kristy: ['chris'],
                kymberly: ['kym'],
                lafayette: ['laffie', 'fate'],
                lamont: ['monty'],
                laodicia: ['dicy', 'cenia'],
                larry: ['laurence', 'lawrence'],
                latisha: ['tish', 'tisha'],
                laurel: ['laurie'],
                lauren: ['ren', 'laurie'],
                laurence: ['lorry', 'larry', 'lon', 'lonny', 'lorne'],
                laurinda: ['laura', 'lawrence'],
                lauryn: ['laurie'],
                laveda: ['veda'],
                laverne: ['vernon', 'verna'],
                lavina: ['vina', 'viney', 'ina'],
                lavinia: ['vina', 'viney', 'ina'],
                lavonia: ['vina', 'vonnie', 'wyncha', 'viney'],
                lavonne: ['von'],
                lawrence: ['lorry', 'larry', 'lon', 'lonny', 'lorne', 'lawrie'],
                leanne: ['lea', 'annie'],
                lecurgus: ['curg'],
                leilani: ['lani'],
                lemuel: ['lem'],
                lena: ['ellen'],
                lenora: ['nora', 'lee'],
                leo: ['leon'],
                leonard: ['lineau', 'leo', 'leon', 'len', 'lenny'],
                leonidas: ['lee', 'leon'],
                leonora: ['nora', 'nell', 'nellie'],
                leonore: ['nora', 'honor', 'elenor'],
                leroy: ['roy', 'lee', 'l.r.'],
                lesley: ['les'],
                leslie: ['les'],
                lester: ['les'],
                letitia: ['tish', 'titia', 'lettice', 'lettie'],
                levi: ['lee'],
                levicy: ['vicy'],
                levone: ['von'],
                lib: ['libby'],
                lidia: ['lyddy'],
                lil: ['lilly', 'lily'],
                lillah: ['lil', 'lilly', 'lily', 'lolly'],
                lillian: ['lil', 'lilly', 'lolly'],
                lilly: ['lily', 'lil'],
                lincoln: ['link'],
                linda: ['lindy', 'lynn'],
                lindsay: ['lindsey', 'lindsie', 'lindsy'],
                lindy: ['lynn'],
                lionel: ['leon'],
                lisa: ['liz'],
                littleberry: ['little', 'berry', 'l.b.'],
                lizzie: ['liz'],
                lois: ['lou', 'louise'],
                lonzo: ['lon'],
                lorelei: ['lori', 'lorrie', 'laurie'],
                lorenzo: ['loren'],
                loretta: ['etta', 'lorrie', 'retta', 'lorie'],
                lorraine: ['lorrie', 'lorie'],
                lotta: ['lottie'],
                lou: ['louis', 'lu'],
                louis: ['lewis', 'louise', 'louie', 'lou'],
                louisa: ['eliza', 'lou', 'lois'],
                louise: ['eliza', 'lou', 'lois'],
                louvinia: ['vina', 'vonnie', 'wyncha', 'viney'],
                lucas: ['luke'],
                lucia: ['lucy', 'lucius'],
                lucias: ['luke'],
                lucille: ['cille', 'lu', 'lucy', 'lou'],
                lucina: ['sinah'],
                lucinda: ['lu', 'lucy', 'cindy', 'lou'],
                lucretia: ['creasey'],
                lucy: ['lucinda'],
                luella: ['lula', 'ella', 'lu'],
                luke: ['lucas'],
                lunetta: ['nettie'],
                lurana: ['lura'],
                luther: ['luke'],
                lydia: ['lyddy'],
                lyndon: ['lindy', 'lynn'],
                mabel: ['mehitabel', 'amabel'],
                mac: ['mc'],
                mack: ['mac', 'mc'],
                mackenzie: ['kenzy', 'mac', 'mack'],
                maddison: ['maddie', 'maddi'],
                maddy: ['madelyn', 'madeline', 'madge'],
                madeline: ['maggie', 'lena', 'magda', 'maddy', 'madge', 'maddie', 'maddi', 'madie', 'maud'],
                madelyn: ['maddy', 'madie'],
                madie: ['madeline', 'madelyn'],
                madison: ['mattie', 'maddy'],
                maegen: ['meg'],
                magdalena: ['maggie', 'lena'],
                magdelina: ['lena', 'magda', 'madge', 'maggie'],
                mahala: ['hallie'],
                makayla: ['kayla'],
                malachi: ['mally'],
                malcolm: ['mac', 'mal', 'malc'],
                malinda: ['lindy'],
                manda: ['mandy'],
                mandie: ['amanda'],
                mandy: ['amanda'],
                manerva: ['minerva', 'nervie', 'eve', 'nerva'],
                manny: ['manuel'],
                manoah: ['noah'],
                manola: ['nonnie'],
                manuel: ['emanuel', 'manny'],
                marcus: ['mark', 'marc'],
                margaret: [
                    'maggie',
                    'meg',
                    'peg',
                    'midge',
                    'margy',
                    'margie',
                    'madge',
                    'peggy',
                    'maggy',
                    'marge',
                    'daisy',
                    'margery',
                    'gretta',
                    'rita',
                ],
                margaretta: ['maggie', 'meg', 'peg', 'midge', 'margie', 'madge', 'peggy', 'marge', 'daisy', 'margery', 'gretta', 'rita'],
                margarita: [
                    'maggie',
                    'meg',
                    'metta',
                    'midge',
                    'greta',
                    'megan',
                    'maisie',
                    'madge',
                    'marge',
                    'daisy',
                    'peggie',
                    'rita',
                    'margo',
                ],
                marge: ['margery', 'margaret', 'margaretta'],
                margie: ['marjorie'],
                marguerite: ['peggy'],
                mariah: ['mary', 'maria'],
                marian: ['marianna', 'marion'],
                marie: ['mae', 'mary'],
                marietta: [
                    'mariah',
                    'mercy',
                    'polly',
                    'may',
                    'molly',
                    'mitzi',
                    'minnie',
                    'mollie',
                    'mae',
                    'maureen',
                    'marion',
                    'marie',
                    'mamie',
                    'mary',
                    'maria',
                ],
                marilyn: ['mary'],
                marion: ['mary'],
                marissa: ['rissa'],
                marjorie: ['margy', 'margie'],
                marni: ['marnie'],
                marsha: ['marcie', 'mary', 'marcia'],
                martha: ['marty', 'mattie', 'mat', 'patsy', 'patty'],
                martin: ['marty'],
                martina: ['tina'],
                martine: ['tine'],
                marv: ['marvin'],
                marvin: ['marv'],
                mary: ['mamie', 'molly', 'mae', 'polly', 'mitzi', 'marie'],
                masayuki: ['masa'],
                mat: ['mattie'],
                mathew: ['mat', 'maty', 'matt'],
                mathilda: ['tillie', 'patty'],
                matilda: ['tilly', 'maud', 'matty', 'tilla'],
                matthew: ['thys', 'matt', 'thias', 'mattie', 'matty'],
                matthews: ['matt', 'mattie', 'matty'],
                matthias: ['thys', 'matt', 'thias'],
                maud: ['middy'],
                maureen: ['mary'],
                maurice: ['morey'],
                mavery: ['mave'],
                mavine: ['mave'],
                maximillian: ['max'],
                maxine: ['max'],
                maxwell: ['max'],
                may: ['mae'],
                mckenna: ['ken', 'kenna', 'meaka'],
                medora: ['dora'],
                megan: ['meg'],
                meghan: ['meg'],
                mehitabel: ['hetty', 'mitty', 'mabel', 'hitty'],
                melanie: ['mellie'],
                melchizedek: ['zadock', 'dick'],
                melinda: ['linda', 'mel', 'lynn', 'mindy', 'lindy'],
                melissa: ['lisa', 'mel', 'missy', 'milly', 'lissa'],
                mellony: ['mellia'],
                melody: ['lodi'],
                melvin: ['mel'],
                melvina: ['vina'],
                mercedes: ['merci', 'sadie', 'mercy'],
                merv: ['mervin'],
                mervin: ['merv'],
                mervyn: ['merv'],
                micajah: ['cage'],
                michael: ['micky', 'mike', 'micah', 'mick', 'mikey', 'mickey'],
                micheal: ['mike', 'miky', 'mikey'],
                michelle: ['mickey', 'shelley', 'shely', 'chelle', 'shellie', 'shelly'],
                mick: ['micky'],
                miguel: ['miguell', 'miguael', 'miguaell', 'miguail', 'miguaill', 'miguayl', 'miguayll', 'michael', 'mike', 'miggy'],
                mike: ['micky', 'mick', 'michael'],
                mildred: ['milly'],
                millicent: ['missy', 'milly'],
                minerva: ['minnie'],
                minnie: ['wilhelmina'],
                miranda: ['randy', 'mandy', 'mira'],
                miriam: ['mimi', 'mitzi', 'mitzie'],
                missy: ['melissa'],
                mitch: ['mitchell'],
                mitchell: ['mitch'],
                mitzi: ['mary', 'mittie', 'mitty'],
                mitzie: ['mittie', 'mitty'],
                monet: ['nettie'],
                monica: ['monna', 'monnie'],
                monteleon: ['monte'],
                montesque: ['monty'],
                montgomery: ['monty', 'gum'],
                monty: ['lamont'],
                morris: ['morey'],
                mortimer: ['mort'],
                moses: ['amos', 'mose', 'moss'],
                muriel: ['mur'],
                myrtle: ['myrt', 'myrti', 'mert'],
                nadine: ['nada', 'deedee'],
                nancy: ['ann', 'nan', 'nanny'],
                naomi: ['omi'],
                napoleon: ['nap', 'nappy', 'leon'],
                natalie: ['natty', 'nettie'],
                natasha: ['tasha', 'nat'],
                nathan: ['nate', 'nat'],
                nathaniel: ['than', 'nathan', 'nate', 'nat', 'natty'],
                nelle: ['nelly'],
                nelson: ['nels'],
                newt: ['newton'],
                newton: ['newt'],
                nicholas: ['nick', 'claes', 'claas', 'nic', 'nicky', 'nico', 'nickie'],
                nicholette: ['nickey', 'nikki', 'cole', 'nicki', 'nicky', 'nichole', 'nicole'],
                nicodemus: ['nick', 'nic', 'nicky', 'nico', 'nickie'],
                nicole: ['nole', 'nikki', 'cole', 'nicki', 'nicky'],
                nikolas: ['nick', 'claes', 'nic', 'nicky', 'nico', 'nickie'],
                nikole: ['nikki'],
                nora: ['nonie'],
                norbert: ['bert', 'norby'],
                norbusamte: ['norbu'],
                norman: ['norm'],
                nowell: ['noel'],
                obadiah: ['dyer', 'obed', 'obie', 'diah'],
                obediah: ['obie'],
                obedience: ['obed', 'beda', 'beedy', 'biddie'],
                obie: ['obediah'],
                octavia: ['tave', 'tavia'],
                odell: ['odo'],
                olive: ['nollie', 'livia', 'ollie'],
                oliver: ['ollie'],
                olivia: ['nollie', 'livia', 'ollie'],
                ollie: ['oliver'],
                onicyphorous: ['cyphorus', 'osaforus', 'syphorous', 'one', 'cy', 'osaforum'],
                orilla: ['rilly', 'ora'],
                orlando: ['roland'],
                orphelia: ['phelia'],
                ossy: ['ozzy'],
                oswald: ['ozzy', 'waldo', 'ossy'],
                otis: ['ode', 'ote'],
                pamela: ['pam'],
                pandora: ['dora'],
                parmelia: ['amelia', 'milly', 'melia'],
                parthenia: ['teeny', 'parsuny', 'pasoonie', 'phenie'],
                patience: ['pat', 'patty'],
                patricia: ['tricia', 'pat', 'patsy', 'patty', 'patti', 'trish', 'trisha'],
                patrick: ['pate', 'peter', 'pat', 'patsy', 'paddy'],
                patsy: ['patty'],
                patty: ['patricia'],
                paul: ['polly'],
                paula: ['polly', 'lina'],
                paulina: ['polly', 'lina'],
                pauline: ['polly'],
                peggy: ['peg'],
                pelegrine: ['perry'],
                penelope: ['penny'],
                percival: ['percy'],
                peregrine: ['perry'],
                permelia: ['melly', 'milly', 'mellie'],
                pernetta: ['nettie'],
                persephone: ['seph', 'sephy'],
                peter: ['pete', 'pate'],
                petronella: ['nellie'],
                pheney: ['josephine'],
                pheriba: ['pherbia', 'ferbie'],
                philadelphia: ['delphia'],
                philander: ['fie'],
                philetus: ['leet', 'phil'],
                philinda: ['linda', 'lynn', 'lindy'],
                philip: ['phil', 'pip'],
                philipina: ['phoebe', 'penie', 'pip'],
                phillip: ['phil', 'pip'],
                philly: ['delphia'],
                philomena: ['menaalmena'],
                phoebe: ['fifi'],
                pinckney: ['pink'],
                pleasant: ['ples'],
                pocahontas: ['pokey'],
                posthuma: ['humey'],
                prescott: ['scotty', 'scott', 'pres'],
                priscilla: ['prissy', 'cissy', 'cilla'],
                providence: ['provy'],
                prudence: ['prue', 'prudy'],
                prudy: ['prudence'],
                rachel: ['shelly', 'rachael'],
                rafaela: ['rafa'],
                ramona: ['mona'],
                randolph: ['dolph', 'randy'],
                raphael: ['ralph'],
                ray: ['raymond'],
                raymond: ['ray'],
                reba: ['beck', 'becca'],
                rebecca: ['beck', 'becca', 'reba', 'becky'],
                reggie: ['reginald', 'reg'],
                regina: ['reggie', 'gina'],
                reginald: ['reggie', 'naldo', 'reg', 'renny'],
                relief: ['leafa'],
                reuben: ['rube'],
                reynold: ['reginald'],
                rhoda: ['rodie'],
                rhodella: ['della'],
                rhyna: ['rhynie'],
                ricardo: ['rick', 'ricky'],
                rich: ['dick', 'rick'],
                richard: ['dick', 'dickon', 'dickie', 'dicky', 'rick', 'rich', 'ricky', 'richie'],
                rick: ['ricky'],
                ricky: ['dick', 'rich'],
                robert: ['hob', 'hobkin', 'dob', 'rob', 'bobby', 'dobbin', 'bob', 'bill', 'billy', 'robby'],
                roberta: ['robbie', 'bert', 'bobbie', 'birdie', 'bertie', 'roby', 'birtie'],
                roberto: ['rob'],
                roderick: ['rod', 'erick', 'rickie', 'roddy'],
                rodger: ['roge', 'bobby', 'hodge', 'rod', 'robby', 'rupert', 'robin'],
                rodney: ['rod'],
                roger: ['roge', 'bobby', 'hodge', 'rod', 'robby', 'rupert', 'robin'],
                roland: ['rollo', 'lanny', 'orlando', 'rolly'],
                ron: ['ronnie', 'ronny'],
                ronald: ['naldo', 'ron', 'ronny', 'ronnie'],
                ronny: ['ronald'],
                rosa: ['rose'],
                rosabel: ['belle', 'roz', 'rosa', 'rose'],
                rosabella: ['belle', 'roz', 'rosa', 'rose', 'bella'],
                rosaenn: ['ann'],
                rosaenna: ['ann'],
                rosalinda: ['linda', 'roz', 'rosa', 'rose'],
                rosalyn: ['linda', 'roz', 'rosa', 'rose'],
                roscoe: ['ross'],
                rose: ['rosie'],
                roseann: ['rose', 'ann', 'rosie', 'roz'],
                roseanna: ['rose', 'ann', 'rosie', 'roz'],
                roseanne: ['ann'],
                rosemary: ['rosemarie', 'marie', 'mary', 'rose', 'rosey'],
                rosina: ['sina'],
                roxane: ['rox', 'roxie'],
                roxanna: ['roxie', 'rose', 'ann'],
                roxanne: ['roxie', 'rose', 'ann'],
                rudolph: ['dolph', 'rudy', 'olph', 'rolf'],
                rudolphus: ['dolph', 'rudy', 'olph', 'rolf'],
                russell: ['russ', 'rusty'],
                ryan: ['ry'],
                sabrina: ['brina'],
                safieel: ['safie'],
                salome: ['loomie'],
                salvador: ['sal', 'sally'],
                sam: ['sammy'],
                samantha: ['sam', 'sammy', 'mantha'],
                sampson: ['sam', 'sammy'],
                samson: ['sam', 'sammy'],
                samuel: ['sam', 'sammy'],
                samyra: ['sam', 'sammy', 'myra'],
                sandra: ['sandy', 'cassandra'],
                sandy: ['sandra'],
                sanford: ['sandy'],
                sarah: ['sally', 'sadie', 'sara'],
                sarilla: ['silla'],
                savannah: ['vannie', 'anna', 'savanna'],
                scott: ['scotty', 'sceeter', 'squat', 'scottie'],
                sebastian: ['sebby', 'seb'],
                selma: ['anselm'],
                serena: ['rena'],
                serilla: ['rilla'],
                seymour: ['see', 'morey'],
                shaina: ['sha', 'shay'],
                sharon: ['sha', 'shay'],
                shaun: ['shawn'],
                shawn: ['shaun'],
                sheila: ['cecilia'],
                sheldon: ['shelly'],
                shelton: ['tony', 'shel', 'shelly'],
                sheridan: ['dan', 'danny', 'sher'],
                sheryl: ['sher', 'sheri', 'sherry', 'sherryl', 'sherri', 'cheri', 'cherie'],
                shirley: ['sherry', 'lee', 'shirl'],
                sibbilla: ['sybill', 'sibbie', 'sibbell'],
                sidney: ['syd', 'sid'],
                sigfired: ['sid'],
                sigfrid: ['sid'],
                sigismund: ['sig'],
                silas: ['si'],
                silence: ['liley'],
                silvester: ['vester', 'si', 'sly', 'vest', 'syl'],
                simeon: ['si', 'sion'],
                simon: ['si', 'sion'],
                smith: ['smitty'],
                socrates: ['crate'],
                solomon: ['sal', 'salmon', 'sol', 'solly', 'saul', 'zolly'],
                sondra: ['dre', 'sonnie'],
                sophia: ['sophie'],
                sophronia: ['frona', 'sophia', 'fronia'],
                stacey: ['stacy', 'staci', 'stacie'],
                stacie: ['stacy', 'stacey', 'staci'],
                stacy: ['staci'],
                stephan: ['steve'],
                stephanie: ['stephie', 'annie', 'steph', 'stevie', 'stephine', 'stephany', 'stephani', 'steffi', 'steffie'],
                stephen: ['steve', 'steph'],
                steven: ['steve', 'steph', 'stevie'],
                stuart: ['stu'],
                sue: ['susie', 'susan'],
                sullivan: ['sully', 'van'],
                susan: ['hannah', 'susie', 'sue', 'sukey', 'suzie'],
                susannah: ['hannah', 'susie', 'sue', 'sukey'],
                susie: ['suzie'],
                suzanne: ['suki', 'sue', 'susie'],
                sybill: ['sibbie'],
                sydney: ['sid'],
                sylvanus: ['sly', 'syl'],
                sylvester: ['sy', 'sly', 'vet', 'syl', 'vester', 'si', 'vessie'],
                tabby: ['tabitha'],
                tabitha: ['tabby'],
                tamarra: ['tammy'],
                tammie: ['tammy', 'tami'],
                tammy: ['tammie', 'tami'],
                tanafra: ['tanny'],
                tasha: ['tash', 'tashie'],
                ted: ['teddy'],
                temperance: ['tempy'],
                terence: ['terry'],
                teresa: ['terry', 'tess', 'tessa', 'tessie'],
                terri: ['terrie', 'terry', 'teri'],
                terry: ['terence'],
                tess: ['teresa', 'theresa'],
                tessa: ['teresa', 'theresa'],
                thad: ['thaddeus'],
                thaddeus: ['thad'],
                theo: ['theodore'],
                theodora: ['dora'],
                theodore: ['theo', 'ted', 'teddy'],
                theodosia: ['theo', 'dosia', 'theodosius'],
                theophilus: ['ophi'],
                theotha: ['otha'],
                theresa: ['tessie', 'thirza', 'tessa', 'terry', 'tracy', 'tess', 'thursa', 'traci', 'tracie'],
                thom: ['thomas', 'tommy', 'tom'],
                thomas: ['thom', 'tommy', 'tom'],
                thomasa: ['tamzine'],
                tiffany: ['tiff', 'tiffy'],
                tilford: ['tillie'],
                tim: ['timmy'],
                timothy: ['tim', 'timmy'],
                tina: ['christina'],
                tisha: ['tish'],
                tobias: ['bias', 'toby'],
                tom: ['thomas', 'tommy'],
                tony: ['anthony'],
                tranquilla: ['trannie', 'quilla'],
                trish: ['trisha', 'patricia'],
                trix: ['trixie'],
                trudy: ['gertrude'],
                tryphena: ['phena'],
                unice: ['eunice', 'nicie'],
                uriah: ['riah'],
                ursula: ['sulie', 'sula'],
                valentina: ['felty', 'vallie', 'val'],
                valentine: ['felty'],
                valeri: ['valerie', 'val'],
                valerie: ['val'],
                vanburen: ['buren'],
                vandalia: ['vannie'],
                vanessa: ['essa', 'vanna', 'nessa'],
                vernisee: ['nicey'],
                veronica: ['vonnie', 'ron', 'ronna', 'ronie', 'frony', 'franky', 'ronnie', 'ronny'],
                vic: ['vicki', 'vickie', 'vicky', 'victor'],
                vicki: ['vickie', 'vicky', 'victoria'],
                victor: ['vic'],
                victoria: ['torie', 'vic', 'vicki', 'tory', 'vicky', 'tori', 'torri', 'torrie', 'vickie'],
                vijay: ['vij'],
                vincent: ['vic', 'vince', 'vinnie', 'vin', 'vinny'],
                vincenzo: ['vic', 'vinnie', 'vin', 'vinny', 'vince'],
                vinson: ['vinny', 'vinnie', 'vin', 'vince'],
                viola: ['ola', 'vi'],
                violetta: ['lettie'],
                virginia: ['jane', 'jennie', 'ginny', 'virgy', 'ginger'],
                vivian: ['vi', 'viv'],
                waldo: ['ozzy', 'ossy'],
                wallace: ['wally'],
                wally: ['walt'],
                walter: ['wally', 'walt'],
                washington: ['wash'],
                webster: ['webb'],
                wendy: ['wen'],
                wesley: ['wes'],
                westley: ['west', 'wes', 'farmboy'],
                wilber: ['will', 'bert'],
                wilbur: ['willy', 'willie', 'will'],
                wilda: ['willie'],
                wilfred: ['will', 'willie', 'fred', 'wil'],
                wilhelm: ['wil', 'willie'],
                wilhelmina: ['mina', 'wilma', 'willie', 'minnie'],
                will: ['bill', 'willie', 'wilbur', 'fred'],
                william: ['willy', 'bell', 'bela', 'bill', 'will', 'billy', 'willie', 'wil'],
                willie: ['william', 'fred'],
                willis: ['willy', 'bill'],
                wilma: ['william', 'billiewilhelm'],
                wilson: ['will', 'willy', 'willie'],
                winfield: ['field', 'winny', 'win'],
                winifred: ['freddie', 'winnie', 'winnet'],
                winnie: ['winnifred'],
                winnifred: ['freddie', 'freddy', 'winny', 'winnie', 'fred'],
                winny: ['winnifred'],
                winton: ['wint'],
                woodrow: ['woody', 'wood', 'drew'],
                yeona: ['onie', 'ona'],
                yoshihiko: ['yoshi'],
                yulan: ['lan', 'yul'],
                yvonne: ['vonna'],
                zach: ['zack', 'zak'],
                zachariah: ['zachy', 'zach', 'zeke', 'zac', 'zack', 'zak', 'zakk'],
                zachary: ['zachy', 'zach', 'zeke', 'zac', 'zack', 'zak', 'zakk'],
                zachery: ['zachy', 'zach', 'zeke', 'zac', 'zack', 'zak', 'zakk'],
                zack: ['zach', 'zak'],
                zebedee: ['zeb'],
                zedediah: ['dyer', 'zed', 'diah'],
                zephaniah: ['zeph'],
            };
            return this._memo;
        },
    };

    const states = {
        AL: 'Alabama',
        AK: 'Alaska',
        AZ: 'Arizona',
        AR: 'Arkansas',
        CA: 'California',
        CO: 'Colorado',
        CT: 'Connecticut',
        DC: 'District of Columbia',
        DE: 'Delaware',
        FL: 'Florida',
        GA: 'Georgia',
        HI: 'Hawaii',
        ID: 'Idaho',
        IL: 'Illinois',
        IN: 'Indiana',
        IA: 'Iowa',
        KS: 'Kansas',
        KY: 'Kentucky',
        LA: 'Louisiana',
        ME: 'Maine',
        MD: 'Maryland',
        MA: 'Massachusetts',
        MI: 'Michigan',
        MN: 'Minnesota',
        MS: 'Mississippi',
        MO: 'Missouri',
        MT: 'Montana',
        NE: 'Nebraska',
        NV: 'Nevada',
        NH: 'New Hampshire',
        NJ: 'New Jersey',
        NM: 'New Mexico',
        NY: 'New York',
        NC: 'North Carolina',
        ND: 'North Dakota',
        OH: 'Ohio',
        OK: 'Oklahoma',
        OR: 'Oregon',
        PA: 'Pennsylvania',
        RI: 'Rhode Island',
        SC: 'South Carolina',
        SD: 'South Dakota',
        TN: 'Tennessee',
        TX: 'Texas',
        UT: 'Utah',
        VT: 'Vermont',
        VA: 'Virginia',
        WA: 'Washington',
        WV: 'West Virginia',
        WI: 'Wisconsin',
        WY: 'Wyoming',
    };

    /**
     * Get a single element.
     *
     * @param {Node} doc
     * @param {string} selector
     * @return {HTMLElement | null}
     */
    function getElement(doc = document, selector) {
        if (isXpath(selector)) {
            return safeQuerySelectorXPath(doc, selector);
        }

        return safeQuerySelector(doc, selector);
    }

    /**
     * Get an array of elements
     *
     * @param {Node} doc
     * @param {string} selector
     * @return {HTMLElement[] | null}
     */
    function getElements(doc = document, selector) {
        if (isXpath(selector)) {
            return safeQuerySelectorAllXpath(doc, selector);
        }

        return safeQuerySelectorAll(doc, selector);
    }

    /**
     * Test if a given selector matches an element.
     *
     * @param {HTMLElement} element
     * @param {string} selector
     */
    function getElementMatches(element, selector) {
        try {
            if (isXpath(selector)) {
                return matchesXPath(element, selector) ? element : null;
            } else {
                return element.matches(selector) ? element : null;
            }
        } catch (e) {
            console.error('getElementMatches threw: ', e);
            return null;
        }
    }

    /**
     * This is a xpath version of `element.matches(CSS_SELECTOR)`
     * @param {HTMLElement} element
     * @param {string} selector
     * @return {boolean}
     */
    function matchesXPath(element, selector) {
        const xpathResult = document.evaluate(selector, element, null, XPathResult.BOOLEAN_TYPE, null);

        return xpathResult.booleanValue;
    }

    /**
     * @param {unknown} selector
     * @returns {boolean}
     */
    function isXpath(selector) {
        if (!(typeof selector === 'string')) return false;

        // see: https://www.w3.org/TR/xpath20/
        // "When the context item is a node, it can also be referred to as the context node. The context item is returned by an expression consisting of a single dot"
        if (selector === '.') return true;
        return selector.startsWith('//') || selector.startsWith('./') || selector.startsWith('(');
    }

    /**
     * @param {Element|Node} element
     * @param selector
     * @returns {HTMLElement[] | null}
     */
    function safeQuerySelectorAll(element, selector) {
        try {
            if (element && 'querySelectorAll' in element) {
                return Array.from(element?.querySelectorAll?.(selector));
            }
            return null;
        } catch (e) {
            return null;
        }
    }
    /**
     * @param {Element|Node} element
     * @param selector
     * @returns {HTMLElement | null}
     */
    function safeQuerySelector(element, selector) {
        try {
            if (element && 'querySelector' in element) {
                return element?.querySelector?.(selector);
            }
            return null;
        } catch (e) {
            return null;
        }
    }

    /**
     * @param {Node} element
     * @param selector
     * @returns {HTMLElement | null}
     */
    function safeQuerySelectorXPath(element, selector) {
        try {
            const match = document.evaluate(selector, element, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
            const single = match?.singleNodeValue;
            if (single) {
                return /** @type {HTMLElement} */ (single);
            }
            return null;
        } catch (e) {
            console.log('safeQuerySelectorXPath threw', e);
            return null;
        }
    }

    /**
     * @param {Element|Node} element
     * @param selector
     * @returns {HTMLElement[] | null}
     */
    function safeQuerySelectorAllXpath(element, selector) {
        try {
            // gets all elements matching the xpath query
            const xpathResult = document.evaluate(selector, element, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
            if (xpathResult) {
                /** @type {HTMLElement[]} */
                const matchedNodes = [];
                for (let i = 0; i < xpathResult.snapshotLength; i++) {
                    const item = xpathResult.snapshotItem(i);
                    if (item) matchedNodes.push(/** @type {HTMLElement} */ (item));
                }
                return /** @type {HTMLElement[]} */ (matchedNodes);
            }
            return null;
        } catch (e) {
            console.log('safeQuerySelectorAllXpath threw', e);
            return null;
        }
    }

    /**
     * @param {number} min
     * @param {number} max
     * @returns {number}
     */
    function generateRandomInt(min, max) {
        return Math.floor(Math.random() * (max - min + 1) + min);
    }

    /**
     * CleanArray flattens an array of any input, removing nulls, undefined, and empty strings.
     *
     * @template T
     * @param {T | T[] | null | undefined} input - The input to clean.
     * @param {NonNullable<T>[]} prev
     * @return {NonNullable<T>[]} - The cleaned array.
     */
    function cleanArray(input, prev = []) {
        if (!Array.isArray(input)) {
            if (input === null) return prev;
            if (input === undefined) return prev;
            // special case for empty strings
            if (typeof input === 'string') {
                const trimmed = input.trim();
                if (trimmed.length > 0) {
                    prev.push(/** @type {NonNullable<T>} */ (trimmed));
                }
            } else {
                prev.push(input);
            }
            return prev;
        }

        for (const item of input) {
            prev.push(...cleanArray(item));
        }

        return prev;
    }

    /**
     * Determines whether the given input is a non-empty string.
     *
     * @param {any} [input] - The input to be checked.
     * @return {boolean} - True if the input is a non-empty string, false otherwise.
     */
    function nonEmptyString(input) {
        if (typeof input !== 'string') return false;
        return input.trim().length > 0;
    }

    /**
     * Checks if two strings are a matching pair, ignoring case and leading/trailing white spaces.
     *
     * @param {any} a - The first string to compare.
     * @param {any} b - The second string to compare.
     * @return {boolean} - Returns true if the strings are a matching pair, false otherwise.
     */
    function matchingPair(a, b) {
        if (!nonEmptyString(a)) return false;
        if (!nonEmptyString(b)) return false;
        return a.toLowerCase().trim() === b.toLowerCase().trim();
    }

    /**
     * Sorts an array of addresses by state, then by city within the state.
     *
     * @param {any} addresses
     * @return {Array}
     */
    function sortAddressesByStateAndCity(addresses) {
        return addresses.sort((a, b) => {
            if (a.state < b.state) {
                return -1;
            }
            if (a.state > b.state) {
                return 1;
            }
            return a.city.localeCompare(b.city);
        });
    }

    /**
     * Returns a SHA-1 hash of the profile
     */
    async function hashObject(profile) {
        const msgUint8 = new TextEncoder().encode(JSON.stringify(profile)); // encode as (utf-8)
        const hashBuffer = await crypto.subtle.digest('SHA-1', msgUint8); // hash the message
        const hashArray = Array.from(new Uint8Array(hashBuffer)); // convert buffer to byte array
        const hashHex = hashArray.map((b) => b.toString(16).padStart(2, '0')).join(''); // convert bytes to hex string

        return hashHex;
    }

    /**
     * @param {{city: string; state: string | null}[]} userAddresses
     * @param {{city: string; state: string | null}[]} foundAddresses
     * @return {boolean}
     */
    function addressMatch(userAddresses, foundAddresses) {
        return userAddresses.some((user) => {
            return foundAddresses.some((found) => {
                return matchingPair(user.city, found.city) && matchingPair(user.state, found.state);
            });
        });
    }

    function getStateFromAbbreviation(stateAbbreviation) {
        if (stateAbbreviation == null || stateAbbreviation.trim() === '') {
            return null;
        }

        const state = stateAbbreviation.toUpperCase();

        return states[state] || null;
    }

    /**
     * @typedef {{url: string} & Record<string, any>} BuildUrlAction
     * @typedef {Record<string, any>} BuildActionWithoutUrl
     * @typedef {Record<string, string|number>} UserData
     */

    /**
     * Input: { url: 'https://example.com/a/${firstName}-${lastName}', ... }
     * Output: { url: 'https://example.com/a/John-Smith' }
     *
     * @param {BuildUrlAction} action
     * @param {Record<string, string|number>} userData
     * @return {{ url: string } | { error: string }}
     */
    function transformUrl(action, userData) {
        const url = new URL(action.url);

        /**
         * assign the updated pathname + search params
         */
        url.search = processSearchParams(url.searchParams, action, userData).toString();
        url.pathname = processPathname(url.pathname, action, userData);

        /**
         * Finally, convert back to a full URL
         */
        return { url: url.toString() };
    }

    /**
     * These will be applied by default if the key exists in the data.
     *
     * @type {Map<string, ((value: string) => string)>}
     */
    const baseTransforms = new Map([
        ['firstName', (value) => capitalize(value)],
        ['lastName', (value) => capitalize(value)],
        ['state', (value) => value.toLowerCase()],
        ['city', (value) => capitalize(value)],
        ['age', (value) => value.toString()],
    ]);

    /**
     * These are optional transforms, will be applied when key is found in the
     * variable syntax
     *
     * Example, `/a/b/${name|capitalize}` -> applies the `capitalize` transform
     * to the name field
     *
     * @type {Map<string, ((value: string, argument: string|undefined, action: BuildUrlAction | BuildActionWithoutUrl) => string)>}
     */
    const optionalTransforms = new Map([
        ['hyphenated', (value) => value.split(' ').join('-')],
        ['capitalize', (value) => capitalize(value)],
        ['downcase', (value) => value.toLowerCase()],
        ['upcase', (value) => value.toUpperCase()],
        ['snakecase', (value) => value.split(' ').join('_')],
        ['stateFull', (value) => getStateFromAbbreviation(value)],
        ['defaultIfEmpty', (value, argument) => value || argument || ''],
        [
            'ageRange',
            (value, argument, action) => {
                if (!action.ageRange) return value;
                const ageNumber = Number(value);
                // find matching age range
                const ageRange = action.ageRange.find((range) => {
                    const [min, max] = range.split('-');
                    return ageNumber >= Number(min) && ageNumber <= Number(max);
                });
                return ageRange || value;
            },
        ],
    ]);

    /**
     * Take an instance of URLSearchParams and produce a new one, with each variable
     * replaced with a value, or a transformed value.
     *
     * @param {URLSearchParams} searchParams
     * @param {BuildUrlAction} action
     * @param {Record<string, string|number>} userData
     * @return {URLSearchParams}
     */
    function processSearchParams(searchParams, action, userData) {
        /**
         * For each key/value pair in the URL Search params, process the value
         * part *only*.
         */
        const updatedPairs = [...searchParams].map(([key, value]) => {
            const processedValue = processTemplateStringWithUserData(value, action, userData);
            return [key, processedValue];
        });

        return new URLSearchParams(updatedPairs);
    }

    /**
     * @param {string} pathname
     * @param {BuildUrlAction} action
     * @param {Record<string, string|number>} userData
     */
    function processPathname(pathname, action, userData) {
        return pathname
            .split('/')
            .filter(Boolean)
            .map((segment) => processTemplateStringWithUserData(segment, action, userData))
            .join('/');
    }

    /**
     * Process strings like /a/b/${name|lowercase}-${age}
     * Where the first segment of any variable is the data key, and any
     * number of subsequent strings are expected to be known transforms
     *
     * In that example:
     *
     *  - `name` would be processed with the 'lowercase' transform
     *  - `age` would be used without processing
     *
     * The regular expression `/\$%7B(.+?)%7D|\$\{(.+?)}/g` is designed to match and capture
     * the content within template literals in two formats: encoded and plain.
     *
     * 1. Encoded Format: `\$%7B(.+?)%7D`
     *    - Matches encoded template strings that start with `$%7B` and end with `%7D`.
     *    - These occur when variables are present in the pathname of the URL
     *
     * 2. Plain Format: `\$\{(.+?)\}`
     *    - Matches plain template strings that start with `${` and end with `}`.
     *    - These occur when variables are present in the value side of any query params
     *
     * This regular expression is used to identify and process these template literals within the input string,
     * allowing the function to replace them with corresponding data from `userData` after applying any specified transformations.
     *
     * @param {string} input
     * @param {BuildUrlAction | BuildActionWithoutUrl} action
     * @param {Record<string, string|number>} userData
     */
    function processTemplateStringWithUserData(input, action, userData) {
        /**
         * Note: this regex covers both pathname + query params.
         * This is why we're handling both encoded and un-encoded.
         */
        return String(input).replace(/\$%7B(.+?)%7D|\$\{(.+?)}/g, (match, encodedValue, plainValue) => {
            const comparison = encodedValue ?? plainValue;
            const [dataKey, ...transforms] = comparison.split(/\||%7C/);
            const data = userData[dataKey];
            return applyTransforms(dataKey, data, transforms, action);
        });
    }

    /**
     * @param {string} dataKey
     * @param {string|number} value
     * @param {string[]} transformNames
     * @param {BuildUrlAction | BuildActionWithoutUrl} action
     */
    function applyTransforms(dataKey, value, transformNames, action) {
        const subject = String(value || '');
        const baseTransform = baseTransforms.get(dataKey);

        // apply base transform to the incoming string
        let outputString = baseTransform ? baseTransform(subject) : subject;

        for (const transformName of transformNames) {
            const [name, argument] = transformName.split(':');
            const transform = optionalTransforms.get(name);
            if (transform) {
                outputString = transform(outputString, argument, action);
            }
        }

        return outputString;
    }

    function capitalize(s) {
        const words = s.split(' ');
        const capitalizedWords = words.map((word) => word.charAt(0).toUpperCase() + word.slice(1));
        return capitalizedWords.join(' ');
    }

    /**
     * @typedef {SuccessResponse | ErrorResponse} ActionResponse
     * @typedef {{ result: true } | { result: false; error: string }} BooleanResult
     * @typedef {{type: "element" | "text" | "url"; selector: string; parent?: string; expect?: string; failSilently?: boolean}} Expectation
     */

    /**
     * Represents an error
     */
    class ErrorResponse {
        /**
         * @param {object} params
         * @param {string} params.actionID
         * @param {string} params.message
         */
        constructor(params) {
            this.error = params;
        }
    }

    /**
     * Represents success, `response` can contain other complex types
     */
    class SuccessResponse {
        /**
         * @param {object} params
         * @param {string} params.actionID
         * @param {string} params.actionType
         * @param {any} params.response
         * @param {import("./actions/extract").Action[]} [params.next]
         * @param {Record<string, any>} [params.meta] - optional meta data
         */
        constructor(params) {
            this.success = params;
        }
    }

    /**
     * A type that includes the result + metadata of comparing a DOM element + children
     * to a set of data. Use this for analysis/debugging
     */
    class ProfileResult {
        /**
         * @param {object} params
         * @param {boolean} params.result - whether we consider this a 'match'
         * @param {string[]} params.matchedFields - a list of the fields in the data that were matched.
         * @param {number} params.score - value to determine
         * @param {HTMLElement} [params.element] - the parent element that was matched. Not present in JSON
         * @param {Record<string, any>} params.scrapedData
         */
        constructor(params) {
            this.scrapedData = params.scrapedData;
            this.result = params.result;
            this.score = params.score;
            this.element = params.element;
            this.matchedFields = params.matchedFields;
        }

        /**
         * Convert this structure into a format that can be sent between JS contexts/native
         * @return {{result: boolean, score: number, matchedFields: string[], scrapedData: Record<string, any>}}
         */
        asData() {
            return {
                scrapedData: this.scrapedData,
                result: this.result,
                score: this.score,
                matchedFields: this.matchedFields,
            };
        }
    }

    /**
     * This builds the proper URL given the URL template and userData.
     *
     * @param action
     * @param {Record<string, any>} userData
     * @return {import('../types.js').ActionResponse}
     */
    function buildUrl(action, userData) {
        const result = replaceTemplatedUrl(action, userData);
        if ('error' in result) {
            return new ErrorResponse({ actionID: action.id, message: result.error });
        }

        return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: { url: result.url } });
    }

    /**
     * Perform some basic validations before we continue into the templating.
     *
     * @param action
     * @param userData
     * @return {{url: string} | {error: string}}
     */
    function replaceTemplatedUrl(action, userData) {
        const url = action?.url;
        if (!url) {
            return { error: 'Error: No url provided.' };
        }

        try {
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            const _ = new URL(action.url);
        } catch (e) {
            return { error: 'Error: Invalid URL provided.' };
        }

        if (!userData) {
            return { url };
        }

        return transformUrl(action, userData);
    }

    /**
     * @param userAge
     * @param ageFound
     * @return {boolean}
     */
    function isSameAge(userAge, ageFound) {
        // variance allows for +/- 1 on the data broker and +/- 1 based on the only having a birth year
        const ageVariance = 2;
        userAge = parseInt(userAge);
        ageFound = parseInt(ageFound);

        if (isNaN(ageFound)) {
            return false;
        }

        if (Math.abs(userAge - ageFound) < ageVariance) {
            return true;
        }

        return false;
    }

    /**
     * @param {string} fullNameExtracted
     * @param {string} userFirstName
     * @param {string | null | undefined} userMiddleName
     * @param {string} userLastName
     * @param {string | null} [userSuffix]
     * @return {boolean}
     */
    function isSameName(fullNameExtracted, userFirstName, userMiddleName, userLastName, userSuffix) {
        // If there's no name on the website, then there's no way we can match it
        if (!fullNameExtracted) {
            return false;
        }

        // these fields should never be absent. If they are we cannot continue
        if (!userFirstName || !userLastName) return false;

        fullNameExtracted = fullNameExtracted.toLowerCase().trim().replace('.', '');
        userFirstName = userFirstName.toLowerCase();
        userMiddleName = userMiddleName ? userMiddleName.toLowerCase() : null;
        userLastName = userLastName.toLowerCase();
        userSuffix = userSuffix ? userSuffix.toLowerCase() : null;

        // Get a list of the user's name and nicknames / full names
        const names = getNames(userFirstName);

        for (const firstName of names) {
            // Let's check if the name matches right off the bat
            const nameCombo1 = `${firstName} ${userLastName}`;
            if (fullNameExtracted === nameCombo1) {
                return true;
            }

            // If the user didn't supply a middle name, then try to match names extracted names that
            // might include a middle name.
            if (!userMiddleName) {
                const combinedLength = firstName.length + userLastName.length;
                const matchesFirstAndLast =
                    fullNameExtracted.startsWith(firstName) &&
                    fullNameExtracted.endsWith(userLastName) &&
                    fullNameExtracted.length > combinedLength;
                if (matchesFirstAndLast) {
                    return true;
                }
            }

            // If there's a suffix, check that too
            if (userSuffix) {
                const nameCombo1WithSuffix = `${firstName} ${userLastName} ${userSuffix}`;
                if (fullNameExtracted === nameCombo1WithSuffix) {
                    return true;
                }
            }

            // If the user has a name with a hyphen, we should split it on the hyphen
            // Note: They may have a last name or first name with a hyphen
            if (userLastName && userLastName.includes('-')) {
                const userLastNameOption2 = userLastName.split('-').join(' ');
                const userLastNameOption3 = userLastName.split('-').join('');
                const userLastNameOption4 = userLastName.split('-')[0];

                const comparisons = [
                    `${firstName} ${userLastNameOption2}`,
                    `${firstName} ${userLastNameOption3}`,
                    `${firstName} ${userLastNameOption4}`,
                ];

                if (comparisons.includes(fullNameExtracted)) {
                    return true;
                }
            }

            // Treat first name with the same logic as the last name
            if (userFirstName && userFirstName.includes('-')) {
                const userFirstNameOption2 = userFirstName.split('-').join(' ');
                const userFirstNameOption3 = userFirstName.split('-').join('');
                const userFirstNameOption4 = userFirstName.split('-')[0];

                const comparisons = [
                    `${userFirstNameOption2} ${userLastName}`,
                    `${userFirstNameOption3} ${userLastName}`,
                    `${userFirstNameOption4} ${userLastName}`,
                ];

                if (comparisons.includes(fullNameExtracted)) {
                    return true;
                }
            }

            // Only run this if they have a middle name
            // Note: Only do the suffix comparison if it actually exists
            if (userMiddleName) {
                const comparisons = [
                    `${firstName} ${userMiddleName} ${userLastName}`,
                    `${firstName} ${userMiddleName} ${userLastName} ${userSuffix}`,
                    `${firstName} ${userMiddleName[0]} ${userLastName}`,
                    `${firstName} ${userMiddleName[0]} ${userLastName} ${userSuffix}`,
                    `${firstName} ${userMiddleName}${userLastName}`,
                    `${firstName} ${userMiddleName}${userLastName} ${userSuffix}`,
                ];

                if (comparisons.includes(fullNameExtracted)) {
                    return true;
                }

                // If it's a hyphenated last name, we have more to try
                if (userLastName && userLastName.includes('-')) {
                    const userLastNameOption2 = userLastName.split('-').join(' ');
                    const userLastNameOption3 = userLastName.split('-').join('');
                    const userLastNameOption4 = userLastName.split('-')[0];

                    const comparisons = [
                        `${firstName} ${userMiddleName} ${userLastNameOption2}`,
                        `${firstName} ${userMiddleName} ${userLastNameOption4}`,
                        `${firstName} ${userMiddleName[0]} ${userLastNameOption2}`,
                        `${firstName} ${userMiddleName[0]} ${userLastNameOption3}`,
                        `${firstName} ${userMiddleName[0]} ${userLastNameOption4}`,
                    ];

                    if (comparisons.includes(fullNameExtracted)) {
                        return true;
                    }
                }

                // If it's a hyphenated name, we have more to try
                if (userFirstName && userFirstName.includes('-')) {
                    const userFirstNameOption2 = userFirstName.split('-').join(' ');
                    const userFirstNameOption3 = userFirstName.split('-').join('');
                    const userFirstNameOption4 = userFirstName.split('-')[0];

                    const comparisons = [
                        `${userFirstNameOption2} ${userMiddleName} ${userLastName}`,
                        `${userFirstNameOption3} ${userMiddleName} ${userLastName}`,
                        `${userFirstNameOption4} ${userMiddleName} ${userLastName}`,
                        `${userFirstNameOption2} ${userMiddleName[0]} ${userLastName}`,
                        `${userFirstNameOption3} ${userMiddleName[0]} ${userLastName}`,
                        `${userFirstNameOption4} ${userMiddleName[0]} ${userLastName}`,
                    ];

                    if (comparisons.includes(fullNameExtracted)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Given the user's provided name, look for nicknames or full names and return a list
     *
     * @param {string | null} name
     * @return {Set<string>}
     */
    function getNames(name) {
        if (!noneEmptyString(name)) {
            return new Set();
        }

        name = name.toLowerCase();
        const nicknames = names.nicknames;

        return new Set([name, ...getNicknames(name, nicknames), ...getFullNames(name, nicknames)]);
    }

    /**
     * Given a full name, get a list of nicknames, e.g. Gregory -> Greg
     *
     * @param {string | null} name
     * @param {Record<string, string[]>} nicknames
     * @return {Set<string>}
     */
    function getNicknames(name, nicknames) {
        const emptySet = new Set();

        if (!noneEmptyString(name)) {
            return emptySet;
        }

        name = name.toLowerCase();

        if (Object.prototype.hasOwnProperty.call(nicknames, name)) {
            return new Set(nicknames[name]);
        }

        return emptySet;
    }

    /**
     * Given a nickname, get a list of full names - e.g. Greg -> Gregory
     *
     * @param {string | null} name
     * @param {Record<string, string[]>} nicknames
     * @return {Set<string>}
     */
    function getFullNames(name, nicknames) {
        const fullNames = new Set();

        if (!noneEmptyString(name)) {
            return fullNames;
        }

        name = name.toLowerCase();

        for (const fullName of Object.keys(nicknames)) {
            if (nicknames[fullName].includes(name)) {
                fullNames.add(fullName);
            }
        }

        return fullNames;
    }

    /**
     * This will handle all none-string types like null / undefined too
     * @param {any} [input]
     * @return {input is string}
     */
    function noneEmptyString(input) {
        if (typeof input !== 'string') return false;
        return input.trim().length > 0;
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars

    /**
     * @implements {Extractor<string | null>}
     */
    class AgeExtractor {
        /**
         * @param {string[]} strs
         * @param {import('../actions/extract.js').ExtractorParams} _extractorParams
         */

        extract(strs, _extractorParams) {
            if (!strs[0]) return null;
            return strs[0].match(/\d+/)?.[0] ?? null;
        }
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars

    /**
     * @implements {Extractor<string | null>}
     */
    class NameExtractor {
        /**
         * @param {string[]} strs
         * @param {import('../actions/extract.js').ExtractorParams} _extractorParams
         */

        extract(strs, _extractorParams) {
            if (!strs[0]) return null;
            return strs[0].replace(/\n/g, ' ').trim();
        }
    }

    /**
     * @implements {Extractor<string[]>}
     */
    class AlternativeNamesExtractor {
        /**
         * @param {string[]} strs
         * @param {import('../actions/extract.js').ExtractorParams} extractorParams
         * @returns {string[]}
         */
        extract(strs, extractorParams) {
            return strs.map((x) => stringToList(x, extractorParams.separator)).flat();
        }
    }

    function getDefaultExportFromCjs (x) {
    	return x && x.__esModule && Object.prototype.hasOwnProperty.call(x, 'default') ? x['default'] : x;
    }

    function commonjsRequire(path) {
    	throw new Error('Could not dynamically require "' + path + '". Please configure the dynamicRequireTargets or/and ignoreDynamicRequires option of @rollup/plugin-commonjs appropriately for this require call to work.');
    }

    var address = {};

    /*!
     * XRegExp 3.2.0
     * <xregexp.com>
     * Steven Levithan (c) 2007-2017 MIT License
     */

    var xregexp;
    var hasRequiredXregexp;

    function requireXregexp () {
    	if (hasRequiredXregexp) return xregexp;
    	hasRequiredXregexp = 1;

    	/**
    	 * XRegExp provides augmented, extensible regular expressions. You get additional regex syntax and
    	 * flags, beyond what browsers support natively. XRegExp is also a regex utility belt with tools to
    	 * make your client-side grepping simpler and more powerful, while freeing you from related
    	 * cross-browser inconsistencies.
    	 */

    	// ==--------------------------==
    	// Private stuff
    	// ==--------------------------==

    	// Property name used for extended regex instance data
    	var REGEX_DATA = 'xregexp';
    	// Optional features that can be installed and uninstalled
    	var features = {
    	    astral: false,
    	    natives: false
    	};
    	// Native methods to use and restore ('native' is an ES3 reserved keyword)
    	var nativ = {
    	    exec: RegExp.prototype.exec,
    	    test: RegExp.prototype.test,
    	    match: String.prototype.match,
    	    replace: String.prototype.replace,
    	    split: String.prototype.split
    	};
    	// Storage for fixed/extended native methods
    	var fixed = {};
    	// Storage for regexes cached by `XRegExp.cache`
    	var regexCache = {};
    	// Storage for pattern details cached by the `XRegExp` constructor
    	var patternCache = {};
    	// Storage for regex syntax tokens added internally or by `XRegExp.addToken`
    	var tokens = [];
    	// Token scopes
    	var defaultScope = 'default';
    	var classScope = 'class';
    	// Regexes that match native regex syntax, including octals
    	var nativeTokens = {
    	    // Any native multicharacter token in default scope, or any single character
    	    'default': /\\(?:0(?:[0-3][0-7]{0,2}|[4-7][0-7]?)?|[1-9]\d*|x[\dA-Fa-f]{2}|u(?:[\dA-Fa-f]{4}|{[\dA-Fa-f]+})|c[A-Za-z]|[\s\S])|\(\?(?:[:=!]|<[=!])|[?*+]\?|{\d+(?:,\d*)?}\??|[\s\S]/,
    	    // Any native multicharacter token in character class scope, or any single character
    	    'class': /\\(?:[0-3][0-7]{0,2}|[4-7][0-7]?|x[\dA-Fa-f]{2}|u(?:[\dA-Fa-f]{4}|{[\dA-Fa-f]+})|c[A-Za-z]|[\s\S])|[\s\S]/
    	};
    	// Any backreference or dollar-prefixed character in replacement strings
    	var replacementToken = /\$(?:{([\w$]+)}|(\d\d?|[\s\S]))/g;
    	// Check for correct `exec` handling of nonparticipating capturing groups
    	var correctExecNpcg = nativ.exec.call(/()??/, '')[1] === undefined;
    	// Check for ES6 `flags` prop support
    	var hasFlagsProp = /x/.flags !== undefined;
    	// Shortcut to `Object.prototype.toString`
    	var toString = {}.toString;

    	function hasNativeFlag(flag) {
    	    // Can't check based on the presence of properties/getters since browsers might support such
    	    // properties even when they don't support the corresponding flag in regex construction (tested
    	    // in Chrome 48, where `'unicode' in /x/` is true but trying to construct a regex with flag `u`
    	    // throws an error)
    	    var isSupported = true;
    	    try {
    	        // Can't use regex literals for testing even in a `try` because regex literals with
    	        // unsupported flags cause a compilation error in IE
    	        new RegExp('', flag);
    	    } catch (exception) {
    	        isSupported = false;
    	    }
    	    return isSupported;
    	}
    	// Check for ES6 `u` flag support
    	var hasNativeU = hasNativeFlag('u');
    	// Check for ES6 `y` flag support
    	var hasNativeY = hasNativeFlag('y');
    	// Tracker for known flags, including addon flags
    	var registeredFlags = {
    	    g: true,
    	    i: true,
    	    m: true,
    	    u: hasNativeU,
    	    y: hasNativeY
    	};

    	/**
    	 * Attaches extended data and `XRegExp.prototype` properties to a regex object.
    	 *
    	 * @private
    	 * @param {RegExp} regex Regex to augment.
    	 * @param {Array} captureNames Array with capture names, or `null`.
    	 * @param {String} xSource XRegExp pattern used to generate `regex`, or `null` if N/A.
    	 * @param {String} xFlags XRegExp flags used to generate `regex`, or `null` if N/A.
    	 * @param {Boolean} [isInternalOnly=false] Whether the regex will be used only for internal
    	 *   operations, and never exposed to users. For internal-only regexes, we can improve perf by
    	 *   skipping some operations like attaching `XRegExp.prototype` properties.
    	 * @returns {RegExp} Augmented regex.
    	 */
    	function augment(regex, captureNames, xSource, xFlags, isInternalOnly) {
    	    var p;

    	    regex[REGEX_DATA] = {
    	        captureNames: captureNames
    	    };

    	    if (isInternalOnly) {
    	        return regex;
    	    }

    	    // Can't auto-inherit these since the XRegExp constructor returns a nonprimitive value
    	    if (regex.__proto__) {
    	        regex.__proto__ = XRegExp.prototype;
    	    } else {
    	        for (p in XRegExp.prototype) {
    	            // An `XRegExp.prototype.hasOwnProperty(p)` check wouldn't be worth it here, since this
    	            // is performance sensitive, and enumerable `Object.prototype` or `RegExp.prototype`
    	            // extensions exist on `regex.prototype` anyway
    	            regex[p] = XRegExp.prototype[p];
    	        }
    	    }

    	    regex[REGEX_DATA].source = xSource;
    	    // Emulate the ES6 `flags` prop by ensuring flags are in alphabetical order
    	    regex[REGEX_DATA].flags = xFlags ? xFlags.split('').sort().join('') : xFlags;

    	    return regex;
    	}

    	/**
    	 * Removes any duplicate characters from the provided string.
    	 *
    	 * @private
    	 * @param {String} str String to remove duplicate characters from.
    	 * @returns {String} String with any duplicate characters removed.
    	 */
    	function clipDuplicates(str) {
    	    return nativ.replace.call(str, /([\s\S])(?=[\s\S]*\1)/g, '');
    	}

    	/**
    	 * Copies a regex object while preserving extended data and augmenting with `XRegExp.prototype`
    	 * properties. The copy has a fresh `lastIndex` property (set to zero). Allows adding and removing
    	 * flags g and y while copying the regex.
    	 *
    	 * @private
    	 * @param {RegExp} regex Regex to copy.
    	 * @param {Object} [options] Options object with optional properties:
    	 *   - `addG` {Boolean} Add flag g while copying the regex.
    	 *   - `addY` {Boolean} Add flag y while copying the regex.
    	 *   - `removeG` {Boolean} Remove flag g while copying the regex.
    	 *   - `removeY` {Boolean} Remove flag y while copying the regex.
    	 *   - `isInternalOnly` {Boolean} Whether the copied regex will be used only for internal
    	 *     operations, and never exposed to users. For internal-only regexes, we can improve perf by
    	 *     skipping some operations like attaching `XRegExp.prototype` properties.
    	 *   - `source` {String} Overrides `<regex>.source`, for special cases.
    	 * @returns {RegExp} Copy of the provided regex, possibly with modified flags.
    	 */
    	function copyRegex(regex, options) {
    	    if (!XRegExp.isRegExp(regex)) {
    	        throw new TypeError('Type RegExp expected');
    	    }

    	    var xData = regex[REGEX_DATA] || {};
    	    var flags = getNativeFlags(regex);
    	    var flagsToAdd = '';
    	    var flagsToRemove = '';
    	    var xregexpSource = null;
    	    var xregexpFlags = null;

    	    options = options || {};

    	    if (options.removeG) {flagsToRemove += 'g';}
    	    if (options.removeY) {flagsToRemove += 'y';}
    	    if (flagsToRemove) {
    	        flags = nativ.replace.call(flags, new RegExp('[' + flagsToRemove + ']+', 'g'), '');
    	    }

    	    if (options.addG) {flagsToAdd += 'g';}
    	    if (options.addY) {flagsToAdd += 'y';}
    	    if (flagsToAdd) {
    	        flags = clipDuplicates(flags + flagsToAdd);
    	    }

    	    if (!options.isInternalOnly) {
    	        if (xData.source !== undefined) {
    	            xregexpSource = xData.source;
    	        }
    	        // null or undefined; don't want to add to `flags` if the previous value was null, since
    	        // that indicates we're not tracking original precompilation flags
    	        if (xData.flags != null) {
    	            // Flags are only added for non-internal regexes by `XRegExp.globalize`. Flags are never
    	            // removed for non-internal regexes, so don't need to handle it
    	            xregexpFlags = flagsToAdd ? clipDuplicates(xData.flags + flagsToAdd) : xData.flags;
    	        }
    	    }

    	    // Augment with `XRegExp.prototype` properties, but use the native `RegExp` constructor to avoid
    	    // searching for special tokens. That would be wrong for regexes constructed by `RegExp`, and
    	    // unnecessary for regexes constructed by `XRegExp` because the regex has already undergone the
    	    // translation to native regex syntax
    	    regex = augment(
    	        new RegExp(options.source || regex.source, flags),
    	        hasNamedCapture(regex) ? xData.captureNames.slice(0) : null,
    	        xregexpSource,
    	        xregexpFlags,
    	        options.isInternalOnly
    	    );

    	    return regex;
    	}

    	/**
    	 * Converts hexadecimal to decimal.
    	 *
    	 * @private
    	 * @param {String} hex
    	 * @returns {Number}
    	 */
    	function dec(hex) {
    	    return parseInt(hex, 16);
    	}

    	/**
    	 * Returns a pattern that can be used in a native RegExp in place of an ignorable token such as an
    	 * inline comment or whitespace with flag x. This is used directly as a token handler function
    	 * passed to `XRegExp.addToken`.
    	 *
    	 * @private
    	 * @param {String} match Match arg of `XRegExp.addToken` handler
    	 * @param {String} scope Scope arg of `XRegExp.addToken` handler
    	 * @param {String} flags Flags arg of `XRegExp.addToken` handler
    	 * @returns {String} Either '' or '(?:)', depending on which is needed in the context of the match.
    	 */
    	function getContextualTokenSeparator(match, scope, flags) {
    	    if (
    	        // No need to separate tokens if at the beginning or end of a group
    	        match.input.charAt(match.index - 1) === '(' ||
    	        match.input.charAt(match.index + match[0].length) === ')' ||
    	        // Avoid separating tokens when the following token is a quantifier
    	        isPatternNext(match.input, match.index + match[0].length, flags, '[?*+]|{\\d+(?:,\\d*)?}')
    	    ) {
    	        return '';
    	    }
    	    // Keep tokens separated. This avoids e.g. inadvertedly changing `\1 1` or `\1(?#)1` to `\11`.
    	    // This also ensures all tokens remain as discrete atoms, e.g. it avoids converting the syntax
    	    // error `(? :` into `(?:`.
    	    return '(?:)';
    	}

    	/**
    	 * Returns native `RegExp` flags used by a regex object.
    	 *
    	 * @private
    	 * @param {RegExp} regex Regex to check.
    	 * @returns {String} Native flags in use.
    	 */
    	function getNativeFlags(regex) {
    	    return hasFlagsProp ?
    	        regex.flags :
    	        // Explicitly using `RegExp.prototype.toString` (rather than e.g. `String` or concatenation
    	        // with an empty string) allows this to continue working predictably when
    	        // `XRegExp.proptotype.toString` is overridden
    	        nativ.exec.call(/\/([a-z]*)$/i, RegExp.prototype.toString.call(regex))[1];
    	}

    	/**
    	 * Determines whether a regex has extended instance data used to track capture names.
    	 *
    	 * @private
    	 * @param {RegExp} regex Regex to check.
    	 * @returns {Boolean} Whether the regex uses named capture.
    	 */
    	function hasNamedCapture(regex) {
    	    return !!(regex[REGEX_DATA] && regex[REGEX_DATA].captureNames);
    	}

    	/**
    	 * Converts decimal to hexadecimal.
    	 *
    	 * @private
    	 * @param {Number|String} dec
    	 * @returns {String}
    	 */
    	function hex(dec) {
    	    return parseInt(dec, 10).toString(16);
    	}

    	/**
    	 * Returns the first index at which a given value can be found in an array.
    	 *
    	 * @private
    	 * @param {Array} array Array to search.
    	 * @param {*} value Value to locate in the array.
    	 * @returns {Number} Zero-based index at which the item is found, or -1.
    	 */
    	function indexOf(array, value) {
    	    var len = array.length;
    	    var i;

    	    for (i = 0; i < len; ++i) {
    	        if (array[i] === value) {
    	            return i;
    	        }
    	    }

    	    return -1;
    	}

    	/**
    	 * Checks whether the next nonignorable token after the specified position matches the
    	 * `needlePattern`
    	 *
    	 * @private
    	 * @param {String} pattern Pattern to search within.
    	 * @param {Number} pos Index in `pattern` to search at.
    	 * @param {String} flags Flags used by the pattern.
    	 * @param {String} needlePattern Pattern to match the next token against.
    	 * @returns {Boolean} Whether the next nonignorable token matches `needlePattern`
    	 */
    	function isPatternNext(pattern, pos, flags, needlePattern) {
    	    var inlineCommentPattern = '\\(\\?#[^)]*\\)';
    	    var lineCommentPattern = '#[^#\\n]*';
    	    var patternsToIgnore = flags.indexOf('x') > -1 ?
    	        // Ignore any leading whitespace, line comments, and inline comments
    	        ['\\s', lineCommentPattern, inlineCommentPattern] :
    	        // Ignore any leading inline comments
    	        [inlineCommentPattern];
    	    return nativ.test.call(
    	        new RegExp('^(?:' + patternsToIgnore.join('|') + ')*(?:' + needlePattern + ')'),
    	        pattern.slice(pos)
    	    );
    	}

    	/**
    	 * Determines whether a value is of the specified type, by resolving its internal [[Class]].
    	 *
    	 * @private
    	 * @param {*} value Object to check.
    	 * @param {String} type Type to check for, in TitleCase.
    	 * @returns {Boolean} Whether the object matches the type.
    	 */
    	function isType(value, type) {
    	    return toString.call(value) === '[object ' + type + ']';
    	}

    	/**
    	 * Adds leading zeros if shorter than four characters. Used for fixed-length hexadecimal values.
    	 *
    	 * @private
    	 * @param {String} str
    	 * @returns {String}
    	 */
    	function pad4(str) {
    	    while (str.length < 4) {
    	        str = '0' + str;
    	    }
    	    return str;
    	}

    	/**
    	 * Checks for flag-related errors, and strips/applies flags in a leading mode modifier. Offloads
    	 * the flag preparation logic from the `XRegExp` constructor.
    	 *
    	 * @private
    	 * @param {String} pattern Regex pattern, possibly with a leading mode modifier.
    	 * @param {String} flags Any combination of flags.
    	 * @returns {Object} Object with properties `pattern` and `flags`.
    	 */
    	function prepareFlags(pattern, flags) {
    	    var i;

    	    // Recent browsers throw on duplicate flags, so copy this behavior for nonnative flags
    	    if (clipDuplicates(flags) !== flags) {
    	        throw new SyntaxError('Invalid duplicate regex flag ' + flags);
    	    }

    	    // Strip and apply a leading mode modifier with any combination of flags except g or y
    	    pattern = nativ.replace.call(pattern, /^\(\?([\w$]+)\)/, function($0, $1) {
    	        if (nativ.test.call(/[gy]/, $1)) {
    	            throw new SyntaxError('Cannot use flag g or y in mode modifier ' + $0);
    	        }
    	        // Allow duplicate flags within the mode modifier
    	        flags = clipDuplicates(flags + $1);
    	        return '';
    	    });

    	    // Throw on unknown native or nonnative flags
    	    for (i = 0; i < flags.length; ++i) {
    	        if (!registeredFlags[flags.charAt(i)]) {
    	            throw new SyntaxError('Unknown regex flag ' + flags.charAt(i));
    	        }
    	    }

    	    return {
    	        pattern: pattern,
    	        flags: flags
    	    };
    	}

    	/**
    	 * Prepares an options object from the given value.
    	 *
    	 * @private
    	 * @param {String|Object} value Value to convert to an options object.
    	 * @returns {Object} Options object.
    	 */
    	function prepareOptions(value) {
    	    var options = {};

    	    if (isType(value, 'String')) {
    	        XRegExp.forEach(value, /[^\s,]+/, function(match) {
    	            options[match] = true;
    	        });

    	        return options;
    	    }

    	    return value;
    	}

    	/**
    	 * Registers a flag so it doesn't throw an 'unknown flag' error.
    	 *
    	 * @private
    	 * @param {String} flag Single-character flag to register.
    	 */
    	function registerFlag(flag) {
    	    if (!/^[\w$]$/.test(flag)) {
    	        throw new Error('Flag must be a single character A-Za-z0-9_$');
    	    }

    	    registeredFlags[flag] = true;
    	}

    	/**
    	 * Runs built-in and custom regex syntax tokens in reverse insertion order at the specified
    	 * position, until a match is found.
    	 *
    	 * @private
    	 * @param {String} pattern Original pattern from which an XRegExp object is being built.
    	 * @param {String} flags Flags being used to construct the regex.
    	 * @param {Number} pos Position to search for tokens within `pattern`.
    	 * @param {Number} scope Regex scope to apply: 'default' or 'class'.
    	 * @param {Object} context Context object to use for token handler functions.
    	 * @returns {Object} Object with properties `matchLength`, `output`, and `reparse`; or `null`.
    	 */
    	function runTokens(pattern, flags, pos, scope, context) {
    	    var i = tokens.length;
    	    var leadChar = pattern.charAt(pos);
    	    var result = null;
    	    var match;
    	    var t;

    	    // Run in reverse insertion order
    	    while (i--) {
    	        t = tokens[i];
    	        if (
    	            (t.leadChar && t.leadChar !== leadChar) ||
    	            (t.scope !== scope && t.scope !== 'all') ||
    	            (t.flag && flags.indexOf(t.flag) === -1)
    	        ) {
    	            continue;
    	        }

    	        match = XRegExp.exec(pattern, t.regex, pos, 'sticky');
    	        if (match) {
    	            result = {
    	                matchLength: match[0].length,
    	                output: t.handler.call(context, match, scope, flags),
    	                reparse: t.reparse
    	            };
    	            // Finished with token tests
    	            break;
    	        }
    	    }

    	    return result;
    	}

    	/**
    	 * Enables or disables implicit astral mode opt-in. When enabled, flag A is automatically added to
    	 * all new regexes created by XRegExp. This causes an error to be thrown when creating regexes if
    	 * the Unicode Base addon is not available, since flag A is registered by that addon.
    	 *
    	 * @private
    	 * @param {Boolean} on `true` to enable; `false` to disable.
    	 */
    	function setAstral(on) {
    	    features.astral = on;
    	}

    	/**
    	 * Enables or disables native method overrides.
    	 *
    	 * @private
    	 * @param {Boolean} on `true` to enable; `false` to disable.
    	 */
    	function setNatives(on) {
    	    RegExp.prototype.exec = (on ? fixed : nativ).exec;
    	    RegExp.prototype.test = (on ? fixed : nativ).test;
    	    String.prototype.match = (on ? fixed : nativ).match;
    	    String.prototype.replace = (on ? fixed : nativ).replace;
    	    String.prototype.split = (on ? fixed : nativ).split;

    	    features.natives = on;
    	}

    	/**
    	 * Returns the object, or throws an error if it is `null` or `undefined`. This is used to follow
    	 * the ES5 abstract operation `ToObject`.
    	 *
    	 * @private
    	 * @param {*} value Object to check and return.
    	 * @returns {*} The provided object.
    	 */
    	function toObject(value) {
    	    // null or undefined
    	    if (value == null) {
    	        throw new TypeError('Cannot convert null or undefined to object');
    	    }

    	    return value;
    	}

    	// ==--------------------------==
    	// Constructor
    	// ==--------------------------==

    	/**
    	 * Creates an extended regular expression object for matching text with a pattern. Differs from a
    	 * native regular expression in that additional syntax and flags are supported. The returned object
    	 * is in fact a native `RegExp` and works with all native methods.
    	 *
    	 * @class XRegExp
    	 * @constructor
    	 * @param {String|RegExp} pattern Regex pattern string, or an existing regex object to copy.
    	 * @param {String} [flags] Any combination of flags.
    	 *   Native flags:
    	 *     - `g` - global
    	 *     - `i` - ignore case
    	 *     - `m` - multiline anchors
    	 *     - `u` - unicode (ES6)
    	 *     - `y` - sticky (Firefox 3+, ES6)
    	 *   Additional XRegExp flags:
    	 *     - `n` - explicit capture
    	 *     - `s` - dot matches all (aka singleline)
    	 *     - `x` - free-spacing and line comments (aka extended)
    	 *     - `A` - astral (requires the Unicode Base addon)
    	 *   Flags cannot be provided when constructing one `RegExp` from another.
    	 * @returns {RegExp} Extended regular expression object.
    	 * @example
    	 *
    	 * // With named capture and flag x
    	 * XRegExp('(?<year>  [0-9]{4} ) -?  # year  \n\
    	 *          (?<month> [0-9]{2} ) -?  # month \n\
    	 *          (?<day>   [0-9]{2} )     # day   ', 'x');
    	 *
    	 * // Providing a regex object copies it. Native regexes are recompiled using native (not XRegExp)
    	 * // syntax. Copies maintain extended data, are augmented with `XRegExp.prototype` properties, and
    	 * // have fresh `lastIndex` properties (set to zero).
    	 * XRegExp(/regex/);
    	 */
    	function XRegExp(pattern, flags) {
    	    if (XRegExp.isRegExp(pattern)) {
    	        if (flags !== undefined) {
    	            throw new TypeError('Cannot supply flags when copying a RegExp');
    	        }
    	        return copyRegex(pattern);
    	    }

    	    // Copy the argument behavior of `RegExp`
    	    pattern = pattern === undefined ? '' : String(pattern);
    	    flags = flags === undefined ? '' : String(flags);

    	    if (XRegExp.isInstalled('astral') && flags.indexOf('A') === -1) {
    	        // This causes an error to be thrown if the Unicode Base addon is not available
    	        flags += 'A';
    	    }

    	    if (!patternCache[pattern]) {
    	        patternCache[pattern] = {};
    	    }

    	    if (!patternCache[pattern][flags]) {
    	        var context = {
    	            hasNamedCapture: false,
    	            captureNames: []
    	        };
    	        var scope = defaultScope;
    	        var output = '';
    	        var pos = 0;
    	        var result;

    	        // Check for flag-related errors, and strip/apply flags in a leading mode modifier
    	        var applied = prepareFlags(pattern, flags);
    	        var appliedPattern = applied.pattern;
    	        var appliedFlags = applied.flags;

    	        // Use XRegExp's tokens to translate the pattern to a native regex pattern.
    	        // `appliedPattern.length` may change on each iteration if tokens use `reparse`
    	        while (pos < appliedPattern.length) {
    	            do {
    	                // Check for custom tokens at the current position
    	                result = runTokens(appliedPattern, appliedFlags, pos, scope, context);
    	                // If the matched token used the `reparse` option, splice its output into the
    	                // pattern before running tokens again at the same position
    	                if (result && result.reparse) {
    	                    appliedPattern = appliedPattern.slice(0, pos) +
    	                        result.output +
    	                        appliedPattern.slice(pos + result.matchLength);
    	                }
    	            } while (result && result.reparse);

    	            if (result) {
    	                output += result.output;
    	                pos += (result.matchLength || 1);
    	            } else {
    	                // Get the native token at the current position
    	                var token = XRegExp.exec(appliedPattern, nativeTokens[scope], pos, 'sticky')[0];
    	                output += token;
    	                pos += token.length;
    	                if (token === '[' && scope === defaultScope) {
    	                    scope = classScope;
    	                } else if (token === ']' && scope === classScope) {
    	                    scope = defaultScope;
    	                }
    	            }
    	        }

    	        patternCache[pattern][flags] = {
    	            // Use basic cleanup to collapse repeated empty groups like `(?:)(?:)` to `(?:)`. Empty
    	            // groups are sometimes inserted during regex transpilation in order to keep tokens
    	            // separated. However, more than one empty group in a row is never needed.
    	            pattern: nativ.replace.call(output, /(?:\(\?:\))+/g, '(?:)'),
    	            // Strip all but native flags
    	            flags: nativ.replace.call(appliedFlags, /[^gimuy]+/g, ''),
    	            // `context.captureNames` has an item for each capturing group, even if unnamed
    	            captures: context.hasNamedCapture ? context.captureNames : null
    	        };
    	    }

    	    var generated = patternCache[pattern][flags];
    	    return augment(
    	        new RegExp(generated.pattern, generated.flags),
    	        generated.captures,
    	        pattern,
    	        flags
    	    );
    	}

    	// Add `RegExp.prototype` to the prototype chain
    	XRegExp.prototype = new RegExp();

    	// ==--------------------------==
    	// Public properties
    	// ==--------------------------==

    	/**
    	 * The XRegExp version number as a string containing three dot-separated parts. For example,
    	 * '2.0.0-beta-3'.
    	 *
    	 * @static
    	 * @memberOf XRegExp
    	 * @type String
    	 */
    	XRegExp.version = '3.2.0';

    	// ==--------------------------==
    	// Public methods
    	// ==--------------------------==

    	// Intentionally undocumented; used in tests and addons
    	XRegExp._clipDuplicates = clipDuplicates;
    	XRegExp._hasNativeFlag = hasNativeFlag;
    	XRegExp._dec = dec;
    	XRegExp._hex = hex;
    	XRegExp._pad4 = pad4;

    	/**
    	 * Extends XRegExp syntax and allows custom flags. This is used internally and can be used to
    	 * create XRegExp addons. If more than one token can match the same string, the last added wins.
    	 *
    	 * @memberOf XRegExp
    	 * @param {RegExp} regex Regex object that matches the new token.
    	 * @param {Function} handler Function that returns a new pattern string (using native regex syntax)
    	 *   to replace the matched token within all future XRegExp regexes. Has access to persistent
    	 *   properties of the regex being built, through `this`. Invoked with three arguments:
    	 *   - The match array, with named backreference properties.
    	 *   - The regex scope where the match was found: 'default' or 'class'.
    	 *   - The flags used by the regex, including any flags in a leading mode modifier.
    	 *   The handler function becomes part of the XRegExp construction process, so be careful not to
    	 *   construct XRegExps within the function or you will trigger infinite recursion.
    	 * @param {Object} [options] Options object with optional properties:
    	 *   - `scope` {String} Scope where the token applies: 'default', 'class', or 'all'.
    	 *   - `flag` {String} Single-character flag that triggers the token. This also registers the
    	 *     flag, which prevents XRegExp from throwing an 'unknown flag' error when the flag is used.
    	 *   - `optionalFlags` {String} Any custom flags checked for within the token `handler` that are
    	 *     not required to trigger the token. This registers the flags, to prevent XRegExp from
    	 *     throwing an 'unknown flag' error when any of the flags are used.
    	 *   - `reparse` {Boolean} Whether the `handler` function's output should not be treated as
    	 *     final, and instead be reparseable by other tokens (including the current token). Allows
    	 *     token chaining or deferring.
    	 *   - `leadChar` {String} Single character that occurs at the beginning of any successful match
    	 *     of the token (not always applicable). This doesn't change the behavior of the token unless
    	 *     you provide an erroneous value. However, providing it can increase the token's performance
    	 *     since the token can be skipped at any positions where this character doesn't appear.
    	 * @example
    	 *
    	 * // Basic usage: Add \a for the ALERT control code
    	 * XRegExp.addToken(
    	 *   /\\a/,
    	 *   function() {return '\\x07';},
    	 *   {scope: 'all'}
    	 * );
    	 * XRegExp('\\a[\\a-\\n]+').test('\x07\n\x07'); // -> true
    	 *
    	 * // Add the U (ungreedy) flag from PCRE and RE2, which reverses greedy and lazy quantifiers.
    	 * // Since `scope` is not specified, it uses 'default' (i.e., transformations apply outside of
    	 * // character classes only)
    	 * XRegExp.addToken(
    	 *   /([?*+]|{\d+(?:,\d*)?})(\??)/,
    	 *   function(match) {return match[1] + (match[2] ? '' : '?');},
    	 *   {flag: 'U'}
    	 * );
    	 * XRegExp('a+', 'U').exec('aaa')[0]; // -> 'a'
    	 * XRegExp('a+?', 'U').exec('aaa')[0]; // -> 'aaa'
    	 */
    	XRegExp.addToken = function(regex, handler, options) {
    	    options = options || {};
    	    var optionalFlags = options.optionalFlags;
    	    var i;

    	    if (options.flag) {
    	        registerFlag(options.flag);
    	    }

    	    if (optionalFlags) {
    	        optionalFlags = nativ.split.call(optionalFlags, '');
    	        for (i = 0; i < optionalFlags.length; ++i) {
    	            registerFlag(optionalFlags[i]);
    	        }
    	    }

    	    // Add to the private list of syntax tokens
    	    tokens.push({
    	        regex: copyRegex(regex, {
    	            addG: true,
    	            addY: hasNativeY,
    	            isInternalOnly: true
    	        }),
    	        handler: handler,
    	        scope: options.scope || defaultScope,
    	        flag: options.flag,
    	        reparse: options.reparse,
    	        leadChar: options.leadChar
    	    });

    	    // Reset the pattern cache used by the `XRegExp` constructor, since the same pattern and flags
    	    // might now produce different results
    	    XRegExp.cache.flush('patterns');
    	};

    	/**
    	 * Caches and returns the result of calling `XRegExp(pattern, flags)`. On any subsequent call with
    	 * the same pattern and flag combination, the cached copy of the regex is returned.
    	 *
    	 * @memberOf XRegExp
    	 * @param {String} pattern Regex pattern string.
    	 * @param {String} [flags] Any combination of XRegExp flags.
    	 * @returns {RegExp} Cached XRegExp object.
    	 * @example
    	 *
    	 * while (match = XRegExp.cache('.', 'gs').exec(str)) {
    	 *   // The regex is compiled once only
    	 * }
    	 */
    	XRegExp.cache = function(pattern, flags) {
    	    if (!regexCache[pattern]) {
    	        regexCache[pattern] = {};
    	    }
    	    return regexCache[pattern][flags] || (
    	        regexCache[pattern][flags] = XRegExp(pattern, flags)
    	    );
    	};

    	// Intentionally undocumented; used in tests
    	XRegExp.cache.flush = function(cacheName) {
    	    if (cacheName === 'patterns') {
    	        // Flush the pattern cache used by the `XRegExp` constructor
    	        patternCache = {};
    	    } else {
    	        // Flush the regex cache populated by `XRegExp.cache`
    	        regexCache = {};
    	    }
    	};

    	/**
    	 * Escapes any regular expression metacharacters, for use when matching literal strings. The result
    	 * can safely be used at any point within a regex that uses any flags.
    	 *
    	 * @memberOf XRegExp
    	 * @param {String} str String to escape.
    	 * @returns {String} String with regex metacharacters escaped.
    	 * @example
    	 *
    	 * XRegExp.escape('Escaped? <.>');
    	 * // -> 'Escaped\?\ <\.>'
    	 */
    	XRegExp.escape = function(str) {
    	    return nativ.replace.call(toObject(str), /[-\[\]{}()*+?.,\\^$|#\s]/g, '\\$&');
    	};

    	/**
    	 * Executes a regex search in a specified string. Returns a match array or `null`. If the provided
    	 * regex uses named capture, named backreference properties are included on the match array.
    	 * Optional `pos` and `sticky` arguments specify the search start position, and whether the match
    	 * must start at the specified position only. The `lastIndex` property of the provided regex is not
    	 * used, but is updated for compatibility. Also fixes browser bugs compared to the native
    	 * `RegExp.prototype.exec` and can be used reliably cross-browser.
    	 *
    	 * @memberOf XRegExp
    	 * @param {String} str String to search.
    	 * @param {RegExp} regex Regex to search with.
    	 * @param {Number} [pos=0] Zero-based index at which to start the search.
    	 * @param {Boolean|String} [sticky=false] Whether the match must start at the specified position
    	 *   only. The string `'sticky'` is accepted as an alternative to `true`.
    	 * @returns {Array} Match array with named backreference properties, or `null`.
    	 * @example
    	 *
    	 * // Basic use, with named backreference
    	 * var match = XRegExp.exec('U+2620', XRegExp('U\\+(?<hex>[0-9A-F]{4})'));
    	 * match.hex; // -> '2620'
    	 *
    	 * // With pos and sticky, in a loop
    	 * var pos = 2, result = [], match;
    	 * while (match = XRegExp.exec('<1><2><3><4>5<6>', /<(\d)>/, pos, 'sticky')) {
    	 *   result.push(match[1]);
    	 *   pos = match.index + match[0].length;
    	 * }
    	 * // result -> ['2', '3', '4']
    	 */
    	XRegExp.exec = function(str, regex, pos, sticky) {
    	    var cacheKey = 'g';
    	    var addY = false;
    	    var fakeY = false;
    	    var match;
    	    var r2;

    	    addY = hasNativeY && !!(sticky || (regex.sticky && sticky !== false));
    	    if (addY) {
    	        cacheKey += 'y';
    	    } else if (sticky) {
    	        // Simulate sticky matching by appending an empty capture to the original regex. The
    	        // resulting regex will succeed no matter what at the current index (set with `lastIndex`),
    	        // and will not search the rest of the subject string. We'll know that the original regex
    	        // has failed if that last capture is `''` rather than `undefined` (i.e., if that last
    	        // capture participated in the match).
    	        fakeY = true;
    	        cacheKey += 'FakeY';
    	    }

    	    regex[REGEX_DATA] = regex[REGEX_DATA] || {};

    	    // Shares cached copies with `XRegExp.match`/`replace`
    	    r2 = regex[REGEX_DATA][cacheKey] || (
    	        regex[REGEX_DATA][cacheKey] = copyRegex(regex, {
    	            addG: true,
    	            addY: addY,
    	            source: fakeY ? regex.source + '|()' : undefined,
    	            removeY: sticky === false,
    	            isInternalOnly: true
    	        })
    	    );

    	    pos = pos || 0;
    	    r2.lastIndex = pos;

    	    // Fixed `exec` required for `lastIndex` fix, named backreferences, etc.
    	    match = fixed.exec.call(r2, str);

    	    // Get rid of the capture added by the pseudo-sticky matcher if needed. An empty string means
    	    // the original regexp failed (see above).
    	    if (fakeY && match && match.pop() === '') {
    	        match = null;
    	    }

    	    if (regex.global) {
    	        regex.lastIndex = match ? r2.lastIndex : 0;
    	    }

    	    return match;
    	};

    	/**
    	 * Executes a provided function once per regex match. Searches always start at the beginning of the
    	 * string and continue until the end, regardless of the state of the regex's `global` property and
    	 * initial `lastIndex`.
    	 *
    	 * @memberOf XRegExp
    	 * @param {String} str String to search.
    	 * @param {RegExp} regex Regex to search with.
    	 * @param {Function} callback Function to execute for each match. Invoked with four arguments:
    	 *   - The match array, with named backreference properties.
    	 *   - The zero-based match index.
    	 *   - The string being traversed.
    	 *   - The regex object being used to traverse the string.
    	 * @example
    	 *
    	 * // Extracts every other digit from a string
    	 * var evens = [];
    	 * XRegExp.forEach('1a2345', /\d/, function(match, i) {
    	 *   if (i % 2) evens.push(+match[0]);
    	 * });
    	 * // evens -> [2, 4]
    	 */
    	XRegExp.forEach = function(str, regex, callback) {
    	    var pos = 0;
    	    var i = -1;
    	    var match;

    	    while ((match = XRegExp.exec(str, regex, pos))) {
    	        // Because `regex` is provided to `callback`, the function could use the deprecated/
    	        // nonstandard `RegExp.prototype.compile` to mutate the regex. However, since `XRegExp.exec`
    	        // doesn't use `lastIndex` to set the search position, this can't lead to an infinite loop,
    	        // at least. Actually, because of the way `XRegExp.exec` caches globalized versions of
    	        // regexes, mutating the regex will not have any effect on the iteration or matched strings,
    	        // which is a nice side effect that brings extra safety.
    	        callback(match, ++i, str, regex);

    	        pos = match.index + (match[0].length || 1);
    	    }
    	};

    	/**
    	 * Copies a regex object and adds flag `g`. The copy maintains extended data, is augmented with
    	 * `XRegExp.prototype` properties, and has a fresh `lastIndex` property (set to zero). Native
    	 * regexes are not recompiled using XRegExp syntax.
    	 *
    	 * @memberOf XRegExp
    	 * @param {RegExp} regex Regex to globalize.
    	 * @returns {RegExp} Copy of the provided regex with flag `g` added.
    	 * @example
    	 *
    	 * var globalCopy = XRegExp.globalize(/regex/);
    	 * globalCopy.global; // -> true
    	 */
    	XRegExp.globalize = function(regex) {
    	    return copyRegex(regex, {addG: true});
    	};

    	/**
    	 * Installs optional features according to the specified options. Can be undone using
    	 * `XRegExp.uninstall`.
    	 *
    	 * @memberOf XRegExp
    	 * @param {Object|String} options Options object or string.
    	 * @example
    	 *
    	 * // With an options object
    	 * XRegExp.install({
    	 *   // Enables support for astral code points in Unicode addons (implicitly sets flag A)
    	 *   astral: true,
    	 *
    	 *   // DEPRECATED: Overrides native regex methods with fixed/extended versions
    	 *   natives: true
    	 * });
    	 *
    	 * // With an options string
    	 * XRegExp.install('astral natives');
    	 */
    	XRegExp.install = function(options) {
    	    options = prepareOptions(options);

    	    if (!features.astral && options.astral) {
    	        setAstral(true);
    	    }

    	    if (!features.natives && options.natives) {
    	        setNatives(true);
    	    }
    	};

    	/**
    	 * Checks whether an individual optional feature is installed.
    	 *
    	 * @memberOf XRegExp
    	 * @param {String} feature Name of the feature to check. One of:
    	 *   - `astral`
    	 *   - `natives`
    	 * @returns {Boolean} Whether the feature is installed.
    	 * @example
    	 *
    	 * XRegExp.isInstalled('astral');
    	 */
    	XRegExp.isInstalled = function(feature) {
    	    return !!(features[feature]);
    	};

    	/**
    	 * Returns `true` if an object is a regex; `false` if it isn't. This works correctly for regexes
    	 * created in another frame, when `instanceof` and `constructor` checks would fail.
    	 *
    	 * @memberOf XRegExp
    	 * @param {*} value Object to check.
    	 * @returns {Boolean} Whether the object is a `RegExp` object.
    	 * @example
    	 *
    	 * XRegExp.isRegExp('string'); // -> false
    	 * XRegExp.isRegExp(/regex/i); // -> true
    	 * XRegExp.isRegExp(RegExp('^', 'm')); // -> true
    	 * XRegExp.isRegExp(XRegExp('(?s).')); // -> true
    	 */
    	XRegExp.isRegExp = function(value) {
    	    return toString.call(value) === '[object RegExp]';
    	    //return isType(value, 'RegExp');
    	};

    	/**
    	 * Returns the first matched string, or in global mode, an array containing all matched strings.
    	 * This is essentially a more convenient re-implementation of `String.prototype.match` that gives
    	 * the result types you actually want (string instead of `exec`-style array in match-first mode,
    	 * and an empty array instead of `null` when no matches are found in match-all mode). It also lets
    	 * you override flag g and ignore `lastIndex`, and fixes browser bugs.
    	 *
    	 * @memberOf XRegExp
    	 * @param {String} str String to search.
    	 * @param {RegExp} regex Regex to search with.
    	 * @param {String} [scope='one'] Use 'one' to return the first match as a string. Use 'all' to
    	 *   return an array of all matched strings. If not explicitly specified and `regex` uses flag g,
    	 *   `scope` is 'all'.
    	 * @returns {String|Array} In match-first mode: First match as a string, or `null`. In match-all
    	 *   mode: Array of all matched strings, or an empty array.
    	 * @example
    	 *
    	 * // Match first
    	 * XRegExp.match('abc', /\w/); // -> 'a'
    	 * XRegExp.match('abc', /\w/g, 'one'); // -> 'a'
    	 * XRegExp.match('abc', /x/g, 'one'); // -> null
    	 *
    	 * // Match all
    	 * XRegExp.match('abc', /\w/g); // -> ['a', 'b', 'c']
    	 * XRegExp.match('abc', /\w/, 'all'); // -> ['a', 'b', 'c']
    	 * XRegExp.match('abc', /x/, 'all'); // -> []
    	 */
    	XRegExp.match = function(str, regex, scope) {
    	    var global = (regex.global && scope !== 'one') || scope === 'all';
    	    var cacheKey = ((global ? 'g' : '') + (regex.sticky ? 'y' : '')) || 'noGY';
    	    var result;
    	    var r2;

    	    regex[REGEX_DATA] = regex[REGEX_DATA] || {};

    	    // Shares cached copies with `XRegExp.exec`/`replace`
    	    r2 = regex[REGEX_DATA][cacheKey] || (
    	        regex[REGEX_DATA][cacheKey] = copyRegex(regex, {
    	            addG: !!global,
    	            removeG: scope === 'one',
    	            isInternalOnly: true
    	        })
    	    );

    	    result = nativ.match.call(toObject(str), r2);

    	    if (regex.global) {
    	        regex.lastIndex = (
    	            (scope === 'one' && result) ?
    	                // Can't use `r2.lastIndex` since `r2` is nonglobal in this case
    	                (result.index + result[0].length) : 0
    	        );
    	    }

    	    return global ? (result || []) : (result && result[0]);
    	};

    	/**
    	 * Retrieves the matches from searching a string using a chain of regexes that successively search
    	 * within previous matches. The provided `chain` array can contain regexes and or objects with
    	 * `regex` and `backref` properties. When a backreference is specified, the named or numbered
    	 * backreference is passed forward to the next regex or returned.
    	 *
    	 * @memberOf XRegExp
    	 * @param {String} str String to search.
    	 * @param {Array} chain Regexes that each search for matches within preceding results.
    	 * @returns {Array} Matches by the last regex in the chain, or an empty array.
    	 * @example
    	 *
    	 * // Basic usage; matches numbers within <b> tags
    	 * XRegExp.matchChain('1 <b>2</b> 3 <b>4 a 56</b>', [
    	 *   XRegExp('(?is)<b>.*?</b>'),
    	 *   /\d+/
    	 * ]);
    	 * // -> ['2', '4', '56']
    	 *
    	 * // Passing forward and returning specific backreferences
    	 * html = '<a href="http://xregexp.com/api/">XRegExp</a>\
    	 *         <a href="http://www.google.com/">Google</a>';
    	 * XRegExp.matchChain(html, [
    	 *   {regex: /<a href="([^"]+)">/i, backref: 1},
    	 *   {regex: XRegExp('(?i)^https?://(?<domain>[^/?#]+)'), backref: 'domain'}
    	 * ]);
    	 * // -> ['xregexp.com', 'www.google.com']
    	 */
    	XRegExp.matchChain = function(str, chain) {
    	    return (function recurseChain(values, level) {
    	        var item = chain[level].regex ? chain[level] : {regex: chain[level]};
    	        var matches = [];

    	        function addMatch(match) {
    	            if (item.backref) {
    	                // Safari 4.0.5 (but not 5.0.5+) inappropriately uses sparse arrays to hold the
    	                // `undefined`s for backreferences to nonparticipating capturing groups. In such
    	                // cases, a `hasOwnProperty` or `in` check on its own would inappropriately throw
    	                // the exception, so also check if the backreference is a number that is within the
    	                // bounds of the array.
    	                if (!(match.hasOwnProperty(item.backref) || +item.backref < match.length)) {
    	                    throw new ReferenceError('Backreference to undefined group: ' + item.backref);
    	                }

    	                matches.push(match[item.backref] || '');
    	            } else {
    	                matches.push(match[0]);
    	            }
    	        }

    	        for (var i = 0; i < values.length; ++i) {
    	            XRegExp.forEach(values[i], item.regex, addMatch);
    	        }

    	        return ((level === chain.length - 1) || !matches.length) ?
    	            matches :
    	            recurseChain(matches, level + 1);
    	    }([str], 0));
    	};

    	/**
    	 * Returns a new string with one or all matches of a pattern replaced. The pattern can be a string
    	 * or regex, and the replacement can be a string or a function to be called for each match. To
    	 * perform a global search and replace, use the optional `scope` argument or include flag g if using
    	 * a regex. Replacement strings can use `${n}` for named and numbered backreferences. Replacement
    	 * functions can use named backreferences via `arguments[0].name`. Also fixes browser bugs compared
    	 * to the native `String.prototype.replace` and can be used reliably cross-browser.
    	 *
    	 * @memberOf XRegExp
    	 * @param {String} str String to search.
    	 * @param {RegExp|String} search Search pattern to be replaced.
    	 * @param {String|Function} replacement Replacement string or a function invoked to create it.
    	 *   Replacement strings can include special replacement syntax:
    	 *     - $$ - Inserts a literal $ character.
    	 *     - $&, $0 - Inserts the matched substring.
    	 *     - $` - Inserts the string that precedes the matched substring (left context).
    	 *     - $' - Inserts the string that follows the matched substring (right context).
    	 *     - $n, $nn - Where n/nn are digits referencing an existent capturing group, inserts
    	 *       backreference n/nn.
    	 *     - ${n} - Where n is a name or any number of digits that reference an existent capturing
    	 *       group, inserts backreference n.
    	 *   Replacement functions are invoked with three or more arguments:
    	 *     - The matched substring (corresponds to $& above). Named backreferences are accessible as
    	 *       properties of this first argument.
    	 *     - 0..n arguments, one for each backreference (corresponding to $1, $2, etc. above).
    	 *     - The zero-based index of the match within the total search string.
    	 *     - The total string being searched.
    	 * @param {String} [scope='one'] Use 'one' to replace the first match only, or 'all'. If not
    	 *   explicitly specified and using a regex with flag g, `scope` is 'all'.
    	 * @returns {String} New string with one or all matches replaced.
    	 * @example
    	 *
    	 * // Regex search, using named backreferences in replacement string
    	 * var name = XRegExp('(?<first>\\w+) (?<last>\\w+)');
    	 * XRegExp.replace('John Smith', name, '${last}, ${first}');
    	 * // -> 'Smith, John'
    	 *
    	 * // Regex search, using named backreferences in replacement function
    	 * XRegExp.replace('John Smith', name, function(match) {
    	 *   return match.last + ', ' + match.first;
    	 * });
    	 * // -> 'Smith, John'
    	 *
    	 * // String search, with replace-all
    	 * XRegExp.replace('RegExp builds RegExps', 'RegExp', 'XRegExp', 'all');
    	 * // -> 'XRegExp builds XRegExps'
    	 */
    	XRegExp.replace = function(str, search, replacement, scope) {
    	    var isRegex = XRegExp.isRegExp(search);
    	    var global = (search.global && scope !== 'one') || scope === 'all';
    	    var cacheKey = ((global ? 'g' : '') + (search.sticky ? 'y' : '')) || 'noGY';
    	    var s2 = search;
    	    var result;

    	    if (isRegex) {
    	        search[REGEX_DATA] = search[REGEX_DATA] || {};

    	        // Shares cached copies with `XRegExp.exec`/`match`. Since a copy is used, `search`'s
    	        // `lastIndex` isn't updated *during* replacement iterations
    	        s2 = search[REGEX_DATA][cacheKey] || (
    	            search[REGEX_DATA][cacheKey] = copyRegex(search, {
    	                addG: !!global,
    	                removeG: scope === 'one',
    	                isInternalOnly: true
    	            })
    	        );
    	    } else if (global) {
    	        s2 = new RegExp(XRegExp.escape(String(search)), 'g');
    	    }

    	    // Fixed `replace` required for named backreferences, etc.
    	    result = fixed.replace.call(toObject(str), s2, replacement);

    	    if (isRegex && search.global) {
    	        // Fixes IE, Safari bug (last tested IE 9, Safari 5.1)
    	        search.lastIndex = 0;
    	    }

    	    return result;
    	};

    	/**
    	 * Performs batch processing of string replacements. Used like `XRegExp.replace`, but accepts an
    	 * array of replacement details. Later replacements operate on the output of earlier replacements.
    	 * Replacement details are accepted as an array with a regex or string to search for, the
    	 * replacement string or function, and an optional scope of 'one' or 'all'. Uses the XRegExp
    	 * replacement text syntax, which supports named backreference properties via `${name}`.
    	 *
    	 * @memberOf XRegExp
    	 * @param {String} str String to search.
    	 * @param {Array} replacements Array of replacement detail arrays.
    	 * @returns {String} New string with all replacements.
    	 * @example
    	 *
    	 * str = XRegExp.replaceEach(str, [
    	 *   [XRegExp('(?<name>a)'), 'z${name}'],
    	 *   [/b/gi, 'y'],
    	 *   [/c/g, 'x', 'one'], // scope 'one' overrides /g
    	 *   [/d/, 'w', 'all'],  // scope 'all' overrides lack of /g
    	 *   ['e', 'v', 'all'],  // scope 'all' allows replace-all for strings
    	 *   [/f/g, function($0) {
    	 *     return $0.toUpperCase();
    	 *   }]
    	 * ]);
    	 */
    	XRegExp.replaceEach = function(str, replacements) {
    	    var i;
    	    var r;

    	    for (i = 0; i < replacements.length; ++i) {
    	        r = replacements[i];
    	        str = XRegExp.replace(str, r[0], r[1], r[2]);
    	    }

    	    return str;
    	};

    	/**
    	 * Splits a string into an array of strings using a regex or string separator. Matches of the
    	 * separator are not included in the result array. However, if `separator` is a regex that contains
    	 * capturing groups, backreferences are spliced into the result each time `separator` is matched.
    	 * Fixes browser bugs compared to the native `String.prototype.split` and can be used reliably
    	 * cross-browser.
    	 *
    	 * @memberOf XRegExp
    	 * @param {String} str String to split.
    	 * @param {RegExp|String} separator Regex or string to use for separating the string.
    	 * @param {Number} [limit] Maximum number of items to include in the result array.
    	 * @returns {Array} Array of substrings.
    	 * @example
    	 *
    	 * // Basic use
    	 * XRegExp.split('a b c', ' ');
    	 * // -> ['a', 'b', 'c']
    	 *
    	 * // With limit
    	 * XRegExp.split('a b c', ' ', 2);
    	 * // -> ['a', 'b']
    	 *
    	 * // Backreferences in result array
    	 * XRegExp.split('..word1..', /([a-z]+)(\d+)/i);
    	 * // -> ['..', 'word', '1', '..']
    	 */
    	XRegExp.split = function(str, separator, limit) {
    	    return fixed.split.call(toObject(str), separator, limit);
    	};

    	/**
    	 * Executes a regex search in a specified string. Returns `true` or `false`. Optional `pos` and
    	 * `sticky` arguments specify the search start position, and whether the match must start at the
    	 * specified position only. The `lastIndex` property of the provided regex is not used, but is
    	 * updated for compatibility. Also fixes browser bugs compared to the native
    	 * `RegExp.prototype.test` and can be used reliably cross-browser.
    	 *
    	 * @memberOf XRegExp
    	 * @param {String} str String to search.
    	 * @param {RegExp} regex Regex to search with.
    	 * @param {Number} [pos=0] Zero-based index at which to start the search.
    	 * @param {Boolean|String} [sticky=false] Whether the match must start at the specified position
    	 *   only. The string `'sticky'` is accepted as an alternative to `true`.
    	 * @returns {Boolean} Whether the regex matched the provided value.
    	 * @example
    	 *
    	 * // Basic use
    	 * XRegExp.test('abc', /c/); // -> true
    	 *
    	 * // With pos and sticky
    	 * XRegExp.test('abc', /c/, 0, 'sticky'); // -> false
    	 * XRegExp.test('abc', /c/, 2, 'sticky'); // -> true
    	 */
    	XRegExp.test = function(str, regex, pos, sticky) {
    	    // Do this the easy way :-)
    	    return !!XRegExp.exec(str, regex, pos, sticky);
    	};

    	/**
    	 * Uninstalls optional features according to the specified options. All optional features start out
    	 * uninstalled, so this is used to undo the actions of `XRegExp.install`.
    	 *
    	 * @memberOf XRegExp
    	 * @param {Object|String} options Options object or string.
    	 * @example
    	 *
    	 * // With an options object
    	 * XRegExp.uninstall({
    	 *   // Disables support for astral code points in Unicode addons
    	 *   astral: true,
    	 *
    	 *   // DEPRECATED: Restores native regex methods
    	 *   natives: true
    	 * });
    	 *
    	 * // With an options string
    	 * XRegExp.uninstall('astral natives');
    	 */
    	XRegExp.uninstall = function(options) {
    	    options = prepareOptions(options);

    	    if (features.astral && options.astral) {
    	        setAstral(false);
    	    }

    	    if (features.natives && options.natives) {
    	        setNatives(false);
    	    }
    	};

    	/**
    	 * Returns an XRegExp object that is the union of the given patterns. Patterns can be provided as
    	 * regex objects or strings. Metacharacters are escaped in patterns provided as strings.
    	 * Backreferences in provided regex objects are automatically renumbered to work correctly within
    	 * the larger combined pattern. Native flags used by provided regexes are ignored in favor of the
    	 * `flags` argument.
    	 *
    	 * @memberOf XRegExp
    	 * @param {Array} patterns Regexes and strings to combine.
    	 * @param {String} [flags] Any combination of XRegExp flags.
    	 * @param {Object} [options] Options object with optional properties:
    	 *   - `conjunction` {String} Type of conjunction to use: 'or' (default) or 'none'.
    	 * @returns {RegExp} Union of the provided regexes and strings.
    	 * @example
    	 *
    	 * XRegExp.union(['a+b*c', /(dogs)\1/, /(cats)\1/], 'i');
    	 * // -> /a\+b\*c|(dogs)\1|(cats)\2/i
    	 *
    	 * XRegExp.union([/man/, /bear/, /pig/], 'i', {conjunction: 'none'});
    	 * // -> /manbearpig/i
    	 */
    	XRegExp.union = function(patterns, flags, options) {
    	    options = options || {};
    	    var conjunction = options.conjunction || 'or';
    	    var numCaptures = 0;
    	    var numPriorCaptures;
    	    var captureNames;

    	    function rewrite(match, paren, backref) {
    	        var name = captureNames[numCaptures - numPriorCaptures];

    	        // Capturing group
    	        if (paren) {
    	            ++numCaptures;
    	            // If the current capture has a name, preserve the name
    	            if (name) {
    	                return '(?<' + name + '>';
    	            }
    	        // Backreference
    	        } else if (backref) {
    	            // Rewrite the backreference
    	            return '\\' + (+backref + numPriorCaptures);
    	        }

    	        return match;
    	    }

    	    if (!(isType(patterns, 'Array') && patterns.length)) {
    	        throw new TypeError('Must provide a nonempty array of patterns to merge');
    	    }

    	    var parts = /(\()(?!\?)|\\([1-9]\d*)|\\[\s\S]|\[(?:[^\\\]]|\\[\s\S])*\]/g;
    	    var output = [];
    	    var pattern;
    	    for (var i = 0; i < patterns.length; ++i) {
    	        pattern = patterns[i];

    	        if (XRegExp.isRegExp(pattern)) {
    	            numPriorCaptures = numCaptures;
    	            captureNames = (pattern[REGEX_DATA] && pattern[REGEX_DATA].captureNames) || [];

    	            // Rewrite backreferences. Passing to XRegExp dies on octals and ensures patterns are
    	            // independently valid; helps keep this simple. Named captures are put back
    	            output.push(nativ.replace.call(XRegExp(pattern.source).source, parts, rewrite));
    	        } else {
    	            output.push(XRegExp.escape(pattern));
    	        }
    	    }

    	    var separator = conjunction === 'none' ? '' : '|';
    	    return XRegExp(output.join(separator), flags);
    	};

    	// ==--------------------------==
    	// Fixed/extended native methods
    	// ==--------------------------==

    	/**
    	 * Adds named capture support (with backreferences returned as `result.name`), and fixes browser
    	 * bugs in the native `RegExp.prototype.exec`. Calling `XRegExp.install('natives')` uses this to
    	 * override the native method. Use via `XRegExp.exec` without overriding natives.
    	 *
    	 * @memberOf RegExp
    	 * @param {String} str String to search.
    	 * @returns {Array} Match array with named backreference properties, or `null`.
    	 */
    	fixed.exec = function(str) {
    	    var origLastIndex = this.lastIndex;
    	    var match = nativ.exec.apply(this, arguments);
    	    var name;
    	    var r2;
    	    var i;

    	    if (match) {
    	        // Fix browsers whose `exec` methods don't return `undefined` for nonparticipating capturing
    	        // groups. This fixes IE 5.5-8, but not IE 9's quirks mode or emulation of older IEs. IE 9
    	        // in standards mode follows the spec.
    	        if (!correctExecNpcg && match.length > 1 && indexOf(match, '') > -1) {
    	            r2 = copyRegex(this, {
    	                removeG: true,
    	                isInternalOnly: true
    	            });
    	            // Using `str.slice(match.index)` rather than `match[0]` in case lookahead allowed
    	            // matching due to characters outside the match
    	            nativ.replace.call(String(str).slice(match.index), r2, function() {
    	                var len = arguments.length;
    	                var i;
    	                // Skip index 0 and the last 2
    	                for (i = 1; i < len - 2; ++i) {
    	                    if (arguments[i] === undefined) {
    	                        match[i] = undefined;
    	                    }
    	                }
    	            });
    	        }

    	        // Attach named capture properties
    	        if (this[REGEX_DATA] && this[REGEX_DATA].captureNames) {
    	            // Skip index 0
    	            for (i = 1; i < match.length; ++i) {
    	                name = this[REGEX_DATA].captureNames[i - 1];
    	                if (name) {
    	                    match[name] = match[i];
    	                }
    	            }
    	        }

    	        // Fix browsers that increment `lastIndex` after zero-length matches
    	        if (this.global && !match[0].length && (this.lastIndex > match.index)) {
    	            this.lastIndex = match.index;
    	        }
    	    }

    	    if (!this.global) {
    	        // Fixes IE, Opera bug (last tested IE 9, Opera 11.6)
    	        this.lastIndex = origLastIndex;
    	    }

    	    return match;
    	};

    	/**
    	 * Fixes browser bugs in the native `RegExp.prototype.test`. Calling `XRegExp.install('natives')`
    	 * uses this to override the native method.
    	 *
    	 * @memberOf RegExp
    	 * @param {String} str String to search.
    	 * @returns {Boolean} Whether the regex matched the provided value.
    	 */
    	fixed.test = function(str) {
    	    // Do this the easy way :-)
    	    return !!fixed.exec.call(this, str);
    	};

    	/**
    	 * Adds named capture support (with backreferences returned as `result.name`), and fixes browser
    	 * bugs in the native `String.prototype.match`. Calling `XRegExp.install('natives')` uses this to
    	 * override the native method.
    	 *
    	 * @memberOf String
    	 * @param {RegExp|*} regex Regex to search with. If not a regex object, it is passed to `RegExp`.
    	 * @returns {Array} If `regex` uses flag g, an array of match strings or `null`. Without flag g,
    	 *   the result of calling `regex.exec(this)`.
    	 */
    	fixed.match = function(regex) {
    	    var result;

    	    if (!XRegExp.isRegExp(regex)) {
    	        // Use the native `RegExp` rather than `XRegExp`
    	        regex = new RegExp(regex);
    	    } else if (regex.global) {
    	        result = nativ.match.apply(this, arguments);
    	        // Fixes IE bug
    	        regex.lastIndex = 0;

    	        return result;
    	    }

    	    return fixed.exec.call(regex, toObject(this));
    	};

    	/**
    	 * Adds support for `${n}` tokens for named and numbered backreferences in replacement text, and
    	 * provides named backreferences to replacement functions as `arguments[0].name`. Also fixes browser
    	 * bugs in replacement text syntax when performing a replacement using a nonregex search value, and
    	 * the value of a replacement regex's `lastIndex` property during replacement iterations and upon
    	 * completion. Calling `XRegExp.install('natives')` uses this to override the native method. Note
    	 * that this doesn't support SpiderMonkey's proprietary third (`flags`) argument. Use via
    	 * `XRegExp.replace` without overriding natives.
    	 *
    	 * @memberOf String
    	 * @param {RegExp|String} search Search pattern to be replaced.
    	 * @param {String|Function} replacement Replacement string or a function invoked to create it.
    	 * @returns {String} New string with one or all matches replaced.
    	 */
    	fixed.replace = function(search, replacement) {
    	    var isRegex = XRegExp.isRegExp(search);
    	    var origLastIndex;
    	    var captureNames;
    	    var result;

    	    if (isRegex) {
    	        if (search[REGEX_DATA]) {
    	            captureNames = search[REGEX_DATA].captureNames;
    	        }
    	        // Only needed if `search` is nonglobal
    	        origLastIndex = search.lastIndex;
    	    } else {
    	        search += ''; // Type-convert
    	    }

    	    // Don't use `typeof`; some older browsers return 'function' for regex objects
    	    if (isType(replacement, 'Function')) {
    	        // Stringifying `this` fixes a bug in IE < 9 where the last argument in replacement
    	        // functions isn't type-converted to a string
    	        result = nativ.replace.call(String(this), search, function() {
    	            var args = arguments;
    	            var i;
    	            if (captureNames) {
    	                // Change the `arguments[0]` string primitive to a `String` object that can store
    	                // properties. This really does need to use `String` as a constructor
    	                args[0] = new String(args[0]);
    	                // Store named backreferences on the first argument
    	                for (i = 0; i < captureNames.length; ++i) {
    	                    if (captureNames[i]) {
    	                        args[0][captureNames[i]] = args[i + 1];
    	                    }
    	                }
    	            }
    	            // Update `lastIndex` before calling `replacement`. Fixes IE, Chrome, Firefox, Safari
    	            // bug (last tested IE 9, Chrome 17, Firefox 11, Safari 5.1)
    	            if (isRegex && search.global) {
    	                search.lastIndex = args[args.length - 2] + args[0].length;
    	            }
    	            // ES6 specs the context for replacement functions as `undefined`
    	            return replacement.apply(undefined, args);
    	        });
    	    } else {
    	        // Ensure that the last value of `args` will be a string when given nonstring `this`,
    	        // while still throwing on null or undefined context
    	        result = nativ.replace.call(this == null ? this : String(this), search, function() {
    	            // Keep this function's `arguments` available through closure
    	            var args = arguments;
    	            return nativ.replace.call(String(replacement), replacementToken, function($0, $1, $2) {
    	                var n;
    	                // Named or numbered backreference with curly braces
    	                if ($1) {
    	                    // XRegExp behavior for `${n}`:
    	                    // 1. Backreference to numbered capture, if `n` is an integer. Use `0` for the
    	                    //    entire match. Any number of leading zeros may be used.
    	                    // 2. Backreference to named capture `n`, if it exists and is not an integer
    	                    //    overridden by numbered capture. In practice, this does not overlap with
    	                    //    numbered capture since XRegExp does not allow named capture to use a bare
    	                    //    integer as the name.
    	                    // 3. If the name or number does not refer to an existing capturing group, it's
    	                    //    an error.
    	                    n = +$1; // Type-convert; drop leading zeros
    	                    if (n <= args.length - 3) {
    	                        return args[n] || '';
    	                    }
    	                    // Groups with the same name is an error, else would need `lastIndexOf`
    	                    n = captureNames ? indexOf(captureNames, $1) : -1;
    	                    if (n < 0) {
    	                        throw new SyntaxError('Backreference to undefined group ' + $0);
    	                    }
    	                    return args[n + 1] || '';
    	                }
    	                // Else, special variable or numbered backreference without curly braces
    	                if ($2 === '$') { // $$
    	                    return '$';
    	                }
    	                if ($2 === '&' || +$2 === 0) { // $&, $0 (not followed by 1-9), $00
    	                    return args[0];
    	                }
    	                if ($2 === '`') { // $` (left context)
    	                    return args[args.length - 1].slice(0, args[args.length - 2]);
    	                }
    	                if ($2 === "'") { // $' (right context)
    	                    return args[args.length - 1].slice(args[args.length - 2] + args[0].length);
    	                }
    	                // Else, numbered backreference without curly braces
    	                $2 = +$2; // Type-convert; drop leading zero
    	                // XRegExp behavior for `$n` and `$nn`:
    	                // - Backrefs end after 1 or 2 digits. Use `${..}` for more digits.
    	                // - `$1` is an error if no capturing groups.
    	                // - `$10` is an error if less than 10 capturing groups. Use `${1}0` instead.
    	                // - `$01` is `$1` if at least one capturing group, else it's an error.
    	                // - `$0` (not followed by 1-9) and `$00` are the entire match.
    	                // Native behavior, for comparison:
    	                // - Backrefs end after 1 or 2 digits. Cannot reference capturing group 100+.
    	                // - `$1` is a literal `$1` if no capturing groups.
    	                // - `$10` is `$1` followed by a literal `0` if less than 10 capturing groups.
    	                // - `$01` is `$1` if at least one capturing group, else it's a literal `$01`.
    	                // - `$0` is a literal `$0`.
    	                if (!isNaN($2)) {
    	                    if ($2 > args.length - 3) {
    	                        throw new SyntaxError('Backreference to undefined group ' + $0);
    	                    }
    	                    return args[$2] || '';
    	                }
    	                // `$` followed by an unsupported char is an error, unlike native JS
    	                throw new SyntaxError('Invalid token ' + $0);
    	            });
    	        });
    	    }

    	    if (isRegex) {
    	        if (search.global) {
    	            // Fixes IE, Safari bug (last tested IE 9, Safari 5.1)
    	            search.lastIndex = 0;
    	        } else {
    	            // Fixes IE, Opera bug (last tested IE 9, Opera 11.6)
    	            search.lastIndex = origLastIndex;
    	        }
    	    }

    	    return result;
    	};

    	/**
    	 * Fixes browser bugs in the native `String.prototype.split`. Calling `XRegExp.install('natives')`
    	 * uses this to override the native method. Use via `XRegExp.split` without overriding natives.
    	 *
    	 * @memberOf String
    	 * @param {RegExp|String} separator Regex or string to use for separating the string.
    	 * @param {Number} [limit] Maximum number of items to include in the result array.
    	 * @returns {Array} Array of substrings.
    	 */
    	fixed.split = function(separator, limit) {
    	    if (!XRegExp.isRegExp(separator)) {
    	        // Browsers handle nonregex split correctly, so use the faster native method
    	        return nativ.split.apply(this, arguments);
    	    }

    	    var str = String(this);
    	    var output = [];
    	    var origLastIndex = separator.lastIndex;
    	    var lastLastIndex = 0;
    	    var lastLength;

    	    // Values for `limit`, per the spec:
    	    // If undefined: pow(2,32) - 1
    	    // If 0, Infinity, or NaN: 0
    	    // If positive number: limit = floor(limit); if (limit >= pow(2,32)) limit -= pow(2,32);
    	    // If negative number: pow(2,32) - floor(abs(limit))
    	    // If other: Type-convert, then use the above rules
    	    // This line fails in very strange ways for some values of `limit` in Opera 10.5-10.63, unless
    	    // Opera Dragonfly is open (go figure). It works in at least Opera 9.5-10.1 and 11+
    	    limit = (limit === undefined ? -1 : limit) >>> 0;

    	    XRegExp.forEach(str, separator, function(match) {
    	        // This condition is not the same as `if (match[0].length)`
    	        if ((match.index + match[0].length) > lastLastIndex) {
    	            output.push(str.slice(lastLastIndex, match.index));
    	            if (match.length > 1 && match.index < str.length) {
    	                Array.prototype.push.apply(output, match.slice(1));
    	            }
    	            lastLength = match[0].length;
    	            lastLastIndex = match.index + lastLength;
    	        }
    	    });

    	    if (lastLastIndex === str.length) {
    	        if (!nativ.test.call(separator, '') || lastLength) {
    	            output.push('');
    	        }
    	    } else {
    	        output.push(str.slice(lastLastIndex));
    	    }

    	    separator.lastIndex = origLastIndex;
    	    return output.length > limit ? output.slice(0, limit) : output;
    	};

    	// ==--------------------------==
    	// Built-in syntax/flag tokens
    	// ==--------------------------==

    	/*
    	 * Letter escapes that natively match literal characters: `\a`, `\A`, etc. These should be
    	 * SyntaxErrors but are allowed in web reality. XRegExp makes them errors for cross-browser
    	 * consistency and to reserve their syntax, but lets them be superseded by addons.
    	 */
    	XRegExp.addToken(
    	    /\\([ABCE-RTUVXYZaeg-mopqyz]|c(?![A-Za-z])|u(?![\dA-Fa-f]{4}|{[\dA-Fa-f]+})|x(?![\dA-Fa-f]{2}))/,
    	    function(match, scope) {
    	        // \B is allowed in default scope only
    	        if (match[1] === 'B' && scope === defaultScope) {
    	            return match[0];
    	        }
    	        throw new SyntaxError('Invalid escape ' + match[0]);
    	    },
    	    {
    	        scope: 'all',
    	        leadChar: '\\'
    	    }
    	);

    	/*
    	 * Unicode code point escape with curly braces: `\u{N..}`. `N..` is any one or more digit
    	 * hexadecimal number from 0-10FFFF, and can include leading zeros. Requires the native ES6 `u` flag
    	 * to support code points greater than U+FFFF. Avoids converting code points above U+FFFF to
    	 * surrogate pairs (which could be done without flag `u`), since that could lead to broken behavior
    	 * if you follow a `\u{N..}` token that references a code point above U+FFFF with a quantifier, or
    	 * if you use the same in a character class.
    	 */
    	XRegExp.addToken(
    	    /\\u{([\dA-Fa-f]+)}/,
    	    function(match, scope, flags) {
    	        var code = dec(match[1]);
    	        if (code > 0x10FFFF) {
    	            throw new SyntaxError('Invalid Unicode code point ' + match[0]);
    	        }
    	        if (code <= 0xFFFF) {
    	            // Converting to \uNNNN avoids needing to escape the literal character and keep it
    	            // separate from preceding tokens
    	            return '\\u' + pad4(hex(code));
    	        }
    	        // If `code` is between 0xFFFF and 0x10FFFF, require and defer to native handling
    	        if (hasNativeU && flags.indexOf('u') > -1) {
    	            return match[0];
    	        }
    	        throw new SyntaxError('Cannot use Unicode code point above \\u{FFFF} without flag u');
    	    },
    	    {
    	        scope: 'all',
    	        leadChar: '\\'
    	    }
    	);

    	/*
    	 * Empty character class: `[]` or `[^]`. This fixes a critical cross-browser syntax inconsistency.
    	 * Unless this is standardized (per the ES spec), regex syntax can't be accurately parsed because
    	 * character class endings can't be determined.
    	 */
    	XRegExp.addToken(
    	    /\[(\^?)\]/,
    	    function(match) {
    	        // For cross-browser compatibility with ES3, convert [] to \b\B and [^] to [\s\S].
    	        // (?!) should work like \b\B, but is unreliable in some versions of Firefox
    	        return match[1] ? '[\\s\\S]' : '\\b\\B';
    	    },
    	    {leadChar: '['}
    	);

    	/*
    	 * Comment pattern: `(?# )`. Inline comments are an alternative to the line comments allowed in
    	 * free-spacing mode (flag x).
    	 */
    	XRegExp.addToken(
    	    /\(\?#[^)]*\)/,
    	    getContextualTokenSeparator,
    	    {leadChar: '('}
    	);

    	/*
    	 * Whitespace and line comments, in free-spacing mode (aka extended mode, flag x) only.
    	 */
    	XRegExp.addToken(
    	    /\s+|#[^\n]*\n?/,
    	    getContextualTokenSeparator,
    	    {flag: 'x'}
    	);

    	/*
    	 * Dot, in dotall mode (aka singleline mode, flag s) only.
    	 */
    	XRegExp.addToken(
    	    /\./,
    	    function() {
    	        return '[\\s\\S]';
    	    },
    	    {
    	        flag: 's',
    	        leadChar: '.'
    	    }
    	);

    	/*
    	 * Named backreference: `\k<name>`. Backreference names can use the characters A-Z, a-z, 0-9, _,
    	 * and $ only. Also allows numbered backreferences as `\k<n>`.
    	 */
    	XRegExp.addToken(
    	    /\\k<([\w$]+)>/,
    	    function(match) {
    	        // Groups with the same name is an error, else would need `lastIndexOf`
    	        var index = isNaN(match[1]) ? (indexOf(this.captureNames, match[1]) + 1) : +match[1];
    	        var endIndex = match.index + match[0].length;
    	        if (!index || index > this.captureNames.length) {
    	            throw new SyntaxError('Backreference to undefined group ' + match[0]);
    	        }
    	        // Keep backreferences separate from subsequent literal numbers. This avoids e.g.
    	        // inadvertedly changing `(?<n>)\k<n>1` to `()\11`.
    	        return '\\' + index + (
    	            endIndex === match.input.length || isNaN(match.input.charAt(endIndex)) ?
    	                '' : '(?:)'
    	        );
    	    },
    	    {leadChar: '\\'}
    	);

    	/*
    	 * Numbered backreference or octal, plus any following digits: `\0`, `\11`, etc. Octals except `\0`
    	 * not followed by 0-9 and backreferences to unopened capture groups throw an error. Other matches
    	 * are returned unaltered. IE < 9 doesn't support backreferences above `\99` in regex syntax.
    	 */
    	XRegExp.addToken(
    	    /\\(\d+)/,
    	    function(match, scope) {
    	        if (
    	            !(
    	                scope === defaultScope &&
    	                /^[1-9]/.test(match[1]) &&
    	                +match[1] <= this.captureNames.length
    	            ) &&
    	            match[1] !== '0'
    	        ) {
    	            throw new SyntaxError('Cannot use octal escape or backreference to undefined group ' +
    	                match[0]);
    	        }
    	        return match[0];
    	    },
    	    {
    	        scope: 'all',
    	        leadChar: '\\'
    	    }
    	);

    	/*
    	 * Named capturing group; match the opening delimiter only: `(?<name>`. Capture names can use the
    	 * characters A-Z, a-z, 0-9, _, and $ only. Names can't be integers. Supports Python-style
    	 * `(?P<name>` as an alternate syntax to avoid issues in some older versions of Opera which natively
    	 * supported the Python-style syntax. Otherwise, XRegExp might treat numbered backreferences to
    	 * Python-style named capture as octals.
    	 */
    	XRegExp.addToken(
    	    /\(\?P?<([\w$]+)>/,
    	    function(match) {
    	        // Disallow bare integers as names because named backreferences are added to match arrays
    	        // and therefore numeric properties may lead to incorrect lookups
    	        if (!isNaN(match[1])) {
    	            throw new SyntaxError('Cannot use integer as capture name ' + match[0]);
    	        }
    	        if (match[1] === 'length' || match[1] === '__proto__') {
    	            throw new SyntaxError('Cannot use reserved word as capture name ' + match[0]);
    	        }
    	        if (indexOf(this.captureNames, match[1]) > -1) {
    	            throw new SyntaxError('Cannot use same name for multiple groups ' + match[0]);
    	        }
    	        this.captureNames.push(match[1]);
    	        this.hasNamedCapture = true;
    	        return '(';
    	    },
    	    {leadChar: '('}
    	);

    	/*
    	 * Capturing group; match the opening parenthesis only. Required for support of named capturing
    	 * groups. Also adds explicit capture mode (flag n).
    	 */
    	XRegExp.addToken(
    	    /\((?!\?)/,
    	    function(match, scope, flags) {
    	        if (flags.indexOf('n') > -1) {
    	            return '(?:';
    	        }
    	        this.captureNames.push(null);
    	        return '(';
    	    },
    	    {
    	        optionalFlags: 'n',
    	        leadChar: '('
    	    }
    	);

    	xregexp = XRegExp;
    	return xregexp;
    }

    var hasRequiredAddress;

    function requireAddress () {
    	if (hasRequiredAddress) return address;
    	hasRequiredAddress = 1;
    	(function (exports) {

    		(function(){
    		  var root;
    		  root = this;
    		  var XRegExp;

    		  if (typeof commonjsRequire !== "undefined"){
    		     XRegExp = requireXregexp();
    		  }
    		  else
    		    XRegExp = root.XRegExp;

    		  var parser = {};
    		  var Addr_Match = {};

    		  var Directional = {
    		    north       : "N",
    		    northeast   : "NE",
    		    east        : "E",
    		    southeast   : "SE",
    		    south       : "S",
    		    southwest   : "SW",
    		    west        : "W",
    		    northwest   : "NW",
    		  };

    		  var Street_Type = {
    		    allee       : "aly",
    		    alley       : "aly",
    		    ally        : "aly",
    		    anex        : "anx",
    		    annex       : "anx",
    		    annx        : "anx",
    		    arcade      : "arc",
    		    av          : "ave",
    		    aven        : "ave",
    		    avenu       : "ave",
    		    avenue      : "ave",
    		    avn         : "ave",
    		    avnue       : "ave",
    		    bayoo       : "byu",
    		    bayou       : "byu",
    		    beach       : "bch",
    		    bend        : "bnd",
    		    bluf        : "blf",
    		    bluff       : "blf",
    		    bluffs      : "blfs",
    		    bot         : "btm",
    		    bottm       : "btm",
    		    bottom      : "btm",
    		    boul        : "blvd",
    		    boulevard   : "blvd",
    		    boulv       : "blvd",
    		    branch      : "br",
    		    brdge       : "brg",
    		    bridge      : "brg",
    		    brnch       : "br",
    		    brook       : "brk",
    		    brooks      : "brks",
    		    burg        : "bg",
    		    burgs       : "bgs",
    		    bypa        : "byp",
    		    bypas       : "byp",
    		    bypass      : "byp",
    		    byps        : "byp",
    		    camp        : "cp",
    		    canyn       : "cyn",
    		    canyon      : "cyn",
    		    cape        : "cpe",
    		    causeway    : "cswy",
    		    causway     : "cswy",
    		    causwa      : "cswy",
    		    cen         : "ctr",
    		    cent        : "ctr",
    		    center      : "ctr",
    		    centers     : "ctrs",
    		    centr       : "ctr",
    		    centre      : "ctr",
    		    circ        : "cir",
    		    circl       : "cir",
    		    circle      : "cir",
    		    circles     : "cirs",
    		    ck          : "crk",
    		    cliff       : "clf",
    		    cliffs      : "clfs",
    		    club        : "clb",
    		    cmp         : "cp",
    		    cnter       : "ctr",
    		    cntr        : "ctr",
    		    cnyn        : "cyn",
    		    common      : "cmn",
    		    commons     : "cmns",
    		    corner      : "cor",
    		    corners     : "cors",
    		    course      : "crse",
    		    court       : "ct",
    		    courts      : "cts",
    		    cove        : "cv",
    		    coves       : "cvs",
    		    cr          : "crk",
    		    crcl        : "cir",
    		    crcle       : "cir",
    		    crecent     : "cres",
    		    creek       : "crk",
    		    crescent    : "cres",
    		    cresent     : "cres",
    		    crest       : "crst",
    		    crossing    : "xing",
    		    crossroad   : "xrd",
    		    crossroads  : "xrds",
    		    crscnt      : "cres",
    		    crsent      : "cres",
    		    crsnt       : "cres",
    		    crssing     : "xing",
    		    crssng      : "xing",
    		    crt         : "ct",
    		    curve       : "curv",
    		    dale        : "dl",
    		    dam         : "dm",
    		    div         : "dv",
    		    divide      : "dv",
    		    driv        : "dr",
    		    drive       : "dr",
    		    drives      : "drs",
    		    drv         : "dr",
    		    dvd         : "dv",
    		    estate      : "est",
    		    estates     : "ests",
    		    exp         : "expy",
    		    expr        : "expy",
    		    express     : "expy",
    		    expressway  : "expy",
    		    expw        : "expy",
    		    extension   : "ext",
    		    extensions  : "exts",
    		    extn        : "ext",
    		    extnsn      : "ext",
    		    fall        : "fall",
    		    falls       : "fls",
    		    ferry       : "fry",
    		    field       : "fld",
    		    fields      : "flds",
    		    flat        : "flt",
    		    flats       : "flts",
    		    ford        : "frd",
    		    fords       : "frds",
    		    forest      : "frst",
    		    forests     : "frst",
    		    forg        : "frg",
    		    forge       : "frg",
    		    forges      : "frgs",
    		    fork        : "frk",
    		    forks       : "frks",
    		    fort        : "ft",
    		    freeway     : "fwy",
    		    freewy      : "fwy",
    		    frry        : "fry",
    		    frt         : "ft",
    		    frway       : "fwy",
    		    frwy        : "fwy",
    		    garden      : "gdn",
    		    gardens     : "gdns",
    		    gardn       : "gdn",
    		    gateway     : "gtwy",
    		    gatewy      : "gtwy",
    		    gatway      : "gtwy",
    		    glen        : "gln",
    		    glens       : "glns",
    		    grden       : "gdn",
    		    grdn        : "gdn",
    		    grdns       : "gdns",
    		    green       : "grn",
    		    greens      : "grns",
    		    grov        : "grv",
    		    grove       : "grv",
    		    groves      : "grvs",
    		    gtway       : "gtwy",
    		    harb        : "hbr",
    		    harbor      : "hbr",
    		    harbors     : "hbrs",
    		    harbr       : "hbr",
    		    haven       : "hvn",
    		    havn        : "hvn",
    		    height      : "hts",
    		    heights     : "hts",
    		    hgts        : "hts",
    		    highway     : "hwy",
    		    highwy      : "hwy",
    		    hill        : "hl",
    		    hills       : "hls",
    		    hiway       : "hwy",
    		    hiwy        : "hwy",
    		    hllw        : "holw",
    		    hollow      : "holw",
    		    hollows     : "holw",
    		    holws       : "holw",
    		    hrbor       : "hbr",
    		    ht          : "hts",
    		    hway        : "hwy",
    		    inlet       : "inlt",
    		    island      : "is",
    		    islands     : "iss",
    		    isles       : "isle",
    		    islnd       : "is",
    		    islnds      : "iss",
    		    jction      : "jct",
    		    jctn        : "jct",
    		    jctns       : "jcts",
    		    junction    : "jct",
    		    junctions   : "jcts",
    		    junctn      : "jct",
    		    juncton     : "jct",
    		    key         : "ky",
    		    keys        : "kys",
    		    knol        : "knl",
    		    knoll       : "knl",
    		    knolls      : "knls",
    		    la          : "ln",
    		    lake        : "lk",
    		    lakes       : "lks",
    		    land        : "land",
    		    landing     : "lndg",
    		    lane        : "ln",
    		    lanes       : "ln",
    		    ldge        : "ldg",
    		    light       : "lgt",
    		    lights      : "lgts",
    		    lndng       : "lndg",
    		    loaf        : "lf",
    		    lock        : "lck",
    		    locks       : "lcks",
    		    lodg        : "ldg",
    		    lodge       : "ldg",
    		    loops       : "loop",
    		    mall        : "mall",
    		    manor       : "mnr",
    		    manors      : "mnrs",
    		    meadow      : "mdw",
    		    meadows     : "mdws",
    		    medows      : "mdws",
    		    mews        : "mews",
    		    mill        : "ml",
    		    mills       : "mls",
    		    mission     : "msn",
    		    missn       : "msn",
    		    mnt         : "mt",
    		    mntain      : "mtn",
    		    mntn        : "mtn",
    		    mntns       : "mtns",
    		    motorway    : "mtwy",
    		    mount       : "mt",
    		    mountain    : "mtn",
    		    mountains   : "mtns",
    		    mountin     : "mtn",
    		    mssn        : "msn",
    		    mtin        : "mtn",
    		    neck        : "nck",
    		    orchard     : "orch",
    		    orchrd      : "orch",
    		    overpass    : "opas",
    		    ovl         : "oval",
    		    parks       : "park",
    		    parkway     : "pkwy",
    		    parkways    : "pkwy",
    		    parkwy      : "pkwy",
    		    pass        : "pass",
    		    passage     : "psge",
    		    paths       : "path",
    		    pikes       : "pike",
    		    pine        : "pne",
    		    pines       : "pnes",
    		    pk          : "park",
    		    pkway       : "pkwy",
    		    pkwys       : "pkwy",
    		    pky         : "pkwy",
    		    place       : "pl",
    		    plain       : "pln",
    		    plaines     : "plns",
    		    plains      : "plns",
    		    plaza       : "plz",
    		    plza        : "plz",
    		    point       : "pt",
    		    points      : "pts",
    		    port        : "prt",
    		    ports       : "prts",
    		    prairie     : "pr",
    		    prarie      : "pr",
    		    prk         : "park",
    		    prr         : "pr",
    		    rad         : "radl",
    		    radial      : "radl",
    		    radiel      : "radl",
    		    ranch       : "rnch",
    		    ranches     : "rnch",
    		    rapid       : "rpd",
    		    rapids      : "rpds",
    		    rdge        : "rdg",
    		    rest        : "rst",
    		    ridge       : "rdg",
    		    ridges      : "rdgs",
    		    river       : "riv",
    		    rivr        : "riv",
    		    rnchs       : "rnch",
    		    road        : "rd",
    		    roads       : "rds",
    		    route       : "rte",
    		    rvr         : "riv",
    		    row         : "row",
    		    rue         : "rue",
    		    run         : "run",
    		    shoal       : "shl",
    		    shoals      : "shls",
    		    shoar       : "shr",
    		    shoars      : "shrs",
    		    shore       : "shr",
    		    shores      : "shrs",
    		    skyway      : "skwy",
    		    spng        : "spg",
    		    spngs       : "spgs",
    		    spring      : "spg",
    		    springs     : "spgs",
    		    sprng       : "spg",
    		    sprngs      : "spgs",
    		    spurs       : "spur",
    		    sqr         : "sq",
    		    sqre        : "sq",
    		    sqrs        : "sqs",
    		    squ         : "sq",
    		    square      : "sq",
    		    squares     : "sqs",
    		    station     : "sta",
    		    statn       : "sta",
    		    stn         : "sta",
    		    str         : "st",
    		    strav       : "stra",
    		    strave      : "stra",
    		    straven     : "stra",
    		    stravenue   : "stra",
    		    stravn      : "stra",
    		    stream      : "strm",
    		    street      : "st",
    		    streets     : "sts",
    		    streme      : "strm",
    		    strt        : "st",
    		    strvn       : "stra",
    		    strvnue     : "stra",
    		    sumit       : "smt",
    		    sumitt      : "smt",
    		    summit      : "smt",
    		    terr        : "ter",
    		    terrace     : "ter",
    		    throughway  : "trwy",
    		    tpk         : "tpke",
    		    tr          : "trl",
    		    trace       : "trce",
    		    traces      : "trce",
    		    track       : "trak",
    		    tracks      : "trak",
    		    trafficway  : "trfy",
    		    trail       : "trl",
    		    trails      : "trl",
    		    trk         : "trak",
    		    trks        : "trak",
    		    trls        : "trl",
    		    trnpk       : "tpke",
    		    trpk        : "tpke",
    		    tunel       : "tunl",
    		    tunls       : "tunl",
    		    tunnel      : "tunl",
    		    tunnels     : "tunl",
    		    tunnl       : "tunl",
    		    turnpike    : "tpke",
    		    turnpk      : "tpke",
    		    underpass   : "upas",
    		    union       : "un",
    		    unions      : "uns",
    		    valley      : "vly",
    		    valleys     : "vlys",
    		    vally       : "vly",
    		    vdct        : "via",
    		    viadct      : "via",
    		    viaduct     : "via",
    		    view        : "vw",
    		    views       : "vws",
    		    vill        : "vlg",
    		    villag      : "vlg",
    		    village     : "vlg",
    		    villages    : "vlgs",
    		    ville       : "vl",
    		    villg       : "vlg",
    		    villiage    : "vlg",
    		    vist        : "vis",
    		    vista       : "vis",
    		    vlly        : "vly",
    		    vst         : "vis",
    		    vsta        : "vis",
    		    wall        : "wall",
    		    walks       : "walk",
    		    well        : "wl",
    		    wells       : "wls",
    		    wy          : "way",
    		  };

    		  var State_Code = {
    		    "alabama" : "AL",
    		    "alaska" : "AK",
    		    "american samoa" : "AS",
    		    "arizona" : "AZ",
    		    "arkansas" : "AR",
    		    "california" : "CA",
    		    "colorado" : "CO",
    		    "connecticut" : "CT",
    		    "delaware" : "DE",
    		    "district of columbia" : "DC",
    		    "federated states of micronesia" : "FM",
    		    "florida" : "FL",
    		    "georgia" : "GA",
    		    "guam" : "GU",
    		    "hawaii" : "HI",
    		    "idaho" : "ID",
    		    "illinois" : "IL",
    		    "indiana" : "IN",
    		    "iowa" : "IA",
    		    "kansas" : "KS",
    		    "kentucky" : "KY",
    		    "louisiana" : "LA",
    		    "maine" : "ME",
    		    "marshall islands" : "MH",
    		    "maryland" : "MD",
    		    "massachusetts" : "MA",
    		    "michigan" : "MI",
    		    "minnesota" : "MN",
    		    "mississippi" : "MS",
    		    "missouri" : "MO",
    		    "montana" : "MT",
    		    "nebraska" : "NE",
    		    "nevada" : "NV",
    		    "new hampshire" : "NH",
    		    "new jersey" : "NJ",
    		    "new mexico" : "NM",
    		    "new york" : "NY",
    		    "north carolina" : "NC",
    		    "north dakota" : "ND",
    		    "northern mariana islands" : "MP",
    		    "ohio" : "OH",
    		    "oklahoma" : "OK",
    		    "oregon" : "OR",
    		    "palau" : "PW",
    		    "pennsylvania" : "PA",
    		    "puerto rico" : "PR",
    		    "rhode island" : "RI",
    		    "south carolina" : "SC",
    		    "south dakota" : "SD",
    		    "tennessee" : "TN",
    		    "texas" : "TX",
    		    "utah" : "UT",
    		    "vermont" : "VT",
    		    "virgin islands" : "VI",
    		    "virginia" : "VA",
    		    "washington" : "WA",
    		    "west virginia" : "WV",
    		    "wisconsin" : "WI",
    		    "wyoming" : "WY",
    		  };

    		  var Direction_Code;
    		  var initialized = false;

    		  var Normalize_Map = {
    		    prefix: Directional,
    		    prefix1: Directional,
    		    prefix2: Directional,
    		    suffix: Directional,
    		    suffix1: Directional,
    		    suffix2: Directional,
    		    type: Street_Type,
    		    type1: Street_Type,
    		    type2: Street_Type,
    		    state: State_Code,
    		  };

    		  function capitalize(s){
    		    return s && s[0].toUpperCase() + s.slice(1);
    		  }
    		  function keys(o){
    		    return Object.keys(o);
    		  }
    		  function values(o){
    		    var v = [];
    		    keys(o).forEach(function(k){
    		      v.push(o[k]);
    		    });
    		    return v;
    		  }
    		  function each(o,fn){
    		    keys(o).forEach(function(k){
    		      fn(o[k],k);
    		    });
    		  }
    		  function invert(o){
    		    var o1= {};
    		    keys(o).forEach(function(k){
    		      o1[o[k]] = k;
    		    });
    		    return o1;
    		  }
    		  function flatten(o){
    		    return keys(o).concat(values(o));
    		  }
    		  function lazyInit(){
    		    if (initialized) {
    		      return;
    		    }
    		    initialized = true;

    		    Direction_Code = invert(Directional);

    		    /*
    		    var Street_Type_Match = {};
    		    each(Street_Type,function(v,k){ Street_Type_Match[v] = XRegExp.escape(v) });
    		    each(Street_Type,function(v,k){ Street_Type_Match[v] = Street_Type_Match[v] + "|" + XRegExp.escape(k); });
    		    each(Street_Type_Match,function(v,k){ Street_Type_Match[k] = new RegExp( '\\b(?:' +  Street_Type_Match[k]  + ')\\b', 'i') });
    		    */

    		    Addr_Match = {
    		      type    : flatten(Street_Type).sort().filter(function(v,i,arr){return arr.indexOf(v)===i }).join('|'),
    		      fraction : '\\d+\\/\\d+',
    		      state   : '\\b(?:' + keys(State_Code).concat(values(State_Code)).map(XRegExp.escape).join('|') + ')\\b',
    		      direct  : values(Directional).sort(function(a,b){return a.length < b.length}).reduce(function(prev,curr){return prev.concat([XRegExp.escape(curr.replace(/\w/g,'$&.')),curr])},keys(Directional)).join('|'),
    		      dircode : keys(Direction_Code).join("|"),
    		      zip     : '(?<zip>\\d{5})[- ]?(?<plus4>\\d{4})?',
    		      corner  : '(?:\\band\\b|\\bat\\b|&|\\@)',
    		    };

    		    Addr_Match.number = '(?<number>(\\d+-?\\d*)|([N|S|E|W]\\d{1,3}[N|S|E|W]\\d{1,6}))(?=\\D)';

    		    Addr_Match.street = '                                       \n\
		      (?:                                                       \n\
		        (?:(?<street_0>'+Addr_Match.direct+')\\W+               \n\
		           (?<type_0>'+Addr_Match.type+')\\b                    \n\
		        )                                                       \n\
		        |                                                       \n\
		        (?:(?<prefix_0>'+Addr_Match.direct+')\\W+)?             \n\
		        (?:                                                     \n\
		          (?<street_1>[^,]*\\d)                                 \n\
		          (?:[^\\w,]*(?<suffix_1>'+Addr_Match.direct+')\\b)     \n\
		          |                                                     \n\
		          (?<street_2>[^,]+)                                    \n\
		          (?:[^\\w,]+(?<type_2>'+Addr_Match.type+')\\b)         \n\
		          (?:[^\\w,]+(?<suffix_2>'+Addr_Match.direct+')\\b)?    \n\
		          |                                                     \n\
		          (?<street_3>[^,]+?)                                   \n\
		          (?:[^\\w,]+(?<type_3>'+Addr_Match.type+')\\b)?        \n\
		          (?:[^\\w,]+(?<suffix_3>'+Addr_Match.direct+')\\b)?    \n\
		        )                                                       \n\
		      )';

    		    Addr_Match.po_box = 'p\\W*(?:[om]|ost\\ ?office)\\W*b(?:ox)?';

    		    Addr_Match.sec_unit_type_numbered = '             \n\
		      (?<sec_unit_type_1>su?i?te                      \n\
		        |'+Addr_Match.po_box+'                        \n\
		        |(?:ap|dep)(?:ar)?t(?:me?nt)?                 \n\
		        |ro*m                                         \n\
		        |flo*r?                                       \n\
		        |uni?t                                        \n\
		        |bu?i?ldi?n?g                                 \n\
		        |ha?nga?r                                     \n\
		        |lo?t                                         \n\
		        |pier                                         \n\
		        |slip                                         \n\
		        |spa?ce?                                      \n\
		        |stop                                         \n\
		        |tra?i?le?r                                   \n\
		        |box)(?![a-z]                                 \n\
		      )                                               \n\
		      ';

    		    Addr_Match.sec_unit_type_unnumbered = '           \n\
		      (?<sec_unit_type_2>ba?se?me?n?t                 \n\
		        |fro?nt                                       \n\
		        |lo?bby                                       \n\
		        |lowe?r                                       \n\
		        |off?i?ce?                                    \n\
		        |pe?n?t?ho?u?s?e?                             \n\
		        |rear                                         \n\
		        |side                                         \n\
		        |uppe?r                                       \n\
		      )\\b';

    		    Addr_Match.sec_unit = '                               \n\
		      (?:                               #fix3             \n\
		        (?:                             #fix1             \n\
		          (?:                                             \n\
		            (?:'+Addr_Match.sec_unit_type_numbered+'\\W*) \n\
		            |(?<sec_unit_type_3>\\#)\\W*                  \n\
		          )                                               \n\
		          (?<sec_unit_num_1>[\\w-]+)                      \n\
		        )                                                 \n\
		        |                                                 \n\
		        '+Addr_Match.sec_unit_type_unnumbered+'           \n\
		      )';

    		    Addr_Match.city_and_state = '                       \n\
		      (?:                                               \n\
		        (?<city>[^\\d,]+?)\\W+                          \n\
		        (?<state>'+Addr_Match.state+')                  \n\
		      )                                                 \n\
		      ';

    		    Addr_Match.place = '                                \n\
		      (?:'+Addr_Match.city_and_state+'\\W*)?            \n\
		      (?:'+Addr_Match.zip+')?                           \n\
		      ';

    		    Addr_Match.address = XRegExp('                      \n\
		      ^                                                 \n\
		      [^\\w\\#]*                                        \n\
		      ('+Addr_Match.number+')\\W*                       \n\
		      (?:'+Addr_Match.fraction+'\\W*)?                  \n\
		         '+Addr_Match.street+'\\W+                      \n\
		      (?:'+Addr_Match.sec_unit+')?\\W*          #fix2   \n\
		         '+Addr_Match.place+'                           \n\
		      \\W*$','ix');

    		    var sep = '(?:\\W+|$)'; // no support for \Z

    		    Addr_Match.informal_address = XRegExp('                   \n\
		      ^                                                       \n\
		      \\s*                                                    \n\
		      (?:'+Addr_Match.sec_unit+sep+')?                        \n\
		      (?:'+Addr_Match.number+')?\\W*                          \n\
		      (?:'+Addr_Match.fraction+'\\W*)?                        \n\
		         '+Addr_Match.street+sep+'                            \n\
		      (?:'+Addr_Match.sec_unit.replace(/_\d/g,'$&1')+sep+')?  \n\
		      (?:'+Addr_Match.place+')?                               \n\
		      ','ix');

    		    Addr_Match.po_address = XRegExp('                         \n\
		      ^                                                       \n\
		      \\s*                                                    \n\
		      (?:'+Addr_Match.sec_unit.replace(/_\d/g,'$&1')+sep+')?  \n\
		      (?:'+Addr_Match.place+')?                               \n\
		      ','ix');

    		    Addr_Match.intersection = XRegExp('                     \n\
		      ^\\W*                                                 \n\
		      '+Addr_Match.street.replace(/_\d/g,'1$&')+'\\W*?      \n\
		      \\s+'+Addr_Match.corner+'\\s+                         \n\
		      '+Addr_Match.street.replace(/_\d/g,'2$&') + '\\W+     \n\
		      '+Addr_Match.place+'\\W*$','ix');
    		  }
    		  parser.normalize_address = function(parts){
    		    lazyInit();
    		    if(!parts)
    		      return null;
    		    var parsed = {};

    		    Object.keys(parts).forEach(function(k){
    		      if(['input','index'].indexOf(k) !== -1 || isFinite(k))
    		        return;
    		      var key = isFinite(k.split('_').pop())? k.split('_').slice(0,-1).join('_'): k ;
    		      if(parts[k])
    		        parsed[key] = parts[k].trim().replace(/^\s+|\s+$|[^\w\s\-#&]/g, '');
    		    });
    		    each(Normalize_Map, function(map,key) {
    		      if(parsed[key] && map[parsed[key].toLowerCase()]) {
    		        parsed[key] = map[parsed[key].toLowerCase()];
    		      }
    		    });

    		    ['type', 'type1', 'type2'].forEach(function(key){
    		      if(key in parsed)
    		        parsed[key] = parsed[key].charAt(0).toUpperCase() + parsed[key].slice(1).toLowerCase();
    		    });

    		    if(parsed.city){
    		      parsed.city = XRegExp.replace(parsed.city,
    		        XRegExp('^(?<dircode>'+Addr_Match.dircode+')\\s+(?=\\S)','ix'),
    		        function(match){
    		          return capitalize(Direction_Code[match.dircode.toUpperCase()]) +' ';
    		        });
    		    }
    		    return parsed;
    		  };

    		  parser.parseAddress = function(address){
    		    lazyInit();
    		    var parts = XRegExp.exec(address,Addr_Match.address);
    		    return parser.normalize_address(parts);
    		  };
    		  parser.parseInformalAddress = function(address){
    		    lazyInit();
    		    var parts = XRegExp.exec(address,Addr_Match.informal_address);
    		    return parser.normalize_address(parts);
    		  }; 
    		  parser.parsePoAddress = function(address){
    		    lazyInit();
    		    var parts = XRegExp.exec(address,Addr_Match.po_address);
    		    return parser.normalize_address(parts);
    		  };
    		  parser.parseLocation = function(address){
    		    lazyInit();
    		    if (XRegExp(Addr_Match.corner,'xi').test(address)) {
    		        return parser.parseIntersection(address);
    		    }
    		    if (XRegExp('^'+Addr_Match.po_box,'xi').test(address)){
    		      return parser.parsePoAddress(address);
    		    }
    		    return parser.parseAddress(address)
    		        || parser.parseInformalAddress(address);
    		  };
    		  parser.parseIntersection = function(address){
    		    lazyInit();
    		    var parts = XRegExp.exec(address,Addr_Match.intersection);
    		    parts = parser.normalize_address(parts);
    		    if(parts){
    		        parts.type2 = parts.type2 || '';
    		        parts.type1 = parts.type1 || '';
    		        if (parts.type2 && !parts.type1 || (parts.type1 === parts.type2)) {
    		            var type = parts.type2;
    		            type = XRegExp.replace(type,/s\W*$/,'');
    		            if (XRegExp('^'+Addr_Match.type+'$','ix').test(type)) {
    		                parts.type1 = parts.type2 = type;
    		            }
    		        }
    		    }

    		    return parts;
    		  };

    		  // AMD / RequireJS
    		  {
    		    exports.parseIntersection = parser.parseIntersection;
    		    exports.parseLocation = parser.parseLocation;
    		    exports.parseInformalAddress = parser.parseInformalAddress;
    		    exports.parseAddress = parser.parseAddress;
    		  }

    		}()); 
    	} (address));
    	return address;
    }

    var addressExports = requireAddress();
    var parseAddress = /*@__PURE__*/getDefaultExportFromCjs(addressExports);

    // eslint-disable-next-line @typescript-eslint/no-unused-vars

    /**
     * @implements {Extractor<{city:string; state: string|null}[]>}
     */
    class CityStateExtractor {
        /**
         * @param {string[]} strs
         * @param {import('../actions/extract.js').ExtractorParams} extractorParams
         */
        extract(strs, extractorParams) {
            const cityStateList = strs.map((str) => stringToList(str, extractorParams.separator)).flat();
            return getCityStateCombos(cityStateList);
        }
    }

    /**
     * @implements {Extractor<{city:string; state: string|null}[]>}
     */
    class AddressFullExtractor {
        /**
         * @param {string[]} strs
         * @param {import('../actions/extract.js').ExtractorParams} extractorParams
         */
        extract(strs, extractorParams) {
            return (
                strs
                    .map((str) => str.replace('\n', ' '))
                    .map((str) => stringToList(str, extractorParams.separator))
                    .flat()
                    .map((str) => parseAddress.parseLocation(str) || {})
                    // at least 'city' is required.
                    .filter((parsed) => Boolean(parsed?.city))
                    .map((addr) => {
                        return { city: addr.city, state: addr.state || null };
                    })
            );
        }
    }

    /**
     * @param {string[]} inputList
     * @return {{ city: string, state: string|null }[] }
     */
    function getCityStateCombos(inputList) {
        const output = [];
        for (let item of inputList) {
            let words;
            // Strip out the zip code since we're only interested in city/state here.
            item = item.replace(/,?\s*\d{5}(-\d{4})?/, '');

            // Replace any commas at the end of the string that could confuse the city/state split.
            item = item.replace(/,$/, '');

            if (item.includes(',')) {
                words = item.split(',').map((item) => item.trim());
            } else {
                words = item.split(' ').map((item) => item.trim());
            }
            // we are removing this partial city/state combos at the end (i.e. Chi...)
            if (words.length === 1) {
                continue;
            }

            const state = words.pop();
            const city = words.join(' ');

            // exclude invalid states
            if (state && !Object.keys(states).includes(state.toUpperCase())) {
                continue;
            }

            output.push({ city, state: state || null });
        }
        return output;
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars

    /**
     * @implements {Extractor<string[]>}
     */
    class PhoneExtractor {
        /**
         * @param {string[]} strs
         * @param {import('../actions/extract.js').ExtractorParams} extractorParams
         */
        extract(strs, extractorParams) {
            return strs
                .map((str) => stringToList(str, extractorParams.separator))
                .flat()
                .map((str) => str.replace(/\D/g, ''));
        }
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars

    /**
     * @implements {Extractor<string[]>}
     */
    class RelativesExtractor {
        /**
         * @param {string[]} strs
         * @param {import('../actions/extract.js').ExtractorParams} extractorParams
         */
        extract(strs, extractorParams) {
            return (
                strs
                    .map((x) => stringToList(x, extractorParams.separator))
                    .flat()
                    // for relatives, remove anything following a comma (usually 'age')
                    // eg: 'John Smith, 39' -> 'John Smith'
                    .map((x) => x.split(',')[0])
            );
        }
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars

    /**
     * @implements {Extractor<{profileUrl: string; identifier: string} | null>}
     */
    class ProfileUrlExtractor {
        /**
         * @param {string[]} strs
         * @param {import('../actions/extract.js').ExtractorParams} extractorParams
         */
        extract(strs, extractorParams) {
            if (strs.length === 0) return null;
            const profile = {
                profileUrl: strs[0],
                identifier: strs[0],
            };

            if (!extractorParams.identifierType || !extractorParams.identifier) {
                return profile;
            }

            const profileUrl = strs[0];
            profile.identifier = this.getIdFromProfileUrl(profileUrl, extractorParams.identifierType, extractorParams.identifier);
            return profile;
        }

        /**
         * Parse a profile id from a profile URL
         * @param {string} profileUrl
         * @param {import('../actions/extract.js').IdentifierType} identifierType
         * @param {string} identifier
         * @return {string}
         */
        getIdFromProfileUrl(profileUrl, identifierType, identifier) {
            const parsedUrl = new URL(profileUrl);
            const urlParams = parsedUrl.searchParams;

            // Attempt to parse out an id from the search parameters
            if (identifierType === 'param' && urlParams.has(identifier)) {
                const profileId = urlParams.get(identifier);
                return profileId || profileUrl;
            }

            return profileUrl;
        }
    }

    /**
     * If a hash is needed, compute it from the profile and
     * set it as the 'identifier'
     *
     * @implements {AsyncProfileTransform}
     */
    class ProfileHashTransformer {
        /**
         * @param {Record<string, any>} profile
         * @param {Record<string, any> } params
         * @return {Promise<Record<string, any>>}
         */
        async transform(profile, params) {
            if (params?.profileUrl?.identifierType !== 'hash') {
                return profile;
            }

            return {
                ...profile,
                identifier: await hashObject(profile),
            };
        }
    }

    /**
     * Adding these types here so that we can switch to generated ones later
     * @typedef {Record<string, any>} Action
     */

    /**
     * @typedef {'param'|'path'|'hash'} IdentifierType
     * @typedef {Object} ExtractProfileProperty
     * For example: {
     *   "selector": ".//div[@class='col-sm-24 col-md-8 relatives']//li"
     * }
     * @property {string} selector - xpath or css selector
     * @property {boolean} [findElements] - whether to get all occurrences of the selector
     * @property {string} [afterText] - get all text after this string
     * @property {string} [beforeText] - get all text before this string
     * @property {string} [separator] - split the text on this string
     * @property {IdentifierType} [identifierType] - the type (path/param) of the identifier
     * @property {string} [identifier] - the identifier itself (either a param name, or a templated URI)
     *
     * @typedef {Omit<ExtractProfileProperty, 'selector' | 'findElements'>} ExtractorParams
     */

    /**
     * @param {Action} action
     * @param {Record<string, any>} userData
     * @param {Document | HTMLElement} root
     * @return {Promise<import('../types.js').ActionResponse>}
     */
    async function extract(action, userData, root = document) {
        const extractResult = extractProfiles(action, userData, root);

        if ('error' in extractResult) {
            return new ErrorResponse({ actionID: action.id, message: extractResult.error });
        }

        const filteredPromises = extractResult.results
            .filter((x) => x.result === true)
            .map((x) => aggregateFields(x.scrapedData))
            .map((profile) => applyPostTransforms(profile, action.profile));

        const filtered = await Promise.all(filteredPromises);

        // omit the DOM node from data transfer

        const debugResults = extractResult.results.map((result) => result.asData());

        return new SuccessResponse({
            actionID: action.id,
            actionType: action.actionType,
            response: filtered,
            meta: {
                userData,
                extractResults: debugResults,
            },
        });
    }

    /**
     * @param {Action} action
     * @param {Record<string, any>} userData
     * @param {Element | Document} [root]
     * @return {{error: string} | {results: ProfileResult[]}}
     */
    function extractProfiles(action, userData, root = document) {
        const profilesElementList = getElements(root, action.selector) ?? [];

        if (profilesElementList.length === 0) {
            if (!action.noResultsSelector) {
                return { error: 'no root elements found for ' + action.selector };
            }

            // Look for the Results Not Found element
            const foundNoResultsElement = getElement(root, action.noResultsSelector);

            if (!foundNoResultsElement) {
                return { error: 'no results found for ' + action.selector + ' or the no results selector ' + action.noResultsSelector };
            }
        }

        return {
            results: profilesElementList.map((element) => {
                const elementFactory = (key, value) => {
                    return value?.findElements
                        ? cleanArray(getElements(element, value.selector))
                        : cleanArray(getElement(element, value.selector) || getElementMatches(element, value.selector));
                };
                const scrapedData = createProfile(elementFactory, action.profile);
                const { result, score, matchedFields } = scrapedDataMatchesUserData(userData, scrapedData);
                return new ProfileResult({
                    scrapedData,
                    result,
                    score,
                    element,
                    matchedFields,
                });
            }),
        };
    }

    /**
     * Produces structures like this:
     *
     * {
     *   "name": "John V Smith",
     *   "alternativeNamesList": [
     *     "John Inc Smith",
     *     "John Vsmith",
     *     "John Smithl"
     *   ],
     *   "age": "97",
     *   "addressCityStateList": [
     *     {
     *       "city": "Orlando",
     *       "state": "FL"
     *     }
     *   ],
     *   "profileUrl": "https://example.com/1234"
     * }
     *
     * @param {(key: string, value: ExtractProfileProperty) => {innerText: string}[]} elementFactory
     *   a function that produces elements for a given key + ExtractProfileProperty
     * @param {Record<string, ExtractProfileProperty>} extractData
     * @return {Record<string, any>}
     */
    function createProfile(elementFactory, extractData) {
        const output = {};
        for (const [key, value] of Object.entries(extractData)) {
            if (!value?.selector) {
                output[key] = null;
            } else {
                const elements = elementFactory(key, value);

                // extract all strings first
                const evaluatedValues = stringValuesFromElements(elements, key, value);

                // clean them up - trimming, removing empties
                const noneEmptyArray = cleanArray(evaluatedValues);

                // Note: This can return any valid JSON valid, it depends on the extractor used.
                const extractedValue = extractValue(key, value, noneEmptyArray);

                // try to use the extracted value, or fall back to null
                // this allows 'extractValue' to return null|undefined
                output[key] = extractedValue || null;
            }
        }
        return output;
    }

    /**
     * @param {({ textContent: string } | { innerText: string })[]} elements
     * @param {string} key
     * @param {ExtractProfileProperty} extractField
     * @return {string[]}
     */
    function stringValuesFromElements(elements, key, extractField) {
        return elements.map((element) => {
            let elementValue;

            if ('innerText' in element) {
                elementValue = rules[key]?.(element) ?? element?.innerText ?? null;

                // In instances where we use the text() node test, innerText will be undefined, and we fall back to textContent
            } else if ('textContent' in element) {
                elementValue = rules[key]?.(element) ?? element?.textContent ?? null;
            }

            if (!elementValue) {
                return elementValue;
            }

            if (extractField?.afterText) {
                elementValue = elementValue?.split(extractField.afterText)[1]?.trim() || elementValue;
            }
            // there is a case where we may want to get the text "after" and "before" certain text
            if (extractField?.beforeText) {
                elementValue = elementValue?.split(extractField.beforeText)[0].trim() || elementValue;
            }

            elementValue = removeCommonSuffixesAndPrefixes(elementValue);

            return elementValue;
        });
    }

    /**
     * Try to filter partial data based on the user's actual profile data
     * @param {Record<string, any>} userData
     * @param {Record<string, any>} scrapedData
     * @return {{score: number, matchedFields: string[], result: boolean}}
     */
    function scrapedDataMatchesUserData(userData, scrapedData) {
        const matchedFields = [];

        // the name matching is always a *requirement*
        if (isSameName(scrapedData.name, userData.firstName, userData.middleName, userData.lastName)) {
            matchedFields.push('name');
        } else {
            return { matchedFields, score: matchedFields.length, result: false };
        }

        // if the age field was present in the scraped data, then we consider this check a *requirement*
        if (scrapedData.age) {
            if (isSameAge(scrapedData.age, userData.age)) {
                matchedFields.push('age');
            } else {
                return { matchedFields, score: matchedFields.length, result: false };
            }
        }

        const addressFields = ['addressCityState', 'addressCityStateList', 'addressFull', 'addressFullList'];

        for (const addressField of addressFields) {
            if (addressField in scrapedData) {
                if (addressMatch(userData.addresses, scrapedData[addressField])) {
                    matchedFields.push(addressField);
                    return { matchedFields, score: matchedFields.length, result: true };
                }
            }
        }

        if (scrapedData.phone) {
            if (userData.phone === scrapedData.phone) {
                matchedFields.push('phone');
                return { matchedFields, score: matchedFields.length, result: true };
            }
        }

        // if we get here we didn't consider it a match
        return { matchedFields, score: matchedFields.length, result: false };
    }

    /**
     * @param {Record<string, any>} profile
     */
    function aggregateFields(profile) {
        // addresses
        const combinedAddresses = [
            ...(profile.addressCityState || []),
            ...(profile.addressCityStateList || []),
            ...(profile.addressFullList || []),
            ...(profile.addressFull || []),
        ];
        const addressMap = new Map(combinedAddresses.map((addr) => [`${addr.city},${addr.state}`, addr]));
        const addresses = sortAddressesByStateAndCity([...addressMap.values()]);

        // phone
        const phoneArray = profile.phone || [];
        const phoneListArray = profile.phoneList || [];
        const phoneNumbers = [...new Set([...phoneArray, ...phoneListArray])].sort((a, b) => parseInt(a) - parseInt(b));

        // relatives
        const relatives = [...new Set(profile.relativesList)].sort();

        // aliases
        const alternativeNames = [...new Set(profile.alternativeNamesList)].sort();

        return {
            name: profile.name,
            alternativeNames,
            age: profile.age,
            addresses,
            phoneNumbers,
            relatives,
            ...profile.profileUrl,
        };
    }

    /**
     * Example input to this:
     *
     * ```json
     * {
     *   "key": "age",
     *   "value": {
     *     "selector": ".//div[@class='col-md-8']/div[2]"
     *   },
     *   "elementValues": ["Age 71"]
     * }
     * ```
     *
     * @param {string} outputFieldKey
     * @param {ExtractProfileProperty} extractorParams
     * @param {string[]} elementValues
     * @return {any}
     */
    function extractValue(outputFieldKey, extractorParams, elementValues) {
        switch (outputFieldKey) {
            case 'age':
                return new AgeExtractor().extract(elementValues, extractorParams);
            case 'name':
                return new NameExtractor().extract(elementValues, extractorParams);

            // all addresses are processed the same way
            case 'addressFull':
            case 'addressFullList':
                return new AddressFullExtractor().extract(elementValues, extractorParams);
            case 'addressCityState':
            case 'addressCityStateList':
                return new CityStateExtractor().extract(elementValues, extractorParams);

            case 'alternativeNamesList':
                return new AlternativeNamesExtractor().extract(elementValues, extractorParams);
            case 'relativesList':
                return new RelativesExtractor().extract(elementValues, extractorParams);
            case 'phone':
            case 'phoneList':
                return new PhoneExtractor().extract(elementValues, extractorParams);
            case 'profileUrl':
                return new ProfileUrlExtractor().extract(elementValues, extractorParams);
        }
        return null;
    }

    /**
     * A list of transforms that should be applied to the profile after extraction/aggregation
     *
     * @param {Record<string, any>} profile
     * @param {Record<string, ExtractProfileProperty>} params
     * @return {Promise<Record<string, any>>}
     */
    async function applyPostTransforms(profile, params) {
        /** @type {import("../types.js").AsyncProfileTransform[]} */
        const transforms = [
            // creates a hash if needed
            new ProfileHashTransformer(),
        ];

        let output = profile;
        for (const knownTransform of transforms) {
            output = await knownTransform.transform(output, params);
        }

        return output;
    }

    /**
     * @param {string} inputList
     * @param {string} [separator]
     * @return {string[]}
     */
    function stringToList(inputList, separator) {
        const defaultSeparator = /[|\n•·]/;
        return cleanArray(inputList.split(separator || defaultSeparator));
    }

    // For extraction
    const rules = {
        profileUrl: function (link) {
            return link?.href ?? null;
        },
    };

    /**
     * Remove common prefixes and suffixes such as
     *
     * - AKA: <value>
     * - <value> + 1 more
     * - <value> -
     *
     * @param {string} elementValue
     * @return {string}
     */
    function removeCommonSuffixesAndPrefixes(elementValue) {
        const regexes = [
            // match text such as +3 more when it appears at the end of a string
            /\+\s*\d+.*$/,
        ];
        // strings that are always safe to remove from the start
        const startsWith = [
            'Associated persons:',
            'AKA:',
            'Known as:',
            'Also known as:',
            'Has lived in:',
            'Used to live:',
            'Used to live in:',
            'Lives in:',
            'Related to:',
            'No other aliases.',
            'RESIDES IN',
        ];

        // strings that are always safe to remove from the end
        const endsWith = [' -', 'years old'];

        for (const regex of regexes) {
            elementValue = elementValue.replace(regex, '').trim();
        }
        for (const prefix of startsWith) {
            if (elementValue.startsWith(prefix)) {
                elementValue = elementValue.slice(prefix.length).trim();
            }
        }
        for (const suffix of endsWith) {
            if (elementValue.endsWith(suffix)) {
                elementValue = elementValue.slice(0, 0 - suffix.length).trim();
            }
        }

        return elementValue;
    }

    function generatePhoneNumber() {
        /**
         * 3 digits, 2-8, last two digits technically can't end in two 1s, but we'll ignore that requirement
         * Source: https://math.stackexchange.com/questions/920972/how-many-different-phone-numbers-are-possible-within-an-area-code/1115411#1115411
         */
        const areaCode = generateRandomInt(200, 899).toString();

        // 555-0100 through 555-0199 are for fictional use (https://en.wikipedia.org/wiki/555_(telephone_number)#Fictional_usage)
        const exchangeCode = '555';
        const lineNumber = generateRandomInt(100, 199).toString().padStart(4, '0');

        return `${areaCode}${exchangeCode}${lineNumber}`;
    }

    function generateZipCode() {
        const zipCode = generateRandomInt(10000, 99999).toString();
        return zipCode;
    }

    function generateStreetAddress() {
        const streetDigits = generateRandomInt(1, 5);
        const streetNumber = generateRandomInt(2, streetDigits * 1000);
        const streetNames = [
            'Main',
            'Elm',
            'Maple',
            'Oak',
            'Pine',
            'Cedar',
            'Hill',
            'Lake',
            'Sunset',
            'Washington',
            'Lincoln',
            'Marshall',
            'Spring',
            'Ridge',
            'Valley',
            'Meadow',
            'Forest',
        ];
        const streetName = streetNames[generateRandomInt(0, streetNames.length - 1)];
        const suffixes = ['', 'St', 'Ave', 'Blvd', 'Rd', 'Ct', 'Dr', 'Ln', 'Pkwy', 'Pl', 'Ter', 'Way'];
        const suffix = suffixes[generateRandomInt(0, suffixes.length - 1)];

        return `${streetNumber} ${streetName}${suffix ? ' ' + suffix : ''}`;
    }

    /**
     * @param {Record<string, any>} action
     * @param {Record<string, any>} userData
     * @param {Document | HTMLElement} root
     * @return {import('../types.js').ActionResponse}
     */
    function fillForm(action, userData, root = document) {
        const form = getElement(root, action.selector);
        if (!form) return new ErrorResponse({ actionID: action.id, message: 'missing form' });
        if (!userData) return new ErrorResponse({ actionID: action.id, message: 'user data was absent' });

        // ensure the element is in the current viewport
        form.scrollIntoView?.();

        const results = fillMany(form, action.elements, userData);

        const errors = results
            .filter((x) => x.result === false)
            .map((x) => {
                if ('error' in x) return x.error;
                return 'unknown error';
            });

        if (errors.length > 0) {
            return new ErrorResponse({ actionID: action.id, message: errors.join(', ') });
        }

        return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: null });
    }

    /**
     * Try to fill form elements. Collecting results + warnings for reporting.
     * @param {HTMLElement} root
     * @param {{selector: string; type: string; min?: string; max?: string;}[]} elements
     * @param {Record<string, any>} data
     * @return {({result: true} | {result: false; error: string})[]}
     */
    function fillMany(root, elements, data) {
        const results = [];

        for (const element of elements) {
            const inputElem = getElement(root, element.selector);
            if (!inputElem) {
                results.push({ result: false, error: `element not found for selector: "${element.selector}"` });
                continue;
            }

            if (element.type === '$file_id$') {
                results.push(setImageUpload(inputElem));
            } else if (element.type === '$generated_phone_number$') {
                results.push(setValueForInput(inputElem, generatePhoneNumber()));
            } else if (element.type === '$generated_zip_code$') {
                results.push(setValueForInput(inputElem, generateZipCode()));
            } else if (element.type === '$generated_random_number$') {
                if (!element.min || !element.max) {
                    results.push({
                        result: false,
                        error: `element found with selector '${element.selector}', but missing min and/or max values`,
                    });
                    continue;
                }
                const minInt = parseInt(element?.min);
                const maxInt = parseInt(element?.max);

                if (isNaN(minInt) || isNaN(maxInt)) {
                    results.push({
                        result: false,
                        error: `element found with selector '${element.selector}', but min or max was not a number`,
                    });
                    continue;
                }

                results.push(setValueForInput(inputElem, generateRandomInt(parseInt(element.min), parseInt(element.max)).toString()));
            } else if (element.type === '$generated_street_address$') {
                results.push(setValueForInput(inputElem, generateStreetAddress()));

                // This is a composite of existing (but separate) city and state fields
            } else if (element.type === 'cityState') {
                if (!Object.prototype.hasOwnProperty.call(data, 'city') || !Object.prototype.hasOwnProperty.call(data, 'state')) {
                    results.push({
                        result: false,
                        error: `element found with selector '${element.selector}', but data didn't contain the keys 'city' and 'state'`,
                    });
                    continue;
                }
                results.push(setValueForInput(inputElem, data.city + ', ' + data.state));
            } else {
                if (isElementTypeOptional(element.type)) {
                    continue;
                }
                if (!Object.prototype.hasOwnProperty.call(data, element.type)) {
                    results.push({
                        result: false,
                        error: `element found with selector '${element.selector}', but data didn't contain the key '${element.type}'`,
                    });
                    continue;
                }
                if (!data[element.type]) {
                    results.push({
                        result: false,
                        error: `data contained the key '${element.type}', but it wasn't something we can fill: ${data[element.type]}`,
                    });
                    continue;
                }
                results.push(setValueForInput(inputElem, data[element.type]));
            }
        }

        return results;
    }

    /**
     * Returns whether an element type is optional, allowing some checks to be skipped
     *
     * @param { string } type
     * @returns Boolean
     */
    function isElementTypeOptional(type) {
        if (type === 'middleName') {
            return true;
        }

        return false;
    }

    /**
     * NOTE: This code comes from Autofill, the reasoning is to make React autofilling work on Chrome and Safari.
     *
     * Ensures the value is set properly and dispatches events to simulate real user action
     *
     * @param {HTMLElement} el
     * @param {string} val
     * @return {{result: true} | {result: false; error: string}}
     */
    function setValueForInput(el, val) {
        // Access the original setters
        // originally needed to bypass React's implementation on mobile
        let target;
        if (el.tagName === 'INPUT') target = window.HTMLInputElement;
        if (el.tagName === 'SELECT') target = window.HTMLSelectElement;

        // Bail early if we cannot fill this element
        if (!target) {
            return { result: false, error: `input type was not supported: ${el.tagName}` };
        }

        const originalSet = Object.getOwnPropertyDescriptor(target.prototype, 'value')?.set;

        // ensure it's a callable method
        if (!originalSet || typeof originalSet.call !== 'function') {
            return { result: false, error: 'cannot access original value setter' };
        }

        try {
            // separate strategies for inputs vs selects
            if (el.tagName === 'INPUT') {
                // set the input value
                el.dispatchEvent(new Event('keydown', { bubbles: true }));
                originalSet.call(el, val);
                const events = [
                    new Event('input', { bubbles: true }),
                    new Event('keyup', { bubbles: true }),
                    new Event('change', { bubbles: true }),
                ];
                events.forEach((ev) => el.dispatchEvent(ev));
                originalSet.call(el, val);
                events.forEach((ev) => el.dispatchEvent(ev));
                el.blur();
            } else if (el.tagName === 'SELECT') {
                // set the select value
                originalSet.call(el, val);
                const events = [
                    new Event('mousedown', { bubbles: true }),
                    new Event('mouseup', { bubbles: true }),
                    new Event('click', { bubbles: true }),
                    new Event('change', { bubbles: true }),
                ];
                events.forEach((ev) => el.dispatchEvent(ev));
                events.forEach((ev) => el.dispatchEvent(ev));
                el.blur();
            }

            return { result: true };
        } catch (e) {
            return { result: false, error: `setValueForInput exception: ${e}` };
        }
    }

    /**
     * @param element
     * @return {{result: true}|{result: false, error: string}}
     */
    function setImageUpload(element) {
        const base64PNG = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/B8AAusB9VF9PmUAAAAASUVORK5CYII=';
        try {
            // Convert the Base64 string to a Blob
            const binaryString = window.atob(base64PNG);

            // Convert binary string to a Typed Array
            const length = binaryString.length;
            const bytes = new Uint8Array(length);
            for (let i = 0; i < length; i++) {
                bytes[i] = binaryString.charCodeAt(i);
            }

            // Create the Blob from the Typed Array
            const blob = new Blob([bytes], { type: 'image/png' });

            // Create a DataTransfer object and append the Blob
            const dataTransfer = new DataTransfer();
            dataTransfer.items.add(new File([blob], 'id.png', { type: 'image/png' }));

            // Step 4: Assign the Blob to the Input Element
            /** @type {any} */
            element.files = dataTransfer.files;
            return { result: true };
        } catch (e) {
            // failed
            return { result: false, error: e.toString() };
        }
    }

    /**
     * @param {object} args
     * @param {string} args.token
     */
    function captchaCallback(args) {
        const clients = findRecaptchaClients(globalThis);

        // if a client was found, check there was a function
        if (clients.length === 0) {
            return console.log('cannot find clients');
        }

        if (typeof clients[0].function === 'function') {
            try {
                clients[0].function(args.token);
                console.log('called function with path', clients[0].callback);
            } catch (e) {
                console.error('could not call function');
            }
        }

        /**
         * Try to find a callback in a path such as ['___grecaptcha_cfg', 'clients', '0', 'U', 'U', 'callback']
         * @param {Record<string, any>} target
         */
        function findRecaptchaClients(target) {
            if (typeof target.___grecaptcha_cfg === 'undefined') {
                console.log('target.___grecaptcha_cfg not found in ', location.href);
                return [];
            }
            return Object.entries(target.___grecaptcha_cfg.clients || {}).map(([cid, client]) => {
                const cidNumber = parseInt(cid, 10);
                const data = {
                    id: cid,
                    version: cidNumber >= 10000 ? 'V3' : 'V2',
                };
                const objects = Object.entries(client).filter(([, value]) => value && typeof value === 'object');

                objects.forEach(([toplevelKey, toplevel]) => {
                    const found = Object.entries(toplevel).find(
                        ([, value]) => value && typeof value === 'object' && 'sitekey' in value && 'size' in value,
                    );

                    if (
                        typeof toplevel === 'object' &&
                        typeof HTMLElement !== 'undefined' &&
                        toplevel instanceof HTMLElement &&
                        toplevel.tagName === 'DIV'
                    ) {
                        data.pageurl = toplevel.baseURI;
                    }

                    if (found) {
                        const [sublevelKey, sublevel] = found;

                        data.sitekey = sublevel.sitekey;
                        const callbackKey = data.version === 'V2' ? 'callback' : 'promise-callback';
                        const callback = sublevel[callbackKey];
                        if (!callback) {
                            data.callback = null;
                            data.function = null;
                        } else {
                            data.function = callback;
                            data.callback = ['___grecaptcha_cfg', 'clients', cid, toplevelKey, sublevelKey, callbackKey];
                        }
                    }
                });
                return data;
            });
        }
    }

    /**
     * Gets the captcha information to send to the backend
     *
     * @param action
     * @param {Document | HTMLElement} root
     * @return {import('../types.js').ActionResponse}
     */
    function getCaptchaInfo(action, root = document) {
        const pageUrl = window.location.href;
        const captchaDiv = getElement(root, action.selector);

        // if 'captchaDiv' was missing, cannot continue
        if (!captchaDiv) {
            return new ErrorResponse({ actionID: action.id, message: `could not find captchaDiv with selector ${action.selector}` });
        }

        // try 2 different captures
        const captcha =
            getElement(captchaDiv, '[src^="https://www.google.com/recaptcha"]') ||
            getElement(captchaDiv, '[src^="https://newassets.hcaptcha.com/captcha"');

        // ensure we have the elements
        if (!captcha) return new ErrorResponse({ actionID: action.id, message: 'could not find captcha' });
        if (!('src' in captcha)) return new ErrorResponse({ actionID: action.id, message: 'missing src attribute' });

        const captchaUrl = String(captcha.src);
        let captchaType;
        let siteKey;

        if (captchaUrl.includes('recaptcha/api2')) {
            captchaType = 'recaptcha2';
            siteKey = new URL(captchaUrl).searchParams.get('k');
        } else if (captchaUrl.includes('recaptcha/enterprise')) {
            captchaType = 'recaptchaEnterprise';
            siteKey = new URL(captchaUrl).searchParams.get('k');
        } else if (captchaUrl.includes('hcaptcha.com/captcha/v1')) {
            captchaType = 'hcaptcha';
            // hcaptcha sitekey may be in either
            if (captcha instanceof Element) {
                siteKey = captcha.getAttribute('data-sitekey');
            }
            if (!siteKey) {
                try {
                    // `new URL(...)` can throw, so it's valid to wrap this in try/catch
                    siteKey = new URL(captchaUrl).searchParams.get('sitekey');
                } catch (e) {
                    console.warn('error parsing captchaUrl', captchaUrl);
                }
            }
            if (!siteKey) {
                try {
                    const hash = new URL(captchaUrl).hash.slice(1);
                    siteKey = new URLSearchParams(hash).get('sitekey');
                } catch (e) {
                    console.warn('error parsing captchaUrl hash', captchaUrl);
                }
            }
        }

        if (!captchaType) {
            return new ErrorResponse({ actionID: action.id, message: 'Could not extract captchaType.' });
        }
        if (!siteKey) {
            return new ErrorResponse({ actionID: action.id, message: 'Could not extract siteKey.' });
        }

        // Remove query params (which may include PII)
        const pageUrlWithoutParams = pageUrl?.split('?')[0];

        const responseData = {
            siteKey,
            url: pageUrlWithoutParams,
            type: captchaType,
        };

        return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: responseData });
    }

    /**
     * Takes the solved captcha token and injects it into the page to solve the captcha
     *
     * @param action
     * @param {string} token
     * @param {Document} root
     * @return {import('../types.js').ActionResponse}
     */
    function solveCaptcha(action, token, root = document) {
        const selectors = ['h-captcha-response', 'g-recaptcha-response'];
        let solved = false;

        for (const selector of selectors) {
            const match = root.getElementsByName(selector)[0];
            if (match) {
                match.innerHTML = token;
                solved = true;
                break;
            }
        }

        if (solved) {
            const json = JSON.stringify({ token });

            const javascript = `;(function(args) {
            ${captchaCallback.toString()};
            captchaCallback(args);
        })(${json});`;

            return new SuccessResponse({
                actionID: action.id,
                actionType: action.actionType,
                response: { callback: { eval: javascript } },
            });
        }

        return new ErrorResponse({ actionID: action.id, message: 'could not solve captcha' });
    }

    /**
     * @param {Record<string, any>} action
     * @param {Record<string, any>} userData
     * @param {Document | HTMLElement} root
     * @return {import('../types.js').ActionResponse}
     */
    function click(action, userData, root = document) {
        /** @type {Array<any> | null} */
        let elements = [];

        if (action.choices?.length) {
            const choices = evaluateChoices(action, userData);

            // If we returned null, the intention is to skip execution, so return success
            if (choices === null) {
                return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: null });
            } else if ('error' in choices) {
                return new ErrorResponse({ actionID: action.id, message: `Unable to evaluate choices: ${choices.error}` });
            } else if (!('elements' in choices)) {
                return new ErrorResponse({ actionID: action.id, message: 'No elements provided to click action' });
            }

            elements = choices.elements;
        } else {
            if (!('elements' in action)) {
                return new ErrorResponse({ actionID: action.id, message: 'No elements provided to click action' });
            }

            elements = action.elements;
        }

        if (!elements || !elements.length) {
            return new ErrorResponse({ actionID: action.id, message: 'No elements provided to click action' });
        }

        // there can be multiple elements provided by the action
        for (const element of elements) {
            let rootElement;

            try {
                rootElement = selectRootElement(element, userData, root);
            } catch (error) {
                return new ErrorResponse({ actionID: action.id, message: `Could not find root element: ${error.message}` });
            }

            const elements = getElements(rootElement, element.selector);

            if (!elements?.length) {
                if (element.failSilently) {
                    return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: null });
                }

                return new ErrorResponse({
                    actionID: action.id,
                    message: `could not find element to click with selector '${element.selector}'!`,
                });
            }

            const loopLength = element.multiple && element.multiple === true ? elements.length : 1;

            for (let i = 0; i < loopLength; i++) {
                const elem = elements[i];

                if ('disabled' in elem) {
                    if (elem.disabled && !element.failSilently) {
                        return new ErrorResponse({ actionID: action.id, message: `could not click disabled element ${element.selector}'!` });
                    }
                }
                if ('click' in elem && typeof elem.click === 'function') {
                    elem.click();
                }
            }
        }

        return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: null });
    }

    /**
     * @param {{parent?: {profileMatch?: Record<string, any>}}} clickElement
     * @param {Record<string, any>} userData
     * @param {Document | HTMLElement} root
     * @return {Node}
     */
    function selectRootElement(clickElement, userData, root = document) {
        // if there's no 'parent' field, just use the document
        if (!clickElement.parent) return root;

        // if the 'parent' field contains 'profileMatch', try to match it
        if (clickElement.parent.profileMatch) {
            const extraction = extractProfiles(clickElement.parent.profileMatch, userData, root);
            if ('results' in extraction) {
                const sorted = extraction.results.filter((x) => x.result === true).sort((a, b) => b.score - a.score);
                const first = sorted[0];
                if (first && first.element) {
                    return first.element;
                }
            }
        }

        throw new Error('`parent` was present on the element, but the configuration is not supported');
    }

    /**
     * Evaluate a comparator and return the appropriate function
     * @param {string} operator
     * @returns {(a: any, b: any) => boolean}
     */
    function getComparisonFunction(operator) {
        switch (operator) {
            case '=':
            case '==':
            case '===':
                return (a, b) => a === b;
            case '!=':
            case '!==':
                return (a, b) => a !== b;
            case '<':
                return (a, b) => a < b;
            case '<=':
                return (a, b) => a <= b;
            case '>':
                return (a, b) => a > b;
            case '>=':
                return (a, b) => a >= b;
            default:
                throw new Error(`Invalid operator: ${operator}`);
        }
    }

    /**
     * Evaluates the defined choices (and/or the default) and returns an array of the elements to be clicked
     *
     * @param {Record<string, any>} action
     * @param {Record<string, any>} userData
     * @returns {{ elements: [Record<string, any>] } | { error: String } | null}
     */
    function evaluateChoices(action, userData) {
        if ('elements' in action) {
            return { error: 'Elements should be nested inside of choices' };
        }

        for (const choice of action.choices) {
            if (!('condition' in choice) || !('elements' in choice)) {
                return { error: 'All choices must have a condition and elements' };
            }

            const comparison = runComparison(choice, action, userData);

            if ('error' in comparison) {
                return { error: comparison.error };
            } else if ('result' in comparison && comparison.result === true) {
                return { elements: choice.elements };
            }
        }

        // If there's no default defined, return an error.
        if (!('default' in action)) {
            return { error: 'All conditions failed and no default action was provided' };
        }

        // If there is a default and it's null (meaning skip any further action) return success.
        if (action.default === null) {
            // Nothing else to do, return null
            return null;
        }

        // If the default is defined and not null (without elements), return an error.
        if (!('elements' in action.default)) {
            return { error: 'Default action must have elements' };
        }

        return { elements: action.default.elements };
    }

    /**
     * Attempts to turn a choice definition into an executable comparison and returns the result
     *
     * @param {Record<string, any>} choice
     * @param {Record<string, any>} action
     * @param {Record<string, any>} userData
     * @returns {{ result: Boolean } | { error: String }}
     */
    function runComparison(choice, action, userData) {
        let compare;
        let left;
        let right;

        try {
            compare = getComparisonFunction(choice.condition.operation);
        } catch (error) {
            return { error: `Unable to get comparison function: ${error.message}` };
        }

        try {
            left = processTemplateStringWithUserData(choice.condition.left, action, userData);
            right = processTemplateStringWithUserData(choice.condition.right, action, userData);
        } catch (error) {
            return { error: `Unable to resolve left/right comparison arguments: ${error.message}` };
        }

        let result;

        try {
            result = compare(left, right);
        } catch (error) {
            return { error: `Comparison failed with the following error: ${error.message}` };
        }

        return { result };
    }

    /**
     * @param {Record<string, any>} action
     * @param {Document} root
     * @return {import('../types.js').ActionResponse}
     */
    function expectation(action, root = document) {
        const results = expectMany(action.expectations, root);

        // filter out good results + silent failures, leaving only fatal errors
        const errors = results
            .filter((x, index) => {
                if (x.result === true) return false;
                if (action.expectations[index].failSilently) return false;
                return true;
            })
            .map((x) => {
                return 'error' in x ? x.error : 'unknown error';
            });

        if (errors.length > 0) {
            return new ErrorResponse({ actionID: action.id, message: errors.join(', ') });
        }

        // only run later actions if every expectation was met
        const runActions = results.every((x) => x.result === true);

        if (action.actions?.length && runActions) {
            return new SuccessResponse({
                actionID: action.id,
                actionType: action.actionType,
                response: null,
                next: action.actions,
            });
        }

        return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: null });
    }

    /**
     * Return a true/false result for every expectation
     *
     * @param {import("../types").Expectation[]} expectations
     * @param {Document | HTMLElement} root
     * @return {import("../types").BooleanResult[]}
     */
    function expectMany(expectations, root) {
        return expectations.map((expectation) => {
            switch (expectation.type) {
                case 'element':
                    return elementExpectation(expectation, root);
                case 'text':
                    return textExpectation(expectation, root);
                case 'url':
                    return urlExpectation(expectation);
                default: {
                    return {
                        result: false,
                        error: `unknown expectation type: ${expectation.type}`,
                    };
                }
            }
        });
    }

    /**
     * Verify that an element exists. If the `.parent` property exists,
     * scroll it into view first
     *
     * @param {import("../types").Expectation} expectation
     * @param {Document | HTMLElement} root
     * @return {import("../types").BooleanResult}
     */
    function elementExpectation(expectation, root) {
        if (expectation.parent) {
            const parent = getElement(root, expectation.parent);
            if (!parent) {
                return {
                    result: false,
                    error: `parent element not found with selector: ${expectation.parent}`,
                };
            }
            parent.scrollIntoView();
        }

        const elementExists = getElement(root, expectation.selector) !== null;

        if (!elementExists) {
            return {
                result: false,
                error: `element with selector ${expectation.selector} not found.`,
            };
        }
        return { result: true };
    }

    /**
     * Check that an element includes a given text string
     *
     * @param {import("../types").Expectation} expectation
     * @param {Document | HTMLElement} root
     * @return {import("../types").BooleanResult}
     */
    function textExpectation(expectation, root) {
        // get the target element first
        const elem = getElement(root, expectation.selector);
        if (!elem) {
            return {
                result: false,
                error: `element with selector ${expectation.selector} not found.`,
            };
        }

        // todo: remove once we have stronger types
        if (!expectation.expect) {
            return {
                result: false,
                error: "missing key: 'expect'",
            };
        }

        // todo: is this too strict a match? we may also want to try innerText
        const textExists = Boolean(elem?.textContent?.includes(expectation.expect));

        if (!textExists) {
            return {
                result: false,
                error: `expected element with selector ${expectation.selector} to have text: ${expectation.expect}, but it didn't`,
            };
        }

        return { result: true };
    }

    /**
     * Check that the current URL includes a given string
     *
     * @param {import("../types").Expectation} expectation
     * @return {import("../types").BooleanResult}
     */
    function urlExpectation(expectation) {
        const url = window.location.href;

        // todo: remove once we have stronger types
        if (!expectation.expect) {
            return {
                result: false,
                error: "missing key: 'expect'",
            };
        }

        if (!url.includes(expectation.expect)) {
            return {
                result: false,
                error: `expected URL to include ${expectation.expect}, but it didn't`,
            };
        }

        return { result: true };
    }

    /**
     * @param {object} action
     * @param {string} action.id
     * @param {string} [action.dataSource] - optional data source
     * @param {"extract" | "fillForm" | "click" | "expectation" | "getCaptchaInfo" | "solveCaptcha" | "navigate"} action.actionType
     * @param {Record<string, any>} inputData
     * @param {Document} [root] - optional root element
     * @return {Promise<import('./types.js').ActionResponse>}
     */
    async function execute(action, inputData, root = document) {
        try {
            switch (action.actionType) {
                case 'navigate':
                    return buildUrl(action, data(action, inputData, 'userProfile'));
                case 'extract':
                    return await extract(action, data(action, inputData, 'userProfile'), root);
                case 'click':
                    return click(action, data(action, inputData, 'userProfile'), root);
                case 'expectation':
                    return expectation(action, root);
                case 'fillForm':
                    return fillForm(action, data(action, inputData, 'extractedProfile'), root);
                case 'getCaptchaInfo':
                    return getCaptchaInfo(action, root);
                case 'solveCaptcha':
                    return solveCaptcha(action, data(action, inputData, 'token'), root);
                default: {
                    return new ErrorResponse({
                        actionID: action.id,
                        message: `unimplemented actionType: ${action.actionType}`,
                    });
                }
            }
        } catch (e) {
            console.log('unhandled exception: ', e);
            return new ErrorResponse({
                actionID: action.id,
                message: `unhandled exception: ${e.message}`,
            });
        }
    }

    /**
     * @param {{dataSource?: string}} action
     * @param {Record<string, any>} data
     * @param {string} defaultSource
     */
    function data(action, data, defaultSource) {
        if (!data) return null;
        const source = action.dataSource || defaultSource;
        if (Object.prototype.hasOwnProperty.call(data, source)) {
            return data[source];
        }
        return null;
    }

    const DEFAULT_RETRY_CONFIG = {
        interval: { ms: 0 },
        maxAttempts: 1,
    };

    /**
     * A generic retry mechanism for synchronous functions that return
     * a 'success' or 'error' response
     *
     * @template T
     * @template {{ success: T } | { error: { message: string } }} FnReturn
     * @param {() => Promise<FnReturn>} fn
     * @param {typeof DEFAULT_RETRY_CONFIG} [config]
     * @return {Promise<{ result: FnReturn | undefined, exceptions: string[] }>}
     */
    async function retry(fn, config = DEFAULT_RETRY_CONFIG) {
        let lastResult;
        const exceptions = [];
        for (let i = 0; i < config.maxAttempts; i++) {
            try {
                lastResult = await Promise.resolve(fn());
            } catch (e) {
                exceptions.push(e.toString());
            }

            // stop when there's a good result to return
            // since fn() returns either { success: <value> } or { error: ... }
            if (lastResult && 'success' in lastResult) break;

            // don't pause on the last item
            if (i === config.maxAttempts - 1) break;

            await new Promise((resolve) => setTimeout(resolve, config.interval.ms));
        }

        return { result: lastResult, exceptions };
    }

    /**
     * @typedef {import("./broker-protection/types.js").ActionResponse} ActionResponse
     */
    class BrokerProtection extends ContentFeature {
        init() {
            this.messaging.subscribe('onActionReceived', async (/** @type {any} */ params) => {
                try {
                    const action = params.state.action;
                    const data = params.state.data;

                    if (!action) {
                        return this.messaging.notify('actionError', { error: 'No action found.' });
                    }

                    const { results, exceptions } = await this.exec(action, data);

                    if (results) {
                        // there might only be a single result.
                        const parent = results[0];
                        const errors = results.filter((x) => 'error' in x);

                        // if there are no secondary actions, or just no errors in general, just report the parent action
                        if (results.length === 1 || errors.length === 0) {
                            return this.messaging.notify('actionCompleted', { result: parent });
                        }

                        // here we must have secondary actions that failed.
                        // so we want to create an error response with the parent ID, but with the errors messages from
                        // the children
                        const joinedErrors = errors.map((x) => x.error.message).join(', ');
                        const response = new ErrorResponse({
                            actionID: action.id,
                            message: 'Secondary actions failed: ' + joinedErrors,
                        });

                        return this.messaging.notify('actionCompleted', { result: response });
                    } else {
                        return this.messaging.notify('actionError', { error: 'No response found, exceptions: ' + exceptions.join(', ') });
                    }
                } catch (e) {
                    console.log('unhandled exception: ', e);
                    this.messaging.notify('actionError', { error: e.toString() });
                }
            });
        }

        /**
         * Recursively execute actions with the same dataset, collecting all results/exceptions for
         * later analysis
         * @param {any} action
         * @param {Record<string, any>} data
         * @return {Promise<{results: ActionResponse[], exceptions: string[]}>}
         */
        async exec(action, data) {
            const retryConfig = this.retryConfigFor(action);
            const { result, exceptions } = await retry(() => execute(action, data), retryConfig);

            if (result) {
                if ('success' in result && Array.isArray(result.success.next)) {
                    const nextResults = [];
                    const nextExceptions = [];

                    for (const nextAction of result.success.next) {
                        const { results: subResults, exceptions: subExceptions } = await this.exec(nextAction, data);

                        nextResults.push(...subResults);
                        nextExceptions.push(...subExceptions);
                    }
                    return { results: [result, ...nextResults], exceptions: exceptions.concat(nextExceptions) };
                }
                return { results: [result], exceptions: [] };
            }
            return { results: [], exceptions };
        }

        /**
         * Define default retry configurations for certain actions
         *
         * @param {any} action
         * @returns
         */
        retryConfigFor(action) {
            /**
             * Note: We're not currently guarding against concurrent actions here
             * since the native side contains the scheduling logic to prevent it.
             */
            const retryConfig = action.retry?.environment === 'web' ? action.retry : undefined;
            /**
             * Special case for the exact action
             */
            if (!retryConfig && action.actionType === 'extract') {
                return {
                    interval: { ms: 1000 },
                    maxAttempts: 30,
                };
            }
            /**
             * Special case for when expectation contains a check for an element, retry it
             */
            if (!retryConfig && action.actionType === 'expectation') {
                if (action.expectations.some((x) => x.type === 'element')) {
                    return {
                        interval: { ms: 1000 },
                        maxAttempts: 30,
                    };
                }
            }
            return retryConfig;
        }
    }

    var platformFeatures = {
        ddg_feature_brokerProtection: BrokerProtection
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

        const featureNames = platformSupport["android-broker-protection"] ;

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
