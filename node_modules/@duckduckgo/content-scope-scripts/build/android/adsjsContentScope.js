/*! © DuckDuckGo ContentScopeScripts android-adsjs https://github.com/duckduckgo/content-scope-scripts/ */
"use strict";
(() => {
  var __defProp = Object.defineProperty;
  var __typeError = (msg) => {
    throw TypeError(msg);
  };
  var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
  var __publicField = (obj, key, value) => __defNormalProp(obj, typeof key !== "symbol" ? key + "" : key, value);
  var __accessCheck = (obj, member, msg) => member.has(obj) || __typeError("Cannot " + msg);
  var __privateGet = (obj, member, getter) => (__accessCheck(obj, member, "read from private field"), getter ? getter.call(obj) : member.get(obj));
  var __privateAdd = (obj, member, value) => member.has(obj) ? __typeError("Cannot add the same private member more than once") : member instanceof WeakSet ? member.add(obj) : member.set(obj, value);
  var __privateSet = (obj, member, value, setter) => (__accessCheck(obj, member, "write to private field"), setter ? setter.call(obj, value) : member.set(obj, value), value);
  var __privateMethod = (obj, member, method) => (__accessCheck(obj, member, "access private method"), method);

  // <define:import.meta.trackerLookup>
  var define_import_meta_trackerLookup_default = { com: { "2020mustang": 1, "33across": 1, "360yield": 1, "3lift": 1, "4dsply": 1, "9gtb": 1, "a-mx": 1, aaxads: 1, abtasty: 1, acsbapp: 1, acscdn: 1, acuityplatform: 1, "ad-score": 1, adalyser: 1, adapf: 1, adara: 1, addtoany: 1, adelixir: 1, adentifi: 1, adgrx: 1, adhese: 1, adition: 1, adkernel: 1, adlightning: 1, admanmedia: 1, admedo: 1, adnxs: 1, adobedtm: 1, adpone: 1, adpushup: 1, adroll: 1, "ads-twitter": 1, adsafeprotected: 1, adswizz: 1, adthrive: 1, adtng: 1, adultfriendfinder: 1, adventive: 1, adyen: 1, aegpresents: 1, affinity: 1, affirm: 1, agilone: 1, agkn: 1, agoda: 1, ahrefs: 1, aimbase: 1, albacross: 1, alcmpn: 1, "alia-prod": 1, alicdn: 1, aliyuncs: 1, alocdn: 1, "amazon-adsystem": 1, amazon: 1, amplitude: 1, amspbs: 1, amxrtb: 1, "analytics-egain": 1, aniview: 1, anymind360: 1, "app-us1": 1, appboycdn: 1, "apple-mapkit": 1, applovin: 1, aswpsdkus: 1, atlassian: 1, atmtd: 1, attntags: 1, audioeye: 1, "automizely-analytics": 1, automizely: 1, avantlink: 1, aweber: 1, awswaf: 1, azure: 1, b2c: 1, batch: 1, bc0a: 1, beehiiv: 1, betweendigital: 1, bfmio: 1, bing: 1, bizrate: 1, blis: 1, blismedia: 1, blogherads: 1, bloomreach: 1, bluecava: 1, booking: 1, boomtrain: 1, bounceexchange: 1, bqstreamer: 1, brainlyads: 1, "brand-display": 1, brandmetrics: 1, brealtime: 1, browsiprod: 1, btloader: 1, bttrack: 1, btttag: 1, bugsnag: 1, buzzoola: 1, byspotify: 1, c1904584f2: 1, callrail: 1, campaigner: 1, canva: 1, "captcha-delivery": 1, cartstack: 1, casalemedia: 1, "cdn-net": 1, cdninstagram: 1, cdnwidget: 1, channeladvisor: 1, chartbeat: 1, chaseherbalpasty: 1, chatango: 1, chaturbate: 1, cheqzone: 1, "ck-ie": 1, clearbit: 1, clickagy: 1, clicktripz: 1, clientgear: 1, cloudflare: 1, cloudflareinsights: 1, cnn: 1, cnzz: 1, codedrink: 1, cognitivlabs: 1, collegenet: 1, comm100: 1, "company-target": 1, "config-security": 1, connatix: 1, consentmo: 1, contentabc: 1, contextweb: 1, convertexperiments: 1, convertkit: 1, "cookie-script": 1, coosync: 1, cootlogix: 1, copper6: 1, coveo: 1, cquotient: 1, crazyegg: 1, crcldu: 1, "creative-serving": 1, creativecdn: 1, criteo: 1, ctctcdn: 1, ctnsnet: 1, cudasvc: 1, cxense: 1, dailymotion: 1, "datadoghq-browser-agent": 1, deadlinefunnel: 1, dealerinspire: 1, deepintent: 1, delivra: 1, demandbase: 1, dianomi: 1, digitaloceanspaces: 1, dimelochat: 1, discover: 1, disqus: 1, docaccess: 1, dotomi: 1, doubleverify: 1, driftt: 1, dtscout: 1, dwin1: 1, dynamicyield: 1, dynatrace: 1, ebxcdn: 1, eccmp: 1, elfsight: 1, eloqua: 1, emxdgt: 1, en25: 1, endowmentoverhangutmost: 1, ensighten: 1, epichosted: 1, eskimi: 1, evergage: 1, evgnet: 1, evidon: 1, exdynsrv: 1, exelator: 1, exoclick: 1, exosrv: 1, exponea: 1, exponential: 1, extole: 1, ezodn: 1, ezoicanalytics: 1, ezojs: 1, feefo: 1, five9: 1, flashtalking: 1, flippback: 1, flixcdn: 1, flodesk: 1, foresee: 1, fouanalytics: 1, freshworks: 1, fullstory: 1, g2: 1, "gannett-cdn": 1, gbqofs: 1, getcandid: 1, getdrip: 1, getelevar: 1, getrockerbox: 1, getsitecontrol: 1, godaddy: 1, "google-analytics": 1, google: 1, googleadservices: 1, googlehosted: 1, googleoptimize: 1, googlesyndication: 1, googletagmanager: 1, googletagservices: 1, greylabeldelivery: 1, groovehq: 1, gsitrix: 1, gstatic: 1, "guarantee-cdn": 1, gumgum: 1, hawksearch: 1, hearstnp: 1, heatmap: 1, hextom: 1, histats: 1, hotjar: 1, "hrzn-nxt": 1, "hs-banner": 1, "hs-scripts": 1, htlbid: 1, "html-load": 1, hubspot: 1, hubspotfeedback: 1, iadvize: 1, "id5-sync": 1, igodigital: 1, iljmp: 1, impact: 1, impactcdn: 1, "impactradius-event": 1, improvedcontactform: 1, improvedigital: 1, imrworldwide: 1, indexww: 1, infolinks: 1, infusionsoft: 1, inmobi: 1, inq: 1, "inside-graph": 1, instagram: 1, intentiq: 1, intergient: 1, intuit: 1, investingchannel: 1, invocacdn: 1, iperceptions: 1, iplsc: 1, ipredictive: 1, iqm: 1, issuu: 1, iteratehq: 1, janusaent: 1, jimstatic: 1, journeymv: 1, journity: 1, amazonaws: { "us-west-2": { s3: 1 }, "ap-southeast-2": 1, "eu-west-3": { s3: { "yelda-chat": 1 } } }, juicyads: 1, jwplayer: 1, kargo: 1, ketchcdn: 1, kettledroopingcontinuation: 1, kit: 1, klarna: 1, klaviyo: 1, krushmedia: 1, kueezrtb: 1, ladesk: 1, ladsp: 1, leadsrx: 1, lendingtree: 1, lexisnexis: 1, liadm: 1, licdn: 1, liftdsp: 1, lightboxcdn: 1, lijit: 1, linkconnector: 1, linkedin: 1, linksynergy: 1, "list-manage": 1, listrakbi: 1, livechatinc: 1, lngtdv: 1, localytics: 1, loggly: 1, loop11: 1, luckyorange: 1, magsrv: 1, mailchimp: 1, mailchimpapp: 1, mailerlite: 1, "maillist-manage": 1, "mantis-intelligence": 1, marketiq: 1, marketo: 1, marphezis: 1, marzaent: 1, matheranalytics: 1, mathtag: 1, mavrtracktor: 1, maxmind: 1, mczbf: 1, measureadv: 1, medallia: 1, media6degrees: 1, mediarithmics: 1, mediavine: 1, memberful: 1, mercari: 1, mfadsrvr: 1, mgid: 1, micpn: 1, "minutemedia-prebid": 1, mixpo: 1, mktoresp: 1, mktoweb: 1, ml314: 1, mmvideocdn: 1, moloco: 1, monsido: 1, mookie1: 1, mountain: 1, mouseflow: 1, mpeasylink: 1, mql5: 1, mxpnl: 1, mygaru: 1, myregistry: 1, newrelic: 1, newscgp: 1, nextdoor: 1, nextmillmedia: 1, nitropay: 1, noibu: 1, nosto: 1, npttech: 1, nuance: 1, nytrng: 1, omappapi: 1, omguk: 1, omnisnippet1: 1, omnisrc: 1, omnitagjs: 1, onaudience: 1, onesignal: 1, "onetag-sys": 1, opecloud: 1, opentable: 1, opentext: 1, openwebmp: 1, opera: 1, opmnstr: 1, "opti-digital": 1, optidigital: 1, optimizely: 1, optimonk: 1, optmnstr: 1, optmstr: 1, optnmnstr: 1, optnmstr: 1, optum: 1, oraclecloud: 1, orbsrv: 1, osano: 1, outbrain: 1, outbrainimg: 1, ownlocal: 1, parastorage: 1, pardot: 1, parsely: 1, patreon: 1, paypal: 1, pbstck: 1, "peer-39": 1, pemsrv: 1, perfalytics: 1, perfdrive: 1, permutive: 1, picreel: 1, pinterest: 1, pippio: 1, pixlee: 1, playwire: 1, postaffiliatepro: 1, posthog: 1, postrelease: 1, preferencenail: 1, pricespider: 1, protrafficinspector: 1, providesupport: 1, ptengine: 1, publir: 1, publitas: 1, pubmatic: 1, pubnation: 1, pulseinsights: 1, pulselive: 1, pushnami: 1, qq: 1, qualaroo: 1, qualified: 1, qualtrics: 1, quantcount: 1, quantserve: 1, quantummetric: 1, quora: 1, rakuten: 1, raptivecdn: 1, realizationnewestfangs: 1, realsrv: 1, rebuyengine: 1, recombee: 1, recruitics: 1, reddit: 1, redditstatic: 1, refinery89: 1, reson8: 1, responsiveads: 1, retargetly: 1, revcontent: 1, rezync: 1, rfihub: 1, rfksrv: 1, richaudience: 1, ringcentral: 1, rkdms: 1, rlcdn: 1, rmhfrtnd: 1, rmtag: 1, roeyecdn: 1, rogersmedia: 1, route: 1, rtbhouse: 1, rubiconproject: 1, rudderlabs: 1, rudderstack: 1, rvlife: 1, "sail-horizon": 1, sailthru: 1, salecycle: 1, "salesforce-sites": 1, salesforceliveagent: 1, sascdn: 1, scene7: 1, scholarlyiq: 1, schwab: 1, scorecardresearch: 1, screenpopper: 1, scriptwrapper: 1, searchserverapi1: 1, securedvisit: 1, seedtag: 1, segment: 1, servenobid: 1, serverbid: 1, herokuapp: { "session-recording-now": 1 }, sharethis: 1, sharethrough: 1, "shb-sync": 1, shgcdn3: 1, sibautomation: 1, signifyd: 1, site: 1, siteimprove: 1, siteimproveanalytics: 1, sitescout: 1, skimresources: 1, slickstream: 1, smartadserver: 1, "smartnews-ads": 1, smilewanted: 1, snapchat: 1, snigelweb: 1, socdm: 1, sojern: 1, sonobi: 1, sparteo: 1, spendsdetachment: 1, sportradar: 1, sportradarserving: 1, sportslocalmedia: 1, springserve: 1, stackadapt: 1, startappnetwork: 1, statcounter: 1, stay22: 1, googleapis: { storage: 1, imasdk: 1, ajax: 1, fonts: 1, translate: 1 }, streamrail: 1, stripchat: 1, sumome: 1, swaven: 1, swymrelay: 1, syf: 1, symantec: 1, syncingbridge: 1, taboola: 1, talkable: 1, taobao: 1, tapad: 1, tapioni: 1, tappx: 1, "teads-xo": 1, tealiumiq: 1, temu: 1, "the-ozone-project": 1, theadex: 1, thejobnetwork: 1, thestar: 1, thrtle: 1, tiktok: 1, tinypass: 1, tiqcdn: 1, trackjs: 1, trafficjunky: 1, travelaudience: 1, travelpayouts: 1, treasuredata: 1, tremorhub: 1, trendemon: 1, tribalfusion: 1, trueleadid: 1, trustedstack: 1, trustpielote: 1, trustpilot: 1, trvdp: 1, tsyndicate: 1, turn: 1, tvpixel: 1, tvspix: 1, tvsquared: 1, tweakwise: 1, twitter: 1, tynt: 1, uidapi: 1, unbxdapi: 1, undertone: 1, unpkg: 1, unrulymedia: 1, "uplift-platform": 1, uplift: 1, upsellit: 1, urbanairship: 1, usabilla: 1, usablenet: 1, usbrowserspeed: 1, useamp: 1, usemessages: 1, userapi: 1, uservoice: 1, valuecommerce: 1, "verint-cdn": 1, vidazoo: 1, videoplayerhub: 1, vidoomy: 1, vimeocdn: 1, vistarsagency: 1, "visually-io": 1, visualwebsiteoptimizer: 1, vk: 1, vrtcal: 1, wbd: 1, "we-stats": 1, webcontentassessor: 1, webengage: 1, webeyez: 1, webtraxs: 1, "webtrends-optimize": 1, webtrends: 1, weglot: 1, woosmap: 1, workdeadlinededicate: 1, "wt-safetag": 1, wysistat: 1, x: 1, yahoo: 1, yandex: 1, yango: 1, yieldlove: 1, yieldmo: 1, ymmobi: 1, yotpo: 1, "youtube-nocookie": 1, youtube: 1, zemanta: 1, zendesk: 1, zeotap: 1, "zi-scripts": 1, zohocdn: 1, zoologyfibre: 1, zoominfo: 1, zopim: 1, createsend1: 1, jivox: 1, klarnaservices: 1, solarwinds: 1, ivitrack: 1, kiyoh: 1, adnuntius: 1, schibsted: 1, facebook: 1, attentivemobile: 1, bootstrapcdn: 1, cloudinary: 1, cookieyes: 1, dtscdn: 1, fontawesome: 1, getclicky: 1, microsoft: 1, playdigo: 1, roeye: 1, rtbwise: 1, shopifycdn: 1, shopifysvc: 1, stripe: 1, vimeo: 1, wp: 1, yimg: 1, yandexmetrica: 1, ymetrica1: 1 }, net: { "2mdn": 1, "a-mo": 1, acint: 1, adform: 1, adhigh: 1, adobedc: 1, adspeed: 1, aggle: 1, appier: 1, edgekey: { au: 1, "com-v1": 1, de: 1, fr: 1, io: 1, net: 1, nl: 1, org: 1, com: { scene7: 1 }, "com-v2": 1 }, azurefd: 1, bannerflow: 1, basis: 1, "bf-ad": 1, bidswitch: 1, blueconic: 1, buysellads: 1, cachefly: 1, ccgateway: 1, "confiant-integrations": 1, contentpass: 1, contentsquare: 1, criteo: 1, crwdcntrl: 1, cloudfront: { d14jnfavjicsbe: 1, d1af033869koo7: 1, d1j0xlutvd326g: 1, d1vg5xiq7qffdj: 1, d1x4rwm1kh8pnu: 1, d21gpk1vhmjuf5: 1, d2trly8m2h0e8p: 1, d38xvr37kwwhcm: 1, d3djbgmgf0l8ev: 1, d3fukwxve5r8zf: 1, d3fv2pqyjay52z: 1, d3nn82uaxijpm6: 1, d6tizftlrpuof: 1, dm2q9qfzyjfox: 1, dokumfe7mps0i: 1, dsh7ky7308k4b: 1, duube1y6ojsji: 1, dvagh3p3rk8xj: 1, d2638j3z8ek976: 1 }, datatrac: 1, demdex: 1, dotmetrics: 1, doubleclick: 1, "e-planning": 1, episerver: 1, esm1: 1, eulerian: 1, everestjs: 1, everesttech: 1, eyeota: 1, ezoic: 1, facebook: 1, fastclick: 1, fbcdn: 1, fonts: 1, fuseplatform: 1, fwmrm: 1, gcprivacy: 1, "go-mpulse": 1, "go-vip": 1, gtranslate: 1, hadronid: 1, "hs-analytics": 1, hsadspixel: 1, hsappstatic: 1, hscta: 1, "im-apps": 1, impervadns: 1, fastly: { global: { sni: { j: 1, m: 1, s: 1 } } }, jsdelivr: 1, kakaocdn: 1, listhub: 1, livedoor: 1, liveperson: 1, lpsnmedia: 1, magnetmail: 1, marketo: 1, mateti: 1, media: 1, mjedge: 1, mobon: 1, monetate: 1, mrktmtrcs: 1, mxptint: 1, naver: 1, "nr-data": 1, ojrq: 1, omtrdc: 1, onecount: 1, openx: 1, openxcdn: 1, opta: 1, optimove: 1, p7cloud: 1, pages02: 1, pages03: 1, pages04: 1, pages05: 1, pages06: 1, pages08: 1, pingdom: 1, pmdstatic: 1, popcash: 1, "pro-market": 1, protechts: 1, akamaihd: { "pxlclnmdecom-a": 1 }, r9cdn: 1, rfihub: 1, akamaized: { s13emagst: 1, tmssl: 1 }, sancdn: 1, "sc-static": 1, semasio: 1, sexad: 1, smaato: 1, spreadshirts: 1, tfaforms: 1, uuidksinc: 1, viafoura: 1, visx: 1, w55c: 1, witglobal: 1, "wt-eu02": 1, yandex: 1, yastatic: 1, zencdn: 1, zetaglobal: 1, zucks: 1, emarsys: 1, apicit: 1, tradetracker: 1, "ad-delivery": 1, bkcdn: 1, chartbeat: 1, ortb: 1, eviltracker: 1 }, io: { "506": 1, "4dex": 1, adapex: 1, aditude: 1, adnami: 1, advolve: 1, aidata: 1, anonm: 1, anonymised: 1, bidr: 1, branch: 1, center: 1, chatra: 1, connectad: 1, cordial: 1, edgetag: 1, extole: 1, fsrv: 1, grsm: 1, hbrd: 1, instana: 1, intelligems: 1, juicer: 1, kameleoon: 1, karte: 1, litix: 1, lytics: 1, mediago: 1, missena: 1, mrf: 1, nexx360: 1, northbeam: 1, ntv: 1, optad360: 1, oracleinfinity: 1, "p-n": 1, personalizer: 1, pghub: 1, piano: 1, postscript: 1, powr: 1, presage: 1, rapidedge: 1, receptivity: 1, searchspring: 1, segment: 1, smct: 1, smile: 1, sspinc: 1, stape: 1, t13: 1, termly: 1, wovn: 1, yellowblue: 1, zprk: 1, pzz: 1, "1rx": 1, akstat: 1, digitalaudience: 1, hotjar: 1, "inmobi-choice": 1, pinklion: 1 }, co: { "6sc": 1, ayads: 1, clinch: 1, empowerlocal: 1, getlasso: 1, idio: 1, increasingly: 1, jads: 1, nc0: 1, optable: 1, "pm-serv": 1, prmutv: 1, t: 1, tctm: 1, tidio: 1, ujet: 1, vibe: 1, zip: 1 }, gt: { ad: 1 }, cloud: { aditude: 1, stpd: 1, squiz: 1 }, cc: { admaster: 1, "html-load": 1 }, jp: { admatrix: 1, fout: 1, ne: { hatena: 1 }, "impact-ad": 1, nakanohito: 1, ptengine: 1, r10s: 1, co: { rakuten: 1, yahoo: 1 }, rtoaster: 1, shinobi: 1, "team-rec": 1, uncn: 1, yimg: 1 }, pl: { adocean: 1, gemius: 1, nsaudience: 1, onet: 1, salesmanago: 1, wp: 1 }, re: { adsco: 1 }, org: { adsrvr: 1, ampproject: 1, "browser-update": 1, cleantalk: 1, featureassets: 1, flowplayer: 1, openstreetmap: 1, "privacy-center": 1, webvisor: 1, framasoft: 1, prodregistryv2: 1, "do-not-tracker": 1, trackersimulator: 1 }, biz: { adtarget: 1 }, google: { adtrafficquality: 1 }, tv: { attn: 1, iris: 1, ispot: 1, teads: 1, twitch: 1 }, de: { "auswaertiges-amt": 1, ioam: 1, itzbund: 1, stroeerdigitalgroup: 1 }, ai: { axon: 1, blackcrow: 1, dxtech: 1, evolv: 1, hybrid: 1, m2: 1, nrich: 1, programmaticx: 1, sardine: 1, wknd: 1 }, delivery: { ay: 1, monu: 1 }, ws: { bids: 1, rmbl: 1 }, ms: { clarity: 1 }, my: { cnt: 1 }, chat: { crisp: 1, gorgias: 1 }, gov: { dhs: 1, digitalgov: 1, weather: 1 }, ru: { digitaltarget: 1, mail: 1, megafon: 1, mts: 1, rambler: 1, top100: 1, yadro: 1, yandex: 1 }, nl: { dpgmedia: 1, rijksoverheid: 1 }, tech: { dv: 1, ingage: 1, primis: 1, yads: 1 }, es: { gaug: 1, pandect: 1 }, ca: { bc: { gov: 1 } }, me: { grow: 1, loopme: 1, tldw: 1 }, media: { grv: 1, nextday: 1, townsquare: 1, underdog: 1 }, health: { hcn: 1 }, page: { hlx: 1 }, cz: { imedia: 1, performax: 1, seznam: 1 }, app: { infusionsoft: 1, permutive: 1, shop: 1, run: { "us-central1": 1 }, web: { "wec-virtualassistant-cx-prod": 1 } }, live: { iqzonertb: 1 }, br: { com: { jsuol: 1 } }, eu: { kameleoon: 1, medallia: 1, rqtrk: 1, slgnt: 1, media01: 1, trengo: 1 }, services: { marketingautomation: 1 }, sg: { mediacorp: 1 }, info: { navistechnologies: 1, usergram: 1, webantenna: 1 }, au: { com: { news: 1, nine: 1, zipmoney: 1 } }, bi: { newsroom: 1 }, fr: { "open-system": 1 }, fm: { pdst: 1 }, pro: { piwik: 1, usocial: 1 }, it: { plug: 1, stbm: 1 }, network: { pub: 1 }, ch: { "ringier-advertising": 1, admin: 1, "da-services": 1 }, fi: { satis: 1, simpli: 1 }, ac: { script: 1 }, pe: { shop: 1 }, us: { shopmy: 1, tiktokw: 1, trkn: 1, zoom: 1 }, xyz: { tracookiepixel: 1 }, digital: { postmedia: 1 }, no: { acdn: 1, api: 1 }, events: { growplow: 1 }, goog: { "merchant-center-analytics": 1 }, example: { "ad-company": 1 }, site: { "ad-company": 1, "third-party": { bad: 1, broken: 1 } } };

  // src/captured-globals.js
  var Set2 = globalThis.Set;
  var Reflect2 = globalThis.Reflect;
  var customElementsGet = globalThis.customElements?.get.bind(globalThis.customElements);
  var customElementsDefine = globalThis.customElements?.define.bind(globalThis.customElements);
  var getOwnPropertyDescriptor = Object.getOwnPropertyDescriptor;
  var getOwnPropertyDescriptors = Object.getOwnPropertyDescriptors;
  var objectKeys = Object.keys;
  var objectEntries = Object.entries;
  var objectDefineProperty = Object.defineProperty;
  var URL2 = globalThis.URL;
  var Proxy2 = globalThis.Proxy;
  var functionToString = Function.prototype.toString;
  var TypeError2 = globalThis.TypeError;
  var Symbol2 = globalThis.Symbol;
  var hasOwnProperty = Object.prototype.hasOwnProperty;
  var dispatchEvent = globalThis.dispatchEvent?.bind(globalThis);
  var addEventListener = globalThis.addEventListener?.bind(globalThis);
  var removeEventListener = globalThis.removeEventListener?.bind(globalThis);
  var CustomEvent2 = globalThis.CustomEvent;
  var Promise2 = globalThis.Promise;
  var String2 = globalThis.String;
  var Map2 = globalThis.Map;
  var Error2 = globalThis.Error;
  var randomUUID = globalThis.crypto?.randomUUID?.bind(globalThis.crypto);
  var console2 = globalThis.console;
  var consoleLog = console2.log.bind(console2);
  var consoleWarn = console2.warn.bind(console2);
  var consoleError = console2.error.bind(console2);
  var TextEncoder = globalThis.TextEncoder;
  var TextDecoder = globalThis.TextDecoder;
  var Uint8Array2 = globalThis.Uint8Array;
  var Uint16Array = globalThis.Uint16Array;
  var Uint32Array2 = globalThis.Uint32Array;
  var JSONparse = JSON.parse;
  var atob = globalThis.atob?.bind(globalThis);
  var DOMException2 = globalThis.DOMException;
  var charCodeAt = globalThis.String.prototype.charCodeAt;
  var ReflectDeleteProperty = Reflect2.deleteProperty.bind(Reflect2);
  var ReflectApply = Reflect2.apply.bind(Reflect2);
  var getRandomValues = globalThis.crypto?.getRandomValues?.bind(globalThis.crypto);
  var generateKey = globalThis.crypto?.subtle?.generateKey?.bind(globalThis.crypto?.subtle);
  var exportKey = globalThis.crypto?.subtle?.exportKey?.bind(globalThis.crypto?.subtle);
  var importKey = globalThis.crypto?.subtle?.importKey?.bind(globalThis.crypto?.subtle);
  var encrypt = globalThis.crypto?.subtle?.encrypt?.bind(globalThis.crypto?.subtle);
  var decrypt = globalThis.crypto?.subtle?.decrypt?.bind(globalThis.crypto?.subtle);

  // src/utils.js
  var globalObj = typeof window === "undefined" ? globalThis : window;
  var Error3 = globalObj.Error;
  var messageSecret;
  var isAppleSiliconCache = null;
  var OriginalCustomEvent = typeof CustomEvent === "undefined" ? null : CustomEvent;
  var originalWindowDispatchEvent = typeof window === "undefined" ? null : window.dispatchEvent.bind(window);
  function registerMessageSecret(secret) {
    messageSecret = secret;
  }
  function getGlobal() {
    return globalObj;
  }
  function nextRandom(v2) {
    return Math.abs(v2 >> 1 | (v2 << 62 ^ v2 << 61) & ~(~0 << 63) << 62);
  }
  var exemptionLists = {};
  function shouldExemptUrl(type, url) {
    const list = exemptionLists[type];
    if (!list) return false;
    for (const regex of list) {
      if (regex.test(url)) {
        return true;
      }
    }
    return false;
  }
  var debug = false;
  function initStringExemptionLists(args) {
    const { stringExemptionLists } = args;
    debug = args.debug || false;
    for (const type in stringExemptionLists) {
      exemptionLists[type] = [];
      const exemptions = stringExemptionLists[type];
      if (!exemptions) continue;
      for (const stringExemption of exemptions) {
        exemptionLists[type].push(new RegExp(stringExemption));
      }
    }
  }
  function isBeingFramed() {
    if (globalThis.location && "ancestorOrigins" in globalThis.location) {
      return globalThis.location.ancestorOrigins.length > 0;
    }
    return globalThis.top !== globalThis.window;
  }
  function getTabUrl() {
    let framingURLString = null;
    try {
      framingURLString = globalThis.top.location.href;
    } catch {
      framingURLString = getTopLevelOriginFromFrameAncestors() ?? globalThis.document.referrer;
    }
    let framingURL;
    try {
      framingURL = new URL(framingURLString);
    } catch {
      framingURL = null;
    }
    return framingURL;
  }
  function getTopLevelOriginFromFrameAncestors() {
    if ("ancestorOrigins" in globalThis.location && globalThis.location.ancestorOrigins.length) {
      return globalThis.location.ancestorOrigins.item(globalThis.location.ancestorOrigins.length - 1);
    }
    return null;
  }
  function getTabHostname() {
    const topURLString = getTabUrl()?.hostname;
    return topURLString || null;
  }
  function matchHostname(hostname, exceptionDomain) {
    return hostname === exceptionDomain || hostname.endsWith(`.${exceptionDomain}`);
  }
  var lineTest = /(\()?(https?:[^)]+):[0-9]+:[0-9]+(\))?/;
  function getStackTraceUrls(stack) {
    const urls = new Set2();
    if (!stack) return urls;
    try {
      const errorLines = stack.split("\n");
      for (const line of errorLines) {
        const res = line.match(lineTest);
        if (res && res[2]) {
          urls.add(new URL(res[2], location.href));
        }
      }
    } catch (e) {
    }
    return urls;
  }
  function getStackTraceOrigins(stack) {
    const urls = getStackTraceUrls(stack);
    const origins = new Set2();
    for (const url of urls) {
      origins.add(url.hostname);
    }
    return origins;
  }
  function shouldExemptMethod(type) {
    const typeExemptions = exemptionLists[type];
    if (!typeExemptions || typeExemptions.length === 0) {
      return false;
    }
    const stack = getStack();
    if (!stack) return false;
    const errorFiles = getStackTraceUrls(stack);
    for (const path of errorFiles) {
      if (shouldExemptUrl(type, path.href)) {
        return true;
      }
    }
    return false;
  }
  function iterateDataKey(key, callback) {
    let item = key.charCodeAt(0);
    for (let i = 0; i < key.length; i++) {
      let byte = key.charCodeAt(i);
      for (let j2 = 8; j2 >= 0; j2--) {
        const res = callback(item, byte);
        if (res === null) {
          return;
        }
        item = nextRandom(item);
        byte = byte >> 1;
      }
    }
  }
  function isFeatureBroken(args, feature) {
    const isFeatureEnabled = args.site.enabledFeatures?.includes(feature) ?? false;
    if (isPlatformSpecificFeature(feature)) {
      return !isFeatureEnabled;
    }
    return args.site.isBroken || args.site.allowlisted || !isFeatureEnabled;
  }
  function camelcase(dashCaseText) {
    return dashCaseText.replace(/-(.)/g, (_2, letter) => {
      return letter.toUpperCase();
    });
  }
  function isAppleSilicon() {
    if (isAppleSiliconCache !== null) {
      return isAppleSiliconCache;
    }
    const canvas = document.createElement("canvas");
    const gl = canvas.getContext("webgl");
    const compressedTextureValue = gl?.getSupportedExtensions()?.indexOf("WEBGL_compressed_texture_etc");
    isAppleSiliconCache = typeof compressedTextureValue === "number" && compressedTextureValue !== -1;
    return isAppleSiliconCache;
  }
  function processAttrByCriteria(configSetting) {
    let bestOption;
    for (const item of configSetting) {
      if (item.criteria) {
        if (item.criteria.arch === "AppleSilicon" && isAppleSilicon()) {
          bestOption = item;
          break;
        }
      } else {
        bestOption = item;
      }
    }
    return bestOption;
  }
  var functionMap = {
    /** Useful for debugging APIs in the wild, shouldn't be used */
    debug: (...args) => {
      console.log("debugger", ...args);
      debugger;
    },
    noop: () => {
    }
  };
  function processAttr(configSetting, defaultValue) {
    if (typeof defaultValue === "number" && isNaN(defaultValue)) {
      defaultValue = void 0;
    }
    if (configSetting === void 0) {
      return defaultValue;
    }
    const configSettingType = typeof configSetting;
    switch (configSettingType) {
      case "object":
        if (Array.isArray(configSetting)) {
          const selectedSetting = processAttrByCriteria(configSetting);
          if (selectedSetting === void 0) {
            return defaultValue;
          }
          return processAttr(selectedSetting, defaultValue);
        }
        if (!configSetting.type) {
          return defaultValue;
        }
        if (configSetting.type === "function") {
          if (configSetting.functionName && functionMap[configSetting.functionName]) {
            return functionMap[configSetting.functionName];
          }
          if (configSetting.functionValue) {
            const functionValue = configSetting.functionValue;
            return () => processAttr(functionValue, void 0);
          }
        }
        if (configSetting.type === "undefined") {
          return void 0;
        }
        if (configSetting.async) {
          return DDGPromise.resolve(configSetting.value);
        }
        return configSetting.value;
      default:
        return defaultValue;
    }
  }
  function getStack() {
    return new Error3().stack;
  }
  function debugSerialize(argsArray) {
    const maxSerializedSize = 1e3;
    const serializedArgs = argsArray.map((arg) => {
      try {
        const serializableOut = JSON.stringify(arg);
        if (serializableOut.length > maxSerializedSize) {
          return `<truncated, length: ${serializableOut.length}, value: ${serializableOut.substring(0, maxSerializedSize)}...>`;
        }
        return serializableOut;
      } catch (e) {
        return "<unserializable>";
      }
    });
    return JSON.stringify(serializedArgs);
  }
  var DDGProxy = class {
    /**
     * @param {import('./content-feature').default} feature
     * @param {P} objectScope
     * @param {K} property
     * @param {ProxyObject<P, K>} proxyObject
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
        if (debug) {
          postDebugMessage(this.camelFeatureName, {
            isProxy: true,
            action: isExempt ? "ignore" : "restrict",
            kind: this.property,
            documentUrl: document.location.href,
            stack: getStack(),
            args: debugSerialize(args[2])
          });
        }
        if (isExempt) {
          return DDGReflect.apply(args[0], args[1], args[2]);
        }
        return proxyObject.apply(...args);
      };
      const getMethod = (target, prop, receiver) => {
        this.feature.addDebugFlag();
        if (prop === "toString") {
          const method = Reflect.get(target, prop, receiver).bind(target);
          Object.defineProperty(method, "toString", {
            value: String.toString.bind(String.toString),
            enumerable: false
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
      Reflect.set(this.objectScope, this.property, this.internal);
    }
    overloadDescriptor() {
      this.feature.defineProperty(this.objectScope, this.property, {
        value: this.internal,
        writable: true,
        enumerable: true,
        configurable: true
      });
    }
  };
  var maxCounter = /* @__PURE__ */ new Map();
  function numberOfTimesDebugged(feature) {
    const current = maxCounter.get(feature) ?? 0;
    maxCounter.set(feature, current + 1);
    return current + 1;
  }
  var DEBUG_MAX_TIMES = 5e3;
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
      message
    });
  }
  var DDGPromise = globalObj.Promise;
  var DDGReflect = globalObj.Reflect;
  function isUnprotectedDomain(topLevelHostname, featureList) {
    let unprotectedDomain = false;
    if (!topLevelHostname) {
      return false;
    }
    const domainParts = topLevelHostname.split(".");
    if (domainParts.length === 1) {
      return featureList.some((entry) => entry.domain === topLevelHostname);
    }
    while (domainParts.length > 1 && !unprotectedDomain) {
      const partialDomain = domainParts.join(".");
      unprotectedDomain = featureList.filter((domain) => domain.domain === partialDomain).length > 0;
      domainParts.shift();
    }
    return unprotectedDomain;
  }
  function computeLimitedSiteObject() {
    const tabURL = getTabUrl();
    return {
      domain: tabURL?.hostname || null,
      url: tabURL?.href || null
    };
  }
  function getPlatformVersion(preferences) {
    if (preferences.platform?.version !== void 0 && preferences.platform?.version !== "") {
      return preferences.platform.version;
    }
    if (preferences.versionNumber) {
      return preferences.versionNumber;
    }
    if (preferences.versionString) {
      return preferences.versionString;
    }
    return void 0;
  }
  function parseVersionString(versionString) {
    return versionString.split(".").map(Number);
  }
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
  function isSupportedVersion(minSupportedVersion, currentVersion) {
    if (typeof currentVersion === "string" && typeof minSupportedVersion === "string") {
      if (satisfiesMinVersion(minSupportedVersion, currentVersion)) {
        return true;
      }
    } else if (typeof currentVersion === "number" && typeof minSupportedVersion === "number") {
      if (minSupportedVersion <= currentVersion) {
        return true;
      }
    }
    return false;
  }
  function isMaxSupportedVersion(maxSupportedVersion, currentVersion) {
    if (typeof currentVersion === "string" && typeof maxSupportedVersion === "string") {
      if (satisfiesMinVersion(currentVersion, maxSupportedVersion)) {
        return true;
      }
    } else if (typeof currentVersion === "number" && typeof maxSupportedVersion === "number") {
      if (maxSupportedVersion >= currentVersion) {
        return true;
      }
    }
    return false;
  }
  function processConfig(data, userList, preferences, platformSpecificFeatures2 = []) {
    const topLevelHostname = getTabHostname();
    const site = computeLimitedSiteObject();
    const allowlisted = userList.filter((domain) => domain === topLevelHostname).length > 0;
    const output = { ...preferences };
    if (output.platform) {
      const version = getPlatformVersion(preferences);
      if (version) {
        output.platform.version = version;
      }
    }
    const enabledFeatures = computeEnabledFeatures(data, topLevelHostname, preferences.platform, platformSpecificFeatures2);
    const isBroken = isUnprotectedDomain(topLevelHostname, data.unprotectedTemporary);
    output.site = Object.assign(site, {
      isBroken,
      allowlisted,
      enabledFeatures
    });
    output.featureSettings = parseFeatureSettings(data, enabledFeatures);
    output.bundledConfig = data;
    output.messagingContextName = output.messagingContextName || "contentScopeScripts";
    return output;
  }
  function getLoadArgs(processedConfig) {
    const { platform, site, bundledConfig, messagingConfig, messageSecret: messageSecret2, messagingContextName, currentCohorts } = processedConfig;
    return { platform, site, bundledConfig, messagingConfig, messageSecret: messageSecret2, messagingContextName, currentCohorts };
  }
  function isStateEnabled(state, platform) {
    switch (state) {
      case "enabled":
        return true;
      case "disabled":
        return false;
      case "internal":
        return platform?.internal === true;
      case "preview":
        return platform?.preview === true;
      default:
        return false;
    }
  }
  function computeEnabledFeatures(data, topLevelHostname, platform, platformSpecificFeatures2 = []) {
    const remoteFeatureNames = Object.keys(data.features);
    const platformSpecificFeaturesNotInRemoteConfig = platformSpecificFeatures2.filter(
      (featureName) => !remoteFeatureNames.includes(featureName)
    );
    const enabledFeatures = remoteFeatureNames.filter((featureName) => {
      const feature = data.features[featureName];
      if (!feature) return false;
      if (feature.minSupportedVersion && platform?.version) {
        if (!isSupportedVersion(feature.minSupportedVersion, platform.version)) {
          return false;
        }
      }
      if (isSelfGatingFeature(featureName)) {
        return isStateEnabled(feature.state, platform);
      }
      return isStateEnabled(feature.state, platform) && !isUnprotectedDomain(topLevelHostname, feature.exceptions);
    }).concat(platformSpecificFeaturesNotInRemoteConfig);
    return enabledFeatures;
  }
  function parseFeatureSettings(data, enabledFeatures) {
    const featureSettings = {};
    const remoteFeatureNames = Object.keys(data.features);
    remoteFeatureNames.forEach((featureName) => {
      if (!enabledFeatures.includes(featureName)) {
        return;
      }
      const feature = data.features[featureName];
      if (!feature) return;
      featureSettings[featureName] = feature.settings;
    });
    return featureSettings;
  }
  function isGloballyDisabled(args) {
    return args.site.allowlisted || args.site.isBroken;
  }
  var platformSpecificFeatures = [
    "contextMenu",
    "navigatorInterface",
    "windowsPermissionUsage",
    "messageBridge",
    "favicon",
    "breakageReporting",
    "print",
    "webInterferenceDetection",
    "webDetection",
    "webEvents",
    "pageObserver",
    "hover",
    "trackerProtection"
    // only enabled on apple platforms
  ];
  var selfGatingFeatures = ["trackerProtection"];
  function isPlatformSpecificFeature(featureName) {
    return platformSpecificFeatures.includes(
      /** @type {import('./features.js').FeatureName} */
      featureName
    );
  }
  function isSelfGatingFeature(featureName) {
    return selfGatingFeatures.includes(
      /** @type {import('./features.js').FeatureName} */
      featureName
    );
  }
  function createCustomEvent(eventName, eventDetail) {
    return new OriginalCustomEvent(eventName, eventDetail);
  }
  function legacySendMessage(messageType, options) {
    return originalWindowDispatchEvent && originalWindowDispatchEvent(
      createCustomEvent("sendMessageProxy" + messageSecret, { detail: JSON.stringify({ messageType, options }) })
    );
  }

  // src/features.js
  var baseFeatures = (
    /** @type {FeatureName[]} */
    [
      "fingerprintingAudio",
      "fingerprintingBattery",
      "fingerprintingCanvas",
      "googleRejected",
      "gpc",
      "fingerprintingHardware",
      "referrer",
      "fingerprintingScreenSize",
      "fingerprintingTemporaryStorage",
      "navigatorInterface",
      "elementHiding",
      "exceptionHandler",
      "apiManipulation"
    ]
  );
  var otherFeatures = (
    /** @type {FeatureName[]} */
    [
      "clickToLoad",
      "contextMenu",
      "cookie",
      "messageBridge",
      "duckPlayer",
      "duckPlayerNative",
      "duckAiDataClearing",
      "duckAiChatHistory",
      "harmfulApis",
      "webCompat",
      "webDetection",
      "webEvents",
      "webInterferenceDetection",
      "windowsPermissionUsage",
      "uaChBrands",
      "brokerProtection",
      "performanceMetrics",
      "breakageReporting",
      "autofillImport",
      "favicon",
      "webTelemetry",
      "pageContext",
      "print",
      "pageObserver",
      "hover",
      "browserUiLock",
      "trackerProtection",
      "tabSuspension",
      "autofillPasskeys"
    ]
  );
  var platformSupport = {
    apple: ["webCompat", "duckPlayerNative", ...baseFeatures, "pageContext", "print", "trackerProtection"],
    "apple-isolated": [
      "contextMenu",
      "duckPlayer",
      "duckPlayerNative",
      "brokerProtection",
      "breakageReporting",
      "performanceMetrics",
      "clickToLoad",
      "messageBridge",
      "favicon",
      "webDetection",
      "webEvents",
      "webInterferenceDetection",
      "webTelemetry",
      "pageObserver",
      "hover",
      "tabSuspension"
    ],
    "apple-ai-clear": ["duckAiDataClearing"],
    "apple-ai-history": ["duckAiChatHistory"],
    android: [
      ...baseFeatures,
      "webCompat",
      "webDetection",
      "webEvents",
      "webInterferenceDetection",
      "breakageReporting",
      "duckPlayer",
      "messageBridge",
      "pageContext",
      "browserUiLock"
    ],
    "android-broker-protection": ["brokerProtection"],
    "android-ai-clear": ["duckAiDataClearing"],
    "android-ai-history": ["duckAiChatHistory"],
    "android-autofill-import": ["autofillImport"],
    "android-adsjs": [
      "apiManipulation",
      "webCompat",
      "fingerprintingHardware",
      "fingerprintingScreenSize",
      "fingerprintingTemporaryStorage",
      "fingerprintingAudio",
      "fingerprintingBattery",
      "gpc",
      "webDetection",
      "webEvents",
      "breakageReporting"
    ],
    windows: [
      "cookie",
      ...baseFeatures,
      "webDetection",
      "webEvents",
      "webInterferenceDetection",
      "webTelemetry",
      "windowsPermissionUsage",
      "uaChBrands",
      "duckPlayer",
      "brokerProtection",
      "breakageReporting",
      "messageBridge",
      "webCompat",
      "pageContext",
      "duckAiDataClearing",
      "performanceMetrics",
      "duckAiChatHistory",
      "autofillPasskeys"
    ],
    firefox: ["cookie", ...baseFeatures, "clickToLoad", "webDetection", "webEvents", "webInterferenceDetection", "breakageReporting"],
    chrome: ["cookie", ...baseFeatures, "clickToLoad", "webDetection", "webEvents", "webInterferenceDetection", "breakageReporting"],
    "chrome-mv3": ["cookie", ...baseFeatures, "clickToLoad", "webDetection", "webEvents", "webInterferenceDetection", "breakageReporting"],
    integration: [...baseFeatures, ...otherFeatures]
  };

  // src/performance.js
  var PerformanceMonitor = class {
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
  };
  var PerformanceMark = class {
    /**
     * @param {string} name
     */
    constructor(name) {
      this.name = name;
      performance.mark(this.name + "Start");
    }
    end() {
      performance.mark(this.name + "End");
    }
    measure() {
      performance.measure(this.name, this.name + "Start", this.name + "End");
    }
  };

  // src/wrapper-utils.js
  var ddgShimMark = /* @__PURE__ */ Symbol("ddgShimMark");
  function defineProperty(object, propertyName, descriptor) {
    objectDefineProperty(object, propertyName, descriptor);
  }
  function wrapToString(newFn, origFn, mockValue) {
    if (typeof newFn !== "function" || typeof origFn !== "function") {
      return newFn;
    }
    return new Proxy(newFn, { get: toStringGetTrap(origFn, mockValue) });
  }
  function toStringGetTrap(targetFn, mockValue) {
    return function get(target, prop, receiver) {
      if (prop === "toString") {
        const origToString = Reflect.get(targetFn, "toString", targetFn);
        const toStringProxy = new Proxy(origToString, {
          apply(target2, thisArg, argumentsList) {
            if (thisArg === receiver) {
              if (mockValue) {
                return mockValue;
              }
              return Reflect.apply(target2, targetFn, argumentsList);
            } else {
              return Reflect.apply(target2, thisArg, argumentsList);
            }
          },
          get(target2, prop2, receiver2) {
            if (prop2 === "toString") {
              const origToStringToString = Reflect.get(origToString, "toString", origToString);
              const toStringToStringProxy = new Proxy(origToStringToString, {
                apply(target3, thisArg, argumentsList) {
                  if (thisArg === toStringProxy) {
                    return Reflect.apply(target3, origToString, argumentsList);
                  } else {
                    return Reflect.apply(target3, thisArg, argumentsList);
                  }
                }
              });
              return toStringToStringProxy;
            }
            return Reflect.get(target2, prop2, receiver2);
          }
        });
        return toStringProxy;
      }
      return Reflect.get(target, prop, receiver);
    };
  }
  function wrapFunction(functionValue, realTarget) {
    return new Proxy(realTarget, {
      get(target, prop, receiver) {
        if (prop === "toString") {
          const method = Reflect.get(target, prop, receiver).bind(target);
          Object.defineProperty(method, "toString", {
            value: functionToString.bind(functionToString),
            enumerable: false
          });
          return method;
        }
        return Reflect.get(target, prop, receiver);
      },
      apply(_2, thisArg, argumentsList) {
        return Reflect.apply(functionValue, thisArg, argumentsList);
      }
    });
  }
  function mergePropertyDescriptors(origDescriptor, partialDescriptor) {
    if ("value" in origDescriptor && "value" in partialDescriptor || "get" in origDescriptor && "get" in partialDescriptor || "set" in origDescriptor && "set" in partialDescriptor) {
      const merged = {
        ...origDescriptor,
        ...partialDescriptor
      };
      if ("value" in merged) {
        return (
          /** @type {import('./wrapper-utils').StrictPropertyDescriptor} */
          {
            value: merged.value,
            writable: typeof merged.writable === "boolean" ? merged.writable : true,
            configurable: typeof merged.configurable === "boolean" ? merged.configurable : true,
            enumerable: typeof merged.enumerable === "boolean" ? merged.enumerable : true
          }
        );
      }
      return (
        /** @type {import('./wrapper-utils').StrictPropertyDescriptor} */
        {
          get: merged.get,
          set: merged.set,
          configurable: typeof merged.configurable === "boolean" ? merged.configurable : true,
          enumerable: typeof merged.enumerable === "boolean" ? merged.enumerable : true
        }
      );
    }
    return void 0;
  }
  function wrapProperty(object, propertyName, descriptor, definePropertyFn) {
    if (!object) {
      return;
    }
    const origDescriptor = getOwnPropertyDescriptor(object, propertyName);
    if (!origDescriptor) {
      return;
    }
    const merged = mergePropertyDescriptors(origDescriptor, descriptor);
    if (!merged) {
      throw new Error(`Property descriptor for ${propertyName} may only include the following keys: ${objectKeys(origDescriptor)}`);
    }
    definePropertyFn(object, propertyName, merged);
    return origDescriptor;
  }
  function wrapMethod(object, propertyName, wrapperFn, definePropertyFn) {
    if (!object) {
      return;
    }
    const origDescriptor = getOwnPropertyDescriptor(object, propertyName);
    if (!origDescriptor) {
      return;
    }
    const origFn = origDescriptor.value;
    if (!origFn || typeof origFn !== "function") {
      throw new Error(`Property ${propertyName} does not look like a method`);
    }
    const newFn = wrapToString(
      /** @this {any} */
      function() {
        return wrapperFn.call(this, origFn, ...arguments);
      },
      origFn
    );
    definePropertyFn(object, propertyName, {
      ...origDescriptor,
      value: newFn
    });
    return origDescriptor;
  }
  function shimInterface(interfaceName, ImplClass, options, definePropertyFn, injectName) {
    const g = globalThis;
    if (injectName === "integration") {
      if (!g.origInterfaceDescriptors) g.origInterfaceDescriptors = {};
      const descriptor = Object.getOwnPropertyDescriptor(globalThis, interfaceName);
      g.origInterfaceDescriptors[interfaceName] = descriptor;
      g.ddgShimMark = ddgShimMark;
    }
    const defaultOptions = {
      allowConstructorCall: false,
      disallowConstructor: false,
      constructorErrorMessage: "Illegal constructor",
      wrapToString: true
    };
    const fullOptions = {
      interfaceDescriptorOptions: { writable: true, enumerable: false, configurable: true, value: ImplClass },
      ...defaultOptions,
      ...options
    };
    const proxyHandler = {};
    if (fullOptions.allowConstructorCall) {
      proxyHandler.apply = function(target, _thisArg, argumentsList) {
        return Reflect.construct(target, argumentsList, target);
      };
    }
    if (fullOptions.disallowConstructor) {
      proxyHandler.construct = function() {
        throw new TypeError(fullOptions.constructorErrorMessage);
      };
    }
    if (fullOptions.wrapToString) {
      for (const [prop, descriptor] of objectEntries(getOwnPropertyDescriptors(ImplClass.prototype))) {
        if (prop !== "constructor" && descriptor.writable && typeof descriptor.value === "function") {
          ImplClass.prototype[prop] = new Proxy(descriptor.value, {
            get: toStringGetTrap(descriptor.value, `function ${prop}() { [native code] }`)
          });
        }
      }
      Object.assign(proxyHandler, {
        get: toStringGetTrap(ImplClass, `function ${interfaceName}() { [native code] }`)
      });
    }
    const Interface = new Proxy(ImplClass, proxyHandler);
    if (ImplClass.prototype?.constructor === ImplClass) {
      const descriptor = getOwnPropertyDescriptor(ImplClass.prototype, "constructor");
      if (descriptor.writable) {
        ImplClass.prototype.constructor = Interface;
      }
    }
    if (injectName === "integration") {
      definePropertyFn(ImplClass, ddgShimMark, {
        value: true,
        configurable: false,
        enumerable: false,
        writable: false
      });
    }
    definePropertyFn(ImplClass, "name", {
      value: interfaceName,
      configurable: true,
      enumerable: false,
      writable: false
    });
    definePropertyFn(globalThis, interfaceName, { ...fullOptions.interfaceDescriptorOptions, value: Interface });
  }
  function shimProperty(baseObject, propertyName, implInstance, readOnly, definePropertyFn, injectName) {
    const ImplClass = implInstance.constructor;
    const g = globalThis;
    if (injectName === "integration") {
      if (!g.origPropDescriptors) g.origPropDescriptors = [];
      const descriptor2 = Object.getOwnPropertyDescriptor(baseObject, propertyName);
      g.origPropDescriptors.push([baseObject, propertyName, descriptor2]);
      g.ddgShimMark = ddgShimMark;
      if (ImplClass[ddgShimMark] !== true) {
        throw new TypeError("implInstance must be an instance of a shimmed class");
      }
    }
    const proxiedInstance = new Proxy(implInstance, {
      get: toStringGetTrap(implInstance, `[object ${ImplClass.name}]`)
    });
    let descriptor;
    if (readOnly) {
      const getter = function get() {
        return proxiedInstance;
      };
      const proxiedGetter = new Proxy(getter, {
        get: toStringGetTrap(getter, `function get ${propertyName}() { [native code] }`)
      });
      descriptor = {
        configurable: true,
        enumerable: true,
        get: proxiedGetter
      };
    } else {
      descriptor = {
        configurable: true,
        enumerable: true,
        writable: true,
        value: proxiedInstance
      };
    }
    definePropertyFn(baseObject, propertyName, descriptor);
  }

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

  // src/navigator-global.js
  function ensureNavigatorDuckDuckGo({ defineProperty: defineProperty2 = objectDefineProperty } = {}) {
    if (navigator.duckduckgo) {
      return navigator.duckduckgo;
    }
    const target = { messageHandlers: {} };
    defineProperty2(Navigator.prototype, "duckduckgo", {
      value: target,
      enumerable: true,
      configurable: false,
      writable: false
    });
    return target;
  }

  // ../messaging/lib/webkit.js
  var WebkitMessagingTransport = class {
    /**
     * @param {WebkitMessagingConfig} config
     * @param {import('../index.js').MessagingContext} messagingContext
     */
    constructor(config, messagingContext) {
      /**
       * Null-prototype cache so a hostile page that pollutes `Object.prototype`
       * cannot supply a callable from there if `capture` ever misses a handler.
       *
       * Uses the `{ __proto__: null }` literal rather than `Object.create(null)`
       * because the latter is a method dispatch through `globalThis.Object`, which
       * page JS could replace before this class field runs if transport
       * construction is deferred (`Messaging` is lazy on `ContentFeature.messaging`).
       * The `__proto__: null` literal is a syntactic construct, not method
       * dispatch, so it always yields a true null-prototype object.
       * @type {Record<string, { handler: any, postMessage: Function }>}
       */
      __publicField(
        this,
        "capturedWebkitHandlers",
        /** @type {any} */
        { __proto__: null }
      );
      this.messagingContext = messagingContext;
      this.config = config;
      this.captureWebkitHandlers(this.config.webkitMessageHandlerNames);
    }
    /**
     * Sends message to the webkit layer (fire and forget)
     * @param {String} handler
     * @param {*} data
     * @returns {*}
     * @throws {MissingHandler}
     * @internal
     */
    wkSend(handler, data = {}) {
      const captured = this.capturedWebkitHandlers[handler];
      if (!captured || typeof captured.postMessage !== "function") {
        throw new MissingHandler(`Missing webkit handler: '${handler}'`, handler);
      }
      return ReflectApply(captured.postMessage, captured.handler, [data]);
    }
    /**
     * Sends message to the webkit layer and waits for the specified response
     * @param {String} handler
     * @param {import('../index.js').RequestMessage} data
     * @returns {Promise<*>}
     * @internal
     */
    async wkSendAndWait(handler, data) {
      const response = await this.wkSend(handler, data);
      return JSONparse(response || "{}");
    }
    /**
     * @param {import('../index.js').NotificationMessage} msg
     * @returns {Promise<void>}
     */
    async notify(msg) {
      await this.wkSend(msg.context, msg);
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
          throw new Error2(data.error.message);
        }
      }
      throw new Error2("an unknown error occurred");
    }
    /**
     * Capture the `postMessage` method on each webkit messageHandler so the
     * transport can call them later without re-reading `window.webkit.messageHandlers`.
     * Makes the transport resilient to later removal or replacement of
     * `window.webkit.messageHandlers` (e.g. by privacy hardening that nullifies
     * the namespace for site JS to reduce fingerprinting surface).
     *
     * Stores the handler object and its `postMessage` function as a pair so
     * `wkSend` can dispatch via the captured `ReflectApply` rather than calling
     * `.bind()` here. `.bind` is a method on the page-mutable
     * `Function.prototype` — if transport construction is deferred (`Messaging`
     * is lazy on `ContentFeature.messaging`) page JS could replace
     * `Function.prototype.bind` first and have the cache store an attacker-
     * controlled function. Storing the unbound pair sidesteps that.
     *
     * @param {string[]} handlerNames
     */
    captureWebkitHandlers(handlerNames) {
      const handlers = window.webkit.messageHandlers;
      if (!handlers) throw new MissingHandler("window.webkit.messageHandlers was absent", "all");
      for (const webkitMessageHandlerName of handlerNames) {
        const handler = handlers[webkitMessageHandlerName];
        if (typeof handler?.postMessage === "function") {
          this.capturedWebkitHandlers[webkitMessageHandlerName] = {
            handler,
            postMessage: handler.postMessage
          };
        }
      }
    }
    /**
     * @param {import('../index.js').Subscription} msg
     * @param {(value: unknown) => void} callback
     */
    subscribe(msg, callback) {
      const target = ensureNavigatorDuckDuckGo().messageHandlers;
      if (msg.subscriptionName in target) {
        throw new Error2(`A subscription with the name ${msg.subscriptionName} already exists`);
      }
      objectDefineProperty(target, msg.subscriptionName, {
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
        ReflectDeleteProperty(target, msg.subscriptionName);
      };
    }
  };
  var WebkitMessagingConfig = class {
    /**
     * @param {object} params
     * @param {string[]} params.webkitMessageHandlerNames
     * @internal
     */
    constructor(params) {
      this.webkitMessageHandlerNames = params.webkitMessageHandlerNames;
    }
  };

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
      } catch (e) {
        console.error(".notify failed", e);
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
        } catch (e) {
          unsub();
          reject(new Error("request failed to send: " + e.message || "unknown error"));
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
      /** @type {(json: string, secret: string) => void} */
      __publicField(this, "_capturedHandler");
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
      } catch (e) {
        if (this.debug) {
          console.error("AndroidMessagingConfig error:", context);
          console.error(e);
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
      } catch (e) {
        console.error(".notify failed", e);
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
        } catch (e) {
          unsub();
          reject(new Error("request failed to send: " + e.message || "unknown error"));
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
    /**
     * @param {object} params
     * @param {Record<string, any>} params.target
     * @param {boolean} params.debug
     * @param {string} params.objectName - the object name for addWebMessageListener
     */
    constructor(params) {
      /** @type {{
       * postMessage: (message: string) => void,
       * addEventListener: (type: string, listener: (event: MessageEvent) => void) => void,
       * } | null} */
      __publicField(this, "_capturedHandler");
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
      } catch (e) {
        if (this.debug) {
          console.error("AndroidAdsjsMessagingConfig error:", context);
          console.error(e);
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
        } catch (e) {
          this._log("Error processing incoming message:", e);
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
      } catch (e) {
        this._log("Failed to send initial ping:", e);
        return false;
      }
    }
  };

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
      try {
        const message = new NotificationMessage({
          context: this.messagingContext.context,
          featureName: this.messagingContext.featureName,
          method: name,
          params: data
        });
        const maybeAsyncResult = this.transport.notify(message);
        if (isPromiseLike(maybeAsyncResult)) {
          void handleAsyncNotificationResult(maybeAsyncResult, this.messagingContext.env, name, data);
        }
      } catch (e) {
        logNotificationError(this.messagingContext.env, name, data, e);
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
  function isPromiseLike(value) {
    return value !== null && value !== void 0 && typeof /** @type {{then?: unknown}} */
    value.then === "function";
  }
  async function handleAsyncNotificationResult(result, env, name, data) {
    try {
      await result;
    } catch (error) {
      logNotificationError(env, name, data, error);
    }
  }
  function logNotificationError(env, name, data, error) {
    if (env === "development") {
      try {
        console.error("[Messaging] Failed to send notification:", error);
        console.error("[Messaging] Message details:", { name, data });
      } catch {
      }
    }
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

  // src/sendmessage-transport.js
  var sharedTransport = null;
  function extensionConstructMessagingConfig() {
    return new TestTransportConfig(getSharedMessagingTransport());
  }
  function getSharedMessagingTransport() {
    if (!sharedTransport) {
      sharedTransport = new SendMessageMessagingTransport();
    }
    return sharedTransport;
  }
  var SendMessageMessagingTransport = class {
    constructor() {
      /**
       * Queue of callbacks to be called with messages sent from the Platform.
       * This is used to connect requests with responses and to trigger subscriptions callbacks.
       */
      __publicField(this, "_queue", /* @__PURE__ */ new Set());
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
     * @param {unknown} response
     */
    onResponse(response) {
      this._queue.forEach((subscription) => subscription(response));
    }
    /**
     * @param {import('@duckduckgo/messaging').NotificationMessage} msg
     */
    notify(msg) {
      let params = msg.params;
      if (msg.method === "setYoutubePreviewsEnabled") {
        params = msg.params?.youtubePreviewsEnabled;
      }
      if (msg.method === "updateYouTubeCTLAddedFlag") {
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
      if (req.method === "getYouTubeVideoDetails") {
        comparator = (eventData) => {
          return eventData.responseMessageType === req.method && eventData.response && eventData.response.videoURL === req.params?.videoURL;
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
      let teardown;
      const idHandler = (event) => {
        if (!event) {
          console.warn("no message available");
          return;
        }
        if (comparator(event)) {
          if (!teardown) throw new this.globals.Error("unreachable");
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
  };

  // src/trackers.js
  function isTrackerOrigin(trackerLookup, originHostname = getGlobal().document.location.hostname) {
    const parts = originHostname.split(".").reverse();
    let node = trackerLookup;
    for (const sub of parts) {
      const next = node[sub];
      if (next === 1) {
        return true;
      } else if (next) {
        node = next;
      } else {
        return false;
      }
    }
    return false;
  }

  // ../node_modules/immutable-json-patch/lib/esm/typeguards.js
  function isJSONArray(value) {
    return Array.isArray(value);
  }
  function isJSONObject(value) {
    return value !== null && typeof value === "object" && (value.constructor === void 0 || // for example Object.create(null)
    value.constructor.name === "Object");
  }

  // ../node_modules/immutable-json-patch/lib/esm/utils.js
  function isEqual(a2, b2) {
    return JSON.stringify(a2) === JSON.stringify(b2);
  }
  function initial(array) {
    return array.slice(0, array.length - 1);
  }
  function last(array) {
    return array[array.length - 1];
  }
  function isObjectOrArray(value) {
    return typeof value === "object" && value !== null;
  }

  // ../node_modules/immutable-json-patch/lib/esm/immutabilityHelpers.js
  function shallowClone(value) {
    if (isJSONArray(value)) {
      const copy2 = value.slice();
      Object.getOwnPropertySymbols(value).forEach((symbol) => {
        copy2[symbol] = value[symbol];
      });
      return copy2;
    }
    if (isJSONObject(value)) {
      const copy2 = {
        ...value
      };
      Object.getOwnPropertySymbols(value).forEach((symbol) => {
        copy2[symbol] = value[symbol];
      });
      return copy2;
    }
    return value;
  }
  function applyProp(object, key, value) {
    if (object[key] === value) {
      return object;
    }
    const updatedObject = shallowClone(object);
    updatedObject[key] = value;
    return updatedObject;
  }
  function getIn(object, path) {
    let value = object;
    let i = 0;
    while (i < path.length) {
      if (isJSONObject(value)) {
        value = value[path[i]];
      } else if (isJSONArray(value)) {
        value = value[Number.parseInt(path[i], 10)];
      } else {
        value = void 0;
      }
      i++;
    }
    return value;
  }
  function setIn(object, path, value, createPath = false) {
    if (path.length === 0) {
      return value;
    }
    const key = path[0];
    const updatedValue = setIn(object ? object[key] : void 0, path.slice(1), value, createPath);
    if (isJSONObject(object) || isJSONArray(object)) {
      return applyProp(object, key, updatedValue);
    }
    if (createPath) {
      const newObject = IS_INTEGER_REGEX.test(key) ? [] : {};
      newObject[key] = updatedValue;
      return newObject;
    }
    throw new Error("Path does not exist");
  }
  var IS_INTEGER_REGEX = /^\d+$/;
  function updateIn(object, path, transform) {
    if (path.length === 0) {
      return transform(object);
    }
    if (!isObjectOrArray(object)) {
      throw new Error("Path doesn't exist");
    }
    const key = path[0];
    const updatedValue = updateIn(object[key], path.slice(1), transform);
    return applyProp(object, key, updatedValue);
  }
  function deleteIn(object, path) {
    if (path.length === 0) {
      return object;
    }
    if (!isObjectOrArray(object)) {
      throw new Error("Path does not exist");
    }
    if (path.length === 1) {
      const key2 = path[0];
      if (!(key2 in object)) {
        return object;
      }
      const updatedObject = shallowClone(object);
      if (isJSONArray(updatedObject)) {
        updatedObject.splice(Number.parseInt(key2, 10), 1);
      }
      if (isJSONObject(updatedObject)) {
        delete updatedObject[key2];
      }
      return updatedObject;
    }
    const key = path[0];
    const updatedValue = deleteIn(object[key], path.slice(1));
    return applyProp(object, key, updatedValue);
  }
  function insertAt(document2, path, value) {
    const parentPath = path.slice(0, path.length - 1);
    const index = path[path.length - 1];
    return updateIn(document2, parentPath, (items) => {
      if (!Array.isArray(items)) {
        throw new TypeError(`Array expected at path ${JSON.stringify(parentPath)}`);
      }
      const updatedItems = shallowClone(items);
      updatedItems.splice(Number.parseInt(index, 10), 0, value);
      return updatedItems;
    });
  }
  function existsIn(document2, path) {
    if (document2 === void 0) {
      return false;
    }
    if (path.length === 0) {
      return true;
    }
    if (document2 === null) {
      return false;
    }
    return existsIn(document2[path[0]], path.slice(1));
  }

  // ../node_modules/immutable-json-patch/lib/esm/jsonPointer.js
  function parseJSONPointer(pointer) {
    const path = pointer.split("/");
    path.shift();
    return path.map((p) => p.replace(/~1/g, "/").replace(/~0/g, "~"));
  }
  function compileJSONPointer(path) {
    return path.map(compileJSONPointerProp).join("");
  }
  function compileJSONPointerProp(pathProp) {
    return `/${String(pathProp).replace(/~/g, "~0").replace(/\//g, "~1")}`;
  }

  // ../node_modules/immutable-json-patch/lib/esm/immutableJSONPatch.js
  function immutableJSONPatch(document2, operations, options) {
    let updatedDocument = document2;
    for (let i = 0; i < operations.length; i++) {
      validateJSONPatchOperation(operations[i]);
      let operation = operations[i];
      if (options?.before) {
        const result = options.before(updatedDocument, operation);
        if (result !== void 0) {
          if (result.document !== void 0) {
            updatedDocument = result.document;
          }
          if (result.json !== void 0) {
            throw new Error('Deprecation warning: returned object property ".json" has been renamed to ".document"');
          }
          if (result.operation !== void 0) {
            operation = result.operation;
          }
        }
      }
      const previousDocument = updatedDocument;
      const path = parsePath(updatedDocument, operation.path);
      if (operation.op === "add") {
        updatedDocument = add(updatedDocument, path, operation.value);
      } else if (operation.op === "remove") {
        updatedDocument = remove(updatedDocument, path);
      } else if (operation.op === "replace") {
        updatedDocument = replace(updatedDocument, path, operation.value);
      } else if (operation.op === "copy") {
        updatedDocument = copy(updatedDocument, path, parseFrom(operation.from));
      } else if (operation.op === "move") {
        updatedDocument = move(updatedDocument, path, parseFrom(operation.from));
      } else if (operation.op === "test") {
        test(updatedDocument, path, operation.value);
      } else {
        throw new Error(`Unknown JSONPatch operation ${JSON.stringify(operation)}`);
      }
      if (options?.after) {
        const result = options.after(updatedDocument, operation, previousDocument);
        if (result !== void 0) {
          updatedDocument = result;
        }
      }
    }
    return updatedDocument;
  }
  function replace(document2, path, value) {
    return existsIn(document2, path) ? setIn(document2, path, value) : document2;
  }
  function remove(document2, path) {
    return deleteIn(document2, path);
  }
  function add(document2, path, value) {
    if (isArrayItem(document2, path)) {
      return insertAt(document2, path, value);
    }
    return setIn(document2, path, value);
  }
  function copy(document2, path, from) {
    const value = getIn(document2, from);
    if (isArrayItem(document2, path)) {
      return insertAt(document2, path, value);
    }
    return setIn(document2, path, value);
  }
  function move(document2, path, from) {
    const value = getIn(document2, from);
    const removedJson = deleteIn(document2, from);
    return isArrayItem(removedJson, path) ? insertAt(removedJson, path, value) : setIn(removedJson, path, value);
  }
  function test(document2, path, value) {
    if (value === void 0) {
      throw new Error(`Test failed: no value provided (path: "${compileJSONPointer(path)}")`);
    }
    if (!existsIn(document2, path)) {
      throw new Error(`Test failed: path not found (path: "${compileJSONPointer(path)}")`);
    }
    const actualValue = getIn(document2, path);
    if (!isEqual(actualValue, value)) {
      throw new Error(`Test failed, value differs (path: "${compileJSONPointer(path)}")`);
    }
  }
  function isArrayItem(document2, path) {
    if (path.length === 0) {
      return false;
    }
    const parent = getIn(document2, initial(path));
    return Array.isArray(parent);
  }
  function resolvePathIndex(document2, path) {
    if (last(path) !== "-") {
      return path;
    }
    const parentPath = initial(path);
    const parent = getIn(document2, parentPath);
    return parentPath.concat(parent.length);
  }
  function validateJSONPatchOperation(operation) {
    const ops = ["add", "remove", "replace", "copy", "move", "test"];
    if (!ops.includes(operation.op)) {
      throw new Error(`Unknown JSONPatch op ${JSON.stringify(operation.op)}`);
    }
    if (typeof operation.path !== "string") {
      throw new Error(`Required property "path" missing or not a string in operation ${JSON.stringify(operation)}`);
    }
    if (operation.op === "copy" || operation.op === "move") {
      if (typeof operation.from !== "string") {
        throw new Error(`Required property "from" missing or not a string in operation ${JSON.stringify(operation)}`);
      }
    }
  }
  function parsePath(document2, pointer) {
    return resolvePathIndex(document2, parseJSONPointer(pointer));
  }
  function parseFrom(fromPointer) {
    return parseJSONPointer(fromPointer);
  }

  // ../node_modules/urlpattern-polyfill/dist/urlpattern.js
  var Pe = Object.defineProperty;
  var a = (e, t) => Pe(e, "name", { value: t, configurable: true });
  var P = class {
    constructor(t, r, n, c, l, f) {
      __publicField(this, "type", 3);
      __publicField(this, "name", "");
      __publicField(this, "prefix", "");
      __publicField(this, "value", "");
      __publicField(this, "suffix", "");
      __publicField(this, "modifier", 3);
      this.type = t, this.name = r, this.prefix = n, this.value = c, this.suffix = l, this.modifier = f;
    }
    hasCustomName() {
      return this.name !== "" && typeof this.name != "number";
    }
  };
  a(P, "Part");
  var Re = /[$_\p{ID_Start}]/u;
  var Ee = /[$_\u200C\u200D\p{ID_Continue}]/u;
  var v = ".*";
  function Oe(e, t) {
    return (t ? /^[\x00-\xFF]*$/ : /^[\x00-\x7F]*$/).test(e);
  }
  a(Oe, "isASCII");
  function D(e, t = false) {
    let r = [], n = 0;
    for (; n < e.length; ) {
      let c = e[n], l = a(function(f) {
        if (!t) throw new TypeError(f);
        r.push({ type: "INVALID_CHAR", index: n, value: e[n++] });
      }, "ErrorOrInvalid");
      if (c === "*") {
        r.push({ type: "ASTERISK", index: n, value: e[n++] });
        continue;
      }
      if (c === "+" || c === "?") {
        r.push({ type: "OTHER_MODIFIER", index: n, value: e[n++] });
        continue;
      }
      if (c === "\\") {
        r.push({ type: "ESCAPED_CHAR", index: n++, value: e[n++] });
        continue;
      }
      if (c === "{") {
        r.push({ type: "OPEN", index: n, value: e[n++] });
        continue;
      }
      if (c === "}") {
        r.push({ type: "CLOSE", index: n, value: e[n++] });
        continue;
      }
      if (c === ":") {
        let f = "", s = n + 1;
        for (; s < e.length; ) {
          let i = e.substr(s, 1);
          if (s === n + 1 && Re.test(i) || s !== n + 1 && Ee.test(i)) {
            f += e[s++];
            continue;
          }
          break;
        }
        if (!f) {
          l(`Missing parameter name at ${n}`);
          continue;
        }
        r.push({ type: "NAME", index: n, value: f }), n = s;
        continue;
      }
      if (c === "(") {
        let f = 1, s = "", i = n + 1, o = false;
        if (e[i] === "?") {
          l(`Pattern cannot start with "?" at ${i}`);
          continue;
        }
        for (; i < e.length; ) {
          if (!Oe(e[i], false)) {
            l(`Invalid character '${e[i]}' at ${i}.`), o = true;
            break;
          }
          if (e[i] === "\\") {
            s += e[i++] + e[i++];
            continue;
          }
          if (e[i] === ")") {
            if (f--, f === 0) {
              i++;
              break;
            }
          } else if (e[i] === "(" && (f++, e[i + 1] !== "?")) {
            l(`Capturing groups are not allowed at ${i}`), o = true;
            break;
          }
          s += e[i++];
        }
        if (o) continue;
        if (f) {
          l(`Unbalanced pattern at ${n}`);
          continue;
        }
        if (!s) {
          l(`Missing pattern at ${n}`);
          continue;
        }
        r.push({ type: "REGEX", index: n, value: s }), n = i;
        continue;
      }
      r.push({ type: "CHAR", index: n, value: e[n++] });
    }
    return r.push({ type: "END", index: n, value: "" }), r;
  }
  a(D, "lexer");
  function F(e, t = {}) {
    let r = D(e);
    t.delimiter ??= "/#?", t.prefixes ??= "./";
    let n = `[^${x(t.delimiter)}]+?`, c = [], l = 0, f = 0, s = "", i = /* @__PURE__ */ new Set(), o = a((u) => {
      if (f < r.length && r[f].type === u) return r[f++].value;
    }, "tryConsume"), h = a(() => o("OTHER_MODIFIER") ?? o("ASTERISK"), "tryConsumeModifier"), p = a((u) => {
      let d = o(u);
      if (d !== void 0) return d;
      let { type: g, index: y } = r[f];
      throw new TypeError(`Unexpected ${g} at ${y}, expected ${u}`);
    }, "mustConsume"), A = a(() => {
      let u = "", d;
      for (; d = o("CHAR") ?? o("ESCAPED_CHAR"); ) u += d;
      return u;
    }, "consumeText"), xe = a((u) => u, "DefaultEncodePart"), N = t.encodePart || xe, H = "", $ = a((u) => {
      H += u;
    }, "appendToPendingFixedValue"), M = a(() => {
      H.length && (c.push(new P(3, "", "", N(H), "", 3)), H = "");
    }, "maybeAddPartFromPendingFixedValue"), X = a((u, d, g, y, Z) => {
      let m = 3;
      switch (Z) {
        case "?":
          m = 1;
          break;
        case "*":
          m = 0;
          break;
        case "+":
          m = 2;
          break;
      }
      if (!d && !g && m === 3) {
        $(u);
        return;
      }
      if (M(), !d && !g) {
        if (!u) return;
        c.push(new P(3, "", "", N(u), "", m));
        return;
      }
      let S;
      g ? g === "*" ? S = v : S = g : S = n;
      let k = 2;
      S === n ? (k = 1, S = "") : S === v && (k = 0, S = "");
      let E;
      if (d ? E = d : g && (E = l++), i.has(E)) throw new TypeError(`Duplicate name '${E}'.`);
      i.add(E), c.push(new P(k, E, N(u), S, N(y), m));
    }, "addPart");
    for (; f < r.length; ) {
      let u = o("CHAR"), d = o("NAME"), g = o("REGEX");
      if (!d && !g && (g = o("ASTERISK")), d || g) {
        let m = u ?? "";
        t.prefixes.indexOf(m) === -1 && ($(m), m = ""), M();
        let S = h();
        X(m, d, g, "", S);
        continue;
      }
      let y = u ?? o("ESCAPED_CHAR");
      if (y) {
        $(y);
        continue;
      }
      if (o("OPEN")) {
        let m = A(), S = o("NAME"), k = o("REGEX");
        !S && !k && (k = o("ASTERISK"));
        let E = A();
        p("CLOSE");
        let be = h();
        X(m, S, k, E, be);
        continue;
      }
      M(), p("END");
    }
    return c;
  }
  a(F, "parse");
  function x(e) {
    return e.replace(/([.+*?^${}()[\]|/\\])/g, "\\$1");
  }
  a(x, "escapeString");
  function B(e) {
    return e && e.ignoreCase ? "ui" : "u";
  }
  a(B, "flags");
  function q(e, t, r) {
    return W(F(e, r), t, r);
  }
  a(q, "stringToRegexp");
  function T(e) {
    switch (e) {
      case 0:
        return "*";
      case 1:
        return "?";
      case 2:
        return "+";
      case 3:
        return "";
    }
  }
  a(T, "modifierToString");
  function W(e, t, r = {}) {
    r.delimiter ??= "/#?", r.prefixes ??= "./", r.sensitive ??= false, r.strict ??= false, r.end ??= true, r.start ??= true, r.endsWith = "";
    let n = r.start ? "^" : "";
    for (let s of e) {
      if (s.type === 3) {
        s.modifier === 3 ? n += x(s.value) : n += `(?:${x(s.value)})${T(s.modifier)}`;
        continue;
      }
      t && t.push(s.name);
      let i = `[^${x(r.delimiter)}]+?`, o = s.value;
      if (s.type === 1 ? o = i : s.type === 0 && (o = v), !s.prefix.length && !s.suffix.length) {
        s.modifier === 3 || s.modifier === 1 ? n += `(${o})${T(s.modifier)}` : n += `((?:${o})${T(s.modifier)})`;
        continue;
      }
      if (s.modifier === 3 || s.modifier === 1) {
        n += `(?:${x(s.prefix)}(${o})${x(s.suffix)})`, n += T(s.modifier);
        continue;
      }
      n += `(?:${x(s.prefix)}`, n += `((?:${o})(?:`, n += x(s.suffix), n += x(s.prefix), n += `(?:${o}))*)${x(s.suffix)})`, s.modifier === 0 && (n += "?");
    }
    let c = `[${x(r.endsWith)}]|$`, l = `[${x(r.delimiter)}]`;
    if (r.end) return r.strict || (n += `${l}?`), r.endsWith.length ? n += `(?=${c})` : n += "$", new RegExp(n, B(r));
    r.strict || (n += `(?:${l}(?=${c}))?`);
    let f = false;
    if (e.length) {
      let s = e[e.length - 1];
      s.type === 3 && s.modifier === 3 && (f = r.delimiter.indexOf(s) > -1);
    }
    return f || (n += `(?=${l}|${c})`), new RegExp(n, B(r));
  }
  a(W, "partsToRegexp");
  var b = { delimiter: "", prefixes: "", sensitive: true, strict: true };
  var J = { delimiter: ".", prefixes: "", sensitive: true, strict: true };
  var Q = { delimiter: "/", prefixes: "/", sensitive: true, strict: true };
  function ee(e, t) {
    return e.length ? e[0] === "/" ? true : !t || e.length < 2 ? false : (e[0] == "\\" || e[0] == "{") && e[1] == "/" : false;
  }
  a(ee, "isAbsolutePathname");
  function te(e, t) {
    return e.startsWith(t) ? e.substring(t.length, e.length) : e;
  }
  a(te, "maybeStripPrefix");
  function ke(e, t) {
    return e.endsWith(t) ? e.substr(0, e.length - t.length) : e;
  }
  a(ke, "maybeStripSuffix");
  function _(e) {
    return !e || e.length < 2 ? false : e[0] === "[" || (e[0] === "\\" || e[0] === "{") && e[1] === "[";
  }
  a(_, "treatAsIPv6Hostname");
  var re = ["ftp", "file", "http", "https", "ws", "wss"];
  function U(e) {
    if (!e) return true;
    for (let t of re) if (e.test(t)) return true;
    return false;
  }
  a(U, "isSpecialScheme");
  function ne(e, t) {
    if (e = te(e, "#"), t || e === "") return e;
    let r = new URL("https://example.com");
    return r.hash = e, r.hash ? r.hash.substring(1, r.hash.length) : "";
  }
  a(ne, "canonicalizeHash");
  function se(e, t) {
    if (e = te(e, "?"), t || e === "") return e;
    let r = new URL("https://example.com");
    return r.search = e, r.search ? r.search.substring(1, r.search.length) : "";
  }
  a(se, "canonicalizeSearch");
  function ie(e, t) {
    return t || e === "" ? e : _(e) ? K(e) : j(e);
  }
  a(ie, "canonicalizeHostname");
  function ae(e, t) {
    if (t || e === "") return e;
    let r = new URL("https://example.com");
    return r.password = e, r.password;
  }
  a(ae, "canonicalizePassword");
  function oe(e, t) {
    if (t || e === "") return e;
    let r = new URL("https://example.com");
    return r.username = e, r.username;
  }
  a(oe, "canonicalizeUsername");
  function ce(e, t, r) {
    if (r || e === "") return e;
    if (t && !re.includes(t)) return new URL(`${t}:${e}`).pathname;
    let n = e[0] == "/";
    return e = new URL(n ? e : "/-" + e, "https://example.com").pathname, n || (e = e.substring(2, e.length)), e;
  }
  a(ce, "canonicalizePathname");
  function le(e, t, r) {
    return z(t) === e && (e = ""), r || e === "" ? e : G(e);
  }
  a(le, "canonicalizePort");
  function fe(e, t) {
    return e = ke(e, ":"), t || e === "" ? e : w(e);
  }
  a(fe, "canonicalizeProtocol");
  function z(e) {
    switch (e) {
      case "ws":
      case "http":
        return "80";
      case "wws":
      case "https":
        return "443";
      case "ftp":
        return "21";
      default:
        return "";
    }
  }
  a(z, "defaultPortForProtocol");
  function w(e) {
    if (e === "") return e;
    if (/^[-+.A-Za-z0-9]*$/.test(e)) return e.toLowerCase();
    throw new TypeError(`Invalid protocol '${e}'.`);
  }
  a(w, "protocolEncodeCallback");
  function he(e) {
    if (e === "") return e;
    let t = new URL("https://example.com");
    return t.username = e, t.username;
  }
  a(he, "usernameEncodeCallback");
  function ue(e) {
    if (e === "") return e;
    let t = new URL("https://example.com");
    return t.password = e, t.password;
  }
  a(ue, "passwordEncodeCallback");
  function j(e) {
    if (e === "") return e;
    if (/[\t\n\r #%/:<>?@[\]^\\|]/g.test(e)) throw new TypeError(`Invalid hostname '${e}'`);
    let t = new URL("https://example.com");
    return t.hostname = e, t.hostname;
  }
  a(j, "hostnameEncodeCallback");
  function K(e) {
    if (e === "") return e;
    if (/[^0-9a-fA-F[\]:]/g.test(e)) throw new TypeError(`Invalid IPv6 hostname '${e}'`);
    return e.toLowerCase();
  }
  a(K, "ipv6HostnameEncodeCallback");
  function G(e) {
    if (e === "" || /^[0-9]*$/.test(e) && parseInt(e) <= 65535) return e;
    throw new TypeError(`Invalid port '${e}'.`);
  }
  a(G, "portEncodeCallback");
  function de(e) {
    if (e === "") return e;
    let t = new URL("https://example.com");
    return t.pathname = e[0] !== "/" ? "/-" + e : e, e[0] !== "/" ? t.pathname.substring(2, t.pathname.length) : t.pathname;
  }
  a(de, "standardURLPathnameEncodeCallback");
  function pe(e) {
    return e === "" ? e : new URL(`data:${e}`).pathname;
  }
  a(pe, "pathURLPathnameEncodeCallback");
  function ge(e) {
    if (e === "") return e;
    let t = new URL("https://example.com");
    return t.search = e, t.search.substring(1, t.search.length);
  }
  a(ge, "searchEncodeCallback");
  function me(e) {
    if (e === "") return e;
    let t = new URL("https://example.com");
    return t.hash = e, t.hash.substring(1, t.hash.length);
  }
  a(me, "hashEncodeCallback");
  var _i, _n, _t, _e, _s, _l, _o, _d, _p, _g, _C_instances, r_fn, R_fn, b_fn, u_fn, m_fn, a_fn, P_fn, E_fn, S_fn, O_fn, k_fn, x_fn, h_fn, f_fn, T_fn, A_fn, y_fn, w_fn, c_fn, C_fn, _a;
  var C = (_a = class {
    constructor(t) {
      __privateAdd(this, _C_instances);
      __privateAdd(this, _i);
      __privateAdd(this, _n, []);
      __privateAdd(this, _t, {});
      __privateAdd(this, _e, 0);
      __privateAdd(this, _s, 1);
      __privateAdd(this, _l, 0);
      __privateAdd(this, _o, 0);
      __privateAdd(this, _d, 0);
      __privateAdd(this, _p, 0);
      __privateAdd(this, _g, false);
      __privateSet(this, _i, t);
    }
    get result() {
      return __privateGet(this, _t);
    }
    parse() {
      for (__privateSet(this, _n, D(__privateGet(this, _i), true)); __privateGet(this, _e) < __privateGet(this, _n).length; __privateSet(this, _e, __privateGet(this, _e) + __privateGet(this, _s))) {
        if (__privateSet(this, _s, 1), __privateGet(this, _n)[__privateGet(this, _e)].type === "END") {
          if (__privateGet(this, _o) === 0) {
            __privateMethod(this, _C_instances, b_fn).call(this), __privateMethod(this, _C_instances, f_fn).call(this) ? __privateMethod(this, _C_instances, r_fn).call(this, 9, 1) : __privateMethod(this, _C_instances, h_fn).call(this) ? __privateMethod(this, _C_instances, r_fn).call(this, 8, 1) : __privateMethod(this, _C_instances, r_fn).call(this, 7, 0);
            continue;
          } else if (__privateGet(this, _o) === 2) {
            __privateMethod(this, _C_instances, u_fn).call(this, 5);
            continue;
          }
          __privateMethod(this, _C_instances, r_fn).call(this, 10, 0);
          break;
        }
        if (__privateGet(this, _d) > 0) if (__privateMethod(this, _C_instances, A_fn).call(this)) __privateSet(this, _d, __privateGet(this, _d) - 1);
        else continue;
        if (__privateMethod(this, _C_instances, T_fn).call(this)) {
          __privateSet(this, _d, __privateGet(this, _d) + 1);
          continue;
        }
        switch (__privateGet(this, _o)) {
          case 0:
            __privateMethod(this, _C_instances, P_fn).call(this) && __privateMethod(this, _C_instances, u_fn).call(this, 1);
            break;
          case 1:
            if (__privateMethod(this, _C_instances, P_fn).call(this)) {
              __privateMethod(this, _C_instances, C_fn).call(this);
              let t = 7, r = 1;
              __privateMethod(this, _C_instances, E_fn).call(this) ? (t = 2, r = 3) : __privateGet(this, _g) && (t = 2), __privateMethod(this, _C_instances, r_fn).call(this, t, r);
            }
            break;
          case 2:
            __privateMethod(this, _C_instances, S_fn).call(this) ? __privateMethod(this, _C_instances, u_fn).call(this, 3) : (__privateMethod(this, _C_instances, x_fn).call(this) || __privateMethod(this, _C_instances, h_fn).call(this) || __privateMethod(this, _C_instances, f_fn).call(this)) && __privateMethod(this, _C_instances, u_fn).call(this, 5);
            break;
          case 3:
            __privateMethod(this, _C_instances, O_fn).call(this) ? __privateMethod(this, _C_instances, r_fn).call(this, 4, 1) : __privateMethod(this, _C_instances, S_fn).call(this) && __privateMethod(this, _C_instances, r_fn).call(this, 5, 1);
            break;
          case 4:
            __privateMethod(this, _C_instances, S_fn).call(this) && __privateMethod(this, _C_instances, r_fn).call(this, 5, 1);
            break;
          case 5:
            __privateMethod(this, _C_instances, y_fn).call(this) ? __privateSet(this, _p, __privateGet(this, _p) + 1) : __privateMethod(this, _C_instances, w_fn).call(this) && __privateSet(this, _p, __privateGet(this, _p) - 1), __privateMethod(this, _C_instances, k_fn).call(this) && !__privateGet(this, _p) ? __privateMethod(this, _C_instances, r_fn).call(this, 6, 1) : __privateMethod(this, _C_instances, x_fn).call(this) ? __privateMethod(this, _C_instances, r_fn).call(this, 7, 0) : __privateMethod(this, _C_instances, h_fn).call(this) ? __privateMethod(this, _C_instances, r_fn).call(this, 8, 1) : __privateMethod(this, _C_instances, f_fn).call(this) && __privateMethod(this, _C_instances, r_fn).call(this, 9, 1);
            break;
          case 6:
            __privateMethod(this, _C_instances, x_fn).call(this) ? __privateMethod(this, _C_instances, r_fn).call(this, 7, 0) : __privateMethod(this, _C_instances, h_fn).call(this) ? __privateMethod(this, _C_instances, r_fn).call(this, 8, 1) : __privateMethod(this, _C_instances, f_fn).call(this) && __privateMethod(this, _C_instances, r_fn).call(this, 9, 1);
            break;
          case 7:
            __privateMethod(this, _C_instances, h_fn).call(this) ? __privateMethod(this, _C_instances, r_fn).call(this, 8, 1) : __privateMethod(this, _C_instances, f_fn).call(this) && __privateMethod(this, _C_instances, r_fn).call(this, 9, 1);
            break;
          case 8:
            __privateMethod(this, _C_instances, f_fn).call(this) && __privateMethod(this, _C_instances, r_fn).call(this, 9, 1);
            break;
          case 9:
            break;
          case 10:
            break;
        }
      }
      __privateGet(this, _t).hostname !== void 0 && __privateGet(this, _t).port === void 0 && (__privateGet(this, _t).port = "");
    }
  }, _i = new WeakMap(), _n = new WeakMap(), _t = new WeakMap(), _e = new WeakMap(), _s = new WeakMap(), _l = new WeakMap(), _o = new WeakMap(), _d = new WeakMap(), _p = new WeakMap(), _g = new WeakMap(), _C_instances = new WeakSet(), r_fn = function(t, r) {
    switch (__privateGet(this, _o)) {
      case 0:
        break;
      case 1:
        __privateGet(this, _t).protocol = __privateMethod(this, _C_instances, c_fn).call(this);
        break;
      case 2:
        break;
      case 3:
        __privateGet(this, _t).username = __privateMethod(this, _C_instances, c_fn).call(this);
        break;
      case 4:
        __privateGet(this, _t).password = __privateMethod(this, _C_instances, c_fn).call(this);
        break;
      case 5:
        __privateGet(this, _t).hostname = __privateMethod(this, _C_instances, c_fn).call(this);
        break;
      case 6:
        __privateGet(this, _t).port = __privateMethod(this, _C_instances, c_fn).call(this);
        break;
      case 7:
        __privateGet(this, _t).pathname = __privateMethod(this, _C_instances, c_fn).call(this);
        break;
      case 8:
        __privateGet(this, _t).search = __privateMethod(this, _C_instances, c_fn).call(this);
        break;
      case 9:
        __privateGet(this, _t).hash = __privateMethod(this, _C_instances, c_fn).call(this);
        break;
      case 10:
        break;
    }
    __privateGet(this, _o) !== 0 && t !== 10 && ([1, 2, 3, 4].includes(__privateGet(this, _o)) && [6, 7, 8, 9].includes(t) && (__privateGet(this, _t).hostname ??= ""), [1, 2, 3, 4, 5, 6].includes(__privateGet(this, _o)) && [8, 9].includes(t) && (__privateGet(this, _t).pathname ??= __privateGet(this, _g) ? "/" : ""), [1, 2, 3, 4, 5, 6, 7].includes(__privateGet(this, _o)) && t === 9 && (__privateGet(this, _t).search ??= "")), __privateMethod(this, _C_instances, R_fn).call(this, t, r);
  }, R_fn = function(t, r) {
    __privateSet(this, _o, t), __privateSet(this, _l, __privateGet(this, _e) + r), __privateSet(this, _e, __privateGet(this, _e) + r), __privateSet(this, _s, 0);
  }, b_fn = function() {
    __privateSet(this, _e, __privateGet(this, _l)), __privateSet(this, _s, 0);
  }, u_fn = function(t) {
    __privateMethod(this, _C_instances, b_fn).call(this), __privateSet(this, _o, t);
  }, m_fn = function(t) {
    return t < 0 && (t = __privateGet(this, _n).length - t), t < __privateGet(this, _n).length ? __privateGet(this, _n)[t] : __privateGet(this, _n)[__privateGet(this, _n).length - 1];
  }, a_fn = function(t, r) {
    let n = __privateMethod(this, _C_instances, m_fn).call(this, t);
    return n.value === r && (n.type === "CHAR" || n.type === "ESCAPED_CHAR" || n.type === "INVALID_CHAR");
  }, P_fn = function() {
    return __privateMethod(this, _C_instances, a_fn).call(this, __privateGet(this, _e), ":");
  }, E_fn = function() {
    return __privateMethod(this, _C_instances, a_fn).call(this, __privateGet(this, _e) + 1, "/") && __privateMethod(this, _C_instances, a_fn).call(this, __privateGet(this, _e) + 2, "/");
  }, S_fn = function() {
    return __privateMethod(this, _C_instances, a_fn).call(this, __privateGet(this, _e), "@");
  }, O_fn = function() {
    return __privateMethod(this, _C_instances, a_fn).call(this, __privateGet(this, _e), ":");
  }, k_fn = function() {
    return __privateMethod(this, _C_instances, a_fn).call(this, __privateGet(this, _e), ":");
  }, x_fn = function() {
    return __privateMethod(this, _C_instances, a_fn).call(this, __privateGet(this, _e), "/");
  }, h_fn = function() {
    if (__privateMethod(this, _C_instances, a_fn).call(this, __privateGet(this, _e), "?")) return true;
    if (__privateGet(this, _n)[__privateGet(this, _e)].value !== "?") return false;
    let t = __privateMethod(this, _C_instances, m_fn).call(this, __privateGet(this, _e) - 1);
    return t.type !== "NAME" && t.type !== "REGEX" && t.type !== "CLOSE" && t.type !== "ASTERISK";
  }, f_fn = function() {
    return __privateMethod(this, _C_instances, a_fn).call(this, __privateGet(this, _e), "#");
  }, T_fn = function() {
    return __privateGet(this, _n)[__privateGet(this, _e)].type == "OPEN";
  }, A_fn = function() {
    return __privateGet(this, _n)[__privateGet(this, _e)].type == "CLOSE";
  }, y_fn = function() {
    return __privateMethod(this, _C_instances, a_fn).call(this, __privateGet(this, _e), "[");
  }, w_fn = function() {
    return __privateMethod(this, _C_instances, a_fn).call(this, __privateGet(this, _e), "]");
  }, c_fn = function() {
    let t = __privateGet(this, _n)[__privateGet(this, _e)], r = __privateMethod(this, _C_instances, m_fn).call(this, __privateGet(this, _l)).index;
    return __privateGet(this, _i).substring(r, t.index);
  }, C_fn = function() {
    let t = {};
    Object.assign(t, b), t.encodePart = w;
    let r = q(__privateMethod(this, _C_instances, c_fn).call(this), void 0, t);
    __privateSet(this, _g, U(r));
  }, _a);
  a(C, "Parser");
  var V = ["protocol", "username", "password", "hostname", "port", "pathname", "search", "hash"];
  var O = "*";
  function Se(e, t) {
    if (typeof e != "string") throw new TypeError("parameter 1 is not of type 'string'.");
    let r = new URL(e, t);
    return { protocol: r.protocol.substring(0, r.protocol.length - 1), username: r.username, password: r.password, hostname: r.hostname, port: r.port, pathname: r.pathname, search: r.search !== "" ? r.search.substring(1, r.search.length) : void 0, hash: r.hash !== "" ? r.hash.substring(1, r.hash.length) : void 0 };
  }
  a(Se, "extractValues");
  function R(e, t) {
    return t ? I(e) : e;
  }
  a(R, "processBaseURLString");
  function L(e, t, r) {
    let n;
    if (typeof t.baseURL == "string") try {
      n = new URL(t.baseURL), t.protocol === void 0 && (e.protocol = R(n.protocol.substring(0, n.protocol.length - 1), r)), !r && t.protocol === void 0 && t.hostname === void 0 && t.port === void 0 && t.username === void 0 && (e.username = R(n.username, r)), !r && t.protocol === void 0 && t.hostname === void 0 && t.port === void 0 && t.username === void 0 && t.password === void 0 && (e.password = R(n.password, r)), t.protocol === void 0 && t.hostname === void 0 && (e.hostname = R(n.hostname, r)), t.protocol === void 0 && t.hostname === void 0 && t.port === void 0 && (e.port = R(n.port, r)), t.protocol === void 0 && t.hostname === void 0 && t.port === void 0 && t.pathname === void 0 && (e.pathname = R(n.pathname, r)), t.protocol === void 0 && t.hostname === void 0 && t.port === void 0 && t.pathname === void 0 && t.search === void 0 && (e.search = R(n.search.substring(1, n.search.length), r)), t.protocol === void 0 && t.hostname === void 0 && t.port === void 0 && t.pathname === void 0 && t.search === void 0 && t.hash === void 0 && (e.hash = R(n.hash.substring(1, n.hash.length), r));
    } catch {
      throw new TypeError(`invalid baseURL '${t.baseURL}'.`);
    }
    if (typeof t.protocol == "string" && (e.protocol = fe(t.protocol, r)), typeof t.username == "string" && (e.username = oe(t.username, r)), typeof t.password == "string" && (e.password = ae(t.password, r)), typeof t.hostname == "string" && (e.hostname = ie(t.hostname, r)), typeof t.port == "string" && (e.port = le(t.port, e.protocol, r)), typeof t.pathname == "string") {
      if (e.pathname = t.pathname, n && !ee(e.pathname, r)) {
        let c = n.pathname.lastIndexOf("/");
        c >= 0 && (e.pathname = R(n.pathname.substring(0, c + 1), r) + e.pathname);
      }
      e.pathname = ce(e.pathname, e.protocol, r);
    }
    return typeof t.search == "string" && (e.search = se(t.search, r)), typeof t.hash == "string" && (e.hash = ne(t.hash, r)), e;
  }
  a(L, "applyInit");
  function I(e) {
    return e.replace(/([+*?:{}()\\])/g, "\\$1");
  }
  a(I, "escapePatternString");
  function Te(e) {
    return e.replace(/([.+*?^${}()[\]|/\\])/g, "\\$1");
  }
  a(Te, "escapeRegexpString");
  function Ae(e, t) {
    t.delimiter ??= "/#?", t.prefixes ??= "./", t.sensitive ??= false, t.strict ??= false, t.end ??= true, t.start ??= true, t.endsWith = "";
    let r = ".*", n = `[^${Te(t.delimiter)}]+?`, c = /[$_\u200C\u200D\p{ID_Continue}]/u, l = "";
    for (let f = 0; f < e.length; ++f) {
      let s = e[f];
      if (s.type === 3) {
        if (s.modifier === 3) {
          l += I(s.value);
          continue;
        }
        l += `{${I(s.value)}}${T(s.modifier)}`;
        continue;
      }
      let i = s.hasCustomName(), o = !!s.suffix.length || !!s.prefix.length && (s.prefix.length !== 1 || !t.prefixes.includes(s.prefix)), h = f > 0 ? e[f - 1] : null, p = f < e.length - 1 ? e[f + 1] : null;
      if (!o && i && s.type === 1 && s.modifier === 3 && p && !p.prefix.length && !p.suffix.length) if (p.type === 3) {
        let A = p.value.length > 0 ? p.value[0] : "";
        o = c.test(A);
      } else o = !p.hasCustomName();
      if (!o && !s.prefix.length && h && h.type === 3) {
        let A = h.value[h.value.length - 1];
        o = t.prefixes.includes(A);
      }
      o && (l += "{"), l += I(s.prefix), i && (l += `:${s.name}`), s.type === 2 ? l += `(${s.value})` : s.type === 1 ? i || (l += `(${n})`) : s.type === 0 && (!i && (!h || h.type === 3 || h.modifier !== 3 || o || s.prefix !== "") ? l += "*" : l += `(${r})`), s.type === 1 && i && s.suffix.length && c.test(s.suffix[0]) && (l += "\\"), l += I(s.suffix), o && (l += "}"), s.modifier !== 3 && (l += T(s.modifier));
    }
    return l;
  }
  a(Ae, "partsToPattern");
  var _i2, _n2, _t2, _e2, _s2, _l2, _a2;
  var Y = (_a2 = class {
    constructor(t = {}, r, n) {
      __privateAdd(this, _i2);
      __privateAdd(this, _n2, {});
      __privateAdd(this, _t2, {});
      __privateAdd(this, _e2, {});
      __privateAdd(this, _s2, {});
      __privateAdd(this, _l2, false);
      try {
        let c;
        if (typeof r == "string" ? c = r : n = r, typeof t == "string") {
          let i = new C(t);
          if (i.parse(), t = i.result, c === void 0 && typeof t.protocol != "string") throw new TypeError("A base URL must be provided for a relative constructor string.");
          t.baseURL = c;
        } else {
          if (!t || typeof t != "object") throw new TypeError("parameter 1 is not of type 'string' and cannot convert to dictionary.");
          if (c) throw new TypeError("parameter 1 is not of type 'string'.");
        }
        typeof n > "u" && (n = { ignoreCase: false });
        let l = { ignoreCase: n.ignoreCase === true }, f = { pathname: O, protocol: O, username: O, password: O, hostname: O, port: O, search: O, hash: O };
        __privateSet(this, _i2, L(f, t, true)), z(__privateGet(this, _i2).protocol) === __privateGet(this, _i2).port && (__privateGet(this, _i2).port = "");
        let s;
        for (s of V) {
          if (!(s in __privateGet(this, _i2))) continue;
          let i = {}, o = __privateGet(this, _i2)[s];
          switch (__privateGet(this, _t2)[s] = [], s) {
            case "protocol":
              Object.assign(i, b), i.encodePart = w;
              break;
            case "username":
              Object.assign(i, b), i.encodePart = he;
              break;
            case "password":
              Object.assign(i, b), i.encodePart = ue;
              break;
            case "hostname":
              Object.assign(i, J), _(o) ? i.encodePart = K : i.encodePart = j;
              break;
            case "port":
              Object.assign(i, b), i.encodePart = G;
              break;
            case "pathname":
              U(__privateGet(this, _n2).protocol) ? (Object.assign(i, Q, l), i.encodePart = de) : (Object.assign(i, b, l), i.encodePart = pe);
              break;
            case "search":
              Object.assign(i, b, l), i.encodePart = ge;
              break;
            case "hash":
              Object.assign(i, b, l), i.encodePart = me;
              break;
          }
          try {
            __privateGet(this, _s2)[s] = F(o, i), __privateGet(this, _n2)[s] = W(__privateGet(this, _s2)[s], __privateGet(this, _t2)[s], i), __privateGet(this, _e2)[s] = Ae(__privateGet(this, _s2)[s], i), __privateSet(this, _l2, __privateGet(this, _l2) || __privateGet(this, _s2)[s].some((h) => h.type === 2));
          } catch {
            throw new TypeError(`invalid ${s} pattern '${__privateGet(this, _i2)[s]}'.`);
          }
        }
      } catch (c) {
        throw new TypeError(`Failed to construct 'URLPattern': ${c.message}`);
      }
    }
    get [Symbol.toStringTag]() {
      return "URLPattern";
    }
    test(t = {}, r) {
      let n = { pathname: "", protocol: "", username: "", password: "", hostname: "", port: "", search: "", hash: "" };
      if (typeof t != "string" && r) throw new TypeError("parameter 1 is not of type 'string'.");
      if (typeof t > "u") return false;
      try {
        typeof t == "object" ? n = L(n, t, false) : n = L(n, Se(t, r), false);
      } catch {
        return false;
      }
      let c;
      for (c of V) if (!__privateGet(this, _n2)[c].exec(n[c])) return false;
      return true;
    }
    exec(t = {}, r) {
      let n = { pathname: "", protocol: "", username: "", password: "", hostname: "", port: "", search: "", hash: "" };
      if (typeof t != "string" && r) throw new TypeError("parameter 1 is not of type 'string'.");
      if (typeof t > "u") return;
      try {
        typeof t == "object" ? n = L(n, t, false) : n = L(n, Se(t, r), false);
      } catch {
        return null;
      }
      let c = {};
      r ? c.inputs = [t, r] : c.inputs = [t];
      let l;
      for (l of V) {
        let f = __privateGet(this, _n2)[l].exec(n[l]);
        if (!f) return null;
        let s = {};
        for (let [i, o] of __privateGet(this, _t2)[l].entries()) if (typeof o == "string" || typeof o == "number") {
          let h = f[i + 1];
          s[o] = h;
        }
        c[l] = { input: n[l] ?? "", groups: s };
      }
      return c;
    }
    static compareComponent(t, r, n) {
      let c = a((i, o) => {
        for (let h of ["type", "modifier", "prefix", "value", "suffix"]) {
          if (i[h] < o[h]) return -1;
          if (i[h] === o[h]) continue;
          return 1;
        }
        return 0;
      }, "comparePart"), l = new P(3, "", "", "", "", 3), f = new P(0, "", "", "", "", 3), s = a((i, o) => {
        let h = 0;
        for (; h < Math.min(i.length, o.length); ++h) {
          let p = c(i[h], o[h]);
          if (p) return p;
        }
        return i.length === o.length ? 0 : c(i[h] ?? l, o[h] ?? l);
      }, "comparePartList");
      return !__privateGet(r, _e2)[t] && !__privateGet(n, _e2)[t] ? 0 : __privateGet(r, _e2)[t] && !__privateGet(n, _e2)[t] ? s(__privateGet(r, _s2)[t], [f]) : !__privateGet(r, _e2)[t] && __privateGet(n, _e2)[t] ? s([f], __privateGet(n, _s2)[t]) : s(__privateGet(r, _s2)[t], __privateGet(n, _s2)[t]);
    }
    get protocol() {
      return __privateGet(this, _e2).protocol;
    }
    get username() {
      return __privateGet(this, _e2).username;
    }
    get password() {
      return __privateGet(this, _e2).password;
    }
    get hostname() {
      return __privateGet(this, _e2).hostname;
    }
    get port() {
      return __privateGet(this, _e2).port;
    }
    get pathname() {
      return __privateGet(this, _e2).pathname;
    }
    get search() {
      return __privateGet(this, _e2).search;
    }
    get hash() {
      return __privateGet(this, _e2).hash;
    }
    get hasRegExpGroups() {
      return __privateGet(this, _l2);
    }
  }, _i2 = new WeakMap(), _n2 = new WeakMap(), _t2 = new WeakMap(), _e2 = new WeakMap(), _s2 = new WeakMap(), _l2 = new WeakMap(), _a2);
  a(Y, "URLPattern");

  // ../node_modules/urlpattern-polyfill/index.js
  if (!globalThis.URLPattern) {
    globalThis.URLPattern = Y;
  }

  // src/config-feature.js
  var _bundledConfig, _args;
  var ConfigFeature = class {
    /**
     * @param {string} name
     * @param {import('./content-scope-features.js').LoadArgs} args
     */
    constructor(name, args) {
      /** @type {import('./utils.js').RemoteConfig | undefined} */
      __privateAdd(this, _bundledConfig);
      /** @type {string} */
      __publicField(this, "name");
      /**
       * @type {{
       *   debug?: boolean,
       *   platform: import('./utils.js').Platform,
       *   desktopModeEnabled?: boolean,
       *   forcedZoomEnabled?: boolean,
       *   featureSettings?: Record<string, unknown>,
       *   assets?: import('./content-feature.js').AssetConfig | undefined,
       *   site: import('./content-feature.js').Site,
       *   messagingConfig?: import('@duckduckgo/messaging').MessagingConfig,
       *   messagingContextName: string,
       *   currentCohorts?: Array<{feature: string, cohort: string, subfeature: string}>,
       *   trackerData?: import('./features/tracker-protection/tracker-resolver.js').TrackerData,
       * } | null}
       */
      __privateAdd(this, _args);
      this.name = name;
      const { bundledConfig, site, platform } = args;
      __privateSet(this, _bundledConfig, bundledConfig);
      __privateSet(this, _args, args);
      if (__privateGet(this, _bundledConfig) && __privateGet(this, _args)) {
        const enabledFeatures = computeEnabledFeatures(__privateGet(this, _bundledConfig), site.domain, platform);
        __privateGet(this, _args).featureSettings = parseFeatureSettings(__privateGet(this, _bundledConfig), enabledFeatures);
      }
    }
    /**
     * Call this when the top URL has changed, to recompute the site object.
     * This is used to update the path matching for urlPattern.
     */
    recomputeSiteObject() {
      if (__privateGet(this, _args)) {
        __privateGet(this, _args).site = computeLimitedSiteObject();
      }
    }
    get args() {
      return __privateGet(this, _args);
    }
    set args(args) {
      __privateSet(this, _args, args);
    }
    get featureSettings() {
      return __privateGet(this, _args)?.featureSettings;
    }
    /**
     * Getter for injectName, will be overridden by subclasses (namely ContentFeature)
     * @returns {string | undefined}
     */
    get injectName() {
      return void 0;
    }
    /**
     * Given a config key, interpret the value as a list of conditionals objects, and return the elements that match the current page
     * Consider in your feature using patchSettings instead as per `getFeatureSetting`.
     * @param {string} featureKeyName
     * @return {ConditionalSettingEntry[]}
     * @protected
     */
    matchConditionalFeatureSetting(featureKeyName) {
      const conditionalChanges = this._getFeatureSettings()?.[featureKeyName] || [];
      return conditionalChanges.filter((rule) => {
        let condition = rule.condition;
        if (condition === void 0 && rule.domain !== void 0) {
          condition = this._domainToConditonBlocks(rule.domain);
        }
        if (condition === void 0) return true;
        return this._matchConditionalBlockOrArray(condition);
      });
    }
    /**
     * Takes a list of domains and returns a list of condition blocks
     * @param {string|string[]} domain
     * @returns {ConditionBlock[]}
     */
    _domainToConditonBlocks(domain) {
      if (Array.isArray(domain)) {
        return domain.map((domain2) => ({ domain: domain2 }));
      } else {
        return [{ domain }];
      }
    }
    /**
     * Takes multiple conditional blocks and returns true if any apply.
     * @param {ConditionBlockOrArray} conditionBlock
     * @returns {boolean}
     */
    _matchConditionalBlockOrArray(conditionBlock) {
      if (Array.isArray(conditionBlock)) {
        return conditionBlock.some((block) => this._matchConditionalBlock(block));
      }
      return this._matchConditionalBlock(conditionBlock);
    }
    /**
     * Takes a conditional block and returns true if it applies.
     * All conditions must be met to return true.
     * @param {ConditionBlock} conditionBlock
     * @returns {boolean}
     */
    _matchConditionalBlock(conditionBlock) {
      const conditionChecks = {
        domain: this._matchDomainConditional,
        context: this._matchContextConditional,
        urlPattern: this._matchUrlPatternConditional,
        experiment: this._matchExperimentConditional,
        minSupportedVersion: this._matchMinSupportedVersion,
        maxSupportedVersion: this._matchMaxSupportedVersion,
        injectName: this._matchInjectNameConditional,
        internal: this._matchInternalConditional,
        preview: this._matchPreviewConditional
      };
      for (const key in conditionBlock) {
        if (!conditionChecks[key]) {
          return false;
        } else if (!conditionChecks[key].call(this, conditionBlock)) {
          return false;
        }
      }
      return true;
    }
    /**
     * Takes a condition block and returns true if the current experiment matches the experimentName and cohort.
     * Expects:
     * ```json
     * {
     *   "experiment": {
     *      "experimentName": "experimentName",
     *      "cohort": "cohort-name"
     *    }
     * }
     * ```
     * Where featureName "contentScopeExperiments" has a subfeature "experimentName" and cohort "cohort-name"
     * @param {ConditionBlock} conditionBlock
     * @returns {boolean}
     */
    _matchExperimentConditional(conditionBlock) {
      if (!conditionBlock.experiment) return false;
      const experiment = conditionBlock.experiment;
      if (!experiment.experimentName || !experiment.cohort) return false;
      const currentCohorts = this.args?.currentCohorts;
      if (!currentCohorts) return false;
      return currentCohorts.some((cohort) => {
        return cohort.feature === "contentScopeExperiments" && cohort.subfeature === experiment.experimentName && cohort.cohort === experiment.cohort;
      });
    }
    /**
     * Takes a condition block and returns true if the current context matches the context.
     * @param {ConditionBlock} conditionBlock
     * @returns {boolean}
     */
    _matchContextConditional(conditionBlock) {
      if (!conditionBlock.context) return false;
      const isFrame = window.self !== window.top;
      if (conditionBlock.context.frame && isFrame) {
        return true;
      }
      if (conditionBlock.context.top && !isFrame) {
        return true;
      }
      return false;
    }
    /**
     * Takes a condtion block and returns true if the current url matches the urlPattern.
     * @param {ConditionBlock} conditionBlock
     * @returns {boolean}
     */
    _matchUrlPatternConditional(conditionBlock) {
      const url = this.args?.site.url;
      if (!url) return false;
      if (typeof conditionBlock.urlPattern === "string") {
        return new Y(conditionBlock.urlPattern, url).test(url);
      }
      const pattern = new Y(conditionBlock.urlPattern);
      return pattern.test(url);
    }
    /**
     * Takes a condition block and returns true if the current domain matches the domain.
     * @param {ConditionBlock} conditionBlock
     * @returns {boolean}
     */
    _matchDomainConditional(conditionBlock) {
      if (!conditionBlock.domain) return false;
      const domain = this.args?.site.domain;
      if (!domain) return false;
      if (Array.isArray(conditionBlock.domain)) {
        return false;
      }
      return matchHostname(domain, conditionBlock.domain);
    }
    /**
     * Takes a condition block and returns true if the current inject name matches the injectName.
     * @param {ConditionBlock} conditionBlock
     * @returns {boolean}
     */
    _matchInjectNameConditional(conditionBlock) {
      if (!conditionBlock.injectName) return false;
      const currentInjectName = this.injectName;
      if (!currentInjectName) return false;
      return conditionBlock.injectName === currentInjectName;
    }
    /**
     * Takes a condition block and returns true if the internal state matches the condition.
     * @param {ConditionBlock} conditionBlock
     * @returns {boolean}
     */
    _matchInternalConditional(conditionBlock) {
      if (conditionBlock.internal === void 0) return false;
      const isInternal = __privateGet(this, _args)?.platform?.internal;
      if (isInternal === void 0) return false;
      return Boolean(conditionBlock.internal) === Boolean(isInternal);
    }
    /**
     * Takes a condition block and returns true if the preview state matches the condition.
     * @param {ConditionBlock} conditionBlock
     * @returns {boolean}
     */
    _matchPreviewConditional(conditionBlock) {
      if (conditionBlock.preview === void 0) return false;
      const isPreview = __privateGet(this, _args)?.platform?.preview;
      if (isPreview === void 0) return false;
      return Boolean(conditionBlock.preview) === Boolean(isPreview);
    }
    /**
     * Takes a condition block and returns true if the platform version satisfies the `minSupportedFeature`
     * @param {ConditionBlock} conditionBlock
     * @returns {boolean}
     */
    _matchMinSupportedVersion(conditionBlock) {
      if (!conditionBlock.minSupportedVersion) return false;
      return isSupportedVersion(conditionBlock.minSupportedVersion, __privateGet(this, _args)?.platform?.version);
    }
    /**
     * Takes a condition block and returns true if the platform version satisfies the `maxSupportedFeature`
     * @param {ConditionBlock} conditionBlock
     * @returns {boolean}
     */
    _matchMaxSupportedVersion(conditionBlock) {
      if (!conditionBlock.maxSupportedVersion) return false;
      return isMaxSupportedVersion(conditionBlock.maxSupportedVersion, __privateGet(this, _args)?.platform?.version);
    }
    /**
     * Check if a state value is enabled for the current platform.
     * @param {import('./utils.js').FeatureState | undefined} state
     * @returns {boolean}
     */
    _isStateEnabled(state) {
      return isStateEnabled(state, __privateGet(this, _args)?.platform);
    }
    /**
     * Return the settings object for a feature
     * @param {string} [featureName] - The name of the feature to get the settings for; defaults to the name of the feature
     * @returns {any}
     */
    _getFeatureSettings(featureName) {
      const camelFeatureName = featureName || camelcase(this.name);
      return this.featureSettings?.[camelFeatureName];
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
     * State values can be: 'enabled', 'disabled', 'internal', or 'preview'.
     * 'internal' and 'preview' are enabled based on platform flags.
     * This also supports domain overrides as per `getFeatureSetting`.
     * @param {string} featureKeyName
     * @param {import('./utils.js').FeatureState} [defaultState]
     * @param {string} [featureName]
     * @returns {boolean}
     */
    getFeatureSettingEnabled(featureKeyName, defaultState, featureName) {
      const result = this.getFeatureSetting(featureKeyName, featureName) || defaultState;
      if (typeof result === "object") {
        return this._isStateEnabled(result.state);
      }
      return this._isStateEnabled(result);
    }
    /**
     * Return a specific setting from the feature settings
     * If the "settings" key within the config has a "conditionalChanges" key, it will be used to override the settings.
     * This uses JSONPatch to apply the patches to settings before getting the setting value.
     * For example.com getFeatureSettings('val') will return 1:
     * ```json
     *  {
     *      "settings": {
     *         "conditionalChanges": [
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
     * Additionally we support urlPattern for more complex matching.
     * For example.com getFeatureSettings('val') will return 1:
     * ```json
     * {
     *    "settings": {
     *       "conditionalChanges": [
     *          {
     *            "condition": {
     *                "urlPattern": "https://example.com/*",
     *            },
     *            "patchSettings": [
     *                { "op": "replace", "path": "/val", "value": 1 }
     *            ]
     *          }
     *       ]
     *   }
     * }
     * ```
     * We also support multiple conditions:
     * ```json
     * {
     *    "settings": {
     *       "conditionalChanges": [
     *          {
     *            "condition": [
     *                {
     *                    "urlPattern": "https://example.com/*",
     *                },
     *                {
     *                    "urlPattern": "https://other.com/path/something",
     *                },
     *            ],
     *            "patchSettings": [
     *                { "op": "replace", "path": "/val", "value": 1 }
     *            ]
     *          }
     *       ]
     *   }
     * }
     * ```
     *
     * For boolean states you should consider using getFeatureSettingEnabled.
     * @param {string} featureKeyName
     * @param {string} [featureName]
     * @returns {any}
     */
    getFeatureSetting(featureKeyName, featureName) {
      let result = this._getFeatureSettings(featureName);
      if (featureKeyName in ["domains", "conditionalChanges"]) {
        throw new Error(`${featureKeyName} is a reserved feature setting key name`);
      }
      let conditionalMatches = [];
      if (result?.conditionalChanges) {
        conditionalMatches = this.matchConditionalFeatureSetting("conditionalChanges");
      } else {
        conditionalMatches = this.matchConditionalFeatureSetting("domains");
      }
      for (const match of conditionalMatches) {
        if (match.patchSettings === void 0) {
          continue;
        }
        try {
          result = immutableJSONPatch(result, match.patchSettings);
        } catch (e) {
          console.error("Error applying patch settings", e);
        }
      }
      return result?.[featureKeyName];
    }
    /**
     * @returns {import('./utils.js').RemoteConfig | undefined}
     **/
    get bundledConfig() {
      return __privateGet(this, _bundledConfig);
    }
  };
  _bundledConfig = new WeakMap();
  _args = new WeakMap();

  // src/content-feature.js
  function createDeferred() {
    let res;
    const promise = new Promise((resolve) => {
      res = resolve;
    });
    return { promise, resolve: res };
  }
  var CallFeatureMethodError = class extends Error {
    /**
     * @param {string} message
     */
    constructor(message) {
      super(message);
      Object.setPrototypeOf(this, new.target.prototype);
      this.name = new.target.name;
    }
  };
  var _messaging, _isDebugFlagSet, _importConfig, _features, _ready;
  var ContentFeature = class extends ConfigFeature {
    /**
     * @param {string} featureName
     * @param {*} importConfig
     * @param {Partial<FeatureMap>} features
     * @param {*} args
     */
    constructor(featureName, importConfig, features, args) {
      super(featureName, args);
      /** @type {import('../../messaging').Messaging | undefined} */
      // eslint-disable-next-line no-unused-private-class-members
      __privateAdd(this, _messaging);
      /** @type {boolean} */
      __privateAdd(this, _isDebugFlagSet, false);
      /**
       * Set this to true if you wish to listen to top level URL changes for config matching.
       * @type {boolean}
       */
      __publicField(this, "listenForUrlChanges", false);
      /**
       * Set this to true if you wish to get update calls (legacy).
       * @type {boolean}
       */
      __publicField(this, "listenForUpdateChanges", false);
      /**
       * Set this to true if you wish to receive configuration updates from initial ping responses (Android only).
       * @type {boolean}
       */
      __publicField(this, "listenForConfigUpdates", false);
      /** @type {ImportMeta} */
      __privateAdd(this, _importConfig);
      /**
       * @type {Partial<FeatureMap>}
       */
      __privateAdd(this, _features);
      /** @type {ReturnType<typeof createDeferred>} */
      __privateAdd(this, _ready);
      /**
       * @template {string} K
       * @typedef {K[] & {__brand: 'exposeMethods'}} ExposeMethods
       */
      /**
       * Methods that are exposed for inter-feature communication.
       *
       * Use `this._declareExposeMethods([...names])` to declare which methods are exposed.
       *
       * @type {ExposeMethods<string> | undefined}
       */
      __publicField(this, "_exposedMethods");
      this.setArgs(this.args);
      this.monitor = new PerformanceMonitor();
      __privateSet(this, _features, features);
      __privateSet(this, _importConfig, importConfig);
      __privateSet(this, _ready, createDeferred());
    }
    get isDebug() {
      return this.args?.debug || false;
    }
    get shouldLog() {
      return this.isDebug;
    }
    /**
     * Returns a promise that resolves when the feature has been initialised with `init`.
     *
     * @returns {Promise<ReadyStatus>}
     */
    get _ready() {
      return __privateGet(this, _ready).promise;
    }
    /**
     * Logging utility for this feature (Stolen some inspo from DuckPlayer logger, will unify in the future)
     */
    get log() {
      const shouldLog = this.shouldLog;
      const prefix = `${this.name.padEnd(20, " ")} |`;
      return {
        // These are getters to have the call site be the reported line number.
        get info() {
          if (!shouldLog) {
            return () => {
            };
          }
          return consoleLog.bind(console, prefix);
        },
        get warn() {
          if (!shouldLog) {
            return () => {
            };
          }
          return consoleWarn.bind(console, prefix);
        },
        get error() {
          if (!shouldLog) {
            return () => {
            };
          }
          return consoleError.bind(console, prefix);
        }
      };
    }
    get desktopModeEnabled() {
      return this.args?.desktopModeEnabled || false;
    }
    get forcedZoomEnabled() {
      return this.args?.forcedZoomEnabled || false;
    }
    /**
     * @param {import('./utils').Platform} platform
     */
    set platform(platform) {
      this._platform = platform;
    }
    get platform() {
      return this._platform;
    }
    /**
     * @type {AssetConfig | undefined}
     */
    get assetConfig() {
      return this.args?.assets;
    }
    /**
     * @returns {import('./trackers.js').TrackerNode | {}}
     **/
    get trackerLookup() {
      return __privateGet(this, _importConfig).trackerLookup || {};
    }
    /**
     * @returns {ImportMeta['injectName']}
     */
    get injectName() {
      return __privateGet(this, _importConfig).injectName;
    }
    /**
     * @returns {boolean}
     */
    get documentOriginIsTracker() {
      return isTrackerOrigin(this.trackerLookup);
    }
    /**
     * Declares which methods may be called on the feature instance from other features.
     *
     * @template {keyof typeof this} K
     * @param {K[]} methods
     * @returns {ExposeMethods<K>}
     */
    _declareExposedMethods(methods) {
      for (const method of methods) {
        if (typeof this[method] !== "function") {
          throw new Error(`'${method.toString()}' is not a method of feature '${this.name}'`);
        }
      }
      return methods;
    }
    /**
     * Run an exposed method of another feature.
     *
     * Waits for the feature to be initialized before calling the method.
     *
     * `args` are the arguments to pass to the feature method.
     *
     * NOTE: be aware of potential circular dependencies. Check that the feature
     * you are calling is not calling you back.
     *
     * @template {keyof FeatureMap} FeatureName
     * @template {FeatureMap[FeatureName]} Feature
     * @template {keyof Feature & (Feature['_exposedMethods'] extends ExposeMethods<infer K> ? K : never)} MethodName
     * @param {FeatureName} featureName
     * @param {MethodName} methodName
     * @param {Feature[MethodName] extends (...args: infer Args) => any ? Args : never} args
     * @returns {Promise<ReturnType<Feature[MethodName]> | CallFeatureMethodError>}
     */
    async callFeatureMethod(featureName, methodName, ...args) {
      const feature = __privateGet(this, _features)[featureName];
      if (!feature) return new CallFeatureMethodError(`Feature not found: '${featureName}'`);
      if (!(feature._exposedMethods !== void 0 && feature._exposedMethods.some((mn) => mn === methodName)))
        return new CallFeatureMethodError(`'${methodName}' is not exposed by feature '${featureName}'`);
      const method = (
        /** @type {Feature} */
        feature[methodName]
      );
      if (!method) return new CallFeatureMethodError(`'${methodName}' not found in feature '${featureName}'`);
      if (!(method instanceof Function))
        return new CallFeatureMethodError(`'${methodName}' is not a function in feature '${featureName}'`);
      const isReady = await feature._ready;
      if (isReady.status === "skipped") {
        return new CallFeatureMethodError(`Initialisation of feature '${featureName}' was skipped: ${isReady.reason}`);
      }
      if (isReady.status === "error") {
        return new CallFeatureMethodError(`Initialisation of feature '${featureName}' failed: ${isReady.error}`);
      }
      return method.call(feature, ...args);
    }
    /**
     * @deprecated as we should make this internal to the class and not used externally
     * @return {MessagingContext}
     */
    _createMessagingContext() {
      if (!this.args) throw new Error("messaging requires args to be set");
      return new MessagingContext({
        context: this.args.messagingContextName,
        env: this.isDebug ? "development" : "production",
        featureName: this.name
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
      let messagingConfig = this.args?.messagingConfig;
      if (!messagingConfig) {
        if (this.platform?.name !== "extension") throw new Error("Only extension messaging supported, all others should be passed in");
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
     * @param {unknown} defaultValue - The default value to use if the config setting is not set
     * @returns The value of the config setting or the default value
     */
    getFeatureAttr(attrName, defaultValue) {
      const configSetting = this.getFeatureSetting(attrName);
      return processAttr(configSetting, defaultValue);
    }
    /**
     * @param {unknown} [_args]
     */
    init(_args2) {
    }
    /**
     * @param {object} args
     */
    async callInit(args) {
      const mark = this.monitor.mark(this.name + "CallInit");
      try {
        this.setArgs(args);
        await this.init(this.args);
        __privateGet(this, _ready).resolve({ status: "ready" });
      } catch (error) {
        __privateGet(this, _ready).resolve({ status: "error", error: String(error) });
        throw error;
      } finally {
        mark.end();
        this.measure();
      }
    }
    /**
     * Mark this feature as skipped (not initialized).
     *
     * This allows inter-feature communication to fail fast instead of hanging indefinitely.
     *
     * @param {string} reason - The reason the feature was skipped
     */
    markFeatureAsSkipped(reason) {
      __privateGet(this, _ready).resolve({ status: "skipped", reason });
    }
    /**
     * @param {any} args
     */
    setArgs(args) {
      this.args = args;
      this.platform = args.platform;
    }
    /**
     * @param {unknown} [_args]
     */
    load(_args2) {
    }
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
    callLoad() {
      const mark = this.monitor.mark(this.name + "CallLoad");
      this.load(this.args);
      mark.end();
    }
    measure() {
      if (this.isDebug) {
        this.monitor.measureAll();
      }
    }
    /**
     * @deprecated - use messaging instead.
     */
    update() {
    }
    /**
     * Called when user preferences are merged from initial ping response. (Android only)
     * Override this method in your feature to handle user preference updates.
     * This only happens once during initialization when the platform responds with user-specific settings.
     * @param {object} _updatedConfig - The configuration with merged user preferences
     */
    onUserPreferencesMerged(_updatedConfig) {
    }
    /**
     * Register a flag that will be added to page breakage reports
     */
    addDebugFlag() {
      if (__privateGet(this, _isDebugFlagSet)) return;
      __privateSet(this, _isDebugFlagSet, true);
      try {
        this.messaging?.notify("addDebugFlag", {
          flag: this.name
        });
      } catch (_e3) {
      }
    }
    /**
     * Define a property descriptor with debug flags.
     * Mainly used for defining new properties. For overriding existing properties, consider using wrapProperty(), wrapMethod() and wrapConstructor().
     * @param {object} object - object whose property we are wrapping (most commonly a prototype, e.g. globalThis.BatteryManager.prototype)
     * @param {string | symbol} propertyName
     * @param {import('./wrapper-utils').StrictPropertyDescriptor} descriptor - requires all descriptor options to be defined because we can't validate correctness based on TS types
     */
    defineProperty(object, propertyName, descriptor) {
      const addDebugFlag = this.addDebugFlag.bind(this);
      const wrapWithDebugFlag = (fn) => {
        const wrapper = new Proxy2(fn, {
          apply(_2, thisArg, argumentsList) {
            addDebugFlag();
            return Reflect2.apply(fn, thisArg, argumentsList);
          }
        });
        return (
          /** @type {F} */
          wrapToString(wrapper, fn)
        );
      };
      if ("value" in descriptor && typeof descriptor.value === "function") {
        descriptor.value = wrapWithDebugFlag(descriptor.value);
      }
      if ("get" in descriptor && typeof descriptor.get === "function") {
        descriptor.get = wrapWithDebugFlag(descriptor.get);
      }
      if ("set" in descriptor && typeof descriptor.set === "function") {
        descriptor.set = wrapWithDebugFlag(descriptor.set);
      }
      return defineProperty(object, propertyName, descriptor);
    }
    /**
     * Wrap a `get`/`set` or `value` property descriptor. Only for data properties. For methods, use wrapMethod(). For constructors, use wrapConstructor().
     * @param {object} object - object whose property we are wrapping (most commonly a prototype, e.g. globalThis.Screen.prototype)
     * @param {string} propertyName
     * @param {Partial<PropertyDescriptor>} descriptor
     * @returns {PropertyDescriptor|undefined} original property descriptor, or undefined if it's not found
     */
    wrapProperty(object, propertyName, descriptor) {
      return wrapProperty(object, propertyName, descriptor, this.defineProperty.bind(this));
    }
    /**
     * Wrap a method descriptor. Only for function properties. For data properties, use wrapProperty(). For constructors, use wrapConstructor().
     * @param {object} object - object whose property we are wrapping (most commonly a prototype, e.g. globalThis.Bluetooth.prototype)
     * @param {string} propertyName
     * @param {(originalFn: any, ...args: any[]) => any } wrapperFn - wrapper function receives the original function as the first argument
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
      return shimInterface(interfaceName, ImplClass, options, this.defineProperty.bind(this), this.injectName);
    }
    /**
     * Define a missing standard property on a global (prototype) object. Only for data properties.
     * For constructors, use shimInterface().
     * Most of the time, you'd want to call shimInterface() first to shim the class itself (MediaSession), and then shimProperty() for the global singleton instance (Navigator.prototype.mediaSession).
     * @template {object} Base
     * @template {keyof Base & string} K
     * @param {Base} instanceHost - object whose property we are shimming (most commonly a prototype object, e.g. Navigator.prototype)
     * @param {K} instanceProp - name of the property to shim (e.g. 'mediaSession')
     * @param {Base[K]} implInstance - instance to use as the shim (e.g. new MyMediaSession())
     * @param {boolean} [readOnly] - whether the property should be read-only (default: false)
     */
    shimProperty(instanceHost, instanceProp, implInstance, readOnly = false) {
      return shimProperty(instanceHost, instanceProp, implInstance, readOnly, this.defineProperty.bind(this), this.injectName);
    }
  };
  _messaging = new WeakMap();
  _isDebugFlagSet = new WeakMap();
  _importConfig = new WeakMap();
  _features = new WeakMap();
  _ready = new WeakMap();

  // src/features/api-manipulation.js
  var ApiManipulation = class extends ContentFeature {
    constructor() {
      super(...arguments);
      __publicField(this, "listenForUrlChanges", true);
    }
    init() {
      const apiChanges = this.getFeatureSetting("apiChanges");
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
    urlChanged() {
      this.init();
    }
    /**
     * Checks if the config API change is valid.
     * @param {any} change
     * @returns {change is APIChange}
     */
    checkIsValidAPIChange(change) {
      if (typeof change !== "object") {
        return false;
      }
      if (change.type === "remove") {
        return true;
      }
      if (change.type === "descriptor") {
        if ("enumerable" in change && typeof change.enumerable !== "boolean") {
          return false;
        }
        if ("configurable" in change && typeof change.configurable !== "boolean") {
          return false;
        }
        if ("define" in change && typeof change.define !== "boolean") {
          return false;
        }
        const hasGetterValue = typeof change.getterValue !== "undefined";
        const hasSetterValue = typeof change.setterValue !== "undefined";
        const hasValue = typeof change.value !== "undefined";
        const isAccessorShape = hasGetterValue || hasSetterValue;
        const isValueShape = hasValue;
        return isAccessorShape !== isValueShape;
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
     * @property {import('../utils.js').ConfigSetting} [setterValue] - The function invoked when the property is assigned. Used alongside (or instead of) getterValue to override accessor-style properties such as event handlers (e.g., `MediaDevices.prototype.ondevicechange`).
     * @property {import('../utils.js').ConfigSetting} [value] - The value assigned to a value descriptor, including methods.
     * @property {boolean} [enumerable] - Whether the property is enumerable.
     * @property {boolean} [configurable] - Whether the property is configurable.
     * @property {boolean} [define] - When true, define a new own property if the key is absent from `api` and its entire prototype chain. When false (default), skip changes for properties that do not exist at all; override own properties via `wrapProperty`; override inherited properties by shadow-defining an own property on `api`.
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
      if (change.type === "remove") {
        this.removeApiMethod(obj, key);
      } else if (change.type === "descriptor") {
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
      } catch (e) {
      }
    }
    /**
     * Wraps a property with descriptor.
     * @param {object} api
     * @param {string} key
     * @param {APIChange} change
     */
    wrapApiDescriptor(api, key, change) {
      const getterValue = change.getterValue;
      const setterValue = change.setterValue;
      const value = change.value;
      const descriptorKind = getterValue !== void 0 || setterValue !== void 0 ? "getter" : value !== void 0 ? "value" : void 0;
      const configSetting = descriptorKind === "getter" ? getterValue : value;
      if (!descriptorKind || descriptorKind === "value" && configSetting === void 0) {
        return;
      }
      const descriptor = this.createApiDescriptor(descriptorKind, configSetting, change);
      const origDescriptor = this.findPropertyDescriptor(api, key);
      if (!origDescriptor) {
        if (change.define === true) {
          this.defineProperty(api, key, this.createDefineDescriptor(descriptor, descriptorKind));
        }
        return;
      }
      if (descriptorKind === "value") {
        const valueDescriptor = (
          /** @type {{ value?: any }} */
          descriptor
        );
        if (typeof valueDescriptor.value === "function" && typeof origDescriptor.value === "function") {
          valueDescriptor.value = this.maskMethodReplacement(valueDescriptor.value, origDescriptor.value);
        }
      } else if (descriptorKind === "getter") {
        const accessorDescriptor = (
          /** @type {{ get?: () => any, set?: (v: any) => void }} */
          descriptor
        );
        if (typeof accessorDescriptor.set === "function" && typeof origDescriptor.set === "function") {
          accessorDescriptor.set = /** @type {(v: any) => void} */
          this.maskMethodReplacement(accessorDescriptor.set, origDescriptor.set);
        }
      }
      if (hasOwnProperty.call(api, key)) {
        this.wrapProperty(api, key, descriptor);
      } else {
        const merged = mergePropertyDescriptors(origDescriptor, descriptor);
        if (merged) {
          this.defineProperty(api, key, merged);
        }
      }
    }
    /**
     * Returns the property descriptor for `key` on `obj` or an ancestor in its prototype chain.
     * @param {object} obj
     * @param {string} key
     * @returns {PropertyDescriptor | undefined}
     */
    findPropertyDescriptor(obj, key) {
      let current = obj;
      while (current) {
        const descriptor = getOwnPropertyDescriptor(current, key);
        if (descriptor) {
          return descriptor;
        }
        current = Object.getPrototypeOf(current);
      }
      return void 0;
    }
    /**
     * Wraps a config-supplied function so its observable identity (`toString`,
     * `toString.toString`, `name`, `length`) mirrors the original DOM method it is
     * replacing. The call itself still executes the configured replacement.
     *
     * Note: `processAttr` may return a shared function (e.g. `functionMap.noop`),
     * so we always create a fresh wrapper before redefining `name`/`length` to
     * avoid mutating module-level singletons.
     *
     * @param {Function} replacementFn - configured replacement to invoke
     * @param {Function} origFn - original DOM method we are masking against
     * @returns {Function}
     */
    maskMethodReplacement(replacementFn, origFn) {
      const wrapper = function() {
        return ReflectApply(replacementFn, this, arguments);
      };
      objectDefineProperty(wrapper, "name", { value: origFn.name, configurable: true });
      objectDefineProperty(wrapper, "length", { value: origFn.length, configurable: true });
      return wrapToString(wrapper, origFn);
    }
    /**
     * @param {'getter' | 'value'} descriptorKind
     * @param {import('../utils.js').ConfigSetting | import('../utils.js').ConfigSetting[] | undefined} configSetting
     * @param {APIChange} change
     * @returns {Partial<import('../wrapper-utils.js').StrictPropertyDescriptor>}
     */
    createApiDescriptor(descriptorKind, configSetting, change) {
      let descriptor;
      if (descriptorKind === "value") {
        const valueSetting = (
          /** @type {import('../utils.js').ConfigSetting | import('../utils.js').ConfigSetting[]} */
          configSetting
        );
        descriptor = { value: processAttr(valueSetting, void 0) };
      } else {
        descriptor = {};
        if (configSetting !== void 0) {
          const getterSetting = configSetting;
          descriptor.get = () => processAttr(getterSetting, void 0);
        }
        if (change.setterValue !== void 0) {
          const setterSetting = (
            /** @type {import('../utils.js').ConfigSetting} */
            change.setterValue
          );
          descriptor.set = function setter(v2) {
            const fn = processAttr(setterSetting, void 0);
            if (typeof fn === "function") {
              ReflectApply(fn, this, [v2]);
            }
          };
        }
      }
      if ("enumerable" in change) {
        descriptor.enumerable = change.enumerable;
      }
      if ("configurable" in change) {
        descriptor.configurable = change.configurable;
      }
      return (
        /** @type {Partial<import('../wrapper-utils.js').StrictPropertyDescriptor>} */
        descriptor
      );
    }
    /**
     * @param {Partial<import('../wrapper-utils.js').StrictPropertyDescriptor>} descriptor
     * @param {'getter' | 'value'} descriptorKind
     * @returns {import('../wrapper-utils.js').StrictPropertyDescriptor}
     */
    createDefineDescriptor(descriptor, descriptorKind) {
      if (descriptorKind === "value") {
        const valueDescriptor = (
          /** @type {{ value: any, enumerable?: boolean, configurable?: boolean }} */
          descriptor
        );
        return {
          value: valueDescriptor.value,
          writable: true,
          enumerable: typeof valueDescriptor.enumerable !== "boolean" ? true : valueDescriptor.enumerable,
          configurable: typeof valueDescriptor.configurable !== "boolean" ? true : valueDescriptor.configurable
        };
      }
      const getterDescriptor = (
        /** @type {{ get?: () => any, set?: (v: any) => void, enumerable?: boolean, configurable?: boolean }} */
        descriptor
      );
      const result = {
        enumerable: typeof getterDescriptor.enumerable !== "boolean" ? true : getterDescriptor.enumerable,
        configurable: typeof getterDescriptor.configurable !== "boolean" ? true : getterDescriptor.configurable
      };
      if (typeof getterDescriptor.get === "function") {
        result.get = getterDescriptor.get;
      }
      if (typeof getterDescriptor.set === "function") {
        result.set = getterDescriptor.set;
      }
      return result;
    }
    /**
     * Looks up a global object from a scope, e.g. 'Navigator.prototype'.
     * @param {string} scope the scope of the object to get to.
     * @returns {[object, string]|null} the object at the scope.
     */
    getGlobalObject(scope) {
      const parts = scope.split(".");
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
  };

  // src/features/web-compat.js
  function windowSizingFix() {
    if (window.outerHeight !== 0 && window.outerWidth !== 0) {
      return;
    }
    window.outerHeight = window.innerHeight;
    window.outerWidth = window.innerWidth;
  }
  var MSG_WEB_SHARE = "webShare";
  var MSG_PERMISSIONS_QUERY = "permissionsQuery";
  var MSG_SCREEN_LOCK = "screenLock";
  var MSG_SCREEN_UNLOCK = "screenUnlock";
  var MSG_DEVICE_ENUMERATION = "deviceEnumeration";
  function canShare(data) {
    if (typeof data !== "object") return false;
    data = Object.assign({}, data);
    for (const key of ["url", "title", "text", "files"]) {
      if (data[key] === void 0 || data[key] === null) {
        delete data[key];
      }
    }
    if (!("url" in data) && !("title" in data) && !("text" in data)) return false;
    if ("files" in data) {
      if (!(Array.isArray(data.files) || data.files instanceof FileList)) return false;
      if (data.files.length > 0) return false;
    }
    if ("title" in data && typeof data.title !== "string") return false;
    if ("text" in data && typeof data.text !== "string") return false;
    if ("url" in data) {
      if (typeof data.url !== "string") return false;
      try {
        const url = new URL2(data.url, location.href);
        if (url.protocol !== "http:" && url.protocol !== "https:") return false;
      } catch (err) {
        return false;
      }
    }
    return true;
  }
  var _hasChangeListener;
  var PermissionStatus = class extends EventTarget {
    constructor(name, state) {
      super();
      __privateAdd(this, _hasChangeListener, false);
      this.name = name;
      this.state = state;
      this.onchange = null;
    }
    get hasChangeListener() {
      return __privateGet(this, _hasChangeListener);
    }
    addEventListener(type, callback, options) {
      if (type === "change") __privateSet(this, _hasChangeListener, true);
      super.addEventListener(type, callback, options);
    }
  };
  _hasChangeListener = new WeakMap();
  function cleanShareData(data) {
    const dataToSend = {};
    for (const key of ["title", "text", "url"]) {
      if (key in data) dataToSend[key] = data[key];
    }
    if ("url" in data) {
      dataToSend.url = new URL2(data.url, location.href).href;
    }
    if ("url" in dataToSend && "text" in dataToSend) {
      dataToSend.text = `${dataToSend.text} ${dataToSend.url}`;
      delete dataToSend.url;
    }
    if (!("url" in dataToSend) && !("text" in dataToSend)) {
      dataToSend.text = "";
    }
    return dataToSend;
  }
  var _activeShareRequest, _activeScreenLockRequest, _webNotifications, _permissionPollingTimer;
  var WebCompat = class extends ContentFeature {
    constructor() {
      super(...arguments);
      /** @type {Promise<{failure?: {name: string, message: string}}> | null} */
      __privateAdd(this, _activeShareRequest, null);
      /** @type {Promise<{failure?: {name: string, message: string}}> | null} */
      __privateAdd(this, _activeScreenLockRequest, null);
      /** @type {Map<string, object>} */
      __privateAdd(this, _webNotifications, /* @__PURE__ */ new Map());
      /** @type {ReturnType<typeof setTimeout> | undefined} */
      __privateAdd(this, _permissionPollingTimer);
      // Opt in to receive configuration updates from initial ping responses
      __publicField(this, "listenForConfigUpdates", true);
    }
    init() {
      if (this.getFeatureSettingEnabled("windowSizing")) {
        windowSizingFix();
      }
      if (this.getFeatureSettingEnabled("navigatorCredentials")) {
        this.navigatorCredentialsFix();
      }
      if (this.getFeatureSettingEnabled("safariObject")) {
        this.safariObjectFix();
      }
      if (this.getFeatureSettingEnabled("messageHandlers")) {
        this.messageHandlersFix();
      }
      if (this.getFeatureSettingEnabled("notification")) {
        this.notificationFix();
      }
      if (this.getFeatureSettingEnabled("webNotifications")) {
        this.webNotificationsFix();
      }
      if (this.getFeatureSettingEnabled("permissions")) {
        const settings = this.getFeatureSetting("permissions");
        this.permissionsFix(settings);
      }
      if (this.getFeatureSettingEnabled("cleanIframeValue")) {
        this.cleanIframeValue();
      }
      if (this.getFeatureSettingEnabled("mediaSession")) {
        this.mediaSessionFix();
      }
      if (this.getFeatureSettingEnabled("presentation")) {
        this.presentationFix();
      }
      if (this.getFeatureSettingEnabled("webShare")) {
        this.shimWebShare();
      }
      if (this.getFeatureSettingEnabled("screenLock")) {
        this.screenLockFix();
      }
      if (this.getFeatureSettingEnabled("modifyLocalStorage")) {
        this.modifyLocalStorage();
      }
      if (this.getFeatureSettingEnabled("modifyCookies")) {
        this.modifyCookies();
      }
      if (this.getFeatureSettingEnabled("enumerateDevices")) {
        this.deviceEnumerationFix();
      }
      if (this.getFeatureSettingEnabled("viewportWidthLegacy", "disabled")) {
        this.viewportWidthFix();
      }
    }
    /**
     * Handle user preference updates when merged during initialization.
     * Re-applies viewport fixes if viewport configuration has changed.
     * Used in the injectName='android-adsjs' instead of 'viewportWidthLegacy' from init.
     * @param {object} _updatedConfig - The configuration with merged user preferences
     */
    onUserPreferencesMerged(_updatedConfig) {
      if (this.getFeatureSettingEnabled("viewportWidth")) {
        this.viewportWidthFix();
      }
    }
    /**
     * Shim Web Share API in Android WebView
     * Note: Always verify API existence before shimming
     */
    shimWebShare() {
      if (typeof navigator.canShare === "function" || typeof navigator.share === "function") return;
      this.defineProperty(Navigator.prototype, "canShare", {
        configurable: true,
        enumerable: true,
        writable: true,
        value: canShare
      });
      this.defineProperty(Navigator.prototype, "share", {
        configurable: true,
        enumerable: true,
        writable: true,
        value: async (data) => {
          if (!canShare(data)) return Promise.reject(new TypeError("Invalid share data"));
          if (__privateGet(this, _activeShareRequest)) {
            return Promise.reject(new DOMException("Share already in progress", "InvalidStateError"));
          }
          if (!navigator.userActivation.isActive) {
            return Promise.reject(new DOMException("Share must be initiated by a user gesture", "InvalidStateError"));
          }
          const dataToSend = cleanShareData(data);
          __privateSet(this, _activeShareRequest, this.request(MSG_WEB_SHARE, dataToSend));
          let resp;
          try {
            resp = await __privateGet(this, _activeShareRequest);
          } catch (err) {
            throw new DOMException(err.message, "DataError");
          } finally {
            __privateSet(this, _activeShareRequest, null);
          }
          if (resp.failure) {
            switch (resp.failure.name) {
              case "AbortError":
              case "NotAllowedError":
              case "DataError":
                throw new DOMException(resp.failure.message, resp.failure.name);
              default:
                throw new DOMException(resp.failure.message, "DataError");
            }
          }
        }
      });
    }
    /**
     * Notification fix for adding missing API for Android WebView.
     */
    notificationFix() {
      if (window.Notification) {
        return;
      }
      const NotificationConstructor = function Notification() {
        throw new TypeError("Failed to construct 'Notification': Illegal constructor");
      };
      const wrappedNotification = wrapToString(
        NotificationConstructor,
        NotificationConstructor,
        "function Notification() { [native code] }"
      );
      this.defineProperty(window, "Notification", {
        value: wrappedNotification,
        writable: true,
        configurable: true,
        enumerable: false
      });
      this.defineProperty(window.Notification, "permission", {
        value: "denied",
        writable: false,
        configurable: true,
        enumerable: true
      });
      this.defineProperty(window.Notification, "maxActions", {
        get: () => 2,
        configurable: true,
        enumerable: true
      });
      const requestPermissionFunc = function requestPermission() {
        return Promise.resolve("denied");
      };
      const wrappedRequestPermission = wrapToString(
        requestPermissionFunc,
        requestPermissionFunc,
        "function requestPermission() { [native code] }"
      );
      this.defineProperty(window.Notification, "requestPermission", {
        value: wrappedRequestPermission,
        writable: true,
        configurable: true,
        enumerable: true
      });
    }
    /**
     * Web Notifications polyfill that communicates with native code for permission
     * management and notification display.
     */
    webNotificationsFix() {
      var _id;
      if (!globalThis.isSecureContext) {
        return;
      }
      const feature = this;
      const settings = this.getFeatureSetting("webNotifications") || {};
      const nativeEnabled = settings.nativeEnabled !== false;
      const nativeNotify = nativeEnabled ? (name, data) => feature.notify(name, data) : () => {
      };
      const nativeRequest = nativeEnabled ? (name, data) => feature.request(name, data) : () => Promise.resolve({ permission: "denied" });
      const nativeSubscribe = nativeEnabled ? (name, cb) => feature.subscribe(name, cb) : () => () => {
      };
      let permission = nativeEnabled ? "default" : "denied";
      class NotificationPolyfill {
        /**
         * @param {string} title
         * @param {NotificationOptions} [options]
         */
        constructor(title, options = {}) {
          /** @type {string} */
          __privateAdd(this, _id);
          /** @type {string} */
          __publicField(this, "title");
          /** @type {string} */
          __publicField(this, "body");
          /** @type {string} */
          __publicField(this, "icon");
          /** @type {string} */
          __publicField(this, "tag");
          /** @type {any} */
          __publicField(this, "data");
          // Event handlers
          /** @type {((this: Notification, ev: Event) => any) | null} */
          __publicField(this, "onclick", null);
          /** @type {((this: Notification, ev: Event) => any) | null} */
          __publicField(this, "onclose", null);
          /** @type {((this: Notification, ev: Event) => any) | null} */
          __publicField(this, "onerror", null);
          /** @type {((this: Notification, ev: Event) => any) | null} */
          __publicField(this, "onshow", null);
          __privateSet(this, _id, crypto.randomUUID());
          this.title = String(title);
          this.body = options.body ? String(options.body) : "";
          this.icon = options.icon ? String(options.icon) : "";
          this.tag = options.tag ? String(options.tag) : "";
          this.data = options.data;
          __privateGet(feature, _webNotifications).set(__privateGet(this, _id), this);
          nativeNotify("showNotification", {
            id: __privateGet(this, _id),
            title: this.title,
            body: this.body,
            icon: this.icon,
            tag: this.tag
          });
        }
        /**
         * @returns {'default' | 'denied' | 'granted'}
         */
        static get permission() {
          return permission;
        }
        /**
         * @param {NotificationPermissionCallback} [deprecatedCallback]
         * @returns {Promise<NotificationPermission>}
         */
        static async requestPermission(deprecatedCallback) {
          try {
            const result = await nativeRequest("requestPermission", {});
            const resultPermission = (
              /** @type {NotificationPermission} */
              result?.permission || "denied"
            );
            permission = resultPermission;
            if (deprecatedCallback) {
              deprecatedCallback(resultPermission);
            }
            return resultPermission;
          } catch (e) {
            permission = "denied";
            if (deprecatedCallback) {
              deprecatedCallback("denied");
            }
            return "denied";
          }
        }
        /**
         * @returns {number}
         */
        static get maxActions() {
          return 2;
        }
        close() {
          if (!__privateGet(feature, _webNotifications).has(__privateGet(this, _id))) {
            return;
          }
          nativeNotify("closeNotification", { id: __privateGet(this, _id) });
          __privateGet(feature, _webNotifications).delete(__privateGet(this, _id));
          if (typeof this.onclose === "function") {
            try {
              this.onclose(new Event("close"));
            } catch (e) {
            }
          }
        }
      }
      _id = new WeakMap();
      const wrappedNotification = wrapFunction(NotificationPolyfill, NotificationPolyfill);
      const wrappedRequestPermission = wrapToString(
        NotificationPolyfill.requestPermission.bind(NotificationPolyfill),
        NotificationPolyfill.requestPermission,
        "function requestPermission() { [native code] }"
      );
      nativeSubscribe("notificationEvent", (data) => {
        const notification = __privateGet(this, _webNotifications).get(data.id);
        if (!notification) return;
        const eventName = `on${data.event}`;
        if (typeof notification[eventName] === "function") {
          try {
            notification[eventName](new Event(data.event));
          } catch (e) {
          }
        }
        if (data.event === "close") {
          __privateGet(this, _webNotifications).delete(data.id);
        }
      });
      this.defineProperty(globalThis, "Notification", {
        value: wrappedNotification,
        writable: true,
        configurable: true,
        enumerable: false
      });
      this.defineProperty(globalThis.Notification, "permission", {
        get: () => permission,
        configurable: true,
        enumerable: true
      });
      this.defineProperty(globalThis.Notification, "maxActions", {
        get: () => 2,
        configurable: true,
        enumerable: true
      });
      this.defineProperty(globalThis.Notification, "requestPermission", {
        value: wrappedRequestPermission,
        writable: true,
        configurable: true,
        enumerable: true
      });
    }
    cleanIframeValue() {
      function cleanValueData(val) {
        const clone = Object.assign({}, val);
        const deleteKeys = ["iframeProto", "iframeData", "remap"];
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
          const cleanKey = "bi_wvdp";
          if (body && typeof body === "string" && body.includes(cleanKey)) {
            const parts = body.split("&").map((part) => {
              return part.split("=");
            });
            if (parts.length > 0) {
              parts.forEach((part) => {
                if (part[0] === cleanKey) {
                  const val = JSON.parse(decodeURIComponent(part[1]));
                  part[1] = encodeURIComponent(JSON.stringify(cleanValueData(val)));
                }
              });
              args[0] = parts.map((part) => {
                return part.join("=");
              }).join("&");
            }
          }
          return Reflect.apply(target, thisArg, args);
        }
      });
    }
    /**
     * Handles permission query with native messaging support.
     * @param {Object} query - The permission query object
     * @param {Object} settings - The permission settings
     * @returns {Promise<PermissionStatus|null>} - Returns PermissionStatus if handled, null to fall through
     */
    async handlePermissionQuery(query, settings) {
      if (!query?.name || !settings?.supportedPermissions?.[query.name]?.native) {
        return null;
      }
      try {
        const permSetting = settings.supportedPermissions[query.name];
        const returnName = permSetting.name || query.name;
        const response = await this.messaging.request(MSG_PERMISSIONS_QUERY, query);
        const returnStatus = response.state || "prompt";
        return new PermissionStatus(returnName, returnStatus);
      } catch (err) {
        return null;
      }
    }
    /**
     * Polls native messaging once per second (up to 30s) to detect when a
     * permission was granted/denied by the user, so the PermissionStatus change
     * event can be dispatched.
     *
     * Since there is a performance impact with polling:
     * - Only one polling timer runs at a time.
     * - Ticks are skipped until a change listener is registered.
     * - Chained setTimeout used instead of setInterval, in case the permission
     *   query message response takes longer than one second, since otherwise a
     *   change event could be dispatched twice accidentally.
     *
     * @param {PermissionStatus} status
     * @param {Object} query
     */
    pollForPermissionChange(status, query) {
      if (__privateGet(this, _permissionPollingTimer)) {
        return;
      }
      let remaining = 30;
      const tick = async () => {
        let statusChanged = false;
        if (status.hasChangeListener || typeof status.onchange === "function") {
          try {
            const { state } = await this.messaging.request(MSG_PERMISSIONS_QUERY, query);
            if (state && state !== "prompt") {
              status.state = state;
              status.dispatchEvent(new Event("change"));
              if (typeof status.onchange === "function") {
                try {
                  status.onchange(new Event("change"));
                } catch (e) {
                }
              }
              statusChanged = true;
            }
          } catch {
          }
        }
        if (!statusChanged && remaining-- > 0) {
          __privateSet(this, _permissionPollingTimer, setTimeout(tick, 1e3));
        } else {
          __privateSet(this, _permissionPollingTimer, void 0);
        }
      };
      __privateSet(this, _permissionPollingTimer, setTimeout(tick, 1e3));
    }
    permissionsPresentFix(settings) {
      const originalQuery = window.navigator.permissions.query;
      if (typeof originalQuery !== "function") {
        return;
      }
      window.navigator.permissions.query = new Proxy(originalQuery, {
        apply: async (target, thisArg, args) => {
          this.addDebugFlag();
          const query = args[0];
          if (query?.name && settings?.supportedPermissions?.[query.name]?.native) {
            const result = await this.handlePermissionQuery(query, settings);
            if (result) {
              if (result.state === "prompt") {
                this.pollForPermissionChange(result, query);
              }
              return result;
            }
          }
          return Reflect.apply(target, thisArg, args);
        }
      });
    }
    /**
     * Adds missing permissions API for Android WebView.
     */
    permissionsFix(settings) {
      if (window.navigator.permissions) {
        if (this.getFeatureSettingEnabled("permissionsPresent")) {
          this.permissionsPresentFix(settings);
        }
        return;
      }
      const permissions = {};
      permissions.query = new Proxy(
        async (query) => {
          this.addDebugFlag();
          if (!query) {
            throw new TypeError("Failed to execute 'query' on 'Permissions': 1 argument required, but only 0 present.");
          }
          if (!query.name) {
            throw new TypeError(
              "Failed to execute 'query' on 'Permissions': Failed to read the 'name' property from 'PermissionDescriptor': Required member is undefined."
            );
          }
          if (!settings.supportedPermissions || !(query.name in settings.supportedPermissions)) {
            throw new TypeError(
              `Failed to execute 'query' on 'Permissions': Failed to read the 'name' property from 'PermissionDescriptor': The provided value '${query.name}' is not a valid enum value of type PermissionName.`
            );
          }
          const result = await this.handlePermissionQuery(query, settings);
          if (result) {
            return result;
          }
          const permSetting = settings.supportedPermissions[query.name];
          const returnName = permSetting.name || query.name;
          const returnStatus = settings.permissionResponse || "prompt";
          return Promise.resolve(new PermissionStatus(returnName, returnStatus));
        },
        {
          get(target, name) {
            return Reflect.get(target, name);
          }
        }
      );
      window.navigator.permissions = permissions;
    }
    /**
     * Fixes screen lock/unlock APIs for Android WebView.
     * Uses wrapProperty to match original property descriptors.
     */
    screenLockFix() {
      const validOrientations = [
        "any",
        "natural",
        "landscape",
        "portrait",
        "portrait-primary",
        "portrait-secondary",
        "landscape-primary",
        "landscape-secondary",
        "unsupported"
      ];
      this.wrapProperty(globalThis.ScreenOrientation.prototype, "lock", {
        value: async (requestedOrientation) => {
          if (!requestedOrientation) {
            return Promise.reject(
              new TypeError("Failed to execute 'lock' on 'ScreenOrientation': 1 argument required, but only 0 present.")
            );
          }
          if (!validOrientations.includes(requestedOrientation)) {
            return Promise.reject(
              new TypeError(
                `Failed to execute 'lock' on 'ScreenOrientation': The provided value '${requestedOrientation}' is not a valid enum value of type OrientationLockType.`
              )
            );
          }
          if (__privateGet(this, _activeScreenLockRequest)) {
            return Promise.reject(new DOMException("Screen lock already in progress", "AbortError"));
          }
          __privateSet(this, _activeScreenLockRequest, this.messaging.request(MSG_SCREEN_LOCK, { orientation: requestedOrientation }));
          let resp;
          try {
            resp = await __privateGet(this, _activeScreenLockRequest);
          } catch (err) {
            throw new DOMException(err.message, "DataError");
          } finally {
            __privateSet(this, _activeScreenLockRequest, null);
          }
          if (resp.failure) {
            switch (resp.failure.name) {
              case "TypeError":
                return Promise.reject(new TypeError(resp.failure.message));
              case "InvalidStateError":
                return Promise.reject(new DOMException(resp.failure.message, resp.failure.name));
              default:
                return Promise.reject(new DOMException(resp.failure.message, "DataError"));
            }
          }
          return Promise.resolve();
        }
      });
      this.wrapProperty(globalThis.ScreenOrientation.prototype, "unlock", {
        value: () => {
          void this.messaging.request(MSG_SCREEN_UNLOCK, {});
        }
      });
    }
    /**
     * Add missing navigator.credentials API
     */
    navigatorCredentialsFix() {
      try {
        if ("credentials" in navigator && "get" in navigator.credentials) {
          return;
        }
        const value = {
          get() {
            return Promise.reject(new Error());
          }
        };
        this.defineProperty(Navigator.prototype, "credentials", {
          value,
          configurable: true,
          enumerable: true,
          writable: true
        });
      } catch {
      }
    }
    safariObjectFix() {
      try {
        if (window.safari) {
          return;
        }
        this.defineProperty(window, "safari", {
          value: {},
          writable: true,
          configurable: true,
          enumerable: true
        });
        this.defineProperty(window.safari, "pushNotification", {
          value: {},
          configurable: true,
          enumerable: true
        });
        this.defineProperty(window.safari.pushNotification, "toString", {
          value: () => {
            return "[object SafariRemoteNotification]";
          },
          configurable: true,
          enumerable: true
        });
        class SafariRemoteNotificationPermission {
          constructor() {
            this.deviceToken = null;
            this.permission = "denied";
          }
        }
        this.defineProperty(window.safari.pushNotification, "permission", {
          value: () => {
            return new SafariRemoteNotificationPermission();
          },
          configurable: true,
          enumerable: true
        });
        this.defineProperty(window.safari.pushNotification, "requestPermission", {
          value: (_name, _domain, _options, callback) => {
            if (typeof callback === "function") {
              callback(new SafariRemoteNotificationPermission());
              return;
            }
            const reason = "Invalid 'callback' value passed to safari.pushNotification.requestPermission(). Expected a function.";
            throw new Error(reason);
          },
          configurable: true,
          enumerable: true
        });
      } catch {
      }
    }
    mediaSessionFix() {
      try {
        if (window.navigator.mediaSession && this.injectName !== "integration") {
          return;
        }
        class MyMediaSession {
          constructor() {
            __publicField(this, "metadata", null);
            /** @type {MediaSession['playbackState']} */
            __publicField(this, "playbackState", "none");
          }
          setActionHandler() {
          }
          async setCameraActive() {
          }
          async setMicrophoneActive() {
          }
          setPositionState() {
          }
        }
        this.shimInterface("MediaSession", MyMediaSession, {
          disallowConstructor: true,
          allowConstructorCall: false,
          wrapToString: true
        });
        this.shimProperty(Navigator.prototype, "mediaSession", new MyMediaSession(), true);
        this.shimInterface(
          "MediaMetadata",
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
            wrapToString: true
          }
        );
      } catch {
      }
    }
    presentationFix() {
      try {
        if (window.navigator.presentation && this.injectName !== "integration") {
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
        this.shimInterface("Presentation", MyPresentation, {
          disallowConstructor: true,
          allowConstructorCall: false,
          wrapToString: true
        });
        this.shimInterface(
          // @ts-expect-error Presentation API is still experimental, TS types are missing
          "PresentationAvailability",
          class {
            // class definition is empty because there's no way to get an instance of it anyways
          },
          {
            disallowConstructor: true,
            allowConstructorCall: false,
            wrapToString: true
          }
        );
        this.shimInterface(
          // @ts-expect-error Presentation API is still experimental, TS types are missing
          "PresentationRequest",
          class {
            // class definition is empty because there's no way to get an instance of it anyways
          },
          {
            disallowConstructor: true,
            allowConstructorCall: false,
            wrapToString: true
          }
        );
        this.shimProperty(Navigator.prototype, "presentation", new MyPresentation(), true);
      } catch {
      }
    }
    /**
     * Support for modifying localStorage entries
     */
    modifyLocalStorage() {
      const settings = this.getFeatureSetting("modifyLocalStorage");
      if (!settings || !settings.changes) return;
      settings.changes.forEach((change) => {
        if (change.action === "delete") {
          localStorage.removeItem(change.key);
        }
      });
    }
    /**
     * Support for modifying cookies
     */
    modifyCookies() {
      const settings = this.getFeatureSetting("modifyCookies");
      if (!settings || !settings.changes) return;
      settings.changes.forEach((change) => {
        if (change.action === "delete") {
          const pathValue = change.path ? `; path=${change.path}` : "";
          const domainValue = change.domain ? `; domain=${change.domain}` : "";
          document.cookie = `${change.key}=; expires=Thu, 01 Jan 1970 00:00:00 GMT${pathValue}${domainValue}`;
        }
      });
    }
    /**
     * Support for proxying `window.webkit.messageHandlers`
     */
    messageHandlersFix() {
      const settings = this.getFeatureSetting("messageHandlers");
      if (!globalThis.webkit?.messageHandlers) return;
      if (!settings) return;
      const proxy = new Proxy(globalThis.webkit.messageHandlers, {
        get(target, messageName, receiver) {
          const handlerName = String(messageName);
          if (settings.handlerStrategies.reflect.includes(handlerName)) {
            return Reflect.get(target, messageName, receiver);
          }
          if (settings.handlerStrategies.undefined.includes(handlerName)) {
            return void 0;
          }
          if (settings.handlerStrategies.polyfill.includes("*") || settings.handlerStrategies.polyfill.includes(handlerName)) {
            return {
              postMessage() {
                return Promise.resolve({});
              }
            };
          }
        }
      });
      globalThis.webkit = {
        ...globalThis.webkit,
        messageHandlers: proxy
      };
    }
    viewportWidthFix() {
      if (this._viewportWidthFixApplied) {
        return;
      }
      this._viewportWidthFixApplied = true;
      if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", () => this.viewportWidthFixInner());
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
        viewportTag = document.createElement("meta");
        viewportTag.setAttribute("name", "viewport");
      }
      viewportTag.setAttribute("content", forcedValue);
      if (!viewportTagExists) {
        document.head.appendChild(viewportTag);
      }
    }
    viewportWidthFixInner() {
      const viewportTags = document.querySelectorAll("meta[name=viewport i]");
      const viewportTag = viewportTags.length === 0 ? null : viewportTags[viewportTags.length - 1];
      const viewportContent = viewportTag?.getAttribute("content") || "";
      const viewportContentParts = viewportContent ? viewportContent.split(/,|;/) : [];
      const parsedViewportContent = viewportContentParts.map((part) => {
        const [key, value] = part.split("=").map((p) => p.trim().toLowerCase());
        return [key, value];
      });
      const { forcedDesktopValue, forcedMobileValue } = this.getFeatureSetting("viewportWidth");
      if (typeof forcedDesktopValue === "string" && this.desktopModeEnabled) {
        this.forceViewportTag(viewportTag, forcedDesktopValue);
        return;
      } else if (typeof forcedMobileValue === "string" && !this.desktopModeEnabled) {
        this.forceViewportTag(viewportTag, forcedMobileValue);
        return;
      }
      const forcedValues = {};
      if (this.forcedZoomEnabled) {
        forcedValues["initial-scale"] = 1;
        forcedValues["user-scalable"] = "yes";
        forcedValues["maximum-scale"] = 10;
      }
      if (this.getFeatureSettingEnabled("plainTextViewPort") && document.contentType === "text/plain") {
        forcedValues.width = "device-width";
        forcedValues["initial-scale"] = 1;
      } else if (!viewportTag || this.desktopModeEnabled) {
        forcedValues.width = screen.width >= 1280 ? 1280 : 980;
        forcedValues["initial-scale"] = (screen.width / forcedValues.width).toFixed(3);
        forcedValues["user-scalable"] = "yes";
        const minimumScalePart = parsedViewportContent.find(([key]) => key === "minimum-scale");
        if (minimumScalePart) {
          forcedValues["minimum-scale"] = 0;
        }
      } else {
        const widthPart = parsedViewportContent.find(([key]) => key === "width");
        const initialScalePart = parsedViewportContent.find(([key]) => key === "initial-scale");
        if (!widthPart && initialScalePart) {
          const parsedInitialScale = parseFloat(initialScalePart[1]);
          if (parsedInitialScale !== 1) {
            forcedValues.width = "device-width";
          }
        }
      }
      const newContent = [];
      Object.keys(forcedValues).forEach((key) => {
        newContent.push(`${key}=${forcedValues[key]}`);
      });
      if (newContent.length > 0) {
        parsedViewportContent.forEach(([key], idx) => {
          if (!(key in forcedValues)) {
            newContent.push(viewportContentParts[idx].trim());
          }
        });
        this.forceViewportTag(viewportTag, newContent.join(", "));
      }
    }
    /**
     * Defines a no-op `getCapabilities` shim on the given target (either an InputDeviceInfo
     * instance or a synthetic intermediate prototype). The shim is `wrapToString`-masked so
     * `Function.prototype.toString` looks native, and the descriptor matches native methods.
     * No-ops when `getCapabilities` is not exposed on InputDeviceInfo.prototype in this browser.
     * @param {object} target
     */
    defineSyntheticGetCapabilities(target) {
      if (typeof /** @type {any} */
      target.getCapabilities !== "function") return;
      const getCapabilities = function getCapabilities2() {
        return {};
      };
      this.defineProperty(target, "getCapabilities", {
        value: wrapToString(getCapabilities, getCapabilities, "function getCapabilities() { [native code] }"),
        writable: true,
        configurable: true,
        enumerable: true
      });
    }
    /**
     * Creates a valid MediaDeviceInfo or InputDeviceInfo object that passes instanceof checks
     * @param {'videoinput' | 'audioinput' | 'audiooutput'} kind - The device kind
     * @param {'syntheticPrototype' | 'instanceOwn'} [shimMode] - Where the synthetic shim
     *   for brand-checked InputDeviceInfo methods lives:
     *   - 'syntheticPrototype' (default): intermediate prototype between the instance and
     *     `InputDeviceInfo.prototype`. Hides shims from `hasOwnProperty` on the instance, at
     *     the cost of a one-level prototype-chain depth difference.
     *   - 'instanceOwn': preserve `InputDeviceInfo.prototype` as the direct prototype; place
     *     own masked shims on the instance.
     * @returns {MediaDeviceInfo | InputDeviceInfo}
     */
    createMediaDeviceInfo(kind, shimMode = "syntheticPrototype") {
      const isInputDevice = kind === "videoinput" || kind === "audioinput";
      let deviceInfo;
      if (isInputDevice && typeof InputDeviceInfo !== "undefined" && InputDeviceInfo.prototype) {
        if (shimMode === "instanceOwn") {
          deviceInfo = Object.create(InputDeviceInfo.prototype);
          this.defineSyntheticGetCapabilities(deviceInfo);
        } else {
          const syntheticInputDeviceInfoPrototype = Object.create(InputDeviceInfo.prototype);
          this.defineSyntheticGetCapabilities(syntheticInputDeviceInfoPrototype);
          deviceInfo = Object.create(syntheticInputDeviceInfoPrototype);
        }
      } else {
        deviceInfo = Object.create(MediaDeviceInfo.prototype);
      }
      Object.defineProperties(deviceInfo, {
        deviceId: {
          value: "default",
          writable: false,
          configurable: false,
          enumerable: true
        },
        kind: {
          value: kind,
          writable: false,
          configurable: false,
          enumerable: true
        },
        label: {
          value: "",
          writable: false,
          configurable: false,
          enumerable: true
        },
        groupId: {
          value: "default-group",
          writable: false,
          configurable: false,
          enumerable: true
        },
        toJSON: {
          value: function() {
            return {
              deviceId: this.deviceId,
              kind: this.kind,
              label: this.label,
              groupId: this.groupId
            };
          },
          writable: false,
          configurable: false,
          enumerable: true
        }
      });
      return deviceInfo;
    }
    /**
     * Fallback device list when the deviceEnumeration messaging request fails.
     * Mimics pre-permission enumerateDevices (unlabeled devices) without calling native.
     * Includes audiooutput so sites can still detect speaker/output capability.
     * @param {'syntheticPrototype' | 'instanceOwn'} shimMode
     * @returns {MediaDeviceInfo[]}
     */
    createEnumerateDevicesFallback(shimMode) {
      return [
        this.createMediaDeviceInfo("videoinput", shimMode),
        this.createMediaDeviceInfo("audioinput", shimMode),
        this.createMediaDeviceInfo("audiooutput", shimMode)
      ];
    }
    /**
     * Helper to wrap a promise with timeout
     * @param {Promise} promise - Promise to wrap
     * @param {number} timeoutMs - Timeout in milliseconds
     * @returns {Promise} Promise that rejects on timeout
     */
    withTimeout(promise, timeoutMs) {
      const timeout = new Promise((_resolve, reject) => setTimeout(() => reject(new Error("Request timeout")), timeoutMs));
      return Promise.race([promise, timeout]);
    }
    /**
     * Fixes device enumeration to handle permission prompts gracefully
     */
    deviceEnumerationFix() {
      if (!window.MediaDevices) {
        return;
      }
      const enumerateDevicesProxy = new DDGProxy(this, MediaDevices.prototype, "enumerateDevices", {
        /**
         * @param {MediaDevices['enumerateDevices']} target
         * @param {MediaDevices} thisArg
         * @param {Parameters<MediaDevices['enumerateDevices']>} args
         * @returns {Promise<MediaDeviceInfo[]>}
         */
        apply: async (target, thisArg, args) => {
          const settings = this.getFeatureSetting("enumerateDevices") || {};
          const timeoutEnabled = settings.timeoutEnabled !== false;
          const timeoutMs = settings.timeoutMs ?? 2e3;
          const shimMode = settings.shimMode === "instanceOwn" ? "instanceOwn" : "syntheticPrototype";
          try {
            const messagingPromise = this.messaging.request(MSG_DEVICE_ENUMERATION, {});
            const response = timeoutEnabled ? await this.withTimeout(messagingPromise, timeoutMs) : await messagingPromise;
            if (response.willPrompt) {
              const devices = [];
              if (response.videoInput) {
                devices.push(this.createMediaDeviceInfo("videoinput", shimMode));
              }
              if (response.audioInput) {
                devices.push(this.createMediaDeviceInfo("audioinput", shimMode));
              }
              if (response.audioOutput) {
                devices.push(this.createMediaDeviceInfo("audiooutput", shimMode));
              }
              return Promise.resolve(devices);
            } else {
              return DDGReflect.apply(target, thisArg, args);
            }
          } catch (_err) {
            return Promise.resolve(this.createEnumerateDevicesFallback(shimMode));
          }
        }
      });
      enumerateDevicesProxy.overload();
    }
  };
  _activeShareRequest = new WeakMap();
  _activeScreenLockRequest = new WeakMap();
  _webNotifications = new WeakMap();
  _permissionPollingTimer = new WeakMap();
  var web_compat_default = WebCompat;

  // src/features/fingerprinting-hardware.js
  var FingerprintingHardware = class extends ContentFeature {
    init() {
      this.wrapProperty(globalThis.Navigator.prototype, "keyboard", {
        get: () => {
          return this.getFeatureAttr("keyboard");
        }
      });
      this.wrapProperty(globalThis.Navigator.prototype, "hardwareConcurrency", {
        get: () => {
          return this.getFeatureAttr("hardwareConcurrency", 2);
        }
      });
      this.wrapProperty(globalThis.Navigator.prototype, "deviceMemory", {
        get: () => {
          return this.getFeatureAttr("deviceMemory", 8);
        }
      });
    }
  };

  // src/features/fingerprinting-screen-size.js
  var FingerprintingScreenSize = class extends ContentFeature {
    constructor() {
      super(...arguments);
      __publicField(this, "origPropertyValues", {});
    }
    init() {
      this.origPropertyValues.availTop = globalThis.screen.availTop;
      this.wrapProperty(globalThis.Screen.prototype, "availTop", {
        get: () => this.getFeatureAttr("availTop", 0)
      });
      this.origPropertyValues.availLeft = globalThis.screen.availLeft;
      this.wrapProperty(globalThis.Screen.prototype, "availLeft", {
        get: () => this.getFeatureAttr("availLeft", 0)
      });
      this.origPropertyValues.availWidth = globalThis.screen.availWidth;
      const forcedAvailWidthValue = globalThis.screen.width;
      this.wrapProperty(globalThis.Screen.prototype, "availWidth", {
        get: () => forcedAvailWidthValue
      });
      this.origPropertyValues.availHeight = globalThis.screen.availHeight;
      const forcedAvailHeightValue = globalThis.screen.height;
      this.wrapProperty(globalThis.Screen.prototype, "availHeight", {
        get: () => forcedAvailHeightValue
      });
      this.origPropertyValues.colorDepth = globalThis.screen.colorDepth;
      this.wrapProperty(globalThis.Screen.prototype, "colorDepth", {
        get: () => this.getFeatureAttr("colorDepth", 24)
      });
      this.origPropertyValues.pixelDepth = globalThis.screen.pixelDepth;
      this.wrapProperty(globalThis.Screen.prototype, "pixelDepth", {
        get: () => this.getFeatureAttr("pixelDepth", 24)
      });
      globalThis.window.addEventListener("resize", () => {
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
      try {
        this.defineProperty(globalThis, property, {
          get: () => value,
          set: () => {
          },
          configurable: true,
          enumerable: true
        });
      } catch (e) {
      }
    }
    /**
     * Fix window dimensions. The extension runs in a different JS context than the
     * page, so we can inject the correct screen values as the window is resized,
     * ensuring that no information is leaked as the dimensions change, but also that the
     * values change correctly for valid use cases.
     */
    setWindowDimensions() {
      try {
        const window2 = globalThis;
        const top = globalThis.top;
        const normalizedY = this.normalizeWindowDimension(window2.screenY, window2.screen.height);
        const normalizedX = this.normalizeWindowDimension(window2.screenX, window2.screen.width);
        if (normalizedY <= this.origPropertyValues.availTop) {
          this.setWindowPropertyValue("screenY", 0);
          this.setWindowPropertyValue("screenTop", 0);
        } else {
          this.setWindowPropertyValue("screenY", normalizedY);
          this.setWindowPropertyValue("screenTop", normalizedY);
        }
        if (top.window.outerHeight >= this.origPropertyValues.availHeight - 1) {
          this.setWindowPropertyValue("outerHeight", top.window.screen.height);
        } else {
          try {
            this.setWindowPropertyValue("outerHeight", top.window.outerHeight);
          } catch (e) {
          }
        }
        if (normalizedX <= this.origPropertyValues.availLeft) {
          this.setWindowPropertyValue("screenX", 0);
          this.setWindowPropertyValue("screenLeft", 0);
        } else {
          this.setWindowPropertyValue("screenX", normalizedX);
          this.setWindowPropertyValue("screenLeft", normalizedX);
        }
        if (top.window.outerWidth >= this.origPropertyValues.availWidth - 1) {
          this.setWindowPropertyValue("outerWidth", top.window.screen.width);
        } else {
          try {
            this.setWindowPropertyValue("outerWidth", top.window.outerWidth);
          } catch (e) {
          }
        }
      } catch (e) {
      }
    }
  };

  // src/features/fingerprinting-temporary-storage.js
  var FingerprintingTemporaryStorage = class extends ContentFeature {
    init() {
      const navigator2 = globalThis.navigator;
      const Navigator2 = globalThis.Navigator;
      if (navigator2.webkitTemporaryStorage) {
        try {
          const org = navigator2.webkitTemporaryStorage.queryUsageAndQuota;
          const tStorage = navigator2.webkitTemporaryStorage;
          tStorage.queryUsageAndQuota = function queryUsageAndQuota(callback, err) {
            const modifiedCallback = function(usedBytes, grantedBytes) {
              const maxBytesGranted = 4 * 1024 * 1024 * 1024;
              const spoofedGrantedBytes = Math.min(grantedBytes, maxBytesGranted);
              callback(usedBytes, spoofedGrantedBytes);
            };
            org.call(navigator2.webkitTemporaryStorage, modifiedCallback, err);
          };
          this.defineProperty(Navigator2.prototype, "webkitTemporaryStorage", {
            get: () => tStorage,
            enumerable: true,
            configurable: true
          });
        } catch (e) {
        }
      }
    }
  };

  // lib/sjcl.js
  var sjcl = (() => {
    "use strict";
    var sjcl2 = {
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
          this.toString = function() {
            return "CORRUPT: " + this.message;
          };
          this.message = message;
        },
        /**
         * Invalid parameter.
         * @constructor
         */
        invalid: function(message) {
          this.toString = function() {
            return "INVALID: " + this.message;
          };
          this.message = message;
        },
        /**
         * Bug or missing feature in SJCL.
         * @constructor
         */
        bug: function(message) {
          this.toString = function() {
            return "BUG: " + this.message;
          };
          this.message = message;
        },
        /**
         * Something isn't ready.
         * @constructor
         */
        notReady: function(message) {
          this.toString = function() {
            return "NOT READY: " + this.message;
          };
          this.message = message;
        }
      }
    };
    sjcl2.bitArray = {
      /**
       * Array slices in units of bits.
       * @param {bitArray} a The array to slice.
       * @param {Number} bstart The offset to the start of the slice, in bits.
       * @param {Number} bend The offset to the end of the slice, in bits.  If this is undefined,
       * slice until the end of the array.
       * @return {bitArray} The requested slice.
       */
      bitSlice: function(a2, bstart, bend) {
        a2 = sjcl2.bitArray._shiftRight(a2.slice(bstart / 32), 32 - (bstart & 31)).slice(1);
        return bend === void 0 ? a2 : sjcl2.bitArray.clamp(a2, bend - bstart);
      },
      /**
       * Extract a number packed into a bit array.
       * @param {bitArray} a The array to slice.
       * @param {Number} bstart The offset to the start of the slice, in bits.
       * @param {Number} blength The length of the number to extract.
       * @return {Number} The requested slice.
       */
      extract: function(a2, bstart, blength) {
        var x2, sh = Math.floor(-bstart - blength & 31);
        if ((bstart + blength - 1 ^ bstart) & -32) {
          x2 = a2[bstart / 32 | 0] << 32 - sh ^ a2[bstart / 32 + 1 | 0] >>> sh;
        } else {
          x2 = a2[bstart / 32 | 0] >>> sh;
        }
        return x2 & (1 << blength) - 1;
      },
      /**
       * Concatenate two bit arrays.
       * @param {bitArray} a1 The first array.
       * @param {bitArray} a2 The second array.
       * @return {bitArray} The concatenation of a1 and a2.
       */
      concat: function(a1, a2) {
        if (a1.length === 0 || a2.length === 0) {
          return a1.concat(a2);
        }
        var last2 = a1[a1.length - 1], shift = sjcl2.bitArray.getPartial(last2);
        if (shift === 32) {
          return a1.concat(a2);
        } else {
          return sjcl2.bitArray._shiftRight(a2, shift, last2 | 0, a1.slice(0, a1.length - 1));
        }
      },
      /**
       * Find the length of an array of bits.
       * @param {bitArray} a The array.
       * @return {Number} The length of a, in bits.
       */
      bitLength: function(a2) {
        var l = a2.length, x2;
        if (l === 0) {
          return 0;
        }
        x2 = a2[l - 1];
        return (l - 1) * 32 + sjcl2.bitArray.getPartial(x2);
      },
      /**
       * Truncate an array.
       * @param {bitArray} a The array.
       * @param {Number} len The length to truncate to, in bits.
       * @return {bitArray} A new array, truncated to len bits.
       */
      clamp: function(a2, len) {
        if (a2.length * 32 < len) {
          return a2;
        }
        a2 = a2.slice(0, Math.ceil(len / 32));
        var l = a2.length;
        len = len & 31;
        if (l > 0 && len) {
          a2[l - 1] = sjcl2.bitArray.partial(len, a2[l - 1] & 2147483648 >> len - 1, 1);
        }
        return a2;
      },
      /**
       * Make a partial word for a bit array.
       * @param {Number} len The number of bits in the word.
       * @param {Number} x The bits.
       * @param {Number} [_end=0] Pass 1 if x has already been shifted to the high side.
       * @return {Number} The partial word.
       */
      partial: function(len, x2, _end) {
        if (len === 32) {
          return x2;
        }
        return (_end ? x2 | 0 : x2 << 32 - len) + len * 1099511627776;
      },
      /**
       * Get the number of bits used by a partial word.
       * @param {Number} x The partial word.
       * @return {Number} The number of bits used by the partial word.
       */
      getPartial: function(x2) {
        return Math.round(x2 / 1099511627776) || 32;
      },
      /**
       * Compare two arrays for equality in a predictable amount of time.
       * @param {bitArray} a The first array.
       * @param {bitArray} b The second array.
       * @return {boolean} true if a == b; false otherwise.
       */
      equal: function(a2, b2) {
        if (sjcl2.bitArray.bitLength(a2) !== sjcl2.bitArray.bitLength(b2)) {
          return false;
        }
        var x2 = 0, i;
        for (i = 0; i < a2.length; i++) {
          x2 |= a2[i] ^ b2[i];
        }
        return x2 === 0;
      },
      /** Shift an array right.
       * @param {bitArray} a The array to shift.
       * @param {Number} shift The number of bits to shift.
       * @param {Number} [carry=0] A byte to carry in
       * @param {bitArray} [out=[]] An array to prepend to the output.
       * @private
       */
      _shiftRight: function(a2, shift, carry, out) {
        var i, last2 = 0, shift2;
        if (out === void 0) {
          out = [];
        }
        for (; shift >= 32; shift -= 32) {
          out.push(carry);
          carry = 0;
        }
        if (shift === 0) {
          return out.concat(a2);
        }
        for (i = 0; i < a2.length; i++) {
          out.push(carry | a2[i] >>> shift);
          carry = a2[i] << 32 - shift;
        }
        last2 = a2.length ? a2[a2.length - 1] : 0;
        shift2 = sjcl2.bitArray.getPartial(last2);
        out.push(sjcl2.bitArray.partial(shift + shift2 & 31, shift + shift2 > 32 ? carry : out.pop(), 1));
        return out;
      },
      /** xor a block of 4 words together.
       * @private
       */
      _xor4: function(x2, y) {
        return [x2[0] ^ y[0], x2[1] ^ y[1], x2[2] ^ y[2], x2[3] ^ y[3]];
      },
      /** byteswap a word array inplace.
       * (does not handle partial words)
       * @param {sjcl.bitArray} a word array
       * @return {sjcl.bitArray} byteswapped array
       */
      byteswapM: function(a2) {
        var i, v2, m = 65280;
        for (i = 0; i < a2.length; ++i) {
          v2 = a2[i];
          a2[i] = v2 >>> 24 | v2 >>> 8 & m | (v2 & m) << 8 | v2 << 24;
        }
        return a2;
      }
    };
    sjcl2.codec.utf8String = {
      /** Convert from a bitArray to a UTF-8 string. */
      fromBits: function(arr) {
        var out = "", bl = sjcl2.bitArray.bitLength(arr), i, tmp;
        for (i = 0; i < bl / 8; i++) {
          if ((i & 3) === 0) {
            tmp = arr[i / 4];
          }
          out += String.fromCharCode(tmp >>> 8 >>> 8 >>> 8);
          tmp <<= 8;
        }
        return decodeURIComponent(escape(out));
      },
      /** Convert from a UTF-8 string to a bitArray. */
      toBits: function(str) {
        str = unescape(encodeURIComponent(str));
        var out = [], i, tmp = 0;
        for (i = 0; i < str.length; i++) {
          tmp = tmp << 8 | str.charCodeAt(i);
          if ((i & 3) === 3) {
            out.push(tmp);
            tmp = 0;
          }
        }
        if (i & 3) {
          out.push(sjcl2.bitArray.partial(8 * (i & 3), tmp));
        }
        return out;
      }
    };
    sjcl2.codec.hex = {
      /** Convert from a bitArray to a hex string. */
      fromBits: function(arr) {
        var out = "", i;
        for (i = 0; i < arr.length; i++) {
          out += ((arr[i] | 0) + 263882790666240).toString(16).substr(4);
        }
        return out.substr(0, sjcl2.bitArray.bitLength(arr) / 4);
      },
      /** Convert from a hex string to a bitArray. */
      toBits: function(str) {
        var i, out = [], len;
        str = str.replace(/\s|0x/g, "");
        len = str.length;
        str = str + "00000000";
        for (i = 0; i < str.length; i += 8) {
          out.push(parseInt(str.substr(i, 8), 16) ^ 0);
        }
        return sjcl2.bitArray.clamp(out, len * 4);
      }
    };
    sjcl2.hash.sha256 = function(hash) {
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
    sjcl2.hash.sha256.hash = function(data) {
      return new sjcl2.hash.sha256().update(data).finalize();
    };
    sjcl2.hash.sha256.prototype = {
      /**
       * The hash's block size, in bits.
       * @constant
       */
      blockSize: 512,
      /**
       * Reset the hash state.
       * @return this
       */
      reset: function() {
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
      update: function(data) {
        if (typeof data === "string") {
          data = sjcl2.codec.utf8String.toBits(data);
        }
        var i, b2 = this._buffer = sjcl2.bitArray.concat(this._buffer, data), ol = this._length, nl = this._length = ol + sjcl2.bitArray.bitLength(data);
        if (nl > 9007199254740991) {
          throw new sjcl2.exception.invalid("Cannot hash more than 2^53 - 1 bits");
        }
        if (typeof Uint32Array !== "undefined") {
          var c = new Uint32Array(b2);
          var j2 = 0;
          for (i = 512 + ol - (512 + ol & 511); i <= nl; i += 512) {
            this._block(c.subarray(16 * j2, 16 * (j2 + 1)));
            j2 += 1;
          }
          b2.splice(0, 16 * j2);
        } else {
          for (i = 512 + ol - (512 + ol & 511); i <= nl; i += 512) {
            this._block(b2.splice(0, 16));
          }
        }
        return this;
      },
      /**
       * Complete hashing and output the hash value.
       * @return {bitArray} The hash value, an array of 8 big-endian words.
       */
      finalize: function() {
        var i, b2 = this._buffer, h = this._h;
        b2 = sjcl2.bitArray.concat(b2, [sjcl2.bitArray.partial(1, 1)]);
        for (i = b2.length + 2; i & 15; i++) {
          b2.push(0);
        }
        b2.push(Math.floor(this._length / 4294967296));
        b2.push(this._length | 0);
        while (b2.length) {
          this._block(b2.splice(0, 16));
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
      _precompute: function() {
        var i = 0, prime = 2, factor, isPrime;
        function frac(x2) {
          return (x2 - Math.floor(x2)) * 4294967296 | 0;
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
      _block: function(w2) {
        var i, tmp, a2, b2, h = this._h, k = this._key, h0 = h[0], h1 = h[1], h2 = h[2], h3 = h[3], h4 = h[4], h5 = h[5], h6 = h[6], h7 = h[7];
        for (i = 0; i < 64; i++) {
          if (i < 16) {
            tmp = w2[i];
          } else {
            a2 = w2[i + 1 & 15];
            b2 = w2[i + 14 & 15];
            tmp = w2[i & 15] = (a2 >>> 7 ^ a2 >>> 18 ^ a2 >>> 3 ^ a2 << 25 ^ a2 << 14) + (b2 >>> 17 ^ b2 >>> 19 ^ b2 >>> 10 ^ b2 << 15 ^ b2 << 13) + w2[i & 15] + w2[i + 9 & 15] | 0;
          }
          tmp = tmp + h7 + (h4 >>> 6 ^ h4 >>> 11 ^ h4 >>> 25 ^ h4 << 26 ^ h4 << 21 ^ h4 << 7) + (h6 ^ h4 & (h5 ^ h6)) + k[i];
          h7 = h6;
          h6 = h5;
          h5 = h4;
          h4 = h3 + tmp | 0;
          h3 = h2;
          h2 = h1;
          h1 = h0;
          h0 = tmp + (h1 & h2 ^ h3 & (h1 ^ h2)) + (h1 >>> 2 ^ h1 >>> 13 ^ h1 >>> 22 ^ h1 << 30 ^ h1 << 19 ^ h1 << 10) | 0;
        }
        h[0] = h[0] + h0 | 0;
        h[1] = h[1] + h1 | 0;
        h[2] = h[2] + h2 | 0;
        h[3] = h[3] + h3 | 0;
        h[4] = h[4] + h4 | 0;
        h[5] = h[5] + h5 | 0;
        h[6] = h[6] + h6 | 0;
        h[7] = h[7] + h7 | 0;
      }
    };
    sjcl2.misc.hmac = function(key, Hash) {
      this._hash = Hash = Hash || sjcl2.hash.sha256;
      var exKey = [[], []], i, bs = Hash.prototype.blockSize / 32;
      this._baseHash = [new Hash(), new Hash()];
      if (key.length > bs) {
        key = Hash.hash(key);
      }
      for (i = 0; i < bs; i++) {
        exKey[0][i] = key[i] ^ 909522486;
        exKey[1][i] = key[i] ^ 1549556828;
      }
      this._baseHash[0].update(exKey[0]);
      this._baseHash[1].update(exKey[1]);
      this._resultHash = new Hash(this._baseHash[0]);
    };
    sjcl2.misc.hmac.prototype.encrypt = sjcl2.misc.hmac.prototype.mac = function(data) {
      if (!this._updated) {
        this.update(data);
        return this.digest(data);
      } else {
        throw new sjcl2.exception.invalid("encrypt on already updated hmac called!");
      }
    };
    sjcl2.misc.hmac.prototype.reset = function() {
      this._resultHash = new this._hash(this._baseHash[0]);
      this._updated = false;
    };
    sjcl2.misc.hmac.prototype.update = function(data) {
      this._updated = true;
      this._resultHash.update(data);
    };
    sjcl2.misc.hmac.prototype.digest = function() {
      var w2 = this._resultHash.finalize(), result = new this._hash(this._baseHash[1]).update(w2).finalize();
      this.reset();
      return result;
    };
    return sjcl2;
  })();

  // src/crypto.js
  function getDataKeySync(sessionKey, domainKey, inputData) {
    const hmac = new /** @type {any} */
    sjcl.misc.hmac(
      /** @type {any} */
      sjcl.codec.utf8String.toBits(sessionKey + domainKey),
      /** @type {any} */
      sjcl.hash.sha256
    );
    return (
      /** @type {string} */
      /** @type {any} */
      sjcl.codec.hex.fromBits(hmac.encrypt(inputData))
    );
  }

  // src/features/fingerprinting-audio.js
  var FingerprintingAudio = class extends ContentFeature {
    init(args) {
      const { sessionKey, site } = args;
      const domainKey = site.domain;
      function transformArrayData(channelData, domainKey2, sessionKey2, thisArg) {
        let { audioKey } = getCachedResponse(thisArg, args);
        if (!audioKey) {
          let cdSum = 0;
          for (const k in channelData) {
            cdSum += channelData[k];
          }
          if (cdSum === 0) {
            return;
          }
          audioKey = getDataKeySync(sessionKey2, domainKey2, cdSum);
          setCache(thisArg, args, audioKey);
        }
        iterateDataKey(audioKey, (item, byte) => {
          const itemAudioIndex = item % channelData.length;
          let factor = byte * 1e-7;
          if (byte ^ 1) {
            factor = 0 - factor;
          }
          channelData[itemAudioIndex] = channelData[itemAudioIndex] + factor;
        });
      }
      const copyFromChannelProxy = new DDGProxy(this, AudioBuffer.prototype, "copyFromChannel", {
        apply(target, thisArg, args2) {
          const [source, channelNumber, startInChannel] = args2;
          if (
            // If channelNumber is longer than arrayBuffer number of channels then call the default method to throw
            // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
            channelNumber > thisArg.numberOfChannels || // If startInChannel is longer than the arrayBuffer length then call the default method to throw
            // @ts-expect-error - error TS18048: 'thisArg' is possibly 'undefined'
            startInChannel > thisArg.length
          ) {
            return DDGReflect.apply(target, thisArg, args2);
          }
          try {
            thisArg.getChannelData(channelNumber).slice(startInChannel).forEach((val, index) => {
              source[index] = val;
            });
          } catch {
            return DDGReflect.apply(target, thisArg, args2);
          }
        }
      });
      copyFromChannelProxy.overload();
      const cacheExpiry = 60;
      const cacheData = /* @__PURE__ */ new WeakMap();
      function getCachedResponse(thisArg, args2) {
        const data = cacheData.get(thisArg);
        const timeNow = Date.now();
        if (data && data.args === JSON.stringify(args2) && data.expires > timeNow) {
          data.expires = timeNow + cacheExpiry;
          cacheData.set(thisArg, data);
          return data;
        }
        return { audioKey: null };
      }
      function setCache(thisArg, args2, audioKey) {
        cacheData.set(thisArg, { args: JSON.stringify(args2), expires: Date.now() + cacheExpiry, audioKey });
      }
      const getChannelDataProxy = new DDGProxy(this, AudioBuffer.prototype, "getChannelData", {
        apply(target, thisArg, args2) {
          const channelData = DDGReflect.apply(target, thisArg, args2);
          try {
            transformArrayData(channelData, domainKey, sessionKey, thisArg, args2);
          } catch {
          }
          return channelData;
        }
      });
      getChannelDataProxy.overload();
      const audioMethods = ["getByteTimeDomainData", "getFloatTimeDomainData", "getByteFrequencyData", "getFloatFrequencyData"];
      for (const methodName of audioMethods) {
        const proxy = new DDGProxy(this, AnalyserNode.prototype, methodName, {
          apply(target, thisArg, args2) {
            DDGReflect.apply(target, thisArg, args2);
            try {
              transformArrayData(args2[0], domainKey, sessionKey, thisArg, args2);
            } catch {
            }
          }
        });
        proxy.overload();
      }
    }
  };

  // src/features/fingerprinting-battery.js
  var FingerprintingBattery = class extends ContentFeature {
    init() {
      if (globalThis.navigator.getBattery) {
        const BatteryManager = globalThis.BatteryManager;
        const spoofedValues = {
          charging: true,
          chargingTime: 0,
          dischargingTime: Infinity,
          level: 1
        };
        const eventProperties = ["onchargingchange", "onchargingtimechange", "ondischargingtimechange", "onlevelchange"];
        for (const [prop, val] of Object.entries(spoofedValues)) {
          try {
            this.defineProperty(BatteryManager.prototype, prop, {
              enumerable: true,
              configurable: true,
              get: () => {
                return val;
              }
            });
          } catch (e) {
          }
        }
        for (const eventProp of eventProperties) {
          try {
            this.defineProperty(BatteryManager.prototype, eventProp, {
              enumerable: true,
              configurable: true,
              set: (x2) => x2,
              // noop
              get: () => {
                return null;
              }
            });
          } catch (e) {
          }
        }
      }
    }
  };

  // src/features/gpc.js
  var GlobalPrivacyControl = class extends ContentFeature {
    /**
     * @param {{globalPrivacyControlValue?: boolean}} args
     */
    init(args) {
      try {
        if (args.globalPrivacyControlValue) {
          if (navigator.globalPrivacyControl) return;
          this.defineProperty(Navigator.prototype, "globalPrivacyControl", {
            get: () => true,
            configurable: true,
            enumerable: true
          });
        } else {
          if (typeof navigator.globalPrivacyControl !== "undefined") return;
          this.defineProperty(Navigator.prototype, "globalPrivacyControl", {
            get: () => false,
            configurable: true,
            enumerable: true
          });
        }
      } catch {
      }
    }
  };

  // src/features/web-detection/parse.js
  var DEFAULT_RUN_CONDITIONS = (
    /** @type {import('../../config-feature.js').ConditionBlock[]} */
    [
      {
        context: { top: true }
      }
    ]
  );
  function isValidName(name) {
    return /^[a-zA-Z][a-zA-Z0-9_]*$/.test(name);
  }
  function normalizeDetector(config) {
    const fireEvent = config.actions?.fireEvent;
    return {
      // Detectors are enabled by default
      state: config.state ?? "enabled",
      match: config.match,
      triggers: {
        // breakageReport: enabled by default - detectors participate in breakage report flow
        breakageReport: {
          state: config.triggers?.breakageReport?.state ?? "enabled",
          runConditions: config.triggers?.breakageReport?.runConditions ?? DEFAULT_RUN_CONDITIONS
        },
        // auto: disabled by default - detectors must opt in to automatic execution
        auto: {
          state: config.triggers?.auto?.state ?? "disabled",
          runConditions: config.triggers?.auto?.runConditions ?? DEFAULT_RUN_CONDITIONS,
          when: config.triggers?.auto?.when ?? { intervalMs: [] }
        }
      },
      actions: {
        // breakageReportData: enabled by default - detection results included in breakage reports
        breakageReportData: { state: config.actions?.breakageReportData?.state ?? "enabled" },
        // fireEvent: only present when configured - opt-in action that sends events to the client via webEvents
        ...fireEvent && {
          fireEvent: {
            state: fireEvent.state ?? "enabled",
            type: fireEvent.type
          }
        }
      }
    };
  }
  function parseDetectors(detectorsConfig) {
    const detectors = {};
    if (!detectorsConfig) {
      return detectors;
    }
    for (const [groupName, groupConfig] of Object.entries(detectorsConfig)) {
      if (!isValidName(groupName)) {
        continue;
      }
      const groupDetectors = {};
      for (const [detectorId, detectorConfig] of Object.entries(groupConfig)) {
        if (!isValidName(detectorId)) {
          continue;
        }
        groupDetectors[detectorId] = normalizeDetector(detectorConfig);
      }
      detectors[groupName] = groupDetectors;
    }
    return detectors;
  }

  // src/features/web-detection/matching.js
  function asArray(value, defaultValue = []) {
    if (value === void 0) return defaultValue;
    return Array.isArray(value) ? value : [value];
  }
  function isVisible(element) {
    const style = getComputedStyle(element);
    const rect = element.getBoundingClientRect();
    return rect.width > 0.5 && rect.height > 0.5 && style.display !== "none" && style.visibility !== "hidden" && parseFloat(style.opacity) > 0.05;
  }
  var contentDomParser;
  var CONTENT_METADATA_SELECTORS = "base,link,meta,script,style,template,title,desc";
  var CONTENT_MEDIA_SELECTORS = "video,canvas,embed,object,audio,map,form,input,textarea,select,button,img,svg";
  var CONTENT_TEXT_PARSE_LIMIT = 5e4;
  function hasContent(element) {
    if ((element.textContent || "").length > CONTENT_TEXT_PARSE_LIMIT) {
      return true;
    }
    if (!contentDomParser) {
      contentDomParser = new DOMParser();
    }
    const parsed = contentDomParser.parseFromString(element.outerHTML, "text/html").documentElement;
    parsed.querySelectorAll(CONTENT_METADATA_SELECTORS).forEach((el) => el.remove());
    if ((parsed.innerText || parsed.textContent || "").trim() !== "") {
      return true;
    }
    if (parsed.querySelector(CONTENT_MEDIA_SELECTORS) !== null) {
      return true;
    }
    return [...parsed.querySelectorAll("iframe")].some((frame) => {
      return !frame.hidden && frame.src !== "" && frame.src !== "about:blank";
    });
  }
  function evaluateSingleTextCondition(condition) {
    const patterns = asArray(condition.pattern);
    const selectors = asArray(condition.selector, ["body"]);
    const patternComb = new RegExp(patterns.join("|"), "i");
    return selectors.some((selector) => {
      const elements = document.querySelectorAll(selector);
      for (const element of elements) {
        if (patternComb.test(element.textContent || "")) {
          return true;
        }
      }
      return false;
    });
  }
  function evaluateSingleElementCondition(config) {
    const visibility = config.visibility ?? "any";
    return asArray(config.selector).some((selector) => {
      if (visibility === "any") {
        return document.querySelector(selector) !== null;
      }
      for (const element of document.querySelectorAll(selector)) {
        if (visibility === "visible" && isVisible(element)) {
          return true;
        }
        if (visibility === "hidden" && !isVisible(element)) {
          return true;
        }
        if (visibility === "content" && hasContent(element)) {
          return true;
        }
      }
      return false;
    });
  }
  function evaluateNode(node, evalFinal) {
    if (node === void 0) return true;
    if (Array.isArray(node)) {
      return node.some((n) => evaluateNode(n, evalFinal));
    }
    if (node === null || typeof node !== "object") {
      return evalFinal(
        /** @type {Final} */
        node
      );
    }
    const operatorKeys = ["any", "all", "none"];
    const opKeys = operatorKeys.filter((k) => hasOwnProperty.call(node, k));
    if (opKeys.length === 0) {
      return evalFinal(
        /** @type {Final} */
        node
      );
    }
    const otherKeys = objectKeys(node).filter((k) => !operatorKeys.includes(k));
    if (otherKeys.length > 0) {
      throw new Error(`Condition node mixes operator keys [${opKeys.join(", ")}] with leaf fields [${otherKeys.join(", ")}]`);
    }
    const block = (
      /** @type {Partial<Record<'all' | 'any' | 'none', ConditionBranch<Final>>>} */
      node
    );
    if (hasOwnProperty.call(block, "all") && !asArray(block.all).every((n) => evaluateNode(n, evalFinal))) return false;
    if (hasOwnProperty.call(block, "any") && !asArray(block.any).some((n) => evaluateNode(n, evalFinal))) return false;
    if (hasOwnProperty.call(block, "none") && asArray(block.none).some((n) => evaluateNode(n, evalFinal))) return false;
    return true;
  }
  function evaluateSingleMatchCondition(condition) {
    if (!evaluateNode(condition.text, evaluateSingleTextCondition)) {
      return false;
    }
    if (!evaluateNode(condition.element, evaluateSingleElementCondition)) {
      return false;
    }
    return true;
  }
  function evaluateMatch(conditions) {
    return evaluateNode(conditions, evaluateSingleMatchCondition);
  }

  // src/features/web-detection.js
  var _detectors, _matchedDetectors;
  var WebDetection = class extends ContentFeature {
    constructor() {
      super(...arguments);
      /** @type {Record<string, Record<string, DetectorConfig>>} */
      __privateAdd(this, _detectors, {});
      /** @type {Map<string, boolean>} */
      __privateAdd(this, _matchedDetectors, /* @__PURE__ */ new Map());
      __publicField(this, "_exposedMethods", this._declareExposedMethods(["runDetectors"]));
    }
    /**
     * Initialize the feature by loading detector configurations
     */
    init() {
      const detectorsConfig = this.getFeatureSetting("detectors");
      __privateSet(this, _detectors, parseDetectors(detectorsConfig));
      this._scheduleAutoRunDetectors();
    }
    /**
     *
     * @param {DetectorConfig} detectorConfig
     * @returns {DetectorMatchResult}
     */
    _evaluateMatch(detectorConfig) {
      try {
        return evaluateMatch(detectorConfig.match);
      } catch {
        return "error";
      }
    }
    /**
     * Schedule automatic detector execution based on configured intervals.
     */
    _scheduleAutoRunDetectors() {
      const detectorsByInterval = /* @__PURE__ */ new Map();
      for (const [groupName, groupDetectors] of Object.entries(__privateGet(this, _detectors))) {
        for (const [detectorId, detectorConfig] of Object.entries(groupDetectors)) {
          if (!this._shouldRunDetector(detectorConfig, { trigger: "auto" })) continue;
          const autoTrigger = detectorConfig.triggers.auto;
          const fullDetectorId = `${groupName}.${detectorId}`;
          for (const interval of autoTrigger.when.intervalMs) {
            const atInterval = detectorsByInterval.get(interval) ?? [];
            atInterval.push({
              detectorId: fullDetectorId,
              config: detectorConfig
            });
            detectorsByInterval.set(interval, atInterval);
          }
        }
      }
      for (const [interval, detectors] of detectorsByInterval.entries()) {
        setTimeout(() => {
          for (const { detectorId, config } of detectors) {
            this._runAutoDetector(detectorId, config);
          }
        }, interval);
      }
    }
    /**
     * Run a single detector with the auto trigger
     * @param {string} fullDetectorId - The full detector ID (groupName.detectorId)
     * @param {DetectorConfig} detectorConfig - The detector configuration
     */
    _runAutoDetector(fullDetectorId, detectorConfig) {
      try {
        if (__privateGet(this, _matchedDetectors).get(fullDetectorId)) {
          return;
        }
        const detected = this._evaluateMatch(detectorConfig);
        if (detected === true) {
          __privateGet(this, _matchedDetectors).set(fullDetectorId, true);
        }
        if (this.isDebug && detected !== false) {
          try {
            this.messaging?.notify("webDetectionAutoRun", {
              detectorId: fullDetectorId,
              detected,
              timestamp: Date.now()
            });
          } catch {
          }
        }
        void this._executeFireEvent(detectorConfig, detected);
      } catch (e) {
        if (this.isDebug) {
          this.log.error(`Error running auto-detector ${fullDetectorId}:`, e);
        }
      }
    }
    /**
     * Fire a web event via webEvents if the detector has a fireEvent action and detection succeeded.
     *
     * @param {DetectorConfig} detectorConfig
     * @param {DetectorMatchResult} detected
     */
    async _executeFireEvent(detectorConfig, detected) {
      try {
        if (detected !== true || !detectorConfig.actions.fireEvent) return;
        if (!this._isStateEnabled(detectorConfig.actions.fireEvent.state)) return;
        await this.callFeatureMethod("webEvents", "fireEvent", {
          type: detectorConfig.actions.fireEvent.type
        });
      } catch {
      }
    }
    /**
     * Check if a detector should be triggered.
     *
     * @param {DetectorConfig} config
     * @param {RunDetectionOptions} options
     * @returns {boolean}
     */
    _shouldRunDetector(config, options) {
      if (!this._isStateEnabled(config.state)) return false;
      const triggerSettings = config.triggers[options.trigger];
      if (!triggerSettings || !this._isStateEnabled(triggerSettings.state)) return false;
      if (triggerSettings.runConditions && !this._matchConditionalBlockOrArray(triggerSettings.runConditions)) return false;
      return true;
    }
    /**
     * Run all detectors for a specific trigger.
     *
     * @param {RunDetectionOptions} options
     * @returns {DetectorResult[]}
     */
    runDetectors(options) {
      const results = [];
      for (const [groupName, groupDetectors] of Object.entries(__privateGet(this, _detectors))) {
        for (const [detectorId, detectorConfig] of Object.entries(groupDetectors)) {
          if (!this._shouldRunDetector(detectorConfig, options)) continue;
          const detected = this._evaluateMatch(detectorConfig);
          if (options.trigger === "breakageReport" && this._isStateEnabled(detectorConfig.actions.breakageReportData.state)) {
            if (detected !== false) {
              results.push({
                detectorId: `${groupName}.${detectorId}`,
                detected
              });
            }
          }
          void this._executeFireEvent(detectorConfig, detected);
        }
      }
      return results;
    }
  };
  _detectors = new WeakMap();
  _matchedDetectors = new WeakMap();

  // src/features/web-events.js
  var MSG_WEB_EVENT = "webEvent";
  var WebEvents = class extends ContentFeature {
    constructor() {
      super(...arguments);
      __publicField(this, "_exposedMethods", this._declareExposedMethods(["fireEvent"]));
    }
    init() {
    }
    /**
     * Forward a web event to the client via messaging.
     *
     * Other features (e.g. webDetection) call this via:
     * ```js
     * this.callFeatureMethod('webEvents', 'fireEvent', { type: 'adwall' });
     * ```
     *
     * @param {{ type: string, data?: Record<string, unknown> }} event
     */
    fireEvent({ type, data = {} }) {
      this.messaging.notify(MSG_WEB_EVENT, { type, data });
    }
  };
  var web_events_default = WebEvents;

  // src/features/breakage-reporting/utils.js
  function getJsPerformanceMetrics() {
    const paintResources = performance.getEntriesByType("paint");
    const firstPaint = paintResources.find((entry) => entry.name === "first-contentful-paint");
    return firstPaint ? [firstPaint.startTime] : [];
  }
  function returnError(errorMessage) {
    return { error: errorMessage, success: false };
  }
  function waitForLCP(timeoutMs = 500) {
    return new Promise((resolve) => {
      const refs = {};
      const cleanup = () => {
        if (refs.obs) refs.obs.disconnect();
        if (refs.id) clearTimeout(refs.id);
      };
      refs.id = setTimeout(() => {
        cleanup();
        resolve(null);
      }, timeoutMs);
      refs.obs = new PerformanceObserver((list) => {
        const entries = list.getEntries();
        const lastEntry = entries[entries.length - 1];
        if (lastEntry) {
          cleanup();
          resolve(lastEntry.startTime);
        }
      });
      try {
        refs.obs.observe({ type: "largest-contentful-paint", buffered: true });
      } catch (error) {
        cleanup();
        resolve(null);
      }
    });
  }
  async function getExpandedPerformanceMetrics(timeoutMs = 500) {
    try {
      if (document.readyState !== "complete") {
        return returnError("Document not ready");
      }
      const navigation = (
        /** @type {PerformanceNavigationTiming} */
        performance.getEntriesByType("navigation")[0]
      );
      const paint = performance.getEntriesByType("paint");
      const resources = (
        /** @type {PerformanceResourceTiming[]} */
        performance.getEntriesByType("resource")
      );
      const fcp = paint.find((p) => p.name === "first-contentful-paint");
      let largestContentfulPaint = null;
      if (PerformanceObserver.supportedEntryTypes.includes("largest-contentful-paint")) {
        largestContentfulPaint = await waitForLCP(timeoutMs);
      }
      const totalResourceSize = resources.reduce((sum, r) => sum + (r.transferSize || 0), 0);
      if (navigation) {
        return {
          success: true,
          metrics: {
            // Core timing metrics (in milliseconds)
            loadComplete: navigation.loadEventEnd - navigation.fetchStart,
            domComplete: navigation.domComplete - navigation.fetchStart,
            domContentLoaded: navigation.domContentLoadedEventEnd - navigation.fetchStart,
            domInteractive: navigation.domInteractive - navigation.fetchStart,
            // Paint metrics
            firstContentfulPaint: fcp ? fcp.startTime : null,
            largestContentfulPaint,
            // Network metrics
            timeToFirstByte: navigation.responseStart - navigation.fetchStart,
            responseTime: navigation.responseEnd - navigation.responseStart,
            serverTime: navigation.responseStart - navigation.requestStart,
            // Size metrics (in octets)
            transferSize: navigation.transferSize,
            encodedBodySize: navigation.encodedBodySize,
            decodedBodySize: navigation.decodedBodySize,
            // Resource metrics
            resourceCount: resources.length,
            totalResourcesSize: totalResourceSize,
            // Additional metadata
            protocol: navigation.nextHopProtocol,
            redirectCount: navigation.redirectCount,
            navigationType: navigation.type
          }
        };
      }
      return returnError("No navigation timing found");
    } catch (e) {
      const message = e instanceof Error ? e.message : String(e);
      return returnError("JavaScript execution error: " + message);
    }
  }

  // src/detectors/utils/detection-utils.js
  function checkSelectors(selectors) {
    if (!selectors || !Array.isArray(selectors)) {
      return false;
    }
    return selectors.some((selector) => document.querySelector(selector));
  }
  function checkSelectorsWithVisibility(selectors) {
    if (!selectors || !Array.isArray(selectors)) {
      return false;
    }
    return selectors.some((selector) => {
      const element = document.querySelector(selector);
      return element && isVisible2(element);
    });
  }
  function checkWindowProperties(properties) {
    if (!properties || !Array.isArray(properties)) {
      return false;
    }
    return properties.some((prop) => prop in window);
  }
  function isVisible2(element) {
    const computedStyle = getComputedStyle(element);
    const rect = element.getBoundingClientRect();
    return rect.width > 0.5 && rect.height > 0.5 && computedStyle.display !== "none" && computedStyle.visibility !== "hidden" && +computedStyle.opacity > 0.05;
  }
  function getTextContent(element, sources) {
    if (!sources || sources.length === 0) {
      return element.textContent || "";
    }
    return sources.map((source) => {
      const value = Reflect.get(element, source);
      return typeof value === "string" ? value : "";
    }).join(" ");
  }
  function matchesSelectors(selectors) {
    if (!selectors || !Array.isArray(selectors)) {
      return false;
    }
    const elements = queryAllSelectors(selectors);
    return elements.length > 0;
  }
  function matchesTextPatterns(element, patterns, sources) {
    if (!patterns || !Array.isArray(patterns)) {
      return false;
    }
    const text = getTextContent(element, sources);
    return patterns.some((pattern) => {
      try {
        const regex = new RegExp(pattern, "i");
        return regex.test(text);
      } catch {
        return false;
      }
    });
  }
  function checkTextPatterns(patterns, sources) {
    if (!patterns || !Array.isArray(patterns)) {
      return false;
    }
    return matchesTextPatterns(document.body, patterns, sources);
  }
  function queryAllSelectors(selectors, root = document) {
    if (!selectors || !Array.isArray(selectors) || selectors.length === 0) {
      return [];
    }
    const elements = root.querySelectorAll(selectors.join(","));
    return Array.from(elements);
  }
  function toRegExpArray(patterns, flags = "i") {
    if (!patterns || !Array.isArray(patterns)) {
      return [];
    }
    return patterns.map((p) => {
      try {
        return new RegExp(p, flags);
      } catch {
        return null;
      }
    }).filter(
      /** @type {(r: RegExp | null) => r is RegExp} */
      (r) => r !== null
    );
  }

  // src/detectors/detections/bot-detection.js
  function runBotDetection(config = {}) {
    const results = Object.entries(config).filter(([_2, challengeConfig]) => challengeConfig?.state === "enabled").map(([challengeId, challengeConfig]) => {
      const detected = checkSelectors(challengeConfig.selectors) || checkWindowProperties(challengeConfig.windowProperties || []);
      if (!detected) {
        return null;
      }
      const challengeStatus = findStatus(challengeConfig.statusSelectors);
      return {
        detected: true,
        vendor: challengeConfig.vendor,
        challengeType: challengeId,
        challengeStatus
      };
    }).filter(Boolean);
    return {
      detected: results.length > 0,
      type: "botDetection",
      results
    };
  }
  function findStatus(statusSelectors) {
    if (!Array.isArray(statusSelectors)) {
      return null;
    }
    const match = statusSelectors.find((statusConfig) => {
      const { selectors, textPatterns, textSources } = statusConfig;
      return matchesSelectors(selectors) || matchesTextPatterns(document.body, textPatterns, textSources);
    });
    return match?.status ?? null;
  }

  // src/detectors/detections/fraud-detection.js
  function runFraudDetection(config = {}) {
    const results = Object.entries(config).filter(([_2, alertConfig]) => alertConfig?.state === "enabled").map(([alertId, alertConfig]) => {
      const detected = checkSelectorsWithVisibility(alertConfig.selectors) || checkTextPatterns(alertConfig.textPatterns, alertConfig.textSources);
      if (!detected) {
        return null;
      }
      return {
        detected: true,
        alertId,
        category: alertConfig.type
      };
    }).filter(Boolean);
    return {
      detected: results.length > 0,
      type: "fraudDetection",
      results
    };
  }

  // src/detectors/detections/adwall-detection.js
  function runAdwallDetection(config = {}) {
    const results = [];
    for (const [detectorId, detectorConfig] of Object.entries(config)) {
      if (detectorConfig?.state !== "enabled") {
        continue;
      }
      const detected = detectAdwall(detectorConfig);
      if (detected) {
        results.push({
          detected: true,
          detectorId
        });
      }
    }
    return {
      detected: results.length > 0,
      type: "adwallDetection",
      results
    };
  }
  function detectAdwall(patternConfig) {
    const { textPatterns, textSources } = patternConfig;
    if (checkTextPatterns(textPatterns, textSources)) {
      return true;
    }
    return false;
  }

  // src/detectors/detections/youtube-ad-detection.js
  var noopLogger = { info: () => {
  }, warn: () => {
  }, error: () => {
  } };
  var YouTubeAdDetector = class {
    /**
     * @param {YouTubeDetectorConfig} config - Configuration from privacy-config (required)
     * @param {{info: Function, warn: Function, error: Function}} [logger] - Optional logger from ContentFeature
     * @param {(type: string, data?: Record<string, unknown>) => void} [onEvent] - Callback fired when a new detection occurs (may be async)
     */
    constructor(config, logger, onEvent) {
      this.log = logger || noopLogger;
      this.onEvent = onEvent || (() => {
      });
      this.config = {
        playerSelectors: config.playerSelectors,
        adClasses: config.adClasses,
        adTextPatterns: config.adTextPatterns,
        sweepIntervalMs: config.sweepIntervalMs,
        slowLoadThresholdMs: config.slowLoadThresholdMs,
        staticAdSelectors: config.staticAdSelectors,
        playabilityErrorSelectors: config.playabilityErrorSelectors,
        playabilityErrorPatterns: config.playabilityErrorPatterns,
        adBlockerDetectionSelectors: config.adBlockerDetectionSelectors,
        adBlockerDetectionPatterns: config.adBlockerDetectionPatterns,
        loginStateSelectors: config.loginStateSelectors,
        fireDetectionEvents: config.fireDetectionEvents
      };
      this.state = this.createInitialState();
      this.pollInterval = null;
      this.rerootInterval = null;
      this.startRetryTimeout = null;
      this.trackedVideoElement = null;
      this.lastLoggedVideoId = null;
      this.currentVideoId = null;
      this.videoLoadStartTime = null;
      this.bufferingStartTime = null;
      this.lastSweepTime = null;
      this.lastSeekTime = null;
      this.playerRoot = null;
      this.adTextPatterns = toRegExpArray(this.config.adTextPatterns);
      this.playabilityErrorPatterns = toRegExpArray(this.config.playabilityErrorPatterns);
      this.adBlockerDetectionPatterns = toRegExpArray(this.config.adBlockerDetectionPatterns);
      this.cachedAdSelector = this.config.adClasses && this.config.adClasses.length > 0 ? this.config.adClasses.map((cls) => "." + cls).join(",") : null;
    }
    // =========================================================================
    // State Management
    // =========================================================================
    createInitialState() {
      return {
        detections: {
          videoAd: { count: 0, showing: false },
          staticAd: { count: 0, showing: false },
          playabilityError: {
            count: 0,
            showing: false,
            /** @type {string|null} */
            lastMessage: null
          },
          adBlocker: { count: 0, showing: false }
        },
        buffering: {
          count: 0,
          /** @type {number[]} */
          durations: []
        },
        videoLoads: 0,
        /** @type {{state: string, isPremium: boolean, rawIndicators: Object}|null} */
        loginState: null,
        perfMetrics: {
          /** @type {number[]} */
          sweepDurations: [],
          /** @type {number[]} */
          adCheckDurations: [],
          sweepCount: 0,
          /** @type {number[]} */
          top5SweepDurations: [],
          /** @type {number[]} */
          top5AdCheckDurations: [],
          sweepsOver10ms: 0,
          sweepsOver50ms: 0
        }
      };
    }
    /**
     * Fire an event notification for native telemetry/action handling.
     * @param {'videoAd'|'staticAd'|'playabilityError'|'adBlocker'|'buffering'} type
     */
    fireDetectionEvent(type) {
      if (this.config.fireDetectionEvents?.[type]) {
        try {
          const result = (
            /** @type {any} */
            this.onEvent(`youtube_${type}`, {
              loginState: this.state.loginState?.state || "unknown"
            })
          );
          if (result && typeof result.catch === "function") {
            result.catch(() => {
            });
          }
        } catch {
        }
      }
    }
    /**
     * Report a detection event
     * @param {'videoAd'|'staticAd'|'playabilityError'|'adBlocker'} type
     * @param {Object} [details]
     * @returns {boolean} Whether detection was new
     */
    reportDetection(type, details = {}) {
      const typeState = this.state.detections[type];
      if (typeState.showing) {
        if (!details.message || typeState.lastMessage === details.message) {
          return false;
        }
      }
      this.log.info(`Detection: ${type}`, details.message || "");
      typeState.showing = true;
      typeState.count++;
      if (details.message && "lastMessage" in typeState) {
        typeState.lastMessage = details.message;
      }
      this.fireDetectionEvent(type);
      return true;
    }
    /**
     * Clear a detection state
     * @param {'videoAd'|'staticAd'|'playabilityError'|'adBlocker'} type
     */
    clearDetection(type) {
      const typeState = this.state.detections[type];
      if (!typeState.showing) return;
      typeState.showing = false;
      if ("lastMessage" in typeState) {
        typeState.lastMessage = null;
      }
    }
    // =========================================================================
    // Main Detection Loop
    // =========================================================================
    /**
     * Run one sweep of all detection checks
     * Called periodically by the poll interval
     */
    sweep() {
      const sweepStart = performance.now();
      this.lastSweepTime = sweepStart;
      const root = this.findPlayerRoot();
      if (!root) return;
      this.attachVideoListeners(root);
      const adCheckStart = performance.now();
      const hasVideoAd = this.checkForVideoAds(root);
      const adCheckDuration = performance.now() - adCheckStart;
      if (hasVideoAd && !this.state.detections.videoAd.showing) {
        this.reportDetection("videoAd");
      } else if (!hasVideoAd && this.state.detections.videoAd.showing) {
        this.clearDetection("videoAd");
      }
      const hasStaticAd = this.checkForStaticAds(root);
      if (hasStaticAd && !this.state.detections.staticAd.showing) {
        this.reportDetection("staticAd");
      } else if (!hasStaticAd && this.state.detections.staticAd.showing) {
        this.clearDetection("staticAd");
      }
      const playabilityError = this.checkForPlayabilityErrors();
      if (playabilityError && !this.state.detections.playabilityError.showing) {
        this.reportDetection("playabilityError", { message: playabilityError });
      } else if (!playabilityError && this.state.detections.playabilityError.showing) {
        this.clearDetection("playabilityError");
      }
      const adBlockerDetected = this.checkForAdBlockerModals();
      if (adBlockerDetected && !this.state.detections.adBlocker.showing) {
        this.reportDetection("adBlocker");
      } else if (!adBlockerDetected && this.state.detections.adBlocker.showing) {
        this.clearDetection("adBlocker");
      }
      this.trackSweepPerformance(sweepStart, adCheckDuration);
    }
    /**
     * Track sweep performance metrics
     * @param {number} sweepStart
     * @param {number} adCheckDuration
     */
    trackSweepPerformance(sweepStart, adCheckDuration) {
      const sweepDuration = performance.now() - sweepStart;
      const perf = this.state.perfMetrics;
      perf.sweepDurations.push(sweepDuration);
      perf.adCheckDurations.push(adCheckDuration);
      perf.sweepCount++;
      perf.top5SweepDurations.push(sweepDuration);
      perf.top5SweepDurations.sort((a2, b2) => b2 - a2);
      if (perf.top5SweepDurations.length > 5) perf.top5SweepDurations.pop();
      perf.top5AdCheckDurations.push(adCheckDuration);
      perf.top5AdCheckDurations.sort((a2, b2) => b2 - a2);
      if (perf.top5AdCheckDurations.length > 5) perf.top5AdCheckDurations.pop();
      if (sweepDuration > 10) perf.sweepsOver10ms++;
      if (sweepDuration > 50) perf.sweepsOver50ms++;
      if (perf.sweepDurations.length > 50) {
        perf.sweepDurations.shift();
        perf.adCheckDurations.shift();
      }
    }
    // =========================================================================
    // Detection Helpers
    // =========================================================================
    /**
     * Check if a node looks like an ad
     * @param {Node} node
     * @returns {boolean}
     */
    looksLikeAdNode(node) {
      if (!(node instanceof HTMLElement)) return false;
      const classList = node.classList;
      const adClasses = this.config.adClasses;
      if (classList && adClasses && adClasses.some((adClass) => classList.contains(adClass))) {
        return true;
      }
      const txt = (node.innerText || "") + " " + (node.getAttribute("aria-label") || "");
      const patterns = this.adTextPatterns;
      return patterns && patterns.some((pattern) => pattern.test(txt));
    }
    /**
     * Check for visible video ads in the player
     * @param {Element} root - Player root element
     * @returns {boolean}
     */
    checkForVideoAds(root) {
      if (!this.cachedAdSelector) {
        return false;
      }
      if (root.matches && root.matches(this.cachedAdSelector)) {
        this.log.info("Ad detected: root element matches ad selector");
        return true;
      }
      const adElements = root.querySelectorAll(this.cachedAdSelector);
      const hasAd = Array.from(adElements).some((el) => isVisible2(el) && this.looksLikeAdNode(el));
      if (hasAd) {
        this.log.info("Ad detected: child element matches ad selector");
      }
      return hasAd;
    }
    /**
     * Check for static overlay ads (image ads over the player)
     * @param {Element|null} [root] - Player root already resolved by the sweep, if any
     * @returns {boolean}
     */
    checkForStaticAds(root) {
      const selectors = this.config.staticAdSelectors;
      if (!selectors || !selectors.background) {
        return false;
      }
      const player = root ?? this.playerRoot ?? this.findPlayerRoot();
      if (!player || !/\bad-showing\b|\bad-interrupting\b/.test((player.className || "").toString())) {
        return false;
      }
      const background = document.querySelector(selectors.background);
      if (!background || !isVisible2(background)) {
        return false;
      }
      const thumbnail = document.querySelector(selectors.thumbnail);
      const image = document.querySelector(selectors.image);
      if (!thumbnail && !image) {
        return false;
      }
      const video = document.querySelector("#movie_player video, .html5-video-player video");
      const videoNotPlaying = !video || video.paused && video.currentTime < 1;
      if (image) {
        const img = image.querySelector("img");
        if (img && img.src && isVisible2(image)) {
          return true;
        }
      }
      if (thumbnail && isVisible2(thumbnail) && videoNotPlaying) {
        return true;
      }
      return false;
    }
    /**
     * Check for visible elements matching selectors and text patterns
     * @param {string[]} selectors
     * @param {RegExp[]} patterns
     * @param {Object} [options]
     * @returns {string|null} Matched text or null
     */
    checkVisiblePatternMatch(selectors, patterns, options = {}) {
      if (!selectors || !selectors.length || !patterns || !patterns.length) {
        return null;
      }
      const maxLen = options.maxLength || 100;
      const checkAttributedStrings = options.checkAttributedStrings || false;
      const checkDialogFallback = options.checkDialogFallback || false;
      for (const selector of selectors) {
        const el = (
          /** @type {HTMLElement | null} */
          document.querySelector(selector)
        );
        if (el && isVisible2(el)) {
          const text = el.innerText || el.textContent || "";
          for (const pattern of patterns) {
            if (pattern.test(text)) {
              return text.trim().substring(0, maxLen);
            }
          }
          if (checkAttributedStrings) {
            const attributedStrings = el.querySelectorAll('.yt-core-attributed-string[role="text"]');
            for (const attrEl of attributedStrings) {
              const attrText = attrEl.textContent || "";
              for (const pattern of patterns) {
                if (pattern.test(attrText)) {
                  return attrText.trim().substring(0, maxLen);
                }
              }
            }
          }
        }
      }
      if (checkDialogFallback) {
        const bodyText = document.body?.innerText || "";
        for (const pattern of patterns) {
          if (pattern.test(bodyText)) {
            const dialogs = document.querySelectorAll('[role="dialog"], [aria-modal="true"], .ytd-popup-container');
            for (const dialog of dialogs) {
              if (dialog instanceof HTMLElement && isVisible2(dialog)) {
                const dialogText = dialog.innerText || "";
                if (pattern.test(dialogText)) {
                  return dialogText.trim().substring(0, maxLen);
                }
              }
            }
          }
        }
      }
      return null;
    }
    /**
     * Check for playability errors (bot detection, content blocking)
     * @returns {string|null}
     */
    checkForPlayabilityErrors() {
      if (!this.isVideoWatchContext()) {
        return null;
      }
      return this.checkVisiblePatternMatch(this.config.playabilityErrorSelectors, this.playabilityErrorPatterns, {
        maxLength: 100,
        checkAttributedStrings: true
      });
    }
    /**
     * Check for ad blocker detection modals
     * @returns {string|null}
     */
    checkForAdBlockerModals() {
      return this.checkVisiblePatternMatch(this.config.adBlockerDetectionSelectors, this.adBlockerDetectionPatterns, {
        maxLength: 150,
        checkDialogFallback: true
      });
    }
    // =========================================================================
    // DOM Queries
    // =========================================================================
    /**
     * Find the YouTube player root element
     * @returns {Element|null}
     */
    findPlayerRoot() {
      if (!this.config.playerSelectors || !this.config.playerSelectors.length) {
        return null;
      }
      for (const selector of this.config.playerSelectors) {
        const el = document.querySelector(selector);
        if (el) return el;
      }
      return null;
    }
    /**
     * Get current video ID from URL
     * @returns {string|null}
     */
    getVideoId() {
      const urlParams = new URLSearchParams(window.location.search);
      return urlParams.get("v");
    }
    /**
     * Whether the current page is an actual video-watch surface — a standard watch page
     * (`?v=<id>`) or a Shorts/live/embed path. Used to avoid firing playability-error
     * detections on non-playback pages (channel/home), where a featured-preview player
     * can transiently show an unavailability message for a video the user isn't watching.
     * @returns {boolean}
     */
    isVideoWatchContext() {
      const pathname = window.location.pathname;
      return !!this.getVideoId() || pathname === "/watch" || /^\/(shorts|live|embed)\//.test(pathname);
    }
    // =========================================================================
    // Login State Detection
    // =========================================================================
    /**
     * Detect YouTube user login state using DOM elements
     * @returns {{state: string, isPremium: boolean, rawIndicators: Object}}
     */
    detectLoginState() {
      const selectors = this.config.loginStateSelectors;
      if (!selectors) {
        return { state: "unknown", isPremium: false, rawIndicators: {} };
      }
      const indicators = {
        hasSignInButton: false,
        hasAvatarButton: false,
        hasPremiumLogo: false
      };
      try {
        indicators.hasSignInButton = !!document.querySelector(selectors.signInButton);
        indicators.hasAvatarButton = !!document.querySelector(selectors.avatarButton);
        indicators.hasPremiumLogo = !!document.querySelector(selectors.premiumLogo);
      } catch {
      }
      let loginState = "unknown";
      if (indicators.hasPremiumLogo) {
        loginState = "premium";
      } else if (indicators.hasAvatarButton) {
        loginState = "logged-in";
      } else if (indicators.hasSignInButton) {
        loginState = "logged-out";
      }
      return {
        state: loginState,
        isPremium: indicators.hasPremiumLogo,
        rawIndicators: indicators
      };
    }
    /**
     * Detect login state with retries for timing issues
     * @param {number} [attempt=1]
     */
    detectAndLogLoginState(attempt = 1) {
      if (this.state.loginState?.state && this.state.loginState.state !== "unknown") {
        return;
      }
      const loginState = this.detectLoginState();
      if (loginState.state !== "unknown" || attempt >= 5) {
        this.state.loginState = loginState;
      } else {
        const delay = attempt * 500;
        setTimeout(() => this.detectAndLogLoginState(attempt + 1), delay);
      }
    }
    // =========================================================================
    // Video Tracking
    // =========================================================================
    /**
     * Attach event listeners to video element for tracking
     * @param {Element} root - Player root element
     * @param {number} [attempt=1] - Current retry attempt
     */
    attachVideoListeners(root, attempt = 1) {
      const videoElement = (
        /** @type {HTMLVideoElement | null} */
        root?.querySelector("video")
      );
      if (!videoElement) {
        if (attempt < 25) {
          setTimeout(() => this.attachVideoListeners(root, attempt + 1), 500);
        }
        return;
      }
      if (this.trackedVideoElement === videoElement) return;
      this.trackedVideoElement = videoElement;
      const onLoadStart = () => {
        const vid2 = this.getVideoId();
        if (vid2 && vid2 !== this.lastLoggedVideoId) {
          this.lastLoggedVideoId = vid2;
          this.currentVideoId = vid2;
          this.videoLoadStartTime = performance.now();
          this.state.videoLoads++;
        }
      };
      const onPlaying = () => {
        let firedBufferingEvent = false;
        if (this.bufferingStartTime) {
          const bufferingDuration = performance.now() - this.bufferingStartTime;
          this.state.buffering.durations.push(Math.round(bufferingDuration));
          if (this.state.buffering.durations.length > 50) {
            this.state.buffering.durations.shift();
          }
          if (bufferingDuration > this.config.slowLoadThresholdMs) {
            this.fireDetectionEvent("buffering");
            firedBufferingEvent = true;
          }
          this.bufferingStartTime = null;
        }
        if (!this.videoLoadStartTime) return;
        const loadTime = performance.now() - this.videoLoadStartTime;
        const isSlow = loadTime > this.config.slowLoadThresholdMs;
        const duringAd = this.state.detections.videoAd.showing;
        const tabWasHidden = document.hidden;
        const tooLong = loadTime > 3e4;
        if (isSlow && !duringAd && !tabWasHidden && !tooLong) {
          this.state.buffering.count++;
          this.state.buffering.durations.push(Math.round(loadTime));
          if (!firedBufferingEvent) {
            this.fireDetectionEvent("buffering");
          }
          if (this.state.buffering.durations.length > 50) {
            this.state.buffering.durations.shift();
          }
        }
        this.videoLoadStartTime = null;
      };
      const onWaiting = () => {
        if (this.state.detections.videoAd.showing) return;
        if (videoElement.currentTime < 0.5) return;
        const recentlySeekd = this.lastSeekTime && performance.now() - this.lastSeekTime < 3e3;
        if (videoElement.seeking || recentlySeekd) return;
        if (!this.bufferingStartTime) {
          this.bufferingStartTime = performance.now();
          this.state.buffering.count++;
        }
      };
      const onSeeking = () => {
        this.lastSeekTime = performance.now();
      };
      videoElement.addEventListener("loadstart", onLoadStart);
      videoElement.addEventListener("playing", onPlaying);
      videoElement.addEventListener("waiting", onWaiting);
      videoElement.addEventListener("seeking", onSeeking);
      const vid = this.getVideoId();
      if (vid && vid !== this.lastLoggedVideoId) {
        this.lastLoggedVideoId = vid;
        this.currentVideoId = vid;
        this.state.videoLoads++;
      }
    }
    // =========================================================================
    // SPA Navigation
    // =========================================================================
    // =========================================================================
    // Lifecycle
    // =========================================================================
    /**
     * Start the detector
     * @param {number} [attempt=1] - Current retry attempt
     */
    start(attempt = 1) {
      this.log.info("YouTubeAdDetector starting...");
      const root = this.findPlayerRoot();
      if (!root) {
        if (attempt < 25) {
          this.log.info(`Player root not found, retrying in 500ms (attempt ${attempt}/25)`);
          this.startRetryTimeout = setTimeout(() => this.start(attempt + 1), 500);
        } else {
          this.log.info("Player root not found after 25 attempts, giving up");
        }
        return;
      }
      this.playerRoot = root;
      this.log.info("Player root found:", root.id || root.className);
      this.detectAndLogLoginState();
      this.attachVideoListeners(root);
      this.sweep();
      this.pollInterval = setInterval(() => this.sweep(), this.config.sweepIntervalMs || 2e3);
      this.log.info(`Detector started, sweep interval: ${this.config.sweepIntervalMs}ms`);
      this.rerootInterval = setInterval(() => {
        const r = this.findPlayerRoot();
        if (r && r !== this.playerRoot) {
          this.playerRoot = r;
          if (this.pollInterval) clearInterval(this.pollInterval);
          this.pollInterval = setInterval(() => this.sweep(), this.config.sweepIntervalMs || 2e3);
        }
      }, 1e3);
    }
    /**
     * Stop the detector
     */
    stop() {
      if (this.startRetryTimeout) {
        clearTimeout(this.startRetryTimeout);
        this.startRetryTimeout = null;
      }
      if (this.pollInterval) {
        clearInterval(this.pollInterval);
        this.pollInterval = null;
      }
      if (this.rerootInterval) {
        clearInterval(this.rerootInterval);
        this.rerootInterval = null;
      }
    }
    // =========================================================================
    // Results
    // =========================================================================
    /**
     * Get detection results in standard format
     * @returns {Object}
     */
    getResults() {
      const d = this.state.detections;
      const totalBufferingMs = this.state.buffering.durations.reduce((sum, dur) => sum + dur, 0);
      const avgBufferingMs = this.state.buffering.durations.length > 0 ? totalBufferingMs / this.state.buffering.durations.length : 0;
      const bufferAvgSec = Math.round(avgBufferingMs / 1e3);
      let loginState = this.state.loginState;
      if (!loginState || loginState.state === "unknown") {
        const freshCheck = this.detectLoginState();
        if (freshCheck.state !== "unknown") {
          this.state.loginState = freshCheck;
          loginState = freshCheck;
        }
      }
      const perf = this.state.perfMetrics;
      let sweepAvgMs = null;
      if (perf && perf.sweepCount > 0 && perf.sweepDurations.length > 0) {
        const avg = perf.sweepDurations.reduce((a2, b2) => a2 + b2, 0) / perf.sweepDurations.length;
        sweepAvgMs = Math.round(avg);
      }
      return {
        detected: d.videoAd.count > 0 || d.staticAd.count > 0 || d.playabilityError.count > 0 || d.adBlocker.count > 0 || this.state.buffering.count > 0,
        type: "youtubeAds",
        results: [
          {
            adsDetected: d.videoAd.count,
            staticAdsDetected: d.staticAd.count,
            playabilityErrorsDetected: d.playabilityError.count,
            adBlockerDetectionCount: d.adBlocker.count,
            bufferingCount: this.state.buffering.count,
            bufferAvgSec,
            userState: loginState?.state || "unknown",
            sweepAvgMs
          }
        ]
      };
    }
  };
  var detectorInstance = null;
  function runYoutubeAdDetection(config, logger, fireEvent) {
    const hostname = window.location.hostname;
    const isYouTube = hostname === "youtube.com" || hostname.endsWith(".youtube.com");
    const isTestDomain = hostname === "privacy-test-pages.site" || hostname.endsWith(".privacy-test-pages.site") || hostname === "localhost";
    if (!isYouTube && !isTestDomain) {
      return { detected: false, type: "youtubeAds", results: [] };
    }
    if (config?.state !== "enabled" && config?.state !== "internal") {
      return { detected: false, type: "youtubeAds", results: [] };
    }
    if (detectorInstance) {
      if (fireEvent) {
        detectorInstance.onEvent = fireEvent;
      }
      return detectorInstance.getResults();
    }
    if (!config) {
      return { detected: false, type: "youtubeAds", results: [] };
    }
    detectorInstance = new YouTubeAdDetector(config, logger, fireEvent);
    detectorInstance.start();
    return detectorInstance.getResults();
  }

  // src/features/breakage-reporting.js
  var BreakageReporting = class extends ContentFeature {
    init() {
      const isExpandedPerformanceMetricsEnabled = this.getFeatureSettingEnabled("expandedPerformanceMetrics", "enabled");
      this.messaging.subscribe("getBreakageReportValues", async () => {
        const breakageDataPayload = (
          /** @type {Record<string, unknown>} */
          {}
        );
        const jsPerformance = getJsPerformanceMetrics();
        const referrer = document.referrer;
        const result = {
          jsPerformance,
          referrer
        };
        const getOpener = this.getFeatureSettingEnabled("opener", "enabled");
        if (getOpener) {
          result.opener = !!window.opener;
        }
        const getReloaded = this.getFeatureSettingEnabled("reloaded", "enabled");
        if (getReloaded) {
          result.pageReloaded = window.performance.navigation && window.performance.navigation.type === 1 || /** @type {PerformanceNavigationTiming[]} */
          window.performance.getEntriesByType("navigation").map((nav) => nav.type).includes("reload");
        }
        const webDetectionResults = await this.callFeatureMethod("webDetection", "runDetectors", { trigger: "breakageReport" });
        if (!(webDetectionResults instanceof CallFeatureMethodError) && webDetectionResults.length > 0) {
          breakageDataPayload.webDetection = webDetectionResults;
        }
        const detectorSettings = this.getFeatureSetting("interferenceTypes", "webInterferenceDetection");
        if (detectorSettings) {
          result.detectorData = {
            botDetection: runBotDetection(detectorSettings.botDetection),
            fraudDetection: runFraudDetection(detectorSettings.fraudDetection),
            adwallDetection: runAdwallDetection(detectorSettings.adwallDetection),
            youtubeAds: runYoutubeAdDetection(detectorSettings.youtubeAds)
          };
        }
        if (isExpandedPerformanceMetricsEnabled) {
          const expandedPerformanceMetrics = await getExpandedPerformanceMetrics();
          if (expandedPerformanceMetrics.success) {
            result.expandedPerformanceMetrics = expandedPerformanceMetrics.metrics;
          }
        }
        if (result.detectorData) {
          breakageDataPayload.detectorData = result.detectorData;
        }
        if (Object.keys(breakageDataPayload).length > 0) {
          try {
            result.breakageData = encodeURIComponent(JSON.stringify(breakageDataPayload));
          } catch (e) {
            result.breakageData = encodeURIComponent(JSON.stringify({ error: "encoding_failed" }));
          }
        }
        this.messaging.notify("breakageReportResult", result);
      });
    }
  };

  // ddg:platformFeatures:ddg:platformFeatures
  var ddg_platformFeatures_default = {
    ddg_feature_apiManipulation: ApiManipulation,
    ddg_feature_webCompat: web_compat_default,
    ddg_feature_fingerprintingHardware: FingerprintingHardware,
    ddg_feature_fingerprintingScreenSize: FingerprintingScreenSize,
    ddg_feature_fingerprintingTemporaryStorage: FingerprintingTemporaryStorage,
    ddg_feature_fingerprintingAudio: FingerprintingAudio,
    ddg_feature_fingerprintingBattery: FingerprintingBattery,
    ddg_feature_gpc: GlobalPrivacyControl,
    ddg_feature_webDetection: WebDetection,
    ddg_feature_webEvents: web_events_default,
    ddg_feature_breakageReporting: BreakageReporting
  };

  // src/url-change.js
  var urlChangeListeners = /* @__PURE__ */ new Set();
  function registerForURLChanges(listener) {
    if (urlChangeListeners.size === 0) {
      listenForURLChanges();
    }
    urlChangeListeners.add(listener);
  }
  function handleURLChange(navigationType = "unknown") {
    for (const listener of urlChangeListeners) {
      listener(navigationType);
    }
  }
  function listenForURLChanges() {
    const urlChangedInstance = new ContentFeature(
      "urlChanged",
      {},
      {},
      /** @type {any} */
      {}
    );
    const nav = (
      /** @type {any} */
      globalThis.navigation
    );
    if (nav && "addEventListener" in nav) {
      const navigations = /* @__PURE__ */ new WeakMap();
      nav.addEventListener("navigate", (event) => {
        navigations.set(event.target, event.navigationType);
      });
      nav.addEventListener("navigatesuccess", (event) => {
        const navigationType = navigations.get(event.target);
        handleURLChange(navigationType);
        navigations.delete(event.target);
      });
      return;
    }
    if (isBeingFramed()) {
      return;
    }
    const historyMethodProxy = new DDGProxy(urlChangedInstance, History.prototype, "pushState", {
      apply(target, thisArg, args) {
        const changeResult = DDGReflect.apply(target, thisArg, args);
        handleURLChange("push");
        return changeResult;
      }
    });
    historyMethodProxy.overload();
    const historyMethodProxyReplace = new DDGProxy(urlChangedInstance, History.prototype, "replaceState", {
      apply(target, thisArg, args) {
        const changeResult = DDGReflect.apply(target, thisArg, args);
        handleURLChange("replace");
        return changeResult;
      }
    });
    historyMethodProxyReplace.overload();
    window.addEventListener("popstate", () => {
      handleURLChange("traverse");
    });
  }

  // src/content-scope-features.js
  var initArgs = null;
  var updates = [];
  var _features2 = {};
  var alwaysInitFeatures = /* @__PURE__ */ new Set(["cookie"]);
  var performanceMonitor = new PerformanceMonitor();
  var isHTMLDocument = document instanceof HTMLDocument || document instanceof XMLDocument && document.createElement("div") instanceof HTMLDivElement;
  function load(args) {
    const mark = performanceMonitor.mark("load");
    if (!isHTMLDocument) {
      return;
    }
    const importConfig = {
      trackerLookup: define_import_meta_trackerLookup_default,
      injectName: "android-adsjs"
    };
    const bundledFeatureNames = typeof importConfig.injectName === "string" ? platformSupport[importConfig.injectName] ?? [] : [];
    const featuresToLoad = isGloballyDisabled(args) ? platformSpecificFeatures : args.site.enabledFeatures || bundledFeatureNames;
    for (const featureName of bundledFeatureNames) {
      if (featuresToLoad.includes(featureName)) {
        const ContentFeature2 = ddg_platformFeatures_default["ddg_feature_" + featureName];
        if (!ContentFeature2) {
          if (args.debug) {
            console.error("Missing feature constructor for", featureName);
          }
          continue;
        }
        const featureInstance = new ContentFeature2(featureName, importConfig, _features2, args);
        if (!featureInstance.getFeatureSettingEnabled("additionalCheck", "enabled")) {
          continue;
        }
        featureInstance.callLoad();
        _features2[featureName] = featureInstance;
      }
    }
    mark.end();
  }
  async function getFeatures() {
    await Promise.all(Object.entries(_features2));
    return _features2;
  }
  async function init(args) {
    const mark = performanceMonitor.mark("init");
    initArgs = args;
    if (!isHTMLDocument) {
      return;
    }
    if (args.messageSecret) {
      registerMessageSecret(args.messageSecret);
    }
    initStringExemptionLists(args);
    const features = await getFeatures();
    await Promise.allSettled(
      Object.entries(features).map(async ([featureName, featureInstance]) => {
        if (!isFeatureBroken(args, featureName) || alwaysInitExtensionFeatures(args, featureName)) {
          if (!featureInstance.getFeatureSettingEnabled("additionalCheck", "enabled")) {
            featureInstance.markFeatureAsSkipped("additionalCheck disabled");
            return;
          }
          await featureInstance.callInit(args);
          const hasUrlChangedMethod = "urlChanged" in featureInstance && typeof featureInstance.urlChanged === "function";
          if (featureInstance.listenForUrlChanges || hasUrlChangedMethod) {
            registerForURLChanges((navigationType) => {
              featureInstance.recomputeSiteObject();
              if (hasUrlChangedMethod) {
                featureInstance.urlChanged(navigationType);
              }
            });
          }
        } else {
          featureInstance.markFeatureAsSkipped("feature is broken or disabled on this site");
        }
      })
    );
    while (updates.length) {
      const update = updates.pop();
      await updateFeaturesInner(update);
    }
    mark.end();
    if (args.debug) {
      performanceMonitor.measureAll();
    }
  }
  async function updateFeatureArgs(updatedArgs) {
    if (!isHTMLDocument) {
      return;
    }
    const features = await getFeatures();
    Object.values(features).forEach((featureInstance) => {
      if (featureInstance && featureInstance.listenForConfigUpdates) {
        if (typeof featureInstance.setArgs === "function") {
          featureInstance.setArgs(updatedArgs);
        }
        if (typeof featureInstance.onUserPreferencesMerged === "function") {
          featureInstance.onUserPreferencesMerged(updatedArgs);
        }
      }
    });
  }
  function alwaysInitExtensionFeatures(args, featureName) {
    return args.platform.name === "extension" && alwaysInitFeatures.has(featureName);
  }
  async function updateFeaturesInner(args) {
    const features = await getFeatures();
    Object.entries(features).forEach(([featureName, featureInstance]) => {
      if (initArgs && !isFeatureBroken(initArgs, featureName) && featureInstance.listenForUpdateChanges) {
        featureInstance.update(args);
      }
    });
  }

  // entry-points/android-adsjs.js
  async function sendInitialPingAndUpdate(messagingConfig, processedConfig) {
    if (isBeingFramed()) {
      return;
    }
    try {
      const messagingContext = new MessagingContext({
        context: processedConfig.messagingContextName,
        env: processedConfig.debug ? "development" : "production",
        featureName: "messaging"
      });
      const messaging = new Messaging(messagingContext, messagingConfig);
      if (processedConfig.debug) {
        console.log("AndroidAdsjs: Sending initial ping...");
      }
      const response = await messaging.request("initialPing", {});
      if (response && typeof response === "object") {
        const updatedConfig = { ...processedConfig, ...response };
        await updateFeatureArgs(updatedConfig);
      }
    } catch (error) {
      if (processedConfig.debug) {
        console.error("AndroidAdsjs: Initial ping failed:", error);
      }
    }
  }
  function initCode() {
    const config = $CONTENT_SCOPE$;
    const userUnprotectedDomains = $USER_UNPROTECTED_DOMAINS$;
    const userPreferences = $USER_PREFERENCES$;
    const processedConfig = (
      /** @type {ProcessedConfig} */
      processConfig(config, userUnprotectedDomains, userPreferences)
    );
    const configConstruct = processedConfig;
    const objectName = configConstruct.objectName || "contentScopeAdsjs";
    const messagingConfig = new AndroidAdsjsMessagingConfig({
      objectName,
      target: globalThis,
      debug: processedConfig.debug ?? false
    });
    processedConfig.messagingConfig = messagingConfig;
    sendInitialPingAndUpdate(messagingConfig, processedConfig);
    load(getLoadArgs(processedConfig));
    init(processedConfig);
  }
  initCode();
})();
