(function() {
    const ddgObj = window.$OBJECT_NAME$;
    const delay = $DELAY$;
    const postInitialPing = $POST_INITIAL_PING$;
    const replyToNativeMessages = $REPLY_TO_NATIVE_MESSAGES$;
    const messagePrefix = 'webViewCompat $SCRIPT_ID$';

    // ============================================================================
    // MESSAGE HANDLING (ORIGINAL WEBVIEWCOMPAT FUNCTIONALITY)
    // ============================================================================

    const supportedMessages = ["ContextMenuOpened", "PageStarted"];
    const webViewCompatPingMessage = messagePrefix + 'Ping:' + window.location.href + ' ' + delay + 'ms';

    // Send initial ping if configured
    if (postInitialPing) {
        console.log('$SCRIPT_ID$ Posting initial ping...');
        if (delay > 0) {
            setTimeout(() => {
                ddgObj.postMessage(webViewCompatPingMessage);
            }, delay);
        } else {
            ddgObj.postMessage(webViewCompatPingMessage);
        }
    }

    // Listen to ddgObj messages
    ddgObj.addEventListener('message', function(event) {
        console.log("[complex script] $OBJECT_NAME$-$SCRIPT_ID$ received", event.data);

        if (replyToNativeMessages && supportedMessages.includes(event.data)) {
            const response = messagePrefix + event.data + " from $OBJECT_NAME$-$SCRIPT_ID$";
            ddgObj.postMessage(response);
            console.log('[complex-script]-$SCRIPT_ID$ Sent response:', response);
        }
    });

    // Listen to window messages
    window.addEventListener('message', function(event) {
        console.log("[complex-script] window-$SCRIPT_ID$ received", event.data);
        
        if (replyToNativeMessages && supportedMessages.includes(event.data)) {
            const response = messagePrefix + event.data + " from window-$SCRIPT_ID$";
            ddgObj.postMessage(response);
            console.log('[complex-script]-$SCRIPT_ID$ Sent response:', response);
        }
    });

    const config = {"features":{"breakageReporting":{"state":"enabled","exceptions":[{"domain":"marvel.com"},{"domain":"noaprints.com"}],"settings":{"conditionalChanges":[{"condition":{"injectName":"android-adsjs"},"patchSettings":[{"op":"add","path":"\/additionalCheck","value":"disabled"}]}]},"hash":"6413ce9f3177be6181eb61c37aa61940"},"apiManipulation":{"state":"enabled","exceptions":[],"readme":"Example config for apiManipulation to set navigator.plugins to return PDF viewer plugins","settings":{"apiChanges":{},"conditionalChanges":[{"condition":{"injectName":"android-adsjs"},"patchSettings":[{"op":"add","path":"\/additionalCheck","value":"disabled"}]}]},"hash":"e34a9ed1b413fc949f9afce218f94201"},"messageBridge":{"exceptions":[],"settings":{"aiChat":"disabled","subscriptions":"disabled","serpSettings":"disabled","domains":[{"domain":["duckduckgo.com","duck.co","duck.ai"],"patchSettings":[{"op":"replace","path":"\/aiChat","value":"enabled"},{"op":"replace","path":"\/subscriptions","value":"enabled"},{"op":"replace","path":"\/serpSettings","value":"enabled"}]}]},"state":"enabled","minSupportedVersion":52250000,"hash":"305f09b17a3362ba85971b34a2ae6df1"},"navigatorInterface":{"exceptions":[{"domain":"marvel.com"},{"domain":"noaprints.com"}],"settings":{"privilegedDomains":[{"domain":"duckduckgo.com"},{"domain":"duck.co"}]},"state":"enabled","minSupportedVersion":52250000,"hash":"6edf6358b28dc13c98b10270c5f45991"},"cookie":{"settings":{"trackerCookie":"enabled","nonTrackerCookie":"disabled","excludedCookieDomains":[{"domain":"accounts.google.com","reason":"On some Google sign-in flows, there is an error after entering username and proceeding: 'Your browser has cookies disabled. Make sure that your cookies are enabled and try again.'"},{"domain":"pay.google.com","reason":"After sign-in for Google Pay flows, there is repeated flickering and a loading spinner, preventing the flow from proceeding."},{"domain":"payments.google.com","reason":"After sign-in for Google Pay flows (after flickering is resolved), blocking this causes the loading spinner to spin indefinitely, and the payment flow cannot proceed."},{"domain":"docs.google.com","reason":"Embedded Google docs get into redirect loop if signed into a Google account"}],"firstPartyTrackerCookiePolicy":{"threshold":86400,"maxAge":86400},"firstPartyCookiePolicy":{"threshold":604800,"maxAge":604800},"thirdPartyCookieNames":["user_id","__Secure-3PAPISID","SAPISID","APISID"]},"exceptions":[{"domain":"nespresso.com"},{"domain":"optout.aboutads.info"},{"domain":"optout.networkadvertising.org"},{"domain":"news.ti.com"},{"domain":"instructure.com"},{"domain":"duckduckgo.com"},{"domain":"marvel.com"},{"domain":"noaprints.com"}],"state":"enabled","hash":"69f2277bd8ad7e09adcdf4f0e1b6e7c5"},"duckPlayer":{"exceptions":[],"features":{"pip":{"state":"disabled"},"autoplay":{"state":"disabled"},"openInNewTab":{"state":"enabled"},"enableDuckPlayer":{"state":"enabled"},"customError":{"state":"enabled","settings":{"signInRequiredSelector":"[href*=\"\/\/support.google.com\/youtube\/answer\/3037019\"]"}},"nativeUI":{"state":"disabled"},"addCustomEmbedReferer":{"state":"disabled"}},"settings":{"tryDuckPlayerLink":"https:\/\/www.youtube.com\/watch?v=yKWIA-Pys4c","duckPlayerDisabledHelpPageLink":null,"youtubePath":"watch","youtubeEmbedUrl":"youtube-nocookie.com","youTubeUrl":"youtube.com","youTubeReferrerHeaders":["Referer"],"youTubeReferrerQueryParams":["embeds_referring_euri"],"youTubeVideoIDQueryParam":"v","overlays":{"youtube":{"state":"disabled","selectors":{"thumbLink":"a[href^='\/watch']","excludedRegions":["#playlist","ytd-movie-renderer","ytd-grid-movie-renderer"],"videoElement":"[full-bleed-player] #player-full-bleed-container video, #player video","videoElementContainer":"[full-bleed-player] #player-full-bleed-container .html5-video-player, #player .html5-video-player","hoverExcluded":[],"clickExcluded":["ytd-thumbnail-overlay-toggle-button-renderer"],"allowedEventTargets":[".ytp-inline-preview-scrim",".ytd-video-preview","#thumbnail-container","#video-title-link","#video-title","video.video-stream.html5-main-video"],"drawerContainer":"body"},"thumbnailOverlays":{"state":"enabled"},"clickInterception":{"state":"enabled"},"videoOverlays":{"state":"enabled"},"videoDrawer":{"state":"disabled"}},"serpProxy":{"state":"disabled"}},"domains":[{"domain":"www.youtube.com","patchSettings":[{"op":"replace","path":"\/overlays\/youtube\/state","value":"enabled"}]},{"domain":["duckduckgo.com","duck.co"],"patchSettings":[{"op":"replace","path":"\/overlays\/serpProxy\/state","value":"enabled"}]},{"domain":"m.youtube.com","patchSettings":[{"op":"replace","path":"\/overlays\/youtube\/state","value":"enabled"}]}]},"state":"enabled","minSupportedVersion":52160000,"hash":"e6639a3c9da0c7394a51bf64fd020d65"},"elementHiding":{"exceptions":[{"domain":"duckduckgo.com"},{"domain":"duck.co"},{"domain":"gmx.net"},{"domain":"web.de"},{"domain":"marvel.com"},{"domain":"noaprints.com"}],"settings":{"useStrictHideStyleTag":true,"rules":[{"selector":"[id*='gpt-']","type":"hide-empty"},{"selector":"[class*='gpt-']","type":"closest-empty"}]},"state":"enabled","hash":"b69e59a1b851254f9c86275062e37daf"},"fingerprintingBattery":{"exceptions":[{"domain":"litebluesso.usps.gov"},{"domain":"marvel.com"},{"domain":"noaprints.com"}],"state":"enabled","settings":{"conditionalChanges":[{"condition":{"injectName":"android-adsjs"},"patchSettings":[{"op":"add","path":"\/additionalCheck","value":"disabled"}]}]},"hash":"57cf54e4c1b66d467e2cc8ac7d538c6b"},"fingerprintingCanvas":{"settings":{"conditionalChanges":[{"condition":{"injectName":"android-adsjs"},"patchSettings":[{"op":"add","path":"\/additionalCheck","value":"disabled"}]}]},"exceptions":[{"domain":"adidas.com"},{"domain":"godaddy.com"},{"domain":"marvel.com"},{"domain":"noaprints.com"}],"state":"disabled","hash":"178d398b9abca11ce22b3aed8de486db"},"fingerprintingHardware":{"settings":{"keyboard":{"type":"undefined"},"hardwareConcurrency":{"type":"number","value":8},"deviceMemory":{"type":"number","value":4},"conditionalChanges":[{"condition":{"injectName":"android-adsjs"},"patchSettings":[{"op":"add","path":"\/additionalCheck","value":"disabled"}]}]},"exceptions":[{"domain":"www.ticketmaster.com"},{"domain":"gamestop.com"},{"domain":"marvel.com"},{"domain":"noaprints.com"}],"state":"enabled","hash":"a363fd9dde581f3a307379d79267fd6c"},"fingerprintingScreenSize":{"settings":{"availTop":{"type":"number","value":0},"availLeft":{"type":"number","value":0},"colorDepth":{"type":"number","value":24},"pixelDepth":{"type":"number","value":24},"conditionalChanges":[{"condition":{"injectName":"android-adsjs"},"patchSettings":[{"op":"add","path":"\/additionalCheck","value":"disabled"}]}]},"exceptions":[{"domain":"fedex.com"},{"domain":"marvel.com"},{"domain":"noaprints.com"},{"domain":"youtube.com"}],"state":"enabled","hash":"9d7f4e62984d8849cd0041fad54848a1"},"fingerprintingTemporaryStorage":{"exceptions":[{"domain":"fedex.com"},{"domain":"marvel.com"},{"domain":"noaprints.com"},{"domain":"ticketmaster.com"}],"state":"enabled","settings":{"conditionalChanges":[{"condition":{"injectName":"android-adsjs"},"patchSettings":[{"op":"add","path":"\/additionalCheck","value":"disabled"}]}]},"hash":"7cf548230dc0f9164ba05e6e2f8dcc63"},"gpc":{"state":"enabled","exceptions":[{"domain":"boston.com"},{"domain":"marvel.com"},{"domain":"noaprints.com"}],"settings":{"gpcHeaderEnabledSites":["global-privacy-control.glitch.me","globalprivacycontrol.org","washingtonpost.com","nytimes.com","privacytests.org","privacytests2.org","privacy-test-pages.site"],"conditionalChanges":[{"condition":{"injectName":"android-adsjs"},"patchSettings":[{"op":"add","path":"\/additionalCheck","value":"disabled"}]}]},"hash":"bbfe9b8659c53956b70109f1d8d302ac"},"runtimeChecks":{"state":"disabled","exceptions":[{"domain":"marvel.com"},{"domain":"noaprints.com"}],"settings":{},"hash":"1429a8a1ea90fbf887a116e6fe14498c"},"webCompat":{"exceptions":[{"domain":"marvel.com"},{"domain":"noaprints.com"}],"state":"enabled","settings":{"conditionalChanges":[{"condition":{"injectName":"android-adsjs"},"patchSettings":[{"op":"add","path":"\/additionalCheck","value":"disabled"}]}],"cleanIframeValue":{"state":"disabled"},"notification":{"state":"enabled"},"permissions":{"state":"enabled"},"mediaSession":"enabled","presentation":"disabled","viewportWidth":"enabled","viewportWidthLegacy":"enabled","webShare":"enabled","screenLock":"enabled","plainTextViewPort":"enabled","modifyLocalStorage":{"state":"enabled","changes":[]}},"hash":"b3fb65e058fbd38c41d574ddfd0b6645"}},"unprotectedTemporary":[]};
    
    const userUnprotectedDomains = ["example.com", "test.com"];
    
    const userPreferences = {
        "forcedZoomEnabled": false,
        "globalPrivacyControlValue": true,
        "currentCohorts": [
            { "feature": "messageBridge", "subfeature": "aiChat", "cohort": "control" },
            { "feature": "duckPlayer", "subfeature": "pip", "cohort": "treatment" },
            { "feature": "webCompat", "subfeature": "notification", "cohort": "control" },
            { "feature": "fingerprintingHardware", "subfeature": "deviceMemory", "cohort": "treatment" }
        ],
        "versionNumber": 52560000,
        "platform": {
            "name": "android",
            "internal": true
        },
        "locale": "en",
        "desktopModeEnabled": false,
    };

    function processConfig(config, userUnprotectedDomains, userPreferences) {
        const result = {
            featureSettings: {},
            unprotectedDomains: [],
            preferences: userPreferences || {}
        };

        // Extract feature settings
        if (config.features) {
            for (const [featureName, featureConfig] of Object.entries(config.features)) {
                if (featureConfig && typeof featureConfig === 'object') {
                    result.featureSettings[featureName] = {
                        enabled: featureConfig.state === 'enabled',
                        exceptions: featureConfig.exceptions || [],
                        settings: featureConfig.settings || {},
                        hash: featureConfig.hash || ''
                    };
                }
            }
        }

        // Process unprotected domains
        const unprotectedSet = new Set();
        
        // Add from config
        if (Array.isArray(config.unprotectedTemporary)) {
            config.unprotectedTemporary.forEach(domain => unprotectedSet.add(domain));
        }
        
        // Add user domains
        if (Array.isArray(userUnprotectedDomains)) {
            userUnprotectedDomains.forEach(domain => unprotectedSet.add(domain));
        }
        
        // Extract domains from exceptions
        if (config.features) {
            for (const feature of Object.values(config.features)) {
                if (feature.exceptions) {
                    feature.exceptions.forEach(exc => {
                        if (exc.domain) {
                            if (Array.isArray(exc.domain)) {
                                exc.domain.forEach(d => unprotectedSet.add(d));
                            } else {
                                unprotectedSet.add(exc.domain);
                            }
                        }
                    });
                }
            }
        }
        
        result.unprotectedDomains = Array.from(unprotectedSet);

        return result;
    }

    // Process the configuration
    const processedConfig = processConfig(config, userUnprotectedDomains, userPreferences);
    const processedPreferences = userPreferences;
    const allUnprotectedDomains = processedConfig.unprotectedDomains;

    function extractFeatureSettings(features) {
        const settings = {};
        for (const [featureName, featureConfig] of Object.entries(features)) {
            if (featureConfig && typeof featureConfig === 'object') {
                settings[featureName] = {
                    enabled: featureConfig.enabled !== false,
                    exceptions: featureConfig.exceptions || [],
                    settings: featureConfig.settings || {}
                };
            }
        }
        return settings;
    }

    function generateSessionKey() {
        return Math.random().toString(36).substring(2) + Date.now().toString(36);
    }

    function mergeUnprotectedDomains(configDomains, userDomains) {
        const merged = new Set();
        
        if (Array.isArray(configDomains)) {
            configDomains.forEach(domain => merged.add(domain));
        }
        
        if (Array.isArray(userDomains)) {
            userDomains.forEach(domain => merged.add(domain));
        }

        return Array.from(merged);
    }

    // ============================================================================
    // EXPERIMENT/COHORT MANAGEMENT
    // ============================================================================

    class ExperimentManager {
        constructor(experiments = []) {
            this.experiments = experiments;
            this.cohorts = new Map();
            this.parseExperiments(experiments);
        }

        parseExperiments(experiments) {
            if (!Array.isArray(experiments)) return;
            
            experiments.forEach(exp => {
                if (exp.feature && exp.subfeature && exp.cohort) {
                    const key = `${exp.feature}-${exp.subfeature}`;
                    this.cohorts.set(key, exp.cohort);
                }
            });
        }

        getCohort(feature, subfeature) {
            return this.cohorts.get(`${feature}-${subfeature}`);
        }

        isInCohort(feature, subfeature, cohortName) {
            return this.getCohort(feature, subfeature) === cohortName;
        }

        getAllCohorts() {
            return Array.from(this.cohorts.entries()).map(([key, cohort]) => {
                const [feature, subfeature] = key.split('-');
                return { feature, subfeature, cohort };
            });
        }
    }

    const experimentManager = new ExperimentManager(processedPreferences.currentCohorts);

    // ============================================================================
    // FEATURE SETTINGS MANAGER
    // ============================================================================

    class FeatureSettingsManager {
        constructor(featureSettings) {
            this.settings = featureSettings;
        }

        isFeatureEnabled(featureName) {
            return this.settings[featureName]?.enabled === true;
        }

        getFeatureSetting(featureName, settingKey, defaultValue) {
            const feature = this.settings[featureName];
            if (!feature || !feature.settings) return defaultValue;
            return feature.settings[settingKey] !== undefined
                ? feature.settings[settingKey] 
                : defaultValue;
        }

        isExempted(featureName, domain) {
            const feature = this.settings[featureName];
            if (!feature || !feature.exceptions) return false;
            
            return feature.exceptions.some(exception => {
                if (typeof exception === 'string') {
                    return domain.includes(exception);
                }
                if (exception.domain) {
                    return domain.includes(exception.domain);
                }
                return false;
            });
        }

        getAllEnabledFeatures() {
            return Object.entries(this.settings)
                .filter(([_, config]) => config.enabled === true)
                .map(([name, _]) => name);
        }
    }

    const featureSettingsManager = new FeatureSettingsManager(processedConfig.featureSettings);

    // ============================================================================
    // DOMAIN PROTECTION MANAGER
    // ============================================================================

    class DomainProtectionManager {
        constructor(unprotectedDomains) {
            this.unprotectedDomains = new Set(unprotectedDomains);
            this.protectionCache = new Map();
        }

        isDomainUnprotected(domain) {
            if (this.protectionCache.has(domain)) {
                return this.protectionCache.get(domain);
            }

            const isUnprotected = this.checkDomain(domain);
            this.protectionCache.set(domain, isUnprotected);
            return isUnprotected;
        }

        checkDomain(domain) {
            // Exact match
            if (this.unprotectedDomains.has(domain)) {
                return true;
            }

            // Check parent domains
            const parts = domain.split('.');
            for (let i = 1; i < parts.length; i++) {
                const parentDomain = parts.slice(i).join('.');
                if (this.unprotectedDomains.has(parentDomain)) {
                    return true;
                }
            }

            return false;
        }

        addUnprotectedDomain(domain) {
            this.unprotectedDomains.add(domain);
            this.protectionCache.clear();
        }

        removeUnprotectedDomain(domain) {
            this.unprotectedDomains.delete(domain);
            this.protectionCache.clear();
        }

        getAllUnprotectedDomains() {
            return Array.from(this.unprotectedDomains);
        }
    }

    const domainProtectionManager = new DomainProtectionManager(allUnprotectedDomains);

    // ============================================================================
    // LARGE DATA STRUCTURES TO SIMULATE SIZE/COMPLEXITY
    // ============================================================================

    // Mimic the massive domain exemption lists from C-S-S
    const largeDomainList = {
        hosts: { 
            abandonedagent: 1, abhorrentaction: 1, abidingafternoon: 1, abjectact: 1, ablazealley: 1,
            ableangle: 1, abnormalangle: 1, abortiveamount: 1, aboundingapple: 1, abrasiveachiever: 1,
            abruptagent: 1, absentangle: 1, absoluteanswer: 1, absorbedact: 1, absorbingact: 1,
            abstinentangle: 1, abstractapparel: 1, absurdangles: 1, abundantangle: 1, abusiveaction: 1,
            acceptableact: 1, accessibleangle: 1, accidentalangle: 1, accommodatingact: 1, accomplishedact: 1,
            accountableact: 1, accurateangle: 1, accusedagent: 1, acerbicaction: 1, achingaction: 1,
            acidicaction: 1, acknowledgableact: 1, acridaction: 1, activeaction: 1, actualaction: 1,
            acutealarm: 1, adaptableaction: 1, addictedaction: 1, additionalaction: 1, adequateaction: 1,
            adjoiningaction: 1, admirableaction: 1, adorableaction: 1, advancedaction: 1, advantageousaction: 1,
            adventurousaction: 1, adverseaction: 1, advisedaction: 1, aerobicaction: 1, affectionateaction: 1,
            affluentaction: 1, affordableaction: 1, afraidaction: 1, afteraction: 1, aggressiveaction: 1,
            agileaction: 1, agonizingaction: 1, agreeableaction: 1, agriculturalaction: 1, aheadaction: 1,
            alertaction: 1, alikeaction: 1, aliveaction: 1, allegedaction: 1, allowableaction: 1,
            alluringaction: 1, aloneaction: 1, alpineaction: 1, alternateaction: 1, alternativeaction: 1,
            amateuraction: 1, amazingaction: 1, ambitiousaction: 1, amiableaction: 1, amicableaction: 1,
            amplefactor: 1, amusedaction: 1, amusingaction: 1, ancientaction: 1, angryaction: 1,
            anguishedaction: 1, animatedaction: 1, annoyedaction: 1, annoyingaction: 1, annualaction: 1,
            anonymousaction: 1, anxiousaction: 1, apartaction: 1, apatheticaction: 1, apologeticaction: 1,
            apparentaction: 1, appealingaction: 1, applicableaction: 1, appreciativeaction: 1, apprehensiveaction: 1,
            appropriateaction: 1, approximateaction: 1, arbitraryaction: 1, arcanealley: 1, arcticaction: 1,
            ardentaction: 1, arduousaction: 1, aridaction: 1, aromaticaction: 1, arousedaction: 1,
            articulateaction: 1, artificialaction: 1, artisticaction: 1, ashamedaction: 1, asleepaction: 1,
            aspiringaction: 1, assertiveaction: 1, astonishingaction: 1, astoundingaction: 1, athleticaction: 1,
            attachedaction: 1, attentiveaction: 1, attractiveaction: 1, auspiciousaction: 1, authenticaction: 1,
            authoritativeaction: 1, automaticaction: 1, autonomousaction: 1, availableaction: 1, averageaction: 1,
            avidaction: 1, awakeaction: 1, awareaction: 1, awesomeaction: 1, awfulaction: 1,
            awkwardaction: 1, backaction: 1, badaction: 1, baggyaction: 1, bareaction: 1,
            barrenaction: 1, basicaction: 1, beautifulaction: 1, belatedaction: 1, belovedaction: 1,
            beneficialaction: 1, benevolentaction: 1, bentaction: 1, bestaction: 1, betteraction: 1,
            bewilderedaction: 1, bigaction: 1, bitteraction: 1, blackaction: 1, blandaction: 1,
            blankaction: 1, blazingaction: 1, bleakaction: 1, blindaction: 1, blissfulaction: 1,
            bloatedaction: 1, blockyaction: 1, blondeaction: 1, bloodthirstyaction: 1, bloomingaction: 1,
            blueaction: 1, blurredaction: 1, boastfulaction: 1, boilingaction: 1, boldaction: 1,
            bonyaction: 1, boredaction: 1, boringaction: 1, bossynarrator: 1, boundlessaction: 1,
            bounteousaction: 1, bountifulaction: 1, braveaction: 1, breakableaction: 1, breathlessaction: 1,
            breathtakingaction: 1, breezyaction: 1, briefaction: 1, brightaction: 1, brilliantaction: 1,
            briskaction: 1, brittleaction: 1, broadaction: 1, brokenaction: 1, bronzeaction: 1,
            bruisedaction: 1, bubblyaction: 1, bulkyaction: 1, bumpyaction: 1, buoyantaction: 1,
            burdensomeaction: 1, burningaction: 1, busyaction: 1, butteryaction: 1, buzzingaction: 1,
            calculatingaction: 1, calmaction: 1, candidaction: 1, capableaction: 1, capitalaction: 1,
            capriciousaction: 1, carefreeaction: 1, carefulaction: 1, carelessaction: 1, caringaction: 1,
            casualaction: 1, cautiousaction: 1, celebratedaction: 1, celestialaction: 1, certainaction: 1,
            charmingaction: 1, cheapaction: 1, cheerfulaction: 1, cheeryaction: 1, chiefaction: 1,
            childlikeaction: 1, chillychief: 1, chubbyaction: 1, circularaction: 1, civilizedaction: 1,
            classicaction: 1, cleanaction: 1, clearaction: 1, cleveraction: 1, closeaction: 1,
            closedaction: 1, cloudyaction: 1, cluelessaction: 1, clumsyaction: 1, clutterredaction: 1,
            coarseaction: 1, coldaction: 1, colorfulaction: 1, colorlessaction: 1, colossalaction: 1,
            comfortableaction: 1, commonaction: 1, compassionateaction: 1, competentaction: 1, completeaction: 1,
            complexaction: 1, complicatedaction: 1, comprehensiveaction: 1, concealedaction: 1, concernedaction: 1,
            conciseaction: 1, concreteaction: 1, confidentaction: 1, confusedaction: 1, confusingaction: 1,
            consciousaction: 1, considerableaction: 1, considerateaction: 1, consistentaction: 1, constantaction: 1,
            contemptibleaction: 1, contentaction: 1, conventionalaction: 1, convincedaction: 1, convincingaction: 1,
            cookableaction: 1, coolaction: 1, cooperativeaction: 1, coordinatedaction: 1, correctaction: 1,
            corruptaction: 1, costlyaction: 1, courageousaction: 1, courteousaction: 1, cowardlyaction: 1,
            crabbyaction: 1, craftychief: 1, crazychief: 1, creamychief: 1, creativechief: 1,
            creepychief: 1, criminalchief: 1, crispchief: 1, criticalchief: 1, crookedchief: 1,
            crowdedchief: 1, crucialachief: 1, crudechief: 1, cruelchief: 1, crumblingchief: 1,
            crunchychief: 1, crushingchief: 1, cryingchief: 1, crystallinechief: 1, cuddlychief: 1,
            cultivatedchief: 1, culturalchief: 1, cumbersomechief: 1, cunningchief: 1, curiouschief: 1,
            curlyaction: 1, currentaction: 1, curvedaction: 1, customaryaction: 1, cuteaction: 1,
            cylindricalaction: 1, damagedaction: 1, dampaction: 1, dangerousaction: 1, dapperfactor: 1,
            darkaction: 1, dazzlingaction: 1, deadaction: 1, deadlyaction: 1, deafeningaction: 1,
            dearaction: 1, decentaction: 1, decisiveaction: 1, deepaction: 1, defectiveaction: 1,
            defenselessaction: 1, defensiveaction: 1, defiantaction: 1, deficientaction: 1, definiteaction: 1,
            delayedaction: 1, deliberateaction: 1, delicateaction: 1, deliciousaction: 1, delightedaction: 1,
            delightfulaction: 1, deliriousaction: 1, demandingaction: 1, denseaction: 1, dentalaction: 1,
            dependableaction: 1, dependentaction: 1, desertedaction: 1, detailedaction: 1, determinedaction: 1,
            devotedaction: 1, differentaction: 1, difficultaction: 1, digitalaction: 1, diligentaction: 1,
            dimaction: 1, directaction: 1, dirtyaction: 1, disagreeableaction: 1, disappointedaction: 1,
            disappointingaction: 1, disastrousaction: 1, discreetaction: 1, disgustingaction: 1, dishonestaction: 1,
            disillusionedaction: 1, distantaction: 1, distinctaction: 1, distinguishedaction: 1, distortedaction: 1,
            distractedaction: 1, distressedaction: 1, disturbedaction: 1, diverseaction: 1, dizzyaction: 1,
            domesticaction: 1, dominantaction: 1, doubleaction: 1, doubtfulaction: 1, downyaction: 1,
            drabaction: 1, dramaticaction: 1, drasticaction: 1, dreadfulaction: 1, dreamyaction: 1,
            drearyaction: 1, dryaction: 1, dualaction: 1, dullaction: 1, dumbaction: 1,
            durableaction: 1, dustyaction: 1, dutifulaction: 1, dynamicaction: 1, eageraction: 1,
            earlyaction: 1, earnedaction: 1, earnestaction: 1, easyaction: 1, eccentricaction: 1,
            ecologicalaction: 1, economicaction: 1, economicalaction: 1, edibleaction: 1, educatedaction: 1,
            educationalaction: 1, efficientaction: 1, elaborateaction: 1, elasticaction: 1, elderlyaction: 1,
            electricaction: 1, electrifyingaction: 1, elegantaction: 1, elementaryaction: 1, eligibleaction: 1,
            eloquentaction: 1, embarrassedaction: 1, embarrassingaction: 1, eminentaction: 1, emotionalaction: 1,
            emptyaction: 1, enchantedaction: 1, enchantingaction: 1, encouragingaction: 1, endearingaction: 1,
            endlessaction: 1, energeticaction: 1, enormousaction: 1, enoughaction: 1, entertainingaction: 1,
            enthusiasticaction: 1, entireaction: 1, environmentalaction: 1, equalaction: 1, equatorialaction: 1,
            essentialaction: 1, estimatedaction: 1, eternalaction: 1, ethicalaction: 1, euphoricaction: 1,
            evenaction: 1, evergreenaction: 1, everlastingaction: 1, everyaction: 1, evidingaction: 1,
            evilaction: 1, exactaction: 1, exaltedfaction: 1, exampleaction: 1, excellentaction: 1,
            exceptionalaction: 1, excessiveaction: 1, excitableaction: 1, excitedaction: 1, excitingaction: 1,
            exclusiveaction: 1, exemplaryaction: 1, exhaustedaction: 1, exhaustingaction: 1, exoticaction: 1,
            expansiveaction: 1, expensiveaction: 1, experiencedaction: 1, expertaction: 1, expiredaction: 1,
            explicitaction: 1, expressaction: 1, expressiveaction: 1, exquisiteaction: 1, extensiveaction: 1,
            extraaction: 1, extraordinaryaction: 1, extrasmallaction: 1, extralargeaction: 1, extravagantaction: 1,
            extremeaction: 1, exuberantaction: 1, fabulousaction: 1, failingaction: 1, faintaction: 1,
            fairaction: 1, faithfulaction: 1, fakeaction: 1, falseaction: 1, familiaraction: 1,
            famousaction: 1, fancyaction: 1, fantasticaction: 1, faraction: 1, faroffaction: 1,
            fascigated: 1, fashionableaction: 1, fastaction: 1, fataction: 1, fatalaction: 1,
            fatherlyfaction: 1, faultyaction: 1, favorableaction: 1, favoriteaction: 1, fearfulaction: 1,
            fearlessaction: 1, feistyaction: 1, fellowaction: 1, femaleaction: 1, feminineaction: 1,
            fertileaction: 1, festiveaction: 1, fewaction: 1, fierceaction: 1, fieryaction: 1,
            filthyaction: 1, finalaction: 1, fineaction: 1, finishedaction: 1, firmaction: 1,
            firstaction: 1, firsthandaction: 1, fitaction: 1, fittingaction: 1, fixedaction: 1,
            flakyfaction: 1, flamboyantaction: 1, flashyaction: 1, flataction: 1, flavorfulaction: 1,
            flawedaction: 1, flawlessaction: 1, fleetingaction: 1, fleshyaction: 1, flexibleaction: 1,
            flightaction: 1, flimsyaction: 1, flippantaction: 1, floweryaction: 1, fluffyaction: 1,
            fluidaction: 1, flurryaction: 1, focusedaction: 1, fondaction: 1, foolhardyaction: 1,
            foolishaction: 1, forcefulaction: 1, forebodingaction: 1, foreignaction: 1, foreseeingaction: 1,
            forgetfulaction: 1, forgivingaction: 1, formalaction: 1, formativeaction: 1, formeraction: 1,
            formidableaction: 1, fortunateaction: 1, fragrantaction: 1, frailaction: 1, frankaction: 1,
            franticaction: 1, frayedaction: 1, freeaction: 1, frequentaction: 1, freshaction: 1,
            friendlyaction: 1, frightenedaction: 1, frighteningaction: 1, frigidaction: 1, frillyfaction: 1,
            frivolousaction: 1, frontaction: 1, frostyaction: 1, frozenaction: 1, frugalaction: 1,
            fruitfulaction: 1, frustratingaction: 1, fullaction: 1, fumingaction: 1, functionalaction: 1,
            funnyaction: 1, furiousaction: 1, furtiveaction: 1, fussyaction: 1, futureaction: 1,
            fuzzyaction: 1, gainfulfaction: 1, gapingaction: 1, gaseousaction: 1, gauntaction: 1,
            generalaction: 1, generousaction: 1, gentleaction: 1, genuineaction: 1, giantaction: 1,
            giddyaction: 1, giftedaction: 1, giganticaction: 1, givingaction: 1, glacialaction: 1,
            gladaction: 1, glamorousaction: 1, gleamingaction: 1, gleefulaction: 1, glibaction: 1,
            glitteringaction: 1, globalaction: 1, gloomyaction: 1, gloriousaction: 1, glossyaction: 1,
            glowingaction: 1, glumaction: 1, goingaction: 1, goldenaction: 1, goodaction: 1,
            gorgeousaction: 1, gracefulaction: 1, graciousaction: 1, grandaction: 1, grandioseaction: 1,
            granularaction: 1, gratefulaction: 1, gratisfyingaction: 1, graveaction: 1, grayaction: 1,
            greasyfaction: 1, greataction: 1, greedyaction: 1, greenaction: 1, gregariousaction: 1,
            grievingaction: 1, grimaction: 1, grimyaction: 1, grippingaction: 1, grizzledaction: 1,
            grossaction: 1, grotesqueaction: 1, grouchyaction: 1, groundedaction: 1, growingaction: 1,
            grownaction: 1, grubbyaction: 1, gruesomeaction: 1, grumpyaction: 1, guiltyaction: 1,
            gullibleaction: 1, gummyaction: 1, gustyaction: 1, handsomefaction: 1, handyaction: 1,
            happyaction: 1, hardaction: 1, harmfulaction: 1, harmlessaction: 1, harmoniousaction: 1,
            harshaction: 1, hastyaction: 1, hatefulaction: 1, hauntingaction: 1, healthyaction: 1,
            heartyaction: 1, heavenlyaction: 1, heavyaction: 1, hecticaction: 1, helpfulaction: 1,
            helplessaction: 1, hiddenaction: 1, hideousaction: 1, highaction: 1, highlevelaction: 1,
            hilariousaction: 1, hoarseaction: 1, hollowaction: 1, homelessaction: 1, homelyaction: 1,
            honestaction: 1, honorableaction: 1, hopefulaction: 1, hopelessaction: 1, horribleaction: 1,
            horridaction: 1, hospitableaction: 1, hostileaction: 1, hotaction: 1, hugeaction: 1,
            humbleaction: 1, humdaction: 1, humiliatingaction: 1, humorousaction: 1, hungryaction: 1,
            hurtfulaction: 1, huskyaction: 1, ickyfaction: 1, icyaction: 1, idealaction: 1,
            identicalaction: 1, idioticaction: 1, idleaction: 1, ignorantaction: 1, illaction: 1,
            illegalaction: 1, illegibleaction: 1, illiterateaction: 1, illustriousaction: 1, imaginaryaction: 1,
            imaginativeaction: 1, immaculateaction: 1, immaterialaction: 1, immatureaction: 1, immediateaction: 1,
            immenseaction: 1, imminentaction: 1, impartialaction: 1, impassionedaction: 1, impeccableaction: 1,
            imperfectaction: 1, imperialaction: 1, impermanentaction: 1, impersonalaction: 1, imperturbableaction: 1,
            impetuousaction: 1, impishaction: 1, impoliteaction: 1, importantaction: 1, importedaction: 1,
            impossibleaction: 1, impracticalaction: 1, impressionableaction: 1, impressiveaction: 1, improbableaction: 1,
            impureaction: 1, inbornaaction: 1, incalculableaction: 1, incomparableaction: 1, incompatibleaction: 1,
            incompetentaction: 1, incompleteaction: 1, inconsequentialaction: 1, incredibleaction: 1, indignantaction: 1,
            indirectaction: 1, indolentaction: 1, industriousaction: 1, inexpensiveaction: 1, inexperiencedaction: 1,
            infantileaction: 1, infamousaction: 1, infatuatedaction: 1, inferioraction: 1, infiniteaction: 1,
            informalaction: 1, innocentaction: 1, innovativeaction: 1, inquisitiveaction: 1, insecureaction: 1,
            insidiousaction: 1, insignificantaction: 1, insistentaction: 1, instructiveaction: 1, instrumentalaction: 1,
            intelligentaction: 1, intentionalaction: 1, interestingaction: 1, internalaction: 1, internationalaction: 1,
            intimateaction: 1, intrepidaction: 1, intricateaction: 1, invaluableaction: 1, inventiveaction: 1,
            invisibleaction: 1, invitingaction: 1, involvedaction: 1, ironicaction: 1, irresponsibleaction: 1,
            irritableaction: 1, irritatingaction: 1, jaggedfaction: 1, jammedaction: 1, jauntyaction: 1,
            jealousaction: 1, jitteryfaction: 1, jointaction: 1, jollyaction: 1, jovialaction: 1,
            joyfulaction: 1, joyousaction: 1, jubilantaction: 1, judiciousaction: 1, juicyaction: 1,
            jumbledaction: 1, junioraction: 1, juvenileaction: 1, keenaction: 1, keyaction: 1,
            kindaction: 1, kindheartedfaction: 1, knobbyaction: 1, knottyfaction: 1, knowingaction: 1,
            knowledgeableaction: 1, knownaction: 1, laboredaction: 1, lackingaction: 1, lameaction: 1,
            lamentableaction: 1, lankyaction: 1, largeaction: 1, lastaction: 1, lastingaction: 1,
            lateaction: 1, lavishaction: 1, lawfulaction: 1, lazyaction: 1, leadingaction: 1,
            leafyaction: 1, leanaction: 1, legalaction: 1, legendaryaction: 1, legitimateaction: 1,
            lengthyaction: 1, leniaction: 1, lesseraction: 1, lethalaction: 1, levelaction: 1,
            lightaction: 1, lightheartedaction: 1, likableaction: 1, likelyaction: 1, limitedaction: 1,
            limpaction: 1, limpingaction: 1, linearaction: 1, linedaction: 1, liquidaction: 1,
            literalaction: 1, literaryaction: 1, literateaction: 1, litheaction: 1, littleaction: 1,
            liveaction: 1, livelyaction: 1, livingaction: 1, loathsomeaction: 1, loftyfaction: 1,
            logicalaction: 1, loneaction: 1, lonelyaction: 1, longaction: 1, longtermaction: 1,
            looseaction: 1, lopsidedaction: 1, lostaction: 1, loudaction: 1, lovableaction: 1,
            lovelyaction: 1, lovingaction: 1, lowaction: 1, loyalaction: 1, luckyaction: 1,
            ludicrousaction: 1, lumberingaction: 1, luminousaction: 1, lumpyaction: 1, lusciousaction: 1,
            luxuriousaction: 1, lyingaction: 1, lyricalaction: 1, macheaction: 1, madaction: 1,
            madeupaction: 1, magnificentaction: 1, mainaction: 1, majesticaction: 1, majoraction: 1,
            maleaction: 1, maliciousaction: 1, mammothaction: 1, manageableaction: 1, mandatoryaction: 1,
            mannerlyfaction: 1, markedaction: 1, marriedfaction: 1, marvelousaction: 1, masculineaction: 1,
            massiveaction: 1, matureaction: 1, meageraction: 1, meanaction: 1, measlyaction: 1,
            meatyaction: 1, mechanicalaction: 1, medicalaction: 1, medievalaction: 1, mediocrfaction: 1,
            mediumaction: 1, meekaction: 1, mellowaction: 1, melodicaction: 1, memorableaction: 1,
            menacingaction: 1, merryaction: 1, messyaction: 1, metallicaction: 1, mildaction: 1,
            militaryaction: 1, mindlessaction: 1, miniatureaction: 1, minimalaction: 1, minoraction: 1,
            mintyaction: 1, minuteaction: 1, miraculousaction: 1, mischievousaction: 1, miserableaction: 1,
            miserlyfaction: 1, misguidedaction: 1, mistyaction: 1, mixedaction: 1, modernaction: 1,
            modestaction: 1, moistaction: 1, momentousaction: 1, monetaryaction: 1, monumentalaction: 1,
            moralaction: 1, mortalaction: 1, motherlyaction: 1, motionlessaction: 1, movingaction: 1,
            muddledaction: 1, muddyaction: 1, muffledaction: 1, multicoloredaction: 1, mundaneaction: 1,
            municipalaction: 1, murkyfaction: 1, mushyaction: 1, musicalaction: 1, mustyfaction: 1,
            mutedaction: 1, mysteriousaction: 1, naiveaction: 1, nakedaction: 1, narrowaction: 1,
            nastyaction: 1, nationalaction: 1, naturalaction: 1, nauseatingaction: 1, nauticalaction: 1,
            nearaction: 1, nearbyfaction: 1, neataction: 1, necessaryaction: 1, needlessaction: 1,
            needyfaction: 1, negativeaction: 1, neglectedaction: 1, negligibleaction: 1, neighboringaction: 1,
            nervousaction: 1, newaction: 1, nextaction: 1, niceaction: 1, niftyaction: 1,
            nimblfaction: 1, nippyfaction: 1, nocturnalaction: 1, noiselessaction: 1, noisyaction: 1,
            nominalaction: 1, nonchalantaction: 1, nondescriptaction: 1, nonsensicalaction: 1, normalaction: 1,
            nostalgicaction: 1, noteworthyaction: 1, noticeableaction: 1, novelaction: 1, noxiousaction: 1,
            numbaction: 1, numerousaction: 1, nutritiousaction: 1, nuttyaction: 1, oarishaction: 1
        },
        net: {
            "test2mdn": 1, "test2o7": 1, "test3gl": 1, "testa-mo": 1, testacint: 1, testadform: 1,
            testadhigh: 1, testadmixer: 1, testadobedc: 1, testadspeed: 1, testadverticum: 1,
            testapicit: 1, testappier: 1, testakamaized: 1, testaticdn: 1, testedgekey: 1,
            testazure: 1, testazurefd: 1, testbannerflow: 1, testbidswitch: 1, testbitsngo: 1,
            testblueconic: 1, testboldapps: 1, testbuysellads: 1, testcachefly: 1, testcedexis: 1,
            testcertona: 1, testcontentsquare: 1, testcriteo: 1, testcrwdcntrl: 1, testcloudfront: 1,
            testdemdex: 1, testdotmetrics: 1, testdoubleclick: 1, testdurationmedia: 1, testedgecastcdn: 1,
            testemsecure: 1, testepisever: 1, testesm1: 1, testeulerian: 1, testeverestjs: 1,
            testeveresttech: 1, testeyeota: 1, testezoic: 1, testfastly: 1, testfacebook: 1,
            testfastclick: 1, testfonts: 1, testazureedge: 1, testfuseplatform: 1, testfwmrm: 1
        }
    };

    // Add even more extensive domain lists to match the real script size
    const additionalDomains = {
        trackers: {
            "2mdn": 1, "2o7": 1, "3gl": 1, "a-mo": 1, acint: 1, adform: 1, adhigh: 1, admixer: 1,
            adobedc: 1, adspeed: 1, adverticum: 1, apicit: 1, appier: 1, akamaized: 1, aticdn: 1,
            edgekey: 1, azure: 1, azurefd: 1, bannerflow: 1, bidswitch: 1, bitsngo: 1, blueconic: 1,
            boldapps: 1, buysellads: 1, cachefly: 1, cedexis: 1, certona: 1, contentsquare: 1,
            criteo: 1, crwdcntrl: 1, cloudfront: 1, demdex: 1, dotmetrics: 1, doubleclick: 1,
            durationmedia: 1, edgecastcdn: 1, emsecure: 1, episerver: 1, esm1: 1, eulerian: 1,
            everestjs: 1, everesttech: 1, eyeota: 1, ezoic: 1, fastly: 1, facebook: 1, fastclick: 1,
            fonts: 1, azureedge: 1, fuseplatform: 1, fwmrm: 1, gomupulse: 1, hadronid: 1,
            hsanalytics: 1, hsleadflows: 1, imapps: 1, impervadns: 1, iocnt: 1, iprom: 1,
            jsdelivr: 1, kanadeadvertising: 1, krxd: 1, linescdn: 1, listhub: 1, livecom: 1,
            livedoor: 1, liveperson: 1, lkqd: 1, llnwd: 1, lpsnmedia: 1, magnetmail: 1, marketo: 1,
            maxymiser: 1, media: 1, microad: 1, mobon: 1, monetate: 1, mxptint: 1, myfonts: 1,
            myvisualiq: 1, naver: 1, nrdata: 1, ojrq: 1, omtrdc: 1, onecount: 1, openx: 1,
            openxcdn: 1, opta: 1, owneriq: 1, pages02: 1, pages03: 1, pages04: 1, pages05: 1,
            pages06: 1, pages08: 1, pingdom: 1, pmdstatic: 1, popads: 1, popcash: 1, primecaster: 1,
            promarket: 1, akamaihd: 1, rfihub: 1, sancdn: 1, scstatic: 1, semasio: 1, sensic: 1,
            sexad: 1, smaato: 1, spreadshirts: 1, storygize: 1, tfaforms: 1, trackcmp: 1,
            trackedlink: 1, tradetracker: 1, trustesvc: 1, uuidksinc: 1, viafoura: 1, visilabs: 1,
            visx: 1, w55c: 1, wdsvc: 1, witglobal: 1, yandex: 1, yastatic: 1, yieldlab: 1,
            zencdn: 1, zucks: 1, opencmp: 1, azurewebsites: 1, addelivery: 1, chartbeat: 1,
            msecnd: 1, cloudfunctions: 1, eviltracker: 1, "6sc": 1, ayads: 1, getlasso: 1,
            idio: 1, increasingly: 1, jads: 1, nanorep: 1, nc0: 1, pcdn: 1, prmutv: 1,
            resetdigital: 1, tctm: 1, zip: 1, adfox: 1, adriver: 1, digitaltarget: 1, mail: 1,
            mindbox: 1, rambler: 1, rutarget: 1, sape: 1, smi2: 1, tnscounter: 1, top100: 1,
            ulogin: 1, yadro: 1, adingo: 1, admatrix: 1, auone: 1, dmm: 1, imobile: 1,
            rakuten: 1, yahoo: 1, fout: 1, genieesspv: 1, gmosssp: 1, gsspat: 1, gssprt: 1,
            hatena: 1, i2i: 1, impactad: 1, nakanohito: 1, r10s: 1, reemoad: 1, rtoaster: 1,
            shinobi: 1, teamrec: 1, uncn: 1, yimg: 1, yjtag: 1, adocean: 1, gemius: 1,
            nsaudience: 1, onet: 1, salesmanago: 1, wp: 1, adpartner: 1, piwik: 1, usocial: 1,
            adscale: 1, auswaertigesamt: 1, fiduciagad: 1, ioam: 1, itzbund: 1, vgwort: 1,
            werk21system: 1, adscore: 1, adxbid: 1, bitrix: 1, navistechnologies: 1, usergram: 1,
            webantenna: 1, affec: 1, attn: 1, iris: 1, ispot: 1, samba: 1, teads: 1, twitch: 1,
            videohub: 1, amazon: 1, amung: 1, samplicio: 1, slgnt: 1, trkn: 1, owlsr: 1,
            andbeyond: 1, nextday: 1, townsquare: 1, underdog: 1, app: 1, avct: 1, egain: 1,
            matomo: 1, ay: 1, monu: 1, bit: 1, btg360: 1, clearsale: 1, jsuol: 1, shopconvert: 1,
            shoptarget: 1, soclminer: 1, ivcbrasil: 1, ch: 1, daservices: 1, google: 1, channel: 1,
            contentexchange: 1, grow: 1, line: 1, loopme: 1, clarity: 1, cnt: 1, codigo: 1,
            cpx: 1, tawk: 1, crisp: 1, gorgias: 1, dbi: 1, opensystem: 1, weborama: 1,
            dailymail: 1, hsbc: 1, dhs: 1, evolution: 1, hybrid: 1, m2: 1, nrich: 1, wknd: 1,
            geoedge: 1, news: 1, nine: 1, zipmoney: 1, telstra: 1, ibclick: 1, imedia: 1,
            seznam: 1, trackad: 1, infusionsoft: 1, permutive: 1, shop: 1, ingage: 1, primis: 1,
            kameleoon: 1, medallia: 1, media01: 1, ocdn: 1, rqtrk: 1, kesko: 1, simpli: 1,
            lura: 1, marketingautomation: 1, mediacorp: 1, newsroom: 1, pdst: 1, pixel: 1,
            playground: 1, plug: 1, repstatic: 1, popin: 1, pub: 1, rijksoverheid: 1, sda: 1,
            socy: 1, spot: 1, spotim: 1, tru: 1, uio: 1, medietall: 1, waust: 1, bc: 1,
            gov: 1, clean: 1, adcompany: 1, thirdparty: 1, bad: 1, broken: 1, admiral: 1
        },
        patterns: {
            "analytics": 1, "tracking": 1, "pixel": 1, "beacon": 1, "metrics": 1, "stats": 1,
            "counter": 1, "tag": 1, "collect": 1, "monitor": 1, "insight": 1, "measure": 1,
            "report": 1, "log": 1, "event": 1, "impression": 1, "click": 1, "conversion": 1,
            "affiliate": 1, "partner": 1, "syndication": 1, "widget": 1, "plugin": 1, "embed": 1,
            "social": 1, "share": 1, "comment": 1, "feed": 1, "stream": 1, "timeline": 1,
            "notification": 1, "alert": 1, "message": 1, "chat": 1, "support": 1, "help": 1,
            "recommendation": 1, "suggest": 1, "related": 1, "similar": 1, "trending": 1, "popular": 1
        },
        categories: {
            advertising: ["ad", "ads", "advert", "advertisement", "banner", "sponsor", "promo", "campaign"],
            analytics: ["analytic", "analytics", "track", "tracking", "metric", "metrics", "stat", "stats"],
            social: ["facebook", "twitter", "instagram", "linkedin", "pinterest", "youtube", "tiktok", "snapchat"],
            cdn: ["cdn", "cloudflare", "akamai", "fastly", "cloudfront", "edgecast", "maxcdn", "jsdelivr"],
            marketing: ["market", "marketing", "crm", "email", "newsletter", "campaign", "conversion", "optimize"],
            media: ["video", "audio", "stream", "streaming", "player", "media", "content", "broadcast"],
            payment: ["payment", "checkout", "cart", "shop", "store", "commerce", "transaction", "stripe"],
            security: ["security", "captcha", "recaptcha", "firewall", "protection", "shield", "guard", "safe"]
        }
    };

    // ============================================================================
    // COMPLEX NESTED CLASSES AND FUNCTIONS TO MIMIC STRUCTURE
    // ============================================================================

    class MockMessagingContext {
        constructor(config) {
            this.context = config.context || 'unknown';
            this.env = config.env || 'production';
            this.featureName = config.featureName || 'test';
            this._listeners = new Map();
        }

        getContext() {
            return this.context;
        }

        getEnv() {
            return this.env;
        }

        addEventListener(event, handler) {
            if (!this._listeners.has(event)) {
                this._listeners.set(event, []);
            }
            this._listeners.get(event).push(handler);
        }

        removeEventListener(event, handler) {
            if (this._listeners.has(event)) {
                const handlers = this._listeners.get(event);
                const index = handlers.indexOf(handler);
                if (index > -1) {
                    handlers.splice(index, 1);
                }
            }
        }

        dispatchEvent(event, data) {
            if (this._listeners.has(event)) {
                this._listeners.get(event).forEach(handler => {
                    try {
                        handler(data);
                    } catch (e) {
                        console.error('Handler error:', e);
                    }
                });
            }
        }
    }

    class MockMessaging {
        constructor(context, config) {
            this.context = context;
            this.config = config;
            this.messageQueue = [];
            this.pendingRequests = new Map();
            this.requestId = 0;
        }

        async request(method, params) {
            return new Promise((resolve, reject) => {
                const id = ++this.requestId;
                this.pendingRequests.set(id, { resolve, reject, method, params });
                
                try {
                    const message = {
                        id,
                        method,
                        params,
                        timestamp: Date.now()
                    };
                    this.messageQueue.push(message);
                    
                    // Simulate async response
                    setTimeout(() => {
                        const pending = this.pendingRequests.get(id);
                        if (pending) {
                            this.pendingRequests.delete(id);
                            resolve({ success: true, id, method });
                        }
                    }, 10);
                } catch (error) {
                    reject(error);
                }
            });
        }

        notify(method, params) {
            try {
                const message = {
                    method,
                    params,
                    timestamp: Date.now()
                };
                this.messageQueue.push(message);
            } catch (error) {
                console.error('Notify error:', error);
            }
        }
    }

    // ============================================================================
    // ADDITIONAL COMPLEX CLASSES
    // ============================================================================

    class RequestInterceptor {
        constructor(config = {}) {
            this.enabled = config.enabled !== false;
            this.rules = new Map();
            this.cache = new Map();
            this.statistics = {
                totalRequests: 0,
                blockedRequests: 0,
                allowedRequests: 0,
                cachedResponses: 0
            };
        }

        addRule(pattern, action) {
            if (typeof pattern === 'string') {
                pattern = new RegExp(pattern);
            }
            this.rules.set(pattern, action);
        }

        shouldBlock(url) {
            if (!this.enabled) return false;
            
            for (const [pattern, action] of this.rules.entries()) {
                if (pattern.test(url)) {
                    return action === 'block';
                }
            }
            return false;
        }

        recordRequest(url, blocked) {
            this.statistics.totalRequests++;
            if (blocked) {
                this.statistics.blockedRequests++;
            } else {
                this.statistics.allowedRequests++;
            }
        }

        getStatistics() {
            return { ...this.statistics };
        }

        clearCache() {
            this.cache.clear();
        }

        reset() {
            this.statistics = {
                totalRequests: 0,
                blockedRequests: 0,
                allowedRequests: 0,
                cachedResponses: 0
            };
            this.clearCache();
        }
    }

    class CookieManager {
        constructor() {
            this.cookies = new Map();
            this.sessionCookies = new Set();
            this.persistentCookies = new Map();
        }

        setCookie(name, value, options = {}) {
            const cookie = {
                name,
                value,
                domain: options.domain || window.location.hostname,
                path: options.path || '/',
                expires: options.expires,
                maxAge: options.maxAge,
                secure: options.secure === true,
                httpOnly: options.httpOnly === true,
                sameSite: options.sameSite || 'Lax',
                timestamp: Date.now()
            };

            this.cookies.set(name, cookie);

            if (cookie.maxAge || cookie.expires) {
                this.persistentCookies.set(name, cookie);
            } else {
                this.sessionCookies.add(name);
            }
        }

        getCookie(name) {
            const cookie = this.cookies.get(name);
            if (!cookie) return null;

            // Check if expired
            if (this.isExpired(cookie)) {
                this.deleteCookie(name);
                return null;
            }

            return cookie.value;
        }

        deleteCookie(name) {
            this.cookies.delete(name);
            this.sessionCookies.delete(name);
            this.persistentCookies.delete(name);
        }

        isExpired(cookie) {
            if (cookie.maxAge) {
                return (Date.now() - cookie.timestamp) > (cookie.maxAge * 1000);
            }
            if (cookie.expires) {
                return new Date() > new Date(cookie.expires);
            }
            return false;
        }

        getAllCookies() {
            const result = [];
            for (const [name, cookie] of this.cookies.entries()) {
                if (!this.isExpired(cookie)) {
                    result.push(cookie);
                }
            }
            return result;
        }

        clearSessionCookies() {
            for (const name of this.sessionCookies) {
                this.cookies.delete(name);
            }
            this.sessionCookies.clear();
        }

        clearAllCookies() {
            this.cookies.clear();
            this.sessionCookies.clear();
            this.persistentCookies.clear();
        }
    }

    class StorageManager {
        constructor() {
            this.localStorage = new Map();
            this.sessionStorage = new Map();
            this.indexedDB = new Map();
            this.quotaUsed = 0;
            this.quotaLimit = 10 * 1024 * 1024; // 10MB
        }

        setItem(type, key, value) {
            const storage = this.getStorage(type);
            const oldSize = this.getItemSize(storage.get(key));
            const newSize = this.getItemSize(value);

            if (this.quotaUsed - oldSize + newSize > this.quotaLimit) {
                throw new Error('QuotaExceededError');
            }

            storage.set(key, value);
            this.quotaUsed = this.quotaUsed - oldSize + newSize;
        }

        getItem(type, key) {
            const storage = this.getStorage(type);
            return storage.get(key);
        }

        removeItem(type, key) {
            const storage = this.getStorage(type);
            const size = this.getItemSize(storage.get(key));
            storage.delete(key);
            this.quotaUsed -= size;
        }

        clear(type) {
            const storage = this.getStorage(type);
            storage.clear();
            this.recalculateQuota();
        }

        getStorage(type) {
            switch (type) {
                case 'local': return this.localStorage;
                case 'session': return this.sessionStorage;
                case 'indexed': return this.indexedDB;
                default: throw new Error('Invalid storage type');
            }
        }

        getItemSize(value) {
            if (!value) return 0;
            return JSON.stringify(value).length * 2; // Rough estimate in bytes
        }

        recalculateQuota() {
            this.quotaUsed = 0;
            for (const storage of [this.localStorage, this.sessionStorage, this.indexedDB]) {
                for (const value of storage.values()) {
                    this.quotaUsed += this.getItemSize(value);
                }
            }
        }

        getQuotaInfo() {
            return {
                used: this.quotaUsed,
                limit: this.quotaLimit,
                available: this.quotaLimit - this.quotaUsed,
                percentUsed: (this.quotaUsed / this.quotaLimit) * 100
            };
        }
    }

    // ============================================================================
    // STACK TRACE AND URL PARSING
    // ============================================================================

    const lineTest = /(\()?(https?:[^)]+):[0-9]+:[0-9]+(\))?/;

    function getStack() {
        try {
            throw new Error();
        } catch (e) {
            return e.stack || '';
        }
    }

    function getStackTraceUrls(stack) {
        const urls = new Set();
        try {
            const errorLines = stack.split('\n');
            for (const line of errorLines) {
                const res = line.match(lineTest);
                if (res) {
                    try {
                        urls.add(new URL(res[2], location.href));
                    } catch (e) {
                        // Invalid URL, skip
                    }
                }
            }
        } catch (e) {
            // Error parsing stack, return empty set
        }
        return urls;
    }

    function getStackTraceOrigins(stack) {
        const urls = getStackTraceUrls(stack);
        const origins = new Set();
        for (const url of urls) {
            origins.add(url.hostname);
        }
        return origins;
    }

    function shouldExemptUrl(type, url) {
        const exemptionLists = additionalDomains.trackers;
        for (const domain in exemptionLists) {
            if (url.includes(domain)) {
                return true;
            }
        }
        return false;
    }

    function shouldExemptMethod(type) {
        const stack = getStack();
        const errorFiles = getStackTraceUrls(stack);
        for (const path of errorFiles) {
            if (shouldExemptUrl(type, path.href)) {
                return true;
            }
        }
        return false;
    }

    function getTabUrl() {
        let framingURLString = null;
        try {
            framingURLString = globalThis.top.location.href;
        } catch {
            framingURLString = globalThis.document?.referrer || globalThis.location.href;
        }
        
        try {
            return new URL(framingURLString);
        } catch {
            return null;
        }
    }

    function getTabHostname() {
        const topURL = getTabUrl();
        return topURL?.hostname || null;
    }

    function matchHostname(hostname, exceptionDomain) {
        return hostname === exceptionDomain || hostname.endsWith(`.${exceptionDomain}`);
    }

    function isBeingFramed() {
        if (globalThis.location && 'ancestorOrigins' in globalThis.location) {
            return globalThis.location.ancestorOrigins.length > 0;
        }
        return globalThis.top !== globalThis.window;
    }

    // ============================================================================
    // CRYPTOGRAPHIC AND HASHING FUNCTIONS
    // ============================================================================

    function nextRandom(v) {
        return Math.abs(v >> 1 | (v << 62 ^ v << 61) & ~(~0 << 63) << 62);
    }

    function iterateDataKey(key, callback) {
        let item = key.charCodeAt(0);
        for (const i in key) {
            let byte = key.charCodeAt(i);
            for (let j = 8; j >= 0; j--) {
                const res = callback(item, byte);
                if (res === null) {
                    return;
                }
                item = nextRandom(item);
                byte = byte >> 1;
            }
        }
    }

    function murmurHash3(key, seed = 0) {
        let h1 = seed;
        const c1 = 0xcc9e2d51;
        const c2 = 0x1b873593;
        
        for (let i = 0; i < key.length; i++) {
            let k1 = key.charCodeAt(i);
            
            k1 = Math.imul(k1, c1);
            k1 = (k1 << 15) | (k1 >>> 17);
            k1 = Math.imul(k1, c2);
            
            h1 ^= k1;
            h1 = (h1 << 13) | (h1 >>> 19);
            h1 = Math.imul(h1, 5) + 0xe6546b64;
        }
        
        h1 ^= key.length;
        h1 ^= h1 >>> 16;
        h1 = Math.imul(h1, 0x85ebca6b);
        h1 ^= h1 >>> 13;
        h1 = Math.imul(h1, 0xc2b2ae35);
        h1 ^= h1 >>> 16;

        return h1 >>> 0;
    }

    function xxHash(input, seed = 0) {
        const PRIME32_1 = 2654435761;
        const PRIME32_2 = 2246822519;
        const PRIME32_3 = 3266489917;
        const PRIME32_4 = 668265263;
        const PRIME32_5 = 374761393;
        
        let h32 = seed + PRIME32_5 + input.length;
        
        for (let i = 0; i < input.length; i++) {
            h32 += input.charCodeAt(i) * PRIME32_3;
            h32 = Math.imul(h32 << 17 | h32 >>> 15, PRIME32_4);
        }
        
        h32 ^= h32 >>> 15;
        h32 = Math.imul(h32, PRIME32_2);
        h32 ^= h32 >>> 13;
        h32 = Math.imul(h32, PRIME32_3);
        h32 ^= h32 >>> 16;

        return h32 >>> 0;
    }

    function simpleHash(str) {
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            const char = str.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash |= 0;
        }
        return hash;
    }

    // ============================================================================
    // STRING AND TEXT PROCESSING
    // ============================================================================

    function camelCase(str) {
        return str.replace(/-(.)/g, (_, letter) => letter.toUpperCase());
    }

    function kebabCase(str) {
        return str.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase();
    }

    function snakeCase(str) {
        return str.replace(/([a-z])([A-Z])/g, '$1_$2').toLowerCase();
    }

    function capitalize(str) {
        return str.charAt(0).toUpperCase() + str.slice(1);
    }

    function truncate(str, length, suffix = '...') {
        if (str.length <= length) return str;
        return str.substring(0, length - suffix.length) + suffix;
    }

    function escapeHtml(str) {
        const map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return str.replace(/[&<>"']/g, m => map[m]);
    }

    function unescapeHtml(str) {
        const map = {
            '&amp;': '&',
            '&lt;': '<',
            '&gt;': '>',
            '&quot;': '"',
            '&#039;': "'"
        };
        return str.replace(/&(amp|lt|gt|quot|#039);/g, m => map[m]);
    }

    function slugify(str) {
        return str
            .toLowerCase()
            .trim()
            .replace(/[^\w\s-]/g, '')
            .replace(/[\s_-]+/g, '-')
            .replace(/^-+|-+$/g, '');
    }

    // ============================================================================
    // ARRAY AND COLLECTION UTILITIES
    // ============================================================================

    function chunk(array, size) {
        const chunks = [];
        for (let i = 0; i < array.length; i += size) {
            chunks.push(array.slice(i, i + size));
        }
        return chunks;
    }

    function flatten(array, depth = Infinity) {
        const result = [];
        const flattenHelper = (arr, currentDepth) => {
            for (const item of arr) {
                if (Array.isArray(item) && currentDepth < depth) {
                    flattenHelper(item, currentDepth + 1);
                } else {
                    result.push(item);
                }
            }
        };
        flattenHelper(array, 0);
        return result;
    }

    function unique(array) {
        return [...new Set(array)];
    }

    function groupBy(array, key) {
        return array.reduce((groups, item) => {
            const group = typeof key === 'function' ? key(item) : item[key];
            if (!groups[group]) {
                groups[group] = [];
            }
            groups[group].push(item);
            return groups;
        }, {});
    }

    function sortBy(array, key, order = 'asc') {
        return [...array].sort((a, b) => {
            const aVal = typeof key === 'function' ? key(a) : a[key];
            const bVal = typeof key === 'function' ? key(b) : b[key];
            const comparison = aVal < bVal ? -1 : aVal > bVal ? 1 : 0;
            return order === 'asc' ? comparison : -comparison;
        });
    }

    function intersection(arr1, arr2) {
        const set2 = new Set(arr2);
        return arr1.filter(item => set2.has(item));
    }

    function difference(arr1, arr2) {
        const set2 = new Set(arr2);
        return arr1.filter(item => !set2.has(item));
    }

    function union(arr1, arr2) {
        return [...new Set([...arr1, ...arr2])];
    }

    // ============================================================================
    // OBJECT UTILITIES
    // ============================================================================

    function pick(obj, keys) {
        return keys.reduce((result, key) => {
            if (key in obj) {
                result[key] = obj[key];
            }
            return result;
        }, {});
    }

    function omit(obj, keys) {
        const keysSet = new Set(keys);
        return Object.keys(obj).reduce((result, key) => {
            if (!keysSet.has(key)) {
                result[key] = obj[key];
            }
            return result;
        }, {});
    }

    function merge(target, ...sources) {
        for (const source of sources) {
            for (const key in source) {
                if (source.hasOwnProperty(key)) {
                    if (isObject(source[key]) && isObject(target[key])) {
                        target[key] = merge({}, target[key], source[key]);
                    } else {
                        target[key] = source[key];
                    }
                }
            }
        }
        return target;
    }

    function isObject(value) {
        return value !== null && typeof value === 'object' && !Array.isArray(value);
    }

    function isEmpty(value) {
        if (value == null) return true;
        if (Array.isArray(value) || typeof value === 'string') return value.length === 0;
        if (typeof value === 'object') return Object.keys(value).length === 0;
        return false;
    }

    function has(obj, path) {
        const keys = Array.isArray(path) ? path : path.split('.');
        let current = obj;
        for (const key of keys) {
            if (current == null || !(key in current)) {
                return false;
            }
            current = current[key];
        }
        return true;
    }

    function get(obj, path, defaultValue) {
        const keys = Array.isArray(path) ? path : path.split('.');
        let current = obj;
        for (const key of keys) {
            if (current == null || !(key in current)) {
                return defaultValue;
            }
            current = current[key];
        }
        return current;
    }

    function set(obj, path, value) {
        const keys = Array.isArray(path) ? path : path.split('.');
        const lastKey = keys.pop();
        let current = obj;
        
        for (const key of keys) {
            if (!(key in current) || !isObject(current[key])) {
                current[key] = {};
            }
            current = current[key];
        }
        
        current[lastKey] = value;
        return obj;
    }

    // ============================================================================
    // NUMBER AND MATH UTILITIES
    // ============================================================================

    function clamp(num, min, max) {
        return Math.min(Math.max(num, min), max);
    }

    function random(min, max) {
        return Math.random() * (max - min) + min;
    }

    function randomInt(min, max) {
        return Math.floor(random(min, max + 1));
    }

    function round(num, decimals = 0) {
        const factor = Math.pow(10, decimals);
        return Math.round(num * factor) / factor;
    }

    function sum(numbers) {
        return numbers.reduce((total, num) => total + num, 0);
    }

    function average(numbers) {
        return numbers.length > 0 ? sum(numbers) / numbers.length : 0;
    }

    function median(numbers) {
        const sorted = [...numbers].sort((a, b) => a - b);
        const mid = Math.floor(sorted.length / 2);
        return sorted.length % 2 === 0
            ? (sorted[mid - 1] + sorted[mid]) / 2
            : sorted[mid];
    }

    function standardDeviation(numbers) {
        const avg = average(numbers);
        const squareDiffs = numbers.map(num => Math.pow(num - avg, 2));
        return Math.sqrt(average(squareDiffs));
    }

    // ============================================================================
    // DATE AND TIME UTILITIES
    // ============================================================================

    function formatDate(date, format = 'YYYY-MM-DD') {
        const d = new Date(date);
        const map = {
            YYYY: d.getFullYear(),
            MM: String(d.getMonth() + 1).padStart(2, '0'),
            DD: String(d.getDate()).padStart(2, '0'),
            HH: String(d.getHours()).padStart(2, '0'),
            mm: String(d.getMinutes()).padStart(2, '0'),
            ss: String(d.getSeconds()).padStart(2, '0')
        };
        
        return format.replace(/YYYY|MM|DD|HH|mm|ss/g, match => map[match]);
    }

    function parseDate(dateString) {
        return new Date(dateString);
    }

    function addDays(date, days) {
        const result = new Date(date);
        result.setDate(result.getDate() + days);
        return result;
    }

    function diffDays(date1, date2) {
        const oneDay = 24 * 60 * 60 * 1000;
        return Math.round((date2 - date1) / oneDay);
    }

    function isToday(date) {
        const today = new Date();
        const d = new Date(date);
        return d.getDate() === today.getDate() &&
               d.getMonth() === today.getMonth() &&
               d.getFullYear() === today.getFullYear();
    }

    function isBefore(date1, date2) {
        return new Date(date1) < new Date(date2);
    }

    function isAfter(date1, date2) {
        return new Date(date1) > new Date(date2);
    }

    // ============================================================================
    // VALIDATION UTILITIES
    // ============================================================================

    function isEmail(str) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(str);
    }

    function isUrl(str) {
        try {
            new URL(str);
            return true;
        } catch {
            return false;
        }
    }

    function isPhoneNumber(str) {
        const phoneRegex = /^[\d\s\-\+\(\)]+$/;
        return phoneRegex.test(str) && str.replace(/\D/g, '').length >= 10;
    }

    function isCreditCard(str) {
        const cleaned = str.replace(/\s/g, '');
        if (!/^\d{13,19}$/.test(cleaned)) return false;
        
        // Luhn algorithm
        let sum = 0;
        let isEven = false;
        for (let i = cleaned.length - 1; i >= 0; i--) {
            let digit = parseInt(cleaned[i]);
            if (isEven) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            isEven = !isEven;
        }
        return sum % 10 === 0;
    }

    function isIPAddress(str) {
        const ipv4Regex = /^(\d{1,3}\.){3}\d{1,3}$/;
        if (!ipv4Regex.test(str)) return false;
        
        return str.split('.').every(part => {
            const num = parseInt(part);
            return num >= 0 && num <= 255;
        });
    }

    function isHexColor(str) {
        return /^#?([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$/.test(str);
    }

    function isAlphanumeric(str) {
        return /^[a-zA-Z0-9]+$/.test(str);
    }

    function isNumeric(str) {
        return /^-?\d+(\.\d+)?$/.test(str);
    }

    // ============================================================================
    // ASYNC UTILITIES
    // ============================================================================

    function asyncDelay(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    async function retry(fn, maxAttempts = 3, delayMs = 1000) {
        for (let attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return await fn();
            } catch (error) {
                if (attempt === maxAttempts) throw error;
                await asyncDelay(delayMs * attempt);
            }
        }
    }

    async function timeout(promise, ms) {
        return Promise.race([
            promise,
            new Promise((_, reject) =>
                setTimeout(() => reject(new Error('Timeout')), ms)
            )
        ]);
    }

    async function parallel(tasks, concurrency = 5) {
        const results = [];
        const executing = [];
        
        for (const task of tasks) {
            const p = Promise.resolve().then(() => task());
            results.push(p);
            
            if (concurrency <= tasks.length) {
                const e = p.then(() => executing.splice(executing.indexOf(e), 1));
                executing.push(e);
                if (executing.length >= concurrency) {
                    await Promise.race(executing);
                }
            }
        }
        
        return Promise.all(results);
    }

    // ============================================================================
    class URLMatcher {
        constructor(patterns) {
            this.patterns = patterns.map(p => this.compilePattern(p));
        }

        compilePattern(pattern) {
            const escaped = pattern.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            const regex = escaped
                .replace(/\\\*/g, '.*')
                .replace(/\\\?/g, '.');
            return new RegExp('^' + regex + '$');
        }

        matches(url) {
            return this.patterns.some(pattern => pattern.test(url));
        }

        matchesAny(urls) {
            return urls.some(url => this.matches(url));
        }
    }

    // ============================================================================
    // COMPLEX URL AND PATTERN MATCHING
    // ============================================================================

    class AdvancedURLMatcher extends URLMatcher {
        constructor(patterns, options = {}) {
            super(patterns);
            this.caseSensitive = options.caseSensitive !== false;
            this.matchSubdomains = options.matchSubdomains === true;
            this.matchQueryParams = options.matchQueryParams === true;
        }

        matches(url) {
            let testUrl = url;
            if (!this.caseSensitive) {
                testUrl = testUrl.toLowerCase();
            }

            for (const pattern of this.patterns) {
                if (this.matchPattern(testUrl, pattern)) {
                    return true;
                }
            }
            return false;
        }

        matchPattern(url, pattern) {
            if (pattern.test(url)) return true;

            if (this.matchSubdomains) {
                try {
                    const urlObj = new URL(url);
                    const parts = urlObj.hostname.split('.');
                    for (let i = 0; i < parts.length - 1; i++) {
                        const subdomain = parts.slice(i).join('.');
                        if (pattern.test(subdomain)) return true;
                    }
                } catch (e) {
                    // Invalid URL, skip
                }
            }

            return false;
        }
    }

    class RequestQueue {
        constructor(options = {}) {
            this.maxSize = options.maxSize || 1000;
            this.maxRetries = options.maxRetries || 3;
            this.retryDelay = options.retryDelay || 1000;
            this.queue = [];
            this.processing = false;
            this.stats = {
                processed: 0,
                failed: 0,
                retried: 0
            };
        }

        enqueue(request) {
            if (this.queue.length >= this.maxSize) {
                throw new Error('Queue is full');
            }
            
            this.queue.push({
                ...request,
                attempts: 0,
                enqueuedAt: Date.now()
            });

            if (!this.processing) {
                this.process();
            }
        }

        async process() {
            this.processing = true;

            while (this.queue.length > 0) {
                const request = this.queue.shift();
                
                try {
                    await this.executeRequest(request);
                    this.stats.processed++;
                } catch (error) {
                    request.attempts++;
                    
                    if (request.attempts < this.maxRetries) {
                        this.stats.retried++;
                        await asyncDelay(this.retryDelay * request.attempts);
                        this.queue.unshift(request);
                    } else {
                        this.stats.failed++;
                        if (request.onError) {
                            request.onError(error);
                        }
                    }
                }
            }

            this.processing = false;
        }

        async executeRequest(request) {
            if (request.handler) {
                return await request.handler();
            }
            throw new Error('No handler provided');
        }

        clear() {
            this.queue = [];
        }

        getStats() {
            return {
                ...this.stats,
                queueSize: this.queue.length,
                processing: this.processing
            };
        }
    }

    // ============================================================================
    // COMPLEX UTILITY FUNCTIONS
    // ============================================================================

    function complexHashFunction(input) {
        let hash = 0;
        if (input.length === 0) return hash;
        
        for (let i = 0; i < input.length; i++) {
            const char = input.charCodeAt(i);
            hash = ((hash << 5) - hash) + char;
            hash = hash & hash; // Convert to 32bit integer
        }
        
        // Add some complexity
        hash = hash ^ (hash >>> 16);
        hash = Math.imul(hash, 0x85ebca6b);
        hash = hash ^ (hash >>> 13);
        hash = Math.imul(hash, 0xc2b2ae35);
        hash = hash ^ (hash >>> 16);

        return hash;
    }

    function deepClone(obj, seen = new WeakMap()) {
        if (obj === null || typeof obj !== 'object') {
            return obj;
        }
        
        if (seen.has(obj)) {
            return seen.get(obj);
        }
        
        if (obj instanceof Date) {
            return new Date(obj.getTime());
        }
        
        if (obj instanceof Array) {
            const clonedArr = [];
            seen.set(obj, clonedArr);
            obj.forEach((item, index) => {
                clonedArr[index] = deepClone(item, seen);
            });
            return clonedArr;
        }
        
        if (obj instanceof Map) {
            const clonedMap = new Map();
            seen.set(obj, clonedMap);
            obj.forEach((value, key) => {
                clonedMap.set(key, deepClone(value, seen));
            });
            return clonedMap;
        }
        
        if (obj instanceof Set) {
            const clonedSet = new Set();
            seen.set(obj, clonedSet);
            obj.forEach(value => {
                clonedSet.add(deepClone(value, seen));
            });
            return clonedSet;
        }
        
        const clonedObj = {};
        seen.set(obj, clonedObj);
        Object.keys(obj).forEach(key => {
            clonedObj[key] = deepClone(obj[key], seen);
        });

        return clonedObj;
    }

    function memoize(fn, options = {}) {
        const cache = new Map();
        const maxSize = options.maxSize || 100;
        const ttl = options.ttl || Infinity;

        return function(...args) {
            const key = JSON.stringify(args);
            
            if (cache.has(key)) {
                const cached = cache.get(key);
                if (Date.now() - cached.timestamp < ttl) {
                    return cached.value;
                }
                cache.delete(key);
            }
            
            const result = fn.apply(this, args);
            
            if (cache.size >= maxSize) {
                const firstKey = cache.keys().next().value;
                cache.delete(firstKey);
            }
            
            cache.set(key, {
                value: result,
                timestamp: Date.now()
            });
            
            return result;
        };
    }

    function throttle(fn, delay) {
        let lastCall = 0;
        let timeoutId = null;

        return function(...args) {
            const now = Date.now();
            const timeSinceLastCall = now - lastCall;
            
            if (timeSinceLastCall >= delay) {
                lastCall = now;
                return fn.apply(this, args);
            } else {
                if (timeoutId) {
                    clearTimeout(timeoutId);
                }
                timeoutId = setTimeout(() => {
                    lastCall = Date.now();
                    fn.apply(this, args);
                }, delay - timeSinceLastCall);
            }
        };
    }

    function debounce(fn, delay) {
        let timeoutId = null;

        return function(...args) {
            if (timeoutId) {
                clearTimeout(timeoutId);
            }
            
            timeoutId = setTimeout(() => {
                fn.apply(this, args);
            }, delay);
        };
    }

    // ============================================================================
    // COMPLEX ALGORITHMS
    // ============================================================================

    function generateComplexData(size) {
        const data = [];
        for (let i = 0; i < size; i++) {
            data.push({
                id: i,
                hash: complexHashFunction(String(i)),
                nested: {
                    level1: {
                        level2: {
                            level3: {
                                value: Math.random()
                            }
                        }
                    }
                },
                array: Array.from({ length: 10 }, (_, j) => j * i)
            });
        }
        return data;
    }

    function sortComplexData(data) {
        return data.sort((a, b) => {
            if (a.hash !== b.hash) {
                return a.hash - b.hash;
            }
            return a.id - b.id;
        });
    }

    function filterComplexData(data, predicate) {
        const result = [];
        for (let i = 0; i < data.length; i++) {
            if (predicate(data[i])) {
                result.push(data[i]);
            }
        }
        return result;
    }

    function mergeComplexData(...dataSets) {
        const merged = new Map();
        
        dataSets.forEach(dataSet => {
            dataSet.forEach(item => {
                if (merged.has(item.id)) {
                    const existing = merged.get(item.id);
                    merged.set(item.id, { ...existing, ...item });
                } else {
                    merged.set(item.id, item);
                }
            });
        });

        return Array.from(merged.values());
    }

    // ============================================================================
    // FEATURE MANAGEMENT SYSTEM
    // ============================================================================

    class FeatureManager {
        constructor() {
            this.features = new Map();
            this.initialized = false;
        }

        registerFeature(name, config) {
            this.features.set(name, {
                name,
                config,
                enabled: config.enabled !== false,
                priority: config.priority || 0,
                dependencies: config.dependencies || []
            });
        }

        async initFeatures() {
            const sortedFeatures = Array.from(this.features.values())
                .sort((a, b) => b.priority - a.priority);
            
            for (const feature of sortedFeatures) {
                if (feature.enabled) {
                    await this.initFeature(feature);
                }
            }
            
            this.initialized = true;
        }

        async initFeature(feature) {
            // Check dependencies
            for (const dep of feature.dependencies) {
                if (!this.features.has(dep)) {
                    console.warn(`Feature ${feature.name} missing dependency: ${dep}`);
                    return;
                }
            }
            
            // Simulate async initialization
            return new Promise(resolve => {
                setTimeout(() => {
                    if (feature.config.onInit) {
                        feature.config.onInit();
                    }
                    resolve();
                }, 1);
            });
        }

        getFeature(name) {
            return this.features.get(name);
        }

        isEnabled(name) {
            const feature = this.features.get(name);
            return feature ? feature.enabled : false;
        }
    }

    // ============================================================================
    // PERFORMANCE MONITORING
    // ============================================================================

    class PerformanceMonitor {
        constructor() {
            this.marks = new Map();
            this.measures = [];
        }

        mark(name) {
            this.marks.set(name, performance.now());
        }

        measure(name, startMark, endMark) {
            const start = this.marks.get(startMark);
            const end = endMark ? this.marks.get(endMark) : performance.now();
            
            if (start !== undefined && end !== undefined) {
                const duration = end - start;
                this.measures.push({ name, duration, start, end });
                return duration;
            }
            return null;
        }

        getMeasures() {
            return [...this.measures];
        }

        clear() {
            this.marks.clear();
            this.measures = [];
        }
    }

    // ============================================================================
    // EVENT BUS SYSTEM
    // ============================================================================

    class EventBus {
        constructor() {
            this.listeners = new Map();
            this.onceListeners = new Map();
        }

        on(event, handler) {
            if (!this.listeners.has(event)) {
                this.listeners.set(event, []);
            }
            this.listeners.get(event).push(handler);

            return () => this.off(event, handler);
        }

        once(event, handler) {
            if (!this.onceListeners.has(event)) {
                this.onceListeners.set(event, []);
            }
            this.onceListeners.get(event).push(handler);
        }

        off(event, handler) {
            if (this.listeners.has(event)) {
                const handlers = this.listeners.get(event);
                const index = handlers.indexOf(handler);
                if (index > -1) {
                    handlers.splice(index, 1);
                }
            }
        }

        emit(event, data) {
            // Regular listeners
            if (this.listeners.has(event)) {
                this.listeners.get(event).forEach(handler => {
                    try {
                        handler(data);
                    } catch (e) {
                        console.error('Event handler error:', e);
                    }
                });
            }
            
            // Once listeners
            if (this.onceListeners.has(event)) {
                const handlers = this.onceListeners.get(event);
                this.onceListeners.delete(event);
                handlers.forEach(handler => {
                    try {
                        handler(data);
                    } catch (e) {
                        console.error('Event handler error:', e);
                    }
                });
            }
        }

        clear() {
            this.listeners.clear();
            this.onceListeners.clear();
        }
    }

    // ============================================================================
    // STATE MANAGEMENT
    // ============================================================================

    class StateManager {
        constructor(initialState = {}) {
            this.state = deepClone(initialState);
            this.subscribers = [];
            this.history = [deepClone(initialState)];
            this.maxHistory = 50;
        }

        getState() {
            return deepClone(this.state);
        }

        setState(updater) {
            const newState = typeof updater === 'function'
                ? updater(this.state)
                : { ...this.state, ...updater };
            
            if (JSON.stringify(newState) !== JSON.stringify(this.state)) {
                this.state = newState;
                
                // Add to history
                this.history.push(deepClone(newState));
                if (this.history.length > this.maxHistory) {
                    this.history.shift();
                }
                
                // Notify subscribers
                this.notify();
            }
        }

        subscribe(callback) {
            this.subscribers.push(callback);
            return () => {
                const index = this.subscribers.indexOf(callback);
                if (index > -1) {
                    this.subscribers.splice(index, 1);
                }
            };
        }

        notify() {
            const state = this.getState();
            this.subscribers.forEach(callback => {
                try {
                    callback(state);
                } catch (e) {
                    console.error('Subscriber error:', e);
                }
            });
        }

        getHistory() {
            return [...this.history];
        }

        undo() {
            if (this.history.length > 1) {
                this.history.pop();
                this.state = deepClone(this.history[this.history.length - 1]);
                this.notify();
            }
        }
    }

    // ============================================================================
    // INITIALIZATION AND MAIN LOGIC
    // ============================================================================

    const performanceMonitor = new PerformanceMonitor();
    performanceMonitor.mark('script-start');

    const featureManager = new FeatureManager();
    const eventBus = new EventBus();
    const stateManager = new StateManager({
        initialized: false,
        messageCount: 0,
        lastMessageTime: null
    });

    // Additional initializations
    const requestInterceptor = new RequestInterceptor({ enabled: true });
    const cookieManager = new CookieManager();
    const storageManager = new StorageManager();
    const requestQueue = new RequestQueue({ maxSize: 500, maxRetries: 2 });

    // Set up some interceptor rules for testing
    for (const tracker in additionalDomains.trackers) {
        requestInterceptor.addRule(tracker, 'block');
    }

    // Register some features
    featureManager.registerFeature('messaging', {
        enabled: true,
        priority: 100,
        onInit: () => {
            console.log('Messaging feature initialized');
        }
    });

    featureManager.registerFeature('performance', {
        enabled: true,
        priority: 90,
        dependencies: ['messaging'],
        onInit: () => {
            console.log('Performance feature initialized');
        }
    });

    // Do some complex operations
    performanceMonitor.mark('data-generation-start');
    const complexData = generateComplexData(100);
    performanceMonitor.mark('data-generation-end');
    performanceMonitor.measure('data-generation', 'data-generation-start', 'data-generation-end');

    performanceMonitor.mark('data-processing-start');
    const sortedData = sortComplexData([...complexData]);
    const filteredData = filterComplexData(sortedData, item => item.id % 2 === 0);
    const mergedData = mergeComplexData(complexData, filteredData);
    performanceMonitor.mark('data-processing-end');
    performanceMonitor.measure('data-processing', 'data-processing-start', 'data-processing-end');

    // Additional complex operations for structure expansion
    const hashTests = [
        murmurHash3('test'),
        xxHash('test'),
        simpleHash('test'),
        complexHashFunction('test')
    ];

    const urlTests = [
        isUrl('https://example.com'),
        isUrl('not a url'),
        isEmail('test@example.com'),
        isIPAddress('192.168.1.1')
    ];

    const dateTests = [
        formatDate(new Date(), 'YYYY-MM-DD HH:mm:ss'),
        isToday(new Date()),
        diffDays(new Date('2024-01-01'), new Date())
    ];

    // Initialize memoized functions
    const memoizedHash = memoize(complexHashFunction, { maxSize: 50, ttl: 5000 });
    const throttledLog = throttle(console.log.bind(console), 1000);
    const debouncedUpdate = debounce(() => {
        stateManager.setState(state => ({
            ...state,
            lastUpdate: Date.now()
        }));
    }, 500);

    // Initialize features asynchronously
    featureManager.initFeatures().then(() => {
        stateManager.setState({ initialized: true });
        eventBus.emit('initialized', { timestamp: Date.now() });
    });

    // Final performance mark
    performanceMonitor.mark('script-end');
    performanceMonitor.measure('total-execution', 'script-start', 'script-end');

    // Log performance data if needed
    if (delay === 0) {
        const measures = performanceMonitor.getMeasures();
        console.log('Performance measures:', measures);
    }

    // Expose additional internals for debugging
    if (typeof window !== 'undefined') {
        window.__webviewCompatTest = {
            featureManager,
            eventBus,
            stateManager,
            performanceMonitor,
            largeDomainList,
            additionalDomains,
            requestInterceptor,
            cookieManager,
            storageManager,
            requestQueue,
            utils: {
                hash: { murmurHash3, xxHash, simpleHash, complexHashFunction },
                string: { camelCase, kebabCase, snakeCase, slugify, truncate },
                array: { chunk, flatten, unique, groupBy, sortBy, intersection, difference, union },
                object: { pick, omit, merge, isEmpty, has, get, set },
                number: { clamp, random, randomInt, round, sum, average, median, standardDeviation },
                date: { formatDate, parseDate, addDays, diffDays, isToday, isBefore, isAfter },
                validate: { isEmail, isUrl, isPhoneNumber, isCreditCard, isIPAddress, isHexColor, isAlphanumeric, isNumeric },
                async: { asyncDelay, retry, timeout, parallel }
            },
            getState: () => stateManager.getState(),
            getMeasures: () => performanceMonitor.getMeasures(),
            getInterceptorStats: () => requestInterceptor.getStatistics(),
            getQueueStats: () => requestQueue.getStats(),
            getQuotaInfo: () => storageManager.getQuotaInfo()
        };
    }
})();
