"use strict";
(() => {
  var __create = Object.create;
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __getProtoOf = Object.getPrototypeOf;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __esm = (fn, res) => function __init() {
    return fn && (res = (0, fn[__getOwnPropNames(fn)[0]])(fn = 0)), res;
  };
  var __commonJS = (cb, mod) => function __require() {
    return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
  };
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
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
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

  // node_modules/tslib/tslib.es6.js
  var tslib_es6_exports = {};
  __export(tslib_es6_exports, {
    __assign: () => __assign,
    __asyncDelegator: () => __asyncDelegator,
    __asyncGenerator: () => __asyncGenerator,
    __asyncValues: () => __asyncValues,
    __await: () => __await,
    __awaiter: () => __awaiter,
    __classPrivateFieldGet: () => __classPrivateFieldGet,
    __classPrivateFieldIn: () => __classPrivateFieldIn,
    __classPrivateFieldSet: () => __classPrivateFieldSet,
    __createBinding: () => __createBinding,
    __decorate: () => __decorate,
    __exportStar: () => __exportStar,
    __extends: () => __extends,
    __generator: () => __generator,
    __importDefault: () => __importDefault,
    __importStar: () => __importStar,
    __makeTemplateObject: () => __makeTemplateObject,
    __metadata: () => __metadata,
    __param: () => __param,
    __read: () => __read,
    __rest: () => __rest,
    __spread: () => __spread,
    __spreadArray: () => __spreadArray,
    __spreadArrays: () => __spreadArrays,
    __values: () => __values
  });
  function __extends(d, b) {
    if (typeof b !== "function" && b !== null)
      throw new TypeError("Class extends value " + String(b) + " is not a constructor or null");
    extendStatics(d, b);
    function __() {
      this.constructor = d;
    }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
  }
  function __rest(s, e) {
    var t = {};
    for (var p in s)
      if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0)
        t[p] = s[p];
    if (s != null && typeof Object.getOwnPropertySymbols === "function")
      for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
        if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i]))
          t[p[i]] = s[p[i]];
      }
    return t;
  }
  function __decorate(decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function")
      r = Reflect.decorate(decorators, target, key, desc);
    else
      for (var i = decorators.length - 1; i >= 0; i--)
        if (d = decorators[i])
          r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
  }
  function __param(paramIndex, decorator) {
    return function(target, key) {
      decorator(target, key, paramIndex);
    };
  }
  function __metadata(metadataKey, metadataValue) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function")
      return Reflect.metadata(metadataKey, metadataValue);
  }
  function __awaiter(thisArg, _arguments, P, generator) {
    function adopt(value) {
      return value instanceof P ? value : new P(function(resolve) {
        resolve(value);
      });
    }
    return new (P || (P = Promise))(function(resolve, reject) {
      function fulfilled(value) {
        try {
          step(generator.next(value));
        } catch (e) {
          reject(e);
        }
      }
      function rejected(value) {
        try {
          step(generator["throw"](value));
        } catch (e) {
          reject(e);
        }
      }
      function step(result) {
        result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected);
      }
      step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
  }
  function __generator(thisArg, body) {
    var _ = { label: 0, sent: function() {
      if (t[0] & 1)
        throw t[1];
      return t[1];
    }, trys: [], ops: [] }, f, y, t, g;
    return g = { next: verb(0), "throw": verb(1), "return": verb(2) }, typeof Symbol === "function" && (g[Symbol.iterator] = function() {
      return this;
    }), g;
    function verb(n) {
      return function(v) {
        return step([n, v]);
      };
    }
    function step(op) {
      if (f)
        throw new TypeError("Generator is already executing.");
      while (_)
        try {
          if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done)
            return t;
          if (y = 0, t)
            op = [op[0] & 2, t.value];
          switch (op[0]) {
            case 0:
            case 1:
              t = op;
              break;
            case 4:
              _.label++;
              return { value: op[1], done: false };
            case 5:
              _.label++;
              y = op[1];
              op = [0];
              continue;
            case 7:
              op = _.ops.pop();
              _.trys.pop();
              continue;
            default:
              if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) {
                _ = 0;
                continue;
              }
              if (op[0] === 3 && (!t || op[1] > t[0] && op[1] < t[3])) {
                _.label = op[1];
                break;
              }
              if (op[0] === 6 && _.label < t[1]) {
                _.label = t[1];
                t = op;
                break;
              }
              if (t && _.label < t[2]) {
                _.label = t[2];
                _.ops.push(op);
                break;
              }
              if (t[2])
                _.ops.pop();
              _.trys.pop();
              continue;
          }
          op = body.call(thisArg, _);
        } catch (e) {
          op = [6, e];
          y = 0;
        } finally {
          f = t = 0;
        }
      if (op[0] & 5)
        throw op[1];
      return { value: op[0] ? op[1] : void 0, done: true };
    }
  }
  function __exportStar(m, o) {
    for (var p in m)
      if (p !== "default" && !Object.prototype.hasOwnProperty.call(o, p))
        __createBinding(o, m, p);
  }
  function __values(o) {
    var s = typeof Symbol === "function" && Symbol.iterator, m = s && o[s], i = 0;
    if (m)
      return m.call(o);
    if (o && typeof o.length === "number")
      return {
        next: function() {
          if (o && i >= o.length)
            o = void 0;
          return { value: o && o[i++], done: !o };
        }
      };
    throw new TypeError(s ? "Object is not iterable." : "Symbol.iterator is not defined.");
  }
  function __read(o, n) {
    var m = typeof Symbol === "function" && o[Symbol.iterator];
    if (!m)
      return o;
    var i = m.call(o), r, ar = [], e;
    try {
      while ((n === void 0 || n-- > 0) && !(r = i.next()).done)
        ar.push(r.value);
    } catch (error) {
      e = { error };
    } finally {
      try {
        if (r && !r.done && (m = i["return"]))
          m.call(i);
      } finally {
        if (e)
          throw e.error;
      }
    }
    return ar;
  }
  function __spread() {
    for (var ar = [], i = 0; i < arguments.length; i++)
      ar = ar.concat(__read(arguments[i]));
    return ar;
  }
  function __spreadArrays() {
    for (var s = 0, i = 0, il = arguments.length; i < il; i++)
      s += arguments[i].length;
    for (var r = Array(s), k = 0, i = 0; i < il; i++)
      for (var a = arguments[i], j = 0, jl = a.length; j < jl; j++, k++)
        r[k] = a[j];
    return r;
  }
  function __spreadArray(to, from, pack) {
    if (pack || arguments.length === 2)
      for (var i = 0, l = from.length, ar; i < l; i++) {
        if (ar || !(i in from)) {
          if (!ar)
            ar = Array.prototype.slice.call(from, 0, i);
          ar[i] = from[i];
        }
      }
    return to.concat(ar || Array.prototype.slice.call(from));
  }
  function __await(v) {
    return this instanceof __await ? (this.v = v, this) : new __await(v);
  }
  function __asyncGenerator(thisArg, _arguments, generator) {
    if (!Symbol.asyncIterator)
      throw new TypeError("Symbol.asyncIterator is not defined.");
    var g = generator.apply(thisArg, _arguments || []), i, q = [];
    return i = {}, verb("next"), verb("throw"), verb("return"), i[Symbol.asyncIterator] = function() {
      return this;
    }, i;
    function verb(n) {
      if (g[n])
        i[n] = function(v) {
          return new Promise(function(a, b) {
            q.push([n, v, a, b]) > 1 || resume(n, v);
          });
        };
    }
    function resume(n, v) {
      try {
        step(g[n](v));
      } catch (e) {
        settle(q[0][3], e);
      }
    }
    function step(r) {
      r.value instanceof __await ? Promise.resolve(r.value.v).then(fulfill, reject) : settle(q[0][2], r);
    }
    function fulfill(value) {
      resume("next", value);
    }
    function reject(value) {
      resume("throw", value);
    }
    function settle(f, v) {
      if (f(v), q.shift(), q.length)
        resume(q[0][0], q[0][1]);
    }
  }
  function __asyncDelegator(o) {
    var i, p;
    return i = {}, verb("next"), verb("throw", function(e) {
      throw e;
    }), verb("return"), i[Symbol.iterator] = function() {
      return this;
    }, i;
    function verb(n, f) {
      i[n] = o[n] ? function(v) {
        return (p = !p) ? { value: __await(o[n](v)), done: n === "return" } : f ? f(v) : v;
      } : f;
    }
  }
  function __asyncValues(o) {
    if (!Symbol.asyncIterator)
      throw new TypeError("Symbol.asyncIterator is not defined.");
    var m = o[Symbol.asyncIterator], i;
    return m ? m.call(o) : (o = typeof __values === "function" ? __values(o) : o[Symbol.iterator](), i = {}, verb("next"), verb("throw"), verb("return"), i[Symbol.asyncIterator] = function() {
      return this;
    }, i);
    function verb(n) {
      i[n] = o[n] && function(v) {
        return new Promise(function(resolve, reject) {
          v = o[n](v), settle(resolve, reject, v.done, v.value);
        });
      };
    }
    function settle(resolve, reject, d, v) {
      Promise.resolve(v).then(function(v2) {
        resolve({ value: v2, done: d });
      }, reject);
    }
  }
  function __makeTemplateObject(cooked, raw) {
    if (Object.defineProperty) {
      Object.defineProperty(cooked, "raw", { value: raw });
    } else {
      cooked.raw = raw;
    }
    return cooked;
  }
  function __importStar(mod) {
    if (mod && mod.__esModule)
      return mod;
    var result = {};
    if (mod != null) {
      for (var k in mod)
        if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k))
          __createBinding(result, mod, k);
    }
    __setModuleDefault(result, mod);
    return result;
  }
  function __importDefault(mod) {
    return mod && mod.__esModule ? mod : { default: mod };
  }
  function __classPrivateFieldGet(receiver, state, kind, f) {
    if (kind === "a" && !f)
      throw new TypeError("Private accessor was defined without a getter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver))
      throw new TypeError("Cannot read private member from an object whose class did not declare it");
    return kind === "m" ? f : kind === "a" ? f.call(receiver) : f ? f.value : state.get(receiver);
  }
  function __classPrivateFieldSet(receiver, state, value, kind, f) {
    if (kind === "m")
      throw new TypeError("Private method is not writable");
    if (kind === "a" && !f)
      throw new TypeError("Private accessor was defined without a setter");
    if (typeof state === "function" ? receiver !== state || !f : !state.has(receiver))
      throw new TypeError("Cannot write private member to an object whose class did not declare it");
    return kind === "a" ? f.call(receiver, value) : f ? f.value = value : state.set(receiver, value), value;
  }
  function __classPrivateFieldIn(state, receiver) {
    if (receiver === null || typeof receiver !== "object" && typeof receiver !== "function")
      throw new TypeError("Cannot use 'in' operator on non-object");
    return typeof state === "function" ? receiver === state : state.has(receiver);
  }
  var extendStatics, __assign, __createBinding, __setModuleDefault;
  var init_tslib_es6 = __esm({
    "node_modules/tslib/tslib.es6.js"() {
      extendStatics = function(d, b) {
        extendStatics = Object.setPrototypeOf || { __proto__: [] } instanceof Array && function(d2, b2) {
          d2.__proto__ = b2;
        } || function(d2, b2) {
          for (var p in b2)
            if (Object.prototype.hasOwnProperty.call(b2, p))
              d2[p] = b2[p];
        };
        return extendStatics(d, b);
      };
      __assign = function() {
        __assign = Object.assign || function __assign2(t) {
          for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s)
              if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
          }
          return t;
        };
        return __assign.apply(this, arguments);
      };
      __createBinding = Object.create ? function(o, m, k, k2) {
        if (k2 === void 0)
          k2 = k;
        var desc = Object.getOwnPropertyDescriptor(m, k);
        if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
          desc = { enumerable: true, get: function() {
            return m[k];
          } };
        }
        Object.defineProperty(o, k2, desc);
      } : function(o, m, k, k2) {
        if (k2 === void 0)
          k2 = k;
        o[k2] = m[k];
      };
      __setModuleDefault = Object.create ? function(o, v) {
        Object.defineProperty(o, "default", { enumerable: true, value: v });
      } : function(o, v) {
        o["default"] = v;
      };
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/CanonicalizeLocaleList.js
  var require_CanonicalizeLocaleList = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/CanonicalizeLocaleList.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.CanonicalizeLocaleList = void 0;
      function CanonicalizeLocaleList(locales) {
        return Intl.getCanonicalLocales(locales);
      }
      exports.CanonicalizeLocaleList = CanonicalizeLocaleList;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/CanonicalizeTimeZoneName.js
  var require_CanonicalizeTimeZoneName = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/CanonicalizeTimeZoneName.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.CanonicalizeTimeZoneName = void 0;
      function CanonicalizeTimeZoneName(tz, _a) {
        var tzData = _a.tzData, uppercaseLinks = _a.uppercaseLinks;
        var uppercasedTz = tz.toUpperCase();
        var uppercasedZones = Object.keys(tzData).reduce(function(all, z) {
          all[z.toUpperCase()] = z;
          return all;
        }, {});
        var ianaTimeZone = uppercaseLinks[uppercasedTz] || uppercasedZones[uppercasedTz];
        if (ianaTimeZone === "Etc/UTC" || ianaTimeZone === "Etc/GMT") {
          return "UTC";
        }
        return ianaTimeZone;
      }
      exports.CanonicalizeTimeZoneName = CanonicalizeTimeZoneName;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/262.js
  var require__ = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/262.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.msFromTime = exports.OrdinaryHasInstance = exports.SecFromTime = exports.MinFromTime = exports.HourFromTime = exports.DateFromTime = exports.MonthFromTime = exports.InLeapYear = exports.DayWithinYear = exports.DaysInYear = exports.YearFromTime = exports.TimeFromYear = exports.DayFromYear = exports.WeekDay = exports.Day = exports.Type = exports.HasOwnProperty = exports.ArrayCreate = exports.SameValue = exports.ToObject = exports.TimeClip = exports.ToNumber = exports.ToString = void 0;
      function ToString(o) {
        if (typeof o === "symbol") {
          throw TypeError("Cannot convert a Symbol value to a string");
        }
        return String(o);
      }
      exports.ToString = ToString;
      function ToNumber(val) {
        if (val === void 0) {
          return NaN;
        }
        if (val === null) {
          return 0;
        }
        if (typeof val === "boolean") {
          return val ? 1 : 0;
        }
        if (typeof val === "number") {
          return val;
        }
        if (typeof val === "symbol" || typeof val === "bigint") {
          throw new TypeError("Cannot convert symbol/bigint to number");
        }
        return Number(val);
      }
      exports.ToNumber = ToNumber;
      function ToInteger(n) {
        var number = ToNumber(n);
        if (isNaN(number) || SameValue(number, -0)) {
          return 0;
        }
        if (isFinite(number)) {
          return number;
        }
        var integer = Math.floor(Math.abs(number));
        if (number < 0) {
          integer = -integer;
        }
        if (SameValue(integer, -0)) {
          return 0;
        }
        return integer;
      }
      function TimeClip(time) {
        if (!isFinite(time)) {
          return NaN;
        }
        if (Math.abs(time) > 8.64 * 1e15) {
          return NaN;
        }
        return ToInteger(time);
      }
      exports.TimeClip = TimeClip;
      function ToObject(arg) {
        if (arg == null) {
          throw new TypeError("undefined/null cannot be converted to object");
        }
        return Object(arg);
      }
      exports.ToObject = ToObject;
      function SameValue(x, y) {
        if (Object.is) {
          return Object.is(x, y);
        }
        if (x === y) {
          return x !== 0 || 1 / x === 1 / y;
        }
        return x !== x && y !== y;
      }
      exports.SameValue = SameValue;
      function ArrayCreate(len) {
        return new Array(len);
      }
      exports.ArrayCreate = ArrayCreate;
      function HasOwnProperty(o, prop) {
        return Object.prototype.hasOwnProperty.call(o, prop);
      }
      exports.HasOwnProperty = HasOwnProperty;
      function Type(x) {
        if (x === null) {
          return "Null";
        }
        if (typeof x === "undefined") {
          return "Undefined";
        }
        if (typeof x === "function" || typeof x === "object") {
          return "Object";
        }
        if (typeof x === "number") {
          return "Number";
        }
        if (typeof x === "boolean") {
          return "Boolean";
        }
        if (typeof x === "string") {
          return "String";
        }
        if (typeof x === "symbol") {
          return "Symbol";
        }
        if (typeof x === "bigint") {
          return "BigInt";
        }
      }
      exports.Type = Type;
      var MS_PER_DAY = 864e5;
      function mod(x, y) {
        return x - Math.floor(x / y) * y;
      }
      function Day(t) {
        return Math.floor(t / MS_PER_DAY);
      }
      exports.Day = Day;
      function WeekDay(t) {
        return mod(Day(t) + 4, 7);
      }
      exports.WeekDay = WeekDay;
      function DayFromYear(y) {
        return Date.UTC(y, 0) / MS_PER_DAY;
      }
      exports.DayFromYear = DayFromYear;
      function TimeFromYear(y) {
        return Date.UTC(y, 0);
      }
      exports.TimeFromYear = TimeFromYear;
      function YearFromTime(t) {
        return new Date(t).getUTCFullYear();
      }
      exports.YearFromTime = YearFromTime;
      function DaysInYear(y) {
        if (y % 4 !== 0) {
          return 365;
        }
        if (y % 100 !== 0) {
          return 366;
        }
        if (y % 400 !== 0) {
          return 365;
        }
        return 366;
      }
      exports.DaysInYear = DaysInYear;
      function DayWithinYear(t) {
        return Day(t) - DayFromYear(YearFromTime(t));
      }
      exports.DayWithinYear = DayWithinYear;
      function InLeapYear(t) {
        return DaysInYear(YearFromTime(t)) === 365 ? 0 : 1;
      }
      exports.InLeapYear = InLeapYear;
      function MonthFromTime(t) {
        var dwy = DayWithinYear(t);
        var leap = InLeapYear(t);
        if (dwy >= 0 && dwy < 31) {
          return 0;
        }
        if (dwy < 59 + leap) {
          return 1;
        }
        if (dwy < 90 + leap) {
          return 2;
        }
        if (dwy < 120 + leap) {
          return 3;
        }
        if (dwy < 151 + leap) {
          return 4;
        }
        if (dwy < 181 + leap) {
          return 5;
        }
        if (dwy < 212 + leap) {
          return 6;
        }
        if (dwy < 243 + leap) {
          return 7;
        }
        if (dwy < 273 + leap) {
          return 8;
        }
        if (dwy < 304 + leap) {
          return 9;
        }
        if (dwy < 334 + leap) {
          return 10;
        }
        if (dwy < 365 + leap) {
          return 11;
        }
        throw new Error("Invalid time");
      }
      exports.MonthFromTime = MonthFromTime;
      function DateFromTime(t) {
        var dwy = DayWithinYear(t);
        var mft = MonthFromTime(t);
        var leap = InLeapYear(t);
        if (mft === 0) {
          return dwy + 1;
        }
        if (mft === 1) {
          return dwy - 30;
        }
        if (mft === 2) {
          return dwy - 58 - leap;
        }
        if (mft === 3) {
          return dwy - 89 - leap;
        }
        if (mft === 4) {
          return dwy - 119 - leap;
        }
        if (mft === 5) {
          return dwy - 150 - leap;
        }
        if (mft === 6) {
          return dwy - 180 - leap;
        }
        if (mft === 7) {
          return dwy - 211 - leap;
        }
        if (mft === 8) {
          return dwy - 242 - leap;
        }
        if (mft === 9) {
          return dwy - 272 - leap;
        }
        if (mft === 10) {
          return dwy - 303 - leap;
        }
        if (mft === 11) {
          return dwy - 333 - leap;
        }
        throw new Error("Invalid time");
      }
      exports.DateFromTime = DateFromTime;
      var HOURS_PER_DAY = 24;
      var MINUTES_PER_HOUR = 60;
      var SECONDS_PER_MINUTE = 60;
      var MS_PER_SECOND = 1e3;
      var MS_PER_MINUTE = MS_PER_SECOND * SECONDS_PER_MINUTE;
      var MS_PER_HOUR = MS_PER_MINUTE * MINUTES_PER_HOUR;
      function HourFromTime(t) {
        return mod(Math.floor(t / MS_PER_HOUR), HOURS_PER_DAY);
      }
      exports.HourFromTime = HourFromTime;
      function MinFromTime(t) {
        return mod(Math.floor(t / MS_PER_MINUTE), MINUTES_PER_HOUR);
      }
      exports.MinFromTime = MinFromTime;
      function SecFromTime(t) {
        return mod(Math.floor(t / MS_PER_SECOND), SECONDS_PER_MINUTE);
      }
      exports.SecFromTime = SecFromTime;
      function IsCallable(fn) {
        return typeof fn === "function";
      }
      function OrdinaryHasInstance(C, O, internalSlots) {
        if (!IsCallable(C)) {
          return false;
        }
        if (internalSlots === null || internalSlots === void 0 ? void 0 : internalSlots.boundTargetFunction) {
          var BC = internalSlots === null || internalSlots === void 0 ? void 0 : internalSlots.boundTargetFunction;
          return O instanceof BC;
        }
        if (typeof O !== "object") {
          return false;
        }
        var P = C.prototype;
        if (typeof P !== "object") {
          throw new TypeError("OrdinaryHasInstance called on an object with an invalid prototype property.");
        }
        return Object.prototype.isPrototypeOf.call(P, O);
      }
      exports.OrdinaryHasInstance = OrdinaryHasInstance;
      function msFromTime(t) {
        return mod(t, MS_PER_SECOND);
      }
      exports.msFromTime = msFromTime;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/CoerceOptionsToObject.js
  var require_CoerceOptionsToObject = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/CoerceOptionsToObject.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.CoerceOptionsToObject = void 0;
      var _262_1 = require__();
      function CoerceOptionsToObject(options) {
        if (typeof options === "undefined") {
          return /* @__PURE__ */ Object.create(null);
        }
        return (0, _262_1.ToObject)(options);
      }
      exports.CoerceOptionsToObject = CoerceOptionsToObject;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/DefaultNumberOption.js
  var require_DefaultNumberOption = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/DefaultNumberOption.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.DefaultNumberOption = void 0;
      function DefaultNumberOption(val, min, max, fallback) {
        if (val !== void 0) {
          val = Number(val);
          if (isNaN(val) || val < min || val > max) {
            throw new RangeError("".concat(val, " is outside of range [").concat(min, ", ").concat(max, "]"));
          }
          return Math.floor(val);
        }
        return fallback;
      }
      exports.DefaultNumberOption = DefaultNumberOption;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/GetNumberOption.js
  var require_GetNumberOption = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/GetNumberOption.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.GetNumberOption = void 0;
      var DefaultNumberOption_1 = require_DefaultNumberOption();
      function GetNumberOption(options, property, minimum, maximum, fallback) {
        var val = options[property];
        return (0, DefaultNumberOption_1.DefaultNumberOption)(val, minimum, maximum, fallback);
      }
      exports.GetNumberOption = GetNumberOption;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/GetOption.js
  var require_GetOption = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/GetOption.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.GetOption = void 0;
      var _262_1 = require__();
      function GetOption(opts, prop, type, values, fallback) {
        if (typeof opts !== "object") {
          throw new TypeError("Options must be an object");
        }
        var value = opts[prop];
        if (value !== void 0) {
          if (type !== "boolean" && type !== "string") {
            throw new TypeError("invalid type");
          }
          if (type === "boolean") {
            value = Boolean(value);
          }
          if (type === "string") {
            value = (0, _262_1.ToString)(value);
          }
          if (values !== void 0 && !values.filter(function(val) {
            return val == value;
          }).length) {
            throw new RangeError("".concat(value, " is not within ").concat(values.join(", ")));
          }
          return value;
        }
        return fallback;
      }
      exports.GetOption = GetOption;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/GetOptionsObject.js
  var require_GetOptionsObject = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/GetOptionsObject.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.GetOptionsObject = void 0;
      function GetOptionsObject(options) {
        if (typeof options === "undefined") {
          return /* @__PURE__ */ Object.create(null);
        }
        if (typeof options === "object") {
          return options;
        }
        throw new TypeError("Options must be an object");
      }
      exports.GetOptionsObject = GetOptionsObject;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/GetStringOrBooleanOption.js
  var require_GetStringOrBooleanOption = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/GetStringOrBooleanOption.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.GetStringOrBooleanOption = void 0;
      var _262_1 = require__();
      function GetStringOrBooleanOption(opts, prop, values, trueValue, falsyValue, fallback) {
        var value = opts[prop];
        if (value === void 0) {
          return fallback;
        }
        if (value === true) {
          return trueValue;
        }
        var valueBoolean = Boolean(value);
        if (valueBoolean === false) {
          return falsyValue;
        }
        value = (0, _262_1.ToString)(value);
        if (value === "true" || value === "false") {
          return fallback;
        }
        if ((values || []).indexOf(value) === -1) {
          throw new RangeError("Invalid value ".concat(value));
        }
        return value;
      }
      exports.GetStringOrBooleanOption = GetStringOrBooleanOption;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/IsSanctionedSimpleUnitIdentifier.js
  var require_IsSanctionedSimpleUnitIdentifier = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/IsSanctionedSimpleUnitIdentifier.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.IsSanctionedSimpleUnitIdentifier = exports.SIMPLE_UNITS = exports.removeUnitNamespace = exports.SANCTIONED_UNITS = void 0;
      exports.SANCTIONED_UNITS = [
        "angle-degree",
        "area-acre",
        "area-hectare",
        "concentr-percent",
        "digital-bit",
        "digital-byte",
        "digital-gigabit",
        "digital-gigabyte",
        "digital-kilobit",
        "digital-kilobyte",
        "digital-megabit",
        "digital-megabyte",
        "digital-petabyte",
        "digital-terabit",
        "digital-terabyte",
        "duration-day",
        "duration-hour",
        "duration-millisecond",
        "duration-minute",
        "duration-month",
        "duration-second",
        "duration-week",
        "duration-year",
        "length-centimeter",
        "length-foot",
        "length-inch",
        "length-kilometer",
        "length-meter",
        "length-mile-scandinavian",
        "length-mile",
        "length-millimeter",
        "length-yard",
        "mass-gram",
        "mass-kilogram",
        "mass-ounce",
        "mass-pound",
        "mass-stone",
        "temperature-celsius",
        "temperature-fahrenheit",
        "volume-fluid-ounce",
        "volume-gallon",
        "volume-liter",
        "volume-milliliter"
      ];
      function removeUnitNamespace(unit) {
        return unit.slice(unit.indexOf("-") + 1);
      }
      exports.removeUnitNamespace = removeUnitNamespace;
      exports.SIMPLE_UNITS = exports.SANCTIONED_UNITS.map(removeUnitNamespace);
      function IsSanctionedSimpleUnitIdentifier(unitIdentifier) {
        return exports.SIMPLE_UNITS.indexOf(unitIdentifier) > -1;
      }
      exports.IsSanctionedSimpleUnitIdentifier = IsSanctionedSimpleUnitIdentifier;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/IsValidTimeZoneName.js
  var require_IsValidTimeZoneName = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/IsValidTimeZoneName.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.IsValidTimeZoneName = void 0;
      function IsValidTimeZoneName(tz, _a) {
        var tzData = _a.tzData, uppercaseLinks = _a.uppercaseLinks;
        var uppercasedTz = tz.toUpperCase();
        var zoneNames = /* @__PURE__ */ new Set();
        var linkNames = /* @__PURE__ */ new Set();
        Object.keys(tzData).map(function(z) {
          return z.toUpperCase();
        }).forEach(function(z) {
          return zoneNames.add(z);
        });
        Object.keys(uppercaseLinks).forEach(function(linkName) {
          linkNames.add(linkName.toUpperCase());
          zoneNames.add(uppercaseLinks[linkName].toUpperCase());
        });
        return zoneNames.has(uppercasedTz) || linkNames.has(uppercasedTz);
      }
      exports.IsValidTimeZoneName = IsValidTimeZoneName;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/IsWellFormedCurrencyCode.js
  var require_IsWellFormedCurrencyCode = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/IsWellFormedCurrencyCode.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.IsWellFormedCurrencyCode = void 0;
      function toUpperCase(str) {
        return str.replace(/([a-z])/g, function(_, c) {
          return c.toUpperCase();
        });
      }
      var NOT_A_Z_REGEX = /[^A-Z]/;
      function IsWellFormedCurrencyCode(currency) {
        currency = toUpperCase(currency);
        if (currency.length !== 3) {
          return false;
        }
        if (NOT_A_Z_REGEX.test(currency)) {
          return false;
        }
        return true;
      }
      exports.IsWellFormedCurrencyCode = IsWellFormedCurrencyCode;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/IsWellFormedUnitIdentifier.js
  var require_IsWellFormedUnitIdentifier = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/IsWellFormedUnitIdentifier.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.IsWellFormedUnitIdentifier = void 0;
      var IsSanctionedSimpleUnitIdentifier_1 = require_IsSanctionedSimpleUnitIdentifier();
      function toLowerCase(str) {
        return str.replace(/([A-Z])/g, function(_, c) {
          return c.toLowerCase();
        });
      }
      function IsWellFormedUnitIdentifier(unit) {
        unit = toLowerCase(unit);
        if ((0, IsSanctionedSimpleUnitIdentifier_1.IsSanctionedSimpleUnitIdentifier)(unit)) {
          return true;
        }
        var units = unit.split("-per-");
        if (units.length !== 2) {
          return false;
        }
        var numerator = units[0], denominator = units[1];
        if (!(0, IsSanctionedSimpleUnitIdentifier_1.IsSanctionedSimpleUnitIdentifier)(numerator) || !(0, IsSanctionedSimpleUnitIdentifier_1.IsSanctionedSimpleUnitIdentifier)(denominator)) {
          return false;
        }
        return true;
      }
      exports.IsWellFormedUnitIdentifier = IsWellFormedUnitIdentifier;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/ApplyUnsignedRoundingMode.js
  var require_ApplyUnsignedRoundingMode = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/ApplyUnsignedRoundingMode.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.ApplyUnsignedRoundingMode = void 0;
      function ApplyUnsignedRoundingMode(x, r1, r2, unsignedRoundingMode) {
        if (x === r1)
          return r1;
        if (unsignedRoundingMode === void 0) {
          throw new Error("unsignedRoundingMode is mandatory");
        }
        if (unsignedRoundingMode === "zero") {
          return r1;
        }
        if (unsignedRoundingMode === "infinity") {
          return r2;
        }
        var d1 = x - r1;
        var d2 = r2 - x;
        if (d1 < d2) {
          return r1;
        }
        if (d2 < d1) {
          return r2;
        }
        if (d1 !== d2) {
          throw new Error("Unexpected error");
        }
        if (unsignedRoundingMode === "half-zero") {
          return r1;
        }
        if (unsignedRoundingMode === "half-infinity") {
          return r2;
        }
        if (unsignedRoundingMode !== "half-even") {
          throw new Error("Unexpected value for unsignedRoundingMode: ".concat(unsignedRoundingMode));
        }
        var cardinality = r1 / (r2 - r1) % 2;
        if (cardinality === 0) {
          return r1;
        }
        return r2;
      }
      exports.ApplyUnsignedRoundingMode = ApplyUnsignedRoundingMode;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/CollapseNumberRange.js
  var require_CollapseNumberRange = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/CollapseNumberRange.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.CollapseNumberRange = void 0;
      function CollapseNumberRange(result) {
        return result;
      }
      exports.CollapseNumberRange = CollapseNumberRange;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/utils.js
  var require_utils = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/utils.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.invariant = exports.UNICODE_EXTENSION_SEQUENCE_REGEX = exports.defineProperty = exports.isLiteralPart = exports.getMultiInternalSlots = exports.getInternalSlot = exports.setMultiInternalSlots = exports.setInternalSlot = exports.repeat = exports.getMagnitude = void 0;
      function getMagnitude(x) {
        return Math.floor(Math.log(x) * Math.LOG10E);
      }
      exports.getMagnitude = getMagnitude;
      function repeat(s, times) {
        if (typeof s.repeat === "function") {
          return s.repeat(times);
        }
        var arr = new Array(times);
        for (var i = 0; i < arr.length; i++) {
          arr[i] = s;
        }
        return arr.join("");
      }
      exports.repeat = repeat;
      function setInternalSlot(map, pl, field, value) {
        if (!map.get(pl)) {
          map.set(pl, /* @__PURE__ */ Object.create(null));
        }
        var slots = map.get(pl);
        slots[field] = value;
      }
      exports.setInternalSlot = setInternalSlot;
      function setMultiInternalSlots(map, pl, props) {
        for (var _i = 0, _a = Object.keys(props); _i < _a.length; _i++) {
          var k = _a[_i];
          setInternalSlot(map, pl, k, props[k]);
        }
      }
      exports.setMultiInternalSlots = setMultiInternalSlots;
      function getInternalSlot(map, pl, field) {
        return getMultiInternalSlots(map, pl, field)[field];
      }
      exports.getInternalSlot = getInternalSlot;
      function getMultiInternalSlots(map, pl) {
        var fields = [];
        for (var _i = 2; _i < arguments.length; _i++) {
          fields[_i - 2] = arguments[_i];
        }
        var slots = map.get(pl);
        if (!slots) {
          throw new TypeError("".concat(pl, " InternalSlot has not been initialized"));
        }
        return fields.reduce(function(all, f) {
          all[f] = slots[f];
          return all;
        }, /* @__PURE__ */ Object.create(null));
      }
      exports.getMultiInternalSlots = getMultiInternalSlots;
      function isLiteralPart(patternPart) {
        return patternPart.type === "literal";
      }
      exports.isLiteralPart = isLiteralPart;
      function defineProperty(target, name, _a) {
        var value = _a.value;
        Object.defineProperty(target, name, {
          configurable: true,
          enumerable: false,
          writable: true,
          value
        });
      }
      exports.defineProperty = defineProperty;
      exports.UNICODE_EXTENSION_SEQUENCE_REGEX = /-u(?:-[0-9a-z]{2,8})+/gi;
      function invariant(condition, message, Err) {
        if (Err === void 0) {
          Err = Error;
        }
        if (!condition) {
          throw new Err(message);
        }
      }
      exports.invariant = invariant;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/ComputeExponentForMagnitude.js
  var require_ComputeExponentForMagnitude = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/ComputeExponentForMagnitude.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.ComputeExponentForMagnitude = void 0;
      function ComputeExponentForMagnitude(numberFormat, magnitude, _a) {
        var getInternalSlots = _a.getInternalSlots;
        var internalSlots = getInternalSlots(numberFormat);
        var notation = internalSlots.notation, dataLocaleData = internalSlots.dataLocaleData, numberingSystem = internalSlots.numberingSystem;
        switch (notation) {
          case "standard":
            return 0;
          case "scientific":
            return magnitude;
          case "engineering":
            return Math.floor(magnitude / 3) * 3;
          default: {
            var compactDisplay = internalSlots.compactDisplay, style = internalSlots.style, currencyDisplay = internalSlots.currencyDisplay;
            var thresholdMap = void 0;
            if (style === "currency" && currencyDisplay !== "name") {
              var currency = dataLocaleData.numbers.currency[numberingSystem] || dataLocaleData.numbers.currency[dataLocaleData.numbers.nu[0]];
              thresholdMap = currency.short;
            } else {
              var decimal = dataLocaleData.numbers.decimal[numberingSystem] || dataLocaleData.numbers.decimal[dataLocaleData.numbers.nu[0]];
              thresholdMap = compactDisplay === "long" ? decimal.long : decimal.short;
            }
            if (!thresholdMap) {
              return 0;
            }
            var num = String(Math.pow(10, magnitude));
            var thresholds = Object.keys(thresholdMap);
            if (num < thresholds[0]) {
              return 0;
            }
            if (num > thresholds[thresholds.length - 1]) {
              return thresholds[thresholds.length - 1].length - 1;
            }
            var i = thresholds.indexOf(num);
            if (i === -1) {
              return 0;
            }
            var magnitudeKey = thresholds[i];
            var compactPattern = thresholdMap[magnitudeKey].other;
            if (compactPattern === "0") {
              return 0;
            }
            return magnitudeKey.length - thresholdMap[magnitudeKey].other.match(/0+/)[0].length;
          }
        }
      }
      exports.ComputeExponentForMagnitude = ComputeExponentForMagnitude;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/ToRawPrecision.js
  var require_ToRawPrecision = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/ToRawPrecision.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.ToRawPrecision = void 0;
      var utils_1 = require_utils();
      function ToRawPrecision(x, minPrecision, maxPrecision) {
        var p = maxPrecision;
        var m;
        var e;
        var xFinal;
        if (x === 0) {
          m = (0, utils_1.repeat)("0", p);
          e = 0;
          xFinal = 0;
        } else {
          var xToString = x.toString();
          var xToStringExponentIndex = xToString.indexOf("e");
          var _a = xToString.split("e"), xToStringMantissa = _a[0], xToStringExponent = _a[1];
          var xToStringMantissaWithoutDecimalPoint = xToStringMantissa.replace(".", "");
          if (xToStringExponentIndex >= 0 && xToStringMantissaWithoutDecimalPoint.length <= p) {
            e = +xToStringExponent;
            m = xToStringMantissaWithoutDecimalPoint + (0, utils_1.repeat)("0", p - xToStringMantissaWithoutDecimalPoint.length);
            xFinal = x;
          } else {
            e = (0, utils_1.getMagnitude)(x);
            var decimalPlaceOffset = e - p + 1;
            var n = Math.round(adjustDecimalPlace(x, decimalPlaceOffset));
            if (adjustDecimalPlace(n, p - 1) >= 10) {
              e = e + 1;
              n = Math.floor(n / 10);
            }
            m = n.toString();
            xFinal = adjustDecimalPlace(n, p - 1 - e);
          }
        }
        var int;
        if (e >= p - 1) {
          m = m + (0, utils_1.repeat)("0", e - p + 1);
          int = e + 1;
        } else if (e >= 0) {
          m = "".concat(m.slice(0, e + 1), ".").concat(m.slice(e + 1));
          int = e + 1;
        } else {
          m = "0.".concat((0, utils_1.repeat)("0", -e - 1)).concat(m);
          int = 1;
        }
        if (m.indexOf(".") >= 0 && maxPrecision > minPrecision) {
          var cut = maxPrecision - minPrecision;
          while (cut > 0 && m[m.length - 1] === "0") {
            m = m.slice(0, -1);
            cut--;
          }
          if (m[m.length - 1] === ".") {
            m = m.slice(0, -1);
          }
        }
        return { formattedString: m, roundedNumber: xFinal, integerDigitsCount: int };
        function adjustDecimalPlace(x2, magnitude) {
          return magnitude < 0 ? x2 * Math.pow(10, -magnitude) : x2 / Math.pow(10, magnitude);
        }
      }
      exports.ToRawPrecision = ToRawPrecision;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/ToRawFixed.js
  var require_ToRawFixed = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/ToRawFixed.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.ToRawFixed = void 0;
      var utils_1 = require_utils();
      function ToRawFixed(x, minFraction, maxFraction) {
        var f = maxFraction;
        var n = Math.round(x * Math.pow(10, f));
        var xFinal = n / Math.pow(10, f);
        var m;
        if (n < 1e21) {
          m = n.toString();
        } else {
          m = n.toString();
          var _a = m.split("e"), mantissa = _a[0], exponent = _a[1];
          m = mantissa.replace(".", "");
          m = m + (0, utils_1.repeat)("0", Math.max(+exponent - m.length + 1, 0));
        }
        var int;
        if (f !== 0) {
          var k = m.length;
          if (k <= f) {
            var z = (0, utils_1.repeat)("0", f + 1 - k);
            m = z + m;
            k = f + 1;
          }
          var a = m.slice(0, k - f);
          var b = m.slice(k - f);
          m = "".concat(a, ".").concat(b);
          int = a.length;
        } else {
          int = m.length;
        }
        var cut = maxFraction - minFraction;
        while (cut > 0 && m[m.length - 1] === "0") {
          m = m.slice(0, -1);
          cut--;
        }
        if (m[m.length - 1] === ".") {
          m = m.slice(0, -1);
        }
        return { formattedString: m, roundedNumber: xFinal, integerDigitsCount: int };
      }
      exports.ToRawFixed = ToRawFixed;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/FormatNumericToString.js
  var require_FormatNumericToString = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/FormatNumericToString.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.FormatNumericToString = void 0;
      var _262_1 = require__();
      var ToRawPrecision_1 = require_ToRawPrecision();
      var utils_1 = require_utils();
      var ToRawFixed_1 = require_ToRawFixed();
      function FormatNumericToString(intlObject, x) {
        var isNegative = x < 0 || (0, _262_1.SameValue)(x, -0);
        if (isNegative) {
          x = -x;
        }
        var result;
        var rourndingType = intlObject.roundingType;
        switch (rourndingType) {
          case "significantDigits":
            result = (0, ToRawPrecision_1.ToRawPrecision)(x, intlObject.minimumSignificantDigits, intlObject.maximumSignificantDigits);
            break;
          case "fractionDigits":
            result = (0, ToRawFixed_1.ToRawFixed)(x, intlObject.minimumFractionDigits, intlObject.maximumFractionDigits);
            break;
          default:
            result = (0, ToRawPrecision_1.ToRawPrecision)(x, 1, 2);
            if (result.integerDigitsCount > 1) {
              result = (0, ToRawFixed_1.ToRawFixed)(x, 0, 0);
            }
            break;
        }
        x = result.roundedNumber;
        var string = result.formattedString;
        var int = result.integerDigitsCount;
        var minInteger = intlObject.minimumIntegerDigits;
        if (int < minInteger) {
          var forwardZeros = (0, utils_1.repeat)("0", minInteger - int);
          string = forwardZeros + string;
        }
        if (isNegative) {
          x = -x;
        }
        return { roundedNumber: x, formattedString: string };
      }
      exports.FormatNumericToString = FormatNumericToString;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/ComputeExponent.js
  var require_ComputeExponent = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/ComputeExponent.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.ComputeExponent = void 0;
      var utils_1 = require_utils();
      var ComputeExponentForMagnitude_1 = require_ComputeExponentForMagnitude();
      var FormatNumericToString_1 = require_FormatNumericToString();
      function ComputeExponent(numberFormat, x, _a) {
        var getInternalSlots = _a.getInternalSlots;
        if (x === 0) {
          return [0, 0];
        }
        if (x < 0) {
          x = -x;
        }
        var magnitude = (0, utils_1.getMagnitude)(x);
        var exponent = (0, ComputeExponentForMagnitude_1.ComputeExponentForMagnitude)(numberFormat, magnitude, {
          getInternalSlots
        });
        x = exponent < 0 ? x * Math.pow(10, -exponent) : x / Math.pow(10, exponent);
        var formatNumberResult = (0, FormatNumericToString_1.FormatNumericToString)(getInternalSlots(numberFormat), x);
        if (formatNumberResult.roundedNumber === 0) {
          return [exponent, magnitude];
        }
        var newMagnitude = (0, utils_1.getMagnitude)(formatNumberResult.roundedNumber);
        if (newMagnitude === magnitude - exponent) {
          return [exponent, magnitude];
        }
        return [
          (0, ComputeExponentForMagnitude_1.ComputeExponentForMagnitude)(numberFormat, magnitude + 1, {
            getInternalSlots
          }),
          magnitude + 1
        ];
      }
      exports.ComputeExponent = ComputeExponent;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/CurrencyDigits.js
  var require_CurrencyDigits = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/CurrencyDigits.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.CurrencyDigits = void 0;
      var _262_1 = require__();
      function CurrencyDigits(c, _a) {
        var currencyDigitsData = _a.currencyDigitsData;
        return (0, _262_1.HasOwnProperty)(currencyDigitsData, c) ? currencyDigitsData[c] : 2;
      }
      exports.CurrencyDigits = CurrencyDigits;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/FormatApproximately.js
  var require_FormatApproximately = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/FormatApproximately.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.FormatApproximately = void 0;
      function FormatApproximately(numberFormat, result, _a) {
        var getInternalSlots = _a.getInternalSlots;
        var internalSlots = getInternalSlots(numberFormat);
        var symbols = internalSlots.dataLocaleData.numbers.symbols[internalSlots.numberingSystem];
        var approximatelySign = symbols.approximatelySign;
        result.push({ type: "approximatelySign", value: approximatelySign });
        return result;
      }
      exports.FormatApproximately = FormatApproximately;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/digit-mapping.generated.js
  var require_digit_mapping_generated = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/digit-mapping.generated.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.digitMapping = void 0;
      exports.digitMapping = { "adlm": ["\u{1E950}", "\u{1E951}", "\u{1E952}", "\u{1E953}", "\u{1E954}", "\u{1E955}", "\u{1E956}", "\u{1E957}", "\u{1E958}", "\u{1E959}"], "ahom": ["\u{11730}", "\u{11731}", "\u{11732}", "\u{11733}", "\u{11734}", "\u{11735}", "\u{11736}", "\u{11737}", "\u{11738}", "\u{11739}"], "arab": ["\u0660", "\u0661", "\u0662", "\u0663", "\u0664", "\u0665", "\u0666", "\u0667", "\u0668", "\u0669"], "arabext": ["\u06F0", "\u06F1", "\u06F2", "\u06F3", "\u06F4", "\u06F5", "\u06F6", "\u06F7", "\u06F8", "\u06F9"], "bali": ["\u1B50", "\u1B51", "\u1B52", "\u1B53", "\u1B54", "\u1B55", "\u1B56", "\u1B57", "\u1B58", "\u1B59"], "beng": ["\u09E6", "\u09E7", "\u09E8", "\u09E9", "\u09EA", "\u09EB", "\u09EC", "\u09ED", "\u09EE", "\u09EF"], "bhks": ["\u{11C50}", "\u{11C51}", "\u{11C52}", "\u{11C53}", "\u{11C54}", "\u{11C55}", "\u{11C56}", "\u{11C57}", "\u{11C58}", "\u{11C59}"], "brah": ["\u{11066}", "\u{11067}", "\u{11068}", "\u{11069}", "\u{1106A}", "\u{1106B}", "\u{1106C}", "\u{1106D}", "\u{1106E}", "\u{1106F}"], "cakm": ["\u{11136}", "\u{11137}", "\u{11138}", "\u{11139}", "\u{1113A}", "\u{1113B}", "\u{1113C}", "\u{1113D}", "\u{1113E}", "\u{1113F}"], "cham": ["\uAA50", "\uAA51", "\uAA52", "\uAA53", "\uAA54", "\uAA55", "\uAA56", "\uAA57", "\uAA58", "\uAA59"], "deva": ["\u0966", "\u0967", "\u0968", "\u0969", "\u096A", "\u096B", "\u096C", "\u096D", "\u096E", "\u096F"], "diak": ["\u{11950}", "\u{11951}", "\u{11952}", "\u{11953}", "\u{11954}", "\u{11955}", "\u{11956}", "\u{11957}", "\u{11958}", "\u{11959}"], "fullwide": ["\uFF10", "\uFF11", "\uFF12", "\uFF13", "\uFF14", "\uFF15", "\uFF16", "\uFF17", "\uFF18", "\uFF19"], "gong": ["\u{11DA0}", "\u{11DA1}", "\u{11DA2}", "\u{11DA3}", "\u{11DA4}", "\u{11DA5}", "\u{11DA6}", "\u{11DA7}", "\u{11DA8}", "\u{11DA9}"], "gonm": ["\u{11D50}", "\u{11D51}", "\u{11D52}", "\u{11D53}", "\u{11D54}", "\u{11D55}", "\u{11D56}", "\u{11D57}", "\u{11D58}", "\u{11D59}"], "gujr": ["\u0AE6", "\u0AE7", "\u0AE8", "\u0AE9", "\u0AEA", "\u0AEB", "\u0AEC", "\u0AED", "\u0AEE", "\u0AEF"], "guru": ["\u0A66", "\u0A67", "\u0A68", "\u0A69", "\u0A6A", "\u0A6B", "\u0A6C", "\u0A6D", "\u0A6E", "\u0A6F"], "hanidec": ["\u3007", "\u4E00", "\u4E8C", "\u4E09", "\u56DB", "\u4E94", "\u516D", "\u4E03", "\u516B", "\u4E5D"], "hmng": ["\u{16B50}", "\u{16B51}", "\u{16B52}", "\u{16B53}", "\u{16B54}", "\u{16B55}", "\u{16B56}", "\u{16B57}", "\u{16B58}", "\u{16B59}"], "hmnp": ["\u{1E140}", "\u{1E141}", "\u{1E142}", "\u{1E143}", "\u{1E144}", "\u{1E145}", "\u{1E146}", "\u{1E147}", "\u{1E148}", "\u{1E149}"], "java": ["\uA9D0", "\uA9D1", "\uA9D2", "\uA9D3", "\uA9D4", "\uA9D5", "\uA9D6", "\uA9D7", "\uA9D8", "\uA9D9"], "kali": ["\uA900", "\uA901", "\uA902", "\uA903", "\uA904", "\uA905", "\uA906", "\uA907", "\uA908", "\uA909"], "khmr": ["\u17E0", "\u17E1", "\u17E2", "\u17E3", "\u17E4", "\u17E5", "\u17E6", "\u17E7", "\u17E8", "\u17E9"], "knda": ["\u0CE6", "\u0CE7", "\u0CE8", "\u0CE9", "\u0CEA", "\u0CEB", "\u0CEC", "\u0CED", "\u0CEE", "\u0CEF"], "lana": ["\u1A80", "\u1A81", "\u1A82", "\u1A83", "\u1A84", "\u1A85", "\u1A86", "\u1A87", "\u1A88", "\u1A89"], "lanatham": ["\u1A90", "\u1A91", "\u1A92", "\u1A93", "\u1A94", "\u1A95", "\u1A96", "\u1A97", "\u1A98", "\u1A99"], "laoo": ["\u0ED0", "\u0ED1", "\u0ED2", "\u0ED3", "\u0ED4", "\u0ED5", "\u0ED6", "\u0ED7", "\u0ED8", "\u0ED9"], "lepc": ["\u1A90", "\u1A91", "\u1A92", "\u1A93", "\u1A94", "\u1A95", "\u1A96", "\u1A97", "\u1A98", "\u1A99"], "limb": ["\u1946", "\u1947", "\u1948", "\u1949", "\u194A", "\u194B", "\u194C", "\u194D", "\u194E", "\u194F"], "mathbold": ["\u{1D7CE}", "\u{1D7CF}", "\u{1D7D0}", "\u{1D7D1}", "\u{1D7D2}", "\u{1D7D3}", "\u{1D7D4}", "\u{1D7D5}", "\u{1D7D6}", "\u{1D7D7}"], "mathdbl": ["\u{1D7D8}", "\u{1D7D9}", "\u{1D7DA}", "\u{1D7DB}", "\u{1D7DC}", "\u{1D7DD}", "\u{1D7DE}", "\u{1D7DF}", "\u{1D7E0}", "\u{1D7E1}"], "mathmono": ["\u{1D7F6}", "\u{1D7F7}", "\u{1D7F8}", "\u{1D7F9}", "\u{1D7FA}", "\u{1D7FB}", "\u{1D7FC}", "\u{1D7FD}", "\u{1D7FE}", "\u{1D7FF}"], "mathsanb": ["\u{1D7EC}", "\u{1D7ED}", "\u{1D7EE}", "\u{1D7EF}", "\u{1D7F0}", "\u{1D7F1}", "\u{1D7F2}", "\u{1D7F3}", "\u{1D7F4}", "\u{1D7F5}"], "mathsans": ["\u{1D7E2}", "\u{1D7E3}", "\u{1D7E4}", "\u{1D7E5}", "\u{1D7E6}", "\u{1D7E7}", "\u{1D7E8}", "\u{1D7E9}", "\u{1D7EA}", "\u{1D7EB}"], "mlym": ["\u0D66", "\u0D67", "\u0D68", "\u0D69", "\u0D6A", "\u0D6B", "\u0D6C", "\u0D6D", "\u0D6E", "\u0D6F"], "modi": ["\u{11650}", "\u{11651}", "\u{11652}", "\u{11653}", "\u{11654}", "\u{11655}", "\u{11656}", "\u{11657}", "\u{11658}", "\u{11659}"], "mong": ["\u1810", "\u1811", "\u1812", "\u1813", "\u1814", "\u1815", "\u1816", "\u1817", "\u1818", "\u1819"], "mroo": ["\u{16A60}", "\u{16A61}", "\u{16A62}", "\u{16A63}", "\u{16A64}", "\u{16A65}", "\u{16A66}", "\u{16A67}", "\u{16A68}", "\u{16A69}"], "mtei": ["\uABF0", "\uABF1", "\uABF2", "\uABF3", "\uABF4", "\uABF5", "\uABF6", "\uABF7", "\uABF8", "\uABF9"], "mymr": ["\u1040", "\u1041", "\u1042", "\u1043", "\u1044", "\u1045", "\u1046", "\u1047", "\u1048", "\u1049"], "mymrshan": ["\u1090", "\u1091", "\u1092", "\u1093", "\u1094", "\u1095", "\u1096", "\u1097", "\u1098", "\u1099"], "mymrtlng": ["\uA9F0", "\uA9F1", "\uA9F2", "\uA9F3", "\uA9F4", "\uA9F5", "\uA9F6", "\uA9F7", "\uA9F8", "\uA9F9"], "newa": ["\u{11450}", "\u{11451}", "\u{11452}", "\u{11453}", "\u{11454}", "\u{11455}", "\u{11456}", "\u{11457}", "\u{11458}", "\u{11459}"], "nkoo": ["\u07C0", "\u07C1", "\u07C2", "\u07C3", "\u07C4", "\u07C5", "\u07C6", "\u07C7", "\u07C8", "\u07C9"], "olck": ["\u1C50", "\u1C51", "\u1C52", "\u1C53", "\u1C54", "\u1C55", "\u1C56", "\u1C57", "\u1C58", "\u1C59"], "orya": ["\u0B66", "\u0B67", "\u0B68", "\u0B69", "\u0B6A", "\u0B6B", "\u0B6C", "\u0B6D", "\u0B6E", "\u0B6F"], "osma": ["\u{104A0}", "\u{104A1}", "\u{104A2}", "\u{104A3}", "\u{104A4}", "\u{104A5}", "\u{104A6}", "\u{104A7}", "\u{104A8}", "\u{104A9}"], "rohg": ["\u{10D30}", "\u{10D31}", "\u{10D32}", "\u{10D33}", "\u{10D34}", "\u{10D35}", "\u{10D36}", "\u{10D37}", "\u{10D38}", "\u{10D39}"], "saur": ["\uA8D0", "\uA8D1", "\uA8D2", "\uA8D3", "\uA8D4", "\uA8D5", "\uA8D6", "\uA8D7", "\uA8D8", "\uA8D9"], "segment": ["\u{1FBF0}", "\u{1FBF1}", "\u{1FBF2}", "\u{1FBF3}", "\u{1FBF4}", "\u{1FBF5}", "\u{1FBF6}", "\u{1FBF7}", "\u{1FBF8}", "\u{1FBF9}"], "shrd": ["\u{111D0}", "\u{111D1}", "\u{111D2}", "\u{111D3}", "\u{111D4}", "\u{111D5}", "\u{111D6}", "\u{111D7}", "\u{111D8}", "\u{111D9}"], "sind": ["\u{112F0}", "\u{112F1}", "\u{112F2}", "\u{112F3}", "\u{112F4}", "\u{112F5}", "\u{112F6}", "\u{112F7}", "\u{112F8}", "\u{112F9}"], "sinh": ["\u0DE6", "\u0DE7", "\u0DE8", "\u0DE9", "\u0DEA", "\u0DEB", "\u0DEC", "\u0DED", "\u0DEE", "\u0DEF"], "sora": ["\u{110F0}", "\u{110F1}", "\u{110F2}", "\u{110F3}", "\u{110F4}", "\u{110F5}", "\u{110F6}", "\u{110F7}", "\u{110F8}", "\u{110F9}"], "sund": ["\u1BB0", "\u1BB1", "\u1BB2", "\u1BB3", "\u1BB4", "\u1BB5", "\u1BB6", "\u1BB7", "\u1BB8", "\u1BB9"], "takr": ["\u{116C0}", "\u{116C1}", "\u{116C2}", "\u{116C3}", "\u{116C4}", "\u{116C5}", "\u{116C6}", "\u{116C7}", "\u{116C8}", "\u{116C9}"], "talu": ["\u19D0", "\u19D1", "\u19D2", "\u19D3", "\u19D4", "\u19D5", "\u19D6", "\u19D7", "\u19D8", "\u19D9"], "tamldec": ["\u0BE6", "\u0BE7", "\u0BE8", "\u0BE9", "\u0BEA", "\u0BEB", "\u0BEC", "\u0BED", "\u0BEE", "\u0BEF"], "telu": ["\u0C66", "\u0C67", "\u0C68", "\u0C69", "\u0C6A", "\u0C6B", "\u0C6C", "\u0C6D", "\u0C6E", "\u0C6F"], "thai": ["\u0E50", "\u0E51", "\u0E52", "\u0E53", "\u0E54", "\u0E55", "\u0E56", "\u0E57", "\u0E58", "\u0E59"], "tibt": ["\u0F20", "\u0F21", "\u0F22", "\u0F23", "\u0F24", "\u0F25", "\u0F26", "\u0F27", "\u0F28", "\u0F29"], "tirh": ["\u{114D0}", "\u{114D1}", "\u{114D2}", "\u{114D3}", "\u{114D4}", "\u{114D5}", "\u{114D6}", "\u{114D7}", "\u{114D8}", "\u{114D9}"], "vaii": ["\u1620", "\u1621", "\u1622", "\u1623", "\u1624", "\u1625", "\u1626", "\u1627", "\u1628", "\u1629"], "wara": ["\u{118E0}", "\u{118E1}", "\u{118E2}", "\u{118E3}", "\u{118E4}", "\u{118E5}", "\u{118E6}", "\u{118E7}", "\u{118E8}", "\u{118E9}"], "wcho": ["\u{1E2F0}", "\u{1E2F1}", "\u{1E2F2}", "\u{1E2F3}", "\u{1E2F4}", "\u{1E2F5}", "\u{1E2F6}", "\u{1E2F7}", "\u{1E2F8}", "\u{1E2F9}"] };
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/regex.generated.js
  var require_regex_generated = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/regex.generated.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.S_UNICODE_REGEX = void 0;
      exports.S_UNICODE_REGEX = /[\$\+<->\^`\|~\xA2-\xA6\xA8\xA9\xAC\xAE-\xB1\xB4\xB8\xD7\xF7\u02C2-\u02C5\u02D2-\u02DF\u02E5-\u02EB\u02ED\u02EF-\u02FF\u0375\u0384\u0385\u03F6\u0482\u058D-\u058F\u0606-\u0608\u060B\u060E\u060F\u06DE\u06E9\u06FD\u06FE\u07F6\u07FE\u07FF\u09F2\u09F3\u09FA\u09FB\u0AF1\u0B70\u0BF3-\u0BFA\u0C7F\u0D4F\u0D79\u0E3F\u0F01-\u0F03\u0F13\u0F15-\u0F17\u0F1A-\u0F1F\u0F34\u0F36\u0F38\u0FBE-\u0FC5\u0FC7-\u0FCC\u0FCE\u0FCF\u0FD5-\u0FD8\u109E\u109F\u1390-\u1399\u166D\u17DB\u1940\u19DE-\u19FF\u1B61-\u1B6A\u1B74-\u1B7C\u1FBD\u1FBF-\u1FC1\u1FCD-\u1FCF\u1FDD-\u1FDF\u1FED-\u1FEF\u1FFD\u1FFE\u2044\u2052\u207A-\u207C\u208A-\u208C\u20A0-\u20BF\u2100\u2101\u2103-\u2106\u2108\u2109\u2114\u2116-\u2118\u211E-\u2123\u2125\u2127\u2129\u212E\u213A\u213B\u2140-\u2144\u214A-\u214D\u214F\u218A\u218B\u2190-\u2307\u230C-\u2328\u232B-\u2426\u2440-\u244A\u249C-\u24E9\u2500-\u2767\u2794-\u27C4\u27C7-\u27E5\u27F0-\u2982\u2999-\u29D7\u29DC-\u29FB\u29FE-\u2B73\u2B76-\u2B95\u2B97-\u2BFF\u2CE5-\u2CEA\u2E50\u2E51\u2E80-\u2E99\u2E9B-\u2EF3\u2F00-\u2FD5\u2FF0-\u2FFB\u3004\u3012\u3013\u3020\u3036\u3037\u303E\u303F\u309B\u309C\u3190\u3191\u3196-\u319F\u31C0-\u31E3\u3200-\u321E\u322A-\u3247\u3250\u3260-\u327F\u328A-\u32B0\u32C0-\u33FF\u4DC0-\u4DFF\uA490-\uA4C6\uA700-\uA716\uA720\uA721\uA789\uA78A\uA828-\uA82B\uA836-\uA839\uAA77-\uAA79\uAB5B\uAB6A\uAB6B\uFB29\uFBB2-\uFBC1\uFDFC\uFDFD\uFE62\uFE64-\uFE66\uFE69\uFF04\uFF0B\uFF1C-\uFF1E\uFF3E\uFF40\uFF5C\uFF5E\uFFE0-\uFFE6\uFFE8-\uFFEE\uFFFC\uFFFD]|\uD800[\uDD37-\uDD3F\uDD79-\uDD89\uDD8C-\uDD8E\uDD90-\uDD9C\uDDA0\uDDD0-\uDDFC]|\uD802[\uDC77\uDC78\uDEC8]|\uD805\uDF3F|\uD807[\uDFD5-\uDFF1]|\uD81A[\uDF3C-\uDF3F\uDF45]|\uD82F\uDC9C|\uD834[\uDC00-\uDCF5\uDD00-\uDD26\uDD29-\uDD64\uDD6A-\uDD6C\uDD83\uDD84\uDD8C-\uDDA9\uDDAE-\uDDE8\uDE00-\uDE41\uDE45\uDF00-\uDF56]|\uD835[\uDEC1\uDEDB\uDEFB\uDF15\uDF35\uDF4F\uDF6F\uDF89\uDFA9\uDFC3]|\uD836[\uDC00-\uDDFF\uDE37-\uDE3A\uDE6D-\uDE74\uDE76-\uDE83\uDE85\uDE86]|\uD838[\uDD4F\uDEFF]|\uD83B[\uDCAC\uDCB0\uDD2E\uDEF0\uDEF1]|\uD83C[\uDC00-\uDC2B\uDC30-\uDC93\uDCA0-\uDCAE\uDCB1-\uDCBF\uDCC1-\uDCCF\uDCD1-\uDCF5\uDD0D-\uDDAD\uDDE6-\uDE02\uDE10-\uDE3B\uDE40-\uDE48\uDE50\uDE51\uDE60-\uDE65\uDF00-\uDFFF]|\uD83D[\uDC00-\uDED7\uDEE0-\uDEEC\uDEF0-\uDEFC\uDF00-\uDF73\uDF80-\uDFD8\uDFE0-\uDFEB]|\uD83E[\uDC00-\uDC0B\uDC10-\uDC47\uDC50-\uDC59\uDC60-\uDC87\uDC90-\uDCAD\uDCB0\uDCB1\uDD00-\uDD78\uDD7A-\uDDCB\uDDCD-\uDE53\uDE60-\uDE6D\uDE70-\uDE74\uDE78-\uDE7A\uDE80-\uDE86\uDE90-\uDEA8\uDEB0-\uDEB6\uDEC0-\uDEC2\uDED0-\uDED6\uDF00-\uDF92\uDF94-\uDFCA]/;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/format_to_parts.js
  var require_format_to_parts = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/format_to_parts.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var ToRawFixed_1 = require_ToRawFixed();
      var digit_mapping_generated_1 = require_digit_mapping_generated();
      var regex_generated_1 = require_regex_generated();
      var CARET_S_UNICODE_REGEX = new RegExp("^".concat(regex_generated_1.S_UNICODE_REGEX.source));
      var S_DOLLAR_UNICODE_REGEX = new RegExp("".concat(regex_generated_1.S_UNICODE_REGEX.source, "$"));
      var CLDR_NUMBER_PATTERN = /[#0](?:[\.,][#0]+)*/g;
      function formatToParts(numberResult, data, pl, options) {
        var sign = numberResult.sign, exponent = numberResult.exponent, magnitude = numberResult.magnitude;
        var notation = options.notation, style = options.style, numberingSystem = options.numberingSystem;
        var defaultNumberingSystem = data.numbers.nu[0];
        var compactNumberPattern = null;
        if (notation === "compact" && magnitude) {
          compactNumberPattern = getCompactDisplayPattern(numberResult, pl, data, style, options.compactDisplay, options.currencyDisplay, numberingSystem);
        }
        var nonNameCurrencyPart;
        if (style === "currency" && options.currencyDisplay !== "name") {
          var byCurrencyDisplay = data.currencies[options.currency];
          if (byCurrencyDisplay) {
            switch (options.currencyDisplay) {
              case "code":
                nonNameCurrencyPart = options.currency;
                break;
              case "symbol":
                nonNameCurrencyPart = byCurrencyDisplay.symbol;
                break;
              default:
                nonNameCurrencyPart = byCurrencyDisplay.narrow;
                break;
            }
          } else {
            nonNameCurrencyPart = options.currency;
          }
        }
        var numberPattern;
        if (!compactNumberPattern) {
          if (style === "decimal" || style === "unit" || style === "currency" && options.currencyDisplay === "name") {
            var decimalData = data.numbers.decimal[numberingSystem] || data.numbers.decimal[defaultNumberingSystem];
            numberPattern = getPatternForSign(decimalData.standard, sign);
          } else if (style === "currency") {
            var currencyData = data.numbers.currency[numberingSystem] || data.numbers.currency[defaultNumberingSystem];
            numberPattern = getPatternForSign(currencyData[options.currencySign], sign);
          } else {
            var percentPattern = data.numbers.percent[numberingSystem] || data.numbers.percent[defaultNumberingSystem];
            numberPattern = getPatternForSign(percentPattern, sign);
          }
        } else {
          numberPattern = compactNumberPattern;
        }
        var decimalNumberPattern = CLDR_NUMBER_PATTERN.exec(numberPattern)[0];
        numberPattern = numberPattern.replace(CLDR_NUMBER_PATTERN, "{0}").replace(/'(.)'/g, "$1");
        if (style === "currency" && options.currencyDisplay !== "name") {
          var currencyData = data.numbers.currency[numberingSystem] || data.numbers.currency[defaultNumberingSystem];
          var afterCurrency = currencyData.currencySpacing.afterInsertBetween;
          if (afterCurrency && !S_DOLLAR_UNICODE_REGEX.test(nonNameCurrencyPart)) {
            numberPattern = numberPattern.replace("\xA4{0}", "\xA4".concat(afterCurrency, "{0}"));
          }
          var beforeCurrency = currencyData.currencySpacing.beforeInsertBetween;
          if (beforeCurrency && !CARET_S_UNICODE_REGEX.test(nonNameCurrencyPart)) {
            numberPattern = numberPattern.replace("{0}\xA4", "{0}".concat(beforeCurrency, "\xA4"));
          }
        }
        var numberPatternParts = numberPattern.split(/({c:[^}]+}|\{0\}|[¤%\-\+])/g);
        var numberParts = [];
        var symbols = data.numbers.symbols[numberingSystem] || data.numbers.symbols[defaultNumberingSystem];
        for (var _i = 0, numberPatternParts_1 = numberPatternParts; _i < numberPatternParts_1.length; _i++) {
          var part = numberPatternParts_1[_i];
          if (!part) {
            continue;
          }
          switch (part) {
            case "{0}": {
              numberParts.push.apply(numberParts, paritionNumberIntoParts(
                symbols,
                numberResult,
                notation,
                exponent,
                numberingSystem,
                // If compact number pattern exists, do not insert group separators.
                !compactNumberPattern && Boolean(options.useGrouping),
                decimalNumberPattern
              ));
              break;
            }
            case "-":
              numberParts.push({ type: "minusSign", value: symbols.minusSign });
              break;
            case "+":
              numberParts.push({ type: "plusSign", value: symbols.plusSign });
              break;
            case "%":
              numberParts.push({ type: "percentSign", value: symbols.percentSign });
              break;
            case "\xA4":
              numberParts.push({ type: "currency", value: nonNameCurrencyPart });
              break;
            default:
              if (/^\{c:/.test(part)) {
                numberParts.push({
                  type: "compact",
                  value: part.substring(3, part.length - 1)
                });
              } else {
                numberParts.push({ type: "literal", value: part });
              }
              break;
          }
        }
        switch (style) {
          case "currency": {
            if (options.currencyDisplay === "name") {
              var unitPattern = (data.numbers.currency[numberingSystem] || data.numbers.currency[defaultNumberingSystem]).unitPattern;
              var unitName = void 0;
              var currencyNameData = data.currencies[options.currency];
              if (currencyNameData) {
                unitName = selectPlural(pl, numberResult.roundedNumber * Math.pow(10, exponent), currencyNameData.displayName);
              } else {
                unitName = options.currency;
              }
              var unitPatternParts = unitPattern.split(/(\{[01]\})/g);
              var result = [];
              for (var _a = 0, unitPatternParts_1 = unitPatternParts; _a < unitPatternParts_1.length; _a++) {
                var part = unitPatternParts_1[_a];
                switch (part) {
                  case "{0}":
                    result.push.apply(result, numberParts);
                    break;
                  case "{1}":
                    result.push({ type: "currency", value: unitName });
                    break;
                  default:
                    if (part) {
                      result.push({ type: "literal", value: part });
                    }
                    break;
                }
              }
              return result;
            } else {
              return numberParts;
            }
          }
          case "unit": {
            var unit = options.unit, unitDisplay = options.unitDisplay;
            var unitData = data.units.simple[unit];
            var unitPattern = void 0;
            if (unitData) {
              unitPattern = selectPlural(pl, numberResult.roundedNumber * Math.pow(10, exponent), data.units.simple[unit][unitDisplay]);
            } else {
              var _b = unit.split("-per-"), numeratorUnit = _b[0], denominatorUnit = _b[1];
              unitData = data.units.simple[numeratorUnit];
              var numeratorUnitPattern = selectPlural(pl, numberResult.roundedNumber * Math.pow(10, exponent), data.units.simple[numeratorUnit][unitDisplay]);
              var perUnitPattern = data.units.simple[denominatorUnit].perUnit[unitDisplay];
              if (perUnitPattern) {
                unitPattern = perUnitPattern.replace("{0}", numeratorUnitPattern);
              } else {
                var perPattern = data.units.compound.per[unitDisplay];
                var denominatorPattern = selectPlural(pl, 1, data.units.simple[denominatorUnit][unitDisplay]);
                unitPattern = unitPattern = perPattern.replace("{0}", numeratorUnitPattern).replace("{1}", denominatorPattern.replace("{0}", ""));
              }
            }
            var result = [];
            for (var _c = 0, _d = unitPattern.split(/(\s*\{0\}\s*)/); _c < _d.length; _c++) {
              var part = _d[_c];
              var interpolateMatch = /^(\s*)\{0\}(\s*)$/.exec(part);
              if (interpolateMatch) {
                if (interpolateMatch[1]) {
                  result.push({ type: "literal", value: interpolateMatch[1] });
                }
                result.push.apply(result, numberParts);
                if (interpolateMatch[2]) {
                  result.push({ type: "literal", value: interpolateMatch[2] });
                }
              } else if (part) {
                result.push({ type: "unit", value: part });
              }
            }
            return result;
          }
          default:
            return numberParts;
        }
      }
      exports.default = formatToParts;
      function paritionNumberIntoParts(symbols, numberResult, notation, exponent, numberingSystem, useGrouping, decimalNumberPattern) {
        var result = [];
        var n = numberResult.formattedString, x = numberResult.roundedNumber;
        if (isNaN(x)) {
          return [{ type: "nan", value: n }];
        } else if (!isFinite(x)) {
          return [{ type: "infinity", value: n }];
        }
        var digitReplacementTable = digit_mapping_generated_1.digitMapping[numberingSystem];
        if (digitReplacementTable) {
          n = n.replace(/\d/g, function(digit) {
            return digitReplacementTable[+digit] || digit;
          });
        }
        var decimalSepIndex = n.indexOf(".");
        var integer;
        var fraction;
        if (decimalSepIndex > 0) {
          integer = n.slice(0, decimalSepIndex);
          fraction = n.slice(decimalSepIndex + 1);
        } else {
          integer = n;
        }
        if (useGrouping && (notation !== "compact" || x >= 1e4)) {
          var groupSepSymbol = symbols.group;
          var groups = [];
          var integerNumberPattern = decimalNumberPattern.split(".")[0];
          var patternGroups = integerNumberPattern.split(",");
          var primaryGroupingSize = 3;
          var secondaryGroupingSize = 3;
          if (patternGroups.length > 1) {
            primaryGroupingSize = patternGroups[patternGroups.length - 1].length;
          }
          if (patternGroups.length > 2) {
            secondaryGroupingSize = patternGroups[patternGroups.length - 2].length;
          }
          var i = integer.length - primaryGroupingSize;
          if (i > 0) {
            groups.push(integer.slice(i, i + primaryGroupingSize));
            for (i -= secondaryGroupingSize; i > 0; i -= secondaryGroupingSize) {
              groups.push(integer.slice(i, i + secondaryGroupingSize));
            }
            groups.push(integer.slice(0, i + secondaryGroupingSize));
          } else {
            groups.push(integer);
          }
          while (groups.length > 0) {
            var integerGroup = groups.pop();
            result.push({ type: "integer", value: integerGroup });
            if (groups.length > 0) {
              result.push({ type: "group", value: groupSepSymbol });
            }
          }
        } else {
          result.push({ type: "integer", value: integer });
        }
        if (fraction !== void 0) {
          result.push({ type: "decimal", value: symbols.decimal }, { type: "fraction", value: fraction });
        }
        if ((notation === "scientific" || notation === "engineering") && isFinite(x)) {
          result.push({ type: "exponentSeparator", value: symbols.exponential });
          if (exponent < 0) {
            result.push({ type: "exponentMinusSign", value: symbols.minusSign });
            exponent = -exponent;
          }
          var exponentResult = (0, ToRawFixed_1.ToRawFixed)(exponent, 0, 0);
          result.push({
            type: "exponentInteger",
            value: exponentResult.formattedString
          });
        }
        return result;
      }
      function getPatternForSign(pattern, sign) {
        if (pattern.indexOf(";") < 0) {
          pattern = "".concat(pattern, ";-").concat(pattern);
        }
        var _a = pattern.split(";"), zeroPattern = _a[0], negativePattern = _a[1];
        switch (sign) {
          case 0:
            return zeroPattern;
          case -1:
            return negativePattern;
          default:
            return negativePattern.indexOf("-") >= 0 ? negativePattern.replace(/-/g, "+") : "+".concat(zeroPattern);
        }
      }
      function getCompactDisplayPattern(numberResult, pl, data, style, compactDisplay, currencyDisplay, numberingSystem) {
        var _a;
        var roundedNumber = numberResult.roundedNumber, sign = numberResult.sign, magnitude = numberResult.magnitude;
        var magnitudeKey = String(Math.pow(10, magnitude));
        var defaultNumberingSystem = data.numbers.nu[0];
        var pattern;
        if (style === "currency" && currencyDisplay !== "name") {
          var byNumberingSystem = data.numbers.currency;
          var currencyData = byNumberingSystem[numberingSystem] || byNumberingSystem[defaultNumberingSystem];
          var compactPluralRules = (_a = currencyData.short) === null || _a === void 0 ? void 0 : _a[magnitudeKey];
          if (!compactPluralRules) {
            return null;
          }
          pattern = selectPlural(pl, roundedNumber, compactPluralRules);
        } else {
          var byNumberingSystem = data.numbers.decimal;
          var byCompactDisplay = byNumberingSystem[numberingSystem] || byNumberingSystem[defaultNumberingSystem];
          var compactPlaralRule = byCompactDisplay[compactDisplay][magnitudeKey];
          if (!compactPlaralRule) {
            return null;
          }
          pattern = selectPlural(pl, roundedNumber, compactPlaralRule);
        }
        if (pattern === "0") {
          return null;
        }
        pattern = getPatternForSign(pattern, sign).replace(/([^\s;\-\+\d¤]+)/g, "{c:$1}").replace(/0+/, "0");
        return pattern;
      }
      function selectPlural(pl, x, rules) {
        return rules[pl.select(x)] || rules.other;
      }
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/PartitionNumberPattern.js
  var require_PartitionNumberPattern = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/PartitionNumberPattern.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.PartitionNumberPattern = void 0;
      var tslib_1 = (init_tslib_es6(), __toCommonJS(tslib_es6_exports));
      var FormatNumericToString_1 = require_FormatNumericToString();
      var _262_1 = require__();
      var ComputeExponent_1 = require_ComputeExponent();
      var format_to_parts_1 = tslib_1.__importDefault(require_format_to_parts());
      function PartitionNumberPattern(numberFormat, x, _a) {
        var _b;
        var getInternalSlots = _a.getInternalSlots;
        var internalSlots = getInternalSlots(numberFormat);
        var pl = internalSlots.pl, dataLocaleData = internalSlots.dataLocaleData, numberingSystem = internalSlots.numberingSystem;
        var symbols = dataLocaleData.numbers.symbols[numberingSystem] || dataLocaleData.numbers.symbols[dataLocaleData.numbers.nu[0]];
        var magnitude = 0;
        var exponent = 0;
        var n;
        if (isNaN(x)) {
          n = symbols.nan;
        } else if (x == Number.POSITIVE_INFINITY || x == Number.NEGATIVE_INFINITY) {
          n = symbols.infinity;
        } else {
          if (!(0, _262_1.SameValue)(x, -0)) {
            if (!isFinite(x)) {
              throw new Error("Input must be a mathematical value");
            }
            if (internalSlots.style == "percent") {
              x *= 100;
            }
            ;
            _b = (0, ComputeExponent_1.ComputeExponent)(numberFormat, x, {
              getInternalSlots
            }), exponent = _b[0], magnitude = _b[1];
            x = exponent < 0 ? x * Math.pow(10, -exponent) : x / Math.pow(10, exponent);
          }
          var formatNumberResult = (0, FormatNumericToString_1.FormatNumericToString)(internalSlots, x);
          n = formatNumberResult.formattedString;
          x = formatNumberResult.roundedNumber;
        }
        var sign;
        var signDisplay = internalSlots.signDisplay;
        switch (signDisplay) {
          case "never":
            sign = 0;
            break;
          case "auto":
            if ((0, _262_1.SameValue)(x, 0) || x > 0 || isNaN(x)) {
              sign = 0;
            } else {
              sign = -1;
            }
            break;
          case "always":
            if ((0, _262_1.SameValue)(x, 0) || x > 0 || isNaN(x)) {
              sign = 1;
            } else {
              sign = -1;
            }
            break;
          default:
            if (x === 0 || isNaN(x)) {
              sign = 0;
            } else if (x > 0) {
              sign = 1;
            } else {
              sign = -1;
            }
        }
        return (0, format_to_parts_1.default)({ roundedNumber: x, formattedString: n, exponent, magnitude, sign }, internalSlots.dataLocaleData, pl, internalSlots);
      }
      exports.PartitionNumberPattern = PartitionNumberPattern;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/FormatNumericToParts.js
  var require_FormatNumericToParts = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/FormatNumericToParts.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.FormatNumericToParts = void 0;
      var PartitionNumberPattern_1 = require_PartitionNumberPattern();
      var _262_1 = require__();
      function FormatNumericToParts(nf, x, implDetails) {
        var parts = (0, PartitionNumberPattern_1.PartitionNumberPattern)(nf, x, implDetails);
        var result = (0, _262_1.ArrayCreate)(0);
        for (var _i = 0, parts_1 = parts; _i < parts_1.length; _i++) {
          var part = parts_1[_i];
          result.push({
            type: part.type,
            value: part.value
          });
        }
        return result;
      }
      exports.FormatNumericToParts = FormatNumericToParts;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/GetUnsignedRoundingMode.js
  var require_GetUnsignedRoundingMode = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/GetUnsignedRoundingMode.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.GetUnsignedRoundingMode = void 0;
      var negativeMapping = {
        ceil: "zero",
        floor: "infinity",
        expand: "infinity",
        trunc: "zero",
        halfCeil: "half-zero",
        halfFloor: "half-infinity",
        halfExpand: "half-infinity",
        halfTrunc: "half-zero",
        halfEven: "half-even"
      };
      var positiveMapping = {
        ceil: "infinity",
        floor: "zero",
        expand: "infinity",
        trunc: "zero",
        halfCeil: "half-infinity",
        halfFloor: "half-zero",
        halfExpand: "half-infinity",
        halfTrunc: "half-zero",
        halfEven: "half-even"
      };
      function GetUnsignedRoundingMode(roundingMode, isNegative) {
        if (isNegative) {
          return negativeMapping[roundingMode];
        }
        return positiveMapping[roundingMode];
      }
      exports.GetUnsignedRoundingMode = GetUnsignedRoundingMode;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/CanonicalizeLocaleList.js
  var require_CanonicalizeLocaleList2 = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/CanonicalizeLocaleList.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.CanonicalizeLocaleList = void 0;
      function CanonicalizeLocaleList(locales) {
        return Intl.getCanonicalLocales(locales);
      }
      exports.CanonicalizeLocaleList = CanonicalizeLocaleList;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/utils.js
  var require_utils2 = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/utils.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.invariant = exports.UNICODE_EXTENSION_SEQUENCE_REGEX = void 0;
      exports.UNICODE_EXTENSION_SEQUENCE_REGEX = /-u(?:-[0-9a-z]{2,8})+/gi;
      function invariant(condition, message, Err) {
        if (Err === void 0) {
          Err = Error;
        }
        if (!condition) {
          throw new Err(message);
        }
      }
      exports.invariant = invariant;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/BestAvailableLocale.js
  var require_BestAvailableLocale = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/BestAvailableLocale.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.BestAvailableLocale = void 0;
      function BestAvailableLocale(availableLocales, locale) {
        var candidate = locale;
        while (true) {
          if (availableLocales.has(candidate)) {
            return candidate;
          }
          var pos = candidate.lastIndexOf("-");
          if (!~pos) {
            return void 0;
          }
          if (pos >= 2 && candidate[pos - 2] === "-") {
            pos -= 2;
          }
          candidate = candidate.slice(0, pos);
        }
      }
      exports.BestAvailableLocale = BestAvailableLocale;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/LookupMatcher.js
  var require_LookupMatcher = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/LookupMatcher.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.LookupMatcher = void 0;
      var utils_1 = require_utils2();
      var BestAvailableLocale_1 = require_BestAvailableLocale();
      function LookupMatcher(availableLocales, requestedLocales, getDefaultLocale) {
        var result = { locale: "" };
        for (var _i = 0, requestedLocales_1 = requestedLocales; _i < requestedLocales_1.length; _i++) {
          var locale = requestedLocales_1[_i];
          var noExtensionLocale = locale.replace(utils_1.UNICODE_EXTENSION_SEQUENCE_REGEX, "");
          var availableLocale = (0, BestAvailableLocale_1.BestAvailableLocale)(availableLocales, noExtensionLocale);
          if (availableLocale) {
            result.locale = availableLocale;
            if (locale !== noExtensionLocale) {
              result.extension = locale.slice(noExtensionLocale.length + 1, locale.length);
            }
            return result;
          }
        }
        result.locale = getDefaultLocale();
        return result;
      }
      exports.LookupMatcher = LookupMatcher;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/BestFitMatcher.js
  var require_BestFitMatcher = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/BestFitMatcher.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.BestFitMatcher = void 0;
      var BestAvailableLocale_1 = require_BestAvailableLocale();
      var utils_1 = require_utils2();
      function BestFitMatcher(availableLocales, requestedLocales, getDefaultLocale) {
        var minimizedAvailableLocaleMap = {};
        var availableLocaleMap = {};
        var canonicalizedLocaleMap = {};
        var minimizedAvailableLocales = /* @__PURE__ */ new Set();
        availableLocales.forEach(function(locale2) {
          var minimizedLocale = new Intl.Locale(locale2).minimize().toString();
          var canonicalizedLocale = Intl.getCanonicalLocales(locale2)[0] || locale2;
          minimizedAvailableLocaleMap[minimizedLocale] = locale2;
          availableLocaleMap[locale2] = locale2;
          canonicalizedLocaleMap[canonicalizedLocale] = locale2;
          minimizedAvailableLocales.add(minimizedLocale);
          minimizedAvailableLocales.add(locale2);
          minimizedAvailableLocales.add(canonicalizedLocale);
        });
        var foundLocale;
        for (var _i = 0, requestedLocales_1 = requestedLocales; _i < requestedLocales_1.length; _i++) {
          var l = requestedLocales_1[_i];
          if (foundLocale) {
            break;
          }
          var noExtensionLocale = l.replace(utils_1.UNICODE_EXTENSION_SEQUENCE_REGEX, "");
          if (availableLocales.has(noExtensionLocale)) {
            foundLocale = noExtensionLocale;
            break;
          }
          if (minimizedAvailableLocales.has(noExtensionLocale)) {
            foundLocale = noExtensionLocale;
            break;
          }
          var locale = new Intl.Locale(noExtensionLocale);
          var maximizedRequestedLocale = locale.maximize().toString();
          var minimizedRequestedLocale = locale.minimize().toString();
          if (minimizedAvailableLocales.has(minimizedRequestedLocale)) {
            foundLocale = minimizedRequestedLocale;
            break;
          }
          foundLocale = (0, BestAvailableLocale_1.BestAvailableLocale)(minimizedAvailableLocales, maximizedRequestedLocale);
        }
        if (!foundLocale) {
          return { locale: getDefaultLocale() };
        }
        return {
          locale: availableLocaleMap[foundLocale] || canonicalizedLocaleMap[foundLocale] || minimizedAvailableLocaleMap[foundLocale] || foundLocale
        };
      }
      exports.BestFitMatcher = BestFitMatcher;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/UnicodeExtensionValue.js
  var require_UnicodeExtensionValue = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/UnicodeExtensionValue.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.UnicodeExtensionValue = void 0;
      var utils_1 = require_utils2();
      function UnicodeExtensionValue(extension, key) {
        (0, utils_1.invariant)(key.length === 2, "key must have 2 elements");
        var size = extension.length;
        var searchValue = "-".concat(key, "-");
        var pos = extension.indexOf(searchValue);
        if (pos !== -1) {
          var start = pos + 4;
          var end = start;
          var k = start;
          var done = false;
          while (!done) {
            var e = extension.indexOf("-", k);
            var len = void 0;
            if (e === -1) {
              len = size - k;
            } else {
              len = e - k;
            }
            if (len === 2) {
              done = true;
            } else if (e === -1) {
              end = size;
              done = true;
            } else {
              end = e;
              k = e + 1;
            }
          }
          return extension.slice(start, end);
        }
        searchValue = "-".concat(key);
        pos = extension.indexOf(searchValue);
        if (pos !== -1 && pos + 3 === size) {
          return "";
        }
        return void 0;
      }
      exports.UnicodeExtensionValue = UnicodeExtensionValue;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/ResolveLocale.js
  var require_ResolveLocale = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/ResolveLocale.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.ResolveLocale = void 0;
      var LookupMatcher_1 = require_LookupMatcher();
      var BestFitMatcher_1 = require_BestFitMatcher();
      var utils_1 = require_utils2();
      var UnicodeExtensionValue_1 = require_UnicodeExtensionValue();
      function ResolveLocale(availableLocales, requestedLocales, options, relevantExtensionKeys, localeData, getDefaultLocale) {
        var matcher = options.localeMatcher;
        var r;
        if (matcher === "lookup") {
          r = (0, LookupMatcher_1.LookupMatcher)(availableLocales, requestedLocales, getDefaultLocale);
        } else {
          r = (0, BestFitMatcher_1.BestFitMatcher)(availableLocales, requestedLocales, getDefaultLocale);
        }
        var foundLocale = r.locale;
        var result = { locale: "", dataLocale: foundLocale };
        var supportedExtension = "-u";
        for (var _i = 0, relevantExtensionKeys_1 = relevantExtensionKeys; _i < relevantExtensionKeys_1.length; _i++) {
          var key = relevantExtensionKeys_1[_i];
          (0, utils_1.invariant)(foundLocale in localeData, "Missing locale data for ".concat(foundLocale));
          var foundLocaleData = localeData[foundLocale];
          (0, utils_1.invariant)(typeof foundLocaleData === "object" && foundLocaleData !== null, "locale data ".concat(key, " must be an object"));
          var keyLocaleData = foundLocaleData[key];
          (0, utils_1.invariant)(Array.isArray(keyLocaleData), "keyLocaleData for ".concat(key, " must be an array"));
          var value = keyLocaleData[0];
          (0, utils_1.invariant)(typeof value === "string" || value === null, "value must be string or null but got ".concat(typeof value, " in key ").concat(key));
          var supportedExtensionAddition = "";
          if (r.extension) {
            var requestedValue = (0, UnicodeExtensionValue_1.UnicodeExtensionValue)(r.extension, key);
            if (requestedValue !== void 0) {
              if (requestedValue !== "") {
                if (~keyLocaleData.indexOf(requestedValue)) {
                  value = requestedValue;
                  supportedExtensionAddition = "-".concat(key, "-").concat(value);
                }
              } else if (~requestedValue.indexOf("true")) {
                value = "true";
                supportedExtensionAddition = "-".concat(key);
              }
            }
          }
          if (key in options) {
            var optionsValue = options[key];
            (0, utils_1.invariant)(typeof optionsValue === "string" || typeof optionsValue === "undefined" || optionsValue === null, "optionsValue must be String, Undefined or Null");
            if (~keyLocaleData.indexOf(optionsValue)) {
              if (optionsValue !== value) {
                value = optionsValue;
                supportedExtensionAddition = "";
              }
            }
          }
          result[key] = value;
          supportedExtension += supportedExtensionAddition;
        }
        if (supportedExtension.length > 2) {
          var privateIndex = foundLocale.indexOf("-x-");
          if (privateIndex === -1) {
            foundLocale = foundLocale + supportedExtension;
          } else {
            var preExtension = foundLocale.slice(0, privateIndex);
            var postExtension = foundLocale.slice(privateIndex, foundLocale.length);
            foundLocale = preExtension + supportedExtension + postExtension;
          }
          foundLocale = Intl.getCanonicalLocales(foundLocale)[0];
        }
        result.locale = foundLocale;
        return result;
      }
      exports.ResolveLocale = ResolveLocale;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/LookupSupportedLocales.js
  var require_LookupSupportedLocales = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/abstract/LookupSupportedLocales.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.LookupSupportedLocales = void 0;
      var utils_1 = require_utils2();
      var BestAvailableLocale_1 = require_BestAvailableLocale();
      function LookupSupportedLocales(availableLocales, requestedLocales) {
        var subset = [];
        for (var _i = 0, requestedLocales_1 = requestedLocales; _i < requestedLocales_1.length; _i++) {
          var locale = requestedLocales_1[_i];
          var noExtensionLocale = locale.replace(utils_1.UNICODE_EXTENSION_SEQUENCE_REGEX, "");
          var availableLocale = (0, BestAvailableLocale_1.BestAvailableLocale)(availableLocales, noExtensionLocale);
          if (availableLocale) {
            subset.push(availableLocale);
          }
        }
        return subset;
      }
      exports.LookupSupportedLocales = LookupSupportedLocales;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/index.js
  var require_intl_localematcher = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/intl-localematcher/index.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.ResolveLocale = exports.LookupSupportedLocales = exports.match = void 0;
      var CanonicalizeLocaleList_1 = require_CanonicalizeLocaleList2();
      var ResolveLocale_1 = require_ResolveLocale();
      function match(requestedLocales, availableLocales, defaultLocale, opts) {
        var locales = availableLocales.reduce(function(all, l) {
          all.add(l);
          return all;
        }, /* @__PURE__ */ new Set());
        return (0, ResolveLocale_1.ResolveLocale)(locales, (0, CanonicalizeLocaleList_1.CanonicalizeLocaleList)(requestedLocales), {
          localeMatcher: (opts === null || opts === void 0 ? void 0 : opts.algorithm) || "best fit"
        }, [], {}, function() {
          return defaultLocale;
        }).locale;
      }
      exports.match = match;
      var LookupSupportedLocales_1 = require_LookupSupportedLocales();
      Object.defineProperty(exports, "LookupSupportedLocales", { enumerable: true, get: function() {
        return LookupSupportedLocales_1.LookupSupportedLocales;
      } });
      var ResolveLocale_2 = require_ResolveLocale();
      Object.defineProperty(exports, "ResolveLocale", { enumerable: true, get: function() {
        return ResolveLocale_2.ResolveLocale;
      } });
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/SetNumberFormatUnitOptions.js
  var require_SetNumberFormatUnitOptions = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/SetNumberFormatUnitOptions.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.SetNumberFormatUnitOptions = void 0;
      var GetOption_1 = require_GetOption();
      var IsWellFormedCurrencyCode_1 = require_IsWellFormedCurrencyCode();
      var IsWellFormedUnitIdentifier_1 = require_IsWellFormedUnitIdentifier();
      function SetNumberFormatUnitOptions(nf, options, _a) {
        if (options === void 0) {
          options = /* @__PURE__ */ Object.create(null);
        }
        var getInternalSlots = _a.getInternalSlots;
        var internalSlots = getInternalSlots(nf);
        var style = (0, GetOption_1.GetOption)(options, "style", "string", ["decimal", "percent", "currency", "unit"], "decimal");
        internalSlots.style = style;
        var currency = (0, GetOption_1.GetOption)(options, "currency", "string", void 0, void 0);
        if (currency !== void 0 && !(0, IsWellFormedCurrencyCode_1.IsWellFormedCurrencyCode)(currency)) {
          throw RangeError("Malformed currency code");
        }
        if (style === "currency" && currency === void 0) {
          throw TypeError("currency cannot be undefined");
        }
        var currencyDisplay = (0, GetOption_1.GetOption)(options, "currencyDisplay", "string", ["code", "symbol", "narrowSymbol", "name"], "symbol");
        var currencySign = (0, GetOption_1.GetOption)(options, "currencySign", "string", ["standard", "accounting"], "standard");
        var unit = (0, GetOption_1.GetOption)(options, "unit", "string", void 0, void 0);
        if (unit !== void 0 && !(0, IsWellFormedUnitIdentifier_1.IsWellFormedUnitIdentifier)(unit)) {
          throw RangeError("Invalid unit argument for Intl.NumberFormat()");
        }
        if (style === "unit" && unit === void 0) {
          throw TypeError("unit cannot be undefined");
        }
        var unitDisplay = (0, GetOption_1.GetOption)(options, "unitDisplay", "string", ["short", "narrow", "long"], "short");
        if (style === "currency") {
          internalSlots.currency = currency.toUpperCase();
          internalSlots.currencyDisplay = currencyDisplay;
          internalSlots.currencySign = currencySign;
        }
        if (style === "unit") {
          internalSlots.unit = unit;
          internalSlots.unitDisplay = unitDisplay;
        }
      }
      exports.SetNumberFormatUnitOptions = SetNumberFormatUnitOptions;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/SetNumberFormatDigitOptions.js
  var require_SetNumberFormatDigitOptions = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/SetNumberFormatDigitOptions.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.SetNumberFormatDigitOptions = void 0;
      var GetNumberOption_1 = require_GetNumberOption();
      var DefaultNumberOption_1 = require_DefaultNumberOption();
      var GetOption_1 = require_GetOption();
      function SetNumberFormatDigitOptions(internalSlots, opts, mnfdDefault, mxfdDefault, notation) {
        var mnid = (0, GetNumberOption_1.GetNumberOption)(opts, "minimumIntegerDigits", 1, 21, 1);
        var mnfd = opts.minimumFractionDigits;
        var mxfd = opts.maximumFractionDigits;
        var mnsd = opts.minimumSignificantDigits;
        var mxsd = opts.maximumSignificantDigits;
        internalSlots.minimumIntegerDigits = mnid;
        var roundingPriority = (0, GetOption_1.GetOption)(opts, "roundingPriority", "string", ["auto", "morePrecision", "lessPrecision"], "auto");
        var hasSd = mnsd !== void 0 || mxsd !== void 0;
        var hasFd = mnfd !== void 0 || mxfd !== void 0;
        var needSd = true;
        var needFd = true;
        if (roundingPriority === "auto") {
          needSd = hasSd;
          if (hasSd || !hasFd && notation === "compact") {
            needFd = false;
          }
        }
        if (needSd) {
          if (hasSd) {
            mnsd = (0, DefaultNumberOption_1.DefaultNumberOption)(mnsd, 1, 21, 1);
            mxsd = (0, DefaultNumberOption_1.DefaultNumberOption)(mxsd, mnsd, 21, 21);
            internalSlots.minimumSignificantDigits = mnsd;
            internalSlots.maximumSignificantDigits = mxsd;
          } else {
            internalSlots.minimumSignificantDigits = 1;
            internalSlots.maximumSignificantDigits = 21;
          }
        }
        if (needFd) {
          if (hasFd) {
            mnfd = (0, DefaultNumberOption_1.DefaultNumberOption)(mnfd, 0, 20, void 0);
            mxfd = (0, DefaultNumberOption_1.DefaultNumberOption)(mxfd, 0, 20, void 0);
            if (mnfd === void 0) {
              mnfd = Math.min(mnfdDefault, mxfd);
            } else if (mxfd === void 0) {
              mxfd = Math.max(mxfdDefault, mnfd);
            } else if (mnfd > mxfd) {
              throw new RangeError("Invalid range, ".concat(mnfd, " > ").concat(mxfd));
            }
            internalSlots.minimumFractionDigits = mnfd;
            internalSlots.maximumFractionDigits = mxfd;
          } else {
            internalSlots.minimumFractionDigits = mnfdDefault;
            internalSlots.maximumFractionDigits = mxfdDefault;
          }
        }
        if (needSd || needFd) {
          if (roundingPriority === "morePrecision") {
            internalSlots.roundingType = "morePrecision";
          } else if (roundingPriority === "lessPrecision") {
            internalSlots.roundingType = "lessPrecision";
          } else if (hasSd) {
            internalSlots.roundingType = "significantDigits";
          } else {
            internalSlots.roundingType = "fractionDigits";
          }
        } else {
          internalSlots.roundingType = "morePrecision";
          internalSlots.minimumFractionDigits = 0;
          internalSlots.maximumFractionDigits = 0;
          internalSlots.minimumSignificantDigits = 1;
          internalSlots.maximumSignificantDigits = 2;
        }
      }
      exports.SetNumberFormatDigitOptions = SetNumberFormatDigitOptions;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/InitializeNumberFormat.js
  var require_InitializeNumberFormat = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/InitializeNumberFormat.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.InitializeNumberFormat = void 0;
      var CanonicalizeLocaleList_1 = require_CanonicalizeLocaleList();
      var GetOption_1 = require_GetOption();
      var intl_localematcher_1 = require_intl_localematcher();
      var SetNumberFormatUnitOptions_1 = require_SetNumberFormatUnitOptions();
      var CurrencyDigits_1 = require_CurrencyDigits();
      var SetNumberFormatDigitOptions_1 = require_SetNumberFormatDigitOptions();
      var utils_1 = require_utils();
      var CoerceOptionsToObject_1 = require_CoerceOptionsToObject();
      var GetNumberOption_1 = require_GetNumberOption();
      var GetStringOrBooleanOption_1 = require_GetStringOrBooleanOption();
      var VALID_ROUND_INCREMENT_VALUES = [
        1,
        2,
        5,
        10,
        20,
        25,
        50,
        100,
        200,
        250,
        500,
        1e3,
        2e3
      ];
      function InitializeNumberFormat(nf, locales, opts, _a) {
        var getInternalSlots = _a.getInternalSlots, localeData = _a.localeData, availableLocales = _a.availableLocales, numberingSystemNames = _a.numberingSystemNames, getDefaultLocale = _a.getDefaultLocale, currencyDigitsData = _a.currencyDigitsData;
        var requestedLocales = (0, CanonicalizeLocaleList_1.CanonicalizeLocaleList)(locales);
        var options = (0, CoerceOptionsToObject_1.CoerceOptionsToObject)(opts);
        var opt = /* @__PURE__ */ Object.create(null);
        var matcher = (0, GetOption_1.GetOption)(options, "localeMatcher", "string", ["lookup", "best fit"], "best fit");
        opt.localeMatcher = matcher;
        var numberingSystem = (0, GetOption_1.GetOption)(options, "numberingSystem", "string", void 0, void 0);
        if (numberingSystem !== void 0 && numberingSystemNames.indexOf(numberingSystem) < 0) {
          throw RangeError("Invalid numberingSystems: ".concat(numberingSystem));
        }
        opt.nu = numberingSystem;
        var r = (0, intl_localematcher_1.ResolveLocale)(
          availableLocales,
          requestedLocales,
          opt,
          // [[RelevantExtensionKeys]] slot, which is a constant
          ["nu"],
          localeData,
          getDefaultLocale
        );
        var dataLocaleData = localeData[r.dataLocale];
        (0, utils_1.invariant)(!!dataLocaleData, "Missing locale data for ".concat(r.dataLocale));
        var internalSlots = getInternalSlots(nf);
        internalSlots.locale = r.locale;
        internalSlots.dataLocale = r.dataLocale;
        internalSlots.numberingSystem = r.nu;
        internalSlots.dataLocaleData = dataLocaleData;
        (0, SetNumberFormatUnitOptions_1.SetNumberFormatUnitOptions)(nf, options, { getInternalSlots });
        var style = internalSlots.style;
        var mnfdDefault;
        var mxfdDefault;
        if (style === "currency") {
          var currency = internalSlots.currency;
          var cDigits = (0, CurrencyDigits_1.CurrencyDigits)(currency, { currencyDigitsData });
          mnfdDefault = cDigits;
          mxfdDefault = cDigits;
        } else {
          mnfdDefault = 0;
          mxfdDefault = style === "percent" ? 0 : 3;
        }
        var notation = (0, GetOption_1.GetOption)(options, "notation", "string", ["standard", "scientific", "engineering", "compact"], "standard");
        internalSlots.notation = notation;
        (0, SetNumberFormatDigitOptions_1.SetNumberFormatDigitOptions)(internalSlots, options, mnfdDefault, mxfdDefault, notation);
        var roundingIncrement = (0, GetNumberOption_1.GetNumberOption)(options, "roundingIncrement", 1, 5e3, 1);
        if (VALID_ROUND_INCREMENT_VALUES.indexOf(roundingIncrement) === -1) {
          throw new RangeError("Invalid rounding increment value: ".concat(roundingIncrement, ".\nValid values are ").concat(VALID_ROUND_INCREMENT_VALUES, "."));
        }
        if (roundingIncrement !== 1 && internalSlots.roundingType !== "fractionDigits") {
          throw new TypeError("For roundingIncrement > 1 only fractionDigits is a valid roundingType");
        }
        if (roundingIncrement !== 1 && internalSlots.maximumFractionDigits !== internalSlots.minimumFractionDigits) {
          throw new RangeError("With roundingIncrement > 1, maximumFractionDigits and minimumFractionDigits must be equal.");
        }
        internalSlots.roundingIncrement = roundingIncrement;
        var trailingZeroDisplay = (0, GetOption_1.GetOption)(options, "trailingZeroDisplay", "string", ["auto", "stripIfInteger"], "auto");
        internalSlots.trailingZeroDisplay = trailingZeroDisplay;
        var compactDisplay = (0, GetOption_1.GetOption)(options, "compactDisplay", "string", ["short", "long"], "short");
        var defaultUseGrouping = "auto";
        if (notation === "compact") {
          internalSlots.compactDisplay = compactDisplay;
          defaultUseGrouping = "min2";
        }
        internalSlots.useGrouping = (0, GetStringOrBooleanOption_1.GetStringOrBooleanOption)(options, "useGrouping", ["min2", "auto", "always"], "always", false, defaultUseGrouping);
        internalSlots.signDisplay = (0, GetOption_1.GetOption)(options, "signDisplay", "string", ["auto", "never", "always", "exceptZero", "negative"], "auto");
        internalSlots.roundingMode = (0, GetOption_1.GetOption)(options, "roundingMode", "string", [
          "ceil",
          "floor",
          "expand",
          "trunc",
          "halfCeil",
          "halfFloor",
          "halfExpand",
          "halfTrunc",
          "halfEven"
        ], "halfExpand");
        return nf;
      }
      exports.InitializeNumberFormat = InitializeNumberFormat;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/PartitionNumberRangePattern.js
  var require_PartitionNumberRangePattern = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/NumberFormat/PartitionNumberRangePattern.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.PartitionNumberRangePattern = void 0;
      var _262_1 = require__();
      var PartitionNumberPattern_1 = require_PartitionNumberPattern();
      var CollapseNumberRange_1 = require_CollapseNumberRange();
      var FormatApproximately_1 = require_FormatApproximately();
      function PartitionNumberRangePattern(numberFormat, x, y, _a) {
        var getInternalSlots = _a.getInternalSlots;
        var internalSlots = getInternalSlots(numberFormat);
        if (isNaN(x) || isNaN(y)) {
          throw new RangeError("Input must be a number");
        }
        if (isFinite(x)) {
          if (isFinite(y) && y < x) {
            throw new RangeError("Y input must be bigger than X");
          } else if (y == Number.NEGATIVE_INFINITY) {
            throw new RangeError("Y input must not be NegativeInfinity");
          } else if ((0, _262_1.SameValue)(y, -0) && x >= 0) {
            throw new RangeError("Y input must be bigger than X");
          }
        } else if (x == Number.POSITIVE_INFINITY) {
          if (isFinite(y) || y == Number.NEGATIVE_INFINITY || (0, _262_1.SameValue)(y, -0)) {
            throw new RangeError("Y input must be bigger than X");
          }
        } else if ((0, _262_1.SameValue)(x, -0)) {
          if (isFinite(y) && y < 0) {
            throw new RangeError("Y input must be bigger than X");
          } else if (y == Number.NEGATIVE_INFINITY) {
            throw new RangeError("Y input must be bigger than X");
          }
        }
        var result = [];
        var xResult = (0, PartitionNumberPattern_1.PartitionNumberPattern)(numberFormat, x, { getInternalSlots });
        var yResult = (0, PartitionNumberPattern_1.PartitionNumberPattern)(numberFormat, y, { getInternalSlots });
        if (xResult === yResult) {
          return (0, FormatApproximately_1.FormatApproximately)(numberFormat, xResult, { getInternalSlots });
        }
        for (var _i = 0, xResult_1 = xResult; _i < xResult_1.length; _i++) {
          var r = xResult_1[_i];
          r.source = "startRange";
        }
        result = result.concat(xResult);
        var symbols = internalSlots.dataLocaleData.numbers.symbols[internalSlots.numberingSystem];
        var rangeSeparator = symbols.timeSeparator;
        result.push({ type: "literal", value: rangeSeparator, source: "shared" });
        for (var _b = 0, yResult_1 = yResult; _b < yResult_1.length; _b++) {
          var r = yResult_1[_b];
          r.source = "endRange";
        }
        result = result.concat(yResult);
        return (0, CollapseNumberRange_1.CollapseNumberRange)(result);
      }
      exports.PartitionNumberRangePattern = PartitionNumberRangePattern;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/PartitionPattern.js
  var require_PartitionPattern = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/PartitionPattern.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.PartitionPattern = void 0;
      var utils_1 = require_utils();
      function PartitionPattern(pattern) {
        var result = [];
        var beginIndex = pattern.indexOf("{");
        var endIndex = 0;
        var nextIndex = 0;
        var length = pattern.length;
        while (beginIndex < pattern.length && beginIndex > -1) {
          endIndex = pattern.indexOf("}", beginIndex);
          (0, utils_1.invariant)(endIndex > beginIndex, "Invalid pattern ".concat(pattern));
          if (beginIndex > nextIndex) {
            result.push({
              type: "literal",
              value: pattern.substring(nextIndex, beginIndex)
            });
          }
          result.push({
            type: pattern.substring(beginIndex + 1, endIndex),
            value: void 0
          });
          nextIndex = endIndex + 1;
          beginIndex = pattern.indexOf("{", nextIndex);
        }
        if (nextIndex < length) {
          result.push({
            type: "literal",
            value: pattern.substring(nextIndex, length)
          });
        }
        return result;
      }
      exports.PartitionPattern = PartitionPattern;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/SupportedLocales.js
  var require_SupportedLocales = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/SupportedLocales.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.SupportedLocales = void 0;
      var _262_1 = require__();
      var GetOption_1 = require_GetOption();
      var intl_localematcher_1 = require_intl_localematcher();
      function SupportedLocales(availableLocales, requestedLocales, options) {
        var matcher = "best fit";
        if (options !== void 0) {
          options = (0, _262_1.ToObject)(options);
          matcher = (0, GetOption_1.GetOption)(options, "localeMatcher", "string", ["lookup", "best fit"], "best fit");
        }
        if (matcher === "best fit") {
          return (0, intl_localematcher_1.LookupSupportedLocales)(availableLocales, requestedLocales);
        }
        return (0, intl_localematcher_1.LookupSupportedLocales)(availableLocales, requestedLocales);
      }
      exports.SupportedLocales = SupportedLocales;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/data.js
  var require_data = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/data.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.isMissingLocaleDataError = void 0;
      var tslib_1 = (init_tslib_es6(), __toCommonJS(tslib_es6_exports));
      var MissingLocaleDataError = (
        /** @class */
        function(_super) {
          tslib_1.__extends(MissingLocaleDataError2, _super);
          function MissingLocaleDataError2() {
            var _this = _super !== null && _super.apply(this, arguments) || this;
            _this.type = "MISSING_LOCALE_DATA";
            return _this;
          }
          return MissingLocaleDataError2;
        }(Error)
      );
      function isMissingLocaleDataError(e) {
        return e.type === "MISSING_LOCALE_DATA";
      }
      exports.isMissingLocaleDataError = isMissingLocaleDataError;
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/types/relative-time.js
  var require_relative_time = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/types/relative-time.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/types/date-time.js
  var require_date_time = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/types/date-time.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.RangePatternType = void 0;
      var RangePatternType;
      (function(RangePatternType2) {
        RangePatternType2["startRange"] = "startRange";
        RangePatternType2["shared"] = "shared";
        RangePatternType2["endRange"] = "endRange";
      })(RangePatternType = exports.RangePatternType || (exports.RangePatternType = {}));
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/types/list.js
  var require_list = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/types/list.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/types/plural-rules.js
  var require_plural_rules = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/types/plural-rules.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/types/number.js
  var require_number = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/types/number.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/types/displaynames.js
  var require_displaynames = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/types/displaynames.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
    }
  });

  // node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/index.js
  var require_ecma402_abstract = __commonJS({
    "node_modules/@formatjs/intl-locale/node_modules/@formatjs/ecma402-abstract/index.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.invariant = exports.isMissingLocaleDataError = exports.defineProperty = exports.getMagnitude = exports.setMultiInternalSlots = exports.setInternalSlot = exports.isLiteralPart = exports.getMultiInternalSlots = exports.getInternalSlot = exports._formatToParts = void 0;
      var tslib_1 = (init_tslib_es6(), __toCommonJS(tslib_es6_exports));
      tslib_1.__exportStar(require_CanonicalizeLocaleList(), exports);
      tslib_1.__exportStar(require_CanonicalizeTimeZoneName(), exports);
      tslib_1.__exportStar(require_CoerceOptionsToObject(), exports);
      tslib_1.__exportStar(require_GetNumberOption(), exports);
      tslib_1.__exportStar(require_GetOption(), exports);
      tslib_1.__exportStar(require_GetOptionsObject(), exports);
      tslib_1.__exportStar(require_GetStringOrBooleanOption(), exports);
      tslib_1.__exportStar(require_IsSanctionedSimpleUnitIdentifier(), exports);
      tslib_1.__exportStar(require_IsValidTimeZoneName(), exports);
      tslib_1.__exportStar(require_IsWellFormedCurrencyCode(), exports);
      tslib_1.__exportStar(require_IsWellFormedUnitIdentifier(), exports);
      tslib_1.__exportStar(require_ApplyUnsignedRoundingMode(), exports);
      tslib_1.__exportStar(require_CollapseNumberRange(), exports);
      tslib_1.__exportStar(require_ComputeExponent(), exports);
      tslib_1.__exportStar(require_ComputeExponentForMagnitude(), exports);
      tslib_1.__exportStar(require_CurrencyDigits(), exports);
      tslib_1.__exportStar(require_FormatApproximately(), exports);
      tslib_1.__exportStar(require_FormatNumericToParts(), exports);
      tslib_1.__exportStar(require_FormatNumericToString(), exports);
      tslib_1.__exportStar(require_GetUnsignedRoundingMode(), exports);
      tslib_1.__exportStar(require_InitializeNumberFormat(), exports);
      tslib_1.__exportStar(require_PartitionNumberPattern(), exports);
      tslib_1.__exportStar(require_PartitionNumberRangePattern(), exports);
      tslib_1.__exportStar(require_SetNumberFormatDigitOptions(), exports);
      tslib_1.__exportStar(require_SetNumberFormatUnitOptions(), exports);
      tslib_1.__exportStar(require_ToRawFixed(), exports);
      tslib_1.__exportStar(require_ToRawPrecision(), exports);
      var format_to_parts_1 = require_format_to_parts();
      Object.defineProperty(exports, "_formatToParts", { enumerable: true, get: function() {
        return tslib_1.__importDefault(format_to_parts_1).default;
      } });
      tslib_1.__exportStar(require_PartitionPattern(), exports);
      tslib_1.__exportStar(require_SupportedLocales(), exports);
      var utils_1 = require_utils();
      Object.defineProperty(exports, "getInternalSlot", { enumerable: true, get: function() {
        return utils_1.getInternalSlot;
      } });
      Object.defineProperty(exports, "getMultiInternalSlots", { enumerable: true, get: function() {
        return utils_1.getMultiInternalSlots;
      } });
      Object.defineProperty(exports, "isLiteralPart", { enumerable: true, get: function() {
        return utils_1.isLiteralPart;
      } });
      Object.defineProperty(exports, "setInternalSlot", { enumerable: true, get: function() {
        return utils_1.setInternalSlot;
      } });
      Object.defineProperty(exports, "setMultiInternalSlots", { enumerable: true, get: function() {
        return utils_1.setMultiInternalSlots;
      } });
      Object.defineProperty(exports, "getMagnitude", { enumerable: true, get: function() {
        return utils_1.getMagnitude;
      } });
      Object.defineProperty(exports, "defineProperty", { enumerable: true, get: function() {
        return utils_1.defineProperty;
      } });
      var data_1 = require_data();
      Object.defineProperty(exports, "isMissingLocaleDataError", { enumerable: true, get: function() {
        return data_1.isMissingLocaleDataError;
      } });
      tslib_1.__exportStar(require_relative_time(), exports);
      tslib_1.__exportStar(require_date_time(), exports);
      tslib_1.__exportStar(require_list(), exports);
      tslib_1.__exportStar(require_plural_rules(), exports);
      tslib_1.__exportStar(require_number(), exports);
      tslib_1.__exportStar(require_displaynames(), exports);
      var utils_2 = require_utils();
      Object.defineProperty(exports, "invariant", { enumerable: true, get: function() {
        return utils_2.invariant;
      } });
      tslib_1.__exportStar(require__(), exports);
    }
  });

  // node_modules/@formatjs/intl-getcanonicallocales/src/parser.js
  var require_parser = __commonJS({
    "node_modules/@formatjs/intl-getcanonicallocales/src/parser.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.parseUnicodeLocaleId = exports.parseUnicodeLanguageId = exports.isUnicodeVariantSubtag = exports.isUnicodeScriptSubtag = exports.isUnicodeRegionSubtag = exports.isStructurallyValidLanguageTag = exports.isUnicodeLanguageSubtag = exports.SEPARATOR = void 0;
      var tslib_1 = (init_tslib_es6(), __toCommonJS(tslib_es6_exports));
      var ALPHANUM_1_8 = /^[a-z0-9]{1,8}$/i;
      var ALPHANUM_2_8 = /^[a-z0-9]{2,8}$/i;
      var ALPHANUM_3_8 = /^[a-z0-9]{3,8}$/i;
      var KEY_REGEX = /^[a-z0-9][a-z]$/i;
      var TYPE_REGEX = /^[a-z0-9]{3,8}$/i;
      var ALPHA_4 = /^[a-z]{4}$/i;
      var OTHER_EXTENSION_TYPE = /^[0-9a-svwyz]$/i;
      var UNICODE_REGION_SUBTAG_REGEX = /^([a-z]{2}|[0-9]{3})$/i;
      var UNICODE_VARIANT_SUBTAG_REGEX = /^([a-z0-9]{5,8}|[0-9][a-z0-9]{3})$/i;
      var UNICODE_LANGUAGE_SUBTAG_REGEX = /^([a-z]{2,3}|[a-z]{5,8})$/i;
      var TKEY_REGEX = /^[a-z][0-9]$/i;
      exports.SEPARATOR = "-";
      function isUnicodeLanguageSubtag(lang) {
        return UNICODE_LANGUAGE_SUBTAG_REGEX.test(lang);
      }
      exports.isUnicodeLanguageSubtag = isUnicodeLanguageSubtag;
      function isStructurallyValidLanguageTag(tag) {
        try {
          parseUnicodeLanguageId(tag.split(exports.SEPARATOR));
        } catch (e) {
          return false;
        }
        return true;
      }
      exports.isStructurallyValidLanguageTag = isStructurallyValidLanguageTag;
      function isUnicodeRegionSubtag(region) {
        return UNICODE_REGION_SUBTAG_REGEX.test(region);
      }
      exports.isUnicodeRegionSubtag = isUnicodeRegionSubtag;
      function isUnicodeScriptSubtag(script) {
        return ALPHA_4.test(script);
      }
      exports.isUnicodeScriptSubtag = isUnicodeScriptSubtag;
      function isUnicodeVariantSubtag(variant) {
        return UNICODE_VARIANT_SUBTAG_REGEX.test(variant);
      }
      exports.isUnicodeVariantSubtag = isUnicodeVariantSubtag;
      function parseUnicodeLanguageId(chunks) {
        if (typeof chunks === "string") {
          chunks = chunks.split(exports.SEPARATOR);
        }
        var lang = chunks.shift();
        if (!lang) {
          throw new RangeError("Missing unicode_language_subtag");
        }
        if (lang === "root") {
          return { lang: "root", variants: [] };
        }
        if (!isUnicodeLanguageSubtag(lang)) {
          throw new RangeError("Malformed unicode_language_subtag");
        }
        var script;
        if (chunks.length && isUnicodeScriptSubtag(chunks[0])) {
          script = chunks.shift();
        }
        var region;
        if (chunks.length && isUnicodeRegionSubtag(chunks[0])) {
          region = chunks.shift();
        }
        var variants = {};
        while (chunks.length && isUnicodeVariantSubtag(chunks[0])) {
          var variant = chunks.shift();
          if (variant in variants) {
            throw new RangeError('Duplicate variant "'.concat(variant, '"'));
          }
          variants[variant] = 1;
        }
        return {
          lang,
          script,
          region,
          variants: Object.keys(variants)
        };
      }
      exports.parseUnicodeLanguageId = parseUnicodeLanguageId;
      function parseUnicodeExtension(chunks) {
        var keywords = [];
        var keyword;
        while (chunks.length && (keyword = parseKeyword(chunks))) {
          keywords.push(keyword);
        }
        if (keywords.length) {
          return {
            type: "u",
            keywords,
            attributes: []
          };
        }
        var attributes = [];
        while (chunks.length && ALPHANUM_3_8.test(chunks[0])) {
          attributes.push(chunks.shift());
        }
        while (chunks.length && (keyword = parseKeyword(chunks))) {
          keywords.push(keyword);
        }
        if (keywords.length || attributes.length) {
          return {
            type: "u",
            attributes,
            keywords
          };
        }
        throw new RangeError("Malformed unicode_extension");
      }
      function parseKeyword(chunks) {
        var key;
        if (!KEY_REGEX.test(chunks[0])) {
          return;
        }
        key = chunks.shift();
        var type = [];
        while (chunks.length && TYPE_REGEX.test(chunks[0])) {
          type.push(chunks.shift());
        }
        var value = "";
        if (type.length) {
          value = type.join(exports.SEPARATOR);
        }
        return [key, value];
      }
      function parseTransformedExtension(chunks) {
        var lang;
        try {
          lang = parseUnicodeLanguageId(chunks);
        } catch (e) {
        }
        var fields = [];
        while (chunks.length && TKEY_REGEX.test(chunks[0])) {
          var key = chunks.shift();
          var value = [];
          while (chunks.length && ALPHANUM_3_8.test(chunks[0])) {
            value.push(chunks.shift());
          }
          if (!value.length) {
            throw new RangeError('Missing tvalue for tkey "'.concat(key, '"'));
          }
          fields.push([key, value.join(exports.SEPARATOR)]);
        }
        if (fields.length) {
          return {
            type: "t",
            fields,
            lang
          };
        }
        throw new RangeError("Malformed transformed_extension");
      }
      function parsePuExtension(chunks) {
        var exts = [];
        while (chunks.length && ALPHANUM_1_8.test(chunks[0])) {
          exts.push(chunks.shift());
        }
        if (exts.length) {
          return {
            type: "x",
            value: exts.join(exports.SEPARATOR)
          };
        }
        throw new RangeError("Malformed private_use_extension");
      }
      function parseOtherExtensionValue(chunks) {
        var exts = [];
        while (chunks.length && ALPHANUM_2_8.test(chunks[0])) {
          exts.push(chunks.shift());
        }
        if (exts.length) {
          return exts.join(exports.SEPARATOR);
        }
        return "";
      }
      function parseExtensions(chunks) {
        if (!chunks.length) {
          return { extensions: [] };
        }
        var extensions = [];
        var unicodeExtension;
        var transformedExtension;
        var puExtension;
        var otherExtensionMap = {};
        do {
          var type = chunks.shift();
          switch (type) {
            case "u":
            case "U":
              if (unicodeExtension) {
                throw new RangeError("There can only be 1 -u- extension");
              }
              unicodeExtension = parseUnicodeExtension(chunks);
              extensions.push(unicodeExtension);
              break;
            case "t":
            case "T":
              if (transformedExtension) {
                throw new RangeError("There can only be 1 -t- extension");
              }
              transformedExtension = parseTransformedExtension(chunks);
              extensions.push(transformedExtension);
              break;
            case "x":
            case "X":
              if (puExtension) {
                throw new RangeError("There can only be 1 -x- extension");
              }
              puExtension = parsePuExtension(chunks);
              extensions.push(puExtension);
              break;
            default:
              if (!OTHER_EXTENSION_TYPE.test(type)) {
                throw new RangeError("Malformed extension type");
              }
              if (type in otherExtensionMap) {
                throw new RangeError("There can only be 1 -".concat(type, "- extension"));
              }
              var extension = {
                type,
                value: parseOtherExtensionValue(chunks)
              };
              otherExtensionMap[extension.type] = extension;
              extensions.push(extension);
              break;
          }
        } while (chunks.length);
        return { extensions };
      }
      function parseUnicodeLocaleId(locale) {
        var chunks = locale.split(exports.SEPARATOR);
        var lang = parseUnicodeLanguageId(chunks);
        return tslib_1.__assign({ lang }, parseExtensions(chunks));
      }
      exports.parseUnicodeLocaleId = parseUnicodeLocaleId;
    }
  });

  // node_modules/@formatjs/intl-getcanonicallocales/src/emitter.js
  var require_emitter = __commonJS({
    "node_modules/@formatjs/intl-getcanonicallocales/src/emitter.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.emitUnicodeLocaleId = exports.emitUnicodeLanguageId = void 0;
      var tslib_1 = (init_tslib_es6(), __toCommonJS(tslib_es6_exports));
      function emitUnicodeLanguageId(lang) {
        if (!lang) {
          return "";
        }
        return tslib_1.__spreadArray([lang.lang, lang.script, lang.region], lang.variants || [], true).filter(Boolean).join("-");
      }
      exports.emitUnicodeLanguageId = emitUnicodeLanguageId;
      function emitUnicodeLocaleId(_a) {
        var lang = _a.lang, extensions = _a.extensions;
        var chunks = [emitUnicodeLanguageId(lang)];
        for (var _i = 0, extensions_1 = extensions; _i < extensions_1.length; _i++) {
          var ext = extensions_1[_i];
          chunks.push(ext.type);
          switch (ext.type) {
            case "u":
              chunks.push.apply(chunks, tslib_1.__spreadArray(tslib_1.__spreadArray([], ext.attributes, false), ext.keywords.reduce(function(all, kv) {
                return all.concat(kv);
              }, []), false));
              break;
            case "t":
              chunks.push.apply(chunks, tslib_1.__spreadArray([emitUnicodeLanguageId(ext.lang)], ext.fields.reduce(function(all, kv) {
                return all.concat(kv);
              }, []), false));
              break;
            default:
              chunks.push(ext.value);
              break;
          }
        }
        return chunks.filter(Boolean).join("-");
      }
      exports.emitUnicodeLocaleId = emitUnicodeLocaleId;
    }
  });

  // node_modules/@formatjs/intl-getcanonicallocales/src/aliases.generated.js
  var require_aliases_generated = __commonJS({
    "node_modules/@formatjs/intl-getcanonicallocales/src/aliases.generated.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.variantAlias = exports.scriptAlias = exports.territoryAlias = exports.languageAlias = void 0;
      exports.languageAlias = {
        "aa-saaho": "ssy",
        "aam": "aas",
        "aar": "aa",
        "abk": "ab",
        "adp": "dz",
        "afr": "af",
        "agp": "apf",
        "ais": "ami",
        "aju": "jrb",
        "aka": "ak",
        "alb": "sq",
        "als": "sq",
        "amh": "am",
        "ara": "ar",
        "arb": "ar",
        "arg": "an",
        "arm": "hy",
        "art-lojban": "jbo",
        "asd": "snz",
        "asm": "as",
        "aue": "ktz",
        "ava": "av",
        "ave": "ae",
        "aym": "ay",
        "ayr": "ay",
        "ayx": "nun",
        "aze": "az",
        "azj": "az",
        "bak": "ba",
        "bam": "bm",
        "baq": "eu",
        "baz": "nvo",
        "bcc": "bal",
        "bcl": "bik",
        "bel": "be",
        "ben": "bn",
        "bgm": "bcg",
        "bh": "bho",
        "bhk": "fbl",
        "bic": "bir",
        "bih": "bho",
        "bis": "bi",
        "bjd": "drl",
        "bjq": "bzc",
        "bkb": "ebk",
        "blg": "iba",
        "bod": "bo",
        "bos": "bs",
        "bre": "br",
        "btb": "beb",
        "bul": "bg",
        "bur": "my",
        "bxk": "luy",
        "bxr": "bua",
        "cat": "ca",
        "ccq": "rki",
        "cel-gaulish": "xtg",
        "ces": "cs",
        "cha": "ch",
        "che": "ce",
        "chi": "zh",
        "chu": "cu",
        "chv": "cv",
        "cjr": "mom",
        "cka": "cmr",
        "cld": "syr",
        "cmk": "xch",
        "cmn": "zh",
        "cnr": "sr-ME",
        "cor": "kw",
        "cos": "co",
        "coy": "pij",
        "cqu": "quh",
        "cre": "cr",
        "cwd": "cr",
        "cym": "cy",
        "cze": "cs",
        "daf": "dnj",
        "dan": "da",
        "dap": "njz",
        "deu": "de",
        "dgo": "doi",
        "dhd": "mwr",
        "dik": "din",
        "diq": "zza",
        "dit": "dif",
        "div": "dv",
        "djl": "dze",
        "dkl": "aqd",
        "drh": "mn",
        "drr": "kzk",
        "drw": "fa-AF",
        "dud": "uth",
        "duj": "dwu",
        "dut": "nl",
        "dwl": "dbt",
        "dzo": "dz",
        "ekk": "et",
        "ell": "el",
        "elp": "amq",
        "emk": "man",
        "en-GB-oed": "en-GB-oxendict",
        "eng": "en",
        "epo": "eo",
        "esk": "ik",
        "est": "et",
        "eus": "eu",
        "ewe": "ee",
        "fao": "fo",
        "fas": "fa",
        "fat": "ak",
        "fij": "fj",
        "fin": "fi",
        "fra": "fr",
        "fre": "fr",
        "fry": "fy",
        "fuc": "ff",
        "ful": "ff",
        "gav": "dev",
        "gaz": "om",
        "gbc": "wny",
        "gbo": "grb",
        "geo": "ka",
        "ger": "de",
        "gfx": "vaj",
        "ggn": "gvr",
        "ggo": "esg",
        "ggr": "gtu",
        "gio": "aou",
        "gla": "gd",
        "gle": "ga",
        "glg": "gl",
        "gli": "kzk",
        "glv": "gv",
        "gno": "gon",
        "gre": "el",
        "grn": "gn",
        "gti": "nyc",
        "gug": "gn",
        "guj": "gu",
        "guv": "duz",
        "gya": "gba",
        "hat": "ht",
        "hau": "ha",
        "hbs": "sr-Latn",
        "hdn": "hai",
        "hea": "hmn",
        "heb": "he",
        "her": "hz",
        "him": "srx",
        "hin": "hi",
        "hmo": "ho",
        "hrr": "jal",
        "hrv": "hr",
        "hun": "hu",
        "hy-arevmda": "hyw",
        "hye": "hy",
        "i-ami": "ami",
        "i-bnn": "bnn",
        "i-default": "en-x-i-default",
        "i-enochian": "und-x-i-enochian",
        "i-hak": "hak",
        "i-klingon": "tlh",
        "i-lux": "lb",
        "i-mingo": "see-x-i-mingo",
        "i-navajo": "nv",
        "i-pwn": "pwn",
        "i-tao": "tao",
        "i-tay": "tay",
        "i-tsu": "tsu",
        "ibi": "opa",
        "ibo": "ig",
        "ice": "is",
        "ido": "io",
        "iii": "ii",
        "ike": "iu",
        "iku": "iu",
        "ile": "ie",
        "ill": "ilm",
        "ilw": "gal",
        "in": "id",
        "ina": "ia",
        "ind": "id",
        "ipk": "ik",
        "isl": "is",
        "ita": "it",
        "iw": "he",
        "izi": "eza",
        "jar": "jgk",
        "jav": "jv",
        "jeg": "oyb",
        "ji": "yi",
        "jpn": "ja",
        "jw": "jv",
        "kal": "kl",
        "kan": "kn",
        "kas": "ks",
        "kat": "ka",
        "kau": "kr",
        "kaz": "kk",
        "kdv": "zkd",
        "kgc": "tdf",
        "kgd": "ncq",
        "kgh": "kml",
        "khk": "mn",
        "khm": "km",
        "kik": "ki",
        "kin": "rw",
        "kir": "ky",
        "kmr": "ku",
        "knc": "kr",
        "kng": "kg",
        "knn": "kok",
        "koj": "kwv",
        "kom": "kv",
        "kon": "kg",
        "kor": "ko",
        "kpp": "jkm",
        "kpv": "kv",
        "krm": "bmf",
        "ktr": "dtp",
        "kua": "kj",
        "kur": "ku",
        "kvs": "gdj",
        "kwq": "yam",
        "kxe": "tvd",
        "kxl": "kru",
        "kzh": "dgl",
        "kzj": "dtp",
        "kzt": "dtp",
        "lao": "lo",
        "lat": "la",
        "lav": "lv",
        "lbk": "bnc",
        "leg": "enl",
        "lii": "raq",
        "lim": "li",
        "lin": "ln",
        "lit": "lt",
        "llo": "ngt",
        "lmm": "rmx",
        "ltz": "lb",
        "lub": "lu",
        "lug": "lg",
        "lvs": "lv",
        "mac": "mk",
        "mah": "mh",
        "mal": "ml",
        "mao": "mi",
        "mar": "mr",
        "may": "ms",
        "meg": "cir",
        "mgx": "jbk",
        "mhr": "chm",
        "mkd": "mk",
        "mlg": "mg",
        "mlt": "mt",
        "mnk": "man",
        "mnt": "wnn",
        "mo": "ro",
        "mof": "xnt",
        "mol": "ro",
        "mon": "mn",
        "mri": "mi",
        "msa": "ms",
        "mst": "mry",
        "mup": "raj",
        "mwd": "dmw",
        "mwj": "vaj",
        "mya": "my",
        "myd": "aog",
        "myt": "mry",
        "nad": "xny",
        "nau": "na",
        "nav": "nv",
        "nbf": "nru",
        "nbl": "nr",
        "nbx": "ekc",
        "ncp": "kdz",
        "nde": "nd",
        "ndo": "ng",
        "nep": "ne",
        "nld": "nl",
        "nln": "azd",
        "nlr": "nrk",
        "nno": "nn",
        "nns": "nbr",
        "nnx": "ngv",
        "no-bok": "nb",
        "no-bokmal": "nb",
        "no-nyn": "nn",
        "no-nynorsk": "nn",
        "nob": "nb",
        "noo": "dtd",
        "nor": "no",
        "npi": "ne",
        "nts": "pij",
        "nxu": "bpp",
        "nya": "ny",
        "oci": "oc",
        "ojg": "oj",
        "oji": "oj",
        "ori": "or",
        "orm": "om",
        "ory": "or",
        "oss": "os",
        "oun": "vaj",
        "pan": "pa",
        "pat": "kxr",
        "pbu": "ps",
        "pcr": "adx",
        "per": "fa",
        "pes": "fa",
        "pli": "pi",
        "plt": "mg",
        "pmc": "huw",
        "pmu": "phr",
        "pnb": "lah",
        "pol": "pl",
        "por": "pt",
        "ppa": "bfy",
        "ppr": "lcq",
        "prs": "fa-AF",
        "pry": "prt",
        "pus": "ps",
        "puz": "pub",
        "que": "qu",
        "quz": "qu",
        "rmr": "emx",
        "rmy": "rom",
        "roh": "rm",
        "ron": "ro",
        "rum": "ro",
        "run": "rn",
        "rus": "ru",
        "sag": "sg",
        "san": "sa",
        "sap": "aqt",
        "sca": "hle",
        "scc": "sr",
        "scr": "hr",
        "sgl": "isk",
        "sgn-BE-FR": "sfb",
        "sgn-BE-NL": "vgt",
        "sgn-BR": "bzs",
        "sgn-CH-DE": "sgg",
        "sgn-CO": "csn",
        "sgn-DE": "gsg",
        "sgn-DK": "dsl",
        "sgn-ES": "ssp",
        "sgn-FR": "fsl",
        "sgn-GB": "bfi",
        "sgn-GR": "gss",
        "sgn-IE": "isg",
        "sgn-IT": "ise",
        "sgn-JP": "jsl",
        "sgn-MX": "mfs",
        "sgn-NI": "ncs",
        "sgn-NL": "dse",
        "sgn-NO": "nsi",
        "sgn-PT": "psr",
        "sgn-SE": "swl",
        "sgn-US": "ase",
        "sgn-ZA": "sfs",
        "sh": "sr-Latn",
        "sin": "si",
        "skk": "oyb",
        "slk": "sk",
        "slo": "sk",
        "slv": "sl",
        "sme": "se",
        "smo": "sm",
        "sna": "sn",
        "snd": "sd",
        "som": "so",
        "sot": "st",
        "spa": "es",
        "spy": "kln",
        "sqi": "sq",
        "src": "sc",
        "srd": "sc",
        "srp": "sr",
        "ssw": "ss",
        "sul": "sgd",
        "sum": "ulw",
        "sun": "su",
        "swa": "sw",
        "swc": "sw-CD",
        "swe": "sv",
        "swh": "sw",
        "tah": "ty",
        "tam": "ta",
        "tat": "tt",
        "tdu": "dtp",
        "tel": "te",
        "tgg": "bjp",
        "tgk": "tg",
        "tgl": "fil",
        "tha": "th",
        "thc": "tpo",
        "thw": "ola",
        "thx": "oyb",
        "tib": "bo",
        "tid": "itd",
        "tie": "ras",
        "tir": "ti",
        "tkk": "twm",
        "tl": "fil",
        "tlw": "weo",
        "tmp": "tyj",
        "tne": "kak",
        "tnf": "fa-AF",
        "ton": "to",
        "tsf": "taj",
        "tsn": "tn",
        "tso": "ts",
        "ttq": "tmh",
        "tuk": "tk",
        "tur": "tr",
        "tw": "ak",
        "twi": "ak",
        "uig": "ug",
        "ukr": "uk",
        "umu": "del",
        "und-aaland": "und-AX",
        "und-arevela": "und",
        "und-arevmda": "und",
        "und-bokmal": "und",
        "und-hakka": "und",
        "und-hepburn-heploc": "und-alalc97",
        "und-lojban": "und",
        "und-nynorsk": "und",
        "und-saaho": "und",
        "und-xiang": "und",
        "unp": "wro",
        "uok": "ema",
        "urd": "ur",
        "uzb": "uz",
        "uzn": "uz",
        "ven": "ve",
        "vie": "vi",
        "vol": "vo",
        "wel": "cy",
        "wgw": "wgb",
        "wit": "nol",
        "wiw": "nwo",
        "wln": "wa",
        "wol": "wo",
        "xba": "cax",
        "xho": "xh",
        "xia": "acn",
        "xkh": "waw",
        "xpe": "kpe",
        "xrq": "dmw",
        "xsj": "suj",
        "xsl": "den",
        "ybd": "rki",
        "ydd": "yi",
        "yen": "ynq",
        "yid": "yi",
        "yiy": "yrm",
        "yma": "lrr",
        "ymt": "mtm",
        "yor": "yo",
        "yos": "zom",
        "yuu": "yug",
        "zai": "zap",
        "zh-cmn": "zh",
        "zh-cmn-Hans": "zh-Hans",
        "zh-cmn-Hant": "zh-Hant",
        "zh-gan": "gan",
        "zh-guoyu": "zh",
        "zh-hakka": "hak",
        "zh-min": "nan-x-zh-min",
        "zh-min-nan": "nan",
        "zh-wuu": "wuu",
        "zh-xiang": "hsn",
        "zh-yue": "yue",
        "zha": "za",
        "zho": "zh",
        "zir": "scv",
        "zsm": "ms",
        "zul": "zu",
        "zyb": "za"
      };
      exports.territoryAlias = {
        "100": "BG",
        "104": "MM",
        "108": "BI",
        "112": "BY",
        "116": "KH",
        "120": "CM",
        "124": "CA",
        "132": "CV",
        "136": "KY",
        "140": "CF",
        "144": "LK",
        "148": "TD",
        "152": "CL",
        "156": "CN",
        "158": "TW",
        "162": "CX",
        "166": "CC",
        "170": "CO",
        "172": "RU AM AZ BY GE KG KZ MD TJ TM UA UZ",
        "174": "KM",
        "175": "YT",
        "178": "CG",
        "180": "CD",
        "184": "CK",
        "188": "CR",
        "191": "HR",
        "192": "CU",
        "196": "CY",
        "200": "CZ SK",
        "203": "CZ",
        "204": "BJ",
        "208": "DK",
        "212": "DM",
        "214": "DO",
        "218": "EC",
        "222": "SV",
        "226": "GQ",
        "230": "ET",
        "231": "ET",
        "232": "ER",
        "233": "EE",
        "234": "FO",
        "238": "FK",
        "239": "GS",
        "242": "FJ",
        "246": "FI",
        "248": "AX",
        "249": "FR",
        "250": "FR",
        "254": "GF",
        "258": "PF",
        "260": "TF",
        "262": "DJ",
        "266": "GA",
        "268": "GE",
        "270": "GM",
        "275": "PS",
        "276": "DE",
        "278": "DE",
        "280": "DE",
        "288": "GH",
        "292": "GI",
        "296": "KI",
        "300": "GR",
        "304": "GL",
        "308": "GD",
        "312": "GP",
        "316": "GU",
        "320": "GT",
        "324": "GN",
        "328": "GY",
        "332": "HT",
        "334": "HM",
        "336": "VA",
        "340": "HN",
        "344": "HK",
        "348": "HU",
        "352": "IS",
        "356": "IN",
        "360": "ID",
        "364": "IR",
        "368": "IQ",
        "372": "IE",
        "376": "IL",
        "380": "IT",
        "384": "CI",
        "388": "JM",
        "392": "JP",
        "398": "KZ",
        "400": "JO",
        "404": "KE",
        "408": "KP",
        "410": "KR",
        "414": "KW",
        "417": "KG",
        "418": "LA",
        "422": "LB",
        "426": "LS",
        "428": "LV",
        "430": "LR",
        "434": "LY",
        "438": "LI",
        "440": "LT",
        "442": "LU",
        "446": "MO",
        "450": "MG",
        "454": "MW",
        "458": "MY",
        "462": "MV",
        "466": "ML",
        "470": "MT",
        "474": "MQ",
        "478": "MR",
        "480": "MU",
        "484": "MX",
        "492": "MC",
        "496": "MN",
        "498": "MD",
        "499": "ME",
        "500": "MS",
        "504": "MA",
        "508": "MZ",
        "512": "OM",
        "516": "NA",
        "520": "NR",
        "524": "NP",
        "528": "NL",
        "530": "CW SX BQ",
        "531": "CW",
        "532": "CW SX BQ",
        "533": "AW",
        "534": "SX",
        "535": "BQ",
        "536": "SA IQ",
        "540": "NC",
        "548": "VU",
        "554": "NZ",
        "558": "NI",
        "562": "NE",
        "566": "NG",
        "570": "NU",
        "574": "NF",
        "578": "NO",
        "580": "MP",
        "581": "UM",
        "582": "FM MH MP PW",
        "583": "FM",
        "584": "MH",
        "585": "PW",
        "586": "PK",
        "591": "PA",
        "598": "PG",
        "600": "PY",
        "604": "PE",
        "608": "PH",
        "612": "PN",
        "616": "PL",
        "620": "PT",
        "624": "GW",
        "626": "TL",
        "630": "PR",
        "634": "QA",
        "638": "RE",
        "642": "RO",
        "643": "RU",
        "646": "RW",
        "652": "BL",
        "654": "SH",
        "659": "KN",
        "660": "AI",
        "662": "LC",
        "663": "MF",
        "666": "PM",
        "670": "VC",
        "674": "SM",
        "678": "ST",
        "682": "SA",
        "686": "SN",
        "688": "RS",
        "690": "SC",
        "694": "SL",
        "702": "SG",
        "703": "SK",
        "704": "VN",
        "705": "SI",
        "706": "SO",
        "710": "ZA",
        "716": "ZW",
        "720": "YE",
        "724": "ES",
        "728": "SS",
        "729": "SD",
        "732": "EH",
        "736": "SD",
        "740": "SR",
        "744": "SJ",
        "748": "SZ",
        "752": "SE",
        "756": "CH",
        "760": "SY",
        "762": "TJ",
        "764": "TH",
        "768": "TG",
        "772": "TK",
        "776": "TO",
        "780": "TT",
        "784": "AE",
        "788": "TN",
        "792": "TR",
        "795": "TM",
        "796": "TC",
        "798": "TV",
        "800": "UG",
        "804": "UA",
        "807": "MK",
        "810": "RU AM AZ BY EE GE KZ KG LV LT MD TJ TM UA UZ",
        "818": "EG",
        "826": "GB",
        "830": "JE GG",
        "831": "GG",
        "832": "JE",
        "833": "IM",
        "834": "TZ",
        "840": "US",
        "850": "VI",
        "854": "BF",
        "858": "UY",
        "860": "UZ",
        "862": "VE",
        "876": "WF",
        "882": "WS",
        "886": "YE",
        "887": "YE",
        "890": "RS ME SI HR MK BA",
        "891": "RS ME",
        "894": "ZM",
        "958": "AA",
        "959": "QM",
        "960": "QN",
        "962": "QP",
        "963": "QQ",
        "964": "QR",
        "965": "QS",
        "966": "QT",
        "967": "EU",
        "968": "QV",
        "969": "QW",
        "970": "QX",
        "971": "QY",
        "972": "QZ",
        "973": "XA",
        "974": "XB",
        "975": "XC",
        "976": "XD",
        "977": "XE",
        "978": "XF",
        "979": "XG",
        "980": "XH",
        "981": "XI",
        "982": "XJ",
        "983": "XK",
        "984": "XL",
        "985": "XM",
        "986": "XN",
        "987": "XO",
        "988": "XP",
        "989": "XQ",
        "990": "XR",
        "991": "XS",
        "992": "XT",
        "993": "XU",
        "994": "XV",
        "995": "XW",
        "996": "XX",
        "997": "XY",
        "998": "XZ",
        "999": "ZZ",
        "004": "AF",
        "008": "AL",
        "010": "AQ",
        "012": "DZ",
        "016": "AS",
        "020": "AD",
        "024": "AO",
        "028": "AG",
        "031": "AZ",
        "032": "AR",
        "036": "AU",
        "040": "AT",
        "044": "BS",
        "048": "BH",
        "050": "BD",
        "051": "AM",
        "052": "BB",
        "056": "BE",
        "060": "BM",
        "062": "034 143",
        "064": "BT",
        "068": "BO",
        "070": "BA",
        "072": "BW",
        "074": "BV",
        "076": "BR",
        "084": "BZ",
        "086": "IO",
        "090": "SB",
        "092": "VG",
        "096": "BN",
        "AAA": "AA",
        "ABW": "AW",
        "AFG": "AF",
        "AGO": "AO",
        "AIA": "AI",
        "ALA": "AX",
        "ALB": "AL",
        "AN": "CW SX BQ",
        "AND": "AD",
        "ANT": "CW SX BQ",
        "ARE": "AE",
        "ARG": "AR",
        "ARM": "AM",
        "ASC": "AC",
        "ASM": "AS",
        "ATA": "AQ",
        "ATF": "TF",
        "ATG": "AG",
        "AUS": "AU",
        "AUT": "AT",
        "AZE": "AZ",
        "BDI": "BI",
        "BEL": "BE",
        "BEN": "BJ",
        "BES": "BQ",
        "BFA": "BF",
        "BGD": "BD",
        "BGR": "BG",
        "BHR": "BH",
        "BHS": "BS",
        "BIH": "BA",
        "BLM": "BL",
        "BLR": "BY",
        "BLZ": "BZ",
        "BMU": "BM",
        "BOL": "BO",
        "BRA": "BR",
        "BRB": "BB",
        "BRN": "BN",
        "BTN": "BT",
        "BU": "MM",
        "BUR": "MM",
        "BVT": "BV",
        "BWA": "BW",
        "CAF": "CF",
        "CAN": "CA",
        "CCK": "CC",
        "CHE": "CH",
        "CHL": "CL",
        "CHN": "CN",
        "CIV": "CI",
        "CMR": "CM",
        "COD": "CD",
        "COG": "CG",
        "COK": "CK",
        "COL": "CO",
        "COM": "KM",
        "CPT": "CP",
        "CPV": "CV",
        "CRI": "CR",
        "CS": "RS ME",
        "CT": "KI",
        "CUB": "CU",
        "CUW": "CW",
        "CXR": "CX",
        "CYM": "KY",
        "CYP": "CY",
        "CZE": "CZ",
        "DD": "DE",
        "DDR": "DE",
        "DEU": "DE",
        "DGA": "DG",
        "DJI": "DJ",
        "DMA": "DM",
        "DNK": "DK",
        "DOM": "DO",
        "DY": "BJ",
        "DZA": "DZ",
        "ECU": "EC",
        "EGY": "EG",
        "ERI": "ER",
        "ESH": "EH",
        "ESP": "ES",
        "EST": "EE",
        "ETH": "ET",
        "FIN": "FI",
        "FJI": "FJ",
        "FLK": "FK",
        "FQ": "AQ TF",
        "FRA": "FR",
        "FRO": "FO",
        "FSM": "FM",
        "FX": "FR",
        "FXX": "FR",
        "GAB": "GA",
        "GBR": "GB",
        "GEO": "GE",
        "GGY": "GG",
        "GHA": "GH",
        "GIB": "GI",
        "GIN": "GN",
        "GLP": "GP",
        "GMB": "GM",
        "GNB": "GW",
        "GNQ": "GQ",
        "GRC": "GR",
        "GRD": "GD",
        "GRL": "GL",
        "GTM": "GT",
        "GUF": "GF",
        "GUM": "GU",
        "GUY": "GY",
        "HKG": "HK",
        "HMD": "HM",
        "HND": "HN",
        "HRV": "HR",
        "HTI": "HT",
        "HUN": "HU",
        "HV": "BF",
        "IDN": "ID",
        "IMN": "IM",
        "IND": "IN",
        "IOT": "IO",
        "IRL": "IE",
        "IRN": "IR",
        "IRQ": "IQ",
        "ISL": "IS",
        "ISR": "IL",
        "ITA": "IT",
        "JAM": "JM",
        "JEY": "JE",
        "JOR": "JO",
        "JPN": "JP",
        "JT": "UM",
        "KAZ": "KZ",
        "KEN": "KE",
        "KGZ": "KG",
        "KHM": "KH",
        "KIR": "KI",
        "KNA": "KN",
        "KOR": "KR",
        "KWT": "KW",
        "LAO": "LA",
        "LBN": "LB",
        "LBR": "LR",
        "LBY": "LY",
        "LCA": "LC",
        "LIE": "LI",
        "LKA": "LK",
        "LSO": "LS",
        "LTU": "LT",
        "LUX": "LU",
        "LVA": "LV",
        "MAC": "MO",
        "MAF": "MF",
        "MAR": "MA",
        "MCO": "MC",
        "MDA": "MD",
        "MDG": "MG",
        "MDV": "MV",
        "MEX": "MX",
        "MHL": "MH",
        "MI": "UM",
        "MKD": "MK",
        "MLI": "ML",
        "MLT": "MT",
        "MMR": "MM",
        "MNE": "ME",
        "MNG": "MN",
        "MNP": "MP",
        "MOZ": "MZ",
        "MRT": "MR",
        "MSR": "MS",
        "MTQ": "MQ",
        "MUS": "MU",
        "MWI": "MW",
        "MYS": "MY",
        "MYT": "YT",
        "NAM": "NA",
        "NCL": "NC",
        "NER": "NE",
        "NFK": "NF",
        "NGA": "NG",
        "NH": "VU",
        "NIC": "NI",
        "NIU": "NU",
        "NLD": "NL",
        "NOR": "NO",
        "NPL": "NP",
        "NQ": "AQ",
        "NRU": "NR",
        "NT": "SA IQ",
        "NTZ": "SA IQ",
        "NZL": "NZ",
        "OMN": "OM",
        "PAK": "PK",
        "PAN": "PA",
        "PC": "FM MH MP PW",
        "PCN": "PN",
        "PER": "PE",
        "PHL": "PH",
        "PLW": "PW",
        "PNG": "PG",
        "POL": "PL",
        "PRI": "PR",
        "PRK": "KP",
        "PRT": "PT",
        "PRY": "PY",
        "PSE": "PS",
        "PU": "UM",
        "PYF": "PF",
        "PZ": "PA",
        "QAT": "QA",
        "QMM": "QM",
        "QNN": "QN",
        "QPP": "QP",
        "QQQ": "QQ",
        "QRR": "QR",
        "QSS": "QS",
        "QTT": "QT",
        "QU": "EU",
        "QUU": "EU",
        "QVV": "QV",
        "QWW": "QW",
        "QXX": "QX",
        "QYY": "QY",
        "QZZ": "QZ",
        "REU": "RE",
        "RH": "ZW",
        "ROU": "RO",
        "RUS": "RU",
        "RWA": "RW",
        "SAU": "SA",
        "SCG": "RS ME",
        "SDN": "SD",
        "SEN": "SN",
        "SGP": "SG",
        "SGS": "GS",
        "SHN": "SH",
        "SJM": "SJ",
        "SLB": "SB",
        "SLE": "SL",
        "SLV": "SV",
        "SMR": "SM",
        "SOM": "SO",
        "SPM": "PM",
        "SRB": "RS",
        "SSD": "SS",
        "STP": "ST",
        "SU": "RU AM AZ BY EE GE KZ KG LV LT MD TJ TM UA UZ",
        "SUN": "RU AM AZ BY EE GE KZ KG LV LT MD TJ TM UA UZ",
        "SUR": "SR",
        "SVK": "SK",
        "SVN": "SI",
        "SWE": "SE",
        "SWZ": "SZ",
        "SXM": "SX",
        "SYC": "SC",
        "SYR": "SY",
        "TAA": "TA",
        "TCA": "TC",
        "TCD": "TD",
        "TGO": "TG",
        "THA": "TH",
        "TJK": "TJ",
        "TKL": "TK",
        "TKM": "TM",
        "TLS": "TL",
        "TMP": "TL",
        "TON": "TO",
        "TP": "TL",
        "TTO": "TT",
        "TUN": "TN",
        "TUR": "TR",
        "TUV": "TV",
        "TWN": "TW",
        "TZA": "TZ",
        "UGA": "UG",
        "UK": "GB",
        "UKR": "UA",
        "UMI": "UM",
        "URY": "UY",
        "USA": "US",
        "UZB": "UZ",
        "VAT": "VA",
        "VCT": "VC",
        "VD": "VN",
        "VEN": "VE",
        "VGB": "VG",
        "VIR": "VI",
        "VNM": "VN",
        "VUT": "VU",
        "WK": "UM",
        "WLF": "WF",
        "WSM": "WS",
        "XAA": "XA",
        "XBB": "XB",
        "XCC": "XC",
        "XDD": "XD",
        "XEE": "XE",
        "XFF": "XF",
        "XGG": "XG",
        "XHH": "XH",
        "XII": "XI",
        "XJJ": "XJ",
        "XKK": "XK",
        "XLL": "XL",
        "XMM": "XM",
        "XNN": "XN",
        "XOO": "XO",
        "XPP": "XP",
        "XQQ": "XQ",
        "XRR": "XR",
        "XSS": "XS",
        "XTT": "XT",
        "XUU": "XU",
        "XVV": "XV",
        "XWW": "XW",
        "XXX": "XX",
        "XYY": "XY",
        "XZZ": "XZ",
        "YD": "YE",
        "YEM": "YE",
        "YMD": "YE",
        "YU": "RS ME",
        "YUG": "RS ME",
        "ZAF": "ZA",
        "ZAR": "CD",
        "ZMB": "ZM",
        "ZR": "CD",
        "ZWE": "ZW",
        "ZZZ": "ZZ"
      };
      exports.scriptAlias = {
        "Qaai": "Zinh"
      };
      exports.variantAlias = {
        "heploc": "alalc97",
        "polytoni": "polyton"
      };
    }
  });

  // node_modules/@formatjs/intl-getcanonicallocales/src/likelySubtags.generated.js
  var require_likelySubtags_generated = __commonJS({
    "node_modules/@formatjs/intl-getcanonicallocales/src/likelySubtags.generated.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.likelySubtags = void 0;
      exports.likelySubtags = {
        "aa": "aa-Latn-ET",
        "aai": "aai-Latn-ZZ",
        "aak": "aak-Latn-ZZ",
        "aau": "aau-Latn-ZZ",
        "ab": "ab-Cyrl-GE",
        "abi": "abi-Latn-ZZ",
        "abq": "abq-Cyrl-ZZ",
        "abr": "abr-Latn-GH",
        "abt": "abt-Latn-ZZ",
        "aby": "aby-Latn-ZZ",
        "acd": "acd-Latn-ZZ",
        "ace": "ace-Latn-ID",
        "ach": "ach-Latn-UG",
        "ada": "ada-Latn-GH",
        "ade": "ade-Latn-ZZ",
        "adj": "adj-Latn-ZZ",
        "adp": "adp-Tibt-BT",
        "ady": "ady-Cyrl-RU",
        "adz": "adz-Latn-ZZ",
        "ae": "ae-Avst-IR",
        "aeb": "aeb-Arab-TN",
        "aey": "aey-Latn-ZZ",
        "af": "af-Latn-ZA",
        "agc": "agc-Latn-ZZ",
        "agd": "agd-Latn-ZZ",
        "agg": "agg-Latn-ZZ",
        "agm": "agm-Latn-ZZ",
        "ago": "ago-Latn-ZZ",
        "agq": "agq-Latn-CM",
        "aha": "aha-Latn-ZZ",
        "ahl": "ahl-Latn-ZZ",
        "aho": "aho-Ahom-IN",
        "ajg": "ajg-Latn-ZZ",
        "ak": "ak-Latn-GH",
        "akk": "akk-Xsux-IQ",
        "ala": "ala-Latn-ZZ",
        "ali": "ali-Latn-ZZ",
        "aln": "aln-Latn-XK",
        "alt": "alt-Cyrl-RU",
        "am": "am-Ethi-ET",
        "amm": "amm-Latn-ZZ",
        "amn": "amn-Latn-ZZ",
        "amo": "amo-Latn-NG",
        "amp": "amp-Latn-ZZ",
        "an": "an-Latn-ES",
        "anc": "anc-Latn-ZZ",
        "ank": "ank-Latn-ZZ",
        "ann": "ann-Latn-ZZ",
        "any": "any-Latn-ZZ",
        "aoj": "aoj-Latn-ZZ",
        "aom": "aom-Latn-ZZ",
        "aoz": "aoz-Latn-ID",
        "apc": "apc-Arab-ZZ",
        "apd": "apd-Arab-TG",
        "ape": "ape-Latn-ZZ",
        "apr": "apr-Latn-ZZ",
        "aps": "aps-Latn-ZZ",
        "apz": "apz-Latn-ZZ",
        "ar": "ar-Arab-EG",
        "arc": "arc-Armi-IR",
        "arc-Nbat": "arc-Nbat-JO",
        "arc-Palm": "arc-Palm-SY",
        "arh": "arh-Latn-ZZ",
        "arn": "arn-Latn-CL",
        "aro": "aro-Latn-BO",
        "arq": "arq-Arab-DZ",
        "ars": "ars-Arab-SA",
        "ary": "ary-Arab-MA",
        "arz": "arz-Arab-EG",
        "as": "as-Beng-IN",
        "asa": "asa-Latn-TZ",
        "ase": "ase-Sgnw-US",
        "asg": "asg-Latn-ZZ",
        "aso": "aso-Latn-ZZ",
        "ast": "ast-Latn-ES",
        "ata": "ata-Latn-ZZ",
        "atg": "atg-Latn-ZZ",
        "atj": "atj-Latn-CA",
        "auy": "auy-Latn-ZZ",
        "av": "av-Cyrl-RU",
        "avl": "avl-Arab-ZZ",
        "avn": "avn-Latn-ZZ",
        "avt": "avt-Latn-ZZ",
        "avu": "avu-Latn-ZZ",
        "awa": "awa-Deva-IN",
        "awb": "awb-Latn-ZZ",
        "awo": "awo-Latn-ZZ",
        "awx": "awx-Latn-ZZ",
        "ay": "ay-Latn-BO",
        "ayb": "ayb-Latn-ZZ",
        "az": "az-Latn-AZ",
        "az-Arab": "az-Arab-IR",
        "az-IQ": "az-Arab-IQ",
        "az-IR": "az-Arab-IR",
        "az-RU": "az-Cyrl-RU",
        "ba": "ba-Cyrl-RU",
        "bal": "bal-Arab-PK",
        "ban": "ban-Latn-ID",
        "bap": "bap-Deva-NP",
        "bar": "bar-Latn-AT",
        "bas": "bas-Latn-CM",
        "bav": "bav-Latn-ZZ",
        "bax": "bax-Bamu-CM",
        "bba": "bba-Latn-ZZ",
        "bbb": "bbb-Latn-ZZ",
        "bbc": "bbc-Latn-ID",
        "bbd": "bbd-Latn-ZZ",
        "bbj": "bbj-Latn-CM",
        "bbp": "bbp-Latn-ZZ",
        "bbr": "bbr-Latn-ZZ",
        "bcf": "bcf-Latn-ZZ",
        "bch": "bch-Latn-ZZ",
        "bci": "bci-Latn-CI",
        "bcm": "bcm-Latn-ZZ",
        "bcn": "bcn-Latn-ZZ",
        "bco": "bco-Latn-ZZ",
        "bcq": "bcq-Ethi-ZZ",
        "bcu": "bcu-Latn-ZZ",
        "bdd": "bdd-Latn-ZZ",
        "be": "be-Cyrl-BY",
        "bef": "bef-Latn-ZZ",
        "beh": "beh-Latn-ZZ",
        "bej": "bej-Arab-SD",
        "bem": "bem-Latn-ZM",
        "bet": "bet-Latn-ZZ",
        "bew": "bew-Latn-ID",
        "bex": "bex-Latn-ZZ",
        "bez": "bez-Latn-TZ",
        "bfd": "bfd-Latn-CM",
        "bfq": "bfq-Taml-IN",
        "bft": "bft-Arab-PK",
        "bfy": "bfy-Deva-IN",
        "bg": "bg-Cyrl-BG",
        "bgc": "bgc-Deva-IN",
        "bgn": "bgn-Arab-PK",
        "bgx": "bgx-Grek-TR",
        "bhb": "bhb-Deva-IN",
        "bhg": "bhg-Latn-ZZ",
        "bhi": "bhi-Deva-IN",
        "bhl": "bhl-Latn-ZZ",
        "bho": "bho-Deva-IN",
        "bhy": "bhy-Latn-ZZ",
        "bi": "bi-Latn-VU",
        "bib": "bib-Latn-ZZ",
        "big": "big-Latn-ZZ",
        "bik": "bik-Latn-PH",
        "bim": "bim-Latn-ZZ",
        "bin": "bin-Latn-NG",
        "bio": "bio-Latn-ZZ",
        "biq": "biq-Latn-ZZ",
        "bjh": "bjh-Latn-ZZ",
        "bji": "bji-Ethi-ZZ",
        "bjj": "bjj-Deva-IN",
        "bjn": "bjn-Latn-ID",
        "bjo": "bjo-Latn-ZZ",
        "bjr": "bjr-Latn-ZZ",
        "bjt": "bjt-Latn-SN",
        "bjz": "bjz-Latn-ZZ",
        "bkc": "bkc-Latn-ZZ",
        "bkm": "bkm-Latn-CM",
        "bkq": "bkq-Latn-ZZ",
        "bku": "bku-Latn-PH",
        "bkv": "bkv-Latn-ZZ",
        "blg": "blg-Latn-MY",
        "blt": "blt-Tavt-VN",
        "bm": "bm-Latn-ML",
        "bmh": "bmh-Latn-ZZ",
        "bmk": "bmk-Latn-ZZ",
        "bmq": "bmq-Latn-ML",
        "bmu": "bmu-Latn-ZZ",
        "bn": "bn-Beng-BD",
        "bng": "bng-Latn-ZZ",
        "bnm": "bnm-Latn-ZZ",
        "bnp": "bnp-Latn-ZZ",
        "bo": "bo-Tibt-CN",
        "boj": "boj-Latn-ZZ",
        "bom": "bom-Latn-ZZ",
        "bon": "bon-Latn-ZZ",
        "bpy": "bpy-Beng-IN",
        "bqc": "bqc-Latn-ZZ",
        "bqi": "bqi-Arab-IR",
        "bqp": "bqp-Latn-ZZ",
        "bqv": "bqv-Latn-CI",
        "br": "br-Latn-FR",
        "bra": "bra-Deva-IN",
        "brh": "brh-Arab-PK",
        "brx": "brx-Deva-IN",
        "brz": "brz-Latn-ZZ",
        "bs": "bs-Latn-BA",
        "bsj": "bsj-Latn-ZZ",
        "bsq": "bsq-Bass-LR",
        "bss": "bss-Latn-CM",
        "bst": "bst-Ethi-ZZ",
        "bto": "bto-Latn-PH",
        "btt": "btt-Latn-ZZ",
        "btv": "btv-Deva-PK",
        "bua": "bua-Cyrl-RU",
        "buc": "buc-Latn-YT",
        "bud": "bud-Latn-ZZ",
        "bug": "bug-Latn-ID",
        "buk": "buk-Latn-ZZ",
        "bum": "bum-Latn-CM",
        "buo": "buo-Latn-ZZ",
        "bus": "bus-Latn-ZZ",
        "buu": "buu-Latn-ZZ",
        "bvb": "bvb-Latn-GQ",
        "bwd": "bwd-Latn-ZZ",
        "bwr": "bwr-Latn-ZZ",
        "bxh": "bxh-Latn-ZZ",
        "bye": "bye-Latn-ZZ",
        "byn": "byn-Ethi-ER",
        "byr": "byr-Latn-ZZ",
        "bys": "bys-Latn-ZZ",
        "byv": "byv-Latn-CM",
        "byx": "byx-Latn-ZZ",
        "bza": "bza-Latn-ZZ",
        "bze": "bze-Latn-ML",
        "bzf": "bzf-Latn-ZZ",
        "bzh": "bzh-Latn-ZZ",
        "bzw": "bzw-Latn-ZZ",
        "ca": "ca-Latn-ES",
        "cad": "cad-Latn-US",
        "can": "can-Latn-ZZ",
        "cbj": "cbj-Latn-ZZ",
        "cch": "cch-Latn-NG",
        "ccp": "ccp-Cakm-BD",
        "ce": "ce-Cyrl-RU",
        "ceb": "ceb-Latn-PH",
        "cfa": "cfa-Latn-ZZ",
        "cgg": "cgg-Latn-UG",
        "ch": "ch-Latn-GU",
        "chk": "chk-Latn-FM",
        "chm": "chm-Cyrl-RU",
        "cho": "cho-Latn-US",
        "chp": "chp-Latn-CA",
        "chr": "chr-Cher-US",
        "cic": "cic-Latn-US",
        "cja": "cja-Arab-KH",
        "cjm": "cjm-Cham-VN",
        "cjv": "cjv-Latn-ZZ",
        "ckb": "ckb-Arab-IQ",
        "ckl": "ckl-Latn-ZZ",
        "cko": "cko-Latn-ZZ",
        "cky": "cky-Latn-ZZ",
        "cla": "cla-Latn-ZZ",
        "cme": "cme-Latn-ZZ",
        "cmg": "cmg-Soyo-MN",
        "co": "co-Latn-FR",
        "cop": "cop-Copt-EG",
        "cps": "cps-Latn-PH",
        "cr": "cr-Cans-CA",
        "crh": "crh-Cyrl-UA",
        "crj": "crj-Cans-CA",
        "crk": "crk-Cans-CA",
        "crl": "crl-Cans-CA",
        "crm": "crm-Cans-CA",
        "crs": "crs-Latn-SC",
        "cs": "cs-Latn-CZ",
        "csb": "csb-Latn-PL",
        "csw": "csw-Cans-CA",
        "ctd": "ctd-Pauc-MM",
        "cu": "cu-Cyrl-RU",
        "cu-Glag": "cu-Glag-BG",
        "cv": "cv-Cyrl-RU",
        "cy": "cy-Latn-GB",
        "da": "da-Latn-DK",
        "dad": "dad-Latn-ZZ",
        "daf": "daf-Latn-CI",
        "dag": "dag-Latn-ZZ",
        "dah": "dah-Latn-ZZ",
        "dak": "dak-Latn-US",
        "dar": "dar-Cyrl-RU",
        "dav": "dav-Latn-KE",
        "dbd": "dbd-Latn-ZZ",
        "dbq": "dbq-Latn-ZZ",
        "dcc": "dcc-Arab-IN",
        "ddn": "ddn-Latn-ZZ",
        "de": "de-Latn-DE",
        "ded": "ded-Latn-ZZ",
        "den": "den-Latn-CA",
        "dga": "dga-Latn-ZZ",
        "dgh": "dgh-Latn-ZZ",
        "dgi": "dgi-Latn-ZZ",
        "dgl": "dgl-Arab-ZZ",
        "dgr": "dgr-Latn-CA",
        "dgz": "dgz-Latn-ZZ",
        "dia": "dia-Latn-ZZ",
        "dje": "dje-Latn-NE",
        "dmf": "dmf-Medf-NG",
        "dnj": "dnj-Latn-CI",
        "dob": "dob-Latn-ZZ",
        "doi": "doi-Deva-IN",
        "dop": "dop-Latn-ZZ",
        "dow": "dow-Latn-ZZ",
        "drh": "drh-Mong-CN",
        "dri": "dri-Latn-ZZ",
        "drs": "drs-Ethi-ZZ",
        "dsb": "dsb-Latn-DE",
        "dtm": "dtm-Latn-ML",
        "dtp": "dtp-Latn-MY",
        "dts": "dts-Latn-ZZ",
        "dty": "dty-Deva-NP",
        "dua": "dua-Latn-CM",
        "duc": "duc-Latn-ZZ",
        "dud": "dud-Latn-ZZ",
        "dug": "dug-Latn-ZZ",
        "dv": "dv-Thaa-MV",
        "dva": "dva-Latn-ZZ",
        "dww": "dww-Latn-ZZ",
        "dyo": "dyo-Latn-SN",
        "dyu": "dyu-Latn-BF",
        "dz": "dz-Tibt-BT",
        "dzg": "dzg-Latn-ZZ",
        "ebu": "ebu-Latn-KE",
        "ee": "ee-Latn-GH",
        "efi": "efi-Latn-NG",
        "egl": "egl-Latn-IT",
        "egy": "egy-Egyp-EG",
        "eka": "eka-Latn-ZZ",
        "eky": "eky-Kali-MM",
        "el": "el-Grek-GR",
        "ema": "ema-Latn-ZZ",
        "emi": "emi-Latn-ZZ",
        "en": "en-Latn-US",
        "en-Shaw": "en-Shaw-GB",
        "enn": "enn-Latn-ZZ",
        "enq": "enq-Latn-ZZ",
        "eo": "eo-Latn-001",
        "eri": "eri-Latn-ZZ",
        "es": "es-Latn-ES",
        "esg": "esg-Gonm-IN",
        "esu": "esu-Latn-US",
        "et": "et-Latn-EE",
        "etr": "etr-Latn-ZZ",
        "ett": "ett-Ital-IT",
        "etu": "etu-Latn-ZZ",
        "etx": "etx-Latn-ZZ",
        "eu": "eu-Latn-ES",
        "ewo": "ewo-Latn-CM",
        "ext": "ext-Latn-ES",
        "eza": "eza-Latn-ZZ",
        "fa": "fa-Arab-IR",
        "faa": "faa-Latn-ZZ",
        "fab": "fab-Latn-ZZ",
        "fag": "fag-Latn-ZZ",
        "fai": "fai-Latn-ZZ",
        "fan": "fan-Latn-GQ",
        "ff": "ff-Latn-SN",
        "ff-Adlm": "ff-Adlm-GN",
        "ffi": "ffi-Latn-ZZ",
        "ffm": "ffm-Latn-ML",
        "fi": "fi-Latn-FI",
        "fia": "fia-Arab-SD",
        "fil": "fil-Latn-PH",
        "fit": "fit-Latn-SE",
        "fj": "fj-Latn-FJ",
        "flr": "flr-Latn-ZZ",
        "fmp": "fmp-Latn-ZZ",
        "fo": "fo-Latn-FO",
        "fod": "fod-Latn-ZZ",
        "fon": "fon-Latn-BJ",
        "for": "for-Latn-ZZ",
        "fpe": "fpe-Latn-ZZ",
        "fqs": "fqs-Latn-ZZ",
        "fr": "fr-Latn-FR",
        "frc": "frc-Latn-US",
        "frp": "frp-Latn-FR",
        "frr": "frr-Latn-DE",
        "frs": "frs-Latn-DE",
        "fub": "fub-Arab-CM",
        "fud": "fud-Latn-WF",
        "fue": "fue-Latn-ZZ",
        "fuf": "fuf-Latn-GN",
        "fuh": "fuh-Latn-ZZ",
        "fuq": "fuq-Latn-NE",
        "fur": "fur-Latn-IT",
        "fuv": "fuv-Latn-NG",
        "fuy": "fuy-Latn-ZZ",
        "fvr": "fvr-Latn-SD",
        "fy": "fy-Latn-NL",
        "ga": "ga-Latn-IE",
        "gaa": "gaa-Latn-GH",
        "gaf": "gaf-Latn-ZZ",
        "gag": "gag-Latn-MD",
        "gah": "gah-Latn-ZZ",
        "gaj": "gaj-Latn-ZZ",
        "gam": "gam-Latn-ZZ",
        "gan": "gan-Hans-CN",
        "gaw": "gaw-Latn-ZZ",
        "gay": "gay-Latn-ID",
        "gba": "gba-Latn-ZZ",
        "gbf": "gbf-Latn-ZZ",
        "gbm": "gbm-Deva-IN",
        "gby": "gby-Latn-ZZ",
        "gbz": "gbz-Arab-IR",
        "gcr": "gcr-Latn-GF",
        "gd": "gd-Latn-GB",
        "gde": "gde-Latn-ZZ",
        "gdn": "gdn-Latn-ZZ",
        "gdr": "gdr-Latn-ZZ",
        "geb": "geb-Latn-ZZ",
        "gej": "gej-Latn-ZZ",
        "gel": "gel-Latn-ZZ",
        "gez": "gez-Ethi-ET",
        "gfk": "gfk-Latn-ZZ",
        "ggn": "ggn-Deva-NP",
        "ghs": "ghs-Latn-ZZ",
        "gil": "gil-Latn-KI",
        "gim": "gim-Latn-ZZ",
        "gjk": "gjk-Arab-PK",
        "gjn": "gjn-Latn-ZZ",
        "gju": "gju-Arab-PK",
        "gkn": "gkn-Latn-ZZ",
        "gkp": "gkp-Latn-ZZ",
        "gl": "gl-Latn-ES",
        "glk": "glk-Arab-IR",
        "gmm": "gmm-Latn-ZZ",
        "gmv": "gmv-Ethi-ZZ",
        "gn": "gn-Latn-PY",
        "gnd": "gnd-Latn-ZZ",
        "gng": "gng-Latn-ZZ",
        "god": "god-Latn-ZZ",
        "gof": "gof-Ethi-ZZ",
        "goi": "goi-Latn-ZZ",
        "gom": "gom-Deva-IN",
        "gon": "gon-Telu-IN",
        "gor": "gor-Latn-ID",
        "gos": "gos-Latn-NL",
        "got": "got-Goth-UA",
        "grb": "grb-Latn-ZZ",
        "grc": "grc-Cprt-CY",
        "grc-Linb": "grc-Linb-GR",
        "grt": "grt-Beng-IN",
        "grw": "grw-Latn-ZZ",
        "gsw": "gsw-Latn-CH",
        "gu": "gu-Gujr-IN",
        "gub": "gub-Latn-BR",
        "guc": "guc-Latn-CO",
        "gud": "gud-Latn-ZZ",
        "gur": "gur-Latn-GH",
        "guw": "guw-Latn-ZZ",
        "gux": "gux-Latn-ZZ",
        "guz": "guz-Latn-KE",
        "gv": "gv-Latn-IM",
        "gvf": "gvf-Latn-ZZ",
        "gvr": "gvr-Deva-NP",
        "gvs": "gvs-Latn-ZZ",
        "gwc": "gwc-Arab-ZZ",
        "gwi": "gwi-Latn-CA",
        "gwt": "gwt-Arab-ZZ",
        "gyi": "gyi-Latn-ZZ",
        "ha": "ha-Latn-NG",
        "ha-CM": "ha-Arab-CM",
        "ha-SD": "ha-Arab-SD",
        "hag": "hag-Latn-ZZ",
        "hak": "hak-Hans-CN",
        "ham": "ham-Latn-ZZ",
        "haw": "haw-Latn-US",
        "haz": "haz-Arab-AF",
        "hbb": "hbb-Latn-ZZ",
        "hdy": "hdy-Ethi-ZZ",
        "he": "he-Hebr-IL",
        "hhy": "hhy-Latn-ZZ",
        "hi": "hi-Deva-IN",
        "hia": "hia-Latn-ZZ",
        "hif": "hif-Latn-FJ",
        "hig": "hig-Latn-ZZ",
        "hih": "hih-Latn-ZZ",
        "hil": "hil-Latn-PH",
        "hla": "hla-Latn-ZZ",
        "hlu": "hlu-Hluw-TR",
        "hmd": "hmd-Plrd-CN",
        "hmt": "hmt-Latn-ZZ",
        "hnd": "hnd-Arab-PK",
        "hne": "hne-Deva-IN",
        "hnj": "hnj-Hmnp-US",
        "hnn": "hnn-Latn-PH",
        "hno": "hno-Arab-PK",
        "ho": "ho-Latn-PG",
        "hoc": "hoc-Deva-IN",
        "hoj": "hoj-Deva-IN",
        "hot": "hot-Latn-ZZ",
        "hr": "hr-Latn-HR",
        "hsb": "hsb-Latn-DE",
        "hsn": "hsn-Hans-CN",
        "ht": "ht-Latn-HT",
        "hu": "hu-Latn-HU",
        "hui": "hui-Latn-ZZ",
        "hy": "hy-Armn-AM",
        "hz": "hz-Latn-NA",
        "ia": "ia-Latn-001",
        "ian": "ian-Latn-ZZ",
        "iar": "iar-Latn-ZZ",
        "iba": "iba-Latn-MY",
        "ibb": "ibb-Latn-NG",
        "iby": "iby-Latn-ZZ",
        "ica": "ica-Latn-ZZ",
        "ich": "ich-Latn-ZZ",
        "id": "id-Latn-ID",
        "idd": "idd-Latn-ZZ",
        "idi": "idi-Latn-ZZ",
        "idu": "idu-Latn-ZZ",
        "ife": "ife-Latn-TG",
        "ig": "ig-Latn-NG",
        "igb": "igb-Latn-ZZ",
        "ige": "ige-Latn-ZZ",
        "ii": "ii-Yiii-CN",
        "ijj": "ijj-Latn-ZZ",
        "ik": "ik-Latn-US",
        "ikk": "ikk-Latn-ZZ",
        "ikt": "ikt-Latn-CA",
        "ikw": "ikw-Latn-ZZ",
        "ikx": "ikx-Latn-ZZ",
        "ilo": "ilo-Latn-PH",
        "imo": "imo-Latn-ZZ",
        "in": "in-Latn-ID",
        "inh": "inh-Cyrl-RU",
        "io": "io-Latn-001",
        "iou": "iou-Latn-ZZ",
        "iri": "iri-Latn-ZZ",
        "is": "is-Latn-IS",
        "it": "it-Latn-IT",
        "iu": "iu-Cans-CA",
        "iw": "iw-Hebr-IL",
        "iwm": "iwm-Latn-ZZ",
        "iws": "iws-Latn-ZZ",
        "izh": "izh-Latn-RU",
        "izi": "izi-Latn-ZZ",
        "ja": "ja-Jpan-JP",
        "jab": "jab-Latn-ZZ",
        "jam": "jam-Latn-JM",
        "jar": "jar-Latn-ZZ",
        "jbo": "jbo-Latn-001",
        "jbu": "jbu-Latn-ZZ",
        "jen": "jen-Latn-ZZ",
        "jgk": "jgk-Latn-ZZ",
        "jgo": "jgo-Latn-CM",
        "ji": "ji-Hebr-UA",
        "jib": "jib-Latn-ZZ",
        "jmc": "jmc-Latn-TZ",
        "jml": "jml-Deva-NP",
        "jra": "jra-Latn-ZZ",
        "jut": "jut-Latn-DK",
        "jv": "jv-Latn-ID",
        "jw": "jw-Latn-ID",
        "ka": "ka-Geor-GE",
        "kaa": "kaa-Cyrl-UZ",
        "kab": "kab-Latn-DZ",
        "kac": "kac-Latn-MM",
        "kad": "kad-Latn-ZZ",
        "kai": "kai-Latn-ZZ",
        "kaj": "kaj-Latn-NG",
        "kam": "kam-Latn-KE",
        "kao": "kao-Latn-ML",
        "kbd": "kbd-Cyrl-RU",
        "kbm": "kbm-Latn-ZZ",
        "kbp": "kbp-Latn-ZZ",
        "kbq": "kbq-Latn-ZZ",
        "kbx": "kbx-Latn-ZZ",
        "kby": "kby-Arab-NE",
        "kcg": "kcg-Latn-NG",
        "kck": "kck-Latn-ZW",
        "kcl": "kcl-Latn-ZZ",
        "kct": "kct-Latn-ZZ",
        "kde": "kde-Latn-TZ",
        "kdh": "kdh-Latn-TG",
        "kdl": "kdl-Latn-ZZ",
        "kdt": "kdt-Thai-TH",
        "kea": "kea-Latn-CV",
        "ken": "ken-Latn-CM",
        "kez": "kez-Latn-ZZ",
        "kfo": "kfo-Latn-CI",
        "kfr": "kfr-Deva-IN",
        "kfy": "kfy-Deva-IN",
        "kg": "kg-Latn-CD",
        "kge": "kge-Latn-ID",
        "kgf": "kgf-Latn-ZZ",
        "kgp": "kgp-Latn-BR",
        "kha": "kha-Latn-IN",
        "khb": "khb-Talu-CN",
        "khn": "khn-Deva-IN",
        "khq": "khq-Latn-ML",
        "khs": "khs-Latn-ZZ",
        "kht": "kht-Mymr-IN",
        "khw": "khw-Arab-PK",
        "khz": "khz-Latn-ZZ",
        "ki": "ki-Latn-KE",
        "kij": "kij-Latn-ZZ",
        "kiu": "kiu-Latn-TR",
        "kiw": "kiw-Latn-ZZ",
        "kj": "kj-Latn-NA",
        "kjd": "kjd-Latn-ZZ",
        "kjg": "kjg-Laoo-LA",
        "kjs": "kjs-Latn-ZZ",
        "kjy": "kjy-Latn-ZZ",
        "kk": "kk-Cyrl-KZ",
        "kk-AF": "kk-Arab-AF",
        "kk-Arab": "kk-Arab-CN",
        "kk-CN": "kk-Arab-CN",
        "kk-IR": "kk-Arab-IR",
        "kk-MN": "kk-Arab-MN",
        "kkc": "kkc-Latn-ZZ",
        "kkj": "kkj-Latn-CM",
        "kl": "kl-Latn-GL",
        "kln": "kln-Latn-KE",
        "klq": "klq-Latn-ZZ",
        "klt": "klt-Latn-ZZ",
        "klx": "klx-Latn-ZZ",
        "km": "km-Khmr-KH",
        "kmb": "kmb-Latn-AO",
        "kmh": "kmh-Latn-ZZ",
        "kmo": "kmo-Latn-ZZ",
        "kms": "kms-Latn-ZZ",
        "kmu": "kmu-Latn-ZZ",
        "kmw": "kmw-Latn-ZZ",
        "kn": "kn-Knda-IN",
        "knf": "knf-Latn-GW",
        "knp": "knp-Latn-ZZ",
        "ko": "ko-Kore-KR",
        "koi": "koi-Cyrl-RU",
        "kok": "kok-Deva-IN",
        "kol": "kol-Latn-ZZ",
        "kos": "kos-Latn-FM",
        "koz": "koz-Latn-ZZ",
        "kpe": "kpe-Latn-LR",
        "kpf": "kpf-Latn-ZZ",
        "kpo": "kpo-Latn-ZZ",
        "kpr": "kpr-Latn-ZZ",
        "kpx": "kpx-Latn-ZZ",
        "kqb": "kqb-Latn-ZZ",
        "kqf": "kqf-Latn-ZZ",
        "kqs": "kqs-Latn-ZZ",
        "kqy": "kqy-Ethi-ZZ",
        "kr": "kr-Latn-ZZ",
        "krc": "krc-Cyrl-RU",
        "kri": "kri-Latn-SL",
        "krj": "krj-Latn-PH",
        "krl": "krl-Latn-RU",
        "krs": "krs-Latn-ZZ",
        "kru": "kru-Deva-IN",
        "ks": "ks-Arab-IN",
        "ksb": "ksb-Latn-TZ",
        "ksd": "ksd-Latn-ZZ",
        "ksf": "ksf-Latn-CM",
        "ksh": "ksh-Latn-DE",
        "ksj": "ksj-Latn-ZZ",
        "ksr": "ksr-Latn-ZZ",
        "ktb": "ktb-Ethi-ZZ",
        "ktm": "ktm-Latn-ZZ",
        "kto": "kto-Latn-ZZ",
        "ktr": "ktr-Latn-MY",
        "ku": "ku-Latn-TR",
        "ku-Arab": "ku-Arab-IQ",
        "ku-LB": "ku-Arab-LB",
        "ku-Yezi": "ku-Yezi-GE",
        "kub": "kub-Latn-ZZ",
        "kud": "kud-Latn-ZZ",
        "kue": "kue-Latn-ZZ",
        "kuj": "kuj-Latn-ZZ",
        "kum": "kum-Cyrl-RU",
        "kun": "kun-Latn-ZZ",
        "kup": "kup-Latn-ZZ",
        "kus": "kus-Latn-ZZ",
        "kv": "kv-Cyrl-RU",
        "kvg": "kvg-Latn-ZZ",
        "kvr": "kvr-Latn-ID",
        "kvx": "kvx-Arab-PK",
        "kw": "kw-Latn-GB",
        "kwj": "kwj-Latn-ZZ",
        "kwo": "kwo-Latn-ZZ",
        "kwq": "kwq-Latn-ZZ",
        "kxa": "kxa-Latn-ZZ",
        "kxc": "kxc-Ethi-ZZ",
        "kxe": "kxe-Latn-ZZ",
        "kxl": "kxl-Deva-IN",
        "kxm": "kxm-Thai-TH",
        "kxp": "kxp-Arab-PK",
        "kxw": "kxw-Latn-ZZ",
        "kxz": "kxz-Latn-ZZ",
        "ky": "ky-Cyrl-KG",
        "ky-Arab": "ky-Arab-CN",
        "ky-CN": "ky-Arab-CN",
        "ky-Latn": "ky-Latn-TR",
        "ky-TR": "ky-Latn-TR",
        "kye": "kye-Latn-ZZ",
        "kyx": "kyx-Latn-ZZ",
        "kzh": "kzh-Arab-ZZ",
        "kzj": "kzj-Latn-MY",
        "kzr": "kzr-Latn-ZZ",
        "kzt": "kzt-Latn-MY",
        "la": "la-Latn-VA",
        "lab": "lab-Lina-GR",
        "lad": "lad-Hebr-IL",
        "lag": "lag-Latn-TZ",
        "lah": "lah-Arab-PK",
        "laj": "laj-Latn-UG",
        "las": "las-Latn-ZZ",
        "lb": "lb-Latn-LU",
        "lbe": "lbe-Cyrl-RU",
        "lbu": "lbu-Latn-ZZ",
        "lbw": "lbw-Latn-ID",
        "lcm": "lcm-Latn-ZZ",
        "lcp": "lcp-Thai-CN",
        "ldb": "ldb-Latn-ZZ",
        "led": "led-Latn-ZZ",
        "lee": "lee-Latn-ZZ",
        "lem": "lem-Latn-ZZ",
        "lep": "lep-Lepc-IN",
        "leq": "leq-Latn-ZZ",
        "leu": "leu-Latn-ZZ",
        "lez": "lez-Cyrl-RU",
        "lg": "lg-Latn-UG",
        "lgg": "lgg-Latn-ZZ",
        "li": "li-Latn-NL",
        "lia": "lia-Latn-ZZ",
        "lid": "lid-Latn-ZZ",
        "lif": "lif-Deva-NP",
        "lif-Limb": "lif-Limb-IN",
        "lig": "lig-Latn-ZZ",
        "lih": "lih-Latn-ZZ",
        "lij": "lij-Latn-IT",
        "lis": "lis-Lisu-CN",
        "ljp": "ljp-Latn-ID",
        "lki": "lki-Arab-IR",
        "lkt": "lkt-Latn-US",
        "lle": "lle-Latn-ZZ",
        "lln": "lln-Latn-ZZ",
        "lmn": "lmn-Telu-IN",
        "lmo": "lmo-Latn-IT",
        "lmp": "lmp-Latn-ZZ",
        "ln": "ln-Latn-CD",
        "lns": "lns-Latn-ZZ",
        "lnu": "lnu-Latn-ZZ",
        "lo": "lo-Laoo-LA",
        "loj": "loj-Latn-ZZ",
        "lok": "lok-Latn-ZZ",
        "lol": "lol-Latn-CD",
        "lor": "lor-Latn-ZZ",
        "los": "los-Latn-ZZ",
        "loz": "loz-Latn-ZM",
        "lrc": "lrc-Arab-IR",
        "lt": "lt-Latn-LT",
        "ltg": "ltg-Latn-LV",
        "lu": "lu-Latn-CD",
        "lua": "lua-Latn-CD",
        "luo": "luo-Latn-KE",
        "luy": "luy-Latn-KE",
        "luz": "luz-Arab-IR",
        "lv": "lv-Latn-LV",
        "lwl": "lwl-Thai-TH",
        "lzh": "lzh-Hans-CN",
        "lzz": "lzz-Latn-TR",
        "mad": "mad-Latn-ID",
        "maf": "maf-Latn-CM",
        "mag": "mag-Deva-IN",
        "mai": "mai-Deva-IN",
        "mak": "mak-Latn-ID",
        "man": "man-Latn-GM",
        "man-GN": "man-Nkoo-GN",
        "man-Nkoo": "man-Nkoo-GN",
        "mas": "mas-Latn-KE",
        "maw": "maw-Latn-ZZ",
        "maz": "maz-Latn-MX",
        "mbh": "mbh-Latn-ZZ",
        "mbo": "mbo-Latn-ZZ",
        "mbq": "mbq-Latn-ZZ",
        "mbu": "mbu-Latn-ZZ",
        "mbw": "mbw-Latn-ZZ",
        "mci": "mci-Latn-ZZ",
        "mcp": "mcp-Latn-ZZ",
        "mcq": "mcq-Latn-ZZ",
        "mcr": "mcr-Latn-ZZ",
        "mcu": "mcu-Latn-ZZ",
        "mda": "mda-Latn-ZZ",
        "mde": "mde-Arab-ZZ",
        "mdf": "mdf-Cyrl-RU",
        "mdh": "mdh-Latn-PH",
        "mdj": "mdj-Latn-ZZ",
        "mdr": "mdr-Latn-ID",
        "mdx": "mdx-Ethi-ZZ",
        "med": "med-Latn-ZZ",
        "mee": "mee-Latn-ZZ",
        "mek": "mek-Latn-ZZ",
        "men": "men-Latn-SL",
        "mer": "mer-Latn-KE",
        "met": "met-Latn-ZZ",
        "meu": "meu-Latn-ZZ",
        "mfa": "mfa-Arab-TH",
        "mfe": "mfe-Latn-MU",
        "mfn": "mfn-Latn-ZZ",
        "mfo": "mfo-Latn-ZZ",
        "mfq": "mfq-Latn-ZZ",
        "mg": "mg-Latn-MG",
        "mgh": "mgh-Latn-MZ",
        "mgl": "mgl-Latn-ZZ",
        "mgo": "mgo-Latn-CM",
        "mgp": "mgp-Deva-NP",
        "mgy": "mgy-Latn-TZ",
        "mh": "mh-Latn-MH",
        "mhi": "mhi-Latn-ZZ",
        "mhl": "mhl-Latn-ZZ",
        "mi": "mi-Latn-NZ",
        "mif": "mif-Latn-ZZ",
        "min": "min-Latn-ID",
        "miw": "miw-Latn-ZZ",
        "mk": "mk-Cyrl-MK",
        "mki": "mki-Arab-ZZ",
        "mkl": "mkl-Latn-ZZ",
        "mkp": "mkp-Latn-ZZ",
        "mkw": "mkw-Latn-ZZ",
        "ml": "ml-Mlym-IN",
        "mle": "mle-Latn-ZZ",
        "mlp": "mlp-Latn-ZZ",
        "mls": "mls-Latn-SD",
        "mmo": "mmo-Latn-ZZ",
        "mmu": "mmu-Latn-ZZ",
        "mmx": "mmx-Latn-ZZ",
        "mn": "mn-Cyrl-MN",
        "mn-CN": "mn-Mong-CN",
        "mn-Mong": "mn-Mong-CN",
        "mna": "mna-Latn-ZZ",
        "mnf": "mnf-Latn-ZZ",
        "mni": "mni-Beng-IN",
        "mnw": "mnw-Mymr-MM",
        "mo": "mo-Latn-RO",
        "moa": "moa-Latn-ZZ",
        "moe": "moe-Latn-CA",
        "moh": "moh-Latn-CA",
        "mos": "mos-Latn-BF",
        "mox": "mox-Latn-ZZ",
        "mpp": "mpp-Latn-ZZ",
        "mps": "mps-Latn-ZZ",
        "mpt": "mpt-Latn-ZZ",
        "mpx": "mpx-Latn-ZZ",
        "mql": "mql-Latn-ZZ",
        "mr": "mr-Deva-IN",
        "mrd": "mrd-Deva-NP",
        "mrj": "mrj-Cyrl-RU",
        "mro": "mro-Mroo-BD",
        "ms": "ms-Latn-MY",
        "ms-CC": "ms-Arab-CC",
        "mt": "mt-Latn-MT",
        "mtc": "mtc-Latn-ZZ",
        "mtf": "mtf-Latn-ZZ",
        "mti": "mti-Latn-ZZ",
        "mtr": "mtr-Deva-IN",
        "mua": "mua-Latn-CM",
        "mur": "mur-Latn-ZZ",
        "mus": "mus-Latn-US",
        "mva": "mva-Latn-ZZ",
        "mvn": "mvn-Latn-ZZ",
        "mvy": "mvy-Arab-PK",
        "mwk": "mwk-Latn-ML",
        "mwr": "mwr-Deva-IN",
        "mwv": "mwv-Latn-ID",
        "mww": "mww-Hmnp-US",
        "mxc": "mxc-Latn-ZW",
        "mxm": "mxm-Latn-ZZ",
        "my": "my-Mymr-MM",
        "myk": "myk-Latn-ZZ",
        "mym": "mym-Ethi-ZZ",
        "myv": "myv-Cyrl-RU",
        "myw": "myw-Latn-ZZ",
        "myx": "myx-Latn-UG",
        "myz": "myz-Mand-IR",
        "mzk": "mzk-Latn-ZZ",
        "mzm": "mzm-Latn-ZZ",
        "mzn": "mzn-Arab-IR",
        "mzp": "mzp-Latn-ZZ",
        "mzw": "mzw-Latn-ZZ",
        "mzz": "mzz-Latn-ZZ",
        "na": "na-Latn-NR",
        "nac": "nac-Latn-ZZ",
        "naf": "naf-Latn-ZZ",
        "nak": "nak-Latn-ZZ",
        "nan": "nan-Hans-CN",
        "nap": "nap-Latn-IT",
        "naq": "naq-Latn-NA",
        "nas": "nas-Latn-ZZ",
        "nb": "nb-Latn-NO",
        "nca": "nca-Latn-ZZ",
        "nce": "nce-Latn-ZZ",
        "ncf": "ncf-Latn-ZZ",
        "nch": "nch-Latn-MX",
        "nco": "nco-Latn-ZZ",
        "ncu": "ncu-Latn-ZZ",
        "nd": "nd-Latn-ZW",
        "ndc": "ndc-Latn-MZ",
        "nds": "nds-Latn-DE",
        "ne": "ne-Deva-NP",
        "neb": "neb-Latn-ZZ",
        "new": "new-Deva-NP",
        "nex": "nex-Latn-ZZ",
        "nfr": "nfr-Latn-ZZ",
        "ng": "ng-Latn-NA",
        "nga": "nga-Latn-ZZ",
        "ngb": "ngb-Latn-ZZ",
        "ngl": "ngl-Latn-MZ",
        "nhb": "nhb-Latn-ZZ",
        "nhe": "nhe-Latn-MX",
        "nhw": "nhw-Latn-MX",
        "nif": "nif-Latn-ZZ",
        "nii": "nii-Latn-ZZ",
        "nij": "nij-Latn-ID",
        "nin": "nin-Latn-ZZ",
        "niu": "niu-Latn-NU",
        "niy": "niy-Latn-ZZ",
        "niz": "niz-Latn-ZZ",
        "njo": "njo-Latn-IN",
        "nkg": "nkg-Latn-ZZ",
        "nko": "nko-Latn-ZZ",
        "nl": "nl-Latn-NL",
        "nmg": "nmg-Latn-CM",
        "nmz": "nmz-Latn-ZZ",
        "nn": "nn-Latn-NO",
        "nnf": "nnf-Latn-ZZ",
        "nnh": "nnh-Latn-CM",
        "nnk": "nnk-Latn-ZZ",
        "nnm": "nnm-Latn-ZZ",
        "nnp": "nnp-Wcho-IN",
        "no": "no-Latn-NO",
        "nod": "nod-Lana-TH",
        "noe": "noe-Deva-IN",
        "non": "non-Runr-SE",
        "nop": "nop-Latn-ZZ",
        "nou": "nou-Latn-ZZ",
        "nqo": "nqo-Nkoo-GN",
        "nr": "nr-Latn-ZA",
        "nrb": "nrb-Latn-ZZ",
        "nsk": "nsk-Cans-CA",
        "nsn": "nsn-Latn-ZZ",
        "nso": "nso-Latn-ZA",
        "nss": "nss-Latn-ZZ",
        "nst": "nst-Tnsa-IN",
        "ntm": "ntm-Latn-ZZ",
        "ntr": "ntr-Latn-ZZ",
        "nui": "nui-Latn-ZZ",
        "nup": "nup-Latn-ZZ",
        "nus": "nus-Latn-SS",
        "nuv": "nuv-Latn-ZZ",
        "nux": "nux-Latn-ZZ",
        "nv": "nv-Latn-US",
        "nwb": "nwb-Latn-ZZ",
        "nxq": "nxq-Latn-CN",
        "nxr": "nxr-Latn-ZZ",
        "ny": "ny-Latn-MW",
        "nym": "nym-Latn-TZ",
        "nyn": "nyn-Latn-UG",
        "nzi": "nzi-Latn-GH",
        "oc": "oc-Latn-FR",
        "ogc": "ogc-Latn-ZZ",
        "okr": "okr-Latn-ZZ",
        "okv": "okv-Latn-ZZ",
        "om": "om-Latn-ET",
        "ong": "ong-Latn-ZZ",
        "onn": "onn-Latn-ZZ",
        "ons": "ons-Latn-ZZ",
        "opm": "opm-Latn-ZZ",
        "or": "or-Orya-IN",
        "oro": "oro-Latn-ZZ",
        "oru": "oru-Arab-ZZ",
        "os": "os-Cyrl-GE",
        "osa": "osa-Osge-US",
        "ota": "ota-Arab-ZZ",
        "otk": "otk-Orkh-MN",
        "oui": "oui-Ougr-143",
        "ozm": "ozm-Latn-ZZ",
        "pa": "pa-Guru-IN",
        "pa-Arab": "pa-Arab-PK",
        "pa-PK": "pa-Arab-PK",
        "pag": "pag-Latn-PH",
        "pal": "pal-Phli-IR",
        "pal-Phlp": "pal-Phlp-CN",
        "pam": "pam-Latn-PH",
        "pap": "pap-Latn-AW",
        "pau": "pau-Latn-PW",
        "pbi": "pbi-Latn-ZZ",
        "pcd": "pcd-Latn-FR",
        "pcm": "pcm-Latn-NG",
        "pdc": "pdc-Latn-US",
        "pdt": "pdt-Latn-CA",
        "ped": "ped-Latn-ZZ",
        "peo": "peo-Xpeo-IR",
        "pex": "pex-Latn-ZZ",
        "pfl": "pfl-Latn-DE",
        "phl": "phl-Arab-ZZ",
        "phn": "phn-Phnx-LB",
        "pil": "pil-Latn-ZZ",
        "pip": "pip-Latn-ZZ",
        "pka": "pka-Brah-IN",
        "pko": "pko-Latn-KE",
        "pl": "pl-Latn-PL",
        "pla": "pla-Latn-ZZ",
        "pms": "pms-Latn-IT",
        "png": "png-Latn-ZZ",
        "pnn": "pnn-Latn-ZZ",
        "pnt": "pnt-Grek-GR",
        "pon": "pon-Latn-FM",
        "ppa": "ppa-Deva-IN",
        "ppo": "ppo-Latn-ZZ",
        "pra": "pra-Khar-PK",
        "prd": "prd-Arab-IR",
        "prg": "prg-Latn-001",
        "ps": "ps-Arab-AF",
        "pss": "pss-Latn-ZZ",
        "pt": "pt-Latn-BR",
        "ptp": "ptp-Latn-ZZ",
        "puu": "puu-Latn-GA",
        "pwa": "pwa-Latn-ZZ",
        "qu": "qu-Latn-PE",
        "quc": "quc-Latn-GT",
        "qug": "qug-Latn-EC",
        "rai": "rai-Latn-ZZ",
        "raj": "raj-Deva-IN",
        "rao": "rao-Latn-ZZ",
        "rcf": "rcf-Latn-RE",
        "rej": "rej-Latn-ID",
        "rel": "rel-Latn-ZZ",
        "res": "res-Latn-ZZ",
        "rgn": "rgn-Latn-IT",
        "rhg": "rhg-Rohg-MM",
        "ria": "ria-Latn-IN",
        "rif": "rif-Tfng-MA",
        "rif-NL": "rif-Latn-NL",
        "rjs": "rjs-Deva-NP",
        "rkt": "rkt-Beng-BD",
        "rm": "rm-Latn-CH",
        "rmf": "rmf-Latn-FI",
        "rmo": "rmo-Latn-CH",
        "rmt": "rmt-Arab-IR",
        "rmu": "rmu-Latn-SE",
        "rn": "rn-Latn-BI",
        "rna": "rna-Latn-ZZ",
        "rng": "rng-Latn-MZ",
        "ro": "ro-Latn-RO",
        "rob": "rob-Latn-ID",
        "rof": "rof-Latn-TZ",
        "roo": "roo-Latn-ZZ",
        "rro": "rro-Latn-ZZ",
        "rtm": "rtm-Latn-FJ",
        "ru": "ru-Cyrl-RU",
        "rue": "rue-Cyrl-UA",
        "rug": "rug-Latn-SB",
        "rw": "rw-Latn-RW",
        "rwk": "rwk-Latn-TZ",
        "rwo": "rwo-Latn-ZZ",
        "ryu": "ryu-Kana-JP",
        "sa": "sa-Deva-IN",
        "saf": "saf-Latn-GH",
        "sah": "sah-Cyrl-RU",
        "saq": "saq-Latn-KE",
        "sas": "sas-Latn-ID",
        "sat": "sat-Olck-IN",
        "sav": "sav-Latn-SN",
        "saz": "saz-Saur-IN",
        "sba": "sba-Latn-ZZ",
        "sbe": "sbe-Latn-ZZ",
        "sbp": "sbp-Latn-TZ",
        "sc": "sc-Latn-IT",
        "sck": "sck-Deva-IN",
        "scl": "scl-Arab-ZZ",
        "scn": "scn-Latn-IT",
        "sco": "sco-Latn-GB",
        "scs": "scs-Latn-CA",
        "sd": "sd-Arab-PK",
        "sd-Deva": "sd-Deva-IN",
        "sd-Khoj": "sd-Khoj-IN",
        "sd-Sind": "sd-Sind-IN",
        "sdc": "sdc-Latn-IT",
        "sdh": "sdh-Arab-IR",
        "se": "se-Latn-NO",
        "sef": "sef-Latn-CI",
        "seh": "seh-Latn-MZ",
        "sei": "sei-Latn-MX",
        "ses": "ses-Latn-ML",
        "sg": "sg-Latn-CF",
        "sga": "sga-Ogam-IE",
        "sgs": "sgs-Latn-LT",
        "sgw": "sgw-Ethi-ZZ",
        "sgz": "sgz-Latn-ZZ",
        "shi": "shi-Tfng-MA",
        "shk": "shk-Latn-ZZ",
        "shn": "shn-Mymr-MM",
        "shu": "shu-Arab-ZZ",
        "si": "si-Sinh-LK",
        "sid": "sid-Latn-ET",
        "sig": "sig-Latn-ZZ",
        "sil": "sil-Latn-ZZ",
        "sim": "sim-Latn-ZZ",
        "sjr": "sjr-Latn-ZZ",
        "sk": "sk-Latn-SK",
        "skc": "skc-Latn-ZZ",
        "skr": "skr-Arab-PK",
        "sks": "sks-Latn-ZZ",
        "sl": "sl-Latn-SI",
        "sld": "sld-Latn-ZZ",
        "sli": "sli-Latn-PL",
        "sll": "sll-Latn-ZZ",
        "sly": "sly-Latn-ID",
        "sm": "sm-Latn-WS",
        "sma": "sma-Latn-SE",
        "smj": "smj-Latn-SE",
        "smn": "smn-Latn-FI",
        "smp": "smp-Samr-IL",
        "smq": "smq-Latn-ZZ",
        "sms": "sms-Latn-FI",
        "sn": "sn-Latn-ZW",
        "snc": "snc-Latn-ZZ",
        "snk": "snk-Latn-ML",
        "snp": "snp-Latn-ZZ",
        "snx": "snx-Latn-ZZ",
        "sny": "sny-Latn-ZZ",
        "so": "so-Latn-SO",
        "sog": "sog-Sogd-UZ",
        "sok": "sok-Latn-ZZ",
        "soq": "soq-Latn-ZZ",
        "sou": "sou-Thai-TH",
        "soy": "soy-Latn-ZZ",
        "spd": "spd-Latn-ZZ",
        "spl": "spl-Latn-ZZ",
        "sps": "sps-Latn-ZZ",
        "sq": "sq-Latn-AL",
        "sr": "sr-Cyrl-RS",
        "sr-ME": "sr-Latn-ME",
        "sr-RO": "sr-Latn-RO",
        "sr-RU": "sr-Latn-RU",
        "sr-TR": "sr-Latn-TR",
        "srb": "srb-Sora-IN",
        "srn": "srn-Latn-SR",
        "srr": "srr-Latn-SN",
        "srx": "srx-Deva-IN",
        "ss": "ss-Latn-ZA",
        "ssd": "ssd-Latn-ZZ",
        "ssg": "ssg-Latn-ZZ",
        "ssy": "ssy-Latn-ER",
        "st": "st-Latn-ZA",
        "stk": "stk-Latn-ZZ",
        "stq": "stq-Latn-DE",
        "su": "su-Latn-ID",
        "sua": "sua-Latn-ZZ",
        "sue": "sue-Latn-ZZ",
        "suk": "suk-Latn-TZ",
        "sur": "sur-Latn-ZZ",
        "sus": "sus-Latn-GN",
        "sv": "sv-Latn-SE",
        "sw": "sw-Latn-TZ",
        "swb": "swb-Arab-YT",
        "swc": "swc-Latn-CD",
        "swg": "swg-Latn-DE",
        "swp": "swp-Latn-ZZ",
        "swv": "swv-Deva-IN",
        "sxn": "sxn-Latn-ID",
        "sxw": "sxw-Latn-ZZ",
        "syl": "syl-Beng-BD",
        "syr": "syr-Syrc-IQ",
        "szl": "szl-Latn-PL",
        "ta": "ta-Taml-IN",
        "taj": "taj-Deva-NP",
        "tal": "tal-Latn-ZZ",
        "tan": "tan-Latn-ZZ",
        "taq": "taq-Latn-ZZ",
        "tbc": "tbc-Latn-ZZ",
        "tbd": "tbd-Latn-ZZ",
        "tbf": "tbf-Latn-ZZ",
        "tbg": "tbg-Latn-ZZ",
        "tbo": "tbo-Latn-ZZ",
        "tbw": "tbw-Latn-PH",
        "tbz": "tbz-Latn-ZZ",
        "tci": "tci-Latn-ZZ",
        "tcy": "tcy-Knda-IN",
        "tdd": "tdd-Tale-CN",
        "tdg": "tdg-Deva-NP",
        "tdh": "tdh-Deva-NP",
        "tdu": "tdu-Latn-MY",
        "te": "te-Telu-IN",
        "ted": "ted-Latn-ZZ",
        "tem": "tem-Latn-SL",
        "teo": "teo-Latn-UG",
        "tet": "tet-Latn-TL",
        "tfi": "tfi-Latn-ZZ",
        "tg": "tg-Cyrl-TJ",
        "tg-Arab": "tg-Arab-PK",
        "tg-PK": "tg-Arab-PK",
        "tgc": "tgc-Latn-ZZ",
        "tgo": "tgo-Latn-ZZ",
        "tgu": "tgu-Latn-ZZ",
        "th": "th-Thai-TH",
        "thl": "thl-Deva-NP",
        "thq": "thq-Deva-NP",
        "thr": "thr-Deva-NP",
        "ti": "ti-Ethi-ET",
        "tif": "tif-Latn-ZZ",
        "tig": "tig-Ethi-ER",
        "tik": "tik-Latn-ZZ",
        "tim": "tim-Latn-ZZ",
        "tio": "tio-Latn-ZZ",
        "tiv": "tiv-Latn-NG",
        "tk": "tk-Latn-TM",
        "tkl": "tkl-Latn-TK",
        "tkr": "tkr-Latn-AZ",
        "tkt": "tkt-Deva-NP",
        "tl": "tl-Latn-PH",
        "tlf": "tlf-Latn-ZZ",
        "tlx": "tlx-Latn-ZZ",
        "tly": "tly-Latn-AZ",
        "tmh": "tmh-Latn-NE",
        "tmy": "tmy-Latn-ZZ",
        "tn": "tn-Latn-ZA",
        "tnh": "tnh-Latn-ZZ",
        "to": "to-Latn-TO",
        "tof": "tof-Latn-ZZ",
        "tog": "tog-Latn-MW",
        "toq": "toq-Latn-ZZ",
        "tpi": "tpi-Latn-PG",
        "tpm": "tpm-Latn-ZZ",
        "tpz": "tpz-Latn-ZZ",
        "tqo": "tqo-Latn-ZZ",
        "tr": "tr-Latn-TR",
        "tru": "tru-Latn-TR",
        "trv": "trv-Latn-TW",
        "trw": "trw-Arab-PK",
        "ts": "ts-Latn-ZA",
        "tsd": "tsd-Grek-GR",
        "tsf": "tsf-Deva-NP",
        "tsg": "tsg-Latn-PH",
        "tsj": "tsj-Tibt-BT",
        "tsw": "tsw-Latn-ZZ",
        "tt": "tt-Cyrl-RU",
        "ttd": "ttd-Latn-ZZ",
        "tte": "tte-Latn-ZZ",
        "ttj": "ttj-Latn-UG",
        "ttr": "ttr-Latn-ZZ",
        "tts": "tts-Thai-TH",
        "ttt": "ttt-Latn-AZ",
        "tuh": "tuh-Latn-ZZ",
        "tul": "tul-Latn-ZZ",
        "tum": "tum-Latn-MW",
        "tuq": "tuq-Latn-ZZ",
        "tvd": "tvd-Latn-ZZ",
        "tvl": "tvl-Latn-TV",
        "tvu": "tvu-Latn-ZZ",
        "twh": "twh-Latn-ZZ",
        "twq": "twq-Latn-NE",
        "txg": "txg-Tang-CN",
        "txo": "txo-Toto-IN",
        "ty": "ty-Latn-PF",
        "tya": "tya-Latn-ZZ",
        "tyv": "tyv-Cyrl-RU",
        "tzm": "tzm-Latn-MA",
        "ubu": "ubu-Latn-ZZ",
        "udi": "udi-Aghb-RU",
        "udm": "udm-Cyrl-RU",
        "ug": "ug-Arab-CN",
        "ug-Cyrl": "ug-Cyrl-KZ",
        "ug-KZ": "ug-Cyrl-KZ",
        "ug-MN": "ug-Cyrl-MN",
        "uga": "uga-Ugar-SY",
        "uk": "uk-Cyrl-UA",
        "uli": "uli-Latn-FM",
        "umb": "umb-Latn-AO",
        "und": "en-Latn-US",
        "und-002": "en-Latn-NG",
        "und-003": "en-Latn-US",
        "und-005": "pt-Latn-BR",
        "und-009": "en-Latn-AU",
        "und-011": "en-Latn-NG",
        "und-013": "es-Latn-MX",
        "und-014": "sw-Latn-TZ",
        "und-015": "ar-Arab-EG",
        "und-017": "sw-Latn-CD",
        "und-018": "en-Latn-ZA",
        "und-019": "en-Latn-US",
        "und-021": "en-Latn-US",
        "und-029": "es-Latn-CU",
        "und-030": "zh-Hans-CN",
        "und-034": "hi-Deva-IN",
        "und-035": "id-Latn-ID",
        "und-039": "it-Latn-IT",
        "und-053": "en-Latn-AU",
        "und-054": "en-Latn-PG",
        "und-057": "en-Latn-GU",
        "und-061": "sm-Latn-WS",
        "und-142": "zh-Hans-CN",
        "und-143": "uz-Latn-UZ",
        "und-145": "ar-Arab-SA",
        "und-150": "ru-Cyrl-RU",
        "und-151": "ru-Cyrl-RU",
        "und-154": "en-Latn-GB",
        "und-155": "de-Latn-DE",
        "und-202": "en-Latn-NG",
        "und-419": "es-Latn-419",
        "und-AD": "ca-Latn-AD",
        "und-Adlm": "ff-Adlm-GN",
        "und-AE": "ar-Arab-AE",
        "und-AF": "fa-Arab-AF",
        "und-Aghb": "udi-Aghb-RU",
        "und-Ahom": "aho-Ahom-IN",
        "und-AL": "sq-Latn-AL",
        "und-AM": "hy-Armn-AM",
        "und-AO": "pt-Latn-AO",
        "und-AQ": "und-Latn-AQ",
        "und-AR": "es-Latn-AR",
        "und-Arab": "ar-Arab-EG",
        "und-Arab-CC": "ms-Arab-CC",
        "und-Arab-CN": "ug-Arab-CN",
        "und-Arab-GB": "ks-Arab-GB",
        "und-Arab-ID": "ms-Arab-ID",
        "und-Arab-IN": "ur-Arab-IN",
        "und-Arab-KH": "cja-Arab-KH",
        "und-Arab-MM": "rhg-Arab-MM",
        "und-Arab-MN": "kk-Arab-MN",
        "und-Arab-MU": "ur-Arab-MU",
        "und-Arab-NG": "ha-Arab-NG",
        "und-Arab-PK": "ur-Arab-PK",
        "und-Arab-TG": "apd-Arab-TG",
        "und-Arab-TH": "mfa-Arab-TH",
        "und-Arab-TJ": "fa-Arab-TJ",
        "und-Arab-TR": "az-Arab-TR",
        "und-Arab-YT": "swb-Arab-YT",
        "und-Armi": "arc-Armi-IR",
        "und-Armn": "hy-Armn-AM",
        "und-AS": "sm-Latn-AS",
        "und-AT": "de-Latn-AT",
        "und-Avst": "ae-Avst-IR",
        "und-AW": "nl-Latn-AW",
        "und-AX": "sv-Latn-AX",
        "und-AZ": "az-Latn-AZ",
        "und-BA": "bs-Latn-BA",
        "und-Bali": "ban-Bali-ID",
        "und-Bamu": "bax-Bamu-CM",
        "und-Bass": "bsq-Bass-LR",
        "und-Batk": "bbc-Batk-ID",
        "und-BD": "bn-Beng-BD",
        "und-BE": "nl-Latn-BE",
        "und-Beng": "bn-Beng-BD",
        "und-BF": "fr-Latn-BF",
        "und-BG": "bg-Cyrl-BG",
        "und-BH": "ar-Arab-BH",
        "und-Bhks": "sa-Bhks-IN",
        "und-BI": "rn-Latn-BI",
        "und-BJ": "fr-Latn-BJ",
        "und-BL": "fr-Latn-BL",
        "und-BN": "ms-Latn-BN",
        "und-BO": "es-Latn-BO",
        "und-Bopo": "zh-Bopo-TW",
        "und-BQ": "pap-Latn-BQ",
        "und-BR": "pt-Latn-BR",
        "und-Brah": "pka-Brah-IN",
        "und-Brai": "fr-Brai-FR",
        "und-BT": "dz-Tibt-BT",
        "und-Bugi": "bug-Bugi-ID",
        "und-Buhd": "bku-Buhd-PH",
        "und-BV": "und-Latn-BV",
        "und-BY": "be-Cyrl-BY",
        "und-Cakm": "ccp-Cakm-BD",
        "und-Cans": "cr-Cans-CA",
        "und-Cari": "xcr-Cari-TR",
        "und-CD": "sw-Latn-CD",
        "und-CF": "fr-Latn-CF",
        "und-CG": "fr-Latn-CG",
        "und-CH": "de-Latn-CH",
        "und-Cham": "cjm-Cham-VN",
        "und-Cher": "chr-Cher-US",
        "und-Chrs": "xco-Chrs-UZ",
        "und-CI": "fr-Latn-CI",
        "und-CL": "es-Latn-CL",
        "und-CM": "fr-Latn-CM",
        "und-CN": "zh-Hans-CN",
        "und-CO": "es-Latn-CO",
        "und-Copt": "cop-Copt-EG",
        "und-CP": "und-Latn-CP",
        "und-Cpmn": "und-Cpmn-CY",
        "und-Cpmn-CY": "und-Cpmn-CY",
        "und-Cprt": "grc-Cprt-CY",
        "und-CR": "es-Latn-CR",
        "und-CU": "es-Latn-CU",
        "und-CV": "pt-Latn-CV",
        "und-CW": "pap-Latn-CW",
        "und-CY": "el-Grek-CY",
        "und-Cyrl": "ru-Cyrl-RU",
        "und-Cyrl-AL": "mk-Cyrl-AL",
        "und-Cyrl-BA": "sr-Cyrl-BA",
        "und-Cyrl-GE": "os-Cyrl-GE",
        "und-Cyrl-GR": "mk-Cyrl-GR",
        "und-Cyrl-MD": "uk-Cyrl-MD",
        "und-Cyrl-RO": "bg-Cyrl-RO",
        "und-Cyrl-SK": "uk-Cyrl-SK",
        "und-Cyrl-TR": "kbd-Cyrl-TR",
        "und-Cyrl-XK": "sr-Cyrl-XK",
        "und-CZ": "cs-Latn-CZ",
        "und-DE": "de-Latn-DE",
        "und-Deva": "hi-Deva-IN",
        "und-Deva-BT": "ne-Deva-BT",
        "und-Deva-FJ": "hif-Deva-FJ",
        "und-Deva-MU": "bho-Deva-MU",
        "und-Deva-PK": "btv-Deva-PK",
        "und-Diak": "dv-Diak-MV",
        "und-DJ": "aa-Latn-DJ",
        "und-DK": "da-Latn-DK",
        "und-DO": "es-Latn-DO",
        "und-Dogr": "doi-Dogr-IN",
        "und-Dupl": "fr-Dupl-FR",
        "und-DZ": "ar-Arab-DZ",
        "und-EA": "es-Latn-EA",
        "und-EC": "es-Latn-EC",
        "und-EE": "et-Latn-EE",
        "und-EG": "ar-Arab-EG",
        "und-Egyp": "egy-Egyp-EG",
        "und-EH": "ar-Arab-EH",
        "und-Elba": "sq-Elba-AL",
        "und-Elym": "arc-Elym-IR",
        "und-ER": "ti-Ethi-ER",
        "und-ES": "es-Latn-ES",
        "und-ET": "am-Ethi-ET",
        "und-Ethi": "am-Ethi-ET",
        "und-EU": "en-Latn-IE",
        "und-EZ": "de-Latn-EZ",
        "und-FI": "fi-Latn-FI",
        "und-FO": "fo-Latn-FO",
        "und-FR": "fr-Latn-FR",
        "und-GA": "fr-Latn-GA",
        "und-GE": "ka-Geor-GE",
        "und-Geor": "ka-Geor-GE",
        "und-GF": "fr-Latn-GF",
        "und-GH": "ak-Latn-GH",
        "und-GL": "kl-Latn-GL",
        "und-Glag": "cu-Glag-BG",
        "und-GN": "fr-Latn-GN",
        "und-Gong": "wsg-Gong-IN",
        "und-Gonm": "esg-Gonm-IN",
        "und-Goth": "got-Goth-UA",
        "und-GP": "fr-Latn-GP",
        "und-GQ": "es-Latn-GQ",
        "und-GR": "el-Grek-GR",
        "und-Gran": "sa-Gran-IN",
        "und-Grek": "el-Grek-GR",
        "und-Grek-TR": "bgx-Grek-TR",
        "und-GS": "und-Latn-GS",
        "und-GT": "es-Latn-GT",
        "und-Gujr": "gu-Gujr-IN",
        "und-Guru": "pa-Guru-IN",
        "und-GW": "pt-Latn-GW",
        "und-Hanb": "zh-Hanb-TW",
        "und-Hang": "ko-Hang-KR",
        "und-Hani": "zh-Hani-CN",
        "und-Hano": "hnn-Hano-PH",
        "und-Hans": "zh-Hans-CN",
        "und-Hant": "zh-Hant-TW",
        "und-Hebr": "he-Hebr-IL",
        "und-Hebr-CA": "yi-Hebr-CA",
        "und-Hebr-GB": "yi-Hebr-GB",
        "und-Hebr-SE": "yi-Hebr-SE",
        "und-Hebr-UA": "yi-Hebr-UA",
        "und-Hebr-US": "yi-Hebr-US",
        "und-Hira": "ja-Hira-JP",
        "und-HK": "zh-Hant-HK",
        "und-Hluw": "hlu-Hluw-TR",
        "und-HM": "und-Latn-HM",
        "und-Hmng": "hnj-Hmng-LA",
        "und-Hmnp": "hnj-Hmnp-US",
        "und-HN": "es-Latn-HN",
        "und-HR": "hr-Latn-HR",
        "und-HT": "ht-Latn-HT",
        "und-HU": "hu-Latn-HU",
        "und-Hung": "hu-Hung-HU",
        "und-IC": "es-Latn-IC",
        "und-ID": "id-Latn-ID",
        "und-IL": "he-Hebr-IL",
        "und-IN": "hi-Deva-IN",
        "und-IQ": "ar-Arab-IQ",
        "und-IR": "fa-Arab-IR",
        "und-IS": "is-Latn-IS",
        "und-IT": "it-Latn-IT",
        "und-Ital": "ett-Ital-IT",
        "und-Jamo": "ko-Jamo-KR",
        "und-Java": "jv-Java-ID",
        "und-JO": "ar-Arab-JO",
        "und-JP": "ja-Jpan-JP",
        "und-Jpan": "ja-Jpan-JP",
        "und-Kali": "eky-Kali-MM",
        "und-Kana": "ja-Kana-JP",
        "und-KE": "sw-Latn-KE",
        "und-KG": "ky-Cyrl-KG",
        "und-KH": "km-Khmr-KH",
        "und-Khar": "pra-Khar-PK",
        "und-Khmr": "km-Khmr-KH",
        "und-Khoj": "sd-Khoj-IN",
        "und-Kits": "zkt-Kits-CN",
        "und-KM": "ar-Arab-KM",
        "und-Knda": "kn-Knda-IN",
        "und-Kore": "ko-Kore-KR",
        "und-KP": "ko-Kore-KP",
        "und-KR": "ko-Kore-KR",
        "und-Kthi": "bho-Kthi-IN",
        "und-KW": "ar-Arab-KW",
        "und-KZ": "ru-Cyrl-KZ",
        "und-LA": "lo-Laoo-LA",
        "und-Lana": "nod-Lana-TH",
        "und-Laoo": "lo-Laoo-LA",
        "und-Latn-AF": "tk-Latn-AF",
        "und-Latn-AM": "ku-Latn-AM",
        "und-Latn-CN": "za-Latn-CN",
        "und-Latn-CY": "tr-Latn-CY",
        "und-Latn-DZ": "fr-Latn-DZ",
        "und-Latn-ET": "en-Latn-ET",
        "und-Latn-GE": "ku-Latn-GE",
        "und-Latn-IR": "tk-Latn-IR",
        "und-Latn-KM": "fr-Latn-KM",
        "und-Latn-MA": "fr-Latn-MA",
        "und-Latn-MK": "sq-Latn-MK",
        "und-Latn-MM": "kac-Latn-MM",
        "und-Latn-MO": "pt-Latn-MO",
        "und-Latn-MR": "fr-Latn-MR",
        "und-Latn-RU": "krl-Latn-RU",
        "und-Latn-SY": "fr-Latn-SY",
        "und-Latn-TN": "fr-Latn-TN",
        "und-Latn-TW": "trv-Latn-TW",
        "und-Latn-UA": "pl-Latn-UA",
        "und-LB": "ar-Arab-LB",
        "und-Lepc": "lep-Lepc-IN",
        "und-LI": "de-Latn-LI",
        "und-Limb": "lif-Limb-IN",
        "und-Lina": "lab-Lina-GR",
        "und-Linb": "grc-Linb-GR",
        "und-Lisu": "lis-Lisu-CN",
        "und-LK": "si-Sinh-LK",
        "und-LS": "st-Latn-LS",
        "und-LT": "lt-Latn-LT",
        "und-LU": "fr-Latn-LU",
        "und-LV": "lv-Latn-LV",
        "und-LY": "ar-Arab-LY",
        "und-Lyci": "xlc-Lyci-TR",
        "und-Lydi": "xld-Lydi-TR",
        "und-MA": "ar-Arab-MA",
        "und-Mahj": "hi-Mahj-IN",
        "und-Maka": "mak-Maka-ID",
        "und-Mand": "myz-Mand-IR",
        "und-Mani": "xmn-Mani-CN",
        "und-Marc": "bo-Marc-CN",
        "und-MC": "fr-Latn-MC",
        "und-MD": "ro-Latn-MD",
        "und-ME": "sr-Latn-ME",
        "und-Medf": "dmf-Medf-NG",
        "und-Mend": "men-Mend-SL",
        "und-Merc": "xmr-Merc-SD",
        "und-Mero": "xmr-Mero-SD",
        "und-MF": "fr-Latn-MF",
        "und-MG": "mg-Latn-MG",
        "und-MK": "mk-Cyrl-MK",
        "und-ML": "bm-Latn-ML",
        "und-Mlym": "ml-Mlym-IN",
        "und-MM": "my-Mymr-MM",
        "und-MN": "mn-Cyrl-MN",
        "und-MO": "zh-Hant-MO",
        "und-Modi": "mr-Modi-IN",
        "und-Mong": "mn-Mong-CN",
        "und-MQ": "fr-Latn-MQ",
        "und-MR": "ar-Arab-MR",
        "und-Mroo": "mro-Mroo-BD",
        "und-MT": "mt-Latn-MT",
        "und-Mtei": "mni-Mtei-IN",
        "und-MU": "mfe-Latn-MU",
        "und-Mult": "skr-Mult-PK",
        "und-MV": "dv-Thaa-MV",
        "und-MX": "es-Latn-MX",
        "und-MY": "ms-Latn-MY",
        "und-Mymr": "my-Mymr-MM",
        "und-Mymr-IN": "kht-Mymr-IN",
        "und-Mymr-TH": "mnw-Mymr-TH",
        "und-MZ": "pt-Latn-MZ",
        "und-NA": "af-Latn-NA",
        "und-Nand": "sa-Nand-IN",
        "und-Narb": "xna-Narb-SA",
        "und-Nbat": "arc-Nbat-JO",
        "und-NC": "fr-Latn-NC",
        "und-NE": "ha-Latn-NE",
        "und-Newa": "new-Newa-NP",
        "und-NI": "es-Latn-NI",
        "und-Nkoo": "man-Nkoo-GN",
        "und-NL": "nl-Latn-NL",
        "und-NO": "nb-Latn-NO",
        "und-NP": "ne-Deva-NP",
        "und-Nshu": "zhx-Nshu-CN",
        "und-Ogam": "sga-Ogam-IE",
        "und-Olck": "sat-Olck-IN",
        "und-OM": "ar-Arab-OM",
        "und-Orkh": "otk-Orkh-MN",
        "und-Orya": "or-Orya-IN",
        "und-Osge": "osa-Osge-US",
        "und-Osma": "so-Osma-SO",
        "und-Ougr": "oui-Ougr-143",
        "und-PA": "es-Latn-PA",
        "und-Palm": "arc-Palm-SY",
        "und-Pauc": "ctd-Pauc-MM",
        "und-PE": "es-Latn-PE",
        "und-Perm": "kv-Perm-RU",
        "und-PF": "fr-Latn-PF",
        "und-PG": "tpi-Latn-PG",
        "und-PH": "fil-Latn-PH",
        "und-Phag": "lzh-Phag-CN",
        "und-Phli": "pal-Phli-IR",
        "und-Phlp": "pal-Phlp-CN",
        "und-Phnx": "phn-Phnx-LB",
        "und-PK": "ur-Arab-PK",
        "und-PL": "pl-Latn-PL",
        "und-Plrd": "hmd-Plrd-CN",
        "und-PM": "fr-Latn-PM",
        "und-PR": "es-Latn-PR",
        "und-Prti": "xpr-Prti-IR",
        "und-PS": "ar-Arab-PS",
        "und-PT": "pt-Latn-PT",
        "und-PW": "pau-Latn-PW",
        "und-PY": "gn-Latn-PY",
        "und-QA": "ar-Arab-QA",
        "und-QO": "en-Latn-DG",
        "und-RE": "fr-Latn-RE",
        "und-Rjng": "rej-Rjng-ID",
        "und-RO": "ro-Latn-RO",
        "und-Rohg": "rhg-Rohg-MM",
        "und-RS": "sr-Cyrl-RS",
        "und-RU": "ru-Cyrl-RU",
        "und-Runr": "non-Runr-SE",
        "und-RW": "rw-Latn-RW",
        "und-SA": "ar-Arab-SA",
        "und-Samr": "smp-Samr-IL",
        "und-Sarb": "xsa-Sarb-YE",
        "und-Saur": "saz-Saur-IN",
        "und-SC": "fr-Latn-SC",
        "und-SD": "ar-Arab-SD",
        "und-SE": "sv-Latn-SE",
        "und-Sgnw": "ase-Sgnw-US",
        "und-Shaw": "en-Shaw-GB",
        "und-Shrd": "sa-Shrd-IN",
        "und-SI": "sl-Latn-SI",
        "und-Sidd": "sa-Sidd-IN",
        "und-Sind": "sd-Sind-IN",
        "und-Sinh": "si-Sinh-LK",
        "und-SJ": "nb-Latn-SJ",
        "und-SK": "sk-Latn-SK",
        "und-SM": "it-Latn-SM",
        "und-SN": "fr-Latn-SN",
        "und-SO": "so-Latn-SO",
        "und-Sogd": "sog-Sogd-UZ",
        "und-Sogo": "sog-Sogo-UZ",
        "und-Sora": "srb-Sora-IN",
        "und-Soyo": "cmg-Soyo-MN",
        "und-SR": "nl-Latn-SR",
        "und-ST": "pt-Latn-ST",
        "und-Sund": "su-Sund-ID",
        "und-SV": "es-Latn-SV",
        "und-SY": "ar-Arab-SY",
        "und-Sylo": "syl-Sylo-BD",
        "und-Syrc": "syr-Syrc-IQ",
        "und-Tagb": "tbw-Tagb-PH",
        "und-Takr": "doi-Takr-IN",
        "und-Tale": "tdd-Tale-CN",
        "und-Talu": "khb-Talu-CN",
        "und-Taml": "ta-Taml-IN",
        "und-Tang": "txg-Tang-CN",
        "und-Tavt": "blt-Tavt-VN",
        "und-TD": "fr-Latn-TD",
        "und-Telu": "te-Telu-IN",
        "und-TF": "fr-Latn-TF",
        "und-Tfng": "zgh-Tfng-MA",
        "und-TG": "fr-Latn-TG",
        "und-Tglg": "fil-Tglg-PH",
        "und-TH": "th-Thai-TH",
        "und-Thaa": "dv-Thaa-MV",
        "und-Thai": "th-Thai-TH",
        "und-Thai-CN": "lcp-Thai-CN",
        "und-Thai-KH": "kdt-Thai-KH",
        "und-Thai-LA": "kdt-Thai-LA",
        "und-Tibt": "bo-Tibt-CN",
        "und-Tirh": "mai-Tirh-IN",
        "und-TJ": "tg-Cyrl-TJ",
        "und-TK": "tkl-Latn-TK",
        "und-TL": "pt-Latn-TL",
        "und-TM": "tk-Latn-TM",
        "und-TN": "ar-Arab-TN",
        "und-Tnsa": "nst-Tnsa-IN",
        "und-TO": "to-Latn-TO",
        "und-Toto": "txo-Toto-IN",
        "und-TR": "tr-Latn-TR",
        "und-TV": "tvl-Latn-TV",
        "und-TW": "zh-Hant-TW",
        "und-TZ": "sw-Latn-TZ",
        "und-UA": "uk-Cyrl-UA",
        "und-UG": "sw-Latn-UG",
        "und-Ugar": "uga-Ugar-SY",
        "und-UY": "es-Latn-UY",
        "und-UZ": "uz-Latn-UZ",
        "und-VA": "it-Latn-VA",
        "und-Vaii": "vai-Vaii-LR",
        "und-VE": "es-Latn-VE",
        "und-Vith": "sq-Vith-AL",
        "und-VN": "vi-Latn-VN",
        "und-VU": "bi-Latn-VU",
        "und-Wara": "hoc-Wara-IN",
        "und-Wcho": "nnp-Wcho-IN",
        "und-WF": "fr-Latn-WF",
        "und-WS": "sm-Latn-WS",
        "und-XK": "sq-Latn-XK",
        "und-Xpeo": "peo-Xpeo-IR",
        "und-Xsux": "akk-Xsux-IQ",
        "und-YE": "ar-Arab-YE",
        "und-Yezi": "ku-Yezi-GE",
        "und-Yiii": "ii-Yiii-CN",
        "und-YT": "fr-Latn-YT",
        "und-Zanb": "cmg-Zanb-MN",
        "und-ZW": "sn-Latn-ZW",
        "unr": "unr-Beng-IN",
        "unr-Deva": "unr-Deva-NP",
        "unr-NP": "unr-Deva-NP",
        "unx": "unx-Beng-IN",
        "uok": "uok-Latn-ZZ",
        "ur": "ur-Arab-PK",
        "uri": "uri-Latn-ZZ",
        "urt": "urt-Latn-ZZ",
        "urw": "urw-Latn-ZZ",
        "usa": "usa-Latn-ZZ",
        "uth": "uth-Latn-ZZ",
        "utr": "utr-Latn-ZZ",
        "uvh": "uvh-Latn-ZZ",
        "uvl": "uvl-Latn-ZZ",
        "uz": "uz-Latn-UZ",
        "uz-AF": "uz-Arab-AF",
        "uz-Arab": "uz-Arab-AF",
        "uz-CN": "uz-Cyrl-CN",
        "vag": "vag-Latn-ZZ",
        "vai": "vai-Vaii-LR",
        "van": "van-Latn-ZZ",
        "ve": "ve-Latn-ZA",
        "vec": "vec-Latn-IT",
        "vep": "vep-Latn-RU",
        "vi": "vi-Latn-VN",
        "vic": "vic-Latn-SX",
        "viv": "viv-Latn-ZZ",
        "vls": "vls-Latn-BE",
        "vmf": "vmf-Latn-DE",
        "vmw": "vmw-Latn-MZ",
        "vo": "vo-Latn-001",
        "vot": "vot-Latn-RU",
        "vro": "vro-Latn-EE",
        "vun": "vun-Latn-TZ",
        "vut": "vut-Latn-ZZ",
        "wa": "wa-Latn-BE",
        "wae": "wae-Latn-CH",
        "waj": "waj-Latn-ZZ",
        "wal": "wal-Ethi-ET",
        "wan": "wan-Latn-ZZ",
        "war": "war-Latn-PH",
        "wbp": "wbp-Latn-AU",
        "wbq": "wbq-Telu-IN",
        "wbr": "wbr-Deva-IN",
        "wci": "wci-Latn-ZZ",
        "wer": "wer-Latn-ZZ",
        "wgi": "wgi-Latn-ZZ",
        "whg": "whg-Latn-ZZ",
        "wib": "wib-Latn-ZZ",
        "wiu": "wiu-Latn-ZZ",
        "wiv": "wiv-Latn-ZZ",
        "wja": "wja-Latn-ZZ",
        "wji": "wji-Latn-ZZ",
        "wls": "wls-Latn-WF",
        "wmo": "wmo-Latn-ZZ",
        "wnc": "wnc-Latn-ZZ",
        "wni": "wni-Arab-KM",
        "wnu": "wnu-Latn-ZZ",
        "wo": "wo-Latn-SN",
        "wob": "wob-Latn-ZZ",
        "wos": "wos-Latn-ZZ",
        "wrs": "wrs-Latn-ZZ",
        "wsg": "wsg-Gong-IN",
        "wsk": "wsk-Latn-ZZ",
        "wtm": "wtm-Deva-IN",
        "wuu": "wuu-Hans-CN",
        "wuv": "wuv-Latn-ZZ",
        "wwa": "wwa-Latn-ZZ",
        "xav": "xav-Latn-BR",
        "xbi": "xbi-Latn-ZZ",
        "xco": "xco-Chrs-UZ",
        "xcr": "xcr-Cari-TR",
        "xes": "xes-Latn-ZZ",
        "xh": "xh-Latn-ZA",
        "xla": "xla-Latn-ZZ",
        "xlc": "xlc-Lyci-TR",
        "xld": "xld-Lydi-TR",
        "xmf": "xmf-Geor-GE",
        "xmn": "xmn-Mani-CN",
        "xmr": "xmr-Merc-SD",
        "xna": "xna-Narb-SA",
        "xnr": "xnr-Deva-IN",
        "xog": "xog-Latn-UG",
        "xon": "xon-Latn-ZZ",
        "xpr": "xpr-Prti-IR",
        "xrb": "xrb-Latn-ZZ",
        "xsa": "xsa-Sarb-YE",
        "xsi": "xsi-Latn-ZZ",
        "xsm": "xsm-Latn-ZZ",
        "xsr": "xsr-Deva-NP",
        "xwe": "xwe-Latn-ZZ",
        "yam": "yam-Latn-ZZ",
        "yao": "yao-Latn-MZ",
        "yap": "yap-Latn-FM",
        "yas": "yas-Latn-ZZ",
        "yat": "yat-Latn-ZZ",
        "yav": "yav-Latn-CM",
        "yay": "yay-Latn-ZZ",
        "yaz": "yaz-Latn-ZZ",
        "yba": "yba-Latn-ZZ",
        "ybb": "ybb-Latn-CM",
        "yby": "yby-Latn-ZZ",
        "yer": "yer-Latn-ZZ",
        "ygr": "ygr-Latn-ZZ",
        "ygw": "ygw-Latn-ZZ",
        "yi": "yi-Hebr-001",
        "yko": "yko-Latn-ZZ",
        "yle": "yle-Latn-ZZ",
        "ylg": "ylg-Latn-ZZ",
        "yll": "yll-Latn-ZZ",
        "yml": "yml-Latn-ZZ",
        "yo": "yo-Latn-NG",
        "yon": "yon-Latn-ZZ",
        "yrb": "yrb-Latn-ZZ",
        "yre": "yre-Latn-ZZ",
        "yrl": "yrl-Latn-BR",
        "yss": "yss-Latn-ZZ",
        "yua": "yua-Latn-MX",
        "yue": "yue-Hant-HK",
        "yue-CN": "yue-Hans-CN",
        "yue-Hans": "yue-Hans-CN",
        "yuj": "yuj-Latn-ZZ",
        "yut": "yut-Latn-ZZ",
        "yuw": "yuw-Latn-ZZ",
        "za": "za-Latn-CN",
        "zag": "zag-Latn-SD",
        "zdj": "zdj-Arab-KM",
        "zea": "zea-Latn-NL",
        "zgh": "zgh-Tfng-MA",
        "zh": "zh-Hans-CN",
        "zh-AU": "zh-Hant-AU",
        "zh-BN": "zh-Hant-BN",
        "zh-Bopo": "zh-Bopo-TW",
        "zh-GB": "zh-Hant-GB",
        "zh-GF": "zh-Hant-GF",
        "zh-Hanb": "zh-Hanb-TW",
        "zh-Hant": "zh-Hant-TW",
        "zh-HK": "zh-Hant-HK",
        "zh-ID": "zh-Hant-ID",
        "zh-MO": "zh-Hant-MO",
        "zh-PA": "zh-Hant-PA",
        "zh-PF": "zh-Hant-PF",
        "zh-PH": "zh-Hant-PH",
        "zh-SR": "zh-Hant-SR",
        "zh-TH": "zh-Hant-TH",
        "zh-TW": "zh-Hant-TW",
        "zh-US": "zh-Hant-US",
        "zh-VN": "zh-Hant-VN",
        "zhx": "zhx-Nshu-CN",
        "zia": "zia-Latn-ZZ",
        "zkt": "zkt-Kits-CN",
        "zlm": "zlm-Latn-TG",
        "zmi": "zmi-Latn-MY",
        "zne": "zne-Latn-ZZ",
        "zu": "zu-Latn-ZA",
        "zza": "zza-Latn-TR"
      };
    }
  });

  // node_modules/@formatjs/intl-getcanonicallocales/src/canonicalizer.js
  var require_canonicalizer = __commonJS({
    "node_modules/@formatjs/intl-getcanonicallocales/src/canonicalizer.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.canonicalizeUnicodeLocaleId = exports.canonicalizeUnicodeLanguageId = void 0;
      var tslib_1 = (init_tslib_es6(), __toCommonJS(tslib_es6_exports));
      var aliases_generated_1 = require_aliases_generated();
      var parser_1 = require_parser();
      var likelySubtags_generated_1 = require_likelySubtags_generated();
      var emitter_1 = require_emitter();
      function canonicalizeAttrs(strs) {
        return Object.keys(strs.reduce(function(all, str) {
          all[str.toLowerCase()] = 1;
          return all;
        }, {})).sort();
      }
      function canonicalizeKVs(arr) {
        var all = {};
        var result = [];
        for (var _i = 0, arr_1 = arr; _i < arr_1.length; _i++) {
          var kv = arr_1[_i];
          if (kv[0] in all) {
            continue;
          }
          all[kv[0]] = 1;
          if (!kv[1] || kv[1] === "true") {
            result.push([kv[0].toLowerCase()]);
          } else {
            result.push([kv[0].toLowerCase(), kv[1].toLowerCase()]);
          }
        }
        return result.sort(compareKV);
      }
      function compareKV(t1, t2) {
        return t1[0] < t2[0] ? -1 : t1[0] > t2[0] ? 1 : 0;
      }
      function compareExtension(e1, e2) {
        return e1.type < e2.type ? -1 : e1.type > e2.type ? 1 : 0;
      }
      function mergeVariants(v1, v2) {
        var result = tslib_1.__spreadArray([], v1, true);
        for (var _i = 0, v2_1 = v2; _i < v2_1.length; _i++) {
          var v = v2_1[_i];
          if (v1.indexOf(v) < 0) {
            result.push(v);
          }
        }
        return result;
      }
      function canonicalizeUnicodeLanguageId(unicodeLanguageId) {
        var finalLangAst = unicodeLanguageId;
        if (unicodeLanguageId.variants.length) {
          var replacedLang_1 = "";
          for (var _i = 0, _a = unicodeLanguageId.variants; _i < _a.length; _i++) {
            var variant = _a[_i];
            if (replacedLang_1 = aliases_generated_1.languageAlias[(0, emitter_1.emitUnicodeLanguageId)({
              lang: unicodeLanguageId.lang,
              variants: [variant]
            })]) {
              var replacedLangAst = (0, parser_1.parseUnicodeLanguageId)(replacedLang_1.split(parser_1.SEPARATOR));
              finalLangAst = {
                lang: replacedLangAst.lang,
                script: finalLangAst.script || replacedLangAst.script,
                region: finalLangAst.region || replacedLangAst.region,
                variants: mergeVariants(finalLangAst.variants, replacedLangAst.variants)
              };
              break;
            }
          }
        }
        if (finalLangAst.script && finalLangAst.region) {
          var replacedLang_2 = aliases_generated_1.languageAlias[(0, emitter_1.emitUnicodeLanguageId)({
            lang: finalLangAst.lang,
            script: finalLangAst.script,
            region: finalLangAst.region,
            variants: []
          })];
          if (replacedLang_2) {
            var replacedLangAst = (0, parser_1.parseUnicodeLanguageId)(replacedLang_2.split(parser_1.SEPARATOR));
            finalLangAst = {
              lang: replacedLangAst.lang,
              script: replacedLangAst.script,
              region: replacedLangAst.region,
              variants: finalLangAst.variants
            };
          }
        }
        if (finalLangAst.region) {
          var replacedLang_3 = aliases_generated_1.languageAlias[(0, emitter_1.emitUnicodeLanguageId)({
            lang: finalLangAst.lang,
            region: finalLangAst.region,
            variants: []
          })];
          if (replacedLang_3) {
            var replacedLangAst = (0, parser_1.parseUnicodeLanguageId)(replacedLang_3.split(parser_1.SEPARATOR));
            finalLangAst = {
              lang: replacedLangAst.lang,
              script: finalLangAst.script || replacedLangAst.script,
              region: replacedLangAst.region,
              variants: finalLangAst.variants
            };
          }
        }
        var replacedLang = aliases_generated_1.languageAlias[(0, emitter_1.emitUnicodeLanguageId)({
          lang: finalLangAst.lang,
          variants: []
        })];
        if (replacedLang) {
          var replacedLangAst = (0, parser_1.parseUnicodeLanguageId)(replacedLang.split(parser_1.SEPARATOR));
          finalLangAst = {
            lang: replacedLangAst.lang,
            script: finalLangAst.script || replacedLangAst.script,
            region: finalLangAst.region || replacedLangAst.region,
            variants: finalLangAst.variants
          };
        }
        if (finalLangAst.region) {
          var region = finalLangAst.region.toUpperCase();
          var regionAlias = aliases_generated_1.territoryAlias[region];
          var replacedRegion = void 0;
          if (regionAlias) {
            var regions = regionAlias.split(" ");
            replacedRegion = regions[0];
            var likelySubtag = likelySubtags_generated_1.likelySubtags[(0, emitter_1.emitUnicodeLanguageId)({
              lang: finalLangAst.lang,
              script: finalLangAst.script,
              variants: []
            })];
            if (likelySubtag) {
              var likelyRegion = (0, parser_1.parseUnicodeLanguageId)(likelySubtag.split(parser_1.SEPARATOR)).region;
              if (likelyRegion && regions.indexOf(likelyRegion) > -1) {
                replacedRegion = likelyRegion;
              }
            }
          }
          if (replacedRegion) {
            finalLangAst.region = replacedRegion;
          }
          finalLangAst.region = finalLangAst.region.toUpperCase();
        }
        if (finalLangAst.script) {
          finalLangAst.script = finalLangAst.script[0].toUpperCase() + finalLangAst.script.slice(1).toLowerCase();
          if (aliases_generated_1.scriptAlias[finalLangAst.script]) {
            finalLangAst.script = aliases_generated_1.scriptAlias[finalLangAst.script];
          }
        }
        if (finalLangAst.variants.length) {
          for (var i = 0; i < finalLangAst.variants.length; i++) {
            var variant = finalLangAst.variants[i].toLowerCase();
            if (aliases_generated_1.variantAlias[variant]) {
              var alias = aliases_generated_1.variantAlias[variant];
              if ((0, parser_1.isUnicodeVariantSubtag)(alias)) {
                finalLangAst.variants[i] = alias;
              } else if ((0, parser_1.isUnicodeLanguageSubtag)(alias)) {
                finalLangAst.lang = alias;
              }
            }
          }
          finalLangAst.variants.sort();
        }
        return finalLangAst;
      }
      exports.canonicalizeUnicodeLanguageId = canonicalizeUnicodeLanguageId;
      function canonicalizeUnicodeLocaleId(locale) {
        locale.lang = canonicalizeUnicodeLanguageId(locale.lang);
        if (locale.extensions) {
          for (var _i = 0, _a = locale.extensions; _i < _a.length; _i++) {
            var extension = _a[_i];
            switch (extension.type) {
              case "u":
                extension.keywords = canonicalizeKVs(extension.keywords);
                if (extension.attributes) {
                  extension.attributes = canonicalizeAttrs(extension.attributes);
                }
                break;
              case "t":
                if (extension.lang) {
                  extension.lang = canonicalizeUnicodeLanguageId(extension.lang);
                }
                extension.fields = canonicalizeKVs(extension.fields);
                break;
              default:
                extension.value = extension.value.toLowerCase();
                break;
            }
          }
          locale.extensions.sort(compareExtension);
        }
        return locale;
      }
      exports.canonicalizeUnicodeLocaleId = canonicalizeUnicodeLocaleId;
    }
  });

  // node_modules/@formatjs/intl-getcanonicallocales/src/types.js
  var require_types = __commonJS({
    "node_modules/@formatjs/intl-getcanonicallocales/src/types.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
    }
  });

  // node_modules/@formatjs/intl-getcanonicallocales/index.js
  var require_intl_getcanonicallocales = __commonJS({
    "node_modules/@formatjs/intl-getcanonicallocales/index.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.isUnicodeLanguageSubtag = exports.isUnicodeScriptSubtag = exports.isUnicodeRegionSubtag = exports.isStructurallyValidLanguageTag = exports.parseUnicodeLanguageId = exports.parseUnicodeLocaleId = exports.getCanonicalLocales = void 0;
      var tslib_1 = (init_tslib_es6(), __toCommonJS(tslib_es6_exports));
      var parser_1 = require_parser();
      var emitter_1 = require_emitter();
      var canonicalizer_1 = require_canonicalizer();
      function CanonicalizeLocaleList(locales) {
        if (locales === void 0) {
          return [];
        }
        var seen = [];
        if (typeof locales === "string") {
          locales = [locales];
        }
        for (var _i = 0, locales_1 = locales; _i < locales_1.length; _i++) {
          var locale = locales_1[_i];
          var canonicalizedTag = (0, emitter_1.emitUnicodeLocaleId)((0, canonicalizer_1.canonicalizeUnicodeLocaleId)((0, parser_1.parseUnicodeLocaleId)(locale)));
          if (seen.indexOf(canonicalizedTag) < 0) {
            seen.push(canonicalizedTag);
          }
        }
        return seen;
      }
      function getCanonicalLocales(locales) {
        return CanonicalizeLocaleList(locales);
      }
      exports.getCanonicalLocales = getCanonicalLocales;
      var parser_2 = require_parser();
      Object.defineProperty(exports, "parseUnicodeLocaleId", { enumerable: true, get: function() {
        return parser_2.parseUnicodeLocaleId;
      } });
      Object.defineProperty(exports, "parseUnicodeLanguageId", { enumerable: true, get: function() {
        return parser_2.parseUnicodeLanguageId;
      } });
      Object.defineProperty(exports, "isStructurallyValidLanguageTag", { enumerable: true, get: function() {
        return parser_2.isStructurallyValidLanguageTag;
      } });
      Object.defineProperty(exports, "isUnicodeRegionSubtag", { enumerable: true, get: function() {
        return parser_2.isUnicodeRegionSubtag;
      } });
      Object.defineProperty(exports, "isUnicodeScriptSubtag", { enumerable: true, get: function() {
        return parser_2.isUnicodeScriptSubtag;
      } });
      Object.defineProperty(exports, "isUnicodeLanguageSubtag", { enumerable: true, get: function() {
        return parser_2.isUnicodeLanguageSubtag;
      } });
      tslib_1.__exportStar(require_types(), exports);
      tslib_1.__exportStar(require_emitter(), exports);
      tslib_1.__exportStar(require_likelySubtags_generated(), exports);
    }
  });

  // node_modules/@formatjs/intl-locale/get_internal_slots.js
  var require_get_internal_slots = __commonJS({
    "node_modules/@formatjs/intl-locale/get_internal_slots.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var internalSlotMap = /* @__PURE__ */ new WeakMap();
      function getInternalSlots(x) {
        var internalSlots = internalSlotMap.get(x);
        if (!internalSlots) {
          internalSlots = /* @__PURE__ */ Object.create(null);
          internalSlotMap.set(x, internalSlots);
        }
        return internalSlots;
      }
      exports.default = getInternalSlots;
    }
  });

  // node_modules/@formatjs/intl-locale/index.js
  var require_intl_locale = __commonJS({
    "node_modules/@formatjs/intl-locale/index.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.Locale = void 0;
      var tslib_1 = (init_tslib_es6(), __toCommonJS(tslib_es6_exports));
      var ecma402_abstract_1 = require_ecma402_abstract();
      var intl_getcanonicallocales_1 = require_intl_getcanonicallocales();
      var get_internal_slots_1 = tslib_1.__importDefault(require_get_internal_slots());
      var RELEVANT_EXTENSION_KEYS = ["ca", "co", "hc", "kf", "kn", "nu"];
      var UNICODE_TYPE_REGEX = /^[a-z0-9]{3,8}(-[a-z0-9]{3,8})*$/i;
      function applyOptionsToTag(tag, options) {
        (0, ecma402_abstract_1.invariant)(typeof tag === "string", "language tag must be a string");
        (0, ecma402_abstract_1.invariant)((0, intl_getcanonicallocales_1.isStructurallyValidLanguageTag)(tag), "malformed language tag", RangeError);
        var language = (0, ecma402_abstract_1.GetOption)(options, "language", "string", void 0, void 0);
        if (language !== void 0) {
          (0, ecma402_abstract_1.invariant)((0, intl_getcanonicallocales_1.isUnicodeLanguageSubtag)(language), "Malformed unicode_language_subtag", RangeError);
        }
        var script = (0, ecma402_abstract_1.GetOption)(options, "script", "string", void 0, void 0);
        if (script !== void 0) {
          (0, ecma402_abstract_1.invariant)((0, intl_getcanonicallocales_1.isUnicodeScriptSubtag)(script), "Malformed unicode_script_subtag", RangeError);
        }
        var region = (0, ecma402_abstract_1.GetOption)(options, "region", "string", void 0, void 0);
        if (region !== void 0) {
          (0, ecma402_abstract_1.invariant)((0, intl_getcanonicallocales_1.isUnicodeRegionSubtag)(region), "Malformed unicode_region_subtag", RangeError);
        }
        var languageId = (0, intl_getcanonicallocales_1.parseUnicodeLanguageId)(tag);
        if (language !== void 0) {
          languageId.lang = language;
        }
        if (script !== void 0) {
          languageId.script = script;
        }
        if (region !== void 0) {
          languageId.region = region;
        }
        return Intl.getCanonicalLocales((0, intl_getcanonicallocales_1.emitUnicodeLocaleId)(tslib_1.__assign(tslib_1.__assign({}, (0, intl_getcanonicallocales_1.parseUnicodeLocaleId)(tag)), { lang: languageId })))[0];
      }
      function applyUnicodeExtensionToTag(tag, options, relevantExtensionKeys) {
        var unicodeExtension;
        var keywords = [];
        var ast = (0, intl_getcanonicallocales_1.parseUnicodeLocaleId)(tag);
        for (var _i = 0, _a = ast.extensions; _i < _a.length; _i++) {
          var ext = _a[_i];
          if (ext.type === "u") {
            unicodeExtension = ext;
            if (Array.isArray(ext.keywords))
              keywords = ext.keywords;
          }
        }
        var result = /* @__PURE__ */ Object.create(null);
        for (var _b = 0, relevantExtensionKeys_1 = relevantExtensionKeys; _b < relevantExtensionKeys_1.length; _b++) {
          var key = relevantExtensionKeys_1[_b];
          var value = void 0, entry = void 0;
          for (var _c = 0, keywords_1 = keywords; _c < keywords_1.length; _c++) {
            var keyword = keywords_1[_c];
            if (keyword[0] === key) {
              entry = keyword;
              value = entry[1];
            }
          }
          (0, ecma402_abstract_1.invariant)(key in options, "".concat(key, " must be in options"));
          var optionsValue = options[key];
          if (optionsValue !== void 0) {
            (0, ecma402_abstract_1.invariant)(typeof optionsValue === "string", "Value for ".concat(key, " must be a string"));
            value = optionsValue;
            if (entry) {
              entry[1] = value;
            } else {
              keywords.push([key, value]);
            }
          }
          result[key] = value;
        }
        if (!unicodeExtension) {
          if (keywords.length) {
            ast.extensions.push({
              type: "u",
              keywords,
              attributes: []
            });
          }
        } else {
          unicodeExtension.keywords = keywords;
        }
        result.locale = Intl.getCanonicalLocales((0, intl_getcanonicallocales_1.emitUnicodeLocaleId)(ast))[0];
        return result;
      }
      function mergeUnicodeLanguageId(lang, script, region, variants, replacement) {
        if (variants === void 0) {
          variants = [];
        }
        if (!replacement) {
          return {
            lang: lang || "und",
            script,
            region,
            variants
          };
        }
        return {
          lang: !lang || lang === "und" ? replacement.lang : lang,
          script: script || replacement.script,
          region: region || replacement.region,
          variants: tslib_1.__spreadArray(tslib_1.__spreadArray([], variants, true), replacement.variants, true)
        };
      }
      function addLikelySubtags(tag) {
        var ast = (0, intl_getcanonicallocales_1.parseUnicodeLocaleId)(tag);
        var unicodeLangId = ast.lang;
        var lang = unicodeLangId.lang, script = unicodeLangId.script, region = unicodeLangId.region, variants = unicodeLangId.variants;
        if (script && region) {
          var match_1 = intl_getcanonicallocales_1.likelySubtags[(0, intl_getcanonicallocales_1.emitUnicodeLanguageId)({ lang, script, region, variants: [] })];
          if (match_1) {
            var parts_1 = (0, intl_getcanonicallocales_1.parseUnicodeLanguageId)(match_1);
            ast.lang = mergeUnicodeLanguageId(void 0, void 0, void 0, variants, parts_1);
            return (0, intl_getcanonicallocales_1.emitUnicodeLocaleId)(ast);
          }
        }
        if (script) {
          var match_2 = intl_getcanonicallocales_1.likelySubtags[(0, intl_getcanonicallocales_1.emitUnicodeLanguageId)({ lang, script, variants: [] })];
          if (match_2) {
            var parts_2 = (0, intl_getcanonicallocales_1.parseUnicodeLanguageId)(match_2);
            ast.lang = mergeUnicodeLanguageId(void 0, void 0, region, variants, parts_2);
            return (0, intl_getcanonicallocales_1.emitUnicodeLocaleId)(ast);
          }
        }
        if (region) {
          var match_3 = intl_getcanonicallocales_1.likelySubtags[(0, intl_getcanonicallocales_1.emitUnicodeLanguageId)({ lang, region, variants: [] })];
          if (match_3) {
            var parts_3 = (0, intl_getcanonicallocales_1.parseUnicodeLanguageId)(match_3);
            ast.lang = mergeUnicodeLanguageId(void 0, script, void 0, variants, parts_3);
            return (0, intl_getcanonicallocales_1.emitUnicodeLocaleId)(ast);
          }
        }
        var match = intl_getcanonicallocales_1.likelySubtags[lang] || intl_getcanonicallocales_1.likelySubtags[(0, intl_getcanonicallocales_1.emitUnicodeLanguageId)({ lang: "und", script, variants: [] })];
        if (!match) {
          throw new Error("No match for addLikelySubtags");
        }
        var parts = (0, intl_getcanonicallocales_1.parseUnicodeLanguageId)(match);
        ast.lang = mergeUnicodeLanguageId(void 0, script, region, variants, parts);
        return (0, intl_getcanonicallocales_1.emitUnicodeLocaleId)(ast);
      }
      function removeLikelySubtags(tag) {
        var maxLocale = addLikelySubtags(tag);
        if (!maxLocale) {
          return tag;
        }
        maxLocale = (0, intl_getcanonicallocales_1.emitUnicodeLanguageId)(tslib_1.__assign(tslib_1.__assign({}, (0, intl_getcanonicallocales_1.parseUnicodeLanguageId)(maxLocale)), { variants: [] }));
        var ast = (0, intl_getcanonicallocales_1.parseUnicodeLocaleId)(tag);
        var _a = ast.lang, lang = _a.lang, script = _a.script, region = _a.region, variants = _a.variants;
        var trial = addLikelySubtags((0, intl_getcanonicallocales_1.emitUnicodeLanguageId)({ lang, variants: [] }));
        if (trial === maxLocale) {
          return (0, intl_getcanonicallocales_1.emitUnicodeLocaleId)(tslib_1.__assign(tslib_1.__assign({}, ast), { lang: mergeUnicodeLanguageId(lang, void 0, void 0, variants) }));
        }
        if (region) {
          var trial_1 = addLikelySubtags((0, intl_getcanonicallocales_1.emitUnicodeLanguageId)({ lang, region, variants: [] }));
          if (trial_1 === maxLocale) {
            return (0, intl_getcanonicallocales_1.emitUnicodeLocaleId)(tslib_1.__assign(tslib_1.__assign({}, ast), { lang: mergeUnicodeLanguageId(lang, void 0, region, variants) }));
          }
        }
        if (script) {
          var trial_2 = addLikelySubtags((0, intl_getcanonicallocales_1.emitUnicodeLanguageId)({ lang, script, variants: [] }));
          if (trial_2 === maxLocale) {
            return (0, intl_getcanonicallocales_1.emitUnicodeLocaleId)(tslib_1.__assign(tslib_1.__assign({}, ast), { lang: mergeUnicodeLanguageId(lang, script, void 0, variants) }));
          }
        }
        return tag;
      }
      var Locale = (
        /** @class */
        function() {
          function Locale2(tag, opts) {
            var newTarget = this && this instanceof Locale2 ? this.constructor : void 0;
            if (!newTarget) {
              throw new TypeError("Intl.Locale must be called with 'new'");
            }
            var relevantExtensionKeys = Locale2.relevantExtensionKeys;
            var internalSlotsList = [
              "initializedLocale",
              "locale",
              "calendar",
              "collation",
              "hourCycle",
              "numberingSystem"
            ];
            if (relevantExtensionKeys.indexOf("kf") > -1) {
              internalSlotsList.push("caseFirst");
            }
            if (relevantExtensionKeys.indexOf("kn") > -1) {
              internalSlotsList.push("numeric");
            }
            if (tag === void 0) {
              throw new TypeError("First argument to Intl.Locale constructor can't be empty or missing");
            }
            if (typeof tag !== "string" && typeof tag !== "object") {
              throw new TypeError("tag must be a string or object");
            }
            var internalSlots;
            if (typeof tag === "object" && (internalSlots = (0, get_internal_slots_1.default)(tag)) && internalSlots.initializedLocale) {
              tag = internalSlots.locale;
            } else {
              tag = tag.toString();
            }
            internalSlots = (0, get_internal_slots_1.default)(this);
            var options = (0, ecma402_abstract_1.CoerceOptionsToObject)(opts);
            tag = applyOptionsToTag(tag, options);
            var opt = /* @__PURE__ */ Object.create(null);
            var calendar = (0, ecma402_abstract_1.GetOption)(options, "calendar", "string", void 0, void 0);
            if (calendar !== void 0) {
              if (!UNICODE_TYPE_REGEX.test(calendar)) {
                throw new RangeError("invalid calendar");
              }
            }
            opt.ca = calendar;
            var collation = (0, ecma402_abstract_1.GetOption)(options, "collation", "string", void 0, void 0);
            if (collation !== void 0) {
              if (!UNICODE_TYPE_REGEX.test(collation)) {
                throw new RangeError("invalid collation");
              }
            }
            opt.co = collation;
            var hc = (0, ecma402_abstract_1.GetOption)(options, "hourCycle", "string", ["h11", "h12", "h23", "h24"], void 0);
            opt.hc = hc;
            var kf = (0, ecma402_abstract_1.GetOption)(options, "caseFirst", "string", ["upper", "lower", "false"], void 0);
            opt.kf = kf;
            var _kn = (0, ecma402_abstract_1.GetOption)(options, "numeric", "boolean", void 0, void 0);
            var kn;
            if (_kn !== void 0) {
              kn = String(_kn);
            }
            opt.kn = kn;
            var numberingSystem = (0, ecma402_abstract_1.GetOption)(options, "numberingSystem", "string", void 0, void 0);
            if (numberingSystem !== void 0) {
              if (!UNICODE_TYPE_REGEX.test(numberingSystem)) {
                throw new RangeError("Invalid numberingSystem");
              }
            }
            opt.nu = numberingSystem;
            var r = applyUnicodeExtensionToTag(tag, opt, relevantExtensionKeys);
            internalSlots.locale = r.locale;
            internalSlots.calendar = r.ca;
            internalSlots.collation = r.co;
            internalSlots.hourCycle = r.hc;
            if (relevantExtensionKeys.indexOf("kf") > -1) {
              internalSlots.caseFirst = r.kf;
            }
            if (relevantExtensionKeys.indexOf("kn") > -1) {
              internalSlots.numeric = (0, ecma402_abstract_1.SameValue)(r.kn, "true");
            }
            internalSlots.numberingSystem = r.nu;
          }
          Locale2.prototype.maximize = function() {
            var locale = (0, get_internal_slots_1.default)(this).locale;
            try {
              var maximizedLocale = addLikelySubtags(locale);
              return new Locale2(maximizedLocale);
            } catch (e) {
              return new Locale2(locale);
            }
          };
          Locale2.prototype.minimize = function() {
            var locale = (0, get_internal_slots_1.default)(this).locale;
            try {
              var minimizedLocale = removeLikelySubtags(locale);
              return new Locale2(minimizedLocale);
            } catch (e) {
              return new Locale2(locale);
            }
          };
          Locale2.prototype.toString = function() {
            return (0, get_internal_slots_1.default)(this).locale;
          };
          Object.defineProperty(Locale2.prototype, "baseName", {
            get: function() {
              var locale = (0, get_internal_slots_1.default)(this).locale;
              return (0, intl_getcanonicallocales_1.emitUnicodeLanguageId)((0, intl_getcanonicallocales_1.parseUnicodeLanguageId)(locale));
            },
            enumerable: false,
            configurable: true
          });
          Object.defineProperty(Locale2.prototype, "calendar", {
            get: function() {
              return (0, get_internal_slots_1.default)(this).calendar;
            },
            enumerable: false,
            configurable: true
          });
          Object.defineProperty(Locale2.prototype, "collation", {
            get: function() {
              return (0, get_internal_slots_1.default)(this).collation;
            },
            enumerable: false,
            configurable: true
          });
          Object.defineProperty(Locale2.prototype, "hourCycle", {
            get: function() {
              return (0, get_internal_slots_1.default)(this).hourCycle;
            },
            enumerable: false,
            configurable: true
          });
          Object.defineProperty(Locale2.prototype, "caseFirst", {
            get: function() {
              return (0, get_internal_slots_1.default)(this).caseFirst;
            },
            enumerable: false,
            configurable: true
          });
          Object.defineProperty(Locale2.prototype, "numeric", {
            get: function() {
              return (0, get_internal_slots_1.default)(this).numeric;
            },
            enumerable: false,
            configurable: true
          });
          Object.defineProperty(Locale2.prototype, "numberingSystem", {
            get: function() {
              return (0, get_internal_slots_1.default)(this).numberingSystem;
            },
            enumerable: false,
            configurable: true
          });
          Object.defineProperty(Locale2.prototype, "language", {
            /**
             * https://tc39.es/proposal-intl-locale/#sec-Intl.Locale.prototype.language
             */
            get: function() {
              var locale = (0, get_internal_slots_1.default)(this).locale;
              return (0, intl_getcanonicallocales_1.parseUnicodeLanguageId)(locale).lang;
            },
            enumerable: false,
            configurable: true
          });
          Object.defineProperty(Locale2.prototype, "script", {
            /**
             * https://tc39.es/proposal-intl-locale/#sec-Intl.Locale.prototype.script
             */
            get: function() {
              var locale = (0, get_internal_slots_1.default)(this).locale;
              return (0, intl_getcanonicallocales_1.parseUnicodeLanguageId)(locale).script;
            },
            enumerable: false,
            configurable: true
          });
          Object.defineProperty(Locale2.prototype, "region", {
            /**
             * https://tc39.es/proposal-intl-locale/#sec-Intl.Locale.prototype.region
             */
            get: function() {
              var locale = (0, get_internal_slots_1.default)(this).locale;
              return (0, intl_getcanonicallocales_1.parseUnicodeLanguageId)(locale).region;
            },
            enumerable: false,
            configurable: true
          });
          Locale2.relevantExtensionKeys = RELEVANT_EXTENSION_KEYS;
          return Locale2;
        }()
      );
      exports.Locale = Locale;
      try {
        if (typeof Symbol !== "undefined") {
          Object.defineProperty(Locale.prototype, Symbol.toStringTag, {
            value: "Intl.Locale",
            writable: false,
            enumerable: false,
            configurable: true
          });
        }
        Object.defineProperty(Locale.prototype.constructor, "length", {
          value: 1,
          writable: false,
          enumerable: false,
          configurable: true
        });
      } catch (e) {
      }
      exports.default = Locale;
    }
  });

  // node_modules/@formatjs/intl-locale/should-polyfill.js
  var require_should_polyfill = __commonJS({
    "node_modules/@formatjs/intl-locale/should-polyfill.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      exports.shouldPolyfill = void 0;
      function hasIntlGetCanonicalLocalesBug() {
        try {
          return new Intl.Locale("und-x-private").toString() === "x-private";
        } catch (e) {
          return true;
        }
      }
      function shouldPolyfill() {
        return !("Locale" in Intl) || hasIntlGetCanonicalLocalesBug();
      }
      exports.shouldPolyfill = shouldPolyfill;
    }
  });

  // node_modules/@formatjs/intl-locale/polyfill.js
  var require_polyfill = __commonJS({
    "node_modules/@formatjs/intl-locale/polyfill.js"(exports) {
      "use strict";
      Object.defineProperty(exports, "__esModule", { value: true });
      var _1 = require_intl_locale();
      var should_polyfill_1 = require_should_polyfill();
      if ((0, should_polyfill_1.shouldPolyfill)()) {
        Object.defineProperty(Intl, "Locale", {
          value: _1.Locale,
          writable: true,
          enumerable: false,
          configurable: true
        });
      }
    }
  });

  // shared/js/polyfill.js
  var import_polyfill = __toESM(require_polyfill(), 1);
})();
