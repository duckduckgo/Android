(function () {
  'use strict';

  // lib/consentomatic/tools.ts
  var _Tools = class _Tools {
    static setBase(base2) {
      _Tools.base = base2;
    }
    static findElement(options, parent = null, multiple = false) {
      let possibleTargets = null;
      if (parent != null) {
        possibleTargets = Array.from(parent.querySelectorAll(options.selector));
      } else {
        if (_Tools.base != null) {
          possibleTargets = Array.from(
            _Tools.base.querySelectorAll(options.selector)
          );
        } else {
          possibleTargets = Array.from(
            document.querySelectorAll(options.selector)
          );
        }
      }
      if (options.textFilter != null) {
        possibleTargets = possibleTargets.filter((possibleTarget) => {
          const textContent = possibleTarget.textContent.toLowerCase();
          if (Array.isArray(options.textFilter)) {
            let foundText = false;
            for (const text of options.textFilter) {
              if (textContent.indexOf(text.toLowerCase()) !== -1) {
                foundText = true;
                break;
              }
            }
            return foundText;
          } else if (options.textFilter != null) {
            return textContent.indexOf(options.textFilter.toLowerCase()) !== -1;
          }
        });
      }
      if (options.styleFilters != null) {
        possibleTargets = possibleTargets.filter((possibleTarget) => {
          const styles = window.getComputedStyle(possibleTarget);
          let keep = true;
          for (const styleFilter of options.styleFilters) {
            const option = styles[styleFilter.option];
            if (styleFilter.negated) {
              keep = keep && option !== styleFilter.value;
            } else {
              keep = keep && option === styleFilter.value;
            }
          }
          return keep;
        });
      }
      if (options.displayFilter != null) {
        possibleTargets = possibleTargets.filter((possibleTarget) => {
          if (options.displayFilter) {
            return possibleTarget.offsetHeight !== 0;
          } else {
            return possibleTarget.offsetHeight === 0;
          }
        });
      }
      if (options.iframeFilter != null) {
        possibleTargets = possibleTargets.filter(() => {
          if (options.iframeFilter) {
            return window.location !== window.parent.location;
          } else {
            return window.location === window.parent.location;
          }
        });
      }
      if (options.childFilter != null) {
        possibleTargets = possibleTargets.filter((possibleTarget) => {
          const oldBase = _Tools.base;
          _Tools.setBase(possibleTarget);
          const childResults = _Tools.find(options.childFilter);
          _Tools.setBase(oldBase);
          return childResults.target != null;
        });
      }
      if (multiple) {
        return possibleTargets;
      } else {
        if (possibleTargets.length > 1) {
          console.warn(
            "Multiple possible targets: ",
            possibleTargets,
            options,
            parent
          );
        }
        return possibleTargets[0];
      }
    }
    static find(options, multiple = false) {
      const results = [];
      if (options.parent != null) {
        const parent = _Tools.findElement(options.parent, null, multiple);
        if (parent != null) {
          if (parent instanceof Array) {
            parent.forEach((p) => {
              const targets = _Tools.findElement(options.target, p, multiple);
              if (targets instanceof Array) {
                targets.forEach((target) => {
                  results.push({
                    parent: p,
                    target
                  });
                });
              } else {
                results.push({
                  parent: p,
                  target: targets
                });
              }
            });
            return results;
          } else {
            const targets = _Tools.findElement(options.target, parent, multiple);
            if (targets instanceof Array) {
              targets.forEach((target) => {
                results.push({
                  parent,
                  target
                });
              });
            } else {
              results.push({
                parent,
                target: targets
              });
            }
          }
        }
      } else {
        const targets = _Tools.findElement(options.target, null, multiple);
        if (targets instanceof Array) {
          targets.forEach((target) => {
            results.push({
              parent: null,
              target
            });
          });
        } else {
          results.push({
            parent: null,
            target: targets
          });
        }
      }
      if (results.length === 0) {
        results.push({
          parent: null,
          target: null
        });
      }
      if (multiple) {
        return results;
      } else {
        if (results.length !== 1) {
          console.warn(
            "Multiple results found, even though multiple false",
            results
          );
        }
        return results[0];
      }
    }
  };
  _Tools.base = null;
  var Tools = _Tools;

  // lib/consentomatic/index.ts
  function matches(config) {
    const result = Tools.find(config);
    if (config.type === "css") {
      return !!result.target;
    } else if (config.type === "checkbox") {
      return !!result.target && result.target.checked;
    }
  }
  async function executeAction(config, param) {
    switch (config.type) {
      case "click":
        return clickAction(config);
      case "list":
        return listAction(config, param);
      case "consent":
        return consentAction(config, param);
      case "ifcss":
        return ifCssAction(config, param);
      case "waitcss":
        return waitCssAction(config);
      case "foreach":
        return forEachAction(config, param);
      case "hide":
        return hideAction(config);
      case "slide":
        return slideAction(config);
      case "close":
        return closeAction();
      case "wait":
        return waitAction(config);
      case "eval":
        return evalAction(config);
      default:
        throw "Unknown action type: " + config.type;
    }
  }
  var STEP_TIMEOUT = 0;
  function waitTimeout(timeout) {
    return new Promise((resolve) => {
      setTimeout(() => {
        resolve();
      }, timeout);
    });
  }
  async function clickAction(config) {
    const result = Tools.find(config);
    if (result.target != null) {
      result.target.click();
    }
    return waitTimeout(STEP_TIMEOUT);
  }
  async function listAction(config, param) {
    for (const action of config.actions) {
      await executeAction(action, param);
    }
  }
  async function consentAction(config, consentTypes) {
    for (const consentConfig of config.consents) {
      const shouldEnable = consentTypes.indexOf(consentConfig.type) !== -1;
      if (consentConfig.matcher && consentConfig.toggleAction) {
        const isEnabled = matches(consentConfig.matcher);
        if (isEnabled !== shouldEnable) {
          await executeAction(consentConfig.toggleAction);
        }
      } else {
        if (shouldEnable) {
          await executeAction(consentConfig.trueAction);
        } else {
          await executeAction(consentConfig.falseAction);
        }
      }
    }
  }
  async function ifCssAction(config, param) {
    const result = Tools.find(config);
    if (!result.target) {
      if (config.trueAction) {
        await executeAction(config.trueAction, param);
      }
    } else {
      if (config.falseAction) {
        await executeAction(config.falseAction, param);
      }
    }
  }
  async function waitCssAction(config) {
    await new Promise((resolve) => {
      let numRetries = config.retries || 10;
      const waitTime = config.waitTime || 250;
      const checkCss = () => {
        const result = Tools.find(config);
        if (config.negated && result.target || !config.negated && !result.target) {
          if (numRetries > 0) {
            numRetries -= 1;
            setTimeout(checkCss, waitTime);
          } else {
            resolve();
          }
        } else {
          resolve();
        }
      };
      checkCss();
    });
  }
  async function forEachAction(config, param) {
    const results = Tools.find(config, true);
    const oldBase = Tools.base;
    for (const result of results) {
      if (result.target) {
        Tools.setBase(result.target);
        await executeAction(config.action, param);
      }
    }
    Tools.setBase(oldBase);
  }
  async function hideAction(config) {
    const result = Tools.find(config);
    if (result.target) {
      result.target.classList.add("Autoconsent-Hidden");
    }
  }
  async function slideAction(config) {
    const result = Tools.find(config);
    const dragResult = Tools.find(config.dragTarget);
    if (result.target) {
      const targetBounds = result.target.getBoundingClientRect();
      const dragTargetBounds = dragResult.target.getBoundingClientRect();
      let yDiff = dragTargetBounds.top - targetBounds.top;
      let xDiff = dragTargetBounds.left - targetBounds.left;
      if (this.config.axis.toLowerCase() === "y") {
        xDiff = 0;
      }
      if (this.config.axis.toLowerCase() === "x") {
        yDiff = 0;
      }
      const screenX = window.screenX + targetBounds.left + targetBounds.width / 2;
      const screenY = window.screenY + targetBounds.top + targetBounds.height / 2;
      const clientX = targetBounds.left + targetBounds.width / 2;
      const clientY = targetBounds.top + targetBounds.height / 2;
      const mouseDown = document.createEvent("MouseEvents");
      mouseDown.initMouseEvent(
        "mousedown",
        true,
        true,
        window,
        0,
        screenX,
        screenY,
        clientX,
        clientY,
        false,
        false,
        false,
        false,
        0,
        result.target
      );
      const mouseMove = document.createEvent("MouseEvents");
      mouseMove.initMouseEvent(
        "mousemove",
        true,
        true,
        window,
        0,
        screenX + xDiff,
        screenY + yDiff,
        clientX + xDiff,
        clientY + yDiff,
        false,
        false,
        false,
        false,
        0,
        result.target
      );
      const mouseUp = document.createEvent("MouseEvents");
      mouseUp.initMouseEvent(
        "mouseup",
        true,
        true,
        window,
        0,
        screenX + xDiff,
        screenY + yDiff,
        clientX + xDiff,
        clientY + yDiff,
        false,
        false,
        false,
        false,
        0,
        result.target
      );
      result.target.dispatchEvent(mouseDown);
      await this.waitTimeout(10);
      result.target.dispatchEvent(mouseMove);
      await this.waitTimeout(10);
      result.target.dispatchEvent(mouseUp);
    }
  }
  async function waitAction(config) {
    await waitTimeout(config.waitTime);
  }
  async function closeAction() {
    window.close();
  }
  async function evalAction(config) {
    console.log("eval!", config.code);
    return new Promise((resolve) => {
      try {
        if (config.async) {
          window.eval(config.code);
          setTimeout(() => {
            resolve(window.eval("window.__consentCheckResult"));
          }, config.timeout || 250);
        } else {
          resolve(window.eval(config.code));
        }
      } catch (e) {
        console.warn("eval error", e, config.code);
        resolve(false);
      }
    });
  }

  // lib/random.ts
  function getRandomID() {
    if (crypto && typeof crypto.randomUUID !== "undefined") {
      return crypto.randomUUID();
    }
    return Math.random().toString();
  }

  // lib/eval-handler.ts
  var Deferred = class {
    constructor(id, timeout = 1e3) {
      this.id = id;
      this.promise = new Promise((resolve, reject) => {
        this.resolve = resolve;
        this.reject = reject;
      });
      this.timer = window.setTimeout(() => {
        this.reject(new Error("timeout"));
      }, timeout);
    }
  };
  var evalState = {
    pending: /* @__PURE__ */ new Map(),
    sendContentMessage: null
  };
  function requestEval(code, snippetId) {
    const id = getRandomID();
    evalState.sendContentMessage({
      type: "eval",
      id,
      code,
      snippetId
    });
    const deferred = new Deferred(id);
    evalState.pending.set(deferred.id, deferred);
    return deferred.promise;
  }
  function resolveEval(id, value) {
    const deferred = evalState.pending.get(id);
    if (deferred) {
      evalState.pending.delete(id);
      deferred.timer && window.clearTimeout(deferred.timer);
      deferred.resolve(value);
    } else {
      console.warn("no eval #", id);
    }
  }

  // lib/eval-snippets.ts
  var snippets = {
    // code-based rules
    EVAL_0: () => console.log(1),
    EVAL_CONSENTMANAGER_1: () => window.__cmp && typeof __cmp("getCMPData") === "object",
    EVAL_CONSENTMANAGER_2: () => !__cmp("consentStatus").userChoiceExists,
    EVAL_CONSENTMANAGER_3: () => __cmp("setConsent", 0),
    EVAL_CONSENTMANAGER_4: () => __cmp("setConsent", 1),
    EVAL_CONSENTMANAGER_5: () => __cmp("consentStatus").userChoiceExists,
    EVAL_COOKIEBOT_1: () => !!window.Cookiebot,
    EVAL_COOKIEBOT_2: () => !window.Cookiebot.hasResponse && window.Cookiebot.dialog?.visible === true,
    EVAL_COOKIEBOT_3: () => window.Cookiebot.withdraw() || true,
    EVAL_COOKIEBOT_4: () => window.Cookiebot.hide() || true,
    EVAL_COOKIEBOT_5: () => window.Cookiebot.declined === true,
    EVAL_KLARO_1: () => {
      const config = globalThis.klaroConfig || globalThis.klaro?.getManager && globalThis.klaro.getManager().config;
      if (!config) {
        return true;
      }
      const optionalServices = (config.services || config.apps).filter((s) => !s.required).map((s) => s.name);
      if (klaro && klaro.getManager) {
        const manager = klaro.getManager();
        return optionalServices.every((name) => !manager.consents[name]);
      } else if (klaroConfig && klaroConfig.storageMethod === "cookie") {
        const cookieName = klaroConfig.cookieName || klaroConfig.storageName;
        const consents = JSON.parse(decodeURIComponent(document.cookie.split(";").find((c) => c.trim().startsWith(cookieName)).split("=")[1]));
        return Object.keys(consents).filter((k) => optionalServices.includes(k)).every((k) => consents[k] === false);
      }
    },
    EVAL_KLARO_OPEN_POPUP: () => {
      klaro.show(void 0, true);
    },
    EVAL_KLARO_TRY_API_OPT_OUT: () => {
      if (window.klaro && typeof klaro.show === "function" && typeof klaro.getManager === "function") {
        try {
          klaro.getManager().changeAll(false);
          klaro.getManager().saveAndApplyConsents();
          return true;
        } catch (e) {
          console.warn(e);
          return false;
        }
      }
      return false;
    },
    EVAL_ONETRUST_1: () => window.OnetrustActiveGroups.split(",").filter((s) => s.length > 0).length <= 1,
    EVAL_TRUSTARC_TOP: () => window && window.truste && window.truste.eu.bindMap.prefCookie === "0",
    EVAL_TRUSTARC_FRAME_TEST: () => window && window.QueryString && window.QueryString.preferences === "0",
    EVAL_TRUSTARC_FRAME_GTM: () => window && window.QueryString && window.QueryString.gtm === "1",
    // declarative rules
    EVAL_ADROLL_0: () => !document.cookie.includes("__adroll_fpc"),
    EVAL_ALMACMP_0: () => document.cookie.includes('"name":"Google","consent":false'),
    EVAL_AFFINITY_SERIF_COM_0: () => document.cookie.includes("serif_manage_cookies_viewed") && !document.cookie.includes("serif_allow_analytics"),
    EVAL_ARBEITSAGENTUR_TEST: () => document.cookie.includes("cookie_consent=denied"),
    EVAL_AXEPTIO_0: () => document.cookie.includes("axeptio_authorized_vendors=%2C%2C"),
    EVAL_BAHN_TEST: () => utag.gdpr.getSelectedCategories().length === 1,
    EVAL_BING_0: () => document.cookie.includes("AL=0") && document.cookie.includes("AD=0") && document.cookie.includes("SM=0"),
    EVAL_BLOCKSY_0: () => document.cookie.includes("blocksy_cookies_consent_accepted=no"),
    EVAL_BORLABS_0: () => !JSON.parse(decodeURIComponent(document.cookie.split(";").find((c) => c.indexOf("borlabs-cookie") !== -1).split("=", 2)[1])).consents.statistics,
    EVAL_BUNDESREGIERUNG_DE_0: () => document.cookie.match("cookie-allow-tracking=0"),
    EVAL_CANVA_0: () => !document.cookie.includes("gtm_fpc_engagement_event"),
    EVAL_CC_BANNER2_0: () => !!document.cookie.match(/sncc=[^;]+D%3Dtrue/),
    EVAL_CLICKIO_0: () => document.cookie.includes("__lxG__consent__v2_daisybit="),
    EVAL_CLINCH_0: () => document.cookie.includes("ctc_rejected=1"),
    EVAL_COOKIECONSENT2_TEST: () => document.cookie.includes("cc_cookie="),
    EVAL_COOKIECONSENT3_TEST: () => document.cookie.includes("cc_cookie="),
    EVAL_COINBASE_0: () => JSON.parse(decodeURIComponent(document.cookie.match(/cm_(eu|default)_preferences=([0-9a-zA-Z\\{\\}\\[\\]%:]*);?/)[2])).consent.length <= 1,
    EVAL_COMPLIANZ_BANNER_0: () => document.cookie.includes("cmplz_banner-status=dismissed"),
    EVAL_COOKIE_LAW_INFO_0: () => CLI.disableAllCookies() || CLI.reject_close() || true,
    EVAL_COOKIE_LAW_INFO_1: () => document.cookie.indexOf("cookielawinfo-checkbox-non-necessary=yes") === -1,
    EVAL_COOKIE_LAW_INFO_DETECT: () => !!window.CLI,
    EVAL_COOKIE_MANAGER_POPUP_0: () => JSON.parse(document.cookie.split(";").find((c) => c.trim().startsWith("CookieLevel")).split("=")[1]).social === false,
    EVAL_COOKIEALERT_0: () => document.querySelector("body").removeAttribute("style") || true,
    EVAL_COOKIEALERT_1: () => document.querySelector("body").removeAttribute("style") || true,
    EVAL_COOKIEALERT_2: () => window.CookieConsent.declined === true,
    EVAL_COOKIEFIRST_0: () => ((o) => o.performance === false && o.functional === false && o.advertising === false)(JSON.parse(decodeURIComponent(document.cookie.split(";").find((c) => c.indexOf("cookiefirst") !== -1).trim()).split("=")[1])),
    EVAL_COOKIEFIRST_1: () => document.querySelectorAll("button[data-cookiefirst-accent-color=true][role=checkbox]:not([disabled])").forEach((i) => i.getAttribute("aria-checked") == "true" && i.click()) || true,
    EVAL_COOKIEINFORMATION_0: () => CookieInformation.declineAllCategories() || true,
    EVAL_COOKIEINFORMATION_1: () => CookieInformation.submitAllCategories() || true,
    EVAL_COOKIEINFORMATION_2: () => document.cookie.includes("CookieInformationConsent="),
    EVAL_COOKIEYES_0: () => document.cookie.includes("advertisement:no"),
    EVAL_DAILYMOTION_0: () => !!document.cookie.match("dm-euconsent-v2"),
    EVAL_DNDBEYOND_TEST: () => document.cookie.includes("cookie-consent=denied"),
    EVAL_DSGVO_0: () => !document.cookie.includes("sp_dsgvo_cookie_settings"),
    EVAL_DUNELM_0: () => document.cookie.includes("cc_functional=0") && document.cookie.includes("cc_targeting=0"),
    EVAL_ETSY_0: () => document.querySelectorAll(".gdpr-overlay-body input").forEach((toggle) => {
      toggle.checked = false;
    }) || true,
    EVAL_ETSY_1: () => document.querySelector(".gdpr-overlay-view button[data-wt-overlay-close]").click() || true,
    EVAL_EU_COOKIE_COMPLIANCE_0: () => document.cookie.indexOf("cookie-agreed=2") === -1,
    EVAL_EU_COOKIE_LAW_0: () => !document.cookie.includes("euCookie"),
    EVAL_EZOIC_0: () => ezCMP.handleAcceptAllClick(),
    EVAL_EZOIC_1: () => !!document.cookie.match(/ez-consent-tcf/),
    EVAL_FIDES_DETECT_POPUP: () => window.Fides?.initialized,
    EVAL_GOOGLE_0: () => !!document.cookie.match(/SOCS=CAE/),
    EVAL_HEMA_TEST_0: () => document.cookie.includes("cookies_rejected=1"),
    EVAL_IUBENDA_0: () => document.querySelectorAll(".purposes-item input[type=checkbox]:not([disabled])").forEach((x) => {
      if (x.checked)
        x.click();
    }) || true,
    EVAL_IUBENDA_1: () => !!document.cookie.match(/_iub_cs-\d+=/),
    EVAL_IWINK_TEST: () => document.cookie.includes("cookie_permission_granted=no"),
    EVAL_JQUERY_COOKIEBAR_0: () => !document.cookie.includes("cookies-state=accepted"),
    EVAL_KETCH_TEST: () => document.cookie.includes("_ketch_consent_v1_"),
    EVAL_MEDIAVINE_0: () => document.querySelectorAll('[data-name="mediavine-gdpr-cmp"] input[type=checkbox]').forEach((x) => x.checked && x.click()) || true,
    EVAL_MICROSOFT_0: () => Array.from(document.querySelectorAll("div > button")).filter((el) => el.innerText.match("Reject|Ablehnen"))[0].click() || true,
    EVAL_MICROSOFT_1: () => Array.from(document.querySelectorAll("div > button")).filter((el) => el.innerText.match("Accept|Annehmen"))[0].click() || true,
    EVAL_MICROSOFT_2: () => !!document.cookie.match("MSCC|GHCC"),
    EVAL_MOOVE_0: () => document.querySelectorAll("#moove_gdpr_cookie_modal input").forEach((i) => {
      if (!i.disabled)
        i.checked = i.name === "moove_gdpr_strict_cookies" || i.id === "moove_gdpr_strict_cookies";
    }) || true,
    EVAL_ONENINETWO_0: () => document.cookie.includes("CC_ADVERTISING=NO") && document.cookie.includes("CC_ANALYTICS=NO"),
    EVAL_OPERA_0: () => document.cookie.includes("cookie_consent_essential=true") && !document.cookie.includes("cookie_consent_marketing=true"),
    EVAL_PAYPAL_0: () => document.cookie.includes("cookie_prefs") === true,
    EVAL_PRIMEBOX_0: () => !document.cookie.includes("cb-enabled=accepted"),
    EVAL_PUBTECH_0: () => document.cookie.includes("euconsent-v2") && (document.cookie.match(/.YAAAAAAAAAAA/) || document.cookie.match(/.aAAAAAAAAAAA/) || document.cookie.match(/.YAAACFgAAAAA/)),
    EVAL_REDDIT_0: () => document.cookie.includes("eu_cookie={%22opted%22:true%2C%22nonessential%22:false}"),
    EVAL_ROBLOX_TEST: () => document.cookie.includes("RBXcb"),
    EVAL_SIRDATA_UNBLOCK_SCROLL: () => {
      document.documentElement.classList.forEach((cls) => {
        if (cls.startsWith("sd-cmp-"))
          document.documentElement.classList.remove(cls);
      });
      return true;
    },
    EVAL_SNIGEL_0: () => !!document.cookie.match("snconsent"),
    EVAL_STEAMPOWERED_0: () => JSON.parse(decodeURIComponent(document.cookie.split(";").find((s) => s.trim().startsWith("cookieSettings")).split("=")[1])).preference_state === 2,
    EVAL_SVT_TEST: () => document.cookie.includes('cookie-consent-1={"optedIn":true,"functionality":false,"statistics":false}'),
    EVAL_TAKEALOT_0: () => document.body.classList.remove("freeze") || (document.body.style = "") || true,
    EVAL_TARTEAUCITRON_0: () => tarteaucitron.userInterface.respondAll(false) || true,
    EVAL_TARTEAUCITRON_1: () => tarteaucitron.userInterface.respondAll(true) || true,
    EVAL_TARTEAUCITRON_2: () => document.cookie.match(/tarteaucitron=[^;]*/)?.[0].includes("false"),
    EVAL_TAUNTON_TEST: () => document.cookie.includes("taunton_user_consent_submitted=true"),
    EVAL_TEALIUM_0: () => typeof window.utag !== "undefined" && typeof utag.gdpr === "object",
    EVAL_TEALIUM_1: () => utag.gdpr.setConsentValue(false) || true,
    EVAL_TEALIUM_DONOTSELL: () => utag.gdpr.dns?.setDnsState(false) || true,
    EVAL_TEALIUM_2: () => utag.gdpr.setConsentValue(true) || true,
    EVAL_TEALIUM_3: () => utag.gdpr.getConsentState() !== 1,
    EVAL_TEALIUM_DONOTSELL_CHECK: () => utag.gdpr.dns?.getDnsState() !== 1,
    EVAL_TESTCMP_0: () => window.results.results[0] === "button_clicked",
    EVAL_TESTCMP_COSMETIC_0: () => window.results.results[0] === "banner_hidden",
    EVAL_THEFREEDICTIONARY_0: () => cmpUi.showPurposes() || cmpUi.rejectAll() || true,
    EVAL_THEFREEDICTIONARY_1: () => cmpUi.allowAll() || true,
    EVAL_THEVERGE_0: () => document.cookie.includes("_duet_gdpr_acknowledged=1"),
    EVAL_UBUNTU_COM_0: () => document.cookie.includes("_cookies_accepted=essential"),
    EVAL_UK_COOKIE_CONSENT_0: () => !document.cookie.includes("catAccCookies"),
    EVAL_USERCENTRICS_API_0: () => typeof UC_UI === "object",
    EVAL_USERCENTRICS_API_1: () => !!UC_UI.closeCMP(),
    EVAL_USERCENTRICS_API_2: () => !!UC_UI.denyAllConsents(),
    EVAL_USERCENTRICS_API_3: () => !!UC_UI.acceptAllConsents(),
    EVAL_USERCENTRICS_API_4: () => !!UC_UI.closeCMP(),
    EVAL_USERCENTRICS_API_5: () => UC_UI.areAllConsentsAccepted() === true,
    EVAL_USERCENTRICS_API_6: () => UC_UI.areAllConsentsAccepted() === false,
    EVAL_USERCENTRICS_BUTTON_0: () => JSON.parse(localStorage.getItem("usercentrics")).consents.every((c) => c.isEssential || !c.consentStatus),
    EVAL_WAITROSE_0: () => Array.from(document.querySelectorAll("label[id$=cookies-deny-label]")).forEach((e) => e.click()) || true,
    EVAL_WAITROSE_1: () => document.cookie.includes("wtr_cookies_advertising=0") && document.cookie.includes("wtr_cookies_analytics=0"),
    EVAL_WP_COOKIE_NOTICE_0: () => document.cookie.includes("wpl_viewed_cookie=no"),
    EVAL_XE_TEST: () => document.cookie.includes("xeConsentState={%22performance%22:false%2C%22marketing%22:false%2C%22compliance%22:false}"),
    EVAL_XING_0: () => document.cookie.includes("userConsent=%7B%22marketing%22%3Afalse"),
    EVAL_YOUTUBE_DESKTOP_0: () => !!document.cookie.match(/SOCS=CAE/),
    EVAL_YOUTUBE_MOBILE_0: () => !!document.cookie.match(/SOCS=CAE/)
  };
  function getFunctionBody(snippetFunc) {
    const snippetStr = snippetFunc.toString();
    return `(${snippetStr})()`;
  }

  // lib/cmps/base.ts
  var defaultRunContext = {
    main: true,
    frame: false,
    urlPattern: ""
  };
  var AutoConsentCMPBase = class {
    constructor(autoconsentInstance) {
      this.runContext = defaultRunContext;
      this.autoconsent = autoconsentInstance;
    }
    get hasSelfTest() {
      throw new Error("Not Implemented");
    }
    get isIntermediate() {
      throw new Error("Not Implemented");
    }
    get isCosmetic() {
      throw new Error("Not Implemented");
    }
    mainWorldEval(snippetId) {
      const snippet = snippets[snippetId];
      if (!snippet) {
        console.warn("Snippet not found", snippetId);
        return Promise.resolve(false);
      }
      const logsConfig = this.autoconsent.config.logs;
      if (this.autoconsent.config.isMainWorld) {
        logsConfig.evals && console.log("inline eval:", snippetId, snippet);
        let result = false;
        try {
          result = !!snippet.call(globalThis);
        } catch (e) {
          logsConfig.evals && console.error("error evaluating rule", snippetId, e);
        }
        return Promise.resolve(result);
      }
      const snippetSrc = getFunctionBody(snippet);
      logsConfig.evals && console.log("async eval:", snippetId, snippetSrc);
      return requestEval(snippetSrc, snippetId).catch((e) => {
        logsConfig.evals && console.error("error evaluating rule", snippetId, e);
        return false;
      });
    }
    checkRunContext() {
      const runCtx = {
        ...defaultRunContext,
        ...this.runContext
      };
      const isTop = window.top === window;
      if (isTop && !runCtx.main) {
        return false;
      }
      if (!isTop && !runCtx.frame) {
        return false;
      }
      if (runCtx.urlPattern && !window.location.href.match(runCtx.urlPattern)) {
        return false;
      }
      return true;
    }
    detectCmp() {
      throw new Error("Not Implemented");
    }
    async detectPopup() {
      return false;
    }
    optOut() {
      throw new Error("Not Implemented");
    }
    optIn() {
      throw new Error("Not Implemented");
    }
    openCmp() {
      throw new Error("Not Implemented");
    }
    async test() {
      return Promise.resolve(true);
    }
    // Implementing DomActionsProvider below:
    click(selector, all = false) {
      return this.autoconsent.domActions.click(selector, all);
    }
    elementExists(selector) {
      return this.autoconsent.domActions.elementExists(selector);
    }
    elementVisible(selector, check) {
      return this.autoconsent.domActions.elementVisible(selector, check);
    }
    waitForElement(selector, timeout) {
      return this.autoconsent.domActions.waitForElement(selector, timeout);
    }
    waitForVisible(selector, timeout, check) {
      return this.autoconsent.domActions.waitForVisible(selector, timeout, check);
    }
    waitForThenClick(selector, timeout, all) {
      return this.autoconsent.domActions.waitForThenClick(selector, timeout, all);
    }
    wait(ms) {
      return this.autoconsent.domActions.wait(ms);
    }
    hide(selector, method) {
      return this.autoconsent.domActions.hide(selector, method);
    }
    prehide(selector) {
      return this.autoconsent.domActions.prehide(selector);
    }
    undoPrehide() {
      return this.autoconsent.domActions.undoPrehide();
    }
    querySingleReplySelector(selector, parent) {
      return this.autoconsent.domActions.querySingleReplySelector(selector, parent);
    }
    querySelectorChain(selectors) {
      return this.autoconsent.domActions.querySelectorChain(selectors);
    }
    elementSelector(selector) {
      return this.autoconsent.domActions.elementSelector(selector);
    }
  };
  var AutoConsentCMP = class extends AutoConsentCMPBase {
    constructor(rule, autoconsentInstance) {
      super(autoconsentInstance);
      this.rule = rule;
      this.name = rule.name;
      this.runContext = rule.runContext || defaultRunContext;
    }
    get hasSelfTest() {
      return !!this.rule.test;
    }
    get isIntermediate() {
      return !!this.rule.intermediate;
    }
    get isCosmetic() {
      return !!this.rule.cosmetic;
    }
    get prehideSelectors() {
      return this.rule.prehideSelectors;
    }
    async detectCmp() {
      if (this.rule.detectCmp) {
        return this._runRulesParallel(this.rule.detectCmp);
      }
      return false;
    }
    async detectPopup() {
      if (this.rule.detectPopup) {
        return this._runRulesSequentially(this.rule.detectPopup);
      }
      return false;
    }
    async optOut() {
      const logsConfig = this.autoconsent.config.logs;
      if (this.rule.optOut) {
        logsConfig.lifecycle && console.log("Initiated optOut()", this.rule.optOut);
        return this._runRulesSequentially(this.rule.optOut);
      }
      return false;
    }
    async optIn() {
      const logsConfig = this.autoconsent.config.logs;
      if (this.rule.optIn) {
        logsConfig.lifecycle && console.log("Initiated optIn()", this.rule.optIn);
        return this._runRulesSequentially(this.rule.optIn);
      }
      return false;
    }
    async openCmp() {
      if (this.rule.openCmp) {
        return this._runRulesSequentially(this.rule.openCmp);
      }
      return false;
    }
    async test() {
      if (this.hasSelfTest) {
        return this._runRulesSequentially(this.rule.test);
      }
      return super.test();
    }
    async evaluateRuleStep(rule) {
      const results = [];
      const logsConfig = this.autoconsent.config.logs;
      if (rule.exists) {
        results.push(this.elementExists(rule.exists));
      }
      if (rule.visible) {
        results.push(this.elementVisible(rule.visible, rule.check));
      }
      if (rule.eval) {
        const res = this.mainWorldEval(rule.eval);
        results.push(res);
      }
      if (rule.waitFor) {
        results.push(this.waitForElement(rule.waitFor, rule.timeout));
      }
      if (rule.waitForVisible) {
        results.push(this.waitForVisible(rule.waitForVisible, rule.timeout, rule.check));
      }
      if (rule.click) {
        results.push(this.click(rule.click, rule.all));
      }
      if (rule.waitForThenClick) {
        results.push(this.waitForThenClick(rule.waitForThenClick, rule.timeout, rule.all));
      }
      if (rule.wait) {
        results.push(this.wait(rule.wait));
      }
      if (rule.hide) {
        results.push(this.hide(rule.hide, rule.method));
      }
      if (rule.if) {
        if (!rule.if.exists && !rule.if.visible) {
          console.error("invalid conditional rule", rule.if);
          return false;
        }
        const condition = await this.evaluateRuleStep(rule.if);
        logsConfig.rulesteps && console.log("Condition is", condition);
        if (condition) {
          results.push(this._runRulesSequentially(rule.then));
        } else if (rule.else) {
          results.push(this._runRulesSequentially(rule.else));
        } else {
          results.push(true);
        }
      }
      if (rule.any) {
        for (const step of rule.any) {
          if (await this.evaluateRuleStep(step)) {
            return true;
          }
        }
        return false;
      }
      if (results.length === 0) {
        logsConfig.errors && console.warn("Unrecognized rule", rule);
        return false;
      }
      const all = await Promise.all(results);
      return all.reduce((a, b) => a && b, true);
    }
    async _runRulesParallel(rules) {
      const results = rules.map((rule) => this.evaluateRuleStep(rule));
      const detections = await Promise.all(results);
      return detections.every((r) => !!r);
    }
    async _runRulesSequentially(rules) {
      const logsConfig = this.autoconsent.config.logs;
      for (const rule of rules) {
        logsConfig.rulesteps && console.log("Running rule...", rule);
        const result = await this.evaluateRuleStep(rule);
        logsConfig.rulesteps && console.log("...rule result", result);
        if (!result && !rule.optional) {
          return false;
        }
      }
      return true;
    }
  };

  // lib/cmps/consentomatic.ts
  var ConsentOMaticCMP = class {
    constructor(name, config) {
      this.name = name;
      this.config = config;
      this.methods = /* @__PURE__ */ new Map();
      this.runContext = defaultRunContext;
      this.isCosmetic = false;
      config.methods.forEach((methodConfig) => {
        if (methodConfig.action) {
          this.methods.set(methodConfig.name, methodConfig.action);
        }
      });
      this.hasSelfTest = false;
    }
    get isIntermediate() {
      return false;
    }
    checkRunContext() {
      return true;
    }
    async detectCmp() {
      const matchResults = this.config.detectors.map(
        (detectorConfig) => matches(detectorConfig.presentMatcher)
      );
      return matchResults.some((r) => !!r);
    }
    async detectPopup() {
      const matchResults = this.config.detectors.map(
        (detectorConfig) => matches(detectorConfig.showingMatcher)
      );
      return matchResults.some((r) => !!r);
    }
    async executeAction(method, param) {
      if (this.methods.has(method)) {
        return executeAction(this.methods.get(method), param);
      }
      return true;
    }
    async optOut() {
      await this.executeAction("HIDE_CMP");
      await this.executeAction("OPEN_OPTIONS");
      await this.executeAction("HIDE_CMP");
      await this.executeAction("DO_CONSENT", []);
      await this.executeAction("SAVE_CONSENT");
      return true;
    }
    async optIn() {
      await this.executeAction("HIDE_CMP");
      await this.executeAction("OPEN_OPTIONS");
      await this.executeAction("HIDE_CMP");
      await this.executeAction("DO_CONSENT", ["D", "A", "B", "E", "F", "X"]);
      await this.executeAction("SAVE_CONSENT");
      return true;
    }
    async openCmp() {
      await this.executeAction("HIDE_CMP");
      await this.executeAction("OPEN_OPTIONS");
      return true;
    }
    async test() {
      return true;
    }
  };

  // lib/utils.ts
  function getStyleElement(styleOverrideElementId = "autoconsent-css-rules") {
    const styleSelector = `style#${styleOverrideElementId}`;
    const existingElement = document.querySelector(styleSelector);
    if (existingElement && existingElement instanceof HTMLStyleElement) {
      return existingElement;
    } else {
      const parent = document.head || document.getElementsByTagName("head")[0] || document.documentElement;
      const css = document.createElement("style");
      css.id = styleOverrideElementId;
      parent.appendChild(css);
      return css;
    }
  }
  function hideElements(styleEl, selector, method = "display") {
    const hidingSnippet = method === "opacity" ? `opacity: 0` : `display: none`;
    const rule = `${selector} { ${hidingSnippet} !important; z-index: -1 !important; pointer-events: none !important; } `;
    if (styleEl instanceof HTMLStyleElement) {
      styleEl.innerText += rule;
      return selector.length > 0;
    }
    return false;
  }
  async function waitFor(predicate, maxTimes, interval) {
    const result = await predicate();
    if (!result && maxTimes > 0) {
      return new Promise((resolve) => {
        setTimeout(async () => {
          resolve(waitFor(predicate, maxTimes - 1, interval));
        }, interval);
      });
    }
    return Promise.resolve(result);
  }
  function isElementVisible(elem) {
    if (!elem) {
      return false;
    }
    if (elem.offsetParent !== null) {
      return true;
    } else {
      const css = window.getComputedStyle(elem);
      if (css.position === "fixed" && css.display !== "none") {
        return true;
      }
    }
    return false;
  }
  function copyObject(data) {
    if (globalThis.structuredClone) {
      return structuredClone(data);
    }
    return JSON.parse(JSON.stringify(data));
  }
  function normalizeConfig(providedConfig) {
    const defaultConfig = {
      enabled: true,
      autoAction: "optOut",
      // if falsy, the extension will wait for an explicit user signal before opting in/out
      disabledCmps: [],
      enablePrehide: true,
      enableCosmeticRules: true,
      detectRetries: 20,
      isMainWorld: false,
      prehideTimeout: 2e3,
      logs: {
        lifecycle: true,
        rulesteps: true,
        evals: false,
        errors: true,
        messages: false
      }
    };
    const updatedConfig = copyObject(defaultConfig);
    for (const key of Object.keys(defaultConfig)) {
      if (typeof providedConfig[key] !== "undefined") {
        updatedConfig[key] = providedConfig[key];
      }
    }
    return updatedConfig;
  }

  // lib/cmps/trustarc-top.ts
  var cookieSettingsButton = "#truste-show-consent";
  var shortcutOptOut = "#truste-consent-required";
  var shortcutOptIn = "#truste-consent-button";
  var popupContent = "#truste-consent-content";
  var bannerOverlay = "#trustarc-banner-overlay";
  var bannerContainer = "#truste-consent-track";
  var TrustArcTop = class extends AutoConsentCMPBase {
    constructor(autoconsentInstance) {
      super(autoconsentInstance);
      this.name = "TrustArc-top";
      this.prehideSelectors = [
        ".trustarc-banner-container",
        `.truste_popframe,.truste_overlay,.truste_box_overlay,${bannerContainer}`
      ];
      this.runContext = {
        main: true,
        frame: false
      };
      this._shortcutButton = null;
      this._optInDone = false;
    }
    get hasSelfTest() {
      return true;
    }
    get isIntermediate() {
      if (this._optInDone) {
        return false;
      }
      return !this._shortcutButton;
    }
    get isCosmetic() {
      return false;
    }
    async detectCmp() {
      const result = this.elementExists(`${cookieSettingsButton},${bannerContainer}`);
      if (result) {
        this._shortcutButton = document.querySelector(shortcutOptOut);
      }
      return result;
    }
    async detectPopup() {
      return this.elementVisible(`${popupContent},${bannerOverlay},${bannerContainer}`, "all");
    }
    openFrame() {
      this.click(cookieSettingsButton);
    }
    async optOut() {
      if (this._shortcutButton) {
        this._shortcutButton.click();
        return true;
      }
      hideElements(
        getStyleElement(),
        `.truste_popframe, .truste_overlay, .truste_box_overlay, ${bannerContainer}`
      );
      this.click(cookieSettingsButton);
      setTimeout(() => {
        getStyleElement().remove();
      }, 1e4);
      return true;
    }
    async optIn() {
      this._optInDone = true;
      return this.click(shortcutOptIn);
    }
    async openCmp() {
      return true;
    }
    async test() {
      await this.wait(500);
      return await this.mainWorldEval("EVAL_TRUSTARC_TOP");
    }
  };

  // lib/cmps/trustarc-frame.ts
  var TrustArcFrame = class extends AutoConsentCMPBase {
    constructor() {
      super(...arguments);
      this.name = "TrustArc-frame";
      this.runContext = {
        main: false,
        frame: true,
        urlPattern: "^https://consent-pref\\.trustarc\\.com/\\?"
      };
    }
    get hasSelfTest() {
      return true;
    }
    get isIntermediate() {
      return false;
    }
    get isCosmetic() {
      return false;
    }
    async detectCmp() {
      return true;
    }
    async detectPopup() {
      return this.elementVisible("#defaultpreferencemanager", "any") && this.elementVisible(".mainContent", "any");
    }
    async navigateToSettings() {
      await waitFor(
        async () => {
          return this.elementExists(".shp") || this.elementVisible(".advance", "any") || this.elementExists(".switch span:first-child");
        },
        10,
        500
      );
      if (this.elementExists(".shp")) {
        this.click(".shp");
      }
      await this.waitForElement(".prefPanel", 5e3);
      if (this.elementVisible(".advance", "any")) {
        this.click(".advance");
      }
      return await waitFor(
        () => this.elementVisible(".switch span:first-child", "any"),
        5,
        1e3
      );
    }
    async optOut() {
      if (await this.mainWorldEval("EVAL_TRUSTARC_FRAME_TEST")) {
        return true;
      }
      let timeout = 3e3;
      if (await this.mainWorldEval("EVAL_TRUSTARC_FRAME_GTM")) {
        timeout = 1500;
      }
      await waitFor(() => document.readyState === "complete", 20, 100);
      await this.waitForElement(".mainContent[aria-hidden=false]", timeout);
      if (this.click(".rejectAll")) {
        return true;
      }
      if (this.elementExists(".prefPanel")) {
        await this.waitForElement('.prefPanel[style="visibility: visible;"]', timeout);
      }
      if (this.click("#catDetails0")) {
        this.click(".submit");
        this.waitForThenClick("#gwt-debug-close_id", timeout);
        return true;
      }
      if (this.click(".required")) {
        this.waitForThenClick("#gwt-debug-close_id", timeout);
        return true;
      }
      await this.navigateToSettings();
      this.click(".switch span:nth-child(1):not(.active)", true);
      this.click(".submit");
      this.waitForThenClick("#gwt-debug-close_id", timeout * 10);
      return true;
    }
    async optIn() {
      if (this.click(".call")) {
        return true;
      }
      await this.navigateToSettings();
      this.click(".switch span:nth-child(2)", true);
      this.click(".submit");
      this.waitForElement("#gwt-debug-close_id", 3e5).then(() => {
        this.click("#gwt-debug-close_id");
      });
      return true;
    }
    async test() {
      await this.wait(500);
      return await this.mainWorldEval("EVAL_TRUSTARC_FRAME_TEST");
    }
  };

  // lib/cmps/cookiebot.ts
  var Cookiebot = class extends AutoConsentCMPBase {
    constructor() {
      super(...arguments);
      this.name = "Cybotcookiebot";
      this.prehideSelectors = ["#CybotCookiebotDialog,#CybotCookiebotDialogBodyUnderlay,#dtcookie-container,#cookiebanner,#cb-cookieoverlay,.modal--cookie-banner,#cookiebanner_outer,#CookieBanner"];
    }
    get hasSelfTest() {
      return true;
    }
    get isIntermediate() {
      return false;
    }
    get isCosmetic() {
      return false;
    }
    async detectCmp() {
      return await this.mainWorldEval("EVAL_COOKIEBOT_1");
    }
    async detectPopup() {
      return this.mainWorldEval("EVAL_COOKIEBOT_2");
    }
    async optOut() {
      await this.wait(500);
      let res = await this.mainWorldEval("EVAL_COOKIEBOT_3");
      await this.wait(500);
      res = res && await this.mainWorldEval("EVAL_COOKIEBOT_4");
      return res;
    }
    async optIn() {
      if (this.elementExists("#dtcookie-container")) {
        return this.click(".h-dtcookie-accept");
      }
      this.click(".CybotCookiebotDialogBodyLevelButton:not(:checked):enabled", true);
      this.click("#CybotCookiebotDialogBodyLevelButtonAccept");
      this.click("#CybotCookiebotDialogBodyButtonAccept");
      return true;
    }
    async test() {
      await this.wait(500);
      return await this.mainWorldEval("EVAL_COOKIEBOT_5");
    }
  };

  // lib/cmps/sourcepoint-frame.ts
  var SourcePoint = class extends AutoConsentCMPBase {
    constructor() {
      super(...arguments);
      this.name = "Sourcepoint-frame";
      this.prehideSelectors = ["div[id^='sp_message_container_'],.message-overlay", "#sp_privacy_manager_container"];
      this.ccpaNotice = false;
      this.ccpaPopup = false;
      this.runContext = {
        main: true,
        frame: true
      };
    }
    get hasSelfTest() {
      return false;
    }
    get isIntermediate() {
      return false;
    }
    get isCosmetic() {
      return false;
    }
    async detectCmp() {
      const url = new URL(location.href);
      if (url.searchParams.has("message_id") && url.hostname === "ccpa-notice.sp-prod.net") {
        this.ccpaNotice = true;
        return true;
      }
      if (url.hostname === "ccpa-pm.sp-prod.net") {
        this.ccpaPopup = true;
        return true;
      }
      return (url.pathname === "/index.html" || url.pathname === "/privacy-manager/index.html" || url.pathname === "/ccpa_pm/index.html") && (url.searchParams.has("message_id") || url.searchParams.has("requestUUID") || url.searchParams.has("consentUUID"));
    }
    async detectPopup() {
      if (this.ccpaNotice) {
        return true;
      }
      if (this.ccpaPopup) {
        return await this.waitForElement(".priv-save-btn", 2e3);
      }
      await this.waitForElement(".sp_choice_type_11,.sp_choice_type_12,.sp_choice_type_13,.sp_choice_type_ACCEPT_ALL,.sp_choice_type_SAVE_AND_EXIT", 2e3);
      return !this.elementExists(".sp_choice_type_9");
    }
    async optIn() {
      await this.waitForElement(".sp_choice_type_11,.sp_choice_type_ACCEPT_ALL", 2e3);
      if (this.click(".sp_choice_type_11")) {
        return true;
      }
      if (this.click(".sp_choice_type_ACCEPT_ALL")) {
        return true;
      }
      return false;
    }
    isManagerOpen() {
      return location.pathname === "/privacy-manager/index.html" || location.pathname === "/ccpa_pm/index.html";
    }
    async optOut() {
      const logsConfig = this.autoconsent.config.logs;
      if (this.ccpaPopup) {
        const toggles = document.querySelectorAll(".priv-purpose-container .sp-switch-arrow-block a.neutral.on .right");
        for (const t of toggles) {
          t.click();
        }
        const switches = document.querySelectorAll(".priv-purpose-container .sp-switch-arrow-block a.switch-bg.on");
        for (const t of switches) {
          t.click();
        }
        return this.click(".priv-save-btn");
      }
      if (!this.isManagerOpen()) {
        const actionable = await this.waitForElement(".sp_choice_type_12,.sp_choice_type_13");
        if (!actionable) {
          return false;
        }
        if (!this.elementExists(".sp_choice_type_12")) {
          return this.click(".sp_choice_type_13");
        }
        this.click(".sp_choice_type_12");
        await waitFor(
          () => this.isManagerOpen(),
          200,
          100
        );
      }
      await this.waitForElement(".type-modal", 2e4);
      this.waitForThenClick(".ccpa-stack .pm-switch[aria-checked=true] .slider", 500, true);
      try {
        const rejectSelector1 = ".sp_choice_type_REJECT_ALL";
        const rejectSelector2 = ".reject-toggle";
        const path = await Promise.race([
          this.waitForElement(rejectSelector1, 2e3).then((success) => success ? 0 : -1),
          this.waitForElement(rejectSelector2, 2e3).then((success) => success ? 1 : -1),
          this.waitForElement(".pm-features", 2e3).then((success) => success ? 2 : -1)
        ]);
        if (path === 0) {
          await this.wait(1500);
          return this.click(rejectSelector1);
        } else if (path === 1) {
          this.click(rejectSelector2);
        } else if (path === 2) {
          await this.waitForElement(".pm-features", 1e4);
          this.click(".checked > span", true);
          this.click(".chevron");
        }
      } catch (e) {
        logsConfig.errors && console.warn(e);
      }
      return this.click(".sp_choice_type_SAVE_AND_EXIT");
    }
  };

  // lib/cmps/consentmanager.ts
  var ConsentManager = class extends AutoConsentCMPBase {
    constructor() {
      super(...arguments);
      this.name = "consentmanager.net";
      this.prehideSelectors = ["#cmpbox,#cmpbox2"];
      this.apiAvailable = false;
    }
    get hasSelfTest() {
      return this.apiAvailable;
    }
    get isIntermediate() {
      return false;
    }
    get isCosmetic() {
      return false;
    }
    async detectCmp() {
      this.apiAvailable = await this.mainWorldEval("EVAL_CONSENTMANAGER_1");
      if (!this.apiAvailable) {
        return this.elementExists("#cmpbox");
      } else {
        return true;
      }
    }
    async detectPopup() {
      if (this.apiAvailable) {
        await this.wait(500);
        return await this.mainWorldEval("EVAL_CONSENTMANAGER_2");
      }
      return this.elementVisible("#cmpbox .cmpmore", "any");
    }
    async optOut() {
      await this.wait(500);
      if (this.apiAvailable) {
        return await this.mainWorldEval("EVAL_CONSENTMANAGER_3");
      }
      if (this.click(".cmpboxbtnno")) {
        return true;
      }
      if (this.elementExists(".cmpwelcomeprpsbtn")) {
        this.click(".cmpwelcomeprpsbtn > a[aria-checked=true]", true);
        this.click(".cmpboxbtnsave");
        return true;
      }
      this.click(".cmpboxbtncustom");
      await this.waitForElement(".cmptblbox", 2e3);
      this.click(".cmptdchoice > a[aria-checked=true]", true);
      this.click(".cmpboxbtnyescustomchoices");
      this.hide("#cmpwrapper,#cmpbox", "display");
      return true;
    }
    async optIn() {
      if (this.apiAvailable) {
        return await this.mainWorldEval("EVAL_CONSENTMANAGER_4");
      }
      return this.click(".cmpboxbtnyes");
    }
    async test() {
      if (this.apiAvailable) {
        return await this.mainWorldEval("EVAL_CONSENTMANAGER_5");
      }
    }
  };

  // lib/cmps/evidon.ts
  var Evidon = class extends AutoConsentCMPBase {
    constructor() {
      super(...arguments);
      this.name = "Evidon";
    }
    get hasSelfTest() {
      return false;
    }
    get isIntermediate() {
      return false;
    }
    get isCosmetic() {
      return false;
    }
    async detectCmp() {
      return this.elementExists("#_evidon_banner");
    }
    async detectPopup() {
      return this.elementVisible("#_evidon_banner", "any");
    }
    async optOut() {
      if (this.click("#_evidon-decline-button")) {
        return true;
      }
      hideElements(getStyleElement(), "#evidon-prefdiag-overlay,#evidon-prefdiag-background,#_evidon-background");
      await this.waitForThenClick("#_evidon-option-button");
      await this.waitForElement("#evidon-prefdiag-overlay", 5e3);
      await this.wait(500);
      await this.waitForThenClick("#evidon-prefdiag-decline");
      return true;
    }
    async optIn() {
      return this.click("#_evidon-accept-button");
    }
  };

  // lib/cmps/onetrust.ts
  var Onetrust = class extends AutoConsentCMPBase {
    constructor() {
      super(...arguments);
      this.name = "Onetrust";
      this.prehideSelectors = ["#onetrust-banner-sdk,#onetrust-consent-sdk,.onetrust-pc-dark-filter,.js-consent-banner"];
      this.runContext = {
        urlPattern: "^(?!.*https://www\\.nba\\.com/)"
      };
    }
    get hasSelfTest() {
      return true;
    }
    get isIntermediate() {
      return false;
    }
    get isCosmetic() {
      return false;
    }
    async detectCmp() {
      return this.elementExists("#onetrust-banner-sdk,#onetrust-pc-sdk");
    }
    async detectPopup() {
      return this.elementVisible("#onetrust-banner-sdk,#onetrust-pc-sdk", "any");
    }
    async optOut() {
      if (this.elementVisible("#onetrust-reject-all-handler,.ot-pc-refuse-all-handler,.js-reject-cookies", "any")) {
        return this.click("#onetrust-reject-all-handler,.ot-pc-refuse-all-handler,.js-reject-cookies");
      }
      if (this.elementExists("#onetrust-pc-btn-handler")) {
        this.click("#onetrust-pc-btn-handler");
      } else {
        this.click(".ot-sdk-show-settings,button.js-cookie-settings");
      }
      await this.waitForElement("#onetrust-consent-sdk", 2e3);
      await this.wait(1e3);
      this.click("#onetrust-consent-sdk input.category-switch-handler:checked,.js-editor-toggle-state:checked", true);
      await this.wait(1e3);
      await this.waitForElement(".save-preference-btn-handler,.js-consent-save", 2e3);
      this.click(".save-preference-btn-handler,.js-consent-save");
      await this.waitForVisible("#onetrust-banner-sdk", 5e3, "none");
      return true;
    }
    async optIn() {
      return this.click("#onetrust-accept-btn-handler,#accept-recommended-btn-handler,.js-accept-cookies");
    }
    async test() {
      return await waitFor(
        () => this.mainWorldEval("EVAL_ONETRUST_1"),
        10,
        500
      );
    }
  };

  // lib/cmps/klaro.ts
  var Klaro = class extends AutoConsentCMPBase {
    constructor() {
      super(...arguments);
      this.name = "Klaro";
      this.prehideSelectors = [".klaro"];
      this.settingsOpen = false;
    }
    get hasSelfTest() {
      return true;
    }
    get isIntermediate() {
      return false;
    }
    get isCosmetic() {
      return false;
    }
    async detectCmp() {
      if (this.elementExists(".klaro > .cookie-modal")) {
        this.settingsOpen = true;
        return true;
      }
      return this.elementExists(".klaro > .cookie-notice");
    }
    async detectPopup() {
      return this.elementVisible(".klaro > .cookie-notice,.klaro > .cookie-modal", "any");
    }
    async optOut() {
      const apiOptOutSuccess = await this.mainWorldEval("EVAL_KLARO_TRY_API_OPT_OUT");
      if (apiOptOutSuccess) {
        return true;
      }
      if (this.click(".klaro .cn-decline")) {
        return true;
      }
      await this.mainWorldEval("EVAL_KLARO_OPEN_POPUP");
      if (this.click(".klaro .cn-decline")) {
        return true;
      }
      this.click(".cm-purpose:not(.cm-toggle-all) > input:not(.half-checked,.required,.only-required),.cm-purpose:not(.cm-toggle-all) > div > input:not(.half-checked,.required,.only-required)", true);
      return this.click(".cm-btn-accept,.cm-button");
    }
    async optIn() {
      if (this.click(".klaro .cm-btn-accept-all")) {
        return true;
      }
      if (this.settingsOpen) {
        this.click(".cm-purpose:not(.cm-toggle-all) > input.half-checked", true);
        return this.click(".cm-btn-accept");
      }
      return this.click(".klaro .cookie-notice .cm-btn-success");
    }
    async test() {
      return await this.mainWorldEval("EVAL_KLARO_1");
    }
  };

  // lib/cmps/uniconsent.ts
  var Uniconsent = class extends AutoConsentCMPBase {
    constructor() {
      super(...arguments);
      this.name = "Uniconsent";
    }
    get prehideSelectors() {
      return [".unic", ".modal:has(.unic)"];
    }
    get hasSelfTest() {
      return true;
    }
    get isIntermediate() {
      return false;
    }
    get isCosmetic() {
      return false;
    }
    async detectCmp() {
      return this.elementExists(".unic .unic-box,.unic .unic-bar,.unic .unic-modal");
    }
    async detectPopup() {
      return this.elementVisible(".unic .unic-box,.unic .unic-bar,.unic .unic-modal", "any");
    }
    async optOut() {
      await this.waitForElement(".unic button", 1e3);
      document.querySelectorAll(".unic button").forEach((button) => {
        const text = button.textContent;
        if (text.includes("Manage Options") || text.includes("Optionen verwalten")) {
          button.click();
        }
      });
      if (await this.waitForElement(".unic input[type=checkbox]", 1e3)) {
        await this.waitForElement(".unic button", 1e3);
        document.querySelectorAll(".unic input[type=checkbox]").forEach((c) => {
          if (c.checked) {
            c.click();
          }
        });
        for (const b of document.querySelectorAll(".unic button")) {
          const text = b.textContent;
          for (const pattern of ["Confirm Choices", "Save Choices", "Auswahl speichern"]) {
            if (text.includes(pattern)) {
              b.click();
              await this.wait(500);
              return true;
            }
          }
        }
      }
      return false;
    }
    async optIn() {
      return this.waitForThenClick(".unic #unic-agree");
    }
    async test() {
      await this.wait(1e3);
      const res = this.elementExists(".unic .unic-box,.unic .unic-bar");
      return !res;
    }
  };

  // lib/cmps/conversant.ts
  var Conversant = class extends AutoConsentCMPBase {
    constructor() {
      super(...arguments);
      this.prehideSelectors = [".cmp-root"];
      this.name = "Conversant";
    }
    get hasSelfTest() {
      return true;
    }
    get isIntermediate() {
      return false;
    }
    get isCosmetic() {
      return false;
    }
    async detectCmp() {
      return this.elementExists(".cmp-root .cmp-receptacle");
    }
    async detectPopup() {
      return this.elementVisible(".cmp-root .cmp-receptacle", "any");
    }
    async optOut() {
      if (!await this.waitForThenClick(".cmp-main-button:not(.cmp-main-button--primary)")) {
        return false;
      }
      if (!await this.waitForElement(".cmp-view-tab-tabs")) {
        return false;
      }
      await this.waitForThenClick(".cmp-view-tab-tabs > :first-child");
      await this.waitForThenClick(".cmp-view-tab-tabs > .cmp-view-tab--active:first-child");
      for (const item of Array.from(document.querySelectorAll(".cmp-accordion-item"))) {
        item.querySelector(".cmp-accordion-item-title").click();
        await waitFor(() => !!item.querySelector(".cmp-accordion-item-content.cmp-active"), 10, 50);
        const content = item.querySelector(".cmp-accordion-item-content.cmp-active");
        content.querySelectorAll(".cmp-toggle-actions .cmp-toggle-deny:not(.cmp-toggle-deny--active)").forEach((e) => e.click());
        content.querySelectorAll(".cmp-toggle-actions .cmp-toggle-checkbox:not(.cmp-toggle-checkbox--active)").forEach((e) => e.click());
      }
      await this.click(".cmp-main-button:not(.cmp-main-button--primary)");
      return true;
    }
    async optIn() {
      return this.waitForThenClick(".cmp-main-button.cmp-main-button--primary");
    }
    async test() {
      return document.cookie.includes("cmp-data=0");
    }
  };

  // lib/cmps/tiktok.ts
  var Tiktok = class extends AutoConsentCMPBase {
    constructor() {
      super(...arguments);
      this.name = "tiktok.com";
      this.runContext = {
        urlPattern: "tiktok"
      };
    }
    get hasSelfTest() {
      return true;
    }
    get isIntermediate() {
      return false;
    }
    get isCosmetic() {
      return false;
    }
    getShadowRoot() {
      const container = document.querySelector("tiktok-cookie-banner");
      if (!container) {
        return null;
      }
      return container.shadowRoot;
    }
    async detectCmp() {
      return this.elementExists("tiktok-cookie-banner");
    }
    async detectPopup() {
      const banner = this.getShadowRoot().querySelector(".tiktok-cookie-banner");
      return isElementVisible(banner);
    }
    async optOut() {
      const logsConfig = this.autoconsent.config.logs;
      const declineButton = this.getShadowRoot().querySelector(".button-wrapper button:first-child");
      if (declineButton) {
        logsConfig.rulesteps && console.log("[clicking]", declineButton);
        declineButton.click();
        return true;
      } else {
        logsConfig.errors && console.log("no decline button found");
        return false;
      }
    }
    async optIn() {
      const logsConfig = this.autoconsent.config.logs;
      const acceptButton = this.getShadowRoot().querySelector(".button-wrapper button:last-child");
      if (acceptButton) {
        logsConfig.rulesteps && console.log("[clicking]", acceptButton);
        acceptButton.click();
        return true;
      } else {
        logsConfig.errors && console.log("no accept button found");
        return false;
      }
    }
    async test() {
      const match = document.cookie.match(/cookie-consent=([^;]+)/);
      if (!match) {
        return false;
      }
      const value = JSON.parse(decodeURIComponent(match[1]));
      return Object.values(value).every((x) => typeof x !== "boolean" || x === false);
    }
  };

  // lib/cmps/airbnb.ts
  var Airbnb = class extends AutoConsentCMPBase {
    constructor() {
      super(...arguments);
      this.name = "airbnb";
      this.runContext = {
        urlPattern: "^https://(www\\.)?airbnb\\.[^/]+/"
      };
      this.prehideSelectors = [
        "div[data-testid=main-cookies-banner-container]",
        'div:has(> div:first-child):has(> div:last-child):has(> section [data-testid="strictly-necessary-cookies"])'
      ];
    }
    get hasSelfTest() {
      return true;
    }
    get isIntermediate() {
      return false;
    }
    get isCosmetic() {
      return false;
    }
    async detectCmp() {
      return this.elementExists("div[data-testid=main-cookies-banner-container]");
    }
    async detectPopup() {
      return this.elementVisible("div[data-testid=main-cookies-banner-container", "any");
    }
    async optOut() {
      await this.waitForThenClick("div[data-testid=main-cookies-banner-container] button._snbhip0");
      let check;
      while (check = document.querySelector("[data-testid=modal-container] button[aria-checked=true]:not([disabled])")) {
        check.click();
      }
      return this.waitForThenClick("button[data-testid=save-btn]");
    }
    async optIn() {
      return this.waitForThenClick("div[data-testid=main-cookies-banner-container] button._148dgdpk");
    }
    async test() {
      return await waitFor(
        () => !!document.cookie.match("OptanonAlertBoxClosed"),
        20,
        200
      );
    }
  };

  // lib/cmps/tumblr-com.ts
  var Tumblr = class extends AutoConsentCMPBase {
    constructor() {
      super(...arguments);
      this.name = "tumblr-com";
      this.runContext = {
        "urlPattern": "^https://(www\\.)?tumblr\\.com/"
      };
    }
    get hasSelfTest() {
      return false;
    }
    get isIntermediate() {
      return false;
    }
    get isCosmetic() {
      return false;
    }
    get prehideSelectors() {
      return ["#cmp-app-container"];
    }
    async detectCmp() {
      return this.elementExists("#cmp-app-container");
    }
    async detectPopup() {
      return this.elementVisible("#cmp-app-container", "any");
    }
    async optOut() {
      let iframe = document.querySelector("#cmp-app-container iframe");
      let settingsButton = iframe.contentDocument?.querySelector(".cmp-components-button.is-secondary");
      if (!settingsButton) {
        return false;
      }
      settingsButton.click();
      await waitFor(() => {
        const iframe2 = document.querySelector("#cmp-app-container iframe");
        return !!iframe2.contentDocument?.querySelector(".cmp__dialog input");
      }, 5, 500);
      iframe = document.querySelector("#cmp-app-container iframe");
      settingsButton = iframe.contentDocument?.querySelector(".cmp-components-button.is-secondary");
      if (!settingsButton) {
        return false;
      }
      settingsButton.click();
      return true;
    }
    async optIn() {
      const iframe = document.querySelector("#cmp-app-container iframe");
      const acceptButton = iframe.contentDocument.querySelector(".cmp-components-button.is-primary");
      if (acceptButton) {
        acceptButton.click();
        return true;
      }
      return false;
    }
  };

  // lib/cmps/all.ts
  var dynamicCMPs = [
    TrustArcTop,
    TrustArcFrame,
    Cookiebot,
    SourcePoint,
    ConsentManager,
    Evidon,
    Onetrust,
    Klaro,
    Uniconsent,
    Conversant,
    Tiktok,
    Airbnb,
    Tumblr
  ];

  // lib/dom-actions.ts
  var DomActions = class {
    constructor(autoconsentInstance) {
      this.autoconsentInstance = autoconsentInstance;
    }
    click(selector, all = false) {
      const elem = this.elementSelector(selector);
      this.autoconsentInstance.config.logs.rulesteps && console.log("[click]", selector, all, elem);
      if (elem.length > 0) {
        if (all) {
          elem.forEach((e) => e.click());
        } else {
          elem[0].click();
        }
      }
      return elem.length > 0;
    }
    elementExists(selector) {
      const exists = this.elementSelector(selector).length > 0;
      return exists;
    }
    elementVisible(selector, check) {
      const elem = this.elementSelector(selector);
      const results = new Array(elem.length);
      elem.forEach((e, i) => {
        results[i] = isElementVisible(e);
      });
      if (check === "none") {
        return results.every((r) => !r);
      } else if (results.length === 0) {
        return false;
      } else if (check === "any") {
        return results.some((r) => r);
      }
      return results.every((r) => r);
    }
    waitForElement(selector, timeout = 1e4) {
      const interval = 200;
      const times = Math.ceil(timeout / interval);
      this.autoconsentInstance.config.logs.rulesteps && console.log("[waitForElement]", selector);
      return waitFor(
        () => this.elementSelector(selector).length > 0,
        times,
        interval
      );
    }
    waitForVisible(selector, timeout = 1e4, check = "any") {
      const interval = 200;
      const times = Math.ceil(timeout / interval);
      return waitFor(
        () => this.elementVisible(selector, check),
        times,
        interval
      );
    }
    async waitForThenClick(selector, timeout = 1e4, all = false) {
      await this.waitForElement(selector, timeout);
      return this.click(selector, all);
    }
    wait(ms) {
      return new Promise((resolve) => {
        setTimeout(() => {
          resolve(true);
        }, ms);
      });
    }
    hide(selector, method) {
      const styleEl = getStyleElement();
      return hideElements(styleEl, selector, method);
    }
    prehide(selector) {
      const styleEl = getStyleElement("autoconsent-prehide");
      this.autoconsentInstance.config.logs.lifecycle && console.log("[prehide]", styleEl, location.href);
      return hideElements(styleEl, selector, "opacity");
    }
    undoPrehide() {
      const existingElement = getStyleElement("autoconsent-prehide");
      this.autoconsentInstance.config.logs.lifecycle && console.log("[undoprehide]", existingElement, location.href);
      if (existingElement) {
        existingElement.remove();
      }
      return !!existingElement;
    }
    applyCosmetics(selector) {
      const styleEl = getStyleElement("autoconsent-cosmetics");
      this.autoconsentInstance.config.logs.lifecycle && console.log("[cosmetics]", styleEl, location.href);
      return hideElements(styleEl, selector, "opacity");
    }
    undoCosmetics() {
      const existingElement = getStyleElement("autoconsent-cosmetics");
      this.autoconsentInstance.config.logs.lifecycle && console.log("[undocosmetics]", existingElement, location.href);
      if (existingElement) {
        existingElement.remove();
      }
      return !!existingElement;
    }
    querySingleReplySelector(selector, parent = document) {
      if (selector.startsWith("aria/")) {
        return [];
      }
      if (selector.startsWith("xpath/")) {
        const xpath = selector.slice(6);
        const result = document.evaluate(xpath, parent, null, XPathResult.ANY_TYPE, null);
        let node = null;
        const elements = [];
        while (node = result.iterateNext()) {
          elements.push(node);
        }
        return elements;
      }
      if (selector.startsWith("text/")) {
        return [];
      }
      if (selector.startsWith("pierce/")) {
        return [];
      }
      if (parent.shadowRoot) {
        return Array.from(parent.shadowRoot.querySelectorAll(selector));
      }
      return Array.from(parent.querySelectorAll(selector));
    }
    querySelectorChain(selectors) {
      let parent = document;
      let matches3;
      for (const selector of selectors) {
        matches3 = this.querySingleReplySelector(selector, parent);
        if (matches3.length === 0) {
          return [];
        }
        parent = matches3[0];
      }
      return matches3;
    }
    elementSelector(selector) {
      if (typeof selector === "string") {
        return this.querySingleReplySelector(selector);
      }
      return this.querySelectorChain(selector);
    }
  };

  // node_modules/@remusao/trie/dist/es6/index.js
  function newNode() {
    return {
      chars: /* @__PURE__ */ new Map(),
      code: void 0
    };
  }
  function create(strings) {
    const node = newNode();
    for (let i = 0; i < strings.length; i += 1) {
      const tok = strings[i];
      let root = node;
      for (let j = 0; j < tok.length; j += 1) {
        const c = tok.charCodeAt(j);
        let next = root.chars.get(c);
        if (next === void 0) {
          next = newNode();
          root.chars.set(c, next);
        }
        root = next;
      }
      root.code = i;
    }
    return node;
  }

  // node_modules/@remusao/smaz-compress/dist/es6/index.js
  var EMPTY_UINT8_ARRAY = new Uint8Array(0);
  var SmazCompress = class {
    constructor(codebook, maxSize = 3e4) {
      this.trie = create(codebook);
      this.buffer = new Uint8Array(maxSize);
      this.verbatim = new Uint8Array(255);
    }
    getCompressedSize(str) {
      if (str.length === 0) {
        return 0;
      }
      let bufferIndex = 0;
      let verbatimIndex = 0;
      let inputIndex = 0;
      while (inputIndex < str.length) {
        let indexAfterMatch = -1;
        let code = -1;
        let root = this.trie;
        for (let j = inputIndex; j < str.length; j += 1) {
          root = root.chars.get(str.charCodeAt(j));
          if (root === void 0) {
            break;
          }
          if (root.code !== void 0) {
            code = root.code;
            indexAfterMatch = j + 1;
          }
        }
        if (code === -1) {
          verbatimIndex++;
          inputIndex++;
          if (verbatimIndex === 255) {
            bufferIndex += 2 + verbatimIndex;
            verbatimIndex = 0;
          }
        } else {
          if (verbatimIndex !== 0) {
            bufferIndex += 2 + (verbatimIndex === 1 ? 0 : verbatimIndex);
            verbatimIndex = 0;
          }
          bufferIndex++;
          inputIndex = indexAfterMatch;
        }
      }
      if (verbatimIndex !== 0) {
        bufferIndex += 2 + (verbatimIndex === 1 ? 0 : verbatimIndex);
      }
      return bufferIndex;
    }
    compress(str) {
      if (str.length === 0) {
        return EMPTY_UINT8_ARRAY;
      }
      let bufferIndex = 0;
      let verbatimIndex = 0;
      let inputIndex = 0;
      const len = str.length;
      while (inputIndex < str.length) {
        let indexAfterMatch = -1;
        let code = -1;
        let root = this.trie;
        for (let j = inputIndex; j < len; j += 1) {
          root = root.chars.get(str.charCodeAt(j));
          if (root === void 0) {
            break;
          }
          if (root.code !== void 0) {
            code = root.code;
            indexAfterMatch = j + 1;
          }
        }
        if (code === -1) {
          this.verbatim[verbatimIndex++] = str.charCodeAt(inputIndex++);
          if (verbatimIndex === 255) {
            bufferIndex = this.flushVerbatim(verbatimIndex, bufferIndex);
            verbatimIndex = 0;
          }
        } else {
          if (verbatimIndex !== 0) {
            bufferIndex = this.flushVerbatim(verbatimIndex, bufferIndex);
            verbatimIndex = 0;
          }
          this.buffer[bufferIndex++] = code;
          inputIndex = indexAfterMatch;
        }
      }
      if (verbatimIndex !== 0) {
        bufferIndex = this.flushVerbatim(verbatimIndex, bufferIndex);
      }
      return this.buffer.slice(0, bufferIndex);
    }
    flushVerbatim(verbatimIndex, bufferIndex) {
      if (verbatimIndex === 1) {
        this.buffer[bufferIndex++] = 254;
        this.buffer[bufferIndex++] = this.verbatim[0];
      } else {
        this.buffer[bufferIndex++] = 255;
        this.buffer[bufferIndex++] = verbatimIndex;
        for (let k = 0; k < verbatimIndex; k += 1) {
          this.buffer[bufferIndex++] = this.verbatim[k];
        }
      }
      return bufferIndex;
    }
  };

  // node_modules/@remusao/smaz-decompress/dist/es6/index.js
  var SmazDecompress = class {
    constructor(codebook) {
      this.codebook = codebook;
    }
    decompress(arr) {
      if (arr.byteLength === 0) {
        return "";
      }
      let output = "";
      let i = 0;
      while (i < arr.byteLength) {
        if (arr[i] === 254) {
          output += String.fromCharCode(arr[i + 1]);
          i += 2;
        } else if (arr[i] === 255) {
          const stop = i + arr[i + 1] + 2;
          for (i += 2; i < stop; i += 1) {
            output += String.fromCharCode(arr[i]);
          }
        } else {
          output += this.codebook[arr[i]];
          i += 1;
        }
      }
      return output;
    }
  };

  // node_modules/@remusao/smaz/dist/es6/index.js
  var Smaz = class {
    constructor(codebook, maxSize = 3e4) {
      this.codebook = codebook;
      this.compressor = new SmazCompress(codebook, maxSize);
      this.decompressor = new SmazDecompress(codebook);
    }
    compress(str) {
      return this.compressor.compress(str);
    }
    getCompressedSize(str) {
      return this.compressor.getCompressedSize(str);
    }
    decompress(buffer) {
      return this.decompressor.decompress(buffer);
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/codebooks/cosmetic-selector.js
  var cosmetic_selector_default = [
    ", euconsent-v2, CPzBFAAPzBFAAAHABBENDYCgAAAAAAAAAAAAJNFB_W_fD2Ni-35_avt0aQ1dwVC_6-UxDgKZB4kFyRpEMKwX3mAKKFXgpKAKGBYEsUZAIQBlHCHEDECwQIERLzHMIAEQJQIAJqJEgFERAkJQCBpZHwMACAIQgHRWATFIiB-HaBroyfhEMaC0AUBQ4AonhMTPAoSdwXCkg7uaHIgIImgFASBAIoYMEEEEBlTkFABAAAkAAABJSADAAEQUCUAGAAIgoDoAMAARBQIQAYAAiCgEgAwABEFA",
    "trusted-set-cookie, CookieConsent, {stamp:%27Uv9YOAVP5djCBw71lxhE4rNAfTObaGck2Sn3rKWf9dPCYpqcWvAlpA==%27%2Cnecessary:true%2Cpreferences:false%2Cstatistics:false%2Cmarketing:false%2Cmethod:%27explicit%27%2Cver:",
    'div[style="position: fixed; display: block; width: 100%; height: 100%; inset: 0px; background-color: rgba(0, 0, 0, 0); z-index: 300000;"]',
    "acs, document.createElement, /l\\.parentNode\\.insertBefore\\(s/",
    ':not([style^="position: absolute; left: -5000px"])',
    "acs, addEventListener, google_ad_client",
    "aost, String.prototype.charCodeAt, ai_",
    "aopr, app_vars.force_disable_adblock",
    "aeld, DOMContentLoaded, adsBlocked",
    'paid.outbrain.com/network/redir?"]',
    "acs, document.addEventListener, ",
    "acs, document.getElementById, ",
    "no-fetch-if, googlesyndication",
    "acs, document.createElement, ",
    "aopr, document.dispatchEvent",
    "acs, String.fromCharCode, ",
    "nosiif, visibility, 1000",
    "trusted-click-element, ",
    "set, Object.prototype.",
    "set, blurred, false",
    "acs, eval, replace",
    '[target="_blank"]',
    "^script:has-text(",
    '[href^="https://',
    '[href^="http://',
    "set, flashvars.",
    "rmnt, script, ",
    "#custom_html-",
    "BlockDetected",
    "no-fetch-if, ",
    'div[class^="',
    "notification",
    "trusted-set-",
    ", document.",
    "contextmenu",
    ", noopFunc",
    ":has-text(",
    "AaDetector",
    "background",
    "-backdrop",
    "container",
    "Container",
    "decodeURI",
    'div[id^="',
    "div[style",
    "no-xhr-if",
    "placehold",
    "rectangle",
    '[href*="',
    "#wpsafe-",
    "AAAAAAAA",
    "disclaim",
    "https://",
    "nano-sib",
    "nextFunc",
    "nostif, ",
    "Notifica",
    "nowebrtc",
    '.com/"]',
    "300x250",
    "article",
    "consent",
    "content",
    "message",
    "Message",
    "privacy",
    "sidebar",
    "sponsor",
    "wrapper",
    "-child",
    "[class",
    "[data-",
    "%22%3A",
    "%2C%22",
    "728x90",
    "accept",
    "Accept",
    "aeld, ",
    "aopr, ",
    "banner",
    "billbo",
    "bottom",
    "cookie",
    "Cookie",
    "google",
    "nosiif",
    "notice",
    "nowoif",
    "policy",
    "Policy",
    "sticky",
    "widget",
    ":has(",
    ":not(",
    "block",
    "Block",
    "click",
    "cript",
    "fixed",
    "frame",
    "modal",
    "popup",
    "0px;",
    "body",
    "butt",
    "down",
    "foot",
    "gdpr",
    "html",
    "ight",
    "show",
    "tion",
    "true",
    "www.",
    " > ",
    "age",
    "box",
    "Box",
    "div",
    "ent",
    "lay",
    "out",
    "rap",
    "sby",
    "top",
    "__",
    ", ",
    ": ",
    ".m",
    '"]',
    '="',
    "00",
    "ab",
    "ac",
    "ad",
    "Ad",
    "al",
    "am",
    "an",
    "ar",
    "at",
    "d-",
    "de",
    "e-",
    "ed",
    "el",
    "en",
    "er",
    "et",
    "fo",
    "g-",
    "he",
    "ic",
    "id",
    "im",
    "in",
    "is",
    "it",
    "la",
    "le",
    "lo",
    "ol",
    "om",
    "on",
    "op",
    "or",
    "ot",
    "ov",
    "p-",
    "ra",
    "re",
    "ro",
    "s_",
    "s-",
    "se",
    "st",
    "t-",
    "te",
    "ti",
    "un",
    " ",
    "_",
    "-",
    ";",
    ":",
    ".",
    "'",
    '"',
    ")",
    "[",
    "]",
    "*",
    "/",
    "#",
    "%",
    "^",
    "|",
    "0",
    "1",
    "2",
    "3",
    "4",
    "5",
    "6",
    "7",
    "8",
    "9",
    "a",
    "b",
    "B",
    "c",
    "C",
    "d",
    "D",
    "e",
    "E",
    "f",
    "F",
    "g",
    "G",
    "h",
    "H",
    "i",
    "I",
    "j",
    "J",
    "k",
    "l",
    "L",
    "m",
    "M",
    "n",
    "N",
    "o",
    "O",
    "p",
    "P",
    "q",
    "Q",
    "r",
    "R",
    "s",
    "S",
    "t",
    "T",
    "u",
    "U",
    "v",
    "V",
    "w",
    "W",
    "x",
    "y",
    "z"
  ];

  // node_modules/@cliqz/adblocker/dist/es6/src/codebooks/network-csp.js
  var network_csp_default = [
    "sandbox allow-forms allow-same-origin allow-scripts allow-modals allow-orientation-lock allow-pointer-lock allow-presentation allow-top-navigation",
    "script-src 'self' 'unsafe-eval' http: https: data: blob: mediastream: filesystem:",
    "script-src 'self' 'unsafe-inline' 'unsafe-eval' data:",
    " *.google.com *.gstatic.com *.googleapis.com",
    ".com *.google.com *.googletagmanager.com *.",
    "script-src 'self' '*' 'unsafe-inline'",
    "default-src 'unsafe-inline' 'self'",
    " *.google.com *.gstatic.com *.",
    "t-src 'self' 'unsafe-inline' ",
    "script-src * 'unsafe-inline'",
    ".com *.googleapis.com *.",
    " *.googletagmanager.com",
    "default-src 'self'",
    "frame-src 'self' *",
    " *.cloudflare.com",
    "child-src 'none';",
    "worker-src 'none'",
    " 'unsafe-inline'",
    "google-analytics",
    "'unsafe-eval'",
    "bootstrapcdn",
    "connect-src ",
    "child-src *",
    "googleapis",
    "jquery.com",
    "script-src",
    "style-src ",
    "*.gstatic",
    "frame-src",
    "facebook",
    "https://",
    ".com *.",
    "addthis",
    "captcha",
    "gstatic",
    "youtube",
    " data:",
    "'self'",
    "defaul",
    "disqus",
    "google",
    "scrip",
    "ytimg",
    ".com",
    ".net",
    "n.cc",
    " *.",
    "age",
    "dia",
    "vic",
    " *",
    "ch",
    "er",
    "im",
    "in",
    "me",
    "ra",
    "rc",
    "re",
    "ta",
    "tt",
    "wi",
    "wp",
    " ",
    "-",
    ";",
    ":",
    ".",
    "'",
    "*",
    "/",
    "3",
    "a",
    "b",
    "c",
    "d",
    "e",
    "f",
    "g",
    "h",
    "i",
    "j",
    "k",
    "l",
    "m",
    "n",
    "o",
    "p",
    "q",
    "r",
    "s",
    "t",
    "u",
    "v",
    "w",
    "x",
    "y"
  ];

  // node_modules/@cliqz/adblocker/dist/es6/src/codebooks/network-filter.js
  var network_filter_default = [
    "\\/[a-d][-\\.\\/_A-Za-z][DHWXm][-\\.\\/_A-Za-z][59FVZ][-\\.\\/_A-Za-z][6swyz][-\\.\\/_A-Za-z][-\\/_0-9a-zA-Z][-\\.\\/_A-Za-z][-\\/_0-9a-zA-Z]{22,162}$/",
    "\\/(?=[\\/a-zA-Z]*[+0-9])(?=[+\\/0-9a-z]*[A-Z])[+\\/0-9a-zA-Z]{",
    "/homad-global-configs.schneevonmorgen.com/global_config",
    "/videojs-vast-vpaid@2.0.2/bin/videojs_5.vast.vpaid.min",
    "/etc.clientlibs/logitech-common/clientlibs/onetrust.",
    "[\\/]{1,}.*[a-zA-Z0-9]{3,7}\\/[a-zA-Z0-9]{6,}\\/.*/",
    "\\/(?=[a-z]{0,6}[0-9A-Z])[0-9a-zA-Z]{7}\\",
    "/pagead/managed/js/gpt/*/pubads_impl",
    "\\?aHR0c[\\/0-9a-zA-Z]{33,}=?=?$/",
    "\\.pussyspace\\.(?:com|net)\\/",
    "/pagead/js/adsbygoogle.js",
    "\\/[+\\/0-9a-zA-Z]{400,}$/",
    "/fileadmin/user_upload/",
    "/js/sdkloader/ima3_dai",
    "/js/sdkloader/ima3.js",
    "/wp-content/plugins/",
    "/wp-content/uploads/",
    "/wp-content/themes/",
    "/detroitchicago/",
    "*/satellitelib-",
    "/appmeasurement",
    "/img/linklist2/",
    "/(https?:\\/\\/)",
    "/cdn-cgi/trace",
    "\\/[a-zA-Z0-9]{",
    "[a-z]{8,15}\\.",
    "/^https?:\\/\\/",
    "/^https:\\/\\/",
    "notification",
    "impression",
    "[0-9a-f]{",
    "[0-9a-z]{",
    "/template",
    "affiliate",
    "analytics",
    "/assets/",
    "/images/",
    "tracking",
    "300x250",
    "captcha",
    "collect",
    "consent",
    "content",
    "counter",
    "privacy",
    "sponsor",
    ".aspx?",
    "/media",
    "/video",
    "0-9a-z",
    "728x90",
    "banner",
    "bundle",
    "client",
    "cookie",
    "detect",
    "google",
    "iframe",
    "metric",
    "prebid",
    "public",
    "script",
    "source",
    "widget",
    ".cgi?",
    ".com/",
    ".html",
    "/api/",
    "/file",
    "/html",
    "/img/",
    "/java",
    "/page",
    "/stat",
    "0x600",
    "a-z]{",
    "block",
    "click",
    "count",
    "event",
    "fault",
    "image",
    "manag",
    "pixel",
    "popup",
    "track",
    ".gif",
    ".jpg",
    ".min",
    ".php",
    ".png",
    "/jqu",
    "/js/",
    "/lib",
    "/log",
    "/web",
    "/wp-",
    "468x",
    "bung",
    "gdpr",
    "gi-b",
    "page",
    "play",
    "view",
    ".js",
    "(?:",
    "}\\.",
    "/ad",
    "/js",
    "=*&",
    "com",
    "how",
    "id=",
    "jax",
    "lug",
    "new",
    "sdk",
    "tag",
    "web",
    "*/",
    "*^",
    "/_",
    "/?",
    "/*",
    "/d",
    "/f",
    "/g",
    "/h",
    "/l",
    "/m",
    "/n",
    "/r",
    "/u",
    "/v",
    "/w",
    "\\/",
    "ac",
    "ad",
    "ag",
    "al",
    "am",
    "an",
    "ap",
    "ar",
    "as",
    "at",
    "bo",
    "ce",
    "ch",
    "co",
    "de",
    "e-",
    "e/",
    "ed",
    "el",
    "em",
    "en",
    "er",
    "et",
    "ic",
    "id",
    "ig",
    "il",
    "im",
    "in",
    "is",
    "it",
    "la",
    "le",
    "li",
    "lo",
    "ma",
    "mp",
    "ol",
    "om",
    "on",
    "op",
    "or",
    "ot",
    "re",
    "ro",
    "s_",
    "s-",
    "s?",
    "s/",
    "si",
    "sp",
    "ss",
    "st",
    "t/",
    "te",
    "ti",
    "tm",
    "tr",
    "ub",
    "um",
    "un",
    "ur",
    "us",
    "ut",
    "ve",
    "_",
    "-",
    ",",
    ":",
    "?",
    ".",
    ")",
    "[",
    "}",
    "*",
    "/",
    "\\",
    "&",
    "^",
    "=",
    "|",
    "$",
    "0",
    "1",
    "2",
    "3",
    "4",
    "5",
    "6",
    "7",
    "8",
    "9",
    "a",
    "b",
    "c",
    "d",
    "e",
    "f",
    "g",
    "h",
    "i",
    "j",
    "k",
    "l",
    "m",
    "n",
    "o",
    "p",
    "q",
    "r",
    "s",
    "t",
    "u",
    "v",
    "w",
    "x",
    "y",
    "z"
  ];

  // node_modules/@cliqz/adblocker/dist/es6/src/codebooks/network-hostname.js
  var network_hostname_default = [
    "securepubads.g.doubleclick",
    ".xx.fbcdn.net.iberostar",
    "googlesyndication",
    "imasdk.googleapis",
    ".cloudfront.net",
    "googletagmanag",
    ".actonservice",
    "marketing.",
    "analytics",
    "tracking.",
    "metrics.",
    ".co.jp",
    ".co.uk",
    "a8clk.",
    "stats.",
    "a8cv.",
    "media",
    "track",
    ".com",
    ".net",
    ".xyz",
    "mail",
    "secu",
    "www.",
    ".de",
    ".io",
    ".jp",
    ".ru",
    "app",
    "cdn",
    "new",
    "pro",
    "web",
    ".b",
    ".c",
    ".d",
    ".e",
    ".f",
    ".h",
    ".k",
    ".l",
    ".m",
    ".n",
    ".p",
    ".s",
    ".t",
    ".v",
    ".w",
    "24",
    "a1",
    "a4",
    "a8",
    "ab",
    "ac",
    "ad",
    "af",
    "ag",
    "ai",
    "ak",
    "al",
    "am",
    "an",
    "ap",
    "ar",
    "as",
    "at",
    "au",
    "av",
    "aw",
    "ax",
    "ay",
    "az",
    "be",
    "bl",
    "bo",
    "br",
    "bu",
    "ca",
    "ce",
    "ch",
    "ci",
    "ck",
    "cl",
    "cr",
    "ct",
    "cu",
    "de",
    "di",
    "do",
    "dr",
    "ds",
    "du",
    "e-",
    "eb",
    "ec",
    "ed",
    "ef",
    "eg",
    "ek",
    "el",
    "em",
    "en",
    "ep",
    "er",
    "es",
    "et",
    "ev",
    "ew",
    "ex",
    "ey",
    "fe",
    "ff",
    "fi",
    "fl",
    "fo",
    "fr",
    "ge",
    "gh",
    "gl",
    "go",
    "gr",
    "gu",
    "he",
    "hi",
    "ho",
    "ia",
    "ib",
    "ic",
    "id",
    "if",
    "ig",
    "ik",
    "il",
    "im",
    "in",
    "ip",
    "ir",
    "is",
    "it",
    "iv",
    "ix",
    "iz",
    "jo",
    "ks",
    "la",
    "ld",
    "le",
    "li",
    "lo",
    "lu",
    "ly",
    "me",
    "mo",
    "mp",
    "my",
    "nd",
    "no",
    "nt",
    "ob",
    "od",
    "og",
    "ok",
    "ol",
    "om",
    "on",
    "op",
    "or",
    "os",
    "ot",
    "ou",
    "ow",
    "ph",
    "pl",
    "po",
    "pr",
    "qu",
    "re",
    "ri",
    "ro",
    "ru",
    "s-",
    "sc",
    "se",
    "sh",
    "si",
    "sk",
    "sn",
    "so",
    "sp",
    "ss",
    "st",
    "su",
    "sw",
    "sy",
    "t-",
    "ta",
    "te",
    "th",
    "ti",
    "tn",
    "to",
    "tr",
    "ts",
    "tu",
    "ty",
    "ub",
    "ul",
    "um",
    "un",
    "up",
    "ur",
    "us",
    "ut",
    "ve",
    "vi",
    "we",
    "-",
    ".",
    "0",
    "1",
    "2",
    "3",
    "4",
    "5",
    "6",
    "7",
    "8",
    "9",
    "a",
    "b",
    "c",
    "d",
    "e",
    "f",
    "g",
    "h",
    "i",
    "j",
    "k",
    "l",
    "m",
    "n",
    "o",
    "p",
    "q",
    "r",
    "s",
    "t",
    "u",
    "v",
    "w",
    "x",
    "y",
    "z"
  ];

  // node_modules/@cliqz/adblocker/dist/es6/src/codebooks/network-redirect.js
  var network_redirect_default = [
    "google-analytics.com/analytics.js",
    "googlesyndication_adsbygoogle.js",
    "googletagmanager.com/gtm.js",
    "googletagservices_gpt.js",
    "googletagmanager_gtm.js",
    "fuckadblock.js-3.2.0",
    "amazon_apstag.js",
    "google-analytics",
    "fingerprint2.js",
    "-transparent.",
    "google-ima.js",
    "prebid-ads.js",
    "nobab2.js:10",
    "noopmp3-0.1s",
    "noop-1s.mp4",
    "hd-main.js",
    "noopmp4-1s",
    "32x32.png",
    "noop.html",
    "noopframe",
    "noop.txt",
    "nooptext",
    "1x1.gif",
    "2x2.png",
    "noop.js",
    "noopjs",
    ".com/",
    ".js:5",
    "noop",
    "s.js",
    ":10",
    "_a",
    ":5",
    "am",
    "ic",
    "in",
    "le",
    "mp",
    "re",
    "s.",
    "st",
    "_",
    "-",
    ":",
    ".",
    "/",
    "0",
    "1",
    "2",
    "3",
    "4",
    "5",
    "a",
    "b",
    "c",
    "d",
    "e",
    "f",
    "g",
    "h",
    "i",
    "j",
    "k",
    "l",
    "m",
    "n",
    "o",
    "p",
    "r",
    "s",
    "t",
    "u",
    "v",
    "w",
    "x",
    "y",
    "z"
  ];

  // node_modules/@cliqz/adblocker/dist/es6/src/codebooks/raw-network.js
  var raw_network_default = [
    "/pagead/js/adsbygoogle.j",
    "imasdk.googleapis.com/js",
    "redirect-rule=google-ima",
    ",redirect-rule=1x1.gif",
    "*$script,3p,denyallow=",
    "/wp-content/plugins/",
    "/wp-content/uploads/",
    ",redirect-rule=noop",
    "/sdkloader/ima3.js",
    ".com^$third-party",
    "googlesyndication",
    ".cloudfront.net^",
    "$script,domain=",
    ",redirect=noop",
    "xmlhttprequest",
    ".actonservice",
    "/^https?:\\/\\/",
    "^$third-party",
    "||smetrics.",
    "third-party",
    "marketing.",
    "$subdocum",
    "analytics",
    "cloudfla",
    "metrics.",
    "tracking",
    "$script",
    "domain=",
    ".co.uk",
    "$ghide",
    "a8clk.",
    "cookie",
    "google",
    "script",
    ".com^",
    ".top^",
    ".xyz^",
    "$xhr,",
    "a8cv.",
    "image",
    "media",
    "track",
    "video",
    ".au^",
    ".com",
    ".fr^",
    ".gif",
    ".jp^",
    ".net",
    "/js/",
    "$doc",
    "html",
    "ight",
    "stat",
    "www.",
    ",1p",
    ".de",
    ".io",
    ".jp",
    ".js",
    "$3p",
    "cdn",
    "new",
    "web",
    ".b",
    ".c",
    ".m",
    ".n",
    ".p",
    ".s",
    "@@",
    "*/",
    "/*",
    "/b",
    "/p",
    "||",
    "a-",
    "ab",
    "ac",
    "ad",
    "af",
    "ag",
    "ak",
    "al",
    "am",
    "an",
    "ap",
    "ar",
    "as",
    "at",
    "au",
    "av",
    "aw",
    "ax",
    "ay",
    "az",
    "be",
    "bo",
    "br",
    "ca",
    "ce",
    "ch",
    "ck",
    "cl",
    "ct",
    "de",
    "di",
    "do",
    "e-",
    "e^",
    "eb",
    "ec",
    "ed",
    "el",
    "em",
    "en",
    "ep",
    "er",
    "es",
    "et",
    "ev",
    "ex",
    "ff",
    "fi",
    "fo",
    "fr",
    "g^",
    "ge",
    "go",
    "gr",
    "he",
    "ho",
    "hp",
    "ib",
    "ic",
    "id",
    "ie",
    "if",
    "ig",
    "ik",
    "il",
    "im",
    "in",
    "ip",
    "ir",
    "is",
    "it",
    "iv",
    "ix",
    "iz",
    "js",
    "le",
    "li",
    "lo",
    "lu",
    "ly",
    "ma",
    "me",
    "mo",
    "mp",
    "my",
    "no",
    "ob",
    "od",
    "ok",
    "ol",
    "om",
    "on",
    "op",
    "or",
    "ot",
    "pl",
    "po",
    "pr",
    "qu",
    "re",
    "ro",
    "ru",
    "s-",
    "s/",
    "sc",
    "se",
    "sh",
    "so",
    "sp",
    "ss",
    "st",
    "th",
    "ti",
    "to",
    "tr",
    "ts",
    "tv",
    "ub",
    "ud",
    "ul",
    "um",
    "un",
    "up",
    "ur",
    "us",
    "ut",
    "ve",
    "we",
    "yo",
    "_",
    "-",
    ",",
    "?",
    ".",
    "*",
    "/",
    "\\",
    "^",
    "=",
    "|",
    "~",
    "$",
    "0",
    "1",
    "2",
    "3",
    "4",
    "5",
    "6",
    "7",
    "8",
    "9",
    "a",
    "b",
    "c",
    "d",
    "e",
    "f",
    "g",
    "h",
    "i",
    "j",
    "k",
    "l",
    "m",
    "n",
    "o",
    "p",
    "q",
    "r",
    "s",
    "t",
    "u",
    "v",
    "w",
    "x",
    "y",
    "z"
  ];

  // node_modules/@cliqz/adblocker/dist/es6/src/codebooks/raw-cosmetic.js
  var raw_cosmetic_default = [
    " default !important; -",
    "-webkit-touch-callout:",
    "+js(nosiif, visibility",
    "+js(set, blurred, fals",
    "app_vars.force_disable",
    "user-select: text !imp",
    "+js(acs, document.",
    "+js(rmnt, script, ",
    "addEventListener, ",
    "^script:has-text(",
    "+js(no-fetch-if, ",
    '[href^="https://',
    "+js(trusted-set-",
    '[href^="http://',
    "+js(no-xhr-if, ",
    "createElement, ",
    "+js(nano-sib",
    "+js(nostif, ",
    'div[class^="',
    "notification",
    " !important",
    ", noopFunc)",
    "+js(nowoif)",
    "contextmenu",
    ":has-text(",
    "+js(aeld, ",
    "+js(aopr, ",
    "+js(aopw, ",
    '="https://',
    "background",
    ".*,xhamst",
    ".blogspot",
    "+js(acs, ",
    "+js(set, ",
    "container",
    'div[id^="',
    "div[style",
    ",amazon.",
    ",google.",
    ":style(",
    '.com/"]',
    "consent",
    "content",
    "message",
    "ortant;",
    "privacy",
    "-wrapp",
    ".co.uk",
    ".com##",
    "[class",
    "[data-",
    "accept",
    "banner",
    "bottom",
    "cookie",
    "Cookie",
    "google",
    "notice",
    "policy",
    "widget",
    ":has(",
    ".com,",
    "block",
    "movie",
    "popup",
    "video",
    "width",
    ",img",
    ".net",
    ".nl,",
    ".pl,",
    ".xyz",
    "#@#.",
    "+js(",
    "com.",
    "foot",
    "gdpr",
    "html",
    "ight",
    "news",
    "tube",
    "www.",
    " > ",
    ".*,",
    ".de",
    ".tv",
    "age",
    "ent",
    "top",
    "web",
    "xxx",
    "__",
    ", ",
    ": ",
    ".*",
    ".p",
    '"]',
    "##",
    '="',
    "00",
    "ab",
    "ad",
    "Ad",
    "al",
    "am",
    "an",
    "ap",
    "ar",
    "at",
    "au",
    "bi",
    "bo",
    "ca",
    "ce",
    "ch",
    "ck",
    "co",
    "ct",
    "d-",
    "da",
    "de",
    "di",
    "do",
    "ed",
    "el",
    "en",
    "er",
    "es",
    "et",
    "fi",
    "fo",
    "g-",
    "ga",
    "ge",
    "go",
    "he",
    "ho",
    "ic",
    "id",
    "il",
    "im",
    "in",
    "is",
    "it",
    "la",
    "le",
    "li",
    "lo",
    "ma",
    "me",
    "mo",
    "mp",
    "na",
    "ne",
    "no",
    "ol",
    "on",
    "op",
    "or",
    "ov",
    "po",
    "pp",
    "ra",
    "re",
    "ro",
    "ru",
    "s_",
    "s-",
    "sc",
    "se",
    "sh",
    "si",
    "sk",
    "so",
    "sp",
    "st",
    "t-",
    "ta",
    "te",
    "ti",
    "to",
    "ub",
    "ul",
    "um",
    "un",
    "up",
    "ur",
    "us",
    "ut",
    "ve",
    "vi",
    " ",
    "_",
    "-",
    ",",
    ";",
    ":",
    ".",
    ")",
    "[",
    "*",
    "/",
    "#",
    "%",
    "0",
    "1",
    "2",
    "3",
    "4",
    "5",
    "6",
    "7",
    "8",
    "9",
    "a",
    "b",
    "B",
    "c",
    "C",
    "d",
    "D",
    "e",
    "E",
    "f",
    "F",
    "g",
    "h",
    "i",
    "I",
    "j",
    "k",
    "l",
    "L",
    "m",
    "M",
    "n",
    "N",
    "o",
    "p",
    "P",
    "q",
    "r",
    "R",
    "s",
    "S",
    "t",
    "T",
    "u",
    "v",
    "w",
    "W",
    "x",
    "y",
    "z"
  ];

  // node_modules/@cliqz/adblocker/dist/es6/src/compression.js
  var Compression = class {
    constructor() {
      this.cosmeticSelector = new Smaz(cosmetic_selector_default);
      this.networkCSP = new Smaz(network_csp_default);
      this.networkRedirect = new Smaz(network_redirect_default);
      this.networkHostname = new Smaz(network_hostname_default);
      this.networkFilter = new Smaz(network_filter_default);
      this.networkRaw = new Smaz(raw_network_default);
      this.cosmeticRaw = new Smaz(raw_cosmetic_default);
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/crc32.js
  var T = (() => {
    let c = 0;
    const table = new Int32Array(256);
    for (let n = 0; n !== 256; n += 1) {
      c = n;
      c = c & 1 ? -306674912 ^ c >>> 1 : c >>> 1;
      c = c & 1 ? -306674912 ^ c >>> 1 : c >>> 1;
      c = c & 1 ? -306674912 ^ c >>> 1 : c >>> 1;
      c = c & 1 ? -306674912 ^ c >>> 1 : c >>> 1;
      c = c & 1 ? -306674912 ^ c >>> 1 : c >>> 1;
      c = c & 1 ? -306674912 ^ c >>> 1 : c >>> 1;
      c = c & 1 ? -306674912 ^ c >>> 1 : c >>> 1;
      c = c & 1 ? -306674912 ^ c >>> 1 : c >>> 1;
      table[n] = c;
    }
    return table;
  })();
  function crc32(buf, start, end) {
    let C = 0 ^ -1;
    const L = end - 7;
    let i = start;
    while (i < L) {
      C = C >>> 8 ^ T[(C ^ buf[i++]) & 255];
      C = C >>> 8 ^ T[(C ^ buf[i++]) & 255];
      C = C >>> 8 ^ T[(C ^ buf[i++]) & 255];
      C = C >>> 8 ^ T[(C ^ buf[i++]) & 255];
      C = C >>> 8 ^ T[(C ^ buf[i++]) & 255];
      C = C >>> 8 ^ T[(C ^ buf[i++]) & 255];
      C = C >>> 8 ^ T[(C ^ buf[i++]) & 255];
      C = C >>> 8 ^ T[(C ^ buf[i++]) & 255];
    }
    while (i < L + 7) {
      C = C >>> 8 ^ T[(C ^ buf[i++]) & 255];
    }
    return (C ^ -1) >>> 0;
  }

  // node_modules/@cliqz/adblocker/dist/es6/src/punycode.js
  var maxInt = 2147483647;
  var base = 36;
  var tMin = 1;
  var tMax = 26;
  var skew = 38;
  var damp = 700;
  var initialBias = 72;
  var initialN = 128;
  var delimiter = "-";
  var regexNonASCII = /[^\0-\x7E]/;
  var regexSeparators = /[\x2E\u3002\uFF0E\uFF61]/g;
  var errors = {
    "invalid-input": "Invalid input",
    "not-basic": "Illegal input >= 0x80 (not a basic code point)",
    "overflow": "Overflow: input needs wider integers to process"
  };
  var baseMinusTMin = base - tMin;
  function error(type) {
    throw new RangeError(errors[type]);
  }
  function ucs2decode(str) {
    const output = [];
    let counter = 0;
    const length = str.length;
    while (counter < length) {
      const value = str.charCodeAt(counter++);
      if (value >= 55296 && value <= 56319 && counter < length) {
        const extra = str.charCodeAt(counter++);
        if ((extra & 64512) === 56320) {
          output.push(((value & 1023) << 10) + (extra & 1023) + 65536);
        } else {
          output.push(value);
          counter--;
        }
      } else {
        output.push(value);
      }
    }
    return output;
  }
  function basicToDigit(codePoint) {
    if (codePoint - 48 < 10) {
      return codePoint - 22;
    }
    if (codePoint - 65 < 26) {
      return codePoint - 65;
    }
    if (codePoint - 97 < 26) {
      return codePoint - 97;
    }
    return base;
  }
  function digitToBasic(digit, flag) {
    return digit + 22 + 75 * (digit < 26 ? 1 : 0) - ((flag !== 0 ? 1 : 0) << 5);
  }
  function adapt(delta, numPoints, firstTime) {
    let k = 0;
    delta = firstTime ? Math.floor(delta / damp) : delta >> 1;
    delta += Math.floor(delta / numPoints);
    for (
      ;
      /* no initialization */
      delta > baseMinusTMin * tMax >> 1;
      k += base
    ) {
      delta = Math.floor(delta / baseMinusTMin);
    }
    return Math.floor(k + (baseMinusTMin + 1) * delta / (delta + skew));
  }
  function decode(input) {
    const output = [];
    const inputLength = input.length;
    let i = 0;
    let n = initialN;
    let bias = initialBias;
    let basic = input.lastIndexOf(delimiter);
    if (basic < 0) {
      basic = 0;
    }
    for (let j = 0; j < basic; ++j) {
      if (input.charCodeAt(j) >= 128) {
        error("not-basic");
      }
      output.push(input.charCodeAt(j));
    }
    for (let index = basic > 0 ? basic + 1 : 0; index < inputLength; ) {
      const oldi = i;
      for (let w = 1, k = base; ; k += base) {
        if (index >= inputLength) {
          error("invalid-input");
        }
        const digit = basicToDigit(input.charCodeAt(index++));
        if (digit >= base || digit > Math.floor((maxInt - i) / w)) {
          error("overflow");
        }
        i += digit * w;
        const t = k <= bias ? tMin : k >= bias + tMax ? tMax : k - bias;
        if (digit < t) {
          break;
        }
        const baseMinusT = base - t;
        if (w > Math.floor(maxInt / baseMinusT)) {
          error("overflow");
        }
        w *= baseMinusT;
      }
      const out = output.length + 1;
      bias = adapt(i - oldi, out, oldi === 0);
      if (Math.floor(i / out) > maxInt - n) {
        error("overflow");
      }
      n += Math.floor(i / out);
      i %= out;
      output.splice(i++, 0, n);
    }
    return String.fromCodePoint.apply(null, output);
  }
  function encode(str) {
    const output = [];
    const input = ucs2decode(str);
    const inputLength = input.length;
    let n = initialN;
    let delta = 0;
    let bias = initialBias;
    for (let i = 0; i < input.length; i += 1) {
      const currentValue = input[i];
      if (currentValue < 128) {
        output.push(String.fromCharCode(currentValue));
      }
    }
    const basicLength = output.length;
    let handledCPCount = basicLength;
    if (basicLength) {
      output.push(delimiter);
    }
    while (handledCPCount < inputLength) {
      let m = maxInt;
      for (let i = 0; i < input.length; i += 1) {
        const currentValue = input[i];
        if (currentValue >= n && currentValue < m) {
          m = currentValue;
        }
      }
      const handledCPCountPlusOne = handledCPCount + 1;
      if (m - n > Math.floor((maxInt - delta) / handledCPCountPlusOne)) {
        error("overflow");
      }
      delta += (m - n) * handledCPCountPlusOne;
      n = m;
      for (let i = 0; i < input.length; i += 1) {
        const currentValue = input[i];
        if (currentValue < n && ++delta > maxInt) {
          error("overflow");
        }
        if (currentValue === n) {
          let q = delta;
          for (let k = base; ; k += base) {
            const t = k <= bias ? tMin : k >= bias + tMax ? tMax : k - bias;
            if (q < t) {
              break;
            }
            const qMinusT = q - t;
            const baseMinusT = base - t;
            output.push(String.fromCharCode(digitToBasic(t + qMinusT % baseMinusT, 0)));
            q = Math.floor(qMinusT / baseMinusT);
          }
          output.push(String.fromCharCode(digitToBasic(q, 0)));
          bias = adapt(delta, handledCPCountPlusOne, handledCPCount === basicLength);
          delta = 0;
          ++handledCPCount;
        }
      }
      ++delta;
      ++n;
    }
    return output.join("");
  }
  function toASCII(input) {
    const labels = input.replace(regexSeparators, ".").split(".");
    const encoded = [];
    for (let i = 0; i < labels.length; i += 1) {
      encoded.push(regexNonASCII.test(labels[i]) ? "xn--" + encode(labels[i]) : labels[i]);
    }
    return encoded.join(".");
  }

  // node_modules/@cliqz/adblocker/dist/es6/src/data-view.js
  var EMPTY_UINT8_ARRAY2 = new Uint8Array(0);
  var EMPTY_UINT32_ARRAY = new Uint32Array(0);
  var LITTLE_ENDIAN = new Int8Array(new Int16Array([1]).buffer)[0] === 1;
  var getCompressionSingleton = () => {
    const COMPRESSION = new Compression();
    getCompressionSingleton = () => COMPRESSION;
    return COMPRESSION;
  };
  function align4(pos) {
    return pos + 3 & ~3;
  }
  function sizeOfByte() {
    return 1;
  }
  function sizeOfBool() {
    return 1;
  }
  function sizeOfLength(length) {
    return length <= 127 ? 1 : 5;
  }
  function sizeOfBytes(array, align) {
    return sizeOfBytesWithLength(array.length, align);
  }
  function sizeOfBytesWithLength(length, align) {
    return (align ? 3 : 0) + length + sizeOfLength(length);
  }
  function sizeOfASCII(str) {
    return str.length + sizeOfLength(str.length);
  }
  function sizeOfUTF8(str) {
    const encodedLength = encode(str).length;
    return encodedLength + sizeOfLength(encodedLength);
  }
  function sizeOfUint32Array(array) {
    return array.byteLength + sizeOfLength(array.length);
  }
  function sizeOfNetworkRedirect(str, compression) {
    return compression === true ? sizeOfBytesWithLength(getCompressionSingleton().networkRedirect.getCompressedSize(str), false) : sizeOfASCII(str);
  }
  function sizeOfNetworkHostname(str, compression) {
    return compression === true ? sizeOfBytesWithLength(getCompressionSingleton().networkHostname.getCompressedSize(str), false) : sizeOfASCII(str);
  }
  function sizeOfNetworkCSP(str, compression) {
    return compression === true ? sizeOfBytesWithLength(getCompressionSingleton().networkCSP.getCompressedSize(str), false) : sizeOfASCII(str);
  }
  function sizeOfNetworkFilter(str, compression) {
    return compression === true ? sizeOfBytesWithLength(getCompressionSingleton().networkFilter.getCompressedSize(str), false) : sizeOfASCII(str);
  }
  function sizeOfCosmeticSelector(str, compression) {
    return compression === true ? sizeOfBytesWithLength(getCompressionSingleton().cosmeticSelector.getCompressedSize(str), false) : sizeOfASCII(str);
  }
  function sizeOfRawNetwork(str, compression) {
    return compression === true ? sizeOfBytesWithLength(getCompressionSingleton().networkRaw.getCompressedSize(encode(str)), false) : sizeOfUTF8(str);
  }
  function sizeOfRawCosmetic(str, compression) {
    return compression === true ? sizeOfBytesWithLength(getCompressionSingleton().cosmeticRaw.getCompressedSize(encode(str)), false) : sizeOfUTF8(str);
  }
  var StaticDataView = class _StaticDataView {
    /**
     * Create an empty (i.e.: size = 0) StaticDataView.
     */
    static empty(options) {
      return _StaticDataView.fromUint8Array(EMPTY_UINT8_ARRAY2, options);
    }
    /**
     * Instantiate a StaticDataView instance from `array` of type Uint8Array.
     */
    static fromUint8Array(array, options) {
      return new _StaticDataView(array, options);
    }
    /**
     * Instantiate a StaticDataView with given `capacity` number of bytes.
     */
    static allocate(capacity, options) {
      return new _StaticDataView(new Uint8Array(capacity), options);
    }
    constructor(buffer, { enableCompression }) {
      if (LITTLE_ENDIAN === false) {
        throw new Error("Adblocker currently does not support Big-endian systems");
      }
      if (enableCompression === true) {
        this.enableCompression();
      }
      this.buffer = buffer;
      this.pos = 0;
    }
    enableCompression() {
      this.compression = getCompressionSingleton();
    }
    checksum() {
      return crc32(this.buffer, 0, this.pos);
    }
    dataAvailable() {
      return this.pos < this.buffer.byteLength;
    }
    setPos(pos) {
      this.pos = pos;
    }
    getPos() {
      return this.pos;
    }
    seekZero() {
      this.pos = 0;
    }
    slice() {
      this.checkSize();
      return this.buffer.slice(0, this.pos);
    }
    subarray() {
      if (this.pos === this.buffer.byteLength) {
        return this.buffer;
      }
      this.checkSize();
      return this.buffer.subarray(0, this.pos);
    }
    /**
     * Make sure that `this.pos` is aligned on a multiple of 4.
     */
    align4() {
      this.pos = align4(this.pos);
    }
    set(buffer) {
      this.buffer = new Uint8Array(buffer);
      this.seekZero();
    }
    pushBool(bool) {
      this.pushByte(Number(bool));
    }
    getBool() {
      return Boolean(this.getByte());
    }
    setByte(pos, byte) {
      this.buffer[pos] = byte;
    }
    pushByte(octet) {
      this.pushUint8(octet);
    }
    getByte() {
      return this.getUint8();
    }
    pushBytes(bytes, align = false) {
      this.pushLength(bytes.length);
      if (align === true) {
        this.align4();
      }
      this.buffer.set(bytes, this.pos);
      this.pos += bytes.byteLength;
    }
    getBytes(align = false) {
      const numberOfBytes = this.getLength();
      if (align === true) {
        this.align4();
      }
      const bytes = this.buffer.subarray(this.pos, this.pos + numberOfBytes);
      this.pos += numberOfBytes;
      return bytes;
    }
    /**
     * Allows row access to the internal buffer through a Uint32Array acting like
     * a view. This is used for super fast writing/reading of large chunks of
     * Uint32 numbers in the byte array.
     */
    getUint32ArrayView(desiredSize) {
      this.align4();
      if (desiredSize === 0) {
        return EMPTY_UINT32_ARRAY;
      }
      const view = new Uint32Array(this.buffer.buffer, this.pos + this.buffer.byteOffset, desiredSize);
      this.pos += desiredSize * 4;
      return view;
    }
    pushUint8(uint8) {
      this.buffer[this.pos++] = uint8;
    }
    getUint8() {
      return this.buffer[this.pos++];
    }
    pushUint16(uint16) {
      this.buffer[this.pos++] = uint16 >>> 8;
      this.buffer[this.pos++] = uint16;
    }
    getUint16() {
      return (this.buffer[this.pos++] << 8 | this.buffer[this.pos++]) >>> 0;
    }
    pushUint32(uint32) {
      this.buffer[this.pos++] = uint32 >>> 24;
      this.buffer[this.pos++] = uint32 >>> 16;
      this.buffer[this.pos++] = uint32 >>> 8;
      this.buffer[this.pos++] = uint32;
    }
    getUint32() {
      return (this.buffer[this.pos++] << 24 >>> 0) + (this.buffer[this.pos++] << 16 | this.buffer[this.pos++] << 8 | this.buffer[this.pos++]) >>> 0;
    }
    pushUint32Array(arr) {
      this.pushLength(arr.length);
      for (const n of arr) {
        this.pushUint32(n);
      }
    }
    getUint32Array() {
      const length = this.getLength();
      const arr = new Uint32Array(length);
      for (let i = 0; i < length; i += 1) {
        arr[i] = this.getUint32();
      }
      return arr;
    }
    pushUTF8(raw) {
      const str = encode(raw);
      this.pushLength(str.length);
      for (let i = 0; i < str.length; i += 1) {
        this.buffer[this.pos++] = str.charCodeAt(i);
      }
    }
    getUTF8() {
      const byteLength = this.getLength();
      this.pos += byteLength;
      return decode(String.fromCharCode.apply(
        null,
        // @ts-ignore
        this.buffer.subarray(this.pos - byteLength, this.pos)
      ));
    }
    pushASCII(str) {
      this.pushLength(str.length);
      for (let i = 0; i < str.length; i += 1) {
        this.buffer[this.pos++] = str.charCodeAt(i);
      }
    }
    getASCII() {
      const byteLength = this.getLength();
      this.pos += byteLength;
      return String.fromCharCode.apply(null, this.buffer.subarray(this.pos - byteLength, this.pos));
    }
    pushNetworkRedirect(str) {
      if (this.compression !== void 0) {
        this.pushBytes(this.compression.networkRedirect.compress(str));
      } else {
        this.pushASCII(str);
      }
    }
    getNetworkRedirect() {
      if (this.compression !== void 0) {
        return this.compression.networkRedirect.decompress(this.getBytes());
      }
      return this.getASCII();
    }
    pushNetworkHostname(str) {
      if (this.compression !== void 0) {
        this.pushBytes(this.compression.networkHostname.compress(str));
      } else {
        this.pushASCII(str);
      }
    }
    getNetworkHostname() {
      if (this.compression !== void 0) {
        return this.compression.networkHostname.decompress(this.getBytes());
      }
      return this.getASCII();
    }
    pushNetworkCSP(str) {
      if (this.compression !== void 0) {
        this.pushBytes(this.compression.networkCSP.compress(str));
      } else {
        this.pushASCII(str);
      }
    }
    getNetworkCSP() {
      if (this.compression !== void 0) {
        return this.compression.networkCSP.decompress(this.getBytes());
      }
      return this.getASCII();
    }
    pushNetworkFilter(str) {
      if (this.compression !== void 0) {
        this.pushBytes(this.compression.networkFilter.compress(str));
      } else {
        this.pushASCII(str);
      }
    }
    getNetworkFilter() {
      if (this.compression !== void 0) {
        return this.compression.networkFilter.decompress(this.getBytes());
      }
      return this.getASCII();
    }
    pushCosmeticSelector(str) {
      if (this.compression !== void 0) {
        this.pushBytes(this.compression.cosmeticSelector.compress(str));
      } else {
        this.pushASCII(str);
      }
    }
    getCosmeticSelector() {
      if (this.compression !== void 0) {
        return this.compression.cosmeticSelector.decompress(this.getBytes());
      }
      return this.getASCII();
    }
    pushRawCosmetic(str) {
      if (this.compression !== void 0) {
        this.pushBytes(this.compression.cosmeticRaw.compress(encode(str)));
      } else {
        this.pushUTF8(str);
      }
    }
    getRawCosmetic() {
      if (this.compression !== void 0) {
        return decode(this.compression.cosmeticRaw.decompress(this.getBytes()));
      }
      return this.getUTF8();
    }
    pushRawNetwork(str) {
      if (this.compression !== void 0) {
        this.pushBytes(this.compression.networkRaw.compress(encode(str)));
      } else {
        this.pushUTF8(str);
      }
    }
    getRawNetwork() {
      if (this.compression !== void 0) {
        return decode(this.compression.networkRaw.decompress(this.getBytes()));
      }
      return this.getUTF8();
    }
    checkSize() {
      if (this.pos !== 0 && this.pos > this.buffer.byteLength) {
        throw new Error(`StaticDataView too small: ${this.buffer.byteLength}, but required ${this.pos} bytes`);
      }
    }
    // Serialiez `length` with variable encoding to save space
    pushLength(length) {
      if (length <= 127) {
        this.pushUint8(length);
      } else {
        this.pushUint8(128);
        this.pushUint32(length);
      }
    }
    getLength() {
      const lengthShort = this.getUint8();
      return lengthShort === 128 ? this.getUint32() : lengthShort;
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/config.js
  var Config = class _Config {
    static deserialize(buffer) {
      return new _Config({
        debug: buffer.getBool(),
        enableCompression: buffer.getBool(),
        enableHtmlFiltering: buffer.getBool(),
        enableInMemoryCache: buffer.getBool(),
        enableMutationObserver: buffer.getBool(),
        enableOptimizations: buffer.getBool(),
        enablePushInjectionsOnNavigationEvents: buffer.getBool(),
        guessRequestTypeFromUrl: buffer.getBool(),
        integrityCheck: buffer.getBool(),
        loadCSPFilters: buffer.getBool(),
        loadCosmeticFilters: buffer.getBool(),
        loadExceptionFilters: buffer.getBool(),
        loadExtendedSelectors: buffer.getBool(),
        loadGenericCosmeticsFilters: buffer.getBool(),
        loadNetworkFilters: buffer.getBool()
      });
    }
    constructor({ debug = false, enableCompression = false, enableHtmlFiltering = false, enableInMemoryCache = true, enableMutationObserver = true, enableOptimizations = true, enablePushInjectionsOnNavigationEvents = true, guessRequestTypeFromUrl = false, integrityCheck = true, loadCSPFilters = true, loadCosmeticFilters = true, loadExceptionFilters = true, loadExtendedSelectors = false, loadGenericCosmeticsFilters = true, loadNetworkFilters = true } = {}) {
      this.debug = debug;
      this.enableCompression = enableCompression;
      this.enableHtmlFiltering = enableHtmlFiltering;
      this.enableInMemoryCache = enableInMemoryCache;
      this.enableMutationObserver = enableMutationObserver;
      this.enableOptimizations = enableOptimizations;
      this.enablePushInjectionsOnNavigationEvents = enablePushInjectionsOnNavigationEvents;
      this.guessRequestTypeFromUrl = guessRequestTypeFromUrl;
      this.integrityCheck = integrityCheck;
      this.loadCSPFilters = loadCSPFilters;
      this.loadCosmeticFilters = loadCosmeticFilters;
      this.loadExceptionFilters = loadExceptionFilters;
      this.loadExtendedSelectors = loadExtendedSelectors;
      this.loadGenericCosmeticsFilters = loadGenericCosmeticsFilters;
      this.loadNetworkFilters = loadNetworkFilters;
    }
    getSerializedSize() {
      return 15 * sizeOfBool();
    }
    serialize(buffer) {
      buffer.pushBool(this.debug);
      buffer.pushBool(this.enableCompression);
      buffer.pushBool(this.enableHtmlFiltering);
      buffer.pushBool(this.enableInMemoryCache);
      buffer.pushBool(this.enableMutationObserver);
      buffer.pushBool(this.enableOptimizations);
      buffer.pushBool(this.enablePushInjectionsOnNavigationEvents);
      buffer.pushBool(this.guessRequestTypeFromUrl);
      buffer.pushBool(this.integrityCheck);
      buffer.pushBool(this.loadCSPFilters);
      buffer.pushBool(this.loadCosmeticFilters);
      buffer.pushBool(this.loadExceptionFilters);
      buffer.pushBool(this.loadExtendedSelectors);
      buffer.pushBool(this.loadGenericCosmeticsFilters);
      buffer.pushBool(this.loadNetworkFilters);
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/queue-microtask.js
  var promise;
  var queueMicrotask = typeof window !== "undefined" && typeof window.queueMicrotask === "function" ? (cb) => window.queueMicrotask(cb) : (
    // reuse resolved promise, and allocate it lazily
    (cb) => (promise || (promise = Promise.resolve())).then(cb).catch((err) => setTimeout(() => {
      throw err;
    }, 0))
  );

  // node_modules/@cliqz/adblocker/dist/es6/src/events.js
  function registerCallback(event, callback, listeners) {
    let listenersForEvent = listeners.get(event);
    if (listenersForEvent === void 0) {
      listenersForEvent = [];
      listeners.set(event, listenersForEvent);
    }
    listenersForEvent.push(callback);
  }
  function unregisterCallback(event, callback, listeners) {
    const listenersForEvent = listeners.get(event);
    if (listenersForEvent !== void 0) {
      const indexOfCallback = listenersForEvent.indexOf(callback);
      if (indexOfCallback !== -1) {
        listenersForEvent.splice(indexOfCallback, 1);
      }
    }
  }
  function triggerCallback(event, args, listeners) {
    if (listeners.size === 0) {
      return false;
    }
    const listenersForEvent = listeners.get(event);
    if (listenersForEvent !== void 0) {
      queueMicrotask(() => {
        for (const listener of listenersForEvent) {
          listener(...args);
        }
      });
      return true;
    }
    return false;
  }
  var EventEmitter = class {
    constructor() {
      this.onceListeners = /* @__PURE__ */ new Map();
      this.onListeners = /* @__PURE__ */ new Map();
    }
    /**
     * Register an event listener for `event`.
     */
    on(event, callback) {
      registerCallback(event, callback, this.onListeners);
    }
    /**
     * Register an event listener for `event`; but only listen to first instance
     * of this event. The listener is automatically deleted afterwards.
     */
    once(event, callback) {
      registerCallback(event, callback, this.onceListeners);
    }
    /**
     * Remove `callback` from list of listeners for `event`.
     */
    unsubscribe(event, callback) {
      unregisterCallback(event, callback, this.onListeners);
      unregisterCallback(event, callback, this.onceListeners);
    }
    /**
     * Emit an event. Call all registered listeners to this event.
     */
    emit(event, ...args) {
      triggerCallback(event, args, this.onListeners);
      if (triggerCallback(event, args, this.onceListeners) === true) {
        this.onceListeners.delete(event);
      }
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/fetch.js
  function fetchWithRetry(fetch2, url) {
    let retry = 3;
    const fetchWrapper = () => {
      return fetch2(url).catch((ex) => {
        if (retry > 0) {
          retry -= 1;
          return new Promise((resolve, reject) => {
            setTimeout(() => {
              fetchWrapper().then(resolve).catch(reject);
            }, 500);
          });
        }
        throw ex;
      });
    };
    return fetchWrapper();
  }
  function fetchResource(fetch2, url) {
    return fetchWithRetry(fetch2, url).then((response) => response.text());
  }
  var PREFIX = "https://raw.githubusercontent.com/cliqz-oss/adblocker/master/packages/adblocker/assets";
  var adsLists = [
    `${PREFIX}/easylist/easylist.txt`,
    `${PREFIX}/easylist/easylistgermany.txt`,
    `${PREFIX}/peter-lowe/serverlist.txt`,
    `${PREFIX}/ublock-origin/badware.txt`,
    `${PREFIX}/ublock-origin/filters-2020.txt`,
    `${PREFIX}/ublock-origin/filters-2021.txt`,
    `${PREFIX}/ublock-origin/filters-2022.txt`,
    `${PREFIX}/ublock-origin/filters-2023.txt`,
    `${PREFIX}/ublock-origin/filters-mobile.txt`,
    `${PREFIX}/ublock-origin/filters.txt`,
    `${PREFIX}/ublock-origin/quick-fixes.txt`,
    `${PREFIX}/ublock-origin/resource-abuse.txt`,
    `${PREFIX}/ublock-origin/unbreak.txt`
  ];
  var adsAndTrackingLists = [
    ...adsLists,
    `${PREFIX}/easylist/easyprivacy.txt`,
    `${PREFIX}/ublock-origin/privacy.txt`
  ];
  var fullLists = [
    ...adsAndTrackingLists,
    `${PREFIX}/easylist/easylist-cookie.txt`,
    `${PREFIX}/ublock-origin/annoyances.txt`,
    `${PREFIX}/ublock-origin/annoyances-others.txt`,
    `${PREFIX}/ublock-origin/annoyances-cookies.txt`
  ];
  function fetchLists(fetch2, urls) {
    return Promise.all(urls.map((url) => fetchResource(fetch2, url)));
  }
  function fetchResources(fetch2) {
    return fetchResource(fetch2, `${PREFIX}/ublock-origin/resources.txt`);
  }

  // node_modules/@cliqz/adblocker/dist/es6/src/filters/dsl.js
  var NetworkBuilder = class {
    constructor() {
      this.options = /* @__PURE__ */ new Set();
      this.prefix = void 0;
      this.infix = void 0;
      this.suffix = void 0;
      this.redirect = void 0;
    }
    blockRequestsWithType(t) {
      if (this.options.has(t)) {
        throw new Error(`Already blocking type ${t}`);
      }
      this.options.add(t);
      return this;
    }
    images() {
      return this.blockRequestsWithType("image");
    }
    scripts() {
      return this.blockRequestsWithType("script");
    }
    frames() {
      return this.blockRequestsWithType("frame");
    }
    fonts() {
      return this.blockRequestsWithType("font");
    }
    medias() {
      return this.blockRequestsWithType("media");
    }
    styles() {
      return this.blockRequestsWithType("css");
    }
    redirectTo(redirect) {
      if (this.redirect !== void 0) {
        throw new Error(`Already redirecting: ${this.redirect}`);
      }
      this.redirect = `redirect=${redirect}`;
      return this;
    }
    urlContains(infix) {
      if (this.infix !== void 0) {
        throw new Error(`Already matching pattern: ${this.infix}`);
      }
      this.infix = infix;
      return this;
    }
    urlStartsWith(prefix) {
      if (this.prefix !== void 0) {
        throw new Error(`Already matching prefix: ${this.prefix}`);
      }
      this.prefix = `|${prefix}`;
      return this;
    }
    urlEndsWith(suffix) {
      if (this.suffix !== void 0) {
        throw new Error(`Already matching suffix: ${this.suffix}`);
      }
      this.suffix = `${suffix}|`;
      return this;
    }
    withHostname(hostname) {
      if (this.prefix !== void 0) {
        throw new Error(`Cannot match hostname if filter already has prefix: ${this.prefix}`);
      }
      this.prefix = `||${hostname}^`;
      return this;
    }
    toString() {
      const parts = [];
      if (this.prefix !== void 0) {
        parts.push(this.prefix);
      }
      if (this.infix !== void 0) {
        parts.push(this.infix);
      }
      if (this.suffix !== void 0) {
        parts.push(this.suffix);
      }
      const options = ["important"];
      if (this.options.size !== 0) {
        for (const option of this.options) {
          options.push(option);
        }
      }
      if (this.redirect !== void 0) {
        options.push(this.redirect);
      }
      return `${parts.length === 0 ? "*" : parts.join("*")}$${options.join(",")}`;
    }
  };
  function block() {
    return new NetworkBuilder();
  }

  // node_modules/@cliqz/adblocker-extended-selectors/dist/es6/src/types.js
  function isAtoms(tokens) {
    return tokens.every((token) => typeof token !== "string");
  }
  function isAST(tokens) {
    return tokens.every((token) => token.type !== "comma" && token.type !== "combinator");
  }

  // node_modules/@cliqz/adblocker-extended-selectors/dist/es6/src/parse.js
  var RECURSIVE_PSEUDO_CLASSES = /* @__PURE__ */ new Set([
    "any",
    "dir",
    "has",
    "host-context",
    "if",
    "if-not",
    "is",
    "matches",
    "not",
    "where"
  ]);
  var TOKENS = {
    attribute: /\[\s*(?:(?<namespace>\*|[-\w]*)\|)?(?<name>[-\w\u{0080}-\u{FFFF}]+)\s*(?:(?<operator>\W?=)\s*(?<value>.+?)\s*(?<caseSensitive>[iIsS])?\s*)?\]/gu,
    id: /#(?<name>(?:[-\w\u{0080}-\u{FFFF}]|\\.)+)/gu,
    class: /\.(?<name>(?:[-\w\u{0080}-\u{FFFF}]|\\.)+)/gu,
    comma: /\s*,\s*/g,
    // must be before combinator
    combinator: /\s*[\s>+~]\s*/g,
    // this must be after attribute
    "pseudo-element": /::(?<name>[-\w\u{0080}-\u{FFFF}]+)(?:\((?:¶*)\))?/gu,
    // this must be before pseudo-class
    "pseudo-class": /:(?<name>[-\w\u{0080}-\u{FFFF}]+)(?:\((?<argument>¶*)\))?/gu,
    type: /(?:(?<namespace>\*|[-\w]*)\|)?(?<name>[-\w\u{0080}-\u{FFFF}]+)|\*/gu
    // this must be last
  };
  var TOKENS_WITH_PARENS = /* @__PURE__ */ new Set(["pseudo-class", "pseudo-element"]);
  var TOKENS_WITH_STRINGS = /* @__PURE__ */ new Set([...TOKENS_WITH_PARENS, "attribute"]);
  var TRIM_TOKENS = /* @__PURE__ */ new Set(["combinator", "comma"]);
  var TOKENS_FOR_RESTORE = Object.assign({}, TOKENS);
  TOKENS_FOR_RESTORE["pseudo-element"] = RegExp(TOKENS["pseudo-element"].source.replace("(?<argument>\xB6*)", "(?<argument>.*?)"), "gu");
  TOKENS_FOR_RESTORE["pseudo-class"] = RegExp(TOKENS["pseudo-class"].source.replace("(?<argument>\xB6*)", "(?<argument>.*)"), "gu");
  function splitOnMatch(pattern, str) {
    pattern.lastIndex = 0;
    const match = pattern.exec(str);
    if (match === null) {
      return void 0;
    }
    const from = match.index - 1;
    const content = match[0];
    const before = str.slice(0, from + 1);
    const after = str.slice(from + content.length + 1);
    return [before, [content, match.groups || {}], after];
  }
  var GRAMMAR = [
    // attribute
    (str) => {
      const match = splitOnMatch(TOKENS.attribute, str);
      if (match === void 0) {
        return void 0;
      }
      const [before, [content, { name, operator, value, namespace, caseSensitive }], after] = match;
      if (name === void 0) {
        return void 0;
      }
      return [
        before,
        {
          type: "attribute",
          content,
          length: content.length,
          namespace,
          caseSensitive,
          pos: [],
          name,
          operator,
          value
        },
        after
      ];
    },
    // #id
    (str) => {
      const match = splitOnMatch(TOKENS.id, str);
      if (match === void 0) {
        return void 0;
      }
      const [before, [content, { name }], after] = match;
      if (name === void 0) {
        return void 0;
      }
      return [
        before,
        {
          type: "id",
          content,
          length: content.length,
          pos: [],
          name
        },
        after
      ];
    },
    // .class
    (str) => {
      const match = splitOnMatch(TOKENS.class, str);
      if (match === void 0) {
        return void 0;
      }
      const [before, [content, { name }], after] = match;
      if (name === void 0) {
        return void 0;
      }
      return [
        before,
        {
          type: "class",
          content,
          length: content.length,
          pos: [],
          name
        },
        after
      ];
    },
    // comma ,
    (str) => {
      const match = splitOnMatch(TOKENS.comma, str);
      if (match === void 0) {
        return void 0;
      }
      const [before, [content], after] = match;
      return [
        before,
        {
          type: "comma",
          content,
          length: content.length,
          pos: []
        },
        after
      ];
    },
    // combinator
    (str) => {
      const match = splitOnMatch(TOKENS.combinator, str);
      if (match === void 0) {
        return void 0;
      }
      const [before, [content], after] = match;
      return [
        before,
        {
          type: "combinator",
          content,
          length: content.length,
          pos: []
        },
        after
      ];
    },
    // pseudo-element
    (str) => {
      const match = splitOnMatch(TOKENS["pseudo-element"], str);
      if (match === void 0) {
        return void 0;
      }
      const [before, [content, { name }], after] = match;
      if (name === void 0) {
        return void 0;
      }
      return [
        before,
        {
          type: "pseudo-element",
          content,
          length: content.length,
          pos: [],
          name
        },
        after
      ];
    },
    // pseudo-class
    (str) => {
      const match = splitOnMatch(TOKENS["pseudo-class"], str);
      if (match === void 0) {
        return void 0;
      }
      const [before, [content, { name, argument }], after] = match;
      if (name === void 0) {
        return void 0;
      }
      return [
        before,
        {
          type: "pseudo-class",
          content,
          length: content.length,
          pos: [],
          name,
          argument,
          subtree: void 0
        },
        after
      ];
    },
    // type
    (str) => {
      const match = splitOnMatch(TOKENS.type, str);
      if (match === void 0) {
        return void 0;
      }
      const [before, [content, { name, namespace }], after] = match;
      return [
        before,
        {
          type: "type",
          content,
          length: content.length,
          namespace,
          pos: [],
          name
        },
        after
      ];
    }
  ];
  function tokenizeBy(text) {
    if (!text) {
      return [];
    }
    const strarr = [text];
    for (const tokenizer of GRAMMAR) {
      for (let i = 0; i < strarr.length; i++) {
        const str = strarr[i];
        if (typeof str === "string") {
          const match = tokenizer(str);
          if (match !== void 0) {
            strarr.splice(i, 1, ...match.filter((a) => a.length !== 0));
          }
        }
      }
    }
    let offset = 0;
    for (const token of strarr) {
      if (typeof token !== "string") {
        token.pos = [offset, offset + token.length];
        if (TRIM_TOKENS.has(token.type)) {
          token.content = token.content.trim() || " ";
        }
      }
      offset += token.length;
    }
    if (isAtoms(strarr)) {
      return strarr;
    }
    return [];
  }
  function restoreNested(tokens, strings, regex, types) {
    for (const str of strings) {
      for (const token of tokens) {
        if (types.has(token.type) && token.pos[0] < str.start && str.start < token.pos[1]) {
          const content = token.content;
          token.content = token.content.replace(regex, str.str);
          if (token.content !== content) {
            TOKENS_FOR_RESTORE[token.type].lastIndex = 0;
            const match = TOKENS_FOR_RESTORE[token.type].exec(token.content);
            if (match !== null) {
              Object.assign(token, match.groups);
            }
          }
        }
      }
    }
  }
  function isEscaped(str, index) {
    let backslashes = 0;
    index -= 1;
    while (index >= 0 && str[index] === "\\") {
      backslashes += 1;
      index -= 1;
    }
    return backslashes % 2 !== 0;
  }
  function gobbleQuotes(text, quote, start) {
    let end = start + 1;
    while ((end = text.indexOf(quote, end)) !== -1 && isEscaped(text, end) === true) {
      end += 1;
    }
    if (end === -1) {
      return void 0;
    }
    return text.slice(start, end + 1);
  }
  function gobbleParens(text, start) {
    let stack = 0;
    for (let i = start; i < text.length; i++) {
      const char = text[i];
      if (char === "(") {
        stack += 1;
      } else if (char === ")") {
        if (stack > 0) {
          stack -= 1;
        } else {
          return void 0;
        }
      }
      if (stack === 0) {
        return text.slice(start, i + 1);
      }
    }
    return void 0;
  }
  function replace(selector, replacement, opening, gobble) {
    const strings = [];
    let offset = 0;
    while ((offset = selector.indexOf(opening, offset)) !== -1) {
      const str = gobble(selector, offset);
      if (str === void 0) {
        break;
      }
      strings.push({ str, start: offset });
      selector = `${selector.slice(0, offset + 1)}${replacement.repeat(str.length - 2)}${selector.slice(offset + str.length - 1)}`;
      offset += str.length;
    }
    return [strings, selector];
  }
  function tokenize(selector) {
    if (typeof selector !== "string") {
      return [];
    }
    selector = selector.trim();
    if (selector.length === 0) {
      return [];
    }
    const [doubleQuotes, selectorWithoutDoubleQuotes] = replace(selector, "\xA7", '"', (text, start) => gobbleQuotes(text, '"', start));
    const [singleQuotes, selectorWithoutQuotes] = replace(selectorWithoutDoubleQuotes, "\xA7", "'", (text, start) => gobbleQuotes(text, "'", start));
    const [parens, selectorWithoutParens] = replace(selectorWithoutQuotes, "\xB6", "(", gobbleParens);
    const tokens = tokenizeBy(selectorWithoutParens);
    restoreNested(tokens, parens, /\(¶*\)/, TOKENS_WITH_PARENS);
    restoreNested(tokens, doubleQuotes, /"§*"/, TOKENS_WITH_STRINGS);
    restoreNested(tokens, singleQuotes, /'§*'/, TOKENS_WITH_STRINGS);
    return tokens;
  }
  function nestTokens(tokens, { list = true } = {}) {
    if (list === true && tokens.some((t) => t.type === "comma")) {
      const selectors = [];
      const temp = [];
      for (let i = 0; i < tokens.length; i += 1) {
        const token = tokens[i];
        if (token.type === "comma") {
          if (temp.length === 0) {
            throw new Error("Incorrect comma at " + i);
          }
          const sub = nestTokens(temp, { list: false });
          if (sub !== void 0) {
            selectors.push(sub);
          }
          temp.length = 0;
        } else {
          temp.push(token);
        }
      }
      if (temp.length === 0) {
        throw new Error("Trailing comma");
      } else {
        const sub = nestTokens(temp, { list: false });
        if (sub !== void 0) {
          selectors.push(sub);
        }
      }
      return { type: "list", list: selectors };
    }
    for (let i = tokens.length - 1; i >= 0; i--) {
      const token = tokens[i];
      if (token.type === "combinator") {
        const left = nestTokens(tokens.slice(0, i));
        const right = nestTokens(tokens.slice(i + 1));
        if (right === void 0) {
          return void 0;
        }
        if (token.content !== " " && token.content !== "~" && token.content !== "+" && token.content !== ">") {
          return void 0;
        }
        return {
          type: "complex",
          combinator: token.content,
          left,
          right
        };
      }
    }
    if (tokens.length === 0) {
      return void 0;
    }
    if (isAST(tokens)) {
      if (tokens.length === 1) {
        return tokens[0];
      }
      return {
        type: "compound",
        compound: [...tokens]
        // clone to avoid pointers messing up the AST
      };
    }
    return void 0;
  }
  function walk(node, callback, o, parent) {
    if (node === void 0) {
      return;
    }
    if (node.type === "complex") {
      walk(node.left, callback, o, node);
      walk(node.right, callback, o, node);
    } else if (node.type === "compound") {
      for (const n of node.compound) {
        walk(n, callback, o, node);
      }
    } else if (node.type === "pseudo-class" && node.subtree !== void 0 && o !== void 0 && o.type === "pseudo-class" && o.subtree !== void 0) {
      walk(node.subtree, callback, o, node);
    }
    callback(node, parent);
  }
  function parse(selector, { recursive = true, list = true } = {}) {
    const tokens = tokenize(selector);
    if (tokens.length === 0) {
      return void 0;
    }
    const ast = nestTokens(tokens, { list });
    if (recursive === true) {
      walk(ast, (node) => {
        if (node.type === "pseudo-class" && node.argument && node.name !== void 0 && RECURSIVE_PSEUDO_CLASSES.has(node.name)) {
          node.subtree = parse(node.argument, { recursive: true, list: true });
        }
      });
    }
    return ast;
  }

  // node_modules/@cliqz/adblocker-extended-selectors/dist/es6/src/extended.js
  var EXTENDED_PSEUDO_CLASSES = /* @__PURE__ */ new Set([
    // '-abp-contains',
    // '-abp-has',
    // '-abp-properties',
    "has",
    "has-text",
    "if"
    // 'if-not',
    // 'matches-css',
    // 'matches-css-after',
    // 'matches-css-before',
    // 'min-text-length',
    // 'nth-ancestor',
    // 'upward',
    // 'watch-attr',
    // 'watch-attrs',
    // 'xpath',
  ]);
  var PSEUDO_CLASSES = /* @__PURE__ */ new Set([
    "active",
    "any",
    "any-link",
    "blank",
    "checked",
    "default",
    "defined",
    "dir",
    "disabled",
    "empty",
    "enabled",
    "first",
    "first-child",
    "first-of-type",
    "focus",
    "focus-visible",
    "focus-within",
    "fullscreen",
    "host",
    "host-context",
    "hover",
    "in-range",
    "indeterminate",
    "invalid",
    "is",
    "lang",
    "last-child",
    "last-of-type",
    "left",
    "link",
    "matches",
    // NOTE: by default we consider `:not(...)` to be a normal CSS selector since,
    // we are only interested in cases where the argument is an extended selector.
    // If that is the case, it will still be detected as such.
    "not",
    "nth-child",
    "nth-last-child",
    "nth-last-of-type",
    "nth-of-type",
    "only-child",
    "only-of-type",
    "optional",
    "out-of-range",
    "placeholder-shown",
    "read-only",
    "read-write",
    "required",
    "right",
    "root",
    "scope",
    "target",
    "valid",
    "visited",
    "where"
  ]);
  var PSEUDO_ELEMENTS = /* @__PURE__ */ new Set(["after", "before", "first-letter", "first-line"]);
  var SelectorType;
  (function(SelectorType2) {
    SelectorType2[SelectorType2["Normal"] = 0] = "Normal";
    SelectorType2[SelectorType2["Extended"] = 1] = "Extended";
    SelectorType2[SelectorType2["Invalid"] = 2] = "Invalid";
  })(SelectorType || (SelectorType = {}));
  function classifySelector(selector) {
    if (selector.indexOf(":") === -1) {
      return SelectorType.Normal;
    }
    const tokens = tokenize(selector);
    let foundSupportedExtendedSelector = false;
    for (const token of tokens) {
      if (token.type === "pseudo-class") {
        const { name } = token;
        if (EXTENDED_PSEUDO_CLASSES.has(name) === true) {
          foundSupportedExtendedSelector = true;
        } else if (PSEUDO_CLASSES.has(name) === false && PSEUDO_ELEMENTS.has(name) === false) {
          return SelectorType.Invalid;
        }
        if (foundSupportedExtendedSelector === false && token.argument !== void 0 && RECURSIVE_PSEUDO_CLASSES.has(name) === true) {
          const argumentType = classifySelector(token.argument);
          if (argumentType === SelectorType.Invalid) {
            return argumentType;
          } else if (argumentType === SelectorType.Extended) {
            foundSupportedExtendedSelector = true;
          }
        }
      }
    }
    if (foundSupportedExtendedSelector === true) {
      return SelectorType.Extended;
    }
    return SelectorType.Normal;
  }

  // node_modules/@remusao/guess-url-type/dist/es6/src/extensions/documents.js
  var EXTENSIONS = /* @__PURE__ */ new Set(["htm", "html", "xhtml"]);

  // node_modules/@remusao/guess-url-type/dist/es6/src/extensions/fonts.js
  var EXTENSIONS2 = /* @__PURE__ */ new Set([
    "eot",
    "otf",
    "sfnt",
    "ttf",
    "woff",
    "woff2"
  ]);

  // node_modules/@remusao/guess-url-type/dist/es6/src/extensions/images.js
  var EXTENSIONS3 = /* @__PURE__ */ new Set([
    "apng",
    "bmp",
    "cur",
    "dib",
    "eps",
    "gif",
    "heic",
    "heif",
    "ico",
    "j2k",
    "jfi",
    "jfif",
    "jif",
    "jp2",
    "jpe",
    "jpeg",
    "jpf",
    "jpg",
    "jpm",
    "jpx",
    "mj2",
    "pjp",
    "pjpeg",
    "png",
    "svg",
    "svgz",
    "tif",
    "tiff",
    "webp"
  ]);

  // node_modules/@remusao/guess-url-type/dist/es6/src/extensions/medias.js
  var EXTENSIONS4 = /* @__PURE__ */ new Set([
    "avi",
    "flv",
    "mp3",
    "mp4",
    "ogg",
    "wav",
    "weba",
    "webm",
    "wmv"
  ]);

  // node_modules/@remusao/guess-url-type/dist/es6/src/extensions/scripts.js
  var EXTENSIONS5 = /* @__PURE__ */ new Set(["js", "ts", "jsx", "esm"]);

  // node_modules/@remusao/guess-url-type/dist/es6/src/extensions/stylesheets.js
  var EXTENSIONS6 = /* @__PURE__ */ new Set(["css", "scss"]);

  // node_modules/@remusao/guess-url-type/dist/es6/src/extname.js
  function extname(url) {
    let endOfPath = url.length;
    const indexOfFragment = url.indexOf("#");
    if (indexOfFragment !== -1) {
      endOfPath = indexOfFragment;
    }
    const indexOfQuery = url.indexOf("?");
    if (indexOfQuery !== -1 && indexOfQuery < endOfPath) {
      endOfPath = indexOfQuery;
    }
    let startOfExt = endOfPath - 1;
    let code = 0;
    for (; startOfExt >= 0; startOfExt -= 1) {
      code = url.charCodeAt(startOfExt);
      if ((code >= 65 && code <= 90 || code >= 97 && code <= 122 || code >= 48 && code <= 57) === false) {
        break;
      }
    }
    if (code !== 46 || startOfExt < 0 || endOfPath - startOfExt >= 10) {
      return "";
    }
    return url.slice(startOfExt + 1, endOfPath);
  }

  // node_modules/@remusao/guess-url-type/dist/es6/index.js
  function getRequestType(url) {
    const ext = extname(url);
    if (EXTENSIONS3.has(ext) || url.startsWith("data:image/") || url.startsWith("https://frog.wix.com/bt")) {
      return "image";
    }
    if (EXTENSIONS4.has(ext) || url.startsWith("data:audio/") || url.startsWith("data:video/")) {
      return "media";
    }
    if (EXTENSIONS6.has(ext) || url.startsWith("data:text/css")) {
      return "stylesheet";
    }
    if (EXTENSIONS5.has(ext) || url.startsWith("data:") && (url.startsWith("data:application/ecmascript") || url.startsWith("data:application/javascript") || url.startsWith("data:application/x-ecmascript") || url.startsWith("data:application/x-javascript") || url.startsWith("data:text/ecmascript") || url.startsWith("data:text/javascript") || url.startsWith("data:text/javascript1.0") || url.startsWith("data:text/javascript1.1") || url.startsWith("data:text/javascript1.2") || url.startsWith("data:text/javascript1.3") || url.startsWith("data:text/javascript1.4") || url.startsWith("data:text/javascript1.5") || url.startsWith("data:text/jscript") || url.startsWith("data:text/livescript") || url.startsWith("data:text/x-ecmascript") || url.startsWith("data:text/x-javascript")) || url.startsWith("https://maps.googleapis.com/maps/api/js") || url.startsWith("https://www.googletagmanager.com/gtag/js")) {
      return "script";
    }
    if (EXTENSIONS.has(ext) || url.startsWith("data:text/html") || url.startsWith("data:application/xhtml") || url.startsWith("https://www.youtube.com/embed/") || url.startsWith("https://www.google.com/gen_204")) {
      return "document";
    }
    if (EXTENSIONS2.has(ext) || url.startsWith("data:font/")) {
      return "font";
    }
    return "other";
  }

  // node_modules/tldts-core/dist/es6/src/domain.js
  function shareSameDomainSuffix(hostname, vhost) {
    if (hostname.endsWith(vhost)) {
      return hostname.length === vhost.length || hostname[hostname.length - vhost.length - 1] === ".";
    }
    return false;
  }
  function extractDomainWithSuffix(hostname, publicSuffix) {
    const publicSuffixIndex = hostname.length - publicSuffix.length - 2;
    const lastDotBeforeSuffixIndex = hostname.lastIndexOf(".", publicSuffixIndex);
    if (lastDotBeforeSuffixIndex === -1) {
      return hostname;
    }
    return hostname.slice(lastDotBeforeSuffixIndex + 1);
  }
  function getDomain(suffix, hostname, options) {
    if (options.validHosts !== null) {
      const validHosts = options.validHosts;
      for (const vhost of validHosts) {
        if (
          /*@__INLINE__*/
          shareSameDomainSuffix(hostname, vhost)
        ) {
          return vhost;
        }
      }
    }
    let numberOfLeadingDots = 0;
    if (hostname.startsWith(".")) {
      while (numberOfLeadingDots < hostname.length && hostname[numberOfLeadingDots] === ".") {
        numberOfLeadingDots += 1;
      }
    }
    if (suffix.length === hostname.length - numberOfLeadingDots) {
      return null;
    }
    return (
      /*@__INLINE__*/
      extractDomainWithSuffix(hostname, suffix)
    );
  }

  // node_modules/tldts-core/dist/es6/src/domain-without-suffix.js
  function getDomainWithoutSuffix(domain, suffix) {
    return domain.slice(0, -suffix.length - 1);
  }

  // node_modules/tldts-core/dist/es6/src/extract-hostname.js
  function extractHostname(url, urlIsValidHostname) {
    let start = 0;
    let end = url.length;
    let hasUpper = false;
    if (!urlIsValidHostname) {
      if (url.startsWith("data:")) {
        return null;
      }
      while (start < url.length && url.charCodeAt(start) <= 32) {
        start += 1;
      }
      while (end > start + 1 && url.charCodeAt(end - 1) <= 32) {
        end -= 1;
      }
      if (url.charCodeAt(start) === 47 && url.charCodeAt(start + 1) === 47) {
        start += 2;
      } else {
        const indexOfProtocol = url.indexOf(":/", start);
        if (indexOfProtocol !== -1) {
          const protocolSize = indexOfProtocol - start;
          const c0 = url.charCodeAt(start);
          const c1 = url.charCodeAt(start + 1);
          const c2 = url.charCodeAt(start + 2);
          const c3 = url.charCodeAt(start + 3);
          const c4 = url.charCodeAt(start + 4);
          if (protocolSize === 5 && c0 === 104 && c1 === 116 && c2 === 116 && c3 === 112 && c4 === 115) ; else if (protocolSize === 4 && c0 === 104 && c1 === 116 && c2 === 116 && c3 === 112) ; else if (protocolSize === 3 && c0 === 119 && c1 === 115 && c2 === 115) ; else if (protocolSize === 2 && c0 === 119 && c1 === 115) ; else {
            for (let i = start; i < indexOfProtocol; i += 1) {
              const lowerCaseCode = url.charCodeAt(i) | 32;
              if (!(lowerCaseCode >= 97 && lowerCaseCode <= 122 || // [a, z]
              lowerCaseCode >= 48 && lowerCaseCode <= 57 || // [0, 9]
              lowerCaseCode === 46 || // '.'
              lowerCaseCode === 45 || // '-'
              lowerCaseCode === 43)) {
                return null;
              }
            }
          }
          start = indexOfProtocol + 2;
          while (url.charCodeAt(start) === 47) {
            start += 1;
          }
        }
      }
      let indexOfIdentifier = -1;
      let indexOfClosingBracket = -1;
      let indexOfPort = -1;
      for (let i = start; i < end; i += 1) {
        const code = url.charCodeAt(i);
        if (code === 35 || // '#'
        code === 47 || // '/'
        code === 63) {
          end = i;
          break;
        } else if (code === 64) {
          indexOfIdentifier = i;
        } else if (code === 93) {
          indexOfClosingBracket = i;
        } else if (code === 58) {
          indexOfPort = i;
        } else if (code >= 65 && code <= 90) {
          hasUpper = true;
        }
      }
      if (indexOfIdentifier !== -1 && indexOfIdentifier > start && indexOfIdentifier < end) {
        start = indexOfIdentifier + 1;
      }
      if (url.charCodeAt(start) === 91) {
        if (indexOfClosingBracket !== -1) {
          return url.slice(start + 1, indexOfClosingBracket).toLowerCase();
        }
        return null;
      } else if (indexOfPort !== -1 && indexOfPort > start && indexOfPort < end) {
        end = indexOfPort;
      }
    }
    while (end > start + 1 && url.charCodeAt(end - 1) === 46) {
      end -= 1;
    }
    const hostname = start !== 0 || end !== url.length ? url.slice(start, end) : url;
    if (hasUpper) {
      return hostname.toLowerCase();
    }
    return hostname;
  }

  // node_modules/tldts-core/dist/es6/src/is-ip.js
  function isProbablyIpv4(hostname) {
    if (hostname.length < 7) {
      return false;
    }
    if (hostname.length > 15) {
      return false;
    }
    let numberOfDots = 0;
    for (let i = 0; i < hostname.length; i += 1) {
      const code = hostname.charCodeAt(i);
      if (code === 46) {
        numberOfDots += 1;
      } else if (code < 48 || code > 57) {
        return false;
      }
    }
    return numberOfDots === 3 && hostname.charCodeAt(0) !== 46 && hostname.charCodeAt(hostname.length - 1) !== 46;
  }
  function isProbablyIpv6(hostname) {
    if (hostname.length < 3) {
      return false;
    }
    let start = hostname.startsWith("[") ? 1 : 0;
    let end = hostname.length;
    if (hostname[end - 1] === "]") {
      end -= 1;
    }
    if (end - start > 39) {
      return false;
    }
    let hasColon = false;
    for (; start < end; start += 1) {
      const code = hostname.charCodeAt(start);
      if (code === 58) {
        hasColon = true;
      } else if (!(code >= 48 && code <= 57 || // 0-9
      code >= 97 && code <= 102 || // a-f
      code >= 65 && code <= 90)) {
        return false;
      }
    }
    return hasColon;
  }
  function isIp(hostname) {
    return isProbablyIpv6(hostname) || isProbablyIpv4(hostname);
  }

  // node_modules/tldts-core/dist/es6/src/is-valid.js
  function isValidAscii(code) {
    return code >= 97 && code <= 122 || code >= 48 && code <= 57 || code > 127;
  }
  function is_valid_default(hostname) {
    if (hostname.length > 255) {
      return false;
    }
    if (hostname.length === 0) {
      return false;
    }
    if (
      /*@__INLINE__*/
      !isValidAscii(hostname.charCodeAt(0)) && hostname.charCodeAt(0) !== 46 && // '.' (dot)
      hostname.charCodeAt(0) !== 95
    ) {
      return false;
    }
    let lastDotIndex = -1;
    let lastCharCode = -1;
    const len = hostname.length;
    for (let i = 0; i < len; i += 1) {
      const code = hostname.charCodeAt(i);
      if (code === 46) {
        if (
          // Check that previous label is < 63 bytes long (64 = 63 + '.')
          i - lastDotIndex > 64 || // Check that previous character was not already a '.'
          lastCharCode === 46 || // Check that the previous label does not end with a '-' (dash)
          lastCharCode === 45 || // Check that the previous label does not end with a '_' (underscore)
          lastCharCode === 95
        ) {
          return false;
        }
        lastDotIndex = i;
      } else if (!/*@__INLINE__*/
      (isValidAscii(code) || code === 45 || code === 95)) {
        return false;
      }
      lastCharCode = code;
    }
    return (
      // Check that last label is shorter than 63 chars
      len - lastDotIndex - 1 <= 63 && // Check that the last character is an allowed trailing label character.
      // Since we already checked that the char is a valid hostname character,
      // we only need to check that it's different from '-'.
      lastCharCode !== 45
    );
  }

  // node_modules/tldts-core/dist/es6/src/options.js
  function setDefaultsImpl({ allowIcannDomains = true, allowPrivateDomains = false, detectIp = true, extractHostname: extractHostname2 = true, mixedInputs = true, validHosts = null, validateHostname = true }) {
    return {
      allowIcannDomains,
      allowPrivateDomains,
      detectIp,
      extractHostname: extractHostname2,
      mixedInputs,
      validHosts,
      validateHostname
    };
  }
  var DEFAULT_OPTIONS = (
    /*@__INLINE__*/
    setDefaultsImpl({})
  );
  function setDefaults(options) {
    if (options === void 0) {
      return DEFAULT_OPTIONS;
    }
    return (
      /*@__INLINE__*/
      setDefaultsImpl(options)
    );
  }

  // node_modules/tldts-core/dist/es6/src/subdomain.js
  function getSubdomain(hostname, domain) {
    if (domain.length === hostname.length) {
      return "";
    }
    return hostname.slice(0, -domain.length - 1);
  }

  // node_modules/tldts-core/dist/es6/src/factory.js
  function getEmptyResult() {
    return {
      domain: null,
      domainWithoutSuffix: null,
      hostname: null,
      isIcann: null,
      isIp: null,
      isPrivate: null,
      publicSuffix: null,
      subdomain: null
    };
  }
  function parseImpl(url, step, suffixLookup2, partialOptions, result) {
    const options = (
      /*@__INLINE__*/
      setDefaults(partialOptions)
    );
    if (typeof url !== "string") {
      return result;
    }
    if (!options.extractHostname) {
      result.hostname = url;
    } else if (options.mixedInputs) {
      result.hostname = extractHostname(url, is_valid_default(url));
    } else {
      result.hostname = extractHostname(url, false);
    }
    if (step === 0 || result.hostname === null) {
      return result;
    }
    if (options.detectIp) {
      result.isIp = isIp(result.hostname);
      if (result.isIp) {
        return result;
      }
    }
    if (options.validateHostname && options.extractHostname && !is_valid_default(result.hostname)) {
      result.hostname = null;
      return result;
    }
    suffixLookup2(result.hostname, options, result);
    if (step === 2 || result.publicSuffix === null) {
      return result;
    }
    result.domain = getDomain(result.publicSuffix, result.hostname, options);
    if (step === 3 || result.domain === null) {
      return result;
    }
    result.subdomain = getSubdomain(result.hostname, result.domain);
    if (step === 4) {
      return result;
    }
    result.domainWithoutSuffix = getDomainWithoutSuffix(result.domain, result.publicSuffix);
    return result;
  }

  // node_modules/tldts-core/dist/es6/src/lookup/fast-path.js
  function fast_path_default(hostname, options, out) {
    if (!options.allowPrivateDomains && hostname.length > 3) {
      const last = hostname.length - 1;
      const c3 = hostname.charCodeAt(last);
      const c2 = hostname.charCodeAt(last - 1);
      const c1 = hostname.charCodeAt(last - 2);
      const c0 = hostname.charCodeAt(last - 3);
      if (c3 === 109 && c2 === 111 && c1 === 99 && c0 === 46) {
        out.isIcann = true;
        out.isPrivate = false;
        out.publicSuffix = "com";
        return true;
      } else if (c3 === 103 && c2 === 114 && c1 === 111 && c0 === 46) {
        out.isIcann = true;
        out.isPrivate = false;
        out.publicSuffix = "org";
        return true;
      } else if (c3 === 117 && c2 === 100 && c1 === 101 && c0 === 46) {
        out.isIcann = true;
        out.isPrivate = false;
        out.publicSuffix = "edu";
        return true;
      } else if (c3 === 118 && c2 === 111 && c1 === 103 && c0 === 46) {
        out.isIcann = true;
        out.isPrivate = false;
        out.publicSuffix = "gov";
        return true;
      } else if (c3 === 116 && c2 === 101 && c1 === 110 && c0 === 46) {
        out.isIcann = true;
        out.isPrivate = false;
        out.publicSuffix = "net";
        return true;
      } else if (c3 === 101 && c2 === 100 && c1 === 46) {
        out.isIcann = true;
        out.isPrivate = false;
        out.publicSuffix = "de";
        return true;
      }
    }
    return false;
  }

  // node_modules/tldts-experimental/dist/es6/src/data/hashes.js
  var hashes_default = new Uint32Array([6, 0, 0, 9, 5860739, 5860978, 5861026, 5861029, 5861126, 5861352, 5861357, 5861403, 5861586, 0, 0, 0, 1, 1850179732, 0, 9, 328184559, 1866923597, 2123501943, 2282562397, 2795346450, 3130446446, 3136607046, 3453334789, 4194175729, 64, 3156266, 20989895, 65021741, 101876503, 179500755, 311298055, 425802535, 460682395, 582839475, 819014943, 819028732, 870639071, 1075688039, 1139486022, 1241916785, 1335010188, 1370787547, 1370800824, 1431231509, 1498275876, 1516508161, 1522025464, 1544104458, 1554653742, 1570707647, 1626814538, 1630208269, 1675555530, 1679919230, 1687232530, 1730108052, 1789539963, 1873769763, 1893848785, 2001752368, 2023201532, 2182413090, 2391299855, 2419619562, 2445171142, 2496327381, 2525245455, 2573179642, 2703420555, 2709520566, 2762771525, 2800127296, 2921343336, 2989808530, 3000405309, 3015527775, 3047607849, 3382460164, 3420815319, 3461355676, 3498015045, 3738715095, 3810061811, 3843717774, 3934774481, 4033285539, 4085096371, 4146774829, 4208486561, 3692, 100835, 372942, 373596, 399643, 403867, 589540, 737224, 1210028, 1861414, 2424682, 2658901, 2946999, 3329363, 3333156, 6942202, 9086062, 9095117, 9267209, 9340158, 9485932, 11010102, 11406846, 16314893, 17546564, 18146303, 18331450, 19211200, 20314441, 20797457, 25057869, 26663359, 28320278, 30499151, 30585840, 36605120, 36775470, 36775473, 36990037, 39275208, 41892561, 42049478, 42538024, 45214788, 47656662, 50173535, 53599326, 53858455, 54537430, 63815836, 64422985, 64643127, 64831187, 69971116, 73517283, 73904368, 75706244, 78793775, 78794171, 79558910, 80324123, 84993902, 87977581, 87978853, 87978860, 93811268, 95641381, 95641777, 96671837, 100511481, 100947456, 108215410, 108929491, 110526112, 110662188, 112311307, 114507832, 116811054, 120488259, 122521550, 133427701, 134012911, 141513861, 141517490, 144349377, 144362028, 144550088, 144770230, 147205859, 147810002, 147989623, 149598895, 150736276, 150856054, 152379730, 156555774, 164189124, 164189258, 164189262, 164189691, 164189842, 164560958, 165069166, 165106627, 165107021, 165339368, 165444557, 165444558, 165444615, 165444629, 165444745, 165444749, 165445368, 165512129, 165512527, 165749053, 165749188, 165749299, 165749435, 165749535, 165779060, 167155067, 169909265, 169909275, 169909419, 169909512, 169909517, 169909531, 169909608, 169909724, 169909733, 169909734, 169909738, 169909857, 169910036, 169910195, 169910226, 169938982, 169939075, 169939172, 169939304, 169939334, 169939474, 169939481, 169939680, 169939682, 169939793, 169977029, 169977163, 170281136, 170281250, 170281253, 170281258, 170281275, 170281382, 170281390, 170281415, 170281447, 170281457, 170281473, 170281497, 170281511, 170281522, 170281525, 170281528, 170281579, 170281589, 170281687, 170281689, 170281699, 170281742, 170281776, 170281812, 170281852, 170281902, 170281972, 170311352, 170649202, 170649385, 170649596, 171188220, 172078401, 172145927, 172484120, 172484301, 172788260, 172788689, 172788693, 172788754, 172788809, 172788827, 173118530, 173118924, 173456648, 173591948, 173930212, 173930286, 174129293, 174306499, 174306893, 174307245, 174307439, 174358551, 174374100, 174407806, 174410098, 174488250, 174509317, 174577099, 174644617, 174843632, 174844030, 174847160, 175181758, 175524135, 175524873, 176843304, 176948764, 178529610, 178530165, 178530256, 178530299, 178530303, 178530355, 178868363, 178868576, 178868974, 179274397, 179274476, 179379459, 179379616, 179379624, 179379849, 179379853, 179380220, 179657877, 179692651, 179714168, 179913714, 180090112, 180090244, 180090304, 180090314, 180090337, 180090372, 180090450, 180090510, 180090525, 180090526, 180090587, 180090702, 180091049, 180091118, 180091210, 180091228, 180091258, 180091259, 180283722, 180292996, 180293014, 180293036, 180293067, 180293093, 180293105, 180293124, 180293152, 180293156, 180293169, 180293179, 180293199, 180293253, 180293290, 180293294, 180293300, 180293302, 180293304, 180293317, 180293344, 180293346, 180293381, 180293447, 180293487, 180293501, 180293503, 180293522, 180293535, 180293716, 180293796, 180293819, 180293997, 180294e3, 180294004, 180294009, 180428032, 180902137, 180969265, 180969566, 180969653, 180969723, 181240259, 181240353, 181240367, 181240371, 181240391, 181240392, 181240393, 181240398, 181240404, 181240451, 181240474, 181240479, 181240483, 181240490, 181240509, 181240515, 181240844, 181240853, 181240956, 181241149, 181241165, 181241168, 181244839, 181375748, 181548621, 181548644, 181548727, 181548873, 181549108, 181549176, 181949900, 181950639, 182056031, 182385920, 182419943, 182893167, 182893283, 182893394, 182893788, 183163149, 183163151, 183163155, 183163168, 183163169, 183163171, 183163181, 183163182, 183163183, 183163186, 183163188, 183163233, 183163248, 183163251, 183163252, 183163254, 183163270, 183163303, 183163314, 183163317, 183163334, 183163335, 183163336, 183163340, 183163345, 183163347, 183163350, 183163362, 183163363, 183163365, 183163366, 183163367, 183163371, 183163375, 183163376, 183163378, 183163380, 183163383, 183163630, 183163631, 183163644, 183163649, 183163651, 183163653, 183163655, 183163664, 183163668, 183163669, 183163678, 183163679, 183163682, 183163687, 183163713, 183163715, 183163728, 183163731, 183163735, 183163742, 183163777, 183163779, 183163780, 183163781, 183163783, 183163796, 183163797, 183163801, 183163843, 183163845, 183163847, 183163859, 183163864, 183163865, 183163874, 183163895, 183163897, 183163913, 183163922, 183163933, 183163960, 183163961, 183163963, 183163977, 183163978, 183163979, 183163981, 183163988, 183163989, 183163991, 183163992, 183163994, 183163995, 183163998, 183164008, 183164010, 183164012, 183164021, 183164025, 183164026, 183164027, 183164029, 183164041, 183164044, 183164045, 183164047, 183164050, 183164051, 183164057, 183164060, 183164061, 183164093, 184080938, 184081253, 184081673, 184081677, 184081778, 184246330, 184246511, 184486318, 184486865, 184487263, 184828195, 184828212, 184844696, 184844824, 184848486, 184848491, 184849029, 184849387, 184859173, 184869208, 184869819, 184994607, 185163947, 185216284, 185289081, 185292632, 185295605, 185501943, 185502073, 185502077, 185772974, 186723357, 186723671, 186723801, 186763265, 186771866, 186840059, 186858006, 186875993, 186950941, 186953244, 186994101, 186994720, 187011432, 187022814, 187064894, 187067400, 187076090, 187078647, 187088813, 187161171, 187188812, 187203075, 187219343, 187222314, 187251332, 187328908, 187332203, 187378741, 187385256, 187386889, 187403121, 187403860, 187404132, 187409119, 187410536, 187415116, 187415841, 187417183, 187453423, 187455618, 187483569, 187506658, 187521457, 187531575, 187554851, 187557872, 187932036, 187932044, 187932595, 187932730, 187932752, 187932756, 187932794, 187932985, 187932989, 189851312, 190236828, 190304994, 190305388, 190372512, 190372516, 190372621, 190372839, 190373457, 190575460, 190575594, 190879986, 191043224, 191246659, 191458643, 191459037, 191524213, 193856736, 193857103, 193857114, 193857243, 193991787, 194363750, 194498585, 194498630, 194498988, 194499056, 194499063, 194532263, 194532626, 194532630, 194532693, 194532760, 194532936, 194533115, 194802308, 194802313, 194802316, 194802351, 194802818, 194802832, 194802974, 194803141, 194803143, 194803161, 194803226, 194803230, 194836546, 194870589, 194870610, 194871004, 195040013, 195040230, 195040360, 195077902, 195078025, 195078028, 195078034, 195078035, 195078038, 195078058, 195078062, 195078071, 195078081, 195078095, 195078112, 195078119, 195078120, 195078149, 195078150, 195078156, 195078185, 195078215, 195078217, 195078250, 195078251, 195078272, 195078273, 195078277, 195078283, 195078287, 195078298, 195078299, 195078300, 195078368, 195078372, 195078375, 195078394, 195078464, 195078474, 195078493, 195078531, 195078554, 195078559, 195078687, 195078710, 195078753, 195078828, 195078837, 195078892, 195078895, 195078900, 195078906, 195078959, 195078960, 195078974, 195078995, 195078997, 195079007, 195146051, 195817892, 195817910, 195818040, 196653590, 197775763, 198219289, 198248729, 198354195, 198354632, 202063369, 203326381, 203326382, 203326695, 203326709, 203326825, 203326829, 203327047, 203327192, 203360584, 203427712, 203428110, 203563443, 203563837, 203664976, 203665374, 203762913, 203901612, 204069808, 206121592, 207568995, 208227118, 218659706, 219797064, 231775478, 232791016, 232866163, 232870916, 237059472, 238230825, 238671321, 241611072, 245880244, 249954601, 256262487, 257210252, 257542887, 259829097, 260353797, 260353928, 260353938, 260354380, 260381156, 260390354, 271387034, 274691435, 279382168, 280527902, 280532777, 280535076, 280542659, 281931451, 292827804, 295209043, 296292341, 297619746, 305011770, 306510696, 313583e3, 314643431, 320313766, 320318114, 321023689, 321141002, 321447655, 325454853, 326762411, 337081594, 338040061, 339830659, 340010259, 341833935, 342149828, 342665371, 356194258, 359223603, 359276554, 360327984, 368215882, 370146306, 370150662, 373255328, 373394720, 374785091, 376173808, 377307531, 377336144, 377652210, 379825795, 380248845, 380316586, 381874529, 381884647, 382049883, 382486912, 382598847, 389069795, 389909922, 392084057, 393290800, 395076177, 395140257, 402724451, 403769719, 404122044, 410188633, 413977571, 418962805, 419080649, 423458772, 430711818, 430784915, 431116435, 431157415, 431370962, 431390595, 431489022, 431585240, 431586828, 431608121, 433686700, 442888655, 442922019, 445176561, 449218512, 449424719, 451217894, 451870618, 459172225, 459395692, 464626711, 464765206, 464834904, 469098393, 471052880, 478642118, 480635114, 480636362, 480638119, 480638181, 480638612, 480653244, 480658155, 480658807, 484603510, 484645735, 486805732, 490264076, 490274093, 493445761, 511578298, 513731936, 514111995, 514955151, 515474792, 515491843, 515593995, 518161197, 520595267, 522631343, 523234636, 525872321, 527144416, 531427447, 533682535, 533847771, 534396735, 545433338, 547443445, 550462929, 551440509, 557981738, 559064708, 560636591, 572640614, 572652435, 572800203, 572833146, 572867160, 575127842, 575742406, 575835832, 576106402, 576590271, 577168455, 582462766, 583917065, 583936789, 584490345, 587768078, 588145733, 596395114, 596517435, 602054693, 609523853, 627471386, 630686153, 632559259, 635121653, 635859009, 637007260, 643488605, 643663853, 648304671, 650538190, 656171171, 656243914, 656640963, 665693626, 667797222, 678076451, 679253935, 684522993, 684536293, 689172736, 689202009, 693611235, 694324728, 695649196, 703142796, 706540885, 707132367, 715533184, 722903474, 725879070, 728415570, 731964179, 733989474, 744440632, 745674128, 752520493, 752687122, 752687226, 752699150, 752938578, 753314817, 762792020, 766278458, 771168358, 772916985, 785945688, 787032422, 793080342, 794341423, 794638681, 799598398, 803443550, 803504423, 803576910, 803750530, 804899040, 810638083, 813049915, 813882670, 813882809, 821390609, 822184173, 824372117, 826639012, 826993974, 827624512, 831815016, 834750300, 834856638, 834963202, 835666250, 838463501, 843454848, 845393562, 845537310, 846032279, 853098265, 855980394, 858467853, 869651422, 878524814, 881613818, 885943745, 896206971, 896253025, 900375831, 900562876, 904696072, 907903147, 911040096, 912288153, 912452591, 913046780, 914761571, 915088911, 915769822, 915838470, 919008564, 919376364, 928343570, 933141848, 935240483, 936096500, 939243980, 939281294, 939375524, 939697158, 939922440, 940027871, 942743627, 943328481, 943363810, 947022624, 950098348, 954017396, 958817278, 959069811, 961909457, 961915153, 962363178, 962549619, 963013768, 968961134, 973306633, 973587946, 973591516, 973595243, 973613934, 973618563, 976871270, 977251657, 983929219, 983931665, 983936021, 984542401, 985854160, 994961720, 1002154839, 1005485664, 1005660307, 1005931709, 1008280710, 1009678005, 1015938248, 1018008327, 1024510565, 1027688850, 1033879086, 1034357170, 1038843968, 1039500800, 1043537387, 1043742405, 1044060157, 1045601283, 1046273911, 1046743273, 1046756254, 1048099261, 1052311686, 1052441930, 1052883806, 1055187548, 1056740120, 1058016469, 1059921109, 1068743400, 1072264613, 1080832696, 1083646554, 1084662717, 1086607170, 1086818213, 1086839634, 1087030220, 1087432248, 1087540767, 1088313455, 1101657937, 1101658065, 1102136407, 1102691201, 1104888372, 1107574816, 1107604513, 1107608406, 1114346722, 1115517588, 1116603570, 1116886791, 1121068214, 1121069468, 1123274870, 1123277038, 1123281470, 1123286137, 1123300855, 1135543458, 1135544712, 1135545955, 1135553917, 1135559494, 1135563376, 1141006631, 1141018311, 1142918810, 1143019669, 1145288372, 1146787097, 1149112251, 1151589762, 1152383075, 1153556935, 1153560693, 1153560855, 1153576209, 1153582928, 1155609853, 1158010336, 1158014282, 1158019276, 1158022529, 1158025585, 1158030151, 1158040127, 1158040853, 1158043091, 1160141196, 1160245697, 1160246728, 1160253683, 1160271099, 1160271446, 1160272445, 1160277399, 1161223806, 1161235355, 1162489113, 1166908086, 1166937977, 1166949933, 1166952503, 1166953757, 1166959964, 1169030529, 1169037994, 1169039382, 1169046802, 1169046815, 1169048548, 1169054036, 1169994302, 1171270800, 1171270813, 1172775704, 1174042111, 1174752677, 1174762471, 1175721241, 1175725254, 1175726508, 1175727467, 1175727495, 1175735444, 1175735449, 1175736592, 1175738385, 1175738578, 1175738760, 1175746250, 1175746252, 1175749986, 1175793566, 1181427747, 1181429001, 1181435208, 1181446765, 1181453654, 1181460959, 1185692184, 1189090107, 1193567716, 1194400508, 1204258276, 1204470469, 1207765705, 1207825797, 1208230324, 1208517393, 1208911775, 1211364607, 1212671635, 1214258492, 1217924538, 1220965831, 1229000062, 1229783327, 1232816452, 1237771172, 1237773393, 1237773841, 1245899123, 1247245722, 1257366451, 1260762188, 1261854970, 1265324777, 1265669119, 1273073240, 1280280379, 1280768035, 1291368159, 1295085673, 1296518360, 1297048848, 1300364681, 1303650868, 1304687455, 1304781392, 1304918086, 1305056028, 1306968125, 1306972554, 1306973586, 1307665177, 1308558601, 1308559744, 1308574194, 1308583254, 1308584508, 1308585495, 1310785148, 1310799239, 1310800921, 1310801269, 1310803416, 1310807041, 1310808370, 1311349087, 1313021694, 1313023237, 1313030377, 1314270973, 1314287001, 1314293208, 1321085506, 1324313259, 1324313985, 1324320704, 1324322270, 1324332261, 1324636022, 1325293061, 1325300526, 1325303158, 1325308368, 1325309334, 1325309339, 1325310241, 1325310486, 1325311328, 1325311482, 1326707500, 1328209699, 1328777903, 1328778629, 1328785348, 1328786906, 1328789635, 1328794451, 1328797153, 1329963165, 1329987910, 1330666198, 1330807345, 1330903052, 1331009222, 1331010221, 1331013633, 1331015175, 1331019352, 1331025251, 1331026645, 1331028446, 1331143849, 1335892543, 1336436046, 1336436772, 1336437775, 1336438057, 1336439236, 1336443338, 1336449024, 1336456660, 1336460266, 1336462620, 1336463768, 1336469142, 1341018428, 1341081128, 1341091249, 1341179896, 1342001696, 1344411053, 1344426134, 1344436952, 1344437939, 1344444146, 1346529166, 1349466130, 1350170659, 1350170661, 1350356518, 1350356534, 1350620578, 1351056251, 1351154191, 1351382419, 1351445663, 1354447091, 1354448055, 1354464484, 1354467042, 1354475004, 1354584300, 1355466970, 1355483586, 1355607656, 1355929695, 1355947655, 1356150953, 1356150969, 1356150973, 1356457867, 1356471002, 1356757572, 1357692080, 1357876668, 1357880232, 1360043731, 1360220638, 1362168625, 1362262729, 1362271868, 1362285703, 1362326863, 1362506071, 1362656266, 1365811994, 1367692098, 1367811071, 1368820926, 1369663049, 1377739598, 1378565283, 1379014609, 1383613953, 1383613964, 1383629111, 1383647122, 1385857457, 1385879444, 1388074128, 1388078600, 1388084119, 1388086017, 1388094003, 1388104573, 1388109527, 1388111766, 1390304957, 1390318095, 1390319238, 1390327192, 1390328435, 1390329689, 1391292472, 1391295130, 1391298115, 1391299402, 1391302044, 1391307254, 1391308253, 1392560940, 1396553940, 1397006395, 1397007527, 1397007872, 1397007885, 1397015305, 1397016949, 1397022431, 1400354688, 1400355947, 1400356673, 1400360856, 1400364702, 1400366245, 1401741660, 1407053336, 1407067683, 1409840426, 1410939834, 1414623055, 1417953492, 1417953925, 1417969521, 1417971248, 1418042854, 1418666866, 1422407147, 1422418384, 1422432926, 1422434165, 1422435892, 1423090882, 1425971467, 1426162994, 1426865884, 1426871783, 1426872814, 1426880658, 1426881913, 1426884152, 1428612014, 1429098926, 1429105132, 1429112250, 1430623854, 1433558874, 1433568865, 1433577620, 1433578879, 1435862377, 1444705448, 1444706435, 1444707945, 1444708598, 1444713016, 1444718265, 1444720166, 1444723003, 1444725453, 1444731199, 1444731564, 1444731950, 1444732047, 1444732342, 1444732347, 1444738453, 1448052138, 1448052864, 1448054123, 1448067662, 1448078965, 1449172589, 1452091461, 1453961462, 1457037634, 1457145422, 1457156469, 1457178704, 1459376581, 1459377857, 1459377868, 1459384567, 1459385707, 1459403577, 1459405260, 1459408531, 1463053013, 1463840740, 1463842504, 1463849459, 1463849797, 1463867222, 1463868221, 1463873175, 1464819582, 1464821125, 1464829402, 1464830128, 1464831131, 1465838987, 1466068861, 1466074694, 1466091096, 1466403701, 1467047928, 1467061763, 1467063453, 1467065948, 1467070902, 1468307140, 1468314970, 1468321435, 1469284474, 1469285761, 1469294772, 1469295775, 1471526086, 1474720970, 1474751199, 1474796155, 1474852365, 1474856386, 1474857640, 1474858627, 1474866589, 1474867476, 1474871748, 1474880870, 1482183211, 1482187228, 1482389973, 1486003341, 1486005836, 1486010790, 1486021608, 1486029338, 1486036499, 1486036510, 1491300687, 1492905126, 1495099017, 1496999162, 1497335658, 1497338257, 1497341434, 1497353781, 1497360500, 1497361503, 1503214457, 1504022303, 1504024292, 1504032122, 1504033105, 1504038587, 1509379857, 1510741574, 1511059454, 1514359714, 1514604870, 1517410020, 1517415502, 1517416485, 1517424315, 1517426048, 1519466742, 1519486936, 1521633706, 1524564715, 1526518672, 1534242148, 1535379077, 1535411852, 1535416972, 1535418272, 1535419013, 1535426999, 1535427585, 1535429447, 1535437817, 1535442771, 1535445010, 1538631370, 1539876488, 1539883905, 1539891891, 1539902461, 1539907415, 1539909654, 1540853566, 1540863813, 1540865371, 1540871834, 1540872816, 1540972285, 1544565822, 1547523228, 1548000883, 1548203684, 1548662272, 1548668010, 1548668993, 1548676831, 1548677846, 1548686756, 1550655859, 1551291701, 1552780862, 1554083280, 1554160502, 1556617220, 1556618479, 1556619205, 1556627226, 1556629025, 1571587981, 1572843623, 1577978899, 1578737375, 1579027766, 1580891870, 1580902117, 1580903020, 1580910138, 1580910864, 1581061599, 1584242651, 1584252576, 1584258687, 1584260414, 1584261397, 1594150134, 1594318433, 1594644051, 1595762332, 1596345927, 1596503336, 1599871881, 1600554193, 1600562964, 1600967980, 1600968967, 1600970477, 1600988233, 1600993979, 1600994866, 1600997301, 1601541268, 1602995891, 1603061457, 1604314670, 1604316655, 1604330442, 1604341489, 1604342648, 1605183784, 1605406132, 1605908391, 1607689728, 1607689741, 1607690628, 1607701062, 1607701276, 1607705078, 1607710365, 1607715640, 1607716607, 1607716627, 1608344260, 1610313759, 1610666926, 1611239998, 1611396088, 1614382839, 1614530679, 1615167003, 1615172374, 1615640392, 1615647347, 1615658840, 1615665110, 1615666109, 1615671063, 1620094847, 1620095619, 1620095929, 1620105028, 1620113841, 1620119323, 1620795340, 1621082362, 1621083649, 1621092660, 1622329964, 1622331641, 1622337218, 1622353628, 1623408910, 1624559739, 1624569664, 1624577502, 1624577906, 1624578485, 1626556599, 1628470609, 1630022199, 1632310642, 1633163415, 1635568907, 1635591150, 1635593749, 1635643420, 1635994183, 1635994320, 1641006393, 1645672758, 1645785364, 1645803376, 1645808858, 1645809841, 1646891621, 1646892908, 1646907799, 1646910247, 1646917618, 1646918617, 1648006829, 1648007716, 1648013185, 1648013984, 1648016015, 1648021910, 1648025704, 1648032728, 1648033439, 1648033715, 1648035901, 1648039922, 1648043240, 1649119056, 1649454738, 1649581121, 1652486802, 1652497372, 1652504566, 1652932064, 1652936599, 1653583645, 1653598182, 1653599929, 1653606136, 1653607123, 1654697756, 1654712103, 1654713134, 1654716280, 1654721234, 1654722233, 1656168200, 1659162648, 1659176739, 1659180924, 1659185878, 1659186877, 1659695250, 1660874915, 1664393911, 1666510724, 1668155429, 1669474757, 1673661122, 1673662353, 1673671436, 1673686839, 1673856704, 1674136053, 1674769898, 1674770881, 1674776363, 1674793871, 1675780006, 1676641114, 1677004461, 1677008482, 1677010668, 1677010688, 1677011655, 1677022217, 1677030942, 1677037554, 1679194024, 1679234542, 1679234666, 1679237897, 1679241007, 1679252114, 1679258763, 1679261552, 1679266928, 1681499983, 1681500998, 1681504918, 1681510964, 1681520272, 1681526010, 1681526993, 1682221833, 1682359277, 1685960411, 1685962398, 1685964612, 1685965520, 1685965569, 1685965582, 1685965890, 1685967499, 1685968865, 1685974082, 1685987547, 1685988215, 1685988552, 1685991645, 1686112357, 1686592668, 1686670946, 1687209740, 1690419670, 1690419852, 1690423356, 1690429255, 1690430286, 1690438386, 1690439385, 1690439477, 1691674376, 1691689779, 1691700349, 1691705303, 1691707542, 1691739899, 1692242488, 1693900733, 1693904467, 1693911703, 1693913871, 1693915014, 1693915019, 1693922968, 1693923252, 1693924211, 1693925465, 1696514991, 1697110779, 1697112784, 1697112842, 1697116346, 1697119048, 1697126337, 1697127463, 1697127903, 1697134366, 1697135348, 1699859798, 1705948764, 1706596362, 1707661217, 1709380801, 1709397036, 1709401602, 1709403991, 1709403994, 1709715630, 1709719753, 1710553669, 1710842194, 1711349139, 1711911296, 1712862856, 1712864099, 1712865353, 1712874413, 1712889750, 1715042583, 1716067791, 1716074254, 1716075236, 1716090026, 1716093784, 1716101073, 1716987897, 1717046504, 1717344945, 1717458342, 1717567159, 1717665490, 1720424110, 1720435157, 1720448732, 1720448944, 1720449947, 1720450929, 1722611952, 1723770733, 1723771620, 1723777366, 1723796376, 1723797619, 1723869014, 1724144999, 1724360630, 1724888746, 1724891334, 1724900049, 1724902970, 1724913368, 1724913588, 1724914591, 1724915573, 1727744610, 1733044570, 1737465416, 1740104597, 1740108386, 1741479646, 1741618915, 1741621154, 1741622153, 1741631292, 1741636935, 1741709977, 1742216984, 1743089654, 1744959211, 1744968590, 1744969829, 1744971556, 1744977659, 1744987840, 1745343269, 1745488513, 1746392299, 1747200908, 1747202151, 1747210105, 1747211248, 1747212978, 1747215938, 1747219291, 1747533677, 1747671543, 1747762259, 1748301224, 1748301648, 1748302211, 1748318651, 1748321229, 1748327140, 1748327340, 1748328118, 1748329946, 1749416322, 1749419816, 1749422630, 1749422974, 1749423815, 1749423848, 1749423862, 1749423980, 1749432545, 1749435316, 1749435457, 1749435956, 1749437829, 1749437986, 1749440303, 1749441388, 1749442296, 1749442361, 1749443256, 1749443576, 1749444398, 1749445477, 1749445739, 1749750164, 1749955965, 1752768365, 1753028168, 1753430927, 1753880966, 1753882221, 1753900232, 1753906931, 1756680747, 1759105063, 1762715404, 1763952265, 1763967858, 1763978172, 1763979159, 1765274516, 1768132013, 1774870841, 1775278057, 1776446407, 1778765218, 1779479261, 1779706923, 1779707649, 1779709525, 1779713177, 1779714057, 1779714368, 1779715934, 1779715971, 1779725925, 1779730307, 1779731494, 1780768183, 1781938242, 1781939241, 1781944195, 1781948380, 1781954023, 1781961852, 1783657515, 1785147288, 1785152492, 1785564290, 1786402886, 1786403885, 1786408839, 1786413016, 1786418915, 1786422601, 1793085197, 1793091404, 1793103209, 1793109842, 1794311882, 1796513490, 1798682988, 1799934413, 1800873944, 1804734874, 1804986274, 1805201900, 1805201909, 1805381533, 1805390218, 1805394927, 1805396070, 1805397817, 1805404024, 1805410294, 1808346875, 1809278593, 1809846425, 1809852765, 1809854826, 1809860706, 1809868668, 1809869655, 1809909084, 1810126394, 1810162729, 1811189710, 1812804641, 1813167465, 1818860644, 1819164253, 1824377544, 1826567786, 1826567942, 1826568769, 1826574251, 1826586852, 1826591759, 1826593533, 1826594804, 1826595685, 1826597041, 1826838298, 1830073720, 1832102940, 1835526804, 1835527882, 1835530317, 1835531888, 1835536950, 1835540435, 1835541852, 1835548479, 1835548755, 1835552425, 1835554706, 1835556216, 1836706536, 1838062951, 1839007628, 1839021100, 1839022775, 1839033593, 1839038547, 1839040786, 1839994953, 1840001842, 1840013399, 1840019350, 1840019827, 1840020860, 1843076481, 1845608978, 1846070315, 1848013570, 1854921046, 1859450748, 1859510931, 1859511204, 1860240647, 1860312281, 1860334137, 1861101595, 1863024310, 1866891339, 1866893066, 1866896736, 1866908847, 1866910185, 1866914026, 1867191437, 1867861768, 1867865679, 1867867083, 1867872142, 1867873124, 1867876289, 1867885376, 1867885466, 1867887914, 1867892691, 1867897750, 1867898961, 1867899162, 1873521117, 1875950626, 1878219696, 1883713830, 1883718737, 1883722494, 1883726489, 1883992567, 1884025074, 1889208808, 1889317056, 1890185274, 1890552293, 1891315242, 1893129355, 1894534152, 1894535395, 1894543357, 1894548934, 1895822736, 1896748195, 1896864381, 1896883495, 1896884690, 1896893413, 1897086584, 1897144569, 1897150382, 1897161336, 1898308423, 1899713189, 1903920486, 1903920882, 1906518923, 1906815088, 1907758428, 1907908343, 1907910446, 1907911172, 1907924055, 1907926218, 1907937265, 1910568778, 1912588116, 1912664290, 1912773142, 1919704439, 1919708663, 1925589573, 1928014104, 1931786446, 1933270769, 1933847987, 1934282690, 1935832225, 1937137824, 1940180687, 1941545223, 1944881831, 1944883085, 1944889292, 1944901097, 1944907730, 1944915291, 1947690884, 1949378607, 1949381140, 1949385828, 1949388221, 1949404634, 1953208595, 1957126749, 1965980590, 1966393263, 1967560433, 1968030901, 1968344522, 1968345101, 1968353343, 1968354820, 1969952988, 1969953274, 1970271924, 1982830318, 1982831301, 1982836783, 1982854539, 1982856313, 1982857328, 1982862253, 1982863214, 1983945412, 1983946415, 1983946627, 1983953134, 1983957025, 1983968650, 1983971249, 1983972408, 1983977373, 1985096774, 1985106740, 1985116048, 1985122769, 1987638584, 1989155232, 1991785536, 1991792841, 1991799730, 1991811287, 1991817238, 1991817715, 1991818748, 1994019132, 1994026062, 1994028952, 1994613365, 2000627256, 2002587178, 2002703477, 2004080420, 2007546240, 2007547499, 2007556254, 2007557797, 2009780252, 2013938002, 2016158046, 2016458632, 2016459875, 2016461129, 2016470189, 2016476340, 2016482461, 2016485526, 2019785049, 2023148389, 2023153871, 2023155598, 2023156002, 2023157760, 2023171627, 2023174160, 2023812622, 2029256230, 2029286951, 2029296544, 2037064184, 2042215210, 2042272668, 2042423451, 2043073993, 2044012869, 2046744295, 2047386704, 2047490213, 2047625030, 2047828609, 2051192703, 2052284669, 2056364987, 2056365175, 2056459861, 2057257910, 2058376024, 2058382302, 2058436464, 2058440319, 2058445367, 2058448694, 2058452545, 2058552215, 2058569521, 2058573621, 2058924197, 2058929805, 2058958371, 2058984507, 2058988863, 2059003240, 2059051015, 2059075746, 2059422408, 2059824807, 2061714098, 2062014471, 2063260135, 2063415690, 2063627333, 2063814283, 2064238717, 2064313581, 2064484772, 2064499575, 2064635107, 2064635452, 2064635773, 2064639428, 2064639883, 2064648773, 2064654772, 2064655646, 2065476844, 2065542420, 2065542544, 2065543022, 2065727011, 2066567940, 2066734284, 2066828553, 2066833534, 2067202738, 2067233317, 2068031208, 2068725531, 2068831008, 2068854498, 2068854512, 2068858196, 2068859575, 2068860177, 2068862627, 2068863232, 2068869021, 2068950273, 2068994789, 2068994807, 2069062998, 2069102686, 2069161595, 2069263945, 2069338842, 2069365704, 2069468800, 2069558220, 2069561350, 2069566268, 2069591394, 2069593072, 2069595618, 2069600040, 2069600946, 2069600957, 2069604100, 2069765192, 2069904166, 2069904305, 2071035931, 2071149679, 2071643658, 2073289171, 2073308845, 2073310709, 2073312474, 2073322881, 2073335784, 2073440452, 2073448514, 2073457247, 2073500084, 2073509625, 2073523923, 2073533208, 2073640292, 2073794194, 2073803151, 2073803461, 2073808229, 2073811616, 2073811996, 2073815760, 2073826308, 2073826688, 2073827152, 2073830759, 2073831593, 2073831601, 2074299520, 2075044848, 2075423284, 2075693433, 2078935992, 2078936931, 2078937889, 2078937913, 2078938163, 2078938295, 2078944407, 2078944555, 2078944613, 2078944933, 2081181239, 2082063743, 2082285629, 2082430948, 2084946688, 2086083080, 2087431076, 2087431077, 2087431079, 2087431080, 2087431081, 2087431082, 2087431085, 2087431086, 2087431087, 2087431088, 2087431089, 2087431090, 2087431091, 2087431092, 2087431093, 2087431094, 2087431096, 2087431097, 2087431098, 2087431099, 2087431100, 2087431102, 2087431103, 2087617590, 2087617591, 2087617592, 2087617593, 2087617594, 2087617595, 2087617596, 2087617597, 2087617598, 2087617599, 2087617632, 2087617633, 2087617634, 2087617635, 2087617636, 2087617637, 2087617638, 2087617639, 2087617640, 2087617641, 2087617642, 2087617643, 2087617644, 2087617645, 2087617647, 2087617652, 2087617654, 2087617655, 2087617656, 2087617657, 2087617658, 2087617659, 2087617660, 2087617661, 2087617662, 2087617663, 2087629931, 2087822490, 2088302297, 2088726760, 2088953542, 2090213881, 2090218574, 2090297888, 2090298020, 2090439875, 2090439900, 2091225604, 2092577468, 2092702023, 2092715579, 2092766986, 2092957042, 2093991393, 2093995617, 2093995632, 2097113374, 2098599777, 2098599792, 2099138174, 2102249573, 2102285158, 2102285168, 2102285285, 2102285374, 2102286572, 2102291553, 2102297313, 2102301463, 2102304381, 2102311282, 2102312281, 2102313468, 2102315379, 2102317235, 2102322718, 2103529616, 2105684477, 2105873178, 2106751208, 2106757636, 2106766355, 2106769656, 2106775467, 2106775926, 2106776925, 2106781879, 2118750891, 2119037299, 2119037310, 2119041270, 2119043865, 2119381911, 2119891962, 2120136928, 2120142410, 2120143393, 2120151231, 2120152708, 2121629990, 2122433548, 2123414271, 2123472843, 2123472936, 2123472941, 2123472990, 2123479292, 2123481132, 2123481326, 2123481391, 2123481939, 2123481960, 2123482409, 2123482928, 2123482935, 2123485221, 2123485512, 2123485548, 2123486092, 2123487587, 2123487602, 2123487868, 2123488061, 2123488218, 2123489049, 2123491458, 2123491494, 2123491502, 2123491940, 2123491950, 2123491964, 2123492067, 2123492380, 2123492410, 2123492613, 2123492943, 2123493403, 2123494323, 2123494721, 2123494806, 2123495205, 2123495222, 2123495263, 2123495538, 2123495599, 2123495615, 2123495829, 2123496707, 2123496945, 2123497027, 2123497539, 2123498152, 2123498482, 2123498621, 2123498738, 2123499337, 2123499387, 2123499393, 2123499675, 2123499817, 2123499823, 2123500085, 2123500670, 2123501043, 2123501651, 2123501946, 2123502012, 2123502614, 2123502618, 2123502909, 2123502931, 2123502972, 2123503489, 2123503580, 2123503633, 2123503639, 2123503645, 2123503683, 2123503690, 2123503871, 2123503914, 2123503925, 2123506021, 2123508761, 2123508887, 2123508888, 2123509104, 2123509367, 2123510210, 2126830924, 2126831627, 2126831911, 2126831915, 2126834731, 2126838118, 2126839865, 2126841008, 2126851442, 2126854146, 2127933481, 2127939688, 2127940675, 2127945958, 2127950989, 2127966582, 2130163562, 2130164545, 2130170027, 2130187535, 2130190580, 2131286378, 2132327224, 2132331087, 2132359596, 2133546426, 2134655216, 2135730753, 2135744303, 2135751022, 2135766376, 2135766538, 2136033383, 2136198665, 2140379406, 2140382005, 2140404240, 2140405499, 2140406225, 2141369520, 2141378580, 2141384318, 2142607534, 2142608862, 2142616598, 2142619146, 2143588731, 2143590729, 2143592861, 2143597618, 2143609175, 2143615126, 2143616636, 2144000095, 2144838611, 2144844042, 2144846897, 2144858266, 2144868884, 2144870143, 2144870869, 2157945278, 2158338411, 2160318468, 2160324206, 2160325189, 2160333019, 2160343200, 2161569257, 2161578129, 2161578140, 2161592231, 2161595735, 2165898261, 2166038855, 2166996811, 2167003274, 2167004256, 2167015877, 2167018798, 2167213797, 2167993101, 2169327252, 2170481633, 2170487115, 2170488842, 2170504623, 2170507412, 2174946277, 2174951759, 2174953486, 2174953890, 2174969515, 2174972048, 2176528068, 2179101309, 2180545870, 2191744103, 2191744212, 2191821366, 2191883015, 2192566334, 2193960351, 2195897610, 2195898849, 2195906687, 2195916612, 2195922100, 2196631346, 2205406696, 2211506222, 2216825796, 2219145843, 2221394610, 2225058301, 2225061335, 2225064134, 2225071439, 2225073075, 2225080536, 2226037368, 2226044042, 2226051203, 2226052893, 2226055388, 2226060342, 2226419862, 2229788675, 2230793522, 2230840997, 2231615745, 2231617728, 2231623210, 2231628742, 2231632031, 2231633170, 2231633764, 2231638049, 2231729235, 2231751291, 2231760201, 2231761216, 2231769054, 2231770037, 2231775519, 2233884981, 2235097422, 2235100587, 2235101313, 2235108032, 2235109598, 2235116887, 2235119589, 2236869449, 2241796550, 2241797549, 2241806680, 2241812579, 2242828527, 2246244298, 2246245281, 2246250763, 2246260079, 2246271316, 2247223374, 2247249937, 2247251096, 2248592412, 2250708942, 2250715407, 2250719552, 2250724971, 2250725805, 2250733692, 2250734937, 2250735952, 2258665553, 2258878642, 2264886749, 2266447633, 2267607e3, 2274782645, 2282544968, 2285662351, 2290599544, 2292158595, 2293175691, 2293351636, 2296071446, 2299255515, 2301040846, 2306079466, 2307580553, 2313241363, 2313504811, 2318220358, 2320224028, 2325476095, 2337176745, 2339504386, 2344847762, 2345345412, 2345556981, 2346482211, 2346482871, 2351498341, 2352240646, 2352738840, 2358991500, 2361087993, 2364634824, 2371011349, 2373457221, 2375393789, 2376425283, 2379512524, 2379580075, 2390286898, 2390518325, 2390736011, 2392516839, 2392521063, 2400874900, 2400879124, 2402335630, 2404974948, 2405102721, 2405117283, 2405120727, 2414810349, 2415093005, 2415923742, 2415925541, 2415935547, 2415976346, 2418152088, 2422623072, 2422625395, 2422631927, 2422634373, 2422636295, 2422636392, 2425962056, 2425963043, 2425969250, 2425969487, 2425971892, 2425985030, 2428197348, 2428202830, 2428203813, 2428211643, 2428212914, 2428213376, 2428240545, 2430223084, 2433759338, 2433759634, 2433760321, 2433765803, 2433783311, 2433785126, 2433786356, 2433788522, 2435993901, 2436000108, 2436001095, 2436011657, 2436026994, 2439339076, 2439340079, 2439340291, 2439346798, 2439350689, 2439362314, 2439364913, 2439366072, 2439371037, 2439876345, 2440431898, 2440444045, 2440449369, 2444112661, 2447928023, 2452264162, 2454797153, 2458316286, 2459819944, 2462285242, 2462802458, 2463186757, 2466741694, 2466758807, 2467213089, 2467545358, 2467601561, 2467655846, 2467686484, 2467740953, 2473985870, 2474042431, 2474150919, 2474285829, 2474577412, 2474661520, 2475343068, 2475470210, 2475772433, 2475877012, 2475877016, 2475892298, 2476213365, 2476552306, 2479517659, 2489453909, 2489531547, 2498555779, 2501597440, 2507278661, 2510852110, 2511694664, 2512156190, 2540805343, 2543008264, 2547140668, 2553182506, 2558063998, 2558416820, 2560726248, 2564751176, 2566787042, 2569608194, 2572602371, 2577853220, 2579803386, 2583084289, 2586020617, 2600402029, 2604613571, 2614694552, 2616608417, 2623678483, 2624091113, 2626979216, 2627765050, 2629831661, 2630340943, 2630577386, 2637047575, 2637160117, 2637393619, 2637589507, 2639283063, 2642320383, 2657728452, 2661288721, 2663538084, 2673250796, 2673678071, 2673953045, 2683622002, 2686768508, 2689921282, 2691751732, 2691869931, 2692015714, 2693065457, 2693628719, 2694158948, 2699054734, 2699567323, 2701589506, 2708247797, 2710218932, 2712973569, 2713114330, 2714570818, 2714658156, 2715859111, 2716538256, 2717691085, 2718235570, 2719851426, 2722275573, 2728431851, 2731033959, 2733567145, 2745064373, 2747735009, 2748168364, 2748310006, 2753354596, 2761147374, 2762813598, 2767767034, 2769808878, 2775691349, 2789347571, 2792452218, 2793624174, 2794767436, 2795183554, 2795185357, 2795205893, 2798224110, 2803597621, 2804113804, 2807804736, 2809486328, 2813025413, 2815428841, 2815585428, 2816618421, 2819662823, 2822221150, 2824682484, 2828575765, 2828866516, 2829935276, 2834927579, 2836892761, 2839658405, 2844621372, 2844815106, 2845489684, 2845638303, 2857193006, 2860702321, 2870435535, 2874906565, 2880233005, 2885526550, 2889073982, 2893961579, 2896115089, 2896360091, 2896815948, 2898520762, 2898642745, 2908250170, 2908376536, 2911135641, 2915014315, 2918403731, 2918486269, 2919235927, 2920587887, 2922468503, 2922493886, 2923084706, 2929584080, 2931398379, 2931402541, 2934893225, 2937779198, 2941551192, 2942859576, 2948690168, 2948867989, 2949433359, 2951266128, 2954570766, 2956489777, 2960184498, 2960188722, 2960612931, 2962892549, 2963032843, 2966548328, 2976545290, 2976620947, 2978924197, 2982913903, 2986096991, 2987284613, 2988637881, 2993692642, 2996709992, 2999106536, 3000568496, 3005531064, 3005732955, 3007175865, 3007286028, 3008753857, 3010444860, 3010880247, 3017258218, 3019938621, 3020499579, 3022866914, 3023311759, 3024482653, 3024795687, 3024807531, 3027071777, 3029820267, 3032088673, 3032839979, 3033043261, 3033965900, 3036878933, 3037343835, 3038234864, 3051293097, 3052701732, 3055037923, 3056484673, 3060407188, 3061523114, 3071254387, 3071254500, 3071254881, 3073058130, 3074871971, 3074935051, 3075008146, 3075048985, 3075285442, 3075422693, 3075548305, 3075766008, 3075860343, 3075962648, 3076097045, 3077391764, 3079190285, 3085252246, 3091553195, 3103424085, 3107541791, 3107727924, 3107749241, 3107778469, 3107783354, 3107787446, 3107790299, 3107948057, 3107956419, 3107974264, 3107984588, 3107991466, 3108296169, 3111583245, 3113459538, 3116256345, 3116975703, 3117043431, 3121647752, 3123411243, 3123445549, 3123737595, 3127243644, 3131616468, 3134139083, 3134716611, 3141709512, 3148676509, 3154082174, 3155375542, 3160028447, 3163162577, 3163167462, 3163515572, 3163650864, 3172095015, 3178395499, 3179705353, 3183658699, 3187099641, 3187299343, 3189362935, 3189614929, 3189845278, 3191231848, 3191324353, 3196795314, 3196799538, 3197664642, 3200115829, 3202732235, 3206363778, 3207294280, 3218691622, 3224832477, 3226582088, 3231960701, 3231960825, 3238444781, 3240506687, 3241127686, 3245505639, 3246685420, 3255250502, 3255475289, 3255493270, 3258010725, 3259268259, 3259708744, 3272088211, 3277477189, 3287497511, 3289363789, 3294281816, 3300709686, 3302430666, 3307080284, 3310372188, 3310580422, 3313110325, 3317570505, 3321771963, 3323504524, 3331794938, 3332552236, 3344936763, 3351242611, 3354164541, 3356161036, 3357443896, 3358280978, 3360549707, 3361435146, 3362509089, 3362630778, 3366341181, 3366920760, 3372160500, 3373297021, 3374596217, 3375285141, 3377755895, 3379029866, 3380241983, 3380595728, 3381834713, 3385946526, 3386125251, 3388057612, 3393544563, 3404840083, 3405857857, 3407191084, 3408814815, 3408819560, 3409018494, 3409457570, 3410577155, 3411051814, 3411102162, 3413983999, 3416635233, 3418887913, 3424150275, 3426036948, 3426656604, 3429124e3, 3430316367, 3430320824, 3430870942, 3431771155, 3432731814, 3434192147, 3440930072, 3441289467, 3448289841, 3448536520, 3452859864, 3455445539, 3455973701, 3456106851, 3456282588, 3457601666, 3463597433, 3467469261, 3473077716, 3481649290, 3487446962, 3488816292, 3495434909, 3503723552, 3503962589, 3503975251, 3504086267, 3504111353, 3504116046, 3504274912, 3506277065, 3508805241, 3509081590, 3511319965, 3513566261, 3515728076, 3515960057, 3516630755, 3523519258, 3526432473, 3530287752, 3530798581, 3531066474, 3531601080, 3532265658, 3532567787, 3533680386, 3538145547, 3540002868, 3540019679, 3541120058, 3551826674, 3554146688, 3557238629, 3557288966, 3560409651, 3560721423, 3560755308, 3560772904, 3560776799, 3560843986, 3563273081, 3564677062, 3564681286, 3567399383, 3582031081, 3584271853, 3584286131, 3585048866, 3585049834, 3585528102, 3593775985, 3599378282, 3602300234, 3607509617, 3611661676, 3611790203, 3621964687, 3621965124, 3621966081, 3621966083, 3621968414, 3621969916, 3621970585, 3621975893, 3622095083, 3622538650, 3627671724, 3631197772, 3636965307, 3639447013, 3650032210, 3667545339, 3668394990, 3668555001, 3668632957, 3671699945, 3674122558, 3682693088, 3690182854, 3691035506, 3691048605, 3691317036, 3693068020, 3697923226, 3699114476, 3702342894, 3706900355, 3708334595, 3709045244, 3712703179, 3712728440, 3712733478, 3718845099, 3718930524, 3720827503, 3728968422, 3729352785, 3730027878, 3734185373, 3735541918, 3737224996, 3738382782, 3738387349, 3738389800, 3738389990, 3738390006, 3738390241, 3738390427, 3738394220, 3738394620, 3738394722, 3738394744, 3738394859, 3738396519, 3738397033, 3738399064, 3738400460, 3738887202, 3738887334, 3739466542, 3743223168, 3743289449, 3744330913, 3745299015, 3748385635, 3749221030, 3756564018, 3766265917, 3766587032, 3767014136, 3767872686, 3768672199, 3771941409, 3772113601, 3772128853, 3772772804, 3776028623, 3776032376, 3777321837, 3777702607, 3777706691, 3777840696, 3778052019, 3778877784, 3788596678, 3788641118, 3789096147, 3790949066, 3792555306, 3792675197, 3794434962, 3795445637, 3799396589, 3802359444, 3802425981, 3802900168, 3803509878, 3803533553, 3803824710, 3817195077, 3825134626, 3831783888, 3836226283, 3837130236, 3839963077, 3842564401, 3842605521, 3845461162, 3845489549, 3848928610, 3854658802, 3856336918, 3857323999, 3858008723, 3859684851, 3862352064, 3867966833, 3870049918, 3871085378, 3871829833, 3872291932, 3872427595, 3873740388, 3875975886, 3876231871, 3878080222, 3881750832, 3882302039, 3886373040, 3890622701, 3890644440, 3890892359, 3896043913, 3896689307, 3899279503, 3900747045, 3906847659, 3911916015, 3927826024, 3935292304, 3943337509, 3944324480, 3944448839, 3945529821, 3947301018, 3949488650, 3950159753, 3952494101, 3960241116, 3960376152, 3961917741, 3963099658, 3963421060, 3963723254, 3967007952, 3967259205, 3969124422, 3970612783, 3970678261, 3973713485, 3975040093, 3975243357, 3975693785, 3987058095, 3990704705, 3992681822, 3994071046, 3995478227, 3998971354, 3999298006, 4000670401, 4000993351, 4001099777, 4001277861, 4001735503, 4002465742, 4003357293, 4005356768, 4007925342, 4011050686, 4011066530, 4011075332, 4011273939, 4011552428, 4011788459, 4012217148, 4012217259, 4024186918, 4027830515, 4028975169, 4029110469, 4029583348, 4030423947, 4031498693, 4031499367, 4031499504, 4031509172, 4031928713, 4032208645, 4032479130, 4033316487, 4036743247, 4038287798, 4038545865, 4040900190, 4042024153, 4059950647, 4061045790, 4064482362, 4064482494, 4064686007, 4068398139, 4074270800, 4074270919, 4074308286, 4075674315, 4075712516, 4075885548, 4078878227, 4080178633, 4081049105, 4089654486, 4090206590, 4090679933, 4091412422, 4095259202, 4095274203, 4097043581, 4097047544, 4097047888, 4097050487, 4097053538, 4097079538, 4097094723, 4097094855, 4097218811, 4097289420, 4097298261, 4097355529, 4097358800, 4097358806, 4097359478, 4097365147, 4097365569, 4097368351, 4097368475, 4097373732, 4097381131, 4097390898, 4097493023, 4097494448, 4097500420, 4097504860, 4097508952, 4097518447, 4097523657, 4097528230, 4097528249, 4097565588, 4097595928, 4097769515, 4097769660, 4097770040, 4097900631, 4097993352, 4097993363, 4098078311, 4098093255, 4098096816, 4098101881, 4098102013, 4098120408, 4099257624, 4099391059, 4100119818, 4101141701, 4101990706, 4102099355, 4102141580, 4102295291, 4103385373, 4104416776, 4108421678, 4108481771, 4113654278, 4120143040, 4120573143, 4120685305, 4120832270, 4121323786, 4122797449, 4123137490, 4123141719, 4123166778, 4123237466, 4124517918, 4124852870, 4126190390, 4126265264, 4126330058, 4126584791, 4128561486, 4130538182, 4130665595, 4135804702, 4138805004, 4138959002, 4142649353, 4143010615, 4143011353, 4149276818, 4149741566, 4155964946, 4160851306, 4165043845, 4165602674, 4166101816, 4168666626, 4168671212, 4169534192, 4169538416, 4175499442, 4178182706, 4179726175, 4180321577, 4180398911, 4180437564, 4180584501, 4180592595, 4180655876, 4182610142, 4190427894, 4190436241, 4190438903, 4190464587, 4190536489, 4191350062, 4197904504, 4208748285, 4213114634, 4213114766, 4213115878, 4213133169, 4213139443, 4216213600, 4229539334, 4230260404, 4236039784, 4239211903, 4244301284, 4244359264, 4244636840, 4244650461, 4244697370, 4246504751, 4248927363, 4249781266, 4250093591, 4255547342, 4269915810, 4271230391, 4273205904, 4280822506, 4281987205, 4281991429, 4288642117, 4290818353, 4290862694, 4290938088, 4291163255, 4291519114, 4292375442, 1661, 113029, 2431109, 6154799, 9085905, 10454523, 11833936, 15005411, 29369909, 32348563, 32392946, 34831997, 35241656, 35407085, 41719852, 42040525, 44148994, 49751269, 54657448, 54829135, 61297674, 64616140, 64792746, 65243007, 69912355, 73497087, 75564691, 84754216, 85474843, 88950783, 95227810, 97671606, 97869711, 98556036, 111529024, 112714201, 113832573, 118457586, 119013459, 129204800, 129504899, 132934253, 133576354, 140643360, 141325108, 142928709, 144351849, 147399388, 148485881, 153516070, 159755595, 162751717, 164324227, 164324569, 164324664, 164324791, 165338893, 166383271, 169804649, 169909381, 170281316, 170281555, 170281599, 170281747, 170281951, 172221532, 172281217, 172281369, 172281601, 172281701, 172281789, 172615247, 173287589, 173592416, 173930363, 174306514, 176844018, 177079695, 177080427, 177147671, 177148014, 177148020, 177546706, 179139641, 179379875, 179569944, 180056941, 180259371, 181198501, 181205574, 181240422, 181950714, 182150488, 183096195, 183096537, 183468910, 183469260, 183603589, 183603872, 186043176, 187501046, 187763081, 189174183, 190912115, 191031927, 191069908, 191103669, 193357074, 193420201, 194024818, 195040318, 195040605, 195184107, 195615400, 195788148, 195817845, 196491587, 197098180, 197098196, 197098242, 197098387, 197098716, 197098773, 197098834, 198219090, 201802654, 202716248, 203969128, 204000291, 204003102, 205844479, 208540214, 211702237, 213315812, 216404638, 221220686, 223633303, 225036633, 231228447, 233832515, 235585683, 236122625, 238349947, 244953360, 253603556, 254556119, 268305044, 270508724, 279497384, 282260013, 286117940, 288337735, 294222691, 294944592, 297796540, 299806932, 301175958, 305396028, 309814229, 316711416, 319659866, 321667918, 323472705, 329290740, 333539694, 336073493, 344556873, 345150446, 346582968, 348240977, 352942917, 361618841, 362641227, 363650316, 368392429, 371447569, 379018060, 379803748, 381683792, 382346929, 390037588, 392534911, 393050977, 398079720, 401473592, 404580880, 408076405, 409551689, 412923104, 413523569, 417762611, 418389794, 418643706, 419980117, 420076057, 430774757, 431420666, 431463230, 433047970, 433759801, 433960232, 434424313, 439016491, 446595824, 448347366, 453082265, 459993498, 461991320, 467355959, 468677861, 471325996, 478194174, 481007914, 483933287, 492579864, 492820046, 493239087, 495294245, 500922416, 501132892, 502571724, 503870109, 505520155, 505540840, 505547348, 507674743, 507704542, 508155006, 508732896, 524499536, 528698966, 533082472, 536472645, 536606854, 536706420, 544035780, 545100578, 547829195, 548068662, 550157112, 554422931, 557980541, 558904957, 566123574, 569085212, 574052622, 575078226, 579214441, 582810837, 583362052, 583453417, 594063106, 598128236, 601157755, 601161740, 601948346, 602413319, 603986209, 605582466, 609198625, 610045978, 617827459, 620396524, 626039263, 626988485, 629099694, 630452394, 635400744, 640415961, 643558590, 645257576, 652659119, 656273907, 665354414, 666296511, 667333922, 668403785, 669730879, 669929645, 674175725, 680972003, 682364285, 684524418, 689215333, 704136516, 706383966, 708808466, 710978465, 712995495, 713788357, 717519098, 722655660, 722956329, 725449644, 727209749, 729977159, 734622016, 735035205, 737142807, 737152212, 737166334, 737644692, 737837074, 739516787, 739566545, 739985822, 741816033, 742252614, 742260586, 745092996, 747930588, 750219296, 750508933, 752522257, 753857751, 754000708, 757807602, 758478444, 761228031, 762067870, 762641736, 764248075, 764320946, 764825188, 766296725, 766355544, 766643209, 766774330, 767540529, 772363084, 774299734, 777688891, 787564577, 792068311, 792844833, 796739176, 800010738, 800087019, 809563086, 810061706, 810813298, 811092091, 817847511, 819009519, 826260124, 833658992, 834470340, 839856739, 842147301, 847675799, 861294299, 862950715, 867021650, 867036335, 867732810, 870151875, 874296659, 875096251, 875944810, 876149555, 884498580, 887482102, 893652881, 894264732, 896104248, 896979123, 897240751, 902139830, 911653942, 912249299, 919599881, 927052135, 933697266, 933746041, 939098524, 939114841, 948752149, 955130439, 955354780, 955942299, 956480228, 958121442, 966830075, 970076747, 972273212, 976381303, 978919739, 981829565, 984418838, 997412732, 1001458257, 1001637783, 1001651627, 1005191377, 1008948875, 1010556097, 1016009727, 1016348317, 1023879932, 1024317101, 1027786481, 1027856392, 1032266307, 1033049924, 1035709107, 1038486906, 1041294385, 1043437244, 1049779946, 1051535617, 1053737172, 1054370922, 1056150770, 1056645919, 1056720884, 1063952736, 1064732809, 1064857294, 1065290596, 1079732589, 1080478458, 1081536009, 1086069586, 1088535269, 1094421058, 1095718313, 1096687866, 1100372480, 1101043104, 1102004406, 1104733017, 1110237878, 1112959177, 1113096701, 1114972095, 1117545303, 1118952562, 1125668821, 1130216203, 1132104794, 1132534664, 1132579070, 1132598106, 1135683888, 1135892328, 1136018325, 1138287902, 1145147923, 1145899518, 1146523166, 1149204820, 1151262913, 1152056864, 1154446700, 1154536715, 1154542665, 1155367440, 1155994599, 1161218045, 1164964007, 1166033123, 1167024992, 1167025137, 1174582808, 1174756828, 1179004234, 1181469438, 1183829925, 1186977866, 1187653498, 1188208310, 1189560180, 1191923730, 1192723278, 1195304992, 1199133859, 1199554249, 1199600208, 1202045876, 1204911535, 1208429990, 1210779948, 1210807525, 1220586092, 1221782335, 1221920801, 1236932222, 1238449939, 1243532105, 1246474378, 1257626414, 1257876060, 1258346504, 1259689738, 1260820433, 1260836076, 1261324364, 1265824053, 1266641105, 1268763191, 1271531819, 1273274467, 1276658942, 1282928227, 1283757717, 1290537388, 1296032318, 1296235125, 1301946320, 1305140481, 1308857550, 1310807544, 1310899277, 1312163653, 1316036626, 1316125796, 1324285266, 1324310094, 1324331646, 1324337571, 1324579984, 1325750278, 1326569216, 1333842476, 1349684561, 1351415139, 1351558342, 1351862653, 1351880550, 1354386923, 1356250756, 1356331589, 1357629674, 1362165018, 1363404812, 1364008114, 1364487272, 1365133140, 1365599531, 1365623138, 1366730785, 1366987615, 1372241226, 1372705460, 1372794328, 1373871548, 1375834117, 1377641421, 1378253217, 1385859280, 1391291390, 1391293134, 1391299074, 1391300548, 1393577155, 1394469288, 1394469303, 1394469473, 1394469866, 1394470005, 1394470066, 1396870772, 1399867662, 1410090536, 1413067533, 1423547895, 1430902259, 1431096661, 1433581041, 1435770227, 1436788950, 1441443055, 1441473969, 1443172426, 1444705872, 1444722875, 1444727957, 1445594238, 1447082963, 1448082324, 1455246557, 1457519039, 1458084479, 1458493639, 1458555099, 1459794391, 1460930084, 1465058743, 1465974914, 1465976327, 1465976425, 1465976436, 1465976550, 1465976555, 1465976625, 1465976632, 1465976696, 1465976747, 1465976870, 1465976979, 1465976985, 1465976986, 1465976991, 1465977196, 1465977261, 1465977271, 1465977274, 1465977303, 1465977323, 1474444421, 1478092049, 1478716185, 1481566528, 1482522967, 1489772937, 1494181387, 1504535254, 1509029106, 1510450262, 1511907991, 1515598870, 1519441587, 1522685369, 1525831150, 1526085253, 1527459723, 1529619411, 1532042759, 1533712942, 1537663939, 1539902893, 1541073018, 1541496652, 1542773859, 1549199388, 1549209224, 1549210203, 1552284203, 1553692884, 1555806428, 1561102750, 1568416773, 1570561776, 1573188605, 1576134740, 1582406800, 1582529544, 1585380899, 1587251606, 1592687509, 1594093747, 1601662530, 1602151715, 1602222565, 1602416912, 1604312683, 1604313702, 1604341906, 1605478605, 1610069144, 1610724928, 1613430619, 1616149762, 1616623247, 1616826805, 1622345684, 1624120544, 1624575040, 1631446240, 1634840328, 1635306209, 1637735434, 1639041637, 1643893360, 1645239134, 1645714411, 1646967505, 1647763648, 1648026812, 1648459154, 1652482428, 1654623339, 1659538076, 1660752253, 1661285202, 1662950537, 1675032552, 1676328914, 1681382184, 1682444281, 1683407715, 1684605451, 1684964181, 1686375531, 1686572406, 1686834359, 1687225102, 1687228988, 1687238599, 1687241697, 1693905970, 1693924649, 1694678234, 1696017211, 1697022103, 1698247372, 1700196518, 1700874190, 1702743585, 1705191422, 1705572464, 1705775316, 1708553688, 1709604401, 1711224201, 1712893263, 1713051167, 1713095897, 1715999558, 1716074224, 1716087943, 1716947524, 1721557559, 1722492001, 1723859941, 1728197301, 1730461660, 1732377833, 1740500925, 1740503023, 1747349646, 1747349737, 1747349747, 1747349811, 1747350242, 1747350353, 1747350383, 1747350483, 1747350570, 1757625214, 1758838683, 1759487629, 1759488516, 1759498393, 1759499821, 1759502442, 1759502966, 1759512274, 1759512283, 1759513528, 1759514495, 1759514515, 1759516437, 1759524172, 1759589336, 1760335250, 1762975960, 1762992044, 1763004314, 1763977119, 1771261987, 1772061961, 1772164204, 1782043531, 1789421301, 1792792037, 1793905730, 1800839994, 1801396125, 1804673412, 1806579373, 1807671676, 1813955111, 1814430790, 1816595094, 1817436421, 1822787251, 1823557150, 1828043124, 1839996532, 1839996844, 1841030555, 1842560365, 1844448916, 1844480213, 1846724376, 1860761623, 1861064328, 1863000850, 1867708596, 1873773882, 1874142716, 1875798230, 1880233189, 1882601503, 1885862630, 1890372289, 1890379225, 1891031342, 1891205640, 1891938925, 1894539933, 1896919160, 1896919227, 1896919294, 1897898461, 1899147627, 1900573373, 1901379444, 1902628941, 1905060165, 1906789934, 1906790006, 1906790139, 1906796594, 1906797455, 1906801573, 1906801694, 1906806837, 1906810233, 1906810485, 1906811690, 1906817274, 1906818921, 1906820915, 1906820924, 1906823423, 1925206882, 1927020241, 1928994e3, 1935386784, 1936188797, 1939298330, 1939994885, 1941474619, 1944071536, 1945201987, 1946130305, 1946324244, 1947055740, 1949193282, 1951127334, 1956200886, 1960661844, 1964294607, 1971670426, 1975660003, 1977074332, 1979063800, 1986972074, 1987660949, 1991785763, 1992080509, 1995174355, 1995890751, 2001507875, 2004488903, 2015900220, 2018783243, 2021213332, 2023260368, 2025018361, 2025037989, 2025039155, 2026543248, 2027114414, 2027551630, 2034028822, 2034497157, 2034927376, 2035815698, 2037403782, 2037552632, 2038238057, 2038463378, 2038609522, 2039595722, 2040354520, 2040943501, 2041028464, 2044842550, 2047340057, 2047377876, 2047791608, 2047824538, 2050823774, 2050838609, 2051525062, 2051827668, 2052255777, 2052901511, 2053206810, 2053240934, 2053337172, 2053478875, 2053493456, 2053853373, 2054449324, 2055229681, 2055578022, 2056180496, 2057710300, 2058751811, 2059048621, 2061275137, 2064241908, 2066721635, 2067699997, 2071301924, 2075934693, 2077460241, 2077463931, 2082279457, 2082350395, 2082490504, 2083899515, 2084905908, 2087506861, 2087556005, 2087568425, 2087595516, 2092046651, 2092301721, 2097381010, 2097529923, 2100199727, 2103470828, 2105481502, 2107063121, 2107436658, 2111314048, 2113664954, 2116750738, 2117068897, 2119040128, 2122563214, 2122618177, 2124668692, 2132271390, 2134191641, 2134715695, 2138049165, 2138494997, 2144770101, 2151094932, 2151644274, 2163712208, 2163898589, 2170508442, 2176727539, 2177318798, 2178944930, 2179027416, 2184528600, 2186571792, 2187374596, 2190645414, 2190660247, 2190897184, 2195413098, 2195424198, 2198290764, 2203121973, 2208876632, 2209276004, 2211529485, 2216861598, 2219976143, 2224936471, 2229389306, 2229428098, 2233205867, 2235535537, 2238302643, 2241998064, 2243922068, 2245744882, 2246095470, 2246624423, 2249578444, 2251500542, 2256423319, 2257131811, 2259407586, 2265403416, 2277922362, 2278366865, 2281444864, 2283990470, 2284221844, 2290521795, 2298483014, 2298859942, 2303709693, 2305684069, 2306183534, 2310688315, 2315634657, 2319104481, 2323978889, 2326416557, 2327685947, 2331542577, 2334488740, 2335980755, 2343955873, 2343987387, 2344051572, 2344081298, 2353017729, 2357782940, 2360233424, 2365749167, 2372460029, 2372478071, 2376327406, 2380959235, 2384339112, 2391410598, 2392072803, 2393811335, 2399346319, 2399822664, 2401643245, 2401782259, 2403261116, 2407789481, 2409182571, 2417084170, 2417165267, 2417652035, 2419411749, 2419417423, 2422324904, 2423117096, 2424431334, 2424771770, 2432634086, 2435584133, 2436015021, 2441679501, 2441854846, 2444838503, 2451024601, 2451094457, 2453483137, 2453497460, 2454448917, 2456215407, 2459247176, 2463271525, 2463506842, 2467234433, 2469945372, 2473920266, 2485004952, 2486666796, 2489018185, 2489169796, 2490847830, 2492077342, 2492970238, 2497220049, 2503042985, 2512844015, 2518379243, 2518777282, 2525588137, 2525608018, 2528358668, 2528706848, 2531896313, 2536602755, 2539686262, 2551310943, 2554772601, 2556085817, 2558131228, 2564231467, 2565836498, 2569358076, 2571159128, 2572746788, 2575905107, 2579846032, 2582295686, 2585286228, 2585297154, 2587884409, 2590263013, 2592032772, 2597156358, 2600208325, 2600311538, 2609976564, 2614031703, 2619619987, 2622453927, 2622601193, 2622997773, 2630676340, 2635726130, 2636739119, 2637611531, 2637745410, 2637827916, 2639832942, 2646831691, 2652889161, 2656916375, 2658971428, 2660417858, 2667387895, 2669967601, 2671812960, 2675377616, 2680331975, 2692646873, 2694622232, 2697812844, 2707358863, 2708256980, 2708843581, 2721005193, 2723132333, 2723449219, 2727613517, 2729386864, 2732129495, 2738025026, 2739504392, 2742067873, 2743561936, 2745053658, 2748129339, 2755346949, 2756835810, 2762308724, 2762732310, 2772048233, 2773342582, 2773916239, 2774237802, 2777215669, 2780442125, 2780969136, 2784038323, 2786612080, 2787145966, 2787151566, 2791623281, 2792656912, 2793820597, 2793843165, 2794535853, 2794558276, 2794571602, 2794589073, 2794607684, 2794781905, 2794812897, 2794904579, 2795201682, 2795215251, 2795316793, 2795413889, 2795489178, 2795518714, 2795546979, 2795547152, 2795551511, 2795554576, 2795555553, 2795567189, 2795581043, 2795588603, 2796767057, 2797512177, 2798111293, 2798512509, 2799526810, 2799947922, 2802973072, 2804403738, 2804874542, 2805637755, 2805753744, 2812187177, 2812916202, 2815541885, 2820491263, 2822394574, 2829422945, 2831048350, 2832237259, 2834623189, 2837348717, 2839650695, 2840525902, 2841159353, 2842490055, 2844781614, 2846385194, 2846982791, 2849248490, 2849860412, 2850213786, 2852028874, 2852573181, 2854701866, 2855519660, 2857974075, 2859686627, 2864766480, 2865932173, 2873369054, 2873382924, 2877054650, 2878248977, 2880150758, 2882016813, 2894321712, 2896549226, 2900972274, 2907164383, 2909422460, 2910191497, 2912050734, 2914081458, 2914744694, 2914938714, 2915009556, 2917041430, 2918571873, 2929237742, 2931708704, 2933052029, 2935350303, 2939956665, 2941858877, 2943539162, 2944562948, 2945364171, 2947166646, 2953041500, 2953600606, 2958695479, 2959025464, 2963193938, 2963907974, 2964323647, 2969439522, 2972958854, 2976622717, 2978201778, 2982085395, 2985605450, 2996423818, 2999691650, 3008190733, 3008855969, 3016122305, 3017646001, 3023766416, 3029366772, 3032047068, 3036119914, 3036992672, 3039024727, 3042813479, 3043904968, 3050467218, 3051886594, 3053067553, 3056188564, 3057812794, 3065938060, 3066185554, 3067801157, 3067842181, 3068762275, 3077857486, 3080857101, 3087114209, 3087935921, 3088190003, 3089015336, 3091255985, 3095401268, 3096813247, 3098725318, 3105671535, 3117883740, 3118052513, 3118932015, 3119183299, 3121944857, 3124496054, 3126706525, 3129135980, 3130262956, 3136193853, 3140687365, 3146277579, 3150523560, 3154412692, 3159557566, 3164499075, 3164706839, 3168577861, 3173559921, 3174529089, 3176196996, 3176871024, 3180039849, 3180784320, 3181226348, 3184223807, 3185392090, 3186278865, 3187205025, 3189849017, 3192015124, 3201052817, 3206103617, 3212240200, 3229338204, 3231038915, 3232995840, 3236363663, 3236684869, 3240062262, 3241501460, 3243217472, 3245554401, 3249410406, 3254464708, 3257959952, 3274402918, 3276160836, 3276181105, 3276196901, 3278107133, 3290502878, 3291450742, 3293286977, 3293297241, 3296419295, 3299472058, 3299767442, 3301223392, 3301309499, 3301391192, 3304599725, 3306064327, 3313552392, 3321637504, 3331885553, 3332277580, 3333914252, 3337182013, 3337858974, 3341471161, 3342158460, 3346063476, 3347209717, 3350345047, 3350816321, 3351869587, 3352060268, 3355691995, 3356175586, 3356927752, 3362723114, 3366755503, 3367073048, 3367944003, 3372319994, 3375346812, 3376868662, 3381262072, 3382258705, 3385088233, 3389287501, 3392485763, 3403435361, 3403782237, 3406109171, 3406111906, 3407122639, 3411575670, 3424242744, 3426100153, 3426523263, 3431675506, 3431798787, 3432725491, 3433958809, 3443103158, 3445734210, 3450482982, 3453219838, 3455171543, 3455975626, 3458629656, 3459326184, 3460835389, 3468111852, 3471910127, 3473608107, 3474158466, 3478804050, 3479897537, 3480605972, 3480868929, 3481097537, 3485240025, 3491815953, 3492209950, 3494777461, 3500328283, 3503925212, 3506796962, 3514565086, 3514565812, 3518469610, 3519718992, 3519725933, 3524188747, 3529349528, 3542452078, 3546487756, 3550700124, 3550989552, 3551573749, 3553442167, 3554781799, 3556847596, 3557221487, 3557691349, 3558264087, 3560824248, 3563344816, 3565186253, 3565418379, 3566074326, 3568626956, 3569886279, 3570187564, 3574536814, 3576593305, 3584104748, 3586564634, 3588013803, 3590119076, 3594126223, 3605649145, 3607964178, 3610130320, 3611466472, 3615937331, 3618863110, 3629119210, 3629792790, 3635135986, 3635459541, 3636074310, 3638424639, 3640911628, 3642130958, 3642225062, 3647798063, 3656108419, 3657615451, 3659534155, 3659611370, 3659667263, 3660545348, 3660867367, 3671487562, 3678946749, 3680027665, 3684023399, 3686613485, 3686646984, 3691543485, 3691543777, 3694814128, 3695175653, 3698130051, 3700803863, 3704722354, 3717443225, 3718851041, 3722297297, 3724304421, 3727535579, 3735382080, 3740438523, 3740440657, 3745910284, 3748112414, 3748157778, 3751765724, 3751843037, 3758548269, 3759175702, 3760229117, 3767579376, 3767636566, 3774416951, 3774620406, 3775107448, 3777554302, 3784459817, 3789001045, 3789217359, 3790213466, 3791430232, 3792756850, 3797275201, 3797334865, 3797547975, 3797752814, 3798120765, 3799727891, 3800284920, 3803890887, 3807736858, 3811590943, 3812650457, 3813081457, 3814583456, 3816238011, 3818244185, 3820433217, 3821631768, 3824973847, 3830752599, 3831121452, 3831131041, 3837373870, 3839962587, 3842157165, 3849728326, 3849729892, 3849734551, 3849787726, 3849792721, 3849819373, 3853184002, 3854490492, 3856121458, 3860607422, 3861431943, 3861926244, 3867504094, 3869648625, 3871255217, 3879613384, 3888702999, 3902486573, 3909678524, 3911290870, 3914258422, 3919568627, 3924938673, 3928836058, 3929271846, 3932881151, 3932899585, 3934007962, 3942901813, 3950379841, 3960912026, 3973890763, 3976040035, 3981060932, 3981985710, 3991078309, 3992022849, 3992259208, 4010941807, 4012569891, 4013412307, 4021161495, 4025854722, 4027536004, 4033312623, 4036094574, 4037300319, 4043405137, 4048222256, 4048420974, 4049948378, 4051811237, 4052267313, 4054558966, 4064836207, 4066383490, 4070580503, 4073707968, 4100786237, 4104807039, 4115427659, 4116271014, 4117626035, 4127381498, 4128299636, 4132054341, 4132795027, 4133480683, 4136878052, 4138452493, 4138537192, 4138587115, 4138850346, 4138930624, 4148483014, 4149140792, 4149626272, 4149641566, 4149809179, 4152090640, 4152153727, 4156628388, 4159166567, 4161006924, 4161031359, 4166727800, 4167095051, 4168702437, 4168921085, 4175490343, 4178043127, 4179607399, 4182917435, 4196816243, 4201195770, 4201710836, 4204344500, 4212065046, 4216249688, 4218603456, 4220181346, 4230252988, 4230808631, 4235216564, 4245730359, 4250048329, 4251017064, 4254397175, 4261049438, 4265986719, 4266150865, 4270257086, 4272517612, 4285995571, 4287809158, 4287924367, 4293141634, 4293320049, 7, 171252454, 314658260, 1911007288, 2310391087, 2705648135, 3085052283, 4199583372, 0, 0, 33, 66987915, 366428436, 366991379, 487687151, 649399193, 716916462, 900018457, 911616432, 914855142, 981141093, 981156754, 1068454171, 1213136917, 1357549542, 1437166305, 1491010671, 1491010869, 1599218285, 2035443912, 2098925819, 2412701058, 2447973967, 2572472237, 2572499572, 2572504631, 2734871983, 2873757688, 3147193074, 3229893628, 3613204738, 3628727675, 3840638318, 4020469118, 1985, 3609572, 4707302, 4731941, 7066741, 12732264, 12733869, 12874473, 12898727, 15239865, 15443925, 15464989, 17770158, 18806137, 22641470, 34805542, 37254453, 38352510, 47103897, 47124528, 47160482, 47264668, 47270558, 47521880, 47670735, 47682584, 48206184, 54052064, 55399270, 55790429, 57861540, 64629239, 65951659, 73540622, 74816563, 79005572, 79010572, 79432449, 79977826, 80960607, 90941114, 91781471, 93732497, 101061895, 101792620, 105281118, 114635485, 121111459, 126395821, 127613999, 134819976, 135124399, 135156325, 135512978, 139443164, 140195744, 146403274, 147165318, 147311351, 147680945, 154712981, 156193153, 157683252, 162021680, 165184869, 165682351, 167795310, 169177047, 169285407, 170248114, 175536255, 176298648, 181584625, 186190871, 188366635, 190461039, 190805290, 190817793, 191644192, 193330267, 200367649, 204872798, 208246903, 213994908, 222038678, 222914983, 226753977, 227658815, 230657663, 231976681, 232418677, 234224516, 235125560, 235385397, 235630461, 235880887, 236100347, 237106084, 237695302, 243768879, 244905302, 245221564, 245221621, 245248688, 246957980, 247379872, 247404538, 247547714, 249186148, 249832804, 250298968, 252007821, 252166643, 254498243, 256250975, 256734086, 257675257, 258276240, 260078806, 269653037, 270614174, 270803459, 279865482, 290747254, 296104342, 296106331, 296214241, 297365588, 297388265, 297388314, 297395043, 297872731, 297875338, 305678573, 310113063, 317059542, 318726251, 320983337, 321380700, 329390871, 340233049, 343985311, 368331859, 368339983, 374202536, 374729119, 377042975, 377218502, 377330983, 379160277, 387137528, 390536878, 397426025, 410462833, 410898354, 411028646, 415359567, 418289923, 418809394, 420699727, 422768411, 423087664, 434374676, 434499530, 439966930, 443910462, 444881445, 446735168, 470802373, 473022090, 475752042, 480190019, 481797890, 482141996, 493334140, 493996949, 494002753, 494111972, 496668263, 497004637, 505642028, 513006918, 520166698, 522732652, 524323805, 524791178, 525296785, 532366388, 537994409, 538156652, 539123093, 539125333, 540384923, 545724556, 546598380, 552815312, 564847266, 572585472, 572589595, 572660745, 572917514, 572938118, 581295982, 583116728, 584477771, 585356786, 585510953, 586974440, 588341431, 590260151, 593171510, 600861600, 602587622, 608185550, 608501e3, 611172806, 617227910, 620862123, 625412750, 626878575, 627192073, 628675473, 636454657, 644892435, 645708934, 646772532, 650376939, 653264074, 653865504, 654835286, 655274400, 657684596, 657843927, 665654464, 665772443, 667917050, 667982163, 668803663, 678409190, 685972429, 687873546, 699223116, 722349553, 723381066, 723506578, 725289629, 728910939, 728916446, 729301272, 730375222, 731520837, 731524865, 731524893, 733458327, 734942836, 742063133, 744425628, 745118723, 750501894, 753379261, 753585532, 755936840, 755999442, 757164322, 757742871, 758908039, 758927262, 766978617, 767310694, 767319597, 768502512, 775086059, 775783015, 776818569, 777129529, 782249017, 782470551, 782586541, 783225086, 783819749, 787058931, 793173186, 793643539, 793791572, 794069868, 797737785, 801549019, 805476735, 809560577, 810471911, 810660018, 813069363, 813965189, 814609400, 819689086, 822265343, 827811881, 828807618, 840895172, 842670706, 845178939, 849626506, 857304293, 867054787, 875581912, 878480613, 878489001, 888652626, 892902192, 904040802, 904780949, 904781069, 904781208, 904781211, 904781269, 904781270, 904781407, 904781445, 904781469, 904781569, 904781597, 904781741, 904781750, 904781797, 904781798, 907680375, 909542970, 913350787, 915552624, 943105427, 944616168, 945567936, 946059164, 946112067, 950116031, 950459761, 950797941, 950991772, 952407653, 954708706, 954904735, 956279390, 959296218, 959317553, 960000436, 960088334, 964474682, 965248297, 965252181, 968600148, 969495568, 969714387, 969714391, 969714751, 975014436, 976847064, 977515724, 978655375, 985441466, 985451059, 988676432, 989199112, 995754553, 995754557, 998100773, 998582596, 1001682227, 1002897238, 1005026102, 1007267340, 1018029509, 1019292109, 1021170671, 1021615491, 1027478448, 1027904949, 1028176876, 1028524011, 1033544761, 1037073656, 1039464298, 1041396131, 1043364491, 1051084878, 1053049944, 1055328538, 1055480209, 1058862972, 1066609925, 1068948457, 1071874351, 1072134738, 1082834847, 1084511341, 1087693738, 1089012798, 1089634494, 1093384439, 1093825560, 1094815391, 1098082937, 1102471353, 1113642022, 1113846049, 1121249692, 1127953536, 1132317159, 1132485954, 1132585385, 1132689597, 1132723356, 1132858392, 1133501028, 1133636064, 1134046361, 1134351151, 1134824033, 1135467502, 1135737574, 1135775689, 1136782059, 1136883336, 1137085890, 1137173922, 1138138823, 1138714596, 1139072942, 1139153897, 1139221159, 1139981182, 1140405028, 1140510661, 1141246959, 1141280718, 1141381995, 1141584549, 1141719585, 1141874653, 1142159541, 1142193300, 1142260818, 1142366610, 1144440814, 1144457023, 1144667374, 1144802410, 1144975561, 1145579956, 1145625081, 1147135141, 1147314976, 1148184718, 1148522564, 1149131059, 1150514349, 1150729533, 1151393172, 1151494449, 1153073825, 1154465661, 1155177503, 1156094385, 1156940664, 1158572559, 1160038984, 1160487168, 1161167906, 1161578459, 1161965872, 1162013821, 1163255421, 1163472226, 1163645377, 1163777146, 1163979700, 1164916562, 1165010690, 1165068597, 1165937726, 1165940993, 1166410608, 1167096330, 1167193469, 1167260731, 1167598577, 1169823858, 1170720439, 1171147706, 1171150005, 1180230175, 1180849387, 1188216287, 1188228500, 1188701654, 1190334387, 1190352716, 1190641324, 1202600586, 1206718941, 1209302133, 1214814043, 1216095517, 1220486075, 1223892937, 1224444732, 1225577971, 1229986049, 1243738793, 1247471306, 1252266596, 1252792940, 1253960230, 1254127330, 1255848785, 1255859538, 1257563663, 1257583343, 1258195056, 1258213434, 1262993336, 1263908042, 1265512654, 1267283463, 1278475387, 1281229947, 1281889125, 1284797630, 1288585218, 1290240457, 1290513099, 1293031053, 1295516865, 1297095740, 1297597617, 1298827289, 1298832842, 1299380998, 1300818337, 1304310342, 1304455504, 1310534169, 1316956180, 1336232039, 1337809090, 1340075459, 1343684265, 1347737800, 1348149256, 1354685816, 1355025196, 1357282216, 1357301365, 1363667295, 1364395531, 1364732891, 1373278040, 1373514813, 1373685873, 1375205051, 1375419602, 1376146087, 1380234474, 1380513046, 1381723825, 1382632688, 1382645602, 1382709874, 1386126578, 1388184353, 1389190819, 1389902309, 1389912616, 1390104485, 1390958270, 1391687090, 1391699393, 1393151104, 1395748391, 1395924208, 1397018707, 1397022500, 1397827261, 1398423514, 1400330808, 1401462671, 1410284129, 1411428439, 1412479074, 1412717811, 1412831927, 1420822802, 1423109435, 1423890423, 1424552007, 1425040900, 1428131728, 1431817030, 1431897749, 1433480127, 1433483767, 1434457973, 1451286836, 1451565010, 1452211848, 1452224159, 1455851258, 1458060161, 1458176029, 1458620255, 1463365872, 1466302404, 1472319400, 1475303091, 1484355552, 1486115226, 1486401243, 1489893113, 1490054949, 1492145100, 1494001659, 1494630697, 1494690535, 1494695213, 1494714660, 1494714786, 1494714930, 1494889015, 1494990523, 1494992680, 1494997876, 1495466906, 1500014997, 1502962162, 1504548128, 1505655813, 1508029184, 1508045454, 1509815249, 1518807662, 1524160328, 1529373691, 1536802563, 1538089784, 1539586715, 1544812783, 1547140470, 1552392687, 1552405115, 1552405169, 1553111822, 1553462237, 1554120313, 1554158027, 1555241094, 1555436471, 1555595989, 1556675361, 1557492455, 1557696008, 1558835738, 1558865070, 1559582938, 1559928005, 1561078602, 1565016185, 1565113430, 1565407826, 1568314306, 1568314316, 1568317266, 1568696751, 1568699472, 1568940804, 1569248185, 1570879860, 1573625992, 1573800670, 1576869802, 1581247153, 1581398717, 1581675892, 1581718434, 1583510121, 1583803496, 1588886160, 1595292826, 1602148307, 1605015374, 1609481646, 1612153257, 1618209596, 1618218864, 1618873873, 1619384363, 1624861042, 1630153983, 1638526919, 1639454708, 1640524262, 1641042489, 1641812886, 1647303548, 1648240296, 1650468220, 1650500409, 1651513056, 1658862087, 1658979753, 1661301475, 1667470132, 1667473335, 1667728240, 1667806132, 1677105623, 1680875001, 1680882207, 1681660610, 1685495090, 1685495093, 1685495270, 1685495398, 1688394353, 1688567575, 1688665455, 1688778883, 1690751126, 1691125863, 1693300755, 1694472929, 1703388735, 1709297356, 1709313729, 1712511978, 1715661089, 1717927392, 1718114956, 1721373840, 1722360575, 1724823399, 1726408681, 1726606395, 1726645504, 1732927910, 1736066754, 1736347741, 1740486766, 1742215384, 1745377406, 1758824175, 1758930481, 1758975612, 1759122505, 1759143730, 1759143733, 1759227293, 1759313682, 1759313685, 1759412017, 1759432510, 1759498975, 1759505228, 1759507354, 1759515800, 1759642661, 1759864276, 1759893786, 1760159824, 1763810143, 1766750547, 1769211545, 1769618102, 1772590156, 1775156822, 1780760274, 1783870720, 1784406502, 1786353732, 1793007575, 1811810046, 1815656403, 1816569647, 1816866992, 1822574126, 1822868024, 1822868031, 1823268852, 1823275309, 1823288115, 1823390804, 1823768300, 1833535991, 1842420860, 1844031908, 1844296341, 1844524436, 1844853963, 1845272265, 1845433501, 1850725233, 1851761689, 1851765614, 1852766386, 1853687691, 1854177922, 1861204803, 1863593250, 1872674263, 1872992134, 1873841021, 1877281407, 1877305076, 1881597618, 1884316146, 1886743174, 1887188539, 1892879921, 1905997196, 1912353097, 1916296381, 1919640688, 1919643810, 1924325687, 1935798204, 1935801369, 1935813711, 1935815187, 1935818499, 1941710024, 1944260378, 1945210145, 1951157591, 1955955663, 1957378415, 1957388660, 1957444069, 1958153525, 1958153878, 1962799016, 1964448624, 1967235715, 1967514117, 1968334692, 1970709900, 1974828022, 1977445003, 1980811473, 1981302481, 1984866213, 1986874949, 1987285901, 1987558613, 1988913069, 1998855379, 2023930736, 2026542768, 2029442974, 2029502301, 2031253491, 2041190670, 2044176332, 2044519717, 2044521677, 2044845895, 2044862336, 2050748464, 2055299797, 2059226128, 2060744697, 2060874008, 2061631935, 2062602594, 2062613436, 2062713055, 2062721365, 2062782118, 2064194523, 2064289093, 2064667157, 2064835977, 2065546931, 2065580690, 2065783508, 2066019598, 2067177842, 2067640249, 2068518016, 2068619301, 2069026672, 2069773511, 2070805664, 2073324624, 2075547993, 2076314666, 2076760108, 2076927096, 2078661044, 2080078919, 2080126248, 2080270176, 2080768362, 2080948565, 2081049148, 2081811414, 2082081519, 2083365940, 2084275182, 2089789238, 2090043919, 2090165361, 2090287045, 2092471497, 2092773191, 2093281591, 2093290649, 2093484170, 2095261287, 2096596043, 2096775591, 2100685312, 2102866955, 2108433077, 2109903284, 2110249550, 2112026046, 2112754908, 2114424326, 2115251185, 2116737470, 2118764990, 2119510407, 2120903194, 2121183749, 2121530494, 2121539444, 2122085862, 2123968241, 2123974461, 2124038667, 2126585211, 2127702833, 2127711196, 2129393172, 2140172366, 2141043403, 2144163444, 2144352359, 2146552134, 2146559400, 2146579609, 2146771534, 2146787712, 2147192784, 2149214372, 2150227387, 2151276842, 2152677197, 2158829447, 2159124528, 2159550475, 2161337980, 2161361535, 2163722410, 2163917836, 2165826914, 2169168320, 2170868227, 2173022808, 2174751247, 2179048400, 2184998274, 2196541409, 2200622033, 2203412941, 2206322353, 2208794483, 2219653172, 2219657520, 2225010953, 2226828879, 2238722895, 2238722920, 2238723506, 2241976578, 2245936247, 2248375230, 2249276550, 2249625301, 2254065144, 2254179087, 2254183431, 2254275149, 2254449430, 2254449877, 2255178054, 2264880989, 2270863210, 2290294367, 2304704334, 2304866355, 2305219189, 2310350875, 2310486036, 2312897274, 2314773060, 2315564905, 2319231065, 2319463533, 2325240383, 2327016339, 2330482855, 2337919027, 2340169455, 2359883328, 2361871491, 2366081778, 2369823335, 2369831600, 2371523459, 2372759050, 2374977123, 2376431395, 2378889732, 2382890223, 2383755454, 2386589953, 2387052696, 2389856295, 2391789782, 2398718314, 2399324290, 2400888860, 2401211408, 2404756392, 2406557074, 2407241140, 2409418646, 2411497922, 2411691127, 2413846222, 2413908037, 2414944572, 2415208709, 2417936111, 2419639306, 2423159152, 2423360684, 2425978408, 2428076111, 2437572023, 2440527060, 2444775143, 2449407487, 2457428534, 2469735934, 2475146676, 2475744613, 2476033552, 2476112212, 2476147614, 2477393954, 2478803388, 2479415778, 2482075359, 2485317413, 2485370363, 2488499588, 2488699734, 2491415998, 2492607180, 2493496209, 2497515972, 2499072481, 2499532790, 2504383993, 2504870149, 2505121421, 2505147736, 2513647314, 2513693640, 2513701512, 2513706827, 2521253655, 2521398855, 2526527953, 2526528078, 2527291586, 2527292245, 2527666001, 2528098475, 2536669081, 2536933437, 2537106090, 2538335365, 2541170503, 2541170604, 2541177518, 2545965593, 2546249066, 2546819122, 2548278991, 2548782015, 2549421379, 2557808039, 2557863700, 2558865115, 2568950385, 2569073380, 2569341502, 2569405925, 2570837952, 2575053435, 2575619554, 2575627585, 2579451785, 2581687876, 2582936524, 2586547509, 2590439971, 2600983050, 2602643559, 2605946857, 2608238576, 2608504686, 2611889973, 2612202111, 2619739935, 2621175072, 2627204334, 2627570013, 2627677159, 2631480810, 2631901285, 2635187702, 2637430468, 2638897207, 2639751704, 2642390316, 2644459471, 2644532855, 2644906311, 2645171587, 2647433605, 2647443463, 2649904288, 2651288351, 2652440186, 2655263134, 2660229222, 2660362019, 2662714632, 2671981072, 2673085999, 2676359415, 2678218950, 2680015310, 2683201101, 2683726243, 2687071289, 2687546085, 2689958531, 2690565794, 2691049537, 2696922944, 2702278755, 2705586928, 2707450736, 2708750293, 2710694053, 2710777678, 2717039465, 2719746264, 2719953243, 2722365346, 2724396360, 2730361077, 2732178535, 2732249147, 2732255792, 2732453216, 2732465831, 2733162785, 2733179003, 2740913336, 2743326046, 2745816408, 2746770100, 2768031559, 2768594053, 2769743066, 2770453396, 2777301260, 2777413063, 2779047561, 2779131760, 2781151044, 2788878449, 2791114477, 2792266216, 2795123222, 2795130739, 2795148393, 2803000277, 2803220098, 2820015673, 2824852881, 2825063248, 2825297984, 2826183623, 2826618777, 2828159974, 2830840737, 2840364717, 2844137461, 2844192015, 2844331414, 2844474265, 2845536368, 2847702680, 2847708560, 2849875839, 2854691117, 2857021867, 2857111846, 2857167445, 2857291628, 2857718467, 2857718874, 2859609075, 2860369035, 2860944275, 2861234828, 2861431296, 2861773187, 2862323803, 2862729831, 2862789186, 2862818280, 2865000297, 2865536587, 2872917161, 2879220442, 2885591219, 2886256228, 2886266660, 2886337850, 2886340600, 2886347487, 2886358758, 2886559394, 2888553420, 2893735969, 2893987517, 2894277589, 2895201770, 2895970159, 2903889952, 2904798808, 2907566289, 2911967032, 2913775681, 2917443420, 2921648360, 2921994283, 2925162127, 2925540459, 2931480722, 2936112276, 2938485423, 2939997155, 2941295122, 2942568797, 2944555176, 2950549599, 2952067971, 2952072562, 2955690120, 2961421753, 2962144430, 2962519996, 2962841785, 2964270344, 2964373735, 2965548040, 2966852375, 2970298080, 2974400461, 2975755381, 2981996158, 2987922608, 2991195167, 2991625994, 2993771546, 2995901561, 3000958971, 3001281849, 3001388716, 3004478994, 3004479027, 3004479111, 3004479159, 3004479171, 3004479184, 3004479190, 3004479239, 3004479240, 3004479258, 3004479289, 3004479305, 3004479323, 3004479334, 3004479373, 3004479389, 3004479390, 3004479401, 3004479425, 3004479785, 3004479787, 3004479818, 3004479829, 3004479837, 3004479976, 3004479994, 3004480114, 3005847375, 3006723884, 3006726944, 3006727797, 3006731179, 3006737252, 3006744684, 3006811183, 3012299493, 3014399025, 3019017018, 3019072181, 3019996757, 3020108825, 3020133371, 3020188532, 3023885513, 3024558034, 3024589567, 3024626538, 3033483503, 3034109278, 3035739007, 3035887950, 3044634578, 3044797796, 3044821749, 3045244983, 3045788419, 3045876876, 3046124074, 3046256428, 3050244615, 3050333064, 3050334784, 3056297406, 3062281966, 3063798750, 3063849681, 3073445035, 3073797863, 3073848296, 3086119708, 3087786680, 3089398889, 3089451715, 3089454054, 3089461994, 3089735415, 3094552970, 3097888413, 3098875466, 3099276787, 3104375123, 3104503715, 3105798493, 3107144912, 3107146953, 3110631110, 3110681545, 3111601102, 3111601746, 3111606786, 3114815727, 3119543502, 3119594433, 3120807553, 3120857998, 3122897068, 3125786613, 3128821880, 3133975234, 3135838657, 3136281421, 3145164732, 3147940006, 3154068140, 3154152867, 3157412719, 3157501664, 3159380027, 3160589879, 3161016478, 3161897203, 3174437714, 3180245112, 3180300610, 3182786585, 3183126568, 3183293814, 3183325319, 3184294753, 3188347051, 3191217062, 3196370198, 3197567695, 3198643172, 3198783739, 3198824989, 3198841920, 3198930383, 3199640352, 3200095506, 3203439089, 3203573947, 3203579445, 3208441350, 3209729826, 3210506925, 3210514725, 3210570457, 3214383466, 3214394316, 3214653823, 3215790970, 3217760577, 3218901480, 3218928718, 3218996674, 3218997101, 3219339071, 3219427268, 3220535722, 3220543483, 3221757640, 3223098753, 3224727829, 3232284385, 3232339054, 3234508143, 3234559072, 3235473148, 3237969392, 3243142044, 3247991594, 3253953941, 3269910681, 3270985722, 3273573836, 3273628995, 3275986591, 3277061645, 3277112578, 3277868236, 3277980164, 3278129999, 3278154322, 3280832255, 3280992609, 3283017533, 3286262047, 3290414111, 3301409832, 3301494567, 3302526185, 3302610918, 3305712858, 3305866028, 3305950755, 3309540327, 3309590022, 3309595898, 3309596203, 3309660560, 3309660597, 3309937069, 3312550946, 3312639405, 3317007142, 3317095593, 3324397363, 3331028046, 3331525682, 3331580349, 3331802213, 3332642035, 3332696700, 3333929978, 3334870005, 3334920442, 3335058344, 3335315569, 3343940221, 3345496201, 3350023967, 3353092349, 3358586999, 3365687143, 3366763202, 3368167300, 3371155980, 3372842751, 3373802982, 3374003367, 3374007861, 3374013921, 3374033257, 3374071862, 3374072315, 3374075119, 3374222601, 3374506623, 3377952754, 3382868701, 3384928690, 3388197033, 3390931348, 3391051206, 3391063809, 3391068622, 3391334282, 3391402631, 3391423133, 3391432603, 3392425741, 3394879910, 3395277647, 3399311251, 3402270417, 3404440519, 3414226886, 3414277321, 3415566709, 3417045783, 3417060092, 3418683074, 3418733517, 3424453774, 3431921225, 3437307073, 3437430868, 3437705452, 3444401619, 3445590826, 3447374472, 3456431399, 3458638240, 3461359920, 3463272868, 3468986640, 3469121667, 3471246134, 3474393156, 3474446194, 3476056250, 3478543821, 3486841411, 3486906847, 3489097968, 3491201265, 3495569706, 3496705474, 3497897502, 3497994843, 3498252682, 3502149957, 3504414102, 3504826781, 3506839508, 3506948350, 3508950458, 3509210745, 3509498189, 3511959565, 3512025010, 3512493029, 3514111400, 3517669498, 3518790968, 3521920341, 3523035738, 3523862571, 3524226140, 3530307622, 3530358057, 3536335853, 3536792162, 3538712404, 3541452460, 3541507619, 3542648636, 3544416242, 3550676375, 3551025439, 3553383951, 3556498831, 3561501051, 3561585780, 3565016796, 3565023071, 3565174365, 3565227623, 3565288856, 3566089568, 3572109810, 3575114019, 3577841990, 3586425916, 3589694483, 3591020567, 3592221649, 3594125448, 3595182758, 3596128381, 3602035250, 3602533630, 3602552275, 3604829927, 3607233834, 3607322789, 3607604079, 3608554389, 3610981370, 3617629034, 3619761411, 3623812162, 3629877419, 3636237811, 3636292476, 3639577654, 3639632313, 3645953597, 3647523178, 3649784978, 3653883892, 3660676457, 3664234276, 3674197367, 3675513627, 3681233287, 3684650455, 3688377898, 3689406359, 3692544695, 3693437133, 3694959415, 3703294733, 3704443907, 3704956777, 3706490306, 3709178884, 3709268355, 3709272958, 3717182590, 3718660896, 3719413702, 3721853564, 3731122282, 3734934472, 3736397122, 3736397691, 3738359136, 3744502996, 3744505315, 3744515994, 3744516038, 3745225898, 3745403285, 3749377655, 3751498613, 3752631559, 3753565240, 3756319792, 3758308501, 3758308691, 3761682835, 3762386667, 3762488637, 3763193356, 3763904751, 3764062969, 3764739038, 3769398133, 3770065529, 3774076759, 3779092995, 3780318738, 3781089827, 3783201212, 3785420602, 3786786081, 3788364543, 3791375542, 3791430201, 3791912060, 3792007260, 3792147146, 3793208754, 3794029235, 3805317549, 3808957225, 3809652473, 3811984999, 3812594538, 3819295903, 3819351056, 3821104144, 3821104746, 3829518367, 3832811824, 3833121835, 3833171090, 3833706374, 3838812042, 3843969806, 3844552031, 3850681433, 3851222744, 3851541567, 3851602009, 3851679807, 3853676291, 3855415829, 3856249405, 3859110665, 3859972063, 3862928629, 3865386916, 3865396334, 3873108359, 3873163016, 3876524049, 3883472548, 3885986978, 3888196487, 3895773227, 3898366596, 3900605466, 3900796753, 3906034907, 3907036333, 3914330405, 3916906002, 3922403377, 3925982068, 3933039724, 3936549300, 3939824482, 3940957272, 3941201834, 3941535714, 3943160335, 3943296300, 3950173236, 3955179593, 3959867562, 3960938237, 3961299015, 3961303520, 3961836502, 3962329360, 3963273426, 3966271140, 3969493837, 3970184201, 3971378905, 3972349404, 3972404563, 3974206923, 3977375686, 3977639927, 3981851856, 3984175284, 3984369770, 3984383153, 3984388901, 3984577838, 3986753035, 3987449768, 3988320676, 3989122328, 3989124781, 3989300792, 3991957101, 3991978776, 3992246021, 3993156440, 3995285601, 4002046206, 4002059123, 4002298131, 4007368305, 4009075902, 4012314248, 4014272956, 4018800601, 4021398623, 4022152923, 4023242992, 4034787018, 4034837957, 4040007159, 4040507273, 4040558214, 4042630615, 4042667369, 4044815570, 4044899805, 4046325025, 4051504220, 4051593171, 4059166898, 4059387372, 4060969098, 4060986772, 4062588735, 4063625944, 4063736412, 4064813411, 4074640059, 4077930265, 4080197122, 4081731399, 4081736449, 4081740860, 4081761692, 4082508192, 4082648933, 4085037592, 4085499470, 4085741867, 4086206754, 4087477773, 4087973382, 4087974431, 4087975312, 4087977920, 4087977986, 4087982672, 4087983230, 4087984585, 4087984590, 4087984656, 4087988411, 4087993231, 4087993234, 4087993291, 4087993428, 4088004545, 4089941093, 4090379779, 4094838531, 4095533224, 4098180267, 4104794847, 4104808845, 4105491350, 4105500480, 4109580593, 4111598640, 4115797781, 4116207257, 4116258198, 4116322118, 4116406345, 4116912946, 4122262153, 4126221625, 4127308650, 4128209898, 4128210099, 4128224738, 4128228031, 4128452341, 4131804567, 4131859224, 4137741343, 4141029933, 4142953920, 4145022541, 4149201544, 4150566897, 4151710650, 4152474623, 4155185738, 4156445644, 4157556469, 4157644922, 4159136925, 4159401066, 4159780211, 4159864444, 4164601660, 4166043368, 4168091484, 4169450331, 4170161097, 4170579962, 4170925049, 4171014006, 4171016671, 4171029715, 4172482250, 4175353143, 4176008925, 4178981053, 4184703759, 4186748423, 4188894668, 4189635776, 4190045706, 4190142208, 4195146068, 4196943735, 4199824850, 4203521301, 4206809827, 4206944958, 4207535653, 4208164707, 4211585807, 4215346074, 4215356593, 4218114605, 4218115138, 4218132009, 4219656584, 4219999876, 4220379359, 4221957810, 4222018626, 4225873997, 4227433758, 4228171984, 4228217908, 4228360888, 4228368741, 4228368760, 4231583294, 4231662792, 4232149414, 4232629512, 4234942237, 4235762280, 4240864861, 4241320459, 4241740950, 4242647335, 4243702915, 4245105172, 4246629902, 4248741847, 4252833472, 4252840599, 4254781707, 4254799704, 4255058051, 4260594638, 4261873154, 4261894730, 4262104449, 4262374147, 4262375371, 4262499171, 4264253465, 4265048576, 4267292711, 4271528787, 4272039260, 4272350188, 4272417877, 4276136562, 4288066094, 368, 54631547, 68945260, 76317054, 90122581, 107533418, 134757519, 142022835, 149084067, 159782934, 169736776, 205527546, 244603010, 255553804, 262051769, 263431316, 265459661, 284810646, 289494951, 371032970, 373243562, 374621869, 387545720, 391377589, 415171499, 415171548, 415171976, 418990556, 418990602, 435420269, 461226423, 483976516, 501379566, 531625563, 553327069, 590191545, 595217502, 649854972, 656131164, 668816409, 678957092, 680578927, 714686602, 715141614, 717942499, 720960146, 720974524, 720974736, 720975995, 725617684, 744932012, 793535325, 806495002, 817571047, 823641433, 857348365, 862847657, 871542102, 874448701, 877175745, 893771636, 914435801, 931438088, 937200556, 1015486168, 1026348750, 1029964103, 1030875558, 1083568115, 1106088318, 1135523977, 1206251138, 1219433535, 1220725895, 1220852235, 1220852260, 1220852796, 1220852957, 1222628504, 1230410191, 1240508317, 1242746690, 1260212779, 1282239389, 1290343418, 1326192098, 1338160975, 1340954405, 1351436722, 1361325259, 1374669131, 1374800320, 1389489864, 1401511709, 1421711922, 1452449030, 1467196671, 1467386990, 1490648152, 1493005045, 1506058569, 1507763651, 1514073041, 1515782688, 1515784934, 1515785058, 1523142552, 1526329423, 1553174585, 1591300266, 1629584534, 1641166031, 1642384128, 1661678914, 1679178836, 1679485164, 1681545174, 1704277516, 1705410866, 1705908110, 1714538458, 1768636249, 1772526810, 1818263278, 1821800212, 1833750850, 1834601376, 1834613468, 1866278547, 1867401367, 1936236019, 1945296852, 1978039580, 1997464432, 2017904725, 2055461758, 2058918178, 2080694907, 2086814061, 2089989988, 2123843096, 2170766397, 2172738430, 2174442073, 2177527468, 2178512614, 2233637259, 2246941078, 2274845447, 2274845649, 2279046513, 2293400491, 2299670458, 2300280964, 2300527715, 2305877279, 2307152224, 2316307169, 2322963439, 2335588857, 2337430377, 2359562546, 2360555826, 2405952063, 2419834458, 2423179189, 2436862648, 2436862650, 2436862651, 2436862652, 2436862653, 2436862654, 2436862655, 2439178127, 2458256817, 2460729245, 2517521888, 2585317679, 2631335866, 2632842752, 2726667654, 2726667661, 2726667752, 2726667756, 2726667834, 2726667995, 2726668398, 2737177336, 2757711981, 2779759639, 2787445139, 2796817467, 2812190333, 2816464305, 2817592022, 2826795200, 2827908591, 2872823135, 2873905939, 2876785673, 2876785759, 2901134565, 2913059937, 2924726497, 2938670220, 2939089089, 2943360116, 2945389039, 3005172573, 3015670621, 3022308183, 3050185270, 3050185436, 3056015384, 3056015484, 3071839865, 3079506072, 3079929644, 3116612793, 3164097381, 3176996220, 3180729164, 3227651590, 3234391576, 3234432745, 3278041418, 3278041727, 3278041816, 3319967633, 3330642108, 3334769994, 3354637514, 3375261606, 3375312977, 3410106074, 3423153883, 3456106742, 3461071037, 3473412940, 3486684134, 3504293483, 3517650814, 3547292615, 3571189672, 3593285841, 3612407497, 3666061454, 3666061458, 3666061568, 3666061577, 3666061585, 3666061591, 3666061602, 3666061610, 3666061613, 3666061619, 3666061666, 3666061672, 3666061702, 3666061706, 3666061732, 3666061760, 3666061781, 3666061825, 3666061864, 3666061891, 3666061895, 3666061896, 3666061902, 3666061903, 3666061913, 3666062029, 3666062293, 3666062299, 3666062326, 3666062331, 3666062345, 3666062357, 3666062361, 3666062379, 3666062386, 3666062390, 3666062391, 3666062394, 3666062408, 3666062418, 3666062422, 3666062427, 3666062453, 3666062517, 3666062569, 3666062581, 3666062582, 3666062586, 3666062587, 3666099519, 3667007182, 3676644409, 3676644411, 3676644421, 3676644429, 3676644442, 3676644586, 3676644600, 3676644610, 3676644643, 3676644706, 3676644759, 3676644775, 3676644800, 3676644806, 3676644819, 3676644874, 3676644887, 3676644888, 3676644905, 3676644939, 3676644953, 3676644982, 3676645005, 3676645006, 3676645021, 3676645049, 3676645073, 3684315096, 3691777760, 3697941178, 3708654452, 3710369155, 3749170769, 3772863442, 3793240332, 3798969166, 3800169971, 3820675046, 3829710462, 3829710568, 3843370951, 3845152461, 3847111189, 3861225221, 3871154340, 3872238039, 3916589493, 3949265042, 3952274701, 3967179311, 4020468984, 4088042711, 4088042763, 4088043471, 4098608917, 4098609219, 4098704176, 4098704230, 4098775844, 4098776178, 4098815877, 4098816211, 4126370696, 4127380674, 4155818428, 4160021452, 4179162156, 4189349925, 4224941776, 4233200080, 4259920717, 4268562148, 0, 0, 0, 17, 343511425, 1030334438, 1035444912, 1035444966, 1126785220, 1160516735, 2496101809, 2496102373, 2501069285, 2506938014, 2613448893, 2668096359, 2767610756, 2943255975, 3483670337, 3483670995, 4233599295, 4, 989615076, 1348282182, 2372695675, 2793429742, 410, 2315777, 2516160, 11643297, 13203897, 21231554, 35967653, 38712935, 47792331, 72865995, 74144458, 82734700, 83460346, 105527502, 113271207, 133306274, 134752460, 140540365, 149081424, 159784149, 160693466, 187981691, 201714711, 257089230, 271126044, 284628322, 288401789, 293512087, 299470436, 301921344, 303087651, 310748895, 317229038, 318673258, 321508235, 328967865, 331740776, 333043316, 343618051, 344116268, 349554276, 368842915, 373240553, 380288946, 385653806, 387546555, 391603917, 397299232, 407552584, 411195e3, 457925677, 465045723, 466546683, 476300545, 496262010, 508940895, 528158848, 576308682, 599016891, 599034260, 599035482, 617473653, 629010449, 643159709, 649306413, 657763177, 658030821, 663498697, 666920016, 684079208, 730892591, 745170160, 748876721, 748886222, 752803028, 756281027, 761348098, 767592699, 768148470, 771634050, 771637032, 797075449, 798164153, 806496217, 807477757, 814547322, 823640218, 828791723, 830209933, 830328663, 830812219, 831086733, 843422410, 848961657, 871543061, 884068409, 931459402, 935834596, 953760609, 956026316, 973201175, 985556812, 990107236, 1002251210, 1006286666, 1015491227, 1017212284, 1017751931, 1026349709, 1027559288, 1039189287, 1040655967, 1048681185, 1049173028, 1069937338, 1071441344, 1072691903, 1128950639, 1139782538, 1139843834, 1141008431, 1156523661, 1160815779, 1194239092, 1197936283, 1211577197, 1211637010, 1222641289, 1240507358, 1264600767, 1288872441, 1321698432, 1329660539, 1338155660, 1339210968, 1373285759, 1382125974, 1390628516, 1399578255, 1399657308, 1403708559, 1408484449, 1432456391, 1433498959, 1468959011, 1474134153, 1481167509, 1481892069, 1485735468, 1512063165, 1514478145, 1519263375, 1522675342, 1541305645, 1542981532, 1553720283, 1557499238, 1632730660, 1635518266, 1640297675, 1644040136, 1645084619, 1672625515, 1675268949, 1675573659, 1679183895, 1701744405, 1704282831, 1705918154, 1709094170, 1740118996, 1745197398, 1746168006, 1763490076, 1764138250, 1778504542, 1804707890, 1807895638, 1812124962, 1825087480, 1827997201, 1834433178, 1836295865, 1836721468, 1855831597, 1871287494, 1874044309, 1894472089, 1911094612, 1944286571, 1945301911, 1964668429, 1992457158, 2005254865, 2048724462, 2072914399, 2073220142, 2080693816, 2086817070, 2091822363, 2116894487, 2120589916, 2132854800, 2138833857, 2149555928, 2149786502, 2159131792, 2166792548, 2193556503, 2203826663, 2222814745, 2233632200, 2234478015, 2266871804, 2270958851, 2280109123, 2300684501, 2320975486, 2368015199, 2368150205, 2378859099, 2402357659, 2420231640, 2421832104, 2442099500, 2445444524, 2450316872, 2477231344, 2480056360, 2511017726, 2535682339, 2564758885, 2580206998, 2634443356, 2655498207, 2659744440, 2664398480, 2665288759, 2683912382, 2685363948, 2697995386, 2705244823, 2707054618, 2731911143, 2746095604, 2750223108, 2751457001, 2757038073, 2757714990, 2757832374, 2763102979, 2764615893, 2768173321, 2769632227, 2788021838, 2808290141, 2813866328, 2817995155, 2823576784, 2832963785, 2833300206, 2839929991, 2842899363, 2855442276, 2861724882, 2873826097, 2875201553, 2889802328, 2894057006, 2902844704, 2920776771, 2938675535, 2940382413, 2940687092, 2942499160, 2944606430, 2976147113, 2999699036, 3012594373, 3014878073, 3021947486, 3024482894, 3027886950, 3033143700, 3041972547, 3043054392, 3061104959, 3068421535, 3068451149, 3073259213, 3076287128, 3092740204, 3097180103, 3126517186, 3134714387, 3141033517, 3153726305, 3161099645, 3164570023, 3168616586, 3178721795, 3185964212, 3199657339, 3213212569, 3227558031, 3234959359, 3242752110, 3245911312, 3250117513, 3255207552, 3263028169, 3267416959, 3271443733, 3276067803, 3303582289, 3303582897, 3303582994, 3303614961, 3327283712, 3336973745, 3345667381, 3346748653, 3358811093, 3365430328, 3371744816, 3393399711, 3418876414, 3451234301, 3462047339, 3466186248, 3487461167, 3489942689, 3511023565, 3528253833, 3577832733, 3577832874, 3579570991, 3581968529, 3587382024, 3594263141, 3597175734, 3609198260, 3620670314, 3644061745, 3686687805, 3699471696, 3704450806, 3719669200, 3724302375, 3732631655, 3737867596, 3737869333, 3737882439, 3743824089, 3748450386, 3755463030, 3762725071, 3780090414, 3786960458, 3789613664, 3804622433, 3829814476, 3834232417, 3836376093, 3843018675, 3847110230, 3849573984, 3888536498, 3895950835, 3925391633, 3927045026, 3929681833, 3930866393, 3974478460, 3989861270, 3990612749, 3994206764, 3994206767, 4003176468, 4013705057, 4020891302, 4040130402, 4055956024, 4064081091, 4069710253, 4076793042, 4078153021, 4090215578, 4095557691, 4103392506, 4105790268, 4130682685, 4131077260, 4150503708, 4155492542, 4165042016, 4186885299, 4188349987, 4190709408, 4193373567, 4197651626, 4198443983, 4202984206, 4210375752, 4212069506, 4216891535, 4228991204, 4251429164, 4263509307, 4279717352, 4282015733, 0, 0, 0, 2, 898804372, 2420122849, 0, 191, 14034108, 23188555, 69988957, 176179919, 181602757, 231162178, 234878220, 241015393, 282017655, 286917352, 298380305, 303225044, 333040682, 436746473, 437068413, 449019336, 449464240, 451920903, 472319354, 495318858, 500388520, 512606097, 527005648, 531402563, 554651161, 561857715, 570474602, 588675343, 615779940, 680838102, 688229624, 722503086, 733631603, 759879349, 760863762, 768579191, 769161927, 777931472, 804592434, 820388681, 834351359, 838060561, 871806992, 907959623, 917609192, 921095799, 922653385, 936253712, 951807472, 976944213, 1057868108, 1061438860, 1097991931, 1099387701, 1118780323, 1129127307, 1134058690, 1149298066, 1173449599, 1188365042, 1221482277, 1242510922, 1244344576, 1249042959, 1256956692, 1322375458, 1340682260, 1389219463, 1420709285, 1468300758, 1544881072, 1554252850, 1557974723, 1564485910, 1566036640, 1637844009, 1641584834, 1668922875, 1697481902, 1700564263, 1779722906, 1817679755, 1844196310, 1862443027, 1863425670, 1874439438, 1918635827, 1942164974, 1953292144, 1996832610, 2005075462, 2171493616, 2174172768, 2200270403, 2224853335, 2232538822, 2253530761, 2271804726, 2307427283, 2314778321, 2325064176, 2347507979, 2356867634, 2422267260, 2435625787, 2442761119, 2448910470, 2454582508, 2471444403, 2478294033, 2487762682, 2505529649, 2513007594, 2514973059, 2523046044, 2645305307, 2697781106, 2700249759, 2713921343, 2858583336, 2869381059, 2875883974, 2877426354, 2906087318, 2940183875, 2941854634, 2983778787, 2991311078, 3020661286, 3033841873, 3036938981, 3061233249, 3088839886, 3090851e3, 311688e4, 3160125774, 3173697968, 3175256934, 3193365922, 3209525171, 3248874150, 3262696949, 3293334302, 3294129343, 3298593e3, 3336190368, 3342381501, 3344449059, 3367460946, 3387371732, 3391640312, 3415133140, 3415553447, 3416872467, 3486599559, 3505446608, 3554833241, 3594782899, 3615198865, 3629910769, 3642670614, 3643744473, 3654513786, 3675702820, 3697030868, 3710380917, 3732976135, 3779661543, 3795518186, 3803370028, 3804920752, 3840174405, 3861583079, 3867650596, 3894082090, 3900359633, 3928753122, 3942119031, 3951224511, 4009634354, 4063453845, 4065646590, 4079144597, 4163056211, 4180315949, 4189523019, 4196008531, 4241738188, 4254148468, 4265459019, 4273759132, 0, 0, 0, 1, 1058807915, 0, 5, 1148034389, 1373602048, 2160920720, 2391490885, 2722440867]);

  // node_modules/tldts-experimental/dist/es6/src/packed-hashes.js
  function binSearch(arr, elt, start, end) {
    if (start >= end) {
      return false;
    }
    let low = start;
    let high = end - 1;
    while (low <= high) {
      const mid = low + high >>> 1;
      const midVal = arr[mid];
      if (midVal < elt) {
        low = mid + 1;
      } else if (midVal > elt) {
        high = mid - 1;
      } else {
        return true;
      }
    }
    return false;
  }
  var BUFFER = new Uint32Array(20);
  function hashHostnameLabelsBackward(hostname, maximumNumberOfLabels) {
    let hash = 5381;
    let index = 0;
    for (let i = hostname.length - 1; i >= 0; i -= 1) {
      const code = hostname.charCodeAt(i);
      if (code === 46) {
        BUFFER[index << 1] = hash >>> 0;
        BUFFER[(index << 1) + 1] = i + 1;
        index += 1;
        if (index === maximumNumberOfLabels) {
          return index;
        }
      }
      hash = hash * 33 ^ code;
    }
    BUFFER[index << 1] = hash >>> 0;
    BUFFER[(index << 1) + 1] = 0;
    index += 1;
    return index;
  }
  function suffixLookup(hostname, options, out) {
    if (fast_path_default(hostname, options, out)) {
      return;
    }
    const { allowIcannDomains, allowPrivateDomains } = options;
    let matchIndex = -1;
    let matchKind = 0;
    let matchLabels = 0;
    let index = 1;
    const numberOfHashes = hashHostnameLabelsBackward(
      hostname,
      hashes_default[0]
      /* maximumNumberOfLabels */
    );
    for (let label = 0; label < numberOfHashes; label += 1) {
      const hash = BUFFER[label << 1];
      const labelStart = BUFFER[(label << 1) + 1];
      let match = 0;
      if (allowIcannDomains) {
        match = binSearch(hashes_default, hash, index + 1, index + hashes_default[index] + 1) ? 1 | 4 : 0;
      }
      index += hashes_default[index] + 1;
      if (allowPrivateDomains && match === 0) {
        match = binSearch(hashes_default, hash, index + 1, index + hashes_default[index] + 1) ? 2 | 4 : 0;
      }
      index += hashes_default[index] + 1;
      if (allowIcannDomains && match === 0 && (matchKind & 4) === 0) {
        match = binSearch(hashes_default, hash, index + 1, index + hashes_default[index] + 1) ? 16 | 1 : 0;
      }
      index += hashes_default[index] + 1;
      if (allowPrivateDomains && match === 0 && (matchKind & 4) === 0) {
        match = binSearch(hashes_default, hash, index + 1, index + hashes_default[index] + 1) ? 16 | 2 : 0;
      }
      index += hashes_default[index] + 1;
      if (allowIcannDomains && match === 0 && (matchKind & 4) === 0 && matchLabels <= label) {
        match = binSearch(hashes_default, hash, index + 1, index + hashes_default[index] + 1) ? 8 | 1 : 0;
      }
      index += hashes_default[index] + 1;
      if (allowPrivateDomains && match === 0 && (matchKind & 4) === 0 && matchLabels <= label) {
        match = binSearch(hashes_default, hash, index + 1, index + hashes_default[index] + 1) ? 8 | 2 : 0;
      }
      index += hashes_default[index] + 1;
      if (match !== 0) {
        matchKind = match;
        matchLabels = label + ((match & 16) !== 0 ? 2 : 1);
        matchIndex = labelStart;
      }
    }
    out.isIcann = (matchKind & 1) !== 0;
    out.isPrivate = (matchKind & 2) !== 0;
    if (matchIndex === -1) {
      out.publicSuffix = numberOfHashes === 1 ? hostname : hostname.slice(BUFFER[1]);
      return;
    }
    if ((matchKind & 4) !== 0) {
      out.publicSuffix = hostname.slice(BUFFER[(matchLabels - 2 << 1) + 1]);
      return;
    }
    if ((matchKind & 16) !== 0) {
      if (matchLabels < numberOfHashes) {
        out.publicSuffix = hostname.slice(BUFFER[(matchLabels - 1 << 1) + 1]);
        return;
      }
      const parts = hostname.split(".");
      while (parts.length > matchLabels) {
        parts.shift();
      }
      out.publicSuffix = parts.join(".");
      return;
    }
    out.publicSuffix = hostname.slice(matchIndex);
  }
  function parse2(url, options = {}) {
    return parseImpl(url, 5, suffixLookup, options, getEmptyResult());
  }

  // node_modules/@cliqz/adblocker/dist/es6/src/tokens-buffer.js
  var TokensBuffer = class {
    constructor(size) {
      this.pos = 0;
      this.buffer = new Uint32Array(size);
    }
    reset() {
      this.pos = 0;
    }
    slice() {
      return this.buffer.slice(0, this.pos);
    }
    push(token) {
      this.buffer[this.pos++] = token;
    }
    empty() {
      return this.pos === 0;
    }
    full() {
      return this.pos === this.buffer.length;
    }
    remaining() {
      return this.buffer.length - this.pos;
    }
  };
  var TOKENS_BUFFER = new TokensBuffer(1024);

  // node_modules/@cliqz/adblocker/dist/es6/src/utils.js
  var HASH_SEED = 7877;
  function bitCount(n) {
    n = n - (n >> 1 & 1431655765);
    n = (n & 858993459) + (n >> 2 & 858993459);
    return (n + (n >> 4) & 252645135) * 16843009 >> 24;
  }
  function getBit(n, mask) {
    return !!(n & mask);
  }
  function setBit(n, mask) {
    return n | mask;
  }
  function clearBit(n, mask) {
    return n & ~mask;
  }
  function fastHashBetween(str, begin, end) {
    let hash = HASH_SEED;
    for (let i = begin; i < end; i += 1) {
      hash = hash * 33 ^ str.charCodeAt(i);
    }
    return hash >>> 0;
  }
  function fastHash(str) {
    if (typeof str !== "string") {
      return HASH_SEED;
    }
    if (str.length === 0) {
      return HASH_SEED;
    }
    return fastHashBetween(str, 0, str.length);
  }
  function hashStrings(strings) {
    const result = new Uint32Array(strings.length);
    let index = 0;
    for (const str of strings) {
      result[index++] = fastHash(str);
    }
    return result;
  }
  function fastStartsWith(haystack, needle) {
    if (haystack.length < needle.length) {
      return false;
    }
    const ceil = needle.length;
    for (let i = 0; i < ceil; i += 1) {
      if (haystack[i] !== needle[i]) {
        return false;
      }
    }
    return true;
  }
  function fastStartsWithFrom(haystack, needle, start) {
    if (haystack.length - start < needle.length) {
      return false;
    }
    const ceil = start + needle.length;
    for (let i = start; i < ceil; i += 1) {
      if (haystack[i] !== needle[i - start]) {
        return false;
      }
    }
    return true;
  }
  function isDigit(ch) {
    return ch >= 48 && ch <= 57;
  }
  function isAlpha(ch) {
    return ch >= 97 && ch <= 122 || ch >= 65 && ch <= 90;
  }
  function isAlphaExtended(ch) {
    return ch >= 192 && ch <= 450;
  }
  function isCyrillic(ch) {
    return ch >= 1024 && ch <= 1279;
  }
  function isAllowedCode(ch) {
    return isAlpha(ch) || isDigit(ch) || ch === 37 || isAlphaExtended(ch) || isCyrillic(ch);
  }
  function tokenizeWithWildcardsInPlace(pattern, skipFirstToken, skipLastToken, buffer) {
    const len = Math.min(pattern.length, buffer.remaining() * 2);
    let inside = false;
    let precedingCh = 0;
    let start = 0;
    let hash = HASH_SEED;
    for (let i = 0; i < len; i += 1) {
      const ch = pattern.charCodeAt(i);
      if (isAllowedCode(ch) === true) {
        if (inside === false) {
          hash = HASH_SEED;
          inside = true;
          start = i;
        }
        hash = hash * 33 ^ ch;
      } else {
        if (inside === true) {
          inside = false;
          if (i - start > 1 && // Ignore tokens of 1 character
          ch !== 42 && // Ignore tokens followed by a '*'
          precedingCh !== 42 && // Ignore tokens preceeded by a '*'
          (skipFirstToken === false || start !== 0)) {
            buffer.push(hash >>> 0);
          }
        }
        precedingCh = ch;
      }
    }
    if (skipLastToken === false && inside === true && precedingCh !== 42 && // Ignore tokens preceeded by a '*'
    pattern.length - start > 1 && // Ignore tokens of 1 character
    buffer.full() === false) {
      buffer.push(hash >>> 0);
    }
  }
  function tokenizeInPlace(pattern, skipFirstToken, skipLastToken, buffer) {
    const len = Math.min(pattern.length, buffer.remaining() * 2);
    let inside = false;
    let start = 0;
    let hash = HASH_SEED;
    for (let i = 0; i < len; i += 1) {
      const ch = pattern.charCodeAt(i);
      if (isAllowedCode(ch) === true) {
        if (inside === false) {
          hash = HASH_SEED;
          inside = true;
          start = i;
        }
        hash = hash * 33 ^ ch;
      } else if (inside === true) {
        inside = false;
        if (i - start > 1 && // Ignore tokens of 1 character
        (skipFirstToken === false || start !== 0)) {
          buffer.push(hash >>> 0);
        }
      }
    }
    if (inside === true && skipLastToken === false && pattern.length - start > 1 && // Ignore tokens of 1 character
    buffer.full() === false) {
      buffer.push(hash >>> 0);
    }
  }
  function tokenizeNoSkipInPlace(pattern, buffer) {
    const len = Math.min(pattern.length, buffer.remaining() * 2);
    let inside = false;
    let start = 0;
    let hash = HASH_SEED;
    for (let i = 0; i < len; i += 1) {
      const ch = pattern.charCodeAt(i);
      if (isAllowedCode(ch) === true) {
        if (inside === false) {
          hash = HASH_SEED;
          inside = true;
          start = i;
        }
        hash = hash * 33 ^ ch;
      } else if (inside === true) {
        inside = false;
        if (i - start > 1) {
          buffer.push(hash >>> 0);
        }
      }
    }
    if (inside === true && pattern.length - start > 1 && buffer.full() === false) {
      buffer.push(hash >>> 0);
    }
  }
  function tokenizeNoSkip(pattern) {
    TOKENS_BUFFER.reset();
    tokenizeNoSkipInPlace(pattern, TOKENS_BUFFER);
    return TOKENS_BUFFER.slice();
  }
  function tokenize2(pattern, skipFirstToken, skipLastToken) {
    TOKENS_BUFFER.reset();
    tokenizeInPlace(pattern, skipFirstToken, skipLastToken, TOKENS_BUFFER);
    return TOKENS_BUFFER.slice();
  }
  function tokenizeRegexInPlace(selector, tokens) {
    let end = selector.length - 1;
    let begin = 1;
    let prev = 0;
    for (; begin < end; begin += 1) {
      const code = selector.charCodeAt(begin);
      if (code === 124) {
        return;
      }
      if (code === 40 || code === 42 || code === 43 || code === 63 || code === 91 || code === 123 || code === 46 && prev !== 92 || code === 92 && isAlpha(selector.charCodeAt(begin + 1))) {
        break;
      }
      prev = code;
    }
    prev = 0;
    for (; end >= begin; end -= 1) {
      const code = selector.charCodeAt(end);
      if (code === 124) {
        return;
      }
      if (code === 41 || code === 42 || code === 43 || code === 63 || code === 93 || code === 125 || code === 46 && selector.charCodeAt(end - 1) !== 92 || code === 92 && isAlpha(prev)) {
        break;
      }
      prev = code;
    }
    if (end < begin) {
      const skipFirstToken = selector.charCodeAt(1) !== 94;
      const skipLastToken = selector.charCodeAt(selector.length - 1) !== 36;
      tokenizeInPlace(selector.slice(1, selector.length - 1), skipFirstToken, skipLastToken, tokens);
    } else {
      if (begin > 1) {
        tokenizeInPlace(
          selector.slice(1, begin),
          selector.charCodeAt(1) !== 94,
          // skipFirstToken
          true,
          tokens
        );
      }
      if (end < selector.length - 1) {
        tokenizeInPlace(
          selector.slice(end + 1, selector.length - 1),
          true,
          selector.charCodeAt(selector.length - 1) !== 94,
          // skipLastToken
          tokens
        );
      }
    }
  }
  function binSearch2(arr, elt) {
    if (arr.length === 0) {
      return -1;
    }
    let low = 0;
    let high = arr.length - 1;
    while (low <= high) {
      const mid = low + high >>> 1;
      const midVal = arr[mid];
      if (midVal < elt) {
        low = mid + 1;
      } else if (midVal > elt) {
        high = mid - 1;
      } else {
        return mid;
      }
    }
    return -1;
  }
  function binLookup(arr, elt) {
    return binSearch2(arr, elt) !== -1;
  }
  var hasUnicodeRe = /[^\u0000-\u00ff]/;
  function hasUnicode(str) {
    return hasUnicodeRe.test(str);
  }

  // node_modules/@cliqz/adblocker/dist/es6/src/request.js
  var TLDTS_OPTIONS = {
    extractHostname: true,
    mixedInputs: false,
    validateHostname: false
  };
  var NORMALIZED_TYPE_TOKEN = {
    beacon: fastHash("type:beacon"),
    cspReport: fastHash("type:csp"),
    csp_report: fastHash("type:csp"),
    cspviolationreport: fastHash("type:cspviolationreport"),
    document: fastHash("type:document"),
    eventsource: fastHash("type:other"),
    fetch: fastHash("type:xhr"),
    font: fastHash("type:font"),
    image: fastHash("type:image"),
    imageset: fastHash("type:image"),
    mainFrame: fastHash("type:document"),
    main_frame: fastHash("type:document"),
    manifest: fastHash("type:other"),
    media: fastHash("type:media"),
    object: fastHash("type:object"),
    object_subrequest: fastHash("type:object"),
    other: fastHash("type:other"),
    ping: fastHash("type:ping"),
    prefetch: fastHash("type:other"),
    preflight: fastHash("type:preflight"),
    script: fastHash("type:script"),
    signedexchange: fastHash("type:signedexchange"),
    speculative: fastHash("type:other"),
    stylesheet: fastHash("type:stylesheet"),
    subFrame: fastHash("type:subdocument"),
    sub_frame: fastHash("type:subdocument"),
    texttrack: fastHash("type:other"),
    webSocket: fastHash("type:websocket"),
    web_manifest: fastHash("type:other"),
    websocket: fastHash("type:websocket"),
    xhr: fastHash("type:xhr"),
    xml_dtd: fastHash("type:other"),
    xmlhttprequest: fastHash("type:xhr"),
    xslt: fastHash("type:other")
  };
  function hashHostnameBackward(hostname) {
    let hash = HASH_SEED;
    for (let j = hostname.length - 1; j >= 0; j -= 1) {
      hash = hash * 33 ^ hostname.charCodeAt(j);
    }
    return hash >>> 0;
  }
  function getHashesFromLabelsBackward(hostname, end, startOfDomain) {
    TOKENS_BUFFER.reset();
    let hash = HASH_SEED;
    for (let i = end - 1; i >= 0; i -= 1) {
      const code = hostname.charCodeAt(i);
      if (code === 46 && i < startOfDomain) {
        TOKENS_BUFFER.push(hash >>> 0);
      }
      hash = hash * 33 ^ code;
    }
    TOKENS_BUFFER.push(hash >>> 0);
    return TOKENS_BUFFER.slice();
  }
  function getHostnameWithoutPublicSuffix(hostname, domain) {
    let hostnameWithoutPublicSuffix = null;
    const indexOfDot = domain.indexOf(".");
    if (indexOfDot !== -1) {
      const publicSuffix = domain.slice(indexOfDot + 1);
      hostnameWithoutPublicSuffix = hostname.slice(0, -publicSuffix.length - 1);
    }
    return hostnameWithoutPublicSuffix;
  }
  function getEntityHashesFromLabelsBackward(hostname, domain) {
    const hostnameWithoutPublicSuffix = getHostnameWithoutPublicSuffix(hostname, domain);
    if (hostnameWithoutPublicSuffix !== null) {
      return getHashesFromLabelsBackward(hostnameWithoutPublicSuffix, hostnameWithoutPublicSuffix.length, hostnameWithoutPublicSuffix.length);
    }
    return EMPTY_UINT32_ARRAY;
  }
  function getHostnameHashesFromLabelsBackward(hostname, domain) {
    return getHashesFromLabelsBackward(hostname, hostname.length, hostname.length - domain.length);
  }
  function isThirdParty(hostname, domain, sourceHostname, sourceDomain, type) {
    if (type === "main_frame" || type === "mainFrame") {
      return false;
    } else if (domain.length !== 0 && sourceDomain.length !== 0) {
      return domain !== sourceDomain;
    } else if (domain.length !== 0 && sourceHostname.length !== 0) {
      return domain !== sourceHostname;
    } else if (sourceDomain.length !== 0 && hostname.length !== 0) {
      return hostname !== sourceDomain;
    }
    return false;
  }
  var Request = class _Request {
    /**
     * Create an instance of `Request` from raw request details.
     */
    static fromRawDetails({ requestId = "0", tabId = 0, url = "", hostname, domain, sourceUrl = "", sourceHostname, sourceDomain, type = "main_frame", _originalRequestDetails }) {
      url = url.toLowerCase();
      if (hostname === void 0 || domain === void 0) {
        const parsed = parse2(url, TLDTS_OPTIONS);
        hostname = hostname || parsed.hostname || "";
        domain = domain || parsed.domain || "";
      }
      if (sourceHostname === void 0 || sourceDomain === void 0) {
        const parsed = parse2(sourceHostname || sourceDomain || sourceUrl, TLDTS_OPTIONS);
        sourceHostname = sourceHostname || parsed.hostname || "";
        sourceDomain = sourceDomain || parsed.domain || sourceHostname || "";
      }
      return new _Request({
        requestId,
        tabId,
        domain,
        hostname,
        url,
        sourceDomain,
        sourceHostname,
        sourceUrl,
        type,
        _originalRequestDetails
      });
    }
    constructor({ requestId, tabId, type, domain, hostname, url, sourceDomain, sourceHostname, _originalRequestDetails }) {
      this.tokens = void 0;
      this.hostnameHashes = void 0;
      this.entityHashes = void 0;
      this._originalRequestDetails = _originalRequestDetails;
      this.id = requestId;
      this.tabId = tabId;
      this.type = type;
      this.url = url;
      this.hostname = hostname;
      this.domain = domain;
      this.sourceHostnameHashes = sourceHostname.length === 0 ? EMPTY_UINT32_ARRAY : getHostnameHashesFromLabelsBackward(sourceHostname, sourceDomain);
      this.sourceEntityHashes = sourceHostname.length === 0 ? EMPTY_UINT32_ARRAY : getEntityHashesFromLabelsBackward(sourceHostname, sourceDomain);
      this.isThirdParty = isThirdParty(hostname, domain, sourceHostname, sourceDomain, type);
      this.isFirstParty = !this.isThirdParty;
      this.isSupported = true;
      if (this.type === "websocket" || this.url.startsWith("ws:") || this.url.startsWith("wss:")) {
        this.isHttp = false;
        this.isHttps = false;
        this.type = "websocket";
        this.isSupported = true;
      } else if (this.url.startsWith("http:")) {
        this.isHttp = true;
        this.isHttps = false;
      } else if (this.url.startsWith("https:")) {
        this.isHttps = true;
        this.isHttp = false;
      } else if (this.url.startsWith("data:")) {
        this.isHttp = false;
        this.isHttps = false;
        const indexOfComa = this.url.indexOf(",");
        if (indexOfComa !== -1) {
          this.url = this.url.slice(0, indexOfComa);
        }
      } else {
        this.isHttp = false;
        this.isHttps = false;
        this.isSupported = false;
      }
    }
    getHostnameHashes() {
      if (this.hostnameHashes === void 0) {
        this.hostnameHashes = this.hostname.length === 0 ? EMPTY_UINT32_ARRAY : getHostnameHashesFromLabelsBackward(this.hostname, this.domain);
      }
      return this.hostnameHashes;
    }
    getEntityHashes() {
      if (this.entityHashes === void 0) {
        this.entityHashes = this.hostname.length === 0 ? EMPTY_UINT32_ARRAY : getEntityHashesFromLabelsBackward(this.hostname, this.domain);
      }
      return this.entityHashes;
    }
    getTokens() {
      if (this.tokens === void 0) {
        TOKENS_BUFFER.reset();
        for (const hash of this.sourceHostnameHashes) {
          TOKENS_BUFFER.push(hash);
        }
        TOKENS_BUFFER.push(NORMALIZED_TYPE_TOKEN[this.type]);
        tokenizeNoSkipInPlace(this.url, TOKENS_BUFFER);
        this.tokens = TOKENS_BUFFER.slice();
      }
      return this.tokens;
    }
    isMainFrame() {
      return this.type === "main_frame" || this.type === "mainFrame";
    }
    isSubFrame() {
      return this.type === "sub_frame" || this.type === "subFrame";
    }
    /**
     * Calling this method will attempt to guess the type of a request based on
     * information found in `url` only. This can be useful to try and fine-tune
     * the type of a Request when it is not otherwise available or if it was
     * inferred as 'other'.
     */
    guessTypeOfRequest() {
      const currentType = this.type;
      this.type = getRequestType(this.url);
      if (currentType !== this.type) {
        this.tokens = void 0;
      }
      return this.type;
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/engine/domains.js
  var Domains = class _Domains {
    static parse(parts, debug = false) {
      if (parts.length === 0) {
        return void 0;
      }
      const entities = [];
      const notEntities = [];
      const hostnames = [];
      const notHostnames = [];
      for (let hostname of parts) {
        if (hasUnicode(hostname)) {
          hostname = toASCII(hostname);
        }
        const negation = hostname.charCodeAt(0) === 126;
        const entity = hostname.charCodeAt(hostname.length - 1) === 42 && hostname.charCodeAt(hostname.length - 2) === 46;
        const start = negation ? 1 : 0;
        const end = entity ? hostname.length - 2 : hostname.length;
        const hash = hashHostnameBackward(negation === true || entity === true ? hostname.slice(start, end) : hostname);
        if (negation) {
          if (entity) {
            notEntities.push(hash);
          } else {
            notHostnames.push(hash);
          }
        } else {
          if (entity) {
            entities.push(hash);
          } else {
            hostnames.push(hash);
          }
        }
      }
      return new _Domains({
        entities: entities.length !== 0 ? new Uint32Array(entities).sort() : void 0,
        hostnames: hostnames.length !== 0 ? new Uint32Array(hostnames).sort() : void 0,
        notEntities: notEntities.length !== 0 ? new Uint32Array(notEntities).sort() : void 0,
        notHostnames: notHostnames.length !== 0 ? new Uint32Array(notHostnames).sort() : void 0,
        parts: debug === true ? parts.join(",") : void 0
      });
    }
    static deserialize(buffer) {
      const optionalParts = buffer.getUint8();
      return new _Domains({
        entities: (optionalParts & 1) === 1 ? buffer.getUint32Array() : void 0,
        hostnames: (optionalParts & 2) === 2 ? buffer.getUint32Array() : void 0,
        notEntities: (optionalParts & 4) === 4 ? buffer.getUint32Array() : void 0,
        notHostnames: (optionalParts & 8) === 8 ? buffer.getUint32Array() : void 0,
        parts: (optionalParts & 16) === 16 ? buffer.getUTF8() : void 0
      });
    }
    constructor({ entities, hostnames, notEntities, notHostnames, parts }) {
      this.entities = entities;
      this.hostnames = hostnames;
      this.notEntities = notEntities;
      this.notHostnames = notHostnames;
      this.parts = parts;
    }
    updateId(hash) {
      const { hostnames, entities, notHostnames, notEntities } = this;
      if (hostnames !== void 0) {
        for (const hostname of hostnames) {
          hash = hash * 33 ^ hostname;
        }
      }
      if (entities !== void 0) {
        for (const entity of entities) {
          hash = hash * 33 ^ entity;
        }
      }
      if (notHostnames !== void 0) {
        for (const notHostname of notHostnames) {
          hash = hash * 33 ^ notHostname;
        }
      }
      if (notEntities !== void 0) {
        for (const notEntity of notEntities) {
          hash = hash * 33 ^ notEntity;
        }
      }
      return hash;
    }
    serialize(buffer) {
      const index = buffer.getPos();
      buffer.pushUint8(0);
      let optionalParts = 0;
      if (this.entities !== void 0) {
        optionalParts |= 1;
        buffer.pushUint32Array(this.entities);
      }
      if (this.hostnames !== void 0) {
        optionalParts |= 2;
        buffer.pushUint32Array(this.hostnames);
      }
      if (this.notEntities !== void 0) {
        optionalParts |= 4;
        buffer.pushUint32Array(this.notEntities);
      }
      if (this.notHostnames !== void 0) {
        optionalParts |= 8;
        buffer.pushUint32Array(this.notHostnames);
      }
      if (this.parts !== void 0) {
        optionalParts |= 16;
        buffer.pushUTF8(this.parts);
      }
      buffer.setByte(index, optionalParts);
    }
    getSerializedSize() {
      let estimate = 1;
      if (this.entities !== void 0) {
        estimate += sizeOfUint32Array(this.entities);
      }
      if (this.hostnames !== void 0) {
        estimate += sizeOfUint32Array(this.hostnames);
      }
      if (this.notHostnames !== void 0) {
        estimate += sizeOfUint32Array(this.notHostnames);
      }
      if (this.notEntities !== void 0) {
        estimate += sizeOfUint32Array(this.notEntities);
      }
      if (this.parts !== void 0) {
        estimate += sizeOfUTF8(this.parts);
      }
      return estimate;
    }
    match(hostnameHashes, entityHashes) {
      if (this.notHostnames !== void 0) {
        for (const hash of hostnameHashes) {
          if (binLookup(this.notHostnames, hash)) {
            return false;
          }
        }
      }
      if (this.notEntities !== void 0) {
        for (const hash of entityHashes) {
          if (binLookup(this.notEntities, hash)) {
            return false;
          }
        }
      }
      if (this.hostnames !== void 0 || this.entities !== void 0) {
        if (this.hostnames !== void 0) {
          for (const hash of hostnameHashes) {
            if (binLookup(this.hostnames, hash)) {
              return true;
            }
          }
        }
        if (this.entities !== void 0) {
          for (const hash of entityHashes) {
            if (binLookup(this.entities, hash)) {
              return true;
            }
          }
        }
        return false;
      }
      return true;
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/html-filtering.js
  function extractHTMLSelectorFromRule(rule) {
    if (rule.startsWith("^script") === false) {
      return void 0;
    }
    const prefix = ":has-text(";
    const selectors = [];
    let index = 7;
    while (rule.startsWith(prefix, index)) {
      index += prefix.length;
      let currentParsingDepth = 1;
      const startOfSelectorIndex = index;
      let prev = -1;
      for (; index < rule.length && currentParsingDepth !== 0; index += 1) {
        const code = rule.charCodeAt(index);
        if (prev !== 92) {
          if (code === 40) {
            currentParsingDepth += 1;
          }
          if (code === 41) {
            currentParsingDepth -= 1;
          }
        }
        prev = code;
      }
      selectors.push(rule.slice(startOfSelectorIndex, index - 1));
    }
    if (index !== rule.length) {
      return void 0;
    }
    return ["script", selectors];
  }

  // node_modules/@cliqz/adblocker/dist/es6/src/filters/cosmetic.js
  var EMPTY_TOKENS = [EMPTY_UINT32_ARRAY];
  var DEFAULT_HIDDING_STYLE = "display: none !important;";
  var REGEXP_UNICODE_COMMA = new RegExp(/\\u002C/, "g");
  var REGEXP_UNICODE_BACKSLASH = new RegExp(/\\u005C/, "g");
  function isSimpleSelector(selector) {
    for (let i = 1; i < selector.length; i += 1) {
      const code = selector.charCodeAt(i);
      if (!(code === 45 || code === 95 || code >= 48 && code <= 57 || code >= 65 && code <= 90 || code >= 97 && code <= 122)) {
        if (i < selector.length - 1) {
          const nextCode = selector.charCodeAt(i + 1);
          if (code === 91 || code === 46 || code === 58 || code === 32 && (nextCode === 62 || nextCode === 43 || nextCode === 126 || nextCode === 46 || nextCode === 35)) {
            return true;
          }
        }
        return false;
      }
    }
    return true;
  }
  function isSimpleHrefSelector(selector, start) {
    return selector.startsWith('href^="', start) || selector.startsWith('href*="', start) || selector.startsWith('href="', start);
  }
  var isValidCss = (() => {
    const div = typeof document !== "undefined" ? document.createElement("div") : {
      matches: () => {
      }
    };
    const matches3 = (selector) => div.matches(selector);
    const validSelectorRe = /^[#.]?[\w-.]+$/;
    return function isValidCssImpl(selector) {
      if (validSelectorRe.test(selector)) {
        return true;
      }
      try {
        matches3(selector);
      } catch (ex) {
        return false;
      }
      return true;
    };
  })();
  function computeFilterId(mask, selector, domains, style) {
    let hash = 5437 * 33 ^ mask;
    if (selector !== void 0) {
      for (let i = 0; i < selector.length; i += 1) {
        hash = hash * 33 ^ selector.charCodeAt(i);
      }
    }
    if (domains !== void 0) {
      hash = domains.updateId(hash);
    }
    if (style !== void 0) {
      for (let i = 0; i < style.length; i += 1) {
        hash = hash * 33 ^ style.charCodeAt(i);
      }
    }
    return hash >>> 0;
  }
  var CosmeticFilter = class _CosmeticFilter {
    /**
     * Given a line that we know contains a cosmetic filter, create a CosmeticFiler
     * instance out of it. This function should be *very* efficient, as it will be
     * used to parse tens of thousands of lines.
     */
    static parse(line, debug = false) {
      const rawLine = line;
      let mask = 0;
      let selector;
      let domains;
      let style;
      const sharpIndex = line.indexOf("#");
      const afterSharpIndex = sharpIndex + 1;
      let suffixStartIndex = afterSharpIndex + 1;
      if (line.length > afterSharpIndex) {
        if (line[afterSharpIndex] === "@") {
          mask = setBit(
            mask,
            1
            /* COSMETICS_MASK.unhide */
          );
          suffixStartIndex += 1;
        } else if (line[afterSharpIndex] === "?") {
          suffixStartIndex += 1;
        }
      }
      if (suffixStartIndex >= line.length) {
        return null;
      }
      if (sharpIndex > 0) {
        domains = Domains.parse(line.slice(0, sharpIndex).split(","), debug);
      }
      if (line.endsWith(":remove()")) {
        mask = setBit(
          mask,
          64
          /* COSMETICS_MASK.remove */
        );
        mask = setBit(
          mask,
          128
          /* COSMETICS_MASK.extended */
        );
        line = line.slice(0, -9);
      } else if (line.length - suffixStartIndex >= 8 && line.endsWith(")") && line.indexOf(":style(", suffixStartIndex) !== -1) {
        const indexOfStyle = line.indexOf(":style(", suffixStartIndex);
        style = line.slice(indexOfStyle + 7, -1);
        line = line.slice(0, indexOfStyle);
      }
      if (line.charCodeAt(suffixStartIndex) === 94) {
        if (fastStartsWithFrom(line, "script:has-text(", suffixStartIndex + 1) === false || line.charCodeAt(line.length - 1) !== 41) {
          return null;
        }
        selector = line.slice(suffixStartIndex, line.length);
        if (extractHTMLSelectorFromRule(selector) === void 0) {
          return null;
        }
      } else if (line.length - suffixStartIndex > 4 && line.charCodeAt(suffixStartIndex) === 43 && fastStartsWithFrom(line, "+js(", suffixStartIndex)) {
        if ((domains === void 0 || domains.hostnames === void 0 && domains.entities === void 0) && getBit(
          mask,
          1
          /* COSMETICS_MASK.unhide */
        ) === false) {
          return null;
        }
        mask = setBit(
          mask,
          2
          /* COSMETICS_MASK.scriptInject */
        );
        selector = line.slice(suffixStartIndex + 4, line.length - 1);
        if (getBit(
          mask,
          1
          /* COSMETICS_MASK.unhide */
        ) === false && selector.length === 0) {
          return null;
        }
      } else {
        selector = line.slice(suffixStartIndex);
        const selectorType = classifySelector(selector);
        if (selectorType === SelectorType.Extended) {
          mask = setBit(
            mask,
            128
            /* COSMETICS_MASK.extended */
          );
        } else if (selectorType === SelectorType.Invalid || !isValidCss(selector)) {
          return null;
        }
      }
      if (domains === void 0 && getBit(
        mask,
        128
        /* COSMETICS_MASK.extended */
      ) === true) {
        return null;
      }
      if (selector !== void 0) {
        if (hasUnicode(selector)) {
          mask = setBit(
            mask,
            4
            /* COSMETICS_MASK.isUnicode */
          );
        }
        if (getBit(
          mask,
          2
          /* COSMETICS_MASK.scriptInject */
        ) === false && getBit(
          mask,
          64
          /* COSMETICS_MASK.remove */
        ) === false && getBit(
          mask,
          128
          /* COSMETICS_MASK.extended */
        ) === false && selector.startsWith("^") === false) {
          const c0 = selector.charCodeAt(0);
          const c1 = selector.charCodeAt(1);
          const c2 = selector.charCodeAt(2);
          if (getBit(
            mask,
            2
            /* COSMETICS_MASK.scriptInject */
          ) === false) {
            if (c0 === 46 && isSimpleSelector(selector)) {
              mask = setBit(
                mask,
                8
                /* COSMETICS_MASK.isClassSelector */
              );
            } else if (c0 === 35 && isSimpleSelector(selector)) {
              mask = setBit(
                mask,
                16
                /* COSMETICS_MASK.isIdSelector */
              );
            } else if (c0 === 97 && c1 === 91 && c2 === 104 && isSimpleHrefSelector(selector, 2)) {
              mask = setBit(
                mask,
                32
                /* COSMETICS_MASK.isHrefSelector */
              );
            } else if (c0 === 91 && c1 === 104 && isSimpleHrefSelector(selector, 1)) {
              mask = setBit(
                mask,
                32
                /* COSMETICS_MASK.isHrefSelector */
              );
            }
          }
        }
      }
      return new _CosmeticFilter({
        mask,
        rawLine: debug === true ? rawLine : void 0,
        selector,
        style,
        domains
      });
    }
    /**
     * Deserialize cosmetic filters. The code accessing the buffer should be
     * symetrical to the one in `serializeCosmeticFilter`.
     */
    static deserialize(buffer) {
      const mask = buffer.getUint8();
      const isUnicode = getBit(
        mask,
        4
        /* COSMETICS_MASK.isUnicode */
      );
      const optionalParts = buffer.getUint8();
      const selector = isUnicode ? buffer.getUTF8() : buffer.getCosmeticSelector();
      return new _CosmeticFilter({
        // Mandatory fields
        mask,
        selector,
        // Optional fields
        domains: (optionalParts & 1) === 1 ? Domains.deserialize(buffer) : void 0,
        rawLine: (optionalParts & 2) === 2 ? buffer.getRawCosmetic() : void 0,
        style: (optionalParts & 4) === 4 ? buffer.getASCII() : void 0
      });
    }
    constructor({ mask, selector, domains, rawLine, style }) {
      this.mask = mask;
      this.selector = selector;
      this.domains = domains;
      this.style = style;
      this.id = void 0;
      this.rawLine = rawLine;
    }
    isCosmeticFilter() {
      return true;
    }
    isNetworkFilter() {
      return false;
    }
    /**
     * The format of a cosmetic filter is:
     *
     * | mask | selector length | selector... | hostnames length | hostnames...
     *   32     16                              16
     *
     * The header (mask) is 32 bits, then we have a total of 32 bits to store the
     * length of `selector` and `hostnames` (16 bits each).
     *
     * Improvements similar to the onces mentioned in `serializeNetworkFilters`
     * could be applied here, to get a more compact representation.
     */
    serialize(buffer) {
      buffer.pushUint8(this.mask);
      const index = buffer.getPos();
      buffer.pushUint8(0);
      if (this.isUnicode()) {
        buffer.pushUTF8(this.selector);
      } else {
        buffer.pushCosmeticSelector(this.selector);
      }
      let optionalParts = 0;
      if (this.domains !== void 0) {
        optionalParts |= 1;
        this.domains.serialize(buffer);
      }
      if (this.rawLine !== void 0) {
        optionalParts |= 2;
        buffer.pushRawCosmetic(this.rawLine);
      }
      if (this.style !== void 0) {
        optionalParts |= 4;
        buffer.pushASCII(this.style);
      }
      buffer.setByte(index, optionalParts);
    }
    /**
     * Return an estimation of the size (in bytes) needed to persist this filter
     * in a DataView. This does not need to be 100% accurate but should be an
     * upper-bound. It should also be as fast as possible.
     */
    getSerializedSize(compression) {
      let estimate = 1 + 1;
      if (this.isUnicode()) {
        estimate += sizeOfUTF8(this.selector);
      } else {
        estimate += sizeOfCosmeticSelector(this.selector, compression);
      }
      if (this.domains !== void 0) {
        estimate += this.domains.getSerializedSize();
      }
      if (this.rawLine !== void 0) {
        estimate += sizeOfRawCosmetic(this.rawLine, compression);
      }
      if (this.style !== void 0) {
        estimate += sizeOfASCII(this.style);
      }
      return estimate;
    }
    /**
     * Create a more human-readable version of this filter. It is mainly used for
     * debugging purpose, as it will expand the values stored in the bit mask.
     */
    toString() {
      if (this.rawLine !== void 0) {
        return this.rawLine;
      }
      let filter = "";
      if (this.domains !== void 0) {
        if (this.domains.parts !== void 0) {
          filter += this.domains.parts;
        } else {
          filter += "<hostnames>";
        }
      }
      if (this.isUnhide()) {
        filter += "#@#";
      } else {
        filter += "##";
      }
      if (this.isScriptInject()) {
        filter += "+js(";
        filter += this.selector;
        filter += ")";
      } else {
        filter += this.selector;
      }
      return filter;
    }
    match(hostname, domain) {
      if (this.hasHostnameConstraint() === false) {
        return true;
      }
      if (!hostname && this.hasHostnameConstraint()) {
        return false;
      }
      if (this.domains !== void 0) {
        return this.domains.match(hostname.length === 0 ? EMPTY_UINT32_ARRAY : getHostnameHashesFromLabelsBackward(hostname, domain), hostname.length === 0 ? EMPTY_UINT32_ARRAY : getEntityHashesFromLabelsBackward(hostname, domain));
      }
      return true;
    }
    /**
     * Get tokens for this filter. It can be indexed multiple times if multiple
     * hostnames are specified (e.g.: host1,host2##.selector).
     */
    getTokens() {
      const tokens = [];
      if (this.domains !== void 0) {
        const { hostnames, entities } = this.domains;
        if (hostnames !== void 0) {
          for (const hostname of hostnames) {
            tokens.push(new Uint32Array([hostname]));
          }
        }
        if (entities !== void 0) {
          for (const entity of entities) {
            tokens.push(new Uint32Array([entity]));
          }
        }
      }
      if (tokens.length === 0 && this.isUnhide() === false) {
        if (this.isIdSelector() || this.isClassSelector()) {
          let endOfSelector = 1;
          const selector = this.selector;
          for (; endOfSelector < selector.length; endOfSelector += 1) {
            const code = selector.charCodeAt(endOfSelector);
            if (code === 32 || code === 46 || code === 58 || code === 91) {
              break;
            }
          }
          const arr = new Uint32Array(1);
          arr[0] = fastHashBetween(selector, 1, endOfSelector);
          tokens.push(arr);
        } else if (this.isHrefSelector() === true) {
          const selector = this.getSelector();
          let hrefIndex = selector.indexOf("href");
          if (hrefIndex === -1) {
            return EMPTY_TOKENS;
          }
          hrefIndex += 4;
          let skipFirstToken = false;
          let skipLastToken = true;
          if (selector.charCodeAt(hrefIndex) === 42) {
            skipFirstToken = true;
            hrefIndex += 1;
          } else if (selector.charCodeAt(hrefIndex) === 94) {
            hrefIndex += 1;
          } else {
            skipLastToken = false;
          }
          hrefIndex += 2;
          const hrefEnd = selector.indexOf('"', hrefIndex);
          if (hrefEnd === -1) {
            return EMPTY_TOKENS;
          }
          tokens.push(tokenize2(this.selector.slice(hrefIndex, hrefEnd), skipFirstToken, skipLastToken));
        }
      }
      if (tokens.length === 0) {
        return EMPTY_TOKENS;
      }
      return tokens;
    }
    parseScript() {
      const selector = this.getSelector();
      if (selector.length === 0) {
        return void 0;
      }
      const parts = [];
      let index = 0;
      let lastComaIndex = -1;
      let inDoubleQuotes = false;
      let inSingleQuotes = false;
      let inRegexp = false;
      let objectNesting = 0;
      let lastCharIsBackslash = false;
      let inArgument = false;
      for (; index < selector.length; index += 1) {
        const char = selector[index];
        if (lastCharIsBackslash === false) {
          if (inDoubleQuotes === true) {
            if (char === '"') {
              inDoubleQuotes = false;
            }
          } else if (inSingleQuotes === true) {
            if (char === "'") {
              inSingleQuotes = false;
            }
          } else if (objectNesting !== 0) {
            if (char === "{") {
              objectNesting += 1;
            } else if (char === "}") {
              objectNesting -= 1;
            } else if (char === '"') {
              inDoubleQuotes = true;
            } else if (char === "'") {
              inSingleQuotes = true;
            }
          } else if (inRegexp === true) {
            if (char === "/") {
              inRegexp = false;
            }
          } else {
            if (inArgument === false) {
              if (char === " ") ; else if (char === '"' && selector.indexOf('"', index + 1) > 0) {
                inDoubleQuotes = true;
              } else if (char === "'" && selector.indexOf("'", index + 1) > 0) {
                inSingleQuotes = true;
              } else if (char === "{" && selector.indexOf("}", index + 1) > 0) {
                objectNesting += 1;
              } else if (char === "/" && selector.indexOf("/", index + 1) > 0) {
                inRegexp = true;
              } else {
                inArgument = true;
              }
            }
            if (char === ",") {
              parts.push(selector.slice(lastComaIndex + 1, index).trim());
              lastComaIndex = index;
              inArgument = false;
            }
          }
        }
        lastCharIsBackslash = char === "\\";
      }
      parts.push(selector.slice(lastComaIndex + 1).trim());
      if (parts.length === 0) {
        return void 0;
      }
      const args = parts.slice(1).map((part) => {
        if (part.startsWith(`'`) && part.endsWith(`'`) || part.startsWith(`"`) && part.endsWith(`"`)) {
          return part.substring(1, part.length - 1);
        }
        return part;
      }).map((part) => part.replace(REGEXP_UNICODE_COMMA, ",").replace(REGEXP_UNICODE_BACKSLASH, "\\"));
      return { name: parts[0], args };
    }
    getScript(js) {
      const parsed = this.parseScript();
      if (parsed === void 0) {
        return void 0;
      }
      const { name, args } = parsed;
      let script = js.get(name);
      if (script !== void 0) {
        for (let i = 0; i < args.length; i += 1) {
          const arg = args[i].replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
          script = script.replace(`{{${i + 1}}}`, arg);
        }
        return script;
      }
      return void 0;
    }
    hasHostnameConstraint() {
      return this.domains !== void 0;
    }
    getId() {
      if (this.id === void 0) {
        this.id = computeFilterId(this.mask, this.selector, this.domains, this.style);
      }
      return this.id;
    }
    hasCustomStyle() {
      return this.style !== void 0;
    }
    getStyle() {
      return this.style || DEFAULT_HIDDING_STYLE;
    }
    getStyleAttributeHash() {
      return `s${fastHash(this.getStyle())}`;
    }
    getSelector() {
      return this.selector;
    }
    getSelectorAST() {
      return parse(this.getSelector());
    }
    getExtendedSelector() {
      return extractHTMLSelectorFromRule(this.selector);
    }
    isExtended() {
      return getBit(
        this.mask,
        128
        /* COSMETICS_MASK.extended */
      );
    }
    isRemove() {
      return getBit(
        this.mask,
        64
        /* COSMETICS_MASK.remove */
      );
    }
    isUnhide() {
      return getBit(
        this.mask,
        1
        /* COSMETICS_MASK.unhide */
      );
    }
    isScriptInject() {
      return getBit(
        this.mask,
        2
        /* COSMETICS_MASK.scriptInject */
      );
    }
    isCSS() {
      return this.isScriptInject() === false;
    }
    isIdSelector() {
      return getBit(
        this.mask,
        16
        /* COSMETICS_MASK.isIdSelector */
      );
    }
    isClassSelector() {
      return getBit(
        this.mask,
        8
        /* COSMETICS_MASK.isClassSelector */
      );
    }
    isHrefSelector() {
      return getBit(
        this.mask,
        32
        /* COSMETICS_MASK.isHrefSelector */
      );
    }
    isUnicode() {
      return getBit(
        this.mask,
        4
        /* COSMETICS_MASK.isUnicode */
      );
    }
    isHtmlFiltering() {
      return this.getSelector().startsWith("^");
    }
    // A generic hide cosmetic filter is one that:
    //
    // * Do not have a domain specified. "Hide this element on all domains"
    // * Have only domain exceptions specified. "Hide this element on all domains except example.com"
    //
    // For example: ~example.com##.ad  is a generic filter as well!
    isGenericHide() {
      var _a, _b;
      return ((_a = this === null || this === void 0 ? void 0 : this.domains) === null || _a === void 0 ? void 0 : _a.hostnames) === void 0 && ((_b = this === null || this === void 0 ? void 0 : this.domains) === null || _b === void 0 ? void 0 : _b.entities) === void 0;
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/filters/network.js
  var HTTP_HASH = fastHash("http");
  var HTTPS_HASH = fastHash("https");
  function isAllowedHostname(ch) {
    return isDigit(ch) || isAlpha(ch) || ch === 95 || ch === 45 || ch === 46;
  }
  var FROM_ANY = 1 | 2 | 16 | 32 | 64 | 128 | 256 | 512 | 1024 | 2048 | 4096 | 8192;
  var REQUEST_TYPE_TO_MASK = {
    beacon: 256,
    // fromOther?
    document: 1,
    cspviolationreport: 128,
    fetch: 8192,
    font: 2,
    image: 16,
    imageset: 16,
    mainFrame: 1,
    main_frame: 1,
    media: 32,
    object: 64,
    object_subrequest: 64,
    ping: 256,
    // fromOther?
    script: 512,
    stylesheet: 1024,
    subFrame: 2048,
    sub_frame: 2048,
    webSocket: 4096,
    websocket: 4096,
    xhr: 8192,
    xmlhttprequest: 8192,
    // Other
    cspReport: 128,
    csp_report: 128,
    eventsource: 128,
    manifest: 128,
    other: 128,
    prefetch: 128,
    preflight: 128,
    signedexchange: 128,
    speculative: 128,
    texttrack: 128,
    web_manifest: 128,
    xml_dtd: 128,
    xslt: 128
  };
  function getListOfRequestTypesNegated(filter) {
    const types = [];
    if (filter.fromDocument() === false) {
      types.push("document");
    }
    if (filter.fromImage() === false) {
      types.push("image");
    }
    if (filter.fromMedia() === false) {
      types.push("media");
    }
    if (filter.fromObject() === false) {
      types.push("object");
    }
    if (filter.fromOther() === false) {
      types.push("other");
    }
    if (filter.fromPing() === false) {
      types.push("ping");
    }
    if (filter.fromScript() === false) {
      types.push("script");
    }
    if (filter.fromStylesheet() === false) {
      types.push("stylesheet");
    }
    if (filter.fromSubdocument() === false) {
      types.push("sub_frame");
    }
    if (filter.fromWebsocket() === false) {
      types.push("websocket");
    }
    if (filter.fromXmlHttpRequest() === false) {
      types.push("xhr");
    }
    if (filter.fromFont() === false) {
      types.push("font");
    }
    return types;
  }
  function getListOfRequestTypes(filter) {
    const types = [];
    if (filter.fromDocument()) {
      types.push("document");
    }
    if (filter.fromImage()) {
      types.push("image");
    }
    if (filter.fromMedia()) {
      types.push("media");
    }
    if (filter.fromObject()) {
      types.push("object");
    }
    if (filter.fromOther()) {
      types.push("other");
    }
    if (filter.fromPing()) {
      types.push("ping");
    }
    if (filter.fromScript()) {
      types.push("script");
    }
    if (filter.fromStylesheet()) {
      types.push("stylesheet");
    }
    if (filter.fromSubdocument()) {
      types.push("sub_frame");
    }
    if (filter.fromWebsocket()) {
      types.push("websocket");
    }
    if (filter.fromXmlHttpRequest()) {
      types.push("xhr");
    }
    if (filter.fromFont()) {
      types.push("font");
    }
    return types;
  }
  function computeFilterId2(csp, mask, filter, hostname, domains, denyallow, redirect) {
    let hash = 7907 * 33 ^ mask;
    if (csp !== void 0) {
      for (let i = 0; i < csp.length; i += 1) {
        hash = hash * 33 ^ csp.charCodeAt(i);
      }
    }
    if (domains !== void 0) {
      hash = domains.updateId(hash);
    }
    if (denyallow !== void 0) {
      hash = denyallow.updateId(hash);
    }
    if (filter !== void 0) {
      for (let i = 0; i < filter.length; i += 1) {
        hash = hash * 33 ^ filter.charCodeAt(i);
      }
    }
    if (hostname !== void 0) {
      for (let i = 0; i < hostname.length; i += 1) {
        hash = hash * 33 ^ hostname.charCodeAt(i);
      }
    }
    if (redirect !== void 0) {
      for (let i = 0; i < redirect.length; i += 1) {
        hash = hash * 33 ^ redirect.charCodeAt(i);
      }
    }
    return hash >>> 0;
  }
  function compileRegex(filter, isLeftAnchor, isRightAnchor, isFullRegex) {
    if (isFullRegex === true) {
      return new RegExp(filter.slice(1, filter.length - 1), "i");
    }
    filter = filter.replace(/([|.$+?{}()[\]\\])/g, "\\$1");
    filter = filter.replace(/\*/g, ".*");
    filter = filter.replace(/\^/g, "(?:[^\\w\\d_.%-]|$)");
    if (isRightAnchor) {
      filter = `${filter}$`;
    }
    if (isLeftAnchor) {
      filter = `^${filter}`;
    }
    return new RegExp(filter);
  }
  var MATCH_ALL = new RegExp("");
  var NetworkFilter = class _NetworkFilter {
    static parse(line, debug = false) {
      let mask = 32768 | 16384 | 8 | 4;
      let cptMaskPositive = 0;
      let cptMaskNegative = FROM_ANY;
      let hostname;
      let domains;
      let denyallow;
      let redirect;
      let csp;
      let filterIndexStart = 0;
      let filterIndexEnd = line.length;
      if (line.charCodeAt(0) === 64 && line.charCodeAt(1) === 64) {
        filterIndexStart += 2;
        mask = setBit(
          mask,
          134217728
          /* NETWORK_FILTER_MASK.isException */
        );
      }
      const optionsIndex = line.lastIndexOf("$");
      if (optionsIndex !== -1 && line.charCodeAt(optionsIndex + 1) !== 47) {
        filterIndexEnd = optionsIndex;
        for (const rawOption of line.slice(optionsIndex + 1).split(",")) {
          const negation = rawOption.charCodeAt(0) === 126;
          let option = negation === true ? rawOption.slice(1) : rawOption;
          let optionValue = "";
          const indexOfEqual = option.indexOf("=");
          if (indexOfEqual !== -1) {
            optionValue = option.slice(indexOfEqual + 1);
            option = option.slice(0, indexOfEqual);
          }
          switch (option) {
            case "denyallow": {
              denyallow = Domains.parse(optionValue.split("|"), debug);
              break;
            }
            case "domain":
            case "from": {
              if (optionValue.charCodeAt(0) === 124 || optionValue.charCodeAt(optionValue.length - 1) === 124) {
                return null;
              }
              domains = Domains.parse(optionValue.split("|"), debug);
              break;
            }
            case "badfilter":
              mask = setBit(
                mask,
                131072
                /* NETWORK_FILTER_MASK.isBadFilter */
              );
              break;
            case "important":
              if (negation) {
                return null;
              }
              mask = setBit(
                mask,
                1048576
                /* NETWORK_FILTER_MASK.isImportant */
              );
              break;
            case "match-case":
              if (negation) {
                return null;
              }
              break;
            case "3p":
            case "third-party":
              if (negation) {
                mask = clearBit(
                  mask,
                  32768
                  /* NETWORK_FILTER_MASK.thirdParty */
                );
              } else {
                mask = clearBit(
                  mask,
                  16384
                  /* NETWORK_FILTER_MASK.firstParty */
                );
              }
              break;
            case "1p":
            case "first-party":
              if (negation) {
                mask = clearBit(
                  mask,
                  16384
                  /* NETWORK_FILTER_MASK.firstParty */
                );
              } else {
                mask = clearBit(
                  mask,
                  32768
                  /* NETWORK_FILTER_MASK.thirdParty */
                );
              }
              break;
            case "redirect-rule":
            case "redirect":
              if (negation) {
                return null;
              }
              if (optionValue.length === 0) {
                return null;
              }
              if (option === "redirect-rule") {
                mask = setBit(
                  mask,
                  536870912
                  /* NETWORK_FILTER_MASK.isRedirectRule */
                );
              }
              redirect = optionValue;
              break;
            case "csp":
              if (negation) {
                return null;
              }
              mask = setBit(
                mask,
                262144
                /* NETWORK_FILTER_MASK.isCSP */
              );
              if (optionValue.length > 0) {
                csp = optionValue;
              }
              break;
            case "ehide":
            case "elemhide":
              if (negation) {
                return null;
              }
              mask = setBit(
                mask,
                524288
                /* NETWORK_FILTER_MASK.isGenericHide */
              );
              mask = setBit(
                mask,
                2097152
                /* NETWORK_FILTER_MASK.isSpecificHide */
              );
              break;
            case "shide":
            case "specifichide":
              if (negation) {
                return null;
              }
              mask = setBit(
                mask,
                2097152
                /* NETWORK_FILTER_MASK.isSpecificHide */
              );
              break;
            case "ghide":
            case "generichide":
              if (negation) {
                return null;
              }
              mask = setBit(
                mask,
                524288
                /* NETWORK_FILTER_MASK.isGenericHide */
              );
              break;
            case "inline-script":
              if (negation) {
                return null;
              }
              mask = setBit(
                mask,
                262144
                /* NETWORK_FILTER_MASK.isCSP */
              );
              csp = "script-src 'self' 'unsafe-eval' http: https: data: blob: mediastream: filesystem:";
              break;
            case "inline-font":
              if (negation) {
                return null;
              }
              mask = setBit(
                mask,
                262144
                /* NETWORK_FILTER_MASK.isCSP */
              );
              csp = "font-src 'self' 'unsafe-eval' http: https: data: blob: mediastream: filesystem:";
              break;
            default: {
              let optionMask = 0;
              switch (option) {
                case "all":
                  if (negation) {
                    return null;
                  }
                  break;
                case "image":
                  optionMask = 16;
                  break;
                case "media":
                  optionMask = 32;
                  break;
                case "object":
                case "object-subrequest":
                  optionMask = 64;
                  break;
                case "other":
                  optionMask = 128;
                  break;
                case "ping":
                case "beacon":
                  optionMask = 256;
                  break;
                case "script":
                  optionMask = 512;
                  break;
                case "css":
                case "stylesheet":
                  optionMask = 1024;
                  break;
                case "frame":
                case "subdocument":
                  optionMask = 2048;
                  break;
                case "xhr":
                case "xmlhttprequest":
                  optionMask = 8192;
                  break;
                case "websocket":
                  optionMask = 4096;
                  break;
                case "font":
                  optionMask = 2;
                  break;
                case "doc":
                case "document":
                  optionMask = 1;
                  break;
                default:
                  return null;
              }
              if (negation) {
                cptMaskNegative = clearBit(cptMaskNegative, optionMask);
              } else {
                cptMaskPositive = setBit(cptMaskPositive, optionMask);
              }
              break;
            }
          }
        }
      }
      if (cptMaskPositive === 0) {
        mask |= cptMaskNegative;
      } else if (cptMaskNegative === FROM_ANY) {
        mask |= cptMaskPositive;
      } else {
        mask |= cptMaskPositive & cptMaskNegative;
      }
      let filter;
      if (filterIndexEnd - filterIndexStart >= 2 && line.charCodeAt(filterIndexStart) === 47 && line.charCodeAt(filterIndexEnd - 1) === 47) {
        filter = line.slice(filterIndexStart, filterIndexEnd);
        try {
          compileRegex(
            filter,
            false,
            false,
            true
            /* isFullRegex */
          );
        } catch (ex) {
          return null;
        }
        mask = setBit(
          mask,
          4194304
          /* NETWORK_FILTER_MASK.isFullRegex */
        );
      } else {
        if (filterIndexEnd > 0 && line.charCodeAt(filterIndexEnd - 1) === 124) {
          mask = setBit(
            mask,
            67108864
            /* NETWORK_FILTER_MASK.isRightAnchor */
          );
          filterIndexEnd -= 1;
        }
        if (filterIndexStart < filterIndexEnd && line.charCodeAt(filterIndexStart) === 124) {
          if (filterIndexStart < filterIndexEnd - 1 && line.charCodeAt(filterIndexStart + 1) === 124) {
            mask = setBit(
              mask,
              268435456
              /* NETWORK_FILTER_MASK.isHostnameAnchor */
            );
            filterIndexStart += 2;
          } else {
            mask = setBit(
              mask,
              33554432
              /* NETWORK_FILTER_MASK.isLeftAnchor */
            );
            filterIndexStart += 1;
          }
        }
        if (getBit(
          mask,
          268435456
          /* NETWORK_FILTER_MASK.isHostnameAnchor */
        )) {
          let firstSeparator = filterIndexStart;
          while (firstSeparator < filterIndexEnd && isAllowedHostname(line.charCodeAt(firstSeparator)) === true) {
            firstSeparator += 1;
          }
          if (firstSeparator === filterIndexEnd) {
            hostname = line.slice(filterIndexStart, filterIndexEnd);
            filterIndexStart = filterIndexEnd;
          } else {
            hostname = line.slice(filterIndexStart, firstSeparator);
            filterIndexStart = firstSeparator;
            const separatorCode = line.charCodeAt(firstSeparator);
            if (separatorCode === 94) {
              if (filterIndexEnd - filterIndexStart === 1) {
                filterIndexStart = filterIndexEnd;
                mask = setBit(
                  mask,
                  67108864
                  /* NETWORK_FILTER_MASK.isRightAnchor */
                );
              } else {
                mask = setBit(
                  mask,
                  8388608
                  /* NETWORK_FILTER_MASK.isRegex */
                );
                mask = setBit(
                  mask,
                  33554432
                  /* NETWORK_FILTER_MASK.isLeftAnchor */
                );
              }
            } else if (separatorCode === 42) {
              mask = setBit(
                mask,
                8388608
                /* NETWORK_FILTER_MASK.isRegex */
              );
            } else {
              mask = setBit(
                mask,
                33554432
                /* NETWORK_FILTER_MASK.isLeftAnchor */
              );
            }
          }
        }
        if (filterIndexEnd - filterIndexStart > 0 && line.charCodeAt(filterIndexEnd - 1) === 42) {
          filterIndexEnd -= 1;
        }
        if (getBit(
          mask,
          268435456
          /* NETWORK_FILTER_MASK.isHostnameAnchor */
        ) === false && filterIndexEnd - filterIndexStart > 0 && line.charCodeAt(filterIndexStart) === 42) {
          mask = clearBit(
            mask,
            33554432
            /* NETWORK_FILTER_MASK.isLeftAnchor */
          );
          filterIndexStart += 1;
        }
        if (getBit(
          mask,
          33554432
          /* NETWORK_FILTER_MASK.isLeftAnchor */
        )) {
          if (filterIndexEnd - filterIndexStart === 5 && fastStartsWithFrom(line, "ws://", filterIndexStart)) {
            mask = setBit(
              mask,
              4096
              /* NETWORK_FILTER_MASK.fromWebsocket */
            );
            mask = clearBit(
              mask,
              33554432
              /* NETWORK_FILTER_MASK.isLeftAnchor */
            );
            mask = clearBit(
              mask,
              4
              /* NETWORK_FILTER_MASK.fromHttp */
            );
            mask = clearBit(
              mask,
              8
              /* NETWORK_FILTER_MASK.fromHttps */
            );
            filterIndexStart = filterIndexEnd;
          } else if (filterIndexEnd - filterIndexStart === 7 && fastStartsWithFrom(line, "http://", filterIndexStart)) {
            mask = setBit(
              mask,
              4
              /* NETWORK_FILTER_MASK.fromHttp */
            );
            mask = clearBit(
              mask,
              8
              /* NETWORK_FILTER_MASK.fromHttps */
            );
            mask = clearBit(
              mask,
              33554432
              /* NETWORK_FILTER_MASK.isLeftAnchor */
            );
            filterIndexStart = filterIndexEnd;
          } else if (filterIndexEnd - filterIndexStart === 8 && fastStartsWithFrom(line, "https://", filterIndexStart)) {
            mask = setBit(
              mask,
              8
              /* NETWORK_FILTER_MASK.fromHttps */
            );
            mask = clearBit(
              mask,
              4
              /* NETWORK_FILTER_MASK.fromHttp */
            );
            mask = clearBit(
              mask,
              33554432
              /* NETWORK_FILTER_MASK.isLeftAnchor */
            );
            filterIndexStart = filterIndexEnd;
          } else if (filterIndexEnd - filterIndexStart === 8 && fastStartsWithFrom(line, "http*://", filterIndexStart)) {
            mask = setBit(
              mask,
              8
              /* NETWORK_FILTER_MASK.fromHttps */
            );
            mask = setBit(
              mask,
              4
              /* NETWORK_FILTER_MASK.fromHttp */
            );
            mask = clearBit(
              mask,
              33554432
              /* NETWORK_FILTER_MASK.isLeftAnchor */
            );
            filterIndexStart = filterIndexEnd;
          }
        }
        if (filterIndexEnd - filterIndexStart > 0) {
          filter = line.slice(filterIndexStart, filterIndexEnd).toLowerCase();
          mask = setNetworkMask(mask, 16777216, hasUnicode(filter));
          if (getBit(
            mask,
            8388608
            /* NETWORK_FILTER_MASK.isRegex */
          ) === false) {
            mask = setNetworkMask(mask, 8388608, checkIsRegex(filter, 0, filter.length));
          }
        }
        if (hostname !== void 0) {
          hostname = hostname.toLowerCase();
          if (hasUnicode(hostname)) {
            mask = setNetworkMask(mask, 16777216, true);
            hostname = toASCII(hostname);
          }
        }
      }
      return new _NetworkFilter({
        csp,
        filter,
        hostname,
        mask,
        domains,
        denyallow,
        rawLine: debug === true ? line : void 0,
        redirect,
        regex: void 0
      });
    }
    /**
     * Deserialize network filters. The code accessing the buffer should be
     * symetrical to the one in `serializeNetworkFilter`.
     */
    static deserialize(buffer) {
      const mask = buffer.getUint32();
      const optionalParts = buffer.getUint8();
      const isUnicode = getBit(
        mask,
        16777216
        /* NETWORK_FILTER_MASK.isUnicode */
      );
      return new _NetworkFilter({
        // Mandatory field
        mask,
        // Optional parts
        csp: (optionalParts & 1) === 1 ? buffer.getNetworkCSP() : void 0,
        filter: (optionalParts & 2) === 2 ? isUnicode ? buffer.getUTF8() : buffer.getNetworkFilter() : void 0,
        hostname: (optionalParts & 4) === 4 ? buffer.getNetworkHostname() : void 0,
        domains: (optionalParts & 8) === 8 ? Domains.deserialize(buffer) : void 0,
        rawLine: (optionalParts & 16) === 16 ? buffer.getRawNetwork() : void 0,
        redirect: (optionalParts & 32) === 32 ? buffer.getNetworkRedirect() : void 0,
        denyallow: (optionalParts & 64) === 64 ? Domains.deserialize(buffer) : void 0,
        regex: void 0
      });
    }
    constructor({ csp, filter, hostname, mask, domains, denyallow, rawLine, redirect, regex }) {
      this.csp = csp;
      this.filter = filter;
      this.hostname = hostname;
      this.mask = mask;
      this.domains = domains;
      this.denyallow = denyallow;
      this.redirect = redirect;
      this.rawLine = rawLine;
      this.id = void 0;
      this.regex = regex;
    }
    isCosmeticFilter() {
      return false;
    }
    isNetworkFilter() {
      return true;
    }
    match(request) {
      return checkOptions(this, request) && checkPattern(this, request);
    }
    /**
     * To allow for a more compact representation of network filters, the
     * representation is composed of a mandatory header, and some optional
     *
     * Header:
     * =======
     *
     *  | opt | mask
     *     8     32
     *
     * For an empty filter having no pattern, hostname, the minimum size is: 42 bits.
     *
     * Then for each optional part (filter, hostname optDomains, optNotDomains,
     * redirect), it takes 16 bits for the length of the string + the length of the
     * string in bytes.
     *
     * The optional parts are written in order of there number of occurrence in the
     * filter list used by the adblocker. The most common being `hostname`, then
     * `filter`, `optDomains`, `optNotDomains`, `redirect`.
     *
     * Example:
     * ========
     *
     * @@||cliqz.com would result in a serialized version:
     *
     * | 1 | mask | 9 | c | l | i | q | z | . | c | o | m  (16 bytes)
     *
     * In this case, the serialized version is actually bigger than the original
     * filter, but faster to deserialize. In the future, we could optimize the
     * representation to compact small filters better.
     *
     * Ideas:
     *  * variable length encoding for the mask (if not option, take max 1 byte).
     *  * first byte could contain the mask as well if small enough.
     *  * when packing ascii string, store several of them in each byte.
     */
    serialize(buffer) {
      buffer.pushUint32(this.mask);
      const index = buffer.getPos();
      buffer.pushUint8(0);
      let optionalParts = 0;
      if (this.csp !== void 0) {
        optionalParts |= 1;
        buffer.pushNetworkCSP(this.csp);
      }
      if (this.filter !== void 0) {
        optionalParts |= 2;
        if (this.isUnicode()) {
          buffer.pushUTF8(this.filter);
        } else {
          buffer.pushNetworkFilter(this.filter);
        }
      }
      if (this.hostname !== void 0) {
        optionalParts |= 4;
        buffer.pushNetworkHostname(this.hostname);
      }
      if (this.domains !== void 0) {
        optionalParts |= 8;
        this.domains.serialize(buffer);
      }
      if (this.rawLine !== void 0) {
        optionalParts |= 16;
        buffer.pushRawNetwork(this.rawLine);
      }
      if (this.redirect !== void 0) {
        optionalParts |= 32;
        buffer.pushNetworkRedirect(this.redirect);
      }
      if (this.denyallow !== void 0) {
        optionalParts |= 64;
        this.denyallow.serialize(buffer);
      }
      buffer.setByte(index, optionalParts);
    }
    getSerializedSize(compression) {
      let estimate = 4 + 1;
      if (this.csp !== void 0) {
        estimate += sizeOfNetworkCSP(this.csp, compression);
      }
      if (this.filter !== void 0) {
        if (this.isUnicode() === true) {
          estimate += sizeOfUTF8(this.filter);
        } else {
          estimate += sizeOfNetworkFilter(this.filter, compression);
        }
      }
      if (this.hostname !== void 0) {
        estimate += sizeOfNetworkHostname(this.hostname, compression);
      }
      if (this.domains !== void 0) {
        estimate += this.domains.getSerializedSize();
      }
      if (this.rawLine !== void 0) {
        estimate += sizeOfRawNetwork(this.rawLine, compression);
      }
      if (this.redirect !== void 0) {
        estimate += sizeOfNetworkRedirect(this.redirect, compression);
      }
      if (this.denyallow !== void 0) {
        estimate += this.denyallow.getSerializedSize();
      }
      return estimate;
    }
    /**
     * Tries to recreate the original representation of the filter (adblock
     * syntax) from the internal representation. If `rawLine` is set (when filters
     * are parsed in `debug` mode for example), then it is returned directly.
     * Otherwise, we try to stick as closely as possible to the original form;
     * there are things which cannot be recovered though, like domains options
     * of which only hashes are stored.
     */
    toString(modifierReplacer) {
      if (this.rawLine !== void 0) {
        return this.rawLine;
      }
      let filter = "";
      if (this.isException()) {
        filter += "@@";
      }
      if (this.isHostnameAnchor()) {
        filter += "||";
      } else if (this.fromHttp() !== this.fromHttps()) {
        if (this.fromHttp()) {
          filter += "|http://";
        } else {
          filter += "|https://";
        }
      } else if (this.isLeftAnchor()) {
        filter += "|";
      }
      if (this.hasHostname()) {
        filter += this.getHostname();
        filter += "^";
      }
      if (this.isFullRegex()) {
        filter += `/${this.getRegex().source}/`;
      } else if (this.isRegex()) {
        filter += this.getRegex().source;
      } else {
        filter += this.getFilter();
      }
      if (this.isRightAnchor() && filter[filter.length - 1] !== "^") {
        filter += "|";
      }
      const options = [];
      if (this.fromAny() === false) {
        const numberOfCptOptions = bitCount(this.getCptMask());
        const numberOfNegatedOptions = bitCount(FROM_ANY) - numberOfCptOptions;
        if (numberOfNegatedOptions < numberOfCptOptions) {
          for (const type of getListOfRequestTypesNegated(this)) {
            options.push(`~${type}`);
          }
        } else {
          for (const type of getListOfRequestTypes(this)) {
            options.push(type);
          }
        }
      }
      if (this.isImportant()) {
        options.push("important");
      }
      if (this.isRedirectRule()) {
        options.push(`redirect-rule=${this.getRedirect()}`);
      } else if (this.isRedirect()) {
        options.push(`redirect=${this.getRedirect()}`);
      }
      if (this.isCSP()) {
        options.push(`csp=${this.csp}`);
      }
      if (this.isElemHide()) {
        options.push("elemhide");
      }
      if (this.isSpecificHide()) {
        options.push("specifichide");
      }
      if (this.isGenericHide()) {
        options.push("generichide");
      }
      if (this.firstParty() !== this.thirdParty()) {
        if (this.firstParty()) {
          options.push("1p");
        }
        if (this.thirdParty()) {
          options.push("3p");
        }
      }
      if (this.domains !== void 0) {
        if (this.domains.parts !== void 0) {
          options.push(`domain=${this.domains.parts}`);
        } else {
          options.push("domain=<hashed>");
        }
      }
      if (this.denyallow !== void 0) {
        if (this.denyallow.parts !== void 0) {
          options.push(`denyallow=${this.denyallow.parts}`);
        } else {
          options.push("denyallow=<hashed>");
        }
      }
      if (this.isBadFilter()) {
        options.push("badfilter");
      }
      if (options.length > 0) {
        if (typeof modifierReplacer === "function") {
          filter += `$${options.map(modifierReplacer).join(",")}`;
        } else {
          filter += `$${options.join(",")}`;
        }
      }
      return filter;
    }
    // Public API (Read-Only)
    getIdWithoutBadFilter() {
      return computeFilterId2(this.csp, this.mask & ~131072, this.filter, this.hostname, this.domains, this.denyallow, this.redirect);
    }
    getId() {
      if (this.id === void 0) {
        this.id = computeFilterId2(this.csp, this.mask, this.filter, this.hostname, this.domains, this.denyallow, this.redirect);
      }
      return this.id;
    }
    hasFilter() {
      return this.filter !== void 0;
    }
    hasDomains() {
      return this.domains !== void 0;
    }
    getMask() {
      return this.mask;
    }
    getCptMask() {
      return this.getMask() & FROM_ANY;
    }
    isRedirect() {
      return this.redirect !== void 0;
    }
    isRedirectRule() {
      return getBit(
        this.mask,
        536870912
        /* NETWORK_FILTER_MASK.isRedirectRule */
      );
    }
    getRedirect() {
      return this.redirect || "";
    }
    hasHostname() {
      return this.hostname !== void 0;
    }
    getHostname() {
      return this.hostname || "";
    }
    getFilter() {
      return this.filter || "";
    }
    getRegex() {
      if (this.regex === void 0) {
        this.regex = this.filter !== void 0 && this.isRegex() ? compileRegex(this.filter, this.isLeftAnchor(), this.isRightAnchor(), this.isFullRegex()) : MATCH_ALL;
      }
      return this.regex;
    }
    getTokens() {
      TOKENS_BUFFER.reset();
      if (this.domains !== void 0 && this.domains.hostnames !== void 0 && this.domains.entities === void 0 && this.domains.notHostnames === void 0 && this.domains.notEntities === void 0 && this.domains.hostnames.length === 1) {
        TOKENS_BUFFER.push(this.domains.hostnames[0]);
      }
      if (this.isFullRegex() === false) {
        if (this.filter !== void 0) {
          const skipLastToken = !this.isRightAnchor();
          const skipFirstToken = !this.isLeftAnchor();
          tokenizeWithWildcardsInPlace(this.filter, skipFirstToken, skipLastToken, TOKENS_BUFFER);
        }
        if (this.hostname !== void 0) {
          tokenizeInPlace(this.hostname, false, this.filter !== void 0 && this.filter.charCodeAt(0) === 42, TOKENS_BUFFER);
        }
      } else if (this.filter !== void 0) {
        tokenizeRegexInPlace(this.filter, TOKENS_BUFFER);
      }
      if (TOKENS_BUFFER.empty() === true && this.domains !== void 0 && this.domains.hostnames !== void 0 && this.domains.entities === void 0 && this.domains.notHostnames === void 0 && this.domains.notEntities === void 0) {
        const result = [];
        for (const hostname of this.domains.hostnames) {
          const arr = new Uint32Array(1);
          arr[0] = hostname;
          result.push(arr);
        }
        return result;
      }
      if (TOKENS_BUFFER.empty() === true && this.fromAny() === false) {
        const types = getListOfRequestTypes(this);
        if (types.length !== 0) {
          const result = [];
          for (const type of types) {
            const arr = new Uint32Array(1);
            arr[0] = NORMALIZED_TYPE_TOKEN[type];
            result.push(arr);
          }
          return result;
        }
      }
      if (this.fromHttp() === true && this.fromHttps() === false) {
        TOKENS_BUFFER.push(HTTP_HASH);
      } else if (this.fromHttps() === true && this.fromHttp() === false) {
        TOKENS_BUFFER.push(HTTPS_HASH);
      }
      return [TOKENS_BUFFER.slice()];
    }
    /**
     * Check if this filter should apply to a request with this content type.
     */
    isCptAllowed(cpt) {
      const mask = REQUEST_TYPE_TO_MASK[cpt];
      if (mask !== void 0) {
        return getBit(this.mask, mask);
      }
      return this.fromAny();
    }
    isException() {
      return getBit(
        this.mask,
        134217728
        /* NETWORK_FILTER_MASK.isException */
      );
    }
    isHostnameAnchor() {
      return getBit(
        this.mask,
        268435456
        /* NETWORK_FILTER_MASK.isHostnameAnchor */
      );
    }
    isRightAnchor() {
      return getBit(
        this.mask,
        67108864
        /* NETWORK_FILTER_MASK.isRightAnchor */
      );
    }
    isLeftAnchor() {
      return getBit(
        this.mask,
        33554432
        /* NETWORK_FILTER_MASK.isLeftAnchor */
      );
    }
    isImportant() {
      return getBit(
        this.mask,
        1048576
        /* NETWORK_FILTER_MASK.isImportant */
      );
    }
    isFullRegex() {
      return getBit(
        this.mask,
        4194304
        /* NETWORK_FILTER_MASK.isFullRegex */
      );
    }
    isRegex() {
      return getBit(
        this.mask,
        8388608
        /* NETWORK_FILTER_MASK.isRegex */
      ) || getBit(
        this.mask,
        4194304
        /* NETWORK_FILTER_MASK.isFullRegex */
      );
    }
    isPlain() {
      return !this.isRegex();
    }
    isCSP() {
      return getBit(
        this.mask,
        262144
        /* NETWORK_FILTER_MASK.isCSP */
      );
    }
    isElemHide() {
      return this.isSpecificHide() && this.isGenericHide();
    }
    isSpecificHide() {
      return getBit(
        this.mask,
        2097152
        /* NETWORK_FILTER_MASK.isSpecificHide */
      );
    }
    isGenericHide() {
      return getBit(
        this.mask,
        524288
        /* NETWORK_FILTER_MASK.isGenericHide */
      );
    }
    isBadFilter() {
      return getBit(
        this.mask,
        131072
        /* NETWORK_FILTER_MASK.isBadFilter */
      );
    }
    isUnicode() {
      return getBit(
        this.mask,
        16777216
        /* NETWORK_FILTER_MASK.isUnicode */
      );
    }
    fromAny() {
      return this.getCptMask() === FROM_ANY;
    }
    thirdParty() {
      return getBit(
        this.mask,
        32768
        /* NETWORK_FILTER_MASK.thirdParty */
      );
    }
    firstParty() {
      return getBit(
        this.mask,
        16384
        /* NETWORK_FILTER_MASK.firstParty */
      );
    }
    fromImage() {
      return getBit(
        this.mask,
        16
        /* NETWORK_FILTER_MASK.fromImage */
      );
    }
    fromMedia() {
      return getBit(
        this.mask,
        32
        /* NETWORK_FILTER_MASK.fromMedia */
      );
    }
    fromObject() {
      return getBit(
        this.mask,
        64
        /* NETWORK_FILTER_MASK.fromObject */
      );
    }
    fromOther() {
      return getBit(
        this.mask,
        128
        /* NETWORK_FILTER_MASK.fromOther */
      );
    }
    fromPing() {
      return getBit(
        this.mask,
        256
        /* NETWORK_FILTER_MASK.fromPing */
      );
    }
    fromScript() {
      return getBit(
        this.mask,
        512
        /* NETWORK_FILTER_MASK.fromScript */
      );
    }
    fromStylesheet() {
      return getBit(
        this.mask,
        1024
        /* NETWORK_FILTER_MASK.fromStylesheet */
      );
    }
    fromDocument() {
      return getBit(
        this.mask,
        1
        /* NETWORK_FILTER_MASK.fromDocument */
      );
    }
    fromSubdocument() {
      return getBit(
        this.mask,
        2048
        /* NETWORK_FILTER_MASK.fromSubdocument */
      );
    }
    fromWebsocket() {
      return getBit(
        this.mask,
        4096
        /* NETWORK_FILTER_MASK.fromWebsocket */
      );
    }
    fromHttp() {
      return getBit(
        this.mask,
        4
        /* NETWORK_FILTER_MASK.fromHttp */
      );
    }
    fromHttps() {
      return getBit(
        this.mask,
        8
        /* NETWORK_FILTER_MASK.fromHttps */
      );
    }
    fromXmlHttpRequest() {
      return getBit(
        this.mask,
        8192
        /* NETWORK_FILTER_MASK.fromXmlHttpRequest */
      );
    }
    fromFont() {
      return getBit(
        this.mask,
        2
        /* NETWORK_FILTER_MASK.fromFont */
      );
    }
  };
  function setNetworkMask(mask, m, value) {
    if (value === true) {
      return setBit(mask, m);
    }
    return clearBit(mask, m);
  }
  function checkIsRegex(filter, start, end) {
    const indexOfSeparator = filter.indexOf("^", start);
    if (indexOfSeparator !== -1 && indexOfSeparator < end) {
      return true;
    }
    const indexOfWildcard = filter.indexOf("*", start);
    return indexOfWildcard !== -1 && indexOfWildcard < end;
  }
  function isAnchoredByHostname(filterHostname, hostname, isFollowedByWildcard) {
    if (filterHostname.length === 0) {
      return true;
    }
    if (filterHostname.length > hostname.length) {
      return false;
    }
    if (filterHostname.length === hostname.length) {
      return filterHostname === hostname;
    }
    const matchIndex = hostname.indexOf(filterHostname);
    if (matchIndex === -1) {
      return false;
    }
    if (matchIndex === 0) {
      return isFollowedByWildcard === true || hostname.charCodeAt(filterHostname.length) === 46 || filterHostname.charCodeAt(filterHostname.length - 1) === 46;
    }
    if (hostname.length === matchIndex + filterHostname.length) {
      return hostname.charCodeAt(matchIndex - 1) === 46 || filterHostname.charCodeAt(0) === 46;
    }
    return (isFollowedByWildcard === true || hostname.charCodeAt(filterHostname.length) === 46 || filterHostname.charCodeAt(filterHostname.length - 1) === 46) && (hostname.charCodeAt(matchIndex - 1) === 46 || filterHostname.charCodeAt(0) === 46);
  }
  function checkPattern(filter, request) {
    const pattern = filter.getFilter();
    if (filter.isHostnameAnchor() === true) {
      const filterHostname = filter.getHostname();
      if (isAnchoredByHostname(
        filterHostname,
        request.hostname,
        filter.filter !== void 0 && filter.filter.charCodeAt(0) === 42
        /* '*' */
      ) === false) {
        return false;
      }
      if (filter.isRegex()) {
        return filter.getRegex().test(request.url.slice(request.url.indexOf(filterHostname) + filterHostname.length));
      } else if (filter.isRightAnchor() && filter.isLeftAnchor()) {
        const urlAfterHostname = request.url.slice(request.url.indexOf(filterHostname) + filterHostname.length);
        return pattern === urlAfterHostname;
      } else if (filter.isRightAnchor()) {
        const requestHostname = request.hostname;
        if (filter.hasFilter() === false) {
          return filterHostname.length === requestHostname.length || requestHostname.endsWith(filterHostname);
        } else {
          return request.url.endsWith(pattern);
        }
      } else if (filter.isLeftAnchor()) {
        return fastStartsWithFrom(request.url, pattern, request.url.indexOf(filterHostname) + filterHostname.length);
      }
      if (filter.hasFilter() === false) {
        return true;
      }
      return request.url.indexOf(pattern, request.url.indexOf(filterHostname) + filterHostname.length) !== -1;
    } else if (filter.isRegex()) {
      return filter.getRegex().test(request.url);
    } else if (filter.isLeftAnchor() && filter.isRightAnchor()) {
      return request.url === pattern;
    } else if (filter.isLeftAnchor()) {
      return fastStartsWith(request.url, pattern);
    } else if (filter.isRightAnchor()) {
      return request.url.endsWith(pattern);
    }
    if (filter.hasFilter() === false) {
      return true;
    }
    return request.url.indexOf(pattern) !== -1;
  }
  function checkOptions(filter, request) {
    if (filter.isCptAllowed(request.type) === false || request.isHttps === true && filter.fromHttps() === false || request.isHttp === true && filter.fromHttp() === false || filter.firstParty() === false && request.isFirstParty === true || filter.thirdParty() === false && request.isThirdParty === true) {
      return false;
    }
    if (filter.domains !== void 0 && filter.domains.match(request.sourceHostnameHashes, request.sourceEntityHashes) === false) {
      return false;
    }
    if (filter.denyallow !== void 0 && filter.denyallow.match(request.getHostnameHashes(), request.getEntityHashes()) === true) {
      return false;
    }
    return true;
  }

  // node_modules/@cliqz/adblocker/dist/es6/src/lists.js
  function detectFilterType(line) {
    if (line.length === 0 || line.length === 1) {
      return 0;
    }
    const firstCharCode = line.charCodeAt(0);
    const secondCharCode = line.charCodeAt(1);
    if (firstCharCode === 33 || firstCharCode === 35 && secondCharCode <= 32 || firstCharCode === 91 && fastStartsWith(line, "[Adblock")) {
      return 0;
    }
    const lastCharCode = line.charCodeAt(line.length - 1);
    if (firstCharCode === 36 || firstCharCode === 38 || firstCharCode === 42 || firstCharCode === 45 || firstCharCode === 46 || firstCharCode === 47 || firstCharCode === 58 || firstCharCode === 61 || firstCharCode === 63 || firstCharCode === 64 || firstCharCode === 95 || firstCharCode === 124 || lastCharCode === 124) {
      return 1;
    }
    const dollarIndex = line.indexOf("$");
    if (dollarIndex !== -1 && dollarIndex !== line.length - 1) {
      const afterDollarIndex = dollarIndex + 1;
      const afterDollarCharCode = line.charCodeAt(afterDollarIndex);
      if (afterDollarCharCode === 36 || afterDollarCharCode === 64 && fastStartsWithFrom(
        line,
        /* $@$ */
        "@$",
        afterDollarIndex
      )) {
        return 0;
      }
    }
    const sharpIndex = line.indexOf("#");
    if (sharpIndex !== -1 && sharpIndex !== line.length - 1) {
      const afterSharpIndex = sharpIndex + 1;
      const afterSharpCharCode = line.charCodeAt(afterSharpIndex);
      if (afterSharpCharCode === 35 || afterSharpCharCode === 64 && fastStartsWithFrom(
        line,
        /* #@# */
        "@#",
        afterSharpIndex
      )) {
        return 2;
      } else if (afterSharpCharCode === 64 && (fastStartsWithFrom(
        line,
        /* #@$# */
        "@$#",
        afterSharpIndex
      ) || fastStartsWithFrom(
        line,
        /* #@%# */
        "@%#",
        afterSharpIndex
      )) || afterSharpCharCode === 37 && fastStartsWithFrom(
        line,
        /* #%# */
        "%#",
        afterSharpIndex
      ) || afterSharpCharCode === 36 && fastStartsWithFrom(
        line,
        /* #$# */
        "$#",
        afterSharpIndex
      ) || afterSharpCharCode === 63 && fastStartsWithFrom(
        line,
        /* #?# */
        "?#",
        afterSharpIndex
      )) {
        return 0;
      }
    }
    return 1;
  }
  function parseFilters(list, config = new Config()) {
    config = new Config(config);
    const networkFilters = [];
    const cosmeticFilters = [];
    const lines = list.split("\n");
    for (let i = 0; i < lines.length; i += 1) {
      let line = lines[i];
      if (line.length !== 0 && line.charCodeAt(0) <= 32) {
        line = line.trim();
      }
      if (line.length > 2) {
        while (i < lines.length - 1 && line.charCodeAt(line.length - 1) === 92 && line.charCodeAt(line.length - 2) === 32) {
          line = line.slice(0, -2);
          const nextLine = lines[i + 1];
          if (nextLine.length > 4 && nextLine.charCodeAt(0) === 32 && nextLine.charCodeAt(1) === 32 && nextLine.charCodeAt(2) === 32 && nextLine.charCodeAt(3) === 32 && nextLine.charCodeAt(4) !== 32) {
            line += nextLine.slice(4);
            i += 1;
          } else {
            break;
          }
        }
      }
      if (line.length !== 0 && line.charCodeAt(line.length - 1) <= 32) {
        line = line.trim();
      }
      const filterType = detectFilterType(line);
      if (filterType === 1 && config.loadNetworkFilters === true) {
        const filter = NetworkFilter.parse(line, config.debug);
        if (filter !== null) {
          networkFilters.push(filter);
        }
      } else if (filterType === 2 && config.loadCosmeticFilters === true) {
        const filter = CosmeticFilter.parse(line, config.debug);
        if (filter !== null) {
          if (config.loadGenericCosmeticsFilters === true || filter.isGenericHide() === false) {
            cosmeticFilters.push(filter);
          }
        }
      }
    }
    return { networkFilters, cosmeticFilters };
  }

  // node_modules/@remusao/small/dist/es6/src/flv.js
  var CONTENT_TYPE = "video/flv";
  var flv_default = {
    contentType: `${CONTENT_TYPE};base64`,
    aliases: [
      CONTENT_TYPE,
      ".flv",
      "flv"
    ],
    body: "RkxWAQEAAAAJAAAAABIAALgAAAAAAAAAAgAKb25NZXRhRGF0YQgAAAAIAAhkdXJhdGlvbgAAAAAAAAAAAAAFd2lkdGgAP/AAAAAAAAAABmhlaWdodAA/8AAAAAAAAAANdmlkZW9kYXRhcmF0ZQBAaGoAAAAAAAAJZnJhbWVyYXRlAEBZAAAAAAAAAAx2aWRlb2NvZGVjaWQAQAAAAAAAAAAAB2VuY29kZXICAA1MYXZmNTcuNDEuMTAwAAhmaWxlc2l6ZQBAaoAAAAAAAAAACQAAAMM="
  };

  // node_modules/@remusao/small/dist/es6/src/gif.js
  var CONTENT_TYPE2 = "image/gif";
  var gif_default = {
    contentType: `${CONTENT_TYPE2};base64`,
    aliases: [
      CONTENT_TYPE2,
      ".gif",
      "gif"
    ],
    body: "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
  };

  // node_modules/@remusao/small/dist/es6/src/html.js
  var CONTENT_TYPE3 = "text/html";
  var html_default = {
    contentType: CONTENT_TYPE3,
    aliases: [
      CONTENT_TYPE3,
      ".html",
      "html",
      ".htm",
      "htm",
      "noopframe",
      "noop.html"
    ],
    body: "<!DOCTYPE html>"
  };

  // node_modules/@remusao/small/dist/es6/src/ico.js
  var CONTENT_TYPE4 = "image/vnd.microsoft.icon";
  var ico_default = {
    contentType: `${CONTENT_TYPE4};base64`,
    aliases: [
      CONTENT_TYPE4,
      ".ico",
      "ico"
    ],
    body: "AAABAAEAAQEAAAEAGAAwAAAAFgAAACgAAAABAAAAAgAAAAEAGAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP8AAAAAAA=="
  };

  // node_modules/@remusao/small/dist/es6/src/jpeg.js
  var CONTENT_TYPE5 = "image/jpeg";
  var jpeg_default = {
    contentType: `${CONTENT_TYPE5};base64`,
    aliases: [
      CONTENT_TYPE5,
      ".jpg",
      "jpg",
      ".jpeg",
      "jpeg"
    ],
    body: "/9j/2wBDAAMCAgICAgMCAgIDAwMDBAYEBAQEBAgGBgUGCQgKCgkICQkKDA8MCgsOCwkJDRENDg8QEBEQCgwSExIQEw8QEBD/yQALCAABAAEBAREA/8wABgAQEAX/2gAIAQEAAD8A0s8g/9k="
  };

  // node_modules/@remusao/small/dist/es6/src/javascript.js
  var CONTENT_TYPE6 = "application/javascript";
  var javascript_default = {
    contentType: CONTENT_TYPE6,
    aliases: [
      CONTENT_TYPE6,
      ".js",
      "js",
      "javascript",
      ".jsx",
      "jsx",
      "typescript",
      ".ts",
      "ts",
      "noop.js",
      "noopjs"
    ],
    body: ""
  };

  // node_modules/@remusao/small/dist/es6/src/json.js
  var CONTENT_TYPE7 = "application/json";
  var json_default = {
    contentType: CONTENT_TYPE7,
    aliases: [
      CONTENT_TYPE7,
      ".json",
      "json"
    ],
    body: "0"
  };

  // node_modules/@remusao/small/dist/es6/src/mp3.js
  var CONTENT_TYPE8 = "audio/mpeg";
  var mp3_default = {
    contentType: `${CONTENT_TYPE8};base64`,
    aliases: [
      CONTENT_TYPE8,
      ".mp3",
      "mp3",
      "noop-0.1s.mp3",
      "noopmp3-0.1s"
    ],
    body: "/+MYxAAAAANIAAAAAExBTUUzLjk4LjIAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
  };

  // node_modules/@remusao/small/dist/es6/src/mp4.js
  var CONTENT_TYPE9 = "video/mp4";
  var mp4_default = {
    contentType: `${CONTENT_TYPE9};base64`,
    aliases: [
      CONTENT_TYPE9,
      ".mp4",
      "mp4",
      ".m4a",
      "m4a",
      ".m4p",
      "m4p",
      ".m4b",
      "m4b",
      ".m4r",
      "m4r",
      ".m4v",
      "m4v",
      "noop-1s.mp4",
      "noopmp4-1s"
    ],
    body: "AAAAHGZ0eXBpc29tAAACAGlzb21pc28ybXA0MQAAAAhmcmVlAAAC721kYXQhEAUgpBv/wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA3pwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAcCEQBSCkG//AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADengAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAcAAAAsJtb292AAAAbG12aGQAAAAAAAAAAAAAAAAAAAPoAAAALwABAAABAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADAAAB7HRyYWsAAABcdGtoZAAAAAMAAAAAAAAAAAAAAAIAAAAAAAAALwAAAAAAAAAAAAAAAQEAAAAAAQAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAACRlZHRzAAAAHGVsc3QAAAAAAAAAAQAAAC8AAAAAAAEAAAAAAWRtZGlhAAAAIG1kaGQAAAAAAAAAAAAAAAAAAKxEAAAIAFXEAAAAAAAtaGRscgAAAAAAAAAAc291bgAAAAAAAAAAAAAAAFNvdW5kSGFuZGxlcgAAAAEPbWluZgAAABBzbWhkAAAAAAAAAAAAAAAkZGluZgAAABxkcmVmAAAAAAAAAAEAAAAMdXJsIAAAAAEAAADTc3RibAAAAGdzdHNkAAAAAAAAAAEAAABXbXA0YQAAAAAAAAABAAAAAAAAAAAAAgAQAAAAAKxEAAAAAAAzZXNkcwAAAAADgICAIgACAASAgIAUQBUAAAAAAfQAAAHz+QWAgIACEhAGgICAAQIAAAAYc3R0cwAAAAAAAAABAAAAAgAABAAAAAAcc3RzYwAAAAAAAAABAAAAAQAAAAIAAAABAAAAHHN0c3oAAAAAAAAAAAAAAAIAAAFzAAABdAAAABRzdGNvAAAAAAAAAAEAAAAsAAAAYnVkdGEAAABabWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAbWRpcmFwcGwAAAAAAAAAAAAAAAAtaWxzdAAAACWpdG9vAAAAHWRhdGEAAAABAAAAAExhdmY1Ni40MC4xMDE="
  };

  // node_modules/@remusao/small/dist/es6/src/pdf.js
  var CONTENT_TYPE10 = "application/pdf";
  var pdf_default = {
    contentType: `${CONTENT_TYPE10};base64`,
    aliases: [
      CONTENT_TYPE10,
      ".pdf",
      "pdf"
    ],
    body: "JVBERi0xLgoxIDAgb2JqPDwvUGFnZXMgMiAwIFI+PmVuZG9iagoyIDAgb2JqPDwvS2lkc1szIDAgUl0vQ291bnQgMT4+ZW5kb2JqCjMgMCBvYmo8PC9QYXJlbnQgMiAwIFI+PmVuZG9iagp0cmFpbGVyIDw8L1Jvb3QgMSAwIFI+Pg=="
  };

  // node_modules/@remusao/small/dist/es6/src/png.js
  var CONTENT_TYPE11 = "image/png";
  var png_default = {
    contentType: `${CONTENT_TYPE11};base64`,
    aliases: [
      CONTENT_TYPE11,
      ".png",
      "png"
    ],
    body: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACklEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg=="
  };

  // node_modules/@remusao/small/dist/es6/src/svg.js
  var CONTENT_TYPE12 = "image/svg+xml";
  var svg_default = {
    contentType: CONTENT_TYPE12,
    aliases: [
      CONTENT_TYPE12,
      ".svg",
      "svg"
    ],
    body: "https://raw.githubusercontent.com/mathiasbynens/small/master/svg.svg"
  };

  // node_modules/@remusao/small/dist/es6/src/txt.js
  var CONTENT_TYPE13 = "text/plain";
  var txt_default = {
    contentType: CONTENT_TYPE13,
    aliases: [
      CONTENT_TYPE13,
      ".txt",
      "txt",
      "text",
      "nooptext",
      "noop.txt"
    ],
    body: ""
  };

  // node_modules/@remusao/small/dist/es6/src/wav.js
  var CONTENT_TYPE14 = "audio/wav";
  var wav_default = {
    contentType: `${CONTENT_TYPE14};base64`,
    aliases: [
      CONTENT_TYPE14,
      ".wav",
      "wav"
    ],
    body: "UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA="
  };

  // node_modules/@remusao/small/dist/es6/src/webm.js
  var CONTENT_TYPE15 = "video/webm";
  var webm_default = {
    contentType: `${CONTENT_TYPE15};base64`,
    aliases: [
      CONTENT_TYPE15,
      ".webm",
      "webm"
    ],
    body: "GkXfo0AgQoaBAUL3gQFC8oEEQvOBCEKCQAR3ZWJtQoeBAkKFgQIYU4BnQI0VSalmQCgq17FAAw9CQE2AQAZ3aGFtbXlXQUAGd2hhbW15RIlACECPQAAAAAAAFlSua0AxrkAu14EBY8WBAZyBACK1nEADdW5khkAFVl9WUDglhohAA1ZQOIOBAeBABrCBCLqBCB9DtnVAIueBAKNAHIEAAIAwAQCdASoIAAgAAUAmJaQAA3AA/vz0AAA="
  };

  // node_modules/@remusao/small/dist/es6/src/webp.js
  var CONTENT_TYPE16 = "image/webp";
  var webp_default = {
    contentType: `${CONTENT_TYPE16};base64`,
    aliases: [
      CONTENT_TYPE16,
      ".webp",
      "webp"
    ],
    body: "UklGRhIAAABXRUJQVlA4TAYAAAAvQWxvAGs="
  };

  // node_modules/@remusao/small/dist/es6/src/wmv.js
  var CONTENT_TYPE17 = "video/wmv";
  var wmv_default = {
    contentType: `${CONTENT_TYPE17};base64`,
    aliases: [
      CONTENT_TYPE17,
      ".wmv",
      "wmv"
    ],
    body: "MCaydY5mzxGm2QCqAGLObOUBAAAAAAAABQAAAAECodyrjEepzxGO5ADADCBTZWgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABcCAAAAAAAAAIA+1d6xnQEAAAAAAAAAAMAF2QEAAAAAAAAAAAAAAAAcDAAAAAAAAAIAAACADAAAgAwAAEANAwC1A79fLqnPEY7jAMAMIFNlLgAAAAAAAAAR0tOruqnPEY7mAMAMIFNlBgAAAAAAQKTQ0gfj0hGX8ACgyV6oUGQAAAAAAAAAAQAoAFcATQAvAEUAbgBjAG8AZABpAG4AZwBTAGUAdAB0AGkAbgBnAHMAAAAAABwATABhAHYAZgA1ADcALgA0ADEALgAxADAAMAAAAJEH3Le3qc8RjuYAwAwgU2WBAAAAAAAAAMDvGbxNW88RqP0AgF9cRCsAV/sgVVvPEaj9AIBfXEQrAAAAAAAAAAAzAAAAAAAAAAEAAAAAAAEAAAABAAAAAigAKAAAAAEAAAABAAAAAQAYAE1QNDMDAAAAAAAAAAAAAAAAAAAAAAAAAEBS0YYdMdARo6QAoMkDSPZMAAAAAAAAAEFS0YYdMdARo6QAoMkDSPYBAAAAAQAKAG0AcwBtAHAAZQBnADQAdgAzAAAAAAAEAE1QNDM2JrJ1jmbPEabZAKoAYs5sMgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAQ=="
  };

  // node_modules/@remusao/small/dist/es6/index.js
  var MIME_TO_RESOURCE = (() => {
    const resources = {};
    for (const fake of [
      flv_default,
      gif_default,
      html_default,
      ico_default,
      jpeg_default,
      javascript_default,
      json_default,
      mp3_default,
      mp4_default,
      pdf_default,
      png_default,
      svg_default,
      txt_default,
      wav_default,
      webm_default,
      webp_default,
      wmv_default
    ]) {
      for (const alias of fake.aliases) {
        resources[alias] = fake;
      }
    }
    return resources;
  })();
  function getFallbackTextResource() {
    return txt_default;
  }
  function getResourceForMime(mime) {
    return MIME_TO_RESOURCE[mime] || getFallbackTextResource();
  }

  // node_modules/@cliqz/adblocker/dist/es6/src/resources.js
  function btoaPolyfill(buffer) {
    if (typeof btoa !== "undefined") {
      return btoa(buffer);
    } else if (typeof Buffer !== "undefined") {
      return Buffer.from(buffer).toString("base64");
    }
    return buffer;
  }
  var Resources = class _Resources {
    static deserialize(buffer) {
      const checksum = buffer.getASCII();
      const resources = /* @__PURE__ */ new Map();
      const numberOfResources = buffer.getUint16();
      for (let i = 0; i < numberOfResources; i += 1) {
        resources.set(buffer.getASCII(), {
          contentType: buffer.getASCII(),
          body: buffer.getASCII()
        });
      }
      const js = /* @__PURE__ */ new Map();
      resources.forEach(({ contentType, body }, name) => {
        if (contentType === "application/javascript") {
          js.set(name, body);
        }
      });
      return new _Resources({
        checksum,
        js,
        resources
      });
    }
    static parse(data, { checksum }) {
      const typeToResource = /* @__PURE__ */ new Map();
      const trimComments = (str) => str.replace(/^\s*#.*$/gm, "");
      const chunks = data.split("\n\n");
      for (const chunk of chunks) {
        const resource = trimComments(chunk).trim();
        if (resource.length !== 0) {
          const firstNewLine = resource.indexOf("\n");
          const split = resource.slice(0, firstNewLine).split(/\s+/);
          const name = split[0];
          const type = split[1];
          const body = resource.slice(firstNewLine + 1);
          if (name === void 0 || type === void 0 || body === void 0) {
            continue;
          }
          let resources = typeToResource.get(type);
          if (resources === void 0) {
            resources = /* @__PURE__ */ new Map();
            typeToResource.set(type, resources);
          }
          resources.set(name, body);
        }
      }
      const js = typeToResource.get("application/javascript") || /* @__PURE__ */ new Map();
      for (const [key, value] of js.entries()) {
        if (key.endsWith(".js")) {
          js.set(key.slice(0, -3), value);
        }
      }
      const resourcesByName = /* @__PURE__ */ new Map();
      typeToResource.forEach((resources, contentType) => {
        resources.forEach((resource, name) => {
          resourcesByName.set(name, {
            contentType,
            body: resource
          });
        });
      });
      return new _Resources({
        checksum,
        js,
        resources: resourcesByName
      });
    }
    constructor({ checksum = "", js = /* @__PURE__ */ new Map(), resources = /* @__PURE__ */ new Map() } = {}) {
      this.checksum = checksum;
      this.js = js;
      this.resources = resources;
    }
    getResource(name) {
      const { body, contentType } = this.resources.get(name) || getResourceForMime(name);
      let dataUrl;
      if (contentType.indexOf(";") !== -1) {
        dataUrl = `data:${contentType},${body}`;
      } else {
        dataUrl = `data:${contentType};base64,${btoaPolyfill(body)}`;
      }
      return { body, contentType, dataUrl };
    }
    getSerializedSize() {
      let estimatedSize = sizeOfASCII(this.checksum) + 2 * sizeOfByte();
      this.resources.forEach(({ contentType, body }, name) => {
        estimatedSize += sizeOfASCII(name) + sizeOfASCII(contentType) + sizeOfASCII(body);
      });
      return estimatedSize;
    }
    serialize(buffer) {
      buffer.pushASCII(this.checksum);
      buffer.pushUint16(this.resources.size);
      this.resources.forEach(({ contentType, body }, name) => {
        buffer.pushASCII(name);
        buffer.pushASCII(contentType);
        buffer.pushASCII(body);
      });
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/compact-set.js
  function compactTokens(tokens) {
    const sorted = tokens.sort();
    let lastIndex = 1;
    for (let i = 1; i < sorted.length; i += 1) {
      if (sorted[lastIndex - 1] !== sorted[i]) {
        sorted[lastIndex++] = sorted[i];
      }
    }
    return sorted.subarray(0, lastIndex);
  }
  var EMPTY_UINT32_ARRAY2 = new Uint32Array(0);
  function concatTypedArrays(arrays) {
    if (arrays.length === 0) {
      return EMPTY_UINT32_ARRAY2;
    }
    if (arrays.length === 1) {
      return arrays[0];
    }
    let totalSize = 0;
    for (let i = 0; i < arrays.length; i += 1) {
      totalSize += arrays[i].length;
    }
    const result = new Uint32Array(totalSize);
    let index = 0;
    for (let i = 0; i < arrays.length; i += 1) {
      const array = arrays[i];
      for (let j = 0; j < array.length; j += 1) {
        result[index++] = array[j];
      }
    }
    return result;
  }

  // node_modules/@cliqz/adblocker/dist/es6/src/engine/optimizer.js
  function processRegex(r) {
    return `(?:${r.source})`;
  }
  function escape(s) {
    return `(?:${s.replace(/[-/\\^$*+?.()|[\]{}]/g, "\\$&")})`;
  }
  function setWithDefault(map, key, value) {
    let bucket = map.get(key);
    if (bucket === void 0) {
      bucket = [];
      map.set(key, bucket);
    }
    bucket.push(value);
  }
  function groupBy(filters, criteria) {
    const grouped = /* @__PURE__ */ new Map();
    for (const filter of filters) {
      setWithDefault(grouped, criteria(filter), filter);
    }
    return Array.from(grouped.values());
  }
  function splitBy(filters, condition) {
    const positive = [];
    const negative = [];
    for (const filter of filters) {
      if (condition(filter)) {
        positive.push(filter);
      } else {
        negative.push(filter);
      }
    }
    return {
      negative,
      positive
    };
  }
  var OPTIMIZATIONS = [
    {
      description: "Remove duplicated filters by ID",
      fusion: (filters) => filters[0],
      groupByCriteria: (filter) => "" + filter.getId(),
      select: () => true
    },
    {
      description: "Group idential filter with same mask but different domains in single filters",
      fusion: (filters) => {
        const parts = [];
        const hostnames = /* @__PURE__ */ new Set();
        const notHostnames = /* @__PURE__ */ new Set();
        const entities = /* @__PURE__ */ new Set();
        const notEntities = /* @__PURE__ */ new Set();
        for (const { domains } of filters) {
          if (domains !== void 0) {
            if (domains.parts !== void 0) {
              parts.push(domains.parts);
            }
            if (domains.hostnames !== void 0) {
              for (const hash of domains.hostnames) {
                hostnames.add(hash);
              }
            }
            if (domains.entities !== void 0) {
              for (const hash of domains.entities) {
                entities.add(hash);
              }
            }
            if (domains.notHostnames !== void 0) {
              for (const hash of domains.notHostnames) {
                notHostnames.add(hash);
              }
            }
            if (domains.notEntities !== void 0) {
              for (const hash of domains.notEntities) {
                notEntities.add(hash);
              }
            }
          }
        }
        return new NetworkFilter(Object.assign({}, filters[0], {
          domains: new Domains({
            hostnames: hostnames.size !== 0 ? new Uint32Array(hostnames).sort() : void 0,
            entities: entities.size !== 0 ? new Uint32Array(entities).sort() : void 0,
            notHostnames: notHostnames.size !== 0 ? new Uint32Array(notHostnames).sort() : void 0,
            notEntities: notEntities.size !== 0 ? new Uint32Array(notEntities).sort() : void 0,
            parts: parts.length !== 0 ? parts.join(",") : void 0
          }),
          rawLine: filters[0].rawLine !== void 0 ? filters.map(({ rawLine }) => rawLine).join(" <+> ") : void 0
        }));
      },
      groupByCriteria: (filter) => filter.getHostname() + filter.getFilter() + filter.getMask() + filter.getRedirect(),
      select: (filter) => !filter.isCSP() && filter.denyallow === void 0 && filter.domains !== void 0
    },
    {
      description: "Group simple patterns, into a single filter",
      fusion: (filters) => {
        const patterns = [];
        for (const f2 of filters) {
          if (f2.isRegex()) {
            patterns.push(processRegex(f2.getRegex()));
          } else if (f2.isRightAnchor()) {
            patterns.push(`${escape(f2.getFilter())}$`);
          } else if (f2.isLeftAnchor()) {
            patterns.push(`^${escape(f2.getFilter())}`);
          } else {
            patterns.push(escape(f2.getFilter()));
          }
        }
        return new NetworkFilter(Object.assign({}, filters[0], {
          mask: setBit(
            filters[0].mask,
            8388608
            /* NETWORK_FILTER_MASK.isRegex */
          ),
          rawLine: filters[0].rawLine !== void 0 ? filters.map(({ rawLine }) => rawLine).join(" <+> ") : void 0,
          regex: new RegExp(patterns.join("|"))
        }));
      },
      groupByCriteria: (filter) => "" + (filter.getMask() & ~8388608 & ~4194304),
      select: (filter) => filter.domains === void 0 && filter.denyallow === void 0 && !filter.isHostnameAnchor() && !filter.isRedirect() && !filter.isCSP()
    }
  ];
  function noopOptimizeNetwork(filters) {
    return filters;
  }
  function noopOptimizeCosmetic(filters) {
    return filters;
  }
  function optimizeNetwork(filters) {
    const fused = [];
    let toFuse = filters;
    for (const { select, fusion, groupByCriteria } of OPTIMIZATIONS) {
      const { positive, negative } = splitBy(toFuse, select);
      toFuse = negative;
      const groups = groupBy(positive, groupByCriteria);
      for (const group of groups) {
        if (group.length > 1) {
          fused.push(fusion(group));
        } else {
          toFuse.push(group[0]);
        }
      }
    }
    for (const filter of toFuse) {
      fused.push(filter);
    }
    return fused;
  }

  // node_modules/@cliqz/adblocker/dist/es6/src/engine/reverse-index.js
  function nextPow2(v) {
    v--;
    v |= v >> 1;
    v |= v >> 2;
    v |= v >> 4;
    v |= v >> 8;
    v |= v >> 16;
    v++;
    return v;
  }
  var UID = 1;
  function getNextId() {
    const id = UID;
    UID = (UID + 1) % 1e9;
    return id;
  }
  var EMPTY_BUCKET = Number.MAX_SAFE_INTEGER >>> 0;
  var ReverseIndex = class _ReverseIndex {
    static deserialize(buffer, deserialize4, optimize, config) {
      const tokensLookupIndexSize = buffer.getUint32();
      const bucketsIndexSize = buffer.getUint32();
      const numberOfFilters = buffer.getUint32();
      const view = StaticDataView.fromUint8Array(buffer.getBytes(
        true
        /* align */
      ), config);
      const tokensLookupIndex = view.getUint32ArrayView(tokensLookupIndexSize);
      const bucketsIndex = view.getUint32ArrayView(bucketsIndexSize);
      const filtersIndexStart = view.pos;
      view.seekZero();
      return new _ReverseIndex({
        config,
        deserialize: deserialize4,
        filters: [],
        optimize
      }).updateInternals({
        bucketsIndex,
        filtersIndexStart,
        numberOfFilters,
        tokensLookupIndex,
        view
      });
    }
    constructor({ deserialize: deserialize4, filters, optimize, config }) {
      this.bucketsIndex = EMPTY_UINT32_ARRAY;
      this.filtersIndexStart = 0;
      this.numberOfFilters = 0;
      this.tokensLookupIndex = EMPTY_UINT32_ARRAY;
      this.cache = /* @__PURE__ */ new Map();
      this.view = StaticDataView.empty(config);
      this.deserializeFilter = deserialize4;
      this.optimize = optimize;
      this.config = config;
      if (filters.length !== 0) {
        this.update(filters, void 0);
      }
    }
    /**
     * Load all filters from this index in memory (i.e.: deserialize them from
     * the byte array into NetworkFilter or CosmeticFilter instances). This is
     * mostly useful for debugging or testing purposes.
     */
    getFilters() {
      const filters = [];
      if (this.numberOfFilters === 0) {
        return filters;
      }
      this.view.setPos(this.filtersIndexStart);
      for (let i = 0; i < this.numberOfFilters; i += 1) {
        filters.push(this.deserializeFilter(this.view));
      }
      this.view.seekZero();
      return filters;
    }
    /**
     * Return an array of all the tokens currently used as keys of the "buckets index".
     */
    getTokens() {
      const tokens = /* @__PURE__ */ new Set();
      for (let i = 0; i < this.bucketsIndex.length; i += 2) {
        tokens.add(this.bucketsIndex[i]);
      }
      return new Uint32Array(tokens);
    }
    /**
     * Estimate the number of bytes needed to serialize this instance of `ReverseIndex`.
     */
    getSerializedSize() {
      return 12 + sizeOfBytes(
        this.view.buffer,
        true
        /* align */
      );
    }
    /**
     * Dump this index to `buffer`.
     */
    serialize(buffer) {
      buffer.pushUint32(this.tokensLookupIndex.length);
      buffer.pushUint32(this.bucketsIndex.length);
      buffer.pushUint32(this.numberOfFilters);
      buffer.pushBytes(
        this.view.buffer,
        true
        /* align */
      );
    }
    /**
     * Iterate on all filters found in buckets associated with the given list of
     * tokens. The callback is called on each of them. Early termination can be
     * achieved if the callback returns `false`.
     *
     * This will not check if each filter returned would match a given request but
     * is instead used as a list of potential candidates (much smaller than the
     * total set of filters; typically between 5 and 10 filters will be checked).
     */
    iterMatchingFilters(tokens, cb) {
      const requestId = getNextId();
      for (const token of tokens) {
        if (this.iterBucket(token, requestId, cb) === false) {
          return;
        }
      }
      this.iterBucket(0, requestId, cb);
    }
    /**
     * Re-create the internal data-structure of the reverse index *in-place*. It
     * needs to be called with a list of new filters and optionally a list of ids
     * (as returned by either NetworkFilter.getId() or CosmeticFilter.getId())
     * which need to be removed from the index.
     */
    update(newFilters, removedFilters) {
      if (this.cache.size !== 0) {
        this.cache.clear();
      }
      const compression = this.config.enableCompression;
      let totalNumberOfTokens = 0;
      let totalNumberOfIndexedFilters = 0;
      const filtersTokens = [];
      let bucketsIndexSize = 0;
      let estimatedBufferSize = this.view.buffer.byteLength - this.filtersIndexStart;
      let filters = this.getFilters();
      if (filters.length !== 0) {
        if (removedFilters !== void 0 && removedFilters.size !== 0) {
          filters = filters.filter((f2) => {
            if (removedFilters.has(f2.getId())) {
              estimatedBufferSize -= f2.getSerializedSize(compression);
              return false;
            }
            return true;
          });
        }
        for (const filter of newFilters) {
          estimatedBufferSize += filter.getSerializedSize(compression);
          filters.push(filter);
        }
      } else {
        filters = newFilters;
        for (const filter of newFilters) {
          estimatedBufferSize += filter.getSerializedSize(compression);
        }
      }
      if (filters.length === 0) {
        this.updateInternals({
          bucketsIndex: EMPTY_UINT32_ARRAY,
          filtersIndexStart: 0,
          numberOfFilters: 0,
          tokensLookupIndex: EMPTY_UINT32_ARRAY,
          view: StaticDataView.empty(this.config)
        });
        return;
      }
      if (this.config.debug === true) {
        filters.sort((f1, f2) => f1.getId() - f2.getId());
      }
      const histogram = new Uint32Array(Math.max(nextPow2(2 * filters.length), 256));
      for (const filter of filters) {
        const multiTokens = filter.getTokens();
        filtersTokens.push(multiTokens);
        bucketsIndexSize += 2 * multiTokens.length;
        totalNumberOfIndexedFilters += multiTokens.length;
        for (const tokens of multiTokens) {
          totalNumberOfTokens += tokens.length;
          for (const token of tokens) {
            histogram[token % histogram.length] += 1;
          }
        }
      }
      estimatedBufferSize += bucketsIndexSize * 4;
      const tokensLookupIndexSize = Math.max(2, nextPow2(totalNumberOfIndexedFilters));
      const mask = tokensLookupIndexSize - 1;
      const suffixes = [];
      for (let i = 0; i < tokensLookupIndexSize; i += 1) {
        suffixes.push([]);
      }
      estimatedBufferSize += tokensLookupIndexSize * 4;
      const buffer = StaticDataView.allocate(estimatedBufferSize, this.config);
      const tokensLookupIndex = buffer.getUint32ArrayView(tokensLookupIndexSize);
      const bucketsIndex = buffer.getUint32ArrayView(bucketsIndexSize);
      const filtersIndexStart = buffer.getPos();
      for (let i = 0; i < filtersTokens.length; i += 1) {
        const filter = filters[i];
        const multiTokens = filtersTokens[i];
        const filterIndex = buffer.pos;
        filter.serialize(buffer);
        for (const tokens of multiTokens) {
          let bestToken = 0;
          let minCount = totalNumberOfTokens + 1;
          for (const token of tokens) {
            const tokenCount = histogram[token % histogram.length];
            if (tokenCount < minCount) {
              minCount = tokenCount;
              bestToken = token;
              if (minCount === 1) {
                break;
              }
            }
          }
          suffixes[bestToken & mask].push([bestToken, filterIndex]);
        }
      }
      let indexInBucketsIndex = 0;
      for (let i = 0; i < tokensLookupIndexSize; i += 1) {
        const filtersForMask = suffixes[i];
        tokensLookupIndex[i] = indexInBucketsIndex;
        for (const [token, filterIndex] of filtersForMask) {
          bucketsIndex[indexInBucketsIndex++] = token;
          bucketsIndex[indexInBucketsIndex++] = filterIndex;
        }
      }
      buffer.seekZero();
      this.updateInternals({
        bucketsIndex,
        filtersIndexStart,
        numberOfFilters: filtersTokens.length,
        tokensLookupIndex,
        view: buffer
      });
    }
    updateInternals({ bucketsIndex, filtersIndexStart, numberOfFilters, tokensLookupIndex, view }) {
      this.bucketsIndex = bucketsIndex;
      this.filtersIndexStart = filtersIndexStart;
      this.numberOfFilters = numberOfFilters;
      this.tokensLookupIndex = tokensLookupIndex;
      this.view = view;
      view.seekZero();
      return this;
    }
    /**
     * If a bucket exists for the given token, call the callback on each filter
     * found inside. An early termination mechanism is built-in, to stop iterating
     * as soon as `false` is returned from the callback.
     */
    iterBucket(token, requestId, cb) {
      let bucket = this.config.enableInMemoryCache === true ? this.cache.get(token) : void 0;
      if (bucket === void 0) {
        const offset = token & this.tokensLookupIndex.length - 1;
        const startOfBucket = this.tokensLookupIndex[offset];
        if (startOfBucket === EMPTY_BUCKET) {
          return true;
        }
        const endOfBucket = offset === this.tokensLookupIndex.length - 1 ? this.bucketsIndex.length : this.tokensLookupIndex[offset + 1];
        const filtersIndices = [];
        for (let i = startOfBucket; i < endOfBucket; i += 2) {
          const currentToken = this.bucketsIndex[i];
          if (currentToken === token) {
            filtersIndices.push(this.bucketsIndex[i + 1]);
          }
        }
        if (filtersIndices.length === 0) {
          return true;
        }
        const filters = [];
        const view = this.view;
        for (let i = 0; i < filtersIndices.length; i += 1) {
          view.setPos(filtersIndices[i]);
          filters.push(this.deserializeFilter(view));
        }
        bucket = {
          filters: filters.length > 1 ? this.optimize(filters) : filters,
          lastRequestSeen: -1
          // safe because all ids are positive
        };
        if (this.config.enableInMemoryCache === true) {
          this.cache.set(token, bucket);
        }
      }
      if (bucket.lastRequestSeen !== requestId) {
        bucket.lastRequestSeen = requestId;
        const filters = bucket.filters;
        for (let i = 0; i < filters.length; i += 1) {
          if (cb(filters[i]) === false) {
            if (i > 0) {
              const filter = filters[i];
              filters[i] = filters[i - 1];
              filters[i - 1] = filter;
            }
            return false;
          }
        }
      }
      return true;
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/engine/bucket/filters.js
  var EMPTY_FILTERS = new Uint8Array(4);
  var FiltersContainer = class _FiltersContainer {
    static deserialize(buffer, deserialize4, config) {
      const container = new _FiltersContainer({ deserialize: deserialize4, config, filters: [] });
      container.filters = buffer.getBytes();
      return container;
    }
    constructor({ config, deserialize: deserialize4, filters }) {
      this.deserialize = deserialize4;
      this.filters = EMPTY_FILTERS;
      this.config = config;
      if (filters.length !== 0) {
        this.update(filters, void 0);
      }
    }
    /**
     * Update filters based on `newFilters` and `removedFilters`.
     */
    update(newFilters, removedFilters) {
      let bufferSizeEstimation = this.filters.byteLength;
      let selected = [];
      const compression = this.config.enableCompression;
      const currentFilters = this.getFilters();
      if (currentFilters.length !== 0) {
        if (removedFilters === void 0 || removedFilters.size === 0) {
          selected = currentFilters;
        } else {
          for (const filter of currentFilters) {
            if (removedFilters.has(filter.getId()) === false) {
              selected.push(filter);
            } else {
              bufferSizeEstimation -= filter.getSerializedSize(compression);
            }
          }
        }
      }
      const storedFiltersRemoved = selected.length !== currentFilters.length;
      const numberOfExistingFilters = selected.length;
      for (const filter of newFilters) {
        bufferSizeEstimation += filter.getSerializedSize(compression);
        selected.push(filter);
      }
      const storedFiltersAdded = selected.length > numberOfExistingFilters;
      if (selected.length === 0) {
        this.filters = EMPTY_FILTERS;
      } else if (storedFiltersAdded === true || storedFiltersRemoved === true) {
        const buffer = StaticDataView.allocate(bufferSizeEstimation, this.config);
        buffer.pushUint32(selected.length);
        if (this.config.debug === true) {
          selected.sort((f1, f2) => f1.getId() - f2.getId());
        }
        for (const filter of selected) {
          filter.serialize(buffer);
        }
        this.filters = buffer.buffer;
      }
    }
    getSerializedSize() {
      return sizeOfBytes(
        this.filters,
        false
        /* no alignement */
      );
    }
    serialize(buffer) {
      buffer.pushBytes(this.filters);
    }
    getFilters() {
      if (this.filters.byteLength <= 4) {
        return [];
      }
      const filters = [];
      const buffer = StaticDataView.fromUint8Array(this.filters, this.config);
      const numberOfFilters = buffer.getUint32();
      for (let i = 0; i < numberOfFilters; i += 1) {
        filters.push(this.deserialize(buffer));
      }
      return filters;
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/engine/bucket/cosmetic.js
  function createStylesheet(rules, style) {
    if (rules.length === 0) {
      return "";
    }
    const maximumNumberOfSelectors = 1024;
    const parts = [];
    const styleStr = ` { ${style} }`;
    for (let i = 0; i < rules.length; i += maximumNumberOfSelectors) {
      let selector = rules[i];
      for (let j = i + 1, end = Math.min(rules.length, i + maximumNumberOfSelectors); j < end; j += 1) {
        selector += ",\n" + rules[j];
      }
      selector += styleStr;
      if (rules.length < maximumNumberOfSelectors) {
        return selector;
      }
      parts.push(selector);
    }
    return parts.join("\n");
  }
  function createStylesheetFromRulesWithCustomStyles(rules) {
    const selectorsPerStyle = /* @__PURE__ */ new Map();
    for (const rule of rules) {
      const style = rule.getStyle();
      const selectors = selectorsPerStyle.get(style);
      if (selectors === void 0) {
        selectorsPerStyle.set(style, [rule.getSelector()]);
      } else {
        selectors.push(rule.getSelector());
      }
    }
    const stylesheets = [];
    const selectorsPerStyleArray = Array.from(selectorsPerStyle.entries());
    for (const [style, selectors] of selectorsPerStyleArray) {
      stylesheets.push(createStylesheet(selectors, style));
    }
    return stylesheets.join("\n\n");
  }
  function createStylesheetFromRules(rules) {
    const selectors = [];
    for (const rule of rules) {
      if (rule.hasCustomStyle()) {
        return createStylesheetFromRulesWithCustomStyles(rules);
      }
      selectors.push(rule.selector);
    }
    return createStylesheet(selectors, DEFAULT_HIDDING_STYLE);
  }
  function createLookupTokens(hostname, domain) {
    const hostnamesHashes = getHostnameHashesFromLabelsBackward(hostname, domain);
    const entitiesHashes = getEntityHashesFromLabelsBackward(hostname, domain);
    const tokens = new Uint32Array(hostnamesHashes.length + entitiesHashes.length);
    let index = 0;
    for (const hash of hostnamesHashes) {
      tokens[index++] = hash;
    }
    for (const hash of entitiesHashes) {
      tokens[index++] = hash;
    }
    return tokens;
  }
  var CosmeticFilterBucket = class _CosmeticFilterBucket {
    static deserialize(buffer, config) {
      const bucket = new _CosmeticFilterBucket({ config });
      bucket.genericRules = FiltersContainer.deserialize(buffer, CosmeticFilter.deserialize, config);
      bucket.classesIndex = ReverseIndex.deserialize(buffer, CosmeticFilter.deserialize, noopOptimizeCosmetic, config);
      bucket.hostnameIndex = ReverseIndex.deserialize(buffer, CosmeticFilter.deserialize, noopOptimizeCosmetic, config);
      bucket.hrefsIndex = ReverseIndex.deserialize(buffer, CosmeticFilter.deserialize, noopOptimizeCosmetic, config);
      bucket.htmlIndex = ReverseIndex.deserialize(buffer, CosmeticFilter.deserialize, noopOptimizeCosmetic, config);
      bucket.idsIndex = ReverseIndex.deserialize(buffer, CosmeticFilter.deserialize, noopOptimizeCosmetic, config);
      bucket.unhideIndex = ReverseIndex.deserialize(buffer, CosmeticFilter.deserialize, noopOptimizeCosmetic, config);
      return bucket;
    }
    constructor({ filters = [], config }) {
      this.genericRules = new FiltersContainer({
        config,
        deserialize: CosmeticFilter.deserialize,
        filters: []
      });
      this.classesIndex = new ReverseIndex({
        config,
        deserialize: CosmeticFilter.deserialize,
        filters: [],
        optimize: noopOptimizeCosmetic
      });
      this.hostnameIndex = new ReverseIndex({
        config,
        deserialize: CosmeticFilter.deserialize,
        filters: [],
        optimize: noopOptimizeCosmetic
      });
      this.hrefsIndex = new ReverseIndex({
        config,
        deserialize: CosmeticFilter.deserialize,
        filters: [],
        optimize: noopOptimizeCosmetic
      });
      this.htmlIndex = new ReverseIndex({
        config,
        deserialize: CosmeticFilter.deserialize,
        filters: [],
        optimize: noopOptimizeCosmetic
      });
      this.idsIndex = new ReverseIndex({
        config,
        deserialize: CosmeticFilter.deserialize,
        filters: [],
        optimize: noopOptimizeCosmetic
      });
      this.unhideIndex = new ReverseIndex({
        config,
        deserialize: CosmeticFilter.deserialize,
        filters: [],
        optimize: noopOptimizeCosmetic
      });
      this.baseStylesheet = null;
      this.extraGenericRules = null;
      if (filters.length !== 0) {
        this.update(filters, void 0, config);
      }
    }
    getFilters() {
      const filters = [];
      return filters.concat(this.genericRules.getFilters(), this.classesIndex.getFilters(), this.hostnameIndex.getFilters(), this.hrefsIndex.getFilters(), this.htmlIndex.getFilters(), this.idsIndex.getFilters(), this.unhideIndex.getFilters());
    }
    update(newFilters, removedFilters, config) {
      const classSelectors = [];
      const genericHideRules = [];
      const hostnameSpecificRules = [];
      const hrefSelectors = [];
      const htmlRules = [];
      const idSelectors = [];
      const unHideRules = [];
      for (const rule of newFilters) {
        if (rule.isUnhide()) {
          unHideRules.push(rule);
        } else if (rule.isHtmlFiltering()) {
          htmlRules.push(rule);
        } else if (rule.isGenericHide()) {
          if (rule.isClassSelector()) {
            classSelectors.push(rule);
          } else if (rule.isIdSelector()) {
            idSelectors.push(rule);
          } else if (rule.isHrefSelector()) {
            hrefSelectors.push(rule);
          } else {
            genericHideRules.push(rule);
          }
        } else if (rule.isExtended() === false || config.loadExtendedSelectors === true) {
          hostnameSpecificRules.push(rule);
        }
      }
      this.genericRules.update(genericHideRules, removedFilters);
      this.classesIndex.update(classSelectors, removedFilters);
      this.hostnameIndex.update(hostnameSpecificRules, removedFilters);
      this.hrefsIndex.update(hrefSelectors, removedFilters);
      this.htmlIndex.update(htmlRules, removedFilters);
      this.idsIndex.update(idSelectors, removedFilters);
      this.unhideIndex.update(unHideRules, removedFilters);
    }
    getSerializedSize() {
      return this.genericRules.getSerializedSize() + this.classesIndex.getSerializedSize() + this.hostnameIndex.getSerializedSize() + this.hrefsIndex.getSerializedSize() + this.htmlIndex.getSerializedSize() + this.idsIndex.getSerializedSize() + this.unhideIndex.getSerializedSize();
    }
    serialize(buffer) {
      this.genericRules.serialize(buffer);
      this.classesIndex.serialize(buffer);
      this.hostnameIndex.serialize(buffer);
      this.hrefsIndex.serialize(buffer);
      this.htmlIndex.serialize(buffer);
      this.idsIndex.serialize(buffer);
      this.unhideIndex.serialize(buffer);
    }
    getHtmlRules({ domain, hostname }) {
      const hostnameTokens = createLookupTokens(hostname, domain);
      const rules = [];
      this.htmlIndex.iterMatchingFilters(hostnameTokens, (rule) => {
        if (rule.match(hostname, domain)) {
          rules.push(rule);
        }
        return true;
      });
      const disabledRules = /* @__PURE__ */ new Set();
      if (rules.length !== 0) {
        this.unhideIndex.iterMatchingFilters(hostnameTokens, (rule) => {
          if (rule.match(hostname, domain)) {
            disabledRules.add(rule.getSelector());
          }
          return true;
        });
      }
      return rules.filter((rule) => disabledRules.size === 0 || disabledRules.has(rule.getSelector()) === false);
    }
    /**
     * Request cosmetics and scripts to inject in a page.
     */
    getCosmeticsFilters({
      domain,
      hostname,
      classes = [],
      hrefs = [],
      ids = [],
      allowGenericHides = true,
      allowSpecificHides = true,
      // Allows to specify which rules to return
      getBaseRules = true,
      getInjectionRules = true,
      getExtendedRules = true,
      getRulesFromDOM = true,
      getRulesFromHostname = true
    }) {
      const hostnameTokens = createLookupTokens(hostname, domain);
      const rules = [];
      if (getRulesFromHostname === true) {
        this.hostnameIndex.iterMatchingFilters(hostnameTokens, (rule) => {
          if ((allowSpecificHides === true || rule.isScriptInject() === true) && rule.match(hostname, domain)) {
            rules.push(rule);
          }
          return true;
        });
      }
      if (allowGenericHides === true && getRulesFromHostname === true) {
        const genericRules = this.getGenericRules();
        for (const rule of genericRules) {
          if (rule.match(hostname, domain) === true) {
            rules.push(rule);
          }
        }
      }
      if (allowGenericHides === true && getRulesFromDOM === true && classes.length !== 0) {
        this.classesIndex.iterMatchingFilters(hashStrings(classes), (rule) => {
          if (rule.match(hostname, domain)) {
            rules.push(rule);
          }
          return true;
        });
      }
      if (allowGenericHides === true && getRulesFromDOM === true && ids.length !== 0) {
        this.idsIndex.iterMatchingFilters(hashStrings(ids), (rule) => {
          if (rule.match(hostname, domain)) {
            rules.push(rule);
          }
          return true;
        });
      }
      if (allowGenericHides === true && getRulesFromDOM === true && hrefs.length !== 0) {
        this.hrefsIndex.iterMatchingFilters(compactTokens(concatTypedArrays(hrefs.map((href) => tokenizeNoSkip(href)))), (rule) => {
          if (rule.match(hostname, domain)) {
            rules.push(rule);
          }
          return true;
        });
      }
      const extended = [];
      const injections = [];
      const styles = [];
      if (rules.length !== 0) {
        let injectionsDisabled = false;
        const disabledRules = /* @__PURE__ */ new Set();
        this.unhideIndex.iterMatchingFilters(hostnameTokens, (rule) => {
          if (rule.match(hostname, domain)) {
            disabledRules.add(rule.getSelector());
            if (rule.isScriptInject() === true && rule.isUnhide() === true && rule.getSelector().length === 0) {
              injectionsDisabled = true;
            }
          }
          return true;
        });
        for (const rule of rules) {
          if (disabledRules.size !== 0 && disabledRules.has(rule.getSelector())) {
            continue;
          }
          if (rule.isScriptInject() === true) {
            if (getInjectionRules === true && injectionsDisabled === false) {
              injections.push(rule);
            }
          } else if (rule.isExtended()) {
            if (getExtendedRules === true) {
              extended.push(rule);
            }
          } else {
            styles.push(rule);
          }
        }
      }
      let stylesheet = getBaseRules === false || allowGenericHides === false ? "" : this.getBaseStylesheet();
      if (styles.length !== 0) {
        if (stylesheet.length !== 0) {
          stylesheet += "\n\n";
        }
        stylesheet += createStylesheetFromRules(styles);
      }
      const extendedProcessed = [];
      if (extended.length !== 0) {
        const extendedStyles = /* @__PURE__ */ new Map();
        for (const rule of extended) {
          const ast = rule.getSelectorAST();
          if (ast !== void 0) {
            const attribute = rule.isRemove() ? void 0 : rule.getStyleAttributeHash();
            if (attribute !== void 0) {
              extendedStyles.set(rule.getStyle(), attribute);
            }
            extendedProcessed.push({
              ast,
              remove: rule.isRemove(),
              attribute
            });
          }
        }
        if (extendedStyles.size !== 0) {
          if (stylesheet.length !== 0) {
            stylesheet += "\n\n";
          }
          stylesheet += [...extendedStyles.entries()].map(([style, attribute]) => `[${attribute}] { ${style} }`).join("\n\n");
        }
      }
      return {
        extended: extendedProcessed,
        injections,
        stylesheet
      };
    }
    /**
     * Return the list of filters which can potentially be un-hidden by another
     * rule currently contained in the cosmetic bucket.
     */
    getGenericRules() {
      if (this.extraGenericRules === null) {
        return this.lazyPopulateGenericRulesCache().genericRules;
      }
      return this.extraGenericRules;
    }
    /**
     * The base stylesheet is made of generic filters (not specific to any
     * hostname) which cannot be hidden (i.e.: there is currently no rule which
     * might hide their selector). This means that it will never change and is
     * the same for all sites. We generate it once and re-use it any-time we want
     * to inject it.
     */
    getBaseStylesheet() {
      if (this.baseStylesheet === null) {
        return this.lazyPopulateGenericRulesCache().baseStylesheet;
      }
      return this.baseStylesheet;
    }
    /**
     * This is used to lazily generate both the list of generic rules which can
     * *potentially be un-hidden* (i.e.: there exists at least one unhide rule
     * for the selector) and a stylesheet containing all selectors which cannot
     * be un-hidden. Since this list will not change between updates we can
     * generate once and use many times.
     */
    lazyPopulateGenericRulesCache() {
      if (this.baseStylesheet === null || this.extraGenericRules === null) {
        const unHideRules = this.unhideIndex.getFilters();
        const canBeHiddenSelectors = /* @__PURE__ */ new Set();
        for (const rule of unHideRules) {
          canBeHiddenSelectors.add(rule.getSelector());
        }
        const genericRules = this.genericRules.getFilters();
        const cannotBeHiddenRules = [];
        const canBeHiddenRules = [];
        for (const rule of genericRules) {
          if (rule.hasCustomStyle() || rule.isScriptInject() || rule.hasHostnameConstraint() || canBeHiddenSelectors.has(rule.getSelector())) {
            canBeHiddenRules.push(rule);
          } else {
            cannotBeHiddenRules.push(rule);
          }
        }
        this.baseStylesheet = createStylesheetFromRules(cannotBeHiddenRules);
        this.extraGenericRules = canBeHiddenRules;
      }
      return {
        baseStylesheet: this.baseStylesheet,
        genericRules: this.extraGenericRules
      };
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/engine/bucket/network.js
  var NetworkFilterBucket = class _NetworkFilterBucket {
    static deserialize(buffer, config) {
      const bucket = new _NetworkFilterBucket({ config });
      bucket.index = ReverseIndex.deserialize(buffer, NetworkFilter.deserialize, config.enableOptimizations ? optimizeNetwork : noopOptimizeNetwork, config);
      bucket.badFilters = FiltersContainer.deserialize(buffer, NetworkFilter.deserialize, config);
      return bucket;
    }
    constructor({ filters = [], config }) {
      this.index = new ReverseIndex({
        config,
        deserialize: NetworkFilter.deserialize,
        filters: [],
        optimize: config.enableOptimizations ? optimizeNetwork : noopOptimizeNetwork
      });
      this.badFiltersIds = null;
      this.badFilters = new FiltersContainer({
        config,
        deserialize: NetworkFilter.deserialize,
        filters: []
      });
      if (filters.length !== 0) {
        this.update(filters, void 0);
      }
    }
    getFilters() {
      const filters = [];
      return filters.concat(this.badFilters.getFilters(), this.index.getFilters());
    }
    update(newFilters, removedFilters) {
      const badFilters = [];
      const remaining = [];
      for (const filter of newFilters) {
        if (filter.isBadFilter()) {
          badFilters.push(filter);
        } else {
          remaining.push(filter);
        }
      }
      this.badFilters.update(badFilters, removedFilters);
      this.index.update(remaining, removedFilters);
      this.badFiltersIds = null;
    }
    getSerializedSize() {
      return this.badFilters.getSerializedSize() + this.index.getSerializedSize();
    }
    serialize(buffer) {
      this.index.serialize(buffer);
      this.badFilters.serialize(buffer);
    }
    matchAll(request) {
      const filters = [];
      this.index.iterMatchingFilters(request.getTokens(), (filter) => {
        if (filter.match(request) && this.isFilterDisabled(filter) === false) {
          filters.push(filter);
        }
        return true;
      });
      return filters;
    }
    match(request) {
      let match;
      this.index.iterMatchingFilters(request.getTokens(), (filter) => {
        if (filter.match(request) && this.isFilterDisabled(filter) === false) {
          match = filter;
          return false;
        }
        return true;
      });
      return match;
    }
    /**
     * Given a matching filter, check if it is disabled by a $badfilter
     */
    isFilterDisabled(filter) {
      if (this.badFiltersIds === null) {
        const badFilters = this.badFilters.getFilters();
        if (badFilters.length === 0) {
          return false;
        }
        const badFiltersIds = /* @__PURE__ */ new Set();
        for (const badFilter of badFilters) {
          badFiltersIds.add(badFilter.getIdWithoutBadFilter());
        }
        this.badFiltersIds = badFiltersIds;
      }
      return this.badFiltersIds.has(filter.getId());
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/engine/map.js
  var EMPTY_BUCKET2 = Number.MAX_SAFE_INTEGER >>> 0;
  var CompactMap = class _CompactMap {
    static deserialize(buffer, deserialize4) {
      const tokensLookupIndexSize = buffer.getUint32();
      const bucketsIndexSize = buffer.getUint32();
      const numberOfValues = buffer.getUint32();
      const view = StaticDataView.fromUint8Array(buffer.getBytes(
        true
        /* align */
      ), {
        enableCompression: false
      });
      const tokensLookupIndex = view.getUint32ArrayView(tokensLookupIndexSize);
      const bucketsIndex = view.getUint32ArrayView(bucketsIndexSize);
      const valuesIndexStart = view.pos;
      view.seekZero();
      return new _CompactMap({
        deserialize: deserialize4,
        // Left empty on purpose since we don't need these to deserialize (all
        // the data is already in the serialized data).
        values: [],
        getKeys: () => [],
        getSerializedSize: () => 0,
        serialize: () => {
        }
      }).updateInternals({
        bucketsIndex,
        valuesIndexStart,
        numberOfValues,
        tokensLookupIndex,
        view
      });
    }
    constructor({ serialize: serialize4, deserialize: deserialize4, getKeys: getKeys2, getSerializedSize: getSerializedSize4, values }) {
      this.cache = /* @__PURE__ */ new Map();
      this.bucketsIndex = EMPTY_UINT32_ARRAY;
      this.tokensLookupIndex = EMPTY_UINT32_ARRAY;
      this.valuesIndexStart = 0;
      this.numberOfValues = 0;
      this.view = StaticDataView.empty({ enableCompression: false });
      this.deserializeValue = deserialize4;
      if (values.length !== 0) {
        const patternsKeys = [];
        let bucketsIndexSize = 0;
        let estimatedBufferSize = 0;
        for (const value of values) {
          estimatedBufferSize += getSerializedSize4(value);
        }
        if (values.length === 0) {
          this.updateInternals({
            bucketsIndex: EMPTY_UINT32_ARRAY,
            valuesIndexStart: 0,
            numberOfValues: 0,
            tokensLookupIndex: EMPTY_UINT32_ARRAY,
            view: StaticDataView.empty({ enableCompression: false })
          });
          return;
        }
        for (const value of values) {
          const keys = getKeys2(value);
          patternsKeys.push(keys);
          bucketsIndexSize += 2 * keys.length;
        }
        estimatedBufferSize += bucketsIndexSize * 4;
        const tokensLookupIndexSize = Math.max(2, nextPow2(values.length));
        const mask = tokensLookupIndexSize - 1;
        const suffixes = [];
        for (let i = 0; i < tokensLookupIndexSize; i += 1) {
          suffixes.push([]);
        }
        estimatedBufferSize += tokensLookupIndexSize * 4;
        const buffer = StaticDataView.allocate(estimatedBufferSize, { enableCompression: false });
        const tokensLookupIndex = buffer.getUint32ArrayView(tokensLookupIndexSize);
        const bucketsIndex = buffer.getUint32ArrayView(bucketsIndexSize);
        const valuesIndexStart = buffer.getPos();
        for (let i = 0; i < patternsKeys.length; i += 1) {
          const value = values[i];
          const keys = patternsKeys[i];
          const valueIndex = buffer.pos;
          serialize4(value, buffer);
          for (const key of keys) {
            suffixes[key & mask].push([key, valueIndex]);
          }
        }
        let indexInBucketsIndex = 0;
        for (let i = 0; i < tokensLookupIndexSize; i += 1) {
          const valuesForMask = suffixes[i];
          tokensLookupIndex[i] = indexInBucketsIndex;
          for (const [token, valueIndex] of valuesForMask) {
            bucketsIndex[indexInBucketsIndex++] = token;
            bucketsIndex[indexInBucketsIndex++] = valueIndex;
          }
        }
        this.updateInternals({
          bucketsIndex,
          valuesIndexStart,
          numberOfValues: patternsKeys.length,
          tokensLookupIndex,
          view: buffer
        });
      }
    }
    updateInternals({ bucketsIndex, valuesIndexStart, numberOfValues, tokensLookupIndex, view }) {
      this.bucketsIndex = bucketsIndex;
      this.valuesIndexStart = valuesIndexStart;
      this.numberOfValues = numberOfValues;
      this.tokensLookupIndex = tokensLookupIndex;
      this.view = view;
      view.seekZero();
      return this;
    }
    getValues() {
      const values = [];
      if (this.numberOfValues === 0) {
        return values;
      }
      this.view.setPos(this.valuesIndexStart);
      for (let i = 0; i < this.numberOfValues; i += 1) {
        values.push(this.deserializeValue(this.view));
      }
      this.view.seekZero();
      return values;
    }
    /**
     * Estimate the number of bytes needed to serialize this instance of `Map`.
     */
    getSerializedSize() {
      return 12 + sizeOfBytes(
        this.view.buffer,
        true
        /* align */
      );
    }
    /**
     * Dump this index to `buffer`.
     */
    serialize(buffer) {
      buffer.pushUint32(this.tokensLookupIndex.length);
      buffer.pushUint32(this.bucketsIndex.length);
      buffer.pushUint32(this.numberOfValues);
      buffer.pushBytes(
        this.view.buffer,
        true
        /* align */
      );
    }
    get(key) {
      const cachedValues = this.cache.get(key);
      if (cachedValues !== void 0) {
        return cachedValues;
      }
      const offset = key & this.tokensLookupIndex.length - 1;
      const startOfBucket = this.tokensLookupIndex[offset];
      if (startOfBucket === EMPTY_BUCKET2) {
        return [];
      }
      const endOfBucket = offset === this.tokensLookupIndex.length - 1 ? this.bucketsIndex.length : this.tokensLookupIndex[offset + 1];
      const valuesIndices = [];
      for (let i = startOfBucket; i < endOfBucket; i += 2) {
        const currentToken = this.bucketsIndex[i];
        if (currentToken === key) {
          valuesIndices.push(this.bucketsIndex[i + 1]);
        }
      }
      if (valuesIndices.length === 0) {
        return [];
      }
      const values = [];
      const view = this.view;
      for (let i = 0; i < valuesIndices.length; i += 1) {
        view.setPos(valuesIndices[i]);
        values.push(this.deserializeValue(view));
      }
      this.cache.set(key, values);
      return values;
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/engine/metadata/categories.js
  function isValid(category) {
    if (category === null) {
      return false;
    }
    if (typeof category !== "object") {
      return false;
    }
    const { key, name, color, description } = category;
    if (typeof key !== "string") {
      return false;
    }
    if (typeof name !== "string") {
      return false;
    }
    if (typeof color !== "string") {
      return false;
    }
    if (typeof description !== "string") {
      return false;
    }
    return true;
  }
  function getKey(category) {
    return fastHash(category.key);
  }
  function getSerializedSize(category) {
    return sizeOfUTF8(category.key) + sizeOfUTF8(category.name) + sizeOfUTF8(category.color) + sizeOfUTF8(category.description);
  }
  function serialize(category, view) {
    view.pushUTF8(category.key);
    view.pushUTF8(category.name);
    view.pushUTF8(category.color);
    view.pushUTF8(category.description);
  }
  function deserialize(view) {
    return {
      key: view.getUTF8(),
      name: view.getUTF8(),
      color: view.getUTF8(),
      description: view.getUTF8()
    };
  }
  function createMap(categories) {
    return new CompactMap({
      getSerializedSize,
      getKeys: (category) => [getKey(category)],
      serialize,
      deserialize,
      values: categories
    });
  }

  // node_modules/@cliqz/adblocker/dist/es6/src/engine/metadata/organizations.js
  function isValid2(organization) {
    if (organization === null) {
      return false;
    }
    if (typeof organization !== "object") {
      return false;
    }
    const { key, name, description, country, website_url: websiteUrl, privacy_policy_url: privacyPolicyUrl, privacy_contact: privacyContact, ghostery_id: ghosteryId } = organization;
    if (typeof key !== "string") {
      return false;
    }
    if (typeof name !== "string") {
      return false;
    }
    if (description !== null && typeof description !== "string") {
      return false;
    }
    if (country !== null && typeof country !== "string") {
      return false;
    }
    if (websiteUrl !== null && typeof websiteUrl !== "string") {
      return false;
    }
    if (privacyPolicyUrl !== null && typeof privacyPolicyUrl !== "string") {
      return false;
    }
    if (privacyContact !== null && typeof privacyContact !== "string") {
      return false;
    }
    if (ghosteryId !== null && typeof ghosteryId !== "string") {
      return false;
    }
    return true;
  }
  function getKey2(organization) {
    return fastHash(organization.key);
  }
  function getSerializedSize2(organization) {
    return sizeOfUTF8(organization.key) + sizeOfUTF8(organization.name) + sizeOfUTF8(organization.description || "") + sizeOfUTF8(organization.website_url || "") + sizeOfUTF8(organization.country || "") + sizeOfUTF8(organization.privacy_policy_url || "") + sizeOfUTF8(organization.privacy_contact || "") + sizeOfUTF8(organization.ghostery_id || "");
  }
  function serialize2(organization, view) {
    view.pushUTF8(organization.key);
    view.pushUTF8(organization.name);
    view.pushUTF8(organization.description || "");
    view.pushUTF8(organization.website_url || "");
    view.pushUTF8(organization.country || "");
    view.pushUTF8(organization.privacy_policy_url || "");
    view.pushUTF8(organization.privacy_contact || "");
    view.pushUTF8(organization.ghostery_id || "");
  }
  function deserialize2(view) {
    return {
      key: view.getUTF8(),
      name: view.getUTF8(),
      description: view.getUTF8() || null,
      website_url: view.getUTF8() || null,
      country: view.getUTF8() || null,
      privacy_policy_url: view.getUTF8() || null,
      privacy_contact: view.getUTF8() || null,
      ghostery_id: view.getUTF8() || null
    };
  }
  function createMap2(organizations) {
    return new CompactMap({
      getSerializedSize: getSerializedSize2,
      getKeys: (organization) => [getKey2(organization)],
      serialize: serialize2,
      deserialize: deserialize2,
      values: organizations
    });
  }

  // node_modules/@cliqz/adblocker/dist/es6/src/engine/metadata/patterns.js
  function isValid3(pattern) {
    if (pattern === null) {
      return false;
    }
    if (typeof pattern !== "object") {
      return false;
    }
    const { key, name, category, organization, alias, website_url: websiteUrl, domains, filters } = pattern;
    if (typeof key !== "string") {
      return false;
    }
    if (typeof name !== "string") {
      return false;
    }
    if (typeof category !== "string") {
      return false;
    }
    if (organization !== null && typeof organization !== "string") {
      return false;
    }
    if (typeof alias !== "string" && alias !== null) {
      return false;
    }
    if (websiteUrl !== null && typeof websiteUrl !== "string") {
      return false;
    }
    if (!Array.isArray(domains) || !domains.every((domain) => typeof domain === "string")) {
      return false;
    }
    if (!Array.isArray(filters) || !filters.every((filter) => typeof filter === "string")) {
      return false;
    }
    return true;
  }
  function getKeys(pattern) {
    const keys = [];
    for (const filter of pattern.filters) {
      const parsedFilter = NetworkFilter.parse(filter);
      if (parsedFilter !== null) {
        keys.push(parsedFilter.getId());
      }
    }
    for (const domain of pattern.domains) {
      const parsedFilter = NetworkFilter.parse(`||${domain}^`);
      if (parsedFilter !== null) {
        keys.push(parsedFilter.getId());
      }
    }
    return [...new Set(keys)];
  }
  function getSerializedSize3(pattern) {
    let sizeOfDomains = sizeOfLength(pattern.domains.length);
    for (const domain of pattern.domains) {
      sizeOfDomains += sizeOfUTF8(domain);
    }
    let sizeOfFilters = sizeOfLength(pattern.filters.length);
    for (const filter of pattern.filters) {
      sizeOfFilters += sizeOfUTF8(filter);
    }
    return sizeOfUTF8(pattern.key) + sizeOfUTF8(pattern.name) + sizeOfUTF8(pattern.category) + sizeOfUTF8(pattern.organization || "") + sizeOfUTF8(pattern.alias || "") + sizeOfUTF8(pattern.website_url || "") + sizeOfUTF8(pattern.ghostery_id || "") + sizeOfDomains + sizeOfFilters;
  }
  function serialize3(pattern, view) {
    view.pushUTF8(pattern.key);
    view.pushUTF8(pattern.name);
    view.pushUTF8(pattern.category);
    view.pushUTF8(pattern.organization || "");
    view.pushUTF8(pattern.alias || "");
    view.pushUTF8(pattern.website_url || "");
    view.pushUTF8(pattern.ghostery_id || "");
    view.pushLength(pattern.domains.length);
    for (const domain of pattern.domains) {
      view.pushUTF8(domain);
    }
    view.pushLength(pattern.filters.length);
    for (const filter of pattern.filters) {
      view.pushUTF8(filter);
    }
  }
  function deserialize3(view) {
    const key = view.getUTF8();
    const name = view.getUTF8();
    const category = view.getUTF8();
    const organization = view.getUTF8() || null;
    const alias = view.getUTF8() || null;
    const website_url = view.getUTF8() || null;
    const ghostery_id = view.getUTF8() || null;
    const numberOfDomains = view.getLength();
    const domains = [];
    for (let i = 0; i < numberOfDomains; i += 1) {
      domains.push(view.getUTF8());
    }
    const numberOfFilters = view.getLength();
    const filters = [];
    for (let i = 0; i < numberOfFilters; i += 1) {
      filters.push(view.getUTF8());
    }
    return {
      key,
      name,
      category,
      organization,
      alias,
      website_url,
      ghostery_id,
      domains,
      filters
    };
  }
  function createMap3(patterns) {
    return new CompactMap({
      getSerializedSize: getSerializedSize3,
      getKeys,
      serialize: serialize3,
      deserialize: deserialize3,
      values: patterns
    });
  }

  // node_modules/@cliqz/adblocker/dist/es6/src/engine/metadata.js
  var Metadata = class _Metadata {
    static deserialize(buffer) {
      const metadata = new _Metadata(null);
      metadata.categories = CompactMap.deserialize(buffer, deserialize);
      metadata.organizations = CompactMap.deserialize(buffer, deserialize2);
      metadata.patterns = CompactMap.deserialize(buffer, deserialize3);
      return metadata;
    }
    constructor(rawTrackerDB) {
      if (!rawTrackerDB) {
        this.organizations = createMap2([]);
        this.categories = createMap([]);
        this.patterns = createMap3([]);
        return;
      }
      const { patterns: rawPatterns, organizations: rawOrganizations, categories: rawCategories } = rawTrackerDB;
      const categories = [];
      if (typeof rawCategories === "object") {
        for (const [key, category] of Object.entries(rawCategories)) {
          if (typeof category !== "object") {
            continue;
          }
          const categoryWithKey = Object.assign({ key }, category);
          if (isValid(categoryWithKey)) {
            categories.push(categoryWithKey);
          } else {
            console.error("?? invalid category", categoryWithKey);
          }
        }
      }
      this.categories = createMap(categories);
      const organizations = [];
      if (typeof rawOrganizations === "object") {
        for (const [key, organization] of Object.entries(rawOrganizations)) {
          if (typeof organization !== "object") {
            continue;
          }
          const organizationWithKey = Object.assign({ key }, organization);
          if (isValid2(organizationWithKey)) {
            organizations.push(organizationWithKey);
          } else {
            console.error("?? invalid organization", organizationWithKey);
          }
        }
      }
      this.organizations = createMap2(organizations);
      const patterns = [];
      if (typeof rawPatterns === "object") {
        for (const [key, pattern] of Object.entries(rawPatterns)) {
          if (typeof pattern !== "object") {
            continue;
          }
          const patternWithKey = Object.assign({ key }, pattern);
          if (isValid3(patternWithKey)) {
            patterns.push(patternWithKey);
          } else {
            console.error("?? invalid pattern", patternWithKey);
          }
        }
      }
      this.patterns = createMap3(patterns);
    }
    getCategories() {
      return this.categories.getValues();
    }
    getOrganizations() {
      return this.organizations.getValues();
    }
    getPatterns() {
      return this.patterns.getValues();
    }
    /**
     * Estimate the total serialized size of this Metadata instance.
     */
    getSerializedSize() {
      return this.categories.getSerializedSize() + this.organizations.getSerializedSize() + this.patterns.getSerializedSize();
    }
    /**
     * Serialize this instance of Metadata into `view`
     */
    serialize(buffer) {
      this.categories.serialize(buffer);
      this.organizations.serialize(buffer);
      this.patterns.serialize(buffer);
    }
    /**
     * Given an instance of NetworkFilter, retrieve pattern, organization and
     * category information.
     */
    fromFilter(filter) {
      return this.fromId(filter.getId());
    }
    /**
     * Given a domain, retrieve pattern, organization and category information.
     */
    fromDomain(domain) {
      const domainParts = domain.split(".");
      for (; domainParts.length >= 2; domainParts.shift()) {
        const subdomain = domainParts.join(".");
        const parsedDomainFilter = NetworkFilter.parse(`||${subdomain}^`);
        if (parsedDomainFilter === null) {
          continue;
        }
        const patterns = this.fromId(parsedDomainFilter.getId());
        if (patterns.length > 0) {
          return patterns;
        }
      }
      return [];
    }
    /**
     * Given an `id` from filter, retrieve using the NetworkFilter.getId() method,
     * lookup associated patterns (including organization and category) in an
     * efficient way.
     */
    fromId(id) {
      var _a, _b;
      const results = [];
      for (const pattern of this.patterns.get(id)) {
        results.push({
          pattern,
          category: (_a = this.categories.get(getKey({ key: pattern.category }))) === null || _a === void 0 ? void 0 : _a[0],
          organization: pattern.organization !== null ? (_b = this.organizations.get(getKey2({ key: pattern.organization }))) === null || _b === void 0 ? void 0 : _b[0] : null
        });
      }
      return results;
    }
  };

  // node_modules/@cliqz/adblocker/dist/es6/src/engine/engine.js
  var ENGINE_VERSION = 626;
  function shouldApplyHideException(filters) {
    if (filters.length === 0) {
      return false;
    }
    let genericHideFilter;
    let currentScore = 0;
    for (const filter of filters) {
      const score = (filter.isImportant() ? 4 : 0) | (filter.isException() ? 1 : 2);
      if (score >= currentScore) {
        currentScore = score;
        genericHideFilter = filter;
      }
    }
    if (genericHideFilter === void 0) {
      return false;
    }
    return genericHideFilter.isException();
  }
  var FilterEngine = class extends EventEmitter {
    static fromCached(init, caching) {
      if (caching === void 0) {
        return init();
      }
      const { path, read, write } = caching;
      return read(path).then((buffer) => this.deserialize(buffer)).catch(() => init().then((engine) => write(path, engine.serialize()).then(() => engine)));
    }
    static empty(config = {}) {
      return new this({ config });
    }
    /**
     * Create an instance of `FiltersEngine` (or subclass like `ElectronBlocker`,
     * etc.), from the list of subscriptions provided as argument (e.g.:
     * EasyList).
     *
     * Lists are fetched using the instance of `fetch` provided as a first
     * argument. Optionally resources.txt and config can be provided.
     */
    static fromLists(fetch2, urls, config = {}, caching) {
      return this.fromCached(() => {
        const listsPromises = fetchLists(fetch2, urls);
        const resourcesPromise = fetchResources(fetch2);
        return Promise.all([listsPromises, resourcesPromise]).then(([lists, resources]) => {
          const engine = this.parse(lists.join("\n"), config);
          if (resources !== void 0) {
            engine.updateResources(resources, "" + resources.length);
          }
          return engine;
        });
      }, caching);
    }
    /**
     * Initialize blocker of *ads only*.
     *
     * Attempt to initialize a blocking engine using a pre-built version served
     * from Cliqz's CDN. If this fails (e.g.: if no pre-built engine is available
     * for this version of the library), then falls-back to using `fromLists(...)`
     * method with the same subscriptions.
     */
    static fromPrebuiltAdsOnly(fetchImpl = fetch, caching) {
      return this.fromLists(fetchImpl, adsLists, {}, caching);
    }
    /**
     * Same as `fromPrebuiltAdsOnly(...)` but also contains rules to block
     * tracking (i.e.: using extra lists such as EasyPrivacy and more).
     */
    static fromPrebuiltAdsAndTracking(fetchImpl = fetch, caching) {
      return this.fromLists(fetchImpl, adsAndTrackingLists, {}, caching);
    }
    /**
     * Same as `fromPrebuiltAdsAndTracking(...)` but also contains annoyances
     * rules to block things like cookie notices.
     */
    static fromPrebuiltFull(fetchImpl = fetch, caching) {
      return this.fromLists(fetchImpl, fullLists, {}, caching);
    }
    static fromTrackerDB(rawJsonDump, options = {}) {
      const config = new Config(options);
      const metadata = new Metadata(rawJsonDump);
      const filters = [];
      for (const pattern of metadata.getPatterns()) {
        filters.push(...pattern.filters);
      }
      const engine = this.parse(filters.join("\n"), config);
      engine.metadata = metadata;
      return engine;
    }
    static parse(filters, options = {}) {
      const config = new Config(options);
      return new this(Object.assign(Object.assign({}, parseFilters(filters, config)), { config }));
    }
    static deserialize(serialized) {
      const buffer = StaticDataView.fromUint8Array(serialized, {
        enableCompression: false
      });
      const serializedEngineVersion = buffer.getUint16();
      if (ENGINE_VERSION !== serializedEngineVersion) {
        throw new Error(`serialized engine version mismatch, expected ${ENGINE_VERSION} but got ${serializedEngineVersion}`);
      }
      const config = Config.deserialize(buffer);
      if (config.enableCompression) {
        buffer.enableCompression();
      }
      if (config.integrityCheck) {
        const currentPos = buffer.pos;
        buffer.pos = serialized.length - 4;
        const checksum = buffer.checksum();
        const expected = buffer.getUint32();
        if (checksum !== expected) {
          throw new Error(`serialized engine checksum mismatch, expected ${expected} but got ${checksum}`);
        }
        buffer.pos = currentPos;
      }
      const engine = new this({ config });
      engine.resources = Resources.deserialize(buffer);
      const lists = /* @__PURE__ */ new Map();
      const numberOfLists = buffer.getUint16();
      for (let i = 0; i < numberOfLists; i += 1) {
        lists.set(buffer.getASCII(), buffer.getASCII());
      }
      engine.lists = lists;
      engine.importants = NetworkFilterBucket.deserialize(buffer, config);
      engine.redirects = NetworkFilterBucket.deserialize(buffer, config);
      engine.filters = NetworkFilterBucket.deserialize(buffer, config);
      engine.exceptions = NetworkFilterBucket.deserialize(buffer, config);
      engine.csp = NetworkFilterBucket.deserialize(buffer, config);
      engine.cosmetics = CosmeticFilterBucket.deserialize(buffer, config);
      engine.hideExceptions = NetworkFilterBucket.deserialize(buffer, config);
      const hasMetadata = buffer.getBool();
      if (hasMetadata) {
        engine.metadata = Metadata.deserialize(buffer);
      }
      buffer.seekZero();
      return engine;
    }
    constructor({
      // Optionally initialize the engine with filters
      cosmeticFilters = [],
      networkFilters = [],
      config = new Config(),
      lists = /* @__PURE__ */ new Map()
    } = {}) {
      super();
      this.config = new Config(config);
      this.lists = lists;
      this.csp = new NetworkFilterBucket({ config: this.config });
      this.hideExceptions = new NetworkFilterBucket({ config: this.config });
      this.exceptions = new NetworkFilterBucket({ config: this.config });
      this.importants = new NetworkFilterBucket({ config: this.config });
      this.redirects = new NetworkFilterBucket({ config: this.config });
      this.filters = new NetworkFilterBucket({ config: this.config });
      this.cosmetics = new CosmeticFilterBucket({ config: this.config });
      this.resources = new Resources();
      if (networkFilters.length !== 0 || cosmeticFilters.length !== 0) {
        this.update({
          newCosmeticFilters: cosmeticFilters,
          newNetworkFilters: networkFilters
        });
      }
    }
    /**
     * Estimate the number of bytes needed to serialize this instance of
     * `FiltersEngine` using the `serialize(...)` method. It is used internally
     * by `serialize(...)` to allocate a buffer of the right size and you should
     * not have to call it yourself most of the time.
     *
     * There are cases where we cannot estimate statically the exact size of the
     * resulting buffer (due to alignement which needs to be performed); this
     * method will return a safe estimate which will always be at least equal to
     * the real number of bytes needed, or bigger (usually of a few bytes only:
     * ~20 bytes is to be expected).
     */
    getSerializedSize() {
      let estimatedSize = sizeOfByte() + // engine version
      this.config.getSerializedSize() + this.resources.getSerializedSize() + this.filters.getSerializedSize() + this.exceptions.getSerializedSize() + this.importants.getSerializedSize() + this.redirects.getSerializedSize() + this.csp.getSerializedSize() + this.cosmetics.getSerializedSize() + this.hideExceptions.getSerializedSize() + 4;
      for (const [name, checksum] of this.lists) {
        estimatedSize += sizeOfASCII(name) + sizeOfASCII(checksum);
      }
      estimatedSize += sizeOfBool();
      if (this.metadata !== void 0) {
        estimatedSize += this.metadata.getSerializedSize();
      }
      return estimatedSize;
    }
    /**
     * Creates a binary representation of the full engine. It can be stored
     * on-disk for faster loading of the adblocker. The `deserialize` static
     * method of Engine can be used to restore the engine.
     */
    serialize(array) {
      const buffer = StaticDataView.fromUint8Array(array || new Uint8Array(this.getSerializedSize()), this.config);
      buffer.pushUint16(ENGINE_VERSION);
      this.config.serialize(buffer);
      this.resources.serialize(buffer);
      buffer.pushUint16(this.lists.size);
      for (const [name, value] of Array.from(this.lists.entries()).sort()) {
        buffer.pushASCII(name);
        buffer.pushASCII(value);
      }
      this.importants.serialize(buffer);
      this.redirects.serialize(buffer);
      this.filters.serialize(buffer);
      this.exceptions.serialize(buffer);
      this.csp.serialize(buffer);
      this.cosmetics.serialize(buffer);
      this.hideExceptions.serialize(buffer);
      buffer.pushBool(this.metadata !== void 0);
      if (this.metadata !== void 0) {
        this.metadata.serialize(buffer);
      }
      if (this.config.integrityCheck) {
        buffer.pushUint32(buffer.checksum());
      }
      return buffer.subarray();
    }
    /**
     * Update engine with new filters or resources.
     */
    loadedLists() {
      return Array.from(this.lists.keys());
    }
    hasList(name, checksum) {
      return this.lists.has(name) && this.lists.get(name) === checksum;
    }
    /**
     * Update engine with `resources.txt` content.
     */
    updateResources(data, checksum) {
      if (this.resources.checksum === checksum) {
        return false;
      }
      this.resources = Resources.parse(data, { checksum });
      return true;
    }
    getFilters() {
      const cosmeticFilters = [];
      const networkFilters = [];
      return {
        cosmeticFilters: cosmeticFilters.concat(this.cosmetics.getFilters()),
        networkFilters: networkFilters.concat(this.filters.getFilters(), this.exceptions.getFilters(), this.importants.getFilters(), this.redirects.getFilters(), this.csp.getFilters(), this.hideExceptions.getFilters())
      };
    }
    /**
     * Update engine with new filters as well as optionally removed filters.
     */
    update({ newNetworkFilters = [], newCosmeticFilters = [], removedCosmeticFilters = [], removedNetworkFilters = [] }) {
      let updated = false;
      if (this.config.loadCosmeticFilters && (newCosmeticFilters.length !== 0 || removedCosmeticFilters.length !== 0)) {
        updated = true;
        this.cosmetics.update(newCosmeticFilters, removedCosmeticFilters.length === 0 ? void 0 : new Set(removedCosmeticFilters), this.config);
      }
      if (this.config.loadNetworkFilters && (newNetworkFilters.length !== 0 || removedNetworkFilters.length !== 0)) {
        updated = true;
        const filters = [];
        const csp = [];
        const exceptions = [];
        const importants = [];
        const redirects = [];
        const hideExceptions = [];
        for (const filter of newNetworkFilters) {
          if (filter.isCSP()) {
            csp.push(filter);
          } else if (filter.isGenericHide() || filter.isSpecificHide()) {
            hideExceptions.push(filter);
          } else if (filter.isException()) {
            exceptions.push(filter);
          } else if (filter.isImportant()) {
            importants.push(filter);
          } else if (filter.isRedirect()) {
            redirects.push(filter);
          } else {
            filters.push(filter);
          }
        }
        const removedNetworkFiltersSet = removedNetworkFilters.length === 0 ? void 0 : new Set(removedNetworkFilters);
        this.importants.update(importants, removedNetworkFiltersSet);
        this.redirects.update(redirects, removedNetworkFiltersSet);
        this.filters.update(filters, removedNetworkFiltersSet);
        if (this.config.loadExceptionFilters === true) {
          this.exceptions.update(exceptions, removedNetworkFiltersSet);
        }
        if (this.config.loadCSPFilters === true) {
          this.csp.update(csp, removedNetworkFiltersSet);
        }
        this.hideExceptions.update(hideExceptions, removedNetworkFiltersSet);
      }
      return updated;
    }
    updateFromDiff({ added, removed }) {
      const newCosmeticFilters = [];
      const newNetworkFilters = [];
      const removedCosmeticFilters = [];
      const removedNetworkFilters = [];
      if (removed !== void 0 && removed.length !== 0) {
        const { networkFilters, cosmeticFilters } = parseFilters(removed.join("\n"), this.config);
        Array.prototype.push.apply(removedCosmeticFilters, cosmeticFilters);
        Array.prototype.push.apply(removedNetworkFilters, networkFilters);
      }
      if (added !== void 0 && added.length !== 0) {
        const { networkFilters, cosmeticFilters } = parseFilters(added.join("\n"), this.config);
        Array.prototype.push.apply(newCosmeticFilters, cosmeticFilters);
        Array.prototype.push.apply(newNetworkFilters, networkFilters);
      }
      return this.update({
        newCosmeticFilters,
        newNetworkFilters,
        removedCosmeticFilters: removedCosmeticFilters.map((f2) => f2.getId()),
        removedNetworkFilters: removedNetworkFilters.map((f2) => f2.getId())
      });
    }
    /**
     * Return a list of HTML filtering rules.
     */
    getHtmlFilters({
      // Page information
      url,
      hostname,
      domain
    }) {
      const htmlSelectors = [];
      if (this.config.enableHtmlFiltering === false || this.config.loadCosmeticFilters === false) {
        return htmlSelectors;
      }
      const rules = this.cosmetics.getHtmlRules({
        domain: domain || "",
        hostname
      });
      for (const rule of rules) {
        const extended = rule.getExtendedSelector();
        if (extended !== void 0) {
          htmlSelectors.push(extended);
        }
      }
      if (htmlSelectors.length !== 0) {
        this.emit("html-filtered", htmlSelectors, url);
      }
      return htmlSelectors;
    }
    /**
     * Given `hostname` and `domain` of a page (or frame), return the list of
     * styles and scripts to inject in the page.
     */
    getCosmeticsFilters({
      // Page information
      url,
      hostname,
      domain,
      // DOM information
      classes,
      hrefs,
      ids,
      // Allows to specify which rules to return
      getBaseRules = true,
      getInjectionRules = true,
      getExtendedRules = true,
      getRulesFromDOM = true,
      getRulesFromHostname = true
    }) {
      if (this.config.loadCosmeticFilters === false) {
        return {
          active: false,
          extended: [],
          scripts: [],
          styles: ""
        };
      }
      let allowGenericHides = true;
      let allowSpecificHides = true;
      const exceptions = this.hideExceptions.matchAll(Request.fromRawDetails({
        domain: domain || "",
        hostname,
        url,
        sourceDomain: "",
        sourceHostname: "",
        sourceUrl: ""
      }));
      const genericHides = [];
      const specificHides = [];
      for (const filter of exceptions) {
        if (filter.isElemHide()) {
          allowGenericHides = false;
          allowSpecificHides = false;
          break;
        }
        if (filter.isSpecificHide()) {
          specificHides.push(filter);
        } else if (filter.isGenericHide()) {
          genericHides.push(filter);
        }
      }
      if (allowGenericHides === true) {
        allowGenericHides = shouldApplyHideException(genericHides) === false;
      }
      if (allowSpecificHides === true) {
        allowSpecificHides = shouldApplyHideException(specificHides) === false;
      }
      const { injections, stylesheet, extended } = this.cosmetics.getCosmeticsFilters({
        domain: domain || "",
        hostname,
        classes,
        hrefs,
        ids,
        allowGenericHides,
        allowSpecificHides,
        getBaseRules,
        getInjectionRules,
        getExtendedRules,
        getRulesFromDOM,
        getRulesFromHostname
      });
      const scripts = [];
      for (const injection of injections) {
        const script = injection.getScript(this.resources.js);
        if (script !== void 0) {
          this.emit("script-injected", script, url);
          scripts.push(script);
        }
      }
      if (stylesheet.length !== 0) {
        this.emit("style-injected", stylesheet, url);
      }
      return {
        active: true,
        extended,
        scripts,
        styles: stylesheet
      };
    }
    /**
     * Given a `request`, return all matching network filters found in the engine.
     */
    matchAll(request) {
      const filters = [];
      if (request.isSupported) {
        Array.prototype.push.apply(filters, this.importants.matchAll(request));
        Array.prototype.push.apply(filters, this.filters.matchAll(request));
        Array.prototype.push.apply(filters, this.exceptions.matchAll(request));
        Array.prototype.push.apply(filters, this.csp.matchAll(request));
        Array.prototype.push.apply(filters, this.hideExceptions.matchAll(request));
        Array.prototype.push.apply(filters, this.redirects.matchAll(request));
      }
      return new Set(filters);
    }
    /**
     * Given a "main_frame" request, check if some content security policies
     * should be injected in the page.
     */
    getCSPDirectives(request) {
      if (!this.config.loadNetworkFilters) {
        return void 0;
      }
      if (request.isSupported !== true || request.isMainFrame() === false) {
        return void 0;
      }
      const matches3 = this.csp.matchAll(request);
      if (matches3.length === 0) {
        return void 0;
      }
      const disabledCsp = /* @__PURE__ */ new Set();
      const enabledCsp = /* @__PURE__ */ new Set();
      for (const filter of matches3) {
        if (filter.isException()) {
          if (filter.csp === void 0) {
            return void 0;
          }
          disabledCsp.add(filter.csp);
        } else {
          enabledCsp.add(filter.csp);
        }
      }
      const csps = Array.from(enabledCsp).filter((csp) => !disabledCsp.has(csp)).join("; ") || void 0;
      if (csps !== void 0) {
        this.emit("csp-injected", csps, request);
      }
      return csps;
    }
    /**
     * Decide if a network request (usually from WebRequest API) should be
     * blocked, redirected or allowed.
     */
    match(request, withMetadata = false) {
      const result = {
        exception: void 0,
        filter: void 0,
        match: false,
        redirect: void 0,
        metadata: void 0
      };
      if (!this.config.loadNetworkFilters) {
        return result;
      }
      if (request.isSupported) {
        result.filter = this.importants.match(request);
        let redirectNone;
        let redirectRule;
        if (result.filter === void 0) {
          const redirects = this.redirects.matchAll(request);
          if (redirects.length !== 0) {
            for (const filter of redirects) {
              if (filter.getRedirect() === "none") {
                redirectNone = filter;
              } else if (filter.isRedirectRule()) {
                redirectRule = filter;
              } else {
                result.filter = filter;
              }
            }
          }
          if (result.filter === void 0) {
            result.filter = this.filters.match(request);
            if (redirectRule !== void 0 && result.filter !== void 0) {
              result.filter = redirectRule;
            }
          }
          if (result.filter !== void 0) {
            result.exception = this.exceptions.match(request);
          }
        }
        if (result.filter !== void 0 && result.exception === void 0 && result.filter.isRedirect()) {
          if (redirectNone !== void 0) {
            result.exception = redirectNone;
          } else {
            result.redirect = this.resources.getResource(result.filter.getRedirect());
          }
        }
      }
      result.match = result.exception === void 0 && result.filter !== void 0;
      if (result.exception !== void 0) {
        this.emit("request-whitelisted", request, result);
      } else if (result.redirect !== void 0) {
        this.emit("request-redirected", request, result);
      } else if (result.filter !== void 0) {
        this.emit("request-blocked", request, result);
      } else {
        this.emit("request-allowed", request, result);
      }
      if (withMetadata === true && result.filter !== void 0 && this.metadata) {
        result.metadata = this.metadata.fromFilter(result.filter);
      }
      return result;
    }
    getPatternMetadata(request, { getDomainMetadata = false } = {}) {
      if (this.metadata === void 0) {
        return [];
      }
      const seenPatterns = /* @__PURE__ */ new Set();
      const patterns = [];
      for (const filter of this.matchAll(request)) {
        for (const patternInfo of this.metadata.fromFilter(filter)) {
          if (!seenPatterns.has(patternInfo.pattern.key)) {
            seenPatterns.add(patternInfo.pattern.key);
            patterns.push(patternInfo);
          }
        }
      }
      if (getDomainMetadata) {
        for (const patternInfo of this.metadata.fromDomain(request.hostname)) {
          if (!seenPatterns.has(patternInfo.pattern.key)) {
            seenPatterns.add(patternInfo.pattern.key);
            patterns.push(patternInfo);
          }
        }
      }
      return patterns;
    }
    blockScripts() {
      this.updateFromDiff({
        added: [block().scripts().redirectTo("javascript").toString()]
      });
      return this;
    }
    blockImages() {
      this.updateFromDiff({
        added: [block().images().redirectTo("png").toString()]
      });
      return this;
    }
    blockMedias() {
      this.updateFromDiff({
        added: [block().medias().redirectTo("mp4").toString()]
      });
      return this;
    }
    blockFrames() {
      this.updateFromDiff({
        added: [block().frames().redirectTo("html").toString()]
      });
      return this;
    }
    blockFonts() {
      this.updateFromDiff({
        added: [block().fonts().toString()]
      });
      return this;
    }
    blockStyles() {
      this.updateFromDiff({
        added: [block().styles().toString()]
      });
      return this;
    }
  };

  // node_modules/@cliqz/adblocker-content/dist/es6/adblocker.js
  function extractFeaturesFromDOM(roots) {
    const ignoredTags = /* @__PURE__ */ new Set(["br", "head", "link", "meta", "script", "style", "s"]);
    const classes = /* @__PURE__ */ new Set();
    const hrefs = /* @__PURE__ */ new Set();
    const ids = /* @__PURE__ */ new Set();
    for (const root of roots) {
      for (const element of [
        root,
        ...root.querySelectorAll("[id]:not(html):not(body),[class]:not(html):not(body),[href]:not(html):not(body)")
      ]) {
        if (ignoredTags.has(element.nodeName.toLowerCase())) {
          continue;
        }
        const id = element.id;
        if (id) {
          ids.add(id);
        }
        const classList = element.classList;
        if (classList) {
          for (const cls of classList) {
            classes.add(cls);
          }
        }
        const href = element.getAttribute("href");
        if (typeof href === "string") {
          hrefs.add(href);
        }
      }
    }
    return {
      classes: Array.from(classes),
      hrefs: Array.from(hrefs),
      ids: Array.from(ids)
    };
  }

  // lib/filterlist-utils.ts
  function parseFilterList(rawFilterlist) {
    const engine = FilterEngine.parse(rawFilterlist, {
      enableMutationObserver: false,
      // we don't monitor DOM changes at the moment
      loadNetworkFilters: false,
      enableHtmlFiltering: false,
      loadCSPFilters: false
    });
    return engine;
  }
  function getFilterlistSelectors(engine) {
    const parsed = parse2(location.href);
    const hostname = parsed.hostname || "";
    const domain = parsed.domain || "";
    const cosmetics = engine.getCosmeticsFilters({
      url: location.href,
      hostname,
      domain,
      // this extracts current ids, classes and attributes (depends on the current DOM state)
      ...extractFeaturesFromDOM([document.documentElement]),
      getBaseRules: true,
      getInjectionRules: true,
      getExtendedRules: true,
      getRulesFromDOM: true,
      getRulesFromHostname: true
    });
    if (cosmetics.styles) {
      const selectorsOnly = cosmetics.styles.replace(/\s*{ display: none !important; }\s*/g, ",").replace(/,$/, "");
      return selectorsOnly;
    }
    return "";
  }

  // node_modules/time-to-interactive-polyfill/src/activityTrackerUtils.js
  var uniqueId = 0;
  function patchXMLHTTPRequest(beforeXHRSendCb, onRequestCompletedCb) {
    const send = XMLHttpRequest.prototype.send;
    const requestId = uniqueId++;
    XMLHttpRequest.prototype.send = function(...args) {
      beforeXHRSendCb(requestId);
      this.addEventListener("readystatechange", () => {
        if (this.readyState === 4)
          onRequestCompletedCb(requestId);
      });
      return send.apply(this, args);
    };
  }
  function patchFetch(beforeRequestCb, afterRequestCb) {
    const originalFetch = fetch;
    fetch = (...args) => {
      return new Promise((resolve, reject) => {
        const requestId = uniqueId++;
        beforeRequestCb(requestId);
        originalFetch(...args).then(
          (value) => {
            afterRequestCb(requestId);
            resolve(value);
          },
          (err) => {
            afterRequestCb(err);
            reject(err);
          }
        );
      });
    };
  }
  var requestCreatingNodeNames = ["img", "script", "iframe", "link", "audio", "video", "source"];
  function subtreeContainsNodeName(nodes, nodeNames) {
    for (const node of nodes) {
      if (nodeNames.includes(node.nodeName.toLowerCase()) || subtreeContainsNodeName(node.children, nodeNames)) {
        return true;
      }
    }
    return false;
  }
  function observeResourceFetchingMutations(callback) {
    const mutationObserver = new MutationObserver((mutations) => {
      mutations = /** @type {!Array<!MutationRecord>} */
      mutations;
      for (const mutation of mutations) {
        if (mutation.type == "childList" && subtreeContainsNodeName(
          mutation.addedNodes,
          requestCreatingNodeNames
        )) {
          callback(mutation);
        } else if (mutation.type == "attributes" && requestCreatingNodeNames.includes(
          mutation.target.tagName.toLowerCase()
        )) {
          callback(mutation);
        }
      }
    });
    mutationObserver.observe(document, {
      attributes: true,
      childList: true,
      subtree: true,
      attributeFilter: ["href", "src"]
    });
    return mutationObserver;
  }
  var log = (...args) => {
    {
      console.log(...args);
    }
  };

  // node_modules/time-to-interactive-polyfill/src/firstConsistentlyInteractiveCore.js
  var computeFirstConsistentlyInteractive = (searchStart, minValue, lastKnownNetwork2Busy, currentTime, longTasks) => {
    if (currentTime - lastKnownNetwork2Busy < 5e3)
      return null;
    const maybeFCI = longTasks.length === 0 ? searchStart : longTasks[longTasks.length - 1].end;
    if (currentTime - maybeFCI < 5e3)
      return null;
    return Math.max(maybeFCI, minValue);
  };
  var computeLastKnownNetwork2Busy = (incompleteRequestStarts, observedResourceRequests) => {
    if (incompleteRequestStarts.length > 2)
      return performance.now();
    const endpoints = [];
    for (const req of observedResourceRequests) {
      endpoints.push({
        timestamp: req.start,
        type: "requestStart"
      });
      endpoints.push({
        timestamp: req.end,
        type: "requestEnd"
      });
    }
    for (const ts of incompleteRequestStarts) {
      endpoints.push({
        timestamp: ts,
        type: "requestStart"
      });
    }
    endpoints.sort((a, b) => a.timestamp - b.timestamp);
    let currentActive = incompleteRequestStarts.length;
    for (let i = endpoints.length - 1; i >= 0; i--) {
      const endpoint = endpoints[i];
      switch (endpoint.type) {
        case "requestStart":
          currentActive--;
          break;
        case "requestEnd":
          currentActive++;
          if (currentActive > 2) {
            return endpoint.timestamp;
          }
          break;
        default:
          throw Error("Internal Error: This should never happen");
      }
    }
    return 0;
  };

  // node_modules/time-to-interactive-polyfill/src/firstConsistentlyInteractiveDetector.js
  var FirstConsistentlyInteractiveDetector = class {
    /**
     * @param {!FirstConsistentlyInteractiveDetectorInit=} config
     */
    constructor(config = {}) {
      this._useMutationObserver = !!config.useMutationObserver;
      this._minValue = config.minValue || null;
      const snippetEntries = window.__tti && window.__tti.e;
      const snippetObserver = window.__tti && window.__tti.o;
      if (snippetEntries) {
        log(`Consuming the long task entries already recorded.`);
        this._longTasks = snippetEntries.map((performanceEntry) => {
          return {
            start: performanceEntry.startTime,
            end: performanceEntry.startTime + performanceEntry.duration
          };
        });
      } else {
        this._longTasks = [];
      }
      if (snippetObserver) {
        snippetObserver.disconnect();
      }
      this._networkRequests = [];
      this._incompleteJSInitiatedRequestStartTimes = /* @__PURE__ */ new Map();
      this._timerId = null;
      this._timerActivationTime = -Infinity;
      this._scheduleTimerTasks = false;
      this._firstConsistentlyInteractiveResolver = null;
      this._performanceObserver = null;
      this._mutationObserver = null;
      this._registerListeners();
    }
    /**
     * Starts checking for a first consistently interactive time and returns a
     * promise that resolves to the found time.
     * @return {!Promise<number>}
     */
    getFirstConsistentlyInteractive() {
      return new Promise((resolve, reject) => {
        this._firstConsistentlyInteractiveResolver = resolve;
        if (document.readyState == "complete") {
          this.startSchedulingTimerTasks();
        } else {
          window.addEventListener("load", () => {
            this.startSchedulingTimerTasks();
          });
        }
      });
    }
    /**
     * Starts scheduling the timer that checks for network quiescence (a 5-second
     * window of no more than 2 in-flight network requests).
     */
    startSchedulingTimerTasks() {
      log(`Enabling FirstConsistentlyInteractiveDetector`);
      this._scheduleTimerTasks = true;
      const lastLongTaskEnd = this._longTasks.length > 0 ? this._longTasks[this._longTasks.length - 1].end : 0;
      const lastKnownNetwork2Busy = computeLastKnownNetwork2Busy(
        this._incompleteRequestStarts,
        this._networkRequests
      );
      this.rescheduleTimer(
        Math.max(lastKnownNetwork2Busy + 5e3, lastLongTaskEnd)
      );
    }
    /**
     * Setter for the `_minValue` property.
     * @param {number} minValue
     */
    setMinValue(minValue) {
      this._minValue = minValue;
    }
    /**
     * Resets the timer that checks for network quiescence.
     * @param {number} earliestTime A timestamp in ms, and the time is relative
     *     to navigationStart.
     */
    rescheduleTimer(earliestTime) {
      if (!this._scheduleTimerTasks) {
        log(`startSchedulingTimerTasks must be called before calling rescheduleTimer`);
        return;
      }
      log(`Attempting to reschedule FirstConsistentlyInteractive check to ${earliestTime}`);
      log(`Previous timer activation time: ${this._timerActivationTime}`);
      if (this._timerActivationTime > earliestTime) {
        log(`Current activation time is greater than attempted reschedule time. No need to postpone.`);
        return;
      }
      clearTimeout(this._timerId);
      this._timerId = setTimeout(() => {
        this._checkTTI();
      }, earliestTime - performance.now());
      this._timerActivationTime = earliestTime;
      log(`Rescheduled firstConsistentlyInteractive check at ${earliestTime}`);
    }
    /**
     * Removes all timers and event listeners.
     */
    disable() {
      log(`Disabling FirstConsistentlyInteractiveDetector`);
      clearTimeout(this._timerId);
      this._scheduleTimerTasks = false;
      this._unregisterListeners();
    }
    /**
     * Adds
     */
    _registerPerformanceObserver() {
      this._performanceObserver = new PerformanceObserver((entryList) => {
        const entries = entryList.getEntries();
        for (const entry of entries) {
          if (entry.entryType === "resource") {
            this._networkRequestFinishedCallback(entry);
          }
          if (entry.entryType === "longtask") {
            this._longTaskFinishedCallback(entry);
          }
        }
      });
      this._performanceObserver.observe({ type: "longtask", buffered: true });
      this._performanceObserver.observe({ type: "resource", buffered: true });
    }
    /**
     * Registers listeners to detect XHR, fetch, resource timing entries, and
     * DOM mutations to detect long tasks and network quiescence.
     */
    _registerListeners() {
      patchXMLHTTPRequest(
        this._beforeJSInitiatedRequestCallback.bind(this),
        this._afterJSInitiatedRequestCallback.bind(this)
      );
      patchFetch(
        this._beforeJSInitiatedRequestCallback.bind(this),
        this._afterJSInitiatedRequestCallback.bind(this)
      );
      this._registerPerformanceObserver();
      if (this._useMutationObserver) {
        this._mutationObserver = observeResourceFetchingMutations(
          this._mutationObserverCallback.bind(this)
        );
      }
    }
    /**
     * Removes all added listeners.
     */
    _unregisterListeners() {
      if (this._performanceObserver)
        this._performanceObserver.disconnect();
      if (this._mutationObserver)
        this._mutationObserver.disconnect();
    }
    /**
     * A callback to be run before any new XHR requests. This adds the request
     * to a map so in-flight requests can be tracked.
     * @param {string} requestId
     */
    _beforeJSInitiatedRequestCallback(requestId) {
      log(`Starting JS initiated request. Request ID: ${requestId}`);
      this._incompleteJSInitiatedRequestStartTimes.set(
        requestId,
        performance.now()
      );
      log(`Active XHRs: ${this._incompleteJSInitiatedRequestStartTimes.size}`);
    }
    /**
     * A callback to be run once any XHR requests have completed. This removes
     * the request from the in-flight request map.
     * @param {string} requestId
     */
    _afterJSInitiatedRequestCallback(requestId) {
      log(`Completed JS initiated request with request ID: ${requestId}`);
      this._incompleteJSInitiatedRequestStartTimes.delete(requestId);
      log(`Active XHRs: ${this._incompleteJSInitiatedRequestStartTimes.size}`);
    }
    /**
     * A callback to be run once new resource timing entries are observed.
     * This adds the entry to an array and resets the timeout detecting the
     * quiet window.
     * @param {PerformanceEntry} performanceEntry
     */
    _networkRequestFinishedCallback(performanceEntry) {
      log(`Network request finished`, performanceEntry);
      this._networkRequests.push({
        start: performanceEntry.fetchStart,
        end: performanceEntry.responseEnd
      });
      this.rescheduleTimer(
        computeLastKnownNetwork2Busy(
          this._incompleteRequestStarts,
          this._networkRequests
        ) + 5e3
      );
    }
    /**
     * A callback to be run once new long tasks are observed. This resets the
     * timeout detecting the quiet window.
     * @param {PerformanceEntry} performanceEntry
     */
    _longTaskFinishedCallback(performanceEntry) {
      log(`Long task finished`, performanceEntry);
      const taskEndTime = performanceEntry.startTime + performanceEntry.duration;
      this._longTasks.push({
        start: performanceEntry.startTime,
        end: taskEndTime
      });
      this.rescheduleTimer(taskEndTime + 5e3);
    }
    /**
     * A callback to be run once any DOM elements are added that would initiate
     * a new network request. This resets the timeout detecting the quiet window.
     * @param {!MutationRecord} mutationRecord
     */
    _mutationObserverCallback(mutationRecord) {
      log(
        `Potentially network resource fetching mutation detected`,
        mutationRecord
      );
      log(`Pushing back FirstConsistentlyInteractive check by 5 seconds.`);
      this.rescheduleTimer(performance.now() + 5e3);
    }
    /**
     * Returns either a manually set min value or the time since
     * domContentLoadedEventEnd and navigationStart. If the
     * domContentLoadedEventEnd data isn't available, `null` is returned.
     * @return {number|null}
     */
    _getMinValue() {
      if (this._minValue)
        return this._minValue;
      if (performance.timing.domContentLoadedEventEnd) {
        const { domContentLoadedEventEnd, navigationStart } = performance.timing;
        return domContentLoadedEventEnd - navigationStart;
      }
      return null;
    }
    /**
     * Gets a list of all in-flight requests.
     * @return {!Array}
     */
    get _incompleteRequestStarts() {
      return [...this._incompleteJSInitiatedRequestStartTimes.values()];
    }
    /**
     * Checks to see if a first consistently interactive time has been found.
     * If one has been found, the promise resolver is invoked with the time. If
     * no time has been found, the timeout detecting the quiet window is reset.
     */
    _checkTTI() {
      log(`Checking if First Consistently Interactive was reached...`);
      const navigationStart = performance.timing.navigationStart;
      const lastBusy = computeLastKnownNetwork2Busy(
        this._incompleteRequestStarts,
        this._networkRequests
      );
      const fcpEntry = window.performance.getEntriesByName(
        "first-contentful-paint"
      )[0];
      const fcp = fcpEntry ? fcpEntry.startTime : void 0;
      const searchStart = fcp || performance.timing.domContentLoadedEventEnd - navigationStart;
      const minValue = this._getMinValue();
      const currentTime = performance.now();
      if (minValue === null) {
        log(`No usable minimum value yet. Postponing check.`);
        this.rescheduleTimer(Math.max(lastBusy + 5e3, currentTime + 1e3));
      }
      log(`Parameter values:`);
      log(`NavigationStart`, navigationStart);
      log(`lastKnownNetwork2Busy`, lastBusy);
      log(`Search Start`, searchStart);
      log(`Min Value`, minValue);
      log(`Last busy`, lastBusy);
      log(`Current time`, currentTime);
      log(`Long tasks`, this._longTasks);
      log(`Incomplete JS Request Start Times`, this._incompleteRequestStarts);
      log(`Network requests`, this._networkRequests);
      const maybeFCI = computeFirstConsistentlyInteractive(
        searchStart,
        /** @type {number} */
        minValue,
        lastBusy,
        currentTime,
        this._longTasks
      );
      if (maybeFCI) {
        this._firstConsistentlyInteractiveResolver(
          /** @type {number} */
          maybeFCI
        );
        this.disable();
      }
      log(`Could not detect First Consistently Interactive. Retrying in 1 second.`);
      this.rescheduleTimer(performance.now() + 1e3);
    }
  };

  // node_modules/time-to-interactive-polyfill/src/index.js
  var getFirstConsistentlyInteractive = (opts = {}) => {
    if ("PerformanceLongTaskTiming" in window) {
      const detector = new FirstConsistentlyInteractiveDetector(opts);
      return detector.getFirstConsistentlyInteractive();
    } else {
      return Promise.resolve(null);
    }
  };

  // lib/web.ts
  function filterCMPs(rules, config) {
    return rules.filter((cmp) => {
      return (!config.disabledCmps || !config.disabledCmps.includes(cmp.name)) && (config.enableCosmeticRules || !cmp.isCosmetic);
    });
  }
  var AutoConsent = class {
    constructor(sendContentMessage, config = null, declarativeRules = null) {
      this.id = getRandomID();
      this.rules = [];
      this.foundCmp = null;
      this.state = {
        cosmeticFiltersOn: false,
        lifecycle: "loading",
        prehideOn: false,
        findCmpAttempts: 0,
        detectedCmps: [],
        detectedPopups: [],
        selfTest: null
      };
      performance.mark("autoconsent-constructor");
      evalState.sendContentMessage = sendContentMessage;
      this.sendContentMessage = sendContentMessage;
      this.rules = [];
      this.updateState({ lifecycle: "loading" });
      this.addDynamicRules();
      if (config) {
        this.initialize(config, declarativeRules);
      } else {
        if (declarativeRules) {
          this.parseDeclarativeRules(declarativeRules);
        }
        const initMsg = {
          type: "init",
          url: window.location.href
        };
        sendContentMessage(initMsg);
        this.updateState({ lifecycle: "waitingForInitResponse" });
      }
      this.domActions = new DomActions(this);
    }
    initialize(config, declarativeRules) {
      performance.mark("autoconsent-initialize");
      console.log("init called with", JSON.stringify(config), declarativeRules?.filterList?.substring(0, 100));
      const normalizedConfig = normalizeConfig(config);
      normalizedConfig.logs.lifecycle && console.log("autoconsent init", window.location.href);
      this.config = normalizedConfig;
      if (!normalizedConfig.enabled) {
        normalizedConfig.logs.lifecycle && console.log("autoconsent is disabled");
        return;
      }
      if (declarativeRules) {
        this.parseDeclarativeRules(declarativeRules);
      }
      this.rules = filterCMPs(this.rules, normalizedConfig);
      if (config.enablePrehide) {
        if (document.documentElement) {
          this.prehideElements();
        } else {
          const delayedPrehide = () => {
            window.removeEventListener("DOMContentLoaded", delayedPrehide);
            this.prehideElements();
          };
          window.addEventListener("DOMContentLoaded", delayedPrehide);
        }
      }
      if (document.readyState === "loading") {
        const onReady = () => {
          window.removeEventListener("DOMContentLoaded", onReady);
          this.start();
        };
        window.addEventListener("DOMContentLoaded", onReady);
      } else {
        this.start();
      }
      this.updateState({ lifecycle: "initialized" });
    }
    addDynamicRules() {
      dynamicCMPs.forEach((cmp) => {
        this.rules.push(new cmp(this));
      });
    }
    parseDeclarativeRules(declarativeRules) {
      if (declarativeRules.consentomatic) {
        Object.keys(declarativeRules.consentomatic).forEach((name) => {
          this.addConsentomaticCMP(name, declarativeRules.consentomatic[name]);
        });
      }
      if (declarativeRules.autoconsent) {
        declarativeRules.autoconsent.forEach((ruleset) => {
          this.addDeclarativeCMP(ruleset);
        });
      }
      if (declarativeRules.filterList) {
        performance.mark("autoconsent-parse-start");
        this.filtersEngine = parseFilterList(declarativeRules.filterList);
        performance.mark("autoconsent-parse-end");
        if (document.readyState === "loading") {
          window.addEventListener("DOMContentLoaded", () => {
            performance.mark("autoconsent-apply-filterlist-start");
            this.applyCosmeticFilters(false);
            performance.mark("autoconsent-apply-filterlist-end");
          });
        } else {
          performance.mark("autoconsent-apply-filterlist-start");
          this.applyCosmeticFilters(false);
          performance.mark("autoconsent-apply-filterlist-end");
        }
      }
    }
    addDeclarativeCMP(ruleset) {
      this.rules.push(new AutoConsentCMP(ruleset, this));
    }
    addConsentomaticCMP(name, config) {
      this.rules.push(new ConsentOMaticCMP(`com_${name}`, config));
    }
    // start the detection process, possibly with a delay
    start() {
      if (window.requestIdleCallback) {
        window.requestIdleCallback(() => this._start(), { timeout: 500 });
      } else {
        this._start();
      }
    }
    async _start() {
      performance.mark("autoconsent-start");
      const logsConfig = this.config.logs;
      logsConfig.lifecycle && console.log(`Detecting CMPs on ${window.location.href}`);
      this.updateState({ lifecycle: "started" });
      const foundCmps = await this.findCmp(this.config.detectRetries);
      this.updateState({ detectedCmps: foundCmps.map((c) => c.name) });
      if (foundCmps.length === 0) {
        logsConfig.lifecycle && console.log("no CMP found", location.href);
        if (this.config.enablePrehide) {
          this.undoPrehide();
        }
        return this.filterListFallback();
      }
      this.updateState({ lifecycle: "cmpDetected" });
      const staticCmps = [];
      const cosmeticCmps = [];
      for (const cmp of foundCmps) {
        if (cmp.isCosmetic) {
          cosmeticCmps.push(cmp);
        } else {
          staticCmps.push(cmp);
        }
      }
      let result = false;
      let foundPopups = await this.detectPopups(staticCmps, async (cmp) => {
        result = await this.handlePopup(cmp);
      });
      if (foundPopups.length === 0) {
        foundPopups = await this.detectPopups(cosmeticCmps, async (cmp) => {
          result = await this.handlePopup(cmp);
        });
      }
      if (foundPopups.length === 0) {
        logsConfig.lifecycle && console.log("no popup found");
        if (this.config.enablePrehide) {
          this.undoPrehide();
        }
        return false;
      }
      if (foundPopups.length > 1) {
        const errorDetails = {
          msg: `Found multiple CMPs, check the detection rules.`,
          cmps: foundPopups.map((cmp) => cmp.name)
        };
        logsConfig.errors && console.warn(errorDetails.msg, errorDetails.cmps);
        this.sendContentMessage({
          type: "autoconsentError",
          details: errorDetails
        });
      }
      return result;
    }
    async findCmp(retries) {
      const logsConfig = this.config.logs;
      this.updateState({ findCmpAttempts: this.state.findCmpAttempts + 1 });
      const foundCMPs = [];
      for (const cmp of this.rules) {
        try {
          if (!cmp.checkRunContext()) {
            continue;
          }
          const result = await cmp.detectCmp();
          if (result) {
            logsConfig.lifecycle && console.log(`Found CMP: ${cmp.name} ${window.location.href}`);
            this.sendContentMessage({
              type: "cmpDetected",
              url: location.href,
              cmp: cmp.name
            });
            foundCMPs.push(cmp);
          }
        } catch (e) {
          logsConfig.errors && console.warn(`error detecting ${cmp.name}`, e);
        }
      }
      if (foundCMPs.length === 0 && retries > 0) {
        await this.domActions.wait(500);
        return this.findCmp(retries - 1);
      }
      return foundCMPs;
    }
    /**
     * Detect if a CMP has a popup open. Fullfils with the CMP if a popup is open, otherwise rejects.
     */
    async detectPopup(cmp) {
      const isOpen = await this.waitForPopup(cmp).catch((error2) => {
        this.config.logs.errors && console.warn(`error waiting for a popup for ${cmp.name}`, error2);
        return false;
      });
      if (isOpen) {
        this.updateState({ detectedPopups: this.state.detectedPopups.concat([cmp.name]) });
        this.sendContentMessage({
          type: "popupFound",
          cmp: cmp.name,
          url: location.href
        });
        return cmp;
      }
      throw new Error("Popup is not shown");
    }
    /**
     * Detect if any of the CMPs has a popup open. Returns a list of CMPs with open popups.
     */
    async detectPopups(cmps, onFirstPopupAppears) {
      const tasks = cmps.map(
        (cmp) => this.detectPopup(cmp)
      );
      await Promise.any(tasks).then((cmp) => {
        onFirstPopupAppears(cmp);
      }).catch(() => null);
      const results = await Promise.allSettled(tasks);
      const popups = [];
      for (const result of results) {
        if (result.status === "fulfilled") {
          popups.push(result.value);
        }
      }
      return popups;
    }
    async handlePopup(cmp) {
      this.updateState({ lifecycle: "openPopupDetected" });
      if (this.config.enablePrehide && !this.state.prehideOn) {
        this.prehideElements();
      }
      if (this.state.cosmeticFiltersOn) {
        this.undoCosmetics();
      }
      this.foundCmp = cmp;
      if (this.config.autoAction === "optOut") {
        return await this.doOptOut();
      } else if (this.config.autoAction === "optIn") {
        return await this.doOptIn();
      } else {
        this.config.logs.lifecycle && console.log("waiting for opt-out signal...", location.href);
        return true;
      }
    }
    async doOptOut() {
      const logsConfig = this.config.logs;
      this.updateState({ lifecycle: "runningOptOut" });
      let optOutResult;
      if (!this.foundCmp) {
        logsConfig.errors && console.log("no CMP to opt out");
        optOutResult = false;
      } else {
        logsConfig.lifecycle && console.log(`CMP ${this.foundCmp.name}: opt out on ${window.location.href}`);
        optOutResult = await this.foundCmp.optOut();
        logsConfig.lifecycle && console.log(`${this.foundCmp.name}: opt out result ${optOutResult}`);
      }
      if (this.config.enablePrehide) {
        this.undoPrehide();
      }
      this.sendContentMessage({
        type: "optOutResult",
        cmp: this.foundCmp ? this.foundCmp.name : "none",
        result: optOutResult,
        scheduleSelfTest: this.foundCmp && this.foundCmp.hasSelfTest,
        url: location.href
      });
      if (optOutResult && !this.foundCmp.isIntermediate) {
        this.sendContentMessage({
          type: "autoconsentDone",
          cmp: this.foundCmp.name,
          isCosmetic: this.foundCmp.isCosmetic,
          url: location.href
        });
        this.updateState({ lifecycle: "done" });
      } else {
        this.updateState({ lifecycle: optOutResult ? "optOutSucceeded" : "optOutFailed" });
      }
      return optOutResult;
    }
    async doOptIn() {
      const logsConfig = this.config.logs;
      this.updateState({ lifecycle: "runningOptIn" });
      let optInResult;
      if (!this.foundCmp) {
        logsConfig.errors && console.log("no CMP to opt in");
        optInResult = false;
      } else {
        logsConfig.lifecycle && console.log(`CMP ${this.foundCmp.name}: opt in on ${window.location.href}`);
        optInResult = await this.foundCmp.optIn();
        logsConfig.lifecycle && console.log(`${this.foundCmp.name}: opt in result ${optInResult}`);
      }
      if (this.config.enablePrehide) {
        this.undoPrehide();
      }
      this.sendContentMessage({
        type: "optInResult",
        cmp: this.foundCmp ? this.foundCmp.name : "none",
        result: optInResult,
        scheduleSelfTest: false,
        // self-tests are only for opt-out at the moment
        url: location.href
      });
      if (optInResult && !this.foundCmp.isIntermediate) {
        this.sendContentMessage({
          type: "autoconsentDone",
          cmp: this.foundCmp.name,
          isCosmetic: this.foundCmp.isCosmetic,
          url: location.href
        });
        this.updateState({ lifecycle: "done" });
      } else {
        this.updateState({ lifecycle: optInResult ? "optInSucceeded" : "optInFailed" });
      }
      return optInResult;
    }
    async doSelfTest() {
      const logsConfig = this.config.logs;
      let selfTestResult;
      if (!this.foundCmp) {
        logsConfig.errors && console.log("no CMP to self test");
        selfTestResult = false;
      } else {
        logsConfig.lifecycle && console.log(`CMP ${this.foundCmp.name}: self-test on ${window.location.href}`);
        selfTestResult = await this.foundCmp.test();
      }
      this.sendContentMessage({
        type: "selfTestResult",
        cmp: this.foundCmp ? this.foundCmp.name : "none",
        result: selfTestResult,
        url: location.href
      });
      this.updateState({ selfTest: selfTestResult });
      return selfTestResult;
    }
    async waitForPopup(cmp, retries = 5, interval = 500) {
      const logsConfig = this.config.logs;
      logsConfig.lifecycle && console.log("checking if popup is open...", cmp.name);
      const isOpen = await cmp.detectPopup().catch((e) => {
        logsConfig.errors && console.warn(`error detecting popup for ${cmp.name}`, e);
        return false;
      });
      if (!isOpen && retries > 0) {
        await this.domActions.wait(interval);
        return this.waitForPopup(cmp, retries - 1, interval);
      }
      logsConfig.lifecycle && console.log(cmp.name, `popup is ${isOpen ? "open" : "not open"}`);
      return isOpen;
    }
    prehideElements() {
      const logsConfig = this.config.logs;
      const globalHidden = [
        "#didomi-popup,.didomi-popup-container,.didomi-popup-notice,.didomi-consent-popup-preferences,#didomi-notice,.didomi-popup-backdrop,.didomi-screen-medium"
      ];
      const selectors = this.rules.filter((rule) => rule.prehideSelectors && rule.checkRunContext()).reduce((selectorList, rule) => [...selectorList, ...rule.prehideSelectors], globalHidden);
      this.updateState({ prehideOn: true });
      setTimeout(() => {
        if (this.config.enablePrehide && this.state.prehideOn && !["runningOptOut", "runningOptIn"].includes(this.state.lifecycle)) {
          logsConfig.lifecycle && console.log("Process is taking too long, unhiding elements");
          this.undoPrehide();
        }
      }, this.config.prehideTimeout || 2e3);
      return this.domActions.prehide(selectors.join(","));
    }
    undoPrehide() {
      this.updateState({ prehideOn: false });
      return this.domActions.undoPrehide();
    }
    /**
     * Apply cosmetic filters
     * @param verify if true, will check if the filters are actually hiding something
     * @returns true if the cosmetic filters are actually hiding something (only when verify is set).
     */
    applyCosmeticFilters(verify) {
      if (!this.filtersEngine) {
        return false;
      }
      this.updateState({ cosmeticFiltersOn: true });
      const selectors = getFilterlistSelectors(this.filtersEngine);
      if (verify) {
        return this.domActions.elementVisible(selectors, "any");
      }
      this.domActions.applyCosmetics(selectors);
      return false;
    }
    undoCosmetics() {
      this.updateState({ cosmeticFiltersOn: false });
      this.domActions.undoCosmetics();
    }
    filterListFallback() {
      const logsConfig = this.config.logs;
      const cosmeticFiltersWorked = this.applyCosmeticFilters(true);
      if (!cosmeticFiltersWorked) {
        logsConfig.lifecycle && console.log("Cosmetic filters didn't work, removing them", location.href);
        this.undoCosmetics();
        this.updateState({ lifecycle: "nothingDetected" });
        return false;
      } else {
        logsConfig.lifecycle && console.log("Keeping cosmetic filters", location.href);
        this.updateState({ lifecycle: "cosmeticFiltersDetected" });
        this.sendContentMessage({
          type: "cmpDetected",
          url: location.href,
          cmp: "filterList"
        });
        this.sendContentMessage({
          type: "popupFound",
          cmp: "filterList",
          url: location.href
        });
        this.sendContentMessage({
          type: "optOutResult",
          cmp: "filterList",
          result: true,
          scheduleSelfTest: false,
          url: location.href
        });
        this.updateState({ lifecycle: "done" });
        this.sendContentMessage({
          type: "autoconsentDone",
          cmp: "filterList",
          isCosmetic: true,
          url: location.href
        });
        return true;
      }
    }
    updateState(change) {
      Object.assign(this.state, change);
      this.sendContentMessage({
        type: "report",
        instanceId: this.id,
        url: window.location.href,
        mainFrame: window.top === window.self,
        state: this.state
      });
    }
    async receiveMessageCallback(message) {
      const logsConfig = this.config?.logs;
      if (logsConfig?.messages) {
        console.log("received from background", message, window.location.href);
      }
      switch (message.type) {
        case "initResp":
          this.initialize(message.config, message.rules);
          break;
        case "optIn":
          await this.doOptIn();
          break;
        case "optOut":
          await this.doOptOut();
          break;
        case "selfTest":
          await this.doSelfTest();
          break;
        case "evalResp":
          resolveEval(message.id, message.result);
          break;
      }
    }
  };
  var BLOCKING_TIME_THRESHOLD = 50;
  function calcTBT(tti, longTasks = [], fcp) {
    if (tti <= fcp)
      return 0;
    return longTasks.reduce((memo, curr) => {
      if (curr.startTime < fcp && curr.startTime + curr.duration >= fcp) {
        const afterFCPDuration = curr.duration - (fcp - curr.startTime);
        if (afterFCPDuration >= BLOCKING_TIME_THRESHOLD) {
          memo += afterFCPDuration - BLOCKING_TIME_THRESHOLD;
        }
        return memo;
      }
      if (curr.startTime < tti && curr.startTime + curr.duration > tti && tti - curr.startTime >= BLOCKING_TIME_THRESHOLD) {
        memo += tti - curr.startTime - BLOCKING_TIME_THRESHOLD;
        return memo;
      }
      if (curr.startTime < fcp || curr.startTime > tti || curr.duration <= BLOCKING_TIME_THRESHOLD) {
        return memo;
      }
      memo += curr.duration - BLOCKING_TIME_THRESHOLD;
      return memo;
    }, 0);
  }
  async function collectMetrics() {
    const longTasks = [];
    const longTaskObserver = new PerformanceObserver((list) => {
      list.getEntries().forEach((entry) => {
        longTasks.push(entry);
      });
    });
    longTaskObserver.observe({ type: "longtask", buffered: true });
    let lcpCandidate = 0;
    const lcpObserver = new PerformanceObserver((entryList) => {
      const entries = entryList.getEntries();
      lcpCandidate = entries[entries.length - 1].startTime;
    });
    lcpObserver.observe({ type: "largest-contentful-paint", buffered: true });
    const tti = await getFirstConsistentlyInteractive({ useMutationObserver: false });
    longTaskObserver.disconnect();
    const fcp = performance.getEntriesByName("first-contentful-paint")[0]?.startTime ?? 0;
    const tbt = calcTBT(tti, longTasks, fcp);
    lcpObserver.disconnect();
    let navigationEntry;
    if (document.readyState === "complete") {
      navigationEntry = performance.getEntriesByType("navigation")[0];
    } else {
      navigationEntry = await new Promise((resolve) => {
        const observer = new PerformanceObserver((list) => {
          const navigationEntry2 = list.getEntries()[0];
          if (navigationEntry2) {
            resolve(navigationEntry2);
            observer.disconnect();
          }
        });
        observer.observe({ type: "navigation", buffered: true });
      });
    }
    const cpmInit = await new Promise((resolve) => {
      const observer = new PerformanceObserver((list) => {
        const entries = list.getEntries();
        const cpmStart = entries.find((entry) => entry.name === "autoconsent-start");
        if (cpmStart) {
          observer.disconnect();
          resolve(performance.measure("cpmInit", "autoconsent-constructor", "autoconsent-start"));
        }
      });
      observer.observe({ type: "mark", buffered: true });
    });
    const parseMark = performance.getEntriesByName("autoconsent-parse-end");
    const cpmParseDuration = parseMark.length > 0 ? performance.measure("cpmParseFilterlist", "autoconsent-parse-start", "autoconsent-parse-end").duration : 0;
    const applyMark = performance.getEntriesByName("autoconsent-apply-filterlist-end");
    const cpmApplyDuration = applyMark.length > 0 ? performance.measure("cpmApplyFilterlist", "autoconsent-apply-filterlist-start", "autoconsent-apply-filterlist-end").duration : 0;
    return {
      tbt: Math.round(tbt),
      tti: Math.round(tti),
      lcp: Math.round(lcpCandidate),
      longTasks: longTasks.length,
      navDuration: Math.round(navigationEntry.duration),
      domReady: Math.round(navigationEntry.domComplete - navigationEntry.domInteractive),
      cpmInit: Math.round(cpmInit.duration),
      cpmParseList: Math.round(cpmParseDuration),
      cpmApplyList: Math.round(cpmApplyDuration)
    };
  }
  /*! Bundled license information:

  @cliqz/adblocker/dist/es6/src/codebooks/cosmetic-selector.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/codebooks/network-csp.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/codebooks/network-filter.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/codebooks/network-hostname.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/codebooks/network-redirect.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/codebooks/raw-network.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/codebooks/raw-cosmetic.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/compression.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/punycode.js:
    (*!
     * Copyright Mathias Bynens <https://mathiasbynens.be/>
     *
     * Permission is hereby granted, free of charge, to any person obtaining
     * a copy of this software and associated documentation files (the
     * "Software"), to deal in the Software without restriction, including
     * without limitation the rights to use, copy, modify, merge, publish,
     * distribute, sublicense, and/or sell copies of the Software, and to
     * permit persons to whom the Software is furnished to do so, subject to
     * the following conditions:
     *
     * The above copyright notice and this permission notice shall be
     * included in all copies or substantial portions of the Software.
     *
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
     * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
     * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
     * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
     * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
     * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
     * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
     *)

  @cliqz/adblocker/dist/es6/src/data-view.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/config.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/events.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/fetch.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/filters/dsl.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker-extended-selectors/dist/es6/src/types.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker-extended-selectors/dist/es6/src/parse.js:
    (*!
     * Based on parsel. Extended by Rémi Berson for Ghostery (2021).
     * https://github.com/LeaVerou/parsel
     *
     * MIT License
     *
     * Copyright (c) 2020 Lea Verou
     *
     * Permission is hereby granted, free of charge, to any person obtaining a copy
     * of this software and associated documentation files (the "Software"), to deal
     * in the Software without restriction, including without limitation the rights
     * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
     * copies of the Software, and to permit persons to whom the Software is
     * furnished to do so, subject to the following conditions:
     *
     * The above copyright notice and this permission notice shall be included in all
     * copies or substantial portions of the Software.
     *
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
     * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
     * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
     * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
     * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
     * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
     * SOFTWARE.
     *)

  @cliqz/adblocker-extended-selectors/dist/es6/src/eval.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker-extended-selectors/dist/es6/src/extended.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker-extended-selectors/dist/es6/adblocker.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/tokens-buffer.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/utils.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/request.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/engine/domains.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/html-filtering.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/filters/cosmetic.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/filters/network.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/lists.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/resources.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/compact-set.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/engine/optimizer.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/engine/reverse-index.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/engine/bucket/filters.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/engine/bucket/cosmetic.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/engine/bucket/network.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/engine/map.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/engine/metadata/categories.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/engine/metadata/organizations.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/engine/metadata/patterns.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/engine/metadata.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/engine/engine.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker/dist/es6/src/encoding.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)
    (*!
     * Copyright (c) 2008-2009 Bjoern Hoehrmann <bjoern@hoehrmann.de>
     *
     * Permission is hereby granted, free of charge, to any person obtaining a copy
     * of this software and associated documentation files (the "Software"), to
     * deal in the Software without restriction, including without limitation the
     * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
     * sell copies of the Software, and to permit persons to whom the Software is
     * furnished to do so, subject to the following conditions:
     *
     * The above copyright notice and this permission notice shall be included in
     * all copies or substantial portions of the Software.
     *
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
     * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
     * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
     * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
     * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
     * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
     * IN THE SOFTWARE.
     *)

  @cliqz/adblocker/dist/es6/adblocker.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)

  @cliqz/adblocker-content/dist/es6/adblocker.js:
    (*!
     * Copyright (c) 2017-present Cliqz GmbH. All rights reserved.
     *
     * This Source Code Form is subject to the terms of the Mozilla Public
     * License, v. 2.0. If a copy of the MPL was not distributed with this
     * file, You can obtain one at https://mozilla.org/MPL/2.0/.
     *)
  */

  var autoconsent$1 = [
  	{
  		name: "192.com",
  		detectCmp: [
  			{
  				exists: ".ont-cookies"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".ont-cookies"
  			}
  		],
  		optIn: [
  			{
  				click: ".ont-btn-main.ont-cookies-btn.js-ont-btn-ok2"
  			}
  		],
  		optOut: [
  			{
  				click: ".ont-cookes-btn-manage"
  			},
  			{
  				click: ".ont-btn-main.ont-cookies-btn.js-ont-btn-choose"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_ONENINETWO_0"
  			}
  		]
  	},
  	{
  		name: "1password-com",
  		cosmetic: true,
  		prehideSelectors: [
  			"footer #footer-root [aria-label=\"Cookie Consent\"]"
  		],
  		detectCmp: [
  			{
  				exists: "footer #footer-root [aria-label=\"Cookie Consent\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "footer #footer-root [aria-label=\"Cookie Consent\"]"
  			}
  		],
  		optIn: [
  			{
  				click: "footer #footer-root [aria-label=\"Cookie Consent\"] button"
  			}
  		],
  		optOut: [
  			{
  				hide: "footer #footer-root [aria-label=\"Cookie Consent\"]"
  			}
  		]
  	},
  	{
  		name: "abconcerts.be",
  		vendorUrl: "https://unknown",
  		intermediate: false,
  		prehideSelectors: [
  			"dialog.cookie-consent"
  		],
  		detectCmp: [
  			{
  				exists: "dialog.cookie-consent form.cookie-consent__form"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "dialog.cookie-consent form.cookie-consent__form"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "dialog.cookie-consent form.cookie-consent__form button[value=yes]"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "dialog.cookie-consent form.cookie-consent__form button[value=no]"
  				},
  				then: [
  					{
  						click: "dialog.cookie-consent form.cookie-consent__form button[value=no]"
  					}
  				],
  				"else": [
  					{
  						click: "dialog.cookie-consent form.cookie-consent__form button.cookie-consent__options-toggle"
  					},
  					{
  						waitForThenClick: "dialog.cookie-consent form.cookie-consent__form button[value=\"save_options\"]"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "activobank.pt",
  		runContext: {
  			urlPattern: "^https://(www\\.)?activobank\\.pt"
  		},
  		prehideSelectors: [
  			"aside#cookies,.overlay-cookies"
  		],
  		detectCmp: [
  			{
  				exists: "#cookies .cookies-btn"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookies #submitCookies"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#cookies #submitCookies"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#cookies #rejectCookies"
  			}
  		]
  	},
  	{
  		name: "Adroll",
  		prehideSelectors: [
  			"#adroll_consent_container"
  		],
  		detectCmp: [
  			{
  				exists: "#adroll_consent_container"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#adroll_consent_container"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#adroll_consent_accept"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#adroll_consent_reject"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_ADROLL_0"
  			}
  		]
  	},
  	{
  		name: "affinity.serif.com",
  		detectCmp: [
  			{
  				exists: ".c-cookie-banner button[data-qa='allow-all-cookies']"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".c-cookie-banner"
  			}
  		],
  		optIn: [
  			{
  				click: "button[data-qa=\"allow-all-cookies\"]"
  			}
  		],
  		optOut: [
  			{
  				click: "button[data-qa=\"manage-cookies\"]"
  			},
  			{
  				waitFor: ".c-cookie-banner ~ [role=\"dialog\"]"
  			},
  			{
  				waitForThenClick: ".c-cookie-banner ~ [role=\"dialog\"] input[type=\"checkbox\"][value=\"true\"]",
  				all: true
  			},
  			{
  				click: ".c-cookie-banner ~ [role=\"dialog\"] .c-modal__action button"
  			}
  		],
  		test: [
  			{
  				wait: 500
  			},
  			{
  				"eval": "EVAL_AFFINITY_SERIF_COM_0"
  			}
  		]
  	},
  	{
  		name: "agolde.com",
  		cosmetic: true,
  		prehideSelectors: [
  			"#modal-1 div[data-micromodal-close]"
  		],
  		detectCmp: [
  			{
  				exists: "#modal-1 div[aria-labelledby=modal-1-title]"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "#modal-1 div[data-micromodal-close]"
  			}
  		],
  		optIn: [
  			{
  				click: "button[aria-label=\"Close modal\"]"
  			}
  		],
  		optOut: [
  			{
  				hide: "#modal-1 div[data-micromodal-close]"
  			}
  		]
  	},
  	{
  		name: "aliexpress",
  		vendorUrl: "https://aliexpress.com/",
  		runContext: {
  			urlPattern: "^https://.*\\.aliexpress\\.com/"
  		},
  		prehideSelectors: [
  			"#gdpr-new-container"
  		],
  		detectCmp: [
  			{
  				exists: "#gdpr-new-container"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#gdpr-new-container"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#gdpr-new-container .btn-accept"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#gdpr-new-container .btn-more"
  			},
  			{
  				waitFor: "#gdpr-new-container .gdpr-dialog-switcher"
  			},
  			{
  				click: "#gdpr-new-container .switcher-on",
  				all: true,
  				optional: true
  			},
  			{
  				click: "#gdpr-new-container .btn-save"
  			}
  		]
  	},
  	{
  		name: "almacmp",
  		prehideSelectors: [
  			"#alma-cmpv2-container"
  		],
  		detectCmp: [
  			{
  				exists: "#alma-cmpv2-container"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#alma-cmpv2-container #almacmp-modal-layer1"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#alma-cmpv2-container #almacmp-modal-layer1 #almacmp-modalConfirmBtn"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#alma-cmpv2-container #almacmp-modal-layer1 #almacmp-modalSettingBtn"
  			},
  			{
  				waitFor: "#alma-cmpv2-container #almacmp-modal-layer2"
  			},
  			{
  				waitForThenClick: "#alma-cmpv2-container #almacmp-modal-layer2 #almacmp-reject-all-layer2"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_ALMACMP_0"
  			}
  		]
  	},
  	{
  		name: "altium.com",
  		cosmetic: true,
  		prehideSelectors: [
  			".altium-privacy-bar"
  		],
  		detectCmp: [
  			{
  				exists: ".altium-privacy-bar"
  			}
  		],
  		detectPopup: [
  			{
  				exists: ".altium-privacy-bar"
  			}
  		],
  		optIn: [
  			{
  				click: "a.altium-privacy-bar__btn"
  			}
  		],
  		optOut: [
  			{
  				hide: ".altium-privacy-bar"
  			}
  		]
  	},
  	{
  		name: "amazon.com",
  		prehideSelectors: [
  			"span[data-action=\"sp-cc\"][data-sp-cc*=\"rejectAllAction\"]"
  		],
  		detectCmp: [
  			{
  				exists: "span[data-action=\"sp-cc\"][data-sp-cc*=\"rejectAllAction\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "span[data-action=\"sp-cc\"][data-sp-cc*=\"rejectAllAction\"]"
  			}
  		],
  		optIn: [
  			{
  				waitForVisible: "#sp-cc-accept"
  			},
  			{
  				wait: 500
  			},
  			{
  				click: "#sp-cc-accept"
  			}
  		],
  		optOut: [
  			{
  				waitForVisible: "#sp-cc-rejectall-link"
  			},
  			{
  				wait: 500
  			},
  			{
  				click: "#sp-cc-rejectall-link"
  			}
  		]
  	},
  	{
  		name: "aquasana.com",
  		prehideSelectors: [
  			"#consent-tracking"
  		],
  		detectCmp: [
  			{
  				exists: "#consent-tracking"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "#consent-tracking"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#consent-tracking .affirm.btn"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "#consent-tracking .decline.btn"
  				},
  				then: [
  					{
  						click: "#consent-tracking .decline.btn"
  					}
  				],
  				"else": [
  					{
  						hide: "#consent-tracking"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "arbeitsagentur",
  		vendorUrl: "https://www.arbeitsagentur.de/",
  		prehideSelectors: [
  			".modal-open bahf-cookie-disclaimer-dpl3"
  		],
  		detectCmp: [
  			{
  				exists: "bahf-cookie-disclaimer-dpl3"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "bahf-cookie-disclaimer-dpl3"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: [
  					"bahf-cookie-disclaimer-dpl3",
  					"bahf-cd-modal-dpl3 .ba-btn-primary"
  				]
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: [
  					"bahf-cookie-disclaimer-dpl3",
  					"bahf-cd-modal-dpl3 .ba-btn-contrast"
  				]
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_ARBEITSAGENTUR_TEST"
  			}
  		]
  	},
  	{
  		name: "asus",
  		vendorUrl: "https://www.asus.com/",
  		runContext: {
  			urlPattern: "^https://www\\.asus\\.com/"
  		},
  		prehideSelectors: [
  			"#cookie-policy-info,#cookie-policy-info-bg"
  		],
  		detectCmp: [
  			{
  				exists: "#cookie-policy-info"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookie-policy-info"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#cookie-policy-info [data-agree=\"Accept Cookies\"]"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "#cookie-policy-info .btn-reject"
  				},
  				then: [
  					{
  						waitForThenClick: "#cookie-policy-info .btn-reject"
  					}
  				],
  				"else": [
  					{
  						waitForThenClick: "#cookie-policy-info .btn-setting"
  					},
  					{
  						waitForThenClick: "#cookie-policy-lightbox-wrapper [data-agree=\"Save Settings\"]"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "athlinks-com",
  		runContext: {
  			urlPattern: "^https://(www\\.)?athlinks\\.com/"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			"#footer-container ~ div"
  		],
  		detectCmp: [
  			{
  				exists: "#footer-container ~ div"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#footer-container > div"
  			}
  		],
  		optIn: [
  			{
  				click: "#footer-container ~ div button"
  			}
  		],
  		optOut: [
  			{
  				hide: "#footer-container ~ div"
  			}
  		]
  	},
  	{
  		name: "ausopen.com",
  		cosmetic: true,
  		detectCmp: [
  			{
  				exists: ".gdpr-popup__message"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".gdpr-popup__message"
  			}
  		],
  		optOut: [
  			{
  				hide: ".gdpr-popup__message"
  			}
  		],
  		optIn: [
  			{
  				click: ".gdpr-popup__message button"
  			}
  		]
  	},
  	{
  		name: "automattic-cmp-optout",
  		prehideSelectors: [
  			"form[class*=\"cookie-banner\"][method=\"post\"]"
  		],
  		detectCmp: [
  			{
  				exists: "form[class*=\"cookie-banner\"][method=\"post\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "form[class*=\"cookie-banner\"][method=\"post\"]"
  			}
  		],
  		optIn: [
  			{
  				click: "a[class*=\"accept-all-button\"]"
  			}
  		],
  		optOut: [
  			{
  				click: "form[class*=\"cookie-banner\"] div[class*=\"simple-options\"] a[class*=\"customize-button\"]"
  			},
  			{
  				waitForThenClick: "input[type=checkbox][checked]:not([disabled])",
  				all: true
  			},
  			{
  				click: "a[class*=\"accept-selection-button\"]"
  			}
  		]
  	},
  	{
  		name: "aws.amazon.com",
  		prehideSelectors: [
  			"#awsccc-cb-content",
  			"#awsccc-cs-container",
  			"#awsccc-cs-modalOverlay",
  			"#awsccc-cs-container-inner"
  		],
  		detectCmp: [
  			{
  				exists: "#awsccc-cb-content"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#awsccc-cb-content"
  			}
  		],
  		optIn: [
  			{
  				click: "button[data-id=awsccc-cb-btn-accept"
  			}
  		],
  		optOut: [
  			{
  				click: "button[data-id=awsccc-cb-btn-customize]"
  			},
  			{
  				waitFor: "input[aria-checked]"
  			},
  			{
  				click: "input[aria-checked=true]",
  				all: true,
  				optional: true
  			},
  			{
  				click: "button[data-id=awsccc-cs-btn-save]"
  			}
  		]
  	},
  	{
  		name: "axeptio",
  		prehideSelectors: [
  			".axeptio_widget"
  		],
  		detectCmp: [
  			{
  				exists: ".axeptio_widget"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".axeptio_widget"
  			}
  		],
  		optIn: [
  			{
  				waitFor: ".axeptio-widget--open"
  			},
  			{
  				click: "button#axeptio_btn_acceptAll"
  			}
  		],
  		optOut: [
  			{
  				waitFor: ".axeptio-widget--open"
  			},
  			{
  				click: "button#axeptio_btn_dismiss"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_AXEPTIO_0"
  			}
  		]
  	},
  	{
  		name: "baden-wuerttemberg.de",
  		prehideSelectors: [
  			".cookie-alert.t-dark"
  		],
  		cosmetic: true,
  		detectCmp: [
  			{
  				exists: ".cookie-alert.t-dark"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-alert.t-dark"
  			}
  		],
  		optIn: [
  			{
  				click: ".cookie-alert__form input:not([disabled]):not([checked])"
  			},
  			{
  				click: ".cookie-alert__button button"
  			}
  		],
  		optOut: [
  			{
  				hide: ".cookie-alert.t-dark"
  			}
  		]
  	},
  	{
  		name: "bahn-de",
  		vendorUrl: "https://www.bahn.de/",
  		cosmetic: false,
  		runContext: {
  			main: true,
  			frame: false,
  			urlPattern: "^https://(www\\.)?bahn\\.de/"
  		},
  		intermediate: false,
  		prehideSelectors: [
  		],
  		detectCmp: [
  			{
  				exists: [
  					"body > div:first-child",
  					"#consent-layer"
  				]
  			}
  		],
  		detectPopup: [
  			{
  				visible: [
  					"body > div:first-child",
  					"#consent-layer"
  				]
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: [
  					"body > div:first-child",
  					"#consent-layer .js-accept-all-cookies"
  				]
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: [
  					"body > div:first-child",
  					"#consent-layer .js-accept-essential-cookies"
  				]
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_BAHN_TEST"
  			}
  		]
  	},
  	{
  		name: "bbb.org",
  		runContext: {
  			urlPattern: "^https://www\\.bbb\\.org/"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			"div[aria-label=\"use of cookies on bbb.org\"]"
  		],
  		detectCmp: [
  			{
  				exists: "div[aria-label=\"use of cookies on bbb.org\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "div[aria-label=\"use of cookies on bbb.org\"]"
  			}
  		],
  		optIn: [
  			{
  				click: "div[aria-label=\"use of cookies on bbb.org\"] button.bds-button-unstyled span.visually-hidden"
  			}
  		],
  		optOut: [
  			{
  				hide: "div[aria-label=\"use of cookies on bbb.org\"]"
  			}
  		]
  	},
  	{
  		name: "bing.com",
  		prehideSelectors: [
  			"#bnp_container"
  		],
  		detectCmp: [
  			{
  				exists: "#bnp_cookie_banner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#bnp_cookie_banner"
  			}
  		],
  		optIn: [
  			{
  				click: "#bnp_btn_accept"
  			}
  		],
  		optOut: [
  			{
  				click: "#bnp_btn_preference"
  			},
  			{
  				click: "#mcp_savesettings"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_BING_0"
  			}
  		]
  	},
  	{
  		name: "blocksy",
  		vendorUrl: "https://creativethemes.com/blocksy/docs/extensions/cookies-consent/",
  		cosmetic: false,
  		runContext: {
  			main: true,
  			frame: false
  		},
  		intermediate: false,
  		prehideSelectors: [
  			".cookie-notification"
  		],
  		detectCmp: [
  			{
  				exists: "#blocksy-ext-cookies-consent-styles-css"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-notification"
  			}
  		],
  		optIn: [
  			{
  				click: ".cookie-notification .ct-cookies-decline-button"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".cookie-notification .ct-cookies-decline-button"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_BLOCKSY_0"
  			}
  		]
  	},
  	{
  		name: "borlabs",
  		detectCmp: [
  			{
  				exists: "._brlbs-block-content"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "._brlbs-bar-wrap,._brlbs-box-wrap"
  			}
  		],
  		optIn: [
  			{
  				click: "a[data-cookie-accept-all]"
  			}
  		],
  		optOut: [
  			{
  				click: "a[data-cookie-individual]"
  			},
  			{
  				waitForVisible: ".cookie-preference"
  			},
  			{
  				click: "input[data-borlabs-cookie-checkbox]:checked",
  				all: true,
  				optional: true
  			},
  			{
  				click: "#CookiePrefSave"
  			},
  			{
  				wait: 500
  			}
  		],
  		prehideSelectors: [
  			"#BorlabsCookieBox"
  		],
  		test: [
  			{
  				"eval": "EVAL_BORLABS_0"
  			}
  		]
  	},
  	{
  		name: "bundesregierung.de",
  		prehideSelectors: [
  			".bpa-cookie-banner"
  		],
  		detectCmp: [
  			{
  				exists: ".bpa-cookie-banner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".bpa-cookie-banner .bpa-module-full-hero"
  			}
  		],
  		optIn: [
  			{
  				click: ".bpa-accept-all-button"
  			}
  		],
  		optOut: [
  			{
  				wait: 500,
  				comment: "click is not immediately recognized"
  			},
  			{
  				waitForThenClick: ".bpa-close-button"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_BUNDESREGIERUNG_DE_0"
  			}
  		]
  	},
  	{
  		name: "burpee.com",
  		cosmetic: true,
  		prehideSelectors: [
  			"#notice-cookie-block"
  		],
  		detectCmp: [
  			{
  				exists: "#notice-cookie-block"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "#html-body #notice-cookie-block"
  			}
  		],
  		optIn: [
  			{
  				click: "#btn-cookie-allow"
  			}
  		],
  		optOut: [
  			{
  				hide: "#html-body #notice-cookie-block, #notice-cookie"
  			}
  		]
  	},
  	{
  		name: "canva.com",
  		prehideSelectors: [
  			"div[role=\"dialog\"] a[data-anchor-id=\"cookie-policy\"]"
  		],
  		detectCmp: [
  			{
  				exists: "div[role=\"dialog\"] a[data-anchor-id=\"cookie-policy\"]"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "div[role=\"dialog\"] a[data-anchor-id=\"cookie-policy\"]"
  			}
  		],
  		optIn: [
  			{
  				click: "div[role=\"dialog\"] button:nth-child(1)"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "div[role=\"dialog\"] button:nth-child(3)"
  				},
  				then: [
  					{
  						click: "div[role=\"dialog\"] button:nth-child(2)"
  					}
  				],
  				"else": [
  					{
  						click: "div[role=\"dialog\"] button:nth-child(2)"
  					},
  					{
  						waitFor: "div[role=\"dialog\"] a[data-anchor-id=\"cookie-policy\"]"
  					},
  					{
  						waitFor: "div[role=\"dialog\"] button[role=switch]"
  					},
  					{
  						click: "div[role=\"dialog\"] button:nth-child(2):not([role])"
  					},
  					{
  						click: "div[role=\"dialog\"] div:last-child button:only-child"
  					}
  				]
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_CANVA_0"
  			}
  		]
  	},
  	{
  		name: "canyon.com",
  		runContext: {
  			urlPattern: "^https://www\\.canyon\\.com/"
  		},
  		prehideSelectors: [
  			"div.modal.cookiesModal.is-open"
  		],
  		detectCmp: [
  			{
  				exists: "div.modal.cookiesModal.is-open"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "div.modal.cookiesModal.is-open"
  			}
  		],
  		optIn: [
  			{
  				click: "div.cookiesModal__buttonWrapper > button[data-closecause=\"close-by-submit\"]"
  			}
  		],
  		optOut: [
  			{
  				click: "div.cookiesModal__buttonWrapper > button[data-closecause=\"close-by-manage-cookies\"]"
  			},
  			{
  				waitForThenClick: "button#js-manage-data-privacy-save-button"
  			}
  		]
  	},
  	{
  		name: "cc-banner-springer",
  		prehideSelectors: [
  			".cc-banner[data-cc-banner]"
  		],
  		detectCmp: [
  			{
  				exists: ".cc-banner[data-cc-banner]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cc-banner[data-cc-banner]"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".cc-banner[data-cc-banner] button[data-cc-action=accept]"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: ".cc-banner[data-cc-banner] button[data-cc-action=reject]"
  				},
  				then: [
  					{
  						click: ".cc-banner[data-cc-banner] button[data-cc-action=reject]"
  					}
  				],
  				"else": [
  					{
  						waitForThenClick: ".cc-banner[data-cc-banner] button[data-cc-action=preferences]"
  					},
  					{
  						waitFor: ".cc-preferences[data-cc-preferences]"
  					},
  					{
  						click: ".cc-preferences[data-cc-preferences] input[type=radio][data-cc-action=toggle-category][value=off]",
  						all: true,
  						optional: true
  					},
  					{
  						"if": {
  							exists: ".cc-preferences[data-cc-preferences] button[data-cc-action=reject]"
  						},
  						then: [
  							{
  								click: ".cc-preferences[data-cc-preferences] button[data-cc-action=reject]"
  							}
  						],
  						"else": [
  							{
  								click: ".cc-preferences[data-cc-preferences] button[data-cc-action=save]"
  							}
  						]
  					}
  				]
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_CC_BANNER2_0"
  			}
  		]
  	},
  	{
  		name: "cc_banner",
  		cosmetic: true,
  		prehideSelectors: [
  			".cc_banner-wrapper"
  		],
  		detectCmp: [
  			{
  				exists: ".cc_banner-wrapper"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cc_banner"
  			}
  		],
  		optIn: [
  			{
  				click: ".cc_btn_accept_all"
  			}
  		],
  		optOut: [
  			{
  				hide: ".cc_banner-wrapper"
  			}
  		]
  	},
  	{
  		name: "ciaopeople.it",
  		prehideSelectors: [
  			"#cp-gdpr-choices"
  		],
  		detectCmp: [
  			{
  				exists: "#cp-gdpr-choices"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cp-gdpr-choices"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".gdpr-btm__right > button:nth-child(2)"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".gdpr-top-content > button"
  			},
  			{
  				waitFor: ".gdpr-top-back"
  			},
  			{
  				waitForThenClick: ".gdpr-btm__right > button:nth-child(1)"
  			}
  		],
  		test: [
  			{
  				visible: "#cp-gdpr-choices",
  				check: "none"
  			}
  		]
  	},
  	{
  		vendorUrl: "https://www.civicuk.com/cookie-control/",
  		name: "civic-cookie-control",
  		prehideSelectors: [
  			"#ccc-module,#ccc-overlay"
  		],
  		detectCmp: [
  			{
  				exists: "#ccc-module"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#ccc"
  			},
  			{
  				visible: "#ccc-module"
  			}
  		],
  		optOut: [
  			{
  				click: "#ccc-reject-settings"
  			}
  		],
  		optIn: [
  			{
  				click: "#ccc-recommended-settings"
  			}
  		]
  	},
  	{
  		name: "click.io",
  		prehideSelectors: [
  			"#cl-consent"
  		],
  		detectCmp: [
  			{
  				exists: "#cl-consent"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cl-consent"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#cl-consent [data-role=\"b_agree\"]"
  			}
  		],
  		optOut: [
  			{
  				waitFor: "#cl-consent [data-role=\"b_options\"]"
  			},
  			{
  				wait: 500
  			},
  			{
  				click: "#cl-consent [data-role=\"b_options\"]"
  			},
  			{
  				waitFor: ".cl-consent-popup.cl-consent-visible [data-role=\"alloff\"]"
  			},
  			{
  				click: ".cl-consent-popup.cl-consent-visible [data-role=\"alloff\"]",
  				all: true
  			},
  			{
  				click: "[data-role=\"b_save\"]"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_CLICKIO_0",
  				comment: "TODO: this only checks if we interacted at all"
  			}
  		]
  	},
  	{
  		name: "clinch",
  		intermediate: false,
  		runContext: {
  			frame: false,
  			main: true
  		},
  		prehideSelectors: [
  			".consent-modal[role=dialog]"
  		],
  		detectCmp: [
  			{
  				exists: ".consent-modal[role=dialog]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".consent-modal[role=dialog]"
  			}
  		],
  		optIn: [
  			{
  				click: "#consent_agree"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "#consent_reject"
  				},
  				then: [
  					{
  						click: "#consent_reject"
  					}
  				],
  				"else": [
  					{
  						click: "#manage_cookie_preferences"
  					},
  					{
  						click: "#cookie_consent_preferences input:checked",
  						all: true,
  						optional: true
  					},
  					{
  						click: "#consent_save"
  					}
  				]
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_CLINCH_0"
  			}
  		]
  	},
  	{
  		name: "clustrmaps.com",
  		runContext: {
  			urlPattern: "^https://(www\\.)?clustrmaps\\.com/"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			"#gdpr-cookie-message"
  		],
  		detectCmp: [
  			{
  				exists: "#gdpr-cookie-message"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#gdpr-cookie-message"
  			}
  		],
  		optIn: [
  			{
  				click: "button#gdpr-cookie-accept"
  			}
  		],
  		optOut: [
  			{
  				hide: "#gdpr-cookie-message"
  			}
  		]
  	},
  	{
  		name: "coinbase",
  		intermediate: false,
  		runContext: {
  			frame: true,
  			main: true,
  			urlPattern: "^https://(www|help)\\.coinbase\\.com"
  		},
  		prehideSelectors: [
  		],
  		detectCmp: [
  			{
  				exists: "div[class^=CookieBannerContent__Container]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "div[class^=CookieBannerContent__Container]"
  			}
  		],
  		optIn: [
  			{
  				click: "div[class^=CookieBannerContent__CTA] :nth-last-child(1)"
  			}
  		],
  		optOut: [
  			{
  				click: "button[class^=CookieBannerContent__Settings]"
  			},
  			{
  				click: "div[class^=CookiePreferencesModal__CategoryContainer] input:checked",
  				all: true,
  				optional: true
  			},
  			{
  				click: "div[class^=CookiePreferencesModal__ButtonContainer] > button"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_COINBASE_0"
  			}
  		]
  	},
  	{
  		name: "Complianz banner",
  		prehideSelectors: [
  			"#cmplz-cookiebanner-container"
  		],
  		detectCmp: [
  			{
  				exists: "#cmplz-cookiebanner-container .cmplz-cookiebanner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cmplz-cookiebanner-container .cmplz-cookiebanner",
  				check: "any"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".cmplz-cookiebanner .cmplz-accept"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".cmplz-cookiebanner .cmplz-deny"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_COMPLIANZ_BANNER_0"
  			}
  		]
  	},
  	{
  		name: "Complianz categories",
  		prehideSelectors: [
  			".cc-type-categories[aria-describedby=\"cookieconsent:desc\"]"
  		],
  		detectCmp: [
  			{
  				exists: ".cc-type-categories[aria-describedby=\"cookieconsent:desc\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cc-type-categories[aria-describedby=\"cookieconsent:desc\"]"
  			}
  		],
  		optIn: [
  			{
  				any: [
  					{
  						click: ".cc-accept-all"
  					},
  					{
  						click: ".cc-allow-all"
  					},
  					{
  						click: ".cc-allow"
  					},
  					{
  						click: ".cc-dismiss"
  					}
  				]
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: ".cc-type-categories[aria-describedby=\"cookieconsent:desc\"] .cc-dismiss"
  				},
  				then: [
  					{
  						click: ".cc-dismiss"
  					}
  				],
  				"else": [
  					{
  						click: ".cc-type-categories input[type=checkbox]:not([disabled]):checked",
  						all: true,
  						optional: true
  					},
  					{
  						click: ".cc-save"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "Complianz notice",
  		prehideSelectors: [
  			".cc-type-info[aria-describedby=\"cookieconsent:desc\"]"
  		],
  		cosmetic: true,
  		detectCmp: [
  			{
  				exists: ".cc-type-info[aria-describedby=\"cookieconsent:desc\"] .cc-compliance .cc-btn"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cc-type-info[aria-describedby=\"cookieconsent:desc\"] .cc-compliance .cc-btn"
  			}
  		],
  		optIn: [
  			{
  				click: ".cc-accept-all",
  				optional: true
  			},
  			{
  				click: ".cc-allow",
  				optional: true
  			},
  			{
  				click: ".cc-dismiss",
  				optional: true
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: ".cc-deny"
  				},
  				then: [
  					{
  						click: ".cc-deny"
  					}
  				],
  				"else": [
  					{
  						hide: "[aria-describedby=\"cookieconsent:desc\"]"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "Complianz opt-both",
  		prehideSelectors: [
  			"[aria-describedby=\"cookieconsent:desc\"] .cc-type-opt-both"
  		],
  		detectCmp: [
  			{
  				exists: "[aria-describedby=\"cookieconsent:desc\"] .cc-type-opt-both"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "[aria-describedby=\"cookieconsent:desc\"] .cc-type-opt-both"
  			}
  		],
  		optIn: [
  			{
  				click: ".cc-accept-all",
  				optional: true
  			},
  			{
  				click: ".cc-allow",
  				optional: true
  			},
  			{
  				click: ".cc-dismiss",
  				optional: true
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".cc-deny"
  			}
  		]
  	},
  	{
  		name: "Complianz opt-out",
  		prehideSelectors: [
  			"[aria-describedby=\"cookieconsent:desc\"].cc-type-opt-out"
  		],
  		detectCmp: [
  			{
  				exists: "[aria-describedby=\"cookieconsent:desc\"].cc-type-opt-out"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "[aria-describedby=\"cookieconsent:desc\"].cc-type-opt-out"
  			}
  		],
  		optIn: [
  			{
  				click: ".cc-accept-all",
  				optional: true
  			},
  			{
  				click: ".cc-allow",
  				optional: true
  			},
  			{
  				click: ".cc-dismiss",
  				optional: true
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: ".cc-deny"
  				},
  				then: [
  					{
  						click: ".cc-deny"
  					}
  				],
  				"else": [
  					{
  						"if": {
  							exists: ".cmp-pref-link"
  						},
  						then: [
  							{
  								click: ".cmp-pref-link"
  							},
  							{
  								waitForThenClick: ".cmp-body [id*=rejectAll]"
  							},
  							{
  								waitForThenClick: ".cmp-body .cmp-save-btn"
  							}
  						]
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "Complianz optin",
  		prehideSelectors: [
  			".cc-type-opt-in[aria-describedby=\"cookieconsent:desc\"]"
  		],
  		detectCmp: [
  			{
  				exists: ".cc-type-opt-in[aria-describedby=\"cookieconsent:desc\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cc-type-opt-in[aria-describedby=\"cookieconsent:desc\"]"
  			}
  		],
  		optIn: [
  			{
  				any: [
  					{
  						click: ".cc-accept-all"
  					},
  					{
  						click: ".cc-allow"
  					},
  					{
  						click: ".cc-dismiss"
  					}
  				]
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					visible: ".cc-deny"
  				},
  				then: [
  					{
  						click: ".cc-deny"
  					}
  				],
  				"else": [
  					{
  						"if": {
  							visible: ".cc-settings"
  						},
  						then: [
  							{
  								waitForThenClick: ".cc-settings"
  							},
  							{
  								waitForVisible: ".cc-settings-view"
  							},
  							{
  								click: ".cc-settings-view input[type=checkbox]:not([disabled]):checked",
  								all: true,
  								optional: true
  							},
  							{
  								click: ".cc-settings-view .cc-btn-accept-selected"
  							}
  						],
  						"else": [
  							{
  								click: ".cc-dismiss"
  							}
  						]
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "cookie-law-info",
  		prehideSelectors: [
  			"#cookie-law-info-bar"
  		],
  		detectCmp: [
  			{
  				exists: "#cookie-law-info-bar"
  			},
  			{
  				"eval": "EVAL_COOKIE_LAW_INFO_DETECT"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookie-law-info-bar"
  			}
  		],
  		optIn: [
  			{
  				click: "[data-cli_action=\"accept_all\"]"
  			}
  		],
  		optOut: [
  			{
  				hide: "#cookie-law-info-bar"
  			},
  			{
  				"eval": "EVAL_COOKIE_LAW_INFO_0"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_COOKIE_LAW_INFO_1"
  			}
  		]
  	},
  	{
  		name: "cookie-manager-popup",
  		cosmetic: false,
  		runContext: {
  			main: true,
  			frame: false
  		},
  		intermediate: false,
  		detectCmp: [
  			{
  				exists: "#notice-cookie-block #allow-functional-cookies, #notice-cookie-block #btn-cookie-settings"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#notice-cookie-block"
  			}
  		],
  		optIn: [
  			{
  				click: "#btn-cookie-allow"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "#allow-functional-cookies"
  				},
  				then: [
  					{
  						click: "#allow-functional-cookies"
  					}
  				],
  				"else": [
  					{
  						waitForThenClick: "#btn-cookie-settings"
  					},
  					{
  						waitForVisible: ".modal-body"
  					},
  					{
  						click: ".modal-body input:checked, .switch[data-switch=\"on\"]",
  						all: true,
  						optional: true
  					},
  					{
  						click: "[role=\"dialog\"] .modal-footer button"
  					}
  				]
  			}
  		],
  		prehideSelectors: [
  			"#btn-cookie-settings"
  		],
  		test: [
  			{
  				"eval": "EVAL_COOKIE_MANAGER_POPUP_0"
  			}
  		]
  	},
  	{
  		name: "cookie-notice",
  		prehideSelectors: [
  			"#cookie-notice"
  		],
  		cosmetic: true,
  		detectCmp: [
  			{
  				visible: "#cookie-notice .cookie-notice-container"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookie-notice"
  			}
  		],
  		optIn: [
  			{
  				click: "#cn-accept-cookie"
  			}
  		],
  		optOut: [
  			{
  				hide: "#cookie-notice"
  			}
  		]
  	},
  	{
  		name: "cookie-script",
  		vendorUrl: "https://cookie-script.com/",
  		prehideSelectors: [
  			"#cookiescript_injected"
  		],
  		detectCmp: [
  			{
  				exists: "#cookiescript_injected"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookiescript_injected"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "#cookiescript_reject"
  				},
  				then: [
  					{
  						wait: 100
  					},
  					{
  						click: "#cookiescript_reject"
  					}
  				],
  				"else": [
  					{
  						click: "#cookiescript_manage"
  					},
  					{
  						waitForVisible: ".cookiescript_fsd_main"
  					},
  					{
  						waitForThenClick: "#cookiescript_reject"
  					}
  				]
  			}
  		],
  		optIn: [
  			{
  				click: "#cookiescript_accept"
  			}
  		]
  	},
  	{
  		name: "cookieacceptbar",
  		vendorUrl: "https://unknown",
  		cosmetic: true,
  		prehideSelectors: [
  			"#cookieAcceptBar.cookieAcceptBar"
  		],
  		detectCmp: [
  			{
  				exists: "#cookieAcceptBar.cookieAcceptBar"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookieAcceptBar.cookieAcceptBar"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#cookieAcceptBarConfirm"
  			}
  		],
  		optOut: [
  			{
  				hide: "#cookieAcceptBar.cookieAcceptBar"
  			}
  		]
  	},
  	{
  		name: "cookiealert",
  		intermediate: false,
  		prehideSelectors: [
  		],
  		runContext: {
  			frame: true,
  			main: true
  		},
  		detectCmp: [
  			{
  				exists: ".cookie-alert-extended"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-alert-extended-modal"
  			}
  		],
  		optIn: [
  			{
  				click: "button[data-controller='cookie-alert/extended/button/accept']"
  			},
  			{
  				"eval": "EVAL_COOKIEALERT_0"
  			}
  		],
  		optOut: [
  			{
  				click: "a[data-controller='cookie-alert/extended/detail-link']"
  			},
  			{
  				click: ".cookie-alert-configuration-input:checked",
  				all: true,
  				optional: true
  			},
  			{
  				click: "button[data-controller='cookie-alert/extended/button/configuration']"
  			},
  			{
  				"eval": "EVAL_COOKIEALERT_0"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_COOKIEALERT_2"
  			}
  		]
  	},
  	{
  		name: "cookieconsent2",
  		vendorUrl: "https://www.github.com/orestbida/cookieconsent",
  		comment: "supports v2.x.x of the library",
  		prehideSelectors: [
  			"#cc--main"
  		],
  		detectCmp: [
  			{
  				exists: "#cc--main"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cm"
  			},
  			{
  				exists: "#s-all-bn"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#s-all-bn"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#s-rall-bn"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_COOKIECONSENT2_TEST"
  			}
  		]
  	},
  	{
  		name: "cookieconsent3",
  		vendorUrl: "https://www.github.com/orestbida/cookieconsent",
  		comment: "supports v3.x.x of the library",
  		prehideSelectors: [
  			"#cc-main"
  		],
  		detectCmp: [
  			{
  				exists: "#cc-main"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cc-main .cm-wrapper"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".cm__btn[data-role=all]"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".cm__btn[data-role=necessary]"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_COOKIECONSENT3_TEST"
  			}
  		]
  	},
  	{
  		name: "cookiecuttr",
  		vendorUrl: "https://github.com/cdwharton/cookieCuttr",
  		cosmetic: false,
  		runContext: {
  			main: true,
  			frame: false,
  			urlPattern: ""
  		},
  		prehideSelectors: [
  			".cc-cookies"
  		],
  		detectCmp: [
  			{
  				exists: ".cc-cookies .cc-cookie-accept"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cc-cookies .cc-cookie-accept"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".cc-cookies .cc-cookie-accept"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: ".cc-cookies .cc-cookie-decline"
  				},
  				then: [
  					{
  						click: ".cc-cookies .cc-cookie-decline"
  					}
  				],
  				"else": [
  					{
  						hide: ".cc-cookies"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "cookiefirst.com",
  		prehideSelectors: [
  			"#cookiefirst-root,.cookiefirst-root,[aria-labelledby=cookie-preference-panel-title]"
  		],
  		detectCmp: [
  			{
  				exists: "#cookiefirst-root,.cookiefirst-root"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookiefirst-root,.cookiefirst-root"
  			}
  		],
  		optIn: [
  			{
  				click: "button[data-cookiefirst-action=accept]"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "button[data-cookiefirst-action=adjust]"
  				},
  				then: [
  					{
  						click: "button[data-cookiefirst-action=adjust]"
  					},
  					{
  						waitForVisible: "[data-cookiefirst-widget=modal]",
  						timeout: 1000
  					},
  					{
  						"eval": "EVAL_COOKIEFIRST_1"
  					},
  					{
  						wait: 1000
  					},
  					{
  						click: "button[data-cookiefirst-action=save]"
  					}
  				],
  				"else": [
  					{
  						click: "button[data-cookiefirst-action=reject]"
  					}
  				]
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_COOKIEFIRST_0"
  			}
  		]
  	},
  	{
  		name: "Cookie Information Banner",
  		prehideSelectors: [
  			"#cookie-information-template-wrapper"
  		],
  		detectCmp: [
  			{
  				exists: "#cookie-information-template-wrapper"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookie-information-template-wrapper"
  			}
  		],
  		optIn: [
  			{
  				"eval": "EVAL_COOKIEINFORMATION_1"
  			}
  		],
  		optOut: [
  			{
  				hide: "#cookie-information-template-wrapper",
  				comment: "some templates don't hide the banner automatically"
  			},
  			{
  				"eval": "EVAL_COOKIEINFORMATION_0"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_COOKIEINFORMATION_2"
  			}
  		]
  	},
  	{
  		name: "cookieyes",
  		prehideSelectors: [
  			".cky-overlay,.cky-consent-container"
  		],
  		detectCmp: [
  			{
  				exists: ".cky-consent-container"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cky-consent-container"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".cky-consent-container [data-cky-tag=accept-button]"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: ".cky-consent-container [data-cky-tag=reject-button]"
  				},
  				then: [
  					{
  						waitForThenClick: ".cky-consent-container [data-cky-tag=reject-button]"
  					}
  				],
  				"else": [
  					{
  						"if": {
  							exists: ".cky-consent-container [data-cky-tag=settings-button]"
  						},
  						then: [
  							{
  								click: ".cky-consent-container [data-cky-tag=settings-button]"
  							},
  							{
  								waitFor: ".cky-modal-open input[type=checkbox]"
  							},
  							{
  								click: ".cky-modal-open input[type=checkbox]:checked",
  								all: true,
  								optional: true
  							},
  							{
  								waitForThenClick: ".cky-modal [data-cky-tag=detail-save-button]"
  							}
  						],
  						"else": [
  							{
  								hide: ".cky-consent-container,.cky-overlay"
  							}
  						]
  					}
  				]
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_COOKIEYES_0"
  			}
  		]
  	},
  	{
  		name: "corona-in-zahlen.de",
  		prehideSelectors: [
  			".cookiealert"
  		],
  		detectCmp: [
  			{
  				exists: ".cookiealert"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookiealert"
  			}
  		],
  		optOut: [
  			{
  				click: ".configurecookies"
  			},
  			{
  				click: ".confirmcookies"
  			}
  		],
  		optIn: [
  			{
  				click: ".acceptcookies"
  			}
  		]
  	},
  	{
  		name: "crossfit-com",
  		cosmetic: true,
  		prehideSelectors: [
  			"body #modal > div > div[class^=\"_wrapper_\"]"
  		],
  		detectCmp: [
  			{
  				exists: "body #modal > div > div[class^=\"_wrapper_\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "body #modal > div > div[class^=\"_wrapper_\"]"
  			}
  		],
  		optIn: [
  			{
  				click: "button[aria-label=\"accept cookie policy\"]"
  			}
  		],
  		optOut: [
  			{
  				hide: "body #modal > div > div[class^=\"_wrapper_\"]"
  			}
  		]
  	},
  	{
  		name: "csu-landtag-de",
  		runContext: {
  			urlPattern: "^https://(www\\.|)?csu-landtag\\.de"
  		},
  		prehideSelectors: [
  			"#cookie-disclaimer"
  		],
  		detectCmp: [
  			{
  				exists: "#cookie-disclaimer"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookie-disclaimer"
  			}
  		],
  		optIn: [
  			{
  				click: "#cookieall"
  			}
  		],
  		optOut: [
  			{
  				click: "#cookiesel"
  			}
  		]
  	},
  	{
  		name: "dailymotion-us",
  		cosmetic: true,
  		prehideSelectors: [
  			"div[class*=\"CookiePopup__desktopContainer\"]:has(div[class*=\"CookiePopup\"])"
  		],
  		detectCmp: [
  			{
  				exists: "div[class*=\"CookiePopup__desktopContainer\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "div[class*=\"CookiePopup__desktopContainer\"]"
  			}
  		],
  		optIn: [
  			{
  				click: "div[class*=\"CookiePopup__desktopContainer\"] > button > span"
  			}
  		],
  		optOut: [
  			{
  				hide: "div[class*=\"CookiePopup__desktopContainer\"]"
  			}
  		]
  	},
  	{
  		name: "dailymotion.com",
  		runContext: {
  			urlPattern: "^https://(www\\.)?dailymotion\\.com/"
  		},
  		prehideSelectors: [
  			"div[class*=\"Overlay__container\"]:has(div[class*=\"TCF2Popup\"])"
  		],
  		detectCmp: [
  			{
  				exists: "div[class*=\"TCF2Popup\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "[class*=\"TCF2Popup\"] a[href^=\"https://www.dailymotion.com/legal/cookiemanagement\"]"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "button[class*=\"TCF2Popup__button\"]:not([class*=\"TCF2Popup__personalize\"])"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "button[class*=\"TCF2ContinueWithoutAcceptingButton\"]"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_DAILYMOTION_0"
  			}
  		]
  	},
  	{
  		name: "deepl.com",
  		prehideSelectors: [
  			".dl_cookieBanner_container"
  		],
  		detectCmp: [
  			{
  				exists: ".dl_cookieBanner_container"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".dl_cookieBanner_container"
  			}
  		],
  		optOut: [
  			{
  				click: ".dl_cookieBanner--buttonSelected"
  			}
  		],
  		optIn: [
  			{
  				click: ".dl_cookieBanner--buttonAll"
  			}
  		]
  	},
  	{
  		name: "delta.com",
  		runContext: {
  			urlPattern: "^https://www\\.delta\\.com/"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			"ngc-cookie-banner"
  		],
  		detectCmp: [
  			{
  				exists: "div.cookie-footer-container"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "div.cookie-footer-container"
  			}
  		],
  		optIn: [
  			{
  				click: " button.cookie-close-icon"
  			}
  		],
  		optOut: [
  			{
  				hide: "div.cookie-footer-container"
  			}
  		]
  	},
  	{
  		name: "dmgmedia-us",
  		prehideSelectors: [
  			"#mol-ads-cmp-iframe, div.mol-ads-cmp > form > div"
  		],
  		detectCmp: [
  			{
  				exists: "div.mol-ads-cmp > form > div"
  			}
  		],
  		detectPopup: [
  			{
  				waitForVisible: "div.mol-ads-cmp > form > div"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "button.mol-ads-cmp--btn-primary"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "div.mol-ads-ccpa--message > u > a"
  			},
  			{
  				waitForVisible: ".mol-ads-cmp--modal-dialog"
  			},
  			{
  				waitForThenClick: "a.mol-ads-cmp-footer-privacy"
  			},
  			{
  				waitForThenClick: "button.mol-ads-cmp--btn-secondary"
  			}
  		]
  	},
  	{
  		name: "dmgmedia",
  		prehideSelectors: [
  			"[data-project=\"mol-fe-cmp\"]"
  		],
  		detectCmp: [
  			{
  				exists: "[data-project=\"mol-fe-cmp\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "[data-project=\"mol-fe-cmp\"]"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "[data-project=\"mol-fe-cmp\"] button[class*=primary]"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "[data-project=\"mol-fe-cmp\"] button[class*=basic]"
  			},
  			{
  				waitForVisible: "[data-project=\"mol-fe-cmp\"] div[class*=\"tabContent\"]"
  			},
  			{
  				waitForThenClick: "[data-project=\"mol-fe-cmp\"] div[class*=\"toggle\"][class*=\"enabled\"]",
  				all: true
  			},
  			{
  				waitForThenClick: [
  					"[data-project=\"mol-fe-cmp\"] [class*=footer]",
  					"xpath///button[contains(., 'Save & Exit')]"
  				]
  			}
  		]
  	},
  	{
  		name: "dndbeyond",
  		vendorUrl: "https://www.dndbeyond.com/",
  		runContext: {
  			urlPattern: "^https://(www\\.)?dndbeyond\\.com/"
  		},
  		prehideSelectors: [
  			"[id^=cookie-consent-banner]"
  		],
  		detectCmp: [
  			{
  				exists: "[id^=cookie-consent-banner]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "[id^=cookie-consent-banner]"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#cookie-consent-granted"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#cookie-consent-denied"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_DNDBEYOND_TEST"
  			}
  		]
  	},
  	{
  		name: "Drupal",
  		detectCmp: [
  			{
  				exists: "#drupalorg-crosssite-gdpr"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#drupalorg-crosssite-gdpr"
  			}
  		],
  		optOut: [
  			{
  				click: ".no"
  			}
  		],
  		optIn: [
  			{
  				click: ".yes"
  			}
  		]
  	},
  	{
  		name: "WP DSGVO Tools",
  		link: "https://wordpress.org/plugins/shapepress-dsgvo/",
  		prehideSelectors: [
  			".sp-dsgvo"
  		],
  		cosmetic: true,
  		detectCmp: [
  			{
  				exists: ".sp-dsgvo.sp-dsgvo-popup-overlay"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".sp-dsgvo.sp-dsgvo-popup-overlay",
  				check: "any"
  			}
  		],
  		optIn: [
  			{
  				click: ".sp-dsgvo-privacy-btn-accept-all",
  				all: true
  			}
  		],
  		optOut: [
  			{
  				hide: ".sp-dsgvo.sp-dsgvo-popup-overlay"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_DSGVO_0"
  			}
  		]
  	},
  	{
  		name: "dunelm.com",
  		prehideSelectors: [
  			"div[data-testid=cookie-consent-modal-backdrop]"
  		],
  		detectCmp: [
  			{
  				exists: "div[data-testid=cookie-consent-message-contents]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "div[data-testid=cookie-consent-message-contents]"
  			}
  		],
  		optIn: [
  			{
  				click: "[data-testid=\"cookie-consent-allow-all\"]"
  			}
  		],
  		optOut: [
  			{
  				click: "button[data-testid=cookie-consent-adjust-settings]"
  			},
  			{
  				click: "button[data-testid=cookie-consent-preferences-save]"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_DUNELM_0"
  			}
  		]
  	},
  	{
  		name: "ecosia",
  		vendorUrl: "https://www.ecosia.org/",
  		runContext: {
  			urlPattern: "^https://www\\.ecosia\\.org/"
  		},
  		prehideSelectors: [
  			".cookie-wrapper"
  		],
  		detectCmp: [
  			{
  				exists: ".cookie-wrapper > .cookie-notice"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-wrapper > .cookie-notice"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "[data-test-id=cookie-notice-accept]"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "[data-test-id=cookie-notice-reject]"
  			}
  		]
  	},
  	{
  		name: "Ensighten ensModal",
  		prehideSelectors: [
  			".ensModal"
  		],
  		detectCmp: [
  			{
  				exists: ".ensModal"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".ensModal"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#modalAcceptButton"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".ensCheckbox:checked",
  				all: true
  			},
  			{
  				waitForThenClick: "#ensSave"
  			}
  		]
  	},
  	{
  		name: "Ensighten ensNotifyBanner",
  		prehideSelectors: [
  			"#ensNotifyBanner"
  		],
  		detectCmp: [
  			{
  				exists: "#ensNotifyBanner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#ensNotifyBanner"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#ensCloseBanner"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#ensRejectAll,#rejectAll,#ensRejectBanner"
  			}
  		]
  	},
  	{
  		name: "espace-personnel.agirc-arrco.fr",
  		runContext: {
  			urlPattern: "^https://espace-personnel\\.agirc-arrco\\.fr/"
  		},
  		prehideSelectors: [
  			".cdk-overlay-container"
  		],
  		detectCmp: [
  			{
  				exists: ".cdk-overlay-container app-esaa-cookie-component"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cdk-overlay-container app-esaa-cookie-component"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".btn-cookie-accepter"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".btn-cookie-refuser"
  			}
  		]
  	},
  	{
  		name: "etsy",
  		prehideSelectors: [
  			"#gdpr-single-choice-overlay",
  			"#gdpr-privacy-settings"
  		],
  		detectCmp: [
  			{
  				exists: "#gdpr-single-choice-overlay"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#gdpr-single-choice-overlay"
  			}
  		],
  		optOut: [
  			{
  				click: "button[data-gdpr-open-full-settings]"
  			},
  			{
  				waitForVisible: ".gdpr-overlay-body input",
  				timeout: 3000
  			},
  			{
  				wait: 1000
  			},
  			{
  				"eval": "EVAL_ETSY_0"
  			},
  			{
  				"eval": "EVAL_ETSY_1"
  			}
  		],
  		optIn: [
  			{
  				click: "button[data-gdpr-single-choice-accept]"
  			}
  		]
  	},
  	{
  		name: "eu-cookie-compliance-banner",
  		detectCmp: [
  			{
  				exists: "body.eu-cookie-compliance-popup-open"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "body.eu-cookie-compliance-popup-open"
  			}
  		],
  		optIn: [
  			{
  				click: ".agree-button"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					visible: ".decline-button,.eu-cookie-compliance-save-preferences-button"
  				},
  				then: [
  					{
  						click: ".decline-button,.eu-cookie-compliance-save-preferences-button"
  					}
  				]
  			},
  			{
  				hide: ".eu-cookie-compliance-banner-info, #sliding-popup"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_EU_COOKIE_COMPLIANCE_0"
  			}
  		]
  	},
  	{
  		name: "EU Cookie Law",
  		prehideSelectors: [
  			".pea_cook_wrapper,.pea_cook_more_info_popover"
  		],
  		cosmetic: true,
  		detectCmp: [
  			{
  				exists: ".pea_cook_wrapper"
  			}
  		],
  		detectPopup: [
  			{
  				wait: 500
  			},
  			{
  				visible: ".pea_cook_wrapper"
  			}
  		],
  		optIn: [
  			{
  				click: "#pea_cook_btn"
  			}
  		],
  		optOut: [
  			{
  				hide: ".pea_cook_wrapper"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_EU_COOKIE_LAW_0"
  			}
  		]
  	},
  	{
  		name: "europa-eu",
  		vendorUrl: "https://ec.europa.eu/",
  		runContext: {
  			urlPattern: "^https://[^/]*europa\\.eu/"
  		},
  		prehideSelectors: [
  			"#cookie-consent-banner"
  		],
  		detectCmp: [
  			{
  				exists: ".cck-container"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cck-container"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".cck-actions-button[href=\"#accept\"]"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".cck-actions-button[href=\"#refuse\"]",
  				hide: ".cck-container"
  			}
  		]
  	},
  	{
  		name: "EZoic",
  		prehideSelectors: [
  			"#ez-cookie-dialog-wrapper"
  		],
  		detectCmp: [
  			{
  				exists: "#ez-cookie-dialog-wrapper"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#ez-cookie-dialog-wrapper"
  			}
  		],
  		optIn: [
  			{
  				click: "#ez-accept-all",
  				optional: true
  			},
  			{
  				"eval": "EVAL_EZOIC_0",
  				optional: true
  			}
  		],
  		optOut: [
  			{
  				wait: 500
  			},
  			{
  				click: "#ez-manage-settings"
  			},
  			{
  				waitFor: "#ez-cookie-dialog input[type=checkbox]"
  			},
  			{
  				click: "#ez-cookie-dialog input[type=checkbox]:checked",
  				all: true
  			},
  			{
  				click: "#ez-save-settings"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_EZOIC_1"
  			}
  		]
  	},
  	{
  		name: "facebook",
  		runContext: {
  			urlPattern: "^https://([a-z0-9-]+\\.)?facebook\\.com/"
  		},
  		prehideSelectors: [
  			"div[data-testid=\"cookie-policy-manage-dialog\"]"
  		],
  		detectCmp: [
  			{
  				exists: "div[data-testid=\"cookie-policy-manage-dialog\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "div[data-testid=\"cookie-policy-manage-dialog\"]"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "button[data-cookiebanner=\"accept_button\"]"
  			},
  			{
  				waitForVisible: "div[data-testid=\"cookie-policy-manage-dialog\"]",
  				check: "none"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "button[data-cookiebanner=\"accept_only_essential_button\"]"
  			},
  			{
  				waitForVisible: "div[data-testid=\"cookie-policy-manage-dialog\"]",
  				check: "none"
  			}
  		]
  	},
  	{
  		name: "fides",
  		vendorUrl: "https://github.com/ethyca/fides",
  		prehideSelectors: [
  			"#fides-overlay"
  		],
  		detectCmp: [
  			{
  				exists: "#fides-overlay #fides-banner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#fides-overlay #fides-banner"
  			},
  			{
  				"eval": "EVAL_FIDES_DETECT_POPUP"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#fides-banner .fides-accept-all-button"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#fides-banner .fides-reject-all-button"
  			}
  		]
  	},
  	{
  		name: "funding-choices",
  		prehideSelectors: [
  			".fc-consent-root,.fc-dialog-container,.fc-dialog-overlay,.fc-dialog-content"
  		],
  		detectCmp: [
  			{
  				exists: ".fc-consent-root"
  			}
  		],
  		detectPopup: [
  			{
  				exists: ".fc-dialog-container"
  			}
  		],
  		optOut: [
  			{
  				click: ".fc-cta-do-not-consent,.fc-cta-manage-options"
  			},
  			{
  				click: ".fc-preference-consent:checked,.fc-preference-legitimate-interest:checked",
  				all: true,
  				optional: true
  			},
  			{
  				click: ".fc-confirm-choices",
  				optional: true
  			}
  		],
  		optIn: [
  			{
  				click: ".fc-cta-consent"
  			}
  		]
  	},
  	{
  		name: "geeks-for-geeks",
  		runContext: {
  			urlPattern: "^https://www\\.geeksforgeeks\\.org/"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			".cookie-consent"
  		],
  		detectCmp: [
  			{
  				exists: ".cookie-consent"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-consent"
  			}
  		],
  		optIn: [
  			{
  				click: ".cookie-consent button.consent-btn"
  			}
  		],
  		optOut: [
  			{
  				hide: ".cookie-consent"
  			}
  		]
  	},
  	{
  		name: "generic-cosmetic",
  		cosmetic: true,
  		prehideSelectors: [
  			"#js-cookie-banner,.js-cookie-banner,.cookie-banner,#cookie-banner"
  		],
  		detectCmp: [
  			{
  				exists: "#js-cookie-banner,.js-cookie-banner,.cookie-banner,#cookie-banner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#js-cookie-banner,.js-cookie-banner,.cookie-banner,#cookie-banner"
  			}
  		],
  		optIn: [
  		],
  		optOut: [
  			{
  				hide: "#js-cookie-banner,.js-cookie-banner,.cookie-banner,#cookie-banner"
  			}
  		]
  	},
  	{
  		name: "google-consent-standalone",
  		prehideSelectors: [
  		],
  		detectCmp: [
  			{
  				exists: "a[href^=\"https://policies.google.com/technologies/cookies\""
  			},
  			{
  				exists: "form[action^=\"https://consent.google.\"][action$=\".com/save\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "a[href^=\"https://policies.google.com/technologies/cookies\""
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "form[action^=\"https://consent.google.\"][action$=\".com/save\"]:has(input[name=set_eom][value=false]) button"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "form[action^=\"https://consent.google.\"][action$=\".com/save\"]:has(input[name=set_eom][value=true]) button"
  			}
  		]
  	},
  	{
  		name: "google.com",
  		prehideSelectors: [
  			".HTjtHe#xe7COe"
  		],
  		detectCmp: [
  			{
  				exists: ".HTjtHe#xe7COe"
  			},
  			{
  				exists: ".HTjtHe#xe7COe a[href^=\"https://policies.google.com/technologies/cookies\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".HTjtHe#xe7COe button#W0wltc"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".HTjtHe#xe7COe button#L2AGLb"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".HTjtHe#xe7COe button#W0wltc"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_GOOGLE_0"
  			}
  		]
  	},
  	{
  		name: "gov.uk",
  		detectCmp: [
  			{
  				exists: "#global-cookie-message"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "#global-cookie-message"
  			}
  		],
  		optIn: [
  			{
  				click: "button[data-accept-cookies=true]"
  			}
  		],
  		optOut: [
  			{
  				click: "button[data-reject-cookies=true],#reject-cookies"
  			},
  			{
  				click: "button[data-hide-cookie-banner=true],#hide-cookie-decision"
  			}
  		]
  	},
  	{
  		name: "hashicorp",
  		vendorUrl: "https://hashicorp.com/",
  		runContext: {
  			urlPattern: "^https://[^.]*\\.hashicorp\\.com/"
  		},
  		prehideSelectors: [
  			"[data-testid=consent-banner]"
  		],
  		detectCmp: [
  			{
  				exists: "[data-testid=consent-banner]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "[data-testid=consent-banner]"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "[data-testid=accept]"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "[data-testid=manage-preferences]"
  			},
  			{
  				waitForThenClick: "[data-testid=consent-mgr-dialog] [data-ga-button=save-preferences]"
  			}
  		]
  	},
  	{
  		name: "healthline-media",
  		prehideSelectors: [
  			"#modal-host > div.no-hash > div.window-wrapper"
  		],
  		detectCmp: [
  			{
  				exists: "#modal-host > div.no-hash > div.window-wrapper, div[data-testid=qualtrics-container]"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "#modal-host > div.no-hash > div.window-wrapper, div[data-testid=qualtrics-container]"
  			}
  		],
  		optIn: [
  			{
  				click: "#modal-host > div.no-hash > div.window-wrapper > div:last-child button"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "#modal-host > div.no-hash > div.window-wrapper > div:last-child a[href=\"/privacy-settings\"]"
  				},
  				then: [
  					{
  						click: "#modal-host > div.no-hash > div.window-wrapper > div:last-child a[href=\"/privacy-settings\"]"
  					}
  				],
  				"else": [
  					{
  						waitForVisible: "div#__next"
  					},
  					{
  						click: "#__next div:nth-child(1) > button:first-child"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "hema",
  		prehideSelectors: [
  			".cookie-modal"
  		],
  		detectCmp: [
  			{
  				visible: ".cookie-modal .cookie-accept-btn"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-modal .cookie-accept-btn"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".cookie-modal .cookie-accept-btn"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".cookie-modal .js-cookie-reject-btn"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_HEMA_TEST_0"
  			}
  		]
  	},
  	{
  		name: "hetzner.com",
  		runContext: {
  			urlPattern: "^https://www\\.hetzner\\.com/"
  		},
  		prehideSelectors: [
  			"#CookieConsent"
  		],
  		detectCmp: [
  			{
  				exists: "#CookieConsent"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#CookieConsent"
  			}
  		],
  		optIn: [
  			{
  				click: "#CookieConsentGiven"
  			}
  		],
  		optOut: [
  			{
  				click: "#CookieConsentDeclined"
  			}
  		]
  	},
  	{
  		name: "hl.co.uk",
  		prehideSelectors: [
  			".cookieModalContent",
  			"#cookie-banner-overlay"
  		],
  		detectCmp: [
  			{
  				exists: "#cookie-banner-overlay"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "#cookie-banner-overlay"
  			}
  		],
  		optIn: [
  			{
  				click: "#acceptCookieButton"
  			}
  		],
  		optOut: [
  			{
  				click: "#manageCookie"
  			},
  			{
  				hide: ".cookieSettingsModal"
  			},
  			{
  				waitFor: "#AOCookieToggle"
  			},
  			{
  				click: "#AOCookieToggle[aria-pressed=true]",
  				optional: true
  			},
  			{
  				waitFor: "#TPCookieToggle"
  			},
  			{
  				click: "#TPCookieToggle[aria-pressed=true]",
  				optional: true
  			},
  			{
  				click: "#updateCookieButton"
  			}
  		]
  	},
  	{
  		name: "hu-manity",
  		vendorUrl: "https://hu-manity.co/",
  		prehideSelectors: [
  			"#hu.hu-wrapper"
  		],
  		detectCmp: [
  			{
  				exists: "#hu.hu-visible"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#hu.hu-visible"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "[data-hu-action=cookies-notice-consent-choices-3]"
  			},
  			{
  				waitForThenClick: "#hu-cookies-save"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#hu-cookies-save"
  			}
  		]
  	},
  	{
  		name: "hubspot",
  		detectCmp: [
  			{
  				exists: "#hs-eu-cookie-confirmation"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#hs-eu-cookie-confirmation"
  			}
  		],
  		optIn: [
  			{
  				click: "#hs-eu-confirmation-button"
  			}
  		],
  		optOut: [
  			{
  				click: "#hs-eu-decline-button"
  			}
  		]
  	},
  	{
  		name: "indeed.com",
  		cosmetic: true,
  		prehideSelectors: [
  			"#CookiePrivacyNotice"
  		],
  		detectCmp: [
  			{
  				exists: "#CookiePrivacyNotice"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#CookiePrivacyNotice"
  			}
  		],
  		optIn: [
  			{
  				click: "#CookiePrivacyNotice button[data-gnav-element-name=CookiePrivacyNoticeOk]"
  			}
  		],
  		optOut: [
  			{
  				hide: "#CookiePrivacyNotice"
  			}
  		]
  	},
  	{
  		name: "ing.de",
  		runContext: {
  			urlPattern: "^https://www\\.ing\\.de/"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			"div[slot=\"backdrop\"]"
  		],
  		detectCmp: [
  			{
  				exists: "[data-tag-name=\"ing-cc-dialog-frame\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "[data-tag-name=\"ing-cc-dialog-frame\"]"
  			}
  		],
  		optIn: [
  			{
  				click: [
  					"[data-tag-name=\"ing-cc-dialog-level0\"]",
  					"[data-tag-name=\"ing-cc-button\"][class*=\"accept\"]"
  				]
  			}
  		],
  		optOut: [
  			{
  				click: [
  					"[data-tag-name=\"ing-cc-dialog-level0\"]",
  					"[data-tag-name=\"ing-cc-button\"][class*=\"more\"]"
  				]
  			}
  		]
  	},
  	{
  		name: "instagram",
  		vendorUrl: "https://instagram.com",
  		runContext: {
  			urlPattern: "^https://www\\.instagram\\.com/"
  		},
  		prehideSelectors: [
  			".x78zum5.xdt5ytf.xg6iff7.x1n2onr6:has(._a9--)"
  		],
  		detectCmp: [
  			{
  				exists: ".x1qjc9v5.x9f619.x78zum5.xdt5ytf.x1iyjqo2.xl56j7k"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".x1qjc9v5.x9f619.x78zum5.xdt5ytf.x1iyjqo2.xl56j7k"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "._a9--._a9_0"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "._a9--._a9_1"
  			},
  			{
  				wait: 2000
  			}
  		]
  	},
  	{
  		name: "ionos.de",
  		prehideSelectors: [
  			".privacy-consent--backdrop",
  			".privacy-consent--modal"
  		],
  		detectCmp: [
  			{
  				exists: ".privacy-consent--modal"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".privacy-consent--modal"
  			}
  		],
  		optIn: [
  			{
  				click: "#selectAll"
  			}
  		],
  		optOut: [
  			{
  				click: ".footer-config-link"
  			},
  			{
  				click: "#confirmSelection"
  			}
  		]
  	},
  	{
  		name: "itopvpn.com",
  		cosmetic: true,
  		prehideSelectors: [
  			".pop-cookie"
  		],
  		detectCmp: [
  			{
  				exists: ".pop-cookie"
  			}
  		],
  		detectPopup: [
  			{
  				exists: ".pop-cookie"
  			}
  		],
  		optIn: [
  			{
  				click: "#_pcookie"
  			}
  		],
  		optOut: [
  			{
  				hide: ".pop-cookie"
  			}
  		]
  	},
  	{
  		name: "iubenda",
  		prehideSelectors: [
  			"#iubenda-cs-banner"
  		],
  		detectCmp: [
  			{
  				exists: "#iubenda-cs-banner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".iubenda-cs-accept-btn"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".iubenda-cs-accept-btn"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".iubenda-cs-customize-btn"
  			},
  			{
  				"eval": "EVAL_IUBENDA_0"
  			},
  			{
  				waitForThenClick: "#iubFooterBtn"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_IUBENDA_1"
  			}
  		]
  	},
  	{
  		name: "iWink",
  		prehideSelectors: [
  			"body.cookies-request #cookie-bar"
  		],
  		detectCmp: [
  			{
  				exists: "body.cookies-request #cookie-bar"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "body.cookies-request #cookie-bar"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "body.cookies-request #cookie-bar .allow-cookies"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "body.cookies-request #cookie-bar .disallow-cookies"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_IWINK_TEST"
  			}
  		]
  	},
  	{
  		name: "jdsports",
  		vendorUrl: "https://www.jdsports.co.uk/",
  		runContext: {
  			urlPattern: "^https://(www|m)\\.jdsports\\."
  		},
  		prehideSelectors: [
  			".miniConsent,#PrivacyPolicyBanner"
  		],
  		detectCmp: [
  			{
  				exists: ".miniConsent,#PrivacyPolicyBanner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".miniConsent,#PrivacyPolicyBanner"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".miniConsent .accept-all-cookies"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "#PrivacyPolicyBanner"
  				},
  				then: [
  					{
  						hide: "#PrivacyPolicyBanner"
  					}
  				],
  				"else": [
  					{
  						waitForThenClick: "#cookie-settings"
  					},
  					{
  						waitForThenClick: "#reject-all-cookies"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "johnlewis.com",
  		prehideSelectors: [
  			"div[class^=pecr-cookie-banner-]"
  		],
  		detectCmp: [
  			{
  				exists: "div[class^=pecr-cookie-banner-]"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "div[class^=pecr-cookie-banner-]"
  			}
  		],
  		optOut: [
  			{
  				click: "button[data-test^=manage-cookies]"
  			},
  			{
  				wait: "500"
  			},
  			{
  				click: "label[data-test^=toggle][class*=checked]:not([class*=disabled])",
  				all: true,
  				optional: true
  			},
  			{
  				click: "button[data-test=save-preferences]"
  			}
  		],
  		optIn: [
  			{
  				click: "button[data-test=allow-all]"
  			}
  		]
  	},
  	{
  		name: "jquery.cookieBar",
  		vendorUrl: "https://github.com/kovarp/jquery.cookieBar",
  		prehideSelectors: [
  			".cookie-bar"
  		],
  		cosmetic: true,
  		detectCmp: [
  			{
  				exists: ".cookie-bar .cookie-bar__message,.cookie-bar .cookie-bar__buttons"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-bar .cookie-bar__message,.cookie-bar .cookie-bar__buttons",
  				check: "any"
  			}
  		],
  		optIn: [
  			{
  				click: ".cookie-bar .cookie-bar__btn"
  			}
  		],
  		optOut: [
  			{
  				hide: ".cookie-bar"
  			}
  		],
  		test: [
  			{
  				visible: ".cookie-bar .cookie-bar__message,.cookie-bar .cookie-bar__buttons",
  				check: "none"
  			},
  			{
  				"eval": "EVAL_JQUERY_COOKIEBAR_0"
  			}
  		]
  	},
  	{
  		name: "justwatch.com",
  		prehideSelectors: [
  			".consent-banner"
  		],
  		detectCmp: [
  			{
  				exists: ".consent-banner .consent-banner__actions"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".consent-banner .consent-banner__actions"
  			}
  		],
  		optIn: [
  			{
  				click: ".consent-banner__actions button.basic-button.primary"
  			}
  		],
  		optOut: [
  			{
  				click: ".consent-banner__actions button.basic-button.secondary"
  			},
  			{
  				waitForThenClick: ".consent-modal__footer button.basic-button.secondary"
  			},
  			{
  				waitForThenClick: ".consent-modal ion-content > div > a:nth-child(9)"
  			},
  			{
  				click: "label.consent-switch input[type=checkbox]:checked",
  				all: true,
  				optional: true
  			},
  			{
  				waitForVisible: ".consent-modal__footer button.basic-button.primary"
  			},
  			{
  				click: ".consent-modal__footer button.basic-button.primary"
  			}
  		]
  	},
  	{
  		name: "ketch",
  		vendorUrl: "https://www.ketch.com",
  		runContext: {
  			frame: false,
  			main: true
  		},
  		intermediate: false,
  		prehideSelectors: [
  			"#lanyard_root div[role='dialog']"
  		],
  		detectCmp: [
  			{
  				exists: "#lanyard_root div[role='dialog']"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#lanyard_root div[role='dialog']"
  			}
  		],
  		optIn: [
  			{
  				"if": {
  					exists: "#lanyard_root button[class='confirmButton']"
  				},
  				then: [
  					{
  						waitForThenClick: "#lanyard_root div[class*=buttons] > :nth-child(2)"
  					},
  					{
  						click: "#lanyard_root button[class='confirmButton']"
  					}
  				],
  				"else": [
  					{
  						waitForThenClick: "#lanyard_root div[class*=buttons] > :nth-child(2)"
  					}
  				]
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "#lanyard_root [aria-describedby=banner-description]"
  				},
  				then: [
  					{
  						waitForThenClick: "#lanyard_root div[class*=buttons] > button[class*=secondaryButton], #lanyard_root button[class*=buttons-secondary]",
  						comment: "can be either settings or reject button"
  					}
  				]
  			},
  			{
  				waitFor: "#lanyard_root [aria-describedby=preference-description],#lanyard_root [aria-describedby=modal-description], #ketch-preferences",
  				timeout: 1000,
  				optional: true
  			},
  			{
  				"if": {
  					exists: "#lanyard_root [aria-describedby=preference-description],#lanyard_root [aria-describedby=modal-description], #ketch-preferences"
  				},
  				then: [
  					{
  						waitForThenClick: "#lanyard_root button[class*=rejectButton], #lanyard_root button[class*=rejectAllButton]"
  					},
  					{
  						click: "#lanyard_root button[class*=confirmButton],#lanyard_root div[class*=actions_] > button:nth-child(1), #lanyard_root button[class*=actionButton]"
  					}
  				]
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_KETCH_TEST"
  			}
  		]
  	},
  	{
  		name: "kleinanzeigen-de",
  		runContext: {
  			urlPattern: "^https?://(www\\.)?kleinanzeigen\\.de"
  		},
  		prehideSelectors: [
  			"#gdpr-banner-container"
  		],
  		detectCmp: [
  			{
  				any: [
  					{
  						exists: "#gdpr-banner-container #gdpr-banner [data-testid=gdpr-banner-cmp-button]"
  					},
  					{
  						exists: "#ConsentManagementPage"
  					}
  				]
  			}
  		],
  		detectPopup: [
  			{
  				any: [
  					{
  						visible: "#gdpr-banner-container #gdpr-banner [data-testid=gdpr-banner-cmp-button]"
  					},
  					{
  						visible: "#ConsentManagementPage"
  					}
  				]
  			}
  		],
  		optIn: [
  			{
  				"if": {
  					exists: "#gdpr-banner-container #gdpr-banner"
  				},
  				then: [
  					{
  						click: "#gdpr-banner-container #gdpr-banner [data-testid=gdpr-banner-accept]"
  					}
  				],
  				"else": [
  					{
  						click: "#ConsentManagementPage .Button-primary"
  					}
  				]
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "#gdpr-banner-container #gdpr-banner"
  				},
  				then: [
  					{
  						click: "#gdpr-banner-container #gdpr-banner [data-testid=gdpr-banner-cmp-button]"
  					}
  				],
  				"else": [
  					{
  						click: "#ConsentManagementPage .Button-secondary"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "lightbox",
  		prehideSelectors: [
  			".darken-layer.open,.lightbox.lightbox--cookie-consent"
  		],
  		detectCmp: [
  			{
  				exists: "body.cookie-consent-is-active div.lightbox--cookie-consent > div.lightbox__content > div.cookie-consent[data-jsb]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "body.cookie-consent-is-active div.lightbox--cookie-consent > div.lightbox__content > div.cookie-consent[data-jsb]"
  			}
  		],
  		optOut: [
  			{
  				click: ".cookie-consent__footer > button[type='submit']:not([data-button='selectAll'])"
  			}
  		],
  		optIn: [
  			{
  				click: ".cookie-consent__footer > button[type='submit'][data-button='selectAll']"
  			}
  		]
  	},
  	{
  		name: "lineagrafica",
  		vendorUrl: "https://addons.prestashop.com/en/legal/8734-eu-cookie-law-gdpr-banner-blocker.html",
  		cosmetic: true,
  		prehideSelectors: [
  			"#lgcookieslaw_banner,#lgcookieslaw_modal,.lgcookieslaw-overlay"
  		],
  		detectCmp: [
  			{
  				exists: "#lgcookieslaw_banner,#lgcookieslaw_modal,.lgcookieslaw-overlay"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "#lgcookieslaw_banner,#lgcookieslaw_modal,.lgcookieslaw-overlay"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#lgcookieslaw_accept"
  			}
  		],
  		optOut: [
  			{
  				hide: "#lgcookieslaw_banner,#lgcookieslaw_modal,.lgcookieslaw-overlay"
  			}
  		]
  	},
  	{
  		name: "linkedin.com",
  		prehideSelectors: [
  			".artdeco-global-alert[type=COOKIE_CONSENT]"
  		],
  		detectCmp: [
  			{
  				exists: ".artdeco-global-alert[type=COOKIE_CONSENT]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".artdeco-global-alert[type=COOKIE_CONSENT]"
  			}
  		],
  		optIn: [
  			{
  				waitForVisible: ".artdeco-global-alert[type=COOKIE_CONSENT] button[action-type=ACCEPT]"
  			},
  			{
  				wait: 500
  			},
  			{
  				waitForThenClick: ".artdeco-global-alert[type=COOKIE_CONSENT] button[action-type=ACCEPT]"
  			}
  		],
  		optOut: [
  			{
  				waitForVisible: ".artdeco-global-alert[type=COOKIE_CONSENT] button[action-type=DENY]"
  			},
  			{
  				wait: 500
  			},
  			{
  				waitForThenClick: ".artdeco-global-alert[type=COOKIE_CONSENT] button[action-type=DENY]"
  			}
  		],
  		test: [
  			{
  				waitForVisible: ".artdeco-global-alert[type=COOKIE_CONSENT]",
  				check: "none"
  			}
  		]
  	},
  	{
  		name: "livejasmin",
  		vendorUrl: "https://www.livejasmin.com/",
  		runContext: {
  			urlPattern: "^https://(m|www)\\.livejasmin\\.com/"
  		},
  		prehideSelectors: [
  			"#consent_modal"
  		],
  		detectCmp: [
  			{
  				exists: "#consent_modal"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#consent_modal"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#consent_modal button[data-testid=ButtonStyledButton]:first-of-type"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#consent_modal button[data-testid=ButtonStyledButton]:nth-of-type(2)"
  			},
  			{
  				waitForVisible: "[data-testid=PrivacyPreferenceCenterWithConsentCookieContent]"
  			},
  			{
  				click: "[data-testid=PrivacyPreferenceCenterWithConsentCookieContent] input[data-testid=PrivacyPreferenceCenterWithConsentCookieSwitch]:checked",
  				optional: true,
  				all: true
  			},
  			{
  				waitForThenClick: "[data-testid=PrivacyPreferenceCenterWithConsentCookieContent] button[data-testid=ButtonStyledButton]:last-child"
  			}
  		]
  	},
  	{
  		name: "macpaw.com",
  		cosmetic: true,
  		prehideSelectors: [
  			"div[data-banner=\"cookies\"]"
  		],
  		detectCmp: [
  			{
  				exists: "div[data-banner=\"cookies\"]"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "div[data-banner=\"cookies\"]"
  			}
  		],
  		optIn: [
  			{
  				click: "button[data-banner-close=\"cookies\"]"
  			}
  		],
  		optOut: [
  			{
  				hide: "div[data-banner=\"cookies\"]"
  			}
  		]
  	},
  	{
  		name: "marksandspencer.com",
  		cosmetic: true,
  		detectCmp: [
  			{
  				exists: ".navigation-cookiebbanner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".navigation-cookiebbanner"
  			}
  		],
  		optOut: [
  			{
  				hide: ".navigation-cookiebbanner"
  			}
  		],
  		optIn: [
  			{
  				click: ".navigation-cookiebbanner__submit"
  			}
  		]
  	},
  	{
  		name: "mediamarkt.de",
  		prehideSelectors: [
  			"div[aria-labelledby=pwa-consent-layer-title]",
  			"div[class^=StyledConsentLayerWrapper-]"
  		],
  		detectCmp: [
  			{
  				exists: "div[aria-labelledby^=pwa-consent-layer-title]"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "div[aria-labelledby^=pwa-consent-layer-title]"
  			}
  		],
  		optOut: [
  			{
  				click: "button[data-test^=pwa-consent-layer-deny-all]"
  			}
  		],
  		optIn: [
  			{
  				click: "button[data-test^=pwa-consent-layer-accept-all"
  			}
  		]
  	},
  	{
  		name: "Mediavine",
  		prehideSelectors: [
  			"[data-name=\"mediavine-gdpr-cmp\"]"
  		],
  		detectCmp: [
  			{
  				exists: "[data-name=\"mediavine-gdpr-cmp\"]"
  			}
  		],
  		detectPopup: [
  			{
  				wait: 500
  			},
  			{
  				visible: "[data-name=\"mediavine-gdpr-cmp\"]"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "[data-name=\"mediavine-gdpr-cmp\"] [format=\"primary\"]"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "[data-name=\"mediavine-gdpr-cmp\"] [data-view=\"manageSettings\"]"
  			},
  			{
  				waitFor: "[data-name=\"mediavine-gdpr-cmp\"] input[type=checkbox]"
  			},
  			{
  				"eval": "EVAL_MEDIAVINE_0",
  				optional: true
  			},
  			{
  				click: "[data-name=\"mediavine-gdpr-cmp\"] [format=\"secondary\"]"
  			}
  		]
  	},
  	{
  		name: "microsoft.com",
  		prehideSelectors: [
  			"#wcpConsentBannerCtrl"
  		],
  		detectCmp: [
  			{
  				exists: "#wcpConsentBannerCtrl"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "#wcpConsentBannerCtrl"
  			}
  		],
  		optOut: [
  			{
  				"eval": "EVAL_MICROSOFT_0"
  			}
  		],
  		optIn: [
  			{
  				"eval": "EVAL_MICROSOFT_1"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_MICROSOFT_2"
  			}
  		]
  	},
  	{
  		name: "midway-usa",
  		runContext: {
  			urlPattern: "^https://www\\.midwayusa\\.com/"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			"#cookie-container"
  		],
  		detectCmp: [
  			{
  				exists: [
  					"div[aria-label=\"Cookie Policy Banner\"]"
  				]
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookie-container"
  			}
  		],
  		optIn: [
  			{
  				click: "button#cookie-btn"
  			}
  		],
  		optOut: [
  			{
  				hide: "div[aria-label=\"Cookie Policy Banner\"]"
  			}
  		]
  	},
  	{
  		name: "moneysavingexpert.com",
  		detectCmp: [
  			{
  				exists: "dialog[data-testid=accept-our-cookies-dialog]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "dialog[data-testid=accept-our-cookies-dialog]"
  			}
  		],
  		optIn: [
  			{
  				click: "#banner-accept"
  			}
  		],
  		optOut: [
  			{
  				click: "#banner-manage"
  			},
  			{
  				click: "#pc-confirm"
  			}
  		]
  	},
  	{
  		name: "monzo.com",
  		prehideSelectors: [
  			".cookie-alert, cookie-alert__content"
  		],
  		detectCmp: [
  			{
  				exists: "div.cookie-alert[role=\"dialog\"]"
  			},
  			{
  				exists: "a[href*=\"monzo\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-alert__content"
  			}
  		],
  		optIn: [
  			{
  				click: ".js-accept-cookie-policy"
  			}
  		],
  		optOut: [
  			{
  				click: ".js-decline-cookie-policy"
  			}
  		]
  	},
  	{
  		name: "Moove",
  		prehideSelectors: [
  			"#moove_gdpr_cookie_info_bar"
  		],
  		detectCmp: [
  			{
  				exists: "#moove_gdpr_cookie_info_bar"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#moove_gdpr_cookie_info_bar:not(.moove-gdpr-info-bar-hidden)"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".moove-gdpr-infobar-allow-all"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "#moove_gdpr_cookie_info_bar .change-settings-button"
  				},
  				then: [
  					{
  						click: "#moove_gdpr_cookie_info_bar .change-settings-button"
  					},
  					{
  						waitForVisible: "#moove_gdpr_cookie_modal"
  					},
  					{
  						"eval": "EVAL_MOOVE_0"
  					},
  					{
  						click: ".moove-gdpr-modal-save-settings"
  					}
  				],
  				"else": [
  					{
  						hide: "#moove_gdpr_cookie_info_bar"
  					}
  				]
  			}
  		],
  		test: [
  			{
  				visible: "#moove_gdpr_cookie_info_bar",
  				check: "none"
  			}
  		]
  	},
  	{
  		name: "national-lottery.co.uk",
  		detectCmp: [
  			{
  				exists: ".cuk_cookie_consent"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cuk_cookie_consent",
  				check: "any"
  			}
  		],
  		optOut: [
  			{
  				click: ".cuk_cookie_consent_manage_pref"
  			},
  			{
  				click: ".cuk_cookie_consent_save_pref"
  			},
  			{
  				click: ".cuk_cookie_consent_close"
  			}
  		],
  		optIn: [
  			{
  				click: ".cuk_cookie_consent_accept_all"
  			}
  		]
  	},
  	{
  		name: "nba.com",
  		runContext: {
  			urlPattern: "^https://(www\\.)?nba.com/"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			"#onetrust-banner-sdk"
  		],
  		detectCmp: [
  			{
  				exists: "#onetrust-banner-sdk"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#onetrust-banner-sdk"
  			}
  		],
  		optIn: [
  			{
  				click: "#onetrust-accept-btn-handler"
  			}
  		],
  		optOut: [
  			{
  				hide: "#onetrust-banner-sdk"
  			}
  		]
  	},
  	{
  		name: "netflix.de",
  		detectCmp: [
  			{
  				exists: "#cookie-disclosure"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-disclosure-message",
  				check: "any"
  			}
  		],
  		optIn: [
  			{
  				click: ".btn-accept"
  			}
  		],
  		optOut: [
  			{
  				hide: "#cookie-disclosure"
  			},
  			{
  				click: ".btn-reject"
  			}
  		]
  	},
  	{
  		name: "nhs.uk",
  		prehideSelectors: [
  			"#nhsuk-cookie-banner"
  		],
  		detectCmp: [
  			{
  				exists: "#nhsuk-cookie-banner"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "#nhsuk-cookie-banner"
  			}
  		],
  		optOut: [
  			{
  				click: "#nhsuk-cookie-banner__link_accept"
  			}
  		],
  		optIn: [
  			{
  				click: "#nhsuk-cookie-banner__link_accept_analytics"
  			}
  		]
  	},
  	{
  		name: "notice-cookie",
  		prehideSelectors: [
  			".button--notice"
  		],
  		cosmetic: true,
  		detectCmp: [
  			{
  				exists: ".notice--cookie"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".notice--cookie"
  			}
  		],
  		optIn: [
  			{
  				click: ".button--notice"
  			}
  		],
  		optOut: [
  			{
  				hide: ".notice--cookie"
  			}
  		]
  	},
  	{
  		name: "nrk.no",
  		cosmetic: true,
  		prehideSelectors: [
  			".nrk-masthead__info-banner--cookie"
  		],
  		detectCmp: [
  			{
  				exists: ".nrk-masthead__info-banner--cookie"
  			}
  		],
  		detectPopup: [
  			{
  				exists: ".nrk-masthead__info-banner--cookie"
  			}
  		],
  		optIn: [
  			{
  				click: "div.nrk-masthead__info-banner--cookie button > span:has(+ svg.nrk-close)"
  			}
  		],
  		optOut: [
  			{
  				hide: ".nrk-masthead__info-banner--cookie"
  			}
  		]
  	},
  	{
  		name: "obi.de",
  		prehideSelectors: [
  			".disc-cp--active"
  		],
  		detectCmp: [
  			{
  				exists: ".disc-cp-modal__modal"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".disc-cp-modal__modal"
  			}
  		],
  		optIn: [
  			{
  				click: ".js-disc-cp-accept-all"
  			}
  		],
  		optOut: [
  			{
  				click: ".js-disc-cp-deny-all"
  			}
  		]
  	},
  	{
  		name: "om",
  		vendorUrl: "https://olli-machts.de/en/extension/cookie-manager",
  		prehideSelectors: [
  			".tx-om-cookie-consent"
  		],
  		detectCmp: [
  			{
  				exists: ".tx-om-cookie-consent .active[data-omcookie-panel]"
  			}
  		],
  		detectPopup: [
  			{
  				exists: ".tx-om-cookie-consent .active[data-omcookie-panel]"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "[data-omcookie-panel-save=all]"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "[data-omcookie-panel-save=min]"
  				},
  				then: [
  					{
  						waitForThenClick: "[data-omcookie-panel-save=min]"
  					}
  				],
  				"else": [
  					{
  						click: "input[data-omcookie-panel-grp]:checked:not(:disabled)",
  						all: true,
  						optional: true
  					},
  					{
  						waitForThenClick: "[data-omcookie-panel-save=save]"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "onlyFans.com",
  		runContext: {
  			urlPattern: "^https://onlyfans\\.com/"
  		},
  		prehideSelectors: [
  			"div.b-cookies-informer"
  		],
  		detectCmp: [
  			{
  				exists: "div.b-cookies-informer"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "div.b-cookies-informer"
  			}
  		],
  		optIn: [
  			{
  				click: "div.b-cookies-informer__nav > button:nth-child(2)"
  			}
  		],
  		optOut: [
  			{
  				click: "div.b-cookies-informer__nav > button:nth-child(1)"
  			},
  			{
  				"if": {
  					exists: "div.b-cookies-informer__switchers"
  				},
  				then: [
  					{
  						click: "div.b-cookies-informer__switchers input:not([disabled])",
  						all: true
  					},
  					{
  						click: "div.b-cookies-informer__nav > button"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "openli",
  		vendorUrl: "https://openli.com",
  		prehideSelectors: [
  			".legalmonster-cleanslate"
  		],
  		detectCmp: [
  			{
  				exists: ".legalmonster-cleanslate"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".legalmonster-cleanslate #lm-cookie-wall-container",
  				check: "any"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#lm-accept-all"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#lm-accept-necessary"
  			}
  		]
  	},
  	{
  		name: "opera.com",
  		vendorUrl: "https://unknown",
  		cosmetic: false,
  		runContext: {
  			main: true,
  			frame: false
  		},
  		intermediate: false,
  		prehideSelectors: [
  		],
  		detectCmp: [
  			{
  				exists: "#cookie-consent .manage-cookies__btn"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookie-consent .cookie-basic-consent__btn"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#cookie-consent .cookie-basic-consent__btn"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#cookie-consent .manage-cookies__btn"
  			},
  			{
  				waitForThenClick: "#cookie-consent .active.marketing_option_switch.cookie-consent__switch",
  				all: true
  			},
  			{
  				waitForThenClick: "#cookie-consent .cookie-selection__btn"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_OPERA_0"
  			}
  		]
  	},
  	{
  		name: "osano",
  		prehideSelectors: [
  			".osano-cm-window,.osano-cm-dialog"
  		],
  		detectCmp: [
  			{
  				exists: ".osano-cm-window"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".osano-cm-dialog"
  			}
  		],
  		optIn: [
  			{
  				click: ".osano-cm-accept-all",
  				optional: true
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".osano-cm-denyAll"
  			}
  		]
  	},
  	{
  		name: "otto.de",
  		prehideSelectors: [
  			".cookieBanner--visibility"
  		],
  		detectCmp: [
  			{
  				exists: ".cookieBanner--visibility"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookieBanner__wrapper"
  			}
  		],
  		optIn: [
  			{
  				click: ".js_cookieBannerPermissionButton"
  			}
  		],
  		optOut: [
  			{
  				click: ".js_cookieBannerProhibitionButton"
  			}
  		]
  	},
  	{
  		name: "ourworldindata",
  		vendorUrl: "https://ourworldindata.org/",
  		runContext: {
  			urlPattern: "^https://ourworldindata\\.org/"
  		},
  		prehideSelectors: [
  			".cookie-manager"
  		],
  		detectCmp: [
  			{
  				exists: ".cookie-manager"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-manager .cookie-notice.open"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".cookie-notice [data-test=accept]"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".cookie-notice [data-test=reject]"
  			}
  		]
  	},
  	{
  		name: "pabcogypsum",
  		vendorUrl: "https://unknown",
  		prehideSelectors: [
  			".js-cookie-notice:has(#cookie_settings-form)"
  		],
  		detectCmp: [
  			{
  				exists: ".js-cookie-notice #cookie_settings-form"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".js-cookie-notice #cookie_settings-form"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".js-cookie-notice button[value=allow]"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".js-cookie-notice button[value=disable]"
  			}
  		]
  	},
  	{
  		name: "paypal-us",
  		prehideSelectors: [
  			"#ccpaCookieContent_wrapper, article.ppvx_modal--overpanel"
  		],
  		detectCmp: [
  			{
  				exists: "#ccpaCookieBanner, .privacy-sheet-content"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "#ccpaCookieBanner, .privacy-sheet-content"
  			}
  		],
  		optIn: [
  			{
  				click: "#acceptAllButton"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "a#manageCookiesLink"
  				},
  				then: [
  					{
  						click: "a#manageCookiesLink"
  					}
  				],
  				"else": [
  					{
  						waitForVisible: ".privacy-sheet-content #formContent"
  					},
  					{
  						click: "#formContent .cookiepref-11m2iee-checkbox_base input:checked",
  						all: true,
  						optional: true
  					},
  					{
  						click: ".confirmCookie #submitCookiesBtn"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "paypal.com",
  		prehideSelectors: [
  			"#gdprCookieBanner"
  		],
  		detectCmp: [
  			{
  				exists: "#gdprCookieBanner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#gdprCookieContent_wrapper"
  			}
  		],
  		optIn: [
  			{
  				click: "#acceptAllButton"
  			}
  		],
  		optOut: [
  			{
  				wait: 200
  			},
  			{
  				click: ".gdprCookieBanner_decline-button"
  			}
  		],
  		test: [
  			{
  				wait: 500
  			},
  			{
  				"eval": "EVAL_PAYPAL_0"
  			}
  		]
  	},
  	{
  		name: "pinetools.com",
  		cosmetic: true,
  		prehideSelectors: [
  			"#aviso_cookies"
  		],
  		detectCmp: [
  			{
  				exists: "#aviso_cookies"
  			}
  		],
  		detectPopup: [
  			{
  				exists: ".lang_en #aviso_cookies"
  			}
  		],
  		optIn: [
  			{
  				click: "#aviso_cookies .a_boton_cerrar"
  			}
  		],
  		optOut: [
  			{
  				hide: "#aviso_cookies"
  			}
  		]
  	},
  	{
  		name: "pmc",
  		cosmetic: true,
  		prehideSelectors: [
  			"#pmc-pp-tou--notice"
  		],
  		detectCmp: [
  			{
  				exists: "#pmc-pp-tou--notice"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#pmc-pp-tou--notice"
  			}
  		],
  		optIn: [
  			{
  				click: "span.pmc-pp-tou--notice-close-btn"
  			}
  		],
  		optOut: [
  			{
  				hide: "#pmc-pp-tou--notice"
  			}
  		]
  	},
  	{
  		name: "pornhub.com",
  		runContext: {
  			urlPattern: "^https://(www\\.)?pornhub\\.com/"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			".cookiesBanner"
  		],
  		detectCmp: [
  			{
  				exists: ".cookiesBanner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookiesBanner"
  			}
  		],
  		optIn: [
  			{
  				click: ".cookiesBanner .okButton"
  			}
  		],
  		optOut: [
  			{
  				hide: ".cookiesBanner"
  			}
  		]
  	},
  	{
  		name: "pornpics.com",
  		cosmetic: true,
  		prehideSelectors: [
  			"#cookie-contract"
  		],
  		detectCmp: [
  			{
  				exists: "#cookie-contract"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookie-contract"
  			}
  		],
  		optIn: [
  			{
  				click: "#cookie-contract .icon-cross"
  			}
  		],
  		optOut: [
  			{
  				hide: "#cookie-contract"
  			}
  		]
  	},
  	{
  		name: "PrimeBox CookieBar",
  		prehideSelectors: [
  			"#cookie-bar"
  		],
  		detectCmp: [
  			{
  				exists: "#cookie-bar .cb-enable,#cookie-bar .cb-disable,#cookie-bar .cb-policy"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookie-bar .cb-enable,#cookie-bar .cb-disable,#cookie-bar .cb-policy",
  				check: "any"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#cookie-bar .cb-enable"
  			}
  		],
  		optOut: [
  			{
  				click: "#cookie-bar .cb-disable",
  				optional: true
  			},
  			{
  				hide: "#cookie-bar"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_PRIMEBOX_0"
  			}
  		]
  	},
  	{
  		name: "privacymanager.io",
  		prehideSelectors: [
  			"#gdpr-consent-tool-wrapper",
  			"iframe[src^=\"https://cmp-consent-tool.privacymanager.io\"]"
  		],
  		runContext: {
  			urlPattern: "^https://cmp-consent-tool\\.privacymanager\\.io/",
  			main: false,
  			frame: true
  		},
  		detectCmp: [
  			{
  				exists: "button#save"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "button#save"
  			}
  		],
  		optIn: [
  			{
  				click: "button#save"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "#denyAll"
  				},
  				then: [
  					{
  						click: "#denyAll"
  					},
  					{
  						waitForThenClick: ".okButton"
  					}
  				],
  				"else": [
  					{
  						waitForThenClick: "#manageSettings"
  					},
  					{
  						waitFor: ".purposes-overview-list"
  					},
  					{
  						waitFor: "button#saveAndExit"
  					},
  					{
  						click: "span[role=checkbox][aria-checked=true]",
  						all: true,
  						optional: true
  					},
  					{
  						click: "button#saveAndExit"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "productz.com",
  		vendorUrl: "https://productz.com/",
  		runContext: {
  			urlPattern: "^https://productz\\.com/"
  		},
  		prehideSelectors: [
  		],
  		detectCmp: [
  			{
  				exists: ".c-modal.is-active"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".c-modal.is-active"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".c-modal.is-active .is-accept"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".c-modal.is-active .is-dismiss"
  			}
  		]
  	},
  	{
  		name: "pubtech",
  		prehideSelectors: [
  			"#pubtech-cmp"
  		],
  		detectCmp: [
  			{
  				exists: "#pubtech-cmp"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#pubtech-cmp #pt-actions"
  			}
  		],
  		optIn: [
  			{
  				"if": {
  					exists: "#pt-accept-all"
  				},
  				then: [
  					{
  						click: "#pubtech-cmp #pt-actions #pt-accept-all"
  					}
  				],
  				"else": [
  					{
  						click: "#pubtech-cmp #pt-actions button:nth-of-type(2)"
  					}
  				]
  			}
  		],
  		optOut: [
  			{
  				click: "#pubtech-cmp #pt-close"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_PUBTECH_0"
  			}
  		]
  	},
  	{
  		name: "quantcast",
  		prehideSelectors: [
  			"#qc-cmp2-main,#qc-cmp2-container"
  		],
  		detectCmp: [
  			{
  				exists: "#qc-cmp2-container"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#qc-cmp2-ui"
  			}
  		],
  		optOut: [
  			{
  				click: ".qc-cmp2-summary-buttons > button[mode=\"secondary\"]"
  			},
  			{
  				waitFor: "#qc-cmp2-ui"
  			},
  			{
  				click: ".qc-cmp2-toggle-switch > button[aria-checked=\"true\"]",
  				all: true,
  				optional: true
  			},
  			{
  				click: ".qc-cmp2-main button[aria-label=\"REJECT ALL\"]",
  				optional: true
  			},
  			{
  				waitForThenClick: ".qc-cmp2-main button[aria-label=\"SAVE & EXIT\"],.qc-cmp2-buttons-desktop > button[mode=\"primary\"]",
  				timeout: 5000
  			}
  		],
  		optIn: [
  			{
  				click: ".qc-cmp2-summary-buttons > button[mode=\"primary\"]"
  			}
  		]
  	},
  	{
  		name: "reddit.com",
  		runContext: {
  			urlPattern: "^https://www\\.reddit\\.com/"
  		},
  		prehideSelectors: [
  			"[bundlename=reddit_cookie_banner]"
  		],
  		detectCmp: [
  			{
  				exists: "reddit-cookie-banner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "reddit-cookie-banner"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: [
  					"reddit-cookie-banner",
  					"#accept-all-cookies-button > button"
  				]
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: [
  					"reddit-cookie-banner",
  					"#reject-nonessential-cookies-button > button"
  				]
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_REDDIT_0"
  			}
  		]
  	},
  	{
  		name: "roblox",
  		vendorUrl: "https://roblox.com",
  		cosmetic: false,
  		runContext: {
  			main: true,
  			frame: false,
  			urlPattern: "^https://(www\\.)?roblox\\.com/"
  		},
  		prehideSelectors: [
  		],
  		detectCmp: [
  			{
  				exists: ".cookie-banner-wrapper"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-banner-wrapper .cookie-banner"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".cookie-banner-wrapper button.btn-cta-lg"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".cookie-banner-wrapper button.btn-secondary-lg"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_ROBLOX_TEST"
  			}
  		]
  	},
  	{
  		name: "rog-forum.asus.com",
  		runContext: {
  			urlPattern: "^https://rog-forum\\.asus\\.com/"
  		},
  		prehideSelectors: [
  			"#cookie-policy-info"
  		],
  		detectCmp: [
  			{
  				exists: "#cookie-policy-info"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookie-policy-info"
  			}
  		],
  		optIn: [
  			{
  				click: "div.cookie-btn-box > div[aria-label=\"Accept\"]"
  			}
  		],
  		optOut: [
  			{
  				click: "div.cookie-btn-box > div[aria-label=\"Reject\"]"
  			},
  			{
  				waitForThenClick: ".cookie-policy-lightbox-bottom > div[aria-label=\"Save Settings\"]"
  			}
  		]
  	},
  	{
  		name: "roofingmegastore.co.uk",
  		runContext: {
  			urlPattern: "^https://(www\\.)?roofingmegastore\\.co\\.uk"
  		},
  		prehideSelectors: [
  			"#m-cookienotice"
  		],
  		detectCmp: [
  			{
  				exists: "#m-cookienotice"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#m-cookienotice"
  			}
  		],
  		optIn: [
  			{
  				click: "#accept-cookies"
  			}
  		],
  		optOut: [
  			{
  				click: "#manage-cookies"
  			},
  			{
  				waitForThenClick: "#accept-selected"
  			}
  		]
  	},
  	{
  		name: "samsung.com",
  		runContext: {
  			urlPattern: "^https://www\\.samsung\\.com/"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			"div.cookie-bar"
  		],
  		detectCmp: [
  			{
  				exists: "div.cookie-bar"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "div.cookie-bar"
  			}
  		],
  		optIn: [
  			{
  				click: "div.cookie-bar__manage > a"
  			}
  		],
  		optOut: [
  			{
  				hide: "div.cookie-bar"
  			}
  		]
  	},
  	{
  		name: "setapp.com",
  		vendorUrl: "https://setapp.com/",
  		cosmetic: true,
  		runContext: {
  			urlPattern: "^https://setapp\\.com/"
  		},
  		prehideSelectors: [
  		],
  		detectCmp: [
  			{
  				exists: ".cookie-banner.js-cookie-banner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-banner.js-cookie-banner"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".cookie-banner.js-cookie-banner button"
  			}
  		],
  		optOut: [
  			{
  				hide: ".cookie-banner.js-cookie-banner"
  			}
  		]
  	},
  	{
  		name: "sibbo",
  		prehideSelectors: [
  			"sibbo-cmp-layout"
  		],
  		detectCmp: [
  			{
  				exists: "sibbo-cmp-layout"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#rejectAllMain"
  			}
  		],
  		optIn: [
  			{
  				click: "#acceptAllMain"
  			}
  		],
  		optOut: [
  			{
  				click: "#rejectAllMain"
  			}
  		]
  	},
  	{
  		name: "similarweb.com",
  		cosmetic: true,
  		prehideSelectors: [
  			".app-cookies-notification"
  		],
  		detectCmp: [
  			{
  				exists: ".app-cookies-notification"
  			}
  		],
  		detectPopup: [
  			{
  				exists: ".app-layout .app-cookies-notification"
  			}
  		],
  		optIn: [
  			{
  				click: "button.app-cookies-notification__dismiss"
  			}
  		],
  		optOut: [
  			{
  				hide: ".app-layout .app-cookies-notification"
  			}
  		]
  	},
  	{
  		name: "Sirdata",
  		cosmetic: false,
  		prehideSelectors: [
  			"#sd-cmp"
  		],
  		detectCmp: [
  			{
  				exists: "#sd-cmp"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#sd-cmp"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#sd-cmp .sd-cmp-3cRQ2"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: [
  					"#sd-cmp",
  					"xpath///span[contains(., 'Do not accept') or contains(., 'Acceptera inte') or contains(., 'No aceptar') or contains(., 'Ikke acceptere') or contains(., 'Nicht akzeptieren') or contains(., 'Не приемам') or contains(., 'Να μην γίνει αποδοχή') or contains(., 'Niet accepteren') or contains(., 'Nepřijímat') or contains(., 'Nie akceptuj') or contains(., 'Nu acceptați') or contains(., 'Não aceitar') or contains(., 'Continuer sans accepter') or contains(., 'Non accettare') or contains(., 'Nem fogad el')]"
  				]
  			}
  		]
  	},
  	{
  		name: "snigel",
  		detectCmp: [
  			{
  				exists: ".snigel-cmp-framework"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".snigel-cmp-framework"
  			}
  		],
  		optOut: [
  			{
  				click: "#sn-b-custom"
  			},
  			{
  				click: "#sn-b-save"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_SNIGEL_0"
  			}
  		],
  		optIn: [
  			{
  				click: ".snigel-cmp-framework #accept-choices"
  			}
  		]
  	},
  	{
  		name: "steampowered.com",
  		detectCmp: [
  			{
  				exists: ".cookiepreferences_popup"
  			},
  			{
  				visible: ".cookiepreferences_popup"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookiepreferences_popup"
  			}
  		],
  		optOut: [
  			{
  				click: "#rejectAllButton"
  			}
  		],
  		optIn: [
  			{
  				click: "#acceptAllButton"
  			}
  		],
  		test: [
  			{
  				wait: 1000
  			},
  			{
  				"eval": "EVAL_STEAMPOWERED_0"
  			}
  		]
  	},
  	{
  		name: "strato.de",
  		prehideSelectors: [
  			".consent__wrapper"
  		],
  		runContext: {
  			urlPattern: "^https://www\\.strato\\.de/"
  		},
  		detectCmp: [
  			{
  				exists: ".consent"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".consent"
  			}
  		],
  		optIn: [
  			{
  				click: "button.consentAgree"
  			}
  		],
  		optOut: [
  			{
  				click: "button.consentSettings"
  			},
  			{
  				waitForThenClick: "button#consentSubmit"
  			}
  		]
  	},
  	{
  		name: "svt.se",
  		vendorUrl: "https://www.svt.se/",
  		runContext: {
  			urlPattern: "^https://www\\.svt\\.se/"
  		},
  		prehideSelectors: [
  			"[class*=CookieConsent__root___]"
  		],
  		detectCmp: [
  			{
  				exists: "[class*=CookieConsent__root___]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "[class*=CookieConsent__modal___]"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "[class*=CookieConsent__modal___] > div > button[class*=primary]"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "[class*=CookieConsent__modal___] > div > button[class*=secondary]:nth-child(2)"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_SVT_TEST"
  			}
  		]
  	},
  	{
  		name: "takealot.com",
  		cosmetic: true,
  		prehideSelectors: [
  			"div[class^=\"cookies-banner-module_\"]"
  		],
  		detectCmp: [
  			{
  				exists: "div[class^=\"cookies-banner-module_cookie-banner_\"]"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "div[class^=\"cookies-banner-module_cookie-banner_\"]"
  			}
  		],
  		optIn: [
  			{
  				click: "button[class*=\"cookies-banner-module_dismiss-button_\"]"
  			}
  		],
  		optOut: [
  			{
  				hide: "div[class^=\"cookies-banner-module_\"]"
  			},
  			{
  				"if": {
  					exists: "div[class^=\"cookies-banner-module_small-cookie-banner_\"]"
  				},
  				then: [
  					{
  						"eval": "EVAL_TAKEALOT_0"
  					}
  				],
  				"else": [
  				]
  			}
  		]
  	},
  	{
  		name: "tarteaucitron.js",
  		prehideSelectors: [
  			"#tarteaucitronRoot"
  		],
  		detectCmp: [
  			{
  				exists: "#tarteaucitronRoot"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#tarteaucitronRoot #tarteaucitronAlertBig",
  				check: "any"
  			}
  		],
  		optIn: [
  			{
  				"eval": "EVAL_TARTEAUCITRON_1"
  			}
  		],
  		optOut: [
  			{
  				"eval": "EVAL_TARTEAUCITRON_0"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_TARTEAUCITRON_2",
  				comment: "sometimes there are required categories, so we check that at least something is false"
  			}
  		]
  	},
  	{
  		name: "taunton",
  		vendorUrl: "https://www.taunton.com/",
  		prehideSelectors: [
  			"#taunton-user-consent__overlay"
  		],
  		detectCmp: [
  			{
  				exists: "#taunton-user-consent__overlay"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "#taunton-user-consent__overlay:not([aria-hidden=true])"
  			}
  		],
  		optIn: [
  			{
  				click: "#taunton-user-consent__toolbar input[type=checkbox]:not(:checked)"
  			},
  			{
  				click: "#taunton-user-consent__toolbar button[type=submit]"
  			}
  		],
  		optOut: [
  			{
  				click: "#taunton-user-consent__toolbar input[type=checkbox]:checked",
  				optional: true,
  				all: true
  			},
  			{
  				click: "#taunton-user-consent__toolbar button[type=submit]"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_TAUNTON_TEST"
  			}
  		]
  	},
  	{
  		name: "Tealium",
  		prehideSelectors: [
  			"#__tealiumGDPRecModal,#__tealiumGDPRcpPrefs,#__tealiumImplicitmodal,#consent-layer"
  		],
  		detectCmp: [
  			{
  				exists: "#__tealiumGDPRecModal *,#__tealiumGDPRcpPrefs *,#__tealiumImplicitmodal *"
  			},
  			{
  				"eval": "EVAL_TEALIUM_0"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#__tealiumGDPRecModal *,#__tealiumGDPRcpPrefs *,#__tealiumImplicitmodal *",
  				check: "any"
  			}
  		],
  		optOut: [
  			{
  				"eval": "EVAL_TEALIUM_1"
  			},
  			{
  				"eval": "EVAL_TEALIUM_DONOTSELL"
  			},
  			{
  				hide: "#__tealiumGDPRecModal,#__tealiumGDPRcpPrefs,#__tealiumImplicitmodal"
  			},
  			{
  				waitForThenClick: "#cm-acceptNone,.js-accept-essential-cookies",
  				timeout: 1000,
  				optional: true
  			}
  		],
  		optIn: [
  			{
  				hide: "#__tealiumGDPRecModal,#__tealiumGDPRcpPrefs"
  			},
  			{
  				"eval": "EVAL_TEALIUM_2"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_TEALIUM_3"
  			},
  			{
  				"eval": "EVAL_TEALIUM_DONOTSELL_CHECK"
  			},
  			{
  				visible: "#__tealiumGDPRecModal,#__tealiumGDPRcpPrefs",
  				check: "none"
  			}
  		]
  	},
  	{
  		name: "temu",
  		vendorUrl: "https://temu.com",
  		runContext: {
  			urlPattern: "^https://[^/]*temu\\.com/"
  		},
  		prehideSelectors: [
  			"._2d-8vq-W,._1UdBUwni"
  		],
  		detectCmp: [
  			{
  				exists: "._3YCsmIaS"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "._3YCsmIaS"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "._3fKiu5wx._3zN5SumS._3tAK973O.IYOfhWEs.VGNGF1pA"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "._3fKiu5wx._1_XToJBF._3tAK973O.IYOfhWEs.VGNGF1pA"
  			}
  		]
  	},
  	{
  		name: "Termly",
  		prehideSelectors: [
  			"#termly-code-snippet-support"
  		],
  		detectCmp: [
  			{
  				exists: "#termly-code-snippet-support"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#termly-code-snippet-support div"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "[data-tid=\"banner-accept\"]"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "[data-tid=\"banner-decline\"]"
  				},
  				then: [
  					{
  						click: "[data-tid=\"banner-decline\"]"
  					}
  				],
  				"else": [
  					{
  						click: ".t-preference-button"
  					},
  					{
  						wait: 500
  					},
  					{
  						"if": {
  							exists: ".t-declineAllButton"
  						},
  						then: [
  							{
  								click: ".t-declineAllButton"
  							}
  						],
  						"else": [
  							{
  								waitForThenClick: ".t-preference-modal input[type=checkbox][checked]:not([disabled])",
  								all: true
  							},
  							{
  								waitForThenClick: ".t-saveButton"
  							}
  						]
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "termsfeed",
  		vendorUrl: "https://termsfeed.com",
  		comment: "v4.x.x",
  		prehideSelectors: [
  			".termsfeed-com---nb"
  		],
  		detectCmp: [
  			{
  				exists: ".termsfeed-com---nb"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".termsfeed-com---nb"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".cc-nb-okagree"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".cc-nb-reject"
  			}
  		]
  	},
  	{
  		name: "termsfeed3",
  		vendorUrl: "https://termsfeed.com",
  		comment: "v3.x.x",
  		prehideSelectors: [
  			".cc_dialog.cc_css_reboot,.cc_overlay_lock"
  		],
  		detectCmp: [
  			{
  				exists: ".cc_dialog.cc_css_reboot"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cc_dialog.cc_css_reboot"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".cc_dialog.cc_css_reboot .cc_b_ok"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: ".cc_dialog.cc_css_reboot .cc_b_cp"
  				},
  				then: [
  					{
  						click: ".cc_dialog.cc_css_reboot .cc_b_cp"
  					},
  					{
  						waitForVisible: ".cookie-consent-preferences-dialog .cc_cp_f_save button"
  					},
  					{
  						waitForThenClick: ".cookie-consent-preferences-dialog .cc_cp_f_save button"
  					}
  				],
  				"else": [
  					{
  						hide: ".cc_dialog.cc_css_reboot,.cc_overlay_lock"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "Test page cosmetic CMP",
  		cosmetic: true,
  		prehideSelectors: [
  			"#privacy-test-page-cmp-test-prehide"
  		],
  		detectCmp: [
  			{
  				exists: "#privacy-test-page-cmp-test-banner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#privacy-test-page-cmp-test-banner"
  			}
  		],
  		optIn: [
  			{
  				waitFor: "#accept-all"
  			},
  			{
  				click: "#accept-all"
  			}
  		],
  		optOut: [
  			{
  				hide: "#privacy-test-page-cmp-test-banner"
  			}
  		],
  		test: [
  			{
  				wait: 500
  			},
  			{
  				"eval": "EVAL_TESTCMP_COSMETIC_0"
  			}
  		]
  	},
  	{
  		name: "Test page CMP",
  		prehideSelectors: [
  			"#reject-all"
  		],
  		detectCmp: [
  			{
  				exists: "#privacy-test-page-cmp-test"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#privacy-test-page-cmp-test"
  			}
  		],
  		optIn: [
  			{
  				waitFor: "#accept-all"
  			},
  			{
  				click: "#accept-all"
  			}
  		],
  		optOut: [
  			{
  				waitFor: "#reject-all"
  			},
  			{
  				click: "#reject-all"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_TESTCMP_0"
  			}
  		]
  	},
  	{
  		name: "thalia.de",
  		prehideSelectors: [
  			".consent-banner-box"
  		],
  		detectCmp: [
  			{
  				exists: "consent-banner[component=consent-banner]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".consent-banner-box"
  			}
  		],
  		optIn: [
  			{
  				click: ".button-zustimmen"
  			}
  		],
  		optOut: [
  			{
  				click: "button[data-consent=disagree]"
  			}
  		]
  	},
  	{
  		name: "thefreedictionary.com",
  		prehideSelectors: [
  			"#cmpBanner"
  		],
  		detectCmp: [
  			{
  				exists: "#cmpBanner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cmpBanner"
  			}
  		],
  		optIn: [
  			{
  				"eval": "EVAL_THEFREEDICTIONARY_1"
  			}
  		],
  		optOut: [
  			{
  				"eval": "EVAL_THEFREEDICTIONARY_0"
  			}
  		]
  	},
  	{
  		name: "theverge",
  		runContext: {
  			frame: false,
  			main: true,
  			urlPattern: "^https://(www)?\\.theverge\\.com"
  		},
  		intermediate: false,
  		prehideSelectors: [
  			".duet--cta--cookie-banner"
  		],
  		detectCmp: [
  			{
  				exists: ".duet--cta--cookie-banner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".duet--cta--cookie-banner"
  			}
  		],
  		optIn: [
  			{
  				click: ".duet--cta--cookie-banner button.tracking-12",
  				all: false
  			}
  		],
  		optOut: [
  			{
  				click: ".duet--cta--cookie-banner button.tracking-12 > span"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_THEVERGE_0"
  			}
  		]
  	},
  	{
  		name: "tidbits-com",
  		cosmetic: true,
  		prehideSelectors: [
  			"#eu_cookie_law_widget-2"
  		],
  		detectCmp: [
  			{
  				exists: "#eu_cookie_law_widget-2"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#eu_cookie_law_widget-2"
  			}
  		],
  		optIn: [
  			{
  				click: "#eu-cookie-law form > input.accept"
  			}
  		],
  		optOut: [
  			{
  				hide: "#eu_cookie_law_widget-2"
  			}
  		]
  	},
  	{
  		name: "tractor-supply",
  		runContext: {
  			urlPattern: "^https://www\\.tractorsupply\\.com/"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			".tsc-cookie-banner"
  		],
  		detectCmp: [
  			{
  				exists: ".tsc-cookie-banner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".tsc-cookie-banner"
  			}
  		],
  		optIn: [
  			{
  				click: "#cookie-banner-cancel"
  			}
  		],
  		optOut: [
  			{
  				hide: ".tsc-cookie-banner"
  			}
  		]
  	},
  	{
  		name: "trader-joes-com",
  		cosmetic: true,
  		prehideSelectors: [
  			"div.aem-page > div[class^=\"CookiesAlert_cookiesAlert__\"]"
  		],
  		detectCmp: [
  			{
  				exists: "div.aem-page > div[class^=\"CookiesAlert_cookiesAlert__\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "div.aem-page > div[class^=\"CookiesAlert_cookiesAlert__\"]"
  			}
  		],
  		optIn: [
  			{
  				click: "div[class^=\"CookiesAlert_cookiesAlert__container__\"] button"
  			}
  		],
  		optOut: [
  			{
  				hide: "div.aem-page > div[class^=\"CookiesAlert_cookiesAlert__\"]"
  			}
  		]
  	},
  	{
  		name: "transcend",
  		vendorUrl: "https://unknown",
  		cosmetic: true,
  		prehideSelectors: [
  			"#transcend-consent-manager"
  		],
  		detectCmp: [
  			{
  				exists: "#transcend-consent-manager"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#transcend-consent-manager"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: [
  					"#transcend-consent-manager",
  					"#consentManagerMainDialog .inner-container button"
  				]
  			}
  		],
  		optOut: [
  			{
  				hide: "#transcend-consent-manager"
  			}
  		]
  	},
  	{
  		name: "transip-nl",
  		runContext: {
  			urlPattern: "^https://www\\.transip\\.nl/"
  		},
  		prehideSelectors: [
  			"#consent-modal"
  		],
  		detectCmp: [
  			{
  				any: [
  					{
  						exists: "#consent-modal"
  					},
  					{
  						exists: "#privacy-settings-content"
  					}
  				]
  			}
  		],
  		detectPopup: [
  			{
  				any: [
  					{
  						visible: "#consent-modal"
  					},
  					{
  						visible: "#privacy-settings-content"
  					}
  				]
  			}
  		],
  		optIn: [
  			{
  				click: "button[type=\"submit\"]"
  			}
  		],
  		optOut: [
  			{
  				"if": {
  					exists: "#privacy-settings-content"
  				},
  				then: [
  					{
  						click: "button[type=\"submit\"]"
  					}
  				],
  				"else": [
  					{
  						click: "div.one-modal__action-footer-column--secondary > a"
  					}
  				]
  			}
  		]
  	},
  	{
  		name: "tropicfeel-com",
  		prehideSelectors: [
  			"#shopify-section-cookies-controller"
  		],
  		detectCmp: [
  			{
  				exists: "#shopify-section-cookies-controller"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#shopify-section-cookies-controller #cookies-controller-main-pane",
  				check: "any"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#cookies-controller-main-pane form[data-form-allow-all] button"
  			}
  		],
  		optOut: [
  			{
  				click: "#cookies-controller-main-pane a[data-tab-target=manage-cookies]"
  			},
  			{
  				waitFor: "#manage-cookies-pane.active"
  			},
  			{
  				click: "#manage-cookies-pane.active input[type=checkbox][checked]:not([disabled])",
  				all: true
  			},
  			{
  				click: "#manage-cookies-pane.active button[type=submit]"
  			}
  		],
  		test: [
  		]
  	},
  	{
  		name: "true-car",
  		runContext: {
  			urlPattern: "^https://www\\.truecar\\.com/"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			[
  				"div[aria-labelledby=\"cookie-banner-heading\"]"
  			]
  		],
  		detectCmp: [
  			{
  				exists: "div[aria-labelledby=\"cookie-banner-heading\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "div[aria-labelledby=\"cookie-banner-heading\"]"
  			}
  		],
  		optIn: [
  			{
  				click: "div[aria-labelledby=\"cookie-banner-heading\"] > button[aria-label=\"Close\"]"
  			}
  		],
  		optOut: [
  			{
  				hide: "div[aria-labelledby=\"cookie-banner-heading\"]"
  			}
  		]
  	},
  	{
  		name: "truyo",
  		prehideSelectors: [
  			"#truyo-consent-module"
  		],
  		detectCmp: [
  			{
  				exists: "#truyo-cookieBarContent"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#truyo-consent-module"
  			}
  		],
  		optIn: [
  			{
  				click: "button#acceptAllCookieButton"
  			}
  		],
  		optOut: [
  			{
  				click: "button#declineAllCookieButton"
  			}
  		]
  	},
  	{
  		name: "twitch-mobile",
  		vendorUrl: "https://m.twitch.tv/",
  		cosmetic: true,
  		runContext: {
  			urlPattern: "^https?://m\\.twitch\\.tv"
  		},
  		prehideSelectors: [
  		],
  		detectCmp: [
  			{
  				exists: ".ReactModal__Overlay [href=\"https://www.twitch.tv/p/cookie-policy\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".ReactModal__Overlay [href=\"https://www.twitch.tv/p/cookie-policy\"]"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".ReactModal__Overlay:has([href=\"https://www.twitch.tv/p/cookie-policy\"]) button"
  			}
  		],
  		optOut: [
  			{
  				hide: ".ReactModal__Overlay:has([href=\"https://www.twitch.tv/p/cookie-policy\"])"
  			}
  		]
  	},
  	{
  		name: "twitch.tv",
  		runContext: {
  			urlPattern: "^https?://(www\\.)?twitch\\.tv"
  		},
  		prehideSelectors: [
  			"div:has(> .consent-banner .consent-banner__content--gdpr-v2),.ReactModalPortal:has([data-a-target=consent-modal-save])"
  		],
  		detectCmp: [
  			{
  				exists: ".consent-banner .consent-banner__content--gdpr-v2"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".consent-banner .consent-banner__content--gdpr-v2"
  			}
  		],
  		optIn: [
  			{
  				click: "button[data-a-target=\"consent-banner-accept\"]"
  			}
  		],
  		optOut: [
  			{
  				hide: "div:has(> .consent-banner .consent-banner__content--gdpr-v2)"
  			},
  			{
  				click: "button[data-a-target=\"consent-banner-manage-preferences\"]"
  			},
  			{
  				waitFor: "input[type=checkbox][data-a-target=tw-checkbox]"
  			},
  			{
  				click: "input[type=checkbox][data-a-target=tw-checkbox][checked]:not([disabled])",
  				all: true,
  				optional: true
  			},
  			{
  				waitForThenClick: "[data-a-target=consent-modal-save]"
  			},
  			{
  				waitForVisible: ".ReactModalPortal:has([data-a-target=consent-modal-save])",
  				check: "none"
  			}
  		]
  	},
  	{
  		name: "twitter",
  		runContext: {
  			urlPattern: "^https://([a-z0-9-]+\\.)?twitter\\.com/"
  		},
  		prehideSelectors: [
  			"[data-testid=\"BottomBar\"]"
  		],
  		detectCmp: [
  			{
  				exists: "[data-testid=\"BottomBar\"] div"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "[data-testid=\"BottomBar\"] div"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "[data-testid=\"BottomBar\"] > div:has(>div:first-child>div:last-child>span[role=button]) > div:last-child > div[role=button]:first-child"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "[data-testid=\"BottomBar\"] > div:has(>div:first-child>div:last-child>span[role=button]) > div:last-child > div[role=button]:last-child"
  			}
  		],
  		TODOtest: [
  			{
  				"eval": "EVAL_document.cookie.includes('d_prefs=MjoxLGNvbnNlbnRfdmVyc2lvbjoy')"
  			}
  		]
  	},
  	{
  		name: "ubuntu.com",
  		prehideSelectors: [
  			"dialog.cookie-policy"
  		],
  		detectCmp: [
  			{
  				any: [
  					{
  						exists: "dialog.cookie-policy header"
  					},
  					{
  						exists: "xpath///*[@id=\"modal\"]/div/header"
  					}
  				]
  			}
  		],
  		detectPopup: [
  			{
  				any: [
  					{
  						visible: "dialog header"
  					},
  					{
  						visible: "xpath///*[@id=\"modal\"]/div/header"
  					}
  				]
  			}
  		],
  		optIn: [
  			{
  				any: [
  					{
  						waitForThenClick: "#cookie-policy-button-accept"
  					},
  					{
  						waitForThenClick: "xpath///*[@id=\"cookie-policy-button-accept\"]"
  					}
  				]
  			}
  		],
  		optOut: [
  			{
  				any: [
  					{
  						waitForThenClick: "button.js-manage"
  					},
  					{
  						waitForThenClick: "xpath///*[@id=\"cookie-policy-content\"]/p[4]/button[2]"
  					}
  				]
  			},
  			{
  				waitForThenClick: "dialog.cookie-policy .p-switch__input:checked",
  				optional: true,
  				all: true,
  				timeout: 500
  			},
  			{
  				any: [
  					{
  						waitForThenClick: "dialog.cookie-policy .js-save-preferences"
  					},
  					{
  						waitForThenClick: "xpath///*[@id=\"modal\"]/div/button"
  					}
  				]
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_UBUNTU_COM_0"
  			}
  		]
  	},
  	{
  		name: "UK Cookie Consent",
  		prehideSelectors: [
  			"#catapult-cookie-bar"
  		],
  		cosmetic: true,
  		detectCmp: [
  			{
  				exists: "#catapult-cookie-bar"
  			}
  		],
  		detectPopup: [
  			{
  				exists: ".has-cookie-bar #catapult-cookie-bar"
  			}
  		],
  		optIn: [
  			{
  				click: "#catapultCookie"
  			}
  		],
  		optOut: [
  			{
  				hide: "#catapult-cookie-bar"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_UK_COOKIE_CONSENT_0"
  			}
  		]
  	},
  	{
  		name: "urbanarmorgear-com",
  		cosmetic: true,
  		prehideSelectors: [
  			"div[class^=\"Layout__CookieBannerContainer-\"]"
  		],
  		detectCmp: [
  			{
  				exists: "div[class^=\"Layout__CookieBannerContainer-\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "div[class^=\"Layout__CookieBannerContainer-\"]"
  			}
  		],
  		optIn: [
  			{
  				click: "button[class^=\"CookieBanner__AcceptButton\"]"
  			}
  		],
  		optOut: [
  			{
  				hide: "div[class^=\"Layout__CookieBannerContainer-\"]"
  			}
  		]
  	},
  	{
  		name: "usercentrics-api",
  		detectCmp: [
  			{
  				exists: "#usercentrics-root"
  			}
  		],
  		detectPopup: [
  			{
  				"eval": "EVAL_USERCENTRICS_API_0"
  			},
  			{
  				exists: [
  					"#usercentrics-root",
  					"[data-testid=uc-container]"
  				]
  			},
  			{
  				waitForVisible: "#usercentrics-root",
  				timeout: 2000
  			}
  		],
  		optIn: [
  			{
  				"eval": "EVAL_USERCENTRICS_API_3"
  			},
  			{
  				"eval": "EVAL_USERCENTRICS_API_1"
  			},
  			{
  				"eval": "EVAL_USERCENTRICS_API_5"
  			}
  		],
  		optOut: [
  			{
  				"eval": "EVAL_USERCENTRICS_API_1"
  			},
  			{
  				"eval": "EVAL_USERCENTRICS_API_2"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_USERCENTRICS_API_6"
  			}
  		]
  	},
  	{
  		name: "usercentrics-button",
  		detectCmp: [
  			{
  				exists: "#usercentrics-button"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#usercentrics-button #uc-btn-accept-banner"
  			}
  		],
  		optIn: [
  			{
  				click: "#usercentrics-button #uc-btn-accept-banner"
  			}
  		],
  		optOut: [
  			{
  				click: "#usercentrics-button #uc-btn-deny-banner"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_USERCENTRICS_BUTTON_0"
  			}
  		]
  	},
  	{
  		name: "uswitch.com",
  		runContext: {
  			main: true,
  			frame: false,
  			urlPattern: "^https://(www\\.)?uswitch\\.com/"
  		},
  		prehideSelectors: [
  			".ucb"
  		],
  		detectCmp: [
  			{
  				exists: ".ucb-banner"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".ucb-banner"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: ".ucb-banner .ucb-btn-accept"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: ".ucb-banner .ucb-btn-save"
  			}
  		]
  	},
  	{
  		name: "vodafone.de",
  		runContext: {
  			urlPattern: "^https://www\\.vodafone\\.de/"
  		},
  		prehideSelectors: [
  			".dip-consent,.dip-consent-container"
  		],
  		detectCmp: [
  			{
  				exists: ".dip-consent-container"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".dip-consent-content"
  			}
  		],
  		optOut: [
  			{
  				click: ".dip-consent-btn[tabindex=\"2\"]"
  			}
  		],
  		optIn: [
  			{
  				click: ".dip-consent-btn[tabindex=\"1\"]"
  			}
  		]
  	},
  	{
  		name: "waitrose.com",
  		prehideSelectors: [
  			"div[aria-labelledby=CookieAlertModalHeading]",
  			"section[data-test=initial-waitrose-cookie-consent-banner]",
  			"section[data-test=cookie-consent-modal]"
  		],
  		detectCmp: [
  			{
  				exists: "section[data-test=initial-waitrose-cookie-consent-banner]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "section[data-test=initial-waitrose-cookie-consent-banner]"
  			}
  		],
  		optIn: [
  			{
  				click: "button[data-test=accept-all]"
  			}
  		],
  		optOut: [
  			{
  				click: "button[data-test=manage-cookies]"
  			},
  			{
  				wait: 200
  			},
  			{
  				"eval": "EVAL_WAITROSE_0"
  			},
  			{
  				click: "button[data-test=submit]"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_WAITROSE_1"
  			}
  		]
  	},
  	{
  		name: "webflow",
  		vendorUrl: "https://webflow.com/",
  		prehideSelectors: [
  			".fs-cc-components"
  		],
  		detectCmp: [
  			{
  				exists: ".fs-cc-components"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".fs-cc-components"
  			},
  			{
  				visible: "[fs-cc=banner]"
  			}
  		],
  		optIn: [
  			{
  				wait: 500
  			},
  			{
  				waitForThenClick: "[fs-cc=banner] [fs-cc=allow]"
  			}
  		],
  		optOut: [
  			{
  				wait: 500
  			},
  			{
  				waitForThenClick: "[fs-cc=banner] [fs-cc=deny]"
  			}
  		]
  	},
  	{
  		name: "wetransfer.com",
  		detectCmp: [
  			{
  				exists: ".welcome__cookie-notice"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".welcome__cookie-notice"
  			}
  		],
  		optIn: [
  			{
  				click: ".welcome__button--accept"
  			}
  		],
  		optOut: [
  			{
  				click: ".welcome__button--decline"
  			}
  		]
  	},
  	{
  		name: "whitepages.com",
  		runContext: {
  			urlPattern: "^https://www\\.whitepages\\.com/"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			".cookie-wrapper, .cookie-overlay"
  		],
  		detectCmp: [
  			{
  				exists: ".cookie-wrapper"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-overlay"
  			}
  		],
  		optIn: [
  			{
  				click: "button[aria-label=\"Got it\"]"
  			}
  		],
  		optOut: [
  			{
  				hide: ".cookie-wrapper"
  			}
  		]
  	},
  	{
  		name: "wolframalpha",
  		vendorUrl: "https://www.wolframalpha.com",
  		prehideSelectors: [
  		],
  		cosmetic: true,
  		runContext: {
  			urlPattern: "^https://www\\.wolframalpha\\.com/"
  		},
  		detectCmp: [
  			{
  				exists: "section._a_yb"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "section._a_yb"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "section._a_yb button"
  			}
  		],
  		optOut: [
  			{
  				hide: "section._a_yb"
  			}
  		]
  	},
  	{
  		name: "woo-commerce-com",
  		prehideSelectors: [
  			".wccom-comp-privacy-banner .wccom-privacy-banner"
  		],
  		detectCmp: [
  			{
  				exists: ".wccom-comp-privacy-banner .wccom-privacy-banner"
  			}
  		],
  		detectPopup: [
  			{
  				exists: ".wccom-comp-privacy-banner .wccom-privacy-banner"
  			}
  		],
  		optIn: [
  			{
  				click: ".wccom-privacy-banner__content-buttons button.is-primary"
  			}
  		],
  		optOut: [
  			{
  				click: ".wccom-privacy-banner__content-buttons button.is-secondary"
  			},
  			{
  				waitForThenClick: "input[type=checkbox][checked]:not([disabled])",
  				all: true
  			},
  			{
  				click: "div.wccom-modal__footer > button"
  			}
  		]
  	},
  	{
  		name: "WP Cookie Notice for GDPR",
  		vendorUrl: "https://wordpress.org/plugins/gdpr-cookie-consent/",
  		prehideSelectors: [
  			"#gdpr-cookie-consent-bar"
  		],
  		detectCmp: [
  			{
  				exists: "#gdpr-cookie-consent-bar"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#gdpr-cookie-consent-bar"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#gdpr-cookie-consent-bar #cookie_action_accept"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#gdpr-cookie-consent-bar #cookie_action_reject"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_WP_COOKIE_NOTICE_0"
  			}
  		]
  	},
  	{
  		name: "wpcc",
  		cosmetic: true,
  		prehideSelectors: [
  			".wpcc-container"
  		],
  		detectCmp: [
  			{
  				exists: ".wpcc-container"
  			}
  		],
  		detectPopup: [
  			{
  				exists: ".wpcc-container .wpcc-message"
  			}
  		],
  		optIn: [
  			{
  				click: ".wpcc-compliance .wpcc-btn"
  			}
  		],
  		optOut: [
  			{
  				hide: ".wpcc-container"
  			}
  		]
  	},
  	{
  		name: "xe.com",
  		vendorUrl: "https://www.xe.com/",
  		runContext: {
  			urlPattern: "^https://www\\.xe\\.com/"
  		},
  		prehideSelectors: [
  			"[class*=ConsentBanner]"
  		],
  		detectCmp: [
  			{
  				exists: "[class*=ConsentBanner]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "[class*=ConsentBanner]"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "[class*=ConsentBanner] .egnScw"
  			}
  		],
  		optOut: [
  			{
  				wait: 1000
  			},
  			{
  				waitForThenClick: "[class*=ConsentBanner] .frDWEu"
  			},
  			{
  				waitForThenClick: "[class*=ConsentBanner] .hXIpFU"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_XE_TEST"
  			}
  		]
  	},
  	{
  		name: "xhamster-eu",
  		prehideSelectors: [
  			".cookies-modal"
  		],
  		detectCmp: [
  			{
  				exists: ".cookies-modal"
  			}
  		],
  		detectPopup: [
  			{
  				exists: ".cookies-modal"
  			}
  		],
  		optIn: [
  			{
  				click: "button.cmd-button-accept-all"
  			}
  		],
  		optOut: [
  			{
  				click: "button.cmd-button-reject-all"
  			}
  		]
  	},
  	{
  		name: "xhamster-us",
  		runContext: {
  			urlPattern: "^https://(www\\.)?xhamster\\d?\\.com"
  		},
  		cosmetic: true,
  		prehideSelectors: [
  			".cookie-announce"
  		],
  		detectCmp: [
  			{
  				exists: ".cookie-announce"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".cookie-announce .announce-text"
  			}
  		],
  		optIn: [
  			{
  				click: ".cookie-announce button.xh-button"
  			}
  		],
  		optOut: [
  			{
  				hide: ".cookie-announce"
  			}
  		]
  	},
  	{
  		name: "xing.com",
  		detectCmp: [
  			{
  				exists: "div[class^=cookie-consent-CookieConsent]"
  			}
  		],
  		detectPopup: [
  			{
  				exists: "div[class^=cookie-consent-CookieConsent]"
  			}
  		],
  		optIn: [
  			{
  				click: "#consent-accept-button"
  			}
  		],
  		optOut: [
  			{
  				click: "#consent-settings-button"
  			},
  			{
  				click: ".consent-banner-button-accept-overlay"
  			}
  		],
  		test: [
  			{
  				"eval": "EVAL_XING_0"
  			}
  		]
  	},
  	{
  		name: "xnxx-com",
  		cosmetic: true,
  		prehideSelectors: [
  			"#cookies-use-alert"
  		],
  		detectCmp: [
  			{
  				exists: "#cookies-use-alert"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#cookies-use-alert"
  			}
  		],
  		optIn: [
  			{
  				click: "#cookies-use-alert .close"
  			}
  		],
  		optOut: [
  			{
  				hide: "#cookies-use-alert"
  			}
  		]
  	},
  	{
  		name: "xvideos",
  		vendorUrl: "https://xvideos.com",
  		runContext: {
  			urlPattern: "^https://[^/]*xvideos\\.com/"
  		},
  		prehideSelectors: [
  		],
  		detectCmp: [
  			{
  				exists: ".disclaimer-opened #disclaimer-cookies"
  			}
  		],
  		detectPopup: [
  			{
  				visible: ".disclaimer-opened #disclaimer-cookies"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#disclaimer-accept_cookies"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#disclaimer-reject_cookies"
  			}
  		]
  	},
  	{
  		name: "Yahoo",
  		runContext: {
  			urlPattern: "^https://consent\\.yahoo\\.com/v2/"
  		},
  		prehideSelectors: [
  			"#reject-all"
  		],
  		detectCmp: [
  			{
  				exists: "#consent-page"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#consent-page"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#consent-page button[value=agree]"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#consent-page button[value=reject]"
  			}
  		]
  	},
  	{
  		name: "youporn.com",
  		cosmetic: true,
  		prehideSelectors: [
  			".euCookieModal, #js_euCookieModal"
  		],
  		detectCmp: [
  			{
  				exists: ".euCookieModal"
  			}
  		],
  		detectPopup: [
  			{
  				exists: ".euCookieModal, #js_euCookieModal"
  			}
  		],
  		optIn: [
  			{
  				click: "button[name=\"user_acceptCookie\"]"
  			}
  		],
  		optOut: [
  			{
  				hide: ".euCookieModal"
  			}
  		]
  	},
  	{
  		name: "youtube-desktop",
  		prehideSelectors: [
  			"tp-yt-iron-overlay-backdrop.opened",
  			"ytd-consent-bump-v2-lightbox"
  		],
  		detectCmp: [
  			{
  				exists: "ytd-consent-bump-v2-lightbox tp-yt-paper-dialog"
  			},
  			{
  				exists: "ytd-consent-bump-v2-lightbox tp-yt-paper-dialog a[href^=\"https://consent.youtube.com/\"]"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "ytd-consent-bump-v2-lightbox tp-yt-paper-dialog"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "ytd-consent-bump-v2-lightbox .eom-buttons .eom-button-row:first-child ytd-button-renderer:last-child #button,ytd-consent-bump-v2-lightbox .eom-buttons .eom-button-row:first-child ytd-button-renderer:last-child button"
  			},
  			{
  				wait: 500
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "ytd-consent-bump-v2-lightbox .eom-buttons .eom-button-row:first-child ytd-button-renderer:first-child #button,ytd-consent-bump-v2-lightbox .eom-buttons .eom-button-row:first-child ytd-button-renderer:first-child button"
  			},
  			{
  				wait: 500
  			}
  		],
  		test: [
  			{
  				wait: 500
  			},
  			{
  				"eval": "EVAL_YOUTUBE_DESKTOP_0"
  			}
  		]
  	},
  	{
  		name: "youtube-mobile",
  		prehideSelectors: [
  			".consent-bump-v2-lightbox"
  		],
  		detectCmp: [
  			{
  				exists: "ytm-consent-bump-v2-renderer"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "ytm-consent-bump-v2-renderer"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "ytm-consent-bump-v2-renderer .privacy-terms + .one-col-dialog-buttons c3-material-button:first-child button, ytm-consent-bump-v2-renderer .privacy-terms + .one-col-dialog-buttons ytm-button-renderer:first-child button"
  			},
  			{
  				wait: 500
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "ytm-consent-bump-v2-renderer .privacy-terms + .one-col-dialog-buttons c3-material-button:nth-child(2) button, ytm-consent-bump-v2-renderer .privacy-terms + .one-col-dialog-buttons ytm-button-renderer:nth-child(2) button"
  			},
  			{
  				wait: 500
  			}
  		],
  		test: [
  			{
  				wait: 500
  			},
  			{
  				"eval": "EVAL_YOUTUBE_MOBILE_0"
  			}
  		]
  	},
  	{
  		name: "zdf",
  		prehideSelectors: [
  			"#zdf-cmp-banner-sdk"
  		],
  		detectCmp: [
  			{
  				exists: "#zdf-cmp-banner-sdk"
  			}
  		],
  		detectPopup: [
  			{
  				visible: "#zdf-cmp-main.zdf-cmp-show"
  			}
  		],
  		optIn: [
  			{
  				waitForThenClick: "#zdf-cmp-main #zdf-cmp-accept-btn"
  			}
  		],
  		optOut: [
  			{
  				waitForThenClick: "#zdf-cmp-main #zdf-cmp-deny-btn"
  			}
  		],
  		test: [
  		]
  	}
  ];
  var consentomatic = {
  	"didomi.io": {
  		detectors: [
  			{
  				presentMatcher: {
  					target: {
  						selector: "#didomi-host, #didomi-notice"
  					},
  					type: "css"
  				},
  				showingMatcher: {
  					target: {
  						selector: "body.didomi-popup-open, .didomi-notice-banner"
  					},
  					type: "css"
  				}
  			}
  		],
  		methods: [
  			{
  				action: {
  					target: {
  						selector: ".didomi-popup-notice-buttons .didomi-button:not(.didomi-button-highlight), .didomi-notice-banner .didomi-learn-more-button"
  					},
  					type: "click"
  				},
  				name: "OPEN_OPTIONS"
  			},
  			{
  				action: {
  					actions: [
  						{
  							retries: 50,
  							target: {
  								selector: "#didomi-purpose-cookies"
  							},
  							type: "waitcss",
  							waitTime: 50
  						},
  						{
  							consents: [
  								{
  									description: "Share (everything) with others",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-share_whith_others]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-share_whith_others]:last-child"
  										},
  										type: "click"
  									},
  									type: "X"
  								},
  								{
  									description: "Information storage and access",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-cookies]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-cookies]:last-child"
  										},
  										type: "click"
  									},
  									type: "D"
  								},
  								{
  									description: "Content selection, offers and marketing",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-CL-T1Rgm7]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-CL-T1Rgm7]:last-child"
  										},
  										type: "click"
  									},
  									type: "E"
  								},
  								{
  									description: "Analytics",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-analytics]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-analytics]:last-child"
  										},
  										type: "click"
  									},
  									type: "B"
  								},
  								{
  									description: "Analytics",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-M9NRHJe3G]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-M9NRHJe3G]:last-child"
  										},
  										type: "click"
  									},
  									type: "B"
  								},
  								{
  									description: "Ad and content selection",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-advertising_personalization]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-advertising_personalization]:last-child"
  										},
  										type: "click"
  									},
  									type: "F"
  								},
  								{
  									description: "Ad and content selection",
  									falseAction: {
  										parent: {
  											childFilter: {
  												target: {
  													selector: "#didomi-purpose-pub-ciblee"
  												}
  											},
  											selector: ".didomi-consent-popup-data-processing, .didomi-components-accordion-label-container"
  										},
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-pub-ciblee]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-pub-ciblee]:last-child"
  										},
  										type: "click"
  									},
  									type: "F"
  								},
  								{
  									description: "Ad and content selection - basics",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-q4zlJqdcD]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-q4zlJqdcD]:last-child"
  										},
  										type: "click"
  									},
  									type: "F"
  								},
  								{
  									description: "Ad and content selection - partners and subsidiaries",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-partenaire-cAsDe8jC]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-partenaire-cAsDe8jC]:last-child"
  										},
  										type: "click"
  									},
  									type: "F"
  								},
  								{
  									description: "Ad and content selection - social networks",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-p4em9a8m]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-p4em9a8m]:last-child"
  										},
  										type: "click"
  									},
  									type: "F"
  								},
  								{
  									description: "Ad and content selection - others",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-autres-pub]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-autres-pub]:last-child"
  										},
  										type: "click"
  									},
  									type: "F"
  								},
  								{
  									description: "Social networks",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-reseauxsociaux]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-reseauxsociaux]:last-child"
  										},
  										type: "click"
  									},
  									type: "A"
  								},
  								{
  									description: "Social networks",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-social_media]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-social_media]:last-child"
  										},
  										type: "click"
  									},
  									type: "A"
  								},
  								{
  									description: "Content selection",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-content_personalization]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-content_personalization]:last-child"
  										},
  										type: "click"
  									},
  									type: "E"
  								},
  								{
  									description: "Ad delivery",
  									falseAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-ad_delivery]:first-child"
  										},
  										type: "click"
  									},
  									trueAction: {
  										target: {
  											selector: ".didomi-components-radio__option[aria-describedby=didomi-purpose-ad_delivery]:last-child"
  										},
  										type: "click"
  									},
  									type: "F"
  								}
  							],
  							type: "consent"
  						},
  						{
  							action: {
  								consents: [
  									{
  										matcher: {
  											childFilter: {
  												target: {
  													selector: ":not(.didomi-components-radio__option--selected)"
  												}
  											},
  											type: "css"
  										},
  										trueAction: {
  											target: {
  												selector: ":nth-child(2)"
  											},
  											type: "click"
  										},
  										falseAction: {
  											target: {
  												selector: ":first-child"
  											},
  											type: "click"
  										},
  										type: "X"
  									}
  								],
  								type: "consent"
  							},
  							target: {
  								selector: ".didomi-components-radio"
  							},
  							type: "foreach"
  						}
  					],
  					type: "list"
  				},
  				name: "DO_CONSENT"
  			},
  			{
  				action: {
  					parent: {
  						selector: ".didomi-consent-popup-footer .didomi-consent-popup-actions"
  					},
  					target: {
  						selector: ".didomi-components-button:first-child"
  					},
  					type: "click"
  				},
  				name: "SAVE_CONSENT"
  			}
  		]
  	},
  	oil: {
  		detectors: [
  			{
  				presentMatcher: {
  					target: {
  						selector: ".as-oil-content-overlay"
  					},
  					type: "css"
  				},
  				showingMatcher: {
  					target: {
  						selector: ".as-oil-content-overlay"
  					},
  					type: "css"
  				}
  			}
  		],
  		methods: [
  			{
  				action: {
  					actions: [
  						{
  							target: {
  								selector: ".as-js-advanced-settings"
  							},
  							type: "click"
  						},
  						{
  							retries: "10",
  							target: {
  								selector: ".as-oil-cpc__purpose-container"
  							},
  							type: "waitcss",
  							waitTime: "250"
  						}
  					],
  					type: "list"
  				},
  				name: "OPEN_OPTIONS"
  			},
  			{
  				action: {
  					actions: [
  						{
  							consents: [
  								{
  									matcher: {
  										parent: {
  											selector: ".as-oil-cpc__purpose-container",
  											textFilter: [
  												"Information storage and access",
  												"Opbevaring af og adgang til oplysninger på din enhed"
  											]
  										},
  										target: {
  											selector: "input"
  										},
  										type: "checkbox"
  									},
  									toggleAction: {
  										parent: {
  											selector: ".as-oil-cpc__purpose-container",
  											textFilter: [
  												"Information storage and access",
  												"Opbevaring af og adgang til oplysninger på din enhed"
  											]
  										},
  										target: {
  											selector: ".as-oil-cpc__switch"
  										},
  										type: "click"
  									},
  									type: "D"
  								},
  								{
  									matcher: {
  										parent: {
  											selector: ".as-oil-cpc__purpose-container",
  											textFilter: [
  												"Personlige annoncer",
  												"Personalisation"
  											]
  										},
  										target: {
  											selector: "input"
  										},
  										type: "checkbox"
  									},
  									toggleAction: {
  										parent: {
  											selector: ".as-oil-cpc__purpose-container",
  											textFilter: [
  												"Personlige annoncer",
  												"Personalisation"
  											]
  										},
  										target: {
  											selector: ".as-oil-cpc__switch"
  										},
  										type: "click"
  									},
  									type: "E"
  								},
  								{
  									matcher: {
  										parent: {
  											selector: ".as-oil-cpc__purpose-container",
  											textFilter: [
  												"Annoncevalg, levering og rapportering",
  												"Ad selection, delivery, reporting"
  											]
  										},
  										target: {
  											selector: "input"
  										},
  										type: "checkbox"
  									},
  									toggleAction: {
  										parent: {
  											selector: ".as-oil-cpc__purpose-container",
  											textFilter: [
  												"Annoncevalg, levering og rapportering",
  												"Ad selection, delivery, reporting"
  											]
  										},
  										target: {
  											selector: ".as-oil-cpc__switch"
  										},
  										type: "click"
  									},
  									type: "F"
  								},
  								{
  									matcher: {
  										parent: {
  											selector: ".as-oil-cpc__purpose-container",
  											textFilter: [
  												"Personalisering af indhold",
  												"Content selection, delivery, reporting"
  											]
  										},
  										target: {
  											selector: "input"
  										},
  										type: "checkbox"
  									},
  									toggleAction: {
  										parent: {
  											selector: ".as-oil-cpc__purpose-container",
  											textFilter: [
  												"Personalisering af indhold",
  												"Content selection, delivery, reporting"
  											]
  										},
  										target: {
  											selector: ".as-oil-cpc__switch"
  										},
  										type: "click"
  									},
  									type: "E"
  								},
  								{
  									matcher: {
  										parent: {
  											childFilter: {
  												target: {
  													selector: ".as-oil-cpc__purpose-header",
  													textFilter: [
  														"Måling",
  														"Measurement"
  													]
  												}
  											},
  											selector: ".as-oil-cpc__purpose-container"
  										},
  										target: {
  											selector: "input"
  										},
  										type: "checkbox"
  									},
  									toggleAction: {
  										parent: {
  											childFilter: {
  												target: {
  													selector: ".as-oil-cpc__purpose-header",
  													textFilter: [
  														"Måling",
  														"Measurement"
  													]
  												}
  											},
  											selector: ".as-oil-cpc__purpose-container"
  										},
  										target: {
  											selector: ".as-oil-cpc__switch"
  										},
  										type: "click"
  									},
  									type: "B"
  								},
  								{
  									matcher: {
  										parent: {
  											selector: ".as-oil-cpc__purpose-container",
  											textFilter: "Google"
  										},
  										target: {
  											selector: "input"
  										},
  										type: "checkbox"
  									},
  									toggleAction: {
  										parent: {
  											selector: ".as-oil-cpc__purpose-container",
  											textFilter: "Google"
  										},
  										target: {
  											selector: ".as-oil-cpc__switch"
  										},
  										type: "click"
  									},
  									type: "F"
  								}
  							],
  							type: "consent"
  						}
  					],
  					type: "list"
  				},
  				name: "DO_CONSENT"
  			},
  			{
  				action: {
  					target: {
  						selector: ".as-oil__btn-optin"
  					},
  					type: "click"
  				},
  				name: "SAVE_CONSENT"
  			},
  			{
  				action: {
  					target: {
  						selector: "div.as-oil"
  					},
  					type: "hide"
  				},
  				name: "HIDE_CMP"
  			}
  		]
  	},
  	optanon: {
  		detectors: [
  			{
  				presentMatcher: {
  					target: {
  						selector: "#optanon-menu, .optanon-alert-box-wrapper"
  					},
  					type: "css"
  				},
  				showingMatcher: {
  					target: {
  						displayFilter: true,
  						selector: ".optanon-alert-box-wrapper"
  					},
  					type: "css"
  				}
  			}
  		],
  		methods: [
  			{
  				action: {
  					actions: [
  						{
  							target: {
  								selector: ".optanon-alert-box-wrapper .optanon-toggle-display, a[onclick*='OneTrust.ToggleInfoDisplay()'], a[onclick*='Optanon.ToggleInfoDisplay()']"
  							},
  							type: "click"
  						}
  					],
  					type: "list"
  				},
  				name: "OPEN_OPTIONS"
  			},
  			{
  				action: {
  					actions: [
  						{
  							target: {
  								selector: ".preference-menu-item #Your-privacy"
  							},
  							type: "click"
  						},
  						{
  							target: {
  								selector: "#optanon-vendor-consent-text"
  							},
  							type: "click"
  						},
  						{
  							action: {
  								consents: [
  									{
  										matcher: {
  											target: {
  												selector: "input"
  											},
  											type: "checkbox"
  										},
  										toggleAction: {
  											target: {
  												selector: "label"
  											},
  											type: "click"
  										},
  										type: "X"
  									}
  								],
  								type: "consent"
  							},
  							target: {
  								selector: "#optanon-vendor-consent-list .vendor-item"
  							},
  							type: "foreach"
  						},
  						{
  							target: {
  								selector: ".vendor-consent-back-link"
  							},
  							type: "click"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-performance"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-performance"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "B"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-functional"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-functional"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "E"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-advertising"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-advertising"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "F"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-social"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-social"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "B"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Social Media Cookies"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Social Media Cookies"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "B"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Personalisation"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Personalisation"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "E"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Site monitoring cookies"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Site monitoring cookies"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "B"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Third party privacy-enhanced content"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Third party privacy-enhanced content"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "X"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Performance & Advertising Cookies"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Performance & Advertising Cookies"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "F"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Information storage and access"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Information storage and access"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "D"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Ad selection, delivery, reporting"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Ad selection, delivery, reporting"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "F"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Content selection, delivery, reporting"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Content selection, delivery, reporting"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "E"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Measurement"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Measurement"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "B"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Recommended Cookies"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Recommended Cookies"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "X"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Unclassified Cookies"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Unclassified Cookies"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "X"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Analytical Cookies"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Analytical Cookies"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "B"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Marketing Cookies"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Marketing Cookies"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "F"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Personalization"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Personalization"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "E"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Ad Selection, Delivery & Reporting"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Ad Selection, Delivery & Reporting"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "F"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						},
  						{
  							parent: {
  								selector: "#optanon-menu, .optanon-menu"
  							},
  							target: {
  								selector: ".menu-item-necessary",
  								textFilter: "Content Selection, Delivery & Reporting"
  							},
  							trueAction: {
  								actions: [
  									{
  										parent: {
  											selector: "#optanon-menu, .optanon-menu"
  										},
  										target: {
  											selector: ".menu-item-necessary",
  											textFilter: "Content Selection, Delivery & Reporting"
  										},
  										type: "click"
  									},
  									{
  										consents: [
  											{
  												matcher: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status input"
  													},
  													type: "checkbox"
  												},
  												toggleAction: {
  													parent: {
  														selector: "#optanon-popup-body-right"
  													},
  													target: {
  														selector: ".optanon-status label"
  													},
  													type: "click"
  												},
  												type: "E"
  											}
  										],
  										type: "consent"
  									}
  								],
  								type: "list"
  							},
  							type: "ifcss"
  						}
  					],
  					type: "list"
  				},
  				name: "DO_CONSENT"
  			},
  			{
  				action: {
  					parent: {
  						selector: ".optanon-save-settings-button"
  					},
  					target: {
  						selector: ".optanon-white-button-middle"
  					},
  					type: "click"
  				},
  				name: "SAVE_CONSENT"
  			},
  			{
  				action: {
  					actions: [
  						{
  							target: {
  								selector: "#optanon-popup-wrapper"
  							},
  							type: "hide"
  						},
  						{
  							target: {
  								selector: "#optanon-popup-bg"
  							},
  							type: "hide"
  						},
  						{
  							target: {
  								selector: ".optanon-alert-box-wrapper"
  							},
  							type: "hide"
  						}
  					],
  					type: "list"
  				},
  				name: "HIDE_CMP"
  			}
  		]
  	},
  	quantcast2: {
  		detectors: [
  			{
  				presentMatcher: {
  					target: {
  						selector: "[data-tracking-opt-in-overlay]"
  					},
  					type: "css"
  				},
  				showingMatcher: {
  					target: {
  						selector: "[data-tracking-opt-in-overlay] [data-tracking-opt-in-learn-more]"
  					},
  					type: "css"
  				}
  			}
  		],
  		methods: [
  			{
  				action: {
  					target: {
  						selector: "[data-tracking-opt-in-overlay] [data-tracking-opt-in-learn-more]"
  					},
  					type: "click"
  				},
  				name: "OPEN_OPTIONS"
  			},
  			{
  				action: {
  					actions: [
  						{
  							type: "wait",
  							waitTime: 500
  						},
  						{
  							action: {
  								actions: [
  									{
  										target: {
  											selector: "div",
  											textFilter: [
  												"Information storage and access"
  											]
  										},
  										trueAction: {
  											consents: [
  												{
  													matcher: {
  														target: {
  															selector: "input"
  														},
  														type: "checkbox"
  													},
  													toggleAction: {
  														target: {
  															selector: "label"
  														},
  														type: "click"
  													},
  													type: "D"
  												}
  											],
  											type: "consent"
  										},
  										type: "ifcss"
  									},
  									{
  										target: {
  											selector: "div",
  											textFilter: [
  												"Personalization"
  											]
  										},
  										trueAction: {
  											consents: [
  												{
  													matcher: {
  														target: {
  															selector: "input"
  														},
  														type: "checkbox"
  													},
  													toggleAction: {
  														target: {
  															selector: "label"
  														},
  														type: "click"
  													},
  													type: "F"
  												}
  											],
  											type: "consent"
  										},
  										type: "ifcss"
  									},
  									{
  										target: {
  											selector: "div",
  											textFilter: [
  												"Ad selection, delivery, reporting"
  											]
  										},
  										trueAction: {
  											consents: [
  												{
  													matcher: {
  														target: {
  															selector: "input"
  														},
  														type: "checkbox"
  													},
  													toggleAction: {
  														target: {
  															selector: "label"
  														},
  														type: "click"
  													},
  													type: "F"
  												}
  											],
  											type: "consent"
  										},
  										type: "ifcss"
  									},
  									{
  										target: {
  											selector: "div",
  											textFilter: [
  												"Content selection, delivery, reporting"
  											]
  										},
  										trueAction: {
  											consents: [
  												{
  													matcher: {
  														target: {
  															selector: "input"
  														},
  														type: "checkbox"
  													},
  													toggleAction: {
  														target: {
  															selector: "label"
  														},
  														type: "click"
  													},
  													type: "E"
  												}
  											],
  											type: "consent"
  										},
  										type: "ifcss"
  									},
  									{
  										target: {
  											selector: "div",
  											textFilter: [
  												"Measurement"
  											]
  										},
  										trueAction: {
  											consents: [
  												{
  													matcher: {
  														target: {
  															selector: "input"
  														},
  														type: "checkbox"
  													},
  													toggleAction: {
  														target: {
  															selector: "label"
  														},
  														type: "click"
  													},
  													type: "B"
  												}
  											],
  											type: "consent"
  										},
  										type: "ifcss"
  									},
  									{
  										target: {
  											selector: "div",
  											textFilter: [
  												"Other Partners"
  											]
  										},
  										trueAction: {
  											consents: [
  												{
  													matcher: {
  														target: {
  															selector: "input"
  														},
  														type: "checkbox"
  													},
  													toggleAction: {
  														target: {
  															selector: "label"
  														},
  														type: "click"
  													},
  													type: "X"
  												}
  											],
  											type: "consent"
  										},
  										type: "ifcss"
  									}
  								],
  								type: "list"
  							},
  							parent: {
  								childFilter: {
  									target: {
  										selector: "input"
  									}
  								},
  								selector: "[data-tracking-opt-in-overlay] > div > div"
  							},
  							target: {
  								childFilter: {
  									target: {
  										selector: "input"
  									}
  								},
  								selector: ":scope > div"
  							},
  							type: "foreach"
  						}
  					],
  					type: "list"
  				},
  				name: "DO_CONSENT"
  			},
  			{
  				action: {
  					target: {
  						selector: "[data-tracking-opt-in-overlay] [data-tracking-opt-in-save]"
  					},
  					type: "click"
  				},
  				name: "SAVE_CONSENT"
  			}
  		]
  	},
  	springer: {
  		detectors: [
  			{
  				presentMatcher: {
  					parent: null,
  					target: {
  						selector: ".cmp-app_gdpr"
  					},
  					type: "css"
  				},
  				showingMatcher: {
  					parent: null,
  					target: {
  						displayFilter: true,
  						selector: ".cmp-popup_popup"
  					},
  					type: "css"
  				}
  			}
  		],
  		methods: [
  			{
  				action: {
  					actions: [
  						{
  							target: {
  								selector: ".cmp-intro_rejectAll"
  							},
  							type: "click"
  						},
  						{
  							type: "wait",
  							waitTime: 250
  						},
  						{
  							target: {
  								selector: ".cmp-purposes_purposeItem:not(.cmp-purposes_selectedPurpose)"
  							},
  							type: "click"
  						}
  					],
  					type: "list"
  				},
  				name: "OPEN_OPTIONS"
  			},
  			{
  				action: {
  					consents: [
  						{
  							matcher: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Przechowywanie informacji na urządzeniu lub dostęp do nich",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch .cmp-switch_isSelected"
  								},
  								type: "css"
  							},
  							toggleAction: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Przechowywanie informacji na urządzeniu lub dostęp do nich",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch:not(.cmp-switch_isSelected)"
  								},
  								type: "click"
  							},
  							type: "D"
  						},
  						{
  							matcher: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Wybór podstawowych reklam",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch .cmp-switch_isSelected"
  								},
  								type: "css"
  							},
  							toggleAction: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Wybór podstawowych reklam",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch:not(.cmp-switch_isSelected)"
  								},
  								type: "click"
  							},
  							type: "F"
  						},
  						{
  							matcher: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Tworzenie profilu spersonalizowanych reklam",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch .cmp-switch_isSelected"
  								},
  								type: "css"
  							},
  							toggleAction: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Tworzenie profilu spersonalizowanych reklam",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch:not(.cmp-switch_isSelected)"
  								},
  								type: "click"
  							},
  							type: "F"
  						},
  						{
  							matcher: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Wybór spersonalizowanych reklam",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch .cmp-switch_isSelected"
  								},
  								type: "css"
  							},
  							toggleAction: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Wybór spersonalizowanych reklam",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch:not(.cmp-switch_isSelected)"
  								},
  								type: "click"
  							},
  							type: "E"
  						},
  						{
  							matcher: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Tworzenie profilu spersonalizowanych treści",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch .cmp-switch_isSelected"
  								},
  								type: "css"
  							},
  							toggleAction: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Tworzenie profilu spersonalizowanych treści",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch:not(.cmp-switch_isSelected)"
  								},
  								type: "click"
  							},
  							type: "E"
  						},
  						{
  							matcher: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Wybór spersonalizowanych treści",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch .cmp-switch_isSelected"
  								},
  								type: "css"
  							},
  							toggleAction: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Wybór spersonalizowanych treści",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch:not(.cmp-switch_isSelected)"
  								},
  								type: "click"
  							},
  							type: "B"
  						},
  						{
  							matcher: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Pomiar wydajności reklam",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch .cmp-switch_isSelected"
  								},
  								type: "css"
  							},
  							toggleAction: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Pomiar wydajności reklam",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch:not(.cmp-switch_isSelected)"
  								},
  								type: "click"
  							},
  							type: "B"
  						},
  						{
  							matcher: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Pomiar wydajności treści",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch .cmp-switch_isSelected"
  								},
  								type: "css"
  							},
  							toggleAction: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Pomiar wydajności treści",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch:not(.cmp-switch_isSelected)"
  								},
  								type: "click"
  							},
  							type: "B"
  						},
  						{
  							matcher: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Stosowanie badań rynkowych w celu generowania opinii odbiorców",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch .cmp-switch_isSelected"
  								},
  								type: "css"
  							},
  							toggleAction: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Stosowanie badań rynkowych w celu generowania opinii odbiorców",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch:not(.cmp-switch_isSelected)"
  								},
  								type: "click"
  							},
  							type: "X"
  						},
  						{
  							matcher: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Opracowywanie i ulepszanie produktów",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch .cmp-switch_isSelected"
  								},
  								type: "css"
  							},
  							toggleAction: {
  								parent: {
  									selector: ".cmp-purposes_detailHeader",
  									textFilter: "Opracowywanie i ulepszanie produktów",
  									childFilter: {
  										target: {
  											selector: ".cmp-switch_switch"
  										}
  									}
  								},
  								target: {
  									selector: ".cmp-switch_switch:not(.cmp-switch_isSelected)"
  								},
  								type: "click"
  							},
  							type: "X"
  						}
  					],
  					type: "consent"
  				},
  				name: "DO_CONSENT"
  			},
  			{
  				action: {
  					target: {
  						selector: ".cmp-details_save"
  					},
  					type: "click"
  				},
  				name: "SAVE_CONSENT"
  			}
  		]
  	},
  	wordpressgdpr: {
  		detectors: [
  			{
  				presentMatcher: {
  					parent: null,
  					target: {
  						selector: ".wpgdprc-consent-bar"
  					},
  					type: "css"
  				},
  				showingMatcher: {
  					parent: null,
  					target: {
  						displayFilter: true,
  						selector: ".wpgdprc-consent-bar"
  					},
  					type: "css"
  				}
  			}
  		],
  		methods: [
  			{
  				action: {
  					parent: null,
  					target: {
  						selector: ".wpgdprc-consent-bar .wpgdprc-consent-bar__settings",
  						textFilter: null
  					},
  					type: "click"
  				},
  				name: "OPEN_OPTIONS"
  			},
  			{
  				action: {
  					actions: [
  						{
  							target: {
  								selector: ".wpgdprc-consent-modal .wpgdprc-button",
  								textFilter: "Eyeota"
  							},
  							type: "click"
  						},
  						{
  							consents: [
  								{
  									description: "Eyeota Cookies",
  									matcher: {
  										parent: {
  											selector: ".wpgdprc-consent-modal__description",
  											textFilter: "Eyeota"
  										},
  										target: {
  											selector: "input"
  										},
  										type: "checkbox"
  									},
  									toggleAction: {
  										parent: {
  											selector: ".wpgdprc-consent-modal__description",
  											textFilter: "Eyeota"
  										},
  										target: {
  											selector: "label"
  										},
  										type: "click"
  									},
  									type: "X"
  								}
  							],
  							type: "consent"
  						},
  						{
  							target: {
  								selector: ".wpgdprc-consent-modal .wpgdprc-button",
  								textFilter: "Advertising"
  							},
  							type: "click"
  						},
  						{
  							consents: [
  								{
  									description: "Advertising Cookies",
  									matcher: {
  										parent: {
  											selector: ".wpgdprc-consent-modal__description",
  											textFilter: "Advertising"
  										},
  										target: {
  											selector: "input"
  										},
  										type: "checkbox"
  									},
  									toggleAction: {
  										parent: {
  											selector: ".wpgdprc-consent-modal__description",
  											textFilter: "Advertising"
  										},
  										target: {
  											selector: "label"
  										},
  										type: "click"
  									},
  									type: "F"
  								}
  							],
  							type: "consent"
  						}
  					],
  					type: "list"
  				},
  				name: "DO_CONSENT"
  			},
  			{
  				action: {
  					parent: null,
  					target: {
  						selector: ".wpgdprc-button",
  						textFilter: "Save my settings"
  					},
  					type: "click"
  				},
  				name: "SAVE_CONSENT"
  			}
  		]
  	}
  };
  var rules = {
  	autoconsent: autoconsent$1,
  	consentomatic: consentomatic
  };

  var rules$1 = /*#__PURE__*/Object.freeze({
    __proto__: null,
    autoconsent: autoconsent$1,
    consentomatic: consentomatic,
    'default': rules
  });

  const autoconsent = new AutoConsent(
      (message) => {
          AutoconsentAndroid.process(JSON.stringify(message));
      },
      null,
      rules$1,
  );
  window.autoconsentMessageCallback = (msg) => {
      autoconsent.receiveMessageCallback(msg);
  };

  collectMetrics().then((results) => {
      // pass the results to the native code. ddgPerfMetrics is a custom JS interface
      const resultsJson = JSON.stringify(results);
      ddgPerfMetrics.onMetrics(location.href + ' ' + resultsJson);
      window.alert(`PERF METRICS: ` + resultsJson);
  });

})();
