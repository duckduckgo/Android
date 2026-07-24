/*! © DuckDuckGo ContentScopeScripts android-broker-protection https://github.com/duckduckgo/content-scope-scripts/ */
"use strict";
(() => {
  var __create = Object.create;
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __getProtoOf = Object.getPrototypeOf;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __typeError = (msg) => {
    throw TypeError(msg);
  };
  var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
  var __require = /* @__PURE__ */ ((x2) => typeof require !== "undefined" ? require : typeof Proxy !== "undefined" ? new Proxy(x2, {
    get: (a2, b2) => (typeof require !== "undefined" ? require : a2)[b2]
  }) : x2)(function(x2) {
    if (typeof require !== "undefined") return require.apply(this, arguments);
    throw Error('Dynamic require of "' + x2 + '" is not supported');
  });
  var __esm = (fn, res, err) => function __init() {
    if (err) throw err[0];
    try {
      return fn && (res = (0, fn[__getOwnPropNames(fn)[0]])(fn = 0)), res;
    } catch (e) {
      throw err = [e], e;
    }
  };
  var __commonJS = (cb, mod) => function __require2() {
    try {
      return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
    } catch (e) {
      throw mod = 0, e;
    }
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
  var __accessCheck = (obj, member, msg) => member.has(obj) || __typeError("Cannot " + msg);
  var __privateGet = (obj, member, getter) => (__accessCheck(obj, member, "read from private field"), getter ? getter.call(obj) : member.get(obj));
  var __privateAdd = (obj, member, value) => member.has(obj) ? __typeError("Cannot add the same private member more than once") : member instanceof WeakSet ? member.add(obj) : member.set(obj, value);
  var __privateSet = (obj, member, value, setter) => (__accessCheck(obj, member, "write to private field"), setter ? setter.call(obj, value) : member.set(obj, value), value);
  var __privateMethod = (obj, member, method) => (__accessCheck(obj, member, "access private method"), method);

  // <define:import.meta.trackerLookup>
  var define_import_meta_trackerLookup_default;
  var init_define_import_meta_trackerLookup = __esm({
    "<define:import.meta.trackerLookup>"() {
      define_import_meta_trackerLookup_default = { com: { "2020mustang": 1, "33across": 1, "360yield": 1, "3lift": 1, "4dsply": 1, "9gtb": 1, "a-mx": 1, aaxads: 1, abtasty: 1, acsbapp: 1, acscdn: 1, acuityplatform: 1, "ad-score": 1, adalyser: 1, adapf: 1, adara: 1, addtoany: 1, adelixir: 1, adentifi: 1, adgrx: 1, adhese: 1, adition: 1, adkernel: 1, adlightning: 1, admanmedia: 1, admedo: 1, adnxs: 1, adobedtm: 1, adpone: 1, adpushup: 1, adroll: 1, "ads-twitter": 1, adsafeprotected: 1, adswizz: 1, adthrive: 1, adtng: 1, adultfriendfinder: 1, adventive: 1, adyen: 1, aegpresents: 1, affinity: 1, affirm: 1, agilone: 1, agkn: 1, agoda: 1, ahrefs: 1, aimbase: 1, albacross: 1, alcmpn: 1, "alia-prod": 1, alicdn: 1, aliyuncs: 1, alocdn: 1, "amazon-adsystem": 1, amazon: 1, amplitude: 1, amspbs: 1, amxrtb: 1, "analytics-egain": 1, aniview: 1, anymind360: 1, "app-us1": 1, appboycdn: 1, "apple-mapkit": 1, applovin: 1, aswpsdkus: 1, atlassian: 1, atmtd: 1, attntags: 1, audioeye: 1, "automizely-analytics": 1, automizely: 1, avantlink: 1, aweber: 1, awswaf: 1, azure: 1, b2c: 1, batch: 1, bc0a: 1, beehiiv: 1, betweendigital: 1, bfmio: 1, bing: 1, bizrate: 1, blis: 1, blismedia: 1, blogherads: 1, bloomreach: 1, bluecava: 1, booking: 1, boomtrain: 1, bounceexchange: 1, bqstreamer: 1, brainlyads: 1, "brand-display": 1, brandmetrics: 1, brealtime: 1, browsiprod: 1, btloader: 1, bttrack: 1, btttag: 1, bugsnag: 1, buzzoola: 1, byspotify: 1, c1904584f2: 1, callrail: 1, campaigner: 1, canva: 1, "captcha-delivery": 1, cartstack: 1, casalemedia: 1, "cdn-net": 1, cdninstagram: 1, cdnwidget: 1, channeladvisor: 1, chartbeat: 1, chaseherbalpasty: 1, chatango: 1, chaturbate: 1, cheqzone: 1, "ck-ie": 1, clearbit: 1, clickagy: 1, clicktripz: 1, clientgear: 1, cloudflare: 1, cloudflareinsights: 1, cnn: 1, cnzz: 1, codedrink: 1, cognitivlabs: 1, collegenet: 1, comm100: 1, "company-target": 1, "config-security": 1, connatix: 1, consentmo: 1, contentabc: 1, contextweb: 1, convertexperiments: 1, convertkit: 1, "cookie-script": 1, coosync: 1, cootlogix: 1, copper6: 1, coveo: 1, cquotient: 1, crazyegg: 1, crcldu: 1, "creative-serving": 1, creativecdn: 1, criteo: 1, ctctcdn: 1, ctnsnet: 1, cudasvc: 1, cxense: 1, dailymotion: 1, "datadoghq-browser-agent": 1, deadlinefunnel: 1, dealerinspire: 1, deepintent: 1, delivra: 1, demandbase: 1, dianomi: 1, digitaloceanspaces: 1, dimelochat: 1, discover: 1, disqus: 1, docaccess: 1, dotomi: 1, doubleverify: 1, driftt: 1, dtscout: 1, dwin1: 1, dynamicyield: 1, dynatrace: 1, ebxcdn: 1, eccmp: 1, elfsight: 1, eloqua: 1, emxdgt: 1, en25: 1, endowmentoverhangutmost: 1, ensighten: 1, epichosted: 1, eskimi: 1, evergage: 1, evgnet: 1, evidon: 1, exdynsrv: 1, exelator: 1, exoclick: 1, exosrv: 1, exponea: 1, exponential: 1, extole: 1, ezodn: 1, ezoicanalytics: 1, ezojs: 1, feefo: 1, five9: 1, flashtalking: 1, flippback: 1, flixcdn: 1, flodesk: 1, foresee: 1, fouanalytics: 1, freshworks: 1, fullstory: 1, g2: 1, "gannett-cdn": 1, gbqofs: 1, getcandid: 1, getdrip: 1, getelevar: 1, getrockerbox: 1, getsitecontrol: 1, godaddy: 1, "google-analytics": 1, google: 1, googleadservices: 1, googlehosted: 1, googleoptimize: 1, googlesyndication: 1, googletagmanager: 1, googletagservices: 1, greylabeldelivery: 1, groovehq: 1, gsitrix: 1, gstatic: 1, "guarantee-cdn": 1, gumgum: 1, hawksearch: 1, hearstnp: 1, heatmap: 1, hextom: 1, histats: 1, hotjar: 1, "hrzn-nxt": 1, "hs-banner": 1, "hs-scripts": 1, htlbid: 1, "html-load": 1, hubspot: 1, hubspotfeedback: 1, iadvize: 1, "id5-sync": 1, igodigital: 1, iljmp: 1, impact: 1, impactcdn: 1, "impactradius-event": 1, improvedcontactform: 1, improvedigital: 1, imrworldwide: 1, indexww: 1, infolinks: 1, infusionsoft: 1, inmobi: 1, inq: 1, "inside-graph": 1, instagram: 1, intentiq: 1, intergient: 1, intuit: 1, investingchannel: 1, invocacdn: 1, iperceptions: 1, iplsc: 1, ipredictive: 1, iqm: 1, issuu: 1, iteratehq: 1, janusaent: 1, jimstatic: 1, journeymv: 1, journity: 1, amazonaws: { "us-west-2": { s3: 1 }, "ap-southeast-2": 1, "eu-west-3": { s3: { "yelda-chat": 1 } } }, juicyads: 1, jwplayer: 1, kargo: 1, ketchcdn: 1, kettledroopingcontinuation: 1, kit: 1, klarna: 1, klaviyo: 1, krushmedia: 1, kueezrtb: 1, ladesk: 1, ladsp: 1, leadsrx: 1, lendingtree: 1, lexisnexis: 1, liadm: 1, licdn: 1, liftdsp: 1, lightboxcdn: 1, lijit: 1, linkconnector: 1, linkedin: 1, linksynergy: 1, "list-manage": 1, listrakbi: 1, livechatinc: 1, lngtdv: 1, localytics: 1, loggly: 1, loop11: 1, luckyorange: 1, magsrv: 1, mailchimp: 1, mailchimpapp: 1, mailerlite: 1, "maillist-manage": 1, "mantis-intelligence": 1, marketiq: 1, marketo: 1, marphezis: 1, marzaent: 1, matheranalytics: 1, mathtag: 1, mavrtracktor: 1, maxmind: 1, mczbf: 1, measureadv: 1, medallia: 1, media6degrees: 1, mediarithmics: 1, mediavine: 1, memberful: 1, mercari: 1, mfadsrvr: 1, mgid: 1, micpn: 1, "minutemedia-prebid": 1, mixpo: 1, mktoresp: 1, mktoweb: 1, ml314: 1, mmvideocdn: 1, moloco: 1, monsido: 1, mookie1: 1, mountain: 1, mouseflow: 1, mpeasylink: 1, mql5: 1, mxpnl: 1, mygaru: 1, myregistry: 1, newrelic: 1, newscgp: 1, nextdoor: 1, nextmillmedia: 1, nitropay: 1, noibu: 1, nosto: 1, npttech: 1, nuance: 1, nytrng: 1, omappapi: 1, omguk: 1, omnisnippet1: 1, omnisrc: 1, omnitagjs: 1, onaudience: 1, onesignal: 1, "onetag-sys": 1, opecloud: 1, opentable: 1, opentext: 1, openwebmp: 1, opera: 1, opmnstr: 1, "opti-digital": 1, optidigital: 1, optimizely: 1, optimonk: 1, optmnstr: 1, optmstr: 1, optnmnstr: 1, optnmstr: 1, optum: 1, oraclecloud: 1, orbsrv: 1, osano: 1, outbrain: 1, outbrainimg: 1, ownlocal: 1, parastorage: 1, pardot: 1, parsely: 1, patreon: 1, paypal: 1, pbstck: 1, "peer-39": 1, pemsrv: 1, perfalytics: 1, perfdrive: 1, permutive: 1, picreel: 1, pinterest: 1, pippio: 1, pixlee: 1, playwire: 1, postaffiliatepro: 1, posthog: 1, postrelease: 1, preferencenail: 1, pricespider: 1, protrafficinspector: 1, providesupport: 1, ptengine: 1, publir: 1, publitas: 1, pubmatic: 1, pubnation: 1, pulseinsights: 1, pulselive: 1, pushnami: 1, qq: 1, qualaroo: 1, qualified: 1, qualtrics: 1, quantcount: 1, quantserve: 1, quantummetric: 1, quora: 1, rakuten: 1, raptivecdn: 1, realizationnewestfangs: 1, realsrv: 1, rebuyengine: 1, recombee: 1, recruitics: 1, reddit: 1, redditstatic: 1, refinery89: 1, reson8: 1, responsiveads: 1, retargetly: 1, revcontent: 1, rezync: 1, rfihub: 1, rfksrv: 1, richaudience: 1, ringcentral: 1, rkdms: 1, rlcdn: 1, rmhfrtnd: 1, rmtag: 1, roeyecdn: 1, rogersmedia: 1, route: 1, rtbhouse: 1, rubiconproject: 1, rudderlabs: 1, rudderstack: 1, rvlife: 1, "sail-horizon": 1, sailthru: 1, salecycle: 1, "salesforce-sites": 1, salesforceliveagent: 1, sascdn: 1, scene7: 1, scholarlyiq: 1, schwab: 1, scorecardresearch: 1, screenpopper: 1, scriptwrapper: 1, searchserverapi1: 1, securedvisit: 1, seedtag: 1, segment: 1, servenobid: 1, serverbid: 1, herokuapp: { "session-recording-now": 1 }, sharethis: 1, sharethrough: 1, "shb-sync": 1, shgcdn3: 1, sibautomation: 1, signifyd: 1, site: 1, siteimprove: 1, siteimproveanalytics: 1, sitescout: 1, skimresources: 1, slickstream: 1, smartadserver: 1, "smartnews-ads": 1, smilewanted: 1, snapchat: 1, snigelweb: 1, socdm: 1, sojern: 1, sonobi: 1, sparteo: 1, spendsdetachment: 1, sportradar: 1, sportradarserving: 1, sportslocalmedia: 1, springserve: 1, stackadapt: 1, startappnetwork: 1, statcounter: 1, stay22: 1, googleapis: { storage: 1, imasdk: 1, ajax: 1, fonts: 1, translate: 1 }, streamrail: 1, stripchat: 1, sumome: 1, swaven: 1, swymrelay: 1, syf: 1, symantec: 1, syncingbridge: 1, taboola: 1, talkable: 1, taobao: 1, tapad: 1, tapioni: 1, tappx: 1, "teads-xo": 1, tealiumiq: 1, temu: 1, "the-ozone-project": 1, theadex: 1, thejobnetwork: 1, thestar: 1, thrtle: 1, tiktok: 1, tinypass: 1, tiqcdn: 1, trackjs: 1, trafficjunky: 1, travelaudience: 1, travelpayouts: 1, treasuredata: 1, tremorhub: 1, trendemon: 1, tribalfusion: 1, trueleadid: 1, trustedstack: 1, trustpielote: 1, trustpilot: 1, trvdp: 1, tsyndicate: 1, turn: 1, tvpixel: 1, tvspix: 1, tvsquared: 1, tweakwise: 1, twitter: 1, tynt: 1, uidapi: 1, unbxdapi: 1, undertone: 1, unpkg: 1, unrulymedia: 1, "uplift-platform": 1, uplift: 1, upsellit: 1, urbanairship: 1, usabilla: 1, usablenet: 1, usbrowserspeed: 1, useamp: 1, usemessages: 1, userapi: 1, uservoice: 1, valuecommerce: 1, "verint-cdn": 1, vidazoo: 1, videoplayerhub: 1, vidoomy: 1, vimeocdn: 1, vistarsagency: 1, "visually-io": 1, visualwebsiteoptimizer: 1, vk: 1, vrtcal: 1, wbd: 1, "we-stats": 1, webcontentassessor: 1, webengage: 1, webeyez: 1, webtraxs: 1, "webtrends-optimize": 1, webtrends: 1, weglot: 1, woosmap: 1, workdeadlinededicate: 1, "wt-safetag": 1, wysistat: 1, x: 1, yahoo: 1, yandex: 1, yango: 1, yieldlove: 1, yieldmo: 1, ymmobi: 1, yotpo: 1, "youtube-nocookie": 1, youtube: 1, zemanta: 1, zendesk: 1, zeotap: 1, "zi-scripts": 1, zohocdn: 1, zoologyfibre: 1, zoominfo: 1, zopim: 1, createsend1: 1, jivox: 1, klarnaservices: 1, solarwinds: 1, ivitrack: 1, kiyoh: 1, adnuntius: 1, schibsted: 1, facebook: 1, attentivemobile: 1, bootstrapcdn: 1, cloudinary: 1, cookieyes: 1, dtscdn: 1, fontawesome: 1, getclicky: 1, microsoft: 1, playdigo: 1, roeye: 1, rtbwise: 1, shopifycdn: 1, shopifysvc: 1, stripe: 1, vimeo: 1, wp: 1, yimg: 1, yandexmetrica: 1, ymetrica1: 1 }, net: { "2mdn": 1, "a-mo": 1, acint: 1, adform: 1, adhigh: 1, adobedc: 1, adspeed: 1, aggle: 1, appier: 1, edgekey: { au: 1, "com-v1": 1, de: 1, fr: 1, io: 1, net: 1, nl: 1, org: 1, com: { scene7: 1 }, "com-v2": 1 }, azurefd: 1, bannerflow: 1, basis: 1, "bf-ad": 1, bidswitch: 1, blueconic: 1, buysellads: 1, cachefly: 1, ccgateway: 1, "confiant-integrations": 1, contentpass: 1, contentsquare: 1, criteo: 1, crwdcntrl: 1, cloudfront: { d14jnfavjicsbe: 1, d1af033869koo7: 1, d1j0xlutvd326g: 1, d1vg5xiq7qffdj: 1, d1x4rwm1kh8pnu: 1, d21gpk1vhmjuf5: 1, d2trly8m2h0e8p: 1, d38xvr37kwwhcm: 1, d3djbgmgf0l8ev: 1, d3fukwxve5r8zf: 1, d3fv2pqyjay52z: 1, d3nn82uaxijpm6: 1, d6tizftlrpuof: 1, dm2q9qfzyjfox: 1, dokumfe7mps0i: 1, dsh7ky7308k4b: 1, duube1y6ojsji: 1, dvagh3p3rk8xj: 1, d2638j3z8ek976: 1 }, datatrac: 1, demdex: 1, dotmetrics: 1, doubleclick: 1, "e-planning": 1, episerver: 1, esm1: 1, eulerian: 1, everestjs: 1, everesttech: 1, eyeota: 1, ezoic: 1, facebook: 1, fastclick: 1, fbcdn: 1, fonts: 1, fuseplatform: 1, fwmrm: 1, gcprivacy: 1, "go-mpulse": 1, "go-vip": 1, gtranslate: 1, hadronid: 1, "hs-analytics": 1, hsadspixel: 1, hsappstatic: 1, hscta: 1, "im-apps": 1, impervadns: 1, fastly: { global: { sni: { j: 1, m: 1, s: 1 } } }, jsdelivr: 1, kakaocdn: 1, listhub: 1, livedoor: 1, liveperson: 1, lpsnmedia: 1, magnetmail: 1, marketo: 1, mateti: 1, media: 1, mjedge: 1, mobon: 1, monetate: 1, mrktmtrcs: 1, mxptint: 1, naver: 1, "nr-data": 1, ojrq: 1, omtrdc: 1, onecount: 1, openx: 1, openxcdn: 1, opta: 1, optimove: 1, p7cloud: 1, pages02: 1, pages03: 1, pages04: 1, pages05: 1, pages06: 1, pages08: 1, pingdom: 1, pmdstatic: 1, popcash: 1, "pro-market": 1, protechts: 1, akamaihd: { "pxlclnmdecom-a": 1 }, r9cdn: 1, rfihub: 1, akamaized: { s13emagst: 1, tmssl: 1 }, sancdn: 1, "sc-static": 1, semasio: 1, sexad: 1, smaato: 1, spreadshirts: 1, tfaforms: 1, uuidksinc: 1, viafoura: 1, visx: 1, w55c: 1, witglobal: 1, "wt-eu02": 1, yandex: 1, yastatic: 1, zencdn: 1, zetaglobal: 1, zucks: 1, emarsys: 1, apicit: 1, tradetracker: 1, "ad-delivery": 1, bkcdn: 1, chartbeat: 1, ortb: 1, eviltracker: 1 }, io: { "506": 1, "4dex": 1, adapex: 1, aditude: 1, adnami: 1, advolve: 1, aidata: 1, anonm: 1, anonymised: 1, bidr: 1, branch: 1, center: 1, chatra: 1, connectad: 1, cordial: 1, edgetag: 1, extole: 1, fsrv: 1, grsm: 1, hbrd: 1, instana: 1, intelligems: 1, juicer: 1, kameleoon: 1, karte: 1, litix: 1, lytics: 1, mediago: 1, missena: 1, mrf: 1, nexx360: 1, northbeam: 1, ntv: 1, optad360: 1, oracleinfinity: 1, "p-n": 1, personalizer: 1, pghub: 1, piano: 1, postscript: 1, powr: 1, presage: 1, rapidedge: 1, receptivity: 1, searchspring: 1, segment: 1, smct: 1, smile: 1, sspinc: 1, stape: 1, t13: 1, termly: 1, wovn: 1, yellowblue: 1, zprk: 1, pzz: 1, "1rx": 1, akstat: 1, digitalaudience: 1, hotjar: 1, "inmobi-choice": 1, pinklion: 1 }, co: { "6sc": 1, ayads: 1, clinch: 1, empowerlocal: 1, getlasso: 1, idio: 1, increasingly: 1, jads: 1, nc0: 1, optable: 1, "pm-serv": 1, prmutv: 1, t: 1, tctm: 1, tidio: 1, ujet: 1, vibe: 1, zip: 1 }, gt: { ad: 1 }, cloud: { aditude: 1, stpd: 1, squiz: 1 }, cc: { admaster: 1, "html-load": 1 }, jp: { admatrix: 1, fout: 1, ne: { hatena: 1 }, "impact-ad": 1, nakanohito: 1, ptengine: 1, r10s: 1, co: { rakuten: 1, yahoo: 1 }, rtoaster: 1, shinobi: 1, "team-rec": 1, uncn: 1, yimg: 1 }, pl: { adocean: 1, gemius: 1, nsaudience: 1, onet: 1, salesmanago: 1, wp: 1 }, re: { adsco: 1 }, org: { adsrvr: 1, ampproject: 1, "browser-update": 1, cleantalk: 1, featureassets: 1, flowplayer: 1, openstreetmap: 1, "privacy-center": 1, webvisor: 1, framasoft: 1, prodregistryv2: 1, "do-not-tracker": 1, trackersimulator: 1 }, biz: { adtarget: 1 }, google: { adtrafficquality: 1 }, tv: { attn: 1, iris: 1, ispot: 1, teads: 1, twitch: 1 }, de: { "auswaertiges-amt": 1, ioam: 1, itzbund: 1, stroeerdigitalgroup: 1 }, ai: { axon: 1, blackcrow: 1, dxtech: 1, evolv: 1, hybrid: 1, m2: 1, nrich: 1, programmaticx: 1, sardine: 1, wknd: 1 }, delivery: { ay: 1, monu: 1 }, ws: { bids: 1, rmbl: 1 }, ms: { clarity: 1 }, my: { cnt: 1 }, chat: { crisp: 1, gorgias: 1 }, gov: { dhs: 1, digitalgov: 1, weather: 1 }, ru: { digitaltarget: 1, mail: 1, megafon: 1, mts: 1, rambler: 1, top100: 1, yadro: 1, yandex: 1 }, nl: { dpgmedia: 1, rijksoverheid: 1 }, tech: { dv: 1, ingage: 1, primis: 1, yads: 1 }, es: { gaug: 1, pandect: 1 }, ca: { bc: { gov: 1 } }, me: { grow: 1, loopme: 1, tldw: 1 }, media: { grv: 1, nextday: 1, townsquare: 1, underdog: 1 }, health: { hcn: 1 }, page: { hlx: 1 }, cz: { imedia: 1, performax: 1, seznam: 1 }, app: { infusionsoft: 1, permutive: 1, shop: 1, run: { "us-central1": 1 }, web: { "wec-virtualassistant-cx-prod": 1 } }, live: { iqzonertb: 1 }, br: { com: { jsuol: 1 } }, eu: { kameleoon: 1, medallia: 1, rqtrk: 1, slgnt: 1, media01: 1, trengo: 1 }, services: { marketingautomation: 1 }, sg: { mediacorp: 1 }, info: { navistechnologies: 1, usergram: 1, webantenna: 1 }, au: { com: { news: 1, nine: 1, zipmoney: 1 } }, bi: { newsroom: 1 }, fr: { "open-system": 1 }, fm: { pdst: 1 }, pro: { piwik: 1, usocial: 1 }, it: { plug: 1, stbm: 1 }, network: { pub: 1 }, ch: { "ringier-advertising": 1, admin: 1, "da-services": 1 }, fi: { satis: 1, simpli: 1 }, ac: { script: 1 }, pe: { shop: 1 }, us: { shopmy: 1, tiktokw: 1, trkn: 1, zoom: 1 }, xyz: { tracookiepixel: 1 }, digital: { postmedia: 1 }, no: { acdn: 1, api: 1 }, events: { growplow: 1 }, goog: { "merchant-center-analytics": 1 }, example: { "ad-company": 1 }, site: { "ad-company": 1, "third-party": { bad: 1, broken: 1 } } };
    }
  });

  // ../node_modules/xregexp/src/xregexp.js
  var require_xregexp = __commonJS({
    "../node_modules/xregexp/src/xregexp.js"(exports, module) {
      "use strict";
      init_define_import_meta_trackerLookup();
      /*!
       * XRegExp 3.2.0
       * <xregexp.com>
       * Steven Levithan (c) 2007-2017 MIT License
       */
      var REGEX_DATA = "xregexp";
      var features = {
        astral: false,
        natives: false
      };
      var nativ = {
        exec: RegExp.prototype.exec,
        test: RegExp.prototype.test,
        match: String.prototype.match,
        replace: String.prototype.replace,
        split: String.prototype.split
      };
      var fixed = {};
      var regexCache = {};
      var patternCache = {};
      var tokens = [];
      var defaultScope = "default";
      var classScope = "class";
      var nativeTokens = {
        // Any native multicharacter token in default scope, or any single character
        "default": /\\(?:0(?:[0-3][0-7]{0,2}|[4-7][0-7]?)?|[1-9]\d*|x[\dA-Fa-f]{2}|u(?:[\dA-Fa-f]{4}|{[\dA-Fa-f]+})|c[A-Za-z]|[\s\S])|\(\?(?:[:=!]|<[=!])|[?*+]\?|{\d+(?:,\d*)?}\??|[\s\S]/,
        // Any native multicharacter token in character class scope, or any single character
        "class": /\\(?:[0-3][0-7]{0,2}|[4-7][0-7]?|x[\dA-Fa-f]{2}|u(?:[\dA-Fa-f]{4}|{[\dA-Fa-f]+})|c[A-Za-z]|[\s\S])|[\s\S]/
      };
      var replacementToken = /\$(?:{([\w$]+)}|(\d\d?|[\s\S]))/g;
      var correctExecNpcg = nativ.exec.call(/()??/, "")[1] === void 0;
      var hasFlagsProp = /x/.flags !== void 0;
      var toString = {}.toString;
      function hasNativeFlag(flag) {
        var isSupported = true;
        try {
          new RegExp("", flag);
        } catch (exception) {
          isSupported = false;
        }
        return isSupported;
      }
      var hasNativeU = hasNativeFlag("u");
      var hasNativeY = hasNativeFlag("y");
      var registeredFlags = {
        g: true,
        i: true,
        m: true,
        u: hasNativeU,
        y: hasNativeY
      };
      function augment(regex, captureNames, xSource, xFlags, isInternalOnly) {
        var p;
        regex[REGEX_DATA] = {
          captureNames
        };
        if (isInternalOnly) {
          return regex;
        }
        if (regex.__proto__) {
          regex.__proto__ = XRegExp.prototype;
        } else {
          for (p in XRegExp.prototype) {
            regex[p] = XRegExp.prototype[p];
          }
        }
        regex[REGEX_DATA].source = xSource;
        regex[REGEX_DATA].flags = xFlags ? xFlags.split("").sort().join("") : xFlags;
        return regex;
      }
      function clipDuplicates(str) {
        return nativ.replace.call(str, /([\s\S])(?=[\s\S]*\1)/g, "");
      }
      function copyRegex(regex, options) {
        if (!XRegExp.isRegExp(regex)) {
          throw new TypeError("Type RegExp expected");
        }
        var xData = regex[REGEX_DATA] || {};
        var flags = getNativeFlags(regex);
        var flagsToAdd = "";
        var flagsToRemove = "";
        var xregexpSource = null;
        var xregexpFlags = null;
        options = options || {};
        if (options.removeG) {
          flagsToRemove += "g";
        }
        if (options.removeY) {
          flagsToRemove += "y";
        }
        if (flagsToRemove) {
          flags = nativ.replace.call(flags, new RegExp("[" + flagsToRemove + "]+", "g"), "");
        }
        if (options.addG) {
          flagsToAdd += "g";
        }
        if (options.addY) {
          flagsToAdd += "y";
        }
        if (flagsToAdd) {
          flags = clipDuplicates(flags + flagsToAdd);
        }
        if (!options.isInternalOnly) {
          if (xData.source !== void 0) {
            xregexpSource = xData.source;
          }
          if (xData.flags != null) {
            xregexpFlags = flagsToAdd ? clipDuplicates(xData.flags + flagsToAdd) : xData.flags;
          }
        }
        regex = augment(
          new RegExp(options.source || regex.source, flags),
          hasNamedCapture(regex) ? xData.captureNames.slice(0) : null,
          xregexpSource,
          xregexpFlags,
          options.isInternalOnly
        );
        return regex;
      }
      function dec(hex2) {
        return parseInt(hex2, 16);
      }
      function getContextualTokenSeparator(match, scope, flags) {
        if (
          // No need to separate tokens if at the beginning or end of a group
          match.input.charAt(match.index - 1) === "(" || match.input.charAt(match.index + match[0].length) === ")" || // Avoid separating tokens when the following token is a quantifier
          isPatternNext(match.input, match.index + match[0].length, flags, "[?*+]|{\\d+(?:,\\d*)?}")
        ) {
          return "";
        }
        return "(?:)";
      }
      function getNativeFlags(regex) {
        return hasFlagsProp ? regex.flags : (
          // Explicitly using `RegExp.prototype.toString` (rather than e.g. `String` or concatenation
          // with an empty string) allows this to continue working predictably when
          // `XRegExp.proptotype.toString` is overridden
          nativ.exec.call(/\/([a-z]*)$/i, RegExp.prototype.toString.call(regex))[1]
        );
      }
      function hasNamedCapture(regex) {
        return !!(regex[REGEX_DATA] && regex[REGEX_DATA].captureNames);
      }
      function hex(dec2) {
        return parseInt(dec2, 10).toString(16);
      }
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
      function isPatternNext(pattern, pos, flags, needlePattern) {
        var inlineCommentPattern = "\\(\\?#[^)]*\\)";
        var lineCommentPattern = "#[^#\\n]*";
        var patternsToIgnore = flags.indexOf("x") > -1 ? (
          // Ignore any leading whitespace, line comments, and inline comments
          ["\\s", lineCommentPattern, inlineCommentPattern]
        ) : (
          // Ignore any leading inline comments
          [inlineCommentPattern]
        );
        return nativ.test.call(
          new RegExp("^(?:" + patternsToIgnore.join("|") + ")*(?:" + needlePattern + ")"),
          pattern.slice(pos)
        );
      }
      function isType(value, type) {
        return toString.call(value) === "[object " + type + "]";
      }
      function pad4(str) {
        while (str.length < 4) {
          str = "0" + str;
        }
        return str;
      }
      function prepareFlags(pattern, flags) {
        var i;
        if (clipDuplicates(flags) !== flags) {
          throw new SyntaxError("Invalid duplicate regex flag " + flags);
        }
        pattern = nativ.replace.call(pattern, /^\(\?([\w$]+)\)/, function($0, $1) {
          if (nativ.test.call(/[gy]/, $1)) {
            throw new SyntaxError("Cannot use flag g or y in mode modifier " + $0);
          }
          flags = clipDuplicates(flags + $1);
          return "";
        });
        for (i = 0; i < flags.length; ++i) {
          if (!registeredFlags[flags.charAt(i)]) {
            throw new SyntaxError("Unknown regex flag " + flags.charAt(i));
          }
        }
        return {
          pattern,
          flags
        };
      }
      function prepareOptions(value) {
        var options = {};
        if (isType(value, "String")) {
          XRegExp.forEach(value, /[^\s,]+/, function(match) {
            options[match] = true;
          });
          return options;
        }
        return value;
      }
      function registerFlag(flag) {
        if (!/^[\w$]$/.test(flag)) {
          throw new Error("Flag must be a single character A-Za-z0-9_$");
        }
        registeredFlags[flag] = true;
      }
      function runTokens(pattern, flags, pos, scope, context) {
        var i = tokens.length;
        var leadChar = pattern.charAt(pos);
        var result = null;
        var match;
        var t;
        while (i--) {
          t = tokens[i];
          if (t.leadChar && t.leadChar !== leadChar || t.scope !== scope && t.scope !== "all" || t.flag && flags.indexOf(t.flag) === -1) {
            continue;
          }
          match = XRegExp.exec(pattern, t.regex, pos, "sticky");
          if (match) {
            result = {
              matchLength: match[0].length,
              output: t.handler.call(context, match, scope, flags),
              reparse: t.reparse
            };
            break;
          }
        }
        return result;
      }
      function setAstral(on) {
        features.astral = on;
      }
      function setNatives(on) {
        RegExp.prototype.exec = (on ? fixed : nativ).exec;
        RegExp.prototype.test = (on ? fixed : nativ).test;
        String.prototype.match = (on ? fixed : nativ).match;
        String.prototype.replace = (on ? fixed : nativ).replace;
        String.prototype.split = (on ? fixed : nativ).split;
        features.natives = on;
      }
      function toObject(value) {
        if (value == null) {
          throw new TypeError("Cannot convert null or undefined to object");
        }
        return value;
      }
      function XRegExp(pattern, flags) {
        if (XRegExp.isRegExp(pattern)) {
          if (flags !== void 0) {
            throw new TypeError("Cannot supply flags when copying a RegExp");
          }
          return copyRegex(pattern);
        }
        pattern = pattern === void 0 ? "" : String(pattern);
        flags = flags === void 0 ? "" : String(flags);
        if (XRegExp.isInstalled("astral") && flags.indexOf("A") === -1) {
          flags += "A";
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
          var output = "";
          var pos = 0;
          var result;
          var applied = prepareFlags(pattern, flags);
          var appliedPattern = applied.pattern;
          var appliedFlags = applied.flags;
          while (pos < appliedPattern.length) {
            do {
              result = runTokens(appliedPattern, appliedFlags, pos, scope, context);
              if (result && result.reparse) {
                appliedPattern = appliedPattern.slice(0, pos) + result.output + appliedPattern.slice(pos + result.matchLength);
              }
            } while (result && result.reparse);
            if (result) {
              output += result.output;
              pos += result.matchLength || 1;
            } else {
              var token = XRegExp.exec(appliedPattern, nativeTokens[scope], pos, "sticky")[0];
              output += token;
              pos += token.length;
              if (token === "[" && scope === defaultScope) {
                scope = classScope;
              } else if (token === "]" && scope === classScope) {
                scope = defaultScope;
              }
            }
          }
          patternCache[pattern][flags] = {
            // Use basic cleanup to collapse repeated empty groups like `(?:)(?:)` to `(?:)`. Empty
            // groups are sometimes inserted during regex transpilation in order to keep tokens
            // separated. However, more than one empty group in a row is never needed.
            pattern: nativ.replace.call(output, /(?:\(\?:\))+/g, "(?:)"),
            // Strip all but native flags
            flags: nativ.replace.call(appliedFlags, /[^gimuy]+/g, ""),
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
      XRegExp.prototype = new RegExp();
      XRegExp.version = "3.2.0";
      XRegExp._clipDuplicates = clipDuplicates;
      XRegExp._hasNativeFlag = hasNativeFlag;
      XRegExp._dec = dec;
      XRegExp._hex = hex;
      XRegExp._pad4 = pad4;
      XRegExp.addToken = function(regex, handler, options) {
        options = options || {};
        var optionalFlags = options.optionalFlags;
        var i;
        if (options.flag) {
          registerFlag(options.flag);
        }
        if (optionalFlags) {
          optionalFlags = nativ.split.call(optionalFlags, "");
          for (i = 0; i < optionalFlags.length; ++i) {
            registerFlag(optionalFlags[i]);
          }
        }
        tokens.push({
          regex: copyRegex(regex, {
            addG: true,
            addY: hasNativeY,
            isInternalOnly: true
          }),
          handler,
          scope: options.scope || defaultScope,
          flag: options.flag,
          reparse: options.reparse,
          leadChar: options.leadChar
        });
        XRegExp.cache.flush("patterns");
      };
      XRegExp.cache = function(pattern, flags) {
        if (!regexCache[pattern]) {
          regexCache[pattern] = {};
        }
        return regexCache[pattern][flags] || (regexCache[pattern][flags] = XRegExp(pattern, flags));
      };
      XRegExp.cache.flush = function(cacheName) {
        if (cacheName === "patterns") {
          patternCache = {};
        } else {
          regexCache = {};
        }
      };
      XRegExp.escape = function(str) {
        return nativ.replace.call(toObject(str), /[-\[\]{}()*+?.,\\^$|#\s]/g, "\\$&");
      };
      XRegExp.exec = function(str, regex, pos, sticky) {
        var cacheKey = "g";
        var addY = false;
        var fakeY = false;
        var match;
        var r2;
        addY = hasNativeY && !!(sticky || regex.sticky && sticky !== false);
        if (addY) {
          cacheKey += "y";
        } else if (sticky) {
          fakeY = true;
          cacheKey += "FakeY";
        }
        regex[REGEX_DATA] = regex[REGEX_DATA] || {};
        r2 = regex[REGEX_DATA][cacheKey] || (regex[REGEX_DATA][cacheKey] = copyRegex(regex, {
          addG: true,
          addY,
          source: fakeY ? regex.source + "|()" : void 0,
          removeY: sticky === false,
          isInternalOnly: true
        }));
        pos = pos || 0;
        r2.lastIndex = pos;
        match = fixed.exec.call(r2, str);
        if (fakeY && match && match.pop() === "") {
          match = null;
        }
        if (regex.global) {
          regex.lastIndex = match ? r2.lastIndex : 0;
        }
        return match;
      };
      XRegExp.forEach = function(str, regex, callback) {
        var pos = 0;
        var i = -1;
        var match;
        while (match = XRegExp.exec(str, regex, pos)) {
          callback(match, ++i, str, regex);
          pos = match.index + (match[0].length || 1);
        }
      };
      XRegExp.globalize = function(regex) {
        return copyRegex(regex, { addG: true });
      };
      XRegExp.install = function(options) {
        options = prepareOptions(options);
        if (!features.astral && options.astral) {
          setAstral(true);
        }
        if (!features.natives && options.natives) {
          setNatives(true);
        }
      };
      XRegExp.isInstalled = function(feature) {
        return !!features[feature];
      };
      XRegExp.isRegExp = function(value) {
        return toString.call(value) === "[object RegExp]";
      };
      XRegExp.match = function(str, regex, scope) {
        var global = regex.global && scope !== "one" || scope === "all";
        var cacheKey = (global ? "g" : "") + (regex.sticky ? "y" : "") || "noGY";
        var result;
        var r2;
        regex[REGEX_DATA] = regex[REGEX_DATA] || {};
        r2 = regex[REGEX_DATA][cacheKey] || (regex[REGEX_DATA][cacheKey] = copyRegex(regex, {
          addG: !!global,
          removeG: scope === "one",
          isInternalOnly: true
        }));
        result = nativ.match.call(toObject(str), r2);
        if (regex.global) {
          regex.lastIndex = scope === "one" && result ? (
            // Can't use `r2.lastIndex` since `r2` is nonglobal in this case
            result.index + result[0].length
          ) : 0;
        }
        return global ? result || [] : result && result[0];
      };
      XRegExp.matchChain = function(str, chain) {
        return (function recurseChain(values, level) {
          var item = chain[level].regex ? chain[level] : { regex: chain[level] };
          var matches = [];
          function addMatch(match) {
            if (item.backref) {
              if (!(match.hasOwnProperty(item.backref) || +item.backref < match.length)) {
                throw new ReferenceError("Backreference to undefined group: " + item.backref);
              }
              matches.push(match[item.backref] || "");
            } else {
              matches.push(match[0]);
            }
          }
          for (var i = 0; i < values.length; ++i) {
            XRegExp.forEach(values[i], item.regex, addMatch);
          }
          return level === chain.length - 1 || !matches.length ? matches : recurseChain(matches, level + 1);
        })([str], 0);
      };
      XRegExp.replace = function(str, search, replacement, scope) {
        var isRegex = XRegExp.isRegExp(search);
        var global = search.global && scope !== "one" || scope === "all";
        var cacheKey = (global ? "g" : "") + (search.sticky ? "y" : "") || "noGY";
        var s2 = search;
        var result;
        if (isRegex) {
          search[REGEX_DATA] = search[REGEX_DATA] || {};
          s2 = search[REGEX_DATA][cacheKey] || (search[REGEX_DATA][cacheKey] = copyRegex(search, {
            addG: !!global,
            removeG: scope === "one",
            isInternalOnly: true
          }));
        } else if (global) {
          s2 = new RegExp(XRegExp.escape(String(search)), "g");
        }
        result = fixed.replace.call(toObject(str), s2, replacement);
        if (isRegex && search.global) {
          search.lastIndex = 0;
        }
        return result;
      };
      XRegExp.replaceEach = function(str, replacements) {
        var i;
        var r;
        for (i = 0; i < replacements.length; ++i) {
          r = replacements[i];
          str = XRegExp.replace(str, r[0], r[1], r[2]);
        }
        return str;
      };
      XRegExp.split = function(str, separator, limit) {
        return fixed.split.call(toObject(str), separator, limit);
      };
      XRegExp.test = function(str, regex, pos, sticky) {
        return !!XRegExp.exec(str, regex, pos, sticky);
      };
      XRegExp.uninstall = function(options) {
        options = prepareOptions(options);
        if (features.astral && options.astral) {
          setAstral(false);
        }
        if (features.natives && options.natives) {
          setNatives(false);
        }
      };
      XRegExp.union = function(patterns, flags, options) {
        options = options || {};
        var conjunction = options.conjunction || "or";
        var numCaptures = 0;
        var numPriorCaptures;
        var captureNames;
        function rewrite(match, paren, backref) {
          var name = captureNames[numCaptures - numPriorCaptures];
          if (paren) {
            ++numCaptures;
            if (name) {
              return "(?<" + name + ">";
            }
          } else if (backref) {
            return "\\" + (+backref + numPriorCaptures);
          }
          return match;
        }
        if (!(isType(patterns, "Array") && patterns.length)) {
          throw new TypeError("Must provide a nonempty array of patterns to merge");
        }
        var parts = /(\()(?!\?)|\\([1-9]\d*)|\\[\s\S]|\[(?:[^\\\]]|\\[\s\S])*\]/g;
        var output = [];
        var pattern;
        for (var i = 0; i < patterns.length; ++i) {
          pattern = patterns[i];
          if (XRegExp.isRegExp(pattern)) {
            numPriorCaptures = numCaptures;
            captureNames = pattern[REGEX_DATA] && pattern[REGEX_DATA].captureNames || [];
            output.push(nativ.replace.call(XRegExp(pattern.source).source, parts, rewrite));
          } else {
            output.push(XRegExp.escape(pattern));
          }
        }
        var separator = conjunction === "none" ? "" : "|";
        return XRegExp(output.join(separator), flags);
      };
      fixed.exec = function(str) {
        var origLastIndex = this.lastIndex;
        var match = nativ.exec.apply(this, arguments);
        var name;
        var r2;
        var i;
        if (match) {
          if (!correctExecNpcg && match.length > 1 && indexOf(match, "") > -1) {
            r2 = copyRegex(this, {
              removeG: true,
              isInternalOnly: true
            });
            nativ.replace.call(String(str).slice(match.index), r2, function() {
              var len = arguments.length;
              var i2;
              for (i2 = 1; i2 < len - 2; ++i2) {
                if (arguments[i2] === void 0) {
                  match[i2] = void 0;
                }
              }
            });
          }
          if (this[REGEX_DATA] && this[REGEX_DATA].captureNames) {
            for (i = 1; i < match.length; ++i) {
              name = this[REGEX_DATA].captureNames[i - 1];
              if (name) {
                match[name] = match[i];
              }
            }
          }
          if (this.global && !match[0].length && this.lastIndex > match.index) {
            this.lastIndex = match.index;
          }
        }
        if (!this.global) {
          this.lastIndex = origLastIndex;
        }
        return match;
      };
      fixed.test = function(str) {
        return !!fixed.exec.call(this, str);
      };
      fixed.match = function(regex) {
        var result;
        if (!XRegExp.isRegExp(regex)) {
          regex = new RegExp(regex);
        } else if (regex.global) {
          result = nativ.match.apply(this, arguments);
          regex.lastIndex = 0;
          return result;
        }
        return fixed.exec.call(regex, toObject(this));
      };
      fixed.replace = function(search, replacement) {
        var isRegex = XRegExp.isRegExp(search);
        var origLastIndex;
        var captureNames;
        var result;
        if (isRegex) {
          if (search[REGEX_DATA]) {
            captureNames = search[REGEX_DATA].captureNames;
          }
          origLastIndex = search.lastIndex;
        } else {
          search += "";
        }
        if (isType(replacement, "Function")) {
          result = nativ.replace.call(String(this), search, function() {
            var args = arguments;
            var i;
            if (captureNames) {
              args[0] = new String(args[0]);
              for (i = 0; i < captureNames.length; ++i) {
                if (captureNames[i]) {
                  args[0][captureNames[i]] = args[i + 1];
                }
              }
            }
            if (isRegex && search.global) {
              search.lastIndex = args[args.length - 2] + args[0].length;
            }
            return replacement.apply(void 0, args);
          });
        } else {
          result = nativ.replace.call(this == null ? this : String(this), search, function() {
            var args = arguments;
            return nativ.replace.call(String(replacement), replacementToken, function($0, $1, $2) {
              var n;
              if ($1) {
                n = +$1;
                if (n <= args.length - 3) {
                  return args[n] || "";
                }
                n = captureNames ? indexOf(captureNames, $1) : -1;
                if (n < 0) {
                  throw new SyntaxError("Backreference to undefined group " + $0);
                }
                return args[n + 1] || "";
              }
              if ($2 === "$") {
                return "$";
              }
              if ($2 === "&" || +$2 === 0) {
                return args[0];
              }
              if ($2 === "`") {
                return args[args.length - 1].slice(0, args[args.length - 2]);
              }
              if ($2 === "'") {
                return args[args.length - 1].slice(args[args.length - 2] + args[0].length);
              }
              $2 = +$2;
              if (!isNaN($2)) {
                if ($2 > args.length - 3) {
                  throw new SyntaxError("Backreference to undefined group " + $0);
                }
                return args[$2] || "";
              }
              throw new SyntaxError("Invalid token " + $0);
            });
          });
        }
        if (isRegex) {
          if (search.global) {
            search.lastIndex = 0;
          } else {
            search.lastIndex = origLastIndex;
          }
        }
        return result;
      };
      fixed.split = function(separator, limit) {
        if (!XRegExp.isRegExp(separator)) {
          return nativ.split.apply(this, arguments);
        }
        var str = String(this);
        var output = [];
        var origLastIndex = separator.lastIndex;
        var lastLastIndex = 0;
        var lastLength;
        limit = (limit === void 0 ? -1 : limit) >>> 0;
        XRegExp.forEach(str, separator, function(match) {
          if (match.index + match[0].length > lastLastIndex) {
            output.push(str.slice(lastLastIndex, match.index));
            if (match.length > 1 && match.index < str.length) {
              Array.prototype.push.apply(output, match.slice(1));
            }
            lastLength = match[0].length;
            lastLastIndex = match.index + lastLength;
          }
        });
        if (lastLastIndex === str.length) {
          if (!nativ.test.call(separator, "") || lastLength) {
            output.push("");
          }
        } else {
          output.push(str.slice(lastLastIndex));
        }
        separator.lastIndex = origLastIndex;
        return output.length > limit ? output.slice(0, limit) : output;
      };
      XRegExp.addToken(
        /\\([ABCE-RTUVXYZaeg-mopqyz]|c(?![A-Za-z])|u(?![\dA-Fa-f]{4}|{[\dA-Fa-f]+})|x(?![\dA-Fa-f]{2}))/,
        function(match, scope) {
          if (match[1] === "B" && scope === defaultScope) {
            return match[0];
          }
          throw new SyntaxError("Invalid escape " + match[0]);
        },
        {
          scope: "all",
          leadChar: "\\"
        }
      );
      XRegExp.addToken(
        /\\u{([\dA-Fa-f]+)}/,
        function(match, scope, flags) {
          var code = dec(match[1]);
          if (code > 1114111) {
            throw new SyntaxError("Invalid Unicode code point " + match[0]);
          }
          if (code <= 65535) {
            return "\\u" + pad4(hex(code));
          }
          if (hasNativeU && flags.indexOf("u") > -1) {
            return match[0];
          }
          throw new SyntaxError("Cannot use Unicode code point above \\u{FFFF} without flag u");
        },
        {
          scope: "all",
          leadChar: "\\"
        }
      );
      XRegExp.addToken(
        /\[(\^?)\]/,
        function(match) {
          return match[1] ? "[\\s\\S]" : "\\b\\B";
        },
        { leadChar: "[" }
      );
      XRegExp.addToken(
        /\(\?#[^)]*\)/,
        getContextualTokenSeparator,
        { leadChar: "(" }
      );
      XRegExp.addToken(
        /\s+|#[^\n]*\n?/,
        getContextualTokenSeparator,
        { flag: "x" }
      );
      XRegExp.addToken(
        /\./,
        function() {
          return "[\\s\\S]";
        },
        {
          flag: "s",
          leadChar: "."
        }
      );
      XRegExp.addToken(
        /\\k<([\w$]+)>/,
        function(match) {
          var index = isNaN(match[1]) ? indexOf(this.captureNames, match[1]) + 1 : +match[1];
          var endIndex = match.index + match[0].length;
          if (!index || index > this.captureNames.length) {
            throw new SyntaxError("Backreference to undefined group " + match[0]);
          }
          return "\\" + index + (endIndex === match.input.length || isNaN(match.input.charAt(endIndex)) ? "" : "(?:)");
        },
        { leadChar: "\\" }
      );
      XRegExp.addToken(
        /\\(\d+)/,
        function(match, scope) {
          if (!(scope === defaultScope && /^[1-9]/.test(match[1]) && +match[1] <= this.captureNames.length) && match[1] !== "0") {
            throw new SyntaxError("Cannot use octal escape or backreference to undefined group " + match[0]);
          }
          return match[0];
        },
        {
          scope: "all",
          leadChar: "\\"
        }
      );
      XRegExp.addToken(
        /\(\?P?<([\w$]+)>/,
        function(match) {
          if (!isNaN(match[1])) {
            throw new SyntaxError("Cannot use integer as capture name " + match[0]);
          }
          if (match[1] === "length" || match[1] === "__proto__") {
            throw new SyntaxError("Cannot use reserved word as capture name " + match[0]);
          }
          if (indexOf(this.captureNames, match[1]) > -1) {
            throw new SyntaxError("Cannot use same name for multiple groups " + match[0]);
          }
          this.captureNames.push(match[1]);
          this.hasNamedCapture = true;
          return "(";
        },
        { leadChar: "(" }
      );
      XRegExp.addToken(
        /\((?!\?)/,
        function(match, scope, flags) {
          if (flags.indexOf("n") > -1) {
            return "(?:";
          }
          this.captureNames.push(null);
          return "(";
        },
        {
          optionalFlags: "n",
          leadChar: "("
        }
      );
      module.exports = XRegExp;
    }
  });

  // ../node_modules/parse-address/address.js
  var require_address = __commonJS({
    "../node_modules/parse-address/address.js"(exports) {
      "use strict";
      init_define_import_meta_trackerLookup();
      //! Copyright (c) 2014-2015, hassansin
      //!
      //!Perl Ref: http://cpansearch.perl.org/src/TIMB/Geo-StreetAddress-US-1.04/US.pm
      (function() {
        var root;
        root = this;
        var XRegExp;
        if (typeof __require !== "undefined") {
          XRegExp = require_xregexp();
        } else
          XRegExp = root.XRegExp;
        var parser = {};
        var Addr_Match = {};
        var Directional = {
          north: "N",
          northeast: "NE",
          east: "E",
          southeast: "SE",
          south: "S",
          southwest: "SW",
          west: "W",
          northwest: "NW"
        };
        var Street_Type = {
          allee: "aly",
          alley: "aly",
          ally: "aly",
          anex: "anx",
          annex: "anx",
          annx: "anx",
          arcade: "arc",
          av: "ave",
          aven: "ave",
          avenu: "ave",
          avenue: "ave",
          avn: "ave",
          avnue: "ave",
          bayoo: "byu",
          bayou: "byu",
          beach: "bch",
          bend: "bnd",
          bluf: "blf",
          bluff: "blf",
          bluffs: "blfs",
          bot: "btm",
          bottm: "btm",
          bottom: "btm",
          boul: "blvd",
          boulevard: "blvd",
          boulv: "blvd",
          branch: "br",
          brdge: "brg",
          bridge: "brg",
          brnch: "br",
          brook: "brk",
          brooks: "brks",
          burg: "bg",
          burgs: "bgs",
          bypa: "byp",
          bypas: "byp",
          bypass: "byp",
          byps: "byp",
          camp: "cp",
          canyn: "cyn",
          canyon: "cyn",
          cape: "cpe",
          causeway: "cswy",
          causway: "cswy",
          causwa: "cswy",
          cen: "ctr",
          cent: "ctr",
          center: "ctr",
          centers: "ctrs",
          centr: "ctr",
          centre: "ctr",
          circ: "cir",
          circl: "cir",
          circle: "cir",
          circles: "cirs",
          ck: "crk",
          cliff: "clf",
          cliffs: "clfs",
          club: "clb",
          cmp: "cp",
          cnter: "ctr",
          cntr: "ctr",
          cnyn: "cyn",
          common: "cmn",
          commons: "cmns",
          corner: "cor",
          corners: "cors",
          course: "crse",
          court: "ct",
          courts: "cts",
          cove: "cv",
          coves: "cvs",
          cr: "crk",
          crcl: "cir",
          crcle: "cir",
          crecent: "cres",
          creek: "crk",
          crescent: "cres",
          cresent: "cres",
          crest: "crst",
          crossing: "xing",
          crossroad: "xrd",
          crossroads: "xrds",
          crscnt: "cres",
          crsent: "cres",
          crsnt: "cres",
          crssing: "xing",
          crssng: "xing",
          crt: "ct",
          curve: "curv",
          dale: "dl",
          dam: "dm",
          div: "dv",
          divide: "dv",
          driv: "dr",
          drive: "dr",
          drives: "drs",
          drv: "dr",
          dvd: "dv",
          estate: "est",
          estates: "ests",
          exp: "expy",
          expr: "expy",
          express: "expy",
          expressway: "expy",
          expw: "expy",
          extension: "ext",
          extensions: "exts",
          extn: "ext",
          extnsn: "ext",
          fall: "fall",
          falls: "fls",
          ferry: "fry",
          field: "fld",
          fields: "flds",
          flat: "flt",
          flats: "flts",
          ford: "frd",
          fords: "frds",
          forest: "frst",
          forests: "frst",
          forg: "frg",
          forge: "frg",
          forges: "frgs",
          fork: "frk",
          forks: "frks",
          fort: "ft",
          freeway: "fwy",
          freewy: "fwy",
          frry: "fry",
          frt: "ft",
          frway: "fwy",
          frwy: "fwy",
          garden: "gdn",
          gardens: "gdns",
          gardn: "gdn",
          gateway: "gtwy",
          gatewy: "gtwy",
          gatway: "gtwy",
          glen: "gln",
          glens: "glns",
          grden: "gdn",
          grdn: "gdn",
          grdns: "gdns",
          green: "grn",
          greens: "grns",
          grov: "grv",
          grove: "grv",
          groves: "grvs",
          gtway: "gtwy",
          harb: "hbr",
          harbor: "hbr",
          harbors: "hbrs",
          harbr: "hbr",
          haven: "hvn",
          havn: "hvn",
          height: "hts",
          heights: "hts",
          hgts: "hts",
          highway: "hwy",
          highwy: "hwy",
          hill: "hl",
          hills: "hls",
          hiway: "hwy",
          hiwy: "hwy",
          hllw: "holw",
          hollow: "holw",
          hollows: "holw",
          holws: "holw",
          hrbor: "hbr",
          ht: "hts",
          hway: "hwy",
          inlet: "inlt",
          island: "is",
          islands: "iss",
          isles: "isle",
          islnd: "is",
          islnds: "iss",
          jction: "jct",
          jctn: "jct",
          jctns: "jcts",
          junction: "jct",
          junctions: "jcts",
          junctn: "jct",
          juncton: "jct",
          key: "ky",
          keys: "kys",
          knol: "knl",
          knoll: "knl",
          knolls: "knls",
          la: "ln",
          lake: "lk",
          lakes: "lks",
          land: "land",
          landing: "lndg",
          lane: "ln",
          lanes: "ln",
          ldge: "ldg",
          light: "lgt",
          lights: "lgts",
          lndng: "lndg",
          loaf: "lf",
          lock: "lck",
          locks: "lcks",
          lodg: "ldg",
          lodge: "ldg",
          loops: "loop",
          mall: "mall",
          manor: "mnr",
          manors: "mnrs",
          meadow: "mdw",
          meadows: "mdws",
          medows: "mdws",
          mews: "mews",
          mill: "ml",
          mills: "mls",
          mission: "msn",
          missn: "msn",
          mnt: "mt",
          mntain: "mtn",
          mntn: "mtn",
          mntns: "mtns",
          motorway: "mtwy",
          mount: "mt",
          mountain: "mtn",
          mountains: "mtns",
          mountin: "mtn",
          mssn: "msn",
          mtin: "mtn",
          neck: "nck",
          orchard: "orch",
          orchrd: "orch",
          overpass: "opas",
          ovl: "oval",
          parks: "park",
          parkway: "pkwy",
          parkways: "pkwy",
          parkwy: "pkwy",
          pass: "pass",
          passage: "psge",
          paths: "path",
          pikes: "pike",
          pine: "pne",
          pines: "pnes",
          pk: "park",
          pkway: "pkwy",
          pkwys: "pkwy",
          pky: "pkwy",
          place: "pl",
          plain: "pln",
          plaines: "plns",
          plains: "plns",
          plaza: "plz",
          plza: "plz",
          point: "pt",
          points: "pts",
          port: "prt",
          ports: "prts",
          prairie: "pr",
          prarie: "pr",
          prk: "park",
          prr: "pr",
          rad: "radl",
          radial: "radl",
          radiel: "radl",
          ranch: "rnch",
          ranches: "rnch",
          rapid: "rpd",
          rapids: "rpds",
          rdge: "rdg",
          rest: "rst",
          ridge: "rdg",
          ridges: "rdgs",
          river: "riv",
          rivr: "riv",
          rnchs: "rnch",
          road: "rd",
          roads: "rds",
          route: "rte",
          rvr: "riv",
          row: "row",
          rue: "rue",
          run: "run",
          shoal: "shl",
          shoals: "shls",
          shoar: "shr",
          shoars: "shrs",
          shore: "shr",
          shores: "shrs",
          skyway: "skwy",
          spng: "spg",
          spngs: "spgs",
          spring: "spg",
          springs: "spgs",
          sprng: "spg",
          sprngs: "spgs",
          spurs: "spur",
          sqr: "sq",
          sqre: "sq",
          sqrs: "sqs",
          squ: "sq",
          square: "sq",
          squares: "sqs",
          station: "sta",
          statn: "sta",
          stn: "sta",
          str: "st",
          strav: "stra",
          strave: "stra",
          straven: "stra",
          stravenue: "stra",
          stravn: "stra",
          stream: "strm",
          street: "st",
          streets: "sts",
          streme: "strm",
          strt: "st",
          strvn: "stra",
          strvnue: "stra",
          sumit: "smt",
          sumitt: "smt",
          summit: "smt",
          terr: "ter",
          terrace: "ter",
          throughway: "trwy",
          tpk: "tpke",
          tr: "trl",
          trace: "trce",
          traces: "trce",
          track: "trak",
          tracks: "trak",
          trafficway: "trfy",
          trail: "trl",
          trails: "trl",
          trk: "trak",
          trks: "trak",
          trls: "trl",
          trnpk: "tpke",
          trpk: "tpke",
          tunel: "tunl",
          tunls: "tunl",
          tunnel: "tunl",
          tunnels: "tunl",
          tunnl: "tunl",
          turnpike: "tpke",
          turnpk: "tpke",
          underpass: "upas",
          union: "un",
          unions: "uns",
          valley: "vly",
          valleys: "vlys",
          vally: "vly",
          vdct: "via",
          viadct: "via",
          viaduct: "via",
          view: "vw",
          views: "vws",
          vill: "vlg",
          villag: "vlg",
          village: "vlg",
          villages: "vlgs",
          ville: "vl",
          villg: "vlg",
          villiage: "vlg",
          vist: "vis",
          vista: "vis",
          vlly: "vly",
          vst: "vis",
          vsta: "vis",
          wall: "wall",
          walks: "walk",
          well: "wl",
          wells: "wls",
          wy: "way"
        };
        var State_Code = {
          "alabama": "AL",
          "alaska": "AK",
          "american samoa": "AS",
          "arizona": "AZ",
          "arkansas": "AR",
          "california": "CA",
          "colorado": "CO",
          "connecticut": "CT",
          "delaware": "DE",
          "district of columbia": "DC",
          "federated states of micronesia": "FM",
          "florida": "FL",
          "georgia": "GA",
          "guam": "GU",
          "hawaii": "HI",
          "idaho": "ID",
          "illinois": "IL",
          "indiana": "IN",
          "iowa": "IA",
          "kansas": "KS",
          "kentucky": "KY",
          "louisiana": "LA",
          "maine": "ME",
          "marshall islands": "MH",
          "maryland": "MD",
          "massachusetts": "MA",
          "michigan": "MI",
          "minnesota": "MN",
          "mississippi": "MS",
          "missouri": "MO",
          "montana": "MT",
          "nebraska": "NE",
          "nevada": "NV",
          "new hampshire": "NH",
          "new jersey": "NJ",
          "new mexico": "NM",
          "new york": "NY",
          "north carolina": "NC",
          "north dakota": "ND",
          "northern mariana islands": "MP",
          "ohio": "OH",
          "oklahoma": "OK",
          "oregon": "OR",
          "palau": "PW",
          "pennsylvania": "PA",
          "puerto rico": "PR",
          "rhode island": "RI",
          "south carolina": "SC",
          "south dakota": "SD",
          "tennessee": "TN",
          "texas": "TX",
          "utah": "UT",
          "vermont": "VT",
          "virgin islands": "VI",
          "virginia": "VA",
          "washington": "WA",
          "west virginia": "WV",
          "wisconsin": "WI",
          "wyoming": "WY"
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
          state: State_Code
        };
        function capitalize2(s) {
          return s && s[0].toUpperCase() + s.slice(1);
        }
        function keys(o) {
          return Object.keys(o);
        }
        function values(o) {
          var v2 = [];
          keys(o).forEach(function(k) {
            v2.push(o[k]);
          });
          return v2;
        }
        function each(o, fn) {
          keys(o).forEach(function(k) {
            fn(o[k], k);
          });
        }
        function invert(o) {
          var o1 = {};
          keys(o).forEach(function(k) {
            o1[o[k]] = k;
          });
          return o1;
        }
        function flatten(o) {
          return keys(o).concat(values(o));
        }
        function lazyInit() {
          if (initialized) {
            return;
          }
          initialized = true;
          Direction_Code = invert(Directional);
          Addr_Match = {
            type: flatten(Street_Type).sort().filter(function(v2, i, arr) {
              return arr.indexOf(v2) === i;
            }).join("|"),
            fraction: "\\d+\\/\\d+",
            state: "\\b(?:" + keys(State_Code).concat(values(State_Code)).map(XRegExp.escape).join("|") + ")\\b",
            direct: values(Directional).sort(function(a2, b2) {
              return a2.length < b2.length;
            }).reduce(function(prev, curr) {
              return prev.concat([XRegExp.escape(curr.replace(/\w/g, "$&.")), curr]);
            }, keys(Directional)).join("|"),
            dircode: keys(Direction_Code).join("|"),
            zip: "(?<zip>\\d{5})[- ]?(?<plus4>\\d{4})?",
            corner: "(?:\\band\\b|\\bat\\b|&|\\@)"
          };
          Addr_Match.number = "(?<number>(\\d+-?\\d*)|([N|S|E|W]\\d{1,3}[N|S|E|W]\\d{1,6}))(?=\\D)";
          Addr_Match.street = "                                       \n      (?:                                                       \n        (?:(?<street_0>" + Addr_Match.direct + ")\\W+               \n           (?<type_0>" + Addr_Match.type + ")\\b                    \n        )                                                       \n        |                                                       \n        (?:(?<prefix_0>" + Addr_Match.direct + ")\\W+)?             \n        (?:                                                     \n          (?<street_1>[^,]*\\d)                                 \n          (?:[^\\w,]*(?<suffix_1>" + Addr_Match.direct + ")\\b)     \n          |                                                     \n          (?<street_2>[^,]+)                                    \n          (?:[^\\w,]+(?<type_2>" + Addr_Match.type + ")\\b)         \n          (?:[^\\w,]+(?<suffix_2>" + Addr_Match.direct + ")\\b)?    \n          |                                                     \n          (?<street_3>[^,]+?)                                   \n          (?:[^\\w,]+(?<type_3>" + Addr_Match.type + ")\\b)?        \n          (?:[^\\w,]+(?<suffix_3>" + Addr_Match.direct + ")\\b)?    \n        )                                                       \n      )";
          Addr_Match.po_box = "p\\W*(?:[om]|ost\\ ?office)\\W*b(?:ox)?";
          Addr_Match.sec_unit_type_numbered = "             \n      (?<sec_unit_type_1>su?i?te                      \n        |" + Addr_Match.po_box + "                        \n        |(?:ap|dep)(?:ar)?t(?:me?nt)?                 \n        |ro*m                                         \n        |flo*r?                                       \n        |uni?t                                        \n        |bu?i?ldi?n?g                                 \n        |ha?nga?r                                     \n        |lo?t                                         \n        |pier                                         \n        |slip                                         \n        |spa?ce?                                      \n        |stop                                         \n        |tra?i?le?r                                   \n        |box)(?![a-z]                                 \n      )                                               \n      ";
          Addr_Match.sec_unit_type_unnumbered = "           \n      (?<sec_unit_type_2>ba?se?me?n?t                 \n        |fro?nt                                       \n        |lo?bby                                       \n        |lowe?r                                       \n        |off?i?ce?                                    \n        |pe?n?t?ho?u?s?e?                             \n        |rear                                         \n        |side                                         \n        |uppe?r                                       \n      )\\b";
          Addr_Match.sec_unit = "                               \n      (?:                               #fix3             \n        (?:                             #fix1             \n          (?:                                             \n            (?:" + Addr_Match.sec_unit_type_numbered + "\\W*) \n            |(?<sec_unit_type_3>\\#)\\W*                  \n          )                                               \n          (?<sec_unit_num_1>[\\w-]+)                      \n        )                                                 \n        |                                                 \n        " + Addr_Match.sec_unit_type_unnumbered + "           \n      )";
          Addr_Match.city_and_state = "                       \n      (?:                                               \n        (?<city>[^\\d,]+?)\\W+                          \n        (?<state>" + Addr_Match.state + ")                  \n      )                                                 \n      ";
          Addr_Match.place = "                                \n      (?:" + Addr_Match.city_and_state + "\\W*)?            \n      (?:" + Addr_Match.zip + ")?                           \n      ";
          Addr_Match.address = XRegExp("                      \n      ^                                                 \n      [^\\w\\#]*                                        \n      (" + Addr_Match.number + ")\\W*                       \n      (?:" + Addr_Match.fraction + "\\W*)?                  \n         " + Addr_Match.street + "\\W+                      \n      (?:" + Addr_Match.sec_unit + ")?\\W*          #fix2   \n         " + Addr_Match.place + "                           \n      \\W*$", "ix");
          var sep = "(?:\\W+|$)";
          Addr_Match.informal_address = XRegExp("                   \n      ^                                                       \n      \\s*                                                    \n      (?:" + Addr_Match.sec_unit + sep + ")?                        \n      (?:" + Addr_Match.number + ")?\\W*                          \n      (?:" + Addr_Match.fraction + "\\W*)?                        \n         " + Addr_Match.street + sep + "                            \n      (?:" + Addr_Match.sec_unit.replace(/_\d/g, "$&1") + sep + ")?  \n      (?:" + Addr_Match.place + ")?                               \n      ", "ix");
          Addr_Match.po_address = XRegExp("                         \n      ^                                                       \n      \\s*                                                    \n      (?:" + Addr_Match.sec_unit.replace(/_\d/g, "$&1") + sep + ")?  \n      (?:" + Addr_Match.place + ")?                               \n      ", "ix");
          Addr_Match.intersection = XRegExp("                     \n      ^\\W*                                                 \n      " + Addr_Match.street.replace(/_\d/g, "1$&") + "\\W*?      \n      \\s+" + Addr_Match.corner + "\\s+                         \n      " + Addr_Match.street.replace(/_\d/g, "2$&") + "\\W+     \n      " + Addr_Match.place + "\\W*$", "ix");
        }
        parser.normalize_address = function(parts) {
          lazyInit();
          if (!parts)
            return null;
          var parsed = {};
          Object.keys(parts).forEach(function(k) {
            if (["input", "index"].indexOf(k) !== -1 || isFinite(k))
              return;
            var key = isFinite(k.split("_").pop()) ? k.split("_").slice(0, -1).join("_") : k;
            if (parts[k])
              parsed[key] = parts[k].trim().replace(/^\s+|\s+$|[^\w\s\-#&]/g, "");
          });
          each(Normalize_Map, function(map, key) {
            if (parsed[key] && map[parsed[key].toLowerCase()]) {
              parsed[key] = map[parsed[key].toLowerCase()];
            }
          });
          ["type", "type1", "type2"].forEach(function(key) {
            if (key in parsed)
              parsed[key] = parsed[key].charAt(0).toUpperCase() + parsed[key].slice(1).toLowerCase();
          });
          if (parsed.city) {
            parsed.city = XRegExp.replace(
              parsed.city,
              XRegExp("^(?<dircode>" + Addr_Match.dircode + ")\\s+(?=\\S)", "ix"),
              function(match) {
                return capitalize2(Direction_Code[match.dircode.toUpperCase()]) + " ";
              }
            );
          }
          return parsed;
        };
        parser.parseAddress = function(address) {
          lazyInit();
          var parts = XRegExp.exec(address, Addr_Match.address);
          return parser.normalize_address(parts);
        };
        parser.parseInformalAddress = function(address) {
          lazyInit();
          var parts = XRegExp.exec(address, Addr_Match.informal_address);
          return parser.normalize_address(parts);
        };
        parser.parsePoAddress = function(address) {
          lazyInit();
          var parts = XRegExp.exec(address, Addr_Match.po_address);
          return parser.normalize_address(parts);
        };
        parser.parseLocation = function(address) {
          lazyInit();
          if (XRegExp(Addr_Match.corner, "xi").test(address)) {
            return parser.parseIntersection(address);
          }
          if (XRegExp("^" + Addr_Match.po_box, "xi").test(address)) {
            return parser.parsePoAddress(address);
          }
          return parser.parseAddress(address) || parser.parseInformalAddress(address);
        };
        parser.parseIntersection = function(address) {
          lazyInit();
          var parts = XRegExp.exec(address, Addr_Match.intersection);
          parts = parser.normalize_address(parts);
          if (parts) {
            parts.type2 = parts.type2 || "";
            parts.type1 = parts.type1 || "";
            if (parts.type2 && !parts.type1 || parts.type1 === parts.type2) {
              var type = parts.type2;
              type = XRegExp.replace(type, /s\W*$/, "");
              if (XRegExp("^" + Addr_Match.type + "$", "ix").test(type)) {
                parts.type1 = parts.type2 = type;
              }
            }
          }
          return parts;
        };
        if (typeof define !== "undefined" && define.amd) {
          define([], function() {
            return parser;
          });
        } else if (typeof exports !== "undefined") {
          exports.parseIntersection = parser.parseIntersection;
          exports.parseLocation = parser.parseLocation;
          exports.parseInformalAddress = parser.parseInformalAddress;
          exports.parseAddress = parser.parseAddress;
        } else {
          root.addressParser = root.addressParser || parser;
        }
      })();
    }
  });

  // entry-points/android.js
  init_define_import_meta_trackerLookup();

  // src/content-scope-features.js
  init_define_import_meta_trackerLookup();

  // src/utils.js
  init_define_import_meta_trackerLookup();

  // src/captured-globals.js
  init_define_import_meta_trackerLookup();
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
  var TextEncoder2 = globalThis.TextEncoder;
  var TextDecoder = globalThis.TextDecoder;
  var Uint8Array2 = globalThis.Uint8Array;
  var Uint16Array = globalThis.Uint16Array;
  var Uint32Array = globalThis.Uint32Array;
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
  function processConfig(data2, userList, preferences, platformSpecificFeatures2 = []) {
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
    const enabledFeatures = computeEnabledFeatures(data2, topLevelHostname, preferences.platform, platformSpecificFeatures2);
    const isBroken = isUnprotectedDomain(topLevelHostname, data2.unprotectedTemporary);
    output.site = Object.assign(site, {
      isBroken,
      allowlisted,
      enabledFeatures
    });
    output.featureSettings = parseFeatureSettings(data2, enabledFeatures);
    output.bundledConfig = data2;
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
  function computeEnabledFeatures(data2, topLevelHostname, platform, platformSpecificFeatures2 = []) {
    const remoteFeatureNames = Object.keys(data2.features);
    const platformSpecificFeaturesNotInRemoteConfig = platformSpecificFeatures2.filter(
      (featureName) => !remoteFeatureNames.includes(featureName)
    );
    const enabledFeatures = remoteFeatureNames.filter((featureName) => {
      const feature = data2.features[featureName];
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
  function parseFeatureSettings(data2, enabledFeatures) {
    const featureSettings = {};
    const remoteFeatureNames = Object.keys(data2.features);
    remoteFeatureNames.forEach((featureName) => {
      if (!enabledFeatures.includes(featureName)) {
        return;
      }
      const feature = data2.features[featureName];
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
  init_define_import_meta_trackerLookup();
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
  init_define_import_meta_trackerLookup();
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

  // ddg:platformFeatures:ddg:platformFeatures
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection.js
  init_define_import_meta_trackerLookup();

  // src/content-feature.js
  init_define_import_meta_trackerLookup();

  // src/wrapper-utils.js
  init_define_import_meta_trackerLookup();
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

  // ../messaging/index.js
  init_define_import_meta_trackerLookup();

  // ../messaging/lib/windows.js
  init_define_import_meta_trackerLookup();
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
      const data2 = this.globals.JSONparse(this.globals.JSONstringify(msg.params || {}));
      const notification = WindowsNotification.fromNotification(msg, data2);
      this.config.methods.postMessage(notification);
    }
    /**
     * @param {import('../index.js').RequestMessage} msg
     * @param {{signal?: AbortSignal}} opts
     * @return {Promise<any>}
     */
    request(msg, opts = {}) {
      const data2 = this.globals.JSONparse(this.globals.JSONstringify(msg.params || {}));
      const outgoing = WindowsRequestMessage.fromRequest(msg, data2);
      this.config.methods.postMessage(outgoing);
      const comparator = (eventData) => {
        return eventData.featureName === msg.featureName && eventData.context === msg.context && eventData.id === msg.id;
      };
      function isMessageResponse(data3) {
        if ("result" in data3) return true;
        if ("error" in data3) return true;
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
    static fromNotification(notification, data2) {
      const output = {
        Data: data2,
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
    static fromRequest(msg, data2) {
      const output = {
        Data: data2,
        Feature: msg.context,
        SubFeatureName: msg.featureName,
        Name: msg.method,
        Id: msg.id
      };
      return output;
    }
  };

  // ../messaging/lib/webkit.js
  init_define_import_meta_trackerLookup();

  // ../messaging/schema.js
  init_define_import_meta_trackerLookup();
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
  function isResponseFor(request, data2) {
    if ("result" in data2) {
      return data2.featureName === request.featureName && data2.context === request.context && data2.id === request.id;
    }
    if ("error" in data2) {
      if ("message" in data2.error) {
        return true;
      }
    }
    return false;
  }
  function isSubscriptionEventFor(sub, data2) {
    if ("subscriptionName" in data2) {
      return data2.featureName === sub.featureName && data2.context === sub.context && data2.subscriptionName === sub.subscriptionName;
    }
    return false;
  }

  // src/navigator-global.js
  init_define_import_meta_trackerLookup();
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
    wkSend(handler, data2 = {}) {
      const captured = this.capturedWebkitHandlers[handler];
      if (!captured || typeof captured.postMessage !== "function") {
        throw new MissingHandler(`Missing webkit handler: '${handler}'`, handler);
      }
      return ReflectApply(captured.postMessage, captured.handler, [data2]);
    }
    /**
     * Sends message to the webkit layer and waits for the specified response
     * @param {String} handler
     * @param {import('../index.js').RequestMessage} data
     * @returns {Promise<*>}
     * @internal
     */
    async wkSendAndWait(handler, data2) {
      const response = await this.wkSend(handler, data2);
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
      const data2 = await this.wkSendAndWait(msg.context, msg);
      if (isResponseFor(msg, data2)) {
        if (data2.result) {
          return data2.result || {};
        }
        if (data2.error) {
          throw new Error2(data2.error.message);
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
        value: (data2) => {
          if (data2 && isSubscriptionEventFor(msg, data2)) {
            callback(data2.params);
          } else {
            console.warn("Received a message that did not match the subscription", data2);
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
  init_define_import_meta_trackerLookup();
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
        function handler(data2) {
          if (isResponseFor(msg, data2)) {
            if (data2.result) {
              resolve(data2.result || {});
              return unsub();
            }
            if (data2.error) {
              reject(new Error(data2.error.message));
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
      const unsub = this.config.subscribe(msg.subscriptionName, (data2) => {
        if (isSubscriptionEventFor(msg, data2)) {
          callback(data2.params || {});
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
  init_define_import_meta_trackerLookup();
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
        function handler(data2) {
          if (isResponseFor(msg, data2)) {
            if (data2.result) {
              resolve(data2.result || {});
              return unsub();
            }
            if (data2.error) {
              reject(new Error(data2.error.message));
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
      const unsub = this.config.subscribe(msg.subscriptionName, (data2) => {
        if (isSubscriptionEventFor(msg, data2)) {
          callback(data2.params || {});
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
          const data2 = (
            /** @type {MessageEvent} */
            event.data
          );
          if (typeof data2 === "string") {
            const parsedData = JSON.parse(data2);
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

  // ../messaging/lib/typed-messages.js
  init_define_import_meta_trackerLookup();

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
    notify(name, data2 = {}) {
      try {
        const message = new NotificationMessage({
          context: this.messagingContext.context,
          featureName: this.messagingContext.featureName,
          method: name,
          params: data2
        });
        const maybeAsyncResult = this.transport.notify(message);
        if (isPromiseLike(maybeAsyncResult)) {
          void handleAsyncNotificationResult(maybeAsyncResult, this.messagingContext.env, name, data2);
        }
      } catch (e) {
        logNotificationError(this.messagingContext.env, name, data2, e);
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
    request(name, data2 = {}) {
      const id = globalThis?.crypto?.randomUUID?.() || name + ".response";
      const message = new RequestMessage({
        context: this.messagingContext.context,
        featureName: this.messagingContext.featureName,
        method: name,
        params: data2,
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
  async function handleAsyncNotificationResult(result, env, name, data2) {
    try {
      await result;
    } catch (error) {
      logNotificationError(env, name, data2, error);
    }
  }
  function logNotificationError(env, name, data2, error) {
    if (env === "development") {
      try {
        console.error("[Messaging] Failed to send notification:", error);
        console.error("[Messaging] Message details:", { name, data: data2 });
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
  init_define_import_meta_trackerLookup();
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
  init_define_import_meta_trackerLookup();
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

  // src/config-feature.js
  init_define_import_meta_trackerLookup();

  // ../node_modules/immutable-json-patch/lib/esm/index.js
  init_define_import_meta_trackerLookup();

  // ../node_modules/immutable-json-patch/lib/esm/immutabilityHelpers.js
  init_define_import_meta_trackerLookup();

  // ../node_modules/immutable-json-patch/lib/esm/typeguards.js
  init_define_import_meta_trackerLookup();
  function isJSONArray(value) {
    return Array.isArray(value);
  }
  function isJSONObject(value) {
    return value !== null && typeof value === "object" && (value.constructor === void 0 || // for example Object.create(null)
    value.constructor.name === "Object");
  }

  // ../node_modules/immutable-json-patch/lib/esm/utils.js
  init_define_import_meta_trackerLookup();
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

  // ../node_modules/immutable-json-patch/lib/esm/immutableJSONPatch.js
  init_define_import_meta_trackerLookup();

  // ../node_modules/immutable-json-patch/lib/esm/jsonPointer.js
  init_define_import_meta_trackerLookup();
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

  // ../node_modules/urlpattern-polyfill/index.js
  init_define_import_meta_trackerLookup();

  // ../node_modules/urlpattern-polyfill/dist/urlpattern.js
  init_define_import_meta_trackerLookup();
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
        let condition2 = rule.condition;
        if (condition2 === void 0 && rule.domain !== void 0) {
          condition2 = this._domainToConditonBlocks(rule.domain);
        }
        if (condition2 === void 0) return true;
        return this._matchConditionalBlockOrArray(condition2);
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

  // src/features/broker-protection/execute.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/actions/actions.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/actions/extract.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/utils/utils.js
  init_define_import_meta_trackerLookup();
  function getElement(doc = document, selector) {
    if (isXpath(selector)) {
      return safeQuerySelectorXPath(doc, selector);
    }
    return safeQuerySelector(doc, selector);
  }
  function getElementByTagName(doc = document, name) {
    return safeQuerySelector(doc, `[name="${name}"]`);
  }
  function getElementWithSrcStart(node = document, src) {
    return safeQuerySelector(node, `[src^="${src}"]`);
  }
  function getElements(doc = document, selector) {
    if (isXpath(selector)) {
      return safeQuerySelectorAllXpath(doc, selector);
    }
    return safeQuerySelectorAll(doc, selector);
  }
  function getElementMatches(element, selector) {
    try {
      if (isXpath(selector)) {
        return matchesXPath(element, selector) ? element : null;
      } else {
        return element.matches(selector) ? element : null;
      }
    } catch (e) {
      console.error("getElementMatches threw: ", e);
      return null;
    }
  }
  function matchesXPath(element, selector) {
    const xpathResult = document.evaluate(selector, element, null, XPathResult.BOOLEAN_TYPE, null);
    return xpathResult.booleanValue;
  }
  function isXpath(selector) {
    if (!(typeof selector === "string")) return false;
    if (selector === ".") return true;
    return selector.startsWith("//") || selector.startsWith("./") || selector.startsWith("(");
  }
  function safeQuerySelectorAll(element, selector) {
    try {
      if (element && "querySelectorAll" in element) {
        return Array.from(element?.querySelectorAll?.(selector));
      }
      return null;
    } catch (e) {
      return null;
    }
  }
  function safeQuerySelector(element, selector) {
    try {
      if (element && "querySelector" in element) {
        return element?.querySelector?.(selector);
      }
      return null;
    } catch (e) {
      return null;
    }
  }
  function safeQuerySelectorXPath(element, selector) {
    try {
      const match = document.evaluate(selector, element, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
      const single = match?.singleNodeValue;
      if (single) {
        return (
          /** @type {HTMLElement} */
          single
        );
      }
      return null;
    } catch (e) {
      console.log("safeQuerySelectorXPath threw", e);
      return null;
    }
  }
  function safeQuerySelectorAllXpath(element, selector) {
    try {
      const xpathResult = document.evaluate(selector, element, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
      if (xpathResult) {
        const matchedNodes = [];
        for (let i = 0; i < xpathResult.snapshotLength; i++) {
          const item = xpathResult.snapshotItem(i);
          if (item) matchedNodes.push(
            /** @type {HTMLElement} */
            item
          );
        }
        return (
          /** @type {HTMLElement[]} */
          matchedNodes
        );
      }
      return null;
    } catch (e) {
      console.log("safeQuerySelectorAllXpath threw", e);
      return null;
    }
  }
  function generateRandomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1) + min);
  }
  function cleanArray(input, prev = []) {
    if (!Array.isArray(input)) {
      if (input === null) return prev;
      if (input === void 0) return prev;
      if (typeof input === "string") {
        const trimmed = input.trim();
        if (trimmed.length > 0) {
          prev.push(
            /** @type {NonNullable<T>} */
            trimmed
          );
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
  function nonEmptyString(input) {
    if (typeof input !== "string") return false;
    return input.trim().length > 0;
  }
  function matchingPair(a2, b2) {
    if (!nonEmptyString(a2)) return false;
    if (!nonEmptyString(b2)) return false;
    return a2.toLowerCase().trim() === b2.toLowerCase().trim();
  }
  function sortAddressesByStateAndCity(addresses) {
    return addresses.sort((a2, b2) => {
      if (a2.state < b2.state) {
        return -1;
      }
      if (a2.state > b2.state) {
        return 1;
      }
      return a2.city.localeCompare(b2.city);
    });
  }
  async function hashObject(profile) {
    const msgUint8 = new TextEncoder().encode(JSON.stringify(profile));
    const hashBuffer = await crypto.subtle.digest("SHA-1", msgUint8);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    const hashHex = hashArray.map((b2) => b2.toString(16).padStart(2, "0")).join("");
    return hashHex;
  }

  // src/features/broker-protection/types.js
  init_define_import_meta_trackerLookup();
  var PirError = class _PirError {
    /**
     * @param {object} params
     * @param {boolean} params.success
     * @param {object} params.error
     * @param {string} params.error.message
     */
    constructor(params) {
      this.success = params.success;
      this.error = params.error;
    }
    /**
     * @param {string} message
     * @return {PirError}
     * @static
     * @memberof PirError
     */
    static create(message) {
      return new _PirError({ success: false, error: { message } });
    }
    /**
     * @param {object} error
     * @return {error is PirError}
     * @static
     * @memberof PirError
     */
    static isError(error) {
      return error instanceof _PirError && error.success === false;
    }
  };
  var PirSuccess = class _PirSuccess {
    /**
     * @param {object} params
     * @param {boolean} params.success
     * @param {T} params.response
     */
    constructor(params) {
      this.success = params.success;
      this.response = params.response;
    }
    /**
     * @template T
     * @param {T} response
     * @return {PirSuccess<T>}
     * @static
     * @memberof PirSuccess
     */
    static create(response) {
      return new _PirSuccess({ success: true, response });
    }
    static createEmpty() {
      return new _PirSuccess({ success: true, response: null });
    }
    /**
     * @param {object} params
     * @return {params is PirSuccess}
     * @static
     * @memberof PirSuccess
     */
    static isSuccess(params) {
      return params instanceof _PirSuccess && params.success === true;
    }
  };
  var ErrorResponse = class _ErrorResponse {
    /**
     * @param {object} params
     * @param {string} params.actionID
     * @param {string} params.message
     */
    constructor(params) {
      this.error = params;
    }
    /**
     * @param {ActionResponse} response
     * @return {response is ErrorResponse}
     * @static
     * @memberof ErrorResponse
     */
    static isErrorResponse(response) {
      return response instanceof _ErrorResponse;
    }
    /**
     * @param {object} params
     * @param {PirAction['id']} params.actionID
     * @param {string} [params.context]
     * @return {(message: string) => ErrorResponse}
     * @static
     * @memberof ErrorResponse
     */
    static generateErrorResponseFunction({ actionID, context = "" }) {
      return (message) => new _ErrorResponse({ actionID, message: [context, message].filter(Boolean).join(": ") });
    }
  };
  var SuccessResponse = class _SuccessResponse {
    /**
     * @param {SuccessResponseInterface} params
     */
    constructor(params) {
      this.success = params;
    }
    /**
     * @param {SuccessResponseInterface} params
     * @return {SuccessResponse}
     * @static
     * @memberof SuccessResponse
     */
    static create(params) {
      return new _SuccessResponse(params);
    }
  };
  var ProfileResult = class {
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
        matchedFields: this.matchedFields
      };
    }
  };

  // src/features/broker-protection/comparisons/is-same-age.js
  init_define_import_meta_trackerLookup();
  function isSameAge(userAge, ageFound) {
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

  // src/features/broker-protection/comparisons/is-same-name.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/comparisons/constants.js
  init_define_import_meta_trackerLookup();
  var names = {
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
        aaron: ["erin", "ronnie", "ron"],
        abbigail: ["nabby", "abby", "gail", "abbe", "abbi", "abbey", "abbie"],
        abbigale: ["nabby", "abby", "gail", "abbe", "abbi", "abbey", "abbie"],
        abednego: ["bedney"],
        abel: ["ebbie", "ab", "abe", "eb"],
        abiel: ["ab"],
        abigail: ["nabby", "abby", "gail", "abbe", "abbi", "abbey", "abbie"],
        abigale: ["nabby", "abby", "gail", "abbe", "abbi", "abbey", "abbie"],
        abijah: ["ab", "bige"],
        abner: ["ab"],
        abraham: ["ab", "abe"],
        abram: ["ab", "abe"],
        absalom: ["app", "ab", "abbie"],
        ada: ["addy", "adie"],
        adaline: ["delia", "lena", "dell", "addy", "ada", "adie"],
        addison: ["addie", "addy"],
        adela: ["della", "adie"],
        adelaide: ["heidi", "adele", "dell", "addy", "della", "adie"],
        adelbert: ["del", "albert", "delbert", "bert"],
        adele: ["addy", "dell"],
        adeline: ["delia", "lena", "dell", "addy", "ada"],
        adelphia: ["philly", "delphia", "adele", "dell", "addy"],
        adena: ["dena", "dina", "deena", "adina"],
        adolphus: ["dolph", "ado", "adolph"],
        adrian: ["rian"],
        adriane: ["riane"],
        adrienne: ["addie", "rienne", "enne"],
        agatha: ["aggy", "aga"],
        agnes: ["inez", "aggy", "nessa"],
        aileen: ["lena", "allie"],
        alan: ["al"],
        alanson: ["al", "lanson"],
        alastair: ["al"],
        alazama: ["ali"],
        albert: ["bert", "al"],
        alberta: ["bert", "allie", "bertie"],
        aldo: ["al"],
        aldrich: ["riche", "rich", "richie"],
        aleksandr: ["alex", "alek"],
        aleva: ["levy", "leve"],
        alex: ["al"],
        alexander: ["alex", "al", "sandy", "alec"],
        alexandra: ["alex", "sandy", "alla", "sandra"],
        alexandria: ["drina", "alexander", "alla", "sandra", "alex"],
        alexis: ["lexi", "alex"],
        alfonse: ["al"],
        alfred: ["freddy", "al", "fred"],
        alfreda: ["freddy", "alfy", "freda", "frieda"],
        algernon: ["algy"],
        alice: ["lisa", "elsie", "allie"],
        alicia: ["lisa", "elsie", "allie"],
        aline: ["adeline"],
        alison: ["ali", "allie"],
        alixandra: ["alix"],
        allan: ["al", "alan", "allen"],
        allen: ["al", "allan", "alan"],
        allisandra: ["ali", "ally", "allie"],
        allison: ["ali", "ally", "allie"],
        allyson: ["ali", "ally", "allie"],
        allyssa: ["ali", "ally", "allie"],
        almena: ["mena", "ali", "ally", "allie"],
        almina: ["minnie"],
        almira: ["myra"],
        alonzo: ["lon", "al", "lonzo"],
        alphinias: ["alphus"],
        althea: ["ally"],
        alverta: ["virdie", "vert"],
        alyssa: ["lissia", "al", "ally"],
        alzada: ["zada"],
        amanda: ["mandy", "manda"],
        ambrose: ["brose"],
        amelia: ["amy", "mel", "millie", "emily"],
        amos: ["moses"],
        anastasia: ["ana", "stacy"],
        anderson: ["andy"],
        andre: ["drea"],
        andrea: ["drea", "rea", "andrew", "andi", "andy"],
        andrew: ["andy", "drew"],
        andriane: ["ada", "adri", "rienne"],
        angela: ["angel", "angie"],
        angelica: ["angie", "angel", "angelika", "angelique"],
        angelina: ["angel", "angie", "lina"],
        ann: ["annie", "nan"],
        anna: ["anne", "ann", "annie", "nan"],
        anne: ["annie", "ann", "nan"],
        annette: ["anna", "nettie"],
        annie: ["ann", "anna"],
        anselm: ["ansel", "selma", "anse", "ance"],
        anthony: ["ant", "tony"],
        antoinette: ["tony", "netta", "ann"],
        antonia: ["tony", "netta", "ann"],
        antonio: ["ant", "tony"],
        appoline: ["appy", "appie"],
        aquilla: ["quil", "quillie"],
        ara: ["belle", "arry"],
        arabella: ["ara", "bella", "arry", "belle"],
        arabelle: ["ara", "bella", "arry", "belle"],
        araminta: ["armida", "middie", "ruminta", "minty"],
        archibald: ["archie"],
        archilles: ["kill", "killis"],
        ariadne: ["arie", "ari"],
        arielle: ["arie"],
        aristotle: ["telly"],
        arizona: ["onie", "ona"],
        arlene: ["arly", "lena"],
        armanda: ["mandy"],
        armena: ["mena", "arry"],
        armilda: ["milly"],
        arminda: ["mindie"],
        arminta: ["minite", "minnie"],
        arnold: ["arnie"],
        aron: ["erin", "ronnie", "ron"],
        artelepsa: ["epsey"],
        artemus: ["art"],
        arthur: ["art"],
        arthusa: ["thursa"],
        arzada: ["zaddi"],
        asahel: ["asa"],
        asaph: ["asa"],
        asenath: ["sene", "assene", "natty"],
        ashley: ["ash", "leah", "lee", "ashly"],
        aubrey: ["bree"],
        audrey: ["dee", "audree"],
        august: ["gus"],
        augusta: ["tina", "aggy", "gatsy", "gussie"],
        augustina: ["tina", "aggy", "gatsy", "gussie"],
        augustine: ["gus", "austin", "august"],
        augustus: ["gus", "austin", "august"],
        aurelia: ["ree", "rilly", "orilla", "aurilla", "ora"],
        avarilla: ["rilla"],
        azariah: ["riah", "aze"],
        bab: ["barby"],
        babs: ["barby", "barbara", "bab"],
        barbara: ["barby", "babs", "bab", "bobbie", "barbie"],
        barbery: ["barbara"],
        barbie: ["barbara"],
        barnabas: ["barney"],
        barney: ["barnabas"],
        bart: ["bartholomew"],
        bartholomew: ["bartel", "bat", "meus", "bart", "mees"],
        barticus: ["bart"],
        bazaleel: ["basil"],
        bea: ["beatrice"],
        beatrice: ["bea", "trisha", "trixie", "trix"],
        becca: ["beck"],
        beck: ["becky"],
        bedelia: ["delia", "bridgit"],
        belinda: ["belle", "linda"],
        bella: ["belle", "arabella", "isabella"],
        benedict: ["bennie", "ben"],
        benjamin: ["benjy", "jamie", "bennie", "ben", "benny"],
        benjy: ["benjamin"],
        bernard: ["barney", "bernie", "berney", "berny"],
        berney: ["bernie"],
        bert: ["bertie", "bob", "bobby"],
        bertha: ["bert", "birdie", "bertie"],
        bertram: ["bert"],
        bess: ["bessie"],
        beth: ["betsy", "betty", "elizabeth"],
        bethena: ["beth", "thaney"],
        beverly: ["bev"],
        bezaleel: ["zeely"],
        biddie: ["biddy"],
        bill: ["william", "billy", "robert", "willie", "fred"],
        billy: ["william", "robert", "fred"],
        blanche: ["bea"],
        bob: ["rob", "robert"],
        bobby: ["rob", "bob"],
        boetius: ["bo"],
        brad: ["bradford", "ford"],
        bradford: ["ford", "brad"],
        bradley: ["brad"],
        brady: ["brody"],
        breanna: ["bree", "bri"],
        breeanna: ["bree"],
        brenda: ["brandy"],
        brian: ["bryan", "bryant"],
        brianna: ["bri"],
        bridget: ["bridie", "biddy", "bridgie", "biddie"],
        brittany: ["britt", "brittnie"],
        brittney: ["britt", "brittnie"],
        broderick: ["ricky", "brody", "brady", "rick", "rod"],
        bryanna: ["brianna", "bri", "briana", "ana", "anna"],
        caitlin: ["cait", "caity"],
        caitlyn: ["cait", "caity"],
        caldonia: ["calliedona"],
        caleb: ["cal"],
        california: ["callie"],
        calista: ["kissy"],
        calpurnia: ["cally"],
        calvin: ["vin", "vinny", "cal"],
        cameron: ["ron", "cam", "ronny"],
        camile: ["cammie"],
        camille: ["millie", "cammie"],
        campbell: ["cam"],
        candace: ["candy", "dacey"],
        carla: ["karla", "carly"],
        carlotta: ["lottie"],
        carlton: ["carl"],
        carmellia: ["mellia"],
        carmelo: ["melo"],
        carmon: ["charm", "cammie", "carm"],
        carol: ["lynn", "carrie", "carolann", "cassie", "caroline", "carole", "carri", "kari", "kara"],
        carolann: ["carol", "carole"],
        caroline: ["lynn", "carol", "carrie", "cassie", "carole"],
        carolyn: ["lynn", "carrie", "cassie"],
        carrie: ["cassie"],
        carthaette: ["etta", "etty"],
        casey: ["k.c."],
        casper: ["jasper"],
        cassandra: ["sandy", "cassie", "sandra"],
        cassidy: ["cassie", "cass"],
        caswell: ["cass"],
        catherine: ["kathy", "katy", "lena", "kittie", "kit", "trina", "cathy", "kay", "cassie", "casey"],
        cathleen: ["kathy", "katy", "lena", "kittie", "kit", "trina", "cathy", "kay", "cassie", "casey"],
        cathy: ["kathy", "cathleen", "catherine"],
        cecilia: ["cissy", "celia"],
        cedric: ["ced", "rick", "ricky"],
        celeste: ["lessie", "celia"],
        celinda: ["linda", "lynn", "lindy"],
        charity: ["chat"],
        charles: ["charlie", "chuck", "carl", "chick"],
        charlie: ["charles", "chuck"],
        charlotte: ["char", "sherry", "lottie", "lotta"],
        chauncey: ["chan"],
        chelsey: ["chelsie"],
        cheryl: ["cher"],
        chesley: ["chet"],
        chester: ["chet"],
        chet: ["chester"],
        chick: ["charlotte", "caroline", "chuck"],
        chloe: ["clo"],
        chris: ["kris"],
        christa: ["chris"],
        christian: ["chris", "kit"],
        christiana: ["kris", "kristy", "ann", "tina", "christy", "chris", "crissy"],
        christiano: ["chris"],
        christina: ["kris", "kristy", "tina", "christy", "chris", "crissy", "chrissy"],
        christine: ["kris", "kristy", "chrissy", "tina", "chris", "crissy", "christy"],
        christoph: ["chris"],
        christopher: ["chris", "kit"],
        christy: ["crissy"],
        cicely: ["cilla"],
        cinderella: ["arilla", "rella", "cindy", "rilla"],
        cindy: ["cinderella"],
        claire: ["clair", "clare", "clara"],
        clara: ["clarissa"],
        clare: ["clara"],
        clarence: ["clare", "clair"],
        clarinda: ["clara"],
        clarissa: ["cissy", "clara"],
        claudia: ["claud"],
        cleatus: ["cleat"],
        clement: ["clem"],
        clementine: ["clement", "clem"],
        cliff: ["clifford"],
        clifford: ["ford", "cliff"],
        clifton: ["tony", "cliff"],
        cole: ["colie"],
        columbus: ["clum"],
        con: ["conny"],
        conrad: ["conny", "con"],
        constance: ["connie"],
        cordelia: ["cordy", "delia"],
        corey: ["coco", "cordy", "ree"],
        corinne: ["cora", "ora"],
        cornelia: ["nelly", "cornie", "nelia", "corny", "nelle"],
        cornelius: ["conny", "niel", "corny", "con", "neil"],
        cory: ["coco", "cordy", "ree"],
        courtney: ["curt", "court"],
        crystal: ["chris", "tal", "stal", "crys"],
        curtis: ["curt"],
        cynthia: ["cintha", "cindy"],
        cyrenius: ["swene", "cy", "serene", "renius", "cene"],
        cyrus: ["cy"],
        dahl: ["dal"],
        dalton: ["dahl", "dal"],
        daniel: ["dan", "danny", "dann"],
        danielle: ["ellie", "dani"],
        danny: ["daniel"],
        daphne: ["daph", "daphie"],
        darlene: ["lena", "darry"],
        david: ["dave", "day", "davey"],
        daycia: ["daisha", "dacia"],
        deanne: ["ann", "dee"],
        debbie: ["deb", "debra", "deborah", "debby"],
        debby: ["deb"],
        debora: ["deb", "debbie", "debby"],
        deborah: ["deb", "debbie", "debby"],
        debra: ["deb", "debbie"],
        deidre: ["deedee"],
        delbert: ["bert", "del"],
        delia: ["fidelia", "cordelia", "delius"],
        delilah: ["lil", "lila", "dell", "della"],
        deliverance: ["delly", "dilly", "della"],
        della: ["adela", "delilah", "adelaide", "dell"],
        delores: ["lolly", "lola", "della", "dee", "dell"],
        delpha: ["philadelphia"],
        delphine: ["delphi", "del", "delf"],
        demaris: ["dea", "maris", "mary"],
        demerias: ["dea", "maris", "mary"],
        democrates: ["mock"],
        dennis: ["denny", "dennie"],
        dennison: ["denny", "dennis"],
        derek: ["derrek", "rick", "ricky"],
        derick: ["rick", "ricky"],
        derrick: ["ricky", "eric", "rick"],
        deuteronomy: ["duty"],
        diana: ["dicey", "didi", "di"],
        diane: ["dicey", "didi", "di", "dianne", "dian"],
        dicey: ["dicie"],
        dick: ["rick", "richard"],
        dickson: ["dick"],
        domenic: ["dom", "nic"],
        dominic: ["dom", "nic"],
        dominick: ["dom", "nick", "nicky"],
        dominico: ["dom"],
        donald: ["dony", "donnie", "don", "donny"],
        donato: ["don"],
        donna: ["dona"],
        donovan: ["dony", "donnie", "don", "donny"],
        dorcus: ["darkey"],
        dorinda: ["dorothea", "dora"],
        doris: ["dora"],
        dorothea: ["doda", "dora"],
        dorothy: ["dortha", "dolly", "dot", "dotty", "dora", "dottie"],
        dotha: ["dotty"],
        dotty: ["dot"],
        douglas: ["doug"],
        drusilla: ["silla"],
        duncan: ["dunk"],
        earnest: ["ernestine", "ernie"],
        ebbie: ["eb"],
        ebenezer: ["ebbie", "eben", "eb"],
        eddie: ["ed"],
        eddy: ["ed"],
        edgar: ["ed", "eddie", "eddy"],
        edith: ["edie", "edye"],
        edmond: ["ed", "eddie", "eddy"],
        edmund: ["ed", "eddie", "ted", "eddy", "ned"],
        edna: ["edny"],
        eduardo: ["ed", "eddie", "eddy"],
        edward: ["teddy", "ed", "ned", "ted", "eddy", "eddie"],
        edwin: ["ed", "eddie", "win", "eddy", "ned"],
        edwina: ["edwin"],
        edyth: ["edie", "edye"],
        edythe: ["edie", "edye"],
        egbert: ["bert", "burt"],
        eighta: ["athy"],
        eileen: ["helen"],
        elaine: ["lainie", "helen"],
        elbert: ["albert", "bert"],
        elbertson: ["elbert", "bert"],
        eldora: ["dora"],
        eleanor: ["lanna", "nora", "nelly", "ellie", "elaine", "ellen", "lenora"],
        eleazer: ["lazar"],
        elena: ["helen"],
        elias: ["eli", "lee", "lias"],
        elijah: ["lige", "eli"],
        eliphalel: ["life"],
        eliphalet: ["left"],
        elisa: ["lisa"],
        elisha: ["lish", "eli"],
        eliza: ["elizabeth"],
        elizabeth: ["libby", "lisa", "lib", "lizzy", "lizzie", "eliza", "betsy", "liza", "betty", "bessie", "bess", "beth", "liz"],
        ella: ["ellen", "el"],
        ellen: ["nellie", "nell", "helen"],
        ellender: ["nellie", "ellen", "helen"],
        ellie: ["elly"],
        ellswood: ["elsey"],
        elminie: ["minnie"],
        elmira: ["ellie", "elly", "mira"],
        elnora: ["nora"],
        eloise: ["heloise", "louise"],
        elouise: ["louise"],
        elsie: ["elsey"],
        elswood: ["elsey"],
        elvira: ["elvie"],
        elwood: ["woody"],
        elysia: ["lisa", "lissa"],
        elze: ["elsey"],
        emanuel: ["manuel", "manny"],
        emeline: ["em", "emmy", "emma", "milly", "emily"],
        emil: ["emily", "em"],
        emily: ["emmy", "millie", "emma", "mel", "em"],
        emma: ["emmy", "em"],
        epaphroditius: ["dite", "ditus", "eppa", "dyche", "dyce"],
        ephraim: ["eph"],
        erasmus: ["raze", "rasmus"],
        eric: ["rick", "ricky"],
        ernest: ["ernie"],
        ernestine: ["teeny", "ernest", "tina", "erna"],
        erwin: ["irwin"],
        eseneth: ["senie"],
        essy: ["es"],
        estella: ["essy", "stella"],
        estelle: ["essy", "stella"],
        esther: ["hester", "essie"],
        eudicy: ["dicey"],
        eudora: ["dora"],
        eudoris: ["dossie", "dosie"],
        eugene: ["gene"],
        eunice: ["nicie"],
        euphemia: ["effie", "effy"],
        eurydice: ["dicey"],
        eustacia: ["stacia", "stacy"],
        eva: ["eve"],
        evaline: ["eva", "lena", "eve"],
        evangeline: ["ev", "evan", "vangie"],
        evelyn: ["evelina", "ev", "eve"],
        experience: ["exie"],
        ezekiel: ["zeke", "ez"],
        ezideen: ["ez"],
        ezra: ["ez"],
        faith: ["fay"],
        fallon: ["falon", "fal", "fall", "fallie", "fally", "falcon", "lon", "lonnie"],
        felicia: ["fel", "felix", "feli"],
        felicity: ["flick", "tick"],
        feltie: ["felty"],
        ferdinand: ["freddie", "freddy", "ferdie", "fred"],
        ferdinando: ["nando", "ferdie", "fred"],
        fidelia: ["delia"],
        fionna: ["fiona"],
        flora: ["florence"],
        florence: ["flossy", "flora", "flo"],
        floyd: ["lloyd"],
        fran: ["frannie"],
        frances: ["sis", "cissy", "frankie", "franniey", "fran", "francie", "frannie", "fanny", "franny"],
        francie: ["francine"],
        francine: ["franniey", "fran", "frannie", "francie", "franny"],
        francis: ["fran", "frankie", "frank"],
        frankie: ["frank", "francis"],
        franklin: ["fran", "frank"],
        franklind: ["fran", "frank"],
        freda: ["frieda"],
        frederica: ["frederick", "freddy", "erika", "erica", "rickey"],
        frederick: ["freddie", "freddy", "fritz", "fred", "erick", "ricky", "derick", "rick"],
        fredericka: ["freddy", "ricka", "freda", "frieda", "ericka", "rickey"],
        frieda: ["freddie", "freddy", "fred"],
        gabriel: ["gabe", "gabby"],
        gabriella: ["ella", "gabby"],
        gabrielle: ["ella", "gabby"],
        gareth: ["gary", "gare"],
        garrett: ["gare", "gary", "garry", "rhett", "garratt", "garret", "barrett", "jerry"],
        garrick: ["garri"],
        genevieve: ["jean", "eve", "jenny"],
        geoffrey: ["geoff", "jeff"],
        george: ["georgie"],
        georgiana: ["georgia"],
        georgine: ["george"],
        gerald: ["gerry", "jerry"],
        geraldine: ["gerry", "gerrie", "jerry", "dina", "gerri"],
        gerhardt: ["gay"],
        gertie: ["gertrude", "gert"],
        gertrude: ["gertie", "gert", "trudy"],
        gilbert: ["bert", "gil", "wilber"],
        giovanni: ["gio"],
        glenn: ["glen"],
        gloria: ["glory"],
        governor: ["govie"],
        greenberry: ["green", "berry"],
        greggory: ["gregg"],
        gregory: ["greg"],
        gretchen: ["margaret"],
        griselda: ["grissel"],
        gum: ["monty"],
        gus: ["gussie"],
        gustavus: ["gus", "gussie"],
        gwen: ["wendy"],
        gwendolyn: ["gwen", "wendy"],
        hailey: ["hayley", "haylee"],
        hamilton: ["ham"],
        hannah: ["nan", "nanny", "anna"],
        harold: ["hal", "harry", "hap", "haps"],
        harriet: ["hattie"],
        harrison: ["harry", "hap", "haps"],
        harry: ["harold", "henry", "hap", "haps"],
        haseltine: ["hassie"],
        haylee: ["hayley", "hailey"],
        hayley: ["hailey", "haylee"],
        heather: ["hetty"],
        helen: ["lena", "ella", "ellen", "ellie"],
        helena: ["eileen", "lena", "nell", "nellie", "eleanor", "elaine", "ellen", "aileen"],
        helene: ["lena", "ella", "ellen", "ellie"],
        heloise: ["lois", "eloise", "elouise"],
        henrietta: ["hank", "etta", "etty", "retta", "nettie", "henny"],
        henry: ["hank", "hal", "harry", "hap", "haps"],
        hephsibah: ["hipsie"],
        hepsibah: ["hipsie"],
        herbert: ["bert", "herb"],
        herman: ["harman", "dutch"],
        hermione: ["hermie"],
        hester: ["hessy", "esther", "hetty"],
        hezekiah: ["hy", "hez", "kiah"],
        hillary: ["hilary"],
        hipsbibah: ["hipsie"],
        hiram: ["hy"],
        honora: ["honey", "nora", "norry", "norah"],
        hopkins: ["hopp", "hop"],
        horace: ["horry"],
        hortense: ["harty", "tensey"],
        hosea: ["hosey", "hosie"],
        howard: ["hal", "howie"],
        hubert: ["bert", "hugh", "hub"],
        ian: ["john"],
        ignatius: ["natius", "iggy", "nate", "nace"],
        ignatzio: ["naz", "iggy", "nace"],
        immanuel: ["manuel", "emmanuel"],
        india: ["indie", "indy"],
        inez: ["agnes"],
        iona: ["onnie"],
        irene: ["rena"],
        irvin: ["irving"],
        irving: ["irv"],
        irwin: ["erwin"],
        isaac: ["ike", "zeke"],
        isabel: ["tibbie", "bell", "nib", "belle", "bella", "nibby", "ib", "issy"],
        isabella: ["tibbie", "nib", "belle", "bella", "nibby", "ib", "issy"],
        isabelle: ["tibbie", "nib", "belle", "bella", "nibby", "ib", "issy"],
        isadora: ["issy", "dora"],
        isadore: ["izzy"],
        isaiah: ["zadie", "zay"],
        isidore: ["izzy"],
        iva: ["ivy"],
        ivan: ["john"],
        jackson: ["jack"],
        jacob: ["jaap", "jake", "jay"],
        jacobus: ["jacob"],
        jacqueline: ["jackie", "jack", "jacqui"],
        jahoda: ["hody", "hodie", "hoda"],
        jakob: ["jake"],
        jalen: ["jay", "jaye", "len", "lenny", "lennie", "jaylin", "alen", "al", "haylen", "jaelin", "jaelyn", "jailyn", "jaylyn"],
        james: ["jimmy", "jim", "jamie", "jimmie", "jem"],
        jamey: ["james", "jamie"],
        jamie: ["james"],
        jane: ["janie", "jessie", "jean", "jennie"],
        janet: ["jan", "jessie"],
        janice: ["jan"],
        jannett: ["nettie"],
        jasper: ["jap", "casper"],
        jayme: ["jay"],
        jean: ["jane", "jeannie"],
        jeanette: ["jessie", "jean", "janet", "nettie"],
        jeanne: ["jane", "jeannie"],
        jebadiah: ["jeb"],
        jedediah: ["dyer", "jed", "diah"],
        jedidiah: ["dyer", "jed", "diah"],
        jefferey: ["jeff"],
        jefferson: ["sonny", "jeff"],
        jeffery: ["jeff"],
        jeffrey: ["geoff", "jeff"],
        jehiel: ["hiel"],
        jehu: ["hugh", "gee"],
        jemima: ["mima"],
        jennet: ["jessie", "jenny", "jenn"],
        jennifer: ["jennie", "jenn", "jen", "jenny", "jenni"],
        jeremiah: ["jereme", "jerry"],
        jeremy: ["jezza", "jez"],
        jerita: ["rita"],
        jerry: ["jereme", "geraldine", "gerry", "geri"],
        jessica: ["jessie", "jess"],
        jessie: ["jane", "jess", "janet"],
        jillian: ["jill"],
        jim: ["jimmie"],
        jincy: ["jane"],
        jinsy: ["jane"],
        joan: ["jo", "nonie"],
        joann: ["jo"],
        joanna: ["hannah", "jody", "jo", "joan", "jodi"],
        joanne: ["jo"],
        jody: ["jo"],
        joe: ["joey"],
        johann: ["john"],
        johanna: ["jo"],
        johannah: ["hannah", "jody", "joan", "nonie", "jo"],
        johannes: ["jonathan", "john", "johnny"],
        john: ["jon", "johnny", "jonny", "jonnie", "jack", "jock", "ian"],
        johnathan: ["johnathon", "jonathan", "jonathon", "jon", "jonny", "john", "johny", "jonnie", "nathan"],
        johnathon: ["johnathan", "jonathon", "jonathan", "jon", "jonny", "john", "johny", "jonnie"],
        jon: ["john", "johnny", "jonny", "jonnie"],
        jonathan: ["johnathan", "johnathon", "jonathon", "jon", "jonny", "john", "johny", "jonnie", "nathan"],
        jonathon: ["johnathan", "johnathon", "jonathan", "jon", "jonny", "john", "johny", "jonnie"],
        joseph: ["jody", "jos", "joe", "joey"],
        josephine: ["fina", "jody", "jo", "josey", "joey", "josie"],
        josetta: ["jettie"],
        josey: ["josophine"],
        joshua: ["jos", "josh", "joe"],
        josiah: ["jos"],
        josophine: ["jo", "joey", "josey"],
        joyce: ["joy"],
        juanita: ["nita", "nettie"],
        judah: ["juder", "jude"],
        judith: ["judie", "juda", "judy", "judi", "jude"],
        judson: ["sonny", "jud"],
        judy: ["judith"],
        julia: ["julie", "jill"],
        julian: ["jule"],
        julias: ["jule"],
        julie: ["julia", "jule"],
        june: ["junius"],
        junior: ["junie", "june", "jr"],
        justin: ["justus", "justina", "juston"],
        kaitlin: ["kait", "kaitie"],
        kaitlyn: ["kait", "kaitie"],
        kaitlynn: ["kait", "kaitie"],
        kalli: ["kali", "cali"],
        kameron: ["kam"],
        karla: ["carla", "carly"],
        kasey: ["k.c."],
        katarina: ["catherine", "tina"],
        kate: ["kay"],
        katelin: ["kay", "kate", "kaye"],
        katelyn: ["kay", "kate", "kaye"],
        katherine: ["kathy", "katy", "lena", "kittie", "kaye", "kit", "trina", "cathy", "kay", "kate", "cassie"],
        kathleen: ["kathy", "katy", "lena", "kittie", "kit", "trina", "cathy", "kay", "cassie"],
        kathryn: ["kathy", "katie", "kate"],
        katia: ["kate", "katie"],
        katy: ["kathy", "katie", "kate"],
        kayla: ["kay"],
        kelley: ["kellie", "kelli", "kelly"],
        kendall: ["ken", "kenny"],
        kendra: ["kenj", "kenji", "kay", "kenny"],
        kendrick: ["ken", "kenny"],
        kendrik: ["ken", "kenny"],
        kenneth: ["ken", "kenny", "kendrick"],
        kenny: ["ken", "kenneth"],
        kent: ["ken", "kenny", "kendrick"],
        kerry: ["kerri"],
        kevin: ["kev"],
        keziah: ["kizza", "kizzie"],
        kimberley: ["kim", "kimberly", "kimberli"],
        kimberly: ["kim", "kimberli", "kimberley"],
        kingsley: ["king"],
        kingston: ["king"],
        kit: ["kittie"],
        kris: ["chris"],
        kristel: ["kris"],
        kristen: ["chris"],
        kristin: ["chris"],
        kristine: ["kris", "kristy", "tina", "christy", "chris", "crissy"],
        kristopher: ["chris", "kris"],
        kristy: ["chris"],
        kymberly: ["kym"],
        lafayette: ["laffie", "fate"],
        lamont: ["monty"],
        laodicia: ["dicy", "cenia"],
        larry: ["laurence", "lawrence"],
        latisha: ["tish", "tisha"],
        laurel: ["laurie"],
        lauren: ["ren", "laurie"],
        laurence: ["lorry", "larry", "lon", "lonny", "lorne"],
        laurinda: ["laura", "lawrence"],
        lauryn: ["laurie"],
        laveda: ["veda"],
        laverne: ["vernon", "verna"],
        lavina: ["vina", "viney", "ina"],
        lavinia: ["vina", "viney", "ina"],
        lavonia: ["vina", "vonnie", "wyncha", "viney"],
        lavonne: ["von"],
        lawrence: ["lorry", "larry", "lon", "lonny", "lorne", "lawrie"],
        leanne: ["lea", "annie"],
        lecurgus: ["curg"],
        leilani: ["lani"],
        lemuel: ["lem"],
        lena: ["ellen"],
        lenora: ["nora", "lee"],
        leo: ["leon"],
        leonard: ["lineau", "leo", "leon", "len", "lenny"],
        leonidas: ["lee", "leon"],
        leonora: ["nora", "nell", "nellie"],
        leonore: ["nora", "honor", "elenor"],
        leroy: ["roy", "lee", "l.r."],
        lesley: ["les"],
        leslie: ["les"],
        lester: ["les"],
        letitia: ["tish", "titia", "lettice", "lettie"],
        levi: ["lee"],
        levicy: ["vicy"],
        levone: ["von"],
        lib: ["libby"],
        lidia: ["lyddy"],
        lil: ["lilly", "lily"],
        lillah: ["lil", "lilly", "lily", "lolly"],
        lillian: ["lil", "lilly", "lolly"],
        lilly: ["lily", "lil"],
        lincoln: ["link"],
        linda: ["lindy", "lynn"],
        lindsay: ["lindsey", "lindsie", "lindsy"],
        lindy: ["lynn"],
        lionel: ["leon"],
        lisa: ["liz"],
        littleberry: ["little", "berry", "l.b."],
        lizzie: ["liz"],
        lois: ["lou", "louise"],
        lonzo: ["lon"],
        lorelei: ["lori", "lorrie", "laurie"],
        lorenzo: ["loren"],
        loretta: ["etta", "lorrie", "retta", "lorie"],
        lorraine: ["lorrie", "lorie"],
        lotta: ["lottie"],
        lou: ["louis", "lu"],
        louis: ["lewis", "louise", "louie", "lou"],
        louisa: ["eliza", "lou", "lois"],
        louise: ["eliza", "lou", "lois"],
        louvinia: ["vina", "vonnie", "wyncha", "viney"],
        lucas: ["luke"],
        lucia: ["lucy", "lucius"],
        lucias: ["luke"],
        lucille: ["cille", "lu", "lucy", "lou"],
        lucina: ["sinah"],
        lucinda: ["lu", "lucy", "cindy", "lou"],
        lucretia: ["creasey"],
        lucy: ["lucinda"],
        luella: ["lula", "ella", "lu"],
        luke: ["lucas"],
        lunetta: ["nettie"],
        lurana: ["lura"],
        luther: ["luke"],
        lydia: ["lyddy"],
        lyndon: ["lindy", "lynn"],
        mabel: ["mehitabel", "amabel"],
        mac: ["mc"],
        mack: ["mac", "mc"],
        mackenzie: ["kenzy", "mac", "mack"],
        maddison: ["maddie", "maddi"],
        maddy: ["madelyn", "madeline", "madge"],
        madeline: ["maggie", "lena", "magda", "maddy", "madge", "maddie", "maddi", "madie", "maud"],
        madelyn: ["maddy", "madie"],
        madie: ["madeline", "madelyn"],
        madison: ["mattie", "maddy"],
        maegen: ["meg"],
        magdalena: ["maggie", "lena"],
        magdelina: ["lena", "magda", "madge", "maggie"],
        mahala: ["hallie"],
        makayla: ["kayla"],
        malachi: ["mally"],
        malcolm: ["mac", "mal", "malc"],
        malinda: ["lindy"],
        manda: ["mandy"],
        mandie: ["amanda"],
        mandy: ["amanda"],
        manerva: ["minerva", "nervie", "eve", "nerva"],
        manny: ["manuel"],
        manoah: ["noah"],
        manola: ["nonnie"],
        manuel: ["emanuel", "manny"],
        marcus: ["mark", "marc"],
        margaret: [
          "maggie",
          "meg",
          "peg",
          "midge",
          "margy",
          "margie",
          "madge",
          "peggy",
          "maggy",
          "marge",
          "daisy",
          "margery",
          "gretta",
          "rita"
        ],
        margaretta: ["maggie", "meg", "peg", "midge", "margie", "madge", "peggy", "marge", "daisy", "margery", "gretta", "rita"],
        margarita: [
          "maggie",
          "meg",
          "metta",
          "midge",
          "greta",
          "megan",
          "maisie",
          "madge",
          "marge",
          "daisy",
          "peggie",
          "rita",
          "margo"
        ],
        marge: ["margery", "margaret", "margaretta"],
        margie: ["marjorie"],
        marguerite: ["peggy"],
        mariah: ["mary", "maria"],
        marian: ["marianna", "marion"],
        marie: ["mae", "mary"],
        marietta: [
          "mariah",
          "mercy",
          "polly",
          "may",
          "molly",
          "mitzi",
          "minnie",
          "mollie",
          "mae",
          "maureen",
          "marion",
          "marie",
          "mamie",
          "mary",
          "maria"
        ],
        marilyn: ["mary"],
        marion: ["mary"],
        marissa: ["rissa"],
        marjorie: ["margy", "margie"],
        marni: ["marnie"],
        marsha: ["marcie", "mary", "marcia"],
        martha: ["marty", "mattie", "mat", "patsy", "patty"],
        martin: ["marty"],
        martina: ["tina"],
        martine: ["tine"],
        marv: ["marvin"],
        marvin: ["marv"],
        mary: ["mamie", "molly", "mae", "polly", "mitzi", "marie"],
        masayuki: ["masa"],
        mat: ["mattie"],
        mathew: ["mat", "maty", "matt"],
        mathilda: ["tillie", "patty"],
        matilda: ["tilly", "maud", "matty", "tilla"],
        matthew: ["thys", "matt", "thias", "mattie", "matty"],
        matthews: ["matt", "mattie", "matty"],
        matthias: ["thys", "matt", "thias"],
        maud: ["middy"],
        maureen: ["mary"],
        maurice: ["morey"],
        mavery: ["mave"],
        mavine: ["mave"],
        maximillian: ["max"],
        maxine: ["max"],
        maxwell: ["max"],
        may: ["mae"],
        mckenna: ["ken", "kenna", "meaka"],
        medora: ["dora"],
        megan: ["meg"],
        meghan: ["meg"],
        mehitabel: ["hetty", "mitty", "mabel", "hitty"],
        melanie: ["mellie"],
        melchizedek: ["zadock", "dick"],
        melinda: ["linda", "mel", "lynn", "mindy", "lindy"],
        melissa: ["lisa", "mel", "missy", "milly", "lissa"],
        mellony: ["mellia"],
        melody: ["lodi"],
        melvin: ["mel"],
        melvina: ["vina"],
        mercedes: ["merci", "sadie", "mercy"],
        merv: ["mervin"],
        mervin: ["merv"],
        mervyn: ["merv"],
        micajah: ["cage"],
        michael: ["micky", "mike", "micah", "mick", "mikey", "mickey"],
        micheal: ["mike", "miky", "mikey"],
        michelle: ["mickey", "shelley", "shely", "chelle", "shellie", "shelly"],
        mick: ["micky"],
        miguel: ["miguell", "miguael", "miguaell", "miguail", "miguaill", "miguayl", "miguayll", "michael", "mike", "miggy"],
        mike: ["micky", "mick", "michael"],
        mildred: ["milly"],
        millicent: ["missy", "milly"],
        minerva: ["minnie"],
        minnie: ["wilhelmina"],
        miranda: ["randy", "mandy", "mira"],
        miriam: ["mimi", "mitzi", "mitzie"],
        missy: ["melissa"],
        mitch: ["mitchell"],
        mitchell: ["mitch"],
        mitzi: ["mary", "mittie", "mitty"],
        mitzie: ["mittie", "mitty"],
        monet: ["nettie"],
        monica: ["monna", "monnie"],
        monteleon: ["monte"],
        montesque: ["monty"],
        montgomery: ["monty", "gum"],
        monty: ["lamont"],
        morris: ["morey"],
        mortimer: ["mort"],
        moses: ["amos", "mose", "moss"],
        muriel: ["mur"],
        myrtle: ["myrt", "myrti", "mert"],
        nadine: ["nada", "deedee"],
        nancy: ["ann", "nan", "nanny"],
        naomi: ["omi"],
        napoleon: ["nap", "nappy", "leon"],
        natalie: ["natty", "nettie"],
        natasha: ["tasha", "nat"],
        nathan: ["nate", "nat"],
        nathaniel: ["than", "nathan", "nate", "nat", "natty"],
        nelle: ["nelly"],
        nelson: ["nels"],
        newt: ["newton"],
        newton: ["newt"],
        nicholas: ["nick", "claes", "claas", "nic", "nicky", "nico", "nickie"],
        nicholette: ["nickey", "nikki", "cole", "nicki", "nicky", "nichole", "nicole"],
        nicodemus: ["nick", "nic", "nicky", "nico", "nickie"],
        nicole: ["nole", "nikki", "cole", "nicki", "nicky"],
        nikolas: ["nick", "claes", "nic", "nicky", "nico", "nickie"],
        nikole: ["nikki"],
        nora: ["nonie"],
        norbert: ["bert", "norby"],
        norbusamte: ["norbu"],
        norman: ["norm"],
        nowell: ["noel"],
        obadiah: ["dyer", "obed", "obie", "diah"],
        obediah: ["obie"],
        obedience: ["obed", "beda", "beedy", "biddie"],
        obie: ["obediah"],
        octavia: ["tave", "tavia"],
        odell: ["odo"],
        olive: ["nollie", "livia", "ollie"],
        oliver: ["ollie"],
        olivia: ["nollie", "livia", "ollie"],
        ollie: ["oliver"],
        onicyphorous: ["cyphorus", "osaforus", "syphorous", "one", "cy", "osaforum"],
        orilla: ["rilly", "ora"],
        orlando: ["roland"],
        orphelia: ["phelia"],
        ossy: ["ozzy"],
        oswald: ["ozzy", "waldo", "ossy"],
        otis: ["ode", "ote"],
        pamela: ["pam"],
        pandora: ["dora"],
        parmelia: ["amelia", "milly", "melia"],
        parthenia: ["teeny", "parsuny", "pasoonie", "phenie"],
        patience: ["pat", "patty"],
        patricia: ["tricia", "pat", "patsy", "patty", "patti", "trish", "trisha"],
        patrick: ["pate", "peter", "pat", "patsy", "paddy"],
        patsy: ["patty"],
        patty: ["patricia"],
        paul: ["polly"],
        paula: ["polly", "lina"],
        paulina: ["polly", "lina"],
        pauline: ["polly"],
        peggy: ["peg"],
        pelegrine: ["perry"],
        penelope: ["penny"],
        percival: ["percy"],
        peregrine: ["perry"],
        permelia: ["melly", "milly", "mellie"],
        pernetta: ["nettie"],
        persephone: ["seph", "sephy"],
        peter: ["pete", "pate"],
        petronella: ["nellie"],
        pheney: ["josephine"],
        pheriba: ["pherbia", "ferbie"],
        philadelphia: ["delphia"],
        philander: ["fie"],
        philetus: ["leet", "phil"],
        philinda: ["linda", "lynn", "lindy"],
        philip: ["phil", "pip"],
        philipina: ["phoebe", "penie", "pip"],
        phillip: ["phil", "pip"],
        philly: ["delphia"],
        philomena: ["menaalmena"],
        phoebe: ["fifi"],
        pinckney: ["pink"],
        pleasant: ["ples"],
        pocahontas: ["pokey"],
        posthuma: ["humey"],
        prescott: ["scotty", "scott", "pres"],
        priscilla: ["prissy", "cissy", "cilla"],
        providence: ["provy"],
        prudence: ["prue", "prudy"],
        prudy: ["prudence"],
        rachel: ["shelly", "rachael"],
        rafaela: ["rafa"],
        ramona: ["mona"],
        randolph: ["dolph", "randy"],
        raphael: ["ralph"],
        ray: ["raymond"],
        raymond: ["ray"],
        reba: ["beck", "becca"],
        rebecca: ["beck", "becca", "reba", "becky"],
        reggie: ["reginald", "reg"],
        regina: ["reggie", "gina"],
        reginald: ["reggie", "naldo", "reg", "renny"],
        relief: ["leafa"],
        reuben: ["rube"],
        reynold: ["reginald"],
        rhoda: ["rodie"],
        rhodella: ["della"],
        rhyna: ["rhynie"],
        ricardo: ["rick", "ricky"],
        rich: ["dick", "rick"],
        richard: ["dick", "dickon", "dickie", "dicky", "rick", "rich", "ricky", "richie"],
        rick: ["ricky"],
        ricky: ["dick", "rich"],
        robert: ["hob", "hobkin", "dob", "rob", "bobby", "dobbin", "bob", "bill", "billy", "robby"],
        roberta: ["robbie", "bert", "bobbie", "birdie", "bertie", "roby", "birtie"],
        roberto: ["rob"],
        roderick: ["rod", "erick", "rickie", "roddy"],
        rodger: ["roge", "bobby", "hodge", "rod", "robby", "rupert", "robin"],
        rodney: ["rod"],
        roger: ["roge", "bobby", "hodge", "rod", "robby", "rupert", "robin"],
        roland: ["rollo", "lanny", "orlando", "rolly"],
        ron: ["ronnie", "ronny"],
        ronald: ["naldo", "ron", "ronny", "ronnie"],
        ronny: ["ronald"],
        rosa: ["rose"],
        rosabel: ["belle", "roz", "rosa", "rose"],
        rosabella: ["belle", "roz", "rosa", "rose", "bella"],
        rosaenn: ["ann"],
        rosaenna: ["ann"],
        rosalinda: ["linda", "roz", "rosa", "rose"],
        rosalyn: ["linda", "roz", "rosa", "rose"],
        roscoe: ["ross"],
        rose: ["rosie"],
        roseann: ["rose", "ann", "rosie", "roz"],
        roseanna: ["rose", "ann", "rosie", "roz"],
        roseanne: ["ann"],
        rosemary: ["rosemarie", "marie", "mary", "rose", "rosey"],
        rosina: ["sina"],
        roxane: ["rox", "roxie"],
        roxanna: ["roxie", "rose", "ann"],
        roxanne: ["roxie", "rose", "ann"],
        rudolph: ["dolph", "rudy", "olph", "rolf"],
        rudolphus: ["dolph", "rudy", "olph", "rolf"],
        russell: ["russ", "rusty"],
        ryan: ["ry"],
        sabrina: ["brina"],
        safieel: ["safie"],
        salome: ["loomie"],
        salvador: ["sal", "sally"],
        sam: ["sammy"],
        samantha: ["sam", "sammy", "mantha"],
        sampson: ["sam", "sammy"],
        samson: ["sam", "sammy"],
        samuel: ["sam", "sammy"],
        samyra: ["sam", "sammy", "myra"],
        sandra: ["sandy", "cassandra"],
        sandy: ["sandra"],
        sanford: ["sandy"],
        sarah: ["sally", "sadie", "sara"],
        sarilla: ["silla"],
        savannah: ["vannie", "anna", "savanna"],
        scott: ["scotty", "sceeter", "squat", "scottie"],
        sebastian: ["sebby", "seb"],
        selma: ["anselm"],
        serena: ["rena"],
        serilla: ["rilla"],
        seymour: ["see", "morey"],
        shaina: ["sha", "shay"],
        sharon: ["sha", "shay"],
        shaun: ["shawn"],
        shawn: ["shaun"],
        sheila: ["cecilia"],
        sheldon: ["shelly"],
        shelton: ["tony", "shel", "shelly"],
        sheridan: ["dan", "danny", "sher"],
        sheryl: ["sher", "sheri", "sherry", "sherryl", "sherri", "cheri", "cherie"],
        shirley: ["sherry", "lee", "shirl"],
        sibbilla: ["sybill", "sibbie", "sibbell"],
        sidney: ["syd", "sid"],
        sigfired: ["sid"],
        sigfrid: ["sid"],
        sigismund: ["sig"],
        silas: ["si"],
        silence: ["liley"],
        silvester: ["vester", "si", "sly", "vest", "syl"],
        simeon: ["si", "sion"],
        simon: ["si", "sion"],
        smith: ["smitty"],
        socrates: ["crate"],
        solomon: ["sal", "salmon", "sol", "solly", "saul", "zolly"],
        sondra: ["dre", "sonnie"],
        sophia: ["sophie"],
        sophronia: ["frona", "sophia", "fronia"],
        stacey: ["stacy", "staci", "stacie"],
        stacie: ["stacy", "stacey", "staci"],
        stacy: ["staci"],
        stephan: ["steve"],
        stephanie: ["stephie", "annie", "steph", "stevie", "stephine", "stephany", "stephani", "steffi", "steffie"],
        stephen: ["steve", "steph"],
        steven: ["steve", "steph", "stevie"],
        stuart: ["stu"],
        sue: ["susie", "susan"],
        sullivan: ["sully", "van"],
        susan: ["hannah", "susie", "sue", "sukey", "suzie"],
        susannah: ["hannah", "susie", "sue", "sukey"],
        susie: ["suzie"],
        suzanne: ["suki", "sue", "susie"],
        sybill: ["sibbie"],
        sydney: ["sid"],
        sylvanus: ["sly", "syl"],
        sylvester: ["sy", "sly", "vet", "syl", "vester", "si", "vessie"],
        tabby: ["tabitha"],
        tabitha: ["tabby"],
        tamarra: ["tammy"],
        tammie: ["tammy", "tami"],
        tammy: ["tammie", "tami"],
        tanafra: ["tanny"],
        tasha: ["tash", "tashie"],
        ted: ["teddy"],
        temperance: ["tempy"],
        terence: ["terry"],
        teresa: ["terry", "tess", "tessa", "tessie"],
        terri: ["terrie", "terry", "teri"],
        terry: ["terence"],
        tess: ["teresa", "theresa"],
        tessa: ["teresa", "theresa"],
        thad: ["thaddeus"],
        thaddeus: ["thad"],
        theo: ["theodore"],
        theodora: ["dora"],
        theodore: ["theo", "ted", "teddy"],
        theodosia: ["theo", "dosia", "theodosius"],
        theophilus: ["ophi"],
        theotha: ["otha"],
        theresa: ["tessie", "thirza", "tessa", "terry", "tracy", "tess", "thursa", "traci", "tracie"],
        thom: ["thomas", "tommy", "tom"],
        thomas: ["thom", "tommy", "tom"],
        thomasa: ["tamzine"],
        tiffany: ["tiff", "tiffy"],
        tilford: ["tillie"],
        tim: ["timmy"],
        timothy: ["tim", "timmy"],
        tina: ["christina"],
        tisha: ["tish"],
        tobias: ["bias", "toby"],
        tom: ["thomas", "tommy"],
        tony: ["anthony"],
        tranquilla: ["trannie", "quilla"],
        trish: ["trisha", "patricia"],
        trix: ["trixie"],
        trudy: ["gertrude"],
        tryphena: ["phena"],
        unice: ["eunice", "nicie"],
        uriah: ["riah"],
        ursula: ["sulie", "sula"],
        valentina: ["felty", "vallie", "val"],
        valentine: ["felty"],
        valeri: ["valerie", "val"],
        valerie: ["val"],
        vanburen: ["buren"],
        vandalia: ["vannie"],
        vanessa: ["essa", "vanna", "nessa"],
        vernisee: ["nicey"],
        veronica: ["vonnie", "ron", "ronna", "ronie", "frony", "franky", "ronnie", "ronny"],
        vic: ["vicki", "vickie", "vicky", "victor"],
        vicki: ["vickie", "vicky", "victoria"],
        victor: ["vic"],
        victoria: ["torie", "vic", "vicki", "tory", "vicky", "tori", "torri", "torrie", "vickie"],
        vijay: ["vij"],
        vincent: ["vic", "vince", "vinnie", "vin", "vinny"],
        vincenzo: ["vic", "vinnie", "vin", "vinny", "vince"],
        vinson: ["vinny", "vinnie", "vin", "vince"],
        viola: ["ola", "vi"],
        violetta: ["lettie"],
        virginia: ["jane", "jennie", "ginny", "virgy", "ginger"],
        vivian: ["vi", "viv"],
        waldo: ["ozzy", "ossy"],
        wallace: ["wally"],
        wally: ["walt"],
        walter: ["wally", "walt"],
        washington: ["wash"],
        webster: ["webb"],
        wendy: ["wen"],
        wesley: ["wes"],
        westley: ["west", "wes", "farmboy"],
        wilber: ["will", "bert"],
        wilbur: ["willy", "willie", "will"],
        wilda: ["willie"],
        wilfred: ["will", "willie", "fred", "wil"],
        wilhelm: ["wil", "willie"],
        wilhelmina: ["mina", "wilma", "willie", "minnie"],
        will: ["bill", "willie", "wilbur", "fred"],
        william: ["willy", "bell", "bela", "bill", "will", "billy", "willie", "wil"],
        willie: ["william", "fred"],
        willis: ["willy", "bill"],
        wilma: ["william", "billiewilhelm"],
        wilson: ["will", "willy", "willie"],
        winfield: ["field", "winny", "win"],
        winifred: ["freddie", "winnie", "winnet"],
        winnie: ["winnifred"],
        winnifred: ["freddie", "freddy", "winny", "winnie", "fred"],
        winny: ["winnifred"],
        winton: ["wint"],
        woodrow: ["woody", "wood", "drew"],
        yeona: ["onie", "ona"],
        yoshihiko: ["yoshi"],
        yulan: ["lan", "yul"],
        yvonne: ["vonna"],
        zach: ["zack", "zak"],
        zachariah: ["zachy", "zach", "zeke", "zac", "zack", "zak", "zakk"],
        zachary: ["zachy", "zach", "zeke", "zac", "zack", "zak", "zakk"],
        zachery: ["zachy", "zach", "zeke", "zac", "zack", "zak", "zakk"],
        zack: ["zach", "zak"],
        zebedee: ["zeb"],
        zedediah: ["dyer", "zed", "diah"],
        zephaniah: ["zeph"]
      };
      return this._memo;
    }
  };
  var states = {
    AL: "Alabama",
    AK: "Alaska",
    AZ: "Arizona",
    AR: "Arkansas",
    CA: "California",
    CO: "Colorado",
    CT: "Connecticut",
    DC: "District of Columbia",
    DE: "Delaware",
    FL: "Florida",
    GA: "Georgia",
    HI: "Hawaii",
    ID: "Idaho",
    IL: "Illinois",
    IN: "Indiana",
    IA: "Iowa",
    KS: "Kansas",
    KY: "Kentucky",
    LA: "Louisiana",
    ME: "Maine",
    MD: "Maryland",
    MA: "Massachusetts",
    MI: "Michigan",
    MN: "Minnesota",
    MS: "Mississippi",
    MO: "Missouri",
    MT: "Montana",
    NE: "Nebraska",
    NV: "Nevada",
    NH: "New Hampshire",
    NJ: "New Jersey",
    NM: "New Mexico",
    NY: "New York",
    NC: "North Carolina",
    ND: "North Dakota",
    OH: "Ohio",
    OK: "Oklahoma",
    OR: "Oregon",
    PA: "Pennsylvania",
    RI: "Rhode Island",
    SC: "South Carolina",
    SD: "South Dakota",
    TN: "Tennessee",
    TX: "Texas",
    UT: "Utah",
    VT: "Vermont",
    VA: "Virginia",
    WA: "Washington",
    WV: "West Virginia",
    WI: "Wisconsin",
    WY: "Wyoming"
  };

  // src/features/broker-protection/comparisons/is-same-name.js
  function isSameName(fullNameExtracted, userFirstName, userMiddleName, userLastName, userSuffix) {
    if (!fullNameExtracted) {
      return false;
    }
    if (!userFirstName || !userLastName) return false;
    fullNameExtracted = fullNameExtracted.toLowerCase().trim().replace(".", "");
    userFirstName = userFirstName.toLowerCase();
    userMiddleName = userMiddleName ? userMiddleName.toLowerCase() : null;
    userLastName = userLastName.toLowerCase();
    userSuffix = userSuffix ? userSuffix.toLowerCase() : null;
    const names2 = getNames(userFirstName);
    for (const firstName of names2) {
      const nameCombo1 = `${firstName} ${userLastName}`;
      if (fullNameExtracted === nameCombo1) {
        return true;
      }
      if (!userMiddleName) {
        const combinedLength = firstName.length + userLastName.length;
        const matchesFirstAndLast = fullNameExtracted.startsWith(firstName) && fullNameExtracted.endsWith(userLastName) && fullNameExtracted.length > combinedLength;
        if (matchesFirstAndLast) {
          return true;
        }
      }
      if (userSuffix) {
        const nameCombo1WithSuffix = `${firstName} ${userLastName} ${userSuffix}`;
        if (fullNameExtracted === nameCombo1WithSuffix) {
          return true;
        }
      }
      if (userLastName && userLastName.includes("-")) {
        const userLastNameOption2 = userLastName.split("-").join(" ");
        const userLastNameOption3 = userLastName.split("-").join("");
        const userLastNameOption4 = userLastName.split("-")[0];
        const comparisons = [
          `${firstName} ${userLastNameOption2}`,
          `${firstName} ${userLastNameOption3}`,
          `${firstName} ${userLastNameOption4}`
        ];
        if (comparisons.includes(fullNameExtracted)) {
          return true;
        }
      }
      if (userFirstName && userFirstName.includes("-")) {
        const userFirstNameOption2 = userFirstName.split("-").join(" ");
        const userFirstNameOption3 = userFirstName.split("-").join("");
        const userFirstNameOption4 = userFirstName.split("-")[0];
        const comparisons = [
          `${userFirstNameOption2} ${userLastName}`,
          `${userFirstNameOption3} ${userLastName}`,
          `${userFirstNameOption4} ${userLastName}`
        ];
        if (comparisons.includes(fullNameExtracted)) {
          return true;
        }
      }
      if (userMiddleName) {
        const comparisons = [
          `${firstName} ${userMiddleName} ${userLastName}`,
          `${firstName} ${userMiddleName} ${userLastName} ${userSuffix}`,
          `${firstName} ${userMiddleName[0]} ${userLastName}`,
          `${firstName} ${userMiddleName[0]} ${userLastName} ${userSuffix}`,
          `${firstName} ${userMiddleName}${userLastName}`,
          `${firstName} ${userMiddleName}${userLastName} ${userSuffix}`
        ];
        if (comparisons.includes(fullNameExtracted)) {
          return true;
        }
        if (userLastName && userLastName.includes("-")) {
          const userLastNameOption2 = userLastName.split("-").join(" ");
          const userLastNameOption3 = userLastName.split("-").join("");
          const userLastNameOption4 = userLastName.split("-")[0];
          const comparisons2 = [
            `${firstName} ${userMiddleName} ${userLastNameOption2}`,
            `${firstName} ${userMiddleName} ${userLastNameOption4}`,
            `${firstName} ${userMiddleName[0]} ${userLastNameOption2}`,
            `${firstName} ${userMiddleName[0]} ${userLastNameOption3}`,
            `${firstName} ${userMiddleName[0]} ${userLastNameOption4}`
          ];
          if (comparisons2.includes(fullNameExtracted)) {
            return true;
          }
        }
        if (userFirstName && userFirstName.includes("-")) {
          const userFirstNameOption2 = userFirstName.split("-").join(" ");
          const userFirstNameOption3 = userFirstName.split("-").join("");
          const userFirstNameOption4 = userFirstName.split("-")[0];
          const comparisons2 = [
            `${userFirstNameOption2} ${userMiddleName} ${userLastName}`,
            `${userFirstNameOption3} ${userMiddleName} ${userLastName}`,
            `${userFirstNameOption4} ${userMiddleName} ${userLastName}`,
            `${userFirstNameOption2} ${userMiddleName[0]} ${userLastName}`,
            `${userFirstNameOption3} ${userMiddleName[0]} ${userLastName}`,
            `${userFirstNameOption4} ${userMiddleName[0]} ${userLastName}`
          ];
          if (comparisons2.includes(fullNameExtracted)) {
            return true;
          }
        }
      }
    }
    return false;
  }
  function getNames(name) {
    if (!noneEmptyString(name)) {
      return /* @__PURE__ */ new Set();
    }
    name = name.toLowerCase();
    const nicknames = names.nicknames;
    return /* @__PURE__ */ new Set([name, ...getNicknames(name, nicknames), ...getFullNames(name, nicknames)]);
  }
  function getNicknames(name, nicknames) {
    const emptySet = /* @__PURE__ */ new Set();
    if (!noneEmptyString(name)) {
      return emptySet;
    }
    name = name.toLowerCase();
    if (Object.prototype.hasOwnProperty.call(nicknames, name)) {
      return new Set(nicknames[name]);
    }
    return emptySet;
  }
  function getFullNames(name, nicknames) {
    const fullNames = /* @__PURE__ */ new Set();
    if (!noneEmptyString(name)) {
      return fullNames;
    }
    name = name.toLowerCase();
    for (const fullName of Object.keys(nicknames)) {
      if (nicknames[fullName]?.includes(name)) {
        fullNames.add(fullName);
      }
    }
    return fullNames;
  }
  function noneEmptyString(input) {
    if (typeof input !== "string") return false;
    return input.trim().length > 0;
  }

  // src/features/broker-protection/comparisons/address.js
  init_define_import_meta_trackerLookup();
  function addressMatch(userAddresses, foundAddresses) {
    return userAddresses.some((user) => {
      return foundAddresses.some((found) => {
        return matchingPair(user.city, found.city) && matchingPair(user.state, found.state);
      });
    });
  }
  function getStateFromAbbreviation(stateAbbreviation) {
    if (stateAbbreviation == null || stateAbbreviation.trim() === "") {
      return null;
    }
    const state = stateAbbreviation.toUpperCase();
    return states[state] || null;
  }

  // src/features/broker-protection/extractors/age.js
  init_define_import_meta_trackerLookup();
  function extractAge(select2, root, spec) {
    const [first] = selectStrings(select2, root, spec);
    return first?.match(/\d+/)?.[0] ?? null;
  }

  // src/features/broker-protection/extractors/name.js
  init_define_import_meta_trackerLookup();
  function extractName(select2, root, spec) {
    const [first] = selectStrings(select2, root, spec);
    return first ? first.replace(/\n/g, " ").trim() : null;
  }
  function extractAlternativeNames(select2, root, spec) {
    return selectStrings(select2, root, spec).flatMap((value) => stringToList(value, spec.separator));
  }

  // src/features/broker-protection/extractors/address.js
  init_define_import_meta_trackerLookup();
  var import_parse_address = __toESM(require_address(), 1);
  function extractCityState(select2, root, spec) {
    if (isNestedCityStateSpec(spec)) {
      const { city, state } = spec;
      return select2(root, spec.selector, spec.findElements).flatMap(
        (row) => cityStatePartToCombo({
          city: firstString(selectStrings(select2, row, city)),
          state: state ? firstString(selectStrings(select2, row, state)) : ""
        })
      );
    }
    return cityStateCombosFromStrings(selectStrings(select2, root, spec), spec.separator);
  }
  function isNestedCityStateSpec(spec) {
    return Object.prototype.hasOwnProperty.call(spec, "city") && Boolean(
      /** @type {import('../actions/extract.js').NestedCityStateSpec} */
      spec.city?.selector
    );
  }
  function cityStateCombosFromStrings(strings, separator) {
    return strings.flatMap((value) => getCityStateCombos(stringToList(value, separator)));
  }
  function cityStatePartToCombo({ city, state }) {
    const trimmedCity = city.trim();
    if (!trimmedCity) return [];
    const trimmedState = state.trim();
    if (!trimmedState) return [{ city: trimmedCity, state: null }];
    const normalized = normalizeState(trimmedState);
    return normalized ? [{ city: trimmedCity, state: normalized }] : [];
  }
  function extractAddressFull(select2, root, spec) {
    return selectStrings(select2, root, spec).map((str) => str.replace("\n", " ")).flatMap((str) => stringToList(str, spec.separator)).map((str) => import_parse_address.default.parseLocation(str) || {}).filter((parsed) => Boolean(parsed?.city)).map((addr) => {
      return { city: addr.city, state: addr.state || null };
    });
  }
  function getCityStateCombos(inputList) {
    const output = [];
    for (let item of inputList) {
      let words;
      item = item.replace(/,?\s*\d{5}(-\d{4})?/, "");
      item = item.replace(/,$/, "");
      if (item.includes(",")) {
        words = item.split(",").map((item2) => item2.trim());
      } else {
        words = item.split(" ").map((item2) => item2.trim());
      }
      if (words.length === 1) {
        continue;
      }
      const stateCandidate = words.pop();
      const city = words.join(" ");
      const state = stateCandidate ? normalizeState(stateCandidate) : null;
      if (stateCandidate && !state) {
        continue;
      }
      output.push({ city, state });
    }
    return output;
  }
  var stateNameToAbbreviation = null;
  function normalizeState(token) {
    const trimmed = token.trim();
    if (!trimmed) {
      return null;
    }
    const upper = trimmed.toUpperCase();
    if (Object.prototype.hasOwnProperty.call(states, upper)) {
      return upper;
    }
    if (stateNameToAbbreviation === null) {
      stateNameToAbbreviation = /** @type {Record<string, string>} */
      /* @__PURE__ */ Object.create(null);
      for (const [abbreviation, name] of Object.entries(states)) {
        stateNameToAbbreviation[name.toLowerCase()] = abbreviation;
      }
    }
    return stateNameToAbbreviation[trimmed.toLowerCase()] ?? null;
  }

  // src/features/broker-protection/extractors/phone.js
  init_define_import_meta_trackerLookup();
  function extractPhone(select2, root, spec) {
    return selectStrings(select2, root, spec).flatMap((str) => stringToList(str, spec.separator)).map((str) => str.replace(/\D/g, ""));
  }

  // src/features/broker-protection/extractors/relatives.js
  init_define_import_meta_trackerLookup();
  function extractRelatives(select2, root, spec) {
    return selectStrings(select2, root, spec).flatMap((value) => stringToList(value, spec.separator)).map((value) => (
      /** @type {string} */
      value.split(",")[0]
    ));
  }

  // src/features/broker-protection/extractors/profile-url.js
  init_define_import_meta_trackerLookup();
  function extractProfileUrl(select2, root, spec) {
    const rawUrl = spec.source === "pageUrl" ? firstString(cleanArray(globalThis.location.href)) : firstString(profileUrlStrings(select2, root, spec));
    if (!rawUrl) return null;
    const url = parseProfileUrl(rawUrl);
    const profileUrl = url?.href ?? rawUrl;
    const profile = { profileUrl, identifier: profileUrl };
    if (spec.identifierType && spec.identifier) {
      profile.identifier = getIdFromProfileUrl(url, spec.identifierType, spec.identifier) ?? profileUrl;
    }
    return profile;
  }
  function profileUrlStrings(select2, root, spec) {
    return cleanArray(
      select2(root, spec.selector, spec.findElements).map((element) => readProfileUrlValue(element, spec)).map((value) => value ? shapeString(value, spec) : value)
    );
  }
  function readProfileUrlValue(element, spec) {
    if (spec.attribute && "getAttribute" in element) {
      return element.getAttribute?.(spec.attribute) ?? null;
    }
    if ("href" in element && element.href) {
      return element.href;
    }
    if ("innerText" in element) {
      return element.innerText ?? null;
    }
    if ("textContent" in element) {
      return element.textContent ?? null;
    }
    return void 0;
  }
  function parseProfileUrl(profileUrl) {
    try {
      return new URL(profileUrl, globalThis.location.href);
    } catch {
      return null;
    }
  }
  function getIdFromProfileUrl(url, identifierType, identifier) {
    if (!url) return null;
    if (identifierType === "param" && url.searchParams.has(identifier)) {
      return url.searchParams.get(identifier) || null;
    }
    return null;
  }
  var ProfileHashTransformer = class {
    /**
     * @param {Record<string, any>} profile
     * @param {Record<string, any> } params
     * @return {Promise<Record<string, any>>}
     */
    async transform(profile, params) {
      if (params?.profileUrl?.identifierType !== "hash") {
        return profile;
      }
      return {
        ...profile,
        identifier: await hashObject(profile)
      };
    }
  };

  // src/features/broker-protection/actions/extract.js
  async function extract(action, userData, root = document) {
    const extractResult = extractProfiles(action, userData, root);
    if ("error" in extractResult) {
      return new ErrorResponse({ actionID: action.id, message: extractResult.error });
    }
    const filteredPromises = extractResult.results.filter((x2) => x2.result === true).map((x2) => aggregateFields(x2.scrapedData)).map((profile) => applyPostTransforms(profile, action.profile));
    const filtered = await Promise.all(filteredPromises);
    const debugResults = extractResult.results.map((result) => result.asData());
    return new SuccessResponse({
      actionID: action.id,
      actionType: action.actionType,
      response: filtered,
      meta: {
        userData,
        extractResults: debugResults
      }
    });
  }
  function select(root, selector, all = false) {
    if (!selector) return [];
    const node = (
      /** @type {HTMLElement} */
      root
    );
    return all ? cleanArray(getElements(node, selector)) : cleanArray(getElement(node, selector) || getElementMatches(node, selector));
  }
  function extractProfiles(action, userData, root = document) {
    const profilesElementList = getElements(root, action.selector) ?? [];
    if (profilesElementList.length === 0) {
      if (!action.noResultsSelector) {
        return { error: "no root elements found for " + action.selector };
      }
      const foundNoResultsElement = getElement(root, action.noResultsSelector);
      if (!foundNoResultsElement) {
        return { error: "no results found for " + action.selector + " or the no results selector " + action.noResultsSelector };
      }
    }
    return {
      results: profilesElementList.map((element) => {
        const scrapedData = createProfile(select, element, action.profile);
        const { result, score, matchedFields } = scrapedDataMatchesUserData(userData, scrapedData);
        return new ProfileResult({
          scrapedData,
          result,
          score,
          element,
          matchedFields
        });
      })
    };
  }
  var extractors = {
    name: extractName,
    age: extractAge,
    alternativeNamesList: extractAlternativeNames,
    relativesList: extractRelatives,
    phone: extractPhone,
    phoneList: extractPhone,
    addressFull: extractAddressFull,
    addressFullList: extractAddressFull,
    addressCityState: extractCityState,
    addressCityStateList: extractCityState,
    profileUrl: extractProfileUrl
  };
  function createProfile(select2, root, profileSpec) {
    const output = {};
    for (const [field, fieldSpec] of Object.entries(profileSpec)) {
      const extractField = extractors[field];
      if (!extractField) continue;
      output[field] = extractField(select2, root, fieldSpec ?? {}) || null;
    }
    return output;
  }
  function selectStrings(select2, root, spec) {
    return cleanArray(stringsFromElements(select2(root, spec.selector, spec.findElements), spec));
  }
  function stringsFromElements(elements, spec) {
    return elements.map((element) => {
      const value = readElementText(element, spec);
      return value ? shapeString(value, spec) : value;
    });
  }
  function readElementText(element, spec) {
    if (spec.attribute && "getAttribute" in element) {
      return element.getAttribute?.(spec.attribute) ?? null;
    }
    if ("innerText" in element) {
      return element.innerText ?? null;
    }
    if ("textContent" in element) {
      return element.textContent ?? null;
    }
    return void 0;
  }
  function shapeString(value, spec) {
    if (spec.afterText) {
      value = splitOnce(value, parseRegexFromString(spec.afterText), "after")?.trim() || value;
    }
    if (spec.beforeText) {
      value = splitOnce(value, parseRegexFromString(spec.beforeText), "before")?.trim() || value;
    }
    return removeCommonSuffixesAndPrefixes(value);
  }
  function firstString(strings) {
    const [value] = strings;
    return typeof value === "string" ? value : "";
  }
  function scrapedDataMatchesUserData(userData, scrapedData) {
    const matchedFields = [];
    if (isSameName(scrapedData.name, userData.firstName, userData.middleName, userData.lastName)) {
      matchedFields.push("name");
    } else {
      return { matchedFields, score: matchedFields.length, result: false };
    }
    if (scrapedData.age) {
      if (isSameAge(scrapedData.age, userData.age)) {
        matchedFields.push("age");
      } else {
        return { matchedFields, score: matchedFields.length, result: false };
      }
    }
    const addressFields = ["addressCityState", "addressCityStateList", "addressFull", "addressFullList"];
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
        matchedFields.push("phone");
        return { matchedFields, score: matchedFields.length, result: true };
      }
    }
    return { matchedFields, score: matchedFields.length, result: false };
  }
  function aggregateFields(profile) {
    const combinedAddresses = [
      ...profile.addressCityState || [],
      ...profile.addressCityStateList || [],
      ...profile.addressFullList || [],
      ...profile.addressFull || []
    ];
    const addressMap = new Map(combinedAddresses.map((addr) => [`${addr.city},${addr.state}`, addr]));
    const addresses = sortAddressesByStateAndCity([...addressMap.values()]);
    const phoneArray = profile.phone || [];
    const phoneListArray = profile.phoneList || [];
    const phoneNumbers = [.../* @__PURE__ */ new Set([...phoneArray, ...phoneListArray])].sort((a2, b2) => parseInt(a2) - parseInt(b2));
    const relatives = [...new Set(profile.relativesList)].sort();
    const alternativeNames = [...new Set(profile.alternativeNamesList)].sort();
    return {
      name: profile.name,
      alternativeNames,
      age: profile.age,
      addresses,
      phoneNumbers,
      relatives,
      ...profile.profileUrl
    };
  }
  async function applyPostTransforms(profile, profileSpec) {
    const transforms = [
      // creates a hash if needed
      new ProfileHashTransformer()
    ];
    let output = profile;
    for (const knownTransform of transforms) {
      output = await knownTransform.transform(output, profileSpec);
    }
    return output;
  }
  function parseRegexFromString(value) {
    const match = typeof value === "string" && value.match(/^\/(.+)\/(i?)$/);
    return match ? new RegExp(match[1], match[2]) : value;
  }
  function splitOnce(value, matcher, keep) {
    if (matcher instanceof RegExp) {
      const match = value.match(matcher);
      if (!match || match.index === void 0) return void 0;
      return keep === "after" ? value.slice(match.index + match[0].length) : value.slice(0, match.index);
    }
    return keep === "after" ? value.split(matcher)[1] : value.split(matcher)[0];
  }
  function stringToList(inputList, separator) {
    const defaultSeparator = /[|\n•·]/;
    const splitOn = parseRegexFromString(separator) || defaultSeparator;
    return cleanArray(inputList.split(splitOn));
  }
  function removeCommonSuffixesAndPrefixes(elementValue) {
    const regexes = [
      // match text such as +3 more when it appears at the end of a string
      /\+\s*\d+.*$/
    ];
    const startsWith = [
      "Associated persons:",
      "AKA:",
      "Known as:",
      "Also known as:",
      "Has lived in:",
      "Used to live:",
      "Used to live in:",
      "Lives in:",
      "Related to:",
      "No other aliases.",
      "RESIDES IN"
    ];
    const endsWith = [" -", "years old"];
    for (const regex of regexes) {
      elementValue = elementValue.replace(regex, "").trim();
    }
    for (const prefix of startsWith) {
      if (elementValue.toLowerCase().startsWith(prefix.toLowerCase())) {
        elementValue = elementValue.slice(prefix.length).trim();
      }
    }
    for (const suffix of endsWith) {
      if (elementValue.toLowerCase().endsWith(suffix.toLowerCase())) {
        elementValue = elementValue.slice(0, 0 - suffix.length).trim();
      }
    }
    return elementValue;
  }

  // src/features/broker-protection/actions/fill-form.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/actions/generators.js
  init_define_import_meta_trackerLookup();
  function generatePhoneNumber() {
    const areaCode = generateRandomInt(200, 899).toString();
    const exchangeCode = "555";
    const lineNumber = generateRandomInt(100, 199).toString().padStart(4, "0");
    return `${areaCode}${exchangeCode}${lineNumber}`;
  }
  function generateZipCode() {
    const zipCode = generateRandomInt(1e4, 99999).toString();
    return zipCode;
  }
  function generateStreetAddress() {
    const streetDigits = generateRandomInt(1, 5);
    const streetNumber = generateRandomInt(2, streetDigits * 1e3);
    const streetNames = [
      "Main",
      "Elm",
      "Maple",
      "Oak",
      "Pine",
      "Cedar",
      "Hill",
      "Lake",
      "Sunset",
      "Washington",
      "Lincoln",
      "Marshall",
      "Spring",
      "Ridge",
      "Valley",
      "Meadow",
      "Forest"
    ];
    const streetName = streetNames[generateRandomInt(0, streetNames.length - 1)];
    const suffixes = ["", "St", "Ave", "Blvd", "Rd", "Ct", "Dr", "Ln", "Pkwy", "Pl", "Ter", "Way"];
    const suffix = suffixes[generateRandomInt(0, suffixes.length - 1)];
    return `${streetNumber} ${streetName}${suffix ? " " + suffix : ""}`;
  }

  // src/features/broker-protection/actions/fill-form.js
  function fillForm(action, userData, root = document) {
    const form = getElement(root, action.selector);
    if (!form) return new ErrorResponse({ actionID: action.id, message: "missing form" });
    if (!userData) return new ErrorResponse({ actionID: action.id, message: "user data was absent" });
    form.scrollIntoView?.();
    const results = fillMany(form, action.elements, userData);
    const errors = results.filter((x2) => x2.result === false).map((x2) => {
      if ("error" in x2) return x2.error;
      return "unknown error";
    });
    if (errors.length > 0) {
      return new ErrorResponse({ actionID: action.id, message: errors.join(", ") });
    }
    return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: null });
  }
  function fillMany(root, elements, data2) {
    const results = [];
    for (const element of elements) {
      const inputElem = getElement(root, element.selector);
      if (!inputElem) {
        results.push({ result: false, error: `element not found for selector: "${element.selector}"` });
        continue;
      }
      if (element.type === "$file_id$") {
        results.push(setImageUpload(inputElem));
      } else if (element.type === "$generated_phone_number$") {
        results.push(setValueForInput(inputElem, generatePhoneNumber()));
      } else if (element.type === "$generated_zip_code$") {
        results.push(setValueForInput(inputElem, generateZipCode()));
      } else if (element.type === "$generated_random_number$") {
        if (!element.min || !element.max) {
          results.push({
            result: false,
            error: `element found with selector '${element.selector}', but missing min and/or max values`
          });
          continue;
        }
        const minInt = parseInt(element?.min);
        const maxInt = parseInt(element?.max);
        if (isNaN(minInt) || isNaN(maxInt)) {
          results.push({
            result: false,
            error: `element found with selector '${element.selector}', but min or max was not a number`
          });
          continue;
        }
        results.push(setValueForInput(inputElem, generateRandomInt(parseInt(element.min), parseInt(element.max)).toString()));
      } else if (element.type === "$generated_street_address$") {
        results.push(setValueForInput(inputElem, generateStreetAddress()));
      } else if (element.type === "cityState") {
        if (!Object.prototype.hasOwnProperty.call(data2, "city") || !Object.prototype.hasOwnProperty.call(data2, "state")) {
          results.push({
            result: false,
            error: `element found with selector '${element.selector}', but data didn't contain the keys 'city' and 'state'`
          });
          continue;
        }
        results.push(setValueForInput(inputElem, data2.city + ", " + data2.state));
      } else if (element.type === "fullState") {
        if (!Object.prototype.hasOwnProperty.call(data2, "state")) {
          results.push({
            result: false,
            error: `element found with selector '${element.selector}', but data didn't contain the key 'state'`
          });
          continue;
        }
        const state = data2.state;
        if (!Object.prototype.hasOwnProperty.call(states, state)) {
          results.push({
            result: false,
            error: `element found with selector '${element.selector}', but data contained an invalid 'state' abbreviation`
          });
          continue;
        }
        const stateFull = states[state];
        results.push(setValueForInput(inputElem, stateFull));
      } else {
        if (isElementTypeOptional(element.type)) {
          continue;
        }
        if (!Object.prototype.hasOwnProperty.call(data2, element.type)) {
          results.push({
            result: false,
            error: `element found with selector '${element.selector}', but data didn't contain the key '${element.type}'`
          });
          continue;
        }
        if (!data2[element.type]) {
          results.push({
            result: false,
            error: `data contained the key '${element.type}', but it wasn't something we can fill: ${data2[element.type]}`
          });
          continue;
        }
        results.push(setValueForInput(inputElem, data2[element.type]));
      }
    }
    return results;
  }
  function isElementTypeOptional(type) {
    if (type === "middleName") {
      return true;
    }
    return false;
  }
  function setValueForInput(el, val) {
    let target;
    if (el.tagName === "INPUT") target = window.HTMLInputElement;
    if (el.tagName === "SELECT") target = window.HTMLSelectElement;
    if (el.tagName === "TEXTAREA") target = window.HTMLTextAreaElement;
    if (!target) {
      return { result: false, error: `input type was not supported: ${el.tagName}` };
    }
    const originalSet = Object.getOwnPropertyDescriptor(target.prototype, "value")?.set;
    if (!originalSet || typeof originalSet.call !== "function") {
      return { result: false, error: "cannot access original value setter" };
    }
    try {
      if (el.tagName === "INPUT" || el.tagName === "TEXTAREA") {
        el.dispatchEvent(new Event("keydown", { bubbles: true }));
        originalSet.call(el, val);
        const events = [
          new Event("input", { bubbles: true }),
          new Event("keyup", { bubbles: true }),
          new Event("change", { bubbles: true })
        ];
        events.forEach((ev) => el.dispatchEvent(ev));
        originalSet.call(el, val);
        events.forEach((ev) => el.dispatchEvent(ev));
        el.blur();
      } else if (el.tagName === "SELECT") {
        const selectElement = (
          /** @type {HTMLSelectElement} */
          el
        );
        const selectValues = [...selectElement.options].map((o) => o.value);
        const valStr = String(val);
        const matchingValue = selectValues.find((option) => option.toLowerCase() === valStr.toLowerCase());
        if (matchingValue === void 0) {
          return { result: false, error: `could not find matching value for select element: ${val}` };
        }
        originalSet.call(el, matchingValue);
        const events = [
          new Event("mousedown", { bubbles: true }),
          new Event("mouseup", { bubbles: true }),
          new Event("click", { bubbles: true }),
          new Event("change", { bubbles: true })
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
  function setImageUpload(element) {
    const base64PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/B8AAusB9VF9PmUAAAAASUVORK5CYII=";
    try {
      const binaryString = window.atob(base64PNG);
      const length = binaryString.length;
      const bytes = new Uint8Array(length);
      for (let i = 0; i < length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      const blob = new Blob([bytes], { type: "image/png" });
      const dataTransfer = new DataTransfer();
      dataTransfer.items.add(new File([blob], "id.png", { type: "image/png" }));
      element.files = dataTransfer.files;
      return { result: true };
    } catch (e) {
      return { result: false, error: e.toString() };
    }
  }

  // src/features/broker-protection/actions/click.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/actions/build-url-transforms.js
  init_define_import_meta_trackerLookup();
  function transformUrl(action, userData) {
    const url = new URL(action.url);
    url.search = processSearchParams(url.searchParams, action, userData).toString();
    url.pathname = processPathname(url.pathname, action, userData);
    return { url: url.toString() };
  }
  var baseTransforms = /* @__PURE__ */ new Map([
    ["firstName", (value) => capitalize(value)],
    ["lastName", (value) => capitalize(value)],
    ["state", (value) => value.toLowerCase()],
    ["city", (value) => capitalize(value)],
    ["age", (value) => value.toString()]
  ]);
  var optionalTransforms = /* @__PURE__ */ new Map([
    ["hyphenated", (value) => value.split(" ").join("-")],
    ["capitalize", (value) => capitalize(value)],
    ["downcase", (value) => value.toLowerCase()],
    ["upcase", (value) => value.toUpperCase()],
    ["snakecase", (value) => value.split(" ").join("_")],
    ["stateFull", (value) => getStateFromAbbreviation(value)],
    ["defaultIfEmpty", (value, argument) => value || argument || ""],
    [
      "ageRange",
      (value, _2, action) => {
        if (!action.ageRange) return value;
        const ageNumber = Number(value);
        const ageRange = action.ageRange.find((range) => {
          const [min, max] = range.split("-");
          return ageNumber >= Number(min) && ageNumber <= Number(max);
        });
        return ageRange || value;
      }
    ],
    ["replaceWhitespace", (value, argument) => value.split(" ").join(argument ?? " ")]
  ]);
  function processSearchParams(searchParams, action, userData) {
    const updatedPairs = [...searchParams].map(([key, value]) => {
      const processedValue = processTemplateStringWithUserData(value, action, userData);
      return [key, processedValue];
    });
    return new URLSearchParams(updatedPairs);
  }
  function processPathname(pathname, action, userData) {
    return pathname.split("/").filter(Boolean).map((segment) => processTemplateStringWithUserData(segment, action, userData)).join("/");
  }
  function processTemplateStringWithUserData(input, action, userData) {
    return String(input).replace(/\$%7B(.+?)%7D|\$\{(.+?)}/g, (_2, encodedValue, plainValue) => {
      const comparison = encodedValue ?? plainValue;
      const [dataKey, ...transforms] = comparison.split(/\||%7C/);
      const data2 = userData[dataKey];
      return applyTransforms(dataKey, data2, transforms, action);
    });
  }
  function applyTransforms(dataKey, value, transformNames, action) {
    const subject = String(value || "");
    const baseTransform = baseTransforms.get(dataKey);
    let outputString = baseTransform ? baseTransform(subject) : subject;
    for (const transformName of transformNames) {
      const [name, argument] = transformName.split(":");
      const transform = optionalTransforms.get(name);
      if (transform) {
        outputString = transform(outputString, argument, action);
      }
    }
    return outputString;
  }
  function capitalize(s) {
    const words = s.split(" ");
    const capitalizedWords = words.map((word) => word.charAt(0).toUpperCase() + word.slice(1));
    return capitalizedWords.join(" ");
  }

  // src/features/broker-protection/actions/click.js
  function click(action, userData, root = document) {
    let elements = [];
    if (action.choices?.length) {
      const choices = evaluateChoices(action, userData);
      if (choices === null) {
        return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: null });
      } else if ("error" in choices) {
        return new ErrorResponse({ actionID: action.id, message: `Unable to evaluate choices: ${choices.error}` });
      } else if (!("elements" in choices)) {
        return new ErrorResponse({ actionID: action.id, message: "No elements provided to click action" });
      }
      elements = choices.elements;
    } else {
      if (!("elements" in action)) {
        return new ErrorResponse({ actionID: action.id, message: "No elements provided to click action" });
      }
      elements = action.elements;
    }
    if (!elements || !elements.length) {
      return new ErrorResponse({ actionID: action.id, message: "No elements provided to click action" });
    }
    for (const element of elements) {
      let rootElement;
      try {
        rootElement = selectRootElement(element, userData, root);
      } catch (error) {
        return new ErrorResponse({ actionID: action.id, message: `Could not find root element: ${error.message}` });
      }
      const elements2 = getElements(rootElement, element.selector);
      if (!elements2?.length) {
        if (element.failSilently) {
          return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: null });
        }
        return new ErrorResponse({
          actionID: action.id,
          message: `could not find element to click with selector '${element.selector}'!`
        });
      }
      const loopLength = element.multiple == true ? elements2.length : 1;
      for (let i = 0; i < loopLength; i++) {
        const elem = elements2[i];
        if ("disabled" in elem) {
          if (elem.disabled && !element.failSilently) {
            return new ErrorResponse({ actionID: action.id, message: `could not click disabled element ${element.selector}'!` });
          }
        }
        if ("click" in elem && typeof elem.click === "function") {
          elem.click();
        }
      }
    }
    return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: null });
  }
  function selectRootElement(clickElement, userData, root = document) {
    if (!clickElement.parent) return root;
    if (clickElement.parent.profileMatch) {
      const extraction = extractProfiles(clickElement.parent.profileMatch, userData, root);
      if ("results" in extraction) {
        const sorted = extraction.results.filter((x2) => x2.result === true).sort((a2, b2) => b2.score - a2.score);
        const first = sorted[0];
        if (first && first.element) {
          return first.element;
        }
      }
    }
    throw new Error("`parent` was present on the element, but the configuration is not supported");
  }
  function getComparisonFunction(operator) {
    switch (operator) {
      case "=":
      case "==":
      case "===":
        return (a2, b2) => a2 === b2;
      case "!=":
      case "!==":
        return (a2, b2) => a2 !== b2;
      case "<":
        return (a2, b2) => a2 < b2;
      case "<=":
        return (a2, b2) => a2 <= b2;
      case ">":
        return (a2, b2) => a2 > b2;
      case ">=":
        return (a2, b2) => a2 >= b2;
      default:
        throw new Error(`Invalid operator: ${operator}`);
    }
  }
  function evaluateChoices(action, userData) {
    if ("elements" in action) {
      return { error: "Elements should be nested inside of choices" };
    }
    for (const choice of action.choices) {
      if (!("condition" in choice) || !("elements" in choice)) {
        return { error: "All choices must have a condition and elements" };
      }
      const comparison = runComparison(choice, action, userData);
      if ("error" in comparison) {
        return { error: comparison.error };
      } else if ("result" in comparison && comparison.result === true) {
        return { elements: choice.elements };
      }
    }
    if (!("default" in action)) {
      return { error: "All conditions failed and no default action was provided" };
    }
    if (action.default === null) {
      return null;
    }
    if (!("elements" in action.default)) {
      return { error: "Default action must have elements" };
    }
    return { elements: action.default.elements };
  }
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

  // src/features/broker-protection/actions/expectation.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/utils/expectations.js
  init_define_import_meta_trackerLookup();
  function expectMany(expectations, root) {
    return expectations.map((expectation2) => {
      switch (expectation2.type) {
        case "element":
          return elementExpectation(expectation2, root);
        case "text":
          return textExpectation(expectation2, root);
        case "url":
          return urlExpectation(expectation2);
        default: {
          return {
            result: false,
            error: `unknown expectation type: ${expectation2.type}`
          };
        }
      }
    });
  }
  function elementExpectation(expectation2, root) {
    if (expectation2.parent) {
      const parent = getElement(root, expectation2.parent);
      if (!parent) {
        return {
          result: false,
          error: `parent element not found with selector: ${expectation2.parent}`
        };
      }
      parent.scrollIntoView();
    }
    const elementExists = getElement(root, expectation2.selector) !== null;
    if (!elementExists) {
      return {
        result: false,
        error: `element with selector ${expectation2.selector} not found.`
      };
    }
    return { result: true };
  }
  function textExpectation(expectation2, root) {
    const elem = getElement(root, expectation2.selector);
    if (!elem) {
      return {
        result: false,
        error: `element with selector ${expectation2.selector} not found.`
      };
    }
    if (!expectation2.expect) {
      return {
        result: false,
        error: "missing key: 'expect'"
      };
    }
    const textExists = Boolean(elem?.textContent?.includes(expectation2.expect));
    if (!textExists) {
      return {
        result: false,
        error: `expected element with selector ${expectation2.selector} to have text: ${expectation2.expect}, but it didn't`
      };
    }
    return { result: true };
  }
  function urlExpectation(expectation2) {
    const url = window.location.href;
    if (!expectation2.expect) {
      return {
        result: false,
        error: "missing key: 'expect'"
      };
    }
    if (!url.includes(expectation2.expect)) {
      return {
        result: false,
        error: `expected URL to include ${expectation2.expect}, but it didn't`
      };
    }
    return { result: true };
  }

  // src/features/broker-protection/actions/expectation.js
  function expectation(action, root = document) {
    const results = expectMany(action.expectations, root);
    const errors = results.filter((x2, index) => {
      if (x2.result === true) return false;
      if (action.expectations[index].failSilently) return false;
      return true;
    }).map((x2) => {
      return "error" in x2 ? x2.error : "unknown error";
    });
    if (errors.length > 0) {
      return new ErrorResponse({ actionID: action.id, message: errors.join(", ") });
    }
    const runActions = results.every((x2) => x2.result === true);
    if (action.actions?.length && runActions) {
      return new SuccessResponse({
        actionID: action.id,
        actionType: action.actionType,
        response: null,
        next: action.actions
      });
    }
    return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: null });
  }

  // src/features/broker-protection/actions/navigate.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/captcha-services/captcha.service.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/utils/url.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/utils/safe-call.js
  init_define_import_meta_trackerLookup();
  function safeCall(fn, { errorMessage } = {}) {
    try {
      return fn();
    } catch (e) {
      console.error(errorMessage ?? "[safeCall] Error:", e);
      return null;
    }
  }
  function safeCallWithError(fn, { errorMessage } = {}) {
    const message = errorMessage ?? "[safeCallWithError] Error";
    return safeCall(fn, { errorMessage: message }) ?? PirError.create(message);
  }

  // src/features/broker-protection/utils/url.js
  function getUrlParameter(url, param) {
    if (!url || !param) {
      return null;
    }
    return safeCall(() => new URL(url).searchParams.get(param), { errorMessage: `[getUrlParameter] Error parsing URL: ${url}` });
  }
  function removeUrlQueryParams(url) {
    if (!url) {
      return "";
    }
    return (
      /** @type {string} */
      url.split("?")[0]
    );
  }

  // src/features/broker-protection/captcha-services/get-captcha-provider.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/captcha-services/providers/registry.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/captcha-services/factory.js
  init_define_import_meta_trackerLookup();
  var CaptchaFactory = class {
    constructor() {
      this.providers = /* @__PURE__ */ new Map();
    }
    /**
     * Register a captcha provider
     * @param {import('./providers/provider.interface').CaptchaProvider} provider - The provider to register
     * @param {Object} [options] - The optional provider options
     * @param {Array<string>} [options.aliases] - An optional list of aliases for that provider
     */
    registerProvider(provider, options) {
      this.providers.set(provider.getType(), provider);
      if (options?.aliases) {
        options?.aliases.forEach((alias) => {
          this.providers.set(alias, provider);
        });
      }
    }
    /**
     * Get a provider by type
     * @param {string} type - The provider type
     * @returns {import('./providers/provider.interface').CaptchaProvider|null}
     */
    getProviderByType(type) {
      return this.providers.get(type) || null;
    }
    /**
     * Detect the captcha provider based on the element
     * @param {Document | HTMLElement} root
     * @param {HTMLElement} element - The element to check
     * @returns {import('./providers/provider.interface').CaptchaProvider|null}
     */
    detectProvider(root, element) {
      return this._getAllProviders().find((provider) => provider.isSupportedForElement(root, element)) || null;
    }
    /**
     * Detect the captcha provider based on the root document
     * @param {HTMLElement} element - The element to check
     * @returns {import('./providers/provider.interface').CaptchaProvider|null}
     */
    detectSolveProvider(element) {
      return this._getAllProviders().find((provider) => provider.canSolve(element)) || null;
    }
    /**
     * Get all registered providers
     * @private
     * @returns {Array<import('./providers/provider.interface').CaptchaProvider>}
     */
    _getAllProviders() {
      return Array.from(new Set(this.providers.values()));
    }
  };

  // src/features/broker-protection/captcha-services/providers/recaptcha.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/captcha-services/utils/sitekey.js
  init_define_import_meta_trackerLookup();
  function getSiteKeyFromSearchParam({ captchaElement, siteKeyAttrName }) {
    if (!captchaElement) {
      throw Error("[getSiteKeyFromSearchParam] could not find captcha");
    }
    if (!("src" in captchaElement)) {
      throw Error("[getSiteKeyFromSearchParam] missing src attribute");
    }
    return getUrlParameter(String(captchaElement.src), siteKeyAttrName);
  }

  // src/features/broker-protection/captcha-services/utils/stringify-function.js
  init_define_import_meta_trackerLookup();
  function stringifyFunction({ functionName, functionBody, args }) {
    return safeCall(
      () => `;(function(args) {
        ${functionBody.toString()};
        ${functionName}(args);
    })(${JSON.stringify(args)});`,
      { errorMessage: `[stringifyFunction] error stringifying function ${functionName}` }
    );
  }

  // src/features/broker-protection/captcha-services/utils/token.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/captcha-services/utils/element.js
  init_define_import_meta_trackerLookup();
  function isElementType(element, tag) {
    if (Array.isArray(tag)) {
      return tag.some((t) => isElementType(element, t));
    }
    return element.tagName.toLowerCase() === tag.toLowerCase();
  }

  // src/features/broker-protection/captcha-services/utils/token.js
  function injectTokenIntoElement({ captchaContainerElement, captchaInputElement, elementName, token }) {
    let element;
    if (captchaInputElement) {
      element = captchaInputElement;
    } else if (elementName) {
      element = getElementByTagName(captchaContainerElement, elementName);
    } else {
      return PirError.create(`[injectTokenIntoElement] must pass in either captcha input element or element name`);
    }
    if (!element) {
      return PirError.create(`[injectTokenIntoElement] could not find element to inject token into`);
    }
    return safeCallWithError(
      () => {
        if (isInputElement(element) && ["text", "hidden"].includes(element.type) || isTextAreaElement(element)) {
          element.value = token;
          return PirSuccess.create({ injected: true });
        } else {
          return PirError.create(`[injectTokenIntoElement] element is neither a text input or textarea`);
        }
      },
      { errorMessage: `[injectTokenIntoElement] error injecting token into element` }
    );
  }
  function isInputElement(element) {
    return isElementType(element, "input");
  }
  function isTextAreaElement(element) {
    return isElementType(element, "textarea");
  }

  // src/features/broker-protection/actions/captcha-callback.js
  init_define_import_meta_trackerLookup();
  function captchaCallback(args) {
    const clients = findRecaptchaClients(globalThis);
    if (clients.length === 0) {
      return console.log("cannot find clients");
    }
    if (typeof clients[0].function === "function") {
      try {
        clients[0].function(args.token);
        console.log("called function with path", clients[0].callback);
      } catch (e) {
        console.error("could not call function");
      }
    }
    function findRecaptchaClients(target) {
      if (typeof target.___grecaptcha_cfg === "undefined") {
        console.log("target.___grecaptcha_cfg not found in ", location.href);
        return [];
      }
      return Object.entries(target.___grecaptcha_cfg.clients || {}).map(([cid, client]) => {
        const cidNumber = parseInt(cid, 10);
        const data2 = {
          id: cid,
          version: cidNumber >= 1e4 ? "V3" : "V2"
        };
        const objects = Object.entries(client).filter(([, value]) => value && typeof value === "object");
        objects.forEach(([toplevelKey, toplevel]) => {
          const found = Object.entries(toplevel).find(
            ([, value]) => value && typeof value === "object" && "sitekey" in value && "size" in value
          );
          if (typeof toplevel === "object" && typeof HTMLElement !== "undefined" && toplevel instanceof HTMLElement && toplevel.tagName === "DIV") {
            data2.pageurl = toplevel.baseURI;
          }
          if (found) {
            const [sublevelKey, sublevel] = found;
            data2.sitekey = sublevel.sitekey;
            const callbackKey = data2.version === "V2" ? "callback" : "promise-callback";
            const callback = sublevel[callbackKey];
            if (!callback) {
              data2.callback = null;
              data2.function = null;
            } else {
              data2.function = callback;
              data2.callback = ["___grecaptcha_cfg", "clients", cid, toplevelKey, sublevelKey, callbackKey];
            }
          }
        });
        return data2;
      });
    }
  }

  // src/features/broker-protection/captcha-services/providers/recaptcha.js
  var _config;
  var ReCaptchaProvider = class {
    /**
     * @param {ReCaptchaProviderConfig} config
     */
    constructor(config) {
      /**
       * @type {ReCaptchaProviderConfig}
       */
      __privateAdd(this, _config);
      __privateSet(this, _config, config);
    }
    getType() {
      return __privateGet(this, _config).type;
    }
    /**
     * @param {Document | HTMLElement} _root
     * @param {HTMLElement} captchaContainerElement
     */
    isSupportedForElement(_root, captchaContainerElement) {
      return !!this._getCaptchaElement(captchaContainerElement);
    }
    /**
     * @param {HTMLElement} captchaContainerElement
     */
    getCaptchaIdentifier(captchaContainerElement) {
      return Promise.resolve(
        safeCallWithError(
          () => getSiteKeyFromSearchParam({ captchaElement: this._getCaptchaElement(captchaContainerElement), siteKeyAttrName: "k" }),
          { errorMessage: "[ReCaptchaProvider.getCaptchaIdentifier] could not extract site key" }
        )
      );
    }
    getSupportingCodeToInject() {
      return null;
    }
    /**
     * @param {HTMLElement} _captchaContainerElement - The element containing the captcha
     * @param {string} token
     */
    getSolveCallback(_captchaContainerElement, token) {
      return stringifyFunction({
        functionBody: captchaCallback,
        functionName: "captchaCallback",
        args: { token }
      });
    }
    /**
     * @param {HTMLElement} captchaContainerElement - The element containing the captcha
     */
    canSolve(captchaContainerElement) {
      return !!getElementByTagName(captchaContainerElement, __privateGet(this, _config).responseElementName);
    }
    /**
     * @param {HTMLElement} captchaContainerElement - The element containing the captcha
     * @param {string} token
     */
    injectToken(captchaContainerElement, token) {
      return injectTokenIntoElement({ captchaContainerElement, elementName: __privateGet(this, _config).responseElementName, token });
    }
    /**
     * @private
     * @param {HTMLElement} captchaContainerElement
     */
    _getCaptchaElement(captchaContainerElement) {
      return getElementWithSrcStart(captchaContainerElement, __privateGet(this, _config).providerUrl);
    }
  };
  _config = new WeakMap();

  // src/features/broker-protection/captcha-services/providers/image.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/captcha-services/utils/image.js
  init_define_import_meta_trackerLookup();
  function svgToBase64Jpg(svgElement, backgroundColor = "white") {
    const svgString = new XMLSerializer().serializeToString(svgElement);
    const svgDataUrl = "data:image/svg+xml;base64," + btoa(svgString);
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement("canvas");
        const ctx = canvas.getContext("2d");
        if (!ctx) {
          reject(new Error("Could not get 2D context from canvas"));
          return;
        }
        canvas.width = img.width;
        canvas.height = img.height;
        ctx.fillStyle = backgroundColor;
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(img, 0, 0);
        const jpgBase64 = canvas.toDataURL("image/jpeg");
        resolve(jpgBase64);
      };
      img.onerror = (error) => {
        reject(error);
      };
      img.src = svgDataUrl;
    });
  }
  var MAX_CAPTCHA_IMAGE_BYTES = 5 * 1024 * 1024;
  async function imageToBase64(imageElement) {
    const src = imageElement.currentSrc || imageElement.src;
    if (src.startsWith("data:") && src.includes(";base64,")) {
      const base64Data = src.slice(src.indexOf(",") + 1);
      const padding = base64Data.endsWith("==") ? 2 : base64Data.endsWith("=") ? 1 : 0;
      const decodedBytes = Math.floor(base64Data.length * 3 / 4) - padding;
      if (decodedBytes > MAX_CAPTCHA_IMAGE_BYTES) {
        throw new Error(`[imageToBase64] captcha image exceeds ${MAX_CAPTCHA_IMAGE_BYTES} bytes: ${decodedBytes}`);
      }
      return src;
    }
    const response = await fetch(src, { credentials: "omit" });
    if (!response.ok) {
      throw new Error(`[imageToBase64] failed to fetch image from ${src}: ${response.status} ${response.statusText}`);
    }
    const contentLength = Number(response.headers.get("content-length"));
    if (contentLength > MAX_CAPTCHA_IMAGE_BYTES) {
      throw new Error(`[imageToBase64] captcha image exceeds ${MAX_CAPTCHA_IMAGE_BYTES} bytes: ${contentLength}`);
    }
    const blob = await response.blob();
    if (blob.size > MAX_CAPTCHA_IMAGE_BYTES) {
      throw new Error(`[imageToBase64] captcha image exceeds ${MAX_CAPTCHA_IMAGE_BYTES} bytes: ${blob.size}`);
    }
    return await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onloadend = () => resolve(
        /** @type {string} */
        reader.result
      );
      reader.onerror = () => reject(reader.error);
      reader.readAsDataURL(blob);
    });
  }

  // src/features/broker-protection/captcha-services/providers/image.js
  var ImageProvider = class {
    getType() {
      return "image";
    }
    /**
     * @param {Document | HTMLElement} _root
     * @param {HTMLElement} captchaImageElement - The captcha image element
     */
    isSupportedForElement(_root, captchaImageElement) {
      if (!captchaImageElement) {
        return false;
      }
      return isElementType(captchaImageElement, ["img", "svg"]);
    }
    /**
     * @param {HTMLElement} captchaImageElement - The captcha image element
     */
    async getCaptchaIdentifier(captchaImageElement) {
      if (isSVGElement(captchaImageElement)) {
        return await svgToBase64Jpg(captchaImageElement);
      }
      if (isImgElement(captchaImageElement)) {
        return await imageToBase64(captchaImageElement);
      }
      return PirError.create(
        `[ImageProvider.getCaptchaIdentifier] could not extract Base64 from image with tag name: ${captchaImageElement.tagName}`
      );
    }
    getSupportingCodeToInject() {
      return null;
    }
    /**
     * @param {HTMLElement} captchaInputElement - The captcha input element
     */
    canSolve(captchaInputElement) {
      return isElementType(captchaInputElement, ["input", "textarea"]);
    }
    /**
     * @param {HTMLInputElement} captchaInputElement - The captcha input element
     * @param {string} token - The solved captcha token
     */
    injectToken(captchaInputElement, token) {
      return injectTokenIntoElement({ captchaInputElement, token });
    }
    /**
     * @param {HTMLElement} _captchaInputElement - The element containing the captcha
     * @param {string} _token - The solved captcha token
     */
    getSolveCallback(_captchaInputElement, _token) {
      return stringifyFunction({
        functionBody: function callbackNoop() {
        },
        functionName: "callbackNoop",
        args: {}
      });
    }
  };
  function isSVGElement(element) {
    return isElementType(element, "svg");
  }
  function isImgElement(element) {
    return isElementType(element, "img");
  }

  // src/features/broker-protection/captcha-services/providers/cloudflare-turnstile.js
  init_define_import_meta_trackerLookup();

  // src/features/broker-protection/captcha-services/utils/attribute.js
  init_define_import_meta_trackerLookup();
  function getAttributeValue({ element, attrName }) {
    if (!element) {
      throw Error("[getAttributeValue] element parameter is required");
    }
    const attributeValue = element.getAttribute(attrName);
    if (!attributeValue) {
      throw Error(`[getAttributeValue] ${attrName} is not defined or has no value`);
    }
    return attributeValue;
  }

  // src/features/broker-protection/captcha-services/providers/cloudflare-turnstile.js
  var _config2;
  var CloudFlareTurnstileProvider = class {
    constructor() {
      /**
       * @type {CloudFlareTurnstileProviderConfig}
       */
      __privateAdd(this, _config2);
      __privateSet(this, _config2, {
        providerUrl: "https://challenges.cloudflare.com/turnstile/v0",
        responseElementName: "cf-turnstile-response"
      });
    }
    getType() {
      return "cloudFlareTurnstile";
    }
    /**
     * @param {Document | HTMLElement} root
     * @param {HTMLElement} _captchaContainerElement
     * @returns {boolean} Whether the captcha is supported for the element
     */
    isSupportedForElement(root, _captchaContainerElement) {
      return !!this._getCaptchaScript(root);
    }
    /**
     * @param {HTMLElement} captchaContainerElement - The element containing the captcha
     */
    getCaptchaIdentifier(captchaContainerElement) {
      const sitekeyAttribute = "data-sitekey";
      return Promise.resolve(
        safeCallWithError(() => getAttributeValue({ element: captchaContainerElement, attrName: sitekeyAttribute }), {
          errorMessage: `[CloudFlareTurnstileProvider.getCaptchaIdentifier] could not extract site key from attribute: ${sitekeyAttribute}`
        })
      );
    }
    getSupportingCodeToInject() {
      return null;
    }
    /**
     * @param {HTMLElement} captchaContainerElement - The element containing the captcha
     * @returns {boolean} Whether the captcha can be solved
     */
    canSolve(captchaContainerElement) {
      const callbackAttribute = "data-callback";
      const hasCallback = safeCallWithError(() => getAttributeValue({ element: captchaContainerElement, attrName: callbackAttribute }), {
        errorMessage: `[CloudFlareTurnstileProvider.canSolve] could not extract callback function name from attribute: ${callbackAttribute}`
      });
      if (PirError.isError(hasCallback)) {
        return false;
      }
      const hasResponseElement = safeCallWithError(() => getElementByTagName(captchaContainerElement, __privateGet(this, _config2).responseElementName), {
        errorMessage: `[CloudFlareTurnstileProvider.canSolve] could not find response element: ${__privateGet(this, _config2).responseElementName}`
      });
      if (PirError.isError(hasResponseElement)) {
        return false;
      }
      return true;
    }
    /**
     * @param {HTMLElement} captchaContainerElement - The element containing the captcha
     * @param {string} token - The solved captcha token
     */
    injectToken(captchaContainerElement, token) {
      return injectTokenIntoElement({ captchaContainerElement, elementName: __privateGet(this, _config2).responseElementName, token });
    }
    /**
     * @param {HTMLElement} captchaContainerElement - The element containing the captcha
     * @param {string} token - The solved captcha token
     */
    getSolveCallback(captchaContainerElement, token) {
      const callbackAttribute = "data-callback";
      const callbackFunctionName = safeCallWithError(
        () => getAttributeValue({ element: captchaContainerElement, attrName: callbackAttribute }),
        {
          errorMessage: `[CloudFlareTurnstileProvider.getSolveCallback] could not extract callback function name from attribute: ${callbackAttribute}`
        }
      );
      if (PirError.isError(callbackFunctionName)) {
        return callbackFunctionName;
      }
      return stringifyFunction({
        /**
         * @param {Object} args - The arguments passed to the function
         * @param {string} args.callbackFunctionName - The callback function name
         * @param {string} args.token - The solved captcha token
         */
        functionBody: function cloudflareCaptchaCallback(args) {
          window[args.callbackFunctionName](args.token);
        },
        functionName: "cloudflareCaptchaCallback",
        args: { callbackFunctionName, token }
      });
    }
    /**
     * @private
     * @param {Document | HTMLElement} root - The root element to search in
     */
    _getCaptchaScript(root) {
      return getElementWithSrcStart(root, __privateGet(this, _config2).providerUrl);
    }
  };
  _config2 = new WeakMap();

  // src/features/broker-protection/captcha-services/providers/registry.js
  var captchaFactory = new CaptchaFactory();
  captchaFactory.registerProvider(
    new ReCaptchaProvider({
      type: "recaptcha2",
      providerUrl: "https://www.google.com/recaptcha/api2",
      responseElementName: "g-recaptcha-response"
    })
  );
  captchaFactory.registerProvider(
    new ReCaptchaProvider({
      type: "recaptchaEnterprise",
      providerUrl: "https://www.google.com/recaptcha/enterprise",
      responseElementName: "g-recaptcha-response"
    })
  );
  captchaFactory.registerProvider(new CloudFlareTurnstileProvider());
  captchaFactory.registerProvider(new ImageProvider(), { aliases: ["red-circle", "basic-math"] });

  // src/features/broker-protection/captcha-services/get-captcha-provider.js
  function getCaptchaProvider(root, captchaContainer, captchaType) {
    const captchaProvider = captchaFactory.getProviderByType(captchaType);
    if (!captchaProvider) {
      return PirError.create(`[getCaptchaProvider] could not find captcha provider with type ${captchaType}`);
    }
    if (captchaProvider.isSupportedForElement(root, captchaContainer)) {
      return captchaProvider;
    }
    const detectedProvider = captchaFactory.detectProvider(root, captchaContainer);
    if (!detectedProvider) {
      return PirError.create(
        `[getCaptchaProvider] could not detect captcha provider for ${captchaType} captcha and element ${captchaContainer}`
      );
    }
    console.warn(
      `[getCaptchaProvider] mismatch between expected capctha type ${captchaType} and detected type ${detectedProvider.getType()}`
    );
    return detectedProvider;
  }
  function getCaptchaSolveProvider(captchaContainer, captchaType) {
    const captchaProvider = captchaFactory.getProviderByType(captchaType);
    if (!captchaProvider) {
      return PirError.create(`[getCaptchaSolveProvider] could not find captcha provider with type ${captchaType}`);
    }
    if (captchaProvider.canSolve(captchaContainer)) {
      return captchaProvider;
    }
    const detectedProvider = captchaFactory.detectSolveProvider(captchaContainer);
    if (!detectedProvider) {
      return PirError.create(
        `[getCaptchaSolveProvider] could not detect captcha provider for ${captchaType} captcha and element ${captchaContainer}`
      );
    }
    console.warn(
      `[getCaptchaSolveProvider] mismatch between expected captha type ${captchaType} and detected type ${detectedProvider.getType()}`
    );
    return detectedProvider;
  }

  // src/features/broker-protection/actions/captcha-deprecated.js
  init_define_import_meta_trackerLookup();
  function getCaptchaInfo(action, root = document) {
    const pageUrl = window.location.href;
    if (!action.selector) {
      return new ErrorResponse({ actionID: action.id, message: "missing selector" });
    }
    const captchaDiv = getElement(root, action.selector);
    if (!captchaDiv) {
      return new ErrorResponse({ actionID: action.id, message: `could not find captchaDiv with selector ${action.selector}` });
    }
    const captcha = getElement(captchaDiv, '[src^="https://www.google.com/recaptcha"]') || getElement(captchaDiv, '[src^="https://newassets.hcaptcha.com/captcha"');
    if (!captcha) return new ErrorResponse({ actionID: action.id, message: "could not find captcha" });
    if (!("src" in captcha)) return new ErrorResponse({ actionID: action.id, message: "missing src attribute" });
    const captchaUrl = String(captcha.src);
    let captchaType;
    let siteKey;
    if (captchaUrl.includes("recaptcha/api2")) {
      captchaType = "recaptcha2";
      siteKey = new URL(captchaUrl).searchParams.get("k");
    } else if (captchaUrl.includes("recaptcha/enterprise")) {
      captchaType = "recaptchaEnterprise";
      siteKey = new URL(captchaUrl).searchParams.get("k");
    } else if (captchaUrl.includes("hcaptcha.com/captcha/v1")) {
      captchaType = "hcaptcha";
      if (captcha instanceof Element) {
        siteKey = captcha.getAttribute("data-sitekey");
      }
      if (!siteKey) {
        try {
          siteKey = new URL(captchaUrl).searchParams.get("sitekey");
        } catch (e) {
          console.warn("error parsing captchaUrl", captchaUrl);
        }
      }
      if (!siteKey) {
        try {
          const hash = new URL(captchaUrl).hash.slice(1);
          siteKey = new URLSearchParams(hash).get("sitekey");
        } catch (e) {
          console.warn("error parsing captchaUrl hash", captchaUrl);
        }
      }
    }
    if (!captchaType) {
      return new ErrorResponse({ actionID: action.id, message: "Could not extract captchaType." });
    }
    if (!siteKey) {
      return new ErrorResponse({ actionID: action.id, message: "Could not extract siteKey." });
    }
    const pageUrlWithoutParams = pageUrl?.split("?")[0];
    const responseData = {
      siteKey,
      url: pageUrlWithoutParams,
      type: captchaType
    };
    return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: responseData });
  }
  function solveCaptcha(action, token, root = document) {
    const selectors = ["h-captcha-response", "g-recaptcha-response"];
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
        response: { callback: { eval: javascript } }
      });
    }
    return new ErrorResponse({ actionID: action.id, message: "could not solve captcha" });
  }

  // src/features/broker-protection/captcha-services/captcha.service.js
  var getCaptchaContainer = (root, selector) => {
    if (!selector) {
      return PirError.create("missing selector");
    }
    const captchaContainer = getElement(root, selector);
    if (!captchaContainer) {
      return PirError.create(`could not find captcha container with selector ${selector}`);
    }
    return captchaContainer;
  };
  function getSupportingCodeToInject(action) {
    const { id: actionID, actionType, injectCaptchaHandler: captchaType } = action;
    const createError = ErrorResponse.generateErrorResponseFunction({ actionID, context: "getSupportingCodeToInject" });
    if (!captchaType) {
      return SuccessResponse.create({ actionID, actionType, response: {} });
    }
    const captchaProvider = captchaFactory.getProviderByType(captchaType);
    if (!captchaProvider) {
      return createError(`could not find captchaProvider with type ${captchaType}`);
    }
    return SuccessResponse.create({ actionID, actionType, response: { code: captchaProvider.getSupportingCodeToInject() } });
  }
  async function getCaptchaInfo2(action, root = document) {
    const { id: actionID, actionType, captchaType, selector } = action;
    if (!captchaType) {
      return getCaptchaInfo(action, root);
    }
    const createError = ErrorResponse.generateErrorResponseFunction({ actionID, context: `[getCaptchaInfo] captchaType: ${captchaType}` });
    const captchaContainer = getCaptchaContainer(root, selector);
    if (PirError.isError(captchaContainer)) {
      return createError(captchaContainer.error.message);
    }
    const captchaProvider = getCaptchaProvider(root, captchaContainer, captchaType);
    if (PirError.isError(captchaProvider)) {
      return createError(captchaProvider.error.message);
    }
    const captchaIdentifier = await captchaProvider.getCaptchaIdentifier(captchaContainer);
    if (!captchaIdentifier) {
      return createError(`could not extract captcha identifier from the container with selector ${selector}`);
    }
    if (PirError.isError(captchaIdentifier)) {
      return createError(captchaIdentifier.error.message);
    }
    const reportedType = captchaFactory.getProviderByType(captchaType) === captchaProvider ? captchaType : captchaProvider.getType();
    const response = {
      url: removeUrlQueryParams(window.location.href),
      // query params (which may include PII)
      siteKey: captchaIdentifier,
      type: reportedType
    };
    return SuccessResponse.create({ actionID, actionType, response });
  }
  function solveCaptcha2(action, token, root = document) {
    const { id: actionID, actionType, captchaType, selector } = action;
    if (!captchaType) {
      return solveCaptcha(action, token, root);
    }
    const createError = ErrorResponse.generateErrorResponseFunction({ actionID, context: `[solveCaptcha] captchaType: ${captchaType}` });
    const captchaContainer = getCaptchaContainer(root, selector);
    if (PirError.isError(captchaContainer)) {
      return createError(captchaContainer.error.message);
    }
    const captchaSolveProvider = getCaptchaSolveProvider(captchaContainer, captchaType);
    if (PirError.isError(captchaSolveProvider)) {
      return createError(captchaSolveProvider.error.message);
    }
    if (!captchaSolveProvider.canSolve(captchaContainer)) {
      return createError("cannot solve captcha");
    }
    const tokenResponse = captchaSolveProvider.injectToken(captchaContainer, token);
    if (PirError.isError(tokenResponse)) {
      return createError(tokenResponse.error.message);
    }
    if (!tokenResponse.response.injected) {
      return createError("could not inject token");
    }
    return SuccessResponse.create({
      actionID,
      actionType,
      response: { callback: { eval: captchaSolveProvider.getSolveCallback(captchaContainer, token) } }
    });
  }

  // src/features/broker-protection/actions/build-url.js
  init_define_import_meta_trackerLookup();
  function buildUrl(action, userData) {
    const result = replaceTemplatedUrl(action, userData);
    if ("error" in result) {
      return new ErrorResponse({ actionID: action.id, message: result.error });
    }
    return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: { url: result.url } });
  }
  function replaceTemplatedUrl(action, userData) {
    const url = action?.url;
    if (!url) {
      return { error: "Error: No url provided." };
    }
    try {
      const _2 = new URL(action.url);
    } catch (e) {
      return { error: "Error: Invalid URL provided." };
    }
    if (!userData) {
      return { url };
    }
    return transformUrl(action, userData);
  }

  // src/features/broker-protection/actions/navigate.js
  function navigate(action, userData) {
    const { id: actionID, actionType } = action;
    const urlResult = buildUrl(action, userData);
    if (urlResult instanceof ErrorResponse) {
      return urlResult;
    }
    const codeToInjectResponse = getSupportingCodeToInject(action);
    if (codeToInjectResponse instanceof ErrorResponse) {
      return codeToInjectResponse;
    }
    const response = {
      ...urlResult.success.response,
      ...codeToInjectResponse.success.response
    };
    return new SuccessResponse({ actionID, actionType, response });
  }

  // src/features/broker-protection/actions/condition.js
  init_define_import_meta_trackerLookup();
  function condition(action, root = document) {
    const results = expectMany(action.expectations, root);
    const errors = results.filter((x2, index) => {
      if (x2.result === true) return false;
      if (action.expectations[index].failSilently) return false;
      return true;
    }).map((x2) => {
      return "error" in x2 ? x2.error : "unknown error";
    });
    if (errors.length > 0) {
      return new ErrorResponse({ actionID: action.id, message: errors.join(", ") });
    }
    const returnActions = results.every((x2) => x2.result === true);
    if (action.actions?.length && returnActions) {
      return new SuccessResponse({
        actionID: action.id,
        actionType: action.actionType,
        response: { actions: action.actions }
      });
    }
    return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: { actions: [] } });
  }

  // src/features/broker-protection/actions/scroll.js
  init_define_import_meta_trackerLookup();
  function scroll(action, root = document) {
    const element = getElement(root, action.selector);
    if (!element) return new ErrorResponse({ actionID: action.id, message: "missing element" });
    element.scrollIntoView({ behavior: "smooth", block: "center", inline: "center" });
    return new SuccessResponse({ actionID: action.id, actionType: action.actionType, response: null });
  }

  // src/features/broker-protection/execute.js
  async function execute(action, inputData, root = document) {
    try {
      switch (action.actionType) {
        case "navigate":
          return navigate(action, data(action, inputData, "userProfile"));
        case "extract":
          return await extract(action, data(action, inputData, "userProfile"), root);
        case "click":
          return click(action, data(action, inputData, "userProfile"), root);
        case "expectation":
          return expectation(action, root);
        case "fillForm":
          return fillForm(action, data(action, inputData, "extractedProfile"), root);
        case "getCaptchaInfo":
          return await getCaptchaInfo2(action, root);
        case "solveCaptcha":
          return solveCaptcha2(action, data(action, inputData, "token"), root);
        case "condition":
          return condition(action, root);
        case "scroll":
          return scroll(action, root);
        default: {
          return new ErrorResponse({
            actionID: action.id,
            message: `unimplemented actionType: ${action.actionType}`
          });
        }
      }
    } catch (e) {
      console.log("unhandled exception: ", e);
      return new ErrorResponse({
        actionID: action.id,
        message: `unhandled exception: ${e.message}`
      });
    }
  }
  function data(action, data2, defaultSource) {
    if (!data2) return null;
    const source = action.dataSource || defaultSource;
    if (Object.prototype.hasOwnProperty.call(data2, source)) {
      return data2[source];
    }
    return null;
  }

  // src/timer-utils.js
  init_define_import_meta_trackerLookup();
  var DEFAULT_RETRY_CONFIG = {
    interval: { ms: 0 },
    maxAttempts: 1
  };
  async function retry(fn, config = DEFAULT_RETRY_CONFIG) {
    let lastResult;
    const exceptions = [];
    for (let i = 0; i < config.maxAttempts; i++) {
      try {
        lastResult = await Promise.resolve(fn());
      } catch (e) {
        exceptions.push(String(e));
      }
      if (lastResult && "success" in lastResult) break;
      if (i === config.maxAttempts - 1) break;
      await new Promise((resolve) => setTimeout(resolve, config.interval.ms));
    }
    return { result: lastResult, exceptions };
  }

  // src/features/broker-protection.js
  var ActionExecutorBase = class extends ContentFeature {
    /**
     * @param {any} action
     * @param {Record<string, any>} data
     */
    async processActionAndNotify(action, data2) {
      try {
        if (!action) {
          return this.messaging.notify("actionError", { error: "No action found." });
        }
        const { results, exceptions } = await this.exec(action, data2);
        if (results) {
          const parent = results[0];
          const errors = results.filter((x2) => "error" in x2);
          if (results.length === 1 || errors.length === 0) {
            return this.messaging.notify("actionCompleted", { result: parent });
          }
          const joinedErrors = errors.map((x2) => x2.error.message).join(", ");
          const response = new ErrorResponse({
            actionID: action.id,
            message: "Secondary actions failed: " + joinedErrors
          });
          return this.messaging.notify("actionCompleted", { result: response });
        } else {
          return this.messaging.notify("actionError", { error: "No response found, exceptions: " + exceptions.join(", ") });
        }
      } catch (e) {
        this.log.error("unhandled exception: ", e);
        return this.messaging.notify("actionError", { error: e.toString() });
      }
    }
    /**
     * Recursively execute actions with the same dataset, collecting all results/exceptions for
     * later analysis
     * @param {any} action
     * @param {Record<string, any>} data
     * @return {Promise<{results: ActionResponse[], exceptions: string[]}>}
     */
    async exec(action, data2) {
      const retryConfig = this.retryConfigFor(action);
      const { result, exceptions } = await retry(() => execute(action, data2, document), retryConfig);
      if (result) {
        if ("success" in result && Array.isArray(result.success.next)) {
          const nextResults = [];
          const nextExceptions = [];
          for (const nextAction of result.success.next) {
            const { results: subResults, exceptions: subExceptions } = await this.exec(nextAction, data2);
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
     * @returns {any}
     */
    retryConfigFor(action) {
      this.log.error("unimplemented method: retryConfigFor:", action);
    }
  };
  var BrokerProtection = class extends ActionExecutorBase {
    init() {
      this.messaging.subscribe("onActionReceived", async (params) => {
        const { action, data: data2 } = params.state;
        return await this.processActionAndNotify(action, data2);
      });
    }
    /**
     * Define default retry configurations for certain actions
     *
     * @param {any} action
     * @returns
     */
    retryConfigFor(action) {
      const retryConfig = action.retry?.environment === "web" ? action.retry : void 0;
      if (!retryConfig && action.actionType === "extract") {
        return {
          interval: { ms: 1e3 },
          maxAttempts: 30
        };
      }
      if (!retryConfig && (action.actionType === "expectation" || action.actionType === "condition")) {
        if (action.expectations.some((x2) => x2.type === "element")) {
          return {
            interval: { ms: 1e3 },
            maxAttempts: 30
          };
        }
      }
      return retryConfig;
    }
  };

  // ddg:platformFeatures:ddg:platformFeatures
  var ddg_platformFeatures_default = {
    ddg_feature_brokerProtection: BrokerProtection
  };

  // src/url-change.js
  init_define_import_meta_trackerLookup();
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
      injectName: "android-broker-protection"
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

  // entry-points/android.js
  function initCode() {
    const config = $CONTENT_SCOPE$;
    const userUnprotectedDomains = $USER_UNPROTECTED_DOMAINS$;
    const userPreferences = $USER_PREFERENCES$;
    const processedConfig = processConfig(config, userUnprotectedDomains, userPreferences);
    const configConstruct = processedConfig;
    const messageCallback = configConstruct.messageCallback;
    const messageSecret2 = configConstruct.messageSecret;
    const javascriptInterface = configConstruct.javascriptInterface;
    processedConfig.messagingConfig = new AndroidMessagingConfig({
      messageSecret: messageSecret2,
      messageCallback,
      javascriptInterface,
      target: globalThis,
      debug: processedConfig.debug
    });
    load(getLoadArgs(processedConfig));
    init(processedConfig);
  }
  initCode();
})();
