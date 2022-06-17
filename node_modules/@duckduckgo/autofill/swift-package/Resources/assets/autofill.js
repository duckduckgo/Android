(function(){function r(e,n,t){function o(i,f){if(!n[i]){if(!e[i]){var c="function"==typeof require&&require;if(!f&&c)return c(i,!0);if(u)return u(i,!0);var a=new Error("Cannot find module '"+i+"'");throw a.code="MODULE_NOT_FOUND",a}var p=n[i]={exports:{}};e[i][0].call(p.exports,function(r){var n=e[i][1][r];return o(n||r)},p,p.exports,r,e,n,t)}return n[i].exports}for(var u="function"==typeof require&&require,i=0;i<t.length;i++)o(t[i]);return o}return r})()({1:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
Object.defineProperty(exports, "Message", {
  enumerable: true,
  get: function () {
    return _message.Message;
  }
});
Object.defineProperty(exports, "RuntimeConfiguration", {
  enumerable: true,
  get: function () {
    return _runtimeConfiguration.RuntimeConfiguration;
  }
});
Object.defineProperty(exports, "SchemaValidationError", {
  enumerable: true,
  get: function () {
    return _message.SchemaValidationError;
  }
});
Object.defineProperty(exports, "Sender", {
  enumerable: true,
  get: function () {
    return _sender.Sender;
  }
});
Object.defineProperty(exports, "UnimplementedError", {
  enumerable: true,
  get: function () {
    return _sender.UnimplementedError;
  }
});
Object.defineProperty(exports, "createRuntimeConfiguration", {
  enumerable: true,
  get: function () {
    return _runtimeConfiguration.createRuntimeConfiguration;
  }
});
Object.defineProperty(exports, "isFeatureEnabledFromProcessedConfig", {
  enumerable: true,
  get: function () {
    return _utils.isFeatureEnabledFromProcessedConfig;
  }
});
Object.defineProperty(exports, "processConfig", {
  enumerable: true,
  get: function () {
    return _appleUtils.processConfig;
  }
});
Object.defineProperty(exports, "tryCreateRuntimeConfiguration", {
  enumerable: true,
  get: function () {
    return _runtimeConfiguration.tryCreateRuntimeConfiguration;
  }
});

var _runtimeConfiguration = require("./src/config/runtime-configuration.js");

var _appleUtils = require("./src/apple-utils");

var _utils = require("./src/utils");

var _message = require("./src/messaging/message");

var _sender = require("./src/messaging/sender");

},{"./src/apple-utils":3,"./src/config/runtime-configuration.js":4,"./src/messaging/message":6,"./src/messaging/sender":7,"./src/utils":8}],2:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.sjcl = void 0;

// @ts-nocheck
const sjcl = (() => {
  /** @fileOverview Javascript cryptography implementation.
  *
  * Crush to remove comments, shorten variable names and
  * generally reduce transmission size.
  *
  * @author Emily Stark
  * @author Mike Hamburg
  * @author Dan Boneh
  */
  "use strict";
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
          return "CORRUPT: " + this.message;
        };

        this.message = message;
      },

      /**
       * Invalid parameter.
       * @constructor
       */
      invalid: function (message) {
        this.toString = function () {
          return "INVALID: " + this.message;
        };

        this.message = message;
      },

      /**
       * Bug or missing feature in SJCL.
       * @constructor
       */
      bug: function (message) {
        this.toString = function () {
          return "BUG: " + this.message;
        };

        this.message = message;
      },

      /**
       * Something isn't ready.
       * @constructor
       */
      notReady: function (message) {
        this.toString = function () {
          return "NOT READY: " + this.message;
        };

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
          sh = Math.floor(-bstart - blength & 31);

      if ((bstart + blength - 1 ^ bstart) & -32) {
        // it crosses a boundary
        x = a[bstart / 32 | 0] << 32 - sh ^ a[bstart / 32 + 1 | 0] >>> sh;
      } else {
        // within a single word
        x = a[bstart / 32 | 0] >>> sh;
      }

      return x & (1 << blength) - 1;
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
        a[l - 1] = sjcl.bitArray.partial(len, a[l - 1] & 0x80000000 >> len - 1, 1);
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

      return (_end ? x | 0 : x << 32 - len) + len * 0x10000000000;
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
        out.push(carry | a[i] >>> shift);
        carry = a[i] << 32 - shift;
      }

      last2 = a.length ? a[a.length - 1] : 0;
      shift2 = sjcl.bitArray.getPartial(last2);
      out.push(sjcl.bitArray.partial(shift + shift2 & 31, shift + shift2 > 32 ? carry : out.pop(), 1));
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
        a[i] = v >>> 24 | v >>> 8 & m | (v & m) << 8 | v << 24;
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
      var out = "",
          bl = sjcl.bitArray.bitLength(arr),
          i,
          tmp;

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
    toBits: function (str) {
      str = unescape(encodeURIComponent(str));
      var out = [],
          i,
          tmp = 0;

      for (i = 0; i < str.length; i++) {
        tmp = tmp << 8 | str.charCodeAt(i);

        if ((i & 3) === 3) {
          out.push(tmp);
          tmp = 0;
        }
      }

      if (i & 3) {
        out.push(sjcl.bitArray.partial(8 * (i & 3), tmp));
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
      var out = "",
          i;

      for (i = 0; i < arr.length; i++) {
        out += ((arr[i] | 0) + 0xF00000000000).toString(16).substr(4);
      }

      return out.substr(0, sjcl.bitArray.bitLength(arr) / 4); //.replace(/(.{8})/g, "$1 ");
    },

    /** Convert from a hex string to a bitArray. */
    toBits: function (str) {
      var i,
          out = [],
          len;
      str = str.replace(/\s|0x/g, "");
      len = str.length;
      str = str + "00000000";

      for (i = 0; i < str.length; i += 8) {
        out.push(parseInt(str.substr(i, 8), 16) ^ 0);
      }

      return sjcl.bitArray.clamp(out, len * 4);
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
      if (typeof data === "string") {
        data = sjcl.codec.utf8String.toBits(data);
      }

      var i,
          b = this._buffer = sjcl.bitArray.concat(this._buffer, data),
          ol = this._length,
          nl = this._length = ol + sjcl.bitArray.bitLength(data);

      if (nl > 9007199254740991) {
        throw new sjcl.exception.invalid("Cannot hash more than 2^53 - 1 bits");
      }

      if (typeof Uint32Array !== 'undefined') {
        var c = new Uint32Array(b);
        var j = 0;

        for (i = 512 + ol - (512 + ol & 511); i <= nl; i += 512) {
          this._block(c.subarray(16 * j, 16 * (j + 1)));

          j += 1;
        }

        b.splice(0, 16 * j);
      } else {
        for (i = 512 + ol - (512 + ol & 511); i <= nl; i += 512) {
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
          h = this._h; // Round out and push the buffer

      b = sjcl.bitArray.concat(b, [sjcl.bitArray.partial(1, 1)]); // Round out the buffer to a multiple of 16 words, less the 2 length words.

      for (i = b.length + 2; i & 15; i++) {
        b.push(0);
      } // append the length


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
        return (x - Math.floor(x)) * 0x100000000 | 0;
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
          a = w[i + 1 & 15];
          b = w[i + 14 & 15];
          tmp = w[i & 15] = (a >>> 7 ^ a >>> 18 ^ a >>> 3 ^ a << 25 ^ a << 14) + (b >>> 17 ^ b >>> 19 ^ b >>> 10 ^ b << 15 ^ b << 13) + w[i & 15] + w[i + 9 & 15] | 0;
        }

        tmp = tmp + h7 + (h4 >>> 6 ^ h4 >>> 11 ^ h4 >>> 25 ^ h4 << 26 ^ h4 << 21 ^ h4 << 7) + (h6 ^ h4 & (h5 ^ h6)) + k[i]; // | 0;
        // shift register

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
      exKey[1][i] = key[i] ^ 0x5C5C5C5C;
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
    var w = this._resultHash.finalize(),
        result = new this._hash(this._baseHash[1]).update(w).finalize();

    this.reset();
    return result;
  };

  return sjcl;
})();

exports.sjcl = sjcl;

},{}],3:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.processConfig = processConfig;

function getTopLevelURL() {
  try {
    // FROM: https://stackoverflow.com/a/7739035/73479
    // FIX: Better capturing of top level URL so that trackers in embedded documents are not considered first party
    if (window.location !== window.parent.location) {
      return new URL(window.location.href !== 'about:blank' ? document.referrer : window.parent.location.href);
    } else {
      return new URL(window.location.href);
    }
  } catch (error) {
    return new URL(location.href);
  }
}
/**
 * @param {URL} topLevelUrl
 * @param {*} featureList
 */


function isUnprotectedDomain(topLevelUrl, featureList) {
  let unprotectedDomain = false;
  const domainParts = topLevelUrl && topLevelUrl.host ? topLevelUrl.host.split('.') : []; // walk up the domain to see if it's unprotected

  while (domainParts.length > 1 && !unprotectedDomain) {
    const partialDomain = domainParts.join('.');
    unprotectedDomain = featureList.filter(domain => domain.domain === partialDomain).length > 0;
    domainParts.shift();
  }

  return unprotectedDomain;
}
/**
 * @param {*} data
 * @param {string[]} userList
 * @param {*} preferences
 * @param {string|URL} [maybeTopLevelUrl]
 */


function processConfig(data, userList, preferences, maybeTopLevelUrl) {
  const topLevelUrl = maybeTopLevelUrl || getTopLevelURL();
  const allowlisted = userList.filter(domain => domain === topLevelUrl.host).length > 0;
  const enabledFeatures = Object.keys(data.features).filter(featureName => {
    const feature = data.features[featureName];
    return feature.state === 'enabled' && !isUnprotectedDomain(topLevelUrl, feature.exceptions);
  });
  const isBroken = isUnprotectedDomain(topLevelUrl, data.unprotectedTemporary);
  const prefs = { ...preferences,
    site: {
      domain: topLevelUrl.hostname,
      isBroken,
      allowlisted,
      enabledFeatures
    },
    cookie: {}
  };
  return prefs;
}

},{}],4:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.RuntimeConfiguration = void 0;
exports.createRuntimeConfiguration = createRuntimeConfiguration;
exports.tryCreateRuntimeConfiguration = tryCreateRuntimeConfiguration;

var _appleUtils = require("../apple-utils.js");

var _validate = _interopRequireDefault(require("./validate.cjs"));

var _utils = require("../utils.js");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

class RuntimeConfiguration {
  constructor() {
    _defineProperty(this, "validate", _validate.default);

    _defineProperty(this, "config", null);
  }

  /**
   * @throws
   * @param {Input} config
   * @returns {RuntimeConfiguration}
   */
  assign(config) {
    if (this.validate(config)) {
      this.config = config;
    } else {
      for (const error of this.validate.errors) {
        // todo: Give an error summary
        console.error(error);
      }

      throw new Error('invalid inputs');
    }

    return this;
  }
  /**
   * @param {any} config
   * @returns {{errors: import("ajv").ErrorObject[], config: RuntimeConfiguration | null}}
   */


  tryAssign(config) {
    if (this.validate(config)) {
      this.config = config;
      return {
        errors: [],
        config: this
      };
    }

    return {
      errors: this.validate.errors.slice(),
      config: null
    };
  }
  /**
   * This will only return settings for a feature if that feature is remotely enabled.
   *
   * @param {string} featureName
   * @param {URL} [url]
   * @returns {null|Record<string, any>}
   */


  getSettings(featureName, url) {
    var _this$config$userPref, _this$config$userPref2;

    const isEnabled = this.isFeatureRemoteEnabled(featureName, url);
    if (!isEnabled) return null;
    const settings = { ...((_this$config$userPref = this.config.userPreferences.features) === null || _this$config$userPref === void 0 ? void 0 : (_this$config$userPref2 = _this$config$userPref[featureName]) === null || _this$config$userPref2 === void 0 ? void 0 : _this$config$userPref2.settings) // todo: Decide on merge strategy?
      // ...this.config.contentScope.features?.[featureName]?.settings

    };
    return settings;
  }
  /**
   * @returns {"macos"|"ios"|"extension"|"windows"|"android"|"unknown"}
   */


  get platform() {
    return this.config.userPreferences.platform.name;
  }
  /**
   * @param {string} featureName
   * @param {URL} [url]
   * @returns {boolean}
   */


  isFeatureRemoteEnabled(featureName, url) {
    const privacyConfig = (0, _appleUtils.processConfig)(this.config.contentScope, this.config.userUnprotectedDomains, this.config.userPreferences, url);
    return (0, _utils.isFeatureEnabledFromProcessedConfig)(privacyConfig, featureName);
  }

}
/**
 * Factory for creating config instance
 * @param {Input} incoming
 * @returns {RuntimeConfiguration}
 */


exports.RuntimeConfiguration = RuntimeConfiguration;

function createRuntimeConfiguration(incoming) {
  return new RuntimeConfiguration().assign(incoming);
}
/**
 * Factory for creating config instance
 * @param {Input} incoming
 * @returns {{errors: import("ajv").ErrorObject[], config: RuntimeConfiguration | null}}
 */


function tryCreateRuntimeConfiguration(incoming) {
  return new RuntimeConfiguration().tryAssign(incoming);
}

},{"../apple-utils.js":3,"../utils.js":8,"./validate.cjs":5}],5:[function(require,module,exports){
"use strict";

module.exports = validate20;
module.exports.default = validate20;
const schema22 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/RuntimeConfiguration",
  "type": "object",
  "additionalProperties": false,
  "title": "Runtime Configuration Schema",
  "description": "Required Properties to enable an instance of RuntimeConfiguration",
  "properties": {
    "contentScope": {
      "$ref": "#/definitions/ContentScope"
    },
    "userUnprotectedDomains": {
      "type": "array",
      "items": {}
    },
    "userPreferences": {
      "$ref": "#/definitions/UserPreferences"
    }
  },
  "required": ["contentScope", "userPreferences", "userUnprotectedDomains"],
  "definitions": {
    "ContentScope": {
      "type": "object",
      "additionalProperties": true,
      "properties": {
        "features": {
          "$ref": "#/definitions/ContentScopeFeatures"
        },
        "unprotectedTemporary": {
          "type": "array",
          "items": {}
        }
      },
      "required": ["features", "unprotectedTemporary"],
      "title": "ContentScope"
    },
    "ContentScopeFeatures": {
      "type": "object",
      "additionalProperties": {
        "$ref": "#/definitions/ContentScopeFeatureItem"
      },
      "title": "ContentScopeFeatures"
    },
    "ContentScopeFeatureItem": {
      "type": "object",
      "properties": {
        "exceptions": {
          "type": "array",
          "items": {}
        },
        "state": {
          "type": "string"
        },
        "settings": {
          "type": "object"
        }
      },
      "required": ["exceptions", "state"],
      "title": "ContentScopeFeatureItem"
    },
    "UserPreferences": {
      "type": "object",
      "properties": {
        "debug": {
          "type": "boolean"
        },
        "platform": {
          "$ref": "#/definitions/Platform"
        },
        "features": {
          "$ref": "#/definitions/UserPreferencesFeatures"
        }
      },
      "required": ["debug", "features", "platform"],
      "title": "UserPreferences"
    },
    "UserPreferencesFeatures": {
      "type": "object",
      "additionalProperties": {
        "$ref": "#/definitions/UserPreferencesFeatureItem"
      },
      "title": "UserPreferencesFeatures"
    },
    "UserPreferencesFeatureItem": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "settings": {
          "$ref": "#/definitions/Settings"
        }
      },
      "required": ["settings"],
      "title": "UserPreferencesFeatureItem"
    },
    "Settings": {
      "type": "object",
      "additionalProperties": true,
      "title": "Settings"
    },
    "Platform": {
      "type": "object",
      "properties": {
        "name": {
          "type": "string",
          "enum": ["ios", "macos", "windows", "extension", "android", "unknown"]
        }
      },
      "required": ["name"],
      "title": "Platform"
    }
  }
};
const schema23 = {
  "type": "object",
  "additionalProperties": true,
  "properties": {
    "features": {
      "$ref": "#/definitions/ContentScopeFeatures"
    },
    "unprotectedTemporary": {
      "type": "array",
      "items": {}
    }
  },
  "required": ["features", "unprotectedTemporary"],
  "title": "ContentScope"
};
const schema24 = {
  "type": "object",
  "additionalProperties": {
    "$ref": "#/definitions/ContentScopeFeatureItem"
  },
  "title": "ContentScopeFeatures"
};
const schema25 = {
  "type": "object",
  "properties": {
    "exceptions": {
      "type": "array",
      "items": {}
    },
    "state": {
      "type": "string"
    },
    "settings": {
      "type": "object"
    }
  },
  "required": ["exceptions", "state"],
  "title": "ContentScopeFeatureItem"
};

function validate22(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      for (const key0 in data) {
        let data0 = data[key0];
        const _errs2 = errors;
        const _errs3 = errors;

        if (errors === _errs3) {
          if (data0 && typeof data0 == "object" && !Array.isArray(data0)) {
            let missing0;

            if (data0.exceptions === undefined && (missing0 = "exceptions") || data0.state === undefined && (missing0 = "state")) {
              validate22.errors = [{
                instancePath: instancePath + "/" + key0.replace(/~/g, "~0").replace(/\//g, "~1"),
                schemaPath: "#/definitions/ContentScopeFeatureItem/required",
                keyword: "required",
                params: {
                  missingProperty: missing0
                },
                message: "must have required property '" + missing0 + "'"
              }];
              return false;
            } else {
              if (data0.exceptions !== undefined) {
                const _errs5 = errors;

                if (errors === _errs5) {
                  if (!Array.isArray(data0.exceptions)) {
                    validate22.errors = [{
                      instancePath: instancePath + "/" + key0.replace(/~/g, "~0").replace(/\//g, "~1") + "/exceptions",
                      schemaPath: "#/definitions/ContentScopeFeatureItem/properties/exceptions/type",
                      keyword: "type",
                      params: {
                        type: "array"
                      },
                      message: "must be array"
                    }];
                    return false;
                  }
                }

                var valid2 = _errs5 === errors;
              } else {
                var valid2 = true;
              }

              if (valid2) {
                if (data0.state !== undefined) {
                  const _errs7 = errors;

                  if (typeof data0.state !== "string") {
                    validate22.errors = [{
                      instancePath: instancePath + "/" + key0.replace(/~/g, "~0").replace(/\//g, "~1") + "/state",
                      schemaPath: "#/definitions/ContentScopeFeatureItem/properties/state/type",
                      keyword: "type",
                      params: {
                        type: "string"
                      },
                      message: "must be string"
                    }];
                    return false;
                  }

                  var valid2 = _errs7 === errors;
                } else {
                  var valid2 = true;
                }

                if (valid2) {
                  if (data0.settings !== undefined) {
                    let data3 = data0.settings;
                    const _errs9 = errors;

                    if (!(data3 && typeof data3 == "object" && !Array.isArray(data3))) {
                      validate22.errors = [{
                        instancePath: instancePath + "/" + key0.replace(/~/g, "~0").replace(/\//g, "~1") + "/settings",
                        schemaPath: "#/definitions/ContentScopeFeatureItem/properties/settings/type",
                        keyword: "type",
                        params: {
                          type: "object"
                        },
                        message: "must be object"
                      }];
                      return false;
                    }

                    var valid2 = _errs9 === errors;
                  } else {
                    var valid2 = true;
                  }
                }
              }
            }
          } else {
            validate22.errors = [{
              instancePath: instancePath + "/" + key0.replace(/~/g, "~0").replace(/\//g, "~1"),
              schemaPath: "#/definitions/ContentScopeFeatureItem/type",
              keyword: "type",
              params: {
                type: "object"
              },
              message: "must be object"
            }];
            return false;
          }
        }

        var valid0 = _errs2 === errors;

        if (!valid0) {
          break;
        }
      }
    } else {
      validate22.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate22.errors = vErrors;
  return errors === 0;
}

function validate21(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.features === undefined && (missing0 = "features") || data.unprotectedTemporary === undefined && (missing0 = "unprotectedTemporary")) {
        validate21.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.features !== undefined) {
          const _errs2 = errors;

          if (!validate22(data.features, {
            instancePath: instancePath + "/features",
            parentData: data,
            parentDataProperty: "features",
            rootData
          })) {
            vErrors = vErrors === null ? validate22.errors : vErrors.concat(validate22.errors);
            errors = vErrors.length;
          }

          var valid0 = _errs2 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.unprotectedTemporary !== undefined) {
            const _errs3 = errors;

            if (errors === _errs3) {
              if (!Array.isArray(data.unprotectedTemporary)) {
                validate21.errors = [{
                  instancePath: instancePath + "/unprotectedTemporary",
                  schemaPath: "#/properties/unprotectedTemporary/type",
                  keyword: "type",
                  params: {
                    type: "array"
                  },
                  message: "must be array"
                }];
                return false;
              }
            }

            var valid0 = _errs3 === errors;
          } else {
            var valid0 = true;
          }
        }
      }
    } else {
      validate21.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate21.errors = vErrors;
  return errors === 0;
}

const schema26 = {
  "type": "object",
  "properties": {
    "debug": {
      "type": "boolean"
    },
    "platform": {
      "$ref": "#/definitions/Platform"
    },
    "features": {
      "$ref": "#/definitions/UserPreferencesFeatures"
    }
  },
  "required": ["debug", "features", "platform"],
  "title": "UserPreferences"
};
const schema27 = {
  "type": "object",
  "properties": {
    "name": {
      "type": "string",
      "enum": ["ios", "macos", "windows", "extension", "android", "unknown"]
    }
  },
  "required": ["name"],
  "title": "Platform"
};
const schema28 = {
  "type": "object",
  "additionalProperties": {
    "$ref": "#/definitions/UserPreferencesFeatureItem"
  },
  "title": "UserPreferencesFeatures"
};
const schema29 = {
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "settings": {
      "$ref": "#/definitions/Settings"
    }
  },
  "required": ["settings"],
  "title": "UserPreferencesFeatureItem"
};
const schema30 = {
  "type": "object",
  "additionalProperties": true,
  "title": "Settings"
};

function validate27(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.settings === undefined && (missing0 = "settings")) {
        validate27.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        const _errs1 = errors;

        for (const key0 in data) {
          if (!(key0 === "settings")) {
            validate27.errors = [{
              instancePath,
              schemaPath: "#/additionalProperties",
              keyword: "additionalProperties",
              params: {
                additionalProperty: key0
              },
              message: "must NOT have additional properties"
            }];
            return false;
            break;
          }
        }

        if (_errs1 === errors) {
          if (data.settings !== undefined) {
            let data0 = data.settings;
            const _errs3 = errors;

            if (errors === _errs3) {
              if (data0 && typeof data0 == "object" && !Array.isArray(data0)) {} else {
                validate27.errors = [{
                  instancePath: instancePath + "/settings",
                  schemaPath: "#/definitions/Settings/type",
                  keyword: "type",
                  params: {
                    type: "object"
                  },
                  message: "must be object"
                }];
                return false;
              }
            }
          }
        }
      }
    } else {
      validate27.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate27.errors = vErrors;
  return errors === 0;
}

function validate26(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      for (const key0 in data) {
        const _errs2 = errors;

        if (!validate27(data[key0], {
          instancePath: instancePath + "/" + key0.replace(/~/g, "~0").replace(/\//g, "~1"),
          parentData: data,
          parentDataProperty: key0,
          rootData
        })) {
          vErrors = vErrors === null ? validate27.errors : vErrors.concat(validate27.errors);
          errors = vErrors.length;
        }

        var valid0 = _errs2 === errors;

        if (!valid0) {
          break;
        }
      }
    } else {
      validate26.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate26.errors = vErrors;
  return errors === 0;
}

function validate25(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.debug === undefined && (missing0 = "debug") || data.features === undefined && (missing0 = "features") || data.platform === undefined && (missing0 = "platform")) {
        validate25.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.debug !== undefined) {
          const _errs1 = errors;

          if (typeof data.debug !== "boolean") {
            validate25.errors = [{
              instancePath: instancePath + "/debug",
              schemaPath: "#/properties/debug/type",
              keyword: "type",
              params: {
                type: "boolean"
              },
              message: "must be boolean"
            }];
            return false;
          }

          var valid0 = _errs1 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.platform !== undefined) {
            let data1 = data.platform;
            const _errs3 = errors;
            const _errs4 = errors;

            if (errors === _errs4) {
              if (data1 && typeof data1 == "object" && !Array.isArray(data1)) {
                let missing1;

                if (data1.name === undefined && (missing1 = "name")) {
                  validate25.errors = [{
                    instancePath: instancePath + "/platform",
                    schemaPath: "#/definitions/Platform/required",
                    keyword: "required",
                    params: {
                      missingProperty: missing1
                    },
                    message: "must have required property '" + missing1 + "'"
                  }];
                  return false;
                } else {
                  if (data1.name !== undefined) {
                    let data2 = data1.name;

                    if (typeof data2 !== "string") {
                      validate25.errors = [{
                        instancePath: instancePath + "/platform/name",
                        schemaPath: "#/definitions/Platform/properties/name/type",
                        keyword: "type",
                        params: {
                          type: "string"
                        },
                        message: "must be string"
                      }];
                      return false;
                    }

                    if (!(data2 === "ios" || data2 === "macos" || data2 === "windows" || data2 === "extension" || data2 === "android" || data2 === "unknown")) {
                      validate25.errors = [{
                        instancePath: instancePath + "/platform/name",
                        schemaPath: "#/definitions/Platform/properties/name/enum",
                        keyword: "enum",
                        params: {
                          allowedValues: schema27.properties.name.enum
                        },
                        message: "must be equal to one of the allowed values"
                      }];
                      return false;
                    }
                  }
                }
              } else {
                validate25.errors = [{
                  instancePath: instancePath + "/platform",
                  schemaPath: "#/definitions/Platform/type",
                  keyword: "type",
                  params: {
                    type: "object"
                  },
                  message: "must be object"
                }];
                return false;
              }
            }

            var valid0 = _errs3 === errors;
          } else {
            var valid0 = true;
          }

          if (valid0) {
            if (data.features !== undefined) {
              const _errs8 = errors;

              if (!validate26(data.features, {
                instancePath: instancePath + "/features",
                parentData: data,
                parentDataProperty: "features",
                rootData
              })) {
                vErrors = vErrors === null ? validate26.errors : vErrors.concat(validate26.errors);
                errors = vErrors.length;
              }

              var valid0 = _errs8 === errors;
            } else {
              var valid0 = true;
            }
          }
        }
      }
    } else {
      validate25.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate25.errors = vErrors;
  return errors === 0;
}

function validate20(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/RuntimeConfiguration" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.contentScope === undefined && (missing0 = "contentScope") || data.userPreferences === undefined && (missing0 = "userPreferences") || data.userUnprotectedDomains === undefined && (missing0 = "userUnprotectedDomains")) {
        validate20.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        const _errs1 = errors;

        for (const key0 in data) {
          if (!(key0 === "contentScope" || key0 === "userUnprotectedDomains" || key0 === "userPreferences")) {
            validate20.errors = [{
              instancePath,
              schemaPath: "#/additionalProperties",
              keyword: "additionalProperties",
              params: {
                additionalProperty: key0
              },
              message: "must NOT have additional properties"
            }];
            return false;
            break;
          }
        }

        if (_errs1 === errors) {
          if (data.contentScope !== undefined) {
            const _errs2 = errors;

            if (!validate21(data.contentScope, {
              instancePath: instancePath + "/contentScope",
              parentData: data,
              parentDataProperty: "contentScope",
              rootData
            })) {
              vErrors = vErrors === null ? validate21.errors : vErrors.concat(validate21.errors);
              errors = vErrors.length;
            }

            var valid0 = _errs2 === errors;
          } else {
            var valid0 = true;
          }

          if (valid0) {
            if (data.userUnprotectedDomains !== undefined) {
              const _errs3 = errors;

              if (errors === _errs3) {
                if (!Array.isArray(data.userUnprotectedDomains)) {
                  validate20.errors = [{
                    instancePath: instancePath + "/userUnprotectedDomains",
                    schemaPath: "#/properties/userUnprotectedDomains/type",
                    keyword: "type",
                    params: {
                      type: "array"
                    },
                    message: "must be array"
                  }];
                  return false;
                }
              }

              var valid0 = _errs3 === errors;
            } else {
              var valid0 = true;
            }

            if (valid0) {
              if (data.userPreferences !== undefined) {
                const _errs5 = errors;

                if (!validate25(data.userPreferences, {
                  instancePath: instancePath + "/userPreferences",
                  parentData: data,
                  parentDataProperty: "userPreferences",
                  rootData
                })) {
                  vErrors = vErrors === null ? validate25.errors : vErrors.concat(validate25.errors);
                  errors = vErrors.length;
                }

                var valid0 = _errs5 === errors;
              } else {
                var valid0 = true;
              }
            }
          }
        }
      }
    } else {
      validate20.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate20.errors = vErrors;
  return errors === 0;
}

},{}],6:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.SchemaValidationError = exports.Message = void 0;

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * @template [Request=any],[Response=any]
 */
class Message {
  /**
   * @type {any}
   */

  /**
   * @type {any}
   */

  /**
   * String representation of this message's name
   * @type {string}
   */

  /**
   * The name of a response message, if it exists
   * @type {string}
   */

  /**
   * This is the data that will be sent in the message.
   * @type {Request|undefined}
   */

  /**
   * @param {Request} [data]
   */
  constructor(data) {
    _defineProperty(this, "reqValidator", null);

    _defineProperty(this, "resValidator", null);

    _defineProperty(this, "name", 'unknown');

    _defineProperty(this, "responseName", this.name + 'Response');

    _defineProperty(this, "data", void 0);

    this.data = data;
  }
  /**
   * @returns {Request|undefined}
   */


  validateRequest() {
    var _this$reqValidator;

    if (this.reqValidator && !((_this$reqValidator = this.reqValidator) !== null && _this$reqValidator !== void 0 && _this$reqValidator.call(this, this.data))) {
      var _this$reqValidator2;

      this.throwError((_this$reqValidator2 = this.reqValidator) === null || _this$reqValidator2 === void 0 ? void 0 : _this$reqValidator2.errors);
    }

    return this.data;
  }
  /**
   * @param {import('ajv').ErrorObject[]} errors
   */


  throwError(errors) {
    const error = SchemaValidationError.fromErrors(errors, this.constructor.name);
    throw error;
  }
  /**
   * @param {any|null|undefined} incoming
   * @returns {Response}
   */


  validateResponse(incoming) {
    var _this$resValidator;

    if (this.resValidator && !((_this$resValidator = this.resValidator) !== null && _this$resValidator !== void 0 && _this$resValidator.call(this, incoming))) {
      var _this$resValidator2;

      this.throwError((_this$resValidator2 = this.resValidator) === null || _this$resValidator2 === void 0 ? void 0 : _this$resValidator2.errors);
    }

    if (!incoming) {
      return incoming;
    }

    if ('data' in incoming) {
      console.warn('response had `data` property. Please migrate to `success`');
      return incoming.data;
    }

    if ('success' in incoming) {
      return incoming.success;
    }

    throw new Error('unreachable. Response did not contain `success` or `data`');
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
   * @param {Response} response
   * @returns {Response}
   */


  response(response) {
    return response;
  }
  /**
   * This is where you can alter the response before validation,
   * to aid when migrating legacy messages, or to alter data to conform
   * to a schema before senders can change their format
   *
   * For example, an old message may not be returned under the 'success' key,
   * so you can wrap the value here.
   *
   * @example
   *
   * ```js
   * class RecoveringMessage extends Message {
   *     preResponseValidation (response) {
   *         return { success: response }
   *     }
   * }
   * ```
   *
   * @param {any} response
   * @returns {{success: Response, error?: string}}
   */


  preResponseValidation(response) {
    return response;
  }

}
/**
 * Check for this error if you'd like to
 */


exports.Message = Message;

class SchemaValidationError extends Error {
  constructor() {
    super(...arguments);

    _defineProperty(this, "validationErrors", []);
  }

  /**
   * @param {import("ajv").ErrorObject[]} errors
   * @param {string} name
   * @returns {SchemaValidationError}
   */
  static fromErrors(errors, name) {
    const heading = "".concat(errors.length, " SchemaValidationError(s) errors for ") + name;
    const lines = [];

    for (const error of errors) {
      // console.log(JSON.stringify(error, null, 2));
      lines.push(error.message || 'unknown');
    }

    const message = [heading, ...lines].join('\n    ');
    const error = new SchemaValidationError(message);
    error.validationErrors = errors;
    return error;
  }

}

exports.SchemaValidationError = SchemaValidationError;

},{}],7:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.UnimplementedError = exports.Sender = void 0;

/**
 * This is the base Sender class that platforms can will implement.
 *
 * Note: The 'handle' method must be implemented, unless you also implement 'send'
 */
class Sender {
  /**
   * Try to send a message. Throws with validation errors
   *
   * @throws {SchemaValidationError}
   * @template Request,Response
   * @param {import("./message").Message<Request, Response>} message
   * @returns {Promise<ReturnType<import("./message").Message<Request, Response>['validateResponse']>>}
   */
  async send(message) {
    message.validateRequest();
    const response = await this.handle(message);
    const processed = message.preResponseValidation(response);
    return message.validateResponse(processed);
  }
  /**
   * @template Request,Response
   * @param {import("./message").Message<Request, Response>} message
   * @returns {Promise<Response | undefined>}
   */


  async handle(message) {
    throw new UnimplementedError('Must implement `sender.handle`, tried to send message.name: ' + message.name);
  }

}

exports.Sender = Sender;

class UnimplementedError extends Error {}

exports.UnimplementedError = UnimplementedError;
;

},{}],8:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.DDGReflect = exports.DDGProxy = exports.DDGPromise = void 0;
exports.defineProperty = defineProperty;
exports.getDataKeySync = getDataKeySync;
exports.getFeatureSetting = getFeatureSetting;
exports.getFeatureSettingEnabled = getFeatureSettingEnabled;
exports.initStringExemptionLists = initStringExemptionLists;
exports.isFeatureBroken = isFeatureBroken;
exports.isFeatureEnabledFromProcessedConfig = isFeatureEnabledFromProcessedConfig;
exports.iterateDataKey = iterateDataKey;
exports.nextRandom = nextRandom;
exports.overrideProperty = overrideProperty;
exports.postDebugMessage = postDebugMessage;
exports.shouldExemptMethod = shouldExemptMethod;
exports.shouldExemptUrl = shouldExemptUrl;

var _sjcl = require("../lib/sjcl.js");

/* global cloneInto, exportFunction, mozProxies */
// Only use globalThis for testing this breaks window.wrappedJSObject code in Firefox
// eslint-disable-next-line no-global-assign
const globalObj = typeof window === 'undefined' ? globalThis : window; // Tests don't define this variable so fallback to behave like chrome

const hasMozProxies = typeof mozProxies !== 'undefined' ? mozProxies : false;

function getDataKeySync(sessionKey, domainKey, inputData) {
  // eslint-disable-next-line new-cap
  const hmac = new _sjcl.sjcl.misc.hmac(_sjcl.sjcl.codec.utf8String.toBits(sessionKey + domainKey), _sjcl.sjcl.hash.sha256);
  return _sjcl.sjcl.codec.hex.fromBits(hmac.encrypt(inputData));
} // linear feedback shift register to find a random approximation


function nextRandom(v) {
  return Math.abs(v >> 1 | (v << 62 ^ v << 61) & ~(~0 << 63) << 62);
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
  const {
    stringExemptionLists
  } = args;
  debug = args.debug;

  for (const type in stringExemptionLists) {
    exemptionLists[type] = [];

    for (const stringExemption of stringExemptionLists[type]) {
      exemptionLists[type].push(new RegExp(stringExemption));
    }
  }
} // Checks the stack trace if there are known libraries that are broken.


function shouldExemptMethod(type) {
  // Short circuit stack tracing if we don't have checks
  if (!(type in exemptionLists) || exemptionLists[type].length === 0) {
    return false;
  }

  try {
    const errorLines = new Error().stack.split('\n');
    const errorFiles = new Set(); // Should cater for Chrome and Firefox stacks, we only care about https? resources.

    const lineTest = /(\()?(http[^)]+):[0-9]+:[0-9]+(\))?/;

    for (const line of errorLines) {
      const res = line.match(lineTest);

      if (res) {
        const path = res[2]; // checked already

        if (errorFiles.has(path)) {
          continue;
        }

        if (shouldExemptUrl(type, path)) {
          return true;
        }

        errorFiles.add(res[2]);
      }
    }
  } catch (e) {// Fall through
  }

  return false;
} // Iterate through the key, passing an item index and a byte to be modified


function iterateDataKey(key, callback) {
  let item = key.charCodeAt(0);

  for (const i in key) {
    let byte = key.charCodeAt(i);

    for (let j = 8; j >= 0; j--) {
      const res = callback(item, byte); // Exit early if callback returns null

      if (res === null) {
        return;
      } // find next item to perturb


      item = nextRandom(item); // Right shift as we use the least significant bit of it

      byte = byte >> 1;
    }
  }
}

function isFeatureBroken(args, feature) {
  return args.site.isBroken || args.site.allowlisted || !args.site.enabledFeatures.includes(feature);
}

function isFeatureEnabledFromProcessedConfig(processedConfig, featureName) {
  const site = processedConfig.site;

  if (site.isBroken || !site.enabledFeatures.includes(featureName)) {
    return false;
  }

  return true;
}
/**
 * For each property defined on the object, update it with the target value.
 */


function overrideProperty(name, prop) {
  // Don't update if existing value is undefined or null
  if (!(prop.origValue === undefined)) {
    /**
     * When re-defining properties, we bind the overwritten functions to null. This prevents
     * sites from using toString to see if the function has been overwritten
     * without this bind call, a site could run something like
     * `Object.getOwnPropertyDescriptor(Screen.prototype, "availTop").get.toString()` and see
     * the contents of the function. Appending .bind(null) to the function definition will
     * have the same toString call return the default [native code]
     */
    try {
      defineProperty(prop.object, name, {
        // eslint-disable-next-line no-extra-bind
        get: (() => prop.targetValue).bind(null)
      });
    } catch (e) {}
  }

  return prop.origValue;
}

function defineProperty(object, propertyName, descriptor) {
  if (hasMozProxies) {
    const usedObj = object.wrappedJSObject;
    const UsedObjectInterface = globalObj.wrappedJSObject.Object;
    const definedDescriptor = new UsedObjectInterface();
    ['configurable', 'enumerable', 'value', 'writable'].forEach(propertyName => {
      if (propertyName in descriptor) {
        definedDescriptor[propertyName] = cloneInto(descriptor[propertyName], definedDescriptor, {
          cloneFunctions: true
        });
      }
    });
    ['get', 'set'].forEach(methodName => {
      if (methodName in descriptor) {
        exportFunction(descriptor[methodName], definedDescriptor, {
          defineAs: methodName
        });
      }
    });
    UsedObjectInterface.defineProperty(usedObj, propertyName, definedDescriptor);
  } else {
    Object.defineProperty(object, propertyName, descriptor);
  }
}

function camelcase(dashCaseText) {
  return dashCaseText.replace(/-(.)/g, (match, letter) => {
    return letter.toUpperCase();
  });
}
/**
 * @param {string} featureName
 * @param {object} args
 * @param {string} prop
 * @returns {any}
 */


function getFeatureSetting(featureName, args, prop) {
  var _args$featureSettings, _args$featureSettings2;

  const camelFeatureName = camelcase(featureName);
  return (_args$featureSettings = args.featureSettings) === null || _args$featureSettings === void 0 ? void 0 : (_args$featureSettings2 = _args$featureSettings[camelFeatureName]) === null || _args$featureSettings2 === void 0 ? void 0 : _args$featureSettings2[prop];
}
/**
 * @param {string} featureName
 * @param {object} args
 * @param {string} prop
 * @returns {boolean}
 */


function getFeatureSettingEnabled(featureName, args, prop) {
  const result = getFeatureSetting(featureName, args, prop);
  return result === 'enabled';
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
   * @param {string} featureName
   * @param {P} objectScope
   * @param {string} property
   * @param {ProxyObject<P>} proxyObject
   */
  constructor(featureName, objectScope, property, proxyObject) {
    var _this = this;

    this.objectScope = objectScope;
    this.property = property;
    this.featureName = featureName;
    this.camelFeatureName = camelcase(this.featureName);

    const outputHandler = function () {
      const isExempt = shouldExemptMethod(_this.camelFeatureName);

      if (debug) {
        postDebugMessage(_this.camelFeatureName, {
          action: isExempt ? 'ignore' : 'restrict',
          kind: _this.property,
          documentUrl: document.location.href,
          stack: new Error().stack,
          args: JSON.stringify(arguments.length <= 2 ? undefined : arguments[2])
        });
      } // The normal return value


      if (isExempt) {
        return DDGReflect.apply(...arguments);
      }

      return proxyObject.apply(...arguments);
    };

    if (hasMozProxies) {
      this._native = objectScope[property];
      const handler = new globalObj.wrappedJSObject.Object();
      handler.apply = exportFunction(outputHandler, globalObj); // @ts-ignore

      this.internal = new globalObj.wrappedJSObject.Proxy(objectScope.wrappedJSObject[property], handler);
    } else {
      this._native = objectScope[property];
      const handler = {};
      handler.apply = outputHandler;
      this.internal = new globalObj.Proxy(objectScope[property], handler);
    }
  } // Actually apply the proxy to the native property


  overload() {
    if (hasMozProxies) {
      // @ts-ignore
      exportFunction(this.internal, this.objectScope, {
        defineAs: this.property
      });
    } else {
      this.objectScope[this.property] = this.internal;
    }
  }

}

exports.DDGProxy = DDGProxy;

function postDebugMessage(feature, message) {
  globalObj.postMessage({
    action: feature,
    message
  });
}

let DDGReflect;
exports.DDGReflect = DDGReflect;
let DDGPromise; // Exports for usage where we have to cross the xray boundary: https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Sharing_objects_with_page_scripts

exports.DDGPromise = DDGPromise;

if (hasMozProxies) {
  exports.DDGPromise = DDGPromise = globalObj.wrappedJSObject.Promise;
  exports.DDGReflect = DDGReflect = globalObj.wrappedJSObject.Reflect;
} else {
  exports.DDGPromise = DDGPromise = globalObj.Promise;
  exports.DDGReflect = DDGReflect = globalObj.Reflect;
}

},{"../lib/sjcl.js":2}],9:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
}); // https://github.com/ajv-validator/ajv/issues/889

const equal = require("fast-deep-equal");

equal.code = 'require("ajv/dist/runtime/equal").default';
exports.default = equal;

},{"fast-deep-equal":10}],10:[function(require,module,exports){
'use strict'; // do not edit .js files directly - edit src/index.jst

module.exports = function equal(a, b) {
  if (a === b) return true;

  if (a && b && typeof a == 'object' && typeof b == 'object') {
    if (a.constructor !== b.constructor) return false;
    var length, i, keys;

    if (Array.isArray(a)) {
      length = a.length;
      if (length != b.length) return false;

      for (i = length; i-- !== 0;) if (!equal(a[i], b[i])) return false;

      return true;
    }

    if (a.constructor === RegExp) return a.source === b.source && a.flags === b.flags;
    if (a.valueOf !== Object.prototype.valueOf) return a.valueOf() === b.valueOf();
    if (a.toString !== Object.prototype.toString) return a.toString() === b.toString();
    keys = Object.keys(a);
    length = keys.length;
    if (length !== Object.keys(b).length) return false;

    for (i = length; i-- !== 0;) if (!Object.prototype.hasOwnProperty.call(b, keys[i])) return false;

    for (i = length; i-- !== 0;) {
      var key = keys[i];
      if (!equal(a[key], b[key])) return false;
    }

    return true;
  } // true if both NaN, false otherwise


  return a !== a && b !== b;
};

},{}],11:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.HostnameInputError = void 0;
Object.defineProperty(exports, "ParserError", {
  enumerable: true,
  get: function () {
    return _rulesParser.ParserError;
  }
});
exports._selectPasswordRules = _selectPasswordRules;
Object.defineProperty(exports, "constants", {
  enumerable: true,
  get: function () {
    return _constants.constants;
  }
});
exports.generate = generate;

var _applePassword = require("./lib/apple.password.js");

var _rulesParser = require("./lib/rules-parser.js");

var _constants = require("./lib/constants.js");

/**
 * @typedef {{
 *   domain?: string | null | undefined;
 *   input?: string | null | undefined;
 *   rules?: RulesFormat | null | undefined;
 *   onError?: ((error: unknown) => void) | null | undefined;
 * }} GenerateOptions
 */

/**
 * Generate a random password based on the following attempts
 *
 * 1) using `options.input` if provided -> falling back to default ruleset
 * 2) using `options.domain` if provided -> falling back to default ruleset
 * 3) using default ruleset
 *
 * Note: This API is designed to never throw - if you want to observe errors
 * during development, you can provide an `onError` callback
 *
 * @param {GenerateOptions} [options]
 */
function generate() {
  let options = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {};

  try {
    if (typeof (options === null || options === void 0 ? void 0 : options.input) === 'string') {
      return _applePassword.Password.generateOrThrow(options.input);
    }

    if (typeof (options === null || options === void 0 ? void 0 : options.domain) === 'string') {
      if (options !== null && options !== void 0 && options.rules) {
        const rules = _selectPasswordRules(options.domain, options.rules);

        if (rules) {
          return _applePassword.Password.generateOrThrow(rules);
        }
      }
    }
  } catch (e) {
    // if an 'onError' callback was provided, forward all errors
    if (options !== null && options !== void 0 && options.onError && typeof (options === null || options === void 0 ? void 0 : options.onError) === 'function') {
      options.onError(e);
    } else {
      // otherwise, only console.error unknown errors (which could be implementation bugs)
      const isKnownError = e instanceof _rulesParser.ParserError || e instanceof HostnameInputError;

      if (!isKnownError) {
        console.error(e);
      }
    }
  } // At this point, we have to trust the generation will not throw
  // as it is NOT using any user/page-provided data


  return _applePassword.Password.generateDefault();
} // An extension type to differentiate between known errors


class HostnameInputError extends Error {}
/**
 * @typedef {Record<string, {"password-rules": string}>} RulesFormat
 */

/**
 * @private
 * @param {string} inputHostname
 * @param {RulesFormat} rules
 * @returns {string | undefined}
 * @throws {HostnameInputError}
 */


exports.HostnameInputError = HostnameInputError;

function _selectPasswordRules(inputHostname, rules) {
  const hostname = _safeHostname(inputHostname); // direct match


  if (rules[hostname]) {
    return rules[hostname]['password-rules'];
  } // otherwise, start chopping off subdomains and re-joining to compare


  const pieces = hostname.split('.');

  while (pieces.length > 1) {
    pieces.shift();
    const joined = pieces.join('.');

    if (rules[joined]) {
      return rules[joined]['password-rules'];
    }
  }

  return undefined;
}
/**
 * @private
 * @param {string} inputHostname;
 * @throws {HostnameInputError}
 * @returns {string}
 */


function _safeHostname(inputHostname) {
  if (inputHostname.startsWith('http:') || inputHostname.startsWith('https:')) {
    throw new HostnameInputError('invalid input, you can only provide a hostname but you gave a scheme');
  }

  if (inputHostname.includes(':')) {
    throw new HostnameInputError('invalid input, you can only provide a hostname but you gave a :port');
  }

  try {
    const asUrl = new URL('https://' + inputHostname);
    return asUrl.hostname;
  } catch (e) {
    throw new HostnameInputError("could not instantiate a URL from that hostname ".concat(inputHostname));
  }
}

},{"./lib/apple.password.js":12,"./lib/constants.js":13,"./lib/rules-parser.js":14}],12:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.Password = void 0;

var parser = _interopRequireWildcard(require("./rules-parser.js"));

var _constants = require("./constants.js");

function _getRequireWildcardCache(nodeInterop) { if (typeof WeakMap !== "function") return null; var cacheBabelInterop = new WeakMap(); var cacheNodeInterop = new WeakMap(); return (_getRequireWildcardCache = function (nodeInterop) { return nodeInterop ? cacheNodeInterop : cacheBabelInterop; })(nodeInterop); }

function _interopRequireWildcard(obj, nodeInterop) { if (!nodeInterop && obj && obj.__esModule) { return obj; } if (obj === null || typeof obj !== "object" && typeof obj !== "function") { return { default: obj }; } var cache = _getRequireWildcardCache(nodeInterop); if (cache && cache.has(obj)) { return cache.get(obj); } var newObj = {}; var hasPropertyDescriptor = Object.defineProperty && Object.getOwnPropertyDescriptor; for (var key in obj) { if (key !== "default" && Object.prototype.hasOwnProperty.call(obj, key)) { var desc = hasPropertyDescriptor ? Object.getOwnPropertyDescriptor(obj, key) : null; if (desc && (desc.get || desc.set)) { Object.defineProperty(newObj, key, desc); } else { newObj[key] = obj[key]; } } } newObj.default = obj; if (cache) { cache.set(obj, newObj); } return newObj; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * @typedef {{
 *     PasswordAllowedCharacters?: string,
 *     PasswordRequiredCharacters?: string[],
 *     PasswordRepeatedCharacterLimit?: number,
 *     PasswordConsecutiveCharacterLimit?: number,
 *     PasswordMinLength?: number,
 *     PasswordMaxLength?: number,
 * }} Requirements
 */

/**
 * @typedef {{
 *     NumberOfRequiredRandomCharacters: number,
 *     PasswordAllowedCharacters: string,
 *     RequiredCharacterSets: string[]
 * }} PasswordParameters
 */
const defaults = Object.freeze({
  SCAN_SET_ORDER: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-~!@#$%^&*_+=`|(){}[:;\\\"'<>,.?/ ]",
  defaultUnambiguousCharacters: 'abcdefghijkmnopqrstuvwxyzABCDEFGHIJKLMNPQRSTUVWXYZ0123456789',
  defaultPasswordLength: _constants.constants.DEFAULT_MIN_LENGTH,
  defaultPasswordRules: _constants.constants.DEFAULT_PASSWORD_RULES,
  defaultRequiredCharacterSets: ['abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', '0123456789'],

  /**
   * @type {typeof window.crypto.getRandomValues | typeof import("crypto").randomFillSync | null}
   */
  getRandomValues: null
});
/**
 * This is added here to ensure:
 *
 * 1) `getRandomValues` is called with the correct prototype chain
 * 2) `window` is not accessed when in a node environment
 * 3) `bind` is not called in a hot code path
 *
 * @type {{ getRandomValues: typeof window.crypto.getRandomValues }}
 */

const safeGlobals = {};

if (typeof window !== 'undefined') {
  safeGlobals.getRandomValues = window.crypto.getRandomValues.bind(window.crypto);
}

class Password {
  /**
   * @type {typeof defaults}
   */

  /**
   * @param {Partial<typeof defaults>} [options]
   */
  constructor() {
    let options = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {};

    _defineProperty(this, "options", void 0);

    this.options = { ...defaults,
      ...options
    };
    return this;
  }
  /**
   * This is here to provide external access to un-modified defaults
   * in case they are needed for tests/verifications
   * @type {typeof defaults}
   */


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
  static generateOrThrow(inputString) {
    let options = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
    return new Password(options).parse(inputString).generate();
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


  static generateDefault() {
    let options = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {};
    return new Password(options).parse(Password.defaults.defaultPasswordRules).generate();
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
    const rules = parser.parsePasswordRules(inputString);

    const requirements = this._requirementsFromRules(rules);

    if (!requirements) throw new Error('could not generate requirements for ' + JSON.stringify(inputString));

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
        /**
         * The following is unreachable because if user input was incorrect then
         * the parsing phase would throw. The following lines is to satisfy Typescript
         */


        if (password === '') throw new Error('unreachable');
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
    /** @type {Requirements} */
    const requirements = {};

    for (let rule of passwordRules) {
      if (rule.name === parser.RuleName.ALLOWED) {
        console.assert(!('PasswordAllowedCharacters' in requirements));

        const chars = this._charactersFromCharactersClasses(rule.value);

        const scanSet = this._canonicalizedScanSetFromCharacters(chars);

        if (scanSet) {
          requirements.PasswordAllowedCharacters = scanSet;
        }
      } else if (rule.name === parser.RuleName.MAX_CONSECUTIVE) {
        console.assert(!('PasswordRepeatedCharacterLimit' in requirements));
        requirements.PasswordRepeatedCharacterLimit = rule.value;
      } else if (rule.name === parser.RuleName.REQUIRED) {
        let requiredCharacters = requirements.PasswordRequiredCharacters;

        if (!requiredCharacters) {
          requiredCharacters = requirements.PasswordRequiredCharacters = [];
        }

        requiredCharacters.push(this._canonicalizedScanSetFromCharacters(this._charactersFromCharactersClasses(rule.value)));
      } else if (rule.name === parser.RuleName.MIN_LENGTH) {
        requirements.PasswordMinLength = rule.value;
      } else if (rule.name === parser.RuleName.MAX_LENGTH) {
        requirements.PasswordMaxLength = rule.value;
      }
    } // Only include an allowed rule matching SCAN_SET_ORDER (all characters) when a required rule is also present.


    if (requirements.PasswordAllowedCharacters === this.options.SCAN_SET_ORDER && !requirements.PasswordRequiredCharacters) {
      delete requirements.PasswordAllowedCharacters;
    } // Fix up PasswordRequiredCharacters, if needed.


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
    const getRandomValues = this.options.getRandomValues || safeGlobals.getRandomValues; // Based on the algorithm described in https://pthree.org/2018/06/13/why-the-multiply-and-floor-rng-method-is-biased/

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

    return randomCharArray.join('');
  }
  /**
   * @param {string} password
   * @param {number} consecutiveCharLimit
   * @returns {boolean}
   */


  _passwordHasNotExceededConsecutiveCharLimit(password, consecutiveCharLimit) {
    let longestConsecutiveCharLength = 1;
    let firstConsecutiveCharIndex = 0; // Both "123" or "abc" and "321" or "cba" are considered consecutive.

    let isSequenceAscending;

    for (let i = 1; i < password.length; i++) {
      const currCharCode = password.charCodeAt(i);
      const prevCharCode = password.charCodeAt(i - 1);

      if (isSequenceAscending) {
        // If `isSequenceAscending` is defined, then we know that we are in the middle of an existing
        // pattern. Check if the pattern continues based on whether the previous pattern was
        // ascending or descending.
        if (isSequenceAscending.valueOf() && currCharCode === prevCharCode + 1 || !isSequenceAscending.valueOf() && currCharCode === prevCharCode - 1) {
          continue;
        } // Take into account the case when the sequence transitions from descending
        // to ascending.


        if (currCharCode === prevCharCode + 1) {
          firstConsecutiveCharIndex = i - 1;
          isSequenceAscending = Boolean(true);
          continue;
        } // Take into account the case when the sequence transitions from ascending
        // to descending.


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
    const maxPasswordLength = requirements.PasswordMaxLength; // @ts-ignore

    if (minPasswordLength > maxPasswordLength) {
      // Resetting invalid value of min length to zero means "ignore min length parameter in password generation".
      minPasswordLength = 0;
    }

    const requiredCharacterArray = requirements.PasswordRequiredCharacters;
    let allowedCharacters = requirements.PasswordAllowedCharacters;
    let requiredCharacterSets = this.options.defaultRequiredCharacterSets;

    if (requiredCharacterArray) {
      const mutatedRequiredCharacterSets = [];
      const requiredCharacterArrayLength = requiredCharacterArray.length;

      for (let i = 0; i < requiredCharacterArrayLength; i++) {
        const requiredCharacters = requiredCharacterArray[i];

        if (allowedCharacters && this._stringsHaveAtLeastOneCommonCharacter(requiredCharacters, allowedCharacters)) {
          mutatedRequiredCharacterSets.push(requiredCharacters);
        }
      }

      requiredCharacterSets = mutatedRequiredCharacterSets;
    } // If requirements allow, we will generateOrThrow the password in default format: "xxx-xxx-xxx-xxx".


    let numberOfRequiredRandomCharacters = this.options.defaultPasswordLength;

    if (minPasswordLength && minPasswordLength > numberOfRequiredRandomCharacters) {
      numberOfRequiredRandomCharacters = minPasswordLength;
    }

    if (maxPasswordLength && maxPasswordLength < numberOfRequiredRandomCharacters) {
      numberOfRequiredRandomCharacters = maxPasswordLength;
    }

    if (!allowedCharacters) {
      allowedCharacters = this.options.defaultUnambiguousCharacters;
    } // In default password format, we use dashes only as separators, not as symbols you can encounter at a random position.


    if (!requiredCharacterSets) {
      requiredCharacterSets = this.options.defaultRequiredCharacterSets;
    } // If we have more requirements of the type "need a character from set" than the length of the password we want to generateOrThrow, then
    // we will never be able to meet these requirements, and we'll end up in an infinite loop generating passwords. To avoid this,
    // reset required character sets if the requirements are impossible to meet.


    if (requiredCharacterSets.length > numberOfRequiredRandomCharacters) {
      requiredCharacterSets = [];
    } // Do not require any character sets that do not contain allowed characters.


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
        if (repeatedCharLimit !== undefined && repeatedCharLimit >= 1 && !this._passwordHasNotExceededRepeatedCharLimit(password, repeatedCharLimit)) {
          continue;
        }
      }

      const consecutiveCharLimit = requirements.PasswordConsecutiveCharacterLimit;

      if (consecutiveCharLimit && consecutiveCharLimit >= 1) {
        if (!this._passwordHasNotExceededConsecutiveCharLimit(password, consecutiveCharLimit)) {
          continue;
        }
      }

      return password || '';
    }
  }
  /**
   * @param {parser.CustomCharacterClass | parser.NamedCharacterClass} characterClass
   * @returns {string[]}
   */


  _scanSetFromCharacterClass(characterClass) {
    if (characterClass instanceof parser.CustomCharacterClass) {
      return characterClass.characters;
    }

    console.assert(characterClass instanceof parser.NamedCharacterClass);

    switch (characterClass.name) {
      case parser.Identifier.ASCII_PRINTABLE:
      case parser.Identifier.UNICODE:
        return this.options.SCAN_SET_ORDER.split('');

      case parser.Identifier.DIGIT:
        return this.options.SCAN_SET_ORDER.substring(this.options.SCAN_SET_ORDER.indexOf('0'), this.options.SCAN_SET_ORDER.indexOf('9') + 1).split('');

      case parser.Identifier.LOWER:
        return this.options.SCAN_SET_ORDER.substring(this.options.SCAN_SET_ORDER.indexOf('a'), this.options.SCAN_SET_ORDER.indexOf('z') + 1).split('');

      case parser.Identifier.SPECIAL:
        return this.options.SCAN_SET_ORDER.substring(this.options.SCAN_SET_ORDER.indexOf('-'), this.options.SCAN_SET_ORDER.indexOf(']') + 1).split('');

      case parser.Identifier.UPPER:
        return this.options.SCAN_SET_ORDER.substring(this.options.SCAN_SET_ORDER.indexOf('A'), this.options.SCAN_SET_ORDER.indexOf('Z') + 1).split('');
    }

    console.assert(false, parser.SHOULD_NOT_BE_REACHED);
    return [];
  }
  /**
   * @param {(parser.CustomCharacterClass | parser.NamedCharacterClass)[]} characterClasses
   */


  _charactersFromCharactersClasses(characterClasses) {
    const output = [];

    for (let characterClass of characterClasses) {
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
      return '';
    }

    let shadowCharacters = Array.prototype.slice.call(characters);
    shadowCharacters.sort((a, b) => this.options.SCAN_SET_ORDER.indexOf(a) - this.options.SCAN_SET_ORDER.indexOf(b));
    let uniqueCharacters = [shadowCharacters[0]];

    for (let i = 1, length = shadowCharacters.length; i < length; ++i) {
      if (shadowCharacters[i] === shadowCharacters[i - 1]) {
        continue;
      }

      uniqueCharacters.push(shadowCharacters[i]);
    }

    return uniqueCharacters.join('');
  }

}

exports.Password = Password;

_defineProperty(Password, "defaults", defaults);

},{"./constants.js":13,"./rules-parser.js":14}],13:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.constants = void 0;
const DEFAULT_MIN_LENGTH = 20;
const DEFAULT_MAX_LENGTH = 30;
const DEFAULT_REQUIRED_CHARS = '-!?$&#%';
const DEFAULT_UNAMBIGUOUS_CHARS = 'abcdefghijkmnopqrstuvwxyzABCDEFGHIJKLMNPQRSTUVWXYZ0123456789';
const DEFAULT_PASSWORD_RULES = ["minlength: ".concat(DEFAULT_MIN_LENGTH), "maxlength: ".concat(DEFAULT_MAX_LENGTH), "required: [".concat(DEFAULT_REQUIRED_CHARS, "]"), "allowed: [".concat(DEFAULT_UNAMBIGUOUS_CHARS, "]")].join('; ');
const constants = {
  DEFAULT_MIN_LENGTH,
  DEFAULT_MAX_LENGTH,
  DEFAULT_PASSWORD_RULES,
  DEFAULT_REQUIRED_CHARS,
  DEFAULT_UNAMBIGUOUS_CHARS
};
exports.constants = constants;

},{}],14:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.SHOULD_NOT_BE_REACHED = exports.RuleName = exports.Rule = exports.ParserError = exports.NamedCharacterClass = exports.Identifier = exports.CustomCharacterClass = void 0;
exports.parsePasswordRules = parsePasswordRules;
// Copyright (c) 2019 - 2020 Apple Inc. Licensed under MIT License.

/*
 *
 * NOTE:
 *
 * This file was taken as intended from https://github.com/apple/password-manager-resources.
 *
 * The only additions from DuckDuckGo employees are
 *
 * 1) exporting some identifiers
 * 2) adding some JSDoc comments
 * 3) making this parser throw when it cannot produce any rules
 *    ^ the default implementation still returns a base-line ruleset, which we didn't want.
 *
 */
const Identifier = {
  ASCII_PRINTABLE: 'ascii-printable',
  DIGIT: 'digit',
  LOWER: 'lower',
  SPECIAL: 'special',
  UNICODE: 'unicode',
  UPPER: 'upper'
};
exports.Identifier = Identifier;
const RuleName = {
  ALLOWED: 'allowed',
  MAX_CONSECUTIVE: 'max-consecutive',
  REQUIRED: 'required',
  MIN_LENGTH: 'minlength',
  MAX_LENGTH: 'maxlength'
};
exports.RuleName = RuleName;
const CHARACTER_CLASS_START_SENTINEL = '[';
const CHARACTER_CLASS_END_SENTINEL = ']';
const PROPERTY_VALUE_SEPARATOR = ',';
const PROPERTY_SEPARATOR = ';';
const PROPERTY_VALUE_START_SENTINEL = ':';
const SPACE_CODE_POINT = ' '.codePointAt(0);
const SHOULD_NOT_BE_REACHED = 'Should not be reached';
exports.SHOULD_NOT_BE_REACHED = SHOULD_NOT_BE_REACHED;

class Rule {
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

}

exports.Rule = Rule;
;

class NamedCharacterClass {
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

}

exports.NamedCharacterClass = NamedCharacterClass;
;

class ParserError extends Error {}

exports.ParserError = ParserError;
;

class CustomCharacterClass {
  constructor(characters) {
    console.assert(characters instanceof Array);
    this._characters = characters;
  }

  get characters() {
    return this._characters;
  }

  toString() {
    return "[".concat(this._characters.join(''), "]");
  }

  toHTMLString() {
    return "[".concat(this._characters.join('').replace('"', '&quot;'), "]");
  }

}

exports.CustomCharacterClass = CustomCharacterClass;
; // MARK: Lexer functions

function _isIdentifierCharacter(c) {
  console.assert(c.length === 1); // eslint-disable-next-line no-mixed-operators

  return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c === '-';
}

function _isASCIIDigit(c) {
  console.assert(c.length === 1);
  return c >= '0' && c <= '9';
}

function _isASCIIPrintableCharacter(c) {
  console.assert(c.length === 1);
  return c >= ' ' && c <= '~';
}

function _isASCIIWhitespace(c) {
  console.assert(c.length === 1);
  return c === ' ' || c === '\f' || c === '\n' || c === '\r' || c === '\t';
} // MARK: ASCII printable character bit set and canonicalization functions


function _bitSetIndexForCharacter(c) {
  console.assert(c.length === 1); // @ts-ignore

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
    bitSet.fill(true, _bitSetIndexForCharacter('A'), _bitSetIndexForCharacter('Z') + 1);
  } else if (namedCharacterClass.name === Identifier.LOWER) {
    bitSet.fill(true, _bitSetIndexForCharacter('a'), _bitSetIndexForCharacter('z') + 1);
  } else if (namedCharacterClass.name === Identifier.DIGIT) {
    bitSet.fill(true, _bitSetIndexForCharacter('0'), _bitSetIndexForCharacter('9') + 1);
  } else if (namedCharacterClass.name === Identifier.SPECIAL) {
    bitSet.fill(true, _bitSetIndexForCharacter(' '), _bitSetIndexForCharacter('/') + 1);
    bitSet.fill(true, _bitSetIndexForCharacter(':'), _bitSetIndexForCharacter('@') + 1);
    bitSet.fill(true, _bitSetIndexForCharacter('['), _bitSetIndexForCharacter('`') + 1);
    bitSet.fill(true, _bitSetIndexForCharacter('{'), _bitSetIndexForCharacter('~') + 1);
  } else {
    console.assert(false, SHOULD_NOT_BE_REACHED, namedCharacterClass);
  }
}

function _markBitsForCustomCharacterClass(bitSet, customCharacterClass) {
  for (let character of customCharacterClass.characters) {
    bitSet[_bitSetIndexForCharacter(character)] = true;
  }
}

function _canonicalizedPropertyValues(propertyValues, keepCustomCharacterClassFormatCompliant) {
  // @ts-ignore
  let asciiPrintableBitSet = new Array('~'.codePointAt(0) - ' '.codePointAt(0) + 1);

  for (let propertyValue of propertyValues) {
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
    let temp = [];

    for (let i = _bitSetIndexForCharacter(start); i <= _bitSetIndexForCharacter(end); ++i) {
      if (asciiPrintableBitSet[i]) {
        temp.push(_characterAtBitSetIndex(i));
      }
    }

    let result = temp.length === _bitSetIndexForCharacter(end) - _bitSetIndexForCharacter(start) + 1;

    if (!result) {
      charactersSeen = charactersSeen.concat(temp);
    }

    return result;
  }

  let hasAllUpper = checkRange('A', 'Z');
  let hasAllLower = checkRange('a', 'z');
  let hasAllDigits = checkRange('0', '9'); // Check for special characters, accounting for characters that are given special treatment (i.e. '-' and ']')

  let hasAllSpecial = false;
  let hasDash = false;
  let hasRightSquareBracket = false;
  let temp = [];

  for (let i = _bitSetIndexForCharacter(' '); i <= _bitSetIndexForCharacter('/'); ++i) {
    if (!asciiPrintableBitSet[i]) {
      continue;
    }

    let character = _characterAtBitSetIndex(i);

    if (keepCustomCharacterClassFormatCompliant && character === '-') {
      hasDash = true;
    } else {
      temp.push(character);
    }
  }

  for (let i = _bitSetIndexForCharacter(':'); i <= _bitSetIndexForCharacter('@'); ++i) {
    if (asciiPrintableBitSet[i]) {
      temp.push(_characterAtBitSetIndex(i));
    }
  }

  for (let i = _bitSetIndexForCharacter('['); i <= _bitSetIndexForCharacter('`'); ++i) {
    if (!asciiPrintableBitSet[i]) {
      continue;
    }

    let character = _characterAtBitSetIndex(i);

    if (keepCustomCharacterClassFormatCompliant && character === ']') {
      hasRightSquareBracket = true;
    } else {
      temp.push(character);
    }
  }

  for (let i = _bitSetIndexForCharacter('{'); i <= _bitSetIndexForCharacter('~'); ++i) {
    if (asciiPrintableBitSet[i]) {
      temp.push(_characterAtBitSetIndex(i));
    }
  }

  if (hasDash) {
    temp.unshift('-');
  }

  if (hasRightSquareBracket) {
    temp.push(']');
  }

  let numberOfSpecialCharacters = _bitSetIndexForCharacter('/') - _bitSetIndexForCharacter(' ') + 1 + (_bitSetIndexForCharacter('@') - _bitSetIndexForCharacter(':') + 1) + (_bitSetIndexForCharacter('`') - _bitSetIndexForCharacter('[') + 1) + (_bitSetIndexForCharacter('~') - _bitSetIndexForCharacter('{') + 1);
  hasAllSpecial = temp.length === numberOfSpecialCharacters;

  if (!hasAllSpecial) {
    charactersSeen = charactersSeen.concat(temp);
  }

  let result = [];

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
} // MARK: Parser functions


function _indexOfNonWhitespaceCharacter(input) {
  let position = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 0;
  console.assert(position >= 0);
  console.assert(position <= input.length);
  let length = input.length;

  while (position < length && _isASCIIWhitespace(input[position])) {
    ++position;
  }

  return position;
}

function _parseIdentifier(input, position) {
  console.assert(position >= 0);
  console.assert(position < input.length);
  console.assert(_isIdentifierCharacter(input[position]));
  let length = input.length;
  let seenIdentifiers = [];

  do {
    let c = input[position];

    if (!_isIdentifierCharacter(c)) {
      break;
    }

    seenIdentifiers.push(c);
    ++position;
  } while (position < length);

  return [seenIdentifiers.join(''), position];
}

function _isValidRequiredOrAllowedPropertyValueIdentifier(identifier) {
  return identifier && Object.values(Identifier).includes(identifier.toLowerCase());
}

function _parseCustomCharacterClass(input, position) {
  console.assert(position >= 0);
  console.assert(position < input.length);
  console.assert(input[position] === CHARACTER_CLASS_START_SENTINEL);
  let length = input.length;
  ++position;

  if (position >= length) {
    // console.error('Found end-of-line instead of character class character')
    return [null, position];
  }

  let initialPosition = position;
  let result = [];

  do {
    let c = input[position];

    if (!_isASCIIPrintableCharacter(c)) {
      ++position;
      continue;
    }

    if (c === '-' && position - initialPosition > 0) {
      // FIXME: Should this be an error?
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
    // Fix up result; we over consumed.
    result.pop();
    return [result, position];
  } else if (position === length && input[position - 1] === CHARACTER_CLASS_END_SENTINEL) {
    // Fix up result; we over consumed.
    result.pop();
    return [result, position];
  }

  if (position < length && input[position] === CHARACTER_CLASS_END_SENTINEL) {
    return [result, position + 1];
  } // console.error('Found end-of-line instead of end of character class')


  return [null, position];
}

function _parsePasswordRequiredOrAllowedPropertyValue(input, position) {
  console.assert(position >= 0);
  console.assert(position < input.length);
  let length = input.length;
  let propertyValues = [];

  while (true) {
    if (_isIdentifierCharacter(input[position])) {
      let identifierStartPosition = position; // eslint-disable-next-line no-redeclare

      var [propertyValue, position] = _parseIdentifier(input, position);

      if (!_isValidRequiredOrAllowedPropertyValueIdentifier(propertyValue)) {
        // console.error('Unrecognized property value identifier: ' + propertyValue)
        return [null, identifierStartPosition];
      }

      propertyValues.push(new NamedCharacterClass(propertyValue));
    } else if (input[position] === CHARACTER_CLASS_START_SENTINEL) {
      // eslint-disable-next-line no-redeclare
      var [propertyValue, position] = _parseCustomCharacterClass(input, position);

      if (propertyValue && propertyValue.length) {
        propertyValues.push(new CustomCharacterClass(propertyValue));
      }
    } else {
      // console.error('Failed to find start of property value: ' + input.substr(position))
      return [null, position];
    }

    position = _indexOfNonWhitespaceCharacter(input, position);

    if (position >= length || input[position] === PROPERTY_SEPARATOR) {
      break;
    }

    if (input[position] === PROPERTY_VALUE_SEPARATOR) {
      position = _indexOfNonWhitespaceCharacter(input, position + 1);

      if (position >= length) {
        // console.error('Found end-of-line instead of start of next property value')
        return [null, position];
      }

      continue;
    } // console.error('Failed to find start of next property or property value: ' + input.substr(position))


    return [null, position];
  }

  return [propertyValues, position];
}
/**
 * @param input
 * @param position
 * @returns {[Rule|null, number, string|undefined]}
 * @private
 */


function _parsePasswordRule(input, position) {
  console.assert(position >= 0);
  console.assert(position < input.length);
  console.assert(_isIdentifierCharacter(input[position]));
  let length = input.length;
  var mayBeIdentifierStartPosition = position; // eslint-disable-next-line no-redeclare

  var [identifier, position] = _parseIdentifier(input, position);

  if (!Object.values(RuleName).includes(identifier)) {
    // console.error('Unrecognized property name: ' + identifier)
    return [null, mayBeIdentifierStartPosition, undefined];
  }

  if (position >= length) {
    // console.error('Found end-of-line instead of start of property value')
    return [null, position, undefined];
  }

  if (input[position] !== PROPERTY_VALUE_START_SENTINEL) {
    // console.error('Failed to find start of property value: ' + input.substr(position))
    return [null, position, undefined];
  }

  let property = {
    name: identifier,
    value: null
  };
  position = _indexOfNonWhitespaceCharacter(input, position + 1); // Empty value

  if (position >= length || input[position] === PROPERTY_SEPARATOR) {
    return [new Rule(property.name, property.value), position, undefined];
  }

  switch (identifier) {
    case RuleName.ALLOWED:
    case RuleName.REQUIRED:
      {
        // eslint-disable-next-line no-redeclare
        var [propertyValue, position] = _parsePasswordRequiredOrAllowedPropertyValue(input, position);

        if (propertyValue) {
          property.value = propertyValue;
        }

        return [new Rule(property.name, property.value), position, undefined];
      }

    case RuleName.MAX_CONSECUTIVE:
      {
        // eslint-disable-next-line no-redeclare
        var [propertyValue, position] = _parseMaxConsecutivePropertyValue(input, position);

        if (propertyValue) {
          property.value = propertyValue;
        }

        return [new Rule(property.name, property.value), position, undefined];
      }

    case RuleName.MIN_LENGTH:
    case RuleName.MAX_LENGTH:
      {
        // eslint-disable-next-line no-redeclare
        var [propertyValue, position] = _parseMinLengthMaxLengthPropertyValue(input, position);

        if (propertyValue) {
          property.value = propertyValue;
        }

        return [new Rule(property.name, property.value), position, undefined];
      }
  }

  console.assert(false, SHOULD_NOT_BE_REACHED);
  return [null, -1, undefined];
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
    // console.error('Failed to parse value of type integer; not a number: ' + input.substr(position))
    return [null, position];
  }

  let length = input.length; // let initialPosition = position

  let result = 0;

  do {
    result = 10 * result + parseInt(input[position], 10);
    ++position;
  } while (position < length && input[position] !== PROPERTY_SEPARATOR && _isASCIIDigit(input[position]));

  if (position >= length || input[position] === PROPERTY_SEPARATOR) {
    return [result, position];
  } // console.error('Failed to parse value of type integer; not a number: ' + input.substr(initialPosition))


  return [null, position];
}
/**
 * @param input
 * @returns {[Rule[]|null, string|undefined]}
 * @private
 */


function _parsePasswordRulesInternal(input) {
  let parsedProperties = [];
  let length = input.length;

  var position = _indexOfNonWhitespaceCharacter(input);

  while (position < length) {
    if (!_isIdentifierCharacter(input[position])) {
      // console.warn('Failed to find start of property: ' + input.substr(position))
      return [parsedProperties, undefined];
    } // eslint-disable-next-line no-redeclare


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
        return [parsedProperties, undefined];
      }

      continue;
    } // console.error('Failed to find start of next property: ' + input.substr(position))


    return [null, message || 'Failed to find start of next property: ' + input.substr(position)];
  }

  return [parsedProperties, undefined];
}
/**
 * @param {string} input
 * @param {boolean} [formatRulesForMinifiedVersion]
 * @returns {Rule[]}
 */


function parsePasswordRules(input, formatRulesForMinifiedVersion) {
  let [passwordRules, maybeMessage] = _parsePasswordRulesInternal(input);

  if (!passwordRules) {
    throw new ParserError(maybeMessage);
  }

  if (passwordRules.length === 0) {
    throw new ParserError('No valid rules were provided');
  } // When formatting rules for minified version, we should keep the formatted rules
  // as similar to the input as possible. Avoid copying required rules to allowed rules.


  let suppressCopyingRequiredToAllowed = formatRulesForMinifiedVersion;
  let requiredRules = [];
  let newAllowedValues = [];
  let minimumMaximumConsecutiveCharacters = null;
  let maximumMinLength = 0;
  let minimumMaxLength = null;

  for (let rule of passwordRules) {
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

  let sortedRequiredRules = requiredRules.sort(function (a, b) {
    const namedCharacterClassOrder = [Identifier.LOWER, Identifier.UPPER, Identifier.DIGIT, Identifier.SPECIAL, Identifier.ASCII_PRINTABLE, Identifier.UNICODE];
    let aIsJustOneNamedCharacterClass = a.value.length === 1 && a.value[0] instanceof NamedCharacterClass;
    let bIsJustOneNamedCharacterClass = b.value.length === 1 && b.value[0] instanceof NamedCharacterClass;

    if (aIsJustOneNamedCharacterClass && !bIsJustOneNamedCharacterClass) {
      return -1;
    }

    if (!aIsJustOneNamedCharacterClass && bIsJustOneNamedCharacterClass) {
      return 1;
    }

    if (aIsJustOneNamedCharacterClass && bIsJustOneNamedCharacterClass) {
      let aIndex = namedCharacterClassOrder.indexOf(a.value[0].name);
      let bIndex = namedCharacterClassOrder.indexOf(b.value[0].name);
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

},{}],15:[function(require,module,exports){
module.exports={
  "163.com": {
    "password-rules": "minlength: 6; maxlength: 16;"
  },
  "1800flowers.com": {
    "password-rules": "minlength: 6; required: lower, upper; required: digit;"
  },
  "access.service.gov.uk": {
    "password-rules": "minlength: 10; required: lower; required: upper; required: digit; required: special;"
  },
  "admiral.com": {
    "password-rules": "minlength: 8; required: digit; required: [- !\"#$&'()*+,.:;<=>?@[^_`{|}~]]; allowed: lower, upper;"
  },
  "ae.com": {
    "password-rules": "minlength: 8; maxlength: 25; required: lower; required: upper; required: digit;"
  },
  "aetna.com": {
    "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 2; required: upper; required: digit; allowed: lower, [-_&#@];"
  },
  "airasia.com": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit;"
  },
  "ajisushionline.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; allowed: [ !#$%&*?@];"
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
  "anatel.gov.br": {
    "password-rules": "minlength: 6; maxlength: 15; allowed: lower, upper, digit;"
  },
  "ancestry.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [-!\"#$%&'()*+,./:;<=>?@[^_`{|}~]];"
  },
  "angieslist.com": {
    "password-rules": "minlength: 6; maxlength: 15;"
  },
  "anthem.com": {
    "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 3; required: lower, upper; required: digit; allowed: [!$*?@|];"
  },
  "app.digio.in": {
    "password-rules": "minlength: 8; maxlength: 15;"
  },
  "app.parkmobile.io": {
    "password-rules": "minlength: 8; maxlength: 25; required: lower; required: upper; required: digit; required: [!@#$%^&];"
  },
  "apple.com": {
    "password-rules": "minlength: 8; maxlength: 63; required: lower; required: upper; required: digit; allowed: ascii-printable;"
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
  "autify.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!\"#$%&'()*+,./:;<=>?@[^_`{|}~]];"
  },
  "axa.de": {
    "password-rules": "minlength: 8; maxlength: 65; required: lower; required: upper; required: digit; allowed: [-!\"§$%&/()=?;:_+*'#];"
  },
  "baidu.com": {
    "password-rules": "minlength: 6; maxlength: 14;"
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
  "brighthorizons.com": {
    "password-rules": "minlength: 8; maxlength: 16;"
  },
  "callofduty.com": {
    "password-rules": "minlength: 8; maxlength: 20; max-consecutive: 2; required: lower, upper; required: digit;"
  },
  "capitalone.com": {
    "password-rules": "minlength: 8; maxlength: 32; required: lower, upper; required: digit; allowed: [-_./\\@$*&!#];"
  },
  "cardbenefitservices.com": {
    "password-rules": "minlength: 7; maxlength: 100; required: lower, upper; required: digit;"
  },
  "cb2.com": {
    "password-rules": "minlength: 7; maxlength: 18; required: lower, upper; required: digit;"
  },
  "cecredentialtrust.com": {
    "password-rules": "minlength: 12; required: lower; required: upper; required: digit; required: [!#$%&*@^];"
  },
  "chase.com": {
    "password-rules": "minlength: 8; maxlength: 32; max-consecutive: 2; required: lower, upper; required: digit; required: [!#$%+/=@~];"
  },
  "cigna.co.uk": {
    "password-rules": "minlength: 8; maxlength: 12; required: lower; required: upper; required: digit;"
  },
  "citi.com": {
    "password-rules": "minlength: 6; maxlength: 50; max-consecutive: 2; required: lower, upper; required: digit; allowed: [_!@$]"
  },
  "claimlookup.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [@#$%^&+=!];"
  },
  "claro.com.br": {
    "password-rules": "minlength: 8; required: lower; allowed: upper, digit, [-!@#$%&*_+=<>];"
  },
  "clien.net": {
    "password-rules": "minlength: 5; required: lower, upper; required: digit;"
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
    "password-rules": "minlength: 8; maxlength: 20; required: lower, upper; allowed: digit, [-!#$%&'()*+/:;=?@[^_`{|}~]];"
  },
  "coursera.com": {
    "password-rules": "minlength: 8; maxlength: 72;"
  },
  "cox.com": {
    "password-rules": "minlength: 8; maxlength: 24; required: digit; required: upper,lower; allowed: [!#$%()*@^];"
  },
  "crateandbarrel.com": {
    "password-rules": "minlength: 9; maxlength: 64; required: lower; required: upper; required: digit; required: [!\"#$%&()*,.:<>?@^_{|}];"
  },
  "cvs.com": {
    "password-rules": "minlength: 8; maxlength: 25; required: lower, upper; required: digit; allowed: [!@#$%^&*()];"
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
    "password-rules": "minlength: 8; maxlength: 38; required: lower, upper; required: digit; allowed: [-äüöÄÜÖß!$%&/()=?+#,.:];"
  },
  "dmm.com": {
    "password-rules": "minlength: 4; maxlength: 16; required: lower; required: upper; required: digit;"
  },
  "dowjones.com": {
    "password-rules": "maxlength: 15;"
  },
  "ea.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: special;"
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
  "fedex.com": {
    "password-rules": "minlength: 8; max-consecutive: 3; required: lower; required: upper; required: digit; allowed: [-!@#$%^&*_+=`|(){}[:;,.?]];"
  },
  "fidelity.com": {
    "password-rules": "minlength: 6; maxlength: 20; required: lower; allowed: upper,digit,[!$%'()+,./:;=?@^_|~];"
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
  "getflywheel.com": {
    "password-rules": "minlength: 7; maxlength: 72;"
  },
  "girlscouts.org": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: [$#!];"
  },
  "gmx.net": {
    "password-rules": "minlength: 8; maxlength: 40; allowed: lower, upper, digit, [-<=>~!|()@#{}$%,.?^'&*_+`:;\"[]];"
  },
  "google.com": {
    "password-rules": "minlength: 8; allowed: lower, upper, digit, [-!\"#$%&'()*+,./:;<=>?@[^_{|}~]];"
  },
  "guardiananytime.com": {
    "password-rules": "minlength: 8; maxlength: 50; max-consecutive: 2; required: lower; required: upper; required: digit, [-~!@#$%^&*_+=`|(){}[:;,.?]];"
  },
  "gwl.greatwestlife.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [-!#$%_=+<>];"
  },
  "hangseng.com": {
    "password-rules": "minlength: 8; maxlength: 30; required: lower; required: upper; required: digit;"
  },
  "hawaiianairlines.com": {
    "password-rules": "maxlength: 16;"
  },
  "hertz.com": {
    "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower; required: upper; required: digit; required: [#$%^&!@];"
  },
  "hetzner.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit, special;"
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
    "password-rules": "minlength: 6; maxlength: 20; required: digit; allowed: lower, upper, [@$!#()&^*%];"
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
    "password-rules": "minlength: 6; maxlength: 10; required: lower, upper, digit; allowed: [!\"#$%&()*+:;<=>?@[{}~]];"
  },
  "hyresbostader.se": {
    "password-rules": "minlength: 6; maxlength: 20; required: lower, upper; required: digit;"
  },
  "id.sonyentertainmentnetwork.com": {
    "password-rules": "minlength: 8; maxlength: 30; required: lower, upper; required: digit; allowed: [-!@#^&*=+;:];"
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
  "jordancu-onlinebanking.org": {
    "password-rules": "minlength: 6; maxlength: 32; allowed: upper, lower, digit,[-!\"#$%&'()*+,.:;<=>?@[^_`{|}~]];"
  },
  "keldoc.com": {
    "password-rules": "minlength: 12; required: lower; required: upper; required: digit; required: [!@#$%^&*];"
  },
  "key.harvard.edu": {
    "password-rules": "minlength: 10; maxlength: 100; required: lower; required: upper; required: digit; allowed: [-@_#!&$`%*+()./,;~:{}|?>=<^[']];"
  },
  "kfc.ca": {
    "password-rules": "minlength: 6; maxlength: 15; required: lower; required: upper; required: digit; required: [!@#$%&?*];"
  },
  "klm.com": {
    "password-rules": "minlength: 8; maxlength: 12;"
  },
  "la-z-boy.com": {
    "password-rules": "minlength: 6; maxlength: 15; required: lower, upper; required: digit;"
  },
  "ladwp.com": {
    "password-rules": "minlength: 8; maxlength: 20; required: digit; allowed: lower, upper;"
  },
  "launtel.net.au": {
    "password-rules": "minlength: 8; required: digit; required: digit; allowed: lower, upper;"
  },
  "leetchi.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&()*+,./:;<>?@\"_];"
  },
  "lg.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: [-!#$%&'()*+,.:;=?@[^_{|}~]];"
  },
  "live.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; allowed: [-@_#!&$`%*+()./,;~:{}|?>=<^'[]];"
  },
  "lloydsbank.co.uk": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: digit; allowed: upper;"
  },
  "lowes.com": {
    "password-rules": "minlength: 8; maxlength: 12; required: lower, upper; required: digit;"
  },
  "loyalty.accor.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&=@];"
  },
  "lsacsso.b2clogin.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit, [-!#$%&*?@^_];"
  },
  "lufthansa.com": {
    "password-rules": "minlength: 8; maxlength: 32; required: lower; required: upper; required: digit; required: [!#$%&()*+,./:;<>?@\"_];"
  },
  "macys.com": {
    "password-rules": "minlength: 7; maxlength: 16; allowed: lower, upper, digit, [~!@#$%^&*+`(){}[:;\"'<>?]];"
  },
  "mailbox.org": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; allowed: [-!$\"%&/()=*+#.,;:@?{}[]];"
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
  "metlife.com": {
    "password-rules": "minlength: 6; maxlength: 20;"
  },
  "microsoft.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: special;"
  },
  "minecraft.com": {
    "password-rules": "minlength: 8; required: lower, upper; required: digit; allowed: ascii-printable;"
  },
  "mintmobile.com": {
    "password-rules": "minlength: 8; maxlength: 20; required: lower; required: upper; required: digit; required: special; allowed: [!#$%&()*+:;=@[^_`{}~]];"
  },
  "mlb.com": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; allowed: [!\"#$%&'()*+,./:;<=>?[\\^_`{|}~]];"
  },
  "mpv.tickets.com": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit;"
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
  "mysubaru.com": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; allowed: [!#$%()*+,./:;=?@\\^`~];"
  },
  "naver.com": {
    "password-rules": "minlength: 6; maxlength: 16;"
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
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit, [-~!#$%&_+=|(){}[:;\"’<>,.? ]];"
  },
  "powells.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [\"!@#$%^&*(){}[]];"
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
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [!@#$%^&*()+~{}'\";:<>?];"
  },
  "prestocard.ca": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit,[!\"#$%&'()*+,<>?@];"
  },
  "propelfuels.com": {
    "password-rules": "minlength: 6; maxlength: 16;"
  },
  "qdosstatusreview.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$%&@^];"
  },
  "questdiagnostics.com": {
    "password-rules": "minlength: 8; maxlength: 30; required: upper, lower; required: digit, [!#$%&()*+<>?@^_~];"
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
  "salslimo.com": {
    "password-rules": "minlength: 8; maxlength: 50; required: upper; required: lower; required: digit; required: [!@#$&*];"
  },
  "santahelenasaude.com.br": {
    "password-rules": "minlength: 8; maxlength: 15; required: lower; required: upper; required: digit; required: [-!@#$%&*_+=<>];"
  },
  "santander.de": {
    "password-rules": "minlength: 8; maxlength: 12; required: lower, upper; required: digit; allowed: [-!#$%&'()*,.:;=?^{}];"
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
  "secure.wa.aaa.com": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; allowed: ascii-printable;"
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
  "store.nvidia.com": {
    "password-rules": "minlength: 8; maxlength: 32; required: lower; required: upper; required: digit; required: [-!@#$%^*~:;&><[{}|_+=?]];"
  },
  "store.steampowered.com": {
    "password-rules": "minlength: 6; required: lower; required: upper; required: digit; allowed: [~!@#$%^&*];"
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
  "telekom-dienste.de": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: [#$%&()*+,./<=>?@_{|}~];"
  },
  "thameswater.co.uk": {
    "password-rules": "minlength: 8; maxlength: 16; required: lower; required: upper; required: digit; required: special;"
  },
  "tix.soundrink.com": {
    "password-rules": "minlength: 6; maxlength: 16;"
  },
  "training.confluent.io": {
    "password-rules": "minlength: 6; maxlength: 16; required: lower; required: upper; required: digit; allowed: [!#$%*@^_~];"
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
  "user.ornl.gov": {
    "password-rules": "minlength: 8; maxlength: 30; max-consecutive: 3; required: lower, upper; required: digit; allowed: [!#$%./_];"
  },
  "usps.com": {
    "password-rules": "minlength: 8; maxlength: 50; max-consecutive: 2; required: lower; required: upper; required: digit; allowed: [-!\"#&'()+,./?@];"
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
  "virginmobile.ca": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!#$@];"
  },
  "visa.com": {
    "password-rules": "minlength: 6; maxlength: 32;"
  },
  "visabenefits-auth.axa-assistance.us": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit; required: [!\"#$%&()*,.:<>?@^{|}];"
  },
  "vivo.com.br": {
    "password-rules": "maxlength: 6; max-consecutive: 3; allowed: digit;"
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
  "zara.com": {
    "password-rules": "minlength: 8; required: lower; required: upper; required: digit;"
  },
  "zdf.de": {
    "password-rules": "minlength: 8; required: upper; required: digit; allowed: lower, special;"
  },
  "zoom.us": {
    "password-rules": "minlength: 8; maxlength: 32; max-consecutive: 6; required: lower; required: upper; required: digit;"
  }
}
},{}],16:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.createDevice = createDevice;

var _AndroidInterface = require("./DeviceInterface/AndroidInterface");

var _ExtensionInterface = require("./DeviceInterface/ExtensionInterface");

var _AppleDeviceInterface = require("./DeviceInterface/AppleDeviceInterface");

var _WindowsInterface = require("./DeviceInterface/WindowsInterface");

var _AppleOverlayDeviceInterface = require("./DeviceInterface/AppleOverlayDeviceInterface");

/**
 * @param {import("./senders/sender").Sender} sender
 * @param {TooltipInterface} tooltip
 * @param {GlobalConfig} globalConfig
 * @param {import("@duckduckgo/content-scope-scripts").RuntimeConfiguration} platformConfig
 * @param {import("./settings/settings").Settings} autofillSettings
 * @returns {AndroidInterface|AppleDeviceInterface|AppleOverlayDeviceInterface|ExtensionInterface|WindowsInterface|WindowsOverlayDeviceInterface}
 */
function createDevice(sender, tooltip, globalConfig, platformConfig, autofillSettings) {
  switch (platformConfig.platform) {
    case 'macos':
    case 'ios':
      {
        if (globalConfig.isTopFrame) {
          return new _AppleOverlayDeviceInterface.AppleOverlayDeviceInterface(sender, tooltip, globalConfig, platformConfig, autofillSettings);
        }

        return new _AppleDeviceInterface.AppleDeviceInterface(sender, tooltip, globalConfig, platformConfig, autofillSettings);
      }

    case 'extension':
      return new _ExtensionInterface.ExtensionInterface(sender, tooltip, globalConfig, platformConfig, autofillSettings);

    case 'windows':
      {
        if (globalConfig.isTopFrame) {
          return new _WindowsInterface.WindowsOverlayDeviceInterface(sender, tooltip, globalConfig, platformConfig, autofillSettings);
        } else {
          return new _WindowsInterface.WindowsInterface(sender, tooltip, globalConfig, platformConfig, autofillSettings);
        }
      }

    case 'android':
      return new _AndroidInterface.AndroidInterface(sender, tooltip, globalConfig, platformConfig, autofillSettings);

    case 'unknown':
      throw new Error('unreachable. tooltipHandler platform was "unknown"');
  }

  throw new Error('undefined');
}

},{"./DeviceInterface/AndroidInterface":17,"./DeviceInterface/AppleDeviceInterface":18,"./DeviceInterface/AppleOverlayDeviceInterface":19,"./DeviceInterface/ExtensionInterface":20,"./DeviceInterface/WindowsInterface":22}],17:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.AndroidInterface = void 0;

var _InterfacePrototype = _interopRequireDefault(require("./InterfacePrototype.js"));

var _autofillUtils = require("../autofill-utils");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

class AndroidInterface extends _InterfacePrototype.default {
  async getAlias() {
    const {
      alias
    } = await (0, _autofillUtils.sendAndWaitForAnswer)(() => {
      return window.EmailInterface.showTooltip();
    }, 'getAliasResponse');
    return alias;
  }

  isDeviceSignedIn() {
    // isDeviceSignedIn is only available on DDG domains...
    if (this.globalConfig.isDDGDomain) return window.EmailInterface.isSignedIn() === 'true'; // ...on other domains we assume true because the script wouldn't exist otherwise

    return true;
  }

  async setupAutofill() {
    if (this.isDeviceSignedIn()) {
      const cleanup = this.scanner.init();
      this.addLogoutListener(cleanup);
    }
  }

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

  storeUserData(_ref) {
    let {
      addUserData: {
        token,
        userName,
        cohort
      }
    } = _ref;
    return window.EmailInterface.storeCredentials(token, userName, cohort);
  }

}

exports.AndroidInterface = AndroidInterface;

},{"../autofill-utils":51,"./InterfacePrototype.js":21}],18:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.AppleDeviceInterface = void 0;

var _InterfacePrototype = _interopRequireDefault(require("./InterfacePrototype.js"));

var _autofillUtils = require("../autofill-utils");

var _styles = require("../UI/styles/styles");

var _messages = require("../messages/messages");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

class AppleDeviceInterface extends _InterfacePrototype.default {
  constructor() {
    super(...arguments);

    _defineProperty(this, "initialSetupDelayMs", 300);
  }

  async setupAutofill() {
    if (this.globalConfig.isApp) {
      const response = await this.getAutofillInitData();
      this.storeLocalData(response);
    }

    const signedIn = this.availableInputTypes.email;

    if (signedIn) {
      if (this.globalConfig.isApp) {
        await this.getAddresses();
      }

      this.scanner.forms.forEach(form => form.redecorateAllInputs());
    }

    const cleanup = this.scanner.init();
    this.addLogoutListener(cleanup);
  }

  isDeviceSignedIn() {
    return Boolean(this.availableInputTypes.email);
  }

  getUserData() {
    return this.sender.send((0, _messages.createLegacyMessage)('emailHandlerGetUserData'));
  }

  async getAddresses() {
    if (!this.globalConfig.isApp) return this.getAlias();
    const {
      addresses
    } = await this.sender.send((0, _messages.createLegacyMessage)('emailHandlerGetAddresses'));
    this.storeLocalAddresses(addresses);
    return addresses;
  }

  async refreshAlias() {
    await this.sender.send(new _messages.EmailRefreshAlias()); // On macOS we also update the addresses stored locally

    if (this.globalConfig.isApp) this.getAddresses();
  }

  storeUserData(_ref) {
    let {
      addUserData: {
        token,
        userName,
        cohort
      }
    } = _ref;
    return this.sender.send((0, _messages.createLegacyMessage)('emailHandlerStoreToken', {
      token,
      username: userName,
      cohort
    }));
  }
  /**
   * PM endpoints
   */

  /**
   * Sends credentials to the native layer
   * @param {{username: string, password: string}} credentials
   * @deprecated
   */


  storeCredentials(credentials) {
    return this.sender.send((0, _messages.createLegacyMessage)('pmHandlerStoreCredentials', credentials));
  }
  /**
   * Opens the native UI for managing passwords
   */


  openManagePasswords() {
    return this.sender.send((0, _messages.createLegacyMessage)('pmHandlerOpenManagePasswords'));
  }
  /**
   * Opens the native UI for managing identities
   */


  openManageIdentities() {
    return this.sender.send((0, _messages.createLegacyMessage)('pmHandlerOpenManageIdentities'));
  }
  /**
   * Opens the native UI for managing credit cards
   */


  openManageCreditCards() {
    return this.sender.send((0, _messages.createLegacyMessage)('pmHandlerOpenManageCreditCards'));
  }
  /**
   * Gets a single identity obj once the user requests it
   * @param {Number} id
   * @returns {Promise<{success: IdentityObject|undefined}>}
   */


  getAutofillIdentity(id) {
    const identity = this.getLocalIdentities().find(_ref2 => {
      let {
        id: identityId
      } = _ref2;
      return "".concat(identityId) === "".concat(id);
    });
    return Promise.resolve({
      success: identity
    });
  }
  /**
   * Gets a single complete credit card obj once the user requests it
   * @param {Number} id
   * @returns {APIResponse<CreditCardObject>}
   */


  getAutofillCreditCard(id) {
    return this.sender.send((0, _messages.createLegacyMessage)('pmHandlerGetCreditCard', {
      id
    }));
  } // Used to encode data to send back to the child autofill


  async selectedDetail(detailIn, configType) {
    this.activeFormSelectedDetail(detailIn, configType);
  }

  async getCurrentInputType() {
    const {
      inputType
    } = this.getTopContextData() || {};
    return inputType || 'unknown';
  }

  async getAlias() {
    const {
      alias
    } = await this.sender.send((0, _messages.createLegacyMessage)('emailHandlerGetAlias', {
      requiresUserPermission: !this.globalConfig.isApp,
      shouldConsumeAliasIfProvided: !this.globalConfig.isApp
    }));
    return (0, _autofillUtils.formatDuckAddress)(alias);
  }

  tooltipStyles() {
    return "<style>".concat(_styles.CSS_STYLES, "</style>");
  }

}

exports.AppleDeviceInterface = AppleDeviceInterface;

},{"../UI/styles/styles":49,"../autofill-utils":51,"../messages/messages":56,"./InterfacePrototype.js":21}],19:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.AppleOverlayDeviceInterface = void 0;

var _InterfacePrototype = _interopRequireDefault(require("./InterfacePrototype.js"));

var _autofillUtils = require("../autofill-utils");

var _styles = require("../UI/styles/styles");

var _messages = require("../messages/messages");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * todo(Shane): Decide which data is/isn't needed when apple is inside overlay
 */
class AppleOverlayDeviceInterface extends _InterfacePrototype.default {
  constructor() {
    super(...arguments);

    _defineProperty(this, "stripCredentials", false);
  }

  async setupAutofill() {
    const response = await this.getAutofillInitData();
    this.storeLocalData(response);
    const signedIn = this.availableInputTypes.email;

    if (signedIn) {
      if (this.globalConfig.isApp) {
        await this.getAddresses();
      }
    }

    await this._setupTopFrame();
  }

  isDeviceSignedIn() {
    return Boolean(this.availableInputTypes.email);
  }

  async _setupTopFrame() {
    var _this$tooltip$addList, _this$tooltip, _this$tooltip$createT, _this$tooltip3;

    const topContextData = this.getTopContextData();
    if (!topContextData) throw new Error('unreachable, topContextData should be available'); // Provide dummy values, they're not used
    // todo(Shane): Is this truly not used?

    const getPosition = () => {
      return {
        x: 0,
        y: 0,
        height: 50,
        width: 50
      };
    }; // this is the apple specific part about faking the focus etc.
    // todo(Shane): The fact this 'addListener' could be undefined is a design problem


    (_this$tooltip$addList = (_this$tooltip = this.tooltip).addListener) === null || _this$tooltip$addList === void 0 ? void 0 : _this$tooltip$addList.call(_this$tooltip, () => {
      const handler = event => {
        var _this$tooltip$getActi, _this$tooltip2;

        const tooltip = (_this$tooltip$getActi = (_this$tooltip2 = this.tooltip).getActiveTooltip) === null || _this$tooltip$getActi === void 0 ? void 0 : _this$tooltip$getActi.call(_this$tooltip2);
        tooltip === null || tooltip === void 0 ? void 0 : tooltip.focus(event.detail.x, event.detail.y);
      };

      window.addEventListener('mouseMove', handler);
      return () => {
        window.removeEventListener('mouseMove', handler);
      };
    });
    const tooltip = (_this$tooltip$createT = (_this$tooltip3 = this.tooltip).createTooltip) === null || _this$tooltip$createT === void 0 ? void 0 : _this$tooltip$createT.call(_this$tooltip3, getPosition, topContextData);
    this.setActiveTooltip(tooltip);
  }

  getUserData() {
    return this.sender.send((0, _messages.createLegacyMessage)('emailHandlerGetUserData'));
  }

  async getAddresses() {
    if (!this.globalConfig.isApp) return this.getAlias();
    const {
      addresses
    } = await this.sender.send((0, _messages.createLegacyMessage)('emailHandlerGetAddresses'));
    this.storeLocalAddresses(addresses);
    return addresses;
  }

  async refreshAlias() {
    await this.sender.send((0, _messages.createLegacyMessage)('emailHandlerRefreshAlias')); // On macOS we also update the addresses stored locally

    if (this.globalConfig.isApp) this.getAddresses();
  }

  async setSize(cb) {
    const details = cb(); // todo(Shane): Upgrade to new runtime

    await this.sender.send((0, _messages.createLegacyMessage)('setSize', details));
  }

  async removeTooltip() {
    console.warn('no-op in overlay'); // await this.closeAutofillParent()fig
  }

  storeUserData(_ref) {
    let {
      addUserData: {
        token,
        userName,
        cohort
      }
    } = _ref;
    return this.sender.send((0, _messages.createLegacyMessage)('emailHandlerStoreToken', {
      token,
      username: userName,
      cohort
    }));
  }
  /**
   * PM endpoints
   */

  /**
   * Sends credentials to the native layer
   * @param {{username: string, password: string}} credentials
   * @deprecated
   */


  storeCredentials(credentials) {
    return this.sender.send((0, _messages.createLegacyMessage)('pmHandlerStoreCredentials', credentials));
  }
  /**
   * Opens the native UI for managing passwords
   */


  openManagePasswords() {
    return this.sender.send((0, _messages.createLegacyMessage)('pmHandlerOpenManagePasswords'));
  }
  /**
   * Gets a single identity obj once the user requests it
   * @param {Number} id
   * @returns {Promise<{success: IdentityObject|undefined}>}
   */


  getAutofillIdentity(id) {
    const identity = this.getLocalIdentities().find(_ref2 => {
      let {
        id: identityId
      } = _ref2;
      return "".concat(identityId) === "".concat(id);
    });
    return Promise.resolve({
      success: identity
    });
  }
  /**
   * Gets a single complete credit card obj once the user requests it
   * @param {Number} id
   * @returns {APIResponse<CreditCardObject>}
   */


  getAutofillCreditCard(id) {
    return this.sender.send((0, _messages.createLegacyMessage)('pmHandlerGetCreditCard', {
      id
    }));
  } // Used to encode data to send back to the child autofill


  async selectedDetail(detailIn, configType) {
    let detailsEntries = Object.entries(detailIn).map(_ref3 => {
      let [key, value] = _ref3;
      return [key, String(value)];
    });
    const data = Object.fromEntries(detailsEntries); // todo(Shane): Migrate

    await this.sender.send((0, _messages.createLegacyMessage)('selectedDetail', {
      data,
      configType
    }));
  }

  async getCurrentInputType() {
    const {
      inputType
    } = this.getTopContextData() || {};
    return inputType || 'unknown';
  }

  async getAlias() {
    const {
      alias
    } = await this.sender.send((0, _messages.createLegacyMessage)('emailHandlerGetAlias', {
      requiresUserPermission: !this.globalConfig.isApp,
      shouldConsumeAliasIfProvided: !this.globalConfig.isApp
    }));
    return (0, _autofillUtils.formatDuckAddress)(alias);
  }

  tooltipStyles() {
    return "<style>".concat(_styles.CSS_STYLES, "</style>");
  }

  tooltipWrapperClass() {
    return 'top-autofill';
  }

  tooltipPositionClass(_top, _left) {
    return '.wrapper {transform: none; }';
  }

  setupSizeListener(cb) {
    cb();
  }

}

exports.AppleOverlayDeviceInterface = AppleOverlayDeviceInterface;

},{"../UI/styles/styles":49,"../autofill-utils":51,"../messages/messages":56,"./InterfacePrototype.js":21}],20:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.ExtensionInterface = void 0;

var _InterfacePrototype = _interopRequireDefault(require("./InterfacePrototype.js"));

var _autofillUtils = require("../autofill-utils");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

class ExtensionInterface extends _InterfacePrototype.default {
  isDeviceSignedIn() {
    return this.hasLocalAddresses;
  }

  async setupAutofill() {
    await this._addDeviceListeners();
    return this.getAddresses().then(_addresses => {
      if (this.hasLocalAddresses) {
        // todo(Shane): Should we re-evaluate input types now?
        this.availableInputTypes = { ...this.availableInputTypes,
          email: true
        };
        const cleanup = this.scanner.init(); // todo(Shane): Should we re-evaluate input types now?

        this.addLogoutListener(() => {
          cleanup();
          this.availableInputTypes = { ...this.availableInputTypes,
            email: false
          };
        });
      }
    });
  }

  getAddresses() {
    return new Promise(resolve => chrome.runtime.sendMessage({
      getAddresses: true
    }, data => {
      this.storeLocalAddresses(data);
      return resolve(data);
    }));
  }

  getUserData() {
    return new Promise(resolve => chrome.runtime.sendMessage({
      getUserData: true
    }, data => resolve(data)));
  }

  refreshAlias() {
    return chrome.runtime.sendMessage({
      refreshAlias: true
    }, addresses => this.storeLocalAddresses(addresses));
  }

  async trySigningIn() {
    if (this.globalConfig.isDDGDomain) {
      const data = await (0, _autofillUtils.sendAndWaitForAnswer)(_autofillUtils.SIGN_IN_MSG, 'addUserData');
      this.storeUserData(data);
    }
  }

  storeUserData(data) {
    return chrome.runtime.sendMessage(data);
  }

  _addDeviceListeners() {
    // Add contextual menu listeners
    let activeEl = null;
    document.addEventListener('contextmenu', e => {
      activeEl = e.target;
    });
    chrome.runtime.onMessage.addListener((message, sender) => {
      if (sender.id !== chrome.runtime.id) return;

      switch (message.type) {
        case 'ddgUserReady':
          this.setupAutofill().then(() => {
            this.setupSettingsPage({
              shouldLog: true
            });
          });
          break;

        case 'contextualAutofill':
          (0, _autofillUtils.setValue)(activeEl, (0, _autofillUtils.formatDuckAddress)(message.alias), this.globalConfig);
          activeEl.classList.add('ddg-autofilled');
          this.refreshAlias(); // If the user changes the alias, remove the decoration

          activeEl.addEventListener('input', e => e.target.classList.remove('ddg-autofilled'), {
            once: true
          });
          break;

        default:
          break;
      }
    });
  }

  addLogoutListener(handler) {
    // Cleanup on logout events
    chrome.runtime.onMessage.addListener((message, sender) => {
      if (sender.id === chrome.runtime.id && message.type === 'logout') {
        handler();
      }
    });
  }
  /** @override */


  tooltipStyles() {
    return "<link rel=\"stylesheet\" href=\"".concat(chrome.runtime.getURL('public/css/autofill.css'), "\" crossorigin=\"anonymous\">");
  }

}

exports.ExtensionInterface = ExtensionInterface;

},{"../autofill-utils":51,"./InterfacePrototype.js":21}],21:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;

var _autofillUtils = require("../autofill-utils");

var _matching = require("../Form/matching");

var _formatters = require("../Form/formatters");

var _listenForFormSubmission = _interopRequireDefault(require("../Form/listenForFormSubmission"));

var _Credentials = require("../InputTypes/Credentials");

var _PasswordGenerator = require("../PasswordGenerator");

var _Scanner = require("../Scanner");

var _config = require("../config");

var _settings = require("../settings/settings");

var _contentScopeScripts = require("@duckduckgo/content-scope-scripts");

var _WebTooltip = require("../UI/WebTooltip");

var _inputTypes = require("../InputTypes/input-types");

var messages = _interopRequireWildcard(require("../messages/messages"));

var _sender = require("../senders/sender");

function _getRequireWildcardCache(nodeInterop) { if (typeof WeakMap !== "function") return null; var cacheBabelInterop = new WeakMap(); var cacheNodeInterop = new WeakMap(); return (_getRequireWildcardCache = function (nodeInterop) { return nodeInterop ? cacheNodeInterop : cacheBabelInterop; })(nodeInterop); }

function _interopRequireWildcard(obj, nodeInterop) { if (!nodeInterop && obj && obj.__esModule) { return obj; } if (obj === null || typeof obj !== "object" && typeof obj !== "function") { return { default: obj }; } var cache = _getRequireWildcardCache(nodeInterop); if (cache && cache.has(obj)) { return cache.get(obj); } var newObj = {}; var hasPropertyDescriptor = Object.defineProperty && Object.getOwnPropertyDescriptor; for (var key in obj) { if (key !== "default" && Object.prototype.hasOwnProperty.call(obj, key)) { var desc = hasPropertyDescriptor ? Object.getOwnPropertyDescriptor(obj, key) : null; if (desc && (desc.get || desc.set)) { Object.defineProperty(newObj, key, desc); } else { newObj[key] = obj[key]; } } } newObj.default = obj; if (cache) { cache.set(obj, newObj); } return newObj; }

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

var _addresses = /*#__PURE__*/new WeakMap();

var _data2 = /*#__PURE__*/new WeakMap();

/**
 * @implements {GlobalConfigImpl}
 * @implements {FormExtensionPoints}
 * @implements {DeviceExtensionPoints}
 */
class InterfacePrototype {
  /** @type {import("../Form/Form").Form | null} */

  /** @type {import("../UI/Tooltip.js").Tooltip | null} */

  /** @type {number} */

  /** @type {PasswordGenerator} */

  /** @type {{privateAddress: string, personalAddress: string}} */

  /** @type {GlobalConfig} */

  /** @type {import("@duckduckgo/content-scope-scripts").RuntimeConfiguration} */

  /** @type {import("../settings/settings").Settings} */

  /** @type {AvailableInputTypes} */

  /** @type {import('../Scanner').Scanner} */

  /** @type {TooltipInterface} */

  /** @type {import("../senders/sender").Sender} */

  /**
   * @param {import("../senders/sender").Sender} sender
   * @param {TooltipInterface} tooltip
   * @param {GlobalConfig} globalConfig
   * @param {import("@duckduckgo/content-scope-scripts").RuntimeConfiguration} platformConfig
   * @param {import("../settings/settings").Settings} autofillSettings
   */
  constructor(sender, tooltip, globalConfig, platformConfig, autofillSettings) {
    _defineProperty(this, "attempts", 0);

    _defineProperty(this, "currentAttached", null);

    _defineProperty(this, "currentTooltip", null);

    _defineProperty(this, "stripCredentials", true);

    _defineProperty(this, "initialSetupDelayMs", 0);

    _defineProperty(this, "passwordGenerator", new _PasswordGenerator.PasswordGenerator());

    _classPrivateFieldInitSpec(this, _addresses, {
      writable: true,
      value: {
        privateAddress: '',
        personalAddress: ''
      }
    });

    _defineProperty(this, "globalConfig", void 0);

    _defineProperty(this, "runtimeConfiguration", void 0);

    _defineProperty(this, "autofillSettings", void 0);

    _defineProperty(this, "availableInputTypes", {});

    _defineProperty(this, "scanner", void 0);

    _defineProperty(this, "tooltip", void 0);

    _defineProperty(this, "sender", void 0);

    _classPrivateFieldInitSpec(this, _data2, {
      writable: true,
      value: {
        credentials: [],
        creditCards: [],
        identities: [],
        topContextData: undefined
      }
    });

    this.globalConfig = globalConfig;
    this.tooltip = tooltip;
    this.runtimeConfiguration = platformConfig;
    this.autofillSettings = autofillSettings;
    this.sender = sender;
    this.scanner = (0, _Scanner.createScanner)(this, {
      initialDelay: this.initialSetupDelayMs
    });
  }

  get hasLocalAddresses() {
    var _classPrivateFieldGet2, _classPrivateFieldGet3;

    return !!((_classPrivateFieldGet2 = _classPrivateFieldGet(this, _addresses)) !== null && _classPrivateFieldGet2 !== void 0 && _classPrivateFieldGet2.privateAddress && (_classPrivateFieldGet3 = _classPrivateFieldGet(this, _addresses)) !== null && _classPrivateFieldGet3 !== void 0 && _classPrivateFieldGet3.personalAddress);
  }

  getLocalAddresses() {
    return _classPrivateFieldGet(this, _addresses);
  }

  storeLocalAddresses(addresses) {
    _classPrivateFieldSet(this, _addresses, addresses); // When we get new duck addresses, add them to the identities list


    const identities = this.getLocalIdentities();
    const privateAddressIdentity = identities.find(_ref => {
      let {
        id
      } = _ref;
      return id === 'privateAddress';
    }); // If we had previously stored them, just update the private address

    if (privateAddressIdentity) {
      privateAddressIdentity.emailAddress = (0, _autofillUtils.formatDuckAddress)(addresses.privateAddress);
    } else {
      // Otherwise, add both addresses
      _classPrivateFieldGet(this, _data2).identities = this.addDuckAddressesToIdentities(identities);
    }
  }
  /** @type { PMData } */


  /**
   * @returns {Promise<import('../Form/matching').SupportedTypes>}
   */
  async getCurrentInputType() {
    throw new Error('Not implemented');
  }

  addDuckAddressesToIdentities(identities) {
    if (!this.hasLocalAddresses) return identities;
    const newIdentities = [];
    let {
      privateAddress,
      personalAddress
    } = this.getLocalAddresses();
    privateAddress = (0, _autofillUtils.formatDuckAddress)(privateAddress);
    personalAddress = (0, _autofillUtils.formatDuckAddress)(personalAddress); // Get the duck addresses in identities

    const duckEmailsInIdentities = identities.reduce((duckEmails, _ref2) => {
      let {
        emailAddress: email
      } = _ref2;
      return email !== null && email !== void 0 && email.includes(_autofillUtils.ADDRESS_DOMAIN) ? duckEmails.concat(email) : duckEmails;
    }, []); // Only add the personal duck address to identities if the user hasn't
    // already manually added it

    if (!duckEmailsInIdentities.includes(personalAddress)) {
      newIdentities.push({
        id: 'personalAddress',
        emailAddress: personalAddress,
        title: 'Blocks email trackers'
      });
    }

    newIdentities.push({
      id: 'privateAddress',
      emailAddress: privateAddress,
      title: 'Blocks email trackers and hides your address'
    });
    return [...identities, ...newIdentities];
  }
  /**
   * Stores init data coming from the tooltipHandler
   * @param { InboundPMData } data
   */


  storeLocalData(data) {
    if (this.stripCredentials) {
      data.credentials.forEach(cred => delete cred.password);
      data.creditCards.forEach(cc => delete cc.cardNumber && delete cc.cardSecurityCode);
    } // Store the full name as a separate field to simplify autocomplete


    const updatedIdentities = data.identities.map(identity => ({ ...identity,
      fullName: (0, _formatters.formatFullName)(identity)
    })); // Add addresses

    _classPrivateFieldGet(this, _data2).identities = this.addDuckAddressesToIdentities(updatedIdentities);
    _classPrivateFieldGet(this, _data2).creditCards = data.creditCards;
    _classPrivateFieldGet(this, _data2).credentials = data.credentials; // Top autofill only

    if (data.serializedInputContext) {
      try {
        _classPrivateFieldGet(this, _data2).topContextData = JSON.parse(data.serializedInputContext);
      } catch (e) {
        console.error(e);
        this.removeTooltip();
      }
    }
  }

  getTopContextData() {
    return _classPrivateFieldGet(this, _data2).topContextData;
  }

  get hasLocalCredentials() {
    return _classPrivateFieldGet(this, _data2).credentials.length > 0;
  }

  getLocalCredentials() {
    return _classPrivateFieldGet(this, _data2).credentials.map(cred => {
      const {
        password,
        ...rest
      } = cred;
      return rest;
    });
  }

  get hasLocalIdentities() {
    return _classPrivateFieldGet(this, _data2).identities.length > 0;
  }

  getLocalIdentities() {
    return _classPrivateFieldGet(this, _data2).identities;
  }

  get hasLocalCreditCards() {
    return _classPrivateFieldGet(this, _data2).creditCards.length > 0;
  }
  /** @return {CreditCardObject[]} */


  getLocalCreditCards() {
    return _classPrivateFieldGet(this, _data2).creditCards;
  }

  async init() {
    if (document.readyState === 'complete') {
      this._startInit().catch(e => console.error('init error', e));
    } else {
      window.addEventListener('load', () => {
        this._startInit().catch(e => console.error('init error', e));
      });
    }
  }

  async _startInit() {
    if (this.autofillSettings.featureToggles.credentials_saving) {
      (0, _listenForFormSubmission.default)(this.scanner.forms);
    }

    await this.refreshAvailableInputTypes();
    await this.setupAutofill();
    await this.setupSettingsPage();
  }
  /**
   * @param {IdentityObject|CreditCardObject|CredentialsObject|{email:string, id: string}} data
   * @param {string} type
   */


  async selectedDetail(data, type) {
    this.activeFormSelectedDetail(data, type);
  }
  /**
   * @param {IdentityObject|CreditCardObject|CredentialsObject|{email:string, id: string}} data
   * @param {string} type
   */


  activeFormSelectedDetail(data, type) {
    const form = this.currentAttached;

    if (!form) {
      return;
    }

    if (data.id === 'privateAddress') {
      this.refreshAlias();
    }

    if (type === 'email' && 'email' in data) {
      form.autofillEmail(data.email);
    } else {
      form.autofillData(data, type);
    }

    this.removeTooltip();
  }
  /**
   * Before the DataWebTooltip opens, we collect the data based on the config.type
   * @param {InputTypeConfigs} config
   * @param {import('../Form/matching').SupportedTypes} inputType
   * @param {TopContextData} [data]
   * @returns {(CredentialsObject|CreditCardObject|IdentityObject)[]}
   */


  dataForAutofill(config, inputType, data) {
    const subtype = (0, _matching.getSubtypeFromType)(inputType);

    if (config.type === 'identities') {
      return this.getLocalIdentities().filter(identity => !!identity[subtype]);
    }

    if (config.type === 'creditCards') {
      return this.getLocalCreditCards();
    }

    if (config.type === 'credentials') {
      if (data) {
        if (Array.isArray(data.credentials) && data.credentials.length > 0) {
          return data.credentials;
        } else {
          return this.getLocalCredentials();
        }
      }
    }

    return [];
  }
  /**
   * @param {import("../Form/Form").Form} form
   * @deprecated
   */


  getEmailAlias(form) {
    this.getAlias().then(alias => {
      var _form$activeInput;

      if (alias) form.autofillEmail(alias);else (_form$activeInput = form.activeInput) === null || _form$activeInput === void 0 ? void 0 : _form$activeInput.focus();
    });
  }
  /**
   * @param {import("../Form/Form").Form} form
   * @param {HTMLInputElement} input
   * @param {{ (): { x: number; y: number; height: number; width: number; } }} getPosition
   * @param {{ x: number; y: number; } | null} click
   */


  attachTooltip(form, input, getPosition, click) {
    form.activeInput = input;
    this.currentAttached = form;
    const inputType = (0, _matching.getInputType)(input);

    if (this.globalConfig.isMobileApp && inputType === 'identities.emailAddress') {
      return this.getEmailAlias(form);
    }
    /** @type {TopContextData} */


    const topContextData = {
      inputType
    }; // Allow features to append/change top context data
    // for example, generated passwords may get appended here

    const processedTopContext = this.preAttachTooltip(topContextData, input, form);
    this.tooltip.attach({
      input,
      form,
      click,
      getPosition,
      topContextData: processedTopContext,
      device: this
    });
  }
  /**
   * If the tooltipHandler was capable of generating password, and it
   * previously did so for the form in question, then offer to
   * save the credentials
   *
   * @param {{ formElement?: HTMLElement; }} options
   */


  shouldPromptToStoreCredentials(options) {
    if (!options.formElement) return false;
    if (!this.autofillSettings.featureToggles.password_generation) return false; // if we previously generated a password, allow it to be saved

    if (this.passwordGenerator.generated) {
      return true;
    }

    return false;
  }
  /**
   * When an item was selected, we then call back to the tooltipHandler
   * to fetch the full suite of data needed to complete the autofill
   *
   * @param {InputTypeConfigs} config
   * @param {(CreditCardObject|IdentityObject|CredentialsObject)[]} items
   * @param {string|number} id
   */


  onSelect(config, items, id) {
    id = String(id);
    const matchingData = items.find(item => String(item.id) === id);
    if (!matchingData) throw new Error('unreachable (fatal)');

    const dataPromise = (() => {
      switch (config.type) {
        case 'creditCards':
          return this.getAutofillCreditCard(id);

        case 'identities':
          return this.getAutofillIdentity(id);

        case 'credentials':
          {
            if (_Credentials.AUTOGENERATED_KEY in matchingData) {
              return Promise.resolve({
                success: matchingData
              });
            }

            return this.getAutofillCredentials(id);
          }

        default:
          throw new Error('unreachable!');
      }
    })(); // wait for the data back from the tooltipHandler


    dataPromise.then(response => {
      if (response.success) {
        return this.selectedDetail(response.success, config.type);
      } else if (response) {
        return this.selectedDetail(response, config.type);
      } else {
        return Promise.reject(new Error('none-success response'));
      }
    }).catch(e => {
      console.error(e);
      return this.removeTooltip();
    });
  }

  getActiveTooltip() {
    var _this$tooltip$getActi, _this$tooltip;

    return (_this$tooltip$getActi = (_this$tooltip = this.tooltip).getActiveTooltip) === null || _this$tooltip$getActi === void 0 ? void 0 : _this$tooltip$getActi.call(_this$tooltip);
  }

  setActiveTooltip(tooltip) {
    var _this$tooltip$setActi, _this$tooltip2;

    (_this$tooltip$setActi = (_this$tooltip2 = this.tooltip).setActiveTooltip) === null || _this$tooltip$setActi === void 0 ? void 0 : _this$tooltip$setActi.call(_this$tooltip2, tooltip);
  }

  async setupSettingsPage() {
    let {
      shouldLog
    } = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {
      shouldLog: false
    };

    if (!this.globalConfig.isDDGDomain) {
      return;
    }

    (0, _autofillUtils.notifyWebApp)({
      isApp: this.globalConfig.isApp
    });

    if (this.isDeviceSignedIn()) {
      let userData;

      try {
        userData = await this.getUserData();
      } catch (e) {}

      const hasUserData = userData && !userData.error && Object.entries(userData).length > 0;
      (0, _autofillUtils.notifyWebApp)({
        deviceSignedIn: {
          value: true,
          shouldLog,
          userData: hasUserData ? userData : undefined
        }
      });
    } else {
      // todo(Shane): Handle this case of signing i
      this.trySigningIn();
    }
  }
  /**
   * Update availableInputTypes on the fly, this could happen when signing in for email, as an example
   * @returns {Promise<void>}
   */


  async refreshAvailableInputTypes() {
    const runtimeAvailableInputTypes = await this.getAvailableInputTypes();
    const inputTypes = (0, _inputTypes.featureToggleAwareInputTypes)(runtimeAvailableInputTypes, this.autofillSettings.featureToggles);
    this.availableInputTypes = inputTypes;
  }

  async setupAutofill() {}
  /** @returns {Promise<EmailAddresses>} */


  async getAddresses() {
    throw new Error('unimplemented');
  }
  /** @returns {Promise<null|Record<any,any>>} */


  getUserData() {
    return Promise.resolve(null);
  }

  refreshAlias() {}

  async trySigningIn() {
    if (this.globalConfig.isDDGDomain) {
      if (this.attempts < 10) {
        this.attempts++;
        const data = await (0, _autofillUtils.sendAndWaitForAnswer)(_autofillUtils.SIGN_IN_MSG, 'addUserData'); // This call doesn't send a response, so we can't know if it succeeded

        this.storeUserData(data);
        await this.refreshAvailableInputTypes();
        await this.setupAutofill();
        await this.setupSettingsPage({
          shouldLog: true
        });
      } else {
        console.warn('max attempts reached, bailing');
      }
    }
  }

  storeUserData(_data) {}
  /** @param {() => void} _fn */


  addLogoutListener(_fn) {}

  isDeviceSignedIn() {
    return false;
  }
  /**
   * @returns {Promise<null|string>}
   */


  async getAlias() {
    return null;
  } // PM endpoints


  storeCredentials(_opts) {}

  getAccounts() {}
  /**
   * Gets credentials ready for autofill
   * @param {number|string} id - the credential id
   * @returns {Promise<CredentialsObject>}
   */


  async getAutofillCredentials(id) {
    return this.sender.send(new messages.GetAutofillCredentialsMsg({
      id: String(id)
    }));
  }
  /**
   * @public
   * @returns {Promise<AvailableInputTypes>}
   */


  async getAvailableInputTypes() {
    return this.sender.send(new messages.GetAvailableInputTypes());
  }
  /**
   * @public
   * @param {GetAutofillDataRequest} input
   * @return {Promise<IdentityObject|CredentialsObject|CreditCardObject>}
   */


  async getAutofillData(input) {
    return this.sender.send(new messages.GetAutofillData(input));
  }
  /**
   * @returns {Promise<InboundPMData>}
   */


  async getAutofillInitData() {
    return this.sender.send(new messages.GetAutofillInitData());
  }
  /**
   * Sends form data to the native layer
   * @param {DataStorageObject} data
   */


  storeFormData(data) {
    return this.sender.send(new messages.StoreFormData(data));
  }
  /**
   * @param {ShowAutofillParentRequest} parentArgs
   * @returns {Promise<void>}
   */


  async showAutofillParent(parentArgs) {
    await this.sender.send(new messages.ShowAutofillParent(parentArgs));
  }
  /**
   * todo(Shane): Schema for this?
   * @deprecated This was a port from the macOS implementation so the API may not be suitable for all
   * @returns {Promise<any>}
   */


  async getSelectedCredentials() {
    return this.sender.send(new messages.GetSelectedCredentials(null));
  }
  /**
   * @returns {Promise<any>}
   */


  async closeAutofillParent() {
    await this.sender.send(new messages.CloseAutofillParent(null));
  }
  /** @returns {APIResponse<CreditCardObject>} */


  async getAutofillCreditCard(_id) {
    throw new Error('unimplemented');
  }
  /** @returns {Promise<{success: IdentityObject|undefined}>} */


  async getAutofillIdentity(_id) {
    throw new Error('unimplemented');
  }

  openManagePasswords() {}

  setSize(_cb) {// noop
  } // todo(Shane): remove all these things from the InterfacePrototype

  /** @returns {string} */


  tooltipStyles() {
    return "";
  }

  tooltipWrapperClass() {
    return '';
  }

  tooltipPositionClass(top, left) {
    return ".wrapper {transform: translate(".concat(left, "px, ").concat(top, "px);}");
  }

  setupSizeListener(_cb) {// no-op
  }

  static default() {
    const config = new _contentScopeScripts.RuntimeConfiguration();
    const globalConfig = (0, _config.createGlobalConfig)();
    const sender = new _sender.NullSender();
    const tooltip = new _WebTooltip.WebTooltip({
      tooltipKind: 'modern'
    });
    return new InterfacePrototype(sender, tooltip, globalConfig, config, _settings.Settings.default());
  }

  removeTooltip() {
    var _this$tooltip$removeT, _this$tooltip3;

    return (_this$tooltip$removeT = (_this$tooltip3 = this.tooltip).removeTooltip) === null || _this$tooltip$removeT === void 0 ? void 0 : _this$tooltip$removeT.call(_this$tooltip3);
  }

  isTestMode() {
    return this.globalConfig.isDDGTestMode;
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
   * @param {{isSignup: boolean|null}} form
   */


  preAttachTooltip(topContextData, input, form) {
    // A list of checks to determine if we need to generate a password
    const checks = [topContextData.inputType === 'credentials.password', this.autofillSettings.featureToggles.password_generation, form.isSignup]; // if all checks pass, generate and save a password

    if (checks.every(Boolean)) {
      const password = this.passwordGenerator.generate({
        input: input.getAttribute('passwordrules'),
        domain: window.location.hostname
      }); // append the new credential to the topContextData so that the top autofill can display it

      topContextData.credentials = [(0, _Credentials.fromPassword)(password)];
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
   * @param {DataStorageObject} formValues
   */


  postAutofill(data, formValues) {
    if (_Credentials.AUTOGENERATED_KEY in data && 'password' in data) {
      var _formValues$credentia;

      if (((_formValues$credentia = formValues.credentials) === null || _formValues$credentia === void 0 ? void 0 : _formValues$credentia.password) === data.password) {
        const withAutoGeneratedFlag = (0, _Credentials.appendGeneratedId)(formValues, data.password);
        this.storeFormData(withAutoGeneratedFlag);
      }
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
    const checks = [form.shouldPromptToStoreData, this.passwordGenerator.generated];

    if (checks.some(Boolean)) {
      const withAutoGeneratedFlag = (0, _Credentials.appendGeneratedId)(values, this.passwordGenerator.password);
      this.storeFormData(withAutoGeneratedFlag);
    }
  }

}

var _default = InterfacePrototype;
exports.default = _default;

},{"../Form/formatters":26,"../Form/listenForFormSubmission":30,"../Form/matching":33,"../InputTypes/Credentials":36,"../InputTypes/input-types":39,"../PasswordGenerator":40,"../Scanner":41,"../UI/WebTooltip":47,"../autofill-utils":51,"../config":53,"../messages/messages":56,"../senders/sender":69,"../settings/settings":71,"@duckduckgo/content-scope-scripts":1}],22:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.WindowsOverlayDeviceInterface = exports.WindowsInterface = void 0;

var _InterfacePrototype = _interopRequireDefault(require("./InterfacePrototype"));

var _styles = require("../UI/styles/styles");

var _messages = require("../messages/messages");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

class WindowsInterface extends _InterfacePrototype.default {
  async setupAutofill() {
    // todo(Shane): is this is the correct place to determine this?
    const initData = await this.getAutofillInitData();
    this.storeLocalData(initData);
    const cleanup = this.scanner.init();
    this.addLogoutListener(cleanup);
  }

  tooltipStyles() {
    return "<style>".concat(_styles.CSS_STYLES, "</style>");
  }

}

exports.WindowsInterface = WindowsInterface;

/**
 * todo(Shane): Decide which data is/isn't needed when apple is inside overlay
 */
class WindowsOverlayDeviceInterface extends _InterfacePrototype.default {
  constructor() {
    super(...arguments);

    _defineProperty(this, "stripCredentials", false);
  }

  async setupAutofill() {
    const response = await this.getAutofillInitData();
    this.storeLocalData(response);
    await this._setupTopFrame();
  }

  async _setupTopFrame() {
    var _this$tooltip$addList, _this$tooltip, _this$tooltip$createT, _this$tooltip3;

    const topContextData = this.getTopContextData();
    if (!topContextData) throw new Error('unreachable, topContextData should be available'); // Provide dummy values, they're not used
    // todo(Shane): Is this truly not used?

    const getPosition = () => {
      return {
        x: 0,
        y: 0,
        height: 50,
        width: 50
      };
    }; // this is the apple specific part about faking the focus etc.
    // todo(Shane): The fact this 'addListener' could be undefined is a design problem


    (_this$tooltip$addList = (_this$tooltip = this.tooltip).addListener) === null || _this$tooltip$addList === void 0 ? void 0 : _this$tooltip$addList.call(_this$tooltip, () => {
      const handler = event => {
        var _this$tooltip$getActi, _this$tooltip2;

        const tooltip = (_this$tooltip$getActi = (_this$tooltip2 = this.tooltip).getActiveTooltip) === null || _this$tooltip$getActi === void 0 ? void 0 : _this$tooltip$getActi.call(_this$tooltip2);
        tooltip === null || tooltip === void 0 ? void 0 : tooltip.focus(event.detail.x, event.detail.y);
      };

      window.addEventListener('mouseMove', handler);
      return () => {
        window.removeEventListener('mouseMove', handler);
      };
    });
    const tooltip = (_this$tooltip$createT = (_this$tooltip3 = this.tooltip).createTooltip) === null || _this$tooltip$createT === void 0 ? void 0 : _this$tooltip$createT.call(_this$tooltip3, getPosition, topContextData);
    this.setActiveTooltip(tooltip);
  }

  async setSize(_cb) {// const details = cb()
    // todo(Shane): Upgrade to new runtime
    // await this.sender.send(createLegacyMessage('setSize', details))
  }

  async removeTooltip() {
    console.warn('no-op in overlay');
  } // Used to encode data to send back to the child autofill


  async selectedDetail(detailIn, configType) {
    let detailsEntries = Object.entries(detailIn).map(_ref => {
      let [key, value] = _ref;
      return [key, String(value)];
    });
    const data = Object.fromEntries(detailsEntries);
    await this.sender.send(new _messages.SelectedDetailMessage({
      data,
      configType
    }));
  }

  async getCurrentInputType() {
    const {
      inputType
    } = this.getTopContextData() || {};
    return inputType || 'unknown';
  }

  tooltipStyles() {
    return "<style>".concat(_styles.CSS_STYLES, "</style>");
  }

  tooltipWrapperClass() {
    return 'top-autofill';
  }

  tooltipPositionClass(_top, _left) {
    return '.wrapper {transform: none; }';
  }

  setupSizeListener(cb) {
    cb();
  }

}

exports.WindowsOverlayDeviceInterface = WindowsOverlayDeviceInterface;

},{"../UI/styles/styles":49,"../messages/messages":56,"./InterfacePrototype":21}],23:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.Form = void 0;

var _FormAnalyzer = _interopRequireDefault(require("./FormAnalyzer"));

var _autofillUtils = require("../autofill-utils");

var _matching = require("./matching");

var _inputStyles = require("./inputStyles");

var _inputTypeConfig = require("./inputTypeConfig.js");

var _formatters = require("./formatters");

var _constants = require("../constants");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

const {
  ATTR_AUTOFILL
} = _constants.constants;

class Form {
  /** @type {import('../Form/matching').Matching} */

  /** @type {HTMLElement} */

  /** @type {HTMLInputElement | null} */

  /** @type {boolean | null} */

  /**
   * @param {HTMLElement} form
   * @param {HTMLInputElement|HTMLSelectElement} input
   * @param {import('../DeviceInterface/InterfacePrototype').default} deviceInterface
   * @param {import('../Form/matching').Matching} [matching]
   */
  constructor(form, input, deviceInterface, matching) {
    _defineProperty(this, "matching", void 0);

    _defineProperty(this, "form", void 0);

    _defineProperty(this, "activeInput", void 0);

    _defineProperty(this, "isSignup", void 0);

    this.form = form;
    this.matching = matching || (0, _matching.createMatching)();
    this.formAnalyzer = new _FormAnalyzer.default(form, input, matching);
    this.isLogin = this.formAnalyzer.isLogin;
    this.isSignup = this.formAnalyzer.isSignup;
    this.device = deviceInterface;
    /** @type Record<'all' | SupportedMainTypes, Set> */

    this.inputs = {
      all: new Set(),
      credentials: new Set(),
      creditCards: new Set(),
      identities: new Set(),
      unknown: new Set()
    };
    this.touched = new Set();
    this.listeners = new Set();
    this.activeInput = null; // We set this to true to skip event listeners while we're autofilling

    this.isAutofilling = false;
    this.handlerExecuted = false;
    this.shouldPromptToStoreData = true;
    /**
     * @type {IntersectionObserver | null}
     */

    this.intObs = new IntersectionObserver(entries => {
      for (const entry of entries) {
        if (!entry.isIntersecting) this.removeTooltip();
      }
    }); // This ensures we fire the handler again if the form is changed

    this.addListener(form, 'input', () => {
      if (!this.isAutofilling) {
        this.handlerExecuted = false;
        this.shouldPromptToStoreData = true;
      }
    });
    this.categorizeInputs();
  }
  /**
   * Checks if the form element contains the activeElement
   * @return {boolean}
   */


  hasFocus() {
    return this.form.contains(document.activeElement);
  }
  /**
   * Checks that the form element doesn't contain an invalid field
   * @return {boolean}
   */


  isValid() {
    if (this.form instanceof HTMLFormElement) {
      return this.form.checkValidity();
    } // If the container is not a valid form, we must check fields individually


    let validity = true;
    this.execOnInputs(input => {
      if (input.validity && !input.validity.valid) validity = false;
    }, 'all', false);
    return validity;
  }

  submitHandler() {
    var _this$device$postSubm, _this$device;

    if (this.handlerExecuted) return;
    if (!this.isValid()) return;
    const values = this.getValues();
    (_this$device$postSubm = (_this$device = this.device).postSubmit) === null || _this$device$postSubm === void 0 ? void 0 : _this$device$postSubm.call(_this$device, values, this); // mark this form as being handled

    this.handlerExecuted = true;
  }
  /** @return {DataStorageObject} */


  getValues() {
    const formValues = [...this.inputs.credentials, ...this.inputs.identities, ...this.inputs.creditCards].reduce((output, inputEl) => {
      var _output$mainType;

      const mainType = (0, _matching.getInputMainType)(inputEl);
      const subtype = (0, _matching.getInputSubtype)(inputEl);
      let value = inputEl.value || ((_output$mainType = output[mainType]) === null || _output$mainType === void 0 ? void 0 : _output$mainType[subtype]);

      if (subtype === 'addressCountryCode') {
        value = (0, _formatters.inferCountryCodeFromElement)(inputEl);
      }

      if (value) {
        output[mainType][subtype] = value;
      }

      return output;
    }, {
      credentials: {},
      creditCards: {},
      identities: {}
    });
    return (0, _formatters.prepareFormValuesForStorage)(formValues);
  }
  /**
   * Determine if the form has values we want to store in the tooltipHandler
   * @param {DataStorageObject} [values]
   * @return {boolean}
   */


  hasValues(values) {
    const {
      credentials,
      creditCards,
      identities
    } = values || this.getValues();
    return Boolean(credentials || creditCards || identities);
  }

  removeTooltip() {
    var _this$intObs;

    const tooltip = this.device.getActiveTooltip();

    if (this.isAutofilling || !tooltip) {
      return;
    }

    this.device.removeTooltip();
    (_this$intObs = this.intObs) === null || _this$intObs === void 0 ? void 0 : _this$intObs.disconnect();
  }

  showingTooltip(input) {
    var _this$intObs2;

    (_this$intObs2 = this.intObs) === null || _this$intObs2 === void 0 ? void 0 : _this$intObs2.observe(input);
  }

  removeInputHighlight(input) {
    (0, _autofillUtils.removeInlineStyles)(input, (0, _inputStyles.getIconStylesAutofilled)(input, this));
    input.classList.remove('ddg-autofilled');
    this.addAutofillStyles(input);
  }

  removeAllHighlights(e, dataType) {
    // This ensures we are not removing the highlight ourselves when autofilling more than once
    if (e && !e.isTrusted) return; // If the user has changed the value, we prompt to update the stored creds

    this.shouldPromptToStoreCredentials = true;
    this.execOnInputs(input => this.removeInputHighlight(input), dataType);
  }

  removeInputDecoration(input) {
    (0, _autofillUtils.removeInlineStyles)(input, (0, _inputStyles.getIconStylesBase)(input, this));
    input.removeAttribute(ATTR_AUTOFILL);
  }

  removeAllDecorations() {
    this.execOnInputs(input => this.removeInputDecoration(input));
    this.listeners.forEach(_ref => {
      let {
        el,
        type,
        fn
      } = _ref;
      return el.removeEventListener(type, fn);
    });
  }

  redecorateAllInputs() {
    this.removeAllDecorations();
    this.execOnInputs(input => this.decorateInput(input));
  }

  resetAllInputs() {
    this.execOnInputs(input => {
      (0, _autofillUtils.setValue)(input, '', this.device.globalConfig);
      this.removeInputHighlight(input);
    });
    if (this.activeInput) this.activeInput.focus();
    this.matching.clear();
  }

  dismissTooltip() {
    this.removeTooltip();
  } // This removes all listeners to avoid memory leaks and weird behaviours


  destroy() {
    this.removeAllDecorations();
    this.removeTooltip();
    this.matching.clear();
    this.intObs = null;
  }

  categorizeInputs() {
    const selector = this.matching.cssSelector('FORM_INPUTS_SELECTOR');
    this.form.querySelectorAll(selector).forEach(input => this.addInput(input));
  }

  get submitButtons() {
    const selector = this.matching.cssSelector('SUBMIT_BUTTON_SELECTOR');
    const allButtons =
    /** @type {HTMLElement[]} */
    [...this.form.querySelectorAll(selector)];
    return allButtons.filter(_autofillUtils.isLikelyASubmitButton) // filter out buttons of the wrong type - login buttons on a signup form, signup buttons on a login form
    .filter(button => {
      if (this.isLogin) {
        return !/sign.?up/i.test(button.textContent || '');
      } else if (this.isSignup) {
        return !/(log|sign).?([io])n/i.test(button.textContent || '');
      } else {
        return true;
      }
    });
  }
  /**
   * Executes a function on input elements. Can be limited to certain element types
   * @param {(input: HTMLInputElement|HTMLSelectElement) => void} fn
   * @param {'all' | SupportedMainTypes} inputType
   * @param {boolean} shouldCheckForDecorate
   */


  execOnInputs(fn) {
    let inputType = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 'all';
    let shouldCheckForDecorate = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : true;
    const inputs = this.inputs[inputType];

    for (const input of inputs) {
      let canExecute = true; // sometimes we want to execute even if we didn't decorate

      if (shouldCheckForDecorate) {
        const {
          shouldDecorate
        } = (0, _inputTypeConfig.getInputConfig)(input);
        canExecute = shouldDecorate(input, this);
      }

      if (canExecute) fn(input);
    }
  }

  addInput(input) {
    if (this.inputs.all.has(input)) return this;
    this.inputs.all.add(input);
    const type = this.matching.setInputType(input, this.form, {
      isLogin: this.isLogin
    });
    const mainInputType = (0, _matching.getInputMainType)(input);
    this.inputs[mainInputType].add(input);
    this.decorateInput(input, type);
    return this;
  }

  areAllInputsEmpty(inputType) {
    let allEmpty = true;
    this.execOnInputs(input => {
      if (input.value) allEmpty = false;
    }, inputType);
    return allEmpty;
  }

  addListener(el, type, fn) {
    el.addEventListener(type, fn);
    this.listeners.add({
      el,
      type,
      fn
    });
  }

  addAutofillStyles(input) {
    const styles = (0, _inputStyles.getIconStylesBase)(input, this);
    (0, _autofillUtils.addInlineStyles)(input, styles);
  }

  decorateInput(input, type) {
    const config = (0, _inputTypeConfig.getInputConfig)(input); // todo(Shane): Where should this logic live?

    const inputTypeSupported = (() => {
      if (type === 'identities.emailAddress') {
        if (this.device.availableInputTypes.email) {
          return true;
        }
      }

      if (this.isSignup && type === 'credentials.password') {
        // todo(Shane): Needs runtime polymorphism here
        if (this.device.autofillSettings.featureToggles.password_generation) {
          return true;
        }
      }

      if (this.device.availableInputTypes[config.type] !== true) {
        return false;
      }

      return true;
    })(); // bail if we cannot decorate


    if (!inputTypeSupported) {
      return this;
    }

    if (!config.shouldDecorate(input, this)) {
      return this;
    }

    input.setAttribute(ATTR_AUTOFILL, 'true');
    const hasIcon = !!config.getIconBase(input, this);

    if (hasIcon) {
      this.addAutofillStyles(input);
      this.addListener(input, 'mousemove', e => {
        if ((0, _autofillUtils.isEventWithinDax)(e, e.target)) {
          e.target.style.setProperty('cursor', 'pointer', 'important');
        } else {
          e.target.style.removeProperty('cursor');
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
    } // Store the click to a label so we can use the click when the field is focused


    let storedClick = new WeakMap();
    let timeout = null;

    const handlerLabel = e => {
      // Look for e.target OR it's closest parent to be a HTMLLabelElement
      const control = e.target.closest('label').control;
      if (!control) return;
      storedClick.set(control, getMainClickCoords(e));
      clearTimeout(timeout); // Remove the stored click if the timer expires

      timeout = setTimeout(() => {
        storedClick = new WeakMap();
      }, 1000);
    };

    const handler = e => {
      if (this.device.getActiveTooltip() || this.isAutofilling) return;
      const input = e.target;
      let click = null;

      const getPosition = () => {
        // In extensions, the tooltip is centered on the Dax icon
        // todo(Shane): Work this out
        const alignLeft = this.device.globalConfig.isApp || this.device.globalConfig.isWindows;
        return alignLeft ? input.getBoundingClientRect() : (0, _autofillUtils.getDaxBoundingBox)(input);
      }; // Checks for mousedown event


      if (e.type === 'pointerdown') {
        click = getMainClickCoords(e);
        if (!click) return;
      } else if (storedClick) {
        // Reuse a previous click if one exists for this element
        click = storedClick.get(input);
        storedClick.delete(input);
      }

      if (this.shouldOpenTooltip(e, input)) {
        if (this.device.globalConfig.isMobileApp && // Avoid the icon capturing clicks on small fields making it impossible to focus
        input.offsetWidth > 50 && (0, _autofillUtils.isEventWithinDax)(e, input)) {
          e.preventDefault();
          e.stopImmediatePropagation();
        }

        this.touched.add(input);
        this.device.attachTooltip(this, input, getPosition, click);
      }
    };

    if (input.nodeName !== 'SELECT') {
      const events = ['pointerdown'];

      if (!this.device.globalConfig.isMobileApp) {// todo(Shane): Re-enable focus event?
        // events.push('focus')
      }

      input.labels.forEach(label => {
        this.addListener(label, 'pointerdown', handlerLabel);
      });
      events.forEach(ev => this.addListener(input, ev, handler));
    }

    return this;
  }

  shouldOpenTooltip(e, input) {
    if (this.device.globalConfig.isApp) return true;
    const inputType = (0, _matching.getInputMainType)(input);
    return !this.touched.has(input) && this.areAllInputsEmpty(inputType) || (0, _autofillUtils.isEventWithinDax)(e, input);
  }

  autofillInput(input, string, dataType) {
    // Do not autofill if it's invisible (select elements can be hidden because of custom implementations)
    if (input instanceof HTMLInputElement && !(0, _autofillUtils.isVisible)(input)) return; // @ts-ignore

    const activeInputSubtype = (0, _matching.getInputSubtype)(this.activeInput);
    const inputSubtype = (0, _matching.getInputSubtype)(input);
    const isEmailAutofill = activeInputSubtype === 'emailAddress' && inputSubtype === 'emailAddress'; // Don't override values for identities, unless it's the current input or we're autofilling email

    if (dataType === 'identities' && // only for identities
    input.nodeName !== 'SELECT' && input.value !== '' && // if the input is not empty
    this.activeInput !== input && // and this is not the active input
    !isEmailAutofill // and we're not auto-filling email
    ) return; // do not overwrite the value

    const successful = (0, _autofillUtils.setValue)(input, string, this.device.globalConfig);
    if (!successful) return;
    input.classList.add('ddg-autofilled');
    (0, _autofillUtils.addInlineStyles)(input, (0, _inputStyles.getIconStylesAutofilled)(input, this)); // If the user changes the value, remove the decoration

    input.addEventListener('input', e => this.removeAllHighlights(e, dataType), {
      once: true
    });
  }
  /**
   * Autofill method for email protection only
   * @param {string} alias
   * @param {'all' | SupportedMainTypes} dataType
   */


  autofillEmail(alias) {
    let dataType = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 'identities';
    this.isAutofilling = true;
    this.execOnInputs(input => this.autofillInput(input, alias, dataType), dataType);
    this.isAutofilling = false;
    this.removeTooltip();
  }

  autofillData(data, dataType) {
    var _this$device$postAuto, _this$device2;

    this.shouldPromptToStoreData = false;
    this.isAutofilling = true;
    this.execOnInputs(input => {
      const inputSubtype = (0, _matching.getInputSubtype)(input);
      let autofillData = data[inputSubtype];

      if (inputSubtype === 'expiration' && input instanceof HTMLInputElement) {
        autofillData = (0, _formatters.getUnifiedExpiryDate)(input, data.expirationMonth, data.expirationYear, this);
      }

      if (inputSubtype === 'expirationYear' && input instanceof HTMLInputElement) {
        autofillData = (0, _formatters.formatCCYear)(input, autofillData, this);
      }

      if (inputSubtype === 'addressCountryCode') {
        autofillData = (0, _formatters.getCountryName)(input, data);
      }

      if (autofillData) this.autofillInput(input, autofillData, dataType);
    }, dataType);
    this.isAutofilling = false;
    (_this$device$postAuto = (_this$device2 = this.device).postAutofill) === null || _this$device$postAuto === void 0 ? void 0 : _this$device$postAuto.call(_this$device2, data, this.getValues());
    this.removeTooltip();
  }

}

exports.Form = Form;

},{"../autofill-utils":51,"../constants":54,"./FormAnalyzer":24,"./formatters":26,"./inputStyles":27,"./inputTypeConfig.js":28,"./matching":33}],24:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;

var _matching = require("./matching");

var _constants = require("../constants");

var _matchingConfiguration = require("./matching-configuration");

var _autofillUtils = require("../autofill-utils");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

class FormAnalyzer {
  /** @type HTMLElement */

  /** @type Matching */

  /**
   * @param {HTMLElement} form
   * @param {HTMLInputElement|HTMLSelectElement} input
   * @param {Matching} [matching]
   */
  constructor(form, input, matching) {
    _defineProperty(this, "form", void 0);

    _defineProperty(this, "matching", void 0);

    this.form = form;
    this.matching = matching || new _matching.Matching(_matchingConfiguration.matchingConfiguration);
    this.autofillSignal = 0;
    this.signals = []; // Avoid autofill on our signup page

    if (window.location.href.match(/^https:\/\/(.+\.)?duckduckgo\.com\/email\/choose-address/i)) {
      return this;
    }

    this.evaluateElAttributes(input, 3, true);
    form ? this.evaluateForm() : this.evaluatePage();
    return this;
  }

  get isLogin() {
    return this.autofillSignal < 0;
  }

  get isSignup() {
    return this.autofillSignal >= 0;
  }

  increaseSignalBy(strength, signal) {
    this.autofillSignal += strength;
    this.signals.push("".concat(signal, ": +").concat(strength));
    return this;
  }

  decreaseSignalBy(strength, signal) {
    this.autofillSignal -= strength;
    this.signals.push("".concat(signal, ": -").concat(strength));
    return this;
  }

  updateSignal(_ref) {
    let {
      string,
      // The string to check
      strength,
      // Strength of the signal
      signalType = 'generic',
      // For debugging purposes, we give a name to the signal
      shouldFlip = false,
      // Flips the signals, i.e. when a link points outside. See below
      shouldCheckUnifiedForm = false,
      // Should check for login/signup forms
      shouldBeConservative = false // Should use the conservative signup regex

    } = _ref;
    const negativeRegex = new RegExp(/sign(ing)?.?in(?!g)|log.?in|unsubscri/i);
    const positiveRegex = new RegExp(/sign(ing)?.?up|join|\bregist(er|ration)|newsletter|\bsubscri(be|ption)|contact|create|start|settings|preferences|profile|update|checkout|guest|purchase|buy|order|schedule|estimate|request/i);
    const conservativePositiveRegex = new RegExp(/sign.?up|join|register|newsletter|subscri(be|ption)|settings|preferences|profile|update/i);
    const strictPositiveRegex = new RegExp(/sign.?up|join|register|settings|preferences|profile|update/i);
    const matchesNegative = string === 'current-password' || string.match(negativeRegex); // Check explicitly for unified login/signup forms. They should always be negative, so we increase signal

    if (shouldCheckUnifiedForm && matchesNegative && string.match(strictPositiveRegex)) {
      this.decreaseSignalBy(strength + 2, "Unified detected ".concat(signalType));
      return this;
    }

    const matchesPositive = string === 'new-password' || string.match(shouldBeConservative ? conservativePositiveRegex : positiveRegex); // In some cases a login match means the login is somewhere else, i.e. when a link points outside

    if (shouldFlip) {
      if (matchesNegative) this.increaseSignalBy(strength, signalType);
      if (matchesPositive) this.decreaseSignalBy(strength, signalType);
    } else {
      if (matchesNegative) this.decreaseSignalBy(strength, signalType);
      if (matchesPositive) this.increaseSignalBy(strength, signalType);
    }

    return this;
  }

  evaluateElAttributes(el) {
    let signalStrength = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : 3;
    let isInput = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : false;
    Array.from(el.attributes).forEach(attr => {
      if (attr.name === 'style') return;
      const attributeString = "".concat(attr.name, "=").concat(attr.value);
      this.updateSignal({
        string: attributeString,
        strength: signalStrength,
        signalType: "".concat(el.name, " attr: ").concat(attributeString),
        shouldCheckUnifiedForm: isInput
      });
    });
  }

  evaluatePageTitle() {
    const pageTitle = document.title;
    this.updateSignal({
      string: pageTitle,
      strength: 2,
      signalType: "page title: ".concat(pageTitle)
    });
  }

  evaluatePageHeadings() {
    const headings = document.querySelectorAll('h1, h2, h3, [class*="title"], [id*="title"]');

    if (headings) {
      headings.forEach(_ref2 => {
        let {
          textContent
        } = _ref2;
        textContent = (0, _matching.removeExcessWhitespace)(textContent || '');
        this.updateSignal({
          string: textContent,
          strength: 0.5,
          signalType: "heading: ".concat(textContent),
          shouldCheckUnifiedForm: true,
          shouldBeConservative: true
        });
      });
    }
  }

  evaluatePage() {
    this.evaluatePageTitle();
    this.evaluatePageHeadings(); // Check for submit buttons

    const buttons = document.querySelectorAll("\n                button[type=submit],\n                button:not([type]),\n                [role=button]\n            ");
    buttons.forEach(button => {
      // if the button has a form, it's not related to our input, because our input has no form here
      if (button instanceof HTMLButtonElement) {
        if (!button.form && !button.closest('form')) {
          this.evaluateElement(button);
          this.evaluateElAttributes(button, 0.5);
        }
      }
    });
  }

  elementIs(el, type) {
    return el.nodeName.toLowerCase() === type.toLowerCase();
  }

  getText(el) {
    // for buttons, we don't care about descendants, just get the whole text as is
    // this is important in order to give proper attribution of the text to the button
    if (this.elementIs(el, 'BUTTON')) return (0, _matching.removeExcessWhitespace)(el.textContent);
    if (this.elementIs(el, 'INPUT') && ['submit', 'button'].includes(el.type)) return el.value;
    return (0, _matching.removeExcessWhitespace)(Array.from(el.childNodes).reduce((text, child) => this.elementIs(child, '#text') ? text + ' ' + child.textContent : text, ''));
  }

  evaluateElement(el) {
    const string = this.getText(el);

    if (el.matches(this.matching.cssSelector('password'))) {
      // These are explicit signals by the web author, so we weigh them heavily
      this.updateSignal({
        string: el.getAttribute('autocomplete') || '',
        strength: 20,
        signalType: "explicit: ".concat(el.getAttribute('autocomplete'))
      });
    } // check button contents


    if (el.matches(this.matching.cssSelector('SUBMIT_BUTTON_SELECTOR'))) {
      // If we're sure this is a submit button, it's a stronger signal
      const strength = (0, _autofillUtils.isLikelyASubmitButton)(el) ? 20 : 2;
      this.updateSignal({
        string,
        strength,
        signalType: "submit: ".concat(string)
      });
    } // if a link points to relevant urls or contain contents outside the page…


    if (this.elementIs(el, 'A') && el.href && el.href !== '#' || (el.getAttribute('role') || '').toUpperCase() === 'LINK' || el.matches('button[class*=secondary]')) {
      // …and matches one of the regexes, we assume the match is not pertinent to the current form
      this.updateSignal({
        string,
        strength: 1,
        signalType: "external link: ".concat(string),
        shouldFlip: true
      });
    } else {
      var _removeExcessWhitespa;

      // any other case
      // only consider the el if it's a small text to avoid noisy disclaimers
      if (((_removeExcessWhitespa = (0, _matching.removeExcessWhitespace)(el.textContent)) === null || _removeExcessWhitespa === void 0 ? void 0 : _removeExcessWhitespa.length) < _constants.constants.TEXT_LENGTH_CUTOFF) {
        this.updateSignal({
          string,
          strength: 1,
          signalType: "generic: ".concat(string),
          shouldCheckUnifiedForm: true
        });
      }
    }
  }

  evaluateForm() {
    // Check page title
    this.evaluatePageTitle(); // Check form attributes

    this.evaluateElAttributes(this.form); // Check form contents (skip select and option because they contain too much noise)

    this.form.querySelectorAll('*:not(select):not(option)').forEach(el => {
      // Check if element is not hidden. Note that we can't use offsetHeight
      // nor intersectionObserver, because the element could be outside the
      // viewport or its parent hidden
      const displayValue = window.getComputedStyle(el, null).getPropertyValue('display');
      if (displayValue !== 'none') this.evaluateElement(el);
    }); // If we can't decide at this point, try reading page headings

    if (this.autofillSignal === 0) {
      this.evaluatePageHeadings();
    }

    return this;
  }

}

var _default = FormAnalyzer;
exports.default = _default;

},{"../autofill-utils":51,"../constants":54,"./matching":33,"./matching-configuration":32}],25:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.COUNTRY_NAMES_TO_CODES = exports.COUNTRY_CODES_TO_NAMES = void 0;

/**
 * Country names object using 2-letter country codes to reference country name
 * Derived from the Intl.DisplayNames implementation
 * @source https://stackoverflow.com/a/70517921/1948947
 */
const COUNTRY_CODES_TO_NAMES = {
  AC: 'Ascension Island',
  AD: 'Andorra',
  AE: 'United Arab Emirates',
  AF: 'Afghanistan',
  AG: 'Antigua & Barbuda',
  AI: 'Anguilla',
  AL: 'Albania',
  AM: 'Armenia',
  AN: 'Curaçao',
  AO: 'Angola',
  AQ: 'Antarctica',
  AR: 'Argentina',
  AS: 'American Samoa',
  AT: 'Austria',
  AU: 'Australia',
  AW: 'Aruba',
  AX: 'Åland Islands',
  AZ: 'Azerbaijan',
  BA: 'Bosnia & Herzegovina',
  BB: 'Barbados',
  BD: 'Bangladesh',
  BE: 'Belgium',
  BF: 'Burkina Faso',
  BG: 'Bulgaria',
  BH: 'Bahrain',
  BI: 'Burundi',
  BJ: 'Benin',
  BL: 'St. Barthélemy',
  BM: 'Bermuda',
  BN: 'Brunei',
  BO: 'Bolivia',
  BQ: 'Caribbean Netherlands',
  BR: 'Brazil',
  BS: 'Bahamas',
  BT: 'Bhutan',
  BU: 'Myanmar (Burma)',
  BV: 'Bouvet Island',
  BW: 'Botswana',
  BY: 'Belarus',
  BZ: 'Belize',
  CA: 'Canada',
  CC: 'Cocos (Keeling) Islands',
  CD: 'Congo - Kinshasa',
  CF: 'Central African Republic',
  CG: 'Congo - Brazzaville',
  CH: 'Switzerland',
  CI: 'Côte d’Ivoire',
  CK: 'Cook Islands',
  CL: 'Chile',
  CM: 'Cameroon',
  CN: 'China mainland',
  CO: 'Colombia',
  CP: 'Clipperton Island',
  CR: 'Costa Rica',
  CS: 'Serbia',
  CU: 'Cuba',
  CV: 'Cape Verde',
  CW: 'Curaçao',
  CX: 'Christmas Island',
  CY: 'Cyprus',
  CZ: 'Czechia',
  DD: 'Germany',
  DE: 'Germany',
  DG: 'Diego Garcia',
  DJ: 'Djibouti',
  DK: 'Denmark',
  DM: 'Dominica',
  DO: 'Dominican Republic',
  DY: 'Benin',
  DZ: 'Algeria',
  EA: 'Ceuta & Melilla',
  EC: 'Ecuador',
  EE: 'Estonia',
  EG: 'Egypt',
  EH: 'Western Sahara',
  ER: 'Eritrea',
  ES: 'Spain',
  ET: 'Ethiopia',
  EU: 'European Union',
  EZ: 'Eurozone',
  FI: 'Finland',
  FJ: 'Fiji',
  FK: 'Falkland Islands',
  FM: 'Micronesia',
  FO: 'Faroe Islands',
  FR: 'France',
  FX: 'France',
  GA: 'Gabon',
  GB: 'United Kingdom',
  GD: 'Grenada',
  GE: 'Georgia',
  GF: 'French Guiana',
  GG: 'Guernsey',
  GH: 'Ghana',
  GI: 'Gibraltar',
  GL: 'Greenland',
  GM: 'Gambia',
  GN: 'Guinea',
  GP: 'Guadeloupe',
  GQ: 'Equatorial Guinea',
  GR: 'Greece',
  GS: 'So. Georgia & So. Sandwich Isl.',
  GT: 'Guatemala',
  GU: 'Guam',
  GW: 'Guinea-Bissau',
  GY: 'Guyana',
  HK: 'Hong Kong',
  HM: 'Heard & McDonald Islands',
  HN: 'Honduras',
  HR: 'Croatia',
  HT: 'Haiti',
  HU: 'Hungary',
  HV: 'Burkina Faso',
  IC: 'Canary Islands',
  ID: 'Indonesia',
  IE: 'Ireland',
  IL: 'Israel',
  IM: 'Isle of Man',
  IN: 'India',
  IO: 'Chagos Archipelago',
  IQ: 'Iraq',
  IR: 'Iran',
  IS: 'Iceland',
  IT: 'Italy',
  JE: 'Jersey',
  JM: 'Jamaica',
  JO: 'Jordan',
  JP: 'Japan',
  KE: 'Kenya',
  KG: 'Kyrgyzstan',
  KH: 'Cambodia',
  KI: 'Kiribati',
  KM: 'Comoros',
  KN: 'St. Kitts & Nevis',
  KP: 'North Korea',
  KR: 'South Korea',
  KW: 'Kuwait',
  KY: 'Cayman Islands',
  KZ: 'Kazakhstan',
  LA: 'Laos',
  LB: 'Lebanon',
  LC: 'St. Lucia',
  LI: 'Liechtenstein',
  LK: 'Sri Lanka',
  LR: 'Liberia',
  LS: 'Lesotho',
  LT: 'Lithuania',
  LU: 'Luxembourg',
  LV: 'Latvia',
  LY: 'Libya',
  MA: 'Morocco',
  MC: 'Monaco',
  MD: 'Moldova',
  ME: 'Montenegro',
  MF: 'St. Martin',
  MG: 'Madagascar',
  MH: 'Marshall Islands',
  MK: 'North Macedonia',
  ML: 'Mali',
  MM: 'Myanmar (Burma)',
  MN: 'Mongolia',
  MO: 'Macao',
  MP: 'Northern Mariana Islands',
  MQ: 'Martinique',
  MR: 'Mauritania',
  MS: 'Montserrat',
  MT: 'Malta',
  MU: 'Mauritius',
  MV: 'Maldives',
  MW: 'Malawi',
  MX: 'Mexico',
  MY: 'Malaysia',
  MZ: 'Mozambique',
  NA: 'Namibia',
  NC: 'New Caledonia',
  NE: 'Niger',
  NF: 'Norfolk Island',
  NG: 'Nigeria',
  NH: 'Vanuatu',
  NI: 'Nicaragua',
  NL: 'Netherlands',
  NO: 'Norway',
  NP: 'Nepal',
  NR: 'Nauru',
  NU: 'Niue',
  NZ: 'New Zealand',
  OM: 'Oman',
  PA: 'Panama',
  PE: 'Peru',
  PF: 'French Polynesia',
  PG: 'Papua New Guinea',
  PH: 'Philippines',
  PK: 'Pakistan',
  PL: 'Poland',
  PM: 'St. Pierre & Miquelon',
  PN: 'Pitcairn Islands',
  PR: 'Puerto Rico',
  PS: 'Palestinian Territories',
  PT: 'Portugal',
  PW: 'Palau',
  PY: 'Paraguay',
  QA: 'Qatar',
  QO: 'Outlying Oceania',
  RE: 'Réunion',
  RH: 'Zimbabwe',
  RO: 'Romania',
  RS: 'Serbia',
  RU: 'Russia',
  RW: 'Rwanda',
  SA: 'Saudi Arabia',
  SB: 'Solomon Islands',
  SC: 'Seychelles',
  SD: 'Sudan',
  SE: 'Sweden',
  SG: 'Singapore',
  SH: 'St. Helena',
  SI: 'Slovenia',
  SJ: 'Svalbard & Jan Mayen',
  SK: 'Slovakia',
  SL: 'Sierra Leone',
  SM: 'San Marino',
  SN: 'Senegal',
  SO: 'Somalia',
  SR: 'Suriname',
  SS: 'South Sudan',
  ST: 'São Tomé & Príncipe',
  SU: 'Russia',
  SV: 'El Salvador',
  SX: 'Sint Maarten',
  SY: 'Syria',
  SZ: 'Eswatini',
  TA: 'Tristan da Cunha',
  TC: 'Turks & Caicos Islands',
  TD: 'Chad',
  TF: 'French Southern Territories',
  TG: 'Togo',
  TH: 'Thailand',
  TJ: 'Tajikistan',
  TK: 'Tokelau',
  TL: 'Timor-Leste',
  TM: 'Turkmenistan',
  TN: 'Tunisia',
  TO: 'Tonga',
  TP: 'Timor-Leste',
  TR: 'Turkey',
  TT: 'Trinidad & Tobago',
  TV: 'Tuvalu',
  TW: 'Taiwan',
  TZ: 'Tanzania',
  UA: 'Ukraine',
  UG: 'Uganda',
  UK: 'United Kingdom',
  UM: 'U.S. Outlying Islands',
  UN: 'United Nations',
  US: 'United States',
  UY: 'Uruguay',
  UZ: 'Uzbekistan',
  VA: 'Vatican City',
  VC: 'St. Vincent & Grenadines',
  VD: 'Vietnam',
  VE: 'Venezuela',
  VG: 'British Virgin Islands',
  VI: 'U.S. Virgin Islands',
  VN: 'Vietnam',
  VU: 'Vanuatu',
  WF: 'Wallis & Futuna',
  WS: 'Samoa',
  XA: 'Pseudo-Accents',
  XB: 'Pseudo-Bidi',
  XK: 'Kosovo',
  YD: 'Yemen',
  YE: 'Yemen',
  YT: 'Mayotte',
  YU: 'Serbia',
  ZA: 'South Africa',
  ZM: 'Zambia',
  ZR: 'Congo - Kinshasa',
  ZW: 'Zimbabwe',
  ZZ: 'Unknown Region'
};
/**
 * Country names object using country name to reference 2-letter country codes
 * Derived from the solution above with
 * Object.fromEntries(Object.entries(COUNTRY_CODES_TO_NAMES).map(entry => [entry[1], entry[0]]))
 */

exports.COUNTRY_CODES_TO_NAMES = COUNTRY_CODES_TO_NAMES;
const COUNTRY_NAMES_TO_CODES = {
  'Ascension Island': 'AC',
  Andorra: 'AD',
  'United Arab Emirates': 'AE',
  Afghanistan: 'AF',
  'Antigua & Barbuda': 'AG',
  Anguilla: 'AI',
  Albania: 'AL',
  Armenia: 'AM',
  'Curaçao': 'CW',
  Angola: 'AO',
  Antarctica: 'AQ',
  Argentina: 'AR',
  'American Samoa': 'AS',
  Austria: 'AT',
  Australia: 'AU',
  Aruba: 'AW',
  'Åland Islands': 'AX',
  Azerbaijan: 'AZ',
  'Bosnia & Herzegovina': 'BA',
  Barbados: 'BB',
  Bangladesh: 'BD',
  Belgium: 'BE',
  'Burkina Faso': 'HV',
  Bulgaria: 'BG',
  Bahrain: 'BH',
  Burundi: 'BI',
  Benin: 'DY',
  'St. Barthélemy': 'BL',
  Bermuda: 'BM',
  Brunei: 'BN',
  Bolivia: 'BO',
  'Caribbean Netherlands': 'BQ',
  Brazil: 'BR',
  Bahamas: 'BS',
  Bhutan: 'BT',
  'Myanmar (Burma)': 'MM',
  'Bouvet Island': 'BV',
  Botswana: 'BW',
  Belarus: 'BY',
  Belize: 'BZ',
  Canada: 'CA',
  'Cocos (Keeling) Islands': 'CC',
  'Congo - Kinshasa': 'ZR',
  'Central African Republic': 'CF',
  'Congo - Brazzaville': 'CG',
  Switzerland: 'CH',
  'Côte d’Ivoire': 'CI',
  'Cook Islands': 'CK',
  Chile: 'CL',
  Cameroon: 'CM',
  'China mainland': 'CN',
  Colombia: 'CO',
  'Clipperton Island': 'CP',
  'Costa Rica': 'CR',
  Serbia: 'YU',
  Cuba: 'CU',
  'Cape Verde': 'CV',
  'Christmas Island': 'CX',
  Cyprus: 'CY',
  Czechia: 'CZ',
  Germany: 'DE',
  'Diego Garcia': 'DG',
  Djibouti: 'DJ',
  Denmark: 'DK',
  Dominica: 'DM',
  'Dominican Republic': 'DO',
  Algeria: 'DZ',
  'Ceuta & Melilla': 'EA',
  Ecuador: 'EC',
  Estonia: 'EE',
  Egypt: 'EG',
  'Western Sahara': 'EH',
  Eritrea: 'ER',
  Spain: 'ES',
  Ethiopia: 'ET',
  'European Union': 'EU',
  Eurozone: 'EZ',
  Finland: 'FI',
  Fiji: 'FJ',
  'Falkland Islands': 'FK',
  Micronesia: 'FM',
  'Faroe Islands': 'FO',
  France: 'FX',
  Gabon: 'GA',
  'United Kingdom': 'UK',
  Grenada: 'GD',
  Georgia: 'GE',
  'French Guiana': 'GF',
  Guernsey: 'GG',
  Ghana: 'GH',
  Gibraltar: 'GI',
  Greenland: 'GL',
  Gambia: 'GM',
  Guinea: 'GN',
  Guadeloupe: 'GP',
  'Equatorial Guinea': 'GQ',
  Greece: 'GR',
  'So. Georgia & So. Sandwich Isl.': 'GS',
  Guatemala: 'GT',
  Guam: 'GU',
  'Guinea-Bissau': 'GW',
  Guyana: 'GY',
  'Hong Kong': 'HK',
  'Heard & McDonald Islands': 'HM',
  Honduras: 'HN',
  Croatia: 'HR',
  Haiti: 'HT',
  Hungary: 'HU',
  'Canary Islands': 'IC',
  Indonesia: 'ID',
  Ireland: 'IE',
  Israel: 'IL',
  'Isle of Man': 'IM',
  India: 'IN',
  'Chagos Archipelago': 'IO',
  Iraq: 'IQ',
  Iran: 'IR',
  Iceland: 'IS',
  Italy: 'IT',
  Jersey: 'JE',
  Jamaica: 'JM',
  Jordan: 'JO',
  Japan: 'JP',
  Kenya: 'KE',
  Kyrgyzstan: 'KG',
  Cambodia: 'KH',
  Kiribati: 'KI',
  Comoros: 'KM',
  'St. Kitts & Nevis': 'KN',
  'North Korea': 'KP',
  'South Korea': 'KR',
  Kuwait: 'KW',
  'Cayman Islands': 'KY',
  Kazakhstan: 'KZ',
  Laos: 'LA',
  Lebanon: 'LB',
  'St. Lucia': 'LC',
  Liechtenstein: 'LI',
  'Sri Lanka': 'LK',
  Liberia: 'LR',
  Lesotho: 'LS',
  Lithuania: 'LT',
  Luxembourg: 'LU',
  Latvia: 'LV',
  Libya: 'LY',
  Morocco: 'MA',
  Monaco: 'MC',
  Moldova: 'MD',
  Montenegro: 'ME',
  'St. Martin': 'MF',
  Madagascar: 'MG',
  'Marshall Islands': 'MH',
  'North Macedonia': 'MK',
  Mali: 'ML',
  Mongolia: 'MN',
  Macao: 'MO',
  'Northern Mariana Islands': 'MP',
  Martinique: 'MQ',
  Mauritania: 'MR',
  Montserrat: 'MS',
  Malta: 'MT',
  Mauritius: 'MU',
  Maldives: 'MV',
  Malawi: 'MW',
  Mexico: 'MX',
  Malaysia: 'MY',
  Mozambique: 'MZ',
  Namibia: 'NA',
  'New Caledonia': 'NC',
  Niger: 'NE',
  'Norfolk Island': 'NF',
  Nigeria: 'NG',
  Vanuatu: 'VU',
  Nicaragua: 'NI',
  Netherlands: 'NL',
  Norway: 'NO',
  Nepal: 'NP',
  Nauru: 'NR',
  Niue: 'NU',
  'New Zealand': 'NZ',
  Oman: 'OM',
  Panama: 'PA',
  Peru: 'PE',
  'French Polynesia': 'PF',
  'Papua New Guinea': 'PG',
  Philippines: 'PH',
  Pakistan: 'PK',
  Poland: 'PL',
  'St. Pierre & Miquelon': 'PM',
  'Pitcairn Islands': 'PN',
  'Puerto Rico': 'PR',
  'Palestinian Territories': 'PS',
  Portugal: 'PT',
  Palau: 'PW',
  Paraguay: 'PY',
  Qatar: 'QA',
  'Outlying Oceania': 'QO',
  'Réunion': 'RE',
  Zimbabwe: 'ZW',
  Romania: 'RO',
  Russia: 'SU',
  Rwanda: 'RW',
  'Saudi Arabia': 'SA',
  'Solomon Islands': 'SB',
  Seychelles: 'SC',
  Sudan: 'SD',
  Sweden: 'SE',
  Singapore: 'SG',
  'St. Helena': 'SH',
  Slovenia: 'SI',
  'Svalbard & Jan Mayen': 'SJ',
  Slovakia: 'SK',
  'Sierra Leone': 'SL',
  'San Marino': 'SM',
  Senegal: 'SN',
  Somalia: 'SO',
  Suriname: 'SR',
  'South Sudan': 'SS',
  'São Tomé & Príncipe': 'ST',
  'El Salvador': 'SV',
  'Sint Maarten': 'SX',
  Syria: 'SY',
  Eswatini: 'SZ',
  'Tristan da Cunha': 'TA',
  'Turks & Caicos Islands': 'TC',
  Chad: 'TD',
  'French Southern Territories': 'TF',
  Togo: 'TG',
  Thailand: 'TH',
  Tajikistan: 'TJ',
  Tokelau: 'TK',
  'Timor-Leste': 'TP',
  Turkmenistan: 'TM',
  Tunisia: 'TN',
  Tonga: 'TO',
  Turkey: 'TR',
  'Trinidad & Tobago': 'TT',
  Tuvalu: 'TV',
  Taiwan: 'TW',
  Tanzania: 'TZ',
  Ukraine: 'UA',
  Uganda: 'UG',
  'U.S. Outlying Islands': 'UM',
  'United Nations': 'UN',
  'United States': 'US',
  Uruguay: 'UY',
  Uzbekistan: 'UZ',
  'Vatican City': 'VA',
  'St. Vincent & Grenadines': 'VC',
  Vietnam: 'VN',
  Venezuela: 'VE',
  'British Virgin Islands': 'VG',
  'U.S. Virgin Islands': 'VI',
  'Wallis & Futuna': 'WF',
  Samoa: 'WS',
  'Pseudo-Accents': 'XA',
  'Pseudo-Bidi': 'XB',
  Kosovo: 'XK',
  Yemen: 'YE',
  Mayotte: 'YT',
  'South Africa': 'ZA',
  Zambia: 'ZM',
  'Unknown Region': 'ZZ'
};
exports.COUNTRY_NAMES_TO_CODES = COUNTRY_NAMES_TO_CODES;

},{}],26:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.prepareFormValuesForStorage = exports.inferCountryCodeFromElement = exports.getUnifiedExpiryDate = exports.getMMAndYYYYFromString = exports.getCountryName = exports.getCountryDisplayName = exports.formatFullName = exports.formatCCYear = void 0;

var _matching = require("./matching");

var _countryNames = require("./countryNames");

var _templateObject, _templateObject2;

function _taggedTemplateLiteral(strings, raw) { if (!raw) { raw = strings.slice(0); } return Object.freeze(Object.defineProperties(strings, { raw: { value: Object.freeze(raw) } })); }

// Matches strings like mm/yy, mm-yyyy, mm-aa
const DATE_SEPARATOR_REGEX = /\w\w\s?(?<separator>[/\s.\-_—–])\s?\w\w/i; // Matches 4 non-digit repeated characters (YYYY or AAAA) or 4 digits (2022)

const FOUR_DIGIT_YEAR_REGEX = /(\D)\1{3}|\d{4}/i;
/**
 * Format the cc year to best adapt to the input requirements (YY vs YYYY)
 * @param {HTMLInputElement} input
 * @param {string} year
 * @param {import("./Form").Form} form
 * @returns {string}
 */

const formatCCYear = (input, year, form) => {
  const selector = form.matching.cssSelector('FORM_INPUTS_SELECTOR');
  if (input.maxLength === 4 || (0, _matching.checkPlaceholderAndLabels)(input, FOUR_DIGIT_YEAR_REGEX, form.form, selector)) return year;
  return "".concat(Number(year) - 2000);
};
/**
 * Get a unified expiry date with separator
 * @param {HTMLInputElement} input
 * @param {string} month
 * @param {string} year
 * @param {import("./Form").Form} form
 * @returns {string}
 */


exports.formatCCYear = formatCCYear;

const getUnifiedExpiryDate = (input, month, year, form) => {
  var _matchInPlaceholderAn, _matchInPlaceholderAn2;

  const formattedYear = formatCCYear(input, year, form);
  const paddedMonth = "".concat(month).padStart(2, '0');
  const cssSelector = form.matching.cssSelector('FORM_INPUTS_SELECTOR');
  const separator = ((_matchInPlaceholderAn = (0, _matching.matchInPlaceholderAndLabels)(input, DATE_SEPARATOR_REGEX, form.form, cssSelector)) === null || _matchInPlaceholderAn === void 0 ? void 0 : (_matchInPlaceholderAn2 = _matchInPlaceholderAn.groups) === null || _matchInPlaceholderAn2 === void 0 ? void 0 : _matchInPlaceholderAn2.separator) || '/';
  return "".concat(paddedMonth).concat(separator).concat(formattedYear);
};

exports.getUnifiedExpiryDate = getUnifiedExpiryDate;

const formatFullName = _ref => {
  let {
    firstName = '',
    middleName = '',
    lastName = ''
  } = _ref;
  return "".concat(firstName, " ").concat(middleName ? middleName + ' ' : '').concat(lastName).trim();
};
/**
 * Tries to look up a human-readable country name from the country code
 * @param {string} locale
 * @param {string} addressCountryCode
 * @return {string} - Returns the country code if we can't find a name
 */


exports.formatFullName = formatFullName;

const getCountryDisplayName = (locale, addressCountryCode) => {
  try {
    const regionNames = new Intl.DisplayNames([locale], {
      type: 'region'
    }); // Adding this ts-ignore to prevent having to change this implementation.
    // @ts-ignore

    return regionNames.of(addressCountryCode);
  } catch (e) {
    return _countryNames.COUNTRY_CODES_TO_NAMES[addressCountryCode] || addressCountryCode;
  }
};
/**
 * Tries to infer the element locale or returns 'en'
 * @param {HTMLInputElement | HTMLSelectElement} el
 * @return {string | 'en'}
 */


exports.getCountryDisplayName = getCountryDisplayName;

const inferElementLocale = el => {
  var _el$form;

  return el.lang || ((_el$form = el.form) === null || _el$form === void 0 ? void 0 : _el$form.lang) || document.body.lang || document.documentElement.lang || 'en';
};
/**
 * Tries to format the country code into a localised country name
 * @param {HTMLInputElement | HTMLSelectElement} el
 * @param {{addressCountryCode?: string}} options
 */


const getCountryName = function (el) {
  let options = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  const {
    addressCountryCode
  } = options;
  if (!addressCountryCode) return ''; // Try to infer the field language or fallback to en

  const elLocale = inferElementLocale(el);
  const localisedCountryName = getCountryDisplayName(elLocale, addressCountryCode); // If it's a select el we try to find a suitable match to autofill

  if (el.nodeName === 'SELECT') {
    const englishCountryName = getCountryDisplayName('en', addressCountryCode); // This regex matches both the localised and English country names

    const countryNameRegex = new RegExp(String.raw(_templateObject || (_templateObject = _taggedTemplateLiteral(["", "|", ""])), localisedCountryName.replace(/ /g, '.?'), englishCountryName.replace(/ /g, '.?')), 'i');
    const countryCodeRegex = new RegExp(String.raw(_templateObject2 || (_templateObject2 = _taggedTemplateLiteral(["\b", "\b"], ["\\b", "\\b"])), addressCountryCode), 'i'); // We check the country code first because it's more accurate

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
/**
 * Try to get a map of localised country names to code, or falls back to the English map
 * @param {HTMLInputElement | HTMLSelectElement} el
 */


exports.getCountryName = getCountryName;

const getLocalisedCountryNamesToCodes = el => {
  if (typeof Intl.DisplayNames !== 'function') return _countryNames.COUNTRY_NAMES_TO_CODES; // Try to infer the field language or fallback to en

  const elLocale = inferElementLocale(el);
  return Object.fromEntries(Object.entries(_countryNames.COUNTRY_CODES_TO_NAMES).map(_ref2 => {
    let [code] = _ref2;
    return [getCountryDisplayName(elLocale, code), code];
  }));
};
/**
 * Try to infer a country code from an element we identified as identities.addressCountryCode
 * @param {HTMLInputElement | HTMLSelectElement} el
 * @return {string}
 */


const inferCountryCodeFromElement = el => {
  if (_countryNames.COUNTRY_CODES_TO_NAMES[el.value]) return el.value;
  if (_countryNames.COUNTRY_NAMES_TO_CODES[el.value]) return _countryNames.COUNTRY_NAMES_TO_CODES[el.value];
  const localisedCountryNamesToCodes = getLocalisedCountryNamesToCodes(el);
  if (localisedCountryNamesToCodes[el.value]) return localisedCountryNamesToCodes[el.value];

  if (el instanceof HTMLSelectElement) {
    var _el$selectedOptions$;

    const selectedText = (_el$selectedOptions$ = el.selectedOptions[0]) === null || _el$selectedOptions$ === void 0 ? void 0 : _el$selectedOptions$.text;
    if (_countryNames.COUNTRY_CODES_TO_NAMES[selectedText]) return selectedText;
    if (_countryNames.COUNTRY_NAMES_TO_CODES[selectedText]) return localisedCountryNamesToCodes[selectedText];
    if (localisedCountryNamesToCodes[selectedText]) return localisedCountryNamesToCodes[selectedText];
  }

  return '';
};
/**
 * Gets separate expiration month and year from a single string
 * @param {string} expiration
 * @return {{expirationYear: string, expirationMonth: string}}
 */


exports.inferCountryCodeFromElement = inferCountryCodeFromElement;

const getMMAndYYYYFromString = expiration => {
  const values = expiration.match(/(\d+)/g) || [];
  return values === null || values === void 0 ? void 0 : values.reduce((output, current) => {
    if (Number(current) > 12) {
      output.expirationYear = current.padStart(4, '20');
    } else {
      output.expirationMonth = current.padStart(2, '0');
    }

    return output;
  }, {
    expirationYear: '',
    expirationMonth: ''
  });
};
/**
 * @param {InternalDataStorageObject} credentials
 * @return {boolean}
 */


exports.getMMAndYYYYFromString = getMMAndYYYYFromString;

const shouldStoreCredentials = _ref3 => {
  let {
    credentials
  } = _ref3;
  return Boolean(credentials.password);
};
/**
 * @param {InternalDataStorageObject} credentials
 * @return {boolean}
 */


const shouldStoreIdentities = _ref4 => {
  let {
    identities
  } = _ref4;
  return Boolean((identities.firstName || identities.fullName) && identities.addressStreet && identities.addressCity);
};
/**
 * @param {InternalDataStorageObject} credentials
 * @return {boolean}
 */


const shouldStoreCreditCards = _ref5 => {
  let {
    creditCards
  } = _ref5;
  if (!creditCards.cardNumber) return false;
  if (creditCards.cardSecurityCode) return true; // Some forms (Amazon) don't have the cvv, so we still save if there's the expiration

  if (creditCards.expiration) return true; // Expiration can also be two separate values

  return Boolean(creditCards.expirationYear && creditCards.expirationMonth);
};
/**
 * Formats form data into an object to send to the device for storage
 * If values are insufficient for a complete entry, they are discarded
 * @param {InternalDataStorageObject} formValues
 * @return {DataStorageObject}
 */


const prepareFormValuesForStorage = formValues => {
  var _identities, _identities2;

  /** @type {Partial<InternalDataStorageObject>} */
  let {
    credentials,
    identities,
    creditCards
  } = formValues; // If we have an identity name but not a card name, copy it over there

  if (!creditCards.cardName && ((_identities = identities) !== null && _identities !== void 0 && _identities.fullName || (_identities2 = identities) !== null && _identities2 !== void 0 && _identities2.firstName)) {
    var _identities3;

    creditCards.cardName = ((_identities3 = identities) === null || _identities3 === void 0 ? void 0 : _identities3.fullName) || formatFullName(identities);
  }
  /** Fixes for credentials **/
  // Don't store if there isn't enough data


  if (shouldStoreCredentials(formValues)) {
    // If we don't have a username to match a password, let's see if the email is available
    if (credentials.password && !credentials.username && identities.emailAddress) {
      credentials.username = identities.emailAddress;
    }
  } else {
    credentials = undefined;
  }
  /** Fixes for identities **/
  // Don't store if there isn't enough data


  if (shouldStoreIdentities(formValues)) {
    if (identities.fullName) {
      // when forms have both first/last and fullName we keep the individual values and drop the fullName
      if (!(identities.firstName && identities.lastName)) {
        // If the fullname can be easily split into two, we'll store it as first and last
        const nameParts = identities.fullName.trim().split(/\s+/);

        if (nameParts.length === 2) {
          identities.firstName = nameParts[0];
          identities.lastName = nameParts[1];
        } else {
          // If we can't split it, just store it as first name
          identities.firstName = identities.fullName;
        }
      }

      delete identities.fullName;
    }
  } else {
    identities = undefined;
  }
  /** Fixes for credit cards **/
  // Don't store if there isn't enough data


  if (shouldStoreCreditCards(formValues)) {
    var _creditCards$expirati;

    if (creditCards.expiration) {
      const {
        expirationMonth,
        expirationYear
      } = getMMAndYYYYFromString(creditCards.expiration);
      creditCards.expirationMonth = expirationMonth;
      creditCards.expirationYear = expirationYear;
      delete creditCards.expiration;
    }

    creditCards.expirationYear = (_creditCards$expirati = creditCards.expirationYear) === null || _creditCards$expirati === void 0 ? void 0 : _creditCards$expirati.padStart(4, '20');

    if (creditCards.cardNumber) {
      creditCards.cardNumber = creditCards.cardNumber.replace(/\D/g, '');
    }
  } else {
    creditCards = undefined;
  }

  return {
    credentials,
    identities,
    creditCards
  };
};

exports.prepareFormValuesForStorage = prepareFormValuesForStorage;

},{"./countryNames":25,"./matching":33}],27:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.getIconStylesBase = exports.getIconStylesAutofilled = void 0;

var _inputTypeConfig = require("./inputTypeConfig.js");

/**
 * Returns the css-ready base64 encoding of the icon for the given input
 * @param {HTMLInputElement} input
 * @param {import("./Form").Form} form
 * @param {'base' | 'filled'} type
 * @return {string}
 */
const getIcon = function (input, form) {
  let type = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : 'base';
  const config = (0, _inputTypeConfig.getInputConfig)(input);

  if (type === 'base') {
    return config.getIconBase(input, form);
  }

  if (type === 'filled') {
    return config.getIconFilled(input, form);
  }

  return '';
};
/**
 * Returns an object with styles to be applied inline
 * @param {HTMLInputElement} input
 * @param {String} icon
 * @return {Object<string, string>}
 */


const getBasicStyles = (input, icon) => ({
  // Height must be > 0 to account for fields initially hidden
  'background-size': "auto ".concat(input.offsetHeight <= 30 && input.offsetHeight > 0 ? '100%' : '26px'),
  'background-position': 'center right',
  'background-repeat': 'no-repeat',
  'background-origin': 'content-box',
  'background-image': "url(".concat(icon, ")"),
  'transition': 'background 0s'
});
/**
 * Get inline styles for the injected icon, base state
 * @param {HTMLInputElement} input
 * @param {import("./Form").Form} form
 * @return {Object<string, string>}
 */


const getIconStylesBase = (input, form) => {
  const icon = getIcon(input, form);
  if (!icon) return {};
  return getBasicStyles(input, icon);
};
/**
 * Get inline styles for the injected icon, autofilled state
 * @param {HTMLInputElement} input
 * @param {import("./Form").Form} form
 * @return {Object<string, string>}
 */


exports.getIconStylesBase = getIconStylesBase;

const getIconStylesAutofilled = (input, form) => {
  const icon = getIcon(input, form, 'filled');
  const iconStyle = icon ? getBasicStyles(input, icon) : {};
  return { ...iconStyle,
    'background-color': '#F8F498',
    'color': '#333333'
  };
};

exports.getIconStylesAutofilled = getIconStylesAutofilled;

},{"./inputTypeConfig.js":28}],28:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.getInputConfigFromType = exports.getInputConfig = void 0;

var _logoSvg = require("./logo-svg");

var ddgPasswordIcons = _interopRequireWildcard(require("../UI/img/ddgPasswordIcon"));

var _matching = require("./matching");

var _Credentials = require("../InputTypes/Credentials");

var _CreditCard = require("../InputTypes/CreditCard");

var _Identity = require("../InputTypes/Identity");

function _getRequireWildcardCache(nodeInterop) { if (typeof WeakMap !== "function") return null; var cacheBabelInterop = new WeakMap(); var cacheNodeInterop = new WeakMap(); return (_getRequireWildcardCache = function (nodeInterop) { return nodeInterop ? cacheNodeInterop : cacheBabelInterop; })(nodeInterop); }

function _interopRequireWildcard(obj, nodeInterop) { if (!nodeInterop && obj && obj.__esModule) { return obj; } if (obj === null || typeof obj !== "object" && typeof obj !== "function") { return { default: obj }; } var cache = _getRequireWildcardCache(nodeInterop); if (cache && cache.has(obj)) { return cache.get(obj); } var newObj = {}; var hasPropertyDescriptor = Object.defineProperty && Object.getOwnPropertyDescriptor; for (var key in obj) { if (key !== "default" && Object.prototype.hasOwnProperty.call(obj, key)) { var desc = hasPropertyDescriptor ? Object.getOwnPropertyDescriptor(obj, key) : null; if (desc && (desc.get || desc.set)) { Object.defineProperty(newObj, key, desc); } else { newObj[key] = obj[key]; } } } newObj.default = obj; if (cache) { cache.set(obj, newObj); } return newObj; }

/**
 * Get the icon for the identities (currently only Dax for emails)
 * @param {HTMLInputElement} input
 * @param {import("./Form").Form} form
 * @return {string}
 */
const getIdentitiesIcon = (input, _ref) => {
  let {
    device
  } = _ref;
  // In Firefox web_accessible_resources could leak a unique user identifier, so we avoid it here
  const {
    isDDGApp,
    isFirefox,
    isWindows
  } = device.globalConfig;
  const getDaxImg = isDDGApp || isFirefox || isWindows ? _logoSvg.daxBase64 : chrome.runtime.getURL('img/logo-small.svg');
  const subtype = (0, _matching.getInputSubtype)(input);

  if (subtype === 'emailAddress') {
    // todo(Shane): Needs runtime polymorphism again here
    if (device.availableInputTypes.email) {
      return getDaxImg;
    }
  }

  return '';
};
/**
 * Inputs with readOnly or disabled should never be decorated
 * @param {HTMLInputElement} input
 * @return {boolean}
 */


const canBeDecorated = input => !input.readOnly && !input.disabled;
/**
 * A map of config objects. These help by centralising here some complexity
 * @type {InputTypeConfig}
 */


const inputTypeConfig = {
  /** @type {CredentialsInputTypeConfig} */
  credentials: {
    type: 'credentials',
    getIconBase: () => ddgPasswordIcons.ddgPasswordIconBase,
    getIconFilled: () => ddgPasswordIcons.ddgPasswordIconFilled,
    shouldDecorate: (input, _ref2) => {
      let {
        isLogin,
        device
      } = _ref2;

      // if we are on a 'login' page, continue to use old logic, eg: just checking if there's a
      // saved password
      if (isLogin) {
        return true;
      } // at this point, it's not a 'login' attempt, so we could offer to provide a password?
      // todo(Shane): move this


      if (device.autofillSettings.featureToggles.password_generation) {
        const subtype = (0, _matching.getInputSubtype)(input);

        if (subtype === 'password') {
          return true;
        }
      }

      return false;
    },
    dataType: 'Credentials',
    tooltipItem: data => (0, _Credentials.createCredentialsTooltipItem)(data)
  },

  /** @type {CreditCardsInputTypeConfig} */
  creditCards: {
    type: 'creditCards',
    getIconBase: () => '',
    getIconFilled: () => '',
    shouldDecorate: (_input, _ref3) => {
      let {
        device
      } = _ref3;
      return (// todo(Shane): shouldn't need this hasLocalCreditCards check with toggles
        canBeDecorated(_input) && device.hasLocalCreditCards
      );
    },
    dataType: 'CreditCards',
    tooltipItem: data => new _CreditCard.CreditCardTooltipItem(data)
  },

  /** @type {IdentitiesInputTypeConfig} */
  identities: {
    type: 'identities',
    getIconBase: getIdentitiesIcon,
    getIconFilled: getIdentitiesIcon,
    shouldDecorate: (_input, _ref4) => {
      let {
        device
      } = _ref4;

      if (!canBeDecorated(_input)) {
        console.warn('cannot be decorated');
        return false;
      }

      const subtype = (0, _matching.getInputSubtype)(_input); // todo(Shane): Handle the mac specific logic also with feature toggles

      if (device.globalConfig.isApp) {
        var _device$getLocalIdent;

        return Boolean((_device$getLocalIdent = device.getLocalIdentities()) === null || _device$getLocalIdent === void 0 ? void 0 : _device$getLocalIdent.some(identity => !!identity[subtype]));
      } // if it's email then we can always decorate


      if (subtype === 'emailAddress') {
        return Boolean(device.availableInputTypes.email);
      }

      return false;
    },
    dataType: 'Identities',
    tooltipItem: data => new _Identity.IdentityTooltipItem(data)
  },

  /** @type {UnknownInputTypeConfig} */
  unknown: {
    type: 'unknown',
    getIconBase: () => '',
    getIconFilled: () => '',
    shouldDecorate: () => false,
    dataType: '',
    tooltipItem: _data => {
      throw new Error('unreachable');
    }
  }
};
/**
 * Retrieves configs from an input el
 * @param {HTMLInputElement} input
 * @returns {InputTypeConfigs}
 */

const getInputConfig = input => {
  const inputType = (0, _matching.getInputType)(input);
  return getInputConfigFromType(inputType);
};
/**
 * Retrieves configs from an input type
 * @param {import('./matching').SupportedTypes | string} inputType
 * @returns {InputTypeConfigs}
 */


exports.getInputConfig = getInputConfig;

const getInputConfigFromType = inputType => {
  const inputMainType = (0, _matching.getMainTypeFromType)(inputType);
  return inputTypeConfig[inputMainType];
};

exports.getInputConfigFromType = getInputConfigFromType;

},{"../InputTypes/Credentials":36,"../InputTypes/CreditCard":37,"../InputTypes/Identity":38,"../UI/img/ddgPasswordIcon":48,"./logo-svg":31,"./matching":33}],29:[function(require,module,exports){
"use strict";

const EXCLUDED_TAGS = ['SCRIPT', 'NOSCRIPT', 'OPTION', 'STYLE'];
/**
 * Extract all strings of an element's children to an array.
 * "element.textContent" is a string which is merged of all children nodes,
 * which can cause issues with things like script tags etc.
 *
 * @param  {HTMLElement} element
 *         A DOM element to be extracted.
 * @returns {string[]}
 *          All strings in an element.
 */

const extractElementStrings = element => {
  const strings = [];

  const _extractElementStrings = el => {
    if (EXCLUDED_TAGS.includes(el.tagName)) {
      return;
    } // only take the string when it's an explicit text node


    if (el.nodeType === el.TEXT_NODE || !el.childNodes.length) {
      let trimmedText = el.textContent.trim();

      if (trimmedText) {
        strings.push(trimmedText);
      }

      return;
    }

    for (let node of el.childNodes) {
      let nodeType = node.nodeType;

      if (nodeType !== node.ELEMENT_NODE && nodeType !== node.TEXT_NODE) {
        continue;
      }

      _extractElementStrings(node);
    }
  };

  _extractElementStrings(element);

  return strings;
};

module.exports.extractElementStrings = extractElementStrings;

},{}],30:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;

/**
 * @param {Map<HTMLElement, import("./Form").Form>} forms
 */
const listenForGlobalFormSubmission = forms => {
  try {
    window.addEventListener('submit', e => {
      var _forms$get;

      return (// @ts-ignore
        (_forms$get = forms.get(e.target)) === null || _forms$get === void 0 ? void 0 : _forms$get.submitHandler()
      );
    }, true);
    window.addEventListener('keypress', e => {
      if (e.key === 'Enter') {
        const focusedForm = [...forms.values()].find(form => form.hasFocus());
        focusedForm === null || focusedForm === void 0 ? void 0 : focusedForm.submitHandler();
      }
    });
    const observer = new PerformanceObserver(list => {
      const entries = list.getEntries().filter(entry => // @ts-ignore why does TS not know about `entry.initiatorType`?
      ['fetch', 'xmlhttprequest'].includes(entry.initiatorType) && entry.name.match(/login|sign-in|signin/));
      if (!entries.length) return;
      const filledForm = [...forms.values()].find(form => form.hasValues());
      filledForm === null || filledForm === void 0 ? void 0 : filledForm.submitHandler();
    });
    observer.observe({
      entryTypes: ['resource']
    });
  } catch (error) {// Unable to detect form submissions using AJAX calls
  }
};

var _default = listenForGlobalFormSubmission;
exports.default = _default;

},{}],31:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.daxBase64 = void 0;
const daxBase64 = 'data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjI0IiB2aWV3Qm94PSIwIDAgNDQgNDQiIHdpZHRoPSIyNCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+PGxpbmVhckdyYWRpZW50IGlkPSJhIj48c3RvcCBvZmZzZXQ9Ii4wMSIgc3RvcC1jb2xvcj0iIzYxNzZiOSIvPjxzdG9wIG9mZnNldD0iLjY5IiBzdG9wLWNvbG9yPSIjMzk0YTlmIi8+PC9saW5lYXJHcmFkaWVudD48bGluZWFyR3JhZGllbnQgaWQ9ImIiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMTMuOTI5NyIgeDI9IjE3LjA3MiIgeGxpbms6aHJlZj0iI2EiIHkxPSIxNi4zOTgiIHkyPSIxNi4zOTgiLz48bGluZWFyR3JhZGllbnQgaWQ9ImMiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMjMuODExNSIgeDI9IjI2LjY3NTIiIHhsaW5rOmhyZWY9IiNhIiB5MT0iMTQuOTY3OSIgeTI9IjE0Ljk2NzkiLz48bWFzayBpZD0iZCIgaGVpZ2h0PSI0MCIgbWFza1VuaXRzPSJ1c2VyU3BhY2VPblVzZSIgd2lkdGg9IjQwIiB4PSIyIiB5PSIyIj48cGF0aCBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Im0yMi4wMDAzIDQxLjA2NjljMTAuNTMwMiAwIDE5LjA2NjYtOC41MzY0IDE5LjA2NjYtMTkuMDY2NiAwLTEwLjUzMDMtOC41MzY0LTE5LjA2NjcxLTE5LjA2NjYtMTkuMDY2NzEtMTAuNTMwMyAwLTE5LjA2NjcxIDguNTM2NDEtMTkuMDY2NzEgMTkuMDY2NzEgMCAxMC41MzAyIDguNTM2NDEgMTkuMDY2NiAxOS4wNjY3MSAxOS4wNjY2eiIgZmlsbD0iI2ZmZiIgZmlsbC1ydWxlPSJldmVub2RkIi8+PC9tYXNrPjxwYXRoIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0ibTIyIDQ0YzEyLjE1MDMgMCAyMi05Ljg0OTcgMjItMjIgMC0xMi4xNTAyNi05Ljg0OTctMjItMjItMjItMTIuMTUwMjYgMC0yMiA5Ljg0OTc0LTIyIDIyIDAgMTIuMTUwMyA5Ljg0OTc0IDIyIDIyIDIyeiIgZmlsbD0iI2RlNTgzMyIgZmlsbC1ydWxlPSJldmVub2RkIi8+PGcgbWFzaz0idXJsKCNkKSI+PHBhdGggY2xpcC1ydWxlPSJldmVub2RkIiBkPSJtMjYuMDgxMyA0MS42Mzg2Yy0uOTIwMy0xLjc4OTMtMS44MDAzLTMuNDM1Ni0yLjM0NjYtNC41MjQ2LTEuNDUyLTIuOTA3Ny0yLjkxMTQtNy4wMDctMi4yNDc3LTkuNjUwNy4xMjEtLjQ4MDMtMS4zNjc3LTE3Ljc4Njk5LTIuNDItMTguMzQ0MzItMS4xNjk3LS42MjMzMy0zLjcxMDctMS40NDQ2Ny01LjAyNy0xLjY2NDY3LS45MTY3LS4xNDY2Ni0xLjEyNTcuMTEtMS41MTA3LjE2ODY3LjM2My4wMzY2NyAyLjA5Ljg4NzMzIDIuNDIzNy45MzUtLjMzMzcuMjI3MzMtMS4zMi0uMDA3MzMtMS45NTA3LjI3MTMzLS4zMTkuMTQ2NjctLjU1NzMuNjg5MzQtLjU1Ljk0NiAxLjc5NjctLjE4MzMzIDQuNjA1NC0uMDAzNjYgNi4yNy43MzMyOS0xLjMyMzYuMTUwNC0zLjMzMy4zMTktNC4xOTgzLjc3MzctMi41MDggMS4zMi0zLjYxNTMgNC40MTEtMi45NTUzIDguMTE0My42NTYzIDMuNjk2IDMuNTY0IDE3LjE3ODQgNC40OTE2IDIxLjY4MS45MjQgNC40OTkgMTEuNTUzNyAzLjU1NjcgMTAuMDE3NC41NjF6IiBmaWxsPSIjZDVkN2Q4IiBmaWxsLXJ1bGU9ImV2ZW5vZGQiLz48cGF0aCBkPSJtMjIuMjg2NSAyNi44NDM5Yy0uNjYgMi42NDM2Ljc5MiA2LjczOTMgMi4yNDc2IDkuNjUwNi40ODkxLjk3MjcgMS4yNDM4IDIuMzkyMSAyLjA1NTggMy45NjM3LTEuODk0LjQ2OTMtNi40ODk1IDEuMTI2NC05LjcxOTEgMC0uOTI0LTQuNDkxNy0zLjgzMTctMTcuOTc3Ny00LjQ5NTMtMjEuNjgxLS42Ni0zLjcwMzMgMC02LjM0NyAyLjUxNTMtNy42NjcuODYxNy0uNDU0NyAyLjA5MzctLjc4NDcgMy40MTM3LS45MzEzLTEuNjY0Ny0uNzQwNy0zLjYzNzQtMS4wMjY3LTUuNDQxNC0uODQzMzYtLjAwNzMtLjc2MjY3IDEuMzM4NC0uNzE4NjcgMS44NDQ0LTEuMDYzMzQtLjMzMzctLjA0NzY2LTEuMTYyNC0uNzk1NjYtMS41MjktLjgzMjMzIDIuMjg4My0uMzkyNDQgNC42NDIzLS4wMjEzOCA2LjY5OSAxLjA1NiAxLjA0ODYuNTYxIDEuNzg5MyAxLjE2MjMzIDIuMjQ3NiAxLjc5MzAzIDEuMTk1NC4yMjczIDIuMjUxNC42NiAyLjk0MDcgMS4zNDkzIDIuMTE5MyAyLjExNTcgNC4wMTEzIDYuOTUyIDMuMjE5MyA5LjczMTMtLjIyMzYuNzctLjczMzMgMS4zMzEtMS4zNzEzIDEuNzk2Ny0xLjIzOTMuOTAyLTEuMDE5My0xLjA0NS00LjEwMy45NzE3LS4zOTk3LjI2MDMtLjM5OTcgMi4yMjU2LS41MjQzIDIuNzA2eiIgZmlsbD0iI2ZmZiIvPjwvZz48ZyBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGZpbGwtcnVsZT0iZXZlbm9kZCI+PHBhdGggZD0ibTE2LjY3MjQgMjAuMzU0Yy43Njc1IDAgMS4zODk2LS42MjIxIDEuMzg5Ni0xLjM4OTZzLS42MjIxLTEuMzg5Ny0xLjM4OTYtMS4zODk3LTEuMzg5Ny42MjIyLTEuMzg5NyAxLjM4OTcuNjIyMiAxLjM4OTYgMS4zODk3IDEuMzg5NnoiIGZpbGw9IiMyZDRmOGUiLz48cGF0aCBkPSJtMTcuMjkyNCAxOC44NjE3Yy4xOTg1IDAgLjM1OTQtLjE2MDguMzU5NC0uMzU5M3MtLjE2MDktLjM1OTMtLjM1OTQtLjM1OTNjLS4xOTg0IDAtLjM1OTMuMTYwOC0uMzU5My4zNTkzcy4xNjA5LjM1OTMuMzU5My4zNTkzeiIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Im0yNS45NTY4IDE5LjMzMTFjLjY1ODEgMCAxLjE5MTctLjUzMzUgMS4xOTE3LTEuMTkxNyAwLS42NTgxLS41MzM2LTEuMTkxNi0xLjE5MTctMS4xOTE2cy0xLjE5MTcuNTMzNS0xLjE5MTcgMS4xOTE2YzAgLjY1ODIuNTMzNiAxLjE5MTcgMS4xOTE3IDEuMTkxN3oiIGZpbGw9IiMyZDRmOGUiLz48cGF0aCBkPSJtMjYuNDg4MiAxOC4wNTExYy4xNzAxIDAgLjMwOC0uMTM3OS4zMDgtLjMwOHMtLjEzNzktLjMwOC0uMzA4LS4zMDgtLjMwOC4xMzc5LS4zMDguMzA4LjEzNzkuMzA4LjMwOC4zMDh6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTE3LjA3MiAxNC45NDJzLTEuMDQ4Ni0uNDc2Ni0yLjA2NDMuMTY1Yy0xLjAxNTcuNjM4LS45NzkgMS4yOTA3LS45NzkgMS4yOTA3cy0uNTM5LTEuMjAyNy44OTgzLTEuNzkzYzEuNDQxLS41ODY3IDIuMTQ1LjMzNzMgMi4xNDUuMzM3M3oiIGZpbGw9InVybCgjYikiLz48cGF0aCBkPSJtMjYuNjc1MiAxNC44NDY3cy0uNzUxNy0uNDI5LTEuMzM4My0uNDIxN2MtMS4xOTkuMDE0Ny0xLjUyNTQuNTQyNy0xLjUyNTQuNTQyN3MuMjAxNy0xLjI2MTQgMS43MzQ0LTEuMDA4NGMuNDk5Ny4wOTE0LjkyMjMuNDIzNCAxLjEyOTMuODg3NHoiIGZpbGw9InVybCgjYykiLz48cGF0aCBkPSJtMjAuOTI1OCAyNC4zMjFjLjEzOTMtLjg0MzMgMi4zMS0yLjQzMSAzLjg1LTIuNTMgMS41NC0uMDk1MyAyLjAxNjctLjA3MzMgMy4zLS4zODEzIDEuMjg3LS4zMDQzIDQuNTk4LTEuMTI5MyA1LjUxMS0xLjU1NDcuOTE2Ny0uNDIxNiA0LjgwMzMuMjA5IDIuMDY0MyAxLjczOC0xLjE4NDMuNjYzNy00LjM3OCAxLjg4MS02LjY2MjMgMi41NjMtMi4yODA3LjY4Mi0zLjY2My0uNjUyNi00LjQyMi40Njk0LS42MDEzLjg5MS0uMTIxIDIuMTEyIDIuNjAzMyAyLjM2NSAzLjY4MTQuMzQxIDcuMjA4Ny0xLjY1NzQgNy41OTc0LS41OTQuMzg4NiAxLjA2MzMtMy4xNjA3IDIuMzgzMy01LjMyNCAyLjQyNzMtMi4xNjM0LjA0MDMtNi41MTk0LTEuNDMtNy4xNzItMS44ODQ3LS42NTY0LS40NTEtMS41MjU0LTEuNTE0My0xLjM0NTctMi42MTh6IiBmaWxsPSIjZmRkMjBhIi8+PHBhdGggZD0ibTI4Ljg4MjUgMzEuODM4NmMtLjc3NzMtLjE3MjQtNC4zMTIgMi41MDA2LTQuMzEyIDIuNTAwNmguMDAzN2wtLjE2NSAyLjA1MzRzNC4wNDA2IDEuNjUzNiA0LjczIDEuMzk3Yy42ODkzLS4yNjQuNTE3LTUuNzc1LS4yNTY3LTUuOTUxem0tMTEuNTQ2MyAxLjAzNGMuMDg0My0xLjExODQgNS4yNTQzIDEuNjQyNiA1LjI1NDMgMS42NDI2bC4wMDM3LS4wMDM2LjI1NjYgMi4xNTZzLTQuMzA4MyAyLjU4MTMtNC45MTMzIDIuMjM2NmMtLjYwMTMtLjM0NDYtLjY4OTMtNC45MDk2LS42MDEzLTYuMDMxNnoiIGZpbGw9IiM2NWJjNDYiLz48cGF0aCBkPSJtMjEuMzQgMzQuODA0OWMwIDEuODA3Ny0uMjYwNCAyLjU4NS41MTMzIDIuNzU3NC43NzczLjE3MjMgMi4yNDAzIDAgMi43NjEtLjM0NDcuNTEzMy0uMzQ0Ny4wODQzLTIuNjY5My0uMDg4LTMuMTAycy0zLjE5LS4wODgtMy4xOS42ODkzeiIgZmlsbD0iIzQzYTI0NCIvPjxwYXRoIGQ9Im0yMS42NzAxIDM0LjQwNTFjMCAxLjgwNzYtLjI2MDQgMi41ODEzLjUxMzMgMi43NTM2Ljc3MzcuMTc2IDIuMjM2NyAwIDIuNzU3My0uMzQ0Ni41MTctLjM0NDcuMDg4LTIuNjY5NC0uMDg0My0zLjEwMi0uMTcyMy0uNDMyNy0zLjE5LS4wODQ0LTMuMTkuNjg5M3oiIGZpbGw9IiM2NWJjNDYiLz48cGF0aCBkPSJtMjIuMDAwMiA0MC40NDgxYzEwLjE4ODUgMCAxOC40NDc5LTguMjU5NCAxOC40NDc5LTE4LjQ0NzlzLTguMjU5NC0xOC40NDc5NS0xOC40NDc5LTE4LjQ0Nzk1LTE4LjQ0Nzk1IDguMjU5NDUtMTguNDQ3OTUgMTguNDQ3OTUgOC4yNTk0NSAxOC40NDc5IDE4LjQ0Nzk1IDE4LjQ0Nzl6bTAgMS43MTg3YzExLjEzNzcgMCAyMC4xNjY2LTkuMDI4OSAyMC4xNjY2LTIwLjE2NjYgMC0xMS4xMzc4LTkuMDI4OS0yMC4xNjY3LTIwLjE2NjYtMjAuMTY2Ny0xMS4xMzc4IDAtMjAuMTY2NyA5LjAyODktMjAuMTY2NyAyMC4xNjY3IDAgMTEuMTM3NyA5LjAyODkgMjAuMTY2NiAyMC4xNjY3IDIwLjE2NjZ6IiBmaWxsPSIjZmZmIi8+PC9nPjwvc3ZnPg==';
exports.daxBase64 = daxBase64;

},{}],32:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.matchingConfiguration = void 0;

var _selectorsCss = _interopRequireDefault(require("./selectors-css"));

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

/**
 * This is here to mimic what Remote Configuration might look like
 * later on.
 *
 * @type {MatchingConfiguration}
 */
const matchingConfiguration = {
  /** @type {MatcherConfiguration} */
  matchers: {
    fields: {
      email: {
        type: 'email',
        strategies: {
          cssSelector: 'email',
          ddgMatcher: 'email',
          vendorRegex: 'email'
        }
      },
      password: {
        type: 'password',
        strategies: {
          cssSelector: 'password',
          ddgMatcher: 'password'
        }
      },
      username: {
        type: 'username',
        strategies: {
          cssSelector: 'username',
          ddgMatcher: 'username'
        }
      },
      firstName: {
        type: 'firstName',
        strategies: {
          cssSelector: 'firstName',
          ddgMatcher: 'firstName',
          vendorRegex: 'given-name'
        }
      },
      middleName: {
        type: 'middleName',
        strategies: {
          cssSelector: 'middleName',
          ddgMatcher: 'middleName',
          vendorRegex: 'additional-name'
        }
      },
      lastName: {
        type: 'lastName',
        strategies: {
          cssSelector: 'lastName',
          ddgMatcher: 'lastName',
          vendorRegex: 'family-name'
        }
      },
      fullName: {
        type: 'fullName',
        strategies: {
          cssSelector: 'fullName',
          ddgMatcher: 'fullName',
          vendorRegex: 'name'
        }
      },
      phone: {
        type: 'phone',
        strategies: {
          cssSelector: 'phone',
          ddgMatcher: 'phone',
          vendorRegex: 'tel'
        }
      },
      addressStreet: {
        type: 'addressStreet',
        strategies: {
          cssSelector: 'addressStreet',
          ddgMatcher: 'addressStreet',
          vendorRegex: 'address-line1'
        }
      },
      addressStreet2: {
        type: 'addressStreet2',
        strategies: {
          cssSelector: 'addressStreet2',
          ddgMatcher: 'addressStreet2',
          vendorRegex: 'address-line2'
        }
      },
      addressCity: {
        type: 'addressCity',
        strategies: {
          cssSelector: 'addressCity',
          ddgMatcher: 'addressCity',
          vendorRegex: 'address-level2'
        }
      },
      addressProvince: {
        type: 'addressProvince',
        strategies: {
          cssSelector: 'addressProvince',
          ddgMatcher: 'addressProvince',
          vendorRegex: 'address-level1'
        }
      },
      addressPostalCode: {
        type: 'addressPostalCode',
        strategies: {
          cssSelector: 'addressPostalCode',
          ddgMatcher: 'addressPostalCode',
          vendorRegex: 'postal-code'
        }
      },
      addressCountryCode: {
        type: 'addressCountryCode',
        strategies: {
          cssSelector: 'addressCountryCode',
          ddgMatcher: 'addressCountryCode',
          vendorRegex: 'country'
        }
      },
      birthdayDay: {
        type: 'birthdayDay',
        strategies: {
          cssSelector: 'birthdayDay'
        }
      },
      birthdayMonth: {
        type: 'birthdayMonth',
        strategies: {
          cssSelector: 'birthdayMonth'
        }
      },
      birthdayYear: {
        type: 'birthdayYear',
        strategies: {
          cssSelector: 'birthdayYear'
        }
      },
      cardName: {
        type: 'cardName',
        strategies: {
          cssSelector: 'cardName',
          ddgMatcher: 'cardName',
          vendorRegex: 'cc-name'
        }
      },
      cardNumber: {
        type: 'cardNumber',
        strategies: {
          cssSelector: 'cardNumber',
          ddgMatcher: 'cardNumber',
          vendorRegex: 'cc-number'
        }
      },
      cardSecurityCode: {
        type: 'cardSecurityCode',
        strategies: {
          cssSelector: 'cardSecurityCode',
          ddgMatcher: 'cardSecurityCode'
        }
      },
      expirationMonth: {
        type: 'expirationMonth',
        strategies: {
          cssSelector: 'expirationMonth',
          ddgMatcher: 'expirationMonth',
          vendorRegex: 'cc-exp-month'
        }
      },
      expirationYear: {
        type: 'expirationYear',
        strategies: {
          cssSelector: 'expirationYear',
          ddgMatcher: 'expirationYear',
          vendorRegex: 'cc-exp-year'
        }
      },
      expiration: {
        type: 'expiration',
        strategies: {
          cssSelector: 'expiration',
          ddgMatcher: 'expiration',
          vendorRegex: 'cc-exp'
        }
      }
    },
    lists: {
      email: ['email'],
      password: ['password'],
      username: ['username'],
      cc: ['cardName', 'cardNumber', 'cardSecurityCode', 'expirationMonth', 'expirationYear', 'expiration'],
      id: ['firstName', 'middleName', 'lastName', 'fullName', 'phone', 'addressStreet', 'addressStreet2', 'addressCity', 'addressProvince', 'addressPostalCode', 'addressCountryCode', 'birthdayDay', 'birthdayMonth', 'birthdayYear']
    }
  },
  strategies: {
    /** @type {CssSelectorConfiguration} */
    cssSelector: {
      selectors: {
        // Generic
        FORM_INPUTS_SELECTOR: _selectorsCss.default.__secret_do_not_use.FORM_INPUTS_SELECTOR,
        SUBMIT_BUTTON_SELECTOR: _selectorsCss.default.__secret_do_not_use.SUBMIT_BUTTON_SELECTOR,
        GENERIC_TEXT_FIELD: _selectorsCss.default.__secret_do_not_use.GENERIC_TEXT_FIELD,
        // user
        email: _selectorsCss.default.__secret_do_not_use.email,
        password: _selectorsCss.default.__secret_do_not_use.password,
        username: _selectorsCss.default.__secret_do_not_use.username,
        // CC
        cardName: _selectorsCss.default.__secret_do_not_use.cardName,
        cardNumber: _selectorsCss.default.__secret_do_not_use.cardNumber,
        cardSecurityCode: _selectorsCss.default.__secret_do_not_use.cardSecurityCode,
        expirationMonth: _selectorsCss.default.__secret_do_not_use.expirationMonth,
        expirationYear: _selectorsCss.default.__secret_do_not_use.expirationYear,
        expiration: _selectorsCss.default.__secret_do_not_use.expiration,
        // Identities
        firstName: _selectorsCss.default.__secret_do_not_use.firstName,
        middleName: _selectorsCss.default.__secret_do_not_use.middleName,
        lastName: _selectorsCss.default.__secret_do_not_use.lastName,
        fullName: _selectorsCss.default.__secret_do_not_use.fullName,
        phone: _selectorsCss.default.__secret_do_not_use.phone,
        addressStreet: _selectorsCss.default.__secret_do_not_use.addressStreet1,
        addressStreet2: _selectorsCss.default.__secret_do_not_use.addressStreet2,
        addressCity: _selectorsCss.default.__secret_do_not_use.addressCity,
        addressProvince: _selectorsCss.default.__secret_do_not_use.addressProvince,
        addressPostalCode: _selectorsCss.default.__secret_do_not_use.addressPostalCode,
        addressCountryCode: _selectorsCss.default.__secret_do_not_use.addressCountryCode,
        birthdayDay: _selectorsCss.default.__secret_do_not_use.birthdayDay,
        birthdayMonth: _selectorsCss.default.__secret_do_not_use.birthdayMonth,
        birthdayYear: _selectorsCss.default.__secret_do_not_use.birthdayYear
      }
    },

    /** @type {DDGMatcherConfiguration} */
    ddgMatcher: {
      matchers: {
        email: {
          match: '.mail\\b',
          skip: 'phone',
          forceUnknown: 'search|filter|subject'
        },
        password: {
          match: 'password',
          forceUnknown: 'captcha'
        },
        username: {
          match: '(user|account|apple|login)((.)?(name|id|login).?)?$',
          forceUnknown: 'search'
        },
        // CC
        cardName: {
          match: '(card.*name|name.*card)|(card.*holder|holder.*card)|(card.*owner|owner.*card)'
        },
        cardNumber: {
          match: 'card.*number|number.*card',
          forceUnknown: 'plus'
        },
        cardSecurityCode: {
          match: 'security.?code|card.?verif|cvv|csc|cvc'
        },
        expirationMonth: {
          match: '(card|\\bcc\\b)?.?(exp(iry|iration)?)?.?(month|\\bmm\\b(?![.\\s/-]yy))',
          skip: 'mm[/\\s.\\-_—–]'
        },
        expirationYear: {
          match: '(card|\\bcc\\b)?.?(exp(iry|iration)?)?.?(year|yy)',
          skip: 'mm[/\\s.\\-_—–]'
        },
        expiration: {
          match: '(\\bmm\\b|\\b\\d\\d\\b)[/\\s.\\-_—–](\\byy|\\bjj|\\baa|\\b\\d\\d)|\\bexp|\\bvalid(idity| through| until)',
          skip: 'invalid'
        },
        // Identities
        firstName: {
          match: '(first|given|fore).?name',
          skip: 'last'
        },
        middleName: {
          match: '(middle|additional).?name'
        },
        lastName: {
          match: '(last|family|sur)[^i]?name',
          skip: 'first'
        },
        fullName: {
          match: '^(full.?|whole\\s|first.*last\\s|real\\s|contact.?)?name\\b',
          forceUnknown: 'company|org|item'
        },
        phone: {
          match: 'phone',
          skip: 'code|pass'
        },
        addressStreet: {
          match: 'address',
          forceUnknown: '\\bip\\b|duck|web|url',
          skip: 'address.*(2|two)|email|log.?in|sign.?in'
        },
        addressStreet2: {
          match: 'address.*(2|two)|apartment|\\bapt\\b|\\bflat\\b|\\bline.*(2|two)',
          forceUnknown: '\\bip\\b|duck',
          skip: 'email|log.?in|sign.?in'
        },
        addressCity: {
          match: 'city|town',
          forceUnknown: 'vatican'
        },
        addressProvince: {
          match: 'state|province|region|county',
          forceUnknown: 'united',
          skip: 'country'
        },
        addressPostalCode: {
          match: '\\bzip\\b|postal\b|post.?code'
        },
        addressCountryCode: {
          match: 'country'
        }
      }
    },

    /**
     * @type {VendorRegexConfiguration}
     */
    vendorRegex: {
      rules: {
        email: null,
        tel: null,
        organization: null,
        'street-address': null,
        'address-line1': null,
        'address-line2': null,
        'address-line3': null,
        'address-level2': null,
        'address-level1': null,
        'postal-code': null,
        country: null,
        'cc-name': null,
        name: null,
        'given-name': null,
        'additional-name': null,
        'family-name': null,
        'cc-number': null,
        'cc-exp-month': null,
        'cc-exp-year': null,
        'cc-exp': null,
        'cc-type': null
      },
      ruleSets: [//= ========================================================================
      // Firefox-specific rules
      {
        'address-line1': 'addrline1|address_1',
        'address-line2': 'addrline2|address_2',
        'address-line3': 'addrline3|address_3',
        'address-level1': 'land',
        // de-DE
        'additional-name': 'apellido.?materno|lastlastname',
        'cc-name': 'accountholdername' + '|titulaire',
        // fr-FR
        'cc-number': '(cc|kk)nr',
        // de-DE
        'cc-exp-month': '(cc|kk)month',
        // de-DE
        'cc-exp-year': '(cc|kk)year',
        // de-DE
        'cc-type': 'type' + '|kartenmarke' // de-DE

      }, //= ========================================================================
      // These are the rules used by Bitwarden [0], converted into RegExp form.
      // [0] https://github.com/bitwarden/browser/blob/c2b8802201fac5e292d55d5caf3f1f78088d823c/src/services/autofill.service.ts#L436
      {
        email: '(^e-?mail$)|(^email-?address$)',
        tel: '(^phone$)' + '|(^mobile$)' + '|(^mobile-?phone$)' + '|(^tel$)' + '|(^telephone$)' + '|(^phone-?number$)',
        organization: '(^company$)' + '|(^company-?name$)' + '|(^organization$)' + '|(^organization-?name$)',
        'street-address': '(^address$)' + '|(^street-?address$)' + '|(^addr$)' + '|(^street$)' + '|(^mailing-?addr(ess)?$)' + // Modified to not grab lines, below
        '|(^billing-?addr(ess)?$)' + // Modified to not grab lines, below
        '|(^mail-?addr(ess)?$)' + // Modified to not grab lines, below
        '|(^bill-?addr(ess)?$)',
        // Modified to not grab lines, below
        'address-line1': '(^address-?1$)' + '|(^address-?line-?1$)' + '|(^addr-?1$)' + '|(^street-?1$)',
        'address-line2': '(^address-?2$)' + '|(^address-?line-?2$)' + '|(^addr-?2$)' + '|(^street-?2$)',
        'address-line3': '(^address-?3$)' + '|(^address-?line-?3$)' + '|(^addr-?3$)' + '|(^street-?3$)',
        'address-level2': '(^city$)' + '|(^town$)' + '|(^address-?level-?2$)' + '|(^address-?city$)' + '|(^address-?town$)',
        'address-level1': '(^state$)' + '|(^province$)' + '|(^provence$)' + '|(^address-?level-?1$)' + '|(^address-?state$)' + '|(^address-?province$)',
        'postal-code': '(^postal$)' + '|(^zip$)' + '|(^zip2$)' + '|(^zip-?code$)' + '|(^postal-?code$)' + '|(^post-?code$)' + '|(^address-?zip$)' + '|(^address-?postal$)' + '|(^address-?code$)' + '|(^address-?postal-?code$)' + '|(^address-?zip-?code$)',
        country: '(^country$)' + '|(^country-?code$)' + '|(^country-?name$)' + '|(^address-?country$)' + '|(^address-?country-?name$)' + '|(^address-?country-?code$)',
        name: '(^name$)|full-?name|your-?name',
        'given-name': '(^f-?name$)' + '|(^first-?name$)' + '|(^given-?name$)' + '|(^first-?n$)',
        'additional-name': '(^m-?name$)' + '|(^middle-?name$)' + '|(^additional-?name$)' + '|(^middle-?initial$)' + '|(^middle-?n$)' + '|(^middle-?i$)',
        'family-name': '(^l-?name$)' + '|(^last-?name$)' + '|(^s-?name$)' + '|(^surname$)' + '|(^family-?name$)' + '|(^family-?n$)' + '|(^last-?n$)',
        'cc-name': 'cc-?name' + '|card-?name' + '|cardholder-?name' + '|cardholder' + // "|(^name$)" + // Removed to avoid overwriting "name", above.
        '|(^nom$)',
        'cc-number': 'cc-?number' + '|cc-?num' + '|card-?number' + '|card-?num' + '|(^number$)' + '|(^cc$)' + '|cc-?no' + '|card-?no' + '|(^credit-?card$)' + '|numero-?carte' + '|(^carte$)' + '|(^carte-?credit$)' + '|num-?carte' + '|cb-?num',
        'cc-exp': '(^cc-?exp$)' + '|(^card-?exp$)' + '|(^cc-?expiration$)' + '|(^card-?expiration$)' + '|(^cc-?ex$)' + '|(^card-?ex$)' + '|(^card-?expire$)' + '|(^card-?expiry$)' + '|(^validite$)' + '|(^expiration$)' + '|(^expiry$)' + '|mm-?yy' + '|mm-?yyyy' + '|yy-?mm' + '|yyyy-?mm' + '|expiration-?date' + '|payment-?card-?expiration' + '|(^payment-?cc-?date$)',
        'cc-exp-month': '(^exp-?month$)' + '|(^cc-?exp-?month$)' + '|(^cc-?month$)' + '|(^card-?month$)' + '|(^cc-?mo$)' + '|(^card-?mo$)' + '|(^exp-?mo$)' + '|(^card-?exp-?mo$)' + '|(^cc-?exp-?mo$)' + '|(^card-?expiration-?month$)' + '|(^expiration-?month$)' + '|(^cc-?mm$)' + '|(^cc-?m$)' + '|(^card-?mm$)' + '|(^card-?m$)' + '|(^card-?exp-?mm$)' + '|(^cc-?exp-?mm$)' + '|(^exp-?mm$)' + '|(^exp-?m$)' + '|(^expire-?month$)' + '|(^expire-?mo$)' + '|(^expiry-?month$)' + '|(^expiry-?mo$)' + '|(^card-?expire-?month$)' + '|(^card-?expire-?mo$)' + '|(^card-?expiry-?month$)' + '|(^card-?expiry-?mo$)' + '|(^mois-?validite$)' + '|(^mois-?expiration$)' + '|(^m-?validite$)' + '|(^m-?expiration$)' + '|(^expiry-?date-?field-?month$)' + '|(^expiration-?date-?month$)' + '|(^expiration-?date-?mm$)' + '|(^exp-?mon$)' + '|(^validity-?mo$)' + '|(^exp-?date-?mo$)' + '|(^cb-?date-?mois$)' + '|(^date-?m$)',
        'cc-exp-year': '(^exp-?year$)' + '|(^cc-?exp-?year$)' + '|(^cc-?year$)' + '|(^card-?year$)' + '|(^cc-?yr$)' + '|(^card-?yr$)' + '|(^exp-?yr$)' + '|(^card-?exp-?yr$)' + '|(^cc-?exp-?yr$)' + '|(^card-?expiration-?year$)' + '|(^expiration-?year$)' + '|(^cc-?yy$)' + '|(^cc-?y$)' + '|(^card-?yy$)' + '|(^card-?y$)' + '|(^card-?exp-?yy$)' + '|(^cc-?exp-?yy$)' + '|(^exp-?yy$)' + '|(^exp-?y$)' + '|(^cc-?yyyy$)' + '|(^card-?yyyy$)' + '|(^card-?exp-?yyyy$)' + '|(^cc-?exp-?yyyy$)' + '|(^expire-?year$)' + '|(^expire-?yr$)' + '|(^expiry-?year$)' + '|(^expiry-?yr$)' + '|(^card-?expire-?year$)' + '|(^card-?expire-?yr$)' + '|(^card-?expiry-?year$)' + '|(^card-?expiry-?yr$)' + '|(^an-?validite$)' + '|(^an-?expiration$)' + '|(^annee-?validite$)' + '|(^annee-?expiration$)' + '|(^expiry-?date-?field-?year$)' + '|(^expiration-?date-?year$)' + '|(^cb-?date-?ann$)' + '|(^expiration-?date-?yy$)' + '|(^expiration-?date-?yyyy$)' + '|(^validity-?year$)' + '|(^exp-?date-?year$)' + '|(^date-?y$)',
        'cc-type': '(^cc-?type$)' + '|(^card-?type$)' + '|(^card-?brand$)' + '|(^cc-?brand$)' + '|(^cb-?type$)'
      }, //= ========================================================================
      // These rules are from Chromium source codes [1]. Most of them
      // converted to JS format have the same meaning with the original ones
      // except the first line of "address-level1".
      // [1] https://source.chromium.org/chromium/chromium/src/+/master:components/autofill/core/common/autofill_regex_constants.cc
      {
        // ==== Email ====
        email: 'e.?mail' + '|courriel' + // fr
        '|correo.*electr(o|ó)nico' + // es-ES
        '|メールアドレス' + // ja-JP
        '|Электронной.?Почты' + // ru
        '|邮件|邮箱' + // zh-CN
        '|電郵地址' + // zh-TW
        '|ഇ-മെയില്‍|ഇലക്ട്രോണിക്.?' + 'മെയിൽ' + // ml
        '|ایمیل|پست.*الکترونیک' + // fa
        '|ईमेल|इलॅक्ट्रॉनिक.?मेल' + // hi
        '|(\\b|_)eposta(\\b|_)' + // tr
        '|(?:이메일|전자.?우편|[Ee]-?mail)(.?주소)?',
        // ko-KR
        // ==== Telephone ====
        tel: 'phone|mobile|contact.?number' + '|telefonnummer' + // de-DE
        '|telefono|teléfono' + // es
        '|telfixe' + // fr-FR
        '|電話' + // ja-JP
        '|telefone|telemovel' + // pt-BR, pt-PT
        '|телефон' + // ru
        '|मोबाइल' + // hi for mobile
        '|(\\b|_|\\*)telefon(\\b|_|\\*)' + // tr
        '|电话' + // zh-CN
        '|മൊബൈല്‍' + // ml for mobile
        '|(?:전화|핸드폰|휴대폰|휴대전화)(?:.?번호)?',
        // ko-KR
        // ==== Address Fields ====
        organization: 'company|business|organization|organisation' + // '|(?<!con)firma' + // de-DE // // todo: not supported in safari
        '|empresa' + // es
        '|societe|société' + // fr-FR
        '|ragione.?sociale' + // it-IT
        '|会社' + // ja-JP
        '|название.?компании' + // ru
        '|单位|公司' + // zh-CN
        '|شرکت' + // fa
        '|회사|직장',
        // ko-KR
        'street-address': 'streetaddress|street-address',
        'address-line1': '^address$|address[_-]?line[_-]?(1|one)|address1|addr1|street' + '|(?:shipping|billing)address$' + '|strasse|straße|hausnummer|housenumber' + // de-DE
        '|house.?name' + // en-GB
        '|direccion|dirección' + // es
        '|adresse' + // fr-FR
        '|indirizzo' + // it-IT
        '|^住所$|住所1' + // ja-JP
        // '|morada|((?<!identificação do )endereço)' + // pt-BR, pt-PT // todo: not supported in safari
        '|Адрес' + // ru
        '|地址' + // zh-CN
        '|(\\b|_)adres(?! (başlığı(nız)?|tarifi))(\\b|_)' + // tr
        '|^주소.?$|주소.?1',
        // ko-KR
        'address-line2': 'address[_-]?line(2|two)|address2|addr2|street|suite|unit(?!e)' + // Firefox adds `(?!e)` to unit to skip `United State`
        '|adresszusatz|ergänzende.?angaben' + // de-DE
        '|direccion2|colonia|adicional' + // es
        '|addresssuppl|complementnom|appartement' + // fr-FR
        '|indirizzo2' + // it-IT
        '|住所2' + // ja-JP
        '|complemento|addrcomplement' + // pt-BR, pt-PT
        '|Улица' + // ru
        '|地址2' + // zh-CN
        '|주소.?2',
        // ko-KR
        'address-line3': 'address[_-]?line(3|three)|address3|addr3|street|suite|unit(?!e)' + // Firefox adds `(?!e)` to unit to skip `United State`
        '|adresszusatz|ergänzende.?angaben' + // de-DE
        '|direccion3|colonia|adicional' + // es
        '|addresssuppl|complementnom|appartement' + // fr-FR
        '|indirizzo3' + // it-IT
        '|住所3' + // ja-JP
        '|complemento|addrcomplement' + // pt-BR, pt-PT
        '|Улица' + // ru
        '|地址3' + // zh-CN
        '|주소.?3',
        // ko-KR
        'address-level2': 'city|town' + '|\\bort\\b|stadt' + // de-DE
        '|suburb' + // en-AU
        '|ciudad|provincia|localidad|poblacion' + // es
        '|ville|commune' + // fr-FR
        '|localit(a|à)|citt(a|à)' + // it-IT
        '|市区町村' + // ja-JP
        '|cidade' + // pt-BR, pt-PT
        '|Город' + // ru
        '|市' + // zh-CN
        '|分區' + // zh-TW
        '|شهر' + // fa
        '|शहर' + // hi for city
        '|ग्राम|गाँव' + // hi for village
        '|നഗരം|ഗ്രാമം' + // ml for town|village
        '|((\\b|_|\\*)([İii̇]l[cç]e(miz|niz)?)(\\b|_|\\*))' + // tr
        '|^시[^도·・]|시[·・]?군[·・]?구',
        // ko-KR
        'address-level1': // '(?<!(united|hist|history).?)state|county|region|province' + // todo: not supported in safari
        'county|region|province' + '|county|principality' + // en-UK
        '|都道府県' + // ja-JP
        '|estado|provincia' + // pt-BR, pt-PT
        '|область' + // ru
        '|省' + // zh-CN
        '|地區' + // zh-TW
        '|സംസ്ഥാനം' + // ml
        '|استان' + // fa
        '|राज्य' + // hi
        '|((\\b|_|\\*)(eyalet|[şs]ehir|[İii̇]l(imiz)?|kent)(\\b|_|\\*))' + // tr
        '|^시[·・]?도',
        // ko-KR
        'postal-code': 'zip|postal|post.*code|pcode' + '|pin.?code' + // en-IN
        '|postleitzahl' + // de-DE
        '|\\bcp\\b' + // es
        '|\\bcdp\\b' + // fr-FR
        '|\\bcap\\b' + // it-IT
        '|郵便番号' + // ja-JP
        '|codigo|codpos|\\bcep\\b' + // pt-BR, pt-PT
        '|Почтовый.?Индекс' + // ru
        '|पिन.?कोड' + // hi
        '|പിന്‍കോഡ്' + // ml
        '|邮政编码|邮编' + // zh-CN
        '|郵遞區號' + // zh-TW
        '|(\\b|_)posta kodu(\\b|_)' + // tr
        '|우편.?번호',
        // ko-KR
        country: 'country|countries' + '|país|pais' + // es
        '|(\\b|_)land(\\b|_)(?!.*(mark.*))' + // de-DE landmark is a type in india.
        // '|(?<!(入|出))国' + // ja-JP // todo: not supported in safari
        '|国家' + // zh-CN
        '|국가|나라' + // ko-KR
        '|(\\b|_)(ülke|ulce|ulke)(\\b|_)' + // tr
        '|کشور',
        // fa
        // ==== Name Fields ====
        'cc-name': 'card.?(?:holder|owner)|name.*(\\b)?on(\\b)?.*card' + '|(?:card|cc).?name|cc.?full.?name' + '|karteninhaber' + // de-DE
        '|nombre.*tarjeta' + // es
        '|nom.*carte' + // fr-FR
        '|nome.*cart' + // it-IT
        '|名前' + // ja-JP
        '|Имя.*карты' + // ru
        '|信用卡开户名|开户名|持卡人姓名' + // zh-CN
        '|持卡人姓名',
        // zh-TW
        name: '^name|full.?name|your.?name|customer.?name|bill.?name|ship.?name' + '|name.*first.*last|firstandlastname' + '|nombre.*y.*apellidos' + // es
        '|^nom(?!bre)' + // fr-FR
        '|お名前|氏名' + // ja-JP
        '|^nome' + // pt-BR, pt-PT
        '|نام.*نام.*خانوادگی' + // fa
        '|姓名' + // zh-CN
        '|(\\b|_|\\*)ad[ı]? soyad[ı]?(\\b|_|\\*)' + // tr
        '|성명',
        // ko-KR
        'given-name': 'first.*name|initials|fname|first$|given.*name' + '|vorname' + // de-DE
        '|nombre' + // es
        '|forename|prénom|prenom' + // fr-FR
        '|名' + // ja-JP
        '|nome' + // pt-BR, pt-PT
        '|Имя' + // ru
        '|نام' + // fa
        '|이름' + // ko-KR
        '|പേര്' + // ml
        '|(\\b|_|\\*)(isim|ad|ad(i|ı|iniz|ınız)?)(\\b|_|\\*)' + // tr
        '|नाम',
        // hi
        'additional-name': 'middle.*name|mname|middle$|middle.*initial|m\\.i\\.|mi$|\\bmi\\b',
        'family-name': 'last.*name|lname|surname|last$|secondname|family.*name' + '|nachname' + // de-DE
        '|apellidos?' + // es
        '|famille|^nom(?!bre)' + // fr-FR
        '|cognome' + // it-IT
        '|姓' + // ja-JP
        '|apelidos|surename|sobrenome' + // pt-BR, pt-PT
        '|Фамилия' + // ru
        '|نام.*خانوادگی' + // fa
        '|उपनाम' + // hi
        '|മറുപേര്' + // ml
        '|(\\b|_|\\*)(soyisim|soyad(i|ı|iniz|ınız)?)(\\b|_|\\*)' + // tr
        '|\\b성(?:[^명]|\\b)',
        // ko-KR
        // ==== Credit Card Fields ====
        // Note: `cc-name` expression has been moved up, above `name`, in
        // order to handle specialization through ordering.
        'cc-number': '(add)?(?:card|cc|acct).?(?:number|#|no|num|field)' + // '|(?<!telefon|haus|person|fødsels)nummer' + // de-DE, sv-SE, no // todo: not supported in safari
        '|カード番号' + // ja-JP
        '|Номер.*карты' + // ru
        '|信用卡号|信用卡号码' + // zh-CN
        '|信用卡卡號' + // zh-TW
        '|카드' + // ko-KR
        // es/pt/fr
        '|(numero|número|numéro)(?!.*(document|fono|phone|réservation))',
        'cc-exp-month': // 'expir|exp.*mo|exp.*date|ccmonth|cardmonth|addmonth' + // todo: Decide if we need any of this
        'gueltig|gültig|monat' + // de-DE
        '|fecha' + // es
        '|date.*exp' + // fr-FR
        '|scadenza' + // it-IT
        '|有効期限' + // ja-JP
        '|validade' + // pt-BR, pt-PT
        '|Срок действия карты' + // ru
        '|月',
        // zh-CN
        'cc-exp-year': // 'exp|^/|(add)?year' + // todo: Decide if we need any of this
        'ablaufdatum|gueltig|gültig|jahr' + // de-DE
        '|fecha' + // es
        '|scadenza' + // it-IT
        '|有効期限' + // ja-JP
        '|validade' + // pt-BR, pt-PT
        '|Срок действия карты' + // ru
        '|年|有效期',
        // zh-CN
        'cc-exp': 'expir|exp.*date|^expfield$' + '|gueltig|gültig' + // de-DE
        '|fecha' + // es
        '|date.*exp' + // fr-FR
        '|scadenza' + // it-IT
        '|有効期限' + // ja-JP
        '|validade' + // pt-BR, pt-PT
        '|Срок действия карты' // ru

      }]
    }
  }
};
exports.matchingConfiguration = matchingConfiguration;

},{"./selectors-css":34}],33:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.checkPlaceholderAndLabels = exports.Matching = void 0;
exports.createMatching = createMatching;
exports.getInputMainType = exports.getExplicitLabelsText = void 0;
exports.getInputSubtype = getInputSubtype;
exports.getInputType = getInputType;
exports.getMainTypeFromType = getMainTypeFromType;
exports.getRelatedText = void 0;
exports.getSubtypeFromType = getSubtypeFromType;
exports.safeRegex = exports.removeExcessWhitespace = exports.matchInPlaceholderAndLabels = void 0;

var _vendorRegex = require("./vendor-regex");

var _constants = require("../constants");

var _labelUtil = require("./label-util");

var _selectorsCss = require("./selectors-css");

var _matchingConfiguration = require("./matching-configuration");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

const {
  TEXT_LENGTH_CUTOFF,
  ATTR_INPUT_TYPE
} = _constants.constants;
/**
 * An abstraction around the concept of classifying input fields.
 *
 * The only state this class keeps is derived from the passed-in MatchingConfiguration.
 */

var _config = /*#__PURE__*/new WeakMap();

var _cssSelectors = /*#__PURE__*/new WeakMap();

var _ddgMatchers = /*#__PURE__*/new WeakMap();

var _vendorRegExpCache = /*#__PURE__*/new WeakMap();

var _matcherLists = /*#__PURE__*/new WeakMap();

var _defaultStrategyOrder = /*#__PURE__*/new WeakMap();

class Matching {
  /** @type {MatchingConfiguration} */

  /** @type {CssSelectorConfiguration['selectors']} */

  /** @type {Record<string, DDGMatcher>} */

  /**
   * This acts as an internal cache for the larger vendorRegexes
   * @type {{RULES: Record<keyof VendorRegexRules, RegExp|undefined>}}
   */

  /** @type {MatcherLists} */

  /** @type {Array<StrategyNames>} */

  /** @type {Record<MatchableStrings, string>} */

  /**
   * @param {MatchingConfiguration} config
   */
  constructor(config) {
    _classPrivateFieldInitSpec(this, _config, {
      writable: true,
      value: void 0
    });

    _classPrivateFieldInitSpec(this, _cssSelectors, {
      writable: true,
      value: void 0
    });

    _classPrivateFieldInitSpec(this, _ddgMatchers, {
      writable: true,
      value: void 0
    });

    _classPrivateFieldInitSpec(this, _vendorRegExpCache, {
      writable: true,
      value: void 0
    });

    _classPrivateFieldInitSpec(this, _matcherLists, {
      writable: true,
      value: void 0
    });

    _classPrivateFieldInitSpec(this, _defaultStrategyOrder, {
      writable: true,
      value: ['cssSelector', 'ddgMatcher', 'vendorRegex']
    });

    _defineProperty(this, "activeElementStrings", {
      nameAttr: '',
      labelText: '',
      placeholderAttr: '',
      relatedText: '',
      id: ''
    });

    _defineProperty(this, "_elementStringCache", new WeakMap());

    _classPrivateFieldSet(this, _config, config);

    const {
      rules,
      ruleSets
    } = _classPrivateFieldGet(this, _config).strategies.vendorRegex;

    _classPrivateFieldSet(this, _vendorRegExpCache, (0, _vendorRegex.createCacheableVendorRegexes)(rules, ruleSets));

    _classPrivateFieldSet(this, _cssSelectors, _classPrivateFieldGet(this, _config).strategies.cssSelector.selectors);

    _classPrivateFieldSet(this, _ddgMatchers, _classPrivateFieldGet(this, _config).strategies.ddgMatcher.matchers);

    _classPrivateFieldSet(this, _matcherLists, {
      cc: [],
      id: [],
      password: [],
      username: [],
      email: []
    });
    /**
     * Convert the raw config data into actual references.
     *
     * For example this takes `email: ["email"]` and creates
     *
     * `email: [{type: "email", strategies: {cssSelector: "email", ... etc}]`
     */


    for (let [listName, matcherNames] of Object.entries(_classPrivateFieldGet(this, _config).matchers.lists)) {
      for (let fieldName of matcherNames) {
        if (!_classPrivateFieldGet(this, _matcherLists)[listName]) {
          _classPrivateFieldGet(this, _matcherLists)[listName] = [];
        }

        _classPrivateFieldGet(this, _matcherLists)[listName].push(_classPrivateFieldGet(this, _config).matchers.fields[fieldName]);
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
    const match = _classPrivateFieldGet(this, _vendorRegExpCache).RULES[regexName];

    if (!match) {
      console.warn('Vendor Regex not found for', regexName);
      return undefined;
    }

    return match;
  }
  /**
   * Try to access a 'css selector' by name from configuration
   * @param {keyof RequiredCssSelectors | string} selectorName
   * @returns {string};
   */


  cssSelector(selectorName) {
    const match = _classPrivateFieldGet(this, _cssSelectors)[selectorName];

    if (!match) {
      console.warn('CSS selector not found for %s, using a default value', selectorName);
      return '';
    }

    if (Array.isArray(match)) {
      return match.join(',');
    }

    return match;
  }
  /**
   * Try to access a 'ddg matcher' by name from configuration
   * @param {keyof RequiredCssSelectors | string} matcherName
   * @returns {DDGMatcher | undefined}
   */


  ddgMatcher(matcherName) {
    const match = _classPrivateFieldGet(this, _ddgMatchers)[matcherName];

    if (!match) {
      console.warn('DDG matcher not found for', matcherName);
      return undefined;
    }

    return match;
  }
  /**
   * Try to access a list of matchers by name - these are the ones collected in the constructor
   * @param {keyof MatcherLists} listName
   * @return {Matcher[]}
   */


  matcherList(listName) {
    const matcherList = _classPrivateFieldGet(this, _matcherLists)[listName];

    if (!matcherList) {
      console.warn('MatcherList not found for ', listName);
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
      console.warn('Matcher list not found for', listName);
      return undefined;
    }
    /**
     * @type {string[]}
     */


    const selectors = [];

    for (let matcher of matcherList) {
      if (matcher.strategies.cssSelector) {
        const css = this.cssSelector(matcher.strategies.cssSelector);

        if (css) {
          selectors.push(css);
        }
      }
    }

    return selectors.join(', ');
  }
  /**
   * Tries to infer the input type for an input
   *
   * @param {HTMLInputElement|HTMLSelectElement} input
   * @param {HTMLElement} formEl
   * @param {{isLogin?: boolean}} [opts]
   * @returns {SupportedTypes}
   */


  inferInputType(input, formEl) {
    let opts = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : {};
    const presetType = getInputType(input);

    if (presetType !== 'unknown') {
      return presetType;
    }

    this.setActiveElementStrings(input, formEl); // // For CC forms we run aggressive matches, so we want to make sure we only
    // // run them on actual CC forms to avoid false positives and expensive loops

    if (this.isCCForm(formEl)) {
      const subtype = this.subtypeFromMatchers('cc', input);

      if (subtype && isValidCreditCardSubtype(subtype)) {
        return "creditCards.".concat(subtype);
      }
    }

    if (input instanceof HTMLInputElement) {
      if (this.subtypeFromMatchers('password', input)) {
        return 'credentials.password';
      }

      if (this.subtypeFromMatchers('email', input)) {
        return opts.isLogin ? 'credentials.username' : 'identities.emailAddress';
      }

      if (this.subtypeFromMatchers('username', input)) {
        return 'credentials.username';
      }
    }

    const idSubtype = this.subtypeFromMatchers('id', input);

    if (idSubtype && isValidIdentitiesSubtype(idSubtype)) {
      return "identities.".concat(idSubtype);
    }

    return 'unknown';
  }
  /**
   * Sets the input type as a data attribute to the element and returns it
   * @param {HTMLInputElement} input
   * @param {HTMLElement} formEl
   * @param {{isLogin?: boolean}} [opts]
   * @returns {SupportedSubTypes | string}
   */


  setInputType(input, formEl) {
    let opts = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : {};
    const type = this.inferInputType(input, formEl, opts);
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
    /**
     * Loop through each strategy in order
     */

    for (let strategyName of _classPrivateFieldGet(this, _defaultStrategyOrder)) {
      /**
       * Now loop through each matcher in the list.
       */
      for (let matcher of matchers) {
        var _result, _result2, _result3;

        /**
         * for each `strategyName` (such as cssSelector), check
         * if the current matcher implements it.
         */
        const lookup = matcher.strategies[strategyName];
        /**
         * Sometimes a matcher may not implement the current strategy,
         * so we skip it
         */

        if (!lookup) continue;
        /**
         * Now perform the matching
         */

        let result;

        if (strategyName === 'cssSelector') {
          result = this.execCssSelector(lookup, el);
        }

        if (strategyName === 'ddgMatcher') {
          result = this.execDDGMatcher(lookup);
        }

        if (strategyName === 'vendorRegex') {
          result = this.execVendorRegex(lookup);
        }
        /**
         * If there's a match, return the matcher type.
         *
         * So, for example if 'username' had a `cssSelector` implemented, and
         * it matched the current element, then we'd return 'username'
         */


        if ((_result = result) !== null && _result !== void 0 && _result.matched) {
          return matcher.type;
        }
        /**
         * If a matcher wants to prevent all future matching on this element,
         * it would return { matched: false, proceed: false }
         */


        if (!((_result2 = result) !== null && _result2 !== void 0 && _result2.matched) && ((_result3 = result) === null || _result3 === void 0 ? void 0 : _result3.proceed) === false) {
          // If we get here, do not allow subsequent strategies to continue
          return undefined;
        }
      }
    }

    return undefined;
  }
  /**
   * CSS selector matching just leverages the `.matches` method on elements
   *
   * @param {string} lookup
   * @param {HTMLInputElement|HTMLSelectElement} el
   * @returns {MatchingResult}
   */


  execCssSelector(lookup, el) {
    const selector = this.cssSelector(lookup);
    return {
      matched: el.matches(selector)
    };
  }
  /**
   * A DDG Matcher can have a `match` regex along with a `not` regex. This is done
   * to allow it to be driven by configuration as it avoids needing to invoke custom functions.
   *
   * todo: maxDigits was added as an edge-case when converting this over to be declarative, but I'm
   * unsure if it's actually needed. It's not urgent, but we should consider removing it if that's the case
   *
   * @param {string} lookup
   * @returns {MatchingResult}
   */


  execDDGMatcher(lookup) {
    const ddgMatcher = this.ddgMatcher(lookup);

    if (!ddgMatcher || !ddgMatcher.match) {
      return {
        matched: false
      };
    }

    let matchRexExp = safeRegex(ddgMatcher.match || '');

    if (!matchRexExp) {
      return {
        matched: false
      };
    }

    let requiredScore = ['match', 'forceUnknown', 'maxDigits'].filter(ddgMatcherProp => ddgMatcherProp in ddgMatcher).length;
    /** @type {MatchableStrings[]} */

    const matchableStrings = ddgMatcher.matchableStrings || ['labelText', 'placeholderAttr', 'relatedText'];

    for (let stringName of matchableStrings) {
      let elementString = this.activeElementStrings[stringName];
      if (!elementString) continue;
      elementString = elementString.toLowerCase(); // Scoring to ensure all DDG tests are valid

      let score = 0; // If a negated regex was provided, ensure it does not match
      // If it DOES match - then we need to prevent any future strategies from continuing

      if (ddgMatcher.forceUnknown) {
        let notRegex = safeRegex(ddgMatcher.forceUnknown);

        if (!notRegex) {
          return {
            matched: false
          };
        }

        if (notRegex.test(elementString)) {
          return {
            matched: false,
            proceed: false
          };
        } else {
          // All good here, increment the score
          score++;
        }
      }

      if (ddgMatcher.skip) {
        let skipRegex = safeRegex(ddgMatcher.skip);

        if (!skipRegex) {
          return {
            matched: false
          };
        }

        if (skipRegex.test(elementString)) {
          continue;
        }
      } // if the `match` regex fails, moves onto the next string


      if (!matchRexExp.test(elementString)) {
        continue;
      } // Otherwise, increment the score


      score++; // If a 'maxDigits' rule was provided, validate it

      if (ddgMatcher.maxDigits) {
        const digitLength = elementString.replace(/[^0-9]/g, '').length;

        if (digitLength > ddgMatcher.maxDigits) {
          return {
            matched: false
          };
        } else {
          score++;
        }
      }

      if (score === requiredScore) {
        return {
          matched: true
        };
      }
    }

    return {
      matched: false
    };
  }
  /**
   * If we get here, a firefox/vendor regex was given and we can execute it on the element
   * strings
   * @param {string} lookup
   * @return {MatchingResult}
   */


  execVendorRegex(lookup) {
    const regex = this.vendorRegex(lookup);

    if (!regex) {
      return {
        matched: false
      };
    }
    /** @type {MatchableStrings[]} */


    const stringsToMatch = ['placeholderAttr', 'nameAttr', 'labelText', 'id', 'relatedText'];

    for (let stringName of stringsToMatch) {
      let elementString = this.activeElementStrings[stringName];
      if (!elementString) continue;
      elementString = elementString.toLowerCase();

      if (regex.test(elementString)) {
        return {
          matched: true
        };
      }
    }

    return {
      matched: false
    };
  }
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


  getElementStrings(el, form) {
    if (this._elementStringCache.has(el)) {
      return this._elementStringCache.get(el);
    }
    /** @type {Record<MatchableStrings, string>} */


    const next = {
      nameAttr: el.name,
      labelText: getExplicitLabelsText(el),
      placeholderAttr: el.placeholder || '',
      id: el.id,
      relatedText: getRelatedText(el, form, this.cssSelector('FORM_INPUTS_SELECTOR'))
    };

    this._elementStringCache.set(el, next);

    return next;
  }

  clear() {
    this._elementStringCache = new WeakMap();
  }
  /**
   * @param {HTMLInputElement|HTMLSelectElement} input
   * @param {HTMLElement} form
   * @returns {Matching}
   */


  forInput(input, form) {
    this.setActiveElementStrings(input, form);
    return this;
  }
  /**
   * Tries to infer if it's a credit card form
   * @param {HTMLElement} formEl
   * @returns {boolean}
   */


  isCCForm(formEl) {
    var _formEl$textContent;

    const ccFieldSelector = this.joinCssSelectors('cc');

    if (!ccFieldSelector) {
      return false;
    }

    const hasCCSelectorChild = formEl.querySelector(ccFieldSelector); // If the form contains one of the specific selectors, we have high confidence

    if (hasCCSelectorChild) return true; // Read form attributes to find a signal

    const hasCCAttribute = [...formEl.attributes].some(_ref => {
      let {
        name,
        value
      } = _ref;
      return /(credit|payment).?card/i.test("".concat(name, "=").concat(value));
    });
    if (hasCCAttribute) return true; // Match form textContent against common cc fields (includes hidden labels)

    const textMatches = (_formEl$textContent = formEl.textContent) === null || _formEl$textContent === void 0 ? void 0 : _formEl$textContent.match(/(credit)?card(.?number)?|ccv|security.?code|cvv|cvc|csc/ig); // We check for more than one to minimise false positives

    return Boolean(textMatches && textMatches.length > 1);
  }
  /**
   * @type {MatchingConfiguration}
   */


}
/**
 *  @returns {SupportedTypes}
 */


exports.Matching = Matching;

_defineProperty(Matching, "emptyConfig", {
  matchers: {
    lists: {},
    fields: {}
  },
  strategies: {
    'vendorRegex': {
      rules: {},
      ruleSets: []
    },
    'ddgMatcher': {
      matchers: {}
    },
    'cssSelector': {
      selectors: {
        FORM_INPUTS_SELECTOR: _selectorsCss.FORM_INPUTS_SELECTOR
      }
    }
  }
});

function getInputType(input) {
  const attr = input.getAttribute(ATTR_INPUT_TYPE);

  if (isValidSupportedType(attr)) {
    return attr;
  }

  return 'unknown';
}
/**
 * Retrieves the main type
 * @param {SupportedTypes | string} type
 * @returns {SupportedMainTypes}
 */


function getMainTypeFromType(type) {
  const mainType = type.split('.')[0];

  switch (mainType) {
    case 'credentials':
    case 'creditCards':
    case 'identities':
      return mainType;
  }

  return 'unknown';
}
/**
 * Retrieves the input main type
 * @param {HTMLInputElement} input
 * @returns {SupportedMainTypes}
 */


const getInputMainType = input => getMainTypeFromType(getInputType(input));
/** @typedef {supportedIdentitiesSubtypes[number]} SupportedIdentitiesSubTypes */


exports.getInputMainType = getInputMainType;
const supportedIdentitiesSubtypes =
/** @type {const} */
['emailAddress', 'firstName', 'middleName', 'lastName', 'fullName', 'phone', 'addressStreet', 'addressStreet2', 'addressCity', 'addressProvince', 'addressPostalCode', 'addressCountryCode', 'birthdayDay', 'birthdayMonth', 'birthdayYear'];
/**
 * @param {SupportedTypes | any} supportedType
 * @returns {supportedType is SupportedIdentitiesSubTypes}
 */

function isValidIdentitiesSubtype(supportedType) {
  return supportedIdentitiesSubtypes.includes(supportedType);
}
/** @typedef {supportedCreditCardSubtypes[number]} SupportedCreditCardSubTypes */


const supportedCreditCardSubtypes =
/** @type {const} */
['cardName', 'cardNumber', 'cardSecurityCode', 'expirationMonth', 'expirationYear', 'expiration'];
/**
 * @param {SupportedTypes | any} supportedType
 * @returns {supportedType is SupportedCreditCardSubTypes}
 */

function isValidCreditCardSubtype(supportedType) {
  return supportedCreditCardSubtypes.includes(supportedType);
}
/** @typedef {supportedCredentialsSubtypes[number]} SupportedCredentialsSubTypes */


const supportedCredentialsSubtypes =
/** @type {const} */
['password', 'username'];
/**
 * @param {SupportedTypes | any} supportedType
 * @returns {supportedType is SupportedCredentialsSubTypes}
 */

function isValidCredentialsSubtype(supportedType) {
  return supportedCredentialsSubtypes.includes(supportedType);
}
/** @typedef {SupportedIdentitiesSubTypes | SupportedCreditCardSubTypes | SupportedCredentialsSubTypes} SupportedSubTypes */

/** @typedef {`identities.${SupportedIdentitiesSubTypes}` | `creditCards.${SupportedCreditCardSubTypes}` | `credentials.${SupportedCredentialsSubTypes}` | 'unknown'} SupportedTypes */


const supportedTypes = [...supportedIdentitiesSubtypes.map(type => "identities.".concat(type)), ...supportedCreditCardSubtypes.map(type => "creditCards.".concat(type)), ...supportedCredentialsSubtypes.map(type => "credentials.".concat(type))];
/**
 * Retrieves the subtype
 * @param {SupportedTypes | string} type
 * @returns {SupportedSubTypes | 'unknown'}
 */

function getSubtypeFromType(type) {
  const subType = type === null || type === void 0 ? void 0 : type.split('.')[1];
  const validType = isValidSubtype(subType);
  return validType ? subType : 'unknown';
}
/**
 * @param {SupportedSubTypes | any} supportedSubType
 * @returns {supportedSubType is SupportedSubTypes}
 */


function isValidSubtype(supportedSubType) {
  return isValidIdentitiesSubtype(supportedSubType) || isValidCreditCardSubtype(supportedSubType) || isValidCredentialsSubtype(supportedSubType);
}
/**
 * @param {SupportedTypes | any} supportedType
 * @returns {supportedType is SupportedTypes}
 */


function isValidSupportedType(supportedType) {
  return supportedTypes.includes(supportedType);
}
/**
 * Retrieves the input subtype
 * @param {HTMLInputElement|Element} input
 * @returns {SupportedSubTypes | 'unknown'}
 */


function getInputSubtype(input) {
  const type = getInputType(input);
  return getSubtypeFromType(type);
}
/**
 * Remove whitespace of more than 2 in a row and trim the string
 * @param string
 * @return {string}
 */


const removeExcessWhitespace = function () {
  let string = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : '';
  return string.replace(/\n/g, ' ').replace(/\s{2,}/, ' ').trim();
};
/**
 * Get text from all explicit labels
 * @param {HTMLInputElement|HTMLSelectElement} el
 * @return {string}
 */


exports.removeExcessWhitespace = removeExcessWhitespace;

const getExplicitLabelsText = el => {
  const labelTextCandidates = [];

  for (let label of el.labels || []) {
    labelTextCandidates.push(...(0, _labelUtil.extractElementStrings)(label));
  }

  if (el.hasAttribute('aria-label')) {
    labelTextCandidates.push(el.getAttribute('aria-label'));
  } // Try to access another element if it was marked as the label for this input/select


  const ariaLabelAttr = el.getAttribute('aria-labelled') || el.getAttribute('aria-labelledby');

  if (ariaLabelAttr) {
    const labelledByElement = document.getElementById(ariaLabelAttr);

    if (labelledByElement) {
      labelTextCandidates.push(...(0, _labelUtil.extractElementStrings)(labelledByElement));
    }
  }

  if (labelTextCandidates.length > 0) {
    return removeExcessWhitespace(labelTextCandidates.join(' '));
  }

  return '';
};
/**
 * Get all text close to the input (useful when no labels are defined)
 * @param {HTMLInputElement|HTMLSelectElement} el
 * @param {HTMLElement} form
 * @param {string} cssSelector
 * @return {string}
 */


exports.getExplicitLabelsText = getExplicitLabelsText;

const getRelatedText = (el, form, cssSelector) => {
  const container = getLargestMeaningfulContainer(el, form, cssSelector); // If there is no meaningful container return empty string

  if (container === el || container.nodeName === 'SELECT') return ''; // If the container has a select element, remove its contents to avoid noise

  const text = removeExcessWhitespace((0, _labelUtil.extractElementStrings)(container).join(' ')); // If the text is longer than n chars it's too noisy and likely to yield false positives, so return ''

  if (text.length < TEXT_LENGTH_CUTOFF) return text;
  return '';
};
/**
 * Find a container for the input field that won't contain other inputs (useful to get elements related to the field)
 * @param {HTMLElement} el
 * @param {HTMLElement} form
 * @param {string} cssSelector
 * @return {HTMLElement}
 */


exports.getRelatedText = getRelatedText;

const getLargestMeaningfulContainer = (el, form, cssSelector) => {
  /* TODO: there could be more than one select el for the same label, in that case we should
      change how we compute the container */
  const parentElement = el.parentElement;
  if (!parentElement || el === form) return el;
  const inputsInParentsScope = parentElement.querySelectorAll(cssSelector); // To avoid noise, ensure that our input is the only in scope

  if (inputsInParentsScope.length === 1) {
    return getLargestMeaningfulContainer(parentElement, form, cssSelector);
  }

  return el;
};
/**
 * Find a regex match for a given input
 * @param {HTMLInputElement} input
 * @param {RegExp} regex
 * @param {HTMLElement} form
 * @param {string} cssSelector
 * @returns {RegExpMatchArray|null}
 */


const matchInPlaceholderAndLabels = (input, regex, form, cssSelector) => {
  var _input$placeholder;

  return ((_input$placeholder = input.placeholder) === null || _input$placeholder === void 0 ? void 0 : _input$placeholder.match(regex)) || getExplicitLabelsText(input).match(regex) || getRelatedText(input, form, cssSelector).match(regex);
};
/**
 * Check if a given input matches a regex
 * @param {HTMLInputElement} input
 * @param {RegExp} regex
 * @param {HTMLElement} form
 * @param {string} cssSelector
 * @returns {boolean}
 */


exports.matchInPlaceholderAndLabels = matchInPlaceholderAndLabels;

const checkPlaceholderAndLabels = (input, regex, form, cssSelector) => {
  return !!matchInPlaceholderAndLabels(input, regex, form, cssSelector);
};
/**
 * Creating Regex instances can throw, so we add this to be
 * @param {string} string
 * @returns {RegExp | undefined} string
 */


exports.checkPlaceholderAndLabels = checkPlaceholderAndLabels;

const safeRegex = string => {
  try {
    // This is lower-cased here because giving a `i` on a regex flag is a performance problem in some cases
    const input = String(string).toLowerCase().normalize('NFKC');
    return new RegExp(input, 'u');
  } catch (e) {
    console.warn('Could not generate regex from string input', string);
    return undefined;
  }
};
/**
 * Factory for instances of Matching
 *
 * @return {Matching}
 */


exports.safeRegex = safeRegex;

function createMatching() {
  return new Matching(_matchingConfiguration.matchingConfiguration);
}

},{"../constants":54,"./label-util":29,"./matching-configuration":32,"./selectors-css":34,"./vendor-regex":35}],34:[function(require,module,exports){
"use strict";

const FORM_INPUTS_SELECTOR = "\ninput:not([type=submit]):not([type=button]):not([type=checkbox]):not([type=radio]):not([type=hidden]):not([type=file]),\nselect";
const SUBMIT_BUTTON_SELECTOR = "\ninput[type=submit],\ninput[type=button],\nbutton:not([role=switch]):not([role=link]),\n[role=button]";
const email = "\ninput:not([type])[name*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput[type=\"\"][name*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput[type=text][name*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput:not([type])[placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput[type=text][placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput[type=\"\"][placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput:not([type])[placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput[type=email],\ninput[type=text][aria-label*=mail i]:not([aria-label*=search i]),\ninput:not([type])[aria-label*=mail i]:not([aria-label*=search i]),\ninput[type=text][placeholder*=mail i]:not([placeholder*=search i]):not([placeholder*=filter i]):not([placeholder*=subject i]),\ninput[name=username][type=email],\ninput[autocomplete=email]"; // We've seen non-standard types like 'user'. This selector should get them, too

const GENERIC_TEXT_FIELD = "\ninput:not([type=button]):not([type=checkbox]):not([type=color]):not([type=date]):not([type=datetime-local]):not([type=datetime]):not([type=file]):not([type=hidden]):not([type=month]):not([type=number]):not([type=radio]):not([type=range]):not([type=reset]):not([type=search]):not([type=submit]):not([type=time]):not([type=url]):not([type=week])";
const password = "input[type=password]:not([autocomplete*=cc]):not([autocomplete=one-time-code]):not([name*=answer i])";
const cardName = "\ninput[autocomplete=\"cc-name\"],\ninput[autocomplete=\"ccname\"],\ninput[name=\"ccname\"],\ninput[name=\"cc-name\"],\ninput[name=\"ppw-accountHolderName\"],\ninput[id*=cardname i],\ninput[id*=card-name i],\ninput[id*=card_name i]";
const cardNumber = "\ninput[autocomplete=\"cc-number\"],\ninput[autocomplete=\"ccnumber\"],\ninput[autocomplete=\"cardnumber\"],\ninput[autocomplete=\"card-number\"],\ninput[name=\"ccnumber\"],\ninput[name=\"cc-number\"],\ninput[name*=card i][name*=number i],\ninput[id*=cardnumber i],\ninput[id*=card-number i],\ninput[id*=card_number i]";
const cardSecurityCode = "\ninput[autocomplete=\"cc-csc\"],\ninput[autocomplete=\"csc\"],\ninput[autocomplete=\"cc-cvc\"],\ninput[autocomplete=\"cvc\"],\ninput[name=\"cvc\"],\ninput[name=\"cc-cvc\"],\ninput[name=\"cc-csc\"],\ninput[name=\"csc\"],\ninput[name*=security i][name*=code i]";
const expirationMonth = "\n[autocomplete=\"cc-exp-month\"],\n[name=\"ccmonth\"],\n[name=\"ppw-expirationDate_month\"],\n[name=cardExpiryMonth],\n[name*=ExpDate_Month i],\n[name*=expiration i][name*=month i],\n[id*=expiration i][id*=month i]";
const expirationYear = "\n[autocomplete=\"cc-exp-year\"],\n[name=\"ccyear\"],\n[name=\"ppw-expirationDate_year\"],\n[name=cardExpiryYear],\n[name*=ExpDate_Year i],\n[name*=expiration i][name*=year i],\n[id*=expiration i][id*=year i]";
const expiration = "\n[autocomplete=\"cc-exp\"],\n[name=\"cc-exp\"],\n[name=\"exp-date\"],\n[name=\"expirationDate\"],\ninput[id*=expiration i]";
const firstName = "\n[name*=fname i], [autocomplete*=given-name i],\n[name*=firstname i], [autocomplete*=firstname i],\n[name*=first-name i], [autocomplete*=first-name i],\n[name*=first_name i], [autocomplete*=first_name i],\n[name*=givenname i], [autocomplete*=givenname i],\n[name*=given-name i],\n[name*=given_name i], [autocomplete*=given_name i],\n[name*=forename i], [autocomplete*=forename i]";
const middleName = "\n[name*=mname i], [autocomplete*=additional-name i],\n[name*=middlename i], [autocomplete*=middlename i],\n[name*=middle-name i], [autocomplete*=middle-name i],\n[name*=middle_name i], [autocomplete*=middle_name i],\n[name*=additionalname i], [autocomplete*=additionalname i],\n[name*=additional-name i],\n[name*=additional_name i], [autocomplete*=additional_name i]";
const lastName = "\n[name=lname], [autocomplete*=family-name i],\n[name*=lastname i], [autocomplete*=lastname i],\n[name*=last-name i], [autocomplete*=last-name i],\n[name*=last_name i], [autocomplete*=last_name i],\n[name*=familyname i], [autocomplete*=familyname i],\n[name*=family-name i],\n[name*=family_name i], [autocomplete*=family_name i],\n[name*=surname i], [autocomplete*=surname i]";
const fullName = "\n[name=name], [autocomplete=name],\n[name*=fullname i], [autocomplete*=fullname i],\n[name*=full-name i], [autocomplete*=full-name i],\n[name*=full_name i], [autocomplete*=full_name i],\n[name*=your-name i], [autocomplete*=your-name i]";
const phone = "\n[name*=phone i], [name*=mobile i], [autocomplete=tel], [placeholder*=\"phone number\" i]";
const addressStreet1 = "\n[name=address], [autocomplete=street-address], [autocomplete=address-line1],\n[name=street],\n[name=ppw-line1], [name*=addressLine1 i]";
const addressStreet2 = "\n[name=address], [autocomplete=address-line2],\n[name=ppw-line2], [name*=addressLine2 i]";
const addressCity = "\n[name=city], [autocomplete=address-level2],\n[name=ppw-city], [name*=addressCity i]";
const addressProvince = "\n[name=province], [name=state], [autocomplete=address-level1]";
const addressPostalCode = "\n[name=zip], [name=zip2], [name=postal], [autocomplete=postal-code], [autocomplete=zip-code],\n[name*=postalCode i], [name*=zipcode i]";
const addressCountryCode = ["[name=country], [autocomplete=country],\n     [name*=countryCode i], [name*=country-code i],\n     [name*=countryName i], [name*=country-name i]", "select.idms-address-country" // Fix for Apple signup
];
const birthdayDay = "\n[name=bday-day],\n[name=birthday_day], [name=birthday-day],\n[name=date_of_birth_day], [name=date-of-birth-day],\n[name^=birthdate_d], [name^=birthdate-d]";
const birthdayMonth = "\n[name=bday-month],\n[name=birthday_month], [name=birthday-month],\n[name=date_of_birth_month], [name=date-of-birth-month],\n[name^=birthdate_m], [name^=birthdate-m]";
const birthdayYear = "\n[name=bday-year],\n[name=birthday_year], [name=birthday-year],\n[name=date_of_birth_year], [name=date-of-birth-year],\n[name^=birthdate_y], [name^=birthdate-y]";
const username = ["".concat(GENERIC_TEXT_FIELD, "[autocomplete^=user]"), "input[name=username]", // fix for `aa.com`
"input[name=\"loginId\"]", // fix for https://online.mbank.pl/pl/Login
"input[name=\"userID\"]", "input[id=\"login-id\"]", "input[name=accountname]"]; // todo: these are still used directly right now, mostly in scanForInputs
// todo: ensure these can be set via configuration

module.exports.FORM_INPUTS_SELECTOR = FORM_INPUTS_SELECTOR;
module.exports.SUBMIT_BUTTON_SELECTOR = SUBMIT_BUTTON_SELECTOR; // Exported here for now, to be moved to configuration later

module.exports.__secret_do_not_use = {
  GENERIC_TEXT_FIELD,
  SUBMIT_BUTTON_SELECTOR,
  FORM_INPUTS_SELECTOR,
  email: email,
  password,
  username,
  cardName,
  cardNumber,
  cardSecurityCode,
  expirationMonth,
  expirationYear,
  expiration,
  firstName,
  middleName,
  lastName,
  fullName,
  phone,
  addressStreet1,
  addressStreet2,
  addressCity,
  addressProvince,
  addressPostalCode,
  addressCountryCode,
  birthdayDay,
  birthdayMonth,
  birthdayYear
};

},{}],35:[function(require,module,exports){
"use strict";

/**
 * Given some ruleSets, create an efficient
 * lookup system for accessing cached regexes by name.
 *
 * @param {VendorRegexConfiguration["rules"]} rules
 * @param {VendorRegexConfiguration["ruleSets"]} ruleSets
 * @return {{RULES: Record<keyof VendorRegexRules, RegExp | undefined>}}
 */
function createCacheableVendorRegexes(rules, ruleSets) {
  const vendorRegExp = {
    RULES: rules,
    RULE_SETS: ruleSets,

    _getRule(name) {
      let rules = [];
      this.RULE_SETS.forEach(set => {
        if (set[name]) {
          var _set$name;

          // Add the rule.
          // We make the regex lower case so that we can match it against the
          // lower-cased field name and get a rough equivalent of a case-insensitive
          // match. This avoids a performance cliff with the "iu" flag on regular
          // expressions.
          rules.push("(".concat((_set$name = set[name]) === null || _set$name === void 0 ? void 0 : _set$name.toLowerCase(), ")").normalize('NFKC'));
        }
      });
      const value = new RegExp(rules.join('|'), 'u');
      Object.defineProperty(this.RULES, name, {
        get: undefined
      });
      Object.defineProperty(this.RULES, name, {
        value
      });
      return value;
    },

    init() {
      Object.keys(this.RULES).forEach(field => Object.defineProperty(this.RULES, field, {
        get() {
          return vendorRegExp._getRule(field);
        }

      }));
    }

  };
  vendorRegExp.init(); // @ts-ignore

  return vendorRegExp;
}

module.exports.createCacheableVendorRegexes = createCacheableVendorRegexes;

},{}],36:[function(require,module,exports){
"use strict";

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

const AUTOGENERATED_KEY = 'autogenerated';
/**
 * @implements {TooltipItemRenderer}
 */

var _data = /*#__PURE__*/new WeakMap();

class CredentialsTooltipItem {
  /** @type {CredentialsObject} */

  /** @param {CredentialsObject} data */
  constructor(data) {
    _classPrivateFieldInitSpec(this, _data, {
      writable: true,
      value: void 0
    });

    _defineProperty(this, "id", () => String(_classPrivateFieldGet(this, _data).id));

    _defineProperty(this, "labelMedium", _subtype => _classPrivateFieldGet(this, _data).username);

    _defineProperty(this, "labelSmall", _subtype => '•••••••••••••••');

    _classPrivateFieldSet(this, _data, data);
  }

}
/**
 * @implements {TooltipItemRenderer}
 */


var _data2 = /*#__PURE__*/new WeakMap();

class AutoGeneratedCredential {
  /** @type {CredentialsObject} */

  /** @param {CredentialsObject} data */
  constructor(data) {
    _classPrivateFieldInitSpec(this, _data2, {
      writable: true,
      value: void 0
    });

    _defineProperty(this, "id", () => String(_classPrivateFieldGet(this, _data2).id));

    _defineProperty(this, "label", _subtype => _classPrivateFieldGet(this, _data2).password);

    _defineProperty(this, "labelMedium", _subtype => 'Generated password');

    _defineProperty(this, "labelSmall", _subtype => 'Login information will be saved for this website');

    _classPrivateFieldSet(this, _data2, data);
  }

}
/**
 * Generate a stand-in 'CredentialsObject' from a
 * given (generated) password.
 *
 * @param {string} password
 * @returns {CredentialsObject}
 */


function fromPassword(password) {
  return {
    [AUTOGENERATED_KEY]: true,
    password: password,
    username: ''
  };
}
/**
 * If the locally generated/stored password ends up being the same
 * as submitted in a subsequent form submission - then we mark the
 * credentials as 'autogenerated' so that the native layer can decide
 * how to process it
 *
 * @type {PreRequest<DataStorageObject, string|null>}
 */


function appendGeneratedId(data, generatedPassword) {
  var _data$credentials;

  if (generatedPassword && ((_data$credentials = data.credentials) === null || _data$credentials === void 0 ? void 0 : _data$credentials.password) === generatedPassword) {
    return { ...data,
      credentials: { ...data.credentials,
        [AUTOGENERATED_KEY]: true
      }
    };
  }

  return data;
}
/**
 * Factory for creating a TooltipItemRenderer
 *
 * @param {CredentialsObject} data
 * @returns {TooltipItemRenderer}
 */


function createCredentialsTooltipItem(data) {
  if (AUTOGENERATED_KEY in data && data.password) {
    return new AutoGeneratedCredential(data);
  }

  return new CredentialsTooltipItem(data);
}

module.exports.createCredentialsTooltipItem = createCredentialsTooltipItem;
module.exports.fromPassword = fromPassword;
module.exports.appendGeneratedId = appendGeneratedId;
module.exports.AUTOGENERATED_KEY = AUTOGENERATED_KEY;

},{}],37:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.CreditCardTooltipItem = void 0;

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

var _data = /*#__PURE__*/new WeakMap();

/**
 * @implements {TooltipItemRenderer}
 */
class CreditCardTooltipItem {
  /** @type {CreditCardObject} */

  /** @param {CreditCardObject} data */
  constructor(data) {
    _classPrivateFieldInitSpec(this, _data, {
      writable: true,
      value: void 0
    });

    _defineProperty(this, "id", () => String(_classPrivateFieldGet(this, _data).id));

    _defineProperty(this, "labelMedium", _ => _classPrivateFieldGet(this, _data).title);

    _defineProperty(this, "labelSmall", _ => _classPrivateFieldGet(this, _data).displayNumber);

    _classPrivateFieldSet(this, _data, data);
  }

}

exports.CreditCardTooltipItem = CreditCardTooltipItem;

},{}],38:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.IdentityTooltipItem = void 0;

var _formatters = require("../Form/formatters");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

var _data = /*#__PURE__*/new WeakMap();

/**
 * @implements {TooltipItemRenderer}
 */
class IdentityTooltipItem {
  /** @type {IdentityObject} */

  /** @param {IdentityObject} data */
  constructor(data) {
    _classPrivateFieldInitSpec(this, _data, {
      writable: true,
      value: void 0
    });

    _defineProperty(this, "id", () => String(_classPrivateFieldGet(this, _data).id));

    _defineProperty(this, "labelMedium", subtype => {
      if (subtype === 'addressCountryCode') {
        return (0, _formatters.getCountryDisplayName)('en', _classPrivateFieldGet(this, _data).addressCountryCode || '');
      }

      if (_classPrivateFieldGet(this, _data).id === 'privateAddress') {
        return 'Generated Private Duck Address';
      }

      return _classPrivateFieldGet(this, _data)[subtype];
    });

    _defineProperty(this, "labelSmall", _ => {
      return _classPrivateFieldGet(this, _data).title;
    });

    _classPrivateFieldSet(this, _data, data);
  }

  label(subtype) {
    if (_classPrivateFieldGet(this, _data).id === 'privateAddress') {
      return _classPrivateFieldGet(this, _data)[subtype];
    }

    return null;
  }

}

exports.IdentityTooltipItem = IdentityTooltipItem;

},{"../Form/formatters":26}],39:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.featureToggleAwareInputTypes = featureToggleAwareInputTypes;

/**
 * Take the available input types and augment to suit the enabled
 * features.
 *
 * @param {AvailableInputTypes} inputTypes
 * @param {FeatureToggles} featureToggles
 * @return {AvailableInputTypes}
 */
function featureToggleAwareInputTypes(inputTypes, featureToggles) {
  const local = { ...inputTypes
  };

  if (!featureToggles.inputType_credentials) {
    local.credentials = false;
  }

  if (!featureToggles.inputType_creditCards) {
    local.creditCards = false;
  }

  if (!featureToggles.inputType_identities) {
    local.identities = false;
  }

  if (!featureToggles.emailProtection) {
    local.email = false;
  }

  return local;
}

},{}],40:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.PasswordGenerator = void 0;

var _password = require("../packages/password");

var _rules = _interopRequireDefault(require("../packages/password/rules.json"));

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

var _previous = /*#__PURE__*/new WeakMap();

/**
 * Create a password once and reuse it.
 */
class PasswordGenerator {
  constructor() {
    _classPrivateFieldInitSpec(this, _previous, {
      writable: true,
      value: null
    });
  }

  /** @returns {boolean} */
  get generated() {
    return _classPrivateFieldGet(this, _previous) !== null;
  }
  /** @returns {string|null} */


  get password() {
    return _classPrivateFieldGet(this, _previous);
  }
  /** @param {import('../packages/password').GenerateOptions} [params] */


  generate() {
    let params = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : {};

    if (_classPrivateFieldGet(this, _previous)) {
      return _classPrivateFieldGet(this, _previous);
    }

    _classPrivateFieldSet(this, _previous, (0, _password.generate)({ ...params,
      rules: _rules.default
    }));

    return _classPrivateFieldGet(this, _previous);
  }

}

exports.PasswordGenerator = PasswordGenerator;

},{"../packages/password":11,"../packages/password/rules.json":15}],41:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.createScanner = createScanner;

var _Form = require("./Form/Form");

var _autofillUtils = require("./autofill-utils");

var _selectorsCss = require("./Form/selectors-css");

var _matching = require("./Form/matching");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * @typedef {{
 *     forms: Map<HTMLElement, import("./Form/Form").Form>;
 *     init(): ()=> void;
 *     enqueue(elements: (HTMLElement|Document)[]): void;
 *     findEligibleInputs(context): Scanner;
 * }} Scanner
 *
 * @typedef {{
 *     initialDelay: number,
 *     bufferSize: number,
 *     debounceTimePeriod: number,
 * }} ScannerOptions
 */

/** @type {ScannerOptions} */
const defaultScannerOptions = {
  // This buffer size is very large because it's an unexpected edge-case that
  // a DOM will be continually modified over and over without ever stopping. If we do see 1000 unique
  // new elements in the buffer however then this will prevent the algorithm from never ending.
  bufferSize: 50,
  // wait for a 500ms window of event silence before performing the scan
  debounceTimePeriod: 500,
  // how long to wait when performing the initial scan
  initialDelay: 0
};
/**
 * This allows:
 *   1) synchronous DOM scanning + mutations - via `createScanner(device).findEligibleInputs(document)`
 *   2) or, as above + a debounced mutation observer to re-run the scan after the given time
 */

class DefaultScanner {
  /** @type Map<HTMLElement, Form> */

  /** @type {any|undefined} the timer to reset */

  /** @type {Set<HTMLElement|Document>} stored changed elements until they can be processed */

  /** @type {ScannerOptions} */

  /** @type {HTMLInputElement | null} */

  /** @type {boolean} A flag to indicate the whole page will be re-scanned */

  /**
   * @param {import("./DeviceInterface/InterfacePrototype").default} device
   * @param {ScannerOptions} options
   */
  constructor(device, options) {
    _defineProperty(this, "forms", new Map());

    _defineProperty(this, "debounceTimer", void 0);

    _defineProperty(this, "changedElements", new Set());

    _defineProperty(this, "options", void 0);

    _defineProperty(this, "activeInput", null);

    _defineProperty(this, "rescanAll", false);

    _defineProperty(this, "mutObs", new MutationObserver(mutationList => {
      /** @type {HTMLElement[]} */
      if (this.rescanAll) {
        // quick version if buffer full
        this.enqueue([]);
        return;
      }

      const outgoing = [];

      for (const mutationRecord of mutationList) {
        if (mutationRecord.type === 'childList') {
          for (let addedNode of mutationRecord.addedNodes) {
            if (!(addedNode instanceof HTMLElement)) continue;
            if (addedNode.nodeName === 'DDG-AUTOFILL') continue;
            outgoing.push(addedNode);
          }
        }
      }

      this.enqueue(outgoing);
    }));

    this.device = device;
    this.matching = (0, _matching.createMatching)();
    this.options = options;
  }
  /**
   * Call this to scan once and then watch for changes.
   *
   * Call the returned function to remove listeners.
   * @returns {() => void}
   */


  init() {
    const delay = this.options.initialDelay; // if the delay is zero, (chrome/firefox etc) then use `requestIdleCallback`

    if (delay === 0) {
      window.requestIdleCallback(() => this.scanAndObserve());
    } else {
      // otherwise, use the delay time to defer the initial scan
      setTimeout(() => this.scanAndObserve(), delay);
    }

    return () => {
      // remove Dax, listeners, timers, and observers
      clearTimeout(this.debounceTimer);
      this.mutObs.disconnect();
      this.forms.forEach(form => {
        form.resetAllInputs();
        form.removeAllDecorations();
      });
      this.forms.clear();

      if (this.device.globalConfig.isDDGDomain) {
        (0, _autofillUtils.notifyWebApp)({
          deviceSignedIn: {
            value: false
          }
        });
      }
    };
  }
  /**
   * Scan the page and begin observing changes
   */


  scanAndObserve() {
    var _window$performance, _window$performance$m, _window$performance2, _window$performance2$;

    (_window$performance = window.performance) === null || _window$performance === void 0 ? void 0 : (_window$performance$m = _window$performance.mark) === null || _window$performance$m === void 0 ? void 0 : _window$performance$m.call(_window$performance, 'scanner:init:start');
    this.findEligibleInputs(document);
    (_window$performance2 = window.performance) === null || _window$performance2 === void 0 ? void 0 : (_window$performance2$ = _window$performance2.mark) === null || _window$performance2$ === void 0 ? void 0 : _window$performance2$.call(_window$performance2, 'scanner:init:end');
    this.mutObs.observe(document.body, {
      childList: true,
      subtree: true
    });
  }
  /**
   * @param context
   */


  findEligibleInputs(context) {
    var _context$matches;

    if ('matches' in context && (_context$matches = context.matches) !== null && _context$matches !== void 0 && _context$matches.call(context, _selectorsCss.FORM_INPUTS_SELECTOR)) {
      this.addInput(context);
    } else {
      context.querySelectorAll(_selectorsCss.FORM_INPUTS_SELECTOR).forEach(input => this.addInput(input));
    }

    return this;
  }
  /**
   * @param {HTMLElement|HTMLInputElement|HTMLSelectElement} input
   * @returns {HTMLFormElement|HTMLElement}
   */


  getParentForm(input) {
    if (input instanceof HTMLInputElement || input instanceof HTMLSelectElement) {
      if (input.form) return input.form;
    }

    let element = input; // traverse the DOM to search for related inputs

    while (element.parentElement && element.parentElement !== document.body) {
      element = element.parentElement; // todo: These selectors should be configurable

      const inputs = element.querySelectorAll(_selectorsCss.FORM_INPUTS_SELECTOR);
      const buttons = element.querySelectorAll(_selectorsCss.SUBMIT_BUTTON_SELECTOR); // If we find a button or another input, we assume that's our form

      if (inputs.length > 1 || buttons.length) {
        // found related input, return common ancestor
        return element;
      }
    }

    return input;
  }
  /**
   * @param {HTMLInputElement|HTMLSelectElement} input
   */


  addInput(input) {
    const parentForm = this.getParentForm(input); // Note that el.contains returns true for el itself

    const previouslyFoundParent = [...this.forms.keys()].find(form => form.contains(parentForm));

    if (previouslyFoundParent) {
      var _this$forms$get;

      // If we've already met the form or a descendant, add the input
      (_this$forms$get = this.forms.get(previouslyFoundParent)) === null || _this$forms$get === void 0 ? void 0 : _this$forms$get.addInput(input);
    } else {
      // if this form is an ancestor of an existing form, remove that before adding this
      const childForm = [...this.forms.keys()].find(form => parentForm.contains(form));

      if (childForm) {
        var _this$forms$get2;

        (_this$forms$get2 = this.forms.get(childForm)) === null || _this$forms$get2 === void 0 ? void 0 : _this$forms$get2.destroy();
        this.forms.delete(childForm);
      }

      this.forms.set(parentForm, new _Form.Form(parentForm, input, this.device, this.matching));
    }
  }
  /**
   * enqueue elements to be re-scanned after the given
   * amount of time has elapsed.
   *
   * @param {(HTMLElement|Document)[]} htmlElements
   */


  enqueue(htmlElements) {
    // if the buffer limit is reached, stop trying to track elements and process body instead.
    if (this.changedElements.size >= this.options.bufferSize) {
      this.rescanAll = true;
      this.changedElements.clear();
    } else if (!this.rescanAll) {
      // otherwise keep adding each element to the queue
      for (let element of htmlElements) {
        this.changedElements.add(element);
      }
    }

    clearTimeout(this.debounceTimer);
    this.debounceTimer = setTimeout(() => {
      this.processChangedElements();
      this.changedElements.clear();
      this.rescanAll = false;
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

    for (let element of this.changedElements) {
      if (element.isConnected) {
        this.findEligibleInputs(element);
      }
    }
  }
  /**
   * Watch for changes in the DOM, and enqueue elements to be scanned
   * @type {MutationObserver}
   */


}
/**
 * @param {import("./DeviceInterface/InterfacePrototype").default} device
 * @param {Partial<ScannerOptions>} [scannerOptions]
 * @returns {Scanner}
 */


function createScanner(device, scannerOptions) {
  return new DefaultScanner(device, { ...defaultScannerOptions,
    ...scannerOptions
  });
}

},{"./Form/Form":23,"./Form/matching":33,"./Form/selectors-css":34,"./autofill-utils":51}],42:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;

var _autofillUtils = require("../autofill-utils");

var _Tooltip = _interopRequireDefault(require("./Tooltip"));

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

class DataWebTooltip extends _Tooltip.default {
  /**
   * @param {InputTypeConfigs} config
   * @param {TooltipItemRenderer[]} items
   * @param {{onSelect(id:string): void}} callbacks
   */
  render(config, items, callbacks) {
    const includeStyles = this.tooltipHandler.tooltipStyles();
    let hasAddedSeparator = false; // Only show an hr above the first duck address button, but it can be either personal or private

    const shouldShowSeparator = dataId => {
      const shouldShow = ['personalAddress', 'privateAddress'].includes(dataId) && !hasAddedSeparator;
      if (shouldShow) hasAddedSeparator = true;
      return shouldShow;
    };

    const topClass = this.tooltipHandler.tooltipWrapperClass();
    this.shadow.innerHTML = "\n".concat(includeStyles, "\n<div class=\"wrapper wrapper--data ").concat(topClass, "\">\n    <div class=\"tooltip tooltip--data\" hidden>\n        ").concat(items.map(item => {
      var _item$labelSmall, _item$label;

      // these 2 are optional
      const labelSmall = (_item$labelSmall = item.labelSmall) === null || _item$labelSmall === void 0 ? void 0 : _item$labelSmall.call(item, this.subtype);
      const label = (_item$label = item.label) === null || _item$label === void 0 ? void 0 : _item$label.call(item, this.subtype);
      return "\n            ".concat(shouldShowSeparator(item.id()) ? '<hr />' : '', "\n            <button id=\"").concat(item.id(), "\" class=\"tooltip__button tooltip__button--data tooltip__button--data--").concat(config.type, " js-autofill-button\" >\n                <span class=\"tooltip__button__text-container\">\n                    <span class=\"label label--medium\">").concat((0, _autofillUtils.escapeXML)(item.labelMedium(this.subtype)), "</span>\n                    ").concat(label ? "<span class=\"label\">".concat((0, _autofillUtils.escapeXML)(label), "</span>") : '', "\n                    ").concat(labelSmall ? "<span class=\"label label--small\">".concat((0, _autofillUtils.escapeXML)(labelSmall), "</span>") : '', "\n                </span>\n            </button>\n        ");
    }).join(''), "\n    </div>\n</div>");
    this.wrapper = this.shadow.querySelector('.wrapper');
    this.tooltip = this.shadow.querySelector('.tooltip');
    this.autofillButtons = this.shadow.querySelectorAll('.js-autofill-button');
    this.autofillButtons.forEach(btn => {
      this.registerClickableButton(btn, () => {
        callbacks.onSelect(btn.id);
      });
    });
    this.init();
    return this;
  }

}

var _default = DataWebTooltip;
exports.default = _default;

},{"../autofill-utils":51,"./Tooltip":46}],43:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;

var _autofillUtils = require("../autofill-utils");

var _Tooltip = _interopRequireDefault(require("./Tooltip"));

var _styles = require("./styles/styles");

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

class EmailWebTooltip extends _Tooltip.default {
  /**
   * @param {import("../DeviceInterface/InterfacePrototype").default} device
   */
  render(device) {
    this.device = device;
    this.addresses = device.getLocalAddresses();
    const includeStyles = device.globalConfig.isApp ? "<style>".concat(_styles.CSS_STYLES, "</style>") : "<link rel=\"stylesheet\" href=\"".concat(chrome.runtime.getURL('public/css/autofill.css'), "\" crossorigin=\"anonymous\">");
    this.shadow.innerHTML = "\n".concat(includeStyles, "\n<div class=\"wrapper wrapper--email\">\n    <div class=\"tooltip tooltip--email\" hidden>\n        <button class=\"tooltip__button tooltip__button--email js-use-personal\">\n            <span class=\"tooltip__button--email__primary-text\">\n                Use <span class=\"js-address\">").concat((0, _autofillUtils.formatDuckAddress)((0, _autofillUtils.escapeXML)(this.addresses.personalAddress)), "</span>\n            </span>\n            <span class=\"tooltip__button--email__secondary-text\">Blocks email trackers</span>\n        </button>\n        <button class=\"tooltip__button tooltip__button--email js-use-private\">\n            <span class=\"tooltip__button--email__primary-text\">Use a Private Address</span>\n            <span class=\"tooltip__button--email__secondary-text\">Blocks email trackers and hides your address</span>\n        </button>\n    </div>\n</div>");
    this.wrapper = this.shadow.querySelector('.wrapper');
    this.tooltip = this.shadow.querySelector('.tooltip');
    this.usePersonalButton = this.shadow.querySelector('.js-use-personal');
    this.usePrivateButton = this.shadow.querySelector('.js-use-private');
    this.addressEl = this.shadow.querySelector('.js-address');

    this.updateAddresses = addresses => {
      if (addresses && this.addressEl) {
        this.addresses = addresses;
        this.addressEl.textContent = (0, _autofillUtils.formatDuckAddress)(addresses.personalAddress);
      }
    };

    this.registerClickableButton(this.usePersonalButton, () => {
      this.fillForm('personalAddress');
    });
    this.registerClickableButton(this.usePrivateButton, () => {
      this.fillForm('privateAddress');
    }); // Get the alias from the extension

    device.getAddresses().then(this.updateAddresses);
    this.init();
    return this;
  }
  /**
   * @param {'personalAddress' | 'privateAddress'} id
   */


  async fillForm(id) {
    var _this$device;

    const address = this.addresses[id];
    const formattedAddress = (0, _autofillUtils.formatDuckAddress)(address);
    (_this$device = this.device) === null || _this$device === void 0 ? void 0 : _this$device.selectedDetail({
      email: formattedAddress,
      id
    }, 'email');
  }

}

var _default = EmailWebTooltip;
exports.default = _default;

},{"../autofill-utils":51,"./Tooltip":46,"./styles/styles":49}],44:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.NativeTooltip = void 0;

var _matching = require("../Form/matching");

/**
 * A 'Native' tooltip means that that autofill is not responsible
 * for rendering **any** UI relating to the selecting of items
 *
 * @implements {TooltipInterface}
 */
class NativeTooltip {
  /**
   * To 'attach' on iOS/Android is to ask the runtime for autofill data - this
   * will eventually cause the native overlays to show
   * @param args
   */
  attach(args) {
    const {
      form,
      input,
      device
    } = args;
    const inputType = (0, _matching.getInputType)(input);
    const mainType = (0, _matching.getMainTypeFromType)(inputType);
    const subType = (0, _matching.getSubtypeFromType)(inputType);

    if (mainType === 'unknown') {
      throw new Error('unreachable, should not be here if (mainType === "unknown")');
    }
    /** @type {GetAutofillDataRequest} */


    const payload = {
      inputType,
      mainType,
      subType
    };
    device.getAutofillData(payload).then(resp => {
      console.log('Autofilling...', resp, mainType);
      form.autofillData(resp, mainType);
    }).catch(e => {
      console.error('NativeTooltip::device.getAutofillData(payload)');
      console.error(e);
    });
  }

}

exports.NativeTooltip = NativeTooltip;

},{"../Form/matching":33}],45:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.OverlayControllerTooltip = void 0;

function _classPrivateMethodInitSpec(obj, privateSet) { _checkPrivateRedeclaration(obj, privateSet); privateSet.add(obj); }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classPrivateMethodGet(receiver, privateSet, fn) { if (!privateSet.has(receiver)) { throw new TypeError("attempted to get private field on non-instance"); } return fn; }

function _classPrivateFieldSet(receiver, privateMap, value) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "set"); _classApplyDescriptorSet(receiver, descriptor, value); return value; }

function _classApplyDescriptorSet(receiver, descriptor, value) { if (descriptor.set) { descriptor.set.call(receiver, value); } else { if (!descriptor.writable) { throw new TypeError("attempted to set read only private field"); } descriptor.value = value; } }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

var _state = /*#__PURE__*/new WeakMap();

var _attachListeners = /*#__PURE__*/new WeakSet();

var _removeListeners = /*#__PURE__*/new WeakSet();

/**
 * @typedef {object} TopFrameControllerTooltipOptions
 */

/**
 * @implements {TooltipInterface}
 */
class OverlayControllerTooltip {
  constructor() {
    _classPrivateMethodInitSpec(this, _removeListeners);

    _classPrivateMethodInitSpec(this, _attachListeners);

    _defineProperty(this, "_activeTooltip", null);

    _classPrivateFieldInitSpec(this, _state, {
      writable: true,
      value: 'idle'
    });

    _defineProperty(this, "_device", null);

    _defineProperty(this, "_listenerFactories", []);

    _defineProperty(this, "_listenerCleanups", []);

    _defineProperty(this, "pollingTimeout", null);
  }

  attach(args) {
    if (_classPrivateFieldGet(this, _state) !== 'idle') {
      this.removeTooltip().catch(e => {
        // todo(Shane): can we recover here?
        console.log('could not remove', e);
      }).finally(() => this._attach(args));
    } else {
      this._attach(args);
    }
  }
  /**
   * @param {AttachArgs} args
   * @private
   */


  _attach(args) {
    const {
      getPosition,
      topContextData,
      click,
      input
    } = args;
    let delay = 0;

    if (!click && !this.elementIsInViewport(getPosition())) {
      input.scrollIntoView(true);
      delay = 500;
    }

    setTimeout(() => {
      this.showTopTooltip(click, getPosition(), topContextData).catch(e => {
        console.error('error from showTopTooltip', e);
      });
    }, delay);
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
   * @param {TopContextData} [data]
   */


  async showTopTooltip(click, inputDimensions, data) {
    let diffX = inputDimensions.x;
    let diffY = inputDimensions.y;

    if (click) {
      diffX -= click.x;
      diffY -= click.y;
    } else if (!this.elementIsInViewport(inputDimensions)) {
      // If the focus event is outside the viewport ignore, we've already tried to scroll to it
      return;
    }
    /** @type {ShowAutofillParentRequest} */


    const details = {
      wasFromClick: Boolean(click),
      inputTop: Math.floor(diffY),
      inputLeft: Math.floor(diffX),
      inputHeight: Math.floor(inputDimensions.height),
      inputWidth: Math.floor(inputDimensions.width),
      serializedInputContext: JSON.stringify(data)
    };
    if (!this._device) throw new Error('unreachable');

    try {
      await this._device.showAutofillParent(details);

      _classPrivateFieldSet(this, _state, 'parentShown');

      _classPrivateMethodGet(this, _attachListeners, _attachListeners2).call(this);
    } catch (e) {
      console.error('could not show parent', e);

      _classPrivateFieldSet(this, _state, 'idle');
    }
  }

  handleEvent(event) {
    switch (event.type) {
      case 'scroll':
        {
          this.removeTooltip();
          break;
        }

      case 'keydown':
        {
          if (['Escape', 'Tab', 'Enter'].includes(event.code)) {
            this.removeTooltip();
          }

          break;
        }

      case 'input':
        {
          this.removeTooltip();
          break;
        }

      case 'pointerdown':
        {
          this.removeTooltip();
          break;
        }
    }
  }
  /** @type {number|null} */


  /**
   * Poll the native listener until the user has selected a credential.
   * Message return types are:
   * - 'stop' is returned whenever the message sent doesn't match the native last opened tooltip.
   *     - This also is triggered when the close event is called and prevents any edge case continued polling.
   * - 'ok' is when the user has selected a credential and the value can be injected into the page.
   * - 'none' is when the tooltip is open in the native window however hasn't been entered.
   * todo(Shane): How to make this generic - probably don't assume polling.
   * @returns {Promise<void>}
   */
  async listenForSelectedCredential() {
    var _this$_device;

    // Prevent two timeouts from happening
    // @ts-ignore
    clearTimeout(this.pollingTimeout);
    const response = await ((_this$_device = this._device) === null || _this$_device === void 0 ? void 0 : _this$_device.getSelectedCredentials());

    switch (response.type) {
      case 'none':
        // Parent hasn't got a selected credential yet
        // @ts-ignore
        this.pollingTimeout = setTimeout(() => {
          this.listenForSelectedCredential();
        }, 100);
        return;

      case 'ok':
        {
          var _this$_device2;

          return (_this$_device2 = this._device) === null || _this$_device2 === void 0 ? void 0 : _this$_device2.activeFormSelectedDetail(response.data, response.configType);
        }

      case 'stop':
        // Parent wants us to stop polling
        break;
    }
  }

  async removeTooltip() {
    var _this$_device3;

    if (_classPrivateFieldGet(this, _state) === 'removingParent') return;
    if (_classPrivateFieldGet(this, _state) === 'idle') return;
    if (!this._device) throw new Error('unreachable');

    _classPrivateFieldSet(this, _state, 'removingParent');

    await ((_this$_device3 = this._device) === null || _this$_device3 === void 0 ? void 0 : _this$_device3.closeAutofillParent().catch(e => console.error('Could not close parent', e)));

    _classPrivateFieldSet(this, _state, 'idle');

    _classPrivateMethodGet(this, _removeListeners, _removeListeners2).call(this);
  }
  /**
   * TODO: Don't allow this to be called from outside since it's deprecated.
   * @param {PosFn} _getPosition
   * @param {TopContextData} _topContextData
   * @return {import('./Tooltip').Tooltip}
   */


  createTooltip(_getPosition, _topContextData) {
    throw new Error('unimplemented');
  }

  getActiveTooltip() {
    return this._activeTooltip;
  }

  setActiveTooltip(tooltip) {
    this._activeTooltip = tooltip;
  }

  setDevice(device) {
    this._device = device;
  }

}

exports.OverlayControllerTooltip = OverlayControllerTooltip;

function _attachListeners2() {
  this.listenForSelectedCredential();
  window.addEventListener('scroll', this);
  window.addEventListener('keydown', this);
  window.addEventListener('input', this);
  window.addEventListener('pointerdown', this);
}

function _removeListeners2() {
  window.removeEventListener('scroll', this);
  window.removeEventListener('keydown', this);
  window.removeEventListener('input', this);
  window.removeEventListener('pointerdown', this);
}

},{}],46:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = exports.Tooltip = void 0;

var _autofillUtils = require("../autofill-utils");

var _matching = require("../Form/matching");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * @typedef {object} TooltipOptions
 * @property {boolean} testMode
 */
class Tooltip {
  /**
   * @param config
   * @param inputType
   * @param getPosition
   * @param {WebTooltipHandler} tooltipHandler
   * @param {TooltipOptions} options
   */
  constructor(config, inputType, getPosition, tooltipHandler, options) {
    _defineProperty(this, "resObs", new ResizeObserver(entries => entries.forEach(() => this.checkPosition())));

    _defineProperty(this, "mutObs", new MutationObserver(mutationList => {
      for (const mutationRecord of mutationList) {
        if (mutationRecord.type === 'childList') {
          // Only check added nodes
          mutationRecord.addedNodes.forEach(el => {
            if (el.nodeName === 'DDG-AUTOFILL') return;
            this.ensureIsLastInDOM();
          });
        }
      }

      this.checkPosition();
    }));

    _defineProperty(this, "clickableButtons", new Map());

    this.shadow = document.createElement('ddg-autofill').attachShadow({
      mode: options.testMode ? 'open' : 'closed'
    });
    this.host = this.shadow.host;
    this.config = config;
    this.subtype = (0, _matching.getSubtypeFromType)(inputType);
    this.tooltip = null;
    this.getPosition = getPosition;
    const forcedVisibilityStyles = {
      'display': 'block',
      'visibility': 'visible',
      'opacity': '1'
    }; // @ts-ignore how to narrow this.host to HTMLElement?

    (0, _autofillUtils.addInlineStyles)(this.host, forcedVisibilityStyles);
    this.tooltipHandler = tooltipHandler;
    this.count = 0;
  }

  append() {
    document.body.appendChild(this.host);
  }

  remove() {
    window.removeEventListener('scroll', this, {
      capture: true
    });
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
      case 'scroll':
        this.checkPosition();
        break;
    }
  }

  focus(x, y) {
    var _this$shadow$elementF, _this$shadow$elementF2;

    const focusableElements = 'button';
    const currentFocusClassName = 'currentFocus';
    const currentFocused = this.shadow.querySelectorAll(".".concat(currentFocusClassName));
    [...currentFocused].forEach(el => {
      el.classList.remove(currentFocusClassName);
    });
    (_this$shadow$elementF = this.shadow.elementFromPoint(x, y)) === null || _this$shadow$elementF === void 0 ? void 0 : (_this$shadow$elementF2 = _this$shadow$elementF.closest(focusableElements)) === null || _this$shadow$elementF2 === void 0 ? void 0 : _this$shadow$elementF2.classList.add(currentFocusClassName);
  }

  checkPosition() {
    if (this.animationFrame) {
      window.cancelAnimationFrame(this.animationFrame);
    }

    this.animationFrame = window.requestAnimationFrame(() => {
      const {
        left,
        bottom
      } = this.getPosition();

      if (left !== this.left || bottom !== this.top) {
        this.updatePosition({
          left,
          top: bottom
        });
      }

      this.animationFrame = null;
    });
  }

  updatePosition(_ref) {
    let {
      left,
      top
    } = _ref;
    const shadow = this.shadow; // If the stylesheet is not loaded wait for load (Chrome bug)

    if (!shadow.styleSheets.length) {
      var _this$stylesheet;

      (_this$stylesheet = this.stylesheet) === null || _this$stylesheet === void 0 ? void 0 : _this$stylesheet.addEventListener('load', () => this.checkPosition());
      return;
    }

    this.left = left;
    this.top = top;

    if (this.transformRuleIndex && shadow.styleSheets[0].rules[this.transformRuleIndex]) {
      // If we have already set the rule, remove it…
      shadow.styleSheets[0].deleteRule(this.transformRuleIndex);
    } else {
      // …otherwise, set the index as the very last rule
      this.transformRuleIndex = shadow.styleSheets[0].rules.length;
    }

    let cssRule = this.tooltipHandler.tooltipPositionClass(top, left);
    shadow.styleSheets[0].insertRule(cssRule, this.transformRuleIndex);
  }

  ensureIsLastInDOM() {
    this.count = this.count || 0; // If DDG el is not the last in the doc, move it there

    if (document.body.lastElementChild !== this.host) {
      // Try up to 15 times to avoid infinite loop in case someone is doing the same
      if (this.count < 15) {
        this.lift();
        this.append();
        this.checkPosition();
        this.count++;
      } else {
        // Remove the tooltip from the form to cleanup listeners and observers
        this.tooltipHandler.removeTooltip();
        console.info("DDG autofill bailing out");
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
    this.clickableButtons.set(btn, handler); // Needed because clicks within the shadow dom don't provide this info to the outside

    btn.addEventListener('mouseenter', e => this.setActiveButton(e));
    btn.addEventListener('mouseleave', () => this.unsetActiveButton());
  }

  dispatchClick() {
    const handler = this.clickableButtons.get(this.activeButton);

    if (handler) {
      (0, _autofillUtils.safeExecute)(this.activeButton, handler);
    }
  }

  setupSizeListener() {
    this.tooltipHandler.setupSizeListener(() => {
      // Listen to layout and paint changes to register the size
      const observer = new PerformanceObserver(() => {
        this.setSize();
      });
      observer.observe({
        entryTypes: ['layout-shift', 'paint']
      });
    });
  }

  setSize() {
    this.tooltipHandler.setSize(() => {
      const innerNode = this.shadow.querySelector('.wrapper--data'); // Shouldn't be possible

      if (!innerNode) return;
      return {
        height: innerNode.clientHeight,
        width: innerNode.clientWidth
      };
    });
  }

  init() {
    var _this$stylesheet2;

    this.animationFrame = null;
    this.top = 0;
    this.left = 0;
    this.transformRuleIndex = null;
    this.stylesheet = this.shadow.querySelector('link, style'); // Un-hide once the style is loaded, to avoid flashing unstyled content

    (_this$stylesheet2 = this.stylesheet) === null || _this$stylesheet2 === void 0 ? void 0 : _this$stylesheet2.addEventListener('load', () => this.tooltip.removeAttribute('hidden'));
    this.append();
    this.resObs.observe(document.body);
    this.mutObs.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true
    });
    window.addEventListener('scroll', this, {
      capture: true
    });
    this.setSize();
    this.setupSizeListener();
  }

}

exports.Tooltip = Tooltip;
var _default = Tooltip;
exports.default = _default;

},{"../Form/matching":33,"../autofill-utils":51}],47:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.WebTooltip = void 0;

var _inputTypeConfig = require("../Form/inputTypeConfig");

var _DataWebTooltip = _interopRequireDefault(require("./DataWebTooltip"));

var _EmailWebTooltip = _interopRequireDefault(require("./EmailWebTooltip"));

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _classPrivateFieldInitSpec(obj, privateMap, value) { _checkPrivateRedeclaration(obj, privateMap); privateMap.set(obj, value); }

function _classPrivateMethodInitSpec(obj, privateSet) { _checkPrivateRedeclaration(obj, privateSet); privateSet.add(obj); }

function _checkPrivateRedeclaration(obj, privateCollection) { if (privateCollection.has(obj)) { throw new TypeError("Cannot initialize the same private elements twice on an object"); } }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

function _classPrivateFieldGet(receiver, privateMap) { var descriptor = _classExtractFieldDescriptor(receiver, privateMap, "get"); return _classApplyDescriptorGet(receiver, descriptor); }

function _classExtractFieldDescriptor(receiver, privateMap, action) { if (!privateMap.has(receiver)) { throw new TypeError("attempted to " + action + " private field on non-instance"); } return privateMap.get(receiver); }

function _classApplyDescriptorGet(receiver, descriptor) { if (descriptor.get) { return descriptor.get.call(receiver); } return descriptor.value; }

function _classPrivateMethodGet(receiver, privateSet, fn) { if (!privateSet.has(receiver)) { throw new TypeError("attempted to get private field on non-instance"); } return fn; }

var _attachCloseListeners = /*#__PURE__*/new WeakSet();

var _removeCloseListeners = /*#__PURE__*/new WeakSet();

var _device = /*#__PURE__*/new WeakMap();

var _setDevice = /*#__PURE__*/new WeakSet();

var _dataForAutofill = /*#__PURE__*/new WeakSet();

var _onSelect = /*#__PURE__*/new WeakSet();

/**
 * @typedef {object} WebTooltipOptions
 * @property {"modern" | "legacy"} tooltipKind
 */

/**
 * @implements {TooltipInterface}
 * @implements {WebTooltipHandler}
 */
class WebTooltip {
  /** @type {import("../UI/Tooltip.js").Tooltip | null} */

  /** @type {WebTooltipOptions} */

  /**
   * @deprecated do not access the tooltipHandler directly here
   * @type {import("../DeviceInterface/InterfacePrototype").default | null}
   */

  /**
   * @param {WebTooltipOptions} options
   */
  constructor(options) {
    _classPrivateMethodInitSpec(this, _onSelect);

    _classPrivateMethodInitSpec(this, _dataForAutofill);

    _classPrivateMethodInitSpec(this, _setDevice);

    _classPrivateFieldInitSpec(this, _device, {
      get: _get_device,
      set: void 0
    });

    _classPrivateMethodInitSpec(this, _removeCloseListeners);

    _classPrivateMethodInitSpec(this, _attachCloseListeners);

    _defineProperty(this, "_activeTooltip", null);

    _defineProperty(this, "_options", void 0);

    _defineProperty(this, "_device", null);

    _defineProperty(this, "_listenerFactories", []);

    _defineProperty(this, "_listenerCleanups", []);

    this._options = options;
    window.addEventListener('pointerdown', this, true);
  }

  attach(args) {
    if (this.getActiveTooltip()) {
      // todo: Is this is correct logic?
      return;
    }

    _classPrivateMethodGet(this, _setDevice, _setDevice2).call(this, args.device);

    const {
      topContextData,
      getPosition,
      input,
      form
    } = args;
    this.setActiveTooltip(this.createTooltip(getPosition, topContextData));
    form.showingTooltip(input);
  }
  /**
   * TODO: Don't allow this to be called from outside since it's deprecated.
   * @param {PosFn} getPosition
   * @param {TopContextData} topContextData
   * @return {import("./Tooltip").Tooltip}
   */


  createTooltip(getPosition, topContextData) {
    _classPrivateMethodGet(this, _attachCloseListeners, _attachCloseListeners2).call(this);

    const config = (0, _inputTypeConfig.getInputConfigFromType)(topContextData.inputType);

    if (this._options.tooltipKind === 'modern') {
      // collect the data for each item to display
      const data = _classPrivateMethodGet(this, _dataForAutofill, _dataForAutofill2).call(this, config, topContextData.inputType, topContextData); // convert the data into tool tip item renderers


      const asRenderers = data.map(d => config.tooltipItem(d)); // construct the autofill

      return new _DataWebTooltip.default(config, topContextData.inputType, getPosition, this, {
        testMode: _classPrivateFieldGet(this, _device).isTestMode()
      }).render(config, asRenderers, {
        onSelect: id => {
          _classPrivateMethodGet(this, _onSelect, _onSelect2).call(this, config, data, id);
        }
      });
    } else {
      return new _EmailWebTooltip.default(config, topContextData.inputType, getPosition, this, {
        testMode: _classPrivateFieldGet(this, _device).isTestMode()
      }).render(_classPrivateFieldGet(this, _device));
    }
  }

  handleEvent(event) {
    switch (event.type) {
      case 'keydown':
        if (['Escape', 'Tab', 'Enter'].includes(event.code)) {
          this.removeTooltip();
        }

        break;

      case 'input':
        this.removeTooltip();
        break;
      // todo(Shane): Why was this 'click' needed?

      case 'click':
      case 'pointerdown':
        {
          this._pointerDownListener(event);

          break;
        }
    }
  } // Global listener for event delegation


  _pointerDownListener(e) {
    if (!e.isTrusted) return; // @ts-ignore

    if (e.target.nodeName === 'DDG-AUTOFILL') {
      e.preventDefault();
      e.stopImmediatePropagation();
      const activeTooltip = this.getActiveTooltip();

      if (!activeTooltip) {
        console.warn('Could not get activeTooltip');
      } else {
        activeTooltip.dispatchClick();
      }
    } else {
      this.removeTooltip().catch(e => {
        console.error('error removing tooltip', e);
      });
    } // todo(Shane): Form submissions here
    // exit now if form saving was not enabled
    // todo(Shane): more runtime polymorphism here, + where does this live?
    // if (this.autofillSettings.featureToggles.credentials_saving) {
    //     // Check for clicks on submit buttons
    //     const matchingForm = [...this.scanner.forms.values()].find(
    //         (form) => {
    //             const btns = [...form.submitButtons]
    //             // @ts-ignore
    //             if (btns.includes(e.target)) return true
    //
    //             // @ts-ignore
    //             if (btns.find((btn) => btn.contains(e.target))) return true
    //         }
    //     )
    //
    //     matchingForm?.submitHandler()
    // }

  }

  async removeTooltip() {
    if (this._activeTooltip) {
      _classPrivateMethodGet(this, _removeCloseListeners, _removeCloseListeners2).call(this);

      this._activeTooltip.remove();

      this._activeTooltip = null;
      this.currentAttached = null;
    }
  }
  /**
   * @returns {import("../UI/Tooltip.js").Tooltip|null}
   */


  getActiveTooltip() {
    return this._activeTooltip;
  }
  /**
   * @param {import("../UI/Tooltip.js").Tooltip} value
   */


  setActiveTooltip(value) {
    this._activeTooltip = value;
  }
  /**
   * @deprecated don't rely in device in this class
   * @returns {Device}
   */


  setSize(_cb) {
    _classPrivateFieldGet(this, _device).setSize(_cb);
  }

  setupSizeListener(_cb) {
    _classPrivateFieldGet(this, _device).setupSizeListener(_cb);
  }

  tooltipPositionClass(top, left) {
    return _classPrivateFieldGet(this, _device).tooltipPositionClass(top, left);
  }

  tooltipStyles() {
    return _classPrivateFieldGet(this, _device).tooltipStyles();
  }

  tooltipWrapperClass() {
    return _classPrivateFieldGet(this, _device).tooltipWrapperClass();
  }

  setDevice(device) {
    _classPrivateMethodGet(this, _setDevice, _setDevice2).call(this, device);
  }

  addListener(cb) {
    this._listenerFactories.push(cb);
  }

}

exports.WebTooltip = WebTooltip;

function _attachCloseListeners2() {
  window.addEventListener('input', this);
  window.addEventListener('keydown', this);
  this._listenerCleanups = [];

  for (let listenerFactory of this._listenerFactories) {
    this._listenerCleanups.push(listenerFactory());
  }
}

function _removeCloseListeners2() {
  window.removeEventListener('input', this);
  window.removeEventListener('keydown', this);

  for (let listenerCleanup of this._listenerCleanups) {
    listenerCleanup();
  }
}

function _get_device() {
  if (!this._device) throw new Error('device was not assigned');
  return this._device;
}

function _setDevice2(device) {
  this._device = device;
}

function _dataForAutofill2(config, inputType, topContextData) {
  return _classPrivateFieldGet(this, _device).dataForAutofill(config, inputType, topContextData);
}

function _onSelect2(config, data, id) {
  return _classPrivateFieldGet(this, _device).onSelect(config, data, id);
}

},{"../Form/inputTypeConfig":28,"./DataWebTooltip":42,"./EmailWebTooltip":43}],48:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.ddgPasswordIconFocused = exports.ddgPasswordIconFilled = exports.ddgPasswordIconBaseWhite = exports.ddgPasswordIconBase = exports.ddgIdentityIconBase = exports.ddgCcIconFilled = exports.ddgCcIconBase = void 0;
const ddgPasswordIconBase = 'data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB3aWR0aD0iMjRweCIgaGVpZ2h0PSIyNHB4IiB2aWV3Qm94PSIwIDAgMjQgMjQiIHZlcnNpb249IjEuMSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+CiAgICA8dGl0bGU+ZGRnLXBhc3N3b3JkLWljb24tYmFzZTwvdGl0bGU+CiAgICA8ZyBpZD0iZGRnLXBhc3N3b3JkLWljb24tYmFzZSIgc3Ryb2tlPSJub25lIiBzdHJva2Utd2lkdGg9IjEiIGZpbGw9Im5vbmUiIGZpbGwtcnVsZT0iZXZlbm9kZCI+CiAgICAgICAgPGcgaWQ9IlVuaW9uIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSg0LjAwMDAwMCwgNC4wMDAwMDApIiBmaWxsPSIjMDAwMDAwIj4KICAgICAgICAgICAgPHBhdGggZD0iTTExLjMzMzMsMi42NjY2NyBDMTAuMjI4OCwyLjY2NjY3IDkuMzMzMzMsMy41NjIxIDkuMzMzMzMsNC42NjY2NyBDOS4zMzMzMyw1Ljc3MTI0IDEwLjIyODgsNi42NjY2NyAxMS4zMzMzLDYuNjY2NjcgQzEyLjQzNzksNi42NjY2NyAxMy4zMzMzLDUuNzcxMjQgMTMuMzMzMyw0LjY2NjY3IEMxMy4zMzMzLDMuNTYyMSAxMi40Mzc5LDIuNjY2NjcgMTEuMzMzMywyLjY2NjY3IFogTTEwLjY2NjcsNC42NjY2NyBDMTAuNjY2Nyw0LjI5ODQ4IDEwLjk2NTEsNCAxMS4zMzMzLDQgQzExLjcwMTUsNCAxMiw0LjI5ODQ4IDEyLDQuNjY2NjcgQzEyLDUuMDM0ODYgMTEuNzAxNSw1LjMzMzMzIDExLjMzMzMsNS4zMzMzMyBDMTAuOTY1MSw1LjMzMzMzIDEwLjY2NjcsNS4wMzQ4NiAxMC42NjY3LDQuNjY2NjcgWiIgaWQ9IlNoYXBlIj48L3BhdGg+CiAgICAgICAgICAgIDxwYXRoIGQ9Ik0xMC42NjY3LDAgQzcuNzIxMTUsMCA1LjMzMzMzLDIuMzg3ODEgNS4zMzMzMyw1LjMzMzMzIEM1LjMzMzMzLDUuNzYxMTkgNS4zODM4NSw2LjE3Nzk4IDUuNDc5NDUsNi41Nzc3NSBMMC4xOTUyNjIsMTEuODYxOSBDMC4wNzAyMzc5LDExLjk4NyAwLDEyLjE1NjUgMCwxMi4zMzMzIEwwLDE1LjMzMzMgQzAsMTUuNzAxNSAwLjI5ODQ3NywxNiAwLjY2NjY2NywxNiBMMy4zMzMzMywxNiBDNC4wNjk3MSwxNiA0LjY2NjY3LDE1LjQwMyA0LjY2NjY3LDE0LjY2NjcgTDQuNjY2NjcsMTQgTDUuMzMzMzMsMTQgQzYuMDY5NzEsMTQgNi42NjY2NywxMy40MDMgNi42NjY2NywxMi42NjY3IEw2LjY2NjY3LDExLjMzMzMgTDgsMTEuMzMzMyBDOC4xNzY4MSwxMS4zMzMzIDguMzQ2MzgsMTEuMjYzMSA4LjQ3MTQxLDExLjEzODEgTDkuMTU5MDYsMTAuNDUwNCBDOS42Mzc3MiwxMC41OTEyIDEwLjE0MzksMTAuNjY2NyAxMC42NjY3LDEwLjY2NjcgQzEzLjYxMjIsMTAuNjY2NyAxNiw4LjI3ODg1IDE2LDUuMzMzMzMgQzE2LDIuMzg3ODEgMTMuNjEyMiwwIDEwLjY2NjcsMCBaIE02LjY2NjY3LDUuMzMzMzMgQzYuNjY2NjcsMy4xMjQxOSA4LjQ1NzUzLDEuMzMzMzMgMTAuNjY2NywxLjMzMzMzIEMxMi44NzU4LDEuMzMzMzMgMTQuNjY2NywzLjEyNDE5IDE0LjY2NjcsNS4zMzMzMyBDMTQuNjY2Nyw3LjU0MjQ3IDEyLjg3NTgsOS4zMzMzMyAxMC42NjY3LDkuMzMzMzMgQzEwLjE1NTgsOS4zMzMzMyA5LjY2ODg2LDkuMjM3OSA5LjIyMTUyLDkuMDY0NSBDOC45NzUyOCw4Ljk2OTA1IDguNjk1OTEsOS4wMjc5NSA4LjUwOTE2LDkuMjE0NjkgTDcuNzIzODYsMTAgTDYsMTAgQzUuNjMxODEsMTAgNS4zMzMzMywxMC4yOTg1IDUuMzMzMzMsMTAuNjY2NyBMNS4zMzMzMywxMi42NjY3IEw0LDEyLjY2NjcgQzMuNjMxODEsMTIuNjY2NyAzLjMzMzMzLDEyLjk2NTEgMy4zMzMzMywxMy4zMzMzIEwzLjMzMzMzLDE0LjY2NjcgTDEuMzMzMzMsMTQuNjY2NyBMMS4zMzMzMywxMi42MDk1IEw2LjY5Nzg3LDcuMjQ0OTQgQzYuODc1MDIsNy4wNjc3OSA2LjkzNzksNi44MDYyOSA2Ljg2MDY1LDYuNTY3OTggQzYuNzM0ODksNi4xNzk5NyA2LjY2NjY3LDUuNzY1MjcgNi42NjY2Nyw1LjMzMzMzIFoiIGlkPSJTaGFwZSI+PC9wYXRoPgogICAgICAgIDwvZz4KICAgIDwvZz4KPC9zdmc+';
exports.ddgPasswordIconBase = ddgPasswordIconBase;
const ddgPasswordIconBaseWhite = 'data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB3aWR0aD0iMjRweCIgaGVpZ2h0PSIyNHB4IiB2aWV3Qm94PSIwIDAgMjQgMjQiIHZlcnNpb249IjEuMSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+CiAgICA8dGl0bGU+ZGRnLXBhc3N3b3JkLWljb24tYmFzZS13aGl0ZTwvdGl0bGU+CiAgICA8ZyBpZD0iZGRnLXBhc3N3b3JkLWljb24tYmFzZS13aGl0ZSIgc3Ryb2tlPSJub25lIiBzdHJva2Utd2lkdGg9IjEiIGZpbGw9Im5vbmUiIGZpbGwtcnVsZT0iZXZlbm9kZCI+CiAgICAgICAgPGcgaWQ9IlVuaW9uIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSg0LjAwMDAwMCwgNC4wMDAwMDApIiBmaWxsPSIjRkZGRkZGIj4KICAgICAgICAgICAgPHBhdGggZD0iTTExLjMzMzMsMi42NjY2NyBDMTAuMjI4OCwyLjY2NjY3IDkuMzMzMzMsMy41NjIxIDkuMzMzMzMsNC42NjY2NyBDOS4zMzMzMyw1Ljc3MTI0IDEwLjIyODgsNi42NjY2NyAxMS4zMzMzLDYuNjY2NjcgQzEyLjQzNzksNi42NjY2NyAxMy4zMzMzLDUuNzcxMjQgMTMuMzMzMyw0LjY2NjY3IEMxMy4zMzMzLDMuNTYyMSAxMi40Mzc5LDIuNjY2NjcgMTEuMzMzMywyLjY2NjY3IFogTTEwLjY2NjcsNC42NjY2NyBDMTAuNjY2Nyw0LjI5ODQ4IDEwLjk2NTEsNCAxMS4zMzMzLDQgQzExLjcwMTUsNCAxMiw0LjI5ODQ4IDEyLDQuNjY2NjcgQzEyLDUuMDM0ODYgMTEuNzAxNSw1LjMzMzMzIDExLjMzMzMsNS4zMzMzMyBDMTAuOTY1MSw1LjMzMzMzIDEwLjY2NjcsNS4wMzQ4NiAxMC42NjY3LDQuNjY2NjcgWiIgaWQ9IlNoYXBlIj48L3BhdGg+CiAgICAgICAgICAgIDxwYXRoIGQ9Ik0xMC42NjY3LDAgQzcuNzIxMTUsMCA1LjMzMzMzLDIuMzg3ODEgNS4zMzMzMyw1LjMzMzMzIEM1LjMzMzMzLDUuNzYxMTkgNS4zODM4NSw2LjE3Nzk4IDUuNDc5NDUsNi41Nzc3NSBMMC4xOTUyNjIsMTEuODYxOSBDMC4wNzAyMzc5LDExLjk4NyAwLDEyLjE1NjUgMCwxMi4zMzMzIEwwLDE1LjMzMzMgQzAsMTUuNzAxNSAwLjI5ODQ3NywxNiAwLjY2NjY2NywxNiBMMy4zMzMzMywxNiBDNC4wNjk3MSwxNiA0LjY2NjY3LDE1LjQwMyA0LjY2NjY3LDE0LjY2NjcgTDQuNjY2NjcsMTQgTDUuMzMzMzMsMTQgQzYuMDY5NzEsMTQgNi42NjY2NywxMy40MDMgNi42NjY2NywxMi42NjY3IEw2LjY2NjY3LDExLjMzMzMgTDgsMTEuMzMzMyBDOC4xNzY4MSwxMS4zMzMzIDguMzQ2MzgsMTEuMjYzMSA4LjQ3MTQxLDExLjEzODEgTDkuMTU5MDYsMTAuNDUwNCBDOS42Mzc3MiwxMC41OTEyIDEwLjE0MzksMTAuNjY2NyAxMC42NjY3LDEwLjY2NjcgQzEzLjYxMjIsMTAuNjY2NyAxNiw4LjI3ODg1IDE2LDUuMzMzMzMgQzE2LDIuMzg3ODEgMTMuNjEyMiwwIDEwLjY2NjcsMCBaIE02LjY2NjY3LDUuMzMzMzMgQzYuNjY2NjcsMy4xMjQxOSA4LjQ1NzUzLDEuMzMzMzMgMTAuNjY2NywxLjMzMzMzIEMxMi44NzU4LDEuMzMzMzMgMTQuNjY2NywzLjEyNDE5IDE0LjY2NjcsNS4zMzMzMyBDMTQuNjY2Nyw3LjU0MjQ3IDEyLjg3NTgsOS4zMzMzMyAxMC42NjY3LDkuMzMzMzMgQzEwLjE1NTgsOS4zMzMzMyA5LjY2ODg2LDkuMjM3OSA5LjIyMTUyLDkuMDY0NSBDOC45NzUyOCw4Ljk2OTA1IDguNjk1OTEsOS4wMjc5NSA4LjUwOTE2LDkuMjE0NjkgTDcuNzIzODYsMTAgTDYsMTAgQzUuNjMxODEsMTAgNS4zMzMzMywxMC4yOTg1IDUuMzMzMzMsMTAuNjY2NyBMNS4zMzMzMywxMi42NjY3IEw0LDEyLjY2NjcgQzMuNjMxODEsMTIuNjY2NyAzLjMzMzMzLDEyLjk2NTEgMy4zMzMzMywxMy4zMzMzIEwzLjMzMzMzLDE0LjY2NjcgTDEuMzMzMzMsMTQuNjY2NyBMMS4zMzMzMywxMi42MDk1IEw2LjY5Nzg3LDcuMjQ0OTQgQzYuODc1MDIsNy4wNjc3OSA2LjkzNzksNi44MDYyOSA2Ljg2MDY1LDYuNTY3OTggQzYuNzM0ODksNi4xNzk5NyA2LjY2NjY3LDUuNzY1MjcgNi42NjY2Nyw1LjMzMzMzIFoiIGlkPSJTaGFwZSI+PC9wYXRoPgogICAgICAgIDwvZz4KICAgIDwvZz4KPC9zdmc+';
exports.ddgPasswordIconBaseWhite = ddgPasswordIconBaseWhite;
const ddgPasswordIconFilled = 'data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB3aWR0aD0iMjRweCIgaGVpZ2h0PSIyNHB4IiB2aWV3Qm94PSIwIDAgMjQgMjQiIHZlcnNpb249IjEuMSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+CiAgICA8dGl0bGU+ZGRnLXBhc3N3b3JkLWljb24tZmlsbGVkPC90aXRsZT4KICAgIDxnIGlkPSJkZGctcGFzc3dvcmQtaWNvbi1maWxsZWQiIHN0cm9rZT0ibm9uZSIgc3Ryb2tlLXdpZHRoPSIxIiBmaWxsPSJub25lIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiPgogICAgICAgIDxnIGlkPSJTaGFwZSIgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoNC4wMDAwMDAsIDQuMDAwMDAwKSIgZmlsbD0iIzc2NDMxMCI+CiAgICAgICAgICAgIDxwYXRoIGQ9Ik0xMS4yNSwyLjc1IEMxMC4xNDU0LDIuNzUgOS4yNSwzLjY0NTQzIDkuMjUsNC43NSBDOS4yNSw1Ljg1NDU3IDEwLjE0NTQsNi43NSAxMS4yNSw2Ljc1IEMxMi4zNTQ2LDYuNzUgMTMuMjUsNS44NTQ1NyAxMy4yNSw0Ljc1IEMxMy4yNSwzLjY0NTQzIDEyLjM1NDYsMi43NSAxMS4yNSwyLjc1IFogTTEwLjc1LDQuNzUgQzEwLjc1LDQuNDczODYgMTAuOTczOSw0LjI1IDExLjI1LDQuMjUgQzExLjUyNjEsNC4yNSAxMS43NSw0LjQ3Mzg2IDExLjc1LDQuNzUgQzExLjc1LDUuMDI2MTQgMTEuNTI2MSw1LjI1IDExLjI1LDUuMjUgQzEwLjk3MzksNS4yNSAxMC43NSw1LjAyNjE0IDEwLjc1LDQuNzUgWiI+PC9wYXRoPgogICAgICAgICAgICA8cGF0aCBkPSJNMTAuNjI1LDAgQzcuNjU2NDcsMCA1LjI1LDIuNDA2NDcgNS4yNSw1LjM3NSBDNS4yNSw1Ljc4MDk4IDUuMjk1MTQsNi4xNzcxNCA1LjM4MDg4LDYuNTU4NDYgTDAuMjE5NjcsMTEuNzE5NyBDMC4wNzkwMTc2LDExLjg2MDMgMCwxMi4wNTExIDAsMTIuMjUgTDAsMTUuMjUgQzAsMTUuNjY0MiAwLjMzNTc4NiwxNiAwLjc1LDE2IEwzLjc0NjYxLDE2IEM0LjMwMDc2LDE2IDQuNzUsMTUuNTUwOCA0Ljc1LDE0Ljk5NjYgTDQuNzUsMTQgTDUuNzQ2NjEsMTQgQzYuMzAwNzYsMTQgNi43NSwxMy41NTA4IDYuNzUsMTIuOTk2NiBMNi43NSwxMS41IEw4LDExLjUgQzguMTk4OTEsMTEuNSA4LjM4OTY4LDExLjQyMSA4LjUzMDMzLDExLjI4MDMgTDkuMjQwNzgsMTAuNTY5OSBDOS42ODMwNCwxMC42ODc1IDEwLjE0NzIsMTAuNzUgMTAuNjI1LDEwLjc1IEMxMy41OTM1LDEwLjc1IDE2LDguMzQzNTMgMTYsNS4zNzUgQzE2LDIuNDA2NDcgMTMuNTkzNSwwIDEwLjYyNSwwIFogTTYuNzUsNS4zNzUgQzYuNzUsMy4yMzQ5IDguNDg0OSwxLjUgMTAuNjI1LDEuNSBDMTIuNzY1MSwxLjUgMTQuNSwzLjIzNDkgMTQuNSw1LjM3NSBDMTQuNSw3LjUxNTEgMTIuNzY1MSw5LjI1IDEwLjYyNSw5LjI1IEMxMC4xNTQ1LDkuMjUgOS43MDUyOCw5LjE2NjUgOS4yOTAxMSw5LjAxNDE2IEM5LjAxNTgxLDguOTEzNSA4LjcwODAzLDguOTgxMzEgOC41MDE0Miw5LjE4NzkyIEw3LjY4OTM0LDEwIEw2LDEwIEM1LjU4NTc5LDEwIDUuMjUsMTAuMzM1OCA1LjI1LDEwLjc1IEw1LjI1LDEyLjUgTDQsMTIuNSBDMy41ODU3OSwxMi41IDMuMjUsMTIuODM1OCAzLjI1LDEzLjI1IEwzLjI1LDE0LjUgTDEuNSwxNC41IEwxLjUsMTIuNTYwNyBMNi43NDgyNiw3LjMxMjQgQzYuOTQ2NjYsNy4xMTQgNy4wMTc3Myw2LjgyMTQ1IDYuOTMyNDUsNi41NTQxMyBDNi44MTQxNSw2LjE4MzI3IDYuNzUsNS43ODczNSA2Ljc1LDUuMzc1IFoiPjwvcGF0aD4KICAgICAgICA8L2c+CiAgICA8L2c+Cjwvc3ZnPg==';
exports.ddgPasswordIconFilled = ddgPasswordIconFilled;
const ddgPasswordIconFocused = 'data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB3aWR0aD0iMjRweCIgaGVpZ2h0PSIyNHB4IiB2aWV3Qm94PSIwIDAgMjQgMjQiIHZlcnNpb249IjEuMSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+CiAgICA8dGl0bGU+ZGRnLXBhc3N3b3JkLWljb24tZm9jdXNlZDwvdGl0bGU+CiAgICA8ZyBpZD0iZGRnLXBhc3N3b3JkLWljb24tZm9jdXNlZCIgc3Ryb2tlPSJub25lIiBzdHJva2Utd2lkdGg9IjEiIGZpbGw9Im5vbmUiIGZpbGwtcnVsZT0iZXZlbm9kZCI+CiAgICAgICAgPGcgaWQ9Ikljb24tQ29udGFpbmVyIiBmaWxsPSIjMDAwMDAwIj4KICAgICAgICAgICAgPHJlY3QgaWQ9IlJlY3RhbmdsZSIgZmlsbC1vcGFjaXR5PSIwLjEiIGZpbGwtcnVsZT0ibm9uemVybyIgeD0iMCIgeT0iMCIgd2lkdGg9IjI0IiBoZWlnaHQ9IjI0IiByeD0iMTIiPjwvcmVjdD4KICAgICAgICAgICAgPGcgaWQ9Ikdyb3VwIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSg0LjAwMDAwMCwgNC4wMDAwMDApIiBmaWxsLW9wYWNpdHk9IjAuOSI+CiAgICAgICAgICAgICAgICA8cGF0aCBkPSJNMTEuMjUsMi43NSBDMTAuMTQ1NCwyLjc1IDkuMjUsMy42NDU0MyA5LjI1LDQuNzUgQzkuMjUsNS44NTQ1NyAxMC4xNDU0LDYuNzUgMTEuMjUsNi43NSBDMTIuMzU0Niw2Ljc1IDEzLjI1LDUuODU0NTcgMTMuMjUsNC43NSBDMTMuMjUsMy42NDU0MyAxMi4zNTQ2LDIuNzUgMTEuMjUsMi43NSBaIE0xMC43NSw0Ljc1IEMxMC43NSw0LjQ3Mzg2IDEwLjk3MzksNC4yNSAxMS4yNSw0LjI1IEMxMS41MjYxLDQuMjUgMTEuNzUsNC40NzM4NiAxMS43NSw0Ljc1IEMxMS43NSw1LjAyNjE0IDExLjUyNjEsNS4yNSAxMS4yNSw1LjI1IEMxMC45NzM5LDUuMjUgMTAuNzUsNS4wMjYxNCAxMC43NSw0Ljc1IFoiIGlkPSJTaGFwZSI+PC9wYXRoPgogICAgICAgICAgICAgICAgPHBhdGggZD0iTTEwLjYyNSwwIEM3LjY1NjUsMCA1LjI1LDIuNDA2NDcgNS4yNSw1LjM3NSBDNS4yNSw1Ljc4MDk4IDUuMjk1MTQsNi4xNzcxIDUuMzgwODgsNi41NTg1IEwwLjIxOTY3LDExLjcxOTcgQzAuMDc5MDIsMTEuODYwMyAwLDEyLjA1MTEgMCwxMi4yNSBMMCwxNS4yNSBDMCwxNS42NjQyIDAuMzM1NzksMTYgMC43NSwxNiBMMy43NDY2MSwxNiBDNC4zMDA3NiwxNiA0Ljc1LDE1LjU1MDggNC43NSwxNC45OTY2IEw0Ljc1LDE0IEw1Ljc0NjYxLDE0IEM2LjMwMDgsMTQgNi43NSwxMy41NTA4IDYuNzUsMTIuOTk2NiBMNi43NSwxMS41IEw4LDExLjUgQzguMTk4OSwxMS41IDguMzg5NywxMS40MjEgOC41MzAzLDExLjI4MDMgTDkuMjQwOCwxMC41Njk5IEM5LjY4MywxMC42ODc1IDEwLjE0NzIsMTAuNzUgMTAuNjI1LDEwLjc1IEMxMy41OTM1LDEwLjc1IDE2LDguMzQzNSAxNiw1LjM3NSBDMTYsMi40MDY0NyAxMy41OTM1LDAgMTAuNjI1LDAgWiBNNi43NSw1LjM3NSBDNi43NSwzLjIzNDkgOC40ODQ5LDEuNSAxMC42MjUsMS41IEMxMi43NjUxLDEuNSAxNC41LDMuMjM0OSAxNC41LDUuMzc1IEMxNC41LDcuNTE1MSAxMi43NjUxLDkuMjUgMTAuNjI1LDkuMjUgQzEwLjE1NDUsOS4yNSA5LjcwNTMsOS4xNjY1IDkuMjkwMSw5LjAxNDIgQzkuMDE1OCw4LjkxMzUgOC43MDgsOC45ODEzIDguNTAxNCw5LjE4NzkgTDcuNjg5MywxMCBMNiwxMCBDNS41ODU3OSwxMCA1LjI1LDEwLjMzNTggNS4yNSwxMC43NSBMNS4yNSwxMi41IEw0LDEyLjUgQzMuNTg1NzksMTIuNSAzLjI1LDEyLjgzNTggMy4yNSwxMy4yNSBMMy4yNSwxNC41IEwxLjUsMTQuNSBMMS41LDEyLjU2MDcgTDYuNzQ4Myw3LjMxMjQgQzYuOTQ2Nyw3LjExNCA3LjAxNzcsNi44MjE0IDYuOTMyNSw2LjU1NDEgQzYuODE0MSw2LjE4MzMgNi43NSw1Ljc4NzM1IDYuNzUsNS4zNzUgWiIgaWQ9IlNoYXBlIj48L3BhdGg+CiAgICAgICAgICAgIDwvZz4KICAgICAgICA8L2c+CiAgICA8L2c+Cjwvc3ZnPg==';
exports.ddgPasswordIconFocused = ddgPasswordIconFocused;
const ddgCcIconBase = 'data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgZmlsbD0ibm9uZSI+CiAgICA8cGF0aCBkPSJNNSA5Yy0uNTUyIDAtMSAuNDQ4LTEgMXYyYzAgLjU1Mi40NDggMSAxIDFoM2MuNTUyIDAgMS0uNDQ4IDEtMXYtMmMwLS41NTItLjQ0OC0xLTEtMUg1eiIgZmlsbD0iIzAwMCIvPgogICAgPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xIDZjMC0yLjIxIDEuNzktNCA0LTRoMTRjMi4yMSAwIDQgMS43OSA0IDR2MTJjMCAyLjIxLTEuNzkgNC00IDRINWMtMi4yMSAwLTQtMS43OS00LTRWNnptNC0yYy0xLjEwNSAwLTIgLjg5NS0yIDJ2OWgxOFY2YzAtMS4xMDUtLjg5NS0yLTItMkg1em0wIDE2Yy0xLjEwNSAwLTItLjg5NS0yLTJoMThjMCAxLjEwNS0uODk1IDItMiAySDV6IiBmaWxsPSIjMDAwIi8+Cjwvc3ZnPgo=';
exports.ddgCcIconBase = ddgCcIconBase;
const ddgCcIconFilled = 'data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgZmlsbD0ibm9uZSI+CiAgICA8cGF0aCBkPSJNNSA5Yy0uNTUyIDAtMSAuNDQ4LTEgMXYyYzAgLjU1Mi40NDggMSAxIDFoM2MuNTUyIDAgMS0uNDQ4IDEtMXYtMmMwLS41NTItLjQ0OC0xLTEtMUg1eiIgZmlsbD0iIzc2NDMxMCIvPgogICAgPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xIDZjMC0yLjIxIDEuNzktNCA0LTRoMTRjMi4yMSAwIDQgMS43OSA0IDR2MTJjMCAyLjIxLTEuNzkgNC00IDRINWMtMi4yMSAwLTQtMS43OS00LTRWNnptNC0yYy0xLjEwNSAwLTIgLjg5NS0yIDJ2OWgxOFY2YzAtMS4xMDUtLjg5NS0yLTItMkg1em0wIDE2Yy0xLjEwNSAwLTItLjg5NS0yLTJoMThjMCAxLjEwNS0uODk1IDItMiAySDV6IiBmaWxsPSIjNzY0MzEwIi8+Cjwvc3ZnPgo=';
exports.ddgCcIconFilled = ddgCcIconFilled;
const ddgIdentityIconBase = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgZmlsbD0ibm9uZSI+CiAgICA8cGF0aCBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0iTTEyIDIxYzIuMTQzIDAgNC4xMTEtLjc1IDUuNjU3LTItLjYyNi0uNTA2LTEuMzE4LS45MjctMi4wNi0xLjI1LTEuMS0uNDgtMi4yODUtLjczNS0zLjQ4Ni0uNzUtMS4yLS4wMTQtMi4zOTIuMjExLTMuNTA0LjY2NC0uODE3LjMzMy0xLjU4Ljc4My0yLjI2NCAxLjMzNiAxLjU0NiAxLjI1IDMuNTE0IDIgNS42NTcgMnptNC4zOTctNS4wODNjLjk2Ny40MjIgMS44NjYuOTggMi42NzIgMS42NTVDMjAuMjc5IDE2LjAzOSAyMSAxNC4xMDQgMjEgMTJjMC00Ljk3LTQuMDMtOS05LTlzLTkgNC4wMy05IDljMCAyLjEwNC43MjIgNC4wNCAxLjkzMiA1LjU3Mi44NzQtLjczNCAxLjg2LTEuMzI4IDIuOTIxLTEuNzYgMS4zNi0uNTU0IDIuODE2LS44MyA0LjI4My0uODExIDEuNDY3LjAxOCAyLjkxNi4zMyA0LjI2LjkxNnpNMTIgMjNjNi4wNzUgMCAxMS00LjkyNSAxMS0xMVMxOC4wNzUgMSAxMiAxIDEgNS45MjUgMSAxMnM0LjkyNSAxMSAxMSAxMXptMy0xM2MwIDEuNjU3LTEuMzQzIDMtMyAzcy0zLTEuMzQzLTMtMyAxLjM0My0zIDMtMyAzIDEuMzQzIDMgM3ptMiAwYzAgMi43NjEtMi4yMzkgNS01IDVzLTUtMi4yMzktNS01IDIuMjM5LTUgNS01IDUgMi4yMzkgNSA1eiIgZmlsbD0iIzAwMCIvPgo8L3N2Zz4KPHBhdGggeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0iTTEyIDIxYzIuMTQzIDAgNC4xMTEtLjc1IDUuNjU3LTItLjYyNi0uNTA2LTEuMzE4LS45MjctMi4wNi0xLjI1LTEuMS0uNDgtMi4yODUtLjczNS0zLjQ4Ni0uNzUtMS4yLS4wMTQtMi4zOTIuMjExLTMuNTA0LjY2NC0uODE3LjMzMy0xLjU4Ljc4My0yLjI2NCAxLjMzNiAxLjU0NiAxLjI1IDMuNTE0IDIgNS42NTcgMnptNC4zOTctNS4wODNjLjk2Ny40MjIgMS44NjYuOTggMi42NzIgMS42NTVDMjAuMjc5IDE2LjAzOSAyMSAxNC4xMDQgMjEgMTJjMC00Ljk3LTQuMDMtOS05LTlzLTkgNC4wMy05IDljMCAyLjEwNC43MjIgNC4wNCAxLjkzMiA1LjU3Mi44NzQtLjczNCAxLjg2LTEuMzI4IDIuOTIxLTEuNzYgMS4zNi0uNTU0IDIuODE2LS44MyA0LjI4My0uODExIDEuNDY3LjAxOCAyLjkxNi4zMyA0LjI2LjkxNnpNMTIgMjNjNi4wNzUgMCAxMS00LjkyNSAxMS0xMVMxOC4wNzUgMSAxMiAxIDEgNS45MjUgMSAxMnM0LjkyNSAxMSAxMSAxMXptMy0xM2MwIDEuNjU3LTEuMzQzIDMtMyAzcy0zLTEuMzQzLTMtMyAxLjM0My0zIDMtMyAzIDEuMzQzIDMgM3ptMiAwYzAgMi43NjEtMi4yMzkgNS01IDVzLTUtMi4yMzktNS01IDIuMjM5LTUgNS01IDUgMi4yMzkgNSA1eiIgZmlsbD0iIzAwMCIvPgo8c3ZnIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgd2lkdGg9IjI0IiBoZWlnaHQ9IjI0IiBmaWxsPSJub25lIj4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xMiAyMWMyLjE0MyAwIDQuMTExLS43NSA1LjY1Ny0yLS42MjYtLjUwNi0xLjMxOC0uOTI3LTIuMDYtMS4yNS0xLjEtLjQ4LTIuMjg1LS43MzUtMy40ODYtLjc1LTEuMi0uMDE0LTIuMzkyLjIxMS0zLjUwNC42NjQtLjgxNy4zMzMtMS41OC43ODMtMi4yNjQgMS4zMzYgMS41NDYgMS4yNSAzLjUxNCAyIDUuNjU3IDJ6bTQuMzk3LTUuMDgzYy45NjcuNDIyIDEuODY2Ljk4IDIuNjcyIDEuNjU1QzIwLjI3OSAxNi4wMzkgMjEgMTQuMTA0IDIxIDEyYzAtNC45Ny00LjAzLTktOS05cy05IDQuMDMtOSA5YzAgMi4xMDQuNzIyIDQuMDQgMS45MzIgNS41NzIuODc0LS43MzQgMS44Ni0xLjMyOCAyLjkyMS0xLjc2IDEuMzYtLjU1NCAyLjgxNi0uODMgNC4yODMtLjgxMSAxLjQ2Ny4wMTggMi45MTYuMzMgNC4yNi45MTZ6TTEyIDIzYzYuMDc1IDAgMTEtNC45MjUgMTEtMTFTMTguMDc1IDEgMTIgMSAxIDUuOTI1IDEgMTJzNC45MjUgMTEgMTEgMTF6bTMtMTNjMCAxLjY1Ny0xLjM0MyAzLTMgM3MtMy0xLjM0My0zLTMgMS4zNDMtMyAzLTMgMyAxLjM0MyAzIDN6bTIgMGMwIDIuNzYxLTIuMjM5IDUtNSA1cy01LTIuMjM5LTUtNSAyLjIzOS01IDUtNSA1IDIuMjM5IDUgNXoiIGZpbGw9IiMwMDAiLz4KPC9zdmc+Cg==";
exports.ddgIdentityIconBase = ddgIdentityIconBase;

},{}],49:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.CSS_STYLES = void 0;
const CSS_STYLES = ".wrapper *, .wrapper *::before, .wrapper *::after {\n    box-sizing: border-box;\n}\n.wrapper {\n    position: fixed;\n    top: 0;\n    left: 0;\n    padding: 0;\n    font-family: 'DDG_ProximaNova', 'Proxima Nova', -apple-system,\n    BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu',\n    'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif;\n    -webkit-font-smoothing: antialiased;\n    /* move it offscreen to avoid flashing */\n    transform: translate(-1000px);\n    z-index: 2147483647;\n}\n:not(.top-autofill).wrapper--data {\n    font-family: 'SF Pro Text', -apple-system,\n    BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu',\n    'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif;\n}\n:not(.top-autofill) .tooltip {\n    position: absolute;\n    width: 300px;\n    max-width: calc(100vw - 25px);\n    z-index: 2147483647;\n}\n.tooltip--data, #topAutofill {\n    background-color: rgba(242, 240, 240, 0.9);\n    -webkit-backdrop-filter: blur(40px);\n    backdrop-filter: blur(40px);\n}\n.tooltip--data {\n    padding: 6px;\n    font-size: 13px;\n    line-height: 14px;\n    width: 315px;\n}\n:not(.top-autofill) .tooltip--data {\n    top: 100%;\n    left: 100%;\n    border: 0.5px solid rgba(0, 0, 0, 0.2);\n    border-radius: 6px;\n    box-shadow: 0 10px 20px rgba(0, 0, 0, 0.32);\n}\n:not(.top-autofill) .tooltip--email {\n    top: calc(100% + 6px);\n    right: calc(100% - 46px);\n    padding: 8px;\n    border: 1px solid #D0D0D0;\n    border-radius: 10px;\n    background-color: #FFFFFF;\n    font-size: 14px;\n    line-height: 1.3;\n    color: #333333;\n    box-shadow: 0 10px 20px rgba(0, 0, 0, 0.15);\n}\n.tooltip--email::before,\n.tooltip--email::after {\n    content: \"\";\n    width: 0;\n    height: 0;\n    border-left: 10px solid transparent;\n    border-right: 10px solid transparent;\n    display: block;\n    border-bottom: 8px solid #D0D0D0;\n    position: absolute;\n    right: 20px;\n}\n.tooltip--email::before {\n    border-bottom-color: #D0D0D0;\n    top: -9px;\n}\n.tooltip--email::after {\n    border-bottom-color: #FFFFFF;\n    top: -8px;\n}\n\n/* Buttons */\n.tooltip__button {\n    display: flex;\n    width: 100%;\n    padding: 8px 0px;\n    font-family: inherit;\n    color: inherit;\n    background: transparent;\n    border: none;\n    border-radius: 6px;\n}\n.tooltip__button.currentFocus,\n.tooltip__button:hover {\n    background-color: rgba(0, 121, 242, 0.8);\n    color: #FFFFFF;\n}\n\n/* Data autofill tooltip specific */\n.tooltip__button--data {\n    min-height: 48px;\n    flex-direction: row;\n    justify-content: flex-start;\n    font-size: inherit;\n    font-weight: 500;\n    line-height: 16px;\n    text-align: left;\n}\n.tooltip__button--data > * {\n    opacity: 0.9;\n}\n.tooltip__button--data:first-child {\n    margin-top: 0;\n}\n.tooltip__button--data:last-child {\n    margin-bottom: 0;\n}\n.tooltip__button--data::before {\n    content: '';\n    flex-shrink: 0;\n    display: block;\n    width: 32px;\n    height: 32px;\n    margin: 0 8px;\n    background-size: 24px 24px;\n    background-repeat: no-repeat;\n    background-position: center 1px;\n}\n.tooltip__button--data.currentFocus::before,\n.tooltip__button--data:hover::before {\n    filter: invert(100%);\n}\n.tooltip__button__text-container {\n    margin: auto 0;\n}\n.label {\n    display: block;\n    font-weight: 400;\n    letter-spacing: -0.25px;\n    color: rgba(0,0,0,.8);\n    line-height: 13px;\n}\n.label + .label {\n    margin-top: 5px;\n}\n.label.label--medium {\n    letter-spacing: -0.08px;\n    color: rgba(0,0,0,.9)\n}\n.label.label--small {\n    font-size: 11px;\n    font-weight: 400;\n    letter-spacing: 0.06px;\n    color: rgba(0,0,0,0.6);\n}\n.tooltip__button.currentFocus .label,\n.tooltip__button:hover .label,\n.tooltip__button.currentFocus .label,\n.tooltip__button:hover .label {\n    color: #FFFFFF;\n}\n\n/* Icons */\n.tooltip__button--data--credentials::before {\n    /* TODO: use dynamically from src/UI/img/ddgPasswordIcon.js */\n    background-image: url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjQiIGhlaWdodD0iMjQiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+CiAgPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik05LjYzNiA4LjY4MkM5LjYzNiA1LjU0NCAxMi4xOCAzIDE1LjMxOCAzIDE4LjQ1NiAzIDIxIDUuNTQ0IDIxIDguNjgyYzAgMy4xMzgtMi41NDQgNS42ODItNS42ODIgNS42ODItLjY5MiAwLTEuMzUzLS4xMjQtMS45NjQtLjM0OS0uMzcyLS4xMzctLjc5LS4wNDEtMS4wNjYuMjQ1bC0uNzEzLjc0SDEwYy0uNTUyIDAtMSAuNDQ4LTEgMXYySDdjLS41NTIgMC0xIC40NDgtMSAxdjJIM3YtMi44ODFsNi42NjgtNi42NjhjLjI2NS0uMjY2LjM2LS42NTguMjQ0LTEuMDE1LS4xNzktLjU1MS0uMjc2LTEuMTQtLjI3Ni0xLjc1NHpNMTUuMzE4IDFjLTQuMjQyIDAtNy42ODIgMy40NC03LjY4MiA3LjY4MiAwIC42MDcuMDcxIDEuMi4yMDUgMS43NjdsLTYuNTQ4IDYuNTQ4Yy0uMTg4LjE4OC0uMjkzLjQ0Mi0uMjkzLjcwOFYyMmMwIC4yNjUuMTA1LjUyLjI5My43MDcuMTg3LjE4OC40NDIuMjkzLjcwNy4yOTNoNGMxLjEwNSAwIDItLjg5NSAyLTJ2LTFoMWMxLjEwNSAwIDItLjg5NSAyLTJ2LTFoMWMuMjcyIDAgLjUzMi0uMTEuNzItLjMwNmwuNTc3LS42Yy42NDUuMTc2IDEuMzIzLjI3IDIuMDIxLjI3IDQuMjQzIDAgNy42ODItMy40NCA3LjY4Mi03LjY4MkMyMyA0LjQzOSAxOS41NiAxIDE1LjMxOCAxek0xNSA4YzAtLjU1Mi40NDgtMSAxLTFzMSAuNDQ4IDEgMS0uNDQ4IDEtMSAxLTEtLjQ0OC0xLTF6bTEtM2MtMS42NTcgMC0zIDEuMzQzLTMgM3MxLjM0MyAzIDMgMyAzLTEuMzQzIDMtMy0xLjM0My0zLTMtM3oiIGZpbGw9IiMwMDAiIGZpbGwtb3BhY2l0eT0iLjkiLz4KPC9zdmc+');\n}\n.tooltip__button--data--creditCards::before {\n    background-image: url('data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiPz4KPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgZmlsbD0ibm9uZSI+CiAgICA8cGF0aCBkPSJNNSA5Yy0uNTUyIDAtMSAuNDQ4LTEgMXYyYzAgLjU1Mi40NDggMSAxIDFoM2MuNTUyIDAgMS0uNDQ4IDEtMXYtMmMwLS41NTItLjQ0OC0xLTEtMUg1eiIgZmlsbD0iIzAwMCIvPgogICAgPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0xIDZjMC0yLjIxIDEuNzktNCA0LTRoMTRjMi4yMSAwIDQgMS43OSA0IDR2MTJjMCAyLjIxLTEuNzkgNC00IDRINWMtMi4yMSAwLTQtMS43OS00LTRWNnptNC0yYy0xLjEwNSAwLTIgLjg5NS0yIDJ2OWgxOFY2YzAtMS4xMDUtLjg5NS0yLTItMkg1em0wIDE2Yy0xLjEwNSAwLTItLjg5NS0yLTJoMThjMCAxLjEwNS0uODk1IDItMiAySDV6IiBmaWxsPSIjMDAwIi8+Cjwvc3ZnPgo=');\n}\n.tooltip__button--data--identities::before {\n    background-image: url('data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgZmlsbD0ibm9uZSI+CiAgICA8cGF0aCBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0iTTEyIDIxYzIuMTQzIDAgNC4xMTEtLjc1IDUuNjU3LTItLjYyNi0uNTA2LTEuMzE4LS45MjctMi4wNi0xLjI1LTEuMS0uNDgtMi4yODUtLjczNS0zLjQ4Ni0uNzUtMS4yLS4wMTQtMi4zOTIuMjExLTMuNTA0LjY2NC0uODE3LjMzMy0xLjU4Ljc4My0yLjI2NCAxLjMzNiAxLjU0NiAxLjI1IDMuNTE0IDIgNS42NTcgMnptNC4zOTctNS4wODNjLjk2Ny40MjIgMS44NjYuOTggMi42NzIgMS42NTVDMjAuMjc5IDE2LjAzOSAyMSAxNC4xMDQgMjEgMTJjMC00Ljk3LTQuMDMtOS05LTlzLTkgNC4wMy05IDljMCAyLjEwNC43MjIgNC4wNCAxLjkzMiA1LjU3Mi44NzQtLjczNCAxLjg2LTEuMzI4IDIuOTIxLTEuNzYgMS4zNi0uNTU0IDIuODE2LS44MyA0LjI4My0uODExIDEuNDY3LjAxOCAyLjkxNi4zMyA0LjI2LjkxNnpNMTIgMjNjNi4wNzUgMCAxMS00LjkyNSAxMS0xMVMxOC4wNzUgMSAxMiAxIDEgNS45MjUgMSAxMnM0LjkyNSAxMSAxMSAxMXptMy0xM2MwIDEuNjU3LTEuMzQzIDMtMyAzcy0zLTEuMzQzLTMtMyAxLjM0My0zIDMtMyAzIDEuMzQzIDMgM3ptMiAwYzAgMi43NjEtMi4yMzkgNS01IDVzLTUtMi4yMzktNS01IDIuMjM5LTUgNS01IDUgMi4yMzkgNSA1eiIgZmlsbD0iIzAwMCIvPgo8L3N2Zz4=');\n}\n\nhr {\n    display: block;\n    margin: 5px 10px;\n    border: none; /* reset the border */\n    border-top: 1px solid rgba(0,0,0,.1);\n}\n\nhr:first-child {\n    display: none;\n}\n\n#privateAddress {\n    align-items: flex-start;\n}\n#personalAddress::before,\n#privateAddress::before,\n#personalAddress.currentFocus::before,\n#personalAddress:hover::before,\n#privateAddress.currentFocus::before,\n#privateAddress:hover::before {\n    filter: none;\n    background-image: url('data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjI0IiB2aWV3Qm94PSIwIDAgNDQgNDQiIHdpZHRoPSIyNCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIiB4bWxuczp4bGluaz0iaHR0cDovL3d3dy53My5vcmcvMTk5OS94bGluayI+PGxpbmVhckdyYWRpZW50IGlkPSJhIj48c3RvcCBvZmZzZXQ9Ii4wMSIgc3RvcC1jb2xvcj0iIzYxNzZiOSIvPjxzdG9wIG9mZnNldD0iLjY5IiBzdG9wLWNvbG9yPSIjMzk0YTlmIi8+PC9saW5lYXJHcmFkaWVudD48bGluZWFyR3JhZGllbnQgaWQ9ImIiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMTMuOTI5NyIgeDI9IjE3LjA3MiIgeGxpbms6aHJlZj0iI2EiIHkxPSIxNi4zOTgiIHkyPSIxNi4zOTgiLz48bGluZWFyR3JhZGllbnQgaWQ9ImMiIGdyYWRpZW50VW5pdHM9InVzZXJTcGFjZU9uVXNlIiB4MT0iMjMuODExNSIgeDI9IjI2LjY3NTIiIHhsaW5rOmhyZWY9IiNhIiB5MT0iMTQuOTY3OSIgeTI9IjE0Ljk2NzkiLz48bWFzayBpZD0iZCIgaGVpZ2h0PSI0MCIgbWFza1VuaXRzPSJ1c2VyU3BhY2VPblVzZSIgd2lkdGg9IjQwIiB4PSIyIiB5PSIyIj48cGF0aCBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Im0yMi4wMDAzIDQxLjA2NjljMTAuNTMwMiAwIDE5LjA2NjYtOC41MzY0IDE5LjA2NjYtMTkuMDY2NiAwLTEwLjUzMDMtOC41MzY0LTE5LjA2NjcxLTE5LjA2NjYtMTkuMDY2NzEtMTAuNTMwMyAwLTE5LjA2NjcxIDguNTM2NDEtMTkuMDY2NzEgMTkuMDY2NzEgMCAxMC41MzAyIDguNTM2NDEgMTkuMDY2NiAxOS4wNjY3MSAxOS4wNjY2eiIgZmlsbD0iI2ZmZiIgZmlsbC1ydWxlPSJldmVub2RkIi8+PC9tYXNrPjxwYXRoIGNsaXAtcnVsZT0iZXZlbm9kZCIgZD0ibTIyIDQ0YzEyLjE1MDMgMCAyMi05Ljg0OTcgMjItMjIgMC0xMi4xNTAyNi05Ljg0OTctMjItMjItMjItMTIuMTUwMjYgMC0yMiA5Ljg0OTc0LTIyIDIyIDAgMTIuMTUwMyA5Ljg0OTc0IDIyIDIyIDIyeiIgZmlsbD0iI2RlNTgzMyIgZmlsbC1ydWxlPSJldmVub2RkIi8+PGcgbWFzaz0idXJsKCNkKSI+PHBhdGggY2xpcC1ydWxlPSJldmVub2RkIiBkPSJtMjYuMDgxMyA0MS42Mzg2Yy0uOTIwMy0xLjc4OTMtMS44MDAzLTMuNDM1Ni0yLjM0NjYtNC41MjQ2LTEuNDUyLTIuOTA3Ny0yLjkxMTQtNy4wMDctMi4yNDc3LTkuNjUwNy4xMjEtLjQ4MDMtMS4zNjc3LTE3Ljc4Njk5LTIuNDItMTguMzQ0MzItMS4xNjk3LS42MjMzMy0zLjcxMDctMS40NDQ2Ny01LjAyNy0xLjY2NDY3LS45MTY3LS4xNDY2Ni0xLjEyNTcuMTEtMS41MTA3LjE2ODY3LjM2My4wMzY2NyAyLjA5Ljg4NzMzIDIuNDIzNy45MzUtLjMzMzcuMjI3MzMtMS4zMi0uMDA3MzMtMS45NTA3LjI3MTMzLS4zMTkuMTQ2NjctLjU1NzMuNjg5MzQtLjU1Ljk0NiAxLjc5NjctLjE4MzMzIDQuNjA1NC0uMDAzNjYgNi4yNy43MzMyOS0xLjMyMzYuMTUwNC0zLjMzMy4zMTktNC4xOTgzLjc3MzctMi41MDggMS4zMi0zLjYxNTMgNC40MTEtMi45NTUzIDguMTE0My42NTYzIDMuNjk2IDMuNTY0IDE3LjE3ODQgNC40OTE2IDIxLjY4MS45MjQgNC40OTkgMTEuNTUzNyAzLjU1NjcgMTAuMDE3NC41NjF6IiBmaWxsPSIjZDVkN2Q4IiBmaWxsLXJ1bGU9ImV2ZW5vZGQiLz48cGF0aCBkPSJtMjIuMjg2NSAyNi44NDM5Yy0uNjYgMi42NDM2Ljc5MiA2LjczOTMgMi4yNDc2IDkuNjUwNi40ODkxLjk3MjcgMS4yNDM4IDIuMzkyMSAyLjA1NTggMy45NjM3LTEuODk0LjQ2OTMtNi40ODk1IDEuMTI2NC05LjcxOTEgMC0uOTI0LTQuNDkxNy0zLjgzMTctMTcuOTc3Ny00LjQ5NTMtMjEuNjgxLS42Ni0zLjcwMzMgMC02LjM0NyAyLjUxNTMtNy42NjcuODYxNy0uNDU0NyAyLjA5MzctLjc4NDcgMy40MTM3LS45MzEzLTEuNjY0Ny0uNzQwNy0zLjYzNzQtMS4wMjY3LTUuNDQxNC0uODQzMzYtLjAwNzMtLjc2MjY3IDEuMzM4NC0uNzE4NjcgMS44NDQ0LTEuMDYzMzQtLjMzMzctLjA0NzY2LTEuMTYyNC0uNzk1NjYtMS41MjktLjgzMjMzIDIuMjg4My0uMzkyNDQgNC42NDIzLS4wMjEzOCA2LjY5OSAxLjA1NiAxLjA0ODYuNTYxIDEuNzg5MyAxLjE2MjMzIDIuMjQ3NiAxLjc5MzAzIDEuMTk1NC4yMjczIDIuMjUxNC42NiAyLjk0MDcgMS4zNDkzIDIuMTE5MyAyLjExNTcgNC4wMTEzIDYuOTUyIDMuMjE5MyA5LjczMTMtLjIyMzYuNzctLjczMzMgMS4zMzEtMS4zNzEzIDEuNzk2Ny0xLjIzOTMuOTAyLTEuMDE5My0xLjA0NS00LjEwMy45NzE3LS4zOTk3LjI2MDMtLjM5OTcgMi4yMjU2LS41MjQzIDIuNzA2eiIgZmlsbD0iI2ZmZiIvPjwvZz48ZyBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGZpbGwtcnVsZT0iZXZlbm9kZCI+PHBhdGggZD0ibTE2LjY3MjQgMjAuMzU0Yy43Njc1IDAgMS4zODk2LS42MjIxIDEuMzg5Ni0xLjM4OTZzLS42MjIxLTEuMzg5Ny0xLjM4OTYtMS4zODk3LTEuMzg5Ny42MjIyLTEuMzg5NyAxLjM4OTcuNjIyMiAxLjM4OTYgMS4zODk3IDEuMzg5NnoiIGZpbGw9IiMyZDRmOGUiLz48cGF0aCBkPSJtMTcuMjkyNCAxOC44NjE3Yy4xOTg1IDAgLjM1OTQtLjE2MDguMzU5NC0uMzU5M3MtLjE2MDktLjM1OTMtLjM1OTQtLjM1OTNjLS4xOTg0IDAtLjM1OTMuMTYwOC0uMzU5My4zNTkzcy4xNjA5LjM1OTMuMzU5My4zNTkzeiIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Im0yNS45NTY4IDE5LjMzMTFjLjY1ODEgMCAxLjE5MTctLjUzMzUgMS4xOTE3LTEuMTkxNyAwLS42NTgxLS41MzM2LTEuMTkxNi0xLjE5MTctMS4xOTE2cy0xLjE5MTcuNTMzNS0xLjE5MTcgMS4xOTE2YzAgLjY1ODIuNTMzNiAxLjE5MTcgMS4xOTE3IDEuMTkxN3oiIGZpbGw9IiMyZDRmOGUiLz48cGF0aCBkPSJtMjYuNDg4MiAxOC4wNTExYy4xNzAxIDAgLjMwOC0uMTM3OS4zMDgtLjMwOHMtLjEzNzktLjMwOC0uMzA4LS4zMDgtLjMwOC4xMzc5LS4zMDguMzA4LjEzNzkuMzA4LjMwOC4zMDh6IiBmaWxsPSIjZmZmIi8+PHBhdGggZD0ibTE3LjA3MiAxNC45NDJzLTEuMDQ4Ni0uNDc2Ni0yLjA2NDMuMTY1Yy0xLjAxNTcuNjM4LS45NzkgMS4yOTA3LS45NzkgMS4yOTA3cy0uNTM5LTEuMjAyNy44OTgzLTEuNzkzYzEuNDQxLS41ODY3IDIuMTQ1LjMzNzMgMi4xNDUuMzM3M3oiIGZpbGw9InVybCgjYikiLz48cGF0aCBkPSJtMjYuNjc1MiAxNC44NDY3cy0uNzUxNy0uNDI5LTEuMzM4My0uNDIxN2MtMS4xOTkuMDE0Ny0xLjUyNTQuNTQyNy0xLjUyNTQuNTQyN3MuMjAxNy0xLjI2MTQgMS43MzQ0LTEuMDA4NGMuNDk5Ny4wOTE0LjkyMjMuNDIzNCAxLjEyOTMuODg3NHoiIGZpbGw9InVybCgjYykiLz48cGF0aCBkPSJtMjAuOTI1OCAyNC4zMjFjLjEzOTMtLjg0MzMgMi4zMS0yLjQzMSAzLjg1LTIuNTMgMS41NC0uMDk1MyAyLjAxNjctLjA3MzMgMy4zLS4zODEzIDEuMjg3LS4zMDQzIDQuNTk4LTEuMTI5MyA1LjUxMS0xLjU1NDcuOTE2Ny0uNDIxNiA0LjgwMzMuMjA5IDIuMDY0MyAxLjczOC0xLjE4NDMuNjYzNy00LjM3OCAxLjg4MS02LjY2MjMgMi41NjMtMi4yODA3LjY4Mi0zLjY2My0uNjUyNi00LjQyMi40Njk0LS42MDEzLjg5MS0uMTIxIDIuMTEyIDIuNjAzMyAyLjM2NSAzLjY4MTQuMzQxIDcuMjA4Ny0xLjY1NzQgNy41OTc0LS41OTQuMzg4NiAxLjA2MzMtMy4xNjA3IDIuMzgzMy01LjMyNCAyLjQyNzMtMi4xNjM0LjA0MDMtNi41MTk0LTEuNDMtNy4xNzItMS44ODQ3LS42NTY0LS40NTEtMS41MjU0LTEuNTE0My0xLjM0NTctMi42MTh6IiBmaWxsPSIjZmRkMjBhIi8+PHBhdGggZD0ibTI4Ljg4MjUgMzEuODM4NmMtLjc3NzMtLjE3MjQtNC4zMTIgMi41MDA2LTQuMzEyIDIuNTAwNmguMDAzN2wtLjE2NSAyLjA1MzRzNC4wNDA2IDEuNjUzNiA0LjczIDEuMzk3Yy42ODkzLS4yNjQuNTE3LTUuNzc1LS4yNTY3LTUuOTUxem0tMTEuNTQ2MyAxLjAzNGMuMDg0My0xLjExODQgNS4yNTQzIDEuNjQyNiA1LjI1NDMgMS42NDI2bC4wMDM3LS4wMDM2LjI1NjYgMi4xNTZzLTQuMzA4MyAyLjU4MTMtNC45MTMzIDIuMjM2NmMtLjYwMTMtLjM0NDYtLjY4OTMtNC45MDk2LS42MDEzLTYuMDMxNnoiIGZpbGw9IiM2NWJjNDYiLz48cGF0aCBkPSJtMjEuMzQgMzQuODA0OWMwIDEuODA3Ny0uMjYwNCAyLjU4NS41MTMzIDIuNzU3NC43NzczLjE3MjMgMi4yNDAzIDAgMi43NjEtLjM0NDcuNTEzMy0uMzQ0Ny4wODQzLTIuNjY5My0uMDg4LTMuMTAycy0zLjE5LS4wODgtMy4xOS42ODkzeiIgZmlsbD0iIzQzYTI0NCIvPjxwYXRoIGQ9Im0yMS42NzAxIDM0LjQwNTFjMCAxLjgwNzYtLjI2MDQgMi41ODEzLjUxMzMgMi43NTM2Ljc3MzcuMTc2IDIuMjM2NyAwIDIuNzU3My0uMzQ0Ni41MTctLjM0NDcuMDg4LTIuNjY5NC0uMDg0My0zLjEwMi0uMTcyMy0uNDMyNy0zLjE5LS4wODQ0LTMuMTkuNjg5M3oiIGZpbGw9IiM2NWJjNDYiLz48cGF0aCBkPSJtMjIuMDAwMiA0MC40NDgxYzEwLjE4ODUgMCAxOC40NDc5LTguMjU5NCAxOC40NDc5LTE4LjQ0NzlzLTguMjU5NC0xOC40NDc5NS0xOC40NDc5LTE4LjQ0Nzk1LTE4LjQ0Nzk1IDguMjU5NDUtMTguNDQ3OTUgMTguNDQ3OTUgOC4yNTk0NSAxOC40NDc5IDE4LjQ0Nzk1IDE4LjQ0Nzl6bTAgMS43MTg3YzExLjEzNzcgMCAyMC4xNjY2LTkuMDI4OSAyMC4xNjY2LTIwLjE2NjYgMC0xMS4xMzc4LTkuMDI4OS0yMC4xNjY3LTIwLjE2NjYtMjAuMTY2Ny0xMS4xMzc4IDAtMjAuMTY2NyA5LjAyODktMjAuMTY2NyAyMC4xNjY3IDAgMTEuMTM3NyA5LjAyODkgMjAuMTY2NiAyMC4xNjY3IDIwLjE2NjZ6IiBmaWxsPSIjZmZmIi8+PC9nPjwvc3ZnPg==');\n}\n\n/* Email tooltip specific */\n.tooltip__button--email {\n    flex-direction: column;\n    justify-content: center;\n    align-items: flex-start;\n    font-size: 14px;\n    padding: 4px 8px;\n}\n.tooltip__button--email__primary-text {\n    font-weight: bold;\n}\n.tooltip__button--email__secondary-text {\n    font-size: 12px;\n}\n";
exports.CSS_STYLES = CSS_STYLES;

},{}],50:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.createTooltip = createTooltip;

var _NativeTooltip = require("./NativeTooltip");

var _WebTooltip = require("./WebTooltip");

var _OverlayControllerTooltip = require("./OverlayControllerTooltip");

/**
 * @param {GlobalConfig} globalConfig
 * @param {import('@duckduckgo/content-scope-scripts').RuntimeConfiguration} platformConfig
 * @param {import('../settings/settings').Settings} _autofillSettings
 * @returns {TooltipInterface}
 */
function createTooltip(globalConfig, platformConfig, _autofillSettings) {
  switch (platformConfig.platform) {
    case 'macos':
    case 'windows':
      {
        if (globalConfig.supportsTopFrame) {
          if (globalConfig.isTopFrame) {
            return new _WebTooltip.WebTooltip({
              tooltipKind: 'modern'
            });
          } else {
            return new _OverlayControllerTooltip.OverlayControllerTooltip();
          }
        }

        return new _WebTooltip.WebTooltip({
          tooltipKind: 'modern'
        });
      }

    case 'android':
    case 'ios':
      {
        return new _NativeTooltip.NativeTooltip();
      }

    case 'extension':
      {
        return new _WebTooltip.WebTooltip({
          tooltipKind: 'legacy'
        });
      }

    case 'unknown':
      throw new Error('unreachable. tooltipHandler platform was "unknown"');
  }

  throw new Error('undefined');
}

},{"./NativeTooltip":44,"./OverlayControllerTooltip":45,"./WebTooltip":47}],51:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.addInlineStyles = exports.SIGN_IN_MSG = exports.ADDRESS_DOMAIN = void 0;
exports.escapeXML = escapeXML;
exports.setValue = exports.sendAndWaitForAnswer = exports.safeExecute = exports.removeInlineStyles = exports.notifyWebApp = exports.isVisible = exports.isLikelyASubmitButton = exports.isEventWithinDax = exports.getDaxBoundingBox = exports.formatDuckAddress = void 0;

var _matching = require("./Form/matching");

const SIGN_IN_MSG = {
  signMeIn: true
}; // Send a message to the web app (only on DDG domains)

exports.SIGN_IN_MSG = SIGN_IN_MSG;

const notifyWebApp = message => {
  window.postMessage(message, window.origin);
};
/**
 * Sends a message and returns a Promise that resolves with the response
 * @param {{} | Function} msgOrFn - a fn to call or an object to send via postMessage
 * @param {String} expectedResponse - the name of the response
 * @returns {Promise<*>}
 */


exports.notifyWebApp = notifyWebApp;

const sendAndWaitForAnswer = (msgOrFn, expectedResponse) => {
  if (typeof msgOrFn === 'function') {
    msgOrFn();
  } else {
    window.postMessage(msgOrFn, window.origin);
  }

  return new Promise(resolve => {
    const handler = e => {
      if (e.origin !== window.origin) {
        console.log("\u274C origin-mismatch e.origin(".concat(e.origin, ") !== window.origin(").concat(window.origin, ")"));
        return;
      }

      if (!e.data) {
        console.log('❌ event.data missing');
        return;
      }

      if (!(e.data[expectedResponse] || e.data.type === expectedResponse)) {
        console.log('❌ event.data or event.data.type mismatch', JSON.stringify(e.data));
        return;
      }

      resolve(e.data);
      window.removeEventListener('message', handler);
    };

    window.addEventListener('message', handler);
  });
}; // Access the original setter (needed to bypass React's implementation on mobile)
// @ts-ignore


exports.sendAndWaitForAnswer = sendAndWaitForAnswer;
const originalSet = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
/**
 * Ensures the value is set properly and dispatches events to simulate real user action
 * @param {HTMLInputElement} el
 * @param {string} val
 * @param {GlobalConfig} [config]
 * @return {boolean}
 */

const setValueForInput = (el, val, config) => {
  // Avoid keyboard flashing on Android
  if (!(config !== null && config !== void 0 && config.isAndroid)) {
    el.focus();
  } // todo(Shane): Not sending a 'key' property on these events can cause exceptions on 3rd party listeners that expect it


  el.dispatchEvent(new Event('keydown', {
    bubbles: true
  }));
  originalSet === null || originalSet === void 0 ? void 0 : originalSet.call(el, val);
  const events = [new Event('input', {
    bubbles: true
  }), // todo(Shane): Not sending a 'key' property on these events can cause exceptions on 3rd party listeners that expect it
  new Event('keyup', {
    bubbles: true
  }), new Event('change', {
    bubbles: true
  })];
  events.forEach(ev => el.dispatchEvent(ev)); // We call this again to make sure all forms are happy

  originalSet === null || originalSet === void 0 ? void 0 : originalSet.call(el, val);
  events.forEach(ev => el.dispatchEvent(ev));
  el.blur();
  return true;
};
/**
 * Fires events on a select element to simulate user interaction
 * @param {HTMLSelectElement} el
 */


const fireEventsOnSelect = el => {
  /** @type {Event[]} */
  const events = [new Event('mousedown', {
    bubbles: true
  }), new Event('mouseup', {
    bubbles: true
  }), new Event('click', {
    bubbles: true
  }), new Event('change', {
    bubbles: true
  })]; // Events fire on the select el, not option

  events.forEach(ev => el.dispatchEvent(ev));
  events.forEach(ev => el.dispatchEvent(ev));
  el.blur();
};
/**
 * Selects an option of a select element
 * We assume Select is only used for dates, i.e. in the credit card
 * @param {HTMLSelectElement} el
 * @param {string} val
 * @return {boolean}
 */


const setValueForSelect = (el, val) => {
  const subtype = (0, _matching.getInputSubtype)(el);
  const isMonth = subtype.includes('Month');
  const isZeroBasedNumber = isMonth && el.options[0].value === '0' && el.options.length === 12; // Loop first through all values because they tend to be more precise

  for (const option of el.options) {
    // If values for months are zero-based (Jan === 0), add one to match our data type
    let value = option.value;

    if (isZeroBasedNumber) {
      value = "".concat(Number(value) + 1);
    } // TODO: try to match localised month names


    if (value === String(val)) {
      if (option.selected) return false;
      option.selected = true;
      fireEventsOnSelect(el);
      return true;
    }
  }

  for (const option of el.options) {
    if (option.innerText === String(val)) {
      if (option.selected) return false;
      option.selected = true;
      fireEventsOnSelect(el);
      return true;
    }
  } // If we didn't find a matching option return false


  return false;
};
/**
 * Sets or selects a value to a form element
 * @param {HTMLInputElement | HTMLSelectElement} el
 * @param {string} val
 * @param {GlobalConfig} [config]
 * @return {boolean}
 */


const setValue = (el, val, config) => {
  if (el instanceof HTMLInputElement) return setValueForInput(el, val, config);
  if (el instanceof HTMLSelectElement) return setValueForSelect(el, val);
  return false;
};
/**
 * Use IntersectionObserver v2 to make sure the element is visible when clicked
 * https://developers.google.com/web/updates/2019/02/intersectionobserver-v2
 */


exports.setValue = setValue;

const safeExecute = (el, fn) => {
  const intObs = new IntersectionObserver(changes => {
    for (const change of changes) {
      // Feature detection
      if (typeof change.isVisible === 'undefined') {
        // The browser doesn't support Intersection Observer v2, falling back to v1 behavior.
        change.isVisible = true;
      } // todo(Shane): Why is this broken on windows...


      if (change.isIntersecting) {
        fn();
      }
    }

    intObs.disconnect();
  }, {
    trackVisibility: true,
    delay: 100
  });
  intObs.observe(el);
};
/**
 * Checks that an element is potentially viewable (even if off-screen)
 * @param {HTMLElement} el
 * @return {boolean}
 */


exports.safeExecute = safeExecute;

const isVisible = el => el.clientWidth !== 0 && el.clientHeight !== 0 && (el.style.opacity !== '' ? parseFloat(el.style.opacity) > 0 : true);
/**
 * Gets the bounding box of the icon
 * @param {HTMLInputElement} input
 * @returns {{top: number, left: number, bottom: number, width: number, x: number, y: number, right: number, height: number}}
 */


exports.isVisible = isVisible;

const getDaxBoundingBox = input => {
  const {
    right: inputRight,
    top: inputTop,
    height: inputHeight
  } = input.getBoundingClientRect();
  const inputRightPadding = parseInt(getComputedStyle(input).paddingRight);
  const width = 30;
  const height = 30;
  const top = inputTop + (inputHeight - height) / 2;
  const right = inputRight - inputRightPadding;
  const left = right - width;
  const bottom = top + height;
  return {
    bottom,
    height,
    left,
    right,
    top,
    width,
    x: left,
    y: top
  };
};
/**
 * Check if a mouse event is within the icon
 * @param {MouseEvent} e
 * @param {HTMLInputElement} input
 * @returns {boolean}
 */


exports.getDaxBoundingBox = getDaxBoundingBox;

const isEventWithinDax = (e, input) => {
  const {
    left,
    right,
    top,
    bottom
  } = getDaxBoundingBox(input);
  const withinX = e.clientX >= left && e.clientX <= right;
  const withinY = e.clientY >= top && e.clientY <= bottom;
  return withinX && withinY;
};
/**
 * Adds inline styles from a prop:value object
 * @param {HTMLElement} el
 * @param {Object<string, string>} styles
 */


exports.isEventWithinDax = isEventWithinDax;

const addInlineStyles = (el, styles) => Object.entries(styles).forEach(_ref => {
  let [property, val] = _ref;
  return el.style.setProperty(property, val, 'important');
});
/**
 * Removes inline styles from a prop:value object
 * @param {HTMLElement} el
 * @param {Object<string, string>} styles
 */


exports.addInlineStyles = addInlineStyles;

const removeInlineStyles = (el, styles) => Object.keys(styles).forEach(property => el.style.removeProperty(property));

exports.removeInlineStyles = removeInlineStyles;
const ADDRESS_DOMAIN = '@duck.com';
/**
 * Given a username, returns the full email address
 * @param {string} address
 * @returns {string}
 */

exports.ADDRESS_DOMAIN = ADDRESS_DOMAIN;

const formatDuckAddress = address => address + ADDRESS_DOMAIN;
/**
 * Escapes any occurrences of &, ", <, > or / with XML entities.
 * @param {string} str The string to escape.
 * @return {string} The escaped string.
 */


exports.formatDuckAddress = formatDuckAddress;

function escapeXML(str) {
  const replacements = {
    '&': '&amp;',
    '"': '&quot;',
    "'": '&apos;',
    '<': '&lt;',
    '>': '&gt;',
    '/': '&#x2F;'
  };
  return String(str).replace(/[&"'<>/]/g, m => replacements[m]);
}

const SUBMIT_BUTTON_REGEX = /submit|send|confirm|save|continue|sign|log.?([io])n|buy|purchase|check.?out|subscribe|donate/i;
const SUBMIT_BUTTON_UNLIKELY_REGEX = /facebook|twitter|google|apple|cancel|password|show|toggle|reveal|hide/i;
/**
 * Determines if an element is likely to be a submit button
 * @param {HTMLElement} el A button, input, anchor or other element with role=button
 * @return {boolean}
 */

const isLikelyASubmitButton = el => {
  const text = el.textContent || '';
  const ariaLabel = el.getAttribute('aria-label') || '';
  const title = el.title || '';
  const value = el instanceof HTMLInputElement ? el.value || '' : '';
  const contentExcludingLabel = text + ' ' + title + ' ' + value;
  return (el.getAttribute('type') === 'submit' || // is explicitly set as "submit"
  /primary|submit/i.test(el.className) || // has high-signal submit classes
  SUBMIT_BUTTON_REGEX.test(contentExcludingLabel) || // has high-signal text
  el.offsetHeight * el.offsetWidth >= 10000) && // it's a large element, at least 250x40px
  !SUBMIT_BUTTON_UNLIKELY_REGEX.test(contentExcludingLabel + ' ' + ariaLabel);
};

exports.isLikelyASubmitButton = isLikelyASubmitButton;

},{"./Form/matching":33}],52:[function(require,module,exports){
"use strict";

require("./requestIdleCallback");

require("./senders/captureDdgGlobals");

var _DeviceInterface = require("./DeviceInterface");

var _config = require("./config");

var _tooltips = require("./UI/tooltips");

var _settings = require("./settings/settings");

var _contentScopeScripts = require("@duckduckgo/content-scope-scripts");

var _messages = require("./messages/messages");

var _createSender = require("./senders/create-sender");

// Polyfills/shims
(async () => {
  if (!window.isSecureContext) return false;

  try {
    var _tooltip$setDevice;

    // this is config already present in the script, or derived from the page etc.
    const globalConfig = (0, _config.createGlobalConfig)(); // if (globalConfig.isDDGTestMode) {

    console.log('globalConfig', globalConfig); // }
    // Transport is needed very early because we may need to fetch initial configuration, before any
    // autofill logic can run...

    const sender = (0, _createSender.createSender)(globalConfig); // Get runtime configuration - this may include messaging

    const runtimeConfiguration = await getRuntimeConfiguration(sender); // Autofill settings need to be derived from runtime config

    const autofillSettings = (0, _settings.fromRuntimeConfig)(runtimeConfiguration); // log feature toggles for clarity when testing

    if (globalConfig.isDDGTestMode) {
      console.log('autofillSettings.featureToggles', JSON.stringify(autofillSettings.featureToggles, null, 2));
    } // If it was enabled, try to ask for available input types


    if (!runtimeConfiguration.isFeatureRemoteEnabled('autofill')) {
      console.log('feature was remotely disabled');
      return;
    } // Determine the tooltipHandler type


    const tooltip = (0, _tooltips.createTooltip)(globalConfig, runtimeConfiguration, autofillSettings);
    console.log(tooltip, 'tooltip');
    const device = (0, _DeviceInterface.createDevice)(sender, tooltip, globalConfig, runtimeConfiguration, autofillSettings);
    console.log(device, 'device'); // This is a workaround for the previous design, we should refactor if possible

    (_tooltip$setDevice = tooltip.setDevice) === null || _tooltip$setDevice === void 0 ? void 0 : _tooltip$setDevice.call(tooltip, device); // Init services

    await device.init();
  } catch (e) {
    console.error(e); // Noop, we errored
  }
})();
/**
 * @public
 * @param {import("./senders/sender").Sender} sender
 * @returns {import("@duckduckgo/content-scope-scripts").RuntimeConfiguration}
 */


async function getRuntimeConfiguration(sender) {
  const data = await sender.send(new _messages.GetRuntimeConfiguration(null));
  const {
    config,
    errors
  } = (0, _contentScopeScripts.tryCreateRuntimeConfiguration)(data);

  if (errors.length) {
    for (let error of errors) {
      console.log(error.message, error);
    }

    throw new Error("".concat(errors.length, " errors prevented global configuration from being created."));
  }

  return config;
}

},{"./DeviceInterface":16,"./UI/tooltips":50,"./config":53,"./messages/messages":56,"./requestIdleCallback":57,"./senders/captureDdgGlobals":66,"./senders/create-sender":67,"./settings/settings":71,"@duckduckgo/content-scope-scripts":1}],53:[function(require,module,exports){
"use strict";

const DDG_DOMAIN_REGEX = new RegExp(/^https:\/\/(([a-z0-9-_]+?)\.)?duckduckgo\.com\/email/);
/**
 * This is a centralised place to contain all string/variable replacements
 *
 * @returns {GlobalConfig}
 */

function createGlobalConfig() {
  let isApp = false;
  let isTopFrame = false;
  let supportsTopFrame = false; // Do not remove -- Apple devices change this when they support modern webkit messaging

  let hasModernWebkitAPI = false; // INJECT isApp HERE
  // INJECT isTopFrame HERE
  // INJECT supportsTopFrame HERE
  // INJECT hasModernWebkitAPI HERE

  let isDDGTestMode = false; // INJECT isDDGTestMode HERE

  let contentScope = null;
  let userUnprotectedDomains = null;
  let userPreferences = null; // INJECT contentScope HERE
  // INJECT userUnprotectedDomains HERE
  // INJECT userPreferences HERE
  // The native layer will inject a randomised secret here and use it to verify the origin

  let secret = 'PLACEHOLDER_SECRET';
  let isDDGApp = /(iPhone|iPad|Android|Mac).*DuckDuckGo\/[0-9]/i.test(window.navigator.userAgent) || isApp || isTopFrame;
  const isAndroid = isDDGApp && /Android/i.test(window.navigator.userAgent);
  const isMobileApp = isDDGApp && !isApp;
  const isFirefox = navigator.userAgent.includes('Firefox');
  const isWindows = navigator.userAgent.includes('Edg/');
  const isDDGDomain = Boolean(window.location.href.match(DDG_DOMAIN_REGEX));
  return {
    isApp,
    isDDGApp,
    isAndroid,
    isFirefox,
    isMobileApp,
    isTopFrame,
    isWindows,
    secret,
    supportsTopFrame,
    hasModernWebkitAPI,
    contentScope,
    userUnprotectedDomains,
    userPreferences,
    isDDGTestMode,
    isDDGDomain
  };
}

module.exports.createGlobalConfig = createGlobalConfig;
module.exports.DDG_DOMAIN_REGEX = DDG_DOMAIN_REGEX;

},{}],54:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.constants = void 0;
const constants = {
  ATTR_INPUT_TYPE: 'data-ddg-inputType',
  ATTR_AUTOFILL: 'data-ddg-autofill',
  TEXT_LENGTH_CUTOFF: 50
};
exports.constants = constants;

},{}],55:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.SchemaValidationError = exports.Message = void 0;

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * @template [Request=any],[Response=any]
 */
class Message {
  /**
   * @type {any}
   */

  /**
   * @type {any}
   */

  /**
   * String representation of this message's name
   * @type {string}
   */

  /**
   * The name of a response message, if it exists
   * @type {string}
   */

  /**
   * This is the data that will be sent in the message.
   * @type {Request|undefined}
   */

  /**
   * @param {Request} [data]
   */
  constructor(data) {
    _defineProperty(this, "reqValidator", null);

    _defineProperty(this, "resValidator", null);

    _defineProperty(this, "name", 'unknown');

    _defineProperty(this, "responseName", this.name + 'Response');

    _defineProperty(this, "data", void 0);

    this.data = data;
  }
  /**
   * @returns {Request|undefined}
   */


  validateRequest() {
    var _this$reqValidator;

    if (this.data === undefined) {
      return undefined;
    }

    if (this.reqValidator && !((_this$reqValidator = this.reqValidator) !== null && _this$reqValidator !== void 0 && _this$reqValidator.call(this, this.data))) {
      var _this$reqValidator2;

      this.throwError((_this$reqValidator2 = this.reqValidator) === null || _this$reqValidator2 === void 0 ? void 0 : _this$reqValidator2['errors']);
    }

    return this.data;
  }
  /**
   * @param {import('ajv').ErrorObject[]} errors
   */


  throwError(errors) {
    const error = SchemaValidationError.fromErrors(errors, this.constructor.name);
    throw error;
  }
  /**
   * @param {any|null} incoming
   * @returns {Response}
   */


  validateResponse(incoming) {
    var _this$resValidator;

    if (this.resValidator && !((_this$resValidator = this.resValidator) !== null && _this$resValidator !== void 0 && _this$resValidator.call(this, incoming))) {
      var _this$resValidator2;

      this.throwError((_this$resValidator2 = this.resValidator) === null || _this$resValidator2 === void 0 ? void 0 : _this$resValidator2.errors);
    }

    if (!incoming) {
      return incoming;
    }

    if ('data' in incoming) {
      console.warn('response had `data` property. Please migrate to `success`');
      return incoming.data;
    }

    if ('success' in incoming) {
      return incoming.success;
    }

    throw new Error('unreachable. Response did not contain `success` or `data`');
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
   * @param {Response} response
   * @returns {Response}
   */


  response(response) {
    return response;
  }
  /**
   * @param {any} response
   * @returns {{success: Response, error?: string}}
   */


  preResponseValidation(response) {
    return response;
  }

}
/**
 * Check for this error if you'd like to
 */


exports.Message = Message;

class SchemaValidationError extends Error {
  constructor() {
    super(...arguments);

    _defineProperty(this, "validationErrors", []);
  }

  /**
   * @param {import("ajv").ErrorObject[]} errors
   * @param {string} name
   * @returns {SchemaValidationError}
   */
  static fromErrors(errors, name) {
    const heading = "".concat(errors.length, " SchemaValidationError(s) errors for ") + name;
    const lines = [];

    for (let error of errors) {
      // console.log(JSON.stringify(error, null, 2));
      lines.push(error.message || 'unknown');
    }

    const message = [heading, ...lines].join('\n    ');
    const error = new SchemaValidationError(message);
    error.validationErrors = errors;
    return error;
  }

}

exports.SchemaValidationError = SchemaValidationError;

},{}],56:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.StoreFormData = exports.ShowAutofillParent = exports.SelectedDetailMessage = exports.LegacyMessage = exports.GetSelectedCredentials = exports.GetRuntimeConfiguration = exports.GetAvailableInputTypes = exports.GetAutofillInitData = exports.GetAutofillData = exports.GetAutofillCredentialsMsg = exports.EmailSignedIn = exports.EmailRefreshAlias = exports.CloseAutofillParent = void 0;
exports.createLegacyMessage = createLegacyMessage;

var _message = require("./message");

var _validators = _interopRequireDefault(require("../schema/validators.cjs"));

var _responseGetRuntimeConfigurationSchema = _interopRequireDefault(require("../schema/response.getRuntimeConfiguration.schema.json"));

var _responseGetAvailableInputTypesSchema = _interopRequireDefault(require("../schema/response.getAvailableInputTypes.schema.json"));

var _responseGetAutofillDataSchema = _interopRequireDefault(require("../schema/response.getAutofillData.schema.json"));

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * This file contains every message this application can 'send' to
 * a native side.
 */

/**
 * @extends {Message<undefined, AvailableInputTypes>}
 */
class GetAvailableInputTypes extends _message.Message {
  constructor() {
    super(...arguments);

    _defineProperty(this, "name", 'getAvailableInputTypes');

    _defineProperty(this, "responseName", _responseGetAvailableInputTypesSchema.default.properties.type.const);

    _defineProperty(this, "resValidator", _validators.default['#/definitions/GetAvailableInputTypesResponse']);
  }

}
/**
 * @extends {Message}
 */


exports.GetAvailableInputTypes = GetAvailableInputTypes;

class CloseAutofillParent extends _message.Message {
  constructor() {
    super(...arguments);

    _defineProperty(this, "name", 'closeAutofillParent');
  }

}
/**
 * @extends {Message<GetAutofillCredentials, Credentials>}
 */


exports.CloseAutofillParent = CloseAutofillParent;

class GetAutofillCredentialsMsg extends _message.Message {
  constructor() {
    super(...arguments);

    _defineProperty(this, "name", 'getAutofillCredentials');

    _defineProperty(this, "reqValidator", _validators.default['#/definitions/GetAutofillCredentials']);
  }

}
/**
 * @extends {Message<ShowAutofillParentRequest, void>}
 */


exports.GetAutofillCredentialsMsg = GetAutofillCredentialsMsg;

class ShowAutofillParent extends _message.Message {
  constructor() {
    super(...arguments);

    _defineProperty(this, "reqValidator", _validators.default['#/definitions/ShowAutofillParentRequest']);

    _defineProperty(this, "name", 'showAutofillParent');
  }

}
/**
 * @extends {Message<null, any>}
 */


exports.ShowAutofillParent = ShowAutofillParent;

class GetSelectedCredentials extends _message.Message {
  constructor() {
    super(...arguments);

    _defineProperty(this, "name", 'getSelectedCredentials');
  }

}
/**
 * @extends {Message<DataStorageObject, void>}
 */


exports.GetSelectedCredentials = GetSelectedCredentials;

class StoreFormData extends _message.Message {
  constructor() {
    super(...arguments);

    _defineProperty(this, "name", 'storeFormData');

    _defineProperty(this, "reqValidator", _validators.default['#/definitions/StoreFormDataRequest']);
  }

}
/**
 * @typedef {StoreFormData} Names2
 */

/**
 * @extends {Message<null, InboundPMData>}
 */


exports.StoreFormData = StoreFormData;

class GetAutofillInitData extends _message.Message {
  constructor() {
    super(...arguments);

    _defineProperty(this, "name", 'getAutofillInitData');

    _defineProperty(this, "resValidator", _validators.default['#/definitions/GetAutofillInitDataResponse']);
  }

}
/**
 * @extends {Message<{data: Record<string, any>, configType: string}, InboundPMData>}
 */


exports.GetAutofillInitData = GetAutofillInitData;

class SelectedDetailMessage extends _message.Message {
  constructor() {
    super(...arguments);

    _defineProperty(this, "name", 'selectedDetail');
  }

}
/**
 * @extends {Message<GetAutofillDataRequest, IdentityObject|CredentialsObject|CreditCardObject>}
 */


exports.SelectedDetailMessage = SelectedDetailMessage;

class GetAutofillData extends _message.Message {
  constructor() {
    super(...arguments);

    _defineProperty(this, "name", 'getAutofillData');

    _defineProperty(this, "reqValidator", _validators.default['#/definitions/GetAutofillDataRequest']);

    _defineProperty(this, "resValidator", _validators.default['#/definitions/GetAutofillDataResponse']);

    _defineProperty(this, "responseName", _responseGetAutofillDataSchema.default.properties.type.const);
  }

  preResponseValidation(response) {
    const cloned = JSON.parse(JSON.stringify(response.success));

    if ('id' in cloned) {
      if (typeof cloned.id === 'number') {
        console.warn("updated the credentials' id field as it was a number, but should be a string");
        cloned.id = String(cloned.id);
      }
    }

    return {
      success: cloned
    };
  }

}
/**
 * @extends {Message<null, RuntimeConfiguration>}
 */


exports.GetAutofillData = GetAutofillData;

class GetRuntimeConfiguration extends _message.Message {
  constructor() {
    super(...arguments);

    _defineProperty(this, "name", 'getRuntimeConfiguration');

    _defineProperty(this, "resValidator", _validators.default['#/definitions/GetRuntimeConfigurationResponse']);

    _defineProperty(this, "responseName", _responseGetRuntimeConfigurationSchema.default.properties.type.const);
  }

}
/**
 * @extends Message<undefined, {isAppSignedIn: boolean}>
 */


exports.GetRuntimeConfiguration = GetRuntimeConfiguration;

class EmailSignedIn extends _message.Message {
  constructor() {
    super(...arguments);

    _defineProperty(this, "name", 'emailHandlerCheckAppSignedInStatus');
  }

}
/**
 * @extends Message<undefined, {isAppSignedIn: boolean}>
 */


exports.EmailSignedIn = EmailSignedIn;

class EmailRefreshAlias extends _message.Message {
  constructor() {
    super(...arguments);

    _defineProperty(this, "name", 'emailHandlerRefreshAlias');
  }

  preResponseValidation(response) {
    return {
      success: response
    };
  }

}
/**
 * Use this to wrap legacy messages where schema validation is not available.
 */


exports.EmailRefreshAlias = EmailRefreshAlias;

class LegacyMessage extends _message.Message {}
/**
 * @template [Req=any]
 * @param {string} name
 * @param {Req} [data]
 * @returns {Message<Req, any>}
 */


exports.LegacyMessage = LegacyMessage;

function createLegacyMessage(name, data) {
  const message = new LegacyMessage(data);
  message.name = name;
  return message;
}

},{"../schema/response.getAutofillData.schema.json":58,"../schema/response.getAvailableInputTypes.schema.json":60,"../schema/response.getRuntimeConfiguration.schema.json":61,"../schema/validators.cjs":62,"./message":55}],57:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;

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

/*
 * @see https://developers.google.com/web/updates/2015/08/using-requestidlecallback
 */
// @ts-ignore
window.requestIdleCallback = window.requestIdleCallback || function (cb) {
  return setTimeout(function () {
    const start = Date.now(); // eslint-disable-next-line standard/no-callback-literal

    cb({
      didTimeout: false,
      timeRemaining: function () {
        return Math.max(0, 50 - (Date.now() - start));
      }
    });
  }, 1);
};

window.cancelIdleCallback = window.cancelIdleCallback || function (id) {
  clearTimeout(id);
};

var _default = {};
exports.default = _default;

},{}],58:[function(require,module,exports){
module.exports={
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAutofillDataResponse",
  "title": "GetAutofillDataResponse",
  "type": "object",
  "properties": {
    "type": {
      "title": "This is the 'type' field on message that may be sent back to the window",
      "description": "Required on Android + Windows devices, optional on iOS",
      "type": "string",
      "const": "getAutofillDataResponse"
    },
    "success": {
      "$id": "#/definitions/AutofillData",
      "title": "GetAutofillDataResponse Success Response",
      "description": "The data returned, containing only fields that will be auto-filled",
      "type": "object",
      "oneOf": [
        { "$ref": "#/definitions/Credentials" }
      ]
    },
    "error": {
      "$ref": "#/definitions/GenericError"
    }
  },
  "required": [
    "success"
  ]
}

},{}],59:[function(require,module,exports){
module.exports={
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAutofillInitDataResponse",
  "title": "GetAutofillInitDataResponse",
  "type": "object",
  "properties": {
    "type": {
      "title": "This is the 'type' field on message that may be sent back to the window",
      "description": "Required on Android + Windows devices, optional on iOS",
      "type": "string",
      "const": "getAutofillInitDataResponse"
    },
    "success": {
      "title": "GetAutofillInitDataResponse Success Response",
      "$id": "#/definitions/AutofillInitData",
      "type": "object",
      "properties": {
        "credentials": {
          "type": "array",
          "items": {
            "$ref": "#/definitions/Credentials"
          }
        },
        "identities": {
          "type": "array",
          "items": {
            "type": "object"
          }
        },
        "creditCards": {
          "type": "array",
          "items": {
            "type": "object"
          }
        },
        "serializedInputContext": {
          "description": "A clone of the `serializedInputContext` that was sent in the request",
          "type": "string"
        }
      },
      "required": [
        "serializedInputContext",
        "credentials",
        "creditCards",
        "identities"
      ]
    },
    "error": {
      "$ref": "#/definitions/GenericError"
    }
  },
  "required": [
    "success"
  ]
}

},{}],60:[function(require,module,exports){
module.exports={
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAvailableInputTypesResponse",
  "type": "object",
  "title": "GetAvailableInputTypesResponse Success Response",
  "properties": {
    "type": {
      "title": "This is the 'type' field on message that may be sent back to the window",
      "description": "Required on Android + Windows devices, optional on iOS",
      "type": "string",
      "const": "getAvailableInputTypesResponse"
    },
    "success": {
      "type": "object",
      "$id": "#/definitions/AvailableInputTypes",
      "properties": {
        "credentials": {
          "description": "true if *any* credentials are available",
          "type": "boolean"
        },
        "identities": {
          "description": "true if *any* identities are available",
          "type": "boolean"
        },
        "creditCards": {
          "description": "true if *any* credit cards are available",
          "type": "boolean"
        },
        "email": {
          "description": "true if signed in for Email Protection",
          "type": "boolean"
        }
      }
    },
    "error":  {
      "$ref": "#/definitions/GenericError"
    }
  },
  "required": [
    "success"
  ]
}

},{}],61:[function(require,module,exports){
module.exports={
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetRuntimeConfigurationResponse",
  "type": "object",
  "title": "GetRuntimeConfigurationResponse Success Response",
  "description": "Data that can be understood by @duckduckgo/content-scope-scripts",
  "properties": {
    "type": {
      "title": "This is the 'type' field on message that may be sent back to the window",
      "description": "Required on Android + Windows devices, optional on iOS",
      "type": "string",
      "const": "getRuntimeConfigurationResponse"
    },
    "success": {
      "description": "This is loaded dynamically from @duckduckgo/content-scope-scripts/src/schema/runtime-configuration.schema.json",
      "$ref": "#/definitions/RuntimeConfiguration"
    },
    "error": {
      "$ref": "#/definitions/GenericError"
    }
  },
  "required": [
    "success"
  ]
}

},{}],62:[function(require,module,exports){
// @ts-nocheck
"use strict";

exports["#/definitions/Credentials"] = validate10;
const schema11 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/Credentials",
  "title": "Credentials",
  "type": "object",
  "properties": {
    "id": {
      "title": "Credentials.id",
      "description": "If present, must be a string",
      "type": "string"
    },
    "username": {
      "title": "Credentials.username",
      "description": "This field is always present, but sometimes it could be an empty string",
      "type": "string"
    },
    "password": {
      "title": "Credentials.password",
      "description": "This field may be empty or absent altogether, which is why it's not marked as 'required'",
      "type": "string"
    }
  },
  "required": ["username"]
};

function validate10(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/Credentials" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.username === undefined && (missing0 = "username")) {
        validate10.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.id !== undefined) {
          const _errs1 = errors;

          if (typeof data.id !== "string") {
            validate10.errors = [{
              instancePath: instancePath + "/id",
              schemaPath: "#/properties/id/type",
              keyword: "type",
              params: {
                type: "string"
              },
              message: "must be string"
            }];
            return false;
          }

          var valid0 = _errs1 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.username !== undefined) {
            const _errs3 = errors;

            if (typeof data.username !== "string") {
              validate10.errors = [{
                instancePath: instancePath + "/username",
                schemaPath: "#/properties/username/type",
                keyword: "type",
                params: {
                  type: "string"
                },
                message: "must be string"
              }];
              return false;
            }

            var valid0 = _errs3 === errors;
          } else {
            var valid0 = true;
          }

          if (valid0) {
            if (data.password !== undefined) {
              const _errs5 = errors;

              if (typeof data.password !== "string") {
                validate10.errors = [{
                  instancePath: instancePath + "/password",
                  schemaPath: "#/properties/password/type",
                  keyword: "type",
                  params: {
                    type: "string"
                  },
                  message: "must be string"
                }];
                return false;
              }

              var valid0 = _errs5 === errors;
            } else {
              var valid0 = true;
            }
          }
        }
      }
    } else {
      validate10.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate10.errors = vErrors;
  return errors === 0;
}

exports["#/definitions/CreditCard"] = validate11;
const schema12 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/CreditCard",
  "title": "CreditCard",
  "type": "object",
  "properties": {
    "id": {
      "type": "string"
    },
    "title": {
      "type": "string"
    },
    "displayNumber": {
      "type": "string"
    },
    "cardName": {
      "type": "string"
    },
    "cardSecurityCode": {
      "type": "string"
    },
    "expirationMonth": {
      "type": "string"
    },
    "expirationYear": {
      "type": "string"
    },
    "cardNumber": {
      "type": "string"
    }
  },
  "required": ["username"]
};

function validate11(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/CreditCard" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.username === undefined && (missing0 = "username")) {
        validate11.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.id !== undefined) {
          const _errs1 = errors;

          if (typeof data.id !== "string") {
            validate11.errors = [{
              instancePath: instancePath + "/id",
              schemaPath: "#/properties/id/type",
              keyword: "type",
              params: {
                type: "string"
              },
              message: "must be string"
            }];
            return false;
          }

          var valid0 = _errs1 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.title !== undefined) {
            const _errs3 = errors;

            if (typeof data.title !== "string") {
              validate11.errors = [{
                instancePath: instancePath + "/title",
                schemaPath: "#/properties/title/type",
                keyword: "type",
                params: {
                  type: "string"
                },
                message: "must be string"
              }];
              return false;
            }

            var valid0 = _errs3 === errors;
          } else {
            var valid0 = true;
          }

          if (valid0) {
            if (data.displayNumber !== undefined) {
              const _errs5 = errors;

              if (typeof data.displayNumber !== "string") {
                validate11.errors = [{
                  instancePath: instancePath + "/displayNumber",
                  schemaPath: "#/properties/displayNumber/type",
                  keyword: "type",
                  params: {
                    type: "string"
                  },
                  message: "must be string"
                }];
                return false;
              }

              var valid0 = _errs5 === errors;
            } else {
              var valid0 = true;
            }

            if (valid0) {
              if (data.cardName !== undefined) {
                const _errs7 = errors;

                if (typeof data.cardName !== "string") {
                  validate11.errors = [{
                    instancePath: instancePath + "/cardName",
                    schemaPath: "#/properties/cardName/type",
                    keyword: "type",
                    params: {
                      type: "string"
                    },
                    message: "must be string"
                  }];
                  return false;
                }

                var valid0 = _errs7 === errors;
              } else {
                var valid0 = true;
              }

              if (valid0) {
                if (data.cardSecurityCode !== undefined) {
                  const _errs9 = errors;

                  if (typeof data.cardSecurityCode !== "string") {
                    validate11.errors = [{
                      instancePath: instancePath + "/cardSecurityCode",
                      schemaPath: "#/properties/cardSecurityCode/type",
                      keyword: "type",
                      params: {
                        type: "string"
                      },
                      message: "must be string"
                    }];
                    return false;
                  }

                  var valid0 = _errs9 === errors;
                } else {
                  var valid0 = true;
                }

                if (valid0) {
                  if (data.expirationMonth !== undefined) {
                    const _errs11 = errors;

                    if (typeof data.expirationMonth !== "string") {
                      validate11.errors = [{
                        instancePath: instancePath + "/expirationMonth",
                        schemaPath: "#/properties/expirationMonth/type",
                        keyword: "type",
                        params: {
                          type: "string"
                        },
                        message: "must be string"
                      }];
                      return false;
                    }

                    var valid0 = _errs11 === errors;
                  } else {
                    var valid0 = true;
                  }

                  if (valid0) {
                    if (data.expirationYear !== undefined) {
                      const _errs13 = errors;

                      if (typeof data.expirationYear !== "string") {
                        validate11.errors = [{
                          instancePath: instancePath + "/expirationYear",
                          schemaPath: "#/properties/expirationYear/type",
                          keyword: "type",
                          params: {
                            type: "string"
                          },
                          message: "must be string"
                        }];
                        return false;
                      }

                      var valid0 = _errs13 === errors;
                    } else {
                      var valid0 = true;
                    }

                    if (valid0) {
                      if (data.cardNumber !== undefined) {
                        const _errs15 = errors;

                        if (typeof data.cardNumber !== "string") {
                          validate11.errors = [{
                            instancePath: instancePath + "/cardNumber",
                            schemaPath: "#/properties/cardNumber/type",
                            keyword: "type",
                            params: {
                              type: "string"
                            },
                            message: "must be string"
                          }];
                          return false;
                        }

                        var valid0 = _errs15 === errors;
                      } else {
                        var valid0 = true;
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    } else {
      validate11.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate11.errors = vErrors;
  return errors === 0;
}

exports["#/definitions/GenericError"] = validate12;
const schema13 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GenericError",
  "title": "GenericError",
  "type": "object",
  "properties": {
    "message": {
      "type": "string"
    }
  },
  "required": ["message"]
};

function validate12(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/GenericError" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.message === undefined && (missing0 = "message")) {
        validate12.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.message !== undefined) {
          if (typeof data.message !== "string") {
            validate12.errors = [{
              instancePath: instancePath + "/message",
              schemaPath: "#/properties/message/type",
              keyword: "type",
              params: {
                type: "string"
              },
              message: "must be string"
            }];
            return false;
          }
        }
      }
    } else {
      validate12.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate12.errors = vErrors;
  return errors === 0;
}

exports["#/definitions/Identity"] = validate13;
const schema14 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/Identity",
  "title": "Identity",
  "description": "A user's Identity",
  "type": "object",
  "properties": {
    "id": {
      "type": "string"
    },
    "title": {
      "description": "This is the only required field",
      "type": "string"
    },
    "firstName": {
      "type": "string"
    },
    "middleName": {
      "type": "string"
    },
    "lastName": {
      "type": "string"
    },
    "birthdayDay": {
      "type": "string"
    },
    "birthdayMonth": {
      "type": "string"
    },
    "birthdayYear": {
      "type": "string"
    },
    "addressStreet": {
      "type": "string"
    },
    "addressStreet2": {
      "type": "string"
    },
    "addressCity": {
      "type": "string"
    },
    "addressProvince": {
      "type": "string"
    },
    "addressPostalCode": {
      "type": "string"
    },
    "addressCountryCode": {
      "type": "string"
    },
    "phone": {
      "type": "string"
    },
    "emailAddress": {
      "type": "string"
    }
  },
  "required": ["title"]
};

function validate13(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/Identity" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.title === undefined && (missing0 = "title")) {
        validate13.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.id !== undefined) {
          const _errs1 = errors;

          if (typeof data.id !== "string") {
            validate13.errors = [{
              instancePath: instancePath + "/id",
              schemaPath: "#/properties/id/type",
              keyword: "type",
              params: {
                type: "string"
              },
              message: "must be string"
            }];
            return false;
          }

          var valid0 = _errs1 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.title !== undefined) {
            const _errs3 = errors;

            if (typeof data.title !== "string") {
              validate13.errors = [{
                instancePath: instancePath + "/title",
                schemaPath: "#/properties/title/type",
                keyword: "type",
                params: {
                  type: "string"
                },
                message: "must be string"
              }];
              return false;
            }

            var valid0 = _errs3 === errors;
          } else {
            var valid0 = true;
          }

          if (valid0) {
            if (data.firstName !== undefined) {
              const _errs5 = errors;

              if (typeof data.firstName !== "string") {
                validate13.errors = [{
                  instancePath: instancePath + "/firstName",
                  schemaPath: "#/properties/firstName/type",
                  keyword: "type",
                  params: {
                    type: "string"
                  },
                  message: "must be string"
                }];
                return false;
              }

              var valid0 = _errs5 === errors;
            } else {
              var valid0 = true;
            }

            if (valid0) {
              if (data.middleName !== undefined) {
                const _errs7 = errors;

                if (typeof data.middleName !== "string") {
                  validate13.errors = [{
                    instancePath: instancePath + "/middleName",
                    schemaPath: "#/properties/middleName/type",
                    keyword: "type",
                    params: {
                      type: "string"
                    },
                    message: "must be string"
                  }];
                  return false;
                }

                var valid0 = _errs7 === errors;
              } else {
                var valid0 = true;
              }

              if (valid0) {
                if (data.lastName !== undefined) {
                  const _errs9 = errors;

                  if (typeof data.lastName !== "string") {
                    validate13.errors = [{
                      instancePath: instancePath + "/lastName",
                      schemaPath: "#/properties/lastName/type",
                      keyword: "type",
                      params: {
                        type: "string"
                      },
                      message: "must be string"
                    }];
                    return false;
                  }

                  var valid0 = _errs9 === errors;
                } else {
                  var valid0 = true;
                }

                if (valid0) {
                  if (data.birthdayDay !== undefined) {
                    const _errs11 = errors;

                    if (typeof data.birthdayDay !== "string") {
                      validate13.errors = [{
                        instancePath: instancePath + "/birthdayDay",
                        schemaPath: "#/properties/birthdayDay/type",
                        keyword: "type",
                        params: {
                          type: "string"
                        },
                        message: "must be string"
                      }];
                      return false;
                    }

                    var valid0 = _errs11 === errors;
                  } else {
                    var valid0 = true;
                  }

                  if (valid0) {
                    if (data.birthdayMonth !== undefined) {
                      const _errs13 = errors;

                      if (typeof data.birthdayMonth !== "string") {
                        validate13.errors = [{
                          instancePath: instancePath + "/birthdayMonth",
                          schemaPath: "#/properties/birthdayMonth/type",
                          keyword: "type",
                          params: {
                            type: "string"
                          },
                          message: "must be string"
                        }];
                        return false;
                      }

                      var valid0 = _errs13 === errors;
                    } else {
                      var valid0 = true;
                    }

                    if (valid0) {
                      if (data.birthdayYear !== undefined) {
                        const _errs15 = errors;

                        if (typeof data.birthdayYear !== "string") {
                          validate13.errors = [{
                            instancePath: instancePath + "/birthdayYear",
                            schemaPath: "#/properties/birthdayYear/type",
                            keyword: "type",
                            params: {
                              type: "string"
                            },
                            message: "must be string"
                          }];
                          return false;
                        }

                        var valid0 = _errs15 === errors;
                      } else {
                        var valid0 = true;
                      }

                      if (valid0) {
                        if (data.addressStreet !== undefined) {
                          const _errs17 = errors;

                          if (typeof data.addressStreet !== "string") {
                            validate13.errors = [{
                              instancePath: instancePath + "/addressStreet",
                              schemaPath: "#/properties/addressStreet/type",
                              keyword: "type",
                              params: {
                                type: "string"
                              },
                              message: "must be string"
                            }];
                            return false;
                          }

                          var valid0 = _errs17 === errors;
                        } else {
                          var valid0 = true;
                        }

                        if (valid0) {
                          if (data.addressStreet2 !== undefined) {
                            const _errs19 = errors;

                            if (typeof data.addressStreet2 !== "string") {
                              validate13.errors = [{
                                instancePath: instancePath + "/addressStreet2",
                                schemaPath: "#/properties/addressStreet2/type",
                                keyword: "type",
                                params: {
                                  type: "string"
                                },
                                message: "must be string"
                              }];
                              return false;
                            }

                            var valid0 = _errs19 === errors;
                          } else {
                            var valid0 = true;
                          }

                          if (valid0) {
                            if (data.addressCity !== undefined) {
                              const _errs21 = errors;

                              if (typeof data.addressCity !== "string") {
                                validate13.errors = [{
                                  instancePath: instancePath + "/addressCity",
                                  schemaPath: "#/properties/addressCity/type",
                                  keyword: "type",
                                  params: {
                                    type: "string"
                                  },
                                  message: "must be string"
                                }];
                                return false;
                              }

                              var valid0 = _errs21 === errors;
                            } else {
                              var valid0 = true;
                            }

                            if (valid0) {
                              if (data.addressProvince !== undefined) {
                                const _errs23 = errors;

                                if (typeof data.addressProvince !== "string") {
                                  validate13.errors = [{
                                    instancePath: instancePath + "/addressProvince",
                                    schemaPath: "#/properties/addressProvince/type",
                                    keyword: "type",
                                    params: {
                                      type: "string"
                                    },
                                    message: "must be string"
                                  }];
                                  return false;
                                }

                                var valid0 = _errs23 === errors;
                              } else {
                                var valid0 = true;
                              }

                              if (valid0) {
                                if (data.addressPostalCode !== undefined) {
                                  const _errs25 = errors;

                                  if (typeof data.addressPostalCode !== "string") {
                                    validate13.errors = [{
                                      instancePath: instancePath + "/addressPostalCode",
                                      schemaPath: "#/properties/addressPostalCode/type",
                                      keyword: "type",
                                      params: {
                                        type: "string"
                                      },
                                      message: "must be string"
                                    }];
                                    return false;
                                  }

                                  var valid0 = _errs25 === errors;
                                } else {
                                  var valid0 = true;
                                }

                                if (valid0) {
                                  if (data.addressCountryCode !== undefined) {
                                    const _errs27 = errors;

                                    if (typeof data.addressCountryCode !== "string") {
                                      validate13.errors = [{
                                        instancePath: instancePath + "/addressCountryCode",
                                        schemaPath: "#/properties/addressCountryCode/type",
                                        keyword: "type",
                                        params: {
                                          type: "string"
                                        },
                                        message: "must be string"
                                      }];
                                      return false;
                                    }

                                    var valid0 = _errs27 === errors;
                                  } else {
                                    var valid0 = true;
                                  }

                                  if (valid0) {
                                    if (data.phone !== undefined) {
                                      const _errs29 = errors;

                                      if (typeof data.phone !== "string") {
                                        validate13.errors = [{
                                          instancePath: instancePath + "/phone",
                                          schemaPath: "#/properties/phone/type",
                                          keyword: "type",
                                          params: {
                                            type: "string"
                                          },
                                          message: "must be string"
                                        }];
                                        return false;
                                      }

                                      var valid0 = _errs29 === errors;
                                    } else {
                                      var valid0 = true;
                                    }

                                    if (valid0) {
                                      if (data.emailAddress !== undefined) {
                                        const _errs31 = errors;

                                        if (typeof data.emailAddress !== "string") {
                                          validate13.errors = [{
                                            instancePath: instancePath + "/emailAddress",
                                            schemaPath: "#/properties/emailAddress/type",
                                            keyword: "type",
                                            params: {
                                              type: "string"
                                            },
                                            message: "must be string"
                                          }];
                                          return false;
                                        }

                                        var valid0 = _errs31 === errors;
                                      } else {
                                        var valid0 = true;
                                      }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    } else {
      validate13.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate13.errors = vErrors;
  return errors === 0;
}

exports["#/definitions/GetAutofillCredentials"] = validate14;
const schema15 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAutofillCredentials",
  "title": "GetAutofillCredentials Request Object",
  "type": "object",
  "description": "This describes the argument given to `getAutofillCredentials`",
  "properties": {
    "id": {
      "type": "string"
    }
  },
  "required": ["id"]
};

function validate14(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/GetAutofillCredentials" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.id === undefined && (missing0 = "id")) {
        validate14.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.id !== undefined) {
          if (typeof data.id !== "string") {
            validate14.errors = [{
              instancePath: instancePath + "/id",
              schemaPath: "#/properties/id/type",
              keyword: "type",
              params: {
                type: "string"
              },
              message: "must be string"
            }];
            return false;
          }
        }
      }
    } else {
      validate14.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate14.errors = vErrors;
  return errors === 0;
}

exports["#/definitions/GetAutofillDataRequest"] = validate15;
const schema16 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAutofillDataRequest",
  "title": "GetAutofillDataRequest Request Object",
  "type": "object",
  "description": "This describes the argument given to `getAutofillData(data)`",
  "properties": {
    "inputType": {
      "title": "The input type that triggered the call",
      "description": "This is the combined input type, such as `credentials.username`",
      "type": "string"
    },
    "mainType": {
      "title": "The main input type",
      "type": "string",
      "enum": ["credentials", "identities", "creditCards"]
    },
    "subType": {
      "title": "Just the subtype, such as `password` or `username`",
      "type": "string"
    }
  },
  "required": ["inputType", "mainType", "subType"]
};

const func0 = require("ajv/dist/runtime/equal").default;

function validate15(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/GetAutofillDataRequest" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.inputType === undefined && (missing0 = "inputType") || data.mainType === undefined && (missing0 = "mainType") || data.subType === undefined && (missing0 = "subType")) {
        validate15.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.inputType !== undefined) {
          const _errs1 = errors;

          if (typeof data.inputType !== "string") {
            validate15.errors = [{
              instancePath: instancePath + "/inputType",
              schemaPath: "#/properties/inputType/type",
              keyword: "type",
              params: {
                type: "string"
              },
              message: "must be string"
            }];
            return false;
          }

          var valid0 = _errs1 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.mainType !== undefined) {
            let data1 = data.mainType;
            const _errs3 = errors;

            if (typeof data1 !== "string") {
              validate15.errors = [{
                instancePath: instancePath + "/mainType",
                schemaPath: "#/properties/mainType/type",
                keyword: "type",
                params: {
                  type: "string"
                },
                message: "must be string"
              }];
              return false;
            }

            if (!(data1 === "credentials" || data1 === "identities" || data1 === "creditCards")) {
              validate15.errors = [{
                instancePath: instancePath + "/mainType",
                schemaPath: "#/properties/mainType/enum",
                keyword: "enum",
                params: {
                  allowedValues: schema16.properties.mainType.enum
                },
                message: "must be equal to one of the allowed values"
              }];
              return false;
            }

            var valid0 = _errs3 === errors;
          } else {
            var valid0 = true;
          }

          if (valid0) {
            if (data.subType !== undefined) {
              const _errs5 = errors;

              if (typeof data.subType !== "string") {
                validate15.errors = [{
                  instancePath: instancePath + "/subType",
                  schemaPath: "#/properties/subType/type",
                  keyword: "type",
                  params: {
                    type: "string"
                  },
                  message: "must be string"
                }];
                return false;
              }

              var valid0 = _errs5 === errors;
            } else {
              var valid0 = true;
            }
          }
        }
      }
    } else {
      validate15.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate15.errors = vErrors;
  return errors === 0;
}

exports["#/definitions/GetAvailableInputTypesRequest"] = validate16;
const schema17 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAvailableInputTypesRequest",
  "type": "object",
  "title": "GetAvailableInputTypesRequest",
  "description": "This method does not currently send any data"
};

function validate16(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/GetAvailableInputTypesRequest" */
  ;
  let vErrors = null;
  let errors = 0;

  if (!(data && typeof data == "object" && !Array.isArray(data))) {
    validate16.errors = [{
      instancePath,
      schemaPath: "#/type",
      keyword: "type",
      params: {
        type: "object"
      },
      message: "must be object"
    }];
    return false;
  }

  validate16.errors = vErrors;
  return errors === 0;
}

exports["#/definitions/GetRuntimeConfigurationRequest"] = validate17;
const schema18 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetRuntimeConfigurationRequest",
  "title": "GetRuntimeConfigurationRequest",
  "description": "This method does not currently send any data"
};

function validate17(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/GetRuntimeConfigurationRequest" */
  ;
  validate17.errors = null;
  return true;
}

exports["#/definitions/ShowAutofillParentRequest"] = validate18;
const schema19 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/ShowAutofillParentRequest",
  "title": "ShowAutofillParentRequest Request Object",
  "type": "object",
  "description": "This describes the argument given to showAutofillParent(data)",
  "properties": {
    "wasFromClick": {
      "type": "boolean"
    },
    "inputTop": {
      "type": "number"
    },
    "inputLeft": {
      "type": "number"
    },
    "inputHeight": {
      "type": "number"
    },
    "inputWidth": {
      "type": "number"
    },
    "serializedInputContext": {
      "description": "JSON string that will be available from `getAutofillInitData()`",
      "type": "string"
    }
  },
  "required": ["wasFromClick", "inputTop", "inputLeft", "inputHeight", "inputWidth", "serializedInputContext"]
};

function validate18(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/ShowAutofillParentRequest" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.wasFromClick === undefined && (missing0 = "wasFromClick") || data.inputTop === undefined && (missing0 = "inputTop") || data.inputLeft === undefined && (missing0 = "inputLeft") || data.inputHeight === undefined && (missing0 = "inputHeight") || data.inputWidth === undefined && (missing0 = "inputWidth") || data.serializedInputContext === undefined && (missing0 = "serializedInputContext")) {
        validate18.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.wasFromClick !== undefined) {
          const _errs1 = errors;

          if (typeof data.wasFromClick !== "boolean") {
            validate18.errors = [{
              instancePath: instancePath + "/wasFromClick",
              schemaPath: "#/properties/wasFromClick/type",
              keyword: "type",
              params: {
                type: "boolean"
              },
              message: "must be boolean"
            }];
            return false;
          }

          var valid0 = _errs1 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.inputTop !== undefined) {
            let data1 = data.inputTop;
            const _errs3 = errors;

            if (!(typeof data1 == "number" && isFinite(data1))) {
              validate18.errors = [{
                instancePath: instancePath + "/inputTop",
                schemaPath: "#/properties/inputTop/type",
                keyword: "type",
                params: {
                  type: "number"
                },
                message: "must be number"
              }];
              return false;
            }

            var valid0 = _errs3 === errors;
          } else {
            var valid0 = true;
          }

          if (valid0) {
            if (data.inputLeft !== undefined) {
              let data2 = data.inputLeft;
              const _errs5 = errors;

              if (!(typeof data2 == "number" && isFinite(data2))) {
                validate18.errors = [{
                  instancePath: instancePath + "/inputLeft",
                  schemaPath: "#/properties/inputLeft/type",
                  keyword: "type",
                  params: {
                    type: "number"
                  },
                  message: "must be number"
                }];
                return false;
              }

              var valid0 = _errs5 === errors;
            } else {
              var valid0 = true;
            }

            if (valid0) {
              if (data.inputHeight !== undefined) {
                let data3 = data.inputHeight;
                const _errs7 = errors;

                if (!(typeof data3 == "number" && isFinite(data3))) {
                  validate18.errors = [{
                    instancePath: instancePath + "/inputHeight",
                    schemaPath: "#/properties/inputHeight/type",
                    keyword: "type",
                    params: {
                      type: "number"
                    },
                    message: "must be number"
                  }];
                  return false;
                }

                var valid0 = _errs7 === errors;
              } else {
                var valid0 = true;
              }

              if (valid0) {
                if (data.inputWidth !== undefined) {
                  let data4 = data.inputWidth;
                  const _errs9 = errors;

                  if (!(typeof data4 == "number" && isFinite(data4))) {
                    validate18.errors = [{
                      instancePath: instancePath + "/inputWidth",
                      schemaPath: "#/properties/inputWidth/type",
                      keyword: "type",
                      params: {
                        type: "number"
                      },
                      message: "must be number"
                    }];
                    return false;
                  }

                  var valid0 = _errs9 === errors;
                } else {
                  var valid0 = true;
                }

                if (valid0) {
                  if (data.serializedInputContext !== undefined) {
                    const _errs11 = errors;

                    if (typeof data.serializedInputContext !== "string") {
                      validate18.errors = [{
                        instancePath: instancePath + "/serializedInputContext",
                        schemaPath: "#/properties/serializedInputContext/type",
                        keyword: "type",
                        params: {
                          type: "string"
                        },
                        message: "must be string"
                      }];
                      return false;
                    }

                    var valid0 = _errs11 === errors;
                  } else {
                    var valid0 = true;
                  }
                }
              }
            }
          }
        }
      }
    } else {
      validate18.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate18.errors = vErrors;
  return errors === 0;
}

exports["#/definitions/StoreFormDataRequest"] = validate19;
const schema20 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/StoreFormDataRequest",
  "title": "StoreFormData Request",
  "type": "object",
  "description": "Autofill could send this data at any point. \n\nIt will **not** listen for a response, it's expected that the native side will handle",
  "properties": {
    "credentials": {
      "type": "object",
      "$id": "#/definitions/CredentialsOutgoing",
      "properties": {
        "username": {
          "description": "Optional username",
          "type": "string"
        },
        "password": {
          "description": "Optional password",
          "type": "string"
        }
      }
    },
    "identities": {
      "description": "todo(Shane): Rename to identity",
      "$ref": "#/definitions/Identity"
    },
    "creditCards": {
      "description": "todo(Shane): Rename to creditCard",
      "$ref": "#/definitions/CreditCard"
    }
  }
};

function validate19(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/StoreFormDataRequest" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      if (data.credentials !== undefined) {
        let data0 = data.credentials;
        const _errs1 = errors;

        if (errors === _errs1) {
          if (data0 && typeof data0 == "object" && !Array.isArray(data0)) {
            if (data0.username !== undefined) {
              const _errs3 = errors;

              if (typeof data0.username !== "string") {
                validate19.errors = [{
                  instancePath: instancePath + "/credentials/username",
                  schemaPath: "#/properties/credentials/properties/username/type",
                  keyword: "type",
                  params: {
                    type: "string"
                  },
                  message: "must be string"
                }];
                return false;
              }

              var valid1 = _errs3 === errors;
            } else {
              var valid1 = true;
            }

            if (valid1) {
              if (data0.password !== undefined) {
                const _errs5 = errors;

                if (typeof data0.password !== "string") {
                  validate19.errors = [{
                    instancePath: instancePath + "/credentials/password",
                    schemaPath: "#/properties/credentials/properties/password/type",
                    keyword: "type",
                    params: {
                      type: "string"
                    },
                    message: "must be string"
                  }];
                  return false;
                }

                var valid1 = _errs5 === errors;
              } else {
                var valid1 = true;
              }
            }
          } else {
            validate19.errors = [{
              instancePath: instancePath + "/credentials",
              schemaPath: "#/properties/credentials/type",
              keyword: "type",
              params: {
                type: "object"
              },
              message: "must be object"
            }];
            return false;
          }
        }

        var valid0 = _errs1 === errors;
      } else {
        var valid0 = true;
      }

      if (valid0) {
        if (data.identities !== undefined) {
          let data3 = data.identities;
          const _errs7 = errors;
          const _errs8 = errors;

          if (errors === _errs8) {
            if (data3 && typeof data3 == "object" && !Array.isArray(data3)) {
              let missing0;

              if (data3.title === undefined && (missing0 = "title")) {
                validate19.errors = [{
                  instancePath: instancePath + "/identities",
                  schemaPath: "#/definitions/Identity/required",
                  keyword: "required",
                  params: {
                    missingProperty: missing0
                  },
                  message: "must have required property '" + missing0 + "'"
                }];
                return false;
              } else {
                if (data3.id !== undefined) {
                  const _errs10 = errors;

                  if (typeof data3.id !== "string") {
                    validate19.errors = [{
                      instancePath: instancePath + "/identities/id",
                      schemaPath: "#/definitions/Identity/properties/id/type",
                      keyword: "type",
                      params: {
                        type: "string"
                      },
                      message: "must be string"
                    }];
                    return false;
                  }

                  var valid3 = _errs10 === errors;
                } else {
                  var valid3 = true;
                }

                if (valid3) {
                  if (data3.title !== undefined) {
                    const _errs12 = errors;

                    if (typeof data3.title !== "string") {
                      validate19.errors = [{
                        instancePath: instancePath + "/identities/title",
                        schemaPath: "#/definitions/Identity/properties/title/type",
                        keyword: "type",
                        params: {
                          type: "string"
                        },
                        message: "must be string"
                      }];
                      return false;
                    }

                    var valid3 = _errs12 === errors;
                  } else {
                    var valid3 = true;
                  }

                  if (valid3) {
                    if (data3.firstName !== undefined) {
                      const _errs14 = errors;

                      if (typeof data3.firstName !== "string") {
                        validate19.errors = [{
                          instancePath: instancePath + "/identities/firstName",
                          schemaPath: "#/definitions/Identity/properties/firstName/type",
                          keyword: "type",
                          params: {
                            type: "string"
                          },
                          message: "must be string"
                        }];
                        return false;
                      }

                      var valid3 = _errs14 === errors;
                    } else {
                      var valid3 = true;
                    }

                    if (valid3) {
                      if (data3.middleName !== undefined) {
                        const _errs16 = errors;

                        if (typeof data3.middleName !== "string") {
                          validate19.errors = [{
                            instancePath: instancePath + "/identities/middleName",
                            schemaPath: "#/definitions/Identity/properties/middleName/type",
                            keyword: "type",
                            params: {
                              type: "string"
                            },
                            message: "must be string"
                          }];
                          return false;
                        }

                        var valid3 = _errs16 === errors;
                      } else {
                        var valid3 = true;
                      }

                      if (valid3) {
                        if (data3.lastName !== undefined) {
                          const _errs18 = errors;

                          if (typeof data3.lastName !== "string") {
                            validate19.errors = [{
                              instancePath: instancePath + "/identities/lastName",
                              schemaPath: "#/definitions/Identity/properties/lastName/type",
                              keyword: "type",
                              params: {
                                type: "string"
                              },
                              message: "must be string"
                            }];
                            return false;
                          }

                          var valid3 = _errs18 === errors;
                        } else {
                          var valid3 = true;
                        }

                        if (valid3) {
                          if (data3.birthdayDay !== undefined) {
                            const _errs20 = errors;

                            if (typeof data3.birthdayDay !== "string") {
                              validate19.errors = [{
                                instancePath: instancePath + "/identities/birthdayDay",
                                schemaPath: "#/definitions/Identity/properties/birthdayDay/type",
                                keyword: "type",
                                params: {
                                  type: "string"
                                },
                                message: "must be string"
                              }];
                              return false;
                            }

                            var valid3 = _errs20 === errors;
                          } else {
                            var valid3 = true;
                          }

                          if (valid3) {
                            if (data3.birthdayMonth !== undefined) {
                              const _errs22 = errors;

                              if (typeof data3.birthdayMonth !== "string") {
                                validate19.errors = [{
                                  instancePath: instancePath + "/identities/birthdayMonth",
                                  schemaPath: "#/definitions/Identity/properties/birthdayMonth/type",
                                  keyword: "type",
                                  params: {
                                    type: "string"
                                  },
                                  message: "must be string"
                                }];
                                return false;
                              }

                              var valid3 = _errs22 === errors;
                            } else {
                              var valid3 = true;
                            }

                            if (valid3) {
                              if (data3.birthdayYear !== undefined) {
                                const _errs24 = errors;

                                if (typeof data3.birthdayYear !== "string") {
                                  validate19.errors = [{
                                    instancePath: instancePath + "/identities/birthdayYear",
                                    schemaPath: "#/definitions/Identity/properties/birthdayYear/type",
                                    keyword: "type",
                                    params: {
                                      type: "string"
                                    },
                                    message: "must be string"
                                  }];
                                  return false;
                                }

                                var valid3 = _errs24 === errors;
                              } else {
                                var valid3 = true;
                              }

                              if (valid3) {
                                if (data3.addressStreet !== undefined) {
                                  const _errs26 = errors;

                                  if (typeof data3.addressStreet !== "string") {
                                    validate19.errors = [{
                                      instancePath: instancePath + "/identities/addressStreet",
                                      schemaPath: "#/definitions/Identity/properties/addressStreet/type",
                                      keyword: "type",
                                      params: {
                                        type: "string"
                                      },
                                      message: "must be string"
                                    }];
                                    return false;
                                  }

                                  var valid3 = _errs26 === errors;
                                } else {
                                  var valid3 = true;
                                }

                                if (valid3) {
                                  if (data3.addressStreet2 !== undefined) {
                                    const _errs28 = errors;

                                    if (typeof data3.addressStreet2 !== "string") {
                                      validate19.errors = [{
                                        instancePath: instancePath + "/identities/addressStreet2",
                                        schemaPath: "#/definitions/Identity/properties/addressStreet2/type",
                                        keyword: "type",
                                        params: {
                                          type: "string"
                                        },
                                        message: "must be string"
                                      }];
                                      return false;
                                    }

                                    var valid3 = _errs28 === errors;
                                  } else {
                                    var valid3 = true;
                                  }

                                  if (valid3) {
                                    if (data3.addressCity !== undefined) {
                                      const _errs30 = errors;

                                      if (typeof data3.addressCity !== "string") {
                                        validate19.errors = [{
                                          instancePath: instancePath + "/identities/addressCity",
                                          schemaPath: "#/definitions/Identity/properties/addressCity/type",
                                          keyword: "type",
                                          params: {
                                            type: "string"
                                          },
                                          message: "must be string"
                                        }];
                                        return false;
                                      }

                                      var valid3 = _errs30 === errors;
                                    } else {
                                      var valid3 = true;
                                    }

                                    if (valid3) {
                                      if (data3.addressProvince !== undefined) {
                                        const _errs32 = errors;

                                        if (typeof data3.addressProvince !== "string") {
                                          validate19.errors = [{
                                            instancePath: instancePath + "/identities/addressProvince",
                                            schemaPath: "#/definitions/Identity/properties/addressProvince/type",
                                            keyword: "type",
                                            params: {
                                              type: "string"
                                            },
                                            message: "must be string"
                                          }];
                                          return false;
                                        }

                                        var valid3 = _errs32 === errors;
                                      } else {
                                        var valid3 = true;
                                      }

                                      if (valid3) {
                                        if (data3.addressPostalCode !== undefined) {
                                          const _errs34 = errors;

                                          if (typeof data3.addressPostalCode !== "string") {
                                            validate19.errors = [{
                                              instancePath: instancePath + "/identities/addressPostalCode",
                                              schemaPath: "#/definitions/Identity/properties/addressPostalCode/type",
                                              keyword: "type",
                                              params: {
                                                type: "string"
                                              },
                                              message: "must be string"
                                            }];
                                            return false;
                                          }

                                          var valid3 = _errs34 === errors;
                                        } else {
                                          var valid3 = true;
                                        }

                                        if (valid3) {
                                          if (data3.addressCountryCode !== undefined) {
                                            const _errs36 = errors;

                                            if (typeof data3.addressCountryCode !== "string") {
                                              validate19.errors = [{
                                                instancePath: instancePath + "/identities/addressCountryCode",
                                                schemaPath: "#/definitions/Identity/properties/addressCountryCode/type",
                                                keyword: "type",
                                                params: {
                                                  type: "string"
                                                },
                                                message: "must be string"
                                              }];
                                              return false;
                                            }

                                            var valid3 = _errs36 === errors;
                                          } else {
                                            var valid3 = true;
                                          }

                                          if (valid3) {
                                            if (data3.phone !== undefined) {
                                              const _errs38 = errors;

                                              if (typeof data3.phone !== "string") {
                                                validate19.errors = [{
                                                  instancePath: instancePath + "/identities/phone",
                                                  schemaPath: "#/definitions/Identity/properties/phone/type",
                                                  keyword: "type",
                                                  params: {
                                                    type: "string"
                                                  },
                                                  message: "must be string"
                                                }];
                                                return false;
                                              }

                                              var valid3 = _errs38 === errors;
                                            } else {
                                              var valid3 = true;
                                            }

                                            if (valid3) {
                                              if (data3.emailAddress !== undefined) {
                                                const _errs40 = errors;

                                                if (typeof data3.emailAddress !== "string") {
                                                  validate19.errors = [{
                                                    instancePath: instancePath + "/identities/emailAddress",
                                                    schemaPath: "#/definitions/Identity/properties/emailAddress/type",
                                                    keyword: "type",
                                                    params: {
                                                      type: "string"
                                                    },
                                                    message: "must be string"
                                                  }];
                                                  return false;
                                                }

                                                var valid3 = _errs40 === errors;
                                              } else {
                                                var valid3 = true;
                                              }
                                            }
                                          }
                                        }
                                      }
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            } else {
              validate19.errors = [{
                instancePath: instancePath + "/identities",
                schemaPath: "#/definitions/Identity/type",
                keyword: "type",
                params: {
                  type: "object"
                },
                message: "must be object"
              }];
              return false;
            }
          }

          var valid0 = _errs7 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.creditCards !== undefined) {
            let data20 = data.creditCards;
            const _errs42 = errors;
            const _errs43 = errors;

            if (errors === _errs43) {
              if (data20 && typeof data20 == "object" && !Array.isArray(data20)) {
                let missing1;

                if (data20.username === undefined && (missing1 = "username")) {
                  validate19.errors = [{
                    instancePath: instancePath + "/creditCards",
                    schemaPath: "#/definitions/CreditCard/required",
                    keyword: "required",
                    params: {
                      missingProperty: missing1
                    },
                    message: "must have required property '" + missing1 + "'"
                  }];
                  return false;
                } else {
                  if (data20.id !== undefined) {
                    const _errs45 = errors;

                    if (typeof data20.id !== "string") {
                      validate19.errors = [{
                        instancePath: instancePath + "/creditCards/id",
                        schemaPath: "#/definitions/CreditCard/properties/id/type",
                        keyword: "type",
                        params: {
                          type: "string"
                        },
                        message: "must be string"
                      }];
                      return false;
                    }

                    var valid5 = _errs45 === errors;
                  } else {
                    var valid5 = true;
                  }

                  if (valid5) {
                    if (data20.title !== undefined) {
                      const _errs47 = errors;

                      if (typeof data20.title !== "string") {
                        validate19.errors = [{
                          instancePath: instancePath + "/creditCards/title",
                          schemaPath: "#/definitions/CreditCard/properties/title/type",
                          keyword: "type",
                          params: {
                            type: "string"
                          },
                          message: "must be string"
                        }];
                        return false;
                      }

                      var valid5 = _errs47 === errors;
                    } else {
                      var valid5 = true;
                    }

                    if (valid5) {
                      if (data20.displayNumber !== undefined) {
                        const _errs49 = errors;

                        if (typeof data20.displayNumber !== "string") {
                          validate19.errors = [{
                            instancePath: instancePath + "/creditCards/displayNumber",
                            schemaPath: "#/definitions/CreditCard/properties/displayNumber/type",
                            keyword: "type",
                            params: {
                              type: "string"
                            },
                            message: "must be string"
                          }];
                          return false;
                        }

                        var valid5 = _errs49 === errors;
                      } else {
                        var valid5 = true;
                      }

                      if (valid5) {
                        if (data20.cardName !== undefined) {
                          const _errs51 = errors;

                          if (typeof data20.cardName !== "string") {
                            validate19.errors = [{
                              instancePath: instancePath + "/creditCards/cardName",
                              schemaPath: "#/definitions/CreditCard/properties/cardName/type",
                              keyword: "type",
                              params: {
                                type: "string"
                              },
                              message: "must be string"
                            }];
                            return false;
                          }

                          var valid5 = _errs51 === errors;
                        } else {
                          var valid5 = true;
                        }

                        if (valid5) {
                          if (data20.cardSecurityCode !== undefined) {
                            const _errs53 = errors;

                            if (typeof data20.cardSecurityCode !== "string") {
                              validate19.errors = [{
                                instancePath: instancePath + "/creditCards/cardSecurityCode",
                                schemaPath: "#/definitions/CreditCard/properties/cardSecurityCode/type",
                                keyword: "type",
                                params: {
                                  type: "string"
                                },
                                message: "must be string"
                              }];
                              return false;
                            }

                            var valid5 = _errs53 === errors;
                          } else {
                            var valid5 = true;
                          }

                          if (valid5) {
                            if (data20.expirationMonth !== undefined) {
                              const _errs55 = errors;

                              if (typeof data20.expirationMonth !== "string") {
                                validate19.errors = [{
                                  instancePath: instancePath + "/creditCards/expirationMonth",
                                  schemaPath: "#/definitions/CreditCard/properties/expirationMonth/type",
                                  keyword: "type",
                                  params: {
                                    type: "string"
                                  },
                                  message: "must be string"
                                }];
                                return false;
                              }

                              var valid5 = _errs55 === errors;
                            } else {
                              var valid5 = true;
                            }

                            if (valid5) {
                              if (data20.expirationYear !== undefined) {
                                const _errs57 = errors;

                                if (typeof data20.expirationYear !== "string") {
                                  validate19.errors = [{
                                    instancePath: instancePath + "/creditCards/expirationYear",
                                    schemaPath: "#/definitions/CreditCard/properties/expirationYear/type",
                                    keyword: "type",
                                    params: {
                                      type: "string"
                                    },
                                    message: "must be string"
                                  }];
                                  return false;
                                }

                                var valid5 = _errs57 === errors;
                              } else {
                                var valid5 = true;
                              }

                              if (valid5) {
                                if (data20.cardNumber !== undefined) {
                                  const _errs59 = errors;

                                  if (typeof data20.cardNumber !== "string") {
                                    validate19.errors = [{
                                      instancePath: instancePath + "/creditCards/cardNumber",
                                      schemaPath: "#/definitions/CreditCard/properties/cardNumber/type",
                                      keyword: "type",
                                      params: {
                                        type: "string"
                                      },
                                      message: "must be string"
                                    }];
                                    return false;
                                  }

                                  var valid5 = _errs59 === errors;
                                } else {
                                  var valid5 = true;
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              } else {
                validate19.errors = [{
                  instancePath: instancePath + "/creditCards",
                  schemaPath: "#/definitions/CreditCard/type",
                  keyword: "type",
                  params: {
                    type: "object"
                  },
                  message: "must be object"
                }];
                return false;
              }
            }

            var valid0 = _errs42 === errors;
          } else {
            var valid0 = true;
          }
        }
      }
    } else {
      validate19.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate19.errors = vErrors;
  return errors === 0;
}

exports["#/definitions/GetAutofillDataResponse"] = validate20;
const schema23 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAutofillDataResponse",
  "title": "GetAutofillDataResponse",
  "type": "object",
  "properties": {
    "type": {
      "title": "This is the 'type' field on message that may be sent back to the window",
      "description": "Required on Android + Windows devices, optional on iOS",
      "type": "string",
      "const": "getAutofillDataResponse"
    },
    "success": {
      "$id": "#/definitions/AutofillData",
      "title": "GetAutofillDataResponse Success Response",
      "description": "The data returned, containing only fields that will be auto-filled",
      "type": "object",
      "oneOf": [{
        "$ref": "#/definitions/Credentials"
      }]
    },
    "error": {
      "$ref": "#/definitions/GenericError"
    }
  },
  "required": ["success"]
};

function validate20(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/GetAutofillDataResponse" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.success === undefined && (missing0 = "success")) {
        validate20.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.type !== undefined) {
          let data0 = data.type;
          const _errs1 = errors;

          if (typeof data0 !== "string") {
            validate20.errors = [{
              instancePath: instancePath + "/type",
              schemaPath: "#/properties/type/type",
              keyword: "type",
              params: {
                type: "string"
              },
              message: "must be string"
            }];
            return false;
          }

          if ("getAutofillDataResponse" !== data0) {
            validate20.errors = [{
              instancePath: instancePath + "/type",
              schemaPath: "#/properties/type/const",
              keyword: "const",
              params: {
                allowedValue: "getAutofillDataResponse"
              },
              message: "must be equal to constant"
            }];
            return false;
          }

          var valid0 = _errs1 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.success !== undefined) {
            let data1 = data.success;
            const _errs3 = errors;

            if (!(data1 && typeof data1 == "object" && !Array.isArray(data1))) {
              validate20.errors = [{
                instancePath: instancePath + "/success",
                schemaPath: "#/properties/success/type",
                keyword: "type",
                params: {
                  type: "object"
                },
                message: "must be object"
              }];
              return false;
            }

            const _errs5 = errors;
            let valid1 = false;
            let passing0 = null;
            const _errs6 = errors;
            const _errs7 = errors;

            if (errors === _errs7) {
              if (data1 && typeof data1 == "object" && !Array.isArray(data1)) {
                let missing1;

                if (data1.username === undefined && (missing1 = "username")) {
                  const err0 = {
                    instancePath: instancePath + "/success",
                    schemaPath: "#/definitions/Credentials/required",
                    keyword: "required",
                    params: {
                      missingProperty: missing1
                    },
                    message: "must have required property '" + missing1 + "'"
                  };

                  if (vErrors === null) {
                    vErrors = [err0];
                  } else {
                    vErrors.push(err0);
                  }

                  errors++;
                } else {
                  if (data1.id !== undefined) {
                    const _errs9 = errors;

                    if (typeof data1.id !== "string") {
                      const err1 = {
                        instancePath: instancePath + "/success/id",
                        schemaPath: "#/definitions/Credentials/properties/id/type",
                        keyword: "type",
                        params: {
                          type: "string"
                        },
                        message: "must be string"
                      };

                      if (vErrors === null) {
                        vErrors = [err1];
                      } else {
                        vErrors.push(err1);
                      }

                      errors++;
                    }

                    var valid3 = _errs9 === errors;
                  } else {
                    var valid3 = true;
                  }

                  if (valid3) {
                    if (data1.username !== undefined) {
                      const _errs11 = errors;

                      if (typeof data1.username !== "string") {
                        const err2 = {
                          instancePath: instancePath + "/success/username",
                          schemaPath: "#/definitions/Credentials/properties/username/type",
                          keyword: "type",
                          params: {
                            type: "string"
                          },
                          message: "must be string"
                        };

                        if (vErrors === null) {
                          vErrors = [err2];
                        } else {
                          vErrors.push(err2);
                        }

                        errors++;
                      }

                      var valid3 = _errs11 === errors;
                    } else {
                      var valid3 = true;
                    }

                    if (valid3) {
                      if (data1.password !== undefined) {
                        const _errs13 = errors;

                        if (typeof data1.password !== "string") {
                          const err3 = {
                            instancePath: instancePath + "/success/password",
                            schemaPath: "#/definitions/Credentials/properties/password/type",
                            keyword: "type",
                            params: {
                              type: "string"
                            },
                            message: "must be string"
                          };

                          if (vErrors === null) {
                            vErrors = [err3];
                          } else {
                            vErrors.push(err3);
                          }

                          errors++;
                        }

                        var valid3 = _errs13 === errors;
                      } else {
                        var valid3 = true;
                      }
                    }
                  }
                }
              } else {
                const err4 = {
                  instancePath: instancePath + "/success",
                  schemaPath: "#/definitions/Credentials/type",
                  keyword: "type",
                  params: {
                    type: "object"
                  },
                  message: "must be object"
                };

                if (vErrors === null) {
                  vErrors = [err4];
                } else {
                  vErrors.push(err4);
                }

                errors++;
              }
            }

            var _valid0 = _errs6 === errors;

            if (_valid0) {
              valid1 = true;
              passing0 = 0;
            }

            if (!valid1) {
              const err5 = {
                instancePath: instancePath + "/success",
                schemaPath: "#/properties/success/oneOf",
                keyword: "oneOf",
                params: {
                  passingSchemas: passing0
                },
                message: "must match exactly one schema in oneOf"
              };

              if (vErrors === null) {
                vErrors = [err5];
              } else {
                vErrors.push(err5);
              }

              errors++;
              validate20.errors = vErrors;
              return false;
            } else {
              errors = _errs5;

              if (vErrors !== null) {
                if (_errs5) {
                  vErrors.length = _errs5;
                } else {
                  vErrors = null;
                }
              }
            }

            var valid0 = _errs3 === errors;
          } else {
            var valid0 = true;
          }

          if (valid0) {
            if (data.error !== undefined) {
              let data5 = data.error;
              const _errs15 = errors;
              const _errs16 = errors;

              if (errors === _errs16) {
                if (data5 && typeof data5 == "object" && !Array.isArray(data5)) {
                  let missing2;

                  if (data5.message === undefined && (missing2 = "message")) {
                    validate20.errors = [{
                      instancePath: instancePath + "/error",
                      schemaPath: "#/definitions/GenericError/required",
                      keyword: "required",
                      params: {
                        missingProperty: missing2
                      },
                      message: "must have required property '" + missing2 + "'"
                    }];
                    return false;
                  } else {
                    if (data5.message !== undefined) {
                      if (typeof data5.message !== "string") {
                        validate20.errors = [{
                          instancePath: instancePath + "/error/message",
                          schemaPath: "#/definitions/GenericError/properties/message/type",
                          keyword: "type",
                          params: {
                            type: "string"
                          },
                          message: "must be string"
                        }];
                        return false;
                      }
                    }
                  }
                } else {
                  validate20.errors = [{
                    instancePath: instancePath + "/error",
                    schemaPath: "#/definitions/GenericError/type",
                    keyword: "type",
                    params: {
                      type: "object"
                    },
                    message: "must be object"
                  }];
                  return false;
                }
              }

              var valid0 = _errs15 === errors;
            } else {
              var valid0 = true;
            }
          }
        }
      }
    } else {
      validate20.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate20.errors = vErrors;
  return errors === 0;
}

exports["#/definitions/GetAutofillInitDataResponse"] = validate21;
const schema26 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAutofillInitDataResponse",
  "title": "GetAutofillInitDataResponse",
  "type": "object",
  "properties": {
    "type": {
      "title": "This is the 'type' field on message that may be sent back to the window",
      "description": "Required on Android + Windows devices, optional on iOS",
      "type": "string",
      "const": "getAutofillInitDataResponse"
    },
    "success": {
      "title": "GetAutofillInitDataResponse Success Response",
      "$id": "#/definitions/AutofillInitData",
      "type": "object",
      "properties": {
        "credentials": {
          "type": "array",
          "items": {
            "$ref": "#/definitions/Credentials"
          }
        },
        "identities": {
          "type": "array",
          "items": {
            "type": "object"
          }
        },
        "creditCards": {
          "type": "array",
          "items": {
            "type": "object"
          }
        },
        "serializedInputContext": {
          "description": "A clone of the `serializedInputContext` that was sent in the request",
          "type": "string"
        }
      },
      "required": ["serializedInputContext", "credentials", "creditCards", "identities"]
    },
    "error": {
      "$ref": "#/definitions/GenericError"
    }
  },
  "required": ["success"]
};

function validate21(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/GetAutofillInitDataResponse" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.success === undefined && (missing0 = "success")) {
        validate21.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.type !== undefined) {
          let data0 = data.type;
          const _errs1 = errors;

          if (typeof data0 !== "string") {
            validate21.errors = [{
              instancePath: instancePath + "/type",
              schemaPath: "#/properties/type/type",
              keyword: "type",
              params: {
                type: "string"
              },
              message: "must be string"
            }];
            return false;
          }

          if ("getAutofillInitDataResponse" !== data0) {
            validate21.errors = [{
              instancePath: instancePath + "/type",
              schemaPath: "#/properties/type/const",
              keyword: "const",
              params: {
                allowedValue: "getAutofillInitDataResponse"
              },
              message: "must be equal to constant"
            }];
            return false;
          }

          var valid0 = _errs1 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.success !== undefined) {
            let data1 = data.success;
            const _errs3 = errors;

            if (errors === _errs3) {
              if (data1 && typeof data1 == "object" && !Array.isArray(data1)) {
                let missing1;

                if (data1.serializedInputContext === undefined && (missing1 = "serializedInputContext") || data1.credentials === undefined && (missing1 = "credentials") || data1.creditCards === undefined && (missing1 = "creditCards") || data1.identities === undefined && (missing1 = "identities")) {
                  validate21.errors = [{
                    instancePath: instancePath + "/success",
                    schemaPath: "#/properties/success/required",
                    keyword: "required",
                    params: {
                      missingProperty: missing1
                    },
                    message: "must have required property '" + missing1 + "'"
                  }];
                  return false;
                } else {
                  if (data1.credentials !== undefined) {
                    let data2 = data1.credentials;
                    const _errs5 = errors;

                    if (errors === _errs5) {
                      if (Array.isArray(data2)) {
                        var valid2 = true;
                        const len0 = data2.length;

                        for (let i0 = 0; i0 < len0; i0++) {
                          let data3 = data2[i0];
                          const _errs7 = errors;
                          const _errs8 = errors;

                          if (errors === _errs8) {
                            if (data3 && typeof data3 == "object" && !Array.isArray(data3)) {
                              let missing2;

                              if (data3.username === undefined && (missing2 = "username")) {
                                validate21.errors = [{
                                  instancePath: instancePath + "/success/credentials/" + i0,
                                  schemaPath: "#/definitions/Credentials/required",
                                  keyword: "required",
                                  params: {
                                    missingProperty: missing2
                                  },
                                  message: "must have required property '" + missing2 + "'"
                                }];
                                return false;
                              } else {
                                if (data3.id !== undefined) {
                                  const _errs10 = errors;

                                  if (typeof data3.id !== "string") {
                                    validate21.errors = [{
                                      instancePath: instancePath + "/success/credentials/" + i0 + "/id",
                                      schemaPath: "#/definitions/Credentials/properties/id/type",
                                      keyword: "type",
                                      params: {
                                        type: "string"
                                      },
                                      message: "must be string"
                                    }];
                                    return false;
                                  }

                                  var valid4 = _errs10 === errors;
                                } else {
                                  var valid4 = true;
                                }

                                if (valid4) {
                                  if (data3.username !== undefined) {
                                    const _errs12 = errors;

                                    if (typeof data3.username !== "string") {
                                      validate21.errors = [{
                                        instancePath: instancePath + "/success/credentials/" + i0 + "/username",
                                        schemaPath: "#/definitions/Credentials/properties/username/type",
                                        keyword: "type",
                                        params: {
                                          type: "string"
                                        },
                                        message: "must be string"
                                      }];
                                      return false;
                                    }

                                    var valid4 = _errs12 === errors;
                                  } else {
                                    var valid4 = true;
                                  }

                                  if (valid4) {
                                    if (data3.password !== undefined) {
                                      const _errs14 = errors;

                                      if (typeof data3.password !== "string") {
                                        validate21.errors = [{
                                          instancePath: instancePath + "/success/credentials/" + i0 + "/password",
                                          schemaPath: "#/definitions/Credentials/properties/password/type",
                                          keyword: "type",
                                          params: {
                                            type: "string"
                                          },
                                          message: "must be string"
                                        }];
                                        return false;
                                      }

                                      var valid4 = _errs14 === errors;
                                    } else {
                                      var valid4 = true;
                                    }
                                  }
                                }
                              }
                            } else {
                              validate21.errors = [{
                                instancePath: instancePath + "/success/credentials/" + i0,
                                schemaPath: "#/definitions/Credentials/type",
                                keyword: "type",
                                params: {
                                  type: "object"
                                },
                                message: "must be object"
                              }];
                              return false;
                            }
                          }

                          var valid2 = _errs7 === errors;

                          if (!valid2) {
                            break;
                          }
                        }
                      } else {
                        validate21.errors = [{
                          instancePath: instancePath + "/success/credentials",
                          schemaPath: "#/properties/success/properties/credentials/type",
                          keyword: "type",
                          params: {
                            type: "array"
                          },
                          message: "must be array"
                        }];
                        return false;
                      }
                    }

                    var valid1 = _errs5 === errors;
                  } else {
                    var valid1 = true;
                  }

                  if (valid1) {
                    if (data1.identities !== undefined) {
                      let data7 = data1.identities;
                      const _errs16 = errors;

                      if (errors === _errs16) {
                        if (Array.isArray(data7)) {
                          var valid5 = true;
                          const len1 = data7.length;

                          for (let i1 = 0; i1 < len1; i1++) {
                            let data8 = data7[i1];
                            const _errs18 = errors;

                            if (!(data8 && typeof data8 == "object" && !Array.isArray(data8))) {
                              validate21.errors = [{
                                instancePath: instancePath + "/success/identities/" + i1,
                                schemaPath: "#/properties/success/properties/identities/items/type",
                                keyword: "type",
                                params: {
                                  type: "object"
                                },
                                message: "must be object"
                              }];
                              return false;
                            }

                            var valid5 = _errs18 === errors;

                            if (!valid5) {
                              break;
                            }
                          }
                        } else {
                          validate21.errors = [{
                            instancePath: instancePath + "/success/identities",
                            schemaPath: "#/properties/success/properties/identities/type",
                            keyword: "type",
                            params: {
                              type: "array"
                            },
                            message: "must be array"
                          }];
                          return false;
                        }
                      }

                      var valid1 = _errs16 === errors;
                    } else {
                      var valid1 = true;
                    }

                    if (valid1) {
                      if (data1.creditCards !== undefined) {
                        let data9 = data1.creditCards;
                        const _errs20 = errors;

                        if (errors === _errs20) {
                          if (Array.isArray(data9)) {
                            var valid6 = true;
                            const len2 = data9.length;

                            for (let i2 = 0; i2 < len2; i2++) {
                              let data10 = data9[i2];
                              const _errs22 = errors;

                              if (!(data10 && typeof data10 == "object" && !Array.isArray(data10))) {
                                validate21.errors = [{
                                  instancePath: instancePath + "/success/creditCards/" + i2,
                                  schemaPath: "#/properties/success/properties/creditCards/items/type",
                                  keyword: "type",
                                  params: {
                                    type: "object"
                                  },
                                  message: "must be object"
                                }];
                                return false;
                              }

                              var valid6 = _errs22 === errors;

                              if (!valid6) {
                                break;
                              }
                            }
                          } else {
                            validate21.errors = [{
                              instancePath: instancePath + "/success/creditCards",
                              schemaPath: "#/properties/success/properties/creditCards/type",
                              keyword: "type",
                              params: {
                                type: "array"
                              },
                              message: "must be array"
                            }];
                            return false;
                          }
                        }

                        var valid1 = _errs20 === errors;
                      } else {
                        var valid1 = true;
                      }

                      if (valid1) {
                        if (data1.serializedInputContext !== undefined) {
                          const _errs24 = errors;

                          if (typeof data1.serializedInputContext !== "string") {
                            validate21.errors = [{
                              instancePath: instancePath + "/success/serializedInputContext",
                              schemaPath: "#/properties/success/properties/serializedInputContext/type",
                              keyword: "type",
                              params: {
                                type: "string"
                              },
                              message: "must be string"
                            }];
                            return false;
                          }

                          var valid1 = _errs24 === errors;
                        } else {
                          var valid1 = true;
                        }
                      }
                    }
                  }
                }
              } else {
                validate21.errors = [{
                  instancePath: instancePath + "/success",
                  schemaPath: "#/properties/success/type",
                  keyword: "type",
                  params: {
                    type: "object"
                  },
                  message: "must be object"
                }];
                return false;
              }
            }

            var valid0 = _errs3 === errors;
          } else {
            var valid0 = true;
          }

          if (valid0) {
            if (data.error !== undefined) {
              let data12 = data.error;
              const _errs26 = errors;
              const _errs27 = errors;

              if (errors === _errs27) {
                if (data12 && typeof data12 == "object" && !Array.isArray(data12)) {
                  let missing3;

                  if (data12.message === undefined && (missing3 = "message")) {
                    validate21.errors = [{
                      instancePath: instancePath + "/error",
                      schemaPath: "#/definitions/GenericError/required",
                      keyword: "required",
                      params: {
                        missingProperty: missing3
                      },
                      message: "must have required property '" + missing3 + "'"
                    }];
                    return false;
                  } else {
                    if (data12.message !== undefined) {
                      if (typeof data12.message !== "string") {
                        validate21.errors = [{
                          instancePath: instancePath + "/error/message",
                          schemaPath: "#/definitions/GenericError/properties/message/type",
                          keyword: "type",
                          params: {
                            type: "string"
                          },
                          message: "must be string"
                        }];
                        return false;
                      }
                    }
                  }
                } else {
                  validate21.errors = [{
                    instancePath: instancePath + "/error",
                    schemaPath: "#/definitions/GenericError/type",
                    keyword: "type",
                    params: {
                      type: "object"
                    },
                    message: "must be object"
                  }];
                  return false;
                }
              }

              var valid0 = _errs26 === errors;
            } else {
              var valid0 = true;
            }
          }
        }
      }
    } else {
      validate21.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate21.errors = vErrors;
  return errors === 0;
}

exports["#/definitions/GetAvailableInputTypesResponse"] = validate22;
const schema29 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetAvailableInputTypesResponse",
  "type": "object",
  "title": "GetAvailableInputTypesResponse Success Response",
  "properties": {
    "type": {
      "title": "This is the 'type' field on message that may be sent back to the window",
      "description": "Required on Android + Windows devices, optional on iOS",
      "type": "string",
      "const": "getAvailableInputTypesResponse"
    },
    "success": {
      "type": "object",
      "$id": "#/definitions/AvailableInputTypes",
      "properties": {
        "credentials": {
          "description": "true if *any* credentials are available",
          "type": "boolean"
        },
        "identities": {
          "description": "true if *any* identities are available",
          "type": "boolean"
        },
        "creditCards": {
          "description": "true if *any* credit cards are available",
          "type": "boolean"
        },
        "email": {
          "description": "true if signed in for Email Protection",
          "type": "boolean"
        }
      }
    },
    "error": {
      "$ref": "#/definitions/GenericError"
    }
  },
  "required": ["success"]
};

function validate22(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/GetAvailableInputTypesResponse" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.success === undefined && (missing0 = "success")) {
        validate22.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.type !== undefined) {
          let data0 = data.type;
          const _errs1 = errors;

          if (typeof data0 !== "string") {
            validate22.errors = [{
              instancePath: instancePath + "/type",
              schemaPath: "#/properties/type/type",
              keyword: "type",
              params: {
                type: "string"
              },
              message: "must be string"
            }];
            return false;
          }

          if ("getAvailableInputTypesResponse" !== data0) {
            validate22.errors = [{
              instancePath: instancePath + "/type",
              schemaPath: "#/properties/type/const",
              keyword: "const",
              params: {
                allowedValue: "getAvailableInputTypesResponse"
              },
              message: "must be equal to constant"
            }];
            return false;
          }

          var valid0 = _errs1 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.success !== undefined) {
            let data1 = data.success;
            const _errs3 = errors;

            if (errors === _errs3) {
              if (data1 && typeof data1 == "object" && !Array.isArray(data1)) {
                if (data1.credentials !== undefined) {
                  const _errs5 = errors;

                  if (typeof data1.credentials !== "boolean") {
                    validate22.errors = [{
                      instancePath: instancePath + "/success/credentials",
                      schemaPath: "#/properties/success/properties/credentials/type",
                      keyword: "type",
                      params: {
                        type: "boolean"
                      },
                      message: "must be boolean"
                    }];
                    return false;
                  }

                  var valid1 = _errs5 === errors;
                } else {
                  var valid1 = true;
                }

                if (valid1) {
                  if (data1.identities !== undefined) {
                    const _errs7 = errors;

                    if (typeof data1.identities !== "boolean") {
                      validate22.errors = [{
                        instancePath: instancePath + "/success/identities",
                        schemaPath: "#/properties/success/properties/identities/type",
                        keyword: "type",
                        params: {
                          type: "boolean"
                        },
                        message: "must be boolean"
                      }];
                      return false;
                    }

                    var valid1 = _errs7 === errors;
                  } else {
                    var valid1 = true;
                  }

                  if (valid1) {
                    if (data1.creditCards !== undefined) {
                      const _errs9 = errors;

                      if (typeof data1.creditCards !== "boolean") {
                        validate22.errors = [{
                          instancePath: instancePath + "/success/creditCards",
                          schemaPath: "#/properties/success/properties/creditCards/type",
                          keyword: "type",
                          params: {
                            type: "boolean"
                          },
                          message: "must be boolean"
                        }];
                        return false;
                      }

                      var valid1 = _errs9 === errors;
                    } else {
                      var valid1 = true;
                    }

                    if (valid1) {
                      if (data1.email !== undefined) {
                        const _errs11 = errors;

                        if (typeof data1.email !== "boolean") {
                          validate22.errors = [{
                            instancePath: instancePath + "/success/email",
                            schemaPath: "#/properties/success/properties/email/type",
                            keyword: "type",
                            params: {
                              type: "boolean"
                            },
                            message: "must be boolean"
                          }];
                          return false;
                        }

                        var valid1 = _errs11 === errors;
                      } else {
                        var valid1 = true;
                      }
                    }
                  }
                }
              } else {
                validate22.errors = [{
                  instancePath: instancePath + "/success",
                  schemaPath: "#/properties/success/type",
                  keyword: "type",
                  params: {
                    type: "object"
                  },
                  message: "must be object"
                }];
                return false;
              }
            }

            var valid0 = _errs3 === errors;
          } else {
            var valid0 = true;
          }

          if (valid0) {
            if (data.error !== undefined) {
              let data6 = data.error;
              const _errs13 = errors;
              const _errs14 = errors;

              if (errors === _errs14) {
                if (data6 && typeof data6 == "object" && !Array.isArray(data6)) {
                  let missing1;

                  if (data6.message === undefined && (missing1 = "message")) {
                    validate22.errors = [{
                      instancePath: instancePath + "/error",
                      schemaPath: "#/definitions/GenericError/required",
                      keyword: "required",
                      params: {
                        missingProperty: missing1
                      },
                      message: "must have required property '" + missing1 + "'"
                    }];
                    return false;
                  } else {
                    if (data6.message !== undefined) {
                      if (typeof data6.message !== "string") {
                        validate22.errors = [{
                          instancePath: instancePath + "/error/message",
                          schemaPath: "#/definitions/GenericError/properties/message/type",
                          keyword: "type",
                          params: {
                            type: "string"
                          },
                          message: "must be string"
                        }];
                        return false;
                      }
                    }
                  }
                } else {
                  validate22.errors = [{
                    instancePath: instancePath + "/error",
                    schemaPath: "#/definitions/GenericError/type",
                    keyword: "type",
                    params: {
                      type: "object"
                    },
                    message: "must be object"
                  }];
                  return false;
                }
              }

              var valid0 = _errs13 === errors;
            } else {
              var valid0 = true;
            }
          }
        }
      }
    } else {
      validate22.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate22.errors = vErrors;
  return errors === 0;
}

exports["#/definitions/GetRuntimeConfigurationResponse"] = validate23;
const schema31 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/GetRuntimeConfigurationResponse",
  "type": "object",
  "title": "GetRuntimeConfigurationResponse Success Response",
  "description": "Data that can be understood by @duckduckgo/content-scope-scripts",
  "properties": {
    "type": {
      "title": "This is the 'type' field on message that may be sent back to the window",
      "description": "Required on Android + Windows devices, optional on iOS",
      "type": "string",
      "const": "getRuntimeConfigurationResponse"
    },
    "success": {
      "description": "This is loaded dynamically from @duckduckgo/content-scope-scripts/src/schema/runtime-configuration.schema.json",
      "$ref": "#/definitions/RuntimeConfiguration"
    },
    "error": {
      "$ref": "#/definitions/GenericError"
    }
  },
  "required": ["success"]
};
const schema32 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/RuntimeConfiguration",
  "type": "object",
  "additionalProperties": false,
  "title": "Runtime Configuration Schema",
  "description": "Required Properties to enable an instance of RuntimeConfiguration",
  "properties": {
    "contentScope": {
      "$ref": "#/definitions/ContentScope"
    },
    "userUnprotectedDomains": {
      "type": "array",
      "items": {}
    },
    "userPreferences": {
      "$ref": "#/definitions/UserPreferences"
    }
  },
  "required": ["contentScope", "userPreferences", "userUnprotectedDomains"],
  "definitions": {
    "ContentScope": {
      "type": "object",
      "additionalProperties": true,
      "properties": {
        "features": {
          "$ref": "#/definitions/ContentScopeFeatures"
        },
        "unprotectedTemporary": {
          "type": "array",
          "items": {}
        }
      },
      "required": ["features", "unprotectedTemporary"],
      "title": "ContentScope"
    },
    "ContentScopeFeatures": {
      "type": "object",
      "additionalProperties": {
        "$ref": "#/definitions/ContentScopeFeatureItem"
      },
      "title": "ContentScopeFeatures"
    },
    "ContentScopeFeatureItem": {
      "type": "object",
      "properties": {
        "exceptions": {
          "type": "array",
          "items": {}
        },
        "state": {
          "type": "string"
        },
        "settings": {
          "type": "object"
        }
      },
      "required": ["exceptions", "state"],
      "title": "ContentScopeFeatureItem"
    },
    "UserPreferences": {
      "type": "object",
      "properties": {
        "debug": {
          "type": "boolean"
        },
        "platform": {
          "$ref": "#/definitions/Platform"
        },
        "features": {
          "$ref": "#/definitions/UserPreferencesFeatures"
        }
      },
      "required": ["debug", "features", "platform"],
      "title": "UserPreferences"
    },
    "UserPreferencesFeatures": {
      "type": "object",
      "additionalProperties": {
        "$ref": "#/definitions/UserPreferencesFeatureItem"
      },
      "title": "UserPreferencesFeatures"
    },
    "UserPreferencesFeatureItem": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "settings": {
          "$ref": "#/definitions/Settings"
        }
      },
      "required": ["settings"],
      "title": "UserPreferencesFeatureItem"
    },
    "Settings": {
      "type": "object",
      "additionalProperties": true,
      "title": "Settings"
    },
    "Platform": {
      "type": "object",
      "properties": {
        "name": {
          "type": "string",
          "enum": ["ios", "macos", "windows", "extension", "android", "unknown"]
        }
      },
      "required": ["name"],
      "title": "Platform"
    }
  }
};
const schema33 = {
  "type": "object",
  "additionalProperties": true,
  "properties": {
    "features": {
      "$ref": "#/definitions/ContentScopeFeatures"
    },
    "unprotectedTemporary": {
      "type": "array",
      "items": {}
    }
  },
  "required": ["features", "unprotectedTemporary"],
  "title": "ContentScope"
};
const schema34 = {
  "type": "object",
  "additionalProperties": {
    "$ref": "#/definitions/ContentScopeFeatureItem"
  },
  "title": "ContentScopeFeatures"
};
const schema35 = {
  "type": "object",
  "properties": {
    "exceptions": {
      "type": "array",
      "items": {}
    },
    "state": {
      "type": "string"
    },
    "settings": {
      "type": "object"
    }
  },
  "required": ["exceptions", "state"],
  "title": "ContentScopeFeatureItem"
};

function validate26(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      for (const key0 in data) {
        let data0 = data[key0];
        const _errs2 = errors;
        const _errs3 = errors;

        if (errors === _errs3) {
          if (data0 && typeof data0 == "object" && !Array.isArray(data0)) {
            let missing0;

            if (data0.exceptions === undefined && (missing0 = "exceptions") || data0.state === undefined && (missing0 = "state")) {
              validate26.errors = [{
                instancePath: instancePath + "/" + key0.replace(/~/g, "~0").replace(/\//g, "~1"),
                schemaPath: "#/definitions/ContentScopeFeatureItem/required",
                keyword: "required",
                params: {
                  missingProperty: missing0
                },
                message: "must have required property '" + missing0 + "'"
              }];
              return false;
            } else {
              if (data0.exceptions !== undefined) {
                const _errs5 = errors;

                if (errors === _errs5) {
                  if (!Array.isArray(data0.exceptions)) {
                    validate26.errors = [{
                      instancePath: instancePath + "/" + key0.replace(/~/g, "~0").replace(/\//g, "~1") + "/exceptions",
                      schemaPath: "#/definitions/ContentScopeFeatureItem/properties/exceptions/type",
                      keyword: "type",
                      params: {
                        type: "array"
                      },
                      message: "must be array"
                    }];
                    return false;
                  }
                }

                var valid2 = _errs5 === errors;
              } else {
                var valid2 = true;
              }

              if (valid2) {
                if (data0.state !== undefined) {
                  const _errs7 = errors;

                  if (typeof data0.state !== "string") {
                    validate26.errors = [{
                      instancePath: instancePath + "/" + key0.replace(/~/g, "~0").replace(/\//g, "~1") + "/state",
                      schemaPath: "#/definitions/ContentScopeFeatureItem/properties/state/type",
                      keyword: "type",
                      params: {
                        type: "string"
                      },
                      message: "must be string"
                    }];
                    return false;
                  }

                  var valid2 = _errs7 === errors;
                } else {
                  var valid2 = true;
                }

                if (valid2) {
                  if (data0.settings !== undefined) {
                    let data3 = data0.settings;
                    const _errs9 = errors;

                    if (!(data3 && typeof data3 == "object" && !Array.isArray(data3))) {
                      validate26.errors = [{
                        instancePath: instancePath + "/" + key0.replace(/~/g, "~0").replace(/\//g, "~1") + "/settings",
                        schemaPath: "#/definitions/ContentScopeFeatureItem/properties/settings/type",
                        keyword: "type",
                        params: {
                          type: "object"
                        },
                        message: "must be object"
                      }];
                      return false;
                    }

                    var valid2 = _errs9 === errors;
                  } else {
                    var valid2 = true;
                  }
                }
              }
            }
          } else {
            validate26.errors = [{
              instancePath: instancePath + "/" + key0.replace(/~/g, "~0").replace(/\//g, "~1"),
              schemaPath: "#/definitions/ContentScopeFeatureItem/type",
              keyword: "type",
              params: {
                type: "object"
              },
              message: "must be object"
            }];
            return false;
          }
        }

        var valid0 = _errs2 === errors;

        if (!valid0) {
          break;
        }
      }
    } else {
      validate26.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate26.errors = vErrors;
  return errors === 0;
}

function validate25(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.features === undefined && (missing0 = "features") || data.unprotectedTemporary === undefined && (missing0 = "unprotectedTemporary")) {
        validate25.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.features !== undefined) {
          const _errs2 = errors;

          if (!validate26(data.features, {
            instancePath: instancePath + "/features",
            parentData: data,
            parentDataProperty: "features",
            rootData
          })) {
            vErrors = vErrors === null ? validate26.errors : vErrors.concat(validate26.errors);
            errors = vErrors.length;
          }

          var valid0 = _errs2 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.unprotectedTemporary !== undefined) {
            const _errs3 = errors;

            if (errors === _errs3) {
              if (!Array.isArray(data.unprotectedTemporary)) {
                validate25.errors = [{
                  instancePath: instancePath + "/unprotectedTemporary",
                  schemaPath: "#/properties/unprotectedTemporary/type",
                  keyword: "type",
                  params: {
                    type: "array"
                  },
                  message: "must be array"
                }];
                return false;
              }
            }

            var valid0 = _errs3 === errors;
          } else {
            var valid0 = true;
          }
        }
      }
    } else {
      validate25.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate25.errors = vErrors;
  return errors === 0;
}

const schema36 = {
  "type": "object",
  "properties": {
    "debug": {
      "type": "boolean"
    },
    "platform": {
      "$ref": "#/definitions/Platform"
    },
    "features": {
      "$ref": "#/definitions/UserPreferencesFeatures"
    }
  },
  "required": ["debug", "features", "platform"],
  "title": "UserPreferences"
};
const schema37 = {
  "type": "object",
  "properties": {
    "name": {
      "type": "string",
      "enum": ["ios", "macos", "windows", "extension", "android", "unknown"]
    }
  },
  "required": ["name"],
  "title": "Platform"
};
const schema38 = {
  "type": "object",
  "additionalProperties": {
    "$ref": "#/definitions/UserPreferencesFeatureItem"
  },
  "title": "UserPreferencesFeatures"
};
const schema39 = {
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "settings": {
      "$ref": "#/definitions/Settings"
    }
  },
  "required": ["settings"],
  "title": "UserPreferencesFeatureItem"
};
const schema40 = {
  "type": "object",
  "additionalProperties": true,
  "title": "Settings"
};

function validate31(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.settings === undefined && (missing0 = "settings")) {
        validate31.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        const _errs1 = errors;

        for (const key0 in data) {
          if (!(key0 === "settings")) {
            validate31.errors = [{
              instancePath,
              schemaPath: "#/additionalProperties",
              keyword: "additionalProperties",
              params: {
                additionalProperty: key0
              },
              message: "must NOT have additional properties"
            }];
            return false;
            break;
          }
        }

        if (_errs1 === errors) {
          if (data.settings !== undefined) {
            let data0 = data.settings;
            const _errs3 = errors;

            if (errors === _errs3) {
              if (data0 && typeof data0 == "object" && !Array.isArray(data0)) {} else {
                validate31.errors = [{
                  instancePath: instancePath + "/settings",
                  schemaPath: "#/definitions/Settings/type",
                  keyword: "type",
                  params: {
                    type: "object"
                  },
                  message: "must be object"
                }];
                return false;
              }
            }
          }
        }
      }
    } else {
      validate31.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate31.errors = vErrors;
  return errors === 0;
}

function validate30(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      for (const key0 in data) {
        const _errs2 = errors;

        if (!validate31(data[key0], {
          instancePath: instancePath + "/" + key0.replace(/~/g, "~0").replace(/\//g, "~1"),
          parentData: data,
          parentDataProperty: key0,
          rootData
        })) {
          vErrors = vErrors === null ? validate31.errors : vErrors.concat(validate31.errors);
          errors = vErrors.length;
        }

        var valid0 = _errs2 === errors;

        if (!valid0) {
          break;
        }
      }
    } else {
      validate30.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate30.errors = vErrors;
  return errors === 0;
}

function validate29(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.debug === undefined && (missing0 = "debug") || data.features === undefined && (missing0 = "features") || data.platform === undefined && (missing0 = "platform")) {
        validate29.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.debug !== undefined) {
          const _errs1 = errors;

          if (typeof data.debug !== "boolean") {
            validate29.errors = [{
              instancePath: instancePath + "/debug",
              schemaPath: "#/properties/debug/type",
              keyword: "type",
              params: {
                type: "boolean"
              },
              message: "must be boolean"
            }];
            return false;
          }

          var valid0 = _errs1 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.platform !== undefined) {
            let data1 = data.platform;
            const _errs3 = errors;
            const _errs4 = errors;

            if (errors === _errs4) {
              if (data1 && typeof data1 == "object" && !Array.isArray(data1)) {
                let missing1;

                if (data1.name === undefined && (missing1 = "name")) {
                  validate29.errors = [{
                    instancePath: instancePath + "/platform",
                    schemaPath: "#/definitions/Platform/required",
                    keyword: "required",
                    params: {
                      missingProperty: missing1
                    },
                    message: "must have required property '" + missing1 + "'"
                  }];
                  return false;
                } else {
                  if (data1.name !== undefined) {
                    let data2 = data1.name;

                    if (typeof data2 !== "string") {
                      validate29.errors = [{
                        instancePath: instancePath + "/platform/name",
                        schemaPath: "#/definitions/Platform/properties/name/type",
                        keyword: "type",
                        params: {
                          type: "string"
                        },
                        message: "must be string"
                      }];
                      return false;
                    }

                    if (!(data2 === "ios" || data2 === "macos" || data2 === "windows" || data2 === "extension" || data2 === "android" || data2 === "unknown")) {
                      validate29.errors = [{
                        instancePath: instancePath + "/platform/name",
                        schemaPath: "#/definitions/Platform/properties/name/enum",
                        keyword: "enum",
                        params: {
                          allowedValues: schema37.properties.name.enum
                        },
                        message: "must be equal to one of the allowed values"
                      }];
                      return false;
                    }
                  }
                }
              } else {
                validate29.errors = [{
                  instancePath: instancePath + "/platform",
                  schemaPath: "#/definitions/Platform/type",
                  keyword: "type",
                  params: {
                    type: "object"
                  },
                  message: "must be object"
                }];
                return false;
              }
            }

            var valid0 = _errs3 === errors;
          } else {
            var valid0 = true;
          }

          if (valid0) {
            if (data.features !== undefined) {
              const _errs8 = errors;

              if (!validate30(data.features, {
                instancePath: instancePath + "/features",
                parentData: data,
                parentDataProperty: "features",
                rootData
              })) {
                vErrors = vErrors === null ? validate30.errors : vErrors.concat(validate30.errors);
                errors = vErrors.length;
              }

              var valid0 = _errs8 === errors;
            } else {
              var valid0 = true;
            }
          }
        }
      }
    } else {
      validate29.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate29.errors = vErrors;
  return errors === 0;
}

function validate24(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/RuntimeConfiguration" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.contentScope === undefined && (missing0 = "contentScope") || data.userPreferences === undefined && (missing0 = "userPreferences") || data.userUnprotectedDomains === undefined && (missing0 = "userUnprotectedDomains")) {
        validate24.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        const _errs1 = errors;

        for (const key0 in data) {
          if (!(key0 === "contentScope" || key0 === "userUnprotectedDomains" || key0 === "userPreferences")) {
            validate24.errors = [{
              instancePath,
              schemaPath: "#/additionalProperties",
              keyword: "additionalProperties",
              params: {
                additionalProperty: key0
              },
              message: "must NOT have additional properties"
            }];
            return false;
            break;
          }
        }

        if (_errs1 === errors) {
          if (data.contentScope !== undefined) {
            const _errs2 = errors;

            if (!validate25(data.contentScope, {
              instancePath: instancePath + "/contentScope",
              parentData: data,
              parentDataProperty: "contentScope",
              rootData
            })) {
              vErrors = vErrors === null ? validate25.errors : vErrors.concat(validate25.errors);
              errors = vErrors.length;
            }

            var valid0 = _errs2 === errors;
          } else {
            var valid0 = true;
          }

          if (valid0) {
            if (data.userUnprotectedDomains !== undefined) {
              const _errs3 = errors;

              if (errors === _errs3) {
                if (!Array.isArray(data.userUnprotectedDomains)) {
                  validate24.errors = [{
                    instancePath: instancePath + "/userUnprotectedDomains",
                    schemaPath: "#/properties/userUnprotectedDomains/type",
                    keyword: "type",
                    params: {
                      type: "array"
                    },
                    message: "must be array"
                  }];
                  return false;
                }
              }

              var valid0 = _errs3 === errors;
            } else {
              var valid0 = true;
            }

            if (valid0) {
              if (data.userPreferences !== undefined) {
                const _errs5 = errors;

                if (!validate29(data.userPreferences, {
                  instancePath: instancePath + "/userPreferences",
                  parentData: data,
                  parentDataProperty: "userPreferences",
                  rootData
                })) {
                  vErrors = vErrors === null ? validate29.errors : vErrors.concat(validate29.errors);
                  errors = vErrors.length;
                }

                var valid0 = _errs5 === errors;
              } else {
                var valid0 = true;
              }
            }
          }
        }
      }
    } else {
      validate24.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate24.errors = vErrors;
  return errors === 0;
}

function validate23(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/GetRuntimeConfigurationResponse" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.success === undefined && (missing0 = "success")) {
        validate23.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.type !== undefined) {
          let data0 = data.type;
          const _errs1 = errors;

          if (typeof data0 !== "string") {
            validate23.errors = [{
              instancePath: instancePath + "/type",
              schemaPath: "#/properties/type/type",
              keyword: "type",
              params: {
                type: "string"
              },
              message: "must be string"
            }];
            return false;
          }

          if ("getRuntimeConfigurationResponse" !== data0) {
            validate23.errors = [{
              instancePath: instancePath + "/type",
              schemaPath: "#/properties/type/const",
              keyword: "const",
              params: {
                allowedValue: "getRuntimeConfigurationResponse"
              },
              message: "must be equal to constant"
            }];
            return false;
          }

          var valid0 = _errs1 === errors;
        } else {
          var valid0 = true;
        }

        if (valid0) {
          if (data.success !== undefined) {
            const _errs3 = errors;

            if (!validate24(data.success, {
              instancePath: instancePath + "/success",
              parentData: data,
              parentDataProperty: "success",
              rootData
            })) {
              vErrors = vErrors === null ? validate24.errors : vErrors.concat(validate24.errors);
              errors = vErrors.length;
            }

            var valid0 = _errs3 === errors;
          } else {
            var valid0 = true;
          }

          if (valid0) {
            if (data.error !== undefined) {
              let data2 = data.error;
              const _errs4 = errors;
              const _errs5 = errors;

              if (errors === _errs5) {
                if (data2 && typeof data2 == "object" && !Array.isArray(data2)) {
                  let missing1;

                  if (data2.message === undefined && (missing1 = "message")) {
                    validate23.errors = [{
                      instancePath: instancePath + "/error",
                      schemaPath: "#/definitions/GenericError/required",
                      keyword: "required",
                      params: {
                        missingProperty: missing1
                      },
                      message: "must have required property '" + missing1 + "'"
                    }];
                    return false;
                  } else {
                    if (data2.message !== undefined) {
                      if (typeof data2.message !== "string") {
                        validate23.errors = [{
                          instancePath: instancePath + "/error/message",
                          schemaPath: "#/definitions/GenericError/properties/message/type",
                          keyword: "type",
                          params: {
                            type: "string"
                          },
                          message: "must be string"
                        }];
                        return false;
                      }
                    }
                  }
                } else {
                  validate23.errors = [{
                    instancePath: instancePath + "/error",
                    schemaPath: "#/definitions/GenericError/type",
                    keyword: "type",
                    params: {
                      type: "object"
                    },
                    message: "must be object"
                  }];
                  return false;
                }
              }

              var valid0 = _errs4 === errors;
            } else {
              var valid0 = true;
            }
          }
        }
      }
    } else {
      validate23.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate23.errors = vErrors;
  return errors === 0;
}

exports["#/definitions/RuntimeConfiguration"] = validate24;
exports["#/definitions/AutofillSettings"] = validate36;
const schema42 = {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "#/definitions/AutofillSettings",
  "title": "AutofillSettings",
  "type": "object",
  "properties": {
    "featureToggles": {
      "title": "FeatureToggles",
      "$id": "#/definitions/FeatureToggles",
      "description": "These are toggles used throughout the application to enable/disable features fully",
      "type": "object",
      "properties": {
        "inputType_credentials": {
          "type": "boolean"
        },
        "inputType_identities": {
          "type": "boolean"
        },
        "inputType_creditCards": {
          "type": "boolean"
        },
        "emailProtection": {
          "type": "boolean"
        },
        "password_generation": {
          "type": "boolean"
        },
        "credentials_saving": {
          "type": "boolean"
        }
      },
      "required": ["inputType_credentials", "inputType_identities", "inputType_creditCards", "emailProtection", "password_generation", "credentials_saving"]
    }
  },
  "required": ["featureToggles"]
};

function validate36(data) {
  let {
    instancePath = "",
    parentData,
    parentDataProperty,
    rootData = data
  } = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};

  /*# sourceURL="#/definitions/AutofillSettings" */
  ;
  let vErrors = null;
  let errors = 0;

  if (errors === 0) {
    if (data && typeof data == "object" && !Array.isArray(data)) {
      let missing0;

      if (data.featureToggles === undefined && (missing0 = "featureToggles")) {
        validate36.errors = [{
          instancePath,
          schemaPath: "#/required",
          keyword: "required",
          params: {
            missingProperty: missing0
          },
          message: "must have required property '" + missing0 + "'"
        }];
        return false;
      } else {
        if (data.featureToggles !== undefined) {
          let data0 = data.featureToggles;
          const _errs1 = errors;

          if (errors === _errs1) {
            if (data0 && typeof data0 == "object" && !Array.isArray(data0)) {
              let missing1;

              if (data0.inputType_credentials === undefined && (missing1 = "inputType_credentials") || data0.inputType_identities === undefined && (missing1 = "inputType_identities") || data0.inputType_creditCards === undefined && (missing1 = "inputType_creditCards") || data0.emailProtection === undefined && (missing1 = "emailProtection") || data0.password_generation === undefined && (missing1 = "password_generation") || data0.credentials_saving === undefined && (missing1 = "credentials_saving")) {
                validate36.errors = [{
                  instancePath: instancePath + "/featureToggles",
                  schemaPath: "#/properties/featureToggles/required",
                  keyword: "required",
                  params: {
                    missingProperty: missing1
                  },
                  message: "must have required property '" + missing1 + "'"
                }];
                return false;
              } else {
                if (data0.inputType_credentials !== undefined) {
                  const _errs3 = errors;

                  if (typeof data0.inputType_credentials !== "boolean") {
                    validate36.errors = [{
                      instancePath: instancePath + "/featureToggles/inputType_credentials",
                      schemaPath: "#/properties/featureToggles/properties/inputType_credentials/type",
                      keyword: "type",
                      params: {
                        type: "boolean"
                      },
                      message: "must be boolean"
                    }];
                    return false;
                  }

                  var valid1 = _errs3 === errors;
                } else {
                  var valid1 = true;
                }

                if (valid1) {
                  if (data0.inputType_identities !== undefined) {
                    const _errs5 = errors;

                    if (typeof data0.inputType_identities !== "boolean") {
                      validate36.errors = [{
                        instancePath: instancePath + "/featureToggles/inputType_identities",
                        schemaPath: "#/properties/featureToggles/properties/inputType_identities/type",
                        keyword: "type",
                        params: {
                          type: "boolean"
                        },
                        message: "must be boolean"
                      }];
                      return false;
                    }

                    var valid1 = _errs5 === errors;
                  } else {
                    var valid1 = true;
                  }

                  if (valid1) {
                    if (data0.inputType_creditCards !== undefined) {
                      const _errs7 = errors;

                      if (typeof data0.inputType_creditCards !== "boolean") {
                        validate36.errors = [{
                          instancePath: instancePath + "/featureToggles/inputType_creditCards",
                          schemaPath: "#/properties/featureToggles/properties/inputType_creditCards/type",
                          keyword: "type",
                          params: {
                            type: "boolean"
                          },
                          message: "must be boolean"
                        }];
                        return false;
                      }

                      var valid1 = _errs7 === errors;
                    } else {
                      var valid1 = true;
                    }

                    if (valid1) {
                      if (data0.emailProtection !== undefined) {
                        const _errs9 = errors;

                        if (typeof data0.emailProtection !== "boolean") {
                          validate36.errors = [{
                            instancePath: instancePath + "/featureToggles/emailProtection",
                            schemaPath: "#/properties/featureToggles/properties/emailProtection/type",
                            keyword: "type",
                            params: {
                              type: "boolean"
                            },
                            message: "must be boolean"
                          }];
                          return false;
                        }

                        var valid1 = _errs9 === errors;
                      } else {
                        var valid1 = true;
                      }

                      if (valid1) {
                        if (data0.password_generation !== undefined) {
                          const _errs11 = errors;

                          if (typeof data0.password_generation !== "boolean") {
                            validate36.errors = [{
                              instancePath: instancePath + "/featureToggles/password_generation",
                              schemaPath: "#/properties/featureToggles/properties/password_generation/type",
                              keyword: "type",
                              params: {
                                type: "boolean"
                              },
                              message: "must be boolean"
                            }];
                            return false;
                          }

                          var valid1 = _errs11 === errors;
                        } else {
                          var valid1 = true;
                        }

                        if (valid1) {
                          if (data0.credentials_saving !== undefined) {
                            const _errs13 = errors;

                            if (typeof data0.credentials_saving !== "boolean") {
                              validate36.errors = [{
                                instancePath: instancePath + "/featureToggles/credentials_saving",
                                schemaPath: "#/properties/featureToggles/properties/credentials_saving/type",
                                keyword: "type",
                                params: {
                                  type: "boolean"
                                },
                                message: "must be boolean"
                              }];
                              return false;
                            }

                            var valid1 = _errs13 === errors;
                          } else {
                            var valid1 = true;
                          }
                        }
                      }
                    }
                  }
                }
              }
            } else {
              validate36.errors = [{
                instancePath: instancePath + "/featureToggles",
                schemaPath: "#/properties/featureToggles/type",
                keyword: "type",
                params: {
                  type: "object"
                },
                message: "must be object"
              }];
              return false;
            }
          }
        }
      }
    } else {
      validate36.errors = [{
        instancePath,
        schemaPath: "#/type",
        keyword: "type",
        params: {
          type: "object"
        },
        message: "must be object"
      }];
      return false;
    }
  }

  validate36.errors = vErrors;
  return errors === 0;
}

},{"ajv/dist/runtime/equal":9}],63:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.AndroidSender = void 0;

var _sender = require("./sender");

var _messages = require("../messages/messages");

class AndroidSender extends _sender.Sender {
  async handle(msg) {
    const {
      data
    } = msg;

    if (msg instanceof _messages.GetRuntimeConfiguration) {
      window.BrowserAutofill.getRuntimeConfiguration();
      return waitForResponse(msg.responseName);
    }

    if (msg instanceof _messages.GetAvailableInputTypes) {
      window.BrowserAutofill.getAvailableInputTypes();
      return waitForResponse(msg.responseName);
    }

    if (msg instanceof _messages.GetAutofillData) {
      window.BrowserAutofill.getAutofillData(JSON.stringify(data));
      return waitForResponse(msg.responseName);
    }

    if (msg instanceof _messages.StoreFormData) {
      return window.BrowserAutofill.storeFormData(JSON.stringify(data));
    }

    throw new Error('android: not implemented: ' + msg.name);
  }

}
/**
 * Sends a message and returns a Promise that resolves with the response
 *
 * @param {string} expectedResponse - the name of the response
 * @returns {Promise<*>}
 */


exports.AndroidSender = AndroidSender;

function waitForResponse(expectedResponse) {
  return new Promise(resolve => {
    const handler = e => {
      // todo(Shane): Allow blank string, try sandboxed iframe. allow-scripts
      // if (e.origin !== window.origin) {
      //     console.log(`❌ origin-mismatch e.origin(${e.origin}) !== window.origin(${window.origin})`);
      //     return
      // }
      console.warn('event.origin check was disabled on Android.');

      if (!e.data) {
        console.log('❌ event.data missing');
        return;
      }

      if (typeof e.data !== 'string') {
        console.log('❌ event.data was not a string. Expected a string so that it can be JSON parsed');
        return;
      }

      try {
        let data = JSON.parse(e.data);

        if (data.type === expectedResponse) {
          window.removeEventListener('message', handler);
          return resolve(data);
        }

        console.log("\u274C event.data.type was '".concat(data.type, "', which didnt match '").concat(expectedResponse, "'"), JSON.stringify(data));
      } catch (e) {
        window.removeEventListener('message', handler);
        console.log('❌ Could not JSON.parse the response');
      }
    };

    window.addEventListener('message', handler);
  });
}

},{"../messages/messages":56,"./sender":69}],64:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.AppleSender = void 0;
exports.createSender = createSender;

var _sender = require("./sender");

var _messages = require("../messages/messages");

var _appleDeviceUtils = require("./appleDeviceUtils");

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

class AppleSender extends _sender.Sender {
  /** @type {GlobalConfig} */

  /** @param {GlobalConfig} globalConfig */
  constructor(globalConfig) {
    super();

    _defineProperty(this, "config", void 0);

    this.config = globalConfig;
  }

  async handle(msg) {
    let {
      name,
      data
    } = msg;

    try {
      if (name === 'getAutofillCredentials') {
        name = 'pmHandlerGetAutofillCredentials';
      } // todo(Shane): better way to handle this?


      if (data === null) data = undefined;
      let response = await (0, _appleDeviceUtils.wkSendAndWait)(name, data, {
        secret: this.config.secret,
        hasModernWebkitAPI: this.config.hasModernWebkitAPI
      }); // todo(Shane): Better way to handle this - per message?

      if (response && !('success' in response) && !('error' in response)) {
        return {
          success: response
        };
      }

      return response;
    } catch (e) {
      if (e instanceof _appleDeviceUtils.MissingWebkitHandler) {
        if (msg instanceof _messages.GetRuntimeConfiguration) {
          return fallbacks.getRuntimeConfiguration(this.config);
        }

        if (msg instanceof _messages.GetAvailableInputTypes) {
          return fallbacks.getAvailableInputTypes(this.config);
        }

        if (msg instanceof _messages.GetAutofillInitData) {
          return fallbacks.getAutofillInitData(this.config);
        }

        throw new Error('unimplemented handler: ' + name);
      } else {
        throw e;
      }
    }
  }

}
/**
 * Create a wrapper around the webkit messaging that conforms
 * to the Transport tooltipHandler
 *
 * @param {GlobalConfig} config
 */


exports.AppleSender = AppleSender;

function createSender(config) {
  return new AppleSender(config);
}

const fallbacks = {
  'getAutofillInitData': async globalConfig => {
    const sender = createSender(globalConfig); // mimic the old message

    const msg = new _messages.LegacyMessage();
    msg.name = 'pmHandlerGetAutofillInitData'; // get the response

    const response = await sender.send(msg); // update items to fix `id` field

    response.credentials.forEach(cred => {
      cred.id = String(cred.id);
    });
    return {
      success: {
        // default, allowing it to be overriden
        serializedInputContext: '{}',
        ...response
      }
    };
  },

  /**
   * If this handler is not available, we default to the old check for email, which is
   * to call 'emailHandlerCheckAppSignedInStatus'
   * @param globalConfig
   */
  'getAvailableInputTypes': async globalConfig => {
    const sender = createSender(globalConfig);
    const message = new _messages.EmailSignedIn();
    const {
      isAppSignedIn
    } = await sender.send(message);
    /** @type {AvailableInputTypes} */

    const legacyMacOsTypes = {
      credentials: true,
      identities: true,
      creditCards: true,
      email: isAppSignedIn
    };
    /** @type {AvailableInputTypes} */

    const legacyIOsTypes = {
      credentials: false,
      identities: false,
      creditCards: false,
      email: isAppSignedIn
    };
    return {
      /** @type {AvailableInputTypes} */
      success: globalConfig.isApp ? legacyMacOsTypes : legacyIOsTypes
    };
  },

  /**
   * @param {GlobalConfig} globalConfig
   */
  'getRuntimeConfiguration': async globalConfig => {
    const legacyMacOSToggles = {
      inputType_credentials: true,
      inputType_identities: true,
      inputType_creditCards: true,
      emailProtection: true,
      password_generation: true,
      credentials_saving: true
    };
    const legacyIOSToggles = {
      inputType_credentials: false,
      inputType_identities: false,
      inputType_creditCards: false,
      emailProtection: true,
      password_generation: false,
      credentials_saving: false
    };
    return {
      success: {
        contentScope: globalConfig.contentScope,
        userPreferences: {
          // this is for old ios/macos
          features: {
            autofill: {
              settings: {
                /** @type {FeatureToggles} */
                featureToggles: globalConfig.isApp ? legacyMacOSToggles : legacyIOSToggles
              }
            }
          },
          ...globalConfig.userPreferences
        },
        userUnprotectedDomains: globalConfig.userUnprotectedDomains
      }
    };
  }
};

},{"../messages/messages":56,"./appleDeviceUtils":65,"./sender":69}],65:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.wkSendAndWait = exports.MissingWebkitHandler = void 0;

var _captureDdgGlobals = _interopRequireDefault(require("./captureDdgGlobals"));

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * Sends message to the webkit layer (fire and forget)
 * @param {String} handler
 * @param {*} data
 * @param {{hasModernWebkitAPI?: boolean, secret?: string}} opts
 */
const wkSend = function (handler) {
  let data = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let opts = arguments.length > 2 ? arguments[2] : undefined;

  if (!(handler in window.webkit.messageHandlers)) {
    throw new MissingWebkitHandler("Missing webkit handler: '".concat(handler, "'"));
  }

  return window.webkit.messageHandlers[handler].postMessage({ ...data,
    messageHandling: { ...data.messageHandling,
      secret: opts.secret
    }
  });
};
/**
 * Generate a random method name and adds it to the global scope
 * The native layer will use this method to send the response
 * @param {String} randomMethodName
 * @param {Function} callback
 */


const generateRandomMethod = (randomMethodName, callback) => {
  _captureDdgGlobals.default.ObjectDefineProperty(_captureDdgGlobals.default.window, randomMethodName, {
    enumerable: false,
    // configurable, To allow for deletion later
    configurable: true,
    writable: false,
    value: function () {
      callback(...arguments);
      delete _captureDdgGlobals.default.window[randomMethodName];
    }
  });
};
/**
 * Sends message to the webkit layer and waits for the specified response
 * @param {String} handler
 * @param {*} data
 * @param {{hasModernWebkitAPI?: boolean, secret?: string}} opts
 * @returns {Promise<*>}
 */


const wkSendAndWait = async function (handler) {
  let data = arguments.length > 1 && arguments[1] !== undefined ? arguments[1] : {};
  let opts = arguments.length > 2 && arguments[2] !== undefined ? arguments[2] : {};

  if (opts.hasModernWebkitAPI) {
    const response = await wkSend(handler, data, opts);
    return _captureDdgGlobals.default.JSONparse(response || '{}');
  }

  try {
    const randMethodName = createRandMethodName();
    const key = await createRandKey();
    const iv = createRandIv();
    const {
      ciphertext,
      tag
    } = await new _captureDdgGlobals.default.Promise(resolve => {
      generateRandomMethod(randMethodName, resolve);
      data.messageHandling = {
        methodName: randMethodName,
        secret: opts.secret,
        key: _captureDdgGlobals.default.Arrayfrom(key),
        iv: _captureDdgGlobals.default.Arrayfrom(iv)
      };
      wkSend(handler, data, opts);
    });
    const cipher = new _captureDdgGlobals.default.Uint8Array([...ciphertext, ...tag]);
    const decrypted = await decrypt(cipher, key, iv);
    return _captureDdgGlobals.default.JSONparse(decrypted || '{}');
  } catch (e) {
    console.error('decryption failed', e);
    return {
      error: e
    };
  }
};

exports.wkSendAndWait = wkSendAndWait;

const randomString = () => '' + _captureDdgGlobals.default.getRandomValues(new _captureDdgGlobals.default.Uint32Array(1))[0];

const createRandMethodName = () => '_' + randomString();

const algoObj = {
  name: 'AES-GCM',
  length: 256
};

const createRandKey = async () => {
  const key = await _captureDdgGlobals.default.generateKey(algoObj, true, ['encrypt', 'decrypt']);
  const exportedKey = await _captureDdgGlobals.default.exportKey('raw', key);
  return new _captureDdgGlobals.default.Uint8Array(exportedKey);
};

const createRandIv = () => _captureDdgGlobals.default.getRandomValues(new _captureDdgGlobals.default.Uint8Array(12));

const decrypt = async (ciphertext, key, iv) => {
  const cryptoKey = await _captureDdgGlobals.default.importKey('raw', key, 'AES-GCM', false, ['decrypt']);
  const algo = {
    name: 'AES-GCM',
    iv
  };
  let decrypted = await _captureDdgGlobals.default.decrypt(algo, cryptoKey, ciphertext);
  let dec = new _captureDdgGlobals.default.TextDecoder();
  return dec.decode(decrypted);
};

class MissingWebkitHandler extends Error {
  constructor(handlerName) {
    super();

    _defineProperty(this, "handlerName", void 0);

    this.handlerName = handlerName;
  }

}

exports.MissingWebkitHandler = MissingWebkitHandler;

},{"./captureDdgGlobals":66}],66:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.default = void 0;
// Capture the globals we need on page start
const secretGlobals = {
  window,
  // Methods must be bound to their tooltipHandler, otherwise they throw Illegal invocation
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
  ObjectDefineProperty: window.Object.defineProperty
};
var _default = secretGlobals;
exports.default = _default;

},{}],67:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.createSender = createSender;

var _apple = require("./apple.sender");

var _android = require("./android.sender");

var _windows = require("./windows.sender");

var _extension = require("./extension.sender");

var _sender = require("./sender");

/**
 * We have to decide on a sender, *before* we have a 'tooltipHandler'.
 *
 * This is because an initial message to retrieve the platform configuration might be needed
 *
 * @param {GlobalConfig} globalConfig
 * @returns {import("./sender").Sender}
 */
function createSender(globalConfig) {
  const sender = selectSender(globalConfig); // if (globalConfig.isDDGTestMode) {
  // }
  // during integration, we are always adding this

  return new _sender.LoggingSender(sender);
}
/**
 * The runtime has to decide on a transport, *before* we have a 'tooltipHandler'.
 *
 * This is because an initial message to retrieve the platform configuration might be needed
 *
 * @param {GlobalConfig} globalConfig
 * @returns {import("./sender").Sender}
 */


function selectSender(globalConfig) {
  var _globalConfig$userPre, _globalConfig$userPre2, _globalConfig$userPre3, _globalConfig$userPre4;

  // On some platforms, things like `platform.name` are embedded into the script
  // and therefor may be immediately available.
  if (globalConfig.isWindows) {
    return (0, _windows.createWindowsSender)();
  }

  if (typeof ((_globalConfig$userPre = globalConfig.userPreferences) === null || _globalConfig$userPre === void 0 ? void 0 : (_globalConfig$userPre2 = _globalConfig$userPre.platform) === null || _globalConfig$userPre2 === void 0 ? void 0 : _globalConfig$userPre2.name) === 'string') {
    switch ((_globalConfig$userPre3 = globalConfig.userPreferences) === null || _globalConfig$userPre3 === void 0 ? void 0 : (_globalConfig$userPre4 = _globalConfig$userPre3.platform) === null || _globalConfig$userPre4 === void 0 ? void 0 : _globalConfig$userPre4.name) {
      case 'ios':
      case 'macos':
        return new _apple.AppleSender(globalConfig);

      default:
        throw new Error('selectSender unimplemented!');
    }
  }

  if (globalConfig.isDDGApp) {
    if (globalConfig.isAndroid) {
      return new _android.AndroidSender();
    }

    console.warn('should never get here...');
    return new _apple.AppleSender(globalConfig);
  } // falls back to extension... is this still the best way to determine this?


  return new _extension.ExtensionSender();
}

},{"./android.sender":63,"./apple.sender":64,"./extension.sender":68,"./sender":69,"./windows.sender":70}],68:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.ExtensionSender = void 0;

var _sender = require("./sender");

var _messages = require("../messages/messages");

var _contentScopeScripts = require("@duckduckgo/content-scope-scripts");

class ExtensionSender extends _sender.Sender {
  async handle(msg) {
    if (msg instanceof _messages.GetRuntimeConfiguration) {
      return handlers.getRuntimeConfiguration(msg);
    }

    if (msg instanceof _messages.GetAvailableInputTypes) {
      return handlers.getAvailableInputTypes(msg);
    }

    throw new Error('not implemented yet');
  }

}
/**
 * Try to send a message to the Extension.
 *
 * This will try to detect if you've called a handler that's not available in the extension,
 * if so, it will throw a known message so that you can decide to fallback/recover if possible.
 *
 * For example, this can help when implementing new messaging
 *
 * @param {Record<string, any>} input
 * @returns {Promise<any>}
 */


exports.ExtensionSender = ExtensionSender;

function sendToExtension(input) {
  return new Promise((resolve, reject) => {
    chrome.runtime.sendMessage(input, data => {
      if (typeof data === 'undefined') {
        reject(new Error('Unknown extension error for message: ' + name));
      } else {
        return resolve(data);
      }
    });
  });
}

const handlers = {
  /**
   * This is a stub for the new message until the extension supports it
   *
   * @param {GetAvailableInputTypes} msg
   * @returns {Promise<any>}
   */
  'getAvailableInputTypes': async msg => {
    return {
      success: msg.response({
        email: false,
        identities: false,
        credentials: false,
        creditCards: false
      })
    };
  },

  /**
   * This is a stub for the new message until the extension supports it
   *
   * @param {GetRuntimeConfiguration} msg
   */
  'getRuntimeConfiguration': async msg => {
    const extensionResponse = await sendToExtension({
      registeredTempAutofillContentScript: true,
      documentUrl: window.location.href
    });
    const enabled = (0, _contentScopeScripts.isFeatureEnabledFromProcessedConfig)(extensionResponse, 'autofill');
    /**
     * @type {FeatureToggles}
     */

    const featureToggles = {
      'inputType_credentials': false,
      'inputType_identities': false,
      'inputType_creditCards': false,
      'emailProtection': true,
      'password_generation': false,
      'credentials_saving': false
    };
    const response = msg.response({
      contentScope: {
        features: {
          autofill: {
            state: enabled ? 'enabled' : 'disabled',
            exceptions: []
          }
        },
        unprotectedTemporary: []
      },
      userPreferences: {
        // @ts-ignore
        sessionKey: '',
        debug: false,
        globalPrivacyControlValue: false,
        platform: {
          name: 'extension'
        },
        features: {
          autofill: {
            settings: {
              featureToggles: featureToggles
            }
          }
        }
      },
      userUnprotectedDomains: []
    });
    return {
      success: response
    };
  }
};

},{"../messages/messages":56,"./sender":69,"@duckduckgo/content-scope-scripts":1}],69:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.Sender = exports.NullSender = exports.LoggingSender = void 0;

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * This is the base Sender class that platforms can will implement.
 *
 * Note: The 'handle' method must be implemented, unless you also implement 'send'
 */
class Sender {
  /**
   * Try to send a message. Throws with validation errors
   *
   * @throws {SchemaValidationError}
   * @template Request,Response
   * @param {import("../messages/message").Message<Request, Response>} message
   * @returns {Promise<ReturnType<import("../messages/message").Message<Request, Response>['validateResponse']>>}
   */
  async send(message) {
    message.validateRequest();
    let response = await this.handle(message);
    let processed = message.preResponseValidation(response);
    return message.validateResponse(processed);
  }
  /**
   * @template Request,Response
   * @param {import("../messages/message").Message<Request, Response>} message
   * @returns {Promise<Response | undefined>}
   */


  async handle(message) {
    throw new Error('must implement `.handle`, message: ' + message.name);
  }

}

exports.Sender = Sender;

class NullSender extends Sender {
  /**
   * @param _message
   * @returns {Promise<any>}
   */
  async send(_message) {
    return null;
  }

}
/**
 *
 */


exports.NullSender = NullSender;

class LoggingSender extends Sender {
  constructor(sender) {
    super();

    _defineProperty(this, "sender", void 0);

    this.sender = sender;
  }

  async handle(message) {
    LoggingSender.printOutgoing(message);
    const value = await this.sender.handle(message);
    LoggingSender.printIncoming(message, value);
    return value;
  }
  /**
   * @param {import("../messages/message").Message} message
   */


  static printOutgoing(message) {
    if (message.data) {
      if (typeof message.data === 'string') {
        return console.log('✈️', message.name, message.data);
      } else {
        return console.log("\u2708", message.name, JSON.stringify(message.data));
      }
    }

    console.log('✈️', message.name);
  }
  /**
   * @param {import("../messages/message").Message} message
   * @param {any} value
   */


  static printIncoming(message, value) {
    console.log("\uD83D\uDCE5", message.name, JSON.stringify(value, null, 2));
  }

}

exports.LoggingSender = LoggingSender;

},{}],70:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.createWindowsSender = createWindowsSender;

var _sender = require("./sender");

var _responseGetAutofillInitDataSchema = _interopRequireDefault(require("../schema/response.getAutofillInitData.schema.json"));

var _responseGetAvailableInputTypesSchema = _interopRequireDefault(require("../schema/response.getAvailableInputTypes.schema.json"));

var _responseGetRuntimeConfigurationSchema = _interopRequireDefault(require("../schema/response.getRuntimeConfiguration.schema.json"));

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

// import getAutofillData from '../schema/response.getAutofillData.schema.json'
class WindowsSender extends _sender.Sender {
  async handle(message) {
    const {
      name,
      data
    } = message;

    switch (name) {
      case 'setSize':
        {
          windowsTransport(name);
          break;
        }

      case 'showAutofillParent':
        {
          windowsTransport(name, data);
          break;
        }

      case 'closeAutofillParent':
        {
          windowsTransport(name);
          break;
        }

      case 'getRuntimeConfiguration':
        {
          return windowsTransport(name).withResponse(_responseGetRuntimeConfigurationSchema.default.properties.type.const);
        }

      case 'getAvailableInputTypes':
        {
          return windowsTransport(name).withResponse(_responseGetAvailableInputTypesSchema.default.properties.type.const);
        }

      case 'getAutofillInitData':
        {
          return windowsTransport(name).withResponse(_responseGetAutofillInitDataSchema.default.properties.type.const);
        }

      case 'storeFormData':
        {
          windowsTransport('storeFormData', data);
          break;
        }

      case 'selectedDetail':
        {
          windowsTransport(name, data);
          break;
        }

      case 'getAutofillCredentials':
        {
          // todo(Shane): Schema
          return windowsTransport('getAutofillCredentials', data).withResponse('getAutofillCredentialsResponse');
        }

      default:
        throw new Error('windows: not implemented: ' + name);
    }
  }

}

function createWindowsSender() {
  return new WindowsSender();
}
/**
 * @param {Names} name
 * @param {any} [data]
 */


function windowsTransport(name, data) {
  if (data) {
    window.chrome.webview.postMessage({
      type: name,
      data: data
    });
  } else {
    window.chrome.webview.postMessage({
      type: name
    });
  }

  return {
    /**
     * Sends a message and returns a Promise that resolves with the response
     * @param responseName
     * @returns {Promise<*>}
     */
    withResponse(responseName) {
      return new Promise(resolve => {
        const handler = event => {
          /* if (event.origin !== window.origin) {
              console.warn(`origin mis-match. window.origin: ${window.origin}, event.origin: ${event.origin}`)
              return
          } */
          if (!event.data) {
            console.warn('data absent from message');
            return;
          }

          if (event.data.type === responseName) {
            resolve(event.data);
            window.chrome.webview.removeEventListener('message', handler);
          } // at this point we're confident we have the correct message type

        };

        window.chrome.webview.addEventListener('message', handler, {
          once: true
        });
      });
    }

  };
}

},{"../schema/response.getAutofillInitData.schema.json":59,"../schema/response.getAvailableInputTypes.schema.json":60,"../schema/response.getRuntimeConfiguration.schema.json":61,"./sender":69}],71:[function(require,module,exports){
"use strict";

Object.defineProperty(exports, "__esModule", {
  value: true
});
exports.Settings = void 0;
exports.fromRuntimeConfig = fromRuntimeConfig;

var _validators = _interopRequireDefault(require("../schema/validators.cjs"));

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { default: obj }; }

function _defineProperty(obj, key, value) { if (key in obj) { Object.defineProperty(obj, key, { value: value, enumerable: true, configurable: true, writable: true }); } else { obj[key] = value; } return obj; }

/**
 * A wrapper for Autofill settings
 */
class Settings {
  constructor() {
    _defineProperty(this, "validate", _validators.default['#/definitions/AutofillSettings']);

    _defineProperty(this, "settings", null);
  }

  /**
   * Try to convert an object into Autofill Settings.
   * This will try to validate the keys against the schema
   *
   * @throws
   * @returns {Settings}
   */
  from(input) {
    if (this.validate(input)) {
      this.settings = input;
    } else {
      // @ts-ignore
      for (const error of this.validate.errors) {
        console.error(error.message);
        console.error(error);
      }

      throw new Error('Could not create settings from global configuration');
    }

    return this;
  }
  /**
   * @returns {FeatureToggles}
   */


  get featureToggles() {
    if (!this.settings) throw new Error('unreachable');
    return this.settings.featureToggles;
  }
  /** @returns {Settings} */


  static default() {
    return new Settings().from({
      /** @type {FeatureToggles} */
      featureToggles: {
        inputType_credentials: true,
        inputType_identities: true,
        inputType_creditCards: true,
        emailProtection: true,
        password_generation: true,
        credentials_saving: true
      }
    });
  }

}
/**
 * @param {import("@duckduckgo/content-scope-scripts").RuntimeConfiguration} config
 * @returns {Settings}
 */


exports.Settings = Settings;

function fromRuntimeConfig(config) {
  const autofillSettings = config.getSettings('autofill');
  const settings = new Settings().from(autofillSettings);
  return settings;
}

},{"../schema/validators.cjs":62}]},{},[52]);
