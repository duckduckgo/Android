"use strict";
(() => {
  var __defProp = Object.defineProperty;
  var __typeError = (msg) => {
    throw TypeError(msg);
  };
  var __defNormalProp = (obj, key2, value) => key2 in obj ? __defProp(obj, key2, { enumerable: true, configurable: true, writable: true, value }) : obj[key2] = value;
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __publicField = (obj, key2, value) => __defNormalProp(obj, typeof key2 !== "symbol" ? key2 + "" : key2, value);
  var __accessCheck = (obj, member, msg) => member.has(obj) || __typeError("Cannot " + msg);
  var __privateGet = (obj, member, getter) => (__accessCheck(obj, member, "read from private field"), getter ? getter.call(obj) : member.get(obj));
  var __privateAdd = (obj, member, value) => member.has(obj) ? __typeError("Cannot add the same private member more than once") : member instanceof WeakSet ? member.add(obj) : member.set(obj, value);
  var __privateSet = (obj, member, value, setter) => (__accessCheck(obj, member, "write to private field"), setter ? setter.call(obj, value) : member.set(obj, value), value);

  // src/requestIdleCallback.js
  /*!
   * Copyright 2015 Google Inc. All rights reserved.
   *
   * Licensed under the Apache License, Version 2.0 (the "License");
   * you may not use this file except in compliance with the License.
   * You may obtain a copy of the License at
   *
   * http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
   * or implied. See the License for the specific language governing
   * permissions and limitations under the License.
   */
  window.requestIdleCallback = window.requestIdleCallback || function(cb) {
    return setTimeout(function() {
      const start = Date.now();
      cb({
        didTimeout: false,
        timeRemaining: function() {
          return Math.max(0, 50 - (Date.now() - start));
        }
      });
    }, 1);
  };
  window.cancelIdleCallback = window.cancelIdleCallback || function(id) {
    clearTimeout(id);
  };

  // src/config.js
  var DDG_DOMAIN_REGEX = /^https:\/\/(([a-z0-9-_]+?)\.)?duckduckgo\.com\/email/;
  function createGlobalConfig(overrides) {
    let isApp = false;
    let isTopFrame = false;
    let supportsTopFrame = false;
    let hasModernWebkitAPI = false;
    // INJECT isApp HERE
    // INJECT isTopFrame HERE
    // INJECT supportsTopFrame HERE
    // INJECT hasModernWebkitAPI HERE
    let isWindows = false;
    // INJECT isWindows HERE
    let webkitMessageHandlerNames = [];
    // INJECT webkitMessageHandlerNames HERE
    let isDDGTestMode = false;
    isDDGTestMode = true;
    let contentScope = null;
    let userUnprotectedDomains = [];
    let userPreferences = null;
    // INJECT contentScope HERE
    // INJECT userUnprotectedDomains HERE
    // INJECT userPreferences HERE
    let availableInputTypes = null;
    // INJECT availableInputTypes HERE
    let secret = "PLACEHOLDER_SECRET";
    const isAndroid = userPreferences?.platform.name === "android";
    const isIOS = userPreferences?.platform.name === "ios";
    const isDDGApp = ["ios", "android", "macos", "windows"].includes(userPreferences?.platform.name) || isWindows;
    const isMobileApp = ["ios", "android"].includes(userPreferences?.platform.name);
    const isFirefox = navigator.userAgent.includes("Firefox");
    const isDDGDomain = Boolean(window.location.href.match(DDG_DOMAIN_REGEX));
    const isExtension = false;
    const config = {
      isApp,
      isDDGApp,
      isAndroid,
      isIOS,
      isFirefox,
      isMobileApp,
      isExtension,
      isTopFrame,
      isWindows,
      secret,
      supportsTopFrame,
      hasModernWebkitAPI,
      contentScope,
      userUnprotectedDomains,
      userPreferences,
      isDDGTestMode,
      isDDGDomain,
      availableInputTypes,
      webkitMessageHandlerNames,
      ...overrides
    };
    return config;
  }

  // src/constants.js
  var constants = {
    ATTR_INPUT_TYPE: "data-ddg-inputType",
    ATTR_AUTOFILL: "data-ddg-autofill",
    TEXT_LENGTH_CUTOFF: 100,
    MAX_INPUTS_PER_PAGE: 100,
    MAX_FORMS_PER_PAGE: 30,
    MAX_INPUTS_PER_FORM: 80,
    MAX_FORM_RESCANS: 50
  };

  // src/Form/label-util.js
  var EXCLUDED_TAGS = ["BR", "SCRIPT", "NOSCRIPT", "OPTION", "STYLE"];
  var extractElementStrings = (element) => {
    const strings = /* @__PURE__ */ new Set();
    const _extractElementStrings = (el) => {
      if (EXCLUDED_TAGS.includes(el.tagName)) {
        return;
      }
      if (el.nodeType === el.TEXT_NODE || !el.childNodes.length) {
        const trimmedText = removeExcessWhitespace(el.textContent);
        if (trimmedText) {
          strings.add(trimmedText);
        }
        return;
      }
      for (const node of el.childNodes) {
        const nodeType = node.nodeType;
        if (nodeType !== node.ELEMENT_NODE && nodeType !== node.TEXT_NODE) {
          continue;
        }
        _extractElementStrings(node);
      }
    };
    _extractElementStrings(element);
    return [...strings];
  };

  // src/Form/matching-config/__generated__/compiled-matching-config.js
  var matchingConfiguration = {
    matchers: {
      fields: {
        unknown: { type: "unknown", strategies: { ddgMatcher: "unknown" } },
        emailAddress: {
          type: "emailAddress",
          strategies: {
            cssSelector: "emailAddress",
            ddgMatcher: "emailAddress",
            vendorRegex: "email"
          }
        },
        password: {
          type: "password",
          strategies: { cssSelector: "password", ddgMatcher: "password" }
        },
        username: {
          type: "username",
          strategies: { cssSelector: "username", ddgMatcher: "username" }
        },
        firstName: {
          type: "firstName",
          strategies: {
            cssSelector: "firstName",
            ddgMatcher: "firstName",
            vendorRegex: "given-name"
          }
        },
        middleName: {
          type: "middleName",
          strategies: {
            cssSelector: "middleName",
            ddgMatcher: "middleName",
            vendorRegex: "additional-name"
          }
        },
        lastName: {
          type: "lastName",
          strategies: {
            cssSelector: "lastName",
            ddgMatcher: "lastName",
            vendorRegex: "family-name"
          }
        },
        fullName: {
          type: "fullName",
          strategies: {
            cssSelector: "fullName",
            ddgMatcher: "fullName",
            vendorRegex: "name"
          }
        },
        phone: {
          type: "phone",
          strategies: {
            cssSelector: "phone",
            ddgMatcher: "phone",
            vendorRegex: "tel"
          }
        },
        addressStreet: {
          type: "addressStreet",
          strategies: {
            cssSelector: "addressStreet",
            ddgMatcher: "addressStreet",
            vendorRegex: "address-line1"
          }
        },
        addressStreet2: {
          type: "addressStreet2",
          strategies: {
            cssSelector: "addressStreet2",
            ddgMatcher: "addressStreet2",
            vendorRegex: "address-line2"
          }
        },
        addressCity: {
          type: "addressCity",
          strategies: {
            cssSelector: "addressCity",
            ddgMatcher: "addressCity",
            vendorRegex: "address-level2"
          }
        },
        addressProvince: {
          type: "addressProvince",
          strategies: {
            cssSelector: "addressProvince",
            ddgMatcher: "addressProvince",
            vendorRegex: "address-level1"
          }
        },
        addressPostalCode: {
          type: "addressPostalCode",
          strategies: {
            cssSelector: "addressPostalCode",
            ddgMatcher: "addressPostalCode",
            vendorRegex: "postal-code"
          }
        },
        addressCountryCode: {
          type: "addressCountryCode",
          strategies: {
            cssSelector: "addressCountryCode",
            ddgMatcher: "addressCountryCode",
            vendorRegex: "country"
          }
        },
        birthdayDay: {
          type: "birthdayDay",
          strategies: { cssSelector: "birthdayDay", ddgMatcher: "birthdayDay" }
        },
        birthdayMonth: {
          type: "birthdayMonth",
          strategies: { cssSelector: "birthdayMonth", ddgMatcher: "birthdayMonth" }
        },
        birthdayYear: {
          type: "birthdayYear",
          strategies: { cssSelector: "birthdayYear", ddgMatcher: "birthdayYear" }
        },
        cardName: {
          type: "cardName",
          strategies: {
            cssSelector: "cardName",
            ddgMatcher: "cardName",
            vendorRegex: "cc-name"
          }
        },
        cardNumber: {
          type: "cardNumber",
          strategies: {
            cssSelector: "cardNumber",
            ddgMatcher: "cardNumber",
            vendorRegex: "cc-number"
          }
        },
        cardSecurityCode: {
          type: "cardSecurityCode",
          strategies: {
            cssSelector: "cardSecurityCode",
            ddgMatcher: "cardSecurityCode"
          }
        },
        expirationMonth: {
          type: "expirationMonth",
          strategies: {
            cssSelector: "expirationMonth",
            ddgMatcher: "expirationMonth",
            vendorRegex: "cc-exp-month"
          }
        },
        expirationYear: {
          type: "expirationYear",
          strategies: {
            cssSelector: "expirationYear",
            ddgMatcher: "expirationYear",
            vendorRegex: "cc-exp-year"
          }
        },
        expiration: {
          type: "expiration",
          strategies: {
            cssSelector: "expiration",
            ddgMatcher: "expiration",
            vendorRegex: "cc-exp"
          }
        }
      },
      lists: {
        unknown: ["unknown"],
        emailAddress: ["emailAddress"],
        password: ["password"],
        username: ["username"],
        cc: [
          "cardName",
          "cardNumber",
          "cardSecurityCode",
          "expirationMonth",
          "expirationYear",
          "expiration"
        ],
        id: [
          "firstName",
          "middleName",
          "lastName",
          "fullName",
          "phone",
          "addressStreet",
          "addressStreet2",
          "addressCity",
          "addressProvince",
          "addressPostalCode",
          "addressCountryCode",
          "birthdayDay",
          "birthdayMonth",
          "birthdayYear"
        ]
      }
    },
    strategies: {
      cssSelector: {
        selectors: {
          genericTextInputField: 'input:not([type=button]):not([type=checkbox]):not([type=color]):not([type=file]):not([type=hidden]):not([type=radio]):not([type=range]):not([type=reset]):not([type=image]):not([type=search]):not([role=search]):not([type=submit]):not([type=time]):not([type=url]):not([type=week]):not([name^=fake i]):not([data-description^=dummy i]):not([name*=otp]):not([autocomplete="fake"]):not([placeholder^=search i]):not([type=date]):not([type=datetime-local]):not([type=datetime]):not([type=month])',
          submitButtonSelector: 'input[type=submit], input[type=button], input[type=image], button:not([role=switch]):not([role=link]):not([aria-label="clear" i]), [role=button]:not([aria-label="clear" i]), a[href="#"][id*=button i], a[href="#"][id*=btn i]',
          formInputsSelectorWithoutSelect: 'input:not([type=button]):not([type=checkbox]):not([type=color]):not([type=file]):not([type=hidden]):not([type=radio]):not([type=range]):not([type=reset]):not([type=image]):not([type=search]):not([role=search]):not([type=submit]):not([type=time]):not([type=url]):not([type=week]):not([name^=fake i]):not([data-description^=dummy i]):not([name*=otp]):not([autocomplete="fake"]):not([placeholder^=search i]):not([type=date]):not([type=datetime-local]):not([type=datetime]):not([type=month]),[autocomplete=username]',
          formInputsSelector: 'input:not([type=button]):not([type=checkbox]):not([type=color]):not([type=file]):not([type=hidden]):not([type=radio]):not([type=range]):not([type=reset]):not([type=image]):not([type=search]):not([role=search]):not([type=submit]):not([type=time]):not([type=url]):not([type=week]):not([name^=fake i]):not([data-description^=dummy i]):not([name*=otp]):not([autocomplete="fake"]):not([placeholder^=search i]):not([type=date]):not([type=datetime-local]):not([type=datetime]):not([type=month]),[autocomplete=username],select',
          safeUniversalSelector: "*:not(select):not(option):not(script):not(noscript):not(style):not(br)",
          emailAddress: 'input:not([type])[name*=email i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]):not([name*=code i]), input[type=""][name*=email i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]):not([type=tel]), input[type=text][name*=email i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]):not([name*=title i]):not([name*=tab i]):not([name*=code i]), input:not([type])[placeholder*=email i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]):not([name*=code i]), input[type=text][placeholder*=email i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]), input[type=""][placeholder*=email i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]), input[type=email], input[type=text][aria-label*=email i]:not([aria-label*=search i]), input:not([type])[aria-label*=email i]:not([aria-label*=search i]), input[name=username][type=email], input[autocomplete=username][type=email], input[autocomplete=username][placeholder*=email i], input[autocomplete=email],input[name="mail_tel" i],input[value=email i]',
          username: 'input:not([type=button]):not([type=checkbox]):not([type=color]):not([type=file]):not([type=hidden]):not([type=radio]):not([type=range]):not([type=reset]):not([type=image]):not([type=search]):not([role=search]):not([type=submit]):not([type=time]):not([type=url]):not([type=week]):not([name^=fake i]):not([data-description^=dummy i]):not([name*=otp]):not([autocomplete="fake"]):not([placeholder^=search i]):not([type=date]):not([type=datetime-local]):not([type=datetime]):not([type=month])[autocomplete^=user i],input[name=username i],input[name="loginId" i],input[name="userid" i],input[id="userid" i],input[name="user_id" i],input[name="user-id" i],input[id="login-id" i],input[id="login_id" i],input[id="loginid" i],input[name="login" i],input[name=accountname i],input[autocomplete=username i],input[name*=accountid i],input[name="j_username" i],input[id="j_username" i],input[name="uwinid" i],input[name="livedoor_id" i],input[name="ssousername" i],input[name="j_userlogin_pwd" i],input[name="user[login]" i],input[name="user" i],input[name$="_username" i],input[id="lmSsoinput" i],input[name="account_subdomain" i],input[name="masterid" i],input[name="tridField" i],input[id="signInName" i],input[id="w3c_accountsbundle_accountrequeststep1_login" i],input[id="username" i],input[name="_user" i],input[name="login_username" i],input[name^="login-user-account" i],input[id="loginusuario" i],input[name="usuario" i],input[id="UserLoginFormUsername" i],input[id="nw_username" i],input[can-field="accountName"],input[name="login[username]"],input[placeholder^="username" i]',
          password: "input[type=password]:not([autocomplete*=cc]):not([autocomplete=one-time-code]):not([name*=answer i]):not([name*=mfa i]):not([name*=tin i]):not([name*=card i]):not([name*=cvv i]),input.js-cloudsave-phrase",
          cardName: 'input[autocomplete="cc-name" i], input[autocomplete="ccname" i], input[name="ccname" i], input[name="cc-name" i], input[name="ppw-accountHolderName" i], input[name="payment[name]"], input[id="cc-name" i], input[id="ccname" i], input[id*=cardname i], input[id*=card-name i], input[id*=card_name i]',
          cardNumber: 'input[autocomplete="cc-number" i], input[autocomplete="ccnumber" i], input[autocomplete="cardnumber" i], input[autocomplete="card-number" i], input[name="ccnumber" i], input[name="cc-number" i], input[name*=card i][name*=number i]:not([name*=verif i]):not([name*=phone i]):not([name*=secur i]), input[name*=cardnumber i], input[name="payment[card_no]"], input[id="cc-number" i], input[id="ccnumber" i], input[id*=cardnumber i], input[id*=card-number i], input[id*=card_number i]',
          cardSecurityCode: 'input[autocomplete="cc-csc" i], input[autocomplete="csc" i], input[autocomplete="cc-cvc" i], input[autocomplete="cvc" i], input[name*="cvc" i], input[name*="cvv" i], input[name="cc-cvc" i], input[name="cc-csc" i], input[name="csc" i], input[name*=security i][name*=code i], input[id="cc-csc" i], input[id="csc" i], input[id="cc-cvc" i], input[id="cvc" i]',
          expirationMonth: '[autocomplete="cc-exp-month" i], [autocomplete="cc_exp_month" i], [name="ccmonth" i], [name="ppw-expirationDate_month" i], [name=cardExpiryMonth i], [name*=ExpDate_Month i], [name*=exp i][name*=month i]:not([name*=year i]), [id*=exp i][id*=month i]:not([id*=year i]), [name*=cc-exp-month i], [name*="card_exp-month" i], [name*=cc_exp_month i], [id="cc-exp-month" i], [id="cc_exp_month" i], [id*=cc-month i]',
          expirationYear: '[autocomplete="cc-exp-year" i], [autocomplete="cc_exp_year" i], [name="ccyear" i], [name="ppw-expirationDate_year" i], [name=cardExpiryYear i], [name*=ExpDate_Year i], [name*=exp i][name*=year i]:not([name*=month i]), [id*=exp i][id*=year i]:not([id*=month i]), [name*="cc-exp-year" i], [name*="card_exp-year" i], [name*=cc_exp_year i], [id="cc-exp-year" i], [id="cc_exp_year" i], [id*=cc-year i]',
          expiration: '[autocomplete="cc-exp" i], [name="cc-exp" i], [name="exp-date" i], input[name="expiry" i], [name="expirationDate" i], input[name*=ex][placeholder="mm/yy" i], [id="cc-exp" i], input[id*=expiration i]',
          firstName: "[name*=fname i], [autocomplete*=given-name i], [name*=firstname i], [autocomplete*=firstname i], [name*=first-name i], [autocomplete*=first-name i], [name*=first_name i], [autocomplete*=first_name i], [name*=givenname i], [autocomplete*=givenname i], [name*=given-name i], [name*=given_name i], [autocomplete*=given_name i], [name*=forename i], [autocomplete*=forename i]",
          middleName: "[name*=mname i], [autocomplete*=additional-name i], [name*=middlename i], [autocomplete*=middlename i], [name*=middle-name i], [autocomplete*=middle-name i], [name*=middle_name i], [autocomplete*=middle_name i], [name*=additionalname i], [autocomplete*=additionalname i], [name*=additional-name i], [name*=additional_name i], [autocomplete*=additional_name i]",
          lastName: "[name=lname], [autocomplete*=family-name i], [name*=lastname i], [autocomplete*=lastname i], [name*=last-name i], [autocomplete*=last-name i], [name*=last_name i], [autocomplete*=last_name i], [name*=familyname i], [autocomplete*=familyname i], [name*=family-name i], [name*=family_name i], [autocomplete*=family_name i], [name*=surname i], [autocomplete*=surname i]",
          fullName: "[autocomplete=name], [name*=fullname i], [autocomplete*=fullname i], [name*=full-name i], [autocomplete*=full-name i], [name*=full_name i], [autocomplete*=full_name i], [name*=your-name i], [autocomplete*=your-name i]",
          phone: '[name*=phone i]:not([name*=extension i]):not([name*=type i]):not([name*=country i]), [name*=mobile i]:not([name*=type i]), [autocomplete=tel], [autocomplete="tel-national"], [placeholder*="phone number" i]',
          addressStreet: "[name=address i], [autocomplete=street-address i], [autocomplete=address-line1 i], [name=street i], [name=ppw-line1 i], [name*=addressLine1 i]",
          addressStreet2: "[name=address2 i], [autocomplete=address-line2 i], [name=ppw-line2 i], [name*=addressLine2 i]",
          addressCity: "[name=city i], [autocomplete=address-level2 i], [name=ppw-city i], [name*=addressCity i]",
          addressProvince: "[name=province i], [name=state i], [autocomplete=address-level1 i]",
          addressPostalCode: "[name=zip i], [name=zip2 i], [name=postal i], [autocomplete=postal-code i], [autocomplete=zip-code i], [name*=postalCode i], [name*=zipcode i]",
          addressCountryCode: "[name=country i], [autocomplete=country i], [name*=countryCode i], [name*=country-code i], [name*=countryName i], [name*=country-name i],select.idms-address-country",
          birthdayDay: '[autocomplete=bday-day i], [name=bday-day i], [name*=birthday_day i], [name*=birthday-day i], [name=date_of_birth_day i], [name=date-of-birth-day i], [name^=birthdate_d i], [name^=birthdate-d i], [aria-label="birthday" i][placeholder="day" i]',
          birthdayMonth: '[autocomplete=bday-month i], [name=bday-month i], [name*=birthday_month i], [name*=birthday-month i], [name=date_of_birth_month i], [name=date-of-birth-month i], [name^=birthdate_m i], [name^=birthdate-m i], select[name="mm" i]',
          birthdayYear: '[autocomplete=bday-year i], [name=bday-year i], [name*=birthday_year i], [name*=birthday-year i], [name=date_of_birth_year i], [name=date-of-birth-year i], [name^=birthdate_y i], [name^=birthdate-y i], [aria-label="birthday" i][placeholder="year" i]'
        }
      },
      ddgMatcher: {
        matchers: {
          unknown: {
            match: /search|find|filter|subject|title|captcha|mfa|2fa|(two|2).?factor|one-time|otp|social security number|ssn|cerca|filtr|oggetto|titolo|(due|2|più).?fattori|suche|filtern|betreff|zoeken|filter|onderwerp|titel|chercher|filtrer|objet|titre|authentification multifacteur|double authentification|à usage unique|busca|busqueda|filtra|dos pasos|un solo uso|sök|filter|ämne|multifaktorsautentisering|tvåfaktorsautentisering|två.?faktor|engångs/iu,
            skip: /phone|mobile|email|password/iu
          },
          emailAddress: {
            match: /.mail\b|apple.?id|posta elettronica|e.?mailadres|correo electr|correo-e|^correo$|\be.?post|e.?postadress/iu,
            skip: /phone|(first.?|last.?)name|number|code|\bdate\b/iu,
            forceUnknown: /search|filter|subject|title|\btab\b|otp/iu
          },
          password: {
            match: /password|passwort|kennwort|wachtwoord|mot de passe|clave|contraseña|lösenord/iu,
            skip: /email|one-time|error|hint|^username$/iu,
            forceUnknown: /search|captcha|mfa|2fa|two factor|otp|pin/iu
          },
          newPassword: { match: /new|confirm|re.?(enter|type)|repeat|update\b/iu },
          currentPassword: { match: /current|old|previous|expired|existing/iu },
          username: {
            match: /(user|account|online.?id|membership.?id|log(i|o)n|net)((.)?(name|i.?d.?|log(i|o)n).?)?(.?((or|\/).+|\*|:)( required)?)?$|(nome|id|login).?utente|(nome|id) (dell.)?account|codice (cliente|uten)|nutzername|anmeldename|gebruikersnaam|nom d.utilisateur|identifiant|pseudo|usuari|cuenta|identificador|apodo|\bdni\b|\bnie\b| del? documento|documento de identidad|användarnamn|kontonamn|användar-id/iu,
            skip: /phone/iu,
            forceUnknown: /search|policy|choose a user\b/iu
          },
          cardName: {
            match: /(card.*name|name.*card)|(card(.*)?holder|holder.*card)|(card.*owner|owner.*card)/iu,
            skip: /email|street|zip|city|state|address/iu
          },
          cardNumber: {
            match: /card.*number|number.*card/iu,
            skip: /phone/iu,
            forceUnknown: /plus/iu
          },
          cardSecurityCode: {
            match: /security.?code|card.?verif|cvv|csc|cvc|cv2|card id/iu
          },
          expirationMonth: {
            match: /(card|\bcc\b)?.?(exp(iry|iration)?)?.?(month|\bmm\b(?![.\s/-]yy))/iu,
            skip: /mm[/\s.\-_—–]|check|year/iu
          },
          expirationYear: {
            match: /(card|\bcc\b)?.?(exp(iry|iration)?)?.?(year|yy)/iu,
            skip: /mm[/\s.\-_—–]|check|month/iu
          },
          expiration: {
            match: /(\bmm\b|\b\d\d\b)[/\s.\-_—–](\byy|\bjj|\baa|\b\d\d)|\bexp|\bvalid(idity| through| until)/iu,
            skip: /invalid|^dd\/|check|street|zip|city|state|address/iu
          },
          firstName: {
            match: /(first|given|fore).?name|\bnome/iu,
            skip: /last|cognome|completo/iu
          },
          middleName: { match: /(middle|additional).?name/iu },
          lastName: {
            match: /(last|family|sur)[^i]?name|cognome/iu,
            skip: /first|\bnome/iu
          },
          fullName: {
            match: /^(full.?|whole\s|first.*last\s|real\s|contact.?)?name\b|\bnome/iu,
            forceUnknown: /company|org|item/iu
          },
          phone: {
            match: /phone|mobile|telefono|cellulare/iu,
            skip: /code|pass|country/iu,
            forceUnknown: /ext|type|otp/iu
          },
          addressStreet: {
            match: /address/iu,
            forceUnknown: /\bip\b|duck|web|url/iu,
            skip: /address.*(2|two|3|three)|email|log.?in|sign.?in|civico/iu
          },
          addressStreet2: {
            match: /address.*(2|two)|apartment|\bapt\b|\bflat\b|\bline.*(2|two)/iu,
            forceUnknown: /\bip\b|duck/iu,
            skip: /email|log.?in|sign.?in/iu
          },
          addressCity: {
            match: /city|town|città|comune/iu,
            skip: /\bzip\b|\bcap\b/iu,
            forceUnknown: /vatican/iu
          },
          addressProvince: {
            match: /state|province|region|county|provincia|regione/iu,
            forceUnknown: /united/iu,
            skip: /country/iu
          },
          addressPostalCode: {
            match: /\bzip\b|postal\b|post.?code|\bcap\b|codice postale/iu
          },
          addressCountryCode: { match: /country|\bnation\b|nazione|paese/iu },
          birthdayDay: { match: /(birth.*day|day.*birth)/iu, skip: /month|year/iu },
          birthdayMonth: { match: /(birth.*month|month.*birth)/iu, skip: /year/iu },
          birthdayYear: { match: /(birth.*year|year.*birth)/iu },
          loginRegex: {
            match: /sign(ing)?.?[io]n(?!g)|log.?[io]n|log.?out|unsubscri|(forgot(ten)?|reset) (your )?password|password( |-)(forgotten|lost|recovery)|mfa-submit-form|access.+?settings|unlock|logged in as|entra|accedi|accesso|resetta password|password dimenticata|dimenticato la password|recuper[ao] password|(ein|aus)loggen|anmeld(eformular|ung|efeld)|abmelden|passwort (vergessen|verloren)|zugang| zugangsformular|einwahl|inloggen|se (dé)?connecter|(dé)?connexion|récupérer ((mon|ton|votre|le) )?mot de passe|mot de passe (oublié|perdu)|clave(?! su)|olvidó su (clave|contraseña)|.*sesión|conect(arse|ado)|conéctate|acce(de|so)|entrar|logga (in|ut)|avprenumerera|avregistrera|glömt lösenord|återställ lösenord/iu
          },
          signupRegex: {
            match: /sign(ing)?.?up|join|\bregist(er|ration)|newsletter|\bsubscri(be|ption)|contact|create|start|enroll|settings|preferences|profile|update|checkout|purchase|buy|^order|schedule|estimate|request|new.?customer|(confirm|re.?(type|enter)|repeat) password|password confirm|iscri(viti|zione)|registra(ti|zione)|(?:nuovo|crea(?:zione)?) account|contatt(?:ac)i|sottoscriv|sottoscrizione|compra|acquist(a|o)|ordin[aeio]|richie(?:di|sta)|(?:conferma|ripeti) password|inizia|nuovo cliente|impostazioni|preferenze|profilo|aggiorna|paga|registrier(ung|en)|profil (anlegen|erstellen)| nachrichten|verteiler|neukunde|neuer (kunde|benutzer|nutzer)|passwort wiederholen|anmeldeseite|nieuwsbrief|aanmaken|profiel|s.inscrire|inscription|s.abonner|créer|préférences|profil|mise à jour|payer|ach(eter|at)| nouvel utilisateur|(confirmer|réessayer) ((mon|ton|votre|le) )?mot de passe|regis(trarse|tro)|regístrate|inscr(ibirse|ipción|íbete)|solicitar|crea(r cuenta)?|nueva cuenta|nuevo (cliente|usuario)|preferencias|perfil|lista de correo|registrer(a|ing)|(nytt|öppna) konto|nyhetsbrev|prenumer(era|ation)|kontakt|skapa|starta|inställningar|min (sida|kundvagn)|uppdatera|till kassan|gäst|köp|beställ|schemalägg|ny kund|(repetera|bekräfta) lösenord/iu
          },
          conservativeSignupRegex: {
            match: /sign.?up|join|register|enroll|(create|new).+account|newsletter|subscri(be|ption)|settings|preferences|update|iscri(viti|zione)|registra(ti|zione)|(?:nuovo|crea(?:zione)?) account|contatt(?:ac)?i|sottoscriv|sottoscrizione|impostazioni|preferenze|aggiorna|anmeld(en|ung)|registrier(en|ung)|neukunde|neuer (kunde|benutzer|nutzer)|registreren|eigenschappen|bijwerken|s.inscrire|inscription|s.abonner|abonnement|préférences|créer un compte|regis(trarse|tro)|regístrate|inscr(ibirse|ipción|íbete)|crea(r cuenta)?|nueva cuenta|nuevo (cliente|usuario)|preferencias|lista de correo|registrer(a|ing)|(nytt|öppna) konto|nyhetsbrev|prenumer(era|ation)|kontakt|skapa|starta|inställningar|min (sida|kundvagn)|uppdatera/iu
          },
          resetPasswordLink: {
            match: /(forgot(ten)?|reset|don't remember).?(your )?(password|username)|password forgotten|password dimenticata|reset(?:ta) password|recuper[ao] password|(vergessen|verloren|verlegt|wiederherstellen) passwort|wachtwoord (vergeten|reset)|(oublié|récupérer) ((mon|ton|votre|le) )?mot de passe|mot de passe (oublié|perdu)|re(iniciar|cuperar) (contraseña|clave)|olvid(ó su|aste tu|é mi) (contraseña|clave)|recordar( su)? (contraseña|clave)|glömt lösenord|återställ lösenord/iu
          },
          loginProvidersRegex: { match: / with | con | mit | met | avec /iu },
          passwordHintsRegex: {
            match: /at least (\d+|one) (character|letter|number|special|uppercase|lowercase)|must be between (\d+) and (\d+) characters/iu
          },
          submitButtonRegex: {
            match: /submit|send|confirm|save|continue|next|sign|log.?([io])n|buy|purchase|check.?out|subscribe|donate|update|\bset\b|invia|conferma|salva|continua|entra|acced|accesso|compra|paga|sottoscriv|registra|dona|senden|\bja\b|bestätigen|weiter|nächste|kaufen|bezahlen|spenden|versturen|verzenden|opslaan|volgende|koop|kopen|voeg toe|aanmelden|envoyer|confirmer|sauvegarder|continuer|suivant|signer|connexion|acheter|payer|s.abonner|donner|enviar|confirmar|registrarse|continuar|siguiente|comprar|donar|skicka|bekräfta|spara|fortsätt|nästa|logga in|köp|handla|till kassan|registrera|donera/iu
          },
          submitButtonUnlikelyRegex: {
            match: /facebook|twitter|google|apple|cancel|show|toggle|reveal|hide|print|back|already|annulla|mostra|nascondi|stampa|indietro|già|abbrechen|passwort|zeigen|verbergen|drucken|zurück|annuleer|wachtwoord|toon|vorige|annuler|mot de passe|montrer|cacher|imprimer|retour|déjà|anular|cancelar|imprimir|cerrar|avbryt|lösenord|visa|dölj|skirv ut|tillbaka|redan/iu
          }
        }
      },
      vendorRegex: {
        rules: {
          email: /((^e-?mail$)|(^email-?address$))|(e.?mail|courriel|correo.*electr(o|ó)nico|メールアドレス|электронной.?почты|邮件|邮箱|電郵地址|ഇ-മെയില്‍|ഇലക്ട്രോണിക്.?മെയിൽ|ایمیل|پست.*الکترونیک|ईमेल|इलॅक्ट्रॉनिक.?मेल|(\b|_)eposta(\b|_)|(?:이메일|전자.?우편|[ee]-?mail)(.?주소)?)/iu,
          tel: /((^phone$)|(^mobile$)|(^mobile-?phone$)|(^tel$)|(^telephone$)|(^phone-?number$))|(phone|mobile|contact.?number|telefonnummer|telefono|teléfono|telfixe|電話|telefone|telemovel|телефон|मोबाइल|(\b|_|\*)telefon(\b|_|\*)|电话|മൊബൈല്‍|(?:전화|핸드폰|휴대폰|휴대전화)(?:.?번호)?)/iu,
          organization: /((^company$)|(^company-?name$)|(^organization$)|(^organization-?name$))|(company|business|organization|organisation|empresa|societe|société|ragione.?sociale|会社|название.?компании|单位|公司|شرکت|회사|직장)/iu,
          "street-address": /((^address$)|(^street-?address$)|(^addr$)|(^street$)|(^mailing-?addr(ess)?$)|(^billing-?addr(ess)?$)|(^mail-?addr(ess)?$)|(^bill-?addr(ess)?$))|(streetaddress|street-address)/iu,
          "address-line1": /(addrline1|address_1)|((^address-?1$)|(^address-?line-?1$)|(^addr-?1$)|(^street-?1$))|(^address$|address[_-]?line[_-]?(1|one)|address1|addr1|street|(?:shipping|billing)address$|strasse|straße|hausnummer|housenumber|house.?name|direccion|dirección|adresse|indirizzo|^住所$|住所1|адрес|地址|(\b|_)adres(?! (başlığı(nız)?|tarifi))(\b|_)|^주소.?$|주소.?1)/iu,
          "address-line2": /(addrline2|address_2)|((^address-?2$)|(^address-?line-?2$)|(^addr-?2$)|(^street-?2$))|(address[_-]?line(2|two)|address2|addr2|street|suite|unit(?!e)|adresszusatz|ergänzende.?angaben|direccion2|colonia|adicional|addresssuppl|complementnom|appartement|indirizzo2|住所2|complemento|addrcomplement|улица|地址2|주소.?2)/iu,
          "address-line3": /(addrline3|address_3)|((^address-?3$)|(^address-?line-?3$)|(^addr-?3$)|(^street-?3$))|(address[_-]?line(3|three)|address3|addr3|street|suite|unit(?!e)|adresszusatz|ergänzende.?angaben|direccion3|colonia|adicional|addresssuppl|complementnom|appartement|indirizzo3|住所3|complemento|addrcomplement|улица|地址3|주소.?3)/iu,
          "address-level2": /((^city$)|(^town$)|(^address-?level-?2$)|(^address-?city$)|(^address-?town$))|(city|town|\bort\b|stadt|suburb|ciudad|provincia|localidad|poblacion|ville|commune|localit(a|à)|citt(a|à)|市区町村|cidade|город|市|分區|شهر|शहर|ग्राम|गाँव|നഗരം|ഗ്രാമം|((\b|_|\*)([i̇ii̇]l[cç]e(miz|niz)?)(\b|_|\*))|^시[^도·・]|시[·・]?군[·・]?구)/iu,
          "address-level1": /(land)|((^state$)|(^province$)|(^provence$)|(^address-?level-?1$)|(^address-?state$)|(^address-?province$))|(county|region|province|county|principality|都道府県|estado|provincia|область|省|地區|സംസ്ഥാനം|استان|राज्य|((\b|_|\*)(eyalet|[şs]ehir|[i̇ii̇]limiz|kent)(\b|_|\*))|^시[·・]?도)/iu,
          "postal-code": /((^postal$)|(^zip$)|(^zip2$)|(^zip-?code$)|(^postal-?code$)|(^post-?code$)|(^address-?zip$)|(^address-?postal$)|(^address-?code$)|(^address-?postal-?code$)|(^address-?zip-?code$))|(zip|postal|post.*code|pcode|pin.?code|postleitzahl|\bcp\b|\bcdp\b|\bcap\b|郵便番号|codigo|codpos|\bcep\b|почтовый.?индекс|पिन.?कोड|പിന്‍കോഡ്|邮政编码|邮编|郵遞區號|(\b|_)posta kodu(\b|_)|우편.?번호)/iu,
          country: /((^country$)|(^country-?code$)|(^country-?name$)|(^address-?country$)|(^address-?country-?name$)|(^address-?country-?code$))|(country|countries|país|pais|(\b|_)land(\b|_)(?!.*(mark.*))|国家|국가|나라|(\b|_)(ülke|ulce|ulke)(\b|_)|کشور)/iu,
          "cc-name": /(accountholdername|titulaire)|(cc-?name|card-?name|cardholder-?name|cardholder|cardholder(?!.*(street|zip|city|state|address))|(^nom$))|(card.?(?:holder|owner)|name.*(\b)?on(\b)?.*cardcard.?(?:holder|owner)(?!.*(street|zip|city|state|address))|name.*(\b)?on(\b)?.*card(?!.*(street|zip|city|state|address))|(?:card|cc).?name|cc.?full.?name|karteninhaber|nombre.*tarjeta|nom.*carte|nome.*cart|名前|имя.*карты|信用卡开户名|开户名|持卡人姓名|持卡人姓名)/iu,
          name: /((^name$)|full-?name|your-?name)|(^name|full.?name|your.?name|customer.?name|bill.?name|ship.?name|name.*first.*last|firstandlastname|nombre.*y.*apellidos|^nom(?!bre)\b|お名前|氏名|^nome|نام.*نام.*خانوادگی|姓名|(\b|_|\*)ad[ı]? soyad[ı]?(\b|_|\*)|성명)/iu,
          "given-name": /((^f-?name$)|(^first-?name$)|(^given-?name$)|(^first-?n$))|(first.*name|initials|fname|first$|given.*name|vorname|nombre|forename|prénom|prenom|名|\bnome|имя|نام|이름|പേര്|(\b|_|\*)(isim|ad|ad(i|ı|iniz|ınız)?)(\b|_|\*)|नाम)/iu,
          "additional-name": /(apellido.?materno|lastlastname)|((^m-?name$)|(^middle-?name$)|(^additional-?name$)|(^middle-?initial$)|(^middle-?n$)|(^middle-?i$))|(middle.*name|mname|middle$|middle.*initial|m\.i\.|mi$|\bmi\b)/iu,
          "family-name": /((^l-?name$)|(^last-?name$)|(^s-?name$)|(^surname$)|(^family-?name$)|(^family-?n$)|(^last-?n$))|(last.*name|lname|surname|last$|secondname|family.*name|nachname|apellidos?|famille|^nom(?!bre)|cognome|姓|apelidos|surename|sobrenome|фамилия|نام.*خانوادگی|उपनाम|മറുപേര്|(\b|_|\*)(soyisim|soyad(i|ı|iniz|ınız)?)(\b|_|\*)|\b성(?:[^명]|\b))/iu,
          "cc-number": /((cc|kk)nr)|(cc-?number|cc-?num|card-?number|card-?num|(^number$)|(^cc$)|cc-?no|card-?no|(^credit-?card$)|numero-?carte|(^carte$)|(^carte-?credit$)|num-?carte|cb-?num)|((add)?(?:card|cc|acct).?(?:number|#|no|num|field)|カード番号|номер.*карты|信用卡号|信用卡号码|信用卡卡號|카드|(numero|número|numéro)(?!.*(document|fono|phone|réservation)))/iu,
          "cc-exp-month": /((cc|kk)month)|((^exp-?month$)|(^cc-?exp-?month$)|(^cc-?month$)|(^card-?month$)|(^cc-?mo$)|(^card-?mo$)|(^exp-?mo$)|(^card-?exp-?mo$)|(^cc-?exp-?mo$)|(^card-?expiration-?month$)|(^expiration-?month$)|(^cc-?mm$)|(^cc-?m$)|(^card-?mm$)|(^card-?m$)|(^card-?exp-?mm$)|(^cc-?exp-?mm$)|(^exp-?mm$)|(^exp-?m$)|(^expire-?month$)|(^expire-?mo$)|(^expiry-?month$)|(^expiry-?mo$)|(^card-?expire-?month$)|(^card-?expire-?mo$)|(^card-?expiry-?month$)|(^card-?expiry-?mo$)|(^mois-?validite$)|(^mois-?expiration$)|(^m-?validite$)|(^m-?expiration$)|(^expiry-?date-?field-?month$)|(^expiration-?date-?month$)|(^expiration-?date-?mm$)|(^exp-?mon$)|(^validity-?mo$)|(^exp-?date-?mo$)|(^cb-?date-?mois$)|(^date-?m$))|(gueltig|gültig|monat|fecha|date.*exp|scadenza|有効期限|validade|срок действия карты|月)/iu,
          "cc-exp-year": /((cc|kk)year)|((^exp-?year$)|(^cc-?exp-?year$)|(^cc-?year$)|(^card-?year$)|(^cc-?yr$)|(^card-?yr$)|(^exp-?yr$)|(^card-?exp-?yr$)|(^cc-?exp-?yr$)|(^card-?expiration-?year$)|(^expiration-?year$)|(^cc-?yy$)|(^cc-?y$)|(^card-?yy$)|(^card-?y$)|(^card-?exp-?yy$)|(^cc-?exp-?yy$)|(^exp-?yy$)|(^exp-?y$)|(^cc-?yyyy$)|(^card-?yyyy$)|(^card-?exp-?yyyy$)|(^cc-?exp-?yyyy$)|(^expire-?year$)|(^expire-?yr$)|(^expiry-?year$)|(^expiry-?yr$)|(^card-?expire-?year$)|(^card-?expire-?yr$)|(^card-?expiry-?year$)|(^card-?expiry-?yr$)|(^an-?validite$)|(^an-?expiration$)|(^annee-?validite$)|(^annee-?expiration$)|(^expiry-?date-?field-?year$)|(^expiration-?date-?year$)|(^cb-?date-?ann$)|(^expiration-?date-?yy$)|(^expiration-?date-?yyyy$)|(^validity-?year$)|(^exp-?date-?year$)|(^date-?y$))|(ablaufdatum|gueltig|gültig|jahr|fecha|scadenza|有効期限|validade|срок действия карты|年|有效期)/iu,
          "cc-exp": /((^cc-?exp$)|(^card-?exp$)|(^cc-?expiration$)|(^card-?expiration$)|(^cc-?ex$)|(^card-?ex$)|(^card-?expire$)|(^card-?expiry$)|(^validite$)|(^expiration$)|(^expiry$)|mm-?yy|mm-?yyyy|yy-?mm|yyyy-?mm|expiration-?date|payment-?card-?expiration|(^payment-?cc-?date$))|(expir|exp.*date|^expfield$|gueltig|gültig|fecha|date.*exp|scadenza|有効期限|validade|срок действия карты)/iu,
          "cc-type": /(type|kartenmarke)|((^cc-?type$)|(^card-?type$)|(^card-?brand$)|(^cc-?brand$)|(^cb-?type$))/iu
        },
        ruleSets: [
          {
            "address-line1": "addrline1|address_1",
            "address-line2": "addrline2|address_2",
            "address-line3": "addrline3|address_3",
            "address-level1": "land",
            "additional-name": "apellido.?materno|lastlastname",
            "cc-name": "accountholdername|titulaire",
            "cc-number": "(cc|kk)nr",
            "cc-exp-month": "(cc|kk)month",
            "cc-exp-year": "(cc|kk)year",
            "cc-type": "type|kartenmarke"
          },
          {
            email: "(^e-?mail$)|(^email-?address$)",
            tel: "(^phone$)|(^mobile$)|(^mobile-?phone$)|(^tel$)|(^telephone$)|(^phone-?number$)",
            organization: "(^company$)|(^company-?name$)|(^organization$)|(^organization-?name$)",
            "street-address": "(^address$)|(^street-?address$)|(^addr$)|(^street$)|(^mailing-?addr(ess)?$)|(^billing-?addr(ess)?$)|(^mail-?addr(ess)?$)|(^bill-?addr(ess)?$)",
            "address-line1": "(^address-?1$)|(^address-?line-?1$)|(^addr-?1$)|(^street-?1$)",
            "address-line2": "(^address-?2$)|(^address-?line-?2$)|(^addr-?2$)|(^street-?2$)",
            "address-line3": "(^address-?3$)|(^address-?line-?3$)|(^addr-?3$)|(^street-?3$)",
            "address-level2": "(^city$)|(^town$)|(^address-?level-?2$)|(^address-?city$)|(^address-?town$)",
            "address-level1": "(^state$)|(^province$)|(^provence$)|(^address-?level-?1$)|(^address-?state$)|(^address-?province$)",
            "postal-code": "(^postal$)|(^zip$)|(^zip2$)|(^zip-?code$)|(^postal-?code$)|(^post-?code$)|(^address-?zip$)|(^address-?postal$)|(^address-?code$)|(^address-?postal-?code$)|(^address-?zip-?code$)",
            country: "(^country$)|(^country-?code$)|(^country-?name$)|(^address-?country$)|(^address-?country-?name$)|(^address-?country-?code$)",
            name: "(^name$)|full-?name|your-?name",
            "given-name": "(^f-?name$)|(^first-?name$)|(^given-?name$)|(^first-?n$)",
            "additional-name": "(^m-?name$)|(^middle-?name$)|(^additional-?name$)|(^middle-?initial$)|(^middle-?n$)|(^middle-?i$)",
            "family-name": "(^l-?name$)|(^last-?name$)|(^s-?name$)|(^surname$)|(^family-?name$)|(^family-?n$)|(^last-?n$)",
            "cc-name": "cc-?name|card-?name|cardholder-?name|cardholder|cardholder(?!.*(street|zip|city|state|address))|(^nom$)",
            "cc-number": "cc-?number|cc-?num|card-?number|card-?num|(^number$)|(^cc$)|cc-?no|card-?no|(^credit-?card$)|numero-?carte|(^carte$)|(^carte-?credit$)|num-?carte|cb-?num",
            "cc-exp": "(^cc-?exp$)|(^card-?exp$)|(^cc-?expiration$)|(^card-?expiration$)|(^cc-?ex$)|(^card-?ex$)|(^card-?expire$)|(^card-?expiry$)|(^validite$)|(^expiration$)|(^expiry$)|mm-?yy|mm-?yyyy|yy-?mm|yyyy-?mm|expiration-?date|payment-?card-?expiration|(^payment-?cc-?date$)",
            "cc-exp-month": "(^exp-?month$)|(^cc-?exp-?month$)|(^cc-?month$)|(^card-?month$)|(^cc-?mo$)|(^card-?mo$)|(^exp-?mo$)|(^card-?exp-?mo$)|(^cc-?exp-?mo$)|(^card-?expiration-?month$)|(^expiration-?month$)|(^cc-?mm$)|(^cc-?m$)|(^card-?mm$)|(^card-?m$)|(^card-?exp-?mm$)|(^cc-?exp-?mm$)|(^exp-?mm$)|(^exp-?m$)|(^expire-?month$)|(^expire-?mo$)|(^expiry-?month$)|(^expiry-?mo$)|(^card-?expire-?month$)|(^card-?expire-?mo$)|(^card-?expiry-?month$)|(^card-?expiry-?mo$)|(^mois-?validite$)|(^mois-?expiration$)|(^m-?validite$)|(^m-?expiration$)|(^expiry-?date-?field-?month$)|(^expiration-?date-?month$)|(^expiration-?date-?mm$)|(^exp-?mon$)|(^validity-?mo$)|(^exp-?date-?mo$)|(^cb-?date-?mois$)|(^date-?m$)",
            "cc-exp-year": "(^exp-?year$)|(^cc-?exp-?year$)|(^cc-?year$)|(^card-?year$)|(^cc-?yr$)|(^card-?yr$)|(^exp-?yr$)|(^card-?exp-?yr$)|(^cc-?exp-?yr$)|(^card-?expiration-?year$)|(^expiration-?year$)|(^cc-?yy$)|(^cc-?y$)|(^card-?yy$)|(^card-?y$)|(^card-?exp-?yy$)|(^cc-?exp-?yy$)|(^exp-?yy$)|(^exp-?y$)|(^cc-?yyyy$)|(^card-?yyyy$)|(^card-?exp-?yyyy$)|(^cc-?exp-?yyyy$)|(^expire-?year$)|(^expire-?yr$)|(^expiry-?year$)|(^expiry-?yr$)|(^card-?expire-?year$)|(^card-?expire-?yr$)|(^card-?expiry-?year$)|(^card-?expiry-?yr$)|(^an-?validite$)|(^an-?expiration$)|(^annee-?validite$)|(^annee-?expiration$)|(^expiry-?date-?field-?year$)|(^expiration-?date-?year$)|(^cb-?date-?ann$)|(^expiration-?date-?yy$)|(^expiration-?date-?yyyy$)|(^validity-?year$)|(^exp-?date-?year$)|(^date-?y$)",
            "cc-type": "(^cc-?type$)|(^card-?type$)|(^card-?brand$)|(^cc-?brand$)|(^cb-?type$)"
          },
          {
            email: "e.?mail|courriel|correo.*electr(o|\xF3)nico|\u30E1\u30FC\u30EB\u30A2\u30C9\u30EC\u30B9|\u042D\u043B\u0435\u043A\u0442\u0440\u043E\u043D\u043D\u043E\u0439.?\u041F\u043E\u0447\u0442\u044B|\u90AE\u4EF6|\u90AE\u7BB1|\u96FB\u90F5\u5730\u5740|\u0D07-\u0D2E\u0D46\u0D2F\u0D3F\u0D32\u0D4D\u200D|\u0D07\u0D32\u0D15\u0D4D\u0D1F\u0D4D\u0D30\u0D4B\u0D23\u0D3F\u0D15\u0D4D.?\u0D2E\u0D46\u0D2F\u0D3F\u0D7D|\u0627\u06CC\u0645\u06CC\u0644|\u067E\u0633\u062A.*\u0627\u0644\u06A9\u062A\u0631\u0648\u0646\u06CC\u06A9|\u0908\u092E\u0947\u0932|\u0907\u0932\u0945\u0915\u094D\u091F\u094D\u0930\u0949\u0928\u093F\u0915.?\u092E\u0947\u0932|(\\b|_)eposta(\\b|_)|(?:\uC774\uBA54\uC77C|\uC804\uC790.?\uC6B0\uD3B8|[Ee]-?mail)(.?\uC8FC\uC18C)?",
            tel: "phone|mobile|contact.?number|telefonnummer|telefono|tel\xE9fono|telfixe|\u96FB\u8A71|telefone|telemovel|\u0442\u0435\u043B\u0435\u0444\u043E\u043D|\u092E\u094B\u092C\u093E\u0907\u0932|(\\b|_|\\*)telefon(\\b|_|\\*)|\u7535\u8BDD|\u0D2E\u0D4A\u0D2C\u0D48\u0D32\u0D4D\u200D|(?:\uC804\uD654|\uD578\uB4DC\uD3F0|\uD734\uB300\uD3F0|\uD734\uB300\uC804\uD654)(?:.?\uBC88\uD638)?",
            organization: "company|business|organization|organisation|empresa|societe|soci\xE9t\xE9|ragione.?sociale|\u4F1A\u793E|\u043D\u0430\u0437\u0432\u0430\u043D\u0438\u0435.?\u043A\u043E\u043C\u043F\u0430\u043D\u0438\u0438|\u5355\u4F4D|\u516C\u53F8|\u0634\u0631\u06A9\u062A|\uD68C\uC0AC|\uC9C1\uC7A5",
            "street-address": "streetaddress|street-address",
            "address-line1": "^address$|address[_-]?line[_-]?(1|one)|address1|addr1|street|(?:shipping|billing)address$|strasse|stra\xDFe|hausnummer|housenumber|house.?name|direccion|direcci\xF3n|adresse|indirizzo|^\u4F4F\u6240$|\u4F4F\u62401|\u0410\u0434\u0440\u0435\u0441|\u5730\u5740|(\\b|_)adres(?! (ba\u015Fl\u0131\u011F\u0131(n\u0131z)?|tarifi))(\\b|_)|^\uC8FC\uC18C.?$|\uC8FC\uC18C.?1",
            "address-line2": "address[_-]?line(2|two)|address2|addr2|street|suite|unit(?!e)|adresszusatz|erg\xE4nzende.?angaben|direccion2|colonia|adicional|addresssuppl|complementnom|appartement|indirizzo2|\u4F4F\u62402|complemento|addrcomplement|\u0423\u043B\u0438\u0446\u0430|\u5730\u57402|\uC8FC\uC18C.?2",
            "address-line3": "address[_-]?line(3|three)|address3|addr3|street|suite|unit(?!e)|adresszusatz|erg\xE4nzende.?angaben|direccion3|colonia|adicional|addresssuppl|complementnom|appartement|indirizzo3|\u4F4F\u62403|complemento|addrcomplement|\u0423\u043B\u0438\u0446\u0430|\u5730\u57403|\uC8FC\uC18C.?3",
            "address-level2": "city|town|\\bort\\b|stadt|suburb|ciudad|provincia|localidad|poblacion|ville|commune|localit(a|\xE0)|citt(a|\xE0)|\u5E02\u533A\u753A\u6751|cidade|\u0413\u043E\u0440\u043E\u0434|\u5E02|\u5206\u5340|\u0634\u0647\u0631|\u0936\u0939\u0930|\u0917\u094D\u0930\u093E\u092E|\u0917\u093E\u0901\u0935|\u0D28\u0D17\u0D30\u0D02|\u0D17\u0D4D\u0D30\u0D3E\u0D2E\u0D02|((\\b|_|\\*)([\u0130ii\u0307]l[c\xE7]e(miz|niz)?)(\\b|_|\\*))|^\uC2DC[^\uB3C4\xB7\u30FB]|\uC2DC[\xB7\u30FB]?\uAD70[\xB7\u30FB]?\uAD6C",
            "address-level1": "county|region|province|county|principality|\u90FD\u9053\u5E9C\u770C|estado|provincia|\u043E\u0431\u043B\u0430\u0441\u0442\u044C|\u7701|\u5730\u5340|\u0D38\u0D02\u0D38\u0D4D\u0D25\u0D3E\u0D28\u0D02|\u0627\u0633\u062A\u0627\u0646|\u0930\u093E\u091C\u094D\u092F|((\\b|_|\\*)(eyalet|[\u015Fs]ehir|[\u0130ii\u0307]limiz|kent)(\\b|_|\\*))|^\uC2DC[\xB7\u30FB]?\uB3C4",
            "postal-code": "zip|postal|post.*code|pcode|pin.?code|postleitzahl|\\bcp\\b|\\bcdp\\b|\\bcap\\b|\u90F5\u4FBF\u756A\u53F7|codigo|codpos|\\bcep\\b|\u041F\u043E\u0447\u0442\u043E\u0432\u044B\u0439.?\u0418\u043D\u0434\u0435\u043A\u0441|\u092A\u093F\u0928.?\u0915\u094B\u0921|\u0D2A\u0D3F\u0D28\u0D4D\u200D\u0D15\u0D4B\u0D21\u0D4D|\u90AE\u653F\u7F16\u7801|\u90AE\u7F16|\u90F5\u905E\u5340\u865F|(\\b|_)posta kodu(\\b|_)|\uC6B0\uD3B8.?\uBC88\uD638",
            country: "country|countries|pa\xEDs|pais|(\\b|_)land(\\b|_)(?!.*(mark.*))|\u56FD\u5BB6|\uAD6D\uAC00|\uB098\uB77C|(\\b|_)(\xFClke|ulce|ulke)(\\b|_)|\u06A9\u0634\u0648\u0631",
            "cc-name": "card.?(?:holder|owner)|name.*(\\b)?on(\\b)?.*cardcard.?(?:holder|owner)(?!.*(street|zip|city|state|address))|name.*(\\b)?on(\\b)?.*card(?!.*(street|zip|city|state|address))|(?:card|cc).?name|cc.?full.?name|karteninhaber|nombre.*tarjeta|nom.*carte|nome.*cart|\u540D\u524D|\u0418\u043C\u044F.*\u043A\u0430\u0440\u0442\u044B|\u4FE1\u7528\u5361\u5F00\u6237\u540D|\u5F00\u6237\u540D|\u6301\u5361\u4EBA\u59D3\u540D|\u6301\u5361\u4EBA\u59D3\u540D",
            name: "^name|full.?name|your.?name|customer.?name|bill.?name|ship.?name|name.*first.*last|firstandlastname|nombre.*y.*apellidos|^nom(?!bre)\\b|\u304A\u540D\u524D|\u6C0F\u540D|^nome|\u0646\u0627\u0645.*\u0646\u0627\u0645.*\u062E\u0627\u0646\u0648\u0627\u062F\u06AF\u06CC|\u59D3\u540D|(\\b|_|\\*)ad[\u0131]? soyad[\u0131]?(\\b|_|\\*)|\uC131\uBA85",
            "given-name": "first.*name|initials|fname|first$|given.*name|vorname|nombre|forename|pr\xE9nom|prenom|\u540D|\\bnome|\u0418\u043C\u044F|\u0646\u0627\u0645|\uC774\uB984|\u0D2A\u0D47\u0D30\u0D4D|(\\b|_|\\*)(isim|ad|ad(i|\u0131|iniz|\u0131n\u0131z)?)(\\b|_|\\*)|\u0928\u093E\u092E",
            "additional-name": "middle.*name|mname|middle$|middle.*initial|m\\.i\\.|mi$|\\bmi\\b",
            "family-name": "last.*name|lname|surname|last$|secondname|family.*name|nachname|apellidos?|famille|^nom(?!bre)|cognome|\u59D3|apelidos|surename|sobrenome|\u0424\u0430\u043C\u0438\u043B\u0438\u044F|\u0646\u0627\u0645.*\u062E\u0627\u0646\u0648\u0627\u062F\u06AF\u06CC|\u0909\u092A\u0928\u093E\u092E|\u0D2E\u0D31\u0D41\u0D2A\u0D47\u0D30\u0D4D|(\\b|_|\\*)(soyisim|soyad(i|\u0131|iniz|\u0131n\u0131z)?)(\\b|_|\\*)|\\b\uC131(?:[^\uBA85]|\\b)",
            "cc-number": "(add)?(?:card|cc|acct).?(?:number|#|no|num|field)|\u30AB\u30FC\u30C9\u756A\u53F7|\u041D\u043E\u043C\u0435\u0440.*\u043A\u0430\u0440\u0442\u044B|\u4FE1\u7528\u5361\u53F7|\u4FE1\u7528\u5361\u53F7\u7801|\u4FE1\u7528\u5361\u5361\u865F|\uCE74\uB4DC|(numero|n\xFAmero|num\xE9ro)(?!.*(document|fono|phone|r\xE9servation))",
            "cc-exp-month": "gueltig|g\xFCltig|monat|fecha|date.*exp|scadenza|\u6709\u52B9\u671F\u9650|validade|\u0421\u0440\u043E\u043A \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044F \u043A\u0430\u0440\u0442\u044B|\u6708",
            "cc-exp-year": "ablaufdatum|gueltig|g\xFCltig|jahr|fecha|scadenza|\u6709\u52B9\u671F\u9650|validade|\u0421\u0440\u043E\u043A \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044F \u043A\u0430\u0440\u0442\u044B|\u5E74|\u6709\u6548\u671F",
            "cc-exp": "expir|exp.*date|^expfield$|gueltig|g\xFCltig|fecha|date.*exp|scadenza|\u6709\u52B9\u671F\u9650|validade|\u0421\u0440\u043E\u043A \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044F \u043A\u0430\u0440\u0442\u044B"
          }
        ]
      }
    }
  };

  // src/Form/matching-utils.js
  function logMatching(el, matchingResult) {
    if (!shouldLog()) return;
    const fieldIdentifier = getInputIdentifier(el);
    console.group(fieldIdentifier);
    console.log(el);
    const { strategyName, matchedString, matchedFrom, matcherType } = matchingResult;
    const verb = getVerb(matchingResult);
    let stringToLog = `${verb} for "${matcherType}" with "${strategyName}"`;
    if (matchedString && matchedFrom) {
      stringToLog += `
String: "${matchedString}"
Source: "${matchedFrom}"`;
    }
    console.log(stringToLog);
    console.groupEnd();
  }
  function getVerb(matchingResult) {
    if (matchingResult.matched) return "Matched";
    if (matchingResult.proceed === false) return "Matched forceUnknown";
    if (matchingResult.skip) return "Skipped";
    return "";
  }
  function getInputIdentifier(el) {
    const label = getExplicitLabelsText(el);
    const placeholder = el instanceof HTMLInputElement && el.placeholder ? `${el.placeholder}` : "";
    const name = el.name ? `${el.name}` : "";
    const id = el.id ? `#${el.id}` : "";
    return "Field: " + (label || placeholder || name || id);
  }
  function logUnmatched(el, allStrings) {
    if (!shouldLog()) return;
    const fieldIdentifier = getInputIdentifier(el);
    console.group(fieldIdentifier);
    console.log(el);
    const stringToLog = "Field not matched.";
    console.log(stringToLog, allStrings);
    console.groupEnd();
  }

  // src/Form/matching.js
  var { TEXT_LENGTH_CUTOFF, ATTR_INPUT_TYPE } = constants;
  var dimensionBounds = {
    emailAddress: { minWidth: 35 }
  };
  var _config, _cssSelectors, _ddgMatchers, _vendorRegexRules, _matcherLists, _defaultStrategyOrder;
  var Matching = class {
    /**
     * @param {MatchingConfiguration} config
     */
    constructor(config) {
      /** @type {MatchingConfiguration} */
      __privateAdd(this, _config);
      /** @type {CssSelectorConfiguration['selectors']} */
      __privateAdd(this, _cssSelectors);
      /** @type {Record<string, DDGMatcher>} */
      __privateAdd(this, _ddgMatchers);
      /**
       * This acts as an internal cache for the larger vendorRegexes
       * @type {VendorRegexConfiguration['rules']}
       */
      __privateAdd(this, _vendorRegexRules);
      /** @type {MatcherLists} */
      __privateAdd(this, _matcherLists);
      /** @type {Array<StrategyNames>} */
      __privateAdd(this, _defaultStrategyOrder, ["cssSelector", "ddgMatcher", "vendorRegex"]);
      /** @type {Record<MatchableStrings, string>} */
      __publicField(this, "activeElementStrings", {
        nameAttr: "",
        labelText: "",
        placeholderAttr: "",
        relatedText: "",
        id: ""
      });
      /**
       * Yield strings in the order in which they should be checked against.
       *
       * Note: some strategies may not want to accept all strings, which is
       * where `matchableStrings` helps. It defaults to when you see below but can
       * be overridden.
       *
       * For example, `nameAttr` is first, since this has the highest chance of matching
       * and then the rest are in decreasing order of value vs cost
       *
       * A generator function is used here to prevent any potentially expensive
       * lookups occurring if they are rare. For example if 90% of all matching never needs
       * to look at the output from `relatedText`, then the cost of computing it will be avoided.
       *
       * @param {HTMLInputElement|HTMLSelectElement} el
       * @param {HTMLElement} form
       * @returns {Record<MatchableStrings, string>}
       */
      __publicField(this, "_elementStringCache", /* @__PURE__ */ new WeakMap());
      __privateSet(this, _config, config);
      __privateSet(this, _vendorRegexRules, __privateGet(this, _config).strategies.vendorRegex.rules);
      __privateSet(this, _cssSelectors, __privateGet(this, _config).strategies.cssSelector.selectors);
      __privateSet(this, _ddgMatchers, __privateGet(this, _config).strategies.ddgMatcher.matchers);
      __privateSet(this, _matcherLists, {
        unknown: [],
        cc: [],
        id: [],
        password: [],
        username: [],
        emailAddress: []
      });
      for (const [listName, matcherNames] of Object.entries(__privateGet(this, _config).matchers.lists)) {
        for (const fieldName of matcherNames) {
          if (!__privateGet(this, _matcherLists)[listName]) {
            __privateGet(this, _matcherLists)[listName] = [];
          }
          __privateGet(this, _matcherLists)[listName].push(__privateGet(this, _config).matchers.fields[fieldName]);
        }
      }
    }
    /**
     * @param {HTMLInputElement|HTMLSelectElement} input
     * @param {HTMLElement} formEl
     */
    setActiveElementStrings(input, formEl) {
      this.activeElementStrings = this.getElementStrings(input, formEl);
    }
    /**
     * Try to access a 'vendor regex' by name
     * @param {string} regexName
     * @returns {RegExp | undefined}
     */
    vendorRegex(regexName) {
      const match = __privateGet(this, _vendorRegexRules)[regexName];
      if (!match) {
        console.warn("Vendor Regex not found for", regexName);
        return void 0;
      }
      return match;
    }
    /**
     * Strategies can have different lookup names. This returns the correct one
     * @param {MatcherTypeNames} matcherName
     * @param {StrategyNames} vendorRegex
     * @returns {MatcherTypeNames}
     */
    getStrategyLookupByType(matcherName, vendorRegex) {
      return __privateGet(this, _config).matchers.fields[matcherName]?.strategies[vendorRegex];
    }
    /**
     * Try to access a 'css selector' by name from configuration
     * @param {RequiredCssSelectors | string} selectorName
     * @returns {string};
     */
    cssSelector(selectorName) {
      const match = __privateGet(this, _cssSelectors)[selectorName];
      if (!match) {
        console.warn("CSS selector not found for %s, using a default value", selectorName);
        return "";
      }
      return match;
    }
    /**
     * Try to access a 'ddg matcher' by name from configuration
     * @param {MatcherTypeNames | string} matcherName
     * @returns {DDGMatcher | undefined}
     */
    ddgMatcher(matcherName) {
      const match = __privateGet(this, _ddgMatchers)[matcherName];
      if (!match) {
        console.warn("DDG matcher not found for", matcherName);
        return void 0;
      }
      return match;
    }
    /**
     * Returns the RegExp for the given matcherName, with proper flags
     * @param {AllDDGMatcherNames} matcherName
     * @returns {RegExp|undefined}
     */
    getDDGMatcherRegex(matcherName) {
      const matcher = this.ddgMatcher(matcherName);
      if (!matcher || !matcher.match) {
        console.warn("DDG matcher has unexpected format");
        return void 0;
      }
      return matcher?.match;
    }
    /**
     * Try to access a list of matchers by name - these are the ones collected in the constructor
     * @param {keyof MatcherLists} listName
     * @return {Matcher[]}
     */
    matcherList(listName) {
      const matcherList = __privateGet(this, _matcherLists)[listName];
      if (!matcherList) {
        console.warn("MatcherList not found for ", listName);
        return [];
      }
      return matcherList;
    }
    /**
     * Convert a list of matchers into a single CSS selector.
     *
     * This will consider all matchers in the list and if it
     * contains a CSS Selector it will be added to the final output
     *
     * @param {keyof MatcherLists} listName
     * @returns {string | undefined}
     */
    joinCssSelectors(listName) {
      const matcherList = this.matcherList(listName);
      if (!matcherList) {
        console.warn("Matcher list not found for", listName);
        return void 0;
      }
      const selectors = [];
      for (const matcher of matcherList) {
        if (matcher.strategies.cssSelector) {
          const css = this.cssSelector(matcher.strategies.cssSelector);
          if (css) {
            selectors.push(css);
          }
        }
      }
      return selectors.join(", ");
    }
    /**
     * Returns true if the field is visible and large enough
     * @param {keyof MatcherLists} matchedType
     * @param {HTMLInputElement} input
     * @returns {boolean}
     */
    isInputLargeEnough(matchedType, input) {
      const expectedDimensionBounds = dimensionBounds[matchedType];
      if (!expectedDimensionBounds) return true;
      const width = input.offsetWidth;
      const height = input.offsetHeight;
      const isHidden = height === 0 && width === 0;
      if (isHidden) return true;
      return width >= expectedDimensionBounds.minWidth;
    }
    /**
     * Tries to infer the input type for an input
     *
     * @param {HTMLInputElement|HTMLSelectElement} input
     * @param {HTMLElement} formEl
     * @param {SetInputTypeOpts} [opts]
     * @returns {SupportedTypes}
     */
    inferInputType(input, formEl, opts = {}) {
      const presetType = getInputType(input);
      if (presetType !== "unknown") {
        return presetType;
      }
      this.setActiveElementStrings(input, formEl);
      if (this.subtypeFromMatchers("unknown", input)) return "unknown";
      if (opts.isCCForm) {
        const subtype = this.subtypeFromMatchers("cc", input);
        if (subtype && isValidCreditCardSubtype(subtype)) {
          return `creditCards.${subtype}`;
        }
      }
      if (input instanceof HTMLInputElement) {
        if (this.subtypeFromMatchers("password", input)) {
          if ((input.type === "password" || // Some sites might not use the password type, but a placeholder should catch those cases
          // See test-forms/playpiknik_login.html
          safeRegexTest(/password/i, input.placeholder)) && input.name !== "email" && // pcsretirement.com, improper use of the for attribute
          input.name !== "Username") {
            return this.inferPasswordVariant(input, opts);
          }
        }
        if (this.subtypeFromMatchers("emailAddress", input)) {
          if (!this.isInputLargeEnough("emailAddress", input)) {
            if (shouldLog()) {
              console.log("Field matched for Email Address, but discarded because too small when scanned");
            }
            return "unknown";
          }
          if (opts.isLogin || opts.isHybrid) {
            return "credentials.username";
          }
          if (window.location.href.includes("https://accounts.google.com/v3/signin/identifier") && input.matches("[type=email][autocomplete=username]")) {
            return "credentials.username";
          }
          return "identities.emailAddress";
        }
        if (this.subtypeFromMatchers("username", input)) {
          return "credentials.username";
        }
      }
      const idSubtype = this.subtypeFromMatchers("id", input);
      if (idSubtype && isValidIdentitiesSubtype(idSubtype)) {
        return `identities.${idSubtype}`;
      }
      logUnmatched(input, this.activeElementStrings);
      return "unknown";
    }
    /**
     * @typedef {{
     *   isLogin?: boolean,
     *   isHybrid?: boolean,
     *   isCCForm?: boolean,
     *   isSignup?: boolean,
     *   hasCredentials?: boolean,
     *   supportsIdentitiesAutofill?: boolean,
     * }} SetInputTypeOpts
     */
    /**
     * Sets the input type as a data attribute to the element and returns it
     * @param {HTMLInputElement} input
     * @param {HTMLElement} formEl
     * @param {import('../site-specific-feature.js').default | null} siteSpecificFeature
     * @param {SetInputTypeOpts} [opts]
     * @returns {SupportedSubTypes | string}
     */
    setInputType(input, formEl, siteSpecificFeature, opts = {}) {
      const forcedInputType = siteSpecificFeature?.getForcedInputType(input);
      const type = forcedInputType || this.inferInputType(input, formEl, opts);
      input.setAttribute(ATTR_INPUT_TYPE, type);
      return type;
    }
    /**
     * Tries to infer input subtype, with checks in decreasing order of reliability
     * @param {keyof MatcherLists} listName
     * @param {HTMLInputElement|HTMLSelectElement} el
     * @return {MatcherTypeNames|undefined}
     */
    subtypeFromMatchers(listName, el) {
      const matchers = this.matcherList(listName);
      for (const strategyName of __privateGet(this, _defaultStrategyOrder)) {
        let result;
        for (const matcher of matchers) {
          const lookup = matcher.strategies[strategyName];
          if (!lookup) continue;
          if (strategyName === "cssSelector") {
            result = this.execCssSelector(lookup, el);
          }
          if (strategyName === "ddgMatcher") {
            result = this.execDDGMatcher(lookup);
          }
          if (strategyName === "vendorRegex") {
            result = this.execVendorRegex(lookup);
          }
          if (result?.matched) {
            logMatching(el, result);
            return matcher.type;
          }
          if (!result?.matched && result?.proceed === false) {
            logMatching(el, result);
            return void 0;
          }
        }
        if (result?.skip) {
          logMatching(el, result);
          break;
        }
      }
      return void 0;
    }
    /**
     * Returns the password type string including the variant
     * @param {HTMLInputElement} input
     * @param opts
     * @returns {'credentials.password.new'|'credentials.password.current'}
     */
    inferPasswordVariant(input, opts) {
      const attrsToCheck = [input.autocomplete, input.name, input.id];
      if (opts.isSignup && attrsToCheck.some((str) => safeRegexTest(/new.?password|password.?new/i, str))) {
        return "credentials.password.new";
      }
      if ((opts.isLogin || opts.isHybrid) && attrsToCheck.some((str) => safeRegexTest(/(current|old|previous).?password|password.?(current|old|previous)/i, str))) {
        return "credentials.password.current";
      }
      const newPasswordMatch = this.execDDGMatcher("newPassword");
      if (newPasswordMatch.matched) {
        return "credentials.password.new";
      }
      const currentPasswordMatch = this.execDDGMatcher("currentPassword");
      if (currentPasswordMatch.matched) {
        return "credentials.password.current";
      }
      if (opts.isLogin || opts.isHybrid) {
        return "credentials.password.current";
      }
      return "credentials.password.new";
    }
    /**
     * CSS selector matching just leverages the `.matches` method on elements
     *
     * @param {MatcherTypeNames} lookup
     * @param {HTMLInputElement|HTMLSelectElement} el
     * @returns {MatchingResult}
     */
    execCssSelector(lookup, el) {
      const selector = this.cssSelector(lookup);
      return {
        matched: el.matches(selector),
        strategyName: "cssSelector",
        matcherType: lookup
      };
    }
    /**
     * A DDG Matcher can have a `match` regex along with a `not` regex. This is done
     * to allow it to be driven by configuration as it avoids needing to invoke custom functions.
     *
     * todo: maxDigits was added as an edge-case when converting this over to be declarative, but I'm
     * unsure if it's actually needed. It's not urgent, but we should consider removing it if that's the case
     *
     * @param {MatcherTypeNames} lookup
     * @returns {MatchingResult}
     */
    execDDGMatcher(lookup) {
      const defaultResult = { matched: false, strategyName: "ddgMatcher", matcherType: lookup };
      const ddgMatcher = this.ddgMatcher(lookup);
      if (!ddgMatcher || !ddgMatcher.match) {
        return defaultResult;
      }
      const matchRexExp = this.getDDGMatcherRegex(lookup);
      if (!matchRexExp) {
        return defaultResult;
      }
      const requiredScore = ["match", "forceUnknown", "maxDigits"].filter((ddgMatcherProp) => ddgMatcherProp in ddgMatcher).length;
      const matchableStrings = ddgMatcher.matchableStrings || ["labelText", "placeholderAttr", "relatedText"];
      for (const stringName of matchableStrings) {
        const elementString = this.activeElementStrings[stringName];
        if (!elementString) continue;
        let score = 0;
        const result = {
          ...defaultResult,
          matchedString: elementString,
          matchedFrom: stringName
        };
        if (ddgMatcher.forceUnknown) {
          const notRegex = ddgMatcher.forceUnknown;
          if (!notRegex) {
            return { ...result, matched: false };
          }
          if (safeRegexTest(notRegex, elementString)) {
            return { ...result, matched: false, proceed: false };
          } else {
            score++;
          }
        }
        if (ddgMatcher.skip) {
          const skipRegex = ddgMatcher.skip;
          if (!skipRegex) {
            return { ...result, matched: false };
          }
          if (safeRegexTest(skipRegex, elementString)) {
            return { ...result, matched: false, skip: true };
          }
        }
        if (!safeRegexTest(matchRexExp, elementString)) {
          continue;
        }
        score++;
        if (ddgMatcher.maxDigits) {
          const digitLength = elementString.replace(/[^0-9]/g, "").length;
          if (digitLength > ddgMatcher.maxDigits) {
            return { ...result, matched: false };
          } else {
            score++;
          }
        }
        if (score === requiredScore) {
          return { ...result, matched: true };
        }
      }
      return defaultResult;
    }
    /**
     * If we get here, a firefox/vendor regex was given and we can execute it on the element
     * strings
     * @param {MatcherTypeNames} lookup
     * @return {MatchingResult}
     */
    execVendorRegex(lookup) {
      const defaultResult = { matched: false, strategyName: "vendorRegex", matcherType: lookup };
      const regex = this.vendorRegex(lookup);
      if (!regex) {
        return defaultResult;
      }
      const stringsToMatch = ["placeholderAttr", "nameAttr", "labelText", "id", "relatedText"];
      for (const stringName of stringsToMatch) {
        const elementString = this.activeElementStrings[stringName];
        if (!elementString) continue;
        if (safeRegexTest(regex, elementString)) {
          return {
            ...defaultResult,
            matched: true,
            matchedString: elementString,
            matchedFrom: stringName
          };
        }
      }
      return defaultResult;
    }
    getElementStrings(el, form) {
      if (this._elementStringCache.has(el)) {
        return this._elementStringCache.get(el);
      }
      const explicitLabelsText = getExplicitLabelsText(el);
      const next = {
        nameAttr: el.name,
        labelText: explicitLabelsText,
        placeholderAttr: el.placeholder || "",
        id: el.id,
        relatedText: explicitLabelsText ? "" : getRelatedText(el, form, this.cssSelector("formInputsSelector"))
      };
      this._elementStringCache.set(el, next);
      return next;
    }
    clear() {
      this._elementStringCache = /* @__PURE__ */ new WeakMap();
    }
    /**
     * Only used for testing
     * @param {HTMLInputElement|HTMLSelectElement} input
     * @param {HTMLElement} form
     * @returns {Matching}
     */
    forInput(input, form) {
      this.setActiveElementStrings(input, form);
      return this;
    }
  };
  _config = new WeakMap();
  _cssSelectors = new WeakMap();
  _ddgMatchers = new WeakMap();
  _vendorRegexRules = new WeakMap();
  _matcherLists = new WeakMap();
  _defaultStrategyOrder = new WeakMap();
  /**
   * @type {MatchingConfiguration}
   */
  __publicField(Matching, "emptyConfig", {
    matchers: {
      lists: {},
      fields: {}
    },
    strategies: {
      vendorRegex: {
        rules: {},
        ruleSets: []
      },
      ddgMatcher: {
        matchers: {}
      },
      cssSelector: {
        selectors: {}
      }
    }
  });
  function getInputType(input) {
    const attr = input?.getAttribute(ATTR_INPUT_TYPE);
    if (isValidSupportedType(attr)) {
      return attr;
    }
    return "unknown";
  }
  function getMainTypeFromType(type) {
    const mainType = type.split(".")[0];
    switch (mainType) {
      case "credentials":
      case "creditCards":
      case "identities":
        return mainType;
    }
    return "unknown";
  }
  var getInputMainType = (input) => getMainTypeFromType(getInputType(input));
  var supportedIdentitiesSubtypes = (
    /** @type {const} */
    [
      "emailAddress",
      "firstName",
      "middleName",
      "lastName",
      "fullName",
      "phone",
      "addressStreet",
      "addressStreet2",
      "addressCity",
      "addressProvince",
      "addressPostalCode",
      "addressCountryCode",
      "birthdayDay",
      "birthdayMonth",
      "birthdayYear"
    ]
  );
  function isValidIdentitiesSubtype(supportedType) {
    return supportedIdentitiesSubtypes.includes(supportedType);
  }
  var supportedCreditCardSubtypes = (
    /** @type {const} */
    [
      "cardName",
      "cardNumber",
      "cardSecurityCode",
      "expirationMonth",
      "expirationYear",
      "expiration"
    ]
  );
  function isValidCreditCardSubtype(supportedType) {
    return supportedCreditCardSubtypes.includes(supportedType);
  }
  var supportedCredentialsSubtypes = (
    /** @type {const} */
    ["password", "password.new", "password.current", "username"]
  );
  var supportedVariants = (
    /** @type {const} */
    ["new", "current"]
  );
  function isValidCredentialsSubtype(supportedType) {
    return supportedCredentialsSubtypes.includes(supportedType);
  }
  var supportedTypes = [
    ...supportedIdentitiesSubtypes.map((type) => `identities.${type}`),
    ...supportedCreditCardSubtypes.map((type) => `creditCards.${type}`),
    ...supportedCredentialsSubtypes.map((type) => `credentials.${type}`)
  ];
  function getSubtypeFromType(type) {
    const subType = type?.split(".")[1];
    const validType = isValidSubtype(subType);
    return validType ? subType : "unknown";
  }
  function getVariantFromType(type) {
    const variant = type?.split(".")[2];
    const validVariant = isValidVariant(variant);
    return validVariant ? variant : "";
  }
  function isValidSubtype(supportedSubType) {
    return isValidIdentitiesSubtype(supportedSubType) || isValidCreditCardSubtype(supportedSubType) || isValidCredentialsSubtype(supportedSubType);
  }
  function isValidSupportedType(supportedType) {
    return supportedTypes.includes(supportedType);
  }
  function isValidVariant(supportedVariant) {
    return supportedVariants.includes(supportedVariant);
  }
  function getInputSubtype(input) {
    const type = getInputType(input);
    return getSubtypeFromType(type);
  }
  function getInputVariant(input) {
    const type = getInputType(input);
    return getVariantFromType(type);
  }
  var removeExcessWhitespace = (string = "", textLengthCutoff = TEXT_LENGTH_CUTOFF) => {
    string = string?.trim() || "";
    if (!string || string.length > textLengthCutoff + 50) return "";
    return string.replace(/\n/g, " ").replace(/\s{2,}/g, " ");
  };
  var getExplicitLabelsText = (el) => {
    const labelTextCandidates = [];
    for (const label of el.labels || []) {
      labelTextCandidates.push(...extractElementStrings(label));
    }
    if (el.hasAttribute("aria-label")) {
      labelTextCandidates.push(removeExcessWhitespace(el.getAttribute("aria-label")));
    }
    const ariaLabelAttr = removeExcessWhitespace(el.getAttribute("aria-labelled") || el.getAttribute("aria-labelledby"));
    if (ariaLabelAttr) {
      const labelledByElement = document.getElementById(ariaLabelAttr);
      if (labelledByElement) {
        labelTextCandidates.push(...extractElementStrings(labelledByElement));
      }
    }
    const filteredLabels = labelTextCandidates.filter((string) => string.length < 65);
    if (filteredLabels.length > 0) {
      return filteredLabels.join(" ");
    }
    return "";
  };
  var recursiveGetPreviousElSibling = (el) => {
    const previousEl = el.previousElementSibling;
    if (!previousEl) return null;
    if (EXCLUDED_TAGS.includes(previousEl.tagName)) {
      return recursiveGetPreviousElSibling(previousEl);
    }
    return previousEl;
  };
  var getRelatedText = (el, form, cssSelector) => {
    let scope = getLargestMeaningfulContainer(el, form, cssSelector);
    if (scope === el) {
      const previousEl = recursiveGetPreviousElSibling(el);
      if (previousEl instanceof HTMLElement) {
        scope = previousEl;
      }
      if (scope === el || scope instanceof HTMLSelectElement) {
        if (el.previousSibling instanceof Text) {
          return removeExcessWhitespace(el.previousSibling.textContent);
        }
        return "";
      }
    }
    if (scope === el || scope instanceof HTMLSelectElement) {
      if (el.previousSibling instanceof Text) {
        return removeExcessWhitespace(el.previousSibling.textContent);
      }
      return "";
    }
    let trimmedText = "";
    const label = scope.querySelector("label");
    if (label) {
      trimmedText = extractElementStrings(label).join(" ");
    } else {
      trimmedText = extractElementStrings(scope).join(" ");
    }
    if (trimmedText.length < TEXT_LENGTH_CUTOFF) return trimmedText;
    return "";
  };
  var getLargestMeaningfulContainer = (el, form, cssSelector) => {
    const parentElement = el.parentElement;
    if (!parentElement || el === form || !cssSelector) return el;
    const inputsInParentsScope = parentElement.querySelectorAll(cssSelector);
    if (inputsInParentsScope.length === 1) {
      const labelInParentScope = parentElement.querySelector("label");
      if (labelInParentScope?.textContent?.trim()) {
        return parentElement;
      }
      return getLargestMeaningfulContainer(parentElement, form, cssSelector);
    }
    return el;
  };
  var matchInPlaceholderAndLabels = (input, regex, form, cssSelector) => {
    return input.placeholder?.match(regex) || getExplicitLabelsText(input).match(regex) || getRelatedText(input, form, cssSelector).match(regex);
  };
  var checkPlaceholderAndLabels = (input, regex, form, cssSelector) => {
    return !!matchInPlaceholderAndLabels(input, regex, form, cssSelector);
  };
  function createMatching() {
    return new Matching(matchingConfiguration);
  }

  // node_modules/@duckduckgo/content-scope-scripts/injected/src/captured-globals.js
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

  // node_modules/@duckduckgo/content-scope-scripts/injected/src/utils.js
  var globalObj = typeof window === "undefined" ? globalThis : window;
  var Error3 = globalObj.Error;
  var originalWindowDispatchEvent = typeof window === "undefined" ? null : window.dispatchEvent.bind(window);
  function getTabHostname() {
    let framingOrigin = null;
    try {
      framingOrigin = globalThis.top.location.href;
    } catch {
      framingOrigin = globalThis.document.referrer;
    }
    if ("ancestorOrigins" in globalThis.location && globalThis.location.ancestorOrigins.length) {
      framingOrigin = globalThis.location.ancestorOrigins.item(globalThis.location.ancestorOrigins.length - 1);
    }
    try {
      framingOrigin = new URL(framingOrigin).hostname;
    } catch {
      framingOrigin = null;
    }
    return framingOrigin;
  }
  function matchHostname(hostname, exceptionDomain) {
    return hostname === exceptionDomain || hostname.endsWith(`.${exceptionDomain}`);
  }
  function camelcase(dashCaseText) {
    return dashCaseText.replace(/-(.)/g, (_, letter) => {
      return letter.toUpperCase();
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
    while (domainParts.length > 1 && !unprotectedDomain) {
      const partialDomain = domainParts.join(".");
      unprotectedDomain = featureList.filter((domain) => domain.domain === partialDomain).length > 0;
      domainParts.shift();
    }
    return unprotectedDomain;
  }
  function computeLimitedSiteObject() {
    const topLevelHostname = getTabHostname();
    return {
      domain: topLevelHostname
    };
  }
  function getPlatformVersion(preferences) {
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
  function processConfig(data, userList, preferences, platformSpecificFeatures = []) {
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
    const enabledFeatures = computeEnabledFeatures(data, topLevelHostname, preferences.platform?.version, platformSpecificFeatures);
    const isBroken = isUnprotectedDomain(topLevelHostname, data.unprotectedTemporary);
    output.site = Object.assign(site, {
      isBroken,
      allowlisted,
      enabledFeatures
    });
    output.featureSettings = parseFeatureSettings(data, enabledFeatures);
    output.bundledConfig = data;
    return output;
  }
  function computeEnabledFeatures(data, topLevelHostname, platformVersion, platformSpecificFeatures = []) {
    const remoteFeatureNames = Object.keys(data.features);
    const platformSpecificFeaturesNotInRemoteConfig = platformSpecificFeatures.filter(
      (featureName) => !remoteFeatureNames.includes(featureName)
    );
    const enabledFeatures = remoteFeatureNames.filter((featureName) => {
      const feature = data.features[featureName];
      if (feature.minSupportedVersion && platformVersion) {
        if (!isSupportedVersion(feature.minSupportedVersion, platformVersion)) {
          return false;
        }
      }
      return feature.state === "enabled" && !isUnprotectedDomain(topLevelHostname, feature.exceptions);
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
      featureSettings[featureName] = data.features[featureName].settings;
    });
    return featureSettings;
  }

  // src/autofill-utils.js
  var SIGN_IN_MSG = { signMeIn: true };
  var notifyWebApp = (message) => {
    window.postMessage(message, window.origin);
  };
  var sendAndWaitForAnswer = (msgOrFn, expectedResponse) => {
    if (typeof msgOrFn === "function") {
      msgOrFn();
    } else {
      window.postMessage(msgOrFn, window.origin);
    }
    return new Promise((resolve) => {
      const handler = (e) => {
        if (e.origin !== window.origin) return;
        if (!e.data || e.data && !(e.data[expectedResponse] || e.data.type === expectedResponse)) return;
        resolve(e.data);
        window.removeEventListener("message", handler);
      };
      window.addEventListener("message", handler);
    });
  };
  var autofillEnabled = (globalConfig) => {
    if (!globalConfig.contentScope) {
      return true;
    }
    if ("site" in globalConfig.contentScope) {
      const enabled = isAutofillEnabledFromProcessedConfig(globalConfig.contentScope);
      return enabled;
    }
    const { contentScope, userUnprotectedDomains, userPreferences } = globalConfig;
    if (!userPreferences) return false;
    const processedConfig = processConfig(contentScope, userUnprotectedDomains, userPreferences);
    return isAutofillEnabledFromProcessedConfig(processedConfig);
  };
  var isAutofillEnabledFromProcessedConfig = (processedConfig) => {
    const site = processedConfig.site;
    if (site.isBroken || !site.enabledFeatures.includes("autofill")) {
      if (shouldLog()) {
        console.log("\u26A0\uFE0F Autofill disabled by remote config");
      }
      return false;
    }
    return true;
  };
  var isIncontextSignupEnabledFromProcessedConfig = (processedConfig) => {
    const site = processedConfig.site;
    if (site.isBroken || !site.enabledFeatures.includes("incontextSignup")) {
      if (shouldLog()) {
        console.log("\u26A0\uFE0F In-context signup disabled by remote config");
      }
      return false;
    }
    return true;
  };
  var originalSet = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
  var setValueForInput = (el, val, config) => {
    if (!config?.isAndroid) el.focus();
    el.dispatchEvent(new Event("keydown", { bubbles: true }));
    originalSet?.call(el, val);
    const events = [
      new Event("input", { bubbles: true }),
      // todo(Shane): Not sending a 'key' property on these events can cause exceptions on 3rd party listeners that expect it
      new Event("keyup", { bubbles: true }),
      new Event("change", { bubbles: true })
    ];
    events.forEach((ev) => el.dispatchEvent(ev));
    originalSet?.call(el, val);
    events.forEach((ev) => el.dispatchEvent(ev));
    el.blur();
    return true;
  };
  var fireEventsOnSelect = (el) => {
    const events = [
      new Event("mousedown", { bubbles: true }),
      new Event("mouseup", { bubbles: true }),
      new Event("click", { bubbles: true }),
      new Event("change", { bubbles: true })
    ];
    events.forEach((ev) => el.dispatchEvent(ev));
    events.forEach((ev) => el.dispatchEvent(ev));
    el.blur();
  };
  var setValueForSelect = (el, val) => {
    const subtype = getInputSubtype(el);
    const isMonth = subtype.includes("Month");
    const isZeroBasedNumber = isMonth && el.options[0].value === "0" && el.options.length === 12;
    const stringVal = String(val);
    const numberVal = Number(val);
    for (const option of el.options) {
      let value = option.value;
      if (isZeroBasedNumber) {
        value = `${Number(value) + 1}`;
      }
      if (value === stringVal || Number(value) === numberVal) {
        if (option.selected) return false;
        option.selected = true;
        fireEventsOnSelect(el);
        return true;
      }
    }
    for (const option of el.options) {
      if (option.innerText === stringVal || Number(option.innerText) === numberVal || safeRegexTest(new RegExp(stringVal, "i"), option.innerText)) {
        if (option.selected) return false;
        option.selected = true;
        fireEventsOnSelect(el);
        return true;
      }
    }
    return false;
  };
  var setValue = (el, val, config) => {
    if (el instanceof HTMLInputElement) return setValueForInput(el, val, config);
    if (el instanceof HTMLSelectElement) return setValueForSelect(el, val);
    return false;
  };
  var safeExecute = (el, fn, _opts = {}) => {
    const intObs = new IntersectionObserver(
      (changes) => {
        for (const change of changes) {
          if (typeof change.isVisible === "undefined") {
            change.isVisible = true;
          }
          if (change.isIntersecting) {
            fn();
          }
        }
        intObs.disconnect();
      },
      { trackVisibility: true, delay: 100 }
    );
    intObs.observe(el);
  };
  var isPotentiallyViewable = (el) => {
    const computedStyle = window.getComputedStyle(el);
    const opacity = parseFloat(computedStyle.getPropertyValue("opacity") || "1");
    const visibility = computedStyle.getPropertyValue("visibility");
    const opacityThreshold = 0.6;
    return el.clientWidth !== 0 && el.clientHeight !== 0 && opacity > opacityThreshold && visibility !== "hidden";
  };
  var getDaxBoundingBox = (input) => {
    const { right: inputRight, top: inputTop, height: inputHeight } = input.getBoundingClientRect();
    const inputRightPadding = parseInt(getComputedStyle(input).paddingRight);
    const width = 30;
    const height = 30;
    const top = inputTop + (inputHeight - height) / 2;
    const right = inputRight - inputRightPadding;
    const left = right - width;
    const bottom = top + height;
    return { bottom, height, left, right, top, width, x: left, y: top };
  };
  var isEventWithinDax = (e, input) => {
    const { left, right, top, bottom } = getDaxBoundingBox(input);
    const withinX = e.clientX >= left && e.clientX <= right;
    const withinY = e.clientY >= top && e.clientY <= bottom;
    return withinX && withinY;
  };
  var addInlineStyles = (el, styles) => Object.entries(styles).forEach(([property, val]) => el.style.setProperty(property, val, "important"));
  var removeInlineStyles = (el, styles) => Object.keys(styles).forEach((property) => el.style.removeProperty(property));
  var ADDRESS_DOMAIN = "@duck.com";
  var formatDuckAddress = (address) => address + ADDRESS_DOMAIN;
  function escapeXML(str) {
    const replacements = { "&": "&amp;", '"': "&quot;", "'": "&apos;", "<": "&lt;", ">": "&gt;", "/": "&#x2F;" };
    return String(str).replace(/[&"'<>/]/g, (m) => replacements[m]);
  }
  var isLikelyASubmitButton = (el, matching) => {
    const text = getTextShallow(el);
    const ariaLabel = el.getAttribute("aria-label") || "";
    const dataTestId = el.getAttribute("data-test-id") || "";
    if ((el.getAttribute("type") === "submit" || // is explicitly set as "submit"
    el.getAttribute("name") === "submit") && // is called "submit"
    !safeRegexTest(matching.getDDGMatcherRegex("submitButtonUnlikelyRegex"), text + " " + ariaLabel))
      return true;
    return (safeRegexTest(/primary|submit/i, el.className) || // has high-signal submit classes
    safeRegexTest(/submit/i, dataTestId) || safeRegexTest(matching.getDDGMatcherRegex("submitButtonRegex"), text) || // has high-signal text
    el.offsetHeight * el.offsetWidth >= 1e4 && !safeRegexTest(/secondary/i, el.className)) && // it's a large element 250x40px
    el.offsetHeight * el.offsetWidth >= 2e3 && // it's not a very small button like inline links and such
    !safeRegexTest(matching.getDDGMatcherRegex("submitButtonUnlikelyRegex"), text + " " + ariaLabel);
  };
  var buttonMatchesFormType = (el, formObj) => {
    if (formObj.isLogin) {
      return !safeRegexTest(/sign.?up|register|join/i, el.textContent || "");
    } else if (formObj.isSignup) {
      return !safeRegexTest(/(log|sign).?([io])n/i, el.textContent || "");
    } else {
      return true;
    }
  };
  var buttonInputTypes = ["submit", "button"];
  var getTextShallow = (el) => {
    if (el instanceof HTMLButtonElement) return removeExcessWhitespace(el.textContent);
    if (el instanceof HTMLInputElement) {
      if (buttonInputTypes.includes(el.type)) {
        return el.value;
      }
      if (el.type === "image") {
        return removeExcessWhitespace(el.alt || el.value || el.title || el.name);
      }
    }
    let text = "";
    for (const childNode of el.childNodes) {
      if (childNode instanceof Text) {
        text += " " + childNode.textContent;
      }
    }
    return removeExcessWhitespace(text);
  };
  function isLocalNetwork(hostname = window.location.hostname) {
    return ["localhost", "", "::1"].includes(hostname) || hostname.includes("127.0.0.1") || hostname.includes("192.168.") || hostname.startsWith("10.0.") || hostname.endsWith(".local") || hostname.endsWith(".internal");
  }
  var tldrs = /\.(?:c(?:o(?:m|op)?|at?|[iykgdmnxruhcfzvl])|o(?:rg|m)|n(?:et?|a(?:me)?|[ucgozrfpil])|e(?:d?u|[gechstr])|i(?:n(?:t|fo)?|[stqldroem])|m(?:o(?:bi)?|u(?:seum)?|i?l|[mcyvtsqhaerngxzfpwkd])|g(?:ov|[glqeriabtshdfmuywnp])|b(?:iz?|[drovfhtaywmzjsgbenl])|t(?:r(?:avel)?|[ncmfzdvkopthjwg]|e?l)|k[iemygznhwrp]|s[jtvberindlucygkhaozm]|u[gymszka]|h[nmutkr]|r[owesu]|d[kmzoej]|a(?:e(?:ro)?|r(?:pa)?|[qofiumsgzlwcnxdt])|p(?:ro?|[sgnthfymakwle])|v[aegiucn]|l[sayuvikcbrt]|j(?:o(?:bs)?|[mep])|w[fs]|z[amw]|f[rijkom]|y[eut]|qa)$/i;
  function isValidTLD(hostname = window.location.hostname) {
    return tldrs.test(hostname) || hostname === "fill.dev";
  }
  var wasAutofilledByChrome = (input) => {
    try {
      return input.matches("input:-internal-autofill-selected");
    } catch (e) {
      return false;
    }
  };
  function shouldLog() {
    return readDebugSetting("ddg-autofill-debug");
  }
  function shouldLogPerformance() {
    return readDebugSetting("ddg-autofill-perf");
  }
  function readDebugSetting(setting) {
    try {
      return window.sessionStorage?.getItem(setting) === "true";
    } catch (e) {
      return false;
    }
  }
  function logPerformance(markName) {
    if (shouldLogPerformance()) {
      const measurement = window.performance?.measure(`${markName}:init`, `${markName}:init:start`, `${markName}:init:end`);
      console.log(`${markName} took ${Math.round(measurement?.duration)}ms`);
      window.performance?.clearMarks();
    }
  }
  function whenIdle(callback) {
    let timer;
    return (...args) => {
      cancelIdleCallback(timer);
      timer = requestIdleCallback(() => callback.apply(this, args));
    };
  }
  function truncateFromMiddle(string, totalLength = 30) {
    if (totalLength < 4) {
      throw new Error("Do not use with strings shorter than 4");
    }
    if (string.length <= totalLength) return string;
    const truncated = string.slice(0, totalLength / 2).concat("\u2026", string.slice(totalLength / -2));
    return truncated;
  }
  function isFormLikelyToBeUsedAsPageWrapper(form) {
    if (form.parentElement !== document.body) return false;
    const formChildren = form.querySelectorAll("*").length;
    if (formChildren < 100) return false;
    const bodyChildren = document.body.querySelectorAll("*").length;
    const formChildrenPercentage = formChildren * 100 / bodyChildren;
    return formChildrenPercentage > 50;
  }
  function safeRegexTest(regex, string, textLengthCutoff = constants.TEXT_LENGTH_CUTOFF) {
    if (!string || !regex || string.length > textLengthCutoff) return false;
    return regex.test(string);
  }
  function pierceShadowTree(event, wantedTargetType) {
    const { target } = event;
    if (!(target instanceof Element) || !target?.shadowRoot || !event.composedPath) return target;
    const clickStack = event.composedPath();
    if (!wantedTargetType) {
      return clickStack[0];
    }
    return clickStack.find((el) => el instanceof wantedTargetType) || target;
  }
  function getActiveElement(root = document) {
    const activeElement = root.activeElement;
    if (!(activeElement instanceof Element) || !activeElement.shadowRoot) return activeElement;
    const innerActiveElement = activeElement.shadowRoot.activeElement;
    if (innerActiveElement?.shadowRoot) {
      return getActiveElement(innerActiveElement.shadowRoot);
    }
    return innerActiveElement;
  }
  function runWithTreeWalker(root, callback) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT);
    let node = walker.currentNode;
    while (node) {
      const result = callback(node);
      if (result === true) return true;
      node = walker.nextNode();
    }
    return false;
  }
  function findElementsInShadowTree(root, selector) {
    const shadowElements = [];
    runWithTreeWalker(root, (node) => {
      if (node instanceof HTMLElement && node.shadowRoot) {
        shadowElements.push(...node.shadowRoot.querySelectorAll(selector));
      }
    });
    return shadowElements;
  }
  function getFormControlElements(form, selector) {
    if (form instanceof HTMLFormElement && form.elements != null && Symbol.iterator in Object(form.elements)) {
      const formControls = [...form.elements].filter((el) => el.matches(selector));
      return [...formControls];
    } else {
      return null;
    }
  }
  function queryElementsWithShadow(element, selector, forceScanShadowTree = false) {
    const elements = element.querySelectorAll(selector);
    if (forceScanShadowTree || elements.length === 0) {
      return [...elements, ...findElementsInShadowTree(element, selector)];
    }
    return [...elements];
  }
  function getUsernameLikeIdentity(identities, creditCards) {
    if (identities?.emailAddress) {
      return identities.emailAddress;
    }
    if (identities && Object.keys(identities).length === 1 && Boolean(identities.phone)) {
      return identities.phone;
    }
    if (creditCards && Object.keys(creditCards).length === 1 && Boolean(creditCards.cardNumber)) {
      return creditCards.cardNumber;
    }
  }
  function containsShadowedTarget(container, target) {
    if (container.contains(target)) return true;
    const targetRoot = target.getRootNode();
    const foundInShadow = runWithTreeWalker(container, (node) => {
      if (targetRoot instanceof ShadowRoot && node.contains(targetRoot.host)) {
        return true;
      }
      return false;
    });
    return foundInShadow;
  }

  // src/Form/countryNames.js
  var COUNTRY_CODES_TO_NAMES = {
    AC: "Ascension Island",
    AD: "Andorra",
    AE: "United Arab Emirates",
    AF: "Afghanistan",
    AG: "Antigua & Barbuda",
    AI: "Anguilla",
    AL: "Albania",
    AM: "Armenia",
    AN: "Cura\xE7ao",
    AO: "Angola",
    AQ: "Antarctica",
    AR: "Argentina",
    AS: "American Samoa",
    AT: "Austria",
    AU: "Australia",
    AW: "Aruba",
    AX: "\xC5land Islands",
    AZ: "Azerbaijan",
    BA: "Bosnia & Herzegovina",
    BB: "Barbados",
    BD: "Bangladesh",
    BE: "Belgium",
    BF: "Burkina Faso",
    BG: "Bulgaria",
    BH: "Bahrain",
    BI: "Burundi",
    BJ: "Benin",
    BL: "St. Barth\xE9lemy",
    BM: "Bermuda",
    BN: "Brunei",
    BO: "Bolivia",
    BQ: "Caribbean Netherlands",
    BR: "Brazil",
    BS: "Bahamas",
    BT: "Bhutan",
    BU: "Myanmar (Burma)",
    BV: "Bouvet Island",
    BW: "Botswana",
    BY: "Belarus",
    BZ: "Belize",
    CA: "Canada",
    CC: "Cocos (Keeling) Islands",
    CD: "Congo - Kinshasa",
    CF: "Central African Republic",
    CG: "Congo - Brazzaville",
    CH: "Switzerland",
    CI: "C\xF4te d\u2019Ivoire",
    CK: "Cook Islands",
    CL: "Chile",
    CM: "Cameroon",
    CN: "China mainland",
    CO: "Colombia",
    CP: "Clipperton Island",
    CR: "Costa Rica",
    CS: "Serbia",
    CU: "Cuba",
    CV: "Cape Verde",
    CW: "Cura\xE7ao",
    CX: "Christmas Island",
    CY: "Cyprus",
    CZ: "Czechia",
    DD: "Germany",
    DE: "Germany",
    DG: "Diego Garcia",
    DJ: "Djibouti",
    DK: "Denmark",
    DM: "Dominica",
    DO: "Dominican Republic",
    DY: "Benin",
    DZ: "Algeria",
    EA: "Ceuta & Melilla",
    EC: "Ecuador",
    EE: "Estonia",
    EG: "Egypt",
    EH: "Western Sahara",
    ER: "Eritrea",
    ES: "Spain",
    ET: "Ethiopia",
    EU: "European Union",
    EZ: "Eurozone",
    FI: "Finland",
    FJ: "Fiji",
    FK: "Falkland Islands",
    FM: "Micronesia",
    FO: "Faroe Islands",
    FR: "France",
    FX: "France",
    GA: "Gabon",
    GB: "United Kingdom",
    GD: "Grenada",
    GE: "Georgia",
    GF: "French Guiana",
    GG: "Guernsey",
    GH: "Ghana",
    GI: "Gibraltar",
    GL: "Greenland",
    GM: "Gambia",
    GN: "Guinea",
    GP: "Guadeloupe",
    GQ: "Equatorial Guinea",
    GR: "Greece",
    GS: "So. Georgia & So. Sandwich Isl.",
    GT: "Guatemala",
    GU: "Guam",
    GW: "Guinea-Bissau",
    GY: "Guyana",
    HK: "Hong Kong",
    HM: "Heard & McDonald Islands",
    HN: "Honduras",
    HR: "Croatia",
    HT: "Haiti",
    HU: "Hungary",
    HV: "Burkina Faso",
    IC: "Canary Islands",
    ID: "Indonesia",
    IE: "Ireland",
    IL: "Israel",
    IM: "Isle of Man",
    IN: "India",
    IO: "Chagos Archipelago",
    IQ: "Iraq",
    IR: "Iran",
    IS: "Iceland",
    IT: "Italy",
    JE: "Jersey",
    JM: "Jamaica",
    JO: "Jordan",
    JP: "Japan",
    KE: "Kenya",
    KG: "Kyrgyzstan",
    KH: "Cambodia",
    KI: "Kiribati",
    KM: "Comoros",
    KN: "St. Kitts & Nevis",
    KP: "North Korea",
    KR: "South Korea",
    KW: "Kuwait",
    KY: "Cayman Islands",
    KZ: "Kazakhstan",
    LA: "Laos",
    LB: "Lebanon",
    LC: "St. Lucia",
    LI: "Liechtenstein",
    LK: "Sri Lanka",
    LR: "Liberia",
    LS: "Lesotho",
    LT: "Lithuania",
    LU: "Luxembourg",
    LV: "Latvia",
    LY: "Libya",
    MA: "Morocco",
    MC: "Monaco",
    MD: "Moldova",
    ME: "Montenegro",
    MF: "St. Martin",
    MG: "Madagascar",
    MH: "Marshall Islands",
    MK: "North Macedonia",
    ML: "Mali",
    MM: "Myanmar (Burma)",
    MN: "Mongolia",
    MO: "Macao",
    MP: "Northern Mariana Islands",
    MQ: "Martinique",
    MR: "Mauritania",
    MS: "Montserrat",
    MT: "Malta",
    MU: "Mauritius",
    MV: "Maldives",
    MW: "Malawi",
    MX: "Mexico",
    MY: "Malaysia",
    MZ: "Mozambique",
    NA: "Namibia",
    NC: "New Caledonia",
    NE: "Niger",
    NF: "Norfolk Island",
    NG: "Nigeria",
    NH: "Vanuatu",
    NI: "Nicaragua",
    NL: "Netherlands",
    NO: "Norway",
    NP: "Nepal",
    NR: "Nauru",
    NU: "Niue",
    NZ: "New Zealand",
    OM: "Oman",
    PA: "Panama",
    PE: "Peru",
    PF: "French Polynesia",
    PG: "Papua New Guinea",
    PH: "Philippines",
    PK: "Pakistan",
    PL: "Poland",
    PM: "St. Pierre & Miquelon",
    PN: "Pitcairn Islands",
    PR: "Puerto Rico",
    PS: "Palestinian Territories",
    PT: "Portugal",
    PW: "Palau",
    PY: "Paraguay",
    QA: "Qatar",
    QO: "Outlying Oceania",
    RE: "R\xE9union",
    RH: "Zimbabwe",
    RO: "Romania",
    RS: "Serbia",
    RU: "Russia",
    RW: "Rwanda",
    SA: "Saudi Arabia",
    SB: "Solomon Islands",
    SC: "Seychelles",
    SD: "Sudan",
    SE: "Sweden",
    SG: "Singapore",
    SH: "St. Helena",
    SI: "Slovenia",
    SJ: "Svalbard & Jan Mayen",
    SK: "Slovakia",
    SL: "Sierra Leone",
    SM: "San Marino",
    SN: "Senegal",
    SO: "Somalia",
    SR: "Suriname",
    SS: "South Sudan",
    ST: "S\xE3o Tom\xE9 & Pr\xEDncipe",
    SU: "Russia",
    SV: "El Salvador",
    SX: "Sint Maarten",
    SY: "Syria",
    SZ: "Eswatini",
    TA: "Tristan da Cunha",
    TC: "Turks & Caicos Islands",
    TD: "Chad",
    TF: "French Southern Territories",
    TG: "Togo",
    TH: "Thailand",
    TJ: "Tajikistan",
    TK: "Tokelau",
    TL: "Timor-Leste",
    TM: "Turkmenistan",
    TN: "Tunisia",
    TO: "Tonga",
    TP: "Timor-Leste",
    TR: "Turkey",
    TT: "Trinidad & Tobago",
    TV: "Tuvalu",
    TW: "Taiwan",
    TZ: "Tanzania",
    UA: "Ukraine",
    UG: "Uganda",
    UK: "United Kingdom",
    UM: "U.S. Outlying Islands",
    UN: "United Nations",
    US: "United States",
    UY: "Uruguay",
    UZ: "Uzbekistan",
    VA: "Vatican City",
    VC: "St. Vincent & Grenadines",
    VD: "Vietnam",
    VE: "Venezuela",
    VG: "British Virgin Islands",
    VI: "U.S. Virgin Islands",
    VN: "Vietnam",
    VU: "Vanuatu",
    WF: "Wallis & Futuna",
    WS: "Samoa",
    XA: "Pseudo-Accents",
    XB: "Pseudo-Bidi",
    XK: "Kosovo",
    YD: "Yemen",
    YE: "Yemen",
    YT: "Mayotte",
    YU: "Serbia",
    ZA: "South Africa",
    ZM: "Zambia",
    ZR: "Congo - Kinshasa",
    ZW: "Zimbabwe",
    ZZ: "Unknown Region"
  };
  var COUNTRY_NAMES_TO_CODES = {
    "Ascension Island": "AC",
    Andorra: "AD",
    "United Arab Emirates": "AE",
    Afghanistan: "AF",
    "Antigua & Barbuda": "AG",
    Anguilla: "AI",
    Albania: "AL",
    Armenia: "AM",
    Cura\u00E7ao: "CW",
    Angola: "AO",
    Antarctica: "AQ",
    Argentina: "AR",
    "American Samoa": "AS",
    Austria: "AT",
    Australia: "AU",
    Aruba: "AW",
    "\xC5land Islands": "AX",
    Azerbaijan: "AZ",
    "Bosnia & Herzegovina": "BA",
    Barbados: "BB",
    Bangladesh: "BD",
    Belgium: "BE",
    "Burkina Faso": "HV",
    Bulgaria: "BG",
    Bahrain: "BH",
    Burundi: "BI",
    Benin: "DY",
    "St. Barth\xE9lemy": "BL",
    Bermuda: "BM",
    Brunei: "BN",
    Bolivia: "BO",
    "Caribbean Netherlands": "BQ",
    Brazil: "BR",
    Bahamas: "BS",
    Bhutan: "BT",
    "Myanmar (Burma)": "MM",
    "Bouvet Island": "BV",
    Botswana: "BW",
    Belarus: "BY",
    Belize: "BZ",
    Canada: "CA",
    "Cocos (Keeling) Islands": "CC",
    "Congo - Kinshasa": "ZR",
    "Central African Republic": "CF",
    "Congo - Brazzaville": "CG",
    Switzerland: "CH",
    "C\xF4te d\u2019Ivoire": "CI",
    "Cook Islands": "CK",
    Chile: "CL",
    Cameroon: "CM",
    "China mainland": "CN",
    Colombia: "CO",
    "Clipperton Island": "CP",
    "Costa Rica": "CR",
    Serbia: "YU",
    Cuba: "CU",
    "Cape Verde": "CV",
    "Christmas Island": "CX",
    Cyprus: "CY",
    Czechia: "CZ",
    Germany: "DE",
    "Diego Garcia": "DG",
    Djibouti: "DJ",
    Denmark: "DK",
    Dominica: "DM",
    "Dominican Republic": "DO",
    Algeria: "DZ",
    "Ceuta & Melilla": "EA",
    Ecuador: "EC",
    Estonia: "EE",
    Egypt: "EG",
    "Western Sahara": "EH",
    Eritrea: "ER",
    Spain: "ES",
    Ethiopia: "ET",
    "European Union": "EU",
    Eurozone: "EZ",
    Finland: "FI",
    Fiji: "FJ",
    "Falkland Islands": "FK",
    Micronesia: "FM",
    "Faroe Islands": "FO",
    France: "FX",
    Gabon: "GA",
    "United Kingdom": "UK",
    Grenada: "GD",
    Georgia: "GE",
    "French Guiana": "GF",
    Guernsey: "GG",
    Ghana: "GH",
    Gibraltar: "GI",
    Greenland: "GL",
    Gambia: "GM",
    Guinea: "GN",
    Guadeloupe: "GP",
    "Equatorial Guinea": "GQ",
    Greece: "GR",
    "So. Georgia & So. Sandwich Isl.": "GS",
    Guatemala: "GT",
    Guam: "GU",
    "Guinea-Bissau": "GW",
    Guyana: "GY",
    "Hong Kong": "HK",
    "Heard & McDonald Islands": "HM",
    Honduras: "HN",
    Croatia: "HR",
    Haiti: "HT",
    Hungary: "HU",
    "Canary Islands": "IC",
    Indonesia: "ID",
    Ireland: "IE",
    Israel: "IL",
    "Isle of Man": "IM",
    India: "IN",
    "Chagos Archipelago": "IO",
    Iraq: "IQ",
    Iran: "IR",
    Iceland: "IS",
    Italy: "IT",
    Jersey: "JE",
    Jamaica: "JM",
    Jordan: "JO",
    Japan: "JP",
    Kenya: "KE",
    Kyrgyzstan: "KG",
    Cambodia: "KH",
    Kiribati: "KI",
    Comoros: "KM",
    "St. Kitts & Nevis": "KN",
    "North Korea": "KP",
    "South Korea": "KR",
    Kuwait: "KW",
    "Cayman Islands": "KY",
    Kazakhstan: "KZ",
    Laos: "LA",
    Lebanon: "LB",
    "St. Lucia": "LC",
    Liechtenstein: "LI",
    "Sri Lanka": "LK",
    Liberia: "LR",
    Lesotho: "LS",
    Lithuania: "LT",
    Luxembourg: "LU",
    Latvia: "LV",
    Libya: "LY",
    Morocco: "MA",
    Monaco: "MC",
    Moldova: "MD",
    Montenegro: "ME",
    "St. Martin": "MF",
    Madagascar: "MG",
    "Marshall Islands": "MH",
    "North Macedonia": "MK",
    Mali: "ML",
    Mongolia: "MN",
    Macao: "MO",
    "Northern Mariana Islands": "MP",
    Martinique: "MQ",
    Mauritania: "MR",
    Montserrat: "MS",
    Malta: "MT",
    Mauritius: "MU",
    Maldives: "MV",
    Malawi: "MW",
    Mexico: "MX",
    Malaysia: "MY",
    Mozambique: "MZ",
    Namibia: "NA",
    "New Caledonia": "NC",
    Niger: "NE",
    "Norfolk Island": "NF",
    Nigeria: "NG",
    Vanuatu: "VU",
    Nicaragua: "NI",
    Netherlands: "NL",
    Norway: "NO",
    Nepal: "NP",
    Nauru: "NR",
    Niue: "NU",
    "New Zealand": "NZ",
    Oman: "OM",
    Panama: "PA",
    Peru: "PE",
    "French Polynesia": "PF",
    "Papua New Guinea": "PG",
    Philippines: "PH",
    Pakistan: "PK",
    Poland: "PL",
    "St. Pierre & Miquelon": "PM",
    "Pitcairn Islands": "PN",
    "Puerto Rico": "PR",
    "Palestinian Territories": "PS",
    Portugal: "PT",
    Palau: "PW",
    Paraguay: "PY",
    Qatar: "QA",
    "Outlying Oceania": "QO",
    R\u00E9union: "RE",
    Zimbabwe: "ZW",
    Romania: "RO",
    Russia: "SU",
    Rwanda: "RW",
    "Saudi Arabia": "SA",
    "Solomon Islands": "SB",
    Seychelles: "SC",
    Sudan: "SD",
    Sweden: "SE",
    Singapore: "SG",
    "St. Helena": "SH",
    Slovenia: "SI",
    "Svalbard & Jan Mayen": "SJ",
    Slovakia: "SK",
    "Sierra Leone": "SL",
    "San Marino": "SM",
    Senegal: "SN",
    Somalia: "SO",
    Suriname: "SR",
    "South Sudan": "SS",
    "S\xE3o Tom\xE9 & Pr\xEDncipe": "ST",
    "El Salvador": "SV",
    "Sint Maarten": "SX",
    Syria: "SY",
    Eswatini: "SZ",
    "Tristan da Cunha": "TA",
    "Turks & Caicos Islands": "TC",
    Chad: "TD",
    "French Southern Territories": "TF",
    Togo: "TG",
    Thailand: "TH",
    Tajikistan: "TJ",
    Tokelau: "TK",
    "Timor-Leste": "TP",
    Turkmenistan: "TM",
    Tunisia: "TN",
    Tonga: "TO",
    Turkey: "TR",
    "Trinidad & Tobago": "TT",
    Tuvalu: "TV",
    Taiwan: "TW",
    Tanzania: "TZ",
    Ukraine: "UA",
    Uganda: "UG",
    "U.S. Outlying Islands": "UM",
    "United Nations": "UN",
    "United States": "US",
    Uruguay: "UY",
    Uzbekistan: "UZ",
    "Vatican City": "VA",
    "St. Vincent & Grenadines": "VC",
    Vietnam: "VN",
    Venezuela: "VE",
    "British Virgin Islands": "VG",
    "U.S. Virgin Islands": "VI",
    "Wallis & Futuna": "WF",
    Samoa: "WS",
    "Pseudo-Accents": "XA",
    "Pseudo-Bidi": "XB",
    Kosovo: "XK",
    Yemen: "YE",
    Mayotte: "YT",
    "South Africa": "ZA",
    Zambia: "ZM",
    "Unknown Region": "ZZ"
  };

  // src/Form/formatters.js
  var DATE_SEPARATOR_REGEX = /\b((.)\2{1,3}|\d+)(?<separator>\s?[/\s.\-_—–]\s?)((.)\5{1,3}|\d+)\b/i;
  var FOUR_DIGIT_YEAR_REGEX = /(\D)\1{3}|\d{4}/i;
  var formatCCYear = (input, year, form) => {
    const selector = form.matching.cssSelector("formInputsSelector");
    if (input.maxLength === 4 || checkPlaceholderAndLabels(input, FOUR_DIGIT_YEAR_REGEX, form.form, selector)) return year;
    return `${Number(year) - 2e3}`;
  };
  var getUnifiedExpiryDate = (input, month, year, form) => {
    const formattedYear = formatCCYear(input, year, form);
    const paddedMonth = `${month}`.padStart(2, "0");
    const cssSelector = form.matching.cssSelector("formInputsSelector");
    const separator = matchInPlaceholderAndLabels(input, DATE_SEPARATOR_REGEX, form.form, cssSelector)?.groups?.separator || "/";
    return `${paddedMonth}${separator}${formattedYear}`;
  };
  var formatFullName = ({ firstName = "", middleName = "", lastName = "" }) => `${firstName} ${middleName ? middleName + " " : ""}${lastName}`.trim();
  var getCountryDisplayName = (locale, addressCountryCode) => {
    try {
      const regionNames = new Intl.DisplayNames([locale], { type: "region" });
      return regionNames.of(addressCountryCode);
    } catch (e) {
      return COUNTRY_CODES_TO_NAMES[addressCountryCode] || addressCountryCode;
    }
  };
  var inferElementLocale = (el) => el.lang || el.form?.lang || document.body.lang || document.documentElement.lang || "en";
  var getCountryName = (el, options = {}) => {
    const { addressCountryCode } = options;
    if (!addressCountryCode) return "";
    const elLocale = inferElementLocale(el);
    const localisedCountryName = getCountryDisplayName(elLocale, addressCountryCode);
    if (el.nodeName === "SELECT") {
      const englishCountryName = getCountryDisplayName("en", addressCountryCode);
      const countryNameRegex = new RegExp(
        String.raw`${localisedCountryName.replace(/ /g, ".?")}|${englishCountryName.replace(/ /g, ".?")}`,
        "i"
      );
      const countryCodeRegex = new RegExp(String.raw`\b${addressCountryCode}\b`, "i");
      if (el instanceof HTMLSelectElement) {
        for (const option of el.options) {
          if (countryCodeRegex.test(option.value)) {
            return option.value;
          }
        }
        for (const option of el.options) {
          if (countryNameRegex.test(option.value) || countryNameRegex.test(option.innerText)) return option.value;
        }
      }
    }
    return localisedCountryName;
  };
  var getLocalisedCountryNamesToCodes = (el) => {
    if (typeof Intl.DisplayNames !== "function") return COUNTRY_NAMES_TO_CODES;
    const elLocale = inferElementLocale(el);
    return Object.fromEntries(Object.entries(COUNTRY_CODES_TO_NAMES).map(([code]) => [getCountryDisplayName(elLocale, code), code]));
  };
  var inferCountryCodeFromElement = (el) => {
    if (COUNTRY_CODES_TO_NAMES[el.value]) return el.value;
    if (COUNTRY_NAMES_TO_CODES[el.value]) return COUNTRY_NAMES_TO_CODES[el.value];
    const localisedCountryNamesToCodes = getLocalisedCountryNamesToCodes(el);
    if (localisedCountryNamesToCodes[el.value]) return localisedCountryNamesToCodes[el.value];
    if (el instanceof HTMLSelectElement) {
      const selectedText = el.selectedOptions[0]?.text;
      if (COUNTRY_CODES_TO_NAMES[selectedText]) return selectedText;
      if (COUNTRY_NAMES_TO_CODES[selectedText]) return localisedCountryNamesToCodes[selectedText];
      if (localisedCountryNamesToCodes[selectedText]) return localisedCountryNamesToCodes[selectedText];
    }
    return "";
  };
  var getMMAndYYYYFromString = (expiration) => {
    const values = expiration.match(/(\d+)/g) || [];
    return values?.reduce(
      (output, current) => {
        if (Number(current) > 12) {
          output.expirationYear = current.padStart(4, "20");
        } else {
          output.expirationMonth = current.padStart(2, "0");
        }
        return output;
      },
      { expirationYear: "", expirationMonth: "" }
    );
  };
  var shouldStoreIdentities = ({ identities }) => Boolean((identities.firstName || identities.fullName) && identities.addressStreet && identities.addressCity);
  var shouldStoreCreditCards = ({ creditCards }) => {
    if (!creditCards.cardNumber) return false;
    if (creditCards.cardSecurityCode) return true;
    if (creditCards.expiration) return true;
    return Boolean(creditCards.expirationYear && creditCards.expirationMonth);
  };
  var formatPhoneNumber = (phone) => phone.replaceAll(/[^0-9|+]/g, "");
  var inferCredentialsForPartialSave = (credentials, identities, creditCards) => {
    if (!credentials.username) {
      const possibleUsername = getUsernameLikeIdentity(identities, creditCards);
      if (possibleUsername) credentials.username = possibleUsername;
    }
    if (Object.keys(credentials ?? {}).length === 0) {
      return void 0;
    }
    return credentials;
  };
  var inferCredentials = (credentials, identities, creditCards) => {
    if (!credentials.password) {
      return void 0;
    }
    if (credentials.password && !credentials.username) {
      credentials.username = getUsernameLikeIdentity(identities, creditCards);
    }
    return credentials;
  };
  var prepareFormValuesForStorage = (formValues, canTriggerPartialSave = false) => {
    let { credentials, identities, creditCards } = formValues;
    if (!creditCards.cardName && (identities?.fullName || identities?.firstName)) {
      creditCards.cardName = identities?.fullName || formatFullName(identities);
    }
    credentials = canTriggerPartialSave ? inferCredentialsForPartialSave(credentials, identities, creditCards) : inferCredentials(credentials, identities, creditCards);
    if (shouldStoreIdentities(formValues)) {
      if (identities.fullName) {
        if (!(identities.firstName && identities.lastName)) {
          const nameParts = identities.fullName.trim().split(/\s+/);
          if (nameParts.length === 2) {
            identities.firstName = nameParts[0];
            identities.lastName = nameParts[1];
          } else {
            identities.firstName = identities.fullName;
          }
        }
        delete identities.fullName;
      }
      if (identities.phone) {
        identities.phone = formatPhoneNumber(identities.phone);
      }
    } else {
      identities = void 0;
    }
    if (shouldStoreCreditCards(formValues)) {
      if (creditCards.expiration) {
        const { expirationMonth, expirationYear } = getMMAndYYYYFromString(creditCards.expiration);
        creditCards.expirationMonth = expirationMonth;
        creditCards.expirationYear = expirationYear;
        delete creditCards.expiration;
      }
      creditCards.expirationYear = creditCards.expirationYear?.padStart(4, "20");
      if (creditCards.cardNumber) {
        creditCards.cardNumber = creditCards.cardNumber.replace(/\D/g, "");
      }
    } else {
      creditCards = void 0;
    }
    return { credentials, identities, creditCards };
  };

  // src/InputTypes/Credentials.js
  var AUTOGENERATED_KEY = "autogenerated";
  var PROVIDER_LOCKED = "provider_locked";
  var _data;
  var CredentialsTooltipItem = class {
    /** @param {CredentialsObject} data */
    constructor(data) {
      /** @type {CredentialsObject} */
      __privateAdd(this, _data);
      __publicField(this, "id", () => String(__privateGet(this, _data).id));
      /** @param {import('../locales/strings.js').TranslateFn} t */
      __publicField(this, "labelMedium", (t) => {
        if (__privateGet(this, _data).username) {
          return __privateGet(this, _data).username;
        }
        if (__privateGet(this, _data).origin?.url) {
          return t("autofill:passwordForUrl", { url: truncateFromMiddle(__privateGet(this, _data).origin.url) });
        }
        return "";
      });
      __publicField(this, "labelSmall", () => {
        if (__privateGet(this, _data).origin?.url) {
          return truncateFromMiddle(__privateGet(this, _data).origin.url);
        }
        return "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";
      });
      __publicField(this, "credentialsProvider", () => __privateGet(this, _data).credentialsProvider);
      __privateSet(this, _data, data);
    }
  };
  _data = new WeakMap();
  var _data2;
  var AutoGeneratedCredential = class {
    /** @param {CredentialsObject} data */
    constructor(data) {
      /** @type {CredentialsObject} */
      __privateAdd(this, _data2);
      __publicField(this, "id", () => String(__privateGet(this, _data2).id));
      __publicField(this, "label", (_subtype) => __privateGet(this, _data2).password);
      /** @param {import('../locales/strings.js').TranslateFn} t */
      __publicField(this, "labelMedium", (t) => t("autofill:generatedPassword"));
      /** @param {import('../locales/strings.js').TranslateFn} t */
      __publicField(this, "labelSmall", (t) => t("autofill:passwordWillBeSaved"));
      __privateSet(this, _data2, data);
    }
  };
  _data2 = new WeakMap();
  function fromPassword(password, username) {
    return {
      [AUTOGENERATED_KEY]: true,
      password,
      username
    };
  }
  var _data3;
  var ProviderLockedItem = class {
    /** @param {CredentialsObject} data */
    constructor(data) {
      /** @type {CredentialsObject} */
      __privateAdd(this, _data3);
      __publicField(this, "id", () => String(__privateGet(this, _data3).id));
      /** @param {import('../locales/strings.js').TranslateFn} t */
      __publicField(this, "labelMedium", (t) => t("autofill:bitwardenIsLocked"));
      /** @param {import('../locales/strings.js').TranslateFn} t */
      __publicField(this, "labelSmall", (t) => t("autofill:unlockYourVault"));
      __publicField(this, "credentialsProvider", () => __privateGet(this, _data3).credentialsProvider);
      __privateSet(this, _data3, data);
    }
  };
  _data3 = new WeakMap();
  function appendGeneratedKey(data, autofilledFields = {}) {
    let autogenerated = false;
    if (autofilledFields.password && data.credentials?.password === autofilledFields.password) {
      autogenerated = true;
    }
    if (autofilledFields.username && data.credentials?.username === autofilledFields.username) {
      autogenerated = true;
    }
    if (!autogenerated) return data;
    return {
      ...data,
      credentials: {
        ...data.credentials,
        [AUTOGENERATED_KEY]: true
      }
    };
  }
  function createCredentialsTooltipItem(data) {
    if (data.id === PROVIDER_LOCKED) {
      return new ProviderLockedItem(data);
    }
    if (AUTOGENERATED_KEY in data && data.password) {
      return new AutoGeneratedCredential(data);
    }
    return new CredentialsTooltipItem(data);
  }

  // packages/password/lib/rules-parser.js
  var Identifier = {
    ASCII_PRINTABLE: "ascii-printable",
    DIGIT: "digit",
    LOWER: "lower",
    SPECIAL: "special",
    UNICODE: "unicode",
    UPPER: "upper"
  };
  var RuleName = {
    ALLOWED: "allowed",
    MAX_CONSECUTIVE: "max-consecutive",
    REQUIRED: "required",
    MIN_LENGTH: "minlength",
    MAX_LENGTH: "maxlength"
  };
  var CHARACTER_CLASS_START_SENTINEL = "[";
  var CHARACTER_CLASS_END_SENTINEL = "]";
  var PROPERTY_VALUE_SEPARATOR = ",";
  var PROPERTY_SEPARATOR = ";";
  var PROPERTY_VALUE_START_SENTINEL = ":";
  var SPACE_CODE_POINT = " ".codePointAt(0);
  var SHOULD_NOT_BE_REACHED = "Should not be reached";
  var Rule = class {
    constructor(name, value) {
      this._name = name;
      this.value = value;
    }
    get name() {
      return this._name;
    }
    toString() {
      return JSON.stringify(this);
    }
  };
  var NamedCharacterClass = class {
    constructor(name) {
      console.assert(_isValidRequiredOrAllowedPropertyValueIdentifier(name));
      this._name = name;
    }
    get name() {
      return this._name.toLowerCase();
    }
    toString() {
      return this._name;
    }
    toHTMLString() {
      return this._name;
    }
  };
  var ParserError = class extends Error {
  };
  var CustomCharacterClass = class {
    constructor(characters) {
      console.assert(characters instanceof Array);
      this._characters = characters;
    }
    get characters() {
      return this._characters;
    }
    toString() {
      return `[${this._characters.join("")}]`;
    }
    toHTMLString() {
      return `[${this._characters.join("").replace('"', "&quot;")}]`;
    }
  };
  function _isIdentifierCharacter(c) {
    console.assert(c.length === 1);
    return c >= "a" && c <= "z" || c >= "A" && c <= "Z" || c === "-";
  }
  function _isASCIIDigit(c) {
    console.assert(c.length === 1);
    return c >= "0" && c <= "9";
  }
  function _isASCIIPrintableCharacter(c) {
    console.assert(c.length === 1);
    return c >= " " && c <= "~";
  }
  function _isASCIIWhitespace(c) {
    console.assert(c.length === 1);
    return c === " " || c === "\f" || c === "\n" || c === "\r" || c === "	";
  }
  function _bitSetIndexForCharacter(c) {
    console.assert(c.length === 1);
    return c.codePointAt(0) - SPACE_CODE_POINT;
  }
  function _characterAtBitSetIndex(index) {
    return String.fromCodePoint(index + SPACE_CODE_POINT);
  }
  function _markBitsForNamedCharacterClass(bitSet, namedCharacterClass) {
    console.assert(bitSet instanceof Array);
    console.assert(namedCharacterClass.name !== Identifier.UNICODE);
    console.assert(namedCharacterClass.name !== Identifier.ASCII_PRINTABLE);
    if (namedCharacterClass.name === Identifier.UPPER) {
      bitSet.fill(true, _bitSetIndexForCharacter("A"), _bitSetIndexForCharacter("Z") + 1);
    } else if (namedCharacterClass.name === Identifier.LOWER) {
      bitSet.fill(true, _bitSetIndexForCharacter("a"), _bitSetIndexForCharacter("z") + 1);
    } else if (namedCharacterClass.name === Identifier.DIGIT) {
      bitSet.fill(true, _bitSetIndexForCharacter("0"), _bitSetIndexForCharacter("9") + 1);
    } else if (namedCharacterClass.name === Identifier.SPECIAL) {
      bitSet.fill(true, _bitSetIndexForCharacter(" "), _bitSetIndexForCharacter("/") + 1);
      bitSet.fill(true, _bitSetIndexForCharacter(":"), _bitSetIndexForCharacter("@") + 1);
      bitSet.fill(true, _bitSetIndexForCharacter("["), _bitSetIndexForCharacter("`") + 1);
      bitSet.fill(true, _bitSetIndexForCharacter("{"), _bitSetIndexForCharacter("~") + 1);
    } else {
      console.assert(false, SHOULD_NOT_BE_REACHED, namedCharacterClass);
    }
  }
  function _markBitsForCustomCharacterClass(bitSet, customCharacterClass) {
    for (const character of customCharacterClass.characters) {
      bitSet[_bitSetIndexForCharacter(character)] = true;
    }
  }
  function _canonicalizedPropertyValues(propertyValues, keepCustomCharacterClassFormatCompliant) {
    const asciiPrintableBitSet = new Array("~".codePointAt(0) - " ".codePointAt(0) + 1);
    for (const propertyValue of propertyValues) {
      if (propertyValue instanceof NamedCharacterClass) {
        if (propertyValue.name === Identifier.UNICODE) {
          return [new NamedCharacterClass(Identifier.UNICODE)];
        }
        if (propertyValue.name === Identifier.ASCII_PRINTABLE) {
          return [new NamedCharacterClass(Identifier.ASCII_PRINTABLE)];
        }
        _markBitsForNamedCharacterClass(asciiPrintableBitSet, propertyValue);
      } else if (propertyValue instanceof CustomCharacterClass) {
        _markBitsForCustomCharacterClass(asciiPrintableBitSet, propertyValue);
      }
    }
    let charactersSeen = [];
    function checkRange(start, end) {
      const temp2 = [];
      for (let i = _bitSetIndexForCharacter(start); i <= _bitSetIndexForCharacter(end); ++i) {
        if (asciiPrintableBitSet[i]) {
          temp2.push(_characterAtBitSetIndex(i));
        }
      }
      const result2 = temp2.length === _bitSetIndexForCharacter(end) - _bitSetIndexForCharacter(start) + 1;
      if (!result2) {
        charactersSeen = charactersSeen.concat(temp2);
      }
      return result2;
    }
    const hasAllUpper = checkRange("A", "Z");
    const hasAllLower = checkRange("a", "z");
    const hasAllDigits = checkRange("0", "9");
    let hasAllSpecial = false;
    let hasDash = false;
    let hasRightSquareBracket = false;
    const temp = [];
    for (let i = _bitSetIndexForCharacter(" "); i <= _bitSetIndexForCharacter("/"); ++i) {
      if (!asciiPrintableBitSet[i]) {
        continue;
      }
      const character = _characterAtBitSetIndex(i);
      if (keepCustomCharacterClassFormatCompliant && character === "-") {
        hasDash = true;
      } else {
        temp.push(character);
      }
    }
    for (let i = _bitSetIndexForCharacter(":"); i <= _bitSetIndexForCharacter("@"); ++i) {
      if (asciiPrintableBitSet[i]) {
        temp.push(_characterAtBitSetIndex(i));
      }
    }
    for (let i = _bitSetIndexForCharacter("["); i <= _bitSetIndexForCharacter("`"); ++i) {
      if (!asciiPrintableBitSet[i]) {
        continue;
      }
      const character = _characterAtBitSetIndex(i);
      if (keepCustomCharacterClassFormatCompliant && character === "]") {
        hasRightSquareBracket = true;
      } else {
        temp.push(character);
      }
    }
    for (let i = _bitSetIndexForCharacter("{"); i <= _bitSetIndexForCharacter("~"); ++i) {
      if (asciiPrintableBitSet[i]) {
        temp.push(_characterAtBitSetIndex(i));
      }
    }
    if (hasDash) {
      temp.unshift("-");
    }
    if (hasRightSquareBracket) {
      temp.push("]");
    }
    const numberOfSpecialCharacters = _bitSetIndexForCharacter("/") - _bitSetIndexForCharacter(" ") + 1 + (_bitSetIndexForCharacter("@") - _bitSetIndexForCharacter(":") + 1) + (_bitSetIndexForCharacter("`") - _bitSetIndexForCharacter("[") + 1) + (_bitSetIndexForCharacter("~") - _bitSetIndexForCharacter("{") + 1);
    hasAllSpecial = temp.length === numberOfSpecialCharacters;
    if (!hasAllSpecial) {
      charactersSeen = charactersSeen.concat(temp);
    }
    const result = [];
    if (hasAllUpper && hasAllLower && hasAllDigits && hasAllSpecial) {
      return [new NamedCharacterClass(Identifier.ASCII_PRINTABLE)];
    }
    if (hasAllUpper) {
      result.push(new NamedCharacterClass(Identifier.UPPER));
    }
    if (hasAllLower) {
      result.push(new NamedCharacterClass(Identifier.LOWER));
    }
    if (hasAllDigits) {
      result.push(new NamedCharacterClass(Identifier.DIGIT));
    }
    if (hasAllSpecial) {
      result.push(new NamedCharacterClass(Identifier.SPECIAL));
    }
    if (charactersSeen.length) {
      result.push(new CustomCharacterClass(charactersSeen));
    }
    return result;
  }
  function _indexOfNonWhitespaceCharacter(input, position = 0) {
    console.assert(position >= 0);
    console.assert(position <= input.length);
    const length = input.length;
    while (position < length && _isASCIIWhitespace(input[position])) {
      ++position;
    }
    return position;
  }
  function _parseIdentifier(input, position) {
    console.assert(position >= 0);
    console.assert(position < input.length);
    console.assert(_isIdentifierCharacter(input[position]));
    const length = input.length;
    const seenIdentifiers = [];
    do {
      const c = input[position];
      if (!_isIdentifierCharacter(c)) {
        break;
      }
      seenIdentifiers.push(c);
      ++position;
    } while (position < length);
    return [seenIdentifiers.join(""), position];
  }
  function _isValidRequiredOrAllowedPropertyValueIdentifier(identifier) {
    return identifier && Object.values(Identifier).includes(identifier.toLowerCase());
  }
  function _parseCustomCharacterClass(input, position) {
    console.assert(position >= 0);
    console.assert(position < input.length);
    console.assert(input[position] === CHARACTER_CLASS_START_SENTINEL);
    const length = input.length;
    ++position;
    if (position >= length) {
      return [null, position];
    }
    const initialPosition = position;
    const result = [];
    do {
      const c = input[position];
      if (!_isASCIIPrintableCharacter(c)) {
        ++position;
        continue;
      }
      if (c === "-" && position - initialPosition > 0) {
        console.warn("Ignoring '-'; a '-' may only appear as the first character in a character class");
        ++position;
        continue;
      }
      result.push(c);
      ++position;
      if (c === CHARACTER_CLASS_END_SENTINEL) {
        break;
      }
    } while (position < length);
    if (position < length && input[position] !== CHARACTER_CLASS_END_SENTINEL) {
      result.pop();
      return [result, position];
    } else if (position === length && input[position - 1] === CHARACTER_CLASS_END_SENTINEL) {
      result.pop();
      return [result, position];
    }
    if (position < length && input[position] === CHARACTER_CLASS_END_SENTINEL) {
      return [result, position + 1];
    }
    return [null, position];
  }
  function _parsePasswordRequiredOrAllowedPropertyValue(input, position) {
    console.assert(position >= 0);
    console.assert(position < input.length);
    const length = input.length;
    const propertyValues = [];
    while (true) {
      if (_isIdentifierCharacter(input[position])) {
        const identifierStartPosition = position;
        var [propertyValue, position] = _parseIdentifier(input, position);
        if (!_isValidRequiredOrAllowedPropertyValueIdentifier(propertyValue)) {
          return [null, identifierStartPosition];
        }
        propertyValues.push(new NamedCharacterClass(propertyValue));
      } else if (input[position] === CHARACTER_CLASS_START_SENTINEL) {
        var [propertyValue, position] = _parseCustomCharacterClass(input, position);
        if (propertyValue && propertyValue.length) {
          propertyValues.push(new CustomCharacterClass(propertyValue));
        }
      } else {
        return [null, position];
      }
      position = _indexOfNonWhitespaceCharacter(input, position);
      if (position >= length || input[position] === PROPERTY_SEPARATOR) {
        break;
      }
      if (input[position] === PROPERTY_VALUE_SEPARATOR) {
        position = _indexOfNonWhitespaceCharacter(input, position + 1);
        if (position >= length) {
          return [null, position];
        }
        continue;
      }
      return [null, position];
    }
    return [propertyValues, position];
  }
  function _parsePasswordRule(input, position) {
    console.assert(position >= 0);
    console.assert(position < input.length);
    console.assert(_isIdentifierCharacter(input[position]));
    const length = input.length;
    const mayBeIdentifierStartPosition = position;
    var [identifier, position] = _parseIdentifier(input, position);
    if (!Object.values(RuleName).includes(identifier)) {
      return [null, mayBeIdentifierStartPosition, void 0];
    }
    if (position >= length) {
      return [null, position, void 0];
    }
    if (input[position] !== PROPERTY_VALUE_START_SENTINEL) {
      return [null, position, void 0];
    }
    const property = { name: identifier, value: null };
    position = _indexOfNonWhitespaceCharacter(input, position + 1);
    if (position >= length || input[position] === PROPERTY_SEPARATOR) {
      return [new Rule(property.name, property.value), position, void 0];
    }
    switch (identifier) {
      case RuleName.ALLOWED:
      case RuleName.REQUIRED: {
        var [propertyValue, position] = _parsePasswordRequiredOrAllowedPropertyValue(input, position);
        if (propertyValue) {
          property.value = propertyValue;
        }
        return [new Rule(property.name, property.value), position, void 0];
      }
      case RuleName.MAX_CONSECUTIVE: {
        var [propertyValue, position] = _parseMaxConsecutivePropertyValue(input, position);
        if (propertyValue) {
          property.value = propertyValue;
        }
        return [new Rule(property.name, property.value), position, void 0];
      }
      case RuleName.MIN_LENGTH:
      case RuleName.MAX_LENGTH: {
        var [propertyValue, position] = _parseMinLengthMaxLengthPropertyValue(input, position);
        if (propertyValue) {
          property.value = propertyValue;
        }
        return [new Rule(property.name, property.value), position, void 0];
      }
    }
    console.assert(false, SHOULD_NOT_BE_REACHED);
    return [null, -1, void 0];
  }
  function _parseMinLengthMaxLengthPropertyValue(input, position) {
    return _parseInteger(input, position);
  }
  function _parseMaxConsecutivePropertyValue(input, position) {
    return _parseInteger(input, position);
  }
  function _parseInteger(input, position) {
    console.assert(position >= 0);
    console.assert(position < input.length);
    if (!_isASCIIDigit(input[position])) {
      return [null, position];
    }
    const length = input.length;
    let result = 0;
    do {
      result = 10 * result + parseInt(input[position], 10);
      ++position;
    } while (position < length && input[position] !== PROPERTY_SEPARATOR && _isASCIIDigit(input[position]));
    if (position >= length || input[position] === PROPERTY_SEPARATOR) {
      return [result, position];
    }
    return [null, position];
  }
  function _parsePasswordRulesInternal(input) {
    const parsedProperties = [];
    const length = input.length;
    var position = _indexOfNonWhitespaceCharacter(input);
    while (position < length) {
      if (!_isIdentifierCharacter(input[position])) {
        return [parsedProperties, void 0];
      }
      var [parsedProperty, position, message] = _parsePasswordRule(input, position);
      if (parsedProperty && parsedProperty.value) {
        parsedProperties.push(parsedProperty);
      }
      position = _indexOfNonWhitespaceCharacter(input, position);
      if (position >= length) {
        break;
      }
      if (input[position] === PROPERTY_SEPARATOR) {
        position = _indexOfNonWhitespaceCharacter(input, position + 1);
        if (position >= length) {
          return [parsedProperties, void 0];
        }
        continue;
      }
      return [null, message || "Failed to find start of next property: " + input.substr(position)];
    }
    return [parsedProperties, void 0];
  }
  function parsePasswordRules(input, formatRulesForMinifiedVersion) {
    const [passwordRules, maybeMessage] = _parsePasswordRulesInternal(input);
    if (!passwordRules) {
      throw new ParserError(maybeMessage);
    }
    if (passwordRules.length === 0) {
      throw new ParserError("No valid rules were provided");
    }
    const suppressCopyingRequiredToAllowed = formatRulesForMinifiedVersion;
    const requiredRules = [];
    let newAllowedValues = [];
    let minimumMaximumConsecutiveCharacters = null;
    let maximumMinLength = 0;
    let minimumMaxLength = null;
    for (const rule of passwordRules) {
      switch (rule.name) {
        case RuleName.MAX_CONSECUTIVE:
          minimumMaximumConsecutiveCharacters = minimumMaximumConsecutiveCharacters ? Math.min(rule.value, minimumMaximumConsecutiveCharacters) : rule.value;
          break;
        case RuleName.MIN_LENGTH:
          maximumMinLength = Math.max(rule.value, maximumMinLength);
          break;
        case RuleName.MAX_LENGTH:
          minimumMaxLength = minimumMaxLength ? Math.min(rule.value, minimumMaxLength) : rule.value;
          break;
        case RuleName.REQUIRED:
          rule.value = _canonicalizedPropertyValues(rule.value, formatRulesForMinifiedVersion);
          requiredRules.push(rule);
          if (!suppressCopyingRequiredToAllowed) {
            newAllowedValues = newAllowedValues.concat(rule.value);
          }
          break;
        case RuleName.ALLOWED:
          newAllowedValues = newAllowedValues.concat(rule.value);
          break;
      }
    }
    let newPasswordRules = [];
    if (maximumMinLength > 0) {
      newPasswordRules.push(new Rule(RuleName.MIN_LENGTH, maximumMinLength));
    }
    if (minimumMaxLength !== null) {
      newPasswordRules.push(new Rule(RuleName.MAX_LENGTH, minimumMaxLength));
    }
    if (minimumMaximumConsecutiveCharacters !== null) {
      newPasswordRules.push(new Rule(RuleName.MAX_CONSECUTIVE, minimumMaximumConsecutiveCharacters));
    }
    const sortedRequiredRules = requiredRules.sort(function(a, b) {
      const namedCharacterClassOrder = [
        Identifier.LOWER,
        Identifier.UPPER,
        Identifier.DIGIT,
        Identifier.SPECIAL,
        Identifier.ASCII_PRINTABLE,
        Identifier.UNICODE
      ];
      const aIsJustOneNamedCharacterClass = a.value.length === 1 && a.value[0] instanceof NamedCharacterClass;
      const bIsJustOneNamedCharacterClass = b.value.length === 1 && b.value[0] instanceof NamedCharacterClass;
      if (aIsJustOneNamedCharacterClass && !bIsJustOneNamedCharacterClass) {
        return -1;
      }
      if (!aIsJustOneNamedCharacterClass && bIsJustOneNamedCharacterClass) {
        return 1;
      }
      if (aIsJustOneNamedCharacterClass && bIsJustOneNamedCharacterClass) {
        const aIndex = namedCharacterClassOrder.indexOf(a.value[0].name);
        const bIndex = namedCharacterClassOrder.indexOf(b.value[0].name);
        return aIndex - bIndex;
      }
      return 0;
    });
    newPasswordRules = newPasswordRules.concat(sortedRequiredRules);
    newAllowedValues = _canonicalizedPropertyValues(newAllowedValues, suppressCopyingRequiredToAllowed);
    if (!suppressCopyingRequiredToAllowed && !newAllowedValues.length) {
      newAllowedValues = [new NamedCharacterClass(Identifier.ASCII_PRINTABLE)];
    }
    if (newAllowedValues.length) {
      newPasswordRules.push(new Rule(RuleName.ALLOWED, newAllowedValues));
    }
    return newPasswordRules;
  }

  // packages/password/lib/constants.js
  var DEFAULT_MIN_LENGTH = 20;
  var DEFAULT_MAX_LENGTH = 30;
  var DEFAULT_REQUIRED_CHARS = "-!?$&#%";
  var DEFAULT_UNAMBIGUOUS_CHARS = "abcdefghijkmnopqrstuvwxyzABCDEFGHIJKLMNPQRSTUVWXYZ0123456789";
  var DEFAULT_PASSWORD_RULES = [
    `minlength: ${DEFAULT_MIN_LENGTH}`,
    `maxlength: ${DEFAULT_MAX_LENGTH}`,
    `required: [${DEFAULT_REQUIRED_CHARS}]`,
    `required: digit`,
    `allowed: [${DEFAULT_UNAMBIGUOUS_CHARS}]`
  ].join("; ");
  var constants2 = {
    DEFAULT_MIN_LENGTH,
    DEFAULT_MAX_LENGTH,
    DEFAULT_PASSWORD_RULES,
    DEFAULT_REQUIRED_CHARS,
    DEFAULT_UNAMBIGUOUS_CHARS
  };

  // packages/password/lib/apple.password.js
  var defaults = Object.freeze({
    SCAN_SET_ORDER: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-~!@#$%^&*_+=`|(){}[:;\\\"'<>,.?/ ]",
    defaultUnambiguousCharacters: "abcdefghijkmnopqrstuvwxyzABCDEFGHIJKLMNPQRSTUVWXYZ0123456789",
    defaultPasswordLength: constants2.DEFAULT_MIN_LENGTH,
    defaultPasswordRules: constants2.DEFAULT_PASSWORD_RULES,
    defaultRequiredCharacterSets: ["abcdefghijklmnopqrstuvwxyz", "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "0123456789"],
    /**
     * @type {typeof window.crypto.getRandomValues | null}
     */
    getRandomValues: null
  });
  var safeGlobals = {};
  if (typeof window !== "undefined") {
    safeGlobals.getRandomValues = window.crypto.getRandomValues.bind(window.crypto);
  }
  var Password = class _Password {
    /**
     * @param {Partial<typeof defaults>} [options]
     */
    constructor(options = {}) {
      this.options = {
        ...defaults,
        ...options
      };
      return this;
    }
    static get defaults() {
      return defaults;
    }
    /**
     * Generates a password from the given input.
     *
     * Note: This method will throw an error if parsing fails - use with caution
     *
     * @example
     *
     * ```javascript
     * const password = Password.generateOrThrow("minlength: 20")
     * ```
     * @public
     * @param {string} inputString
     * @param {Partial<typeof defaults>} [options]
     * @throws {ParserError|Error}
     * @returns {string}
     */
    static generateOrThrow(inputString, options = {}) {
      return new _Password(options).parse(inputString).generate();
    }
    /**
     * Generates a password using the default ruleset.
     *
     * @example
     *
     * ```javascript
     * const password = Password.generateDefault()
     * ```
     *
     * @public
     * @param {Partial<typeof defaults>} [options]
     * @returns {string}
     */
    static generateDefault(options = {}) {
      return new _Password(options).parse(_Password.defaults.defaultPasswordRules).generate();
    }
    /**
     * Convert a ruleset into it's internally-used component pieces.
     *
     * @param {string} inputString
     * @throws {parser.ParserError|Error}
     * @returns {{
     *    requirements: Requirements;
     *    parameters: PasswordParameters;
     *    rules: parser.Rule[],
     *    get entropy(): number;
     *    generate: () => string;
     * }}
     */
    parse(inputString) {
      const rules = parsePasswordRules(inputString);
      const requirements = this._requirementsFromRules(rules);
      if (!requirements) throw new Error("could not generate requirements for " + JSON.stringify(inputString));
      const parameters = this._passwordGenerationParametersDictionary(requirements);
      return {
        requirements,
        parameters,
        rules,
        get entropy() {
          return Math.log2(parameters.PasswordAllowedCharacters.length ** parameters.NumberOfRequiredRandomCharacters);
        },
        generate: () => {
          const password = this._generatedPasswordMatchingRequirements(requirements, parameters);
          if (password === "") throw new Error("unreachable");
          return password;
        }
      };
    }
    /**
     * Given an array of `Rule's`, convert into `Requirements`
     *
     * @param {parser.Rule[]} passwordRules
     * @returns {Requirements | null}
     */
    _requirementsFromRules(passwordRules) {
      const requirements = {};
      for (const rule of passwordRules) {
        if (rule.name === RuleName.ALLOWED) {
          console.assert(!("PasswordAllowedCharacters" in requirements));
          const chars = this._charactersFromCharactersClasses(rule.value);
          const scanSet = this._canonicalizedScanSetFromCharacters(chars);
          if (scanSet) {
            requirements.PasswordAllowedCharacters = scanSet;
          }
        } else if (rule.name === RuleName.MAX_CONSECUTIVE) {
          console.assert(!("PasswordRepeatedCharacterLimit" in requirements));
          requirements.PasswordRepeatedCharacterLimit = rule.value;
        } else if (rule.name === RuleName.REQUIRED) {
          let requiredCharacters = requirements.PasswordRequiredCharacters;
          if (!requiredCharacters) {
            requiredCharacters = requirements.PasswordRequiredCharacters = [];
          }
          requiredCharacters.push(this._canonicalizedScanSetFromCharacters(this._charactersFromCharactersClasses(rule.value)));
        } else if (rule.name === RuleName.MIN_LENGTH) {
          requirements.PasswordMinLength = rule.value;
        } else if (rule.name === RuleName.MAX_LENGTH) {
          requirements.PasswordMaxLength = rule.value;
        }
      }
      if (requirements.PasswordAllowedCharacters === this.options.SCAN_SET_ORDER && !requirements.PasswordRequiredCharacters) {
        delete requirements.PasswordAllowedCharacters;
      }
      if (requirements.PasswordRequiredCharacters && requirements.PasswordRequiredCharacters.length === 1 && requirements.PasswordRequiredCharacters[0] === this.options.SCAN_SET_ORDER) {
        delete requirements.PasswordRequiredCharacters;
      }
      return Object.keys(requirements).length ? requirements : null;
    }
    /**
     * @param {number} range
     * @returns {number}
     */
    _randomNumberWithUniformDistribution(range) {
      const getRandomValues = this.options.getRandomValues || safeGlobals.getRandomValues;
      const max = Math.floor(2 ** 32 / range) * range;
      let x;
      do {
        x = getRandomValues(new Uint32Array(1))[0];
      } while (x >= max);
      return x % range;
    }
    /**
     * @param {number} numberOfRequiredRandomCharacters
     * @param {string} allowedCharacters
     */
    _classicPassword(numberOfRequiredRandomCharacters, allowedCharacters) {
      const length = allowedCharacters.length;
      const randomCharArray = Array(numberOfRequiredRandomCharacters);
      for (let i = 0; i < numberOfRequiredRandomCharacters; i++) {
        const index = this._randomNumberWithUniformDistribution(length);
        randomCharArray[i] = allowedCharacters[index];
      }
      return randomCharArray.join("");
    }
    /**
     * @param {string} password
     * @param {number} consecutiveCharLimit
     * @returns {boolean}
     */
    _passwordHasNotExceededConsecutiveCharLimit(password, consecutiveCharLimit) {
      let longestConsecutiveCharLength = 1;
      let firstConsecutiveCharIndex = 0;
      let isSequenceAscending;
      for (let i = 1; i < password.length; i++) {
        const currCharCode = password.charCodeAt(i);
        const prevCharCode = password.charCodeAt(i - 1);
        if (isSequenceAscending) {
          if (isSequenceAscending.valueOf() && currCharCode === prevCharCode + 1 || !isSequenceAscending.valueOf() && currCharCode === prevCharCode - 1) {
            continue;
          }
          if (currCharCode === prevCharCode + 1) {
            firstConsecutiveCharIndex = i - 1;
            isSequenceAscending = Boolean(true);
            continue;
          }
          if (currCharCode === prevCharCode - 1) {
            firstConsecutiveCharIndex = i - 1;
            isSequenceAscending = Boolean(false);
            continue;
          }
          isSequenceAscending = null;
        } else if (currCharCode === prevCharCode + 1) {
          isSequenceAscending = Boolean(true);
          continue;
        } else if (currCharCode === prevCharCode - 1) {
          isSequenceAscending = Boolean(false);
          continue;
        }
        const currConsecutiveCharLength = i - firstConsecutiveCharIndex;
        if (currConsecutiveCharLength > longestConsecutiveCharLength) {
          longestConsecutiveCharLength = currConsecutiveCharLength;
        }
        firstConsecutiveCharIndex = i;
      }
      if (isSequenceAscending) {
        const currConsecutiveCharLength = password.length - firstConsecutiveCharIndex;
        if (currConsecutiveCharLength > longestConsecutiveCharLength) {
          longestConsecutiveCharLength = currConsecutiveCharLength;
        }
      }
      return longestConsecutiveCharLength <= consecutiveCharLimit;
    }
    /**
     * @param {string} password
     * @param {number} repeatedCharLimit
     * @returns {boolean}
     */
    _passwordHasNotExceededRepeatedCharLimit(password, repeatedCharLimit) {
      let longestRepeatedCharLength = 1;
      let lastRepeatedChar = password.charAt(0);
      let lastRepeatedCharIndex = 0;
      for (let i = 1; i < password.length; i++) {
        const currChar = password.charAt(i);
        if (currChar === lastRepeatedChar) {
          continue;
        }
        const currRepeatedCharLength = i - lastRepeatedCharIndex;
        if (currRepeatedCharLength > longestRepeatedCharLength) {
          longestRepeatedCharLength = currRepeatedCharLength;
        }
        lastRepeatedChar = currChar;
        lastRepeatedCharIndex = i;
      }
      return longestRepeatedCharLength <= repeatedCharLimit;
    }
    /**
     * @param {string} password
     * @param {string[]} requiredCharacterSets
     * @returns {boolean}
     */
    _passwordContainsRequiredCharacters(password, requiredCharacterSets) {
      const requiredCharacterSetsLength = requiredCharacterSets.length;
      const passwordLength = password.length;
      for (let i = 0; i < requiredCharacterSetsLength; i++) {
        const requiredCharacterSet = requiredCharacterSets[i];
        let hasRequiredChar = false;
        for (let j = 0; j < passwordLength; j++) {
          const char = password.charAt(j);
          if (requiredCharacterSet.indexOf(char) !== -1) {
            hasRequiredChar = true;
            break;
          }
        }
        if (!hasRequiredChar) {
          return false;
        }
      }
      return true;
    }
    /**
     * @param {string} string1
     * @param {string} string2
     * @returns {boolean}
     */
    _stringsHaveAtLeastOneCommonCharacter(string1, string2) {
      const string2Length = string2.length;
      for (let i = 0; i < string2Length; i++) {
        const char = string2.charAt(i);
        if (string1.indexOf(char) !== -1) {
          return true;
        }
      }
      return false;
    }
    /**
     * @param {Requirements} requirements
     * @returns {PasswordParameters}
     */
    _passwordGenerationParametersDictionary(requirements) {
      let minPasswordLength = requirements.PasswordMinLength;
      const maxPasswordLength = requirements.PasswordMaxLength;
      if (minPasswordLength > maxPasswordLength) {
        minPasswordLength = 0;
      }
      const requiredCharacterArray = requirements.PasswordRequiredCharacters;
      let allowedCharacters = requirements.PasswordAllowedCharacters;
      let requiredCharacterSets = this.options.defaultRequiredCharacterSets;
      if (requiredCharacterArray) {
        const mutatedRequiredCharacterSets2 = [];
        const requiredCharacterArrayLength = requiredCharacterArray.length;
        for (let i = 0; i < requiredCharacterArrayLength; i++) {
          const requiredCharacters = requiredCharacterArray[i];
          if (allowedCharacters && this._stringsHaveAtLeastOneCommonCharacter(requiredCharacters, allowedCharacters)) {
            mutatedRequiredCharacterSets2.push(requiredCharacters);
          }
        }
        requiredCharacterSets = mutatedRequiredCharacterSets2;
      }
      let numberOfRequiredRandomCharacters = this.options.defaultPasswordLength;
      if (minPasswordLength && minPasswordLength > numberOfRequiredRandomCharacters) {
        numberOfRequiredRandomCharacters = minPasswordLength;
      }
      if (maxPasswordLength && maxPasswordLength < numberOfRequiredRandomCharacters) {
        numberOfRequiredRandomCharacters = maxPasswordLength;
      }
      if (!allowedCharacters) {
        allowedCharacters = this.options.defaultUnambiguousCharacters;
      }
      if (!requiredCharacterSets) {
        requiredCharacterSets = this.options.defaultRequiredCharacterSets;
      }
      if (requiredCharacterSets.length > numberOfRequiredRandomCharacters) {
        requiredCharacterSets = [];
      }
      const requiredCharacterSetsLength = requiredCharacterSets.length;
      const mutatedRequiredCharacterSets = [];
      const allowedCharactersLength = allowedCharacters.length;
      for (let i = 0; i < requiredCharacterSetsLength; i++) {
        const requiredCharacterSet = requiredCharacterSets[i];
        let requiredCharacterSetContainsAllowedCharacters = false;
        for (let j = 0; j < allowedCharactersLength; j++) {
          const character = allowedCharacters.charAt(j);
          if (requiredCharacterSet.indexOf(character) !== -1) {
            requiredCharacterSetContainsAllowedCharacters = true;
            break;
          }
        }
        if (requiredCharacterSetContainsAllowedCharacters) {
          mutatedRequiredCharacterSets.push(requiredCharacterSet);
        }
      }
      requiredCharacterSets = mutatedRequiredCharacterSets;
      return {
        NumberOfRequiredRandomCharacters: numberOfRequiredRandomCharacters,
        PasswordAllowedCharacters: allowedCharacters,
        RequiredCharacterSets: requiredCharacterSets
      };
    }
    /**
     * @param {Requirements | null} requirements
     * @param {PasswordParameters} [parameters]
     * @returns {string}
     */
    _generatedPasswordMatchingRequirements(requirements, parameters) {
      requirements = requirements || {};
      parameters = parameters || this._passwordGenerationParametersDictionary(requirements);
      const numberOfRequiredRandomCharacters = parameters.NumberOfRequiredRandomCharacters;
      const repeatedCharLimit = requirements.PasswordRepeatedCharacterLimit;
      const allowedCharacters = parameters.PasswordAllowedCharacters;
      const shouldCheckRepeatedCharRequirement = !!repeatedCharLimit;
      while (true) {
        const password = this._classicPassword(numberOfRequiredRandomCharacters, allowedCharacters);
        if (!this._passwordContainsRequiredCharacters(password, parameters.RequiredCharacterSets)) {
          continue;
        }
        if (shouldCheckRepeatedCharRequirement) {
          if (repeatedCharLimit !== void 0 && repeatedCharLimit >= 1 && !this._passwordHasNotExceededRepeatedCharLimit(password, repeatedCharLimit)) {
            continue;
          }
        }
        const consecutiveCharLimit = requirements.PasswordConsecutiveCharacterLimit;
        if (consecutiveCharLimit && consecutiveCharLimit >= 1) {
          if (!this._passwordHasNotExceededConsecutiveCharLimit(password, consecutiveCharLimit)) {
            continue;
          }
        }
        return password || "";
      }
    }
    /**
     * @param {parser.CustomCharacterClass | parser.NamedCharacterClass} characterClass
     * @returns {string[]}
     */
    _scanSetFromCharacterClass(characterClass) {
      if (characterClass instanceof CustomCharacterClass) {
        return characterClass.characters;
      }
      console.assert(characterClass instanceof NamedCharacterClass);
      switch (characterClass.name) {
        case Identifier.ASCII_PRINTABLE:
        case Identifier.UNICODE:
          return this.options.SCAN_SET_ORDER.split("");
        case Identifier.DIGIT:
          return this.options.SCAN_SET_ORDER.substring(
            this.options.SCAN_SET_ORDER.indexOf("0"),
            this.options.SCAN_SET_ORDER.indexOf("9") + 1
          ).split("");
        case Identifier.LOWER:
          return this.options.SCAN_SET_ORDER.substring(
            this.options.SCAN_SET_ORDER.indexOf("a"),
            this.options.SCAN_SET_ORDER.indexOf("z") + 1
          ).split("");
        case Identifier.SPECIAL:
          return this.options.SCAN_SET_ORDER.substring(
            this.options.SCAN_SET_ORDER.indexOf("-"),
            this.options.SCAN_SET_ORDER.indexOf("]") + 1
          ).split("");
        case Identifier.UPPER:
          return this.options.SCAN_SET_ORDER.substring(
            this.options.SCAN_SET_ORDER.indexOf("A"),
            this.options.SCAN_SET_ORDER.indexOf("Z") + 1
          ).split("");
      }
      console.assert(false, SHOULD_NOT_BE_REACHED);
      return [];
    }
    /**
     * @param {(parser.CustomCharacterClass | parser.NamedCharacterClass)[]} characterClasses
     */
    _charactersFromCharactersClasses(characterClasses) {
      const output = [];
      for (const characterClass of characterClasses) {
        output.push(...this._scanSetFromCharacterClass(characterClass));
      }
      return output;
    }
    /**
     * @param {string[]} characters
     * @returns {string}
     */
    _canonicalizedScanSetFromCharacters(characters) {
      if (!characters.length) {
        return "";
      }
      const shadowCharacters = Array.prototype.slice.call(characters);
      shadowCharacters.sort((a, b) => this.options.SCAN_SET_ORDER.indexOf(a) - this.options.SCAN_SET_ORDER.indexOf(b));
      const uniqueCharacters = [shadowCharacters[0]];
      for (let i = 1, length = shadowCharacters.length; i < length; ++i) {
        if (shadowCharacters[i] === shadowCharacters[i - 1]) {
          continue;
        }
        uniqueCharacters.push(shadowCharacters[i]);
      }
      return uniqueCharacters.join("");
    }
  };

  // packages/password/index.js
  function generate(options = {}) {
    try {
      if (typeof options?.input === "string") {
        return Password.generateOrThrow(options.input);
      }
      if (typeof options?.domain === "string") {
        if (options?.rules) {
          const rules = _selectPasswordRules(options.domain, options.rules);
          if (rules) {
            return Password.generateOrThrow(rules);
          }
        }
      }
    } catch (e) {
      if (options?.onError && typeof options?.onError === "function") {
        options.onError(e);
      } else {
        const isKnownError = e instanceof ParserError || e instanceof HostnameInputError;
        if (!isKnownError) {
          console.error(e);
        }
      }
    }
    return Password.generateDefault();
  }
  var HostnameInputError = class extends Error {
  };
  function _selectPasswordRules(inputHostname, rules) {
    const hostname = _safeHostname(inputHostname);
    if (rules[hostname]) {
      return rules[hostname]["password-rules"];
    }
    const pieces = hostname.split(".");
    while (pieces.length > 1) {
      pieces.shift();
      const joined = pieces.join(".");
      if (rules[joined]) {
        return rules[joined]["password-rules"];
      }
    }
    return void 0;
  }
  function _safeHostname(inputHostname) {
    if (inputHostname.startsWith("http:") || inputHostname.startsWith("https:")) {
      throw new HostnameInputError("invalid input, you can only provide a hostname but you gave a scheme");
    }
    if (inputHostname.includes(":")) {
      throw new HostnameInputError("invalid input, you can only provide a hostname but you gave a :port");
    }
    try {
      const asUrl = new URL("https://" + inputHostname);
      return asUrl.hostname;
    } catch (e) {
      throw new HostnameInputError(`could not instantiate a URL from that hostname ${inputHostname}`);
    }
  }

  // packages/password/rules.json
  var rules_default = {
    "163.com": {
      "password-rules": "minlength: 6; maxlength: 16;"
    },
    "1800flowers.com": {
      "password-rules": "minlength: 6; required: lower, upper; required: digit;"
    },
    "access.service.gov.uk": {
      "password-rules": "minlength: 10; required: lower; required: upper; required: digit; required: special;"
    },
    "account.samsung.com": {
      "password-rules": "minlength: 8; maxlength: 15; required: digit; required: special; required: upper,lower;"
    },
    "acmemarkets.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "act.org": {
      "password-rules": "minlength: 8; maxlength: 64; required: lower; required: upper; required: digit; required: [!#$%&*@^];"
    },
    "admiral.com": {
      "password-rules": "minlength: 8; required: digit; required: [- !\"#$&'()*+,.:;<=>?@[^_`{|}~]]; allowed: lower, upper;"
    },
    "ae.com": {
      "password-rules": "minlength: 8; maxlength: 25; required: lower; required: upper; required: digit;"
    },
    "aeon.co.jp": {
      "password-rules": "minlength: 8; maxlength: 8; max-consecutive: 3; required: digit; required: upper,lower,[#$+./:=?@[^_|~]];"
    },
    "aeromexico.com": {
      "password-rules": "minlength: 8; maxlength: 25; required: lower; required: upper; required: digit;"
    },
    "aetna.com": {
      "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 2; required: upper; required: digit; allowed: lower, [-_&#@];"
    },
    "airasia.com": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit;"
    },
    "airfrance.com": {
      "password-rules": "minlength: 8; maxlength: 12; required: lower; required: upper; required: digit; allowed: [-!#$&+/?@_];"
    },
    "airfrance.us": {
      "password-rules": "minlength: 8; maxlength: 12; required: lower; required: upper; required: digit; allowed: [-!#$&+/?@_];"
    },
    "ajisushionline.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; allowed: [ !#$%&*?@];"
    },
    "albertsons.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "alelo.com.br": {
      "password-rules": "minlength: 6; maxlength: 10; required: lower; required: upper; required: digit;"
    },
    "aliexpress.com": {
      "password-rules": "minlength: 6; maxlength: 20; allowed: lower, upper, digit;"
    },
    "alliantcreditunion.com": {
      "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 3; required: lower, upper; required: digit; allowed: [!#$*];"
    },
    "allianz.com.br": {
      "password-rules": "minlength: 4; maxlength: 4;"
    },
    "americanexpress.com": {
      "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 4; required: lower, upper; required: digit; allowed: [%&_?#=];"
    },
    "amnh.org": {
      "password-rules": "minlength: 8; maxlength: 16; required: digit; required: upper,lower; allowed: ascii-printable;"
    },
    "ana.co.jp": {
      "password-rules": "minlength: 8; maxlength: 16; required: digit; required: upper,lower;"
    },
    "anatel.gov.br": {
      "password-rules": "minlength: 6; maxlength: 15; allowed: lower, upper, digit;"
    },
    "ancestry.com": {
      "password-rules": "minlength: 8; required: lower, upper; required: digit;"
    },
    "andronicos.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "angieslist.com": {
      "password-rules": "minlength: 6; maxlength: 15;"
    },
    "anthem.com": {
      "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 3; required: lower, upper; required: digit; allowed: [!$*?@|];"
    },
    "app.digiboxx.com": {
      "password-rules": "minlength: 8; maxlength: 14; required: lower; required: upper; required: digit; required: [@$!%*?&];"
    },
    "app.digio.in": {
      "password-rules": "minlength: 8; maxlength: 15;"
    },
    "app.parkmobile.io": {
      "password-rules": "minlength: 8; maxlength: 25; required: lower; required: upper; required: digit; required: [!@#$%^&];"
    },
    "app8menu.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [@$!%*?&];"
    },
    "apple.com": {
      "password-rules": "minlength: 8; maxlength: 63; required: lower; required: upper; required: digit; allowed: ascii-printable;"
    },
    "appleloan.citizensbank.com": {
      "password-rules": "minlength: 10; maxlength: 20; max-consecutive: 2; required: lower; required: upper; required: digit; required: [!#$%@^_];"
    },
    "areariservata.bancaetica.it": {
      "password-rules": "minlength: 8; maxlength: 10; required: lower; required: upper; required: digit; required: [!#&*+/=@_];"
    },
    "artscyclery.com": {
      "password-rules": "minlength: 6; maxlength: 19;"
    },
    "astonmartinf1.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: special;"
    },
    "auth.readymag.com": {
      "password-rules": "minlength: 8; maxlength: 128; required: lower; required: upper; allowed: special;"
    },
    "autify.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!\"#$%&'()*+,./:;<=>?@[^_`{|}~]];"
    },
    "axa.de": {
      "password-rules": `minlength: 8; maxlength: 65; required: lower; required: upper; required: digit; allowed: [-!"\xA7$%&/()=?;:_+*'#];`
    },
    "baidu.com": {
      "password-rules": "minlength: 6; maxlength: 14;"
    },
    "balduccis.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "bancochile.cl": {
      "password-rules": "minlength: 8; maxlength: 8; required: lower; required: upper; required: digit;"
    },
    "bankofamerica.com": {
      "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 3; required: lower; required: upper; required: digit; allowed: [-@#*()+={}/?~;,._];"
    },
    "battle.net": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower, upper; allowed: digit, special;"
    },
    "bcassessment.ca": {
      "password-rules": "minlength: 8; maxlength: 14;"
    },
    "belkin.com": {
      "password-rules": "minlength: 8; required: lower, upper; required: digit; required: [$!@~_,%&];"
    },
    "benefitslogin.discoverybenefits.com": {
      "password-rules": "minlength: 10; required: upper; required: digit; required: [!#$%&*?@]; allowed: lower;"
    },
    "benjerry.com": {
      "password-rules": "required: upper; required: upper; required: digit; required: digit; required: special; required: special; allowed: lower;"
    },
    "bestbuy.com": {
      "password-rules": "minlength: 20; required: lower; required: upper; required: digit; required: special;"
    },
    "bhphotovideo.com": {
      "password-rules": "maxlength: 15;"
    },
    "bilibili.com": {
      "password-rules": "maxlength: 16;"
    },
    "billerweb.com": {
      "password-rules": "minlength: 8; max-consecutive: 2; required: digit; required: upper,lower;"
    },
    "biovea.com": {
      "password-rules": "maxlength: 19;"
    },
    "bitly.com": {
      "password-rules": "minlength: 6; required: lower; required: upper; required: digit; required: [`!@#$%^&*()+~{}'\";:<>?]];"
    },
    "blackwells.co.uk": {
      "password-rules": "minlength: 8; maxlength: 30; allowed: upper,lower,digit;"
    },
    "bloomingdales.com": {
      "password-rules": "minlength: 7; maxlength: 16; required: lower, upper; required: digit; required: [`!@#$%^&*()+~{}'\";:<>?]];"
    },
    "bluesguitarunleashed.com": {
      "password-rules": "allowed: lower, upper, digit, [!$#@];"
    },
    "bochk.com": {
      "password-rules": "minlength: 8; maxlength: 12; max-consecutive: 3; required: lower; required: upper; required: digit; allowed: [#$%&()*+,.:;<=>?@_];"
    },
    "box.com": {
      "password-rules": "minlength: 6; maxlength: 20; required: lower; required: upper; required: digit; required: digit;"
    },
    "bpl.bibliocommons.com": {
      "password-rules": "minlength: 4; maxlength: 4; required: digit;"
    },
    "brighthorizons.com": {
      "password-rules": "minlength: 8; maxlength: 16;"
    },
    "callofduty.com": {
      "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 2; required: lower, upper; required: digit;"
    },
    "candyrect.com": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit;"
    },
    "capitalone.com": {
      "password-rules": "minlength: 8; maxlength: 32; required: lower, upper; required: digit; allowed: [-_./\\@$*&!#];"
    },
    "cardbenefitservices.com": {
      "password-rules": "minlength: 7; maxlength: 100; required: lower, upper; required: digit;"
    },
    "carrefour.it": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&*?@_];"
    },
    "carrsqc.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "carte-mobilite-inclusion.fr": {
      "password-rules": "minlength: 12; maxlength: 30; required: lower; required: upper; required: digit;"
    },
    "cathaypacific.com": {
      "password-rules": "minlength: 8; maxlength: 20; required: upper; required: digit; required: [!#$*^]; allowed: lower;"
    },
    "cb2.com": {
      "password-rules": "minlength: 9; required: lower, upper; required: digit; required: [!#*_%.$];"
    },
    "ccs-grp.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: digit; required: upper,lower; allowed: [-!#$%&'+./=?\\^_`{|}~];"
    },
    "cecredentialtrust.com": {
      "password-rules": "minlength: 12; required: lower; required: upper; required: digit; required: [!#$%&*@^];"
    },
    "charlie.mbta.com": {
      "password-rules": "minlength: 10; required: lower; required: upper; required: digit; required: [!#$%@^];"
    },
    "chase.com": {
      "password-rules": "minlength: 8; maxlength: 32; max-consecutive: 2; required: lower, upper; required: digit; required: [!#$%+/=@~];"
    },
    "cigna.co.uk": {
      "password-rules": "minlength: 8; maxlength: 12; required: lower; required: upper; required: digit;"
    },
    "citi.com": {
      "password-rules": "minlength: 8; maxlength: 64; max-consecutive: 2; required: digit; required: upper; required: lower; required: [-~`!@#$%^&*()_\\/|];"
    },
    "claimlookup.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [@#$%^&+=!];"
    },
    "clarksoneyecare.com": {
      "password-rules": "minlength: 9; allowed: lower; required: upper; required: digit; required: [~!@#$%^&*()_+{}|;,.<>?[]];"
    },
    "claro.com.br": {
      "password-rules": "minlength: 8; required: lower; allowed: upper, digit, [-!@#$%&*_+=<>];"
    },
    "classmates.com": {
      "password-rules": "minlength: 6; maxlength: 20; allowed: lower, upper, digit, [!@#$%^&*];"
    },
    "clegc-gckey.gc.ca": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower, upper, digit;"
    },
    "clien.net": {
      "password-rules": "minlength: 5; required: lower, upper; required: digit;"
    },
    "cogmembers.org": {
      "password-rules": "minlength: 8; maxlength: 14; required: upper; required: digit; allowed: lower;"
    },
    "collectivehealth.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit;"
    },
    "comcastpaymentcenter.com": {
      "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 2;required: lower, upper; required: digit;"
    },
    "comed.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: [-~!@#$%^&*_+=`|(){}[:;\"'<>,.?/\\]];"
    },
    "commerzbank.de": {
      "password-rules": "minlength: 5; maxlength: 8; required: lower, upper; required: digit;"
    },
    "consorsbank.de": {
      "password-rules": "minlength: 5; maxlength: 5; required: lower, upper, digit;"
    },
    "consorsfinanz.de": {
      "password-rules": "minlength: 6; maxlength: 15; allowed: lower, upper, digit, [-.];"
    },
    "costco.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower, upper; allowed: digit, [-!#$%&'()*+/:;=?@[^_`{|}~]];"
    },
    "coursera.com": {
      "password-rules": "minlength: 8; maxlength: 72;"
    },
    "cox.com": {
      "password-rules": "minlength: 8; maxlength: 24; required: digit; required: upper,lower; allowed: [!#$%()*@^];"
    },
    "crateandbarrel.com": {
      "password-rules": 'minlength: 9; maxlength: 64; required: lower; required: upper; required: digit; required: [!"#$%&()*,.:<>?@^_{|}];'
    },
    "crowdgen.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [!#$%&()*+=@^_];"
    },
    "cvs.com": {
      "password-rules": "minlength: 8; maxlength: 25; required: lower, upper; required: digit; required: [!@#$%^&*()];"
    },
    "dailymail.co.uk": {
      "password-rules": "minlength: 5; maxlength: 15;"
    },
    "dan.org": {
      "password-rules": "minlength: 8; maxlength: 25; required: lower; required: upper; required: digit; required: [!@$%^&*];"
    },
    "danawa.com": {
      "password-rules": "minlength: 8; maxlength: 21; required: lower, upper; required: digit; required: [!@$%^&*];"
    },
    "darty.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit;"
    },
    "dbs.com.hk": {
      "password-rules": "minlength: 8; maxlength: 30; required: lower; required: upper; required: digit;"
    },
    "decluttr.com": {
      "password-rules": "minlength: 8; maxlength: 45; required: lower; required: upper; required: digit;"
    },
    "delta.com": {
      "password-rules": "minlength: 8; maxlength: 20; required: lower; required: upper; required: digit;"
    },
    "deutsche-bank.de": {
      "password-rules": "minlength: 5; maxlength: 5; required: lower, upper, digit;"
    },
    "devstore.cn": {
      "password-rules": "minlength: 6; maxlength: 12;"
    },
    "dickssportinggoods.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&*?@^];"
    },
    "dkb.de": {
      "password-rules": "minlength: 8; maxlength: 38; required: lower, upper; required: digit; allowed: [-\xE4\xFC\xF6\xC4\xDC\xD6\xDF!$%&/()=?+#,.:];"
    },
    "dmm.com": {
      "password-rules": "minlength: 4; maxlength: 16; required: lower; required: upper; required: digit;"
    },
    "dodgeridge.com": {
      "password-rules": "minlength: 8; maxlength: 12; required: lower; required: upper; required: digit;"
    },
    "dowjones.com": {
      "password-rules": "maxlength: 15;"
    },
    "ea.com": {
      "password-rules": "minlength: 8; maxlength: 64; required: lower; required: upper; required: digit; allowed: special;"
    },
    "easycoop.com": {
      "password-rules": "minlength: 8; required: upper; required: special; allowed: lower, digit;"
    },
    "easyjet.com": {
      "password-rules": "minlength: 6; maxlength: 20; required: lower; required: upper; required: digit; required: [-];"
    },
    "ebrap.org": {
      "password-rules": "minlength: 15; required: lower; required: lower; required: upper; required: upper; required: digit; required: digit; required: [-!@#$%^&*()_+|~=`{}[:\";'?,./.]]; required: [-!@#$%^&*()_+|~=`{}[:\";'?,./.]];"
    },
    "ecompanystore.com": {
      "password-rules": "minlength: 8; maxlength: 16; max-consecutive: 2; required: lower; required: upper; required: digit; required: [#$%*+.=@^_];"
    },
    "eddservices.edd.ca.gov": {
      "password-rules": "minlength: 8; maxlength: 12; required: lower; required: upper; required: digit; required: [!@#$%^&*()];"
    },
    "edistrict.kerala.gov.in": {
      "password-rules": "minlength: 5; maxlength: 15; required: lower; required: upper; required: digit; required: [!@#$];"
    },
    "empower-retirement.com": {
      "password-rules": "minlength: 8; maxlength: 16;"
    },
    "epicgames.com": {
      "password-rules": "minlength: 7; required: lower; required: upper; required: digit; required: [-!\"#$%&'()*+,./:;<=>?@[^_`{|}~]];"
    },
    "epicmix.com": {
      "password-rules": "minlength: 8; maxlength: 16;"
    },
    "equifax.com": {
      "password-rules": "minlength: 8; maxlength: 20; required: lower; required: upper; required: digit; required: [!$*+@];"
    },
    "essportal.excelityglobal.com": {
      "password-rules": "minlength: 6; maxlength: 8; allowed: lower, upper, digit;"
    },
    "ettoday.net": {
      "password-rules": "minlength: 6; maxlength: 12;"
    },
    "examservice.com.tw": {
      "password-rules": "minlength: 6; maxlength: 8;"
    },
    "expertflyer.com": {
      "password-rules": "minlength: 5; maxlength: 16; required: lower, upper; required: digit;"
    },
    "extraspace.com": {
      "password-rules": "minlength: 8; maxlength: 20; allowed: lower; required: upper, digit, [!#$%&*?@];"
    },
    "ezpassva.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: special;"
    },
    "fc2.com": {
      "password-rules": "minlength: 8; maxlength: 16;"
    },
    "fccaccessonline.com": {
      "password-rules": "minlength: 8; maxlength: 14; max-consecutive: 3; required: lower; required: upper; required: digit; required: [!#$%*^_];"
    },
    "fedex.com": {
      "password-rules": "minlength: 8; max-consecutive: 3; required: lower; required: upper; required: digit; allowed: [-!@#$%^&*_+=`|(){}[:;,.?]];"
    },
    "fidelity.com": {
      "password-rules": "minlength: 6; maxlength: 20; required: lower; required: upper; required: digit; required: [-!$%+,./:;=?@^_|]; max-consecutive: 2;"
    },
    "flysas.com": {
      "password-rules": "minlength: 8; maxlength: 14; required: lower; required: upper; required: digit; required: [-~!@#$%^&_+=`|(){}[:\"'<>,.?]];"
    },
    "fnac.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit;"
    },
    "fuelrewards.com": {
      "password-rules": "minlength: 8; maxlength: 16; allowed: upper,lower,digit,[!#$%@];"
    },
    "gamestop.com": {
      "password-rules": "minlength: 8; maxlength: 225; required: lower; required: upper; required: digit; required: [!@#$%];"
    },
    "gap.com": {
      "password-rules": "minlength: 8; maxlength: 24; required: lower; required: upper; required: digit; required: [-!@#$%^&*()_+];"
    },
    "garmin.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit;"
    },
    "getflywheel.com": {
      "password-rules": "minlength: 7; maxlength: 72;"
    },
    "girlscouts.org": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: [$#!];"
    },
    "gmx.net": {
      "password-rules": "minlength: 8; maxlength: 40; allowed: lower, upper, digit, [-<=>~!|()@#{}$%,.?^'&*_+`:;\"[]];"
    },
    "gocurb.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [$%&#*?!@^];"
    },
    "google.com": {
      "password-rules": `minlength: 8; allowed: lower, upper, digit, [-!"#$%&'()*+,./:;<=>?@[^_{|}~]];`
    },
    "guardiananytime.com": {
      "password-rules": "minlength: 8; maxlength: 50; max-consecutive: 2; required: lower; required: upper; required: digit, [-~!@#$%^&*_+=`|(){}[:;,.?]];"
    },
    "gwl.greatwestlife.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [-!#$%_=+<>];"
    },
    "haggen.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "hangseng.com": {
      "password-rules": "minlength: 8; maxlength: 30; required: lower; required: upper; required: digit;"
    },
    "hawaiianairlines.com": {
      "password-rules": "maxlength: 16;"
    },
    "hertz-japan.com": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz-kuwait.com": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz-saudi.com": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.at": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.be": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.bh": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.ca": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.ch": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.cn": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.co.ao": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.co.id": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.co.kr": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.co.nz": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.co.th": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.co.uk": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.com": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.com.au": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.com.bh": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.com.hk": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.com.kw": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.com.mt": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.com.pl": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.com.pt": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.com.sg": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.com.tw": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.cv": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.cz": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.de": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.ee": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.es": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.fi": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.fr": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.hu": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.ie": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.it": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.jo": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.lt": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.nl": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.no": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.nu": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.pl": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.pt": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.qa": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.ru": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.se": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertz.si": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hertzcaribbean.com": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
    },
    "hetzner.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [-^!$%/()=?+#.,;:~*@{}_&[]];"
    },
    "hilton.com": {
      "password-rules": "minlength: 8; maxlength: 32; required: lower; required: upper; required: digit;"
    },
    "hkbea.com": {
      "password-rules": "minlength: 8; maxlength: 12; required: lower; required: upper; required: digit;"
    },
    "hkexpress.com": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; required: special;"
    },
    "hotels.com": {
      "password-rules": "minlength: 6; maxlength: 20; required: digit; required: [-~#@$%&!*_?^]; allowed: lower, upper;"
    },
    "hotwire.com": {
      "password-rules": "minlength: 6; maxlength: 30; allowed: lower, upper, digit, [-~!@#$%^&*_+=`|(){}[:;\"'<>,.?]];"
    },
    "hrblock.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [$#%!];"
    },
    "hsbc.com.hk": {
      "password-rules": "minlength: 6; maxlength: 30; required: lower; required: upper; required: digit; allowed: ['.@_];"
    },
    "hsbc.com.my": {
      "password-rules": "minlength: 8; maxlength: 30; required: lower, upper; required: digit; allowed: [-!$*.=?@_'];"
    },
    "hypovereinsbank.de": {
      "password-rules": 'minlength: 6; maxlength: 10; required: lower, upper, digit; allowed: [!"#$%&()*+:;<=>?@[{}~]];'
    },
    "hyresbostader.se": {
      "password-rules": "minlength: 6; maxlength: 20; required: lower, upper; required: digit;"
    },
    "ichunqiu.com": {
      "password-rules": "minlength: 8; maxlength: 20; required: lower; required: upper; required: digit;"
    },
    "id.sonyentertainmentnetwork.com": {
      "password-rules": "minlength: 8; maxlength: 30; required: lower, upper; required: digit; allowed: [-!@#^&*=+;:];"
    },
    "identity.codesignal.com": {
      "password-rules": "minlength: 14; required: digit; required: lower, upper; required: [!#$%&*@^]"
    },
    "identitytheft.gov": {
      "password-rules": "allowed: lower, upper, digit, [!#%&*@^];"
    },
    "idestination.info": {
      "password-rules": "maxlength: 15;"
    },
    "impots.gouv.fr": {
      "password-rules": "minlength: 12; maxlength: 128; required: lower; required: digit; allowed: [-!#$%&*+/=?^_'.{|}];"
    },
    "indochino.com": {
      "password-rules": "minlength: 6; maxlength: 15; required: upper; required: digit; allowed: lower, special;"
    },
    "inntopia.travel": {
      "password-rules": "minlength: 7; maxlength: 19; required: digit; allowed: upper,lower,[-];"
    },
    "internationalsos.com": {
      "password-rules": "required: lower; required: upper; required: digit; required: [@#$%^&+=_];"
    },
    "irctc.co.in": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; required: [!@#$%^&*()+];"
    },
    "irs.gov": {
      "password-rules": "minlength: 8; maxlength: 32; required: lower; required: upper; required: digit; required: [!#$%&*@];"
    },
    "jal.co.jp": {
      "password-rules": "minlength: 8; maxlength: 16;"
    },
    "japanpost.jp": {
      "password-rules": "minlength: 8; maxlength: 16; required: digit; required: upper,lower;"
    },
    "jewelosco.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "jordancu-onlinebanking.org": {
      "password-rules": "minlength: 6; maxlength: 32; allowed: upper, lower, digit,[-!\"#$%&'()*+,.:;<=>?@[^_`{|}~]];"
    },
    "keldoc.com": {
      "password-rules": "minlength: 12; required: lower; required: upper; required: digit; required: [!@#$%^&*];"
    },
    "kennedy-center.org": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&*?@];"
    },
    "key.harvard.edu": {
      "password-rules": "minlength: 10; maxlength: 100; required: lower; required: upper; required: digit; allowed: [-@_#!&$`%*+()./,;~:{}|?>=<^[']];"
    },
    "kfc.ca": {
      "password-rules": "minlength: 6; maxlength: 15; required: lower; required: upper; required: digit; required: [!@#$%&?*];"
    },
    "kiehls.com": {
      "password-rules": "minlength: 8; maxlength: 25; required: lower; required: upper; required: digit; required: [!#$%&?@];"
    },
    "kingsfoodmarkets.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "klm.com": {
      "password-rules": "minlength: 8; maxlength: 12;"
    },
    "kundenportal.edeka-smart.de": {
      "password-rules": 'minlength: 8; maxlength: 16; required: digit; required: upper, lower; required: [!"\xA7$%&#];'
    },
    "la-z-boy.com": {
      "password-rules": "minlength: 6; maxlength: 15; required: lower, upper; required: digit;"
    },
    "labcorp.com": {
      "password-rules": "minlength: 8; maxlength: 20; required: upper; required: lower; required: digit; required: [!@#$%^&*];"
    },
    "ladwp.com": {
      "password-rules": "minlength: 8; maxlength: 20; required: digit; allowed: lower, upper;"
    },
    "launtel.net.au": {
      "password-rules": "minlength: 8; required: digit; required: digit; allowed: lower, upper;"
    },
    "leetchi.com": {
      "password-rules": 'minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&()*+,./:;<>?@"_];'
    },
    "lepida.it": {
      "password-rules": "minlength: 8; maxlength: 16; max-consecutive: 2; required: lower; required: upper; required: digit; required: [-!\"#$%&'()*+,.:;<=>?@[^_`{|}~]];"
    },
    "lg.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: [-!#$%&'()*+,.:;=?@[^_{|}~]];"
    },
    "linearity.io": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: special;"
    },
    "live.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; allowed: [-@_#!&$`%*+()./,;~:{}|?>=<^'[]];"
    },
    "lloydsbank.co.uk": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: digit; allowed: upper;"
    },
    "lowes.com": {
      "password-rules": "minlength: 8; maxlength: 128; max-consecutive: 3; required: lower, upper; required: digit;"
    },
    "loyalty.accor.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&=@];"
    },
    "lsacsso.b2clogin.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit, [-!#$%&*?@^_];"
    },
    "lufthansa.com": {
      "password-rules": 'minlength: 8; maxlength: 32; required: lower; required: upper; required: digit; required: [!#$%&()*+,./:;<>?@"_];'
    },
    "lufthansagroup.careers": {
      "password-rules": "minlength: 12; required: lower; required: upper; required: digit; required: [!#$%&*?@];"
    },
    "macys.com": {
      "password-rules": "minlength: 7; maxlength: 16; allowed: lower, upper, digit, [~!@#$%^&*+`(){}[:;\"'<>?]];"
    },
    "mailbox.org": {
      "password-rules": 'minlength: 8; required: lower; required: upper; required: digit; allowed: [-!$"%&/()=*+#.,;:@?{}[]];'
    },
    "makemytrip.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [@$!%*#?&];"
    },
    "marriott.com": {
      "password-rules": "minlength: 8; maxlength: 20; required: lower; required: upper; required: digit; allowed: [$!#&@?%=];"
    },
    "maybank2u.com.my": {
      "password-rules": "minlength: 8; maxlength: 12; max-consecutive: 2; required: lower; required: upper; required: digit; required: [-~!@#$%^&*_+=`|(){}[:;\"'<>,.?];"
    },
    "medicare.gov": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [@!$%^*()];"
    },
    "meineschufa.de": {
      "password-rules": "minlength: 10; required: lower; required: upper; required: digit; required: [!?#%$];"
    },
    "member.everbridge.net": {
      "password-rules": "minlength: 8; required: lower, upper; required: digit; allowed: [!@#$%^&*()];"
    },
    "metlife.com": {
      "password-rules": "minlength: 6; maxlength: 20;"
    },
    "microsoft.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: special;"
    },
    "milogin.michigan.gov": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [@#$!~&];"
    },
    "mintmobile.com": {
      "password-rules": "minlength: 8; maxlength: 20; required: lower; required: upper; required: digit; required: special; allowed: [!#$%&()*+:;=@[^_`{}~]];"
    },
    "mlb.com": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit;"
    },
    "mountainwarehouse.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: [-@#$%^&*_+={}|\\:',?/`~\"();.];"
    },
    "mpv.tickets.com": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit;"
    },
    "museumofflight.org": {
      "password-rules": "minlength: 8; maxlength: 15;"
    },
    "my.konami.net": {
      "password-rules": "minlength: 8; maxlength: 32; required: lower; required: upper; required: digit;"
    },
    "myaccess.dmdc.osd.mil": {
      "password-rules": "minlength: 9; maxlength: 20; required: lower; required: upper; required: digit; allowed: [-@_#!&$`%*+()./,;~:{}|?>=<^'[]];"
    },
    "mygoodtogo.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower, upper, digit;"
    },
    "myhealthrecord.com": {
      "password-rules": "minlength: 8; maxlength: 20; allowed: lower, upper, digit, [_.!$*=];"
    },
    "mypay.dfas.mil": {
      "password-rules": "minlength: 9; maxlength: 30; required: lower; required: upper; required: digit; required: [#@$%^!*+=_];"
    },
    "mysavings.breadfinancial.com": {
      "password-rules": "minlength: 8; maxlength: 25; required: lower; required: upper; required: digit; required: [+_%@!$*~];"
    },
    "mysedgwick.com": {
      "password-rules": "minlength: 8; maxlength: 16; allowed: lower; required: upper; required: digit; required: [@#%^&+=!]; allowed: [-~_$.,;]"
    },
    "mysubaru.com": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; allowed: [!#$%()*+,./:;=?@\\^`~];"
    },
    "naver.com": {
      "password-rules": "minlength: 6; maxlength: 16;"
    },
    "nekochat.cn": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit;"
    },
    "nelnet.net": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit, [!@#$&*];"
    },
    "netflix.com": {
      "password-rules": "minlength: 4; maxlength: 60; required: lower, upper, digit; allowed: special;"
    },
    "netgear.com": {
      "password-rules": "minlength: 6; maxlength: 128; allowed: lower, upper, digit, [!@#$%^&*()];"
    },
    "nowinstock.net": {
      "password-rules": "minlength: 6; maxlength: 20; allowed: lower, upper, digit;"
    },
    "order.wendys.com": {
      "password-rules": "minlength: 6; maxlength: 20; required: lower; required: upper; required: digit; allowed: [!#$%&()*+/=?^_{}];"
    },
    "ototoy.jp": {
      "password-rules": "minlength: 8; allowed: upper,lower,digit,[- .=_];"
    },
    "packageconciergeadmin.com": {
      "password-rules": "minlength: 4; maxlength: 4; allowed: digit;"
    },
    "pavilions.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "paypal.com": {
      "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 3; required: lower, upper; required: digit, [!@#$%^&*()];"
    },
    "payvgm.youraccountadvantage.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: special;"
    },
    "pilotflyingj.com": {
      "password-rules": "minlength: 7; required: digit; allowed: lower, upper;"
    },
    "pixnet.cc": {
      "password-rules": "minlength: 4; maxlength: 16; allowed: lower, upper;"
    },
    "planetary.org": {
      "password-rules": "minlength: 5; maxlength: 20; required: lower; required: upper; required: digit; allowed: ascii-printable;"
    },
    "plazapremiumlounge.com": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; allowed: [!#$%&*,@^];"
    },
    "portal.edd.ca.gov": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&()*@^];"
    },
    "portals.emblemhealth.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&'()*+,./:;<>?@\\^_`{|}~[]];"
    },
    "portlandgeneral.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: [!#$%&*?@];"
    },
    "poste.it": {
      "password-rules": "minlength: 8; maxlength: 16; max-consecutive: 2; required: lower; required: upper; required: digit; required: special;"
    },
    "posteo.de": {
      "password-rules": 'minlength: 8; required: lower; required: upper; required: digit, [-~!#$%&_+=|(){}[:;"\u2019<>,.? ]];'
    },
    "powells.com": {
      "password-rules": 'minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: ["!@#$%^&*(){}[]];'
    },
    "preferredhotels.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&()*+@^_];"
    },
    "premier.ticketek.com.au": {
      "password-rules": "minlength: 6; maxlength: 16;"
    },
    "premierinn.com": {
      "password-rules": "minlength: 8; required: upper; required: digit; allowed: lower;"
    },
    "prepaid.bankofamerica.com": {
      "password-rules": `minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [!@#$%^&*()+~{}'";:<>?];`
    },
    "prestocard.ca": {
      "password-rules": `minlength: 8; required: lower; required: upper; required: digit,[!"#$%&'()*+,<>?@];`
    },
    "pret.com": {
      "password-rules": "minlength: 12; required: lower; required: digit; required: [@$!%*#?&]; allowed: upper;"
    },
    "propelfuels.com": {
      "password-rules": "minlength: 6; maxlength: 16;"
    },
    "publix.com": {
      "password-rules": "minlength: 8; maxlength: 28; required: upper; required: lower; allowed: digit,[!#$%*@^];"
    },
    "qdosstatusreview.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&@^];"
    },
    "questdiagnostics.com": {
      "password-rules": "minlength: 8; maxlength: 30; required: upper, lower; required: digit, [!#$%&()*+<>?@^_~];"
    },
    "randalls.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "rejsekort.dk": {
      "password-rules": "minlength: 7; maxlength: 15; required: lower; required: upper; required: digit;"
    },
    "renaud-bray.com": {
      "password-rules": "minlength: 8; maxlength: 38; allowed: upper,lower,digit;"
    },
    "ring.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!@#$%^&*<>?];"
    },
    "riteaid.com": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit;"
    },
    "robinhood.com": {
      "password-rules": "minlength: 10;"
    },
    "rogers.com": {
      "password-rules": "minlength: 8; required: lower, upper; required: digit; required: [!@#$];"
    },
    "ruc.dk": {
      "password-rules": "minlength: 6; maxlength: 8; required: lower, upper; required: [-!#%&(){}*+;%/<=>?_];"
    },
    "runescape.com": {
      "password-rules": "minlength: 5; maxlength: 20; required: lower; required: upper; required: digit;"
    },
    "ruten.com.tw": {
      "password-rules": "minlength: 6; maxlength: 15; required: lower, upper;"
    },
    "safeway.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "salslimo.com": {
      "password-rules": "minlength: 8; maxlength: 50; required: upper; required: lower; required: digit; required: [!@#$&*];"
    },
    "santahelenasaude.com.br": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; required: [-!@#$%&*_+=<>];"
    },
    "santander.de": {
      "password-rules": "minlength: 8; maxlength: 12; required: lower, upper; required: digit; allowed: [-!#$%&'()*,.:;=?^{}];"
    },
    "savemart.com": {
      "password-rules": "minlength: 8; maxlength: 12; required: digit; required: upper,lower; required: [!#$%&@]; allowed: ascii-printable;"
    },
    "sbisec.co.jp": {
      "password-rules": "minlength: 10; maxlength: 20; allowed: upper,lower,digit;"
    },
    "secure-arborfcu.org": {
      "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; required: [!#$%&'()+,.:?@[_`~]];"
    },
    "secure.orclinic.com": {
      "password-rules": "minlength: 6; maxlength: 15; required: lower; required: digit; allowed: ascii-printable;"
    },
    "secure.snnow.ca": {
      "password-rules": "minlength: 7; maxlength: 16; required: digit; allowed: lower, upper;"
    },
    "sephora.com": {
      "password-rules": "minlength: 6; maxlength: 12;"
    },
    "serviziconsolari.esteri.it": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: special;"
    },
    "servizioelettriconazionale.it": {
      "password-rules": "minlength: 8; maxlength: 20; required: lower; required: upper; required: digit; required: [!#$%&*?@^_~];"
    },
    "sfwater.org": {
      "password-rules": "minlength: 10; maxlength: 30; required: digit; allowed: lower, upper, [!@#$%*()_+^}{:;?.];"
    },
    "shaws.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "signin.ea.com": {
      "password-rules": "minlength: 8; maxlength: 64; required: lower, upper; required: digit; allowed: [-!@#^&*=+;:];"
    },
    "southwest.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: upper; required: digit; allowed: lower, [!@#$%^*(),.;:/\\];"
    },
    "speedway.com": {
      "password-rules": "minlength: 4; maxlength: 8; required: digit;"
    },
    "spirit.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [!@#$%^&*()];"
    },
    "splunk.com": {
      "password-rules": "minlength: 8; maxlength: 64; required: lower; required: upper; required: digit; required: [-!@#$%&*_+=<>];"
    },
    "ssa.gov": {
      "password-rules": "required: lower; required: upper; required: digit; required: [~!@#$%^&*];"
    },
    "starmarket.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "store.nintendo.co.uk": {
      "password-rules": "minlength: 8; maxlength: 20;"
    },
    "store.nvidia.com": {
      "password-rules": "minlength: 8; maxlength: 32; required: lower; required: upper; required: digit; required: [-!@#$%^*~:;&><[{}|_+=?]];"
    },
    "store.steampowered.com": {
      "password-rules": "minlength: 6; required: lower; required: upper; required: digit; allowed: [~!@#$%^&*];"
    },
    "subscribe.free.fr": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [!#&()*+/@[_]];"
    },
    "successfactors.eu": {
      "password-rules": "minlength: 8; maxlength: 18; required: lower; required: upper; required: digit,[-!\"#$%&'()*+,.:;<=>?@[^_`{|}~]];"
    },
    "sulamericaseguros.com.br": {
      "password-rules": "minlength: 6; maxlength: 6;"
    },
    "sunlife.com": {
      "password-rules": "minlength: 8; maxlength: 10; required: digit; required: lower, upper;"
    },
    "t-mobile.net": {
      "password-rules": "minlength: 8; maxlength: 16;"
    },
    "target.com": {
      "password-rules": "minlength: 8; maxlength: 20; required: lower, upper; required: digit, [-!\"#$%&'()*+,./:;=?@[\\^_`{|}~];"
    },
    "tdscpc.gov.in": {
      "password-rules": `minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; required: [ &',;"];`
    },
    "telekom-dienste.de": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [#$%&()*+,./<=>?@_{|}~];"
    },
    "thameswater.co.uk": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: special;"
    },
    "themovingportal.co.uk": {
      "password-rules": `minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: [-@#$%^&*_+={}|\\:',?/'~" ();.[]];`
    },
    "ticketweb.com": {
      "password-rules": "minlength: 12; maxlength: 15;"
    },
    "tix.soundrink.com": {
      "password-rules": "minlength: 6; maxlength: 16;"
    },
    "tomthumb.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "training.confluent.io": {
      "password-rules": "minlength: 6; maxlength: 16; required: lower; required: upper; required: digit; allowed: [!#$%*@^_~];"
    },
    "treasurer.mo.gov": {
      "password-rules": "minlength: 8; maxlength: 26; required: lower; required: upper; required: digit; required: [!#$&];"
    },
    "truist.com": {
      "password-rules": "minlength: 8; maxlength: 28; max-consecutive: 2; required: lower; required: upper; required: digit; required: [!#$%()*,:;=@_];"
    },
    "turkishairlines.com": {
      "password-rules": "minlength: 6; maxlength: 6; required: digit; max-consecutive: 3;"
    },
    "twitch.tv": {
      "password-rules": "minlength: 8; maxlength: 71;"
    },
    "twitter.com": {
      "password-rules": "minlength: 8;"
    },
    "ubisoft.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [-]; required: [!@#$%^&*()+];"
    },
    "udel.edu": {
      "password-rules": "minlength: 12; maxlength: 30; required: lower; required: upper; required: digit; required: [!@#$%^&*()+];"
    },
    "umterps.evenue.net": {
      "password-rules": "minlength: 14; required: digit; required: upper; required: lower; required: [-~!@#$%^&*_+=`|(){}:;];"
    },
    "unito.it": {
      "password-rules": `minlength: 8; required: upper; required: lower; required: digit; required: [-!?+*/:;'"{}()@\xA3$%&=^#[]];`
    },
    "user.ornl.gov": {
      "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower, upper; required: digit; allowed: [!#$%./_];"
    },
    "usps.com": {
      "password-rules": `minlength: 8; maxlength: 50; max-consecutive: 2; required: lower; required: upper; required: digit; allowed: [-!"#&'()+,./?@];`
    },
    "vanguard.com": {
      "password-rules": "minlength: 6; maxlength: 20; required: lower; required: upper; required: digit; required: digit;"
    },
    "vanguardinvestor.co.uk": {
      "password-rules": "minlength: 8; maxlength: 50; required: lower; required: upper; required: digit; required: digit;"
    },
    "ventrachicago.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit, [!@#$%^];"
    },
    "verizonwireless.com": {
      "password-rules": "minlength: 8; maxlength: 20; required: lower, upper; required: digit; allowed: unicode;"
    },
    "vetsfirstchoice.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; allowed: [?!@$%^+=&];"
    },
    "vince.com": {
      "password-rules": "minlength: 8; required: digit; required: lower; required: upper; required: [$%/(){}=?!.,_*|+~#[]];"
    },
    "virginmobile.ca": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$@];"
    },
    "visa.com": {
      "password-rules": "minlength: 6; maxlength: 32;"
    },
    "visabenefits-auth.axa-assistance.us": {
      "password-rules": 'minlength: 8; required: lower; required: upper; required: digit; required: [!"#$%&()*,.:<>?@^{|}];'
    },
    "vivo.com.br": {
      "password-rules": "maxlength: 6; max-consecutive: 3; allowed: digit;"
    },
    "volaris.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: special;"
    },
    "vons.com": {
      "password-rules": "minlength: 8; maxlength: 40; required: upper; required: [!#$%&*@^]; allowed: lower,digit;"
    },
    "wa.aaa.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: ascii-printable;"
    },
    "walkhighlands.co.uk": {
      "password-rules": "minlength: 9; maxlength: 15; required: lower; required: upper; required: digit; allowed: special;"
    },
    "walmart.com": {
      "password-rules": "allowed: lower, upper, digit, [-(~!@#$%^&*_+=`|(){}[:;\"'<>,.?]];"
    },
    "waze.com": {
      "password-rules": "minlength: 8; maxlength: 64; required: lower, upper, digit;"
    },
    "wccls.org": {
      "password-rules": "minlength: 4; maxlength: 16; allowed: lower, upper, digit;"
    },
    "web.de": {
      "password-rules": "minlength: 8; maxlength: 40; allowed: lower, upper, digit, [-<=>~!|()@#{}$%,.?^'&*_+`:;\"[]];"
    },
    "wegmans.com": {
      "password-rules": "minlength: 8; required: digit; required: upper,lower; required: [!#$%&*+=?@^];"
    },
    "weibo.com": {
      "password-rules": "minlength: 6; maxlength: 16;"
    },
    "wellsfargo.com": {
      "password-rules": "minlength: 8; maxlength: 32; required: lower; required: upper; required: digit;"
    },
    "wmata.com": {
      "password-rules": 'minlength: 8; required: lower, upper; required: digit; required: digit; required: [-!@#$%^&*~/"()_=+\\|,.?[]];'
    },
    "worldstrides.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [-!#$%&*+=?@^_~];"
    },
    "wsj.com": {
      "password-rules": "minlength: 5; maxlength: 15; required: digit; allowed: lower, upper, [-~!@#$^*_=`|(){}[:;\"'<>,.?]];"
    },
    "xfinity.com": {
      "password-rules": "minlength: 8; maxlength: 16; required: lower, upper; required: digit;"
    },
    "xvoucher.com": {
      "password-rules": "minlength: 11; required: upper; required: digit; required: [!@#$%&_];"
    },
    "yatra.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&'()+,.:?@[_`~]];"
    },
    "yeti.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [#$%*];"
    },
    "zara.com": {
      "password-rules": "minlength: 8; required: lower; required: upper; required: digit;"
    },
    "zdf.de": {
      "password-rules": "minlength: 8; required: upper; required: digit; allowed: lower, special;"
    },
    "zoom.us": {
      "password-rules": "minlength: 8; maxlength: 32; max-consecutive: 6; required: lower; required: upper; required: digit;"
    }
  };

  // src/PasswordGenerator.js
  var _previous;
  var PasswordGenerator = class {
    constructor() {
      /** @type {string|null} */
      __privateAdd(this, _previous, null);
    }
    /** @returns {boolean} */
    get generated() {
      return __privateGet(this, _previous) !== null;
    }
    /** @returns {string|null} */
    get password() {
      return __privateGet(this, _previous);
    }
    /** @param {import('../packages/password').GenerateOptions} [params] */
    generate(params = {}) {
      if (__privateGet(this, _previous)) {
        return __privateGet(this, _previous);
      }
      __privateSet(this, _previous, generate({ ...params, rules: rules_default }));
      return __privateGet(this, _previous);
    }
  };
  _previous = new WeakMap();

  // src/Form/FormAnalyzer.js
  var FormAnalyzer = class {
    /**
     * @param {HTMLElement} form
     * @param {import('../site-specific-feature').default|null} siteSpecificFeature
     * @param {HTMLInputElement|HTMLSelectElement} input
     * @param {Matching} [matching]
     */
    constructor(form, siteSpecificFeature, input, matching) {
      /** @type HTMLElement */
      __publicField(this, "form");
      /** @type Matching */
      __publicField(this, "matching");
      /** @type {import('../site-specific-feature').default|null} */
      __publicField(this, "siteSpecificFeature");
      /** @type {undefined|boolean} */
      __publicField(this, "_isCCForm");
      this.form = form;
      this.siteSpecificFeature = siteSpecificFeature;
      this.matching = matching || new Matching(matchingConfiguration);
      this.autofillSignal = 0;
      this.hybridSignal = 0;
      this.signals = [];
      this.evaluateElAttributes(input, 1, true);
      if (form !== input) {
        this.evaluateForm();
      } else {
        this.evaluatePage();
      }
      return this;
    }
    areLoginOrSignupSignalsWeak() {
      return Math.abs(this.autofillSignal) < 10;
    }
    /**
     * Hybrid forms can be used for both login and signup
     * @returns {boolean}
     */
    get isHybrid() {
      const forcedFormType = this.siteSpecificFeature?.getForcedFormType(this.form);
      if (forcedFormType) {
        return forcedFormType === "hybrid";
      }
      return this.hybridSignal > 0 && this.areLoginOrSignupSignalsWeak();
    }
    get isLogin() {
      const forcedFormType = this.siteSpecificFeature?.getForcedFormType(this.form);
      if (forcedFormType) {
        return forcedFormType === "login";
      }
      if (this.isHybrid) return false;
      return this.autofillSignal < 0;
    }
    get isSignup() {
      const forcedFormType = this.siteSpecificFeature?.getForcedFormType(this.form);
      if (forcedFormType) {
        return forcedFormType === "signup";
      }
      if (this.isHybrid) return false;
      return this.autofillSignal >= 0;
    }
    /**
     * Tilts the scoring towards Signup
     * @param {number} strength
     * @param {string} signal
     * @returns {FormAnalyzer}
     */
    increaseSignalBy(strength, signal) {
      this.autofillSignal += strength;
      this.signals.push(`${signal}: +${strength}`);
      return this;
    }
    /**
     * Tilts the scoring towards Login
     * @param {number} strength
     * @param {string} signal
     * @returns {FormAnalyzer}
     */
    decreaseSignalBy(strength, signal) {
      this.autofillSignal -= strength;
      this.signals.push(`${signal}: -${strength}`);
      return this;
    }
    /**
     * Increases the probability that this is a hybrid form (can be either login or signup)
     * @param {number} strength
     * @param {string} signal
     * @returns {FormAnalyzer}
     */
    increaseHybridSignal(strength, signal) {
      this.hybridSignal += strength;
      this.signals.push(`${signal} (hybrid): +${strength}`);
      return this;
    }
    /**
     * Updates the Login<->Signup signal according to the provided parameters
     * @param {object} p
     * @param {string} p.string - The string to check
     * @param {number} p.strength - Strength of the signal
     * @param {string} [p.signalType] - For debugging purposes, we give a name to the signal
     * @param {boolean} [p.shouldFlip] - Flips the signals, i.e. when a link points outside. See below
     * @param {boolean} [p.shouldCheckUnifiedForm] - Should check for login/signup forms
     * @param {boolean} [p.shouldBeConservative] - Should use the conservative signup regex
     * @returns {FormAnalyzer}
     */
    updateSignal({
      string,
      strength,
      signalType = "generic",
      shouldFlip = false,
      shouldCheckUnifiedForm = false,
      shouldBeConservative = false
    }) {
      if (!string || string.length > constants.TEXT_LENGTH_CUTOFF) return this;
      const matchesLogin = safeRegexTest(/current.?password/i, string) || safeRegexTest(this.matching.getDDGMatcherRegex("loginRegex"), string) || safeRegexTest(this.matching.getDDGMatcherRegex("resetPasswordLink"), string);
      if (shouldCheckUnifiedForm && matchesLogin && safeRegexTest(this.matching.getDDGMatcherRegex("conservativeSignupRegex"), string)) {
        this.increaseHybridSignal(strength, signalType);
        return this;
      }
      const signupRegexToUse = this.matching.getDDGMatcherRegex(shouldBeConservative ? "conservativeSignupRegex" : "signupRegex");
      const matchesSignup = safeRegexTest(/new.?(password|username)/i, string) || safeRegexTest(signupRegexToUse, string);
      if (shouldFlip) {
        if (matchesLogin) this.increaseSignalBy(strength, signalType);
        if (matchesSignup) this.decreaseSignalBy(strength, signalType);
      } else {
        if (matchesLogin) this.decreaseSignalBy(strength, signalType);
        if (matchesSignup) this.increaseSignalBy(strength, signalType);
      }
      return this;
    }
    evaluateElAttributes(el, signalStrength = 3, isInput = false) {
      Array.from(el.attributes).forEach((attr) => {
        if (attr.name === "style") return;
        const attributeString = `${attr.name}=${attr.value}`;
        this.updateSignal({
          string: attributeString,
          strength: signalStrength,
          signalType: `${el.name} attr: ${attributeString}`,
          shouldCheckUnifiedForm: isInput,
          shouldBeConservative: true
        });
      });
    }
    evaluateUrl() {
      const { pathname, hash } = window.location;
      const pathToMatch = pathname + hash;
      const matchesLogin = safeRegexTest(this.matching.getDDGMatcherRegex("loginRegex"), pathToMatch);
      const matchesSignup = safeRegexTest(this.matching.getDDGMatcherRegex("conservativeSignupRegex"), pathToMatch);
      if (matchesLogin && matchesSignup) return;
      if (matchesLogin) {
        this.decreaseSignalBy(1, "url matches login");
      }
      if (matchesSignup) {
        this.increaseSignalBy(1, "url matches signup");
      }
    }
    evaluatePageTitle() {
      const pageTitle = document.title;
      this.updateSignal({ string: pageTitle, strength: 2, signalType: `page title: ${pageTitle}`, shouldCheckUnifiedForm: true });
    }
    evaluatePageHeadings() {
      const headings = document.querySelectorAll("h1, h2, h3");
      headings.forEach((heading) => {
        const textContent = removeExcessWhitespace(heading.textContent || "");
        this.updateSignal({
          string: textContent,
          strength: 0.5,
          signalType: `heading: ${textContent}`,
          shouldCheckUnifiedForm: true,
          shouldBeConservative: true
        });
      });
    }
    evaluatePage() {
      this.evaluateUrl();
      this.evaluatePageTitle();
      this.evaluatePageHeadings();
      const buttons = document.querySelectorAll(this.matching.cssSelector("submitButtonSelector"));
      buttons.forEach((button) => {
        if (button instanceof HTMLButtonElement) {
          if (!button.form && !button.closest("form")) {
            this.evaluateElement(button);
            this.evaluateElAttributes(button, 0.5);
          }
        }
      });
    }
    evaluatePasswordHints() {
      const textContent = removeExcessWhitespace(this.form.textContent, 200);
      if (textContent) {
        const hasPasswordHints = safeRegexTest(this.matching.getDDGMatcherRegex("passwordHintsRegex"), textContent, 500);
        if (hasPasswordHints) {
          this.increaseSignalBy(5, "Password hints");
        }
      }
    }
    /**
     * Function that checks if the element is link like and navigating away from the current page
     * @param {any} el
     * @returns {boolean}
     */
    isOutboundLink(el) {
      const tagName = el.nodeName.toLowerCase();
      const isCustomWebElementLink = customElements?.get(tagName) != null && /-link$/.test(tagName) && findElementsInShadowTree(el, "a").length > 0;
      const isElementLikelyALink = (el2) => {
        if (el2 == null) return false;
        return el2 instanceof HTMLAnchorElement && el2.href && !el2.getAttribute("href")?.startsWith("#") || (el2.getAttribute("role") || "").toUpperCase() === "LINK" || el2.matches("button[class*=secondary]");
      };
      return isCustomWebElementLink || isElementLikelyALink(el) || isElementLikelyALink(el.closest("a"));
    }
    evaluateElement(el) {
      const string = getTextShallow(el);
      if (el.matches(this.matching.cssSelector("password"))) {
        this.updateSignal({
          string: el.getAttribute("autocomplete") || el.getAttribute("name") || "",
          strength: 5,
          signalType: `explicit: ${el.getAttribute("autocomplete")}`
        });
        return;
      }
      if (el.matches(this.matching.cssSelector("submitButtonSelector") + ", *[class*=button]")) {
        let likelyASubmit = isLikelyASubmitButton(el, this.matching);
        let shouldFlip = false;
        if (likelyASubmit) {
          this.form.querySelectorAll("input[type=submit], button[type=submit]").forEach((submit) => {
            if (el.getAttribute("type") !== "submit" && el !== submit) {
              likelyASubmit = false;
            }
          });
        } else {
          const hasAnotherSubmitButton = Boolean(this.form.querySelector("input[type=submit], button[type=submit]"));
          const buttonText = string;
          if (hasAnotherSubmitButton) {
            shouldFlip = this.shouldFlipScoreForButtonText(buttonText);
          } else {
            const isOutboundLink = this.isOutboundLink(el);
            shouldFlip = isOutboundLink && this.shouldFlipScoreForButtonText(buttonText);
          }
        }
        const strength = likelyASubmit ? 20 : 4;
        this.updateSignal({ string, strength, signalType: `button: ${string}`, shouldFlip });
        return;
      }
      if (this.isOutboundLink(el)) {
        let shouldFlip = true;
        let strength = 1;
        if (safeRegexTest(this.matching.getDDGMatcherRegex("resetPasswordLink"), string)) {
          shouldFlip = false;
          strength = 3;
        } else if (safeRegexTest(this.matching.getDDGMatcherRegex("loginProvidersRegex"), string)) {
          shouldFlip = false;
        }
        this.updateSignal({ string, strength, signalType: `external link: ${string}`, shouldFlip });
      } else {
        const isH1Element = el.tagName === "H1";
        this.updateSignal({ string, strength: isH1Element ? 3 : 1, signalType: `generic: ${string}`, shouldCheckUnifiedForm: true });
      }
    }
    evaluateForm() {
      this.evaluateUrl();
      this.evaluatePageTitle();
      this.evaluateElAttributes(this.form);
      const selector = this.matching.cssSelector("safeUniversalSelector");
      const formElements = queryElementsWithShadow(this.form, selector);
      for (let i = 0; i < formElements.length; i++) {
        if (i >= 200) break;
        const element = formElements[i];
        const displayValue = window.getComputedStyle(element, null).getPropertyValue("display");
        if (displayValue !== "none") this.evaluateElement(element);
      }
      const relevantFields = this.form.querySelectorAll(this.matching.cssSelector("genericTextInputField"));
      if (relevantFields.length >= 4) {
        this.increaseSignalBy(relevantFields.length * 1.5, "many fields: it is probably not a login");
      }
      if (this.areLoginOrSignupSignalsWeak()) {
        this.evaluatePasswordHints();
      }
      if (this.autofillSignal === 0) {
        this.evaluatePageHeadings();
      }
      return this;
    }
    /**
     * Tries to infer if it's a credit card form
     * @returns {boolean}
     */
    isCCForm() {
      if (this._isCCForm !== void 0) return this._isCCForm;
      const formEl = this.form;
      const ccFieldSelector = this.matching.joinCssSelectors("cc");
      if (!ccFieldSelector) {
        this._isCCForm = false;
        return this._isCCForm;
      }
      const hasCCSelectorChild = formEl.matches(ccFieldSelector) || formEl.querySelector(ccFieldSelector);
      if (hasCCSelectorChild) {
        this._isCCForm = true;
        return this._isCCForm;
      }
      const hasCCAttribute = [...formEl.attributes].some(
        ({ name, value }) => safeRegexTest(/(credit|payment).?card/i, `${name}=${value}`)
      );
      if (hasCCAttribute) {
        this._isCCForm = true;
        return this._isCCForm;
      }
      const textMatches = formEl.textContent?.match(/(credit|payment).?card(.?number)?|ccv|security.?code|cvv|cvc|csc/gi);
      const deDupedMatches = new Set(textMatches?.map((match) => match.toLowerCase()));
      this._isCCForm = Boolean(textMatches && deDupedMatches.size > 1);
      return this._isCCForm;
    }
    /**
     * @param {string} text
     * @returns {boolean}
     */
    shouldFlipScoreForButtonText(text) {
      const isForgotPassword = safeRegexTest(this.matching.getDDGMatcherRegex("resetPasswordLink"), text);
      const isSocialButton = /facebook|twitter|google|apple/i.test(text);
      return !isForgotPassword && !isSocialButton;
    }
  };
  var FormAnalyzer_default = FormAnalyzer;

  // src/Form/logo-svg.js
  var daxSvg = `
<svg width="128" height="128" fill="none" viewBox="0 0 128 128" xmlns="http://www.w3.org/2000/svg">
    <path clip-rule="evenodd" d="m64 128c35.346 0 64-28.654 64-64s-28.654-64-64-64-64 28.654-64 64 28.654 64 64 64z" fill="#de5833" fill-rule="evenodd"/>
    <path clip-rule="evenodd" d="m73 111.75c0-.5.123-.614-1.466-3.782-4.224-8.459-8.47-20.384-6.54-28.075.353-1.397-3.978-51.744-7.04-53.365-3.402-1.813-7.588-4.69-11.418-5.33-1.943-.31-4.49-.164-6.482.105-.353.047-.368.683-.03.798 1.308.443 2.895 1.212 3.83 2.375.178.22-.06.566-.342.577-.882.032-2.482.402-4.593 2.195-.244.207-.041.592.273.53 4.536-.897 9.17-.455 11.9 2.027.177.16.084.45-.147.512-23.694 6.44-19.003 27.05-12.696 52.344 5.619 22.53 7.733 29.792 8.4 32.004a.718.718 0 0 0 .423.467c8.156 3.248 25.928 3.392 25.928-2.132z" fill="#ddd" fill-rule="evenodd"/>
    <path d="m76.25 116.5c-2.875 1.125-8.5 1.625-11.75 1.625-4.764 0-11.625-.75-14.125-1.875-1.544-4.751-6.164-19.48-10.727-38.185l-.447-1.827-.004-.015c-5.424-22.157-9.855-40.253 14.427-45.938.222-.052.33-.317.184-.492-2.786-3.305-8.005-4.388-14.605-2.111-.27.093-.506-.18-.337-.412 1.294-1.783 3.823-3.155 5.071-3.756.258-.124.242-.502-.03-.588a27.877 27.877 0 0 0 -3.772-.9c-.37-.059-.403-.693-.032-.743 9.356-1.259 19.125 1.55 24.028 7.726a.326.326 0 0 0 .186.114c17.952 3.856 19.238 32.235 17.17 33.528-.408.255-1.715.108-3.438-.085-6.986-.781-20.818-2.329-9.402 18.948.113.21-.036.488-.272.525-6.438 1 1.812 21.173 7.875 34.461z" fill="#fff"/>
    <path d="m84.28 90.698c-1.367-.633-6.621 3.135-10.11 6.028-.728-1.031-2.103-1.78-5.203-1.242-2.713.472-4.211 1.126-4.88 2.254-4.283-1.623-11.488-4.13-13.229-1.71-1.902 2.646.476 15.161 3.003 16.786 1.32.849 7.63-3.208 10.926-6.005.532.749 1.388 1.178 3.148 1.137 2.662-.062 6.979-.681 7.649-1.921.04-.075.075-.164.105-.266 3.388 1.266 9.35 2.606 10.682 2.406 3.47-.521-.484-16.723-2.09-17.467z" fill="#3ca82b"/>
    <path d="m74.49 97.097c.144.256.26.526.358.8.483 1.352 1.27 5.648.674 6.709-.595 1.062-4.459 1.574-6.843 1.615s-2.92-.831-3.403-2.181c-.387-1.081-.577-3.621-.572-5.075-.098-2.158.69-2.916 4.334-3.506 2.696-.436 4.121.071 4.944.94 3.828-2.857 10.215-6.889 10.838-6.152 3.106 3.674 3.499 12.42 2.826 15.939-.22 1.151-10.505-1.139-10.505-2.38 0-5.152-1.337-6.565-2.65-6.71zm-22.53-1.609c.843-1.333 7.674.325 11.424 1.993 0 0-.77 3.491.456 7.604.359 1.203-8.627 6.558-9.8 5.637-1.355-1.065-3.85-12.432-2.08-15.234z" fill="#4cba3c"/>
    <path clip-rule="evenodd" d="m55.269 68.406c.553-2.403 3.127-6.932 12.321-6.822 4.648-.019 10.422-.002 14.25-.436a51.312 51.312 0 0 0 12.726-3.095c3.98-1.519 5.392-1.18 5.887-.272.544.999-.097 2.722-1.488 4.309-2.656 3.03-7.431 5.38-15.865 6.076-8.433.698-14.02-1.565-16.425 2.118-1.038 1.589-.236 5.333 7.92 6.512 11.02 1.59 20.072-1.917 21.19.201 1.119 2.118-5.323 6.428-16.362 6.518s-17.934-3.865-20.379-5.83c-3.102-2.495-4.49-6.133-3.775-9.279z" fill="#fc3" fill-rule="evenodd"/>
    <g fill="#14307e" opacity=".8">
      <path d="m69.327 42.127c.616-1.008 1.981-1.786 4.216-1.786 2.234 0 3.285.889 4.013 1.88.148.202-.076.44-.306.34a59.869 59.869 0 0 1 -.168-.073c-.817-.357-1.82-.795-3.54-.82-1.838-.026-2.997.435-3.727.831-.246.134-.634-.133-.488-.372zm-25.157 1.29c2.17-.907 3.876-.79 5.081-.504.254.06.43-.213.227-.377-.935-.755-3.03-1.692-5.76-.674-2.437.909-3.585 2.796-3.592 4.038-.002.292.6.317.756.07.42-.67 1.12-1.646 3.289-2.553z"/>
      <path clip-rule="evenodd" d="m75.44 55.92a3.47 3.47 0 0 1 -3.474-3.462 3.47 3.47 0 0 1 3.475-3.46 3.47 3.47 0 0 1 3.474 3.46 3.47 3.47 0 0 1 -3.475 3.462zm2.447-4.608a.899.899 0 0 0 -1.799 0c0 .494.405.895.9.895.499 0 .9-.4.9-.895zm-25.464 3.542a4.042 4.042 0 0 1 -4.049 4.037 4.045 4.045 0 0 1 -4.05-4.037 4.045 4.045 0 0 1 4.05-4.037 4.045 4.045 0 0 1 4.05 4.037zm-1.193-1.338a1.05 1.05 0 0 0 -2.097 0 1.048 1.048 0 0 0 2.097 0z" fill-rule="evenodd"/>
    </g>
    <path clip-rule="evenodd" d="m64 117.75c29.685 0 53.75-24.065 53.75-53.75s-24.065-53.75-53.75-53.75-53.75 24.065-53.75 53.75 24.065 53.75 53.75 53.75zm0 5c32.447 0 58.75-26.303 58.75-58.75s-26.303-58.75-58.75-58.75-58.75 26.303-58.75 58.75 26.303 58.75 58.75 58.75z" fill="#fff" fill-rule="evenodd"/>
</svg>
`.trim();
  var daxBase64 = `data:image/svg+xml;base64,${window.btoa(daxSvg)}`;
  var daxGrayscaleSvg = `
<svg width="128" height="128" viewBox="0 0 128 128" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path fill-rule="evenodd" clip-rule="evenodd" d="M64 128C99.3586 128 128 99.3586 128 64C128 28.6414 99.3586 0 64 0C28.6414 0 0 28.6414 0 64C0 99.3586 28.6414 128 64 128Z" fill="#444444"/>
    <path d="M76.9991 52.2137C77.4966 52.2137 77.9009 51.8094 77.9009 51.3118C77.9009 50.8143 77.4966 50.41 76.9991 50.41C76.5015 50.41 76.0972 50.8143 76.0972 51.3118C76.0972 51.8094 76.5015 52.2137 76.9991 52.2137Z" fill="white"/>
    <path d="M50.1924 54.546C50.7833 54.546 51.2497 54.0796 51.2497 53.4887C51.2497 52.8978 50.7833 52.4314 50.1924 52.4314C49.6015 52.4314 49.1351 52.8978 49.1351 53.4887C49.1351 54.0796 49.6015 54.546 50.1924 54.546Z" fill="white"/>
    <path fill-rule="evenodd" clip-rule="evenodd" d="M122.75 64C122.75 96.4468 96.4467 122.75 64 122.75C31.5533 122.75 5.25 96.4468 5.25 64C5.25 31.5533 31.5533 5.25 64 5.25C96.4467 5.25 122.75 31.5533 122.75 64ZM46.7837 114.934C45.5229 110.558 42.6434 100.26 38.2507 82.659C31.9378 57.3762 27.2419 36.7581 50.9387 30.3208C51.1875 30.2586 51.2808 29.9787 51.0942 29.8232C48.3576 27.3353 43.724 26.9 39.1836 27.8018C38.9659 27.8329 38.8105 27.6774 38.8105 27.4908C38.8105 27.4286 38.8105 27.3664 38.8726 27.3042C39.9611 25.7804 41.9203 24.5987 43.2575 23.8834C42.3245 23.0438 41.0806 22.484 40.0233 22.1109C39.7123 21.9865 39.7123 21.4578 39.9922 21.3334C40.0233 21.3023 40.0544 21.2712 40.1166 21.2712C49.446 20.0273 59.2419 22.8261 64.0622 28.9835C64.1243 29.0457 64.1865 29.0768 64.2487 29.1079C80.0777 32.4976 82.9698 54.9194 82.0058 61.1079C87.5724 60.4549 91.7395 59.0866 94.5072 58.0292C98.4878 56.5054 99.8872 56.8475 100.385 57.7493C100.913 58.7756 100.292 60.486 98.8921 62.072C96.2487 65.0885 91.4596 67.452 83.032 68.1361C80.1189 68.3726 77.544 68.2598 75.3225 68.1625C71.1174 67.9784 68.1791 67.8497 66.6122 70.2508C65.586 71.8368 66.3945 75.5686 74.5422 76.7503C80.3586 77.5883 85.6281 77.0026 89.4701 76.5755C92.8998 76.1943 95.192 75.9395 95.7201 76.9369C96.8396 79.0827 90.4023 83.3742 79.3624 83.4675C78.5228 83.4675 77.6831 83.4364 76.8746 83.4053C70.033 83.0633 64.9951 81.1974 61.8542 79.487C61.7609 79.4559 61.6987 79.4248 61.6676 79.3937C61.1078 79.0827 60.0194 79.6424 60.6725 80.8242C61.0456 81.5394 63.0359 83.3742 66.0213 84.9602C65.7104 87.4481 66.4878 91.2732 67.825 95.6269C67.9804 95.601 68.1357 95.5697 68.2955 95.5376C68.5196 95.4924 68.7526 95.4455 69.0068 95.4092C71.7123 94.9738 73.1428 95.4714 73.9514 96.3422C77.7764 93.4811 84.1516 89.4384 84.7735 90.1847C87.8833 93.8854 88.2876 102.624 87.6035 106.138C87.5724 106.2 87.5102 106.262 87.3858 106.325C85.9242 106.947 77.8698 104.746 77.8698 103.596C77.5588 97.866 76.4937 97.3373 75.2498 97.0574H74.4178C74.4489 97.0885 74.48 97.1507 74.5111 97.2129L74.791 97.866C75.2886 99.2343 76.066 103.526 75.4752 104.583C74.8843 105.641 71.0281 106.169 68.6336 106.2C66.2701 106.231 65.7415 105.361 65.2439 104.023C64.8707 102.935 64.6841 100.416 64.6841 98.9544C64.653 98.519 64.6841 98.1459 64.7463 97.8038C64.0311 98.1148 62.9816 98.83 62.6395 99.2964C62.5462 100.696 62.5462 102.935 63.2925 105.423C63.6657 106.605 55.1992 111.642 54.0174 110.71C52.8046 109.745 50.6278 100.292 51.5607 96.4666C50.5656 96.5599 49.757 96.8708 49.3216 97.4928C47.3624 100.198 49.8192 113.135 52.4314 114.814C53.7998 115.716 60.6414 111.86 64.0311 108.968C64.5908 109.745 65.6638 109.808 66.9854 109.808C68.7269 109.745 71.1525 109.497 72.8629 108.968C73.8867 111.367 75.1219 114.157 76.1353 116.374C99.9759 110.873 117.75 89.5121 117.75 64C117.75 34.3147 93.6853 10.25 64 10.25C34.3147 10.25 10.25 34.3147 10.25 64C10.25 87.664 25.5423 107.756 46.7837 114.934ZM77.1275 42.5198C77.168 42.5379 77.2081 42.5558 77.2478 42.5734C77.4655 42.6667 77.7142 42.418 77.5587 42.2314C76.8435 41.2673 75.7862 40.3655 73.5471 40.3655C71.308 40.3655 69.9397 41.1429 69.3177 42.1381C69.1933 42.3869 69.5665 42.6356 69.8153 42.5112C70.5617 42.107 71.7123 41.6405 73.5471 41.6716C75.2952 41.7012 76.3094 42.1543 77.1275 42.5198ZM75.4441 55.9146C77.3722 55.9146 78.9271 54.3596 78.9271 52.4627C78.9271 50.5346 77.3722 49.0108 75.4441 49.0108C73.516 49.0108 71.9611 50.5657 71.9611 52.4627C71.9611 54.3596 73.516 55.9146 75.4441 55.9146ZM52.4314 54.8572C52.4314 52.6181 50.6278 50.8145 48.3887 50.8145C46.1496 50.8145 44.3148 52.6181 44.3459 54.8572C44.3459 57.0963 46.1496 58.9 48.3887 58.9C50.6278 58.9 52.4314 57.0963 52.4314 54.8572ZM40.8629 45.9631C41.2983 45.3101 41.9825 44.3149 44.1593 43.4131C46.3362 42.5112 48.0466 42.6356 49.2283 42.9155C49.4771 42.9777 49.6637 42.6978 49.446 42.5423C48.5131 41.7649 46.4295 40.8319 43.6929 41.8582C41.2672 42.76 40.1166 44.657 40.1166 45.9009C40.1166 46.1808 40.7074 46.2119 40.8629 45.9631Z" fill="white"/>
</svg>
`.trim();
  var daxGrayscaleBase64 = `data:image/svg+xml;base64,${window.btoa(daxGrayscaleSvg)}`;

  // src/UI/img/ddgPasswordIcon.js
  var key = "data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiB2aWV3Qm94PSIwIDAgMjQgMjQiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZmlsbD0iIzAwMCIgZmlsbC1vcGFjaXR5PSIuOSIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTUuNSA2YTIuNSAyLjUgMCAxIDEgMCA1IDIuNSAyLjUgMCAwIDEgMC01bTAgMS41YTEgMSAwIDEgMCAwIDIgMSAxIDAgMCAwIDAtMiIgY2xpcC1ydWxlPSJldmVub2RkIi8+CiAgPHBhdGggZmlsbD0iIzAwMCIgZmlsbC1vcGFjaXR5PSIuOSIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTQuOTk4IDJBNy4wMDUgNy4wMDUgMCAwIDEgMjIgOS4wMDdhNy4wMDQgNy4wMDQgMCAwIDEtOC43MDUgNi43OTdjLS4xNjMtLjA0MS0uMjg2LjAwOC0uMzQ1LjA2N2wtMi41NTcgMi41NTlhMiAyIDAgMCAxLTEuNDE1LjU4NmgtLjk4MnYuNzM0QTIuMjUgMi4yNSAwIDAgMSA1Ljc0NSAyMmgtLjk5M2EyLjc1IDIuNzUgMCAwIDEtMi43NS0yLjczNUwyIDE4Ljc3YTMuNzUgMy43NSAwIDAgMSAxLjA5OC0yLjY3bDUuMDQtNS4wNDNjLjA2LS4wNi4xMDctLjE4My4wNjYtLjM0NmE3IDcgMCAwIDEtLjIwOC0xLjcwNEE3LjAwNCA3LjAwNCAwIDAgMSAxNC45OTggMm0wIDEuNWE1LjUwNCA1LjUwNCAwIDAgMC01LjMzNyA2Ljg0OGMuMTQ3LjU4OS4wMjcgMS4yNzktLjQ2MiAxLjc2OGwtNS4wNCA1LjA0NGEyLjI1IDIuMjUgMCAwIDAtLjY1OSAxLjYwM2wuMDAzLjQ5NGExLjI1IDEuMjUgMCAwIDAgMS4yNSAxLjI0M2guOTkyYS43NS43NSAwIDAgMCAuNzUtLjc1di0uNzM0YTEuNSAxLjUgMCAwIDEgMS41LTEuNWguOTgzYS41LjUgMCAwIDAgLjM1My0uMTQ3bDIuNTU4LTIuNTU5Yy40OS0uNDkgMS4xOC0uNjA5IDEuNzctLjQ2MWE1LjUwNCA1LjUwNCAwIDAgMCA2Ljg0LTUuMzQyQTUuNTA1IDUuNTA1IDAgMCAwIDE1IDMuNVoiIGNsaXAtcnVsZT0iZXZlbm9kZCIvPgo8L3N2Zz4=";
  var keyFilled = "data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiB2aWV3Qm94PSIwIDAgMjQgMjQiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZmlsbD0iIzc2NDMxMCIgZmlsbC1vcGFjaXR5PSIuOSIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTUuNSA2YTIuNSAyLjUgMCAxIDEgMCA1IDIuNSAyLjUgMCAwIDEgMC01bTAgMS41YTEgMSAwIDEgMCAwIDIgMSAxIDAgMCAwIDAtMiIgY2xpcC1ydWxlPSJldmVub2RkIi8+CiAgPHBhdGggZmlsbD0iIzc2NDMxMCIgZmlsbC1vcGFjaXR5PSIuOSIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTQuOTk4IDJBNy4wMDUgNy4wMDUgMCAwIDEgMjIgOS4wMDdhNy4wMDQgNy4wMDQgMCAwIDEtOC43MDUgNi43OTdjLS4xNjMtLjA0MS0uMjg2LjAwOC0uMzQ1LjA2N2wtMi41NTcgMi41NTlhMiAyIDAgMCAxLTEuNDE1LjU4NmgtLjk4MnYuNzM0QTIuMjUgMi4yNSAwIDAgMSA1Ljc0NSAyMmgtLjk5M2EyLjc1IDIuNzUgMCAwIDEtMi43NS0yLjczNUwyIDE4Ljc3YTMuNzUgMy43NSAwIDAgMSAxLjA5OC0yLjY3bDUuMDQtNS4wNDNjLjA2LS4wNi4xMDctLjE4My4wNjYtLjM0NmE3IDcgMCAwIDEtLjIwOC0xLjcwNEE3LjAwNCA3LjAwNCAwIDAgMSAxNC45OTggMm0wIDEuNWE1LjUwNCA1LjUwNCAwIDAgMC01LjMzNyA2Ljg0OGMuMTQ3LjU4OS4wMjcgMS4yNzktLjQ2MiAxLjc2OGwtNS4wNCA1LjA0NGEyLjI1IDIuMjUgMCAwIDAtLjY1OSAxLjYwM2wuMDAzLjQ5NGExLjI1IDEuMjUgMCAwIDAgMS4yNSAxLjI0M2guOTkyYS43NS43NSAwIDAgMCAuNzUtLjc1di0uNzM0YTEuNSAxLjUgMCAwIDEgMS41LTEuNWguOTgzYS41LjUgMCAwIDAgLjM1My0uMTQ3bDIuNTU4LTIuNTU5Yy40OS0uNDkgMS4xOC0uNjA5IDEuNzctLjQ2MWE1LjUwNCA1LjUwNCAwIDAgMCA2Ljg0LTUuMzQyQTUuNTA1IDUuNTA1IDAgMCAwIDE1IDMuNVoiIGNsaXAtcnVsZT0iZXZlbm9kZCIvPgo8L3N2Zz4K";
  var keyLogin = "data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiB2aWV3Qm94PSIwIDAgMjQgMjQiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZmlsbD0iIzAwMCIgZD0iTTExLjIyNCA0LjY0YS45LjkgMCAwIDAgLjY0Ny0uMTY1IDUuNDcgNS40NyAwIDAgMSAzLjEyNy0uOTc1QTUuNTA0IDUuNTA0IDAgMCAxIDIwLjUgOS4wMDZhNS41MDQgNS41MDQgMCAwIDEtNi44NCA1LjM0M2MtLjU5LS4xNDgtMS4yODEtLjAyOC0xLjc3MS40NjJsLTIuNTU3IDIuNTU4YS41LjUgMCAwIDEtLjM1NC4xNDdoLS45ODJhMS41IDEuNSAwIDAgMC0xLjUgMS41di43MzRhLjc1Ljc1IDAgMCAxLS43NS43NWgtLjk5M2ExLjI1IDEuMjUgMCAwIDEtMS4yNS0xLjI0NGwtLjAwMy0uNDk0YTIuMjUgMi4yNSAwIDAgMSAuNjU5LTEuNjAybDUuMDQtNS4wNDNjLjM0My0uMzQ0LjQ2MS0uNzExLjQ3OS0xLjA5NS4wMjctLjU4Mi0uNzM3LS44NDctMS4xNzktLjQ2N2wtLjA2Ni4wNTZhLjcuNyAwIDAgMC0uMTU4LjIzMi44LjggMCAwIDEtLjEzNy4yMTNMMy4wOTggMTYuMUEzLjc1IDMuNzUgMCAwIDAgMiAxOC43N2wuMDAzLjQ5NEEyLjc1IDIuNzUgMCAwIDAgNC43NTMgMjJoLjk5MmEyLjI1IDIuMjUgMCAwIDAgMi4yNS0yLjI1di0uNzM0aC45ODNhMiAyIDAgMCAwIDEuNDE1LS41ODZsMi41NTctMi41NTljLjA1OS0uMDU5LjE4Mi0uMTA4LjM0Ni0uMDY3QTcuMDA0IDcuMDA0IDAgMCAwIDIyIDkuMDA2IDcuMDA0IDcuMDA0IDAgMCAwIDEwLjgyNiAzLjM4Yy0uNTMzLjM5NS0uMjYgMS4xNjYuMzk3IDEuMjZaIi8+CiAgPHBhdGggZmlsbD0iIzAwMCIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTUuNSA2YTIuNSAyLjUgMCAxIDEgMCA1IDIuNSAyLjUgMCAwIDEgMC01bTAgMS41YTEgMSAwIDEgMCAwIDIgMSAxIDAgMCAwIDAtMiIgY2xpcC1ydWxlPSJldmVub2RkIi8+CiAgPHBhdGggZmlsbD0iIzAwMCIgZD0iTTcuMTI1IDIuODA0QzcgMi4xNiA2LjkxNSAyIDYuNSAyYy0uNDE0IDAtLjUuMTYtLjYyNS44MDQtLjA4LjQxMy0uMjEyIDEuODItLjI5NiAyLjc3NS0uOTU0LjA4NC0yLjM2Mi4yMTYtMi43NzUuMjk2QzIuMTYgNiAyIDYuMDg1IDIgNi41YzAgLjQxNC4xNjEuNS44MDQuNjI1LjQxMi4wOCAxLjgxOC4yMTIgMi43NzIuMjk2LjA4My45ODkuMjE4IDIuNDYxLjMgMi43NzUuMTI0LjQ4My4yMS44MDQuNjI0LjgwNHMuNS0uMTYuNjI1LS44MDRjLjA4LS40MTIuMjEyLTEuODE3LjI5Ni0yLjc3MS45OS0uMDg0IDIuNDYyLS4yMTkgMi43NzYtLjNDMTAuNjc5IDcgMTEgNi45MTUgMTEgNi41YzAtLjQxNC0uMTYtLjUtLjgwMy0uNjI1LS40MTMtLjA4LTEuODIxLS4yMTItMi43NzUtLjI5Ni0uMDg1LS45NTQtLjIxNi0yLjM2Mi0uMjk3LTIuNzc1bS00LjM0MiA4Ljc2MWEuNzgzLjc4MyAwIDEgMCAwLTEuNTY1Ljc4My43ODMgMCAwIDAgMCAxLjU2NSIvPgo8L3N2Zz4K";
  var keyLoginFilled = "data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiB2aWV3Qm94PSIwIDAgMjQgMjQiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZmlsbD0iIzc2NDMxMCIgZD0iTTExLjIyNCA0LjY0YS45LjkgMCAwIDAgLjY0Ny0uMTY1IDUuNDcgNS40NyAwIDAgMSAzLjEyNy0uOTc1QTUuNTA0IDUuNTA0IDAgMCAxIDIwLjUgOS4wMDZhNS41MDQgNS41MDQgMCAwIDEtNi44NCA1LjM0M2MtLjU5LS4xNDgtMS4yODEtLjAyOC0xLjc3MS40NjJsLTIuNTU3IDIuNTU4YS41LjUgMCAwIDEtLjM1NC4xNDdoLS45ODJhMS41IDEuNSAwIDAgMC0xLjUgMS41di43MzRhLjc1Ljc1IDAgMCAxLS43NS43NWgtLjk5M2ExLjI1IDEuMjUgMCAwIDEtMS4yNS0xLjI0NGwtLjAwMy0uNDk0YTIuMjUgMi4yNSAwIDAgMSAuNjU5LTEuNjAybDUuMDQtNS4wNDNjLjM0My0uMzQ0LjQ2MS0uNzExLjQ3OS0xLjA5NS4wMjctLjU4Mi0uNzM3LS44NDctMS4xNzktLjQ2N2wtLjA2Ni4wNTZhLjcuNyAwIDAgMC0uMTU4LjIzMi44LjggMCAwIDEtLjEzNy4yMTNMMy4wOTggMTYuMUEzLjc1IDMuNzUgMCAwIDAgMiAxOC43N2wuMDAzLjQ5NEEyLjc1IDIuNzUgMCAwIDAgNC43NTMgMjJoLjk5MmEyLjI1IDIuMjUgMCAwIDAgMi4yNS0yLjI1di0uNzM0aC45ODNhMiAyIDAgMCAwIDEuNDE1LS41ODZsMi41NTctMi41NTljLjA1OS0uMDU5LjE4Mi0uMTA4LjM0Ni0uMDY3QTcuMDA0IDcuMDA0IDAgMCAwIDIyIDkuMDA2IDcuMDA0IDcuMDA0IDAgMCAwIDEwLjgyNiAzLjM4Yy0uNTMzLjM5NS0uMjYgMS4xNjYuMzk3IDEuMjZaIi8+CiAgPHBhdGggZmlsbD0iIzc2NDMxMCIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTUuNSA2YTIuNSAyLjUgMCAxIDEgMCA1IDIuNSAyLjUgMCAwIDEgMC01bTAgMS41YTEgMSAwIDEgMCAwIDIgMSAxIDAgMCAwIDAtMiIgY2xpcC1ydWxlPSJldmVub2RkIi8+CiAgPHBhdGggZmlsbD0iIzc2NDMxMCIgZD0iTTcuMTI1IDIuODA0QzcgMi4xNiA2LjkxNSAyIDYuNSAyYy0uNDE0IDAtLjUuMTYtLjYyNS44MDQtLjA4LjQxMy0uMjEyIDEuODItLjI5NiAyLjc3NS0uOTU0LjA4NC0yLjM2Mi4yMTYtMi43NzUuMjk2QzIuMTYgNiAyIDYuMDg1IDIgNi41YzAgLjQxNC4xNjEuNS44MDQuNjI1LjQxMi4wOCAxLjgxOC4yMTIgMi43NzIuMjk2LjA4My45ODkuMjE4IDIuNDYxLjMgMi43NzUuMTI0LjQ4My4yMS44MDQuNjI0LjgwNHMuNS0uMTYuNjI1LS44MDRjLjA4LS40MTIuMjEyLTEuODE3LjI5Ni0yLjc3MS45OS0uMDg0IDIuNDYyLS4yMTkgMi43NzYtLjNDMTAuNjc5IDcgMTEgNi45MTUgMTEgNi41YzAtLjQxNC0uMTYtLjUtLjgwMy0uNjI1LS40MTMtLjA4LTEuODIxLS4yMTItMi43NzUtLjI5Ni0uMDg1LS45NTQtLjIxNi0yLjM2Mi0uMjk3LTIuNzc1bS00LjM0MiA4Ljc2MWEuNzgzLjc4MyAwIDEgMCAwLTEuNTY1Ljc4My43ODMgMCAwIDAgMCAxLjU2NSIvPgo8L3N2Zz4K";
  var ddgPasswordIconBase = key;
  var ddgPasswordIconFilled = keyFilled;
  var ddgPasswordGenIconBase = keyLogin;
  var ddgPasswordGenIconFilled = keyLoginFilled;

  // src/InputTypes/CreditCard.js
  var _data4;
  var CreditCardTooltipItem = class {
    /** @param {CreditCardObject} data */
    constructor(data) {
      /** @type {CreditCardObject} */
      __privateAdd(this, _data4);
      __publicField(this, "id", () => String(__privateGet(this, _data4).id));
      __publicField(this, "labelMedium", () => __privateGet(this, _data4).title);
      /** @param {import('../locales/strings.js').TranslateFn} t */
      __publicField(this, "labelSmall", (t) => {
        const { displayNumber, expirationMonth, expirationYear } = __privateGet(this, _data4);
        const expiration = expirationMonth && expirationYear ? `\xA0 ${t("autofill:expiry")}: ${String(expirationMonth).padStart(2, "0")}/${expirationYear}` : "";
        return `\u2022\u2022\u2022\u2022 ${displayNumber}${expiration}`;
      });
      __publicField(this, "paymentProvider", () => __privateGet(this, _data4).paymentProvider || "");
      __privateSet(this, _data4, data);
    }
  };
  _data4 = new WeakMap();

  // src/InputTypes/Identity.js
  var _data5;
  var IdentityTooltipItem = class {
    /** @param {IdentityObject} data */
    constructor(data) {
      /** @type {IdentityObject} */
      __privateAdd(this, _data5);
      __publicField(this, "id", () => String(__privateGet(this, _data5).id));
      /**
       * @param {import('../locales/strings.js').TranslateFn} t
       * @param {string} subtype
       */
      __publicField(this, "labelMedium", (t, subtype) => {
        if (subtype === "addressCountryCode") {
          return getCountryDisplayName("en", __privateGet(this, _data5).addressCountryCode || "");
        }
        if (__privateGet(this, _data5).id === "privateAddress") {
          return t("autofill:generatePrivateDuckAddr");
        }
        return __privateGet(this, _data5)[subtype];
      });
      __publicField(this, "labelSmall", (_) => {
        return __privateGet(this, _data5).title;
      });
      __privateSet(this, _data5, data);
    }
    label(_t, subtype) {
      if (__privateGet(this, _data5).id === "privateAddress") {
        return __privateGet(this, _data5)[subtype];
      }
      return null;
    }
  };
  _data5 = new WeakMap();

  // src/Form/inputTypeConfig.js
  var getIdentitiesIcon = (input, { device }) => {
    if (!canBeInteractedWith(input)) return "";
    const { isDDGApp, isFirefox, isExtension } = device.globalConfig;
    const subtype = getInputSubtype(input);
    if (device.inContextSignup?.isAvailable(subtype)) {
      if (isDDGApp || isFirefox) {
        return daxGrayscaleBase64;
      } else if (isExtension) {
        return chrome.runtime.getURL("img/logo-small-grayscale.svg");
      }
    }
    if (subtype === "emailAddress" && device.isDeviceSignedIn()) {
      if (isDDGApp || isFirefox) {
        return daxBase64;
      } else if (isExtension) {
        return chrome.runtime.getURL("img/logo-small.svg");
      }
    }
    return "";
  };
  var getIdentitiesAlternateIcon = (input, { device }) => {
    if (!canBeInteractedWith(input)) return "";
    const { isDDGApp, isFirefox, isExtension } = device.globalConfig;
    const subtype = getInputSubtype(input);
    const isIncontext = device.inContextSignup?.isAvailable(subtype);
    const isEmailProtection = subtype === "emailAddress" && device.isDeviceSignedIn();
    if (isIncontext || isEmailProtection) {
      if (isDDGApp || isFirefox) {
        return daxBase64;
      } else if (isExtension) {
        return chrome.runtime.getURL("img/logo-small.svg");
      }
    }
    return "";
  };
  var canBeInteractedWith = (input) => !input.readOnly && !input.disabled;
  var canBeAutofilled = async (input, device) => {
    const mainType = getInputMainType(input);
    if (mainType === "unknown") return false;
    const subtype = getInputSubtype(input);
    const variant = getInputVariant(input);
    await device.settings.populateDataIfNeeded({ mainType, subtype });
    const canAutofill = device.settings.canAutofillType({ mainType, subtype, variant }, device.inContextSignup);
    return Boolean(canAutofill);
  };
  var inputTypeConfig = {
    /** @type {CredentialsInputTypeConfig} */
    credentials: {
      type: "credentials",
      displayName: "passwords",
      getIconBase: (input, form) => {
        const { device } = form;
        if (!canBeInteractedWith(input)) return "";
        if (device.credentialsImport?.isAvailable() && (form?.isLogin || form?.isHybrid)) return "";
        if (device.settings.featureToggles.inlineIcon_credentials) {
          const subtype = getInputSubtype(input);
          const variant = getInputVariant(input);
          if (subtype === "password" && variant === "new") {
            return ddgPasswordGenIconBase;
          }
          return ddgPasswordIconBase;
        }
        return "";
      },
      getIconFilled: (input, { device }) => {
        if (device.settings.featureToggles.inlineIcon_credentials) {
          const subtype = getInputSubtype(input);
          const variant = getInputVariant(input);
          if (subtype === "password" && variant === "new") {
            return ddgPasswordGenIconFilled;
          }
          return ddgPasswordIconFilled;
        }
        return "";
      },
      getIconAlternate: () => "",
      shouldDecorate: async (input, { isLogin, isHybrid, device, isCredentialsImportAvailable }) => {
        const subtype = getInputSubtype(input);
        const variant = getInputVariant(input);
        if (subtype === "password" && variant === "new" || // New passord field
        isLogin || isHybrid || variant === "current") {
          return isCredentialsImportAvailable || canBeAutofilled(input, device);
        }
        return false;
      },
      dataType: "Credentials",
      tooltipItem: (data) => createCredentialsTooltipItem(data)
    },
    /** @type {CreditCardsInputTypeConfig} */
    creditCards: {
      type: "creditCards",
      displayName: "credit cards",
      getIconBase: () => "",
      getIconFilled: () => "",
      getIconAlternate: () => "",
      shouldDecorate: async (input, { device }) => {
        return canBeAutofilled(input, device);
      },
      dataType: "CreditCards",
      tooltipItem: (data) => new CreditCardTooltipItem(data)
    },
    /** @type {IdentitiesInputTypeConfig} */
    identities: {
      type: "identities",
      displayName: "identities",
      getIconBase: getIdentitiesIcon,
      getIconFilled: getIdentitiesIcon,
      getIconAlternate: getIdentitiesAlternateIcon,
      shouldDecorate: async (input, { device }) => {
        return canBeAutofilled(input, device);
      },
      dataType: "Identities",
      tooltipItem: (data) => new IdentityTooltipItem(data)
    },
    /** @type {UnknownInputTypeConfig} */
    unknown: {
      type: "unknown",
      displayName: "",
      getIconBase: () => "",
      getIconFilled: () => "",
      getIconAlternate: () => "",
      shouldDecorate: async () => false,
      dataType: "",
      tooltipItem: (_data7) => {
        throw new Error("unreachable - setting tooltip to unknown field type");
      }
    }
  };
  var getInputConfig = (input) => {
    const inputType = getInputType(input);
    return getInputConfigFromType(inputType);
  };
  var getInputConfigFromType = (inputType) => {
    const inputMainType = getMainTypeFromType(inputType);
    return inputTypeConfig[inputMainType];
  };
  var isFieldDecorated = (input) => {
    return input.hasAttribute(constants.ATTR_INPUT_TYPE);
  };

  // src/Form/inputStyles.js
  var getIcon = (input, form, type = "base") => {
    const config = getInputConfig(input);
    if (type === "base") {
      return config.getIconBase(input, form);
    }
    if (type === "filled") {
      return config.getIconFilled(input, form);
    }
    if (type === "alternate") {
      return config.getIconAlternate(input, form);
    }
    return "";
  };
  var getBasicStyles = (input, icon) => ({
    // Height must be > 0 to account for fields initially hidden
    "background-size": `auto ${input.offsetHeight <= 30 && input.offsetHeight > 0 ? "100%" : "20px"}`,
    "background-position": "center right",
    "background-repeat": "no-repeat",
    "background-origin": "content-box",
    "background-image": `url(${icon})`,
    transition: "background 0s"
  });
  var getIconStylesBase = (input, form) => {
    const icon = getIcon(input, form);
    if (!icon) return {};
    return getBasicStyles(input, icon);
  };
  var getIconStylesAlternate = (input, form) => {
    const icon = getIcon(input, form, "alternate");
    if (!icon) return {};
    return {
      ...getBasicStyles(input, icon)
    };
  };
  var getIconStylesAutofilled = (input, form) => {
    const icon = getIcon(input, form, "filled");
    const iconStyle = icon ? getBasicStyles(input, icon) : {};
    return {
      ...iconStyle,
      "background-color": "#F8F498",
      color: "#333333"
    };
  };

  // src/Form/Form.js
  var { ATTR_AUTOFILL, ATTR_INPUT_TYPE: ATTR_INPUT_TYPE2, MAX_INPUTS_PER_FORM, MAX_FORM_RESCANS } = constants;
  var Form = class {
    /**
     * @param {HTMLElement} form
     * @param {HTMLInputElement|HTMLSelectElement} input
     * @param {import("../DeviceInterface/InterfacePrototype").default} deviceInterface
     * @param {import("../Form/matching").Matching} [matching]
     * @param {Boolean} [shouldAutoprompt]
     * @param {Boolean} [hasShadowTree]
     */
    constructor(form, input, deviceInterface, matching, shouldAutoprompt = false, hasShadowTree = false) {
      /** @type {import("../Form/matching").Matching} */
      __publicField(this, "matching");
      /** @type {HTMLElement} */
      __publicField(this, "form");
      /** @type {HTMLInputElement | null} */
      __publicField(this, "activeInput");
      this.form = form;
      this.matching = matching || createMatching();
      this.formAnalyzer = new FormAnalyzer_default(form, deviceInterface.settings.siteSpecificFeature, input, matching);
      this.device = deviceInterface;
      this.hasShadowTree = hasShadowTree;
      this.inputs = {
        all: /* @__PURE__ */ new Set(),
        credentials: /* @__PURE__ */ new Set(),
        creditCards: /* @__PURE__ */ new Set(),
        identities: /* @__PURE__ */ new Set(),
        unknown: /* @__PURE__ */ new Set()
      };
      this.touched = /* @__PURE__ */ new Set();
      this.listeners = /* @__PURE__ */ new Set();
      this.activeInput = null;
      this.isAutofilling = false;
      this.submitHandlerExecuted = false;
      this.shouldPromptToStoreData = deviceInterface.settings.featureToggles.credentials_saving;
      this.shouldAutoSubmit = this.device.globalConfig.isMobileApp;
      this.intObs = new IntersectionObserver((entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) this.removeTooltip();
        }
      });
      this.rescanCount = 0;
      this.mutObsConfig = { childList: true, subtree: true };
      this.mutObs = new MutationObserver((records) => {
        const anythingRemoved = records.some((record) => record.removedNodes.length > 0);
        if (anythingRemoved) {
          if (!this.form.isConnected) {
            this.destroy();
            return;
          }
          if ([...this.inputs.all].some((input2) => !input2.isConnected)) {
            this.mutObs.disconnect();
            window.requestIdleCallback(() => {
              this.formAnalyzer = new FormAnalyzer_default(this.form, this.device.settings.siteSpecificFeature, input, this.matching);
              this.recategorizeAllInputs();
            });
          }
        }
      });
      this.initFormListeners();
      this.categorizeInputs();
      this.logFormInfo();
      if (shouldAutoprompt) {
        this.promptLoginIfNeeded();
      }
    }
    get isLogin() {
      return this.formAnalyzer.isLogin;
    }
    get isSignup() {
      return this.formAnalyzer.isSignup;
    }
    get isHybrid() {
      return this.formAnalyzer.isHybrid;
    }
    get isCCForm() {
      return this.formAnalyzer.isCCForm();
    }
    logFormInfo() {
      if (!shouldLog()) return;
      console.log(`Form type: %c${this.getFormType()}`, "font-weight: bold");
      console.log("Signals: ", this.formAnalyzer.signals);
      console.log("Wrapping element: ", this.form);
      console.log("Inputs: ", this.inputs);
      console.log("Submit Buttons: ", this.submitButtons);
    }
    getFormType() {
      if (this.isHybrid) return `hybrid (hybrid score: ${this.formAnalyzer.hybridSignal}, score: ${this.formAnalyzer.autofillSignal})`;
      if (this.isLogin) return `login (score: ${this.formAnalyzer.autofillSignal}, hybrid score: ${this.formAnalyzer.hybridSignal})`;
      if (this.isSignup) return `signup (score: ${this.formAnalyzer.autofillSignal}, hybrid score: ${this.formAnalyzer.hybridSignal})`;
      return "something went wrong";
    }
    /**
     * Checks if the form element contains the activeElement or the event target
     * @return {boolean}
     * @param {KeyboardEvent | null} [e]
     */
    hasFocus(e) {
      return this.form.contains(getActiveElement()) || this.form.contains(
        /** @type HTMLElement */
        e?.target
      );
    }
    submitHandler(via = "unknown") {
      if (this.device.globalConfig.isDDGTestMode) {
        console.log("Form.submitHandler via:", via, this);
      }
      if (this.submitHandlerExecuted) return;
      const values = this.getValuesReadyForStorage();
      this.device.postSubmit?.(values, this);
      this.submitHandlerExecuted = true;
    }
    /**
     * Reads the values from the form without preparing to store them
     * @return {InternalDataStorageObject}
     */
    getRawValues() {
      const formValues = [...this.inputs.credentials, ...this.inputs.identities, ...this.inputs.creditCards].reduce(
        (output, inputEl) => {
          const mainType = getInputMainType(inputEl);
          const subtype = getInputSubtype(inputEl);
          let value = inputEl.value || output[mainType]?.[subtype];
          if (subtype === "addressCountryCode") {
            value = inferCountryCodeFromElement(inputEl);
          }
          if (subtype === "password" && value?.length <= 3) {
            value = void 0;
          }
          if (value) {
            output[mainType][subtype] = value;
          }
          return output;
        },
        { credentials: {}, creditCards: {}, identities: {} }
      );
      if (!formValues.credentials.username && !formValues.identities.emailAddress) {
        const hiddenFields = (
          /** @type [HTMLInputElement] */
          [...this.form.querySelectorAll("input[type=hidden]")]
        );
        const probableField = hiddenFields.find((field) => {
          const regex = new RegExp("email|" + this.matching.getDDGMatcherRegex("username")?.source);
          const attributeText = field.id + " " + field.name;
          return safeRegexTest(regex, attributeText);
        });
        if (probableField?.value) {
          formValues.credentials.username = probableField.value;
        } else if (
          // If a form has phone + password(s) fields, save the phone as username
          formValues.identities.phone && this.inputs.all.size - this.inputs.unknown.size < 4
        ) {
          formValues.credentials.username = formValues.identities.phone;
        } else {
          this.form.querySelectorAll(this.matching.cssSelector("safeUniversalSelector")).forEach((el) => {
            const elText = getTextShallow(el);
            if (elText.length > 70) return;
            const emailOrUsername = elText.match(
              // https://www.emailregex.com/
              /[a-zA-Z\d.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z\d-]+(?:\.[a-zA-Z\d-]+)*/
            )?.[0];
            if (emailOrUsername) {
              formValues.credentials.username = emailOrUsername;
            }
          });
        }
      }
      return formValues;
    }
    /**
     * Return form values ready for storage
     * @returns {DataStorageObject}
     */
    getValuesReadyForStorage() {
      const formValues = this.getRawValues();
      return prepareFormValuesForStorage(formValues, this.device.settings.featureToggles.partial_form_saves);
    }
    /**
     * Determine if the form has values we want to store in the device
     * @param {DataStorageObject} [values]
     * @return {boolean}
     */
    hasValues(values) {
      const { credentials, creditCards, identities } = values || this.getValuesReadyForStorage();
      return Boolean(credentials || creditCards || identities);
    }
    async removeTooltip() {
      const tooltip = this.device.isTooltipActive();
      if (this.isAutofilling || !tooltip) {
        return;
      }
      await this.device.removeTooltip();
      this.intObs?.disconnect();
    }
    showingTooltip(input) {
      this.intObs?.observe(input);
    }
    removeInputHighlight(input) {
      if (!input.classList.contains("ddg-autofilled")) return;
      removeInlineStyles(input, getIconStylesAutofilled(input, this));
      removeInlineStyles(input, { cursor: "pointer" });
      input.classList.remove("ddg-autofilled");
      this.addAutofillStyles(input);
    }
    resetIconStylesToInitial() {
      const input = this.activeInput;
      if (input) {
        const initialStyles = getIconStylesBase(input, this);
        addInlineStyles(input, initialStyles);
      }
    }
    removeAllHighlights(e, dataType) {
      if (e && !e.isTrusted) return;
      this.resetShouldPromptToStoreData();
      this.execOnInputs((input) => this.removeInputHighlight(input), dataType);
    }
    removeInputDecoration(input) {
      removeInlineStyles(input, getIconStylesBase(input, this));
      removeInlineStyles(input, getIconStylesAlternate(input, this));
      input.removeAttribute(ATTR_AUTOFILL);
      input.removeAttribute(ATTR_INPUT_TYPE2);
    }
    removeAllDecorations() {
      this.execOnInputs((input) => this.removeInputDecoration(input));
      this.listeners.forEach(({ el, type, fn, opts }) => el.removeEventListener(type, fn, opts));
    }
    redecorateAllInputs() {
      this.execOnInputs((input) => {
        if (input instanceof HTMLInputElement) {
          this.decorateInput(input);
        }
      });
    }
    /**
     * Removes all scoring attributes from the inputs and deletes them from memory
     */
    forgetAllInputs() {
      this.execOnInputs((input) => {
        input.removeAttribute(ATTR_AUTOFILL);
        input.removeAttribute(ATTR_INPUT_TYPE2);
      });
      Object.values(this.inputs).forEach((inputSet) => inputSet.clear());
    }
    /**
     * Resets our input scoring and starts from scratch
     */
    recategorizeAllInputs() {
      if (this.rescanCount >= MAX_FORM_RESCANS) {
        this.mutObs.disconnect();
        return;
      }
      this.rescanCount++;
      this.initialScanComplete = false;
      this.removeAllDecorations();
      this.forgetAllInputs();
      this.initFormListeners();
      this.categorizeInputs();
    }
    resetAllInputs() {
      this.execOnInputs((input) => {
        setValue(input, "", this.device.globalConfig);
        this.removeInputHighlight(input);
      });
      if (this.activeInput) this.activeInput.focus();
      this.matching.clear();
    }
    resetShouldPromptToStoreData() {
      this.shouldPromptToStoreData = this.device.settings.featureToggles.credentials_saving;
    }
    dismissTooltip() {
      this.removeTooltip();
    }
    // This removes all listeners to avoid memory leaks and weird behaviours
    destroy() {
      this.mutObs.disconnect();
      this.removeAllDecorations();
      this.removeTooltip();
      this.forgetAllInputs();
      this.matching.clear();
      this.intObs = null;
      this.device.scanner.forms.delete(this.form);
    }
    initFormListeners() {
      this.addListener(this.form, "input", (e) => {
        if (!this.isAutofilling) {
          this.submitHandlerExecuted = false;
          const inputType = e.target.getAttribute(ATTR_INPUT_TYPE2);
          if (inputType && inputType !== "unknown") {
            this.resetShouldPromptToStoreData();
          } else {
            this.shouldPromptToStoreData = false;
          }
        }
      });
      if (this.form instanceof HTMLFormElement && this.form.getRootNode()) {
        this.addListener(
          this.form,
          "submit",
          () => {
            this.submitHandler("in-form submit handler");
          },
          { capture: true }
        );
      }
    }
    canCategorizeAmbiguousInput() {
      return this.device.settings.featureToggles.unknown_username_categorization && this.isLogin && this.ambiguousInputs?.length === 1;
    }
    canCategorizePasswordVariant() {
      return this.device.settings.featureToggles.password_variant_categorization;
    }
    /**
     * Takes an ambiguous input and tries to get a target type that the input should be categorized to.
     * @param {HTMLInputElement} ambiguousInput
     * @returns {import('./matching.js').SupportedTypes | undefined}
     */
    getTargetTypeForAmbiguousInput(ambiguousInput) {
      const ambiguousInputSubtype = getInputSubtype(ambiguousInput);
      const hasUsernameData = Boolean(this.device.settings.availableInputTypes.credentials?.username);
      const hasPhoneData = Boolean(this.device.settings.availableInputTypes.identities?.phone);
      const hasCreditCardData = Boolean(this.device.settings.availableInputTypes.creditCards?.cardNumber);
      if (hasUsernameData || ambiguousInputSubtype === "unknown") return "credentials.username";
      if (hasPhoneData && ambiguousInputSubtype === "phone") return "identities.phone";
      if (hasCreditCardData && ambiguousInputSubtype === "cardNumber") return "creditCards.cardNumber";
    }
    /**
     * Returns the ambiguous inputs that should be categorised.
     * An input is considered ambiguous if it's unknown, phone or credit card and,
     * the form doesn't have a username field,
     * the form has password fields.
     * @returns {HTMLInputElement[] | null}
     */
    get ambiguousInputs() {
      const hasUsernameInput = [...this.inputs.credentials].some((input) => getInputSubtype(input) === "username");
      if (hasUsernameInput) return null;
      const hasPasswordInputs = [...this.inputs.credentials].filter((input) => getInputSubtype(input) === "password").length > 0;
      if (!hasPasswordInputs) return null;
      const phoneInputs = [...this.inputs.identities].filter((input) => getInputSubtype(input) === "phone");
      const cardNumberInputs = [...this.inputs.creditCards].filter((input) => getInputSubtype(input) === "cardNumber");
      return [...this.inputs.unknown, ...phoneInputs, ...cardNumberInputs];
    }
    /**
     * Recategorizes input's attribute to username, decorates it and also updates the input set.
     */
    recategorizeInputToTargetType() {
      const ambiguousInput = this.ambiguousInputs?.[0];
      const inputSelector = this.matching.cssSelector("formInputsSelectorWithoutSelect");
      if (ambiguousInput?.matches?.(inputSelector)) {
        const targetType = this.getTargetTypeForAmbiguousInput(ambiguousInput);
        const inputType = getInputType(ambiguousInput);
        if (!targetType || targetType === inputType) return;
        ambiguousInput.setAttribute(ATTR_INPUT_TYPE2, targetType);
        this.decorateInput(ambiguousInput);
        this.inputs[getMainTypeFromType(targetType)].add(ambiguousInput);
        this.inputs[getMainTypeFromType(inputType)].delete(ambiguousInput);
        if (shouldLog()) console.log(`Recategorized input from ${inputType} to ${targetType}`, ambiguousInput);
      }
    }
    /**
     * Recategorizes the new/current password field variant
     */
    recategorizeInputVariantIfNeeded() {
      let newPasswordFields = 0;
      let currentPasswordFields = 0;
      let firstNewPasswordField = null;
      for (const credentialElement of this.inputs.credentials) {
        const variant = getInputVariant(credentialElement);
        if (variant === "new") {
          newPasswordFields++;
          if (!firstNewPasswordField) firstNewPasswordField = credentialElement;
        }
        if (variant === "current") currentPasswordFields++;
        if (newPasswordFields > 3 || currentPasswordFields > 0) return;
      }
      if (newPasswordFields === 3 && currentPasswordFields === 0) {
        if (shouldLog()) console.log('Recategorizing password variant to "current"', firstNewPasswordField);
        firstNewPasswordField.setAttribute(ATTR_INPUT_TYPE2, "credentials.password.current");
      }
    }
    categorizeInputs() {
      const selector = this.matching.cssSelector("formInputsSelector");
      if (this.form.matches(selector)) {
        this.addInput(this.form);
      } else {
        const formControlElements = getFormControlElements(this.form, selector);
        const foundInputs = formControlElements != null ? [...formControlElements, ...findElementsInShadowTree(this.form, selector)] : queryElementsWithShadow(this.form, selector, true);
        if (foundInputs.length < (this.device.settings.siteSpecificFeature?.maxInputsPerForm || MAX_INPUTS_PER_FORM)) {
          foundInputs.forEach((input) => this.addInput(input));
        } else {
          this.device.scanner.setMode("stopped", `The form has too many inputs (${foundInputs.length}), bailing.`);
          return;
        }
      }
      if (this.canCategorizeAmbiguousInput()) this.recategorizeInputToTargetType();
      if (this.canCategorizePasswordVariant()) this.recategorizeInputVariantIfNeeded();
      if (this.inputs.all.size === 1 && this.inputs.unknown.size === 1) {
        this.destroy();
        return;
      }
      this.initialScanComplete = true;
      if (this.form !== document.body) {
        this.mutObs.observe(this.form, this.mutObsConfig);
      }
    }
    get submitButtons() {
      const selector = this.matching.cssSelector("submitButtonSelector");
      const allButtons = (
        /** @type {HTMLElement[]} */
        queryElementsWithShadow(this.form, selector)
      );
      return allButtons.filter(
        (btn) => isPotentiallyViewable(btn) && isLikelyASubmitButton(btn, this.matching) && buttonMatchesFormType(btn, this)
      );
    }
    attemptSubmissionIfNeeded() {
      if (!this.isLogin || // Only submit login forms
      this.submitButtons.length > 1)
        return;
      let isThereAnEmptyVisibleField = false;
      this.execOnInputs(
        (input) => {
          if (input.value === "" && isPotentiallyViewable(input)) isThereAnEmptyVisibleField = true;
        },
        "all",
        false
      );
      if (isThereAnEmptyVisibleField) return;
      this.submitButtons.forEach((button) => {
        if (isPotentiallyViewable(button)) {
          button.click();
        }
      });
    }
    /**
     * Executes a function on input elements. Can be limited to certain element types
     * @param {(input: HTMLInputElement|HTMLSelectElement) => void} fn
     * @param {'all' | SupportedMainTypes} inputType
     * @param {boolean} shouldCheckForDecorate
     */
    execOnInputs(fn, inputType = "all", shouldCheckForDecorate = true) {
      const inputs = this.inputs[inputType];
      for (const input of inputs) {
        let canExecute = true;
        if (shouldCheckForDecorate) {
          canExecute = isFieldDecorated(input);
        }
        if (canExecute) fn(input);
      }
    }
    addInput(input) {
      if (this.inputs.all.has(input)) return this;
      const siteSpecificFeature = this.device.settings.siteSpecificFeature;
      if (this.inputs.all.size > (siteSpecificFeature?.maxInputsPerForm || MAX_INPUTS_PER_FORM)) {
        this.device.scanner.setMode("stopped", "The form has too many inputs, bailing.");
        return this;
      }
      if (this.initialScanComplete && this.rescanCount < MAX_FORM_RESCANS) {
        this.formAnalyzer = new FormAnalyzer_default(this.form, siteSpecificFeature, input, this.matching);
        this.recategorizeAllInputs();
        return this;
      }
      if (input.maxLength === 1) return this;
      this.inputs.all.add(input);
      const opts = {
        isLogin: this.isLogin,
        isHybrid: this.isHybrid,
        isCCForm: this.isCCForm,
        hasCredentials: Boolean(this.device.settings.availableInputTypes.credentials?.username),
        supportsIdentitiesAutofill: this.device.settings.featureToggles.inputType_identities
      };
      this.matching.setInputType(input, this.form, this.device.settings.siteSpecificFeature, opts);
      const mainInputType = getInputMainType(input);
      this.inputs[mainInputType].add(input);
      this.decorateInput(input);
      return this;
    }
    /**
     * Adds event listeners and keeps track of them for subsequent removal
     * @param {HTMLElement} el
     * @param {Event['type']} type
     * @param {(Event) => void} fn
     * @param {AddEventListenerOptions} [opts]
     */
    addListener(el, type, fn, opts) {
      el.addEventListener(type, fn, opts);
      this.listeners.add({ el, type, fn, opts });
    }
    addAutofillStyles(input) {
      const initialStyles = getIconStylesBase(input, this);
      const activeStyles = getIconStylesAlternate(input, this);
      addInlineStyles(input, initialStyles);
      return {
        onMouseMove: activeStyles,
        onMouseLeave: initialStyles
      };
    }
    /**
     * Decorate here means adding listeners and an optional icon
     * @param {HTMLInputElement} input
     * @returns {Promise<Form>}
     */
    async decorateInput(input) {
      const config = getInputConfig(input);
      const shouldDecorate = await config.shouldDecorate(input, this);
      if (!shouldDecorate) return this;
      input.setAttribute(ATTR_AUTOFILL, "true");
      const hasIcon = !!config.getIconBase(input, this);
      if (hasIcon) {
        const { onMouseMove, onMouseLeave } = this.addAutofillStyles(input);
        this.addListener(input, "mousemove", (e) => {
          if (wasAutofilledByChrome(input)) return;
          if (isEventWithinDax(e, e.target)) {
            addInlineStyles(e.target, {
              cursor: "pointer",
              ...onMouseMove
            });
          } else {
            removeInlineStyles(e.target, { cursor: "pointer" });
            if (!this.device.isTooltipActive()) {
              addInlineStyles(e.target, { ...onMouseLeave });
            }
          }
        });
        this.addListener(input, "mouseleave", (e) => {
          removeInlineStyles(e.target, { cursor: "pointer" });
          if (!this.device.isTooltipActive()) {
            addInlineStyles(e.target, { ...onMouseLeave });
          }
        });
      }
      function getMainClickCoords(e) {
        if (!e.isTrusted) return;
        const isMainMouseButton = e.button === 0;
        if (!isMainMouseButton) return;
        return {
          x: e.clientX,
          y: e.clientY
        };
      }
      function getClickCoords(e, storedClickCoords2) {
        if (e.type === "pointerdown") {
          return getMainClickCoords(
            /** @type {PointerEvent} */
            e
          ) || null;
        }
        const click = storedClickCoords2.get(input);
        storedClickCoords2.delete(input);
        return click || null;
      }
      let storedClickCoords = /* @__PURE__ */ new WeakMap();
      let timeout = null;
      const handlerLabel = (e) => {
        const control = (
          /** @type HTMLElement */
          e.target?.closest("label")?.control
        );
        if (!control) return;
        if (e.isTrusted) {
          storedClickCoords.set(control, getMainClickCoords(e));
        }
        clearTimeout(timeout);
        timeout = setTimeout(() => {
          storedClickCoords = /* @__PURE__ */ new WeakMap();
        }, 1e3);
      };
      const handlerSelect = () => {
        this.touched.add(input);
      };
      const handler = (e) => {
        if (this.isAutofilling || this.device.isTooltipActive()) {
          return;
        }
        const isLabel = e.target instanceof HTMLLabelElement;
        const input2 = isLabel ? e.target.control : e.target;
        if (!input2 || !this.inputs.all.has(input2)) return;
        if (wasAutofilledByChrome(input2)) return;
        if (!canBeInteractedWith(input2)) return;
        const clickCoords = getClickCoords(e, storedClickCoords);
        if (e.type === "pointerdown") {
          if (!e.isTrusted || !clickCoords) return;
        }
        if (this.shouldOpenTooltip(e, input2)) {
          const iconClicked = isEventWithinDax(e, input2);
          if ((this.device.globalConfig.isMobileApp || this.device.globalConfig.isExtension) && // Avoid the icon capturing clicks on small fields making it impossible to focus
          input2.offsetWidth > 50 && iconClicked) {
            e.preventDefault();
            e.stopImmediatePropagation();
            input2.blur();
          }
          this.touched.add(input2);
          this.device.attachTooltip({
            form: this,
            input: input2,
            click: clickCoords,
            trigger: "userInitiated",
            triggerMetaData: {
              // An 'icon' click is very different to a field click or focus.
              // It indicates an explicit opt-in to the feature.
              type: iconClicked ? "explicit-opt-in" : "implicit-opt-in"
            }
          });
          const activeStyles = getIconStylesAlternate(input2, this);
          addInlineStyles(input2, activeStyles);
        }
      };
      const isMobileApp = this.device.globalConfig.isMobileApp;
      if (!(input instanceof HTMLSelectElement)) {
        const events = ["pointerdown"];
        if (!isMobileApp) events.push("focus");
        input.labels?.forEach((label) => {
          this.addListener(label, "pointerdown", isMobileApp ? handler : handlerLabel);
        });
        events.forEach((ev) => this.addListener(input, ev, handler));
      } else {
        this.addListener(input, "change", handlerSelect);
        input.labels?.forEach((label) => {
          this.addListener(label, "pointerdown", isMobileApp ? handlerSelect : handlerLabel);
        });
      }
      return this;
    }
    shouldOpenTooltip(e, input) {
      if (!isPotentiallyViewable(input)) return false;
      if (isEventWithinDax(e, input)) return true;
      if (this.device.globalConfig.isWindows) return true;
      const subtype = getInputSubtype(input);
      const variant = getInputVariant(input);
      const isIncontextSignupAvailable = this.device.inContextSignup?.isAvailable(subtype);
      if (this.device.globalConfig.isApp) {
        const mainType = getInputMainType(input);
        const hasSavedDetails = this.device.settings.canAutofillType({ mainType, subtype, variant }, null);
        if (hasSavedDetails) {
          return true;
        } else if (isIncontextSignupAvailable) {
          return false;
        } else {
          const isInputEmpty = input.value === "";
          return this.isCredentialsImportAvailable && isInputEmpty;
        }
      }
      if (this.device.globalConfig.isExtension || this.device.globalConfig.isMobileApp) {
        if (isIncontextSignupAvailable) return false;
      }
      return !this.touched.has(input) && !input.classList.contains("ddg-autofilled");
    }
    /**
     * Skip overridding values that the user provided if:
     * - we're autofilling non credit card type and,
     * - it's a previously filled input or,
     * - it's a select input that was already "touched" by the user.
     * @param {HTMLInputElement|HTMLSelectElement} input
     * @param {'all' | SupportedMainTypes} dataType
     * @returns {boolean}
     **/
    shouldSkipInput(input, dataType) {
      if (dataType === "creditCards") {
        return false;
      }
      const isPreviouslyFilledInput = input.value !== "" && this.activeInput !== input;
      return input.nodeName === "SELECT" ? this.touched.has(input) : isPreviouslyFilledInput;
    }
    autofillInput(input, string, dataType) {
      if (input instanceof HTMLInputElement && !isPotentiallyViewable(input)) return;
      if (!canBeInteractedWith(input)) return;
      if (this.shouldSkipInput(input, dataType)) return;
      if (input.value === string) return;
      const successful = setValue(input, string, this.device.globalConfig);
      if (!successful) return;
      input.classList.add("ddg-autofilled");
      addInlineStyles(input, getIconStylesAutofilled(input, this));
      this.touched.add(input);
      input.addEventListener("input", (e) => this.removeAllHighlights(e, dataType), { once: true });
    }
    /**
     * Autofill method for email protection only
     * @param {string} alias
     * @param {'all' | SupportedMainTypes} dataType
     */
    autofillEmail(alias, dataType = "identities") {
      this.isAutofilling = true;
      this.execOnInputs((input) => {
        const inputSubtype = getInputSubtype(input);
        if (inputSubtype === "emailAddress") {
          this.autofillInput(input, alias, dataType);
        }
      }, dataType);
      this.isAutofilling = false;
      this.removeTooltip();
    }
    autofillData(data, dataType) {
      this.isAutofilling = true;
      this.execOnInputs((input) => {
        const inputSubtype = getInputSubtype(input);
        let autofillData = data[inputSubtype];
        if (inputSubtype === "expiration" && input instanceof HTMLInputElement) {
          autofillData = getUnifiedExpiryDate(input, data.expirationMonth, data.expirationYear, this);
        }
        if (inputSubtype === "expirationYear" && input instanceof HTMLInputElement) {
          autofillData = formatCCYear(input, autofillData, this);
        }
        if (inputSubtype === "addressCountryCode") {
          autofillData = getCountryName(input, data);
        }
        if (autofillData) {
          const variant = getInputVariant(input);
          if (!variant) {
            return this.autofillInput(input, autofillData, dataType);
          }
          if (variant === "new" && AUTOGENERATED_KEY in data) {
            return this.autofillInput(input, autofillData, dataType);
          }
          if (variant === "current" && !(AUTOGENERATED_KEY in data)) {
            return this.autofillInput(input, autofillData, dataType);
          }
        }
      }, dataType);
      this.isAutofilling = false;
      const formValues = this.getValuesReadyForStorage();
      const areAllFormValuesKnown = Object.keys(formValues[dataType] || {}).every(
        (subtype) => formValues[dataType][subtype] === data[subtype]
      );
      if (areAllFormValuesKnown) {
        this.shouldPromptToStoreData = false;
        this.shouldAutoSubmit = this.device.globalConfig.isMobileApp;
      } else {
        this.resetShouldPromptToStoreData();
        this.shouldAutoSubmit = false;
      }
      this.device.postAutofill?.(data, dataType, this);
      this.removeTooltip();
    }
    /**
     * Set all inputs of the data type to "touched"
     * @param {'all' | SupportedMainTypes} dataType
     */
    touchAllInputs(dataType = "all") {
      this.execOnInputs((input) => this.touched.add(input), dataType);
    }
    get isCredentialsImportAvailable() {
      const isLoginOrHybrid = this.isLogin || this.isHybrid;
      return isLoginOrHybrid && this.device.credentialsImport.isAvailable();
    }
    getFirstViableCredentialsInput() {
      return [...this.inputs.credentials].find((input) => canBeInteractedWith(input) && isPotentiallyViewable(input));
    }
    async promptLoginIfNeeded() {
      if (document.visibilityState !== "visible" || !this.isLogin) return;
      const firstCredentialInput = this.getFirstViableCredentialsInput();
      const input = this.activeInput || firstCredentialInput;
      if (!input) return;
      const mainType = getInputMainType(input);
      const subtype = getInputSubtype(input);
      const variant = getInputVariant(input);
      await this.device.settings.populateDataIfNeeded({ mainType, subtype });
      if (this.device.settings.canAutofillType({ mainType, subtype, variant }, this.device.inContextSignup) || this.isCredentialsImportAvailable) {
        setTimeout(() => {
          safeExecute(this.form, () => {
            const { x, y, width, height } = this.form.getBoundingClientRect();
            const elHCenter = x + width / 2;
            const elVCenter = y + height / 2;
            const topMostElementFromPoint = document.elementFromPoint(elHCenter, elVCenter);
            if (this.form.contains(topMostElementFromPoint)) {
              this.execOnInputs((input2) => {
                if (isPotentiallyViewable(input2)) {
                  this.touched.add(input2);
                }
              }, "credentials");
              this.device.attachTooltip({
                form: this,
                input,
                click: null,
                trigger: "autoprompt",
                triggerMetaData: {
                  type: "implicit-opt-in"
                }
              });
            }
          });
        }, 200);
      }
    }
  };

  // src/Scanner.js
  var { ATTR_INPUT_TYPE: ATTR_INPUT_TYPE3, MAX_INPUTS_PER_PAGE, MAX_FORMS_PER_PAGE, MAX_INPUTS_PER_FORM: MAX_INPUTS_PER_FORM2 } = constants;
  var defaultScannerOptions = {
    // This buffer size is very large because it's an unexpected edge-case that
    // a DOM will be continually modified over and over without ever stopping. If we do see 1000 unique
    // new elements in the buffer however then this will prevent the algorithm from never ending.
    bufferSize: 50,
    // wait for a 500ms window of event silence before performing the scan
    debounceTimePeriod: 500,
    // how long to wait when performing the initial scan
    initialDelay: 0,
    // How many inputs is too many on the page. If we detect that there's above
    // this maximum, then we don't scan the page. This will prevent slowdowns on
    // large pages which are unlikely to require autofill anyway.
    maxInputsPerPage: MAX_INPUTS_PER_PAGE,
    maxFormsPerPage: MAX_FORMS_PER_PAGE,
    maxInputsPerForm: MAX_INPUTS_PER_FORM2
  };
  var DefaultScanner = class {
    /**
     * @param {import("./DeviceInterface/InterfacePrototype").default} device
     * @param {ScannerOptions} options
     */
    constructor(device, options) {
      /** @type Map<HTMLElement, Form> */
      __publicField(this, "forms", /* @__PURE__ */ new Map());
      /** @type {any|undefined} the timer to reset */
      __publicField(this, "debounceTimer");
      /** @type {Set<HTMLElement|Document>} stored changed elements until they can be processed */
      __publicField(this, "changedElements", /* @__PURE__ */ new Set());
      /** @type {ScannerOptions} */
      __publicField(this, "options");
      /** @type {HTMLInputElement | null} */
      __publicField(this, "activeInput", null);
      /** @type {boolean} A flag to indicate the whole page will be re-scanned */
      __publicField(this, "rescanAll", false);
      /** @type {Mode} Indicates the mode in which the scanner is operating */
      __publicField(this, "mode", "scanning");
      /** @type {import("./Form/matching").Matching} matching */
      __publicField(this, "matching");
      /** @type {HTMLElement|null} */
      __publicField(this, "_forcedForm", null);
      /**
       * Watch for changes in the DOM, and enqueue elements to be scanned
       * @type {MutationObserver}
       */
      __publicField(this, "mutObs", new MutationObserver((mutationList) => {
        if (this.rescanAll) {
          this.enqueue([]);
          return;
        }
        const outgoing = [];
        for (const mutationRecord of mutationList) {
          if (mutationRecord.type === "childList") {
            for (const addedNode of mutationRecord.addedNodes) {
              if (!(addedNode instanceof HTMLElement)) continue;
              if (addedNode.nodeName === "DDG-AUTOFILL") continue;
              outgoing.push(addedNode);
            }
          }
        }
        this.enqueue(outgoing);
      }));
      this.device = device;
      this.matching = createMatching();
      this.options = options;
      this.initTimeStamp = Date.now();
    }
    /**
     * Determine whether we should fire the credentials autoprompt. This is needed because some sites are blank
     * on page load and load scripts asynchronously, so our initial scan didn't set the autoprompt correctly
     * @returns {boolean}
     */
    get shouldAutoprompt() {
      if (this.device.globalConfig.isMobileApp && this.device.credentialsImport.isAvailable()) {
        return false;
      }
      return Date.now() - this.initTimeStamp <= 1500;
    }
    /**
     * Call this to scan once and then watch for changes.
     *
     * Call the returned function to remove listeners.
     * @returns {(reason: string, ...rest) => void}
     */
    init() {
      window.addEventListener("pointerdown", this, true);
      if (!this.device.globalConfig.isMobileApp) {
        window.addEventListener("focus", this, true);
      }
      const delay = this.options.initialDelay;
      if (delay === 0) {
        window.requestIdleCallback(() => this.scanAndObserve());
      } else {
        setTimeout(() => this.scanAndObserve(), delay);
      }
      return (reason, ...rest) => {
        this.setMode("stopped", reason, ...rest);
      };
    }
    /**
     * Scan the page and begin observing changes
     */
    scanAndObserve() {
      window.performance?.mark?.("initial_scanner:init:start");
      this.findEligibleInputs(document);
      window.performance?.mark?.("initial_scanner:init:end");
      logPerformance("initial_scanner");
      this.mutObs.observe(document.documentElement, { childList: true, subtree: true });
    }
    /**
     * Core logic for find inputs that are eligible for autofill. If they are,
     * then call addInput which will attempt to add the input to a parent form.
     * @param context
     */
    findEligibleInputs(context) {
      if (this.device.globalConfig.isDDGDomain) {
        return this;
      }
      const formInputsSelectorWithoutSelect = this.matching.cssSelector("formInputsSelectorWithoutSelect");
      if ("matches" in context && context.matches?.(formInputsSelectorWithoutSelect)) {
        this.addInput(context);
      } else {
        const inputs = context.querySelectorAll(formInputsSelectorWithoutSelect);
        if (inputs.length > (this.device.settings.siteSpecificFeature?.maxInputsPerPage || this.options.maxInputsPerPage)) {
          this.setMode("stopped", `Too many input fields in the given context (${inputs.length}), stop scanning`, context);
          return this;
        }
        inputs.forEach((input) => this.addInput(input));
        if (context instanceof HTMLFormElement && this.forms.get(context)?.hasShadowTree) {
          findElementsInShadowTree(context, formInputsSelectorWithoutSelect).forEach((input) => {
            if (input instanceof HTMLInputElement) {
              this.addInput(input, context);
            }
          });
        }
      }
      return this;
    }
    /**
     * Sets the scanner mode, logging the reason and any additional arguments.
     * 'stopped', switches off the mutation observer and clears all forms and listeners,
     * 'on-click', keeps event listeners so that scanning can continue on clicking,
     * 'scanning', default operation triggered in normal conditions
     * Keep the listener for pointerdown to scan on click if needed.
     * @param {Mode} mode
     * @param {string} reason
     * @param {any} rest
     */
    setMode(mode, reason, ...rest) {
      this.mode = mode;
      if (shouldLog()) {
        console.log(mode, reason, ...rest);
      }
      if (mode === "scanning") return;
      if (mode === "stopped") {
        window.removeEventListener("pointerdown", this, true);
        window.removeEventListener("focus", this, true);
      }
      clearTimeout(this.debounceTimer);
      this.changedElements.clear();
      this.mutObs.disconnect();
      this.forms.forEach((form) => {
        form.destroy();
      });
      this.forms.clear();
      const activeInput = this.device.activeForm?.activeInput;
      activeInput?.focus();
    }
    get isStopped() {
      return this.mode === "stopped";
    }
    /**
     * @param {HTMLElement} input
     * @returns {HTMLElement}
     */
    getParentForm(input) {
      this._forcedForm = this.device.settings.siteSpecificFeature?.getForcedForm() || null;
      if (this._forcedForm && containsShadowedTarget(this._forcedForm, input)) {
        return this._forcedForm;
      }
      if (input instanceof HTMLInputElement || input instanceof HTMLSelectElement) {
        if (input.form) {
          if (this.forms.has(input.form) || // If we've added the form we've already checked that it's not a page wrapper
          !isFormLikelyToBeUsedAsPageWrapper(input.form)) {
            return input.form;
          }
        }
      }
      let traversalLayerCount = 0;
      let element = input;
      while (traversalLayerCount <= 5 && element.parentElement !== document.documentElement) {
        const siblingForm = element.parentElement?.querySelector("form");
        if (siblingForm && siblingForm !== element) {
          return element;
        }
        if (element instanceof HTMLFormElement) {
          return element;
        }
        if (element.parentElement) {
          element = element.parentElement;
          if (element.childElementCount > 1) {
            const inputs = element.querySelectorAll(this.matching.cssSelector("formInputsSelector"));
            const buttons = element.querySelectorAll(this.matching.cssSelector("submitButtonSelector"));
            if (inputs.length > 1 || buttons.length) {
              return element;
            }
            traversalLayerCount++;
          }
        } else {
          const root = element.getRootNode();
          if (root instanceof ShadowRoot && root.host) {
            element = root.host;
          } else {
            break;
          }
        }
      }
      return input;
    }
    /**
     * @param {HTMLInputElement|HTMLSelectElement} input
     * @returns {boolean}
     */
    inputExistsInForms(input) {
      return [...this.forms.values()].some((form) => form.inputs.all.has(input));
    }
    /**
     * @param {HTMLInputElement|HTMLSelectElement} input
     * @param {HTMLFormElement|null} form
     */
    addInput(input, form = null) {
      if (this.isStopped) return;
      if (this.inputExistsInForms(input)) return;
      const parentForm = form || this.getParentForm(input);
      if (parentForm instanceof HTMLFormElement && this.forms.has(parentForm)) {
        const foundForm = this.forms.get(parentForm);
        if (foundForm && foundForm.inputs.all.size < (this.device.settings.siteSpecificFeature?.maxInputsPerForm || MAX_INPUTS_PER_FORM2)) {
          foundForm.addInput(input);
        } else {
          this.setMode("stopped", "The form has too many inputs, destroying.");
        }
        return;
      }
      if (parentForm.role === "search") return;
      let previouslyFoundParent, childForm;
      for (const [formEl] of this.forms) {
        if (!formEl.isConnected) {
          this.forms.delete(formEl);
          continue;
        }
        if (formEl.contains(parentForm)) {
          previouslyFoundParent = formEl;
          break;
        }
        if (parentForm.contains(formEl)) {
          childForm = formEl;
          break;
        }
      }
      if (previouslyFoundParent) {
        if (parentForm instanceof HTMLFormElement && parentForm !== previouslyFoundParent) {
          this.forms.delete(previouslyFoundParent);
        } else {
          this.forms.get(previouslyFoundParent)?.addInput(input);
        }
      } else {
        if (childForm && childForm !== this._forcedForm) {
          this.forms.get(childForm)?.destroy();
          this.forms.delete(childForm);
        }
        if (this.forms.size < this.options.maxFormsPerPage) {
          this.forms.set(parentForm, new Form(parentForm, input, this.device, this.matching, this.shouldAutoprompt));
        } else {
          this.setMode("on-click", "The page has too many forms, stop adding them.");
        }
      }
    }
    /**
     * enqueue elements to be re-scanned after the given
     * amount of time has elapsed.
     *
     * @param {(HTMLElement|Document)[]} htmlElements
     */
    enqueue(htmlElements) {
      if (this.changedElements.size >= this.options.bufferSize) {
        this.rescanAll = true;
        this.changedElements.clear();
      } else if (!this.rescanAll) {
        for (const element of htmlElements) {
          this.changedElements.add(element);
        }
      }
      clearTimeout(this.debounceTimer);
      this.debounceTimer = setTimeout(() => {
        window.performance?.mark?.("scanner:init:start");
        this.processChangedElements();
        this.changedElements.clear();
        this.rescanAll = false;
        window.performance?.mark?.("scanner:init:end");
        logPerformance("scanner");
      }, this.options.debounceTimePeriod);
    }
    /**
     * re-scan the changed elements, but only if they
     * are still present in the DOM
     */
    processChangedElements() {
      if (this.rescanAll) {
        this.findEligibleInputs(document);
        return;
      }
      for (const element of this.changedElements) {
        if (element.isConnected) {
          this.findEligibleInputs(element);
        }
      }
    }
    handleEvent(event) {
      switch (event.type) {
        case "pointerdown":
        case "focus":
          this.scanOnClick(event);
          break;
      }
    }
    /**
     * Scan clicked input fields, even if they're within a shadow tree
     * @param {FocusEvent | PointerEvent} event
     */
    scanOnClick(event) {
      if (this.isStopped || !(event.target instanceof Element)) return;
      window.performance?.mark?.("scan_shadow:init:start");
      const realTarget = pierceShadowTree(event, HTMLInputElement);
      if (realTarget instanceof HTMLInputElement && realTarget.matches(this.matching.cssSelector("genericTextInputField")) && !realTarget.hasAttribute(ATTR_INPUT_TYPE3)) {
        if (shouldLog()) console.log("scanOnClick executing for target", realTarget);
        const parentForm = this.getParentForm(realTarget);
        if (parentForm instanceof HTMLInputElement) return;
        const hasShadowTree = event.target?.shadowRoot != null;
        const form = this.forms.get(parentForm);
        if (!form) {
          this.forms.set(
            parentForm,
            new Form(parentForm, realTarget, this.device, this.matching, this.shouldAutoprompt, hasShadowTree)
          );
        } else {
          form.addInput(realTarget);
        }
        this.findEligibleInputs(parentForm);
      }
      window.performance?.mark?.("scan_shadow:init:end");
      logPerformance("scan_shadow");
    }
  };
  function createScanner(device, scannerOptions) {
    return new DefaultScanner(device, {
      ...defaultScannerOptions,
      ...scannerOptions
    });
  }

  // src/UI/controllers/UIController.js
  var UIController = class {
    /**
     * Implement this method to control what happen when Autofill
     * has enough information to 'attach' a tooltip.
     *
     * @param {AttachTooltipArgs} _args
     * @returns {void}
     */
    attachTooltip(_args2) {
      throw new Error("must implement attachTooltip");
    }
    /**
     * Implement this method to control what happen when Autofill
     * has enough information to show the keyboard extension.
     *
     * @param {AttachKeyboardArgs} _args
     * @returns {void}
     */
    attachKeyboard(_args2) {
      throw new Error("must implement attachKeyboard");
    }
    /**
     * Implement this if your tooltip can be created from positioning
     * + topContextData.
     *
     * For example, in an 'overlay' on macOS/Windows this is needed since
     * there's no page information to call 'attach' above.
     *
     * @param {import("../interfaces").PosFn} _pos
     * @param {TopContextData} _topContextData
     * @returns {any | null}
     */
    createTooltip(_pos, _topContextData) {
    }
    /**
     * @param {string} _via
     */
    removeTooltip(_via) {
    }
    /**
     * Set the currently open HTMLTooltip instance
     *
     * @param {import("../HTMLTooltip.js").HTMLTooltip} _tooltip
     */
    setActiveTooltip(_tooltip) {
    }
    /**
     * Get the currently open HTMLTooltip instance, if one exists
     *
     * @returns {import("../HTMLTooltip.js").HTMLTooltip | null}
     */
    getActiveTooltip() {
      return null;
    }
    /**
     * Indicate whether the controller deems itself 'active'
     *
     * @returns {boolean}
     */
    isActive() {
      return false;
    }
    /**
     * Updates the items in the tooltip based on new data. Currently only supporting credentials.
     * @param {CredentialsObject[]} _data
     */
    updateItems(_data7) {
    }
    destroy() {
    }
  };

  // node_modules/zod/v3/external.js
  var external_exports = {};
  __export(external_exports, {
    BRAND: () => BRAND,
    DIRTY: () => DIRTY,
    EMPTY_PATH: () => EMPTY_PATH,
    INVALID: () => INVALID,
    NEVER: () => NEVER,
    OK: () => OK,
    ParseStatus: () => ParseStatus,
    Schema: () => ZodType,
    ZodAny: () => ZodAny,
    ZodArray: () => ZodArray,
    ZodBigInt: () => ZodBigInt,
    ZodBoolean: () => ZodBoolean,
    ZodBranded: () => ZodBranded,
    ZodCatch: () => ZodCatch,
    ZodDate: () => ZodDate,
    ZodDefault: () => ZodDefault,
    ZodDiscriminatedUnion: () => ZodDiscriminatedUnion,
    ZodEffects: () => ZodEffects,
    ZodEnum: () => ZodEnum,
    ZodError: () => ZodError,
    ZodFirstPartyTypeKind: () => ZodFirstPartyTypeKind,
    ZodFunction: () => ZodFunction,
    ZodIntersection: () => ZodIntersection,
    ZodIssueCode: () => ZodIssueCode,
    ZodLazy: () => ZodLazy,
    ZodLiteral: () => ZodLiteral,
    ZodMap: () => ZodMap,
    ZodNaN: () => ZodNaN,
    ZodNativeEnum: () => ZodNativeEnum,
    ZodNever: () => ZodNever,
    ZodNull: () => ZodNull,
    ZodNullable: () => ZodNullable,
    ZodNumber: () => ZodNumber,
    ZodObject: () => ZodObject,
    ZodOptional: () => ZodOptional,
    ZodParsedType: () => ZodParsedType,
    ZodPipeline: () => ZodPipeline,
    ZodPromise: () => ZodPromise,
    ZodReadonly: () => ZodReadonly,
    ZodRecord: () => ZodRecord,
    ZodSchema: () => ZodType,
    ZodSet: () => ZodSet,
    ZodString: () => ZodString,
    ZodSymbol: () => ZodSymbol,
    ZodTransformer: () => ZodEffects,
    ZodTuple: () => ZodTuple,
    ZodType: () => ZodType,
    ZodUndefined: () => ZodUndefined,
    ZodUnion: () => ZodUnion,
    ZodUnknown: () => ZodUnknown,
    ZodVoid: () => ZodVoid,
    addIssueToContext: () => addIssueToContext,
    any: () => anyType,
    array: () => arrayType,
    bigint: () => bigIntType,
    boolean: () => booleanType,
    coerce: () => coerce,
    custom: () => custom,
    date: () => dateType,
    datetimeRegex: () => datetimeRegex,
    defaultErrorMap: () => en_default,
    discriminatedUnion: () => discriminatedUnionType,
    effect: () => effectsType,
    enum: () => enumType,
    function: () => functionType,
    getErrorMap: () => getErrorMap,
    getParsedType: () => getParsedType,
    instanceof: () => instanceOfType,
    intersection: () => intersectionType,
    isAborted: () => isAborted,
    isAsync: () => isAsync,
    isDirty: () => isDirty,
    isValid: () => isValid,
    late: () => late,
    lazy: () => lazyType,
    literal: () => literalType,
    makeIssue: () => makeIssue,
    map: () => mapType,
    nan: () => nanType,
    nativeEnum: () => nativeEnumType,
    never: () => neverType,
    null: () => nullType,
    nullable: () => nullableType,
    number: () => numberType,
    object: () => objectType,
    objectUtil: () => objectUtil,
    oboolean: () => oboolean,
    onumber: () => onumber,
    optional: () => optionalType,
    ostring: () => ostring,
    pipeline: () => pipelineType,
    preprocess: () => preprocessType,
    promise: () => promiseType,
    quotelessJson: () => quotelessJson,
    record: () => recordType,
    set: () => setType,
    setErrorMap: () => setErrorMap,
    strictObject: () => strictObjectType,
    string: () => stringType,
    symbol: () => symbolType,
    transformer: () => effectsType,
    tuple: () => tupleType,
    undefined: () => undefinedType,
    union: () => unionType,
    unknown: () => unknownType,
    util: () => util,
    void: () => voidType
  });

  // node_modules/zod/v3/helpers/util.js
  var util;
  (function(util2) {
    util2.assertEqual = (_) => {
    };
    function assertIs(_arg) {
    }
    util2.assertIs = assertIs;
    function assertNever(_x) {
      throw new Error();
    }
    util2.assertNever = assertNever;
    util2.arrayToEnum = (items) => {
      const obj = {};
      for (const item of items) {
        obj[item] = item;
      }
      return obj;
    };
    util2.getValidEnumValues = (obj) => {
      const validKeys = util2.objectKeys(obj).filter((k) => typeof obj[obj[k]] !== "number");
      const filtered = {};
      for (const k of validKeys) {
        filtered[k] = obj[k];
      }
      return util2.objectValues(filtered);
    };
    util2.objectValues = (obj) => {
      return util2.objectKeys(obj).map(function(e) {
        return obj[e];
      });
    };
    util2.objectKeys = typeof Object.keys === "function" ? (obj) => Object.keys(obj) : (object) => {
      const keys = [];
      for (const key2 in object) {
        if (Object.prototype.hasOwnProperty.call(object, key2)) {
          keys.push(key2);
        }
      }
      return keys;
    };
    util2.find = (arr, checker) => {
      for (const item of arr) {
        if (checker(item))
          return item;
      }
      return void 0;
    };
    util2.isInteger = typeof Number.isInteger === "function" ? (val) => Number.isInteger(val) : (val) => typeof val === "number" && Number.isFinite(val) && Math.floor(val) === val;
    function joinValues(array, separator = " | ") {
      return array.map((val) => typeof val === "string" ? `'${val}'` : val).join(separator);
    }
    util2.joinValues = joinValues;
    util2.jsonStringifyReplacer = (_, value) => {
      if (typeof value === "bigint") {
        return value.toString();
      }
      return value;
    };
  })(util || (util = {}));
  var objectUtil;
  (function(objectUtil2) {
    objectUtil2.mergeShapes = (first, second) => {
      return {
        ...first,
        ...second
        // second overwrites first
      };
    };
  })(objectUtil || (objectUtil = {}));
  var ZodParsedType = util.arrayToEnum([
    "string",
    "nan",
    "number",
    "integer",
    "float",
    "boolean",
    "date",
    "bigint",
    "symbol",
    "function",
    "undefined",
    "null",
    "array",
    "object",
    "unknown",
    "promise",
    "void",
    "never",
    "map",
    "set"
  ]);
  var getParsedType = (data) => {
    const t = typeof data;
    switch (t) {
      case "undefined":
        return ZodParsedType.undefined;
      case "string":
        return ZodParsedType.string;
      case "number":
        return Number.isNaN(data) ? ZodParsedType.nan : ZodParsedType.number;
      case "boolean":
        return ZodParsedType.boolean;
      case "function":
        return ZodParsedType.function;
      case "bigint":
        return ZodParsedType.bigint;
      case "symbol":
        return ZodParsedType.symbol;
      case "object":
        if (Array.isArray(data)) {
          return ZodParsedType.array;
        }
        if (data === null) {
          return ZodParsedType.null;
        }
        if (data.then && typeof data.then === "function" && data.catch && typeof data.catch === "function") {
          return ZodParsedType.promise;
        }
        if (typeof Map !== "undefined" && data instanceof Map) {
          return ZodParsedType.map;
        }
        if (typeof Set !== "undefined" && data instanceof Set) {
          return ZodParsedType.set;
        }
        if (typeof Date !== "undefined" && data instanceof Date) {
          return ZodParsedType.date;
        }
        return ZodParsedType.object;
      default:
        return ZodParsedType.unknown;
    }
  };

  // node_modules/zod/v3/ZodError.js
  var ZodIssueCode = util.arrayToEnum([
    "invalid_type",
    "invalid_literal",
    "custom",
    "invalid_union",
    "invalid_union_discriminator",
    "invalid_enum_value",
    "unrecognized_keys",
    "invalid_arguments",
    "invalid_return_type",
    "invalid_date",
    "invalid_string",
    "too_small",
    "too_big",
    "invalid_intersection_types",
    "not_multiple_of",
    "not_finite"
  ]);
  var quotelessJson = (obj) => {
    const json = JSON.stringify(obj, null, 2);
    return json.replace(/"([^"]+)":/g, "$1:");
  };
  var ZodError = class _ZodError extends Error {
    get errors() {
      return this.issues;
    }
    constructor(issues) {
      super();
      this.issues = [];
      this.addIssue = (sub) => {
        this.issues = [...this.issues, sub];
      };
      this.addIssues = (subs = []) => {
        this.issues = [...this.issues, ...subs];
      };
      const actualProto = new.target.prototype;
      if (Object.setPrototypeOf) {
        Object.setPrototypeOf(this, actualProto);
      } else {
        this.__proto__ = actualProto;
      }
      this.name = "ZodError";
      this.issues = issues;
    }
    format(_mapper) {
      const mapper = _mapper || function(issue) {
        return issue.message;
      };
      const fieldErrors = { _errors: [] };
      const processError = (error) => {
        for (const issue of error.issues) {
          if (issue.code === "invalid_union") {
            issue.unionErrors.map(processError);
          } else if (issue.code === "invalid_return_type") {
            processError(issue.returnTypeError);
          } else if (issue.code === "invalid_arguments") {
            processError(issue.argumentsError);
          } else if (issue.path.length === 0) {
            fieldErrors._errors.push(mapper(issue));
          } else {
            let curr = fieldErrors;
            let i = 0;
            while (i < issue.path.length) {
              const el = issue.path[i];
              const terminal = i === issue.path.length - 1;
              if (!terminal) {
                curr[el] = curr[el] || { _errors: [] };
              } else {
                curr[el] = curr[el] || { _errors: [] };
                curr[el]._errors.push(mapper(issue));
              }
              curr = curr[el];
              i++;
            }
          }
        }
      };
      processError(this);
      return fieldErrors;
    }
    static assert(value) {
      if (!(value instanceof _ZodError)) {
        throw new Error(`Not a ZodError: ${value}`);
      }
    }
    toString() {
      return this.message;
    }
    get message() {
      return JSON.stringify(this.issues, util.jsonStringifyReplacer, 2);
    }
    get isEmpty() {
      return this.issues.length === 0;
    }
    flatten(mapper = (issue) => issue.message) {
      const fieldErrors = {};
      const formErrors = [];
      for (const sub of this.issues) {
        if (sub.path.length > 0) {
          const firstEl = sub.path[0];
          fieldErrors[firstEl] = fieldErrors[firstEl] || [];
          fieldErrors[firstEl].push(mapper(sub));
        } else {
          formErrors.push(mapper(sub));
        }
      }
      return { formErrors, fieldErrors };
    }
    get formErrors() {
      return this.flatten();
    }
  };
  ZodError.create = (issues) => {
    const error = new ZodError(issues);
    return error;
  };

  // node_modules/zod/v3/locales/en.js
  var errorMap = (issue, _ctx) => {
    let message;
    switch (issue.code) {
      case ZodIssueCode.invalid_type:
        if (issue.received === ZodParsedType.undefined) {
          message = "Required";
        } else {
          message = `Expected ${issue.expected}, received ${issue.received}`;
        }
        break;
      case ZodIssueCode.invalid_literal:
        message = `Invalid literal value, expected ${JSON.stringify(issue.expected, util.jsonStringifyReplacer)}`;
        break;
      case ZodIssueCode.unrecognized_keys:
        message = `Unrecognized key(s) in object: ${util.joinValues(issue.keys, ", ")}`;
        break;
      case ZodIssueCode.invalid_union:
        message = `Invalid input`;
        break;
      case ZodIssueCode.invalid_union_discriminator:
        message = `Invalid discriminator value. Expected ${util.joinValues(issue.options)}`;
        break;
      case ZodIssueCode.invalid_enum_value:
        message = `Invalid enum value. Expected ${util.joinValues(issue.options)}, received '${issue.received}'`;
        break;
      case ZodIssueCode.invalid_arguments:
        message = `Invalid function arguments`;
        break;
      case ZodIssueCode.invalid_return_type:
        message = `Invalid function return type`;
        break;
      case ZodIssueCode.invalid_date:
        message = `Invalid date`;
        break;
      case ZodIssueCode.invalid_string:
        if (typeof issue.validation === "object") {
          if ("includes" in issue.validation) {
            message = `Invalid input: must include "${issue.validation.includes}"`;
            if (typeof issue.validation.position === "number") {
              message = `${message} at one or more positions greater than or equal to ${issue.validation.position}`;
            }
          } else if ("startsWith" in issue.validation) {
            message = `Invalid input: must start with "${issue.validation.startsWith}"`;
          } else if ("endsWith" in issue.validation) {
            message = `Invalid input: must end with "${issue.validation.endsWith}"`;
          } else {
            util.assertNever(issue.validation);
          }
        } else if (issue.validation !== "regex") {
          message = `Invalid ${issue.validation}`;
        } else {
          message = "Invalid";
        }
        break;
      case ZodIssueCode.too_small:
        if (issue.type === "array")
          message = `Array must contain ${issue.exact ? "exactly" : issue.inclusive ? `at least` : `more than`} ${issue.minimum} element(s)`;
        else if (issue.type === "string")
          message = `String must contain ${issue.exact ? "exactly" : issue.inclusive ? `at least` : `over`} ${issue.minimum} character(s)`;
        else if (issue.type === "number")
          message = `Number must be ${issue.exact ? `exactly equal to ` : issue.inclusive ? `greater than or equal to ` : `greater than `}${issue.minimum}`;
        else if (issue.type === "bigint")
          message = `Number must be ${issue.exact ? `exactly equal to ` : issue.inclusive ? `greater than or equal to ` : `greater than `}${issue.minimum}`;
        else if (issue.type === "date")
          message = `Date must be ${issue.exact ? `exactly equal to ` : issue.inclusive ? `greater than or equal to ` : `greater than `}${new Date(Number(issue.minimum))}`;
        else
          message = "Invalid input";
        break;
      case ZodIssueCode.too_big:
        if (issue.type === "array")
          message = `Array must contain ${issue.exact ? `exactly` : issue.inclusive ? `at most` : `less than`} ${issue.maximum} element(s)`;
        else if (issue.type === "string")
          message = `String must contain ${issue.exact ? `exactly` : issue.inclusive ? `at most` : `under`} ${issue.maximum} character(s)`;
        else if (issue.type === "number")
          message = `Number must be ${issue.exact ? `exactly` : issue.inclusive ? `less than or equal to` : `less than`} ${issue.maximum}`;
        else if (issue.type === "bigint")
          message = `BigInt must be ${issue.exact ? `exactly` : issue.inclusive ? `less than or equal to` : `less than`} ${issue.maximum}`;
        else if (issue.type === "date")
          message = `Date must be ${issue.exact ? `exactly` : issue.inclusive ? `smaller than or equal to` : `smaller than`} ${new Date(Number(issue.maximum))}`;
        else
          message = "Invalid input";
        break;
      case ZodIssueCode.custom:
        message = `Invalid input`;
        break;
      case ZodIssueCode.invalid_intersection_types:
        message = `Intersection results could not be merged`;
        break;
      case ZodIssueCode.not_multiple_of:
        message = `Number must be a multiple of ${issue.multipleOf}`;
        break;
      case ZodIssueCode.not_finite:
        message = "Number must be finite";
        break;
      default:
        message = _ctx.defaultError;
        util.assertNever(issue);
    }
    return { message };
  };
  var en_default = errorMap;

  // node_modules/zod/v3/errors.js
  var overrideErrorMap = en_default;
  function setErrorMap(map) {
    overrideErrorMap = map;
  }
  function getErrorMap() {
    return overrideErrorMap;
  }

  // node_modules/zod/v3/helpers/parseUtil.js
  var makeIssue = (params) => {
    const { data, path, errorMaps, issueData } = params;
    const fullPath = [...path, ...issueData.path || []];
    const fullIssue = {
      ...issueData,
      path: fullPath
    };
    if (issueData.message !== void 0) {
      return {
        ...issueData,
        path: fullPath,
        message: issueData.message
      };
    }
    let errorMessage = "";
    const maps = errorMaps.filter((m) => !!m).slice().reverse();
    for (const map of maps) {
      errorMessage = map(fullIssue, { data, defaultError: errorMessage }).message;
    }
    return {
      ...issueData,
      path: fullPath,
      message: errorMessage
    };
  };
  var EMPTY_PATH = [];
  function addIssueToContext(ctx, issueData) {
    const overrideMap = getErrorMap();
    const issue = makeIssue({
      issueData,
      data: ctx.data,
      path: ctx.path,
      errorMaps: [
        ctx.common.contextualErrorMap,
        // contextual error map is first priority
        ctx.schemaErrorMap,
        // then schema-bound map if available
        overrideMap,
        // then global override map
        overrideMap === en_default ? void 0 : en_default
        // then global default map
      ].filter((x) => !!x)
    });
    ctx.common.issues.push(issue);
  }
  var ParseStatus = class _ParseStatus {
    constructor() {
      this.value = "valid";
    }
    dirty() {
      if (this.value === "valid")
        this.value = "dirty";
    }
    abort() {
      if (this.value !== "aborted")
        this.value = "aborted";
    }
    static mergeArray(status, results) {
      const arrayValue = [];
      for (const s of results) {
        if (s.status === "aborted")
          return INVALID;
        if (s.status === "dirty")
          status.dirty();
        arrayValue.push(s.value);
      }
      return { status: status.value, value: arrayValue };
    }
    static async mergeObjectAsync(status, pairs) {
      const syncPairs = [];
      for (const pair of pairs) {
        const key2 = await pair.key;
        const value = await pair.value;
        syncPairs.push({
          key: key2,
          value
        });
      }
      return _ParseStatus.mergeObjectSync(status, syncPairs);
    }
    static mergeObjectSync(status, pairs) {
      const finalObject = {};
      for (const pair of pairs) {
        const { key: key2, value } = pair;
        if (key2.status === "aborted")
          return INVALID;
        if (value.status === "aborted")
          return INVALID;
        if (key2.status === "dirty")
          status.dirty();
        if (value.status === "dirty")
          status.dirty();
        if (key2.value !== "__proto__" && (typeof value.value !== "undefined" || pair.alwaysSet)) {
          finalObject[key2.value] = value.value;
        }
      }
      return { status: status.value, value: finalObject };
    }
  };
  var INVALID = Object.freeze({
    status: "aborted"
  });
  var DIRTY = (value) => ({ status: "dirty", value });
  var OK = (value) => ({ status: "valid", value });
  var isAborted = (x) => x.status === "aborted";
  var isDirty = (x) => x.status === "dirty";
  var isValid = (x) => x.status === "valid";
  var isAsync = (x) => typeof Promise !== "undefined" && x instanceof Promise;

  // node_modules/zod/v3/helpers/errorUtil.js
  var errorUtil;
  (function(errorUtil2) {
    errorUtil2.errToObj = (message) => typeof message === "string" ? { message } : message || {};
    errorUtil2.toString = (message) => typeof message === "string" ? message : message?.message;
  })(errorUtil || (errorUtil = {}));

  // node_modules/zod/v3/types.js
  var ParseInputLazyPath = class {
    constructor(parent, value, path, key2) {
      this._cachedPath = [];
      this.parent = parent;
      this.data = value;
      this._path = path;
      this._key = key2;
    }
    get path() {
      if (!this._cachedPath.length) {
        if (Array.isArray(this._key)) {
          this._cachedPath.push(...this._path, ...this._key);
        } else {
          this._cachedPath.push(...this._path, this._key);
        }
      }
      return this._cachedPath;
    }
  };
  var handleResult = (ctx, result) => {
    if (isValid(result)) {
      return { success: true, data: result.value };
    } else {
      if (!ctx.common.issues.length) {
        throw new Error("Validation failed but no issues detected.");
      }
      return {
        success: false,
        get error() {
          if (this._error)
            return this._error;
          const error = new ZodError(ctx.common.issues);
          this._error = error;
          return this._error;
        }
      };
    }
  };
  function processCreateParams(params) {
    if (!params)
      return {};
    const { errorMap: errorMap2, invalid_type_error, required_error, description } = params;
    if (errorMap2 && (invalid_type_error || required_error)) {
      throw new Error(`Can't use "invalid_type_error" or "required_error" in conjunction with custom error map.`);
    }
    if (errorMap2)
      return { errorMap: errorMap2, description };
    const customMap = (iss, ctx) => {
      const { message } = params;
      if (iss.code === "invalid_enum_value") {
        return { message: message ?? ctx.defaultError };
      }
      if (typeof ctx.data === "undefined") {
        return { message: message ?? required_error ?? ctx.defaultError };
      }
      if (iss.code !== "invalid_type")
        return { message: ctx.defaultError };
      return { message: message ?? invalid_type_error ?? ctx.defaultError };
    };
    return { errorMap: customMap, description };
  }
  var ZodType = class {
    get description() {
      return this._def.description;
    }
    _getType(input) {
      return getParsedType(input.data);
    }
    _getOrReturnCtx(input, ctx) {
      return ctx || {
        common: input.parent.common,
        data: input.data,
        parsedType: getParsedType(input.data),
        schemaErrorMap: this._def.errorMap,
        path: input.path,
        parent: input.parent
      };
    }
    _processInputParams(input) {
      return {
        status: new ParseStatus(),
        ctx: {
          common: input.parent.common,
          data: input.data,
          parsedType: getParsedType(input.data),
          schemaErrorMap: this._def.errorMap,
          path: input.path,
          parent: input.parent
        }
      };
    }
    _parseSync(input) {
      const result = this._parse(input);
      if (isAsync(result)) {
        throw new Error("Synchronous parse encountered promise.");
      }
      return result;
    }
    _parseAsync(input) {
      const result = this._parse(input);
      return Promise.resolve(result);
    }
    parse(data, params) {
      const result = this.safeParse(data, params);
      if (result.success)
        return result.data;
      throw result.error;
    }
    safeParse(data, params) {
      const ctx = {
        common: {
          issues: [],
          async: params?.async ?? false,
          contextualErrorMap: params?.errorMap
        },
        path: params?.path || [],
        schemaErrorMap: this._def.errorMap,
        parent: null,
        data,
        parsedType: getParsedType(data)
      };
      const result = this._parseSync({ data, path: ctx.path, parent: ctx });
      return handleResult(ctx, result);
    }
    "~validate"(data) {
      const ctx = {
        common: {
          issues: [],
          async: !!this["~standard"].async
        },
        path: [],
        schemaErrorMap: this._def.errorMap,
        parent: null,
        data,
        parsedType: getParsedType(data)
      };
      if (!this["~standard"].async) {
        try {
          const result = this._parseSync({ data, path: [], parent: ctx });
          return isValid(result) ? {
            value: result.value
          } : {
            issues: ctx.common.issues
          };
        } catch (err) {
          if (err?.message?.toLowerCase()?.includes("encountered")) {
            this["~standard"].async = true;
          }
          ctx.common = {
            issues: [],
            async: true
          };
        }
      }
      return this._parseAsync({ data, path: [], parent: ctx }).then((result) => isValid(result) ? {
        value: result.value
      } : {
        issues: ctx.common.issues
      });
    }
    async parseAsync(data, params) {
      const result = await this.safeParseAsync(data, params);
      if (result.success)
        return result.data;
      throw result.error;
    }
    async safeParseAsync(data, params) {
      const ctx = {
        common: {
          issues: [],
          contextualErrorMap: params?.errorMap,
          async: true
        },
        path: params?.path || [],
        schemaErrorMap: this._def.errorMap,
        parent: null,
        data,
        parsedType: getParsedType(data)
      };
      const maybeAsyncResult = this._parse({ data, path: ctx.path, parent: ctx });
      const result = await (isAsync(maybeAsyncResult) ? maybeAsyncResult : Promise.resolve(maybeAsyncResult));
      return handleResult(ctx, result);
    }
    refine(check, message) {
      const getIssueProperties = (val) => {
        if (typeof message === "string" || typeof message === "undefined") {
          return { message };
        } else if (typeof message === "function") {
          return message(val);
        } else {
          return message;
        }
      };
      return this._refinement((val, ctx) => {
        const result = check(val);
        const setError = () => ctx.addIssue({
          code: ZodIssueCode.custom,
          ...getIssueProperties(val)
        });
        if (typeof Promise !== "undefined" && result instanceof Promise) {
          return result.then((data) => {
            if (!data) {
              setError();
              return false;
            } else {
              return true;
            }
          });
        }
        if (!result) {
          setError();
          return false;
        } else {
          return true;
        }
      });
    }
    refinement(check, refinementData) {
      return this._refinement((val, ctx) => {
        if (!check(val)) {
          ctx.addIssue(typeof refinementData === "function" ? refinementData(val, ctx) : refinementData);
          return false;
        } else {
          return true;
        }
      });
    }
    _refinement(refinement) {
      return new ZodEffects({
        schema: this,
        typeName: ZodFirstPartyTypeKind.ZodEffects,
        effect: { type: "refinement", refinement }
      });
    }
    superRefine(refinement) {
      return this._refinement(refinement);
    }
    constructor(def) {
      this.spa = this.safeParseAsync;
      this._def = def;
      this.parse = this.parse.bind(this);
      this.safeParse = this.safeParse.bind(this);
      this.parseAsync = this.parseAsync.bind(this);
      this.safeParseAsync = this.safeParseAsync.bind(this);
      this.spa = this.spa.bind(this);
      this.refine = this.refine.bind(this);
      this.refinement = this.refinement.bind(this);
      this.superRefine = this.superRefine.bind(this);
      this.optional = this.optional.bind(this);
      this.nullable = this.nullable.bind(this);
      this.nullish = this.nullish.bind(this);
      this.array = this.array.bind(this);
      this.promise = this.promise.bind(this);
      this.or = this.or.bind(this);
      this.and = this.and.bind(this);
      this.transform = this.transform.bind(this);
      this.brand = this.brand.bind(this);
      this.default = this.default.bind(this);
      this.catch = this.catch.bind(this);
      this.describe = this.describe.bind(this);
      this.pipe = this.pipe.bind(this);
      this.readonly = this.readonly.bind(this);
      this.isNullable = this.isNullable.bind(this);
      this.isOptional = this.isOptional.bind(this);
      this["~standard"] = {
        version: 1,
        vendor: "zod",
        validate: (data) => this["~validate"](data)
      };
    }
    optional() {
      return ZodOptional.create(this, this._def);
    }
    nullable() {
      return ZodNullable.create(this, this._def);
    }
    nullish() {
      return this.nullable().optional();
    }
    array() {
      return ZodArray.create(this);
    }
    promise() {
      return ZodPromise.create(this, this._def);
    }
    or(option) {
      return ZodUnion.create([this, option], this._def);
    }
    and(incoming) {
      return ZodIntersection.create(this, incoming, this._def);
    }
    transform(transform) {
      return new ZodEffects({
        ...processCreateParams(this._def),
        schema: this,
        typeName: ZodFirstPartyTypeKind.ZodEffects,
        effect: { type: "transform", transform }
      });
    }
    default(def) {
      const defaultValueFunc = typeof def === "function" ? def : () => def;
      return new ZodDefault({
        ...processCreateParams(this._def),
        innerType: this,
        defaultValue: defaultValueFunc,
        typeName: ZodFirstPartyTypeKind.ZodDefault
      });
    }
    brand() {
      return new ZodBranded({
        typeName: ZodFirstPartyTypeKind.ZodBranded,
        type: this,
        ...processCreateParams(this._def)
      });
    }
    catch(def) {
      const catchValueFunc = typeof def === "function" ? def : () => def;
      return new ZodCatch({
        ...processCreateParams(this._def),
        innerType: this,
        catchValue: catchValueFunc,
        typeName: ZodFirstPartyTypeKind.ZodCatch
      });
    }
    describe(description) {
      const This = this.constructor;
      return new This({
        ...this._def,
        description
      });
    }
    pipe(target) {
      return ZodPipeline.create(this, target);
    }
    readonly() {
      return ZodReadonly.create(this);
    }
    isOptional() {
      return this.safeParse(void 0).success;
    }
    isNullable() {
      return this.safeParse(null).success;
    }
  };
  var cuidRegex = /^c[^\s-]{8,}$/i;
  var cuid2Regex = /^[0-9a-z]+$/;
  var ulidRegex = /^[0-9A-HJKMNP-TV-Z]{26}$/i;
  var uuidRegex = /^[0-9a-fA-F]{8}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{12}$/i;
  var nanoidRegex = /^[a-z0-9_-]{21}$/i;
  var jwtRegex = /^[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+\.[A-Za-z0-9-_]*$/;
  var durationRegex = /^[-+]?P(?!$)(?:(?:[-+]?\d+Y)|(?:[-+]?\d+[.,]\d+Y$))?(?:(?:[-+]?\d+M)|(?:[-+]?\d+[.,]\d+M$))?(?:(?:[-+]?\d+W)|(?:[-+]?\d+[.,]\d+W$))?(?:(?:[-+]?\d+D)|(?:[-+]?\d+[.,]\d+D$))?(?:T(?=[\d+-])(?:(?:[-+]?\d+H)|(?:[-+]?\d+[.,]\d+H$))?(?:(?:[-+]?\d+M)|(?:[-+]?\d+[.,]\d+M$))?(?:[-+]?\d+(?:[.,]\d+)?S)?)??$/;
  var emailRegex = /^(?!\.)(?!.*\.\.)([A-Z0-9_'+\-\.]*)[A-Z0-9_+-]@([A-Z0-9][A-Z0-9\-]*\.)+[A-Z]{2,}$/i;
  var _emojiRegex = `^(\\p{Extended_Pictographic}|\\p{Emoji_Component})+$`;
  var emojiRegex;
  var ipv4Regex = /^(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])$/;
  var ipv4CidrRegex = /^(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9][0-9]|[0-9])\/(3[0-2]|[12]?[0-9])$/;
  var ipv6Regex = /^(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))$/;
  var ipv6CidrRegex = /^(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))\/(12[0-8]|1[01][0-9]|[1-9]?[0-9])$/;
  var base64Regex = /^([0-9a-zA-Z+/]{4})*(([0-9a-zA-Z+/]{2}==)|([0-9a-zA-Z+/]{3}=))?$/;
  var base64urlRegex = /^([0-9a-zA-Z-_]{4})*(([0-9a-zA-Z-_]{2}(==)?)|([0-9a-zA-Z-_]{3}(=)?))?$/;
  var dateRegexSource = `((\\d\\d[2468][048]|\\d\\d[13579][26]|\\d\\d0[48]|[02468][048]00|[13579][26]00)-02-29|\\d{4}-((0[13578]|1[02])-(0[1-9]|[12]\\d|3[01])|(0[469]|11)-(0[1-9]|[12]\\d|30)|(02)-(0[1-9]|1\\d|2[0-8])))`;
  var dateRegex = new RegExp(`^${dateRegexSource}$`);
  function timeRegexSource(args) {
    let secondsRegexSource = `[0-5]\\d`;
    if (args.precision) {
      secondsRegexSource = `${secondsRegexSource}\\.\\d{${args.precision}}`;
    } else if (args.precision == null) {
      secondsRegexSource = `${secondsRegexSource}(\\.\\d+)?`;
    }
    const secondsQuantifier = args.precision ? "+" : "?";
    return `([01]\\d|2[0-3]):[0-5]\\d(:${secondsRegexSource})${secondsQuantifier}`;
  }
  function timeRegex(args) {
    return new RegExp(`^${timeRegexSource(args)}$`);
  }
  function datetimeRegex(args) {
    let regex = `${dateRegexSource}T${timeRegexSource(args)}`;
    const opts = [];
    opts.push(args.local ? `Z?` : `Z`);
    if (args.offset)
      opts.push(`([+-]\\d{2}:?\\d{2})`);
    regex = `${regex}(${opts.join("|")})`;
    return new RegExp(`^${regex}$`);
  }
  function isValidIP(ip, version) {
    if ((version === "v4" || !version) && ipv4Regex.test(ip)) {
      return true;
    }
    if ((version === "v6" || !version) && ipv6Regex.test(ip)) {
      return true;
    }
    return false;
  }
  function isValidJWT(jwt, alg) {
    if (!jwtRegex.test(jwt))
      return false;
    try {
      const [header] = jwt.split(".");
      if (!header)
        return false;
      const base64 = header.replace(/-/g, "+").replace(/_/g, "/").padEnd(header.length + (4 - header.length % 4) % 4, "=");
      const decoded = JSON.parse(atob(base64));
      if (typeof decoded !== "object" || decoded === null)
        return false;
      if ("typ" in decoded && decoded?.typ !== "JWT")
        return false;
      if (!decoded.alg)
        return false;
      if (alg && decoded.alg !== alg)
        return false;
      return true;
    } catch {
      return false;
    }
  }
  function isValidCidr(ip, version) {
    if ((version === "v4" || !version) && ipv4CidrRegex.test(ip)) {
      return true;
    }
    if ((version === "v6" || !version) && ipv6CidrRegex.test(ip)) {
      return true;
    }
    return false;
  }
  var ZodString = class _ZodString extends ZodType {
    _parse(input) {
      if (this._def.coerce) {
        input.data = String(input.data);
      }
      const parsedType = this._getType(input);
      if (parsedType !== ZodParsedType.string) {
        const ctx2 = this._getOrReturnCtx(input);
        addIssueToContext(ctx2, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.string,
          received: ctx2.parsedType
        });
        return INVALID;
      }
      const status = new ParseStatus();
      let ctx = void 0;
      for (const check of this._def.checks) {
        if (check.kind === "min") {
          if (input.data.length < check.value) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.too_small,
              minimum: check.value,
              type: "string",
              inclusive: true,
              exact: false,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "max") {
          if (input.data.length > check.value) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.too_big,
              maximum: check.value,
              type: "string",
              inclusive: true,
              exact: false,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "length") {
          const tooBig = input.data.length > check.value;
          const tooSmall = input.data.length < check.value;
          if (tooBig || tooSmall) {
            ctx = this._getOrReturnCtx(input, ctx);
            if (tooBig) {
              addIssueToContext(ctx, {
                code: ZodIssueCode.too_big,
                maximum: check.value,
                type: "string",
                inclusive: true,
                exact: true,
                message: check.message
              });
            } else if (tooSmall) {
              addIssueToContext(ctx, {
                code: ZodIssueCode.too_small,
                minimum: check.value,
                type: "string",
                inclusive: true,
                exact: true,
                message: check.message
              });
            }
            status.dirty();
          }
        } else if (check.kind === "email") {
          if (!emailRegex.test(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "email",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "emoji") {
          if (!emojiRegex) {
            emojiRegex = new RegExp(_emojiRegex, "u");
          }
          if (!emojiRegex.test(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "emoji",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "uuid") {
          if (!uuidRegex.test(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "uuid",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "nanoid") {
          if (!nanoidRegex.test(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "nanoid",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "cuid") {
          if (!cuidRegex.test(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "cuid",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "cuid2") {
          if (!cuid2Regex.test(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "cuid2",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "ulid") {
          if (!ulidRegex.test(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "ulid",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "url") {
          try {
            new URL(input.data);
          } catch {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "url",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "regex") {
          check.regex.lastIndex = 0;
          const testResult = check.regex.test(input.data);
          if (!testResult) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "regex",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "trim") {
          input.data = input.data.trim();
        } else if (check.kind === "includes") {
          if (!input.data.includes(check.value, check.position)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.invalid_string,
              validation: { includes: check.value, position: check.position },
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "toLowerCase") {
          input.data = input.data.toLowerCase();
        } else if (check.kind === "toUpperCase") {
          input.data = input.data.toUpperCase();
        } else if (check.kind === "startsWith") {
          if (!input.data.startsWith(check.value)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.invalid_string,
              validation: { startsWith: check.value },
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "endsWith") {
          if (!input.data.endsWith(check.value)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.invalid_string,
              validation: { endsWith: check.value },
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "datetime") {
          const regex = datetimeRegex(check);
          if (!regex.test(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.invalid_string,
              validation: "datetime",
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "date") {
          const regex = dateRegex;
          if (!regex.test(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.invalid_string,
              validation: "date",
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "time") {
          const regex = timeRegex(check);
          if (!regex.test(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.invalid_string,
              validation: "time",
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "duration") {
          if (!durationRegex.test(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "duration",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "ip") {
          if (!isValidIP(input.data, check.version)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "ip",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "jwt") {
          if (!isValidJWT(input.data, check.alg)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "jwt",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "cidr") {
          if (!isValidCidr(input.data, check.version)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "cidr",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "base64") {
          if (!base64Regex.test(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "base64",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "base64url") {
          if (!base64urlRegex.test(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              validation: "base64url",
              code: ZodIssueCode.invalid_string,
              message: check.message
            });
            status.dirty();
          }
        } else {
          util.assertNever(check);
        }
      }
      return { status: status.value, value: input.data };
    }
    _regex(regex, validation, message) {
      return this.refinement((data) => regex.test(data), {
        validation,
        code: ZodIssueCode.invalid_string,
        ...errorUtil.errToObj(message)
      });
    }
    _addCheck(check) {
      return new _ZodString({
        ...this._def,
        checks: [...this._def.checks, check]
      });
    }
    email(message) {
      return this._addCheck({ kind: "email", ...errorUtil.errToObj(message) });
    }
    url(message) {
      return this._addCheck({ kind: "url", ...errorUtil.errToObj(message) });
    }
    emoji(message) {
      return this._addCheck({ kind: "emoji", ...errorUtil.errToObj(message) });
    }
    uuid(message) {
      return this._addCheck({ kind: "uuid", ...errorUtil.errToObj(message) });
    }
    nanoid(message) {
      return this._addCheck({ kind: "nanoid", ...errorUtil.errToObj(message) });
    }
    cuid(message) {
      return this._addCheck({ kind: "cuid", ...errorUtil.errToObj(message) });
    }
    cuid2(message) {
      return this._addCheck({ kind: "cuid2", ...errorUtil.errToObj(message) });
    }
    ulid(message) {
      return this._addCheck({ kind: "ulid", ...errorUtil.errToObj(message) });
    }
    base64(message) {
      return this._addCheck({ kind: "base64", ...errorUtil.errToObj(message) });
    }
    base64url(message) {
      return this._addCheck({
        kind: "base64url",
        ...errorUtil.errToObj(message)
      });
    }
    jwt(options) {
      return this._addCheck({ kind: "jwt", ...errorUtil.errToObj(options) });
    }
    ip(options) {
      return this._addCheck({ kind: "ip", ...errorUtil.errToObj(options) });
    }
    cidr(options) {
      return this._addCheck({ kind: "cidr", ...errorUtil.errToObj(options) });
    }
    datetime(options) {
      if (typeof options === "string") {
        return this._addCheck({
          kind: "datetime",
          precision: null,
          offset: false,
          local: false,
          message: options
        });
      }
      return this._addCheck({
        kind: "datetime",
        precision: typeof options?.precision === "undefined" ? null : options?.precision,
        offset: options?.offset ?? false,
        local: options?.local ?? false,
        ...errorUtil.errToObj(options?.message)
      });
    }
    date(message) {
      return this._addCheck({ kind: "date", message });
    }
    time(options) {
      if (typeof options === "string") {
        return this._addCheck({
          kind: "time",
          precision: null,
          message: options
        });
      }
      return this._addCheck({
        kind: "time",
        precision: typeof options?.precision === "undefined" ? null : options?.precision,
        ...errorUtil.errToObj(options?.message)
      });
    }
    duration(message) {
      return this._addCheck({ kind: "duration", ...errorUtil.errToObj(message) });
    }
    regex(regex, message) {
      return this._addCheck({
        kind: "regex",
        regex,
        ...errorUtil.errToObj(message)
      });
    }
    includes(value, options) {
      return this._addCheck({
        kind: "includes",
        value,
        position: options?.position,
        ...errorUtil.errToObj(options?.message)
      });
    }
    startsWith(value, message) {
      return this._addCheck({
        kind: "startsWith",
        value,
        ...errorUtil.errToObj(message)
      });
    }
    endsWith(value, message) {
      return this._addCheck({
        kind: "endsWith",
        value,
        ...errorUtil.errToObj(message)
      });
    }
    min(minLength, message) {
      return this._addCheck({
        kind: "min",
        value: minLength,
        ...errorUtil.errToObj(message)
      });
    }
    max(maxLength, message) {
      return this._addCheck({
        kind: "max",
        value: maxLength,
        ...errorUtil.errToObj(message)
      });
    }
    length(len, message) {
      return this._addCheck({
        kind: "length",
        value: len,
        ...errorUtil.errToObj(message)
      });
    }
    /**
     * Equivalent to `.min(1)`
     */
    nonempty(message) {
      return this.min(1, errorUtil.errToObj(message));
    }
    trim() {
      return new _ZodString({
        ...this._def,
        checks: [...this._def.checks, { kind: "trim" }]
      });
    }
    toLowerCase() {
      return new _ZodString({
        ...this._def,
        checks: [...this._def.checks, { kind: "toLowerCase" }]
      });
    }
    toUpperCase() {
      return new _ZodString({
        ...this._def,
        checks: [...this._def.checks, { kind: "toUpperCase" }]
      });
    }
    get isDatetime() {
      return !!this._def.checks.find((ch) => ch.kind === "datetime");
    }
    get isDate() {
      return !!this._def.checks.find((ch) => ch.kind === "date");
    }
    get isTime() {
      return !!this._def.checks.find((ch) => ch.kind === "time");
    }
    get isDuration() {
      return !!this._def.checks.find((ch) => ch.kind === "duration");
    }
    get isEmail() {
      return !!this._def.checks.find((ch) => ch.kind === "email");
    }
    get isURL() {
      return !!this._def.checks.find((ch) => ch.kind === "url");
    }
    get isEmoji() {
      return !!this._def.checks.find((ch) => ch.kind === "emoji");
    }
    get isUUID() {
      return !!this._def.checks.find((ch) => ch.kind === "uuid");
    }
    get isNANOID() {
      return !!this._def.checks.find((ch) => ch.kind === "nanoid");
    }
    get isCUID() {
      return !!this._def.checks.find((ch) => ch.kind === "cuid");
    }
    get isCUID2() {
      return !!this._def.checks.find((ch) => ch.kind === "cuid2");
    }
    get isULID() {
      return !!this._def.checks.find((ch) => ch.kind === "ulid");
    }
    get isIP() {
      return !!this._def.checks.find((ch) => ch.kind === "ip");
    }
    get isCIDR() {
      return !!this._def.checks.find((ch) => ch.kind === "cidr");
    }
    get isBase64() {
      return !!this._def.checks.find((ch) => ch.kind === "base64");
    }
    get isBase64url() {
      return !!this._def.checks.find((ch) => ch.kind === "base64url");
    }
    get minLength() {
      let min = null;
      for (const ch of this._def.checks) {
        if (ch.kind === "min") {
          if (min === null || ch.value > min)
            min = ch.value;
        }
      }
      return min;
    }
    get maxLength() {
      let max = null;
      for (const ch of this._def.checks) {
        if (ch.kind === "max") {
          if (max === null || ch.value < max)
            max = ch.value;
        }
      }
      return max;
    }
  };
  ZodString.create = (params) => {
    return new ZodString({
      checks: [],
      typeName: ZodFirstPartyTypeKind.ZodString,
      coerce: params?.coerce ?? false,
      ...processCreateParams(params)
    });
  };
  function floatSafeRemainder(val, step) {
    const valDecCount = (val.toString().split(".")[1] || "").length;
    const stepDecCount = (step.toString().split(".")[1] || "").length;
    const decCount = valDecCount > stepDecCount ? valDecCount : stepDecCount;
    const valInt = Number.parseInt(val.toFixed(decCount).replace(".", ""));
    const stepInt = Number.parseInt(step.toFixed(decCount).replace(".", ""));
    return valInt % stepInt / 10 ** decCount;
  }
  var ZodNumber = class _ZodNumber extends ZodType {
    constructor() {
      super(...arguments);
      this.min = this.gte;
      this.max = this.lte;
      this.step = this.multipleOf;
    }
    _parse(input) {
      if (this._def.coerce) {
        input.data = Number(input.data);
      }
      const parsedType = this._getType(input);
      if (parsedType !== ZodParsedType.number) {
        const ctx2 = this._getOrReturnCtx(input);
        addIssueToContext(ctx2, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.number,
          received: ctx2.parsedType
        });
        return INVALID;
      }
      let ctx = void 0;
      const status = new ParseStatus();
      for (const check of this._def.checks) {
        if (check.kind === "int") {
          if (!util.isInteger(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.invalid_type,
              expected: "integer",
              received: "float",
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "min") {
          const tooSmall = check.inclusive ? input.data < check.value : input.data <= check.value;
          if (tooSmall) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.too_small,
              minimum: check.value,
              type: "number",
              inclusive: check.inclusive,
              exact: false,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "max") {
          const tooBig = check.inclusive ? input.data > check.value : input.data >= check.value;
          if (tooBig) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.too_big,
              maximum: check.value,
              type: "number",
              inclusive: check.inclusive,
              exact: false,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "multipleOf") {
          if (floatSafeRemainder(input.data, check.value) !== 0) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.not_multiple_of,
              multipleOf: check.value,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "finite") {
          if (!Number.isFinite(input.data)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.not_finite,
              message: check.message
            });
            status.dirty();
          }
        } else {
          util.assertNever(check);
        }
      }
      return { status: status.value, value: input.data };
    }
    gte(value, message) {
      return this.setLimit("min", value, true, errorUtil.toString(message));
    }
    gt(value, message) {
      return this.setLimit("min", value, false, errorUtil.toString(message));
    }
    lte(value, message) {
      return this.setLimit("max", value, true, errorUtil.toString(message));
    }
    lt(value, message) {
      return this.setLimit("max", value, false, errorUtil.toString(message));
    }
    setLimit(kind, value, inclusive, message) {
      return new _ZodNumber({
        ...this._def,
        checks: [
          ...this._def.checks,
          {
            kind,
            value,
            inclusive,
            message: errorUtil.toString(message)
          }
        ]
      });
    }
    _addCheck(check) {
      return new _ZodNumber({
        ...this._def,
        checks: [...this._def.checks, check]
      });
    }
    int(message) {
      return this._addCheck({
        kind: "int",
        message: errorUtil.toString(message)
      });
    }
    positive(message) {
      return this._addCheck({
        kind: "min",
        value: 0,
        inclusive: false,
        message: errorUtil.toString(message)
      });
    }
    negative(message) {
      return this._addCheck({
        kind: "max",
        value: 0,
        inclusive: false,
        message: errorUtil.toString(message)
      });
    }
    nonpositive(message) {
      return this._addCheck({
        kind: "max",
        value: 0,
        inclusive: true,
        message: errorUtil.toString(message)
      });
    }
    nonnegative(message) {
      return this._addCheck({
        kind: "min",
        value: 0,
        inclusive: true,
        message: errorUtil.toString(message)
      });
    }
    multipleOf(value, message) {
      return this._addCheck({
        kind: "multipleOf",
        value,
        message: errorUtil.toString(message)
      });
    }
    finite(message) {
      return this._addCheck({
        kind: "finite",
        message: errorUtil.toString(message)
      });
    }
    safe(message) {
      return this._addCheck({
        kind: "min",
        inclusive: true,
        value: Number.MIN_SAFE_INTEGER,
        message: errorUtil.toString(message)
      })._addCheck({
        kind: "max",
        inclusive: true,
        value: Number.MAX_SAFE_INTEGER,
        message: errorUtil.toString(message)
      });
    }
    get minValue() {
      let min = null;
      for (const ch of this._def.checks) {
        if (ch.kind === "min") {
          if (min === null || ch.value > min)
            min = ch.value;
        }
      }
      return min;
    }
    get maxValue() {
      let max = null;
      for (const ch of this._def.checks) {
        if (ch.kind === "max") {
          if (max === null || ch.value < max)
            max = ch.value;
        }
      }
      return max;
    }
    get isInt() {
      return !!this._def.checks.find((ch) => ch.kind === "int" || ch.kind === "multipleOf" && util.isInteger(ch.value));
    }
    get isFinite() {
      let max = null;
      let min = null;
      for (const ch of this._def.checks) {
        if (ch.kind === "finite" || ch.kind === "int" || ch.kind === "multipleOf") {
          return true;
        } else if (ch.kind === "min") {
          if (min === null || ch.value > min)
            min = ch.value;
        } else if (ch.kind === "max") {
          if (max === null || ch.value < max)
            max = ch.value;
        }
      }
      return Number.isFinite(min) && Number.isFinite(max);
    }
  };
  ZodNumber.create = (params) => {
    return new ZodNumber({
      checks: [],
      typeName: ZodFirstPartyTypeKind.ZodNumber,
      coerce: params?.coerce || false,
      ...processCreateParams(params)
    });
  };
  var ZodBigInt = class _ZodBigInt extends ZodType {
    constructor() {
      super(...arguments);
      this.min = this.gte;
      this.max = this.lte;
    }
    _parse(input) {
      if (this._def.coerce) {
        try {
          input.data = BigInt(input.data);
        } catch {
          return this._getInvalidInput(input);
        }
      }
      const parsedType = this._getType(input);
      if (parsedType !== ZodParsedType.bigint) {
        return this._getInvalidInput(input);
      }
      let ctx = void 0;
      const status = new ParseStatus();
      for (const check of this._def.checks) {
        if (check.kind === "min") {
          const tooSmall = check.inclusive ? input.data < check.value : input.data <= check.value;
          if (tooSmall) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.too_small,
              type: "bigint",
              minimum: check.value,
              inclusive: check.inclusive,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "max") {
          const tooBig = check.inclusive ? input.data > check.value : input.data >= check.value;
          if (tooBig) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.too_big,
              type: "bigint",
              maximum: check.value,
              inclusive: check.inclusive,
              message: check.message
            });
            status.dirty();
          }
        } else if (check.kind === "multipleOf") {
          if (input.data % check.value !== BigInt(0)) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.not_multiple_of,
              multipleOf: check.value,
              message: check.message
            });
            status.dirty();
          }
        } else {
          util.assertNever(check);
        }
      }
      return { status: status.value, value: input.data };
    }
    _getInvalidInput(input) {
      const ctx = this._getOrReturnCtx(input);
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.bigint,
        received: ctx.parsedType
      });
      return INVALID;
    }
    gte(value, message) {
      return this.setLimit("min", value, true, errorUtil.toString(message));
    }
    gt(value, message) {
      return this.setLimit("min", value, false, errorUtil.toString(message));
    }
    lte(value, message) {
      return this.setLimit("max", value, true, errorUtil.toString(message));
    }
    lt(value, message) {
      return this.setLimit("max", value, false, errorUtil.toString(message));
    }
    setLimit(kind, value, inclusive, message) {
      return new _ZodBigInt({
        ...this._def,
        checks: [
          ...this._def.checks,
          {
            kind,
            value,
            inclusive,
            message: errorUtil.toString(message)
          }
        ]
      });
    }
    _addCheck(check) {
      return new _ZodBigInt({
        ...this._def,
        checks: [...this._def.checks, check]
      });
    }
    positive(message) {
      return this._addCheck({
        kind: "min",
        value: BigInt(0),
        inclusive: false,
        message: errorUtil.toString(message)
      });
    }
    negative(message) {
      return this._addCheck({
        kind: "max",
        value: BigInt(0),
        inclusive: false,
        message: errorUtil.toString(message)
      });
    }
    nonpositive(message) {
      return this._addCheck({
        kind: "max",
        value: BigInt(0),
        inclusive: true,
        message: errorUtil.toString(message)
      });
    }
    nonnegative(message) {
      return this._addCheck({
        kind: "min",
        value: BigInt(0),
        inclusive: true,
        message: errorUtil.toString(message)
      });
    }
    multipleOf(value, message) {
      return this._addCheck({
        kind: "multipleOf",
        value,
        message: errorUtil.toString(message)
      });
    }
    get minValue() {
      let min = null;
      for (const ch of this._def.checks) {
        if (ch.kind === "min") {
          if (min === null || ch.value > min)
            min = ch.value;
        }
      }
      return min;
    }
    get maxValue() {
      let max = null;
      for (const ch of this._def.checks) {
        if (ch.kind === "max") {
          if (max === null || ch.value < max)
            max = ch.value;
        }
      }
      return max;
    }
  };
  ZodBigInt.create = (params) => {
    return new ZodBigInt({
      checks: [],
      typeName: ZodFirstPartyTypeKind.ZodBigInt,
      coerce: params?.coerce ?? false,
      ...processCreateParams(params)
    });
  };
  var ZodBoolean = class extends ZodType {
    _parse(input) {
      if (this._def.coerce) {
        input.data = Boolean(input.data);
      }
      const parsedType = this._getType(input);
      if (parsedType !== ZodParsedType.boolean) {
        const ctx = this._getOrReturnCtx(input);
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.boolean,
          received: ctx.parsedType
        });
        return INVALID;
      }
      return OK(input.data);
    }
  };
  ZodBoolean.create = (params) => {
    return new ZodBoolean({
      typeName: ZodFirstPartyTypeKind.ZodBoolean,
      coerce: params?.coerce || false,
      ...processCreateParams(params)
    });
  };
  var ZodDate = class _ZodDate extends ZodType {
    _parse(input) {
      if (this._def.coerce) {
        input.data = new Date(input.data);
      }
      const parsedType = this._getType(input);
      if (parsedType !== ZodParsedType.date) {
        const ctx2 = this._getOrReturnCtx(input);
        addIssueToContext(ctx2, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.date,
          received: ctx2.parsedType
        });
        return INVALID;
      }
      if (Number.isNaN(input.data.getTime())) {
        const ctx2 = this._getOrReturnCtx(input);
        addIssueToContext(ctx2, {
          code: ZodIssueCode.invalid_date
        });
        return INVALID;
      }
      const status = new ParseStatus();
      let ctx = void 0;
      for (const check of this._def.checks) {
        if (check.kind === "min") {
          if (input.data.getTime() < check.value) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.too_small,
              message: check.message,
              inclusive: true,
              exact: false,
              minimum: check.value,
              type: "date"
            });
            status.dirty();
          }
        } else if (check.kind === "max") {
          if (input.data.getTime() > check.value) {
            ctx = this._getOrReturnCtx(input, ctx);
            addIssueToContext(ctx, {
              code: ZodIssueCode.too_big,
              message: check.message,
              inclusive: true,
              exact: false,
              maximum: check.value,
              type: "date"
            });
            status.dirty();
          }
        } else {
          util.assertNever(check);
        }
      }
      return {
        status: status.value,
        value: new Date(input.data.getTime())
      };
    }
    _addCheck(check) {
      return new _ZodDate({
        ...this._def,
        checks: [...this._def.checks, check]
      });
    }
    min(minDate, message) {
      return this._addCheck({
        kind: "min",
        value: minDate.getTime(),
        message: errorUtil.toString(message)
      });
    }
    max(maxDate, message) {
      return this._addCheck({
        kind: "max",
        value: maxDate.getTime(),
        message: errorUtil.toString(message)
      });
    }
    get minDate() {
      let min = null;
      for (const ch of this._def.checks) {
        if (ch.kind === "min") {
          if (min === null || ch.value > min)
            min = ch.value;
        }
      }
      return min != null ? new Date(min) : null;
    }
    get maxDate() {
      let max = null;
      for (const ch of this._def.checks) {
        if (ch.kind === "max") {
          if (max === null || ch.value < max)
            max = ch.value;
        }
      }
      return max != null ? new Date(max) : null;
    }
  };
  ZodDate.create = (params) => {
    return new ZodDate({
      checks: [],
      coerce: params?.coerce || false,
      typeName: ZodFirstPartyTypeKind.ZodDate,
      ...processCreateParams(params)
    });
  };
  var ZodSymbol = class extends ZodType {
    _parse(input) {
      const parsedType = this._getType(input);
      if (parsedType !== ZodParsedType.symbol) {
        const ctx = this._getOrReturnCtx(input);
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.symbol,
          received: ctx.parsedType
        });
        return INVALID;
      }
      return OK(input.data);
    }
  };
  ZodSymbol.create = (params) => {
    return new ZodSymbol({
      typeName: ZodFirstPartyTypeKind.ZodSymbol,
      ...processCreateParams(params)
    });
  };
  var ZodUndefined = class extends ZodType {
    _parse(input) {
      const parsedType = this._getType(input);
      if (parsedType !== ZodParsedType.undefined) {
        const ctx = this._getOrReturnCtx(input);
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.undefined,
          received: ctx.parsedType
        });
        return INVALID;
      }
      return OK(input.data);
    }
  };
  ZodUndefined.create = (params) => {
    return new ZodUndefined({
      typeName: ZodFirstPartyTypeKind.ZodUndefined,
      ...processCreateParams(params)
    });
  };
  var ZodNull = class extends ZodType {
    _parse(input) {
      const parsedType = this._getType(input);
      if (parsedType !== ZodParsedType.null) {
        const ctx = this._getOrReturnCtx(input);
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.null,
          received: ctx.parsedType
        });
        return INVALID;
      }
      return OK(input.data);
    }
  };
  ZodNull.create = (params) => {
    return new ZodNull({
      typeName: ZodFirstPartyTypeKind.ZodNull,
      ...processCreateParams(params)
    });
  };
  var ZodAny = class extends ZodType {
    constructor() {
      super(...arguments);
      this._any = true;
    }
    _parse(input) {
      return OK(input.data);
    }
  };
  ZodAny.create = (params) => {
    return new ZodAny({
      typeName: ZodFirstPartyTypeKind.ZodAny,
      ...processCreateParams(params)
    });
  };
  var ZodUnknown = class extends ZodType {
    constructor() {
      super(...arguments);
      this._unknown = true;
    }
    _parse(input) {
      return OK(input.data);
    }
  };
  ZodUnknown.create = (params) => {
    return new ZodUnknown({
      typeName: ZodFirstPartyTypeKind.ZodUnknown,
      ...processCreateParams(params)
    });
  };
  var ZodNever = class extends ZodType {
    _parse(input) {
      const ctx = this._getOrReturnCtx(input);
      addIssueToContext(ctx, {
        code: ZodIssueCode.invalid_type,
        expected: ZodParsedType.never,
        received: ctx.parsedType
      });
      return INVALID;
    }
  };
  ZodNever.create = (params) => {
    return new ZodNever({
      typeName: ZodFirstPartyTypeKind.ZodNever,
      ...processCreateParams(params)
    });
  };
  var ZodVoid = class extends ZodType {
    _parse(input) {
      const parsedType = this._getType(input);
      if (parsedType !== ZodParsedType.undefined) {
        const ctx = this._getOrReturnCtx(input);
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.void,
          received: ctx.parsedType
        });
        return INVALID;
      }
      return OK(input.data);
    }
  };
  ZodVoid.create = (params) => {
    return new ZodVoid({
      typeName: ZodFirstPartyTypeKind.ZodVoid,
      ...processCreateParams(params)
    });
  };
  var ZodArray = class _ZodArray extends ZodType {
    _parse(input) {
      const { ctx, status } = this._processInputParams(input);
      const def = this._def;
      if (ctx.parsedType !== ZodParsedType.array) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.array,
          received: ctx.parsedType
        });
        return INVALID;
      }
      if (def.exactLength !== null) {
        const tooBig = ctx.data.length > def.exactLength.value;
        const tooSmall = ctx.data.length < def.exactLength.value;
        if (tooBig || tooSmall) {
          addIssueToContext(ctx, {
            code: tooBig ? ZodIssueCode.too_big : ZodIssueCode.too_small,
            minimum: tooSmall ? def.exactLength.value : void 0,
            maximum: tooBig ? def.exactLength.value : void 0,
            type: "array",
            inclusive: true,
            exact: true,
            message: def.exactLength.message
          });
          status.dirty();
        }
      }
      if (def.minLength !== null) {
        if (ctx.data.length < def.minLength.value) {
          addIssueToContext(ctx, {
            code: ZodIssueCode.too_small,
            minimum: def.minLength.value,
            type: "array",
            inclusive: true,
            exact: false,
            message: def.minLength.message
          });
          status.dirty();
        }
      }
      if (def.maxLength !== null) {
        if (ctx.data.length > def.maxLength.value) {
          addIssueToContext(ctx, {
            code: ZodIssueCode.too_big,
            maximum: def.maxLength.value,
            type: "array",
            inclusive: true,
            exact: false,
            message: def.maxLength.message
          });
          status.dirty();
        }
      }
      if (ctx.common.async) {
        return Promise.all([...ctx.data].map((item, i) => {
          return def.type._parseAsync(new ParseInputLazyPath(ctx, item, ctx.path, i));
        })).then((result2) => {
          return ParseStatus.mergeArray(status, result2);
        });
      }
      const result = [...ctx.data].map((item, i) => {
        return def.type._parseSync(new ParseInputLazyPath(ctx, item, ctx.path, i));
      });
      return ParseStatus.mergeArray(status, result);
    }
    get element() {
      return this._def.type;
    }
    min(minLength, message) {
      return new _ZodArray({
        ...this._def,
        minLength: { value: minLength, message: errorUtil.toString(message) }
      });
    }
    max(maxLength, message) {
      return new _ZodArray({
        ...this._def,
        maxLength: { value: maxLength, message: errorUtil.toString(message) }
      });
    }
    length(len, message) {
      return new _ZodArray({
        ...this._def,
        exactLength: { value: len, message: errorUtil.toString(message) }
      });
    }
    nonempty(message) {
      return this.min(1, message);
    }
  };
  ZodArray.create = (schema, params) => {
    return new ZodArray({
      type: schema,
      minLength: null,
      maxLength: null,
      exactLength: null,
      typeName: ZodFirstPartyTypeKind.ZodArray,
      ...processCreateParams(params)
    });
  };
  function deepPartialify(schema) {
    if (schema instanceof ZodObject) {
      const newShape = {};
      for (const key2 in schema.shape) {
        const fieldSchema = schema.shape[key2];
        newShape[key2] = ZodOptional.create(deepPartialify(fieldSchema));
      }
      return new ZodObject({
        ...schema._def,
        shape: () => newShape
      });
    } else if (schema instanceof ZodArray) {
      return new ZodArray({
        ...schema._def,
        type: deepPartialify(schema.element)
      });
    } else if (schema instanceof ZodOptional) {
      return ZodOptional.create(deepPartialify(schema.unwrap()));
    } else if (schema instanceof ZodNullable) {
      return ZodNullable.create(deepPartialify(schema.unwrap()));
    } else if (schema instanceof ZodTuple) {
      return ZodTuple.create(schema.items.map((item) => deepPartialify(item)));
    } else {
      return schema;
    }
  }
  var ZodObject = class _ZodObject extends ZodType {
    constructor() {
      super(...arguments);
      this._cached = null;
      this.nonstrict = this.passthrough;
      this.augment = this.extend;
    }
    _getCached() {
      if (this._cached !== null)
        return this._cached;
      const shape = this._def.shape();
      const keys = util.objectKeys(shape);
      this._cached = { shape, keys };
      return this._cached;
    }
    _parse(input) {
      const parsedType = this._getType(input);
      if (parsedType !== ZodParsedType.object) {
        const ctx2 = this._getOrReturnCtx(input);
        addIssueToContext(ctx2, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.object,
          received: ctx2.parsedType
        });
        return INVALID;
      }
      const { status, ctx } = this._processInputParams(input);
      const { shape, keys: shapeKeys } = this._getCached();
      const extraKeys = [];
      if (!(this._def.catchall instanceof ZodNever && this._def.unknownKeys === "strip")) {
        for (const key2 in ctx.data) {
          if (!shapeKeys.includes(key2)) {
            extraKeys.push(key2);
          }
        }
      }
      const pairs = [];
      for (const key2 of shapeKeys) {
        const keyValidator = shape[key2];
        const value = ctx.data[key2];
        pairs.push({
          key: { status: "valid", value: key2 },
          value: keyValidator._parse(new ParseInputLazyPath(ctx, value, ctx.path, key2)),
          alwaysSet: key2 in ctx.data
        });
      }
      if (this._def.catchall instanceof ZodNever) {
        const unknownKeys = this._def.unknownKeys;
        if (unknownKeys === "passthrough") {
          for (const key2 of extraKeys) {
            pairs.push({
              key: { status: "valid", value: key2 },
              value: { status: "valid", value: ctx.data[key2] }
            });
          }
        } else if (unknownKeys === "strict") {
          if (extraKeys.length > 0) {
            addIssueToContext(ctx, {
              code: ZodIssueCode.unrecognized_keys,
              keys: extraKeys
            });
            status.dirty();
          }
        } else if (unknownKeys === "strip") {
        } else {
          throw new Error(`Internal ZodObject error: invalid unknownKeys value.`);
        }
      } else {
        const catchall = this._def.catchall;
        for (const key2 of extraKeys) {
          const value = ctx.data[key2];
          pairs.push({
            key: { status: "valid", value: key2 },
            value: catchall._parse(
              new ParseInputLazyPath(ctx, value, ctx.path, key2)
              //, ctx.child(key), value, getParsedType(value)
            ),
            alwaysSet: key2 in ctx.data
          });
        }
      }
      if (ctx.common.async) {
        return Promise.resolve().then(async () => {
          const syncPairs = [];
          for (const pair of pairs) {
            const key2 = await pair.key;
            const value = await pair.value;
            syncPairs.push({
              key: key2,
              value,
              alwaysSet: pair.alwaysSet
            });
          }
          return syncPairs;
        }).then((syncPairs) => {
          return ParseStatus.mergeObjectSync(status, syncPairs);
        });
      } else {
        return ParseStatus.mergeObjectSync(status, pairs);
      }
    }
    get shape() {
      return this._def.shape();
    }
    strict(message) {
      errorUtil.errToObj;
      return new _ZodObject({
        ...this._def,
        unknownKeys: "strict",
        ...message !== void 0 ? {
          errorMap: (issue, ctx) => {
            const defaultError = this._def.errorMap?.(issue, ctx).message ?? ctx.defaultError;
            if (issue.code === "unrecognized_keys")
              return {
                message: errorUtil.errToObj(message).message ?? defaultError
              };
            return {
              message: defaultError
            };
          }
        } : {}
      });
    }
    strip() {
      return new _ZodObject({
        ...this._def,
        unknownKeys: "strip"
      });
    }
    passthrough() {
      return new _ZodObject({
        ...this._def,
        unknownKeys: "passthrough"
      });
    }
    // const AugmentFactory =
    //   <Def extends ZodObjectDef>(def: Def) =>
    //   <Augmentation extends ZodRawShape>(
    //     augmentation: Augmentation
    //   ): ZodObject<
    //     extendShape<ReturnType<Def["shape"]>, Augmentation>,
    //     Def["unknownKeys"],
    //     Def["catchall"]
    //   > => {
    //     return new ZodObject({
    //       ...def,
    //       shape: () => ({
    //         ...def.shape(),
    //         ...augmentation,
    //       }),
    //     }) as any;
    //   };
    extend(augmentation) {
      return new _ZodObject({
        ...this._def,
        shape: () => ({
          ...this._def.shape(),
          ...augmentation
        })
      });
    }
    /**
     * Prior to zod@1.0.12 there was a bug in the
     * inferred type of merged objects. Please
     * upgrade if you are experiencing issues.
     */
    merge(merging) {
      const merged = new _ZodObject({
        unknownKeys: merging._def.unknownKeys,
        catchall: merging._def.catchall,
        shape: () => ({
          ...this._def.shape(),
          ...merging._def.shape()
        }),
        typeName: ZodFirstPartyTypeKind.ZodObject
      });
      return merged;
    }
    // merge<
    //   Incoming extends AnyZodObject,
    //   Augmentation extends Incoming["shape"],
    //   NewOutput extends {
    //     [k in keyof Augmentation | keyof Output]: k extends keyof Augmentation
    //       ? Augmentation[k]["_output"]
    //       : k extends keyof Output
    //       ? Output[k]
    //       : never;
    //   },
    //   NewInput extends {
    //     [k in keyof Augmentation | keyof Input]: k extends keyof Augmentation
    //       ? Augmentation[k]["_input"]
    //       : k extends keyof Input
    //       ? Input[k]
    //       : never;
    //   }
    // >(
    //   merging: Incoming
    // ): ZodObject<
    //   extendShape<T, ReturnType<Incoming["_def"]["shape"]>>,
    //   Incoming["_def"]["unknownKeys"],
    //   Incoming["_def"]["catchall"],
    //   NewOutput,
    //   NewInput
    // > {
    //   const merged: any = new ZodObject({
    //     unknownKeys: merging._def.unknownKeys,
    //     catchall: merging._def.catchall,
    //     shape: () =>
    //       objectUtil.mergeShapes(this._def.shape(), merging._def.shape()),
    //     typeName: ZodFirstPartyTypeKind.ZodObject,
    //   }) as any;
    //   return merged;
    // }
    setKey(key2, schema) {
      return this.augment({ [key2]: schema });
    }
    // merge<Incoming extends AnyZodObject>(
    //   merging: Incoming
    // ): //ZodObject<T & Incoming["_shape"], UnknownKeys, Catchall> = (merging) => {
    // ZodObject<
    //   extendShape<T, ReturnType<Incoming["_def"]["shape"]>>,
    //   Incoming["_def"]["unknownKeys"],
    //   Incoming["_def"]["catchall"]
    // > {
    //   // const mergedShape = objectUtil.mergeShapes(
    //   //   this._def.shape(),
    //   //   merging._def.shape()
    //   // );
    //   const merged: any = new ZodObject({
    //     unknownKeys: merging._def.unknownKeys,
    //     catchall: merging._def.catchall,
    //     shape: () =>
    //       objectUtil.mergeShapes(this._def.shape(), merging._def.shape()),
    //     typeName: ZodFirstPartyTypeKind.ZodObject,
    //   }) as any;
    //   return merged;
    // }
    catchall(index) {
      return new _ZodObject({
        ...this._def,
        catchall: index
      });
    }
    pick(mask) {
      const shape = {};
      for (const key2 of util.objectKeys(mask)) {
        if (mask[key2] && this.shape[key2]) {
          shape[key2] = this.shape[key2];
        }
      }
      return new _ZodObject({
        ...this._def,
        shape: () => shape
      });
    }
    omit(mask) {
      const shape = {};
      for (const key2 of util.objectKeys(this.shape)) {
        if (!mask[key2]) {
          shape[key2] = this.shape[key2];
        }
      }
      return new _ZodObject({
        ...this._def,
        shape: () => shape
      });
    }
    /**
     * @deprecated
     */
    deepPartial() {
      return deepPartialify(this);
    }
    partial(mask) {
      const newShape = {};
      for (const key2 of util.objectKeys(this.shape)) {
        const fieldSchema = this.shape[key2];
        if (mask && !mask[key2]) {
          newShape[key2] = fieldSchema;
        } else {
          newShape[key2] = fieldSchema.optional();
        }
      }
      return new _ZodObject({
        ...this._def,
        shape: () => newShape
      });
    }
    required(mask) {
      const newShape = {};
      for (const key2 of util.objectKeys(this.shape)) {
        if (mask && !mask[key2]) {
          newShape[key2] = this.shape[key2];
        } else {
          const fieldSchema = this.shape[key2];
          let newField = fieldSchema;
          while (newField instanceof ZodOptional) {
            newField = newField._def.innerType;
          }
          newShape[key2] = newField;
        }
      }
      return new _ZodObject({
        ...this._def,
        shape: () => newShape
      });
    }
    keyof() {
      return createZodEnum(util.objectKeys(this.shape));
    }
  };
  ZodObject.create = (shape, params) => {
    return new ZodObject({
      shape: () => shape,
      unknownKeys: "strip",
      catchall: ZodNever.create(),
      typeName: ZodFirstPartyTypeKind.ZodObject,
      ...processCreateParams(params)
    });
  };
  ZodObject.strictCreate = (shape, params) => {
    return new ZodObject({
      shape: () => shape,
      unknownKeys: "strict",
      catchall: ZodNever.create(),
      typeName: ZodFirstPartyTypeKind.ZodObject,
      ...processCreateParams(params)
    });
  };
  ZodObject.lazycreate = (shape, params) => {
    return new ZodObject({
      shape,
      unknownKeys: "strip",
      catchall: ZodNever.create(),
      typeName: ZodFirstPartyTypeKind.ZodObject,
      ...processCreateParams(params)
    });
  };
  var ZodUnion = class extends ZodType {
    _parse(input) {
      const { ctx } = this._processInputParams(input);
      const options = this._def.options;
      function handleResults(results) {
        for (const result of results) {
          if (result.result.status === "valid") {
            return result.result;
          }
        }
        for (const result of results) {
          if (result.result.status === "dirty") {
            ctx.common.issues.push(...result.ctx.common.issues);
            return result.result;
          }
        }
        const unionErrors = results.map((result) => new ZodError(result.ctx.common.issues));
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_union,
          unionErrors
        });
        return INVALID;
      }
      if (ctx.common.async) {
        return Promise.all(options.map(async (option) => {
          const childCtx = {
            ...ctx,
            common: {
              ...ctx.common,
              issues: []
            },
            parent: null
          };
          return {
            result: await option._parseAsync({
              data: ctx.data,
              path: ctx.path,
              parent: childCtx
            }),
            ctx: childCtx
          };
        })).then(handleResults);
      } else {
        let dirty = void 0;
        const issues = [];
        for (const option of options) {
          const childCtx = {
            ...ctx,
            common: {
              ...ctx.common,
              issues: []
            },
            parent: null
          };
          const result = option._parseSync({
            data: ctx.data,
            path: ctx.path,
            parent: childCtx
          });
          if (result.status === "valid") {
            return result;
          } else if (result.status === "dirty" && !dirty) {
            dirty = { result, ctx: childCtx };
          }
          if (childCtx.common.issues.length) {
            issues.push(childCtx.common.issues);
          }
        }
        if (dirty) {
          ctx.common.issues.push(...dirty.ctx.common.issues);
          return dirty.result;
        }
        const unionErrors = issues.map((issues2) => new ZodError(issues2));
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_union,
          unionErrors
        });
        return INVALID;
      }
    }
    get options() {
      return this._def.options;
    }
  };
  ZodUnion.create = (types, params) => {
    return new ZodUnion({
      options: types,
      typeName: ZodFirstPartyTypeKind.ZodUnion,
      ...processCreateParams(params)
    });
  };
  var getDiscriminator = (type) => {
    if (type instanceof ZodLazy) {
      return getDiscriminator(type.schema);
    } else if (type instanceof ZodEffects) {
      return getDiscriminator(type.innerType());
    } else if (type instanceof ZodLiteral) {
      return [type.value];
    } else if (type instanceof ZodEnum) {
      return type.options;
    } else if (type instanceof ZodNativeEnum) {
      return util.objectValues(type.enum);
    } else if (type instanceof ZodDefault) {
      return getDiscriminator(type._def.innerType);
    } else if (type instanceof ZodUndefined) {
      return [void 0];
    } else if (type instanceof ZodNull) {
      return [null];
    } else if (type instanceof ZodOptional) {
      return [void 0, ...getDiscriminator(type.unwrap())];
    } else if (type instanceof ZodNullable) {
      return [null, ...getDiscriminator(type.unwrap())];
    } else if (type instanceof ZodBranded) {
      return getDiscriminator(type.unwrap());
    } else if (type instanceof ZodReadonly) {
      return getDiscriminator(type.unwrap());
    } else if (type instanceof ZodCatch) {
      return getDiscriminator(type._def.innerType);
    } else {
      return [];
    }
  };
  var ZodDiscriminatedUnion = class _ZodDiscriminatedUnion extends ZodType {
    _parse(input) {
      const { ctx } = this._processInputParams(input);
      if (ctx.parsedType !== ZodParsedType.object) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.object,
          received: ctx.parsedType
        });
        return INVALID;
      }
      const discriminator = this.discriminator;
      const discriminatorValue = ctx.data[discriminator];
      const option = this.optionsMap.get(discriminatorValue);
      if (!option) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_union_discriminator,
          options: Array.from(this.optionsMap.keys()),
          path: [discriminator]
        });
        return INVALID;
      }
      if (ctx.common.async) {
        return option._parseAsync({
          data: ctx.data,
          path: ctx.path,
          parent: ctx
        });
      } else {
        return option._parseSync({
          data: ctx.data,
          path: ctx.path,
          parent: ctx
        });
      }
    }
    get discriminator() {
      return this._def.discriminator;
    }
    get options() {
      return this._def.options;
    }
    get optionsMap() {
      return this._def.optionsMap;
    }
    /**
     * The constructor of the discriminated union schema. Its behaviour is very similar to that of the normal z.union() constructor.
     * However, it only allows a union of objects, all of which need to share a discriminator property. This property must
     * have a different value for each object in the union.
     * @param discriminator the name of the discriminator property
     * @param types an array of object schemas
     * @param params
     */
    static create(discriminator, options, params) {
      const optionsMap = /* @__PURE__ */ new Map();
      for (const type of options) {
        const discriminatorValues = getDiscriminator(type.shape[discriminator]);
        if (!discriminatorValues.length) {
          throw new Error(`A discriminator value for key \`${discriminator}\` could not be extracted from all schema options`);
        }
        for (const value of discriminatorValues) {
          if (optionsMap.has(value)) {
            throw new Error(`Discriminator property ${String(discriminator)} has duplicate value ${String(value)}`);
          }
          optionsMap.set(value, type);
        }
      }
      return new _ZodDiscriminatedUnion({
        typeName: ZodFirstPartyTypeKind.ZodDiscriminatedUnion,
        discriminator,
        options,
        optionsMap,
        ...processCreateParams(params)
      });
    }
  };
  function mergeValues(a, b) {
    const aType = getParsedType(a);
    const bType = getParsedType(b);
    if (a === b) {
      return { valid: true, data: a };
    } else if (aType === ZodParsedType.object && bType === ZodParsedType.object) {
      const bKeys = util.objectKeys(b);
      const sharedKeys = util.objectKeys(a).filter((key2) => bKeys.indexOf(key2) !== -1);
      const newObj = { ...a, ...b };
      for (const key2 of sharedKeys) {
        const sharedValue = mergeValues(a[key2], b[key2]);
        if (!sharedValue.valid) {
          return { valid: false };
        }
        newObj[key2] = sharedValue.data;
      }
      return { valid: true, data: newObj };
    } else if (aType === ZodParsedType.array && bType === ZodParsedType.array) {
      if (a.length !== b.length) {
        return { valid: false };
      }
      const newArray = [];
      for (let index = 0; index < a.length; index++) {
        const itemA = a[index];
        const itemB = b[index];
        const sharedValue = mergeValues(itemA, itemB);
        if (!sharedValue.valid) {
          return { valid: false };
        }
        newArray.push(sharedValue.data);
      }
      return { valid: true, data: newArray };
    } else if (aType === ZodParsedType.date && bType === ZodParsedType.date && +a === +b) {
      return { valid: true, data: a };
    } else {
      return { valid: false };
    }
  }
  var ZodIntersection = class extends ZodType {
    _parse(input) {
      const { status, ctx } = this._processInputParams(input);
      const handleParsed = (parsedLeft, parsedRight) => {
        if (isAborted(parsedLeft) || isAborted(parsedRight)) {
          return INVALID;
        }
        const merged = mergeValues(parsedLeft.value, parsedRight.value);
        if (!merged.valid) {
          addIssueToContext(ctx, {
            code: ZodIssueCode.invalid_intersection_types
          });
          return INVALID;
        }
        if (isDirty(parsedLeft) || isDirty(parsedRight)) {
          status.dirty();
        }
        return { status: status.value, value: merged.data };
      };
      if (ctx.common.async) {
        return Promise.all([
          this._def.left._parseAsync({
            data: ctx.data,
            path: ctx.path,
            parent: ctx
          }),
          this._def.right._parseAsync({
            data: ctx.data,
            path: ctx.path,
            parent: ctx
          })
        ]).then(([left, right]) => handleParsed(left, right));
      } else {
        return handleParsed(this._def.left._parseSync({
          data: ctx.data,
          path: ctx.path,
          parent: ctx
        }), this._def.right._parseSync({
          data: ctx.data,
          path: ctx.path,
          parent: ctx
        }));
      }
    }
  };
  ZodIntersection.create = (left, right, params) => {
    return new ZodIntersection({
      left,
      right,
      typeName: ZodFirstPartyTypeKind.ZodIntersection,
      ...processCreateParams(params)
    });
  };
  var ZodTuple = class _ZodTuple extends ZodType {
    _parse(input) {
      const { status, ctx } = this._processInputParams(input);
      if (ctx.parsedType !== ZodParsedType.array) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.array,
          received: ctx.parsedType
        });
        return INVALID;
      }
      if (ctx.data.length < this._def.items.length) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.too_small,
          minimum: this._def.items.length,
          inclusive: true,
          exact: false,
          type: "array"
        });
        return INVALID;
      }
      const rest = this._def.rest;
      if (!rest && ctx.data.length > this._def.items.length) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.too_big,
          maximum: this._def.items.length,
          inclusive: true,
          exact: false,
          type: "array"
        });
        status.dirty();
      }
      const items = [...ctx.data].map((item, itemIndex) => {
        const schema = this._def.items[itemIndex] || this._def.rest;
        if (!schema)
          return null;
        return schema._parse(new ParseInputLazyPath(ctx, item, ctx.path, itemIndex));
      }).filter((x) => !!x);
      if (ctx.common.async) {
        return Promise.all(items).then((results) => {
          return ParseStatus.mergeArray(status, results);
        });
      } else {
        return ParseStatus.mergeArray(status, items);
      }
    }
    get items() {
      return this._def.items;
    }
    rest(rest) {
      return new _ZodTuple({
        ...this._def,
        rest
      });
    }
  };
  ZodTuple.create = (schemas, params) => {
    if (!Array.isArray(schemas)) {
      throw new Error("You must pass an array of schemas to z.tuple([ ... ])");
    }
    return new ZodTuple({
      items: schemas,
      typeName: ZodFirstPartyTypeKind.ZodTuple,
      rest: null,
      ...processCreateParams(params)
    });
  };
  var ZodRecord = class _ZodRecord extends ZodType {
    get keySchema() {
      return this._def.keyType;
    }
    get valueSchema() {
      return this._def.valueType;
    }
    _parse(input) {
      const { status, ctx } = this._processInputParams(input);
      if (ctx.parsedType !== ZodParsedType.object) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.object,
          received: ctx.parsedType
        });
        return INVALID;
      }
      const pairs = [];
      const keyType = this._def.keyType;
      const valueType = this._def.valueType;
      for (const key2 in ctx.data) {
        pairs.push({
          key: keyType._parse(new ParseInputLazyPath(ctx, key2, ctx.path, key2)),
          value: valueType._parse(new ParseInputLazyPath(ctx, ctx.data[key2], ctx.path, key2)),
          alwaysSet: key2 in ctx.data
        });
      }
      if (ctx.common.async) {
        return ParseStatus.mergeObjectAsync(status, pairs);
      } else {
        return ParseStatus.mergeObjectSync(status, pairs);
      }
    }
    get element() {
      return this._def.valueType;
    }
    static create(first, second, third) {
      if (second instanceof ZodType) {
        return new _ZodRecord({
          keyType: first,
          valueType: second,
          typeName: ZodFirstPartyTypeKind.ZodRecord,
          ...processCreateParams(third)
        });
      }
      return new _ZodRecord({
        keyType: ZodString.create(),
        valueType: first,
        typeName: ZodFirstPartyTypeKind.ZodRecord,
        ...processCreateParams(second)
      });
    }
  };
  var ZodMap = class extends ZodType {
    get keySchema() {
      return this._def.keyType;
    }
    get valueSchema() {
      return this._def.valueType;
    }
    _parse(input) {
      const { status, ctx } = this._processInputParams(input);
      if (ctx.parsedType !== ZodParsedType.map) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.map,
          received: ctx.parsedType
        });
        return INVALID;
      }
      const keyType = this._def.keyType;
      const valueType = this._def.valueType;
      const pairs = [...ctx.data.entries()].map(([key2, value], index) => {
        return {
          key: keyType._parse(new ParseInputLazyPath(ctx, key2, ctx.path, [index, "key"])),
          value: valueType._parse(new ParseInputLazyPath(ctx, value, ctx.path, [index, "value"]))
        };
      });
      if (ctx.common.async) {
        const finalMap = /* @__PURE__ */ new Map();
        return Promise.resolve().then(async () => {
          for (const pair of pairs) {
            const key2 = await pair.key;
            const value = await pair.value;
            if (key2.status === "aborted" || value.status === "aborted") {
              return INVALID;
            }
            if (key2.status === "dirty" || value.status === "dirty") {
              status.dirty();
            }
            finalMap.set(key2.value, value.value);
          }
          return { status: status.value, value: finalMap };
        });
      } else {
        const finalMap = /* @__PURE__ */ new Map();
        for (const pair of pairs) {
          const key2 = pair.key;
          const value = pair.value;
          if (key2.status === "aborted" || value.status === "aborted") {
            return INVALID;
          }
          if (key2.status === "dirty" || value.status === "dirty") {
            status.dirty();
          }
          finalMap.set(key2.value, value.value);
        }
        return { status: status.value, value: finalMap };
      }
    }
  };
  ZodMap.create = (keyType, valueType, params) => {
    return new ZodMap({
      valueType,
      keyType,
      typeName: ZodFirstPartyTypeKind.ZodMap,
      ...processCreateParams(params)
    });
  };
  var ZodSet = class _ZodSet extends ZodType {
    _parse(input) {
      const { status, ctx } = this._processInputParams(input);
      if (ctx.parsedType !== ZodParsedType.set) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.set,
          received: ctx.parsedType
        });
        return INVALID;
      }
      const def = this._def;
      if (def.minSize !== null) {
        if (ctx.data.size < def.minSize.value) {
          addIssueToContext(ctx, {
            code: ZodIssueCode.too_small,
            minimum: def.minSize.value,
            type: "set",
            inclusive: true,
            exact: false,
            message: def.minSize.message
          });
          status.dirty();
        }
      }
      if (def.maxSize !== null) {
        if (ctx.data.size > def.maxSize.value) {
          addIssueToContext(ctx, {
            code: ZodIssueCode.too_big,
            maximum: def.maxSize.value,
            type: "set",
            inclusive: true,
            exact: false,
            message: def.maxSize.message
          });
          status.dirty();
        }
      }
      const valueType = this._def.valueType;
      function finalizeSet(elements2) {
        const parsedSet = /* @__PURE__ */ new Set();
        for (const element of elements2) {
          if (element.status === "aborted")
            return INVALID;
          if (element.status === "dirty")
            status.dirty();
          parsedSet.add(element.value);
        }
        return { status: status.value, value: parsedSet };
      }
      const elements = [...ctx.data.values()].map((item, i) => valueType._parse(new ParseInputLazyPath(ctx, item, ctx.path, i)));
      if (ctx.common.async) {
        return Promise.all(elements).then((elements2) => finalizeSet(elements2));
      } else {
        return finalizeSet(elements);
      }
    }
    min(minSize, message) {
      return new _ZodSet({
        ...this._def,
        minSize: { value: minSize, message: errorUtil.toString(message) }
      });
    }
    max(maxSize, message) {
      return new _ZodSet({
        ...this._def,
        maxSize: { value: maxSize, message: errorUtil.toString(message) }
      });
    }
    size(size, message) {
      return this.min(size, message).max(size, message);
    }
    nonempty(message) {
      return this.min(1, message);
    }
  };
  ZodSet.create = (valueType, params) => {
    return new ZodSet({
      valueType,
      minSize: null,
      maxSize: null,
      typeName: ZodFirstPartyTypeKind.ZodSet,
      ...processCreateParams(params)
    });
  };
  var ZodFunction = class _ZodFunction extends ZodType {
    constructor() {
      super(...arguments);
      this.validate = this.implement;
    }
    _parse(input) {
      const { ctx } = this._processInputParams(input);
      if (ctx.parsedType !== ZodParsedType.function) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.function,
          received: ctx.parsedType
        });
        return INVALID;
      }
      function makeArgsIssue(args, error) {
        return makeIssue({
          data: args,
          path: ctx.path,
          errorMaps: [ctx.common.contextualErrorMap, ctx.schemaErrorMap, getErrorMap(), en_default].filter((x) => !!x),
          issueData: {
            code: ZodIssueCode.invalid_arguments,
            argumentsError: error
          }
        });
      }
      function makeReturnsIssue(returns, error) {
        return makeIssue({
          data: returns,
          path: ctx.path,
          errorMaps: [ctx.common.contextualErrorMap, ctx.schemaErrorMap, getErrorMap(), en_default].filter((x) => !!x),
          issueData: {
            code: ZodIssueCode.invalid_return_type,
            returnTypeError: error
          }
        });
      }
      const params = { errorMap: ctx.common.contextualErrorMap };
      const fn = ctx.data;
      if (this._def.returns instanceof ZodPromise) {
        const me = this;
        return OK(async function(...args) {
          const error = new ZodError([]);
          const parsedArgs = await me._def.args.parseAsync(args, params).catch((e) => {
            error.addIssue(makeArgsIssue(args, e));
            throw error;
          });
          const result = await Reflect.apply(fn, this, parsedArgs);
          const parsedReturns = await me._def.returns._def.type.parseAsync(result, params).catch((e) => {
            error.addIssue(makeReturnsIssue(result, e));
            throw error;
          });
          return parsedReturns;
        });
      } else {
        const me = this;
        return OK(function(...args) {
          const parsedArgs = me._def.args.safeParse(args, params);
          if (!parsedArgs.success) {
            throw new ZodError([makeArgsIssue(args, parsedArgs.error)]);
          }
          const result = Reflect.apply(fn, this, parsedArgs.data);
          const parsedReturns = me._def.returns.safeParse(result, params);
          if (!parsedReturns.success) {
            throw new ZodError([makeReturnsIssue(result, parsedReturns.error)]);
          }
          return parsedReturns.data;
        });
      }
    }
    parameters() {
      return this._def.args;
    }
    returnType() {
      return this._def.returns;
    }
    args(...items) {
      return new _ZodFunction({
        ...this._def,
        args: ZodTuple.create(items).rest(ZodUnknown.create())
      });
    }
    returns(returnType) {
      return new _ZodFunction({
        ...this._def,
        returns: returnType
      });
    }
    implement(func) {
      const validatedFunc = this.parse(func);
      return validatedFunc;
    }
    strictImplement(func) {
      const validatedFunc = this.parse(func);
      return validatedFunc;
    }
    static create(args, returns, params) {
      return new _ZodFunction({
        args: args ? args : ZodTuple.create([]).rest(ZodUnknown.create()),
        returns: returns || ZodUnknown.create(),
        typeName: ZodFirstPartyTypeKind.ZodFunction,
        ...processCreateParams(params)
      });
    }
  };
  var ZodLazy = class extends ZodType {
    get schema() {
      return this._def.getter();
    }
    _parse(input) {
      const { ctx } = this._processInputParams(input);
      const lazySchema = this._def.getter();
      return lazySchema._parse({ data: ctx.data, path: ctx.path, parent: ctx });
    }
  };
  ZodLazy.create = (getter, params) => {
    return new ZodLazy({
      getter,
      typeName: ZodFirstPartyTypeKind.ZodLazy,
      ...processCreateParams(params)
    });
  };
  var ZodLiteral = class extends ZodType {
    _parse(input) {
      if (input.data !== this._def.value) {
        const ctx = this._getOrReturnCtx(input);
        addIssueToContext(ctx, {
          received: ctx.data,
          code: ZodIssueCode.invalid_literal,
          expected: this._def.value
        });
        return INVALID;
      }
      return { status: "valid", value: input.data };
    }
    get value() {
      return this._def.value;
    }
  };
  ZodLiteral.create = (value, params) => {
    return new ZodLiteral({
      value,
      typeName: ZodFirstPartyTypeKind.ZodLiteral,
      ...processCreateParams(params)
    });
  };
  function createZodEnum(values, params) {
    return new ZodEnum({
      values,
      typeName: ZodFirstPartyTypeKind.ZodEnum,
      ...processCreateParams(params)
    });
  }
  var ZodEnum = class _ZodEnum extends ZodType {
    _parse(input) {
      if (typeof input.data !== "string") {
        const ctx = this._getOrReturnCtx(input);
        const expectedValues = this._def.values;
        addIssueToContext(ctx, {
          expected: util.joinValues(expectedValues),
          received: ctx.parsedType,
          code: ZodIssueCode.invalid_type
        });
        return INVALID;
      }
      if (!this._cache) {
        this._cache = new Set(this._def.values);
      }
      if (!this._cache.has(input.data)) {
        const ctx = this._getOrReturnCtx(input);
        const expectedValues = this._def.values;
        addIssueToContext(ctx, {
          received: ctx.data,
          code: ZodIssueCode.invalid_enum_value,
          options: expectedValues
        });
        return INVALID;
      }
      return OK(input.data);
    }
    get options() {
      return this._def.values;
    }
    get enum() {
      const enumValues = {};
      for (const val of this._def.values) {
        enumValues[val] = val;
      }
      return enumValues;
    }
    get Values() {
      const enumValues = {};
      for (const val of this._def.values) {
        enumValues[val] = val;
      }
      return enumValues;
    }
    get Enum() {
      const enumValues = {};
      for (const val of this._def.values) {
        enumValues[val] = val;
      }
      return enumValues;
    }
    extract(values, newDef = this._def) {
      return _ZodEnum.create(values, {
        ...this._def,
        ...newDef
      });
    }
    exclude(values, newDef = this._def) {
      return _ZodEnum.create(this.options.filter((opt) => !values.includes(opt)), {
        ...this._def,
        ...newDef
      });
    }
  };
  ZodEnum.create = createZodEnum;
  var ZodNativeEnum = class extends ZodType {
    _parse(input) {
      const nativeEnumValues = util.getValidEnumValues(this._def.values);
      const ctx = this._getOrReturnCtx(input);
      if (ctx.parsedType !== ZodParsedType.string && ctx.parsedType !== ZodParsedType.number) {
        const expectedValues = util.objectValues(nativeEnumValues);
        addIssueToContext(ctx, {
          expected: util.joinValues(expectedValues),
          received: ctx.parsedType,
          code: ZodIssueCode.invalid_type
        });
        return INVALID;
      }
      if (!this._cache) {
        this._cache = new Set(util.getValidEnumValues(this._def.values));
      }
      if (!this._cache.has(input.data)) {
        const expectedValues = util.objectValues(nativeEnumValues);
        addIssueToContext(ctx, {
          received: ctx.data,
          code: ZodIssueCode.invalid_enum_value,
          options: expectedValues
        });
        return INVALID;
      }
      return OK(input.data);
    }
    get enum() {
      return this._def.values;
    }
  };
  ZodNativeEnum.create = (values, params) => {
    return new ZodNativeEnum({
      values,
      typeName: ZodFirstPartyTypeKind.ZodNativeEnum,
      ...processCreateParams(params)
    });
  };
  var ZodPromise = class extends ZodType {
    unwrap() {
      return this._def.type;
    }
    _parse(input) {
      const { ctx } = this._processInputParams(input);
      if (ctx.parsedType !== ZodParsedType.promise && ctx.common.async === false) {
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.promise,
          received: ctx.parsedType
        });
        return INVALID;
      }
      const promisified = ctx.parsedType === ZodParsedType.promise ? ctx.data : Promise.resolve(ctx.data);
      return OK(promisified.then((data) => {
        return this._def.type.parseAsync(data, {
          path: ctx.path,
          errorMap: ctx.common.contextualErrorMap
        });
      }));
    }
  };
  ZodPromise.create = (schema, params) => {
    return new ZodPromise({
      type: schema,
      typeName: ZodFirstPartyTypeKind.ZodPromise,
      ...processCreateParams(params)
    });
  };
  var ZodEffects = class extends ZodType {
    innerType() {
      return this._def.schema;
    }
    sourceType() {
      return this._def.schema._def.typeName === ZodFirstPartyTypeKind.ZodEffects ? this._def.schema.sourceType() : this._def.schema;
    }
    _parse(input) {
      const { status, ctx } = this._processInputParams(input);
      const effect = this._def.effect || null;
      const checkCtx = {
        addIssue: (arg) => {
          addIssueToContext(ctx, arg);
          if (arg.fatal) {
            status.abort();
          } else {
            status.dirty();
          }
        },
        get path() {
          return ctx.path;
        }
      };
      checkCtx.addIssue = checkCtx.addIssue.bind(checkCtx);
      if (effect.type === "preprocess") {
        const processed = effect.transform(ctx.data, checkCtx);
        if (ctx.common.async) {
          return Promise.resolve(processed).then(async (processed2) => {
            if (status.value === "aborted")
              return INVALID;
            const result = await this._def.schema._parseAsync({
              data: processed2,
              path: ctx.path,
              parent: ctx
            });
            if (result.status === "aborted")
              return INVALID;
            if (result.status === "dirty")
              return DIRTY(result.value);
            if (status.value === "dirty")
              return DIRTY(result.value);
            return result;
          });
        } else {
          if (status.value === "aborted")
            return INVALID;
          const result = this._def.schema._parseSync({
            data: processed,
            path: ctx.path,
            parent: ctx
          });
          if (result.status === "aborted")
            return INVALID;
          if (result.status === "dirty")
            return DIRTY(result.value);
          if (status.value === "dirty")
            return DIRTY(result.value);
          return result;
        }
      }
      if (effect.type === "refinement") {
        const executeRefinement = (acc) => {
          const result = effect.refinement(acc, checkCtx);
          if (ctx.common.async) {
            return Promise.resolve(result);
          }
          if (result instanceof Promise) {
            throw new Error("Async refinement encountered during synchronous parse operation. Use .parseAsync instead.");
          }
          return acc;
        };
        if (ctx.common.async === false) {
          const inner = this._def.schema._parseSync({
            data: ctx.data,
            path: ctx.path,
            parent: ctx
          });
          if (inner.status === "aborted")
            return INVALID;
          if (inner.status === "dirty")
            status.dirty();
          executeRefinement(inner.value);
          return { status: status.value, value: inner.value };
        } else {
          return this._def.schema._parseAsync({ data: ctx.data, path: ctx.path, parent: ctx }).then((inner) => {
            if (inner.status === "aborted")
              return INVALID;
            if (inner.status === "dirty")
              status.dirty();
            return executeRefinement(inner.value).then(() => {
              return { status: status.value, value: inner.value };
            });
          });
        }
      }
      if (effect.type === "transform") {
        if (ctx.common.async === false) {
          const base = this._def.schema._parseSync({
            data: ctx.data,
            path: ctx.path,
            parent: ctx
          });
          if (!isValid(base))
            return INVALID;
          const result = effect.transform(base.value, checkCtx);
          if (result instanceof Promise) {
            throw new Error(`Asynchronous transform encountered during synchronous parse operation. Use .parseAsync instead.`);
          }
          return { status: status.value, value: result };
        } else {
          return this._def.schema._parseAsync({ data: ctx.data, path: ctx.path, parent: ctx }).then((base) => {
            if (!isValid(base))
              return INVALID;
            return Promise.resolve(effect.transform(base.value, checkCtx)).then((result) => ({
              status: status.value,
              value: result
            }));
          });
        }
      }
      util.assertNever(effect);
    }
  };
  ZodEffects.create = (schema, effect, params) => {
    return new ZodEffects({
      schema,
      typeName: ZodFirstPartyTypeKind.ZodEffects,
      effect,
      ...processCreateParams(params)
    });
  };
  ZodEffects.createWithPreprocess = (preprocess, schema, params) => {
    return new ZodEffects({
      schema,
      effect: { type: "preprocess", transform: preprocess },
      typeName: ZodFirstPartyTypeKind.ZodEffects,
      ...processCreateParams(params)
    });
  };
  var ZodOptional = class extends ZodType {
    _parse(input) {
      const parsedType = this._getType(input);
      if (parsedType === ZodParsedType.undefined) {
        return OK(void 0);
      }
      return this._def.innerType._parse(input);
    }
    unwrap() {
      return this._def.innerType;
    }
  };
  ZodOptional.create = (type, params) => {
    return new ZodOptional({
      innerType: type,
      typeName: ZodFirstPartyTypeKind.ZodOptional,
      ...processCreateParams(params)
    });
  };
  var ZodNullable = class extends ZodType {
    _parse(input) {
      const parsedType = this._getType(input);
      if (parsedType === ZodParsedType.null) {
        return OK(null);
      }
      return this._def.innerType._parse(input);
    }
    unwrap() {
      return this._def.innerType;
    }
  };
  ZodNullable.create = (type, params) => {
    return new ZodNullable({
      innerType: type,
      typeName: ZodFirstPartyTypeKind.ZodNullable,
      ...processCreateParams(params)
    });
  };
  var ZodDefault = class extends ZodType {
    _parse(input) {
      const { ctx } = this._processInputParams(input);
      let data = ctx.data;
      if (ctx.parsedType === ZodParsedType.undefined) {
        data = this._def.defaultValue();
      }
      return this._def.innerType._parse({
        data,
        path: ctx.path,
        parent: ctx
      });
    }
    removeDefault() {
      return this._def.innerType;
    }
  };
  ZodDefault.create = (type, params) => {
    return new ZodDefault({
      innerType: type,
      typeName: ZodFirstPartyTypeKind.ZodDefault,
      defaultValue: typeof params.default === "function" ? params.default : () => params.default,
      ...processCreateParams(params)
    });
  };
  var ZodCatch = class extends ZodType {
    _parse(input) {
      const { ctx } = this._processInputParams(input);
      const newCtx = {
        ...ctx,
        common: {
          ...ctx.common,
          issues: []
        }
      };
      const result = this._def.innerType._parse({
        data: newCtx.data,
        path: newCtx.path,
        parent: {
          ...newCtx
        }
      });
      if (isAsync(result)) {
        return result.then((result2) => {
          return {
            status: "valid",
            value: result2.status === "valid" ? result2.value : this._def.catchValue({
              get error() {
                return new ZodError(newCtx.common.issues);
              },
              input: newCtx.data
            })
          };
        });
      } else {
        return {
          status: "valid",
          value: result.status === "valid" ? result.value : this._def.catchValue({
            get error() {
              return new ZodError(newCtx.common.issues);
            },
            input: newCtx.data
          })
        };
      }
    }
    removeCatch() {
      return this._def.innerType;
    }
  };
  ZodCatch.create = (type, params) => {
    return new ZodCatch({
      innerType: type,
      typeName: ZodFirstPartyTypeKind.ZodCatch,
      catchValue: typeof params.catch === "function" ? params.catch : () => params.catch,
      ...processCreateParams(params)
    });
  };
  var ZodNaN = class extends ZodType {
    _parse(input) {
      const parsedType = this._getType(input);
      if (parsedType !== ZodParsedType.nan) {
        const ctx = this._getOrReturnCtx(input);
        addIssueToContext(ctx, {
          code: ZodIssueCode.invalid_type,
          expected: ZodParsedType.nan,
          received: ctx.parsedType
        });
        return INVALID;
      }
      return { status: "valid", value: input.data };
    }
  };
  ZodNaN.create = (params) => {
    return new ZodNaN({
      typeName: ZodFirstPartyTypeKind.ZodNaN,
      ...processCreateParams(params)
    });
  };
  var BRAND = Symbol("zod_brand");
  var ZodBranded = class extends ZodType {
    _parse(input) {
      const { ctx } = this._processInputParams(input);
      const data = ctx.data;
      return this._def.type._parse({
        data,
        path: ctx.path,
        parent: ctx
      });
    }
    unwrap() {
      return this._def.type;
    }
  };
  var ZodPipeline = class _ZodPipeline extends ZodType {
    _parse(input) {
      const { status, ctx } = this._processInputParams(input);
      if (ctx.common.async) {
        const handleAsync = async () => {
          const inResult = await this._def.in._parseAsync({
            data: ctx.data,
            path: ctx.path,
            parent: ctx
          });
          if (inResult.status === "aborted")
            return INVALID;
          if (inResult.status === "dirty") {
            status.dirty();
            return DIRTY(inResult.value);
          } else {
            return this._def.out._parseAsync({
              data: inResult.value,
              path: ctx.path,
              parent: ctx
            });
          }
        };
        return handleAsync();
      } else {
        const inResult = this._def.in._parseSync({
          data: ctx.data,
          path: ctx.path,
          parent: ctx
        });
        if (inResult.status === "aborted")
          return INVALID;
        if (inResult.status === "dirty") {
          status.dirty();
          return {
            status: "dirty",
            value: inResult.value
          };
        } else {
          return this._def.out._parseSync({
            data: inResult.value,
            path: ctx.path,
            parent: ctx
          });
        }
      }
    }
    static create(a, b) {
      return new _ZodPipeline({
        in: a,
        out: b,
        typeName: ZodFirstPartyTypeKind.ZodPipeline
      });
    }
  };
  var ZodReadonly = class extends ZodType {
    _parse(input) {
      const result = this._def.innerType._parse(input);
      const freeze = (data) => {
        if (isValid(data)) {
          data.value = Object.freeze(data.value);
        }
        return data;
      };
      return isAsync(result) ? result.then((data) => freeze(data)) : freeze(result);
    }
    unwrap() {
      return this._def.innerType;
    }
  };
  ZodReadonly.create = (type, params) => {
    return new ZodReadonly({
      innerType: type,
      typeName: ZodFirstPartyTypeKind.ZodReadonly,
      ...processCreateParams(params)
    });
  };
  function cleanParams(params, data) {
    const p = typeof params === "function" ? params(data) : typeof params === "string" ? { message: params } : params;
    const p2 = typeof p === "string" ? { message: p } : p;
    return p2;
  }
  function custom(check, _params = {}, fatal) {
    if (check)
      return ZodAny.create().superRefine((data, ctx) => {
        const r = check(data);
        if (r instanceof Promise) {
          return r.then((r2) => {
            if (!r2) {
              const params = cleanParams(_params, data);
              const _fatal = params.fatal ?? fatal ?? true;
              ctx.addIssue({ code: "custom", ...params, fatal: _fatal });
            }
          });
        }
        if (!r) {
          const params = cleanParams(_params, data);
          const _fatal = params.fatal ?? fatal ?? true;
          ctx.addIssue({ code: "custom", ...params, fatal: _fatal });
        }
        return;
      });
    return ZodAny.create();
  }
  var late = {
    object: ZodObject.lazycreate
  };
  var ZodFirstPartyTypeKind;
  (function(ZodFirstPartyTypeKind2) {
    ZodFirstPartyTypeKind2["ZodString"] = "ZodString";
    ZodFirstPartyTypeKind2["ZodNumber"] = "ZodNumber";
    ZodFirstPartyTypeKind2["ZodNaN"] = "ZodNaN";
    ZodFirstPartyTypeKind2["ZodBigInt"] = "ZodBigInt";
    ZodFirstPartyTypeKind2["ZodBoolean"] = "ZodBoolean";
    ZodFirstPartyTypeKind2["ZodDate"] = "ZodDate";
    ZodFirstPartyTypeKind2["ZodSymbol"] = "ZodSymbol";
    ZodFirstPartyTypeKind2["ZodUndefined"] = "ZodUndefined";
    ZodFirstPartyTypeKind2["ZodNull"] = "ZodNull";
    ZodFirstPartyTypeKind2["ZodAny"] = "ZodAny";
    ZodFirstPartyTypeKind2["ZodUnknown"] = "ZodUnknown";
    ZodFirstPartyTypeKind2["ZodNever"] = "ZodNever";
    ZodFirstPartyTypeKind2["ZodVoid"] = "ZodVoid";
    ZodFirstPartyTypeKind2["ZodArray"] = "ZodArray";
    ZodFirstPartyTypeKind2["ZodObject"] = "ZodObject";
    ZodFirstPartyTypeKind2["ZodUnion"] = "ZodUnion";
    ZodFirstPartyTypeKind2["ZodDiscriminatedUnion"] = "ZodDiscriminatedUnion";
    ZodFirstPartyTypeKind2["ZodIntersection"] = "ZodIntersection";
    ZodFirstPartyTypeKind2["ZodTuple"] = "ZodTuple";
    ZodFirstPartyTypeKind2["ZodRecord"] = "ZodRecord";
    ZodFirstPartyTypeKind2["ZodMap"] = "ZodMap";
    ZodFirstPartyTypeKind2["ZodSet"] = "ZodSet";
    ZodFirstPartyTypeKind2["ZodFunction"] = "ZodFunction";
    ZodFirstPartyTypeKind2["ZodLazy"] = "ZodLazy";
    ZodFirstPartyTypeKind2["ZodLiteral"] = "ZodLiteral";
    ZodFirstPartyTypeKind2["ZodEnum"] = "ZodEnum";
    ZodFirstPartyTypeKind2["ZodEffects"] = "ZodEffects";
    ZodFirstPartyTypeKind2["ZodNativeEnum"] = "ZodNativeEnum";
    ZodFirstPartyTypeKind2["ZodOptional"] = "ZodOptional";
    ZodFirstPartyTypeKind2["ZodNullable"] = "ZodNullable";
    ZodFirstPartyTypeKind2["ZodDefault"] = "ZodDefault";
    ZodFirstPartyTypeKind2["ZodCatch"] = "ZodCatch";
    ZodFirstPartyTypeKind2["ZodPromise"] = "ZodPromise";
    ZodFirstPartyTypeKind2["ZodBranded"] = "ZodBranded";
    ZodFirstPartyTypeKind2["ZodPipeline"] = "ZodPipeline";
    ZodFirstPartyTypeKind2["ZodReadonly"] = "ZodReadonly";
  })(ZodFirstPartyTypeKind || (ZodFirstPartyTypeKind = {}));
  var instanceOfType = (cls, params = {
    message: `Input not instance of ${cls.name}`
  }) => custom((data) => data instanceof cls, params);
  var stringType = ZodString.create;
  var numberType = ZodNumber.create;
  var nanType = ZodNaN.create;
  var bigIntType = ZodBigInt.create;
  var booleanType = ZodBoolean.create;
  var dateType = ZodDate.create;
  var symbolType = ZodSymbol.create;
  var undefinedType = ZodUndefined.create;
  var nullType = ZodNull.create;
  var anyType = ZodAny.create;
  var unknownType = ZodUnknown.create;
  var neverType = ZodNever.create;
  var voidType = ZodVoid.create;
  var arrayType = ZodArray.create;
  var objectType = ZodObject.create;
  var strictObjectType = ZodObject.strictCreate;
  var unionType = ZodUnion.create;
  var discriminatedUnionType = ZodDiscriminatedUnion.create;
  var intersectionType = ZodIntersection.create;
  var tupleType = ZodTuple.create;
  var recordType = ZodRecord.create;
  var mapType = ZodMap.create;
  var setType = ZodSet.create;
  var functionType = ZodFunction.create;
  var lazyType = ZodLazy.create;
  var literalType = ZodLiteral.create;
  var enumType = ZodEnum.create;
  var nativeEnumType = ZodNativeEnum.create;
  var promiseType = ZodPromise.create;
  var effectsType = ZodEffects.create;
  var optionalType = ZodOptional.create;
  var nullableType = ZodNullable.create;
  var preprocessType = ZodEffects.createWithPreprocess;
  var pipelineType = ZodPipeline.create;
  var ostring = () => stringType().optional();
  var onumber = () => numberType().optional();
  var oboolean = () => booleanType().optional();
  var coerce = {
    string: (arg) => ZodString.create({ ...arg, coerce: true }),
    number: (arg) => ZodNumber.create({ ...arg, coerce: true }),
    boolean: (arg) => ZodBoolean.create({
      ...arg,
      coerce: true
    }),
    bigint: (arg) => ZodBigInt.create({ ...arg, coerce: true }),
    date: (arg) => ZodDate.create({ ...arg, coerce: true })
  };
  var NEVER = INVALID;

  // src/deviceApiCalls/__generated__/validators.zod.js
  var sendJSPixelParamsSchema = external_exports.union([external_exports.object({
    pixelName: external_exports.literal("autofill_identity"),
    params: external_exports.object({
      fieldType: external_exports.string().optional()
    }).optional()
  }), external_exports.object({
    pixelName: external_exports.literal("autofill_show")
  }), external_exports.object({
    pixelName: external_exports.literal("autofill_import_credentials_prompt_shown")
  }), external_exports.object({
    pixelName: external_exports.literal("autofill_personal_address")
  }), external_exports.object({
    pixelName: external_exports.literal("autofill_private_address")
  }), external_exports.object({
    pixelName: external_exports.literal("incontext_show")
  }), external_exports.object({
    pixelName: external_exports.literal("incontext_primary_cta")
  }), external_exports.object({
    pixelName: external_exports.literal("incontext_dismiss_persisted")
  }), external_exports.object({
    pixelName: external_exports.literal("incontext_close_x")
  })]);
  var addDebugFlagParamsSchema = external_exports.object({
    flag: external_exports.string()
  });
  var getAutofillDataFocusRequestSchema = external_exports.object({
    inputType: external_exports.string(),
    mainType: external_exports.union([external_exports.literal("credentials"), external_exports.literal("identities"), external_exports.literal("creditCards"), external_exports.literal("unknown")])
  });
  var getAutofillCredentialsParamsSchema = external_exports.object({
    id: external_exports.string()
  });
  var setSizeParamsSchema = external_exports.object({
    height: external_exports.number(),
    width: external_exports.number()
  });
  var selectedDetailParamsSchema = external_exports.object({
    data: external_exports.record(external_exports.unknown()),
    configType: external_exports.string()
  });
  var setIncontextSignupPermanentlyDismissedAtSchema = external_exports.object({
    value: external_exports.number().optional()
  });
  var getIncontextSignupDismissedAtSchema = external_exports.object({
    success: external_exports.object({
      permanentlyDismissedAt: external_exports.number().optional(),
      isInstalledRecently: external_exports.boolean().optional()
    })
  });
  var getAliasParamsSchema = external_exports.object({
    requiresUserPermission: external_exports.boolean(),
    shouldConsumeAliasIfProvided: external_exports.boolean(),
    isIncontextSignupAvailable: external_exports.boolean().optional()
  });
  var getAliasResultSchema = external_exports.object({
    success: external_exports.object({
      alias: external_exports.string().optional()
    })
  });
  var getIdentityParamSchema = external_exports.object({
    id: external_exports.string()
  });
  var getCreditCardParamSchema = external_exports.object({
    id: external_exports.string()
  });
  var emailProtectionStoreUserDataParamsSchema = external_exports.object({
    token: external_exports.string(),
    userName: external_exports.string(),
    cohort: external_exports.string()
  });
  var showInContextEmailProtectionSignupPromptSchema = external_exports.object({
    success: external_exports.object({
      isSignedIn: external_exports.boolean()
    })
  });
  var generatedPasswordSchema = external_exports.object({
    value: external_exports.string(),
    username: external_exports.string()
  });
  var triggerContextSchema = external_exports.object({
    inputTop: external_exports.number(),
    inputLeft: external_exports.number(),
    inputHeight: external_exports.number(),
    inputWidth: external_exports.number(),
    wasFromClick: external_exports.boolean()
  });
  var credentialsSchema = external_exports.object({
    id: external_exports.string().optional(),
    username: external_exports.string(),
    password: external_exports.string(),
    origin: external_exports.object({
      url: external_exports.string()
    }).optional(),
    credentialsProvider: external_exports.union([external_exports.literal("duckduckgo"), external_exports.literal("bitwarden")]).optional(),
    providerStatus: external_exports.union([external_exports.literal("locked"), external_exports.literal("unlocked")]).optional()
  });
  var creditCardObjectSchema = external_exports.object({
    id: external_exports.string(),
    title: external_exports.string(),
    displayNumber: external_exports.string(),
    cardName: external_exports.string().optional(),
    cardSecurityCode: external_exports.string().optional(),
    expirationMonth: external_exports.string().optional(),
    expirationYear: external_exports.string().optional(),
    cardNumber: external_exports.string().optional(),
    paymentProvider: external_exports.string().optional()
  });
  var identityObjectSchema = external_exports.object({
    id: external_exports.string(),
    title: external_exports.string(),
    firstName: external_exports.string().optional(),
    middleName: external_exports.string().optional(),
    lastName: external_exports.string().optional(),
    birthdayDay: external_exports.string().optional(),
    birthdayMonth: external_exports.string().optional(),
    birthdayYear: external_exports.string().optional(),
    addressStreet: external_exports.string().optional(),
    addressStreet2: external_exports.string().optional(),
    addressCity: external_exports.string().optional(),
    addressProvince: external_exports.string().optional(),
    addressPostalCode: external_exports.string().optional(),
    addressCountryCode: external_exports.string().optional(),
    phone: external_exports.string().optional(),
    emailAddress: external_exports.string().optional()
  });
  var availableInputTypesSchema = external_exports.object({
    credentials: external_exports.object({
      username: external_exports.boolean().optional(),
      password: external_exports.boolean().optional()
    }).optional(),
    identities: external_exports.object({
      firstName: external_exports.boolean().optional(),
      middleName: external_exports.boolean().optional(),
      lastName: external_exports.boolean().optional(),
      birthdayDay: external_exports.boolean().optional(),
      birthdayMonth: external_exports.boolean().optional(),
      birthdayYear: external_exports.boolean().optional(),
      addressStreet: external_exports.boolean().optional(),
      addressStreet2: external_exports.boolean().optional(),
      addressCity: external_exports.boolean().optional(),
      addressProvince: external_exports.boolean().optional(),
      addressPostalCode: external_exports.boolean().optional(),
      addressCountryCode: external_exports.boolean().optional(),
      phone: external_exports.boolean().optional(),
      emailAddress: external_exports.boolean().optional()
    }).optional(),
    creditCards: external_exports.object({
      cardName: external_exports.boolean().optional(),
      cardSecurityCode: external_exports.boolean().optional(),
      expirationMonth: external_exports.boolean().optional(),
      expirationYear: external_exports.boolean().optional(),
      cardNumber: external_exports.boolean().optional()
    }).optional(),
    email: external_exports.boolean().optional(),
    credentialsProviderStatus: external_exports.union([external_exports.literal("locked"), external_exports.literal("unlocked")]).optional(),
    credentialsImport: external_exports.boolean().optional()
  });
  var genericErrorSchema = external_exports.object({
    message: external_exports.string()
  });
  var getAutofillDataFocusResponseSchema = external_exports.object({
    type: external_exports.literal("getAutofillDataFocusResponse").optional(),
    success: external_exports.object({
      creditCards: creditCardObjectSchema.optional(),
      action: external_exports.union([external_exports.literal("fill"), external_exports.literal("none")])
    }).optional(),
    error: genericErrorSchema.optional()
  });
  var userPreferencesSchema = external_exports.object({
    globalPrivacyControlValue: external_exports.boolean().optional(),
    sessionKey: external_exports.string(),
    debug: external_exports.boolean(),
    language: external_exports.string().optional(),
    platform: external_exports.object({
      name: external_exports.union([external_exports.literal("ios"), external_exports.literal("macos"), external_exports.literal("windows"), external_exports.literal("extension"), external_exports.literal("android")])
    }),
    features: external_exports.record(external_exports.object({
      settings: external_exports.record(external_exports.unknown())
    }))
  });
  var outgoingCredentialsSchema = external_exports.object({
    username: external_exports.string().optional(),
    password: external_exports.string().optional()
  });
  var availableInputTypes1Schema = external_exports.object({
    credentials: external_exports.object({
      username: external_exports.boolean().optional(),
      password: external_exports.boolean().optional()
    }).optional(),
    identities: external_exports.object({
      firstName: external_exports.boolean().optional(),
      middleName: external_exports.boolean().optional(),
      lastName: external_exports.boolean().optional(),
      birthdayDay: external_exports.boolean().optional(),
      birthdayMonth: external_exports.boolean().optional(),
      birthdayYear: external_exports.boolean().optional(),
      addressStreet: external_exports.boolean().optional(),
      addressStreet2: external_exports.boolean().optional(),
      addressCity: external_exports.boolean().optional(),
      addressProvince: external_exports.boolean().optional(),
      addressPostalCode: external_exports.boolean().optional(),
      addressCountryCode: external_exports.boolean().optional(),
      phone: external_exports.boolean().optional(),
      emailAddress: external_exports.boolean().optional()
    }).optional(),
    creditCards: external_exports.object({
      cardName: external_exports.boolean().optional(),
      cardSecurityCode: external_exports.boolean().optional(),
      expirationMonth: external_exports.boolean().optional(),
      expirationYear: external_exports.boolean().optional(),
      cardNumber: external_exports.boolean().optional()
    }).optional(),
    email: external_exports.boolean().optional(),
    credentialsProviderStatus: external_exports.union([external_exports.literal("locked"), external_exports.literal("unlocked")]).optional(),
    credentialsImport: external_exports.boolean().optional()
  });
  var getAutofillInitDataResponseSchema = external_exports.object({
    type: external_exports.literal("getAutofillInitDataResponse").optional(),
    success: external_exports.object({
      credentials: external_exports.array(credentialsSchema),
      identities: external_exports.array(identityObjectSchema),
      creditCards: external_exports.array(creditCardObjectSchema),
      serializedInputContext: external_exports.string()
    }).optional(),
    error: genericErrorSchema.optional()
  });
  var getAutofillCredentialsResultSchema = external_exports.object({
    type: external_exports.literal("getAutofillCredentialsResponse").optional(),
    success: external_exports.object({
      id: external_exports.string().optional(),
      autogenerated: external_exports.boolean().optional(),
      username: external_exports.string(),
      password: external_exports.string().optional()
    }).optional(),
    error: genericErrorSchema.optional()
  });
  var providerStatusUpdatedSchema = external_exports.object({
    status: external_exports.union([external_exports.literal("locked"), external_exports.literal("unlocked")]),
    credentials: external_exports.array(credentialsSchema),
    availableInputTypes: availableInputTypesSchema
  });
  var checkCredentialsProviderStatusResultSchema = external_exports.object({
    type: external_exports.literal("checkCredentialsProviderStatusResponse").optional(),
    success: providerStatusUpdatedSchema,
    error: genericErrorSchema.optional()
  });
  var autofillFeatureTogglesSchema = external_exports.object({
    autocomplete_attribute_support: external_exports.boolean().optional(),
    inputType_credentials: external_exports.boolean().optional(),
    inputType_identities: external_exports.boolean().optional(),
    inputType_creditCards: external_exports.boolean().optional(),
    emailProtection: external_exports.boolean().optional(),
    emailProtection_incontext_signup: external_exports.boolean().optional(),
    password_generation: external_exports.boolean().optional(),
    credentials_saving: external_exports.boolean().optional(),
    inlineIcon_credentials: external_exports.boolean().optional(),
    third_party_credentials_provider: external_exports.boolean().optional(),
    unknown_username_categorization: external_exports.boolean().optional(),
    input_focus_api: external_exports.boolean().optional(),
    password_variant_categorization: external_exports.boolean().optional(),
    partial_form_saves: external_exports.boolean().optional()
  });
  var getIdentityResultSchema = external_exports.object({
    success: identityObjectSchema
  });
  var getCreditCardResultSchema = external_exports.object({
    success: creditCardObjectSchema
  });
  var emailProtectionGetIsLoggedInResultSchema = external_exports.object({
    success: external_exports.boolean().optional(),
    error: genericErrorSchema.optional()
  });
  var emailProtectionGetUserDataResultSchema = external_exports.object({
    success: external_exports.object({
      userName: external_exports.string(),
      nextAlias: external_exports.string(),
      token: external_exports.string()
    }).optional(),
    error: genericErrorSchema.optional()
  });
  var emailProtectionGetCapabilitiesResultSchema = external_exports.object({
    success: external_exports.object({
      addUserData: external_exports.boolean().optional(),
      getUserData: external_exports.boolean().optional(),
      removeUserData: external_exports.boolean().optional()
    }).optional(),
    error: genericErrorSchema.optional()
  });
  var emailProtectionGetAddressesResultSchema = external_exports.object({
    success: external_exports.object({
      personalAddress: external_exports.string(),
      privateAddress: external_exports.string()
    }).optional(),
    error: genericErrorSchema.optional()
  });
  var emailProtectionRefreshPrivateAddressResultSchema = external_exports.object({
    success: external_exports.object({
      personalAddress: external_exports.string(),
      privateAddress: external_exports.string()
    }).optional(),
    error: genericErrorSchema.optional()
  });
  var getAutofillDataRequestSchema = external_exports.object({
    generatedPassword: generatedPasswordSchema.optional(),
    inputType: external_exports.string(),
    mainType: external_exports.union([external_exports.literal("credentials"), external_exports.literal("identities"), external_exports.literal("creditCards")]),
    subType: external_exports.string(),
    trigger: external_exports.union([external_exports.literal("userInitiated"), external_exports.literal("autoprompt"), external_exports.literal("postSignup"), external_exports.literal("credentialsImport")]).optional(),
    serializedInputContext: external_exports.string().optional(),
    triggerContext: triggerContextSchema.optional()
  });
  var getAutofillDataResponseSchema = external_exports.object({
    type: external_exports.literal("getAutofillDataResponse").optional(),
    success: external_exports.object({
      credentials: credentialsSchema.optional(),
      creditCards: creditCardObjectSchema.optional(),
      identities: identityObjectSchema.optional(),
      availableInputTypes: availableInputTypesSchema.optional(),
      action: external_exports.union([external_exports.literal("fill"), external_exports.literal("focus"), external_exports.literal("none"), external_exports.literal("refreshAvailableInputTypes"), external_exports.literal("acceptGeneratedPassword"), external_exports.literal("rejectGeneratedPassword")])
    }).optional(),
    error: genericErrorSchema.optional()
  });
  var storeFormDataSchema = external_exports.object({
    credentials: outgoingCredentialsSchema.optional(),
    trigger: external_exports.union([external_exports.literal("partialSave"), external_exports.literal("formSubmission"), external_exports.literal("passwordGeneration"), external_exports.literal("emailProtection")]).optional()
  });
  var getAvailableInputTypesResultSchema = external_exports.object({
    type: external_exports.literal("getAvailableInputTypesResponse").optional(),
    success: availableInputTypes1Schema,
    error: genericErrorSchema.optional()
  });
  var askToUnlockProviderResultSchema = external_exports.object({
    type: external_exports.literal("askToUnlockProviderResponse").optional(),
    success: providerStatusUpdatedSchema,
    error: genericErrorSchema.optional()
  });
  var autofillSettingsSchema = external_exports.object({
    featureToggles: autofillFeatureTogglesSchema
  });
  var runtimeConfigurationSchema = external_exports.object({
    contentScope: external_exports.record(external_exports.unknown()),
    userUnprotectedDomains: external_exports.array(external_exports.string()),
    userPreferences: userPreferencesSchema
  });
  var getRuntimeConfigurationResponseSchema = external_exports.object({
    type: external_exports.literal("getRuntimeConfigurationResponse").optional(),
    success: runtimeConfigurationSchema.optional(),
    error: genericErrorSchema.optional()
  });
  var apiSchema = external_exports.object({
    addDebugFlag: external_exports.record(external_exports.unknown()).and(external_exports.object({
      paramsValidator: addDebugFlagParamsSchema.optional()
    })).optional(),
    getAutofillData: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("getAutofillDataResponse").optional(),
      paramsValidator: getAutofillDataRequestSchema.optional(),
      resultValidator: getAutofillDataResponseSchema.optional()
    })).optional(),
    getAutofillDataFocus: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("getAutofillDataFocusResponse").optional(),
      paramsValidator: getAutofillDataFocusRequestSchema.optional(),
      resultValidator: getAutofillDataFocusResponseSchema.optional()
    })).optional(),
    getRuntimeConfiguration: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("getRuntimeConfigurationResponse").optional(),
      resultValidator: getRuntimeConfigurationResponseSchema.optional()
    })).optional(),
    storeFormData: external_exports.record(external_exports.unknown()).and(external_exports.object({
      paramsValidator: storeFormDataSchema.optional()
    })).optional(),
    getAvailableInputTypes: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("getAvailableInputTypesResponse").optional(),
      resultValidator: getAvailableInputTypesResultSchema.optional()
    })).optional(),
    getAutofillInitData: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("getAutofillInitDataResponse").optional(),
      resultValidator: getAutofillInitDataResponseSchema.optional()
    })).optional(),
    getAutofillCredentials: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("getAutofillCredentialsResponse").optional(),
      paramsValidator: getAutofillCredentialsParamsSchema.optional(),
      resultValidator: getAutofillCredentialsResultSchema.optional()
    })).optional(),
    setSize: external_exports.record(external_exports.unknown()).and(external_exports.object({
      paramsValidator: setSizeParamsSchema.optional()
    })).optional(),
    selectedDetail: external_exports.record(external_exports.unknown()).and(external_exports.object({
      paramsValidator: selectedDetailParamsSchema.optional()
    })).optional(),
    closeAutofillParent: external_exports.record(external_exports.unknown()).optional(),
    askToUnlockProvider: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("askToUnlockProviderResponse").optional(),
      resultValidator: askToUnlockProviderResultSchema.optional()
    })).optional(),
    checkCredentialsProviderStatus: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("checkCredentialsProviderStatusResponse").optional(),
      resultValidator: checkCredentialsProviderStatusResultSchema.optional()
    })).optional(),
    sendJSPixel: external_exports.record(external_exports.unknown()).and(external_exports.object({
      paramsValidator: sendJSPixelParamsSchema.optional()
    })).optional(),
    setIncontextSignupPermanentlyDismissedAt: external_exports.record(external_exports.unknown()).and(external_exports.object({
      paramsValidator: setIncontextSignupPermanentlyDismissedAtSchema.optional()
    })).optional(),
    getIncontextSignupDismissedAt: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("getIncontextSignupDismissedAt").optional(),
      resultValidator: getIncontextSignupDismissedAtSchema.optional()
    })).optional(),
    autofillSettings: external_exports.record(external_exports.unknown()).and(external_exports.object({
      validatorsOnly: external_exports.literal(true).optional(),
      resultValidator: autofillSettingsSchema.optional()
    })).optional(),
    getAlias: external_exports.record(external_exports.unknown()).and(external_exports.object({
      validatorsOnly: external_exports.literal(true).optional(),
      paramValidator: getAliasParamsSchema.optional(),
      resultValidator: getAliasResultSchema.optional()
    })).optional(),
    openManagePasswords: external_exports.record(external_exports.unknown()).optional(),
    openManageCreditCards: external_exports.record(external_exports.unknown()).optional(),
    openManageIdentities: external_exports.record(external_exports.unknown()).optional(),
    startCredentialsImportFlow: external_exports.record(external_exports.unknown()).optional(),
    getIdentity: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("getIdentityResponse").optional(),
      paramValidator: getIdentityParamSchema.optional(),
      resultValidator: getIdentityResultSchema.optional()
    })).optional(),
    getCreditCard: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("getCreditCardResponse").optional(),
      paramValidator: getCreditCardParamSchema.optional(),
      resultValidator: getCreditCardResultSchema.optional()
    })).optional(),
    credentialsImportFlowPermanentlyDismissed: external_exports.record(external_exports.unknown()).optional(),
    emailProtectionStoreUserData: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("emailProtectionStoreUserDataResponse").optional(),
      paramsValidator: emailProtectionStoreUserDataParamsSchema.optional()
    })).optional(),
    emailProtectionRemoveUserData: external_exports.record(external_exports.unknown()).optional(),
    emailProtectionGetIsLoggedIn: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("emailProtectionGetIsLoggedInResponse").optional(),
      resultValidator: emailProtectionGetIsLoggedInResultSchema.optional()
    })).optional(),
    emailProtectionGetUserData: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("emailProtectionGetUserDataResponse").optional(),
      resultValidator: emailProtectionGetUserDataResultSchema.optional()
    })).optional(),
    emailProtectionGetCapabilities: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("emailProtectionGetCapabilitiesResponse").optional(),
      resultValidator: emailProtectionGetCapabilitiesResultSchema.optional()
    })).optional(),
    emailProtectionGetAddresses: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("emailProtectionGetAddressesResponse").optional(),
      resultValidator: emailProtectionGetAddressesResultSchema.optional()
    })).optional(),
    emailProtectionRefreshPrivateAddress: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("emailProtectionRefreshPrivateAddressResponse").optional(),
      resultValidator: emailProtectionRefreshPrivateAddressResultSchema.optional()
    })).optional(),
    startEmailProtectionSignup: external_exports.record(external_exports.unknown()).optional(),
    closeEmailProtectionTab: external_exports.record(external_exports.unknown()).optional(),
    ShowInContextEmailProtectionSignupPrompt: external_exports.record(external_exports.unknown()).and(external_exports.object({
      id: external_exports.literal("ShowInContextEmailProtectionSignupPromptResponse").optional(),
      resultValidator: showInContextEmailProtectionSignupPromptSchema.optional()
    })).optional()
  });

  // packages/device-api/lib/device-api-call.js
  var DeviceApiCall = class {
    /**
     * @param {import("zod").infer<Params>} data
     */
    constructor(data) {
      /** @type {string} */
      __publicField(this, "method", "unknown");
      /**
       * An optional 'id' - used to indicate if a request requires a response.
       * @type {string|null}
       */
      __publicField(this, "id", null);
      /** @type {Params | null | undefined} */
      __publicField(this, "paramsValidator", null);
      /** @type {Result | null | undefined} */
      __publicField(this, "resultValidator", null);
      /** @type {import("zod").infer<Params>} */
      __publicField(this, "params");
      /**
       * This is a carve-out for legacy messages that are not typed yet.
       * If you set this to 'true', then the response will not be checked to conform
       * to any shape
       * @deprecated this is here to aid migration, should be removed ASAP
       * @type {boolean}
       */
      __publicField(this, "throwOnResultKeysMissing", true);
      /**
       * New messages should be in a particular format, eg: { success: T },
       * but you can set this to false if you want to access the result as-is,
       * without any unwrapping logic
       * @deprecated this is here to aid migration, should be removed ASAP
       * @type {boolean}
       */
      __publicField(this, "unwrapResult", true);
      this.params = data;
    }
    /**
     * @returns {import("zod").infer<Params>|undefined}
     */
    validateParams() {
      if (this.params === void 0) {
        return void 0;
      }
      this._validate(this.params, this.paramsValidator);
      return this.params;
    }
    /**
     * @param {any|null} incoming
     * @returns {import("zod").infer<Result>}
     */
    validateResult(incoming) {
      this._validate(incoming, this.resultValidator);
      if (!incoming) {
        return incoming;
      }
      if (!this.unwrapResult) {
        return incoming;
      }
      if ("data" in incoming) {
        console.warn("response had `data` property. Please migrate to `success`");
        return incoming.data;
      }
      if ("success" in incoming) {
        return incoming.success;
      }
      if ("error" in incoming) {
        if (typeof incoming.error.message === "string") {
          throw new DeviceApiCallError(`${this.method}: ${incoming.error.message}`);
        }
      }
      if (this.throwOnResultKeysMissing) {
        throw new Error("unreachable. Response did not contain `success` or `data`");
      }
      return incoming;
    }
    /**
     * @param {any} data
     * @param {import("zod").ZodType|undefined|null} [validator]
     * @private
     */
    _validate(data, validator) {
      if (!validator) return data;
      if (validator) {
        const result = validator?.safeParse(data);
        if (!result) {
          throw new Error("unreachable, data failure", data);
        }
        if (!result.success) {
          if ("error" in result) {
            this.throwError(result.error.issues);
          } else {
            console.error("unknown error from validate");
          }
        }
      }
    }
    /**
     * @param {import('zod').ZodIssue[]} errors
     */
    throwError(errors) {
      const error = SchemaValidationError.fromZodErrors(errors, this.constructor.name);
      throw error;
    }
    /**
     * Use this helper for creating stand-in response messages that are typed correctly.
     *
     * @examples
     *
     * ```js
     * const msg = new Message();
     * const response = msg.response({}) // <-- This argument will be typed correctly
     * ```
     *
     * @param {import("zod").infer<Result>} response
     * @returns {import("zod").infer<Result>}
     */
    result(response) {
      return response;
    }
    /**
     * @returns {import("zod").infer<Result>}
     */
    preResultValidation(response) {
      return response;
    }
  };
  var DeviceApiCallError = class extends Error {
  };
  var SchemaValidationError = class _SchemaValidationError extends Error {
    constructor() {
      super(...arguments);
      /** @type {import("zod").ZodIssue[]} */
      __publicField(this, "validationErrors", []);
    }
    /**
     * @param {import("zod").ZodIssue[]} errors
     * @param {string} name
     * @returns {SchemaValidationError}
     */
    static fromZodErrors(errors, name) {
      const heading = `${errors.length} SchemaValidationError(s) errors for ` + name;
      function log(issue) {
        switch (issue.code) {
          case "invalid_literal":
          case "invalid_type": {
            console.log(`${name}. Path: '${issue.path.join(".")}', Error: '${issue.message}'`);
            break;
          }
          case "invalid_union": {
            for (const unionError of issue.unionErrors) {
              for (const issue1 of unionError.issues) {
                log(issue1);
              }
            }
            break;
          }
          default: {
            console.log(name, "other issue:", issue);
          }
        }
      }
      for (const error2 of errors) {
        log(error2);
      }
      const message = [heading, "please see the details above"].join("\n    ");
      const error = new _SchemaValidationError(message);
      error.validationErrors = errors;
      return error;
    }
  };
  function createDeviceApiCall(method, params, paramsValidator = null, resultValidator = null) {
    const deviceApiCall = new DeviceApiCall(params);
    deviceApiCall.paramsValidator = paramsValidator;
    deviceApiCall.resultValidator = resultValidator;
    deviceApiCall.method = method;
    deviceApiCall.throwOnResultKeysMissing = false;
    deviceApiCall.unwrapResult = false;
    return deviceApiCall;
  }
  function createRequest(method, params, id = "n/a", paramsValidator = null, resultValidator = null) {
    const call = createDeviceApiCall(method, params, paramsValidator, resultValidator);
    call.id = id;
    return call;
  }
  var createNotification = createDeviceApiCall;
  function validate(data, validator = null) {
    if (validator) {
      return validator.parse(data);
    }
    return data;
  }

  // packages/device-api/lib/device-api.js
  var DeviceApiTransport = class {
    /**
     * @param {import("./device-api-call.js").DeviceApiCall} _deviceApiCall
     * @param {CallOptions} [_options]
     * @returns {Promise<any>}
     */
    async send(_deviceApiCall, _options) {
      return void 0;
    }
  };
  var DeviceApi = class {
    /** @param {DeviceApiTransport} transport */
    constructor(transport) {
      /** @type {DeviceApiTransport} */
      __publicField(this, "transport");
      this.transport = transport;
    }
    /**
     * @template {import("./device-api-call").DeviceApiCall} D
     * @param {D} deviceApiCall
     * @param {CallOptions} [options]
     * @returns {Promise<NonNullable<ReturnType<D['validateResult']>['success']>>}
     */
    async request(deviceApiCall, options) {
      deviceApiCall.validateParams();
      const result = await this.transport.send(deviceApiCall, options);
      const processed = deviceApiCall.preResultValidation(result);
      return deviceApiCall.validateResult(processed);
    }
    /**
     * @template {import("./device-api-call").DeviceApiCall} P
     * @param {P} deviceApiCall
     * @param {CallOptions} [options]
     * @returns {Promise<void>}
     */
    async notify(deviceApiCall, options) {
      deviceApiCall.validateParams();
      return this.transport.send(deviceApiCall, options);
    }
  };

  // src/deviceApiCalls/__generated__/deviceApiCalls.js
  var AddDebugFlagCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "addDebugFlag");
      __publicField(this, "paramsValidator", addDebugFlagParamsSchema);
    }
  };
  var GetAutofillDataCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "getAutofillData");
      __publicField(this, "id", "getAutofillDataResponse");
      __publicField(this, "paramsValidator", getAutofillDataRequestSchema);
      __publicField(this, "resultValidator", getAutofillDataResponseSchema);
    }
  };
  var GetAutofillDataFocusCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "getAutofillDataFocus");
      __publicField(this, "id", "getAutofillDataFocusResponse");
      __publicField(this, "paramsValidator", getAutofillDataFocusRequestSchema);
      __publicField(this, "resultValidator", getAutofillDataFocusResponseSchema);
    }
  };
  var GetRuntimeConfigurationCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "getRuntimeConfiguration");
      __publicField(this, "id", "getRuntimeConfigurationResponse");
      __publicField(this, "resultValidator", getRuntimeConfigurationResponseSchema);
    }
  };
  var StoreFormDataCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "storeFormData");
      __publicField(this, "paramsValidator", storeFormDataSchema);
    }
  };
  var GetAvailableInputTypesCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "getAvailableInputTypes");
      __publicField(this, "id", "getAvailableInputTypesResponse");
      __publicField(this, "resultValidator", getAvailableInputTypesResultSchema);
    }
  };
  var GetAutofillInitDataCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "getAutofillInitData");
      __publicField(this, "id", "getAutofillInitDataResponse");
      __publicField(this, "resultValidator", getAutofillInitDataResponseSchema);
    }
  };
  var GetAutofillCredentialsCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "getAutofillCredentials");
      __publicField(this, "id", "getAutofillCredentialsResponse");
      __publicField(this, "paramsValidator", getAutofillCredentialsParamsSchema);
      __publicField(this, "resultValidator", getAutofillCredentialsResultSchema);
    }
  };
  var SetSizeCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "setSize");
      __publicField(this, "paramsValidator", setSizeParamsSchema);
    }
  };
  var SelectedDetailCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "selectedDetail");
      __publicField(this, "paramsValidator", selectedDetailParamsSchema);
    }
  };
  var CloseAutofillParentCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "closeAutofillParent");
    }
  };
  var AskToUnlockProviderCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "askToUnlockProvider");
      __publicField(this, "id", "askToUnlockProviderResponse");
      __publicField(this, "resultValidator", askToUnlockProviderResultSchema);
    }
  };
  var CheckCredentialsProviderStatusCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "checkCredentialsProviderStatus");
      __publicField(this, "id", "checkCredentialsProviderStatusResponse");
      __publicField(this, "resultValidator", checkCredentialsProviderStatusResultSchema);
    }
  };
  var SendJSPixelCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "sendJSPixel");
      __publicField(this, "paramsValidator", sendJSPixelParamsSchema);
    }
  };
  var SetIncontextSignupPermanentlyDismissedAtCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "setIncontextSignupPermanentlyDismissedAt");
      __publicField(this, "paramsValidator", setIncontextSignupPermanentlyDismissedAtSchema);
    }
  };
  var GetIncontextSignupDismissedAtCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "getIncontextSignupDismissedAt");
      __publicField(this, "id", "getIncontextSignupDismissedAt");
      __publicField(this, "resultValidator", getIncontextSignupDismissedAtSchema);
    }
  };
  var OpenManagePasswordsCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "openManagePasswords");
    }
  };
  var OpenManageCreditCardsCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "openManageCreditCards");
    }
  };
  var OpenManageIdentitiesCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "openManageIdentities");
    }
  };
  var StartCredentialsImportFlowCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "startCredentialsImportFlow");
    }
  };
  var GetIdentityCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "getIdentity");
      __publicField(this, "id", "getIdentityResponse");
      __publicField(this, "resultValidator", getIdentityResultSchema);
    }
  };
  var GetCreditCardCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "getCreditCard");
      __publicField(this, "id", "getCreditCardResponse");
      __publicField(this, "resultValidator", getCreditCardResultSchema);
    }
  };
  var CredentialsImportFlowPermanentlyDismissedCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "credentialsImportFlowPermanentlyDismissed");
    }
  };
  var EmailProtectionStoreUserDataCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "emailProtectionStoreUserData");
      __publicField(this, "id", "emailProtectionStoreUserDataResponse");
      __publicField(this, "paramsValidator", emailProtectionStoreUserDataParamsSchema);
    }
  };
  var EmailProtectionRemoveUserDataCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "emailProtectionRemoveUserData");
    }
  };
  var EmailProtectionGetIsLoggedInCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "emailProtectionGetIsLoggedIn");
      __publicField(this, "id", "emailProtectionGetIsLoggedInResponse");
      __publicField(this, "resultValidator", emailProtectionGetIsLoggedInResultSchema);
    }
  };
  var EmailProtectionGetUserDataCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "emailProtectionGetUserData");
      __publicField(this, "id", "emailProtectionGetUserDataResponse");
      __publicField(this, "resultValidator", emailProtectionGetUserDataResultSchema);
    }
  };
  var EmailProtectionGetCapabilitiesCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "emailProtectionGetCapabilities");
      __publicField(this, "id", "emailProtectionGetCapabilitiesResponse");
      __publicField(this, "resultValidator", emailProtectionGetCapabilitiesResultSchema);
    }
  };
  var EmailProtectionGetAddressesCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "emailProtectionGetAddresses");
      __publicField(this, "id", "emailProtectionGetAddressesResponse");
      __publicField(this, "resultValidator", emailProtectionGetAddressesResultSchema);
    }
  };
  var EmailProtectionRefreshPrivateAddressCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "emailProtectionRefreshPrivateAddress");
      __publicField(this, "id", "emailProtectionRefreshPrivateAddressResponse");
      __publicField(this, "resultValidator", emailProtectionRefreshPrivateAddressResultSchema);
    }
  };
  var StartEmailProtectionSignupCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "startEmailProtectionSignup");
    }
  };
  var CloseEmailProtectionTabCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "closeEmailProtectionTab");
    }
  };
  var ShowInContextEmailProtectionSignupPromptCall = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "ShowInContextEmailProtectionSignupPrompt");
      __publicField(this, "id", "ShowInContextEmailProtectionSignupPromptResponse");
      __publicField(this, "resultValidator", showInContextEmailProtectionSignupPromptSchema);
    }
  };

  // src/UI/controllers/NativeUIController.js
  var _passwordStatus;
  var NativeUIController = class extends UIController {
    constructor() {
      super(...arguments);
      /**
       * Keep track of when passwords were suggested/rejected/accepted etc
       * State is kept here because it's specific to the interactions on mobile (eg: NativeUIController)
       *
       * @type {"default" | "rejected"}
       */
      __privateAdd(this, _passwordStatus, "default");
    }
    /**
     * @param {import('./UIController').AttachTooltipArgs} args
     */
    attachTooltip(args) {
      const { form, input, device, trigger, triggerMetaData, topContextData } = args;
      const inputType = getInputType(input);
      const mainType = getMainTypeFromType(inputType);
      const subType = getSubtypeFromType(inputType);
      if (mainType === "unknown") {
        throw new Error('unreachable, should not be here if (mainType === "unknown")');
      }
      if (trigger === "autoprompt") {
        window.scrollTo({
          behavior: "smooth",
          top: form.form.getBoundingClientRect().top - document.body.getBoundingClientRect().top - 50
        });
      }
      let payload = {
        inputType,
        mainType,
        subType,
        trigger
      };
      if (device.settings.featureToggles.password_generation) {
        payload = this.appendGeneratedPassword(topContextData, payload, triggerMetaData);
      }
      device.deviceApi.request(new GetAutofillDataCall(payload)).then((resp) => {
        switch (resp.action) {
          case "fill": {
            if (mainType in resp) {
              form.autofillData(resp[mainType], mainType);
            } else {
              throw new Error(`action: "fill" cannot occur because "${mainType}" was missing`);
            }
            break;
          }
          case "focus": {
            form.activeInput?.focus();
            break;
          }
          case "refreshAvailableInputTypes": {
            device.credentialsImport.refresh(resp.availableInputTypes);
            break;
          }
          case "acceptGeneratedPassword": {
            form.autofillData(
              {
                password: topContextData.credentials?.[0].password,
                [AUTOGENERATED_KEY]: true
              },
              mainType
            );
            break;
          }
          case "none": {
            form.touchAllInputs(mainType);
            break;
          }
          case "rejectGeneratedPassword": {
            __privateSet(this, _passwordStatus, "rejected");
            form.touchAllInputs("credentials");
            form.activeInput?.focus();
            break;
          }
          default: {
            if (args.device.isTestMode()) {
              console.warn("response not handled", resp);
            }
          }
        }
      }).catch((e) => {
        console.error("NativeTooltip::device.getAutofillData(payload)");
        console.error(e);
      });
    }
    /**
     * @param {import('./UIController').AttachKeyboardArgs} args
     */
    async attachKeyboard(args) {
      const { device, form, element } = args;
      const inputType = getInputType(element);
      const mainType = getMainTypeFromType(inputType);
      try {
        const resp = await device.deviceApi.request(
          new GetAutofillDataFocusCall({
            inputType,
            mainType
          })
        );
        switch (resp.action) {
          case "fill": {
            form?.autofillData(resp.creditCards, "creditCards");
            element.blur();
            break;
          }
          case "none": {
            break;
          }
        }
      } catch (e) {
        console.error("NativeTooltip::device.getAutofillDataFocus()");
        console.error(e);
      }
    }
    /**
     * If a password exists in `topContextData`, we can append it to the outgoing data
     * in a way that native platforms can easily understand.
     *
     * @param {TopContextData} topContextData
     * @param {import('../../deviceApiCalls/__generated__/validators-ts.js').GetAutofillDataRequest} outgoingData
     * @param {import('../../UI/controllers/UIController.js').AttachTooltipArgs['triggerMetaData']} triggerMetaData
     * @return {import('../../deviceApiCalls/__generated__/validators-ts.js').GetAutofillDataRequest}
     */
    appendGeneratedPassword(topContextData, outgoingData, triggerMetaData) {
      const autoGeneratedCredential = topContextData.credentials?.find((credential) => credential.autogenerated);
      if (!autoGeneratedCredential?.password) {
        return outgoingData;
      }
      function suggestPassword() {
        if (!autoGeneratedCredential?.password) throw new Error("unreachable");
        return {
          ...outgoingData,
          generatedPassword: {
            value: autoGeneratedCredential.password,
            username: autoGeneratedCredential.username
          }
        };
      }
      if (triggerMetaData.type === "explicit-opt-in") {
        return suggestPassword();
      }
      if (triggerMetaData.type === "implicit-opt-in" && __privateGet(this, _passwordStatus) !== "rejected") {
        return suggestPassword();
      }
      return outgoingData;
    }
  };
  _passwordStatus = new WeakMap();

  // packages/messaging/webkit.js
  var WebkitMessagingTransport = class {
    /**
     * @param {WebkitMessagingConfig} config
     */
    constructor(config) {
      /** @type {WebkitMessagingConfig} */
      __publicField(this, "config");
      __publicField(this, "globals");
      /**
       * @type {{name: string, length: number}}
       */
      __publicField(this, "algoObj", { name: "AES-GCM", length: 256 });
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
      const outgoing = {
        ...data,
        messageHandling: { ...data.messageHandling, secret: this.config.secret }
      };
      if (!this.config.hasModernWebkitAPI) {
        if (!(handler in this.globals.capturedWebkitHandlers)) {
          throw new MissingHandler(`cannot continue, method ${handler} not captured on macos < 11`, handler);
        } else {
          return this.globals.capturedWebkitHandlers[handler](outgoing);
        }
      }
      return this.globals.window.webkit.messageHandlers[handler].postMessage?.(outgoing);
    }
    /**
     * Sends message to the webkit layer and waits for the specified response
     * @param {String} handler
     * @param {*} data
     * @returns {Promise<*>}
     * @internal
     */
    async wkSendAndWait(handler, data = {}) {
      if (this.config.hasModernWebkitAPI) {
        const response = await this.wkSend(handler, data);
        return this.globals.JSONparse(response || "{}");
      }
      try {
        const randMethodName = this.createRandMethodName();
        const key2 = await this.createRandKey();
        const iv = this.createRandIv();
        const { ciphertext, tag } = await new this.globals.Promise((resolve) => {
          this.generateRandomMethod(randMethodName, resolve);
          data.messageHandling = new SecureMessagingParams({
            methodName: randMethodName,
            secret: this.config.secret,
            key: this.globals.Arrayfrom(key2),
            iv: this.globals.Arrayfrom(iv)
          });
          this.wkSend(handler, data);
        });
        const cipher = new this.globals.Uint8Array([...ciphertext, ...tag]);
        const decrypted = await this.decrypt(cipher, key2, iv);
        return this.globals.JSONparse(decrypted || "{}");
      } catch (e) {
        if (e instanceof MissingHandler) {
          throw e;
        } else {
          console.error("decryption failed", e);
          console.error(e);
          return { error: e };
        }
      }
    }
    /**
     * @param {string} name
     * @param {Record<string, any>} [data]
     */
    notify(name, data = {}) {
      this.wkSend(name, data);
    }
    /**
     * @param {string} name
     * @param {Record<string, any>} [data]
     */
    request(name, data = {}) {
      return this.wkSendAndWait(name, data);
    }
    /**
     * Generate a random method name and adds it to the global scope
     * The native layer will use this method to send the response
     * @param {string | number} randomMethodName
     * @param {Function} callback
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
    randomString() {
      return "" + this.globals.getRandomValues(new this.globals.Uint32Array(1))[0];
    }
    createRandMethodName() {
      return "_" + this.randomString();
    }
    /**
     * @returns {Promise<Uint8Array>}
     */
    async createRandKey() {
      const key2 = await this.globals.generateKey(this.algoObj, true, ["encrypt", "decrypt"]);
      const exportedKey = await this.globals.exportKey("raw", key2);
      return new this.globals.Uint8Array(exportedKey);
    }
    /**
     * @returns {Uint8Array}
     */
    createRandIv() {
      return this.globals.getRandomValues(new this.globals.Uint8Array(12));
    }
    /**
     * @param {BufferSource} ciphertext
     * @param {BufferSource} key
     * @param {Uint8Array} iv
     * @returns {Promise<string>}
     */
    async decrypt(ciphertext, key2, iv) {
      const cryptoKey = await this.globals.importKey("raw", key2, "AES-GCM", false, ["decrypt"]);
      const algo = { name: "AES-GCM", iv };
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
  };
  var WebkitMessagingConfig = class {
    /**
     * @param {object} params
     * @param {boolean} params.hasModernWebkitAPI
     * @param {string[]} params.webkitMessageHandlerNames
     * @param {string} params.secret
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
      ObjectDefineProperty: window.Object.defineProperty,
      addEventListener: window.addEventListener.bind(window),
      /** @type {Record<string, any>} */
      capturedWebkitHandlers: {}
    };
  }

  // packages/messaging/messaging.js
  var Messaging = class {
    /**
     * @param {WebkitMessagingConfig} config
     */
    constructor(config) {
      this.transport = getTransport(config);
    }
    /**
     * Send a 'fire-and-forget' message.
     * @throws {Error}
     * {@link MissingHandler}
     *
     * @example
     *
     * ```
     * const messaging = new Messaging(config)
     * messaging.notify("foo", {bar: "baz"})
     * ```
     * @param {string} name
     * @param {Record<string, any>} [data]
     */
    notify(name, data = {}) {
      this.transport.notify(name, data);
    }
    /**
     * Send a request, and wait for a response
     * @throws {Error}
     * {@link MissingHandler}
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
      return this.transport.request(name, data);
    }
  };
  function getTransport(config) {
    if (config instanceof WebkitMessagingConfig) {
      return new WebkitMessagingTransport(config);
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

  // src/deviceApiCalls/transports/apple.transport.js
  var AppleTransport = class extends DeviceApiTransport {
    /** @param {GlobalConfig} globalConfig */
    constructor(globalConfig) {
      super();
      this.config = globalConfig;
      const webkitConfig = new WebkitMessagingConfig({
        hasModernWebkitAPI: this.config.hasModernWebkitAPI,
        webkitMessageHandlerNames: this.config.webkitMessageHandlerNames,
        secret: this.config.secret
      });
      this.messaging = new Messaging(webkitConfig);
    }
    async send(deviceApiCall) {
      try {
        if (deviceApiCall.id) {
          return await this.messaging.request(deviceApiCall.method, deviceApiCall.params || void 0);
        } else {
          return this.messaging.notify(deviceApiCall.method, deviceApiCall.params || void 0);
        }
      } catch (e) {
        if (e instanceof MissingHandler) {
          if (this.config.isDDGTestMode) {
            console.log("MissingWebkitHandler error for:", deviceApiCall.method);
          }
          throw new Error("unimplemented handler: " + deviceApiCall.method);
        } else {
          throw e;
        }
      }
    }
  };

  // src/deviceApiCalls/transports/android.transport.js
  var AndroidTransport = class extends DeviceApiTransport {
    /** @param {GlobalConfig} globalConfig */
    constructor(globalConfig) {
      super();
      /** @type {GlobalConfig} */
      __publicField(this, "config");
      this.config = globalConfig;
      if (this.config.isDDGTestMode) {
        if (typeof window.BrowserAutofill?.getAutofillData !== "function") {
          console.warn("window.BrowserAutofill.getAutofillData missing");
        }
        if (typeof window.BrowserAutofill?.storeFormData !== "function") {
          console.warn("window.BrowserAutofill.storeFormData missing");
        }
      }
    }
    /**
     * @param {import("../../../packages/device-api").DeviceApiCall} deviceApiCall
     * @returns {Promise<any>}
     */
    async send(deviceApiCall) {
      if (deviceApiCall instanceof GetRuntimeConfigurationCall) {
        return androidSpecificRuntimeConfiguration(this.config);
      }
      if (deviceApiCall instanceof GetAvailableInputTypesCall) {
        return androidSpecificAvailableInputTypes(this.config);
      }
      if (deviceApiCall instanceof GetIncontextSignupDismissedAtCall) {
        window.BrowserAutofill.getIncontextSignupDismissedAt(JSON.stringify(deviceApiCall.params));
        return waitForResponse(deviceApiCall.id, this.config);
      }
      if (deviceApiCall instanceof SetIncontextSignupPermanentlyDismissedAtCall) {
        return window.BrowserAutofill.setIncontextSignupPermanentlyDismissedAt(JSON.stringify(deviceApiCall.params));
      }
      if (deviceApiCall instanceof StartEmailProtectionSignupCall) {
        return window.BrowserAutofill.startEmailProtectionSignup(JSON.stringify(deviceApiCall.params));
      }
      if (deviceApiCall instanceof CloseEmailProtectionTabCall) {
        return window.BrowserAutofill.closeEmailProtectionTab(JSON.stringify(deviceApiCall.params));
      }
      if (deviceApiCall instanceof ShowInContextEmailProtectionSignupPromptCall) {
        window.BrowserAutofill.showInContextEmailProtectionSignupPrompt(JSON.stringify(deviceApiCall.params));
        return waitForResponse(deviceApiCall.id, this.config);
      }
      if (deviceApiCall instanceof GetAutofillDataCall) {
        window.BrowserAutofill.getAutofillData(JSON.stringify(deviceApiCall.params));
        return waitForResponse(deviceApiCall.id, this.config);
      }
      if (deviceApiCall instanceof StoreFormDataCall) {
        return window.BrowserAutofill.storeFormData(JSON.stringify(deviceApiCall.params));
      }
      throw new Error("android: not implemented: " + deviceApiCall.method);
    }
  };
  function waitForResponse(expectedResponse, config) {
    return new Promise((resolve) => {
      const handler = (e) => {
        if (!config.isDDGTestMode) {
          if (e.origin !== "") {
            return;
          }
        }
        if (!e.data) {
          return;
        }
        if (typeof e.data !== "string") {
          if (config.isDDGTestMode) {
            console.log("\u274C event.data was not a string. Expected a string so that it can be JSON parsed");
          }
          return;
        }
        try {
          const data = JSON.parse(e.data);
          if (data.type === expectedResponse) {
            window.removeEventListener("message", handler);
            return resolve(data);
          }
          if (config.isDDGTestMode) {
            console.log(`\u274C event.data.type was '${data.type}', which didnt match '${expectedResponse}'`, JSON.stringify(data));
          }
        } catch (e2) {
          window.removeEventListener("message", handler);
          if (config.isDDGTestMode) {
            console.log("\u274C Could not JSON.parse the response");
          }
        }
      };
      window.addEventListener("message", handler);
    });
  }
  function androidSpecificRuntimeConfiguration(globalConfig) {
    if (!globalConfig.userPreferences) {
      throw new Error("globalConfig.userPreferences not supported yet on Android");
    }
    return {
      success: {
        // @ts-ignore
        contentScope: globalConfig.contentScope,
        // @ts-ignore
        userPreferences: globalConfig.userPreferences,
        // @ts-ignore
        userUnprotectedDomains: globalConfig.userUnprotectedDomains,
        // @ts-ignore
        availableInputTypes: globalConfig.availableInputTypes
      }
    };
  }
  function androidSpecificAvailableInputTypes(globalConfig) {
    if (!globalConfig.availableInputTypes) {
      throw new Error("globalConfig.availableInputTypes not supported yet on Android");
    }
    return {
      success: globalConfig.availableInputTypes
    };
  }

  // node_modules/immutable-json-patch/lib/esm/typeguards.js
  function isJSONArray(value) {
    return Array.isArray(value);
  }
  function isJSONObject(value) {
    return value !== null && typeof value === "object" && (value.constructor === void 0 || // for example Object.create(null)
    value.constructor.name === "Object");
  }

  // node_modules/immutable-json-patch/lib/esm/utils.js
  function isEqual(a, b) {
    return JSON.stringify(a) === JSON.stringify(b);
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

  // node_modules/immutable-json-patch/lib/esm/immutabilityHelpers.js
  function shallowClone(value) {
    if (isJSONArray(value)) {
      const copy2 = value.slice();
      Object.getOwnPropertySymbols(value).forEach((symbol) => {
        copy2[symbol] = value[symbol];
      });
      return copy2;
    } else if (isJSONObject(value)) {
      const copy2 = {
        ...value
      };
      Object.getOwnPropertySymbols(value).forEach((symbol) => {
        copy2[symbol] = value[symbol];
      });
      return copy2;
    } else {
      return value;
    }
  }
  function applyProp(object, key2, value) {
    if (object[key2] === value) {
      return object;
    } else {
      const updatedObject = shallowClone(object);
      updatedObject[key2] = value;
      return updatedObject;
    }
  }
  function getIn(object, path) {
    let value = object;
    let i = 0;
    while (i < path.length) {
      if (isJSONObject(value)) {
        value = value[path[i]];
      } else if (isJSONArray(value)) {
        value = value[parseInt(path[i])];
      } else {
        value = void 0;
      }
      i++;
    }
    return value;
  }
  function setIn(object, path, value) {
    let createPath = arguments.length > 3 && arguments[3] !== void 0 ? arguments[3] : false;
    if (path.length === 0) {
      return value;
    }
    const key2 = path[0];
    const updatedValue = setIn(object ? object[key2] : void 0, path.slice(1), value, createPath);
    if (isJSONObject(object) || isJSONArray(object)) {
      return applyProp(object, key2, updatedValue);
    } else {
      if (createPath) {
        const newObject = IS_INTEGER_REGEX.test(key2) ? [] : {};
        newObject[key2] = updatedValue;
        return newObject;
      } else {
        throw new Error("Path does not exist");
      }
    }
  }
  var IS_INTEGER_REGEX = /^\d+$/;
  function updateIn(object, path, transform) {
    if (path.length === 0) {
      return transform(object);
    }
    if (!isObjectOrArray(object)) {
      throw new Error("Path doesn't exist");
    }
    const key2 = path[0];
    const updatedValue = updateIn(object[key2], path.slice(1), transform);
    return applyProp(object, key2, updatedValue);
  }
  function deleteIn(object, path) {
    if (path.length === 0) {
      return object;
    }
    if (!isObjectOrArray(object)) {
      throw new Error("Path does not exist");
    }
    if (path.length === 1) {
      const key3 = path[0];
      if (!(key3 in object)) {
        return object;
      } else {
        const updatedObject = shallowClone(object);
        if (isJSONArray(updatedObject)) {
          updatedObject.splice(parseInt(key3), 1);
        }
        if (isJSONObject(updatedObject)) {
          delete updatedObject[key3];
        }
        return updatedObject;
      }
    }
    const key2 = path[0];
    const updatedValue = deleteIn(object[key2], path.slice(1));
    return applyProp(object, key2, updatedValue);
  }
  function insertAt(document2, path, value) {
    const parentPath = path.slice(0, path.length - 1);
    const index = path[path.length - 1];
    return updateIn(document2, parentPath, (items) => {
      if (!Array.isArray(items)) {
        throw new TypeError("Array expected at path " + JSON.stringify(parentPath));
      }
      const updatedItems = shallowClone(items);
      updatedItems.splice(parseInt(index), 0, value);
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

  // node_modules/immutable-json-patch/lib/esm/jsonPointer.js
  function parseJSONPointer(pointer) {
    const path = pointer.split("/");
    path.shift();
    return path.map((p) => p.replace(/~1/g, "/").replace(/~0/g, "~"));
  }
  function compileJSONPointer(path) {
    return path.map(compileJSONPointerProp).join("");
  }
  function compileJSONPointerProp(pathProp) {
    return "/" + String(pathProp).replace(/~/g, "~0").replace(/\//g, "~1");
  }

  // node_modules/immutable-json-patch/lib/esm/immutableJSONPatch.js
  function immutableJSONPatch(document2, operations, options) {
    let updatedDocument = document2;
    for (let i = 0; i < operations.length; i++) {
      validateJSONPatchOperation(operations[i]);
      let operation = operations[i];
      if (options && options.before) {
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
        throw new Error("Unknown JSONPatch operation " + JSON.stringify(operation));
      }
      if (options && options.after) {
        const result = options.after(updatedDocument, operation, previousDocument);
        if (result !== void 0) {
          updatedDocument = result;
        }
      }
    }
    return updatedDocument;
  }
  function replace(document2, path, value) {
    return setIn(document2, path, value);
  }
  function remove(document2, path) {
    return deleteIn(document2, path);
  }
  function add(document2, path, value) {
    if (isArrayItem(document2, path)) {
      return insertAt(document2, path, value);
    } else {
      return setIn(document2, path, value);
    }
  }
  function copy(document2, path, from) {
    const value = getIn(document2, from);
    if (isArrayItem(document2, path)) {
      return insertAt(document2, path, value);
    } else {
      const value2 = getIn(document2, from);
      return setIn(document2, path, value2);
    }
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
      throw new Error("Unknown JSONPatch op " + JSON.stringify(operation.op));
    }
    if (typeof operation.path !== "string") {
      throw new Error('Required property "path" missing or not a string in operation ' + JSON.stringify(operation));
    }
    if (operation.op === "copy" || operation.op === "move") {
      if (typeof operation.from !== "string") {
        throw new Error('Required property "from" missing or not a string in operation ' + JSON.stringify(operation));
      }
    }
  }
  function parsePath(document2, pointer) {
    return resolvePathIndex(document2, parseJSONPointer(pointer));
  }
  function parseFrom(fromPointer) {
    return parseJSONPointer(fromPointer);
  }

  // node_modules/@duckduckgo/content-scope-scripts/injected/src/config-feature.js
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
      /** @type {{ debug?: boolean, desktopModeEnabled?: boolean, forcedZoomEnabled?: boolean, featureSettings?: Record<string, unknown>, assets?: import('./content-feature.js').AssetConfig | undefined, site: import('./content-feature.js').Site, messagingConfig?: import('@duckduckgo/messaging').MessagingConfig } | null} */
      __privateAdd(this, _args);
      this.name = name;
      const { bundledConfig, site, platform } = args;
      __privateSet(this, _bundledConfig, bundledConfig);
      __privateSet(this, _args, args);
      if (__privateGet(this, _bundledConfig) && __privateGet(this, _args)) {
        const enabledFeatures = computeEnabledFeatures(bundledConfig, site.domain, platform.version);
        __privateGet(this, _args).featureSettings = parseFeatureSettings(bundledConfig, enabledFeatures);
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
     * Given a config key, interpret the value as a list of domain overrides, and return the elements that match the current page
     * Consider using patchSettings instead as per `getFeatureSetting`.
     * @param {string} featureKeyName
     * @return {any[]}
     * @protected
     */
    matchDomainFeatureSetting(featureKeyName) {
      const domain = this.args?.site.domain;
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
     * This also supports domain overrides as per `getFeatureSetting`.
     * @param {string} featureKeyName
     * @param {string} [featureName]
     * @returns {boolean}
     */
    getFeatureSettingEnabled(featureKeyName, featureName) {
      const result = this.getFeatureSetting(featureKeyName, featureName);
      if (typeof result === "object") {
        return result.state === "enabled";
      }
      return result === "enabled";
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
      if (featureKeyName === "domains") {
        throw new Error("domains is a reserved feature setting key name");
      }
      const domainMatch = [...this.matchDomainFeatureSetting("domains")].sort((a, b) => {
        return a.domain.length - b.domain.length;
      });
      for (const match of domainMatch) {
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

  // src/site-specific-feature.js
  var FEATURE_NAME = "siteSpecificFixes";
  var SiteSpecificFeature = class extends ConfigFeature {
    constructor(args) {
      super(FEATURE_NAME, args);
    }
    /**
     * @returns {InputTypeSetting[]}
     */
    get inputTypeSettings() {
      return this.getFeatureSetting("inputTypeSettings") || [];
    }
    /**
     * @param {HTMLInputElement} input
     * @returns {import('./Form/matching').SupportedTypes | null}
     */
    getForcedInputType(input) {
      const setting = this.inputTypeSettings.find((config) => input.matches(config.selector));
      if (!isValidSupportedType(setting?.type)) return null;
      return setting?.type;
    }
    /**
     * @returns {FormTypeSetting[]}
     */
    get formTypeSettings() {
      return this.getFeatureSetting("formTypeSettings") || [];
    }
    /**
     * @returns {FormBoundarySelector|null}
     */
    get formBoundarySelector() {
      return this.getFeatureSetting("formBoundarySelector");
    }
    /**
     * @returns {FailsafeSettings}
     */
    get failsafeSettings() {
      return this.getFeatureSetting("failsafeSettings");
    }
    /**
     * @returns {number|undefined}
     */
    get maxInputsPerPage() {
      return this.failsafeSettings?.maxInputsPerPage;
    }
    /**
     * @returns {number|undefined}
     */
    get maxFormsPerPage() {
      return this.failsafeSettings?.maxFormsPerPage;
    }
    /**
     * @returns {number|undefined}
     */
    get maxInputsPerForm() {
      return this.failsafeSettings?.maxInputsPerForm;
    }
    /**
     * Checks if there's a forced form type configuration for the given form element
     * @param {HTMLElement} form
     * @returns {string|null|undefined}
     */
    getForcedFormType(form) {
      return this.formTypeSettings.find((config) => form.matches(config.selector))?.type;
    }
    /**
     * @returns {HTMLElement|null}
     */
    getForcedForm() {
      return this.formBoundarySelector ? document.querySelector(this.formBoundarySelector) : null;
    }
  };

  // src/Settings.js
  var _Settings = class _Settings {
    /**
     * @param {GlobalConfig} config
     * @param {DeviceApi} deviceApi
     */
    constructor(config, deviceApi) {
      /** @type {GlobalConfig} */
      __publicField(this, "globalConfig");
      /** @type {DeviceApi} */
      __publicField(this, "deviceApi");
      /** @type {AutofillFeatureToggles | null} */
      __publicField(this, "_featureToggles", null);
      /** @type {AvailableInputTypes | null} */
      __publicField(this, "_availableInputTypes", null);
      /** @type {RuntimeConfiguration | null | undefined} */
      __publicField(this, "_runtimeConfiguration", null);
      /** @type {boolean | null} */
      __publicField(this, "_enabled", null);
      /** @type {string} */
      __publicField(this, "_language", "en");
      /** @type {SiteSpecificFeature | null} */
      __publicField(this, "_siteSpecificFeature", null);
      this.deviceApi = deviceApi;
      this.globalConfig = config;
    }
    /**
     * Feature toggles are delivered as part of the Runtime Configuration - a flexible design that
     * allows data per user + remote config to be accessed together.
     *
     * Once we access the Runtime Configuration, we then extract the autofill-specific settings via
     * `runtimeConfig.userPreferences.features.autofill.settings` and validate that separately.
     *
     * The 2-step validation occurs because RuntimeConfiguration will be coming from a shared library
     * and does not know about the shape of Autofill specific settings.
     *
     * @returns {Promise<AutofillFeatureToggles>}
     */
    async getFeatureToggles() {
      try {
        const runtimeConfig = await this._getRuntimeConfiguration();
        const autofillSettings = validate(runtimeConfig.userPreferences?.features?.autofill?.settings, autofillSettingsSchema);
        return autofillSettings.featureToggles;
      } catch (e) {
        if (this.globalConfig.isDDGTestMode) {
          console.log("isDDGTestMode: getFeatureToggles: \u274C", e);
        }
        return _Settings.defaults.featureToggles;
      }
    }
    /**
     * If the platform can derive its 'enabled' state from the RuntimeConfiguration,
     * then it should use this. Currently, only Windows supports this, but we plan to extend support
     * to all platforms in the future.
     * @returns {Promise<boolean|null>}
     */
    async getEnabled() {
      try {
        const runtimeConfig = await this._getRuntimeConfiguration();
        const enabled = autofillEnabled(runtimeConfig);
        return enabled;
      } catch (e) {
        if (this.globalConfig.isDDGTestMode) {
          console.log("isDDGTestMode: getEnabled: \u274C", e);
        }
        return null;
      }
    }
    /**
     * Retrieves the user's language from the current platform's `RuntimeConfiguration`. If the
     * platform doesn't include a two-character `.userPreferences.language` property in its runtime
     * configuration, or if an error occurs, 'en' is used as a fallback.
     *
     * NOTE: This function returns the two-character 'language' code of a typical POSIX locale
     * (e.g. 'en', 'de', 'fr') listed in ISO 639-1[1].
     *
     * [1] https://en.wikipedia.org/wiki/ISO_639-1
     *
     * @returns {Promise<string>} the device's current language code, or 'en' if something goes wrong
     */
    async getLanguage() {
      try {
        const conf = await this._getRuntimeConfiguration();
        const language = conf.userPreferences.language ?? "en";
        if (language.length !== 2) {
          console.warn(`config.userPreferences.language must be two characters, but received '${language}'`);
          return "en";
        }
        return language;
      } catch (e) {
        if (this.globalConfig.isDDGTestMode) {
          console.log("isDDGTestMode: getLanguage: \u274C", e);
        }
        return "en";
      }
    }
    /**
     * Get runtime configuration, but only once.
     *
     * Some platforms may be reading this directly from inlined variables, whilst others
     * may make a DeviceApiCall.
     *
     * Currently, it's only read once - but we should be open to the idea that we may need
     * this to be called multiple times in the future.
     *
     * @returns {Promise<RuntimeConfiguration>}
     * @throws
     * @private
     */
    async _getRuntimeConfiguration() {
      if (this._runtimeConfiguration) return this._runtimeConfiguration;
      const runtimeConfig = await this.deviceApi.request(new GetRuntimeConfigurationCall(null));
      this._runtimeConfiguration = runtimeConfig;
      return this._runtimeConfiguration;
    }
    /**
     * Available Input Types are boolean indicators to represent which input types the
     * current **user** has data available for.
     *
     * @returns {Promise<AvailableInputTypes>}
     */
    async getAvailableInputTypes() {
      try {
        if (this.globalConfig.isTopFrame) {
          return _Settings.defaults.availableInputTypes;
        }
        return await this.deviceApi.request(new GetAvailableInputTypesCall(null));
      } catch (e) {
        if (this.globalConfig.isDDGTestMode) {
          console.log("isDDGTestMode: getAvailableInputTypes: \u274C", e);
        }
        return _Settings.defaults.availableInputTypes;
      }
    }
    /**
     * @returns {SiteSpecificFeature|null}
     */
    get siteSpecificFeature() {
      return this._siteSpecificFeature;
    }
    /**
     * WORKAROUND: Currently C-S-S only suppports parsing top level features, so we need to manually allow
     * setting top level features in the content scope from nested features.
     * @param {RuntimeConfiguration} runtimeConfig
     * @param {string} name
     * @returns {RuntimeConfiguration}
     */
    setTopLevelFeatureInContentScopeIfNeeded(runtimeConfig, name) {
      const contentScope = (
        /** @type {import("@duckduckgo/privacy-configuration/schema/config").CurrentGenericConfig} */
        runtimeConfig.contentScope
      );
      const feature = contentScope.features?.autofill?.features?.[name];
      if (feature?.state !== "enabled" || contentScope.features[name]) return runtimeConfig;
      if (feature) {
        runtimeConfig.contentScope.features = {
          ...contentScope.features,
          [name]: {
            ...feature,
            exceptions: [],
            hash: ""
          }
        };
      }
      return runtimeConfig;
    }
    async getsiteSpecificFeature() {
      if (this._siteSpecificFeature) return this._siteSpecificFeature;
      try {
        const runtimeConfig = await this._getRuntimeConfiguration();
        this.setTopLevelFeatureInContentScopeIfNeeded(runtimeConfig, "siteSpecificFixes");
        const args = processConfig(runtimeConfig.contentScope, runtimeConfig.userUnprotectedDomains, runtimeConfig.userPreferences);
        return new SiteSpecificFeature(args);
      } catch (e) {
        if (this.globalConfig.isDDGTestMode) {
          console.log("isDDGTestMode: getsiteSpecificFeature: \u274C", e);
        }
        return _Settings.defaults.siteSpecificFeature;
      }
    }
    setsiteSpecificFeature(siteSpecificFeature) {
      if (this._siteSpecificFeature) return;
      this._siteSpecificFeature = siteSpecificFeature;
    }
    /**
     * To 'refresh' settings means to re-call APIs to determine new state. This may
     * only occur once per page, but it must be done before any page scanning/decorating
     * or translation can happen.
     *
     * @returns {Promise<{
     *      availableInputTypes: AvailableInputTypes,
     *      featureToggles: AutofillFeatureToggles,
     *      enabled: boolean | null
     * }>}
     */
    async refresh() {
      this.setEnabled(await this.getEnabled());
      this.setsiteSpecificFeature(await this.getsiteSpecificFeature());
      this.setFeatureToggles(await this.getFeatureToggles());
      this.setAvailableInputTypes(await this.getAvailableInputTypes());
      this.setLanguage(await this.getLanguage());
      if (typeof this.enabled === "boolean") {
        if (!this.enabled) {
          return _Settings.defaults;
        }
      }
      return {
        featureToggles: this.featureToggles,
        availableInputTypes: this.availableInputTypes,
        enabled: this.enabled
      };
    }
    /**
     * Checks if input type is one which we can't autofill
     * @param {{
     *   mainType: SupportedMainTypes
     *   subtype: import('./Form/matching.js').SupportedSubTypes | "unknown"
     *   variant?: import('./Form/matching.js').SupportedVariants | ""
     * }} types
     * @returns {boolean}
     */
    isTypeUnavailable({ mainType, subtype, variant }) {
      if (mainType === "unknown") return true;
      if (subtype === "password" && variant === "new") {
        return !this.featureToggles.password_generation;
      }
      if (!this.featureToggles[`inputType_${mainType}`] && subtype !== "emailAddress") {
        return true;
      }
      return false;
    }
    /**
     * Requests data from remote
     * @returns {Promise<>}
     */
    async populateData() {
      const availableInputTypesFromRemote = await this.getAvailableInputTypes();
      this.setAvailableInputTypes(availableInputTypesFromRemote);
    }
    /**
     * Requests data from remote if not available
     * @param {{
     *   mainType: SupportedMainTypes
     *   subtype: import('./Form/matching.js').SupportedSubTypes | "unknown"
     *   variant?: import('./Form/matching.js').SupportedVariants | ""
     * }} types
     * @returns {Promise<boolean>}
     */
    async populateDataIfNeeded({ mainType, subtype, variant }) {
      if (this.isTypeUnavailable({ mainType, subtype, variant })) return false;
      if (this.availableInputTypes?.[mainType] === void 0) {
        await this.populateData();
        return true;
      }
      return false;
    }
    /**
     * Checks if items will show in the autofill menu, including in-context signup.
     * Triggers side-effect if input types is not already available.
     * @param {{
     *   mainType: SupportedMainTypes
     *   subtype: import('./Form/matching.js').SupportedSubTypes | "unknown"
     *   variant: import('./Form/matching.js').SupportedVariants | ""
     * }} types
     * @param {import("./InContextSignup.js").InContextSignup?} inContextSignup
     * @returns {boolean}
     */
    canAutofillType({ mainType, subtype, variant }, inContextSignup) {
      if (this.isTypeUnavailable({ mainType, subtype, variant })) return false;
      const isEmailProtectionEnabled = this.featureToggles.emailProtection && this.availableInputTypes.email;
      if (subtype === "emailAddress" && isEmailProtectionEnabled) {
        return true;
      }
      if (inContextSignup?.isAvailable(subtype)) {
        return true;
      }
      if (subtype === "password" && variant === "new" && this.featureToggles.password_generation) {
        return true;
      }
      if (subtype === "fullName") {
        return Boolean(this.availableInputTypes.identities?.firstName || this.availableInputTypes.identities?.lastName);
      }
      if (subtype === "expiration") {
        return Boolean(this.availableInputTypes.creditCards?.expirationMonth || this.availableInputTypes.creditCards?.expirationYear);
      }
      return Boolean(this.availableInputTypes[mainType]?.[subtype]);
    }
    /** @returns {AutofillFeatureToggles} */
    get featureToggles() {
      if (this._featureToggles === null) throw new Error("feature toggles accessed before being set");
      return this._featureToggles;
    }
    /** @param {AutofillFeatureToggles} input */
    setFeatureToggles(input) {
      this._featureToggles = input;
    }
    /** @returns {AvailableInputTypes} */
    get availableInputTypes() {
      if (this._availableInputTypes === null) throw new Error("available input types accessed before being set");
      return this._availableInputTypes;
    }
    /** @param {AvailableInputTypes} value */
    setAvailableInputTypes(value) {
      this._availableInputTypes = { ...this._availableInputTypes, ...value };
    }
    /** @returns {string} the user's current two-character language code, as provided by the platform */
    get language() {
      return this._language;
    }
    /**
     * Sets the current two-character language code.
     * @param {string} language - the language
     */
    setLanguage(language) {
      this._language = language;
    }
    static default(globalConfig, deviceApi) {
      const settings = new _Settings(globalConfig, deviceApi);
      settings.setFeatureToggles(_Settings.defaults.featureToggles);
      settings.setAvailableInputTypes(_Settings.defaults.availableInputTypes);
      return settings;
    }
    /** @returns {boolean|null} */
    get enabled() {
      return this._enabled;
    }
    /**
     * @param {boolean|null} enabled
     */
    setEnabled(enabled) {
      this._enabled = enabled;
    }
  };
  __publicField(_Settings, "defaults", {
    /** @type {SiteSpecificFeature | null} */
    siteSpecificFeature: null,
    /** @type {AutofillFeatureToggles} */
    featureToggles: {
      autocomplete_attribute_support: false,
      credentials_saving: false,
      password_generation: false,
      emailProtection: false,
      emailProtection_incontext_signup: false,
      inputType_identities: false,
      inputType_credentials: false,
      inputType_creditCards: false,
      input_focus_api: false,
      inlineIcon_credentials: false,
      unknown_username_categorization: false,
      password_variant_categorization: false,
      partial_form_saves: false
    },
    /** @type {AvailableInputTypes} */
    availableInputTypes: {
      credentials: {
        username: false,
        password: false
      },
      identities: {
        firstName: false,
        middleName: false,
        lastName: false,
        birthdayDay: false,
        birthdayMonth: false,
        birthdayYear: false,
        addressStreet: false,
        addressStreet2: false,
        addressCity: false,
        addressProvince: false,
        addressPostalCode: false,
        addressCountryCode: false,
        phone: false,
        emailAddress: false
      },
      creditCards: {
        cardName: false,
        cardSecurityCode: false,
        expirationMonth: false,
        expirationYear: false,
        cardNumber: false
      },
      email: false
    },
    /** @type {boolean | null} */
    enabled: null
  });
  var Settings = _Settings;

  // src/deviceApiCalls/transports/extension.transport.js
  var ExtensionTransport = class extends DeviceApiTransport {
    /** @param {GlobalConfig} globalConfig */
    constructor(globalConfig) {
      super();
      this.config = globalConfig;
    }
    async send(deviceApiCall) {
      if (deviceApiCall instanceof GetRuntimeConfigurationCall) {
        return deviceApiCall.result(await extensionSpecificRuntimeConfiguration(this));
      }
      if (deviceApiCall instanceof GetAvailableInputTypesCall) {
        return deviceApiCall.result(await extensionSpecificGetAvailableInputTypes());
      }
      if (deviceApiCall instanceof SetIncontextSignupPermanentlyDismissedAtCall) {
        return deviceApiCall.result(await extensionSpecificSetIncontextSignupPermanentlyDismissedAtCall(deviceApiCall.params));
      }
      if (deviceApiCall instanceof GetIncontextSignupDismissedAtCall) {
        return deviceApiCall.result(await extensionSpecificGetIncontextSignupDismissedAt());
      }
      if (deviceApiCall instanceof SendJSPixelCall) {
        return deviceApiCall.result(await extensionSpecificSendPixel(deviceApiCall.params));
      }
      if (deviceApiCall instanceof AddDebugFlagCall) {
        return deviceApiCall.result(await extensionSpecificAddDebugFlag(deviceApiCall.params));
      }
      if (deviceApiCall instanceof CloseAutofillParentCall || deviceApiCall instanceof StartEmailProtectionSignupCall) {
        return;
      }
      console.error("Send not implemented for " + deviceApiCall.method);
    }
  };
  async function extensionSpecificRuntimeConfiguration(deviceApi) {
    const contentScope = await getContentScopeConfig();
    const emailProtectionEnabled = isAutofillEnabledFromProcessedConfig(contentScope);
    const incontextSignupEnabled = isIncontextSignupEnabledFromProcessedConfig(contentScope);
    return {
      success: {
        // @ts-ignore
        contentScope,
        // @ts-ignore
        userPreferences: {
          // Copy locale to user preferences as 'language' to match expected payload
          language: contentScope.locale,
          features: {
            autofill: {
              settings: {
                featureToggles: {
                  ...Settings.defaults.featureToggles,
                  emailProtection: emailProtectionEnabled,
                  emailProtection_incontext_signup: incontextSignupEnabled
                }
              }
            }
          }
        },
        // @ts-ignore
        userUnprotectedDomains: deviceApi.config?.userUnprotectedDomains || []
      }
    };
  }
  async function extensionSpecificGetAvailableInputTypes() {
    const contentScope = await getContentScopeConfig();
    const emailProtectionEnabled = isAutofillEnabledFromProcessedConfig(contentScope);
    return {
      success: {
        ...Settings.defaults.availableInputTypes,
        email: emailProtectionEnabled
      }
    };
  }
  async function getContentScopeConfig() {
    return new Promise((resolve) => {
      chrome.runtime.sendMessage(
        {
          registeredTempAutofillContentScript: true,
          documentUrl: window.location.href
        },
        (response) => {
          if (response && "site" in response) {
            resolve(response);
          }
        }
      );
    });
  }
  async function extensionSpecificSendPixel(params) {
    return new Promise((resolve) => {
      chrome.runtime.sendMessage(
        {
          messageType: "sendJSPixel",
          options: params
        },
        () => {
          resolve(true);
        }
      );
    });
  }
  async function extensionSpecificAddDebugFlag(params) {
    return new Promise((resolve) => {
      chrome.runtime.sendMessage(
        {
          messageType: "addDebugFlag",
          options: params
        },
        () => {
          resolve(true);
        }
      );
    });
  }
  async function extensionSpecificGetIncontextSignupDismissedAt() {
    return new Promise((resolve) => {
      chrome.runtime.sendMessage(
        {
          messageType: "getIncontextSignupDismissedAt"
        },
        (response) => {
          resolve(response);
        }
      );
    });
  }
  async function extensionSpecificSetIncontextSignupPermanentlyDismissedAtCall(params) {
    return new Promise((resolve) => {
      chrome.runtime.sendMessage(
        {
          messageType: "setIncontextSignupPermanentlyDismissedAt",
          options: params
        },
        () => {
          resolve(true);
        }
      );
    });
  }

  // src/deviceApiCalls/transports/windows.transport.js
  var WindowsTransport = class extends DeviceApiTransport {
    async send(deviceApiCall, options) {
      if (deviceApiCall.id) {
        return windowsTransport(deviceApiCall, options).withResponse(deviceApiCall.id);
      }
      return windowsTransport(deviceApiCall, options);
    }
  };
  function windowsTransport(deviceApiCall, options) {
    windowsInteropPostMessage({
      Feature: "Autofill",
      Name: deviceApiCall.method,
      Data: deviceApiCall.params
    });
    return {
      /**
       * Sends a message and returns a Promise that resolves with the response
       * @param responseId
       * @returns {Promise<*>}
       */
      withResponse(responseId) {
        return waitForWindowsResponse(responseId, options);
      }
    };
  }
  function waitForWindowsResponse(responseId, options) {
    return new Promise((resolve, reject) => {
      if (options?.signal?.aborted) {
        return reject(new DOMException("Aborted", "AbortError"));
      }
      let teardown;
      const handler = (event) => {
        if (!event.data) {
          console.warn("data absent from message");
          return;
        }
        if (event.data.type === responseId) {
          teardown();
          resolve(event.data);
        }
      };
      const abortHandler = () => {
        teardown();
        reject(new DOMException("Aborted", "AbortError"));
      };
      windowsInteropAddEventListener("message", handler);
      options?.signal?.addEventListener("abort", abortHandler);
      teardown = () => {
        windowsInteropRemoveEventListener("message", handler);
        options?.signal?.removeEventListener("abort", abortHandler);
      };
    });
  }

  // src/deviceApiCalls/transports/transports.js
  function createTransport(globalConfig) {
    if (typeof globalConfig.userPreferences?.platform?.name === "string") {
      switch (globalConfig.userPreferences?.platform?.name) {
        case "ios":
        case "macos":
          return new AppleTransport(globalConfig);
        case "android":
          return new AndroidTransport(globalConfig);
        default:
          throw new Error("selectSender unimplemented!");
      }
    }
    if (globalConfig.isWindows) {
      return new WindowsTransport();
    }
    if (globalConfig.isDDGApp) {
      if (globalConfig.isAndroid) {
        return new AndroidTransport(globalConfig);
      }
      throw new Error("unreachable, createTransport");
    }
    return new ExtensionTransport(globalConfig);
  }

  // src/DeviceInterface/initFormSubmissionsApi.js
  function initFormSubmissionsApi(forms, matching) {
    window.addEventListener(
      "submit",
      (e) => {
        return forms.get(e.target)?.submitHandler("global submit event");
      },
      true
    );
    window.addEventListener(
      "keydown",
      (e) => {
        if (e.key === "Enter") {
          const focusedForm = [...forms.values()].find((form) => form.hasFocus(e));
          focusedForm?.submitHandler("global keydown + Enter");
        }
      },
      true
    );
    window.addEventListener(
      "pointerdown",
      (event) => {
        const realTarget = pierceShadowTree(event);
        const formsArray = [...forms.values()];
        const matchingForm = formsArray.find((form) => {
          const btns = [...form.submitButtons];
          if (btns.includes(realTarget)) return true;
          if (btns.find((btn) => btn.contains(realTarget))) return true;
          return false;
        });
        matchingForm?.submitHandler("global pointerdown event + matching form");
        if (!matchingForm) {
          const selector = matching.cssSelector("submitButtonSelector") + ', a[href="#"], a[href^=javascript], *[onclick], [class*=button i]';
          const button = (
            /** @type HTMLElement */
            realTarget?.closest(selector)
          );
          if (!button) return;
          const buttonIsAFalsePositive = formsArray.some((form) => button?.contains(form.form));
          if (buttonIsAFalsePositive) return;
          const text = getTextShallow(button) || extractElementStrings(button).join(" ");
          const hasRelevantText = safeRegexTest(matching.getDDGMatcherRegex("submitButtonRegex"), text);
          if (hasRelevantText && text.length < 25) {
            const filledForm = formsArray.find((form) => form.hasValues());
            if (filledForm && buttonMatchesFormType(
              /** @type HTMLElement */
              button,
              filledForm
            )) {
              filledForm?.submitHandler("global pointerdown event + filled form");
            }
          }
          if (
            /** @type HTMLElement */
            realTarget?.closest("#passwordNext button, #identifierNext button")
          ) {
            const filledForm = formsArray.find((form) => form.hasValues());
            filledForm?.submitHandler("global pointerdown event + google escape hatch");
          }
        }
      },
      true
    );
    const observer = new PerformanceObserver((list) => {
      const formsArray = [...forms.values()];
      const entries = list.getEntries().filter(
        (entry) => (
          // @ts-ignore why does TS not know about `entry.initiatorType`?
          ["fetch", "xmlhttprequest"].includes(entry.initiatorType) && safeRegexTest(/login|sign-in|signin/, entry.name)
        )
      );
      if (!entries.length) return;
      const filledForm = formsArray.find((form) => form.hasValues());
      const focusedForm = formsArray.find((form) => form.hasFocus());
      if (focusedForm) return;
      filledForm?.submitHandler("performance observer");
    });
    observer.observe({ entryTypes: ["resource"] });
  }

  // src/DeviceInterface/initFocusApi.js
  function getAutocompleteValueFromInputType(inputType) {
    const subtype = getSubtypeFromType(inputType);
    const autocompleteMap = {
      // Identities
      emailAddress: "email",
      fullName: "name",
      firstName: "given-name",
      middleName: "additional-name",
      lastName: "family-name",
      phone: "tel",
      addressStreet: "street-address",
      addressStreet2: "address-line2",
      addressCity: "address-level2",
      addressProvince: "address-level1",
      addressPostalCode: "postal-code",
      addressCountryCode: "country"
    };
    return autocompleteMap[subtype];
  }
  function setAutocompleteOnIdentityField(element) {
    if (!(element instanceof HTMLInputElement) || element.hasAttribute("autocomplete")) {
      return;
    }
    const inputType = getInputType(element);
    const mainType = getMainTypeFromType(inputType);
    if (mainType !== "identities") {
      return;
    }
    const autocompleteValue = getAutocompleteValueFromInputType(inputType);
    if (autocompleteValue) {
      element.setAttribute("autocomplete", autocompleteValue);
      element.addEventListener(
        "blur",
        () => {
          element.removeAttribute("autocomplete");
        },
        { once: true }
      );
    }
  }
  function handleFocusEvent(forms, settings, attachKeyboardCallback, e) {
    const isAnyFormAutofilling = [...forms.values()].some((form2) => form2.isAutofilling);
    if (isAnyFormAutofilling) return;
    const targetElement = pierceShadowTree(e);
    if (!targetElement || targetElement instanceof Window) return;
    const form = [...forms.values()].find((form2) => form2.hasFocus());
    if (settings.featureToggles.input_focus_api) {
      attachKeyboardCallback({ form, element: targetElement });
    }
    if (settings.featureToggles.autocomplete_attribute_support) {
      setAutocompleteOnIdentityField(targetElement);
    }
  }
  function initFocusApi(forms, settings, attachKeyboardCallback) {
    const boundHandleFocusEvent = handleFocusEvent.bind(null, forms, settings, attachKeyboardCallback);
    window.addEventListener("focus", boundHandleFocusEvent, true);
    return {
      setAutocompleteOnIdentityField,
      handleFocusEvent: boundHandleFocusEvent,
      cleanup: () => {
        window.removeEventListener("focus", boundHandleFocusEvent, true);
      }
    };
  }

  // src/EmailProtection.js
  var _previous2;
  var EmailProtection = class {
    /** @param {import("./DeviceInterface/InterfacePrototype").default} device */
    constructor(device) {
      /** @type {string|null} */
      __privateAdd(this, _previous2, null);
      this.device = device;
    }
    /** @returns {string|null} */
    get lastGenerated() {
      return __privateGet(this, _previous2);
    }
    /**
     * Store the last received email address
     * @param {string} emailAddress
     */
    storeReceived(emailAddress) {
      __privateSet(this, _previous2, emailAddress);
      return emailAddress;
    }
  };
  _previous2 = new WeakMap();

  // src/locales/bg/autofill.json
  var autofill_default = {
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
    hello: {
      title: "\u0417\u0434\u0440\u0430\u0432\u0435\u0439, \u0441\u0432\u044F\u0442",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "\u0418\u0437\u043F\u043E\u043B\u0437\u0432\u0430\u043D\u0435 \u043D\u0430 {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "\u0411\u043B\u043E\u043A\u0438\u0440\u0430\u043D\u0435 \u043D\u0430 \u0438\u043C\u0435\u0439\u043B \u0442\u0440\u0430\u043A\u0435\u0440\u0438",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "\u041F\u0430\u0440\u043E\u043B\u0430 \u0437\u0430 {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "\u0413\u0435\u043D\u0435\u0440\u0438\u0440\u0430\u043D\u0430 \u043F\u0430\u0440\u043E\u043B\u0430",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "\u041F\u0430\u0440\u043E\u043B\u0430\u0442\u0430 \u0437\u0430 \u0442\u043E\u0437\u0438 \u0443\u0435\u0431\u0441\u0430\u0439\u0442 \u0449\u0435 \u0431\u044A\u0434\u0435 \u0437\u0430\u043F\u0430\u0437\u0435\u043D\u0430",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "\u041F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0435\u0442\u043E Bitwarden \u0435 \u0437\u0430\u043A\u043B\u044E\u0447\u0435\u043D\u043E",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "\u041E\u0442\u043A\u043B\u044E\u0447\u0435\u0442\u0435 \u0441\u0432\u043E\u044F \u0442\u0440\u0435\u0437\u043E\u0440, \u0437\u0430 \u0434\u0430 \u043F\u043E\u043B\u0443\u0447\u0438\u0442\u0435 \u0434\u043E\u0441\u0442\u044A\u043F \u0434\u043E \u0438\u0434\u0435\u043D\u0442\u0438\u0444\u0438\u043A\u0430\u0446\u0438\u043E\u043D\u043D\u0438 \u0434\u0430\u043D\u043D\u0438 \u0438\u043B\u0438 \u0434\u0430 \u0433\u0435\u043D\u0435\u0440\u0438\u0440\u0430\u0442\u0435 \u043F\u0430\u0440\u043E\u043B\u0438",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "\u0413\u0435\u043D\u0435\u0440\u0438\u0440\u0430\u043D\u0435 \u043D\u0430 \u043B\u0438\u0447\u0435\u043D Duck Address",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "\u0421\u043A\u0440\u0438\u0439\u0442\u0435 \u0438\u043C\u0435\u0439\u043B \u0430\u0434\u0440\u0435\u0441\u0430 \u0441\u0438 \u0438 \u0431\u043B\u043E\u043A\u0438\u0440\u0430\u0439\u0442\u0435 \u0442\u0440\u0430\u043A\u0435\u0440\u0438\u0442\u0435",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "\u0421\u044A\u0437\u0434\u0430\u0439\u0442\u0435 \u0443\u043D\u0438\u043A\u0430\u043B\u0435\u043D, \u043F\u0440\u043E\u0438\u0437\u0432\u043E\u043B\u0435\u043D \u0430\u0434\u0440\u0435\u0441, \u043A\u043E\u0439\u0442\u043E \u043F\u0440\u0435\u043C\u0430\u0445\u0432\u0430 \u0441\u043A\u0440\u0438\u0442\u0438\u0442\u0435 \u0442\u0440\u0430\u043A\u0435\u0440\u0438 \u0438 \u043F\u0440\u0435\u043F\u0440\u0430\u0449\u0430 \u0438\u043C\u0435\u0439\u043B\u0438\u0442\u0435 \u043A\u044A\u043C \u043F\u043E\u0449\u0435\u043D\u0441\u043A\u0430\u0442\u0430 \u0412\u0438 \u043A\u0443\u0442\u0438\u044F.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "\u0423\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u043D\u0430 \u0437\u0430\u043F\u0430\u0437\u0435\u043D\u0438\u0442\u0435 \u0435\u043B\u0435\u043C\u0435\u043D\u0442\u0438\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "\u0423\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u043D\u0430 \u043A\u0440\u0435\u0434\u0438\u0442\u043D\u0438\u0442\u0435 \u043A\u0430\u0440\u0442\u0438\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "\u0423\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u043D\u0430 \u0441\u0430\u043C\u043E\u043B\u0438\u0447\u043D\u043E\u0441\u0442\u0438\u0442\u0435\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "\u0423\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u043D\u0430 \u043F\u0430\u0440\u043E\u043B\u0438\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "\u0413\u0435\u043D\u0435\u0440\u0438\u0440\u0430\u043D\u0435 \u043D\u0430 \u043F\u043E\u0432\u0435\u0440\u0438\u0442\u0435\u043B\u0435\u043D Duck Address",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "\u0411\u043B\u043E\u043A\u0438\u0440\u0430\u043D\u0435 \u043D\u0430 \u0438\u043C\u0435\u0439\u043B \u0442\u0440\u0430\u043A\u0435\u0440\u0438\u0442\u0435 \u0438 \u0441\u043A\u0440\u0438\u0432\u0430\u043D\u0435 \u043D\u0430 \u0430\u0434\u0440\u0435\u0441\u0430",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "\u0417\u0430\u0449\u0438\u0442\u0430 \u043D\u0430 \u043C\u043E\u044F \u0438\u043C\u0435\u0439\u043B",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "\u041D\u0435 \u043F\u043E\u043A\u0430\u0437\u0432\u0430\u0439 \u043E\u0442\u043D\u043E\u0432\u043E",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "\u0418\u043C\u043F\u043E\u0440\u0442\u0438\u0440\u0430\u043D\u0435 \u043D\u0430 \u043F\u0430\u0440\u043E\u043B\u0430 \u0432 DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "\u041F\u0440\u0435\u0445\u0432\u044A\u0440\u043B\u044F\u0439\u0442\u0435 \u043F\u0430\u0440\u043E\u043B\u0438\u0442\u0435 \u0441\u0438 \u043E\u0442 \u0434\u0440\u0443\u0433 \u0431\u0440\u0430\u0443\u0437\u044A\u0440 \u0438\u043B\u0438 \u043C\u0435\u043D\u0438\u0434\u0436\u044A\u0440 \u043D\u0430 \u043F\u0430\u0440\u043E\u043B\u0438 \u0431\u044A\u0440\u0437\u043E \u0438 \u0441\u0438\u0433\u0443\u0440\u043D\u043E.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "\u0421\u0440\u043E\u043A \u043D\u0430 \u0432\u0430\u043B\u0438\u0434\u043D\u043E\u0441\u0442",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/cs/autofill.json
  var autofill_default2 = {
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
    hello: {
      title: "Ahoj, sv\u011Bte",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Pou\u017E\xEDt {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Blokuj e-mailov\xE9 trackery",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Heslo pro {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Vygenerovan\xE9 heslo",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Heslo pro tenhle web se ulo\u017E\xED",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Aplikace Bitwarden je zam\u010Den\xE1",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Pro p\u0159\xEDstup k p\u0159ihla\u0161ovac\xEDm \xFAdaj\u016Fm a generov\xE1n\xED hesel je pot\u0159eba odemknout aplikaci Bitwarden",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Vygenerovat soukromou Duck Address",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Skryj sv\u016Fj e-mail a blokuj trackery",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Vytvo\u0159 si jedine\u010Dnou, n\xE1hodnou adresu, kter\xE1 bude odstra\u0148ovat skryt\xE9 trackery a p\u0159epos\xEDlat e-maily do tv\xE9 schr\xE1nky.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Spravovat ulo\u017Een\xE9 polo\u017Eky\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Spravovat platebn\xED karty\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Spravovat identity\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Spravovat hesla\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Vygenerovat soukromou Duck Address",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Blokuj e-mailov\xE9 trackery a skryj svou adresu",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Chr\xE1nit m\u016Fj e-mail",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "U\u017E neukazovat",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Import hesla do DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Rychle a\xA0bezpe\u010Dn\u011B p\u0159enes svoje hesla z\xA0jin\xE9ho prohl\xED\u017Ee\u010De nebo spr\xE1vce hesel.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Datum konce platnosti",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/da/autofill.json
  var autofill_default3 = {
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
    hello: {
      title: "Hej verden",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Brug {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Blokerer e-mailtrackere",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Adgangskode til {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Genereret adgangskode",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Adgangskoden bliver gemt for dette websted",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden er l\xE5st",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "L\xE5s din boks op for at f\xE5 adgang til legitimationsoplysninger eller generere adgangskoder",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Opret privat Duck-adresse",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Skjul din e-mail og bloker trackere",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Opret en unik, tilf\xE6ldig adresse, der ogs\xE5 fjerner skjulte trackere og videresender e-mails til din indbakke.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Administrer gemte elementer\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Administrer kreditkort\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Administrer identiteter\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Administrer adgangskoder\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Opret en privat Duck-adresse",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Bloker e-mailtrackere og skjul adresse",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Beskyt min e-mail",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Vis ikke igen",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Import\xE9r adgangskode til DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Overf\xF8r hurtigt og sikkert dine adgangskoder fra en anden browser eller adgangskodeadministrator.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Udl\xF8bsdato",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/de/autofill.json
  var autofill_default4 = {
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
    hello: {
      title: "Hallo Welt",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "{email} verwenden",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "E-Mail-Tracker blockieren",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Passwort f\xFCr {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Generiertes Passwort",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Passwort wird f\xFCr diese Website gespeichert",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden ist verschlossen",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Entsperre deinen Bitwarden-Datentresor, um auf deine Zugangsdaten zuzugreifen oder Passw\xF6rter zu generieren",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Private Duck-Adresse generieren",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "E-Mail-Adresse verbergen und Tracker blockieren",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Einmalige, zuf\xE4llige Adresse erstellen, die versteckte Tracker entfernt und E-Mails an deinen Posteingang weiterleitet.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Gespeicherte Elemente verwalten\xA0\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Kreditkarten verwalten\xA0\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Identit\xE4ten verwalten\xA0\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Passw\xF6rter verwalten\xA0\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Private Duck Address generieren",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "E-Mail-Tracker blockieren & Adresse verbergen",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Meine E-Mail-Adresse sch\xFCtzen",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Nicht erneut anzeigen",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Passwort in DuckDuckGo importieren",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "\xDCbertrage deine Passw\xF6rter schnell und sicher von einem anderen Browser oder Passwort-Manager.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "G\xFCltigkeit",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/el/autofill.json
  var autofill_default5 = {
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
    hello: {
      title: "\u0393\u03B5\u03B9\u03B1 \u03C3\u03BF\u03C5 \u03BA\u03CC\u03C3\u03BC\u03B5",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "\u03A7\u03C1\u03B7\u03C3\u03B9\u03BC\u03BF\u03C0\u03BF\u03B9\u03AE\u03C3\u03C4\u03B5 \u03C4\u03B7 \u03B4\u03B9\u03B5\u03CD\u03B8\u03C5\u03BD\u03C3\u03B7 {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "\u0391\u03C0\u03BF\u03BA\u03BB\u03B5\u03B9\u03C3\u03BC\u03CC\u03C2 \u03B5\u03C6\u03B1\u03C1\u03BC\u03BF\u03B3\u03CE\u03BD \u03C0\u03B1\u03C1\u03B1\u03BA\u03BF\u03BB\u03BF\u03CD\u03B8\u03B7\u03C3\u03B7\u03C2 email",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "\u039A\u03C9\u03B4\u03B9\u03BA\u03CC\u03C2 \u03C0\u03C1\u03CC\u03C3\u03B2\u03B1\u03C3\u03B7\u03C2 \u03B3\u03B9\u03B1 {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "\u0394\u03B7\u03BC\u03B9\u03BF\u03C5\u03C1\u03B3\u03AE\u03B8\u03B7\u03BA\u03B5 \u03BA\u03C9\u03B4\u03B9\u03BA\u03CC\u03C2 \u03C0\u03C1\u03CC\u03C3\u03B2\u03B1\u03C3\u03B7\u03C2",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "\u039F \u03BA\u03C9\u03B4\u03B9\u03BA\u03CC\u03C2 \u03C0\u03C1\u03CC\u03C3\u03B2\u03B1\u03C3\u03B7\u03C2 \u03B8\u03B1 \u03B1\u03C0\u03BF\u03B8\u03B7\u03BA\u03B5\u03C5\u03C4\u03B5\u03AF \u03B3\u03B9\u03B1 \u03C4\u03BF\u03BD \u03B9\u03C3\u03C4\u03CC\u03C4\u03BF\u03C0\u03BF \u03B1\u03C5\u03C4\u03CC",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "\u03A4\u03BF Bitwarden \u03B5\u03AF\u03BD\u03B1\u03B9 \u03BA\u03BB\u03B5\u03B9\u03B4\u03C9\u03BC\u03AD\u03BD\u03BF",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "\u039E\u03B5\u03BA\u03BB\u03B5\u03B9\u03B4\u03CE\u03C3\u03C4\u03B5 \u03C4\u03BF \u03B8\u03B7\u03C3\u03B1\u03C5\u03C1\u03BF\u03C6\u03C5\u03BB\u03AC\u03BA\u03B9\u03CC \u03C3\u03B1\u03C2 \u03B3\u03B9\u03B1 \u03C0\u03C1\u03CC\u03C3\u03B2\u03B1\u03C3\u03B7 \u03C3\u03B5 \u03B4\u03B9\u03B1\u03C0\u03B9\u03C3\u03C4\u03B5\u03C5\u03C4\u03AE\u03C1\u03B9\u03B1 \u03AE \u03B4\u03B7\u03BC\u03B9\u03BF\u03C5\u03C1\u03B3\u03AF\u03B1 \u03BA\u03C9\u03B4\u03B9\u03BA\u03CE\u03BD \u03C0\u03C1\u03CC\u03C3\u03B2\u03B1\u03C3\u03B7\u03C2",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "\u0394\u03B7\u03BC\u03B9\u03BF\u03C5\u03C1\u03B3\u03AE\u03C3\u03C4\u03B5 \u03B9\u03B4\u03B9\u03C9\u03C4\u03B9\u03BA\u03AE Duck Address",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "\u0391\u03C0\u03CC\u03BA\u03C1\u03C5\u03C8\u03B7 \u03C4\u03BF\u03C5 email \u03C3\u03B1\u03C2 \u03BA\u03B1\u03B9 \u03B1\u03C0\u03BF\u03BA\u03BB\u03B5\u03B9\u03C3\u03BC\u03CC\u03C2 \u03B5\u03C6\u03B1\u03C1\u03BC\u03BF\u03B3\u03CE\u03BD \u03C0\u03B1\u03C1\u03B1\u03BA\u03BF\u03BB\u03BF\u03CD\u03B8\u03B7\u03C3\u03B7\u03C2",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "\u0394\u03B7\u03BC\u03B9\u03BF\u03C5\u03C1\u03B3\u03AE\u03C3\u03C4\u03B5 \u03BC\u03B9\u03B1 \u03BC\u03BF\u03BD\u03B1\u03B4\u03B9\u03BA\u03AE, \u03C4\u03C5\u03C7\u03B1\u03AF\u03B1 \u03B4\u03B9\u03B5\u03CD\u03B8\u03C5\u03BD\u03C3\u03B7 \u03B7 \u03BF\u03C0\u03BF\u03AF\u03B1 \u03B1\u03C6\u03B1\u03B9\u03C1\u03B5\u03AF \u03B5\u03C0\u03AF\u03C3\u03B7\u03C2 \u03BA\u03C1\u03C5\u03C6\u03AD\u03C2 \u03B5\u03C6\u03B1\u03C1\u03BC\u03BF\u03B3\u03AD\u03C2 \u03C0\u03B1\u03C1\u03B1\u03BA\u03BF\u03BB\u03BF\u03CD\u03B8\u03B7\u03C3\u03B7\u03C2 \u03BA\u03B1\u03B9 \u03C0\u03C1\u03BF\u03C9\u03B8\u03B5\u03AF email \u03C3\u03C4\u03B1 \u03B5\u03B9\u03C3\u03B5\u03C1\u03C7\u03CC\u03BC\u03B5\u03BD\u03AC \u03C3\u03B1\u03C2.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "\u0394\u03B9\u03B1\u03C7\u03B5\u03AF\u03C1\u03B9\u03C3\u03B7 \u03B1\u03C0\u03BF\u03B8\u03B7\u03BA\u03B5\u03C5\u03BC\u03AD\u03BD\u03C9\u03BD \u03C3\u03C4\u03BF\u03B9\u03C7\u03B5\u03AF\u03C9\u03BD\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "\u0394\u03B9\u03B1\u03C7\u03B5\u03AF\u03C1\u03B9\u03C3\u03B7 \u03C0\u03B9\u03C3\u03C4\u03C9\u03C4\u03B9\u03BA\u03CE\u03BD \u03BA\u03B1\u03C1\u03C4\u03CE\u03BD\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "\u0394\u03B9\u03B1\u03C7\u03B5\u03AF\u03C1\u03B9\u03C3\u03B7 \u03C4\u03B1\u03C5\u03C4\u03BF\u03C4\u03AE\u03C4\u03C9\u03BD\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "\u0394\u03B9\u03B1\u03C7\u03B5\u03AF\u03C1\u03B9\u03C3\u03B7 \u03BA\u03C9\u03B4\u03B9\u03BA\u03CE\u03BD \u03C0\u03C1\u03CC\u03C3\u03B2\u03B1\u03C3\u03B7\u03C2\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "\u0394\u03B7\u03BC\u03B9\u03BF\u03C5\u03C1\u03B3\u03AE\u03C3\u03C4\u03B5 \u03BC\u03B9\u03B1 \u03B9\u03B4\u03B9\u03C9\u03C4\u03B9\u03BA\u03AE Duck Address",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "\u0391\u03C0\u03BF\u03BA\u03BB\u03B5\u03B9\u03C3\u03BC\u03CC\u03C2 \u03B5\u03C6\u03B1\u03C1\u03BC\u03BF\u03B3\u03CE\u03BD \u03C0\u03B1\u03C1\u03B1\u03BA\u03BF\u03BB\u03BF\u03CD\u03B8\u03B7\u03C3\u03B7\u03C2 email \u03BA\u03B1\u03B9 \u03B1\u03C0\u03CC\u03BA\u03C1\u03C5\u03C8\u03B7 \u03B4\u03B9\u03B5\u03CD\u03B8\u03C5\u03BD\u03C3\u03B7\u03C2",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "\u03A0\u03C1\u03BF\u03C3\u03C4\u03B1\u03C3\u03AF\u03B1 \u03C4\u03BF\u03C5 email \u03BC\u03BF\u03C5",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "\u039D\u03B1 \u03BC\u03B7\u03BD \u03B5\u03BC\u03C6\u03B1\u03BD\u03B9\u03C3\u03C4\u03B5\u03AF \u03BE\u03B1\u03BD\u03AC",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "\u0395\u03B9\u03C3\u03B1\u03B3\u03C9\u03B3\u03AE \u03BA\u03C9\u03B4\u03B9\u03BA\u03BF\u03CD \u03C0\u03C1\u03CC\u03C3\u03B2\u03B1\u03C3\u03B7\u03C2 \u03C3\u03C4\u03BF DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "\u039C\u03B5\u03C4\u03B1\u03C6\u03AD\u03C1\u03B5\u03C4\u03B5 \u03B3\u03C1\u03AE\u03B3\u03BF\u03C1\u03B1 \u03BA\u03B1\u03B9 \u03BC\u03B5 \u03B1\u03C3\u03C6\u03AC\u03BB\u03B5\u03B9\u03B1 \u03C4\u03BF\u03C5\u03C2 \u03BA\u03C9\u03B4\u03B9\u03BA\u03BF\u03CD\u03C2 \u03C0\u03C1\u03CC\u03C3\u03B2\u03B1\u03C3\u03AE\u03C2 \u03C3\u03B1\u03C2 \u03B1\u03C0\u03CC \u03AC\u03BB\u03BB\u03BF \u03C0\u03C1\u03CC\u03B3\u03C1\u03B1\u03BC\u03BC\u03B1 \u03C0\u03B5\u03C1\u03B9\u03AE\u03B3\u03B7\u03C3\u03B7\u03C2 \u03AE \u03C0\u03C1\u03CC\u03B3\u03C1\u03B1\u03BC\u03BC\u03B1 \u03B4\u03B9\u03B1\u03C7\u03B5\u03AF\u03C1\u03B9\u03C3\u03B7\u03C2 \u03BA\u03C9\u03B4\u03B9\u03BA\u03CE\u03BD \u03C0\u03C1\u03CC\u03C3\u03B2\u03B1\u03C3\u03B7\u03C2.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "\u039B\u03AE\u03BE\u03B7",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/en/autofill.json
  var autofill_default6 = {
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
    hello: {
      title: "Hello world",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Use {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Block email trackers",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Password for {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Generated password",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Password will be saved for this website",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden is locked",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Unlock your vault to access credentials or generate passwords",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Generate Private Duck Address",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Hide your email and block trackers",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Create a unique, random address that also removes hidden trackers and forwards email to your inbox.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Manage Saved Items\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePasswords". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Manage Credit Cards\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Manage Identities\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combination of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Manage Passwords\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Generate a Private Duck Address",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Block email trackers & hide address",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Protect My Email",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Don't Show Again",
      note: "Button that prevents the DuckDuckGo email protection signup prompt and credentials import prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Import passwords to DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Quickly and securely transfer your passwords from another browser or password manager.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Expiry",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/es/autofill.json
  var autofill_default7 = {
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
    hello: {
      title: "Hola mundo",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Usar {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Bloquea de rastreadores de correo electr\xF3nico",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Contrase\xF1a para {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Contrase\xF1a generada",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Se guardar\xE1 la contrase\xF1a de este sitio web",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden est\xE1 bloqueado",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Desbloquea tu caja fuerte para acceder a las credenciales o generar contrase\xF1as",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Generar Duck Address privada",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Ocultar tu correo electr\xF3nico y bloquear rastreadores",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Crea una direcci\xF3n aleatoria \xFAnica que tambi\xE9n elimine los rastreadores ocultos y reenv\xEDe el correo electr\xF3nico a tu bandeja de entrada.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Gestionar elementos guardados\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Administrar tarjetas de cr\xE9dito\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Administrar identidades\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Administrar contrase\xF1as\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Generar Duck Address privada",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Bloquea rastreadores de correo electr\xF3nico y oculta la direcci\xF3n",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Proteger mi correo electr\xF3nico",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "No volver a mostrar",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Importar contrase\xF1as a DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Transfiere tus contrase\xF1as de forma r\xE1pida y segura desde otro navegador o administrador de contrase\xF1as.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Fecha de caducidad",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/et/autofill.json
  var autofill_default8 = {
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
    hello: {
      title: "Tere maailm",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Kasutage {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Blokeeri e-posti j\xE4lgijad",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Saidi {url} parool",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Loodud parool",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Parool salvestatakse selle veebilehe jaoks",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden on lukustatud",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Ava mandaatidele juurdep\xE4\xE4suks v\xF5i paroolide loomiseks oma varamu",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Loo privaatne Duck Address",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Peida oma e-post ja blokeeri j\xE4lgijad",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Loo unikaalne, juhuslik aadress, mis eemaldab ka varjatud j\xE4glijad ja edastab e-kirjad sinu postkasti.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Halda salvestatud \xFCksuseid\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Halda krediitkaarte\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Halda identiteete\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Halda paroole\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Loo privaatne Duck Address",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Blokeeri e-posti j\xE4lgijad ja peida aadress",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Kaitse minu e-posti aadressi",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "\xC4ra enam n\xE4ita",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Impordi parool DuckDuckGosse",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Vii oma paroolid kiiresti ja turvaliselt \xFCle teisest brauserist v\xF5i paroolihaldurist.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Aegumiskuup\xE4ev",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/fi/autofill.json
  var autofill_default9 = {
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
    hello: {
      title: "Hei, maailma",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "K\xE4yt\xE4 {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Est\xE4 s\xE4hk\xF6postin seurantaohjelmat",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Sivuston {url} salasana",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Luotu salasana",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Salasana tallennetaan t\xE4lle verkkosivustolle",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden on lukittu",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Avaa holvin lukitus p\xE4\xE4st\xE4ksesi k\xE4siksi tunnistetietoihin tai luodaksesi salasanoja",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Luo yksityinen Duck Address",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Piilota s\xE4hk\xF6postisi ja Est\xE4 seurantaohjelmat",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Luo yksil\xF6llinen, satunnainen osoite, joka lis\xE4ksi poistaa piilotetut seurantaohjelmat ja v\xE4litt\xE4\xE4 s\xE4hk\xF6postin omaan postilaatikkoosi.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Hallitse tallennettuja kohteita\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Hallitse luottokortteja\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Hallitse k\xE4ytt\xE4j\xE4tietoja\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Hallitse salasanoja\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Luo yksityinen Duck Address",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Est\xE4 s\xE4hk\xF6postin seurantaohjelmat ja piilota osoite",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Suojaa s\xE4hk\xF6postini",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "\xC4l\xE4 n\xE4yt\xE4 en\xE4\xE4",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Tuo salasana DuckDuckGoon",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Siirr\xE4 salasanasi nopeasti ja turvallisesti toisesta selaimesta tai salasanojen hallintaohjelmasta.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Vanhenemisp\xE4iv\xE4",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/fr/autofill.json
  var autofill_default10 = {
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
    hello: {
      title: "Bonjour \xE0 tous",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Utiliser {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Bloquer les traqueurs d'e-mails",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Mot de passe pour {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Mot de passe g\xE9n\xE9r\xE9",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Le mot de passe sera enregistr\xE9 pour ce site",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden est verrouill\xE9",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "D\xE9verrouillez votre coffre-fort pour acc\xE9der \xE0 vos informations d'identification ou g\xE9n\xE9rer des mots de passe",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "G\xE9n\xE9rer une Duck Address priv\xE9e",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Masquez votre adresse e-mail et bloquez les traqueurs",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Cr\xE9ez une adresse unique et al\xE9atoire qui supprime les traqueurs masqu\xE9s et transf\xE8re les e-mails vers votre bo\xEEte de r\xE9ception.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "G\xE9rez les \xE9l\xE9ments enregistr\xE9s\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "G\xE9rez les cartes bancaires\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "G\xE9rez les identit\xE9s\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "G\xE9rer les mots de passe\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "G\xE9n\xE9rer une Duck Address priv\xE9e",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Bloquer les traqueurs d'e-mails et masquer l'adresse",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Prot\xE9ger mon adresse e-mail",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Ne plus afficher",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Importer des mots de passe sur DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Transf\xE9rez vos mots de passe rapidement et en toute s\xE9curit\xE9 \xE0 partir d\u2019un autre navigateur ou d\u2019un autre gestionnaire de mots de passe.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Expiration",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/hr/autofill.json
  var autofill_default11 = {
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
    hello: {
      title: "Pozdrav svijete",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Upotrijebite {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Blokiranje alata za pra\u0107enje e-po\u0161te",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Lozinka za {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Generirana lozinka",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Lozinka \u0107e biti spremljena za ovo web-mjesto",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden je zaklju\u010Dan",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Otklju\u010Daj svoj trezor za pristup vjerodajnicama ili generiranje lozinki",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Generiraj privatnu adresu Duck Address",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Sakrij svoju e-po\u0161tu i blokiraj traga\u010De",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: 'Izradi jedinstvenu, nasumi\u010Dnu adresu koja tako\u0111er uklanja skrivene alate za pra\u0107enje ("traga\u010De") i proslje\u0111uje e-po\u0161tu u tvoj sandu\u010Di\u0107 za pristiglu po\u0161tu.',
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Upravljanje spremljenim stavkama\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Upravljanje kreditnim karticama\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Upravljanje identitetima\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Upravljanje lozinkama\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Generiraj privatnu adresu Duck Address",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Blokiraj pra\u0107enje e-po\u0161te i sakrij adresu",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Za\u0161titi moju e-po\u0161tu",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Nemoj ponovno prikazivati",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Uvezi lozinku u DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Brzo i sigurno prenesi svoje lozinke iz drugog preglednika ili upravitelja lozinkama.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Istje\u010De",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/hu/autofill.json
  var autofill_default12 = {
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
    hello: {
      title: "Hell\xF3, vil\xE1g!",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "{email} haszn\xE1lata",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "E-mail nyomk\xF6vet\u0151k blokkol\xE1sa",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "{url} jelszava",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Gener\xE1lt jelsz\xF3",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "A webhelyhez tartoz\xF3 jelsz\xF3 mentve lesz",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "A Bitwarden z\xE1rolva van",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "A hiteles\xEDt\u0151 adatok el\xE9r\xE9s\xE9hez vagy a jelszavak gener\xE1l\xE1s\xE1hoz oldd fel a t\xE1rol\xF3t",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Priv\xE1t Duck Address l\xE9trehoz\xE1sa",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "E-mail elrejt\xE9se \xE9s nyomk\xF6vet\u0151k blokkol\xE1sa",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Hozz l\xE9tre egy egyedi, v\xE9letlenszer\u0171 c\xEDmet, amely elt\xE1vol\xEDtja a rejtett nyomk\xF6vet\u0151ket is, \xE9s a postafi\xF3kodba tov\xE1bb\xEDtja az e-maileket.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Mentett elemek kezel\xE9se\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Hitelk\xE1rty\xE1k kezel\xE9se\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Identit\xE1sok kezel\xE9se\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Jelszavak kezel\xE9se\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Priv\xE1t Duck Address l\xE9trehoz\xE1sa",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "E-mail nyomk\xF6vet\u0151k blokkol\xE1sa, \xE9s a c\xEDm elrejt\xE9se",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "E-mail v\xE9delme",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Ne jelenjen meg \xFAjra",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Jelsz\xF3 import\xE1l\xE1sa a DuckDuckG\xF3ba",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Gyorsan \xE9s biztons\xE1gosan \xE1thozhatod a jelszavaid egy m\xE1sik b\xF6ng\xE9sz\u0151b\u0151l vagy jelsz\xF3kezel\u0151b\u0151l.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Lej\xE1rat",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/it/autofill.json
  var autofill_default13 = {
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
    hello: {
      title: "Ciao mondo",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Usa {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Blocca i sistemi di tracciamento e-mail",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Password per {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Password generata",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "La password verr\xE0 salvata per questo sito web",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden \xE8 bloccato",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Sblocca la tua cassaforte per accedere alle credenziali o generare password",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Genera Duck Address privato",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Nascondi il tuo indirizzo e-mail e blocca i sistemi di tracciamento",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Crea un indirizzo univoco e casuale che rimuove anche i sistemi di tracciamento nascosti e inoltra le e-mail alla tua casella di posta.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Gestisci gli elementi salvati\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Gestisci carte di credito\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Gestisci identit\xE0\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Gestisci password\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Genera un Duck Address privato",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Blocca i sistemi di tracciamento e-mail e nascondi il tuo indirizzo",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Proteggi la mia e-mail",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Non mostrare pi\xF9",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Importa le tue password in DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Trasferisci in modo rapido e sicuro le tue password da un altro browser o gestore di password.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Scadenza",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/lt/autofill.json
  var autofill_default14 = {
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
    hello: {
      title: "Labas pasauli",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Naudoti {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Blokuoti el. pa\u0161to sekimo priemones",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "\u201E{url}\u201C slapta\u017Eodis",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Sugeneruotas slapta\u017Eodis",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Slapta\u017Eodis bus i\u0161saugotas \u0161iai svetainei",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "\u201EBitwarden\u201C u\u017Erakinta",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Atrakinkite saugykl\u0105, kad pasiektum\u0117te prisijungimo duomenis arba sugeneruotum\u0117te slapta\u017Eod\u017Eius",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Generuoti privat\u0173 \u201EDuck Address\u201C",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Pasl\u0117pkite savo el. pa\u0161t\u0105 ir blokuokite steb\u0117jimo priemones",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Sukurkite unikal\u0173 atsitiktin\u012F adres\u0105, kuriuo taip pat pa\u0161alinamos slaptos sekimo priemon\u0117s ir el. lai\u0161kai persiun\u010Diami \u012F pa\u0161to d\u0117\u017Eut\u0119.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Tvarkykite i\u0161saugotus elementus\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Tvarkykite kredito korteles\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Tvarkykite tapatybes\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Slapta\u017Eod\u017Ei\u0173 valdymas\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Generuoti privat\u0173 \u201EDuck Address\u201C",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Blokuoti el. pa\u0161to steb\u0117jimo priemones ir sl\u0117pti adres\u0105",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Apsaugoti mano el. pa\u0161t\u0105",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Daugiau nerodyti",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Importuoti slapta\u017Eod\u012F \u012F \u201EDuckDuckGo\u201C",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Greitai ir saugiai perkelkite slapta\u017Eod\u017Eius i\u0161 kitos nar\u0161ykl\u0117s ar slapta\u017Eod\u017Ei\u0173 tvarkykl\u0117s.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Galiojimo data",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/lv/autofill.json
  var autofill_default15 = {
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
    hello: {
      title: "Sveika, pasaule",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Izmantot {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Blo\u0137\u0113 e-pasta izsekot\u0101jus",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "{url} parole",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "\u0122ener\u0113ta parole",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Parole tiks saglab\u0101ta \u0161ai vietnei",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden ir blo\u0137\u0113ts",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Atblo\u0137\u0113 glab\u0101tavu, lai piek\u013C\u016Btu pieteik\u0161an\u0101s datiem vai \u0123ener\u0113tu paroles",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "\u0122ener\u0113t priv\u0101tu Duck adresi",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Pasl\u0113p savu e-pastu un blo\u0137\u0113 izsekot\u0101jus",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Izveido unik\u0101lu, nejau\u0161i izv\u0113l\u0113tu adresi, kas ar\u012B aizv\u0101c sl\u0113ptos izsekot\u0101jus un p\u0101rs\u016Bta e-pastus uz tavu pastkast\u012Bti.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "P\u0101rvald\u012Bt saglab\u0101tos vienumus\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "P\u0101rvald\u012Bt kred\u012Btkartes\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "P\u0101rvald\u012Bt identit\u0101tes",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "P\u0101rvald\u012Bt paroles\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "\u0122ener\u0113t priv\u0101tu Duck adresi",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Blo\u0137\u0113 e-pasta izsekot\u0101jus un pasl\u0113p adresi",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Aizsarg\u0101t manu e-pastu",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Turpm\u0101k ner\u0101d\u012Bt",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Import\u0113t paroli DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "\u0100tri un dro\u0161i p\u0101rnes savas paroles no citas p\u0101rl\u016Bkprogrammas vai paro\u013Cu p\u0101rvaldnieka.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Der\u012Bguma termi\u0146\u0161",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/nb/autofill.json
  var autofill_default16 = {
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
    hello: {
      title: "Hallo verden",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Bruk {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Blokker e-postsporere",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Passord for {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Generert passord",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Passordet blir lagret for dette nettstedet",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden er l\xE5st",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "L\xE5s opp hvelvet ditt for \xE5 f\xE5 tilgang til legitimasjon eller generere passord",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Generer privat Duck Address",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Skjul e-postadressen din og blokker sporere",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Opprett en unik, tilfeldig adresse som ogs\xE5 fjerner skjulte sporere og videresender e-post til innboksen din.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Administrer lagrede elementer\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Administrer kredittkort\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Administrer identiteter\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Administrer passord\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Generer en privat Duck Address",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Blokker e-postsporere og skjul adresse",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Beskytt e-postadressen min",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Ikke vis igjen",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Importer passord til DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Overf\xF8r passordene dine raskt og sikkert fra en annen nettleser eller passordbehandling.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Utl\xF8psdato",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/nl/autofill.json
  var autofill_default17 = {
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
    hello: {
      title: "Hallo wereld",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "{email} gebruiken",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "E-mailtrackers blokkeren",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Wachtwoord voor {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Gegenereerd wachtwoord",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Wachtwoord wordt opgeslagen voor deze website",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden is vergrendeld",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Ontgrendelt je kluis om toegang te krijgen tot inloggegevens of om wachtwoorden te genereren",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Priv\xE9-Duck Address genereren",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Je e-mailadres verbergen en trackers blokkeren",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Maak een uniek, willekeurig adres dat ook verborgen trackers verwijdert en e-mails doorstuurt naar je inbox.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Opgeslagen items beheren\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Creditcards beheren\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Identiteiten beheren\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Wachtwoorden beheren\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Priv\xE9-Duck Address genereren",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "E-mailtrackers blokkeren en adres verbergen",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Mijn e-mailadres beschermen",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Niet meer weergeven",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Wachtwoorden importeren naar DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Snel en veilig je wachtwoorden overzetten vanuit een andere browser of wachtwoordmanager.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Vervaldatum",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/pl/autofill.json
  var autofill_default18 = {
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
    hello: {
      title: "Witajcie",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "U\u017Cyj {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Blokuj mechanizmy \u015Bledz\u0105ce poczt\u0119 e-mail",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Has\u0142o do witryny {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Wygenerowane has\u0142o",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Has\u0142o zostanie zapisane na potrzeby tej witryny",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Aplikacja Bitwarden jest zablokowana",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Odblokuj sejf, aby uzyska\u0107 dost\u0119p do po\u015Bwiadcze\u0144 lub generowa\u0107 has\u0142a",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Wygeneruj prywatny adres Duck Address",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Ukryj sw\xF3j adres e-mail i blokuj skrypty \u015Bledz\u0105ce",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Utw\xF3rz unikalny, losowy adres, kt\xF3ry usuwa ukryte mechanizmy \u015Bledz\u0105ce i przekazuje wiadomo\u015Bci e-mail do Twojej skrzynki odbiorczej.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Zarz\u0105dzaj zapisanymi elementami\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Zarz\u0105dzaj kartami kredytowymi\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Zarz\u0105dzaj to\u017Csamo\u015Bciami\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Zarz\u0105dzaj has\u0142ami\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Wygeneruj prywatny adres Duck Address",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Zablokuj mechanizmy \u015Bledz\u0105ce poczt\u0119 e-mail i ukryj adres",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Chro\u0144 moj\u0105 poczt\u0119 e-mail",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Nie pokazuj wi\u0119cej",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Importuj has\u0142a do DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Szybko i bezpiecznie przenie\u015B has\u0142a z innej przegl\u0105darki lub mened\u017Cera hase\u0142.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Wa\u017Cno\u015B\u0107",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/pt/autofill.json
  var autofill_default19 = {
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
    hello: {
      title: "Ol\xE1, mundo",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Usar {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Bloquear rastreadores de e-mail",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Palavra-passe de {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Palavra-passe gerada",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "A palavra-passe deste site ser\xE1 guardada",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "O Bitwarden est\xE1 bloqueado",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Desbloqueia o teu cofre para aceder a credenciais ou gerar palavras-passe",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Gerar um Duck Address privado",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Ocultar o teu e-mail e bloquear rastreadores",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Criar um endere\xE7o aleat\xF3rio exclusivo que tamb\xE9m remove rastreadores escondidos e encaminha o e-mail para a tua caixa de entrada.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Gerir itens guardados\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Gerir cart\xF5es de cr\xE9dito\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Gerir identidades\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Gerir palavras-passe\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Gerar um Duck Address Privado",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Bloquear rastreadores de e-mail e ocultar endere\xE7o",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Proteger o meu e-mail",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "N\xE3o mostrar novamente",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Importar palavras-passe para o DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Transfere de forma r\xE1pida e segura as tuas palavras-passe a partir de outro navegador ou gestor de palavras-passe.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Validade",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/ro/autofill.json
  var autofill_default20 = {
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
    hello: {
      title: "Salut!",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Utilizeaz\u0103 {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Blocheaz\u0103 tehnologiile de urm\u0103rire din e-mail",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Parola pentru {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Parola generat\u0103",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Parola va fi salvat\u0103 pentru acest site",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden este blocat",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Deblocheaz\u0103 seiful pentru a accesa datele de conectare sau pentru a genera parole",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Genereaz\u0103 o Duck Address privat\u0103",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Ascunde-\u021Bi e-mailul \u0219i blocheaz\u0103 tehnologiile de urm\u0103rire",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Creeaz\u0103 o adres\u0103 unic\u0103, aleatorie, care elimin\u0103 \u0219i tehnologiile de urm\u0103rire ascunse \u0219i redirec\u021Bioneaz\u0103 e-mailurile c\u0103tre c\u0103su\u021Ba ta de inbox.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Gestioneaz\u0103 elementele salvate\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Gestioneaz\u0103 cardurile de credit\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Gestioneaz\u0103 identit\u0103\u021Bile\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Gestioneaz\u0103 parolele\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Genereaz\u0103 o Duck Address privat\u0103",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Blocheaz\u0103 tehnologiile de urm\u0103rire din e-mailuri \u0219i ascunde adresa",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Protejeaz\u0103-mi adresa de e-mail",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Nu mai afi\u0219a",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Import\u0103 parola \xEEn DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Transfer\u0103 rapid \u0219i sigur parolele dintr-un alt browser sau manager de parole.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Expir\u0103",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/ru/autofill.json
  var autofill_default21 = {
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
    hello: {
      title: "\u041F\u0440\u0438\u0432\u0435\u0442, \u043C\u0438\u0440!",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "\u0418\u0441\u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u044C {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "\u0411\u043B\u043E\u043A\u0438\u0440\u0443\u0435\u0442 \u043F\u043E\u0447\u0442\u043E\u0432\u044B\u0435 \u0442\u0440\u0435\u043A\u0435\u0440\u044B",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "\u041F\u0430\u0440\u043E\u043B\u044C \u0434\u043B\u044F {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "\u0421\u0433\u0435\u043D\u0435\u0440\u0438\u0440\u043E\u0432\u0430\u043D\u043D\u044B\u0439 \u043F\u0430\u0440\u043E\u043B\u044C",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "\u041F\u0430\u0440\u043E\u043B\u044C \u0431\u0443\u0434\u0435\u0442 \u0441\u043E\u0445\u0440\u0430\u043D\u0435\u043D \u0434\u043B\u044F \u044D\u0442\u043E\u0433\u043E \u0441\u0430\u0439\u0442\u0430",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "\u041F\u0440\u0438\u043B\u043E\u0436\u0435\u043D\u0438\u0435 Bitwarden \u0437\u0430\u0431\u043B\u043E\u043A\u0438\u0440\u043E\u0432\u0430\u043D\u043E",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "\u0420\u0430\u0437\u0431\u043B\u043E\u043A\u0438\u0440\u0443\u0439\u0442\u0435 \u0445\u0440\u0430\u043D\u0438\u043B\u0438\u0449\u0435, \u0447\u0442\u043E\u0431\u044B \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u044C\u0441\u044F \u0443\u0447\u0435\u0442\u043D\u044B\u043C\u0438 \u0434\u0430\u043D\u043D\u044B\u043C\u0438 \u0438 \u0433\u0435\u043D\u0435\u0440\u0438\u0440\u043E\u0432\u0430\u0442\u044C \u043F\u0430\u0440\u043E\u043B\u0438.",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "\u0421\u0433\u0435\u043D\u0435\u0440\u0438\u0440\u043E\u0432\u0430\u0442\u044C Duck Address",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "\u0421\u043A\u0440\u044B\u0442\u0438\u0435 \u0430\u0434\u0440\u0435\u0441\u0430 \u043F\u043E\u0447\u0442\u044B \u0438 \u0431\u043B\u043E\u043A\u0438\u0440\u043E\u0432\u043A\u0430 \u0442\u0440\u0435\u043A\u0435\u0440\u043E\u0432",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "\u0421\u043E\u0437\u0434\u0430\u0439\u0442\u0435 \u0443\u043D\u0438\u043A\u0430\u043B\u044C\u043D\u044B\u0439 \u0441\u043B\u0443\u0447\u0430\u0439\u043D\u044B\u0439 \u0430\u0434\u0440\u0435\u0441, \u043A\u043E\u0442\u043E\u0440\u044B\u0439 \u0442\u0430\u043A\u0436\u0435 \u0443\u0434\u0430\u043B\u0438\u0442 \u0441\u043A\u0440\u044B\u0442\u044B\u0435 \u0442\u0440\u0435\u043A\u0435\u0440\u044B \u0438 \u043F\u0435\u0440\u0435\u043D\u0430\u043F\u0440\u0430\u0432\u0438\u0442 \u044D\u043B\u0435\u043A\u0442\u0440\u043E\u043D\u043D\u0443\u044E \u043F\u043E\u0447\u0442\u0443 \u043D\u0430 \u0432\u0430\u0448 \u044F\u0449\u0438\u043A.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "\u041D\u0430\u0441\u0442\u0440\u043E\u0438\u0442\u044C \u0441\u043E\u0445\u0440\u0430\u043D\u0435\u043D\u043D\u044B\u0435 \u0434\u0430\u043D\u043D\u044B\u0435\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "\u041D\u0430\u0441\u0442\u0440\u043E\u0438\u0442\u044C \u043F\u043B\u0430\u0442\u0435\u0436\u043D\u044B\u0435 \u043A\u0430\u0440\u0442\u044B\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "\u041D\u0430\u0441\u0442\u0440\u043E\u0438\u0442\u044C \u0443\u0447\u0435\u0442\u043D\u044B\u0435 \u0434\u0430\u043D\u043D\u044B\u0435\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "\u0423\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u043F\u0430\u0440\u043E\u043B\u044F\u043C\u0438\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "\u0421\u0433\u0435\u043D\u0435\u0440\u0438\u0440\u043E\u0432\u0430\u0442\u044C Duck Address",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "\u0421\u043A\u0440\u044B\u0432\u0430\u0435\u0442 \u0432\u0430\u0448 \u0430\u0434\u0440\u0435\u0441 \u0438 \u0411\u043B\u043E\u043A\u0438\u0440\u0443\u0435\u0442 \u043F\u043E\u0447\u0442\u043E\u0432\u044B\u0435 \u0442\u0440\u0435\u043A\u0435\u0440\u044B",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "\u0417\u0430\u0449\u0438\u0442\u0438\u0442\u044C \u043F\u043E\u0447\u0442\u0443",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "\u0411\u043E\u043B\u044C\u0448\u0435 \u043D\u0435 \u043F\u043E\u043A\u0430\u0437\u044B\u0432\u0430\u0442\u044C",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "\u0418\u043C\u043F\u043E\u0440\u0442\u0438\u0440\u0443\u0439\u0442\u0435 \u043F\u0430\u0440\u043E\u043B\u0438 \u0432 DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "\u0411\u044B\u0441\u0442\u0440\u044B\u0439 \u0438 \u0431\u0435\u0437\u043E\u043F\u0430\u0441\u043D\u044B\u0439 \u0441\u043F\u043E\u0441\u043E\u0431 \u043F\u0435\u0440\u0435\u043D\u0435\u0441\u0442\u0438 \u043F\u0430\u0440\u043E\u043B\u0438 \u0438\u0437 \u0434\u0440\u0443\u0433\u043E\u0433\u043E \u0431\u0440\u0430\u0443\u0437\u0435\u0440\u0430 \u0438\u043B\u0438 \u043C\u0435\u043D\u0435\u0434\u0436\u0435\u0440\u0430.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "\u0421\u0440\u043E\u043A \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044F",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/sk/autofill.json
  var autofill_default22 = {
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
    hello: {
      title: "Ahoj, svet!",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Pou\u017Ei\u0165 {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Blokuje e-mailov\xE9 sledova\u010De",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Heslo pre {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Vygenerovan\xE9 heslo",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Heslo pre t\xFAto webov\xFA str\xE1nku bude ulo\u017Een\xE9",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden je uzamknut\xFD",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Odomknite trezor pre pr\xEDstup k prihlasovac\xEDm \xFAdajom alebo generovaniu hesiel",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Generova\u0165 s\xFAkromn\xFA Duck Address",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Skryte svoj e-mail a blokujte sledova\u010De",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Vytvorte si n\xE1hodn\xFA jedine\u010Dn\xFA adresu, ktor\xE1 odstr\xE1ni aj skryt\xE9 sledovacie prvky a prepo\u0161le e-maily do va\u0161ej schr\xE1nky.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Spravova\u0165 ulo\u017Een\xE9 polo\u017Eky\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Spravova\u0165 kreditn\xE9 karty\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Spravova\u0165 identity\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Spravova\u0165 hesl\xE1\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Generova\u0165 s\xFAkromn\xFA Duck Address",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Blokujte sledova\u010De e-mailov a skryte adresu",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Ochrana m\xF4jho e-mailu",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Nabud\xFAce u\u017E neukazova\u0165",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Importova\u0165 heslo do slu\u017Eby DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "R\xFDchlo a\xA0bezpe\u010Dne preneste svoje hesl\xE1 z\xA0in\xE9ho prehliada\u010Da alebo spr\xE1vcu hesiel.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Vypr\u0161anie platnosti",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/sl/autofill.json
  var autofill_default23 = {
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
    hello: {
      title: "Pozdravljen, svet",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Uporabite {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Blokirajte sledilnike e-po\u0161te",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "Geslo za spletno mesto {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Ustvarjeno geslo",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Geslo bo shranjeno za to spletno mesto",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden je zaklenjen",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Odklenite trezor za dostop do poverilnic ali ustvarjanje gesel",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Ustvarjanje zasebnega naslova Duck Address",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "Skrijte svojo e-po\u0161to in blokirajte sledilnike",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Ustvarite edinstven, naklju\u010Den naslov, ki odstrani tudi skrite sledilnike in posreduje e-po\u0161to v va\u0161 e-po\u0161tni predal.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Upravljaj shranjene elemente\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Upravljaj kreditne kartice\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Upravljaj identitete\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Upravljanje gesel\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Ustvari zasebni naslov Duck Address",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Blokirajte sledilnike e-po\u0161te in skrijte naslov",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Za\u0161\u010Diti mojo e-po\u0161to",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Ne prika\u017Ei ve\u010D",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Uvoz gesla v DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "Hitro in varno prenesite gesla iz drugega brskalnika ali upravitelja gesel.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Datum poteka",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/sv/autofill.json
  var autofill_default24 = {
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
    hello: {
      title: "Hej v\xE4rlden",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "Anv\xE4nd {email}",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "Blockera e-postsp\xE5rare",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "L\xF6senord f\xF6r {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Genererat l\xF6senord",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "L\xF6senordet sparas f\xF6r den h\xE4r webbplatsen",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden \xE4r l\xE5st",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "L\xE5s upp ditt valv f\xF6r att komma \xE5t inloggningsuppgifter eller generera l\xF6senord",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Generera privat Duck Address",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "D\xF6lj din e-postadress och blockera sp\xE5rare",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Skapa en unik, slumpm\xE4ssig adress som ocks\xE5 tar bort dolda sp\xE5rare och vidarebefordrar e-post till din inkorg.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Hantera sparade objekt\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Hantera kreditkort\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Hantera identiteter\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "Hantera l\xF6senord\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "Generera en privat Duck Address",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "Blockera e-postsp\xE5rare och d\xF6lj din adress",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Skydda min e-postadress",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Visa inte igen",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "Importera l\xF6senord till DuckDuckGo",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "\xD6verf\xF6r snabbt och s\xE4kert dina l\xF6senord fr\xE5n en annan webbl\xE4sare eller l\xF6senordshanterare.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Utg\xE5ngsdatum",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/tr/autofill.json
  var autofill_default25 = {
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
    hello: {
      title: "Hello world",
      note: "Static text for testing."
    },
    lipsum: {
      title: "Lorem ipsum dolor sit amet, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "{email} kullan",
      note: 'Button that fills a form using a specific email address. The placeholder is the email address, e.g. "Use test@duck.com".'
    },
    blockEmailTrackers: {
      title: "E-posta izleyicileri engelleyin",
      note: 'Label explaining that by using a duck.com address, email trackers will be blocked. "Block" is a verb in imperative form.'
    },
    passwordForUrl: {
      title: "{url} \u015Fifresi",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Olu\u015Fturulan \u015Fifre",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "\u015Eifre bu web sitesi i\xE7in kaydedilecek",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden kilitlendi",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Kimlik bilgilerine eri\u015Fmek veya \u015Fifre olu\u015Fturmak i\xE7in kasan\u0131z\u0131n kilidini a\xE7\u0131n",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "\xD6zel Duck Address Olu\u015Ftur",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    hideEmailAndBlockTrackers: {
      title: "E-postan\u0131z\u0131 Gizleyin ve \u0130zleyicileri Engelleyin",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "Gizli izleyicileri de kald\u0131ran ve e-postalar\u0131 gelen kutunuza ileten benzersiz, rastgele bir adres olu\u015Fturun.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "Kaydedilen \xF6\u011Feleri y\xF6netin\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "Kredi kartlar\u0131n\u0131 y\xF6netin\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "Kimlikleri y\xF6netin\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "\u015Eifreleri y\xF6net\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "\xD6zel Duck Address Olu\u015Ftur",
      note: 'Button that when clicked creates a new private email address and fills the corresponding field with the generated address. "Generate" is a verb in imperative form, and "Duck Address" is a proper noun that should not be translated.'
    },
    blockEmailTrackersAndHideAddress: {
      title: "E-posta izleyicileri engelleyin ve adresi gizleyin",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "E-postam\u0131 Koru",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Bir Daha G\xF6sterme",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    credentialsImportHeading: {
      title: "\u015Eifreyi DuckDuckGo'ya aktar",
      note: "Label that when clicked, will open a dialog to import user's credentials from other browsers"
    },
    credentialsImportText: {
      title: "\u015Eifrelerinizi ba\u015Fka bir taray\u0131c\u0131dan veya \u015Fifre y\xF6neticisinden h\u0131zl\u0131 ve g\xFCvenli bir \u015Fekilde aktar\u0131n.",
      note: "Subtitle that explains the purpose of the import dialog"
    },
    expiry: {
      title: "Son kullanma tarihi",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/xa/autofill.json
  var autofill_default26 = {
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
    hello: {
      title: "H33ll00 w\xBArrld",
      note: "Static text for testing."
    },
    lipsum: {
      title: "L\xBArr3e3m 1p$$$um d00l1loor s!t @@mett, {foo} {bar}",
      note: "Placeholder text."
    },
    usePersonalDuckAddr: {
      title: "\xDC55\xA3\xA3 {email}",
      note: "Shown when a user can choose their personal @duck.com address."
    },
    blockEmailTrackers: {
      title: "Bl000ck \u20ACm@@@i1il1l tr\xE4\xE1\xE5ck33rr55",
      note: "Shown when a user can choose their personal @duck.com address on native platforms."
    },
    passwordForUrl: {
      title: "Pa@assw0rdd ffo\xF6r {url}",
      note: "Button that fills a form's password field with the saved password for that site. The placeholder 'url' is URL of the matched site, e.g. 'https://example.duckduckgo.com'."
    },
    generatedPassword: {
      title: "Gen33rat\xE9\xE9\xE9d pa@assw0rdd",
      note: 'Label on a button that, when clicked, fills an automatically-created password into a signup form. "Generated" is an adjective in past tense.'
    },
    passwordWillBeSaved: {
      title: "Pa@assw0rdd wi11lll \xDF3 $avvved for th\xEE\xEF$s website",
      note: "Label explaining that the associated automatically-created password will be persisted for the current site when the form is submitted"
    },
    bitwardenIsLocked: {
      title: "Bitwarden iiss l\xF6\xF8c\xE7k3d\u2202",
      note: "Label explaining that passwords are not available because the vault provided by third-party application Bitwarden has not been unlocked"
    },
    unlockYourVault: {
      title: "Unlock yo0ur va@u\xFClt to acce\xE9$$s cr\xE9de\xF1\xF1t\xEF\xE5\xE5\xE5ls or g\xE9\xE9nera\xE5te pass55w\xBA\xBArds5",
      note: "Label explaining that users must unlock the third-party password manager Bitwarden before they can use passwords stored there or create new passwords"
    },
    generatePrivateDuckAddr: {
      title: "Ge\xF1\xF1\xEB\xE9r\xE5\xE5te Priiivate Duck Addddrrreess",
      note: 'Button that creates a new single-use email address and fills a form with that address. "Generate" is a verb in imperative form.'
    },
    hideEmailAndBlockTrackers: {
      title: "H\xEE\xEF\xEDde yo0\xF8ur \xA3\xA3m@il an\u2202\u2202\u2202 bll\xBAck tr@c\xE7ck3rs",
      note: 'Button title prompting users to use an randomly-generated email address. "Hide" and "block" are imperative verbs.'
    },
    createUniqueRandomAddr: {
      title: "\xC7\xC7r3\xA3ate @ \xFC\xFB\xFAn11que, r@@nd0\xF8m ad\u2202dr3s5s that als0\xBA r3mov3s hidd\xA3\xA3n tr@cker$5$ and forwards em@@1l to your 1\xF1b0x.",
      note: 'Button subtitle (paired with "hideEmailAndBlockTrackers") explaining that by creating a randomly-generated address, trackers within emails will also be blocked.'
    },
    manageSavedItems: {
      title: "M\xE5an\xF1age\xE9 s@@ved 17733m5\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved items used to fill forms on web pages. The type of item is indeterminate, so this is intentionally more vague than "manageCreditCards", "manageIdentities", and "managePassworeds". "Manage" is an imperative verb.'
    },
    manageCreditCards: {
      title: "M\xE5an\xF1age\xE9 \xA2\xA2r\xA3d17 ca\xAE\xAE\xAE\u2202\u2202s\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more credit cards used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    manageIdentities: {
      title: "M\xE5an\xF1age\xE9 \xA1d\xA3\xA3nt11ties\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more identities. "Manage" is an imperative verb. An "Identity" (singular of "identities") is a noun representing the combiantion of name, birthday, physical address, email address, and phone number used to fill forms on a web page.'
    },
    managePasswords: {
      title: "M\xE5an\xF1age\xE9 p@\xE5$$$w\xBA\xBAr\u2202\u2202s\u2026",
      note: 'Button that when clicked allows users to add, edit, or delete one or more saved passwords used to fill forms on a web page. "Manage" is an imperative verb.'
    },
    generateDuckAddr: {
      title: "G\xE9\xE9\xE9ner@te a Pr\xEE\xEE\xEEvate DDDuck Addr\xE9\xE9s$s",
      note: "Button that when clicked creates a new private email address and fills the corresponding field with the generated address."
    },
    blockEmailTrackersAndHideAddress: {
      title: "Blo\xBA\xF8ck \xA3m\xE5\xE5il tr@\xE5ack\xA3\xA3rs && h\xEF\xEF\xEFd\xE9\xE9 ad\u2202dr33s5s$",
      note: 'Label (paired with "generateDuckAddr") explaining the benefits of creating a private DuckDuckGo email address. "Block" and "hide" are imperative verbs.'
    },
    protectMyEmail: {
      title: "Pr\xBA\xBA\u2020\u2020\xA3ct M\xA5\xA5 Em@@i\xEEl",
      note: 'Link that takes users to "https://duckduckgo.com/email/start-incontext", where they can sign up for DuckDuckGo email protection.'
    },
    dontShowAgain: {
      title: "Do\xF8\xBAn\xF1't Sh00w Ag@\xE5\xE5\xEEn",
      note: "Button that prevents the DuckDuckGo email protection signup prompt from appearing again."
    },
    expiry: {
      title: "\xA3\xA3xp@\xEE\xEFr\xA5\xA5",
      note: "Label that indicates the expiration date of credit cards."
    }
  };

  // src/locales/translations.js
  var translations_default = {
    bg: { autofill: autofill_default },
    cs: { autofill: autofill_default2 },
    da: { autofill: autofill_default3 },
    de: { autofill: autofill_default4 },
    el: { autofill: autofill_default5 },
    en: { autofill: autofill_default6 },
    es: { autofill: autofill_default7 },
    et: { autofill: autofill_default8 },
    fi: { autofill: autofill_default9 },
    fr: { autofill: autofill_default10 },
    hr: { autofill: autofill_default11 },
    hu: { autofill: autofill_default12 },
    it: { autofill: autofill_default13 },
    lt: { autofill: autofill_default14 },
    lv: { autofill: autofill_default15 },
    nb: { autofill: autofill_default16 },
    nl: { autofill: autofill_default17 },
    pl: { autofill: autofill_default18 },
    pt: { autofill: autofill_default19 },
    ro: { autofill: autofill_default20 },
    ru: { autofill: autofill_default21 },
    sk: { autofill: autofill_default22 },
    sl: { autofill: autofill_default23 },
    sv: { autofill: autofill_default24 },
    tr: { autofill: autofill_default25 },
    xa: { autofill: autofill_default26 }
  };

  // src/locales/strings.js
  function getTranslator(settings) {
    let library;
    return function t(id, opts) {
      if (!library) {
        const { language } = settings;
        library = translations_default[language];
        if (!library) {
          console.warn(`Received unsupported locale '${language}'. Falling back to 'en'.`);
          library = translations_default.en;
        }
      }
      return translateImpl(library, id, opts);
    };
  }
  function translateImpl(library, namespacedId, opts) {
    const [namespace, id] = namespacedId.split(":", 2);
    const namespacedLibrary = library[namespace];
    if (!namespacedLibrary) {
      return id;
    }
    const msg = namespacedLibrary[id];
    if (!msg) {
      return id;
    }
    if (!opts) {
      return msg.title;
    }
    let out = msg.title;
    for (const [name, value] of Object.entries(opts)) {
      out = out.replaceAll(`{${name}}`, value);
    }
    return out;
  }

  // src/CredentialsImport.js
  var CredentialsImport = class {
    /** @param {import("./DeviceInterface/InterfacePrototype").default} device */
    constructor(device) {
      this.device = device;
    }
    /**
     * Check if password promotion prompt should be shown. Only returns valid value in the main webiew.
     */
    isAvailable() {
      return Boolean(this.device.settings.availableInputTypes.credentialsImport);
    }
    init() {
      if (!this.device.globalConfig.hasModernWebkitAPI) return;
      try {
        Object.defineProperty(window, "credentialsImportFinished", {
          enumerable: false,
          configurable: false,
          writable: false,
          value: () => {
            this.refresh();
          }
        });
      } catch (e) {
      }
    }
    /**
     * @param {import("./deviceApiCalls/__generated__/validators-ts").AvailableInputTypes} [availableInputTypes]
     */
    async refresh(availableInputTypes) {
      const inputTypes = availableInputTypes || await this.device.settings.getAvailableInputTypes();
      this.device.settings.setAvailableInputTypes(inputTypes);
      this.device.scanner.forms.forEach((form) => form.redecorateAllInputs());
      this.device.uiController?.removeTooltip("interface");
      const activeForm = this.device.activeForm;
      if (!activeForm) return;
      const { activeInput } = activeForm;
      const { username, password } = this.device.settings.availableInputTypes.credentials || {};
      if (activeInput && (username || password)) {
        this.device.attachTooltip({
          form: activeForm,
          input: activeInput,
          click: null,
          trigger: "credentialsImport",
          triggerMetaData: {
            type: "transactional"
          }
        });
      }
    }
    async started() {
      this.device.deviceApi.notify(new StartCredentialsImportFlowCall({}));
      this.device.deviceApi.notify(new CloseAutofillParentCall(null));
    }
    async dismissed() {
      this.device.deviceApi.notify(new CredentialsImportFlowPermanentlyDismissedCall(null));
      this.device.deviceApi.notify(new CloseAutofillParentCall(null));
    }
  };

  // src/DeviceInterface/InterfacePrototype.js
  var _addresses, _data6;
  var _InterfacePrototype = class _InterfacePrototype {
    /**
     * @param {GlobalConfig} config
     * @param {import("../../packages/device-api").DeviceApi} deviceApi
     * @param {Settings} settings
     */
    constructor(config, deviceApi, settings) {
      __publicField(this, "attempts", 0);
      /** @type {import("../Form/Form").Form | null} */
      __publicField(this, "activeForm", null);
      /** @type {import("../UI/HTMLTooltip.js").default | null} */
      __publicField(this, "currentTooltip", null);
      /** @type {number} */
      __publicField(this, "initialSetupDelayMs", 0);
      __publicField(this, "autopromptFired", false);
      /** @type {PasswordGenerator} */
      __publicField(this, "passwordGenerator", new PasswordGenerator());
      __publicField(this, "emailProtection", new EmailProtection(this));
      __publicField(this, "credentialsImport", new CredentialsImport(this));
      /** @type {Object | null} */
      __publicField(this, "focusApi", null);
      /** @type {import("../InContextSignup.js").InContextSignup | null} */
      __publicField(this, "inContextSignup", null);
      /** @type {import("../ThirdPartyProvider.js").ThirdPartyProvider | null} */
      __publicField(this, "thirdPartyProvider", null);
      /** @type {{privateAddress: string, personalAddress: string}} */
      __privateAdd(this, _addresses, {
        privateAddress: "",
        personalAddress: ""
      });
      /** @type {GlobalConfig} */
      __publicField(this, "globalConfig");
      /** @type {import('../Scanner').Scanner} */
      __publicField(this, "scanner");
      /** @type {import("../UI/controllers/UIController.js").UIController | null} */
      __publicField(this, "uiController");
      /** @type {import("../../packages/device-api").DeviceApi} */
      __publicField(this, "deviceApi");
      /**
       * Translates a string to the current language, replacing each placeholder
       * with a key present in `opts` with the corresponding value.
       * @type {import('../locales/strings').TranslateFn}
       */
      __publicField(this, "t");
      /** @type {boolean} */
      __publicField(this, "isInitializationStarted");
      /** @type {((reason, ...rest) => void) | null} */
      __publicField(this, "_scannerCleanup", null);
      /** @type { PMData } */
      __privateAdd(this, _data6, {
        credentials: [],
        creditCards: [],
        identities: [],
        topContextData: void 0
      });
      this.globalConfig = config;
      this.deviceApi = deviceApi;
      this.settings = settings;
      this.t = getTranslator(settings);
      this.uiController = null;
      this.scanner = createScanner(this, {
        initialDelay: this.initialSetupDelayMs
      });
      this.isInitializationStarted = false;
    }
    /**
     * Implementors should override this with a UI controller that suits
     * their platform.
     *
     * @returns {import("../UI/controllers/UIController.js").UIController}
     */
    createUIController() {
      return new NativeUIController();
    }
    /**
     * @param {string} reason
     */
    removeAutofillUIFromPage(reason) {
      this.uiController?.destroy();
      this._scannerCleanup?.(reason);
      this.focusApi?.cleanup?.();
    }
    get hasLocalAddresses() {
      return !!(__privateGet(this, _addresses)?.privateAddress && __privateGet(this, _addresses)?.personalAddress);
    }
    getLocalAddresses() {
      return __privateGet(this, _addresses);
    }
    storeLocalAddresses(addresses) {
      __privateSet(this, _addresses, addresses);
      const identities = this.getLocalIdentities();
      const privateAddressIdentity = identities.find(({ id }) => id === "privateAddress");
      if (privateAddressIdentity) {
        privateAddressIdentity.emailAddress = formatDuckAddress(addresses.privateAddress);
      } else {
        __privateGet(this, _data6).identities = this.addDuckAddressesToIdentities(identities);
      }
    }
    /**
     * @returns {import('../Form/matching').SupportedTypes}
     */
    getCurrentInputType() {
      throw new Error("Not implemented");
    }
    addDuckAddressesToIdentities(identities) {
      if (!this.hasLocalAddresses) return identities;
      const newIdentities = [];
      let { privateAddress, personalAddress } = this.getLocalAddresses();
      privateAddress = formatDuckAddress(privateAddress);
      personalAddress = formatDuckAddress(personalAddress);
      const duckEmailsInIdentities = identities.reduce(
        (duckEmails, { emailAddress: email }) => email?.includes(ADDRESS_DOMAIN) ? duckEmails.concat(email) : duckEmails,
        []
      );
      if (!duckEmailsInIdentities.includes(personalAddress)) {
        newIdentities.push({
          id: "personalAddress",
          emailAddress: personalAddress,
          title: this.t("autofill:blockEmailTrackers")
        });
      }
      newIdentities.push({
        id: "privateAddress",
        emailAddress: privateAddress,
        title: this.t("autofill:blockEmailTrackersAndHideAddress")
      });
      return [...identities, ...newIdentities];
    }
    /**
     * Stores init data coming from the tooltipHandler
     * @param { InboundPMData } data
     */
    storeLocalData(data) {
      this.storeLocalCredentials(data.credentials);
      data.creditCards.forEach((cc) => delete cc.cardNumber && delete cc.cardSecurityCode);
      const updatedIdentities = data.identities.map((identity) => ({
        ...identity,
        fullName: formatFullName(identity)
      }));
      __privateGet(this, _data6).identities = this.addDuckAddressesToIdentities(updatedIdentities);
      __privateGet(this, _data6).creditCards = data.creditCards;
      if (data.serializedInputContext) {
        try {
          __privateGet(this, _data6).topContextData = JSON.parse(data.serializedInputContext);
        } catch (e) {
          console.error(e);
          this.removeTooltip();
        }
      }
    }
    /**
     * Stores credentials locally
     * @param {CredentialsObject[]} credentials
     */
    storeLocalCredentials(credentials) {
      credentials.forEach((cred) => delete cred.password);
      __privateGet(this, _data6).credentials = credentials;
    }
    getTopContextData() {
      return __privateGet(this, _data6).topContextData;
    }
    /**
     * @deprecated use `availableInputTypes.credentials` directly instead
     * @returns {boolean}
     */
    get hasLocalCredentials() {
      return __privateGet(this, _data6).credentials.length > 0;
    }
    getLocalCredentials() {
      return __privateGet(this, _data6).credentials.map((cred) => {
        const { password, ...rest } = cred;
        return rest;
      });
    }
    /**
     * @deprecated use `availableInputTypes.identities` directly instead
     * @returns {boolean}
     */
    get hasLocalIdentities() {
      return __privateGet(this, _data6).identities.length > 0;
    }
    getLocalIdentities() {
      return __privateGet(this, _data6).identities;
    }
    /**
     * @deprecated use `availableInputTypes.creditCards` directly instead
     * @returns {boolean}
     */
    get hasLocalCreditCards() {
      return __privateGet(this, _data6).creditCards.length > 0;
    }
    /** @return {CreditCardObject[]} */
    getLocalCreditCards() {
      return __privateGet(this, _data6).creditCards;
    }
    /**
     * Initializes a global focus event handler that handles iOS keyboard and autocomplete functionality
     * @param {Map<HTMLElement, import("../Form/Form").Form>} forms - Collection of form objects to monitor
     * @returns {Object}
     */
    initGlobalFocusHandler(forms) {
      return initFocusApi(forms, this.settings, ({ form, element }) => this.attachKeyboard({ device: this, form, element }));
    }
    async startInit() {
      if (this.isInitializationStarted) return;
      this.isInitializationStarted = true;
      this.addDeviceListeners();
      await this.setupAutofill();
      this.uiController = this.createUIController();
      if (!this.settings.enabled) {
        return;
      }
      await this.setupSettingsPage();
      await this.postInit();
      if (this.settings.featureToggles.credentials_saving) {
        initFormSubmissionsApi(this.scanner.forms, this.scanner.matching);
      }
      if (this.settings.featureToggles.input_focus_api || this.settings.featureToggles.autocomplete_attribute_support) {
        this.focusApi = this.initGlobalFocusHandler(this.scanner.forms);
      }
    }
    async init() {
      const settings = await this.settings.refresh();
      if (!settings.enabled) return;
      const handler = async () => {
        if (document.readyState === "complete") {
          window.removeEventListener("load", handler);
          document.removeEventListener("readystatechange", handler);
          await this.startInit();
        }
      };
      if (document.readyState === "complete") {
        await this.startInit();
      } else {
        window.addEventListener("load", handler);
        document.addEventListener("readystatechange", handler);
      }
    }
    postInit() {
      const cleanup = this.scanner.init();
      this.addLogoutListener(() => {
        cleanup("Logged out");
        if (this.globalConfig.isDDGDomain) {
          notifyWebApp({ deviceSignedIn: { value: false } });
        }
      });
    }
    /**
     * @deprecated This was a port from the macOS implementation so the API may not be suitable for all
     * @returns {Promise<any>}
     */
    async getSelectedCredentials() {
      throw new Error("`getSelectedCredentials` not implemented");
    }
    isTestMode() {
      return this.globalConfig.isDDGTestMode;
    }
    /**
     * This indicates an item was selected on Desktop, and we should try to autofill
     *
     * Note: When we're in a top-frame scenario, like on like macOS & Windows in the webview,
     * this method gets overridden {@see WindowsOverlayDeviceInterface} {@see AppleOverlayDeviceInterface}
     *
     * @param {IdentityObject|CreditCardObject|CredentialsObject|{email:string, id: string}} data
     * @param {string} type
     */
    async selectedDetail(data, type) {
      const form = this.activeForm;
      if (!form) {
        return;
      }
      if (type === "email" && "email" in data) {
        form.autofillEmail(data.email);
      } else {
        form.autofillData(data, type);
      }
      const isPrivateAddress = data.id === "privateAddress";
      if (isPrivateAddress) {
        this.refreshAlias();
        if ("emailAddress" in data && data.emailAddress) {
          this.emailProtection.storeReceived(data.emailAddress);
          const formValues = {
            credentials: {
              username: data.emailAddress,
              autogenerated: true
            }
          };
          this.storeFormData(formValues, "emailProtection");
        }
      }
      await this.removeTooltip();
    }
    /**
     * Before the DataWebTooltip opens, we collect the data based on the config.type
     * @param {InputTypeConfigs} config
     * @param {import('../Form/matching').SupportedTypes} inputType
     * @param {TopContextData} [data]
     * @returns {(CredentialsObject|CreditCardObject|IdentityObject)[]}
     */
    dataForAutofill(config, inputType, data) {
      const subtype = getSubtypeFromType(inputType);
      if (config.type === "identities") {
        return this.getLocalIdentities().filter((identity) => !!identity[subtype]);
      }
      if (config.type === "creditCards") {
        return this.getLocalCreditCards();
      }
      if (config.type === "credentials") {
        if (data) {
          if (Array.isArray(data.credentials) && data.credentials.length > 0) {
            return data.credentials;
          } else {
            return this.getLocalCredentials().filter(
              (cred) => !!cred[subtype] || subtype === "password" || cred.id === PROVIDER_LOCKED
            );
          }
        }
      }
      return [];
    }
    /**
     * @param {object} params
     * @param {import("../Form/Form").Form} params.form
     * @param {HTMLInputElement} params.input
     * @param {{ x: number; y: number; } | null} params.click
     * @param {import('../deviceApiCalls/__generated__/validators-ts').GetAutofillDataRequest['trigger']} params.trigger
     * @param {import('../UI/controllers/UIController.js').AttachTooltipArgs["triggerMetaData"]} params.triggerMetaData
     */
    attachTooltip(params) {
      const { form, input, click, trigger } = params;
      if (document.visibilityState !== "visible" && trigger !== "postSignup") return;
      if (trigger === "autoprompt" && !this.globalConfig.isMobileApp) return;
      if (trigger === "autoprompt" && this.autopromptFired) return;
      form.activeInput = input;
      this.activeForm = form;
      const inputType = getInputType(input);
      const getPosition = () => {
        const alignLeft = this.globalConfig.isApp || this.globalConfig.isWindows;
        return alignLeft ? input.getBoundingClientRect() : getDaxBoundingBox(input);
      };
      if (this.globalConfig.isMobileApp && inputType === "identities.emailAddress") {
        this.getAlias().then((alias) => {
          if (alias) {
            form.autofillEmail(alias);
            this.emailProtection.storeReceived(alias);
          } else {
            form.activeInput?.focus();
          }
          this.updateForStateChange();
          this.onFinishedAutofill();
        });
        return;
      }
      const topContextData = {
        inputType,
        credentialsImport: this.credentialsImport.isAvailable() && (this.activeForm.isLogin || this.activeForm.isHybrid)
      };
      const processedTopContext = this.preAttachTooltip(topContextData, input, form);
      this.uiController?.attachTooltip({
        input,
        form,
        click,
        getPosition,
        topContextData: processedTopContext,
        device: this,
        trigger,
        triggerMetaData: params.triggerMetaData
      });
      if (trigger === "autoprompt") {
        this.autopromptFired = true;
      }
    }
    /**
     * @param {import('../UI/controllers/UIController.js').AttachKeyboardArgs} args
     */
    attachKeyboard(args) {
      this.uiController?.attachKeyboard(args);
    }
    /**
     * When an item was selected, we then call back to the device
     * to fetch the full suite of data needed to complete the autofill
     *
     * @param {import('../Form/matching').SupportedTypes} inputType
     * @param {(CreditCardObject|IdentityObject|CredentialsObject)[]} items
     * @param {CreditCardObject['id']|IdentityObject['id']|CredentialsObject['id']} id
     */
    onSelect(inputType, items, id) {
      id = String(id);
      const mainType = getMainTypeFromType(inputType);
      const subtype = getSubtypeFromType(inputType);
      if (id === PROVIDER_LOCKED) {
        return this.thirdPartyProvider?.askToUnlockProvider();
      }
      const matchingData = items.find((item) => String(item.id) === id);
      if (!matchingData) throw new Error("unreachable (fatal)");
      const dataPromise = (() => {
        switch (mainType) {
          case "creditCards":
            return this.getAutofillCreditCard(id);
          case "identities":
            return this.getAutofillIdentity(id);
          case "credentials": {
            if (AUTOGENERATED_KEY in matchingData) {
              const autogeneratedPayload = { ...matchingData, username: "" };
              return Promise.resolve({ success: autogeneratedPayload });
            }
            return this.getAutofillCredentials(id);
          }
          default:
            throw new Error("unreachable!");
        }
      })();
      dataPromise.then((response) => {
        if (response) {
          const data = response.success || response;
          if (mainType === "identities") {
            this.firePixel({ pixelName: "autofill_identity", params: { fieldType: subtype } });
            switch (id) {
              case "personalAddress":
                this.firePixel({ pixelName: "autofill_personal_address" });
                break;
              case "privateAddress":
                this.firePixel({ pixelName: "autofill_private_address" });
                break;
              default: {
                const checks = [
                  subtype === "emailAddress",
                  this.hasLocalAddresses,
                  data?.emailAddress === formatDuckAddress(__privateGet(this, _addresses).personalAddress)
                ];
                if (checks.every(Boolean)) {
                  this.firePixel({ pixelName: "autofill_personal_address" });
                }
                break;
              }
            }
          }
          return this.selectedDetail(data, mainType);
        } else {
          return Promise.reject(new Error("none-success response"));
        }
      }).catch((e) => {
        console.error(e);
        return this.removeTooltip();
      });
    }
    isTooltipActive() {
      return this.uiController?.isActive?.() ?? false;
    }
    removeTooltip() {
      return this.uiController?.removeTooltip?.("interface");
    }
    onFinishedAutofill() {
      this.activeForm?.activeInput?.dispatchEvent(new Event("mouseleave"));
    }
    async updateForStateChange() {
      this.activeForm?.removeAllDecorations();
      await this.refreshData();
      this.activeForm?.recategorizeAllInputs();
    }
    async refreshData() {
      await this.inContextSignup?.refreshData();
      await this.settings.populateData();
    }
    async setupSettingsPage({ shouldLog: shouldLog2 } = { shouldLog: false }) {
      if (!this.globalConfig.isDDGDomain) {
        return;
      }
      notifyWebApp({ isApp: this.globalConfig.isApp });
      if (this.isDeviceSignedIn()) {
        let userData;
        try {
          userData = await this.getUserData();
        } catch (e) {
        }
        let capabilities;
        try {
          capabilities = await this.getEmailProtectionCapabilities();
        } catch (e) {
        }
        if (this.globalConfig.isDDGDomain) {
          window.addEventListener("message", (e) => {
            if (e.data.removeUserData) {
              this.removeUserData();
            }
            if (e.data.closeEmailProtection) {
              this.closeEmailProtection();
            }
          });
        }
        const hasUserData = userData && !userData.error && Object.entries(userData).length > 0;
        notifyWebApp({
          deviceSignedIn: {
            value: true,
            shouldLog: shouldLog2,
            userData: hasUserData ? userData : void 0,
            capabilities
          }
        });
      } else {
        this.trySigningIn();
      }
    }
    async setupAutofill() {
    }
    /** @returns {Promise<EmailAddresses>} */
    async getAddresses() {
      throw new Error("unimplemented");
    }
    /** @returns {Promise<null|Record<any,any>>} */
    getUserData() {
      return Promise.resolve(null);
    }
    /** @returns {void} */
    removeUserData() {
    }
    /** @returns {void} */
    closeEmailProtection() {
    }
    /** @returns {Promise<null|Record<string,boolean>>} */
    getEmailProtectionCapabilities() {
      throw new Error("unimplemented");
    }
    refreshAlias() {
    }
    async trySigningIn() {
      if (this.globalConfig.isDDGDomain) {
        if (this.attempts < 10) {
          this.attempts++;
          const data = await sendAndWaitForAnswer(SIGN_IN_MSG, "addUserData");
          this.storeUserData(data);
          await this.setupAutofill();
          await this.settings.refresh();
          await this.setupSettingsPage({ shouldLog: true });
          await this.postInit();
        } else {
          console.warn("max attempts reached, bailing");
        }
      }
    }
    storeUserData(_data7) {
    }
    addDeviceListeners() {
    }
    /** @param {() => void} _fn */
    addLogoutListener(_fn) {
    }
    isDeviceSignedIn() {
      return false;
    }
    /**
     * @returns {Promise<string|undefined>}
     */
    async getAlias() {
      return void 0;
    }
    // PM endpoints
    getAccounts() {
    }
    /**
     * Gets credentials ready for autofill
     * @param {CredentialsObject['id']} id - the credential id
     * @returns {Promise<CredentialsObject|{success:CredentialsObject}>}
     */
    async getAutofillCredentials(id) {
      return this.deviceApi.request(new GetAutofillCredentialsCall({ id: String(id) }));
    }
    /** @returns {APIResponseSingle<CreditCardObject>} */
    async getAutofillCreditCard(_id) {
      throw new Error("getAutofillCreditCard unimplemented");
    }
    /** @returns {Promise<{success: IdentityObject|undefined}>} */
    async getAutofillIdentity(_id) {
      throw new Error("getAutofillIdentity unimplemented");
    }
    openManagePasswords() {
    }
    openManageCreditCards() {
    }
    openManageIdentities() {
    }
    /**
     * @param {StoreFormData} values
     * @param {StoreFormData['trigger']} trigger
     */
    storeFormData(values, trigger) {
      this.deviceApi.notify(new StoreFormDataCall({ ...values, trigger }));
    }
    /**
     * `preAttachTooltip` happens just before a tooltip is show - features may want to append some data
     * at this point.
     *
     * For example, if password generation is enabled, this will generate
     * a password and send it to the tooltip as though it were a stored credential.
     *
     * @param {TopContextData} topContextData
     * @param {HTMLInputElement} input
     * @param {import("../Form/Form").Form} form
     */
    preAttachTooltip(topContextData, input, form) {
      const checks = [topContextData.inputType === "credentials.password.new", this.settings.featureToggles.password_generation];
      if (checks.every(Boolean)) {
        const password = this.passwordGenerator.generate({
          input: input.getAttribute("passwordrules"),
          domain: window.location.hostname
        });
        const rawValues = form.getRawValues();
        const username = rawValues.credentials?.username || rawValues.identities?.emailAddress || "";
        topContextData.credentials = [fromPassword(password, username)];
      }
      return topContextData;
    }
    /**
     * `postAutofill` gives features an opportunity to perform an action directly
     * following an autofill.
     *
     * For example, if a generated password was used, we want to fire a save event.
     *
     * @param {IdentityObject|CreditCardObject|CredentialsObject} data
     * @param {SupportedMainTypes} dataType
     * @param {import("../Form/Form").Form} formObj
     */
    postAutofill(data, dataType, formObj) {
      if (AUTOGENERATED_KEY in data && "password" in data && // Don't send message on Android to avoid potential abuse. Data is saved on native confirmation instead.
      !this.globalConfig.isAndroid) {
        const formValues = formObj.getValuesReadyForStorage();
        if (formValues.credentials?.password === data.password) {
          const formData = appendGeneratedKey(formValues, { password: data.password });
          this.storeFormData(formData, "passwordGeneration");
        }
      }
      if (dataType === "credentials" && formObj.shouldAutoSubmit) {
        formObj.attemptSubmissionIfNeeded();
      }
    }
    /**
     * `postSubmit` gives features a one-time-only opportunity to perform an
     * action directly after a form submission was observed.
     *
     * Mostly this is about storing data from the form submission, but it can
     * also be used like in the case of Password generation, to append additional
     * data before it's sent to be saved.
     *
     * @param {DataStorageObject} values
     * @param {import("../Form/Form").Form} form
     */
    postSubmit(values, form) {
      if (!form.form) return;
      if (!form.hasValues(values)) return;
      const shouldTriggerPartialSave = Object.keys(values?.credentials || {}).length === 1 && Boolean(values?.credentials?.username) && this.settings.featureToggles.partial_form_saves;
      const checks = [
        form.shouldPromptToStoreData && !form.submitHandlerExecuted,
        this.passwordGenerator.generated,
        shouldTriggerPartialSave
      ];
      if (checks.some(Boolean)) {
        const formData = appendGeneratedKey(values, {
          password: this.passwordGenerator.password,
          username: this.emailProtection.lastGenerated
        });
        const trigger = shouldTriggerPartialSave ? "partialSave" : "formSubmission";
        this.storeFormData(formData, trigger);
      }
    }
    /**
     * Sends a pixel to be fired on the client side
     * @param {import('../deviceApiCalls/__generated__/validators-ts').SendJSPixelParams} pixelParams
     */
    firePixel(pixelParams) {
      this.deviceApi.notify(new SendJSPixelCall(pixelParams));
    }
    /**
     * This serves as a single place to create a default instance
     * of InterfacePrototype that can be useful in testing scenarios
     * @param {Partial<GlobalConfig>} [globalConfigOverrides]
     * @returns {InterfacePrototype}
     */
    static default(globalConfigOverrides) {
      const globalConfig = createGlobalConfig(globalConfigOverrides);
      const transport = createTransport(globalConfig);
      const deviceApi = new DeviceApi(transport);
      const settings = Settings.default(globalConfig, deviceApi);
      return new _InterfacePrototype(globalConfig, deviceApi, settings);
    }
  };
  _addresses = new WeakMap();
  _data6 = new WeakMap();
  var InterfacePrototype = _InterfacePrototype;
  var InterfacePrototype_default = InterfacePrototype;

  // src/InContextSignup.js
  var InContextSignup = class {
    /**
     * @param {import("./DeviceInterface/InterfacePrototype").default} device
     */
    constructor(device) {
      this.device = device;
    }
    async init() {
      await this.refreshData();
      this.addNativeAccessibleGlobalFunctions();
    }
    addNativeAccessibleGlobalFunctions() {
      if (!this.device.globalConfig.hasModernWebkitAPI) return;
      try {
        Object.defineProperty(window, "openAutofillAfterClosingEmailProtectionTab", {
          enumerable: false,
          configurable: false,
          writable: false,
          value: () => {
            this.openAutofillTooltip();
          }
        });
      } catch (e) {
      }
    }
    async refreshData() {
      const incontextSignupDismissedAt = await this.device.deviceApi.request(new GetIncontextSignupDismissedAtCall(null));
      this.permanentlyDismissedAt = incontextSignupDismissedAt.permanentlyDismissedAt;
      this.isInstalledRecently = incontextSignupDismissedAt.isInstalledRecently;
    }
    async openAutofillTooltip() {
      await this.device.refreshData();
      await this.device.uiController?.removeTooltip("stateChange");
      const activeInput = this.device.activeForm?.activeInput;
      activeInput?.blur();
      const selectActiveInput = () => {
        activeInput?.focus();
      };
      if (document.hasFocus()) {
        selectActiveInput();
      } else {
        document.addEventListener(
          "visibilitychange",
          () => {
            selectActiveInput();
          },
          { once: true }
        );
      }
    }
    isPermanentlyDismissed() {
      return Boolean(this.permanentlyDismissedAt);
    }
    isOnValidDomain() {
      return isValidTLD() && !isLocalNetwork();
    }
    isAllowedByDevice() {
      if (typeof this.isInstalledRecently === "boolean") {
        return this.isInstalledRecently;
      } else {
        return true;
      }
    }
    /**
     * @param {import('./Form/matching.js').SupportedSubTypes | "unknown"} [inputType]
     * @returns {boolean}
     */
    isAvailable(inputType = "emailAddress") {
      const isEmailInput = inputType === "emailAddress";
      const isEmailProtectionEnabled = !!this.device.settings?.featureToggles.emailProtection;
      const isIncontextSignupEnabled = !!this.device.settings?.featureToggles.emailProtection_incontext_signup;
      const isNotAlreadyLoggedIn = !this.device.isDeviceSignedIn();
      const isNotDismissed = !this.isPermanentlyDismissed();
      const isOnExpectedPage = this.device.globalConfig.isTopFrame || this.isOnValidDomain();
      const isAllowedByDevice = this.isAllowedByDevice();
      return isEmailInput && isEmailProtectionEnabled && isIncontextSignupEnabled && isNotAlreadyLoggedIn && isNotDismissed && isOnExpectedPage && isAllowedByDevice;
    }
    onIncontextSignup() {
      this.device.deviceApi.notify(new StartEmailProtectionSignupCall({}));
      this.device.firePixel({ pixelName: "incontext_primary_cta" });
    }
    onIncontextSignupDismissed(options = { shouldHideTooltip: true }) {
      if (options.shouldHideTooltip) {
        this.device.removeAutofillUIFromPage("Email Protection in-context signup dismissed.");
        this.device.deviceApi.notify(new CloseAutofillParentCall(null));
      }
      this.permanentlyDismissedAt = (/* @__PURE__ */ new Date()).getTime();
      this.device.deviceApi.notify(new SetIncontextSignupPermanentlyDismissedAtCall({ value: this.permanentlyDismissedAt }));
      this.device.firePixel({ pixelName: "incontext_dismiss_persisted" });
    }
    // In-context signup can be closed when displayed as a stand-alone tooltip, e.g. extension
    onIncontextSignupClosed() {
      this.device.activeForm?.dismissTooltip();
      this.device.firePixel({ pixelName: "incontext_close_x" });
    }
  };

  // src/DeviceInterface/AndroidInterface.js
  var AndroidInterface = class extends InterfacePrototype_default {
    constructor() {
      super(...arguments);
      __publicField(this, "inContextSignup", new InContextSignup(this));
    }
    /**
     * @returns {Promise<string|undefined>}
     */
    async getAlias() {
      const { alias } = await sendAndWaitForAnswer(async () => {
        if (this.inContextSignup.isAvailable()) {
          const { isSignedIn } = await this.deviceApi.request(new ShowInContextEmailProtectionSignupPromptCall(null));
          if (this.globalConfig.availableInputTypes) {
            this.globalConfig.availableInputTypes.email = isSignedIn;
          }
          this.updateForStateChange();
          this.onFinishedAutofill();
        }
        return window.EmailInterface.showTooltip();
      }, "getAliasResponse");
      return alias;
    }
    /**
     * @override
     */
    createUIController() {
      return new NativeUIController();
    }
    /**
     * @deprecated use `this.settings.availableInputTypes.email` in the future
     * @returns {boolean}
     */
    isDeviceSignedIn() {
      if (this.globalConfig.isDDGDomain) {
        return window.EmailInterface.isSignedIn() === "true";
      }
      if (typeof this.globalConfig.availableInputTypes?.email === "boolean") {
        return this.globalConfig.availableInputTypes.email;
      }
      return true;
    }
    async setupAutofill() {
      await this.inContextSignup.init();
    }
    /**
     * Used by the email web app
     * Settings page displays data of the logged in user data
     */
    getUserData() {
      let userData = null;
      try {
        userData = JSON.parse(window.EmailInterface.getUserData());
      } catch (e) {
        if (this.globalConfig.isDDGTestMode) {
          console.error(e);
        }
      }
      return Promise.resolve(userData);
    }
    /**
     * Used by the email web app
     * Device capabilities determine which functionality is available to the user
     */
    getEmailProtectionCapabilities() {
      let deviceCapabilities = null;
      try {
        deviceCapabilities = JSON.parse(window.EmailInterface.getDeviceCapabilities());
      } catch (e) {
        if (this.globalConfig.isDDGTestMode) {
          console.error(e);
        }
      }
      return Promise.resolve(deviceCapabilities);
    }
    storeUserData({ addUserData: { token, userName, cohort } }) {
      return window.EmailInterface.storeCredentials(token, userName, cohort);
    }
    /**
     * Used by the email web app
     * Provides functionality to log the user out
     */
    removeUserData() {
      try {
        return window.EmailInterface.removeCredentials();
      } catch (e) {
        if (this.globalConfig.isDDGTestMode) {
          console.error(e);
        }
      }
    }
    /**
     * Used by the email web app
     * Provides functionality to close the window after in-context sign-up or sign-in
     */
    closeEmailProtection() {
      this.deviceApi.request(new CloseEmailProtectionTabCall(null));
    }
    addLogoutListener(handler) {
      if (!this.globalConfig.isDDGDomain) return;
      window.addEventListener("message", (e) => {
        if (this.globalConfig.isDDGDomain && e.data.emailProtectionSignedOut) {
          handler();
        }
      });
    }
    /** Noop */
    firePixel(_pixelParam) {
    }
  };

  // src/UI/styles/autofill-tooltip-styles.css
  var autofill_tooltip_styles_default = '/* src/UI/styles/autofill-tooltip-styles.css */\n:root {\n  color-scheme: light dark;\n}\n:host {\n  --t-text-primary: #1C1F21;\n  --t-text-secondary: rgba(28, 31, 33, 0.72);\n  --t-text-primary-dark: rgba(255, 255, 255, .84);\n  --t-text-secondary-dark: rgba(255, 255, 255, .60);\n  --t-backdrop-mac: #F2F0F0;\n  --t-backdrop-mac-dark: #646264;\n  --t-backdrop-windows: #FFF;\n  --t-backdrop-windows-dark: #333;\n  --t-mac-interactive: #3969EF;\n  --t-mac-interactive-text: #FFF;\n  --t-windows-interactive: #f0f0f0;\n  --t-windows-interactive-dark: #3f3f3f;\n  --color-primary: var(--t-text-primary);\n  --color-secondary: var(--t-text-secondary);\n  --color-primary-dark: var(--t-text-primary-dark);\n  --color-secondary-dark: var(--t-text-secondary-dark);\n  --bg: var(--t-backdrop-mac);\n  --bg-dark: var(--t-backdrop-mac-dark);\n  --font-size-primary: 13px;\n  --font-size-secondary: 11px;\n  --font-weight: 500;\n  --padding: 6px;\n  --hr-margin: 5px 9px;\n  --border-radius: 4px;\n  --hover-color-primary: var(--t-mac-interactive-text);\n  --hover-color-secondary: var(--t-mac-interactive-text);\n  --hover-color-primary-dark: var(--t-mac-interactive-text);\n  --hover-color-secondary-dark: var(--t-mac-interactive-text);\n  --hover-bg: var(--t-mac-interactive);\n  --hover-bg-dark: var(--t-mac-interactive);\n  --hover-effect: invert(100%);\n  --hover-effect-dark: invert(100%);\n  --top-autofill-min-height: 100vh;\n}\n:host:has([data-platform=windows]) {\n  --bg: var(--t-backdrop-windows);\n  --bg-dark: var(--t-backdrop-windows-dark);\n  --font-size-primary: 14px;\n  --font-size-secondary: 12px;\n  --font-weight: 400;\n  --padding: 0px;\n  --hr-margin: 4px 0px;\n  --border-radius: 3px;\n  --hover-color-primary: var(--t-text-primary);\n  --hover-color-secondary: var(--t-text-secondary);\n  --hover-color-primary-dark: var(--t-text-primary-dark);\n  --hover-color-secondary-dark: var(--t-text-secondary-dark);\n  --hover-bg: var(--t-windows-interactive);\n  --hover-bg-dark: var(--t-windows-interactive-dark);\n  --hover-effect: none;\n  --hover-effect-dark: invert(100%);\n  --top-autofill-min-height: auto;\n}\n.wrapper *,\n.wrapper *::before,\n.wrapper *::after {\n  box-sizing: border-box;\n}\n.wrapper {\n  position: fixed;\n  top: 0;\n  left: 0;\n  z-index: 2147483647;\n  padding: 0;\n  font-family: system-ui;\n  -webkit-font-smoothing: antialiased;\n}\n.wrapper:not(.top-autofill) .tooltip {\n  position: absolute;\n  width: 300px;\n  max-width: calc(100vw - 25px);\n  transform: translate(-1000px, -1000px);\n  z-index: 2147483647;\n}\n.tooltip--data,\n#topAutofill {\n  background-color: var(--bg);\n}\n@media (prefers-color-scheme: dark) {\n  .tooltip--data,\n  #topAutofill {\n    background: var(--bg-dark);\n  }\n}\n.tooltip--data {\n  width: 315px;\n  max-height: 290px;\n  padding: var(--padding);\n  font-size: var(--font-size-primary);\n  line-height: 14px;\n  overflow-y: auto;\n}\n.top-autofill .tooltip--data {\n  min-height: var(--top-autofill-min-height);\n}\n.tooltip--data.tooltip--incontext-signup {\n  width: 360px;\n}\n.wrapper:not(.top-autofill) .tooltip--data {\n  top: 100%;\n  left: 100%;\n}\n.wrapper:not(.top-autofill) .tooltip--email {\n  top: calc(100% + 6px);\n  right: calc(100% - 48px);\n  padding: 8px;\n  border: 1px solid #D0D0D0;\n  border-radius: 10px;\n  background-color: #FFF;\n  font-size: 14px;\n  line-height: 1.3;\n  color: #333;\n  box-shadow: 0 10px 20px rgba(0, 0, 0, 0.15);\n}\n.tooltip--email__caret {\n  position: absolute;\n  transform: translate(-1000px, -1000px);\n  z-index: 2147483647;\n}\n.tooltip--email__caret::before,\n.tooltip--email__caret::after {\n  content: "";\n  display: block;\n  width: 0;\n  height: 0;\n  border-left: 10px solid transparent;\n  border-right: 10px solid transparent;\n  position: absolute;\n  border-bottom: 8px solid #D0D0D0;\n  right: -28px;\n}\n.tooltip--email__caret::before {\n  border-bottom-color: #D0D0D0;\n  top: -1px;\n}\n.tooltip--email__caret::after {\n  border-bottom-color: #FFF;\n  top: 0px;\n}\n.tooltip__button {\n  display: flex;\n  width: 100%;\n  padding: 8px 8px 8px 0px;\n  font-family: inherit;\n  color: inherit;\n  background: transparent;\n  border: none;\n  border-radius: 6px;\n  text-align: left;\n}\n.tooltip__button.currentFocus,\n.wrapper:not(.top-autofill) .tooltip__button:hover {\n  background-color: var(--hover-bg);\n  color: var(--hover-color-primary);\n}\n@media (prefers-color-scheme: dark) {\n  .tooltip__button.currentFocus,\n  .wrapper:not(.top-autofill) .tooltip__button:hover {\n    background-color: var(--hover-bg-dark);\n  }\n}\n.tooltip__button--data {\n  position: relative;\n  min-height: 48px;\n  flex-direction: row;\n  justify-content: flex-start;\n  font-size: inherit;\n  font-weight: var(--font-weight);\n  line-height: 16px;\n  text-align: left;\n  border-radius: var(--border-radius);\n}\n.tooltip--data__item-container {\n  max-height: 220px;\n  overflow: auto;\n}\n.tooltip__button--data:first-child {\n  margin-top: 0;\n}\n.tooltip__button--data:last-child {\n  margin-bottom: 0;\n}\n.tooltip__button--data::before {\n  content: "";\n  display: block;\n  flex-shrink: 0;\n  width: 32px;\n  height: 32px;\n  margin: 0 8px;\n  background-size: 20px 20px;\n  background-repeat: no-repeat;\n  background-position: center center;\n}\n.tooltip__button--data.currentFocus:not(.tooltip__button--data--bitwarden)::before,\n.wrapper:not(.top-autofill) .tooltip__button--data:not(.tooltip__button--data--bitwarden):hover::before {\n  filter: var(--hover-effect);\n}\n.tooltip__button--data.currentFocus.no-hover-effect::before,\n.wrapper:not(.top-autofill) .tooltip__button--data.no-hover-effect:hover::before,\n.tooltip__button--data.no-hover-effect:hover::before {\n  filter: none;\n}\n@media (prefers-color-scheme: dark) {\n  .tooltip__button--data:not(.tooltip__button--data--bitwarden)::before {\n    filter: var(--hover-effect-dark);\n    opacity: .9;\n  }\n  .tooltip__button--data.no-hover-effect::before,\n  .wrapper:not(.top-autofill) .tooltip__button--data.no-hover-effect::before {\n    filter: none;\n    opacity: 1;\n  }\n  .tooltip__button--data.currentFocus:not(.tooltip__button--data--bitwarden)::before,\n  .wrapper:not(.top-autofill) .tooltip__button--data:not(.tooltip__button--data--bitwarden):hover::before {\n    filter: var(--hover-effect-dark);\n  }\n  .tooltip__button--data.currentFocus.no-hover-effect::before,\n  .tooltip__button--data.no-hover-effect:hover::before,\n  .wrapper:not(.top-autofill) .tooltip__button--data.no-hover-effect:hover::before {\n    filter: none;\n  }\n}\n.tooltip__button__text-container {\n  margin: auto 0;\n  width: 100%;\n}\n.label {\n  display: block;\n  font-weight: 400;\n  letter-spacing: -0.25px;\n  color: var(--color-primary);\n  font-size: var(--font-size-primary);\n  line-height: 1;\n}\n.label + .label {\n  margin-top: 3px;\n}\n.label.label--medium {\n  font-weight: var(--font-weight);\n  letter-spacing: -0.25px;\n}\n.label.label--small {\n  font-size: var(--font-size-secondary);\n  font-weight: 400;\n  letter-spacing: 0.06px;\n  color: var(--color-secondary);\n}\n@media (prefers-color-scheme: dark) {\n  .tooltip--data .label {\n    color: var(--color-primary-dark);\n  }\n  .tooltip--data .label--medium {\n    color: var(--color-primary-dark);\n  }\n  .tooltip--data .label--small {\n    color: var(--color-secondary-dark);\n  }\n}\n.tooltip__button.currentFocus .label,\n.wrapper:not(.top-autofill) .tooltip__button:hover .label {\n  color: var(--hover-color-primary);\n  &.label--small {\n    color: var(--hover-color-secondary);\n  }\n}\n@media (prefers-color-scheme: dark) {\n  .tooltip__button.currentFocus .label,\n  .wrapper:not(.top-autofill) .tooltip__button:hover .label {\n    color: var(--hover-color-primary-dark);\n    &.label--small {\n      color: var(--hover-color-secondary-dark);\n    }\n  }\n}\n.tooltip__button--secondary {\n  font-size: 13px;\n  padding: 5px 9px;\n  border-radius: var(--border-radius);\n  margin: 0;\n}\n.tooltip__button--data--credentials::before,\n.tooltip__button--data--credentials__current::before {\n  background-size: 20px;\n  background-image: url(data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiB2aWV3Qm94PSIwIDAgMjQgMjQiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZmlsbD0iIzAwMCIgZmlsbC1vcGFjaXR5PSIuOSIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTUuNSA2YTIuNSAyLjUgMCAxIDEgMCA1IDIuNSAyLjUgMCAwIDEgMC01bTAgMS41YTEgMSAwIDEgMCAwIDIgMSAxIDAgMCAwIDAtMiIgY2xpcC1ydWxlPSJldmVub2RkIi8+CiAgPHBhdGggZmlsbD0iIzAwMCIgZmlsbC1vcGFjaXR5PSIuOSIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTQuOTk4IDJBNy4wMDUgNy4wMDUgMCAwIDEgMjIgOS4wMDdhNy4wMDQgNy4wMDQgMCAwIDEtOC43MDUgNi43OTdjLS4xNjMtLjA0MS0uMjg2LjAwOC0uMzQ1LjA2N2wtMi41NTcgMi41NTlhMiAyIDAgMCAxLTEuNDE1LjU4NmgtLjk4MnYuNzM0QTIuMjUgMi4yNSAwIDAgMSA1Ljc0NSAyMmgtLjk5M2EyLjc1IDIuNzUgMCAwIDEtMi43NS0yLjczNUwyIDE4Ljc3YTMuNzUgMy43NSAwIDAgMSAxLjA5OC0yLjY3bDUuMDQtNS4wNDNjLjA2LS4wNi4xMDctLjE4My4wNjYtLjM0NmE3IDcgMCAwIDEtLjIwOC0xLjcwNEE3LjAwNCA3LjAwNCAwIDAgMSAxNC45OTggMm0wIDEuNWE1LjUwNCA1LjUwNCAwIDAgMC01LjMzNyA2Ljg0OGMuMTQ3LjU4OS4wMjcgMS4yNzktLjQ2MiAxLjc2OGwtNS4wNCA1LjA0NGEyLjI1IDIuMjUgMCAwIDAtLjY1OSAxLjYwM2wuMDAzLjQ5NGExLjI1IDEuMjUgMCAwIDAgMS4yNSAxLjI0M2guOTkyYS43NS43NSAwIDAgMCAuNzUtLjc1di0uNzM0YTEuNSAxLjUgMCAwIDEgMS41LTEuNWguOTgzYS41LjUgMCAwIDAgLjM1My0uMTQ3bDIuNTU4LTIuNTU5Yy40OS0uNDkgMS4xOC0uNjA5IDEuNzctLjQ2MWE1LjUwNCA1LjUwNCAwIDAgMCA2Ljg0LTUuMzQyQTUuNTA1IDUuNTA1IDAgMCAwIDE1IDMuNVoiIGNsaXAtcnVsZT0iZXZlbm9kZCIvPgo8L3N2Zz4K);\n}\n.tooltip__button--data--credentials__new::before {\n  background-size: 20px;\n  background-image: url(data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiB2aWV3Qm94PSIwIDAgMjQgMjQiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZmlsbD0iIzAwMCIgZD0iTTExLjIyNCA0LjY0YS45LjkgMCAwIDAgLjY0Ny0uMTY1IDUuNDcgNS40NyAwIDAgMSAzLjEyNy0uOTc1QTUuNTA0IDUuNTA0IDAgMCAxIDIwLjUgOS4wMDZhNS41MDQgNS41MDQgMCAwIDEtNi44NCA1LjM0M2MtLjU5LS4xNDgtMS4yODEtLjAyOC0xLjc3MS40NjJsLTIuNTU3IDIuNTU4YS41LjUgMCAwIDEtLjM1NC4xNDdoLS45ODJhMS41IDEuNSAwIDAgMC0xLjUgMS41di43MzRhLjc1Ljc1IDAgMCAxLS43NS43NWgtLjk5M2ExLjI1IDEuMjUgMCAwIDEtMS4yNS0xLjI0NGwtLjAwMy0uNDk0YTIuMjUgMi4yNSAwIDAgMSAuNjU5LTEuNjAybDUuMDQtNS4wNDNjLjM0My0uMzQ0LjQ2MS0uNzExLjQ3OS0xLjA5NS4wMjctLjU4Mi0uNzM3LS44NDctMS4xNzktLjQ2N2wtLjA2Ni4wNTZhLjcuNyAwIDAgMC0uMTU4LjIzMi44LjggMCAwIDEtLjEzNy4yMTNMMy4wOTggMTYuMUEzLjc1IDMuNzUgMCAwIDAgMiAxOC43N2wuMDAzLjQ5NEEyLjc1IDIuNzUgMCAwIDAgNC43NTMgMjJoLjk5MmEyLjI1IDIuMjUgMCAwIDAgMi4yNS0yLjI1di0uNzM0aC45ODNhMiAyIDAgMCAwIDEuNDE1LS41ODZsMi41NTctMi41NTljLjA1OS0uMDU5LjE4Mi0uMTA4LjM0Ni0uMDY3QTcuMDA0IDcuMDA0IDAgMCAwIDIyIDkuMDA2IDcuMDA0IDcuMDA0IDAgMCAwIDEwLjgyNiAzLjM4Yy0uNTMzLjM5NS0uMjYgMS4xNjYuMzk3IDEuMjZaIi8+CiAgPHBhdGggZmlsbD0iIzAwMCIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTUuNSA2YTIuNSAyLjUgMCAxIDEgMCA1IDIuNSAyLjUgMCAwIDEgMC01bTAgMS41YTEgMSAwIDEgMCAwIDIgMSAxIDAgMCAwIDAtMiIgY2xpcC1ydWxlPSJldmVub2RkIi8+CiAgPHBhdGggZmlsbD0iIzAwMCIgZD0iTTcuMTI1IDIuODA0QzcgMi4xNiA2LjkxNSAyIDYuNSAyYy0uNDE0IDAtLjUuMTYtLjYyNS44MDQtLjA4LjQxMy0uMjEyIDEuODItLjI5NiAyLjc3NS0uOTU0LjA4NC0yLjM2Mi4yMTYtMi43NzUuMjk2QzIuMTYgNiAyIDYuMDg1IDIgNi41YzAgLjQxNC4xNjEuNS44MDQuNjI1LjQxMi4wOCAxLjgxOC4yMTIgMi43NzIuMjk2LjA4My45ODkuMjE4IDIuNDYxLjMgMi43NzUuMTI0LjQ4My4yMS44MDQuNjI0LjgwNHMuNS0uMTYuNjI1LS44MDRjLjA4LS40MTIuMjEyLTEuODE3LjI5Ni0yLjc3MS45OS0uMDg0IDIuNDYyLS4yMTkgMi43NzYtLjNDMTAuNjc5IDcgMTEgNi45MTUgMTEgNi41YzAtLjQxNC0uMTYtLjUtLjgwMy0uNjI1LS40MTMtLjA4LTEuODIxLS4yMTItMi43NzUtLjI5Ni0uMDg1LS45NTQtLjIxNi0yLjM2Mi0uMjk3LTIuNzc1bS00LjM0MiA4Ljc2MWEuNzgzLjc4MyAwIDEgMCAwLTEuNTY1Ljc4My43ODMgMCAwIDAgMCAxLjU2NSIvPgo8L3N2Zz4K);\n}\n.tooltip__button--data--creditCards::before,\n.tooltip__button--data--provider__generic::before {\n  background-image: url(data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHZpZXdCb3g9IjAgMCAzMiAzMiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTI2IDRDMjkuMzEzNyA0IDMyIDYuNjg2MjkgMzIgMTBMMzIgMjJDMzIgMjUuMzEzNyAyOS4zMTM3IDI4IDI2IDI4TDYgMjhDMi42ODYyOSAyOCA5Ljc1Njk3ZS0wNyAyNS4zMTM3IDEuMTIwNTRlLTA2IDIyTDEuNjQ1MDhlLTA2IDEwQzEuNzg5OTNlLTA2IDYuNjg2MjkgMi42ODYyOSA0IDYgNEwyNiA0WiIgZmlsbD0id2hpdGUiLz4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0zMCAyMkwzMCAxMEMzMCA3Ljc5MDg2IDI4LjIwOTEgNiAyNiA2TDYgNkMzLjc5MDg2IDYgMiA3Ljc5MDg2IDIgMTBMMiAyMkMyIDI0LjIwOTEgMy43OTA4NiAyNiA2IDI2TDI2IDI2QzI4LjIwOTEgMjYgMzAgMjQuMjA5MSAzMCAyMlpNMzIgMTBDMzIgNi42ODYyOSAyOS4zMTM3IDQgMjYgNEw2IDRDMi42ODYyOSA0IDEuNzg5OTNlLTA2IDYuNjg2MjkgMS42NDUwOGUtMDYgMTBMMS4xMjA1NGUtMDYgMjJDOS43NTY5N2UtMDcgMjUuMzEzNyAyLjY4NjI5IDI4IDYgMjhMMjYgMjhDMjkuMzEzNyAyOCAzMiAyNS4zMTM3IDMyIDIyTDMyIDEwWiIgZmlsbD0iI0NDQ0NDQyIvPgo8cGF0aCBkPSJNNCAxM0M0IDEyLjQ0NzcgNC40NDc3MiAxMiA1IDEySDlDOS41NTIyOCAxMiAxMCAxMi40NDc3IDEwIDEzVjE1QzEwIDE1LjU1MjMgOS41NTIyOCAxNiA5IDE2SDVDNC40NDc3MiAxNiA0IDE1LjU1MjMgNCAxNVYxM1oiIGZpbGw9IiNGRkQ2NUMiLz4KPHBhdGggZD0iTTQgMjBDNCAxOS40NDc3IDQuNDQ3NzIgMTkgNSAxOUgxMEMxMC41NTIzIDE5IDExIDE5LjQ0NzcgMTEgMjBDMTEgMjAuNTUyMyAxMC41NTIzIDIxIDEwIDIxSDVDNC40NDc3MiAyMSA0IDIwLjU1MjMgNCAyMFoiIGZpbGw9IiNBQUFBQUEiLz4KPHBhdGggZD0iTTEyIDIwQzEyIDE5LjQ0NzcgMTIuNDQ3NyAxOSAxMyAxOUgxNEMxNC41NTIzIDE5IDE1IDE5LjQ0NzcgMTUgMjBDMTUgMjAuNTUyMyAxNC41NTIzIDIxIDE0IDIxSDEzQzEyLjQ0NzcgMjEgMTIgMjAuNTUyMyAxMiAyMFoiIGZpbGw9IiNBQUFBQUEiLz4KPHBhdGggZD0iTTE2IDIwQzE2IDE5LjQ0NzcgMTYuNDQ3NyAxOSAxNyAxOUgyN0MyNy41NTIzIDE5IDI4IDE5LjQ0NzcgMjggMjBDMjggMjAuNTUyMyAyNy41NTIzIDIxIDI3IDIxSDE3QzE2LjQ0NzcgMjEgMTYgMjAuNTUyMyAxNiAyMFoiIGZpbGw9IiNBQUFBQUEiLz4KPC9zdmc+Cg==);\n}\n.tooltip__button--data--provider__dinersClub::before {\n  background-image: url(data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAATDUlEQVR4AezBgQAAAACAoP2pF6kCAAAAAAAAAGbnGoAkadJo3cX5x9m2bdu2Hfht+1/btr1j2/Y0xrZn2vwuX8Wts9DbmK6NfBHfYiobke9VfqzhQWBwcPATfX192zo7O5usVutUS0uLt7GxMVhfX091dXVhmDDsIfYSe4q9xR6zvd6KPWcmhWvhvPj9vb29500mk10QtThmNpvtPT09Z8FFzAQwMDDweabCxoaGBkFCnBi4YJw0jIyMfJaZFIqFsvgV7I5PYB8WjN/NEELo6uoqmp6evpWZpMf0LMKd/6PW1lan2GTDuAYnO6l/wEzSMs0FLOBYK+564xkCx/7+/lXMJDVTuwjy94lI3tgZBHPbJ5hJSqZ4gUWXx26MjRCGTIGZxDPuD3HsQz0hHjn4IJqcnCS73U4+n4+CwSCFBQHsIfYSe4q9xR5jr0M+CdiJvlKvC/h+KD7fZDLJXywQCJBAbIC9xp6zwDykmIBlcj9UTQMnJiZu1hvtI+UYGxtb5LtcnA7gAFzozQ6QzisJAMd/lp43gvKcTifFBwTgHpqbm/XGA2ncGABVJD1Kslgssk+KLwh4PB7c4XpO7uDQ0NCnmEkw+Q8Yyrs67vz4Jl+IQM9JgGphHTMJhj/kxo7W3Y/rxjj2hTtA1K/FJTi/0AyCCzinpRoEG8aAAHPnemKBMxdjAK2WLrsuon2DpYlwBVqtZGZyDPApLbUg5zQWBFhKr3kKjI6OflTCJI9GAcGwRR6Hx08JjUN039lG+tbGQnrb46l0830JJN1+5hp7yd3n6HUPJ9NnVuTSXw9W0baiTuqetBv5FNCqGKI6uAnRf5OGryCjoXV4jv5ztPYi2a98IJF+uLVEFsKqbAttKmiX/77/bBP9iP38Vew61vHs86vy6GBlL7l9xrsJuru7VQWAIRIJc2Y3yvE/vuCmfx+poRfecZZedOdZ+ufhGiruHCe/fIIFFQ3XSzon5Ne++K5zXCG866l0OlU3QEYCuFPjlnE/IWHYUG0R0gojINsyRm98NEUm6ze7y6ltbB7khmzt4/P02z0V9II7+CfCr3aV06TNbZiUUI1bcC81NTWpNX4MUfjZW9Yj3/G33p9IqS3DIDJsS2sdhuvgiuC9z2SQdXSB4h3gTo1bcC9pFQ3iPf07VNkr363veTqdTCNzIC9i1jG+QB96PpMrgtc/nIxYI+6bRVptYkkrVYhnFHVMyHf+m9jRPzjrkEmLtI3OO5E9cEXw5sdSaHjOSfEMDX6NK4A5p5fe8lgqveye81TVO6VKotvnD+t6Td8UvZx9Dk8E395YRItwSAoB3HW6QSZge3GnanS/Ib+N3vdMhirBH3wuk9bltqlmCztLOpVSRXatSwgglhiYcaBwI/tnnwJp03Y3fWN9IQhCcKgqgNc8lCSv++q6AhbhuxTF9JElWYrxgM3tEwKIFR5OaJY3PqFxkEvWgstLH1+ajTUhCQD2UUbynNPDXZfcPKR4CqzLbRcCiAX8gaDs+9//bAaiXC5Rf9hXCVKuSwAX6ghq7oInAJxGMYAQQG3fjLzhz6SauARlW0ZxPSwBwDJMI9y1z6WZFE8B88i8EEC0sSG/HZuNEi+XoO9vKY6IAL67qYi7tqxrQlEAWwo7hQCijf8erZULPza39xpypuxu9AEiIgB8xgQnIHR6fdzPgOG7RRlCAN/ZVIQYQKl8CyIiIgBYSssQd/3bn0jjrv/mhkIhgGjjsytzkY5xidld2hVRAexQqDF8Ylk2d/0nl+cIAUQZSO9gPGJQFIqoADA3wFuPoRHeemQIUYYQwJfX5qM3zyXmdF1/RAVwsrafux6VRaXhkShDCAA5OoY2vP5r6/ddEwsRFQC6gbyK4EvvPqc4KxBlCAE8kdwqb3bL8CyXzA8/nxURAXzg2UzuWvPInGIa+HSqSQgg2sgwjf4/5+7gErStqCMiAsD7hxJnwArax4UAog27x0c33ZuARg+PILgGZAlhCQBlXQ+/RYz2L5f817L38PgCQgCxwN8PVaNQo+gGMBl0y/0J1yMATBLjfZWOf8Ui0APnmihGEAJoGJiVBfDT7aWKpFb3TmHOPyQBYDy8tGtCcd3Pd5TxyMdQClrUQgCxxK9ZNoDNL+oYVySsb9pOP9hSrEsA39tcTD1TNsU1JZ3KPYAnWWAaYwgB9E875OP63U+lY4hDa6RL9Xp1r/p19Bjeq5D7I95wef1CAIuBk7UDMglfW1fAD9rCN0wcof/AJR9xhmlkXgyFxsN00O/3VqBTF1HyXez9/rivUtHv51rHaJEhBIChoAsDop9blUtDERoPH55z0BdW5ylWFnMsF8gXAogLrM5pwzMCeDwMXUEc3dd95O8p68L78MhHpTEeHwgRAgDKuiZlgi4Uc9DMcelzC3g2AM0kxclf9B+Q6+OR8ziFEADg9QdoKxvNeseTaRdye/lhz4357ZRlHqHGwRn23L9N/hv/x89/x66/+sEkReLxlHE7GkNxDyGAC2BHOSaERkDehUe7dBvSS/w+AQgJj5xfgBCAgQEiS5mLSGoepjP1g9fY2YZBNHNYS9kmj56HA4fDQUeOHCGbzUZ6gd/pk5OTc+MI4Mjhw3T06P/YOwcvS5okiv8bY9u2bdu2bds2F2Pbtu05XNvejY1fnIk5XTWvBr37vtP9Tt8P8/pVdlZW5s2IG5E1md//+N/mzZvtv0OHDtnOFVE4fvy47N2zR1Iq2C2N5xg6dKhMnDBB5syebYOtey0J+PGPfyxlSpeWDOnT225dX4N58+ZZ+fHjxycOAf7whz/IqJEj7cEmTJjA5oS2k+X+/fulY4cOUr1aNTl27JiE0b1bN2nerFnKcyn/+IcNds0aNeT06dM2y/2vYJ8/f17KlS0rkydNEvCDH/zgmwhAHUUKF04sAoBTp05ZRyxfvlzCOHDggGTJkkWmTJ4sSfEvXc79h3Z2SgLt6dSpk9SvVy/SrENwiA2OHT36TQQAlSpWTDgCMDMiCQBWrlxp17ds2SIpGYsWLZLMmTLJq1ev5HNYt3bttxAgjQB/+9vfpFDBglIgf/7ANrSY2yTwwyd8P1zMb6SZpp5YJtbr/Otf/yqOL9UHqC93rlzSrGlT+Vr8rwTACv72t79NfAKA/v36WZmzqoAp37p1a2mj/7mgmjplihQtUsT0w/Bhw6RE8eKSKWNG0xeOP/z+91Zu2rRpMmb0aGnfrh2izPbKO3jwoNSuVcvqHjJkiGTMkEGWL1smehqa3Xve3LkyYvhwadyokemWMC5evEj7qDtZBEDZt2zRwu7loD86d+4sDRo0MHI6AcaOHWtWxEVktapVRQ9oTGwCIKwos2H9evnVr34lFcqXtw4DDFLXLl3s+tIlS3xW2ADynUcTbdu0CQjKMWPG2IzFcsyaOZOyNgCo9QH9+9ugjBs3zup08DnWnog7d+70Z0i2BejXt6+J3qTwdkFSJ0CD+vXZvNFIceLECcmTOzfkR3ckLgFQzpSho0GP7t2dAIYjhw9zPelm1exvZ99dvnxZbt68af55+7ZtHnJiQbASbJPGqVmB+h1dlFhNGjf+2Lm/+93vYnb04Q/3n6kDllwCjB416hMCbNq0yQkQqQEIpSkDcROWAD7D3dT17NEjQICjR46ECcAOl/bdhQsXzBqULFFCdMvb8H/MJCcAs/6TgcUdFCxQgPZFzrKXL17w+7im75oA6BMiJSZJ4orA/PnySdUqVRisZBFgxowZ1BG1nV0kAQAWBH3A9bJlynCPmAKyYoUKNhB6kKZEwDVLsgnAPWJFAeQX5s+fn5gEwO9myZxZrl+7JiA5BNi6ZQuf2Rj5mwiArwUMAO6DMoR7IMoN0LYIQBS0RyQBqlSu/FkC4OshQDj/kD1bNrl9+3bqJIAPHoLL4Z2+ceNGZq6Lt2QTgG3Rs+rsJEnzm9/85mM4uGLFCkK4SAKQxmXQHMWKFZPVq1dLFObOmWP1TJo4EXEZM6fBtVgEmDp1qmTLmtVELvi9Ri3dunZ1Alg7sDB8lxS7d+0iWkidUcCOHTukRo0a9pA8HOzGX5P540EJv37OoIbWATCFxXUwyBTifwcNHGh1LNbZSYdiZj1yIHwjOcO9sCT58uY1X92oUSP2wZXXr19/zLMP0xASMnmo11eVOaHkrVu3mI2EZH4tErQPU16jenUTaHfv3iXbSYRB1GEz9UWSNqtFYU9+7mFCtXKlSjJwwACZPn26XaMM0QDRDFokR/bsJMU4zkX27dtHvWiTVEkA/CWzFnXNQCPKvrjzOLE7ZQj/MNGEfQw6ddApJGxQ9nzmO7/m/peFJtwO4glwzctiKWgDZtVN9n19Poh2586dbzoTAavCesDevXvlxo0bVrcjVpvBmzdvuBek9J9xW56g4rmwYF4v5E9bDk5DGgHSkOgEcNP/N8/Rh1QwriSewF3gp7/UDsx5FHBXmPLkAKHqLiQCuFDKpC4CEJ8jyiar6Dui4oswqLkKJYSWK/nevXubGFui4eDgQYNkoqpo4vKGDRva5y6qgDerQGvXrp39Ptk4Igry/fhS/54ws6lm9ZYsXiyAdQPSw+TU+Qy47zRV4/Xr1w+EVa1btpThKiYBISUizdvR6EM7UOLbNFRkubd0qVJy8oRtUml5+4Eq+NZrGnuQtn9g//74f7Zht/ZUUtGHXgBoG9reQiOcx48eiaN2zZqBdYZHeq1Zs2YmNi9dumQRx5rVqwlVUxcBPIOGAgajNBYuXrQomT8XaShowkDEWOCFEBQ7YGYi3GbPmiUNVaU7yJODBQsWSJ3atQUc2L+fSMBDLVLCRBSsLdjMBijqSUmyaqhvFoFYkUSsMXCOrhqpjBgxQgCDyiwnEmFgAMRspeRx8QghyPk3VZLzfISF3B8iMfj+zsDSpUsDgwPZy5Yt6/UAFoUgW2A9AgEZDwLE7cCIMAGIu1HbWAVmC/USm586eVJADAI4AgRYq7MOcxsmADOUbN4/tPP9rRwUNW/w1KlTh8gCc8uqYWBVjlCOPIJ3cAwCOAIEYHavXbNGkoIZyzNf1PzEKiXAlStXIAEz2tclLD/hmKuhMP1DSvrSxYsRBIjvgRFxODImmgA+68luLdTBO3v27FcToFTJkphjVsxYL3cC8D3xPOsBFoM7IACD+15nXRk127iL3ygJcAOOkfp7aIzGaq779O791QRgMLE2WAEQTlBhjSAAP3PoJlm+Xj168LMTgMGhfu5PKhy3FkGAuB4Z8++4HRoVRQDwAzXPdGC/fv2+2QJQn5PSLQCrgzly5CCT9wkBAD6XRAv+2gnAwPTo0QNdgRbhpQ/cx1cRwNcHPPPnYEaxCskh204AcO3aNeonseMEwDqwNM39TWMULVqUl1VCBIj/oVFxOzYuTIAhgwcH/NyyD2v6YQJ0UwLw4kaYAGTqHAwsGUAIgI8FczRVmydPHiMDINVMJzsuq3nOqSSZqeIUsMDiA/RT9fG4ge3btweWi8NEDGuAIjqz0ReOWdrOwfqcYOHChXSwOBDCkN4JgDVzwiFUcQOUAWN1TaFD+/biICtI+vj/fHooffiLuB0cCbPxyaR+jx49ih8m/RlwKZPUKrDQ4qAcyhufiXsADGinjh1Jo1pkMV0VMwqbLFrnTp2kmg7Izh07WFkkDYz6x59zDRMfCN8wzVNUA5w/dw5XwuvnTg5L7/K73M/aoYTj7WTKeuaOdxUgwOFDh7ACpju4D4MNSSHhX1RMonXaaF2kvxkEB2nexVqWdxxYu0AAu1vEovCcpJsRl/9l76y13QhiMKwu+BRhxjcIPFmwCpZhZoY6daB2ORuq0oU5+b9iLhm05rGPdM6nC8aVtDMaLWiPVgfYixUCwTC0xpFVVZ30Wo0PqXUs7zvqM4AJlGGcNTyWz3dsi++81rFHaR+/eeqaR4fgs3rNo6WMVuIdnkhSMznt40PwFT7z2sd/EmYozfO3vGjhyN5kSAgFNM+fKaUbwgylaWC1N1/wePF9hEOodrIcdX2Jz4UZCsgIvajhClbKnCFlCskoPnL8SEn6hTBAganevTOPAs7cEUFQoOATfOP4j+XzX+3wW4QBagatG5/yJA+OWDHUhJTTJr7Gnp/rOg+FZVAZRoGliqKvPNGDeUbPZ70ZHhhftk/C5835GcrfX/CxsAxqHioO7GWY4AU1IPKodo0wEEKwNev8hUs9b+h///79XmFzQS2EIDjsR1VzZknpkWBgSMpX9PYvsZdjS2yKbbFxztXqgi+rqtovbCGodlzixZNPkFK6IKwVqHYQBKedkaBgAvmOpO+ysHagOsF0cMjJCQokwGd52O8EyoXkIa8Oyicg23/37t1uYR6oWqSUFou7xY4GAckhVb5nShaXC6sDqhsIhm36kFc5Ey2GcPzLORW+2qB6ZbVyhJscVgwnjAdsz1E9nVi6UlgvoPpG1agNShaPcUCp0Wh8UIHiB2cb97+CCLAhtsSm2BYbY2tsLqxfUNPF/3bpUAAAAAAAkL91Io0MaoAGaIAGaIAGaIAGaIAGaIAGaIAGaIAA4s9Fnf1gAtkAAAAASUVORK5CYII=);\n}\n.tooltip__button--data--provider__discover::before {\n  background-image: url(data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjMyIiB2aWV3Qm94PSIwIDAgMzIgMzIiIHdpZHRoPSIzMiIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+PGxpbmVhckdyYWRpZW50IGlkPSJhIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgeDE9IjE1LjU4MzkiIHgyPSIxNy45NjQ4IiB5MT0iMTQuNDU5NCIgeTI9IjE3LjMzNDQiPjxzdG9wIG9mZnNldD0iMCIgc3RvcC1jb2xvcj0iI2U2NzcyZiIvPjxzdG9wIG9mZnNldD0iMSIgc3RvcC1jb2xvcj0iI2VhOWQyYyIvPjwvbGluZWFyR3JhZGllbnQ+PHJhZGlhbEdyYWRpZW50IGlkPSJiIiBjeD0iMCIgY3k9IjAiIGdyYWRpZW50VHJhbnNmb3JtPSJtYXRyaXgoLTEuNTQ5ODIxMzUgLTEuOTc2NTYwNDMgMi4wNjU0OTE5NiAtMS42MTk1NTI1NyAxNy4xNzg3IDE2LjcwNTUpIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgcj0iMSI+PHN0b3Agb2Zmc2V0PSIwIiBzdG9wLWNvbG9yPSIjZWE5ZDJjIiBzdG9wLW9wYWNpdHk9IjAiLz48c3RvcCBvZmZzZXQ9Ii4zMjgxMjUiIHN0b3AtY29sb3I9IiNkZjc2MjQiIHN0b3Atb3BhY2l0eT0iMCIvPjxzdG9wIG9mZnNldD0iLjc2MDE4OCIgc3RvcC1jb2xvcj0iI2JmNGIyMyIgc3RvcC1vcGFjaXR5PSIuNzUiLz48c3RvcCBvZmZzZXQ9IjEiIHN0b3AtY29sb3I9IiM3ZDMwMTciLz48L3JhZGlhbEdyYWRpZW50PjxwYXRoIGQ9Im0yNiA0YzMuMzEzNyAwIDYgMi42ODYyOSA2IDZ2MTJjMCAzLjMxMzctMi42ODYzIDYtNiA2aC0yMGMtMy4zMTM3MSAwLTUuOTk5OTk5MDItMi42ODYzLTUuOTk5OTk4ODgtNmwuMDAwMDAwNTMtMTJjLjAwMDAwMDE0LTMuMzEzNzEgMi42ODYyODgzNS02IDUuOTk5OTk4MzUtNnoiIGZpbGw9IiNmZmYiLz48ZyBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGZpbGwtcnVsZT0iZXZlbm9kZCI+PHBhdGggZD0ibTMwIDIydi0xMmMwLTIuMjA5MTQtMS43OTA5LTQtNC00aC0yMGMtMi4yMDkxNCAwLTQgMS43OTA4Ni00IDR2MTJjMCAyLjIwOTEgMS43OTA4NiA0IDQgNGgyMGMyLjIwOTEgMCA0LTEuNzkwOSA0LTR6bTItMTJjMC0zLjMxMzcxLTIuNjg2My02LTYtNmgtMjBjLTMuMzEzNzEgMC01Ljk5OTk5ODIxIDIuNjg2MjktNS45OTk5OTgzNSA2bC0uMDAwMDAwNTMgMTJjLS4wMDAwMDAxNCAzLjMxMzcgMi42ODYyODg4OCA2IDUuOTk5OTk4ODggNmgyMGMzLjMxMzcgMCA2LTIuNjg2MyA2LTZ6IiBmaWxsPSIjY2NjIi8+PHBhdGggZD0ibTYuNDg4NjMgMTYuODE2MWMtLjIyNzU0LjE5NDYtLjUxNzE5LjI3OTYtLjk4MDM5LjI3OTZoLS4xOTIydi0yLjMyMTdoLjE5MjJjLjQ2MzIgMCAuNzQxNzQuMDc5NC45ODAzOS4yODM2LjI0NjM0LjIxMTEuMzk0MzguNTM2Ni4zOTQzOC44NzM0IDAgLjMzODQtLjE0ODA0LjY3NTYtLjM5NDM4Ljg4NTF6bS0uODM2OTEtMi42MzY4aC0xLjA1MTYydjMuNTExNGgxLjA0NTIxYy41NTYyMyAwIC45NTc3My0uMTI2NyAxLjMwOTY1LS40MDYxLjQxODAzLS4zMzExLjY2NjA4LS44MzA5LjY2NjA4LTEuMzQ4NyAwLTEuMDM2Ni0uODA5MjctMS43NTY2LTEuOTY5MzItMS43NTY2eiIgZmlsbD0iIzIwMWQxYyIvPjxwYXRoIGQ9Im03Ljk1MTE3IDE0LjE3OTNoLjcxMzF2My41MTE0aC0uNzEzMXoiIGZpbGw9IiMyMDFkMWMiLz48cGF0aCBkPSJtMTAuNDE1OSAxNS41MjUxYy0uNDMwNDYtLjE1MS0uNTU2MjctLjI1MTctLjU1NjI3LS40NDE0IDAtLjIyMDYuMjI0ODctLjM4NzkuNTMzOTctLjM4NzkuMjE0MyAwIC4zOTA5LjA4MjEuNTc4OS4yODI2bC4zNzIzLS40Njc0Yy0uMzA3Ni0uMjU5LS42NzU4LS4zOTAyLTEuMDc3MS0uMzkwMi0uNjQ5NDIgMC0xLjE0NDM5LjQzMjQtMS4xNDQzOSAxLjAwNCAwIC40ODU0LjIzMDEuNzMxNi45MDE1OS45NjU0LjI4MDcuMDkzNC40MjMzLjE1NzIuNDk1NS4yMDA1LjE0MzEuMDg4NC4yMTQzLjIxNTIuMjE0My4zNjE4IDAgLjI4NTYtLjIzNjUuNDk1Ni0uNTU2Mi40OTU2LS4zNDAzOCAwLS42MTU3OS0uMTYzMS0uNzgwNjMtLjQ2OTNsLS40NjEzNS40Mjc2Yy4zMjk4NC40NjI0LjcyNTIxLjY2OTEgMS4yNjk4OC42NjkxLjc0MjkgMCAxLjI2NjQtLjQ3NTkgMS4yNjY0LTEuMTUzOCAwLS41NTg2LS4yNDI4LS44MTE4LTEuMDU2OS0xLjA5NjZ6IiBmaWxsPSIjMjAxZDFjIi8+PHBhdGggZD0ibTExLjY5NzMgMTUuOTM1OWMwIDEuMDMyNi44NDcgMS44MzI4IDEuOTM3NCAxLjgzMjguMzA3NCAwIC41NzEtLjA1ODEuODk1Ni0uMjA0N3YtLjgwNjljLS4yODY4LjI3NTgtLjUzODYuMzg0Ni0uODY0My4zODQ2LS43MjA1IDAtMS4yMzA4LS40OTg5LTEuMjMwOC0xLjIxMDcgMC0uNjcyNi41MjY4LTEuMjA1NCAxLjE5OTUtMS4yMDU0LjMzOTQgMCAuNTk4Ny4xMTUxLjg5NTYuMzk1di0uODA2Yy0uMzEzNi0uMTUxOC0uNTcwOS0uMjE0Ni0uODgxLS4yMTQ2LTEuMDg0MSAwLTEuOTUyLjgxNTgtMS45NTIgMS44MzU5eiIgZmlsbD0iIzIwMWQxYyIvPjxwYXRoIGQ9Im0yMC4zMjgxIDE2LjUzNjctLjk3ODYtMi4zNTc0aC0uNzgwMmwxLjU1NTYgMy42aC4zODM3bDEuNTg1OC0zLjZoLS43NzQxeiIgZmlsbD0iIzIwMWQxYyIvPjxwYXRoIGQ9Im0yMi40MTg5IDE3LjY5MDdoMi4wMjkzdi0uNTk1aC0xLjMxMzN2LS45NDc4aDEuMjYzNXYtLjU5NDhoLTEuMjYzNXYtLjc3OTFoMS4zMTMzdi0uNTk0N2gtMi4wMjkzeiIgZmlsbD0iIzIwMWQxYyIvPjxwYXRoIGQ9Im0yNS44NDU3IDE1Ljc5NGgtLjIwNzh2LTEuMDYyMWguMjE5NGMuNDQ3MSAwIC42ODY2LjE3OTYuNjg2Ni41MjE3IDAgLjM1MTQtLjIzOTUuNTQwNC0uNjk4Mi41NDA0em0xLjQzNTEtLjU3ODZjMC0uNjU3Ni0uNDcxMy0xLjAzNjEtMS4yOTc1LTEuMDM2MWgtMS4wNjI5djMuNTExNGguNzE3NXYtMS40MTE4aC4wOTMxbC45ODg2IDEuNDExOGguODgwNGwtMS4xNTQ1LTEuNDc5OWMuNTM5NS0uMTA1Ni44MzUzLS40NTgxLjgzNTMtLjk5NTR6IiBmaWxsPSIjMjAxZDFjIi8+PHBhdGggZD0ibTE4LjY3MDQgMTUuOTQ0MWMwIDEuMDEwOS0uODU2IDEuODMwMy0xLjkxMjkgMS44MzAzLTEuMDU2NiAwLTEuOTEyOC0uODE5NC0xLjkxMjgtMS44MzAzIDAtMS4wMTExLjg1NjItMS44MzA2IDEuOTEyOC0xLjgzMDYgMS4wNTY5IDAgMS45MTI5LjgxOTUgMS45MTI5IDEuODMwNnoiIGZpbGw9InVybCgjYSkiLz48cGF0aCBkPSJtMTguNjcwNCAxNS45NDQxYzAgMS4wMTA5LS44NTYgMS44MzAzLTEuOTEyOSAxLjgzMDMtMS4wNTY2IDAtMS45MTI4LS44MTk0LTEuOTEyOC0xLjgzMDMgMC0xLjAxMTEuODU2Mi0xLjgzMDYgMS45MTI4LTEuODMwNiAxLjA1NjkgMCAxLjkxMjkuODE5NSAxLjkxMjkgMS44MzA2eiIgZmlsbD0idXJsKCNiKSIvPjwvZz48L3N2Zz4=);\n}\n.tooltip__button--data--provider__jcb::before {\n  background-image: url(data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjMyIiB2aWV3Qm94PSIwIDAgMzIgMzIiIHdpZHRoPSIzMiIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+PGxpbmVhckdyYWRpZW50IGlkPSJhIj48c3RvcCBvZmZzZXQ9IjAiIHN0b3AtY29sb3I9IiMwMDc5NDAiLz48c3RvcCBvZmZzZXQ9Ii4yMjg1IiBzdG9wLWNvbG9yPSIjMDA4NzNmIi8+PHN0b3Agb2Zmc2V0PSIuNzQzMyIgc3RvcC1jb2xvcj0iIzQwYTczNyIvPjxzdG9wIG9mZnNldD0iMSIgc3RvcC1jb2xvcj0iIzVjYjUzMSIvPjwvbGluZWFyR3JhZGllbnQ+PGxpbmVhckdyYWRpZW50IGlkPSJiIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgeDE9IjE5LjA4MjEiIHgyPSIyNC4wMTQ4IiB4bGluazpocmVmPSIjYSIgeTE9IjE2LjY1MSIgeTI9IjE2LjY1MSIvPjxsaW5lYXJHcmFkaWVudCBpZD0iYyIgZ3JhZGllbnRVbml0cz0idXNlclNwYWNlT25Vc2UiIHgxPSIxOS4wODIyIiB4Mj0iMjQuMDE0OCIgeGxpbms6aHJlZj0iI2EiIHkxPSIxNS44ODIyIiB5Mj0iMTUuODgyMiIvPjxsaW5lYXJHcmFkaWVudCBpZD0iZCIgZ3JhZGllbnRVbml0cz0idXNlclNwYWNlT25Vc2UiIHgxPSIxOS4wODIiIHgyPSIyNC4wMTQ0IiB4bGluazpocmVmPSIjYSIgeTE9IjE1LjA0NTgiIHkyPSIxNS4wNDU4Ii8+PGxpbmVhckdyYWRpZW50IGlkPSJlIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSIgeDE9IjguMDQwMjIiIHgyPSIxMy4wNDg4IiB5MT0iMTUuODgyMiIgeTI9IjE1Ljg4MjIiPjxzdG9wIG9mZnNldD0iMCIgc3RvcC1jb2xvcj0iIzFmMjg2ZiIvPjxzdG9wIG9mZnNldD0iLjQ3NTEiIHN0b3AtY29sb3I9IiMwMDRlOTQiLz48c3RvcCBvZmZzZXQ9Ii44MjYxIiBzdG9wLWNvbG9yPSIjMDA2NmIxIi8+PHN0b3Agb2Zmc2V0PSIxIiBzdG9wLWNvbG9yPSIjMDA2ZmJjIi8+PC9saW5lYXJHcmFkaWVudD48bGluZWFyR3JhZGllbnQgaWQ9ImYiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMTMuNTM1IiB4Mj0iMTguMzk5MyIgeTE9IjE1Ljg4MjIiIHkyPSIxNS44ODIyIj48c3RvcCBvZmZzZXQ9IjAiIHN0b3AtY29sb3I9IiM2YzJjMmYiLz48c3RvcCBvZmZzZXQ9Ii4xNzM1IiBzdG9wLWNvbG9yPSIjODgyNzMwIi8+PHN0b3Agb2Zmc2V0PSIuNTczMSIgc3RvcC1jb2xvcj0iI2JlMTgzMyIvPjxzdG9wIG9mZnNldD0iLjg1ODUiIHN0b3AtY29sb3I9IiNkYzA0MzYiLz48c3RvcCBvZmZzZXQ9IjEiIHN0b3AtY29sb3I9IiNlNjAwMzkiLz48L2xpbmVhckdyYWRpZW50PjxjbGlwUGF0aCBpZD0iZyI+PHBhdGggZD0ibTcgOWgxOHYxMy44MjkzaC0xOHoiLz48L2NsaXBQYXRoPjxwYXRoIGQ9Im0yNiA0YzMuMzEzNyAwIDYgMi42ODYyOSA2IDZ2MTJjMCAzLjMxMzctMi42ODYzIDYtNiA2aC0yMGMtMy4zMTM3MSAwLTUuOTk5OTk5MDItMi42ODYzLTUuOTk5OTk4ODgtNmwuMDAwMDAwNTMtMTJjLjAwMDAwMDE0LTMuMzEzNzEgMi42ODYyODgzNS02IDUuOTk5OTk4MzUtNnoiIGZpbGw9IiMwMDhlZWQiLz48cGF0aCBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Im0zMCAyMnYtMTJjMC0yLjIwOTE0LTEuNzkwOS00LTQtNGgtMjBjLTIuMjA5MTQgMC00IDEuNzkwODYtNCA0djEyYzAgMi4yMDkxIDEuNzkwODYgNCA0IDRoMjBjMi4yMDkxIDAgNC0xLjc5MDkgNC00em0yLTEyYzAtMy4zMTM3MS0yLjY4NjMtNi02LTZoLTIwYy0zLjMxMzcxIDAtNS45OTk5OTgyMSAyLjY4NjI5LTUuOTk5OTk4MzUgNmwtLjAwMDAwMDUzIDEyYy0uMDAwMDAwMTQgMy4zMTM3IDIuNjg2Mjg4ODggNiA1Ljk5OTk5ODg4IDZoMjBjMy4zMTM3IDAgNi0yLjY4NjMgNi02eiIgZmlsbD0iIzAwMCIgZmlsbC1vcGFjaXR5PSIuMTgiIGZpbGwtcnVsZT0iZXZlbm9kZCIvPjxnIGNsaXAtcGF0aD0idXJsKCNnKSI+PHBhdGggZD0ibTI1IDIwLjAwODVjMCAxLjUzNjYtMS4yNTEyIDIuNzg3OC0yLjc4NzggMi43ODc4aC0xNS4xNjgyNXYtMTEuMDQxNWMwLTEuNTM2NSAxLjI1MTIxLTIuNzg3NzYgMi43ODc4LTIuNzg3NzZoMTUuMTY4MjV6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTIwLjA2MSAxNy4xNzY4aDEuMTUyNWMuMDMyOSAwIC4xMDk3LS4wMTEuMTQyNy0uMDExLjIxOTUtLjA0MzkuNDA2MS0uMjQxNS40MDYxLS41MTU5IDAtLjI2MzQtLjE4NjYtLjQ2MDktLjQwNjEtLjUxNTgtLjAzMy0uMDExLS4wOTg4LS4wMTEtLjE0MjctLjAxMWgtMS4xNTI1eiIgZmlsbD0idXJsKCNiKSIvPjxwYXRoIGQ9Im0yMS4wODE4IDkuODk5OTZjLTEuMDk3NiAwLTEuOTk3Ni44ODkwNC0xLjk5NzYgMS45OTc1NHYyLjA3NDRoMi44MjA3Yy4wNjU5IDAgLjE0MjcgMCAuMTk3Ni4wMTEuNjM2Ni4wMzI5IDEuMTA4NS4zNjIyIDEuMTA4NS45MzI5IDAgLjQ1LS4zMTgyLjgzNDItLjkxMDkuOTExdi4wMjE5Yy42NDc1LjA0MzkgMS4xNDE0LjQwNjEgMS4xNDE0Ljk2NTkgMCAuNjAzNy0uNTQ4Ny45OTg4LTEuMjczMS45OTg4aC0zLjA5NTJ2NC4wNjFoMi45MzA1YzEuMDk3NiAwIDEuOTk3Ni0uODg5MSAxLjk5NzYtMS45OTc2di05Ljk3Njg0eiIgZmlsbD0idXJsKCNjKSIvPjxwYXRoIGQ9Im0yMS42MTk2IDE1LjA0NzVjMC0uMjYzNC0uMTg2Ni0uNDM5LS40MDYxLS40NzE5LS4wMjIgMC0uMDc2OS0uMDExLS4xMDk4LS4wMTFoLTEuMDQyN3YuOTY1OGgxLjA0MjdjLjAzMjkgMCAuMDk4OCAwIC4xMDk4LS4wMTA5LjIxOTUtLjAzMy40MDYxLS4yMDg2LjQwNjEtLjQ3MnoiIGZpbGw9InVybCgjZCkiLz48cGF0aCBkPSJtMTAuMDQwMyA5Ljg5OTk2Yy0xLjA5NzU5IDAtMS45OTc1OS44ODkwNC0xLjk5NzU5IDEuOTk3NTR2NC45MjgxYy41NTk3Ni4yNzQ0IDEuMTQxNDcuNDUgMS43MjMxNy40NS42OTE0MiAwIDEuMDY0NjItLjQxNzEgMS4wNjQ2Mi0uOTg3OHYtMi4zMjY5aDEuNzEyMnYyLjMxNTljMCAuOS0uNTU5NyAxLjYzNTQtMi40NTg1IDEuNjM1NC0xLjE1MjQ2IDAtMi4wNTI0Ni0uMjUyNS0yLjA1MjQ2LS4yNTI1djQuMjAzN2gyLjkzMDQ2YzEuMDk3NiAwIDEuOTk3Ni0uODg5IDEuOTk3Ni0xLjk5NzZ2LTkuOTY1ODR6IiBmaWxsPSJ1cmwoI2UpIi8+PHBhdGggZD0ibTE1LjU2MSA5Ljg5OTk2Yy0xLjA5NzUgMC0xLjk5NzUuODg5MDQtMS45OTc1IDEuOTk3NTR2Mi42MTIyYy41MDQ5LS40MjggMS4zODI5LS43MDI0IDIuNzk4OC0uNjM2Ni43NTczLjAzMyAxLjU2OTUuMjQxNSAxLjU2OTUuMjQxNXYuODQ1MWMtLjQwNjEtLjIwODUtLjg4OTEtLjM5NTEtMS41MTQ3LS40MzktMS4wNzU2LS4wNzY4LTEuNzIzMS40NS0xLjcyMzEgMS4zNzE5IDAgLjkzMy42NDc1IDEuNDU5OCAxLjcyMzEgMS4zNzIuNjI1Ni0uMDQzOSAxLjEwODYtLjI0MTUgMS41MTQ3LS40Mzl2Ljg0NTFzLS44MDEzLjIwODUtMS41Njk1LjI0MTVjLTEuNDE1OS4wNjU4LTIuMjkzOS0uMjA4Ni0yLjc5ODgtLjYzNjZ2NC42MDk3aDIuOTMwNWMxLjA5NzUgMCAxLjk5NzUtLjg4OSAxLjk5NzUtMS45OTc1di05Ljk4Nzg0eiIgZmlsbD0idXJsKCNmKSIvPjwvZz48L3N2Zz4=);\n}\n.tooltip__button--data--provider__mastercard::before {\n  background-image: url(data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHZpZXdCb3g9IjAgMCAzMiAzMiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTI2IDRDMjkuMzEzNyA0IDMyIDYuNjg2MjkgMzIgMTBMMzIgMjJDMzIgMjUuMzEzNyAyOS4zMTM3IDI4IDI2IDI4TDYgMjhDMi42ODYyOSAyOCA5Ljc1Njk3ZS0wNyAyNS4zMTM3IDEuMTIwNTRlLTA2IDIyTDEuNjQ1MDhlLTA2IDEwQzEuNzg5OTNlLTA2IDYuNjg2MjkgMi42ODYyOSA0IDYgNEwyNiA0WiIgZmlsbD0id2hpdGUiLz4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0zMCAyMkwzMCAxMEMzMCA3Ljc5MDg2IDI4LjIwOTEgNiAyNiA2TDYgNkMzLjc5MDg2IDYgMiA3Ljc5MDg2IDIgMTBMMiAyMkMyIDI0LjIwOTEgMy43OTA4NiAyNiA2IDI2TDI2IDI2QzI4LjIwOTEgMjYgMzAgMjQuMjA5MSAzMCAyMlpNMzIgMTBDMzIgNi42ODYyOSAyOS4zMTM3IDQgMjYgNEw2IDRDMi42ODYyOSA0IDEuNzg5OTNlLTA2IDYuNjg2MjkgMS42NDUwOGUtMDYgMTBMMS4xMjA1NGUtMDYgMjJDOS43NTY5N2UtMDcgMjUuMzEzNyAyLjY4NjI5IDI4IDYgMjhMMjYgMjhDMjkuMzEzNyAyOCAzMiAyNS4zMTM3IDMyIDIyTDMyIDEwWiIgZmlsbD0iI0NDQ0NDQyIvPgo8cGF0aCBkPSJNMTguNDAzOCAyMC4xNjExSDEzLjU5MjhWMTEuNTY5SDE4LjQwMzhWMjAuMTYxMVoiIGZpbGw9IiNGRjVGMDAiLz4KPHBhdGggZD0iTTEzLjkwMTYgMTUuODY0NEMxMy45MDE2IDE0LjEyMTQgMTQuNzIyOCAxMi41Njg5IDE2LjAwMTcgMTEuNTY4M0MxNS4wNjY1IDEwLjgzNjcgMTMuODg2MyAxMC40IDEyLjYwMzYgMTAuNEM5LjU2Njk4IDEwLjQgNy4xMDU0NyAxMi44NDY0IDcuMTA1NDcgMTUuODY0NEM3LjEwNTQ3IDE4Ljg4MjMgOS41NjY5OCAyMS4zMjg3IDEyLjYwMzYgMjEuMzI4N0MxMy44ODYzIDIxLjMyODcgMTUuMDY2NSAyMC44OTIgMTYuMDAxNyAyMC4xNjA0QzE0LjcyMjggMTkuMTU5OSAxMy45MDE2IDE3LjYwNzMgMTMuOTAxNiAxNS44NjQ0WiIgZmlsbD0iI0VCMDAxQiIvPgo8cGF0aCBkPSJNMjQuODkzNiAxNS44NjQ0QzI0Ljg5MzYgMTguODgyMyAyMi40MzIxIDIxLjMyODcgMTkuMzk1NSAyMS4zMjg3QzE4LjExMjggMjEuMzI4NyAxNi45MzI2IDIwLjg5MiAxNS45OTcxIDIwLjE2MDRDMTcuMjc2MiAxOS4xNTk5IDE4LjA5NzQgMTcuNjA3MyAxOC4wOTc0IDE1Ljg2NDRDMTguMDk3NCAxNC4xMjE0IDE3LjI3NjIgMTIuNTY4OSAxNS45OTcxIDExLjU2ODNDMTYuOTMyNiAxMC44MzY3IDE4LjExMjggMTAuNCAxOS4zOTU1IDEwLjRDMjIuNDMyMSAxMC40IDI0Ljg5MzYgMTIuODQ2NCAyNC44OTM2IDE1Ljg2NDRaIiBmaWxsPSIjRjc5RTFCIi8+Cjwvc3ZnPgo=);\n}\n.tooltip__button--data--provider__unionPay::before {\n  background-image: url(data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjMyIiB2aWV3Qm94PSIwIDAgMzIgMzIiIHdpZHRoPSIzMiIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cGF0aCBkPSJtMjYgNGMzLjMxMzcgMCA2IDIuNjg2MjkgNiA2djEyYzAgMy4zMTM3LTIuNjg2MyA2LTYgNmgtMjBjLTMuMzEzNzEgMC01Ljk5OTk5OTAyLTIuNjg2My01Ljk5OTk5ODg4LTZsLjAwMDAwMDUzLTEyYy4wMDAwMDAxNC0zLjMxMzcxIDIuNjg2Mjg4MzUtNiA1Ljk5OTk5ODM1LTZ6IiBmaWxsPSIjZmZmIi8+PGcgY2xpcC1ydWxlPSJldmVub2RkIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiPjxwYXRoIGQ9Im0zMCAyMnYtMTJjMC0yLjIwOTE0LTEuNzkwOS00LTQtNGgtMjBjLTIuMjA5MTQgMC00IDEuNzkwODYtNCA0djEyYzAgMi4yMDkxIDEuNzkwODYgNCA0IDRoMjBjMi4yMDkxIDAgNC0xLjc5MDkgNC00em0yLTEyYzAtMy4zMTM3MS0yLjY4NjMtNi02LTZoLTIwYy0zLjMxMzcxIDAtNS45OTk5OTgyMSAyLjY4NjI5LTUuOTk5OTk4MzUgNmwtLjAwMDAwMDUzIDEyYy0uMDAwMDAwMTQgMy4zMTM3IDIuNjg2Mjg4ODggNiA1Ljk5OTk5ODg4IDZoMjBjMy4zMTM3IDAgNi0yLjY4NjMgNi02eiIgZmlsbD0iI2NjYyIvPjxwYXRoIGQ9Im0yNS4wMTM0IDkuNTAzNzItNC45MjUyLS4wMDEyOGMtLjAwMDYgMC0uMDAxMyAwLS4wMDEzIDAtLjAwMzggMC0uMDA3NS4wMDA2OC0uMDExMi4wMDA2OC0uNjc2Ni4wMjA5LTEuNTE5My41Njc5OC0xLjY3MjggMS4yNDI0OGwtMi4zMjkyIDEwLjM4OWMtLjE1MzQuNjgwOC4yNjQxIDEuMjM0OC45MzU4IDEuMjQ1aDUuMTc0MWMuNjYxNC0uMDMzIDEuMzA0Mi0uNTczNyAxLjQ1NTEtMS4yNDA2bDIuMzI5Mi0xMC4zODljLjE1NTktLjY4NzgtLjI3MTctMS4yNDYyOC0uOTU0NS0xLjI0NjI4eiIgZmlsbD0iIzAxNzk4YSIvPjxwYXRoIGQ9Im0xNi4wNzM4IDIxLjEzNDYgMi4zMjkxLTEwLjM4ODljLjE1MzUtLjY3NDYuOTk2Mi0xLjIyMTYyIDEuNjcyOC0xLjI0MjUybC0xLjk1ODItLjAwMTI4LTMuNTI4Mi0uMDAwNjhjLS42Nzg0LjAxMzk5LTEuNTMyMS41NjQ4OC0xLjY4NTUgMS4yNDQ0OGwtMi4zMjk5IDEwLjM4ODljLS4xNTQuNjgwOS4yNjQxIDEuMjM0OS45MzU0IDEuMjQ1MWg1LjUwMDJjLS42NzE3LS4wMTAyLTEuMDg5Mi0uNTY0Mi0uOTM1Ny0xLjI0NTF6IiBmaWxsPSIjMDI0MzgxIi8+PHBhdGggZD0ibTEwLjU3MzkgMjEuMTM0NiAyLjMyOTgtMTAuMzg5Yy4xNTM1LS42Nzk1IDEuMDA3MS0xLjIzMDQyIDEuNjg1Ni0xLjI0NDQxbC00LjUxOTctLjAwMTE5Yy0uNjgyMzMgMC0xLjU1NzA5LjU1NzItMS43MTMwMiAxLjI0NTZsLTIuMzI5ODQgMTAuMzg5Yy0uMDE0MTguMDYzNC0uMDIyMDUuMTI1NS0uMDI2NzQuMTg2M3YuMTkyOGMuMDQ1NjEuNDk2My40MjE2Mi44NTc3Ljk2MjExLjg2Nmg0LjU0NzE5Yy0uNjcxMy0uMDEwMi0xLjA4OTUtLjU2NDMtLjkzNTQtMS4yNDUxeiIgZmlsbD0iI2RkMDIyOCIvPjxwYXRoIGQ9Im0xNC42ODQ2IDE3LjA3MTNoLjA4NTVjLjA3ODYgMCAuMTMxNS0uMDI2Ni4xNTYzLS4wNzkybC4yMjIyLS4zMzU0aC41OTUybC0uMTI0MS4yMjA2aC43MTM3bC0uMDkwNS4zMzc5aC0uODQ5MmMtLjA5NzguMTQ4My0uMjE4Mi4yMTgxLS4zNjMuMjA5OGgtLjQ0MjN6bS0uMDk3OC40ODQzaDEuNTYzNmwtLjA5OTcuMzY3MWgtLjYyODhsLS4wOTU5LjM1NDNoLjYxMThsLS4wOTk2LjM2N2gtLjYxMTlsLS4xNDIxLjUyMzZjLS4wMzUyLjA4NzUuMDExLjEyNjkuMTM4LjExNzloLjQ5ODdsLS4wOTI0LjM0MTFoLS45NTc0Yy0uMTgxNSAwLS4yNDM3LS4xMDQ2LS4xODY4LS4zMTQ0bC4xODE3LS42NjgyaC0uMzkxMWwuMDk5NC0uMzY3aC4zOTExbC4wOTU5LS4zNTQzaC0uMzczOXptMi40OTU2LS45MDE0LS4wMjQ2LjIxNDlzLjI5NDktLjIyMzIuNTYyOC0uMjIzMmguOTg5OGwtLjM3ODUgMS4zODEzYy0uMDMxNC4xNTc5LS4xNjYuMjM2NC0uNDAzOC4yMzY0aC0xLjEyMTlsLS4yNjI3Ljk2OTljLS4wMTUyLjA1Mi4wMDYyLjA3ODYuMDYyOC4wNzg2aC4yMjA4bC0uMDgxMi4zMDExaC0uNTYxMmMtLjIxNTQgMC0uMzA1LS4wNjUzLS4yNjk1LS4xOTY0bC43NDI2LTIuNzYyNnptLjgzODIuMzkwNWgtLjg4MzVsLS4xMDU2LjM3MjdzLjE0NzEtLjEwNzEuMzkzLS4xMTA5Yy4yNDUyLS4wMDM5LjUyNSAwIC41MjUgMHptLS4zMjAxLjg2NDZjLjA2NTQuMDA4OS4xMDE5LS4wMTcxLjEwNjMtLjA3ODZsLjA1NDEtLjE5NjVoLS44ODQ4bC0uMDc0Mi4yNzUxem0tLjU5NjguNDQ2M2guNTEwMWwtLjAwOTUuMjIyNGguMTM1OGMuMDY4NiAwIC4xMDI2LS4wMjIxLjEwMjYtLjA2NThsLjA0MDItLjE0NGguNDIzOWwtLjA1NjYuMjA5OGMtLjA0NzkuMTc1LS4xNzQ4LjI2NjMtLjM4MTEuMjc1MmgtLjI3MTdsLS4wMDEyLjM4MDNjLS4wMDUuMDYwOS4wNDk2LjA5Mi4xNjIxLjA5MmguMjU1NGwtLjA4MjQuMzAxMWgtLjYxMjVjLS4xNzE3LjAwODItLjI1NTgtLjA3NDItLjI1NDEtLjI0OTJ6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTEwLjgyMzggMTQuODE0Yy0uMDY5Mi4zNDE3LS4yMjk2LjYwNDEtLjQ3ODMuNzkwNS0uMjQ2NS4xODMyLS41NjQ0LjI3NTEtLjk1MzYyLjI3NTEtLjM2NjI5IDAtLjYzNDgzLS4wOTM5LS44MDYxOS0uMjgyMS0uMTE4ODUtLjEzMzgtLjE3Nzk4LS4zMDM3LS4xNzc5OC0uNTA5MSAwLS4wODQ5LjAxMDA5LS4xNzYyLjAzMDIyLS4yNzQ0bC40MTQ3LTIuMDE1OGguNjI2MzNsLS40MDkwNiAxLjk5M2MtLjAxMjU5LjA1NTEtLjAxNzYxLjEwNjUtLjAxNjk1LjE1MjgtLjAwMDY2LjEwMi4wMjQ0OC4xODU3LjA3NTQyLjI1MS4wNzQyLjA5Ny4xOTQ2My4xNDUxLjM2MjIyLjE0NTEuMTkyNzIgMCAuMzUxNTItLjA0NzUuNDc0NzYtLjE0MzIuMTIzMjUtLjA5NTEuMjAzNzUtLjIzMDEuMjM5OTUtLjQwNTdsLjQxMDMtMS45OTNoLjYyMzJ6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTEzLjQ1NDEgMTQuMDIwOWguNDkwNWwtLjM4NDIgMS43OTloLS40ODk2em0uMTU0NC0uNjU1NGguNDk0OWwtLjA5MjUuNDM2N2gtLjQ5NDh6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTE0LjM3ODggMTUuNjgzYy0uMTI4My0uMTIzNi0uMTkzMS0uMjkwMy0uMTkzNy0uNTAyIDAtLjAzNjIuMDAyMS0uMDc3NC4wMDY5LS4xMjI0LjAwNDctLjA0NTcuMDEwNy0uMDkuMDE5Mi0uMTMxMi4wNTgxLS4yOTIyLjE4Mi0uNTI0Mi4zNzI5LS42OTU0LjE5MDUtLjE3MTguNDIwNC0uMjU4LjY4OTUtLjI1OC4yMjA0IDAgLjM5NTIuMDYyMS41MjMyLjE4NjQuMTI4LjEyNDkuMTkyMS4yOTM0LjE5MjEuNTA3OCAwIC4wMzY3LS4wMDI4LjA3OTItLjAwNzUuMTI0OC0uMDA1Ny4wNDYzLS4wMTI2LjA5MDctLjAyMDUuMTM0NC0uMDU2OS4yODc4LS4xODA0LjUxNzMtLjM3MTMuNjg1My0uMTkwOC4xNjkyLS40Mi4yNTM1LS42ODczLjI1MzUtLjIyMTQgMC0uMzk1Ni0uMDYwOS0uNTIzNS0uMTgzMnptLjkzNDctLjM1NjJjLjA4NjUtLjA5NDUuMTQ4NC0uMjM3OC4xODYyLS40Mjg1LjAwNTYtLjAyOTguMDEwNi0uMDYwOS4wMTM4LS4wOTIuMDAzMS0uMDMwNC4wMDQ0LS4wNTg5LjAwNDQtLjA4NDkgMC0uMTExLS4wMjgtLjE5NzEtLjA4NDMtLjI1OC0uMDU1OS0uMDYxNS0uMTM1NS0uMDkxOS0uMjM4My0uMDkxOS0uMTM1OCAwLS4yNDY1LjA0ODEtLjMzMzIuMTQ0NS0uMDg3NS4wOTY0LS4xNDk0LjI0MjItLjE4ODQuNDM2MS0uMDA1NC4wMjk4LS4wMDk4LjA1OTYtLjAxMzguMDg4OC0uMDAzMi4wMjk4LS4wMDQxLjA1NzctLjAwMzUuMDgzIDAgLjExMDMuMDI4LjE5NTMuMDg0My4yNTU1LjA1NTkuMDYwMi4xMzUxLjA5LjIzOTIuMDkuMTM2NSAwIC4yNDcyLS4wNDc2LjMzMzYtLjE0MjZ6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTE5LjE3MTQgMTcuMDg2NS4xMTgzLS40MTk3aC41OThsLS4wMjU4LjE1NHMuMzA1Ni0uMTU0LjUyNTctLjE1NGguNzM5NGwtLjExNzUuNDE5N2gtLjExNjNsLS41NTc4IDEuOTc5aC4xMTYzbC0uMTEwNy4zOTNoLS4xMTYzbC0uMDQ4NC4xNzA2aC0uNTc5MWwuMDQ4My0uMTcwNmgtMS4xNDI2bC4xMTE0LS4zOTNoLjExNDRsLjU1ODQtMS45Nzl6bS42NDUyIDAtLjE1MjIuNTM1NnMuMjYwNC0uMTAwNy40ODQ5LS4xMjkzYy4wNDk2LS4xODcuMTE0NC0uNDA2My4xMTQ0LS40MDYzem0tLjIyMjcuNzg2Ni0uMTUyNy41NjFzLjI4ODYtLjE0MzIuNDg2Ny0uMTU1M2MuMDU3Mi0uMjE2OC4xMTQ1LS40MDU3LjExNDUtLjQwNTd6bS4xMTIgMS4xOTI0LjExNDUtLjQwNjloLS40NDY0bC0uMTE1MS40MDY5em0xLjQ0NjMtMi40MjQ2aC41NjIzbC4wMjM4LjIwOTFjLS4wMDM3LjA1MzIuMDI3Ny4wNzg2LjA5NDMuMDc4NmguMDk5NGwtLjEwMDUuMzU0NGgtLjQxMzNjLS4xNTc4LjAwODItLjIzODktLjA1MjYtLjI0NjQtLjE4Mzl6bS0uMTY0Ny43NmgxLjgyMTFsLS4xMDY5LjM4MDRoLS41Nzk4bC0uMDk5NC4zNTM2aC41NzkybC0uMTA3NS4zNzk3aC0uNjQ1MWwtLjE0Ni4yMjI2aC4zMTU4bC4wNzI5LjQ0NTZjLjAwODcuMDQ0NC4wNDc3LjA2NTkuMTE0NC4wNjU5aC4wOThsLS4xMDMuMzY3aC0uMzQ3MWMtLjE3OTkuMDA4OS0uMjcyOS0uMDUxOS0uMjgwNS0uMTgzMWwtLjA4MzctLjQwNy0uMjg3My40MzNjLS4wNjguMTIyMy0uMTcyNC4xNzk0LS4zMTMyLjE3MDVoLS41MzAxbC4xMDMxLS4zNjcxaC4xNjU0Yy4wNjggMCAuMTI0NS0uMDMwNC4xNzU0LS4wOTE5bC40NDk3LS42NTU1aC0uNTc5OGwuMTA3NS0uMzc5N2guNjI4OGwuMS0uMzUzNmgtLjYyOTR6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTExLjQwMDMgMTQuMDIwNGguNDQyNGwtLjA1MDYuMjU5OS4wNjM1LS4wNzQyYy4xNDM0LS4xNTQ2LjMxNzYtLjIzMTQuNTIzMi0uMjMxNC4xODYxIDAgLjMyMDQuMDU0Ni40MDQ3LjE2NDIuMDgyOS4xMDk3LjEwNTYuMjYxMi4wNjYzLjQ1NThsLS4yNDM3IDEuMjI2aC0uNDU0NmwuMjIwMS0xLjExMTNjLjAyMjYtLjExNDcuMDE2NC0uMjAwMy0uMDE4Ni0uMjU1NC0uMDM0Ni0uMDU1Mi0uMTAwNi0uMDgyNC0uMTk1OS0uMDgyNC0uMTE2OSAwLS4yMTU0LjAzNjctLjI5NTUuMTA5Ni0uMDgwNS4wNzM2LS4xMzM3LjE3NTYtLjE1OTguMzA1NmwtLjIwMjggMS4wMzM5aC0uNDU1NXoiIGZpbGw9IiNmZmYiLz48cGF0aCBkPSJtMTYuNDczNSAxNC4wMjA0aC40NDI3bC0uMDUwMi4yNTk5LjA2MjgtLjA3NDJjLjE0MzUtLjE1NDYuMzE4My0uMjMxNC41MjMzLS4yMzE0LjE4NjEgMCAuMzIwNi4wNTQ2LjQwNDIuMTY0Mi4wODI1LjEwOTcuMTA2NC4yNjEyLjA2NjEuNDU1OGwtLjI0MjcgMS4yMjZoLS40NTUzbC4yMjAxLTEuMTExM2MuMDIyNi0uMTE0Ny4wMTY0LS4yMDAzLS4wMTgyLS4yNTU0LS4wMzU5LS4wNTUyLS4xMDA3LS4wODI0LS4xOTU2LS4wODI0LS4xMTcgMC0uMjE1LjAzNjctLjI5NjIuMTA5Ni0uMDgwNS4wNzM2LS4xMzM5LjE3NTYtLjE1OS4zMDU2bC0uMjAzOCAxLjAzMzloLS40NTV6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTE4LjY2MjEgMTIuOTA0N2gxLjI4NTNjLjI0NzEgMCAuNDM4My4wNTY0LjU2OTcuMTY3My4xMzA4LjExMjIuMTk2My4yNzMzLjE5NjMuNDgzMXYuMDA2M2MwIC4wMzk5LS4wMDI3LjA4NDktLjAwNjQuMTMzNy0uMDA2My4wNDgyLS4wMTQ1LjA5Ny0uMDI1MS4xNDc3LS4wNTY2LjI3NzctLjE4ODEuNTAwOC0uMzkxMi42NzAxLS4yMDM4LjE2ODYtLjQ0NTIuMjUzNi0uNzIzMS4yNTM2aC0uNjg5M2wtLjIxMzEgMS4wNTQxaC0uNTk2OHptLjMyMTIgMS4zNTQ2aC41NzE3Yy4xNDkgMCAuMjY3Mi0uMDM0OS4zNTM0LS4xMDM5LjA4NTUtLjA2OTguMTQyMS0uMTc2Mi4xNzM1LS4zMjA4LjAwNS0uMDI2Ny4wMDgxLS4wNTA3LjAxMi0uMDcyOS4wMDE5LS4wMjA5LjAwNDQtLjA0MTkuMDA0NC0uMDYyMSAwLS4xMDMzLS4wMzY0LS4xNzgxLS4xMDk0LS4yMjUtLjA3My0uMDQ3Ni0uMTg3NC0uMDcwNC0uMzQ1OS0uMDcwNGgtLjQ4NTV6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTIzLjM4NDggMTYuMTY4N2MtLjE4ODcuNDA0NC0uMzY4NS42NDAyLS40NzQxLjc0OTgtLjEwNTcuMTA4NS0uMzE1LjM2MDgtLjgxOTQuMzQxN2wuMDQzNC0uMzA4NmMuNDI0NC0uMTMxOS42NTM5LS43MjU5Ljc4NDctLjk4OWwtLjE1NTktMS45MzcyLjMyODMtLjAwNDRoLjI3NTRsLjAyOTYgMS4yMTUyLjUxNjItMS4yMTUyaC41MjI2eiIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Im0yMS45MjMzIDE0LjE2NzMtLjIwNzYuMTQ0Yy0uMjE3LS4xNzEyLS40MTUtLjI3Ny0uNzk3My0uMDk4My0uNTIwNy4yNDM0LS45NTU5IDIuMTEwMy40Nzc5IDEuNDk1NGwuMDgxNy4wOTc2LjU2NDEuMDE0Ni4zNzA0LTEuNjk2NHptLS4zMjA4LjkyNzRjLS4wOTA2LjI2OTUtLjI5My40NDc2LS40NTE0LjM5NjktLjE1ODUtLjA0OTUtLjIxNTEtLjMwOTQtLjEyMzMtLjU3OTQuMDkwNS0uMjcwMS4yOTQzLS40NDc2LjQ1MTUtLjM5NjguMTU4NC4wNDk0LjIxNTYuMzA5My4xMjMyLjU3OTN6IiBmaWxsPSIjZmZmIi8+PC9nPjwvc3ZnPg==);\n}\n.tooltip__button--data--provider__visa::before {\n  background-image: url(data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHZpZXdCb3g9IjAgMCAzMiAzMiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTI2IDRDMjkuMzEzNyA0IDMyIDYuNjg2MjkgMzIgMTBMMzIgMjJDMzIgMjUuMzEzNyAyOS4zMTM3IDI4IDI2IDI4TDYgMjhDMi42ODYyOSAyOCA5Ljc1Njk3ZS0wNyAyNS4zMTM3IDEuMTIwNTRlLTA2IDIyTDEuNjQ1MDhlLTA2IDEwQzEuNzg5OTNlLTA2IDYuNjg2MjkgMi42ODYyOSA0IDYgNEwyNiA0WiIgZmlsbD0id2hpdGUiLz4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0zMCAyMkwzMCAxMEMzMCA3Ljc5MDg2IDI4LjIwOTEgNiAyNiA2TDYgNkMzLjc5MDg2IDYgMiA3Ljc5MDg2IDIgMTBMMiAyMkMyIDI0LjIwOTEgMy43OTA4NiAyNiA2IDI2TDI2IDI2QzI4LjIwOTEgMjYgMzAgMjQuMjA5MSAzMCAyMlpNMzIgMTBDMzIgNi42ODYyOSAyOS4zMTM3IDQgMjYgNEw2IDRDMi42ODYyOSA0IDEuNzg5OTNlLTA2IDYuNjg2MjkgMS42NDUwOGUtMDYgMTBMMS4xMjA1NGUtMDYgMjJDOS43NTY5N2UtMDcgMjUuMzEzNyAyLjY4NjI5IDI4IDYgMjhMMjYgMjhDMjkuMzEzNyAyOCAzMiAyNS4zMTM3IDMyIDIyTDMyIDEwWiIgZmlsbD0iI0NDQ0NDQyIvPgo8cGF0aCBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0iTTE4Ljg5NTYgMTIuNUMxOS41ODQ5IDEyLjUgMjAuMTQyNSAxMi42NDQ1IDIwLjQ5NCAxMi43NzU0TDIwLjI1MTYgMTQuMTcyNEwyMC4wOTA0IDE0LjEwMzdDMTkuNzY4IDEzLjk3MjkgMTkuMzQyMyAxMy44NDIyIDE4Ljc2MjkgMTMuODU2QzE4LjA1OTMgMTMuODU2IDE3Ljc0MzcgMTQuMTM4IDE3LjczNjQgMTQuNDEzM0MxNy43MzY0IDE0LjcxNjIgMTguMTE4MiAxNC45MTU4IDE4Ljc0MTUgMTUuMjExOEMxOS43NjgzIDE1LjY3MjkgMjAuMjQ0NSAxNi4yMzcyIDIwLjIzNzQgMTYuOTczN0MyMC4yMjMgMTguMzE1NyAxOS4wMDU2IDE5LjE4MyAxNy4xMzU2IDE5LjE4M0MxNi4zMzYxIDE5LjE3NiAxNS41NjYgMTkuMDE3NCAxNS4xNDggMTguODM4N0wxNS4zOTc0IDE3LjM4NjVMMTUuNjMyMSAxNy40ODk4QzE2LjIxMTUgMTcuNzMxIDE2LjU5MjcgMTcuODM0IDE3LjMwNDIgMTcuODM0QzE3LjgxNzMgMTcuODM0IDE4LjM2NzQgMTcuNjM0MiAxOC4zNzQ1IDE3LjIwMDhDMTguMzc0NSAxNi45MTg2IDE4LjE0MDEgMTYuNzExOSAxNy40NTA2IDE2LjM5NTRDMTYuNzc2IDE2LjA4NTUgMTUuODc0MSAxNS41Njk1IDE1Ljg4ODcgMTQuNjQwM0MxNS44OTYzIDEzLjM4MDkgMTcuMTM1NiAxMi41IDE4Ljg5NTYgMTIuNVpNMTIuMjUxMyAxOS4wODY3SDE0LjAzMzVMMTUuMTQ4IDEyLjYxNzRIMTMuMzY2TDEyLjI1MTMgMTkuMDg2N1pNMjMuNjgzNiAxMi42MTc0SDI1LjA2MjFMMjYuNDk5OSAxOS4wODY2SDI0Ljg0OThDMjQuODQ5OCAxOS4wODY2IDI0LjY4ODMgMTguMzQzMyAyNC42MzcxIDE4LjExNjFIMjIuMzQ5QzIyLjI4MjggMTguMjg4MSAyMS45NzQ5IDE5LjA4NjYgMjEuOTc0OSAxOS4wODY2SDIwLjEwNUwyMi43NTIxIDEzLjE1NDFDMjIuOTM1NiAxMi43MzQzIDIzLjI1ODUgMTIuNjE3NCAyMy42ODM2IDEyLjYxNzRaTTIzLjU3MzUgMTQuOTg0N0MyMy41NzM1IDE0Ljk4NDcgMjMuMDA4OCAxNi40MjMyIDIyLjg2MiAxNi43OTQ4SDI0LjM0MzNDMjQuMjcgMTYuNDcxNCAyMy45MzI2IDE0LjkyMjggMjMuOTMyNiAxNC45MjI4TDIzLjgwOCAxNC4zNjU0QzIzLjc1NTYgMTQuNTA4OSAyMy42Nzk3IDE0LjcwNjMgMjMuNjI4NiAxNC44Mzk0QzIzLjU5MzkgMTQuOTI5NiAyMy41NzA1IDE0Ljk5MDMgMjMuNTczNSAxNC45ODQ3Wk0xMC43NjI1IDEyLjYxNzRMOS4wMTcyIDE3LjAyODhMOC44MjY0OCAxNi4xMzQxTDguODI2NDUgMTYuMTM0TDguODI2NiAxNi4xMzQ0TDguMjAzMjggMTMuMTYxMkM4LjEwMDY2IDEyLjc0OCA3Ljc4NTMgMTIuNjMxIDcuMzk2NjUgMTIuNjE3NEg0LjUyOTMzTDQuNSAxMi43NDgxQzUuMTk5MjcgMTIuOTE1OSA1LjgyNDcxIDEzLjE1NzggNi4zNzI3MyAxMy40NTg1TDcuOTYxMTggMTkuMDc5OEg5Ljg0NTc2TDEyLjY0NyAxMi42MTc0SDEwLjc2MjVaIiBmaWxsPSIjMTQzNENCIi8+Cjwvc3ZnPgo=);\n}\n.tooltip__button--data--provider__amex::before {\n  background-image: url(data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjMyIiB2aWV3Qm94PSIwIDAgMzIgMzIiIHdpZHRoPSIzMiIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cGF0aCBkPSJtMjYgNGMzLjMxMzcgMCA2IDIuNjg2MjkgNiA2djEyYzAgMy4zMTM3LTIuNjg2MyA2LTYgNmgtMjBjLTMuMzEzNzEgMC01Ljk5OTk5OTAyLTIuNjg2My01Ljk5OTk5ODg4LTZsLjAwMDAwMDUzLTEyYy4wMDAwMDAxNC0zLjMxMzcxIDIuNjg2Mjg4MzUtNiA1Ljk5OTk5ODM1LTZ6IiBmaWxsPSIjMDA2ZmNmIi8+PHBhdGggY2xpcC1ydWxlPSJldmVub2RkIiBkPSJtMzAgMjJ2LTEyYzAtMi4yMDkxNC0xLjc5MDktNC00LTRoLTIwYy0yLjIwOTE0IDAtNCAxLjc5MDg2LTQgNHYxMmMwIDIuMjA5MSAxLjc5MDg2IDQgNCA0aDIwYzIuMjA5MSAwIDQtMS43OTA5IDQtNHptMi0xMmMwLTMuMzEzNzEtMi42ODYzLTYtNi02aC0yMGMtMy4zMTM3MSAwLTUuOTk5OTk4MjEgMi42ODYyOS01Ljk5OTk5ODM1IDZsLS4wMDAwMDA1MyAxMmMtLjAwMDAwMDE0IDMuMzEzNyAyLjY4NjI4ODg4IDYgNS45OTk5OTg4OCA2aDIwYzMuMzEzNyAwIDYtMi42ODYzIDYtNnoiIGZpbGw9IiMwMDAiIGZpbGwtb3BhY2l0eT0iLjE4IiBmaWxsLXJ1bGU9ImV2ZW5vZGQiLz48cGF0aCBkPSJtMjYuNTcwMiAxNC45MjY1aDIuMTIwMnYtNC45MjY1aC0yLjMwNzN2LjY4NmwtLjQzNjYtLjY4NmgtMS45OTU1di44NzMxbC0uMzc0Mi0uODczMWgtMy42NzkyYy0uMTI0OCAwLS4yNDk1LjA2MjQtLjM3NDIuMDYyNHMtLjE4NzEuMDYyMy0uMzExOC4xMjQ3Yy0uMTI0Ny4wNjIzLS4xODcxLjA2MjMtLjMxMTguMTI0N3YtLjMxMThoLTEwLjUzOWwtLjMxMTguODEwNy0uMzExODEtLjgxMDdoLTIuNDk0NDN2Ljg3MzFsLS4zNzQxNi0uODczMWgtMS45OTU1NWwtLjg3MzA1IDIuMTIwM3YyLjgwNjJoMS40MzQzbC4yNDk0NC0uNjg2aC40OTg4OWwuMjQ5NDQuNjg2aDEwLjk3NTUzdi0uNjIzNmwuNDM2NS42MjM2aDMuMDU1N3YtLjM3NDJjLjA2MjMuMDYyNC4xODcxLjA2MjQuMjQ5NC4xMjQ4LjA2MjQuMDYyMy4xODcxLjA2MjMuMjQ5NS4xMjQ3LjEyNDcuMDYyMy4yNDk0LjA2MjMuMzc0MS4wNjIzaDIuMjQ1bC4yNDk1LS42ODU5aC40OTg4bC4yNDk1LjY4NTloMy4wNTU3di0uNjIzNnptMy40Mjk4IDYuMjM2MXYtNC42MTQ3aC0xNy4xNDkybC0uNDM2NS42MjM2LS40MzY2LS42MjM2aC00Ljk4ODg0djQuOTI2NWg0Ljk4ODg0bC40MzY2LS42MjM2LjQzNjUuNjIzNmgzLjExOHYtMS4wNjAxaC0uMTI0N2MuNDM2NSAwIC44MTA3LS4wNjI0IDEuMTIyNS0uMTg3MXYxLjMwOTVoMi4yNDV2LS42MjM2bC40MzY1LjYyMzZoOS4yOTE4Yy4zNzQxLS4xMjQ3Ljc0ODMtLjE4NyAxLjA2MDEtLjM3NDF6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTI4LjkzOTkgMTkuOTc3N2gtMS42ODM4di42ODZoMS42MjE0Yy42ODYgMCAxLjEyMjUtLjQzNjYgMS4xMjI1LTEuMDYwMnMtLjM3NDItLjkzNTQtLjk5NzgtLjkzNTRoLS43NDgzYy0uMTg3MSAwLS4zMTE4LS4xMjQ3LS4zMTE4LS4zMTE4cy4xMjQ3LS4zMTE4LjMxMTgtLjMxMThoMS40MzQzbC4zMTE4LS42ODU5aC0xLjY4MzdjLS42ODYgMC0xLjEyMjUuNDM2NS0xLjEyMjUuOTk3NyAwIC42MjM2LjM3NDEuOTM1NC45OTc3LjkzNTRoLjc0ODRjLjE4NyAwIC4zMTE4LjEyNDguMzExOC4zMTE4LjA2MjMuMjQ5NS0uMDYyNC4zNzQyLS4zMTE4LjM3NDJ6bS0zLjA1NTcgMGgtMS42ODM4di42ODZoMS42MjE0Yy42ODYgMCAxLjEyMjUtLjQzNjYgMS4xMjI1LTEuMDYwMnMtLjM3NDEtLjkzNTQtLjk5NzgtLjkzNTRoLS43NDgzYy0uMTg3MSAwLS4zMTE4LS4xMjQ3LS4zMTE4LS4zMTE4cy4xMjQ3LS4zMTE4LjMxMTgtLjMxMThoMS40MzQzbC4zMTE4LS42ODU5aC0xLjY4MzdjLS42ODYgMC0xLjEyMjUuNDM2NS0xLjEyMjUuOTk3NyAwIC42MjM2LjM3NDEuOTM1NC45OTc4LjkzNTRoLjc0ODNjLjE4NzEgMCAuMzExOC4xMjQ4LjMxMTguMzExOC4wNjI0LjI0OTUtLjEyNDcuMzc0Mi0uMzExOC4zNzQyem0tMi4xODI2LTEuOTk1NXYtLjY4NmgtMi42MTkydjMuMzA1MWgyLjYxOTJ2LS42ODZoLTEuODcwOXYtLjY4NTloMS44MDg1di0uNjg2aC0xLjgwODV2LS42MjM2aDEuODcwOXptLTQuMjQwNiAwYy4zMTE4IDAgLjQzNjYuMTg3LjQzNjYuMzc0MXMtLjEyNDguMzc0Mi0uNDM2Ni4zNzQyaC0uOTM1NHYtLjgxMDd6bS0uOTM1NCAxLjQzNDNoLjM3NDJsLjk5NzggMS4xODQ4aC45MzU0bC0xLjEyMjUtMS4yNDcyYy41NjEyLS4xMjQ3Ljg3My0uNDk4OS44NzMtLjk5NzggMC0uNjIzNi0uNDM2NS0xLjA2MDEtMS4xMjI1LTEuMDYwMWgtMS43NDYxdjMuMzA1MWguNzQ4NHptLTEuOTk1NS0uOTk3OGMwIC4yNDk0LS4xMjQ4LjQzNjUtLjQzNjYuNDM2NWgtLjk5Nzd2LS44NzNoLjkzNTRjLjMxMTggMCAuNDk4OS4xODcuNDk4OS40MzY1em0tMi4xODI3LTEuMTIyNXYzLjMwNTFoLjc0ODR2LTEuMTIyNWguOTk3N2MuNjg2IDAgMS4xODQ5LS40MzY1IDEuMTg0OS0xLjEyMjUgMC0uNjIzNi0uNDM2NS0xLjEyMjUtMS4xMjI1LTEuMTIyNXptLTEuMTIyNSAzLjMwNTFoLjkzNTVsLTEuMzA5Ni0xLjY4MzcgMS4zMDk2LTEuNjIxNGgtLjkzNTVsLS44MTA2IDEuMDYwMS0uODEwNy0xLjA2MDFoLS45MzU0bDEuMzA5NSAxLjYyMTQtMS4zMDk1IDEuNjIxNGguOTM1NGwuODEwNy0xLjA2MDJ6bS0yLjgwNjItMi42MTkxdi0uNjg2aC0yLjYxOTE1djMuMzA1MWgyLjYxOTE1di0uNjg2aC0xLjg3MDgydi0uNjg1OWgxLjgwODQydi0uNjg2aC0xLjgwODQydi0uNjIzNmgxLjg3MDgyem0xNS4xNTM3LTUuODYyIDEuMzA5NiAxLjk5NTZoLjkzNTR2LTMuMzA1MWgtLjc0ODR2Mi4xODI2bC0uMTg3LS4zMTE4LTEuMTg0OS0xLjg3MDhoLS45OTc4djMuMzA1MWguNzQ4NHYtMi4yNDV6bS0zLjI0MjgtLjA2MjMuMjQ5NS0uNjg2LjI0OTQuNjg2LjMxMTguNzQ4M2gtMS4xMjI1em0xLjMwOTYgMi4wNTc5aC44MTA3bC0xLjQzNDMtMy4zMDUxaC0uOTk3OGwtMS40MzQzIDMuMzA1MWguODEwN2wuMzExOC0uNjg2aDEuNjIxNHptLTMuNDkyMiAwIC4zMTE4LS42ODZoLS4xODcxYy0uNTYxMiAwLS44NzMtLjM3NDEtLjg3My0uOTM1NHYtLjA2MjRjMC0uNTYxMi4zMTE4LS45MzU0Ljg3My0uOTM1NGguODEwN3YtLjY4NTloLS44NzNjLS45OTc4IDAtMS41NTkxLjY4NTktMS41NTkxIDEuNjIxM3YuMDYyNGMwIC45OTc4LjU2MTMgMS42MjE0IDEuNDk2NyAxLjYyMTR6bS0yLjgwNjIgMGguNzQ4M3YtMy4yNDI4aC0uNzQ4M3ptLTEuNjIxNC0yLjYxOTJjLjMxMTggMCAuNDM2NS4xODcxLjQzNjUuMzc0MnMtLjEyNDcuMzc0Mi0uNDM2NS4zNzQyaC0uOTM1NHYtLjgxMDd6bS0uOTM1NCAxLjQzNDNoLjM3NDFsLjk5NzggMS4xODQ5aC45MzU0bC0xLjEyMjUtMS4yNDcyYy41NjEzLS4xMjQ3Ljg3MzEtLjQ5ODkuODczMS0uOTk3OCAwLS42MjM2LS40MzY2LTEuMDYwMS0xLjEyMjUtMS4wNjAxaC0xLjc0NjF2My4zMDUxaC43NDgzem0tMS4zNzItMS40MzQzdi0uNjg1OWgtMi42MTkxdjMuMzA1MWgyLjYxOTF2LS42ODZoLTEuODcwOHYtLjY4NTloMS44MDg1di0uNjg2aC0xLjgwODV2LS42MjM2aDEuODcwOHptLTUuNjc0ODEgMi42MTkyaC42ODU5N2wuOTM1NDEtMi42ODE1djIuNjgxNWguNzQ4MzN2LTMuMzA1MWgtMS4yNDcyMWwtLjc0ODMzIDIuMjQ1LS43NDgzMy0yLjI0NWgtMS4yNDcyMnYzLjMwNTFoLjc0ODMzdi0yLjY4MTV6bS00LjA1MzQ1LTIuMDU3OS4yNDk0NC0uNjg2LjI0OTQ1LjY4Ni4zMTE4Ljc0ODNoLTEuMTIyNDl6bTEuMzA5NTggMi4wNTc5aC44MTA2OWwtMS40MzQzLTMuMzA1MWgtLjkzNTQxbC0xLjQzNDMgMy4zMDUxaC44MTA2OWwuMzExOC0uNjg2aDEuNjIxMzl6IiBmaWxsPSIjMDA2ZmNmIi8+PC9zdmc+);\n}\n.tooltip__button--data--identities::before {\n  background-size: 20px;\n  background-image: url(data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiB2aWV3Qm94PSIwIDAgMjQgMjQiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZmlsbD0iIzAwMCIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTYgOS41YTQgNCAwIDEgMS04IDAgNCA0IDAgMCAxIDggMG0tMS41IDBhMi41IDIuNSAwIDEgMS01IDAgMi41IDIuNSAwIDAgMSA1IDAiIGNsaXAtcnVsZT0iZXZlbm9kZCIvPgogIDxwYXRoIGZpbGw9IiMwMDAiIGZpbGwtcnVsZT0iZXZlbm9kZCIgZD0iTTEyIDIyYzUuNTIzIDAgMTAtNC40NzcgMTAtMTBTMTcuNTIzIDIgMTIgMiAyIDYuNDc3IDIgMTJzNC40NzcgMTAgMTAgMTBtMC0xLjVhOC40NyA4LjQ3IDAgMCAwIDUuNzA2LTIuMkE2LjU4IDYuNTggMCAwIDAgMTIgMTVhNi41OCA2LjU4IDAgMCAwLTUuNzA1IDMuM0E4LjQ3IDguNDcgMCAwIDAgMTIgMjAuNW0wLTdhOC4wNyA4LjA3IDAgMCAxIDYuNzYgMy42NTMgOC41IDguNSAwIDEgMC0xMy41MiAwQTguMDcgOC4wNyAwIDAgMSAxMiAxMy41IiBjbGlwLXJ1bGU9ImV2ZW5vZGQiLz4KPC9zdmc+Cg==);\n}\n.tooltip__button--data--credentials.tooltip__button--data--bitwarden::before,\n.tooltip__button--data--credentials__current.tooltip__button--data--bitwarden::before {\n  background-image: url(data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiB2aWV3Qm94PSIwIDAgMjQgMjQiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZmlsbD0iI2ZmZiIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTkuMzM3IDNINC42ODRjLS40MTgtLjAxNC0uODA3LjMyNy0uODA5Ljc0OHY5LjAxYy4wMDQuNjg2LjE1IDEuMzY1LjQyOCAyIC41MjggMS4yOSAxLjQ2NSAyLjM4IDIuNTQ1IDMuMjUzLjk2NC44MzggMi4wNDUgMS41IDMuMTY0IDIuMTEuNTIzLjI4NSAxLjM0OC44NzkgMS45NzQuODc5LjY0MyAwIDEuNDYtLjU4NSAxLjk5OS0uODc5IDEuMTItLjYxMSAyLjE5MS0xLjI4MyAzLjE2My0yLjExIDEuMDgtLjg5MyAxLjk5NC0xLjk2IDIuNTQ2LTMuMjUzYTUuMDQ4IDUuMDQ4IDAgMCAwIC40MjgtMnYtOS4wMWMuMDQ0LS40My0uMzgtLjc1NC0uNzg1LS43NDhaIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiLz4KICA8cGF0aCBmaWxsPSJ1cmwoI0JpdHdhcmRlbi1Db2xvci0yNF9zdmdfX2EpIiBkPSJNNS4wMzMgMmgxMy45NjVhMi4wNDcgMi4wNDcgMCAwIDEgMS4zNzMuNTI4Yy4zNjYuMzMuNjYyLjg1NC42MjYgMS40OXY4LjcyN2E2LjIzOCA2LjIzOCAwIDAgMS0uNTEgMi40MzFjLS42MjEgMS40NzYtMS42MyAyLjY2LTIuNzU4IDMuNjEzLS45NTMuODI2LTEuOTgxIDEuNDkzLTMuMDIgMi4wODJsLS4yMDguMTE3Yy0uMTAzLjA1Ny0uMjIuMTI5LS4zOS4yMzFhMTEuMyAxMS4zIDAgMCAxLS41NDguMzE1Yy0uMzU3LjE5LS45NC40NjYtMS41NzYuNDY2LS42MzUgMC0xLjIyLS4yODUtMS41Ny0uNDcyYTExLjYzIDExLjYzIDAgMCAxLS41NDItLjMxNGMtLjEyNi0uMDc2LS4yMjEtLjEzNS0uMzAyLS4xODJsLS4wNzctLjA0NGMtMS4wOTItLjYwOC0yLjIwOC0xLjI5OS0zLjIyMi0yLjE5NC0xLjEzMS0uOTM1LTIuMTY5LTIuMTQ1LTIuNzY5LTMuNjNBNi4yMzYgNi4yMzYgMCAwIDEgMyAxMi43NDVWMy45NzNjLjAwNC0xLjE0Ny45ODYtMS45OSAyLjAzMy0xLjk3MlptLS4wMTIgMS4yNWMtLjM5OC0uMDE0LS43Ny4zMTgtLjc3MS43Mjd2OC43NmMuMDA0LjY2Ny4xNDMgMS4zMjcuNDA4IDEuOTQ1LjUwMyAxLjI1NCAxLjM5OCAyLjMxNCAyLjQyOCAzLjE2Mi45Mi44MTQgMS45NSAxLjQ1NyAzLjAxOCAyLjA1MS40OTguMjc4IDEuMjg2Ljg1NSAxLjg4My44NTUuNjEzIDAgMS4zOTMtLjU3IDEuOTA2LS44NTUgMS4wNjgtLjU5NCAyLjA5LTEuMjQ3IDMuMDE4LTIuMDUgMS4wMjktLjg2OSAxLjkwMi0xLjkwNyAyLjQyOC0zLjE2M2E0Ljk4NiA0Ljk4NiAwIDAgMCAuNDA4LTEuOTQ1di04Ljc2Yy4wNDItLjQxNy0uMzYyLS43MzMtLjc0OS0uNzI3SDUuMDIxWm0xMi45NzYgOS40NzdjLS4wMDIuNDMtLjA5Mi44NTgtLjI2NiAxLjI2My0uNDI4Ljk5NC0xLjEyNiAxLjgyMi0xLjk0OCAyLjUxNmExNi4zNCAxNi4zNCAwIDAgMS0yLjU1MyAxLjc1NWMtLjQxLjIzLS44LjUwNy0xLjIzLjdWNS4wMDFoNS45OTd2Ny43MjZaIi8+CiAgPGRlZnM+CiAgICA8bGluZWFyR3JhZGllbnQgaWQ9IkJpdHdhcmRlbi1Db2xvci0yNF9zdmdfX2EiIHgxPSIxMiIgeDI9IjEyIiB5MT0iMiIgeTI9IjIyIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSI+CiAgICAgIDxzdG9wIHN0b3AtY29sb3I9IiM1NTdGRjMiLz4KICAgICAgPHN0b3Agb2Zmc2V0PSIxIiBzdG9wLWNvbG9yPSIjMkI1NUNBIi8+CiAgICA8L2xpbmVhckdyYWRpZW50PgogIDwvZGVmcz4KPC9zdmc+Cg==);\n}\n.tooltip__button--data--credentials.tooltip__button--data--bitwarden#provider_locked::before,\n.tooltip__button--data--credentials__current.tooltip__button--data--bitwarden#provider_locked::before {\n  background-image: url(data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiB2aWV3Qm94PSIwIDAgMjQgMjQiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgeG1sbnM6eGxpbms9Imh0dHA6Ly93d3cudzMub3JnLzE5OTkveGxpbmsiPjxsaW5lYXJHcmFkaWVudCBpZD0iYSIgZ3JhZGllbnRVbml0cz0idXNlclNwYWNlT25Vc2UiIHgxPSIxMiIgeDI9IjEyIiB5MT0iMiIgeTI9IjIxLjg0MSI+PHN0b3Agb2Zmc2V0PSIwIiBzdG9wLWNvbG9yPSIjNTU3ZmYzIi8+PHN0b3Agb2Zmc2V0PSIxIiBzdG9wLWNvbG9yPSIjMmI1NWNhIi8+PC9saW5lYXJHcmFkaWVudD48bGluZWFyR3JhZGllbnQgaWQ9ImIiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMTciIHgyPSIxNyIgeTE9IjE5LjI1IiB5Mj0iMTEuNjI1Ij48c3RvcCBvZmZzZXQ9IjAiIHN0b3AtY29sb3I9IiM4ODgiLz48c3RvcCBvZmZzZXQ9IjEiIHN0b3AtY29sb3I9IiNhYWEiLz48L2xpbmVhckdyYWRpZW50PjxsaW5lYXJHcmFkaWVudCBpZD0iYyIgZ3JhZGllbnRVbml0cz0idXNlclNwYWNlT25Vc2UiIHgxPSIxNi45ODYiIHgyPSIxNi45ODYiIHkxPSIxNy4wMzciIHkyPSIyMS4xNTQiPjxzdG9wIG9mZnNldD0iLjAwOCIgc3RvcC1jb2xvcj0iI2UyYTQxMiIvPjxzdG9wIG9mZnNldD0iMSIgc3RvcC1jb2xvcj0iI2MxODAxMCIvPjwvbGluZWFyR3JhZGllbnQ+PHBhdGggZD0ibTE5LjMzNyAzYy40MDUtLjAwNi44MjkuMzE5Ljc4NS43NDh2NC4yNTJjMCAuNTY4LS4zMjYgMi44LS4zMjYgMi44YTQuMjMyIDQuMjMyIDAgMCAwIC0yLjY4Ny0xLjA0OGwtLjEwOS0uMDAyYTQuMjUgNC4yNSAwIDAgMCAtNC4yNSA0LjI1di44NjVhMi43OTggMi43OTggMCAwIDAgLTIgMi42ODJ2Mi43NDhzLS41My0uMDYtLjczOC0uMTc0Yy0xLjExOS0uNjExLTIuMi0xLjI3Mi0zLjE2My0yLjExLTEuMDgtLjg3Mi0yLjAxOS0xLjk2My0yLjU0Ni0zLjI1M2E1LjA0OCA1LjA0OCAwIDAgMSAtLjQyOC0ydi05LjAxYy4wMDItLjQyMS4zOS0uNzYyLjgwOS0uNzQ3aDE0LjY1M3oiIGZpbGw9IiNmZmYiLz48cGF0aCBkPSJtMTkuMTkgMi4wMWMuNDQ2LjA0Ljg2My4yMyAxLjE4MS41MTguMzY2LjMzLjY2Mi44NTQuNjI2IDEuNDl2Ni4zMjJjMCAuNDM1LS45MTguNjk5LTEuMjUuNDE4di02Ljc4Yy4wNDItLjQxOC0uMzYzLS43MzMtLjc0OS0uNzI3aC0xMy45NzdjLS4zOTgtLjAxNS0uNzcuMzE3LS43NzEuNzI2djguNzYxYy4wMDQuNjY3LjE0MiAxLjMyNy40MDggMS45NDQuNTAzIDEuMjU0IDEuMzk4IDIuMzE0IDIuNDI4IDMuMTYyLjkyLjgxNCAxLjk1IDEuNDU3IDMuMDE4IDIuMDUxLjE4My4xMDMuNDA4LjI0NS42NDYuMzg1IDAgLjU2NC0uNTgzIDEuMTAyLTEuMDcuODE1YTEyLjgzMyAxMi44MzMgMCAwIDAgLS4xODQtLjEwN2MtMS4wOTItLjYwOC0yLjIwOC0xLjMtMy4yMjMtMi4xOTQtMS4xMy0uOTM1LTIuMTY3LTIuMTQ1LTIuNzY3LTMuNjNhNi4yMzggNi4yMzggMCAwIDEgLS41MDYtMi40MTl2LTguNzcyYy4wMDQtMS4xNDcuOTg2LTEuOTkgMi4wMzMtMS45NzFoMTMuOTY1eiIgZmlsbD0idXJsKCNhKSIvPjxwYXRoIGQ9Im0xNy45OTcgOS44NjhhNC4yNTcgNC4yNTcgMCAwIDAgLS44ODgtLjExNmwtLjEwOS0uMDAyYTQuMjUgNC4yNSAwIDAgMCAtNC4yNSA0LjI1di44NjVjLS4yNy4wOC0uNTIxLjItLjc1LjM1MnYtMTAuMjE1aDUuOTk3djQuODY3eiIgZmlsbD0idXJsKCNhKSIvPjxnIHN0cm9rZS13aWR0aD0iMS4yNSI+PHJlY3QgaGVpZ2h0PSI3LjYyNSIgcng9IjIuMzc1IiBzdHJva2U9InVybCgjYikiIHdpZHRoPSI0Ljc1IiB4PSIxNC42MjUiIHk9IjExLjYyNSIvPjxwYXRoIGQ9Im0xMi42MjUgMTcuNTQ2YS45Mi45MiAwIDAgMSAuOTIxLS45MjFoNi45MDhhLjkyLjkyIDAgMCAxIC45MjEuOTIxdjIuOTA4YS45MjEuOTIxIDAgMCAxIC0uOTIxLjkyMWgtNi45MDhhLjkyMS45MjEgMCAwIDEgLS45MjEtLjkyMXoiIGZpbGw9IiNmYzMiIHN0cm9rZT0idXJsKCNjKSIgc3Ryb2tlLWxpbmVjYXA9InJvdW5kIi8+PC9nPjwvc3ZnPg==);\n}\nhr {\n  display: block;\n  margin: var(--hr-margin);\n  border: none;\n  border-top: 1px solid rgba(0, 0, 0, .1);\n}\nhr:first-child {\n  display: none;\n}\n@media (prefers-color-scheme: dark) {\n  hr {\n    border-top: 1px solid rgba(255, 255, 255, .2);\n  }\n}\n#privateAddress {\n  align-items: flex-start;\n}\n#personalAddress::before,\n#privateAddress::before,\n#incontextSignup::before,\n#personalAddress.currentFocus::before,\n#personalAddress:hover::before,\n#privateAddress.currentFocus::before,\n#privateAddress:hover::before {\n  filter: none;\n  background-size: 24px;\n  background-image: url(data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiB2aWV3Qm94PSIwIDAgMjQgMjQiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZmlsbD0iI0RFNTgzMyIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTIgMjJjNS41MjMgMCAxMC00LjQ3NyAxMC0xMFMxNy41MjMgMiAxMiAyIDIgNi40NzcgMiAxMnM0LjQ3NyAxMCAxMCAxMCIgY2xpcC1ydWxlPSJldmVub2RkIi8+CiAgPHBhdGggZmlsbD0iI0RERCIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTMuNDA2IDE5LjQ2YzAtLjA3Ny4wMi0uMDk1LS4yMjktLjU5LS42Ni0xLjMyMi0xLjMyMy0zLjE4NS0xLjAyMS00LjM4Ny4wNTUtLjIxOC0uNjIyLTguMDg1LTEuMS04LjMzOC0uNTMyLS4yODMtMS4xODYtLjczMy0xLjc4NC0uODMzLS4zMDQtLjA0OC0uNzAyLS4wMjUtMS4wMTMuMDE3LS4wNTYuMDA3LS4wNTguMTA2LS4wMDUuMTI0LjIwNC4wNy40NTIuMTkuNTk5LjM3MS4wMjcuMDM1LS4wMS4wODktLjA1NC4wOS0uMTM4LjAwNi0uMzg4LjA2My0uNzE4LjM0NC0uMDM4LjAzMi0uMDA2LjA5Mi4wNDMuMDgyLjcwOS0uMTQgMS40MzMtLjA3IDEuODYuMzE3LjAyNy4wMjUuMDEzLjA3LS4wMjQuMDgtMy43MDIgMS4wMDYtMi45NjkgNC4yMjctMS45ODMgOC4xNzkuODc4IDMuNTIgMS4yMDggNC42NTUgMS4zMTIgNXEuMDE2LjA1Mi4wNjYuMDczYzEuMjc1LjUwOCA0LjA1MS41MyA0LjA1MS0uMzMzdi0uMTk1WiIgY2xpcC1ydWxlPSJldmVub2RkIi8+CiAgPHBhdGggZmlsbD0iI2ZmZiIgZD0iTTEzLjkxNCAyMC4yMDNjLS40NDkuMTc2LTEuMzI4LjI1NC0xLjgzNi4yNTQtLjc0NCAwLTEuODE2LS4xMTctMi4yMDctLjI5M2E4OCA4OCAwIDAgMS0xLjY3Ni01Ljk2NmwtLjA3LS4yODZ2LS4wMDJjLS44NDgtMy40NjItMS41NC02LjI5IDIuMjU0LTcuMTc4LjAzNS0uMDA4LjA1Mi0uMDUuMDI5LS4wNzctLjQzNi0uNTE2LTEuMjUxLS42ODYtMi4yODItLjMzLS4wNDMuMDE1LS4wOC0uMDI4LS4wNTMtLjA2NC4yMDItLjI3OS41OTctLjQ5My43OTItLjU4Ny4wNC0uMDIuMDM4LS4wNzktLjAwNC0uMDkyYTQgNCAwIDAgMC0uNTktLjE0Yy0uMDU4LS4wMS0uMDYzLS4xMDktLjAwNS0uMTE3IDEuNDYyLS4xOTYgMi45ODkuMjQzIDMuNzU1IDEuMjA3YS4wNS4wNSAwIDAgMCAuMDI5LjAxOGMyLjgwNS42MDMgMy4wMDYgNS4wMzcgMi42ODIgNS4yNC0uMDYzLjAzOS0uMjY3LjAxNi0uNTM3LS4wMTQtMS4wOTEtLjEyMi0zLjI1Mi0uMzY0LTEuNDY5IDIuOTYuMDE4LjAzMy0uMDA1LjA3Ny0uMDQyLjA4Mi0xLjAwNi4xNTcuMjgzIDMuMzA5IDEuMjMgNS4zODUiLz4KICA8cGF0aCBmaWxsPSIjM0NBODJCIiBkPSJNMTUuMTY5IDE2LjE3MmMtLjIxMy0uMS0xLjAzNS40OS0xLjU4Ljk0Mi0uMTE0LS4xNjItLjMyOC0uMjc5LS44MTMtLjE5NS0uNDI0LjA3NC0uNjU4LjE3Ny0uNzYyLjM1My0uNjctLjI1NC0xLjc5NS0uNjQ2LTIuMDY3LS4yNjctLjI5Ny40MTMuMDc0IDIuMzY4LjQ3IDIuNjIyLjIwNS4xMzMgMS4xOTEtLjUwMSAxLjcwNi0uOTM4LjA4My4xMTcuMjE3LjE4NC40OTIuMTc4LjQxNi0uMDEgMS4wOS0uMTA3IDEuMTk1LS4zYS4yLjIgMCAwIDAgLjAxNy0uMDQyYy41MjkuMTk4IDEuNDYuNDA3IDEuNjY5LjM3Ni41NDItLjA4MS0uMDc2LTIuNjEzLS4zMjctMi43M1oiLz4KICA8cGF0aCBmaWxsPSIjNENCQTNDIiBkPSJNMTMuNjQgMTcuMTcycS4wMzMuMDYuMDU1LjEyNWMuMDc2LjIxLjE5OS44ODIuMTA2IDEuMDQ4cy0uNjk3LjI0Ni0xLjA3LjI1MmMtLjM3Mi4wMDctLjQ1Ni0uMTMtLjUzMS0uMzQtLjA2LS4xNy0uMDktLjU2Ni0uMDktLjc5NC0uMDE1LS4zMzcuMTA4LS40NTUuNjc3LS41NDcuNDIyLS4wNjkuNjQ0LjAxLjc3My4xNDYuNTk4LS40NDYgMS41OTYtMS4wNzYgMS42OTMtLjk2LjQ4Ni41NzMuNTQ3IDEuOTQuNDQyIDIuNDktLjAzNC4xOC0xLjY0MS0uMTc4LTEuNjQxLS4zNzIgMC0uODA1LS4yMS0xLjAyNi0uNDE1LTEuMDQ4Wm0tMy41Mi0uMjUyYy4xMy0uMjA4IDEuMTk4LjA1IDEuNzg0LjMxMiAwIDAtLjEyLjU0NS4wNzEgMS4xODguMDU2LjE4OC0xLjM0OCAxLjAyNC0xLjUzMS44OC0uMjEyLS4xNjYtLjYwMi0xLjk0Mi0uMzI1LTIuMzhaIi8+CiAgPHBhdGggZmlsbD0iI0ZDMyIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTAuNjM2IDEyLjY4OGMuMDg2LS4zNzUuNDg5LTEuMDgzIDEuOTI1LTEuMDY2LjcyNi0uMDAyIDEuNjI5IDAgMi4yMjctLjA2OGE4IDggMCAwIDAgMS45ODgtLjQ4M2MuNjIyLS4yMzcuODQzLS4xODUuOTItLjA0My4wODUuMTU2LS4wMTUuNDI2LS4yMzIuNjczLS40MTUuNDc0LTEuMTYyLjg0MS0yLjQ4Ljk1LTEuMzE3LjEwOS0yLjE5LS4yNDUtMi41NjYuMzMtLjE2Mi4yNS0uMDM3LjgzNCAxLjIzOCAxLjAxOCAxLjcyMi4yNDkgMy4xMzYtLjMgMy4zMS4wMzIuMTc1LjMzLS44MzEgMS4wMDQtMi41NTYgMS4wMThzLTIuODAyLS42MDQtMy4xODQtLjkxYy0uNDg1LS4zOS0uNzAyLS45NTktLjU5LTEuNDVaIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiLz4KICA8ZyBmaWxsPSIjMTQzMDdFIiBvcGFjaXR5PSIuOCI+CiAgICA8cGF0aCBkPSJNMTIuODMzIDguNTgyYy4wOTYtLjE1Ny4zMS0uMjc5LjY1OC0uMjc5LjM1IDAgLjUxNC4xNC42MjcuMjk0LjAyNC4wMzItLjAxMi4wNjktLjA0OC4wNTNsLS4wMjYtLjAxMWExLjMgMS4zIDAgMCAwLS41NTMtLjEyOCAxLjE0IDEuMTQgMCAwIDAtLjU4Mi4xM2MtLjAzOS4wMi0uMS0uMDIxLS4wNzYtLjA1OW0tMy45MzEuMjAyYTEuMjUgMS4yNSAwIDAgMSAuNzk0LS4wNzljLjA0LjAxLjA2Ny0uMDMzLjAzNS0uMDU5LS4xNDYtLjExOC0uNDczLS4yNjQtLjktLjEwNS0uMzguMTQyLS41Ni40MzctLjU2MS42MyAwIC4wNDcuMDk0LjA1LjExOC4wMTIuMDY2LS4xMDUuMTc1LS4yNTcuNTE0LS40WiIvPgogICAgPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNMTMuNzg4IDEwLjczOGEuNTQyLjU0MiAwIDEgMS0uMDAyLTEuMDguNTQyLjU0MiAwIDAgMSAuMDAyIDEuMDhtLjM4Mi0uNzJhLjE0LjE0IDAgMCAwLS4yODEgMCAuMTQuMTQgMCAwIDAgLjI4MSAwbS0zLjk3OS41NTJhLjYzMi42MzIgMCAxIDEtMS4yNjMgMCAuNjMyLjYzMiAwIDAgMSAxLjI2MyAwbS0uMTg2LS4yMDhhLjE2NC4xNjQgMCAwIDAtLjMyOCAwIC4xNjQuMTY0IDAgMCAwIC4zMjggMCIgY2xpcC1ydWxlPSJldmVub2RkIi8+CiAgPC9nPgogIDxwYXRoIGZpbGw9IiNmZmYiIGZpbGwtcnVsZT0iZXZlbm9kZCIgZD0iTTEyIDIwYTggOCAwIDEgMCAwLTE2IDggOCAwIDAgMCAwIDE2bTAgMWE5IDkgMCAxIDAgMC0xOCA5IDkgMCAwIDAgMCAxOCIgY2xpcC1ydWxlPSJldmVub2RkIi8+Cjwvc3ZnPgo=);\n}\n.tooltip__button--email {\n  flex-direction: column;\n  justify-content: center;\n  align-items: flex-start;\n  font-size: 14px;\n  padding: 4px 8px;\n}\n.tooltip__button--email__primary-text {\n  font-weight: bold;\n}\n.tooltip__button--email__secondary-text {\n  font-size: 12px;\n}\n:not(.top-autofill) .tooltip--email-signup {\n  text-align: left;\n  color: #222;\n  padding: 16px 20px;\n  width: 380px;\n}\n.tooltip--email-signup h1 {\n  font-weight: 700;\n  font-size: 16px;\n  line-height: 1.5;\n  margin: 0;\n}\n.tooltip--email-signup p {\n  font-weight: 400;\n  font-size: 14px;\n  line-height: 1.4;\n}\n.notice-controls {\n  display: flex;\n}\n.tooltip--email-signup .notice-controls > * {\n  border-radius: 8px;\n  border: 0;\n  cursor: pointer;\n  display: inline-block;\n  font-family: inherit;\n  font-style: normal;\n  font-weight: bold;\n  padding: 8px 12px;\n  text-decoration: none;\n}\n.notice-controls .ghost {\n  margin-left: 1rem;\n}\n.tooltip--email-signup a.primary {\n  background: #3969EF;\n  color: #fff;\n}\n.tooltip--email-signup a.primary:hover,\n.tooltip--email-signup a.primary:focus {\n  background: #2b55ca;\n}\n.tooltip--email-signup a.primary:active {\n  background: #1e42a4;\n}\n.tooltip--email-signup button.ghost {\n  background: transparent;\n  color: #3969EF;\n}\n.tooltip--email-signup button.ghost:hover,\n.tooltip--email-signup button.ghost:focus {\n  background-color: rgba(0, 0, 0, 0.06);\n  color: #2b55ca;\n}\n.tooltip--email-signup button.ghost:active {\n  background-color: rgba(0, 0, 0, 0.12);\n  color: #1e42a4;\n}\n.tooltip--email-signup button.close-tooltip {\n  background-color: transparent;\n  background-image: url(data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTIiIGhlaWdodD0iMTMiIHZpZXdCb3g9IjAgMCAxMiAxMyIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0wLjI5Mjg5NCAwLjY1NjkwN0MwLjY4MzQxOCAwLjI2NjM4MyAxLjMxNjU4IDAuMjY2MzgzIDEuNzA3MTEgMC42NTY5MDdMNiA0Ljk0OThMMTAuMjkyOSAwLjY1NjkwN0MxMC42ODM0IDAuMjY2MzgzIDExLjMxNjYgMC4yNjYzODMgMTEuNzA3MSAwLjY1NjkwN0MxMi4wOTc2IDEuMDQ3NDMgMTIuMDk3NiAxLjY4MDYgMTEuNzA3MSAyLjA3MTEyTDcuNDE0MjEgNi4zNjQwMUwxMS43MDcxIDEwLjY1NjlDMTIuMDk3NiAxMS4wNDc0IDEyLjA5NzYgMTEuNjgwNiAxMS43MDcxIDEyLjA3MTFDMTEuMzE2NiAxMi40NjE2IDEwLjY4MzQgMTIuNDYxNiAxMC4yOTI5IDEyLjA3MTFMNiA3Ljc3ODIzTDEuNzA3MTEgMTIuMDcxMUMxLjMxNjU4IDEyLjQ2MTYgMC42ODM0MTcgMTIuNDYxNiAwLjI5Mjg5MyAxMi4wNzExQy0wLjA5NzYzMTEgMTEuNjgwNiAtMC4wOTc2MzExIDExLjA0NzQgMC4yOTI4OTMgMTAuNjU2OUw0LjU4NTc5IDYuMzY0MDFMMC4yOTI4OTQgMi4wNzExMkMtMC4wOTc2MzA2IDEuNjgwNiAtMC4wOTc2MzA2IDEuMDQ3NDMgMC4yOTI4OTQgMC42NTY5MDdaIiBmaWxsPSJibGFjayIgZmlsbC1vcGFjaXR5PSIwLjg0Ii8+Cjwvc3ZnPgo=);\n  background-position: center center;\n  background-repeat: no-repeat;\n  border: 0;\n  cursor: pointer;\n  padding: 16px;\n  position: absolute;\n  right: 12px;\n  top: 12px;\n}\n.tooltip__button--credentials-import::before {\n  background-size: 20px;\n  background-image: url(data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiB2aWV3Qm94PSIwIDAgMjQgMjQiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZmlsbD0iIzAwMCIgZmlsbC1vcGFjaXR5PSIuOSIgZD0iTTE0Ljk5OCAyQTcuMDA0IDcuMDA0IDAgMCAxIDIyIDkuMDA2YzAgMi0uODM4IDMuODA2LTIuMTgyIDUuMDgyYS42Ni42NiAwIDAgMS0uNzY4LjA5MmMtLjQ3Mi0uMjUxLS41MDctLjkzLS4xMzItMS4zMUE1LjUgNS41IDAgMCAwIDIwLjUgOS4wMDYgNS41MDQgNS41MDQgMCAwIDAgMTQuOTk4IDMuNWE1LjUwNCA1LjUwNCAwIDAgMC01LjMzOCA2Ljg0N2wuMDI1LjExMmMuMTAzLjU0NC0uMDE4IDEuMTU2LS40NCAxLjYxMWwtLjA0Ni4wNDctNS4wNCA1LjA0M2EyLjI1IDIuMjUgMCAwIDAtLjY1OSAxLjYwMmwuMDAzLjQ5NC4wMDIuMDY1QTEuMjUgMS4yNSAwIDAgMCA0Ljc1MyAyMC41aC45OTNhLjc1Ljc1IDAgMCAwIC43NS0uNzV2LS43MzRhMS41IDEuNSAwIDAgMSAxLjUtMS41aC45ODJhLjUuNSAwIDAgMCAuMzU0LS4xNDdsMS4xNzgtMS4xNzhhLjczNi43MzYgMCAwIDEgMS4wNjIgMS4wMmwtLjUzMi41Ny0uNjQ3LjY0OWEyIDIgMCAwIDEtMS40MTUuNTg2aC0uOTgydi43MzRBMi4yNSAyLjI1IDAgMCAxIDUuNzQ2IDIyaC0uOTkzYTIuNzUgMi43NSAwIDAgMS0yLjc0Ni0yLjU5NWwtLjAwNC0uMTRMMiAxOC43N2EzLjc1IDMuNzUgMCAwIDEgMS4wOTgtMi42N2w1LjA0LTUuMDQ0LjAyMi0uMDI1YS4zNi4zNiAwIDAgMCAuMDU2LS4yNmwtLjAxMS0uMDZBNy4wMDQgNy4wMDQgMCAwIDEgMTQuOTk4IDIiLz4KICA8cGF0aCBmaWxsPSIjMDAwIiBmaWxsLW9wYWNpdHk9Ii45IiBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xNS41IDZhMi41IDIuNSAwIDEgMSAwIDUgMi41IDIuNSAwIDAgMSAwLTVtMCAxLjVhMSAxIDAgMSAwIDAgMiAxIDEgMCAwIDAgMC0yIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiLz4KICA8cGF0aCBmaWxsPSIjMDAwIiBkPSJNMTMuMDAzIDE2LjE5cTAtLjAzLjAwMy0uMDU4YTEgMSAwIDAgMSAuMDE5LS4wOTdsLjAxLS4wNGExIDEgMCAwIDEgLjA0Ni0uMTEybC4wMDgtLjAxOS4wMDMtLjAwNi4wMDYtLjAwOWExIDEgMCAwIDEgLjA2NS0uMDk2bC4wMDgtLjAxMS4wMDMtLjAwNCAzLjEyNS0zLjc1YS43NS43NSAwIDAgMSAxLjE1Mi45NjFsLTIuMTI2IDIuNTVoNS4xNDRhLjc1Ljc1IDAgMCAxIDAgMS41aC01LjA5MmwyLjA3NCAyLjQ5LjA0Ni4wNmEuNzUuNzUgMCAwIDEtMS4xNDYuOTU2bC0uMDUyLS4wNTYtMy4xMjUtMy43NS0uMDAzLS4wMDMtLjAxNi0uMDIxLS4wMjctLjAzOC0uMDItLjAzNi0uMDM1LS4wNjNhLjguOCAwIDAgMS0uMDctLjI3MXoiLz4KPC9zdmc+Cg==);\n}\n.truncate {\n  display: block;\n  width: 0;\n  min-width: 100%;\n  overflow: hidden;\n  text-overflow: ellipsis;\n  white-space: nowrap;\n  line-height: 1.25;\n}\n';

  // src/UI/HTMLTooltip.js
  var defaultOptions = {
    wrapperClass: "",
    platform: null,
    tooltipPositionClass: (top, left) => `
        .tooltip {
            transform: translate(${Math.floor(left)}px, ${Math.floor(top)}px) !important;
        }
    `,
    caretPositionClass: (top, left, isAboveInput) => `
        .tooltip--email__caret {
            ${isAboveInput ? `transform: translate(${Math.floor(left)}px, ${Math.floor(top)}px) rotate(180deg); transform-origin: 18px !important;` : `transform: translate(${Math.floor(left)}px, ${Math.floor(top)}px) !important;`}
        }`,
    css: `<style>${autofill_tooltip_styles_default}</style>`,
    setSize: void 0,
    remove: () => {
    },
    testMode: false,
    checkVisibility: true,
    hasCaret: false,
    isTopAutofill: false,
    isIncontextSignupAvailable: () => false
  };
  var HTMLTooltip = class {
    /**
     * @param inputType
     * @param getPosition
     * @param {HTMLTooltipOptions} options
     */
    constructor(inputType, getPosition, options) {
      __publicField(this, "isAboveInput", false);
      /** @type {HTMLTooltipOptions} */
      __publicField(this, "options");
      __publicField(this, "resObs", new ResizeObserver((entries) => entries.forEach(() => this.checkPosition())));
      __publicField(this, "mutObsCheckPositionWhenIdle", whenIdle.call(this, this.checkPosition));
      __publicField(this, "mutObs", new MutationObserver((mutationList) => {
        for (const mutationRecord of mutationList) {
          if (mutationRecord.type === "childList") {
            mutationRecord.addedNodes.forEach((el) => {
              if (el.nodeName === "DDG-AUTOFILL") return;
              this.ensureIsLastInDOM();
            });
          }
        }
        this.mutObsCheckPositionWhenIdle();
      }));
      __publicField(this, "clickableButtons", /* @__PURE__ */ new Map());
      this.options = options;
      this.shadow = document.createElement("ddg-autofill").attachShadow({
        mode: options.testMode ? "open" : "closed"
      });
      this.host = this.shadow.host;
      this.subtype = getSubtypeFromType(inputType);
      this.variant = getVariantFromType(inputType);
      this.tooltip = null;
      this.getPosition = getPosition;
      const forcedVisibilityStyles = {
        display: "block",
        visibility: "visible",
        opacity: "1"
      };
      addInlineStyles(this.host, forcedVisibilityStyles);
      this.count = 0;
      this.device = null;
      this.transformRules = {
        caret: {
          getRuleString: this.options.caretPositionClass,
          index: null
        },
        tooltip: {
          getRuleString: this.options.tooltipPositionClass,
          index: null
        }
      };
    }
    get isHidden() {
      return this.tooltip.parentNode.hidden;
    }
    append() {
      document.body.appendChild(this.host);
    }
    remove() {
      this.device?.activeForm.resetIconStylesToInitial();
      window.removeEventListener("scroll", this, { capture: true });
      this.resObs.disconnect();
      this.mutObs.disconnect();
      this.lift();
    }
    lift() {
      this.left = null;
      this.top = null;
      document.body.removeChild(this.host);
    }
    handleEvent(event) {
      switch (event.type) {
        case "scroll":
          this.checkPosition();
          break;
      }
    }
    focus(x, y) {
      const focusableElements = "button";
      const currentFocusClassName = "currentFocus";
      const currentFocused = this.shadow.querySelectorAll(`.${currentFocusClassName}`);
      [...currentFocused].forEach((el) => {
        el.classList.remove(currentFocusClassName);
      });
      this.shadow.elementFromPoint(x, y)?.closest(focusableElements)?.classList.add(currentFocusClassName);
    }
    checkPosition() {
      if (this.animationFrame) {
        window.cancelAnimationFrame(this.animationFrame);
      }
      this.animationFrame = window.requestAnimationFrame(() => {
        if (this.isHidden) return;
        const { left, bottom, height, top } = this.getPosition();
        if (left !== this.left || bottom !== this.top) {
          const coords = { left, top: bottom };
          this.updatePosition("tooltip", coords);
          if (this.options.hasCaret) {
            const { top: tooltipTop } = this.tooltip.getBoundingClientRect();
            this.isAboveInput = top > tooltipTop;
            const borderWidth = 2;
            const caretTop = this.isAboveInput ? coords.top - height - borderWidth : coords.top;
            this.updatePosition("caret", { ...coords, top: caretTop });
          }
        }
        this.animationFrame = null;
      });
    }
    getOverridePosition({ left, top }) {
      const tooltipBoundingBox = this.tooltip.getBoundingClientRect();
      const smallScreenWidth = tooltipBoundingBox.width * 2;
      const spacing = 5;
      if (tooltipBoundingBox.bottom > window.innerHeight) {
        const inputPosition = this.getPosition();
        const caretHeight = 14;
        const overriddenTopPosition = top - tooltipBoundingBox.height - inputPosition.height - caretHeight;
        if (overriddenTopPosition >= 0) return { left, top: overriddenTopPosition };
      }
      if (tooltipBoundingBox.left < 0 && window.innerWidth <= smallScreenWidth) {
        const leftOverflow = Math.abs(tooltipBoundingBox.left);
        const leftPosWhenCentered = (window.innerWidth - tooltipBoundingBox.width) / 2;
        const overriddenLeftPosition = left + leftOverflow + leftPosWhenCentered;
        return { left: overriddenLeftPosition, top };
      }
      if (tooltipBoundingBox.left < 0 && window.innerWidth > smallScreenWidth) {
        const leftOverflow = Math.abs(tooltipBoundingBox.left);
        const overriddenLeftPosition = left + leftOverflow + spacing;
        return { left: overriddenLeftPosition, top };
      }
      if (tooltipBoundingBox.right > window.innerWidth) {
        const rightOverflow = tooltipBoundingBox.right - window.innerWidth;
        const overriddenLeftPosition = left - rightOverflow - spacing;
        return { left: overriddenLeftPosition, top };
      }
    }
    /**
     * @param {'tooltip' | 'caret'} element
     * @param {{
     *     left: number,
     *     top: number
     * }} coords
     */
    applyPositionalStyles(element, { left, top }) {
      const shadow = this.shadow;
      const ruleObj = this.transformRules[element];
      if (ruleObj.index) {
        if (shadow.styleSheets[0].rules[ruleObj.index]) {
          shadow.styleSheets[0].deleteRule(ruleObj.index);
        }
      } else {
        ruleObj.index = shadow.styleSheets[0].rules.length;
      }
      const cssRule = ruleObj.getRuleString?.(top, left, this.isAboveInput);
      if (typeof cssRule === "string") {
        shadow.styleSheets[0].insertRule(cssRule, ruleObj.index);
      }
    }
    /**
     * @param {'tooltip' | 'caret'} element
     * @param {{
     *     left: number,
     *     top: number
     * }} coords
     */
    updatePosition(element, { left, top }) {
      if (!this.shadow.styleSheets.length) {
        this.stylesheet?.addEventListener("load", () => this.checkPosition());
        return;
      }
      this.left = left;
      this.top = top;
      this.applyPositionalStyles(element, { left, top });
      if (this.options.hasCaret) {
        const overridePosition = this.getOverridePosition({ left, top });
        if (overridePosition) this.updatePosition(element, overridePosition);
      }
    }
    ensureIsLastInDOM() {
      this.count = this.count || 0;
      if (document.body.lastElementChild !== this.host) {
        if (this.count < 15) {
          this.lift();
          this.append();
          this.checkPosition();
          this.count++;
        } else {
          this.options.remove();
          console.info(`DDG autofill bailing out`);
        }
      }
    }
    setActiveButton(e) {
      this.activeButton = e.target;
    }
    unsetActiveButton() {
      this.activeButton = null;
    }
    registerClickableButton(btn, handler) {
      this.clickableButtons.set(btn, handler);
      btn.addEventListener("mouseenter", (e) => this.setActiveButton(e));
      btn.addEventListener("mouseleave", () => this.unsetActiveButton());
    }
    dispatchClick() {
      const handler = this.clickableButtons.get(this.activeButton);
      if (handler) {
        if (this.activeButton.matches(".wrapper:not(.top-autofill) button:hover, .wrapper:not(.top-autofill) a:hover, .currentFocus")) {
          safeExecute(this.activeButton, handler, {
            checkVisibility: this.options.checkVisibility
          });
        } else {
          console.warn("The button doesn't seem to be hovered. Please check.");
        }
      }
    }
    setupSizeListener() {
      const observer = new PerformanceObserver(() => {
        this.setSize();
      });
      observer.observe({ entryTypes: ["layout-shift", "paint"] });
    }
    setSize() {
      const innerNode = this.shadow.querySelector(".wrapper--data");
      if (!innerNode) return;
      const details = { height: innerNode.clientHeight, width: innerNode.clientWidth };
      this.options.setSize?.(details);
    }
    init() {
      this.animationFrame = null;
      this.top = 0;
      this.left = 0;
      this.transformRuleIndex = null;
      this.stylesheet = this.shadow.querySelector("link, style");
      this.stylesheet?.addEventListener("load", () => {
        Promise.allSettled([
          document.fonts.load("normal 13px 'DDG_ProximaNova'"),
          document.fonts.load("bold 13px 'DDG_ProximaNova'")
        ]).then(() => {
          this.tooltip.parentNode.removeAttribute("hidden");
          this.checkPosition();
        });
      });
      this.append();
      this.resObs.observe(document.body);
      this.mutObs.observe(document.body, { childList: true, subtree: true, attributes: true });
      window.addEventListener("scroll", this, { capture: true });
      this.setSize();
      if (typeof this.options.setSize === "function") {
        this.setupSizeListener();
      }
    }
  };
  var HTMLTooltip_default = HTMLTooltip;

  // src/UI/DataHTMLTooltip.js
  var manageItemStringIds = {
    credentials: "autofill:managePasswords",
    creditCards: "autofill:manageCreditCards",
    identities: "autofill:manageIdentities",
    unknown: "autofill:manageSavedItems"
  };
  var DataHTMLTooltip = class extends HTMLTooltip_default {
    /**
     * @param {import("../locales/strings").TranslateFn} t
     * @param {boolean} isOtherItems
     */
    renderEmailProtectionIncontextSignup(t, isOtherItems) {
      const dataTypeClass = `tooltip__button--data--identities`;
      const providerIconClass = "tooltip__button--data--duckduckgo";
      return `
            ${isOtherItems ? "<hr />" : ""}
            <button id="incontextSignup" class="tooltip__button tooltip__button--data ${dataTypeClass} ${providerIconClass} js-get-email-signup">
                <span class="tooltip__button__text-container">
                    <span class="label label--medium">
                        ${t("autofill:hideEmailAndBlockTrackers")}
                    </span>
                    <span class="label label--small">
                        ${t("autofill:createUniqueRandomAddr")}
                    </span>
                </span>
            </button>
        `;
    }
    /**
     * @param {import("../DeviceInterface/InterfacePrototype").default} device
     * @param {InputTypeConfigs} config
     * @param {import('./interfaces.js').TooltipItemRenderer[]} items
     * @param {{
     *   onSelect(id:string): void
     *   onManage(type:InputTypeConfigs['type']): void
     *   onIncontextSignupDismissed?(data: {
     *      hasOtherOptions: Boolean
     *   }): void
     *   onIncontextSignup?(): void
     * }} callbacks
     */
    render(device, config, items, callbacks) {
      const t = device.t;
      const { wrapperClass, css, isTopAutofill, platform } = this.options;
      let hasAddedSeparator = false;
      const shouldShowSeparator = (dataId, index) => {
        const shouldShow = ["personalAddress", "privateAddress"].includes(dataId) && !hasAddedSeparator;
        if (shouldShow) hasAddedSeparator = true;
        const isFirst = index === 0;
        return shouldShow && !isFirst;
      };
      const shouldShowManageButton = isTopAutofill && items.some((item) => !["personalAddress", "privateAddress", PROVIDER_LOCKED].includes(item.id()));
      const topClass = wrapperClass || "";
      const dataTypeClass = `tooltip__button--data--${config.type}${this.variant ? "__" + this.variant : ""}`;
      this.shadow.innerHTML = `
${css}
<div class="wrapper wrapper--data ${topClass}" hidden data-platform=${platform}>
    <div class="tooltip tooltip--data${this.options.isIncontextSignupAvailable() ? " tooltip--incontext-signup" : ""}">
        ${items.map((item, index) => {
        const credentialsProvider = item.credentialsProvider?.();
        const providerIconClass = credentialsProvider ? `tooltip__button--data--${credentialsProvider}` : "";
        const paymentProvider = item.paymentProvider?.();
        const paymentProviderIconClass = paymentProvider ? `tooltip__button--data--provider__${paymentProvider}` : "";
        const disableHoverEffectClass = paymentProvider ? "no-hover-effect" : "";
        const labelSmall = item.labelSmall?.(t, this.subtype);
        const label = item.label?.(t, this.subtype);
        return `
            ${shouldShowSeparator(item.id(), index) ? "<hr />" : ""}
            <button id="${item.id()}" class="tooltip__button tooltip__button--data ${dataTypeClass} ${paymentProviderIconClass} ${providerIconClass} js-autofill-button ${disableHoverEffectClass}">
                <span class="tooltip__button__text-container">
                    <span class="label label--medium truncate">${escapeXML(item.labelMedium(t, this.subtype))}</span>
                    ${label ? `<span class="label">${escapeXML(label)}</span>` : ""}
                    ${labelSmall ? `<span class="label label--small">${escapeXML(labelSmall)}</span>` : ""}
                </span>
            </button>
        `;
      }).join("")}
        ${this.options.isIncontextSignupAvailable() ? this.renderEmailProtectionIncontextSignup(t, items.length > 0) : ""}
        ${shouldShowManageButton ? `
            <hr />
            <button id="manage-button" class="tooltip__button tooltip__button--secondary" type="button">
                <span class="tooltip__button__text-container">
                    <span class="label label--medium">
                        ${t(manageItemStringIds[config.type] ?? "autofill:manageSavedItems")}
                    </span>
                </span>
            </button>` : ""}
    </div>
</div>`;
      this.wrapper = this.shadow.querySelector(".wrapper");
      this.tooltip = this.shadow.querySelector(".tooltip");
      this.autofillButtons = this.shadow.querySelectorAll(".js-autofill-button");
      this.autofillButtons.forEach((btn) => {
        this.registerClickableButton(btn, () => {
          if (btn.matches(".wrapper:not(.top-autofill) button:hover, .currentFocus")) {
            callbacks.onSelect(btn.id);
          } else {
            console.warn("The button doesn't seem to be hovered. Please check.");
          }
        });
      });
      this.manageButton = this.shadow.getElementById("manage-button");
      if (this.manageButton) {
        this.registerClickableButton(this.manageButton, () => {
          callbacks.onManage(config.type);
        });
      }
      const getIncontextSignup = this.shadow.querySelector(".js-get-email-signup");
      if (getIncontextSignup) {
        this.registerClickableButton(getIncontextSignup, () => {
          callbacks.onIncontextSignupDismissed?.({ hasOtherOptions: items.length > 0 });
          callbacks.onIncontextSignup?.();
        });
      }
      this.init();
      return this;
    }
  };

  // src/UI/EmailHTMLTooltip.js
  var EmailHTMLTooltip = class extends HTMLTooltip_default {
    /**
     * @param {import("../DeviceInterface/InterfacePrototype").default} device
     */
    render(device) {
      this.device = device;
      this.addresses = device.getLocalAddresses();
      this.shadow.innerHTML = `
${this.options.css}
<div class="wrapper wrapper--email" hidden data-platform=${this.options.platform}>
    <div class="tooltip tooltip--email">
        <button class="tooltip__button tooltip__button--email js-use-personal">
            <span class="tooltip__button--email__primary-text">
                ${this.device.t("autofill:usePersonalDuckAddr", { email: formatDuckAddress(escapeXML(this.addresses.personalAddress)) })}
            </span>
            <span class="tooltip__button--email__secondary-text">${this.device.t("autofill:blockEmailTrackers")}</span>
        </button>
        <button class="tooltip__button tooltip__button--email js-use-private">
            <span class="tooltip__button--email__primary-text">${this.device.t("autofill:generateDuckAddr")}</span>
            <span class="tooltip__button--email__secondary-text">${this.device.t("autofill:blockEmailTrackersAndHideAddress")}</span>
        </button>
    </div>
    <div class="tooltip--email__caret"></div>
</div>`;
      this.wrapper = this.shadow.querySelector(".wrapper");
      this.tooltip = this.shadow.querySelector(".tooltip");
      this.usePersonalButton = this.shadow.querySelector(".js-use-personal");
      this.usePrivateButton = this.shadow.querySelector(".js-use-private");
      this.usePersonalCta = this.shadow.querySelector(".js-use-personal > span:first-of-type");
      this.updateAddresses = (addresses) => {
        if (addresses && this.usePersonalCta) {
          this.addresses = addresses;
          this.usePersonalCta.textContent = this.device.t("autofill:usePersonalDuckAddr", {
            email: formatDuckAddress(addresses.personalAddress)
          });
        }
      };
      const firePixel = this.device.firePixel.bind(this.device);
      this.registerClickableButton(this.usePersonalButton, () => {
        this.fillForm("personalAddress");
        firePixel({ pixelName: "autofill_personal_address" });
      });
      this.registerClickableButton(this.usePrivateButton, () => {
        this.fillForm("privateAddress");
        firePixel({ pixelName: "autofill_private_address" });
      });
      this.device.getAddresses().then(this.updateAddresses);
      this.init();
      return this;
    }
    /**
     * @param {'personalAddress' | 'privateAddress'} id
     */
    async fillForm(id) {
      const address = this.addresses[id];
      const formattedAddress = formatDuckAddress(address);
      this.device?.selectedDetail({ email: formattedAddress, id }, "email");
    }
  };

  // src/UI/EmailSignupHTMLTooltip.js
  var EmailSignupHTMLTooltip = class extends HTMLTooltip_default {
    /**
     * @param {import("../DeviceInterface/InterfacePrototype").default} device
     */
    render(device) {
      this.device = device;
      const t = this.device.t;
      this.shadow.innerHTML = `
${this.options.css}
<div class="wrapper wrapper--email" hidden data-platform=${this.options.platform}>
    <div class="tooltip tooltip--email tooltip--email-signup">
        <button class="close-tooltip js-close-email-signup" aria-label="Close"></button>
        <h1>${t("autofill:hideEmailAndBlockTrackers")}</h1>
        <p>${t("autofill:createUniqueRandomAddr")}</p>
        <div class="notice-controls">
            <a href="https://duckduckgo.com/email/start-incontext" target="_blank" class="primary js-get-email-signup">
                ${t("autofill:protectMyEmail")}
            </a>
            <button class="ghost js-dismiss-email-signup">
                ${t("autofill:dontShowAgain")}
            </button>
        </div>
    </div>
    <div class="tooltip--email__caret"></div>
</div>`;
      this.tooltip = this.shadow.querySelector(".tooltip");
      this.closeEmailSignup = this.shadow.querySelector(".js-close-email-signup");
      this.registerClickableButton(this.closeEmailSignup, () => {
        device.inContextSignup?.onIncontextSignupClosed();
      });
      this.dismissEmailSignup = this.shadow.querySelector(".js-dismiss-email-signup");
      this.registerClickableButton(this.dismissEmailSignup, () => {
        device.inContextSignup?.onIncontextSignupDismissed();
      });
      this.getEmailSignup = this.shadow.querySelector(".js-get-email-signup");
      this.registerClickableButton(this.getEmailSignup, () => {
        device.inContextSignup?.onIncontextSignup();
      });
      this.init();
      return this;
    }
  };

  // src/UI/CredentialsImportTooltip.js
  var CredentialsImportTooltip = class extends HTMLTooltip_default {
    /**
     * @param {import("../DeviceInterface/InterfacePrototype.js").default} device
     * @param {{ onStarted(): void, onDismissed(): void }} callbacks
     */
    render(device, callbacks) {
      this.device = device;
      const t = device.t;
      this.shadow.innerHTML = `
${this.options.css}
<div class="wrapper wrapper--data ${this.options.isTopAutofill ? "top-autofill" : ""}" hidden data-platform=${this.options.platform}>
    <div class="tooltip tooltip--data">
        <button class="tooltip__button tooltip__button--data tooltip__button--credentials-import js-promo-wrapper">
            <span class="tooltip__button__text-container">
                <span class="label label--medium">${t("autofill:credentialsImportHeading")}</span>
                <span class="label label--small">${t("autofill:credentialsImportText")}</span>
            </span>
        </button>
        <hr />
        <button class="tooltip__button tooltip__button--secondary js-dismiss">
            <span class="tooltip__button__text-container">
                <span class="label label--medium">${t("autofill:dontShowAgain")}</span>
            </span>
        </button>
    </div>
</div>
`;
      this.tooltip = this.shadow.querySelector(".tooltip");
      this.buttonWrapper = this.shadow.querySelector(".js-promo-wrapper");
      this.dismissWrapper = this.shadow.querySelector(".js-dismiss");
      this.registerClickableButton(this.buttonWrapper, () => {
        callbacks.onStarted();
      });
      this.registerClickableButton(this.dismissWrapper, () => {
        callbacks.onDismissed();
      });
      this.init();
      return this;
    }
  };

  // src/UI/controllers/HTMLTooltipUIController.js
  var HTMLTooltipUIController = class extends UIController {
    /**
     * @param {HTMLTooltipControllerOptions} options
     * @param {Partial<import('../HTMLTooltip.js').HTMLTooltipOptions>} htmlTooltipOptions
     */
    constructor(options, htmlTooltipOptions = defaultOptions) {
      super();
      /** @type {import("../HTMLTooltip.js").HTMLTooltip | null} */
      __publicField(this, "_activeTooltip", null);
      /** @type {HTMLTooltipControllerOptions} */
      __publicField(this, "_options");
      /** @type {import('../HTMLTooltip.js').HTMLTooltipOptions} */
      __publicField(this, "_htmlTooltipOptions");
      /**
       * Overwritten when calling createTooltip
       * @type {import('../../Form/matching').SupportedTypes}
       */
      __publicField(this, "_activeInputType", "unknown");
      __publicField(this, "_activeInput");
      __publicField(this, "_activeInputOriginalAutocomplete");
      this._options = options;
      this._htmlTooltipOptions = Object.assign({}, defaultOptions, htmlTooltipOptions);
      if (options.device.globalConfig.isTopFrame) {
        window.addEventListener("pointerup", this, true);
      } else {
        window.addEventListener("pointerdown", this, true);
      }
    }
    /**
     * Cleans up after this UI controller by removing the tooltip and all
     * listeners.
     */
    destroy() {
      this.removeTooltip();
      window.removeEventListener("pointerdown", this, true);
      window.removeEventListener("pointerup", this, true);
    }
    /**
     * @param {import('./UIController').AttachTooltipArgs} args
     */
    attachTooltip(args) {
      if (this.getActiveTooltip()) {
        return;
      }
      const { topContextData, getPosition, input, form } = args;
      const tooltip = this.createTooltip(getPosition, topContextData);
      this.setActiveTooltip(tooltip);
      form.showingTooltip(input);
      this._activeInput = input;
      this._activeInputOriginalAutocomplete = input.getAttribute("autocomplete");
      input.setAttribute("autocomplete", "off");
    }
    /**
     * Actually create the HTML Tooltip
     * @param {import('../interfaces.js').PosFn} getPosition
     * @param {TopContextData} topContextData
     * @return {import("../HTMLTooltip").HTMLTooltip}
     */
    createTooltip(getPosition, topContextData) {
      this._attachListeners();
      const config = getInputConfigFromType(topContextData.inputType);
      this._activeInputType = topContextData.inputType;
      const tooltipOptions = {
        ...this._htmlTooltipOptions,
        remove: () => this.removeTooltip(),
        isIncontextSignupAvailable: () => {
          const subtype = getSubtypeFromType(topContextData.inputType);
          return !!this._options.device.inContextSignup?.isAvailable(subtype);
        }
      };
      const hasNoCredentialsData = this._options.device.getLocalCredentials().length === 0;
      if (topContextData.credentialsImport && hasNoCredentialsData) {
        this._options.device.firePixel({ pixelName: "autofill_import_credentials_prompt_shown" });
        return new CredentialsImportTooltip(topContextData.inputType, getPosition, tooltipOptions).render(this._options.device, {
          onStarted: () => {
            this._options.device.credentialsImport.started();
          },
          onDismissed: () => {
            this._options.device.credentialsImport.dismissed();
          }
        });
      }
      if (this._options.tooltipKind === "legacy") {
        this._options.device.firePixel({ pixelName: "autofill_show" });
        return new EmailHTMLTooltip(topContextData.inputType, getPosition, tooltipOptions).render(this._options.device);
      }
      if (this._options.tooltipKind === "emailsignup") {
        this._options.device.firePixel({ pixelName: "incontext_show" });
        return new EmailSignupHTMLTooltip(topContextData.inputType, getPosition, tooltipOptions).render(this._options.device);
      }
      const data = this._dataForAutofill(config, topContextData.inputType, topContextData);
      const asRenderers = data.map((d) => config.tooltipItem(d));
      return new DataHTMLTooltip(topContextData.inputType, getPosition, tooltipOptions).render(
        this._options.device,
        config,
        asRenderers,
        {
          onSelect: (id) => {
            this._onSelect(topContextData.inputType, data, id);
          },
          onManage: (type) => {
            this._onManage(type);
          },
          onIncontextSignupDismissed: (flags) => {
            this._onIncontextSignupDismissed(flags);
          },
          onIncontextSignup: () => {
            this._onIncontextSignup();
          }
        }
      );
    }
    updateItems(data) {
      if (this._activeInputType === "unknown") return;
      const config = getInputConfigFromType(this._activeInputType);
      const asRenderers = data.map((d) => config.tooltipItem(d));
      const activeTooltip = this.getActiveTooltip();
      if (activeTooltip instanceof DataHTMLTooltip) {
        activeTooltip?.render(this._options.device, config, asRenderers, {
          onSelect: (id) => {
            this._onSelect(this._activeInputType, data, id);
          },
          onManage: (type) => {
            this._onManage(type);
          },
          onIncontextSignupDismissed: (flags) => {
            this._onIncontextSignupDismissed(flags);
          },
          onIncontextSignup: () => {
            this._onIncontextSignup();
          }
        });
      }
      setTimeout(() => {
        this.getActiveTooltip()?.setSize();
      }, 10);
    }
    _attachListeners() {
      window.addEventListener("input", this);
      window.addEventListener("keydown", this, true);
    }
    _removeListeners() {
      window.removeEventListener("input", this);
      window.removeEventListener("keydown", this, true);
    }
    handleEvent(event) {
      switch (event.type) {
        case "keydown":
          if (["Escape", "Tab", "Enter"].includes(event.code)) {
            if (event.code === "Escape") {
              event.preventDefault();
              event.stopImmediatePropagation();
            }
            this.removeTooltip();
          }
          break;
        case "input":
          this.removeTooltip();
          break;
        case "pointerdown": {
          this._pointerDownListener(event);
          break;
        }
        case "pointerup": {
          this._pointerUpListener(event);
          break;
        }
      }
    }
    // Global listener for event delegation
    _pointerDownListener(e) {
      if (!e.isTrusted) return;
      if (isEventWithinDax(e, e.target)) return;
      if (e.target.nodeName === "DDG-AUTOFILL") {
        this._handleClickInTooltip(e);
      } else {
        this.removeTooltip().catch((e2) => {
          console.error("error removing tooltip", e2);
        });
      }
    }
    // Global listener for event delegation
    _pointerUpListener(e) {
      if (!e.isTrusted) return;
      if (isEventWithinDax(e, e.target)) return;
      if (e.target.nodeName === "DDG-AUTOFILL") {
        this._handleClickInTooltip(e);
      }
    }
    _handleClickInTooltip(e) {
      e.preventDefault();
      e.stopImmediatePropagation();
      const isMainMouseButton = e.button === 0;
      if (!isMainMouseButton) return;
      const activeTooltip = this.getActiveTooltip();
      activeTooltip?.dispatchClick();
    }
    async removeTooltip(_via) {
      this._htmlTooltipOptions.remove();
      if (this._activeTooltip) {
        this._removeListeners();
        this._activeTooltip.remove();
        this._activeTooltip = null;
      }
      if (this._activeInput) {
        if (this._activeInputOriginalAutocomplete) {
          this._activeInput.setAttribute("autocomplete", this._activeInputOriginalAutocomplete);
        } else {
          this._activeInput.removeAttribute("autocomplete");
        }
        this._activeInput = null;
        this._activeInputOriginalAutocomplete = null;
      }
    }
    /**
     * @returns {import("../HTMLTooltip.js").HTMLTooltip|null}
     */
    getActiveTooltip() {
      return this._activeTooltip;
    }
    /**
     * @param {import("../HTMLTooltip.js").HTMLTooltip} value
     */
    setActiveTooltip(value) {
      this._activeTooltip = value;
    }
    /**
     * Collect the data that's needed to populate the Autofill UI.
     *
     * Note: ideally we'd pass this data instead, so that we didn't have a circular dependency
     *
     * @param {InputTypeConfigs} config - This is the selected `InputTypeConfig` based on the type of field
     * @param {import('../../Form/matching').SupportedTypes} inputType - The input type for the current field
     * @param {TopContextData} topContextData
     */
    _dataForAutofill(config, inputType, topContextData) {
      return this._options.device.dataForAutofill(config, inputType, topContextData);
    }
    /**
     * When a field is selected, call the `onSelect` method from the device.
     *
     * Note: ideally we'd pass this data instead, so that we didn't have a circular dependency
     *
     * @param {import('../../Form/matching').SupportedTypes} inputType
     * @param {(CreditCardObject | IdentityObject | CredentialsObject)[]} data
     * @param {CreditCardObject['id']|IdentityObject['id']|CredentialsObject['id']} id
     */
    _onSelect(inputType, data, id) {
      return this._options.device.onSelect(inputType, data, id);
    }
    /**
     * Called when clicking on the Manage… button in the html tooltip
     * @param {SupportedMainTypes} type
     * @returns {*}
     * @private
     */
    _onManage(type) {
      switch (type) {
        case "credentials":
          this._options.device.openManagePasswords();
          break;
        case "creditCards":
          this._options.device.openManageCreditCards();
          break;
        case "identities":
          this._options.device.openManageIdentities();
          break;
        default:
      }
      this.removeTooltip();
    }
    _onIncontextSignupDismissed({ hasOtherOptions }) {
      this._options.device.inContextSignup?.onIncontextSignupDismissed({ shouldHideTooltip: !hasOtherOptions });
      if (hasOtherOptions) {
        const topContextData = this._options.device.getTopContextData();
        if (!topContextData) return;
        const config = getInputConfigFromType(topContextData.inputType);
        const data = this._dataForAutofill(config, topContextData.inputType, topContextData);
        this.updateItems(data);
      }
    }
    _onIncontextSignup() {
      this._options.device.inContextSignup?.onIncontextSignup();
    }
    isActive() {
      return Boolean(this.getActiveTooltip());
    }
  };

  // src/DeviceInterface/ExtensionInterface.js
  var TOOLTIP_TYPES = {
    EmailProtection: "EmailProtection",
    EmailSignup: "EmailSignup"
  };
  var ExtensionInterface = class extends InterfacePrototype_default {
    constructor() {
      super(...arguments);
      /**
       * Adding this here since only the extension currently supports this
       */
      __publicField(this, "inContextSignup", new InContextSignup(this));
    }
    /**
     * @override
     */
    createUIController() {
      const htmlTooltipOptions = {
        ...defaultOptions,
        platform: "extension",
        css: `<link rel="stylesheet" href="${chrome.runtime.getURL("public/css/autofill.css")}" crossOrigin="anonymous">`,
        testMode: this.isTestMode(),
        hasCaret: true
      };
      const tooltipKinds = {
        [TOOLTIP_TYPES.EmailProtection]: "legacy",
        [TOOLTIP_TYPES.EmailSignup]: "emailsignup"
      };
      const tooltipKind = tooltipKinds[this.getActiveTooltipType()] || tooltipKinds[TOOLTIP_TYPES.EmailProtection];
      return new HTMLTooltipUIController({ tooltipKind, device: this }, htmlTooltipOptions);
    }
    getActiveTooltipType() {
      if (this.hasLocalAddresses) {
        return TOOLTIP_TYPES.EmailProtection;
      }
      const inputType = this.activeForm?.activeInput ? getInputSubtype(this.activeForm.activeInput) : void 0;
      if (this.inContextSignup?.isAvailable(inputType)) {
        return TOOLTIP_TYPES.EmailSignup;
      }
      return null;
    }
    async resetAutofillUI(callback) {
      this.removeAutofillUIFromPage("Resetting autofill.");
      await this.setupAutofill();
      if (callback) await callback();
      this.uiController = this.createUIController();
      await this.postInit();
    }
    isDeviceSignedIn() {
      return this.hasLocalAddresses;
    }
    async setupAutofill() {
      await this.inContextSignup.init();
      return this.getAddresses();
    }
    postInit() {
      switch (this.getActiveTooltipType()) {
        case TOOLTIP_TYPES.EmailProtection: {
          this._scannerCleanup = this.scanner.init();
          this.addLogoutListener(() => {
            this.resetAutofillUI();
            if (this.globalConfig.isDDGDomain) {
              notifyWebApp({ deviceSignedIn: { value: false } });
            }
          });
          if (this.activeForm?.activeInput) {
            this.attachTooltip({
              form: this.activeForm,
              input: this.activeForm?.activeInput,
              click: null,
              trigger: "postSignup",
              triggerMetaData: {
                type: "transactional"
              }
            });
          }
          break;
        }
        case TOOLTIP_TYPES.EmailSignup: {
          this._scannerCleanup = this.scanner.init();
          break;
        }
        default: {
          break;
        }
      }
    }
    getAddresses() {
      return new Promise(
        (resolve) => chrome.runtime.sendMessage({ getAddresses: true }, (data) => {
          this.storeLocalAddresses(data);
          return resolve(data);
        })
      );
    }
    /**
     * Used by the email web app
     * Settings page displays data of the logged in user data
     */
    getUserData() {
      return new Promise((resolve) => chrome.runtime.sendMessage({ getUserData: true }, (data) => resolve(data)));
    }
    /**
     * Used by the email web app
     * Device capabilities determine which functionality is available to the user
     */
    getEmailProtectionCapabilities() {
      return new Promise((resolve) => chrome.runtime.sendMessage({ getEmailProtectionCapabilities: true }, (data) => resolve(data)));
    }
    refreshAlias() {
      return chrome.runtime.sendMessage({ refreshAlias: true }, (addresses) => this.storeLocalAddresses(addresses));
    }
    async trySigningIn() {
      if (this.globalConfig.isDDGDomain) {
        const data = await sendAndWaitForAnswer(SIGN_IN_MSG, "addUserData");
        this.storeUserData(data);
      }
    }
    /**
     * @param {object} message
     * @param {object} message.addUserData
     * @param {string} message.addUserData.token
     * @param {string} message.addUserData.userName
     * @param {string} message.addUserData.cohort
     */
    storeUserData(message) {
      return chrome.runtime.sendMessage(message);
    }
    /**
     * Used by the email web app
     * Provides functionality to log the user out
     */
    removeUserData() {
      return chrome.runtime.sendMessage({ removeUserData: true });
    }
    addDeviceListeners() {
      let activeEl = null;
      document.addEventListener("contextmenu", (e) => {
        activeEl = e.target;
      });
      chrome.runtime.onMessage.addListener((message, sender) => {
        if (sender.id !== chrome.runtime.id) return;
        switch (message.type) {
          case "ddgUserReady":
            this.resetAutofillUI(() => this.setupSettingsPage({ shouldLog: true }));
            break;
          case "contextualAutofill":
            setValue(activeEl, formatDuckAddress(message.alias), this.globalConfig);
            activeEl.classList.add("ddg-autofilled");
            this.refreshAlias();
            activeEl.addEventListener("input", (e) => e.target.classList.remove("ddg-autofilled"), { once: true });
            break;
          default:
            break;
        }
      });
    }
    addLogoutListener(handler) {
      if (this._logoutListenerHandler) {
        chrome.runtime.onMessage.removeListener(this._logoutListenerHandler);
      }
      this._logoutListenerHandler = (message, sender) => {
        if (sender.id === chrome.runtime.id && message.type === "logout") {
          handler();
        }
      };
      chrome.runtime.onMessage.addListener(this._logoutListenerHandler);
    }
  };

  // src/UI/controllers/OverlayUIController.js
  var _state;
  var OverlayUIController = class extends UIController {
    /**
     * @param {OverlayControllerOptions} options
     */
    constructor(options) {
      super();
      /** @type {"idle" | "parentShown"} */
      __privateAdd(this, _state, "idle");
      /** @type {import('../HTMLTooltip.js').HTMLTooltip | null} */
      __publicField(this, "_activeTooltip", null);
      /**
       * @type {OverlayControllerOptions}
       */
      __publicField(this, "_options");
      this._options = options;
      window.addEventListener("pointerdown", this, true);
    }
    /**
     * @param {import('./UIController').AttachTooltipArgs} args
     */
    attachTooltip(args) {
      const { getPosition, topContextData, click, input } = args;
      if (!input.parentNode) return;
      this._mutObs = new MutationObserver((mutationList) => {
        for (const mutationRecord of mutationList) {
          mutationRecord.removedNodes.forEach((el) => {
            if (el.contains(input)) {
              this.removeTooltip("mutation observer");
            }
          });
        }
      });
      this._mutObs.observe(document.body, { childList: true, subtree: true });
      const position = getPosition();
      if (!click && !this.elementIsInViewport(position)) {
        input.scrollIntoView(true);
        this._mutObs?.disconnect();
        setTimeout(() => {
          this.attachTooltip(args);
        }, 50);
        return;
      }
      __privateSet(this, _state, "parentShown");
      this.showTopTooltip(click, position, topContextData).catch((e) => {
        console.error("error from showTopTooltip", e);
        __privateSet(this, _state, "idle");
      });
    }
    /**
     * @param {{ x: number; y: number; height: number; width: number; }} inputDimensions
     * @returns {boolean}
     */
    elementIsInViewport(inputDimensions) {
      if (inputDimensions.x < 0 || inputDimensions.y < 0 || inputDimensions.x + inputDimensions.width > document.documentElement.clientWidth || inputDimensions.y + inputDimensions.height > document.documentElement.clientHeight) {
        return false;
      }
      const viewport = document.documentElement;
      if (inputDimensions.x + inputDimensions.width > viewport.clientWidth || inputDimensions.y + inputDimensions.height > viewport.clientHeight) {
        return false;
      }
      return true;
    }
    /**
     * @param {{ x: number; y: number; } | null} click
     * @param {{ x: number; y: number; height: number; width: number; }} inputDimensions
     * @param {TopContextData} data
     */
    async showTopTooltip(click, inputDimensions, data) {
      let diffX = inputDimensions.x;
      let diffY = inputDimensions.y;
      if (click) {
        diffX -= click.x;
        diffY -= click.y;
      } else if (!this.elementIsInViewport(inputDimensions)) {
        return;
      }
      if (!data.inputType) {
        throw new Error("No input type found");
      }
      const mainType = getMainTypeFromType(data.inputType);
      const subType = getSubtypeFromType(data.inputType);
      if (mainType === "unknown") {
        throw new Error('unreachable, should not be here if (mainType === "unknown")');
      }
      const details = {
        inputType: data.inputType,
        mainType,
        subType,
        serializedInputContext: JSON.stringify(data),
        triggerContext: {
          wasFromClick: Boolean(click),
          inputTop: Math.floor(diffY),
          inputLeft: Math.floor(diffX),
          inputHeight: Math.floor(inputDimensions.height),
          inputWidth: Math.floor(inputDimensions.width)
        }
      };
      try {
        __privateSet(this, _state, "parentShown");
        this._attachListeners();
        await this._options.show(details);
      } catch (e) {
        console.error("could not show parent", e);
        __privateSet(this, _state, "idle");
      }
    }
    _attachListeners() {
      window.addEventListener("scroll", this);
      window.addEventListener("keydown", this, true);
      window.addEventListener("input", this);
    }
    _removeListeners() {
      window.removeEventListener("scroll", this);
      window.removeEventListener("keydown", this, true);
      window.removeEventListener("input", this);
    }
    handleEvent(event) {
      switch (event.type) {
        case "scroll": {
          this.removeTooltip(event.type);
          break;
        }
        case "keydown": {
          if (["Escape", "Tab", "Enter"].includes(event.code)) {
            if (event.code === "Escape") {
              event.preventDefault();
              event.stopImmediatePropagation();
            }
            this.removeTooltip(event.type);
          }
          break;
        }
        case "input": {
          this.removeTooltip(event.type);
          break;
        }
        case "pointerdown": {
          this.removeTooltip(event.type);
          break;
        }
      }
    }
    /**
     * @param {string} trigger
     * @returns {Promise<void>}
     */
    async removeTooltip(trigger) {
      if (trigger !== "pointerdown") {
        if (__privateGet(this, _state) !== "parentShown") {
          return;
        }
      }
      try {
        await this._options.remove();
      } catch (e) {
        console.error("Could not close parent", e);
      }
      __privateSet(this, _state, "idle");
      this._removeListeners();
      this._mutObs?.disconnect();
    }
    isActive() {
      return __privateGet(this, _state) === "parentShown";
    }
  };
  _state = new WeakMap();

  // src/deviceApiCalls/additionalDeviceApiCalls.js
  var GetAlias = class extends DeviceApiCall {
    constructor() {
      super(...arguments);
      __publicField(this, "method", "emailHandlerGetAlias");
      __publicField(this, "id", "n/a");
      __publicField(this, "paramsValidator", getAliasParamsSchema);
      __publicField(this, "resultValidator", getAliasResultSchema);
    }
    preResultValidation(response) {
      return { success: response };
    }
  };

  // src/ThirdPartyProvider.js
  var ThirdPartyProvider = class {
    /**
     * @param {import("./DeviceInterface/InterfacePrototype").default} device
     */
    constructor(device) {
      this.device = device;
    }
    init() {
      if (this.device.settings.featureToggles.third_party_credentials_provider) {
        if (this.device.globalConfig.hasModernWebkitAPI) {
          Object.defineProperty(window, "providerStatusUpdated", {
            enumerable: false,
            configurable: false,
            writable: false,
            value: (data) => {
              this.providerStatusUpdated(data);
            }
          });
        } else {
          setTimeout(() => this._pollForUpdatesToCredentialsProvider(), 2e3);
        }
      }
    }
    async askToUnlockProvider() {
      const response = await this.device.deviceApi.request(new AskToUnlockProviderCall(null));
      this.providerStatusUpdated(response);
    }
    /**
     * Called by the native layer on all tabs when the provider status is updated
     * @param {import("./deviceApiCalls/__generated__/validators-ts").ProviderStatusUpdated} data
     */
    providerStatusUpdated(data) {
      try {
        const { credentials, availableInputTypes } = validate(data, providerStatusUpdatedSchema);
        this.device.settings.setAvailableInputTypes(availableInputTypes);
        this.device.storeLocalCredentials(credentials);
        this.device.uiController?.updateItems(credentials);
        if (!this.device.globalConfig.isTopFrame) {
          const currentInputSubtype = getSubtypeFromType(this.device.getCurrentInputType());
          if (!availableInputTypes.credentials?.[currentInputSubtype]) {
            this.device.removeTooltip();
          }
          this.device.scanner.forms.forEach((form) => form.recategorizeAllInputs());
        }
      } catch (e) {
        if (this.device.globalConfig.isDDGTestMode) {
          console.log("isDDGTestMode: providerStatusUpdated error: \u274C", e);
        }
      }
    }
    // Only used on Catalina
    async _pollForUpdatesToCredentialsProvider() {
      try {
        const response = await this.device.deviceApi.request(new CheckCredentialsProviderStatusCall(null));
        if (response.availableInputTypes.credentialsProviderStatus !== this.device.settings.availableInputTypes.credentialsProviderStatus) {
          this.providerStatusUpdated(response);
        }
        setTimeout(() => this._pollForUpdatesToCredentialsProvider(), 2e3);
      } catch (e) {
        if (this.device.globalConfig.isDDGTestMode) {
          console.log("isDDGTestMode: _pollForUpdatesToCredentialsProvider: \u274C", e);
        }
      }
    }
  };

  // src/DeviceInterface/AppleDeviceInterface.js
  var AppleDeviceInterface = class extends InterfacePrototype_default {
    constructor() {
      super(...arguments);
      __publicField(this, "inContextSignup", new InContextSignup(this));
      /** @override */
      __publicField(this, "initialSetupDelayMs", 300);
      __publicField(this, "thirdPartyProvider", new ThirdPartyProvider(this));
      /** @type {any} */
      __publicField(this, "pollingTimeout", null);
    }
    /**
     * The default functionality of this class is to operate as an 'overlay controller' -
     * which means it's purpose is to message the native layer about when to open/close the overlay.
     *
     * There is an additional use-case though, when running on older macOS versions, we just display the
     * HTMLTooltip in-page (like the extension does). This is why the `!this.globalConfig.supportsTopFrame`
     * check exists below - if we know we don't support the overlay, we fall back to in-page.
     *
     * @override
     * @returns {import("../UI/controllers/UIController.js").UIController}
     */
    createUIController() {
      if (this.globalConfig.userPreferences?.platform?.name === "ios") {
        return new NativeUIController();
      }
      if (!this.globalConfig.supportsTopFrame) {
        const options = {
          ...defaultOptions,
          platform: "macos",
          testMode: this.isTestMode()
        };
        return new HTMLTooltipUIController(
          {
            device: this,
            tooltipKind: "modern"
          },
          options
        );
      }
      return new OverlayUIController({
        remove: async () => this._closeAutofillParent(),
        show: async (details) => this._show(details)
      });
    }
    /**
     * For now, this could be running
     *  1) on iOS
     *  2) on macOS + Overlay
     *  3) on macOS + in-page HTMLTooltip
     *
     * @override
     * @returns {Promise<void>}
     */
    async setupAutofill() {
      if (!this.globalConfig.supportsTopFrame) {
        await this._getAutofillInitData();
      }
      await this.inContextSignup.init();
      const signedIn = await this._checkDeviceSignedIn();
      if (signedIn) {
        if (this.globalConfig.isApp) {
          await this.getAddresses();
        }
      }
    }
    /**
     * Used by the email web app
     * Settings page displays data of the logged in user data
     */
    getUserData() {
      return this.deviceApi.request(createRequest("emailHandlerGetUserData"));
    }
    /**
     * Used by the email web app
     * Device capabilities determine which functionality is available to the user
     */
    getEmailProtectionCapabilities() {
      return this.deviceApi.request(createRequest("emailHandlerGetCapabilities"));
    }
    /**
     */
    async getSelectedCredentials() {
      return this.deviceApi.request(createRequest("getSelectedCredentials"));
    }
    /**
     * The data format provided here for `parentArgs` matches Window now.
     * @param {GetAutofillDataRequest} parentArgs
     */
    async _showAutofillParent(parentArgs) {
      const applePayload = {
        ...parentArgs.triggerContext,
        serializedInputContext: parentArgs.serializedInputContext
      };
      return this.deviceApi.notify(createNotification("showAutofillParent", applePayload));
    }
    /**
     * @returns {Promise<any>}
     */
    async _closeAutofillParent() {
      return this.deviceApi.notify(createNotification("closeAutofillParent", {}));
    }
    /**
     * @param {GetAutofillDataRequest} details
     */
    async _show(details) {
      await this._showAutofillParent(details);
      this._listenForSelectedCredential(async (response) => {
        if (!response) return;
        if ("configType" in response) {
          this.selectedDetail(response.data, response.configType);
        } else if ("stop" in response) {
          await this.onFinishedAutofill();
        } else if ("stateChange" in response) {
          await this.updateForStateChange();
        }
      });
    }
    async refreshData() {
      await super.refreshData();
      await this._checkDeviceSignedIn();
    }
    async getAddresses() {
      if (!this.globalConfig.isApp) return this.getAlias();
      const { addresses } = await this.deviceApi.request(createRequest("emailHandlerGetAddresses"));
      this.storeLocalAddresses(addresses);
      return addresses;
    }
    async refreshAlias() {
      await this.deviceApi.notify(createNotification("emailHandlerRefreshAlias"));
      if (this.globalConfig.isApp) this.getAddresses();
    }
    async _checkDeviceSignedIn() {
      const { isAppSignedIn } = await this.deviceApi.request(createRequest("emailHandlerCheckAppSignedInStatus"));
      this.isDeviceSignedIn = () => !!isAppSignedIn;
      return !!isAppSignedIn;
    }
    storeUserData({ addUserData: { token, userName, cohort } }) {
      return this.deviceApi.notify(createNotification("emailHandlerStoreToken", { token, username: userName, cohort }));
    }
    /**
     * Used by the email web app
     * Provides functionality to log the user out
     */
    removeUserData() {
      this.deviceApi.notify(createNotification("emailHandlerRemoveToken"));
    }
    /**
     * Used by the email web app
     * Provides functionality to close the window after in-context sign-up or sign-in
     */
    closeEmailProtection() {
      this.deviceApi.request(new CloseEmailProtectionTabCall(null));
    }
    /**
     * PM endpoints
     */
    /**
     * Gets the init data from the device
     * @returns {APIResponse<PMData>}
     */
    async _getAutofillInitData() {
      const response = await this.deviceApi.request(createRequest("pmHandlerGetAutofillInitData"));
      this.storeLocalData(response.success);
      return response;
    }
    /**
     * Gets credentials ready for autofill
     * @param {CredentialsObject['id']} id - the credential id
     * @returns {APIResponseSingle<CredentialsObject>}
     */
    getAutofillCredentials(id) {
      return this.deviceApi.request(createRequest("pmHandlerGetAutofillCredentials", { id }));
    }
    /**
     * Opens the native UI for managing passwords
     */
    openManagePasswords() {
      return this.deviceApi.notify(createNotification("pmHandlerOpenManagePasswords"));
    }
    /**
     * Opens the native UI for managing identities
     */
    openManageIdentities() {
      return this.deviceApi.notify(createNotification("pmHandlerOpenManageIdentities"));
    }
    /**
     * Opens the native UI for managing credit cards
     */
    openManageCreditCards() {
      return this.deviceApi.notify(createNotification("pmHandlerOpenManageCreditCards"));
    }
    /**
     * Gets a single identity obj once the user requests it
     * @param {IdentityObject['id']} id
     * @returns {Promise<{success: IdentityObject|undefined}>}
     */
    getAutofillIdentity(id) {
      const identity = this.getLocalIdentities().find(({ id: identityId }) => `${identityId}` === `${id}`);
      return Promise.resolve({ success: identity });
    }
    /**
     * Gets a single complete credit card obj once the user requests it
     * @param {CreditCardObject['id']} id
     * @returns {APIResponseSingle<CreditCardObject>}
     */
    getAutofillCreditCard(id) {
      return this.deviceApi.request(createRequest("pmHandlerGetCreditCard", { id }));
    }
    getCurrentInputType() {
      const topContextData = this.getTopContextData();
      return topContextData?.inputType ? topContextData.inputType : getInputType(this.activeForm?.activeInput);
    }
    /**
     * @returns {Promise<string|undefined>}
     */
    async getAlias() {
      const { alias } = await this.deviceApi.request(
        new GetAlias({
          requiresUserPermission: !this.globalConfig.isApp,
          shouldConsumeAliasIfProvided: !this.globalConfig.isApp,
          isIncontextSignupAvailable: this.inContextSignup.isAvailable()
        })
      );
      return alias ? formatDuckAddress(alias) : alias;
    }
    addLogoutListener(handler) {
      if (!this.globalConfig.isDDGDomain) return;
      window.addEventListener("message", (e) => {
        if (this.globalConfig.isDDGDomain && e.data.emailProtectionSignedOut) {
          handler();
        }
      });
    }
    async addDeviceListeners() {
      this.thirdPartyProvider.init();
      this.credentialsImport.init();
    }
    /**
     * Poll the native listener until the user has selected a credential.
     * Message return types are:
     * - 'stop' is returned whenever the message sent doesn't match the native last opened tooltip.
     *     - This also is triggered when the close event is called and prevents any edge case continued polling.
     * - 'ok' is when the user has selected a credential and the value can be injected into the page.
     * - 'none' is when the tooltip is open in the native window however hasn't been entered.
     * @param {(response: {data:IdentityObject|CreditCardObject|CredentialsObject, configType: string} | {stateChange: boolean} | {stop: boolean} | null) => void} callback
     */
    async _listenForSelectedCredential(callback) {
      const poll = async () => {
        clearTimeout(this.pollingTimeout);
        const response = await this.getSelectedCredentials();
        switch (response.type) {
          case "none":
            this.pollingTimeout = setTimeout(() => poll(), 100);
            return;
          case "ok": {
            await callback({ data: response.data, configType: response.configType });
            return;
          }
          case "state": {
            await callback({ stateChange: true });
            this.pollingTimeout = setTimeout(() => poll(), 100);
            return;
          }
          case "stop":
            await callback({ stop: true });
        }
      };
      poll();
    }
  };

  // src/DeviceInterface/overlayApi.js
  function overlayApi(device) {
    return {
      /**
       * When we are inside an 'overlay' - the HTML tooltip will be opened immediately
       */
      showImmediately() {
        const topContextData = device.getTopContextData();
        if (!topContextData) throw new Error("unreachable, topContextData should be available");
        const getPosition = () => {
          return {
            x: 0,
            y: 0,
            height: 50,
            width: 50
          };
        };
        const tooltip = device.uiController?.createTooltip?.(getPosition, topContextData);
        if (tooltip) {
          device.uiController?.setActiveTooltip?.(tooltip);
        }
      },
      /**
       * @param {IdentityObject|CreditCardObject|CredentialsObject|{email:string, id: string}} data
       * @param {string} type
       * @returns {Promise<void>}
       */
      async selectedDetail(data, type) {
        const detailsEntries = Object.entries(data).map(([key2, value]) => {
          return [key2, String(value)];
        });
        const entries = Object.fromEntries(detailsEntries);
        await device.deviceApi.notify(new SelectedDetailCall({ data: entries, configType: type }));
      }
    };
  }

  // src/DeviceInterface/AppleOverlayDeviceInterface.js
  var AppleOverlayDeviceInterface = class extends AppleDeviceInterface {
    constructor() {
      super(...arguments);
      /**
       * Mark top frame as not stripping credential data
       * @type {boolean}
       */
      __publicField(this, "stripCredentials", false);
      /**
       * overlay API helpers
       */
      __publicField(this, "overlay", overlayApi(this));
      __publicField(this, "previousX", 0);
      __publicField(this, "previousY", 0);
    }
    /**
     * Because we're running inside the Overlay, we always create the HTML
     * Tooltip controller.
     *
     * @override
     * @returns {import("../UI/controllers/UIController.js").UIController}
     */
    createUIController() {
      return new HTMLTooltipUIController(
        {
          tooltipKind: (
            /** @type {const} */
            "modern"
          ),
          device: this
        },
        {
          ...defaultOptions,
          platform: "macos",
          wrapperClass: "top-autofill",
          isTopAutofill: true,
          tooltipPositionClass: () => ".wrapper { transform: none; }",
          setSize: (details) => this.deviceApi.notify(createNotification("setSize", details)),
          remove: async () => this._closeAutofillParent(),
          testMode: this.isTestMode()
        }
      );
    }
    async startCredentialsImportFlow() {
      this._closeAutofillParent();
      await this.deviceApi.notify(createNotification("startCredentialsImportFlow"));
    }
    addDeviceListeners() {
      window.addEventListener("mouseMove", (event) => {
        if (!this.previousX && !this.previousY || // if no previous coords
        this.previousX === event.detail.x && this.previousY === event.detail.y) {
          this.previousX = event.detail.x;
          this.previousY = event.detail.y;
          return;
        }
        const activeTooltip = this.uiController?.getActiveTooltip?.();
        activeTooltip?.focus(event.detail.x, event.detail.y);
        this.previousX = event.detail.x;
        this.previousY = event.detail.y;
      });
      return super.addDeviceListeners();
    }
    /**
     * Since we're running inside the Overlay we can limit what happens here to
     * be only things that are needed to power the HTML Tooltip
     *
     * @override
     * @returns {Promise<void>}
     */
    async setupAutofill() {
      await this._getAutofillInitData();
      await this.inContextSignup.init();
      const signedIn = await this._checkDeviceSignedIn();
      if (signedIn) {
        await this.getAddresses();
      }
    }
    async postInit() {
      this.overlay.showImmediately();
      super.postInit();
    }
    /**
     * In the top-frame scenario we override the base 'selectedDetail'.
     *
     * This
     *
     * @override
     * @param {IdentityObject|CreditCardObject|CredentialsObject|{email:string, id: string}} data
     * @param {string} type
     */
    async selectedDetail(data, type) {
      return this.overlay.selectedDetail(data, type);
    }
  };

  // src/DeviceInterface/WindowsInterface.js
  var EMAIL_PROTECTION_LOGOUT_MESSAGE = "EMAIL_PROTECTION_LOGOUT";
  var WindowsInterface = class extends InterfacePrototype_default {
    constructor() {
      super(...arguments);
      __publicField(this, "ready", false);
      /** @type {AbortController|null} */
      __publicField(this, "_abortController", null);
    }
    async setupAutofill() {
      const loggedIn = await this._getIsLoggedIn();
      if (loggedIn) {
        await this.getAddresses();
      }
    }
    postInit() {
      super.postInit();
      this.ready = true;
    }
    createUIController() {
      return new OverlayUIController({
        remove: async () => this._closeAutofillParent(),
        show: async (details) => this._show(details)
      });
    }
    /**
     * @param {GetAutofillDataRequest} details
     */
    async _show(details) {
      const { mainType } = details;
      if (this._abortController && !this._abortController.signal.aborted) {
        this._abortController.abort();
      }
      this._abortController = new AbortController();
      try {
        const resp = await this.deviceApi.request(new GetAutofillDataCall(details), { signal: this._abortController.signal });
        if (!this.activeForm) {
          throw new Error("this.currentAttached was absent");
        }
        switch (resp.action) {
          case "fill": {
            if (mainType in resp) {
              this.activeForm?.autofillData(resp[mainType], mainType);
            } else {
              throw new Error(`action: "fill" cannot occur because "${mainType}" was missing`);
            }
            break;
          }
          case "focus": {
            this.activeForm?.activeInput?.focus();
            break;
          }
          case "none": {
            break;
          }
          case "refreshAvailableInputTypes": {
            await this.removeTooltip();
            return await this.credentialsImport.refresh();
          }
          default:
            if (this.globalConfig.isDDGTestMode) {
              console.warn("unhandled response", resp);
            }
            return this._closeAutofillParent();
        }
      } catch (e) {
        if (this.globalConfig.isDDGTestMode) {
          if (e instanceof DOMException && e.name === "AbortError") {
            console.log("Promise Aborted");
          } else {
            console.error("Promise Rejected", e);
          }
        }
      }
    }
    /**
     * @returns {Promise<any>}
     */
    async _closeAutofillParent() {
      return this.deviceApi.notify(new CloseAutofillParentCall(null));
    }
    /**
     * Email Protection calls
     */
    /**
     * @returns {Promise<any>}
     */
    getEmailProtectionCapabilities() {
      return this.deviceApi.request(new EmailProtectionGetCapabilitiesCall({}));
    }
    async _getIsLoggedIn() {
      const isLoggedIn = await this.deviceApi.request(new EmailProtectionGetIsLoggedInCall({}));
      this.isDeviceSignedIn = () => isLoggedIn;
      return isLoggedIn;
    }
    addLogoutListener(handler) {
      if (!this.globalConfig.isDDGDomain) return;
      windowsInteropAddEventListener("message", (e) => {
        if (this.globalConfig.isDDGDomain && e.data === EMAIL_PROTECTION_LOGOUT_MESSAGE) {
          handler();
        }
      });
    }
    /**
     * @returns {Promise<any>}
     */
    storeUserData({ addUserData }) {
      return this.deviceApi.request(new EmailProtectionStoreUserDataCall(addUserData));
    }
    /**
     * @returns {Promise<any>}
     */
    removeUserData() {
      return this.deviceApi.request(new EmailProtectionRemoveUserDataCall({}));
    }
    /**
     * @returns {Promise<any>}
     */
    getUserData() {
      return this.deviceApi.request(new EmailProtectionGetUserDataCall({}));
    }
    async refreshAlias() {
      const addresses = await this.deviceApi.request(new EmailProtectionRefreshPrivateAddressCall({}));
      this.storeLocalAddresses(addresses);
    }
    async getAddresses() {
      const addresses = await this.deviceApi.request(new EmailProtectionGetAddressesCall({}));
      this.storeLocalAddresses(addresses);
      return addresses;
    }
  };

  // src/DeviceInterface/WindowsOverlayDeviceInterface.js
  var WindowsOverlayDeviceInterface = class extends InterfacePrototype_default {
    constructor() {
      super(...arguments);
      /**
       * Mark top frame as not stripping credential data
       * @type {boolean}
       */
      __publicField(this, "stripCredentials", false);
      /**
       * overlay API helpers
       */
      __publicField(this, "overlay", overlayApi(this));
      __publicField(this, "previousScreenX", 0);
      __publicField(this, "previousScreenY", 0);
    }
    /**
     * Because we're running inside the Overlay, we always create the HTML
     * Tooltip controller.
     *
     * @override
     * @returns {import("../UI/controllers/UIController.js").UIController}
     */
    createUIController() {
      return new HTMLTooltipUIController(
        {
          tooltipKind: (
            /** @type {const} */
            "modern"
          ),
          device: this
        },
        {
          ...defaultOptions,
          platform: "windows",
          wrapperClass: "top-autofill",
          isTopAutofill: true,
          tooltipPositionClass: () => ".wrapper { transform: none; }",
          setSize: (details) => this.deviceApi.notify(new SetSizeCall(details)),
          remove: async () => this._closeAutofillParent(),
          testMode: this.isTestMode(),
          /**
           * Note: This is needed because Mutation observer didn't support visibility checks on Windows
           */
          checkVisibility: false
        }
      );
    }
    addDeviceListeners() {
      window.addEventListener("mousemove", (event) => {
        if (!this.previousScreenX && !this.previousScreenY || // if no previous coords
        this.previousScreenX === event.screenX && this.previousScreenY === event.screenY) {
          this.previousScreenX = event.screenX;
          this.previousScreenY = event.screenY;
          return;
        }
        const activeTooltip = this.uiController?.getActiveTooltip?.();
        activeTooltip?.focus(event.x, event.y);
        this.previousScreenX = event.screenX;
        this.previousScreenY = event.screenY;
      });
      return super.addDeviceListeners();
    }
    /**
     * @returns {Promise<any>}
     */
    async _closeAutofillParent() {
      return this.deviceApi.notify(new CloseAutofillParentCall(null));
    }
    /**
     * @returns {Promise<any>}
     */
    openManagePasswords() {
      return this.deviceApi.notify(new OpenManagePasswordsCall({}));
    }
    /**
     * @returns {Promise<any>}
     */
    openManageCreditCards() {
      return this.deviceApi.notify(new OpenManageCreditCardsCall({}));
    }
    /**
     * @returns {Promise<any>}
     */
    openManageIdentities() {
      return this.deviceApi.notify(new OpenManageIdentitiesCall({}));
    }
    /**
     * Since we're running inside the Overlay we can limit what happens here to
     * be only things that are needed to power the HTML Tooltip
     *
     * @override
     * @returns {Promise<void>}
     */
    async setupAutofill() {
      const loggedIn = await this._getIsLoggedIn();
      if (loggedIn) {
        await this.getAddresses();
      }
      const response = await this.deviceApi.request(new GetAutofillInitDataCall(null));
      this.storeLocalData(response);
    }
    async postInit() {
      this.overlay.showImmediately();
      super.postInit();
    }
    /**
     * In the top-frame scenario, we send a message to the native
     * side to indicate a selection. Once received, the native side will store that selection so that a
     * subsequence call from main webpage can retrieve it
     *
     * @override
     * @param {IdentityObject|CreditCardObject|CredentialsObject|{email:string, id: string}} data
     * @param {string} type
     */
    async selectedDetail(data, type) {
      return this.overlay.selectedDetail(data, type);
    }
    /**
     * Email Protection calls
     */
    async _getIsLoggedIn() {
      const isLoggedIn = await this.deviceApi.request(new EmailProtectionGetIsLoggedInCall({}));
      this.isDeviceSignedIn = () => isLoggedIn;
      return isLoggedIn;
    }
    async getAddresses() {
      const addresses = await this.deviceApi.request(new EmailProtectionGetAddressesCall({}));
      this.storeLocalAddresses(addresses);
      return addresses;
    }
    /**
     * Gets a single identity obj once the user requests it
     * @param {IdentityObject['id']} id
     * @returns {Promise<{success: IdentityObject|undefined}>}
     */
    async getAutofillIdentity(id) {
      const PRIVATE_ADDRESS_ID = "privateAddress";
      const PERSONAL_ADDRESS_ID = "personalAddress";
      if (id === PRIVATE_ADDRESS_ID || id === PERSONAL_ADDRESS_ID) {
        const identity = this.getLocalIdentities().find(({ id: identityId }) => identityId === id);
        return { success: identity };
      }
      const result = await this.deviceApi.request(new GetIdentityCall({ id }));
      return { success: result };
    }
    /**
     * Gets a single complete credit card obj once the user requests it
     * @param {CreditCardObject['id']} id
     * @returns {APIResponseSingle<CreditCardObject>}
     */
    async getAutofillCreditCard(id) {
      const result = await this.deviceApi.request(new GetCreditCardCall({ id }));
      return { success: result };
    }
  };

  // src/DeviceInterface.js
  function createDevice() {
    const globalConfig = createGlobalConfig();
    const transport = createTransport(globalConfig);
    const loggingTransport = {
      async send(deviceApiCall) {
        console.log("[->outgoing]", "id:", deviceApiCall.method, deviceApiCall.params || null);
        const result = await transport.send(deviceApiCall);
        console.log("[<-incoming]", "id:", deviceApiCall.method, result || null);
        return result;
      }
    };
    const deviceApi = new DeviceApi(globalConfig.isDDGTestMode ? loggingTransport : transport);
    const settings = new Settings(globalConfig, deviceApi);
    if (globalConfig.isWindows) {
      if (globalConfig.isTopFrame) {
        return new WindowsOverlayDeviceInterface(globalConfig, deviceApi, settings);
      }
      return new WindowsInterface(globalConfig, deviceApi, settings);
    }
    if (globalConfig.isDDGApp) {
      if (globalConfig.isAndroid) {
        return new AndroidInterface(globalConfig, deviceApi, settings);
      }
      if (globalConfig.isTopFrame) {
        return new AppleOverlayDeviceInterface(globalConfig, deviceApi, settings);
      }
      return new AppleDeviceInterface(globalConfig, deviceApi, settings);
    }
    globalConfig.isExtension = true;
    return new ExtensionInterface(globalConfig, deviceApi, settings);
  }

  // src/autofill.js
  (() => {
    if (shouldLog()) {
      console.log("DuckDuckGo Autofill Active");
    }
    if (!window.isSecureContext) return false;
    try {
      const startupAutofill = () => {
        if (document.visibilityState === "visible") {
          const deviceInterface = createDevice();
          deviceInterface.init();
        } else {
          document.addEventListener("visibilitychange", startupAutofill, { once: true });
        }
      };
      startupAutofill();
    } catch (e) {
      console.error(e);
    }
  })();
})();
